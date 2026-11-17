package com.minhas.teller.data.gst

import com.minhas.teller.utils.backOutTaxPaise
import com.minhas.teller.utils.percentOfPaise
import org.junit.Assert.assertEquals
import org.junit.Test

/** GST amounts are computed and summed in exact paise (see GstReturnModels / utils/Money.kt). */
class GstPaiseMathTest {

    @Test
    fun `percentOfPaise rounds half up to the paisa`() {
        assertEquals(900L, percentOfPaise(10_005L, 9.0))   // 9.0045 -> 9.00
        assertEquals(1L, percentOfPaise(5L, 10.0))          // 0.005 -> 0.01
        assertEquals(1_800L, percentOfPaise(10_000L, 18.0))
        assertEquals(0L, percentOfPaise(10_000L, 0.0))
        assertEquals(250L, percentOfPaise(10_000L, 2.5))    // half of 5%
    }

    @Test
    fun `backOutTaxPaise recovers the taxable base from a tax-inclusive gross`() {
        assertEquals(10_000L, backOutTaxPaise(11_800L, 18.0))
        assertEquals(10_000L, backOutTaxPaise(10_500L, 5.0))
        assertEquals(84_746L, backOutTaxPaise(1_00_000L, 18.0)) // 847.457.. -> 847.46
        assertEquals(1_234L, backOutTaxPaise(1_234L, 0.0))
    }

    @Test
    fun `invoice totals add up exactly across many lines`() {
        // 0.10 + 0.20 style values summed 10,000 times: exact in paise, drifts in Double.
        val lines = List(10_000) { GstInvoiceLine(10L, 18.0, 1L, 1L, 0L) }
        val inv = GstInvoice(
            voucherId = 1, invoiceNumber = "1", dateText = "01-01-2026", totalPaise = 0L,
            placeOfSupply = "03", supplyType = SupplyType.B2CS, reverseCharge = false,
            recipientGstin = null, recipientName = null, lines = lines
        )
        assertEquals(1_00_000L, inv.taxablePaise)
        assertEquals(20_000L, inv.taxPaise)
        assertEquals(1000.0, inv.taxableValue, 0.0)
        assertEquals(200.0, inv.totalTax, 0.0)
    }

    @Test
    fun `intra state flag needs igst and no cgst or sgst`() {
        fun inv(c: Long, s: Long, i: Long) = GstInvoice(
            1, "1", "01-01-2026", 0L, "03", SupplyType.B2B, false, "03ABCDE1234F1Z5", null,
            listOf(GstInvoiceLine(10_000L, 18.0, c, s, i))
        )
        assertEquals(true, inv(0L, 0L, 1_800L).isInterState)
        assertEquals(false, inv(900L, 900L, 0L).isInterState)
    }
}
