package com.minhas.teller.utils

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/**
 * Offline Hindi / Hinglish -> English item-name dictionary (about 9,700 spellings).
 *
 * The data lives in assets/hindi_items.tsv ("key<TAB>English"), where each key is stored in
 * [norm] form so that spelling variants (paani / pani, haldi / haldee, joota / juta) land on the
 * same entry. Sources: hand-curated shop vocabulary plus the Apache-2.0 "hindi-vocab" word list
 * (github.com/openhindi/hindi-vocab), romanised at build time.
 *
 * [norm] MUST stay identical to norm() in the build script that produced the file.
 */
object HindiItemDictionary {

    @Volatile private var map: Map<String, String> = emptyMap()

    private val PARTICLES = setOf("ka", "ki", "ke", "ko", "wala", "wali", "wale", "vala", "vali", "vale")
    private val WS = Regex("\\s+")
    private val NON_ALNUM = Regex("[^a-z0-9]")
    private val ASPIRATE = Regex("([kgjtdb])h")
    private val REPEATS = Regex("([a-z])\\1+")
    private val PAIRS = listOf(
        "aa" to "a", "ee" to "i", "ii" to "i", "oo" to "u", "uu" to "u",
        "ph" to "f", "chh" to "c", "ch" to "c", "sh" to "s"
    )

    /** Loads the asset. Safe to call more than once; failures leave the dictionary empty. */
    fun load(context: Context) {
        if (map.isNotEmpty()) return
        try {
            val loaded = HashMap<String, String>(16384)
            context.applicationContext.assets.open("hindi_items.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab <= 0 || line.startsWith("#")) continue
                    loaded[line.substring(0, tab)] = line.substring(tab + 1).trim()
                }
            }
            map = loaded
        } catch (_: Throwable) {
            // Dictionary is an optional extra: the built-in word list still works without it.
        }
    }

    fun norm(raw: String): String {
        var s = Normalizer.normalize(raw.trim().lowercase(Locale.ROOT), Normalizer.Form.NFC)
        if (s.any { it in 'ऀ'..'ॿ' }) return s.replace(WS, "")
        s = s.replace(NON_ALNUM, "")
        for ((a, b) in PAIRS) s = s.replace(a, b)
        s = s.replace(ASPIRATE, "$1")
        s = s.replace('w', 'v').replace('z', 'j').replace('q', 'k').replace('o', 'u')
        return s.replace(REPEATS, "$1")
    }

    /** English name for a Hindi/Hinglish item, or null when the word(s) are not all known. */
    fun translate(input: String): String? {
        val dict = map
        if (dict.isEmpty()) return null
        val text = input.trim()
        if (text.isEmpty()) return null

        dict[norm(text)]?.let { return it }

        val tokens = text.split(WS).filter { it.isNotBlank() && it.lowercase(Locale.ROOT) !in PARTICLES }
        if (tokens.size < 2 || tokens.size > 4) return null
        val parts = ArrayList<String>(tokens.size)
        for (t in tokens) {
            val hit = dict[norm(t)] ?: return null   // any unknown word: leave the name untouched
            parts.add(hit)
        }
        return parts.joinToString(" ")
    }
}
