package com.minhas.teller.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user preferences for 1-Click Invoice Printing across Teller:
 * - Default printer output mode (A4 System, Bluetooth Thermal, USB, Wi-Fi Network, or Always Ask)
 * - Saved Bluetooth MAC address and device name
 * - Saved Wi-Fi / Network printer IP and port
 * - Selected paper roll width (58mm / 2-inch vs 80mm / 3-inch)
 * - 1-Click Fast Print toggle
 */
object PrinterPrefs {

    private const val PREFS_NAME = "teller_printer_prefs"

    const val MODE_ALWAYS_ASK = "ALWAYS_ASK"
    const val MODE_A4_SYSTEM = "A4_SYSTEM"
    const val MODE_THERMAL_BT = "THERMAL_BT"
    const val MODE_THERMAL_USB = "THERMAL_USB"
    const val MODE_THERMAL_WIFI = "THERMAL_WIFI"
    const val MODE_COUNTER_PC = "COUNTER_PC"

    private const val KEY_DEFAULT_MODE = "default_printer_mode"
    private const val KEY_BT_ADDRESS = "bt_address"
    private const val KEY_BT_NAME = "bt_name"
    private const val KEY_USB_NAME = "usb_name"
    private const val KEY_WIFI_IP = "wifi_ip"
    private const val KEY_WIFI_PORT = "wifi_port"
    private const val KEY_WIFI_NAME = "wifi_name"
    private const val KEY_PAPER_WIDTH = "paper_width"
    private const val KEY_ONE_CLICK_ENABLED = "one_click_enabled"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDefaultMode(context: Context): String =
        prefs(context).getString(KEY_DEFAULT_MODE, MODE_ALWAYS_ASK) ?: MODE_ALWAYS_ASK

    fun setDefaultMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_DEFAULT_MODE, mode).apply()
    }

    fun isOneClickEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONE_CLICK_ENABLED, false)

    fun setOneClickEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONE_CLICK_ENABLED, enabled).apply()
    }

    fun getBluetoothDevice(context: Context): Pair<String?, String?> {
        val p = prefs(context)
        return Pair(p.getString(KEY_BT_ADDRESS, null), p.getString(KEY_BT_NAME, null))
    }

    fun setBluetoothDevice(context: Context, address: String, name: String) {
        prefs(context).edit()
            .putString(KEY_BT_ADDRESS, address)
            .putString(KEY_BT_NAME, name)
            .apply()
    }

    fun getUsbDeviceName(context: Context): String? =
        prefs(context).getString(KEY_USB_NAME, null)

    fun setUsbDeviceName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_USB_NAME, name).apply()
    }

    fun getWifiPrinter(context: Context): Triple<String, Int, String> {
        val p = prefs(context)
        val ip = p.getString(KEY_WIFI_IP, "192.168.1.100") ?: "192.168.1.100"
        val port = p.getInt(KEY_WIFI_PORT, 9100)
        val name = p.getString(KEY_WIFI_NAME, "Network Printer") ?: "Network Printer"
        return Triple(ip, port, name)
    }

    fun setWifiPrinter(context: Context, ip: String, port: Int, name: String) {
        prefs(context).edit()
            .putString(KEY_WIFI_IP, ip)
            .putInt(KEY_WIFI_PORT, port)
            .putString(KEY_WIFI_NAME, name)
            .apply()
    }

    fun getPaperWidth(context: Context): ThermalPrinterHelper.PaperWidth {
        val str = prefs(context).getString(KEY_PAPER_WIDTH, "MM_58")
        return if (str == "MM_80") ThermalPrinterHelper.PaperWidth.MM_80 else ThermalPrinterHelper.PaperWidth.MM_58
    }

    fun setPaperWidth(context: Context, width: ThermalPrinterHelper.PaperWidth) {
        prefs(context).edit().putString(KEY_PAPER_WIDTH, width.name).apply()
    }

    fun getFriendlyPrinterSummary(context: Context): String {
        return when (getDefaultMode(context)) {
            MODE_A4_SYSTEM -> "Standard A4 Printer (Wi-Fi / Spooler)"
            MODE_THERMAL_BT -> {
                val (_, name) = getBluetoothDevice(context)
                if (!name.isNullOrBlank()) "Bluetooth: $name" else "Bluetooth POS Printer"
            }
            MODE_THERMAL_USB -> {
                val name = getUsbDeviceName(context)
                if (!name.isNullOrBlank()) "USB: $name" else "USB POS Printer"
            }
            MODE_THERMAL_WIFI -> {
                val (ip, port, name) = getWifiPrinter(context)
                "$name ($ip:$port)"
            }
            MODE_COUNTER_PC -> "Shop Counter PC (USB Printer)"
            else -> "Always Ask (Choose when printing)"
        }
    }

    fun resetDefaultPrinter(context: Context) {
        prefs(context).edit()
            .putString(KEY_DEFAULT_MODE, MODE_ALWAYS_ASK)
            .putBoolean(KEY_ONE_CLICK_ENABLED, false)
            .apply()
    }
}
