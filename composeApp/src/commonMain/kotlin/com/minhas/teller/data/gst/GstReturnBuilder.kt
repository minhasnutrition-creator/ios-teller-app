package com.minhas.teller.data.gst

import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.GstComplianceRules
import com.minhas.teller.utils.GstStateCodes
import com.minhas.teller.utils.backOutTaxPaise
import com.minhas.teller.utils.percentOfPaise
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pure classification + aggregation of the books into a [GstReturn].
 *
 * No Android/context/coroutine dependencies — only JDK + entities — so it is unit-testable and can
 * later feed a GSP/ASP API exactly as it feeds the JSON writers. Holds the "what does this invoice
 * mean for GST" logic in one place.
 */
object GstReturnBuilder {

    // SimpleDateFormat is not thread-safe and this object is shared by the web server's request
    // threads, so hand out a fresh formatter per use instead of one shared instance.
    private val dateFmt: SimpleDateFormat get() = SimpleDateFormat("dd-MM-yyyy", Locale.US)

    fun buildGstReturn(
        vouchers: List<VoucherWithAllEntries>,
        ledgers: List<Ledger>,
        company: Company?,
        fromDate: Date,
        toDate: Date
    ): GstReturn {
        val ledgerById = ledgers.associateBy { it.id }
        val gstin = company?.gstin?.takeIf { it.isNotBlank() } ?: ""
        val businessStateCode = GstStateCodes.codeForStateName(company?.state)
        val fp = formatFp(fromDate)

        val warnings = mutableListOf<String>()
        if (gstin.isBlank()) warnings.add("Company GSTIN is not set — the return cannot be filed without it.")

        val outward = mutableListOf<GstInvoice>()
        val inward = mutableListOf<GstInvoice>()
        val creditNotes = mutableListOf<GstInvoice>()
        val purchaseReturns = mutableListOf<GstInvoice>()

        for (vwe in vouchers) {
            val v = vwe.voucher
            if (v.date.before(fromDate) || v.date.after(toDate)) continue
            when (v.type.lowercase()) {
                "sales" -> outward += toOutwardInvoice(vwe, ledgerById, businessStateCode, warnings)
                "purchase" -> inward += toInwardInvoice(vwe, ledgerById, businessStateCode, warnings)
                // Returns mirror their bill (Dr/Cr flipped), so the same classifiers apply with the
                // party on the other side of the entry.
                "sales return", "credit note" -> creditNotes += toOutwardInvoice(vwe, ledgerById, businessStateCode, warnings, isReturn = true)
                "purchase return", "debit note" -> purchaseReturns += toInwardInvoice(vwe, ledgerById, businessStateCode, warnings, isReturn = true)
            }
        }

        return GstReturn(
            gstin = gstin,
            fp = fp,
            businessStateCode = businessStateCode,
            outward = outward,
            inward = inward,
            warnings = warnings,
            creditNotes = creditNotes,
            purchaseReturns = purchaseReturns
        )
    }

    // ---- outward (Sales) -------------------------------------------------------------

    private fun toOutwardInvoice(
        vwe: VoucherWithAllEntries,
        ledgerById: Map<Int, Ledger>,
        businessStateCode: String?,
        warnings: MutableList<String>,
        isReturn: Boolean = false
    ): GstInvoice {
        val v = vwe.voucher
        // A sales return credits the customer, so look for the party on the credit side.
        val party = partyFor(vwe, ledgerById, sales = true, debitSide = !isReturn)
        val recipientGstin = party?.gstin?.takeIf { it.isNotBlank() }

        val pos = resolvePos(v.placeOfSupply, party?.stateCode, businessStateCode)
        val supplyType = resolveSupplyType(v.supplyType, recipientGstin, pos, businessStateCode)
        // Warn when a voucher was explicitly marked B2B but the customer has no GSTIN — it is
        // filed as B2C (unregistered), the only option that keeps GSTR-1 and GSTR-3B consistent.
        if (v.supplyType.equals(SupplyType.B2B, true) && recipientGstin.isNullOrBlank()) {
            warnings.add("${v.voucherNumber.ifBlank { "An invoice" }}: marked B2B but customer GSTIN is missing — filed as B2C")
        }
        val interState = supplyType == SupplyType.B2CL ||
            supplyType == SupplyType.EXPORT ||
            (businessStateCode != null && pos != businessStateCode)

        emitWarnings(v.voucherNumber, supplyType, recipientGstin, pos, businessStateCode, warnings)

        val lines = vwe.inventoryEntries.map { lineFor(it, interState, supplyType) }
        val total = lines.sumOf { it.taxablePaise + it.taxPaise }

        return GstInvoice(
            voucherId = v.id,
            invoiceNumber = v.voucherNumber,
            dateText = dateFmt.format(v.date),
            totalPaise = total,
            placeOfSupply = pos,
            supplyType = supplyType,
            reverseCharge = v.reverseCharge,
            recipientGstin = recipientGstin,
            recipientName = party?.name ?: v.customField,
            lines = lines
        )
    }

    // ---- inward (Purchase, for ITC) ---------------------------------------------------

    private fun toInwardInvoice(
        vwe: VoucherWithAllEntries,
        ledgerById: Map<Int, Ledger>,
        businessStateCode: String?,
        warnings: MutableList<String>,
        isReturn: Boolean = false
    ): GstInvoice {
        val v = vwe.voucher
        // A purchase return debits the supplier, so look for the party on the debit side.
        val party = partyFor(vwe, ledgerById, sales = false, debitSide = isReturn)
        val supplierGstin = party?.gstin?.takeIf { it.isNotBlank() }
        val pos = resolvePos(v.placeOfSupply, party?.stateCode, businessStateCode)
        val interState = businessStateCode != null && pos != businessStateCode

        val lines = vwe.inventoryEntries.map { lineFor(it, interState, SupplyType.B2B) }
        val total = lines.sumOf { it.taxablePaise + it.taxPaise }

        return GstInvoice(
            voucherId = v.id,
            invoiceNumber = v.voucherNumber,
            dateText = dateFmt.format(v.date),
            totalPaise = total,
            placeOfSupply = pos,
            supplyType = SupplyType.B2B, // inward always has a supplier GSTIN in the ITC model
            reverseCharge = v.reverseCharge,
            recipientGstin = supplierGstin,
            recipientName = party?.name ?: v.customField,
            lines = lines
        )
    }

    // ---- helpers ----------------------------------------------------------------------

    private fun partyFor(
        vwe: VoucherWithAllEntries,
        ledgerById: Map<Int, Ledger>,
        sales: Boolean,
        debitSide: Boolean = sales // returns flip the side the party sits on
    ): Ledger? {
        val entries = vwe.voucherEntries
        val candidates = if (debitSide) entries.filter { it.isDebit } else entries.filter { !it.isDebit }
        val groupHint = if (sales) "debtor" else "creditor"

        fun ledgerOf(e: com.minhas.teller.data.entities.VoucherEntry) = ledgerById[e.ledgerId]

        candidates.firstOrNull { e ->
            ledgerOf(e)?.group?.lowercase()?.contains(groupHint) == true
        }?.let { return ledgerOf(it) }

        return candidates.firstOrNull { e ->
            val l = ledgerOf(e) ?: return@firstOrNull false
            val g = l.group.lowercase()
            !GstComplianceRules.isTaxLedger(l) &&
                !g.contains("sales accounts") && !g.contains("purchase accounts")
        }?.let { ledgerOf(it) }
    }

    private fun resolvePos(voucherPos: String?, partyState: String?, businessState: String?): String {
        return when {
            GstStateCodes.isValidCode(voucherPos) -> voucherPos!!
            GstStateCodes.isValidCode(partyState) -> partyState!!
            businessState != null -> businessState
            else -> "00"
        }
    }

    private fun resolveSupplyType(
        explicit: String?,
        recipientGstin: String?,
        pos: String?,
        businessState: String?
    ): String {
        val exp = explicit?.uppercase()
        // An explicit B2B is only valid with a recipient GSTIN; otherwise file as B2C so the
        // invoice appears consistently in both GSTR-1 and GSTR-3B (not dropped from GSTR-1).
        if (exp != null && exp in SupplyType.all && exp != SupplyType.B2B) return exp
        return if (recipientGstin.isNullOrBlank()) {
            if (businessState == null || pos == businessState) SupplyType.B2CS else SupplyType.B2CL
        } else {
            SupplyType.B2B
        }
    }

    private fun lineFor(entry: InventoryEntry, interState: Boolean, supplyType: String?): GstInvoiceLine {
        // Nil / exempt / non-GST supplies carry no tax; their "taxable value" is the full amount.
        val nonTaxable = supplyType == SupplyType.NIL || supplyType == SupplyType.EXEMPT || supplyType == SupplyType.NONGST
        if (nonTaxable) {
            return GstInvoiceLine(
                taxablePaise = entry.amount,
                rate = 0.0,
                cgstPaise = 0L, sgstPaise = 0L, igstPaise = 0L,
                hsnCode = entry.hsnCode,
                quantity = entry.quantity
            )
        }
        val rate = when {
            entry.gstRate > 0.0 -> entry.gstRate
            entry.cgstRate + entry.sgstRate > 0.0 -> entry.cgstRate + entry.sgstRate
            else -> entry.igstRate
        }
        // `amount` is the tax-inclusive gross; back out the net base when taxableAmount wasn't
        // stored (legacy rows). Matches the app-wide convention in ExportUtils/ChatViewModel.
        // All in paise with exact half-up rounding, so the return's totals match the books to the
        // paisa (Double maths here drifted by a paisa on some invoices and summed the drift).
        val tx = when {
            entry.taxableAmount > 0L -> entry.taxableAmount
            rate > 0.0 -> backOutTaxPaise(entry.amount, rate)
            else -> entry.amount
        }
        val half = percentOfPaise(tx, rate / 2.0)
        return GstInvoiceLine(
            taxablePaise = tx,
            rate = rate,
            cgstPaise = if (interState) 0L else half,
            sgstPaise = if (interState) 0L else half,
            igstPaise = if (interState) percentOfPaise(tx, rate) else 0L,
            hsnCode = entry.hsnCode,
            quantity = entry.quantity
        )
    }

    private fun emitWarnings(
        number: String,
        supplyType: String,
        recipientGstin: String?,
        pos: String,
        businessState: String?,
        warnings: MutableList<String>
    ) {
        val label = if (number.isBlank()) "An invoice" else "Invoice $number"
        if (supplyType == SupplyType.B2B && recipientGstin.isNullOrBlank()) {
            warnings.add("$label: customer GSTIN is missing — add it on the customer's ledger")
        }
        if (pos == "00") {
            warnings.add("$label: place of supply unknown — set your State in Company Settings (else treated as inter-state)")
        }
        if (businessState == null) {
            warnings.add("$label: your company State is not set — intra/inter-state tax may be wrong. Set it in Company Settings.")
        }
    }

    private fun formatFp(date: Date): String {
        val cal = Calendar.getInstance().apply { time = date }
        return String.format(Locale.US, "%02d%04d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
    }
}
