package com.minhas.teller.utils

import android.content.Context
import com.minhas.teller.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FirebaseUser(
    val uid: String = "ios_local_user",
    val email: String? = "user@teller.app",
    val displayName: String? = "Store Owner"
)

/**
 * Authentication manager for iOS.
 * Connects with Sign in with Apple or Firebase Auth.
 */
object AuthManager {
    private val _user = MutableStateFlow<FirebaseUser?>(FirebaseUser())
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    val uid: StateFlow<String?> get() = SessionManager.currentUserId

    fun refresh() {
        val u = FirebaseUser()
        _user.value = u
        SessionManager.setUserId(u.uid)
    }

    suspend fun signIn(context: Context): Result<String> {
        val u = FirebaseUser()
        _user.value = u
        SessionManager.setUserId(u.uid)
        return Result.success(u.uid)
    }

    suspend fun signOut(context: Context) {
        _user.value = null
        SessionManager.setUserId(null)
    }
}
