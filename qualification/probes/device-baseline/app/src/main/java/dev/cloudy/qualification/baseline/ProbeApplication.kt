package dev.cloudy.qualification.baseline

import android.app.Application

class ProbeApplication : Application() {
    internal val recorder: ProbeRecorder by lazy { ProbeRecorder(this) }
}
