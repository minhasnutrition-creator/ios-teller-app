package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HinglishDiagnosticTest {

    @Test
    fun `english items are not corrupted by translation`() {
        assertEquals("Book", LanguageTranslationUtils.translateItem("books"))
        assertEquals("Candy", LanguageTranslationUtils.translateItem("candy"))
        assertEquals("Socks", LanguageTranslationUtils.translateItem("socks"))
        assertEquals("Pencil", LanguageTranslationUtils.translateItem("pencil"))
        assertEquals("Shirt", LanguageTranslationUtils.translateItem("shirt"))
    }

    @Test
    fun `hinglish items still translate`() {
        assertEquals("Book", LanguageTranslationUtils.translateItem("kitab"))
        assertEquals("Milk", LanguageTranslationUtils.translateItem("doodh"))
        assertEquals("Socks", LanguageTranslationUtils.translateItem("moja"))
        assertEquals("Rice", LanguageTranslationUtils.translateItem("chawal"))
        assertEquals("Banana", LanguageTranslationUtils.translateItem("kela"))
    }

    @Test
    fun `voice command parser handles party ko item bechi amount`() {
        val r = FinanceHelper.parseVoiceCommandLocal("sumit ko candy bechi 10000 ki")
        assert(r is AiResponse.DraftVoucher)
        assertEquals(10000.0, (r as AiResponse.DraftVoucher).amount ?: 0.0, 0.001)
        assertEquals(1, (r as AiResponse.DraftVoucher).inventoryItems.size)
        assertEquals("Candy", r.inventoryItems[0].name)
    }

    @Test
    fun `smart parser produces inventory for party ko item bechi`() = runBlocking {
        val r1 = FinanceHelper.parseSpeechToDraft(null, "sumit ko candy bechi 10000 ki")
        println("=== sumit ko candy bechi 10000 ki ===")
        println(r1)
        println("inventory: ${(r1 as? AiResponse.DraftVoucher)?.inventoryItems}")

        val r2 = FinanceHelper.parseSpeechToDraft(null, "arjit minhas ko books bechi 10000 rs ki")
        println("=== arjit minhas ko books bechi 10000 rs ki ===")
        println(r2)
        println("inventory: ${(r2 as? AiResponse.DraftVoucher)?.inventoryItems}")
    }

    @Test
    fun `english number words normalise so offline parser handles spoken amounts`() = runBlocking {
        val r = FinanceHelper.parseSpeechToDraft(null, "sold 5 wash basin to amit for nine thousand")
        assertTrue("expected DraftVoucher, got $r", r is AiResponse.DraftVoucher)
        val d = r as AiResponse.DraftVoucher
        assertEquals(9000.0, d.amount ?: 0.0, 0.001)
        assertEquals(5.0, d.inventoryItems[0].quantity, 0.001)
        assertEquals("Wash Basin", d.inventoryItems[0].name)
    }

    @Test
    fun `hinglish kharide purchase keeps qty separate from amount`() = runBlocking {
        val r = FinanceHelper.parseSpeechToDraft(null, "50 wash basin kharide hain arjun se 10000 ke")
        assertTrue("expected DraftVoucher, got $r", r is AiResponse.DraftVoucher)
        val d = r as AiResponse.DraftVoucher
        assertEquals(10000.0, d.amount ?: 0.0, 0.001)
        assertEquals(50.0, d.inventoryItems[0].quantity, 0.001)
        assertEquals("Wash Basin", d.inventoryItems[0].name)
        assertEquals("Arjun", d.partyName)
    }

    @Test
    fun `hinglish kharida variant also keeps qty separate from amount`() = runBlocking {
        val r = FinanceHelper.parseSpeechToDraft(null, "50 wash basin kharida hain arjun se 10000 ke")
        assertTrue("expected DraftVoucher, got $r", r is AiResponse.DraftVoucher)
        val d = r as AiResponse.DraftVoucher
        assertEquals(10000.0, d.amount ?: 0.0, 0.001)
        assertEquals(50.0, d.inventoryItems[0].quantity, 0.001)
    }

    @Test
    fun `english plurals and phonetics map to canonical singular items`() {
        assertEquals("Mango", LanguageTranslationUtils.translateItem("mangoes"))
        assertEquals("Apple", LanguageTranslationUtils.translateItem("apples"))
        assertEquals("Wash Basin", LanguageTranslationUtils.translateItem("wash baisen"))
        assertEquals("apple", LanguageTranslationUtils.itemKey("Apples"))
        assertEquals("mango", LanguageTranslationUtils.itemKey("Mangoes"))
        assertEquals("Wash Basin", LanguageTranslationUtils.canonicalItemName("5 wash basin", 1.0).first)
        assertEquals(5.0, LanguageTranslationUtils.canonicalItemName("5 wash basin", 1.0).second, 0.001)
    }
}
