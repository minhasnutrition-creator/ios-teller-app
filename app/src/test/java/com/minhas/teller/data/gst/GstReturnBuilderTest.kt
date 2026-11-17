package com.minhas.teller.data.gst

import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.rupeesToPaise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class GstReturnBuilderTest {

    private fun date(year: Int, month0Based: Int, day: Int): Date =
        Calendar.getInstance().apply { set(year, month0Based, day, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.time

    private val aug1 = date(2026, 7, 1)
    private val aug31 = date(2026, 7, 31)

    private fun company(state: String, gstin: String = "27ABCDE1234F1Z5") =
        Company(id = 1, name = "Test Co", mailingName = null, address = null, state = state, country = "India",
            pincode = null, gstin = gstin, phoneNumber = null, email = null, financialYearFrom = 0L)

    private fun customerLedger(id: Int, gstin: String?, stateCode: String?) =
        Ledger(id = id, name = "Customer", group = "Sundry Debtors", openingBalance = 0L, gstin = gstin, stateCode = stateCode)

    private fun salesLedger(id: Int) =
        Ledger(id = id, name = "Sales", group = "Sales Accounts", openingBalance = 0L)

    private fun item(hsn: String = "1234", taxable: Double = 1000.0, gstRate: Double = 18.0) =
        InventoryEntry(
            voucherId = 0, stockItemId = 0, quantity = 1.0, rate = taxable, amount = taxable.rupeesToPaise(),
            hsnCode = hsn, gstRate = gstRate, cgstRate = gstRate / 2, sgstRate = gstRate / 2, igstRate = gstRate,
            taxableAmount = taxable.rupeesToPaise(), taxAmount = (taxable * gstRate / 100).rupeesToPaise(), totalAmount = (taxable * (1 + gstRate / 100)).rupeesToPaise()
        )

    private fun salesVoucher(
        customerId: Int,
        supplyType: String? = null,
        placeOfSupply: String? = null,
        inventory: List<InventoryEntry> = listOf(item())
    ) = VoucherWithAllEntries(
        voucher = Voucher(
            id = 1, type = "Sales", date = date(2026, 7, 15), voucherNumber = "INV-001",
            placeOfSupply = placeOfSupply, supplyType = supplyType
        ),
        voucherEntries = listOf(
            VoucherEntry(voucherId = 1, ledgerId = customerId, amount = 1180.0.rupeesToPaise(), isDebit = true),
            VoucherEntry(voucherId = 1, ledgerId = 99, amount = 1000.0.rupeesToPaise(), isDebit = false)
        ),
        inventoryEntries = inventory
    )

    @Test
    fun b2bIntraState_splitsCgstSgst() {
        val customer = customerLedger(1, "27AAAAA0000A1Z5", "27")
        val r = GstReturnBuilder.buildGstReturn(
            listOf(salesVoucher(1)),
            listOf(customer, salesLedger(99)),
            company("Maharashtra"),
            aug1, aug31
        )
        val inv = r.outward.single()
        assertEquals(SupplyType.B2B, inv.supplyType)
        assertEquals("27", inv.placeOfSupply)
        assertTrue(!inv.isInterState)
        assertEquals(90.0, inv.cgst, 0.001)
        assertEquals(90.0, inv.sgst, 0.001)
        assertEquals(0.0, inv.igst, 0.001)
        assertEquals("27AAAAA0000A1Z5", inv.recipientGstin)
    }

    @Test
    fun b2bInterState_usesIgst() {
        val customer = customerLedger(1, "06AAAAA0000A1Z5", "06")
        val r = GstReturnBuilder.buildGstReturn(
            listOf(salesVoucher(1)),
            listOf(customer, salesLedger(99)),
            company("Maharashtra"), // 27
            aug1, aug31
        )
        val inv = r.outward.single()
        assertEquals(SupplyType.B2B, inv.supplyType)
        assertTrue(inv.isInterState)
        assertEquals(0.0, inv.cgst, 0.001)
        assertEquals(0.0, inv.sgst, 0.001)
        assertEquals(180.0, inv.igst, 0.001)
    }

    @Test
    fun noGstinIntraState_classifiesB2cs() {
        val customer = customerLedger(1, null, "27")
        val r = GstReturnBuilder.buildGstReturn(
            listOf(salesVoucher(1)),
            listOf(customer, salesLedger(99)),
            company("Maharashtra"),
            aug1, aug31
        )
        assertEquals(SupplyType.B2CS, r.outward.single().supplyType)
    }

    @Test
    fun noGstinInterState_classifiesB2cl() {
        val customer = customerLedger(1, null, "06")
        val r = GstReturnBuilder.buildGstReturn(
            listOf(salesVoucher(1)),
            listOf(customer, salesLedger(99)),
            company("Maharashtra"),
            aug1, aug31
        )
        assertEquals(SupplyType.B2CL, r.outward.single().supplyType)
    }

    @Test
    fun explicitExport_wins() {
        val customer = customerLedger(1, null, null)
        val r = GstReturnBuilder.buildGstReturn(
            listOf(salesVoucher(1, supplyType = "EXPORT")),
            listOf(customer, salesLedger(99)),
            company("Maharashtra"),
            aug1, aug31
        )
        assertEquals(SupplyType.EXPORT, r.outward.single().supplyType)
    }

    @Test
    fun missingGstinForExplicitB2b_warns() {
        val customer = customerLedger(1, null, "27")
        val r = GstReturnBuilder.buildGstReturn(
            listOf(salesVoucher(1, supplyType = "B2B")),
            listOf(customer, salesLedger(99)),
            company("Maharashtra"),
            aug1, aug31
        )
        assertTrue(r.warnings.any { it.contains("GSTIN") })
    }

    @Test
    fun purchaseContributesToItc() {
        val supplier = Ledger(id = 1, name = "Supplier", group = "Sundry Creditors", openingBalance = 0L, gstin = "27BBBBB0000B1Z5", stateCode = "27")
        val purchase = VoucherWithAllEntries(
            voucher = Voucher(id = 2, type = "Purchase", date = date(2026, 7, 10), voucherNumber = "PINV-001"),
            voucherEntries = listOf(
                VoucherEntry(voucherId = 2, ledgerId = 99, amount = 1000.0.rupeesToPaise(), isDebit = true),
                VoucherEntry(voucherId = 2, ledgerId = 1, amount = 1180.0.rupeesToPaise(), isDebit = false)
            ),
            inventoryEntries = listOf(item())
        )
        val r = GstReturnBuilder.buildGstReturn(
            listOf(purchase),
            listOf(supplier, salesLedger(99)),
            company("Maharashtra"),
            aug1, aug31
        )
        assertEquals(1, r.inward.size)
        assertEquals(180.0, r.inward.single().cgst + r.inward.single().sgst, 0.001)
    }
}
