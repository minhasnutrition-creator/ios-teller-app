package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Phase 1 enhancements:
 * 1. Multi-item batch voice billing (HelaFin engine).
 * 2. Dynamic UPI QR code builder (NPCI standard).
 */
class Phase1FeaturesTest {

    private fun parseDraft(query: String) = runBlocking {
        FinanceHelper.tryParseHinglish(null, query) as? AiResponse.DraftVoucher
    }

    @Test
    fun testMultiItemSalesSentence_commaSeparated() {
        val draft = parseDraft("sold 10 kg rice for 600, 5 ltr oil for 700 to ramesh")
        assertNotNull("Draft must be parsed", draft)
        assertEquals("Sales", draft?.type)
        assertEquals("Ramesh", draft?.partyName)
        assertEquals(2, draft?.inventoryItems?.size)

        val item1 = draft?.inventoryItems?.get(0)
        assertEquals("Rice", item1?.name)
        assertEquals(10.0, item1?.quantity ?: 0.0, 0.001)
        assertEquals(600.0, item1?.amount ?: 0.0, 0.001)
        assertEquals("Kg", item1?.unit)

        val item2 = draft?.inventoryItems?.get(1)
        assertEquals("Oil", item2?.name)
        assertEquals(5.0, item2?.quantity ?: 0.0, 0.001)
        assertEquals(700.0, item2?.amount ?: 0.0, 0.001)
        assertEquals("Liter", item2?.unit)

        assertEquals(1300.0, draft?.amount ?: 0.0, 0.001)
    }

    @Test
    fun testMultiItemHinglish_aurConnector() {
        val draft = parseDraft("ramesh ko 5 kg cheeni 200 me aur 2 ltr tel 280 me becha")
        assertNotNull("Draft must be parsed", draft)
        assertEquals("Sales", draft?.type)
        assertEquals("Ramesh", draft?.partyName)
        assertEquals(2, draft?.inventoryItems?.size)

        val item1 = draft?.inventoryItems?.get(0)
        assertEquals("Sugar", item1?.name)
        assertEquals(5.0, item1?.quantity ?: 0.0, 0.001)
        assertEquals(200.0, item1?.amount ?: 0.0, 0.001)
        assertEquals("Kg", item1?.unit)

        val item2 = draft?.inventoryItems?.get(1)
        assertEquals("Oil", item2?.name)
        assertEquals(2.0, item2?.quantity ?: 0.0, 0.001)
        assertEquals(280.0, item2?.amount ?: 0.0, 0.001)
        assertEquals("Liter", item2?.unit)

        assertEquals(480.0, draft?.amount ?: 0.0, 0.001)
    }

    @Test
    fun testMultiItemPurchase_andConnector() {
        val draft = parseDraft("bought 20 pcs soap for 400 and 10 pcs shampoo for 500 from suresh")
        assertNotNull("Draft must be parsed", draft)
        assertEquals("Purchase", draft?.type)
        assertEquals("Suresh", draft?.partyName)
        assertEquals(2, draft?.inventoryItems?.size)

        val item1 = draft?.inventoryItems?.get(0)
        assertEquals("Soap", item1?.name)
        assertEquals(20.0, item1?.quantity ?: 0.0, 0.001)
        assertEquals(400.0, item1?.amount ?: 0.0, 0.001)
        assertEquals("Pcs", item1?.unit)

        val item2 = draft?.inventoryItems?.get(1)
        assertEquals("Shampoo", item2?.name)
        assertEquals(10.0, item2?.quantity ?: 0.0, 0.001)
        assertEquals(500.0, item2?.amount ?: 0.0, 0.001)
        assertEquals("Pcs", item2?.unit)

        assertEquals(900.0, draft?.amount ?: 0.0, 0.001)
    }

    @Test
    fun testMultiItem_noPrices_splitsIntoItemsWithQuantity() {
        val draft = parseDraft("sell 10 tape aur 8 bulb")
        assertNotNull("Draft must be parsed", draft)
        assertEquals("Sales", draft?.type)
        assertEquals(2, draft?.inventoryItems?.size)

        val item1 = draft?.inventoryItems?.get(0)
        assertEquals(10.0, item1?.quantity ?: 0.0, 0.001)
        assertEquals(0.0, item1?.amount ?: 0.0, 0.001)

        val item2 = draft?.inventoryItems?.get(1)
        assertEquals(8.0, item2?.quantity ?: 0.0, 0.001)
        assertEquals(0.0, item2?.amount ?: 0.0, 0.001)
    }

    @Test
    fun testUpiUriBuilder_npciFormat() {
        val uri = UpiQrHelper.buildUpiUri(
            upiId = "merchant@okhdfcbank",
            payeeName = "Gupta Kirana Store",
            amount = 1450.50,
            invoiceNumber = "INV-204"
        )

        assertTrue(uri.startsWith("upi://pay?pa=merchant@okhdfcbank"))
        assertTrue(uri.contains("&pn=Gupta%20Kirana%20Store"))
        assertTrue(uri.contains("&am=1450.50"))
        assertTrue(uri.contains("&cu=INR"))
        assertTrue(uri.contains("&tn=Invoice%20%23INV-204"))
        assertTrue(uri.contains("&tr=INV-204"))
    }
}
