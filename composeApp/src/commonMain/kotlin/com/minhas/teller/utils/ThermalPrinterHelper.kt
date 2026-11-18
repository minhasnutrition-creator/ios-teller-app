package com.minhas.teller.utils

import android.content.Context
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.VoucherWithAllEntries

/**
 * Universal Printer Hub & ESC/POS Driver.
 * Stubbed for iOS - AirPrint and CoreBluetooth can be hooked in.
 */
object ThermalPrinterHelper {

    enum class PaperWidth(val charsPerLine: Int, val widthPt: Float) {
        MM_58(32, 164f),
        MM_80(48, 226f)
    }

    enum class ConnectionType {
        BLUETOOTH,
        USB,
        NETWORK,
        SYSTEM
    }

    data class BluetoothPrinterDevice(
        val name: String,
        val address: String,
        val isBle: Boolean = false
    )

    data class UsbPrinterDevice(
        val name: String,
        val device: Any? = null
    )

    fun bluetoothPermissions(): Array<String> = emptyArray()
    fun hasBluetoothPermission(context: Context): Boolean = true
    fun isBluetoothOn(): Boolean = false
    fun openBluetoothSettings(context: Context) {}

    fun getPairedPrinters(context: Context): List<BluetoothPrinterDevice> = emptyList()
    suspend fun scanBlePrinters(context: Context, timeoutMs: Long = 6000L): List<BluetoothPrinterDevice> = emptyList()
    fun getUsbPrinters(context: Context): List<UsbPrinterDevice> = emptyList()

    fun buildReceiptText(
        voucherWithAllEntries: VoucherWithAllEntries,
        stockItems: List<StockItem>,
        company: Company?,
        customerName: String = "",
        paperWidth: PaperWidth = PaperWidth.MM_80
    ): String = ""

    fun buildReceiptBytes(
        voucherWithAllEntries: VoucherWithAllEntries,
        stockItems: List<StockItem>,
        company: Company?,
        customerName: String = "",
        paperWidth: PaperWidth = PaperWidth.MM_80
    ): ByteArray = ByteArray(0)

    fun cleanPosText(text: String): String = text
    fun buildTestReceiptBytes(label: String = "Test", paperWidth: PaperWidth = PaperWidth.MM_80): ByteArray = ByteArray(0)

    suspend fun testBluetoothConnection(deviceAddress: String): Pair<Boolean, String> = false to "Not supported on iOS"
    suspend fun testNetworkConnection(host: String, port: Int = 9100, timeoutMs: Int = 3000): Pair<Boolean, String> = false to "Not supported on iOS"

    // Real call sites (EasyPrint.kt, VoucherViewScreen.kt) all invoke this positionally as
    // (address, bytes, context) — matching that convention here instead of the alphabetic
    // (context, address, data) order avoids a 3-way positional-argument mismatch everywhere.
    suspend fun printToBluetoothDevice(
        deviceAddress: String,
        data: ByteArray,
        context: Context,
        isBle: Boolean = false
    ): Boolean = false

    suspend fun requestUsbPermission(context: Context, device: Any?): Boolean = false
    suspend fun printToUsbDevice(context: Context, device: Any?, data: ByteArray): Boolean = false
    suspend fun printToNetworkPrinter(host: String, port: Int = 9100, data: ByteArray): Boolean = false

    fun printDocumentWithSystem(context: Context, jobName: String, pdfFile: Any? = null) {}
    fun isSystemPrintServiceEnabled(context: Context): Boolean = false
    fun openSystemPrintSettings(context: Context) {}

    fun generateThermalReceiptPdf(
        voucherWithAllEntries: VoucherWithAllEntries,
        stockItems: List<StockItem>,
        company: Company?,
        outputFile: Any?,
        paperWidth: PaperWidth = PaperWidth.MM_80
    ) {}
}
