package com.minhas.teller.utils

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

data class DiscoveredWifiPrinter(
    val name: String,
    val host: String,
    val port: Int,
    val serviceType: String
)

/**
 * Auto-discovers network and Wi-Fi printers on the local LAN using Android NsdManager (mDNS / DNS-SD).
 * Targets:
 * - `_pdl-datastream._tcp.` (Port 9100 RAW / JetDirect / ESC-POS / PCL)
 * - `_ipp._tcp.` (Port 631 Internet Printing Protocol - HP, Canon, Epson, Brother)
 */
class WifiPrinterDiscovery(private val context: Context) {

    private val tag = "WifiPrinterDiscovery"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _printers = MutableStateFlow<List<DiscoveredWifiPrinter>>(emptyList())
    val printers: StateFlow<List<DiscoveredWifiPrinter>> = _printers.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var discoveryListenerPdl: NsdManager.DiscoveryListener? = null
    private var discoveryListenerIpp: NsdManager.DiscoveryListener? = null

    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val isResolving = AtomicBoolean(false)

    private val subnetScanner = NetworkSubnetPrinterScanner(context)

    fun startDiscovery() {
        stopDiscovery()
        _printers.value = emptyList()
        _isScanning.value = true

        // 1. Instant Wi-Fi Subnet sweep for router USB printers, Windows shared printers, and non-mDNS devices
        subnetScanner.scanSubnet { printer ->
            addPrinterIfAbsent(printer)
        }

        // 2. mDNS discovery for standard AirPrint / Mopria network printers
        if (nsdManager != null) {
            discoveryListenerPdl = createDiscoveryListener("_pdl-datastream._tcp.")
            try {
                nsdManager.discoverServices("_pdl-datastream._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListenerPdl)
            } catch (e: Exception) {
                Log.e(tag, "Failed to start PDL discovery: ${e.message}")
            }

            discoveryListenerIpp = createDiscoveryListener("_ipp._tcp.")
            try {
                nsdManager.discoverServices("_ipp._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListenerIpp)
            } catch (e: Exception) {
                Log.e(tag, "Failed to start IPP discovery: ${e.message}")
            }
        }
    }

    fun stopDiscovery() {
        _isScanning.value = false
        resolveQueue.clear()
        isResolving.set(false)

        discoveryListenerPdl?.let {
            try { nsdManager?.stopServiceDiscovery(it) } catch (ignored: Exception) {}
        }
        discoveryListenerPdl = null

        discoveryListenerIpp?.let {
            try { nsdManager?.stopServiceDiscovery(it) } catch (ignored: Exception) {}
        }
        discoveryListenerIpp = null
    }

    private fun createDiscoveryListener(serviceType: String): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(tag, "Service discovery started for $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(tag, "Service found: ${service.serviceName} (${service.serviceType})")
                enqueueResolve(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(tag, "Service lost: ${service.serviceName}")
                _printers.value = _printers.value.filter { it.name != service.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(tag, "Discovery stopped: $serviceType")
                _isScanning.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Discovery start failed ($serviceType): Error code $errorCode")
                _isScanning.value = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Discovery stop failed ($serviceType): Error code $errorCode")
            }
        }
    }

    private fun enqueueResolve(serviceInfo: NsdServiceInfo) {
        resolveQueue.offer(serviceInfo)
        processResolveQueue()
    }

    private fun processResolveQueue() {
        if (!isResolving.compareAndSet(false, true)) return

        val next = resolveQueue.poll()
        if (next == null) {
            isResolving.set(false)
            return
        }

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(tag, "Resolve failed for ${serviceInfo.serviceName}: code $errorCode")
                isResolving.set(false)
                processResolveQueue()
            }

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                try {
                    val host = resolved.host?.hostAddress
                    val port = resolved.port
                    if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                        val name = cleanPrinterName(resolved.serviceName)
                        val discovered = DiscoveredWifiPrinter(
                            name = name,
                            host = host,
                            port = if (port > 0) port else 9100,
                            serviceType = resolved.serviceType ?: "_pdl-datastream._tcp."
                        )
                        addPrinterIfAbsent(discovered)
                    }
                } finally {
                    isResolving.set(false)
                    processResolveQueue()
                }
            }
        }

        try {
            nsdManager?.resolveService(next, resolveListener)
        } catch (e: Exception) {
            Log.w(tag, "Resolve invocation failed: ${e.message}")
            isResolving.set(false)
            processResolveQueue()
        }
    }

    private fun addPrinterIfAbsent(printer: DiscoveredWifiPrinter) {
        val current = _printers.value.toMutableList()
        if (current.none { it.host == printer.host && it.port == printer.port }) {
            current.add(printer)
            _printers.value = current
            Log.i(tag, "Discovered printer: ${printer.name} at ${printer.host}:${printer.port}")
        }
    }

    private fun cleanPrinterName(raw: String): String {
        return raw.replace("\\032", " ")
            .replace("\\", "")
            .trim()
    }
}
