package com.minhas.teller.utils

import android.content.Context
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.HsnEntry
import com.minhas.teller.data.repositories.HSNSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Smart Tax Service - Automatically applies HSN codes and GST rates
 * when Smart Tax Estimation is enabled
 */
class SmartTaxService(private val context: Context) {
    
    private val hsnRepository: HSNSyncRepository
    private val hindiRepository: com.minhas.teller.data.repositories.HindiMappingRepository
    
    init {
        val database = AppDatabase.getDatabase(context)
        val hsnDao = database.hsnDao()
        hsnRepository = HSNSyncRepository(context, hsnDao)
        
        // Initialize Hindi mappings
        val hindiDao = database.hindiMappingDao()
        hindiRepository = com.minhas.teller.data.repositories.HindiMappingRepository(hindiDao)
    }
    
    /**
     * Initialize Hindi database on first use
     */
    suspend fun initializeHindiDatabase() {
        hindiRepository.initializeDatabase()
    }
    
    data class TaxInfo(
        val hsnCode: String,
        val description: String,
        val gstRate: Double,
        val cgst: Double,
        val sgst: Double,
        val igst: Double,
        val isExempted: Boolean
    )

    data class TaxSuggestion(
        val taxInfo: TaxInfo,
        val matchedLabel: String,
        val isExactMatch: Boolean,
        val usedFuzzyRepair: Boolean,
        val wasReordered: Boolean
    )

    data class TaxLookupResult(
        val confidence: HsnFuzzyMatcher.MatchConfidence,
        val suggestions: List<TaxSuggestion>
    )
    
    companion object {
        @Volatile
        private var instance: SmartTaxService? = null
        
        fun getInstance(context: Context): SmartTaxService {
            return instance ?: synchronized(this) {
                instance ?: SmartTaxService(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Find best matching HSN and tax info for an item name
     * Supports Hindi/Hinglish item names - automatically translates to English
     */
    suspend fun getTaxInfoForItem(itemName: String): TaxInfo? = withContext(Dispatchers.IO) {
        // Auto HSN/GST estimation is a per-company feature gated by two settings:
        // the Tax System master switch and Smart Tax Estimation. If either is off
        // for the active company, skip auto-assignment entirely. Manual HSN lookup
        // via searchHSN()/getHSNByCode() still works independently of this.
        if (!DashboardPrefs.isTaxSystemEnabled(context) || !DashboardPrefs.isSmartTaxEstimationEnabled(context)) {
            return@withContext null
        }
        // Ensure the latest offline directory (including HsnCommonItems brand aliases) is loaded
        // before matching — this also re-syncs when HsnPresetData.DATA_VERSION is bumped.
        HSNDataManager.getInstance(context).ensureDefaultHSNLoaded()

        // Initialize Hindi database if needed
        hindiRepository.initializeDatabase()
        
        // Try to translate Hindi/Hinglish name to English first
        var searchName = itemName
        val hindiMapping = hindiRepository.translateToEnglish(itemName)
        if (hindiMapping != null) {
            searchName = hindiMapping
            android.util.Log.d("SmartTax", "Translated '$itemName' -> '$searchName'")
        }
        
        // Find best matching HSN entry using translated name
        var hsnEntry = hsnRepository.findBestMatchingHSN(searchName)
        if (hsnEntry == null) {
            // Fallback to online lookup which includes Gemini classification
            hsnEntry = HSNDataManager.getInstance(context).lookupAndSaveHSNOnline(searchName)
        }
        
        val countryName = DashboardPrefs.getTaxCountry(context)
        val isIndia = countryName.equals("India", true)

        hsnEntry?.let {
            // India uses the HSN's own GST rate; other countries reuse the same global
            // HS code but apply their own VAT/GST/sales-tax rate for that item category.
            val rate = if (isIndia) it.gstRate else TaxConfigManager.rateForItem(countryName, searchName)
            val cgst = if (isIndia) rate / 2 else 0.0
            val sgst = if (isIndia) rate / 2 else 0.0
            val igst = rate // full rate in igst represents the country standard VAT/GST/Sales tax

            TaxInfo(
                hsnCode = it.hsnCode,
                description = it.description,
                gstRate = rate,
                cgst = cgst,
                sgst = sgst,
                igst = igst,
                isExempted = rate == 0.0
            )
        }
    }
    
    /**
     * Calculate tax amount for given amount and HSN code
     */
    suspend fun calculateTax(itemName: String, amount: Double): Pair<Double, TaxInfo?> = withContext(Dispatchers.IO) {
        val taxInfo = getTaxInfoForItem(itemName)
        
        if (taxInfo != null && !taxInfo.isExempted) {
            val taxAmount = (amount * taxInfo.gstRate) / 100
            Pair(taxAmount, taxInfo)
        } else {
            Pair(0.0, taxInfo)
        }
    }
    
    private fun toTaxInfo(
        entry: HsnEntry,
        queryName: String,
        isIndia: Boolean,
        countryName: String
    ): TaxInfo {
        val rate = if (isIndia) entry.gstRate else TaxConfigManager.rateForItem(countryName, queryName)
        val cgst = if (isIndia) rate / 2 else 0.0
        val sgst = if (isIndia) rate / 2 else 0.0
        return TaxInfo(
            hsnCode = entry.hsnCode,
            description = entry.description,
            gstRate = rate,
            cgst = cgst,
            sgst = sgst,
            igst = rate,
            isExempted = rate == 0.0
        )
    }

    /**
     * Explicit interactive HSN lookup for Item Creation screens ("Find HSN Code & GST from name").
     * Returns whether there is 1 exact winner, 1 closest fuzzy/reordered match ("Did you mean this?"),
     * 2+ competing closest options ("Is it either one of these?"), or none.
     */
    suspend fun lookupHsnSuggestions(query: String, maxResults: Int = 5): TaxLookupResult = withContext(Dispatchers.IO) {
        val raw = query.trim()
        if (raw.isEmpty()) {
            return@withContext TaxLookupResult(HsnFuzzyMatcher.MatchConfidence.NONE, emptyList())
        }
        val hindiEnglish = hindiRepository.translateToEnglish(raw)
        val searchQuery = hindiEnglish ?: raw

        val result = HSNDataManager.getInstance(context).searchFuzzyWithConfidence(searchQuery, maxResults)
        val countryName = DashboardPrefs.getTaxCountry(context)
        val isIndia = countryName.equals("India", true)

        TaxLookupResult(
            confidence = result.confidence,
            suggestions = result.suggestions.map { s ->
                TaxSuggestion(
                    taxInfo = toTaxInfo(s.entry, searchQuery, isIndia, countryName),
                    matchedLabel = s.matchedLabel,
                    isExactMatch = s.isExactMatch,
                    usedFuzzyRepair = s.usedFuzzyRepair,
                    wasReordered = s.wasReordered
                )
            }
        )
    }

    /**
     * Find fuzzy-matched suggestions when no exact HSN match is found.
     * Uses edit-distance and token-based scoring — ideal for "Did you mean?" prompts
     * when a user types a misspelled or reordered item name.
     */
    suspend fun searchSuggestions(query: String, maxResults: Int = 5): List<TaxInfo> = withContext(Dispatchers.IO) {
        lookupHsnSuggestions(query, maxResults).suggestions.map { it.taxInfo }
    }

    /**
     * Search HSN by keyword for manual selection
     */
    suspend fun searchHSN(query: String): List<HsnEntry> = withContext(Dispatchers.IO) {
        if (!hasData()) {
            hsnRepository.prePopulateOfflinePresets()
        }
        hsnRepository.searchHSN(query)
    }
    
    /**
     * Get HSN by exact code
     */
    suspend fun getHSNByCode(code: String): HsnEntry? = withContext(Dispatchers.IO) {
        if (!hasData()) {
            hsnRepository.prePopulateOfflinePresets()
        }
        hsnRepository.getHSNByCode(code)
    }
    
    /**
     * Check if HSN database has data
     */
    suspend fun hasData(): Boolean = withContext(Dispatchers.IO) {
        hsnRepository.getStats().totalCount > 0
    }
    
    /**
     * Get tax breakdown for invoice display
     */
    fun getTaxBreakdown(taxInfo: TaxInfo, taxableAmount: Double): Map<String, Double> {
        val countryName = DashboardPrefs.getTaxCountry(context)
        val isIndia = countryName.equals("India", true)
        return if (taxInfo.isExempted) {
            mapOf(
                "Taxable Amount" to taxableAmount,
                "CGST (0%)" to 0.0,
                "SGST (0%)" to 0.0,
                "IGST (0%)" to 0.0,
                "Total Tax" to 0.0,
                "Grand Total" to taxableAmount
            )
        } else {
            val cgstAmount = (taxableAmount * taxInfo.cgst) / 100
            val sgstAmount = (taxableAmount * taxInfo.sgst) / 100
            val igstAmount = (taxableAmount * taxInfo.igst) / 100
            val totalTax = if (isIndia) (cgstAmount + sgstAmount) else igstAmount
            
            mapOf(
                "Taxable Amount" to taxableAmount,
                "CGST (${taxInfo.cgst}%)" to cgstAmount,
                "SGST (${taxInfo.sgst}%)" to sgstAmount,
                "IGST (${taxInfo.igst}%)" to igstAmount,
                "Total Tax" to totalTax,
                "Grand Total" to (taxableAmount + totalTax)
            )
        }
    }
}
