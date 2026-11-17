package com.minhas.teller.utils

import android.content.Context
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.web.WebCompanionManager
import java.io.File

/**
 * Printing for someone who has never set up a printer.
 *
 * The Printer Hub asks the user to understand connection *modes* (Bluetooth / USB / Wi-Fi /
 * Counter PC / Always Ask), IP addresses and paper widths before anything comes out of the
 * printer. Most shopkeepers can't answer any of that, so this reverses it: we look at what the
 * phone can already reach and offer the handful of real devices by name. The first successful
 * choice is remembered, so from then on printing is one tap with no questions at all.
 *
 * Nothing here runs until the user taps Print on an invoice they are already looking at.
 */
object EasyPrint {

    enum class Kind { BLUETOOTH, WIFI, USB, COUNTER_PC, SYSTEM }

    /**
     * One thing the user can print to, described the way they'd describe it themselves —
     * the printer's own name, not the protocol.
     */
    data class Target(
        val kind: Kind,
        val label: String,
        val detail: String,
        val address: String? = null,
        val port: Int = 9100,
        /** Only for [Kind.USB] — a USB printer is a live handle, not an address we can save. */
        val usbDevice: Any? = null
    )

    sealed class Outcome {
        /** Bytes went to a thermal printer. */
        object Printed : Outcome()
        /** Queued for the shop computer to print. */
        object Queued : Outcome()
        /** Handed to Android's print spooler — its own UI takes over from here. */
        object Dispatched : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    private val systemTarget = Target(
        kind = Kind.SYSTEM,
        label = "A4 / office printer",
        detail = "Choose from printers Android already knows"
    )

    /**
     * The printer we've printed to before, if it still looks usable. When this is non-null the
     * Print button must not ask anything — it just prints.
     */
    fun savedTarget(context: Context): Target? {
        if (!PrinterPrefs.isOneClickEnabled(context)) return null
        return when (PrinterPrefs.getDefaultMode(context)) {
            PrinterPrefs.MODE_THERMAL_BT -> {
                val (address, name) = PrinterPrefs.getBluetoothDevice(context)
                if (address.isNullOrBlank()) null
                else Target(Kind.BLUETOOTH, name ?: "Bill printer", "Bluetooth", address)
            }
            PrinterPrefs.MODE_THERMAL_WIFI -> {
                val (ip, port, name) = PrinterPrefs.getWifiPrinter(context)
                if (ip.isBlank()) null else Target(Kind.WIFI, name, "Wi-Fi · $ip", ip, port)
            }
            PrinterPrefs.MODE_THERMAL_USB -> {
                val savedName = PrinterPrefs.getUsbDeviceName(context)
                // A USB printer has to be physically present; re-resolve it by name each time.
                ThermalPrinterHelper.getUsbPrinters(context)
                    .firstOrNull { savedName == null || it.name == savedName }
                    ?.let { Target(Kind.USB, it.name, "USB cable", usbDevice = it.device) }
            }
            PrinterPrefs.MODE_COUNTER_PC -> Target(Kind.COUNTER_PC, "Shop computer", "Printer attached to your PC")
            PrinterPrefs.MODE_A4_SYSTEM -> systemTarget
            else -> null
        }
    }

    /**
     * Everything this phone can find, for the setup screen: paired Bluetooth printers, Bluetooth-LE
     * printers switched on nearby, USB, any Wi-Fi printers already discovered, the shop PC and the
     * A4/system option. Slower than [targets] because it scans — show a spinner.
     */
    suspend fun discover(
        context: Context,
        wifiPrinters: List<DiscoveredWifiPrinter> = emptyList()
    ): List<Target> {
        val found = linkedMapOf<String, Target>()
        if (ThermalPrinterHelper.hasBluetoothPermission(context)) {
            ThermalPrinterHelper.getPairedPrinters(context).forEach {
                found["bt:" + it.address] =
                    Target(Kind.BLUETOOTH, it.name.ifBlank { "Bill printer" }, "Bluetooth bill printer", it.address)
            }
            ThermalPrinterHelper.scanBlePrinters(context).forEach {
                found.getOrPut("bt:" + it.address) {
                    Target(Kind.BLUETOOTH, it.name.ifBlank { "Bill printer" }, "Bluetooth printer nearby", it.address)
                }
            }
        }
        ThermalPrinterHelper.getUsbPrinters(context).forEach {
            found["usb:" + it.name] = Target(Kind.USB, it.name, "Plugged in by USB cable", usbDevice = it.device)
        }
        wifiPrinters.forEach {
            found["wifi:" + it.host] =
                Target(Kind.WIFI, it.name.ifBlank { "Network printer" }, "Wi-Fi · " + it.host, it.host, it.port)
        }
        if (WebCompanionManager.isPrintStationActive.value) {
            found["pc"] = Target(Kind.COUNTER_PC, "Shop computer", "Printer attached to your PC")
        }
        found["system"] = systemTarget
        return found.values.toList()
    }

    /** Prints a short "printer works" slip. A4/system printers have no test slip — see the UI. */
    suspend fun printTest(context: Context, target: Target): Outcome {
        val bytes = ThermalPrinterHelper.buildTestReceiptBytes(target.label, PrinterPrefs.getPaperWidth(context))
        return when (target.kind) {
            Kind.BLUETOOTH -> {
                val address = target.address ?: return Outcome.Failed("Pick your Bluetooth printer first")
                if (ThermalPrinterHelper.printToBluetoothDevice(address, bytes, context)) Outcome.Printed
                else Outcome.Failed(target.label + " did not respond. Is it switched on?")
            }
            Kind.WIFI -> {
                val ip = target.address ?: return Outcome.Failed("No Wi-Fi printer address")
                if (ThermalPrinterHelper.printToNetworkPrinter(ip, target.port, bytes)) Outcome.Printed
                else Outcome.Failed(target.label + " did not respond at " + ip)
            }
            Kind.USB -> printUsb(context, target, bytes)
            Kind.COUNTER_PC -> Outcome.Failed("Open a bill and press Print to test the shop computer")
            Kind.SYSTEM -> Outcome.Failed("A4 printers are chosen by Android when you press Print")
        }
    }

    private suspend fun printUsb(context: Context, target: Target, bytes: ByteArray): Outcome {
        val device = target.usbDevice ?: return Outcome.Failed("Plug the printer in again")
        if (!ThermalPrinterHelper.requestUsbPermission(context, device)) {
            return Outcome.Failed("Allow Teller to use the USB printer")
        }
        return if (ThermalPrinterHelper.printToUsbDevice(context, device, bytes)) Outcome.Printed
        else Outcome.Failed(target.label + " did not respond over USB")
    }

    /**
     * Everything reachable right now, best guess first. Bluetooth printers are listed by their
     * own name because that is the sticker on the device; the A4/system option is always last so
     * a thermal bill printer is what a shop sees at the top.
     */
    fun targets(context: Context): List<Target> {
        val found = mutableListOf<Target>()
        if (ThermalPrinterHelper.hasBluetoothPermission(context)) {
            ThermalPrinterHelper.getPairedPrinters(context).forEach { device ->
                found.add(
                    Target(
                        kind = Kind.BLUETOOTH,
                        label = device.name.ifBlank { "Bill printer" },
                        detail = "Bluetooth bill printer",
                        address = device.address
                    )
                )
            }
        } else {
            // Can't read the paired list without the permission, so offer the option and ask
            // for it only if they actually pick Bluetooth.
            found.add(Target(Kind.BLUETOOTH, "Bluetooth bill printer", "Tap to allow and pick your printer"))
        }
        val (ip, port, name) = PrinterPrefs.getWifiPrinter(context)
        if (PrinterPrefs.getDefaultMode(context) == PrinterPrefs.MODE_THERMAL_WIFI && ip.isNotBlank()) {
            found.add(Target(Kind.WIFI, name, "Wi-Fi · $ip", ip, port))
        }
        if (WebCompanionManager.isPrintStationActive.value) {
            found.add(Target(Kind.COUNTER_PC, "Shop computer", "Printer attached to your PC"))
        }
        found.add(systemTarget)
        return found
    }

    /** True when choosing [target] needs the Android 12+ "Nearby devices" permission first. */
    fun needsBluetoothPermission(context: Context, target: Target): Boolean =
        target.kind == Kind.BLUETOOTH && !ThermalPrinterHelper.hasBluetoothPermission(context)

    /** Remembers [target] so the next Print is a single tap. */
    fun remember(context: Context, target: Target) {
        when (target.kind) {
            Kind.BLUETOOTH -> {
                val address = target.address ?: return
                PrinterPrefs.setBluetoothDevice(context, address, target.label)
                PrinterPrefs.setDefaultMode(context, PrinterPrefs.MODE_THERMAL_BT)
            }
            Kind.WIFI -> {
                val ip = target.address ?: return
                PrinterPrefs.setWifiPrinter(context, ip, target.port, target.label)
                PrinterPrefs.setDefaultMode(context, PrinterPrefs.MODE_THERMAL_WIFI)
            }
            Kind.USB -> {
                PrinterPrefs.setUsbDeviceName(context, target.label)
                PrinterPrefs.setDefaultMode(context, PrinterPrefs.MODE_THERMAL_USB)
            }
            Kind.COUNTER_PC -> PrinterPrefs.setDefaultMode(context, PrinterPrefs.MODE_COUNTER_PC)
            Kind.SYSTEM -> PrinterPrefs.setDefaultMode(context, PrinterPrefs.MODE_A4_SYSTEM)
        }
        PrinterPrefs.setOneClickEnabled(context, true)
    }

    /** Forgets the saved printer, so the next Print asks again. */
    fun forget(context: Context) = PrinterPrefs.resetDefaultPrinter(context)

    /**
     * Prints [pdf] (A4 paths) or the voucher's receipt (thermal paths) to [target].
     *
     * [voucherId] is needed because a thermal printer takes ESC/POS text built from the voucher,
     * not the PDF — the same bill, rendered for 58/80mm paper. A thermal print without a voucher
     * to build from fails rather than printing something that isn't the invoice.
     */
    suspend fun print(
        context: Context,
        target: Target,
        pdf: File,
        jobName: String,
        voucherId: Int
    ): Outcome = when (target.kind) {
        Kind.SYSTEM -> {
            ExportUtils.printPdf(context, pdf, jobName)
            Outcome.Dispatched
        }
        Kind.COUNTER_PC -> {
            if (voucherId <= 0) Outcome.Failed("Can't send this document to the shop computer")
            else {
                WebCompanionManager.queuePrintJob(voucherId)
                Outcome.Queued
            }
        }
        Kind.BLUETOOTH -> {
            val address = target.address
            val bytes = receiptBytes(context, voucherId)
            when {
                address.isNullOrBlank() -> Outcome.Failed("Pick your Bluetooth printer first")
                bytes == null -> Outcome.Failed("This document can only go to an A4 printer")
                ThermalPrinterHelper.printToBluetoothDevice(address, bytes, context) -> Outcome.Printed
                else -> Outcome.Failed("${target.label} did not respond. Is it switched on?")
            }
        }
        Kind.USB -> {
            val bytes = receiptBytes(context, voucherId)
            if (bytes == null) Outcome.Failed("This document can only go to an A4 printer")
            else printUsb(context, target, bytes)
        }
        Kind.WIFI -> {
            val ip = target.address
            val bytes = receiptBytes(context, voucherId)
            when {
                ip.isNullOrBlank() -> Outcome.Failed("No Wi-Fi printer address saved")
                bytes == null -> Outcome.Failed("This document can only go to an A4 printer")
                ThermalPrinterHelper.printToNetworkPrinter(ip, target.port, bytes) -> Outcome.Printed
                else -> Outcome.Failed("${target.label} did not respond at $ip")
            }
        }
    }

    /** Builds the thermal receipt for a voucher, or null when there's no voucher behind the PDF. */
    private suspend fun receiptBytes(context: Context, voucherId: Int): ByteArray? {
        if (voucherId <= 0) return null
        return try {
            val db = AppDatabase.getDatabase(context)
            val vwe = db.voucherDao().getVoucherWithAllEntriesById(voucherId) ?: return null
            val company = db.companyDao().getCompanyByIdSync(vwe.voucher.companyId)
            val stockItems = db.stockItemDao().getAllStockItems(vwe.voucher.companyId)
            val ledgers = db.ledgerDao().getAllLedgersSync(vwe.voucher.companyId)
            val isSales = vwe.voucher.type == "Sales"
            val partyEntry = vwe.voucherEntries.firstOrNull { if (isSales) it.isDebit else !it.isDebit }
            ThermalPrinterHelper.buildReceiptBytes(
                company = company,
                voucherWithAllEntries = vwe,
                stockItems = stockItems,
                customerName = ledgers.firstOrNull { it.id == partyEntry?.ledgerId }?.name ?: "Counter Sale",
                paperWidth = PrinterPrefs.getPaperWidth(context)
            )
        } catch (e: Exception) {
            android.util.Log.e("EasyPrint", "receiptBytes failed for voucher $voucherId", e)
            null
        }
    }
}
