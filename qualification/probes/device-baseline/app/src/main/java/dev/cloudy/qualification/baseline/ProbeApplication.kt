package dev.cloudy.qualification.baseline

import android.app.Application

class ProbeApplication : Application() {
    internal val recorder: ProbeRecorder by lazy { ProbeRecorder(this) }
    internal val engagement: AndroidEngagementRuntime by lazy { AndroidEngagementRuntime() }
    internal val transfers: TransferRuntime by lazy { TransferRuntime(this) }
    internal val ssh: SshRuntime by lazy { SshRuntime(this) }
    internal val terminal: TerminalRuntime by lazy { TerminalRuntime(this) }
}
