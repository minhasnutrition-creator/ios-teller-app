package com.minhas.teller.data.repositories

import androidx.room.withTransaction
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.dao.BillAllocationDao
import com.minhas.teller.data.dao.VoucherDao
import com.minhas.teller.data.dao.VoucherEntryDao
import com.minhas.teller.data.dao.InventoryEntryDao
import com.minhas.teller.data.dao.LedgerDao
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.entities.BillAllocation
import com.minhas.teller.data.entities.AuditLog
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.utils.InterestCalculator
import com.minhas.teller.utils.paiseToRupees
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID
import kotlin.math.abs

data class PaymentHistoryItem(
    val amount: Double,
    val date: Date,
    val type: String = "Receipt"
)

data class OutstandingVoucher(
    val voucherId: Int,
    val voucherNumber: String,
    val date: Date,
    val originalAmount: Double,
    val pendingAmount: Double,
    val type: String,
    val ledgerId: Int,
    val isDebit: Boolean = true,
    val totalReceived: Double = 0.0,
    val lastPaymentDate: Date? = null,
    val paymentHistory: List<PaymentHistoryItem> = emptyList(),
    val billRefName: String? = null,
    val refDisplayName: String? = null,
    val customerName: String? = null,
    val interestRate: Double = 0.0,
    val interestDays: Int = 0,
    val interestAmount: Double = 0.0
)

class VoucherRepository(
    private val voucherDao: VoucherDao,
    private val voucherEntryDao: VoucherEntryDao,
    private val inventoryEntryDao: InventoryEntryDao,
    private val billAllocationDao: BillAllocationDao,
    private val auditLogDao: com.minhas.teller.data.dao.AuditLogDao,
    private val ledgerDao: LedgerDao,
    private val db: AppDatabase
) {
    suspend fun getAllVouchersWithAllEntries(companyId: Int): List<VoucherWithAllEntries> {
        return voucherDao.getAllVouchersWithAllEntries(companyId)
    }

    suspend fun getAllVouchersWithAllEntriesBetweenDates(companyId: Int, startDate: Date, endDate: Date): List<VoucherWithAllEntries> {
        return voucherDao.getAllVouchersWithAllEntriesBetweenDates(companyId, startDate, endDate)
    }

    suspend fun getVoucherWithAllEntriesById(id: Int, companyId: Int? = null): VoucherWithAllEntries? {
        val voucher = voucherDao.getVoucherWithAllEntriesById(id)
        if (companyId != null && voucher?.voucher?.companyId != companyId) {
            return null
        }
        return voucher
    }

    suspend fun getVouchersWithAllEntriesForLedger(companyId: Int, ledgerId: Int): List<VoucherWithAllEntries> {
        return voucherDao.getAllVouchersWithAllEntriesForLedger(companyId, ledgerId)
    }

    suspend fun getVouchersWithAllEntriesForLedgerBetweenDates(companyId: Int, ledgerId: Int, startDate: Date, endDate: Date): List<VoucherWithAllEntries> {
        return voucherDao.getAllVouchersWithAllEntriesForLedgerBetweenDates(companyId, ledgerId, startDate, endDate)
    }

    suspend fun getAllBillAllocations(companyId: Int): List<BillAllocation> {
        return billAllocationDao.getAllAllocationsForCompany(companyId)
    }

    /** Reject unbalanced vouchers (total debit != total credit) before they corrupt the books. */
    private fun assertBalanced(entries: List<VoucherEntry>) {
        val debitSum = entries.filter { it.isDebit }.sumOf { it.amount }
        val creditSum = entries.filter { !it.isDebit }.sumOf { it.amount }
        // Compare in paise so a full-paisa imbalance is rejected, while float epsilon (<< 0.005)
        // from summing a few doubles is still tolerated.
        val debitPaise = kotlin.math.round(debitSum * 100.0)
        val creditPaise = kotlin.math.round(creditSum * 100.0)
        if (debitPaise != creditPaise) {
            throw IllegalArgumentException("Unbalanced voucher: debit ₹$debitSum vs credit ₹$creditSum")
        }
    }

    suspend fun insertVoucherWithAllEntries(
        voucher: Voucher,
        voucherEntries: List<VoucherEntry>,
        inventoryEntries: List<InventoryEntry>,
        billAllocations: List<BillAllocation> = emptyList()
    ): Long {
        assertBalanced(voucherEntries)
        val txBlock: suspend () -> Long = {
            val stamped = voucher.copy(
                uid = voucher.uid ?: UUID.randomUUID().toString(),
                updatedAt = System.currentTimeMillis()
            )
            val voucherId = voucherDao.insert(stamped)
            val voucherEntriesWithVoucherId = voucherEntries.map { it.copy(voucherId = voucherId.toInt()) }
            val inventoryEntriesWithVoucherId = inventoryEntries.map { it.copy(voucherId = voucherId.toInt()) }

            inventoryEntryDao.insertAll(inventoryEntriesWithVoucherId)

            var matchedAllocations = 0
            voucherEntriesWithVoucherId.forEach { entry ->
                val entryId = voucherEntryDao.insert(entry)
                // billAllocations arrive with a temporary ledgerId stuffed into voucherEntryId;
                // match them here and surface any that silently drop (or double-match).
                val allocs = billAllocations.filter { it.voucherEntryId == entry.ledgerId }
                matchedAllocations += allocs.size
                allocs.forEach { alloc ->
                    billAllocationDao.insert(alloc.copy(voucherEntryId = entryId.toInt(), id = 0))
                }
            }
            if (matchedAllocations != billAllocations.size) {
                android.util.Log.w("VoucherRepo", "Bill allocation mismatch: ${billAllocations.size} passed but $matchedAllocations matched a voucher entry (ledgerId keying)")
            }

            auditLogDao.insert(
                AuditLog(
                    action = "INSERT",
                    entityType = "VOUCHER",
                    entityId = voucherId.toString(),
                    details = "Created ${voucher.type} Voucher #${voucher.voucherNumber}",
                    companyId = voucher.companyId
                )
            )

            voucherId
        }
        val resultId = db.withTransaction { txBlock() }
        com.minhas.teller.utils.CloudAutoBackup.trigger()
        return resultId
    }

    suspend fun updateVoucherWithAllEntries(
        voucher: Voucher,
        voucherEntries: List<VoucherEntry>,
        inventoryEntries: List<InventoryEntry>,
        billAllocations: List<BillAllocation> = emptyList()
    ) {
        assertBalanced(voucherEntries)
        val txBlock: suspend () -> Unit = {
            // voucherDao.update writes every column, so a caller that rebuilt the header
            // without a companyId (leaving the 0 default) would silently move the voucher
            // out of its company — it stays in the table but disappears from every
            // company-scoped query. Fall back to the stored owner in that case.
            val stored = voucherDao.getVoucherById(voucher.id)
            val safeVoucher = if (voucher.companyId == 0 && stored != null && stored.companyId != 0) {
                voucher.copy(companyId = stored.companyId)
            } else voucher
            val stamped = safeVoucher.copy(
                uid = safeVoucher.uid ?: stored?.uid ?: UUID.randomUUID().toString(),
                updatedAt = System.currentTimeMillis()
            )
            voucherDao.update(stamped)

            val oldEntries = voucherEntryDao.getVoucherEntriesForVoucher(voucher.id)
            oldEntries.forEach { entry ->
                billAllocationDao.deleteAllocationsByEntryId(entry.id)
            }
            voucherEntryDao.deleteVoucherEntriesForVoucher(voucher.id)
            inventoryEntryDao.deleteInventoryEntriesForVoucher(voucher.id)

            inventoryEntryDao.insertAll(inventoryEntries.map { it.copy(voucherId = voucher.id, id = 0) })

            var matchedAllocations = 0
            voucherEntries.map { it.copy(voucherId = voucher.id, id = 0) }.forEach { entry ->
                val entryId = voucherEntryDao.insert(entry)
                val allocs = billAllocations.filter { it.voucherEntryId == entry.ledgerId }
                matchedAllocations += allocs.size
                allocs.forEach { alloc ->
                    billAllocationDao.insert(alloc.copy(voucherEntryId = entryId.toInt(), id = 0))
                }
            }
            if (matchedAllocations != billAllocations.size) {
                android.util.Log.w("VoucherRepo", "Bill allocation mismatch: ${billAllocations.size} passed but $matchedAllocations matched a voucher entry (ledgerId keying)")
            }

            auditLogDao.insert(
                AuditLog(
                    action = "UPDATE",
                    entityType = "VOUCHER",
                    entityId = voucher.id.toString(),
                    details = "Updated ${voucher.type} Voucher #${voucher.voucherNumber}",
                    companyId = safeVoucher.companyId
                )
            )
        }
        db.withTransaction { txBlock() }
        com.minhas.teller.utils.CloudAutoBackup.trigger()
    }

    suspend fun deleteVoucherWithAllEntries(voucher: Voucher) {
        val txBlock: suspend () -> Unit = {
            val entries = voucherEntryDao.getVoucherEntriesForVoucher(voucher.id)
            entries.forEach { entry ->
                billAllocationDao.deleteAllocationsByEntryId(entry.id)
            }
            // Delete entries BEFORE the voucher to maintain referential integrity
            voucherEntryDao.deleteVoucherEntriesForVoucher(voucher.id)
            inventoryEntryDao.deleteInventoryEntriesForVoucher(voucher.id)
            // Soft-delete the voucher header so the tombstone can propagate via cloud sync.
            voucherDao.update(voucher.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))

            auditLogDao.insert(
                AuditLog(
                    action = "DELETE",
                    entityType = "VOUCHER",
                    entityId = voucher.id.toString(),
                    details = "Deleted ${voucher.type} Voucher #${voucher.voucherNumber}",
                    companyId = voucher.companyId
                )
            )
        }
        db.withTransaction { txBlock() }
        com.minhas.teller.utils.CloudAutoBackup.trigger()
    }

    @androidx.annotation.VisibleForTesting
    internal fun isOutstandingEligible(group: String): Boolean = Companion.isOutstandingEligible(group)

    private fun isPartyLedger(group: String): Boolean = isOutstandingEligible(group)

    @androidx.annotation.VisibleForTesting
    internal fun isPartyLedgerName(name: String, group: String = ""): Boolean = Companion.isPartyLedgerName(name, group)

    suspend fun getOutstandingVouchers(ledgerId: Int): List<OutstandingVoucher> = withContext(Dispatchers.Default) {
        val ledger = try { ledgerDao.getLedgerById(ledgerId).first() } catch (e: Exception) { return@withContext emptyList() }
        if (!isOutstandingEligible(ledger.group) || !isPartyLedger(ledger.group) || !isPartyLedgerName(ledger.name, ledger.group)) return@withContext emptyList()
        val companyId = ledger.companyId

        val allAllocations = billAllocationDao.getAllAllocationsForCompany(companyId)
        val vouchers = voucherDao.getAllVouchersWithAllEntries(companyId)

        val entryDetailsMap = mutableMapOf<Int, Pair<Date, String>>()
        vouchers.forEach { v ->
            v.voucherEntries.forEach { entry ->
                entryDetailsMap[entry.id] = Pair(v.voucher.date, v.voucher.type)
            }
        }

        val allocationsByBill = allAllocations
            .filter { it.refVoucherId != null }
            .groupBy { it.refVoucherId!! }
            .mapValues { (_, allocs) ->
                val total = allocs.sumOf { it.amount }.paiseToRupees()
                val history = allocs.mapNotNull {
                    val details = entryDetailsMap[it.voucherEntryId]
                    if (details != null) PaymentHistoryItem(it.amount.paiseToRupees(), details.first, details.second) else null
                }.sortedByDescending { it.date }

                val lastDate = history.firstOrNull()?.date
                Triple(total, lastDate, history)
            }

        computeOutstandingForLedgerFromVouchers(
            ledgerId = ledgerId,
            ledger = ledger,
            vouchers = vouchers,
            allocationsByBill = allocationsByBill,
            allAllocations = allAllocations
        )
    }

    suspend fun getAllOutstandingVouchers(companyId: Int): List<OutstandingVoucher> = withContext(Dispatchers.Default) {
        val ledgers = ledgerDao.getAllLedgersSync(companyId)
        val allAllocations = billAllocationDao.getAllAllocationsForCompany(companyId)
        val vouchers = voucherDao.getAllVouchersWithAllEntries(companyId)

        val entryDetailsMap = mutableMapOf<Int, Pair<Date, String>>()
        vouchers.forEach { v ->
            v.voucherEntries.forEach { entry ->
                entryDetailsMap[entry.id] = Pair(v.voucher.date, v.voucher.type)
            }
        }

        val allocationsByBill = allAllocations
            .filter { it.refVoucherId != null }
            .groupBy { it.refVoucherId!! }
            .mapValues { (_, allocs) ->
                val total = allocs.sumOf { it.amount }.paiseToRupees()
                val history = allocs.mapNotNull {
                    val details = entryDetailsMap[it.voucherEntryId]
                    if (details != null) PaymentHistoryItem(it.amount.paiseToRupees(), details.first, details.second) else null
                }.sortedByDescending { it.date }

                val lastDate = history.firstOrNull()?.date
                Triple(total, lastDate, history)
            }

        ledgers
            .asSequence()
            .filter { isOutstandingEligible(it.group) && isPartyLedger(it.group) && isPartyLedgerName(it.name, it.group) }
            .flatMap { ledger ->
                computeOutstandingForLedgerFromVouchers(
                    ledgerId = ledger.id,
                    ledger = ledger,
                    vouchers = vouchers,
                    allocationsByBill = allocationsByBill,
                    allAllocations = allAllocations
                ).asSequence()
            }
            .toList()
    }

    suspend fun getTotalSales(companyId: Int): Long = voucherDao.sumVoucherEntryAmountsByType(companyId, "Sales")

    suspend fun getTotalPurchase(companyId: Int): Long = voucherDao.sumVoucherEntryAmountsByType(companyId, "Purchase")

    suspend fun getTotalReceipt(companyId: Int): Long = voucherDao.sumVoucherEntryAmountsByType(companyId, "Receipt")

    suspend fun getTotalPayment(companyId: Int): Long = voucherDao.sumVoucherEntryAmountsByType(companyId, "Payment")

    fun getBillAllocationsForVoucher(voucherId: Int) = billAllocationDao.getAllocationsForVoucher(voucherId)

    companion object {
        /** Pure logic — does not need a database. Testable in JVM unit tests. */
        @androidx.annotation.VisibleForTesting
        @JvmStatic
        fun isOutstandingEligible(group: String): Boolean {
            val g = group.trim().lowercase()
            if (g == "finance companies" || g.startsWith("finance comp")) return true
            val excluded = listOf(
                "sales", "purchase", "expense", "income", "revenue", "cash", "bank",
                "capital", "tax", "gst", "vat", "duty", "duties", "stock", "inventory",
                "direct", "indirect", "fixed assets", "current assets", "current liabilities",
                "loans (liability)", "loans & advances (asset)", "suspense", "misc"
            )
            if (excluded.any { g.contains(it) }) {
                if (g.contains("loan") && (g.contains("liability") || g.contains("asset") || g.contains("finance"))) {
                    // Allow loan-related groups
                } else {
                    return false
                }
            }
            val party = listOf("debtor", "creditor", "supplier", "customer", "finance", "payable", "receivable", "loan")
            return party.any { g.contains(it) }
        }

        @androidx.annotation.VisibleForTesting
        @JvmStatic
        fun isPartyLedgerName(name: String, group: String = ""): Boolean {
            val g = group.trim().lowercase()
            if (g == "finance companies" || g.startsWith("finance comp")) return true
            val n = name.trim().lowercase()
            val excluded = listOf(
                "sales", "purchase", "purchases", "expense", "expenses", "income", "incomes", "revenue", "cash",
                "capital", "tax", "gst", "vat", "duty", "duties", "stock", "stocks", "inventory",
                "profit", "loss", "depreciation", "opening", "closing", "cgst", "sgst", "igst"
            )
            return !excluded.any { containsWord(n, it) }
        }

        /** True if [word] appears in [text] as a whole word (bounded by non-alphanumeric chars). */
        private fun containsWord(text: String, word: String): Boolean {
            if (word.isBlank()) return false
            val idx = text.indexOf(word)
            if (idx < 0) return false
            val beforeOk = idx == 0 || !text[idx - 1].isLetterOrDigit()
            val after = idx + word.length
            val afterOk = after >= text.length || !text[after].isLetterOrDigit()
            return beforeOk && afterOk
        }

        /** Pure logic — computes pending/outstanding amounts from vouchers + bill allocations. Testable in JVM unit tests. */
        @JvmStatic
        fun computeOutstandingForLedgerFromVouchers(
            ledgerId: Int,
            ledger: Ledger,
            vouchers: List<VoucherWithAllEntries>,
            allocationsByBill: Map<Int, Triple<Double, Date?, List<PaymentHistoryItem>>>,
            allAllocations: List<BillAllocation> = emptyList()
        ): List<OutstandingVoucher> {
            val list = mutableListOf<OutstandingVoucher>()

            // Non-bill-wise ("on account") ledgers keep a running balance — show one net figure,
            // not individual bills plus a separate "on account" receipt that never settle.
            if (!ledger.maintainBillWise) {
                var net = ledger.openingBalance.paiseToRupees()
                for (vw in vouchers) {
                    val entry = vw.voucherEntries.firstOrNull { it.ledgerId == ledgerId } ?: continue
                    net += if (entry.isDebit) entry.amount.paiseToRupees() else -entry.amount.paiseToRupees()
                }
                if (kotlin.math.abs(net) > 0.01) {
                    list.add(
                        OutstandingVoucher(
                            voucherId = 0,
                            voucherNumber = "Balance",
                            date = Date(),
                            originalAmount = kotlin.math.abs(net),
                            pendingAmount = kotlin.math.abs(net),
                            type = "Balance",
                            ledgerId = ledgerId,
                            isDebit = net > 0,
                            totalReceived = 0.0,
                            billRefName = null,
                            refDisplayName = ledger.name,
                            customerName = ledger.name,
                            lastPaymentDate = null,
                            paymentHistory = emptyList()
                        )
                    )
                }
                return list
            }

            // 1. Process Sales and Purchase vouchers (bill-based)
            val bills = vouchers.asSequence()
                .filter { it.voucher.type == "Sales" || it.voucher.type == "Purchase" }
                .mapNotNull { vw ->
                    val entry = vw.voucherEntries.firstOrNull { it.ledgerId == ledgerId } ?: return@mapNotNull null
                    val (allocated, lastDate, history) = allocationsByBill[vw.voucher.id] ?: Triple(0.0, null, emptyList())
                    Triple(vw, entry, Triple(allocated, lastDate, history))
                }

            for ((vw, entry, allocInfo) in bills) {
                val (allocated, lastPaymentDate, history) = allocInfo
                val original = entry.amount.paiseToRupees()
                val pending = (original - allocated).coerceAtLeast(0.0)

                // Simple interest on the unpaid amount, accruing after the grace period
                // (voucher date + interestDays) at the voucher's annual rate.
                val interestAmount = if (vw.voucher.chargeInterest) {
                    InterestCalculator.simpleInterest(
                        pending,
                        vw.voucher.interestRate,
                        InterestCalculator.daysOverdue(vw.voucher.date, vw.voucher.interestDays)
                    )
                } else 0.0

                if (pending > 0.01) {
                    val isDebit = when (vw.voucher.type) {
                        "Sales" -> true   // Receivable: customer owes you
                        "Purchase" -> false // Payable: you owe supplier
                        else -> entry.isDebit
                    }

                    // Find the "New Ref" bill allocation for this entry to get the note/refName
                    val billRef = allAllocations
                        .firstOrNull { it.voucherEntryId == entry.id && it.refVoucherId == null && !it.refName.isNullOrBlank() }
                        ?.refName

                    list.add(
                        OutstandingVoucher(
                            voucherId = vw.voucher.id,
                            voucherNumber = vw.voucher.voucherNumber,
                            date = vw.voucher.date,
                            originalAmount = original,
                            pendingAmount = pending,
                            type = vw.voucher.type,
                            ledgerId = ledgerId,
                            isDebit = isDebit,
                            totalReceived = allocated,
                            billRefName = billRef,
                            refDisplayName = vw.voucher.refDisplayName,
                            customerName = vw.voucher.customField,
                            lastPaymentDate = lastPaymentDate,
                            paymentHistory = history,
                            interestRate = vw.voucher.interestRate,
                            interestDays = vw.voucher.interestDays,
                            interestAmount = interestAmount
                        )
                    )
                }
            }

            // 2. Process Payment and Receipt vouchers that are NOT against any bill (loans/advances)
            val paymentReceiptVouchers = vouchers.asSequence()
                .filter { it.voucher.type == "Payment" || it.voucher.type == "Receipt" }
                .mapNotNull { vw ->
                    // Find the entry for this ledger in the voucher
                    val entry = vw.voucherEntries.firstOrNull { it.ledgerId == ledgerId } ?: return@mapNotNull null

                    // Check if this voucher entry is SETTLING another voucher
                    // (i.e., it has allocations with refVoucherId pointing to another voucher)
                    // If yes, it's a settlement - skip it (the original bill will show reduced outstanding)
                    val isSettlingAnotherBill = allAllocations.any { alloc ->
                        alloc.voucherEntryId == entry.id && alloc.refVoucherId != null && alloc.refVoucherId != vw.voucher.id
                    }

                    // Skip if this is settling another bill
                    if (isSettlingAnotherBill) return@mapNotNull null

                    // Check if this Payment/Receipt itself is being settled against
                    // (some other voucher is settling this one)
                    val settledAmount = allocationsByBill[vw.voucher.id]?.first ?: 0.0
                    val settlementHistory = allocationsByBill[vw.voucher.id]?.third ?: emptyList()
                    val isBeingSettled = allocationsByBill.containsKey(vw.voucher.id)

                    Triple(vw, entry, Triple(settledAmount, settlementHistory, isBeingSettled))
                }

            for ((vw, entry, info) in paymentReceiptVouchers) {
                val (settledAmount, settlementHistory, isBeingSettled) = info
                val original = entry.amount.paiseToRupees()
                val pending = (original - settledAmount).coerceAtLeast(0.0)

                if (pending > 0.01) {
                    // For Receipt: You received money = Payable (you owe them services/goods or refund) = Credit (false)
                    // For Payment: You paid money = Receivable (they owe you back or goods/services) = Debit (true)
                    val isDebit = when (vw.voucher.type) {
                        "Receipt" -> false  // Payable
                        "Payment" -> true   // Receivable
                        else -> entry.isDebit
                    }

                    // Get the note/refName from bill allocation if exists
                    val billRef = allAllocations
                        .firstOrNull { it.voucherEntryId == entry.id && it.refVoucherId == null && !it.refName.isNullOrBlank() }
                        ?.refName

                    list.add(
                        OutstandingVoucher(
                            voucherId = vw.voucher.id,
                            voucherNumber = vw.voucher.voucherNumber,
                            date = vw.voucher.date,
                            originalAmount = original,
                            pendingAmount = pending,
                            type = vw.voucher.type,
                            ledgerId = ledgerId,
                            isDebit = isDebit,
                            totalReceived = settledAmount,
                            billRefName = billRef,
                            refDisplayName = vw.voucher.refDisplayName,
                            customerName = vw.voucher.customField,
                            lastPaymentDate = if (settlementHistory.isNotEmpty()) settlementHistory.first().date else null,
                            paymentHistory = settlementHistory
                        )
                    )
                }
            }

            return list
        }
    }
}