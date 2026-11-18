package com.minhas.teller.utils

import android.content.Context
import android.net.Uri
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.HsnEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Comprehensive HSN Data Manager - Multi-source HSN code and GST rate retrieval
 * Supports: API fetching, File imports (CSV, JSON), Embedded database, Manual updates
 */
class HSNDataManager private constructor(private val context: Context) {

    private val hsnDao = AppDatabase.getDatabase(context).hsnDao()
    private val prefs = context.getSharedPreferences("HSNDataManager", Context.MODE_PRIVATE)

    fun getApiSources(): List<String> {
        val customUrl = prefs.getString("custom_hsn_sync_url", null)
        if (!customUrl.isNullOrBlank()) {
            return listOf(customUrl) + API_SOURCES
        }
        return API_SOURCES
    }

    companion object {
        @Volatile
        private var instance: HSNDataManager? = null

        fun getInstance(context: Context): HSNDataManager {
            return instance ?: synchronized(this) {
                instance ?: HSNDataManager(context.applicationContext).also { instance = it }
            }
        }

        // Multiple API sources for redundancy
        private val API_SOURCES = listOf(
            "https://raw.githubusercontent.com/QuantumByteStudios/gst-hsn-sac-codes/main/data/hsn_codes.json",
            "https://raw.githubusercontent.com/QuantumByteStudios/gst-hsn-sac-codes/master/data/hsn_codes.json"
        )

        // Official government CSV URLs (if accessible)
        private const val GOVT_HSN_CSV_URL = "https://cbic-gst.gov.in/htdocs-cbec/gst/hsn_codes.csv"
    }

    data class HSNImportResult(
        val success: Boolean,
        val message: String,
        val addedCount: Int = 0,
        val updatedCount: Int = 0,
        val source: String = ""
    )

    /**
     * Import/refresh HSN data.
     *
     * NOTE: this used to try the public GitHub-hosted HSN JSON (see API_SOURCES) before falling
     * back to the bundled dataset. That source still reflects the pre-reform rate schedule
     * (12%/28% slabs that GST 2.0 removed on 22 Sept 2025), so pulling from it risked silently
     * overwriting correct current rates with stale ones. Until a source that tracks GST 2.0
     * rates is available, this always loads the bundled, current-rate dataset (HsnPresetData) —
     * fully offline, no network call, matching the app's offline/free requirement.
     */
    suspend fun importFromMultipleSources(): HSNImportResult = withContext(Dispatchers.Default) {
        val embeddedResult = loadEmbeddedHSNDatabase()
        if (embeddedResult.success) {
            return@withContext embeddedResult.copy(source = "embedded_database")
        }

        return@withContext HSNImportResult(
            success = false,
            message = "Failed to load offline HSN database.",
            source = "none"
        )
    }

    /**
     * Try importing from a specific API source
     */
    private suspend fun tryImportFromAPI(apiBaseUrl: String): HSNImportResult = withContext(Dispatchers.Default) {
        try {
            val url = if (apiBaseUrl.contains("githubusercontent.com")) {
                URL(apiBaseUrl)
            } else {
                URL("$apiBaseUrl/search/hsn?query=all&limit=1000")
            }
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 30000
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = if (apiBaseUrl.contains("githubusercontent.com")) {
                    parseGitHubHSNJson(response)
                } else {
                    parseHSNJsonResponse(response)
                }
                if (parsed.isNotEmpty()) {
                    val (added, updated) = saveHSNEntries(parsed)
                    return@withContext HSNImportResult(
                        success = true,
                        message = "Successfully imported ${parsed.size} HSN codes",
                        addedCount = added,
                        updatedCount = updated
                    )
                }
            }
            return@withContext HSNImportResult(success = false, message = "API returned $responseCode")
        } catch (e: Exception) {
            return@withContext HSNImportResult(success = false, message = e.message ?: "Unknown error")
        }
    }

    /**
     * Import HSN data from file (CSV, JSON, Excel)
     */
    suspend fun importFromFile(uri: Uri): HSNImportResult = withContext(Dispatchers.Default) {
        try {
            val fileName = getFileName(uri)
            val extension = fileName.substringAfterLast(".", "").lowercase()

            val entries = when (extension) {
                "csv" -> parseCSVFile(uri)
                "json" -> parseJSONFile(uri)
                "xlsx", "xls" -> parseExcelFile(uri)
                else -> emptyList()
            }

            if (entries.isEmpty()) {
                return@withContext HSNImportResult(
                    success = false,
                    message = "No valid HSN entries found in file"
                )
            }

            val (added, updated) = saveHSNEntries(entries)
            return@withContext HSNImportResult(
                success = true,
                message = "Imported ${entries.size} HSN codes from $fileName",
                addedCount = added,
                updatedCount = updated
            )
        } catch (e: Exception) {
            return@withContext HSNImportResult(
                success = false,
                message = "Import failed: ${e.message}"
            )
        }
    }

    /**
     * Load embedded default HSN database (comprehensive common codes)
     */
    private suspend fun loadEmbeddedHSNDatabase(): HSNImportResult = withContext(Dispatchers.Default) {
        // Replace the whole directory so the bundled full HSN list is the single source of truth
        // and stale hand-curated entries (or rates from an older dataset) can't linger.
        cachedDirectory = null
        hsnDao.deleteAll()
        val defaultEntries = getDefaultHSNEntries()
        val (added, updated) = saveHSNEntries(defaultEntries)
        return@withContext HSNImportResult(
            success = true,
            message = "Loaded ${defaultEntries.size} default HSN codes",
            addedCount = added,
            updatedCount = updated
        )
    }

    /**
     * Parse CSV file with HSN data
     * Expected format: HSN_CODE,DESCRIPTION,GST_RATE,CATEGORY
     */
    private fun parseCSVFile(uri: Uri): List<HsnEntry> {
        val entries = mutableListOf<HsnEntry>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).useLines { lines ->
                var isFirstLine = true
                lines.forEach { line ->
                    if (isFirstLine) {
                        isFirstLine = false
                        return@forEach // Skip header
                    }
                    val parts = line.split(",").map { it.trim().replace("\"", "") }
                    if (parts.size >= 3) {
                        val hsnCode = parts[0]
                        val description = parts[1]
                        val gstRate = parts[2].toDoubleOrNull() ?: 0.0
                        val category = if (parts.size > 3) parts[3] else "General"

                        entries.add(HsnEntry(
                            hsnCode = hsnCode,
                            description = description,
                            keywords = generateKeywords(description),
                            gstRate = gstRate,
                            category = category
                        ))
                    }
                }
            }
        }
        return entries
    }

    /**
     * Parse JSON file with HSN data
     */
    private fun parseJSONFile(uri: Uri): List<HsnEntry> {
        val entries = mutableListOf<HsnEntry>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val json = stream.bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                entries.add(HsnEntry(
                    hsnCode = obj.optString("hsn_code", obj.optString("hsnCode", "")),
                    description = obj.optString("description", ""),
                    keywords = obj.optString("keywords", generateKeywords(obj.optString("description", ""))),
                    gstRate = obj.optDouble("gst_rate", obj.optDouble("gstRate", 0.0)),
                    category = obj.optString("category", obj.optString("hierarchy", "General"))
                ))
            }
        }
        return entries
    }

    /**
     * Parse Excel file (simplified - requires Apache POI)
     */
    private fun parseExcelFile(uri: Uri): List<HsnEntry> {
        // For now, return empty - Excel parsing requires additional dependencies
        // Can be implemented with Apache POI if needed
        return emptyList()
    }

    /**
     * Parse HSN JSON response from API
     */
    private fun parseHSNJsonResponse(json: String): List<HsnEntry> {
        val entries = mutableListOf<HsnEntry>()
        try {
            val root = JSONObject(json)
            if (root.optBoolean("success", false)) {
                val data = root.optJSONArray("data") ?: return emptyList()
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val matches = item.optJSONArray("matches")
                    matches?.let {
                        for (j in 0 until it.length()) {
                            val match = it.getJSONObject(j)
                            val hsnCode = match.optString("hsn_code", "")
                            if (hsnCode.isNotBlank()) {
                                entries.add(HsnEntry(
                                    hsnCode = hsnCode,
                                    description = match.optString("description", ""),
                                    keywords = generateKeywords(match.optString("description", "")),
                                    gstRate = match.optDouble("gst_rate", 0.0),
                                    category = match.optString("hierarchy", "General")
                                ))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Return empty list on parse error
        }
        return entries
    }

    private fun parseGitHubHSNJson(json: String): List<HsnEntry> {
        val entries = mutableListOf<HsnEntry>()
        try {
            val root = JSONArray(json)
            for (i in 0 until root.length()) {
                val item = root.getJSONObject(i)
                val code = item.optString("code", "")
                if (code.isNotBlank()) {
                    val description = item.optString("description", "")
                    val igst = item.optDouble("igst_rate", 0.0)
                    val section = item.optString("section", "General")
                    
                    entries.add(HsnEntry(
                        hsnCode = code,
                        description = description,
                        keywords = generateKeywords(description),
                        gstRate = igst,
                        category = section.takeIf { it.isNotBlank() && it != "null" } ?: "General"
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return entries
    }

    /**
     * Fix the handful of headings the bundled tariff (hsn_codes.txt) shipped with the wrong
     * GST 2.0 rate (5% where it should be 18% / nil). Applied to every entry for these codes —
     * the full directory and the HsnCommonItems brand aliases — so they always agree.
     */
    private fun correctMisPricedHeading(entry: HsnEntry): HsnEntry = HsnPresetData.correctMisPricedHeading(entry)

    private fun sanitizeHsnEntry(entry: HsnEntry): HsnEntry = HsnPresetData.sanitizeHsnEntry(entry)

    /**
     * Save HSN entries to database
     */
    private suspend fun saveHSNEntries(entries: List<HsnEntry>): Pair<Int, Int> = withContext(Dispatchers.Default) {
        val sanitizedList = entries.map { sanitizeHsnEntry(it) }
        sanitizedList.chunked(500).forEach { chunk ->
            hsnDao.insertAll(chunk)
        }

        // Save last update timestamp
        prefs.edit().putLong("last_hsn_update", System.currentTimeMillis()).apply()

        return@withContext Pair(sanitizedList.size, 0)
    }

    /**
     * Get comprehensive default HSN database (embedded fallback)
     */
    private fun getDefaultHSNEntries(): List<HsnEntry> {
        // Single shared dataset — see HsnPresetData for the canonical, current-rate offline list.
        return HsnPresetData.getEntries(context)
    }

    /**
     * Generate search keywords from description
     */
    private fun generateKeywords(description: String): String {
        return description.lowercase()
            .replace(",", " ")
            .replace(";", " ")
            .replace(".", " ")
            .replace("(", " ")
            .replace(")", " ")
            .split(" ")
            .filter { it.length > 2 }
            .distinct()
            .take(10)
            .joinToString(" ")
    }

    /**
     * Get file name from URI
     */
    private fun getFileName(uri: Uri): String {
        return uri.lastPathSegment ?: "unknown"
    }

    /**
     * Check if HSN data needs update (older than 30 days)
     */
    fun isUpdateNeeded(): Boolean {
        val lastUpdate = prefs.getLong("last_hsn_update", 0)
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() - lastUpdate > thirtyDaysInMillis
    }

    /**
     * Get total HSN count in database
     */
    suspend fun getHSNCount(): Int = withContext(Dispatchers.Default) {
        hsnDao.count()
    }

    /**
     * Search HSN codes by keyword
     */
    suspend fun searchHSN(query: String): List<HsnEntry> = withContext(Dispatchers.Default) {
        hsnDao.search("%$query%")
    }

    suspend fun ensureDefaultHSNLoaded() = withContext(Dispatchers.Default) {
        val defaultEntries = getDefaultHSNEntries()
        val storedVersion = prefs.getInt("hsn_data_version", 0)
        // Row-count alone can't detect "same number of rows, some rates changed" — a device that
        // already has >= the current preset count would otherwise never re-run saveHSNEntries and
        // would keep serving stale rates forever (this was the actual bug behind e.g. wash basin
        // still showing 28% after the source data was already fixed to 18%). Re-run whenever the
        // bundled dataset's version has moved on, regardless of row count.
        if (hsnDao.count() < defaultEntries.size || storedVersion < HsnPresetData.DATA_VERSION) {
            loadEmbeddedHSNDatabase()
            prefs.edit().putInt("hsn_data_version", HsnPresetData.DATA_VERSION).apply()
        }
    }

    /**
     * Search HSN locally, and fallback to online search if not found
     */
    suspend fun lookupAndSaveHSNOnline(query: String): HsnEntry? = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext null
        
        ensureDefaultHSNLoaded()
        
        val cleanQuery = query.trim().lowercase()
        
        // 1. Exact or substring match locally
        val localResults = hsnDao.search("%$cleanQuery%")
        if (localResults.isNotEmpty()) {
            return@withContext localResults.first()
        }
        
        // 2. Split query into keywords and search locally
        val stopWords = setOf(
            "fresh", "organic", "local", "super", "large", "small", "quality", "premium", 
            "brand", "heavy", "light", "kg", "g", "ml", "l", "pack", "pcs", "piece", 
            "box", "bag", "sweet", "hot", "cold", "red", "green", "blue", "yellow", 
            "white", "black", "good", "best", "and", "with", "for", "in", "of", "the", "a", "an"
        )
        
        val keywords = cleanQuery.split("[\\s_\\-\\+\\/\\*]+".toRegex())
            .map { it.replace("[^a-z0-9]".toRegex(), "") }
            .filter { it.length > 2 && it !in stopWords }
        
        for (keyword in keywords) {
            val keywordMatches = hsnDao.search("%$keyword%")
            if (keywordMatches.isNotEmpty()) {
                return@withContext keywordMatches.first()
            }
        }
        
        // Try singular/plural forms of keywords
        for (keyword in keywords) {
            val singular = if (keyword.endsWith("es")) {
                keyword.substring(0, keyword.length - 2)
            } else if (keyword.endsWith("s") && !keyword.endsWith("ss")) {
                keyword.substring(0, keyword.length - 1)
            } else {
                keyword
            }
            if (singular != keyword && singular.length > 2) {
                val singularMatches = hsnDao.search("%$singular%")
                if (singularMatches.isNotEmpty()) {
                    return@withContext singularMatches.first()
                }
            }
        }
        
        // No local/offline match found. Return fuzzy suggestions instead.
        return@withContext null
    }

    /**
     * Fuzzy search: finds close matches when the user misspells or reorders words.
     * Uses token-based matching + edit distance for "Did you mean?" suggestions.
     */
    @Volatile
    private var cachedDirectory: List<HsnEntry>? = null

    suspend fun getAllEntriesCached(): List<HsnEntry> = withContext(Dispatchers.Default) {
        ensureDefaultHSNLoaded()
        val count = hsnDao.count()
        val existing = cachedDirectory
        if (existing != null && existing.size == count) return@withContext existing
        val loaded = hsnDao.getAll()
        cachedDirectory = loaded
        loaded
    }

    /**
     * Unified interactive fuzzy + combinatorial HSN lookup returning both confidence
     * ("Did you mean this?" vs "Is it either one of these?") and ranked suggestions.
     */
    suspend fun searchFuzzyWithConfidence(
        query: String,
        maxResults: Int = 5
    ): HsnFuzzyMatcher.LookupResult = withContext(Dispatchers.Default) {
        if (query.isBlank()) {
            return@withContext HsnFuzzyMatcher.LookupResult(
                HsnFuzzyMatcher.MatchConfidence.NONE,
                emptyList()
            )
        }
        val pool = getAllEntriesCached()
        HsnFuzzyMatcher.lookup(query, pool, maxResults)
    }

    /**
     * Fuzzy search: finds close matches when the user misspells or reorders words.
     * Delegates to [HsnFuzzyMatcher] across the entire cached HSN & SAC directory.
     */
    suspend fun searchFuzzy(query: String, maxResults: Int = 5): List<HsnEntry> = withContext(Dispatchers.Default) {
        return@withContext searchFuzzyWithConfidence(query, maxResults).suggestions.map { it.entry }
    }

    /**
     * Scores an HSN entry against the user's query. Higher score = better match.
     * Handles: misspellings (edit distance), word reordering, partial matches, and
     * keyword hits across description, keywords field, and category.
     */
    private fun scoreEntry(entry: HsnEntry, fullQuery: String, queryTokens: List<String>): Int {
        var score = 0
        val descLower = entry.description.lowercase()
        val keywordsLower = entry.keywords.lowercase()
        val categoryLower = entry.category.lowercase()
        val searchText = "$descLower $keywordsLower $categoryLower"

        // Exact full-query match in any field (highest priority)
        if (descLower == fullQuery || keywordsLower == fullQuery) {
            return 10000
        }

        // Full query as a whole word/phrase in description (word-bounded, not a raw
        // substring — "ande" must not match inside "coriANDEr").
        if (containsWholeWord(descLower, fullQuery)) {
            score += 5000
        }

        // Full query as a whole word/phrase in keywords
        if (containsWholeWord(keywordsLower, fullQuery)) {
            score += 3000
        }

        // Token-by-token matching
        for (token in queryTokens) {
            if (token.length < 2) continue

            // Exact token match in description
            if (containsWholeWord(descLower, token)) {
                score += 500
                // Bonus if the token appears near the start of description
                val idx = descLower.indexOf(token)
                if (idx >= 0 && idx < 20) score += 200
            }

            // Exact token match in keywords
            if (containsWholeWord(keywordsLower, token)) {
                score += 300
            }

            // Exact token match in category
            if (containsWholeWord(categoryLower, token)) {
                score += 100
            }

            // Fuzzy match: token is within edit distance 2 of any word in search text
            val searchWords = searchText.split(Regex("[\\s,]+"))
            for (word in searchWords) {
                if (word.length < 2) continue
                val dist = editDistance(token, word)
                when {
                    dist == 0 -> { /* already handled above */ }
                    dist == 1 && token.length >= 4 -> score += 200  // "aple" → "apple"
                    dist == 2 && token.length >= 5 -> score += 100  // "tamatar" → "tomato"
                    dist == 1 && token.length == 3 -> score += 80
                }
            }

            // Partial token: query token is a prefix of a search word
            if (searchWords.any { it.startsWith(token) && it.length > token.length }) {
                score += 150
            }
        }

        // Multi-token bonus: if ALL query tokens appear somewhere in the search text
        if (queryTokens.size >= 2 && queryTokens.all { t -> containsWholeWord(searchText, t) }) {
            score += 1000
        }

        return score
    }

    /**
     * Whole-word/phrase containment: true only if [needle] appears in [haystack]
     * without a letter or digit immediately before/after it. Plain `String.contains`
     * matches inside unrelated words too — e.g. the Hinglish token "ande" (eggs)
     * is a literal substring of "coriander", which previously made egg queries
     * incorrectly score highest against the coriander HSN entry.
     */
    private fun containsWholeWord(haystack: String, needle: String): Boolean {
        if (needle.isEmpty()) return false
        val pattern = "(?<![\\p{L}\\p{N}])${Regex.escape(needle)}(?![\\p{L}\\p{N}])"
        return Regex(pattern).containsMatchIn(haystack)
    }

    /**
     * Levenshtein edit distance between two strings.
     * Lower = more similar. 0 = identical.
     */
    private fun editDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // deletion
                    dp[i][j - 1] + 1,       // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[m][n]
    }

    suspend fun getCategories(): List<String> = withContext(Dispatchers.Default) {
        ensureDefaultHSNLoaded()
        hsnDao.getCategories()
    }

    suspend fun getEntriesByCategory(category: String): List<HsnEntry> = withContext(Dispatchers.Default) {
        ensureDefaultHSNLoaded()
        hsnDao.getEntriesByCategory(category)
    }
}
