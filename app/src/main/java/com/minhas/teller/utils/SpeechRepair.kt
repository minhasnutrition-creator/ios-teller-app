package com.minhas.teller.utils

import java.util.Locale

/**
 * Repairs the *recogniser's* output before any parsing happens.
 *
 * Android's speech recogniser is a general-purpose English/Hindi model: it does not know it is
 * listening to a shopkeeper dictating a bill, so it makes the same two mistakes over and over.
 *
 *  1. **It glues a spoken Hindi number onto the next word.** "paanch rum bechi 4000 ki ankit ko"
 *     comes back as "panchram bechi 4000 ki ankit ko", so the parsers see an item called
 *     "Panchram" with quantity 1 instead of 5 bottles of rum.
 *  2. **It writes the English preposition "for" as the digit 4.** "sold 4 candies for 300" comes
 *     back as "sold 4 candies 4 300", and its own number formatter then joins the two digit
 *     groups into "4300" — the amount becomes ₹4,300 instead of ₹300.
 *
 * Neither is a parser bug: the parsers correctly read text the user never said. These repairs put
 * the words back before [FinanceHelper] looks at them, and [pickBest] chooses between the
 * recogniser's own alternative transcripts instead of blindly taking the first one.
 *
 * Every repair here is idempotent, so it is safe to run at the mic (to show the user a sensible
 * transcript) and again inside the parser's normaliser (so typed text benefits too).
 */
object SpeechRepair {

    /**
     * Number words distinctive enough that a *longer* unknown word starting with one is almost
     * always recogniser glue. Matched longest-first, so "fourteen" is never read as "four"+"teen".
     */
    private val STRONG_PREFIXES = listOf(
        // Hindi units & teens
        "paanch", "panch", "paanj", "panj", "teen", "chaar", "saat", "aath", "dus",
        "gyarah", "gyaarah", "barah", "baarah", "terah", "chaudah", "pandrah", "solah",
        "satrah", "atharah", "unnis",
        // Hindi twenties and up
        "ikkis", "bais", "baais", "teis", "chaubis", "pachis", "pachees", "chabbis",
        "sattais", "athais", "untis", "bees", "tees", "chalis", "chaalis", "pachas", "pachaas",
        "sattar", "nabbe",
        // multipliers
        "hazar", "hazaar", "lakh", "laakh", "crore", "karod",
        // English
        "three", "four", "five", "seven", "eight", "nine", "eleven", "twelve", "thirteen",
        "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
        "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety", "hundred", "thousand"
    ).sortedByDescending { it.length }

    /**
     * Number words that double as the beginning of ordinary words ("do"/"doodh", "char"/"charger",
     * "so"/"sona", "no"/"noodles"). These are split only when the tail is itself a word we know,
     * so "dokilo" → "do kilo" but "noodles" and "sona" are left alone.
     */
    private val WEAK_PREFIXES = listOf(
        "ek", "do", "char", "che", "chhe", "nau", "sau", "das", "saath", "so", "no",
        "one", "two", "six", "ten"
    ).sortedByDescending { it.length }

    /** Every form above, for rejecting a token that *is* a number word rather than glue. */
    private val NUMBER_WORDS: Set<String> = (STRONG_PREFIXES + WEAK_PREFIXES).toSet()

    /**
     * Real words that begin with a strong number word and must survive untouched. The dictionary
     * check in [splitToken] catches trade items; this covers the rest.
     */
    private val PROTECTED = setOf(
        "panchayat", "panchayati", "panchang", "panchami", "panchak", "panchakarma",
        "panchkula", "panchgani", "panchratna", "panchamrit", "panchsheel",
        "teenager", "teenagers", "charminar", "chaarminar", "charpai", "chaarpai",
        "saatvik", "saathi", "saathiya", "dussehra", "dusshera", "dasta", "dastana",
        "lakhpati", "crorepati", "karodpati", "hazari", "hazaari", "hazrat", "hazaare",
        "fourwheeler", "twowheeler", "threewheeler", "fivestar", "sevenstar",
        "thousands", "hundreds", "hundredth", "thousandth", "seventyfirst"
    )

    /** Grammar particles: never a usable item/word tail. */
    private val PARTICLES = setOf("ka", "ki", "ke", "ko", "se", "ne", "mein", "main", "mai", "me", "par", "pe", "aur")

    private val VOWELS = "aeiou"
    private val ALPHA_TOKEN = Regex("[a-zA-Z]{4,}")
    private val UNIT_TOKEN = Regex("^" + ALL_UNIT_PATTERN + "$", RegexOption.IGNORE_CASE)

    /** Transaction verbs, amount markers and party markers — the shape of a real billing sentence. */
    private val VERBS = Regex(
        "\\b(?:beche|becha|bechi|bech|becho|bika|biki|bikri|sold|sell|sale|" +
            "kharide|kharida|kharidi|kharid|khareed|khareeda|bought|buy|purchase|purchased|" +
            "diya|diye|di|dediya|chukaya|paid|liya|liye|li|mila|mile|received|got)\\b",
        RegexOption.IGNORE_CASE
    )
    private val AMOUNT_MARKER = Regex(
        "\\b(?:for|ke|ki|ka|rs|rupaye|rupees|rupya|mein|me|mai|par|pe|each|per|@)\\b",
        RegexOption.IGNORE_CASE
    )
    private val PARTY_MARKER = Regex("\\b(?:ko|se|to|from)\\b", RegexOption.IGNORE_CASE)
    private val DIGIT_GROUP = Regex("\\d+(?:\\.\\d+)?")

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Picks the most billing-shaped transcript out of the recogniser's alternatives and repairs it.
     * The alternatives for one utterance usually differ exactly where the recogniser was unsure —
     * which is where our mistakes live — so a structural score beats always taking the first.
     */
    fun pickAndRepair(alternatives: List<String>?, confidences: FloatArray? = null): String =
        repair(pickBest(alternatives, confidences))

    /** As [pickAndRepair] but keeps the transcript verbatim (used where the raw text is displayed). */
    fun pickBest(alternatives: List<String>?, confidences: FloatArray? = null): String {
        val list = alternatives?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        if (list.size <= 1) return list.firstOrNull() ?: ""
        var best = list[0]
        var bestScore = Int.MIN_VALUE
        list.forEachIndexed { index, candidate ->
            // The recogniser's own ranking is the tie-breaker, and its confidence (when it reports
            // any) nudges the score — but a transcript that actually looks like a transaction wins.
            val confidence = confidences?.getOrNull(index)?.takeIf { it > 0f } ?: 0f
            val score = score(candidate) + (confidence * 2f).toInt() + (list.size - index)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    /** Applies every transcript repair, in the order they depend on each other. */
    fun repair(text: String): String {
        if (text.isBlank()) return text
        var t = splitGluedNumberWords(text)
        t = recoverForHeardAsFour(t)
        return t
    }

    /**
     * How much the transcript looks like a dictated transaction. Used only to rank alternatives of
     * the *same* utterance against each other, never as an absolute "is this valid" test.
     */
    fun score(text: String): Int {
        var score = 0
        if (VERBS.containsMatchIn(text)) score += 4
        if (AMOUNT_MARKER.containsMatchIn(text)) score += 2
        if (PARTY_MARKER.containsMatchIn(text)) score += 1
        val numbers = DIGIT_GROUP.findAll(text).map { it.value }.toList()
        if (numbers.size >= 2) score += 2 else if (numbers.size == 1) score += 1
        // A transcript that still needs de-glueing is one the recogniser ran words together in.
        val glued = countGluedTokens(text)
        score -= glued * 3
        // "4300" where nothing marks it as an amount is the recogniser's "for" + amount join.
        if (!Regex("\\bfor\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            score -= Regex("\\b4\\d{2,}\\b").findAll(text).count()
        }
        // Runaway tokens ("panchkeledjamrit") are agglutination, not words.
        score -= Regex("[a-zA-Z]{14,}").findAll(text).count() * 2
        return score
    }

    /**
     * Splits a spoken number the recogniser glued onto the following word:
     * "panchram" → "panch ram", "paanchkele" → "paanch kele", "dokilo" → "do kilo".
     * Tokens the item dictionary recognises, number words themselves and the [PROTECTED] list are
     * left exactly as they are.
     */
    fun splitGluedNumberWords(text: String): String =
        ALPHA_TOKEN.replace(text) { m -> splitToken(m.value) }

    /**
     * Puts back a "for" the recogniser wrote as the digit 4 ("… 4 candies 4 300" → "… for 300").
     *
     * Deliberately narrow: it only fires when an earlier number in the sentence already supplies
     * the quantity and no amount marker follows, so "4 500 ki" (₹4,500 written with a space as a
     * thousands separator) is left for the compound-amount merge in [FinanceHelper] to handle.
     */
    fun recoverForHeardAsFour(text: String): String {
        val pattern = Regex(
            "\\b(?:4|four)\\s+(\\d{2,})\\b(?!\\s*(?:ki|ke|ka|rs|rupaye|rupay|rupees|rupya|mein|me|mai)\\b)",
            RegexOption.IGNORE_CASE
        )
        return pattern.replace(text) { m ->
            val before = text.substring(0, m.range.first)
            if (before.any { it.isDigit() }) "for ${m.groupValues[1]}" else m.value
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun countGluedTokens(text: String): Int =
        ALPHA_TOKEN.findAll(text).count { splitToken(it.value) != it.value }

    private fun splitToken(token: String): String {
        val lower = token.lowercase(Locale.ROOT)
        if (lower in NUMBER_WORDS || lower in PROTECTED) return token
        if (UNIT_TOKEN.matches(lower)) return token
        if (LanguageTranslationUtils.isKnownWord(lower)) return token

        for (prefix in STRONG_PREFIXES) {
            if (!lower.startsWith(prefix)) continue
            val tail = lower.substring(prefix.length)
            if (isUsableTail(tail)) return "$prefix $tail"
        }
        for (prefix in WEAK_PREFIXES) {
            if (!lower.startsWith(prefix)) continue
            val tail = lower.substring(prefix.length)
            if (!isUsableTail(tail)) continue
            // A short number word only counts as glue when what follows is a word we can vouch for.
            if (tail in NUMBER_WORDS || UNIT_TOKEN.matches(tail) || LanguageTranslationUtils.isKnownWord(tail)) {
                return "$prefix $tail"
            }
        }
        return token
    }

    private fun isUsableTail(tail: String): Boolean =
        tail.length >= 3 && tail.any { it in VOWELS } && tail !in PARTICLES

    /** Kept in sync with [FinanceHelper.ALL_UNIT_REGEX_PATTERN]'s Roman forms. */
    private const val ALL_UNIT_PATTERN =
        "(?:killo|kilo|kilos|kila|kile|kg|kgs|kilogram|kilograms|gms|gram|grams|gm|litres|liters|" +
            "litre|liter|ltr|ltrs|leeter|litar|lit|darjan|dozens|dozen|doz|dzn|pieces|piece|pcs|pc|" +
            "peice|peices|nag|nos|number|numbers|packets|packet|pkt|pkts|pack|packs|meters|meter|" +
            "metre|metres|mtr|mtrs|boriya|boriyan|bori|bags|bag|peti|box|boxes|bottles|bottle|btl|" +
            "btls|pair|pairs|jodi|foot|feet|ft)"
}
