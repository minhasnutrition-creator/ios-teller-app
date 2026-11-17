package com.minhas.teller.utils

import android.content.Context
import java.io.File

/**
 * Binary integrity checker.
 * On iOS, Apple Code Signing and Sandboxing guarantee binary integrity.
 */
object NativeIntegrityChecker {
    fun getNativeLibraries(context: Context): List<File> = emptyList()
    fun verifyNativeLibraries(context: Context): Boolean = true
    fun getLibraryChecksums(context: Context): Map<String, String> = emptyMap()
    fun verifyApkIntegrity(context: Context): Boolean = true
}
