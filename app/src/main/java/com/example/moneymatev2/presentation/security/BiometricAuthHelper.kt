package com.example.moneymatev2.presentation.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import com.example.moneymatev2.R

internal object BiometricAuthHelper {
    const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun canAuthenticate(context: Context): Int =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)

    fun isAvailable(status: Int) = status == BiometricManager.BIOMETRIC_SUCCESS

    fun statusMessage(status: Int): Int = when (status) {
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> R.string.security_bio_no_hardware
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> R.string.security_bio_not_enrolled
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> R.string.security_bio_temporarily_unavailable
        else -> R.string.security_bio_unavailable
    }

    fun findFragmentActivity(context: Context): FragmentActivity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is FragmentActivity) return current
            current = current.baseContext
        }
        return current as? FragmentActivity
    }

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancel: String,
        onSuccess: () -> Unit,
        onError: (Int, CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errorCode, errString)
                override fun onAuthenticationFailed() = onFailed()
            })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle(title).setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setNegativeButtonText(cancel).build())
    }

    fun isCancellation(code: Int) = code == BiometricPrompt.ERROR_USER_CANCELED ||
        code == BiometricPrompt.ERROR_NEGATIVE_BUTTON || code == BiometricPrompt.ERROR_CANCELED
}
