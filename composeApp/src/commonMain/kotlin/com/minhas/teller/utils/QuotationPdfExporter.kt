package com.minhas.teller.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.Quotation
import com.lowagie.text.*
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import com.lowagie.text.pdf.draw.LineSeparator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

object QuotationPdfExporter {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun generateQuotationPdf(
        context: Context,
        quotation: com.minhas.teller.data.entities.Quotation,
        company: Company? = null,
        share: Boolean = true
    ) {
        val fileName = "Quotation_${quotation.quotationNumber}.pdf"
        val file = File(context.getExternalFilesDir(null), fileName)

        Toast.makeText(context, "Generating PDF...", Toast.LENGTH_SHORT).show()

        scope.launch {
            try {
                val doc = Document(PageSize.A4, 36f, 36f, 36f, 36f)
                PdfWriter.getInstance(doc, FileOutputStream(file))
                doc.open()

                val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val GREEN   = BaseColor(46, 125, 50)
                val DKGREEN = BaseColor(27, 94, 32)
                val LTGREEN = BaseColor(200, 230, 201)
                val GRAY    = BaseColor(96, 96, 96)
                val LTGRAY  = BaseColor(245, 245, 245)
                val BLACK   = BaseColor.BLACK
                val WHITE   = BaseColor.WHITE

                // Fonts
                val fB16  = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  16f, WHITE)
                val fB14  = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  14f, WHITE)
                val fB12  = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  12f, BLACK)
                val fB11  = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  11f, BLACK)
                val fB10W = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  10f, WHITE)
                val fR11  = FontFactory.getFont(FontFactory.HELVETICA,       11f, BLACK)
                val fR10  = FontFactory.getFont(FontFactory.HELVETICA,       10f, BLACK)
                val fR9G  = FontFactory.getFont(FontFactory.HELVETICA,        9f, GRAY)
                val fB11G = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  11f, DKGREEN)
                val fI10  = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE,10f, GRAY)

                fun cell(text: String, font: com.lowagie.text.Font,
                         align: Int = Element.ALIGN_LEFT,
                         bg: BaseColor = WHITE,
                         pad: Float = 6f,
                         colspan: Int = 1,
                         border: Int = com.lowagie.text.Rectangle.NO_BORDER): PdfPCell {
                    val c = PdfPCell(Phrase(text, font))
                    c.horizontalAlignment = align
                    c.verticalAlignment   = Element.ALIGN_MIDDLE
                    c.backgroundColor     = bg
                    c.setPadding(pad)
                    c.colspan             = colspan
                    c.border              = border
                    return c
                }

                // ── HEADER BANNER ─────────────────────────────────────────────
                val header = PdfPTable(2)
                header.widthPercentage = 100f
                header.setWidths(floatArrayOf(3f, 1.4f))

                val countryName = DashboardPrefs.getTaxCountry(context)
                val isIndia = countryName.equals("India", true)
                val taxConfig = TaxConfigManager.getConfigForCountry(countryName)
                val taxLabel = taxConfig.taxName
                val taxIdLabel = taxConfig.taxIdName
                val taxCodeLabel = taxConfig.codeSystemName ?: "Tax Code"
                val currencySymbol = company?.currencySymbol ?: "₹"

                // Left: company name + tagline
                val companyName = company?.name ?: "Your Company"
                val companyAddr = buildString {
                    company?.address?.let { append(it); append("\n") }
                    company?.state?.let { append(it) }
                    company?.pincode?.let { append(" - $it") }
                    if (isNotEmpty()) append("\n")
                    company?.phoneNumber?.let { append("Ph: $it") }
                    company?.email?.let { if (isNotEmpty()) append("  |  "); append(it) }
                    if (isNotEmpty()) append("\n")
                    company?.gstin?.let { append("$taxIdLabel: $it") }
                }

                val leftCell = PdfPCell()
                leftCell.backgroundColor = GREEN
                leftCell.setPadding(12f)
                leftCell.border = com.lowagie.text.Rectangle.NO_BORDER
                leftCell.addElement(Paragraph(companyName, fB16))
                if (companyAddr.isNotBlank()) {
                    leftCell.addElement(Paragraph(companyAddr,
                        FontFactory.getFont(FontFactory.HELVETICA, 9f, BaseColor(220, 237, 200))))
                }
                header.addCell(leftCell)

                // Right: QUOTATION label + number + date
                val rightCell = PdfPCell()
                rightCell.backgroundColor = DKGREEN
                rightCell.setPadding(12f)
                rightCell.border = com.lowagie.text.Rectangle.NO_BORDER
                rightCell.horizontalAlignment = Element.ALIGN_RIGHT
                rightCell.addElement(Paragraph("QUOTATION", fB14).apply { alignment = Element.ALIGN_RIGHT })
                rightCell.addElement(Paragraph("# ${quotation.quotationNumber}",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, BaseColor(178, 255, 178))).apply {
                    alignment = Element.ALIGN_RIGHT
                })
                rightCell.addElement(Paragraph(dateFmt.format(quotation.quotationDate),
                    FontFactory.getFont(FontFactory.HELVETICA, 9f, BaseColor(200, 230, 201))).apply {
                    alignment = Element.ALIGN_RIGHT
                })
                quotation.expiryDate?.let {
                    rightCell.addElement(Paragraph("Valid till: ${dateFmt.format(it)}",
                        FontFactory.getFont(FontFactory.HELVETICA, 9f, BaseColor(255, 204, 128))).apply {
                        alignment = Element.ALIGN_RIGHT
                    })
                }
                header.addCell(rightCell)
                doc.add(header)

                doc.add(Chunk.NEWLINE)

                // ── BILL TO / SHIP TO BOX ─────────────────────────────────────
                val addrTable = PdfPTable(1)
                addrTable.widthPercentage = 55f
                addrTable.horizontalAlignment = Element.ALIGN_LEFT

                val addrHeaderCell = cell("BILL TO", fB10W, Element.ALIGN_LEFT, GREEN, 5f)
                addrTable.addCell(addrHeaderCell)

                val addrBodyCell = PdfPCell()
                addrBodyCell.backgroundColor = LTGRAY
                addrBodyCell.setPadding(8f)
                addrBodyCell.border = com.lowagie.text.Rectangle.BOX
                addrBodyCell.borderColor = LTGREEN
                val nameP = Paragraph(quotation.customerName, fB11)
                addrBodyCell.addElement(nameP)
                quotation.customerPhone?.let {
                    addrBodyCell.addElement(Paragraph("Phone: $it", fR10))
                }
                quotation.customerAddress?.let {
                    addrBodyCell.addElement(Paragraph(it, fR10))
                }
                addrTable.addCell(addrBodyCell)
                doc.add(addrTable)

                doc.add(Chunk.NEWLINE)

                // ── ITEMS TABLE ───────────────────────────────────────────────
                val hasHSN = quotation.items.any { !it.hsnCode.isNullOrBlank() }
                val hasGST = quotation.items.any { it.gstRate > 0 }
                val colCount = when {
                    hasHSN && hasGST -> 7
                    hasHSN || hasGST -> 6
                    else -> 5
                }
                val itemsTable = PdfPTable(colCount)
                itemsTable.widthPercentage = 100f
                val widths = when {
                    hasHSN && hasGST -> floatArrayOf(0.5f, 3f, 1f, 1f, 1.5f, 1.2f, 1.8f)
                    hasHSN           -> floatArrayOf(0.5f, 3f, 1.2f, 1f, 1.5f, 1.8f)
                    hasGST           -> floatArrayOf(0.5f, 3f, 1f, 1.5f, 1.2f, 1.8f)
                    else             -> floatArrayOf(0.5f, 3.5f, 1f, 1.5f, 1.8f)
                }
                itemsTable.setWidths(widths)

                // Table header row
                val headers = mutableListOf("#", "Description")
                if (hasHSN) headers.add(taxCodeLabel)
                headers.add("Qty")
                headers.add("Unit")
                headers.add("Rate ($currencySymbol)")
                if (hasGST) headers.add("$taxLabel %")
                headers.add("Amount ($currencySymbol)")

                headers.forEach { h ->
                    itemsTable.addCell(cell(h, fB10W, Element.ALIGN_CENTER, GREEN, 7f))
                }

                // Item rows
                var srNo = 1
                var totalTaxableAmt = 0.0
                val gstBreakup = mutableMapOf<Double, Double>()  // rate -> tax amount

                quotation.items.forEach { item ->
                    val taxable = item.amount.paiseToRupees()
                    val taxAmt  = taxable * (item.gstRate / 100.0)
                    totalTaxableAmt += taxable
                    if (item.gstRate > 0) gstBreakup[item.gstRate] = (gstBreakup[item.gstRate] ?: 0.0) + taxAmt

                    val bg = if (srNo % 2 == 0) LTGRAY else WHITE
                    itemsTable.addCell(cell("$srNo", fR10, Element.ALIGN_CENTER, bg))

                    // Description cell (name + optional desc)
                    val descCell = PdfPCell()
                    descCell.backgroundColor = bg
                    descCell.setPadding(6f)
                    descCell.border = com.lowagie.text.Rectangle.NO_BORDER
                    descCell.addElement(Paragraph(item.itemName, fB11))
                    item.description?.let { descCell.addElement(Paragraph(it, fR9G)) }
                    itemsTable.addCell(descCell)

                    if (hasHSN) itemsTable.addCell(cell(item.hsnCode ?: "-", fR10, Element.ALIGN_CENTER, bg))
                    val qtyStr = if (item.quantity == item.quantity.toLong().toDouble())
                        "${item.quantity.toLong()}" else "%.2f".format(item.quantity)
                    itemsTable.addCell(cell(qtyStr, fR10, Element.ALIGN_CENTER, bg))
                    itemsTable.addCell(cell(item.unit, fR10, Element.ALIGN_CENTER, bg))
                    itemsTable.addCell(cell("%.2f".format(item.rate), fR10, Element.ALIGN_RIGHT, bg))
                    if (hasGST) itemsTable.addCell(cell(
                        if (item.gstRate > 0) "${item.gstRate}%" else "-", fR10, Element.ALIGN_CENTER, bg))
                    itemsTable.addCell(cell("%.2f".format(item.amount.paiseToRupees()), fB11, Element.ALIGN_RIGHT, bg))
                    srNo++
                }
                doc.add(itemsTable)

                doc.add(Chunk.NEWLINE)

                // ── TOTALS BOX (right-aligned) ────────────────────────────────
                val totalsTable = PdfPTable(2)
                totalsTable.widthPercentage = 45f
                totalsTable.horizontalAlignment = Element.ALIGN_RIGHT
                totalsTable.setWidths(floatArrayOf(1.5f, 1f))

                fun totalRow(label: String, value: String, bold: Boolean = false, highlight: Boolean = false) {
                    val lf = if (bold) fB11 else fR11
                    val vf = if (bold) fB11 else fR11
                    val bg = if (highlight) LTGREEN else WHITE
                    val lc = cell(label, lf, Element.ALIGN_LEFT, bg, 5f)
                    val vc = cell(value, vf, Element.ALIGN_RIGHT, bg, 5f)
                    lc.border = com.lowagie.text.Rectangle.TOP
                    lc.borderColor = LTGREEN
                    vc.border = com.lowagie.text.Rectangle.TOP
                    vc.borderColor = LTGREEN
                    totalsTable.addCell(lc)
                    totalsTable.addCell(vc)
                }

                totalRow("Sub Total", "$currencySymbol %.2f".format(quotation.subTotal.paiseToRupees()))

                // GST breakup lines
                gstBreakup.entries.sortedBy { it.key }.forEach { (rate, taxAmt) ->
                    if (isIndia) {
                        val cgst = taxAmt / 2
                        val sgst = taxAmt / 2
                        totalRow("CGST @ ${rate/2}%", "$currencySymbol %.2f".format(cgst))
                        totalRow("SGST @ ${rate/2}%", "$currencySymbol %.2f".format(sgst))
                    } else {
                        totalRow("$taxLabel @ $rate%", "$currencySymbol %.2f".format(taxAmt))
                    }
                }
                if (quotation.taxAmount > 0) {
                    totalRow("Total $taxLabel", "$currencySymbol %.2f".format(quotation.taxAmount.paiseToRupees()), bold = true)
                }
                if (quotation.discount > 0) {
                    totalRow("Discount", "- $currencySymbol %.2f".format(quotation.discount.paiseToRupees()))
                }

                // Grand total row — green background
                val gtLCell = PdfPCell(Phrase("GRAND TOTAL", fB12))
                gtLCell.backgroundColor = GREEN
                gtLCell.setPadding(7f)
                gtLCell.border = com.lowagie.text.Rectangle.NO_BORDER
                val gtVCell = PdfPCell(Phrase("$currencySymbol %.2f".format(quotation.grandTotal.paiseToRupees()),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, WHITE)))
                gtVCell.backgroundColor = GREEN
                gtVCell.horizontalAlignment = Element.ALIGN_RIGHT
                gtVCell.setPadding(7f)
                gtVCell.border = com.lowagie.text.Rectangle.NO_BORDER
                totalsTable.addCell(gtLCell)
                totalsTable.addCell(gtVCell)

                doc.add(totalsTable)

                // Amount in words
                doc.add(Chunk.NEWLINE)
                val amtWords = numberToWords(quotation.grandTotal / 100)
                val amtWordPara = Paragraph("Amount in words: $amtWords Only", fI10)
                doc.add(amtWordPara)

                // ── NOTES ──────────────────────────────────────────────────────
                quotation.notes?.takeIf { it.isNotBlank() }?.let {
                    doc.add(Chunk.NEWLINE)
                    doc.add(Paragraph("Notes:", fB11G))
                    doc.add(Paragraph(it, fR10))
                }

                // ── TERMS & CONDITIONS ─────────────────────────────────────────
                quotation.terms?.takeIf { it.isNotBlank() }?.let {
                    doc.add(Chunk.NEWLINE)
                    doc.add(Paragraph("Terms & Conditions:", fB11G))
                    it.split("\n").forEach { line ->
                        if (line.isNotBlank()) doc.add(Paragraph(line, fR10))
                    }
                }

                // ── SIGNATURE AREA ─────────────────────────────────────────────
                doc.add(Chunk.NEWLINE)
                doc.add(Chunk.NEWLINE)
                val sigTable = PdfPTable(2)
                sigTable.widthPercentage = 100f

                val custSigCell = PdfPCell()
                custSigCell.border = com.lowagie.text.Rectangle.TOP
                custSigCell.borderColor = GRAY
                custSigCell.setPadding(8f)
                custSigCell.addElement(Paragraph("Customer Signature", fR9G))
                custSigCell.addElement(Paragraph(" ", fR9G))
                custSigCell.addElement(Paragraph("Name: ________________________", fR9G))
                custSigCell.addElement(Paragraph("Date: _________________________", fR9G))
                sigTable.addCell(custSigCell)

                val authSigCell = PdfPCell()
                authSigCell.border = com.lowagie.text.Rectangle.TOP
                authSigCell.borderColor = GRAY
                authSigCell.setPadding(8f)
                authSigCell.horizontalAlignment = Element.ALIGN_RIGHT
                authSigCell.addElement(Paragraph("For ${company?.name ?: "Company"}", fR9G).apply {
                    alignment = Element.ALIGN_RIGHT
                })
                authSigCell.addElement(Paragraph(" ", fR9G))
                authSigCell.addElement(Paragraph(" ", fR9G))
                authSigCell.addElement(Paragraph("Authorized Signatory", fR9G).apply {
                    alignment = Element.ALIGN_RIGHT
                })
                sigTable.addCell(authSigCell)
                doc.add(sigTable)

                // ── FOOTER ─────────────────────────────────────────────────────
                doc.add(Chunk.NEWLINE)
                val footerLine = LineSeparator(1f, 100f, LTGREEN, Element.ALIGN_CENTER, 0f)
                doc.add(footerLine)
                val footerPara = Paragraph(
                    "This is a computer-generated quotation. Generated by Teller App.", fI10)
                footerPara.alignment = Element.ALIGN_CENTER
                doc.add(footerPara)

                doc.close()

                withContext(Dispatchers.Main) {
                    PdfNavigator.open(file.absolutePath, "Quotation ${quotation.quotationNumber}")
                    if (share) {
                        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Quotation ${quotation.quotationNumber}")
                            putExtra(Intent.EXTRA_TEXT, "Please find attached quotation.")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Quotation PDF"))
                    }
                    Toast.makeText(context, "Quotation PDF ready!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "PDF Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun numberToWords(n: Long): String {
        if (n == 0L) return "Zero Rupees"
        val ones = arrayOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
            "Eighteen", "Nineteen")
        val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")
        
        fun below100(num: Long): String = when {
            num < 20 -> ones[num.toInt()]
            else -> tens[(num / 10).toInt()] + if (num % 10 != 0L) " " + ones[(num % 10).toInt()] else ""
        }
        
        fun below1000(num: Long): String = when {
            num < 100 -> below100(num)
            else -> ones[(num / 100).toInt()] + " Hundred" + if (num % 100 != 0L) " and " + below100(num % 100) else ""
        }
        
        fun convert(num: Long): String {
            if (num == 0L) return ""
            return when {
                num < 1000 -> below1000(num)
                num < 100000 -> convert(num / 1000) + " Thousand" + if (num % 1000 != 0L) " " + convert(num % 1000) else ""
                num < 10000000 -> convert(num / 100000) + " Lakh" + if (num % 100000 != 0L) " " + convert(num % 100000) else ""
                else -> convert(num / 10000000) + " Crore" + if (num % 10000000 != 0L) " " + convert(num % 10000000) else ""
            }
        }
        return convert(n).trim() + " Rupees"
    }
}
