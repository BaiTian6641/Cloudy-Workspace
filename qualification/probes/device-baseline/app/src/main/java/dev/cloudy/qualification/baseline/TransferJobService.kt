package dev.cloudy.qualification.baseline

import android.app.job.JobParameters
import android.app.job.JobService

/** Framework callbacks stay on main and never perform payload, hash or journal IO. */
class TransferJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val runtime = TransferRuntime.get(applicationContext)
        try {
            // Required on every UIDT delivery, within ten seconds; notification
            // permission denial is reported by the screen, not a scheduling gate.
            setNotification(params, TransferRuntime.NOTIFICATION_ID,
                runtime.notification(params.extras.getString(TransferRuntime.ATTEMPT_EXTRA)),
                JOB_END_NOTIFICATION_POLICY_REMOVE)
        } catch (failure: RuntimeException) {
            runtime.notificationFailed(params, failure)
            return false
        }
        return runtime.beginJob(this, params)
    }

    override fun onStopJob(params: JobParameters): Boolean {
        TransferRuntime.get(applicationContext).stopJob(params)
        return false // This diagnostic never requests an automatic retry.
    }

    override fun onNetworkChanged(params: JobParameters) {
        // A different assigned network is an interrupted attempt, not migration.
        TransferRuntime.get(applicationContext).networkChanged(params)
    }

    override fun onDestroy() {
        TransferRuntime.get(applicationContext).serviceDestroyed(this)
        super.onDestroy()
    }
}
