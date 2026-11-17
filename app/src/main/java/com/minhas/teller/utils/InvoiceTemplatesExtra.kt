package com.minhas.teller.utils

import android.content.Context
import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.FontFactory
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import com.lowagie.text.pdf.draw.LineSeparator
import com.minhas.teller.data.entities.BillAllocation
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Unit
import com.minhas.teller.data.entities.Voucher
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The invoice layouts added beyond the original four, kept out of [ExportUtils] only because that
 * file is already large — they are dispatched from the same `when` and use the same shared helpers
 * (`taxableValue`, `getUpiQrCodeImage`, `addInvoiceFooter`, …), so a change to those applies here too.
 *
 * Each one targets a kind of business rather than just a colour scheme:
 *  - [drawGstClassic]   the bordered Indian GST tax invoice a CA/wholesaler expects, with HSN, UOM,
 *                       per-line tax, amount in words and a declaration block.
 *  - [drawMinimalMono]  no fills at all — fastest and cheapest to print, reads well on a mono laser.
 *  - [drawRoyalIndigo]  serif headings and a colour band, for consultants and service businesses.
 *  - [drawGreenRetail]  counter-friendly: big total, prominent UPI QR, "visit again" strip.
 *  - [drawLetterhead]   leaves the top of the page empty so it prints on pre-printed stationery.
 */
internal object InvoiceTemplatesExtra {

    // ── Shared bits ──────────────────────────────────────────────────────────

    private val W = BaseColor.WHITE
    private val B = BaseColor.BLACK
    private val GREY = BaseColor(120, 120, 120)
    private val HAIRLINE = BaseColor(200, 200, 200)

    private fun df() = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private fun money(company: Company?, value: Double): String =
        "${company?.currencySymbol ?: "₹"}%,.2f".format(value)

    /** Per-invoice tax roll-up: the same line-level split the GST reports use, summed. */
    private class Totals(
        val taxable: Double,
        val cgst: Double,
        val sgst: Double,
        val igst: Double,
        val rate: Double
    ) {
        val tax: Double get() = cgst + sgst + igst
        val grand: Double get() = taxable + tax
    }

    private fun totalsOf(entries: List<InventoryEntry>): Totals {
        var taxable = 0.0
        var cgst = 0.0
        var sgst = 0.0
        var igst = 0.0
        var rate = 0.0
        entries.forEach { entry ->
            taxable += entry.taxableValue()
            if (rate == 0.0 && entry.taxAmount > 0L) rate = entry.gstRate
            val (cg, sg, ig) = GstComplianceRules.taxSplit(entry)
            cgst += cg; sgst += sg; igst += ig
        }
        return Totals(taxable, cgst, sgst, igst, rate)
    }

    private fun lineTax(entry: InventoryEntry): Double {
        val (cg, sg, ig) = GstComplianceRules.taxSplit(entry)
        return cg + sg + ig
    }

    private fun unitName(entry: InventoryEntry, stockItems: List<StockItem>, units: List<Unit>): String {
        val item = stockItems.find { it.id == entry.stockItemId } ?: return "-"
        return units.find { it.id == item.unitId }?.name ?: "-"
    }

    private fun itemName(entry: InventoryEntry, stockItems: List<StockItem>): String =
        stockItems.find { it.id == entry.stockItemId }?.name ?: "Goods"

    private fun cell(
        text: String,
        size: Float,
        bold: Boolean = false,
        color: BaseColor = B,
        align: Int = Element.ALIGN_LEFT,
        bg: BaseColor? = null,
        border: Int = Rectangle.NO_BORDER,
        borderColor: BaseColor = HAIRLINE,
        padding: Float = 5f
    ): PdfPCell {
        val font = FontFactory.getFont(
            if (bold) FontFactory.HELVETICA_BOLD else FontFactory.HELVETICA, size, color
        )
        val c = PdfPCell(Phrase(text, font))
        c.horizontalAlignment = align
        c.border = border
        c.borderColor = borderColor
        c.setPadding(padding)
        if (bg != null) c.backgroundColor = bg
        return c
    }

    private fun para(text: String, size: Float, bold: Boolean = false, color: BaseColor = B): Paragraph =
        Paragraph(
            text,
            FontFactory.getFont(if (bold) FontFactory.HELVETICA_BOLD else FontFactory.HELVETICA, size, color)
        )

    private fun serif(text: String, size: Float, bold: Boolean = false, color: BaseColor = B): Paragraph =
        Paragraph(
            text,
            FontFactory.getFont(if (bold) FontFactory.TIMES_BOLD else FontFactory.TIMES_ROMAN, size, color)
        )

    /** Company's own tax labels ("GST" in India, "VAT" elsewhere). */
    private fun taxLabels(context: Context): Triple<Boolean, String, String> {
        val country = DashboardPrefs.getTaxCountry(context)
        val isIndia = country.equals("India", true)
        val taxName = TaxConfigManager.getConfigForCountry(country).taxName
        return Triple(isIndia, taxName, if (isIndia) "IGST" else taxName)
    }

    // ── Amount in words (Indian numbering) ───────────────────────────────────

    private val ONES = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
        "Eighteen", "Nineteen"
    )
    private val TENS = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    private fun under100(n: Int): String = when {
        n < 20 -> ONES[n]
        n % 10 == 0 -> TENS[n / 10]
        else -> "${TENS[n / 10]} ${ONES[n % 10]}"
    }

    private fun under1000(n: Int): String = when {
        n < 100 -> under100(n)
        n % 100 == 0 -> "${ONES[n / 100]} Hundred"
        else -> "${ONES[n / 100]} Hundred ${under100(n % 100)}"
    }

    private fun inWords(value: Long): String {
        if (value == 0L) return "Zero"
        val parts = mutableListOf<String>()
        val crore = value / 10_000_000
        val lakh = (value / 100_000) % 100
        val thousand = (value / 1_000) % 100
        val rest = (value % 1_000).toInt()
        if (crore > 0) parts.add("${if (crore > 99) inWords(crore) else under100(crore.toInt())} Crore")
        if (lakh > 0) parts.add("${under100(lakh.toInt())} Lakh")
        if (thousand > 0) parts.add("${under100(thousand.toInt())} Thousand")
        if (rest > 0) parts.add(under1000(rest))
        return parts.joinToString(" ")
    }

    /** "Rupees One Thousand Two Hundred Fifty and Fifty Paise Only" */
    internal fun amountInWords(amount: Double, currencyWord: String = "Rupees"): String {
        val totalPaise = Math.round(amount * 100)
        val whole = totalPaise / 100
        val paise = (totalPaise % 100).toInt()
        val head = "$currencyWord ${inWords(whole)}"
        return if (paise > 0) "$head and ${under100(paise)} Paise Only" else "$head Only"
    }

    // ── 5. GST Tax Invoice (Classic) ─────────────────────────────────────────

    fun drawGstClassic(
        document: Document, writer: PdfWriter, context: Context,
        voucher: Voucher, customerLedger: Ledger?, inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>, units: List<Unit>, company: Company?,
        billAllocations: List<BillAllocation>
    ) {
        val (isIndia, taxName, igstLabel) = taxLabels(context)
        val t = totalsOf(inventoryEntries)

        // Title strip — the words a GST officer looks for first.
        val title = PdfPTable(1)
        title.widthPercentage = 100f
        title.addCell(cell("TAX INVOICE", 13f, bold = true, align = Element.ALIGN_CENTER, border = Rectangle.BOX, padding = 6f))
        document.add(title)

        // Seller block, boxed and centred like a pre-printed invoice book.
        val seller = PdfPTable(1)
        seller.widthPercentage = 100f
        val sellerCell = PdfPCell()
        sellerCell.border = Rectangle.BOX
        sellerCell.borderColor = B
        sellerCell.setPadding(8f)
        sellerCell.horizontalAlignment = Element.ALIGN_CENTER
        ExportUtils.getSafeScaledImage(company?.logoUri, 90f, 42f)?.let {
            it.alignment = Element.ALIGN_CENTER
            sellerCell.addElement(it)
        }
        sellerCell.addElement(para(company?.name ?: "Your Company", 14f, bold = true).apply { alignment = Element.ALIGN_CENTER })
        listOfNotNull(
            company?.address?.takeIf { it.isNotBlank() },
            listOfNotNull(company?.state?.takeIf { it.isNotBlank() }, company?.pincode?.takeIf { it.isNotBlank() })
                .joinToString(" - ").takeIf { it.isNotBlank() },
            listOfNotNull(
                company?.phoneNumber?.takeIf { it.isNotBlank() }?.let { "Ph: $it" },
                company?.email?.takeIf { it.isNotBlank() }
            ).joinToString("  |  ").takeIf { it.isNotBlank() },
            company?.gstin?.takeIf { it.isNotBlank() }?.let { "GSTIN: $it" }
        ).forEach {
            sellerCell.addElement(para(it, 8.5f).apply { alignment = Element.ALIGN_CENTER })
        }
        seller.addCell(sellerCell)
        document.add(seller)

        // Buyer | invoice meta, side by side.
        val meta = PdfPTable(2)
        meta.widthPercentage = 100f
        meta.setWidths(floatArrayOf(1.4f, 1f))

        val buyer = PdfPCell()
        buyer.border = Rectangle.BOX
        buyer.borderColor = B
        buyer.setPadding(8f)
        buyer.addElement(para("Billed To", 8f, color = GREY))
        buyer.addElement(para(ExportUtils.invoiceBuyerName(voucher, customerLedger), 11f, bold = true))
        customerLedger?.alias?.takeIf { it.isNotBlank() }?.let { buyer.addElement(para(it, 8.5f)) }
        customerLedger?.gstin?.takeIf { it.isNotBlank() }?.let { buyer.addElement(para("GSTIN: $it", 8.5f)) }
        ExportUtils.invoiceFinancedBy(customerLedger)?.takeIf { it.isNotBlank() }?.let {
            buyer.addElement(para("Financed by: $it", 8.5f, bold = true))
        }
        meta.addCell(buyer)

        val details = PdfPTable(2)
        details.widthPercentage = 100f
        fun detail(label: String, value: String) {
            details.addCell(cell(label, 8.5f, color = GREY))
            details.addCell(cell(value, 8.5f, bold = true, align = Element.ALIGN_RIGHT))
        }
        detail("Invoice No.", voucher.voucherNumber)
        detail("Date", df().format(voucher.date))
        detail("Place of Supply", company?.state ?: "-")
        detail("Reverse Charge", "No")
        val metaRight = PdfPCell(details)
        metaRight.border = Rectangle.BOX
        metaRight.borderColor = B
        metaRight.setPadding(6f)
        meta.addCell(metaRight)
        document.add(meta)

        // Line items. 10 narrow columns, so everything stays on one portrait page.
        val tbl = PdfPTable(10)
        tbl.widthPercentage = 100f
        tbl.setWidths(floatArrayOf(0.5f, 2.9f, 0.9f, 0.7f, 0.7f, 1.1f, 1.2f, 0.6f, 1.1f, 1.3f))
        tbl.headerRows = 1
        val headers = listOf(
            "#" to Element.ALIGN_CENTER,
            "Description" to Element.ALIGN_LEFT,
            "HSN" to Element.ALIGN_CENTER,
            "UOM" to Element.ALIGN_CENTER,
            "Qty" to Element.ALIGN_RIGHT,
            "Rate" to Element.ALIGN_RIGHT,
            "Taxable" to Element.ALIGN_RIGHT,
            "$taxName%" to Element.ALIGN_RIGHT,
            "$taxName Amt" to Element.ALIGN_RIGHT,
            "Total" to Element.ALIGN_RIGHT
        )
        headers.forEach { (h, align) ->
            tbl.addCell(cell(h, 7.5f, bold = true, align = align, bg = BaseColor(235, 235, 235), border = Rectangle.BOX, borderColor = B, padding = 4f))
        }
        inventoryEntries.forEachIndexed { idx, entry ->
            val taxable = entry.taxableValue()
            val tax = lineTax(entry)
            fun row(text: String, align: Int = Element.ALIGN_RIGHT) {
                tbl.addCell(cell(text, 7.5f, align = align, border = Rectangle.BOX, borderColor = HAIRLINE, padding = 4f))
            }
            row("${idx + 1}", Element.ALIGN_CENTER)
            row(itemName(entry, stockItems), Element.ALIGN_LEFT)
            row(entry.hsnCode ?: "-", Element.ALIGN_CENTER)
            row(unitName(entry, stockItems, units), Element.ALIGN_CENTER)
            row("%,.2f".format(entry.quantity).removeSuffix(".00"))
            row("%,.2f".format(entry.taxableRate()))
            row("%,.2f".format(taxable))
            row(fmtRate(entry.gstRate))
            row("%,.2f".format(tax))
            row("%,.2f".format(taxable + tax))
        }
        document.add(tbl)

        // Totals, with the CGST/SGST split spelled out (a single "GST" line is not compliant).
        val totals = PdfPTable(2)
        totals.widthPercentage = 50f
        totals.horizontalAlignment = Element.ALIGN_RIGHT
        totals.setWidths(floatArrayOf(1.3f, 1f))
        fun total(label: String, value: Double, bold: Boolean = false) {
            totals.addCell(cell(label, 9f, bold = bold, border = Rectangle.BOX, borderColor = HAIRLINE, padding = 4f))
            totals.addCell(cell(money(company, value), 9f, bold = bold, align = Element.ALIGN_RIGHT, border = Rectangle.BOX, borderColor = HAIRLINE, padding = 4f))
        }
        total("Taxable Value", t.taxable)
        if (t.igst > 0.0) total("$igstLabel (${fmtRate(t.rate)}%)", t.igst)
        else {
            if (t.cgst > 0.0) total("CGST (${fmtRate(t.rate / 2)}%)", t.cgst)
            if (t.sgst > 0.0) total("SGST (${fmtRate(t.rate / 2)}%)", t.sgst)
        }
        total("Grand Total", t.grand, bold = true)
        document.add(totals)

        val wordsTable = PdfPTable(1)
        wordsTable.widthPercentage = 100f
        wordsTable.setSpacingBefore(6f)
        wordsTable.addCell(
            cell(
                "Amount in words: ${amountInWords(t.grand)}",
                8.5f, bold = true, border = Rectangle.BOX, borderColor = B, padding = 6f
            )
        )
        document.add(wordsTable)

        // Declaration + signature, the two blocks a printed invoice is expected to carry.
        val foot = PdfPTable(2)
        foot.widthPercentage = 100f
        foot.setWidths(floatArrayOf(1.5f, 1f))
        foot.setSpacingBefore(4f)

        val terms = PdfPCell()
        terms.border = Rectangle.BOX
        terms.borderColor = B
        terms.setPadding(8f)
        terms.addElement(para("Declaration", 8.5f, bold = true))
        terms.addElement(
            para(
                "We declare that this invoice shows the actual price of the goods described " +
                    "and that all particulars are true and correct.",
                7.5f, color = GREY
            )
        )
        if (!company?.upiId.isNullOrBlank()) {
            terms.addElement(para("Pay by UPI: ${company?.upiId}", 8f, bold = true))
            ExportUtils.getUpiQrCodeImage(context, company, voucher, inventoryEntries)?.let {
                terms.addElement(Chunk.NEWLINE)
                terms.addElement(it)
            }
        }
        foot.addCell(terms)

        val sign = PdfPCell()
        sign.border = Rectangle.BOX
        sign.borderColor = B
        sign.setPadding(8f)
        sign.horizontalAlignment = Element.ALIGN_CENTER
        sign.addElement(para("For ${company?.name ?: "Your Company"}", 9f, bold = true).apply { alignment = Element.ALIGN_CENTER })
        ExportUtils.getSafeScaledImage(company?.signatureUri, 90f, 34f)?.let {
            it.alignment = Element.ALIGN_CENTER
            sign.addElement(it)
        }
        val stamp = company?.let { ExportUtils.generateStampBitmap(context, it) }
        ExportUtils.getSafeScaledImage(stamp?.absolutePath, 80f, 34f)?.let {
            it.alignment = Element.ALIGN_CENTER
            sign.addElement(it)
        }
        sign.addElement(para("Authorised Signatory", 8f, color = GREY).apply { alignment = Element.ALIGN_CENTER })
        foot.addCell(sign)
        document.add(foot)

        ExportUtils.addInvoiceFooter(document, context, B)
    }

    // ── 6. Minimal Mono ──────────────────────────────────────────────────────

    fun drawMinimalMono(
        document: Document, writer: PdfWriter, context: Context,
        voucher: Voucher, customerLedger: Ledger?, inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>, units: List<Unit>, company: Company?,
        billAllocations: List<BillAllocation>
    ) {
        val (_, taxName, igstLabel) = taxLabels(context)
        val t = totalsOf(inventoryEntries)

        // Deliberately no filled cells anywhere: thin rules only, so a mono laser prints it fast.
        val head = PdfPTable(2)
        head.widthPercentage = 100f
        head.setWidths(floatArrayOf(1.4f, 1f))

        val left = PdfPCell()
        left.border = Rectangle.NO_BORDER
        ExportUtils.getSafeScaledImage(company?.logoUri, 85f, 34f)?.let { left.addElement(it) }
        left.addElement(para((company?.name ?: "Your Company").uppercase(), 13f, bold = true))
        listOfNotNull(
            company?.address?.takeIf { it.isNotBlank() },
            company?.phoneNumber?.takeIf { it.isNotBlank() },
            company?.gstin?.takeIf { it.isNotBlank() }?.let { "GSTIN $it" }
        ).forEach { left.addElement(para(it, 8f, color = GREY)) }
        head.addCell(left)

        val right = PdfPCell()
        right.border = Rectangle.NO_BORDER
        right.horizontalAlignment = Element.ALIGN_RIGHT
        right.addElement(para("INVOICE", 22f, bold = true, color = BaseColor(170, 170, 170)).apply { alignment = Element.ALIGN_RIGHT })
        right.addElement(para("#${voucher.voucherNumber}", 9f, bold = true).apply { alignment = Element.ALIGN_RIGHT })
        right.addElement(para(df().format(voucher.date), 9f, color = GREY).apply { alignment = Element.ALIGN_RIGHT })
        head.addCell(right)
        document.add(head)
        document.add(LineSeparator(0.8f, 100f, B, Element.ALIGN_CENTER, -4f))

        val billTo = PdfPTable(1)
        billTo.widthPercentage = 100f
        billTo.setSpacingBefore(10f)
        val billToCell = PdfPCell()
        billToCell.border = Rectangle.NO_BORDER
        billToCell.addElement(para("BILLED TO", 7.5f, color = GREY))
        billToCell.addElement(para(ExportUtils.invoiceBuyerName(voucher, customerLedger), 11f, bold = true))
        customerLedger?.alias?.takeIf { it.isNotBlank() }?.let { billToCell.addElement(para(it, 8.5f, color = GREY)) }
        customerLedger?.gstin?.takeIf { it.isNotBlank() }?.let { billToCell.addElement(para("GSTIN $it", 8.5f, color = GREY)) }
        billTo.addCell(billToCell)
        document.add(billTo)

        val tbl = PdfPTable(6)
        tbl.widthPercentage = 100f
        tbl.setWidths(floatArrayOf(3.0f, 1f, 0.8f, 1.2f, 1.1f, 1.4f))
        tbl.setSpacingBefore(12f)
        listOf(
            "DESCRIPTION" to Element.ALIGN_LEFT,
            "HSN" to Element.ALIGN_CENTER,
            "QTY" to Element.ALIGN_RIGHT,
            "RATE" to Element.ALIGN_RIGHT,
            taxName.uppercase() to Element.ALIGN_RIGHT,
            "AMOUNT" to Element.ALIGN_RIGHT
        ).forEach { (h, align) ->
            tbl.addCell(cell(h, 7.5f, bold = true, color = GREY, align = align, border = Rectangle.BOTTOM, borderColor = B, padding = 6f))
        }
        inventoryEntries.forEach { entry ->
            val taxable = entry.taxableValue()
            val tax = lineTax(entry)
            fun row(text: String, align: Int = Element.ALIGN_RIGHT, bold: Boolean = false) {
                tbl.addCell(cell(text, 9f, bold = bold, align = align, border = Rectangle.BOTTOM, borderColor = HAIRLINE, padding = 6f))
            }
            row(itemName(entry, stockItems), Element.ALIGN_LEFT)
            row(entry.hsnCode?.takeIf { it.isNotBlank() } ?: "-", Element.ALIGN_CENTER)
            row("%,.2f".format(entry.quantity).removeSuffix(".00"))
            row("%,.2f".format(entry.taxableRate()))
            row("%,.2f".format(tax))
            row("%,.2f".format(taxable + tax), bold = true)
        }
        document.add(tbl)

        val totals = PdfPTable(2)
        totals.widthPercentage = 42f
        totals.horizontalAlignment = Element.ALIGN_RIGHT
        totals.setSpacingBefore(8f)
        fun total(label: String, value: Double, strong: Boolean = false) {
            totals.addCell(cell(label, if (strong) 10f else 9f, bold = strong, color = if (strong) B else GREY, border = if (strong) Rectangle.TOP else Rectangle.NO_BORDER, borderColor = B, padding = 4f))
            totals.addCell(cell(money(company, value), if (strong) 10f else 9f, bold = true, align = Element.ALIGN_RIGHT, border = if (strong) Rectangle.TOP else Rectangle.NO_BORDER, borderColor = B, padding = 4f))
        }
        total("Subtotal", t.taxable)
        if (t.igst > 0.0) total("$igstLabel (${fmtRate(t.rate)}%)", t.igst)
        else {
            if (t.cgst > 0.0) total("CGST (${fmtRate(t.rate / 2)}%)", t.cgst)
            if (t.sgst > 0.0) total("SGST (${fmtRate(t.rate / 2)}%)", t.sgst)
        }
        total("Total", t.grand, strong = true)
        document.add(totals)

        document.add(para(amountInWords(t.grand), 8f, color = GREY).apply { spacingBefore = 10f })

        val sig = PdfPTable(1)
        sig.widthPercentage = 100f
        sig.setSpacingBefore(18f)
        val sigCell = PdfPCell()
        sigCell.border = Rectangle.NO_BORDER
        sigCell.horizontalAlignment = Element.ALIGN_RIGHT
        ExportUtils.getSafeScaledImage(company?.signatureUri, 90f, 32f)?.let {
            it.alignment = Element.ALIGN_RIGHT
            sigCell.addElement(it)
        }
        sigCell.addElement(para("Authorised Signatory", 8f, color = GREY).apply { alignment = Element.ALIGN_RIGHT })
        sig.addCell(sigCell)
        document.add(sig)

        ExportUtils.addInvoiceFooter(document, context, B)
    }

    // ── 7. Royal Indigo ──────────────────────────────────────────────────────

    fun drawRoyalIndigo(
        document: Document, writer: PdfWriter, context: Context,
        voucher: Voucher, customerLedger: Ledger?, inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>, units: List<Unit>, company: Company?,
        billAllocations: List<BillAllocation>
    ) {
        val indigo = BaseColor(49, 46, 129)
        val gold = BaseColor(191, 155, 48)
        val tint = BaseColor(240, 240, 250)
        val (_, taxName, igstLabel) = taxLabels(context)
        val t = totalsOf(inventoryEntries)

        val header = PdfPTable(2)
        header.widthPercentage = 100f
        header.setWidths(floatArrayOf(1f, 1f))

        val brand = PdfPCell()
        brand.backgroundColor = indigo
        brand.border = Rectangle.NO_BORDER
        brand.setPadding(14f)
        ExportUtils.getSafeScaledImage(company?.logoUri, 80f, 40f)?.let { brand.addElement(it) }
        brand.addElement(serif(company?.name ?: "Your Company", 15f, bold = true, color = W))
        listOfNotNull(
            company?.address?.takeIf { it.isNotBlank() },
            company?.phoneNumber?.takeIf { it.isNotBlank() }?.let { "T  $it" },
            company?.email?.takeIf { it.isNotBlank() }?.let { "E  $it" },
            company?.gstin?.takeIf { it.isNotBlank() }?.let { "GSTIN  $it" }
        ).forEach { brand.addElement(para(it, 8f, color = BaseColor(215, 215, 235))) }
        header.addCell(brand)

        val titleCell = PdfPCell()
        titleCell.backgroundColor = tint
        titleCell.border = Rectangle.NO_BORDER
        titleCell.setPadding(14f)
        titleCell.horizontalAlignment = Element.ALIGN_RIGHT
        titleCell.addElement(serif("Invoice", 26f, bold = true, color = indigo).apply { alignment = Element.ALIGN_RIGHT })
        titleCell.addElement(para("No.  ${voucher.voucherNumber}", 9f, bold = true, color = indigo).apply { alignment = Element.ALIGN_RIGHT })
        titleCell.addElement(para("Date  ${df().format(voucher.date)}", 9f, color = GREY).apply { alignment = Element.ALIGN_RIGHT })
        titleCell.addElement(Chunk.NEWLINE)
        titleCell.addElement(para("BILLED TO", 7.5f, color = GREY).apply { alignment = Element.ALIGN_RIGHT })
        titleCell.addElement(para(ExportUtils.invoiceBuyerName(voucher, customerLedger), 11f, bold = true, color = indigo).apply { alignment = Element.ALIGN_RIGHT })
        customerLedger?.alias?.takeIf { it.isNotBlank() }?.let {
            titleCell.addElement(para(it, 8f, color = GREY).apply { alignment = Element.ALIGN_RIGHT })
        }
        customerLedger?.gstin?.takeIf { it.isNotBlank() }?.let {
            titleCell.addElement(para("GSTIN  $it", 8f, color = GREY).apply { alignment = Element.ALIGN_RIGHT })
        }
        header.addCell(titleCell)
        document.add(header)

        val goldBar = PdfPTable(1)
        goldBar.widthPercentage = 100f
        val bar = PdfPCell()
        bar.backgroundColor = gold
        bar.border = Rectangle.NO_BORDER
        bar.fixedHeight = 4f
        goldBar.addCell(bar)
        document.add(goldBar)

        // Services are usually described, not counted — description gets the room here.
        val tbl = PdfPTable(4)
        tbl.widthPercentage = 100f
        tbl.setWidths(floatArrayOf(4f, 0.9f, 1.4f, 1.6f))
        tbl.setSpacingBefore(16f)
        listOf(
            "Description" to Element.ALIGN_LEFT,
            "Qty" to Element.ALIGN_RIGHT,
            "Rate" to Element.ALIGN_RIGHT,
            "Amount" to Element.ALIGN_RIGHT
        ).forEach { (h, align) ->
            tbl.addCell(cell(h, 9f, bold = true, color = W, align = align, bg = indigo, padding = 7f))
        }
        inventoryEntries.forEachIndexed { idx, entry ->
            val bg = if (idx % 2 == 0) W else tint
            fun row(text: String, align: Int = Element.ALIGN_RIGHT) {
                tbl.addCell(cell(text, 9f, align = align, bg = bg, border = Rectangle.BOTTOM, borderColor = HAIRLINE, padding = 7f))
            }
            val hsn = entry.hsnCode?.takeIf { it.isNotBlank() }
            row(itemName(entry, stockItems) + (if (hsn != null) "  ($taxName $hsn)" else ""), Element.ALIGN_LEFT)
            row("%,.2f".format(entry.quantity).removeSuffix(".00"))
            row("%,.2f".format(entry.taxableRate()))
            row("%,.2f".format(entry.taxableValue() + lineTax(entry)))
        }
        document.add(tbl)

        val bottom = PdfPTable(2)
        bottom.widthPercentage = 100f
        bottom.setWidths(floatArrayOf(1.1f, 1f))
        bottom.setSpacingBefore(14f)

        val notes = PdfPCell()
        notes.border = Rectangle.NO_BORDER
        notes.addElement(serif("Notes & Terms", 11f, bold = true, color = indigo))
        notes.addElement(
            para(
                voucher.narration?.takeIf { it.isNotBlank() }
                    ?: "Payment due on receipt. Kindly quote the invoice number with your transfer.",
                8.5f, color = GREY
            )
        )
        if (!company?.upiId.isNullOrBlank()) {
            notes.addElement(para("UPI  ${company?.upiId}", 8.5f, bold = true, color = indigo))
            ExportUtils.getUpiQrCodeImage(context, company, voucher, inventoryEntries)?.let { notes.addElement(it) }
        }
        notes.addElement(Chunk.NEWLINE)
        notes.addElement(para(amountInWords(t.grand), 8f, color = GREY))
        bottom.addCell(notes)

        val sums = PdfPTable(2)
        sums.widthPercentage = 100f
        fun sum(label: String, value: Double, strong: Boolean = false) {
            sums.addCell(cell(label, 9f, bold = strong, color = if (strong) W else B, align = Element.ALIGN_LEFT, bg = if (strong) indigo else null, padding = 6f))
            sums.addCell(cell(money(company, value), 9f, bold = true, color = if (strong) W else B, align = Element.ALIGN_RIGHT, bg = if (strong) indigo else null, padding = 6f))
        }
        sum("Subtotal", t.taxable)
        if (t.igst > 0.0) sum("$igstLabel (${fmtRate(t.rate)}%)", t.igst)
        else {
            if (t.cgst > 0.0) sum("CGST (${fmtRate(t.rate / 2)}%)", t.cgst)
            if (t.sgst > 0.0) sum("SGST (${fmtRate(t.rate / 2)}%)", t.sgst)
        }
        sum("Total Due", t.grand, strong = true)
        val sumsCell = PdfPCell(sums)
        sumsCell.border = Rectangle.NO_BORDER
        ExportUtils.getSafeScaledImage(company?.signatureUri, 90f, 32f)?.let {
            it.alignment = Element.ALIGN_RIGHT
            sumsCell.addElement(Chunk.NEWLINE)
            sumsCell.addElement(it)
        }
        sumsCell.addElement(para("Authorised Signatory", 8f, color = GREY).apply { alignment = Element.ALIGN_RIGHT })
        bottom.addCell(sumsCell)
        document.add(bottom)

        ExportUtils.addInvoiceFooter(document, context, indigo)
    }

    // ── 8. Fresh Green Retail ────────────────────────────────────────────────

    fun drawGreenRetail(
        document: Document, writer: PdfWriter, context: Context,
        voucher: Voucher, customerLedger: Ledger?, inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>, units: List<Unit>, company: Company?,
        billAllocations: List<BillAllocation>
    ) {
        val green = BaseColor(22, 128, 85)
        val lime = BaseColor(214, 240, 225)
        val (_, taxName, igstLabel) = taxLabels(context)
        val t = totalsOf(inventoryEntries)

        val head = PdfPTable(2)
        head.widthPercentage = 100f
        head.setWidths(floatArrayOf(1.6f, 1f))
        val brand = PdfPCell()
        brand.backgroundColor = green
        brand.border = Rectangle.NO_BORDER
        brand.setPadding(12f)
        ExportUtils.getSafeScaledImage(company?.logoUri, 80f, 38f)?.let { brand.addElement(it) }
        brand.addElement(para(company?.name ?: "Your Shop", 16f, bold = true, color = W))
        listOfNotNull(
            company?.address?.takeIf { it.isNotBlank() },
            company?.phoneNumber?.takeIf { it.isNotBlank() }?.let { "Call: $it" },
            company?.gstin?.takeIf { it.isNotBlank() }?.let { "GSTIN: $it" }
        ).forEach { brand.addElement(para(it, 8f, color = BaseColor(220, 240, 230))) }
        head.addCell(brand)

        val billCell = PdfPCell()
        billCell.backgroundColor = lime
        billCell.border = Rectangle.NO_BORDER
        billCell.setPadding(12f)
        billCell.addElement(para("BILL", 24f, bold = true, color = green).apply { alignment = Element.ALIGN_RIGHT })
        billCell.addElement(para("No. ${voucher.voucherNumber}", 9f, bold = true).apply { alignment = Element.ALIGN_RIGHT })
        billCell.addElement(para(df().format(voucher.date), 9f, color = GREY).apply { alignment = Element.ALIGN_RIGHT })
        billCell.addElement(para(ExportUtils.invoiceBuyerName(voucher, customerLedger), 10f, bold = true).apply { alignment = Element.ALIGN_RIGHT })
        customerLedger?.gstin?.takeIf { it.isNotBlank() }?.let {
            billCell.addElement(para("GSTIN $it", 8f, color = GREY).apply { alignment = Element.ALIGN_RIGHT })
        }
        head.addCell(billCell)
        document.add(head)

        val tbl = PdfPTable(5)
        tbl.widthPercentage = 100f
        tbl.setWidths(floatArrayOf(3.4f, 0.8f, 0.9f, 1.2f, 1.5f))
        tbl.setSpacingBefore(14f)
        listOf(
            "Item" to Element.ALIGN_LEFT,
            "Qty" to Element.ALIGN_CENTER,
            "Unit" to Element.ALIGN_CENTER,
            "Rate" to Element.ALIGN_RIGHT,
            "Amount" to Element.ALIGN_RIGHT
        ).forEach { (h, align) ->
            tbl.addCell(cell(h, 10f, bold = true, color = W, align = align, bg = green, padding = 7f))
        }
        inventoryEntries.forEachIndexed { idx, entry ->
            val bg = if (idx % 2 == 0) W else lime
            fun row(text: String, align: Int = Element.ALIGN_RIGHT, bold: Boolean = false) {
                tbl.addCell(cell(text, 9.5f, bold = bold, align = align, bg = bg, border = Rectangle.BOTTOM, borderColor = HAIRLINE, padding = 7f))
            }
            val hsn = entry.hsnCode?.takeIf { it.isNotBlank() }
            row(itemName(entry, stockItems) + (if (hsn != null) "   [$hsn]" else ""), Element.ALIGN_LEFT)
            row("%,.2f".format(entry.quantity).removeSuffix(".00"), Element.ALIGN_CENTER)
            row(unitName(entry, stockItems, units), Element.ALIGN_CENTER)
            row("%,.2f".format(entry.taxableRate()))
            row("%,.2f".format(entry.taxableValue() + lineTax(entry)), bold = true)
        }
        document.add(tbl)

        val pay = PdfPTable(2)
        pay.widthPercentage = 100f
        pay.setWidths(floatArrayOf(1f, 1f))
        pay.setSpacingBefore(12f)

        val qrCell = PdfPCell()
        qrCell.border = Rectangle.NO_BORDER
        val qr = ExportUtils.getUpiQrCodeImage(context, company, voucher, inventoryEntries)
        if (qr != null) {
            qrCell.addElement(para("Scan & Pay", 10f, bold = true, color = green))
            qrCell.addElement(qr)
            if (!company?.upiId.isNullOrBlank()) qrCell.addElement(para(company?.upiId ?: "", 8f, color = GREY))
        } else {
            qrCell.addElement(para("Thank you for shopping with us!", 10f, bold = true, color = green))
        }
        qrCell.addElement(para(amountInWords(t.grand), 8f, color = GREY))
        pay.addCell(qrCell)

        val sums = PdfPTable(2)
        sums.widthPercentage = 100f
        fun sum(label: String, value: Double, strong: Boolean = false) {
            sums.addCell(cell(label, if (strong) 12f else 9.5f, bold = strong, color = if (strong) W else B, bg = if (strong) green else null, padding = 7f))
            sums.addCell(cell(money(company, value), if (strong) 12f else 9.5f, bold = true, color = if (strong) W else B, align = Element.ALIGN_RIGHT, bg = if (strong) green else null, padding = 7f))
        }
        sum("Items Total", t.taxable)
        if (t.igst > 0.0) sum("$igstLabel (${fmtRate(t.rate)}%)", t.igst)
        else {
            if (t.cgst > 0.0) sum("CGST (${fmtRate(t.rate / 2)}%)", t.cgst)
            if (t.sgst > 0.0) sum("SGST (${fmtRate(t.rate / 2)}%)", t.sgst)
        }
        sum("TO PAY", t.grand, strong = true)
        val sumsCell = PdfPCell(sums)
        sumsCell.border = Rectangle.NO_BORDER
        ExportUtils.getSafeScaledImage(company?.signatureUri, 90f, 30f)?.let {
            it.alignment = Element.ALIGN_RIGHT
            sumsCell.addElement(Chunk.NEWLINE)
            sumsCell.addElement(it)
            sumsCell.addElement(para("Authorised Signatory", 8f, color = GREY).apply { alignment = Element.ALIGN_RIGHT })
        }
        pay.addCell(sumsCell)
        document.add(pay)

        val strip = PdfPTable(1)
        strip.widthPercentage = 100f
        strip.setSpacingBefore(10f)
        strip.addCell(
            cell("THANK YOU — PLEASE VISIT AGAIN!", 11f, bold = true, color = W, align = Element.ALIGN_CENTER, bg = green, padding = 8f)
        )
        document.add(strip)

        ExportUtils.addInvoiceFooter(document, context, green)
    }

    // ── 9. Letterhead Friendly ───────────────────────────────────────────────

    fun drawLetterhead(
        document: Document, writer: PdfWriter, context: Context,
        voucher: Voucher, customerLedger: Ledger?, inventoryEntries: List<InventoryEntry>,
        stockItems: List<StockItem>, units: List<Unit>, company: Company?,
        billAllocations: List<BillAllocation>
    ) {
        val (_, taxName, igstLabel) = taxLabels(context)
        val t = totalsOf(inventoryEntries)

        // Blank run at the top so the shop's own pre-printed header is not overprinted, and no
        // company block below it for the same reason — the paper already says who you are.
        val gap = PdfPTable(1)
        gap.widthPercentage = 100f
        val gapCell = PdfPCell(Phrase(" "))
        gapCell.border = Rectangle.NO_BORDER
        gapCell.fixedHeight = 110f
        gap.addCell(gapCell)
        document.add(gap)

        val title = PdfPTable(1)
        title.widthPercentage = 100f
        title.addCell(cell("TAX INVOICE", 12f, bold = true, align = Element.ALIGN_CENTER, border = Rectangle.BOTTOM, borderColor = B, padding = 4f))
        document.add(title)

        val meta = PdfPTable(2)
        meta.widthPercentage = 100f
        meta.setSpacingBefore(8f)
        val buyer = PdfPCell()
        buyer.border = Rectangle.NO_BORDER
        buyer.addElement(para("M/s.", 8f, color = GREY))
        buyer.addElement(para(ExportUtils.invoiceBuyerName(voucher, customerLedger), 11f, bold = true))
        customerLedger?.alias?.takeIf { it.isNotBlank() }?.let { buyer.addElement(para(it, 8.5f, color = GREY)) }
        customerLedger?.gstin?.takeIf { it.isNotBlank() }?.let { buyer.addElement(para("GSTIN: $it", 8.5f)) }
        meta.addCell(buyer)
        val right = PdfPCell()
        right.border = Rectangle.NO_BORDER
        right.addElement(para("Invoice No.  ${voucher.voucherNumber}", 9f, bold = true).apply { alignment = Element.ALIGN_RIGHT })
        right.addElement(para("Date  ${df().format(voucher.date)}", 9f).apply { alignment = Element.ALIGN_RIGHT })
        company?.state?.takeIf { it.isNotBlank() }?.let {
            right.addElement(para("Place of Supply  $it", 9f, color = GREY).apply { alignment = Element.ALIGN_RIGHT })
        }
        meta.addCell(right)
        document.add(meta)

        val tbl = PdfPTable(6)
        tbl.widthPercentage = 100f
        tbl.setWidths(floatArrayOf(0.5f, 3.4f, 1f, 0.8f, 1.2f, 1.5f))
        tbl.setSpacingBefore(10f)
        listOf(
            "#" to Element.ALIGN_CENTER,
            "Particulars" to Element.ALIGN_LEFT,
            "HSN" to Element.ALIGN_CENTER,
            "Qty" to Element.ALIGN_RIGHT,
            "Rate" to Element.ALIGN_RIGHT,
            "Amount" to Element.ALIGN_RIGHT
        ).forEach { (h, align) ->
            tbl.addCell(cell(h, 8.5f, bold = true, align = align, border = Rectangle.BOX, borderColor = B, padding = 5f))
        }
        inventoryEntries.forEachIndexed { idx, entry ->
            fun row(text: String, align: Int = Element.ALIGN_RIGHT) {
                tbl.addCell(cell(text, 8.5f, align = align, border = Rectangle.LEFT or Rectangle.RIGHT, borderColor = B, padding = 5f))
            }
            row("${idx + 1}", Element.ALIGN_CENTER)
            row(itemName(entry, stockItems), Element.ALIGN_LEFT)
            row(entry.hsnCode ?: "-", Element.ALIGN_CENTER)
            row("%,.2f".format(entry.quantity).removeSuffix(".00"))
            row("%,.2f".format(entry.taxableRate()))
            row("%,.2f".format(entry.taxableValue() + lineTax(entry)))
        }
        // Close the ruled box under the last line, the way a written invoice book does.
        val closing = PdfPCell(Phrase(" "))
        closing.colspan = 6
        closing.border = Rectangle.BOX
        closing.borderColor = B
        closing.fixedHeight = 6f
        tbl.addCell(closing)
        document.add(tbl)

        val totals = PdfPTable(2)
        totals.widthPercentage = 48f
        totals.horizontalAlignment = Element.ALIGN_RIGHT
        totals.setSpacingBefore(6f)
        fun total(label: String, value: Double, strong: Boolean = false) {
            totals.addCell(cell(label, 9f, bold = strong, border = Rectangle.BOX, borderColor = B, padding = 4f))
            totals.addCell(cell(money(company, value), 9f, bold = true, align = Element.ALIGN_RIGHT, border = Rectangle.BOX, borderColor = B, padding = 4f))
        }
        total("Taxable Value", t.taxable)
        if (t.igst > 0.0) total("$igstLabel (${fmtRate(t.rate)}%)", t.igst)
        else {
            if (t.cgst > 0.0) total("CGST (${fmtRate(t.rate / 2)}%)", t.cgst)
            if (t.sgst > 0.0) total("SGST (${fmtRate(t.rate / 2)}%)", t.sgst)
        }
        total("Grand Total", t.grand, strong = true)
        document.add(totals)

        document.add(para("Amount in words: ${amountInWords(t.grand)}", 8.5f, bold = true).apply { spacingBefore = 8f })

        val sig = PdfPTable(1)
        sig.widthPercentage = 100f
        sig.setSpacingBefore(26f)
        val sigCell = PdfPCell()
        sigCell.border = Rectangle.NO_BORDER
        sigCell.horizontalAlignment = Element.ALIGN_RIGHT
        ExportUtils.getSafeScaledImage(company?.signatureUri, 90f, 32f)?.let {
            it.alignment = Element.ALIGN_RIGHT
            sigCell.addElement(it)
        }
        sigCell.addElement(para("Authorised Signatory", 8.5f).apply { alignment = Element.ALIGN_RIGHT })
        sig.addCell(sigCell)
        document.add(sig)

        ExportUtils.addInvoiceFooter(document, context, B)
    }
}
