package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Diagnostic flood test (not a strict regression gate): throws a large, varied batch of realistic
 * Hinglish / English / Hindi (Devanagari) shopkeeper phrases — complete, deliberately incomplete,
 * and plain chit-chat — at the full offline parsing pipeline and reports, per phrase, which of
 * three outcomes it landed on:
 *   COMPLETE   — a full DraftVoucher was built (tryParseHinglish / smartRegexParser / parseVoiceCommandLocal)
 *   NEEDS_INFO — recognized as a sale/purchase but something is missing, so Helafin would now ask
 *                a specific follow-up (tryExtractIncompleteTransaction)
 *   UNHANDLED  — nothing matched at all; would escalate to cloud AI (or just fail offline)
 *
 * No hard pass/fail assertions on content — this is a coverage snapshot, not a ground-truth test
 * (real organic phrasing legitimately misses sometimes). It only fails if something throws.
 */
class FloodPhraseStressTest {

    private data class Case(val phrase: String, val tag: String)

    private fun cases(): List<Case> {
        val c = mutableListOf<Case>()
        fun add(tag: String, vararg phrases: String) { phrases.forEach { c.add(Case(it, tag)) } }

        add(
            "hinglish_sale_complete",
            "50 seb becha Ram ko 5000 ki",
            "Ram ko 50 seb beche 5000 me",
            "5000 ki 50 seb beche Ram ko",
            "Amit ko Splendor becha 55000 ki",
            "2 kg cheeni becha 120 rupaye",
            "5 kela beche 500 ki",
            "mal becha 2000",
            "saman becha 1500 cash me",
            "Suresh ko 10 bori cement becha 5000 ki",
            "aaj 3000 ka maal becha",
            "splendor 50000 ki becha Shubham ko",
            "do lakh ka maal becha Suresh ko",
            "2 kroad ka maal becha cash mein"
        )
        add(
            "hinglish_purchase_complete",
            "Amit se 50 seb kharide 10000 ke",
            "amit se 5000 aloo kharida",
            "mal kharida 10000",
            "50000 ki 10 Splendor kharide",
            "5 bori cement Suresh se 3000 me kharida",
            "10000 purchase from Gupta Traders",
            "Ramesh se 2 quintal gehu kharida 4000 ka",
            "paanch hazar ka seb kharida Amit se",
            "amit se 5 kilo aloo kharida"
        )
        add(
            "hinglish_payment_complete",
            "Ramesh ko 5000 diye",
            "kiraya diya 800",
            "salary diya 12000",
            "2000 Ramesh ko diye cash me",
            "Ravi ko udhar diya 1000",
            "bijli bill 2500 diya",
            "paid Ramesh 500",
            "ravi ko tin hazar diye",
            "bees hazar panch sau pachas Ramesh ko diye"
        )
        add(
            "hinglish_receipt_complete",
            "Gagan se 2000 liya",
            "Ramesh se 500 mila",
            "ravi se udhar liya 1000",
            "Anita ne 500 diye",
            "2000 Sharma se mila"
        )
        add(
            "hinglish_incomplete_amount",
            "Ram ko seb becha",
            "Amit se aloo kharida",
            "Suresh ko cement becha",
            "Ravi se gehu kharida",
            "Splendor becha Shubham ko",
            "Amit se Splendor kharida"
        )
        add(
            "hinglish_incomplete_other",
            "Ram ko becha 5000 ki",
            "5000 ka maal kharida Amit se",
            "becha 5000"
        )
        add(
            "hinglish_payment_receipt_incomplete",
            "Ramesh ko diye",
            "paid Ramesh",
            "received from Sharma",
            "Gagan se liya"
        )
        add(
            "regression_becha_past_tense",
            "Ram ko seb becha",
            "Suresh ko cement becha",
            "mobile becha Rahul ko",
            "whey protein becha",
            "jeans becha Amit ko",
            "Splendor becha Shubham ko"
        )
        add(
            "english_complete",
            "Sold 5 bananas to Amit for 200",
            "sold apples to Ram for 500",
            "bought 50 apples from Ram for 500",
            "purchased 10 kg rice for 5000 from Ramesh",
            "paid Ramesh 500",
            "received 500 from Ramesh",
            "gave Ravi 3000",
            "took 2000 from Gagan",
            "Sold item to John for 500",
            "Ramesh ko 1000 rupaye diye"
        )
        add(
            "english_incomplete",
            "sold apples to Ram",
            "bought rice from Ramesh",
            "paid Ramesh",
            "received from Sharma",
            "sold Splendor to Shubham"
        )
        // ── Beyond kirana: every other common Indian retail vertical ──
        add(
            "electrical_store",
            "Ramesh ko 10 copper wire becha 1200 ki",
            "Suresh se 50 MCB kharide 5000 ke",
            "2 ceiling fan becha Amit ko 2800 ki",
            "inverter battery becha Vikram ko 8500 ki",
            "LED bulb 20 piece kharida 1600 ka",
            "Ramesh se switchboard kharida 450 ka"
        )
        add(
            "electronics_mobile_store",
            "Redmi mobile becha Rahul ko 12000 ki",
            "mobile charger becha 500 ki",
            "earphone kharida 20 piece 2000 ka",
            "CCTV camera becha Suresh ko 4500 ki",
            "laptop kharida 45000 ka Vikram se",
            "mobile screen guard becha 150 ki"
        )
        add(
            "automobile_spares_store",
            "brake pad becha Amit ko 900 ki",
            "clutch plate kharida 1500 ka",
            "engine oil 5 litre becha 1800 ki",
            "bike tyre becha Ravi ko 2200 ki",
            "battery kharida Suresh se 3500 ka",
            "spark plug becha 150 ki"
        )
        add(
            "pharmacy_medical_store",
            "paracetamol tablet becha 40 ki",
            "cough syrup becha Anita ko 90 ki",
            "bandage kharida 200 ka",
            "thermometer becha 300 ki",
            "BP machine becha Ramesh ko 1200 ki",
            "antibiotic capsule becha 120 ki",
            "insulin injection becha Vikram ko 450 ki",
            "painkiller tablet kharida 60 ka",
            "eye drops becha 85 ki",
            "cough syrup kharida Suresh se 150 ka",
            "ointment becha Anita ko 70 ki",
            "vitamin syrup becha 200 ki",
            "antiseptic liquid kharida 180 ka",
            "ORS packet becha 25 ki",
            "glucose powder becha Rahul ko 90 ki",
            "sanitizer bottle kharida 120 ka",
            "surgical mask becha 10 piece 200 ki",
            "cold medicine becha 110 ki"
        )
        add(
            "supplements_store",
            "whey protein becha Rahul ko 3500 ki",
            "multivitamin kharida 1200 ka",
            "creatine becha 1800 ki",
            "BCAA supplement becha Vikram ko 2200 ki",
            "mass gainer kharida Suresh se 2800 ka",
            "fish oil capsule becha 600 ki"
        )
        add(
            "hardware_plumbing_store",
            "PVC pipe becha Ramesh ko 800 ki",
            "tap kharida 350 ka",
            "cement bag becha 5 piece 1500 ki",
            "door hinge kharida 120 ka",
            "nail packet becha 50 ki"
        )
        add(
            "garments_clothing_store",
            "kurta becha Sunita ko 1200 ki",
            "jeans kharida 1500 ka",
            "saree becha Anita ko 3500 ki",
            "shirt becha 800 ki",
            "shoes kharida Rahul se 2000 ka"
        )
        add(
            "stationery_store",
            "notebook becha 50 piece 1000 ki",
            "pen kharida 500 ka",
            "register becha Suresh ko 300 ki",
            "geometry box becha 150 ki"
        )
        add(
            "general_retail_store",
            "soap becha 40 ki",
            "shampoo kharida 250 ka",
            "toothpaste becha 90 ki",
            "detergent powder kharida Suresh se 400 ka"
        )
        add(
            "store_verticals_incomplete",
            "MCB kharida Suresh se",
            "mobile becha Rahul ko",
            "brake pad kharida",
            "whey protein becha",
            "cement bag kharida Ramesh se",
            "jeans becha Amit ko"
        )
        add(
            "hindi_devanagari_complete",
            "राम को ५० सेब बेचा ५००० की",
            "अमित से ५० सेब खरीदा १०००० के",
            "रमेश को ५००० दिए",
            "गगन से २००० लिए",
            "१० लीटर दूध ५०० में खरीदा",
            "किराया दिया ८००"
        )
        add(
            "hindi_devanagari_incomplete",
            "राम को सेब बेचा",
            "अमित से आलू खरीदा"
        )
        add(
            "non_transaction_chat",
            "hi",
            "hello",
            "what can you do",
            "show sales today",
            "what is GST rate for mobile",
            "Ramesh ka balance kya hai",
            "show purchase this month",
            "help"
        )
        // ── Round 2: web-researched real-world voice-khata phrasing (Khata-Vani, Vyapar Voice,
        // Dukaan Saathi style dictation) — udhar/credit, large spoken numbers, mixed script,
        // ASR-mangled numbers, and deliberately ambiguous give/take phrasing. ──
        add(
            "hinglish_sale_v2",
            "Ram ko 50 seb becha 5000 ki",
            "Sharma ji ko 2 kilo cheeni becha, 120 rupaye",
            "aaj Ramesh ko 10 packet biscuit bech diya",
            "maal becha Suresh ko 3200 ka",
            "Verma ko 2 bottle oil baicha 300 mein",
            "customer ko cash mein 800 ka saaman diya",
            "aaj 15 kilo aata becha, 600 rupaye mile"
        )
        add(
            "hinglish_purchase_v2",
            "supplier se 5000 ka maal liya",
            "Ramesh se 20 kilo chawal khareeda 1000 ka",
            "aaj wholesaler se stock liya, 15000 ka",
            "50 packet namkeen le liya dukaan ke liye",
            "Gupta se maal khareeda, abhi payment nahi hua",
            "dudhwale se 10 litre doodh liya 500 ka"
        )
        add(
            "hinglish_payment_given_v2",
            "Rajesh ko 2000 rupay diya",
            "dudhwale ko 3000 diye",
            "Sandipan ko udhaar wapas kiya 1500",
            "mazdoor ko tankha de diya, 8000",
            "landlord ko kiraya diya 12000",
            "bijli ka bill 3450 bhara",
            "transport wale ko bhada diya 600"
        )
        add(
            "hinglish_payment_received_v2",
            "Sandipan se 500 rupay mile",
            "Ramesh ne udhaar wapas kiya 2000",
            "Sharma ji se purana udhaar mila 1000 ka",
            "customer se cash mila 750",
            "aaj Anita se 3000 collection hua"
        )
        add(
            "hindi_devanagari_v2",
            "राम को ५० सेब बेचा, ५००० का",
            "शर्मा जी ने २ किलो चीनी ली, १२० रुपये में",
            "सुरेश से माल खरीदा, ३२०० का",
            "राजेश को २००० रुपये उधार दिया",
            "दुकान का किराया १२००० रुपये दिया",
            "बिजली का बिल ३४५० भरा",
            "मज़दूर को तनख्वाह दी, ८०००",
            "रमेश ने उधार वापस किया, १५०० रुपये"
        )
        add(
            "english_colloquial_v2",
            "sold five kg sugar to Ramesh for five hundred rupees",
            "bought twenty kilos of rice from the wholesaler for one thousand",
            "gave the electrician eight hundred rupees for repair",
            "received two thousand rupees from Mr. Verma",
            "paid the shop rent twelve thousand this month",
            "sold three sarees to Anita worth four thousand five hundred",
            "purchased stock worth fifteen thousand on credit"
        )
        add(
            "udhar_credit",
            "Ramesh ko 2 kilo chawal udhaar diya",
            "Sunita ko paanch sau diya, udhaar likh lo",
            "khata me likh do, baad me dega",
            "credit pe becha, abhi cash nahi aaya",
            "Sharma ji ne do kilo cheeni li, ek sau bees udhaar",
            "udhaar ka paisa aaj wapas mila",
            "jama kar do, udhaar chukta ho gaya",
            "naya udhaar khata khol do Ramesh ke naam"
        )
        add(
            "large_numbers_v2",
            "ek lakh pachaas hazar ka order aaya",
            "do lakh ka maal becha is mahine",
            "paanch hazar teen sau rupaye udhaar diya",
            "ek crore ka turnover ho gaya is saal",
            "sattar hazar ka stock liya supplier se",
            "nau lakh pachattar hazar ka hisaab baaki hai",
            "ek lakh ek hazar paanch sau rupaye mile"
        )
        add(
            "ambiguous_edge_cases",
            "Ram ko diya 500",
            "maal liya 2000 ka",
            "becha ya kharida pata nahi, 3000 ka hua",
            "Suresh se 1000 liya ya diya, confirm karo",
            "bech diya sab, paisa abhi nahi aaya",
            "khareeda tha ya udhaar liya, likh do bas"
        )
        add(
            "mixed_script",
            "Ramesh ko 500 rupaye diya, payment pending रहेगा",
            "आज stock लिया wholesaler से, 15000 का",
            "customer ne 2000 paid किया, बाकी उधार है",
            "bijli ka bill bhara, receipt attach कर दो",
            "sold 5kg चीनी को Ramesh for 500 रुपये"
        )
        return c
    }

    @Test
    fun flood() {
        data class Result(val case: Case, val outcome: String, val detail: String)
        val results = mutableListOf<Result>()

        for (case in cases()) {
            val p = case.phrase
            val tier1 = runBlocking { FinanceHelper.tryParseHinglish(null, p) } as? AiResponse.DraftVoucher
            val tier2 = if (tier1 == null) runBlocking { FinanceHelper.smartRegexParser(null, p) } as? AiResponse.DraftVoucher else null
            val tier3 = if (tier1 == null && tier2 == null) FinanceHelper.parseVoiceCommandLocal(p) as? AiResponse.DraftVoucher else null
            val complete = tier1 ?: tier2 ?: tier3

            if (complete != null) {
                results.add(Result(case, "COMPLETE", "${complete.type} amt=${complete.amount} party=${complete.partyName} item=${complete.inventoryItems.firstOrNull()?.name}"))
                continue
            }
            val needsInfo = FinanceHelper.tryExtractIncompleteTransaction(p)
            if (needsInfo != null) {
                results.add(Result(case, "NEEDS_INFO", "${needsInfo.txnType} missing=${needsInfo.missingField} q=\"${needsInfo.question}\""))
                continue
            }
            results.add(Result(case, "UNHANDLED", ""))
        }

        val byOutcome = results.groupBy { it.outcome }
        println("==================== FLOOD TEST REPORT ====================")
        println("Total phrases: ${results.size}")
        byOutcome.forEach { (outcome, list) -> println("  $outcome: ${list.size}") }
        println()
        val byTagAndOutcome = results.groupBy { it.case.tag }
        for ((tag, list) in byTagAndOutcome) {
            val counts = list.groupBy { it.outcome }.mapValues { it.value.size }
            println("--- $tag (${list.size}) --- $counts")
            for (r in list) {
                println("  [${r.outcome}] \"${r.case.phrase}\" ${r.detail}")
            }
        }
        println("=============================================================")
        // Informational only — never fail the build on parse coverage; this is a snapshot, not a
        // ground-truth assertion (see class doc). We only care that nothing throws.
    }
}
