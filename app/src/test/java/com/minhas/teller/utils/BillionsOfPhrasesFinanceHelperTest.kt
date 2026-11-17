package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Massive phrase validation test suite testing real-world conversational,
 * speech-to-text, Hindi, English, Hinglish, and Devanagari interactions
 * with FinanceHelper.
 */
class BillionsOfPhrasesFinanceHelperTest {

    private fun parseVoucher(phrase: String): AiResponse.DraftVoucher? = runBlocking {
        (FinanceHelper.tryParseHinglish(null, phrase) as? AiResponse.DraftVoucher)
            ?: (FinanceHelper.smartRegexParser(null, phrase) as? AiResponse.DraftVoucher)
            ?: (FinanceHelper.parseVoiceCommandLocal(phrase) as? AiResponse.DraftVoucher)
    }

    private fun assertVoucher(
        phrase: String,
        expectedType: String,
        expectedAmount: Double,
        expectedParty: String? = null,
        expectedItem: String? = null,
        expectedQty: Double? = null,
        expectedPaymentAccount: String? = null,
        expectedUnit: String? = null
    ) {
        val result = parseVoucher(phrase)
        assertNotNull("Phrase '$phrase' was expected to produce a $expectedType voucher of $expectedAmount but returned null", result)
        result!!
        assertEquals("[$phrase] Voucher Type mismatch", expectedType, result.type)
        assertEquals("[$phrase] Amount mismatch", expectedAmount, result.amount ?: 0.0, 0.01)

        if (expectedParty != null) {
            assertEquals("[$phrase] Party mismatch", expectedParty.lowercase(), result.partyName?.lowercase())
        }

        if (expectedItem != null) {
            val item = result.inventoryItems.firstOrNull()
            assertNotNull("[$phrase] Expected item '$expectedItem' but inventoryItems was empty", item)
            assertEquals("[$phrase] Item name mismatch", expectedItem.lowercase(), item?.name?.lowercase())
            if (expectedQty != null) {
                assertEquals("[$phrase] Quantity mismatch", expectedQty, item?.quantity ?: 0.0, 0.01)
            }
            if (expectedUnit != null) {
                assertEquals("[$phrase] Unit mismatch", expectedUnit.lowercase(), item?.unit?.lowercase())
            }
        }

        if (expectedPaymentAccount != null) {
            val cashBank = result.entries.firstOrNull { it.ledgerName in listOf("Cash-in-hand", "Bank Account") }
            assertNotNull("[$phrase] Expected cash/bank account '$expectedPaymentAccount' but none found", cashBank)
            assertEquals("[$phrase] Payment ledger mismatch", expectedPaymentAccount, cashBank?.ledgerName)
        }
    }

    private fun assertNotVoucher(phrase: String) {
        val reportResult = FinanceHelper.parseReportCommandLocal(phrase)
        val voucherResult = parseVoucher(phrase)
        val isTaxQuery = IntentClassifier.classify(phrase) == IntentClassifier.Intent.QUESTION || SmartFinancePrompts.isTaxLawQuery(phrase) || FinanceHelper.isGstQuery(phrase)

        assertTrue(
            "Phrase '$phrase' should be recognized as a report/question, not a draft voucher",
            isTaxQuery || reportResult != null || voucherResult == null
        )
    }

    // ── 1. Real-World Online Voice Patterns: Khatabook, Vyapar, myBillBook ──

    @Test
    fun testRealWorldRetailKhatabookAndVyaparPhrases() {
        // Customer payments received via UPI / cash / ne / jama
        assertVoucher("Customer ne 3000 cash diya", "Receipt", 3000.0, "Customer", expectedPaymentAccount = "Cash-in-hand")
        assertVoucher("Sharma ji ne 5000 cheque diya", "Receipt", 5000.0, "Sharma Ji", expectedPaymentAccount = "Bank Account")
        assertVoucher("Rahul ne 2000 bheje", "Receipt", 2000.0, "Rahul")
        assertVoucher("Amit ne 10000 transfer kiye", "Receipt", 10000.0, "Amit", expectedPaymentAccount = "Bank Account")
        assertVoucher("Ramesh se 5000 jama hua", "Receipt", 5000.0, "Ramesh")
        assertVoucher("Kiran ka 2500 jama kiya", "Receipt", 2500.0, "Kiran")

        // Payment-in / Payment-out
        assertVoucher("Payment in 2500 from Vijay", "Receipt", 2500.0, "Vijay")
        assertVoucher("Payment out 10000 to Wholesaler", "Payment", 10000.0, "Wholesaler")
        assertVoucher("Wholesaler ko payment out 10000", "Payment", 10000.0, "Wholesaler")

        // Udhar entries
        assertVoucher("Rajesh ko 2000 udhar diya", "Payment", 2000.0, "Rajesh")
        assertVoucher("Rajesh ko 2000 rupay udhar diya", "Payment", 2000.0, "Rajesh")
        assertVoucher("Ramesh se 5000 udhar liya", "Receipt", 5000.0, "Ramesh")

        // Voice Billing & Invoice generation
        assertVoucher("Sharma ji ka bill banao 5000", "Sales", 5000.0, "Sharma Ji")
        assertVoucher("Sharma ji ko 10 packet doodh ka bill banao 600 me", "Sales", 600.0, "Sharma Ji", "Milk", 10.0)
        assertVoucher("50 kilo basmati chawal ka bill banao 4000 me Ramesh ko", "Sales", 4000.0, "Ramesh", "Basmati Rice", 50.0)
        assertVoucher("Create invoice Rahul 10000", "Sales", 10000.0, "Rahul")

        // Counter sales & purchases
        assertVoucher("Counter sale 1500", "Sales", 1500.0, "Cash-in-hand")
        assertVoucher("Cash sale 5000", "Sales", 5000.0, "Cash-in-hand")
        assertVoucher("Counter purchase 3000", "Purchase", 3000.0, "Cash-in-hand")
        assertVoucher("Cash purchase 5000", "Purchase", 5000.0, "Cash-in-hand")
        assertVoucher("Stock in 5000 from Ram", "Purchase", 5000.0, "Ram")
    }

    // ── 2. Expenses with real-world terms (bhara, dalwaya, nashta, chai) ──

    @Test
    fun testRealWorldExpenses() {
        assertVoucher("Bijli ka bill 1200 bhara", "Payment", 1200.0)
        assertVoucher("Dukan ka kiraya 15000 bhara", "Payment", 15000.0)
        assertVoucher("Driver ki salary 12000 di", "Payment", 12000.0)
        assertVoucher("Chai nashta 200", "Payment", 200.0)
        assertVoucher("Chai pani 150 cash diya", "Payment", 150.0)
        assertVoucher("Dukan ka kharcha 500", "Payment", 500.0)
        assertVoucher("Office kharcha 750", "Payment", 750.0)
        assertVoucher("Gaadi me petrol 500 ka dalwaya", "Payment", 500.0)
    }

    // ── 3. Shorthand modern amounts (5k, 10k, 2.5k, 1cr, 5lakh) ──

    @Test
    fun testShorthandModernAmounts() {
        assertVoucher("Ramesh ko 5k diya", "Payment", 5000.0, "Ramesh")
        assertVoucher("Received 10k from Suresh", "Receipt", 10000.0, "Suresh")
        assertVoucher("Sold 5 apples to Amit for 2k", "Sales", 2000.0, "Amit", "Apple", 5.0)
        assertVoucher("Bought 10 cement from Raj for 5k", "Purchase", 5000.0, "Raj", "Cement", 10.0)
        assertVoucher("Amit ko 2.5k diye", "Payment", 2500.0, "Amit")
        assertVoucher("Suresh se 1cr mila", "Receipt", 10000000.0, "Suresh")
    }

    // ── 4. Devanagari numerals and script ──

    @Test
    fun testDevanagariPhrases() {
        assertVoucher("सुरेश को ५००० रुपये दिए", "Payment", 5000.0)
        assertVoucher("सुरेश को 5000 रुपये दिए", "Payment", 5000.0)
        assertVoucher("रमेश से २००० रुपये मिले", "Receipt", 2000.0)
        assertVoucher("५० किलो चावल बेचा अमित को २५०० में", "Sales", 2500.0, null, "Rice", 50.0)
        assertVoucher("१० बोरी सीमेंट खरीदा राज से ३५०० में", "Purchase", 3500.0, null, "Cement", 10.0)
        assertVoucher("दुकान का किराया १०००० भरा", "Payment", 10000.0)
        assertVoucher("५०००० का माल बेचा", "Sales", 50000.0, "Cash-in-hand")
    }

    // ── 5. Multi-Word Indian Products & Brands ──

    @Test
    fun testMultiWordProducts() {
        assertVoucher("sold 10 basmati rice to Rahul for 1200", "Sales", 1200.0, "Rahul", "Basmati Rice", 10.0)
        assertVoucher("bought 5 mustard oil from Ram for 1000", "Purchase", 1000.0, "Ram", "Mustard Oil", 5.0)
        assertVoucher("10 finolex wire becha Sharma hardware ko 14500", "Sales", 14500.0, "Sharma Hardware", "Finolex Wire", 10.0)
        assertVoucher("2 cooler kharida Gupta ji se 16000", "Purchase", 16000.0, "Gupta Ji", "Air Cooler", 2.0)
        assertVoucher("50 packet chai patti bechi 2500 me", "Sales", 2500.0, "Cash-in-hand", "Tea Leaves", 50.0)
    }

    // ── 6. Combinatorial Sweep (Testing thousands of generated natural variations) ──

    @Test
    fun testCombinatorialThousandsOfPhrases() {
        val testItems = listOf(
            "basmati chawal" to "Basmati Rice",
            "finolex wire" to "Finolex Wire",
            "cooler" to "Air Cooler",
            "dawai" to "Medicine",
            "cheeni" to "Sugar",
            "doodh" to "Milk",
            "atta" to "Flour",
            "tel" to "Oil",
            "ghee" to "Ghee",
            "cement" to "Cement"
        )
        val testParties = listOf("Sharma Ji", "Gupta Traders", "Suresh Kirana", "Amit Verma", "Rahul")
        val testAmts = listOf("1000" to 1000.0, "5000" to 5000.0, "10000" to 10000.0, "50000" to 50000.0)

        var totalTested = 0
        val failures = mutableListOf<String>()

        for ((itemHindi, itemEn) in testItems) {
            for (party in testParties) {
                for ((amtStr, amtVal) in testAmts) {
                    // Pattern A: [party] ko [qty] [item] beche [amt]
                    val pA = "$party ko 10 $itemHindi beche $amtStr"
                    val rA = parseVoucher(pA)
                    totalTested++
                    if (rA == null || rA.type != "Sales" || (rA.amount ?: 0.0) != amtVal) {
                        failures.add("$pA => got $rA")
                    }

                    // Pattern B: [qty] [item] kharide [party] se [amt]
                    val pB = "10 $itemHindi kharide $party se $amtStr"
                    val rB = parseVoucher(pB)
                    totalTested++
                    if (rB == null || rB.type != "Purchase" || (rB.amount ?: 0.0) != amtVal) {
                        failures.add("$pB => got $rB")
                    }

                    // Pattern C: [party] ne [amt] cash diya (Receipt)
                    val pC = "$party ne $amtStr cash diya"
                    val rC = parseVoucher(pC)
                    totalTested++
                    if (rC == null || rC.type != "Receipt" || (rC.amount ?: 0.0) != amtVal) {
                        failures.add("$pC => got $rC")
                    }

                    // Pattern D: [party] ko [amt] gpay kiya (Payment)
                    val pD = "$party ko $amtStr gpay kiya"
                    val rD = parseVoucher(pD)
                    totalTested++
                    if (rD == null || rD.type != "Payment" || (rD.amount ?: 0.0) != amtVal) {
                        failures.add("$pD => got $rD")
                    }
                }
            }
        }

        println("Combinatorial matrix passed: ${totalTested - failures.size} / $totalTested (failures=${failures.size})")
        if (failures.isNotEmpty()) {
            failures.take(20).forEach { println("FAIL: $it") }
        }
        assertEquals("Failures encountered in combinatorial test:\n${failures.take(30).joinToString("\n")}", 0, failures.size)
    }

    // ── 7. Queries vs Transactions Gate: Verify Queries NEVER draft vouchers ──

    @Test
    fun testQueriesNeverDraftVouchers() {
        assertNotVoucher("is paying cash above 10000 illegal?")
        assertNotVoucher("what is the cash balance?")
        assertNotVoucher("show sales today")
        assertNotVoucher("how much profit this month")
        assertNotVoucher("who owes me")
        assertNotVoucher("how much is my gst payable")
        assertNotVoucher("show recent vouchers")
    }

    // ── 8. Permutations: Party First / Middle / Last & Amount First / Middle / Last ──

    @Test
    fun testAllPartyAndAmountPermutations() {
        // --- PAYMENTS ---
        // Party First, Amount Middle
        assertVoucher("Ramesh ko 5000 diye", "Payment", 5000.0, "Ramesh")
        assertVoucher("Ramesh 5000 paid", "Payment", 5000.0, "Ramesh")
        assertVoucher("Ramesh ko 5000 pay kiya cash me", "Payment", 5000.0, "Ramesh")
        assertVoucher("Ramesh 5000 diya", "Payment", 5000.0, "Ramesh")

        // Party First, Amount Last
        assertVoucher("Ramesh ko payment 5000", "Payment", 5000.0, "Ramesh")
        assertVoucher("Ramesh ko pay kiya 5000", "Payment", 5000.0, "Ramesh")
        assertVoucher("Ramesh payment 5000", "Payment", 5000.0, "Ramesh")
        assertVoucher("Ramesh ko diye 5000", "Payment", 5000.0, "Ramesh")

        // Party Middle, Amount First
        assertVoucher("5000 Ramesh ko diye", "Payment", 5000.0, "Ramesh")
        assertVoucher("5000 Ramesh ko pay kiya", "Payment", 5000.0, "Ramesh")
        assertVoucher("5000 cash Ramesh ko diya", "Payment", 5000.0, "Ramesh")

        // Party Middle, Amount Last
        assertVoucher("Paid Ramesh 5000", "Payment", 5000.0, "Ramesh")
        assertVoucher("Paid to Ramesh 5000", "Payment", 5000.0, "Ramesh")
        assertVoucher("Payment to Ramesh 5000", "Payment", 5000.0, "Ramesh")
        assertVoucher("Diya Ramesh ko 5000", "Payment", 5000.0, "Ramesh")
        assertVoucher("Diye Ramesh ko 5000 cash", "Payment", 5000.0, "Ramesh")

        // Party Last, Amount First
        assertVoucher("5000 paid to Ramesh", "Payment", 5000.0, "Ramesh")
        assertVoucher("5000 payment to Ramesh", "Payment", 5000.0, "Ramesh")
        assertVoucher("5000 diya Ramesh ko", "Payment", 5000.0, "Ramesh")
        assertVoucher("5000 cash diya Ramesh ko", "Payment", 5000.0, "Ramesh")

        // Party Last, Amount Middle
        assertVoucher("Paid 5000 to Ramesh", "Payment", 5000.0, "Ramesh")
        assertVoucher("Payment of 5000 to Ramesh", "Payment", 5000.0, "Ramesh")
        assertVoucher("Diye 5000 Ramesh ko", "Payment", 5000.0, "Ramesh")
        assertVoucher("Pay kiya 5000 Ramesh ko", "Payment", 5000.0, "Ramesh")

        // --- RECEIPTS ---
        // Party First, Amount Middle
        assertVoucher("anita ne 500 diye", "Receipt", 500.0, "Anita")
        assertVoucher("Anita ne 500 diya", "Receipt", 500.0, "Anita")
        assertVoucher("Sharma se 10000 mila", "Receipt", 10000.0, "Sharma")
        assertVoucher("Sharma ji ne 10000 cash diya", "Receipt", 10000.0, "Sharma Ji")
        assertVoucher("Sharma 10000 received", "Receipt", 10000.0, "Sharma")
        assertVoucher("Sharma se 10000 receive hua", "Receipt", 10000.0, "Sharma")

        // Party First, Amount Last
        assertVoucher("Anita ne diye 500", "Receipt", 500.0, "Anita")
        assertVoucher("Sharma se mila 10000", "Receipt", 10000.0, "Sharma")
        assertVoucher("Sharma receipt 10000", "Receipt", 10000.0, "Sharma")
        assertVoucher("Sharma se receive kiya 10000", "Receipt", 10000.0, "Sharma")
        assertVoucher("Sharma ka receipt 10000", "Receipt", 10000.0, "Sharma")

        // Party Middle, Amount First
        assertVoucher("500 Anita ne diye", "Receipt", 500.0, "Anita")
        assertVoucher("10000 Sharma se mila", "Receipt", 10000.0, "Sharma")
        assertVoucher("10000 Sharma se receive hua", "Receipt", 10000.0, "Sharma")
        assertVoucher("10000 cash Sharma ji se mila", "Receipt", 10000.0, "Sharma Ji")

        // Party Middle, Amount Last
        assertVoucher("Received from Sharma 10000", "Receipt", 10000.0, "Sharma")
        assertVoucher("Receipt from Sharma 10000", "Receipt", 10000.0, "Sharma")
        assertVoucher("Mila Sharma se 10000", "Receipt", 10000.0, "Sharma")
        assertVoucher("Aaya Sharma ji se 10000", "Receipt", 10000.0, "Sharma Ji")

        // Party Last, Amount First
        assertVoucher("10000 received from Sharma", "Receipt", 10000.0, "Sharma")
        assertVoucher("10000 receipt from Sharma", "Receipt", 10000.0, "Sharma")
        assertVoucher("10000 mila Sharma se", "Receipt", 10000.0, "Sharma")
        assertVoucher("10000 cash received from Sharma", "Receipt", 10000.0, "Sharma")

        // Party Last, Amount Middle
        assertVoucher("Received 10000 from Sharma", "Receipt", 10000.0, "Sharma")
        assertVoucher("Receipt of 10000 from Sharma", "Receipt", 10000.0, "Sharma")
        assertVoucher("Mila 10000 Sharma se", "Receipt", 10000.0, "Sharma")
        assertVoucher("Receive kiya 10000 Sharma ji se", "Receipt", 10000.0, "Sharma Ji")

        // --- SALES ---
        // Party First, Amount Middle
        assertVoucher("Ramesh ko 5000 ki sale", "Sales", 5000.0, "Ramesh")
        assertVoucher("Ramesh ko 5000 ka maal becha", "Sales", 5000.0, "Ramesh")

        // Party First, Amount Last
        assertVoucher("Ramesh ko sale 5000", "Sales", 5000.0, "Ramesh")
        assertVoucher("Ramesh ko becha 5000", "Sales", 5000.0, "Ramesh")
        assertVoucher("Ramesh sale 5000", "Sales", 5000.0, "Ramesh")
        assertVoucher("Ramesh ka bill banao 5000", "Sales", 5000.0, "Ramesh")

        // Party Middle, Amount First
        assertVoucher("5000 ki sale Ramesh ko", "Sales", 5000.0, "Ramesh")
        assertVoucher("5000 ka maal Ramesh ko becha", "Sales", 5000.0, "Ramesh")

        // Party Middle, Amount Last
        assertVoucher("Sale to Ramesh 5000", "Sales", 5000.0, "Ramesh")
        assertVoucher("Sold to Ramesh 5000", "Sales", 5000.0, "Ramesh")
        assertVoucher("Becha Ramesh ko 5000", "Sales", 5000.0, "Ramesh")
        assertVoucher("Bill banao Ramesh ka 5000", "Sales", 5000.0, "Ramesh")
        assertVoucher("Create invoice for Ramesh 5000", "Sales", 5000.0, "Ramesh")

        // Party Last, Amount First
        assertVoucher("5000 sale to Ramesh", "Sales", 5000.0, "Ramesh")
        assertVoucher("5000 sold to Ramesh", "Sales", 5000.0, "Ramesh")
        assertVoucher("5000 becha Ramesh ko", "Sales", 5000.0, "Ramesh")

        // Party Last, Amount Middle
        assertVoucher("Sale of 5000 to Ramesh", "Sales", 5000.0, "Ramesh")
        assertVoucher("Sold 5000 to Ramesh", "Sales", 5000.0, "Ramesh")
        assertVoucher("Becha 5000 ka maal Ramesh ko", "Sales", 5000.0, "Ramesh")

        // --- PURCHASES ---
        // Party First, Amount Middle
        assertVoucher("Gupta Traders se 10000 ka purchase", "Purchase", 10000.0, "Gupta Traders")
        assertVoucher("Gupta Traders se 10000 ka maal kharida", "Purchase", 10000.0, "Gupta Traders")

        // Party First, Amount Last
        assertVoucher("Gupta Traders se purchase 10000", "Purchase", 10000.0, "Gupta Traders")
        assertVoucher("Gupta Traders se kharida 10000", "Purchase", 10000.0, "Gupta Traders")
        assertVoucher("Gupta Traders purchase 10000", "Purchase", 10000.0, "Gupta Traders")

        // Party Middle, Amount First
        assertVoucher("10000 ka purchase Gupta Traders se", "Purchase", 10000.0, "Gupta Traders")
        assertVoucher("10000 ka maal Gupta Traders se kharida", "Purchase", 10000.0, "Gupta Traders")
        assertVoucher("10000 purchase from Gupta Traders", "Purchase", 10000.0, "Gupta Traders")

        // Party Middle, Amount Last
        assertVoucher("Purchase from Gupta Traders 10000", "Purchase", 10000.0, "Gupta Traders")
        assertVoucher("Bought from Gupta Traders 10000", "Purchase", 10000.0, "Gupta Traders")
        assertVoucher("Kharida Gupta Traders se 10000", "Purchase", 10000.0, "Gupta Traders")

        // Party Last, Amount First
        assertVoucher("10000 purchase from Gupta Traders", "Purchase", 10000.0, "Gupta Traders")
        assertVoucher("10000 bought from Gupta Traders", "Purchase", 10000.0, "Gupta Traders")
        assertVoucher("10000 kharida Gupta Traders se", "Purchase", 10000.0, "Gupta Traders")

        // Party Last, Amount Middle
        assertVoucher("Purchase of 10000 from Gupta Traders", "Purchase", 10000.0, "Gupta Traders")
        assertVoucher("Bought 10000 from Gupta Traders", "Purchase", 10000.0, "Gupta Traders")
        assertVoucher("Kharida 10000 ka maal Gupta Traders se", "Purchase", 10000.0, "Gupta Traders")
    }

    // ── 9. Speech Weight and Volume Units (killo, kg, liter, ltr, etc.) ──

    @Test
    fun testWeightAndVolumeUnitsSpeech() {
        // Sales: weight & volume
        assertVoucher("5 killo aaloo 100 me becha", "Sales", 100.0, expectedParty = "Cash-in-hand", expectedItem = "Potato", expectedQty = 5.0, expectedUnit = "Kg")
        assertVoucher("5 killo aaloo 100", "Sales", 100.0, expectedParty = "Cash-in-hand", expectedItem = "Potato", expectedQty = 5.0, expectedUnit = "Kg")
        assertVoucher("20 liter milk becha 1000 me", "Sales", 1000.0, expectedParty = "Cash-in-hand", expectedItem = "Milk", expectedQty = 20.0, expectedUnit = "Liter")
        assertVoucher("20 liter milk 1000 me", "Sales", 1000.0, expectedParty = "Cash-in-hand", expectedItem = "Milk", expectedQty = 20.0, expectedUnit = "Liter")
        assertVoucher("sell 20 liter milk for 1000", "Sales", 1000.0, expectedParty = "Cash-in-hand", expectedItem = "Milk", expectedQty = 20.0, expectedUnit = "Liter")
        assertVoucher("25 kg chawal 1200 me beche", "Sales", 1200.0, expectedParty = "Cash-in-hand", expectedItem = "Rice", expectedQty = 25.0, expectedUnit = "Kg")
        assertVoucher("2 leeter tel 300 me becha", "Sales", 300.0, expectedParty = "Cash-in-hand", expectedItem = "Oil", expectedQty = 2.0, expectedUnit = "Liter")

        // Purchases: weight & volume
        assertVoucher("10 ltr doodh kharida 500 me", "Purchase", 500.0, expectedParty = "Cash-in-hand", expectedItem = "Milk", expectedQty = 10.0, expectedUnit = "Liter")
        assertVoucher("5 kgs sugar kharida 250 me", "Purchase", 250.0, expectedParty = "Cash-in-hand", expectedItem = "Sugar", expectedQty = 5.0, expectedUnit = "Kg")
    }
}
