package io.github.baitian6641.cloudy.testing.ime

import android.content.pm.ApplicationInfo
import android.inputmethodservice.InputMethodService
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputBinding
import android.view.inputmethod.InputConnection
import java.util.UUID

/** Headless test-only IME. Never constructs a View InputConnection, never edits an unmatched target. */
class QualificationImeService : InputMethodService() {
    private val instance = UUID.randomUUID().toString()
    private var generation = 1L
    private var target: Target? = null
    private var compositionOwner: CompositionOwner? = null
    private val consumed = LinkedHashSet<String>()

    private data class Target(
        val packageName: String,
        val clientPid: Int,
        val clientUid: Int,
        val fieldId: Int,
        val inputType: Int,
        val imeOptions: Int,
    )

    private data class CompositionOwner(
        val connection: InputConnection,
        val clientUid: Int,
        val clientPid: Int,
        val packageName: String,
        val fieldId: Int,
    )

    private val component: String get() = "$packageName/${ImeFixtureProtocol.SERVICE_CLASS}"

    override fun onCreate() {
        super.onCreate()
        current = this
        publishState(null)
    }

    override fun onCreateInputView(): View? = null
    override fun onCreateCandidatesView(): View? = null
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return false // Headless qualification input never displays a keyboard window.
    }
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onBindInput() {
        super.onBindInput()
        // A binding transition invalidates the previous target until the next onStartInput.
        cancelOwnedPreedit()
        generation += 1
        target = null
        publishState(null)
    }

    override fun onStartInput(attributes: EditorInfo, restarting: Boolean) {
        // Restart does not call onFinishInput; clear only our still-owned old composition.
        cancelOwnedPreedit()
        super.onStartInput(attributes, restarting)
        generation += 1
        val binding = currentInputBinding
        val connection = currentInputConnection
        target = if (eligible(attributes, binding, connection)) {
            Target(
                packageName = attributes.packageName.toString(),
                clientPid = checkNotNull(binding).pid,
                clientUid = binding.uid,
                fieldId = attributes.fieldId,
                inputType = attributes.inputType,
                imeOptions = attributes.imeOptions,
            )
        } else {
            null
        }
        publishState(target)
    }

    override fun onFinishInput() {
        // The base implementation finishes composition. Do not commit unsubmitted text when
        // cancellation is refused or this callback no longer owns an editable target.
        cancelOwnedPreedit()
        target = null
        retire()
    }

    override fun onUnbindInput() {
        cancelOwnedPreedit()
        target = null
        retire()
        super.onUnbindInput()
    }

    override fun onDestroy() {
        cancelOwnedPreedit()
        target = null
        retire()
        if (current === this) current = null
        super.onDestroy()
    }

    private fun eligible(
        attributes: EditorInfo,
        binding: InputBinding?,
        connection: InputConnection?,
    ): Boolean {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return false
        if (binding == null || connection == null) return false
        if (binding.uid != Process.myUid()) return false
        if (attributes.packageName?.toString() != packageName) return false
        if (attributes.fieldId <= 0) return false
        return eligibleInputType(attributes.inputType)
    }

    private fun eligibleInputType(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        if (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0) return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
    }

    private fun retire() {
        generation += 1
        publishState(null)
    }

    private fun publishState(activeTarget: Target?) {
        runCatching {
            ImeFixtureProtocol.writeState(this, instance, generation, activeTarget?.let {
                ImeTargetState(
                    packageName = it.packageName,
                    clientPid = it.clientPid,
                    clientUid = it.clientUid,
                    fieldId = it.fieldId,
                    inputType = it.inputType,
                    imeOptions = it.imeOptions,
                    hasConnection = currentInputConnection != null,
                )
            })
        }.onFailure { Log.w(TAG, "state_publication_failed") }
    }

    private fun cancelOwnedPreedit() {
        val owner = compositionOwner ?: return
        compositionOwner = null
        val binding = currentInputBinding ?: return
        val editor = currentInputEditorInfo ?: return
        if (binding.uid != owner.clientUid || binding.pid != owner.clientPid ||
            editor.packageName != owner.packageName || editor.fieldId != owner.fieldId) return
        runCatching { owner.connection.setComposingText("", 1) }
    }

    /** Claim even malformed input before decoding; a corrected same-ID payload is still a replay. */
    internal fun claim(requestId: String): ImeFixtureResult.Reason? {
        if (consumed.contains(requestId)) return ImeFixtureResult.Reason.DUPLICATE_REQUEST
        if (consumed.size >= ImeFixtureProtocol.MAX_REQUESTS) return ImeFixtureResult.Reason.REQUEST_LIMIT
        consumed += requestId
        return null
    }

    internal fun dispatch(request: FixtureRequest, lease: LeaseSnapshot): ImeDispatchResult {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            return rejected(ImeFixtureResult.Reason.NOT_DEBUGGABLE)
        }
        val now = SystemClock.elapsedRealtime()
        if (now >= request.expiresElapsedMillis ||
            request.expiresElapsedMillis - now > ImeFixtureProtocol.MAX_REQUEST_AGE_MILLIS
        ) {
            return rejected(ImeFixtureResult.Reason.EXPIRED_REQUEST)
        }
        if (request.run != lease.run || lease.component != component) {
            return rejected(ImeFixtureResult.Reason.LEASE_MISMATCH)
        }
        if (request.instance != instance) {
            return rejected(ImeFixtureResult.Reason.STALE_INSTANCE)
        }
        if (request.operation == ImeFixtureOperation.RESTORE_PREVIOUS) return restore()
        if (lease.ownerPid != request.clientPid) {
            return rejected(ImeFixtureResult.Reason.TARGET_MISMATCH)
        }
        if (request.generation != generation) {
            return rejected(ImeFixtureResult.Reason.STALE_GENERATION)
        }
        val cached = target ?: return rejected(ImeFixtureResult.Reason.NO_ACTIVE_INPUT)
        if (request.targetPackage != cached.packageName ||
            request.clientPid != cached.clientPid ||
            request.fieldId != cached.fieldId
        ) {
            return rejected(ImeFixtureResult.Reason.TARGET_MISMATCH)
        }
        // Effect-boundary revalidation: bind may precede onStartInput and swap the connection.
        val binding = currentInputBinding ?: return rejected(ImeFixtureResult.Reason.NO_ACTIVE_INPUT)
        val editor = currentInputEditorInfo ?: return rejected(ImeFixtureResult.Reason.NO_ACTIVE_INPUT)
        val connection = currentInputConnection ?: return rejected(ImeFixtureResult.Reason.NO_ACTIVE_INPUT)
        if (binding.uid != Process.myUid() || binding.pid != request.clientPid) {
            return rejected(ImeFixtureResult.Reason.TARGET_MISMATCH)
        }
        if (editor.packageName?.toString() != request.targetPackage) {
            return rejected(ImeFixtureResult.Reason.TARGET_MISMATCH)
        }
        if (editor.fieldId != request.fieldId || !eligibleInputType(editor.inputType)) {
            return rejected(ImeFixtureResult.Reason.TARGET_MISMATCH)
        }
        if (cached.clientUid != binding.uid ||
            cached.packageName != editor.packageName?.toString() ||
            cached.fieldId != editor.fieldId
        ) {
            return rejected(ImeFixtureResult.Reason.TARGET_MISMATCH)
        }
        val text = request.text
        return when (request.operation) {
            ImeFixtureOperation.COMMIT -> {
                if (text == null) return rejected(ImeFixtureResult.Reason.INVALID_TEXT)
                if (connection.commitText(text, 1)) {
                    compositionOwner = null
                    submitted(text)
                } else {
                    refused(ImeFixtureResult.Reason.COMMIT_REFUSED)
                }
            }
            ImeFixtureOperation.COMPOSE -> {
                if (text == null) return rejected(ImeFixtureResult.Reason.INVALID_TEXT)
                if (connection.setComposingText(text, 1)) {
                    compositionOwner = if (text.isEmpty()) null else CompositionOwner(
                        connection = connection,
                        clientUid = binding.uid,
                        clientPid = binding.pid,
                        packageName = request.targetPackage,
                        fieldId = request.fieldId,
                    )
                    submitted(text)
                } else {
                    refused(ImeFixtureResult.Reason.COMPOSE_REFUSED)
                }
            }
            ImeFixtureOperation.FINISH_COMPOSITION -> {
                if (connection.finishComposingText()) {
                    compositionOwner = null
                    submitted(null)
                } else refused(ImeFixtureResult.Reason.FINISH_REFUSED)
            }
            ImeFixtureOperation.ENTER -> {
                if (enter(connection)) submitted(null) else refused(ImeFixtureResult.Reason.KEY_REFUSED)
            }
            ImeFixtureOperation.INTERRUPT -> {
                if (interrupt(connection)) submitted(null) else refused(ImeFixtureResult.Reason.KEY_REFUSED)
            }
            ImeFixtureOperation.RESTORE_PREVIOUS ->
                throw IllegalStateException("restore handled before target validation")
        }
    }

    private fun restore(): ImeDispatchResult {
        if (Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) != component) {
            return rejected(ImeFixtureResult.Reason.NOT_SELECTED)
        }
        return if (switchToPreviousInputMethod()) {
            ImeDispatchResult(ImeFixtureResult.Outcome.SUBMITTED, null)
        } else {
            refused(ImeFixtureResult.Reason.RESTORE_REFUSED)
        }
    }

    private fun submitted(text: String?): ImeDispatchResult {
        val encoded = text?.let { ImeFixtureProtocol.utf8(it) }
        return ImeDispatchResult(
            outcome = ImeFixtureResult.Outcome.SUBMITTED,
            reason = null,
            textLength = encoded?.size,
            textSha256 = encoded?.let { ImeFixtureProtocol.sha256(it) },
        )
    }

    private fun refused(reason: ImeFixtureResult.Reason) =
        ImeDispatchResult(ImeFixtureResult.Outcome.API_REFUSED, reason)

    private fun rejected(reason: ImeFixtureResult.Reason) =
        ImeDispatchResult(ImeFixtureResult.Outcome.REJECTED, reason)

    private fun send(
        connection: InputConnection,
        downTime: Long,
        action: Int,
        code: Int,
        meta: Int,
    ): Boolean {
        val event = KeyEvent(
            downTime,
            SystemClock.uptimeMillis(),
            action,
            code,
            0,
            meta,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
        )
        return try { connection.sendKeyEvent(event) } catch (failure: RuntimeException) { false }
    }

    private fun enter(connection: InputConnection): Boolean {
        val downTime = SystemClock.uptimeMillis()
        val down = send(connection, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0)
        val up = send(connection, downTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0)
        return down && up
    }

    private fun interrupt(connection: InputConnection): Boolean {
        val held = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        val downTime = SystemClock.uptimeMillis()
        val controlDown = send(connection, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, held)
        val characterDown = send(connection, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, held)
        val characterUp = send(connection, downTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_C, held)
        val controlUp = send(connection, downTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0)
        return controlDown && characterDown && characterUp && controlUp
    }

    companion object {
        private const val TAG = "QualificationIme"

        @Volatile
        internal var current: QualificationImeService? = null
    }
}
