package dev.cloudy.qualification.usb

/** Symbol-name JNI is deliberately stable; release builds do not rename this boundary. */
internal object NativeUsb : LeaseOwner.NativePort {
    init { System.loadLibrary("cloudyusb") }
    external fun version(): String
    override external fun open(fd: Int): Long
    override external fun describe(handle: Long): String
    override external fun close(handle: Long)
}
