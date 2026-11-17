package com.minhas.teller.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Security utility for anti-tampering, root detection, and integrity checks
 */
object SecurityUtils {

    private const val TAG = "SecurityUtils"

    /**
     * Compose-reactive state flag — set when root/tamper is detected.
     * Composables reading this will recompose when it flips to true.
     */
    var isCompromised by mutableStateOf(false)
        private set

    fun flagCompromisedDevice(rooted: Boolean, tampered: Boolean) {
        if (rooted || tampered) isCompromised = true
    }
    
    // List of known root/superuser related files and packages
    private val ROOT_INDICATORS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/su/su.d",
        "/su/bin",
        "/system/app/Superuser.apk",
        "/system/app/SuperSU/SuperSU.apk",
        "/system/app/Magisk/Magisk.apk",
        "/data/adb/magisk",
        "/sbin/.magisk",
        "/dev/.magisk.unblock",
        "/system/bin/magisk",
        "/system/xbin/magisk"
    )
    
    private val ROOT_PACKAGES = listOf(
        "com.koushikdutta.superuser",
        "com.koushikdutta.rommanager",
        "com.koushikdutta.rommanager.license",
        "com.dimonvideo.luckypatcher",
        "com.chelpus.lackypatch",
        "com.ramdroid.appquarantine",
        "com.ramdroid.appquarantinepro",
        "com.devadvance.rootcloak",
        "com.devadvance.rootcloakplus",
        "de.robv.android.xposed.installer",
        "com.saurik.substrate",
        "com.zachspong.temprootremovejb",
        "com.amphoras.hidemyroot",
        "com.amphoras.hidemyrootadfree",
        "com.formyhm.hiderootPremium",
        "com.formyhm.hideroot",
        "me.phh.superuser",
        "eu.chainfire.supersu",
        "com.kingouser.com",
        "com.topjohnwu.magisk"
    )
    
    /**
     * Check if device is rooted
     */
    fun isDeviceRooted(context: Context): Boolean {
        // Check for test-keys
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            Log.w(TAG, "Root detected: test-keys found in build tags")
            return true
        }
        
        // Check for root files
        for (path in ROOT_INDICATORS) {
            if (File(path).exists()) {
                Log.w(TAG, "Root detected: $path exists")
                return true
            }
        }
        
        // Check for root packages
        val pm = context.packageManager
        for (packageName in ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(packageName, 0)
                Log.w(TAG, "Root detected: $packageName installed")
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // Package not found - expected
            }
        }
        
        // Try to execute su command and check exit code (0 = su found = rooted)
        return try {
            val p1 = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            if (p1.waitFor() == 0) {
                Log.w(TAG, "Root detected: 'which su' succeeded (xbin)")
                return true
            }
            val p2 = Runtime.getRuntime().exec(arrayOf("/system/bin/which", "su"))
            if (p2.waitFor() == 0) {
                Log.w(TAG, "Root detected: 'which su' succeeded (bin)")
                return true
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Root check exception: ${e.message}")
            false
        }
    }
    
    /**
     * Check if app is running in debug mode
     */
    fun isDebugMode(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    
    /**
     * Check if debugger is attached
     */
    fun isDebuggerAttached(): Boolean {
        return android.os.Debug.isDebuggerConnected() || java.lang.Thread.currentThread().stackTrace.any { 
            it.className.contains("android.app.Instrumentation") || 
            it.className.contains("androidx.test")
        }
    }
    
    /**
     * Check if running in emulator
     */
    fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.HARDWARE.contains("goldfish")
            || Build.HARDWARE.contains("ranchu")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.PRODUCT.contains("sdk")
            || Build.PRODUCT.contains("google_sdk")
            || Build.PRODUCT.contains("sdk_x86")
            || Build.PRODUCT.contains("sdk_gphone64")
            || Build.PRODUCT.contains("vbox86p")
            || Build.BOARD.lowercase().contains("nox")
            || Build.BOOTLOADER.lowercase().contains("nox")
            || Build.HARDWARE.lowercase().contains("nox")
            || Build.PRODUCT.lowercase().contains("nox")
    }
    
    /**
     * Verify APK signature integrity
     */
    fun verifySignature(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val packageName = context.packageName
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, 
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo?.apkContentsSigners?.isNotEmpty() == true
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 
                    PackageManager.GET_SIGNATURES
                ).signatures?.isNotEmpty() == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification failed: ${e.message}")
            false
        }
    }
    
    /**
     * Calculate checksum of the APK file
     */
    fun calculateApkChecksum(context: Context): String? {
        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageInfo(context.packageName, 0)
            val apkPath = packageInfo.applicationInfo?.sourceDir ?: return null
            
            val file = File(apkPath)
            if (!file.exists()) return null
            
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Checksum calculation failed: ${e.message}")
            null
        }
    }
    
    /**
     * Check if dynamic hooking frameworks (Frida, Xposed, Substrate, LSPosed) are loaded in process memory
     */
    fun isHookingDetected(): Boolean {
        return try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                val content = mapsFile.readText().lowercase()
                val indicators = listOf("frida", "xposed", "substrate", "sandhook", "edxposed", "lsposed", "libgadget")
                for (indicator in indicators) {
                    if (content.contains(indicator)) {
                        Log.w(TAG, "Hooking framework detected in process memory: $indicator")
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Hooking check error: ${e.message}")
            false
        }
    }

    /**
     * Check if app has been tampered (signature checks & hook detection)
     * Debug builds are allowed - skip tamper check
     */
    fun isAppTampered(context: Context): Boolean {
        // Debug builds are allowed - skip tamper check
        if (isDebugMode(context)) {
            return false
        }
        
        // Check signature on release builds only
        if (!verifySignature(context)) {
            Log.w(TAG, "App tampering: Signature verification failed")
            return true
        }

        // Check for active memory hooking (Frida, Xposed, etc.)
        if (isHookingDetected()) {
            Log.w(TAG, "App tampering: Dynamic memory hooking detected")
            return true
        }
        
        return false
    }
    
    /**
     * Perform all security checks
     */
    fun performSecurityChecks(context: Context): SecurityStatus {
        return SecurityStatus(
            isRooted = isDeviceRooted(context),
            isDebug = isDebugMode(context),
            isEmulator = isEmulator(),
            isDebuggerAttached = isDebuggerAttached(),
            isTampered = isAppTampered(context),
            signatureValid = verifySignature(context),
            nativeLibsValid = NativeIntegrityChecker.verifyNativeLibraries(context)
        )
    }
    
    data class SecurityStatus(
        val isRooted: Boolean,
        val isDebug: Boolean,
        val isEmulator: Boolean,
        val isDebuggerAttached: Boolean,
        val isTampered: Boolean,
        val signatureValid: Boolean,
        val nativeLibsValid: Boolean = true
    ) {
        val isSecure: Boolean
            get() = if (isDebug) {
                // Debug builds: only check native libs validity
                nativeLibsValid
            } else {
                // Release builds: full security check
                !isTampered && signatureValid && !isDebuggerAttached && nativeLibsValid
            }
    }
}
