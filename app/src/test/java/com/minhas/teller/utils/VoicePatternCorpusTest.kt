package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Broad corpus of English / Hinglish / Hindi voice-transaction patterns, used to find
 * gaps in the 3-tier offline parser. Each case asserts the full expected draft. A failure
 * here means a real spoken phrase would mis-parse.
 */
class VoicePatternCorpusTest {

    private data class Expect(
        val type: String,
        val amount: Double,
        val party: String? = null,
        val item: String? = null,
        val qty: Double = 1.0
    )

    private fun t1(p: String) = runBlocking { FinanceHelper.tryParseHinglish(null, p) as? AiResponse.DraftVoucher }
    private fun t2(p: String) = runBlocking { FinanceHelper.smartRegexParser(null, p) as? AiResponse.DraftVoucher }
    private fun t3(p: String) = FinanceHelper.parseVoiceCommandLocal(p) as? AiResponse.DraftVoucher
    private fun any(p: String) = t1(p) ?: t2(p) ?: t3(p)

    private fun check(p: String, e: Expect) {
        val d = any(p)
        assertNotNull("[$p] -> null (expected ${e.type} ${e.amount})", d)
        d!!
        assertEquals("[$p] type", e.type, d.type)
        assertEquals("[$p] amount", e.amount, d.amount ?: 0.0, 0.001)
        if (e.party != null) assertEquals("[$p] party", e.party, d.partyName)
        if (e.item != null) {
            val it = d.inventoryItems.firstOrNull()
            assertNotNull("[$p] item (expected ${e.item})", it)
            assertEquals("[$p] item", e.item, it?.name)
            assertEquals("[$p] qty", e.qty, it?.quantity ?: 0.0, 0.001)
        }
    }

    // ───────────────────────── ENGLISH ─────────────────────────

    @Test fun english_sales() {
        check("sold 5 bananas to Amit for 200", Expect("Sales", 200.0, "Amit", "Banana", 5.0))
        check("sold 5 bananas for 200", Expect("Sales", 200.0, "Cash-in-hand", "Banana", 5.0))
        check("sold 12 mangoes 20 each", Expect("Sales", 240.0, "Cash-in-hand", "Mango", 12.0))
        check("sold 3 mangoes at 80 each", Expect("Sales", 240.0, "Cash-in-hand", "Mango", 3.0))
    }

    @Test fun english_purchases() {
        check("bought 10 kg rice from Ramesh for 5000", Expect("Purchase", 5000.0, "Ramesh", "Rice", 10.0))
        check("bought 2 apples for 200 each", Expect("Purchase", 400.0, "Cash-in-hand", "Apple", 2.0))
        check("purchased 20 apples for 1000", Expect("Purchase", 1000.0, "Cash-in-hand", "Apple", 20.0))
    }

    @Test fun english_payments_receipts() {
        check("paid 500 to Ramesh", Expect("Payment", 500.0, "Ramesh"))
        check("paid 500 for supplies", Expect("Payment", 500.0, null))
        check("paid 200 for tea", Expect("Payment", 200.0, null))
        check("received 2000 from Rahul", Expect("Receipt", 2000.0, "Rahul"))
        check("got 500 from Amit", Expect("Receipt", 500.0, "Amit"))
    }

    // ───────────────────────── HINGLISH ─────────────────────────

    @Test fun hinglish_sales() {
        check("50 kela beche Amit ko 5000 ki", Expect("Sales", 5000.0, "Amit", "Banana", 50.0))
        check("50 kela becha Amit ko 5000 ka", Expect("Sales", 5000.0, "Amit", "Banana", 50.0))
        check("Amit ko 50 kela beche 5000 ki", Expect("Sales", 5000.0, "Amit", "Banana", 50.0))
        check("50 kela beche 5000 ki Amit ko", Expect("Sales", 5000.0, "Amit", "Banana", 50.0))
        check("50 kela bikri Amit ko 5000 ki", Expect("Sales", 5000.0, "Amit", "Banana", 50.0))
        check("mal becha 2000", Expect("Sales", 2000.0, "Cash-in-hand", null, 1.0))
    }

    @Test fun hinglish_purchases() {
        check("50 seb kharida Ram se 10000 ki", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("Ram se 50 seb kharide 10000 ki", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("50 seb kharidi 10000 ki Ram se", Expect("Purchase", 10000.0, "Ram", "Apple", 50.0))
        check("5 kilo cheeni kharidi 500", Expect("Purchase", 500.0, "Cash-in-hand", "Sugar", 5.0))
        // "amount ki qty item kharidi party se" — amount-first with a qty and party
        check("50 lakh ki 10 cylinder kharidi Ankit se", Expect("Purchase", 5000000.0, "Ankit", "Cylinder", 10.0))
        // "car" misheard as "kar" must not be treated as the aux verb
        check("50 lakh ki 20 kar kharidi Ankit se", Expect("Purchase", 5000000.0, "Ankit", "Car", 20.0))
        check("kar kharidi 50 lakh ki Arvind se", Expect("Purchase", 5000000.0, "Arvind", "Car", 1.0))
        // Items with model numbers (iPhone 12/13/14, OnePlus 11, etc.)
        check("50 iphone 12 kharide 500000 ke amrit se", Expect("Purchase", 50000000.0 / 100, "Amrit", "Iphone 12", 50.0))
        check("50 iphone 13 beche 500000 ke amrit ko", Expect("Sales", 500000.0, "Amrit", "Iphone 13", 50.0))
        check("50 iphone 14 kharide 500000 ke amrit se", Expect("Purchase", 500000.0, "Amrit", "Iphone 14", 50.0))
        check("amrit se 50 iphone 12 kharide 500000 ke", Expect("Purchase", 500000.0, "Amrit", "Iphone 12", 50.0))
        check("amrit ko 50 iphone 12 beche 500000 ke", Expect("Sales", 500000.0, "Amrit", "Iphone 12", 50.0))
        check("iphone 12 kharida amrit se 50000 ke", Expect("Purchase", 50000.0, "Amrit", "Iphone 12", 1.0))
        check("500000 ke 50 iphone 12 kharide amrit se", Expect("Purchase", 500000.0, "Amrit", "Iphone 12", 50.0))
    }

    @Test fun hinglish_payments_receipts() {
        check("Ramesh ko 5000 diye", Expect("Payment", 5000.0, "Ramesh"))
        check("5000 diye Ramesh ko", Expect("Payment", 5000.0, "Ramesh"))
        check("Ramesh ko 5000 de diya", Expect("Payment", 5000.0, "Ramesh"))
        check("Gagan se 2000 liya", Expect("Receipt", 2000.0, "Gagan"))
        check("2000 liye Gagan se", Expect("Receipt", 2000.0, "Gagan"))
        check("Gagan se 2000 mila", Expect("Receipt", 2000.0, "Gagan"))
        check("kiraya diya 800", Expect("Payment", 800.0, null))
        check("salary diya 12000", Expect("Payment", 12000.0, null))
    }

    // ───────────────────────── CREDIT / UDHAAR ─────────────────────────

    @Test fun credit_udhaar() {
        check("Ramesh ne 200 udhaar liya", Expect("Receipt", 200.0, "Ramesh"))
        check("Amit ne 100 dene hai", Expect("Receipt", 100.0, "Amit"))
        check("Sunita ne 500 wapas kiye", Expect("Receipt", 500.0, "Sunita"))
        check("Amit owes 100", Expect("Receipt", 100.0, "Amit"))
    }

    // ───────────────────────── DEVANAGARI ─────────────────────────

    @Test fun devanagari() {
        check("गगन से 2000 लिया", Expect("Receipt", 2000.0, "Gagan"))
        check("राम को 5000 दिए", Expect("Payment", 5000.0, null))
        check("50 सेब खरीदा राम से 10000 की", Expect("Purchase", 10000.0, null, "Apple", 50.0))
        check("50 केला बेचा राम को 9000 की", Expect("Sales", 9000.0, null, "Banana", 50.0))
    }

    // ───────────────────────── HINDI NUMBER WORDS ─────────────────────────
    // (compound 21–99 — the map only had tens + 21–29)

    private fun amountPhrase(amountWord: String, expected: Double) {
        check("$amountWord ka maal becha", Expect("Sales", expected, "Cash-in-hand", null, 1.0))
    }

    @Test fun hindi_compound_numbers() {
        amountPhrase("ikkis", 21.0)
        amountPhrase("bais", 22.0)
        amountPhrase("teis", 23.0)
        amountPhrase("chaubis", 24.0)
        amountPhrase("pachis", 25.0)
        amountPhrase("chabbis", 26.0)
        amountPhrase("sattais", 27.0)
        amountPhrase("athais", 28.0)
        amountPhrase("untis", 29.0)
        amountPhrase("ikattis", 31.0)
        amountPhrase("battis", 32.0)
        amountPhrase("taintis", 33.0)
        amountPhrase("chautis", 34.0)
        amountPhrase("paintis", 35.0)
        amountPhrase("chattis", 36.0)
        amountPhrase("saintis", 37.0)
        amountPhrase("adtis", 38.0)
        amountPhrase("untalis", 39.0)
        amountPhrase("iktalis", 41.0)
        amountPhrase("bayalis", 42.0)
        amountPhrase("taintalis", 43.0)
        amountPhrase("chauvalis", 44.0)
        amountPhrase("paintalis", 45.0)
        amountPhrase("chiyalis", 46.0)
        amountPhrase("saintalis", 47.0)
        amountPhrase("adtalis", 48.0)
        amountPhrase("unchas", 49.0)
        amountPhrase("ikyavan", 51.0)
        amountPhrase("bavan", 52.0)
        amountPhrase("tirpan", 53.0)
        amountPhrase("chauvan", 54.0)
        amountPhrase("pachpan", 55.0)
        amountPhrase("chappan", 56.0)
        amountPhrase("sattavan", 57.0)
        amountPhrase("athavan", 58.0)
        amountPhrase("unsath", 59.0)
        amountPhrase("iksath", 61.0)
        amountPhrase("basath", 62.0)
        amountPhrase("tirsath", 63.0)
        amountPhrase("chausath", 64.0)
        amountPhrase("painsath", 65.0)
        amountPhrase("chiyasath", 66.0)
        amountPhrase("sarsath", 67.0)
        amountPhrase("adsath", 68.0)
        amountPhrase("unhattar", 69.0)
        amountPhrase("ikhattar", 71.0)
        amountPhrase("bahattar", 72.0)
        amountPhrase("tihattar", 73.0)
        amountPhrase("chauhattar", 74.0)
        amountPhrase("pachhattar", 75.0)
        amountPhrase("chhihattar", 76.0)
        amountPhrase("sathattar", 77.0)
        amountPhrase("athhattar", 78.0)
        amountPhrase("unasi", 79.0)
        amountPhrase("ikyasi", 81.0)
        amountPhrase("bayasi", 82.0)
        amountPhrase("tirasi", 83.0)
        amountPhrase("chaurasi", 84.0)
        amountPhrase("pachasi", 85.0)
        amountPhrase("chiyasi", 86.0)
        amountPhrase("satasi", 87.0)
        amountPhrase("athasi", 88.0)
        amountPhrase("navasi", 89.0)
        amountPhrase("ikyanave", 91.0)
        amountPhrase("banave", 92.0)
        amountPhrase("tiranave", 93.0)
        amountPhrase("chauranave", 94.0)
        amountPhrase("pachanave", 95.0)
        amountPhrase("chhiyanave", 96.0)
        amountPhrase("sattanave", 97.0)
        amountPhrase("athanave", 98.0)
        amountPhrase("ninanave", 99.0)
    }

    @Test fun hindi_large_numbers() {
        amountPhrase("ek lakh", 100000.0)
        amountPhrase("do lakh", 200000.0)
        amountPhrase("dhaai lakh", 250000.0)
        amountPhrase("das lakh", 1000000.0)
        amountPhrase("ek crore", 10000000.0)
        amountPhrase("do crore", 20000000.0)
        amountPhrase("ek hazar", 1000.0)
        amountPhrase("das hazar", 10000.0)
        amountPhrase("sau ek", 101.0)
        amountPhrase("sau das", 110.0)
        amountPhrase("teen sau chhappan", 356.0)
        amountPhrase("bees hazar panch sau pachas", 20550.0)
        amountPhrase("bees harjar panchsoo", 20500.0)
        amountPhrase("bees hazar panch sau", 20500.0)
        amountPhrase("panchsoo", 500.0)
        amountPhrase("doso", 200.0)
        amountPhrase("charso", 400.0)
    }

    @Test fun spoken_asr_variations() {
        check("bees harjar panchsoo rupey ke 50 kela beche Ram ko", Expect("Sales", 20500.0, "Ram", "Banana", 50.0))
        check("20 hazar 500 ke 50 kela beche Ram ko", Expect("Sales", 20500.0, "Ram", "Banana", 50.0))
        check("20000 500 ke 50 kela beche Ram ko", Expect("Sales", 20500.0, "Ram", "Banana", 50.0))
        check("50 iphone 12 kharide 500000 ke amrit se", Expect("Purchase", 500000.0, "Amrit", "Iphone 12", 50.0))
    }

    // ───────────────────────── UNITS ─────────────────────────
    @Test fun units() {
        check("5 kilo cheeni kharidi 500", Expect("Purchase", 500.0, "Cash-in-hand", "Sugar", 5.0))
        check("2 litre doodh kharida 100", Expect("Purchase", 100.0, "Cash-in-hand", "Milk", 2.0))
        check("1 darjan kela beche 500", Expect("Sales", 500.0, "Cash-in-hand", "Banana", 1.0))
        check("10 kilo atta kharida Ram se 400", Expect("Purchase", 400.0, "Ram", "Flour", 10.0))
    }
}
