package com.minhas.teller.utils

import java.util.Locale

data class CountryTaxConfig(
    val countryName: String,
    val taxName: String, // e.g. "GST", "VAT", "Sales Tax"
    val codeSystemName: String?, // e.g. "HSN Code", "HST Code", "VAT Code", null if none
    val standardRates: List<Double>, // e.g. [0.0, 5.0, 12.0, 18.0, 28.0]
    val defaultRate: Double,
    val currencySymbol: String, // e.g. "₹", "$", "£", "€"
    val currencyCode: String   // ISO 4217, e.g. "INR", "USD", "GBP", "EUR"
)

object TaxConfigManager {
    // ── Curated configs for the main markets the app supports ──────────────────
    // Rates are the current (2025/2026) standard + reduced slabs for each country.
    private val detailedConfigs = mapOf(
        "India" to CountryTaxConfig("India", "GST", "HSN Code", listOf(0.0, 5.0, 18.0, 40.0), 18.0, "₹", "INR"),
        "United States" to CountryTaxConfig("United States", "Sales Tax", null, listOf(0.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0), 6.0, "$", "USD"),
        "United Kingdom" to CountryTaxConfig("United Kingdom", "VAT", "VAT Code", listOf(0.0, 5.0, 20.0), 20.0, "£", "GBP"),
        "Canada" to CountryTaxConfig("Canada", "GST/HST", "HST Code", listOf(0.0, 5.0, 12.0, 13.0, 15.0), 13.0, "$", "CAD"),
        "Australia" to CountryTaxConfig("Australia", "GST", null, listOf(0.0, 10.0), 10.0, "$", "AUD"),
        "Germany" to CountryTaxConfig("Germany", "VAT", "MwSt Code", listOf(0.0, 7.0, 19.0), 19.0, "€", "EUR"),
        "France" to CountryTaxConfig("France", "VAT", "TVA Code", listOf(0.0, 2.1, 5.5, 10.0, 20.0), 20.0, "€", "EUR"),
        "Japan" to CountryTaxConfig("Japan", "Consumption Tax", null, listOf(0.0, 8.0, 10.0), 10.0, "¥", "JPY"),
        "Singapore" to CountryTaxConfig("Singapore", "GST", null, listOf(0.0, 9.0), 9.0, "$", "SGD"),
        "United Arab Emirates" to CountryTaxConfig("United Arab Emirates", "VAT", null, listOf(0.0, 5.0), 5.0, "د.إ", "AED"),
        "Saudi Arabia" to CountryTaxConfig("Saudi Arabia", "VAT", null, listOf(0.0, 15.0), 15.0, "﷼", "SAR"),
        "South Africa" to CountryTaxConfig("South Africa", "VAT", null, listOf(0.0, 15.0), 15.0, "R", "ZAR"),
        "Brazil" to CountryTaxConfig("Brazil", "ICMS/IPI", null, listOf(0.0, 7.0, 12.0, 17.0, 18.0, 25.0), 18.0, "R$", "BRL"),
        "Mexico" to CountryTaxConfig("Mexico", "VAT (IVA)", null, listOf(0.0, 16.0), 16.0, "$", "MXN"),
        "Italy" to CountryTaxConfig("Italy", "VAT (IVA)", "VAT Code", listOf(0.0, 4.0, 5.0, 10.0, 22.0), 22.0, "€", "EUR"),
        "Spain" to CountryTaxConfig("Spain", "VAT (IVA)", "VAT Code", listOf(0.0, 4.0, 10.0, 21.0), 21.0, "€", "EUR"),
        "Netherlands" to CountryTaxConfig("Netherlands", "VAT (BTW)", "BTW Code", listOf(0.0, 9.0, 21.0), 21.0, "€", "EUR"),
        "Sweden" to CountryTaxConfig("Sweden", "VAT (Moms)", "Moms Code", listOf(0.0, 6.0, 12.0, 25.0), 25.0, "kr", "SEK"),
        "New Zealand" to CountryTaxConfig("New Zealand", "GST", null, listOf(0.0, 15.0), 15.0, "$", "NZD"),
        "Switzerland" to CountryTaxConfig("Switzerland", "VAT (MWST)", null, listOf(0.0, 2.6, 3.8, 8.1), 8.1, "CHF", "CHF"),
        "China" to CountryTaxConfig("China", "VAT", null, listOf(0.0, 3.0, 6.0, 9.0, 13.0), 13.0, "¥", "CNY"),
        "Russia" to CountryTaxConfig("Russia", "VAT", null, listOf(0.0, 10.0, 20.0), 20.0, "₽", "RUB"),
        "Turkey" to CountryTaxConfig("Turkey", "VAT (KDV)", null, listOf(0.0, 1.0, 10.0, 20.0), 20.0, "₺", "TRY"),
        "Malaysia" to CountryTaxConfig("Malaysia", "SST", null, listOf(0.0, 5.0, 6.0, 8.0, 10.0), 8.0, "RM", "MYR"),
        "Indonesia" to CountryTaxConfig("Indonesia", "VAT (PPN)", null, listOf(0.0, 11.0), 11.0, "Rp", "IDR"),
        "Philippines" to CountryTaxConfig("Philippines", "VAT", null, listOf(0.0, 12.0), 12.0, "₱", "PHP"),
        "Vietnam" to CountryTaxConfig("Vietnam", "VAT", null, listOf(0.0, 5.0, 10.0), 10.0, "₫", "VND"),
        "Thailand" to CountryTaxConfig("Thailand", "VAT", null, listOf(0.0, 7.0), 7.0, "฿", "THB"),
        "Ireland" to CountryTaxConfig("Ireland", "VAT", "VAT Code", listOf(0.0, 4.8, 9.0, 13.5, 23.0), 23.0, "€", "EUR"),
        "Bangladesh" to CountryTaxConfig("Bangladesh", "VAT", null, listOf(0.0, 5.0, 7.5, 10.0, 15.0), 15.0, "৳", "BDT"),
        "Pakistan" to CountryTaxConfig("Pakistan", "Sales Tax", null, listOf(0.0, 5.0, 18.0), 18.0, "₨", "PKR"),
        "Nepal" to CountryTaxConfig("Nepal", "VAT", null, listOf(0.0, 13.0), 13.0, "रू", "NPR"),
        "Sri Lanka" to CountryTaxConfig("Sri Lanka", "VAT", null, listOf(0.0, 18.0), 18.0, "Rs", "LKR")
    )

    fun getConfigForCountry(countryName: String): CountryTaxConfig {
        return detailedConfigs[countryName] ?: CountryTaxConfig(
            countryName = countryName,
            taxName = "Tax",
            codeSystemName = null,
            standardRates = emptyList(), // Standard rates empty means custom input required
            defaultRate = 0.0,
            currencySymbol = "$",
            currencyCode = "USD"
        )
    }

    /** The curated "main" countries (those with detailed configs), sorted alphabetically. */
    fun getMainCountries(): List<String> = detailedConfigs.keys.sorted()

    fun getAllCountries(): List<String> {
        val systemCountries = Locale.getISOCountries().map {
            // Locale(lang, country) is deprecated; Builder is the non-deprecated equivalent
            // (Locale.of() is Java 19+ and isn't on Android's core library).
            Locale.Builder().setRegion(it).build().displayCountry
        }.filter { it.isNotBlank() }.distinct().sorted()

        // Ensure countries with detailed configs are included first or in alphabetical order, and merge with systemCountries
        val allMerged = (detailedConfigs.keys + systemCountries).distinct().sorted()
        return allMerged
    }

    /** Currency symbol for a country, defaulting to "$" for unknown countries. */
    fun currencyForCountry(countryName: String): String =
        detailedConfigs[countryName]?.currencySymbol ?: "$"

    /**
     * Auto-detected tax rate for an item in a given country, using offline keyword
     * heuristics (no network). HS/HSN *codes* are a global standard, but the *rate*
     * applied to each item category differs by country — this is the per-country rate
     * table that makes item tax work the same way it does for India.
     */
    fun rateForItem(countryName: String, itemName: String?): Double {
        val item = itemName?.lowercase(Locale.getDefault())?.trim().orEmpty()
        val config = getConfigForCountry(countryName)
        if (config.standardRates.isEmpty()) return 0.0

        val isFoodOrBasic = item.contains("rice") || item.contains("wheat") || item.contains("dal") ||
            item.contains("flour") || item.contains("milk") || item.contains("vegetable") ||
            item.contains("fruit") || item.contains("bread") || item.contains("egg") ||
            item.contains("book") || item.contains("medicine") || item.contains("water") ||
            item.contains("banana") || item.contains("apple") || item.contains("orange") ||
            item.contains("mango") || item.contains("grape") || item.contains("potato") ||
            item.contains("onion") || item.contains("tomato") || item.contains("spinach") ||
            item.contains("cauliflower") || item.contains("cabbage") || item.contains("carrot") ||
            item.contains("cucumber") || item.contains("lemon") || item.contains("papaya") ||
            item.contains("pea") || item.contains("bean") || item.contains("lentil") ||
            item.contains("corn") || item.contains("garlic") || item.contains("ginger") ||
            item.contains("chilli") || item.contains("okra") || item.contains("brinjal") ||
            item.contains("pumpkin") || item.contains("watermelon")

        val isReduced = item.contains("sugar") || item.contains("tea") || item.contains("coffee") ||
            item.contains("sweet") || LanguageTranslationUtils.isFivePercentOil(item) || item.contains("transport") ||
            item.contains("fabric") || item.contains("butter") || item.contains("cheese") ||
            item.contains("ghee") || item.contains("fuel") || item.contains("electricity") ||
            item.contains("heating") || item.contains("energy") || item.contains("clothing") ||
            item.contains("cinema") || item.contains("hotel") || item.contains("restaurant")

        val isSinLuxury = item.contains("cigarette") || item.contains("tobacco") || item.contains("pan masala") ||
            item.contains("gutkha") || item.contains("aerated") || item.contains("carbonated") ||
            item.contains("cola") || item.contains("soft drink") || item.contains("alcohol") ||
            item.contains("liquor")

        return when (countryName) {
            "India" -> when {
                isSinLuxury -> 40.0
                isFoodOrBasic -> 0.0
                isReduced -> 5.0
                else -> 18.0
            }
            "United Kingdom" -> when {
                isFoodOrBasic || item.contains("clothing") -> 0.0
                isReduced -> 5.0
                else -> config.defaultRate
            }
            "Canada" -> if (isFoodOrBasic) 0.0 else config.defaultRate
            "Germany" -> if (isFoodOrBasic) 7.0 else config.defaultRate
            "France" -> when {
                isFoodOrBasic -> 5.5
                item.contains("service") || item.contains("transport") -> 10.0
                else -> config.defaultRate
            }
            "Italy" -> when {
                isFoodOrBasic -> 4.0
                item.contains("service") || item.contains("transport") -> 10.0
                else -> config.defaultRate
            }
            "Spain" -> when {
                isFoodOrBasic -> 4.0
                item.contains("service") || item.contains("transport") -> 10.0
                else -> config.defaultRate
            }
            "Australia", "New Zealand" -> if (isFoodOrBasic) 0.0 else config.defaultRate
            "Singapore", "Japan" -> if (isFoodOrBasic) {
                config.standardRates.filter { it > 0.0 }.minOrNull() ?: config.defaultRate
            } else config.defaultRate
            else -> {
                // Generic: zero-rate essentials, reduced for soft categories, else standard.
                when {
                    isFoodOrBasic && config.standardRates.contains(0.0) -> 0.0
                    isReduced -> config.standardRates.filter { it > 0.0 && it < config.defaultRate }
                        .minOrNull() ?: config.defaultRate
                    else -> config.defaultRate
                }
            }
        }
    }
}

val CountryTaxConfig.taxIdName: String
    get() = when (countryName) {
        "India" -> "GSTIN"
        "United Kingdom", "France", "Germany", "Italy", "Spain", "Netherlands", "Sweden", "Switzerland", "Ireland" -> "VAT Reg No"
        "United States" -> "Tax ID (EIN)"
        "Canada" -> "Business Number"
        "Australia" -> "ABN"
        "New Zealand" -> "GST Number"
        else -> "Tax ID / VAT No"
    }
