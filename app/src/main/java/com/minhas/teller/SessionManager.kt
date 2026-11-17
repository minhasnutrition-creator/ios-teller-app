package com.minhas.teller

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionManager {
    private val _currentCompanyId = MutableStateFlow<Int?>(null)
    val currentCompanyId: StateFlow<Int?> = _currentCompanyId.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    fun setCompanyId(id: Int) {
        _currentCompanyId.value = id
    }

    fun setUserId(uid: String?) {
        _currentUserId.value = uid
    }

    fun clearSession() {
        _currentCompanyId.value = null
    }
}
