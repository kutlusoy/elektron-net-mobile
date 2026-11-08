package org.elektronnet.mobile.ui.security

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object Biometric {
    val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }

    fun available(context: Context): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isDeviceSecure == true) return true
        val bm = BiometricManager.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val resCombo = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            if (resCombo == BiometricManager.BIOMETRIC_SUCCESS) return true
            val resCred = bm.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            if (resCred == BiometricManager.BIOMETRIC_SUCCESS) return true
        }
        val resStrong = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (resStrong == BiometricManager.BIOMETRIC_SUCCESS) return true
        val resWeak = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        return resWeak == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun confirmDeviceCredentialIntent(context: Context, title: String? = null, description: String? = null): Intent? {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return null
        return keyguard.createConfirmDeviceCredentialIntent(title, description)
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        negative: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {},
        onFallbackToCredential: (() -> Unit)? = null,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (onFallbackToCredential != null && (
                        errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                        errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT ||
                        errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                        errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                        errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE
                    )) {
                        onFallbackToCredential()
                    } else {
                        onFailure()
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(authenticators)
            .apply { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) setNegativeButtonText(negative) }
            .build()
        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            if (onFallbackToCredential != null) {
                onFallbackToCredential()
            } else {
                onFailure()
            }
        }
    }
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is FragmentActivity) return ctx
        ctx = (ctx as? ContextWrapper)?.baseContext
    }
    return null
}
