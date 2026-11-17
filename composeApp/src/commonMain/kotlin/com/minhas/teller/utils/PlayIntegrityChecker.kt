package com.minhas.teller.utils

import android.content.Context

/**
 * App integrity check for iOS.
 * DeviceCheck / App Attest handles this on iOS; returns Recognized by default.
 */
object PlayIntegrityChecker {

    sealed class Verdict {
        object Recognized : Verdict()
        object Unrecognized : Verdict()
        object Unevaluated : Verdict()
        object Unavailable : Verdict()
    }

    suspend fun check(context: Context): Verdict = Verdict.Recognized
}
