package io.github.baitian6641.cloudy.identity

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import java.util.UUID

enum class VaultPromptCode { BUSY, NOT_VISIBLE, WRONG_OWNER, UNAVAILABLE, CANCELLED, TIMEOUT, AUTH_FAILED, WRONG_AUTHENTICATOR, DISPOSED }

sealed interface VaultPromptResult {
    data class Authorized(val grant: VaultGrant) : VaultPromptResult
    data class Rejected(val code: VaultPromptCode) : VaultPromptResult
}

/** Main-thread prompt owner; no secret or endpoint crosses the framework prompt. */
class VaultPromptController(private val activity: FragmentActivity) : DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private val authority = VaultAuthorization(activity.packageName, UUID.randomUUID(), SystemClock::elapsedRealtime)
    private var disposed = false
    // Reattach only the session that existed when this Activity was created.
    private var prompt = createPrompt(active)

    private fun createPrompt(expected: Session?): BiometricPrompt =
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // AndroidX reattaches callbacks after recreation. Dispatch to the
                // originating slot, never whichever request a new Activity wants.
                val session = expected ?: return
                if (!sessions.retire(session)) return
                session.owner.handler.removeCallbacks(session.timeout)
                if (session.delivered) return
                if (result.authenticationType != BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL) {
                    session.owner.authority.cancel()
                    session.deliver(VaultPromptResult.Rejected(VaultPromptCode.WRONG_AUTHENTICATOR))
                    return
                }
                try {
                    val grant = session.owner.authority.succeeded(session.attempt)
                    session.deliver(VaultPromptResult.Authorized(grant))
                } catch (_: VaultAuthorizationException) {
                    session.deliver(VaultPromptResult.Rejected(VaultPromptCode.CANCELLED))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val session = expected ?: return
                if (!sessions.retire(session)) return
                session.owner.handler.removeCallbacks(session.timeout)
                session.owner.authority.cancel()
                session.deliver(VaultPromptResult.Rejected(VaultPromptCode.AUTH_FAILED))
            }
        })

    init {
        mainThread()
        activity.lifecycle.addObserver(this)
        authority.setVisible(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    fun request(operation: VaultOperation, id: CredentialId, binding: VaultBinding,
                onResult: (VaultPromptResult) -> Unit) {
        mainThread()
        authority.cancel()
        val previous = active
        if (previous != null) {
            previous.owner.revoke(VaultPromptCode.CANCELLED)
            onResult(VaultPromptResult.Rejected(VaultPromptCode.BUSY))
            return
        }
        val rejection = when {
            disposed -> VaultPromptCode.DISPOSED
            binding.ownerPackage != activity.packageName -> VaultPromptCode.WRONG_OWNER
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> VaultPromptCode.NOT_VISIBLE
            !canAuthenticate() -> VaultPromptCode.UNAVAILABLE
            else -> null
        }
        if (rejection != null) {
            onResult(VaultPromptResult.Rejected(rejection))
            return
        }
        authority.setVisible(true)
        val attempt = authority.begin(operation, id, binding)
        val session = Session(this, attempt, onResult)
        check(sessions.begin(session))
        handler.postDelayed(session.timeout, VaultAuthorization.PROMPT_TIMEOUT_MS)
        try {
            prompt = createPrompt(session)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(when (operation) {
                    VaultOperation.SAVE -> R.string.cloudy_vault_save_title
                    VaultOperation.USE -> R.string.cloudy_vault_use_title
                    VaultOperation.DELETE -> R.string.cloudy_vault_delete_title
                }))
                .setSubtitle(activity.getString(R.string.cloudy_vault_authorize_subtitle))
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(info)
        } catch (_: RuntimeException) {
            // Admission may have crossed framework entry; quarantine until a
            // terminal callback rather than assigning late success to another use.
            revoke(VaultPromptCode.UNAVAILABLE)
        }
    }

    fun cancel() {
        mainThread()
        revoke(VaultPromptCode.CANCELLED)
    }

    override fun onStart(owner: LifecycleOwner) {
        authority.setVisible(!disposed)
    }

    override fun onStop(owner: LifecycleOwner) {
        // Even configuration stops revoke this instance's authority. Focus loss
        // alone is deliberately ignored: normal system authentication obscures UI.
        authority.setVisible(false)
        revoke(VaultPromptCode.CANCELLED)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        disposed = true
        authority.setVisible(false)
        revoke(VaultPromptCode.DISPOSED)
        activity.lifecycle.removeObserver(this)
    }

    private fun canAuthenticate(): Boolean = try {
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
    } catch (_: RuntimeException) {
        false
    }

    private fun revoke(code: VaultPromptCode) {
        authority.cancel() // Synchronous, before framework cancellation/callback.
        val session = active
        if (session?.owner !== this) return
        handler.removeCallbacks(session.timeout)
        try {
            session.deliver(VaultPromptResult.Rejected(code))
        } finally {
            try {
                prompt.cancelAuthentication()
            } catch (_: RuntimeException) {
                // Keep the slot occupied: cancellation failure is not retirement.
            }
        }
    }

    private class Session(val owner: VaultPromptController, val attempt: VaultAttempt,
                          private var callback: ((VaultPromptResult) -> Unit)?) {
        var delivered = false
            private set
        val timeout = Runnable { owner.revoke(VaultPromptCode.TIMEOUT) }
        fun deliver(result: VaultPromptResult) {
            if (delivered) return
            delivered = true
            val receiver = callback
            callback = null
            receiver?.invoke(result)
        }
    }

    companion object {
        // Per app process, not cross-package. A cancelling framework session must
        // drain before another window can authenticate. No timeout fakes an ACK.
        private val sessions = PromptSessionState<Session>()
        private val active: Session? get() = sessions.current
        private fun mainThread() = check(Looper.myLooper() == Looper.getMainLooper())
    }
}
