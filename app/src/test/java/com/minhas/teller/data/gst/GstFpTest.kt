package com.minhas.teller.data.gst

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GstFpTest {

    @Test
    fun parseFp_validPeriod_returnsMonthAndYear() {
        assertEquals(9 to 2026, parseFp("092026"))
        assertEquals(12 to 2025, parseFp("122025"))
    }

    @Test
    fun parseFp_wrongLength_returnsNull() {
        assertNull(parseFp("0926"))
        assertNull(parseFp("92026"))
    }

    @Test
    fun parseFp_invalidMonth_returnsNull() {
        assertNull(parseFp("002026"))
        assertNull(parseFp("132026"))
    }

    @Test
    fun parseFp_nonNumeric_returnsNull() {
        assertNull(parseFp("09202X"))
    }

    @Test
    fun netLiability_matchesWizardFormula() {
        val outward = listOf(
            GstInvoice(
                voucherId = 1, invoiceNumber = "INV-001", dateText = "15-08-2026", totalPaise = 118000L,
                placeOfSupply = "27", supplyType = SupplyType.B2B, reverseCharge = false,
                recipientGstin = "27AAAAA0000A1Z5", recipientName = "Customer",
                lines = listOf(GstInvoiceLine(taxablePaise = 100000L, rate = 18.0, cgstPaise = 9000L, sgstPaise = 9000L, igstPaise = 0L))
            )
        )
        val inward = listOf(
            GstInvoice(
                voucherId = 2, invoiceNumber = "PINV-001", dateText = "12-08-2026", totalPaise = 59000L,
                placeOfSupply = "27", supplyType = SupplyType.B2B, reverseCharge = false,
                recipientGstin = "27BBBBB0000B1Z5", recipientName = "Supplier",
                lines = listOf(GstInvoiceLine(taxablePaise = 50000L, rate = 18.0, cgstPaise = 4500L, sgstPaise = 4500L, igstPaise = 0L))
            )
        )
        val r = GstReturn(
            gstin = "27ABCDE1234F1Z5", fp = "082026", businessStateCode = "27",
            outward = outward, inward = inward, warnings = emptyList()
        )

        val outCgst = r.outward.sumOf { it.cgst }
        val outSgst = r.outward.sumOf { it.sgst }
        val outIgst = r.outward.sumOf { it.igst }
        val itc = r.inward.sumOf { it.cgst + it.sgst + it.igst }
        val net = (outCgst + outSgst + outIgst - itc).round2()

        assertEquals(180.0, outCgst + outSgst, 0.001)
        assertEquals(90.0, itc, 0.001)
        assertEquals(90.0, net, 0.001)
    }
}
