package io.github.baitian6641.cloudy.testing.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/** Explicit DUMP-protected endpoint; the Intent carries only a canonical request UUID. */
class QualificationImeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ImeFixtureProtocol.ACTION) return
        val component = intent.component ?: return
        if (component.packageName != context.packageName) return
        if (component.className != ImeFixtureProtocol.RECEIVER_CLASS) return
        val extras = intent.extras ?: return
        if (extras.size() != 1 || !extras.containsKey(ImeFixtureProtocol.EXTRA_REQUEST)) return
        val requestId = extras.getString(ImeFixtureProtocol.EXTRA_REQUEST) ?: return
        if (!ImeFixtureProtocol.isCanonicalUuid(requestId)) return
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val lease = runCatching { ImeFixtureProtocol.readLease(context) }.getOrNull() ?: return
        if (lease.component != "${context.packageName}/${ImeFixtureProtocol.SERVICE_CLASS}") return
        if (requestId !in lease.requests) return
        val service = QualificationImeService.current
        val claimFailure = service?.claim(requestId)
        val request = runCatching { ImeFixtureProtocol.readRequest(context, lease, requestId) }
            .getOrNull() ?: return

        // A prior immutable result forbids redispatch, even if a same-ID request was recreated.
        val hasResult = runCatching {
            ImeFixtureProtocol.resultExists(context, request.run, request.request)
        }.getOrElse { true }
        if (hasResult) {
            runCatching {
                ImeFixtureProtocol.deleteOwned(
                    ImeFixtureProtocol.requestFile(context, request.run, request.request),
                )
            }
            return
        }

        val result = when {
            service == null -> ImeDispatchResult(
                ImeFixtureResult.Outcome.REJECTED, ImeFixtureResult.Reason.NO_ACTIVE_IME)
            claimFailure != null -> ImeDispatchResult(ImeFixtureResult.Outcome.REJECTED, claimFailure)
            else -> service.dispatch(request, lease)
        }
        val published = runCatching {
            ImeFixtureProtocol.writeResult(context, request, result)
        }.isSuccess
        if (published) {
            // Retire text only after a published result; a failed publication leaves residue
            // for session retirement/host audit and never permits a retry.
            runCatching {
                ImeFixtureProtocol.deleteOwned(
                    ImeFixtureProtocol.requestFile(context, request.run, request.request),
                )
            }
        }
    }
}
