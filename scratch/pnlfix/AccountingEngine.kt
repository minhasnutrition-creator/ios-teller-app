package com.minhas.teller.data.accounting

import com.minhas.teller.data.entities.AccountGroup
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.GstComplianceRules
import com.minhas.teller.utils.paiseToRupees
import java.util.Date
import kotlin.math.abs

/**
 * Standard output data model for Balance Sheet reporting.
 */
data class BalanceSheetResult(
    val assets: Map<String, Double>,
    val liabilities: Map<String, Double>,
    val capital: Map<String, Double>,
    val closingStockValue: Double,
    val netProfitLoss: Double,
    val differenceInOpeningBalances: Double,
    val totalAssets: Double,
    val totalLiabilities: Double,
    val totalCapital: Double,
    val totalLiabilitiesAndCapital: Double,
    val isBalanced: Boolean
)

/**
 * Standard output data model for Profit & Loss reporting.
 */
data class ProfitAndLossResult(
    val incomes: Map<String, Double>,
    val expenses: Map<String, Double>,
    val totalIncome: Double,
    val totalExpense: Double,
    val grossProfit: Double,
    val netProfitLoss: Double
)

/**
 * Standard inventory valuation output.
 */
data class StockValuationItem(
    val stockItemId: Int,
    val name: String,
    val closingQuantity: Double,
    val averageCostRate: Double,
    val closingValue: Double
)

data class DoubleEntryValidation(
    val isValid: Boolean,
    val totalDebit: Double,
    val totalCredit: Double,
    val difference: Double
)

object AccountingEngine {

    /**
     * Validates that a voucher satisfies the fundamental double-entry invariant:
     * sum(Debits) == sum(Credits).
     */
    fun validateDoubleEntry(entries: List<VoucherEntry>, tolerance: Double = 0.01): DoubleEntryValidation {
        val debits = entries.filter { it.isDebit }.sumOf { it.amount }
        val credits = entries.filter { !it.isDebit }.sumOf { it.amount }
        val diff = abs(debits - credits)
        return DoubleEntryValidation(
            isValid = diff.paiseToRupees() <= tolerance,
            totalDebit = debits.paiseToRupees(),
            totalCredit = credits.paiseToRupees(),
            difference = diff.paiseToRupees()
        )
    }

    /**
     * Calculates moving-average cost and closing inventory valuation
     * strictly sorted by date.
     */
    fun calculateStockValuation(
        stockItems: List<StockItem>,
        vouchers: List<VoucherWithAllEntries>,
        asOfDate: Date? = null
    ): List<StockValuationItem> {
        val eligibleVouchers = if (asOfDate != null) {
            vouchers.filter { (it.voucher.date?.time ?: 0L) <= asOfDate.time }
        } else {
            vouchers
        }.sortedBy { it.voucher.date?.time ?: 0L }

        // Track Pair(Quantity, Carried Value)
        val balances = stockItems.associate {
            it.id to Pair(it.openingStock, it.openingStock * it.rate)
        }.toMutableMap()

        eligibleVouchers.forEach { vw ->
            when (vw.voucher.type) {
                // Outflows: reduce quantity and carried value at running weighted average rate
                "Sales", "Purchase Return", "Debit Note" -> {
                    vw.inventoryEntries.forEach { entry ->
                        val current = balances.getOrDefault(entry.stockItemId, Pair(0.0, 0.0))
                        val avgCost = if (current.first > 0.0) current.second / current.first else 0.0
                        val newQty = current.first - entry.quantity
                        val newValue = if (newQty > 0.0) current.second - (entry.quantity * avgCost) else 0.0
                        balances[entry.stockItemId] = Pair(newQty, newValue)
                    }
                }
                // Inflows: add quantity and add actual cost of received goods
                "Purchase", "Sales Return", "Credit Note" -> {
                    vw.inventoryEntries.forEach { entry ->
                        val current = balances.getOrDefault(entry.stockItemId, Pair(0.0, 0.0))
                        val cost = if (entry.taxableAmount > 0L) entry.taxableAmount.paiseToRupees() else entry.amount.paiseToRupees()
                        balances[entry.stockItemId] = Pair(current.first + entry.quantity, current.second + cost)
                    }
                }
                // Adjustments
                "Journal" -> {
                    vw.inventoryEntries.forEach { entry ->
                        val current = balances.getOrDefault(entry.stockItemId, Pair(0.0, 0.0))
                        if (entry.quantity > 0) {
                            balances[entry.stockItemId] = Pair(current.first + entry.quantity, current.second + entry.amount.paiseToRupees())
                        } else {
                            val avgCost = if (current.first > 0.0) current.second / current.first else entry.rate
                            val newQty = current.first + entry.quantity
                            val newValue = if (newQty > 0.0) current.second + (entry.quantity * avgCost) else 0.0
                            balances[entry.stockItemId] = Pair(newQty, newValue)
                        }
                    }
                }
            }
        }

        return stockItems.map { item ->
            val (qty, value) = balances.getOrDefault(item.id, Pair(item.openingStock, item.openingStock * item.rate))
            val avgRate = if (qty > 0.0) value / qty else item.rate
            StockValuationItem(
                stockItemId = item.id,
                name = item.name,
                closingQuantity = qty,
                averageCostRate = avgRate,
                closingValue = if (value > 0.0) value else 0.0
            )
        }
    }

    /**
     * Calculates Profit & Loss for a given set of period vouchers.
     *
     * Gross profit = Sales − Cost of goods sold; Net profit = all income − all expenses, where the
     * Purchase account is replaced by the cost of the goods actually sold (weighted-average cost at
     * the time of each sale, net of returns — see [ProfitCalculator]). Unsold goods stay in stock.
     *
     * @param allHistoricalVouchers every voucher up to the period end; cost depends on history, so
     *        pass it whenever [periodVouchers] is only part of the company's vouchers.
     */
    fun calculateProfitAndLoss(
        ledgers: List<Ledger>,
        periodVouchers: List<VoucherWithAllEntries>,
        allGroups: List<AccountGroup> = emptyList(),
        allHistoricalVouchers: List<VoucherWithAllEntries>? = null,
        stockItems: List<StockItem> = emptyList()
    ): ProfitAndLossResult {
        val roles = ProfitCalculator.classifyLedgers(ledgers, allGroups)
        val costLedger = ProfitCalculator.buildCostLedger(
            stockItems,
            allHistoricalVouchers ?: periodVouchers,
            roles.purchaseLedgerIds
        )
        val pnl = ProfitCalculator.profitAndLoss(ledgers, periodVouchers, roles, costLedger)

        return ProfitAndLossResult(
            incomes = pnl.incomesByGroup.mapValues { it.value.paiseToRupees() },
            expenses = pnl.expensesByGroup.mapValues { it.value.paiseToRupees() },
            totalIncome = pnl.totalIncomePaise.paiseToRupees(),
            totalExpense = pnl.totalExpensePaise.paiseToRupees(),
            grossProfit = pnl.grossProfitPaise.paiseToRupees(),
            netProfitLoss = pnl.netProfitPaise.paiseToRupees()
        )
    }

    /**
     * Calculates the cumulative Balance Sheet as of a specific point in time.
     * Incorporates closing stock into Assets and Net Profit/Loss into Capital/Equity.
     */
    fun calculateBalanceSheet(
        ledgers: List<Ledger>,
        vouchersUpToDate: List<VoucherWithAllEntries>,
        stockItems: List<StockItem> = emptyList(),
        allGroups: List<AccountGroup> = emptyList(),
        asOfDate: Date? = null
    ): BalanceSheetResult {
        // 1. Calculate cumulative ledger balances from inception
        val ledgerBalances = ledgers.associate { it.id to it.openingBalance }.toMutableMap()

        vouchersUpToDate.forEach { vw ->
            if (vw.voucher.type == "Quotation") return@forEach
            vw.voucherEntries.forEach { entry ->
                if (entry.isDebit) {
                    ledgerBalances[entry.ledgerId] = (ledgerBalances[entry.ledgerId] ?: 0L) + entry.amount
                } else {
                    ledgerBalances[entry.ledgerId] = (ledgerBalances[entry.ledgerId] ?: 0L) - entry.amount
                }
            }
        }

        val assets = mutableMapOf<String, Double>()
        val liabilities = mutableMapOf<String, Double>()
        val capital = mutableMapOf<String, Double>()

        // 2. Classify ledgers based on hierarchical root
        ledgers.forEach { ledger ->
            val balance = (ledgerBalances[ledger.id] ?: 0L).paiseToRupees()
            val primaryType = AccountHierarchy.classifyGroup(ledger.group, allGroups)

            when (primaryType) {
                PrimaryAccountType.ASSET -> {
                    assets[ledger.group] = (assets[ledger.group] ?: 0.0) + balance
                }
                PrimaryAccountType.LIABILITY -> {
                    // Credit balance is negative in double-entry running balance
                    liabilities[ledger.group] = (liabilities[ledger.group] ?: 0.0) + (-balance)
                }
                PrimaryAccountType.EQUITY -> {
                    capital[ledger.group] = (capital[ledger.group] ?: 0.0) + (-balance)
                }
                else -> {
                    // Incomes & Expenses roll into Net Profit on the Balance Sheet
                }
            }
        }

        // 3. Integrate Closing Stock Valuation into Assets
        val closingStockValue = if (stockItems.isNotEmpty()) {
            calculateStockValuation(stockItems, vouchersUpToDate, asOfDate).sumOf { it.closingValue }
        } else {
            0.0
        }

        if (closingStockValue > 0.0) {
            val stockGroup = "Stock-in-hand"
            // If stock-in-hand doesn't already have manual ledger postings, add real-time stock
            if ((assets[stockGroup] ?: 0.0) == 0.0) {
                assets[stockGroup] = closingStockValue
            }
        }

        // 4. Integrate Net Profit/Loss up to asOfDate into Capital/Equity
        val pnlResult = calculateProfitAndLoss(
            ledgers = ledgers,
            periodVouchers = vouchersUpToDate,
            allGroups = allGroups,
            stockItems = stockItems
        )
        val netProfitLoss = pnlResult.netProfitLoss

        if (netProfitLoss != 0.0) {
            val profitKey = if (netProfitLoss >= 0) "Profit & Loss A/c (Net Profit)" else "Profit & Loss A/c (Net Loss)"
            capital[profitKey] = (capital[profitKey] ?: 0.0) + netProfitLoss
        }

        val totalAssets = assets.values.sum()
        val totalLiabilities = liabilities.values.sum()
        val totalCapital = capital.values.sum()
        val totalLiabilitiesAndCapital = totalLiabilities + totalCapital

        // 5. Calculate Difference in Opening Balances (Suspense)
        // If opening debits != opening credits, account for opening suspense
        val openingDebits = ledgers.sumOf { if (it.openingBalance > 0) it.openingBalance else 0L }
        val openingCredits = ledgers.sumOf { if (it.openingBalance < 0) abs(it.openingBalance) else 0L }
        val openingDifference = openingDebits - openingCredits

        val difference = abs(totalAssets - totalLiabilitiesAndCapital)
        val isBalanced = difference <= 0.05

        return BalanceSheetResult(
            assets = assets,
            liabilities = liabilities,
            capital = capital,
            closingStockValue = closingStockValue,
            netProfitLoss = netProfitLoss,
            differenceInOpeningBalances = openingDifference.paiseToRupees(),
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            totalCapital = totalCapital,
            totalLiabilitiesAndCapital = totalLiabilitiesAndCapital,
            isBalanced = isBalanced
        )
    }
}
