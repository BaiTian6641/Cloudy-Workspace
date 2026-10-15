/* Node-only byte/flow and authored-glue tests. The fake DOM/xterm below is test instrumentation,
 * never a runtime substitute or evidence of WebView, terminal parsing, IME or accessibility. */
"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const sourcePath = path.join(__dirname, "../qualification/probes/device-baseline/app/src/main/assets/terminal/terminal.js");
const api = require(sourcePath);
const identity = { sessionId: "00112233-4455-6677-8899-aabbccddeeff", attachmentId: "ffeeddcc-bbaa-9988-7766-554433221100" };

test("network-order envelope matches native golden bytes and preserves partial UTF-8", () => {
  const frame = api.encode(identity, api.TYPE.OUTPUT, 7, new Uint8Array([0xe4, 0xb8]), 1);
  assert.equal(Buffer.from(frame).toString("hex"), "435754520105000100112233445566778899aabbccddeeffffeeddccbbaa998877665544332211000000000700000002e4b8");
  assert.deepEqual([...api.decode(identity, frame).payload], [0xe4, 0xb8]);
  for (const index of [0, 4, 6, 8, 24, 44, 47]) {
    const changed = frame.slice(0); new Uint8Array(changed)[index] ^= 4;
    assert.throws(() => api.decode(identity, changed));
  }
  assert.throws(() => api.decode(identity, frame.slice(0, 47)));
  assert.throws(() => api.encode(identity, api.TYPE.INPUT, 2147483648, new Uint8Array([1])));
  assert.throws(() => api.encode(identity, api.TYPE.INPUT, 1, new Uint8Array([1]), 1));
});

test("UTF-8 text and raw onBinary bytes follow distinct encoding and whole-input limits", () => {
  assert.equal(Buffer.from(api.encodeText("中文🙂\u0003")).toString("hex"), "e4b8ade69687f09f998203");
  assert.deepEqual([...api.encodeBinary("\x00\x80\xff")], [0, 128, 255]);
  assert.equal(api.encodeBinary("中"), null);
  assert.equal(api.encodeText("中".repeat(21846)), null);
  assert.equal(api.encodeText("a".repeat(65536)).length, 65536);
  assert.equal(api.encodeText("a".repeat(65537)), null);
  assert.equal(api.encodeBinary("a".repeat(65537)), null);
});

test("whole-input ledger refuses overflow without partial reservation or stale credit", () => {
  const ledger = new api.InputLedger();
  assert.equal(ledger.reserve(1, 65535), true);
  assert.equal(ledger.reserve(2, 2), false);
  assert.equal(ledger.bytes, 65535);
  assert.throws(() => ledger.settle(2));
  ledger.settle(1);
  assert.equal(ledger.bytes, 0);
  assert.throws(() => ledger.settle(1));
  for (let i = 1; i <= 128; i++) assert.equal(ledger.reserve(i, 1), true);
  assert.equal(ledger.reserve(129, 1), false);
  ledger.clear();
  assert.equal(ledger.bytes, 0);
});

function harness() {
  const elements = new Map();
  function element(id) {
    if (!elements.has(id)) elements.set(id, { textContent: "", dataset: {}, hidden: false, handlers: {}, addEventListener(name, fn) { this.handlers[name] = fn; } });
    return elements.get(id);
  }
  let terminal;
  const sent = [], raf = [];
  class TestTerminal {
    constructor(options) { this.options = options; this.cols = 80; this.rows = 24; this.writes = []; this.osc = new Map(); this.parser = { registerOscHandler: (id, fn) => this.osc.set(id, fn) }; terminal = this; }
    loadAddon(addon) { this.addon = addon; }
    open(el) { this.element = el; }
    onData(fn) { this.data = fn; }
    onBinary(fn) { this.binary = fn; }
    onResize(fn) { this.resized = fn; }
    resize(cols, rows) { const changed = cols !== this.cols || rows !== this.rows; this.cols = cols; this.rows = rows; if (changed) this.resized({ cols, rows }); }
    write(bytes, done) { this.writes.push({ bytes, done }); }
    focus() { this.focused = true; }
  }
  const context = {
    Uint8Array, ArrayBuffer, DataView, TextEncoder, Number, Map, Error,
    location: { hash: `#${identity.sessionId}/${identity.attachmentId}/1.5` },
    document: { getElementById: element, documentElement: { style: { setProperty() {} } }, fonts: { ready: { then(fn) { fn(); return { catch() {} }; } } } },
    Terminal: TestTerminal, FitAddon: { FitAddon: class { proposeDimensions() { return { cols: 80, rows: 24 }; } } },
    cloudyTerminal: { postMessage(buffer) { sent.push(api.decode(identity, buffer)); } },
    ResizeObserver: class { constructor(fn) { this.fn = fn; } observe() { this.fn(); } },
    requestAnimationFrame(fn) { raf.push(fn); }, addEventListener() {},
  };
  vm.runInNewContext(fs.readFileSync(sourcePath, "utf8"), context, { filename: sourcePath });
  const flush = () => { let n = 0; while (raf.length) { assert.ok(++n < 100); raf.shift()(); } };
  flush();
  const receive = (type, sequence, bytes) => context.cloudyTerminal.onmessage({ data: api.encode(identity, type, sequence, bytes) });
  const admission = (nativeSequence, request, result) => {
    const bytes = new Uint8Array(8), v = new DataView(bytes.buffer);
    v.setUint32(0, request.sequence); v.setUint8(4, request.type); v.setUint8(5, result);
    receive(api.TYPE.ADMISSION, nativeSequence, bytes);
  };
  return { terminal, sent, element, flush, receive, admission };
}

test("real glue emits local readiness and sends text/binary only through their respective events", () => {
  const h = harness();
  assert.equal(h.sent.length, 1); assert.equal(h.sent[0].type, api.TYPE.READY);
  assert.equal(h.terminal.options.fontSize, 21);
  assert.equal(h.terminal.options.screenReaderMode, true);
  assert.equal(h.terminal.options.allowProposedApi, false);
  assert.equal(h.terminal.osc.get(52)("clipboard-data"), true);
  assert.equal(h.terminal.osc.get(8)("https://example.invalid"), true);
  h.terminal.data("中文"); h.terminal.binary("\x80\xff");
  assert.deepEqual(h.sent.map(x => x.type), [api.TYPE.READY, api.TYPE.INPUT, api.TYPE.INPUT]);
  assert.equal(Buffer.from(h.sent[1].payload).toString("hex"), "e4b8ade69687");
  assert.deepEqual([...h.sent[2].payload], [128, 255]);
});

test("output ACK follows the actual write callback and retains split byte boundaries", () => {
  const h = harness();
  h.receive(api.TYPE.OUTPUT, 1, new Uint8Array([0xe4, 0xb8]));
  h.receive(api.TYPE.OUTPUT, 2, new Uint8Array([0xad]));
  assert.equal(h.sent.length, 1);
  assert.deepEqual([...h.terminal.writes[0].bytes], [0xe4, 0xb8]);
  assert.deepEqual([...h.terminal.writes[1].bytes], [0xad]);
  h.terminal.writes[0].done(); h.terminal.writes[1].done();
  assert.deepEqual(h.sent.slice(1).map(x => [x.type, new DataView(x.payload.buffer, x.payload.byteOffset).getUint32(0), new DataView(x.payload.buffer, x.payload.byteOffset).getUint32(4)]), [[api.TYPE.ACK, 1, 2], [api.TYPE.ACK, 2, 1]]);
});

test("oversized and full input are visible whole-action rejections with no automatic retry", () => {
  const h = harness();
  h.terminal.data("中".repeat(21846));
  assert.equal(h.sent.length, 1);
  assert.match(h.element("status").textContent, /No part was sent/);
  h.terminal.data("a".repeat(65536));
  const request = h.sent.at(-1);
  h.terminal.data("b");
  assert.equal(h.sent.length, 2);
  assert.match(h.element("status").textContent, /queue is full/);
  h.admission(1, request, api.ADMISSION.FULL);
  assert.equal(h.sent.length, 2);
  assert.match(h.element("status").textContent, /not accepted or automatically retried/);
  h.terminal.data("b");
  assert.equal(h.sent.length, 3);
});

test("resize coalesces dimensions while preserving one outstanding request", () => {
  const h = harness();
  h.terminal.resize(100, 30); h.terminal.resize(132, 43); h.flush();
  assert.equal(h.sent.length, 2);
  assert.deepEqual([...h.sent[1].payload], [0, 132, 0, 43]);
  h.terminal.resize(140, 50); h.terminal.resize(150, 51); h.flush();
  assert.equal(h.sent.length, 2);
  h.admission(1, h.sent[1], api.ADMISSION.ACCEPTED); h.flush();
  assert.equal(h.sent.length, 3);
  assert.deepEqual([...h.sent[2].payload], [0, 150, 0, 51]);
});

test("closed or reordered native attachment disables input and cannot replay queued data", () => {
  const h = harness();
  h.receive(api.TYPE.CLOSED, 1, new Uint8Array());
  assert.equal(h.terminal.options.disableStdin, true);
  h.terminal.data("must-not-send"); assert.equal(h.sent.length, 1);
  const broken = harness();
  broken.receive(api.TYPE.OUTPUT, 2, new Uint8Array([1]));
  assert.equal(broken.terminal.writes.length, 0);
  assert.equal(broken.sent.at(-1).type, api.TYPE.ERROR);
  assert.equal(broken.terminal.options.disableStdin, true);
});

test("unknown admission cannot release input credit", () => {
  const h = harness();
  h.terminal.data("pending");
  h.admission(1, { sequence: 99, type: api.TYPE.INPUT }, api.ADMISSION.ACCEPTED);
  assert.equal(h.terminal.options.disableStdin, true);
  assert.equal(h.sent.at(-1).type, api.TYPE.ERROR);
});
