package com.minhas.teller.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Native library integrity checker
 * Verifies that native libraries (.so files) haven't been tampered with
 */
object NativeIntegrityChecker {
    
    private const val TAG = "NativeIntegrity"
    
    /**
     * Calculate SHA-256 checksum of a file
     */
    private fun calculateFileChecksum(file: File): String? {
        return try {
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
            Log.e(TAG, "Failed to calculate checksum: ${e.message}")
            null
        }
    }
    
    /**
     * Get list of native libraries from the APK
     */
    fun getNativeLibraries(context: Context): List<File> {
        val nativeLibs = mutableListOf<File>()
        
        try {
            // Check JNI libs directory
            val jniDir = File(context.applicationInfo.nativeLibraryDir)
            if (jniDir.exists() && jniDir.isDirectory) {
                jniDir.listFiles()?.filter { it.name.endsWith(".so") }?.let {
                    nativeLibs.addAll(it)
                }
            }
            
            // Also check app lib directory
            val libDir = context.getDir("libs", Context.MODE_PRIVATE)
            if (libDir.exists() && libDir.isDirectory) {
                libDir.walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".so") }
                    .forEach { nativeLibs.add(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting native libraries: ${e.message}")
        }
        
        return nativeLibs.distinct()
    }
    
    /**
     * Verify native library integrity
     * In a real production app, you would compare against known good checksums
     */
    fun verifyNativeLibraries(context: Context): Boolean {
        val libs = getNativeLibraries(context)
        
        if (libs.isEmpty()) {
            Log.w(TAG, "No native libraries found - possible tampering")
            return false
        }
        
        // Check each library
        for (lib in libs) {
            if (!lib.exists() || !lib.canRead()) {
                Log.e(TAG, "Native library inaccessible: ${lib.name}")
                return false
            }
            
            val checksum = calculateFileChecksum(lib)
            if (checksum == null) {
                Log.e(TAG, "Failed to verify: ${lib.name}")
                return false
            }
            
            Log.d(TAG, "Library ${lib.name} checksum: $checksum")
        }
        
        return true
    }
    
    /**
     * Get the checksums of all native libraries
     * These can be stored and compared on subsequent runs
     */
    fun getLibraryChecksums(context: Context): Map<String, String> {
        val checksums = mutableMapOf<String, String>()
        
        for (lib in getNativeLibraries(context)) {
            calculateFileChecksum(lib)?.let { checksum ->
                checksums[lib.name] = checksum
            }
        }
        
        return checksums
    }
    
    /**
     * Verify that the APK hasn't been modified by checking its integrity
     */
    fun verifyApkIntegrity(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val packageName = context.packageName
            
            // Get package info
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            
            // Check if app is debug
            val appInfo = packageInfo.applicationInfo
            if (appInfo != null && (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                Log.e(TAG, "Debug flag set - tampering detected")
                return false
            }
            
            // Verify signatures
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            
            if (signatures.isNullOrEmpty()) {
                Log.e(TAG, "No signatures found - tampering detected")
                return false
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "APK integrity verification failed: ${e.message}")
            false
        }
    }
}
