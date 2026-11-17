package com.minhas.teller.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the intent classifier. The critical invariant: a knowledge/legal question
 * must classify as QUESTION, never TRANSACTION, so it can never be turned into a voucher.
 */
class IntentClassifierTest {

    // ── The reported bug: a question must never become a voucher ──────────

    @Test
    fun `gst cash limit question is a QUESTION not a transaction`() {
        assertEquals(
            IntentClassifier.Intent.QUESTION,
            IntentClassifier.classify("I can't even pay anyone more than 10000 cash, is it against the GST laws?")
        )
    }

    @Test
    fun `legal cash limit question is a QUESTION`() {
        assertEquals(
            IntentClassifier.Intent.QUESTION,
            IntentClassifier.classify("is paying cash above 10000 illegal")
        )
    }

    @Test
    fun `bare gst question is a QUESTION`() {
        assertEquals(IntentClassifier.Intent.QUESTION, IntentClassifier.classify("what is gst"))
    }

    @Test
    fun `app help question is a QUESTION`() {
        assertEquals(IntentClassifier.Intent.QUESTION, IntentClassifier.classify("how do i add a ledger"))
    }

    // ── Explicit transactions still classify as TRANSACTION ───────────────

    @Test
    fun `explicit sale is a TRANSACTION`() {
        assertEquals(
            IntentClassifier.Intent.TRANSACTION,
            IntentClassifier.classify("sold 5 chairs to ramu for 5000")
        )
    }

    @Test
    fun `explicit payment is a TRANSACTION`() {
        assertEquals(IntentClassifier.Intent.TRANSACTION, IntentClassifier.classify("paid 500 to rahul"))
    }

    @Test
    fun `finance sale is a TRANSACTION`() {
        assertEquals(
            IntentClassifier.Intent.TRANSACTION,
            IntentClassifier.classify("idfc bank financed splendor with loan amount 60000")
        )
    }

    // ── Data queries still classify as REPORT ─────────────────────────────

    @Test
    fun `cash balance query is a REPORT`() {
        assertEquals(IntentClassifier.Intent.REPORT, IntentClassifier.classify("what is my cash balance"))
    }

    @Test
    fun `payable gst is a REPORT not a question`() {
        assertEquals(IntentClassifier.Intent.REPORT, IntentClassifier.classify("how much is my payable gst"))
    }

    @Test
    fun `sales query is a REPORT`() {
        assertEquals(IntentClassifier.Intent.REPORT, IntentClassifier.classify("show sales today"))
    }

    // ── Other ─────────────────────────────────────────────────────────────

    @Test
    fun `chart request is a CHART`() {
        assertEquals(IntentClassifier.Intent.CHART, IntentClassifier.classify("show a chart of my sales"))
    }

    @Test
    fun `greeting is AMBIGUOUS`() {
        assertEquals(IntentClassifier.Intent.AMBIGUOUS, IntentClassifier.classify("hello"))
    }
}
