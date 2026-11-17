package com.minhas.teller.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.runBlocking

/**
 * Unit tests for the finance logic that runs without a Context: GST/VAT rate
 * heuristics, tax config, date parsing, and the offline report-command router.
 * Pure logic — runs on JVM, no device required.
 */
class FinanceHelperTest {

    // ── Tax rate heuristics (India GST 2.0 slabs) ──────────────────────────

    @Test
    fun `essential staples are zero rated`() {
        assertEquals(0.0, FinanceHelper.guessTaxRate(null, "rice"), 0.001)
        assertEquals(0.0, FinanceHelper.guessTaxRate(null, "wheat flour"), 0.001)
        assertEquals(0.0, FinanceHelper.guessTaxRate(null, "milk"), 0.001)
        assertEquals(0.0, FinanceHelper.guessTaxRate(null, "medicine"), 0.001)
    }

    @Test
    fun `sin and luxury goods fall in 40 percent slab`() {
        assertEquals(40.0, FinanceHelper.guessTaxRate(null, "cigarette"), 0.001)
        assertEquals(40.0, FinanceHelper.guessTaxRate(null, "tobacco"), 0.001)
        assertEquals(40.0, FinanceHelper.guessTaxRate(null, "soft drink"), 0.001)
    }

    @Test
    fun `durables and electronics fall in 18 percent slab`() {
        assertEquals(18.0, FinanceHelper.guessTaxRate(null, "mobile phone"), 0.001)
        assertEquals(18.0, FinanceHelper.guessTaxRate(null, "laptop"), 0.001)
        assertEquals(18.0, FinanceHelper.guessTaxRate(null, "refrigerator"), 0.001)
    }

    @Test
    fun `mid tier essentials fall in 5 percent slab`() {
        assertEquals(5.0, FinanceHelper.guessTaxRate(null, "sugar"), 0.001)
        assertEquals(5.0, FinanceHelper.guessTaxRate(null, "tea"), 0.001)
        assertEquals(5.0, FinanceHelper.guessTaxRate(null, "edible oil"), 0.001)
    }

    @Test
    fun `unrecognised items default to the standard rate`() {
        assertEquals(18.0, FinanceHelper.guessTaxRate(null, "steel rod"), 0.001)
    }

    // ── Tax configuration ──────────────────────────────────────────────────

    @Test
    fun `India tax config is GST at 18 percent default`() {
        val config = TaxConfigManager.getConfigForCountry("India")
        assertEquals("GST", config.taxName)
        assertEquals(18.0, config.defaultRate, 0.001)
        assertTrue(config.standardRates.contains(40.0))
    }

    @Test
    fun `unknown country falls back to empty custom config`() {
        val config = TaxConfigManager.getConfigForCountry("Atlantis")
        assertEquals(0.0, config.defaultRate, 0.001)
        assertTrue(config.standardRates.isEmpty())
    }

    @Test
    fun `tax id label is country specific`() {
        assertEquals("GSTIN", TaxConfigManager.getConfigForCountry("India").taxIdName)
        assertEquals("Tax ID (EIN)", TaxConfigManager.getConfigForCountry("United States").taxIdName)
    }

    // ── Date parsing ───────────────────────────────────────────────────────

    @Test
    fun `date query keywords resolve`() {
        assertEquals("today", FinanceHelper.parseDateFromQuery("show sales today"))
        assertEquals("today", FinanceHelper.parseDateFromQuery("aaj ki entry"))
        assertEquals("yesterday", FinanceHelper.parseDateFromQuery("yesterday sales"))
        assertEquals("yesterday", FinanceHelper.parseDateFromQuery("kal ka hisab"))
    }

    @Test
    fun `dmy date is parsed`() {
        assertEquals("2024-06-05", FinanceHelper.parseDateFromQuery("05/06/2024"))
        assertEquals("2024-08-15", FinanceHelper.parseDateFromQuery("15-08-2024"))
    }

    @Test
    fun `no date returns null`() {
        assertNull(FinanceHelper.parseDateFromQuery("just some words"))
    }

    @Test
    fun `voucher date iso is parsed`() {
        val d = FinanceHelper.parseVoucherDate("2024-06-05")
        assertEquals("2024-06-05", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d))
    }

    @Test
    fun `voucher date dmy is parsed`() {
        val d = FinanceHelper.parseVoucherDate("13/11/2024")
        assertEquals("2024-11-13", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d))
    }

    @Test
    fun `blank voucher date falls back to today`() {
        assertNotNull(FinanceHelper.parseVoucherDate(null))
        assertNotNull(FinanceHelper.parseVoucherDate(""))
    }

    // ── Offline report-command routing ─────────────────────────────────────

    @Test
    fun `profit query routes to metric`() {
        assertEquals(
            AiResponse.QueryMetric("profit", "today"),
            FinanceHelper.parseReportCommandLocal("show profit today")
        )
    }

    @Test
    fun `cash balance query routes to metric`() {
        assertEquals(
            AiResponse.QueryMetric("cash", null),
            FinanceHelper.parseReportCommandLocal("what is the cash balance")
        )
    }

    @Test
    fun `who owes routes to collectables list`() {
        assertEquals(AiResponse.QueryCollectablesList, FinanceHelper.parseReportCommandLocal("who owes me"))
    }

    @Test
    fun `payable gst routes to the gst payable metric`() {
        assertEquals(
            AiResponse.QueryMetric("gst_payable", null),
            FinanceHelper.parseReportCommandLocal("how much is my payable gst")
        )
        assertEquals(
            AiResponse.QueryMetric("gst_payable", "month"),
            FinanceHelper.parseReportCommandLocal("gst liability this month")
        )
    }

    @Test
    fun `vouchers count query is not broken by rs removal`() {
        assertEquals(
            AiResponse.QueryMetric("vouchers_count", null),
            FinanceHelper.parseReportCommandLocal("how many vouchers are there")
        )
        assertEquals(
            AiResponse.QueryMetric("vouchers_without_tax", null),
            FinanceHelper.parseReportCommandLocal("how many vouchers are created without tax")
        )
    }

    // ── Offline multi-party ledger creation ────────────────────────────────

    @Test
    fun `create multiple parties produces a multi action with each name`() {
        val result = FinanceHelper.parseVoiceCommandLocal("create 5 parties named Amit, Ramesh, Suresh, Priya, Anil")
        assertTrue("expected MultiAction, got $result", result is AiResponse.MultiAction)
        val actions = (result as AiResponse.MultiAction).actions
        assertEquals(5, actions.size)
        assertTrue(actions.all { it is AiResponse.DraftLedger })
        assertEquals(
            listOf("Amit", "Ramesh", "Suresh", "Priya", "Anil"),
            actions.map { (it as AiResponse.DraftLedger).name }
        )
    }

    @Test
    fun `create suppliers as sundry creditors uses creditor group`() {
        val result = FinanceHelper.parseVoiceCommandLocal("add 3 suppliers Ravi, Sita and Gita")
        assertTrue("expected MultiAction, got $result", result is AiResponse.MultiAction)
        val actions = (result as AiResponse.MultiAction).actions
        assertEquals(3, actions.size)
        assertTrue(actions.all { (it as AiResponse.DraftLedger).group == "Sundry Creditors" })
    }

    @Test
    fun `single ledger creation still works`() {
        val result = FinanceHelper.parseVoiceCommandLocal("create ledger for Rahul")
        assertTrue("expected DraftLedger, got $result", result is AiResponse.DraftLedger)
        assertEquals("Rahul", (result as AiResponse.DraftLedger).name)
    }

    // ── Regression: a tax/law question must never be parsed into a voucher ──

    @Test
    fun `tax question is not parsed as a voucher draft`() = runBlocking {
        assertNull(
            FinanceHelper.parseSpeechToDraft(
                null,
                "I can't even pay anyone more than 10000 cash, is it against the GST laws?"
            )
        )
    }
}
