package com.minhas.teller.utils

import com.minhas.teller.data.entities.HsnEntry
import com.minhas.teller.data.repositories.HSNSyncRepository
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Unified, pure-JVM fast fuzzy & combinatorial HSN/SAC matcher.
 *
 * Indexes ALL entries in the 22,306+ HSN & Chapter 99 SAC directory + curated brand/retail catalogs
 * in memory (inverted token index + O(1) phonetic consonant-skeleton index + phrase index).
 *
 * Handles:
 * - Arbitrarily complex real-world SKU names with unseen brands, vehicle models, pack weights,
 *   wattages, voltages, colors, flavours, and spec codes (by isolating core product head nouns).
 * - Misspellings via O(1) consonant-skeleton lookup + Damerau-Levenshtein + Indian orthographic aliases.
 * - Any word order ("gainer mass", "pad brake", "bulb led", "wire copper finolex").
 * - High-confidence automatic resolution for FinanceHelper / Excel import while surfacing
 *   "Did you mean this?" (1 match) or "Is it either one of these?" (2+ matches) in Item Creation.
 */
object HsnFuzzyMatcher {

    enum class MatchConfidence {
        EXACT_CONFIDENT,
        DID_YOU_MEAN_ONE,
        AMBIGUOUS_MULTIPLE,
        NONE
    }

    data class HsnSuggestion(
        val entry: HsnEntry,
        val matchedLabel: String,
        val score: Int,
        val isExactMatch: Boolean,
        val usedFuzzyRepair: Boolean,
        val wasReordered: Boolean
    )

    data class LookupResult(
        val confidence: MatchConfidence,
        val suggestions: List<HsnSuggestion>
    )

    private val NON_ALNUM = Regex("[^a-z0-9\\u0900-\\u097f]+")
    private val MULTI_SPACE = Regex("\\s+")
    private val SPEC_MEASUREMENT_REGEX = Regex(
        "\\b\\d+(?:\\.\\d+)?\\s*(?:kg|kgs|gm|gms|g|mg|mcg|iu|ml|l|ltr|ltrs|litre|litres|liter|liters|" +
            "lb|lbs|oz|w|watt|watts|kw|kva|va|v|volt|volts|a|amp|amps|ah|mah|wh|mm|cm|m|mtr|ft|" +
            "inch|inches|sqmm|mfd|uf|gb|tb|mb|hz|khz|mhz|ghz|rpm|pcs|pc|pk|pkt|pack|tabs|tab|" +
            "caps|cap|way|pin|pole|ch|yrs|yr|months)\\b"
    )

    private val STOP_WORDS = setOf(
        "and", "or", "the", "of", "for", "in", "on", "at", "to", "by", "from", "with", "without",
        "other", "others", "than", "not", "whether", "its", "their", "them", "this", "that",
        "elsewhere", "specified", "included", "including", "excluding", "thereof", "therefor",
        "heading", "chapter", "nes", "pcs", "pack", "pkt", "box", "new", "item", "size", "type"
    )

    /** Modifiers/colors/pack descriptors stripped when other core product nouns are present */
    private val VARIANT_NOISE_WORDS = setOf(
        "black", "white", "red", "blue", "green", "yellow", "grey", "gray", "pink", "brown",
        "purple", "matte", "glossy", "transparent", "clear", "cool", "warm", "daylight",
        "small", "medium", "large", "xl", "xxl", "xxxl", "xs", "mini", "pocket", "mega",
        "jumbo", "family", "value", "saver", "combo", "refill", "pouch", "jar", "tin",
        "sachet", "original", "classic", "special", "supreme", "heavy", "duty", "super",
        "extra", "ultra", "pro", "max", "plus", "neo", "lite", "eco", "smart", "digital",
        "royal", "premium", "deluxe", "regular", "pure", "natural", "fresh", "unflavoured",
        "unflavored", "bs4", "bs6", "bsiv", "vxi", "zxi", "lxi", "dtsi", "abs", "oem",
        "front", "rear", "left", "right", "upper", "lower", "set", "kit", "pair", "single", "double"
    )

    /** Flavour / series words that must not override an explicit base item noun like whey/tea/biscuit */
    private val FLAVOUR_OR_SERIES_MODIFIERS = setOf(
        "chocolate", "vanilla", "strawberry", "mango", "banana", "pineapple", "orange",
        "blueberry", "caramel", "mocha", "coffee", "butterscotch", "kesar", "pista",
        "masala", "gold", "silver", "milk", "cream", "rich", "belgian", "mint", "lemon"
    )

    /** Generic dosage/physical-form nouns that must not override an active ingredient or product noun */
    private val GENERIC_FORM_NOUNS = setOf(
        "powder", "tablet", "tablets", "capsule", "capsules", "softgel", "softgels",
        "shake", "shakes", "liquid", "syrup", "gel", "cream", "ointment", "drops",
        "formula", "supplement", "supplements", "blend", "mix",
        "block", "blocks", "strip", "strips", "bag", "bags", "sheet", "sheets",
        "bar", "bars", "roll", "rolls"
    )

    private val PRESERVE_CODE_TOKENS = Regex(
        "^(?:10w30|10w40|15w40|20w40|20w50|5w30|5w40|80w90|85w140|dot3|dot4|b12|d3|k2|mk7|" +
            "h4|h7|h11|hs1|ba20d|r15|mt15|sp125|ps5|ps4|4k|5g|4g|6a|16a|32a|63a|b22|e27|t5|t8|" +
            "cat5e|cat6|ddr4|ddr5|m2|s24|6201|6202|6203|6301|6302|6304)$"
    )

    private val ORTHOGRAPHIC_ALIASES = mapOf(
        // Supplements
        "protien" to "protein", "proten" to "protein", "protin" to "protein", "protiens" to "protein",
        "whe" to "whey", "wheyprotein" to "whey",
        "creatin" to "creatine", "creten" to "creatine", "creatinmonohydrate" to "creatine",
        "monohydrat" to "monohydrate", "monohydrte" to "monohydrate",
        "glutamin" to "glutamine", "glutmin" to "glutamine",
        "carnitin" to "carnitine", "citrullin" to "citrulline", "arginin" to "arginine",
        "ashwagnda" to "ashwagandha", "aswagandha" to "ashwagandha", "ashwaganda" to "ashwagandha",
        "shilajeet" to "shilajit", "silajit" to "shilajit", "shilajeeth" to "shilajit",
        "chavanprash" to "chyawanprash", "chyavanprash" to "chyawanprash", "chywanprash" to "chyawanprash",
        "multivitamin" to "multivitamins", "multivitmin" to "multivitamins", "vitmin" to "vitamin",
        "nutriton" to "nutrition", "nutrision" to "nutrition", "suplement" to "supplement", "suppliment" to "supplement",
        "electrolite" to "electrolyte", "electrolites" to "electrolyte",
        "probiotics" to "probiotic", "collagn" to "collagen", "omega3" to "omega",
        // Kirana & FMCG
        "biskut" to "biscuit", "biskit" to "biscuit", "bisket" to "biscuit", "biscut" to "biscuit", "biscuits" to "biscuit",
        "choklet" to "chocolate", "chocalate" to "chocolate", "choclate" to "chocolate", "choclat" to "chocolate",
        "namkin" to "namkeen", "bhujiya" to "bhujia", "bhujiaa" to "bhujia",
        "noodles" to "noodle", "nodles" to "noodle", "maggie" to "maggi",
        "masla" to "masala", "masale" to "masala", "haldhi" to "haldi", "jeeraa" to "jeera", "jira" to "jeera",
        "mirch" to "chilli", "mirchi" to "chilli", "dhaniya" to "coriander", "dhania" to "coriander",
        "chawal" to "rice", "chawl" to "rice", "aata" to "atta", "besan" to "besan",
        "daliya" to "broken wheat", "dalia" to "daliya", "maida" to "maida", "sooji" to "suji",
        "cheeni" to "sugar", "chini" to "sugar", "shakkar" to "sugar", "gud" to "jaggery", "gur" to "jaggery",
        "namak" to "salt", "doodh" to "milk", "dudh" to "milk", "dood" to "milk", "dahi" to "curd",
        "panner" to "paneer", "panir" to "paneer", "makhan" to "butter", "shahad" to "honey", "sahed" to "honey",
        "tel" to "oil", "sarson" to "mustard", "nariyal" to "coconut", "badam" to "almond", "kaju" to "cashew",
        "kishmish" to "raisins", "kismis" to "raisins", "akhrot" to "walnut",
        "saboon" to "soap", "sabun" to "soap", "shampo" to "shampoo", "shampu" to "shampoo",
        "toothpest" to "toothpaste", "toothpast" to "toothpaste", "tootpaste" to "toothpaste", "manjan" to "toothpaste",
        "detergnt" to "detergent", "detrgent" to "detergent", "surf" to "detergent",
        "jhadu" to "broom", "jharu" to "broom", "poncha" to "mop", "pocha" to "mop",
        "agarbati" to "agarbatti", "agarbatti" to "agarbatti", "kapoor" to "camphor", "kapur" to "camphor",
        "machis" to "matchbox", "mombatti" to "candle", "diaper" to "diapers", "pampers" to "pampers",
        "sanitry" to "sanitary", "sanetary" to "sanitary",
        // Electrical & Electronics
        "batry" to "battery", "battry" to "battery", "batri" to "battery", "batteri" to "battery", "bttry" to "battery", "btry" to "battery", "batteries" to "battery",
        "blub" to "bulb", "balb" to "bulb", "bulbs" to "bulb",
        "wyre" to "wire", "wair" to "wire", "taar" to "wire", "tar" to "wire", "cabel" to "cable", "cble" to "cable",
        "swich" to "switch", "swch" to "switch", "swtch" to "switch", "soket" to "socket", "sockt" to "socket",
        "regultor" to "regulator", "stablizer" to "stabilizer", "stabliser" to "stabilizer", "stabiliser" to "stabilizer",
        "invrter" to "inverter", "invrtr" to "inverter", "invertor" to "inverter",
        "geysr" to "geyser", "gizer" to "geyser", "gysr" to "geyser", "gezer" to "geyser",
        "pankha" to "fan", "ceeling" to "ceiling", "ciling" to "ceiling", "exaust" to "exhaust",
        "condensor" to "condenser", "capacitr" to "capacitor", "capcitor" to "capacitor",
        "chrgr" to "charger", "chargar" to "charger", "adpter" to "adapter", "adaptor" to "adapter",
        "earphn" to "earphone", "hedphone" to "headphone", "headphn" to "headphone", "spkr" to "speaker", "speakar" to "speaker",
        "kboard" to "keyboard", "keybord" to "keyboard", "keybrd" to "keyboard", "mous" to "mouse",
        "laptp" to "laptop", "lapotp" to "laptop", "monitr" to "monitor", "printr" to "printer", "roter" to "router", "routor" to "router",
        "pendriv" to "pendrive", "pndrive" to "pendrive", "temperd" to "tempered", "screnguard" to "screenguard",
        "multimetr" to "multimeter", "soldring" to "soldering", "submersble" to "submersible", "submrseble" to "submersible",
        // Automobile & 2W Spare Parts
        "tire" to "tyre", "tires" to "tyre", "tyres" to "tyre", "tubles" to "tubeless", "tublees" to "tubeless",
        "splender" to "splendor", "splndr" to "splendor", "actva" to "activa", "pulsr" to "pulsar",
        "brak" to "brake", "brek" to "brake", "brack" to "brake", "brke" to "brake",
        "spak" to "spark", "sprk" to "spark", "plg" to "plug",
        "shoker" to "shocker", "shockr" to "shocker", "shok" to "shock", "absorbr" to "absorber",
        "cltch" to "clutch", "cluch" to "clutch", "kalach" to "clutch",
        "sproket" to "sprocket", "sporkt" to "sprocket", "spocket" to "sprocket",
        "bearng" to "bearing", "bering" to "bearing", "bairing" to "bearing",
        "pistn" to "piston", "cilynder" to "cylinder", "cylnder" to "cylinder",
        "carborator" to "carburetor", "carburettor" to "carburetor", "carburator" to "carburetor",
        "silenser" to "silencer", "silencr" to "silencer", "exhost" to "exhaust",
        "hedlight" to "headlight", "headlite" to "headlight", "indicatr" to "indicator", "indcator" to "indicator",
        "radiatr" to "radiator", "coolnt" to "coolant", "lubricnt" to "lubricant", "grese" to "grease", "grees" to "grease",
        "wipers" to "wiper", "wipr" to "wiper", "miror" to "mirror", "mrror" to "mirror", "visr" to "visor",
        "mudgard" to "mudguard", "stator" to "stator", "altrnator" to "alternator", "alternatr" to "alternator",
        "injectr" to "injector", "tming" to "timing", "gaskt" to "gasket",
        // Pharma / Hardware / General / US-UK
        "paracitamol" to "paracetamol", "paracetmol" to "paracetamol", "medicne" to "medicine", "medcin" to "medicine", "dawai" to "medicine",
        "tablet" to "tablets", "capsul" to "capsule", "syrp" to "syrup",
        "cment" to "cement", "simnt" to "cement", "spnnr" to "spanner", "hammr" to "hammer", "plumbng" to "plumbing",
        "aluminum" to "aluminium", "fertilizer" to "fertiliser", "fertilizers" to "fertiliser",
        "fiber" to "fibre", "color" to "colour", "jewelry" to "jewellery", "molded" to "moulded", "mold" to "mould"
    )

    private val DIGIT_UNIT_REGEX = Regex("^\\d+[a-z]{1,4}$")

    private class IndexedPhrase(
        val raw: String,
        val tokens: Array<String>
    )

    private class IndexedEntry(
        val index: Int,
        val entry: HsnEntry,
        val isCurated: Boolean,
        val descNormTokens: Set<String>,
        val allNormTokens: Set<String>,
        val phrases: List<IndexedPhrase>
    )

    private class VocabWord(
        val token: String,
        val mask: Int
    )

    private class SearchIndex(
        val size: Int,
        val entries: List<IndexedEntry>,
        val vocabByFirstChar: Map<Char, List<VocabWord>>,
        val skeletonToVocab: Map<String, List<String>>,
        val tokenToEntryIndices: Map<String, IntArray>,
        val fuzzyMemo: ConcurrentHashMap<String, List<MatchedVocabToken>> = ConcurrentHashMap<String, List<MatchedVocabToken>>(512)
    )

    private fun charMask(s: String): Int {
        var m = 0
        for (i in 0 until s.length) {
            val c = s[i]
            if (c in 'a'..'z') {
                m = m or (1 shl (c.code - 97))
            } else if (c in '0'..'9') {
                m = m or (1 shl (26 + ((c.code - 48) % 6)))
            }
        }
        return m
    }

    private class CachedIndex(
        val ref: WeakReference<List<HsnEntry>>,
        val index: SearchIndex
    )

    private val indexLock = Any()
    private val indexCache = ArrayList<CachedIndex>(3)

    private fun getOrBuildIndex(entries: List<HsnEntry>): SearchIndex {
        synchronized(indexLock) {
            indexCache.removeAll { it.ref.get() == null }
            val hit = indexCache.firstOrNull { it.ref.get() === entries && it.index.size == entries.size }
            if (hit != null) return hit.index
        }
        val built = buildIndex(entries)
        synchronized(indexLock) {
            indexCache.removeAll { it.ref.get() == null || it.ref.get() === entries }
            if (entries.size > 1_000) {
                indexCache.removeAll { it.index.size > 1_000 }
            } else if (indexCache.size >= 3) {
                indexCache.removeAt(0)
            }
            indexCache.add(CachedIndex(WeakReference(entries), built))
        }
        return built
    }

    private fun tokenizeRaw(text: String): List<String> {
        val len = text.length
        if (len == 0) return emptyList()
        val out = ArrayList<String>(8)
        val sb = StringBuilder(16)
        for (i in 0 until len) {
            val rawCh = text[i]
            val c = if (rawCh in 'A'..'Z') (rawCh.code + 32).toChar() else rawCh.lowercaseChar()
            val isAlnum = (c in 'a'..'z') || (c in '0'..'9') || (c in '\u0900'..'\u097f')
            if (isAlnum) {
                sb.append(c)
            } else if (sb.isNotEmpty()) {
                out.add(sb.toString())
                sb.setLength(0)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    private fun canonicalizeToken(tok: String): String {
        val folded = ORTHOGRAPHIC_ALIASES[tok] ?: tok
        return singularize(folded)
    }

    private fun singularize(tok: String): String {
        if (tok.length <= 3) return tok
        return when {
            tok.endsWith("ies") && tok.length > 4 -> tok.dropLast(3) + "y"
            (tok.endsWith("ches") || tok.endsWith("shes") || tok.endsWith("sses") ||
                tok.endsWith("xes") || tok.endsWith("zes")) && tok.length > 4 -> tok.dropLast(2)
            tok.endsWith("s") && !tok.endsWith("ss") && !tok.endsWith("us") && !tok.endsWith("is") ->
                tok.dropLast(1)
            else -> tok
        }
    }

    /** Phonetic consonant skeleton for O(1) vowel-missing / double-letter typo repair */
    private fun consonantSkeleton(word: String): String {
        if (word.length <= 3 || word.any(Char::isDigit)) return ""
        val sb = StringBuilder(word.length)
        sb.append(word[0])
        var prev = word[0]
        for (i in 1 until word.length) {
            val c = word[i]
            if (c in "aeiouy") continue
            if (c != prev) {
                sb.append(c)
                prev = c
            }
        }
        return if (sb.length >= 3) sb.toString() else ""
    }

    private fun buildIndex(rawEntries: List<HsnEntry>): SearchIndex {
        val tokenLists = HashMap<String, MutableList<Int>>(16384)
        val strPool = HashMap<String, String>(16384)
        val phrasePool = HashMap<String, IndexedPhrase>(8192)
        val indexedEntries = ArrayList<IndexedEntry>(rawEntries.size)

        fun intern(s: String): String = strPool.getOrPut(s) { s }

        rawEntries.forEachIndexed { idx, entry ->
            val isCurated = entry.keywords.count { it == ',' } >= 3 &&
                !(entry.description.length > 10 && entry.description.all { !it.isLetter() || it.isUpperCase() })

            val descRaw = tokenizeRaw(entry.description).filter { it.length >= 2 || it.all(Char::isDigit) }
            val descNorm = HashSet<String>(max(4, descRaw.size * 2))
            descRaw.forEach { t ->
                descNorm.add(intern(t))
                descNorm.add(intern(canonicalizeToken(t)))
            }

            val kwPhrasesRaw = entry.keywords.split(',')
            val phrases = ArrayList<IndexedPhrase>(if (isCurated) min(kwPhrasesRaw.size + 1, 24) else 6)
            val seenPhraseKeys = HashSet<String>(8)

            val descMain = entry.description.split('(', ';', '-', '/').firstOrNull()?.trim().orEmpty()
            if (descMain.isNotBlank()) {
                val pToks = tokenizeRaw(descMain).map { intern(canonicalizeToken(it)) }
                    .filter { it.length >= 2 && it !in STOP_WORDS }.distinct()
                if (pToks.size in 1..6) {
                    val pKey = pToks.joinToString(" ")
                    if (seenPhraseKeys.add(pKey)) {
                        phrases.add(
                            phrasePool.getOrPut("D:$pKey:${if (isCurated) descMain else ""}") {
                                IndexedPhrase(if (isCurated) descMain else "", pToks.toTypedArray())
                            }
                        )
                    }
                }
            }

            val allNorm = HashSet<String>(descNorm)
            var extraPhraseCount = 0

            for (rawKw in kwPhrasesRaw) {
                val kw = rawKw.trim()
                if (kw.isEmpty()) continue
                val kwToks = tokenizeRaw(kw).filter { it.length >= 2 || kw.length <= 4 }
                if (kwToks.isEmpty()) continue
                val normToks = ArrayList<String>(kwToks.size)
                for (t in kwToks) {
                    val ti = intern(t)
                    val ci = intern(canonicalizeToken(ti))
                    allNorm.add(ti)
                    allNorm.add(ci)
                    if (ci !in STOP_WORDS && ci !in normToks) normToks.add(ci)
                }
                if (kwToks.size == 2 && kwToks[0].length <= 10 && kwToks[1].length <= 10) {
                    val joined = intern(kwToks[0] + kwToks[1])
                    allNorm.add(joined)
                    val jc = intern(canonicalizeToken(joined))
                    allNorm.add(jc)
                }
                if (normToks.size in 2..5 && (isCurated || extraPhraseCount < 10)) {
                    val pKey = normToks.joinToString(" ")
                    if (seenPhraseKeys.add(pKey)) {
                        val poolKey = if (isCurated) "C:$kw" else "A:$pKey"
                        phrases.add(
                            phrasePool.getOrPut(poolKey) {
                                IndexedPhrase(if (isCurated) kw else "", normToks.toTypedArray())
                            }
                        )
                        extraPhraseCount++
                    }
                }
            }

            tokenizeRaw(entry.category).filter { it.length >= 3 && it !in STOP_WORDS }.forEach {
                allNorm.add(intern(canonicalizeToken(it)))
            }

            for (t in allNorm) {
                if (t.length < 2 || t in STOP_WORDS) continue
                tokenLists.getOrPut(t) { ArrayList(4) }.add(idx)
            }
            val cleanCode = entry.hsnCode.trim().lowercase(Locale.ROOT)
            if (cleanCode.length >= 2) {
                tokenLists.getOrPut(intern(cleanCode)) { ArrayList(2) }.add(idx)
            }

            indexedEntries.add(
                IndexedEntry(
                    index = idx,
                    entry = entry,
                    isCurated = isCurated,
                    descNormTokens = descNorm,
                    allNormTokens = allNorm,
                    phrases = phrases
                )
            )
        }

        val byFirst = HashMap<Char, MutableList<VocabWord>>(40)
        val skelMap = HashMap<String, MutableList<String>>(8192)
        val tokenArrays = HashMap<String, IntArray>(tokenLists.size * 2)
        for ((tok, list) in tokenLists) {
            if (list.size > 350) {
                list.sortWith(
                    compareByDescending<Int> { indexedEntries[it].isCurated }
                        .thenByDescending { tok in indexedEntries[it].descNormTokens }
                )
            }
            tokenArrays[tok] = list.toIntArray()
            if (tok.isNotEmpty()) {
                byFirst.getOrPut(tok[0]) { ArrayList(64) }.add(VocabWord(tok, charMask(tok)))
                val skel = consonantSkeleton(tok)
                if (skel.isNotEmpty()) {
                    skelMap.getOrPut(skel) { ArrayList(2) }.add(tok)
                }
            }
        }
        return SearchIndex(rawEntries.size, indexedEntries, byFirst, skelMap, tokenArrays)
    }

    /** Optimal String Alignment distance: adjacent transpositions cost 1 edit. */
    fun damerauLevenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        if (a == b) return 0

        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            val ai = a[i - 1]
            for (j in 1..n) {
                val bj = b[j - 1]
                val cost = if (ai == bj) 0 else 1
                var best = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
                if (i > 1 && j > 1 && ai == b[j - 2] && a[i - 2] == bj) {
                    best = min(best, dp[i - 2][j - 2] + cost)
                }
                dp[i][j] = best
            }
        }
        return dp[m][n]
    }

    private data class MatchedVocabToken(
        val vocabToken: String,
        val similarity: Double,
        val isFuzzy: Boolean
    )

    private fun findMatchingVocabTokens(
        rawTok: String,
        canonTok: String,
        index: SearchIndex,
        allowLoosePrefix: Boolean
    ): List<MatchedVocabToken> {
        val results = ArrayList<MatchedVocabToken>(14)
        val seen = HashSet<String>()

        fun addMatch(tok: String, sim: Double, fuzzy: Boolean) {
            if (tok !in index.tokenToEntryIndices) return
            if (seen.add(tok)) {
                results.add(MatchedVocabToken(tok, sim, fuzzy))
            }
        }

        val wasAliased = (ORTHOGRAPHIC_ALIASES[rawTok] != null)

        // 1. Exact & alias canonical match
        if (rawTok in index.tokenToEntryIndices) addMatch(rawTok, 1.0, false)
        if (canonTok != rawTok && canonTok in index.tokenToEntryIndices) {
            addMatch(canonTok, if (wasAliased) 0.96 else 1.0, wasAliased)
        }
        if (results.any { !it.isFuzzy || it.similarity >= 0.95 }) return results

        // 2. Numeric code prefix match
        if (rawTok.length >= 4 && rawTok.all(Char::isDigit)) {
            for (vw in index.vocabByFirstChar[rawTok[0]].orEmpty()) {
                val v = vw.token
                if (v.startsWith(rawTok) && v != rawTok) addMatch(v, 0.92, false)
            }
            return results
        }

        if (canonTok.length < 3) return results

        val memoKey = if (allowLoosePrefix) "$rawTok|$canonTok|1" else "$rawTok|$canonTok|0"
        index.fuzzyMemo[memoKey]?.let { return it }

        // 3. O(1) phonetic consonant-skeleton lookup (catches arbitrary omitted vowels / double letters!)
        val skel = consonantSkeleton(canonTok)
        if (skel.length >= 3) {
            for (v in index.skeletonToVocab[skel].orEmpty()) {
                if (v == rawTok || v == canonTok) continue
                val dist = damerauLevenshtein(canonTok, v)
                if (dist <= max(2, canonTok.length / 2)) {
                    val sim = max(0.74, 1.0 - dist.toDouble() / max(canonTok.length, v.length).toDouble())
                    addMatch(v, sim, true)
                }
            }
        }

        // 4. Bounded Damerau-Levenshtein & prefix search within vocab bucket
        val len = canonTok.length
        val maxDist = when {
            len <= 4 -> 1
            len <= 9 -> 2
            else -> 3
        }
        val qMask = charMask(canonTok)
        val maxBitDiff = maxDist * 2
        val bucketChars = if (len >= 4 && canonTok[1] != canonTok[0]) {
            charArrayOf(canonTok[0], canonTok[1])
        } else {
            charArrayOf(canonTok[0])
        }

        var fuzzyCount = 0
        for (bi in bucketChars.indices) {
            if (bi > 0 && results.isNotEmpty()) break
            val ch = bucketChars[bi]
            val bucket = index.vocabByFirstChar[ch] ?: continue
            for (vw in bucket) {
                val v = vw.token
                val vLen = v.length
                if (vLen < 3 || v == canonTok || v == rawTok) continue
                if (v[0] in '0'..'9' && v.all(Char::isDigit)) continue

                if (allowLoosePrefix && len >= 5 && vLen in (len + 1)..(len + 5) &&
                    ch == canonTok[0] && v.startsWith(canonTok)
                ) {
                    addMatch(v, max(0.75, len.toDouble() / vLen.toDouble()), true)
                    continue
                }
                if (abs(vLen - len) > maxDist) continue
                if (Integer.bitCount(qMask xor vw.mask) > maxBitDiff) continue
                if (len >= 5 && canonTok[0] != v[0] && canonTok[1] != v[0]) continue

                val dist = damerauLevenshtein(canonTok, v)
                if (dist <= maxDist) {
                    val sim = 1.0 - (dist.toDouble() / max(len, vLen).toDouble())
                    val minSim = when {
                        len == 3 -> 0.66
                        len == 4 -> 0.74
                        len >= 9 -> 0.70
                        else -> 0.65
                    }
                    if (sim >= minSim) {
                        addMatch(v, sim, true)
                        fuzzyCount++
                        if (fuzzyCount >= 24) break
                    }
                }
            }
        }
        if (index.fuzzyMemo.size < 4_096) {
            index.fuzzyMemo[memoKey] = results
        }
        return results
    }

    /**
     * Separates real SKU product nouns/brands from pack sizes, colors, voltages, and flavours
     * so long real-world SKU strings like "ON Gold Standard 100% Whey Double Rich Chocolate 5lbs"
     * or "Maruti Swift Dzire VXi Front Brake Pad" resolve cleanly.
     */
    private fun extractCoreTokens(tokens: List<String>): List<String> {
        val core = tokens.filter { t ->
            if (PRESERVE_CODE_TOKENS.matches(t)) return@filter true
            if (t.all(Char::isDigit)) return@filter false
            if (t in VARIANT_NOISE_WORDS || t == "assembly" || t == "subassembly") return@filter false
            // Drop pure numeric+unit fused tokens like "500gm", "100ml", "150ah", "9w", "2kg"
            if (DIGIT_UNIT_REGEX.matches(t) && !PRESERVE_CODE_TOKENS.matches(t)) return@filter false
            true
        }
        val withoutFlavour = core.filter { it !in FLAVOUR_OR_SERIES_MODIFIERS }
        return when {
            withoutFlavour.isNotEmpty() -> withoutFlavour
            core.isNotEmpty() -> core
            else -> tokens
        }
    }

    private class FastCandidate(
        val ie: IndexedEntry,
        val fastTotalScore: Int,
        val tokenScore: Int,
        val coveredExact: Int,
        val coveredAny: Int,
        val anyFuzzyUsed: Boolean,
        val lastTokenMatched: Boolean,
        val matchedNonFormToken: Boolean,
        val matchedCompletePhrase: Boolean,
        val coverageRatio: Double,
        val allMatchedDescTokens: Boolean,
        val bestPhrase: IndexedPhrase?
    )

    fun lookup(
        query: String,
        entries: List<HsnEntry>,
        maxResults: Int = 5
    ): LookupResult {
        val rawTrimmed = query.trim()
        if (rawTrimmed.isEmpty() || entries.isEmpty()) {
            return LookupResult(MatchConfidence.NONE, emptyList())
        }

        val translated = try {
            LanguageTranslationUtils.translateItem(rawTrimmed)
        } catch (_: Throwable) {
            rawTrimmed
        }
        val cleanRaw = rawTrimmed.lowercase(Locale.ROOT).replace('-', ' ').replace('_', ' ').trim()
        val cleanTrans = translated.lowercase(Locale.ROOT).replace('-', ' ').replace('_', ' ').trim()
        val effectiveBase = if (cleanTrans.isNotBlank() && cleanTrans != cleanRaw) cleanTrans else cleanRaw

        // Strip inline measurements ("1 kg", "500 ml", "9 w") when extracting product core
        val specStripped = effectiveBase.replace(SPEC_MEASUREMENT_REGEX, " ").trim()
        val allRawToks = tokenizeRaw(effectiveBase)
            .filter { (it.length >= 2 || it.all(Char::isDigit)) && it !in STOP_WORDS }
        val strippedToks = tokenizeRaw(specStripped)
            .filter { (it.length >= 2 || it.all(Char::isDigit)) && it !in STOP_WORDS }
        val baseToks = strippedToks.ifEmpty { allRawToks }
        if (baseToks.isEmpty()) return LookupResult(MatchConfidence.NONE, emptyList())

        val queryTokens = extractCoreTokens(baseToks)
        val canonQueryTokens = queryTokens.map { canonicalizeToken(it) }
        val rawCoreTokens = if (cleanTrans != cleanRaw) {
            val rawStripped = cleanRaw.replace(SPEC_MEASUREMENT_REGEX, " ").trim()
            val rawToks = tokenizeRaw(rawStripped)
                .ifEmpty { tokenizeRaw(cleanRaw) }
                .filter { (it.length >= 2 || it.all(Char::isDigit)) && it !in STOP_WORDS }
            extractCoreTokens(rawToks)
        } else {
            emptyList()
        }
        val index = getOrBuildIndex(entries)

        // Direct numeric HSN/SAC lookup (exact 2-8 digit code or 4+ digit prefix)
        if (queryTokens.size == 1 && queryTokens[0].all(Char::isDigit) &&
            (queryTokens[0].length >= 4 || (queryTokens[0].length >= 2 && entries.any { it.hsnCode == queryTokens[0] }))
        ) {
            val codeQ = queryTokens[0]
            val codeMatches = entries
                .filter { it.hsnCode == codeQ || it.hsnCode.startsWith(codeQ) }
                .sortedWith(compareBy<HsnEntry>({ it.hsnCode != codeQ }, { it.hsnCode.length }, { it.description.length }))
                .take(maxResults)
                .map {
                    HsnSuggestion(
                        entry = it,
                        matchedLabel = formatCleanTitle(it.description),
                        score = if (it.hsnCode == codeQ) 100_000 else 50_000,
                        isExactMatch = it.hsnCode == codeQ,
                        usedFuzzyRepair = false,
                        wasReordered = false
                    )
                }
            if (codeMatches.isNotEmpty()) {
                val conf = if (codeMatches.size == 1 || codeMatches.first().isExactMatch) {
                    MatchConfidence.EXACT_CONFIDENT
                } else {
                    MatchConfidence.AMBIGUOUS_MULTIPLE
                }
                return LookupResult(conf, codeMatches)
            }
        }

        val perTokenMatches = queryTokens.indices.map { i ->
            findMatchingVocabTokens(queryTokens[i], canonQueryTokens[i], index, allowLoosePrefix = true)
        }

        val candidateEntryIndices = LinkedHashSet<Int>(320)
        for (matches in perTokenMatches) {
            for (m in matches) {
                val arr = index.tokenToEntryIndices[m.vocabToken] ?: continue
                val limit = when {
                    m.isFuzzy && arr.size > 250 -> 150
                    queryTokens.size >= 2 && arr.size > 400 -> 350
                    else -> arr.size
                }
                for (k in 0 until limit) candidateEntryIndices.add(arr[k])
            }
        }
        // Check concatenated bigrams for split/joined brand names (e.g. "parle g" <-> "parleg", "aero space" etc.)
        var bigramHits: HashSet<Int>? = null
        if (queryTokens.size >= 2) {
            for (j in 0 until queryTokens.size - 1) {
                val joined = queryTokens[j] + queryTokens[j + 1]
                val joinedCanon = canonicalizeToken(joined)
                index.tokenToEntryIndices[joined]?.forEach { idx ->
                    candidateEntryIndices.add(idx)
                    (bigramHits ?: HashSet<Int>(8).also { bigramHits = it }).add(idx)
                }
                if (joinedCanon != joined) {
                    index.tokenToEntryIndices[joinedCanon]?.forEach { idx ->
                        candidateEntryIndices.add(idx)
                        (bigramHits ?: HashSet<Int>(8).also { bigramHits = it }).add(idx)
                    }
                }
            }
        }
        if (candidateEntryIndices.isEmpty()) return LookupResult(MatchConfidence.NONE, emptyList())

        val corePhraseQuery = queryTokens.joinToString(" ")
        val reverseQuery = if (queryTokens.size == 2) "${queryTokens[1]} ${queryTokens[0]}" else null
        val headNounIdx = queryTokens.indices.lastOrNull { !PRESERVE_CODE_TOKENS.matches(queryTokens[it]) }
            ?: queryTokens.lastIndex
        val qCount = queryTokens.size
        val slotVocab = arrayOfNulls<String>(qCount)
        val slotSim = DoubleArray(qCount)
        val coreDiffers = corePhraseQuery != effectiveBase
        val cleanDiffers = cleanTrans != cleanRaw
        val queryHasNonFormToken = canonQueryTokens.any { it !in GENERIC_FORM_NOUNS }
        val lastQueryIsGenericForm = canonQueryTokens.lastOrNull() in GENERIC_FORM_NOUNS

        // Pass 1: Zero-allocation token & phrase screening across all candidate entries
        val fastCandidates = ArrayList<FastCandidate>(min(candidateEntryIndices.size, 320))
        for (eIdx in candidateEntryIndices) {
            val ie = index.entries[eIdx]
            val entry = ie.entry
            val bigramHit = bigramHits?.contains(eIdx) == true

            var coveredExact = 0
            var coveredAny = 0
            var simSum = 0.0
            var anyFuzzyUsed = false
            var tailTokensMatched = 0
            var lastTokenMatched = false
            var matchedNonFormToken = false

            for (i in 0 until qCount) {
                val qRaw = queryTokens[i]
                val qCanon = canonQueryTokens[i]
                var bestTokSim = 0.0
                var bestTokFuzzy = false
                var bestVocab = qCanon

                if (qRaw in ie.allNormTokens && !ORTHOGRAPHIC_ALIASES.containsKey(qRaw)) {
                    bestTokSim = 1.0
                    bestTokFuzzy = false
                    bestVocab = qCanon
                } else if (qCanon in ie.allNormTokens) {
                    val aliased = ORTHOGRAPHIC_ALIASES.containsKey(qRaw)
                    bestTokSim = if (aliased) 0.96 else 1.0
                    bestTokFuzzy = aliased
                    bestVocab = qCanon
                } else {
                    for (m in perTokenMatches[i]) {
                        if (m.vocabToken in ie.allNormTokens && m.similarity > bestTokSim) {
                            bestTokSim = m.similarity
                            bestTokFuzzy = m.isFuzzy
                            bestVocab = m.vocabToken
                        }
                    }
                }

                if (bestTokSim > 0.0) {
                    coveredAny++
                    if (!bestTokFuzzy) coveredExact++
                    if (bestTokFuzzy) anyFuzzyUsed = true
                    if (bestVocab !in GENERIC_FORM_NOUNS && qCanon !in GENERIC_FORM_NOUNS) {
                        matchedNonFormToken = true
                    }
                    simSum += bestTokSim
                    slotVocab[i] = bestVocab
                    slotSim[i] = bestTokSim
                    if (i >= max(0, headNounIdx - 1)) tailTokensMatched++
                    if (i == headNounIdx || i == qCount - 1) lastTokenMatched = true
                } else {
                    slotVocab[i] = null
                    slotSim[i] = 0.0
                }
            }

            if (coveredAny == 0 && !bigramHit) continue

            // Find best phrase match: handles both full-query reordering AND long unseen-brand SKUs matching a tail product phrase!
            var bestPhrase: IndexedPhrase? = null
            var bestPhraseScore = 0.0
            var matchedCompletePhrase = false
            var exactPhraseHint = if (
                entry.description.equals(effectiveBase, ignoreCase = true) ||
                (coreDiffers && entry.description.equals(corePhraseQuery, ignoreCase = true))
            ) 50_000 else if (bigramHit) 25_000 else 0

            for (p in ie.phrases) {
                if (exactPhraseHint < 35_000 && coveredExact == qCount && (
                        p.raw.equals(effectiveBase, ignoreCase = true) ||
                        (coreDiffers && p.raw.equals(corePhraseQuery, ignoreCase = true)) ||
                        (cleanDiffers && p.raw.equals(cleanRaw, ignoreCase = true))
                    )
                ) {
                    exactPhraseHint = 35_000
                }
                if (coveredAny == 0) continue
                var hits = 0
                var phraseSimSum = 0.0
                for (pt in p.tokens) {
                    for (i in 0 until qCount) {
                        if (slotSim[i] > 0.0 && slotVocab[i] == pt) {
                            hits++
                            phraseSimSum += slotSim[i]
                            break
                        }
                    }
                }
                if (hits > 0) {
                    val avgPSim = phraseSimSum / hits.toDouble()
                    val pRatio = hits.toDouble() / p.tokens.size.toDouble()
                    val qRatio = hits.toDouble() / qCount.toDouble()
                    val fullPhraseComplete = (hits == p.tokens.size && p.tokens.size >= 2 && avgPSim >= 0.85)
                    val scoreVal = if (hits >= 2 || (p.tokens.size == 1 && avgPSim >= 0.85)) {
                        ((hits * 4_500) +
                            (pRatio * 5_000) +
                            (qRatio * 5_500) +
                            (if (fullPhraseComplete) 7_000 else 0)) * avgPSim
                    } else {
                        // Single-word match against a multi-word phrase or low-sim fuzzy guess is only a partial fragment
                        pRatio * 1_500 * avgPSim
                    }
                    if (scoreVal > bestPhraseScore) {
                        bestPhraseScore = scoreVal
                        bestPhrase = p
                        matchedCompletePhrase = fullPhraseComplete
                    }
                }
            }

            val coverageRatio = coveredAny.toDouble() / qCount.toDouble()
            val avgSim = simSum / qCount.toDouble()
            val tokenScore = (coveredExact * 2_400) +
                ((coveredAny - coveredExact) * 1_600) +
                (if (coveredAny == qCount) (8_500 * avgSim).toInt() else 0) +
                (if (tailTokensMatched >= 2) 3_500 else if (lastTokenMatched) 2_500 else 0) +
                bestPhraseScore.toInt()
            var allMatchedDescTokens = coveredAny > 0
            if (allMatchedDescTokens) {
                for (i in 0 until qCount) {
                    val sv = slotVocab[i]
                    if (sv != null && sv !in ie.descNormTokens) {
                        allMatchedDescTokens = false
                        break
                    }
                }
            }

            var fastTotalScore = max(exactPhraseHint, tokenScore) +
                (if (coveredAny == qCount && exactPhraseHint >= 8_000) 4_000 else 0)
            if (ie.isCurated && (coverageRatio >= 0.65 || matchedCompletePhrase || lastTokenMatched)) {
                fastTotalScore += 1_500
            }
            if (allMatchedDescTokens && (coverageRatio >= 0.5 || qCount == 1)) {
                fastTotalScore += 1_200
            }
            if (qCount >= 2) {
                if (queryHasNonFormToken && !matchedNonFormToken && exactPhraseHint < 24_000) {
                    fastTotalScore = (fastTotalScore * 0.15).toInt()
                } else if (!lastTokenMatched && exactPhraseHint < 35_000) {
                    if (lastQueryIsGenericForm && (ie.isCurated || coveredAny >= 2)) {
                        fastTotalScore = (fastTotalScore * 0.90).toInt()
                    } else {
                        fastTotalScore = (fastTotalScore * 0.25).toInt()
                    }
                } else if (!matchedCompletePhrase) {
                    if (coverageRatio < 0.5 && exactPhraseHint < 24_000 && (!ie.isCurated || !lastTokenMatched)) {
                        fastTotalScore = (fastTotalScore * 0.35).toInt()
                    } else if (qCount >= 3 && coverageRatio < 0.65 && exactPhraseHint < 24_000 && !lastTokenMatched) {
                        fastTotalScore = (fastTotalScore * 0.45).toInt()
                    }
                }
            }

            if (fastTotalScore < 400 && !bigramHit) continue

            fastCandidates.add(
                FastCandidate(
                    ie = ie,
                    fastTotalScore = fastTotalScore,
                    tokenScore = tokenScore,
                    coveredExact = coveredExact,
                    coveredAny = coveredAny,
                    anyFuzzyUsed = anyFuzzyUsed,
                    lastTokenMatched = lastTokenMatched,
                    matchedNonFormToken = matchedNonFormToken,
                    matchedCompletePhrase = matchedCompletePhrase,
                    coverageRatio = coverageRatio,
                    allMatchedDescTokens = allMatchedDescTokens,
                    bestPhrase = bestPhrase
                )
            )
        }

        if (fastCandidates.isEmpty()) return LookupResult(MatchConfidence.NONE, emptyList())

        fastCandidates.sortWith(
            compareByDescending<FastCandidate> { it.coveredAny == qCount && it.fastTotalScore >= 10000 }
                .thenByDescending { it.fastTotalScore }
                .thenByDescending { it.ie.isCurated }
                .thenByDescending { it.coveredExact }
        )

        // Pass 2: Full exact/reverse refinement on top 10 + curated full-coverage matches in top 40
        val topCount = min(fastCandidates.size, 40)
        val scoredCandidates = ArrayList<HsnSuggestion>(topCount)

        for (k in 0 until topCount) {
            val fc = fastCandidates[k]
            val ie = fc.ie
            val entry = ie.entry

            val runExactCheck = (k < 10 || (ie.isCurated && fc.coveredAny == qCount))
            val repairedViaTranslation = rawCoreTokens.isNotEmpty() && rawCoreTokens.any { r ->
                r.length >= 3 && r.all { it in 'a'..'z' } && singularize(r) !in ie.allNormTokens && r !in ie.allNormTokens
            }
            val effectiveFuzzy = fc.anyFuzzyUsed || repairedViaTranslation

            var exactScore = 0
            var revScore = 0
            if (runExactCheck) {
                exactScore = HSNSyncRepository.scoreHsnMatch(corePhraseQuery, entry)
                if (coreDiffers && k < 4) {
                    exactScore = max(exactScore, HSNSyncRepository.scoreHsnMatch(effectiveBase, entry))
                }
                if (cleanDiffers) {
                    exactScore = max(exactScore, HSNSyncRepository.scoreHsnMatch(cleanRaw, entry))
                }
                if (reverseQuery != null && fc.coveredExact == 2) {
                    revScore = HSNSyncRepository.scoreHsnMatch(reverseQuery, entry)
                }
            }

            var scoreCalc = exactScore
            if (revScore > scoreCalc) {
                scoreCalc = max(scoreCalc, (revScore * 0.92).toInt())
            }

            scoreCalc = max(scoreCalc, fc.tokenScore) +
                (if (fc.coveredAny == qCount && scoreCalc >= 8_000) 4_000 else 0)

            if (ie.isCurated && (fc.coverageRatio >= 0.65 || fc.matchedCompletePhrase || fc.lastTokenMatched)) {
                scoreCalc += 1_500
            }
            if (fc.allMatchedDescTokens && (fc.coverageRatio >= 0.5 || qCount == 1)) {
                scoreCalc += 1_200
            }

            // Penalize partial accidental hits and brand/vehicle-only hits missing the trailing product noun
            if (qCount >= 2) {
                if (queryHasNonFormToken && !fc.matchedNonFormToken && exactScore < 24_000) {
                    scoreCalc = (scoreCalc * 0.15).toInt()
                } else if (!fc.lastTokenMatched && exactScore < 60_000) {
                    if (lastQueryIsGenericForm && (ie.isCurated || fc.coveredAny >= 2)) {
                        scoreCalc = (scoreCalc * 0.90).toInt()
                    } else {
                        // Head noun is typically at the end (e.g. "Maruti Swift Brake Pad", "Hero Splendor Shocker")
                        scoreCalc = (scoreCalc * 0.25).toInt()
                    }
                } else if (!fc.matchedCompletePhrase) {
                    if (fc.coverageRatio < 0.5 && exactScore < 24_000 && (!ie.isCurated || !fc.lastTokenMatched)) {
                        scoreCalc = (scoreCalc * 0.35).toInt()
                    } else if (qCount >= 3 && fc.coverageRatio < 0.65 && exactScore < 24_000 && !fc.lastTokenMatched) {
                        scoreCalc = (scoreCalc * 0.45).toInt()
                    }
                }
            }

            if (scoreCalc < 900) continue
            val exactPhraseHit = exactScore >= 24_000 && !effectiveFuzzy && fc.coveredExact == qCount
            val isReordered = !exactPhraseHit && !effectiveFuzzy && fc.coveredExact == qCount &&
                (revScore > exactScore || fc.bestPhrase != null)

            scoredCandidates.add(
                HsnSuggestion(
                    entry = entry,
                    matchedLabel = buildDisplayLabel(entry, fc.bestPhrase?.raw, ie.isCurated),
                    score = scoreCalc,
                    isExactMatch = exactPhraseHit && !isReordered,
                    usedFuzzyRepair = effectiveFuzzy,
                    wasReordered = isReordered
                )
            )
        }

        if (scoredCandidates.isEmpty()) return LookupResult(MatchConfidence.NONE, emptyList())

        scoredCandidates.sortWith(
            compareByDescending<HsnSuggestion> { it.score }
                .thenByDescending { it.isExactMatch }
                .thenBy { it.entry.hsnCode.length }
        )

        val picked = diversifyTopCandidates(scoredCandidates, maxResults)
        val top = picked.first()
        val second = picked.getOrNull(1)

        val strongCompetitors = picked.filterIndexed { idx, s ->
            if (idx == 0) true
            else isDistinctInterpretation(top.entry, s.entry) && s.score >= (top.score * 0.58).toInt()
        }

        val confidence = when {
            strongCompetitors.size >= 2 && (
                top.usedFuzzyRepair ||
                    top.wasReordered ||
                    !top.isExactMatch ||
                    (queryTokens.size == 1 && second != null && second.score >= (top.score * 0.68).toInt() && isDistinctInterpretation(top.entry, second.entry)) ||
                    (second != null && second.score >= (top.score * 0.85).toInt() && top.entry.gstRate != second.entry.gstRate)
                ) -> {
                val shown = if (strongCompetitors.size == 2) strongCompetitors else strongCompetitors.take(4)
                return LookupResult(MatchConfidence.AMBIGUOUS_MULTIPLE, shown)
            }
            top.usedFuzzyRepair || top.wasReordered -> {
                return LookupResult(MatchConfidence.DID_YOU_MEAN_ONE, listOf(top))
            }
            top.isExactMatch || top.score >= 12_000 -> MatchConfidence.EXACT_CONFIDENT
            else -> MatchConfidence.DID_YOU_MEAN_ONE
        }

        val finalList = if (confidence == MatchConfidence.DID_YOU_MEAN_ONE) listOf(top) else picked
        return LookupResult(confidence, finalList)
    }

    /**
     * High-confidence single best resolution for automated batch/voice flows (FinanceHelper, Excel import).
     */
    fun findConfidentAutoMatch(
        itemName: String,
        category: String? = null,
        entries: List<HsnEntry>
    ): HsnEntry? {
        val raw = itemName.trim()
        if (raw.length < 2) return null

        val queryToSearch = if (!category.isNullOrBlank() &&
            !category.equals("General", true) &&
            !category.equals("General Inventory", true) &&
            !category.equals("Inventory", true) &&
            raw.split(MULTI_SPACE).size <= 2
        ) {
            val itemOnly = lookup(raw, entries, maxResults = 3)
            val topItemOnly = itemOnly.suggestions.firstOrNull()
            if (topItemOnly != null && topItemOnly.score >= 14_000) {
                return verifyAutoConfidence(raw, topItemOnly)
            }
            "$category $raw"
        } else {
            raw
        }

        val res = lookup(queryToSearch, entries, maxResults = 3)
        val top = res.suggestions.firstOrNull() ?: return null
        return verifyAutoConfidence(raw, top)
    }

    private fun verifyAutoConfidence(rawItem: String, top: HsnSuggestion): HsnEntry? {
        val rawToks = extractCoreTokens(
            tokenizeRaw(rawItem).filter { it.length >= 2 && it !in STOP_WORDS }
        )
        if (rawToks.isEmpty()) return null
        if (top.isExactMatch && top.score >= 10_000) return top.entry
        if (rawToks.size == 1) {
            val t = rawToks[0]
            if (t.length < 4 && top.usedFuzzyRepair && top.score < 20_000) return null
            return if (top.score >= 9_500) top.entry else null
        }
        return if (top.score >= 10_500) top.entry else null
    }

    private fun diversifyTopCandidates(sorted: List<HsnSuggestion>, maxResults: Int): List<HsnSuggestion> {
        val out = ArrayList<HsnSuggestion>(maxResults)
        val seenKeys = HashSet<String>()
        val countPerH4 = HashMap<String, Int>()

        for (s in sorted) {
            val h4 = s.entry.hsnCode.take(4)
            val descKey = s.entry.description.lowercase(Locale.ROOT)
                .replace(Regex("\\([^)]*\\)"), "")
                .replace(Regex("[^a-z0-9\\s]"), "")
                .trim()
                .take(36)
            val key = "$h4|${s.entry.gstRate}|$descKey"
            if (!seenKeys.add(key)) continue

            val c = countPerH4[h4] ?: 0
            val sameRateExists = out.any { it.entry.hsnCode.take(4) == h4 && it.entry.gstRate == s.entry.gstRate }
            if (c >= 2 && sameRateExists) continue

            out.add(s)
            countPerH4[h4] = c + 1
            if (out.size >= maxResults) break
        }
        return out
    }

    private fun isDistinctInterpretation(a: HsnEntry, b: HsnEntry): Boolean {
        if (a.gstRate != b.gstRate) return true
        if (a.hsnCode.take(4) != b.hsnCode.take(4)) return true
        val aWords = tokenizeRaw(a.description).filter { it.length >= 4 && it !in STOP_WORDS }.toSet()
        val bWords = tokenizeRaw(b.description).filter { it.length >= 4 && it !in STOP_WORDS }.toSet()
        if (aWords.isEmpty() || bWords.isEmpty()) return false
        val overlap = aWords.intersect(bWords).size
        return overlap < min(aWords.size, bWords.size)
    }

    private fun formatCleanTitle(desc: String): String {
        val clean = desc.trim()
        return if (clean.length > 4 && clean.all { !it.isLetter() || it.isUpperCase() }) {
            clean.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        } else {
            clean
        }
    }

    private fun buildDisplayLabel(entry: HsnEntry, bestPhrase: String?, isCurated: Boolean): String {
        val cleanDesc = formatCleanTitle(entry.description)
        if (bestPhrase.isNullOrBlank()) return cleanDesc
        val phraseTitle = bestPhrase.trim().replaceFirstChar { it.titlecase(Locale.ROOT) }
        return if (isCurated && !cleanDesc.contains(bestPhrase.trim(), ignoreCase = true) && phraseTitle.length <= 36) {
            "$phraseTitle — $cleanDesc"
        } else {
            cleanDesc
        }
    }
}
