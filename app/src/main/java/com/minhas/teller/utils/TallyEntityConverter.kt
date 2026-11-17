package com.minhas.teller.utils

import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Unit
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import java.util.Date

/**
 * Converts parsed Tally XML data (UniversalParser.TallyImportResult) into Teller application entities
 * (Ledger, Unit, StockItem, Voucher, VoucherEntry, InventoryEntry) ensuring strict double-entry balance,
 * accurate paise currency conversion, and automatic missing-master creation.
 */
object TallyEntityConverter {

    data class ConvertedVoucherBundle(
        val voucher: Voucher,
        val entries: List<VoucherEntry>,
        val inventoryEntries: List<InventoryEntry>
    )

    data class ConversionResult(
        val units: List<Unit>,
        val ledgers: List<Ledger>,
        val stockItems: List<StockItem>,
        val voucherBundles: List<ConvertedVoucherBundle>
    )

    /**
     * Determines the most appropriate parent group for a ledger if not explicitly provided by Tally.
     */
    fun inferLedgerGroup(name: String, isDebit: Boolean): String {
        val lower = name.lowercase().trim()
        return when {
            lower.contains("tax") || lower.contains("gst") || lower.contains("cgst") ||
            lower.contains("sgst") || lower.contains("igst") || lower.contains("vat") ||
            lower.contains("cess") || lower.contains("tds") || lower.contains("tcs") -> "Duties & Taxes"

            lower.contains("cash") -> "Cash-in-hand"

            lower.contains("bank") || lower.contains("hdfc") || lower.contains("sbi") ||
            lower.contains("icici") || lower.contains("axis") || lower.contains("kotak") ||
            lower.contains("pnb") || lower.contains("bob") || lower.contains("canara") -> "Bank Accounts"

            lower.contains("sales") || lower.contains("sale") -> "Sales Accounts"

            lower.contains("purchase") -> "Purchase Accounts"

            lower.contains("discount") || lower.contains("round") || lower.contains("expense") ||
            lower.contains("depreciation") || lower.contains("freight") || lower.contains("salary") ||
            lower.contains("rent") || lower.contains("wages") || lower.contains("charges") ||
            lower.contains("interest") || lower.contains("office") -> "Indirect Expenses"

            isDebit -> "Sundry Debtors"
            else -> "Sundry Creditors"
        }
    }

    /**
     * Converts a TallyImportResult into Teller application entities.
     */
    fun convert(
        result: UniversalParser.TallyImportResult,
        companyId: Int,
        existingLedgers: List<Ledger> = emptyList(),
        existingStockItems: List<StockItem> = emptyList(),
        existingUnits: List<Unit> = emptyList()
    ): ConversionResult {
        var nextUnitId = (existingUnits.maxOfOrNull { it.id } ?: 0) + 1
        var nextLedgerId = (existingLedgers.maxOfOrNull { it.id } ?: 0) + 1
        var nextStockItemId = (existingStockItems.maxOfOrNull { it.id } ?: 0) + 1
        var nextVoucherId = 1

        val allUnits = existingUnits.toMutableList()
        val allLedgers = existingLedgers.toMutableList()
        val allStockItems = existingStockItems.toMutableList()

        // 1. Create Units from StockItem master definitions
        val uniqueUnits = result.stockItems.map { it.unitName.trim() }.filter { it.isNotEmpty() }.distinct()
        for (uName in uniqueUnits) {
            if (allUnits.none { it.name.equals(uName, ignoreCase = true) }) {
                val newUnit = Unit(id = nextUnitId++, name = uName, formalName = uName, companyId = companyId)
                allUnits.add(newUnit)
            }
        }

        // 2. Create Ledgers from Master definitions
        for (pl in result.ledgers) {
            if (allLedgers.none { it.name.equals(pl.name, ignoreCase = true) }) {
                val group = pl.parentGroup?.takeIf { it.isNotBlank() } ?: "Sundry Debtors"
                val newLedger = Ledger(
                    id = nextLedgerId++,
                    name = pl.name,
                    group = group,
                    openingBalance = pl.openingBalance.rupeesToPaise(),
                    phoneNumber = pl.phoneNumber,
                    gstin = pl.gstin,
                    companyId = companyId
                )
                allLedgers.add(newLedger)
            }
        }

        // 3. Create StockItems from Master definitions
        for (psi in result.stockItems) {
            if (allStockItems.none { it.name.equals(psi.name, ignoreCase = true) }) {
                val unit = allUnits.find { it.name.equals(psi.unitName, ignoreCase = true) }
                    ?: run {
                        val newUnit = Unit(id = nextUnitId++, name = psi.unitName, formalName = psi.unitName, companyId = companyId)
                        allUnits.add(newUnit)
                        newUnit
                    }
                val rate = if (psi.openingQty != 0.0) psi.openingVal / psi.openingQty else 0.0
                val cgst = if (psi.gstRate > 0) psi.gstRate / 2.0 else 0.0
                val sgst = if (psi.gstRate > 0) psi.gstRate / 2.0 else 0.0
                val newStockItem = StockItem(
                    id = nextStockItemId++,
                    name = psi.name,
                    unitId = unit.id,
                    openingStock = psi.openingQty,
                    rate = rate,
                    companyId = companyId,
                    hsnCode = psi.hsnCode,
                    gstRate = psi.gstRate,
                    cgstRate = cgst,
                    sgstRate = sgst
                )
                allStockItems.add(newStockItem)
            }
        }

        // 4. Convert Vouchers and their Entries
        val bundles = mutableListOf<ConvertedVoucherBundle>()
        for (pv in result.vouchers) {
            val voucherId = nextVoucherId++
            val voucher = Voucher(
                id = voucherId,
                voucherNumber = pv.voucherNumber,
                date = Date(pv.date),
                type = pv.voucherType,
                refDisplayName = pv.partyName,
                narration = pv.narration,
                companyId = companyId
            )

            val vEntries = mutableListOf<VoucherEntry>()
            for (entry in pv.entries) {
                var ledger = allLedgers.find { it.name.equals(entry.ledgerName, ignoreCase = true) }
                if (ledger == null) {
                    val group = inferLedgerGroup(entry.ledgerName, entry.isDebit)
                    val newLedger = Ledger(
                        id = nextLedgerId++,
                        name = entry.ledgerName,
                        group = group,
                        openingBalance = 0L,
                        companyId = companyId
                    )
                    allLedgers.add(newLedger)
                    ledger = newLedger
                }

                vEntries.add(
                    VoucherEntry(
                        voucherId = voucherId,
                        ledgerId = ledger.id,
                        amount = entry.amount.rupeesToPaise(),
                        isDebit = entry.isDebit
                    )
                )
            }

            // Double-entry check and exact paise balancing:
            val totalDebits = vEntries.filter { it.isDebit }.sumOf { it.amount }
            val totalCredits = vEntries.filter { !it.isDebit }.sumOf { it.amount }
            val diff = totalDebits - totalCredits
            if (diff != 0L && Math.abs(diff) <= 100L) { // Small rounding discrepancy up to 1 rupee
                var roundOffLedger = allLedgers.find { it.name.equals("Round Off", ignoreCase = true) }
                if (roundOffLedger == null) {
                    val ro = Ledger(
                        id = nextLedgerId++,
                        name = "Round Off",
                        group = "Indirect Expenses",
                        openingBalance = 0L,
                        companyId = companyId
                    )
                    allLedgers.add(ro)
                    roundOffLedger = ro
                }
                if (diff > 0) {
                    // Debits > Credits -> add Credit entry
                    vEntries.add(
                        VoucherEntry(
                            voucherId = voucherId,
                            ledgerId = roundOffLedger.id,
                            amount = diff,
                            isDebit = false
                        )
                    )
                } else {
                    // Credits > Debits -> add Debit entry
                    vEntries.add(
                        VoucherEntry(
                            voucherId = voucherId,
                            ledgerId = roundOffLedger.id,
                            amount = -diff,
                            isDebit = true
                        )
                    )
                }
            }

            val invEntries = mutableListOf<InventoryEntry>()
            for (ie in pv.inventoryEntries) {
                var stockItem = allStockItems.find { it.name.equals(ie.stockItemName, ignoreCase = true) }
                if (stockItem == null) {
                    val nosUnit = allUnits.find { it.name.equals("Nos", ignoreCase = true) }
                        ?: run {
                            val u = Unit(id = nextUnitId++, name = "Nos", formalName = "Numbers", companyId = companyId)
                            allUnits.add(u)
                            u
                        }
                    val newItem = StockItem(
                        id = nextStockItemId++,
                        name = ie.stockItemName,
                        unitId = nosUnit.id,
                        openingStock = 0.0,
                        rate = ie.rate,
                        companyId = companyId,
                        hsnCode = ie.hsnCode,
                        gstRate = ie.gstRate
                    )
                    allStockItems.add(newItem)
                    stockItem = newItem
                }

                val amountPaise = ie.totalAmount.rupeesToPaise()
                invEntries.add(
                    InventoryEntry(
                        voucherId = voucherId,
                        stockItemId = stockItem.id,
                        quantity = ie.quantity,
                        rate = ie.rate,
                        amount = amountPaise,
                        hsnCode = ie.hsnCode ?: stockItem.hsnCode,
                        gstRate = ie.gstRate,
                        cgstRate = ie.gstRate / 2.0,
                        sgstRate = ie.gstRate / 2.0,
                        taxableAmount = amountPaise,
                        totalAmount = amountPaise
                    )
                )
            }

            bundles.add(ConvertedVoucherBundle(voucher, vEntries, invEntries))
        }

        return ConversionResult(
            units = allUnits,
            ledgers = allLedgers,
            stockItems = allStockItems,
            voucherBundles = bundles
        )
    }
}
