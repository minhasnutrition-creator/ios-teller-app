package com.minhas.teller.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two recogniser failures reported from live use, plus the false positives the repairs must
 * not cause. Plain JUnit — runs with no device (the on-device TSV dictionary stays unloaded, which
 * is the harder case for [SpeechRepair] since it has fewer known words to lean on).
 */
class SpeechRepairTest {

    private fun parse(phrase: String): AiResponse.DraftVoucher? = runBlocking {
        FinanceHelper.tryParseHinglish(null, phrase) as? AiResponse.DraftVoucher
    }

    // ── 1. Glued number words ────────────────────────────────────────────────

    @Test
    fun splits_number_glued_onto_the_next_word() {
        // "paanch rum bechi 4000 ki ankit ko" as the recogniser actually returned it.
        assertEquals("panch ram bechi 4000 ki ankit ko", SpeechRepair.repair("panchram bechi 4000 ki ankit ko"))
        assertEquals("panch kele beche amrit ko", SpeechRepair.repair("panchkele beche amrit ko"))
        assertEquals("paanch seb", SpeechRepair.repair("paanchseb"))
        assertEquals("teen kilo cheeni", SpeechRepair.repair("teenkilo cheeni"))
        assertEquals("do kilo", SpeechRepair.repair("dokilo"))
        assertEquals("do sau", SpeechRepair.repair("dosau"))
        assertEquals("bees kitab", SpeechRepair.repair("beeskitab"))
    }

    @Test
    fun glued_number_becomes_quantity_in_the_voucher() {
        val draft = parse("panchram bechi 4000 ki ankit ko")
        assertNotNull("panchram sale -> null", draft)
        draft!!
        assertEquals("Sales", draft.type)
        assertEquals(4000.0, draft.amount ?: 0.0, 0.001)
        assertEquals("Ankit", draft.partyName)
        // The quantity is the point: 5 bottles, not one item called "Panchram".
        assertEquals(5.0, draft.inventoryItems.firstOrNull()?.quantity ?: 0.0, 0.001)
        assertTrue(
            "item should not be the glued token, was ${draft.inventoryItems.firstOrNull()?.name}",
            draft.inventoryItems.firstOrNull()?.name?.contains("panch", ignoreCase = true) != true
        )
    }

    @Test
    fun real_words_that_start_with_a_number_word_survive() {
        val untouched = listOf(
            "doodh", "dozen", "charger", "sona", "soap", "noodles", "dost", "panchayat",
            "panchkula", "teenager", "charminar", "charpai", "chawal", "lakhpati", "crorepati",
            "hazari", "saathi", "dasta", "fourteen", "seventy", "thousand", "hundred",
            "dollar", "document", "notebook", "nokia", "sofa", "socks", "dosa"
        )
        for (word in untouched) {
            assertEquals("$word must not be split", word, SpeechRepair.repair(word))
        }
        // And in a sentence the parser will see.
        assertEquals(
            "20 litre doodh beche sharma ko",
            SpeechRepair.repair("20 litre doodh beche sharma ko")
        )
    }

    // ── 2. "for" written as the digit 4 ──────────────────────────────────────

    @Test
    fun recovers_for_misheard_as_four() {
        assertEquals("sold 4 candies for 300", SpeechRepair.repair("sold 4 candies 4 300"))
        assertEquals("5 kela beche for 900000 ram ko", SpeechRepair.repair("5 kela beche 4 900000 ram ko"))
    }

    @Test
    fun four_is_left_alone_when_it_is_really_a_number() {
        // Space as a thousands separator with an amount marker: ₹4,500, not "for 500".
        assertEquals("50 seb kharide 4 500 ki", SpeechRepair.repair("50 seb kharide 4 500 ki"))
        // No earlier number, so this 4 is the quantity.
        assertEquals("beche 4 300", SpeechRepair.repair("beche 4 300"))
        assertEquals("4 kilo aloo beche 300 ki", SpeechRepair.repair("4 kilo aloo beche 300 ki"))
    }

    @Test
    fun recovered_for_gives_the_spoken_amount() {
        val draft = parse("sold 4 candies 4 300")
        assertNotNull("candies sale -> null", draft)
        draft!!
        assertEquals("Sales", draft.type)
        assertEquals(300.0, draft.amount ?: 0.0, 0.001)
        assertEquals(4.0, draft.inventoryItems.firstOrNull()?.quantity ?: 0.0, 0.001)
    }

    // ── 3. Ranking the recogniser's alternatives ─────────────────────────────

    @Test
    fun prefers_the_alternative_that_kept_for() {
        val best = SpeechRepair.pickAndRepair(
            listOf("sold 4 candies 4300", "sold 4 candies for 300", "sold for candies 4300")
        )
        assertEquals("sold 4 candies for 300", best)
    }

    @Test
    fun prefers_the_alternative_that_did_not_glue_words() {
        val best = SpeechRepair.pickAndRepair(
            listOf("panchram bechi 4000 ki ankit ko", "paanch rum bechi 4000 ki ankit ko")
        )
        assertEquals("paanch rum bechi 4000 ki ankit ko", best)
    }

    @Test
    fun keeps_the_recognisers_own_order_when_nothing_separates_them() {
        val best = SpeechRepair.pickBest(listOf("20 kilo cheeni beche ram ko 1000 ki", "20 kilo chini beche ram ko 1000 ki"))
        assertEquals("20 kilo cheeni beche ram ko 1000 ki", best)
        assertEquals("", SpeechRepair.pickBest(null))
        assertEquals("", SpeechRepair.pickBest(emptyList()))
    }

    // ── 4. Reusing a misheard item instead of creating a duplicate ───────────

    @Test
    fun misheard_item_resolves_to_the_stock_item_it_sounds_like() {
        val stock = listOf("Rum", "Whisky", "Beer", "Soda")
        assertEquals("Rum", LanguageTranslationUtils.closestKnownItem("Ram", stock))
        assertEquals("Whisky", LanguageTranslationUtils.closestKnownItem("whiskey", stock))
        // Nothing close enough → caller creates the item as spoken.
        assertEquals(null, LanguageTranslationUtils.closestKnownItem("cement", stock))
    }

    @Test
    fun known_dictionary_words_are_never_treated_as_misheard() {
        assertTrue(LanguageTranslationUtils.isKnownWord("doodh"))
        assertTrue(LanguageTranslationUtils.isKnownWord("chawal"))
        assertTrue(LanguageTranslationUtils.isKnownWord("candy"))
        assertTrue(!LanguageTranslationUtils.isKnownWord("panchram"))
    }
}
