package dev.cloudy.qualification.usb

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Visible, finite diagnostic: leaving the window closes access; no service retains USB. */
class UsbActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var manager: UsbManager
    private lateinit var status: TextView
    private lateinit var devices: RadioGroup
    @Volatile private var owner: LeaseOwner? = null // accessed only on worker
    private var selection: UsbDevice? = null
    @Volatile private var generation = 0L // main-thread selection/permission generation
    private val permissionRequests = PermissionRequests()
    private var nativeStatus = "Native initialization pending"
    private var inspectQueued = false
    private val cleanup = CleanupAdmission()
    private var ready = false
    @Volatile private var visible = false
    @Volatile private var destroyed = false
    private val permissionNonce = java.util.UUID.randomUUID().toString()
    private val permissionAction get() = "$packageName.USB_PERMISSION.$permissionNonce"
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (device?.deviceId == selection?.deviceId) { cancel("Device removed; previous access invalidated"); selection = null }
                    refresh()
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> refresh()
                permissionAction -> {
                    // Immutable PendingIntent retains creator extras; Android's fill-in extras
                    // are not required for correlation or the authoritative grant decision.
                    val request = permissionRequests.complete(
                        intent.getLongExtra("requestGeneration", -1),
                        intent.getIntExtra("requestDeviceId", -1),
                        intent.getStringExtra("requestNonce"), generation, selection?.deviceId
                    ) ?: return
                    val current = manager.deviceList.values.firstOrNull { it.deviceId == request.deviceId }
                    show(if (current != null && eligible(current) && manager.hasPermission(current)) "USB access granted; Inspect is now available" else "USB access denied, ineligible or device removed")
                }
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        manager = getSystemService(UsbManager::class.java)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(column) }
        val padding = (16 * resources.displayMetrics.density).toInt()
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            insets
        }
        fun text(resource: Int, size: Float) = TextView(this).apply {
            setText(resource); textSize = size; setTextColor(Color.rgb(24, 36, 39)); column.addView(this)
        }
        text(R.string.title, 22f).setAccessibilityHeading(true)
        text(R.string.scope, 16f)
        fun button(label: Int, action: () -> Unit) = Button(this).apply { setText(label); setOnClickListener { action() }; column.addView(this) }
        button(R.string.refresh) { refresh() }
        text(R.string.devices, 18f).setAccessibilityHeading(true)
        devices = RadioGroup(this).also { column.addView(it) }
        button(R.string.grant) { requestAccess() }
        button(R.string.inspect) { inspect() }
        button(R.string.close) { cancel("Access closed / pending request cancelled") }
        status = text(R.string.initial, 16f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE; setTextIsSelectable(true) }
        setContentView(scroll)
        registerReceiver(receiver, IntentFilter().apply { addAction(permissionAction); addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }, RECEIVER_NOT_EXPORTED)
        worker.execute {
            try {
                val native = NativeUsb
                val version = native.version()
                owner = LeaseOwner(native)
                main.post { if (!destroyed) { ready = true; nativeStatus = version; refresh() } }
            } catch (failure: LinkageError) { main.post { show("Native library unavailable: ${failure.javaClass.simpleName}") } }
            catch (failure: Exception) { main.post { show("Native initialization failed: ${failure.message}") } }
        }
    }

    override fun onStart() { super.onStart(); visible = true }
    override fun onStop() { visible = false; cancel("Window hidden: access closed; refresh before resuming"); super.onStop() }
    override fun onDestroy() {
        destroyed = true
        unregisterReceiver(receiver)
        worker.execute { owner?.close(); owner = null }
        worker.shutdown()
        super.onDestroy()
    }

    private fun show(message: String) {
        if (destroyed) return
        status.text = getString(R.string.diagnostic_report, nativeStatus, message, BuildConfig.QUALIFICATION_BUILD_REF)
    }

    private fun eligible(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_HUB) return false
        return (0 until device.interfaceCount).none {
            val intf = device.getInterface(it)
            intf.interfaceClass == UsbConstants.USB_CLASS_HUB ||
                (intf.interfaceClass == 255 && intf.interfaceSubclass == 66 && intf.interfaceProtocol == 1)
        }
    }

    private fun refresh() {
        val observed = manager.deviceList.values.sortedBy { it.deviceId }
        if (selection != null && observed.none { it.deviceId == selection?.deviceId }) { cancel("Selected device is absent"); selection = null }
        devices.removeAllViews()
        for (device in observed) {
            devices.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = getString(R.string.device_row, device.deviceId, device.vendorId, device.productId, device.interfaceCount, if (eligible(device)) "" else getString(R.string.excluded))
                isEnabled = eligible(device)
                isChecked = selection?.deviceId == device.deviceId
                setOnClickListener { cancel("Selection changed; existing access closed"); selection = device; show(getString(R.string.selection, device.deviceId, device.vendorId, device.productId)) }
            })
        }
        val host = packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        show("USB host feature=$host; observed devices=${observed.size}; native ready=$ready. No forwarding qualification.")
    }

    private fun requestAccess() {
        val device = selection ?: return show("Select an identified peripheral first")
        if (!visible || !ready || !eligible(device)) return show("Access unavailable in the current state")
        if (manager.hasPermission(device)) return show("USB access already granted; choose Inspect")
        cancel("Requesting Android USB permission")
        val ticket = generation
        val request = permissionRequests.begin(ticket, device.deviceId, java.util.UUID.randomUUID().toString())
        val intent = Intent(permissionAction).setPackage(packageName)
            .putExtra("requestGeneration", request.generation)
            .putExtra("requestDeviceId", request.deviceId)
            .putExtra("requestNonce", request.nonce)
        val result = PendingIntent.getBroadcast(this, ticket.toInt(), intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
        try { manager.requestPermission(device, result) }
        catch (failure: RuntimeException) { permissionRequests.cancel(); show("Permission request failed: ${failure.javaClass.simpleName}") }
        main.postDelayed({ if (permissionRequests.isPending(request)) cancel("Permission request timed out; late reply will be ignored") }, 30_000)
    }

    private fun inspect() {
        val device = selection ?: return show("Select an identified peripheral first")
        if (!visible || !ready || !eligible(device)) return show("Inspection unavailable in the current state")
        if (!manager.hasPermission(device)) return show("USB permission required; request access first")
        if (inspectQueued) return show("Previous inspection is still finishing; cancel or wait")
        inspectQueued = true
        cancel("Inspecting selected device; no interface claim or transfer")
        val ticket = generation
        worker.execute {
            val lease = owner
            if (lease == null) { main.post { inspectQueued = false }; return@execute }
            val nativeTicket = lease.invalidate()
            try {
                val report = lease.inspect(nativeTicket) {
                    // A cancellation queued before this worker must not open a fresh FD.
                    if (!isCurrent(ticket)) null else {
                        val current = manager.deviceList.values.firstOrNull { it.deviceId == device.deviceId }
                        val connection = if (current != null && manager.hasPermission(current)) manager.openDevice(current) else null
                        connection?.let { value -> object : LeaseOwner.Connection {
                            override val fd get() = value.fileDescriptor
                            override fun close() = value.close()
                        } }
                    }
                }
                if (!isCurrent(ticket)) lease.close()
                main.post { if (ticket == generation && visible) show(report) }
            } catch (failure: Exception) { main.post { if (ticket == generation) show("Inspection failed: ${failure.message}") } }
            finally { main.post { inspectQueued = false } }
        }
        main.postDelayed({ if (ticket == generation) cancel("Inspection lease expired; access closed") }, 15_000)
    }

    @Synchronized private fun isCurrent(ticket: Long): Boolean = ticket == generation && visible && !destroyed
    private fun cancel(message: String) {
        synchronized(this) { generation += 1 }
        permissionRequests.cancel()
        owner?.invalidate()
        // Invalidation is checked before and after slow opens; cleanup remains serialized.
        val ticket = generation
        show("$message; cleanup requested")
        cleanup.submit({ action -> worker.execute { action() } }) {
            try {
                owner?.close()
                main.post { if (ticket == generation) show("$message; cleanup complete") }
            } catch (failure: Exception) {
                main.post { if (ticket == generation) show("Cleanup failed: ${failure.message}") }
            }
        }
    }
}
