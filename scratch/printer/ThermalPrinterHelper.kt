package com.minhas.teller.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.Paragraph
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherWithAllEntries
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Universal Printer Hub & ESC/POS Driver for Android:
 * - Bluetooth POS Printers (SPP RFCOMM)
 * - USB OTG POS Printers (UsbManager Bulk Transfer)
 * - Network LAN / WiFi POS Printers (TCP Socket Port 9100)
 * - Android System Print Spooler (PrintManager for any WiFi/Network/Mopria/Office printer)
 * - 58mm (2-inch) and 80mm (3-inch) paper width formatting
 * - Dynamic Thermal Receipt PDF generator
 */
object ThermalPrinterHelper {

    private const val TAG = "ThermalPrinterHelper"
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ESC/POS Command Constants
    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val ESC_DOUBLE_SIZE = byteArrayOf(0x1D, 0x21, 0x11)
    private val ESC_NORMAL_SIZE = byteArrayOf(0x1D, 0x21, 0x00)
    private val ESC_LINE_FEED = byteArrayOf(0x0A)
    private val ESC_CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x42, 0x00)

    enum class PaperWidth(val charsPerLine: Int, val widthPt: Float) {
        MM_58(32, 164f),
        MM_80(48, 226f)
    }

    enum class ConnectionType {
        BLUETOOTH,
        USB,
        NETWORK,
        SYSTEM_PRINT
    }

    data class BluetoothPrinterDevice(
        val name: String,
        val address: String,
        /** True for a Bluetooth LE-only printer found by scanning (not paired in Android settings). */
        val isBle: Boolean = false
    )

    data class UsbPrinterDevice(
        val name: String,
        val device: UsbDevice
    )

    // ────────────────────────────────────────────────────────────────────────
    // 1. Discovery Helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Runtime permissions Bluetooth printing needs. On Android 12+ BLUETOOTH_CONNECT must be granted
     * at runtime (it was only declared in the manifest, so on most phones the paired-printer list came
     * back empty and every Bluetooth print failed with a SecurityException).
     */
    fun bluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN)
        else emptyArray()

    fun hasBluetoothPermission(context: Context): Boolean =
        bluetoothPermissions().all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun isBluetoothOn(): Boolean = try { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true } catch (e: Exception) { false }

    /** Opens Android's Bluetooth screen so a new printer can be paired (PIN is usually 0000 or 1234). */
    fun openBluetoothSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open Bluetooth settings", Toast.LENGTH_SHORT).show()
        }
    }

    private val PRINTER_NAME_HINTS = listOf(
        "print", "pos", "pt-", "pt2", "mtp", "rpp", "thermal", "receipt", "bt-58", "bt58", "58mm", "80mm",
        "xp-", "epson", "tm-", "star", "bixolon", "sunmi", "goojprt", "zjiang", "hoin", "milestone", "rongta",
        "gprinter", "munbyn", "zebra", "tsc", "inner", "spp", "mpt", "ble printer"
    )

    private fun looksLikePrinter(name: String?): Boolean {
        val n = name?.lowercase() ?: return false
        return PRINTER_NAME_HINTS.any { n.contains(it) }
    }

    /**
     * Lists all bonded (paired) Bluetooth devices that can serve as thermal printers — printers first
     * (by Bluetooth class or name), then everything else, so headphones/watches don't hide the printer.
     */
    @SuppressLint("MissingPermission")
    fun getPairedPrinters(context: Context): List<BluetoothPrinterDevice> {
        val adapter = try {
            BluetoothAdapter.getDefaultAdapter()
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth not supported on this device: ${e.message}")
            null
        } ?: return emptyList()

        return try {
            adapter.bondedDevices
                .sortedByDescending { d ->
                    val isImaging = try { d.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.IMAGING } catch (e: Exception) { false }
                    when {
                        isImaging -> 2
                        looksLikePrinter(d.name) -> 1
                        else -> 0
                    }
                }
                .map { device ->
                    BluetoothPrinterDevice(
                        name = device.name ?: "Unknown Printer",
                        address = device.address,
                        isBle = device.type == BluetoothDevice.DEVICE_TYPE_LE
                    )
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth permissions: ${e.message}")
            emptyList()
        }
    }

    /**
     * Finds Bluetooth LE-only printers that are switched on nearby. Many low-cost 58mm printers are
     * BLE-only: they never show up in Android's pairing list, so they could not be used before.
     * Android 12+ only (older Android needs location permission to scan, which Teller doesn't ask for).
     */
    @SuppressLint("MissingPermission")
    suspend fun scanBlePrinters(context: Context, timeoutMs: Long = 6000L): List<BluetoothPrinterDevice> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !hasBluetoothPermission(context)) return emptyList()
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return emptyList()
        val scanner = manager.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return emptyList()
        val found = java.util.concurrent.ConcurrentHashMap<String, BluetoothPrinterDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = try { result.device.name } catch (e: SecurityException) { null } ?: result.scanRecord?.deviceName
                if (looksLikePrinter(name)) {
                    found[result.device.address] = BluetoothPrinterDevice(name ?: result.device.address, result.device.address, isBle = true)
                }
            }
        }
        return try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            delay(timeoutMs)
            found.values.sortedBy { it.name }
        } catch (e: Exception) {
            Log.w(TAG, "BLE printer scan failed: ${e.message}")
            emptyList()
        } finally {
            try { scanner.stopScan(callback) } catch (ignored: Exception) {}
        }
    }

    /**
     * Lists all USB devices connected via OTG that identify as printers or bulk devices.
     */
    fun getUsbPrinters(context: Context): List<UsbPrinterDevice> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        val deviceList = usbManager.deviceList
        val printers = mutableListOf<UsbPrinterDevice>()

        for ((_, device) in deviceList) {
            var isPrinter = device.deviceClass == UsbConstants.USB_CLASS_PRINTER
            if (!isPrinter) {
                for (i in 0 until device.interfaceCount) {
                    val intf = device.getInterface(i)
                    if (intf.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                        isPrinter = true
                        break
                    }
                }
            }
            // Many POS printers report class 0 (vendor specific per interface)
            if (isPrinter || device.interfaceCount > 0) {
                val label = device.productName ?: "USB POS Printer (${device.deviceName})"
                printers.add(UsbPrinterDevice(label, device))
            }
        }
        return printers
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. Receipt Layout & Formatting (Text & ESC/POS Bytes)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Plain-text preview representation for UI display.
     */
    fun buildReceiptText(
        company: Company?,
        voucherWithAllEntries: VoucherWithAllEntries,
        stockItems: List<StockItem> = emptyList(),
        customerName: String? = null,
        paperWidth: PaperWidth = PaperWidth.MM_58
    ): String {
        val sb = StringBuilder()
        val cpl = paperWidth.charsPerLine

        fun center(text: String): String {
            if (text.length >= cpl) return text.take(cpl)
            val pad = (cpl - text.length) / 2
            return " ".repeat(pad) + text
        }

        fun divider(char: Char = '-'): String = char.toString().repeat(cpl)

        fun twoCol(left: String, right: String): String {
            val space = cpl - left.length - right.length
            return if (space >= 1) {
                left + " ".repeat(space) + right
            } else {
                val rightPad = (cpl - right.length).coerceAtLeast(0)
                left + "\n" + " ".repeat(rightPad) + right
            }
        }

        // Header
        sb.appendLine(divider('='))
        sb.appendLine(center(company?.name ?: "TELLER INVOICE"))
        company?.address?.takeIf { it.isNotBlank() }?.let { sb.appendLine(center(it)) }
        company?.phoneNumber?.takeIf { it.isNotBlank() }?.let { sb.appendLine(center("Ph: $it")) }
        company?.gstin?.takeIf { it.isNotBlank() }?.let { sb.appendLine(center("GSTIN: $it")) }
        sb.appendLine(divider())

        // Meta
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(voucherWithAllEntries.voucher.date ?: Date())
        val invLabel = "Inv: #${voucherWithAllEntries.voucher.voucherNumber}"
        if (invLabel.length + dateStr.length + 1 > cpl) {
            sb.appendLine(invLabel)
            sb.appendLine(dateStr)
        } else {
            sb.appendLine(twoCol(invLabel, dateStr))
        }
        customerName?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine(twoCol("Cust: $it", voucherWithAllEntries.voucher.type))
        }
        sb.appendLine(divider())

        // Items
        val stockItemMap = stockItems.associateBy { it.id }
        var subTotal = 0.0
        var totalTax = 0.0

        if (voucherWithAllEntries.inventoryEntries.isNotEmpty()) {
            sb.appendLine(twoCol("Item", "Amount"))
            sb.appendLine(divider())
            voucherWithAllEntries.inventoryEntries.forEach { entry ->
                val itemName = stockItemMap[entry.stockItemId]?.name ?: "Item #${entry.stockItemId}"
                val lineAmount = if (entry.totalAmount > 0) entry.totalAmount else entry.amount
                val taxBase = if (entry.taxableAmount > 0L) entry.taxableAmount.paiseToRupees() else (entry.quantity * entry.rate)
                subTotal += taxBase
                totalTax += entry.taxAmount.paiseToRupees()

                sb.appendLine(itemName)
                val qtyRateStr = "  %.2f x %.2f".format(entry.quantity, entry.rate)
                sb.appendLine(twoCol(qtyRateStr, "₹%.2f".format(lineAmount.paiseToRupees())))
            }
            sb.appendLine(divider())
        }

        // Totals
        val grandTotal = if (voucherWithAllEntries.inventoryEntries.isNotEmpty()) {
            voucherWithAllEntries.inventoryEntries.sumOf { if (it.totalAmount > 0) it.totalAmount else it.amount }
        } else {
            voucherWithAllEntries.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
        }

        if (totalTax > 0) {
            sb.appendLine(twoCol("Taxable Base:", "₹%.2f".format(subTotal)))
            sb.appendLine(twoCol("GST Tax:", "₹%.2f".format(totalTax)))
            sb.appendLine(divider())
        }

        sb.appendLine(twoCol("TOTAL:", "₹%.2f".format(grandTotal.paiseToRupees())))
        sb.appendLine(divider())

        // Footer
        company?.upiId?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine(center("UPI: $it"))
        }
        sb.appendLine(center("Thank You! Visit Again"))
        sb.appendLine(center("Powered by Teller"))
        sb.appendLine(divider('='))

        return sb.toString()
    }

    /**
     * Formats a complete ESC/POS byte receipt for 58mm / 80mm printers.
     */
    fun buildReceiptBytes(
        company: Company?,
        voucherWithAllEntries: VoucherWithAllEntries,
        stockItems: List<StockItem> = emptyList(),
        customerName: String? = null,
        paperWidth: PaperWidth = PaperWidth.MM_58
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val cpl = paperWidth.charsPerLine
        val charset = Charset.forName("CP437")

        fun write(bytes: ByteArray) = out.write(bytes)
        fun writeText(text: String) = out.write(cleanPosText(text).toByteArray(charset))
        fun line(text: String = "") {
            writeText(text)
            write(ESC_LINE_FEED)
        }
        fun divider() = line("-".repeat(cpl))

        fun twoColumn(left: String, right: String): String {
            val spaceNeeded = cpl - left.length - right.length
            return if (spaceNeeded >= 1) {
                left + " ".repeat(spaceNeeded) + right
            } else {
                val rightPad = (cpl - right.length).coerceAtLeast(0)
                left + "\n" + " ".repeat(rightPad) + right
            }
        }

        // Initialize printer
        write(ESC_INIT)

        // ── 1. Company Header (Centered & Bold) ──
        write(ESC_ALIGN_CENTER)
        write(ESC_DOUBLE_SIZE)
        line(company?.name ?: "TELLER INVOICE")
        write(ESC_NORMAL_SIZE)

        company?.address?.takeIf { it.isNotBlank() }?.let { line(it) }
        company?.phoneNumber?.takeIf { it.isNotBlank() }?.let { line("Ph: $it") }
        company?.gstin?.takeIf { it.isNotBlank() }?.let { line("GSTIN: $it") }
        divider()

        // ── 2. Invoice Meta ──
        write(ESC_ALIGN_LEFT)
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(voucherWithAllEntries.voucher.date ?: Date())
        val invLabel = "Inv: #${voucherWithAllEntries.voucher.voucherNumber}"
        if (invLabel.length + dateStr.length + 1 > cpl) {
            line(invLabel)
            line(dateStr)
        } else {
            line(twoColumn(invLabel, dateStr))
        }
        customerName?.takeIf { it.isNotBlank() }?.let {
            line("Customer: $it")
        }
        divider()

        // ── 3. Line Items Table ──
        val stockItemMap = stockItems.associateBy { it.id }
        var subTotal = 0.0
        var totalTax = 0.0

        if (voucherWithAllEntries.inventoryEntries.isNotEmpty()) {
            line(twoColumn("Item", "Amount"))
            divider()

            voucherWithAllEntries.inventoryEntries.forEach { entry ->
                val itemName = stockItemMap[entry.stockItemId]?.name ?: "Item #${entry.stockItemId}"
                val lineAmount = if (entry.totalAmount > 0) entry.totalAmount else entry.amount
                val taxBase = if (entry.taxableAmount > 0L) entry.taxableAmount.paiseToRupees() else (entry.quantity * entry.rate)
                subTotal += taxBase
                totalTax += entry.taxAmount.paiseToRupees()

                line(itemName)
                val qtyRateStr = "  %.2f x %.2f".format(entry.quantity, entry.rate)
                line(twoColumn(qtyRateStr, "₹%.2f".format(lineAmount.paiseToRupees())))
            }
            divider()
        }

        // ── 4. Totals & Tax Summary ──
        val grandTotal = if (voucherWithAllEntries.inventoryEntries.isNotEmpty()) {
            voucherWithAllEntries.inventoryEntries.sumOf { if (it.totalAmount > 0) it.totalAmount else it.amount }
        } else {
            voucherWithAllEntries.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
        }

        if (totalTax > 0) {
            line(twoColumn("Taxable Base:", "₹%.2f".format(subTotal)))
            line(twoColumn("GST Tax:", "₹%.2f".format(totalTax)))
        }

        write(ESC_BOLD_ON)
        write(ESC_DOUBLE_SIZE)
        line(twoColumn("TOTAL:", "₹%.2f".format(grandTotal.paiseToRupees())))
        write(ESC_NORMAL_SIZE)
        write(ESC_BOLD_OFF)
        divider()

        // ── 5. UPI QR Code (Universal Raster Bitmap + Native ESC/POS) ──
        val upiId = company?.upiId
        if (!upiId.isNullOrBlank()) {
            try {
                val upiUri = UpiQrHelper.buildUpiUri(
                    upiId = upiId,
                    payeeName = company.name,
                    amount = grandTotal.paiseToRupees(),
                    invoiceNumber = voucherWithAllEntries.voucher.voucherNumber
                )
                val qrSizePx = if (paperWidth == PaperWidth.MM_58) 200 else 280
                val qrBmp = UpiQrHelper.generateQrBitmap(upiUri, qrSizePx)

                write(ESC_ALIGN_CENTER)
                line("Scan to Pay via UPI:")
                if (qrBmp != null) {
                    write(UpiQrHelper.bitmapToEscPosRaster(qrBmp))
                    write(ESC_LINE_FEED)
                } else {
                    val qrBytes = upiUri.toByteArray(charset)
                    val qrLen = qrBytes.size + 3
                    val pL = (qrLen and 0xFF).toByte()
                    val pH = ((qrLen shr 8) and 0xFF).toByte()

                    // GS ( k Model 2
                    write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
                    // Module Size
                    val qrSize = if (paperWidth == PaperWidth.MM_58) 0x04.toByte() else 0x05.toByte()
                    write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, qrSize))
                    // Error correction level M
                    write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31))
                    // Store data
                    write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
                    write(qrBytes)
                    // Print QR code
                    write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
                    write(ESC_LINE_FEED)
                }
                line("UPI: $upiId")
            } catch (e: Exception) {
                Log.w(TAG, "Could not render ESC/POS QR code: ${e.message}")
            }
        }

        // ── 6. Footer ──
        write(ESC_ALIGN_CENTER)
        line("Thank You! Visit Again")
        line("Powered by Teller")

        // Feed & Cut Paper
        write(ESC_LINE_FEED)
        write(ESC_LINE_FEED)
        write(ESC_LINE_FEED)
        write(ESC_CUT_PAPER)

        return out.toByteArray()
    }

    // ────────────────────────────────────────────────────────────────────────
    // 3. Printing Transports: Bluetooth, USB OTG, Network IP, Android Spooler
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Sanitizes text for single-byte ESC/POS codepages (replaces ₹ with Rs. to prevent '?' corruption).
     */
    fun cleanPosText(text: String): String {
        return text.replace("₹", "Rs. ")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("’", "'")
            .replace("–", "-")
            .replace("—", "-")
    }

    /**
     * Builds a self-test ESC/POS receipt to verify printer hardware, connection, and alignment.
     */
    fun buildTestReceiptBytes(
        printerName: String = "POS Thermal Printer",
        paperWidth: PaperWidth = PaperWidth.MM_58
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val cpl = paperWidth.charsPerLine
        val charset = Charset.forName("CP437")

        fun write(bytes: ByteArray) = out.write(bytes)
        fun line(text: String = "") {
            out.write(cleanPosText(text).toByteArray(charset))
            write(ESC_LINE_FEED)
        }

        write(ESC_INIT)
        write(ESC_ALIGN_CENTER)
        write(ESC_DOUBLE_SIZE)
        line("TELLER POS TEST")
        write(ESC_NORMAL_SIZE)
        line("Hardware & Connection OK")
        write(ESC_ALIGN_LEFT)
        line("-".repeat(cpl))
        line("Device: $printerName")
        line("Width: ${if (paperWidth == PaperWidth.MM_58) "58mm (2-inch)" else "80mm (3-inch)"} ($cpl CPL)")
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.getDefault())
        line("Date: ${sdf.format(Date())}")
        line("-".repeat(cpl))
        write(ESC_ALIGN_CENTER)
        line("1234567890 ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        line("Ready for Instant Voice Invoicing")
        line("Powered by Teller")
        write(ESC_LINE_FEED)
        write(ESC_LINE_FEED)
        write(ESC_LINE_FEED)
        write(ESC_CUT_PAPER)
        return out.toByteArray()
    }

    /**
     * Writes [data] in small pieces with a short pause between them. Cheap Bluetooth printers have a
     * small receive buffer and silently drop bytes when a whole receipt arrives at once — that is
     * what produced half-printed receipts, missing QR codes and garbage characters.
     */
    private suspend fun writePaced(out: OutputStream, data: ByteArray, chunk: Int, pauseMs: Long) {
        var offset = 0
        while (offset < data.size) {
            val count = minOf(chunk, data.size - offset)
            out.write(data, offset, count)
            out.flush()
            offset += count
            if (offset < data.size && pauseMs > 0) delay(pauseMs)
        }
    }

    /**
     * After the last byte is sent, keep the link open long enough for the printer to receive and
     * print it. flush() returns as soon as Android has queued the data, so closing straight away cut
     * receipts short (or printed nothing at all on some printers).
     */
    private fun drainWaitMs(bytes: Int): Long = (700L + bytes / 6L).coerceAtMost(6000L)

    /**
     * Establishes a Bluetooth RFCOMM SPP socket using a 4-tier fallback:
     * 1. Standard SPP UUID RFCOMM
     * 2. Insecure SPP UUID RFCOMM
     * 3. Reflection on channel 1 (standard POS port)
     * 4. Insecure reflection on channel 1
     * A socket whose connect() fails is closed before the next attempt (a leaked half-open socket
     * made every later attempt fail with "read failed, socket might be closed").
     */
    @SuppressLint("MissingPermission")
    private fun createConnectedBluetoothSocket(
        device: BluetoothDevice,
        adapter: BluetoothAdapter
    ): BluetoothSocket {
        try { adapter.cancelDiscovery() } catch (ignored: Exception) {}
        var lastEx: Exception? = null

        val attempts: List<Pair<String, () -> BluetoothSocket>> = listOf(
            "standard SPP" to { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            "insecure SPP" to { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            "channel 1" to {
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket
            },
            "insecure channel 1" to {
                device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket
            }
        )
        for ((label, create) in attempts) {
            var s: BluetoothSocket? = null
            try {
                s = create()
                s.connect()
                return s
            } catch (e: Exception) {
                lastEx = e
                try { s?.close() } catch (ignored: Exception) {}
                Log.w(TAG, "Bluetooth $label connect failed: ${e.message}")
            }
        }
        throw lastEx ?: java.io.IOException("Could not connect to Bluetooth printer ${device.address}")
    }

    /** Connects with one retry — printers waking from sleep often refuse the very first connection. */
    private suspend fun connectBluetoothWithRetry(device: BluetoothDevice, adapter: BluetoothAdapter): BluetoothSocket {
        return try {
            createConnectedBluetoothSocket(device, adapter)
        } catch (first: Exception) {
            delay(800)
            createConnectedBluetoothSocket(device, adapter)
        }
    }

    /**
     * Tests connectivity to a paired Bluetooth printer.
     * Returns Pair(success, message).
     */
    @SuppressLint("MissingPermission")
    suspend fun testBluetoothConnection(deviceAddress: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val adapter = try {
            BluetoothAdapter.getDefaultAdapter()
        } catch (e: Exception) {
            null
        } ?: return@withContext Pair(false, "Bluetooth is not supported on this device")
        if (!adapter.isEnabled) return@withContext Pair(false, "Bluetooth is off. Turn it on and try again.")

        var socket: BluetoothSocket? = null
        try {
            val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)
            socket = connectBluetoothWithRetry(device, adapter)
            val stream: OutputStream = socket.outputStream
            stream.write(ESC_INIT)
            stream.flush()
            delay(300)
            val deviceName = try { device.name ?: deviceAddress } catch (e: Exception) { deviceAddress }
            Log.i(TAG, "Bluetooth ping success to $deviceName ($deviceAddress)")
            Pair(true, "Connected successfully to $deviceName")
        } catch (e: SecurityException) {
            Pair(false, "Allow Teller to use Bluetooth (Nearby devices permission) and try again.")
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth ping failed to $deviceAddress: ${e.message}")
            Pair(false, "Could not connect. Make sure the printer is ON, charged and close to the phone.")
        } finally {
            try { socket?.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Tests connectivity to a Network / LAN thermal printer (Port 9100).
     */
    suspend fun testNetworkConnection(
        ipAddress: String,
        port: Int = 9100,
        timeoutMs: Int = 3000
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            val socketAddress = InetSocketAddress(ipAddress, port)
            socket.connect(socketAddress, timeoutMs)
            socket.soTimeout = timeoutMs
            val out = socket.getOutputStream()
            out.write(ESC_INIT)
            out.flush()
            Pair(true, "Connected successfully to Network Printer at $ipAddress:$port")
        } catch (e: Exception) {
            Pair(false, "Network printer connection failed: ${e.localizedMessage ?: e.message}")
        } finally {
            try { socket?.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Sends raw bytes to a Bluetooth printer.
     * Classic (SPP) printers: connect with fallbacks + one retry, send in paced chunks, wait for the
     * printer to finish before disconnecting. Bluetooth LE printers (or dual-mode printers whose SPP
     * link fails) are printed over GATT when a [context] is given.
     */
    @SuppressLint("MissingPermission")
    suspend fun printToBluetoothDevice(
        deviceAddress: String,
        data: ByteArray,
        context: Context? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val adapter = try {
            BluetoothAdapter.getDefaultAdapter()
        } catch (e: Exception) {
            null
        } ?: return@withContext false
        if (!adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is off")
            return@withContext false
        }

        val device: BluetoothDevice = try { adapter.getRemoteDevice(deviceAddress) } catch (e: Exception) { return@withContext false }
        val type = try { device.type } catch (e: SecurityException) { BluetoothDevice.DEVICE_TYPE_UNKNOWN }

        // BLE-only printer: GATT is the only way in.
        if (type == BluetoothDevice.DEVICE_TYPE_LE) {
            return@withContext context != null && printViaBle(context, device, data)
        }

        var socket: BluetoothSocket? = null
        try {
            socket = connectBluetoothWithRetry(device, adapter)
            val stream: OutputStream = socket.outputStream
            writePaced(stream, data, chunk = 512, pauseMs = 25)
            delay(drainWaitMs(data.size))
            Log.i(TAG, "Printed ${data.size} bytes successfully to Bluetooth: $deviceAddress")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Classic Bluetooth print failed on $deviceAddress: ${e.message}", e)
            try { socket?.close() } catch (ignored: Exception) {}
            socket = null
            // Dual-mode / unknown printers: try Bluetooth LE before giving up.
            if (context != null && type != BluetoothDevice.DEVICE_TYPE_CLASSIC) printViaBle(context, device, data) else false
        } finally {
            try { socket?.close() } catch (ignored: Exception) {}
        }
    }

    // Write characteristics used by common BLE ESC/POS printers, most common first.
    private val BLE_PREFERRED_WRITE_CHARS = listOf(
        "00002af1-0000-1000-8000-00805f9b34fb", // service 18F0 — very common 58mm printers
        "bef8d6c9-9c21-4c9e-b632-bd58c1009f9f", // service E7810A71-73AE-499D-8C15-FAA9AEF0C3F2
        "49535343-8841-43f4-a8d4-ecbe34729bb3", // ISSC / Microchip transparent UART
        "0000ff02-0000-1000-8000-00805f9b34fb", // service FF00
        "0000fff2-0000-1000-8000-00805f9b34fb"  // service FFF0
    ).map { UUID.fromString(it) }

    private fun pickBleWriteCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        val writable = gatt.services
            .filterNot { svc -> svc.uuid.toString().lowercase().let { it.startsWith("00001800") || it.startsWith("00001801") || it.startsWith("0000180a") } }
            .flatMap { it.characteristics }
            .filter { c ->
                c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            }
        return BLE_PREFERRED_WRITE_CHARS.firstNotNullOfOrNull { want -> writable.firstOrNull { it.uuid == want } }
            ?: writable.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun startBleWrite(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray, writeType: Int): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(ch, value, writeType) == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            ch.writeType = writeType
            ch.value = value
            gatt.writeCharacteristic(ch)
        }

    /** Prints over Bluetooth LE (GATT) in MTU-sized pieces, waiting for each piece to be accepted. */
    @SuppressLint("MissingPermission")
    private suspend fun printViaBle(context: Context, device: BluetoothDevice, data: ByteArray): Boolean {
        val connected = CompletableDeferred<Boolean>()
        val servicesFound = CompletableDeferred<Boolean>()
        val mtuChanged = CompletableDeferred<Int>()
        var pendingWrite: CompletableDeferred<Boolean>? = null

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connected.complete(true)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connected.complete(false); servicesFound.complete(false); mtuChanged.complete(23)
                    pendingWrite?.complete(false)
                }
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                servicesFound.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                mtuChanged.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23)
            }
            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        val gatt = try {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            Log.e(TAG, "BLE connect failed: ${e.message}")
            null
        } ?: return false

        try {
            if (withTimeoutOrNull(10_000L) { connected.await() } != true) return false
            if (!gatt.discoverServices()) return false
            if (withTimeoutOrNull(8_000L) { servicesFound.await() } != true) return false
            val mtu = if (gatt.requestMtu(185)) withTimeoutOrNull(3_000L) { mtuChanged.await() } ?: 23 else 23
            val ch = pickBleWriteCharacteristic(gatt) ?: run {
                Log.e(TAG, "No writable characteristic on BLE printer ${device.address}")
                return false
            }
            val noResponse = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            val writeType = if (noResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            val chunk = (mtu - 3).coerceIn(20, 180)

            var offset = 0
            while (offset < data.size) {
                val part = data.copyOfRange(offset, minOf(offset + chunk, data.size))
                val done = CompletableDeferred<Boolean>()
                pendingWrite = done
                var started = startBleWrite(gatt, ch, part, writeType)
                if (!started) { delay(40); started = startBleWrite(gatt, ch, part, writeType) }
                if (!started) return false
                if (withTimeoutOrNull(3_000L) { done.await() } != true) return false
                if (noResponse) delay(10) // pacing: BLE printers without flow control drop data otherwise
                offset += part.size
            }
            delay(drainWaitMs(data.size))
            Log.i(TAG, "Printed ${data.size} bytes over Bluetooth LE to ${device.address}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "BLE print failed on ${device.address}: ${e.message}", e)
            return false
        } finally {
            try { gatt.disconnect() } catch (ignored: Exception) {}
            try { gatt.close() } catch (ignored: Exception) {}
        }
    }

    private const val ACTION_USB_PERMISSION = "com.minhas.teller.USB_PRINTER_PERMISSION"

    /**
     * Asks the user (system pop-up) to allow Teller to use the USB printer, and waits for the answer.
     * Without this, USB printing always failed on first use because permission was never requested.
     */
    suspend fun requestUsbPermission(context: Context, device: UsbDevice): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        if (usbManager.hasPermission(device)) return true
        val app = context.applicationContext
        val answer = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == ACTION_USB_PERMISSION) {
                    answer.complete(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                }
            }
        }
        return try {
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                app.registerReceiver(receiver, filter)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(app, 0, Intent(ACTION_USB_PERMISSION).setPackage(app.packageName), flags)
            withContext(Dispatchers.Main) { usbManager.requestPermission(device, pi) }
            (withTimeoutOrNull(60_000L) { answer.await() } ?: false) || usbManager.hasPermission(device)
        } catch (e: Exception) {
            Log.e(TAG, "USB permission request failed: ${e.message}")
            false
        } finally {
            try { app.unregisterReceiver(receiver) } catch (ignored: Exception) {}
        }
    }

    /**
     * Sends raw ESC/POS bytes to a USB OTG printer. Asks for USB permission when needed and prefers
     * the printer-class interface (some printers also expose other interfaces).
     */
    suspend fun printToUsbDevice(
        context: Context,
        device: UsbDevice,
        data: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return@withContext false
        if (!usbManager.hasPermission(device) && !requestUsbPermission(context, device)) {
            Log.w(TAG, "USB permission not granted for device ${device.deviceName}")
            return@withContext false
        }

        val connection = usbManager.openDevice(device) ?: return@withContext false
        var claimed: UsbInterface? = null
        try {
            var printerInterface: UsbInterface? = null
            var bulkEndpoint: UsbEndpoint? = null

            fun findBulkOut(intf: UsbInterface): UsbEndpoint? =
                (0 until intf.endpointCount).map { intf.getEndpoint(it) }
                    .firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }

            val interfaces = (0 until device.interfaceCount).map { device.getInterface(it) }
            for (intf in interfaces.sortedByDescending { it.interfaceClass == UsbConstants.USB_CLASS_PRINTER }) {
                val ep = findBulkOut(intf)
                if (ep != null) { printerInterface = intf; bulkEndpoint = ep; break }
            }

            if (printerInterface == null || bulkEndpoint == null) {
                Log.e(TAG, "No bulk OUT endpoint found on USB device")
                return@withContext false
            }

            if (!connection.claimInterface(printerInterface, true)) {
                Log.e(TAG, "Could not claim USB printer interface")
                return@withContext false
            }
            claimed = printerInterface
            val chunkSize = 4096
            var offset = 0
            while (offset < data.size) {
                val count = minOf(chunkSize, data.size - offset)
                val chunk = data.copyOfRange(offset, offset + count)
                var written = connection.bulkTransfer(bulkEndpoint, chunk, count, 5000)
                if (written < 0) { delay(100); written = connection.bulkTransfer(bulkEndpoint, chunk, count, 5000) }
                if (written < 0) {
                    Log.e(TAG, "USB bulkTransfer failed with code $written")
                    return@withContext false
                }
                offset += count
            }
            delay(300)
            Log.i(TAG, "Printed ${data.size} bytes successfully via USB OTG")
            true
        } catch (e: Exception) {
            Log.e(TAG, "USB print error: ${e.message}", e)
            false
        } finally {
            try { claimed?.let { connection.releaseInterface(it) } } catch (ignored: Exception) {}
            try { connection.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Sends raw ESC/POS bytes over TCP socket to a Network / LAN thermal printer (Port 9100).
     * Retries the connection once (Wi-Fi printers often sleep) and waits briefly before closing.
     */
    suspend fun printToNetworkPrinter(
        ipAddress: String,
        port: Int = 9100,
        data: ByteArray,
        timeoutMs: Int = 5000
    ): Boolean = withContext(Dispatchers.IO) {
        for (attempt in 1..2) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(ipAddress, port), timeoutMs)
                socket.soTimeout = timeoutMs
                val outStream = socket.getOutputStream()
                writePaced(outStream, data, chunk = 4096, pauseMs = 5)
                delay(300)
                Log.i(TAG, "Printed ${data.size} bytes successfully to Network printer: $ipAddress:$port")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Network printer error on $ipAddress:$port (attempt $attempt): ${e.message}", e)
                if (attempt == 1) delay(700)
            } finally {
                try { socket?.close() } catch (ignored: Exception) {}
            }
        }
        false
    }

    /**
     * Hands off a PDF file to Android's built-in PrintManager.
     * Works with ANY printer installed on the system (WiFi, Mopria, Cloud Print, Laser/Inkjet).
     */
    fun printDocumentWithSystem(
        context: Context,
        pdfFile: File,
        jobName: String
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "System print service unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        printManager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal,
                callback: LayoutResultCallback,
                extras: Bundle
            ) {
                if (cancellationSignal.isCanceled) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal,
                callback: WriteResultCallback
            ) {
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }, PrintAttributes.Builder().build())
    }

    /**
     * Checks if Android has at least one active print service enabled.
     * If false, the Android system spooler will default to "Save as PDF" because no printer can be discovered.
     */
    fun isSystemPrintServiceEnabled(context: Context): Boolean {
        return try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager != null) {
                val method = printManager.javaClass.methods.firstOrNull { it.name == "getPrintServices" }
                if (method != null) {
                    val list = method.invoke(printManager, 1) as? List<*> // 1 = PRINT_SERVICES_ENABLED
                    if (list != null) return list.isNotEmpty()
                }
            }
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_print_services")
            !enabled.isNullOrBlank()
        } catch (e: Exception) {
            true // fallback to avoid false alarms
        }
    }

    /**
     * Launches Android's official System Print Settings screen directly in 1 click.
     * Allows the user to toggle "Default Print Service" ON or install brand plugins (HP, Canon, Epson, Mopria).
     */
    fun openSystemPrintSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_PRINT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open print settings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 4. Narrow Thermal Receipt PDF Generator (58mm / 80mm Continuous Roll)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Generates a narrow continuous thermal receipt PDF file tailored for 58mm or 80mm rolls.
     */
    fun generateThermalReceiptPdf(
        context: Context,
        company: Company?,
        voucherWithAllEntries: VoucherWithAllEntries,
        stockItems: List<StockItem> = emptyList(),
        customerName: String? = null,
        paperWidth: PaperWidth = PaperWidth.MM_58
    ): File {
        val widthPt = paperWidth.widthPt
        val itemCount = voucherWithAllEntries.inventoryEntries.size
        val heightPt = (280f + (itemCount * 26f)).coerceAtLeast(360f)

        val pageSize = Rectangle(widthPt, heightPt)
        val document = Document(pageSize, 6f, 6f, 10f, 10f)

        val fileName = "Receipt_${voucherWithAllEntries.voucher.voucherNumber.replace("/", "_")}_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null), fileName)

        val writer = PdfWriter.getInstance(document, FileOutputStream(file))
        document.open()

        val fontTitle = Font(Font.HELVETICA, 10f, Font.BOLD)
        val fontHeader = Font(Font.HELVETICA, 7.5f, Font.BOLD)
        val fontRegular = Font(Font.HELVETICA, 7f, Font.NORMAL)
        val fontSmall = Font(Font.HELVETICA, 6f, Font.NORMAL)
        val fontBold = Font(Font.HELVETICA, 7.5f, Font.BOLD)

        // Store Name
        val pStore = Paragraph(company?.name ?: "TELLER INVOICE", fontTitle)
        pStore.alignment = Element.ALIGN_CENTER
        document.add(pStore)

        company?.address?.takeIf { it.isNotBlank() }?.let {
            val p = Paragraph(it, fontSmall)
            p.alignment = Element.ALIGN_CENTER
            document.add(p)
        }
        company?.phoneNumber?.takeIf { it.isNotBlank() }?.let {
            val p = Paragraph("Ph: $it", fontSmall)
            p.alignment = Element.ALIGN_CENTER
            document.add(p)
        }
        company?.gstin?.takeIf { it.isNotBlank() }?.let {
            val p = Paragraph("GSTIN: $it", fontSmall)
            p.alignment = Element.ALIGN_CENTER
            document.add(p)
        }

        // Divider
        val pDiv = Paragraph("--------------------------------------------------", fontSmall)
        pDiv.alignment = Element.ALIGN_CENTER
        document.add(pDiv)

        // Metadata table
        val metaTable = PdfPTable(2)
        metaTable.widthPercentage = 100f
        metaTable.setWidths(floatArrayOf(1.2f, 1f))

        val sdf = SimpleDateFormat("dd/MM/yy hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(voucherWithAllEntries.voucher.date ?: Date())

        fun addMetaCell(text: String, align: Int) {
            val cell = PdfPCell(Paragraph(text, fontSmall))
            cell.border = PdfPCell.NO_BORDER
            cell.horizontalAlignment = align
            metaTable.addCell(cell)
        }

        addMetaCell("Inv: #${voucherWithAllEntries.voucher.voucherNumber}", Element.ALIGN_LEFT)
        addMetaCell(dateStr, Element.ALIGN_RIGHT)
        if (!customerName.isNullOrBlank()) {
            addMetaCell("Cust: $customerName", Element.ALIGN_LEFT)
            addMetaCell(voucherWithAllEntries.voucher.type, Element.ALIGN_RIGHT)
        }
        document.add(metaTable)
        document.add(pDiv)

        // Line Items
        val stockItemMap = stockItems.associateBy { it.id }
        var subTotal = 0.0
        var totalTax = 0.0

        if (voucherWithAllEntries.inventoryEntries.isNotEmpty()) {
            val itemTable = PdfPTable(2)
            itemTable.widthPercentage = 100f
            itemTable.setWidths(floatArrayOf(2f, 1f))

            fun addItemCell(text: String, font: Font, align: Int) {
                val cell = PdfPCell(Paragraph(text, font))
                cell.border = PdfPCell.NO_BORDER
                cell.horizontalAlignment = align
                itemTable.addCell(cell)
            }

            addItemCell("Item", fontHeader, Element.ALIGN_LEFT)
            addItemCell("Amount", fontHeader, Element.ALIGN_RIGHT)

            voucherWithAllEntries.inventoryEntries.forEach { entry ->
                val itemName = stockItemMap[entry.stockItemId]?.name ?: "Item #${entry.stockItemId}"
                val lineAmount = if (entry.totalAmount > 0) entry.totalAmount else entry.amount
                val taxBase = if (entry.taxableAmount > 0L) entry.taxableAmount.paiseToRupees() else (entry.quantity * entry.rate)
                subTotal += taxBase
                totalTax += entry.taxAmount.paiseToRupees()

                addItemCell(itemName, fontRegular, Element.ALIGN_LEFT)
                addItemCell("₹%.2f".format(lineAmount.paiseToRupees()), fontRegular, Element.ALIGN_RIGHT)

                addItemCell("  %.2f x %.2f".format(entry.quantity, entry.rate), fontSmall, Element.ALIGN_LEFT)
                addItemCell("", fontSmall, Element.ALIGN_RIGHT)
            }
            document.add(itemTable)
            document.add(pDiv)
        }

        // Totals
        val grandTotal = if (voucherWithAllEntries.inventoryEntries.isNotEmpty()) {
            voucherWithAllEntries.inventoryEntries.sumOf { if (it.totalAmount > 0) it.totalAmount else it.amount }
        } else {
            voucherWithAllEntries.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
        }

        val totalTable = PdfPTable(2)
        totalTable.widthPercentage = 100f
        totalTable.setWidths(floatArrayOf(1.5f, 1f))

        fun addTotalCell(label: String, value: String, isBold: Boolean = false) {
            val f = if (isBold) fontBold else fontSmall
            val cellL = PdfPCell(Paragraph(label, f))
            cellL.border = PdfPCell.NO_BORDER
            cellL.horizontalAlignment = Element.ALIGN_LEFT
            totalTable.addCell(cellL)

            val cellR = PdfPCell(Paragraph(value, f))
            cellR.border = PdfPCell.NO_BORDER
            cellR.horizontalAlignment = Element.ALIGN_RIGHT
            totalTable.addCell(cellR)
        }

        if (totalTax > 0) {
            addTotalCell("Taxable Base:", "₹%.2f".format(subTotal))
            addTotalCell("GST Tax:", "₹%.2f".format(totalTax))
            document.add(totalTable)
            document.add(pDiv)
            totalTable.deleteBodyRows()
        }

        addTotalCell("TOTAL:", "₹%.2f".format(grandTotal.paiseToRupees()), isBold = true)
        document.add(totalTable)
        document.add(pDiv)

        // Footer & UPI QR
        company?.upiId?.takeIf { it.isNotBlank() }?.let { upiId ->
            try {
                val upiUri = UpiQrHelper.buildUpiUri(
                    upiId = upiId,
                    payeeName = company.name,
                    amount = grandTotal.paiseToRupees(),
                    invoiceNumber = voucherWithAllEntries.voucher.voucherNumber
                )
                val qrPngBytes = UpiQrHelper.generateQrPngBytes(upiUri, 180)
                if (qrPngBytes != null) {
                    val pPrompt = Paragraph("Scan to Pay via UPI", fontHeader)
                    pPrompt.alignment = Element.ALIGN_CENTER
                    document.add(pPrompt)

                    val qrImg = com.lowagie.text.Image.getInstance(qrPngBytes)
                    qrImg.alignment = Element.ALIGN_CENTER
                    qrImg.scaleToFit(110f, 110f)
                    document.add(qrImg)
                }
            } catch (_: Exception) {}
            val p = Paragraph("UPI: $upiId", fontSmall)
            p.alignment = Element.ALIGN_CENTER
            document.add(p)
            document.add(pDiv)
        }

        val pFooter1 = Paragraph("Thank You! Visit Again", fontHeader)
        pFooter1.alignment = Element.ALIGN_CENTER
        document.add(pFooter1)

        val pFooter2 = Paragraph("Powered by Teller", fontSmall)
        pFooter2.alignment = Element.ALIGN_CENTER
        document.add(pFooter2)

        document.close()
        return file
    }
}
