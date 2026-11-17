package com.minhas.teller.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredWifiPrinter(
    val name: String,
    val host: String,
    val port: Int,
    val serviceType: String
)

/**
 * Auto-discovers network and Wi-Fi printers on the local LAN.
 * Stubbed for iOS - native Bonjour / AirPrint discovery can be connected here.
 */
class WifiPrinterDiscovery(private val context: Context) {

    private val _printers = MutableStateFlow<List<DiscoveredWifiPrinter>>(emptyList())
    val printers: StateFlow<List<DiscoveredWifiPrinter>> = _printers.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun startDiscovery() {
        _isScanning.value = true
        // TODO: iOS native Bonjour/mDNS discovery via NSNetServiceBrowser
        _isScanning.value = false
    }

    fun stopDiscovery() {
        _isScanning.value = false
    }
}
