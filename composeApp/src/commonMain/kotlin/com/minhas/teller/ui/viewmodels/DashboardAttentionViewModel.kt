package com.minhas.teller.ui.viewmodels

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlin.math.abs

enum class AttentionSeverity { HIGH, MEDIUM, LOW }

data class AttentionItem(
    val id: String,
    val title: String,
    val description: String,
    val count: Int,
    val icon: ImageVector,
    val route: String,
    val severity: AttentionSeverity,
    val fixHint: String = ""
)

data class WarningVoucher(
    val voucherId: Int,
    val voucherNumber: String,
    val date: java.util.Date,
    val type: String,
    val customerName: String? = null,
    val phoneNumber: String? = null,
    val pendingAmount: Double = 0.0
)

data class DashboardAttentionState(
    val items: List<AttentionItem> = emptyList(),
    val totalIssues: Int = 0,
    val isLoading: Boolean = true
)

class DashboardAttentionViewModel(
    private val voucherRepository: VoucherRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardAttentionState())
    val state: StateFlow<DashboardAttentionState> = _state

    fun loadAttentionItems(companyId: Int, ignoredIds: Set<String> = emptySet()) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val allVouchers = voucherRepository.getAllVouchersWithAllEntries(companyId)
                val allLedgers = ledgerRepository.getAllLedgersSync(companyId)
                val allOutstanding = voucherRepository.getAllOutstandingVouchers(companyId)

                val items = mutableListOf<AttentionItem>()

                // 1. Unbalanced vouchers (HIGH)
                var unbalancedCount = 0
                allVouchers.forEach { vwe ->
                    val debitSum = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
                    val creditSum = vwe.voucherEntries.filter { !it.isDebit }.sumOf { it.amount }
                    if (abs(debitSum - creditSum) > 0.01) unbalancedCount++
                }
                if (unbalancedCount > 0) {
                    items.add(AttentionItem(
                        id = "unbalanced",
                        title = "Unbalanced Vouchers",
                        description = "$unbalancedCount voucher(s) have debit-credit mismatches",
                        count = unbalancedCount,
                        icon = Icons.Default.Balance,
                        route = "daybook",
                        severity = AttentionSeverity.HIGH,
                        fixHint = "Open each voucher and make the debit total equal the credit total."
                    ))
                }

                // 2. Sales missing tax entries (HIGH)
                var missingTaxCount = 0
                allVouchers.forEach { vwe ->
                    if (vwe.voucher.type.equals("Sales", true)) {
                        if (!GstComplianceRules.voucherHasTax(vwe, allLedgers)) missingTaxCount++
                    }
                }
                if (missingTaxCount > 0) {
                    items.add(AttentionItem(
                        id = "missing_tax",
                        title = "Sales Missing Tax",
                        description = "$missingTaxCount sale(s) have no GST/tax ledger entries",
                        count = missingTaxCount,
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        route = "daybook",
                        severity = AttentionSeverity.HIGH,
                        fixHint = "Add a GST/tax ledger entry to each sale so the tax is recorded."
                    ))
                }

                // 3. Overdue receivables (HIGH)
                val now = System.currentTimeMillis()
                val overdueReceivables = allOutstanding.filter { it.isDebit && it.pendingAmount > 0 }
                val veryOverdue = overdueReceivables.filter {
                    ((now - it.date.time) / (1000 * 60 * 60 * 24)) > 30
                }
                if (veryOverdue.isNotEmpty()) {
                    items.add(AttentionItem(
                        id = "overdue",
                        title = "Overdue Receivables",
                        description = "${veryOverdue.size} invoice(s) unpaid for 30+ days",
                        count = veryOverdue.size,
                        icon = Icons.Default.Warning,
                        route = "outstanding_payments_summary?group=Sundry Debtors",
                        severity = AttentionSeverity.HIGH,
                        fixHint = "Follow up with these customers or record their payment receipts."
                    ))
                }

                // 4. Cash payments exceeding ₹10,000 (MEDIUM)
                var cashLimitCount = 0
                allVouchers.forEach { vwe ->
                    if (vwe.voucher.type.equals("Payment", true)) {
                        val totalAmount = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
                        val paidViaCash = vwe.voucherEntries.any { entry ->
                            !entry.isDebit && allLedgers.find { it.id == entry.ledgerId }?.group.equals("Cash-in-hand", true)
                        }
                        if (paidViaCash && totalAmount > 10000) cashLimitCount++
                    }
                }
                if (cashLimitCount > 0) {
                    items.add(AttentionItem(
                        id = "cash_limit",
                        title = "Cash Limit Exceeded",
                        description = "$cashLimitCount cash payment(s) above ₹10,000 threshold",
                        count = cashLimitCount,
                        icon = Icons.Default.MoneyOff,
                        route = "daybook",
                        severity = AttentionSeverity.MEDIUM,
                        fixHint = "Pay amounts above ₹10,000 via bank/UPI instead of cash."
                    ))
                }

                // 5. Missing narrations (MEDIUM)
                var missingNarrationCount = 0
                allVouchers.forEach { vwe ->
                    if (vwe.voucher.narration.isNullOrBlank() || vwe.voucher.narration.trim().length < 3) {
                        missingNarrationCount++
                    }
                }
                if (missingNarrationCount > 3) {
                    items.add(AttentionItem(
                        id = "narration",
                        title = "Missing Narrations",
                        description = "$missingNarrationCount voucher(s) need proper narration",
                        count = missingNarrationCount,
                        icon = Icons.Default.EditNote,
                        route = "daybook",
                        severity = AttentionSeverity.LOW,
                        fixHint = "Add a short note to each voucher describing the transaction."
                    ))
                }

                // 6. Negative cash balance (HIGH)
                val ledgerBalances = mutableMapOf<Int, Long>()
                allLedgers.forEach { ledgerBalances[it.id] = it.openingBalance }
                allVouchers.forEach { vwe ->
                    vwe.voucherEntries.forEach { entry ->
                        val cur = ledgerBalances.getOrDefault(entry.ledgerId, 0L)
                        ledgerBalances[entry.ledgerId] = if (entry.isDebit) cur + entry.amount else cur - entry.amount
                    }
                }
                var negativeCash = false
                allLedgers.forEach { ledger ->
                    if (ledger.group.equals("Cash-in-hand", true)) {
                        val bal = ledgerBalances.getOrDefault(ledger.id, 0L).paiseToRupees()
                        if (bal < 0) negativeCash = true
                    }
                }
                if (negativeCash) {
                    items.add(AttentionItem(
                        id = "negative_cash",
                        title = "Negative Cash Balance",
                        description = "Cash account shows negative balance — check for errors",
                        count = 1,
                        icon = Icons.Default.Error,
                        route = "accounts_info",
                        severity = AttentionSeverity.HIGH,
                        fixHint = "You've spent more cash than you received. Review cash payments vs receipts, or add an opening balance if it was missed."
                    ))
                }

                // 7. e-Invoicing (IRN) - applies automatically once yearly sales crossed Rs 5 crore in an
                // earlier financial year (or when the owner switched it on).
                val eStore = com.minhas.teller.utils.EInvoiceStore
                val eStatus = eStore.status(companyId, allVouchers, allLedgers)
                if (eStatus.fromMillis != null) {
                    val pend = eStore.pending(companyId, allVouchers, allLedgers)
                    if (pend.isNotEmpty()) {
                        val oldest = pend.maxOf { eStore.daysOld(it.voucher.date) }
                        items.add(AttentionItem(
                            id = "einvoice_pending",
                            title = "e-Invoice (IRN) pending",
                            description = "${pend.size} B2B bill(s) not sent to the government yet" + if (oldest >= 20) " - oldest is $oldest days old" else "",
                            count = pend.size,
                            icon = Icons.Default.Receipt,
                            route = "daybook",
                            severity = if (oldest >= 20) AttentionSeverity.HIGH else AttentionSeverity.MEDIUM,
                            fixHint = "Tap \"Send all now\" below to send every pending bill in one go (first-time customers need their address added once, from the bill's e-Invoice screen). Only bills to customers with a GSTIN (B2B) and exports need an e-invoice."
                        ))
                    }
                } else if (eStatus.reason == com.minhas.teller.utils.EInvoiceStore.Reason.CROSSED_THIS_YEAR) {
                    val from = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date(eStatus.compulsoryFromMillis ?: System.currentTimeMillis()))
                    items.add(AttentionItem(
                        id = "einvoice_suggest",
                        title = "e-Invoicing will apply to you",
                        description = "Your sales in FY ${eStatus.biggestFy} crossed Rs 5 crore - e-invoices are compulsory from $from",
                        count = 1,
                        icon = Icons.Default.Receipt,
                        route = "daybook",
                        severity = AttentionSeverity.MEDIUM,
                        fixHint = "From $from every bill to a customer with a GSTIN (B2B) must be sent to the government first. Open GST Returns > Send Bills to Government and tap \"Start now\" to begin early. Confirm with your CA."
                    ))
                }

                // Filter out ignored warnings, then sort by severity
                val sorted = items.filter { it.id !in ignoredIds }.sortedBy { it.severity.ordinal }

                _state.value = DashboardAttentionState(
                    items = sorted,
                    totalIssues = sorted.sumOf { it.count },
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = DashboardAttentionState(isLoading = false)
            }
        }
    }

    /** Return the specific vouchers that triggered a warning, so a detail screen can list them. */
    fun getWarningVouchers(companyId: Int, warningId: String, onResult: (List<WarningVoucher>) -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Overdue receivables come from the outstanding-vouchers view, not the voucher list.
                if (warningId == "overdue") {
                    val now = System.currentTimeMillis()
                    val allLedgers = ledgerRepository.getAllLedgersSync(companyId)
                    val overdue = voucherRepository.getAllOutstandingVouchers(companyId)
                        .filter { it.isDebit && it.pendingAmount > 0 && ((now - it.date.time) / (1000 * 60 * 60 * 24)) > 30 }
                        .map {
                            val ledger = allLedgers.find { l -> l.id == it.ledgerId }
                            WarningVoucher(it.voucherId, it.voucherNumber, it.date, it.type, it.customerName, ledger?.phoneNumber, it.pendingAmount)
                        }
                    onResult(overdue)
                    return@launch
                }
                val allVouchers = voucherRepository.getAllVouchersWithAllEntries(companyId)
                val allLedgers = ledgerRepository.getAllLedgersSync(companyId)
                val result = when (warningId) {
                    "unbalanced" -> allVouchers.filter { vwe ->
                        val debitSum = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
                        val creditSum = vwe.voucherEntries.filter { !it.isDebit }.sumOf { it.amount }
                        abs(debitSum - creditSum) > 0.01
                    }
                    "missing_tax" -> allVouchers.filter { vwe ->
                        vwe.voucher.type.equals("Sales", true) && vwe.voucherEntries.none { entry ->
                            val name = allLedgers.find { it.id == entry.ledgerId }?.name?.lowercase().orEmpty()
                            name.contains("gst") || name.contains("tax") || name.contains("duty")
                        }
                    }
                    "cash_limit" -> allVouchers.filter { vwe ->
                        vwe.voucher.type.equals("Payment", true) &&
                            vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount } > 10000 &&
                            vwe.voucherEntries.any { entry -> !entry.isDebit && allLedgers.find { it.id == entry.ledgerId }?.group.equals("Cash-in-hand", true) }
                    }
                    "einvoice_pending" -> {
                        // Show who the bill is for and its amount
                        val pend = com.minhas.teller.utils.EInvoiceStore.pending(companyId, allVouchers, allLedgers)
                        onResult(pend.map { v ->
                            val buyer = com.minhas.teller.utils.EInvoiceStore.buyerLedger(v, allLedgers)
                            val amt = v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                            WarningVoucher(
                                v.voucher.id, v.voucher.voucherNumber, v.voucher.date, v.voucher.type,
                                customerName = buyer?.name,
                                phoneNumber = buyer?.phoneNumber,
                                pendingAmount = amt
                            )
                        })
                        return@launch
                    }
                    "narration" -> allVouchers.filter { it.voucher.narration.isNullOrBlank() || it.voucher.narration.trim().length < 3 }
                    else -> emptyList()
                }

                // Map results with actual transaction amounts and customer names instead of defaulting to 0.00
                val mapped = result.map { vwe ->
                    val partyEntry = vwe.voucherEntries.find { entry ->
                        val l = allLedgers.find { l -> l.id == entry.ledgerId }
                        val grp = l?.group.orEmpty()
                        grp.contains("Debtors", true) || grp.contains("Creditors", true) ||
                            grp.contains("Bank", true) || grp.contains("Cash", true)
                    } ?: vwe.voucherEntries.firstOrNull()

                    val partyLedger = partyEntry?.let { entry -> allLedgers.find { l -> l.id == entry.ledgerId } }

                    val totalAmount = if (vwe.voucher.type.equals("Sales", true) || vwe.voucher.type.equals("Receipt", true)) {
                        vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                    } else if (vwe.voucher.type.equals("Purchase", true) || vwe.voucher.type.equals("Payment", true)) {
                        vwe.voucherEntries.filter { !it.isDebit }.sumOf { it.amount }.paiseToRupees()
                    } else {
                        vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                    }

                    WarningVoucher(
                        voucherId = vwe.voucher.id,
                        voucherNumber = vwe.voucher.voucherNumber,
                        date = vwe.voucher.date,
                        type = vwe.voucher.type,
                        customerName = partyLedger?.name,
                        phoneNumber = partyLedger?.phoneNumber,
                        pendingAmount = totalAmount
                    )
                }
                onResult(mapped)
            } catch (e: Exception) {
                onResult(emptyList())
            }
        }
    }
}

class DashboardAttentionViewModelFactory(
    private val voucherRepository: VoucherRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
        if (modelClass.isAssignableFrom(DashboardAttentionViewModel::class.java)) {
            return DashboardAttentionViewModel(voucherRepository, ledgerRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
