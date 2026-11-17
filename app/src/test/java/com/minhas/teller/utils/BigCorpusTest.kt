package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Broad offline sweep — runs a large set of phrases through the full 3-tier pipeline
 * and reports EVERY mismatch (not just the first), so gaps are visible in one run.
 */
class BigCorpusTest {

    private data class E(val type: String, val amount: Double, val party: String? = null, val item: String? = null, val qty: Double = 1.0)

    private fun t1(p: String) = runBlocking { FinanceHelper.tryParseHinglish(null, p) as? AiResponse.DraftVoucher }
    private fun t2(p: String) = runBlocking { FinanceHelper.smartRegexParser(null, p) as? AiResponse.DraftVoucher }
    private fun t3(p: String) = FinanceHelper.parseVoiceCommandLocal(p) as? AiResponse.DraftVoucher
    private fun any(p: String) = t1(p) ?: t2(p) ?: t3(p)

    private data class Case(val phrase: String, val e: E)
    private val cases = mutableListOf<Case>()
    private fun c(phrase: String, type: String, amount: Double, party: String? = null, item: String? = null, qty: Double = 1.0) =
        cases.add(Case(phrase, E(type, amount, party, item, qty)))

    private fun report(): List<String> {
        val fails = mutableListOf<String>()
        for (case in cases) {
            val d = any(case.phrase)
            val e = case.e
            val problems = mutableListOf<String>()
            if (d == null) problems.add("null (expected ${e.type} ${e.amount})")
            else {
                if (d.type != e.type) problems.add("type=${d.type} (want ${e.type})")
                if ((d.amount ?: 0.0) != e.amount) problems.add("amount=${d.amount} (want ${e.amount})")
                if (e.party != null && d.partyName != e.party) problems.add("party=${d.partyName} (want ${e.party})")
                if (e.item != null) {
                    val it = d.inventoryItems.firstOrNull()
                    if (it == null) problems.add("item=null (want ${e.item})")
                    else {
                        if (it.name != e.item) problems.add("item=${it.name} (want ${e.item})")
                        if (it.quantity != e.qty) problems.add("qty=${it.quantity} (want ${e.qty})")
                    }
                }
            }
            if (problems.isNotEmpty()) fails.add("[${case.phrase}] -> ${problems.joinToString("; ")}")
        }
        return fails
    }

    @Test fun sweep() {
        // ── Sales (English) ──
        c("sold 5 bananas to Amit for 200", "Sales", 200.0, "Amit", "Banana", 5.0)
        c("sold 5 bananas for 200", "Sales", 200.0, "Cash-in-hand", "Banana", 5.0)
        c("sold 5 bananas for 200 to Amit", "Sales", 200.0, "Amit", "Banana", 5.0)
        c("sold to Amit 5 bananas for 200", "Sales", 200.0, "Amit", "Banana", 5.0)
        c("5 bananas sold to Amit for 200", "Sales", 200.0, "Amit", "Banana", 5.0)
        c("sold 5 bananas 200", "Sales", 200.0, "Cash-in-hand", "Banana", 5.0)
        c("sold 5 bananas at 40 each", "Sales", 200.0, "Cash-in-hand", "Banana", 5.0)

        // ── Sales (Hinglish) ──
        c("50 kela beche Amit ko 5000 ki", "Sales", 5000.0, "Amit", "Banana", 50.0)
        c("50 kela becha Amit ko 5000 ka", "Sales", 5000.0, "Amit", "Banana", 50.0)
        c("50 kela bechi Amit ko 5000 ki", "Sales", 5000.0, "Amit", "Banana", 50.0)
        c("50 kela bikri Amit ko 5000", "Sales", 5000.0, "Amit", "Banana", 50.0)
        c("Amit ko 50 kela beche 5000 ki", "Sales", 5000.0, "Amit", "Banana", 50.0)
        c("5000 ki 50 kela beche Amit ko", "Sales", 5000.0, "Amit", "Banana", 50.0)
        c("50 kela beche 5000 ki Amit ko", "Sales", 5000.0, "Amit", "Banana", 50.0)
        c("50 kela beche Amit 5000", "Sales", 5000.0, "Amit", "Banana", 50.0)

        // ── Purchases (English) ──
        c("bought 10 kg rice from Ramesh for 5000", "Purchase", 5000.0, "Ramesh", "Rice", 10.0)
        c("purchased 20 apples for 1000", "Purchase", 1000.0, "Cash-in-hand", "Apple", 20.0)
        c("bought 5 books for 500", "Purchase", 500.0, "Cash-in-hand", "Book", 5.0)
        c("bought 5 books at 100 each", "Purchase", 500.0, "Cash-in-hand", "Book", 5.0)
        c("bought 10 kg rice for 5000 from Ramesh", "Purchase", 5000.0, "Ramesh", "Rice", 10.0)

        // ── Purchases (Hinglish) ──
        c("50 seb kharida Ram se 10000 ki", "Purchase", 10000.0, "Ram", "Apple", 50.0)
        c("50 seb kharidi Ram se 10000 ka", "Purchase", 10000.0, "Ram", "Apple", 50.0)
        c("Ram se 50 seb kharide 10000 ki", "Purchase", 10000.0, "Ram", "Apple", 50.0)
        c("10000 ki 50 seb kharida Ram se", "Purchase", 10000.0, "Ram", "Apple", 50.0)
        c("50 seb kharida 10000 ki Ram se", "Purchase", 10000.0, "Ram", "Apple", 50.0)

        // ── Payments ──
        c("paid 500 to Ramesh", "Payment", 500.0, "Ramesh")
        c("paid Ramesh 500", "Payment", 500.0, "Ramesh")
        c("Ramesh ko 500 diye", "Payment", 500.0, "Ramesh")
        c("500 diye Ramesh ko", "Payment", 500.0, "Ramesh")
        c("kiraya diya 800", "Payment", 800.0)
        c("salary diya 12000", "Payment", 12000.0)

        // ── Receipts ──
        c("received 2000 from Rahul", "Receipt", 2000.0, "Rahul")
        c("got 500 from Amit", "Receipt", 500.0, "Amit")
        c("Rahul se 2000 liya", "Receipt", 2000.0, "Rahul")
        c("2000 liye Rahul se", "Receipt", 2000.0, "Rahul")

        // ── Number words ──
        c("bees hazar ka maal becha", "Sales", 20000.0, "Cash-in-hand")
        c("ek lakh ka maal becha", "Sales", 100000.0, "Cash-in-hand")
        c("50 hazaar ka maal kharida", "Purchase", 50000.0, "Cash-in-hand")
        c("sava lakh ka maal becha", "Sales", 125000.0, "Cash-in-hand")
        c("dedh lakh ka maal becha", "Sales", 150000.0, "Cash-in-hand")
        c("dhaai lakh ka maal kharida", "Purchase", 250000.0, "Cash-in-hand")

        // ── amount ki qty item kharidi party se (regression) ──
        c("50 lakh ki 20 kar kharidi Ankit se", "Purchase", 5000000.0, "Ankit", "Car", 20.0)
        c("50 lakh ki 10 cylinder kharidi Ankit se", "Purchase", 5000000.0, "Ankit", "Cylinder", 10.0)
        c("kar kharidi 50 lakh ki Arvind se", "Purchase", 5000000.0, "Arvind", "Car", 1.0)

        val fails = report()
        println("BIG CORPUS: ${cases.size} cases, ${fails.size} failures")
        fails.forEach { println("FAIL $it") }
        assertTrue("${fails.size} failures:\n" + fails.joinToString("\n"), fails.isEmpty())
    }
}
