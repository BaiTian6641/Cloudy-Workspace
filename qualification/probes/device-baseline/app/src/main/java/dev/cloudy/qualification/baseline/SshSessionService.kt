package dev.cloudy.qualification.baseline

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.UUID

/** A finite opt-in lease over ProbeApplication.ssh, never another engine or a restart daemon. */
class SshSessionService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val runtime: SshRuntime get() = (application as ProbeApplication).ssh
    private var activeId: String? = null
    private var latestStartId = 0
    private var foreground = false
    private val observer = SshRuntime.RetentionObserver { state, completion ->
        val id = activeId
        if (id != null) {
            if (completion?.attemptId == id && (completion.workerReturned || !completion.workerStarted)) {
                // The runtime preserves quarantine on helper/write uncertainty. Stopping
                // this FGS never asserts that such a run retired cleanly or permits reuse.
                finish(id)
            } else if (foreground) {
                try { manager(this).notify(NOTIFICATION, notification(id, state.status)) }
                catch (_: RuntimeException) { runtime.cancelAttempt(id) }
            }
        }
    }
    private val accessWatch = object : Runnable {
        override fun run() {
            val id = activeId ?: return
            if (!notificationAllowed(this@SshSessionService)) runtime.cancelAttempt(id)
            runtime.checkRetentionAccess(id)
            main.postDelayed(this, 1_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val id = intent?.getStringExtra(EXTRA)
        if (id == null || !canonical(id)) {
            if (activeId == null) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (intent.action == STOP && intent.dataString == "cloudy-ssh://disconnect/$id") {
            runtime.cancelAttempt(id)
            if (activeId == null) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (intent.action != START || intent.dataString != "cloudy-ssh://retain/$id") {
            if (activeId == null) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (activeId == id) return START_NOT_STICKY // One request cannot run a second engine.
        if (activeId != null || !runtime.retentionPending(id)) {
            runtime.failRetentionStart(id, "retention-start-stale-or-busy")
            if (activeId == null) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (!notificationAllowed(this)) {
            runtime.failRetentionStart(id, "retention-notifications-unavailable")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        try {
            // Promotion precedes connection admission, while start eligibility still
            // derives from the explicit visible user request, not a hidden-window timer.
            startForeground(NOTIFICATION, notification(id, "starting"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            foreground = true
            activeId = id
            if (!runtime.consumeRetention(id, observer)) {
                runtime.failRetentionStart(id, "retention-start-unavailable")
                finish(id)
            } else {
                main.post(accessWatch)
            }
        } catch (failure: RuntimeException) {
            runtime.failRetentionStart(id, "retention-promotion-${failure.javaClass.simpleName.take(40)}")
            if (activeId == id) runtime.cancelAttempt(id)
            finish(id)
        }
        return START_NOT_STICKY
    }

    private fun finish(id: String) {
        if (activeId != null && activeId != id) return
        main.removeCallbacks(accessWatch)
        runtime.detachRetentionObserver(id, observer)
        activeId = null
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false }
        stopSelfResult(latestStartId)
    }

    override fun onDestroy() {
        main.removeCallbacks(accessWatch)
        activeId?.let { id ->
            runtime.cancelAttempt(id)
            runtime.detachRetentionObserver(id, observer)
        }
        activeId = null
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Defensive API35 callback, not a claim that specialUse has a timed-type budget.
        activeId?.let { runtime.cancelAttempt(it); finish(it) }
        stopSelfResult(startId)
    }

    private fun notification(id: String, status: String): Notification {
        ensureChannel(this)
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, SshActivity::class.java).setData(Uri.parse("cloudy-ssh://return/$id"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0,
            Intent(this, SshSessionService::class.java).setAction(STOP)
                .setData(Uri.parse("cloudy-ssh://disconnect/$id")).putExtra(EXTRA, id),
            PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_probe)
            .setContentTitle(getString(R.string.ssh_retention_notification_title))
            .setContentText(getString(R.string.ssh_retention_notification_text, status.take(80)))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.ssh_retention_disconnect), stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "ssh-finite-retention"
        private const val NOTIFICATION = 7301
        private const val EXTRA = "sshRetentionRequest"
        private const val START = "dev.cloudy.qualification.baseline.RETAIN_SSH"
        private const val STOP = "dev.cloudy.qualification.baseline.STOP_RETAINED_SSH"
        private fun canonical(id: String): Boolean = try { UUID.fromString(id).toString() == id }
            catch (_: IllegalArgumentException) { false }
        private fun manager(context: Context): NotificationManager = context.getSystemService(NotificationManager::class.java)
        private fun ensureChannel(context: Context) {
            manager(context).createNotificationChannel(NotificationChannel(CHANNEL,
                context.getString(R.string.ssh_retention_channel), NotificationManager.IMPORTANCE_LOW))
        }
        private fun notificationAllowed(context: Context): Boolean = try {
            ensureChannel(context)
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
                manager(context).areNotificationsEnabled() &&
                (manager(context).getNotificationChannel(CHANNEL)?.importance ?: NotificationManager.IMPORTANCE_NONE) != NotificationManager.IMPORTANCE_NONE
        } catch (_: RuntimeException) { false }

        /** True means one pending request accepted, never connected/authenticated/retained. */
        fun requestStart(activity: Activity, isStillVisible: () -> Boolean): Boolean {
            check(Looper.myLooper() == Looper.getMainLooper())
            val runtime = (activity.application as ProbeApplication).ssh
            if (!isStillVisible() || activity.isFinishing || activity.isDestroyed) return false
            if (!notificationAllowed(activity)) {
                runtime.reportRetentionAdmissionFailure("retention-notifications-unavailable")
                return false
            }
            val id = runtime.reserveRetention(isStillVisible) ?: return false
            return try {
                activity.startForegroundService(Intent(activity, SshSessionService::class.java).setAction(START)
                    .setData(Uri.parse("cloudy-ssh://retain/$id")).putExtra(EXTRA, id))
                true
            } catch (failure: RuntimeException) {
                runtime.failRetentionStart(id, "retention-launch-${failure.javaClass.simpleName.take(40)}")
                false
            }
        }
    }
}
