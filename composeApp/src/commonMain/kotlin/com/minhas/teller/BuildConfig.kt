package com.minhas.teller

/**
 * Multiplatform build configuration for Teller iOS.
 */
object BuildConfig {
    // Named IS_DEBUG, not DEBUG: Kotlin/Native exports every public const as an Obj-C
    // @property verbatim, and Xcode's Debug configuration #defines a `DEBUG` macro, so a
    // property literally named DEBUG makes the generated header fail to parse itself.
    const val IS_DEBUG = true
    const val APPLICATION_ID = "com.minhas.teller"
    const val BUILD_TYPE = "debug"
    const val VERSION_CODE = 657
    const val VERSION_NAME = "6.7.43"

    const val FORCE_FULL_LICENSE = true
    const val GOOGLE_WEB_CLIENT_ID = ""
    const val DEEPSEEK_API_KEY = ""
    const val GEMINI_API_KEY = ""
}
