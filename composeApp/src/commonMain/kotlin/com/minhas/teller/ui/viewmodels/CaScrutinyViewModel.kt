package com.minhas.teller.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.EvidenceAttachment
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.utils.GstComplianceRules
import com.minhas.teller.utils.TaxConfigManager
import com.minhas.teller.utils.taxIdName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Immutable
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

@Immutable
data class AuditFailedVoucher(
    val voucherId: Int,
    val displayText: String,
    val reason: String = "",
    val suggestion: String = "",
    val stockItemId: Int? = null // When set, "Quick Fix" opens this item in Inventory instead of the voucher editor
)

@Immutable
data class PreCheckItem(
    val title: String,
    val statusText: String,
    val isPass: Boolean,
    val failedVouchers: List<AuditFailedVoucher> = emptyList()
)

@Immutable
data class CaScrutinyUiState(
    val auditScore: Int = 1000,
    val preChecks: List<PreCheckItem> = emptyList(),
    val trialBalance: List<Triple<String, Double, Double>> = emptyList(),
    val pnlRows: List<List<String>> = emptyList(),
    val balanceSheetRows: List<List<String>> = emptyList(),
    val attachmentsCount: Int = 0,
    val isGenerating: Boolean = false,
    val generatedPackFile: File? = null,
    val error: String? = null,
    val allLedgers: List<com.minhas.teller.data.entities.Ledger> = emptyList()
)

class CaScrutinyViewModel(
    private val db: AppDatabase,
    private val voucherRepository: VoucherRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaScrutinyUiState())
    val uiState: StateFlow<CaScrutinyUiState> = _uiState

    private var currentCompany: Company? = null
    private var activeCompanyId: Int = 1
    private var fyPeriodStr: String = "This FY"
    private var vouchersScope: List<VoucherWithAllEntries> = emptyList()

    fun loadComplianceData(companyId: Int, fromDate: Long, toDate: Long) {
        activeCompanyId = companyId
        viewModelScope.launch(Dispatchers.Default) {
            try {
                currentCompany = db.companyDao().getCompanyByIdSync(companyId)
                val countryName = currentCompany?.country ?: "India"
                val isIndia = countryName.equals("India", true)
                val taxConfig = com.minhas.teller.utils.TaxConfigManager.getConfigForCountry(countryName)
                val taxLabel = taxConfig.taxName
                val taxIdLabel = taxConfig.taxIdName
                val taxCodeLabel = taxConfig.codeSystemName ?: "Tax Code"
                val taxEntriesLabel = if (isIndia) "CGST/SGST/IGST" else taxLabel
                val currencySymbol = currentCompany?.currencySymbol ?: "₹"

                val allVouchers = voucherRepository.getAllVouchersWithAllEntries(companyId)
                val allLedgers = ledgerRepository.getAllLedgersSync(companyId)
                val attachments = db.evidenceAttachmentDao().getAttachmentsForCompany(companyId)

                // Date-filtered vouchers
                val scopeVouchers = allVouchers.filter {
                    it.voucher.date.time in fromDate..toDate
                }
                vouchersScope = scopeVouchers

                // 1. Calculate Trial Balance
                val ledgerBalances = mutableMapOf<Int, Long>()
                allLedgers.forEach { ledgerBalances[it.id] = it.openingBalance }
                allVouchers.forEach { vwe ->
                    vwe.voucherEntries.forEach { entry ->
                        val cur = ledgerBalances.getOrDefault(entry.ledgerId, 0L)
                        ledgerBalances[entry.ledgerId] = if (entry.isDebit) cur + entry.amount else cur - entry.amount
                    }
                }

                val trialBalance = allLedgers.map { ledger ->
                    val bal = ledgerBalances.getOrDefault(ledger.id, 0L).paiseToRupees()
                    if (bal >= 0) {
                        Triple(ledger.name, bal, 0.0)
                    } else {
                        Triple(ledger.name, 0.0, abs(bal))
                    }
                }

                // 2. Calculate P&L & Balance Sheet rows
                var totalSales = 0.0
                var totalPurchase = 0.0
                var totalReceipt = 0.0
                var totalPayment = 0.0

                scopeVouchers.forEach { vwe ->
                    val debitAmt = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                    when (vwe.voucher.type) {
                        "Sales" -> totalSales += debitAmt
                        "Purchase" -> totalPurchase += debitAmt
                        "Receipt" -> totalReceipt += debitAmt
                        "Payment" -> totalPayment += debitAmt
                    }
                }

                val df = DecimalFormat("#.00")
                val pnlRows = listOf(
                    listOf("Revenue from Operations (Sales)", df.format(totalSales)),
                    listOf("Cost of Goods Purchased", df.format(totalPurchase)),
                    listOf("Gross Profit Margin", df.format(totalSales - totalPurchase))
                )

                var cashInHand = 0.0
                var bankBalance = 0.0
                var receivables = 0.0
                var payables = 0.0

                allLedgers.forEach { ledger ->
                    val bal = ledgerBalances.getOrDefault(ledger.id, 0L).paiseToRupees()
                    when {
                        ledger.group.equals("Cash-in-hand", true) -> cashInHand += bal
                        ledger.group.equals("Bank Accounts", true) -> bankBalance += bal
                        ledger.group.equals("Sundry Debtors", true) -> { if (bal > 0) receivables += bal }
                        ledger.group.equals("Sundry Creditors", true) -> { if (bal < 0) payables += abs(bal) }
                    }
                }

                val balanceSheetRows = listOf(
                    listOf("Partner Capital / Reserves", "Cash-in-hand & Bank", df.format(cashInHand + bankBalance), df.format(cashInHand + bankBalance)),
                    listOf("Sundry Creditors (Payables)", "Sundry Debtors (Receivables)", df.format(payables), df.format(receivables))
                )

                // 3. AI Compliance Pre-Audit checks
                val checks = mutableListOf<PreCheckItem>()
                var deductions = 0
                val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
                val cal = Calendar.getInstance()
                fun getStartOfDay(date: Date): Long {
                    cal.time = date
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    return cal.timeInMillis
                }

                // A. Cash limit compliance (Section 40A(3))
                val cashPayLimitBreaches = scopeVouchers.filter { v ->
                    v.voucher.type == "Payment" && v.voucherEntries.any {
                        val ledg = allLedgers.find { l -> l.id == it.ledgerId }
                        ledg?.group?.lowercase() == "cash-in-hand" && it.amount.paiseToRupees() > 10000.0
                    }
                }
                
                // Aggregate daily cash payments
                val dailyCashPayments = mutableMapOf<Pair<Long, Int>, MutableList<VoucherWithAllEntries>>()
                scopeVouchers.filter { it.voucher.type == "Payment" }.forEach { vwe ->
                    val hasCashCredit = vwe.voucherEntries.any { !it.isDebit && allLedgers.find { l -> l.id == it.ledgerId }?.group?.lowercase() == "cash-in-hand" }
                    if (hasCashCredit) {
                        val day = getStartOfDay(vwe.voucher.date)
                        vwe.voucherEntries.filter { it.isDebit }.forEach { entry ->
                            val key = Pair(day, entry.ledgerId)
                            if (!dailyCashPayments.containsKey(key)) {
                                dailyCashPayments[key] = mutableListOf()
                            }
                            dailyCashPayments[key]!!.add(vwe)
                        }
                    }
                }
                val aggregateCashBreaches = mutableListOf<AuditFailedVoucher>()
                dailyCashPayments.forEach { (key, vouchers) ->
                    val (day, ledgerId) = key
                    val partyName = allLedgers.find { it.id == ledgerId }?.name ?: "Unknown"
                    val totalPaidOnDay = vouchers.sumOf { vwe ->
                        vwe.voucherEntries.filter { it.ledgerId == ledgerId && it.isDebit }.sumOf { it.amount }
                    }.paiseToRupees()
                    if (totalPaidOnDay > 10000.0) {
                        vouchers.forEach { vwe ->
                            if (aggregateCashBreaches.none { it.voucherId == vwe.voucher.id }) {
                                val dateStr = sdf.format(vwe.voucher.date)
                                aggregateCashBreaches.add(AuditFailedVoucher(
                                    voucherId = vwe.voucher.id,
                                    displayText = "${vwe.voucher.voucherNumber} (Aggregate Cash) - ₹${String.format("%.2f", totalPaidOnDay)}",
                                    reason = "Aggregate cash payments to $partyName on $dateStr is ₹${String.format("%.2f", totalPaidOnDay)}, exceeding Section 40A(3) ₹10k limit.",
                                    suggestion = "Split cash payments across days or pay via Bank transfer."
                                ))
                            }
                        }
                    }
                }

                if (cashPayLimitBreaches.isNotEmpty() || aggregateCashBreaches.isNotEmpty()) {
                    val list = (cashPayLimitBreaches.map { vwe ->
                        val amt = vwe.voucherEntries.filter { e -> e.isDebit }.sumOf { e -> e.amount }.paiseToRupees()
                        AuditFailedVoucher(
                            voucherId = vwe.voucher.id,
                            displayText = "${vwe.voucher.voucherNumber} (Cash Payment) - ₹${String.format("%.2f", amt)}",
                            reason = "Cash payment of ₹${String.format("%.2f", amt)} exceeds Section 40A(3) single-person limit of ₹10k.",
                            suggestion = "Pay via bank account or divide the transaction."
                        )
                    } + aggregateCashBreaches).distinctBy { it.voucherId }

                    checks.add(PreCheckItem(
                        title = "Section 40A(3) Cash Limits",
                        statusText = "Warning: ${list.size} cash payments violate the ₹10k single or daily limit.",
                        isPass = false,
                        failedVouchers = list
                    ))
                    deductions += 150
                } else {
                    checks.add(PreCheckItem(
                        title = "Section 40A(3) Cash Limits",
                        statusText = "Pass: All cash payments are within compliance limits.",
                        isPass = true
                    ))
                }

                // B. Section 269ST Single-Day Cash Receipt Limit
                val cashReceiptBreaches = scopeVouchers.filter { v ->
                    v.voucher.type == "Receipt" && v.voucherEntries.any {
                        val ledg = allLedgers.find { l -> l.id == it.ledgerId }
                        ledg?.group?.lowercase() == "cash-in-hand" && it.amount.paiseToRupees() >= 200000.0
                    }
                }
                
                // Aggregate daily cash receipts
                val dailyCashReceipts = mutableMapOf<Pair<Long, Int>, MutableList<VoucherWithAllEntries>>()
                scopeVouchers.filter { it.voucher.type == "Receipt" }.forEach { vwe ->
                    val hasCashDebit = vwe.voucherEntries.any { it.isDebit && allLedgers.find { l -> l.id == it.ledgerId }?.group?.lowercase() == "cash-in-hand" }
                    if (hasCashDebit) {
                        val day = getStartOfDay(vwe.voucher.date)
                        vwe.voucherEntries.filter { !it.isDebit }.forEach { entry ->
                            val key = Pair(day, entry.ledgerId)
                            if (!dailyCashReceipts.containsKey(key)) {
                                dailyCashReceipts[key] = mutableListOf()
                            }
                            dailyCashReceipts[key]!!.add(vwe)
                        }
                    }
                }
                val aggregateReceiptBreaches = mutableListOf<AuditFailedVoucher>()
                dailyCashReceipts.forEach { (key, vouchers) ->
                    val (day, ledgerId) = key
                    val partyName = allLedgers.find { it.id == ledgerId }?.name ?: "Unknown"
                    val totalRecOfDay = vouchers.sumOf { vwe ->
                        vwe.voucherEntries.filter { it.ledgerId == ledgerId && !it.isDebit }.sumOf { it.amount }
                    }.paiseToRupees()
                    if (totalRecOfDay >= 200000.0) {
                        vouchers.forEach { vwe ->
                            if (aggregateReceiptBreaches.none { it.voucherId == vwe.voucher.id }) {
                                val dateStr = sdf.format(vwe.voucher.date)
                                aggregateReceiptBreaches.add(AuditFailedVoucher(
                                    voucherId = vwe.voucher.id,
                                    displayText = "${vwe.voucher.voucherNumber} (Aggregate Receipt) - ₹${String.format("%.2f", totalRecOfDay)}",
                                    reason = "Aggregate cash receipts from $partyName on $dateStr is ₹${String.format("%.2f", totalRecOfDay)}, violating Section 269ST ₹2 Lakh limit.",
                                    suggestion = "Accept payments via Cheque/Bank transfer."
                                ))
                            }
                        }
                    }
                }

                if (cashReceiptBreaches.isNotEmpty() || aggregateReceiptBreaches.isNotEmpty()) {
                    val list = (cashReceiptBreaches.map { vwe ->
                        val amt = vwe.voucherEntries.filter { e -> e.isDebit }.sumOf { e -> e.amount }.paiseToRupees()
                        AuditFailedVoucher(
                            voucherId = vwe.voucher.id,
                            displayText = "${vwe.voucher.voucherNumber} (Cash Receipt) - ₹${String.format("%.2f", amt)}",
                            reason = "Single cash receipt of ₹${String.format("%.2f", amt)} exceeds Section 269ST limit of ₹2 Lakh.",
                            suggestion = "Accept funds via bank transfer only."
                        )
                    } + aggregateReceiptBreaches).distinctBy { it.voucherId }

                    checks.add(PreCheckItem(
                        title = "Section 269ST Cash Limit",
                        statusText = "Risk: ${list.size} cash receipts exceed legal ₹2 Lakh single or daily limit.",
                        isPass = false,
                        failedVouchers = list
                    ))
                    deductions += 250
                } else {
                    checks.add(PreCheckItem(
                        title = "Section 269ST Cash Limit",
                        statusText = "Pass: No cash receipts violate the ₹2 Lakh audit threshold.",
                        isPass = true
                    ))
                }

                // C. Negative Cash account warning
                val failedCashVouchers = mutableListOf<AuditFailedVoucher>()
                if (cashInHand < 0) {
                    val cashPayments = scopeVouchers.filter { v ->
                        v.voucher.type == "Payment" && v.voucherEntries.any {
                            val ledg = allLedgers.find { l -> l.id == it.ledgerId }
                            ledg?.group?.lowercase() == "cash-in-hand"
                        }
                    }
                    cashPayments.forEach { vwe ->
                        val amt = vwe.voucherEntries.filter { e -> e.isDebit }.sumOf { e -> e.amount }.paiseToRupees()
                        failedCashVouchers.add(AuditFailedVoucher(
                            voucherId = vwe.voucher.id,
                            displayText = "${vwe.voucher.voucherNumber} (Negative Cash) - ₹${String.format("%.2f", amt)}",
                            reason = "Cash balance is negative (-₹${String.format("%.2f", abs(cashInHand))}). This payment makes it worse.",
                            suggestion = "Add missing cash receipts or cash injections to correct the cash balance."
                        ))
                    }
                }
                if (failedCashVouchers.isNotEmpty()) {
                    checks.add(PreCheckItem(
                        title = "Negative Cash Verification",
                        statusText = "Warning: Cash accounts have a negative balance of ₹${String.format("%.2f", abs(cashInHand))}.",
                        isPass = false,
                        failedVouchers = failedCashVouchers
                    ))
                    deductions += 100
                } else {
                    checks.add(PreCheckItem(
                        title = "Negative Cash Verification",
                        statusText = "Pass: Cash accounts are positive.",
                        isPass = true
                    ))
                }

                // D. Double Entry Alignment check
                val unbalanced = scopeVouchers.filter { v ->
                    val debits = v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                    val credits = v.voucherEntries.filter { !it.isDebit }.sumOf { it.amount }.paiseToRupees()
                    abs(debits - credits) > 0.01
                }
                if (unbalanced.isNotEmpty()) {
                    val list = unbalanced.map { vwe ->
                        val debits = vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                        val credits = vwe.voucherEntries.filter { !it.isDebit }.sumOf { it.amount }.paiseToRupees()
                        val diff = abs(debits - credits)
                        AuditFailedVoucher(
                            voucherId = vwe.voucher.id,
                            displayText = "${vwe.voucher.voucherNumber} - Diff: ₹${String.format("%.2f", diff)}",
                            reason = "Debit (₹${String.format("%.2f", debits)}) does not match Credit (₹${String.format("%.2f", credits)}). Mismatch is ₹${String.format("%.2f", diff)}.",
                            suggestion = "Ensure total debits equal total credits."
                        )
                    }
                    checks.add(PreCheckItem(
                        title = "Double-Entry Matching",
                        statusText = "Error: ${unbalanced.size} vouchers have unbalanced debits and credits.",
                        isPass = false,
                        failedVouchers = list
                    ))
                    deductions += 150
                } else {
                    checks.add(PreCheckItem(
                        title = "Double-Entry Matching",
                        statusText = "Pass: All ledger entries are balanced.",
                        isPass = true
                    ))
                }

                // E. Narration completeness
                val missingNarration = scopeVouchers.filter { it.voucher.narration.isNullOrBlank() }
                if (missingNarration.isNotEmpty()) {
                    val list = missingNarration.map { vwe ->
                        AuditFailedVoucher(
                            voucherId = vwe.voucher.id,
                            displayText = "${vwe.voucher.voucherNumber} (${vwe.voucher.type})",
                            reason = "This transaction does not contain any explanation or narration.",
                            suggestion = "Add a short description explaining the nature of the entry."
                        )
                    }
                    checks.add(PreCheckItem(
                        title = "Narration Audit",
                        statusText = "Warning: ${missingNarration.size} vouchers lack narrative descriptions.",
                        isPass = false,
                        failedVouchers = list
                    ))
                    deductions += 50
                } else {
                    checks.add(PreCheckItem(
                        title = "Narration Audit",
                        statusText = "Pass: All transactions have narratives.",
                        isPass = true
                    ))
                }

                // F. GST Rate & Mismatch Audit
                val gstMismatchList = mutableListOf<AuditFailedVoucher>()
                val missingTaxList = mutableListOf<AuditFailedVoucher>()
                scopeVouchers.filter { it.voucher.type == "Sales" || it.voucher.type == "Purchase" }.forEach { vwe ->
                    val invEntries = vwe.inventoryEntries
                    if (invEntries.isNotEmpty()) {
                        val expectedTaxSum = invEntries.sumOf { it.taxAmount }.paiseToRupees()
                        val actualTaxSum = GstComplianceRules.voucherTaxAmount(vwe, allLedgers)

                        val hasGstItems = invEntries.any { it.gstRate > 0.0 }
                        if (hasGstItems && actualTaxSum < 0.01) {
                            missingTaxList.add(AuditFailedVoucher(
                                voucherId = vwe.voucher.id,
                                displayText = "${vwe.voucher.voucherNumber} (Missing Tax Ledger)",
                                reason = "Voucher contains taxable stock items, but no $taxEntriesLabel tax ledger is added.",
                                suggestion = "Add a tax ledger entry to charge appropriate $taxLabel."
                            ))
                        } else if (abs(expectedTaxSum - actualTaxSum) > 2.00) {
                            gstMismatchList.add(AuditFailedVoucher(
                                voucherId = vwe.voucher.id,
                                displayText = "${vwe.voucher.voucherNumber} ($taxLabel Mismatch)",
                                reason = "Item $taxLabel totals $currencySymbol${String.format("%.2f", expectedTaxSum)}, but tax ledger entries sum to $currencySymbol${String.format("%.2f", actualTaxSum)}.",
                                suggestion = "Adjust the tax ledger entry amount to match item $taxLabel computation."
                            ))
                        }
                    }
                }
                if (gstMismatchList.isNotEmpty() || missingTaxList.isNotEmpty()) {
                    checks.add(PreCheckItem(
                        title = "$taxLabel Compliance Audit",
                        statusText = "Warning: Mismatch or missing tax ledgers in $taxLabel vouchers.",
                        isPass = false,
                        failedVouchers = gstMismatchList + missingTaxList
                    ))
                    deductions += 100
                } else {
                    checks.add(PreCheckItem(
                        title = "$taxLabel Compliance Audit",
                        statusText = "Pass: All sales and purchases have correct $taxLabel rates applied.",
                        isPass = true
                    ))
                }

                // G. HSN/SAC Code Missing Audit
                val hsnMissingVouchers = scopeVouchers.filter { vwe ->
                    (vwe.voucher.type == "Sales" || vwe.voucher.type == "Purchase") && vwe.inventoryEntries.any { it.hsnCode.isNullOrBlank() }
                }
                val hsnMissingList = mutableListOf<AuditFailedVoucher>()
                if (hsnMissingVouchers.isNotEmpty()) {
                    val allStockItems = db.stockItemDao().getAllStockItems(activeCompanyId)
                    hsnMissingVouchers.forEach { vwe ->
                        val entryWithMissingHsn = vwe.inventoryEntries.find { it.hsnCode.isNullOrBlank() }
                        val itemName = allStockItems.find { it.id == entryWithMissingHsn?.stockItemId }?.name ?: "Stock Item"
                        hsnMissingList.add(AuditFailedVoucher(
                            voucherId = vwe.voucher.id,
                            displayText = "${vwe.voucher.voucherNumber} - Missing $taxCodeLabel",
                            reason = "Invoice contains item '$itemName' which lacks a valid $taxCodeLabel.",
                            suggestion = "Open the item in Inventory and add its $taxCodeLabel.",
                            stockItemId = entryWithMissingHsn?.stockItemId
                        ))
                    }
                }
                if (hsnMissingList.isNotEmpty()) {
                    checks.add(PreCheckItem(
                        title = "$taxCodeLabel Audit",
                        statusText = "Warning: ${hsnMissingList.size} transaction entries are missing ${taxCodeLabel}s.",
                        isPass = false,
                        failedVouchers = hsnMissingList
                    ))
                    deductions += 50
                } else {
                    checks.add(PreCheckItem(
                        title = "$taxCodeLabel Audit",
                        statusText = "Pass: All transaction inventory lines contain ${taxCodeLabel}s.",
                        isPass = true
                    ))
                }

                // H. Suspense Account Usage Audit
                val suspenseVouchers = scopeVouchers.filter { vwe ->
                    vwe.voucherEntries.any { entry ->
                        val ledg = allLedgers.find { l -> l.id == entry.ledgerId }
                        ledg?.group?.lowercase()?.contains("suspense") == true || ledg?.name?.lowercase()?.contains("suspense") == true
                    }
                }
                val suspenseFailedList = suspenseVouchers.map { vwe ->
                    val entry = vwe.voucherEntries.find { e ->
                        val ledg = allLedgers.find { l -> l.id == e.ledgerId }
                        ledg?.group?.lowercase()?.contains("suspense") == true || ledg?.name?.lowercase()?.contains("suspense") == true
                    }
                    val ledgerName = allLedgers.find { it.id == entry?.ledgerId }?.name ?: "Suspense"
                    AuditFailedVoucher(
                        voucherId = vwe.voucher.id,
                        displayText = "${vwe.voucher.voucherNumber} (Suspense Entry)",
                        reason = "Transaction posted to Suspense ledger '$ledgerName'. Suspense account must be cleared.",
                        suggestion = "Audit the transaction and assign the correct ledger."
                    )
                }
                if (suspenseFailedList.isNotEmpty()) {
                    checks.add(PreCheckItem(
                        title = "Suspense Account Audit",
                        statusText = "Warning: Suspense account entries require clearing.",
                        isPass = false,
                        failedVouchers = suspenseFailedList
                    ))
                    deductions += 100
                } else {
                    checks.add(PreCheckItem(
                        title = "Suspense Account Audit",
                        statusText = "Pass: Suspense accounts have zero balance and no entries.",
                        isPass = true
                    ))
                }

                // I. Duplicate Voucher Number Audit
                val duplicateNumberList = mutableListOf<AuditFailedVoucher>()
                val groupedByNum = scopeVouchers.groupBy { it.voucher.type to it.voucher.voucherNumber }
                groupedByNum.forEach { (typeAndNum, list) ->
                    val (type, num) = typeAndNum
                    if (list.size > 1 && num.isNotBlank()) {
                        list.forEach { vwe ->
                            duplicateNumberList.add(AuditFailedVoucher(
                                voucherId = vwe.voucher.id,
                                displayText = "${vwe.voucher.voucherNumber} (Duplicate $type)",
                                reason = "Voucher number '$num' is duplicate. Used in ${list.size} '$type' vouchers.",
                                suggestion = "Assign a unique voucher number to each voucher entry."
                            ))
                        }
                    }
                }
                if (duplicateNumberList.isNotEmpty()) {
                    checks.add(PreCheckItem(
                        title = "Voucher Duplicate Check",
                        statusText = "Warning: Duplicate voucher numbers exist within same type.",
                        isPass = false,
                        failedVouchers = duplicateNumberList
                    ))
                    deductions += 50
                } else {
                    checks.add(PreCheckItem(
                        title = "Voucher Duplicate Check",
                        statusText = "Pass: All voucher numbers are unique.",
                        isPass = true
                    ))
                }

                // J. GST compliance thresholds — registration / composition / GSTR-9C audit
                if (isIndia) {
                    val turnover = GstComplianceRules.annualTurnover(scopeVouchers)
                    val outTax = GstComplianceRules.outputTax(scopeVouchers)
                    val inTax = GstComplianceRules.inputTax(scopeVouchers)
                    val hasGstin = !currentCompany?.gstin.isNullOrBlank()
                    val netLiability = outTax - inTax

                    checks.add(PreCheckItem(
                        title = "$taxLabel Net Liability",
                        statusText = "Output tax $currencySymbol${String.format("%.2f", outTax)} − Input tax $currencySymbol${String.format("%.2f", inTax)} = Net $currencySymbol${String.format("%.2f", netLiability)}.",
                        isPass = true
                    ))

                    GstComplianceRules.runAll(turnover, outTax, inTax, hasGstin).forEach { finding ->
                        when (finding.severity) {
                            GstComplianceRules.Severity.VIOLATION -> deductions += 150
                            GstComplianceRules.Severity.WARNING -> deductions += 75
                            GstComplianceRules.Severity.INFO -> deductions += 0
                        }
                        checks.add(PreCheckItem(
                            title = finding.title,
                            statusText = finding.message,
                            isPass = finding.severity == GstComplianceRules.Severity.INFO,
                            failedVouchers = emptyList()
                        ))
                    }
                }

                val finalScore = (1000 - deductions).coerceIn(100, 1000)

                _uiState.value = CaScrutinyUiState(
                    auditScore = finalScore,
                    preChecks = checks,
                    trialBalance = trialBalance,
                    pnlRows = pnlRows,
                    balanceSheetRows = balanceSheetRows,
                    attachmentsCount = attachments.size,
                    allLedgers = allLedgers
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun updateVoucher(
        voucher: com.minhas.teller.data.entities.Voucher,
        entries: List<com.minhas.teller.data.entities.VoucherEntry>,
        inventoryEntries: List<com.minhas.teller.data.entities.InventoryEntry> = emptyList()
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                voucherRepository.updateVoucherWithAllEntries(voucher, entries, inventoryEntries)
                loadComplianceData(activeCompanyId, 0L, System.currentTimeMillis())
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun attachEvidenceToVoucher(voucherId: Int, companyId: Int, fileUri: String, ocrText: String? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            val attachment = EvidenceAttachment(
                voucherId = voucherId,
                companyId = companyId,
                fileUri = fileUri,
                ocrText = ocrText
            )
            db.evidenceAttachmentDao().insert(attachment)
            loadComplianceData(companyId, 0L, System.currentTimeMillis()) // reload
        }
    }
 
    fun generateCaPack(context: Context, onComplete: (File) -> Unit) {
        val company = currentCompany ?: return
        _uiState.value = _uiState.value.copy(isGenerating = true)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                ExportUtils.exportCaCompliancePack(
                    context = context,
                    company = company,
                    fyPeriod = fyPeriodStr,
                    trialBalance = _uiState.value.trialBalance,
                    pnlRows = _uiState.value.pnlRows,
                    balanceSheetRows = _uiState.value.balanceSheetRows,
                    auditScore = _uiState.value.auditScore,
                    preChecks = _uiState.value.preChecks.map { check ->
                        val details = if (check.failedVouchers.isNotEmpty()) {
                            "\nFailed Vouchers:\n" + check.failedVouchers.joinToString("\n") { " - ${it.displayText}" }
                        } else ""
                        check.title to (check.statusText + details)
                    },
                    onComplete = { zipFile ->
                        viewModelScope.launch(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(isGenerating = false, generatedPackFile = zipFile)
                            onComplete(zipFile)
                        }
                        kotlin.Unit
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isGenerating = false, error = e.localizedMessage)
                }
            }
        }
    }
 
    fun generateScrutinyPack(context: Context, onComplete: (File) -> Unit) {
        val company = currentCompany ?: return
        _uiState.value = _uiState.value.copy(isGenerating = true)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val attachments = db.evidenceAttachmentDao().getAttachmentsForCompany(activeCompanyId)
                val auditLogs = db.auditLogDao().getLogsForCompany(activeCompanyId)
 
                ExportUtils.exportScrutinyPack(
                    context = context,
                    company = company,
                    fyPeriod = fyPeriodStr,
                    vouchers = vouchersScope,
                    evidenceAttachments = attachments,
                    auditLogs = auditLogs,
                    onComplete = { zipFile ->
                        viewModelScope.launch(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(isGenerating = false, generatedPackFile = zipFile)
                            onComplete(zipFile)
                        }
                        kotlin.Unit
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isGenerating = false, error = e.localizedMessage)
                }
            }
        }
    }
}

class CaScrutinyViewModelFactory(
    private val db: AppDatabase,
    private val voucherRepository: VoucherRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CaScrutinyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CaScrutinyViewModel(db, voucherRepository, ledgerRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
