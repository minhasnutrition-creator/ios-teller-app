package com.minhas.teller.utils

import android.content.Context
import com.minhas.teller.compat.SharedPreferences

/**
 * License and subscription manager for iOS.
 */
object LicenseUtils {
    private const val PREFS_NAME = "LicensePrefs"
    private const val KEY_LICENSE_KEY = "license_key"
    private const val KEY_LICENSE_TYPE = "license_type"

    /** In iOS builds, full features are unlocked by default. */
    const val FORCE_FULL_LICENSE = true

    private fun getPrefs(context: Context): SharedPreferences? {
        return context.getSharedPreferences(PREFS_NAME, 0)
    }

    enum class LicenseType(val maxDevices: Int, val label: String) {
        SINGLE(1, "Single User"),
        MULTI_3(3, "Multi User (3 Devices)"),
        GOLD(10, "Gold User (10 Devices)");

        companion object {
            fun fromString(value: String): LicenseType {
                return try {
                    valueOf(value)
                } catch (_: Exception) {
                    SINGLE
                }
            }
        }
    }

    enum class AppStatus {
        LICENSED,
        TRIAL_ACTIVE,
        TRIAL_EXPIRED,
        NOT_LICENSED
    }

    data class LicenseInfo(
        val key: String,
        val type: LicenseType,
        val activatedDevices: List<String> = emptyList()
    )

    fun getStoredLicenseKey(context: Context): String? {
        return getPrefs(context)?.getString(KEY_LICENSE_KEY, null) ?: "IOS-LICENSED-KEY"
    }

    fun getStoredLicenseType(context: Context): String {
        return getPrefs(context)?.getString(KEY_LICENSE_TYPE, "GOLD") ?: "GOLD"
    }

    fun getMachineId(context: Context): String {
        return "ios_device_id"
    }

    suspend fun getAppStatus(context: Context): Pair<AppStatus, Int> {
        return Pair(AppStatus.LICENSED, 365)
    }

    suspend fun checkLicenseStatus(context: Context): Boolean = true

    suspend fun activateLicense(context: Context, inputKey: String): Pair<Boolean, String> {
        saveLicenseLocally(context, inputKey, "GOLD")
        return Pair(true, "License activated successfully.")
    }

    suspend fun surrenderLicense(context: Context): Pair<Boolean, String> {
        clearLocalLicense(context)
        return Pair(true, "License released.")
    }

    fun activateOfflineBypass(context: Context) {
        saveLicenseLocally(context, "OFFLINE_BYPASS", "GOLD")
    }

    fun activateLicenseLocally(context: Context, key: String, type: String) {
        saveLicenseLocally(context, key, type)
    }

    private fun saveLicenseLocally(context: Context, key: String, type: String) {
        getPrefs(context)?.edit()
            ?.putString(KEY_LICENSE_KEY, key)
            ?.putString(KEY_LICENSE_TYPE, type)
            ?.apply()
    }

    private fun clearLocalLicense(context: Context) {
        getPrefs(context)?.edit()
            ?.remove(KEY_LICENSE_KEY)
            ?.remove(KEY_LICENSE_TYPE)
            ?.apply()
    }
}
