package com.minhas.teller.utils

import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.VoucherWithAllEntries

/**
 * Deterministic GST compliance rules for the CA & scrutiny audit.
 *
 * Pure logic — no database, context or coroutines — so every rule is unit-testable and can never
 * hallucinate a violation. These are the checks a CA performs on the books that don't need
 * external return data (GSTR-2A/2B etc.): turnover thresholds (registration / composition /
 * GSTR-9C + audit) and output-vs-input tax reconciliation. A wrong threshold here means either
 * missing a mandatory registration/audit or crying wolf, so the numbers are pinned by tests.
 *
 * Thresholds are India GST (FY 2024-25 / 2025-26, notification 9/2025 rate structure):
 *  - registration: ₹40 lakh aggregate turnover (goods; most states), ₹20 lakh for special-category
 *    states and services;
 *  - composition scheme ceiling: ₹1.5 crore (goods);
 *  - GSTR-9C reconciliation + self-certified audit / e-invoicing: ₹5 crore.
 */
object GstComplianceRules {

    enum class Severity { INFO, WARNING, VIOLATION }

    data class Finding(
        val title: String,
        val severity: Severity,
        val message: String,
        val detail: String = ""
    )

    const val REGISTRATION_LIMIT_GOODS = 40_00_000.0
    const val REGISTRATION_LIMIT_SERVICES_SPECIAL = 20_00_000.0
    const val COMPOSITION_LIMIT = 1_50_00_000.0
    const val AUDIT_GSTR9C_LIMIT = 5_00_00_000.0

    /** Indian-grouped rupee string, e.g. 50000000 -> "₹5,00,00,000". */
    fun inr(value: Double): String {
        val whole = Math.round(value).toString()
        if (whole.length <= 3) return "₹$whole"
        val last3 = whole.substring(whole.length - 3)
        var rest = whole.substring(0, whole.length - 3)
        val parts = mutableListOf<String>()
        while (rest.length > 2) {
            parts.add(rest.substring(rest.length - 2))
            rest = rest.substring(0, rest.length - 2)
        }
        if (rest.isNotEmpty()) parts.add(rest)
        parts.reverse()
        return "₹${parts.joinToString(",")},$last3"
    }

    /**
     * Aggregate turnover = gross value of Sales vouchers in the period, EXCLUDING GST.
     * Indian "aggregate turnover" for the registration / composition / GSTR-9C thresholds excludes
     * CGST/SGST/IGST/cess, so we sum the tax-exclusive base (InventoryEntry.taxableAmount), not the
     * tax-inclusive totalAmount. Legacy rows that predate taxableAmount back out the tax from the
     * gross amount using gstRate (matching the GstReturnBuilder/ExportUtils convention).
     */
    fun annualTurnover(vouchers: List<VoucherWithAllEntries>): Double =
        vouchers.filter { it.voucher.type.equals("Sales", true) }.sumOf { vwe ->
            // Summed in exact paise; converted to rupees once.
            val inv = vwe.inventoryEntries.sumOf { entry ->
                when {
                    entry.taxableAmount > 0L -> entry.taxableAmount
                    entry.gstRate > 0.0 -> backOutTaxPaise(entry.amount, entry.gstRate)
                    else -> entry.amount
                }
            }
            if (inv > 0L) inv else vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
        }.paiseToRupees() - returnsValue(vouchers, "Sales Return")

    /** Output tax = GST charged on sales (sum of inventory taxAmount). */
    fun outputTax(vouchers: List<VoucherWithAllEntries>): Double =
        (vouchers.filter { it.voucher.type.equals("Sales", true) }
            .sumOf { vwe -> vwe.inventoryEntries.sumOf { it.taxAmount } } -
            vouchers.filter { it.voucher.type.equals("Sales Return", true) }
                .sumOf { vwe -> vwe.inventoryEntries.sumOf { it.taxAmount } }).paiseToRupees()

    /** Input tax = GST paid on purchases (sum of inventory taxAmount). */
    fun inputTax(vouchers: List<VoucherWithAllEntries>): Double =
        (vouchers.filter { it.voucher.type.equals("Purchase", true) }
            .sumOf { vwe -> vwe.inventoryEntries.sumOf { it.taxAmount } } -
            vouchers.filter { it.voucher.type.equals("Purchase Return", true) }
                .sumOf { vwe -> vwe.inventoryEntries.sumOf { it.taxAmount } }).paiseToRupees()

    /** Tax-exclusive value of return vouchers of [type] (they reduce turnover). */
    private fun returnsValue(vouchers: List<VoucherWithAllEntries>, type: String): Double =
        vouchers.filter { it.voucher.type.equals(type, true) }.sumOf { vwe ->
            vwe.inventoryEntries.sumOf { entry ->
                when {
                    entry.taxableAmount > 0L -> entry.taxableAmount
                    entry.gstRate > 0.0 -> backOutTaxPaise(entry.amount, entry.gstRate)
                    else -> entry.amount
                }
            }
        }.paiseToRupees()

    /**
     * True when an inventory line is an inter-state supply (IGST only, no CGST/SGST split).
     *
     * `igstRate` has historically been overloaded: several write paths store the FULL combined
     * rate there even for intra-state lines (see SmartTaxService), so `igstRate > 0` alone cannot
     * be used as the inter-state flag. The reliable discriminator is "CGST and SGST are both zero".
     */
    fun isInterState(entry: InventoryEntry): Boolean =
        entry.cgstRate == 0.0 && entry.sgstRate == 0.0 && entry.igstRate > 0.0

    /**
     * Tax-exclusive value of a line, backing the tax out of the gross amount for legacy rows that
     * predate [InventoryEntry.taxableAmount]. Matches the app-wide convention in ExportUtils and
     * [annualTurnover].
     */
    fun taxableValue(entry: InventoryEntry): Double = taxablePaise(entry).paiseToRupees()

    /** [taxableValue] in exact paise (half-up when backing tax out of a legacy gross amount). */
    fun taxablePaise(entry: InventoryEntry): Long = when {
        entry.taxableAmount > 0L -> entry.taxableAmount
        entry.gstRate > 0.0 -> backOutTaxPaise(entry.amount, entry.gstRate)
        entry.cgstRate + entry.sgstRate > 0.0 -> backOutTaxPaise(entry.amount, entry.cgstRate + entry.sgstRate)
        entry.igstRate > 0.0 -> backOutTaxPaise(entry.amount, entry.igstRate)
        else -> entry.amount
    }

    /**
     * Total tax for a line: the posted [InventoryEntry.taxAmount] when present, otherwise derived
     * from the effective rate. Crucially the effective rate is NOT cgst+sgst+igst (which double
     * counts because igstRate may hold the full rate); it is the IGST rate inter-state, or the
     * CGST+SGST rates intra-state.
     */
    fun lineTax(entry: InventoryEntry): Double = lineTaxPaise(entry).paiseToRupees()

    /** [lineTax] in exact paise. */
    fun lineTaxPaise(entry: InventoryEntry): Long {
        if (entry.taxAmount > 0L) return entry.taxAmount
        val tx = taxablePaise(entry)
        return if (isInterState(entry)) percentOfPaise(tx, entry.igstRate)
        else percentOfPaise(tx, entry.cgstRate + entry.sgstRate)
    }

    /**
     * CGST/SGST/IGST split for a line. IGST carries the whole tax inter-state; CGST+SGST split it
     * intra-state with per-head rounding that reconciles exactly to the total.
     */
    fun taxSplit(entry: InventoryEntry): Triple<Double, Double, Double> {
        val tax = lineTaxPaise(entry)
        if (isInterState(entry)) return Triple(0.0, 0.0, tax.paiseToRupees())
        // Odd paisa goes to CGST (half-up), SGST takes the remainder, so they always add up to tax.
        val cgst = if (tax >= 0) (tax + 1) / 2 else (tax - 1) / 2
        val sgst = tax - cgst
        return Triple(cgst.paiseToRupees(), sgst.paiseToRupees(), 0.0)
    }

    /**
     * Round a monetary value to paise (2 decimals), half-up. This is the canonical money-rounding
     * helper, shared by the voucher entry paths so tax/totals are stored already rounded instead of
     * as raw floating-point garbage. BigDecimal.valueOf (which uses the shortest decimal form of the
     * Double) avoids the half-paisa drift that `Math.round(v * 100)` and `String.format("%.2f")`
     * produce at binary boundaries (e.g. 2.675 -> 2.68, not 2.67).
     */
    fun round2(v: Double): Double = java.math.BigDecimal.valueOf(v)
        .setScale(2, java.math.RoundingMode.HALF_UP)
        .toDouble()

    /** Split a total tax amount into CGST and SGST halves, rounded to paise and reconciling exactly. */
    fun splitTax(totalTax: Double): Pair<Double, Double> {
        val cgst = round2(totalTax / 2.0)
        val sgst = round2(totalTax - cgst)
        return cgst to sgst
    }

    /** True when a ledger is a tax/duty ledger (matches the audit detectors' naming). */
    fun isTaxLedger(ledger: Ledger): Boolean {
        val name = ledger.name.lowercase()
        val grp = ledger.group.lowercase()
        return name.contains("gst") || name.contains("tax") || name.contains("duty") ||
            name.contains("vat") || grp.contains("duties") || grp.contains("taxes")
    }

    /**
     * True when a voucher carries tax, either as a tax-ledger posting OR as per-item tax on its
     * inventory lines. The app stores tax per inventory line (InventoryEntry.taxAmount) and only
     * *conditionally* writes a tax ledger; the audit detectors previously looked only at ledgers
     * and so flagged every item-taxed sale as "missing tax".
     */
    fun voucherHasTax(vwe: VoucherWithAllEntries, allLedgers: List<Ledger>): Boolean {
        if (vwe.voucherEntries.any { e -> allLedgers.find { it.id == e.ledgerId }?.let(::isTaxLedger) == true }) {
            return true
        }
        return vwe.inventoryEntries.any { it.taxAmount > 0L || it.gstRate > 0.0 }
    }

    /**
     * The tax amount for a voucher: the tax-ledger posting if one exists, otherwise the sum of
     * per-item tax. (Avoids double-counting when both are somehow present.)
     */
    fun voucherTaxAmount(vwe: VoucherWithAllEntries, allLedgers: List<Ledger>): Double {
        val ledgerTax = vwe.voucherEntries
            .filter { e -> allLedgers.find { it.id == e.ledgerId }?.let(::isTaxLedger) == true }
            .sumOf { it.amount.paiseToRupees() }
        if (ledgerTax > 0.01) return ledgerTax
        return vwe.inventoryEntries.sumOf { it.taxAmount.paiseToRupees() }
    }

    fun netLiability(outputTax: Double, inputTax: Double): Double = outputTax - inputTax

    fun registrationFinding(turnover: Double, hasGstin: Boolean, isSpecialState: Boolean = false): Finding? {
        if (hasGstin) return null
        val limit = if (isSpecialState) REGISTRATION_LIMIT_SERVICES_SPECIAL else REGISTRATION_LIMIT_GOODS
        return if (turnover > limit) {
            Finding(
                title = "GST Registration Required",
                severity = Severity.VIOLATION,
                message = "Turnover of ${inr(turnover)} exceeds the ${inr(limit)} registration threshold, but no GSTIN is on file.",
                detail = "Businesses whose aggregate turnover exceeds the threshold must obtain GST registration and file returns."
            )
        } else null
    }

    fun collectingTaxWithoutGstinFinding(outputTax: Double, hasGstin: Boolean): Finding? {
        if (hasGstin || outputTax <= 0.0) return null
        return Finding(
            title = "GST Collected Without Registration",
            severity = Severity.VIOLATION,
            message = "${inr(outputTax)} of output tax is being charged on sales, but no GSTIN is on file.",
            detail = "GST cannot be collected from customers without a valid registration."
        )
    }

    fun compositionFinding(turnover: Double): Finding? {
        return if (turnover > COMPOSITION_LIMIT) {
            Finding(
                title = "Composition Scheme Limit Exceeded",
                severity = Severity.WARNING,
                message = "Turnover of ${inr(turnover)} exceeds the ${inr(COMPOSITION_LIMIT)} composition-scheme ceiling.",
                detail = "If on the composition scheme, this business must migrate to the regular scheme and start charging GST."
            )
        } else null
    }

    fun auditFinding(turnover: Double): Finding? {
        return if (turnover > AUDIT_GSTR9C_LIMIT) {
            Finding(
                title = "GSTR-9C Reconciliation & Audit Required",
                severity = Severity.INFO,
                message = "Turnover of ${inr(turnover)} exceeds ${inr(AUDIT_GSTR9C_LIMIT)} — annual reconciliation (GSTR-9C) and self-certified audit apply.",
                detail = "Businesses above ₹5 crore turnover must reconcile their annual return with audited books."
            )
        } else null
    }

    /** Run all company-level GST checks in one pass. */
    fun runAll(
        turnover: Double,
        outputTax: Double,
        inputTax: Double,
        hasGstin: Boolean,
        isSpecialState: Boolean = false
    ): List<Finding> = listOfNotNull(
        registrationFinding(turnover, hasGstin, isSpecialState),
        collectingTaxWithoutGstinFinding(outputTax, hasGstin),
        compositionFinding(turnover),
        auditFinding(turnover)
    )
}
