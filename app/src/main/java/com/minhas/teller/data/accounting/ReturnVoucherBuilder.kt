package com.minhas.teller.data.accounting

import com.minhas.teller.data.entities.BillAllocation
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date

/**
 * Builds a Sales Return (credit note) or Purchase Return (debit note) from the original bill.
 *
 * The return is the original voucher mirrored: every ledger line flips Dr/Cr, so the party, the
 * sales/purchase account and every GST ledger reverse exactly as they were posted, whatever ledgers
 * that particular bill used. For a partial return each line is scaled by the share of the bill's
 * value that came back, and any paisa left over by rounding is put on the biggest line so the
 * voucher always balances. Stock goes back the other way because the existing stock logic already
 * treats "Sales Return" as stock-in and "Purchase Return" as stock-out (InventoryEntryDao,
 * AccountingEngine, DashboardViewModel...).
 *
 * Pure function: no database or Android access, so it is unit-tested on the JVM.
 */
object ReturnVoucherBuilder {

    const val SALES_RETURN = "Sales Return"
    const val PURCHASE_RETURN = "Purchase Return"

    fun returnTypeFor(originalType: String): String? = when {
        originalType.equals("Sales", ignoreCase = true) -> SALES_RETURN
        originalType.equals("Purchase", ignoreCase = true) -> PURCHASE_RETURN
        else -> null
    }

    fun isReturn(type: String): Boolean =
        type.equals(SALES_RETURN, ignoreCase = true) || type.equals(PURCHASE_RETURN, ignoreCase = true)

    data class Result(
        val voucher: Voucher,
        val entries: List<VoucherEntry>,
        val inventory: List<InventoryEntry>,
        /** Keyed the way VoucherRepository.insertVoucherWithAllEntries expects (voucherEntryId = ledgerId). */
        val allocations: List<BillAllocation>,
        val totalPaise: Long
    )

    /**
     * @param original the bill being returned (type "Sales" or "Purchase").
     * @param returnQty quantity coming back per original inventory-entry id; null = the whole bill.
     * @param alreadyReturnedQty quantity already returned earlier per original inventory-entry id.
     * @param partyLedgerIds bill-wise party ledgers (debtors/creditors). Their reversed line is
     *        adjusted against the original bill, so its outstanding goes down.
     */
    fun build(
        original: VoucherWithAllEntries,
        returnQty: Map<Int, Double>?,
        voucherNumber: String,
        date: Date,
        partyLedgerIds: Set<Int>,
        alreadyReturnedQty: Map<Int, Double> = emptyMap()
    ): Result {
        val type = returnTypeFor(original.voucher.type)
            ?: throw IllegalArgumentException("Only Sales and Purchase bills can be returned")
        val inv = original.inventoryEntries

        // 1. Items coming back (clamped to what is still returnable).
        val returnedItems = mutableListOf<InventoryEntry>()
        var returnedValue = 0L
        var originalValue = 0L
        var isFull = true
        for (e in inv) {
            originalValue += e.amount
            val remaining = (e.quantity - (alreadyReturnedQty[e.id] ?: 0.0)).coerceAtLeast(0.0)
            val wanted = returnQty?.get(e.id) ?: if (returnQty == null) remaining else 0.0
            val q = wanted.coerceIn(0.0, remaining)
            if (q < e.quantity) isFull = false
            if (q <= 0.0) continue
            val f = if (e.quantity > 0.0) q / e.quantity else 1.0
            val item = InventoryEntry(
                voucherId = 0,
                stockItemId = e.stockItemId,
                quantity = q,
                rate = e.rate,
                amount = scale(e.amount, f),
                hsnCode = e.hsnCode,
                gstRate = e.gstRate,
                cgstRate = e.cgstRate,
                sgstRate = e.sgstRate,
                igstRate = e.igstRate,
                taxableAmount = scale(e.taxableAmount, f),
                taxAmount = scale(e.taxAmount, f),
                totalAmount = scale(e.totalAmount, f)
            )
            returnedItems += item
            returnedValue += item.amount
        }
        if (inv.isNotEmpty() && returnedItems.isEmpty()) {
            throw IllegalArgumentException("Nothing left to return on this bill")
        }

        // 2. Share of the bill coming back (exactly 1 for a full return).
        val ratio: BigDecimal = when {
            inv.isEmpty() || isFull || originalValue <= 0L -> BigDecimal.ONE
            else -> BigDecimal.valueOf(returnedValue).divide(BigDecimal.valueOf(originalValue), 12, RoundingMode.HALF_UP)
        }

        // 3. Mirror every ledger line: Dr <-> Cr, scaled by the ratio.
        val mirrored = original.voucherEntries.map { e ->
            VoucherEntry(
                voucherId = 0,
                ledgerId = e.ledgerId,
                amount = BigDecimal.valueOf(e.amount).multiply(ratio).setScale(0, RoundingMode.HALF_UP).toLong(),
                isDebit = !e.isDebit
            )
        }.toMutableList()

        // 4. Rounding can leave a paisa or two; settle it on the biggest line so Dr == Cr.
        val diff = mirrored.filter { it.isDebit }.sumOf { it.amount } - mirrored.filter { !it.isDebit }.sumOf { it.amount }
        if (diff != 0L && mirrored.isNotEmpty()) {
            val fixSide = diff > 0 // debits too big -> add to the largest credit line
            val idx = mirrored.indices.filter { mirrored[it].isDebit != fixSide }.maxByOrNull { mirrored[it].amount }
            if (idx != null) mirrored[idx] = mirrored[idx].copy(amount = mirrored[idx].amount + kotlin.math.abs(diff))
        }
        val entries = mirrored.filter { it.amount > 0L }
        if (entries.isEmpty()) throw IllegalArgumentException("This bill has no amounts to return")

        // 5. Reduce what the party owes / is owed on the original bill.
        val allocations = entries.filter { it.ledgerId in partyLedgerIds }.map { e ->
            BillAllocation(
                voucherEntryId = e.ledgerId, // temporary key, resolved by VoucherRepository
                refVoucherId = original.voucher.id,
                refName = original.voucher.voucherNumber,
                refType = "Against",
                amount = e.amount
            )
        }

        val o = original.voucher
        val label = if (type == SALES_RETURN) "Sale" else "Purchase"
        val voucher = Voucher(
            type = type,
            date = date,
            voucherNumber = voucherNumber,
            narration = "Return of $label #${o.voucherNumber}",
            companyId = o.companyId,
            customField = o.customField,
            refDisplayName = o.refDisplayName,
            placeOfSupply = o.placeOfSupply,
            supplyType = o.supplyType,
            reverseCharge = o.reverseCharge,
            returnOfVoucherId = o.id
        )
        return Result(voucher, entries, returnedItems, allocations, entries.filter { it.isDebit }.sumOf { it.amount })
    }

    /** Quantity already returned per original inventory-entry, from earlier return vouchers of this bill. */
    fun alreadyReturned(original: VoucherWithAllEntries, earlierReturns: List<VoucherWithAllEntries>): Map<Int, Double> {
        val pool = earlierReturns.filter { it.voucher.returnOfVoucherId == original.voucher.id && !it.voucher.isDeleted }
            .flatMap { it.inventoryEntries }
            .groupBy { it.stockItemId }
            .mapValues { (_, l) -> l.sumOf { it.quantity } }
            .toMutableMap()
        // Hand returned quantity back to the original lines of the same item, in bill order.
        val result = mutableMapOf<Int, Double>()
        for (e in original.inventoryEntries) {
            val left = pool[e.stockItemId] ?: continue
            val take = minOf(left, e.quantity)
            if (take > 0.0) {
                result[e.id] = take
                pool[e.stockItemId] = left - take
            }
        }
        return result
    }

    private fun scale(paise: Long, f: Double): Long =
        if (f >= 1.0) paise
        else BigDecimal.valueOf(paise).multiply(BigDecimal.valueOf(f)).setScale(0, RoundingMode.HALF_UP).toLong()
}
