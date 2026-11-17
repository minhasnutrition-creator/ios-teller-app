package com.minhas.teller.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Play Integrity anti-piracy check.
 *
 * Asks Google's Play Integrity API whether this app is the genuine, unmodified build that was
 * installed from Google Play. A decompiled + re-signed (pirated) copy returns
 * UNRECOGNIZED_VERSION; a legitimate Play install returns PLAY_RECOGNIZED. This is the standard
 * mechanism for stopping "someone decompiled my app and made it free".
 *
 * Note: this is a *client-side* check — it raises the bar against casual pirates but a determined
 * attacker can still patch it out. For the strongest protection, send the integrity token to your
 * own backend and verify it server-side. See LicenseUtils.getAppStatus for where this is wired.
 */
object PlayIntegrityChecker {

    private const val TAG = "PlayIntegrity"

    /**
     * Your Google Cloud project NUMBER (not the project ID). This is the GCP project number
     * backing the Firebase project "teller-89a94" (extracted from the Firebase app ID
     * "1:350286656706:android:..."). Play Integrity requires this field; requests without it
     * are rejected.
     *
     * This project must also be linked to the app in Play Console (Setup → App integrity →
     * Play Integrity API → Link Cloud project) for the verdict to resolve. Until then the check
     * returns Unevaluated/Unavailable, which is non-blocking, so nothing locks out.
     */
    private const val CLOUD_PROJECT_NUMBER = 350286656706L

    sealed class Verdict {
        /** PLAY_RECOGNIZED — the genuine Play install, unmodified. */
        object Recognized : Verdict()

        /** UNRECOGNIZED_VERSION — repackaged / re-signed / sideloaded copy. */
        object Unrecognized : Verdict()

        /** UNEVALUATED — no verdict (e.g. not configured, or no Play data for this device). */
        object Unevaluated : Verdict()

        /** Play Services or the Integrity API is unavailable. */
        object Unavailable : Verdict()
    }

    /** Returns the app-recognition verdict, or [Verdict.Unevaluated] when not configured. */
    suspend fun check(context: Context): Verdict {
        if (CLOUD_PROJECT_NUMBER <= 0L) return Verdict.Unevaluated

        return suspendCancellableCoroutine { cont ->
            try {
                val manager = IntegrityManagerFactory.create(context.applicationContext)
                val request = IntegrityTokenRequest.builder()
                    .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                    .setNonce(java.util.UUID.randomUUID().toString())
                    .build()

                manager.requestIntegrityToken(request)
                    .addOnSuccessListener { response ->
                        if (cont.isActive) cont.resume(parse(response.token()))
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Integrity token request failed", e)
                        if (cont.isActive) cont.resume(Verdict.Unavailable)
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Integrity check unavailable", e)
                if (cont.isActive) cont.resume(Verdict.Unavailable)
            }
        }
    }

    /** Decode the JWT and read appIntegrity.appRecognitionVerdict. */
    private fun parse(token: String): Verdict {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return Verdict.Unavailable
            val payload = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
                Charsets.UTF_8
            )
            val verdict = JSONObject(payload)
                .optJSONObject("appIntegrity")
                ?.optString("appRecognitionVerdict")
                ?: "UNEVALUATED"
            when (verdict) {
                "PLAY_RECOGNIZED" -> Verdict.Recognized
                "UNRECOGNIZED_VERSION" -> Verdict.Unrecognized
                else -> Verdict.Unevaluated
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse integrity token", e)
            Verdict.Unavailable
        }
    }
}
