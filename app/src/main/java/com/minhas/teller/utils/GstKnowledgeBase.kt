package com.minhas.teller.utils

import android.content.Context

/**
 * Offline GST law knowledge base — a small retrieval layer over the consolidated GST corpus
 * bundled at `assets/gst_knowledge.txt` (the CBIC Acts/Rules, GSTR-9/9C guides, CA-certificate
 * and departmental-audit handbooks).
 *
 * The on-device model can't "know" ~2,000 pages of statute by itself, so we retrieve the most
 * relevant sections for a question and hand them to the model (or show them to the user) with a
 * source citation. Retrieval is pure keyword/phrase scoring — no embeddings — so it is fully
 * offline, fast, and unit-testable.
 */
object GstKnowledgeBase {

    data class Chunk(val source: String, val page: Int, val text: String)

    data class Match(val chunk: Chunk, val score: Int)

    const val ASSET_NAME = "gst_knowledge.txt"

    private val marker = Regex("<<<SOURCE: (.+?) \\| PAGE: (\\d+)>>>")
    private val stopWords = setOf(
        "and", "the", "for", "with", "that", "this", "from", "are", "was", "have", "has", "not",
        "but", "all", "any", "such", "into", "upon", "shall", "than", "then", "them", "they",
        "under", "which", "while", "who", "will", "would", "their", "there", "these", "those",
        "what", "when", "where", "per", "section", "sections", "act", "rule", "rules"
    )

    /** Load the bundled corpus text once (caller caches). */
    fun loadCorpus(context: Context): String =
        context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** Split the consolidated corpus into searchable, source-cited chunks. */
    fun chunk(corpusText: String, targetChars: Int = 700): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var source = ""
        var page = 0
        val buffer = StringBuilder()

        fun flush() {
            val text = buffer.toString().trim()
            if (text.isNotEmpty()) {
                chunks.addAll(splitIntoChunks(text, source, page, targetChars))
            }
            buffer.setLength(0)
        }

        for (line in corpusText.lineSequence()) {
            val trimmed = line.trim()
            val m = marker.matchEntire(trimmed)
            if (m != null) {
                flush()
                source = m.groupValues[1].trim()
                page = m.groupValues[2].toIntOrNull() ?: 0
            } else {
                // Drop obvious running-header/footer noise.
                if (trimmed.isNotEmpty() && !trimmed.matches(Regex("Page \\d+.*"))) {
                    buffer.append(trimmed).append('\n')
                }
            }
        }
        flush()
        return chunks
    }

    private fun splitIntoChunks(text: String, source: String, page: Int, targetChars: Int): List<Chunk> {
        val result = mutableListOf<Chunk>()
        val words = text.split(Regex("\\s+"))
        val current = StringBuilder()
        for (word in words) {
            if (current.length + word.length + 1 > targetChars && current.isNotEmpty()) {
                result.add(Chunk(source, page, current.toString().trim()))
                current.setLength(0)
            }
            current.append(word).append(' ')
        }
        if (current.isNotBlank()) result.add(Chunk(source, page, current.toString().trim()))
        return result
    }

    /** Score chunks against a query (phrase match > word overlap) and return the top [k]. */
    fun search(chunks: List<Chunk>, query: String, k: Int = 3): List<Match> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return emptyList()
        val words = q.split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in stopWords }

        return chunks.map { chunk ->
            val text = chunk.text.lowercase()
            var score = 0
            if (text.contains(q)) score += 1000
            for (w in words) if (text.contains(w)) score += 100
            Match(chunk, score)
        }.filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(k)
    }

    /** Answer a question with the top relevant sections, each cited by source + page. */
    fun answer(corpusText: String, query: String, k: Int = 2): String? {
        val matches = search(chunk(corpusText), query, k)
        if (matches.isEmpty()) return null
        return formatAnswer(matches)
    }

    @Volatile
    private var cachedChunks: List<Chunk>? = null

    /** Lazily load + chunk the bundled corpus once (cached for the process lifetime). */
    fun chunks(context: Context): List<Chunk> = cachedChunks
        ?: synchronized(this) {
            cachedChunks ?: chunk(loadCorpus(context)).also { cachedChunks = it }
        }

    /** Answer using the cached bundled corpus (production path). */
    fun answer(context: Context, query: String, k: Int = 2): String? {
        val matches = search(chunks(context), query, k)
        if (matches.isEmpty()) return null
        return formatAnswer(matches)
    }

    /** Retrieve the top relevant chunks — for feeding into the on-device model for synthesis. */
    fun retrieve(context: Context, query: String, k: Int = 3): List<Chunk> =
        search(chunks(context), query, k).map { it.chunk }

    private fun formatAnswer(matches: List<Match>): String =
        matches.joinToString("\n\n") { m ->
            "— ${m.chunk.text.trim()}\n   (${m.chunk.source}, p.${m.chunk.page})"
        }
}
