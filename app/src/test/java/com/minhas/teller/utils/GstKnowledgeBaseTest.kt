package com.minhas.teller.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the offline GST-law retrieval (chunking + keyword search + citation). Uses a
 * small sample corpus — pure logic, runs on JVM, no device.
 */
class GstKnowledgeBaseTest {

    private val sample = """
        <<<SOURCE: GST Act - Bare Law.pdf | PAGE: 10>>>
        Section 22. Persons liable for registration. Every supplier shall be liable to be registered
        under this Act in the State or Union territory from where he makes a taxable supply of goods
        or services or both, if his aggregate turnover in a financial year exceeds forty lakh rupees.

        <<<SOURCE: GST Act - Bare Law.pdf | PAGE: 12>>>
        Section 16. Eligibility and conditions for taking input tax credit. Every registered person
        shall be entitled to take credit of input tax charged on any supply of goods or services
        which are used or intended to be used in the course or furtherance of his business.

        <<<SOURCE: Technical Guide on GST Annual Return (Form GSTR 9).pdf | PAGE: 3>>>
        The annual return GSTR-9 must be filed by 31st December following the end of the financial
        year by every registered person except those exempted.
    """.trimIndent()

    @Test
    fun `chunk preserves source and page`() {
        val chunks = GstKnowledgeBase.chunk(sample)
        assertTrue(chunks.isNotEmpty())
        val pages = chunks.map { it.page }.distinct()
        assertTrue(pages.contains(10))
        assertTrue(pages.contains(12))
        assertTrue(pages.contains(3))
        assertTrue(chunks.all { it.source.isNotBlank() })
    }

    @Test
    fun `search finds the registration section for a registration query`() {
        val chunks = GstKnowledgeBase.chunk(sample)
        val matches = GstKnowledgeBase.search(chunks, "who needs GST registration and turnover limit", k = 1)
        assertEquals(1, matches.size)
        assertEquals(10, matches[0].chunk.page)
        assertTrue(matches[0].chunk.text.contains("Section 22", ignoreCase = true))
    }

    @Test
    fun `search finds input tax credit section`() {
        val chunks = GstKnowledgeBase.chunk(sample)
        val matches = GstKnowledgeBase.search(chunks, "input tax credit eligibility", k = 1)
        assertEquals(1, matches.size)
        assertEquals(12, matches[0].chunk.page)
        assertTrue(matches[0].chunk.text.contains("Section 16", ignoreCase = true))
    }

    @Test
    fun `search finds the annual return due date`() {
        val chunks = GstKnowledgeBase.chunk(sample)
        val matches = GstKnowledgeBase.search(chunks, "GSTR-9 filing due date", k = 1)
        assertEquals(1, matches.size)
        assertEquals(3, matches[0].chunk.page)
        assertTrue(matches[0].chunk.text.contains("31st December", ignoreCase = true))
    }

    @Test
    fun `answer returns cited text or null when nothing matches`() {
        val answer = GstKnowledgeBase.answer(sample, "input tax credit")
        assertNotNull(answer)
        assertTrue(answer!!.contains("GST Act - Bare Law.pdf", ignoreCase = true))

        assertNull(GstKnowledgeBase.answer(sample, "zzz nonsense query about nothing relevant"))
    }
}
