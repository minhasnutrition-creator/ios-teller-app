package com.minhas.teller.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minhas.teller.SessionManager
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.data.repositories.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

import android.content.Context
import com.minhas.teller.utils.DashboardPrefs
import com.minhas.teller.utils.FinanceHelper
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.data.dao.StockItemDao
import androidx.compose.runtime.Immutable

@Immutable
data class HelafinNudge(
    val title: String,
    val description: String,
    val type: String
)

class DashboardViewModel(
    private val repository: VoucherRepository,
    private val ledgerRepository: LedgerRepository,
    private val stockItemDao: StockItemDao
) : ViewModel() {

    private val _totalSales = MutableStateFlow(0.0)
    val totalSales: StateFlow<Double> = _totalSales

    private val _totalPurchase = MutableStateFlow(0.0)
    val totalPurchase: StateFlow<Double> = _totalPurchase

    private val _totalReceipt = MutableStateFlow(0.0)
    val totalReceipt: StateFlow<Double> = _totalReceipt

    private val _totalPayment = MutableStateFlow(0.0)
    val totalPayment: StateFlow<Double> = _totalPayment

    // Last month comparisons
    private val _lastMonthSales = MutableStateFlow(0.0)
    val lastMonthSales: StateFlow<Double> = _lastMonthSales

    private val _lastMonthPurchase = MutableStateFlow(0.0)
    val lastMonthPurchase: StateFlow<Double> = _lastMonthPurchase

    private val _lastMonthReceipt = MutableStateFlow(0.0)
    val lastMonthReceipt: StateFlow<Double> = _lastMonthReceipt

    private val _lastMonthPayment = MutableStateFlow(0.0)
    val lastMonthPayment: StateFlow<Double> = _lastMonthPayment

    // Same month last year
    private val _lastYearSales = MutableStateFlow(0.0)
    val lastYearSales: StateFlow<Double> = _lastYearSales

    private val _lastYearPurchase = MutableStateFlow(0.0)
    val lastYearPurchase: StateFlow<Double> = _lastYearPurchase

    // Last 6 months sales trend (oldest → current), for the hero sparkline
    private val _salesTrend = MutableStateFlow<List<Float>>(emptyList())
    val salesTrend: StateFlow<List<Float>> = _salesTrend

    private val _totalReceivables = MutableStateFlow(0.0)
    val totalReceivables: StateFlow<Double> = _totalReceivables

    private val _totalPayables = MutableStateFlow(0.0)
    val totalPayables: StateFlow<Double> = _totalPayables

    private val _cashInHand = MutableStateFlow(0.0)
    val cashInHand: StateFlow<Double> = _cashInHand

    private val _bankBalance = MutableStateFlow(0.0)
    val bankBalance: StateFlow<Double> = _bankBalance

    private val _healthCardText = MutableStateFlow("Analyzing your finance health...")
    val healthCardText: StateFlow<String> = _healthCardText

    private val _helafinNudges = MutableStateFlow<List<HelafinNudge>>(emptyList())
    val helafinNudges: StateFlow<List<HelafinNudge>> = _helafinNudges

    fun loadDashboardData(companyId: Int, context: Context? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val fromDate = calendar.time
            val toDate = Calendar.getInstance().time

            // 1. Monthly Stats
            val currentVouchers = repository.getAllVouchersWithAllEntriesBetweenDates(companyId, fromDate, toDate)
            var sales = 0.0
            var purchase = 0.0
            var receipt = 0.0
            var payment = 0.0
            
            // Tax Estimation
            var estimatedTaxLiability = 0.0
            val isTaxSystemEnabled = context?.let { DashboardPrefs.isTaxSystemEnabled(it) } ?: false

            // Pre-fetch metadata for tax logic
            val ledgerMap = if (isTaxSystemEnabled) ledgerRepository.getAllLedgersSync(companyId).associate { it.id to it.name } else emptyMap()
            val itemMap = if (isTaxSystemEnabled) stockItemDao.getAllStockItems(companyId).associate { it.id to it.name } else emptyMap()

            currentVouchers.forEach { vwe ->
                val amount = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                when (vwe.voucher.type) {
                    "Sales" -> {
                        sales += amount

                        if (isTaxSystemEnabled) {
                            // Method A: Check for explicit tax ledgers
                            val explicitTax = vwe.voucherEntries.filter {
                                val name = ledgerMap[it.ledgerId]?.lowercase().orEmpty()
                                name.contains("gst") || name.contains("tax") || name.contains("duty")
                            }.sumOf { if (it.isDebit) -it.amount else it.amount }.paiseToRupees() // Credit is liability (positive for us here)

                            if (explicitTax > 0) {
                                estimatedTaxLiability += explicitTax
                            } else {
                                // Method B: AI / Heuristic Estimation
                                if (vwe.inventoryEntries.isNotEmpty()) {
                                    vwe.inventoryEntries.forEach { item ->
                                        val itemName = itemMap[item.stockItemId]
                                        val rate = FinanceHelper.guessTaxRate(context, itemName)
                                        // Item amount is inclusive or exclusive?
                                        // Usually Voucher Amount is Total. Let's assume inclusive for safety or exclusive?
                                        // Rate logic: tax = amount * rate.
                                        estimatedTaxLiability += item.amount.paiseToRupees() * (rate / 100.0)
                                    }
                                } else {
                                    // Method C: Service / Fallback
                                    // 18% of total voucher amount
                                    estimatedTaxLiability += amount * 0.18
                                }
                            }
                        }
                    }
                    "Purchase" -> purchase += amount
                    // Returns reduce the period's sales / purchases (shown net on the dashboard).
                    "Sales Return" -> sales -= amount
                    "Purchase Return" -> purchase -= amount
                    "Receipt" -> receipt += amount
                    "Payment" -> payment += amount
                }
            }

            _totalSales.value = sales
            _totalPurchase.value = purchase
            _totalReceipt.value = receipt
            _totalPayment.value = payment

            // Load last month for comparisons
            val lastMonthCal = Calendar.getInstance()
            lastMonthCal.add(Calendar.MONTH, -1)
            lastMonthCal.set(Calendar.DAY_OF_MONTH, 1)
            val lastMonthFrom = lastMonthCal.time
            lastMonthCal.set(Calendar.DAY_OF_MONTH, lastMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH))
            val lastMonthTo = lastMonthCal.time
            val lastMonthVouchers = repository.getAllVouchersWithAllEntriesBetweenDates(companyId, lastMonthFrom, lastMonthTo)
            var lmSales = 0.0; var lmPurchase = 0.0; var lmReceipt = 0.0; var lmPayment = 0.0
            lastMonthVouchers.forEach { vwe ->
                val amt = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                when (vwe.voucher.type) {
                    "Sales" -> lmSales += amt
                    "Purchase" -> lmPurchase += amt
                    "Sales Return" -> lmSales -= amt
                    "Purchase Return" -> lmPurchase -= amt
                    "Receipt" -> lmReceipt += amt
                    "Payment" -> lmPayment += amt
                }
            }
            _lastMonthSales.value = lmSales
            _lastMonthPurchase.value = lmPurchase
            _lastMonthReceipt.value = lmReceipt
            _lastMonthPayment.value = lmPayment

            // Load same month last year
            val lastYearCal = Calendar.getInstance()
            lastYearCal.add(Calendar.YEAR, -1)
            lastYearCal.set(Calendar.DAY_OF_MONTH, 1)
            val lastYearFrom = lastYearCal.time
            lastYearCal.set(Calendar.DAY_OF_MONTH, lastYearCal.getActualMaximum(Calendar.DAY_OF_MONTH))
            val lastYearTo = lastYearCal.time
            val lastYearVouchers = repository.getAllVouchersWithAllEntriesBetweenDates(companyId, lastYearFrom, lastYearTo)
            var lySales = 0.0; var lyPurchase = 0.0
            lastYearVouchers.forEach { vwe ->
                val amt = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                when (vwe.voucher.type) {
                    "Sales" -> lySales += amt
                    "Purchase" -> lyPurchase += amt
                    "Sales Return" -> lySales -= amt
                    "Purchase Return" -> lyPurchase -= amt
                }
            }
            _lastYearSales.value = lySales
            _lastYearPurchase.value = lyPurchase

            // 6-month sales trend (oldest → current) for the hero sparkline
            val trendCal = Calendar.getInstance()
            trendCal.add(Calendar.MONTH, -5)
            trendCal.set(Calendar.DAY_OF_MONTH, 1)
            trendCal.set(Calendar.HOUR_OF_DAY, 0); trendCal.set(Calendar.MINUTE, 0); trendCal.set(Calendar.SECOND, 0); trendCal.set(Calendar.MILLISECOND, 0)
            val sixMonthsFrom = trendCal.time
            val trendVouchers = repository.getAllVouchersWithAllEntriesBetweenDates(companyId, sixMonthsFrom, toDate)
            val monthlySales = DoubleArray(6)
            trendVouchers.forEach { vwe ->
                if (vwe.voucher.type == "Sales") {
                    val amt = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                    val vc = Calendar.getInstance().apply { time = vwe.voucher.date }
                    val idx = (vc.get(Calendar.YEAR) - trendCal.get(Calendar.YEAR)) * 12 + (vc.get(Calendar.MONTH) - trendCal.get(Calendar.MONTH))
                    if (idx in 0..5) monthlySales[idx] += amt
                }
            }
            _salesTrend.value = monthlySales.map { it.toFloat() }

            // 2. Cumulative Balances
            val allVouchers = repository.getAllVouchersWithAllEntries(companyId)
            val allLedgers = ledgerRepository.getAllLedgersSync(companyId)
            
            val ledgerBalances = mutableMapOf<Int, Long>() // LedgerId -> Balance in paise (Debit is positive, Credit is negative)

            // Include opening balance
            allLedgers.forEach { ledger ->
                ledgerBalances[ledger.id] = ledger.openingBalance
            }

            allVouchers.forEach { vwe ->
                vwe.voucherEntries.forEach { entry ->
                    val currentBal = ledgerBalances.getOrDefault(entry.ledgerId, 0L)
                    if (entry.isDebit) {
                        ledgerBalances[entry.ledgerId] = currentBal + entry.amount
                    } else {
                        ledgerBalances[entry.ledgerId] = currentBal - entry.amount
                    }
                }
            }

            var receivables = 0.0
            var payables = 0.0
            var cash = 0.0
            var bank = 0.0

            val debtorGroups = listOf("Sundry Debtors", "Finance Companies", "Money to Collect")
            val creditorGroups = listOf("Sundry Creditors", "Money to Pay")
            val cashGroups = listOf("Cash-in-hand")
            val bankGroups = listOf("Bank Accounts", "Bank OD A/c")

            allLedgers.forEach { ledger ->
                val balance = ledgerBalances.getOrDefault(ledger.id, 0L).paiseToRupees()
                when {
                    debtorGroups.any { it.equals(ledger.group, ignoreCase = true) } -> {
                        if (balance > 0) receivables += balance // Net Debit
                    }
                    creditorGroups.any { it.equals(ledger.group, ignoreCase = true) } -> {
                        if (balance < 0) payables += Math.abs(balance) // Net Credit
                    }
                    cashGroups.any { it.equals(ledger.group, ignoreCase = true) } -> {
                        cash += balance // Usually Debit
                    }
                    bankGroups.any { it.equals(ledger.group, ignoreCase = true) } -> {
                        bank += balance // Debit for asset, Credit for OD (will be negative)
                    }
                }
            }

            _totalReceivables.value = receivables
            _totalPayables.value = payables
            _cashInHand.value = cash
            _bankBalance.value = bank

            // 3. Health Card Logic (AI Natural Language Generation)
            try {
                // A. Week Range
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val weekStart = cal.time
                val now = Date()
                
                // B. 30 Days Range
                cal.time = now
                cal.add(Calendar.DAY_OF_YEAR, -30)
                val last30DaysStart = cal.time
                
                val weekVouchers = repository.getAllVouchersWithAllEntriesBetweenDates(companyId, weekStart, now)
                val last30DaysVouchers = repository.getAllVouchersWithAllEntriesBetweenDates(companyId, last30DaysStart, now)
                
                // Calculate Weekly Sales vs Spend
                var weekSales = 0.0
                var weekSpend = 0.0
                weekVouchers.forEach { vwe ->
                    val amount = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                    if (vwe.voucher.type == "Sales") weekSales += amount
                    if (vwe.voucher.type == "Payment" || vwe.voucher.type == "Purchase") weekSpend += amount
                }
                
                val diff = weekSales - weekSpend
                val flowText = if (diff >= 0) {
                    "You sold ₹${String.format("%,.0f", diff)} more than you spent this week."
                } else {
                    "You spent ₹${String.format("%,.0f", Math.abs(diff))} more than you sold this week."
                }
                
                // Calculate Runway
                var last30DaysSpend = 0.0
                last30DaysVouchers.forEach { vwe ->
                    val amount = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                     if (vwe.voucher.type == "Payment" || vwe.voucher.type == "Purchase") last30DaysSpend += amount
                }
                
                val dailyBurn = if (last30DaysSpend > 0) last30DaysSpend / 30 else 0.0
                val totalLiquid = cash + bank
                
                val runwayText = if (dailyBurn > 100) { // Threshold for meaningful burn
                    val days = (totalLiquid / dailyBurn).toInt()
                    "You have enough cash to cover expenses for $days days."
                } else {
                    "Your recent expenses are low, so your cash runway looks safe."
                }
                
                var finalText = "$flowText $runwayText"
                
                // Tax Insight
                if (isTaxSystemEnabled && estimatedTaxLiability > 100) {
                    val spendable = (totalLiquid - estimatedTaxLiability).coerceAtLeast(0.0)
                    val taxText = "\n\nTax Watch: You have ₹${String.format("%,.0f", totalLiquid)} liquid cash, but ~₹${String.format("%,.0f", estimatedTaxLiability)} is estimated for tax. Real Spendable: ₹${String.format("%,.0f", spendable)}."
                    finalText += taxText
                }
                
                _healthCardText.value = finalText

                // 6. Helafin Nudges Computation
                val allVouchers = repository.getAllVouchersWithAllEntries(companyId)
                val stockItems = stockItemDao.getAllStockItems(companyId)
                val dbInstance = com.minhas.teller.data.AppDatabase.getDatabase(context ?: return@launch)
                
                // A. Low Stock (Quantity <= 0)
                val stockBalances = stockItems.associate { it.id to Pair(it.openingStock, it.rate) }.toMutableMap()
                allVouchers.forEach { vw ->
                    when (vw.voucher.type) {
                        "Sales", "Purchase Return", "Debit Note" -> vw.inventoryEntries.forEach { entry ->
                            val current = stockBalances.getOrDefault(entry.stockItemId, Pair(0.0, 0.0))
                            stockBalances[entry.stockItemId] = Pair(current.first - entry.quantity, current.second)
                        }
                        "Purchase", "Sales Return", "Credit Note" -> vw.inventoryEntries.forEach { entry ->
                            val current = stockBalances.getOrDefault(entry.stockItemId, Pair(0.0, 0.0))
                            stockBalances[entry.stockItemId] = Pair(current.first + entry.quantity, entry.rate)
                        }
                        "Journal" -> vw.inventoryEntries.forEach { entry ->
                            val current = stockBalances.getOrDefault(entry.stockItemId, Pair(0.0, 0.0))
                            stockBalances[entry.stockItemId] = Pair(current.first + entry.quantity, if (entry.quantity > 0) entry.rate else current.second)
                        }
                    }
                }
                val lowStockItems = stockItems.filter {
                    val qty = stockBalances[it.id]?.first ?: 0.0
                    qty <= 0.0
                }
                
                // B. Negative Margins — same calculation as the Item Profitability report: sale value
                // without GST vs weighted-average purchase cost (without GST) on the day of each sale,
                // net of returns. (Used to mix paise amounts with the item's rupee rate and average
                // ALL purchases, so real losses were missed and fake ones raised.)
                val costLedger = com.minhas.teller.data.accounting.ProfitCalculator.buildCostLedger(stockItems, allVouchers)
                val negativeMarginItems = com.minhas.teller.data.accounting.ProfitCalculator
                    .itemProfits(costLedger, com.minhas.teller.data.accounting.ProfitCalculator.inRange(null, null))
                    .filter { it.costKnown && it.quantity > 0.0 && it.revenuePaise > 0L && it.marginPercent < 0.0 }
                    .map { Pair(it.name, it.marginPercent) }

                // C. Overdue receivables
                val thirtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.time
                val overdueVouchers = repository.getAllOutstandingVouchers(companyId)
                    .filter { it.pendingAmount > 0 && it.date.before(thirtyDaysAgo) }
                val overdueCustomerCount = overdueVouchers.mapNotNull { it.customerName }.distinct().size

                // Create Nudges list
                val nudges = mutableListOf<HelafinNudge>()
                negativeMarginItems.forEach { (name, margin) ->
                    nudges.add(HelafinNudge(
                        title = "Loss Warning: $name",
                        description = "You're selling $name below cost (${String.format("%.1f", margin)}% margin) — raise price or stop.",
                        type = "NEGATIVE_MARGIN"
                    ))
                }
                if (overdueCustomerCount > 0) {
                    nudges.add(HelafinNudge(
                        title = "Overdue Payments",
                        description = "$overdueCustomerCount customer${if (overdueCustomerCount > 1) "s" else ""} haven't paid in 30 days.",
                        type = "PENDING_RECEIVABLES"
                    ))
                }
                if (lowStockItems.isNotEmpty()) {
                    val itemNames = lowStockItems.take(2).joinToString { it.name }
                    val suffix = if (lowStockItems.size > 2) " and ${lowStockItems.size - 2} more" else ""
                    nudges.add(HelafinNudge(
                        title = "Low Stock Alert",
                        description = "${lowStockItems.size} item${if (lowStockItems.size > 1) "s are" else " is"} out of stock ($itemNames$suffix) — suggest reordering.",
                        type = "LOW_STOCK"
                    ))
                }
                
                _helafinNudges.value = nudges
            } catch (e: Exception) {
                _healthCardText.value = "Your financial health summary will appear here once you add more data."
            }
        }
    }
}

class DashboardViewModelFactory(
    private val repository: VoucherRepository,
    private val ledgerRepository: LedgerRepository,
    private val stockItemDao: StockItemDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository, ledgerRepository, stockItemDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
