package com.minhas.teller

/**
 * Platform abstraction layer for Teller Multiplatform.
 * Defines interfaces and expect functions for platform-specific capabilities.
 */
interface Platform {
    val name: String
    val osVersion: String
    val isIOS: Boolean
}

expect fun getPlatform(): Platform

/**
 * Native platform utilities for alerts, sharing, file paths, and external actions.
 */
expect object PlatformUtils {
    fun showToast(message: String)
    fun shareText(text: String, title: String = "")
    fun openUrl(url: String)
    fun getAppDataDirectory(): String
    fun getAppVersion(): String
}
