package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exhaustive sweep of the offline Hinglish/English transaction parsers. Mirrors the real
 * routing in FinanceHelper.chatWithFinance (tier 1 tryParseHinglish → tier 3 parseVoiceCommandLocal),
 * plus a standalone run of the English/`smartRegexParser`-equivalent wording via tier 3.
 *
 * Each case asserts the extracted voucher type, amount, party and (where relevant) item + qty.
 * Phrases that any tier should reasonably handle are asserted strictly; a failure here means a
 * real user query would mis-parse or be dropped.
 */
class HinglishComprehensiveTest {

    private data class Expect(
        val type: String,
        val amount: Double,
        val party: String? = null,
        val item: String? = null,
        val qty: Double = 1.0
    )

    private fun parseTier1(phrase: String): AiResponse.DraftVoucher? = runBlocking {
        FinanceHelper.tryParseHinglish(null, phrase) as? AiResponse.DraftVoucher
    }

    private fun parseTier2(phrase: String): AiResponse.DraftVoucher? = runBlocking {
        FinanceHelper.smartRegexParser(null, phrase) as? AiResponse.DraftVoucher
    }

    private fun parseTier3(phrase: String): AiResponse.DraftVoucher? =
        FinanceHelper.parseVoiceCommandLocal(phrase) as? AiResponse.DraftVoucher

    /** Full offline pipeline: first non-null draft wins (tier1 → tier2 → tier3). */
    private fun parseAny(phrase: String): AiResponse.DraftVoucher? =
        parseTier1(phrase) ?: parseTier2(phrase) ?: parseTier3(phrase)

    private fun assertDraft(phrase: String, e: Expect, d: AiResponse.DraftVoucher?) {
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

    private fun check(phrase: String, e: Expect) = assertDraft(phrase, e, parseAny(phrase))
    private fun checkT1(phrase: String, e: Expect) = assertDraft(phrase, e, parseTier1(phrase))

    // ───────────────────────────── PURCHASES ─────────────────────────────

    @Test fun purchases_verb_variants() {
        // kharida / kharidi / kharide / kharid / khareed / khariden / kharidte / kharidiya
        check("50 seb kharida Ram se 10000 ki", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("50 seb kharidi Ram se 10000 ki", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("50 seb kharide Ram se 10000 ki", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("50 seb khareed Ram se 10000 ki", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("50 seb khariden Ram se 10000 ki", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("50 seb kharidte Ram se 10000 ki", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("50 seb kharidiya Ram se 10000 ki", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("50 seb buy kiya Ram se 10000 ki", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("50 seb purchase kiya Ram se 10000 ki", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
    }

    @Test fun purchases_items_units() {
        check("50 kg chawal kharida Ram se 2000 ki", Expect("Purchase", 2000.0, "Ram", "Rice", 50.0))
        check("5 kilo cheeni kharidi 500", Expect("Purchase", 500.0, "Cash-in-hand", "Sugar", 5.0))
        check("2 litre doodh kharida 100", Expect("Purchase", 100.0, "Cash-in-hand", "Milk", 2.0))
        check("10 kilo atta kharida Ram se 400", Expect("Purchase", 400.0, "Ram", "Flour", 10.0))
        check("1 kilo dal kharida 90", Expect("Purchase", 90.0, "Cash-in-hand", "Lentils", 1.0))
        check("5 kilo namak kharida 100", Expect("Purchase", 100.0, "Cash-in-hand", "Salt", 5.0))
        check("1 kilo aam kharida 150", Expect("Purchase", 150.0, "Cash-in-hand", "Mango", 1.0))
        check("2 kilo tamatar kharida 80", Expect("Purchase", 80.0, "Cash-in-hand", "Tomato", 2.0))
    }

    @Test fun purchases_party_orders() {
        // party-first with se
        check("Ram se 50 seb kharida 10000 ki", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        // party-last with se
        check("50 seb kharida 10000 ki Ram se", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        // amount-first
        check("10000 ki 50 seb kharida Ram", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        // bare party (no connector) — amount last
        check("50 seb kharida Arijit Singh 10000 ki", Expect("Purchase", 10000.0, "Arijit Singh", "Apple", 50.0))
        // party with "se", amount first, item last
        check("Ram se 10000 ki 50 seb kharida", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
    }

    @Test fun purchases_amounts_in_words() {
        check("5 hazaar ka maal kharida", Expect("Purchase", 5000.0, "Cash-in-hand", null, 1.0))
        check("50 seb kharida ek lakh ki", Expect("Purchase", 100000.0, "Cash-in-hand", "Apple", 50.0))
        check("50 hazaar ka saman kharida", Expect("Purchase", 50000.0, "Cash-in-hand", null, 1.0))
        check("10 hazaar ki 20 kilo chawal kharida", Expect("Purchase", 10000.0, "Cash-in-hand", "Rice", 20.0))
    }

    // ───────────────────────────── SALES ─────────────────────────────

    @Test fun sales_verb_variants() {
        check("50 kela becha Ram ko 9000 ki", Expect("Sales", 9000.0, "Ram", "Banana", 50.0))
        check("50 kela beche Ram ko 9000 ki", Expect("Sales", 9000.0, "Ram", "Banana", 50.0))
        check("50 kela bechi Ram ko 9000 ki", Expect("Sales", 9000.0, "Ram", "Banana", 50.0))
        check("50 kela bech Ram ko 9000 ki", Expect("Sales", 9000.0, "Ram", "Banana", 50.0))
        check("50 kela bachi Ram ko 9000 ki", Expect("Sales", 9000.0, "Ram", "Banana", 50.0)) // ASR bachi→beche
        check("50 kela sale kiya Ram ko 9000 ki", Expect("Sales", 9000.0, "Ram", "Banana", 50.0))
        check("50 kela sell kiya Ram ko 9000 ki", Expect("Sales", 9000.0, "Ram", "Banana", 50.0))
        check("50 kela bikri Ram ko 9000 ki", Expect("Sales", 9000.0, "Ram", "Banana", 50.0))
    }

    @Test fun sales_party_orders() {
        check("Ram ko 50 kela beche 9000 ki", Expect("Sales", 9000.0, "Ram", "Banana", 50.0))
        check("9000 ki 50 kela beche Ram ko", Expect("Sales", 9000.0, "Ram", "Banana", 50.0))
        check("50 kela beche 9000 ki Ram ko", Expect("Sales", 9000.0, "Ram", "Banana", 50.0))
        check("Ram ko 9000 ke 50 kela beche", Expect("Sales", 9000.0, "Ram", "Banana", 50.0))
    }

    @Test fun sales_bare_party_and_cash() {
        check("50 kela beche 9000 ki", Expect("Sales", 9000.0, "Cash-in-hand", "Banana", 50.0))
        check("mal becha 2000", Expect("Sales", 2000.0, "Cash-in-hand", null, 1.0))
        check("50000 ka mal becha", Expect("Sales", 50000.0, "Cash-in-hand", null, 1.0))
    }

    // ───────────────────────────── PAYMENTS ─────────────────────────────

    @Test fun payments_ko_diya() {
        check("Ramesh ko 5000 diye", Expect("Payment", 5000.0, "Ramesh"))
        check("Ramesh ko 5000 diya", Expect("Payment", 5000.0, "Ramesh"))
        check("5000 diye Ramesh ko", Expect("Payment", 5000.0, "Ramesh"))
        check("Ramesh ko 5000 de diya", Expect("Payment", 5000.0, "Ramesh"))
        check("Ramesh ko 5000 chukaya", Expect("Payment", 5000.0, "Ramesh"))
        check("Ramesh ko 5000 pay kiya", Expect("Payment", 5000.0, "Ramesh"))
    }

    @Test fun payments_expenses() {
        check("kiraya diya 800", Expect("Payment", 800.0, null))
        check("rent diya 15000", Expect("Payment", 15000.0, null))
        check("salary diya 12000", Expect("Payment", 12000.0, null))
        check("pagar diya 12000", Expect("Payment", 12000.0, null))
        check("bijli diya 900", Expect("Payment", 900.0, null))
        check("petrol diya 500", Expect("Payment", 500.0, null))
        check("diesel diya 3000", Expect("Payment", 3000.0, null))
    }

    @Test fun payments_english() {
        check("paid 5000 to Ramesh", Expect("Payment", 5000.0, "Ramesh"))
        check("paid Ramesh 5000", Expect("Payment", 5000.0, "Ramesh"))
        check("gave 5000 to Ramesh", Expect("Payment", 5000.0, "Ramesh"))
        check("payment 5000 to Ramesh", Expect("Payment", 5000.0, "Ramesh"))
    }

    // ───────────────────────────── RECEIPTS ─────────────────────────────

    @Test fun receipts_se_liya() {
        check("Gagan se 2000 liya", Expect("Receipt", 2000.0, "Gagan"))
        check("Gagan se 2000 liye", Expect("Receipt", 2000.0, "Gagan"))
        check("2000 liye Gagan se", Expect("Receipt", 2000.0, "Gagan"))
        check("Gagan se 2000 mila", Expect("Receipt", 2000.0, "Gagan"))
        check("Gagan se 2000 mile", Expect("Receipt", 2000.0, "Gagan"))
        check("Gagan se 2000 lena", Expect("Receipt", 2000.0, "Gagan"))
    }

    @Test fun receipts_english() {
        check("received 2000 from Gagan", Expect("Receipt", 2000.0, "Gagan"))
        check("got 2000 from Gagan", Expect("Receipt", 2000.0, "Gagan"))
        check("receipt 2000 from Gagan", Expect("Receipt", 2000.0, "Gagan"))
        check("received from Gagan 2000", Expect("Receipt", 2000.0, "Gagan"))
    }

    // ───────────────────────────── AMOUNT FORMATS ─────────────────────────────

    @Test fun amount_formats() {
        check("50 seb kharida Ram se 10,000 ki", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("50 seb kharida Ram se ₹10000", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("50 seb kharida Ram se 10000 rupaye", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("50 seb kharida Ram se 10000 rs", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("50 seb kharida Ram se 10000.50 ki", Expect("Purchase", 10000.50, "Ram", "Apple", 50.0))
    }

    @Test fun indian_number_grouping() {
        check("50 seb kharida Ram se 83 500 ki", Expect("Purchase", 83500.0, "Ram", "Apple", 50.0))
        check("50 seb kharida Ram se 1 500 ki", Expect("Purchase", 1500.0, "Ram", "Apple", 50.0))
    }

    // ───────────────────────────── FULL ENGLISH ─────────────────────────────

    @Test fun english_sales_and_purchases() {
        check("sold 5 bananas to Amit for 200", Expect("Sales", 200.0, "Amit", "Banana", 5.0))
        check("bought 10 kg rice from Ramesh for 5000", Expect("Purchase", 5000.0, "Ramesh", "Rice", 10.0))
        check("purchased 20 apples for 1000", Expect("Purchase", 1000.0, "Cash-in-hand", "Apple", 20.0))
    }

    // ───────────────────────────── DEVANAGARI (Hindi script) ─────────────────────────────

    @Test fun devanagari_transactions() {
        check("गगन से 2000 लिया", Expect("Receipt", 2000.0, "Gagan"))
        check("राम को 5000 दिए", Expect("Payment", 5000.0, null))
        check("50 सेब खरीदा राम से 10000 की", Expect("Purchase", 10000.0, null, "Apple", 50.0))
        check("50 केला बेचा राम को 9000 की", Expect("Sales", 9000.0, null, "Banana", 50.0))
    }

    // ───────────────────────────── FRACTIONAL & LARGE NUMBER WORDS ─────────────────────────────

    @Test fun fractional_and_large_number_words() {
        check("dhaai lakh ka maal kharida", Expect("Purchase", 250000.0, "Cash-in-hand", null, 1.0))
        check("dedh lakh ka maal kharida", Expect("Purchase", 150000.0, "Cash-in-hand", null, 1.0))
        check("pachaas hazaar ka maal kharida", Expect("Purchase", 50000.0, "Cash-in-hand", null, 1.0))
        check("das hazaar ki chawal kharidi", Expect("Purchase", 10000.0, "Cash-in-hand", "Rice", 1.0))
        check("ek karod ka maal becha", Expect("Sales", 10000000.0, "Cash-in-hand", null, 1.0))
    }

    // ───────────────────────────── CONNECTORS (ne / me) ─────────────────────────────

    @Test fun connectors_ne_me() {
        check("Amit ne 50 diya", Expect("Receipt", 50.0, "Amit"))
        check("remote becha 10000 me", Expect("Sales", 10000.0, "Cash-in-hand", null, 1.0))
        check("50 kela becha Ram ko 9000 me", Expect("Sales", 9000.0, "Ram", "Banana", 50.0))
    }

    // ───────────────────────────── NEGATIVE ─────────────────────────────

    @Test fun garbled_or_incomplete_should_not_draft() {
        assertNull("no amount", parseAny("50 namkin ke packet kharidne se"))
        assertNull("bare number", parseAny("50"))
        assertNull("no verb", parseAny("the Splendor VPS Se 500000 Ki"))
        assertNull("question", parseAny("kitna cash bacha hai"))
        assertNull("greeting", parseAny("hello"))
    }
}
