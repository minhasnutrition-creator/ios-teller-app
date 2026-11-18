package com.minhas.teller.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.utils.GstComplianceRules
import com.minhas.teller.utils.paiseToRupees
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.min

@Immutable
data class TellerScoreData(
    val overallScore: Int = 0,
    val cashFlowScore: Int = 0,
    val collectionScore: Int = 0,
    val profitMarginScore: Int = 0,
    val debtRatioScore: Int = 0,
    val paymentDisciplineScore: Int = 0,
    val totalSalesAllTime: Double = 0.0,
    val totalPurchaseAllTime: Double = 0.0,
    val totalReceivables: Double = 0.0,
    val totalPayables: Double = 0.0,
    val cashInHand: Double = 0.0,
    val bankBalance: Double = 0.0,
    val grossProfitMargin: Double = 0.0,
    val avgCollectionDays: Int = 0,
    val currentRatio: Double = 0.0,
    val quickRatio: Double = 0.0,
    val dscr: Double = 0.0,
    val workingCapitalGap: Double = 0.0,
    val debtToRevenueRatio: Double = 0.0,
    val collectionEfficiency: Double = 0.0,
    val negativeCashDeductions: Int = 0,
    val cashLimitDisallowances: Int = 0,
    val unbalancedVouchersCount: Int = 0,
    val missingTaxInvoicesCount: Int = 0,
    val missingNarrationCount: Int = 0,
    val auditScore: Int = 100,
    val isLoading: Boolean = true
)

class TellerScoreViewModel(
    private val voucherRepository: VoucherRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    private val _scoreData = MutableStateFlow(TellerScoreData())
    val scoreData: StateFlow<TellerScoreData> = _scoreData

    fun loadScore(companyId: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val allVouchers = voucherRepository.getAllVouchersWithAllEntries(companyId)
                val allLedgers = ledgerRepository.getAllLedgersSync(companyId)
                val allOutstanding = voucherRepository.getAllOutstandingVouchers(companyId)

                // --- Compute totals ---
                var totalSales = 0.0
                var totalPurchase = 0.0
                var totalReceipt = 0.0
                var totalPayment = 0.0

                allVouchers.forEach { vwe ->
                    val debitAmt = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                    when (vwe.voucher.type) {
                        "Sales" -> totalSales += debitAmt
                        "Purchase" -> totalPurchase += debitAmt
                        "Receipt" -> totalReceipt += debitAmt
                        "Payment" -> totalPayment += debitAmt
                    }
                }

                // --- Ledger balances ---
                val ledgerBalances = mutableMapOf<Int, Long>()
                allLedgers.forEach { ledgerBalances[it.id] = it.openingBalance }
                allVouchers.forEach { vwe ->
                    vwe.voucherEntries.forEach { entry ->
                        val cur = ledgerBalances.getOrDefault(entry.ledgerId, 0L)
                        ledgerBalances[entry.ledgerId] = if (entry.isDebit) cur + entry.amount else cur - entry.amount
                    }
                }

                var cashInHand = 0.0
                var bankBalance = 0.0
                var receivables = 0.0
                var payables = 0.0

                allLedgers.forEach { ledger ->
                    val bal = ledgerBalances.getOrDefault(ledger.id, 0L).paiseToRupees()
                    when {
                        ledger.group.equals("Cash-in-hand", true) -> cashInHand += bal
                        ledger.group.equals("Bank Accounts", true) || ledger.group.equals("Bank OD A/c", true) -> bankBalance += bal
                        ledger.group.equals("Sundry Debtors", true) || ledger.group.equals("Finance Companies", true) -> { if (bal > 0) receivables += bal }
                        ledger.group.equals("Sundry Creditors", true) -> { if (bal < 0) payables += abs(bal) }
                    }
                }

                // --- Score 1: Cash Flow Health (0-100) ---
                // Ratio: liquid cash vs monthly payment obligations
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val monthStart = cal.time
                val monthEnd = Calendar.getInstance().time
                val monthVouchers = voucherRepository.getAllVouchersWithAllEntriesBetweenDates(companyId, monthStart, monthEnd)
                val monthlyExpenses = monthVouchers.filter { it.voucher.type == "Payment" || it.voucher.type == "Purchase" }
                    .sumOf { vwe -> vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees() }.coerceAtLeast(1.0)
                val liquidCash = (cashInHand + bankBalance).coerceAtLeast(0.0)
                val cashCoverageRatio = liquidCash / monthlyExpenses
                val cashFlowScore = when {
                    cashCoverageRatio >= 3.0 -> 100
                    cashCoverageRatio >= 2.0 -> 88
                    cashCoverageRatio >= 1.5 -> 74
                    cashCoverageRatio >= 1.0 -> 60
                    cashCoverageRatio >= 0.5 -> 40
                    else -> 20
                }.coerceIn(10, 100)

                // --- Score 2: Collection Efficiency (0-100) ---
                // How quickly are outstanding receivables being collected
                val collectionRatio = if (totalSales > 0) (totalReceipt / totalSales).coerceIn(0.0, 1.0) else 0.5
                val collectionScore = (collectionRatio * 100).toInt().coerceIn(10, 100)

                // Avg collection days from outstanding
                val avgDays = if (allOutstanding.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    allOutstanding.filter { it.isDebit }.map { ov ->
                        ((now - ov.date.time) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
                    }.average().toInt()
                } else 0

                // --- Score 3: Profit Margin (0-100) ---
                val grossProfit = totalSales - totalPurchase
                val profitMargin = if (totalSales > 0) (grossProfit / totalSales * 100).coerceIn(0.0, 100.0) else 0.0
                val profitMarginScore = when {
                    profitMargin >= 40.0 -> 100
                    profitMargin >= 25.0 -> 88
                    profitMargin >= 15.0 -> 72
                    profitMargin >= 8.0 -> 55
                    profitMargin >= 0.0 -> 35
                    else -> 10
                }.coerceIn(10, 100)

                // --- Score 4: Debt Ratio (0-100) ---
                // Payables vs monthly revenue (lower payables = better)
                val monthlySales = monthVouchers.filter { it.voucher.type == "Sales" }
                    .sumOf { vwe -> vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees() }.coerceAtLeast(1.0)
                val debtToIncomeRatio = payables / monthlySales
                val debtRatioScore = when {
                    debtToIncomeRatio <= 0.2 -> 100
                    debtToIncomeRatio <= 0.5 -> 82
                    debtToIncomeRatio <= 1.0 -> 65
                    debtToIncomeRatio <= 2.0 -> 45
                    else -> 20
                }.coerceIn(10, 100)

                // --- Score 5: Payment Discipline (0-100) ---
                // Are YOU paying your suppliers on time? (payables vs total purchases)
                val outstandingPayables = allOutstanding.filter { !it.isDebit }
                val paymentDisciplineScore = when {
                    outstandingPayables.isEmpty() -> 100
                    outstandingPayables.size <= 2 -> 80
                    outstandingPayables.size <= 5 -> 65
                    outstandingPayables.size <= 10 -> 48
                    else -> 25
                }.coerceIn(10, 100)

                // --- Detailed Financial Ratios for Banks ---
                val totalLiquidAssets = (cashInHand + bankBalance + receivables).coerceAtLeast(0.0)
                val currentRatio = if (payables > 0) (totalLiquidAssets / payables) else 2.5
                val quickRatio = if (payables > 0) ((cashInHand + bankBalance + receivables) / payables) else 2.5
                
                // DSCR: Operating Income (Sales - Purchase) / (Payables/12 + 1.0)
                val operatingIncome = (totalSales - totalPurchase).coerceAtLeast(1.0)
                val monthlyDebtObligation = (payables / 12.0).coerceAtLeast(1.0)
                val dscr = if (monthlyDebtObligation > 0) (operatingIncome / monthlyDebtObligation) else 3.0
                
                val workingCapitalGap = totalLiquidAssets - payables
                val debtToRevenueRatio = if (totalSales > 0) (payables / totalSales) else 0.0
                val collectionEfficiency = if (totalSales > 0) (totalReceipt / totalSales * 100).coerceIn(0.0, 100.0) else 85.0

                // --- AI Audit checks (In-VM calculation) ---
                var unbalancedVouchersCount = 0
                var negativeCashDeductions = 0
                var cashLimitDisallowances = 0
                var missingTaxInvoicesCount = 0
                var missingNarrationCount = 0

                // 1. Negative Cash check
                if (cashInHand < 0) {
                    negativeCashDeductions = 1
                }
                
                // 2. Trans-level checks
                allVouchers.forEach { vwe ->
                    val voucher = vwe.voucher
                    val entries = vwe.voucherEntries
                    val totalAmount = entries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()

                    // Narration check
                    if (voucher.narration.isNullOrBlank() || voucher.narration.trim().length < 3) {
                        missingNarrationCount++
                    }

                    // Sales missing tax check
                    if (voucher.type.equals("Sales", true)) {
                        val hasTax = GstComplianceRules.voucherHasTax(vwe, allLedgers)
                        if (!hasTax && totalAmount > 0) {
                            missingTaxInvoicesCount++
                        }
                    }

                    // Section 40A(3) cash limit check (>10000)
                    if (voucher.type.equals("Payment", true)) {
                        val paidViaCash = entries.any { entry ->
                            !entry.isDebit && allLedgers.find { it.id == entry.ledgerId }?.group.equals("Cash-in-hand", true)
                        }
                        if (paidViaCash && totalAmount > 10000) {
                            cashLimitDisallowances++
                        }
                    }

                    // Unbalanced entries check
                    val debitSum = entries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                    val creditSum = entries.filter { !it.isDebit }.sumOf { it.amount }.paiseToRupees()
                    if (abs(debitSum - creditSum) > 0.01) {
                        unbalancedVouchersCount++
                    }
                }

                // Audit compliance score calculation (similar to Auditor but out of 100)
                val deductions = (negativeCashDeductions * 20) + 
                                 (cashLimitDisallowances * 15) + 
                                 (unbalancedVouchersCount * 15) + 
                                 (missingTaxInvoicesCount * 10) + 
                                 (missingNarrationCount * 2)
                val auditScore = (100 - deductions).coerceIn(10, 100)

                // --- Overall Score (weighted with auditScore) ---
                val overall = (
                    cashFlowScore * 0.20 +
                    collectionScore * 0.20 +
                    profitMarginScore * 0.20 +
                    debtRatioScore * 0.15 +
                    paymentDisciplineScore * 0.10 +
                    auditScore * 0.15
                ).toInt().coerceIn(0, 100)

                // Scale to 0-1000
                val scaledScore = (overall * 10).coerceIn(0, 1000)

                _scoreData.value = TellerScoreData(
                    overallScore = scaledScore,
                    cashFlowScore = cashFlowScore,
                    collectionScore = collectionScore,
                    profitMarginScore = profitMarginScore,
                    debtRatioScore = debtRatioScore,
                    paymentDisciplineScore = paymentDisciplineScore,
                    totalSalesAllTime = totalSales,
                    totalPurchaseAllTime = totalPurchase,
                    totalReceivables = receivables,
                    totalPayables = payables,
                    cashInHand = cashInHand,
                    bankBalance = bankBalance,
                    grossProfitMargin = profitMargin,
                    avgCollectionDays = avgDays,
                    currentRatio = currentRatio,
                    quickRatio = quickRatio,
                    dscr = dscr,
                    workingCapitalGap = workingCapitalGap,
                    debtToRevenueRatio = debtToRevenueRatio,
                    collectionEfficiency = collectionEfficiency,
                    negativeCashDeductions = negativeCashDeductions,
                    cashLimitDisallowances = cashLimitDisallowances,
                    unbalancedVouchersCount = unbalancedVouchersCount,
                    missingTaxInvoicesCount = missingTaxInvoicesCount,
                    missingNarrationCount = missingNarrationCount,
                    auditScore = auditScore,
                    isLoading = false
                )
            } catch (e: Exception) {
                _scoreData.value = TellerScoreData(isLoading = false)
            }
        }
    }
}

class TellerScoreViewModelFactory(
    private val voucherRepository: VoucherRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TellerScoreViewModel::class.java)) {
            return TellerScoreViewModel(voucherRepository, ledgerRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
