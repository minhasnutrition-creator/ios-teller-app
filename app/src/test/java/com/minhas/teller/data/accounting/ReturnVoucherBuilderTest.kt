package com.minhas.teller.data.accounting

import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ReturnVoucherBuilderTest {

    private val CUSTOMER = 1; private val SALES = 2; private val CGST = 3; private val SGST = 4

    /** Sale: item A x2 = 11800 (10000 + 1800 GST), item B x1 = 5900 (5000 + 900 GST). */
    private fun sale(): VoucherWithAllEntries = VoucherWithAllEntries(
        voucher = Voucher(id = 10, type = "Sales", date = Date(0), voucherNumber = "INV-1", companyId = 7),
        voucherEntries = listOf(
            VoucherEntry(id = 1, voucherId = 10, ledgerId = CUSTOMER, amount = 17_700, isDebit = true),
            VoucherEntry(id = 2, voucherId = 10, ledgerId = SALES, amount = 15_000, isDebit = false),
            VoucherEntry(id = 3, voucherId = 10, ledgerId = CGST, amount = 1_350, isDebit = false),
            VoucherEntry(id = 4, voucherId = 10, ledgerId = SGST, amount = 1_350, isDebit = false)
        ),
        inventoryEntries = listOf(
            InventoryEntry(id = 100, voucherId = 10, stockItemId = 500, quantity = 2.0, rate = 5000.0, amount = 11_800,
                gstRate = 18.0, taxableAmount = 10_000, taxAmount = 1_800, totalAmount = 11_800),
            InventoryEntry(id = 101, voucherId = 10, stockItemId = 501, quantity = 1.0, rate = 5000.0, amount = 5_900,
                gstRate = 18.0, taxableAmount = 5_000, taxAmount = 900, totalAmount = 5_900)
        )
    )

    private fun balanced(r: ReturnVoucherBuilder.Result) =
        r.entries.filter { it.isDebit }.sumOf { it.amount } == r.entries.filter { !it.isDebit }.sumOf { it.amount }

    @Test
    fun `full return mirrors every line and links to the bill`() {
        val r = ReturnVoucherBuilder.build(sale(), null, "SR-1", Date(1), partyLedgerIds = setOf(CUSTOMER))
        assertEquals("Sales Return", r.voucher.type)
        assertEquals(10, r.voucher.returnOfVoucherId)
        assertEquals(7, r.voucher.companyId)
        val byLedger = r.entries.associateBy { it.ledgerId }
        assertEquals(17_700L, byLedger[CUSTOMER]!!.amount); assertEquals(false, byLedger[CUSTOMER]!!.isDebit)
        assertEquals(15_000L, byLedger[SALES]!!.amount); assertEquals(true, byLedger[SALES]!!.isDebit)
        assertEquals(1_350L, byLedger[CGST]!!.amount); assertEquals(true, byLedger[CGST]!!.isDebit)
        assertEquals(listOf(2.0, 1.0), r.inventory.map { it.quantity })
        assertEquals(1, r.allocations.size)
        assertEquals(10, r.allocations[0].refVoucherId)
        assertEquals(17_700L, r.allocations[0].amount)
        assertEquals(17_700L, r.totalPaise)
        assertTrue(balanced(r))
    }

    @Test
    fun `partial return scales the bill by the value that came back`() {
        // One of the two A's = 5900 of 17700 = one third of the bill.
        val r = ReturnVoucherBuilder.build(sale(), mapOf(100 to 1.0), "SR-2", Date(1), setOf(CUSTOMER))
        val byLedger = r.entries.associateBy { it.ledgerId }
        assertEquals(5_900L, byLedger[CUSTOMER]!!.amount)
        assertEquals(5_000L, byLedger[SALES]!!.amount)
        assertEquals(450L, byLedger[CGST]!!.amount)
        assertEquals(450L, byLedger[SGST]!!.amount)
        assertEquals(1, r.inventory.size)
        assertEquals(1.0, r.inventory[0].quantity, 0.0)
        assertEquals(5_900L, r.inventory[0].amount)
        assertEquals(900L, r.inventory[0].taxAmount)
        assertTrue(balanced(r))
    }

    @Test
    fun `rounding leftovers are settled so the voucher always balances`() {
        val odd = VoucherWithAllEntries(
            voucher = Voucher(id = 11, type = "Purchase", date = Date(0), voucherNumber = "P-9"),
            voucherEntries = listOf(
                VoucherEntry(voucherId = 11, ledgerId = 9, amount = 1_000, isDebit = false),
                VoucherEntry(voucherId = 11, ledgerId = 8, amount = 335, isDebit = true),
                VoucherEntry(voucherId = 11, ledgerId = 7, amount = 335, isDebit = true),
                VoucherEntry(voucherId = 11, ledgerId = 6, amount = 330, isDebit = true)
            ),
            inventoryEntries = listOf(
                InventoryEntry(id = 1, voucherId = 11, stockItemId = 1, quantity = 2.0, rate = 5.0, amount = 1_000)
            )
        )
        val r = ReturnVoucherBuilder.build(odd, mapOf(1 to 1.0), "PR-1", Date(1), emptySet())
        assertEquals("Purchase Return", r.voucher.type)
        assertTrue(balanced(r))
        assertTrue(r.allocations.isEmpty())
    }

    @Test
    fun `items already returned cannot be returned again`() {
        val bill = sale()
        val first = ReturnVoucherBuilder.build(bill, mapOf(100 to 1.0), "SR-1", Date(1), setOf(CUSTOMER))
        val earlier = VoucherWithAllEntries(first.voucher.copy(id = 20), first.entries, first.inventory)
        val done = ReturnVoucherBuilder.alreadyReturned(bill, listOf(earlier))
        assertEquals(1.0, done[100]!!, 0.0)

        // "Return everything" now only brings back what is left: 1 x A and 1 x B.
        val rest = ReturnVoucherBuilder.build(bill, null, "SR-2", Date(2), setOf(CUSTOMER), done)
        assertEquals(listOf(1.0, 1.0), rest.inventory.map { it.quantity })
        assertEquals(11_800L, rest.totalPaise)
        assertTrue(balanced(rest))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nothing left to return is rejected`() {
        val bill = sale()
        ReturnVoucherBuilder.build(bill, null, "SR-3", Date(1), setOf(CUSTOMER), mapOf(100 to 2.0, 101 to 1.0))
    }

    @Test
    fun `only sales and purchases can be returned`() {
        assertEquals(null, ReturnVoucherBuilder.returnTypeFor("Receipt"))
        assertEquals("Sales Return", ReturnVoucherBuilder.returnTypeFor("Sales"))
        assertEquals("Purchase Return", ReturnVoucherBuilder.returnTypeFor("Purchase"))
    }
}
