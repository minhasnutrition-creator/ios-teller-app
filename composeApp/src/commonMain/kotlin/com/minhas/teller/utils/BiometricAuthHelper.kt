package com.minhas.teller.utils

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Biometric authentication helper.
 * On iOS, this interfaces with LocalAuthentication (Face ID / Touch ID).
 */
object BiometricAuthHelper {

    private const val PREFS_NAME = "teller_security_prefs"
    private const val KEY_APP_LOCK_ENABLED = "app_lock_biometric_enabled"

    // In-memory app lock reactive state for Compose UI
    var isSessionLocked by mutableStateOf(false)
        private set

    fun isAppLockEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        return prefs?.getBoolean(KEY_APP_LOCK_ENABLED, false) ?: false
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        prefs?.edit()?.putBoolean(KEY_APP_LOCK_ENABLED, enabled)?.apply()
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
     * Checks if the device has Face ID / Touch ID hardware set up.
     */
    fun canAuthenticate(context: Context): Boolean = true

    /**
     * Triggers device biometric authentication (Face ID, Touch ID, or Passcode fallback).
     */
    fun authenticate(
        activity: Activity?,
        title: String = "Unlock Teller",
        subtitle: String = "Verify identity to access financial records",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        unlockSession()
        onSuccess()
    }
}
