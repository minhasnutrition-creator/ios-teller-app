package com.minhas.teller.utils

import android.content.Context
import android.net.DhcpInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Fast asynchronous Subnet Port Scanner for detecting local printers and print servers
 * that DO NOT advertise via mDNS / Bonjour (e.g. Router USB print servers, non-Wi-Fi
 * printers shared by router, Windows IPP print shares, and Ethernet A4 printers).
 *
 * Targets:
 * - Port 9100: RAW JetDirect / Router USB print server / Ethernet A4 printers (HP, Epson, Canon, Brother)
 * - Port 631:  IPP (Internet Printing Protocol / Windows Shared Printers / CUPS)
 */
class NetworkSubnetPrinterScanner(private val context: Context) {

    private val tag = "SubnetPrinterScanner"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Cache recently discovered printers to avoid redundant scanning
    private val discoveredMap = ConcurrentHashMap<String, DiscoveredWifiPrinter>()

    /**
     * Scans the current local Wi-Fi subnet for printers.
     * Invokes [onPrinterFound] on the main/caller scope whenever a device responds.
     */
    fun scanSubnet(onPrinterFound: (DiscoveredWifiPrinter) -> Unit) {
        scope.launch {
            val wifiInfo = getSubnetDetails()
            if (wifiInfo == null) {
                Log.d(tag, "Wi-Fi not connected or subnet not determined")
                return@launch
            }

            val (subnetPrefix, gatewayIp) = wifiInfo
            Log.i(tag, "Scanning subnet: ${subnetPrefix}x (Gateway: $gatewayIp)")

            // 1. Prioritize scanning the Gateway (Router USB Print Server is almost always .1)
            if (gatewayIp.isNotBlank()) {
                val gatewayPrinter = probeHost(gatewayIp, isGateway = true)
                if (gatewayPrinter != null) {
                    discoveredMap["${gatewayPrinter.host}:${gatewayPrinter.port}"] = gatewayPrinter
                    withContext(Dispatchers.Main) { onPrinterFound(gatewayPrinter) }
                }
            }

            // 2. Parallel sweep across the remaining 1-254 IP range in chunks of 32
            val hosts = (1..254).map { "$subnetPrefix$it" }.filter { it != gatewayIp }
            val chunkSize = 32

            for (chunk in hosts.chunked(chunkSize)) {
                val jobs = chunk.map { ip ->
                    async {
                        val printer = probeHost(ip, isGateway = false)
                        if (printer != null) {
                            val key = "${printer.host}:${printer.port}"
                            if (!discoveredMap.containsKey(key)) {
                                discoveredMap[key] = printer
                                withContext(Dispatchers.Main) { onPrinterFound(printer) }
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
            Log.i(tag, "Subnet scan completed. Found ${discoveredMap.size} printers.")
        }
    }

    private fun probeHost(ip: String, isGateway: Boolean): DiscoveredWifiPrinter? {
        // Probe Port 9100 (RAW JetDirect / Router Print Server / Network Printer)
        if (isPortOpen(ip, 9100, timeoutMs = 180)) {
            val friendlyName = when {
                isGateway -> "Router USB Shared Printer"
                else -> resolvePrinterName(ip, 9100)
            }
            return DiscoveredWifiPrinter(
                name = friendlyName,
                host = ip,
                port = 9100,
                serviceType = if (isGateway) "router-usb" else "raw-9100"
            )
        }

        // Probe Port 631 (IPP / Windows PC Shared Printer / CUPS)
        if (isPortOpen(ip, 631, timeoutMs = 180)) {
            val friendlyName = when {
                isGateway -> "Router IPP Printer"
                else -> resolvePrinterName(ip, 631)
            }
            return DiscoveredWifiPrinter(
                name = friendlyName,
                host = ip,
                port = 631,
                serviceType = "ipp-631"
            )
        }

        return null
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            true
        } catch (_: Exception) {
            false
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun resolvePrinterName(ip: String, port: Int): String {
        return try {
            val hostName = InetAddress.getByName(ip).hostName
            if (!hostName.isNullOrBlank() && hostName != ip && !hostName.startsWith("192.")) {
                val clean = hostName.split(".").firstOrNull() ?: hostName
                return "$clean ($ip)"
            }
            if (port == 631) "Network / PC Printer ($ip)" else "Network Printer ($ip)"
        } catch (_: Exception) {
            if (port == 631) "Network / PC Printer ($ip)" else "Network Printer ($ip)"
        }
    }

    private fun getSubnetDetails(): Pair<String, String>? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return null
            val dhcp: DhcpInfo = wifiManager.dhcpInfo ?: return null
            val ipInt = dhcp.ipAddress
            val gatewayInt = dhcp.gateway

            if (ipInt == 0) return null

            val ip = formatIp(ipInt)
            val gateway = if (gatewayInt != 0) formatIp(gatewayInt) else ""
            val lastDot = ip.lastIndexOf('.')
            if (lastDot <= 0) return null

            val prefix = ip.substring(0, lastDot + 1)
            Pair(prefix, gateway)
        } catch (e: Exception) {
            Log.w(tag, "Failed to get subnet details: ${e.message}")
            null
        }
    }

    private fun formatIp(ip: Int): String {
        return "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
    }
}
