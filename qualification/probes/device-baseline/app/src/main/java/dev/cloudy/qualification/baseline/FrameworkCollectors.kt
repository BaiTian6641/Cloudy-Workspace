package dev.cloudy.qualification.baseline

import android.app.Activity
import android.content.Context
import android.graphics.Insets
import android.graphics.Rect
import android.hardware.input.InputManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.InputDevice
import android.view.WindowInsets
import android.view.WindowMetrics
import org.json.JSONArray
import org.json.JSONObject

/** Framework reports are observations, never decode/input/performance test results. */
internal object FrameworkCollectors {
    private const val MAX_INPUTS = 64
    private const val MAX_CODECS = 128
    private const val MAX_CODEC_BYTES = 256 * 1024

    fun build(): JSONObject = collect {
        JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("product", Build.PRODUCT)
            .put("device", Build.DEVICE)
            .put("hardware", Build.HARDWARE)
            .put("buildId", Build.ID)
            .put("buildDisplay", Build.DISPLAY)
            .put("buildFingerprint", Build.FINGERPRINT)
            .put("release", Build.VERSION.RELEASE)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("securityPatch", Build.VERSION.SECURITY_PATCH)
            .put("incremental", Build.VERSION.INCREMENTAL)
            .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("identifierPolicy", "No serial, Android ID, account, network identity, or input descriptor is queried.")
    }

    fun inputs(context: Context): JSONObject = collect {
        val manager = context.getSystemService(InputManager::class.java)
            ?: return@collect JSONObject().put("status", "unavailable")
        val ids = manager.inputDeviceIds
        val records = JSONArray()
        for (id in ids.take(MAX_INPUTS)) {
            // IDs are used only to query the current list and are never retained.
            records.put(collect input@{
                val device = manager.getInputDevice(id)
                    ?: return@input JSONObject().put("status", "removed-during-query")
                JSONObject()
                    .put("sourcesMask", device.sources)
                    .put("keyboardType", device.keyboardType)
                    .put("virtual", device.isVirtual)
                    .put("external", device.isExternal)
                    .put("sourceClasses", JSONArray(SOURCE_CLASSES.filter {
                        device.sources and it.second == it.second
                    }.map { it.first }))
            })
        }
        JSONObject().put("reportedCount", ids.size)
            .put("omittedCount", (ids.size - MAX_INPUTS).coerceAtLeast(0))
            .put("devices", records)
            .put("scope", "Source classes only; no input names, descriptors, vendor/product IDs, key contents, or behavioral qualification.")
    }

    /** Called only on the recorder worker: vendor enumeration may block. */
    fun decoders(): JSONObject = collect {
        val decoders = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter {
            !it.isEncoder && it.supportedTypes.any { type -> type.startsWith("video/") }
        }
        val records = JSONArray()
        var bytes = 0
        var omitted = 0
        for (info in decoders) {
            if (records.length() >= MAX_CODECS) {
                omitted++
                continue
            }
            val record = collect {
                val types = info.supportedTypes.filter { it.startsWith("video/") }
                val media = JSONArray()
                for (type in types.take(8)) {
                    media.put(collect {
                        val capabilities = info.getCapabilitiesForType(type)
                        val profiles = JSONArray()
                        capabilities.profileLevels.take(16).forEach {
                            profiles.put(JSONObject().put("profile", it.profile).put("level", it.level))
                        }
                        val value = JSONObject()
                            .put("mime", type.take(256))
                            .put("maxSupportedInstancesReported", capabilities.maxSupportedInstances)
                            .put("lowLatencyReported", capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency))
                            .put("lowLatencyRequiredReported", capabilities.isFeatureRequired(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency))
                            .put("securePlaybackReported", capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback))
                            .put("securePlaybackRequiredReported", capabilities.isFeatureRequired(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback))
                            .put("tunneledPlaybackReported", capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback))
                            .put("tunneledPlaybackRequiredReported", capabilities.isFeatureRequired(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback))
                            .put("profileLevels", profiles)
                            .put("omittedProfileLevels", (capabilities.profileLevels.size - 16).coerceAtLeast(0))
                        capabilities.videoCapabilities?.let { video ->
                            value.put("videoRangesReported", JSONObject()
                                .put("widths", video.supportedWidths.toString())
                                .put("heights", video.supportedHeights.toString())
                                .put("frameRates", video.supportedFrameRates.toString())
                                .put("bitrates", video.bitrateRange.toString())
                                .put("widthAlignment", video.widthAlignment)
                                .put("heightAlignment", video.heightAlignment))
                        }
                        value
                    }.put("mime", type.take(256)))
                }
                JSONObject().put("name", info.name.take(256))
                    .put("canonicalName", info.canonicalName.take(256))
                    .put("alias", info.isAlias)
                    .put("hardwareAcceleratedReported", info.isHardwareAccelerated)
                    .put("softwareOnlyReported", info.isSoftwareOnly)
                    .put("vendorReported", info.isVendor)
                    .put("types", media)
                    .put("omittedTypes", (types.size - 8).coerceAtLeast(0))
            }
            val length = record.toString().toByteArray(Charsets.UTF_8).size
            if (bytes + length > MAX_CODEC_BYTES) {
                omitted++
            } else {
                records.put(record)
                bytes += length
            }
        }
        JSONObject().put("reportedVideoDecoderCount", decoders.size)
            .put("recordedDecoderCount", records.length())
            .put("omittedDecoderCount", omitted)
            .put("enumeration", "MediaCodecList.ALL_CODECS, including aliases and special/secure codecs")
            .put("mediaScope", "video/* MIME types only; audio decoders are outside this probe")
            .put("scope", "Manufacturer/framework reports only. No codec instantiated, stream decoded, throughput measured, or hardware flag independently verified.")
            .put("decoders", records)
    }

    /** Window APIs need the Activity's visual context; capture on its main thread. */
    fun window(activity: Activity): JSONObject = collect {
        val configuration = activity.resources.configuration
        val display = activity.display
        JSONObject()
            .put("currentMetrics", metrics(activity.windowManager.currentWindowMetrics))
            .put("maximumMetrics", metrics(activity.windowManager.maximumWindowMetrics))
            .put("decorInsets", activity.window.decorView.rootWindowInsets?.let(::insets)
                ?: JSONObject().put("status", "not-yet-dispatched"))
            .put("decorWidthPx", activity.window.decorView.width)
            .put("decorHeightPx", activity.window.decorView.height)
            .put("multiWindow", activity.isInMultiWindowMode)
            .put("windowFocus", activity.hasWindowFocus())
            .put("configuration", JSONObject()
                .put("densityDpi", configuration.densityDpi)
                .put("fontScale", configuration.fontScale)
                .put("screenWidthDp", configuration.screenWidthDp)
                .put("screenHeightDp", configuration.screenHeightDp)
                .put("orientation", configuration.orientation)
                .put("keyboard", configuration.keyboard)
                .put("hardKeyboardHidden", configuration.hardKeyboardHidden))
            .put("display", if (display == null) JSONObject().put("status", "unavailable") else {
                JSONObject().put("status", "reported")
                    .put("currentModePhysicalWidthPx", display.mode.physicalWidth)
                    .put("currentModePhysicalHeightPx", display.mode.physicalHeight)
                    .put("refreshRateReportedHz", display.refreshRate)
                    .put("rotation", display.rotation)
            })
    }

    fun insets(value: WindowInsets): JSONObject {
        val caption = WindowInsets.Type.captionBar()
        val captionBounds = value.getBoundingRects(caption)
        return JSONObject()
            .put("systemBarsPx", sides(value.getInsets(WindowInsets.Type.systemBars())))
            .put("displayCutoutPx", sides(value.getInsets(WindowInsets.Type.displayCutout())))
            .put("imePx", sides(value.getInsets(WindowInsets.Type.ime())))
            .put("imeVisible", value.isVisible(WindowInsets.Type.ime()))
            .put("captionPx", sides(value.getInsets(caption)))
            .put("captionVisible", value.isVisible(caption))
            .put("captionBoundingRectsPx", JSONArray(captionBounds.take(32).map(::rect)))
            .put("omittedCaptionRects", (captionBounds.size - 32).coerceAtLeast(0))
            .put("boundingRectCoordinates", "Relative to the inset frame, not screen coordinates; frame can differ from final layout.")
            .put("insetFrameWidthPx", value.frame.width)
            .put("insetFrameHeightPx", value.frame.height)
    }

    private fun metrics(value: WindowMetrics): JSONObject = JSONObject()
        .put("boundsPx", rect(value.bounds)).put("density", value.density)
        .put("insets", insets(value.windowInsets))

    private fun rect(value: Rect): JSONObject = JSONObject()
        .put("left", value.left).put("top", value.top)
        .put("right", value.right).put("bottom", value.bottom)

    private fun sides(value: Insets): JSONObject = JSONObject()
        .put("left", value.left).put("top", value.top)
        .put("right", value.right).put("bottom", value.bottom)

    private fun collect(block: () -> JSONObject): JSONObject = try {
        block().also { if (!it.has("status")) it.put("status", "reported") }
    } catch (failure: RuntimeException) {
        error(failure)
    } catch (failure: LinkageError) {
        error(failure)
    }

    private fun error(failure: Throwable): JSONObject = JSONObject()
        .put("status", "error").put("errorType", failure.javaClass.simpleName)

    private val SOURCE_CLASSES = listOf(
        "keyboard" to InputDevice.SOURCE_KEYBOARD,
        "mouse" to InputDevice.SOURCE_MOUSE,
        "relative-mouse" to InputDevice.SOURCE_MOUSE_RELATIVE,
        "touchpad" to InputDevice.SOURCE_TOUCHPAD,
        "touchscreen" to InputDevice.SOURCE_TOUCHSCREEN,
        "stylus" to InputDevice.SOURCE_STYLUS,
        "gamepad" to InputDevice.SOURCE_GAMEPAD,
        "joystick" to InputDevice.SOURCE_JOYSTICK,
    )
}
