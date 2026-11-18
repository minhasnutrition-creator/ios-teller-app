package com.minhas.teller.data.repositories

import android.content.Context
import android.content.SharedPreferences
import com.minhas.teller.data.dao.HsnDao
import com.minhas.teller.data.entities.HsnEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for syncing HSN codes and GST rates. Offline-only — see syncHSNData() for why the
 * old FastGST network API path was removed.
 */
class HSNSyncRepository(
    private val context: Context,
    private val hsnDao: HsnDao,
    private val stockItemDao: com.minhas.teller.data.dao.StockItemDao? = null
) {
    companion object {
        private const val PREFS_NAME = "HSNSyncPrefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_HSN_COUNT = "hsn_count"
        private const val KEY_AUTO_UPDATE = "auto_update_enabled"
        
        // Sync every 30 days
        private const val SYNC_INTERVAL_DAYS = 30
        const val CONFIDENT_MIN_SCORE = 1_000

        @JvmStatic
        fun isLeadingSubject(desc: String, normItem: String): Boolean {
            val d = desc.trim().lowercase()
            val variants = listOf(normItem, "${normItem}s", "${normItem}es")
            for (v in variants) {
                if (d == v) return true
                val prefixes = listOf("$v,", "$v -", "$v-", "$v/", "$v /", "$v;", "$v (", "$v:", "$v &")
                if (prefixes.any { d.startsWith(it) }) return true
                if (d.startsWith("$v and ") || d.startsWith("$v or ") || d.startsWith("$v & ") || d.startsWith("$v &") || d.startsWith("$v / ")) return true
            }
            return false
        }

        @JvmStatic
        fun matchesPlural(a: String, b: String): Boolean {
            if (a.equals(b, ignoreCase = true)) return true
            val al = a.lowercase()
            val bl = b.lowercase()
            return al == "${bl}s" || al == "${bl}es" ||
                   bl == "${al}s" || bl == "${al}es" ||
                   (al.endsWith("ies") && bl == al.dropLast(3) + "y") ||
                   (bl.endsWith("ies") && al == bl.dropLast(3) + "y")
        }

        /** Pure scoring for one HSN entry against a lowercased item name. Testable in JVM unit tests. */
        @JvmStatic
        fun scoreHsnMatch(itemNameLower: String, entry: HsnEntry): Int {
            val desc = entry.description.lowercase()
            var score = 0
            val normItem = itemNameLower.replace(Regex("[-_]"), " ").trim()

            if (desc == itemNameLower || desc == normItem || matchesPlural(desc, normItem)) {
                score += 100_000
            } else if (isLeadingSubject(desc, normItem)) {
                score += 30_000
            } else if (wholeWordContains(desc, itemNameLower) || wholeWordContains(desc, normItem)) {
                // Full name appears as a phrase, e.g. "engine oil" inside "engine oil (internal ...)".
                score += 12_000
            } else {
                normItem.split(Regex("\\s+"))
                    .filter { it.length > 2 }
                    .forEach { w -> if (wholeWordContains(desc, w)) score += 1_000 }
            }

            val keywords = entry.keywords.lowercase().split(",").map { it.trim() }.filter { it.isNotBlank() }
            var exactKwMatch = false
            var bestSubKwScore = 0
            var additionalSubKwScore = 0
            var bestSuperKwScore = 0

            for (kw in keywords) {
                val normKw = kw.replace(Regex("[-_]"), " ")
                when {
                    itemNameLower == kw || normItem == normKw || matchesPlural(itemNameLower, kw) || matchesPlural(normItem, normKw) -> {
                        exactKwMatch = true
                    }
                    wholeWordContains(itemNameLower, kw) || wholeWordContains(normItem, normKw) -> {
                        // User item contains keyword e.g. item="Dell Laptop 15", kw="laptop"
                        val s = 8_000 + normKw.length * 30
                        if (s > bestSubKwScore) {
                            additionalSubKwScore += bestSubKwScore / 4
                            bestSubKwScore = s
                        } else {
                            additionalSubKwScore += s / 4
                        }
                    }
                    wholeWordContains(kw, itemNameLower) || wholeWordContains(normKw, normItem) -> {
                        // Keyword contains user item e.g. item="laptop", kw="laptop charger" (derivative/accessory)
                        val s = 1_500 + normKw.length * 10
                        if (s > bestSuperKwScore) bestSuperKwScore = s
                    }
                    itemNameLower.contains(kw) || normItem.contains(normKw) -> {
                        val s = normKw.length * 5
                        if (s > bestSubKwScore) bestSubKwScore = maxOf(bestSubKwScore, s)
                    }
                    kw.contains(itemNameLower) || normKw.contains(normItem) -> {
                        val s = normKw.length * 5
                        if (s > bestSuperKwScore) bestSuperKwScore = maxOf(bestSuperKwScore, s)
                    }
                }
            }

            if (exactKwMatch) {
                score += 25_000
            } else {
                score += bestSubKwScore + minOf(additionalSubKwScore, 6_000)
                score += minOf(bestSuperKwScore, 2_000)
            }

            if (entry.category.isNotBlank()) {
                val catLower = entry.category.lowercase()
                if (catLower == itemNameLower || catLower == normItem || matchesPlural(catLower, normItem)) {
                    score += 5_000
                } else if (wholeWordContains(itemNameLower, catLower) || wholeWordContains(normItem, catLower) ||
                           wholeWordContains(catLower, itemNameLower) || wholeWordContains(catLower, normItem)) {
                    score += 2_000
                }
            }

            // Prefer the more specific (longer) code — an 8-digit tariff item beats a 4-digit heading.
            // ONLY apply tie-breaker if there was an actual keyword/description match!
            if (score > 0) {
                score += entry.hsnCode.length
            }

            return score
        }

        /** True if [phrase] appears in [text] bounded by non-alphanumeric characters. */
        @JvmStatic
        fun wholeWordContains(text: String, phrase: String): Boolean {
            if (phrase.isEmpty() || text.isEmpty()) return false
            if (checkSinglePhrase(text, phrase)) return true
            if (phrase.length > 3 && (phrase.endsWith("es") || phrase.endsWith("ES"))) {
                if (checkSinglePhrase(text, phrase.dropLast(2))) return true
            }
            if (phrase.length > 3 && (phrase.endsWith('s') || phrase.endsWith('S'))) {
                if (checkSinglePhrase(text, phrase.dropLast(1))) return true
            }
            return false
        }

        private fun checkSinglePhrase(text: String, phrase: String): Boolean {
            var startIndex = 0
            val pLen = phrase.length
            while (startIndex <= text.length - pLen) {
                val idx = text.indexOf(phrase, startIndex, ignoreCase = true)
                if (idx < 0) break
                val beforeOk = idx == 0 || !text[idx - 1].isLetterOrDigit()
                val after = idx + pLen
                val atEndOrNonAlnum = after >= text.length || !text[after].isLetterOrDigit()
                val pluralS = after < text.length &&
                        (text[after] == 's' || text[after] == 'S') &&
                        (after + 1 >= text.length || !text[after + 1].isLetterOrDigit())
                val pluralEs = after + 1 < text.length &&
                        text.substring(after, after + 2).equals("es", ignoreCase = true) &&
                        (after + 2 >= text.length || !text[after + 2].isLetterOrDigit())
                if (beforeOk && (atEndOrNonAlnum || pluralS || pluralEs)) return true
                startIndex = idx + 1
            }
            return false
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class SyncResult(
        val success: Boolean,
        val message: String,
        val addedCount: Int = 0,
        val updatedCount: Int = 0,
        val itemsRefreshed: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class HSNStats(
        val totalCount: Int,
        val lastSyncTimestamp: Long,
        val isAutoUpdateEnabled: Boolean
    )
    
    /**
     * Get current HSN stats
     */
    suspend fun getStats(): HSNStats {
        val count = hsnDao.count()
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0)
        val autoUpdate = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        
        return HSNStats(
            totalCount = count,
            lastSyncTimestamp = lastSync,
            isAutoUpdateEnabled = autoUpdate
        )
    }
    
    /**
     * Sync HSN data.
     *
     * NOTE: this used to call out to the FastGST network API (api.taxlookup.fastgst.in, a paid
     * third-party service requiring an API key) before falling back to the offline presets on
     * failure. Two problems with that: it violated the app's offline/free requirement, and —
     * worse — when the API call *succeeded* it inserted whatever rates that external service
     * returned with no sanitisation, which could silently overwrite correct current-GST-2.0 rates
     * in the local DB with stale ones (the API's dataset was not tracking the Sep 2025 / Feb 2026
     * rate changes). That was a real root cause of "correct offline rate, wrong rate after Update
     * HSN". This now always goes through the same offline, sanitised dataset used everywhere else
     * (see HsnPresetData / HSNDataManager) so there's a single source of truth.
     */
    fun syncHSNData(): Flow<SyncResult> = flow {
        emit(SyncResult(success = true, message = "Starting sync...", addedCount = 0))
        val fallbackResult = fallbackSync("Offline HSN database (no network sync — see HsnPresetData)")
        emit(fallbackResult)
    }.flowOn(Dispatchers.Default)

    private suspend fun fallbackSync(note: String): SyncResult {
        var addedCount = 0
        var updatedCount = 0

        // Key by hsnCode + description, not hsnCode alone — HsnPresetData intentionally has many
        // entries that share an hsnCode (e.g. several "8516" appliance entries, several "0709"
        // fresh-vegetable entries) with different descriptions/rates. Keying by code alone made
        // every preset row with a shared code collide into one existingCodes entry and silently
        // skip updating (or update the wrong row for) the others.
        val existingCodes = hsnDao.getAll().associateBy { "${it.hsnCode}_${it.description.lowercase()}" }
        val entriesToInsert = mutableListOf<HsnEntry>()

        val presets = getOfflinePresets()
        for (preset in presets) {
            val key = "${preset.hsnCode}_${preset.description.lowercase()}"
            val existing = existingCodes[key]
            if (existing != null) {
                if (existing.gstRate != preset.gstRate || existing.description != preset.description) {
                    updatedCount++
                    entriesToInsert.add(preset.copy(id = existing.id))
                }
            } else {
                addedCount++
                entriesToInsert.add(preset)
            }
        }

        if (entriesToInsert.isNotEmpty()) {
            hsnDao.insertAll(entriesToInsert)
        }

        // Save sync timestamp
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
        prefs.edit().putInt(KEY_HSN_COUNT, hsnDao.count()).apply()

        // Refresh tax rates on existing stock items (scoped to the active company)
        val itemsRefreshed = com.minhas.teller.SessionManager.currentCompanyId.value
            ?.let { refreshStockItemTaxRates(it) } ?: 0

        return SyncResult(
            success = true,
            message = "Sync completed! $note",
            addedCount = addedCount,
            updatedCount = updatedCount,
            itemsRefreshed = itemsRefreshed
        )
    }

    private fun getOfflinePresets(): List<HsnEntry> {
        // Single shared dataset — see HsnPresetData for the canonical, current-rate offline list.
        // (Previously this repo kept its own smaller preset list which lacked common items like
        // fruits/vegetables and disagreed with the rates in HSNDataManager's list — that split
        // was the root cause of items like "apple" failing to auto-populate.)
        return com.minhas.teller.utils.HsnPresetData.getEntries(context)
    }
    
    /**
     * Pre-populate HSN directory with offline presets if empty
     */
    suspend fun prePopulateOfflinePresets() = kotlinx.coroutines.withContext(Dispatchers.Default) {
        val count = hsnDao.count()
        if (count == 0) {
            val presets = getOfflinePresets()
            hsnDao.insertAll(presets)
            prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
            prefs.edit().putInt(KEY_HSN_COUNT, presets.size).apply()
        }
    }
    
    /**
     * Search HSN by code or description
     */
    suspend fun searchHSN(query: String): List<HsnEntry> {
        val searchQuery = "%$query%"
        return hsnDao.search(searchQuery)
    }
    
    /**
     * Get HSN by exact code
     */
    suspend fun getHSNByCode(code: String): HsnEntry? {
        return hsnDao.getByCode(code)
    }
    
    /**
     * Find best matching HSN for a stock item name
     */
    suspend fun findBestMatchingHSN(itemName: String): HsnEntry? {
        val nameLower = itemName.trim().lowercase()
        if (nameLower.isBlank()) return null

        val allEntries = hsnDao.getAll()

        var bestMatch: HsnEntry? = null
        var bestScore = 0

        for (entry in allEntries) {
            val score = scoreHsnMatch(nameLower, entry)
            if (score > bestScore) {
                bestScore = score
                bestMatch = entry
            }
        }

        val itemWords = nameLower.split(Regex("\\s+")).filter { it.length >= 2 }
        val exactWinner = if (bestScore >= 24_000 || (itemWords.size <= 1 && bestScore >= CONFIDENT_MIN_SCORE)) {
            bestMatch
        } else null
        if (exactWinner != null) return exactWinner

        // For misspelled or reordered multi-word names, let HsnFuzzyMatcher find full-phrase / typo-repaired winners
        // before falling back to partial single-word exact hits.
        val fuzzyWinner = com.minhas.teller.utils.HsnFuzzyMatcher.findConfidentAutoMatch(itemName, null, allEntries)
        return fuzzyWinner ?: if (bestScore >= CONFIDENT_MIN_SCORE) bestMatch else null
    }
    
    /**
     * Check if sync is needed (30 days since last sync)
     */
    fun isSyncNeeded(): Boolean {
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0)
        if (lastSync == 0L) return true
        
        val daysSinceSync = (System.currentTimeMillis() - lastSync) / (1000 * 60 * 60 * 24)
        return daysSinceSync >= SYNC_INTERVAL_DAYS
    }
    
    /**
     * Get formatted last sync date
     */
    fun getLastSyncDate(): String {
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0)
        if (lastSync == 0L) return "Never"
        
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return sdf.format(Date(lastSync))
    }
    
    /**
     * Set auto-update enabled
     */
    fun setAutoUpdate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }
    
    /**
     * Clear all HSN data
     */
    suspend fun clearAllData() {
        hsnDao.deleteAll()
        prefs.edit().remove(KEY_LAST_SYNC).apply()
        prefs.edit().remove(KEY_HSN_COUNT).apply()
    }
    
    /**
     * Refresh tax rates on all stock items after HSN update
     * This updates existing items with new GST rates from the HSN database
     */
    suspend fun refreshStockItemTaxRates(companyId: Int): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val dao = stockItemDao ?: return@withContext 0

        var refreshedCount = 0

        try {
            val allStockItems = dao.getAllStockItems(companyId)
            val allHSN = hsnDao.getAll()
            
            for (item in allStockItems) {
                if (item.hsnCode.isNullOrBlank()) {
                    val matchedHSN = findBestMatchingHSN(item.name)
                    if (matchedHSN != null) {
                        dao.update(item.copy(
                            hsnCode = matchedHSN.hsnCode,
                            gstRate = matchedHSN.gstRate,
                            cgstRate = matchedHSN.gstRate / 2,
                            sgstRate = matchedHSN.gstRate / 2,
                            igstRate = matchedHSN.gstRate,
                            hsnAutoDetected = true
                        ))
                        refreshedCount++
                    }
                } else {
                    val hsnEntry = allHSN.find { it.hsnCode == item.hsnCode }
                    if (hsnEntry != null && hsnEntry.gstRate != item.gstRate) {
                        dao.update(item.copy(
                            gstRate = hsnEntry.gstRate,
                            cgstRate = hsnEntry.gstRate / 2,
                            sgstRate = hsnEntry.gstRate / 2,
                            igstRate = hsnEntry.gstRate
                        ))
                        refreshedCount++
                    }
                }
            }
            
            android.util.Log.d("HSNSync", "Refreshed tax rates for $refreshedCount items")
            
        } catch (e: Exception) {
            android.util.Log.e("HSNSync", "Error refreshing stock item tax rates: ${e.message}")
        }
        
        refreshedCount
    }
    
    /**
     * Generate keywords from description for better search
     */
    private fun generateKeywords(description: String): String {
        val commonWords = setOf("and", "or", "the", "of", "in", "on", "at", "to", "for", "with", "by")
        val words = description.split(" ", ",", "-", "(", ")")
            .map { it.trim().lowercase() }
            .filter { it.length > 2 && !commonWords.contains(it) }
            .distinct()
            .take(5)
        
        return words.joinToString(", ")
    }
}
