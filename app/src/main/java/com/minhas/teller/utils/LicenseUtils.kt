package com.minhas.teller.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions
import com.minhas.teller.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object LicenseUtils {
    private const val PREFS_NAME = "LicensePrefs"
    private const val KEY_LICENSE_KEY = "license_key"
    private const val KEY_LICENSE_TYPE = "license_type"
    private const val KEY_TRIAL_START = "trial_start_timestamp"
    private const val KEY_LAST_REVALIDATE = "last_server_revalidate"
    private const val KEY_MAX_TIMESTAMP = "max_seen_timestamp"
    private const val TAG = "LicenseUtils"
    private const val TRIAL_PERIOD_DAYS = 45
    private val REVALIDATE_INTERVAL_MS = TimeUnit.HOURS.toMillis(24)
    private const val FUNCTIONS_REGION = "asia-southeast1" // must match functions/index.js

    // Lazy-initialised EncryptedSharedPreferences — wraps the license file with AES‑256‑GCM
    // backed by the AndroidKeyStore. Falls back to plain SharedPreferences on devices where
    // the Keystore is broken (e.g. some custom ROMs) so the app doesn't hard‑crash.
    @Volatile private var securePrefs: SharedPreferences? = null
    @Volatile private var securePrefsInitFailed: Boolean = false

    private fun getPrefs(context: Context): SharedPreferences {
        val cached = securePrefs
        if (cached != null) return cached
        if (securePrefsInitFailed) {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val encrypted = EncryptedSharedPreferences.create(
                PREFS_NAME, masterKeyAlias, context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            // One‑time migration: copy any plaintext values into the encrypted store, then wipe
            // the old plaintext file so the data can't be read via a simple file grab.
            val legacy = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (legacy.all.isNotEmpty()) {
                encrypted.edit().clear().apply()
                legacy.all.forEach { (k, v) ->
                    when (v) {
                        is String -> encrypted.edit().putString(k, v as String).apply()
                        is Long   -> encrypted.edit().putLong(k, v as Long).apply()
                        is Int    -> encrypted.edit().putInt(k, v as Int).apply()
                        is Boolean -> encrypted.edit().putBoolean(k, v as Boolean).apply()
                        is Float  -> encrypted.edit().putFloat(k, v as Float).apply()
                    }
                }
                legacy.edit().clear().apply()
                Log.i(TAG, "Migrated plaintext license prefs → EncryptedSharedPreferences")
            }
            securePrefs = encrypted
            encrypted
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences init failed, falling back to plain prefs", e)
            securePrefsInitFailed = true
            getPrefs(context)
        }
    }

    enum class LicenseType(val maxDevices: Int, val label: String) {
        SINGLE(1, "Single User"),
        MULTI_3(3, "Multi User (3 Devices)"),
        GOLD(10, "Gold User (10 Devices)");

        companion object {
            fun fromString(value: String): LicenseType {
                return try {
                    valueOf(value)
                } catch (e: Exception) {
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
        val activatedDevices: List<String>
    )

    fun getStoredLicenseKey(context: Context): String? {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_LICENSE_KEY, null)
    }

    fun getStoredLicenseType(context: Context): String {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_LICENSE_TYPE, "Unknown") ?: "Unknown"
    }

    fun getMachineId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return androidId?.uppercase() ?: "UNKNOWN-DEVICE"
    }

    // Returns a status object containing state and days remaining (if trial)
    suspend fun getAppStatus(context: Context): Pair<AppStatus, Int> {
        // Anti-tampering check: if APK signature or binary is tampered in release, lock down immediately
        if (SecurityUtils.isAppTampered(context)) {
            Log.w(TAG, "Tamper detected by SecurityUtils! Refusing access.")
            return Pair(AppStatus.NOT_LICENSED, 0)
        }

        // Debug build bypass
        if (BuildConfig.FORCE_FULL_LICENSE) {
            return Pair(AppStatus.LICENSED, 0)
        }

        // Play Integrity (anti-piracy): a decompiled + re-signed copy returns UNRECOGNIZED_VERSION,
        // while a genuine Play install returns PLAY_RECOGNIZED. Unevaluated/unavailable is
        // non-blocking so the app keeps working until the Cloud project number is configured.
        when (PlayIntegrityChecker.check(context)) {
            PlayIntegrityChecker.Verdict.Unrecognized -> {
                Log.w(TAG, "Play Integrity: app not Play-recognized (repackaged/sideloaded). Refusing access.")
                return Pair(AppStatus.NOT_LICENSED, 0)
            }
            else -> { /* PLAY_RECOGNIZED, UNEVALUATED, or UNAVAILABLE — continue */ }
        }

        val storedKey = getStoredLicenseKey(context)
        val machineId = getMachineId(context)

        // 1. Check Full License
        if (storedKey != null) {
            if (verifyLicenseOnServer(context, storedKey, machineId)) {
                return Pair(AppStatus.LICENSED, 0)
            }
        }

        // 2. Check Play Store License
        if (isInstalledFromPlayStore(context)) {
            if (provisionPlayStoreLicense(context, machineId)) {
                return Pair(AppStatus.LICENSED, 0)
            }
        }

        // 3. Check Trial Status
        return checkTrialStatus(context, machineId)
    }
    
    // For backward compatibility / simple boolean checks
    suspend fun checkLicenseStatus(context: Context): Boolean {
        val (status, _) = getAppStatus(context)
        return status == AppStatus.LICENSED || status == AppStatus.TRIAL_ACTIVE
    }

    private suspend fun checkTrialStatus(context: Context, machineId: String): Pair<AppStatus, Int> {
        val db = FirebaseDatabase.getInstance()
        val trialRef = db.getReference("trials").child(machineId)
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        
        // System Clock Rollback Prevention: Check if system time was rolled back to cheat trial
        val maxSeen = prefs.getLong(KEY_MAX_TIMESTAMP, 0L)
        if (maxSeen > 0L && now < (maxSeen - 300_000L)) { // 5 min grace threshold
            Log.w(TAG, "Clock manipulation hack detected! now=$now, maxSeen=$maxSeen")
            return Pair(AppStatus.TRIAL_EXPIRED, 0)
        }
        prefs.edit().putLong(KEY_MAX_TIMESTAMP, maxOf(now, maxSeen)).apply()

        try {
            var startTime = prefs.getLong(KEY_TRIAL_START, 0L)
            
            // If local not found or we want to be strict, check server
            if (startTime == 0L) {
                val snapshot = trialRef.get().await()
                if (snapshot.exists()) {
                    startTime = snapshot.getValue(Long::class.java) ?: 0L
                    prefs.edit().putLong(KEY_TRIAL_START, startTime).apply()
                } else {
                    // Start New Trial
                    startTime = now
                    trialRef.setValue(startTime).await()
                    prefs.edit().putLong(KEY_TRIAL_START, startTime).apply()
                }
            }
            
            val elapsedMillis = now - startTime
            val elapsedDays = TimeUnit.MILLISECONDS.toDays(elapsedMillis).toInt()
            val daysLeft = TRIAL_PERIOD_DAYS - elapsedDays
            
            return if (daysLeft > 0) {
                Pair(AppStatus.TRIAL_ACTIVE, daysLeft)
            } else {
                Pair(AppStatus.TRIAL_EXPIRED, 0)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking trial", e)
            // Fallback for offline: Use local prefs or package install time
            val installTime = try {
                context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
            } catch (ex: Exception) {
                now
            }
            
            val localStart = if (prefs.contains(KEY_TRIAL_START)) prefs.getLong(KEY_TRIAL_START, installTime) else installTime
            val elapsed = now - localStart
            val days = TimeUnit.MILLISECONDS.toDays(elapsed).toInt()
            val left = TRIAL_PERIOD_DAYS - days
            
             return if (left > 0) {
                Pair(AppStatus.TRIAL_ACTIVE, left)
            } else {
                Pair(AppStatus.TRIAL_EXPIRED, 0)
            }
        }
    }

    private suspend fun verifyLicenseOnServer(context: Context, key: String, machineId: String): Boolean {
        if (key.startsWith("OFFLINE-BYPASS-")) {
            return BuildConfig.DEBUG || BuildConfig.FORCE_FULL_LICENSE
        }
        val db = FirebaseDatabase.getInstance()
        val licenseRef = db.getReference("licenses").child(key)

        return try {
            val snapshot = licenseRef.get().await()
            if (snapshot.exists()) {
                val devicesSnapshot = snapshot.child("devices")
                var isDeviceRegistered = false

                for (device in devicesSnapshot.children) {
                    if (device.value.toString() == machineId) {
                        isDeviceRegistered = true
                        break
                    }
                }

                if (!isDeviceRegistered) {
                    // Key exists but this device is not registered (maybe it was removed/surrendered externally?)
                    // Or local pref exists but server doesn't have it.
                    clearLocalLicense(context)
                    return false
                }

                // A previous revalidateLicense call (or the daily server-side sweep) may have
                // already flagged this license inactive — e.g. the subscription was cancelled or
                // refunded. Respect that immediately rather than waiting for our own next check.
                val activeFlag = snapshot.child("active").getValue(Boolean::class.java)
                if (activeFlag == false) {
                    clearLocalLicense(context)
                    return false
                }

                val typeStr = snapshot.child("type").getValue(String::class.java) ?: "SINGLE"
                val source = snapshot.child("source").getValue(String::class.java)

                // For Play-verified subscriptions/lifetime purchases, periodically re-confirm with
                // Google via the revalidateLicense Cloud Function instead of trusting the cached
                // Realtime Database row forever — this is what actually makes a cancelled or
                // refunded subscription stop working instead of granting access permanently after
                // a single successful purchase.
                if (source == "PLAY_STORE_VERIFIED" && shouldRevalidate(context)) {
                    val stillActive = revalidateWithServer(key)
                    markRevalidated(context)
                    if (!stillActive) {
                        clearLocalLicense(context)
                        return false
                    }
                }

                saveLicenseLocally(context, key, typeStr)
                return true
            } else {
                // License key invalid
                clearLocalLicense(context)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying license", e)
            // Fallback: If network error, assume valid if locally stored to avoid lockout offline
            // In a real strict app, you might want to block or use a cached validity timestamp.
            return true
        }
    }

    private fun shouldRevalidate(context: Context): Boolean {
        val prefs = getPrefs(context)
        val last = prefs.getLong(KEY_LAST_REVALIDATE, 0L)
        return System.currentTimeMillis() - last > REVALIDATE_INTERVAL_MS
    }

    private fun markRevalidated(context: Context) {
        getPrefs(context)
            .edit().putLong(KEY_LAST_REVALIDATE, System.currentTimeMillis()).apply()
    }

    private suspend fun revalidateWithServer(licenseKey: String): Boolean {
        return try {
            val functions = FirebaseFunctions.getInstance(FUNCTIONS_REGION)
            val data = hashMapOf("licenseKey" to licenseKey)
            val result = functions.getHttpsCallable("revalidateLicense").call(data).await()
            @Suppress("UNCHECKED_CAST")
            val response = result.data as? Map<String, Any?>
            (response?.get("active") as? Boolean) ?: true
        } catch (e: Exception) {
            Log.e(TAG, "revalidateLicense call failed, keeping cached state", e)
            true // don't punish the user for our network/API hiccup
        }
    }

    suspend fun activateLicense(context: Context, inputKey: String): Pair<Boolean, String> {
        val machineId = getMachineId(context)
        val db = FirebaseDatabase.getInstance()
        val licenseRef = db.getReference("licenses").child(inputKey)

        return try {
            val snapshot = licenseRef.get().await()
            if (!snapshot.exists()) {
                return Pair(false, "Invalid License Key")
            }

            val typeStr = snapshot.child("type").getValue(String::class.java) ?: "SINGLE"
            val type = LicenseType.fromString(typeStr)
            
            val devicesSnapshot = snapshot.child("devices")
            val currentDevices = mutableListOf<String>()
            var alreadyActive = false

            for (child in devicesSnapshot.children) {
                val devId = child.value.toString()
                currentDevices.add(devId)
                if (devId == machineId) alreadyActive = true
            }

            if (alreadyActive) {
                saveLicenseLocally(context, inputKey, typeStr)
                return Pair(true, "License already active on this device.")
            }

            if (currentDevices.size >= type.maxDevices) {
                return Pair(false, "License limit reached (${type.maxDevices} devices). Please surrender license from another device.")
            }

            // Register device in the first free slot 0..maxDevices-1. database.rules.json only lets a
            // client fill an empty slot below the plan's device limit (and never overwrite another
            // device), so after a surrender the freed slot must be reused rather than appending.
            val usedSlots = devicesSnapshot.children.mapNotNull { it.key?.toIntOrNull() }.toSet()
            val newIndex = (0 until type.maxDevices).firstOrNull { it !in usedSlots }
                ?: return Pair(false, "License limit reached (${type.maxDevices} devices). Please surrender license from another device.")
            licenseRef.child("devices").child(newIndex.toString()).setValue(machineId).await()
            
            saveLicenseLocally(context, inputKey, typeStr)
            return Pair(true, "Activation Successful!")

        } catch (e: Exception) {
            Log.e(TAG, "Activation failed", e)
            return Pair(false, "Network error: ${e.message}")
        }
    }

    /**
     * Used to treat "installed from the Play Store" itself as proof of purchase — it generated a
     * free SINGLE license for literally any user whose installer package was com.android.vending,
     * with no check that they had ever paid anything. Since almost every real-world install comes
     * from the Play Store, this silently gave every user full access forever: the trial never had
     * a chance to expire (this check runs before the trial check in getAppStatus()), and the
     * in-app "Upgrade" prompts never showed because the app already believed the user was
     * licensed. That's the "app doesn't force people to buy" / "it was working for free" bug.
     *
     * Fixed: being installed from the Play Store proves nothing on its own. This now (1) checks
     * for a license already issued to this device via a real verified purchase, and if there
     * isn't one, (2) asks Google Play Billing what this device has actually bought, and (3) sends
     * any owned purchase to the verifyPurchase Cloud Function, which checks it against the Play
     * Developer API server-side before a license is ever created. No purchase found = no license.
     */
    private suspend fun provisionPlayStoreLicense(context: Context, machineId: String): Boolean {
        val db = FirebaseDatabase.getInstance()
        val userLookupRef = db.getReference("play_store_users").child(machineId)

        try {
            val snapshot = userLookupRef.get().await()
            if (snapshot.exists()) {
                val key = snapshot.getValue(String::class.java)
                if (key != null && verifyLicenseOnServer(context, key, machineId)) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "play_store_users lookup failed", e)
        }

        // No previously-issued key for this device — check what Google Play Billing says this
        // device actually owns before granting anything.
        val ownedPurchases = try {
            queryOwnedPlayPurchases(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query owned Play purchases", e)
            emptyList()
        }
        if (ownedPurchases.isEmpty()) return false

        val functions = FirebaseFunctions.getInstance(FUNCTIONS_REGION)
        for (purchase in ownedPurchases) {
            val productId = purchase.products.firstOrNull() ?: continue
            try {
                val data = hashMapOf(
                    "purchaseToken" to purchase.purchaseToken,
                    "productId" to productId,
                    "machineId" to machineId
                )
                val result = functions.getHttpsCallable("verifyPurchase").call(data).await()
                @Suppress("UNCHECKED_CAST")
                val response = result.data as? Map<String, Any?>
                val licenseKey = response?.get("licenseKey") as? String
                if (!licenseKey.isNullOrBlank()) {
                    saveLicenseLocally(context, licenseKey, "PREMIUM")
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "verifyPurchase failed for owned purchase ($productId)", e)
            }
        }
        return false
    }

    /**
     * One-shot, read-only Billing connection used purely to ask "what has this device already
     * bought" — separate from PlayBillingManager, which drives the interactive purchase UI.
     */
    private suspend fun queryOwnedPlayPurchases(context: Context): List<Purchase> =
        suspendCancellableCoroutine { cont ->
            val client = BillingClient.newBuilder(context)
                .setListener { _, _ -> /* not used for a read-only entitlement check */ }
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                )
                .build()

            cont.invokeOnCancellation { runCatching { client.endConnection() } }

            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        client.endConnection()
                        if (cont.isActive) cont.resume(emptyList())
                        return
                    }

                    val owned = mutableListOf<Purchase>()
                    var pending = 2
                    fun finishOne(purchases: List<Purchase>) {
                        owned.addAll(purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED })
                        pending--
                        if (pending == 0) {
                            client.endConnection()
                            if (cont.isActive) cont.resume(owned)
                        }
                    }

                    client.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
                    ) { _, purchases -> finishOne(purchases) }

                    client.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
                    ) { _, purchases -> finishOne(purchases) }
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(emptyList())
                }
            })
        }

    suspend fun surrenderLicense(context: Context): Pair<Boolean, String> {
        val key = getStoredLicenseKey(context) ?: return Pair(false, "No license found")
        val machineId = getMachineId(context)
        val db = FirebaseDatabase.getInstance()
        val licenseRef = db.getReference("licenses").child(key).child("devices")

        return try {
            val snapshot = licenseRef.get().await()
            var keyToRemove: String? = null
            
            for (child in snapshot.children) {
                if (child.value.toString() == machineId) {
                    keyToRemove = child.key
                    break
                }
            }

            if (keyToRemove != null) {
                licenseRef.child(keyToRemove).removeValue().await()
                clearLocalLicense(context)
                return Pair(true, "License Surrendered Successfully")
            } else {
                clearLocalLicense(context) // Local mismatch, just clear
                return Pair(true, "License removed locally (not found on server)")
            }
        } catch (e: Exception) {
            return Pair(false, "Error: ${e.message}")
        }
    }

    fun activateOfflineBypass(context: Context) {
        if (BuildConfig.DEBUG || BuildConfig.FORCE_FULL_LICENSE) {
            val machineId = getMachineId(context)
            saveLicenseLocally(context, "OFFLINE-BYPASS-$machineId", "GOLD")
        }
    }

    fun activateLicenseLocally(context: Context, key: String, type: String) {
        saveLicenseLocally(context, key, type)
    }

    private fun saveLicenseLocally(context: Context, key: String, type: String) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_LICENSE_KEY, key)
            .putString(KEY_LICENSE_TYPE, type)
            .apply()
    }

    private fun clearLocalLicense(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().remove(KEY_LICENSE_KEY).remove(KEY_LICENSE_TYPE).apply()
    }

    private fun isInstalledFromPlayStore(context: Context): Boolean {
        return try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            installer == "com.android.vending"
        } catch (e: Exception) {
            false
        }
    }

}
