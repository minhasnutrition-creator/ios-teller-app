package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive phrase corpus — English, Hinglish, Devanagari — covering both voucher
 * transactions (every verb/word-order/number/unit/connector) and report queries
 * (balance/sales/tax/stock/outstanding/…). Reports EVERY mismatch in one run.
 */
class FullPhraseCorpusTest {

    private data class T(val type: String, val amount: Double, val party: String? = null, val item: String? = null, val qty: Double = 1.0)

    private fun t1(p: String) = runBlocking { FinanceHelper.tryParseHinglish(null, p) as? AiResponse.DraftVoucher }
    private fun t2(p: String) = runBlocking { FinanceHelper.smartRegexParser(null, p) as? AiResponse.DraftVoucher }
    private fun t3(p: String) = FinanceHelper.parseVoiceCommandLocal(p) as? AiResponse.DraftVoucher
    private fun any(p: String) = t1(p) ?: t2(p) ?: t3(p)

    private val tx = mutableListOf<Pair<String, T>>()
    private fun txn(p: String, type: String, amount: Double, party: String? = null, item: String? = null, qty: Double = 1.0) =
        tx.add(p to T(type, amount, party, item, qty))

    private val qs = mutableListOf<Pair<String, String?>>() // phrase -> expected metric (or null = any non-voucher)
    private fun q(phrase: String, metric: String? = null) = qs.add(phrase to metric)

    private fun runTxs(): List<String> {
        val fails = mutableListOf<String>()
        for ((phrase, e) in tx) {
            val d = any(phrase)
            val probs = mutableListOf<String>()
            if (d == null) probs.add("null (want ${e.type} ${e.amount})")
            else {
                if (d.type != e.type) probs.add("type=${d.type}")
                if ((d.amount ?: 0.0) != e.amount) probs.add("amount=${d.amount}")
                if (e.party != null && d.partyName != e.party) probs.add("party=${d.partyName}")
                if (e.item != null) {
                    val it = d.inventoryItems.firstOrNull()
                    if (it == null) probs.add("item=null")
                    else { if (it.name != e.item) probs.add("item=${it.name}"); if (it.quantity != e.qty) probs.add("qty=${it.quantity}") }
                }
            }
            if (probs.isNotEmpty()) fails.add("[TX] $phrase -> ${probs.joinToString("; ")}")
        }
        return fails
    }

    private fun runQueries(): List<String> {
        val fails = mutableListOf<String>()
        for ((phrase, metric) in qs) {
            val r = FinanceHelper.parseReportCommandLocal(phrase)
            if (r == null) { fails.add("[Q] $phrase -> null"); continue }
            if (r is AiResponse.DraftVoucher) { fails.add("[Q] $phrase -> drafted voucher (should be a query)"); continue }
            if (metric != null) {
                val m = (r as? AiResponse.QueryMetric)?.metric
                if (m != metric) fails.add("[Q] $phrase -> metric=$m (want $metric)")
            }
        }
        return fails
    }

    @Test fun full_sweep() {
        // ═══════════════ TRANSACTIONS ═══════════════

        // Sales — English
        txn("sold 5 bananas to Amit for 200", "Sales", 200.0, "Amit", "Banana", 5.0)
        txn("sold 5 bananas for 200", "Sales", 200.0, "Cash-in-hand", "Banana", 5.0)
        txn("sold 5 bananas for 200 to Amit", "Sales", 200.0, "Amit", "Banana", 5.0)
        txn("sold to Amit 5 bananas for 200", "Sales", 200.0, "Amit", "Banana", 5.0)
        txn("5 bananas sold to Amit for 200", "Sales", 200.0, "Amit", "Banana", 5.0)
        txn("sold 5 bananas at 40 each", "Sales", 200.0, "Cash-in-hand", "Banana", 5.0)
        txn("sold a shirt for 500", "Sales", 500.0, "Cash-in-hand", "Shirt", 1.0)

        // Sales — Hinglish
        txn("50 kela beche Amit ko 5000 ki", "Sales", 5000.0, "Amit", "Banana", 50.0)
        txn("50 kela becha Amit ko 5000 ka", "Sales", 5000.0, "Amit", "Banana", 50.0)
        txn("50 kela bechi Amit ko 5000 ki", "Sales", 5000.0, "Amit", "Banana", 50.0)
        txn("50 kela bikri Amit ko 5000", "Sales", 5000.0, "Amit", "Banana", 50.0)
        txn("Amit ko 50 kela beche 5000 ki", "Sales", 5000.0, "Amit", "Banana", 50.0)
        txn("5000 ki 50 kela beche Amit ko", "Sales", 5000.0, "Amit", "Banana", 50.0)
        txn("50 kela beche 5000 ki Amit ko", "Sales", 5000.0, "Amit", "Banana", 50.0)
        txn("50 kela beche Amit 5000", "Sales", 5000.0, "Amit", "Banana", 50.0)
        txn("mal becha 2000", "Sales", 2000.0, "Cash-in-hand")
        txn("50000 ka mal becha", "Sales", 50000.0, "Cash-in-hand")

        // Purchases — English
        txn("bought 10 kg rice from Ramesh for 5000", "Purchase", 5000.0, "Ramesh", "Rice", 10.0)
        txn("purchased 20 apples for 1000", "Purchase", 1000.0, "Cash-in-hand", "Apple", 20.0)
        txn("bought 5 books for 500", "Purchase", 500.0, "Cash-in-hand", "Book", 5.0)
        txn("bought 5 books at 100 each", "Purchase", 500.0, "Cash-in-hand", "Book", 5.0)
        txn("bought 10 kg rice for 5000 from Ramesh", "Purchase", 5000.0, "Ramesh", "Rice", 10.0)

        // Purchases — Hinglish
        txn("50 seb kharida Ram se 10000 ki", "Purchase", 10000.0, "Ram", "Apple", 50.0)
        txn("50 seb kharidi Ram se 10000 ka", "Purchase", 10000.0, "Ram", "Apple", 50.0)
        txn("Ram se 50 seb kharide 10000 ki", "Purchase", 10000.0, "Ram", "Apple", 50.0)
        txn("10000 ki 50 seb kharida Ram se", "Purchase", 10000.0, "Ram", "Apple", 50.0)
        txn("50 seb kharida 10000 ki Ram se", "Purchase", 10000.0, "Ram", "Apple", 50.0)
        txn("50 seb kharida Ram 10000", "Purchase", 10000.0, "Ram", "Apple", 50.0)

        // Payments
        txn("paid 500 to Ramesh", "Payment", 500.0, "Ramesh")
        txn("paid Ramesh 500", "Payment", 500.0, "Ramesh")
        txn("gave 500 to Ramesh", "Payment", 500.0, "Ramesh")
        txn("Ramesh ko 500 diye", "Payment", 500.0, "Ramesh")
        txn("500 diye Ramesh ko", "Payment", 500.0, "Ramesh")
        txn("Ramesh ko 500 de diya", "Payment", 500.0, "Ramesh")
        txn("Ramesh ko 500 chukaya", "Payment", 500.0, "Ramesh")
        txn("kiraya diya 800", "Payment", 800.0)
        txn("salary diya 12000", "Payment", 12000.0)
        txn("rent diya 15000", "Payment", 15000.0)
        txn("bijli diya 900", "Payment", 900.0)

        // Receipts
        txn("received 2000 from Rahul", "Receipt", 2000.0, "Rahul")
        txn("got 500 from Amit", "Receipt", 500.0, "Amit")
        txn("Rahul se 2000 liya", "Receipt", 2000.0, "Rahul")
        txn("2000 liye Rahul se", "Receipt", 2000.0, "Rahul")
        txn("Rahul se 2000 mila", "Receipt", 2000.0, "Rahul")
        txn("Rahul se 2000 lena", "Receipt", 2000.0, "Rahul")

        // Credit / udhaar
        txn("Ramesh ne 200 udhaar liya", "Receipt", 200.0, "Ramesh")
        txn("Amit ne 100 dene hai", "Receipt", 100.0, "Amit")
        txn("Sunita ne 500 wapas kiye", "Receipt", 500.0, "Sunita")
        txn("Amit owes 100", "Receipt", 100.0, "Amit")

        // Units
        txn("5 kilo cheeni kharidi 500", "Purchase", 500.0, "Cash-in-hand", "Sugar", 5.0)
        txn("2 litre doodh kharida 100", "Purchase", 100.0, "Cash-in-hand", "Milk", 2.0)
        txn("1 darjan kela beche 500", "Sales", 500.0, "Cash-in-hand", "Banana", 1.0)
        txn("10 kilo atta kharida Ram se 400", "Purchase", 400.0, "Ram", "Flour", 10.0)
        txn("50 kg chawal kharida Ram se 2000", "Purchase", 2000.0, "Ram", "Rice", 50.0)

        // Number words — Hinglish/English
        txn("bees hazar ka maal becha", "Sales", 20000.0, "Cash-in-hand")
        txn("ek lakh ka maal becha", "Sales", 100000.0, "Cash-in-hand")
        txn("do lakh ka maal becha", "Sales", 200000.0, "Cash-in-hand")
        txn("50 hazaar ka maal kharida", "Purchase", 50000.0, "Cash-in-hand")
        txn("sava lakh ka maal becha", "Sales", 125000.0, "Cash-in-hand")
        txn("dedh lakh ka maal becha", "Sales", 150000.0, "Cash-in-hand")
        txn("dhaai lakh ka maal kharida", "Purchase", 250000.0, "Cash-in-hand")
        txn("ek karod ka maal becha", "Sales", 10000000.0, "Cash-in-hand")
        txn("bees hazar panch sau pachas ka maal becha", "Sales", 20550.0, "Cash-in-hand")
        txn("ikattis ka maal becha", "Sales", 31.0, "Cash-in-hand")
        txn("bavan ka maal becha", "Sales", 52.0, "Cash-in-hand")
        txn("chhappan ka maal becha", "Sales", 56.0, "Cash-in-hand")
        txn("one thousand ka maal becha", "Sales", 1000.0, "Cash-in-hand")
        txn("five thousand ka maal becha", "Sales", 5000.0, "Cash-in-hand")

        // Indian grouping
        txn("50 seb kharida Ram se 83 500 ki", "Purchase", 83500.0, "Ram", "Apple", 50.0)
        txn("50 seb kharida Ram se 1 500 ki", "Purchase", 1500.0, "Ram", "Apple", 50.0)

        // Devanagari
        txn("गगन से 2000 लिया", "Receipt", 2000.0, "Gagan")
        txn("राम को 5000 दिए", "Payment", 5000.0)
        txn("50 सेब खरीदा राम से 10000 की", "Purchase", 10000.0, null, "Apple", 50.0)
        txn("50 केला बेचा राम को 9000 की", "Sales", 9000.0, null, "Banana", 50.0)

        // amount ki qty item kharidi party se (regression)
        txn("50 lakh ki 20 kar kharidi Ankit se", "Purchase", 5000000.0, "Ankit", "Car", 20.0)
        txn("50 lakh ki 10 cylinder kharidi Ankit se", "Purchase", 5000000.0, "Ankit", "Cylinder", 10.0)
        txn("kar kharidi 50 lakh ki Arvind se", "Purchase", 5000000.0, "Arvind", "Car", 1.0)

        // ═══════════════ QUERIES ═══════════════

        // Tax / GST
        q("mera tax kitna hai saal ka", "gst_payable")
        q("gst kitna hai", "gst_payable")
        q("tax kitna hai", "gst_payable")
        q("gst payable kitna hai", "gst_payable")
        q("how much tax do i owe", "gst_payable")

        // Balance
        q("cash balance kya hai")
        q("cash kitna hai")
        q("what is cash balance")
        q("Ramesh ka balance kya hai")
        q("Ramesh balance")

        // Sales / purchase / profit
        q("sale kitna hua")
        q("show sales today")
        q("sales this month")
        q("purchase kitna hua")
        q("show purchase")
        q("profit kya hai")
        q("profit yesterday")

        // Stock
        q("kitna stock hai")
        q("what is stock of rice")

        // Outstanding / receivables / payables
        q("who owes me")
        q("kiska outstanding hai")
        q("receivables list")
        q("dena hai kisko")
        q("payables")

        // Top / recent / summary
        q("top customers")
        q("top suppliers")
        q("top items")
        q("recent vouchers")
        q("summary")
        q("show report")

        val tfails = runTxs()
        val qfails = runQueries()
        println("FULL CORPUS: ${tx.size} txn + ${qs.size} query = ${tx.size + qs.size} total")
        println("TX failures (${tfails.size}):"); tfails.forEach { println("  $it") }
        println("QUERY failures (${qfails.size}):"); qfails.forEach { println("  $it") }
        val all = tfails + qfails
        assertTrue("${all.size} failures:\n" + all.joinToString("\n"), all.isEmpty())
    }
}
