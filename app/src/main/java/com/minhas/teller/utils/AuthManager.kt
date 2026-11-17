package com.minhas.teller.utils

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.minhas.teller.BuildConfig
import com.minhas.teller.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Thin wrapper around Firebase Auth + Sign in with Google. There is deliberately no login screen
 * state machine here — a caller awaits [signIn] from a coroutine. "Same account = same store" for
 * cloud sync.
 *
 * Uses Credential Manager, not the legacy `com.google.android.gms.auth.api.signin` API: Google
 * removed that API's backing implementation from current Play Services, so it fails every sign-in
 * with ApiException 10 (DEVELOPER_ERROR) no matter how correctly the OAuth client is configured.
 */
object AuthManager {
    private val tag = "AuthManager"

    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    val uid: StateFlow<String?> get() = SessionManager.currentUserId

    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    fun refresh() {
        val current = firebaseAuth.currentUser
        _user.value = current
        SessionManager.setUserId(current?.uid)
    }

    /** Null (as a failure) when GOOGLE_WEB_CLIENT_ID is not configured yet. [context] must be an Activity context. */
    suspend fun signIn(context: Context): Result<String> {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isBlank()) {
            Log.w(tag, "GOOGLE_WEB_CLIENT_ID is empty — Google Sign-In unavailable until it is set in local.properties")
            return Result.failure(Exception("Google Sign-In is not configured yet (set GOOGLE_WEB_CLIENT_ID)"))
        }
        return try {
            val option = GetSignInWithGoogleOption.Builder(webClientId)
                // Random nonce each attempt — stops a captured response being replayed later.
                .setNonce(UUID.randomUUID().toString())
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(context).getCredential(context, request)
            handleCredential(response.credential)
        } catch (e: GetCredentialException) {
            Log.e(tag, "Google sign-in error: ${e.type}", e)
            Result.failure(Exception("Google sign-in failed (${e.type}): ${e.message ?: "unknown"}"))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e(tag, "Firebase rejected the credential", e)
            Result.failure(Exception("Firebase rejected sign-in (${e.message ?: "invalid credential"}). Check the Web client ID and that Google is enabled."))
        } catch (e: Exception) {
            Log.e(tag, "Google sign-in failed", e)
            Result.failure(e)
        }
    }

    private suspend fun handleCredential(credential: Credential): Result<String> {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return Result.failure(Exception("Unexpected credential type from Credential Manager"))
        }
        val idToken = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(tag, "Could not parse Google ID token credential", e)
            return Result.failure(Exception("Google sign-in returned no usable ID token"))
        }
        val authResult = firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        val user = authResult.user ?: return Result.failure(Exception("Firebase sign-in returned no user"))
        _user.value = user
        SessionManager.setUserId(user.uid)
        return Result.success(user.uid)
    }

    suspend fun signOut(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(tag, "CredentialManager.clearCredentialState failed (non-fatal)", e)
        }
        firebaseAuth.signOut()
        _user.value = null
        SessionManager.setUserId(null)
    }
}
