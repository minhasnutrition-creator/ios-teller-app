package com.minhas.teller.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the deterministic confidence checks that decide whether to show an on-device answer or
 * escalate to the cloud.
 */
class AnswerValidatorTest {

    // ── isLowConfidence ───────────────────────────────────────────────────

    @Test
    fun `blank or null answer is low confidence`() {
        assertTrue(AnswerValidator.isLowConfidence(null))
        assertTrue(AnswerValidator.isLowConfidence(""))
        assertTrue(AnswerValidator.isLowConfidence("   "))
    }

    @Test
    fun `very short answer is low confidence`() {
        assertTrue(AnswerValidator.isLowConfidence("ok"))
    }

    @Test
    fun `confusion refusal is low confidence`() {
        assertTrue(AnswerValidator.isLowConfidence("I'm sorry, I don't know the answer to that question."))
        assertTrue(AnswerValidator.isLowConfidence("The answer is not in the provided law excerpts."))
    }

    @Test
    fun `substantive answer is not low confidence`() {
        assertFalse(AnswerValidator.isLowConfidence("GST registration is required when turnover exceeds the threshold."))
    }

    // ── isGrounded (phantom section detection) ────────────────────────────

    @Test
    fun `answer citing an absent section is not grounded`() {
        val chunks = listOf("Section 16. Eligibility and conditions for taking input tax credit.")
        val answer = "You can claim ITC under Section 99 of the GST Act."
        assertFalse(AnswerValidator.isGrounded(answer, chunks))
    }

    @Test
    fun `answer citing a present section is grounded`() {
        val chunks = listOf("Section 16. Eligibility and conditions for taking input tax credit.")
        val answer = "You can claim ITC under Section 16 of the GST Act."
        assertTrue(AnswerValidator.isGrounded(answer, chunks))
    }

    @Test
    fun `answer with no section citation is grounded if substantive`() {
        assertTrue(
            AnswerValidator.isGrounded(
                "You can claim input tax credit on goods used in the course of business.",
                listOf("Section 16. Eligibility and conditions for taking input tax credit.")
            )
        )
    }
}
