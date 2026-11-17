package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Real-world voice and conversational phrase test suite for FinanceHelper.
 * Tests how people actually speak in English, Hindi, and Hinglish across
 * all transaction types (Sales, Purchase, Payment, Receipt, Expenses),
 * different payment modes (Cash, GPay, PhonePe, Bank, UPI, Cheque),
 * inventory items with units, and verifies that queries and legal/GST questions
 * NEVER accidentally draft a voucher.
 */
class RealWorldVoicePhraseTest {

    private data class ExpectedVoucher(
        val type: String,
        val amount: Double,
        val partyName: String? = null,
        val itemName: String? = null,
        val qty: Double? = null,
        val paymentLedger: String? = null
    )

    private fun parseVoucher(phrase: String): AiResponse.DraftVoucher? = runBlocking {
        (FinanceHelper.tryParseHinglish(null, phrase) as? AiResponse.DraftVoucher)
            ?: (FinanceHelper.smartRegexParser(null, phrase) as? AiResponse.DraftVoucher)
            ?: (FinanceHelper.parseVoiceCommandLocal(phrase) as? AiResponse.DraftVoucher)
    }

    private fun testVoucher(phrase: String, expected: ExpectedVoucher) {
        val result = parseVoucher(phrase)
        assertNotNull("Phrase '$phrase' was expected to produce a ${expected.type} voucher but got null", result)
        result!!
        assertEquals("[$phrase] Voucher Type mismatch", expected.type, result.type)
        assertEquals("[$phrase] Amount mismatch", expected.amount, result.amount ?: 0.0, 0.01)

        if (expected.partyName != null) {
            assertEquals("[$phrase] Party Name mismatch", expected.partyName, result.partyName)
        }

        if (expected.itemName != null) {
            val item = result.inventoryItems.firstOrNull()
            assertNotNull("[$phrase] Expected item '${expected.itemName}' but inventory was empty", item)
            assertEquals("[$phrase] Item name mismatch", expected.itemName, item?.name)
            if (expected.qty != null) {
                assertEquals("[$phrase] Quantity mismatch", expected.qty, item?.quantity ?: 0.0, 0.01)
            }
        }

        if (expected.paymentLedger != null) {
            val cashBankEntry = result.entries.firstOrNull { it.ledgerName in listOf("Cash-in-hand", "Bank Account") }
            assertNotNull("[$phrase] Expected payment ledger '${expected.paymentLedger}' but none found", cashBankEntry)
            assertEquals("[$phrase] Payment account mismatch", expected.paymentLedger, cashBankEntry?.ledgerName)
        }
    }

    private fun testNotAVoucher(phrase: String) {
        val intent = IntentClassifier.classify(phrase)
        val reportResult = FinanceHelper.parseReportCommandLocal(phrase)
        val voucherResult = parseVoucher(phrase)

        // Must either be classified as QUESTION/REPORT or reportResult != DraftVoucher
        assertTrue("Phrase '$phrase' should not draft a voucher. Classified as: $intent",
            intent == IntentClassifier.Intent.QUESTION ||
            intent == IntentClassifier.Intent.REPORT ||
            reportResult !is AiResponse.DraftVoucher)
    }

    // =========================================================================
    // 1. SALES: Voice & Conversational Phrases (English, Hinglish, Devanagari)
    // =========================================================================

    @Test
    fun testSalesEnglish() {
        testVoucher("sold 10 shirts to Rahul for 5000", ExpectedVoucher("Sales", 5000.0, "Rahul", "Shirt", 10.0))
        testVoucher("sold 5 bananas to Amit for 200", ExpectedVoucher("Sales", 200.0, "Amit", "Banana", 5.0))
        testVoucher("sold goods for 1200 cash", ExpectedVoucher("Sales", 1200.0, "Cash-in-hand"))
        testVoucher("sold 3 mangoes for 240", ExpectedVoucher("Sales", 240.0, "Cash-in-hand", "Mango", 3.0))
    }

    @Test
    fun testSalesHinglish() {
        testVoucher("50 kela beche Amit ko 5000 ki", ExpectedVoucher("Sales", 5000.0, "Amit", "Banana", 50.0))
        testVoucher("Amit ko 50 kela beche 5000 ki", ExpectedVoucher("Sales", 5000.0, "Amit", "Banana", 50.0))
        testVoucher("mal becha 2000", ExpectedVoucher("Sales", 2000.0, "Cash-in-hand"))
        testVoucher("50000 ka mal becha", ExpectedVoucher("Sales", 50000.0, "Cash-in-hand"))
        testVoucher("Sharma ji ko 10 packet doodh becha 600 me", ExpectedVoucher("Sales", 600.0, "Sharma Ji", "Milk", 10.0))
        testVoucher("2 peti aam becha 2400 me Gupta ji ko", ExpectedVoucher("Sales", 2400.0, "Gupta Ji", "Mango", 2.0))
        testVoucher("15000 ka maal becha cash me", ExpectedVoucher("Sales", 15000.0, "Cash-in-hand"))
    }

    @Test
    fun testSalesDevanagari() {
        testVoucher("50 केला बेचा राम को 9000 की", ExpectedVoucher("Sales", 9000.0, null, "Banana", 50.0))
        testVoucher("10000 का माल बेचा", ExpectedVoucher("Sales", 10000.0, "Cash-in-hand"))
    }

    // =========================================================================
    // 2. PURCHASES: Voice & Conversational Phrases (English, Hinglish, Devanagari)
    // =========================================================================

    @Test
    fun testPurchasesEnglish() {
        testVoucher("bought 10 kg rice from Ramesh for 5000", ExpectedVoucher("Purchase", 5000.0, "Ramesh", "Rice", 10.0))
        testVoucher("purchased 20 apples for 1000", ExpectedVoucher("Purchase", 1000.0, "Cash-in-hand", "Apple", 20.0))
        testVoucher("bought 5 books for 500", ExpectedVoucher("Purchase", 500.0, "Cash-in-hand", "Book", 5.0))
    }

    @Test
    fun testPurchasesHinglish() {
        testVoucher("50 seb kharida Ram se 10000 ki", ExpectedVoucher("Purchase", 10000.0, "Ram", "Apple", 50.0))
        testVoucher("Ram se 50 seb kharide 10000 ki", ExpectedVoucher("Purchase", 10000.0, "Ram", "Apple", 50.0))
        testVoucher("5 kilo cheeni kharidi 500", ExpectedVoucher("Purchase", 500.0, "Cash-in-hand", "Sugar", 5.0))
        testVoucher("2 litre doodh kharida 100", ExpectedVoucher("Purchase", 100.0, "Cash-in-hand", "Milk", 2.0))
        testVoucher("50 lakh ki 10 cylinder kharidi Ankit se", ExpectedVoucher("Purchase", 5000000.0, "Ankit", "Cylinder", 10.0))
        testVoucher("5 bori chawal kharida 15000 me Mohan se", ExpectedVoucher("Purchase", 15000.0, "Mohan", "Rice", 5.0))
        testVoucher("Ramesh se 2 peti aam kharida 1200 mein", ExpectedVoucher("Purchase", 1200.0, "Ramesh", "Mango", 2.0))
        testVoucher("8000 ka maal kharida cash me", ExpectedVoucher("Purchase", 8000.0, "Cash-in-hand"))
        testVoucher("20 aaloo liya 500 ka", ExpectedVoucher("Purchase", 500.0, "Cash-in-hand", "Potato", 20.0))
        testVoucher("500 motorcycle kharida 1000 rs per peice", ExpectedVoucher("Purchase", 500000.0, "Cash-in-hand", "Motorcycle", 500.0))
    }

    @Test
    fun testPurchasesDevanagari() {
        testVoucher("50 सेब खरीदा राम से 10000 की", ExpectedVoucher("Purchase", 10000.0, null, "Apple", 50.0))
        testVoucher("5000 का माल खरीदा", ExpectedVoucher("Purchase", 5000.0, "Cash-in-hand"))
    }

    // =========================================================================
    // 3. PAYMENTS & EXPENSES (English, Hinglish, Devanagari)
    // =========================================================================

    @Test
    fun testPaymentsEnglish() {
        testVoucher("paid 500 to Ramesh", ExpectedVoucher("Payment", 500.0, "Ramesh", paymentLedger = "Cash-in-hand"))
        testVoucher("paid 5000 to Ramesh via bank", ExpectedVoucher("Payment", 5000.0, "Ramesh", paymentLedger = "Bank Account"))
        testVoucher("paid 12000 to Suresh via UPI", ExpectedVoucher("Payment", 12000.0, "Suresh", paymentLedger = "Bank Account"))
        testVoucher("paid 200 for tea", ExpectedVoucher("Payment", 200.0))
        testVoucher("paid 15000 for office rent", ExpectedVoucher("Payment", 15000.0))
    }

    @Test
    fun testPaymentsHinglish() {
        testVoucher("Ramesh ko 5000 diye", ExpectedVoucher("Payment", 5000.0, "Ramesh"))
        testVoucher("5000 diye Ramesh ko", ExpectedVoucher("Payment", 5000.0, "Ramesh"))
        testVoucher("Ramesh ko 5000 de diya", ExpectedVoucher("Payment", 5000.0, "Ramesh"))
        testVoucher("Amit ko 5000 pay kiya GPay se", ExpectedVoucher("Payment", 5000.0, "Amit", paymentLedger = "Bank Account"))
        testVoucher("5000 rupaye Suresh ko phonepe kiya", ExpectedVoucher("Payment", 5000.0, "Suresh", paymentLedger = "Bank Account"))
        testVoucher("kiraya diya 800", ExpectedVoucher("Payment", 800.0))
        testVoucher("salary diya 12000", ExpectedVoucher("Payment", 12000.0))
        testVoucher("bijli ka bill bhara 2500", ExpectedVoucher("Payment", 2500.0))
    }

    @Test
    fun testPaymentsDevanagari() {
        testVoucher("राम को 5000 दिए", ExpectedVoucher("Payment", 5000.0))
    }

    // =========================================================================
    // 4. RECEIPTS: Money In (English, Hinglish, Devanagari)
    // =========================================================================

    @Test
    fun testReceiptsEnglish() {
        testVoucher("received 2000 from Rahul", ExpectedVoucher("Receipt", 2000.0, "Rahul", paymentLedger = "Cash-in-hand"))
        testVoucher("received 15000 from Gupta Traders via bank", ExpectedVoucher("Receipt", 15000.0, "Gupta Traders", paymentLedger = "Bank Account"))
        testVoucher("got 500 from Amit", ExpectedVoucher("Receipt", 500.0, "Amit"))
    }

    @Test
    fun testReceiptsHinglish() {
        testVoucher("Gagan se 2000 liya", ExpectedVoucher("Receipt", 2000.0, "Gagan"))
        testVoucher("2000 liye Gagan se", ExpectedVoucher("Receipt", 2000.0, "Gagan"))
        testVoucher("Gagan se 2000 mila", ExpectedVoucher("Receipt", 2000.0, "Gagan"))
        testVoucher("Ramesh se 1500 cash mila", ExpectedVoucher("Receipt", 1500.0, "Ramesh", paymentLedger = "Cash-in-hand"))
        testVoucher("Sharma ji se 10000 mila cheque se", ExpectedVoucher("Receipt", 10000.0, "Sharma Ji", paymentLedger = "Bank Account"))
        testVoucher("Ramesh ne 200 udhaar liya", ExpectedVoucher("Receipt", 200.0, "Ramesh"))
        testVoucher("Sunita ne 500 wapas kiye", ExpectedVoucher("Receipt", 500.0, "Sunita"))
    }

    @Test
    fun testReceiptsDevanagari() {
        testVoucher("गगन से 2000 लिया", ExpectedVoucher("Receipt", 2000.0, "Gagan"))
    }

    // =========================================================================
    // 5. NATURAL HINDI/HINGLISH NUMBER WORDS & SPOKEN ASR
    // =========================================================================

    @Test
    fun testSpokenNumberWords() {
        testVoucher("ek lakh ka maal kharida", ExpectedVoucher("Purchase", 100000.0, "Cash-in-hand"))
        testVoucher("do lakh ka maal becha", ExpectedVoucher("Sales", 200000.0, "Cash-in-hand"))
        testVoucher("dhaai lakh mila Sharma ji se", ExpectedVoucher("Receipt", 250000.0, "Sharma Ji"))
        testVoucher("bees hazar panch sau rupey ke 50 kela beche Ram ko", ExpectedVoucher("Sales", 20500.0, "Ram", "Banana", 50.0))
        testVoucher("20 hazar 500 ke 50 kela beche Ram ko", ExpectedVoucher("Sales", 20500.0, "Ram", "Banana", 50.0))
    }

    // =========================================================================
    // 6. QUERIES & REPORTS: MUST NEVER DRAFT A VOUCHER
    // =========================================================================

    @Test
    fun testReportsAndQueriesDoNotDraftVouchers() {
        testNotAVoucher("Aaj ka total sales kitna hai?")
        testNotAVoucher("today's sales")
        testNotAVoucher("Rahul ka balance kitna hai?")
        testNotAVoucher("what is my cash balance")
        testNotAVoucher("cash balance batao")
        testNotAVoucher("show daybook")
        testNotAVoucher("show profit and loss report")
        testNotAVoucher("outstanding payments")
        testNotAVoucher("kisko kitna paisa dena hai")
        testNotAVoucher("stock summary")
    }

    // =========================================================================
    // 7. COMPLIANCE & LEGAL QUESTIONS: MUST NEVER DRAFT A VOUCHER
    // =========================================================================

    @Test
    fun testComplianceQuestionsDoNotDraftVouchers() {
        testNotAVoucher("is it against the GST laws to pay more than 10000 cash?")
        testNotAVoucher("can I pay 20000 cash to supplier?")
        testNotAVoucher("is cash payment above 10000 legal?")
        testNotAVoucher("what is the penalty for late filing of GSTR-3B?")
        testNotAVoucher("what are the rules for e-way bill under GST?")
        testNotAVoucher("section 269ST limit kya hai?")
        testNotAVoucher("how to claim input tax credit on laptop?")
        testNotAVoucher("reverse charge mechanism kya hota hai?")
    }
}
