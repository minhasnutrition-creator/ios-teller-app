package com.minhas.teller.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Unit
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.entities.LedgerWithBalance
import com.minhas.teller.data.entities.EvidenceAttachment
import com.minhas.teller.ui.viewmodels.TellerScoreData
import com.lowagie.text.*
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import com.lowagie.text.pdf.PdfContentByte
import com.lowagie.text.pdf.draw.LineSeparator
import com.lowagie.text.pdf.PdfPageEventHelper
import com.lowagie.text.pdf.PdfShading
import com.lowagie.text.pdf.PdfShadingPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.text.DecimalFormat
import java.util.*

/**
 * Line's pre-tax value.
 *
 * Inventory amounts are stored TAX-INCLUSIVE — `quantity * rate` is the gross figure
 * the customer pays, and `taxableAmount` is the base back-computed from it. Invoice
 * summaries must start from the base, otherwise adding tax on top double-counts it
 * (a ₹50,000 inclusive sale printed as 50,000 + 2,381 = ₹52,381).
 *
 * Falls back to gross-minus-tax for rows saved before `taxableAmount` was populated.
 */
internal fun InventoryEntry.taxableValue(): Double =
    if (taxableAmount > 0L) taxableAmount.paiseToRupees() else (quantity * rate) - taxAmount.paiseToRupees()

/**
 * Line's pre-tax unit rate, so the printed `quantity x rate` reconciles with the
 * printed line amount and the subtotal column actually adds up.
 */
internal fun InventoryEntry.taxableRate(): Double =
    if (quantity != 0.0) taxableValue() / quantity else rate

/** Formats a tax rate percentage for invoice labels, dropping a trailing ".0" (18.0 -> "18"). */
internal fun fmtRate(rate: Double): String =
    if (rate == rate.toLong().toDouble()) rate.toLong().toString() else rate.toString()

/**
 * Item-wise detail is sometimes gone (e.g. a voucher pulled from a cloud backup whose stock-item
 * catalog was never fully synced): [InventoryEntry] rows are empty even though the voucher clearly
 * has a value. Printing ₹0.00 would be actively wrong — the ledger postings still know the true
 * amount, so fall back to a single undetailed line rather than a false zero.
 */
private fun unknownLine(voucherId: Int, amountPaise: Long) = InventoryEntry(
    voucherId = voucherId, stockItemId = 0, quantity = 1.0, rate = amountPaise.paiseToRupees(),
    amount = amountPaise, taxableAmount = amountPaise, totalAmount = amountPaise
)

private fun List<InventoryEntry>.orFallback(voucherId: Int, voucherEntries: List<VoucherEntry>): List<InventoryEntry> {
    if (isNotEmpty()) return this
    val fallback = voucherEntries.maxOfOrNull { it.amount } ?: 0L
    return if (fallback > 0L) listOf(unknownLine(voucherId, fallback)) else this
}

/**
 * Amount actually payable for these lines.
 *
 * Because amounts are stored tax-inclusive, this is just the sum of the line totals —
 * adding tax on top would double-count it and (for the UPI QR) ask the customer to
 * pay more than the invoice says.
 */
internal fun List<InventoryEntry>.payableTotal(): Double {
    val stored = sumOf { it.totalAmount }
    return if (stored > 0L) stored.paiseToRupees() else sumOf { it.amount }.paiseToRupees()
}

object ExportUtils {
    // Dedicated scope replaces GlobalScope — tied to object lifetime, cancelled if process dies
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        com.minhas.teller.TellerApp.configureGlobalProperties()
    }

    private const val SHARE_LINK_BASE = "https://teller.finance/invoice/"

    enum class ReportTheme {
        GENERAL, SALES, PURCHASE, OUTSTANDING, DAYBOOK, STOCK
    }

    fun exportOutstandingsToPdf(context: Context, ledgers: List<LedgerWithBalance>) {
        val fileName = "Outstandings_${System.currentTimeMillis()}.pdf"
        val data = ledgers.map { listOf(it.ledger.name, it.ledger.group, if (it.balance >= 0) "${it.balance} Dr" else "${Math.abs(it.balance)} Cr") }
        exportReportToPdf(context, fileName, "", "Outstanding Payments Summary", listOf("Ledger Name", "Group", "Amount"), floatArrayOf(3f, 2f, 2f), data)
    }

    fun exportOutstandingsToExcel(context: Context, ledgers: List<LedgerWithBalance>) {
        val fileName = "Outstandings_${System.currentTimeMillis()}.xlsx"
        val data = ledgers.map { listOf(it.ledger.name, it.ledger.group, if (it.balance >= 0) "${it.balance} Dr" else "${Math.abs(it.balance)} Cr") }
        exportReportToExcel(context, fileName, "Outstandings", listOf("Ledger Name", "Group", "Amount"), data)
    }

    fun exportTrialBalanceToPdf(context: Context, data: List<Triple<String, Double, Double>>) {
        val fileName = "TrialBalance_${System.currentTimeMillis()}.pdf"
        val rows = data.map { listOf(it.first, String.format("%.2f", it.second), String.format("%.2f", it.third)) }
        exportReportToPdf(context, fileName, "", "Trial Balance Report", listOf("Ledger Name", "Debit", "Credit"), floatArrayOf(3f, 1.5f, 1.5f), rows)
    }

    fun exportTrialBalanceToExcel(context: Context, data: List<Triple<String, Double, Double>>) {
        val fileName = "TrialBalance_${System.currentTimeMillis()}.xlsx"
        val rows = data.map { listOf(it.first, String.format("%.2f", it.second), String.format("%.2f", it.third)) }
        exportReportToExcel(context, fileName, "Trial Balance", listOf("Ledger Name", "Debit", "Credit"), rows)
    }

    fun exportDaybookToPdf(context: Context, vouchers: List<VoucherWithAllEntries>) {
        val fileName = "Daybook_${System.currentTimeMillis()}.pdf"
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val rows = mutableListOf<List<String>>()
        vouchers.forEach { v ->
            v.voucherEntries.forEach { e ->
                rows.add(listOf(dateFormatter.format(v.voucher.date), v.voucher.voucherNumber, v.voucher.type, if (e.isDebit) "Dr" else "Cr", e.amount.paiseToRupees().toString()))
            }
        }
        exportReportToPdf(context, fileName, "", "Daybook Report", listOf("Date", "Vch No", "Type", "Side", "Amount"), floatArrayOf(1.5f, 1.5f, 1.5f, 1f, 2f), rows)
    }

    fun exportDaybookToExcel(context: Context, vouchers: List<VoucherWithAllEntries>) {
        val fileName = "Daybook_${System.currentTimeMillis()}.xlsx"
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val rows = mutableListOf<List<String>>()
        vouchers.forEach { v ->
            v.voucherEntries.forEach { e ->
                rows.add(listOf(dateFormatter.format(v.voucher.date), v.voucher.voucherNumber, v.voucher.type, if (e.isDebit) "Dr" else "Cr", e.amount.paiseToRupees().toString()))
            }
        }
        exportReportToExcel(context, fileName, "Daybook", listOf("Date", "Vch No", "Type", "Side", "Amount"), rows)
    }

    fun exportStockSummaryToPdf(context: Context, data: List<List<String>>) {
        val fileName = "StockSummary_${System.currentTimeMillis()}.pdf"
        exportReportToPdf(context, fileName, "", "Stock Summary Report", listOf("Stock Item", "Quantity", "Rate", "Value"), floatArrayOf(3f, 1.5f, 1.5f, 2f), data)
    }

    fun exportStockSummaryToExcel(context: Context, data: List<List<String>>) {
        val fileName = "StockSummary_${System.currentTimeMillis()}.xlsx"
        exportReportToExcel(context, fileName, "Stock Summary", listOf("Stock Item", "Quantity", "Rate", "Value"), data)
    }

    fun exportLiveStockToPdf(context: Context, data: List<List<String>>) {
        val fileName = "LiveStock_${System.currentTimeMillis()}.pdf"
        exportReportToPdf(context, fileName, "", "Live Stock Management", listOf("Vehicle", "In", "Out", "Total"), floatArrayOf(3f, 1f, 1f, 1f), data)
    }

    fun exportLiveStockToExcel(context: Context, data: List<List<String>>) {
        val fileName = "LiveStock_${System.currentTimeMillis()}.xlsx"
        exportReportToExcel(context, fileName, "Live Stock", listOf("Vehicle", "In", "Out", "Total"), data)
    }

    /**
     * Prints the government e-invoice details (IRN, Ack No/Date and the signed QR) at the end of an
     * invoice, but only when the shop owner has attached the portal's reply for this invoice.
     * Never throws - an invoice must always print even if this block fails.
     */
    private fun appendEInvoiceBlock(document: Document, context: Context, voucher: Voucher) {
        try {
            val rec = EInvoiceStore.get(voucher.companyId, voucher.id, context) ?: return
            val small = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, BaseColor(64, 64, 64))
            val bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, BaseColor(0, 0, 0))
            val table = PdfPTable(floatArrayOf(4f, 1.4f))
            table.widthPercentage = 100f
            table.setSpacingBefore(8f)
            val left = PdfPCell()
            left.border = Rectangle.BOX
            left.setPadding(6f)
            left.addElement(Paragraph("e-Invoice details", bold))
            left.addElement(Paragraph("IRN: ${rec.irn}", small))
            if (rec.ackNo.isNotBlank()) left.addElement(Paragraph("Ack No: ${rec.ackNo}", small))
            if (rec.ackDate.isNotBlank()) left.addElement(Paragraph("Ack Date: ${rec.ackDate}", small))
            table.addCell(left)
            val right = PdfPCell()
            right.border = Rectangle.BOX
            right.setPadding(4f)
            right.horizontalAlignment = Element.ALIGN_CENTER
            if (rec.signedQr.isNotBlank()) {
                // Large pixel size: the signed QR holds ~1000 characters and must stay scannable when printed.
                val img = BarcodeQRCode(rec.signedQr, 560, 560, null).image
                img.scaleAbsolute(100f, 100f)
                right.addElement(img)
            }
            table.addCell(right)
            document.add(table)
        } catch (e: Exception) {
            android.util.Log.e("ExportUtils", "e-invoice block skipped", e)
        }
    }

    fun exportInvoiceToPdf(
        context: Context,
        voucher: Voucher,
        customerLedger: Ledger?,
        inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>,
        units: List<Unit>,
        company: Company?,
        billAllocations: List<com.minhas.teller.data.entities.BillAllocation> = emptyList(),
        share: Boolean = true,
        voucherEntries: List<VoucherEntry> = emptyList(),
        onGenerated: (File) -> kotlin.Unit = {}
    ) {
        val fileName = "${voucher.type}_${voucher.voucherNumber.replace("/", "_")}_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null), fileName)
        val document = Document(PageSize.A4)
        val inventoryEntries = inventoryEntries.orFallback(voucher.id, voucherEntries)

        Toast.makeText(context, "Generating Invoice PDF...", Toast.LENGTH_SHORT).show()

        scope.launch {
            try {
                val writer = PdfWriter.getInstance(document, FileOutputStream(file))
                document.open()

                val template = DashboardPrefs.getInvoiceTemplate(context)
                when (template) {
                    DashboardPrefs.InvoiceTemplate.BLUE_YELLOW -> 
                        drawBlueYellow(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                    DashboardPrefs.InvoiceTemplate.MAROON_SIDEBAR -> 
                        drawMaroonSidebar(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                    DashboardPrefs.InvoiceTemplate.BLACK_ORANGE -> 
                        drawBlackOrange(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                    DashboardPrefs.InvoiceTemplate.TEAL_CLEAN -> 
                        drawTealClean(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                    DashboardPrefs.InvoiceTemplate.GST_CLASSIC ->
                        InvoiceTemplatesExtra.drawGstClassic(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                    DashboardPrefs.InvoiceTemplate.MINIMAL_MONO ->
                        InvoiceTemplatesExtra.drawMinimalMono(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                    DashboardPrefs.InvoiceTemplate.ROYAL_INDIGO ->
                        InvoiceTemplatesExtra.drawRoyalIndigo(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                    DashboardPrefs.InvoiceTemplate.GREEN_RETAIL ->
                        InvoiceTemplatesExtra.drawGreenRetail(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                    DashboardPrefs.InvoiceTemplate.LETTERHEAD ->
                        InvoiceTemplatesExtra.drawLetterhead(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                }
                appendEInvoiceBlock(document, context, voucher)
                
                document.close()
                val saved = saveFileToDownloads(context, file, fileName, "application/pdf")
                
                withContext(Dispatchers.Main) {
                    PdfNavigator.open(file.absolutePath, "${voucher.type} #${voucher.voucherNumber}", voucher.id)
                    onGenerated(file)
                    if (saved) {
                        Toast.makeText(context, "PDF saved to Downloads", Toast.LENGTH_SHORT).show()
                    }

                    // NOTE: removed the MockPaymentClearanceWorker enqueue that used to run here.
                    // It simulated a UPI payment for every Sales invoice PDF and created phantom
                    // "Auto-cleared via UPI payment" Receipts. Because it added GST on top of an
                    // already tax-inclusive amount, settling a bill left a spurious receipt for
                    // exactly the GST portion (e.g. a 7000 sale settled in full -> +1260 receipt).
                    // Payment receipts must come only from a real settlement action.
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Build a single invoice PDF synchronously and return the raw bytes. Used by the PC desktop
     * companion (TellerWebServer.handlePrintVoucher) so an invoice printed from a PC is
     * pixel-identical to the phone's — it goes through the exact same draw* renderers instead of
     * the simplified HTML mock that was used before. Returns null on any rendering failure.
     */
    fun buildInvoicePdfBytes(
        context: Context,
        voucher: Voucher,
        customerLedger: Ledger?,
        inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>,
        units: List<Unit>,
        company: Company?,
        billAllocations: List<com.minhas.teller.data.entities.BillAllocation> = emptyList(),
        voucherEntries: List<VoucherEntry> = emptyList()
    ): ByteArray? {
        val inventoryEntries = inventoryEntries.orFallback(voucher.id, voucherEntries)
        return try {
            val bos = ByteArrayOutputStream()
            val document = Document(PageSize.A4)
            val writer = PdfWriter.getInstance(document, bos)
            document.open()
            when (DashboardPrefs.getInvoiceTemplate(context)) {
                DashboardPrefs.InvoiceTemplate.BLUE_YELLOW -> drawBlueYellow(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                DashboardPrefs.InvoiceTemplate.MAROON_SIDEBAR -> drawMaroonSidebar(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                DashboardPrefs.InvoiceTemplate.BLACK_ORANGE -> drawBlackOrange(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                DashboardPrefs.InvoiceTemplate.TEAL_CLEAN -> drawTealClean(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                DashboardPrefs.InvoiceTemplate.GST_CLASSIC -> InvoiceTemplatesExtra.drawGstClassic(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                DashboardPrefs.InvoiceTemplate.MINIMAL_MONO -> InvoiceTemplatesExtra.drawMinimalMono(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                DashboardPrefs.InvoiceTemplate.ROYAL_INDIGO -> InvoiceTemplatesExtra.drawRoyalIndigo(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                DashboardPrefs.InvoiceTemplate.GREEN_RETAIL -> InvoiceTemplatesExtra.drawGreenRetail(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
                DashboardPrefs.InvoiceTemplate.LETTERHEAD -> InvoiceTemplatesExtra.drawLetterhead(document, writer, context, voucher, customerLedger, inventoryEntries, stockItems, units, company, billAllocations)
            }
            appendEInvoiceBlock(document, context, voucher)
            document.close()
            bos.toByteArray()
        } catch (e: Exception) {
            android.util.Log.e("ExportUtils", "buildInvoicePdfBytes failed", e)
            null
        }
    }

    internal fun generateStampBitmap(context: Context, company: Company): File? {
        val type = company.stampType
        if (type == 0) return null
        
        val width = 250
        val height = 150
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        
        val colorInt = if (company.stampColor != 0) company.stampColor else Color.RED
        val borderPaint = Paint().apply {
            color = colorInt
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        
        val textPaint = Paint().apply {
            color = colorInt
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        val stampText = (company.stampDesignName ?: company.name).uppercase()
        val displayName = if (stampText.length > 20) stampText.substring(0, 17) + "..." else stampText

        when (type) {
            1 -> {
                // Classic circle stamp
                val size = Math.min(width, height) - 10f
                val left = (width - size) / 2
                val top = (height - size) / 2
                borderPaint.strokeWidth = 4f
                canvas.drawCircle(width / 2f, height / 2f, size / 2f, borderPaint)
                
                borderPaint.strokeWidth = 1.5f
                canvas.drawCircle(width / 2f, height / 2f, (size / 2f) - 6f, borderPaint)
                
                textPaint.textSize = 12f
                canvas.drawText(displayName, width / 2f, height / 2f - 10f, textPaint)
                
                val centerPaint = Paint(textPaint).apply {
                    textSize = 14f
                    typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                }
                canvas.drawText("OFFICIAL", width / 2f, height / 2f + 10f, centerPaint)
                canvas.drawText("STAMP", width / 2f, height / 2f + 25f, textPaint)
            }
            2 -> {
                // Modern rounded rectangle
                val rect = RectF(10f, 10f, width - 10f, height - 10f)
                borderPaint.strokeWidth = 4f
                canvas.drawRoundRect(rect, 15f, 15f, borderPaint)
                
                val innerRect = RectF(16f, 16f, width - 16f, height - 16f)
                borderPaint.strokeWidth = 1f
                canvas.drawRoundRect(innerRect, 10f, 10f, borderPaint)
                
                textPaint.textSize = 13f
                canvas.drawText(displayName, width / 2f, 45f, textPaint)
                
                canvas.drawLine(20f, 60f, width - 20f, 60f, borderPaint)
                
                val boldPaint = Paint(textPaint).apply {
                    textSize = 18f
                    typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                }
                canvas.drawText("APPROVED", width / 2f, 90f, boldPaint)
                
                canvas.drawText("DATE: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())}", width / 2f, 120f, textPaint)
            }
            3 -> {
                // Elegant double border rectangle
                val rect1 = RectF(10f, 10f, width - 10f, height - 10f)
                borderPaint.strokeWidth = 4f
                canvas.drawRect(rect1, borderPaint)
                
                val rect2 = RectF(15f, 15f, width - 15f, height - 15f)
                borderPaint.strokeWidth = 1f
                canvas.drawRect(rect2, borderPaint)
                
                textPaint.textSize = 13f
                canvas.drawText(displayName, width / 2f, 45f, textPaint)
                
                val elegantPaint = Paint(textPaint).apply {
                    textSize = 20f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                }
                canvas.drawText("PAID", width / 2f, 95f, elegantPaint)
                
                canvas.drawText("TELLER FINANCE", width / 2f, 125f, textPaint)
            }
        }
        
        return try {
            val file = File(context.cacheDir, "temp_stamp_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Loads a logo / signature / stamp for the PDF. Goes through [PdfImages] rather than
     * Image.getInstance(path): OpenPDF decodes PNG with javax.imageio, which Android does not
     * have, and the resulting NoClassDefFoundError is an Error that no `catch (Exception)` stops.
     */
    private fun getSafeImage(path: String?): Image? = PdfImages.fromFile(path)

    internal fun getSafeScaledImage(path: String?, maxWidth: Float, maxHeight: Float): Image? {
        val img = getSafeImage(path) ?: return null
        return try {
            img.scaleToFit(maxWidth, maxHeight)
            img
        } catch (t: Throwable) {
            android.util.Log.w("ExportUtils", "Could not scale image $path", t)
            null
        }
    }

    internal fun getUpiQrCodeImage(context: Context, company: Company?, voucher: Voucher, inventoryEntries: List<InventoryEntry>): Image? {
        val upiId = company?.upiId
        if (upiId.isNullOrBlank()) return null
        return try {
            // Line amounts already include tax — summing them IS the payable total.
            val grandTotal = inventoryEntries.payableTotal()
            val formattedAmt = String.format(Locale.US, "%.2f", grandTotal)
            val qrContent = "upi://pay?pa=$upiId&pn=${android.net.Uri.encode(company.name)}&am=$formattedAmt&cu=INR"
            val qrCode = BarcodeQRCode(qrContent, 100, 100, null)
            val qrImg = qrCode.image
            qrImg.scaleAbsolute(70f, 70f)
            qrImg
        } catch (t: Throwable) {
            android.util.Log.w("ExportUtils", "UPI QR code could not be rendered", t)
            null
        }
    }

    // Template 1 - VIBRANT BLUE YELLOW
    /**
     * The invoice buyer name. For a Finance Sale the party ledger is the FINANCE COMPANY
     * and the real customer's name lives in voucher.customField, so we print the customer,
     * never the finance company. For normal sales the customer ledger name is the buyer.
     */
    internal fun invoiceBuyerName(voucher: Voucher, customerLedger: Ledger?): String {
        val grp = customerLedger?.group?.trim()?.lowercase() ?: ""
        val isFinanced = grp == "finance companies" || grp.startsWith("finance comp")
        val isCashOrBank = grp.contains("cash") || grp.contains("bank")
        
        val suffix = when {
            grp.contains("cash") -> " (Cash)"
            grp.contains("bank") -> " (Bank Transfer)"
            else -> ""
        }
        
        return if (isFinanced) {
            voucher.customField?.takeIf { it.isNotBlank() }
                ?: voucher.refDisplayName?.takeIf { it.isNotBlank() }
                ?: "Customer"
        } else if (isCashOrBank) {
            if (!voucher.customField.isNullOrBlank()) {
                voucher.customField!! + suffix
            } else {
                "Cash" // If customer name is not provided, defaults to Cash bill
            }
        } else {
            customerLedger?.name?.takeIf { it.isNotBlank() }
                ?: voucher.customField?.takeIf { it.isNotBlank() }
                ?: "N/A"
        }
    }

    /** Finance company name to show as "Financed by ...", or null for a normal sale. */
    internal fun invoiceFinancedBy(customerLedger: Ledger?): String? {
        val grp = customerLedger?.group?.trim()?.lowercase() ?: ""
        val isFinanced = grp == "finance companies" || grp.startsWith("finance comp")
        return if (isFinanced) customerLedger?.name?.takeIf { it.isNotBlank() } else null
    }

    private fun drawBlueYellow(
        document: Document, writer: PdfWriter, context: Context,
        voucher: Voucher, customerLedger: Ledger?, inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>, units: List<Unit>, company: Company?,
        billAllocations: List<com.minhas.teller.data.entities.BillAllocation>
    ) {
        val df = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val royalBlue = BaseColor(26, 57, 163)
        val neonYellow = BaseColor(220, 235, 60)
        val cream = BaseColor(245, 245, 215)
        val W = BaseColor.WHITE
        val B = BaseColor.BLACK
        
        val cb = writer.directContentUnder
        cb.setColorFill(royalBlue)
        cb.rectangle(0f, 0f, 595f, 842f)
        cb.fill()
        
        // Jagged right accent shape
        cb.setColorFill(neonYellow)
        cb.moveTo(595f, 842f)
        cb.lineTo(480f, 842f)
        cb.lineTo(520f, 500f)
        cb.lineTo(450f, 300f)
        cb.lineTo(540f, 100f)
        cb.lineTo(490f, 0f)
        cb.lineTo(595f, 0f)
        cb.closePath()
        cb.fill()

        val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 32f, neonYellow)
        val toFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20f, W)
        val metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10f, W)
        val companyFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, neonYellow)

        // Logo + title header row
        val headerT = PdfPTable(2)
        headerT.widthPercentage = 100f
        headerT.setWidths(floatArrayOf(1f, 2f))
        headerT.setSpacingAfter(10f)

        val logoCell = PdfPCell()
        logoCell.border = Rectangle.NO_BORDER
        logoCell.backgroundColor = royalBlue
        logoCell.setPadding(4f)
        logoCell.verticalAlignment = Element.ALIGN_MIDDLE
        val logoImg = getSafeScaledImage(company?.logoUri, 80f, 50f)
        if (logoImg != null) {
            logoImg.alignment = Element.ALIGN_LEFT
            logoCell.addElement(logoImg)
        }
        logoCell.addElement(Paragraph(company?.name ?: "Your Company", companyFont))
        if (!company?.address.isNullOrBlank()) logoCell.addElement(Paragraph(company!!.address, FontFactory.getFont(FontFactory.HELVETICA, 8f, BaseColor.WHITE)))
        if (!company?.phoneNumber.isNullOrBlank()) logoCell.addElement(Paragraph("Ph: ${company!!.phoneNumber}", FontFactory.getFont(FontFactory.HELVETICA, 8f, BaseColor.WHITE)))
        headerT.addCell(logoCell)

        val titleCell = PdfPCell()
        titleCell.border = Rectangle.NO_BORDER
        titleCell.backgroundColor = royalBlue
        titleCell.setPadding(4f)
        titleCell.verticalAlignment = Element.ALIGN_MIDDLE
        titleCell.addElement(Paragraph("Invoice", titleFont))
        titleCell.addElement(Paragraph(company?.name ?: "Your Company", companyFont))
        if (!company?.gstin.isNullOrBlank()) {
            titleCell.addElement(Paragraph("GSTIN: ${company.gstin}", metaFont))
        }
        headerT.addCell(titleCell)
        document.add(headerT)

        val recipientName = invoiceBuyerName(voucher, customerLedger)
        document.add(Paragraph("Recipient: $recipientName", toFont))
        document.add(Chunk.NEWLINE)
        
        val dateStr = df.format(voucher.date)
        // Blank fields are left out rather than printed as "N/A" — a bill should not
        // advertise what the shop never filled in.
        val addressStr = customerLedger?.alias?.takeIf { it.isNotBlank() }
        val grp = customerLedger?.group?.trim()?.lowercase() ?: ""
        val isFinanced = grp == "finance companies" || grp.startsWith("finance comp")
        document.add(
            Paragraph(
                listOfNotNull(addressStr?.let { "Address: $it" }, "Date: $dateStr").joinToString("      "),
                metaFont
            )
        )
        customerLedger?.gstin?.takeIf { it.isNotBlank() }?.let {
            document.add(Paragraph("GSTIN: $it", metaFont))
        }
        if (isFinanced && customerLedger != null) {
            document.add(Paragraph("Financed by - ${customerLedger.name}", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, neonYellow)))
        }
        document.add(Chunk.NEWLINE)

        val tbl = PdfPTable(6)
        tbl.widthPercentage = 100f
        tbl.setWidths(floatArrayOf(0.6f, 2.4f, 0.8f, 0.8f, 1.2f, 1.4f))
        tbl.setSpacingAfter(15f)

        val thf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, W)
        for (h in listOf("No", "Item Description", "HSN", "QTY", "Price", "Sub Total")) {
            val c = PdfPCell(Phrase(h, thf))
            c.border = Rectangle.NO_BORDER
            c.backgroundColor = BaseColor(15, 35, 120)
            c.setPaddingTop(8f)
            c.setPaddingBottom(8f)
            c.setPaddingLeft(6f)
            c.horizontalAlignment = if (h == "Item Description") Element.ALIGN_LEFT else Element.ALIGN_CENTER
            tbl.addCell(c)
        }
        
        var subTotal = 0.0
        val rowFont = FontFactory.getFont(FontFactory.HELVETICA, 10f, W)
        inventoryEntries.forEachIndexed { idx, entry ->
            val stockItem = stockItems.find { it.id == entry.stockItemId }
            val amount = entry.taxableValue()
            subTotal += amount
            
            val bg = if (idx % 2 == 0) BaseColor(26, 57, 163) else BaseColor(40, 70, 180)
            
            fun addC(text: String, align: Int = Element.ALIGN_CENTER) {
                val c = PdfPCell(Phrase(text, rowFont))
                c.border = Rectangle.NO_BORDER
                c.backgroundColor = bg
                c.setPaddingTop(6f)
                c.setPaddingBottom(6f)
                c.setPaddingLeft(6f)
                c.horizontalAlignment = align
                tbl.addCell(c)
            }
            
            addC("${idx + 1}.", Element.ALIGN_LEFT)
            addC(stockItem?.name ?: "N/A", Element.ALIGN_LEFT)
            addC(entry.hsnCode ?: "-")
            addC("%.0f".format(entry.quantity))
            addC("${company?.currencySymbol ?: "₹"}%.2f".format(entry.taxableRate()))
            addC("${company?.currencySymbol ?: "₹"}%.2f".format(amount))
        }
        document.add(tbl)

        val countryName = DashboardPrefs.getTaxCountry(context)
        val isIndia = countryName.equals("India", true)
        val taxLabel = TaxConfigManager.getConfigForCountry(countryName).taxName
        val igstLabel = if (isIndia) "IGST" else taxLabel

        var totalCgst = 0.0
        var totalSgst = 0.0
        var totalIgst = 0.0
        var taxRate = 0.0
        
        inventoryEntries.forEach { entry ->
            if (taxRate == 0.0 && entry.taxAmount > 0.0) taxRate = entry.gstRate
            val (cg, sg, ig) = GstComplianceRules.taxSplit(entry)
            totalCgst += cg
            totalSgst += sg
            totalIgst += ig
        }
        val total = subTotal + totalIgst + totalCgst + totalSgst
        val st = PdfPTable(2)
        st.widthPercentage = 45f
        st.horizontalAlignment = Element.ALIGN_RIGHT
        st.setWidths(floatArrayOf(1.5f, 1.2f))
        
        fun addStRow(label: String, valStr: String, hi: Boolean = false) {
            val bg = if (hi) neonYellow else BaseColor(26, 57, 163)
            val tc = if (hi) B else W
            val font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, tc)
            
            val c1 = PdfPCell(Phrase(label, font))
            c1.border = Rectangle.NO_BORDER
            c1.backgroundColor = bg
            c1.setPaddingTop(6f)
            c1.setPaddingBottom(6f)
            c1.setPaddingLeft(8f)
            c1.horizontalAlignment = Element.ALIGN_LEFT
            st.addCell(c1)
            
            val c2 = PdfPCell(Phrase(valStr, font))
            c2.border = Rectangle.NO_BORDER
            c2.backgroundColor = bg
            c2.setPaddingTop(6f)
            c2.setPaddingBottom(6f)
            c2.setPaddingRight(8f)
            c2.horizontalAlignment = Element.ALIGN_RIGHT
            st.addCell(c2)
        }
        
        addStRow("Sub Total", "${company?.currencySymbol ?: "₹"}%.2f".format(subTotal))
        addStRow("Discount", "${company?.currencySymbol ?: "₹"}0.00")
        
        if (totalIgst > 0.0) {
            addStRow("${if (isIndia) "IGST" else taxLabel} (${fmtRate(taxRate)}%)", "${company?.currencySymbol ?: "₹"}%.2f".format(totalIgst))
        } else {
            if (totalCgst > 0.0) {
                addStRow("CGST (${fmtRate(taxRate / 2)}%)", "${company?.currencySymbol ?: "₹"}%.2f".format(totalCgst))
            }
            if (totalSgst > 0.0) {
                addStRow("SGST (${fmtRate(taxRate / 2)}%)", "${company?.currencySymbol ?: "₹"}%.2f".format(totalSgst))
            }
        }
        
        addStRow("Total", "${company?.currencySymbol ?: "₹"}%.2f".format(total), true)
        document.add(st)
        document.add(Paragraph(InvoiceTemplatesExtra.amountInWords(total), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, W)))
        document.add(Chunk.NEWLINE)
        
        // Add financed/HPA information if available
        val financedInfo = invoiceFinancedBy(customerLedger)
        if (!financedInfo.isNullOrBlank()) {
            val financeTable = PdfPTable(1)
            financeTable.widthPercentage = 100f
            
            val financeCell = PdfPCell()
            financeCell.border = Rectangle.NO_BORDER
            financeCell.backgroundColor = BaseColor(15, 35, 120)
            financeCell.setPadding(10f)
            financeCell.horizontalAlignment = Element.ALIGN_CENTER
            
            val financeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, neonYellow)
            financeCell.addElement(Phrase("FINANCED BY: $financedInfo", financeFont))
            
            financeTable.addCell(financeCell)
            document.add(financeTable)
            document.add(Chunk.NEWLINE)
        }

        // Add Dynamic UPI QR Code for instantaneous 1-tap customer payment
        val upiId = company?.upiId
        if (!upiId.isNullOrBlank()) {
            val upiUri = UpiQrHelper.buildUpiUri(
                upiId = upiId,
                payeeName = company.name,
                amount = total,
                invoiceNumber = voucher.voucherNumber
            )
            val qrPngBytes = UpiQrHelper.generateQrPngBytes(upiUri, 200)
            if (qrPngBytes != null) {
                val upiTable = PdfPTable(2)
                upiTable.widthPercentage = 100f
                upiTable.setWidths(floatArrayOf(1.2f, 2.8f))
                upiTable.setSpacingBefore(10f)

                val qrCell = PdfPCell()
                qrCell.border = Rectangle.NO_BORDER
                qrCell.horizontalAlignment = Element.ALIGN_CENTER
                val qrImg = PdfImages.fromBytes(qrPngBytes)
                if (qrImg != null) {
                    qrImg.scaleToFit(85f, 85f)
                    qrImg.alignment = Element.ALIGN_CENTER
                    qrCell.addElement(qrImg)
                }
                upiTable.addCell(qrCell)

                val infoCell = PdfPCell()
                infoCell.border = Rectangle.NO_BORDER
                infoCell.verticalAlignment = Element.ALIGN_MIDDLE
                infoCell.addElement(Paragraph("Scan & Pay via UPI (PhonePe / GPay / Paytm)", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, W)))
                infoCell.addElement(Paragraph("UPI ID: $upiId", FontFactory.getFont(FontFactory.HELVETICA, 9f, W)))
                infoCell.addElement(Paragraph("Exact Payable: ${company.currencySymbol ?: "₹"}%.2f".format(total), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, neonYellow)))
                upiTable.addCell(infoCell)

                document.add(upiTable)
                document.add(Chunk.NEWLINE)
            }
        }

        val sigStampTable = PdfPTable(2)
        sigStampTable.widthPercentage = 80f
        sigStampTable.horizontalAlignment = Element.ALIGN_CENTER
        sigStampTable.setWidths(floatArrayOf(1f, 1f))
        
        val sigCell = PdfPCell()
        sigCell.border = Rectangle.NO_BORDER
        sigCell.horizontalAlignment = Element.ALIGN_CENTER
        sigCell.verticalAlignment = Element.ALIGN_MIDDLE
        val sigImg = getSafeScaledImage(company?.signatureUri, 120f, 60f)
        if (sigImg != null) {
            sigImg.alignment = Element.ALIGN_CENTER
            sigCell.addElement(sigImg)
        }
        val sigLbl = Paragraph("Authorized Signature", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, W))
        sigLbl.alignment = Element.ALIGN_CENTER
        sigCell.addElement(sigLbl)
        sigStampTable.addCell(sigCell)
        
        val stampCell = PdfPCell()
        stampCell.border = Rectangle.NO_BORDER
        stampCell.horizontalAlignment = Element.ALIGN_CENTER
        stampCell.verticalAlignment = Element.ALIGN_MIDDLE
        val stampFile = company?.let { generateStampBitmap(context, it) }
        val stampImg = getSafeScaledImage(stampFile?.absolutePath, 120f, 60f)
        if (stampImg != null) {
            stampImg.alignment = Element.ALIGN_CENTER
            stampCell.addElement(stampImg)
        }
        val stampLbl = Paragraph("Official Stamp", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, W))
        stampLbl.alignment = Element.ALIGN_CENTER
        stampCell.addElement(stampLbl)
        sigStampTable.addCell(stampCell)
        
        document.add(sigStampTable)
        document.add(Chunk.NEWLINE)

        val footTable = PdfPTable(3)
        footTable.widthPercentage = 100f
        footTable.setWidths(floatArrayOf(1f, 1.2f, 1f))
        footTable.setSpacingBefore(20f)
        
        val ftf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, B)
        fun addF(text: String) {
            val c = PdfPCell(Phrase(text, ftf))
            c.backgroundColor = cream
            c.border = Rectangle.NO_BORDER
            c.horizontalAlignment = Element.ALIGN_CENTER
            c.setPaddingTop(10f)
            c.setPaddingBottom(10f)
            footTable.addCell(c)
        }
        addF(company?.phoneNumber?.takeIf { it.isNotBlank() } ?: "")
        addF(company?.address?.takeIf { it.isNotBlank() } ?: "")
        addF(company?.email?.takeIf { it.isNotBlank() } ?: "")
        document.add(footTable)
        addInvoiceFooter(document, context, royalBlue)
    }

    // Template 2 - MAROON SIDEBAR
    private fun drawMaroonSidebar(
        document: Document, writer: PdfWriter, context: Context,
        voucher: Voucher, customerLedger: Ledger?, inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>, units: List<Unit>, company: Company?,
        billAllocations: List<com.minhas.teller.data.entities.BillAllocation>
    ) {
        val df = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val maroon = BaseColor(120, 20, 30)
        val offWhite = BaseColor(245, 245, 245)
        val orange = BaseColor(205, 90, 50)
        val W = BaseColor.WHITE
        val B = BaseColor.BLACK
        
        val mainTable = PdfPTable(2)
        mainTable.widthPercentage = 100f
        mainTable.setWidths(floatArrayOf(1.3f, 2.7f))
        
        val leftCell = PdfPCell()
        leftCell.backgroundColor = maroon
        leftCell.border = Rectangle.NO_BORDER
        leftCell.setPadding(12f)
        
        val dimW = FontFactory.getFont(FontFactory.HELVETICA, 8f, BaseColor(210, 180, 180))
        val bfw = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, W)
        
        leftCell.addElement(Paragraph("Name :", dimW))
        val displayName = invoiceBuyerName(voucher, customerLedger)
        leftCell.addElement(Paragraph(displayName, bfw))
        val grp = customerLedger?.group?.trim()?.lowercase() ?: ""
        val isFinanced = grp == "finance companies" || grp.startsWith("finance comp")
        customerLedger?.gstin?.takeIf { it.isNotBlank() }?.let {
            leftCell.addElement(Paragraph("GSTIN : $it", dimW))
        }
        if (isFinanced && customerLedger != null) {
            leftCell.addElement(Paragraph("Financed by - ${customerLedger.name}", bfw))
        }
        leftCell.addElement(Chunk.NEWLINE)
        
        leftCell.addElement(Paragraph("ID :", dimW))
        leftCell.addElement(Paragraph("INV_${voucher.voucherNumber}", bfw))
        leftCell.addElement(Chunk.NEWLINE)
        
        leftCell.addElement(Paragraph("Date :", dimW))
        leftCell.addElement(Paragraph(df.format(voucher.date), bfw))
        leftCell.addElement(Chunk.NEWLINE)
        
        leftCell.addElement(Paragraph("Due Date :", dimW))
        leftCell.addElement(Paragraph(df.format(voucher.date), bfw))
        leftCell.addElement(Chunk.NEWLINE)
        
        leftCell.addElement(Paragraph("\n\n\n\n\n\n\n\n", bfw))
        
        val sigImg = getSafeScaledImage(company?.signatureUri, 100f, 40f)
        if (sigImg != null) {
            sigImg.alignment = Element.ALIGN_CENTER
            leftCell.addElement(sigImg)
        }
        leftCell.addElement(Paragraph("_________________", bfw))
        leftCell.addElement(Paragraph("Authorized Signature", dimW))
        leftCell.addElement(Chunk.NEWLINE)
        
        val stampFile = company?.let { generateStampBitmap(context, it) }
        val stampImg = getSafeScaledImage(stampFile?.absolutePath, 100f, 45f)
        if (stampImg != null) {
            stampImg.alignment = Element.ALIGN_CENTER
            leftCell.addElement(stampImg)
        }
        leftCell.addElement(Paragraph("_________________", bfw))
        leftCell.addElement(Paragraph("Official Stamp", dimW))
        mainTable.addCell(leftCell)
        
        val rightCell = PdfPCell()
        rightCell.backgroundColor = offWhite
        rightCell.border = Rectangle.NO_BORDER
        rightCell.setPadding(12f)
        
        val logoHeaderT = PdfPTable(2)
        logoHeaderT.widthPercentage = 100f
        logoHeaderT.setWidths(floatArrayOf(1.5f, 1f))
        
        val logoC = PdfPCell()
        logoC.border = Rectangle.NO_BORDER
        logoC.backgroundColor = offWhite
        val companyLogo = getSafeScaledImage(company?.logoUri, 60f, 60f)
        if (companyLogo != null) { logoC.addElement(companyLogo) }
        logoC.addElement(Paragraph(company?.name ?: "Your Company", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, maroon)))
        if (!company?.address.isNullOrBlank()) logoC.addElement(Paragraph(company!!.address, FontFactory.getFont(FontFactory.HELVETICA, 8f, B)))
        if (!company?.email.isNullOrBlank()) logoC.addElement(Paragraph(company!!.email, FontFactory.getFont(FontFactory.HELVETICA, 8f, B)))
        if (!company?.phoneNumber.isNullOrBlank()) logoC.addElement(Paragraph("Ph: ${company!!.phoneNumber}", FontFactory.getFont(FontFactory.HELVETICA, 8f, B)))
        if (!company?.gstin.isNullOrBlank()) {
            logoC.addElement(Paragraph("GSTIN: ${company.gstin}", FontFactory.getFont(FontFactory.HELVETICA, 8f, B)))
        }
        logoHeaderT.addCell(logoC)
        
        val invTitleC = PdfPCell(Phrase("INVOICE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 28f, maroon)))
        invTitleC.border = Rectangle.NO_BORDER
        invTitleC.backgroundColor = offWhite
        invTitleC.horizontalAlignment = Element.ALIGN_RIGHT
        invTitleC.verticalAlignment = Element.ALIGN_TOP
        logoHeaderT.addCell(invTitleC)
        
        rightCell.addElement(logoHeaderT)
        rightCell.addElement(Chunk.NEWLINE)
        
        val tbl = PdfPTable(5)
        tbl.widthPercentage = 100f
        tbl.setWidths(floatArrayOf(2.1f, 0.8f, 1f, 0.8f, 1.2f))

        for (h in listOf("Items", "HSN", "Price", "Qty", "Total")) {
            val c = PdfPCell(Phrase(h, bfw))
            c.backgroundColor = orange
            c.border = Rectangle.NO_BORDER
            c.setPadding(6f)
            c.horizontalAlignment = if (h == "Items") Element.ALIGN_LEFT else Element.ALIGN_CENTER
            tbl.addCell(c)
        }
        
        var subTotal = 0.0
        inventoryEntries.forEachIndexed { idx, entry ->
            val stockItem = stockItems.find { it.id == entry.stockItemId }
            val amount = entry.taxableValue()
            subTotal += amount
            
            val itemCell = PdfPCell(Phrase(stockItem?.name ?: "N/A", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, W)))
            itemCell.backgroundColor = maroon
            itemCell.border = Rectangle.NO_BORDER
            itemCell.setPadding(6f)
            tbl.addCell(itemCell)
            
            val nf = FontFactory.getFont(FontFactory.HELVETICA, 9f, B)
            fun addRightC(text: String) {
                val c = PdfPCell(Phrase(text, nf))
                c.backgroundColor = offWhite
                c.border = Rectangle.BOTTOM
                c.borderColor = BaseColor.LIGHT_GRAY
                c.setPadding(6f)
                c.horizontalAlignment = Element.ALIGN_CENTER
                tbl.addCell(c)
            }
            
            addRightC(entry.hsnCode ?: "-")
            addRightC("${company?.currencySymbol ?: "₹"}%.2f".format(entry.taxableRate()))
            addRightC("%.0f".format(entry.quantity))
            addRightC("${company?.currencySymbol ?: "₹"}%.2f".format(amount))
        }
        rightCell.addElement(tbl)
        rightCell.addElement(Chunk.NEWLINE)
        
        val countryName = DashboardPrefs.getTaxCountry(context)
        val isIndia = countryName.equals("India", true)
        val taxLabel = TaxConfigManager.getConfigForCountry(countryName).taxName
        val igstLabel = if (isIndia) "IGST :" else "$taxLabel :"

        var totalCgst = 0.0
        var totalSgst = 0.0
        var totalIgst = 0.0
        var taxRate = 0.0
        
        inventoryEntries.forEach { entry ->
            if (taxRate == 0.0 && entry.taxAmount > 0.0) taxRate = entry.gstRate
            val (cg, sg, ig) = GstComplianceRules.taxSplit(entry)
            totalCgst += cg
            totalSgst += sg
            totalIgst += ig
        }
        val total = subTotal + totalIgst + totalCgst + totalSgst
        
        val stTable = PdfPTable(2)
        stTable.widthPercentage = 70f
        stTable.horizontalAlignment = Element.ALIGN_RIGHT
        stTable.setWidths(floatArrayOf(1.5f, 1f))
        
        fun addSt(label: String, valStr: String, bg: BaseColor) {
            val lf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, W)
            val c1 = PdfPCell(Phrase(label, lf))
            c1.backgroundColor = bg
            c1.border = Rectangle.NO_BORDER
            c1.setPadding(6f)
            stTable.addCell(c1)
            
            val c2 = PdfPCell(Phrase(valStr, lf))
            c2.backgroundColor = bg
            c2.border = Rectangle.NO_BORDER
            c2.setPadding(6f)
            c2.horizontalAlignment = Element.ALIGN_RIGHT
            stTable.addCell(c2)
        }
        
        addSt("Subtotal :", "${company?.currencySymbol ?: "₹"}%.2f".format(subTotal), orange)
        if (totalIgst > 0.0) {
            addSt("${if (isIndia) "IGST" else taxLabel} (${fmtRate(taxRate)}%)", "${company?.currencySymbol ?: "₹"}%.2f".format(totalIgst), orange)
        } else {
            if (totalCgst > 0.0) {
                addSt("CGST (${fmtRate(taxRate / 2)}%)", "${company?.currencySymbol ?: "₹"}%.2f".format(totalCgst), orange)
            }
            if (totalSgst > 0.0) {
                addSt("SGST (${fmtRate(taxRate / 2)}%)", "${company?.currencySymbol ?: "₹"}%.2f".format(totalSgst), orange)
            }
        }
        addSt("Grand Total :", "${company?.currencySymbol ?: "₹"}%.2f".format(total), maroon)
        
        rightCell.addElement(stTable)
        rightCell.addElement(Paragraph(InvoiceTemplatesExtra.amountInWords(total), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, maroon)))
        rightCell.addElement(Chunk.NEWLINE)
        
        // Add financed/HPA information if available
        val financedInfo = invoiceFinancedBy(customerLedger)
        if (!financedInfo.isNullOrBlank()) {
            val financeTable = PdfPTable(1)
            financeTable.widthPercentage = 100f
            
            val financeCell = PdfPCell()
            financeCell.border = Rectangle.NO_BORDER
            financeCell.backgroundColor = orange
            financeCell.setPadding(8f)
            financeCell.horizontalAlignment = Element.ALIGN_CENTER
            
            val financeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, W)
            financeCell.addElement(Phrase("FINANCED BY: $financedInfo", financeFont))
            
            financeTable.addCell(financeCell)
            rightCell.addElement(financeTable)
            rightCell.addElement(Chunk.NEWLINE)
        }
        
        val termsPayT = PdfPTable(2)
        termsPayT.widthPercentage = 100f
        termsPayT.setWidths(floatArrayOf(1.2f, 0.8f))
        
        val termsC = PdfPCell()
        termsC.border = Rectangle.NO_BORDER
        termsC.backgroundColor = offWhite
        termsC.addElement(Paragraph("Terms & Conditions:", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, B)))
        termsC.addElement(Paragraph("1. Goods once sold will not be taken back.\n2. Subject to local jurisdiction.\n3. Interest @ 18% p.a. will be charged for delayed payments.", FontFactory.getFont(FontFactory.HELVETICA, 7f, BaseColor.GRAY)))
        termsPayT.addCell(termsC)
        
        val payC = PdfPCell()
        payC.border = Rectangle.NO_BORDER
        payC.backgroundColor = offWhite
        payC.addElement(Paragraph("Payment Terms:", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, B)))
        payC.addElement(Paragraph("Settle via Bank Transfer / UPI.\nBeneficiary: ${company?.name ?: "N/A"}", FontFactory.getFont(FontFactory.HELVETICA, 7f, BaseColor.GRAY)))
        val upiQr = getUpiQrCodeImage(context, company, voucher, inventoryEntries)
        if (upiQr != null) {
            payC.addElement(Chunk.NEWLINE)
            payC.addElement(upiQr)
        }
        termsPayT.addCell(payC)
        
        rightCell.addElement(termsPayT)
        mainTable.addCell(rightCell)
        document.add(mainTable)
        addInvoiceFooter(document, context, maroon)
    }

    // Template 3 - MODERN BLACK ORANGE
    /**
     * The default invoice. Rewritten after a real bill came out of it reading "Due :", "NO_" and
     * "INVO" with no date anywhere and the company reduced to a single line — the layout printed
     * labels whether or not it had anything to put after them.
     *
     * Rules this version follows: every label is drawn only when there is a value for it, the
     * document always carries a number and a date, and the top of the page is the seller's
     * identity (logo, name, address, GSTIN) rather than decoration.
     */
    private fun drawBlackOrange(
        document: Document, writer: PdfWriter, context: Context,
        voucher: Voucher, customerLedger: Ledger?, inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>, units: List<Unit>, company: Company?,
        billAllocations: List<com.minhas.teller.data.entities.BillAllocation>
    ) {
        val orange = BaseColor(220, 110, 60)
        val charcoal = BaseColor(40, 40, 40)
        val B = BaseColor.BLACK
        val W = BaseColor.WHITE
        val grey = BaseColor(120, 120, 120)
        val ltG = BaseColor(248, 248, 248)
        val midG = BaseColor(225, 225, 225)
        val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val currency = company?.currencySymbol ?: "₹"

        fun font(size: Float, bold: Boolean = false, color: BaseColor = B) =
            FontFactory.getFont(if (bold) FontFactory.HELVETICA_BOLD else FontFactory.HELVETICA, size, color)

        fun line(text: String, size: Float, bold: Boolean = false, color: BaseColor = B, align: Int = Element.ALIGN_LEFT) =
            Paragraph(text, font(size, bold, color)).apply { alignment = align }

        // A voucher with no number still has to be referable, and an invoice with no date is
        // not a document — fall back rather than printing an empty label.
        val invoiceNo = voucher.voucherNumber.trim().ifBlank { "#${voucher.id}" }
        val isTaxInvoice = !company?.gstin.isNullOrBlank()

        // ── Header: who is billing, and what this document is ────────────────
        val topT = PdfPTable(2)
        topT.widthPercentage = 100f
        topT.setWidths(floatArrayOf(1.45f, 1f))

        val leftC = PdfPCell()
        leftC.border = Rectangle.NO_BORDER
        leftC.setPadding(0f)
        getSafeScaledImage(company?.logoUri, 110f, 55f)?.let {
            it.alignment = Element.ALIGN_LEFT
            leftC.addElement(it)
            leftC.addElement(Paragraph(" ", font(4f)))
        }
        leftC.addElement(line(company?.name ?: "Your Company", 17f, bold = true))
        listOfNotNull(
            company?.address?.takeIf { it.isNotBlank() },
            listOfNotNull(
                company?.state?.takeIf { it.isNotBlank() },
                company?.pincode?.takeIf { it.isNotBlank() }
            ).joinToString(" - ").takeIf { it.isNotBlank() },
            company?.phoneNumber?.takeIf { it.isNotBlank() }?.let { "Phone  $it" },
            company?.email?.takeIf { it.isNotBlank() },
            company?.gstin?.takeIf { it.isNotBlank() }?.let { "GSTIN  $it" }
        ).forEach { leftC.addElement(line(it, 9f, color = grey)) }
        topT.addCell(leftC)

        val rightC = PdfPCell()
        rightC.border = Rectangle.NO_BORDER
        rightC.setPadding(0f)

        val titleT = PdfPTable(1)
        titleT.widthPercentage = 100f
        val titleCell = PdfPCell(Phrase(if (isTaxInvoice) "TAX INVOICE" else "INVOICE", font(26f, bold = true, color = W)))
        titleCell.backgroundColor = charcoal
        titleCell.border = Rectangle.NO_BORDER
        titleCell.setPadding(14f)
        titleCell.horizontalAlignment = Element.ALIGN_CENTER
        titleT.addCell(titleCell)
        val accent = PdfPCell()
        accent.backgroundColor = orange
        accent.border = Rectangle.NO_BORDER
        accent.fixedHeight = 5f
        titleT.addCell(accent)
        rightC.addElement(titleT)

        val metaT = PdfPTable(2)
        metaT.widthPercentage = 100f
        metaT.setWidths(floatArrayOf(1f, 1.3f))
        metaT.setSpacingBefore(8f)
        fun meta(label: String, value: String) {
            val l = PdfPCell(Phrase(label, font(9f, color = grey)))
            l.border = Rectangle.NO_BORDER
            l.setPadding(2f)
            metaT.addCell(l)
            val v = PdfPCell(Phrase(value, font(9f, bold = true)))
            v.border = Rectangle.NO_BORDER
            v.setPadding(2f)
            v.horizontalAlignment = Element.ALIGN_RIGHT
            metaT.addCell(v)
        }
        meta("Invoice No.", invoiceNo)
        meta("Date", df.format(voucher.date))
        company?.state?.takeIf { it.isNotBlank() }?.let { meta("Place of Supply", it) }
        rightC.addElement(metaT)
        topT.addCell(rightC)
        document.add(topT)

        // ── Bill to ──────────────────────────────────────────────────────────
        val billT = PdfPTable(2)
        billT.widthPercentage = 100f
        billT.setWidths(floatArrayOf(1.45f, 1f))
        billT.setSpacingBefore(18f)

        val buyerC = PdfPCell()
        buyerC.border = Rectangle.NO_BORDER
        buyerC.backgroundColor = ltG
        buyerC.setPadding(10f)
        buyerC.addElement(line("BILL TO", 8f, bold = true, color = orange))
        buyerC.addElement(line(invoiceBuyerName(voucher, customerLedger), 13f, bold = true))
        listOfNotNull(
            customerLedger?.alias?.takeIf { it.isNotBlank() },
            customerLedger?.phoneNumber?.takeIf { it.isNotBlank() }?.let { "Phone  $it" },
            customerLedger?.gstin?.takeIf { it.isNotBlank() }?.let { "GSTIN  $it" }
        ).forEach { buyerC.addElement(line(it, 9f, color = grey)) }
        invoiceFinancedBy(customerLedger)?.takeIf { it.isNotBlank() }?.let {
            buyerC.addElement(line("Financed by  $it", 9f, bold = true, color = orange))
        }
        billT.addCell(buyerC)

        // Amount due sits next to the buyer: the number they care about, before the detail.
        val dueC = PdfPCell()
        dueC.border = Rectangle.NO_BORDER
        dueC.backgroundColor = charcoal
        dueC.setPadding(10f)
        dueC.horizontalAlignment = Element.ALIGN_RIGHT
        dueC.addElement(line("AMOUNT DUE", 8f, bold = true, color = orange, align = Element.ALIGN_RIGHT))
        val payable = inventoryEntries.payableTotal()
        dueC.addElement(line("$currency${"%,.2f".format(payable)}", 20f, bold = true, color = W, align = Element.ALIGN_RIGHT))
        dueC.addElement(line(df.format(voucher.date), 8f, color = BaseColor(180, 180, 180), align = Element.ALIGN_RIGHT))
        billT.addCell(dueC)
        document.add(billT)

        // ── Items ────────────────────────────────────────────────────────────
        val tbl = PdfPTable(6)
        tbl.widthPercentage = 100f
        tbl.setWidths(floatArrayOf(0.5f, 3.2f, 1f, 0.8f, 1.4f, 1.6f))
        tbl.setSpacingBefore(16f)
        tbl.setSpacingAfter(10f)
        tbl.headerRows = 1

        listOf(
            "#" to Element.ALIGN_CENTER,
            "Item Description" to Element.ALIGN_LEFT,
            "HSN" to Element.ALIGN_CENTER,
            "Qty" to Element.ALIGN_RIGHT,
            "Rate" to Element.ALIGN_RIGHT,
            "Amount" to Element.ALIGN_RIGHT
        ).forEach { (h, align) ->
            val c = PdfPCell(Phrase(h, font(9.5f, bold = true, color = W)))
            c.backgroundColor = charcoal
            c.border = Rectangle.NO_BORDER
            c.setPadding(7f)
            c.horizontalAlignment = align
            tbl.addCell(c)
        }

        var subTotal = 0.0
        inventoryEntries.forEachIndexed { idx, entry ->
            val stockItem = stockItems.find { it.id == entry.stockItemId }
            val amount = entry.taxableValue()
            subTotal += amount
            val bg = if (idx % 2 == 0) W else ltG
            val unitName = units.find { it.id == stockItem?.unitId }?.name

            fun cellOf(text: String, align: Int = Element.ALIGN_RIGHT) {
                val c = PdfPCell(Phrase(text, font(9f)))
                c.backgroundColor = bg
                c.border = Rectangle.BOTTOM
                c.borderColor = midG
                c.setPadding(7f)
                c.horizontalAlignment = align
                tbl.addCell(c)
            }
            cellOf("${idx + 1}", Element.ALIGN_CENTER)
            cellOf(stockItem?.name ?: "Goods", Element.ALIGN_LEFT)
            cellOf(entry.hsnCode?.takeIf { it.isNotBlank() } ?: "-", Element.ALIGN_CENTER)
            cellOf("%,.2f".format(entry.quantity).removeSuffix(".00") + (unitName?.let { " $it" } ?: ""))
            cellOf("%,.2f".format(entry.taxableRate()))
            cellOf("$currency${"%,.2f".format(amount)}")
        }
        document.add(tbl)

        val countryName = DashboardPrefs.getTaxCountry(context)
        val isIndia = countryName.equals("India", true)
        val taxLabel = TaxConfigManager.getConfigForCountry(countryName).taxName

        var totalCgst = 0.0
        var totalSgst = 0.0
        var totalIgst = 0.0
        var taxRate = 0.0
        inventoryEntries.forEach { entry ->
            if (taxRate == 0.0 && entry.taxAmount > 0L) taxRate = entry.gstRate
            val (cg, sg, ig) = GstComplianceRules.taxSplit(entry)
            totalCgst += cg
            totalSgst += sg
            totalIgst += ig
        }
        val total = subTotal + totalIgst + totalCgst + totalSgst

        // ── Words on the left, figures on the right ──────────────────────────
        val sumRow = PdfPTable(2)
        sumRow.widthPercentage = 100f
        sumRow.setWidths(floatArrayOf(1.2f, 1f))

        val wordsC = PdfPCell()
        wordsC.border = Rectangle.NO_BORDER
        wordsC.setPadding(0f)
        wordsC.addElement(line("AMOUNT IN WORDS", 8f, bold = true, color = grey))
        wordsC.addElement(line(InvoiceTemplatesExtra.amountInWords(total), 9.5f, bold = true))
        sumRow.addCell(wordsC)

        val stTable = PdfPTable(2)
        stTable.widthPercentage = 100f
        stTable.setWidths(floatArrayOf(1.5f, 1.2f))
        fun addSt(label: String, valStr: String, dark: Boolean) {
            val bg = if (dark) charcoal else midG
            val tc = if (dark) W else B
            val f = font(if (dark) 11f else 9.5f, bold = true, color = tc)
            val c1 = PdfPCell(Phrase(label, f))
            c1.backgroundColor = bg
            c1.border = Rectangle.NO_BORDER
            c1.setPadding(6f)
            stTable.addCell(c1)
            val c2 = PdfPCell(Phrase(valStr, f))
            c2.backgroundColor = bg
            c2.border = Rectangle.NO_BORDER
            c2.setPadding(6f)
            c2.horizontalAlignment = Element.ALIGN_RIGHT
            stTable.addCell(c2)
        }
        addSt("Sub Total", "$currency${"%,.2f".format(subTotal)}", false)
        if (totalIgst > 0.0) {
            addSt("${if (isIndia) "IGST" else taxLabel} (${fmtRate(taxRate)}%)", "$currency${"%,.2f".format(totalIgst)}", false)
        } else {
            if (totalCgst > 0.0) addSt("CGST (${fmtRate(taxRate / 2)}%)", "$currency${"%,.2f".format(totalCgst)}", false)
            if (totalSgst > 0.0) addSt("SGST (${fmtRate(taxRate / 2)}%)", "$currency${"%,.2f".format(totalSgst)}", false)
        }
        addSt("Total Amount", "$currency${"%,.2f".format(total)}", true)
        val stCell = PdfPCell(stTable)
        stCell.border = Rectangle.NO_BORDER
        stCell.setPadding(0f)
        sumRow.addCell(stCell)
        document.add(sumRow)

        val financedInfo = invoiceFinancedBy(customerLedger)
        if (!financedInfo.isNullOrBlank()) {
            val financeTable = PdfPTable(1)
            financeTable.widthPercentage = 100f
            financeTable.setSpacingBefore(10f)
            val financeCell = PdfPCell(Phrase("FINANCED BY: $financedInfo", font(11f, bold = true, color = W)))
            financeCell.border = Rectangle.NO_BORDER
            financeCell.backgroundColor = orange
            financeCell.setPadding(8f)
            financeCell.horizontalAlignment = Element.ALIGN_CENTER
            financeTable.addCell(financeCell)
            document.add(financeTable)
        }

        // ── Pay / sign ───────────────────────────────────────────────────────
        val botT = PdfPTable(3)
        botT.widthPercentage = 100f
        botT.setWidths(floatArrayOf(1.5f, 0.9f, 1f))
        botT.setSpacingBefore(22f)

        val termsC = PdfPCell()
        termsC.border = Rectangle.NO_BORDER
        termsC.setPadding(0f)
        termsC.addElement(line("PAYMENT & TERMS", 9f, bold = true))
        termsC.addElement(line("Beneficiary: ${company?.name ?: "-"}", 8.5f, color = grey))
        if (!company?.upiId.isNullOrBlank()) termsC.addElement(line("UPI: ${company?.upiId}", 8.5f, color = grey))
        termsC.addElement(
            line(
                "Please settle via Bank Transfer or UPI. Subject to local jurisdiction; " +
                    "interest @ 18% p.a. on delayed payments.",
                8f, color = grey
            )
        )
        botT.addCell(termsC)

        // The QR is only worth the space when it actually pays someone.
        val qrC = PdfPCell()
        qrC.border = Rectangle.NO_BORDER
        qrC.setPadding(0f)
        qrC.horizontalAlignment = Element.ALIGN_CENTER
        val upiQr = getUpiQrCodeImage(context, company, voucher, inventoryEntries)
        if (upiQr != null) {
            upiQr.alignment = Element.ALIGN_CENTER
            qrC.addElement(upiQr)
            qrC.addElement(line("Scan & Pay", 8f, bold = true, color = orange, align = Element.ALIGN_CENTER))
        }
        botT.addCell(qrC)

        val signC = PdfPCell()
        signC.border = Rectangle.NO_BORDER
        signC.setPadding(0f)
        signC.horizontalAlignment = Element.ALIGN_RIGHT
        signC.addElement(line("For ${company?.name ?: "Your Company"}", 9f, bold = true, align = Element.ALIGN_RIGHT))
        getSafeScaledImage(company?.signatureUri, 90f, 34f)?.let {
            it.alignment = Element.ALIGN_RIGHT
            signC.addElement(it)
        }
        val stampFile = company?.let { generateStampBitmap(context, it) }
        getSafeScaledImage(stampFile?.absolutePath, 80f, 34f)?.let {
            it.alignment = Element.ALIGN_RIGHT
            signC.addElement(it)
        }
        signC.addElement(line("Authorised Signatory", 8.5f, color = grey, align = Element.ALIGN_RIGHT))
        botT.addCell(signC)
        document.add(botT)

        val thanks = PdfPTable(1)
        thanks.widthPercentage = 100f
        thanks.setSpacingBefore(16f)
        val thanksCell = PdfPCell(Phrase("THANK YOU FOR YOUR BUSINESS", font(10f, bold = true, color = W)))
        thanksCell.backgroundColor = orange
        thanksCell.border = Rectangle.NO_BORDER
        thanksCell.setPadding(7f)
        thanksCell.horizontalAlignment = Element.ALIGN_CENTER
        thanks.addCell(thanksCell)
        document.add(thanks)

        addInvoiceFooter(document, context, charcoal)
    }

    // Template 4 - TEAL CLEAN CORPORATE
    private fun drawTealClean(
        document: Document, writer: PdfWriter, context: Context,
        voucher: Voucher, customerLedger: Ledger?, inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>, units: List<Unit>, company: Company?,
        billAllocations: List<com.minhas.teller.data.entities.BillAllocation>
    ) {
        val teal = BaseColor(40, 75, 85)
        val W = BaseColor.WHITE
        val B = BaseColor.BLACK
        val df = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        
        val banner = PdfPTable(2)
        banner.widthPercentage = 100f
        banner.setWidths(floatArrayOf(1f, 1f))
        
        val bLogo = PdfPCell()
        bLogo.backgroundColor = teal
        bLogo.border = Rectangle.NO_BORDER
        bLogo.setPadding(10f)
        val logoImg = getSafeScaledImage(company?.logoUri, 80f, 40f)
        if (logoImg != null) { bLogo.addElement(logoImg) }
        bLogo.addElement(Paragraph(company?.name ?: "Your Company", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, W)))
        if (!company?.address.isNullOrBlank()) bLogo.addElement(Paragraph(company!!.address, FontFactory.getFont(FontFactory.HELVETICA, 8f, W)))
        if (!company?.phoneNumber.isNullOrBlank()) bLogo.addElement(Paragraph("Ph: ${company!!.phoneNumber}", FontFactory.getFont(FontFactory.HELVETICA, 8f, W)))
        if (!company?.email.isNullOrBlank()) bLogo.addElement(Paragraph(company!!.email, FontFactory.getFont(FontFactory.HELVETICA, 8f, W)))
        banner.addCell(bLogo)
        
        val bTitle = PdfPCell(Phrase("INVOICE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24f, W)))
        bTitle.backgroundColor = teal
        bTitle.border = Rectangle.NO_BORDER
        bTitle.horizontalAlignment = Element.ALIGN_RIGHT
        bTitle.verticalAlignment = Element.ALIGN_MIDDLE
        bTitle.setPaddingRight(10f)
        banner.addCell(bTitle)
        
        document.add(banner)
        document.add(Chunk.NEWLINE)
        
        val infoT = PdfPTable(2)
        infoT.widthPercentage = 100f
        infoT.setWidths(floatArrayOf(1.2f, 1f))
        
        val clientC = PdfPCell()
        clientC.border = Rectangle.NO_BORDER
        clientC.addElement(Paragraph("Invoice To,", FontFactory.getFont(FontFactory.HELVETICA, 9f, teal)))
        clientC.addElement(Paragraph(invoiceBuyerName(voucher, customerLedger), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f, B)))
        customerLedger?.alias?.takeIf { it.isNotBlank() }?.let {
            clientC.addElement(Paragraph("Address: $it", FontFactory.getFont(FontFactory.HELVETICA, 9f, BaseColor.GRAY)))
        }
        customerLedger?.gstin?.takeIf { it.isNotBlank() }?.let {
            clientC.addElement(Paragraph("GSTIN: $it", FontFactory.getFont(FontFactory.HELVETICA, 9f, BaseColor.GRAY)))
        }
        val grp = customerLedger?.group?.trim()?.lowercase() ?: ""
        val isFinanced = grp == "finance companies" || grp.startsWith("finance comp")
        if (isFinanced && customerLedger != null) {
            clientC.addElement(Paragraph("Financed by - ${customerLedger.name}", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, teal)))
        }
        infoT.addCell(clientC)
        
        val vchC = PdfPCell()
        vchC.border = Rectangle.NO_BORDER
        vchC.addElement(Paragraph("Invoice #: ${voucher.voucherNumber}", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, B)))
        vchC.addElement(Paragraph("Date: ${df.format(voucher.date)}", FontFactory.getFont(FontFactory.HELVETICA, 10f, B)))
        if (!company?.gstin.isNullOrBlank()) vchC.addElement(Paragraph("GSTIN: ${company?.gstin}", FontFactory.getFont(FontFactory.HELVETICA, 10f, B)))
        infoT.addCell(vchC)
        
        document.add(infoT)
        document.add(Chunk.NEWLINE)
        
        val tbl = PdfPTable(5)
        tbl.widthPercentage = 100f
        tbl.setWidths(floatArrayOf(3f, 0.9f, 0.8f, 1.2f, 1.5f))
        tbl.setSpacingAfter(15f)

        for (h in listOf("ITEM DESCRIPTIONS", "HSN", "QTY", "PRICE", "TOTAL")) {
            val c = PdfPCell(Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, W)))
            c.backgroundColor = teal
            c.border = Rectangle.NO_BORDER
            c.setPadding(6f)
            c.horizontalAlignment = if (h == "ITEM DESCRIPTIONS") Element.ALIGN_LEFT else Element.ALIGN_CENTER
            tbl.addCell(c)
        }
        
        var subTotal = 0.0
        inventoryEntries.forEachIndexed { index, entry ->
            val stockItem = stockItems.find { it.id == entry.stockItemId }
            val unit = units.find { it.id == stockItem?.unitId }?.name ?: ""
            val amount = entry.taxableValue()
            subTotal += amount
            
            val bg = if (index % 2 == 0) W else BaseColor(245, 245, 245)
            
            val dataFont = FontFactory.getFont(FontFactory.HELVETICA, 9f)
            // 4 cells to match the 4-column table (ITEM DESCRIPTIONS, QTY, PRICE, TOTAL)
            addInvoiceItemCell(tbl, "${stockItem?.name ?: "N/A"} (${unit})", dataFont, bg, Element.ALIGN_LEFT)
            addInvoiceItemCell(tbl, entry.hsnCode ?: "-", dataFont, bg, Element.ALIGN_CENTER)
            addInvoiceItemCell(tbl, "%.2f".format(entry.quantity), dataFont, bg, Element.ALIGN_RIGHT)
            addInvoiceItemCell(tbl, "%.2f".format(entry.taxableRate()), dataFont, bg, Element.ALIGN_RIGHT)
            addInvoiceItemCell(tbl, "%.2f".format(amount), dataFont, bg, Element.ALIGN_RIGHT)
        }
        document.add(tbl)
        
        val countryName = DashboardPrefs.getTaxCountry(context)
        val isIndia = countryName.equals("India", true)
        val taxLabel = TaxConfigManager.getConfigForCountry(countryName).taxName
        val igstLabel = if (isIndia) "IGST" else taxLabel

        var totalCgst = 0.0
        var totalSgst = 0.0
        var totalIgst = 0.0
        var taxRate = 0.0
        
        inventoryEntries.forEach { entry ->
            if (taxRate == 0.0 && entry.taxAmount > 0.0) taxRate = entry.gstRate
            val (cg, sg, ig) = GstComplianceRules.taxSplit(entry)
            totalCgst += cg
            totalSgst += sg
            totalIgst += ig
        }
        val total = subTotal + totalIgst + totalCgst + totalSgst
        
        val summaryPayT = PdfPTable(2)
        summaryPayT.widthPercentage = 100f
        summaryPayT.setWidths(floatArrayOf(1.2f, 1f))
        
        val payC = PdfPCell()
        payC.border = Rectangle.NO_BORDER
        payC.addElement(Paragraph("PAYMENT & TERMS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, teal)))
        payC.addElement(Paragraph("Beneficiary: ${company?.name ?: "N/A"}", FontFactory.getFont(FontFactory.HELVETICA, 9f, B)))
        if (!company?.gstin.isNullOrBlank()) payC.addElement(Paragraph("GSTIN: ${company?.gstin}", FontFactory.getFont(FontFactory.HELVETICA, 9f, B)))
        payC.addElement(Paragraph("Terms: Goods once sold will not be taken back. Interest @ 18% p.a. charged on delayed payments.", FontFactory.getFont(FontFactory.HELVETICA, 7f, BaseColor.GRAY)))
        val upiQr = getUpiQrCodeImage(context, company, voucher, inventoryEntries)
        if (upiQr != null) {
            payC.addElement(Chunk.NEWLINE)
            payC.addElement(upiQr)
        }
        
        val stFile = company?.let { generateStampBitmap(context, it) }
        val stImg = getSafeScaledImage(stFile?.absolutePath, 100f, 50f)
        if (stImg != null) {
            payC.addElement(Chunk.NEWLINE)
            payC.addElement(stImg)
        }
        summaryPayT.addCell(payC)
        
        val sumC = PdfPCell()
        sumC.border = Rectangle.NO_BORDER
        
        val st = PdfPTable(2)
        st.widthPercentage = 100f
        st.setWidths(floatArrayOf(1.5f, 1f))
        
        fun addRow(label: String, valStr: String, hi: Boolean = false) {
            val bg = if (hi) teal else BaseColor(240, 240, 240)
            val tc = if (hi) W else B
            val font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, tc)
            
            val c1 = PdfPCell(Phrase(label, font))
            c1.backgroundColor = bg
            c1.border = Rectangle.NO_BORDER
            c1.setPadding(6f)
            st.addCell(c1)
            
            val c2 = PdfPCell(Phrase(valStr, font))
            c2.backgroundColor = bg
            c2.border = Rectangle.NO_BORDER
            c2.setPadding(6f)
            c2.horizontalAlignment = Element.ALIGN_RIGHT
            st.addCell(c2)
        }
        
        addRow("SUB TOTAL", "${company?.currencySymbol ?: "₹"}%.2f".format(subTotal))
        if (totalIgst > 0.0) {
            addRow("${if (isIndia) "IGST" else taxLabel} (${fmtRate(taxRate)}%)", "${company?.currencySymbol ?: "₹"}%.2f".format(totalIgst))
        } else {
            if (totalCgst > 0.0) {
                addRow("CGST (${fmtRate(taxRate / 2)}%)", "${company?.currencySymbol ?: "₹"}%.2f".format(totalCgst))
            }
            if (totalSgst > 0.0) {
                addRow("SGST (${fmtRate(taxRate / 2)}%)", "${company?.currencySymbol ?: "₹"}%.2f".format(totalSgst))
            }
        }
        addRow("GRAND TOTAL", "${company?.currencySymbol ?: "₹"}%.2f".format(total), true)
        sumC.addElement(st)
        sumC.addElement(Paragraph(InvoiceTemplatesExtra.amountInWords(total), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, teal)))
        
        // Add financed/HPA information if available
        val financedInfo = invoiceFinancedBy(customerLedger)
        if (!financedInfo.isNullOrBlank()) {
            sumC.addElement(Chunk.NEWLINE)
            val financeTable = PdfPTable(1)
            financeTable.widthPercentage = 100f
            
            val financeCell = PdfPCell()
            financeCell.border = Rectangle.NO_BORDER
            financeCell.backgroundColor = teal
            financeCell.setPadding(6f)
            financeCell.horizontalAlignment = Element.ALIGN_CENTER
            
            val financeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, W)
            financeCell.addElement(Phrase("FINANCED BY: $financedInfo", financeFont))
            
            financeTable.addCell(financeCell)
            sumC.addElement(financeTable)
        }
        
        sumC.addElement(Chunk.NEWLINE)
        val sImg = getSafeScaledImage(company?.signatureUri, 100f, 40f)
        if (sImg != null) {
            sImg.alignment = Element.ALIGN_RIGHT
            sumC.addElement(sImg)
        }
        val sigLine = Paragraph("___________________________", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, B))
        sigLine.alignment = Element.ALIGN_RIGHT
        sumC.addElement(sigLine)
        
        val sigTitle = Paragraph("Authorized Signatory", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, teal))
        sigTitle.alignment = Element.ALIGN_RIGHT
        sumC.addElement(sigTitle)
        summaryPayT.addCell(sumC)
        document.add(summaryPayT)
        
        document.add(Chunk.NEWLINE)
        val ft = PdfPTable(3)
        ft.widthPercentage = 100f
        ft.setWidths(floatArrayOf(1f, 1f, 1f))
        
        fun addFootC(text: String) {
            val c = PdfPCell(Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, W)))
            c.backgroundColor = teal
            c.border = Rectangle.NO_BORDER
            c.setPadding(8f)
            c.horizontalAlignment = Element.ALIGN_CENTER
            ft.addCell(c)
        }
        if (!company?.phoneNumber.isNullOrBlank()) addFootC(company!!.phoneNumber)
        if (!company?.email.isNullOrBlank()) addFootC(company!!.email)
        if (!company?.address.isNullOrBlank()) addFootC(company!!.address)
        document.add(ft)
        addInvoiceFooter(document, context, teal)
    }

    // Helper: Invoice Details
    private fun addInvoiceDetails(
        document: Document, voucher: Voucher, customerLedger: Ledger?,
        dateFormatter: SimpleDateFormat, accentColor: BaseColor
    ) {
        val detailsTable = PdfPTable(2)
        detailsTable.widthPercentage = 100f
        detailsTable.setWidths(floatArrayOf(1f, 1f))
        
        val billToCell = PdfPCell()
        billToCell.border = Rectangle.NO_BORDER
        billToCell.addElement(Paragraph("BILL TO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, accentColor)))
        billToCell.addElement(Paragraph(invoiceBuyerName(voucher, customerLedger), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f)))
        detailsTable.addCell(billToCell)
        
        val infoCell = PdfPCell()
        infoCell.border = Rectangle.NO_BORDER
        infoCell.addElement(Paragraph("Invoice Date: ${dateFormatter.format(voucher.date)}", FontFactory.getFont(FontFactory.HELVETICA, 10f)))
        infoCell.addElement(Paragraph("Invoice #: ${voucher.voucherNumber}", FontFactory.getFont(FontFactory.HELVETICA, 10f)))
        detailsTable.addCell(infoCell)
        
        document.add(detailsTable)
        document.add(Chunk.NEWLINE)
    }

    // Helper: Invoice Items
    private fun addInvoiceItems(
        context: Context,
        document: Document, inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>, units: List<Unit>,
        accentColor: BaseColor, lineColor: BaseColor? = null
    ) {
        val itemsTable = PdfPTable(5)
        itemsTable.widthPercentage = 100f
        itemsTable.setWidths(floatArrayOf(0.5f, 3f, 1f, 1f, 1.5f))
        itemsTable.setSpacingAfter(15f)
        
        val headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, BaseColor.WHITE)
        val headerBg = accentColor
        
        addHeaderCell(itemsTable, "#", headerFont, headerBg)
        addHeaderCell(itemsTable, "Item", headerFont, headerBg)
        addHeaderCell(itemsTable, "Qty", headerFont, headerBg)
        addHeaderCell(itemsTable, "Rate", headerFont, headerBg)
        addHeaderCell(itemsTable, "Amount", headerFont, headerBg)
        
        var subTotal = 0.0
        inventoryEntries.forEachIndexed { index, entry ->
            val stockItem = stockItems.find { it.id == entry.stockItemId }
            val unit = units.find { it.id == stockItem?.unitId }?.name ?: ""
            val itemDescription = "${stockItem?.name ?: "N/A"} (${unit})"
            val itemAmount = entry.taxableValue()

            // add to PDF table
            addInvoiceItemCell(itemsTable, (index + 1).toString(), FontFactory.getFont(FontFactory.HELVETICA, 9f), if (index % 2 == 0) BaseColor.WHITE else BaseColor(245,245,245))
            addInvoiceItemCell(itemsTable, itemDescription, FontFactory.getFont(FontFactory.HELVETICA, 9f), if (index % 2 == 0) BaseColor.WHITE else BaseColor(245,245,245))
            addInvoiceItemCell(itemsTable, "%.2f".format(entry.quantity), FontFactory.getFont(FontFactory.HELVETICA, 9f), if (index % 2 == 0) BaseColor.WHITE else BaseColor(245,245,245), Element.ALIGN_RIGHT)
            addInvoiceItemCell(itemsTable, "%.2f".format(entry.taxableRate()), FontFactory.getFont(FontFactory.HELVETICA, 9f), if (index % 2 == 0) BaseColor.WHITE else BaseColor(245,245,245), Element.ALIGN_RIGHT)
            addInvoiceItemCell(itemsTable, "%.2f".format(itemAmount), FontFactory.getFont(FontFactory.HELVETICA, 9f), if (index % 2 == 0) BaseColor.WHITE else BaseColor(245,245,245), Element.ALIGN_RIGHT)
            subTotal += itemAmount
        }
        // blank line handled by spacing

        // Summary
        val summaryTable = PdfPTable(2)
        summaryTable.widthPercentage = 40f
        summaryTable.horizontalAlignment = Element.ALIGN_RIGHT
        summaryTable.setWidths(floatArrayOf(1f, 1f))
        
        val countryName = DashboardPrefs.getTaxCountry(context)
        val isIndia = countryName.equals("India", true)
        val taxLabel = TaxConfigManager.getConfigForCountry(countryName).taxName
        val igstLabel = if (isIndia) "IGST:" else "$taxLabel:"

        var totalCgst = 0.0
        var totalSgst = 0.0
        var totalIgst = 0.0
        var taxRate = 0.0
        
        inventoryEntries.forEach { entry ->
            if (taxRate == 0.0 && entry.taxAmount > 0.0) taxRate = entry.gstRate
            val (cg, sg, ig) = GstComplianceRules.taxSplit(entry)
            totalCgst += cg
            totalSgst += sg
            totalIgst += ig
        }
        val total = subTotal + totalIgst + totalCgst + totalSgst
        
        addSummaryRow(summaryTable, "Subtotal:", "%.2f".format(subTotal))
        
        if (totalIgst > 0.0) {
            addSummaryRow(summaryTable, "${if (isIndia) "IGST" else taxLabel} (${fmtRate(taxRate)}%)", "%.2f".format(totalIgst))
        } else {
            if (totalCgst > 0.0) {
                addSummaryRow(summaryTable, "CGST (${fmtRate(taxRate / 2)}%)", "%.2f".format(totalCgst))
            }
            if (totalSgst > 0.0) {
                addSummaryRow(summaryTable, "SGST (${fmtRate(taxRate / 2)}%)", "%.2f".format(totalSgst))
            }
        }
        
        addSummaryRow(summaryTable, "Total:", "%.2f".format(total), true, accentColor)
        
        document.add(summaryTable)
    }

    /**
     * Export a single invoice to Excel (.xlsx)
     */
    fun exportInvoiceToExcel(
        context: Context,
        voucher: Voucher,
        customerLedger: Ledger?,
        inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>,
        units: List<Unit>,
        company: Company?
    ) {
        scope.launch {
        val fileName = "${voucher.type}_${voucher.voucherNumber.replace("/", "_")}_${System.currentTimeMillis()}.xlsx"
        val file = File(context.getExternalFilesDir(null), fileName)
        val workbook = SXSSFWorkbook()
        val sheet = workbook.createSheet("Invoice")

        try {
            // Create styles
            val titleStyle = workbook.createCellStyle().apply {
                val titleFont = workbook.createFont().apply {
                    bold = true
                    fontHeightInPoints = 14
                }
                setFont(titleFont)
            }

            val labelStyle = workbook.createCellStyle().apply {
                val labelFont = workbook.createFont().apply {
                    bold = true
                    fontHeightInPoints = 10
                }
                setFont(labelFont)
            }

            val headerStyle = workbook.createCellStyle().apply {
                val headerFont = workbook.createFont().apply {
                    bold = true
                    fontHeightInPoints = 11
                }
                setFont(headerFont)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
            }

            val dataStyle = workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.LEFT
            }

            val numericStyle = workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.RIGHT
                dataFormat = workbook.createDataFormat().getFormat("0.00")
            }

            val summaryStyle = workbook.createCellStyle().apply {
                val summaryFont = workbook.createFont().apply {
                    bold = true
                    fontHeightInPoints = 10
                }
                setFont(summaryFont)
                alignment = HorizontalAlignment.RIGHT
            }

            val summaryValueStyle = workbook.createCellStyle().apply {
                val summaryFont = workbook.createFont().apply {
                    bold = true
                    fontHeightInPoints = 10
                }
                setFont(summaryFont)
                alignment = HorizontalAlignment.RIGHT
                dataFormat = workbook.createDataFormat().getFormat("0.00")
            }

            var r = 0
            // Title row
            val titleRow = sheet.createRow(r++)
            val titleCell = titleRow.createCell(0)
            titleCell.setCellValue(company?.name ?: "Invoice")
            titleCell.cellStyle = titleStyle

            // Metadata rows
            val metaRow = sheet.createRow(r++)
            val invoiceLabelCell = metaRow.createCell(0)
            invoiceLabelCell.setCellValue("Invoice #: ${voucher.voucherNumber}")
            invoiceLabelCell.cellStyle = dataStyle
            val dateCell = metaRow.createCell(1)
            dateCell.setCellValue("Date: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(voucher.date)}")
            dateCell.cellStyle = dataStyle

            val gstinRow = sheet.createRow(r++)
            gstinRow.createCell(0).apply {
                setCellValue("Supplier GSTIN: ${company?.gstin ?: "N/A"}")
                cellStyle = dataStyle
            }
            gstinRow.createCell(1).apply {
                setCellValue("Place of Supply: ${company?.state ?: "N/A"}")
                cellStyle = dataStyle
            }

            if (customerLedger != null) {
                val cRow = sheet.createRow(r++)
                val billToCell = cRow.createCell(0)
                billToCell.setCellValue("Bill To: " + invoiceBuyerName(voucher, customerLedger))
                billToCell.cellStyle = labelStyle
                
                val grp = customerLedger.group.trim().lowercase()
                val isFinanced = grp == "finance companies" || grp.startsWith("finance comp")
                if (isFinanced) {
                    val finRow = sheet.createRow(r++)
                    val finCell = finRow.createCell(0)
                    finCell.setCellValue("Financed by - " + customerLedger.name)
                    finCell.cellStyle = dataStyle
                }
            }
            r++ // blank line

            // Table header
            val header = sheet.createRow(r++)
            val hc0 = header.createCell(0)
            hc0.setCellValue("No")
            hc0.cellStyle = headerStyle
            val hc1 = header.createCell(1)
            hc1.setCellValue("Item Description")
            hc1.cellStyle = headerStyle
            val hc2 = header.createCell(2)
            hc2.setCellValue("Qty")
            hc2.cellStyle = headerStyle
            val hc3 = header.createCell(3)
            hc3.setCellValue("Rate")
            hc3.cellStyle = headerStyle
            val hc4 = header.createCell(4)
            hc4.setCellValue("Amount")
            hc4.cellStyle = headerStyle

            var rowIndex = r
            var subTotal = 0.0
            inventoryEntries.forEachIndexed { idx, entry ->
                val row = sheet.createRow(rowIndex++)
                val stockItem = stockItems.find { it.id == entry.stockItemId }
                val unit = units.find { it.id == stockItem?.unitId }?.name ?: ""
                val desc = "${stockItem?.name ?: "N/A"} (${unit})"
                val amount = entry.taxableValue()

                row.createCell(0).apply {
                    setCellValue((idx + 1).toDouble())
                    cellStyle = numericStyle
                }
                row.createCell(1).apply {
                    setCellValue(desc)
                    cellStyle = dataStyle
                }
                row.createCell(2).apply {
                    setCellValue(entry.quantity)
                    cellStyle = numericStyle
                }
                row.createCell(3).apply {
                    setCellValue(entry.taxableRate())
                    cellStyle = numericStyle
                }
                row.createCell(4).apply {
                    setCellValue(amount)
                    cellStyle = numericStyle
                }
                subTotal += amount
            }

            // Summary section
            val countryName = DashboardPrefs.getTaxCountry(context)
            val isIndia = countryName.equals("India", true)
            val taxLabel = TaxConfigManager.getConfigForCountry(countryName).taxName
            val igstLabel = if (isIndia) "IGST:" else "$taxLabel:"

            var totalCgst = 0.0
            var totalSgst = 0.0
            var totalIgst = 0.0
            var taxRate = 0.0
            
            inventoryEntries.forEach { entry ->
                if (taxRate == 0.0 && entry.taxAmount > 0.0) taxRate = entry.gstRate
                val (cg, sg, ig) = GstComplianceRules.taxSplit(entry)
                totalCgst += cg
                totalSgst += sg
                totalIgst += ig
            }
            val total = subTotal + totalIgst + totalCgst + totalSgst
            var sumStart = rowIndex + 1

            val s1 = sheet.createRow(sumStart++)
            s1.createCell(3).apply {
                setCellValue("Subtotal:")
                cellStyle = summaryStyle
            }
            s1.createCell(4).apply {
                setCellValue(subTotal)
                cellStyle = numericStyle
            }

            if (totalIgst > 0.0) {
                val sTax = sheet.createRow(sumStart++)
                sTax.createCell(3).apply {
                    setCellValue("${if (isIndia) "IGST" else taxLabel} (${fmtRate(taxRate)}%)")
                    cellStyle = summaryStyle
                }
                sTax.createCell(4).apply {
                    setCellValue(totalIgst)
                    cellStyle = numericStyle
                }
            } else {
                if (totalCgst > 0.0) {
                    val sCgst = sheet.createRow(sumStart++)
                    sCgst.createCell(3).apply {
                        setCellValue("CGST (${fmtRate(taxRate / 2)}%)")
                        cellStyle = summaryStyle
                    }
                    sCgst.createCell(4).apply {
                        setCellValue(totalCgst)
                        cellStyle = numericStyle
                    }
                }
                if (totalSgst > 0.0) {
                    val sSgst = sheet.createRow(sumStart++)
                    sSgst.createCell(3).apply {
                        setCellValue("SGST (${fmtRate(taxRate / 2)}%)")
                        cellStyle = summaryStyle
                    }
                    sSgst.createCell(4).apply {
                        setCellValue(totalSgst)
                        cellStyle = numericStyle
                    }
                }
            }

            val s3 = sheet.createRow(sumStart++)
            s3.createCell(3).apply {
                setCellValue("Total:")
                cellStyle = summaryStyle
            }
            s3.createCell(4).apply {
                setCellValue(total)
                cellStyle = summaryValueStyle
            }

            sumStart += 2
            val termsRow = sheet.createRow(sumStart++)
            termsRow.createCell(0).apply {
                setCellValue("Terms & Conditions:")
                cellStyle = labelStyle
            }
            val term1 = sheet.createRow(sumStart++)
            term1.createCell(0).apply {
                setCellValue("1. Goods once sold will not be taken back.")
                cellStyle = dataStyle
            }
            val term2 = sheet.createRow(sumStart++)
            term2.createCell(0).apply {
                setCellValue("2. Subject to local jurisdiction.")
                cellStyle = dataStyle
            }
            val term3 = sheet.createRow(sumStart++)
            term3.createCell(0).apply {
                setCellValue("3. Interest @ 18% p.a. will be charged for delayed payments.")
                cellStyle = dataStyle
            }

            // Set explicit column widths (autoSizeColumn is a no-op on Android without AWT fonts)
            sheet.setColumnWidth(0, 1500)  // No
            sheet.setColumnWidth(1, 8000)  // Item Description
            sheet.setColumnWidth(2, 2500)  // Qty
            sheet.setColumnWidth(3, 3000)  // Rate
            sheet.setColumnWidth(4, 4000)  // Amount

            FileOutputStream(file).use { workbook.write(it) }
            workbook.close()
            ExcelNavigator.open(file.absolutePath, "Invoice #${voucher.voucherNumber}")
        } catch (t: Throwable) {
            t.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to export Excel: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
        }
    }

    private fun addInvoiceItemCell(table: PdfPTable, text: String, font: Font, bgColor: BaseColor, align: Int = Element.ALIGN_LEFT) {
        val cell = PdfPCell(Phrase(text, font))
        cell.backgroundColor = bgColor
        cell.border = Rectangle.NO_BORDER
        cell.setPaddingTop(6f)
        cell.setPaddingBottom(6f)
        cell.setPaddingLeft(5f)
        cell.setPaddingRight(5f)
        cell.horizontalAlignment = align
        table.addCell(cell)
    }
    
    private fun addCell(table: PdfPTable, text: String, font: Font, horizontalAlignment: Int = Element.ALIGN_LEFT) {
        val cell = PdfPCell(Phrase(text, font))
        cell.setPaddingLeft(5f)
        cell.setPaddingRight(5f)
        cell.setPaddingTop(5f)
        cell.setPaddingBottom(5f)
        cell.horizontalAlignment = horizontalAlignment
        table.addCell(cell)
    }

    private fun addHeaderCell(table: PdfPTable, text: String, font: Font, backgroundColor: BaseColor, horizontalAlignment: Int = Element.ALIGN_CENTER) {
        val cell = PdfPCell(Phrase(text, font))
        cell.backgroundColor = backgroundColor
        cell.setPaddingLeft(5f)
        cell.setPaddingRight(5f)
        cell.setPaddingTop(8f)
        cell.setPaddingBottom(8f)
        cell.horizontalAlignment = horizontalAlignment
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        table.addCell(cell)
    }

    private fun addLabelValueCells(table: PdfPTable, label1: String, value1: String, label2: String, value2: String) {
        val labelFont = FontFactory.getFont(FontFactory.HELVETICA, 10f, BaseColor.DARK_GRAY)
        val valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, BaseColor.BLACK)

        addCell(table, label1, labelFont)
        addCell(table, value1, valueFont)
        addCell(table, label2, labelFont)
        addCell(table, value2, valueFont)
    }

    private fun addLabelValueCells(table: PdfPTable, label: String, value: String, isBold: Boolean = false, bgColor: BaseColor? = null) {
        val labelFont = if (isBold) FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f) else FontFactory.getFont(FontFactory.HELVETICA, 10f)
        val valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f)

        val labelCell = PdfPCell(Phrase(label, labelFont))
        labelCell.border = Rectangle.NO_BORDER
        labelCell.horizontalAlignment = Element.ALIGN_RIGHT
        labelCell.setPadding(5f)
        bgColor?.let { labelCell.backgroundColor = it }
        table.addCell(labelCell)

        val valueCell = PdfPCell(Phrase(value, valueFont))
        valueCell.border = Rectangle.NO_BORDER
        valueCell.horizontalAlignment = Element.ALIGN_RIGHT
        valueCell.setPadding(5f)
        bgColor?.let { valueCell.backgroundColor = it }
        table.addCell(valueCell)
    }

    fun saveFileToDownloads(context: Context, file: File, fileName: String, mimeType: String): Boolean {
        return try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val dest = java.io.File(downloadsDir, fileName)
            file.inputStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    /**
     * Sends a PDF straight to Android's system Print dialog — one tap, no intermediate
     * "open with a PDF viewer" step. The dialog itself lists every printer the OS already
     * knows about (Mopria for generic Wi-Fi printers, Bluetooth print services, manufacturer
     * plugins like HP/Epson/Brother/Canon, "Save as PDF") — this is the actual
     * "works with whatever printer the user has" mechanism Android provides; nothing more
     * automatic exists without shipping a driver per printer brand.
     */
    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun printPdf(context: Context, file: File, title: String) {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "Print file not found or empty", Toast.LENGTH_SHORT).show()
            return
        }

        val activity = context.findActivity() ?: (context as? Activity)
        val targetContext = activity ?: context

        val printManager = try {
            targetContext.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
        } catch (_: Throwable) {
            null
        }

        if (printManager == null) {
            Toast.makeText(context, "Print service unavailable on this device", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            printManager.print(title, object : android.print.PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: android.print.PrintAttributes,
                    newAttributes: android.print.PrintAttributes,
                    cancellationSignal: android.os.CancellationSignal,
                    callback: LayoutResultCallback,
                    extras: android.os.Bundle
                ) {
                    if (cancellationSignal.isCanceled) {
                        callback.onLayoutCancelled()
                        return
                    }
                    val info = android.print.PrintDocumentInfo.Builder(title)
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(android.print.PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build()
                    callback.onLayoutFinished(info, newAttributes != oldAttributes)
                }

                override fun onWrite(
                    pages: Array<android.print.PageRange>,
                    destination: android.os.ParcelFileDescriptor,
                    cancellationSignal: android.os.CancellationSignal,
                    callback: WriteResultCallback
                ) {
                    try {
                        java.io.FileInputStream(file).use { input ->
                            // Use AutoCloseOutputStream to safely wrap ParcelFileDescriptor without double-closing fd
                            android.os.ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                                val buffer = ByteArray(16384)
                                var read: Int
                                while (input.read(buffer).also { read = it } >= 0) {
                                    if (cancellationSignal.isCanceled) {
                                        callback.onWriteCancelled()
                                        return
                                    }
                                    output.write(buffer, 0, read)
                                }
                                output.flush()
                            }
                        }
                        callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        android.util.Log.e("ExportUtils", "Print onWrite failed", e)
                        callback.onWriteFailed(e.message)
                    }
                }
            }, null)
        } catch (t: Throwable) {
            android.util.Log.e("ExportUtils", "PrintManager.print failed", t)
            Toast.makeText(context, "Unable to launch print dialog: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareInvoiceOnWhatsApp(
        context: Context,
        voucher: Voucher,
        customerLedger: Ledger?,
        inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>,
        units: List<Unit>,
        company: Company?
    ) {
        val imageFile = generateInvoicePreviewImage(context, voucher, customerLedger, inventoryEntries, stockItems, units, company)
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
        val link = buildShareLink(voucher)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Invoice from ${company?.name ?: "us"} — Sent via Teller\n$link")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            `package` = "com.whatsapp"
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Invoice from ${company?.name ?: "us"} — Sent via Teller\n$link")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(fallback, "Share Invoice"))
        }
    }

    private fun buildShareLink(voucher: Voucher): String {
        val safeNo = voucher.voucherNumber.replace(" ", "-").replace("/", "-")
        return "$SHARE_LINK_BASE$safeNo"
    }

    private fun isLicensedFast(context: Context): Boolean {
        val key = LicenseUtils.getStoredLicenseKey(context)
        return key != null || isInstalledFromPlayStore(context)
    }

    private fun isInstalledFromPlayStore(context: Context): Boolean {
        return try {
            val installer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            installer == "com.android.vending"
        } catch (e: Exception) {
            false
        }
    }

    private fun generateInvoicePreviewImage(
        context: Context,
        voucher: Voucher,
        customerLedger: Ledger?,
        inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>,
        units: List<Unit>,
        company: Company?
    ): File {
        val width = 1080
        val height = 1350
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint().apply { color = Color.parseColor("#121212") }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val accentPaint = Paint().apply { color = Color.parseColor("#303F9F") }
        val accentRect = RectF(0f, 0f, width.toFloat(), 320f)
        canvas.drawRect(accentRect, accentPaint)

        val white = Paint().apply { color = Color.WHITE; isAntiAlias = true }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 72f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            isAntiAlias = true
        }
        val subPaint = Paint().apply {
            color = Color.LTGRAY
            textSize = 32f
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.parseColor("#B0BEC5")
            textSize = 34f
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val amountPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            isAntiAlias = true
        }

        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(voucher.date)
        val title = if (voucher.type.equals("Sales", true)) "INVOICE" else voucher.type.uppercase(Locale.getDefault())

        val countryName = DashboardPrefs.getTaxCountry(context)
        val taxConfig = TaxConfigManager.getConfigForCountry(countryName)
        val taxIdLabel = taxConfig.taxIdName
        val currencySymbol = company?.currencySymbol ?: "₹"

        canvas.drawText(title, 40f, 180f, titlePaint)
        canvas.drawText(company?.name ?: "Your Company", 40f, 240f, subPaint)
        canvas.drawText("$taxIdLabel: ${company?.gstin ?: "N/A"}", 40f, 290f, subPaint)

        val contentTop = 380f
        canvas.drawText("Invoice #", 40f, contentTop, labelPaint)
        canvas.drawText(voucher.voucherNumber, 40f, contentTop + 44f, valuePaint)

        canvas.drawText("Date", 400f, contentTop, labelPaint)
        canvas.drawText(dateStr, 400f, contentTop + 44f, valuePaint)

        canvas.drawText("Customer", 40f, contentTop + 120f, labelPaint)
        canvas.drawText(invoiceBuyerName(voucher, customerLedger), 40f, contentTop + 164f, valuePaint)
        val grp = customerLedger?.group?.trim()?.lowercase() ?: ""
        val isFinanced = grp == "finance companies" || grp.startsWith("finance comp")
        if (isFinanced && customerLedger != null) {
            canvas.drawText("Financed by - ${customerLedger.name}", 40f, contentTop + 208f, subPaint)
        }

        // Tax-inclusive amounts: derive the base and tax from the stored fields rather
        // than assuming a flat 5% on top, which inflated this preview's total.
        var subTotal = 0.0
        var taxAmount = 0.0
        inventoryEntries.forEach { entry ->
            subTotal += entry.taxableValue()
            taxAmount += entry.taxAmount.paiseToRupees()
        }
        val grandTotal = inventoryEntries.payableTotal()

        canvas.drawText("Total", 40f, contentTop + 260f, labelPaint)
        canvas.drawText("$currencySymbol ${"%.2f".format(grandTotal)}", 40f, contentTop + 310f, amountPaint)

        val boxPaint = Paint().apply { color = Color.parseColor("#1E1E1E") }
        val box = RectF(40f, contentTop + 360f, width - 40f, contentTop + 720f)
        canvas.drawRoundRect(box, 24f, 24f, boxPaint)

        var y = contentTop + 420f
        val itemLabel = Paint().apply { color = Color.WHITE; textSize = 36f; isAntiAlias = true }
        val itemQty = Paint().apply { color = Color.LTGRAY; textSize = 32f; isAntiAlias = true }
        val maxRows = 6
        inventoryEntries.take(maxRows).forEach { entry ->
            val stockItem = stockItems.find { it.id == entry.stockItemId }
            val unit = units.find { it.id == stockItem?.unitId }?.name ?: ""
            val name = stockItem?.name ?: "Item"
            canvas.drawText(name, 60f, y, itemLabel)
            canvas.drawText("Qty ${"%.2f".format(entry.quantity)} $unit  Rate $currencySymbol${"%.2f".format(entry.rate)}", 60f, y + 38f, itemQty)
            y += 72f
        }

        val footerText = if (isLicensedFast(context)) "Powered by Teller" else "Made with Teller — teller.finance"
        val footerPaint = Paint().apply {
            color = Color.parseColor("#757575")
            textSize = 34f
            isAntiAlias = true
        }
        canvas.drawText(footerText, 40f, height - 50f, footerPaint)

        val imageFile = File(context.getExternalFilesDir(null), "InvoicePreview_${System.currentTimeMillis()}.png")
        FileOutputStream(imageFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return imageFile
    }

    // ---- Missing export helpers (added back) ----
    // ---- Theme helper classes and functions for PDF ----
    enum class ScreenCategory {
        PURPLE, YELLOW, TEAL, GREEN
    }

    private fun getScreenCategory(title: String, fileName: String): ScreenCategory {
        val combined = "$title $fileName".lowercase(Locale.getDefault())
        return when {
            combined.contains("ledger list") || combined.contains("group list") || 
            combined.contains("list of accounts") || combined.contains("accountsinfo") || 
            combined.contains("ledgerlist") || combined.contains("grouplist") ||
            combined.contains("accounts info") -> ScreenCategory.TEAL
            
            combined.contains("stock summary") || combined.contains("stockitem") || 
            combined.contains("unitlist") || combined.contains("livestock") || 
            combined.contains("live stock") || combined.contains("inventoryinfo") || 
            combined.contains("stock item") || combined.contains("unit list") ||
            combined.contains("inventory info") -> ScreenCategory.GREEN
            
            combined.contains("balance sheet") || combined.contains("profit & loss") || 
            combined.contains("profitandloss") || combined.contains("trial balance") || 
            combined.contains("trialbalance") || combined.contains("outstanding") || 
            combined.contains("pending") -> ScreenCategory.YELLOW
            
            else -> ScreenCategory.PURPLE
        }
    }

    class PdfThemeColors(
        val backgroundColor: BaseColor,
        val backgroundGradientEnd: BaseColor?,
        val cardColor: BaseColor,
        val cardAlternateColor: BaseColor,
        val headerBackgroundColor: BaseColor,
        val headerTextColor: BaseColor,
        val headerBorderColor: BaseColor,
        val textColor: BaseColor,
        val titleColor: BaseColor,
        val subtitleColor: BaseColor,
        val accentColor: BaseColor,
        val borderColor: BaseColor,
        val isDark: Boolean
    )

    private fun getPdfThemeColors(theme: AppTheme, category: ScreenCategory): PdfThemeColors {
        if (theme != AppTheme.DEFAULT) {
            return when (theme) {
                AppTheme.MODERN_LIGHT -> PdfThemeColors(
                    backgroundColor = BaseColor(245, 247, 250),
                    backgroundGradientEnd = null,
                    cardColor = BaseColor.WHITE,
                    cardAlternateColor = BaseColor(235, 237, 240),
                    headerBackgroundColor = BaseColor(0, 131, 143),
                    headerTextColor = BaseColor.WHITE,
                    headerBorderColor = BaseColor(0, 131, 143),
                    textColor = BaseColor(26, 28, 30),
                    titleColor = BaseColor(26, 28, 30),
                    subtitleColor = BaseColor(120, 120, 120),
                    accentColor = BaseColor(123, 31, 162),
                    borderColor = BaseColor(225, 226, 230),
                    isDark = false
                )
                AppTheme.CYBER_FINANCE -> PdfThemeColors(
                    backgroundColor = BaseColor(5, 5, 5),
                    backgroundGradientEnd = null,
                    cardColor = BaseColor(18, 18, 18),
                    cardAlternateColor = BaseColor(28, 28, 28),
                    headerBackgroundColor = BaseColor(0, 229, 255),
                    headerTextColor = BaseColor.BLACK,
                    headerBorderColor = BaseColor(0, 229, 255),
                    textColor = BaseColor.WHITE,
                    titleColor = BaseColor(0, 229, 255),
                    subtitleColor = BaseColor(255, 64, 129),
                    accentColor = BaseColor(0, 229, 255),
                    borderColor = BaseColor(0, 229, 255),
                    isDark = true
                )
                AppTheme.BENTO -> PdfThemeColors(
                    backgroundColor = BaseColor(10, 14, 33),
                    backgroundGradientEnd = null,
                    cardColor = BaseColor(248, 112, 96), // Salmon Red
                    cardAlternateColor = BaseColor(144, 224, 239), // Pale Blue
                    headerBackgroundColor = BaseColor(44, 62, 80),
                    headerTextColor = BaseColor.WHITE,
                    headerBorderColor = BaseColor(44, 62, 80),
                    textColor = BaseColor(33, 33, 33),
                    titleColor = BaseColor.WHITE,
                    subtitleColor = BaseColor(144, 224, 239),
                    accentColor = BaseColor(144, 224, 239),
                    borderColor = BaseColor(10, 14, 33),
                    isDark = true
                )
                AppTheme.CRYSTAL -> PdfThemeColors(
                    backgroundColor = BaseColor(18, 23, 34),
                    backgroundGradientEnd = BaseColor(30, 38, 51),
                    cardColor = BaseColor(40, 50, 75),
                    cardAlternateColor = BaseColor(48, 58, 85),
                    headerBackgroundColor = BaseColor(60, 75, 110),
                    headerTextColor = BaseColor.WHITE,
                    headerBorderColor = BaseColor(90, 110, 150),
                    textColor = BaseColor.WHITE,
                    titleColor = BaseColor.WHITE,
                    subtitleColor = BaseColor(200, 200, 200),
                    accentColor = BaseColor(139, 92, 246),
                    borderColor = BaseColor(70, 85, 120),
                    isDark = true
                )
                else -> getPdfThemeColors(AppTheme.DEFAULT, category)
            }
        }

        return when (category) {
            ScreenCategory.PURPLE -> PdfThemeColors(
                backgroundColor = BaseColor(26, 5, 51),
                backgroundGradientEnd = BaseColor(45, 27, 78),
                cardColor = BaseColor(45, 27, 78),
                cardAlternateColor = BaseColor(35, 20, 65),
                headerBackgroundColor = BaseColor(77, 48, 105),
                headerTextColor = BaseColor.WHITE,
                headerBorderColor = BaseColor(77, 48, 105),
                textColor = BaseColor.WHITE,
                titleColor = BaseColor.WHITE,
                subtitleColor = BaseColor(184, 160, 208),
                accentColor = BaseColor(184, 160, 208),
                borderColor = BaseColor(77, 48, 105),
                isDark = true
            )
            ScreenCategory.YELLOW -> PdfThemeColors(
                backgroundColor = BaseColor(46, 36, 5),
                backgroundGradientEnd = BaseColor(10, 16, 33),
                cardColor = BaseColor(35, 30, 15),
                cardAlternateColor = BaseColor(25, 22, 10),
                headerBackgroundColor = BaseColor(80, 60, 15),
                headerTextColor = BaseColor.WHITE,
                headerBorderColor = BaseColor(80, 60, 15),
                textColor = BaseColor.WHITE,
                titleColor = BaseColor.WHITE,
                subtitleColor = BaseColor(250, 230, 160),
                accentColor = BaseColor(218, 165, 32),
                borderColor = BaseColor(80, 60, 15),
                isDark = true
            )
            ScreenCategory.TEAL -> PdfThemeColors(
                backgroundColor = BaseColor(5, 36, 32),
                backgroundGradientEnd = BaseColor(15, 36, 36),
                cardColor = BaseColor(15, 36, 36),
                cardAlternateColor = BaseColor(10, 26, 26),
                headerBackgroundColor = BaseColor(10, 70, 60),
                headerTextColor = BaseColor.WHITE,
                headerBorderColor = BaseColor(10, 70, 60),
                textColor = BaseColor.WHITE,
                titleColor = BaseColor.WHITE,
                subtitleColor = BaseColor(150, 240, 230),
                accentColor = BaseColor(0, 229, 255),
                borderColor = BaseColor(10, 70, 60),
                isDark = true
            )
            ScreenCategory.GREEN -> PdfThemeColors(
                backgroundColor = BaseColor(5, 36, 20),
                backgroundGradientEnd = BaseColor(15, 36, 32),
                cardColor = BaseColor(15, 36, 32),
                cardAlternateColor = BaseColor(10, 26, 22),
                headerBackgroundColor = BaseColor(10, 70, 40),
                headerTextColor = BaseColor.WHITE,
                headerBorderColor = BaseColor(10, 70, 40),
                textColor = BaseColor.WHITE,
                titleColor = BaseColor.WHITE,
                subtitleColor = BaseColor(160, 250, 200),
                accentColor = BaseColor(0, 230, 118),
                borderColor = BaseColor(10, 70, 40),
                isDark = true
            )
        }
    }

    private fun getBentoPastelColor(index: Int): BaseColor {
        val bentoPastels = listOf(
            BaseColor(248, 112, 96),  // Salmon Red
            BaseColor(144, 224, 239), // Pale Blue
            BaseColor(250, 243, 221), // Pale Yellow
            BaseColor(255, 218, 185), // Pastel Orange
            BaseColor(193, 251, 164), // Mint Green
            BaseColor(212, 193, 236)  // Lavender
        )
        return bentoPastels[index % bentoPastels.size]
    }

    private fun getAlignmentForColumn(headerName: String, cellText: String = ""): Int {
        val lower = headerName.lowercase(Locale.getDefault())
        val isNumericHeader = lower.contains("amount") || lower.contains("total") || lower.contains("debit") || 
                lower.contains("credit") || lower.contains("price") || lower.contains("rate") || 
                lower.contains("value") || lower.contains("balance") || lower.contains("pending") ||
                lower.contains("interest") || lower.contains("discount") || lower.contains("tax")
                
        val isCenterHeader = lower.contains("date") || lower.contains("vch no") || lower.contains("type") || 
                lower.contains("side") || lower.contains("qty") || lower.contains("quantity") || 
                lower.contains("no") || lower.contains("status") || lower.contains("days") || 
                lower.contains("vehicle") || lower.contains("in") || lower.contains("out")
                
        if (isNumericHeader) return Element.ALIGN_RIGHT
        if (isCenterHeader) return Element.ALIGN_CENTER
        
        if (cellText.isNotBlank()) {
            val cleanText = cellText.replace("₹", "").replace("$", "").replace(",", "").trim()
            if (cleanText.toDoubleOrNull() != null) {
                return Element.ALIGN_RIGHT
            }
        }
        
        return Element.ALIGN_LEFT
    }

    private fun getThemedFontForCell(text: String, defaultColor: BaseColor, themeColors: PdfThemeColors): Font {
        val lower = text.lowercase(Locale.getDefault())
        val color = when {
            text == "Sales" || text.startsWith("Sales ") || text == "Dr" || lower.contains("debit") -> {
                if (themeColors.isDark) BaseColor(0, 230, 118) else BaseColor(46, 125, 50)
            }
            text == "Purchase" || text.startsWith("Purchase ") || text == "Cr" || lower.contains("credit") -> {
                if (themeColors.isDark) BaseColor(255, 82, 82) else BaseColor(198, 40, 40)
            }
            text == "Payment" -> {
                if (themeColors.isDark) BaseColor(64, 196, 255) else BaseColor(21, 101, 192)
            }
            text == "Receipt" -> {
                if (themeColors.isDark) BaseColor(255, 171, 64) else BaseColor(239, 108, 0)
            }
            else -> defaultColor
        }
        
        val style = if (text == "Sales" || text == "Purchase" || text == "Payment" || text == "Receipt" || 
                        text == "Dr" || text == "Cr" || text.startsWith("₹") || text.startsWith("$") || 
                        text.contains("Total")) {
            Font.BOLD
        } else {
            Font.NORMAL
        }
        
        return FontFactory.getFont(FontFactory.HELVETICA, 9f, style, color)
    }

    class PdfBackgroundEvent(private val theme: AppTheme, private val category: ScreenCategory) : PdfPageEventHelper() {
        override fun onStartPage(writer: PdfWriter, document: Document) {
            val cb = writer.directContentUnder
            val pageSize = document.pageSize
            
            if (theme == AppTheme.DEFAULT) {
                val colors = when (category) {
                    ScreenCategory.PURPLE -> Pair(BaseColor(26, 5, 51), BaseColor(45, 27, 78))
                    ScreenCategory.YELLOW -> Pair(BaseColor(46, 36, 5), BaseColor(10, 16, 33))
                    ScreenCategory.TEAL -> Pair(BaseColor(5, 36, 32), BaseColor(15, 36, 36))
                    ScreenCategory.GREEN -> Pair(BaseColor(5, 36, 20), BaseColor(15, 36, 32))
                }
                
                val g = PdfShading.simpleAxial(
                    writer, 
                    0f, pageSize.height, 
                    0f, 0f, 
                    colors.first, 
                    colors.second
                )
                val shadingPattern = PdfShadingPattern(g)
                cb.setShadingFill(shadingPattern)
                cb.rectangle(0f, 0f, pageSize.width, pageSize.height)
                cb.fill()
                return
            }
            
            when (theme) {
                AppTheme.CYBER_FINANCE -> {
                    cb.setColorFill(BaseColor(5, 5, 5))
                    cb.rectangle(0f, 0f, pageSize.width, pageSize.height)
                    cb.fill()
                }
                AppTheme.BENTO -> {
                    cb.setColorFill(BaseColor(10, 14, 33))
                    cb.rectangle(0f, 0f, pageSize.width, pageSize.height)
                    cb.fill()
                }
                AppTheme.CRYSTAL -> {
                    val g = PdfShading.simpleAxial(
                        writer, 
                        0f, pageSize.height, 
                        0f, 0f, 
                        BaseColor(18, 23, 34), 
                        BaseColor(30, 38, 51)
                    )
                    val shadingPattern = PdfShadingPattern(g)
                    cb.setShadingFill(shadingPattern)
                    cb.rectangle(0f, 0f, pageSize.width, pageSize.height)
                    cb.fill()
                }
                AppTheme.MODERN_LIGHT -> {
                    cb.setColorFill(BaseColor(245, 247, 250))
                    cb.rectangle(0f, 0f, pageSize.width, pageSize.height)
                    cb.fill()
                }
                else -> {}
            }
        }
    }

    fun exportReportToPdf(
        context: Context, fileName: String, subtitle: String, title: String,
        headers: List<String>, widths: FloatArray, data: List<List<String>>
    ) {
        scope.launch {
        val file = File(context.getExternalFilesDir(null), fileName)
        val document = Document(PageSize.A4, 36f, 36f, 36f, 36f)

        try {
            val writer = PdfWriter.getInstance(document, FileOutputStream(file))
            val currentTheme = ThemeManager.getCurrentTheme(context)
            val category = getScreenCategory(title, fileName)
            
            writer.pageEvent = PdfBackgroundEvent(currentTheme, category)
            document.open()

            val themeColors = getPdfThemeColors(currentTheme, category)

            // Header Banner
            val bannerTable = PdfPTable(1)
            bannerTable.widthPercentage = 100f
            bannerTable.setSpacingBefore(10f)
            bannerTable.setSpacingAfter(15f)
            
            val bannerCell = PdfPCell()
            bannerCell.backgroundColor = themeColors.headerBackgroundColor
            bannerCell.border = Rectangle.BOX
            bannerCell.borderColor = themeColors.borderColor
            bannerCell.borderWidth = 1f
            bannerCell.setPadding(12f)
            
            val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f, themeColors.headerTextColor)
            val pTitle = Paragraph(title.uppercase(Locale.getDefault()), titleFont)
            pTitle.alignment = Element.ALIGN_LEFT
            bannerCell.addElement(pTitle)
            
            if (subtitle.isNotBlank()) {
                val subColor = if (themeColors.isDark) BaseColor(200, 200, 200) else BaseColor(80, 80, 80)
                val pSub = Paragraph(subtitle, FontFactory.getFont(FontFactory.HELVETICA, 10f, subColor))
                pSub.alignment = Element.ALIGN_LEFT
                bannerCell.addElement(pSub)
            }
            
            bannerTable.addCell(bannerCell)
            document.add(bannerTable)

            val table = PdfPTable(headers.size)
            table.widthPercentage = 100f
            table.setSpacingBefore(10f)
            table.setSpacingAfter(20f)
            try {
                table.setWidths(widths)
            } catch (e: Exception) {
                // fallback: evenly distributed
            }

            val thf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, themeColors.headerTextColor)
            for (header in headers) {
                val cell = PdfPCell(Phrase(header, thf))
                cell.backgroundColor = themeColors.headerBackgroundColor
                cell.border = Rectangle.BOX
                cell.borderColor = themeColors.headerBorderColor
                cell.borderWidth = 1.5f
                cell.setPadding(8f)
                cell.horizontalAlignment = getAlignmentForColumn(header)
                cell.verticalAlignment = Element.ALIGN_MIDDLE
                table.addCell(cell)
            }

            val defaultTextColor = if (currentTheme == AppTheme.BENTO) BaseColor(33, 33, 33) else themeColors.textColor
            for ((rowIdx, row) in data.withIndex()) {
                val rowBgColor = if (currentTheme == AppTheme.BENTO) {
                    getBentoPastelColor(rowIdx)
                } else {
                    if (rowIdx % 2 == 0) themeColors.cardColor else themeColors.cardAlternateColor
                }

                for ((colIdx, cellText) in row.withIndex()) {
                    val cellFont = getThemedFontForCell(cellText, defaultTextColor, themeColors)
                    val pdfCell = PdfPCell(Phrase(cellText, cellFont))
                    pdfCell.backgroundColor = rowBgColor
                    pdfCell.border = Rectangle.BOX
                    pdfCell.borderColor = themeColors.borderColor
                    pdfCell.borderWidth = 0.5f
                    pdfCell.setPadding(8f)
                    
                    val headerName = headers.getOrNull(colIdx) ?: ""
                    pdfCell.horizontalAlignment = getAlignmentForColumn(headerName, cellText)
                    pdfCell.verticalAlignment = Element.ALIGN_MIDDLE
                    table.addCell(pdfCell)
                }
            }

            document.add(table)
            
            addInvoiceFooter(document, context, themeColors.accentColor)
            
            document.close()
            
            // Auto-open in in-app viewer
            PdfNavigator.open(file.absolutePath, title)
            
            // Also provide system share option
            shareFile(context, file, "application/pdf", "Share Report")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        }
    }

    /**
     * Build a generic voucher/report PDF synchronously and return the raw bytes. Mirrors
     * exportReportToPdf() (same themed banner + table + footer) but writes to memory instead of a
     * file, so the PC desktop companion can serve the identical PDF for non-invoice voucher types
     * (Receipt/Payment/Journal/Contra). Keep the rendering in sync with exportReportToPdf.
     */
    fun buildReportPdfBytes(
        context: Context, fileName: String, subtitle: String, title: String,
        headers: List<String>, widths: FloatArray, data: List<List<String>>
    ): ByteArray? {
        return try {
            val bos = ByteArrayOutputStream()
            val document = Document(PageSize.A4, 36f, 36f, 36f, 36f)
            val writer = PdfWriter.getInstance(document, bos)

            val currentTheme = ThemeManager.getCurrentTheme(context)
            val category = getScreenCategory(title, fileName)
            writer.pageEvent = PdfBackgroundEvent(currentTheme, category)
            document.open()

            val themeColors = getPdfThemeColors(currentTheme, category)

            // Header Banner
            val bannerTable = PdfPTable(1)
            bannerTable.widthPercentage = 100f
            bannerTable.setSpacingBefore(10f)
            bannerTable.setSpacingAfter(15f)

            val bannerCell = PdfPCell()
            bannerCell.backgroundColor = themeColors.headerBackgroundColor
            bannerCell.border = Rectangle.BOX
            bannerCell.borderColor = themeColors.borderColor
            bannerCell.borderWidth = 1f
            bannerCell.setPadding(12f)

            val titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f, themeColors.headerTextColor)
            val pTitle = Paragraph(title.uppercase(Locale.getDefault()), titleFont)
            pTitle.alignment = Element.ALIGN_LEFT
            bannerCell.addElement(pTitle)

            if (subtitle.isNotBlank()) {
                val subColor = if (themeColors.isDark) BaseColor(200, 200, 200) else BaseColor(80, 80, 80)
                val pSub = Paragraph(subtitle, FontFactory.getFont(FontFactory.HELVETICA, 10f, subColor))
                pSub.alignment = Element.ALIGN_LEFT
                bannerCell.addElement(pSub)
            }

            bannerTable.addCell(bannerCell)
            document.add(bannerTable)

            val table = PdfPTable(headers.size)
            table.widthPercentage = 100f
            table.setSpacingBefore(10f)
            table.setSpacingAfter(20f)
            try {
                table.setWidths(widths)
            } catch (e: Exception) {
                // fallback: evenly distributed
            }

            val thf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, themeColors.headerTextColor)
            for (header in headers) {
                val cell = PdfPCell(Phrase(header, thf))
                cell.backgroundColor = themeColors.headerBackgroundColor
                cell.border = Rectangle.BOX
                cell.borderColor = themeColors.headerBorderColor
                cell.borderWidth = 1.5f
                cell.setPadding(8f)
                cell.horizontalAlignment = getAlignmentForColumn(header)
                cell.verticalAlignment = Element.ALIGN_MIDDLE
                table.addCell(cell)
            }

            val defaultTextColor = if (currentTheme == AppTheme.BENTO) BaseColor(33, 33, 33) else themeColors.textColor
            for ((rowIdx, row) in data.withIndex()) {
                val rowBgColor = if (currentTheme == AppTheme.BENTO) {
                    getBentoPastelColor(rowIdx)
                } else {
                    if (rowIdx % 2 == 0) themeColors.cardColor else themeColors.cardAlternateColor
                }

                for ((colIdx, cellText) in row.withIndex()) {
                    val cellFont = getThemedFontForCell(cellText, defaultTextColor, themeColors)
                    val pdfCell = PdfPCell(Phrase(cellText, cellFont))
                    pdfCell.backgroundColor = rowBgColor
                    pdfCell.border = Rectangle.BOX
                    pdfCell.borderColor = themeColors.borderColor
                    pdfCell.borderWidth = 0.5f
                    pdfCell.setPadding(8f)

                    val headerName = headers.getOrNull(colIdx) ?: ""
                    pdfCell.horizontalAlignment = getAlignmentForColumn(headerName, cellText)
                    pdfCell.verticalAlignment = Element.ALIGN_MIDDLE
                    table.addCell(pdfCell)
                }
            }

            document.add(table)
            addInvoiceFooter(document, context, themeColors.accentColor)
            document.close()

            bos.toByteArray()
        } catch (e: Exception) {
            android.util.Log.e("ExportUtils", "buildReportPdfBytes failed", e)
            null
        }
    }

    fun exportReportToExcel(
        context: Context, fileName: String, sheetName: String,
        headers: List<String>, data: List<List<String>>
    ) {
        scope.launch {
            com.minhas.teller.utils.export.ExcelExportEngine.exportReportToExcel(
                context, fileName, sheetName, headers, data
            )
        }
    }

    internal fun addInvoiceFooter(document: Document, context: Context, accentColor: BaseColor) {
        document.add(Chunk.NEWLINE)
        val line = LineSeparator(0.5f, 100f, BaseColor.LIGHT_GRAY, Element.ALIGN_CENTER, 0f)
        document.add(line)

        val footerText = if (isLicensedFast(context)) {
            "Powered by Teller"
        } else {
            "Made with Teller — Get the app at teller.finance"
        }
        val footer = Paragraph(footerText, FontFactory.getFont(FontFactory.HELVETICA, 8f, BaseColor.GRAY))
        footer.alignment = Element.ALIGN_CENTER
        document.add(footer)
    }

    private fun addSummaryRow(table: PdfPTable, label: String, value: String, isBold: Boolean = false, accentColor: BaseColor? = null) {
        val labelFont = if (isBold) FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f) else FontFactory.getFont(FontFactory.HELVETICA, 10f)
        val valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f)

        val labelCell = PdfPCell(Phrase(label, labelFont))
        labelCell.border = Rectangle.NO_BORDER
        labelCell.horizontalAlignment = Element.ALIGN_RIGHT
        table.addCell(labelCell)

        val valueCell = PdfPCell(Phrase(value, valueFont))
        valueCell.border = Rectangle.NO_BORDER
        valueCell.horizontalAlignment = Element.ALIGN_RIGHT
        if (isBold && accentColor != null) {
            // Best-effort subtle background using provided color
            valueCell.backgroundColor = BaseColor(accentColor.red, accentColor.green, accentColor.blue)
        }
        table.addCell(valueCell)
    }

    /**
     * Generate a professional quotation PDF
     */
    fun generateQuotationPdf(
        context: Context,
        quotation: com.minhas.teller.data.entities.Quotation,
        company: Company? = null,
        share: Boolean = true
    ) = QuotationPdfExporter.generateQuotationPdf(context, quotation, company, share)
    fun exportFinancialHealthPassport(
        context: Context,
        scoreData: TellerScoreData,
        company: Company?
    ) {
        val fileName = "Teller_Financial_Health_Passport_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null), fileName)
        val document = Document(PageSize.A4, 36f, 36f, 36f, 36f)

        Toast.makeText(context, "Generating Financial Passport...", Toast.LENGTH_SHORT).show()

        scope.launch {
            try {
                val writer = PdfWriter.getInstance(document, FileOutputStream(file))
                document.open()

                // Colors
                val primaryColor = BaseColor(30, 21, 62)       // Deep Indigo/Purple
                val secondaryColor = BaseColor(98, 0, 238)    // Vibrant Purple accent
                val accentGreen = BaseColor(0, 180, 80)       // Success Green
                val accentAmber = BaseColor(220, 150, 0)      // Warning Amber
                val accentRed = BaseColor(220, 50, 50)        // Danger Red
                val darkGray = BaseColor(50, 50, 50)
                val lightGray = BaseColor(240, 240, 240)
                val white = BaseColor.WHITE

                val scoreColor = when {
                    scoreData.overallScore >= 800 -> accentGreen
                    scoreData.overallScore >= 650 -> accentAmber
                    else -> accentRed
                }

                val scoreGrade = when {
                    scoreData.overallScore >= 800 -> "EXCELLENT"
                    scoreData.overallScore >= 650 -> "GOOD"
                    scoreData.overallScore >= 500 -> "AVERAGE"
                    else -> "NEEDS ATTENTION"
                }

                // Fonts
                val fTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20f, white)
                val fSubTitle = FontFactory.getFont(FontFactory.HELVETICA, 10f, BaseColor(184, 160, 208))
                val fHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, primaryColor)
                val fBodyBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, darkGray)
                val fBody = FontFactory.getFont(FontFactory.HELVETICA, 9f, darkGray)
                val fScoreBig = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 38f, white)
                val fScoreLabel = FontFactory.getFont(FontFactory.HELVETICA, 11f, white)

                // ── PAGE 1: EXECUTIVE BRIEF & CREDIT PROFILE ───────────────────
                // 1. HEADER BANNER
                val headerTable = PdfPTable(1)
                headerTable.widthPercentage = 100f
                val headerCell = PdfPCell()
                headerCell.backgroundColor = primaryColor
                headerCell.setPadding(15f)
                headerCell.border = Rectangle.BOX
                headerCell.borderColor = secondaryColor
                headerCell.borderWidth = 1.5f
                
                val pTitle = Paragraph("TELLER FINANCIAL HEALTH PASSPORT", fTitle).apply { alignment = Element.ALIGN_CENTER }
                headerCell.addElement(pTitle)
                val pSub = Paragraph("Official MSME Credit Health & Compliance Identity Report", fSubTitle).apply { alignment = Element.ALIGN_CENTER }
                headerCell.addElement(pSub)
                headerTable.addCell(headerCell)
                document.add(headerTable)
                document.add(Chunk.NEWLINE)

                // 2. BUSINESS PROFILE SUMMARY
                val profileTitle = Paragraph("1. BUSINESS PROFILE IDENTITY", fHeader)
                profileTitle.setSpacingAfter(6f)
                document.add(profileTitle)

                val profileTable = PdfPTable(2)
                profileTable.widthPercentage = 100f
                profileTable.setWidths(floatArrayOf(1.5f, 2.5f))
                profileTable.setSpacingAfter(15f)

                fun addProfileRow(label: String, value: String) {
                    val cLabel = PdfPCell(Phrase(label, fBodyBold))
                    cLabel.backgroundColor = lightGray
                    cLabel.setPadding(6f)
                    cLabel.border = Rectangle.BOX
                    cLabel.borderColor = BaseColor.LIGHT_GRAY
                    profileTable.addCell(cLabel)

                    val cValue = PdfPCell(Phrase(value, fBody))
                    cValue.setPadding(6f)
                    cValue.border = Rectangle.BOX
                    cValue.borderColor = BaseColor.LIGHT_GRAY
                    profileTable.addCell(cValue)
                }

                addProfileRow("Business Legal Name", company?.name ?: "N/A")
                addProfileRow("Tax Registration (GSTIN)", company?.gstin ?: "Unregistered / Exempt")
                addProfileRow("Report Generation Date", SimpleDateFormat("dd MMMM yyyy HH:mm", Locale.getDefault()).format(Date()))
                addProfileRow("Verification Token ID", "TEL-${UUID.randomUUID().toString().substring(0, 8).uppercase(Locale.US)}")
                document.add(profileTable)

                // 3. CREDIT IDENTITY EVALUATION
                val scoreEvalTitle = Paragraph("2. CREDIT EVALUATION & UNDERWRITING GUIDANCE", fHeader)
                scoreEvalTitle.setSpacingAfter(6f)
                document.add(scoreEvalTitle)

                val scoreContainer = PdfPTable(2)
                scoreContainer.widthPercentage = 100f
                scoreContainer.setWidths(floatArrayOf(1.8f, 2.2f))
                scoreContainer.setSpacingAfter(15f)

                // Left: Score Box
                val scoreBox = PdfPCell()
                scoreBox.backgroundColor = primaryColor
                scoreBox.setPadding(12f)
                scoreBox.horizontalAlignment = Element.ALIGN_CENTER
                scoreBox.verticalAlignment = Element.ALIGN_MIDDLE
                scoreBox.border = Rectangle.BOX
                scoreBox.borderColor = scoreColor
                scoreBox.borderWidth = 2f

                val pScoreBig = Paragraph(scoreData.overallScore.toString(), fScoreBig).apply { alignment = Element.ALIGN_CENTER }
                scoreBox.addElement(pScoreBig)
                val pScoreLabel = Paragraph("CREDIT HEALTH SCORE", fScoreLabel).apply { alignment = Element.ALIGN_CENTER }
                scoreBox.addElement(pScoreLabel)
                val pGrade = Paragraph("GRADE: $scoreGrade", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, scoreColor)).apply { alignment = Element.ALIGN_CENTER }
                scoreBox.addElement(pGrade)
                scoreContainer.addCell(scoreBox)

                // Right: Explanation & Lending Guidance
                val guideBox = PdfPCell()
                guideBox.setPadding(12f)
                guideBox.border = Rectangle.BOX
                guideBox.borderColor = BaseColor.LIGHT_GRAY
                guideBox.backgroundColor = white

                val pGuideHeader = Paragraph("LENDING CREDIT RECOMMENDATION", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, primaryColor))
                guideBox.addElement(pGuideHeader)
                guideBox.addElement(Chunk.NEWLINE)

                val riskRating = when {
                    scoreData.overallScore >= 800 -> "LOW RISK CREDIT PROFILE: Pre-approved for Working Capital limits up to INR 25 Lakhs. Highly recommended for unsecured MSME credit facilities based on disciplined ledger cash cycles and high collection rates."
                    scoreData.overallScore >= 650 -> "MODERATE RISK CREDIT PROFILE: Eligible for standard MSME business loans with standard documentation. Cash flow and margins are stable; approval is subject to normal business verification."
                    scoreData.overallScore >= 500 -> "AVERAGE RISK CREDIT PROFILE: Credit approval is typically contingent on secondary collateral, trade guarantees, or tax compliance audit confirmations. Cash flow runways should be monitored."
                    else -> "HIGH RISK CREDIT PROFILE: Restructuring & ledger cleanup recommended. Cash flow cycles reflect tight liquidity. Banker review requires additional collateral or debt guarantees to mitigate default risk."
                }
                val pGuideBody = Paragraph(riskRating, fBody)
                guideBox.addElement(pGuideBody)
                scoreContainer.addCell(guideBox)
                document.add(scoreContainer)

                // 4. CORE SCORING PILLARS
                val breakdownTitle = Paragraph("3. CORE CREDIT PILLARS BREAKDOWN", fHeader)
                breakdownTitle.setSpacingAfter(6f)
                document.add(breakdownTitle)

                val breakdownT = PdfPTable(3)
                breakdownT.widthPercentage = 100f
                breakdownT.setWidths(floatArrayOf(2.5f, 1.2f, 3.3f))
                breakdownT.setSpacingAfter(15f)

                fun addTh(hText: String) {
                    val cell = PdfPCell(Phrase(hText, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, white)))
                    cell.backgroundColor = primaryColor
                    cell.setPadding(6f)
                    cell.border = Rectangle.BOX
                    cell.borderColor = BaseColor.WHITE
                    breakdownT.addCell(cell)
                }

                addTh("Credit Pillar")
                addTh("Score / Rating")
                addTh("Underwriter Evaluation & Comments")

                fun addBreakdownRow(pillar: String, score: Int, desc: String) {
                    val rating = when {
                        score >= 80 -> "Excellent"
                        score >= 60 -> "Good"
                        score >= 40 -> "Average"
                        else -> "Critical"
                    }
                    val cPillar = PdfPCell(Phrase(pillar, fBodyBold))
                    cPillar.setPadding(6f)
                    breakdownT.addCell(cPillar)

                    val rColor = when {
                        score >= 80 -> accentGreen
                        score >= 60 -> accentAmber
                        else -> accentRed
                    }
                    val cScore = PdfPCell(Phrase("$score/100 ($rating)", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, rColor)))
                    cScore.setPadding(6f)
                    breakdownT.addCell(cScore)

                    val cDesc = PdfPCell(Phrase(desc, fBody))
                    cDesc.setPadding(6f)
                    breakdownT.addCell(cDesc)
                }

                addBreakdownRow("Cash Flow Health (20%)", scoreData.cashFlowScore, "Evaluates current liquid cash and bank reserves against monthly operating purchase obligations.")
                addBreakdownRow("Collection Efficiency (20%)", scoreData.collectionScore, "Speed of customer collection. Calculated as receipt entries vs total sales invoices.")
                addBreakdownRow("Profit Margin Stability (20%)", scoreData.profitMarginScore, "Gross margins across operations. Healthy margins represent resilience to market fluctuations.")
                addBreakdownRow("Debt-to-Income Discipline (15%)", scoreData.debtRatioScore, "Outstanding payables relative to sales capacity. Lower ratios indicate a clean debt profile.")
                addBreakdownRow("Payment Reliability (10%)", scoreData.paymentDisciplineScore, "History of clearing payables on-time. Fewer overdue supplier bills translates to a higher score.")
                addBreakdownRow("Audit Compliance (15%)", scoreData.auditScore, "Pre-audit verification status of ledger bookkeeping, tax allocation, and compliance rules.")
                document.add(breakdownT)

                // Page 1 Footer Note
                val page1Footer = Paragraph("\nPage 1 of 3 | Teller Business Intelligence Report.", FontFactory.getFont(FontFactory.HELVETICA, 8f, BaseColor.GRAY))
                page1Footer.alignment = Element.ALIGN_CENTER
                document.add(page1Footer)

                // ── PAGE 2: UNDERWRITING RATIOS & FINANCIAL SOLVENCY ──────────
                document.newPage()

                // Header for Page 2
                val headerTable2 = PdfPTable(1)
                headerTable2.widthPercentage = 100f
                val headerCell2 = PdfPCell()
                headerCell2.backgroundColor = BaseColor(98, 0, 238) // Accent Color Header
                headerCell2.setPadding(10f)
                headerCell2.border = Rectangle.BOX
                headerCell2.borderColor = primaryColor
                
                headerCell2.addElement(Paragraph("TELLER LOAN READINESS ANALYSIS - PAGE 2", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f, white)).apply { alignment = Element.ALIGN_CENTER })
                headerTable2.addCell(headerCell2)
                document.add(headerTable2)
                document.add(Chunk.NEWLINE)

                val ratioTitle = Paragraph("4. BANK UNDERWRITING RATIOS", fHeader)
                ratioTitle.setSpacingAfter(6f)
                document.add(ratioTitle)

                // Ratios table
                val ratioT = PdfPTable(4)
                ratioT.widthPercentage = 100f
                ratioT.setWidths(floatArrayOf(2f, 1.2f, 1.2f, 2.6f))
                ratioT.setSpacingAfter(15f)

                fun addRatioHeader(text: String) {
                    val cell = PdfPCell(Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, white)))
                    cell.backgroundColor = primaryColor
                    cell.setPadding(6f)
                    ratioT.addCell(cell)
                }

                addRatioHeader("Underwriting Ratio")
                addRatioHeader("Actual Value")
                addRatioHeader("Bank Benchmark")
                addRatioHeader("Lender Underwriter Status")

                fun addRatioRow(ratioName: String, actualVal: String, benchmark: String, status: String, pass: Boolean) {
                    ratioT.addCell(PdfPCell(Phrase(ratioName, fBodyBold)).apply { setPadding(6f) })
                    ratioT.addCell(PdfPCell(Phrase(actualVal, fBody)).apply { setPadding(6f) })
                    ratioT.addCell(PdfPCell(Phrase(benchmark, fBody)).apply { setPadding(6f) })
                    
                    val color = if (pass) accentGreen else accentRed
                    ratioT.addCell(PdfPCell(Phrase(status, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, color))).apply { setPadding(6f) })
                }

                addRatioRow(
                    "Current Ratio (Liquidity)", 
                    String.format("%.2f", scoreData.currentRatio), 
                    "> 1.33 : 1", 
                    if (scoreData.currentRatio >= 1.33) "COMPLIANT (Strong)" else "MONITOR (Tight liquidity)", 
                    scoreData.currentRatio >= 1.33
                )
                
                addRatioRow(
                    "Quick Ratio (Acid Test)", 
                    String.format("%.2f", scoreData.quickRatio), 
                    "> 1.00 : 1", 
                    if (scoreData.quickRatio >= 1.00) "PASSED (Excellent cash flow)" else "WARNING (High debt ratio)", 
                    scoreData.quickRatio >= 1.00
                )
                
                addRatioRow(
                    "Debt Service Coverage (DSCR)", 
                    String.format("%.2f", scoreData.dscr), 
                    "> 1.50 : 1", 
                    if (scoreData.dscr >= 1.50) "PASSED (Serviceable debt capacity)" else "MARGINAL (Verify collections)", 
                    scoreData.dscr >= 1.50
                )
                
                addRatioRow(
                    "Debt-to-Revenue Ratio", 
                    String.format("%.2f", scoreData.debtToRevenueRatio), 
                    "< 0.30 : 1", 
                    if (scoreData.debtToRevenueRatio <= 0.30) "COMPLIANT (Clean debt ratio)" else "REVIEW (Overleveraged suppliers)", 
                    scoreData.debtToRevenueRatio <= 0.30
                )
                
                addRatioRow(
                    "Collection Efficiency", 
                    String.format("%.1f%%", scoreData.collectionEfficiency), 
                    "> 85.0%", 
                    if (scoreData.collectionEfficiency >= 85.0) "COMPLIANT (Discipline collection)" else "REVIEW (Late receivables)", 
                    scoreData.collectionEfficiency >= 85.0
                )
                
                addRatioRow(
                    "Avg. Collection Period", 
                    "${scoreData.avgCollectionDays} Days", 
                    "< 45 Days", 
                    if (scoreData.avgCollectionDays <= 45) "COMPLIANT (Optimal cycle)" else "HIGH RISK (Late debtor clearings)", 
                    scoreData.avgCollectionDays <= 45
                )

                document.add(ratioT)

                // Financial checklist
                val checklistTitle = Paragraph("5. DETAILED CAPITAL STATEMENT SUMMARY", fHeader)
                checklistTitle.setSpacingAfter(6f)
                document.add(checklistTitle)

                val checklistT = PdfPTable(2)
                checklistT.widthPercentage = 100f
                checklistT.setWidths(floatArrayOf(2.5f, 1.5f))
                checklistT.setSpacingAfter(15f)

                fun addCheckRow(lbl: String, value: String) {
                    checklistT.addCell(PdfPCell(Phrase(lbl, fBody)).apply { setPadding(6f) })
                    checklistT.addCell(PdfPCell(Phrase(value, fBodyBold)).apply { 
                        setPadding(6f)
                        horizontalAlignment = Element.ALIGN_RIGHT 
                    })
                }

                val curSymbol = company?.currencySymbol ?: "INR"
                addCheckRow("Gross Sales Revenue (All-Time)", "$curSymbol ${String.format(Locale.US, "%,.2f", scoreData.totalSalesAllTime)}")
                addCheckRow("Gross Purchase Obligations (All-Time)", "$curSymbol ${String.format(Locale.US, "%,.2f", scoreData.totalPurchaseAllTime)}")
                addCheckRow("Net Cash & Bank Holdings", "$curSymbol ${String.format(Locale.US, "%,.2f", scoreData.cashInHand + scoreData.bankBalance)}")
                addCheckRow("Sundry Debtors (Total Receivables)", "$curSymbol ${String.format(Locale.US, "%,.2f", scoreData.totalReceivables)}")
                addCheckRow("Sundry Creditors (Total Payables)", "$curSymbol ${String.format(Locale.US, "%,.2f", scoreData.totalPayables)}")
                addCheckRow("Working Capital Position (Net Assets - Liabilities)", "$curSymbol ${String.format(Locale.US, "%,.2f", scoreData.workingCapitalGap)}")
                document.add(checklistT)

                // Page 2 Footer Note
                val page2Footer = Paragraph("\nPage 2 of 3 | Teller Business Intelligence Report.", FontFactory.getFont(FontFactory.HELVETICA, 8f, BaseColor.GRAY))
                page2Footer.alignment = Element.ALIGN_CENTER
                document.add(page2Footer)

                // ── PAGE 3: COMPLIANCE ATTESATION & SIGN-OFF ───────────────────
                document.newPage()

                // Header for Page 3
                val headerTable3 = PdfPTable(1)
                headerTable3.widthPercentage = 100f
                val headerCell3 = PdfPCell()
                headerCell3.backgroundColor = primaryColor
                headerCell3.setPadding(10f)
                headerCell3.border = Rectangle.BOX
                headerCell3.borderColor = secondaryColor
                
                headerCell3.addElement(Paragraph("TELLER LOAN READINESS ANALYSIS - PAGE 3", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f, white)).apply { alignment = Element.ALIGN_CENTER })
                headerTable3.addCell(headerCell3)
                document.add(headerTable3)
                document.add(Chunk.NEWLINE)

                val auditTitle = Paragraph("6. COMPLIANCE & AI CA PRE-AUDIT VERIFICATION", fHeader)
                auditTitle.setSpacingAfter(6f)
                document.add(auditTitle)

                val auditT = PdfPTable(3)
                auditT.widthPercentage = 100f
                auditT.setWidths(floatArrayOf(2.5f, 1f, 3.5f))
                auditT.setSpacingAfter(15f)

                fun addAuditHeader(text: String) {
                    val cell = PdfPCell(Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, white)))
                    cell.backgroundColor = primaryColor
                    cell.setPadding(6f)
                    auditT.addCell(cell)
                }

                addAuditHeader("Compliance Verification Check")
                addAuditHeader("Status")
                addAuditHeader("Audit Findings / Explanation")

                fun addAuditRow(checkName: String, status: String, pass: Boolean, desc: String) {
                    auditT.addCell(PdfPCell(Phrase(checkName, fBodyBold)).apply { setPadding(6f) })
                    val color = if (pass) accentGreen else accentRed
                    auditT.addCell(PdfPCell(Phrase(status, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, color))).apply { 
                        setPadding(6f)
                        horizontalAlignment = Element.ALIGN_CENTER
                    })
                    auditT.addCell(PdfPCell(Phrase(desc, fBody)).apply { setPadding(6f) })
                }

                addAuditRow(
                    "Negative Cash Evaluation", 
                    if (scoreData.negativeCashDeductions == 0) "PASSED" else "WARNING", 
                    scoreData.negativeCashDeductions == 0,
                    if (scoreData.negativeCashDeductions == 0) "Ledgers verified. No negative cash balances detected on hand." else "Negative cash balances detected. Inconsistent payment entries present."
                )
                
                addAuditRow(
                    "Section 40A(3) Compliance Check", 
                    if (scoreData.cashLimitDisallowances == 0) "COMPLIANT" else "WARNING", 
                    scoreData.cashLimitDisallowances == 0,
                    if (scoreData.cashLimitDisallowances == 0) "No single-day cash payments exceeded the statutory INR 10,000 limit." else "Found ${scoreData.cashLimitDisallowances} payment(s) exceeding INR 10,000 in Cash (Disallowed under IT Act)."
                )
                
                addAuditRow(
                    "GST Tax Ledger Integrity", 
                    if (scoreData.missingTaxInvoicesCount == 0) "PASSED" else "WARNING", 
                    scoreData.missingTaxInvoicesCount == 0,
                    if (scoreData.missingTaxInvoicesCount == 0) "GST Tax allocations are mapped correctly on all sales transactions." else "${scoreData.missingTaxInvoicesCount} sales voucher(s) missing tax ledger postings."
                )
                
                addAuditRow(
                    "Double Entry Ledger Balance", 
                    if (scoreData.unbalancedVouchersCount == 0) "BALANCED" else "WARNING", 
                    scoreData.unbalancedVouchersCount == 0,
                    if (scoreData.unbalancedVouchersCount == 0) "All journal and invoice postings are correctly balanced (Debits = Credits)." else "${scoreData.unbalancedVouchersCount} voucher(s) with unbalanced debits/credits."
                )

                addAuditRow(
                    "Narration Completion Ratio", 
                    if (scoreData.missingNarrationCount <= 3) "PASSED" else "REVIEW", 
                    scoreData.missingNarrationCount <= 3,
                    if (scoreData.missingNarrationCount == 0) "All vouchers contain mandatory narrations for audit trails." else "${scoreData.missingNarrationCount} vouchers missing descriptive audit explanations."
                )

                document.add(auditT)

                // Recommendation box
                val loanRecTitle = Paragraph("7. CREDIT ELIGIBILITY RECOMMENDATION (LOAN PITCH)", fHeader)
                loanRecTitle.setSpacingAfter(6f)
                document.add(loanRecTitle)

                val loanRecT = PdfPTable(2)
                loanRecT.widthPercentage = 100f
                loanRecT.setWidths(floatArrayOf(1.8f, 2.2f))
                loanRecT.setSpacingAfter(15f)

                val capEligibilityVal = Math.min(5000000.0, Math.max(100000.0, scoreData.totalSalesAllTime * 0.20))
                val capEligibilityStr = "$curSymbol ${String.format(Locale.US, "%,.2f", capEligibilityVal)}"

                val cellLeft = PdfPCell().apply {
                    backgroundColor = lightGray
                    setPadding(10f)
                    border = Rectangle.BOX
                    borderColor = BaseColor.LIGHT_GRAY
                }
                cellLeft.addElement(Paragraph("RECOMMENDED CREDIT LIMIT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, primaryColor)))
                cellLeft.addElement(Paragraph(capEligibilityStr, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f, secondaryColor)))
                cellLeft.addElement(Chunk.NEWLINE)
                cellLeft.addElement(Paragraph("UNDERWRITING RISK CAT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, primaryColor)))
                
                val riskCatStr = when {
                    scoreData.overallScore >= 800 -> "CLASS-A (Low risk)"
                    scoreData.overallScore >= 650 -> "CLASS-B (Moderate risk)"
                    else -> "CLASS-C (Review required)"
                }
                cellLeft.addElement(Paragraph(riskCatStr, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, scoreColor)))
                loanRecT.addCell(cellLeft)

                val cellRight = PdfPCell().apply {
                    setPadding(10f)
                    border = Rectangle.BOX
                    borderColor = BaseColor.LIGHT_GRAY
                }
                cellRight.addElement(Paragraph("UNDERWRITING NOTES FOR LOAN OFFICER", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, primaryColor)))
                val pitchDesc = when {
                    scoreData.overallScore >= 800 -> "The business demonstrates outstanding credit discipline, solid margins, and a reliable collection pattern. Recommended for unsecured MSME working capital limits or overdraft facilities with quick approvals."
                    scoreData.overallScore >= 650 -> "The business displays good operational health. Cash runway is stable and collections are compliant. Recommended for standard business loans or structured invoice discounting."
                    else -> "The business shows high payables exposure or collection delays. Lender is advised to request collateral backing, secondary personal guarantees, or verify debtors ledgers before credit sanction."
                }
                cellRight.addElement(Paragraph(pitchDesc, fBody))
                loanRecT.addCell(cellRight)
                
                document.add(loanRecT)

                // Signatures area
                val sigStampTable = PdfPTable(2)
                sigStampTable.widthPercentage = 100f
                sigStampTable.setWidths(floatArrayOf(1f, 1f))
                sigStampTable.setSpacingBefore(10f)
                
                val sigCell = PdfPCell()
                sigCell.border = Rectangle.NO_BORDER
                sigCell.horizontalAlignment = Element.ALIGN_CENTER
                sigCell.verticalAlignment = Element.ALIGN_MIDDLE
                val sigImg = getSafeScaledImage(company?.signatureUri, 120f, 60f)
                if (sigImg != null) {
                    sigImg.alignment = Element.ALIGN_CENTER
                    sigCell.addElement(sigImg)
                } else {
                    sigCell.addElement(Paragraph("\n\n\n", fBody))
                }
                val sigLine = LineSeparator(1f, 70f, darkGray, Element.ALIGN_CENTER, -2f)
                sigCell.addElement(sigLine)
                val sigLbl = Paragraph("Authorized Corporate Signature", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, darkGray))
                sigLbl.alignment = Element.ALIGN_CENTER
                sigCell.addElement(sigLbl)
                sigStampTable.addCell(sigCell)
                
                val stampCell = PdfPCell()
                stampCell.border = Rectangle.NO_BORDER
                stampCell.horizontalAlignment = Element.ALIGN_CENTER
                stampCell.verticalAlignment = Element.ALIGN_MIDDLE
                val stampFile = company?.let { generateStampBitmap(context, it) }
                val stampImg = getSafeScaledImage(stampFile?.absolutePath, 120f, 60f)
                if (stampImg != null) {
                    stampImg.alignment = Element.ALIGN_CENTER
                    stampCell.addElement(stampImg)
                } else {
                    stampCell.addElement(Paragraph("\n\n\n", fBody))
                }
                stampCell.addElement(sigLine)
                val stampLbl = Paragraph("Official Corporate Stamp", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, darkGray))
                stampLbl.alignment = Element.ALIGN_CENTER
                stampCell.addElement(stampLbl)
                sigStampTable.addCell(stampCell)
                
                sigStampTable.setSpacingAfter(10f)
                document.add(sigStampTable)

                // Brand footer
                val pBrand = Paragraph("\nReport generated by Teller Business Intelligence Suite. Under Device Integrity Verification.", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8f, BaseColor.GRAY))
                pBrand.alignment = Element.ALIGN_CENTER
                document.add(pBrand)

                document.close()

                val saved = saveFileToDownloads(context, file, fileName, "application/pdf")
                
                withContext(Dispatchers.Main) {
                    PdfNavigator.open(file.absolutePath, "Financial Health Passport")
                    if (saved) {
                        Toast.makeText(context, "Financial Health Passport saved & opened", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun zipFiles(zipFile: File, files: Map<String, File>) {
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zipOut ->
            files.forEach { (path, file) ->
                if (file.exists() && file.isFile) {
                    val entry = java.util.zip.ZipEntry(path)
                    zipOut.putNextEntry(entry)
                    java.io.FileInputStream(file).use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
            }
        }
    }

    fun writeCaPackExcel(file: File, sheetsData: Map<String, Pair<List<String>, List<List<String>>>>) {
        val workbook = SXSSFWorkbook()
        try {
            val headerStyle = workbook.createCellStyle().apply {
                val headerFont = workbook.createFont().apply {
                    bold = true
                    fontHeightInPoints = 11
                }
                setFont(headerFont)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
            }

            val dataStyle = workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.LEFT
            }

            val numericStyle = workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.RIGHT
                dataFormat = workbook.createDataFormat().getFormat("0.00")
            }

            sheetsData.forEach { (sheetName, pair) ->
                val headers = pair.first
                val data = pair.second
                val sheet = workbook.createSheet(sheetName)

                // Create header row
                val headerRow = sheet.createRow(0)
                headers.forEachIndexed { index, header ->
                    val cell = headerRow.createCell(index)
                    cell.setCellValue(header)
                    cell.cellStyle = headerStyle
                }

                // Create data rows
                data.forEachIndexed { rowIndex, rowData ->
                    val row = sheet.createRow(rowIndex + 1)
                    rowData.forEachIndexed { cellIndex, cellData ->
                        val cell = row.createCell(cellIndex)
                        val isNumericColumn = headers.getOrNull(cellIndex)?.lowercase()?.let {
                            it.contains("amount") || it.contains("rate") || it.contains("qty") || it.contains("quantity") || it.contains("debit") || it.contains("credit") || it.contains("balance")
                        } ?: false

                        try {
                            if (isNumericColumn && cellData.isNotEmpty()) {
                                val numValue = cellData.toDoubleOrNull()
                                if (numValue != null) {
                                    cell.setCellValue(numValue)
                                    cell.cellStyle = numericStyle
                                } else {
                                    cell.setCellValue(cellData)
                                    cell.cellStyle = dataStyle
                                }
                            } else {
                                cell.setCellValue(cellData)
                                cell.cellStyle = dataStyle
                            }
                        } catch (e: Exception) {
                            cell.setCellValue(cellData)
                            cell.cellStyle = dataStyle
                        }
                    }
                }

                // Auto-size columns
                (0 until headers.size).forEach { i ->
                    try {
                        sheet.autoSizeColumn(i)
                    } catch (t: Throwable) {
                        sheet.setColumnWidth(i, 5000)
                    }
                }
            }

            java.io.FileOutputStream(file).use { workbook.write(it) }
            workbook.close()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun exportCaCompliancePack(
        context: Context,
        company: Company,
        fyPeriod: String,
        trialBalance: List<Triple<String, Double, Double>>,
        pnlRows: List<List<String>>,
        balanceSheetRows: List<List<String>>,
        auditScore: Int,
        preChecks: List<Pair<String, String>>,
        onComplete: (File) -> kotlin.Unit
    ) {
        val rootDir = context.getExternalFilesDir(null) ?: return
        val tempFolder = File(rootDir, "temp_ca_pack_${System.currentTimeMillis()}").apply { mkdirs() }
        
        val excelFile = File(tempFolder, "Teller_CA_Pack_Books.xlsx")
        val tbHeaders = listOf("Ledger Name", "Debit Amount", "Credit Amount")
        val tbData = trialBalance.map { listOf(it.first, String.format("%.2f", it.second), String.format("%.2f", it.third)) }
        
        val pnlHeaders = listOf("Particulars", "Amount")
        val bsHeaders = listOf("Liabilities & Capital", "Assets", "Amount (Liab)", "Amount (Asset)")
        
        val sheetsData = mapOf(
            "Trial Balance" to Pair(tbHeaders, tbData),
            "Profit & Loss" to Pair(pnlHeaders, pnlRows),
            "Balance Sheet" to Pair(bsHeaders, balanceSheetRows)
        )
        writeCaPackExcel(excelFile, sheetsData)
        
        val pdfFile = File(tempFolder, "CA_Compliance_Summary.pdf")
        val document = Document(PageSize.A4)
        try {
            val writer = PdfWriter.getInstance(document, FileOutputStream(pdfFile))
            document.open()
            
            val fTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f, BaseColor(30, 21, 62))
            val fSub = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, BaseColor.DARK_GRAY)
            val fBody = FontFactory.getFont(FontFactory.HELVETICA, 10f, BaseColor.BLACK)
            val fBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, BaseColor.BLACK)
            
            document.add(Paragraph("Teller CA Compliance & Audit Brief", fTitle))
            document.add(Paragraph("Company: ${company.name} | Period: $fyPeriod", fSub))
            document.add(Paragraph("GSTIN: ${company.gstin ?: "N/A"} | Date Generated: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}", fBody))
            document.add(Chunk.NEWLINE)
            
            document.add(Paragraph("Pre-Audit & Scrutiny Score: $auditScore / 1000", fSub))
            document.add(Chunk.NEWLINE)
            
            val checkTable = PdfPTable(2)
            checkTable.widthPercentage = 100f
            checkTable.setWidths(floatArrayOf(2f, 3f))
            
            val headerBg = BaseColor(30, 21, 62)
            val fHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, BaseColor.WHITE)
            
            fun addCheckHeader(text: String) {
                val cell = PdfPCell(Phrase(text, fHeader)).apply {
                    backgroundColor = headerBg
                    paddingTop = 8f
                    paddingBottom = 8f
                    paddingLeft = 8f
                    paddingRight = 8f
                    border = Rectangle.BOX
                    borderColor = BaseColor.LIGHT_GRAY
                }
                checkTable.addCell(cell)
            }
            
            addCheckHeader("Audit Check Point")
            addCheckHeader("Status / Observation Details")
            
            preChecks.forEach { (label, status) ->
                val labelCell = PdfPCell(Phrase(label, fBold)).apply {
                    paddingTop = 8f
                    paddingBottom = 8f
                    paddingLeft = 8f
                    paddingRight = 8f
                    border = Rectangle.BOX
                    borderColor = BaseColor.LIGHT_GRAY
                }
                
                val isPass = status.lowercase().contains("pass:")
                val statusFont = if (isPass) {
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, BaseColor(46, 125, 50)) // Deep Green
                } else {
                    FontFactory.getFont(FontFactory.HELVETICA, 10f, BaseColor(198, 40, 40)) // Deep Red
                }
                
                val statusCell = PdfPCell(Phrase(status, statusFont)).apply {
                    paddingTop = 8f
                    paddingBottom = 8f
                    paddingLeft = 8f
                    paddingRight = 8f
                    border = Rectangle.BOX
                    borderColor = BaseColor.LIGHT_GRAY
                }
                
                checkTable.addCell(labelCell)
                checkTable.addCell(statusCell)
            }
            document.add(checkTable)
            
            document.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val zipFileName = "Teller_CA_Pack_${company.name.replace(" ", "_")}_${System.currentTimeMillis()}.zip"
        val zipFile = File(rootDir, zipFileName)
        
        val filesToZip = mapOf(
            "Teller_CA_Pack_Books.xlsx" to excelFile,
            "CA_Compliance_Summary.pdf" to pdfFile
        )
        zipFiles(zipFile, filesToZip)
        
        excelFile.delete()
        pdfFile.delete()
        tempFolder.delete()
        
        onComplete(zipFile)
    }

    fun exportScrutinyPack(
        context: Context,
        company: Company,
        fyPeriod: String,
        vouchers: List<VoucherWithAllEntries>,
        evidenceAttachments: List<EvidenceAttachment>,
        auditLogs: List<com.minhas.teller.data.entities.AuditLog>,
        onComplete: (File) -> kotlin.Unit
    ) {
        val rootDir = context.getExternalFilesDir(null) ?: return
        val tempFolder = File(rootDir, "temp_scrutiny_${System.currentTimeMillis()}").apply { mkdirs() }
        val invoicesFolder = File(tempFolder, "Invoices").apply { mkdirs() }
        
        val auditFile = File(tempFolder, "AuditTrails.csv")
        try {
            java.io.PrintWriter(FileOutputStream(auditFile)).use { writer ->
                writer.println("Timestamp,Action,EntityType,EntityId,Details")
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                auditLogs.forEach { log ->
                    writer.println("\"${sdf.format(log.timestamp)}\",\"${log.action}\",\"${log.entityType}\",\"${log.entityId}\",\"${log.details.replace("\"", "\"\"")}\"")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val filesToZip = mutableMapOf<String, File>()
        filesToZip["AuditTrails.csv"] = auditFile
        
        val attachedFilesCopy = mutableListOf<File>()
        evidenceAttachments.forEachIndexed { index, attachment ->
            val srcFile = File(attachment.fileUri)
            if (srcFile.exists()) {
                val extension = srcFile.extension.ifEmpty { "jpg" }
                val destName = "Invoices/Vch_${attachment.voucherId}_Ev_${index + 1}.$extension"
                val destFile = File(tempFolder, destName)
                try {
                    srcFile.copyTo(destFile, overwrite = true)
                    filesToZip[destName] = destFile
                    attachedFilesCopy.add(destFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        val pdfFile = File(tempFolder, "ScrutinyIndex.pdf")
        val document = Document(PageSize.A4)
        try {
            val writer = PdfWriter.getInstance(document, FileOutputStream(pdfFile))
            document.open()
            
            val fTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f, BaseColor(30, 21, 62))
            val fSub = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, BaseColor.DARK_GRAY)
            val fBody = FontFactory.getFont(FontFactory.HELVETICA, 10f, BaseColor.BLACK)
            val fBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, BaseColor.BLACK)
            
            document.add(Paragraph("Teller Scrutiny Evidence Index", fTitle))
            document.add(Paragraph("Company: ${company.name} | Period: $fyPeriod", fSub))
            document.add(Paragraph("Generated on: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}", fBody))
            document.add(Chunk.NEWLINE)
            
            val table = PdfPTable(5)
            table.widthPercentage = 100f
            table.setWidths(floatArrayOf(1.5f, 1.5f, 2f, 2f, 1f))
            
            val headerBg = BaseColor(30, 21, 62)
            val fHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, BaseColor.WHITE)
            
            fun addHeaderCell(text: String) {
                val cell = PdfPCell(Phrase(text, fHeader)).apply {
                    backgroundColor = headerBg
                    paddingTop = 8f
                    paddingBottom = 8f
                    paddingLeft = 6f
                    paddingRight = 6f
                    horizontalAlignment = if (text == "Ledger Party" || text == "Voucher Type") Element.ALIGN_LEFT else Element.ALIGN_CENTER
                    border = Rectangle.BOX
                    borderColor = BaseColor.LIGHT_GRAY
                }
                table.addCell(cell)
            }
            
            addHeaderCell("Date")
            addHeaderCell("Vch No")
            addHeaderCell("Voucher Type")
            addHeaderCell("Ledger Party")
            addHeaderCell("Evidence Count")
            
            fun addDataCell(text: String, align: Int = Element.ALIGN_CENTER) {
                val cell = PdfPCell(Phrase(text, fBody)).apply {
                    paddingTop = 6f
                    paddingBottom = 6f
                    paddingLeft = 6f
                    paddingRight = 6f
                    horizontalAlignment = align
                    border = Rectangle.BOX
                    borderColor = BaseColor.LIGHT_GRAY
                }
                table.addCell(cell)
            }

            val df = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            vouchers.forEach { vwa ->
                val evidenceCount = evidenceAttachments.count { it.voucherId == vwa.voucher.id }
                val partyName = vwa.voucher.narration ?: ""
                addDataCell(df.format(vwa.voucher.date))
                addDataCell(vwa.voucher.voucherNumber ?: "")
                addDataCell(vwa.voucher.type, Element.ALIGN_LEFT)
                addDataCell(vwa.voucher.narration ?: vwa.voucher.type, Element.ALIGN_LEFT)
                addDataCell(evidenceCount.toString())
            }

            document.add(table)
            document.close()
        } catch (e: Exception) {
            e.printStackTrace()
            try { document.close() } catch (_: Exception) {}
        }

        val scrutinyFilesToZip = mutableMapOf<String, File>()
        scrutinyFilesToZip["ScrutinyIndex.pdf"] = pdfFile
        attachedFilesCopy.forEach { f -> scrutinyFilesToZip["Invoices/${f.name}"] = f }
        filesToZip.forEach { (k, v) -> scrutinyFilesToZip[k] = v }

        val zipFileName2 = "Teller_Scrutiny_${company.name.replace(" ", "_")}_${System.currentTimeMillis()}.zip"
        val zipFile2 = File(context.getExternalFilesDir(null) ?: return, zipFileName2)
        zipFiles(zipFile2, scrutinyFilesToZip)

        try { pdfFile.delete() } catch (_: Exception) {}
        try { tempFolder.deleteRecursively() } catch (_: Exception) {}

        onComplete(zipFile2)
    }
}
