package io.github.baitian6641.cloudy.platform

import android.os.IBinder
import android.os.Parcel

/** Invoke only AFTER real caller/permission enforcement. No decoded data leaves this guard. */
internal object FilesHandoffParcel {
    fun validate(code: Int, data: Parcel, flags: Int, hasReply: Boolean) {
        val maximum = when (code) {
            IBinder.FIRST_CALL_TRANSACTION -> 1_024
            IBinder.FIRST_CALL_TRANSACTION + 1 -> HandoffJson.MAX_BYTES + 1_024
            IBinder.INTERFACE_TRANSACTION -> 0
            else -> deny()
        }
        // Includes descriptor/alignment overhead, before Java argument allocation. Binder has
        // already received the native parcel; this app-side cap cannot prevent that allocation.
        // Framework bookkeeping bits may accompany a synchronous call; only the public
        // one-way bit conflicts with this protocol's required request/reply shape.
        if ((flags and IBinder.FLAG_ONEWAY) != 0 || !hasReply ||
            data.hasFileDescriptors() || data.dataSize() > maximum) deny()
        if (code == IBinder.INTERFACE_TRANSACTION) return
        val copy = Parcel.obtain()
        try {
            copy.appendFrom(data, 0, data.dataSize())
            copy.setDataPosition(data.dataPosition())
            copy.enforceInterface(ICloudyFilesHandoff.DESCRIPTOR)
            when (code) {
                IBinder.FIRST_CALL_TRANSACTION -> {
                    copy.readInt()
                    if ((copy.readString()?.length ?: 0) > 36) deny()
                }
                IBinder.FIRST_CALL_TRANSACTION + 1 -> {
                    if ((copy.createByteArray()?.size ?: 0) > HandoffJson.MAX_BYTES) deny()
                }
            }
            // Selected build-tools36 generated code lacks this public API33 check. Do not edit
            // generated code or assume a different upstream generator matches the installed one.
            copy.enforceNoDataAvail()
        } catch (_: Exception) { deny() }
        finally { copy.recycle() }
    }

    private fun deny(): Nothing = throw SecurityException("Files handoff denied")
}
