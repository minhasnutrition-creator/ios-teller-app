package com.minhas.teller.utils

import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.VoucherWithAllEntries
import java.util.Date
import kotlin.math.abs

enum class AnomalySeverity {
    HIGH,     // Financial loss, compliance penalty, or broken books
    MEDIUM,   // Cash flow risk or delayed collection
    LOW       // Optimization suggestion
}

data class FinancialHealthAnomaly(
    val title: String,
    val description: String,
    val severity: AnomalySeverity,
    val entityReference: String? = null,
    val actionSuggestion: String
)

data class CashFlowRunwayForecast(
    val currentLiquidCash: Double,
    val expectedInflows30Days: Double,
    val expectedOutflows30Days: Double,
    val netCashPosition30Days: Double,
    val monthlyBurnRate: Double,
    val runwayMonths: Double,
    val runwayStatus: String
)

object AutonomousBookkeeper {

    private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000

    /**
     * Autonomous audit scan of financial records to catch compliance penalties,
     * bad debts, negative inventory, and broken entries.
     */
    fun scanFinancialHealth(
        vouchers: List<VoucherWithAllEntries>,
        ledgers: List<Ledger>,
        stockItems: List<StockItem>
    ): List<FinancialHealthAnomaly> {
        val anomalies = mutableListOf<FinancialHealthAnomaly>()
        val now = System.currentTimeMillis()

        // 1. Audit Overdue Debtors (> 30 days)
        val customerLedgerIds = ledgers
            .filter { it.group.contains("Debtor", ignoreCase = true) }
            .map { it.id }
            .toSet()

        val unpaidSalesVouchers = vouchers
            .filter { it.voucher.type == "Sales" }
            .filter { (now - (it.voucher.date?.time ?: now)) > THIRTY_DAYS_MILLIS }

        if (unpaidSalesVouchers.isNotEmpty()) {
            val totalOverdue = unpaidSalesVouchers.sumOf { vw ->
                vw.voucherEntries.filter { it.isDebit && it.ledgerId in customerLedgerIds }.sumOf { it.amount }
            }.paiseToRupees()
            if (totalOverdue > 0) {
                anomalies.add(
                    FinancialHealthAnomaly(
                        title = "Overdue Receivables Alert",
                        description = "₹%.2f across ${unpaidSalesVouchers.size} invoices is overdue by more than 30 days.".format(totalOverdue),
                        severity = AnomalySeverity.HIGH,
                        actionSuggestion = "Send automated WhatsApp payment reminders with UPI links."
                    )
                )
            }
        }

        // 2. Audit Negative Stock
        val stockBalances = stockItems.associate { it.id to it.openingStock }.toMutableMap()
        vouchers.sortedBy { it.voucher.date?.time ?: 0L }.forEach { vw ->
            when (vw.voucher.type) {
                "Purchase", "Sales Return" -> vw.inventoryEntries.forEach {
                    stockBalances[it.stockItemId] = (stockBalances[it.stockItemId] ?: 0.0) + it.quantity
                }
                "Sales", "Purchase Return" -> vw.inventoryEntries.forEach {
                    stockBalances[it.stockItemId] = (stockBalances[it.stockItemId] ?: 0.0) - it.quantity
                }
            }
        }

        val negativeItems = stockItems.filter { (stockBalances[it.id] ?: 0.0) < 0.0 }
        if (negativeItems.isNotEmpty()) {
            anomalies.add(
                FinancialHealthAnomaly(
                    title = "Negative Stock Detected",
                    description = "${negativeItems.size} stock items (e.g. '${negativeItems.first().name}') have negative quantities.",
                    severity = AnomalySeverity.HIGH,
                    entityReference = negativeItems.first().name,
                    actionSuggestion = "Record missing purchase vouchers or physical inventory opening stock."
                )
            )
        }

        // 3. Audit Sales Invoices Missing HSN Codes (GST Notice Risk)
        val salesWithoutHsn = vouchers
            .filter { it.voucher.type == "Sales" }
            .filter { vw -> vw.inventoryEntries.any { it.hsnCode.isNullOrBlank() } }

        if (salesWithoutHsn.isNotEmpty()) {
            anomalies.add(
                FinancialHealthAnomaly(
                    title = "Missing HSN Codes on Sales",
                    description = "${salesWithoutHsn.size} sales invoices contain items without mandatory HSN codes.",
                    severity = AnomalySeverity.MEDIUM,
                    actionSuggestion = "Run Smart Tax auto-detection to back-fill HSN codes and prevent GST audit notices."
                )
            )
        }

        // 4. Audit Expenses Spikes
        val expenseLedgerIds = ledgers
            .filter { it.group.contains("Expense", ignoreCase = true) }
            .map { it.id }
            .toSet()

        val recentExpenses = vouchers
            .filter { (now - (it.voucher.date?.time ?: now)) <= THIRTY_DAYS_MILLIS }
            .flatMap { it.voucherEntries }
            .filter { it.ledgerId in expenseLedgerIds && it.isDebit }

        val highExpense = recentExpenses.maxByOrNull { it.amount }
        if (highExpense != null && highExpense.amount.paiseToRupees() > 50000.0) {
            val ledgerName = ledgers.find { it.id == highExpense.ledgerId }?.name ?: "Expense"
            anomalies.add(
                FinancialHealthAnomaly(
                    title = "Unusual High Expense Transaction",
                    description = "A single payment of ₹%.2f was recorded under '$ledgerName'.".format(highExpense.amount.paiseToRupees()),
                    severity = AnomalySeverity.LOW,
                    entityReference = ledgerName,
                    actionSuggestion = "Verify supporting tax invoice or payment receipt attachment."
                )
            )
        }

        return anomalies
    }

    /**
     * Forecasts predictive cash flow runway over the next 30-90 days.
     */
    fun calculateCashFlowRunway(
        vouchers: List<VoucherWithAllEntries>,
        currentLiquidCash: Double
    ): CashFlowRunwayForecast {
        val now = System.currentTimeMillis()
        val ninetyDaysAgo = now - (90L * 24 * 60 * 60 * 1000)

        // 1. Calculate Average Monthly Operating Burn
        val recentOperatingPayments = vouchers
            .filter { (it.voucher.date?.time ?: 0L) >= ninetyDaysAgo }
            .filter { it.voucher.type == "Payment" }
            .flatMap { it.voucherEntries }
            .filter { it.isDebit }
            .sumOf { it.amount }.paiseToRupees()

        val monthlyBurn = (recentOperatingPayments / 3.0).coerceAtLeast(1000.0)

        // 2. Projected 30-Day Inflows from Pending Bill Allocations
        val projectedInflows = vouchers
            .filter { it.voucher.type == "Sales" }
            .filter { (now - (it.voucher.date?.time ?: now)) <= THIRTY_DAYS_MILLIS }
            .flatMap { it.voucherEntries }
            .filter { it.isDebit }
            .sumOf { it.amount.paiseToRupees() * 0.70 } // Conservative collection estimate (70%)

        val projectedOutflows = monthlyBurn

        val net30Days = currentLiquidCash + projectedInflows - projectedOutflows
        val runwayMonths = if (monthlyBurn > 0) currentLiquidCash / monthlyBurn else 12.0

        val status = when {
            runwayMonths < 1.0 -> "Critical (Less than 1 month runway)"
            runwayMonths < 3.0 -> "Caution (1-3 months runway)"
            runwayMonths < 6.0 -> "Healthy (3-6 months runway)"
            else -> "Strong (6+ months cash buffer)"
        }

        return CashFlowRunwayForecast(
            currentLiquidCash = currentLiquidCash,
            expectedInflows30Days = projectedInflows,
            expectedOutflows30Days = projectedOutflows,
            netCashPosition30Days = net30Days,
            monthlyBurnRate = monthlyBurn,
            runwayMonths = runwayMonths,
            runwayStatus = status
        )
    }
}
