package com.minhas.teller.web

import android.content.Context
import java.security.SecureRandom

/**
 * Pairing token for the desktop companion web server and P2P sync.
 *
 * A single persistent per-install token protects the local HTTP server (which exposes the full
 * accounting DB over the LAN). It is generated once and kept in SharedPreferences so the URL the
 * user opens on their PC keeps working across app/server restarts. [rotateToken] issues a new one,
 * which immediately locks out every computer that was paired with the old token.
 */
object CompanionAuth {

    private const val PREFS = "web_companion_prefs"
    private const val KEY_TOKEN = "pairing_token"

    fun getOrCreateToken(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_TOKEN, null)?.let { return it }
        val token = generateToken()
        prefs.edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    fun getToken(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)

    /** Replace the token. Every previously paired computer must open the new link to reconnect. */
    fun rotateToken(context: Context): String {
        val token = generateToken()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TOKEN, token).commit()
        return token
    }

    private fun generateToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
