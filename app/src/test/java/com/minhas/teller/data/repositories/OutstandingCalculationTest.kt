package com.minhas.teller.data.repositories

import com.minhas.teller.data.entities.BillAllocation
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.rupeesToPaise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Unit tests for the outstanding/pending amount calculation — the "original minus
 * allocated = still due" math that drives the Outstanding Payments report.
 * Pure logic — no Room database needed. Runs on JVM, no device required.
 *
 * A bug here means customers shown as owing the wrong amount (or the wrong side),
 * which directly affects collections.
 */
class OutstandingCalculationTest {

    private val today = Date()

    private fun debtor(id: Int = 1) =
        Ledger(id = id, name = "Amit Traders", group = "Sundry Debtors", openingBalance = 0L, maintainBillWise = true)

    private fun voucher(
        id: Int,
        type: String,
        ledgerId: Int,
        amount: Double,
        isDebit: Boolean,
        entryId: Int = id
    ) = VoucherWithAllEntries(
        voucher = Voucher(id = id, type = type, date = today, voucherNumber = "$type-$id"),
        voucherEntries = listOf(VoucherEntry(id = entryId, voucherId = id, ledgerId = ledgerId, amount = amount.rupeesToPaise(), isDebit = isDebit)),
        inventoryEntries = emptyList()
    )

    private fun allocated(voucherId: Int, amount: Double) =
        Triple(amount, today, listOf(PaymentHistoryItem(amount, today, "Receipt")))

    // ── Bill-based (Sales/Purchase) ─────────────────────────────────────────

    @Test
    fun `sale with no payment is fully outstanding as a receivable`() {
        val result = VoucherRepository.computeOutstandingForLedgerFromVouchers(
            ledgerId = 1,
            ledger = debtor(),
            vouchers = listOf(voucher(100, "Sales", ledgerId = 1, amount = 5000.0, isDebit = true)),
            allocationsByBill = emptyMap(),
            allAllocations = emptyList()
        )
        assertEquals(1, result.size)
        assertEquals(5000.0, result[0].originalAmount, 0.001)
        assertEquals(5000.0, result[0].pendingAmount, 0.001)
        assertEquals(0.0, result[0].totalReceived, 0.001)
        assertTrue(result[0].isDebit)
        assertEquals("Sales", result[0].type)
    }

    @Test
    fun `partial payment reduces the pending amount`() {
        val result = VoucherRepository.computeOutstandingForLedgerFromVouchers(
            ledgerId = 1,
            ledger = debtor(),
            vouchers = listOf(voucher(100, "Sales", 1, 5000.0, true)),
            allocationsByBill = mapOf(100 to allocated(100, 3000.0)),
            allAllocations = emptyList()
        )
        assertEquals(1, result.size)
        assertEquals(5000.0, result[0].originalAmount, 0.001)
        assertEquals(2000.0, result[0].pendingAmount, 0.001)
        assertEquals(3000.0, result[0].totalReceived, 0.001)
    }

    @Test
    fun `fully settled bill is not outstanding`() {
        val result = VoucherRepository.computeOutstandingForLedgerFromVouchers(
            ledgerId = 1,
            ledger = debtor(),
            vouchers = listOf(voucher(100, "Sales", 1, 5000.0, true)),
            allocationsByBill = mapOf(100 to allocated(100, 5000.0)),
            allAllocations = emptyList()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `purchase voucher is a payable`() {
        val creditor = Ledger(id = 2, name = "Global Suppliers", group = "Sundry Creditors", openingBalance = 0L, maintainBillWise = true)
        val result = VoucherRepository.computeOutstandingForLedgerFromVouchers(
            ledgerId = 2,
            ledger = creditor,
            vouchers = listOf(voucher(200, "Purchase", 2, 2000.0, false)),
            allocationsByBill = emptyMap(),
            allAllocations = emptyList()
        )
        assertEquals(1, result.size)
        assertFalse(result[0].isDebit)
        assertEquals(2000.0, result[0].pendingAmount, 0.001)
    }

    // ── Settlement handling (Payment/Receipt) ──────────────────────────────

    @Test
    fun `receipt that settles a sale is not listed as separate outstanding`() {
        val sale = voucher(100, "Sales", 1, 5000.0, true, entryId = 100)
        val receipt = voucher(300, "Receipt", 1, 3000.0, false, entryId = 300)
        val result = VoucherRepository.computeOutstandingForLedgerFromVouchers(
            ledgerId = 1,
            ledger = debtor(),
            vouchers = listOf(sale, receipt),
            allocationsByBill = mapOf(100 to allocated(100, 3000.0)),
            allAllocations = listOf(BillAllocation(voucherEntryId = 300, refVoucherId = 100, amount = 3000.0.rupeesToPaise()))
        )
        // Only the sale remains outstanding (5000 - 3000 = 2000); the receipt is a settlement, not an item.
        assertEquals(1, result.size)
        assertEquals(100, result[0].voucherId)
        assertEquals(2000.0, result[0].pendingAmount, 0.001)
    }

    @Test
    fun `standalone receipt advance is a payable`() {
        val result = VoucherRepository.computeOutstandingForLedgerFromVouchers(
            ledgerId = 1,
            ledger = debtor(),
            vouchers = listOf(voucher(400, "Receipt", 1, 2000.0, true)),
            allocationsByBill = emptyMap(),
            allAllocations = emptyList()
        )
        assertEquals(1, result.size)
        // Money received up front = you owe goods/services back = credit/payable.
        assertFalse(result[0].isDebit)
        assertEquals(2000.0, result[0].pendingAmount, 0.001)
    }
}
