package dev.cloudy.qualification.baseline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.UUID

/** Only an explicit immutable notification PendingIntent reaches this receiver. */
class TransferCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(TransferRuntime.ATTEMPT_EXTRA) ?: return
        val validId = try { UUID.fromString(id).toString() == id } catch (_: IllegalArgumentException) { false }
        if (!validId || intent.dataString != "cloudy-transfer://cancel/$id") return
        // Exact attempt matching also fences a stale notification after a later
        // operation reuses the fixed JobScheduler ID. No file/socket work here.
        val pending = goAsync()
        TransferRuntime.get(context).cancelFromNotification(id) { pending.finish() }
    }
}
