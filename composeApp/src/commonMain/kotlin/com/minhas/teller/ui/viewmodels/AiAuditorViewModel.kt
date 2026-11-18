package com.minhas.teller.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.TaxConfigManager
import com.minhas.teller.utils.GstComplianceRules
import com.minhas.teller.utils.paiseToRupees
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max

@Immutable
data class AuditFinding(
    val id: String,
    val type: FindingType,
    val severity: Severity,
    val title: String,
    val message: String,
    val voucherId: Int? = null,
    val actionLabel: String? = null
)

enum class FindingType {
    NEGATIVE_CASH,
    DAILY_NEGATIVE_CASH,
    MISSING_NARRATION,
    MISSING_TAX,
    MISSING_HSN,
    CASH_LIMIT_EXCEEDED,
    SECTION_269ST_VIOLATION,
    DUPLICATE_INVOICE,
    UNBALANCED_ENTRY
}

enum class Severity {
    HIGH, MEDIUM, LOW
}

@Immutable
data class PredictiveRunway(
    val totalLiquidity: Double = 0.0,
    val dailyBurnRate: Double = 0.0,
    val runwayDays: Int = 0,
    val projectedCash30d: Double = 0.0,
    val projectedCash60d: Double = 0.0,
    val projectedCash90d: Double = 0.0
)

@Immutable
data class AuditState(
    val isAuditing: Boolean = false,
    val findings: List<AuditFinding> = emptyList(),
    val auditScore: Int = 100,
    val checkedCount: Int = 0,
    val runway: PredictiveRunway = PredictiveRunway()
)

class AiAuditorViewModel(
    private val voucherRepository: VoucherRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    private val _auditState = MutableStateFlow(AuditState())
    val auditState: StateFlow<AuditState> = _auditState

    fun runAudit(companyId: Int, countryName: String, currencySymbol: String) {
        viewModelScope.launch(Dispatchers.Default) {
            _auditState.value = AuditState(isAuditing = true)
            try {
                val isIndia = countryName.equals("India", true)
                val taxConfig = TaxConfigManager.getConfigForCountry(countryName)
                val taxLabel = taxConfig.taxName
                val taxEntriesLabel = if (isIndia) "CGST/SGST/IGST" else taxLabel

                val allVouchers = voucherRepository.getAllVouchersWithAllEntries(companyId)
                val allLedgers = ledgerRepository.getAllLedgersSync(companyId)
                val findings = mutableListOf<AuditFinding>()
                var checkedCount = 0

                // 1. Calculate Ledger Balances to check for negative cash & liquidity
                val ledgerBalances = mutableMapOf<Int, Long>()
                allLedgers.forEach { ledgerBalances[it.id] = it.openingBalance }
                allVouchers.forEach { vwe ->
                    vwe.voucherEntries.forEach { entry ->
                        val cur = ledgerBalances.getOrDefault(entry.ledgerId, 0L)
                        ledgerBalances[entry.ledgerId] = if (entry.isDebit) cur + entry.amount else cur - entry.amount
                    }
                }

                // Check Final Negative Cash
                val cashLedgerIds = mutableSetOf<Int>()
                val bankLedgerIds = mutableSetOf<Int>()
                var totalCash = 0.0
                var totalBank = 0.0

                allLedgers.forEach { ledger ->
                    val isCash = ledger.group.equals("Cash-in-hand", true) || ledger.name.contains("Cash", true)
                    val isBank = ledger.group.equals("Bank Accounts", true) || ledger.name.contains("Bank", true)
                    val bal = ledgerBalances.getOrDefault(ledger.id, 0L).paiseToRupees()

                    if (isCash) {
                        cashLedgerIds.add(ledger.id)
                        totalCash += bal
                        if (bal < -0.01) {
                            findings.add(
                                AuditFinding(
                                    id = "neg_cash_${ledger.id}",
                                    type = FindingType.NEGATIVE_CASH,
                                    severity = Severity.HIGH,
                                    title = "Negative Cash Balance",
                                    message = "Your cash account '${ledger.name}' has a negative closing balance of $currencySymbol${String.format("%,.2f", abs(bal))}. This indicates recorded payments exceed actual cash on hand.",
                                    actionLabel = "Review Ledger"
                                )
                            )
                        }
                    }
                    if (isBank) {
                        bankLedgerIds.add(ledger.id)
                        totalBank += bal
                    }
                }
                checkedCount += allLedgers.size

                // 2. Daily Chronological Running Negative Cash Sentinel
                val sortedVouchers = allVouchers.sortedBy { it.voucher.date }
                val runningCashBalances = mutableMapOf<Int, Long>()
                allLedgers.filter { cashLedgerIds.contains(it.id) }.forEach {
                    runningCashBalances[it.id] = it.openingBalance
                }
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
                val flaggedCashDates = mutableSetOf<String>()

                sortedVouchers.forEach { vwe ->
                    vwe.voucherEntries.forEach { entry ->
                        if (cashLedgerIds.contains(entry.ledgerId)) {
                            val cur = runningCashBalances.getOrDefault(entry.ledgerId, 0L)
                            val nextBal = if (entry.isDebit) cur + entry.amount else cur - entry.amount
                            runningCashBalances[entry.ledgerId] = nextBal

                            val dateKey = sdf.format(vwe.voucher.date)
                            if (nextBal < 0L && !flaggedCashDates.contains(dateKey)) {
                                flaggedCashDates.add(dateKey)
                                findings.add(
                                    AuditFinding(
                                        id = "daily_neg_cash_${vwe.voucher.id}",
                                        type = FindingType.DAILY_NEGATIVE_CASH,
                                        severity = Severity.HIGH,
                                        title = "Forensic Alert: Negative Running Cash on $dateKey",
                                        message = "On $dateKey, cash balance dropped below zero ($currencySymbol${String.format("%,.2f", nextBal.paiseToRupees())}). Under Section 68 of the Income Tax Act, intraday negative cash is treated as unexplained cash credit during tax scrutiny.",
                                        voucherId = vwe.voucher.id,
                                        actionLabel = "Inspect Day"
                                    )
                                )
                            }
                        }
                    }
                }

                // 3. Section 269ST Sentinel (Cash receipt >= 2,00,000 from person in a day)
                val receiptsByDateAndParty = mutableMapOf<String, Double>()
                allVouchers.filter { it.voucher.type.equals("Receipt", true) }.forEach { vwe ->
                    val isCash = vwe.voucherEntries.any { it.isDebit && cashLedgerIds.contains(it.ledgerId) }
                    val creditParty = vwe.voucherEntries.firstOrNull { !it.isDebit }
                    if (isCash && creditParty != null) {
                        val key = "${sdf.format(vwe.voucher.date)}_${creditParty.ledgerId}"
                        val cur = receiptsByDateAndParty.getOrDefault(key, 0.0)
                        val updated = cur + creditParty.amount.paiseToRupees()
                        receiptsByDateAndParty[key] = updated
                        if (updated >= 200000.0 && cur < 200000.0) {
                            val partyName = allLedgers.find { it.id == creditParty.ledgerId }?.name ?: "Party"
                            findings.add(
                                AuditFinding(
                                    id = "sec_269st_${vwe.voucher.id}",
                                    type = FindingType.SECTION_269ST_VIOLATION,
                                    severity = Severity.HIGH,
                                    title = "Section 269ST Income Tax Violation",
                                    message = "Cash receipt of $currencySymbol${String.format("%,.0f", updated)} from '$partyName' on ${sdf.format(vwe.voucher.date)} exceeds ₹2,00,000 limit. Section 271DA imposes a mandatory 100% penalty on such cash receipts.",
                                    voucherId = vwe.voucher.id,
                                    actionLabel = "View Receipt"
                                )
                            )
                        }
                    }
                }

                // 4. Duplicate Invoice Number Sentinel
                val seenVoucherNumbers = mutableMapOf<String, Int>()
                allVouchers.forEach { vwe ->
                    val num = vwe.voucher.voucherNumber.trim().lowercase(Locale.US)
                    val type = vwe.voucher.type
                    if (num.isNotBlank() && num != "0") {
                        val key = "$type::$num"
                        val count = seenVoucherNumbers.getOrDefault(key, 0)
                        seenVoucherNumbers[key] = count + 1
                        if (count == 1) {
                            findings.add(
                                AuditFinding(
                                    id = "dup_inv_${vwe.voucher.id}",
                                    type = FindingType.DUPLICATE_INVOICE,
                                    severity = Severity.MEDIUM,
                                    title = "Duplicate Invoice Number ($type #${vwe.voucher.voucherNumber})",
                                    message = "Multiple $type vouchers found with identical invoice number '${vwe.voucher.voucherNumber}'. Duplicate invoicing leads to audit disallowance.",
                                    voucherId = vwe.voucher.id,
                                    actionLabel = "Resolve Duplicate"
                                )
                            )
                        }
                    }
                }

                // 5. Transaction-level audits (Narration, GST, 40A(3), Unbalanced)
                allVouchers.forEach { vwe ->
                    val voucher = vwe.voucher
                    val entries = vwe.voucherEntries
                    val totalAmount = entries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()

                    // Check missing narration
                    if (voucher.narration.isNullOrBlank() || voucher.narration.trim().length < 3) {
                        findings.add(
                            AuditFinding(
                                id = "narr_${voucher.id}",
                                type = FindingType.MISSING_NARRATION,
                                severity = Severity.LOW,
                                title = "Missing Narration",
                                message = "${voucher.type} Voucher #${voucher.voucherNumber} ($currencySymbol${String.format("%,.0f", totalAmount)}) has no narration detail. CAs recommend explanations for all audit entries.",
                                voucherId = voucher.id,
                                actionLabel = "Add Narration"
                            )
                        )
                    }

                    // Check Sales Vouchers: Missing Tax
                    if (voucher.type.equals("Sales", true)) {
                        val hasTax = GstComplianceRules.voucherHasTax(vwe, allLedgers)
                        if (!hasTax && totalAmount > 0) {
                            findings.add(
                                AuditFinding(
                                    id = "tax_${voucher.id}",
                                    type = FindingType.MISSING_TAX,
                                    severity = Severity.HIGH,
                                    title = "No Tax Entered on Sale",
                                    message = "Sales invoice #${voucher.voucherNumber} ($currencySymbol${String.format("%,.0f", totalAmount)}) does not contain any $taxEntriesLabel tax allocation ledger. Ensure tax is recorded properly.",
                                    voucherId = voucher.id,
                                    actionLabel = "Add Tax Entry"
                                )
                            )
                        }
                    }

                    // Check Section 40A(3) - Cash payment > ₹10,000 to a person in a single day
                    if (voucher.type.equals("Payment", true)) {
                        val paidViaCash = entries.any { entry ->
                            !entry.isDebit && cashLedgerIds.contains(entry.ledgerId)
                        }
                        if (paidViaCash && totalAmount > 10000) {
                            findings.add(
                                AuditFinding(
                                    id = "cash_limit_${voucher.id}",
                                    type = FindingType.CASH_LIMIT_EXCEEDED,
                                    severity = Severity.MEDIUM,
                                    title = "Section 40A(3) Cash Payment Exceeded",
                                    message = if (isIndia) {
                                        "Payment Voucher #${voucher.voucherNumber} of $currencySymbol${String.format("%,.0f", totalAmount)} was made in Cash. Single-day cash business payments over ₹10,000 are disallowed under Section 40A(3) of the Income Tax Act."
                                    } else {
                                        "Payment Voucher #${voucher.voucherNumber} of $currencySymbol${String.format("%,.0f", totalAmount)} was made in Cash. Cash payments exceeding large thresholds are typically disallowed or restricted by local tax authorities."
                                    },
                                    voucherId = voucher.id,
                                    actionLabel = "Modify Payment"
                                )
                            )
                        }
                    }

                    // Check unbalanced vouchers
                    val debitSum = entries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                    val creditSum = entries.filter { !it.isDebit }.sumOf { it.amount }.paiseToRupees()
                    if (abs(debitSum - creditSum) > 0.01) {
                        findings.add(
                            AuditFinding(
                                id = "bal_${voucher.id}",
                                type = FindingType.UNBALANCED_ENTRY,
                                severity = Severity.HIGH,
                                title = "Unbalanced Voucher Entry",
                                message = "Voucher #${voucher.voucherNumber} has mismatched Debit ($currencySymbol${String.format("%,.2f", debitSum)}) and Credit ($currencySymbol${String.format("%,.2f", creditSum)}) entries.",
                                voucherId = voucher.id,
                                actionLabel = "Fix Entry Balance"
                            )
                        )
                    }
                    checkedCount++
                }

                // 6. Predictive 90-Day Cash Runway Computation
                val totalLiquidity = max(0.0, totalCash + totalBank)
                val pastExpenses = allVouchers
                    .filter { it.voucher.type.equals("Payment", true) }
                    .sumOf { vwe -> vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount } }
                val dailyBurnRate = if (allVouchers.isNotEmpty()) max(100.0, pastExpenses / 90.0) else 500.0
                val runwayDays = if (dailyBurnRate > 0) (totalLiquidity / dailyBurnRate).toInt() else 365

                val runway = PredictiveRunway(
                    totalLiquidity = totalLiquidity,
                    dailyBurnRate = dailyBurnRate,
                    runwayDays = runwayDays.coerceIn(0, 999),
                    projectedCash30d = max(0.0, totalLiquidity - (dailyBurnRate * 30)),
                    projectedCash60d = max(0.0, totalLiquidity - (dailyBurnRate * 60)),
                    projectedCash90d = max(0.0, totalLiquidity - (dailyBurnRate * 90))
                )

                val deductions: Int = findings.fold(0) { acc, finding ->
                    acc + when (finding.severity) {
                        Severity.HIGH -> 15
                        Severity.MEDIUM -> 8
                        Severity.LOW -> 3
                    }
                }
                val score = (100 - deductions).coerceIn(10, 100)

                _auditState.value = AuditState(
                    isAuditing = false,
                    findings = findings,
                    auditScore = score,
                    checkedCount = checkedCount,
                    runway = runway
                )
            } catch (e: Exception) {
                _auditState.value = AuditState(isAuditing = false)
            }
        }
    }
}

class AiAuditorViewModelFactory(
    private val voucherRepository: VoucherRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
        if (modelClass.isAssignableFrom(AiAuditorViewModel::class.java)) {
            return AiAuditorViewModel(voucherRepository, ledgerRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
