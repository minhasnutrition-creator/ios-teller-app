package com.minhas.teller.web

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PairRequest(
    val id: String = "",
    val clientName: String = "Desktop Companion",
    val clientIp: String = "127.0.0.1",
    val device: String = "Desktop Companion",
    val ip: String = "127.0.0.1",
    val code: String = "123456"
)

object CompanionAuth {
    fun getOrCreateToken(context: Context): String = "ios-token"
}

/**
 * Web companion and desktop pairing manager for iOS.
 */
object WebCompanionManager {
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>("http://localhost:8080")
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _localIp = MutableStateFlow<String?>("127.0.0.1")
    val localIp: StateFlow<String?> = _localIp.asStateFlow()

    private val _serverPort = MutableStateFlow(8080)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _requestCount = MutableStateFlow(0)
    val requestCount: StateFlow<Int> = _requestCount.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<String>>(emptyList())
    val recentLogs: StateFlow<List<String>> = _recentLogs.asStateFlow()

    private val _isPrintStationActive = MutableStateFlow(false)
    val isPrintStationActive: StateFlow<Boolean> = _isPrintStationActive.asStateFlow()

    private val _pendingPair = MutableStateFlow<PairRequest?>(null)
    val pendingPair: StateFlow<PairRequest?> = _pendingPair.asStateFlow()

    fun getLocalIpAddress(context: Context): String = "127.0.0.1"
    fun pruneExpiredPairs() {}
    fun updateServerState(active: Boolean = false, ip: String = "", port: Int = 8080, token: String = "") {
        _isServerRunning.value = active
        _serverPort.value = port
    }
    fun updateServerState(active: Boolean = false, port: Int = 8080) {
        _isServerRunning.value = active
        _serverPort.value = port
    }

    fun startServer(context: Context) {
        _isServerRunning.value = true
    }

    fun stopServer(context: Context) {
        _isServerRunning.value = false
    }

    fun publishPairing(sessionId: String, context: Context): Boolean = true
    fun setTrackpadActive(active: Boolean) {}
    fun isKeyboardRequested(): Boolean = false
    fun denyPair(id: String) { _pendingPair.value = null }
    fun approvePair(id: String) { _pendingPair.value = null }
    fun disconnectAllComputers(context: Context) {}
    fun enqueuePointer(action: String, x: Float, y: Float) {}
    fun enqueueKey(key: String) {}
    fun queuePrintJob(voucherId: Int) {}
}
