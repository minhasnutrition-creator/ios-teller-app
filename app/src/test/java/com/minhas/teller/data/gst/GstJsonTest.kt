package com.minhas.teller.data.gst

import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.rupeesToPaise
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class GstJsonTest {

    private fun date(year: Int, month0Based: Int, day: Int): java.util.Date =
        Calendar.getInstance().apply { set(year, month0Based, day, 0, 0, 0) }.time

    private val aug1 = date(2026, 7, 1)
    private val aug31 = date(2026, 7, 31)

    private fun item() = InventoryEntry(
        voucherId = 0, stockItemId = 0, quantity = 1.0, rate = 1000.0, amount = 1000.0.rupeesToPaise(),
        hsnCode = "1234", gstRate = 18.0, cgstRate = 9.0, sgstRate = 9.0, igstRate = 18.0,
        taxableAmount = 1000.0.rupeesToPaise(), taxAmount = 180.0.rupeesToPaise(), totalAmount = 1180.0.rupeesToPaise()
    )

    private fun buildReturn(): GstReturn {
        val customer = Ledger(id = 1, name = "Customer", group = "Sundry Debtors", openingBalance = 0L, gstin = "27AAAAA0000A1Z5", stateCode = "27")
        val sales = Ledger(id = 99, name = "Sales", group = "Sales Accounts", openingBalance = 0L)
        val company = Company(id = 1, name = "Test Co", mailingName = null, address = null, state = "Maharashtra", country = "India", pincode = null, gstin = "27ABCDE1234F1Z5", phoneNumber = null, email = null, financialYearFrom = 0L)
        val sale = VoucherWithAllEntries(
            voucher = Voucher(id = 1, type = "Sales", date = date(2026, 7, 15), voucherNumber = "INV-001"),
            voucherEntries = listOf(
                VoucherEntry(voucherId = 1, ledgerId = 1, amount = 1180.0.rupeesToPaise(), isDebit = true),
                VoucherEntry(voucherId = 1, ledgerId = 99, amount = 1000.0.rupeesToPaise(), isDebit = false)
            ),
            inventoryEntries = listOf(item())
        )
        return GstReturnBuilder.buildGstReturn(listOf(sale), listOf(customer, sales), company, aug1, aug31)
    }

    @Test
    fun gstr1_hasTopLevelAndB2b() {
        val json = Gstr1Json.toJson(buildReturn())
        assertEquals("27ABCDE1234F1Z5", json.getString("gstin"))
        assertEquals("082026", json.getString("fp"))
        val b2b = json.getJSONArray("b2b")
        assertEquals(1, b2b.length())
        val first = b2b.getJSONObject(0)
        assertEquals("27AAAAA0000A1Z5", first.getString("ctin"))
        val inv = first.getJSONArray("inv").getJSONObject(0)
        assertEquals("INV-001", inv.getString("inum"))
        assertEquals("27", inv.getString("pos"))
        val itmDet = inv.getJSONArray("itms").getJSONObject(0).getJSONObject("itm_det")
        assertEquals(1000.0, itmDet.getDouble("txval"), 0.001)
        assertEquals(90.0, itmDet.getDouble("camt"), 0.001)
        assertEquals(90.0, itmDet.getDouble("samt"), 0.001)
        assertEquals(0.0, itmDet.getDouble("iamt"), 0.001)
    }

    @Test
    fun gstr1_hasHsnSummary() {
        val json = Gstr1Json.toJson(buildReturn())
        val hsn = json.getJSONObject("hsn").getJSONArray("data")
        assertEquals(1, hsn.length())
        assertEquals("1234", hsn.getJSONObject(0).getString("hsn_sc"))
    }

    @Test
    fun gstr3b_hasOutwardAndItcSections() {
        val json = Gstr3bJson.toJson(buildReturn())
        val sup = json.getJSONObject("sup_details")
        val osupDet = sup.getJSONObject("osup_det")
        assertEquals(1000.0, osupDet.getDouble("txval"), 0.001)
        assertEquals(90.0, osupDet.getDouble("camt"), 0.001)
        assertEquals(90.0, osupDet.getDouble("samt"), 0.001)

        val itcAvl = json.getJSONObject("itc_elg").getJSONArray("itc_avl")
        val tySet = (0 until itcAvl.length()).map { itcAvl.getJSONObject(it).getString("ty") }.toSet()
        assert(tySet.containsAll(listOf("IMPG", "IMPS", "ISRC", "ISD", "OTH")))
    }
}
