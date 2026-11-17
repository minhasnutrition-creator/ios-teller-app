package com.minhas.teller.utils

/**
 * GST state/UT codes (2-digit) ↔ names, as used in GSTR-1 / GSTR-3B (place of supply, `pos`,
 * `ctin` prefixes, intra/inter-state classification).
 *
 * Pure data — no Android dependencies — so the return builders can use it directly.
 */
object GstStateCodes {

    /** Ordered by code. 26 covers the merged DNH & Daman & Diu; 38 is Ladakh. */
    val stateNameByCode: Map<String, String> = linkedMapOf(
        "01" to "Jammu & Kashmir",
        "02" to "Himachal Pradesh",
        "03" to "Punjab",
        "04" to "Chandigarh",
        "05" to "Uttarakhand",
        "06" to "Haryana",
        "07" to "Delhi",
        "08" to "Rajasthan",
        "09" to "Uttar Pradesh",
        "10" to "Bihar",
        "11" to "Sikkim",
        "12" to "Arunachal Pradesh",
        "13" to "Nagaland",
        "14" to "Manipur",
        "15" to "Mizoram",
        "16" to "Tripura",
        "17" to "Meghalaya",
        "18" to "Assam",
        "19" to "West Bengal",
        "20" to "Jharkhand",
        "21" to "Odisha",
        "22" to "Chhattisgarh",
        "23" to "Madhya Pradesh",
        "24" to "Gujarat",
        "26" to "Dadra & Nagar Haveli and Daman & Diu",
        "27" to "Maharashtra",
        "29" to "Karnataka",
        "30" to "Goa",
        "31" to "Lakshadweep",
        "32" to "Kerala",
        "33" to "Tamil Nadu",
        "34" to "Puducherry",
        "35" to "Andaman & Nicobar Islands",
        "36" to "Telangana",
        "37" to "Andhra Pradesh",
        "38" to "Ladakh"
    )

    /** Normalized (case/space/`&`/`and`-insensitive) name → code. */
    val stateCodeByName: Map<String, String> by lazy {
        buildMap {
            stateNameByCode.forEach { (code, name) -> put(normalize(name), code) }
            // Legacy / common aliases
            put(normalize("Jammu and Kashmir"), "01")
            put(normalize("Uttaranchal"), "05")
            put(normalize("Orissa"), "21")
            put(normalize("Dadra and Nagar Haveli"), "26")
            put(normalize("Daman and Diu"), "26")
            put(normalize("Pondicherry"), "34")
            put(normalize("Andaman and Nicobar"), "35")
            put(normalize("Andaman and Nicobar Islands"), "35")
        }
    }

    fun normalize(name: String): String = name
        .lowercase()
        .replace("&", "and")
        .replace(Regex("[^a-z]"), "")

    /** Look up the 2-digit code for a free-text state name, or null when unknown. */
    fun codeForStateName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        // Allow an already-coded value ("27" or "27 - Maharashtra") through.
        val trimmed = name.trim()
        if (stateNameByCode.containsKey(trimmed)) return trimmed
        val codeMatch = Regex("^\\d{2}").find(trimmed)
        if (codeMatch != null && stateNameByCode.containsKey(codeMatch.value)) return codeMatch.value
        return stateCodeByName[normalize(trimmed)]
    }

    fun nameForCode(code: String?): String? = code?.let { stateNameByCode[it] }

    fun isValidCode(code: String?): Boolean = code != null && stateNameByCode.containsKey(code)
}
