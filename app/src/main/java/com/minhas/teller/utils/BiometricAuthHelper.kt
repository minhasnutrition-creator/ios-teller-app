package com.minhas.teller.utils

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object BiometricAuthHelper {

    private const val TAG = "BiometricAuthHelper"
    private const val PREFS_NAME = "teller_security_prefs"
    private const val KEY_APP_LOCK_ENABLED = "app_lock_biometric_enabled"

    // In-memory app lock reactive state for Compose UI
    var isSessionLocked by mutableStateOf(false)
        private set

    fun isAppLockEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        if (!enabled) {
            isSessionLocked = false
        }
    }

    fun lockSession() {
        isSessionLocked = true
    }

    fun unlockSession() {
        isSessionLocked = false
    }

    /**
     * Checks if the device has biometric or screen lock hardware set up.
     */
    fun canAuthenticate(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isKeyguardSecure == true
    }

    /**
     * Triggers device biometric authentication (Fingerprint, Face, or device PIN/pattern fallback).
     */
    fun authenticate(
        activity: Activity,
        title: String = "Unlock Teller",
        subtitle: String = "Verify identity to access financial records",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!canAuthenticate(activity)) {
            Log.w(TAG, "Device keyguard is not secured")
            unlockSession()
            onSuccess()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            authenticateWithBiometricPrompt(activity, title, subtitle, onSuccess, onError)
        } else {
            // Pre-API 28 Keyguard fallback
            val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val intent = keyguardManager?.createConfirmDeviceCredentialIntent(title, subtitle)
            if (intent != null) {
                activity.startActivity(intent)
                unlockSession()
                onSuccess()
            } else {
                unlockSession()
                onSuccess()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun authenticateWithBiometricPrompt(
        activity: Activity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cancellationSignal = CancellationSignal()
        val executor = activity.mainExecutor

        val promptBuilder = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            promptBuilder.setDeviceCredentialAllowed(true)
        } else {
            promptBuilder.setNegativeButton(
                "Cancel",
                executor,
                DialogInterface.OnClickListener { _, _ ->
                    cancellationSignal.cancel()
                    onError("Authentication cancelled")
                }
            )
        }

        val prompt = promptBuilder.build()

        prompt.authenticate(
            cancellationSignal,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    unlockSession()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.w(TAG, "Biometric error: $errorCode - $errString")
                    onError(errString?.toString() ?: "Authentication failed")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Biometric authentication failed (mismatch)")
                }
            }
        )
    }
}
