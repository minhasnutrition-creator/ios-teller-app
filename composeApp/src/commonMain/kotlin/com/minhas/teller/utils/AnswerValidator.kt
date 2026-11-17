package com.minhas.teller.utils

import java.util.Locale

/**
 * Deterministic confidence checks for answers produced by the on-device model.
 *
 * These are the "escalation ladder" signals: when they fire, the caller escalates to the cloud
 * (DeepSeek) instead of showing an answer the small local model may have made up. Pure JVM logic,
 * unit-testable.
 */
object AnswerValidator {

    // Phrases the on-device model emits when it can't ground an answer or doesn't understand.
    private val CONFUSION_MARKERS = listOf(
        "not in the provided law", "couldn't find", "could not find", "not covered",
        "not mention", "no information", "not sure", "i don't know", "i do not know",
        "don't know", "do not know", "i can't", "i cannot", "sorry", "please clarify",
        "what do you mean", "didn't understand", "did not understand", "not able",
        "no such", "unable to"
    )

    /** True when an answer is empty, too short, or the model signalled it didn't understand. */
    fun isLowConfidence(answer: String?): Boolean {
        if (answer.isNullOrBlank()) return true
        val t = answer.trim()
        if (t.length < 20) return true
        val lower = t.lowercase(Locale.ROOT)
        return CONFUSION_MARKERS.any { lower.contains(it) }
    }

    /**
     * True when [answer] is non-trivial, not a confusion refusal, and — if it cites sections — every
     * cited section actually appears in the retrieved [chunkTexts]. Catches the model inventing a
     * section number that isn't in the provided law.
     */
    fun isGrounded(answer: String?, chunkTexts: List<String>): Boolean {
        if (isLowConfidence(answer)) return false
        val a = answer!!
        val citedSections = Regex("(?i)\\bsection\\s+([0-9]+[a-z]?)\\b")
            .findAll(a).map { it.groupValues[1] }.toList()
        if (citedSections.isEmpty()) return true
        val corpus = chunkTexts.joinToString(" ").lowercase(Locale.ROOT)
        return citedSections.all { section ->
            Regex("(?i)\\bsection\\s+${Regex.escape(section)}\\b").containsMatchIn(corpus)
        }
    }
}
