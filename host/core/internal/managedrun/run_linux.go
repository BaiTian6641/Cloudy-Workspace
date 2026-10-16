//go:build linux

// Package managedrun retains one real local job and its immutable observations.
// It supplies no service lifetime, remote admission or crash/replay authority.
package managedrun

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"os"
	"sync"

	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/bootidentity"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobevents"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobrecorder"
	"github.com/BaiTian6641/Cloudy-Workspace/host/core/internal/jobs"
)

type Failure struct {
	Stage, Code                string
	CompletionUnknown, Claimed bool
}

func (f *Failure) Error() string { return "managed-run-" + f.Stage + ":" + f.Code }

type IDs struct{ JobID, WaitEventID, SettleEventID string }

// Result separates observed retirement from recording. Persistence uncertainty
// is sticky even if a later explicit retry returns a successful receipt.
type Result struct {
	Captured, Recorded, CaptureFailed, PersistenceFailed bool
	PersistenceUnknown, Claimed, Retired                 bool
	Receipt                                              jobevents.Receipt
}
type Pending struct {
	Wait, Settlement                                     jobrecorder.Observation
	HasWait, HasSettlement                               bool
	WaitPersistenceUnknown, SettlementPersistenceUnknown bool
}
type kind uint8

const (
	waitObservation kind = iota
	settleObservation
)

type slot struct {
	observation                                                       jobrecorder.Observation
	captured, recorded, captureFailed, appendFailed, unknown, claimed bool
	captureInvalid                                                    bool
	receipt                                                           jobevents.Receipt
}
type operations struct {
	wait, settle func(context.Context, *jobs.Job, jobrecorder.Metadata) (jobrecorder.Observation, error)
	append       func(kind, jobrecorder.Observation, *jobevents.Store) (jobevents.Receipt, error)
	cancel       func(*jobs.Job)
}
type admission struct {
	boot   func() (bootidentity.ID, error)
	uuid   func() (string, error)
	read   func(*jobevents.Store) (jobevents.Batch, error)
	lookup func(*jobevents.Store, string) (jobevents.Presence, error)
	start  func(context.Context, *os.File, jobs.Spec) (*jobs.Job, error)
}

// Run must remain reachable through uncertain retirement. Store is borrowed:
// its caller must not close or mutate it concurrently, and owns final Close.
// Exported metadata, observations, persistence and errors copy no command inputs,
// output buffers or raw failure pointers. The retained Job still owns its own
// bounded buffers and process/retirement state.
type Run struct {
	mu                    sync.Mutex
	job                   *jobs.Job
	store                 *jobevents.Store
	ids                   IDs
	boot                  bootidentity.ID
	op                    operations
	busy, waited, retired bool
	slots                 [2]slot
}

func fixed(stage, code string) *Failure { return &Failure{Stage: stage, Code: code} }
func uuid() (string, error) {
	var value [16]byte
	if _, e := rand.Read(value[:]); e != nil {
		return "", e
	}
	value[6] = value[6]&15 | 64
	value[8] = value[8]&63 | 128
	s := hex.EncodeToString(value[:])
	return s[:8] + "-" + s[8:12] + "-" + s[12:16] + "-" + s[16:20] + "-" + s[20:], nil
}
func canonicalID(s string) bool {
	if len(s) != 36 {
		return false
	}
	for i, c := range s {
		if i == 8 || i == 13 || i == 18 || i == 23 {
			if c != '-' {
				return false
			}
		} else if !(c >= '0' && c <= '9' || c >= 'a' && c <= 'f') {
			return false
		}
	}
	return s[14] == '4' && (s[19] == '8' || s[19] == '9' || s[19] == 'a' || s[19] == 'b')
}
func productionOperations() operations {
	return operations{
		wait: jobrecorder.CaptureWait, settle: jobrecorder.CaptureSettle,
		append: func(_ kind, o jobrecorder.Observation, s *jobevents.Store) (jobevents.Receipt, error) {
			return o.AppendTo(s)
		},
		cancel: (*jobs.Job).Cancel,
	}
}

// Start performs bounded catalog/ID preflight, then exactly one real Start.
// Store preflight is single-writer observation, not a durable admission ledger.
// Any nonnil returned Run must be retained even when accompanied by an error.
func Start(ctx context.Context, delegation *os.File, store *jobevents.Store, spec jobs.Spec) (*Run, error) {
	return start(ctx, delegation, store, spec, admission{
		boot: bootidentity.Read, uuid: uuid,
		read:   func(s *jobevents.Store) (jobevents.Batch, error) { return s.ReadAfter(jobevents.Cursor{}, 1) },
		lookup: (*jobevents.Store).Lookup, start: jobs.Start,
	}, productionOperations())
}
func start(ctx context.Context, delegation *os.File, store *jobevents.Store, spec jobs.Spec, a admission, op operations) (*Run, error) {
	if ctx == nil || delegation == nil || store == nil {
		return nil, fixed("admission", "invalid-input")
	}
	if ctx.Err() != nil {
		return nil, fixed("admission", "canceled")
	}
	boot, e := a.boot()
	if e != nil || !canonicalID(boot.String()) {
		return nil, fixed("admission", "boot-unavailable")
	}
	var values [3]string
	for i := range values {
		values[i], e = a.uuid()
		if e != nil || !canonicalID(values[i]) {
			return nil, fixed("admission", "identity-failed")
		}
	}
	if values[0] == values[1] || values[0] == values[2] || values[1] == values[2] {
		return nil, fixed("admission", "identity-collision")
	}
	batch, e := a.read(store)
	// A limited normal batch is not a complete catalog. Empty cursor must give
	// the contract's full stream-reset snapshot before claiming capacity.
	if e != nil || !batch.ResetNeeded || batch.GapReason != "stream-reset" || batch.HasMore || len(batch.Events) != 0 || !canonicalID(batch.Next.StreamID) {
		return nil, fixed("admission", "catalog-unavailable")
	}
	if len(batch.Snapshot) >= jobevents.MaxJobs {
		return nil, fixed("admission", "catalog-full")
	}
	for _, event := range batch.Snapshot {
		for _, id := range values {
			if event.ResourceID == id {
				return nil, fixed("admission", "identity-collision")
			}
		}
	}
	for _, id := range values {
		presence, e := a.lookup(store, id)
		if e != nil {
			return nil, fixed("admission", "catalog-unavailable")
		}
		if presence.Found {
			return nil, fixed("admission", "identity-collision")
		}
	}
	if ctx.Err() != nil {
		return nil, fixed("admission", "canceled")
	}
	owner := &Run{store: store, ids: IDs{values[0], values[1], values[2]}, boot: boot, op: op}
	owner.job, e = a.start(ctx, delegation, spec)
	if owner.job == nil {
		return nil, fixed("start", "not-created")
	}
	if e != nil {
		return owner, fixed("start", "owner-retained")
	}
	return owner, nil
}
func (r *Run) IDs() IDs {
	if r == nil {
		return IDs{}
	}
	return r.ids
}
func (r *Run) Retired() bool {
	if r == nil {
		return false
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.retired
}
func (r *Run) PendingObservations() Pending {
	if r == nil {
		return Pending{}
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	return Pending{r.slots[0].observation, r.slots[1].observation, r.slots[0].captured, r.slots[1].captured, r.slots[0].unknown, r.slots[1].unknown}
}

// Cancel does not acquire the mutable-operation gate or wait for observation,
// persistence or retirement. The retained Job implements the actual stop commit.
func (r *Run) Cancel() error {
	if r == nil || r.job == nil {
		return fixed("cancel", "invalid-input")
	}
	r.op.cancel(r.job)
	return nil
}
func (r *Run) result(k kind) Result {
	s := r.slots[k]
	return Result{s.captured, s.recorded, s.captureFailed, s.appendFailed, s.unknown, s.claimed, r.retired, s.receipt}
}
func (r *Run) cachedError(k kind) *Failure {
	s := r.slots[k]
	if s.appendFailed {
		return &Failure{Stage: "append", Code: "persistence-failed", CompletionUnknown: s.unknown, Claimed: s.claimed}
	}
	if s.captureFailed {
		code := "observation-failed"
		if s.captureInvalid {
			code = "invalid-observation"
		}
		return &Failure{Stage: "capture", Code: code, CompletionUnknown: true}
	}
	return nil
}
func (r *Run) begin() error {
	if r == nil || r.job == nil {
		return fixed("operation", "invalid-input")
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.busy {
		return fixed("operation", "busy")
	}
	r.busy = true
	return nil
}
func (r *Run) end() { r.mu.Lock(); r.busy = false; r.mu.Unlock() }
func (r *Run) metadata(k kind) jobrecorder.Metadata {
	id, revision := r.ids.WaitEventID, uint64(1)
	if k == settleObservation {
		id, revision = r.ids.SettleEventID, 2
	}
	return jobrecorder.Metadata{EventID: id, JobID: r.ids.JobID, Revision: revision, BootID: r.boot}
}
func (r *Run) WaitAndRecord(ctx context.Context) (Result, error) {
	return r.observe(ctx, waitObservation)
}
func (r *Run) SettleAndRecord(ctx context.Context) (Result, error) {
	return r.observe(ctx, settleObservation)
}
func (r *Run) observe(ctx context.Context, k kind) (Result, error) {
	// Invalid observer does not consume Wait or touch the operation gate.
	if ctx == nil {
		return Result{}, fixed("capture", "invalid-observer")
	}
	if e := r.begin(); e != nil {
		return Result{}, e
	}
	defer r.end()
	r.mu.Lock()
	if r.slots[k].captured || (k == waitObservation && r.waited) {
		v, e := r.result(k), r.cachedError(k)
		r.mu.Unlock()
		if e != nil {
			return v, e
		}
		return v, nil
	}
	if k == waitObservation && r.retired {
		// Cached Wait was handled above. Never create revision 1 after this
		// owner's successful terminal revision 2 capture, even if its append failed.
		v := r.result(k)
		r.mu.Unlock()
		return v, fixed("capture", "settled-before-wait")
	}
	if k == waitObservation {
		r.waited = true
	}
	r.mu.Unlock()
	capture := r.op.wait
	if k == settleObservation {
		capture = r.op.settle
	}
	observation, e := capture(ctx, r.job, r.metadata(k))
	if k == settleObservation && e != nil {
		// Pending settlement is retained in the real Job, not appended as terminal.
		// No append means a later explicit settle can capture a fresh terminal time.
		return Result{CaptureFailed: true}, fixed("capture", "settlement-uncertain")
	}
	usable := e == nil
	if source, ok := e.(*jobrecorder.Failure); ok && source != nil && source.Code == "observation-failed" && k == waitObservation {
		usable = true
	}
	r.mu.Lock()
	r.slots[k].captureFailed = e != nil
	if !usable {
		r.slots[k].captureInvalid = true
		v := r.result(k)
		r.mu.Unlock()
		return v, fixed("capture", "invalid-observation")
	}
	r.slots[k].observation = observation
	r.slots[k].captured = true
	if k == settleObservation {
		r.retired = true
	} // Real CaptureSettle succeeded, independently of append.
	r.mu.Unlock()
	return r.append(k)
}
func (r *Run) RetryWaitAppend() (Result, error)   { return r.retry(waitObservation) }
func (r *Run) RetrySettleAppend() (Result, error) { return r.retry(settleObservation) }
func (r *Run) retry(k kind) (Result, error) {
	if e := r.begin(); e != nil {
		return Result{}, e
	}
	defer r.end()
	r.mu.Lock()
	ready := r.slots[k].captured
	r.mu.Unlock()
	if !ready {
		return Result{}, fixed("append", "not-captured")
	}
	return r.append(k)
}
func (r *Run) append(k kind) (Result, error) {
	r.mu.Lock()
	observation := r.slots[k].observation
	r.mu.Unlock()
	receipt, e := r.op.append(k, observation, r.store)
	r.mu.Lock()
	defer r.mu.Unlock()
	s := &r.slots[k]
	s.appendFailed = e != nil
	s.recorded = e == nil
	if e != nil {
		s.receipt = jobevents.Receipt{}
		if source, ok := e.(*jobrecorder.Failure); ok && source != nil {
			s.unknown = s.unknown || source.CompletionUnknown
			s.claimed = s.claimed || source.Claimed
		}
	} else {
		s.receipt = receipt
	}
	v, f := r.result(k), r.cachedError(k)
	if f != nil {
		return v, f
	}
	return v, nil
}
