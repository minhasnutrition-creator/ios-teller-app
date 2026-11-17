package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Broad sweep of the offline Hinglish transaction parser (the same path the Finance Helper chat
 * uses), throwing many word orders / phrasings at it and asserting the extracted amount, party,
 * item and quantity. Kept as plain JUnit so it runs in seconds with no device.
 */
class HinglishSweepTest {

    private data class Expect(
        val type: String,
        val amount: Double,
        val party: String? = null,
        val item: String? = null,
        val qty: Double = 1.0
    )

    private fun parse(phrase: String): AiResponse.DraftVoucher? = runBlocking {
        FinanceHelper.tryParseHinglish(null, phrase) as? AiResponse.DraftVoucher
    }

    private fun check(phrase: String, e: Expect) {
        val d = parse(phrase)
        assertNotNull("$phrase -> null (expected ${e.type})", d)
        d!!
        assertEquals("$phrase type", e.type, d.type)
        assertEquals("$phrase amount", e.amount, d.amount ?: 0.0, 0.001)
        if (e.party != null) assertEquals("$phrase party", e.party, d.partyName)
        if (e.item != null) {
            val it = d.inventoryItems.firstOrNull()
            assertNotNull("$phrase item (expected ${e.item})", it)
            assertEquals("$phrase item", e.item, it?.name)
            assertEquals("$phrase qty", e.qty, it?.quantity ?: 0.0, 0.001)
        }
    }

    @Test
    fun purchases_bare_party_and_shuffled_amounts() {
        check("50 seb kharide Arijit Singh 10000 ke", Expect("Purchase", 10000.0, "Arijit Singh", "Apple", 50.0))
        check("50 amrud kharide Arjit se 10000 ke", Expect("Purchase", 10000.0, "Arjit", null, 50.0))
        check("20 Aalu kharide Arjun se 10000 ke", Expect("Purchase", 10000.0, "Arjun", "Potato", 20.0))
        check("50 Aalu kharide 5000 ke Amritsar", Expect("Purchase", 5000.0, "Amritsar", "Potato", 50.0))
        check("50 tiles kharidi Kajaria Amritsar 9000 ki", Expect("Purchase", 9000.0, "Kajaria Amritsar", "Tiles", 50.0))
        check("50 chappal in kharidi Amit se 10000 ki", Expect("Purchase", 10000.0, "Amit", "Slippers", 50.0))
        check("VPS motor se 50 Splendor kharidi rpb 49000", Expect("Purchase", 49000.0, "Vps Motor", "Splendor", 50.0))
        check("50 kilo Aalu kharide ₹10 ke", Expect("Purchase", 10.0, "Cash-in-hand", "Potato", 50.0))
        check("₹10 ke 50 Kele kharide", Expect("Purchase", 10.0, "Cash-in-hand", "Banana", 50.0))
        check("90 katoriya kharidi ₹5000 ki Arvind se", Expect("Purchase", 5000.0, "Arvind", "Katoriya", 90.0))
        check("Amit se Anar kharida ₹10 ka", Expect("Purchase", 10.0, "Amit", "Pomegranate", 1.0))
        check("patakhe khariden ₹8000 ke Amritsar se", Expect("Purchase", 8000.0, "Amritsar", null, 1.0))
        check("10 Splendor kharidi ₹5 lakh ki BPSC", Expect("Purchase", 500000.0, "Bpsc", "Splendor", 10.0))
        check("50 wash basin kharida arjun se 10000 ke", Expect("Purchase", 10000.0, "Arjun", "Wash Basin", 50.0))
    }

    @Test
    fun purchases_party_first_and_generic_items() {
        check("Amit se 50 seb kharide 10000 ke", Expect("Purchase", 10000.0, "Amit", "Apple", 50.0))
        check("amit se 5000 aloo kharida", Expect("Purchase", 5000.0, "Amit", "Potato", 1.0))
        check("mal kharida 10000", Expect("Purchase", 10000.0, "Cash-in-hand", null, 1.0))
        check("saman kharida 20000", Expect("Purchase", 20000.0, "Cash-in-hand", null, 1.0))
        check("50000 ki 10 Splendor kharide", Expect("Purchase", 50000.0, "Cash-in-hand", "Splendor", 10.0))
    }

    @Test
    fun sales() {
        check("50 Kele beche Amrit ko 9000 ke", Expect("Sales", 9000.0, "Amrit", "Banana", 50.0))
        check("Ek HF Deluxe bechi Arjit ko 100570 rupaye ki", Expect("Sales", 100570.0, "Arjit", null, 1.0))
        check("Splendor bachi Shubham ko 50000 ki", Expect("Sales", 50000.0, "Shubham", "Splendor", 1.0))
        check("5 kela beche 500 ki", Expect("Sales", 500.0, "Cash-in-hand", "Banana", 5.0))
        check("mal becha 2000", Expect("Sales", 2000.0, "Cash-in-hand", null, 1.0))
    }

    @Test
    fun payments_and_receipts() {
        check("Ramesh ko 5000 diye", Expect("Payment", 5000.0, "Ramesh"))
        check("kiraya diya 800", Expect("Payment", 800.0, null))
        check("salary diya 12000", Expect("Payment", 12000.0, null))
        check("Gagan se 2000 liya", Expect("Receipt", 2000.0, "Gagan"))
    }

    @Test
    fun garbled_or_incomplete_should_not_draft() {
        // No amount, or a bare number with no verb — must not fabricate a voucher.
        assertNull("no amount", parse("50 namkin ke packet kharidne se"))
        assertNull("bare number", parse("50"))
        assertNull("no verb", parse("the Splendor VPS Se 500000 Ki"))
    }
}
