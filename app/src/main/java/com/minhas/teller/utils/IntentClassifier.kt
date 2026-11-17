package com.minhas.teller.utils

import java.util.Locale

/**
 * Deterministic, conservative intent classifier for the Helafin chat.
 *
 * The single most important guarantee it provides: a **question** is never misclassified as a
 * transaction, so a query like "is it against the GST laws to pay more than 10000 cash?" can
 * never be turned into a voucher by the regex parsers in [FinanceHelper].
 *
 * The classifier is deliberately conservative — when in doubt it returns QUESTION or AMBIGUOUS,
 * never TRANSACTION. Transaction drafting only happens for an explicit verb in command position.
 */
object IntentClassifier {

    enum class Intent { QUESTION, REPORT, TRANSACTION, CHART, AMBIGUOUS }

    // Knowledge/legal/help markers. Any of these make the input a question, outranking any
    // transaction verb that also appears in the sentence.
    private val KNOWLEDGE_MARKERS = listOf(
        "is it", "is this", "is there", "are there", "does it", "do i", "did i", "did you",
        "have i", "am i", "can i", "should i", "may i", "could i", "would i",
        "against", "legal", "illegal", "law", "laws", "allowed", "permitted", "not allowed",
        "limit", "limits", "rules", "rule for", "explain", "meaning", "mean by", "difference",
        "define", "what does", "what do you mean",
        "how to", "how do i", "how can i", "how do you",
        "kaise", "kya hai", "kya hota", "batao", "bataiye", "matlab", "section"
    )

    // Data/report keywords — "what is my cash balance", "show sales", "how much is my payable gst".
    // Checked before transaction verbs, and used to keep data queries OUT of the tax-question path.
    private val DATA_MARKERS = listOf(
        "balance", "sales", "purchase", "stock", "outstanding", "profit", "loss", "margin",
        "top", "recent", "summary", "receivable", "payable", "collect", "owe", "owes",
        "how much", "show", "count", "how many", "daybook", "hisab", "khata", "kitna", "kitni",
        "report", "list", "last"
    )

    // Transaction verbs in command position. Amount is intentionally NOT required here — a command
    // like "gave money to Amit" (no amount) still routes to the transaction/form flow, never to the
    // question path.
    private val TRANSACTION_VERBS = listOf(
        "sold", "sale", "bought", "buy", "purchased", "purchase",
        "paid", "pay", "payment", "received", "receive", "receipt",
        "gave", "give", "given", "diya", "diye", "liya", "liye",
        "becha", "beche", "bechi", "bikri", "kharida", "kharidi", "kharide", "khareed",
        "financed", "finance", "loan", "emi", "down payment", "downpayment",
        "transfer", "transferred", "sent", "bheja"
    )

    fun classify(rawQuery: String): Intent {
        val query = rawQuery.trim()
        if (query.isBlank()) return Intent.AMBIGUOUS
        val q = query.lowercase(Locale.ROOT)

        // Chart/graph requests always render a visual (matches the existing chat shortcut).
        if (q.contains("chart") || q.contains("graph")) return Intent.CHART

        val endsWithQuestion = query.trimEnd().endsWith("?")
        val hasKnowledgeMarker = KNOWLEDGE_MARKERS.any { q.contains(it) }
        val isTaxTerm = SmartFinancePrompts.isTaxLawQuery(query)
        val hasDataMarker = DATA_MARKERS.any { q.contains(it) }

        // A clear knowledge/legal/help question outranks everything. A bare tax term ("gst") only
        // counts as a question when it isn't part of a data query ("gst payable").
        val isQuestion = hasKnowledgeMarker || endsWithQuestion || (isTaxTerm && !hasDataMarker)
        if (isQuestion) return Intent.QUESTION

        if (hasDataMarker) return Intent.REPORT

        val hasTransactionVerb = TRANSACTION_VERBS.any { verb ->
            Regex("\\b${Regex.escape(verb)}\\b").containsMatchIn(q)
        }
        if (hasTransactionVerb) return Intent.TRANSACTION

        return Intent.AMBIGUOUS
    }
}
