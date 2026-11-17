package com.minhas.teller.utils

import android.content.Context

/**
 * Fast asynchronous Subnet Port Scanner for detecting local printers and print servers.
 * Stubbed for iOS.
 */
class NetworkSubnetPrinterScanner(private val context: Context) {
    fun scanSubnet(onPrinterFound: (DiscoveredWifiPrinter) -> Unit) {
        // TODO: iOS native subnet scan if permitted
    }
}
