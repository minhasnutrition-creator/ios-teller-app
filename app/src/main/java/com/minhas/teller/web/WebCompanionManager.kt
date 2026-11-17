package com.minhas.teller.web

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.net.Inet4Address
import java.net.NetworkInterface

object WebCompanionManager {
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _localIp = MutableStateFlow<String?>("127.0.0.1")
    val localIp: StateFlow<String?> = _localIp.asStateFlow()

    private val _serverPort = MutableStateFlow(8080)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _pairingToken = MutableStateFlow<String?>(null)
    val pairingToken: StateFlow<String?> = _pairingToken.asStateFlow()

    private val _requestCount = MutableStateFlow(0)
    val requestCount: StateFlow<Int> = _requestCount.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<String>>(emptyList())
    val recentLogs: StateFlow<List<String>> = _recentLogs.asStateFlow()

    // Interfaces that carry mobile data (a PC can never reach these) vs. ones that carry a local network.
    private val MOBILE_DATA_PREFIXES = listOf("rmnet", "v4-", "ccmni", "pdp", "tun", "ppp", "clat", "dummy", "ifb")
    private val LAN_PREFIXES = listOf("wlan", "swlan", "softap", "ap", "eth", "rndis", "usb", "bt-pan")

    private fun isPrivateLanAddress(host: String): Boolean {
        val p = host.split(".").mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
    }

    /** True when this phone is on Wi-Fi / its own hotspot / USB tethering, i.e. a PC could reach it. */
    fun hasLocalNetwork(context: Context): Boolean = getLocalIpAddress(context) != "127.0.0.1"

    private val printQueue = java.util.concurrent.ConcurrentLinkedQueue<Int>()
    private val _isPrintStationActive = MutableStateFlow(false)
    val isPrintStationActive: StateFlow<Boolean> = _isPrintStationActive.asStateFlow()
    private var lastPrintStationHeartbeat = 0L

    fun notifyPrintStationHeartbeat() {
        lastPrintStationHeartbeat = System.currentTimeMillis()
        _isPrintStationActive.value = true
    }

    fun checkPrintStationHeartbeat() {
        if (System.currentTimeMillis() - lastPrintStationHeartbeat > 6000L) {
            _isPrintStationActive.value = false
        }
    }

    fun queuePrintJob(voucherId: Int) {
        printQueue.offer(voucherId)
    }

    fun pollPrintJob(): Int? {
        notifyPrintStationHeartbeat()
        return printQueue.poll()
    }

    fun updateServerState(running: Boolean, ip: String?, port: Int, token: String? = null) {
        _isServerRunning.value = running
        _localIp.value = ip
        _serverPort.value = port
        _pairingToken.value = token
        _serverUrl.value = if (running && ip != null) "http://$ip:$port/?token=$token" else null
        if (!running) clearPairRequests()
    }

    /**
     * Issue a new pairing token, disconnecting every computer paired with the old one, and refresh
     * the URL shown in the app. The running server checks the live token on every request.
     */
    fun disconnectAllComputers(context: Context) {
        val token = CompanionAuth.rotateToken(context)
        updateServerState(_isServerRunning.value, _localIp.value, _serverPort.value, token)
    }

    fun recordRequest(log: String) {
        _requestCount.value += 1
        val updated = (_recentLogs.value + log).takeLast(20)
        _recentLogs.value = updated
    }

    fun startServer(context: Context) {
        val intent = Intent(context, WebCompanionService::class.java).apply {
            action = WebCompanionService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopServer(context: Context) {
        val intent = Intent(context, WebCompanionService::class.java).apply {
            action = WebCompanionService.ACTION_STOP
        }
        context.startService(intent)
    }

    /**
     * Resolves the primary local IPv4 address (Wi-Fi, Hotspot, Ethernet).
     */
    fun getLocalIpAddress(context: Context? = null): String {
        try {
            // First check Wi-Fi manager if context is provided
            if (context != null) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val wifiIp = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (wifiIp != 0) {
                    val ip = String.format(
                        java.util.Locale.US,
                        "%d.%d.%d.%d",
                        wifiIp and 0xff,
                        wifiIp shr 8 and 0xff,
                        wifiIp shr 16 and 0xff,
                        wifiIp shr 24 and 0xff
                    )
                    if (ip != "0.0.0.0") return ip
                }
            }

            // Fallback (phone's own hotspot, USB tethering, Ethernet): pick a local-network address and
            // never a mobile-data one. Mobile-data interfaces (rmnet*, ccmni*, ...) often come first in the
            // list and their addresses are unreachable from a PC, which used to give a dead link.
            var best: String? = null
            var bestScore = -1
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val name = networkInterface.name.lowercase(java.util.Locale.US)
                if (MOBILE_DATA_PREFIXES.any { name.startsWith(it) }) continue
                val nameScore = if (LAN_PREFIXES.any { name.startsWith(it) }) 2 else 0
                for (address in java.util.Collections.list(networkInterface.inetAddresses)) {
                    if (address !is Inet4Address || address.isLoopbackAddress) continue
                    val host = address.hostAddress ?: continue
                    if (!isPrivateLanAddress(host)) continue
                    if (nameScore + 1 > bestScore) {
                        best = host
                        bestScore = nameScore + 1
                    }
                }
            }
            best?.let { return it }
        } catch (e: Exception) {
            android.util.Log.e("WebCompanionManager", "Error detecting IP: ${e.message}", e)
        }
        return "127.0.0.1"
    }

    // ── Phone-as-trackpad/keyboard input relay (phone → PC browser) ──
    // The phone app and the web server share this process, so the phone's trackpad UI can enqueue
    // events directly here and the PC polls /api/input/events to consume them. Coordinates are
    // normalized (0..1) and mapped to the PC viewport by the client.
    data class InputEvent(
        val seq: Long,
        val type: String,
        val x: Float = 0f,
        val y: Float = 0f,
        val key: String = "",
        val ts: Long = System.currentTimeMillis()
    )

    /** Events older than this are dropped: a PC that opens later must not replay old taps/keys. */
    private const val INPUT_EVENT_TTL_MS = 5000L

    private val inputEvents = java.util.concurrent.CopyOnWriteArrayList<InputEvent>()
    private val inputSeq = java.util.concurrent.atomic.AtomicLong(0)
    private val keyboardRequested = java.util.concurrent.atomic.AtomicBoolean(false)

    fun enqueuePointer(type: String, x: Float, y: Float) {
        pruneOldInputEvents()
        inputEvents.add(InputEvent(inputSeq.incrementAndGet(), type, x, y))
    }

    fun enqueueKey(key: String) {
        pruneOldInputEvents()
        inputEvents.add(InputEvent(inputSeq.incrementAndGet(), "key", key = key))
    }

    private fun pruneOldInputEvents() {
        val cutoff = System.currentTimeMillis() - INPUT_EVENT_TTL_MS
        val stale = inputEvents.filter { it.ts < cutoff }
        if (stale.isNotEmpty()) inputEvents.removeAll(stale.toSet())
    }

    fun drainEvents(since: Long): List<InputEvent> = inputEvents.filter { it.seq > since }

    /** Position of the newest event; a freshly opened PC page starts from here instead of replaying. */
    fun currentInputSeq(): Long = inputSeq.get()

    fun setKeyboardRequested(requested: Boolean) = keyboardRequested.set(requested)

    fun isKeyboardRequested(): Boolean = keyboardRequested.get()

    // True only while the phone's trackpad switch is on. The PC page polls slowly when this is false
    // and quickly when it is true, instead of hammering the phone ~10 times a second all day.
    private val trackpadActive = java.util.concurrent.atomic.AtomicBoolean(false)

    fun setTrackpadActive(active: Boolean) = trackpadActive.set(active)

    fun isTrackpadActive(): Boolean = trackpadActive.get()

    // ── Approve-on-phone pairing ──
    // The PC opens the short address, asks to connect and shows a 4-digit code. The phone shows an
    // Allow/Deny dialog with the same code. Only after Allow does the PC receive the pairing token
    // (once, to whoever holds the random request id).
    data class PairRequest(
        val id: String,
        val code: String,
        val ip: String,
        val device: String,
        val createdAt: Long,
        val status: String = "pending" // pending | approved | denied
    )

    private const val PAIR_TTL_MS = 120_000L
    private const val PAIR_MAX_PENDING = 5
    private val pairRequests = java.util.concurrent.ConcurrentHashMap<String, PairRequest>()
    private val pairRandom = java.security.SecureRandom()

    private val _pendingPair = MutableStateFlow<PairRequest?>(null)
    /** The oldest request still waiting for the phone owner's Allow/Deny (null = none). */
    val pendingPair: StateFlow<PairRequest?> = _pendingPair.asStateFlow()

    @Synchronized
    fun createPairRequest(ip: String, device: String): PairRequest? {
        pruneExpiredPairs()
        // The same computer asking again replaces its earlier request instead of stacking dialogs.
        pairRequests.values.filter { it.ip == ip && it.status == "pending" }.forEach { pairRequests.remove(it.id) }
        if (pairRequests.values.count { it.status == "pending" } >= PAIR_MAX_PENDING) return null
        val idBytes = ByteArray(16)
        pairRandom.nextBytes(idBytes)
        val id = idBytes.joinToString("") { "%02x".format(it) }
        val code = "%04d".format(pairRandom.nextInt(10000))
        val req = PairRequest(id, code, ip, device, System.currentTimeMillis())
        pairRequests[id] = req
        refreshPendingPair()
        return req
    }

    @Synchronized
    fun pruneExpiredPairs() {
        val cutoff = System.currentTimeMillis() - PAIR_TTL_MS
        pairRequests.values.filter { it.createdAt < cutoff }.forEach { pairRequests.remove(it.id) }
        refreshPendingPair()
    }

    private fun refreshPendingPair() {
        _pendingPair.value = pairRequests.values.filter { it.status == "pending" }.minByOrNull { it.createdAt }
    }

    fun approvePair(id: String) = setPairStatus(id, "approved")

    fun denyPair(id: String) = setPairStatus(id, "denied")

    @Synchronized
    private fun setPairStatus(id: String, status: String) {
        val r = pairRequests[id] ?: return
        pairRequests[id] = r.copy(status = status)
        refreshPendingPair()
    }

    /** What the PC sees when it checks. An answered request (approved/denied) is consumed: one-time. */
    @Synchronized
    fun takePairResult(id: String): PairRequest? {
        pruneExpiredPairs()
        val r = pairRequests[id] ?: return null
        if (r.status != "pending") pairRequests.remove(id)
        return r
    }

    @Synchronized
    fun clearPairRequests() {
        pairRequests.clear()
        refreshPendingPair()
    }

    // ── mDNS "teller.local" friendly-name registration ──
    private var nsdManager: android.net.nsd.NsdManager? = null
    private var nsdRegistration: android.net.nsd.NsdManager.RegistrationListener? = null

    fun registerMdns(context: Context, port: Int) {
        if (nsdManager != null && nsdRegistration != null) return
        try {
            val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as android.net.nsd.NsdManager
            val info = android.net.nsd.NsdServiceInfo().apply {
                serviceName = "teller"
                serviceType = "_http._tcp."
                setPort(port)
            }
            val listener = object : android.net.nsd.NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: android.net.nsd.NsdServiceInfo) {
                    android.util.Log.i("WebCompanion", "mDNS ready: teller.local:$port")
                }
                override fun onRegistrationFailed(info: android.net.nsd.NsdServiceInfo, errorCode: Int) {
                    android.util.Log.w("WebCompanion", "mDNS registration failed: $errorCode")
                }
                override fun onServiceUnregistered(info: android.net.nsd.NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: android.net.nsd.NsdServiceInfo, errorCode: Int) {}
            }
            manager.registerService(info, android.net.nsd.NsdManager.PROTOCOL_DNS_SD, listener)
            nsdManager = manager
            nsdRegistration = listener
        } catch (e: Exception) {
            android.util.Log.e("WebCompanion", "mDNS error: ${e.message}")
        }
    }

    fun unregisterMdns() {
        try { nsdManager?.unregisterService(nsdRegistration) } catch (_: Exception) {}
        nsdManager = null
        nsdRegistration = null
    }

    // ── Zero-typing pairing handshake (phone side) ──
    // The hosted desktop page (Firebase Hosting) creates a "waiting" session and shows its QR; the
    // phone scans that QR and publishes its LAN ip + web-server token back to the same node so the
    // PC can redirect to it. Returns false on any network/Firebase failure.
    suspend fun publishPairing(sessionId: String, context: Context): Boolean {
        return try {
            val ip = getLocalIpAddress(context)
            val token = CompanionAuth.getOrCreateToken(context)
            FirebaseDatabase.getInstance()
                .getReference("pairing_sessions")
                .child(sessionId)
                .setValue(
                    mapOf(
                        "status" to "paired",
                        "ip" to ip,
                        "token" to token,
                        "pairedAt" to ServerValue.TIMESTAMP
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            android.util.Log.e("WebCompanion", "publishPairing failed", e)
            false
        }
    }
}
