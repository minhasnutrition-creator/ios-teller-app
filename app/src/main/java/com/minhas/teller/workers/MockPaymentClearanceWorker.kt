package com.minhas.teller.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.BillAllocation
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.utils.rupeesToPaise
import kotlinx.coroutines.delay
import java.util.Date

class MockPaymentClearanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val voucherId = inputData.getInt("voucherId", -1)
        val companyId = inputData.getInt("companyId", -1)
        val customerLedgerId = inputData.getInt("customerLedgerId", -1)
        val amount = inputData.getDouble("amount", 0.0)

        if (voucherId == -1 || companyId == -1 || customerLedgerId == -1 || amount <= 0.0) {
            return Result.failure()
        }

        // Delay 15 seconds to simulate receipt clearance
        delay(15000)

        try {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Check if the sales voucher exists and isn't already cleared
            val salesVoucher = db.voucherDao().getVoucherById(voucherId) ?: return Result.success()
            
            // Calculate total allocated/paid amount against this bill so far (in paise)
            val totalAllocated = db.billAllocationDao().getTotalAllocatedAmount(voucherId) ?: 0L
            val amountPaise = amount.rupeesToPaise()
            if (totalAllocated >= amountPaise) {
                // Already fully paid/cleared, no need to auto-mark
                return Result.success()
            }

            val remainingAmt = amountPaise - totalAllocated

            // Find a bank or cash ledger to deposit the money into
            val allLedgers = db.ledgerDao().getAllLedgersSync(companyId)
            val depositLedger = allLedgers.find { it.group.contains("Bank Accounts", ignoreCase = true) }
                ?: allLedgers.find { it.group.contains("Cash", ignoreCase = true) }
                ?: return Result.failure()

            // 1. Create and insert Receipt Voucher
            val dateStr = java.text.SimpleDateFormat("ddMMMyy_HHmmss", java.util.Locale.getDefault()).format(Date())
            val receiptVoucher = Voucher(
                id = 0,
                companyId = companyId,
                date = Date(),
                voucherNumber = "RCV-AUTO-${dateStr}",
                type = "Receipt",
                narration = "Auto-cleared via UPI payment for Sales #${salesVoucher.voucherNumber}",
                customField = "UPI Auto-Clear"
            )
            val receiptVoucherId = db.voucherDao().insert(receiptVoucher).toInt()

            // 2. Create and insert Voucher Entries
            // Debit: Deposit Ledger (Cash/Bank)
            val drEntry = VoucherEntry(
                id = 0,
                voucherId = receiptVoucherId,
                ledgerId = depositLedger.id,
                amount = remainingAmt,
                isDebit = true
            )
            val drEntryId = db.voucherEntryDao().insert(drEntry).toInt()

            // Credit: Customer Ledger
            val crEntry = VoucherEntry(
                id = 0,
                voucherId = receiptVoucherId,
                ledgerId = customerLedgerId,
                amount = remainingAmt,
                isDebit = false
            )
            val crEntryId = db.voucherEntryDao().insert(crEntry).toInt()

            // 3. Create and insert Bill Allocation linking the Credit entry to the original Sales Voucher
            val billAllocation = BillAllocation(
                id = 0,
                voucherEntryId = crEntryId,
                refVoucherId = voucherId,
                amount = remainingAmt,
                refName = salesVoucher.voucherNumber,
                refType = "Against"
            )
            db.billAllocationDao().insert(billAllocation)

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }
    }
}
