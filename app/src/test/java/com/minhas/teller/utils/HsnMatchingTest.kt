package com.minhas.teller.utils

import com.minhas.teller.data.entities.HsnEntry
import com.minhas.teller.data.repositories.HSNSyncRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for HSN identification — the scoring that decides which HSN code/category an item
 * name maps to, plus the 5%-oil heuristic. A regression here means items auto-classify under the
 * wrong HSN (e.g. "engine oil" landing in cooking oil), which drives wrong GST rates & categories.
 */
class HsnMatchingTest {

    private val engineOil = HsnEntry(
        hsnCode = "27101972",
        description = "ENGINE OIL (INTERNAL COMBUSTION ENGINE CRANKCASE OILS)",
        keywords = "engine, oil, internal, combustion, crankcase",
        gstRate = 5.0,
        category = "Mineral Fuels"
    )
    private val cookingOil = HsnEntry(
        hsnCode = "1512",
        description = "COOKING OIL (SUNFLOWER, MUSTARD, GROUNDNUT, SOYABEAN)",
        keywords = "oil, cooking, sunflower, mustard, groundnut, soyabean",
        gstRate = 5.0,
        category = "Cooking Oils"
    )

    @Test
    fun `engine oil scores higher than cooking oil`() {
        val engine = HSNSyncRepository.scoreHsnMatch("engine oil", engineOil)
        val cooking = HSNSyncRepository.scoreHsnMatch("engine oil", cookingOil)
        assertTrue("engine oil ($engine) should beat cooking oil ($cooking)", engine > cooking)
    }

    @Test
    fun `mustard oil matches the mustard oil entry best`() {
        val mustard = HsnEntry(hsnCode = "15149120", description = "MUSTARD OIL", keywords = "mustard oil", gstRate = 5.0, category = "Cooking Oils")
        // exact description (+100_000) + exact keyword (+25_000) + code length (+8)
        assertEquals(100_000 + 25_000 + 8, HSNSyncRepository.scoreHsnMatch("mustard oil", mustard))
    }

    @Test
    fun `whole word matching does not match inside another word`() {
        assertTrue(HSNSyncRepository.wholeWordContains("mustard oil", "oil"))
        assertTrue(HSNSyncRepository.wholeWordContains("engine oil (internal)", "engine oil"))
        assertFalse(HSNSyncRepository.wholeWordContains("boiled", "oil"))
        assertFalse(HSNSyncRepository.wholeWordContains("soil", "oil"))
    }

    @Test
    fun `edible and engine oils are five percent but personal care oils are not`() {
        assertTrue(LanguageTranslationUtils.isFivePercentOil("engine oil"))
        assertTrue(LanguageTranslationUtils.isFivePercentOil("mustard oil"))
        assertTrue(LanguageTranslationUtils.isFivePercentOil("cooking oil"))
        assertFalse(LanguageTranslationUtils.isFivePercentOil("hair oil"))
        assertFalse(LanguageTranslationUtils.isFivePercentOil("essential oil"))
    }

    @Test
    fun `tax heuristic keeps engine oil at five and hair oil at eighteen`() {
        assertEquals(5.0, FinanceHelper.guessTaxRate(null, "engine oil"), 0.001)
        assertEquals(5.0, FinanceHelper.guessTaxRate(null, "mustard oil"), 0.001)
        assertEquals(18.0, FinanceHelper.guessTaxRate(null, "hair oil"), 0.001)
    }
}
