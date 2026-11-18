package com.minhas.teller.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import androidx.work.Constraints
import androidx.work.OutOfQuotaPolicy
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import com.minhas.teller.BuildConfig
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.utils.IntentClassifier.Intent
import java.util.concurrent.TimeUnit


data class DraftEntry(
    val ledgerName: String,
    val isDebit: Boolean,
    val amount: Double,
    val groupSuggestion: String? = null
)

data class DraftInventoryItem(
    val name: String,
    val quantity: Double,
    val rate: Double,
    val amount: Double,
    val unit: String? = null,
    val gstRate: Double? = null,
    val category: String? = null,
    val hsnCode: String? = null
)

data class VoucherIntent(
    val intent: String = "CREATE_VOUCHER",
    val referenceTag: String?,
    val amount: Double?,
    val paymentMode: String?,
    val narration: String?,
    val isInfoMissing: Boolean,
    val clarificationQuestion: String?
)

data class GeminiEntity(
    val role: String,
    val raw_text_mention: String,
    val suggested_ledger_name: String,
    val amount: Double = 0.0,
    val likely_side: String? = null
)

data class GeminiTransaction(
    val intent: String,
    val suggested_voucher_type: String,
    val narrative_summary: String?,
    val entities: List<GeminiEntity>,
    val items: List<DraftInventoryItem> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

data class GeminiTransactionsResponse(
    val transactions: List<GeminiTransaction>
)

// ── Confidence scoring for 3-tier AI routing ──
enum class Confidence {
    HIGH,    // Regex matched definitively — respond immediately, no API cost
    MEDIUM,  // Best-effort regex match or Hinglish normalization helped
    NONE     // No match — must escalate to DeepSeek cloud AI
}

data class AiResult(
    val response: AiResponse,
    val confidence: Confidence,
    val source: String = "regex" // "regex", "gemma_normalized", "deepseek" for analytics
)

// ── Chart/table data classes for AI-generated visualizations ──
data class ChartDataset(
    val label: String,
    val values: List<Double>,
    val color: String? = null
)

sealed class AiResponse {
    data class Message(val text: String) : AiResponse()
    data class VoucherIntentResponse(val intent: VoucherIntent) : AiResponse()
    data class ParsedTransactions(val response: GeminiTransactionsResponse) : AiResponse()
    data class DraftVoucher(
        val type: String?,
        val date: String?,
        val voucherNumber: String?,
        val entries: List<DraftEntry>,
        val inventoryItems: List<DraftInventoryItem>,
        val narration: String?,
        val partyName: String? = null,
        val amount: Double? = null
    ) : AiResponse()
    data class DraftLedger(
        val name: String,
        val group: String,
        val openingBalance: Double?
    ) : AiResponse()
    /** A sale/purchase verb was recognized but one required field couldn't be resolved — the
     *  chat should ask [question] and, once the user replies, merge the answer into these
     *  already-known fields rather than discarding them and starting over. */
    data class NeedsInfo(
        val txnType: String,       // "Sales" or "Purchase"
        val item: String?,
        val party: String?,
        val amount: Double?,
        val qty: Double?,
        val date: String?,
        val missingField: String,  // "amount" | "item" | "party"
        val question: String
    ) : AiResponse()
    data class MultiAction(val actions: List<AiResponse>) : AiResponse()
    data class QueryBalance(val ledgerName: String?) : AiResponse()
    data class QueryStock(val itemName: String?) : AiResponse()
    data class QuerySales(val period: String?) : AiResponse()
    data class QueryMetric(val metric: String, val period: String?) : AiResponse()
    data class QueryItemSales(val period: String?, val groupBy: String?) : AiResponse()
    data class QueryOutstanding(val ledgerName: String?, val period: String?) : AiResponse()
    data class QueryTopParties(val kind: String, val period: String?, val limit: Int) : AiResponse()
    data class QueryRecentVouchers(val voucherType: String?, val period: String?, val limit: Int) : AiResponse()
    object QuerySummary : AiResponse()
    object QueryCollectables : AiResponse()
    object QueryPayables : AiResponse()
    object QueryCollectablesList : AiResponse()
    object QueryPayablesList : AiResponse()
    // ── New: AI-generated chart and table responses ──
    data class QueryChart(
        val chartType: String,        // "bar", "pie", "line"
        val title: String,
        val labels: List<String>,
        val datasets: List<ChartDataset>,
        val rawCsv: String            // Pre-formatted CSV for one-tap export
    ) : AiResponse()
    data class QueryDataTable(
        val title: String,
        val columns: List<String>,
        val rows: List<List<String>>,
        val rawCsv: String
    ) : AiResponse()
}

object FinanceHelper {
    private const val TAG = "FinanceHelper"
    private const val ASSISTANT_NAME = "Helafin"
    const val OFFLINE_MLC_DOWNLOAD_WORK_NAME = com.minhas.teller.workers.MLCLLMWorker.WORK_NAME

    fun enqueueOfflineModelDownload(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<com.minhas.teller.workers.MLCLLMWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)  // Quick retry for resume
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(OFFLINE_MLC_DOWNLOAD_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueOfflineModelDownloadForce(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<com.minhas.teller.workers.MLCLLMWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)  // Quick retry for resume
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(OFFLINE_MLC_DOWNLOAD_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private const val CACHE_PREFS = "helafin_gemini_cache_v2"
    private const val CACHE_VALUE_PREFIX = "json:"

    const val ALL_UNIT_REGEX_PATTERN =
        "(?:killo|kilo|kilos|kila|kile|kg|kgs|kilogram|kilograms|gms|gram|grams|gm|litres|liters|litre|liter|ltr|ltrs|leeter|litar|lit|darjan|dozens|dozen|doz|dzn|pieces|piece|pcs|pc|peice|peices|nag|nos|number|numbers|packets|packet|pkt|pkts|pack|packs|meters|meter|metre|metres|mtr|mtrs|boriya|boriyan|bori|bags|bag|peti|box|boxes|bottles|bottle|btl|btls|pair|pairs|jodi|foot|feet|ft|किलो|लीटर|ग्राम|मीटर|फीट|फुट|पीस|नग|दर्जन|बोतल|पैकेट|पैक|बोरी|बोरियां|बोरिया|डिब्बा|पेटी|बॉक्स|जोड़ी)"

    fun canonicalUnit(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val l = raw.trim().lowercase(Locale.ROOT)
        return when {
            Regex("^(?:litre|liter|litres|liters|ltr|ltrs|leeter|litar|lit|लीटर)$").matches(l) -> "Liter"
            Regex("^(?:kg|kilo|kilos|killo|killos|kilogram|kilograms|kila|kile|kgs|kilogm|किलो)$").matches(l) -> "Kg"
            Regex("^(?:gm|gram|grams|g|gms|ग्राम)$").matches(l) -> "Gram"
            Regex("^(?:mtr|meter|meters|metre|metres|mtrs|मीटर)$").matches(l) -> "Meter"
            Regex("^(?:foot|feet|ft|फीट|फुट)$").matches(l) -> "Foot"
            Regex("^(?:pcs|piece|pieces|pc|peice|peices|nag|nos|number|numbers|पीस|नग)$").matches(l) -> "Pcs"
            Regex("^(?:doz|dozen|dozens|darjan|dzn|दर्जन)$").matches(l) -> "Dozen"
            Regex("^(?:pair|pairs|jodi|jodee|जोड़ी)$").matches(l) -> "Pair"
            Regex("^(?:btl|bottle|bottles|btls|बोतल)$").matches(l) -> "Bottle"
            Regex("^(?:pkt|packet|packets|pkts|pack|packs|पैकेट|पैक)$").matches(l) -> "Pack"
            Regex("^(?:bag|bags|bori|boriya|boriyan|बोरी|बोरियां|बोरिया)$").matches(l) -> "Bag"
            Regex("^(?:box|boxes|peti|डिब्बा|पेटी|बॉक्स)$").matches(l) -> "Box"
            else -> ProductCategory.resolveUnit(l).takeIf { it != l }
        }
    }

    fun parseDateFromQuery(query: String): String? {
        val q = query.lowercase(Locale.ROOT).trim()
        if (q.contains("today") || q.contains("aaj")) return "today"
        if (q.contains("yesterday") || q.contains("kal")) return "yesterday"
        val thisMonthPattern = Pattern.compile("(\\d+)(?:st|nd|rd|th)?\\s+(?:of\\s+)?this\\s+month")
        val thisMonthMatcher = thisMonthPattern.matcher(q)
        if (thisMonthMatcher.find()) {
            val day = thisMonthMatcher.group(1)?.toIntOrNull()
            if (day != null && day in 1..31) {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, day)
                return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            }
        }
        val thisMonthHinglishPattern1 = Pattern.compile("is\\s+mahine\\s+(?:ki\\s+)?(\\d+)")
        val thisMonthHinglishMatcher1 = thisMonthHinglishPattern1.matcher(q)
        if (thisMonthHinglishMatcher1.find()) {
            val day = thisMonthHinglishMatcher1.group(1)?.toIntOrNull()
            if (day != null && day in 1..31) {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, day)
                return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            }
        }
        val thisMonthHinglishPattern2 = Pattern.compile("(\\d+)\\s*(?:tarikh|tarik|date)?\\s*is\\s+mahine")
        val thisMonthHinglishMatcher2 = thisMonthHinglishPattern2.matcher(q)
        if (thisMonthHinglishMatcher2.find()) {
            val day = thisMonthHinglishMatcher2.group(1)?.toIntOrNull()
            if (day != null && day in 1..31) {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, day)
                return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            }
        }
        val datePattern = Pattern.compile("\\b(?:on\\s+)?date\\s+(\\d+)(?:st|nd|rd|th)?\\b")
        val dateMatcher = datePattern.matcher(q)
        if (dateMatcher.find()) {
            val day = dateMatcher.group(1)?.toIntOrNull()
            if (day != null && day in 1..31) {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, day)
                return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            }
        }
        val dmyPattern = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})\\b")
        val dmyMatcher = dmyPattern.matcher(q)
        if (dmyMatcher.find()) {
            val day = dmyMatcher.group(1)?.toIntOrNull()
            val month = dmyMatcher.group(2)?.toIntOrNull()
            val yearStr = dmyMatcher.group(3)
            if (day != null && month != null && yearStr != null) {
                val cal = Calendar.getInstance()
                val year = if (yearStr.length == 2) (Calendar.getInstance().get(Calendar.YEAR) / 100 * 100) + yearStr.toInt() else yearStr.toInt()
                cal.set(Calendar.YEAR, year); cal.set(Calendar.MONTH, month - 1); cal.set(Calendar.DAY_OF_MONTH, day)
                return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            }
        }
        val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        for (i in months.indices) {
            val mName = months[i]
            val mPattern1 = Pattern.compile("(\\d+)(?:st|nd|rd|th)?\\s+${mName}[a-z]*")
            val mMatcher1 = mPattern1.matcher(q)
            if (mMatcher1.find()) {
                val day = mMatcher1.group(1)?.toIntOrNull()
                if (day != null && day in 1..31) {
                    val cal = Calendar.getInstance(); cal.set(Calendar.MONTH, i); cal.set(Calendar.DAY_OF_MONTH, day)
                    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                }
            }
            val mPattern2 = Pattern.compile("${mName}[a-z]*\\s+(\\d+)(?:st|nd|rd|th)?")
            val mMatcher2 = mPattern2.matcher(q)
            if (mMatcher2.find()) {
                val day = mMatcher2.group(1)?.toIntOrNull()
                if (day != null && day in 1..31) {
                    val cal = Calendar.getInstance(); cal.set(Calendar.MONTH, i); cal.set(Calendar.DAY_OF_MONTH, day)
                    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                }
            }
        }
        return null
    }

    fun parseVoucherDate(dateStr: String?): Date {
        if (dateStr.isNullOrBlank()) return Date()
        val clean = dateStr.trim().lowercase(Locale.ROOT)
            .replace(Regex("\\s*\\(.*\\)"), "")
            .trim()
        if (clean == "today" || clean == "aaj") return Date()
        if (clean == "yesterday" || clean == "kal") {
            val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -1); return cal.time
        }
        try { return SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(clean) ?: Date() } catch (e: Exception) {}
        try { return SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(clean.replace("-", "/")) ?: Date() } catch (e: Exception) {}
        try { return SimpleDateFormat("dd-MMM-yyyy", Locale.US).parse(clean) ?: Date() } catch (e: Exception) {}
        return Date()
    }

    private fun normalizeQuery(query: String): String = query.lowercase(Locale.getDefault()).trim().replace(Regex("\\s+"), " ")

    // ── Devanagari (full Hindi) → Roman Hinglish ──────────────────────────────
    // The mic can be set to Hindi (hi-IN), which returns Devanagari text. The
    // voucher parsers below match Roman Hinglish only, so we normalise Devanagari
    // into the same Roman forms they already understand before they run.

    private val devanagariNumberWords = mapOf(
        "शून्य" to "0", "एक" to "1", "दो" to "2", "तीन" to "3", "चार" to "4",
        "पाँच" to "5", "पांच" to "5", "छह" to "6", "छः" to "6", "सात" to "7",
        "आठ" to "8", "नौ" to "9", "दस" to "10", "ग्यारह" to "11", "बारह" to "12",
        "तेरह" to "13", "चौदह" to "14", "पंद्रह" to "15", "पन्द्रह" to "15", "सोलह" to "16",
        "सत्रह" to "17", "अठारह" to "18", "उन्नीस" to "19", "बीस" to "20",
        "तीस" to "30", "चालीस" to "40", "पचास" to "50", "साठ" to "60",
        "सत्तर" to "70", "अस्सी" to "80", "नब्बे" to "90", "सौ" to "100",
        "हज़ार" to "1000", "हजार" to "1000", "लाख" to "100000", "करोड़" to "10000000",
        "सवा" to "1.25", "डेढ़" to "1.5", "ढाई" to "2.5", "आधा" to "0.5"
    )

    private val devanagariStructural = mapOf(
        // action verbs
        "बेचे" to "beche", "बेचा" to "becha", "बेची" to "bechi", "बेच" to "bech", "बेचो" to "becho", "बिक्री" to "bikri",
        "खरीदा" to "kharida", "खरीदी" to "kharidi", "खरीदे" to "kharide", "खरीद" to "kharid",
        "दिया" to "diya", "दिए" to "diye", "दी" to "di", "चुकाया" to "chukaya",
        "लिया" to "liya", "लिए" to "liye", "ली" to "li", "लेना" to "lena", "लीना" to "lena", "लेनी" to "leni",
        "मिला" to "mila", "मिले" to "mile", "आया" to "aaya", "आए" to "aaye",
        // billing, deposits, transfers
        "बिल" to "bill", "इनवॉइस" to "invoice", "बनाओ" to "banao", "बनाएं" to "banao", "बना" to "bana",
        "जमा" to "jama", "भेजा" to "bheja", "भेजे" to "bheje", "भेजी" to "bheji",
        // expenses
        "भरा" to "bhara", "भरे" to "bhara", "भरी" to "bhara", "किराया" to "kiraya", "वेतन" to "salary", "पगार" to "salary",
        "बिजली" to "bijli", "पेट्रोल" to "petrol", "डीजल" to "diesel", "चाय" to "chai", "नाश्ता" to "nashta",
        // postpositions / particles
        "को" to "ko", "से" to "se", "ने" to "ne", "का" to "ka", "की" to "ki", "के" to "ke",
        "में" to "mein", "मे" to "me", "पर" to "par", "और" to "aur", "माल" to "maal", "समान" to "maal", "सामान" to "maal",
        "नगद" to "nagad", "नकद" to "nagad", "रोकड़" to "rokar", "खाता" to "khata", "कैश" to "cash", "बैंक" to "bank", "चेक" to "cheque",
        "उधार" to "udhaar", "खर्चा" to "kharcha", "खर्च" to "kharcha", "कमाई" to "kamai", "फायदा" to "fayda",
        "नुकसान" to "nuksan", "मुनाफ़ा" to "munafa",
        // currency & rates
        "रुपये" to "rupaye", "रुपया" to "rupaye", "रुपए" to "rupaye", "रू" to "rs", "रु" to "rs",
        "प्रति" to "per", "दर" to "rate", "हिसाब" to "hisab", "भाव" to "bhav",
        // units
        "किलो" to "kg", "ग्राम" to "gm", "दर्जन" to "doz", "बोतल" to "btl",
        "पैकेट" to "pkt", "मीटर" to "mtr", "बोरी" to "bag", "लीटर" to "ltr", "पीस" to "pcs", "पेटी" to "box", "जोड़ी" to "jodi",
        // trade items (multi-word phrases first)
        "सरसों का तेल" to "sarson ka tel", "देसी घी" to "desi ghee", "चाय पत्ती" to "chai patti", "कोल्ड ड्रिंक" to "cold drink",
        "पारले जी" to "parle g", "पारले" to "parle g", "मोबाइल फोन" to "mobile phone", "इंजन ऑयल" to "engine oil",
        "चावल" to "chawal", "चीनी" to "cheeni", "गेहूं" to "gehun", "आटा" to "atta", "दाल" to "dal", "दूध" to "doodh",
        "तेल" to "tel", "घी" to "ghee", "सेब" to "seb", "केला" to "kela", "केले" to "kela", "आम" to "aam", "आलू" to "aloo",
        "प्याज़" to "pyaj", "प्याज" to "pyaj", "टमाटर" to "tamatar", "सीमेंट" to "cement", "ईंट" to "eent",
        "लोहा" to "iron", "सरिया" to "sariya", "दवा" to "dawa", "दवाई" to "dawai", "दवाइयां" to "dawai", "दवाइयों" to "dawai",
        "बिस्कुट" to "biscuit", "बिस्कुटों" to "biscuit", "बिस्किट" to "biscuit", "चॉकलेट" to "chocolate", "नमकीन" to "namkeen", "शहद" to "honey",
        "साबुन" to "sabun", "शैम्पू" to "shampoo", "टूथपेस्ट" to "toothpaste", "डिटर्जेंट" to "detergent",
        "कपड़ा" to "kapda", "कपड़े" to "kapda", "शर्ट" to "shirt", "जींस" to "jeans", "टीशर्ट" to "tshirt", "जूते" to "jute", "जूता" to "juta",
        "पेंट" to "paint", "टाइल्स" to "tiles", "लैपटॉप" to "laptop", "टीवी" to "tv", "टेलीविज़न" to "television", "टेलीविजन" to "television",
        "एसी" to "ac", "फ्रिज" to "fridge", "पंखा" to "fan", "पंखे" to "fan", "बैटरी" to "battery",
        "मोटरसाइकिल" to "motorcycle", "गाड़ी" to "gaadi", "टायर" to "tyre", "हेलमेट" to "helmet",
        "प्रोटीन" to "protein", "किताब" to "kitab", "किताबें" to "kitab", "कॉपी" to "copy", "कापी" to "copy", "नोटबुक" to "notebook",
        "पेन" to "pen", "कुर्सी" to "kursi", "कुर्सियां" to "kursi", "कुर्सियाँ" to "kursi", "मेज" to "table",
        "मैगी" to "maggi", "थम्स अप" to "thums up", "नमक" to "namak",
        "कॉफ़ी" to "coffee", "कॉफी" to "coffee", "मक्खन" to "makhan", "पनीर" to "paneer",
        // fillers
        "भैया" to "bhaiya", "भाई" to "bhai", "सर" to "sir", "कृपया" to "please",
        "मैंने" to "maine", "हमने" to "humne", "अभी" to "abhi"
    )

    private fun normalizeDevanagari(input: String): String {
        var q = input
        for ((word, repl) in devanagariNumberWords + devanagariStructural) {
            q = Regex("(?<![\\u0900-\\u097F])" + word + "(?![\\u0900-\\u097F])").replace(q, repl)
        }
        return transliterateDevanagariToRoman(q)
    }

    private fun transliterateDevanagariToRoman(input: String): String {
        val consonant = mapOf(
            'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
            'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
            'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
            'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
            'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
            'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
            'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h"
        )
        val matra = mapOf(
            'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri",
            'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e"
        )
        val vowel = mapOf(
            'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo",
            'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o"
        )
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            val cons = consonant[c]
            if (cons != null) {
                sb.append(cons)
                val next = input.getOrNull(i + 1)
                when {
                    next != null && matra.containsKey(next) -> { sb.append(matra.getValue(next)); i++ }
                    next == '्' -> { i++ } // virama: suppress inherent 'a'
                    next == null || next.isWhitespace() -> { /* word-final schwa deletion */ }
                    else -> sb.append('a')
                }
            } else {
                when (c) {
                    in matra -> sb.append(matra.getValue(c))
                    in vowel -> sb.append(vowel.getValue(c))
                    'ं', 'ँ' -> sb.append('n') // anusvara / chandrabindu
                    'ः' -> sb.append('h')           // visarga
                    '्', '़', '।', '॥' -> { /* halant / nukta / danda */ }
                    else -> sb.append(c)
                }
            }
            i++
        }
        return sb.toString()
    }

    private fun normalizeHindiNumbersCore(input: String): String {
        val devanagari = mapOf('০' to '0', '১' to '1', '২' to '2', '३' to '3', '৪' to '4', '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9', '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4', '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9')
        var q = input.lowercase(Locale.ROOT).trim().replace("₹", "")
        // Strip comma only between digits (e.g. 10,000 -> 10000), preserving syntactic clause commas
        q = Regex("(?<=\\d),(?=\\d)").replace(q, "")
        q = Regex("\\b(?:rs|rupees|rupaye|rupay|rupey|rupya|rupiya|rupe|rupee|rpb)\\b").replace(q, " ")
        q = Regex("(\\d+)([a-zA-Z\u0900-\u097F]+)").replace(q, "$1 $2")
        q = Regex("([a-zA-Z\u0900-\u097F]+)(\\d+)").replace(q, "$1 $2")
        q = q.map { devanagari[it] ?: it }.joinToString("")
        val devanagariWords = listOf(
            "शून्य" to "0", "एक" to "1", "दो" to "2", "तीन" to "3", "चार" to "4", "पाँच" to "5", "पांच" to "5", "छह" to "6", "छः" to "6", "सात" to "7", "आठ" to "8", "नौ" to "9", "दस" to "10", "ग्यारह" to "11", "बारह" to "12", "तेरह" to "13", "चौदह" to "14", "पंद्रह" to "15", "पन्द्रह" to "15", "सोलह" to "16", "सत्रह" to "17", "अठारह" to "18", "उन्नीस" to "19", "बीस" to "20", "तीस" to "30", "चालीस" to "40", "पचास" to "50", "साठ" to "60", "सत्तर" to "70", "अस्सी" to "80", "नब्बे" to "90", "सौ" to "100", "हज़ार" to "1000", "हजार" to "1000", "लाख" to "100000", "करोड़" to "10000000",
            "बेचे" to "beche", "बेचा" to "becha", "बेची" to "bechi", "बेच" to "bech", "बेचो" to "becho", "बिक्री" to "bikri", "खरीदा" to "kharida", "खरीदी" to "kharidi", "खरीदे" to "kharide", "खरीद" to "kharid", "दिया" to "diya", "दिए" to "diye", "दी" to "di", "चुकाया" to "chukaya", "लिया" to "liya", "लिए" to "liye", "ली" to "li", "लेना" to "lena", "लीना" to "lena", "लेनी" to "leni", "मिला" to "mila", "मिले" to "mile", "आया" to "aaya", "आए" to "aaye", "को" to "ko", "से" to "se", "ने" to "ne", "का" to "ka", "की" to "ki", "के" to "ke", "में" to "mein", "मे" to "me", "पर" to "par", "और" to "aur", "भरा" to "bhara", "भरे" to "bhare",
            "माल" to "maal", "नगद" to "nagad", "नकद" to "nagad", "रोकड़" to "rokar", "खाता" to "khata", "उधार" to "udhaar", "बाकी" to "baki", "खर्चा" to "kharcha", "किराया" to "kiraya", "बिजली" to "bijli", "बिल" to "bill", "कमाई" to "kamai", "फायदा" to "fayda", "नुकसान" to "nuksan", "मुनाफ़ा" to "munafa", "किलो" to "kg", "ग्राम" to "gm", "दर्जन" to "doz", "बोतल" to "btl", "पैकेट" to "pkt", "मीटर" to "mtr", "बोरी" to "bag", "लीटर" to "ltr", "भैया" to "bhaiya", "भाई" to "bhai", "सर" to "sir", "कृपया" to "please", "मैंने" to "maine", "हमने" to "humne", "अभी" to "abhi"
        )
        for ((k, v) in devanagariWords) {
            val p = Pattern.compile("(?<![\\u0900-\\u097F])$k(?![\\u0900-\\u097F])")
            q = p.matcher(q).replaceAll(v)
        }
        q = normalizeDevanagari(q)
        // Undo what the speech recogniser got wrong before any number word is read: it glues
        // spoken numbers onto the next word ("paanch rum" → "panchram") and writes the English
        // "for" as the digit 4 ("for 300" → "4 300"). See [SpeechRepair].
        q = SpeechRepair.repair(q)
        val numberWords = listOf(
            "shunya" to "0", "zero" to "0", "ek" to "1", "do" to "2", "teen" to "3", "char" to "4", "chaar" to "4",
            "panch" to "5", "paanch" to "5", "che" to "6", "chhe" to "6", "chhah" to "6", "saat" to "7", "aath" to "8", "ath" to "8", "nau" to "9", "no" to "9",
            "das" to "10", "gyarah" to "11", "gyaarah" to "11", "gayarah" to "11", "barah" to "12", "baarah" to "12",
            "terah" to "13", "chaudah" to "14", "choudah" to "14", "pandrah" to "15", "solah" to "16", "satrah" to "17", "satra" to "17",
            "atharah" to "18", "athra" to "18", "unnis" to "19", "bees" to "20",
            "ikkis" to "21", "bais" to "22", "baais" to "22", "teis" to "23", "chaubis" to "24", "chobis" to "24",
            "pachis" to "25", "pachees" to "25", "chabbis" to "26", "sattais" to "27", "athais" to "28", "atthais" to "28",
            "untis" to "29", "tees" to "30",
            "ikattis" to "31", "iktees" to "31", "battis" to "32", "batees" to "32", "taintis" to "33", "teetis" to "33",
            "chautis" to "34", "chauntis" to "34", "paintis" to "35", "paitis" to "35", "chattis" to "36", "chhattis" to "36",
            "saintis" to "37", "sattis" to "37", "adtis" to "38", "atthis" to "38", "untalis" to "39", "untalees" to "39",
            "chalis" to "40", "chaalis" to "40", "iktalis" to "41", "iktalees" to "41", "bayalis" to "42", "bayalees" to "42",
            "taintalis" to "43", "tentalis" to "43", "chauvalis" to "44", "chavalis" to "44", "paintalis" to "45", "pantalis" to "45",
            "chiyalis" to "46", "chhiyalis" to "46", "saintalis" to "47", "santalis" to "47", "adtalis" to "48", "adtaalees" to "48",
            "unchas" to "49", "unchaas" to "49",
            "pachas" to "50", "pachaas" to "50", "ikyavan" to "51", "ikyawan" to "51", "bavan" to "52", "bawan" to "52",
            "tirpan" to "53", "chauvan" to "54", "chavan" to "54", "pachpan" to "55", "pachapan" to "55",
            "chappan" to "56", "chhappan" to "56", "sattavan" to "57", "sattawan" to "57", "athavan" to "58", "athawan" to "58",
            "unsath" to "59",
            "saath" to "60", "iksath" to "61", "eksath" to "61", "basath" to "62", "tirsath" to "63", "chausath" to "64",
            "painsath" to "65", "chiyasath" to "66", "chhiyasath" to "66", "sarsath" to "67", "adsath" to "68", "unhattar" to "69",
            "sattar" to "70", "ikhattar" to "71", "bahattar" to "72", "tihattar" to "73", "chauhattar" to "74",
            "pachhattar" to "75", "chhihattar" to "76", "sathattar" to "77", "athhattar" to "78", "unasi" to "79",
            "assi" to "80", "ikyasi" to "81", "bayasi" to "82", "tirasi" to "83", "chaurasi" to "84",
            "pachasi" to "85", "chiyasi" to "86", "chhiyasi" to "86", "satasi" to "87", "athasi" to "88", "navasi" to "89",
            "nabbe" to "90", "ikyanave" to "91", "ikyanve" to "91", "banave" to "92", "bawanve" to "92", "tiranave" to "93", "tiranve" to "93",
            "chauranave" to "94", "chauranve" to "94", "pachanave" to "95", "pachanve" to "95", "chhiyanave" to "96", "chhiyanve" to "96",
            "sattanave" to "97", "sattanve" to "97", "athanave" to "98", "athanve" to "98", "ninanave" to "99", "ninanve" to "99",
            // Hundreds:
            "ekso" to "100", "eksoo" to "100", "eksau" to "100",
            "doso" to "200", "dosoo" to "200", "dosau" to "200", "dooso" to "200", "doosoo" to "200",
            "teenso" to "300", "teensoo" to "300", "teensau" to "300",
            "charso" to "400", "charsoo" to "400", "charsau" to "400", "chaarso" to "400", "chaarsoo" to "400", "chaarsau" to "400",
            "panchso" to "500", "panchsoo" to "500", "panchsau" to "500", "paanchso" to "500", "paanchsoo" to "500", "paanchsau" to "500", "panso" to "500", "pansoo" to "500", "pansau" to "500",
            "chheso" to "600", "chhesoo" to "600", "chhesau" to "600", "cheso" to "600", "chesoo" to "600", "chesau" to "600", "chahso" to "600", "chahsoo" to "600",
            "saatso" to "700", "saatsoo" to "700", "saatsau" to "700", "satso" to "700", "satsoo" to "700",
            "aathso" to "800", "aathsoo" to "800", "aathsau" to "800", "athso" to "800", "athsoo" to "800", "athsau" to "800",
            "nauso" to "900", "nausoo" to "900", "nausau" to "900", "noso" to "900", "nosoo" to "900",
            // Multipliers & fractional:
            "sau" to "100", "so" to "100", "soo" to "100", "sow" to "100",
            "dhaai" to "2.5", "dhai" to "2.5", "dedh" to "1.5", "derh" to "1.5", "sava" to "1.25", "sawaa" to "1.25", "adha" to "0.5", "aadha" to "0.5",
            "hazar" to "1000", "hazaar" to "1000", "harjar" to "1000", "harjaar" to "1000", "hazzar" to "1000", "hazarr" to "1000", "hjaar" to "1000", "hajar" to "1000",
            "lakh" to "100000", "laakh" to "100000", "lac" to "100000", "lacs" to "100000", "lakhs" to "100000",
            "crore" to "10000000", "crores" to "10000000", "karod" to "10000000", "krod" to "10000000", "kror" to "10000000", "krore" to "10000000", "kroad" to "10000000", "kroar" to "10000000", "khokha" to "10000000"
        )
        // English number words too: Android STT often expands spoken amounts to words
        // ("nine thousand"), which the Hindi-only map above leaves untouched → the regex
        // finds no digits → escalates to the cloud LLM → wrong amount/item. Mapping these
        // keeps such queries on the fast, deterministic offline path.
        val englishNumberWords = listOf(
            "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
            "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10",
            "eleven" to "11", "twelve" to "12", "thirteen" to "13", "fourteen" to "14",
            "fifteen" to "15", "sixteen" to "16", "seventeen" to "17", "eighteen" to "18",
            "nineteen" to "19", "twenty" to "20", "thirty" to "30", "forty" to "40",
            "fifty" to "50", "sixty" to "60", "seventy" to "70", "eighty" to "80",
            "ninety" to "90", "hundred" to "100", "thousand" to "1000"
        )
        for ((word, num) in numberWords + englishNumberWords) {
            q = Regex("([\u0900-\u097F]+)($word)").replace(q, "$1 $2")
            if (word == "do") {
                // Avoid replacing "do" when used as an imperative verb particle ("kar do", "bana do", etc.).
                // Note: Android's ICU regex engine rejects variable-length look-behinds, so use pattern alternation.
                q = Regex("\\b(likh|bana|kar|de|kaat|jod|daal)\\s+do\\b|\\bdo\\b", RegexOption.IGNORE_CASE).replace(q) { m ->
                    if (m.groups[1] != null) m.value else num
                }
            } else {
                q = Regex("\\b$word\\b").replace(q, num)
            }
        }
        q = Regex("(\\d+)([a-zA-Z\u0900-\u097F]+)").replace(q, "$1 $2")
        q = Regex("([a-zA-Z\u0900-\u097F]+)(\\d+)").replace(q, "$1 $2")
        // Shorthand multipliers: 5k -> 5000, 2.5k -> 2500, 10k -> 10000 (not followed by g/gm for kg)
        q = Regex("\\b(\\d+(?:\\.\\d+)?)\\s*k\\b(?!\\s*(?:g|gm|gram|ilo))", RegexOption.IGNORE_CASE).replace(q) {
            val v = it.groupValues[1].toDoubleOrNull() ?: 0.0
            (v * 1000).toLong().toString()
        }
        // Shorthand crore: 1cr -> 10000000, 2.5cr -> 25000000
        q = Regex("\\b(\\d+(?:\\.\\d+)?)\\s*(?:cr|crore|crores|karod|krod|kror|krore|kroad|kroar)\\b", RegexOption.IGNORE_CASE).replace(q) {
            val v = it.groupValues[1].toDoubleOrNull() ?: 0.0
            (v * 10000000).toLong().toString()
        }
        // Shorthand lakh/lac: 1lakh -> 100000, 2.5lakh -> 250000
        q = Regex("\\b(\\d+(?:\\.\\d+)?)\\s*(?:lac|lacs|lakh|lakhs)\\b", RegexOption.IGNORE_CASE).replace(q) {
            val v = it.groupValues[1].toDoubleOrNull() ?: 0.0
            (v * 100000).toLong().toString()
        }
        // Compound Indian-magnitude numbers spoken as descending chunks ("ek lakh pachaas hazar" =
        // 1,00,000 + 50,000 = 1,50,000). Each word above was already mapped to its own absolute
        // value (lakh->100000, hazar->1000, sau->100, …) standing alone, so the single-pair
        // multiplier loop below can't add two of them together — it only multiplies one adjacent
        // pair, and with no word boundary on its literal multiplier token it would otherwise
        // mis-slice "100000" as a "100" prefix. Collapse the known 2-to-4-level chains explicitly,
        // longest chain first, before that loop runs.
        val compoundMagnitudeChains = listOf(
            "\\b(\\d+(?:\\.\\d+)?)\\s+10000000\\s+(\\d+(?:\\.\\d+)?)\\s+100000\\s+(\\d+(?:\\.\\d+)?)\\s+1000\\s+(\\d+(?:\\.\\d+)?)\\s+100\\b" to doubleArrayOf(10000000.0, 100000.0, 1000.0, 100.0),
            "\\b(\\d+(?:\\.\\d+)?)\\s+10000000\\s+(\\d+(?:\\.\\d+)?)\\s+100000\\s+(\\d+(?:\\.\\d+)?)\\s+1000\\b" to doubleArrayOf(10000000.0, 100000.0, 1000.0),
            "\\b(\\d+(?:\\.\\d+)?)\\s+10000000\\s+(\\d+(?:\\.\\d+)?)\\s+100000\\b" to doubleArrayOf(10000000.0, 100000.0),
            "\\b(\\d+(?:\\.\\d+)?)\\s+100000\\s+(\\d+(?:\\.\\d+)?)\\s+1000\\s+(\\d+(?:\\.\\d+)?)\\s+100\\b" to doubleArrayOf(100000.0, 1000.0, 100.0),
            "\\b(\\d+(?:\\.\\d+)?)\\s+100000\\s+(\\d+(?:\\.\\d+)?)\\s+1000\\b" to doubleArrayOf(100000.0, 1000.0),
            "\\b(\\d+(?:\\.\\d+)?)\\s+1000\\s+(\\d+(?:\\.\\d+)?)\\s+100\\b" to doubleArrayOf(1000.0, 100.0)
        )
        for ((pattern, scales) in compoundMagnitudeChains) {
            q = Regex(pattern).replace(q) { m ->
                var total = 0.0
                for (i in scales.indices) {
                    total += (m.groupValues[i + 1].toDoubleOrNull() ?: 0.0) * scales[i]
                }
                total.toLong().toString()
            }
        }
        val multipliers = listOf("100", "1000", "100000", "10000000")
        for (mult in multipliers) {
            val p = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s+($mult)")
            val m = p.matcher(q); val sb = StringBuffer()
            while (m.find()) {
                val val1 = m.group(1)?.toDouble() ?: 0.0; val val2 = m.group(2)?.toDouble() ?: 0.0
                m.appendReplacement(sb, (val1 * val2).toLong().toString())
            }
            m.appendTail(sb); q = sb.toString()
        }
        // Indian numbering & compound amounts:
        // 1) "20000 500" / "20 500" (thousands + hundreds)
        val compP = Pattern.compile("\\b(\\d+000)\\s+(\\d{1,3})(?=\\s*(?:ki|ke|ka|me|mein|mai|se|ko|rs|rupaye|rupay|rupey|rupees|rupya)?\\b)")
        val compM = compP.matcher(q)
        val compSb = StringBuffer()
        while (compM.find()) {
            val v1 = compM.group(1)?.toLongOrNull() ?: 0L
            val v2 = compM.group(2)?.toLongOrNull() ?: 0L
            compM.appendReplacement(compSb, (v1 + v2).toString())
        }
        compM.appendTail(compSb)
        q = compSb.toString()

        // 1b) Hundreds + tens/units: "100 1" (101), "100 10" (110), "300 56" (356), "20500 50" (20550)
        val hundredP = Pattern.compile("\\b(\\d+00)\\s+(\\d{1,2})(?=\\s*(?:ki|ke|ka|me|mein|mai|se|ko|rs|rupaye|rupay|rupey|rupees|rupya)?\\b)")
        val hundredM = hundredP.matcher(q)
        val hundredSb = StringBuffer()
        while (hundredM.find()) {
            val v1 = hundredM.group(1)?.toLongOrNull() ?: 0L
            val v2 = hundredM.group(2)?.toLongOrNull() ?: 0L
            hundredM.appendReplacement(hundredSb, (v1 + v2).toString())
        }
        hundredM.appendTail(hundredSb)
        q = hundredSb.toString()

        // 2) "83 500 ki" / "1 500 ka" = a single ₹83,500 / ₹1,500 amount
        // (space = thousands separator). Only merge when an amount marker follows, so a
        // qty-rate pair like "5 chappal 500" is left untouched.
        q = Regex("\\b(\\d{1,2})\\s+(\\d{3})(?=\\s*(?:ki|ke|ka|me|mein|mai)\\b)").replace(q, "$1$2")
        // Strip unambiguous auxiliary verbs ("is"/"was") so the Hinglish verb stays
        // adjacent to its amount; keep "kiya" (part of "sale kiya"/"pay kiya").
        q = Regex("\\b(hai|hain|hua|huye|hui|tha|thi|kare|karo|karna|hi|bhi)\\b", RegexOption.IGNORE_CASE).replace(q, "")
        return q
    }

    internal fun normalizeHindiNumbers(input: String): String {
        var q = normalizeHindiNumbersCore(input)

        // Common speech-to-text / typing errors on transaction action words
        val actionTypos = mapOf(
            "sopld" to "sold", "solld" to "sold", "sald" to "sold", "sod" to "sold", "soled" to "sold", "sole" to "sold",
            "saled" to "sold", "selled" to "sold", "sell" to "sold", "sells" to "sold", "selling" to "sold",
            "bougt" to "bought", "bot" to "bought", "buyed" to "bought", "buy" to "bought", "buys" to "bought", "buying" to "bought",
            "piad" to "paid", "payed" to "paid", "peid" to "paid", "padi" to "paid", "pay" to "paid", "pays" to "paid", "paying" to "paid",
            "recieved" to "received", "recive" to "received", "recived" to "received", "recvd" to "received", "gotten" to "received", "got" to "received",
            "too" to "to", "frm" to "from", "fo" to "for"
        )
        for ((typo, correct) in actionTypos) {
            q = Regex("\\b$typo\\b", RegexOption.IGNORE_CASE).replace(q, correct)
        }

        val financialTerms = mapOf(
            "becha" to "sold", "beche" to "sold", "bechi" to "sold", "bech" to "sold", "becho" to "sold", "bikri" to "sale", "bika" to "sold", "biki" to "sold",
            "kharida" to "bought", "khareeda" to "bought", "kharidi" to "bought", "kharide" to "bought", "kharid" to "bought", "khareed" to "bought", "khareede" to "bought", "kharido" to "bought",
            "lia" to "bought", "liya" to "bought", "liye" to "bought", "maal" to "items",
            "diye" to "paid", "diya" to "paid", "de diya" to "paid", "dediya" to "paid", "chukaya" to "paid", "pay kiya" to "paid", "pay kardo" to "paid",
            "mile" to "received", "mila" to "received", "aaye" to "received", "aya" to "received", "receive hua" to "received", "mil gaya" to "received",
            "lena" to "received", "lene" to "received", "leni" to "received",
            "nagad" to "cash", "rokar" to "cash", "khata" to "account", "entry daalo" to "create", "add karo" to "create",
            "kharcha" to "expense", "kamai" to "income", "fayda" to "profit", "munafa" to "profit", "nuksan" to "loss",
            "udhaar" to "outstanding", "udhhar" to "outstanding", "bakaya" to "outstanding", "baaki" to "outstanding", "vasuli" to "collection"
        )
        for ((hinglish, english) in financialTerms) { q = Regex("\\b$hinglish\\b", RegexOption.IGNORE_CASE).replace(q, english) }

        // "cash mein/me/mai" (and "nagad"/"rokar" mein, already mapped to "cash" above) just
        // signals payment via Cash-in-hand, which is already the default party. Strip it here —
        // otherwise it survives into item-splitting and gets absorbed into an item's name
        // (e.g. "50 splendor bechi cash mein 2 crore ki" -> item "Splendor Cash Mein").
        q = Regex("\\bcash\\s+(?:mein|me|mai)\\b", RegexOption.IGNORE_CASE).replace(q, "")

        // Remove only non-structural filler words (preserve ko, se, ne, to, from, for, mein, me, ka, ki, ke)
        q = Regex("\\b(?:aalloo|aaloo|alloo|aalu|allu|aalo|allo|alu|batata)\\b", RegexOption.IGNORE_CASE).replace(q, "aloo")
        q = Regex("\\b(?:tamatar|tamatr|tamator|tamater)\\b", RegexOption.IGNORE_CASE).replace(q, "tamatar")
        q = Regex("\\b(?:pyaaz|pyaz|pyaj|pyaaj|kanda)\\b", RegexOption.IGNORE_CASE).replace(q, "pyaj")
        q = Regex("\\bgaadi\\b", RegexOption.IGNORE_CASE).replace(q, "gadi")
        q = Regex("(\\d+)(rs|rupaye|rupees|per)\\b", RegexOption.IGNORE_CASE).replace(q, "$1 $2")
        val units = listOf("kilogram" to "kg", "kilograms" to "kg", "kilo" to "kg", "kila" to "kg", "killo" to "kg", "killos" to "kg", "killay" to "kg", "litre" to "ltr", "liter" to "ltr", "litres" to "ltr", "liters" to "ltr", "litray" to "ltr", "gram" to "gm", "grams" to "gm", "pieces" to "pcs", "piece" to "pcs", "pc" to "pcs", "dozen" to "doz", "dozens" to "doz", "bottle" to "btl", "bottles" to "btl", "packet" to "pkt", "packets" to "pkt", "meter" to "mtr", "meters" to "mtr", "metre" to "mtr", "bori" to "bag", "boriya" to "bag", "bags" to "bag", "bag" to "bag")
        for ((word, abbr) in units) { q = Regex("\\b$word\\b", RegexOption.IGNORE_CASE).replace(q, abbr) }
        val fillers = setOf("bhaiya", "sir", "please", "mene", "maine", "humne", "sunno", "ok", "acha", "thik", "theek", "bhai", "yaar", "dekho", "suno")
        for (filler in fillers) { q = Regex("\\b$filler\\b", RegexOption.IGNORE_CASE).replace(q, "") }
        // "abhi" means "now" (strip), but "abhi ko/se/ne" is a person named Abhi (keep).
        q = Regex("\\babhi\\b(?!(?:\\s+(?:ko|se|ne|ke|ki|ka)\\b))", RegexOption.IGNORE_CASE).replace(q, "")
        return Regex("\\s+").replace(q.trim(), " ")
    }

    private data class ParsedTxn(val item: String, val party: String, val amt: Double, val qty: Double)

    private fun extractAmt(g: List<String>, vararg indices: Int): Double {
        for (i in indices) {
            val v = g.getOrNull(i)?.trim()
            if (!v.isNullOrBlank()) {
                val num = v.toDoubleOrNull()
                if (num != null && num > 0) return num
            }
        }
        return 0.0
    }

    private suspend fun parseMultiItemTransaction(
        context: Context?,
        q: String,
        extractedDate: String,
        isPerUnit: Boolean,
        u: String
    ): AiResponse.DraftVoucher? {
        val hasMultiConnector = q.contains(" aur ", ignoreCase = true) ||
                q.contains(" and ", ignoreCase = true) ||
                q.contains(",") ||
                q.contains(" tatha ", ignoreCase = true) ||
                q.contains(" evam ", ignoreCase = true) ||
                q.contains(" sath me ", ignoreCase = true) ||
                q.contains(" saath me ", ignoreCase = true)
        val nonItemWords = setOf("me", "mein", "mai", "ke", "ki", "ka", "pe", "par", "each", "per", "rs", "rupees", "rupaye", "r", "total", "worth", "kul", "sold", "bought", "sale", "purchase", "becha", "beche", "bechi", "kharida", "kharide", "kharidi", "ko", "se", "ne", "to", "from", "for", "at", "of", "in")
        val candidateItems = Regex("\\b\\d+(?:\\.\\d+)?\\s+(?:(?:$u)\\s+)?([a-zA-Z\u0900-\u097F]+)").findAll(q)
            .map { it.groupValues[1].lowercase(Locale.ROOT) }
            .filter { it !in nonItemWords }
            .toList()
        val multiItemCount = candidateItems.size
        if (!hasMultiConnector && multiItemCount < 2) return null

        val isSales = Regex("\\b(?:sold|sale|sales|sell|becha|bechi|beche|bech|bikri|bill|invoice|banao|add\\s+karo)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
        val isPurchase = Regex("\\b(?:bought|buy|purchase|purchased|kharida|khareeda|kharide|kharidi|khareed|kharid|liya)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
        if (!isSales && !isPurchase) return null

        // Extract party name
        var party: String? = null
        var working = q

        // Sales party patterns: "to Ramesh", "Ramesh ko"
        if (isSales) {
            val toMatch = Regex("(?:to)\\s+([a-zA-Z\u0900-\u097F\\s]+)$", RegexOption.IGNORE_CASE).find(working)
                ?: Regex("(?:to)\\s+([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)(?:\\s|$)", RegexOption.IGNORE_CASE).find(working)
            if (toMatch != null) {
                party = cleanPartyName(toMatch.groupValues[1])
                working = working.removeRange(toMatch.range).trim()
            } else {
                val koMatch = Regex("^([a-zA-Z\u0900-\u097F\\s]+?)\\s+ko\\b", RegexOption.IGNORE_CASE).find(working)
                    ?: Regex("([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+ko\\s*$", RegexOption.IGNORE_CASE).find(working)
                    ?: Regex("([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+ko\\b", RegexOption.IGNORE_CASE).find(working)
                if (koMatch != null) {
                    party = cleanPartyName(koMatch.groupValues[1])
                    working = working.removeRange(koMatch.range).trim()
                }
            }
        } else {
            // Purchase party patterns: "from Ramesh", "Ramesh se"
            val fromMatch = Regex("(?:from)\\s+([a-zA-Z\u0900-\u097F\\s]+)$", RegexOption.IGNORE_CASE).find(working)
                ?: Regex("(?:from)\\s+([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)(?:\\s|$)", RegexOption.IGNORE_CASE).find(working)
            if (fromMatch != null) {
                party = cleanPartyName(fromMatch.groupValues[1])
                working = working.removeRange(fromMatch.range).trim()
            } else {
                val seMatch = Regex("^([a-zA-Z\u0900-\u097F\\s]+?)\\s+se\\b", RegexOption.IGNORE_CASE).find(working)
                    ?: Regex("([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+se\\s*$", RegexOption.IGNORE_CASE).find(working)
                    ?: Regex("([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+se\\b", RegexOption.IGNORE_CASE).find(working)
                if (seMatch != null) {
                    party = cleanPartyName(seMatch.groupValues[1])
                    working = working.removeRange(seMatch.range).trim()
                }
            }
        }

        val finalParty = party?.takeIf { it.isNotBlank() } ?: "Cash-in-hand"

        // Extract overall total amount before splitting into item clauses:
        var overallTotalAmount = 0.0
        val txnVerbs = "(?:sold|sale|sales|sell|becha|bechi|beche|bech|bikri|bought|buy|purchase|purchased|kharida|khareeda|kharide|kharidi|khareed|kharid|liya)"

        // Helper to check if text preceding the last item already contains item prices
        fun hasPrecedingPrice(text: String): Boolean {
            val hasIndicator = Regex("(?:(?:for|at|of|in|rs|rupees|rupaye|@)\\s*\\d+|\\d+\\s*(?:rs|rupees|rupaye|r|/-|me|mein|mai|pe|par|ke|ki|ka)\\b)", RegexOption.IGNORE_CASE).containsMatchIn(text)
            if (hasIndicator) return true
            val pieces = text.split(Regex("(?i)\\s*(?:,\\s*(?:and|aur|\\+|tatha|evam)?|\\baur\\b|\\band\\b|\\b\\+\\b|\\bevam\\b|\\btatha\\b|\\bsath me\\b|\\bsaath me\\b)\\s*"))
                .filter { it.isNotBlank() }
            for (p in pieces) {
                if (Regex("^(\\d+(?:\\.\\d+)?)\\s*(?:(?:$u)\\s+)?(.+?)\\s+(\\d+(?:\\.\\d+)?)$").containsMatchIn(p.trim())) return true
            }
            return false
        }

        val connectorIndices = listOf(
            working.lastIndexOf(" aur ", ignoreCase = true),
            working.lastIndexOf(" and ", ignoreCase = true),
            working.lastIndexOf(","),
            working.lastIndexOf(" tatha ", ignoreCase = true),
            working.lastIndexOf(" evam ", ignoreCase = true)
        ).filter { it >= 0 }
        val lastConnectorIdx = connectorIndices.maxOrNull() ?: -1
        val precedingHasPrice = if (lastConnectorIdx > 0) hasPrecedingPrice(working.substring(0, lastConnectorIdx)) else false

        // Case A0: Explicit total markers always indicate an overall total: "total 50000", "kul 50000"
        val explicitTotalWordMatch = Regex("(?:\\b(?:total|kul|having\\s+total)\\s*(?:rs|rupees|rupaye|r|inr|/-)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|r|/-)?(?:\\s*(?:ke|ki|ka|me|mein|mai|par|pe))?)\\s*$", RegexOption.IGNORE_CASE).find(working)
        if (explicitTotalWordMatch != null) {
            overallTotalAmount = explicitTotalWordMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            working = working.removeRange(explicitTotalWordMatch.range).trim()
        } else if (!precedingHasPrice) {
            // Only extract trailing amount if preceding items did NOT have their own prices
            // Case A: Trailing overall amount with preposition ("for 800000", "worth 50000")
            val explicitTotalEndMatch = Regex("(?:\\b(?:for|at|of|worth)\\s*(?:rs|rupees|rupaye|r|inr|/-)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|r|inr|/-)?(?:\\s*(?:ke|ki|ka|me|mein|mai|par|pe|total))?|(?:rs|rupees|rupaye|inr)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:/-)?)\\s*$", RegexOption.IGNORE_CASE).find(working)
            if (explicitTotalEndMatch != null) {
                overallTotalAmount = (explicitTotalEndMatch.groupValues[1].takeIf { it.isNotBlank() } ?: explicitTotalEndMatch.groupValues[2]).toDoubleOrNull() ?: 0.0
                working = working.removeRange(explicitTotalEndMatch.range).trim()
            } else {
                // Case B1: Verb + Amount at the end (English order), e.g. "beche 4000 ke", "sold 4000", "kharide 5000 me".
                // Allows a comma pause ("becha, 120") and a trailing colloquial receipt word
                // ("becha, 600 mile") — without this, a bare post-comma total falls through to
                // clause-splitting below and gets misread as a bogus second item.
                // "mila/mile" are the raw Hinglish spellings (tier1); "received/got" are what the
                // same words normalize to upstream in smartRegexParser's English-canonicalized q.
                val verbAmountMatch = Regex("\\b$txnVerbs\\b[,\\s]+(?:for|at|of|in|rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|r|/-)?(?:\\s*(?:ke|ki|ka|me|mein|mai|par|pe|total))?(?:\\s*(?:mila|mile|milta|milti|mil\\s+gaya|mil\\s+gaye|received|got))?\\s*$", RegexOption.IGNORE_CASE).find(working)
                if (verbAmountMatch != null) {
                    overallTotalAmount = verbAmountMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    working = working.removeRange(verbAmountMatch.range).trim()
                } else {
                    // Case B2: Amount + Verb at the end (Hindi order), e.g. "800000 mein sold", "800000 me beche", "4000 ke beche"
                    val amountVerbMatch = Regex("(?<!\\d)\\b(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|r|inr|/-)?\\s*(?:ke|ki|ka|me|mein|mai|par|pe)?\\s*$txnVerbs\\s*$", RegexOption.IGNORE_CASE).find(working)
                    if (amountVerbMatch != null) {
                        overallTotalAmount = amountVerbMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                        working = working.removeRange(amountVerbMatch.range).trim()
                    } else {
                        // Case C: Trailing amount with Hindi postposition ("4000 ke", "5000 me") when preceding items do not have individual prices
                        val hindiSuffixMatch = Regex("(?<!\\d)\\b(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|r|inr|/-)?\\s*(?:ke|ki|ka|me|mein|mai|par|pe)\\s*$", RegexOption.IGNORE_CASE).find(working)
                        if (hindiSuffixMatch != null) {
                            val candidateAmt = hindiSuffixMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                            val prefixWorking = working.substring(0, hindiSuffixMatch.range.first).trim()
                            val prefixHasConnectors = prefixWorking.contains(" aur ", ignoreCase = true) ||
                                    prefixWorking.contains(" and ", ignoreCase = true) ||
                                    prefixWorking.contains(",") ||
                                    prefixWorking.contains(" tatha ", ignoreCase = true) ||
                                    prefixWorking.contains(" evam ", ignoreCase = true)
                            if (prefixHasConnectors || multiItemCount >= 2) {
                                overallTotalAmount = candidateAmt
                                working = prefixWorking
                            }
                        }
                    }
                }
            }
        }

        // Case D: Overall amount at the beginning of working: "50000 me 50 books aur 40 tables beche"
        if (overallTotalAmount <= 0.0) {
            val startTotalMatch = Regex("^(?:(?:for|worth|total|kul)\\s*(?:rs|rupees|rupaye|r|inr|/-)?\\s*(\\d+(?:\\.\\d+)?)|(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|r|inr|/-)?\\s*(?:ke|ki|ka|me|mein|mai|par|pe)\\s*(?:maal|items)?)\\s+", RegexOption.IGNORE_CASE).find(working)
            if (startTotalMatch != null) {
                overallTotalAmount = (startTotalMatch.groupValues[1].takeIf { it.isNotBlank() } ?: startTotalMatch.groupValues[2]).toDoubleOrNull() ?: 0.0
                working = working.removeRange(startTotalMatch.range).trim()
            }
        }

        // Strip leading / trailing transaction verbs
        working = Regex("\\b(?:sold|sale|sales|sell|becha|bechi|beche|bech|bikri|bought|buy|purchase|purchased|kharida|khareeda|kharide|kharidi|khareed|kharid|liya)\\b", RegexOption.IGNORE_CASE).replace(working, " ").trim()

        // Split into clauses by connectors (handles Oxford comma and conjunctions: ", and ", ", aur ")
        val rawClauses = working.split(Regex("(?i)\\s*(?:,\\s*(?:and|aur|\\+|tatha|evam)?|\\baur\\b|\\band\\b|\\b\\+\\b|\\bevam\\b|\\btatha\\b|\\bsath me\\b|\\bsaath me\\b)\\s*"))
            .map { it.replace(Regex("^(?:and|aur|tatha|evam|sath me|saath me)\\s+", RegexOption.IGNORE_CASE), "").trim() }
            .filter { it.isNotBlank() }

        // Expand clauses where multiple items were spoken without conjunctions (e.g. "50 books 90 tables")
        val clauses = mutableListOf<String>()
        for (rawClause in rawClauses) {
            val splitItems = rawClause.split(
                Regex("(?i)(?<=[a-zA-Z0-9\u0900-\u097F])\\s+(?=\\d+(?:\\.\\d+)?\\s+(?:(?:$u)\\s+)?(?!(?:me|mein|mai|ke|ki|ka|pe|par|each|per|rs|rupees|rupaye|r|total|worth|kul)\\b)[a-zA-Z\u0900-\u097F])")
            ).map { it.trim() }.filter { it.isNotBlank() }
            clauses.addAll(splitItems)
        }

        if (clauses.size < 2) return null

        val draftItems = mutableListOf<DraftInventoryItem>()

        // Item clause patterns:
        // Pattern A: [qty] [unit] [item] [amt] (e.g. "10 kg rice for 600" or "5 kg cheeni 200 me" or "40 register 4000 ke")
        val patternA = Regex("^(\\d+(?:\\.\\d+)?)\\s*(?:($u)\\s+)?(.+?)\\s+(?:for|at|of|in|rs|rupees|rupaye|@|mein|me|ka|ki|ke)?\\s*(?:rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|r|/-)?(?:\\s*(?:me|mein|mai|par|pe|ke|ki|ka))?$", RegexOption.IGNORE_CASE)
        // Pattern B: [item] [qty] [unit] [amt] (e.g. "rice 10 kg 600 me")
        val patternB = Regex("^(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:($u)\\s+)?(?:for|at|of|in|rs|rupees|rupaye|@|mein|me|ka|ki|ke)?\\s*(?:rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|r|/-)?(?:\\s*(?:me|mein|mai|par|pe|ke|ki|ka))?$", RegexOption.IGNORE_CASE)
        // Pattern C: [item] [amt] (quantity defaults to 1.0)
        val patternC = Regex("^(.+?)\\s+(?:for|at|of|in|rs|rupees|rupaye|@|mein|me|ka|ki|ke)?\\s*(?:rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|r|/-)?(?:\\s*(?:me|mein|mai|par|pe|ke|ki|ka))?$", RegexOption.IGNORE_CASE)
        // Pattern D: [qty] [unit] [item] (no price) e.g. "10 kg rice" / "10 tape"
        val patternD = Regex("^(\\d+(?:\\.\\d+)?)\\s*(?:($u)\\s+)?(.+?)$", RegexOption.IGNORE_CASE)
        // Pattern E: [item] [qty] [unit] (no price) e.g. "rice 10 kg"
        val patternE = Regex("^(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:($u)\\s+)?$", RegexOption.IGNORE_CASE)

        for (clause in clauses) {
            var rawQty = 1.0
            var rawUnit: String? = null
            var rawItem: String? = null
            var rawAmt = 0.0
            var noAmount = false

            val mA = patternA.find(clause)
            if (mA != null) {
                rawQty = mA.groupValues[1].toDoubleOrNull() ?: 1.0
                rawUnit = mA.groupValues[2].takeIf { it.isNotBlank() }
                rawItem = mA.groupValues[3].trim()
                rawAmt = mA.groupValues[4].toDoubleOrNull() ?: 0.0
            } else {
                val mB = patternB.find(clause)
                if (mB != null) {
                    rawItem = mB.groupValues[1].trim()
                    rawQty = mB.groupValues[2].toDoubleOrNull() ?: 1.0
                    rawUnit = mB.groupValues[3].takeIf { it.isNotBlank() }
                    rawAmt = mB.groupValues[4].toDoubleOrNull() ?: 0.0
                } else {
                    val mC = patternC.find(clause)
                    if (mC != null) {
                        rawItem = mC.groupValues[1].trim()
                        rawAmt = mC.groupValues[2].toDoubleOrNull() ?: 0.0
                    } else {
                        // No price spoken — still split qty+item so HSN/tax can be auto-assigned
                        // and the user can fill the amount in the review card afterwards.
                        val mD = patternD.find(clause)
                        if (mD != null) {
                            rawQty = mD.groupValues[1].toDoubleOrNull() ?: 1.0
                            rawUnit = mD.groupValues[2].takeIf { it.isNotBlank() }
                            rawItem = mD.groupValues[3].trim()
                            rawAmt = 0.0
                            noAmount = true
                        } else {
                            val mE = patternE.find(clause)
                            if (mE != null) {
                                rawItem = mE.groupValues[1].trim()
                                rawQty = mE.groupValues[2].toDoubleOrNull() ?: 1.0
                                rawUnit = mE.groupValues[3].takeIf { it.isNotBlank() }
                                rawAmt = 0.0
                                noAmount = true
                            }
                        }
                    }
                }
            }

            if (rawItem.isNullOrBlank()) continue
            if (!noAmount && rawAmt <= 0.0) continue

            // Clean up item name and unit
            val (cleanItem, deducedUnit) = extractHinglishUnitAndItem(rawItem, clause)
            val finalUnit = rawUnit?.let { ProductCategory.resolveUnit(it) } ?: deducedUnit
            val translated = LanguageTranslationUtils.translateItem(cleanItem)

            val (finalAmt, rate) = if (isPerUnit && rawQty > 0) {
                (rawAmt * rawQty) to rawAmt
            } else {
                rawAmt to (if (rawQty > 0) rawAmt / rawQty else 0.0)
            }

            val cat = LanguageTranslationUtils.suggestCategory(translated)
            val resolved = VoucherExcelImporter.resolveHsnAndTax(translated, cat, null, null, context)
            val hsn = resolved?.first
            val gst = resolved?.second ?: resolveTaxRate(context, translated)

            draftItems.add(
                DraftInventoryItem(
                    name = translated,
                    quantity = rawQty,
                    rate = rate,
                    amount = finalAmt,
                    unit = finalUnit,
                    gstRate = gst,
                    category = cat,
                    hsnCode = hsn
                )
            )
        }

        if (draftItems.size < 2) return null

        val explicitTotalAmount = draftItems.sumOf { it.amount }
        val finalTotalAmount = if (explicitTotalAmount > 0) explicitTotalAmount else overallTotalAmount

        val finalItems = if (overallTotalAmount > 0 && draftItems.all { it.amount <= 0.0 }) {
            val totalQty = draftItems.sumOf { if (it.quantity > 0) it.quantity else 1.0 }
            val unitRate = if (totalQty > 0) overallTotalAmount / totalQty else (overallTotalAmount / draftItems.size)
            draftItems.map { item ->
                val q = if (item.quantity > 0) item.quantity else 1.0
                val itemAmt = unitRate * q
                item.copy(rate = unitRate, amount = itemAmt)
            }
        } else if (overallTotalAmount > 0 && draftItems.any { it.amount <= 0.0 }) {
            val explicitSum = draftItems.sumOf { it.amount }
            val remainingAmt = (overallTotalAmount - explicitSum).coerceAtLeast(0.0)
            val zeroItems = draftItems.filter { it.amount <= 0.0 }
            val zeroQty = zeroItems.sumOf { if (it.quantity > 0) it.quantity else 1.0 }
            val unitRate = if (zeroQty > 0) remainingAmt / zeroQty else (remainingAmt / zeroItems.size)
            draftItems.map { item ->
                if (item.amount <= 0.0) {
                    val q = if (item.quantity > 0) item.quantity else 1.0
                    val itemAmt = unitRate * q
                    item.copy(rate = unitRate, amount = itemAmt)
                } else {
                    item
                }
            }
        } else {
            draftItems
        }

        val totalAmount = finalItems.sumOf { it.amount }.takeIf { it > 0 } ?: finalTotalAmount
        val voucherType = if (isSales) "Sales" else "Purchase"
        val partyEntry = DraftEntry(finalParty, isDebit = isSales, totalAmount)
        val accountEntry = DraftEntry(if (isSales) "Sales Account" else "Purchase Account", isDebit = !isSales, totalAmount)

        return AiResponse.DraftVoucher(
            type = voucherType,
            date = extractedDate,
            voucherNumber = null,
            entries = listOf(partyEntry, accountEntry),
            inventoryItems = finalItems,
            narration = q,
            partyName = finalParty,
            amount = totalAmount
        )
    }

    internal suspend fun smartRegexParser(context: Context?, query: String): AiResponse.DraftVoucher? {
        val q = normalizeHindiNumbers(query)
        if (q.contains("financed") || q.contains("loan") || q.contains("emi") || q.contains("down payment") || q.contains("downpayment") || q.contains("finance")) return null
        // Never parse a tax/law question as a voucher (protects the voucher-entry mic path too).
        if (IntentClassifier.classify(query) == IntentClassifier.Intent.QUESTION || SmartFinancePrompts.isTaxLawQuery(query) || isGstQuery(query)) return null
        val u = ALL_UNIT_REGEX_PATTERN
        val isPerUnit = Regex("\\b(?:per\\s+(?:piece|peice|pc|pcs|item|unit|kg|kilo|bori|bag|peti|box|bottle|btl|meter|mtr|darjan|dozen|doz|litre|liter|ltr)|each|prati|pratee|hisaab\\s+se|hisab\\s+se|rate\\s+se|dar\\s+se)\\b|@\\s*\\d", RegexOption.IGNORE_CASE).containsMatchIn(query)
        val perUnitSuffix = "(?:per\\s+(?:piece|peice|pc|pcs|item|unit|kg|kilo|bori|bag|peti|box|bottle|btl|meter|mtr|darjan|dozen|doz|litre|liter|ltr)|each|prati|pratee|ke\\s+hisaab\\s+se|ke\\s+hisab\\s+se|hisaab\\s+se|hisab\\s+se|rate\\s+se|dar\\s+se)?"
        val amtSuffix = "(?:(?:for|at|of|in|rs|rupees|rupaye|@|mein|me|ka|ki|ke)?\\s*(?:rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|r|/-)?(?:\\s*$perUnitSuffix)?(?:\\s*(?:me|mein|mai|par|pe|ke|ki|ka))?|(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|r|/-)?(?:\\s*$perUnitSuffix)?(?:\\s*(?:me|mein|mai|par|pe|ke|ki|ka))?)"
        val extractedDate = parseDateFromQuery(query) ?: "today"

        val multiVoucher = parseMultiItemTransaction(context, q, extractedDate, isPerUnit, u)
        if (multiVoucher != null) return multiVoucher

        fun extractUnitFromItemOrQuery(itemStr: String, queryStr: String): Pair<String, String?> {
            return extractHinglishUnitAndItem(itemStr, queryStr)
        }

        suspend fun makeSaleVoucher(txn: ParsedTxn): AiResponse.DraftVoucher {
            val (cleanItem, unit) = extractUnitFromItemOrQuery(txn.item, query)
            val translated = LanguageTranslationUtils.translateItem(cleanItem)
            val (finalAmt, rate) = if (isPerUnit && txn.qty > 0) {
                (txn.amt * txn.qty) to txn.amt
            } else {
                txn.amt to (if (txn.qty > 0) txn.amt / txn.qty else 0.0)
            }
            val p = cleanPartyName(txn.party) ?: "Cash-in-hand"
            val cat = LanguageTranslationUtils.suggestCategory(translated)
            val resolved = VoucherExcelImporter.resolveHsnAndTax(translated, cat, null, null, context)
            val hsn = resolved?.first
            val gst = resolved?.second ?: resolveTaxRate(context, translated)
            val inv = listOf(DraftInventoryItem(translated, txn.qty, rate, finalAmt, unit, gst, cat, hsn))
            return AiResponse.DraftVoucher("Sales", extractedDate, null, listOf(DraftEntry(p, true, finalAmt), DraftEntry("Sales Account", false, finalAmt)), inv, q, p, finalAmt)
        }

        suspend fun makePurchaseVoucher(txn: ParsedTxn): AiResponse.DraftVoucher {
            val (cleanItem, unit) = extractUnitFromItemOrQuery(txn.item, query)
            val translated = LanguageTranslationUtils.translateItem(cleanItem)
            val (finalAmt, rate) = if (isPerUnit && txn.qty > 0) {
                (txn.amt * txn.qty) to txn.amt
            } else {
                txn.amt to (if (txn.qty > 0) txn.amt / txn.qty else 0.0)
            }
            val p = cleanPartyName(txn.party) ?: "Cash-in-hand"
            val cat = LanguageTranslationUtils.suggestCategory(translated)
            val resolved = VoucherExcelImporter.resolveHsnAndTax(translated, cat, null, null, context)
            val hsn = resolved?.first
            val gst = resolved?.second ?: resolveTaxRate(context, translated)
            val inv = listOf(DraftInventoryItem(translated, txn.qty, rate, finalAmt, unit, gst, cat, hsn))
            return AiResponse.DraftVoucher("Purchase", extractedDate, null, listOf(DraftEntry("Purchase Account", true, finalAmt), DraftEntry(p, false, finalAmt)), inv, q, p, finalAmt)
        }

        fun cleanParty(raw: String): String {
            return cleanPartyName(raw) ?: ""
        }

        // Multi-party transfer: "kaale ko 10000 diye ritu se lekar" / "ritu se 10000 lekar kaale ko diye"
        val transferRegexes = listOf<Pair<Regex, (MatchResult) -> Pair<Pair<String, String>, Double>>>(
            Regex("([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+ko\\s+(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye)?\\s*(?:paid|pay|diya|diye|given)?\\s+([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+se\\s*(?:lekar|leker|liya|liye|receive|taking|take)?", RegexOption.IGNORE_CASE) to { m ->
                (m.groupValues[1].trim() to m.groupValues[3].trim()) to (m.groupValues[2].toDoubleOrNull() ?: 0.0)
            },
            Regex("([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+se\\s+(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye)?\\s*(?:lekar|leker|liya|liye|receive karke|taking|take)?\\s+([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+ko\\s*(?:paid|pay|diya|diye|given)?", RegexOption.IGNORE_CASE) to { m ->
                (m.groupValues[3].trim() to m.groupValues[1].trim()) to (m.groupValues[2].toDoubleOrNull() ?: 0.0)
            },
            Regex("(?:transfer)?\\s*(?:rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:from)\\s+([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+(?:to)\\s+([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)", RegexOption.IGNORE_CASE) to { m ->
                (m.groupValues[3].trim() to m.groupValues[2].trim()) to (m.groupValues[1].toDoubleOrNull() ?: 0.0)
            }
        )
        for ((regex, extract) in transferRegexes) {
            val m = regex.find(q) ?: continue
            val (parties, amount) = extract(m)
            val (toPartyRaw, fromPartyRaw) = parties
            val toParty = cleanParty(toPartyRaw)
            val fromParty = cleanParty(fromPartyRaw)
            if (toParty.isNotBlank() && fromParty.isNotBlank() && amount > 0) {
                return AiResponse.DraftVoucher("Journal", extractedDate, null, listOf(DraftEntry(toParty, true, amount), DraftEntry(fromParty, false, amount)), emptyList(), q, toParty, amount)
            }
        }

        val saleRegexes = listOf<Pair<Regex, (MatchResult) -> ParsedTxn>>(
            Regex("^\\s*(?:sold|sale|sales|sell)\\s+(?:to)\\s+([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn("Goods", g[1].trim(), extractAmt(g, 2, 3), 1.0) },
            Regex("^\\s*(?:sold|sale|sales|sell)\\s+(?:to)\\s+([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+(?:(\\d+(?:\\.\\d+)?)\\s+(?:$u\\s+)?)?(.+?)\\s+$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[3].trim(), g[1].trim(), extractAmt(g, 4, 5), g[2].toDoubleOrNull() ?: 1.0) },
            Regex("^\\s*(?:sold|sale|sales|sell)\\s+(\\d+(?:\\.\\d+)?)\\s+(?:$u\\s+)?(.+?)\\s+(?:to)\\s+([a-zA-Z\u0900-\u097F\\s]+?)\\s+$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[2].trim(), g[3].trim(), extractAmt(g, 4, 5), g[1].toDoubleOrNull() ?: 1.0) },
            Regex("^\\s*(?:sold|sale|sales|sell)\\s+(.+?)\\s+(?:to)\\s+([a-zA-Z\u0900-\u097F\\s]+?)\\s+$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[1].trim(), g[2].trim(), extractAmt(g, 3, 4), 1.0) },
            Regex("^\\s*(?:sold|sale|sales|sell)\\s+(\\d+(?:\\.\\d+)?)\\s+(?:$u\\s+)?(.+?)\\s+$amtSuffix\\s+(?:to)\\s+([a-zA-Z\u0900-\u097F\\s]+)$", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[2].trim(), g[5].trim(), extractAmt(g, 3, 4), g[1].toDoubleOrNull() ?: 1.0) },
            Regex("^\\s*(?:sold|sale|sales|sell)\\s+(.+?)\\s+$amtSuffix\\s+(?:to)\\s+([a-zA-Z\u0900-\u097F\\s]+)$", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[1].trim(), g[4].trim(), extractAmt(g, 2, 3), 1.0) },
            Regex("([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+ko\\s+(\\d+(?:\\.\\d+)?)\\s*(?:ka)?\\s*(?:maal)?\\s*(?:sold|sale|sales|sell)", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn("Goods", g[1].trim(), g[2].toDoubleOrNull() ?: 0.0, 1.0) },
            Regex("([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+ko\\s+(?:(\\d+(?:\\.\\d+)?)\\s+(?:$u\\s+)?)?(.+?)\\s+(?:sale|sales|sold|sell)\\s*$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[3].trim(), g[1].trim(), extractAmt(g, 4, 5), g[2].toDoubleOrNull() ?: 1.0) },
            Regex("^\\s*(?:sold|sale|sales|sell)\\s+(\\d+(?:\\.\\d+)?)\\s+(?:$u\\s+)?(.+?)\\s+$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[2].trim(), "Cash-in-hand", extractAmt(g, 3, 4), g[1].toDoubleOrNull() ?: 1.0) },
            Regex("^\\s*(?:sold|sale|sales|sell)\\s+(.+?)\\s+$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[1].trim(), "Cash-in-hand", extractAmt(g, 2, 3), 1.0) },
            Regex("(\\d+(?:\\.\\d+)?)\\s+(?:$u\\s+)?(.+?)\\s+(?:sold|sale|sales|sell)\\s*$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[2].trim(), "Cash-in-hand", extractAmt(g, 3, 4), g[1].toDoubleOrNull() ?: 1.0) },
            Regex("(.+?)\\s+(?:sold|sale|sales|sell)\\s*$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[1].trim(), "Cash-in-hand", extractAmt(g, 2, 3), 1.0) }
        )
        for ((regex, extract) in saleRegexes) { val m = regex.find(q) ?: continue; val txn = extract(m); if (txn.item.isBlank() || txn.amt <= 0) continue; return makeSaleVoucher(txn) }

        val buyRegexes = listOf<Pair<Regex, (MatchResult) -> ParsedTxn>>(
            Regex("^\\s*(?:bought|buy|purchase|purchased)\\s+(?:from|se)\\s+([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn("Goods", g[1].trim(), extractAmt(g, 2, 3), 1.0) },
            Regex("^\\s*(?:bought|buy|purchase|purchased)\\s+(?:from|se)\\s+([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+(?:(\\d+(?:\\.\\d+)?)\\s+(?:$u\\s+)?)?(.+?)\\s+$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[3].trim(), g[1].trim(), extractAmt(g, 4, 5), g[2].toDoubleOrNull() ?: 1.0) },
            Regex("^\\s*(?:bought|buy|purchase|purchased)\\s+(\\d+(?:\\.\\d+)?)\\s+(?:$u\\s+)?(.+?)\\s+$amtSuffix\\s+(?:from|se)\\s+([a-zA-Z\u0900-\u097F\\s]+)$", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[2].trim(), g[5].trim(), extractAmt(g, 3, 4), g[1].toDoubleOrNull() ?: 1.0) },
            Regex("^\\s*(?:bought|buy|purchase|purchased)\\s+(.+?)\\s+$amtSuffix\\s+(?:from|se)\\s+([a-zA-Z\u0900-\u097F\\s]+)$", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[1].trim(), g[4].trim(), extractAmt(g, 2, 3), 1.0) },
            Regex("^\\s*(?:bought|buy|purchase|purchased)\\s+(\\d+(?:\\.\\d+)?)\\s+(?:$u\\s+)?(.+?)\\s+(?:from|se)\\s+(.+?)\\s+$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[2].trim(), g[3].trim(), extractAmt(g, 4, 5), g[1].toDoubleOrNull() ?: 1.0) },
            Regex("^\\s*(?:bought|buy|purchase|purchased)\\s+(.+?)\\s+(?:from|se)\\s+(.+?)\\s+$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[1].trim(), g[2].trim(), extractAmt(g, 3, 4), 1.0) },
            Regex("(?:(\\d+(?:\\.\\d+)?)\\s+(?:$u\\s+)?)?(.+?)\\s+(?:bought|buy|purchase|purchased)\\s+([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+(?:se|from)\\s+$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[2].trim(), g[3].trim(), extractAmt(g, 4, 5), g[1].toDoubleOrNull() ?: 1.0) },
            Regex("([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+se\\s+(\\d+(?:\\.\\d+)?)\\s*(?:ka)?\\s*(?:maal)?\\s*(?:bought|purchase|purchased)", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn("Goods", g[1].trim(), g[2].toDoubleOrNull() ?: 0.0, 1.0) },
            Regex("([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+)?)\\s+se\\s+(?:(\\d+(?:\\.\\d+)?)\\s+(?:$u\\s+)?)?(.+?)\\s+(?:bought|buy|purchase|purchased)\\s*$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[3].trim(), g[1].trim(), extractAmt(g, 4, 5), g[2].toDoubleOrNull() ?: 1.0) },
            Regex("^\\s*(?:bought|buy|purchase|purchased)\\s+(\\d+(?:\\.\\d+)?)\\s+(?:$u\\s+)?(.+?)\\s+$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[2].trim(), "Cash-in-hand", extractAmt(g, 3, 4), g[1].toDoubleOrNull() ?: 1.0) },
            Regex("^\\s*(?:bought|buy|purchase|purchased)\\s+(.+?)\\s+$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[1].trim(), "Cash-in-hand", extractAmt(g, 2, 3), 1.0) },
            Regex("(\\d+(?:\\.\\d+)?)\\s+(?:$u\\s+)?(.+?)\\s+(?:bought|buy|purchase|purchased)\\s*$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[2].trim(), "Cash-in-hand", extractAmt(g, 3, 4), g[1].toDoubleOrNull() ?: 1.0) },
            Regex("(.+?)\\s+(?:bought|buy|purchase|purchased)\\s*$amtSuffix", RegexOption.IGNORE_CASE) to { m -> val g = m.groupValues; ParsedTxn(g[1].trim(), "Cash-in-hand", extractAmt(g, 2, 3), 1.0) }
        )
        for ((regex, extract) in buyRegexes) { val m = regex.find(q) ?: continue; val txn = extract(m); if (txn.item.isBlank() || txn.amt <= 0) continue; return makePurchaseVoucher(txn) }


        val payRegexes = listOf<Pair<Regex, (MatchResult) -> Pair<String, Double>>>(
            Regex("(.+?)(?:\\s+ko)?\\s+(?:rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye)?\\s+\\b(?:paid|pay|given)\\b", RegexOption.IGNORE_CASE) to { m -> m.groupValues[1].trim() to (m.groupValues[2].toDoubleOrNull() ?: 0.0) },
            Regex("\\b(?:paid|pay|payment|given)\\b\\s+(?:to\\s+)?(.+?)\\s+(?:(?:for|at|rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?))", RegexOption.IGNORE_CASE) to { m -> m.groupValues[1].trim() to (m.groupValues[2].toDoubleOrNull() ?: 0.0) },
            Regex("\\b(?:paid|pay|payment|given)\\b\\s+(\\d+(?:\\.\\d+)?)\\s+(?:(?:to|ko)\\s+)?(.+)", RegexOption.IGNORE_CASE) to { m -> m.groupValues[2].trim() to (m.groupValues[1].toDoubleOrNull() ?: 0.0) },
            Regex("(.+?)(?:\\s+ko)?\\s+\\b(?:paid|pay|given)\\b\\s+(?:(?:for|at|rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?))", RegexOption.IGNORE_CASE) to { m -> m.groupValues[1].trim() to (m.groupValues[2].toDoubleOrNull() ?: 0.0) }
        )
        for ((regex, extract) in payRegexes) { val m = regex.find(q) ?: continue; val (party, amount) = extract(m); val p = cleanParty(party); if (p.isBlank() || amount <= 0) continue; return AiResponse.DraftVoucher("Payment", extractedDate, null, listOf(DraftEntry(p, true, amount), DraftEntry(resolvePaymentAccount(q), false, amount)), emptyList(), q, p, amount) }

        val recvRegexes = listOf<Pair<Regex, (MatchResult) -> Pair<String, Double>>>(
            Regex("(.+?)(?:\\s+se)?\\s+(?:rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye)?\\s+\\b(?:received|got)\\b", RegexOption.IGNORE_CASE) to { m -> m.groupValues[1].trim() to (m.groupValues[2].toDoubleOrNull() ?: 0.0) },
            Regex("\\b(?:received|got|receipt)\\b\\s+(?:from\\s+)?(.+?)\\s+(?:(?:for|at|rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?))", RegexOption.IGNORE_CASE) to { m -> m.groupValues[1].trim() to (m.groupValues[2].toDoubleOrNull() ?: 0.0) },
            Regex("\\b(?:received|got|receipt)\\b\\s+(\\d+(?:\\.\\d+)?)\\s+(?:(?:from|se)\\s+)?(.+)", RegexOption.IGNORE_CASE) to { m -> m.groupValues[2].trim() to (m.groupValues[1].toDoubleOrNull() ?: 0.0) },
            Regex("(.+?)(?:\\s+se)?\\s+\\b(?:received|got)\\b\\s+(?:(?:for|at|rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?))", RegexOption.IGNORE_CASE) to { m -> m.groupValues[1].trim() to (m.groupValues[2].toDoubleOrNull() ?: 0.0) }
        )
        for ((regex, extract) in recvRegexes) { val m = regex.find(q) ?: continue; val (party, amount) = extract(m); val p = cleanParty(party); if (p.isBlank() || amount <= 0) continue; return AiResponse.DraftVoucher("Receipt", extractedDate, null, listOf(DraftEntry(resolvePaymentAccount(q), true, amount), DraftEntry(p, false, amount)), emptyList(), q, p, amount) }
        return null
    }

    /**
     * Public entry point for voice/text → voucher draft, used by the voucher entry screen's
     * mic button. Reuses the exact same offline Hinglish/English parser as the Finance Helper chat,
     * so accuracy is identical. Returns null if nothing usable could be parsed.
     */
    suspend fun parseSpeechToDraft(context: Context?, text: String): AiResponse.DraftVoucher? =
        (tryParseHinglish(context, text) as? AiResponse.DraftVoucher) ?: smartRegexParser(context, text)

    private fun sha256(input: String): String = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun cachePrefs(context: Context): SharedPreferences = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    private fun getCachedJson(context: Context, normalizedQuery: String): String? { val key = sha256(normalizedQuery); val raw = cachePrefs(context).getString(key, null) ?: return null; return if (raw.startsWith(CACHE_VALUE_PREFIX)) raw.removePrefix(CACHE_VALUE_PREFIX) else null }
    private fun putCachedJson(context: Context, normalizedQuery: String, json: String) { val key = sha256(normalizedQuery); cachePrefs(context).edit().putString(key, "$CACHE_VALUE_PREFIX$json").apply() }

    fun guessTaxRate(context: Context?, itemName: String?): Double {
        val country = context?.let { DashboardPrefs.getTaxCountry(it) } ?: "India"
        return TaxConfigManager.rateForItem(country, itemName)
    }

    private fun getLocalHeuristicRate(country: String, itemName: String?): Double {
        val item = itemName?.lowercase(Locale.getDefault())?.trim().orEmpty()
        val config = TaxConfigManager.getConfigForCountry(country)
        if (config.standardRates.isEmpty()) return 0.0
        
        val isFoodOrBasic = item.contains("rice") || item.contains("wheat") || item.contains("dal") || 
                            item.contains("flour") || item.contains("milk") || item.contains("vegetable") || 
                            item.contains("fruit") || item.contains("book") || item.contains("medicine")
        
        return when (country) {
            "India" -> {
                when {
                    isFoodOrBasic -> 0.0
                    item.contains("sugar") || item.contains("tea") || item.contains("coffee") || item.contains("sweet") || LanguageTranslationUtils.isFivePercentOil(item) || item.contains("transport") || item.contains("fabric") || item.contains("butter") || item.contains("cheese") || item.contains("ghee") -> 5.0
                    // GST 2.0 (Sep 2025): sin & luxury goods sit in the 40% slab
                    item.contains("cigarette") || item.contains("tobacco") || item.contains("pan masala") || item.contains("gutkha") || item.contains("aerated") || item.contains("carbonated") || item.contains("cola") || item.contains("soft drink") -> 40.0
                    // GST 2.0: the old 28% durables slab collapsed into 18%
                    item.contains("service") || item.contains("consult") || item.contains("repair") || item.contains("maintenance") || item.contains("software") || item.contains("monitor") || item.contains("printer") || item.contains("mobile") || item.contains("computer") || item.contains("laptop") || item.contains("car") || item.contains("ac") || item.contains("air condition") || item.contains("fridge") || item.contains("refrigerator") || item.contains("cement") || item.contains("tv") || item.contains("television") || item.contains("washing machine") || item.contains("tyre") || item.contains("tire") -> 18.0
                    else -> 18.0
                }
            }
            "United Kingdom" -> {
                when {
                    isFoodOrBasic || item.contains("clothing") -> 0.0
                    item.contains("fuel") || item.contains("electricity") || item.contains("heating") || item.contains("energy") -> 5.0
                    else -> 20.0
                }
            }
            "Canada" -> {
                when {
                    isFoodOrBasic -> 0.0
                    else -> config.defaultRate
                }
            }
            "Germany" -> {
                when {
                    isFoodOrBasic -> 7.0
                    else -> 19.0
                }
            }
            "France" -> {
                when {
                    isFoodOrBasic -> 5.5
                    item.contains("service") || item.contains("transport") -> 10.0
                    else -> 20.0
                }
            }
            "Italy" -> {
                when {
                    isFoodOrBasic -> 4.0
                    item.contains("service") || item.contains("transport") -> 10.0
                    else -> 22.0
                }
            }
            "Spain" -> {
                when {
                    isFoodOrBasic -> 4.0
                    item.contains("service") || item.contains("transport") -> 10.0
                    else -> 21.0
                }
            }
            else -> {
                if (isFoodOrBasic && config.standardRates.contains(0.0)) {
                    0.0
                } else {
                    config.defaultRate
                }
            }
        }
    }

    private suspend fun resolveTaxRate(context: Context?, itemName: String): Double {
        return guessTaxRate(context, itemName)
    }

    object ConversationStateManager {
        data class ConversationState(val context: ContextType, val partialData: MutableMap<String, Any> = mutableMapOf())
        enum class ContextType { NONE, CREATING_VOUCHER, CREATING_LEDGER, FINANCE_WORKFLOW }
        private var currentState: ConversationState = ConversationState(ContextType.NONE)
        fun getState(): ConversationState = currentState
        fun setState(context: ContextType) { currentState = ConversationState(context) }
        fun updateData(key: String, value: Any) { currentState.partialData[key] = value }
        fun clear() { currentState = ConversationState(ContextType.NONE) }
    }

    private fun extractParty(q: String): String? {
        val p1 = Pattern.compile("(?:to|from|for|by|party)\\s+([a-zA-Z]+(?:\\s+[a-zA-Z]+){0,2})", Pattern.CASE_INSENSITIVE)
        val m1 = p1.matcher(q)
        if (m1.find()) {
            val candidate = cleanCandidate(m1.group(1)?.trim())
            if (candidate != null && !candidate.lowercase().matches(Regex("cash|bank|gpay|upi|card|online|cheque|draft|the|and|for|with|via|rs|rupees"))) return candidate
        }
        val p2 = Pattern.compile("([a-zA-Z\u0900-\u097F]+(?:\\s+[a-zA-Z\u0900-\u097F]+){0,2})\\s+(?:ko|se|ne|ka|ke|ki)", Pattern.CASE_INSENSITIVE)
        val m2 = p2.matcher(q)
        if (m2.find()) {
            val candidate = m2.group(1)?.trim()
            if (!candidate.isNullOrBlank() && !isFillerOrStopWord(candidate)) return candidate.replaceFirstChar { it.uppercase() }
        }
        return null
    }

    private fun cleanCandidate(candidate: String?): String? {
        if (candidate == null) return null
        val words = candidate.split(Regex("\\s+"))
        val stopWords = setOf("for", "to", "from", "and", "by", "with", "rs", "rupees", "rupaye", "sold", "bought", "cash", "of", "in", "via", "at")
        val cleanedWords = mutableListOf<String>()
        for (word in words) {
            if (word.lowercase(Locale.ROOT) in stopWords) break
            cleanedWords.add(word)
        }
        if (cleanedWords.isEmpty()) return null
        return cleanedWords.joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    private fun createEntriesWithTax(amount: Double, taxAmount: Double, mainLedger: String, partyLedger: String, isDebitMain: Boolean): List<DraftEntry> {
        val entries = mutableListOf<DraftEntry>()
        val baseAmount = if (taxAmount > 0.0) amount - taxAmount else amount
        entries.add(DraftEntry(partyLedger, !isDebitMain, amount))
        entries.add(DraftEntry(mainLedger, isDebitMain, baseAmount))
        if (taxAmount > 0.0) {
            val (cgst, sgst) = GstComplianceRules.splitTax(taxAmount)
            entries.add(DraftEntry("CGST", isDebitMain, cgst))
            entries.add(DraftEntry("SGST", isDebitMain, sgst))
        }
        return entries
    }

    private fun extractItemAfterKo(q: String): String? {
        val m = Regex("\\bko\\s+([a-zA-Zऀ-ॿ]+(?:\\s+[a-zA-Zऀ-ॿ]+)?)\\s+(?:bechi|becha|beche|bech|becho|sold|sale|sales|bikri|kharidi|kharida|kharide|bought|purchase|purchased)", RegexOption.IGNORE_CASE).find(q)
        val item = m?.groupValues?.get(1)?.trim()
        return item?.takeIf { it.isNotBlank() && !Regex(".*\\d.*").matches(it) }
    }

    fun parseVoiceCommandLocal(query: String): AiResponse? {
        if (IntentClassifier.classify(query) == IntentClassifier.Intent.QUESTION || SmartFinancePrompts.isTaxLawQuery(query) || isGstQuery(query)) return null
        val q = query.lowercase().trim().replace("rupees", "").replace(Regex("\\brs\\b"), "").replace(" gpay ", " paid ").replace(" phonepe ", " paid ").replace("paytm", "paid").replace("upi", "paid").replace("neft", "cheque").replace("rtgs", "cheque").replace("downpayment", "down payment").replace("down-payment", "down payment")
        if (q.contains("what can you do") || q.contains("help")) return null
        val extractedDate = parseDateFromQuery(query) ?: "today"
        val amounts = mutableListOf<Double>()
        val amountMatcher = Pattern.compile("(\\d[\\d,]*\\.?[\\d]*)").matcher(q)
        while (amountMatcher.find()) {
            val raw = amountMatcher.group(1) ?: ""; val clean = raw.replace(",", "")
            if (clean.length < 10 && clean.count { it == '.' } <= 1) { clean.toDoubleOrNull()?.let { if (it > 0) amounts.add(it) } }
        }
        val amount = amounts.maxOrNull() ?: 0.0
        val taxAmount = if (amounts.size > 1 && (q.contains("tax") || q.contains("gst"))) { amounts.filter { it < amount }.maxOrNull() ?: 0.0 } else 0.0
        val state = ConversationStateManager.getState()
        if (state.context == ConversationStateManager.ContextType.FINANCE_WORKFLOW) {
            val assetName = state.partialData["asset"] as? String; val bankName = state.partialData["bank"] as? String; val loanAmount = state.partialData["amount"] as? Double ?: 0.0
            if (loanAmount == 0.0 && amount > 0) { ConversationStateManager.updateData("amount", amount); return AiResponse.Message("OK, Loan amount is $amount. Was there any down payment? If yes, say the amount.") }
            if (loanAmount > 0 && amount > 0) { ConversationStateManager.clear(); return AiResponse.DraftVoucher("Journal", extractedDate, null, listOf(DraftEntry(assetName ?: "Vehicle", true, loanAmount + amount), DraftEntry(bankName ?: "Bank Loan", false, loanAmount), DraftEntry("Cash-in-hand", false, amount)), emptyList(), "Loan for $assetName from $bankName with down payment") }
            if (q.contains("no") || q.contains("zero") || q.contains("nothing")) { ConversationStateManager.clear(); return AiResponse.DraftVoucher("Journal", extractedDate, null, listOf(DraftEntry(assetName ?: "Vehicle", true, loanAmount), DraftEntry(bankName ?: "Bank Loan", false, loanAmount)), emptyList(), "Loan for $assetName from $bankName (No Down Payment)") }
        }
        val isPayment = q.contains("pay") || q.contains("paid") || q.contains("diya") || q.contains("diye") || q.contains("expense") || q.contains("kharcha") || q.contains("salary") || q.contains("given") || q.contains("gave")
        val isReceipt = q.contains("receive") || q.contains("got") || q.contains("liya") || q.contains("liye") || q.contains("lena") || q.contains("lene") || q.contains("leni") || q.contains("aaya") || q.contains("aaye") || q.contains("income") || q.contains("mila") || q.contains("mile") || q.contains("taken") || q.contains("took")
        // Word-boundary match — a plain .contains("sale") also matches inside unrelated words
        // like "wholesaler", misclassifying a purchase-from-wholesaler as a Sale.
        val isSale = Regex("\\b(?:sold|sale|sales|sell|becha|beche|bechi|bech|bikri|bill|invoice)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
        val isPurchase = q.contains("purchased") || q.contains("bought") || q.contains("kharida") || q.contains("kharide") || q.contains("kharidi") || q.contains("stock in") || q.contains("maal aaya")
        val ledgerNouns = listOf("party", "parties", "customer", "customers", "supplier", "suppliers", "debtor", "debtors", "creditor", "creditors", "ledger", "ledgers", "khata")
        val ledgerVerbs = listOf("create", "add", "make", "new", "banao", "bana", "kholo")
        val hasLedgerVerb = ledgerVerbs.any { Regex("\\b$it\\b").containsMatchIn(q) }
        val hasLedgerNoun = ledgerNouns.any { Regex("\\b$it\\b").containsMatchIn(q) }
        val isTransactionLike = q.contains("sale") || q.contains("sold") || q.contains("bill") || q.contains("invoice") || q.contains("purchase") || q.contains("payment") || q.contains("paid") || q.contains("receipt") || q.contains("received") || q.contains("diya") || q.contains("liya") || q.contains("becha") || q.contains("kharida")
        val isLedger = q.contains("new party") || q.contains("khata kholo") || q.contains("create account") || q.contains("add ledger") || q.contains("create ledger") || q.contains("make ledger") || q.contains("create ledgers") ||
            (hasLedgerVerb && hasLedgerNoun && !isTransactionLike)
        val isFinance = (q.contains("financed") || q.contains("loan") || q.contains("emi") || q.contains("down payment") || q.contains("finance")) && !isLedger && !q.contains("create")
        if (isLedger) {
            val names = extractLedgerNames(q)
            val group = when { q.contains("supplier") || q.contains("creditor") -> "Sundry Creditors"; q.contains("customer") || q.contains("debtor") -> "Sundry Debtors"; q.contains("finance") || q.contains("loan") || q.contains("bank") -> "Sundry Creditors"; else -> "Sundry Debtors" }
            val actions = names.map { AiResponse.DraftLedger(it.replaceFirstChar { char -> char.uppercase() }, group, 0.0) }
            if (actions.isEmpty()) return AiResponse.Message("Sure! What name should I use for the new ${if (group == "Sundry Creditors") "supplier" else "customer"}?")
            return if (actions.size == 1) actions.first() else AiResponse.MultiAction(actions)
        }
        if (isFinance) {
            val bankName = when { q.contains("idfc") -> "IDFC Bank Loan"; q.contains("bajaj") -> "Bajaj Finance Loan"; q.contains("hdfc") -> "HDFC Bank Loan"; q.contains("sbi") -> "SBI Loan"; else -> extractParty(q) ?: "Bank Loan" }
            val assetName = extractNameFromQuery(q, listOf("financed", "loan", "by", "from", "idfc", "bajaj", "hdfc", "bank", "vehicle", "splendor", "bike", "car")) ?: "Vehicle"
            ConversationStateManager.setState(ConversationStateManager.ContextType.FINANCE_WORKFLOW); ConversationStateManager.updateData("bank", bankName); ConversationStateManager.updateData("asset", assetName)
            if (amount <= 0) return AiResponse.Message("I see you financed a $assetName from $bankName. But what is the loan amount?") else { ConversationStateManager.updateData("amount", amount); return AiResponse.Message("OK, $bankName financed $amount for $assetName. Was there any down payment? If yes, say the amount.") }
        }
        if (isPayment) {
            val expenseType = when { q.contains("rent") || q.contains("bhada") -> "Rent Expense"; q.contains("salary") || q.contains("pagar") -> "Salary Expense"; q.contains("chai") || q.contains("tea") -> "Staff Welfare"; q.contains("petrol") || q.contains("fuel") || q.contains("diesel") -> "Conveyance Expense"; q.contains("bijli") || q.contains("light") -> "Electricity Expense"; else -> null }
            if (expenseType != null && amount > 0) return AiResponse.DraftVoucher("Payment", extractedDate, null, listOf(DraftEntry(expenseType, true, amount), DraftEntry(resolvePaymentAccount(q), false, amount)), emptyList(), query)
        }
        if (isSale) { if (amount <= 0) return AiResponse.Message("I understood you sold something, but I missed the amount. How much was it?"); val party = extractParty(q) ?: "Cash-in-hand"; val soldItem = extractItemAfterKo(q)?.let { LanguageTranslationUtils.translateItem(it) }; val inventory = if (soldItem != null) listOf(DraftInventoryItem(soldItem, 1.0, amount, amount)) else emptyList(); val entries = createEntriesWithTax(amount, taxAmount, "Sales Account", party, false); return AiResponse.DraftVoucher("Sales", extractedDate, null, entries, inventory, query, party, amount) }
        if (isPurchase) { if (amount <= 0) return AiResponse.Message("I understood you bought something, but how much did it cost?"); val party = extractParty(q) ?: "Cash-in-hand"; val boughtItem = extractItemAfterKo(q)?.let { LanguageTranslationUtils.translateItem(it) }; val inventory = if (boughtItem != null) listOf(DraftInventoryItem(boughtItem, 1.0, amount, amount)) else emptyList(); val entries = createEntriesWithTax(amount, taxAmount, "Purchase Account", party, true); return AiResponse.DraftVoucher("Purchase", extractedDate, null, entries, inventory, query, party, amount) }
        if (isPayment) { if (amount <= 0) return AiResponse.Message("I got that you made a payment, but what was the amount?"); val party = extractParty(q); if (party == null) return AiResponse.Message("I got the amount $amount, but who did you pay?"); return AiResponse.DraftVoucher("Payment", extractedDate, null, listOf(DraftEntry(party, true, amount), DraftEntry(resolvePaymentAccount(q), false, amount)), emptyList(), query, party, amount) }
        if (isReceipt) { if (amount <= 0) return AiResponse.Message("I understood you received money, but how much?"); val party = extractParty(q); if (party == null) return AiResponse.Message("I got the amount $amount, but who did you receive it from?"); return AiResponse.DraftVoucher("Receipt", extractedDate, null, listOf(DraftEntry(resolvePaymentAccount(q), true, amount), DraftEntry(party, false, amount)), emptyList(), query, party, amount) }
        if (q.contains("sale") || q.contains("bikri")) { val periodVal = when { q.contains("today") || q.contains("aaj") -> "today"; q.contains("month") || q.contains("mahina") -> "month"; else -> "total" }; return AiResponse.QuerySales(periodVal) }
        if (q.contains("purchase") || q.contains("kharid")) { val periodVal = when { q.contains("today") || q.contains("aaj") -> "today"; q.contains("month") || q.contains("mahina") -> "month"; else -> "total" }; return AiResponse.QueryMetric(metric = "purchase", period = periodVal) }
        return null
    }

    fun parseReportCommandLocal(query: String): AiResponse? {
        val q = query.lowercase().trim().replace("rupees", "").replace(Regex("\\brs\\b"), "").replace(" gpay ", " paid ").replace(" phonepe ", " paid ").replace("paytm", "paid").replace("upi", "paid").replace("neft", "cheque").replace("rtgs", "cheque")
        val amounts = mutableListOf<Double>()
        val amountMatcher = Pattern.compile("(\\d[\\d,]*\\.?[\\d]*)").matcher(q)
        while (amountMatcher.find()) { val raw = amountMatcher.group(1) ?: ""; val clean = raw.replace(",", ""); if (clean.length < 10 && clean.count { it == '.' } <= 1) { clean.toDoubleOrNull()?.let { if (it > 0) amounts.add(it) } } }
        val amount = amounts.maxOrNull() ?: 0.0
        fun detectPeriod(): String? { return when { q.contains("yesterday") || q.contains("kal") -> "yesterday"; q.contains("today") || q.contains("aaj") -> "today"; q.contains("last month") || q.contains("pichla mahina") || q.contains("pichle mahine") -> "last_month"; q.contains("month") || q.contains("mahina") -> "month"; q.contains("overall") || q.contains("all time") || q.contains("total") -> "total"; else -> null } }
        val period = detectPeriod()
        val limit = run { val m = Pattern.compile("\\b(\\d{1,3})\\b").matcher(q); if (m.find()) m.group(1)?.toIntOrNull()?.coerceIn(1, 50) else null }
        if (q.contains("collect") || q.contains("lena hai") || q.contains("debtors") || q.contains("receivable")) return AiResponse.QueryCollectables
        if (q.contains("who owes") || q.contains("list receivable") || q.contains("receivable list") || q.contains("debtors list")) return AiResponse.QueryCollectablesList
        if (q.contains("payable") || (q.contains("pay") && (q.contains("total") || q.contains("summary") || q.contains("creditors")))) { if (!q.contains("paid") && !q.contains("receivable") && !q.contains("list") && !q.contains("gst") && !q.contains("tax")) return AiResponse.QueryPayables }
        if (q.contains("who to pay") || q.contains("list payable") || q.contains("payable list") || q.contains("creditors list") || q.contains("dena hai kisko")) return AiResponse.QueryPayablesList
        if (q.contains("summary") || q.contains("report")) return AiResponse.QuerySummary
        if (q.contains("profit") || q.contains("faida") || q.contains("munafa") || q.contains("margin")) return AiResponse.QueryMetric(metric = "profit", period = period)
        if ((q.contains("sale") || q.contains("bikri") || q.contains("sold")) && (q.contains("how much") || q.contains("kitni") || q.contains("kitna") || q.contains("total"))) return AiResponse.QueryMetric(metric = "sales", period = period)
        if ((q.contains("purchase") || q.contains("kharid") || q.contains("kharida") || q.contains("bought")) && (q.contains("how much") || q.contains("kitna") || q.contains("total"))) return AiResponse.QueryMetric(metric = "purchase", period = period)
        if ((q.contains("receipt") || q.contains("received") || q.contains("aaya") || q.contains("mila")) && (q.contains("how much") || q.contains("kitna") || q.contains("total"))) return AiResponse.QueryMetric(metric = "receipt", period = period)
        if ((q.contains("payment") || q.contains("paid") || q.contains("diya") || q.contains("kharcha")) && (q.contains("how much") || q.contains("kitna") || q.contains("total"))) return AiResponse.QueryMetric(metric = "payment", period = period)
        if ((q.contains("cash") || q.contains("cash-in-hand")) && (q.contains("how much") || q.contains("kitna") || q.contains("balance"))) return AiResponse.QueryMetric(metric = "cash", period = null)
        if (q.contains("bank") && (q.contains("how much") || q.contains("kitna") || q.contains("balance"))) return AiResponse.QueryMetric(metric = "bank", period = null)
        if ((q.contains("gst") || q.contains("tax")) && (q.contains("payable") || q.contains("liability") || q.contains("due") || q.contains("owe") || q.contains("owed") || q.contains("kitna") || q.contains("how much"))) return AiResponse.QueryMetric(metric = "gst_payable", period = period)
        if ((q.contains("how many") || q.contains("count") || q.contains("kitne")) && q.contains("voucher") && (q.contains("without") || q.contains("no tax") || q.contains("missing"))) return AiResponse.QueryMetric(metric = "vouchers_without_tax", period = period)
        if ((q.contains("how many") || q.contains("count") || q.contains("kitne")) && q.contains("voucher")) return AiResponse.QueryMetric(metric = "vouchers_count", period = period)
        if (q.contains("outstanding") || q.contains("pending") || q.contains("due") || q.contains("dues") || q.contains("baaki") || q.contains("bakaya")) { val name = extractNameFromQuery(q, listOf("outstanding", "pending", "due", "dues", "for", "of", "baaki", "bakaya", "bill", "bills", "payment", "amount", "show", "list")); return AiResponse.QueryOutstanding(name, period) }
        val isTop = q.contains("top") || q.contains("highest") || q.contains("largest") || q.contains("maximum") || q.contains("most")
        if (isTop && (q.contains("product") || q.contains("item") || q.contains("items") || q.contains("model") || q.contains("stock"))) return AiResponse.QueryItemSales(period = period, groupBy = null)
        if (isTop && (q.contains("customer") || q.contains("customers") || q.contains("debtor") || q.contains("debtors"))) return AiResponse.QueryTopParties(kind = "customers", period = period, limit = limit ?: 5)
        if (isTop && (q.contains("supplier") || q.contains("suppliers") || q.contains("vendor") || q.contains("creditor") || q.contains("creditors"))) return AiResponse.QueryTopParties(kind = "suppliers", period = period, limit = limit ?: 5)
        if ((q.contains("last") || q.contains("recent") || q.contains("latest") || q.contains("previous")) && (q.contains("voucher") || q.contains("vouchers"))) { val vType = when { q.contains("sales") || q.contains("sale") -> "Sales"; q.contains("purchase") || q.contains("buy") || q.contains("kharid") -> "Purchase"; q.contains("receipt") || q.contains("received") -> "Receipt"; q.contains("payment") || q.contains("paid") -> "Payment"; else -> null }; return AiResponse.QueryRecentVouchers(voucherType = vType, period = period, limit = limit ?: 5) }
        if (amount <= 0 && (q.contains("how many") || q.contains("kitne") || q.contains("count")) && (q.contains("sold") || q.contains("sale") || q.contains("bikri")) && (q.contains("vehicle") || q.contains("vehicles") || q.contains("gaadi") || q.contains("bike") || q.contains("car") || q.contains("model"))) return AiResponse.QueryItemSales(period = period ?: "today", groupBy = "model")
        if (q.contains("balance") || q.contains("kitna hai") || q.contains("how much") || q.contains("outstanding") || q.contains("hisab")) { val name = extractNameFromQuery(q, listOf("of", "for", "balance", "hisab", "what", "is", "the", "check")); return AiResponse.QueryBalance(name) }
        if (q.contains("stock") || q.contains("item") || q.contains("available") || q.contains("bacha hai")) { val name = extractNameFromQuery(q, listOf("of", "stock", "item", "how", "many", "available")); return AiResponse.QueryStock(name) }
        val hasDigits = q.any { it.isDigit() }; val transactionVerbs = listOf("sold", "bought", "purchased", "pay", "paid", "received", "got", "give", "given", "gave", "take", "taken", "took", "diya", "liya", "becha", "bechi", "beche")
        val isTransaction = hasDigits || transactionVerbs.any { q.contains(it) }
        if (!isTransaction) {
            if (q.contains("sale") || q.contains("bikri")) { val periodVal = when { q.contains("today") || q.contains("aaj") -> "today"; q.contains("month") || q.contains("mahina") -> "month"; else -> "total" }; return AiResponse.QuerySales(periodVal) }
            if (q.contains("purchase") || q.contains("kharid")) { val periodVal = when { q.contains("today") || q.contains("aaj") -> "today"; q.contains("month") || q.contains("mahina") -> "month"; else -> "total" }; return AiResponse.QueryMetric(metric = "purchase", period = periodVal) }
        }
        return null
    }

    private fun extractNameFromQuery(query: String, ignoreWords: List<String>): String? {
        val stopWords = ignoreWords + listOf("amount", "is", "was", "rupees", "rs", "rate", "cost", "price", "total", "valued", "worth", "via", "through", "using", "with", "and", "the", "a", "an")
        val words = query.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<String>(); val explicitTriggers = listOf("name", "customer", "party", "to", "from", "by"); var triggerFound = false
        for (word in words) { val w = word.lowercase(); if (w in explicitTriggers) { triggerFound = true; continue }; if (triggerFound) { if (w in stopWords || w.matches(Regex(".*\\d.*"))) break; if (w != "is") result.add(word) } }
        if (result.isNotEmpty()) return result.joinToString(" ").replaceFirstChar { it.uppercase() }
        val fallbackWords = words.filter { val w = it.lowercase(); w !in stopWords && w !in ignoreWords && !w.matches(Regex(".*\\d.*")) && it.length > 2 }
        return if (fallbackWords.isNotEmpty()) fallbackWords.joinToString(" ").replaceFirstChar { it.uppercase() } else null
    }

    /**
     * Extracts one or more party/ledger names from a create-ledger command like
     * "create 5 parties named Amit, Ramesh, Suresh" or "add supplier Rahul and Priya".
     * Returns a distinct list of clean names (empty if nothing usable found).
     */
    private fun extractLedgerNames(q: String): List<String> {
        val cleaned = q.lowercase(Locale.ROOT)
        val stopWords = setOf(
            "party", "parties", "customer", "customers", "supplier", "suppliers",
            "debtor", "debtors", "creditor", "creditors", "ledger", "ledgers", "khata",
            "account", "accounts", "new", "create", "add", "make", "banao", "bana", "kholo",
            "named", "called", "name", "namely", "for", "to", "of", "with", "and", "the",
            "a", "an", "me", "ko", "ka", "ki", "ke", "please", "do", "can", "you", "would", "could", "kya", "five", "four", "three",
            "two", "six", "seven", "eight", "nine", "ten"
        )

        // 1. Cut everything before a name introducer ("named"/"called"/"namely") or a colon.
        val source = when {
            Regex("(?:named|called|namely)\\s+(.+)").containsMatchIn(cleaned) ->
                Regex("(?:named|called|namely)\\s+(.+)").find(cleaned)!!.groupValues[1].trim()
            cleaned.contains(":") -> cleaned.substringAfterLast(":").trim()
            else -> cleaned
        }

        // 2. Split the candidate list on commas / "and" / "&" / "plus" / newlines.
        val parts = source.split(Regex(",\\s*|\\s+and\\s+|\\s*&\\s*|\\s+plus\\s+|\\n+"))
            .map { it.trim() }.filter { it.isNotEmpty() }

        // 3. Keep only the name tokens (strip surrounding stop-words/digits).
        // When a count ("5 ledgers") is given but the names are space-separated
        // (no commas/and), treat each remaining word as its own ledger name.
        val hasCount = Regex("\\d").containsMatchIn(cleaned) ||
            listOf("two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
                .any { Regex("\\b$it\\b").containsMatchIn(cleaned) }
        return parts.flatMap { part ->
            val tokens = part.split(Regex("\\s+")).map { it.trimEnd(':', ',', '.') }
                .filter { w -> w !in stopWords && !Regex(".*\\d.*").matches(w) && w.length > 1 }
            if (tokens.isEmpty()) emptyList()
            else if (tokens.size > 1 && hasCount) tokens
            else listOf(tokens.joinToString(" "))
        }.filter { it.isNotBlank() }.distinct()
    }

    private fun cleanPartyName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var p = raw.trim()

        // Strip trailing payment modes: "via bank", "by upi", "through gpay", "via online", "se", "in cash", etc.
        p = Regex("\\s+(?:via|by|through|using|with|in)\\s+(?:bank\\s+account|bank|upi|gpay|phonepe|paytm|neft|rtgs|cheque|check|online|cash|nakad|rokar)\\b.*$", RegexOption.IGNORE_CASE).replace(p, "")
        p = Regex("\\s+(?:via|by|through|online|cash)$", RegexOption.IGNORE_CASE).replace(p, "")

        // Strip leading particles / connectors / currency tokens
        var prev: String
        do {
            prev = p
            p = Regex("^(?:ko|se|ne|ke|ki|ka|me|mein|mai|par|pe|to|from|for|at|of|rupaye|rupay|rupee|rupees|rs|maal|mal|saman|saaman|item|items|goods)\\s+", RegexOption.IGNORE_CASE).replace(p, "")
        } while (p != prev)

        // Strip trailing particles / connectors
        do {
            prev = p
            p = Regex("\\s+(?:ko|se|ne|ke|ki|ka|me|mein|mai|par|pe|to|from|for|at|of|maal|mal|saman|saaman|item|items|goods)$", RegexOption.IGNORE_CASE).replace(p, "")
        } while (p != prev)

        val dateWords = setOf("aaj", "today", "kal", "yesterday", "parso", "now")
        for (dw in dateWords) { p = Regex("\\b$dw\\b", RegexOption.IGNORE_CASE).replace(p, "").trim() }
        val verbWords = setOf("becha", "beche", "bechi", "bech", "bikri", "sold", "sale", "sales", "sell", "kharida", "kharide", "kharidi", "khareed", "bought", "buy", "purchase", "purchased", "diya", "diye", "liya", "liye", "mila", "mile", "to", "from", "for", "ko", "se", "ne", "ka", "ki", "ke")
        val words = p.split(Regex("\\s+")).filter { it.isNotBlank() && it.lowercase(Locale.ROOT) !in verbWords }
        if (words.isEmpty()) return null
        p = words.joinToString(" ")
        if (p.isBlank()) return null
        if (p.contains(" se ") || p.contains(" ko ") || p.contains(" ne ")) return null
        // A party name never contains a digit; a lone postposition/pronoun/stop-word is noise.
        if (Regex("\\d").containsMatchIn(p)) return null
        val lower = p.lowercase(Locale.ROOT)
        if (lower in setOf("us", "me", "you", "him", "her", "them", "mine", "self", "ours", "the", "and", "of", "cash", "nagad", "nakad", "rokar", "payment", "receipt", "bill", "invoice", "maal", "mal", "saman", "saaman", "item", "items", "goods")) return null
        return words.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /**
     * Resolves the cash/bank ledger for a payment or receipt based on how the user
     * described the mode ("bankaccount se", "gpay se", "cheque se", "cash se", …).
     * Payments credit this account; receipts debit it. Defaults to Cash-in-hand when
     * no mode is stated.
     */
    private fun resolvePaymentAccount(q: String): String {
        val lower = q.lowercase(Locale.ROOT)
        if (listOf("bank", "gpay", "phonepe", "paytm", "upi", "neft", "rtgs", "cheque", "check", "net banking", "netbanking", "online", "transfer", "transferred", "imps", "wire").any { lower.contains(it) }) return "Bank Account"
        if (listOf("cash", "nakad", "nagad", "rokar").any { lower.contains(it) }) return "Cash-in-hand"
        return "Cash-in-hand"
    }

    private fun isFillerOrStopWord(w: String): Boolean {
        val stopWords = setOf("se", "ko", "ne", "ke", "ka", "ki", "mein", "me", "pe", "par", "diya", "diye", "liye", "liya", "becha", "beche", "kharida", "kharide", "aaj", "kal", "parso", "rs", "rupees", "rupaye", "rupya", "cash", "bank", "gpay", "phonepe", "paytm", "upi", "online", "bhaiya", "bhai", "yaar", "suno", "sunno", "ok", "acha", "theek", "thik", "for", "to", "from", "and", "the", "or", "in", "of", "at", "payment", "receipt", "sale", "sales", "purchase", "purchases", "paid", "received", "sold", "bought", "bill", "invoice", "maal", "mal", "saman", "saaman", "item", "items", "goods")
        return w.lowercase(Locale.ROOT).trim() in stopWords
    }

    private fun replaceHinglishNumbers(input: String): String {
        var q = input.lowercase(Locale.ROOT).trim()
        val numMap = mapOf("ek" to "1", "do" to "2", "teen" to "3", "char" to "4", "chaar" to "4", "panch" to "5", "paanch" to "5", "che" to "6", "chhe" to "6", "chhah" to "6", "saat" to "7", "aath" to "8", "ath" to "8", "nau" to "9", "das" to "10", "gyarah" to "11", "gyaarah" to "11", "barah" to "12", "baarah" to "12", "terah" to "13", "chaudah" to "14", "pandrah" to "15", "solah" to "16")
        for ((word, num) in numMap) { q = q.replace(Regex("\\b$word\\b"), num) }
        return q
    }

    internal suspend fun tryParseHinglish(context: Context?, query: String): AiResponse? {
        if (IntentClassifier.classify(query) == IntentClassifier.Intent.QUESTION || SmartFinancePrompts.isTaxLawQuery(query) || isGstQuery(query)) return null
        val qOriginal = query.trim()
        var q = normalizeHindiNumbersCore(query)
        // ASR often writes "bechi"/"beche" (sold) as "bachi"/"bache".
        q = Regex("\\b(bachi|bache)\\b", RegexOption.IGNORE_CASE).replace(q, "beche")
        // Base/imperative & ASR sale-verb variants → canonical "beche" so the sale regexes see them.
        q = Regex("\\b(bech|becho|bika|biki|bikri|bichi)\\b", RegexOption.IGNORE_CASE).replace(q, "beche")
        // "li" (took/bought) is a short form of "kharide". Deliberately NOT "liya"/"liye":
        // "X se 2000 liya" means *received* ₹2000 from X (a Receipt), so "liya" must stay intact
        // for recvRegex1 below. Purchase "liya" is still handled later by smartRegexParser.
        q = Regex("\\bli\\b", RegexOption.IGNORE_CASE).replace(q, "kharide")
        // Strip currency words ("rs"/"rupaye"/"rpb"/…) so a stray currency token can't sit between
        // the verb and the amount and derail the positional purchase patterns — e.g.
        // "vps motor se 50 splendor kharidi rpb 49000" → "… kharidi 49000", which buyRegexParty1 then reads.
        q = Regex("\\b(?:rs|rupees|rupaye|rupya|rupee|rpb)\\b", RegexOption.IGNORE_CASE).replace(q, " ")
        // ASR often garbles the purchase verb ("kharide" → "khariden"/"kharidte"/"kharidiya",
        // "khareed"/"khareeda" double-e spelling), so collapse any "khar(e|i)d*" token to the
        // canonical "kharide" the regexes already know.
        q = Regex("\\bkhar(?:i|ee?)d[a-z]*\\b", RegexOption.IGNORE_CASE).replace(q, "kharide")
        // Normalize unambiguous English past-tense transaction verbs to the same canonical forms,
        // so the positional regexes below treat English and Hinglish input identically.
        q = Regex("\\b(bought|purchased|buy\\s+kiya|purchase\\s+kiya|buy)\\b", RegexOption.IGNORE_CASE).replace(q, "kharide")
        q = Regex("\\b(sold|sell\\s+kiya|sale\\s+kiya|sell)\\b", RegexOption.IGNORE_CASE).replace(q, "beche")
        q = Regex("\\b(paid|gave|given|bheja|bheje|transfer kiya|transfer kiye|phonepe kiya|gpay kiya|paytm kiya|upi kiya|online payment kiya|online pay kiya|upi payment kiya|online kiya)\\b", RegexOption.IGNORE_CASE).replace(q, "diya")
        q = Regex("\\b(received|got|took|taken|wapas diya|wapas diye|wapas kiya|wapas kiye|lautaya|lautaye|mil\\s+gaye|mil\\s+gaya|mil\\s+gya|prapt\\s+hua|prapt\\s+hue|wasool\\s+hua|wasool\\s+hue)\\b", RegexOption.IGNORE_CASE).replace(q, "liya")
        q = Regex("\\b(udhar\\s+(?:likho|likh\\s+lo|likh\\s+do|dalo|daal\\s+do|me\\s+likho|me\\s+dalo)|ba*ki\\s+(?:likho|likh\\s+lo|likh\\s+do|dalo|daal\\s+do|me\\s+likho|me\\s+dalo))\\b", RegexOption.IGNORE_CASE).replace(q, "udhar diya")
        q = Regex("\\b(?:ke\\s+)?khate\\s+(?:me|mein|mai)\\b", RegexOption.IGNORE_CASE).replace(q, "ko")
        val hindiRepository = context?.let {
            com.minhas.teller.data.repositories.HindiMappingRepository(AppDatabase.getDatabase(it).hindiMappingDao())
        }
        val extractedDate = parseDateFromQuery(query) ?: "today"
        val isPerUnit = Regex("\\b(?:per\\s+(?:piece|peice|pc|pcs|item|unit|kg|kilo|bori|bag|peti|box|bottle|btl|meter|mtr|darjan|dozen|doz|litre|liter|ltr)|each|prati|pratee|hisaab\\s+se|hisab\\s+se|rate\\s+se|bhav\\s+se|dar\\s+se)\\b|@\\s*\\d", RegexOption.IGNORE_CASE).containsMatchIn(qOriginal)
        val perUnitItem = "(?:per\\s+(?:piece|peice|pc|pcs|item|unit|kg|kilo|bori|bag|peti|box|bottle|btl|meter|mtr|darjan|dozen|doz|litre|liter|ltr)|each|prati|pratee)"
        val perUnitRate = "(?:ke\\s+hisaab\\s+se|ke\\s+hisab\\s+se|hisaab\\s+se|hisab\\s+se|ke\\s+rate\\s+se|rate\\s+se|ke\\s+bhav\\s+se|bhav\\s+se|dar\\s+se)"
        val perUnitSuffix = "(?:(?:$perUnitItem\\s*)?(?:$perUnitRate)?|(?:$perUnitRate\\s*)?(?:$perUnitItem)?)"
        val amtSuffix = "(?:rs|rupees|rupaye|rupya|cash|nakad|nagad|rokar|bank|online|cheque|check|upi|gpay|phonepe|paytm|\\bke\\b|\\bka\\b|\\bki\\b|\\bmein\\b|\\bme\\b|\\bmai\\b|\\bpar\\b|\\bpe\\b)?(?:\\s*$perUnitSuffix)?"
        val verbFirstSaleVerbs = "(?:beche|becha|bechi|bech|bikri|sale kiya|sell kiya|sold|sale|sales|sell)"
        val saleVerbsPattern = "(?:beche|becha|bechi|bech|bikri|sale kiya|sell kiya|sold|sale|sales|sell|diya|diye|de diya)"
        val buyVerbsPattern = "(?:kharide|kharida|kharidi|khareed|buy kiya|purchase kiya|bought|buy|purchase|purchased)"

        // 1. Multi-item batch billing check FIRST (before single-item sales/purchases)
        parseMultiItemTransaction(context, q, extractedDate, isPerUnit, ALL_UNIT_REGEX_PATTERN)?.let { return it }

        tryParseHinglishPaymentOrReceipt(context, q, qOriginal, extractedDate, amtSuffix)?.let { return it }
        val hasBuyVerb = Regex("\\b$buyVerbsPattern\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
        if (hasBuyVerb) {
            tryParseHinglishPurchases(context, q, qOriginal, extractedDate, isPerUnit, amtSuffix, buyVerbsPattern)?.let { return it }
            tryParseHinglishSales(context, q, qOriginal, extractedDate, isPerUnit, amtSuffix, verbFirstSaleVerbs, saleVerbsPattern, buyVerbsPattern)?.let { return it }
        } else {
            tryParseHinglishSales(context, q, qOriginal, extractedDate, isPerUnit, amtSuffix, verbFirstSaleVerbs, saleVerbsPattern, buyVerbsPattern)?.let { return it }
            tryParseHinglishPurchases(context, q, qOriginal, extractedDate, isPerUnit, amtSuffix, buyVerbsPattern)?.let { return it }
        }
        return null
    }

    private fun extractHinglishUnitAndItem(itemStr: String, queryStr: String): Pair<String, String?> {
        var trimmed = itemStr.trim()
        val leadingUnit = Regex("^($ALL_UNIT_REGEX_PATTERN)\\s+(.+)$", RegexOption.IGNORE_CASE)
        var foundUnit: String? = null
        leadingUnit.find(trimmed)?.let {
            foundUnit = canonicalUnit(it.groupValues[1])
            trimmed = it.groupValues[2].trim()
        }
        val trailingUnit = Regex("^(.*?)\\s+(?:ka|ki|ke)?\\s*($ALL_UNIT_REGEX_PATTERN)$", RegexOption.IGNORE_CASE)
        trailingUnit.find(trimmed)?.let {
            if (foundUnit == null) {
                foundUnit = canonicalUnit(it.groupValues[2])
            }
            trimmed = it.groupValues[1].trim()
        }
        if (foundUnit == null) {
            val qMatch = Regex("(?:\\b|(?<=[\u0900-\u097F]))(?:\\d+(?:\\.\\d+)?|[\\u0966-\\u096F]+)\\s*($ALL_UNIT_REGEX_PATTERN)(?:\\b|(?=[\u0900-\u097F]))", RegexOption.IGNORE_CASE).find(queryStr)
            if (qMatch != null) {
                foundUnit = canonicalUnit(qMatch.groupValues[1])
            }
        }
        // No explicit unit spoken → leave it null. The save path resolves the default using the
        // item's saved unit (StockItem.unitId) first, then ProductCategory.defaultUnitForItem.
        return trimmed to foundUnit
    }

    private suspend fun translateHinglishItem(context: Context?, item: String): String {
        var trimmed = item.trim()
        trimmed = Regex("^(?:a|an)\\s+", RegexOption.IGNORE_CASE).replace(trimmed, "").trim()
        trimmed = Regex("\\s+at$", RegexOption.IGNORE_CASE).replace(trimmed, "").trim()
        trimmed = Regex("\\s+(?:ka|ki|ke)?\\s*$ALL_UNIT_REGEX_PATTERN$", RegexOption.IGNORE_CASE).replace(trimmed, "").trim()
        val leadingUnit = Regex("^$ALL_UNIT_REGEX_PATTERN\\s+(.+)$", RegexOption.IGNORE_CASE)
        leadingUnit.find(trimmed)?.let { trimmed = it.groupValues[1].trim() }
        val hindiRepository = context?.let {
            com.minhas.teller.data.repositories.HindiMappingRepository(AppDatabase.getDatabase(it).hindiMappingDao())
        }
        val dbMatch = hindiRepository?.translateToEnglish(trimmed)
        if (dbMatch != null) return dbMatch.replaceFirstChar { it.uppercase() }
        return LanguageTranslationUtils.translateItem(trimmed)
    }

    private suspend fun makeHinglishSale(
        context: Context?,
        rawItem: String,
        party: String,
        amount: Double,
        qty: Double,
        qOriginal: String,
        extractedDate: String,
        isPerUnit: Boolean
    ): AiResponse.DraftVoucher {
        var effectiveQty = qty
        var itemCleaned = rawItem.trim()
        var prevItem: String
        do {
            prevItem = itemCleaned
            itemCleaned = Regex("\\s+(?:beche|becha|bechi|bech|sold|sell|sale|sales|diya|diye|to|from|for|at|ko|se|ne|ka|ki|ke|mein|me|mai)$", RegexOption.IGNORE_CASE).replace(itemCleaned, "").trim()
            itemCleaned = Regex("^(?:beche|becha|bechi|bech|sold|sell|sale|sales|diya|diye|to|from|for|at|ko|se|ne|ka|ki|ke|mein|me|mai)\\s+", RegexOption.IGNORE_CASE).replace(itemCleaned, "").trim()
        } while (itemCleaned != prevItem)
        val leadingQty = Regex("^(\\d+(?:\\.\\d+)?)\\s+(.+)$").find(itemCleaned)
        if (effectiveQty == 1.0 && leadingQty != null) {
            val parsedQ = leadingQty.groupValues[1].toDoubleOrNull()
            if (parsedQ != null && parsedQ > 0) {
                effectiveQty = parsedQ
                itemCleaned = leadingQty.groupValues[2].trim()
            }
        }
        do {
            prevItem = itemCleaned
            itemCleaned = Regex("\\s+(?:beche|becha|bechi|bech|sold|sell|sale|sales|diya|diye|to|from|for|at|ko|se|ne|ka|ki|ke|mein|me|mai)$", RegexOption.IGNORE_CASE).replace(itemCleaned, "").trim()
            itemCleaned = Regex("^(?:beche|becha|bechi|bech|sold|sell|sale|sales|diya|diye|to|from|for|at|ko|se|ne|ka|ki|ke|mein|me|mai)\\s+", RegexOption.IGNORE_CASE).replace(itemCleaned, "").trim()
        } while (itemCleaned != prevItem)
        val (cleanItem, unit) = extractHinglishUnitAndItem(itemCleaned, qOriginal)
        val translatedItem = translateHinglishItem(context, cleanItem)
        val cat = LanguageTranslationUtils.suggestCategory(translatedItem)
        val resolved = VoucherExcelImporter.resolveHsnAndTax(translatedItem, cat, null, null, context)
        val hsn = resolved?.first
        val gst = resolved?.second ?: resolveTaxRate(context, translatedItem)
        val (finalAmt, rate) = if (isPerUnit && effectiveQty > 0) {
            (amount * effectiveQty) to amount
        } else {
            amount to (if (effectiveQty > 0) amount / effectiveQty else amount)
        }
        val inv = listOf(DraftInventoryItem(translatedItem, effectiveQty, rate, finalAmt, unit, gst, cat, hsn))
        val p = cleanPartyName(party) ?: "Cash-in-hand"
        return AiResponse.DraftVoucher("Sales", extractedDate, null, listOf(DraftEntry(p, true, finalAmt), DraftEntry("Sales Account", false, finalAmt)), inv, qOriginal, p, finalAmt)
    }

    private suspend fun makeHinglishPurchase(
        context: Context?,
        rawItem: String,
        party: String,
        amount: Double,
        qty: Double,
        qOriginal: String,
        extractedDate: String,
        isPerUnit: Boolean
    ): AiResponse.DraftVoucher {
        var effectiveQty = qty
        var itemCleaned = rawItem.trim()
        var prevItem: String
        do {
            prevItem = itemCleaned
            itemCleaned = Regex("\\s+(?:kharide|kharida|kharidi|khareed|bought|buy|purchase|purchased|liya|liye|to|from|for|at|ko|se|ne|ka|ki|ke|mein|me|mai)$", RegexOption.IGNORE_CASE).replace(itemCleaned, "").trim()
            itemCleaned = Regex("^(?:kharide|kharida|kharidi|khareed|bought|buy|purchase|purchased|liya|liye|to|from|for|at|ko|se|ne|ka|ki|ke|mein|me|mai)\\s+", RegexOption.IGNORE_CASE).replace(itemCleaned, "").trim()
        } while (itemCleaned != prevItem)
        val leadingQty = Regex("^(\\d+(?:\\.\\d+)?)\\s+(.+)$").find(itemCleaned)
        if (effectiveQty == 1.0 && leadingQty != null) {
            val parsedQ = leadingQty.groupValues[1].toDoubleOrNull()
            if (parsedQ != null && parsedQ > 0) {
                effectiveQty = parsedQ
                itemCleaned = leadingQty.groupValues[2].trim()
            }
        }
        do {
            prevItem = itemCleaned
            itemCleaned = Regex("\\s+(?:kharide|kharida|kharidi|khareed|bought|buy|purchase|purchased|liya|liye|to|from|for|at|ko|se|ne|ka|ki|ke|mein|me|mai)$", RegexOption.IGNORE_CASE).replace(itemCleaned, "").trim()
            itemCleaned = Regex("^(?:kharide|kharida|kharidi|khareed|bought|buy|purchase|purchased|liya|liye|to|from|for|at|ko|se|ne|ka|ki|ke|mein|me|mai)\\s+", RegexOption.IGNORE_CASE).replace(itemCleaned, "").trim()
        } while (itemCleaned != prevItem)
        val (cleanItem, unit) = extractHinglishUnitAndItem(itemCleaned, qOriginal)
        val translatedItem = translateHinglishItem(context, cleanItem)
        val cat = LanguageTranslationUtils.suggestCategory(translatedItem)
        val resolved = VoucherExcelImporter.resolveHsnAndTax(translatedItem, cat, null, null, context)
        val hsn = resolved?.first
        val gst = resolved?.second ?: resolveTaxRate(context, translatedItem)
        val (finalAmt, rate) = if (isPerUnit && effectiveQty > 0) {
            (amount * effectiveQty) to amount
        } else {
            amount to (if (effectiveQty > 0) amount / effectiveQty else amount)
        }
        val inv = listOf(DraftInventoryItem(translatedItem, effectiveQty, rate, finalAmt, unit, gst, cat, hsn))
        val p = cleanPartyName(party) ?: "Cash-in-hand"
        return AiResponse.DraftVoucher("Purchase", extractedDate, null, listOf(DraftEntry("Purchase Account", true, finalAmt), DraftEntry(p, false, finalAmt)), inv, qOriginal, p, finalAmt)
    }


    private val dualRoleUnitNouns = setOf("bag", "bags", "bottle", "bottles", "btl", "btls", "box", "boxes", "peti")

    private fun shouldMergeLeadingUnit(unitToken: String, remainderWords: List<String>): Boolean {
        if (remainderWords.size < 2) return false
        if (unitToken.lowercase(Locale.ROOT) !in dualRoleUnitNouns) return true
        val nextWord = remainderWords[0].lowercase(Locale.ROOT)
        if (nextWord in setOf("ram", "lal", "singh", "sharma", "kumar", "a", "an", "the")) return false
        return LanguageTranslationUtils.isKnownWord(nextWord)
    }

    private fun refineAipItemAndParty(rawItem: String, rawParty: String): Pair<String, String> {
        var item = rawItem.trim()
        var party = rawParty.trim()
        if (item.matches(Regex("^($ALL_UNIT_REGEX_PATTERN)$", RegexOption.IGNORE_CASE)) && party.contains(" ")) {
            val (partItem, partParty) = splitItemAndParty(party)
            val partWords = listOf(partItem, partParty).filter { it.isNotBlank() }
            if (partParty.isNotBlank() && shouldMergeLeadingUnit(item, partWords)) {
                item = "$item $partItem".trim()
                party = partParty
            }
        } else if (party.lowercase(Locale.ROOT) in listOf("traders", "store", "hardware", "kirana", "enterprise", "enterprises", "agency", "agencies") && item.contains(" ")) {
            val (item1, party1) = splitItemAndParty(item)
            if (party1.isNotBlank()) {
                item = item1
                party = "$party1 $party"
            }
        }
        return item to party
    }

    private fun splitItemAndParty(middle: String): Pair<String, String> {
        val trimmed = middle.trim()
        var working = trimmed
        val unitMatch = Regex("^($ALL_UNIT_REGEX_PATTERN)\\s+", RegexOption.IGNORE_CASE).find(working)
        val unitPrefix = if (unitMatch != null) {
            val u = unitMatch.groupValues[1]
            val afterUnit = working.substring(unitMatch.range.last + 1).trim()
            val afterWords = afterUnit.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (shouldMergeLeadingUnit(u, afterWords)) {
                working = afterUnit
                "$u "
            } else ""
        } else ""

        val knownMultiWordItems = listOf(
            "hari mirch", "lal mirch", "kali mirch", "chai patti", "kabuli chana",
            "nariyal paani", "nimbu paani", "double roti", "shimla mirch",
            "wash basin", "wash baisen", "cold drink", "soft drink", "tea leaves",
            "sarson tel", "mustard oil", "desi ghee", "refined oil", "green chilli", "red chilli", "white chickpeas",
            "basmati chawal", "basmati rice", "finolex wire", "tata salt", "tata namak", "ashirvaad atta", "amul doodh",
            "amul butter", "amul ghee", "led bulb", "tube light", "air cooler", "wall putty"
        )
        val lower = working.lowercase(Locale.ROOT)
        for (multi in knownMultiWordItems) {
            if (lower.startsWith(multi) && (lower.length == multi.length || lower[multi.length].isWhitespace())) {
                val item = unitPrefix + working.substring(0, multi.length)
                val party = working.substring(multi.length).trim()
                return item to party
            }
        }
        val parts = working.split(Regex("\\s+"), limit = 2)
        val first = parts[0]
        val rest = parts.getOrNull(1)?.trim() ?: ""
        val restParts = rest.split(Regex("\\s+"), limit = 2)
        if (restParts.isNotEmpty() && restParts[0].matches(Regex("\\d+[a-zA-Z]*"))) {
            val item = "$unitPrefix$first ${restParts[0]}"
            val party = restParts.getOrNull(1)?.trim() ?: ""
            return item to party
        }
        return "$unitPrefix$first" to rest
    }

    private fun tryParseHinglishPaymentOrReceipt(
        context: Context?,
        q: String,
        qOriginal: String,
        extractedDate: String,
        amtSuffix: String
    ): AiResponse.DraftVoucher? {
        val hasSaleOrPurchaseVerb = Regex("\\b(?:beche|becha|bechi|bech|bikri|sold|sale|sales|kharide|kharida|kharidi|khareed|bought|buy|purchase|purchases)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
        if (hasSaleOrPurchaseVerb) return null

        // Customer / Party gave money (Receipt): "Anita ne 500 diye" / "Sharma ji ne 5000 cheque diya" / "Customer ne 3000 cash diya" / "Rahul ne 2000 bheje"
        val recvRegexNe = Regex("([a-z\u0900-\u097F\\s]+?)\\s+ne\\s+(?:(cash|bank|upi|gpay|phonepe|paytm|cheque|check|online|cheque\\s+se|cash\\s+se|online\\s+se)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*(?:(cash|bank|upi|gpay|phonepe|paytm|cheque|check|online|se\\s+cheque|se\\s+cash|cheque\\s+se|cash\\s+se|me)\\s+)?(?:diya|diye|de diya|pay kiya|bheja|bheje|transfer kiya|transfer kiye|jama kiya|jama karwaya|gpay kiya|phonepe kiya|upi kiya|chuka diya|chukaye)(?:\\s+(cash|cheque|check|online|upi|bank))?", RegexOption.IGNORE_CASE)
        recvRegexNe.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return AiResponse.DraftVoucher("Receipt", extractedDate, null, listOf(DraftEntry(resolvePaymentAccount(qOriginal), true, amount), DraftEntry(p, false, amount)), emptyList(), qOriginal, p, amount)
            }
        }


        // Deposit / Jama: "Ramesh se 5000 jama hua" / "Ramesh ka 5000 jama kiya"
        val recvRegexJama = Regex("([a-z\u0900-\u097F\\s]+?)\\s+(?:se|ka|ke)\\s+(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*(?:jama\\s+hua|jama\\s+huye|jama\\s+kiya|jama\\s+karwaya|jama)", RegexOption.IGNORE_CASE)
        recvRegexJama.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                val p = cleanPartyName(party) ?: "Cash-in-hand"
                return AiResponse.DraftVoucher("Receipt", extractedDate, null, listOf(DraftEntry(resolvePaymentAccount(qOriginal), true, amount), DraftEntry(p, false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // Payment-in: "payment in 2500 from Vijay" / "payment in Vijay 2500" / "Vijay se payment in 2500"
        val paymentInRegex = Regex("\\bpayment[- ]in\\b\\s+(?:(?:rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?)\\s+)?(?:from\\s+)?([a-z\u0900-\u097F\\s]+?)(?:\\s+(?:rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?))?$", RegexOption.IGNORE_CASE)
        paymentInRegex.find(q)?.let { m ->
            val amt1 = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[2]
            val amt2 = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val amount = if (amt1 > 0) amt1 else amt2
            if (amount > 0 && !isFillerOrStopWord(party)) {
                val p = cleanPartyName(party) ?: "Cash-in-hand"
                return AiResponse.DraftVoucher("Receipt", extractedDate, null, listOf(DraftEntry(resolvePaymentAccount(qOriginal), true, amount), DraftEntry(p, false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // Payment-out: "payment out 5000 to Wholesaler" / "payment out Wholesaler 5000" / "Wholesaler ko payment out 5000"
        val paymentOutRegex = Regex("\\bpayment[- ]out\\b\\s+(?:(?:rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?)\\s+)?(?:to\\s+)?([a-z\u0900-\u097F\\s]+?)(?:\\s+(?:rs|rupees|rupaye)?\\s*(\\d+(?:\\.\\d+)?))?$", RegexOption.IGNORE_CASE)
        paymentOutRegex.find(q)?.let { m ->
            val amt1 = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[2]
            val amt2 = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val amount = if (amt1 > 0) amt1 else amt2
            if (amount > 0 && !isFillerOrStopWord(party)) {
                val p = cleanPartyName(party) ?: "Cash-in-hand"
                return AiResponse.DraftVoucher("Payment", extractedDate, null, listOf(DraftEntry(p, true, amount), DraftEntry(resolvePaymentAccount(qOriginal), false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // Party-first payment out: "Wholesaler ko payment out 10000" / "Wholesaler ko payment-out 10000"
        val partyPaymentOutRegex = Regex("([a-z\u0900-\u097F\\s]+?)\\s+ko\\s+payment[- ]out\\s+(?:(?:rs|rupees|rupaye)?\\s*)(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        partyPaymentOutRegex.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                val p = cleanPartyName(party) ?: "Cash-in-hand"
                return AiResponse.DraftVoucher("Payment", extractedDate, null, listOf(DraftEntry(p, true, amount), DraftEntry(resolvePaymentAccount(qOriginal), false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // Party-first payment in: "Vijay se payment in 2500" / "Vijay ka payment in 2500"
        val partyPaymentInRegex = Regex("([a-z\u0900-\u097F\\s]+?)\\s+(?:se|ka|ke)\\s+payment[- ]in\\s+(?:(?:rs|rupees|rupaye)?\\s*)(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        partyPaymentInRegex.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                val p = cleanPartyName(party) ?: "Cash-in-hand"
                return AiResponse.DraftVoucher("Receipt", extractedDate, null, listOf(DraftEntry(resolvePaymentAccount(qOriginal), true, amount), DraftEntry(p, false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // "Amit owes 100"
        val owesRegex = Regex("([a-z\u0900-\u097F\\s]+?)\\s+owes\\s+(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        owesRegex.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                val p = cleanPartyName(party) ?: "Cash-in-hand"
                return AiResponse.DraftVoucher("Receipt", extractedDate, null, listOf(DraftEntry(resolvePaymentAccount(qOriginal), true, amount), DraftEntry(p, false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        val expenseType = when {
            Regex("\\b(rent|kiraya|bhada|kiraaya)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q) -> "Rent Expense"
            Regex("\\b(salary|pagar|tankha|tankhah|mehantana|vetan)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q) -> "Salary Expense"
            Regex("\\b(bijli|electricity|power|light)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q) -> "Electricity Expense"
            Regex("\\b(petrol|diesel|fuel|conveyance)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q) -> "Conveyance Expense"
            Regex("\\b(chai pani|chai nashta|tea coffee|nashta|refreshment|chai\\s+kharcha|chai\\s+ka\\s+kharcha)\\b|\\b(?:chai|tea)\\b(?!\\s+(?:patti|leaf|leaves))", RegexOption.IGNORE_CASE).containsMatchIn(q) -> "Staff Welfare"
            Regex("\\b(office expense|office kharcha|dukan ka kharcha|stationery|safai kharcha)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q) -> "Office Expense"
            else -> null
        }
        if (expenseType != null) {
            val expAmt = Regex("\\d+(?:\\.\\d+)?").find(q)?.value?.toDoubleOrNull()
            if (expAmt != null && expAmt > 0) {
                return AiResponse.DraftVoucher("Payment", extractedDate, null, listOf(DraftEntry(expenseType, true, expAmt), DraftEntry(resolvePaymentAccount(qOriginal), false, expAmt)), emptyList(), qOriginal, null, expAmt)
            }
        }

        val payVerbs = "(?:diya|diye|de diya|pay kiya|pay kardo|paid|pay|payment|gave|given|chukaya|udhar diya|udhaar diya|udhar me diya|udhari di|bhara|bhare|bhari)"
        val recvVerbs = "(?:liya|liye|lena|lene|leni|mila|mile|aaya|aaye|received|receipt|receive\\s+kiya|receive\\s+hua|udhaar liya|udhar liya|wapas diya|wapas diye|wapas kiya|wapas kiye|lautaya|lautaye|chuka diya|chukaye|dene|dena|mil\\s+gaye|mil\\s+gaya)"

        // ── 6 Natural Permutations for Receipts ──

        // 1. Receipt V P A: "Received from Sharma 10000", "Receipt from Sharma 10000", "Mila Sharma se 10000", "Aaya Sharma ji se 10000"
        val recvVPA = Regex("^$recvVerbs\\s+(?:from\\s+|se\\s+)?([a-z\u0900-\u097F\\s]+?)\\s+(?:se\\s+)?(?:for\\s+|of\\s+)?(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        recvVPA.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return AiResponse.DraftVoucher("Receipt", extractedDate, null, listOf(DraftEntry(resolvePaymentAccount(qOriginal), true, amount), DraftEntry(p, false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // 2. Receipt V A P: "Received 10000 from Sharma", "Receipt of 10000 from Sharma", "Mila 10000 Sharma se", "Receive kiya 10000 Sharma ji se"
        val recvVAP = Regex("^$recvVerbs\\s+(?:of\\s+|for\\s+)?(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s+(?:from\\s+|se\\s+)?([a-z\u0900-\u097F\\s]+?)(?:\\s+se)?$", RegexOption.IGNORE_CASE)
        recvVAP.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[2]
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return AiResponse.DraftVoucher("Receipt", extractedDate, null, listOf(DraftEntry(resolvePaymentAccount(qOriginal), true, amount), DraftEntry(p, false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // 3. Receipt A V P: "10000 received from Sharma", "10000 receipt from Sharma", "10000 mila Sharma se", "10000 cash received from Sharma"
        val recvAVP = Regex("^(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*$recvVerbs\\s+(?:from\\s+|se\\s+)?([a-z\u0900-\u097F\\s]+?)(?:\\s+se)?$", RegexOption.IGNORE_CASE)
        recvAVP.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[2]
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return AiResponse.DraftVoucher("Receipt", extractedDate, null, listOf(DraftEntry(resolvePaymentAccount(qOriginal), true, amount), DraftEntry(p, false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        val recvNeVerbs = "(?:diye|diya|de diya|bheja|bheje|transfer kiya|transfer kiye|jama kiya|jama karwaya|gpay kiya|phonepe kiya|upi kiya|chuka diya|chukaye|$recvVerbs)"

        // 4. Receipt A P V: "10000 Sharma se mila", "500 Anita ne diye", "10000 cash Sharma ji se mila", "10000 Sharma received"
        val recvAPV = Regex("^(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s+([a-z\u0900-\u097F\\s]+?)\\s+(?:(?:se|from)\\s+(?:cash\\s+|cheque\\s+|online\\s+|bank\\s+|upi\\s+)?$recvVerbs|ne\\s+(?:cash\\s+|cheque\\s+|online\\s+|bank\\s+|upi\\s+)?$recvNeVerbs|(?:received|receipt))(?:.*)$", RegexOption.IGNORE_CASE)
        recvAPV.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[2]
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return AiResponse.DraftVoucher("Receipt", extractedDate, null, listOf(DraftEntry(resolvePaymentAccount(qOriginal), true, amount), DraftEntry(p, false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // 5. Receipt P V A: "Sharma se mila 10000", "Anita ne diye 500", "Sharma receipt 10000", "Sharma received 10000"
        val recvPVA = Regex("^([a-z\u0900-\u097F\\s]+?)\\s+(?:(?:se\\s+|ka\\s+|ke\\s+)?$recvVerbs|ne\\s+$recvNeVerbs|(?:receipt|received))\\s+(?:from\\s+|for\\s+|of\\s+)?(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        recvPVA.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return AiResponse.DraftVoucher("Receipt", extractedDate, null, listOf(DraftEntry(resolvePaymentAccount(qOriginal), true, amount), DraftEntry(p, false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // 6. Receipt P A V: "Sharma se 10000 mila", "Anita ne 500 diye", "Sharma 10000 received", "Sharma 10000 receipt"
        val recvPAV = Regex("^([a-z\u0900-\u097F\\s]+?)\\s+(?:(?:se\\s+)(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*$recvVerbs|(?:ne\\s+)(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*$recvNeVerbs|(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*(?:received|receipt|receive\\s+hua))(?:.*)$", RegexOption.IGNORE_CASE)
        recvPAV.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amt1 = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val amt2 = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val amount = if (amt1 > 0) amt1 else amt2
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return AiResponse.DraftVoucher("Receipt", extractedDate, null, listOf(DraftEntry(resolvePaymentAccount(qOriginal), true, amount), DraftEntry(p, false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // ── 6 Natural Permutations for Payments ──

        // 1. Payment V P A: "Paid Ramesh 5000", "Paid to Ramesh 5000", "Payment to Ramesh 5000", "Diya Ramesh ko 5000", "Diye Ramesh ko 5000 cash"
        val payVPA = Regex("^$payVerbs\\s+(?:to\\s+)?([a-z\u0900-\u097F\\s]+?)\\s+(?:ko\\s+)?(?:for\\s+|of\\s+)?(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        payVPA.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return AiResponse.DraftVoucher("Payment", extractedDate, null, listOf(DraftEntry(p, true, amount), DraftEntry(resolvePaymentAccount(qOriginal), false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // 2. Payment V A P: "Paid 5000 to Ramesh", "Payment of 5000 to Ramesh", "Diye 5000 Ramesh ko", "Pay kiya 5000 Ramesh ko"
        val payVAP = Regex("^$payVerbs\\s+(?:of\\s+|for\\s+)?(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s+(?:to\\s+|ko\\s+)?([a-z\u0900-\u097F\\s]+)$", RegexOption.IGNORE_CASE)
        payVAP.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[2]
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return AiResponse.DraftVoucher("Payment", extractedDate, null, listOf(DraftEntry(p, true, amount), DraftEntry(resolvePaymentAccount(qOriginal), false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // 3. Payment A V P: "5000 paid to Ramesh", "5000 payment to Ramesh", "5000 diya Ramesh ko", "5000 cash diya Ramesh ko"
        val payAVP = Regex("^(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*$payVerbs\\s+(?:to\\s+|ko\\s+)?([a-z\u0900-\u097F\\s]+)$", RegexOption.IGNORE_CASE)
        payAVP.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[2]
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return AiResponse.DraftVoucher("Payment", extractedDate, null, listOf(DraftEntry(p, true, amount), DraftEntry(resolvePaymentAccount(qOriginal), false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // 4. Payment A P V: "5000 Ramesh ko diye", "5000 Ramesh ko pay kiya", "5000 cash Ramesh ko diya", "5000 Ramesh diya", "5000 Ramesh paid"
        val payAPV = Regex("^(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s+([a-z\u0900-\u097F\\s]+?)\\s+(?:ko\\s+)?$payVerbs(?:.*)$", RegexOption.IGNORE_CASE)
        payAPV.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[2]
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return AiResponse.DraftVoucher("Payment", extractedDate, null, listOf(DraftEntry(p, true, amount), DraftEntry(resolvePaymentAccount(qOriginal), false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // 5. Payment P V A: "Ramesh payment 5000", "Ramesh ko payment 5000", "Ramesh ko pay kiya 5000", "Ramesh ko diye 5000", "Ramesh paid 5000", "Ramesh diya 5000"
        val payPVA = Regex("^([a-z\u0900-\u097F\\s]+?)\\s+(?:ko\\s+)?$payVerbs\\s+(?:to\\s+|for\\s+|of\\s+)?(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        payPVA.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return AiResponse.DraftVoucher("Payment", extractedDate, null, listOf(DraftEntry(p, true, amount), DraftEntry(resolvePaymentAccount(qOriginal), false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // 6. Payment P A V: "Ramesh ko 5000 diye", "Ramesh 5000 paid", "Ramesh 5000 diya", "Ramesh ko 5000 pay kiya cash me"
        val payPAV = Regex("^([a-z\u0900-\u097F\\s]+?)\\s+(?:(?:ko|ka|ke)\\s+)?(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*$payVerbs(?:.*)$", RegexOption.IGNORE_CASE)
        payPAV.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return AiResponse.DraftVoucher("Payment", extractedDate, null, listOf(DraftEntry(p, true, amount), DraftEntry(resolvePaymentAccount(qOriginal), false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        // 7. Payment V P A + trailing reason: "gave the electrician 800 for repair", "paid Ramesh
        // 500 for rent" — none of the 6 permutations above allow extra text after the amount, so a
        // stated reason breaks every one of them even though the transaction itself is unambiguous.
        val payVPAReason = Regex("^$payVerbs\\s+(?:to\\s+)?([a-zऀ-ॿ\\s]+?)\\s+(?:ko\\s+)?(?:(?:rs|rupees|rupaye|rupya|cash|bank)\\s+)?(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya)?\\s+for\\s+[a-zऀ-ॿ\\s]+$", RegexOption.IGNORE_CASE)
        payVPAReason.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return AiResponse.DraftVoucher("Payment", extractedDate, null, listOf(DraftEntry(p, true, amount), DraftEntry(resolvePaymentAccount(qOriginal), false, amount)), emptyList(), qOriginal, p, amount)
            }
        }

        return null
    }

    private data class AivpMatch(val amount: Double, val qty: Double, val item: String, val party: String)

    /**
     * Parses Amount-Item-Verb-Party ("100000 ki 50 seb kharida Arijit Singh se",
     * "500 ki 50 hari mirch becha Ram ko") by anchoring on the mid-sentence verb so
     * multi-word items and multi-word parties cannot capture words from each other.
     */
    private fun extractAivp(
        q: String,
        aMarker: String,
        verbsPattern: String,
        partyPrep: String,
        partyPost: String
    ): AivpMatch? {
        val pat = Regex(
            "^(?:(?:for\\s+|at\\s+)(\\d+(?:\\.\\d+)?)|(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$aMarker)" +
                "(?:,|\\s+)?(?:(\\d+(?:\\.\\d+)?)\\s+)?(?!(?:$verbsPattern)\\b)(.+?)\\s+(?:$verbsPattern)\\s+" +
                "(?:$partyPrep\\s+([a-z\\u0900-\\u097F]+(?:\\s+[a-z\\u0900-\\u097F]+){0,2})|([a-z\\u0900-\\u097F]+(?:\\s+[a-z\\u0900-\\u097F]+){0,2})\\s+$partyPost)$",
            RegexOption.IGNORE_CASE
        )
        val m = pat.find(q) ?: return null
        val amount = m.groupValues[1].ifBlank { m.groupValues[2] }.toDoubleOrNull() ?: 0.0
        val qty = m.groupValues[3].toDoubleOrNull() ?: 1.0
        val item = m.groupValues[4].trim()
        val party = cleanPartyName(m.groupValues[5].ifBlank { m.groupValues[6] }.trim()) ?: return null
        val isGenericGoods = setOf("maal", "mal", "goods", "item", "items", "saman", "saaman").any {
            item.equals(it, ignoreCase = true) || item.startsWith("$it ", ignoreCase = true)
        }
        if (amount <= 0 || isFillerOrStopWord(item) || isGenericGoods) return null
        return AivpMatch(amount, qty, item, party)
    }

    private suspend fun tryParseHinglishSales(
        context: Context?,
        q: String,
        qOriginal: String,
        extractedDate: String,
        isPerUnit: Boolean,
        amtSuffix: String,
        verbFirstSaleVerbs: String,
        saleVerbsPattern: String,
        buyVerbsPattern: String
    ): AiResponse.DraftVoucher? {
        // Counter sale / Cash sale: "counter sale 1500" / "cash sale 5000" / "counter sale 10 shirts 5000"
        val cashSaleRegex = Regex("^(?:counter|cash)\\s+sale(?:s)?\\s+(?:(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+)?(?:for|at|me|mein|mai)?\\s*(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        cashSaleRegex.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].takeIf { it.isNotBlank() } ?: "Goods"
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            if (amount > 0) {
                return makeHinglishSale(context, item, "Cash-in-hand", amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // Bill & Invoice generation: "Sharma ji ka bill banao 5000" / "Sharma ji ko 10 doodh ka bill banao 600"
        val billBanaoRegex1 = Regex("([a-z\u0900-\u097F\\s]+?)\\s+(?:ka|ko)\\s+(?:(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(?:ka|ki|ke)?\\s*)?(?:bill|invoice)\\s*(?:banao|bana\\s+do|bana|kato|kaat\\s+do|generate\\s+karo|karo|do)\\s*(?:for|at|me|mein|mai)?\\s*(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        billBanaoRegex1.find(q)?.let { m ->
            val party = m.groupValues[1]
            val qty = m.groupValues[2].toDoubleOrNull() ?: 1.0
            val rawItem = m.groupValues[3].takeIf { it.isNotBlank() } ?: "Goods"
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, rawItem, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // "bill banao Sharma ji ka 5000" / "create invoice Rahul 10000" / "generate bill Suresh 5000" / "create invoice for Ramesh 5000"
        val billBanaoRegex2 = Regex("^(?:(?:create|generate|make)\\s+)?(?:bill|invoice)(?:\\s+(?:banao|bana\\s+do|bana|generate\\s+karo|create\\s+karo|kato|bana do))?\\s+(?:for\\s+)?([a-z\u0900-\u097F\\s]+?)\\s+(?:ka|ko)?\\s*(?:for|at|me|mein|mai)?\\s*(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        billBanaoRegex2.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, "Goods", party, amount, 1.0, qOriginal, extractedDate, isPerUnit)
            }
        }

        // "50 kilo basmati chawal ka bill banao 4000 me Ramesh ko"
        val billBanaoItemParty = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(?:ka|ki|ke)?\\s*(?:bill|invoice)\\s*(?:banao|bana\\s+do|bana|generate\\s+karo)\\s*(?:for|at|me|mein|mai)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:me|mein|mai|for|at)?\\s+([a-z\u0900-\u097F\\s]+?)\\s*(?:ko)?$", RegexOption.IGNORE_CASE)
        billBanaoItemParty.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2]
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[4]
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        val generalSaleVerbs = "(?:sale|sales|sold|beche|becha|bechi|bech|bikri|sell)"
        val saleVerbsAll = "(?:$saleVerbsPattern|$verbFirstSaleVerbs|$generalSaleVerbs|sold|sell|sale|sales|beche|becha|bechi|bech|bikri|diya|diye)"

        // ── 6 Order Permutations for Sales with Party: [Amount, Item, Party] in any sequence ──
        val aMarker = "(?:(?:rs|rupees|rupaye|rupya|/-)?\\s*\\b(?:mein|me|mai|ke|ki|ka|par|pe)\\b|(?:rs|rupees|rupaye|rupya|/-)\\b|,)"

        extractAivp(q, aMarker, saleVerbsAll, "to", "ko")?.let { m ->
            return makeHinglishSale(context, m.item, m.party, m.amount, m.qty, qOriginal, extractedDate, isPerUnit)
        }

        // P1. Amount First, Item Second, Party Third (A-I-P):
        val saleRegexAIPV = Regex("^(?:(?:for\\s+|at\\s+)(\\d+(?:\\.\\d+)?)|(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$aMarker)(?:,|\\s+)?(?:(?:$saleVerbsAll)\\s+)?(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(?:to\\s+([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})|([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})\\s+ko)(?:\\s+(?:$saleVerbsAll))?$", RegexOption.IGNORE_CASE)
        saleRegexAIPV.find(q)?.let { m ->
            val amount = m.groupValues[1].ifBlank { m.groupValues[2] }.toDoubleOrNull() ?: 0.0
            val qty = m.groupValues[3].toDoubleOrNull() ?: 1.0
            val (item, party) = refineAipItemAndParty(
                m.groupValues[4],
                m.groupValues[5].ifBlank { m.groupValues[6] }
            )
            val p = cleanPartyName(party)
            val hasUnitOrDigit = Regex("\\b($ALL_UNIT_REGEX_PATTERN)\\b|\\d").containsMatchIn(item) || m.groupValues[3].isNotBlank()
            val isPurePartyWithVerb = !hasUnitOrDigit && Regex("\\b$saleVerbsAll\\b", RegexOption.IGNORE_CASE).containsMatchIn(q.substring(0, q.indexOf(item).coerceAtLeast(0)))
            val isGenericGoods = setOf("maal", "mal", "goods", "item", "items", "saman", "saaman").any {
                item.equals(it, ignoreCase = true) || item.startsWith("$it ", ignoreCase = true)
            }
            if (amount > 0 && p != null && !isFillerOrStopWord(item) && !isPurePartyWithVerb && !isGenericGoods) {
                return makeHinglishSale(context, item, p, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // P2. Amount First, Party Second, Item Third (A-P-I):
        val saleRegexAPIV = Regex("^(?:(?:for\\s+|at\\s+)(\\d+(?:\\.\\d+)?)|(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$aMarker)(?:,|\\s+)?(?:to\\s+([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})|([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})\\s+ko)\\s+(?:(?:$saleVerbsAll)\\s+)?(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)(?:\\s+(?:$saleVerbsAll))?$", RegexOption.IGNORE_CASE)
        saleRegexAPIV.find(q)?.let { m ->
            val amount = m.groupValues[1].ifBlank { m.groupValues[2] }.toDoubleOrNull() ?: 0.0
            val party = m.groupValues[3].ifBlank { m.groupValues[4] }.trim()
            val qty = m.groupValues[5].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[6].trim()
            val p = cleanPartyName(party)
            if (amount > 0 && p != null && !isFillerOrStopWord(item)) {
                return makeHinglishSale(context, item, p, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // P3. Item First, Amount Second, Party Third (I-A-P):
        val saleRegexIAPV = Regex("^(?:(\\d+(?:\\.\\d+)?)\\s+)?(?!(?:$saleVerbsAll)\\b)(.+?)\\s+(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s+(?:(?:$saleVerbsAll)\\s+)?(?:to\\s+([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})|([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})\\s+ko)(?:\\s+(?:$saleVerbsAll))?$", RegexOption.IGNORE_CASE)
        saleRegexIAPV.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].trim()
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[4].ifBlank { m.groupValues[5] }.trim()
            val p = cleanPartyName(party)
            if (amount > 0 && p != null && !isFillerOrStopWord(item)) {
                return makeHinglishSale(context, item, p, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // P4. Item First, Party Second, Amount Third (I-P-A):
        // English: "10 kg rice to Ramesh for 5000" / "10 kg rice sold to Ramesh for 5000" / "10 kg rice to Ramesh sold for 5000"
        val saleRegexIPAVEnglish = Regex("^(?:(\\d+(?:\\.\\d+)?)\\s+)?(?!(?:$saleVerbsAll)\\b)(.+?)\\s+(?:(?:$saleVerbsAll)\\s+)?to\\s+([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})\\s+(?:(?:$saleVerbsAll)\\s+)?(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?(?:\\s*(?:mein|me|mai|ke|ki|ka|par|pe))?(?:\\s+(?:$saleVerbsAll))?$", RegexOption.IGNORE_CASE)
        saleRegexIPAVEnglish.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].trim()
            val party = m.groupValues[3].trim()
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null && !isFillerOrStopWord(item)) {
                return makeHinglishSale(context, item, p, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // Hindi/Hinglish: "10 kg chawal Ramesh ko 5000 me beche" / "10 packet doodh Ramesh ko beche 500 me"
        val saleRegexIPAVHindi = Regex("^(\\d+(?:\\.\\d+)?)\\s+(.+?)\\s+ko\\s+(?:(?:$saleVerbsAll)\\s+)?(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?(?:\\s*(?:mein|me|mai|ke|ki|ka|par|pe))?(?:\\s+(?:$saleVerbsAll))?$", RegexOption.IGNORE_CASE)
        saleRegexIPAVHindi.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val middle = m.groupValues[2].trim()
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val hasSaleVerbInMiddle = Regex("\\b$saleVerbsAll\\b", RegexOption.IGNORE_CASE).containsMatchIn(middle)
            if (!hasSaleVerbInMiddle) {
                val (item, party) = splitItemAndParty(middle)
                val p = cleanPartyName(party)
                if (amount > 0 && p != null && !isFillerOrStopWord(item) && item.isNotBlank()) {
                    return makeHinglishSale(context, item, p, amount, qty, qOriginal, extractedDate, isPerUnit)
                }
            }
        }

        // P5. Party First, Amount Second, Item Third (P-A-I):
        val saleRegexPAIV = Regex("^(?:to\\s+([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})|([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})\\s+ko)\\s+(?:(?:for\\s+|at\\s+)(\\d+(?:\\.\\d+)?)|(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$aMarker)\\s+(?:(?:$saleVerbsAll)\\s+)?(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)(?:\\s+(?:$saleVerbsAll))?$", RegexOption.IGNORE_CASE)
        saleRegexPAIV.find(q)?.let { m ->
            val party = m.groupValues[1].ifBlank { m.groupValues[2] }.trim()
            val amount = m.groupValues[3].ifBlank { m.groupValues[4] }.toDoubleOrNull() ?: 0.0
            val qty = m.groupValues[5].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[6].trim()
            val p = cleanPartyName(party)
            if (amount > 0 && p != null && !isFillerOrStopWord(item)) {
                return makeHinglishSale(context, item, p, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // P6. Party First, Item Second, Amount Third (P-I-A):
        val saleRegexPIAV = Regex("^(?:(?:$saleVerbsAll)\\s+)?(?:to\\s+([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})|([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})\\s+ko)\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(?:(?:$saleVerbsAll)\\s+)?(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?(?:\\s*(?:mein|me|mai|ke|ki|ka|par|pe))?(?:\\s+(?:$saleVerbsAll))?$", RegexOption.IGNORE_CASE)
        saleRegexPIAV.find(q)?.let { m ->
            val party = m.groupValues[1].ifBlank { m.groupValues[2] }.trim()
            val qty = m.groupValues[3].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[4].trim()
            val amount = m.groupValues[5].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            val hasUnitOrDigit = Regex("\\b($ALL_UNIT_REGEX_PATTERN)\\b|\\d").containsMatchIn(item) || m.groupValues[3].isNotBlank()
            if (amount > 0 && p != null && !isFillerOrStopWord(item) && hasUnitOrDigit) {
                return makeHinglishSale(context, item, p, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // Verb-first English/Hinglish sales: "sold 50 bananas to Amit for 200" / "sold 5 bananas for 200 to Amit" / "sold 50 apples for 1000"
        val saleRegexVerbFirstParty = Regex("^$verbFirstSaleVerbs\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(?:to|ko)\\s+([a-z\u0900-\u097F\\s]+?)\\s+(?:for\\s+|at\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        saleRegexVerbFirstParty.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2]
            val party = m.groupValues[3]
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        val saleRegexVerbFirstAmtParty = Regex("^$verbFirstSaleVerbs\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(?:for\\s+|at\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s+(?:to|ko)\\s+([a-z\u0900-\u097F\\s]+)$", RegexOption.IGNORE_CASE)
        saleRegexVerbFirstAmtParty.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2]
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[4]
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        val saleRegexVerbPartyFirst = Regex("^$verbFirstSaleVerbs\\s+(?:to|ko)\\s+([a-z\u0900-\u097F\\s]+?)\\s+(\\d+(?:\\.\\d+)?)\\s+(.+?)\\s+(?:for\\s+|at\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        saleRegexVerbPartyFirst.find(q)?.let { m ->
            val party = m.groupValues[1]
            val qty = m.groupValues[2].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[3]
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // 1. Sales V P A: "Sale to Ramesh 5000" / "Sold to Ramesh 5000" / "Becha Ramesh ko 5000"
        val saleVPA = Regex("^$generalSaleVerbs\\s+(?:(?:to|ko)\\s+([a-z\u0900-\u097F\\s]+?)|([a-z\u0900-\u097F\\s]+?)\\s+ko)\\s+(?:for\\s+|at\\s+|of\\s+)?(?:(?:rs|rupees|rupaye|rupya|cash)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        saleVPA.find(q)?.let { m ->
            val party = m.groupValues[1].ifBlank { m.groupValues[2] }
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return makeHinglishSale(context, "Goods", p, amount, 1.0, qOriginal, extractedDate, isPerUnit)
            }
        }

        // 2. Sales V A P: "Sale of 5000 to Ramesh" / "Sold 5000 to Ramesh" / "Becha 5000 ka maal Ramesh ko"
        val saleVAP = Regex("^$generalSaleVerbs\\s+(?:of\\s+|for\\s+)?(?:(?:rs|rupees|rupaye|rupya|cash)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*(?:ka\\s+maal\\s+)?(?:(?:to\\s+|ko\\s+)([a-z\u0900-\u097F\\s]+)|([a-z\u0900-\u097F\\s]+?)\\s+ko)$", RegexOption.IGNORE_CASE)
        saleVAP.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[2].ifBlank { m.groupValues[3] }
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return makeHinglishSale(context, "Goods", p, amount, 1.0, qOriginal, extractedDate, isPerUnit)
            }
        }

        val saleRegexVerbFirstCash = Regex("^$verbFirstSaleVerbs\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(?:for\\s+|at\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        saleRegexVerbFirstCash.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].trim()
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(item) && !item.startsWith("to ") && !item.startsWith("ko ") && !item.endsWith(" ko")) {
                return makeHinglishSale(context, item, "Cash-in-hand", amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // "amit ko 6000 ke 50 kele beche" / "amit ko 20 rs mai 10 apple diye"
        val saleRegexKoAmtConn = Regex("([a-z\u0900-\u097f\\s]+?)\\s+ko\\s+(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya)?\\s*(?:mai|me|ke|ki|ka)\\s+(\\d+(?:\\.\\d+)?)\\s+([^0-9\u0966-\u096f\\s]+(?:\\s+[^0-9\u0966-\u096f\\s]+)?)\\s+$saleVerbsPattern", RegexOption.IGNORE_CASE)
        saleRegexKoAmtConn.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val qty = m.groupValues[3].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[4]
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // "50 seb Ram ko becha 500" / "50 seb Arijit Singh ko becha 100000"
        val saleRegexQtyItemPartyKoVerbAmt = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?([^0-9\u0966-\u096F].+?)\\s+ko\\s+$saleVerbsPattern\\s+(\\d+(?:\\.\\d+)?)\\s*$amtSuffix", RegexOption.IGNORE_CASE)
        saleRegexQtyItemPartyKoVerbAmt.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val middle = m.groupValues[2]
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val (item, party) = splitItemAndParty(middle)
            if (amount > 0 && item.isNotBlank() && party.isNotBlank() && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // "5 kele sold aman 2000"
        val saleRegexQtyItemPartyAmt = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?([^0-9\u0966-\u096F\\s]+(?:\\s+[^0-9\u0966-\u096F\\s]+)?)\\s+$saleVerbsPattern\\s+([a-z\u0900-\u097F\\s]+?)\\s+(\\d+(?:\\.\\d+)?)\\s*$amtSuffix", RegexOption.IGNORE_CASE)
        saleRegexQtyItemPartyAmt.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2]
            val party = m.groupValues[3].trim()
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        val saleRegex1 = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+$saleVerbsPattern\\s+([a-z\u0900-\u097F\\s]+?)\\s+ko\\s+(\\d+(?:\\.\\d+)?)\\s*$amtSuffix", RegexOption.IGNORE_CASE)
        saleRegex1.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2]
            val party = m.groupValues[3]
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        val saleRegex2 = Regex("(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+$saleVerbsPattern\\s+([a-z\u0900-\u097F\\s]+?)(?:\\s+ko)?$", RegexOption.IGNORE_CASE)
        saleRegex2.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val qty = m.groupValues[2].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[3]
            val party = m.groupValues[4]
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        val saleRegex3 = Regex("([a-z\u0900-\u097F\\s]+?)\\s+ko\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+$saleVerbsPattern\\s+(\\d+(?:\\.\\d+)?)\\s*$amtSuffix", RegexOption.IGNORE_CASE)
        saleRegex3.find(q)?.let { m ->
            val party = m.groupValues[1]
            val qty = m.groupValues[2].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[3]
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        val saleRegex4 = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?([^0-9\u0966-\u096F\\s]+(?:\\s+[^0-9\u0966-\u096F\\s]+)?)\\s+([a-z\u0900-\u097F\\s]+?)\\s+ko\\s+$saleVerbsPattern\\s+(\\d+(?:\\.\\d+)?)\\s*$amtSuffix", RegexOption.IGNORE_CASE)
        saleRegex4.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2]
            val party = m.groupValues[3]
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }
        
        val saleRegex5 = Regex("([a-z\u0900-\u097F\\s]+?)\\s+ne\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?([^0-9\u0966-\u096F\\s]+(?:\\s+[^0-9\u0966-\u096F\\s]+)?)\\s+$buyVerbsPattern\\s+(\\d+(?:\\.\\d+)?)\\s*$amtSuffix", RegexOption.IGNORE_CASE)
        saleRegex5.find(q)?.let { m ->
            val party = m.groupValues[1]
            val qty = m.groupValues[2].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[3]
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }
        
        val saleRegexPartyKoAmt = Regex("([a-z\u0900-\u097F\\s]+?)\\s+ko\\s+(\\d+(?:\\.\\d+)?)\\s*(?:(?:rs|rupees|rupaye)\\s*)?(?:ki|ke|ka)\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?([^0-9\u0966-\u096F\\s]+(?:\\s+[^0-9\u0966-\u096F\\s]+)?)\\s+$saleVerbsPattern", RegexOption.IGNORE_CASE)
        saleRegexPartyKoAmt.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val qty = m.groupValues[3].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[4]
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // "qty item beche 9000 ki Ram ko"
        val saleRegexPartyAfterAmt = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+$saleVerbsPattern\\s+(\\d+(?:\\.\\d+)?)\\s*(?:ke|ki|ka|rs|rupees|rupaye|rupya)?\\s+([a-z\u0900-\u097F\\s]+?)\\s+(?:ko|se|ne)\\b", RegexOption.IGNORE_CASE)
        saleRegexPartyAfterAmt.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2]
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[4].trim()
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }


        // 3. Sales A V P: "5000 sale to Ramesh" / "5000 sold to Ramesh" / "5000 becha Ramesh ko" / "5000 ki sale Ramesh ko"
        val saleAVP = Regex("^(?:(?:rs|rupees|rupaye|rupya|cash)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*(?:ki\\s+sale\\s+|ki\\s+|ka\\s+)?$generalSaleVerbs\\s+(?:(?:to\\s+|ko\\s+)([a-z\u0900-\u097F\\s]+)|([a-z\u0900-\u097F\\s]+?)\\s+ko)$", RegexOption.IGNORE_CASE)
        saleAVP.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[2].ifBlank { m.groupValues[3] }
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return makeHinglishSale(context, "Goods", p, amount, 1.0, qOriginal, extractedDate, isPerUnit)
            }
        }

        // 4. Sales A P V: "5000 Ramesh ko becha" / "5000 ka maal Ramesh ko becha"
        val saleAPV = Regex("^(?:(?:rs|rupees|rupaye|rupya|cash)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*(?:ka\\s+maal\\s+|maal\\s+)?([a-z\u0900-\u097F\\s]+?)\\s+(?:ko\\s+|to\\s+)$generalSaleVerbs$", RegexOption.IGNORE_CASE)
        saleAPV.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[2]
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return makeHinglishSale(context, "Goods", p, amount, 1.0, qOriginal, extractedDate, isPerUnit)
            }
        }

        // 5. Sales P V A: "Ramesh ko sale 5000" / "Ramesh ko becha 5000" / "Ramesh sale 5000"
        val salePVA = Regex("^([a-z\u0900-\u097F\\s]+?)\\s+(?:(?:ko|to)\\s+$generalSaleVerbs|(?:sale|sales))\\s+(?:for\\s+|at\\s+|of\\s+)?(?:(?:rs|rupees|rupaye|rupya|cash)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        salePVA.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return makeHinglishSale(context, "Goods", p, amount, 1.0, qOriginal, extractedDate, isPerUnit)
            }
        }

        // 6. Sales P A V: "Ramesh ko 5000 ki sale" / "Ramesh ko 5000 ka maal becha" / "Ramesh 5000 sale"
        val salePAV = Regex("^([a-z\u0900-\u097F\\s]+?)\\s+(?:ko\\s+(?:(?:rs|rupees|rupaye|rupya|cash)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*(?:ka\\s+maal\\s+|ki\\s+sale\\s+|ki\\s+|ka\\s+)?$generalSaleVerbs|(?:(?:rs|rupees|rupaye|rupya|cash)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*(?:sale|sales))$", RegexOption.IGNORE_CASE)
        salePAV.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return makeHinglishSale(context, "Goods", p, amount, 1.0, qOriginal, extractedDate, isPerUnit)
            }
        }

        val saleRegex6 = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+$saleVerbsPattern\\s+(\\d+(?:\\.\\d+)?)\\s*$amtSuffix", RegexOption.IGNORE_CASE)
        saleRegex6.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].trim()
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val isGenericGoods = item.lowercase() in setOf("maal", "mal", "goods", "item", "items", "saman", "saaman")
            if (amount > 0 && (!isFillerOrStopWord(item) || isGenericGoods) && !item.endsWith(" ko") && !item.endsWith(" to") && !item.startsWith("to ") && !item.startsWith("ko ") && !item.contains(" ko ") && !item.contains(" to ")) {
                val resolvedItem = if (isGenericGoods) "Goods" else item
                return makeHinglishSale(context, resolvedItem, "Cash-in-hand", amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }
        
        val saleRegex7 = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-|\\bme\\b|\\bmein\\b|\\bmai\\b|\\bke\\b|\\bki\\b|\\bka\\b)\\s*(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+$saleVerbsPattern", RegexOption.IGNORE_CASE)
        saleRegex7.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val qty = m.groupValues[2].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[3].trim()
            val isGenericGoods = item.lowercase() in setOf("maal", "mal", "goods", "item", "items", "saman", "saaman")
            if (amount > 0 && (!isFillerOrStopWord(item) || isGenericGoods) && !item.endsWith(" ko") && !item.endsWith(" to") && !item.startsWith("to ") && !item.startsWith("ko ") && !item.contains(" ko ") && !item.contains(" to ")) {
                val resolvedItem = if (isGenericGoods) "Goods" else item
                return makeHinglishSale(context, resolvedItem, "Cash-in-hand", amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // [Party] ko [Qty] [Item] [Amt] [me/mein/mai/ke/ki/ka] beche
        // e.g. "Ramesh ko 10 kg cheeni 500 me becha" / "Sharma Traders ko 50 bori cement 15000 me becha"
        val saleRegexPartyKoItemAmtVerb = Regex("([a-z\u0900-\u097F\\s]+?)\\s+ko\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?\\s*(?:me|mein|mai|ke|ki|ka|par|pe)?\\s+$saleVerbsPattern$", RegexOption.IGNORE_CASE)
        saleRegexPartyKoItemAmtVerb.find(q)?.let { m ->
            val party = m.groupValues[1]
            val qty = m.groupValues[2].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[3].trim()
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            val hasLeadingConnector = Regex("^(?:ke|ki|ka|me|mein|mai|se|ko|from|to|for)\\b", RegexOption.IGNORE_CASE).containsMatchIn(item)
            if (amount > 0 && !hasLeadingConnector && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // [Qty] [Item] [Amt] [me/mein/mai/ke/ki/ka] beche [Party] ko
        // e.g. "10 kg cheeni 500 me becha Ramesh ko" / "50 bori cement 15000 me becha Sharma Traders ko"
        val saleRegexItemAmtVerbPartyKo = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?\\s*(?:me|mein|mai|ke|ki|ka|par|pe)?\\s+$saleVerbsPattern\\s+([a-z\u0900-\u097F\\s]+?)\\s*ko$", RegexOption.IGNORE_CASE)
        saleRegexItemAmtVerbPartyKo.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].trim()
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[4].trim()
            val hasLeadingConnector = Regex("^(?:ke|ki|ka|me|mein|mai|se|ko|from|to|for)\\b", RegexOption.IGNORE_CASE).containsMatchIn(item)
            if (amount > 0 && !hasLeadingConnector && !isFillerOrStopWord(party)) {
                return makeHinglishSale(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // [Qty] [Item] [Amt] [me/mein/mai/ke/ki/ka] beche
        // e.g. "20 liter milk 1000 me becha" / "5 killo aaloo 100 me becha" / "basmati chawal 500 me becha"
        val saleRegexItemAmtVerb = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?\\s*(?:me|mein|mai|ke|ki|ka|par|pe)?\\s+$saleVerbsPattern$", RegexOption.IGNORE_CASE)
        saleRegexItemAmtVerb.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].trim()
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val isGenericGoods = item.lowercase() in setOf("maal", "mal", "goods", "item", "items", "saman", "saaman")
            val hasLeadingConnector = Regex("^(?:ke|ki|ka|me|mein|mai|se|ko|from|to|for)\\b", RegexOption.IGNORE_CASE).containsMatchIn(item)
            if (amount > 0 && !hasLeadingConnector && (!isFillerOrStopWord(item) || isGenericGoods) && !item.endsWith(" ko") && !item.endsWith(" to") && !item.startsWith("to ") && !item.startsWith("ko ") && !item.contains(" ko ") && !item.contains(" to ")) {
                val resolvedItem = if (isGenericGoods) "Goods" else item
                return makeHinglishSale(context, resolvedItem, "Cash-in-hand", amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // Implied sale with unit: "20 liter milk 1000 me" / "5 killo aaloo 100 me" / "20 liter milk 1000" / "5 killo aaloo 100"
        val saleRegexImplied = Regex("^(?:(\\d+(?:\\.\\d+)?)\\s+)?($ALL_UNIT_REGEX_PATTERN\\s+[^0-9\u0966-\u096F]+?|[^0-9\u0966-\u096F]+?\\s+$ALL_UNIT_REGEX_PATTERN)\\s+(?:for|at|me|mein|mai)?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?\\s*(?:me|mein|mai)?$", RegexOption.IGNORE_CASE)
        saleRegexImplied.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].trim()
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val hasActionVerb = Regex("\\b(?:$saleVerbsPattern|$buyVerbsPattern|diya|diye|mila|mile|payment|receipt|banao|sold|bought)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
            if (amount > 0 && !hasActionVerb && !isFillerOrStopWord(item)) {
                return makeHinglishSale(context, item, "Cash-in-hand", amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        return null
    }

    private suspend fun tryParseHinglishPurchases(
        context: Context?,
        q: String,
        qOriginal: String,
        extractedDate: String,
        isPerUnit: Boolean,
        amtSuffix: String,
        buyVerbsPattern: String
    ): AiResponse.DraftVoucher? {
        // Counter purchase / Cash purchase: "counter purchase 3000" / "cash purchase 5000"
        val cashPurchaseRegex = Regex("^(?:counter|cash)\\s+(?:purchase|purchases|buy)\\s+(?:(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+)?(?:for|at|me|mein|mai)?\\s*(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        cashPurchaseRegex.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].takeIf { it.isNotBlank() } ?: "Goods"
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            if (amount > 0) {
                return makeHinglishPurchase(context, item, "Cash-in-hand", amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // Stock in: "stock in 5000 from Ram" / "stock in 10 books 500 from Suresh"
        val stockInRegex = Regex("^stock[- ]in\\s+(?:(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+)?(?:from|se\\s+)?([a-z\u0900-\u097F\\s]+?)\\s+(?:for|at|me|mein|mai)?\\s*(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        stockInRegex.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].takeIf { it.isNotBlank() } ?: "Goods"
            val party = m.groupValues[3]
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        val generalBuyVerbs = "(?:purchase|purchases|bought|buy|kharide|kharida|kharidi|khareed)"
        val buyVerbsAll = "(?:$buyVerbsPattern|$generalBuyVerbs|bought|buy|purchase|purchased|kharide|kharida|kharidi|khareed|liya|liye)"

        // ── 6 Order Permutations for Purchases with Party: [Amount, Item, Party] in any sequence ──
        val aMarker = "(?:(?:rs|rupees|rupaye|rupya|/-)?\\s*\\b(?:mein|me|mai|ke|ki|ka|par|pe)\\b|(?:rs|rupees|rupaye|rupya|/-)\\b|,)"

        extractAivp(q, aMarker, buyVerbsAll, "from", "se")?.let { m ->
            return makeHinglishPurchase(context, m.item, m.party, m.amount, m.qty, qOriginal, extractedDate, isPerUnit)
        }

        // P1. Amount First, Item Second, Party Third (A-I-P):
        val buyRegexAIPV = Regex("^(?:(?:for\\s+|at\\s+)(\\d+(?:\\.\\d+)?)|(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$aMarker)(?:,|\\s+)?(?:(?:$buyVerbsAll)\\s+)?(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(?:from\\s+([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})|([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})\\s+se)(?:\\s+(?:$buyVerbsAll))?$", RegexOption.IGNORE_CASE)
        buyRegexAIPV.find(q)?.let { m ->
            val amount = m.groupValues[1].ifBlank { m.groupValues[2] }.toDoubleOrNull() ?: 0.0
            val qty = m.groupValues[3].toDoubleOrNull() ?: 1.0
            val (item, party) = refineAipItemAndParty(
                m.groupValues[4],
                m.groupValues[5].ifBlank { m.groupValues[6] }
            )
            val p = cleanPartyName(party)
            val hasUnitOrDigit = Regex("\\b($ALL_UNIT_REGEX_PATTERN)\\b|\\d").containsMatchIn(item) || m.groupValues[3].isNotBlank()
            val isPurePartyWithVerb = !hasUnitOrDigit && Regex("\\b$buyVerbsAll\\b", RegexOption.IGNORE_CASE).containsMatchIn(q.substring(0, q.indexOf(item).coerceAtLeast(0)))
            val isGenericGoods = setOf("maal", "mal", "goods", "item", "items", "saman", "saaman").any {
                item.equals(it, ignoreCase = true) || item.startsWith("$it ", ignoreCase = true)
            }
            if (amount > 0 && p != null && !isFillerOrStopWord(item) && !isPurePartyWithVerb && !isGenericGoods) {
                return makeHinglishPurchase(context, item, p, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // P2. Amount First, Party Second, Item Third (A-P-I):
        val buyRegexAPIV = Regex("^(?:(?:for\\s+|at\\s+)(\\d+(?:\\.\\d+)?)|(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$aMarker)(?:,|\\s+)?(?:from\\s+([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})|([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})\\s+se)\\s+(?:(?:$buyVerbsAll)\\s+)?(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)(?:\\s+(?:$buyVerbsAll))?$", RegexOption.IGNORE_CASE)
        buyRegexAPIV.find(q)?.let { m ->
            val amount = m.groupValues[1].ifBlank { m.groupValues[2] }.toDoubleOrNull() ?: 0.0
            val party = m.groupValues[3].ifBlank { m.groupValues[4] }.trim()
            val qty = m.groupValues[5].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[6].trim()
            val p = cleanPartyName(party)
            if (amount > 0 && p != null && !isFillerOrStopWord(item)) {
                return makeHinglishPurchase(context, item, p, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // P3. Item First, Amount Second, Party Third (I-A-P):
        val buyRegexIAPV = Regex("^(?:(\\d+(?:\\.\\d+)?)\\s+)?(?!(?:$buyVerbsAll)\\b)(.+?)\\s+(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?\\s*(?:mein|me|mai|ke|ki|ka|par|pe)?\\s+(?:(?:$buyVerbsAll)\\s+)?(?:from\\s+([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})|([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})\\s+se)(?:\\s+(?:$buyVerbsAll))?$", RegexOption.IGNORE_CASE)
        buyRegexIAPV.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].trim()
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[4].ifBlank { m.groupValues[5] }.trim()
            val p = cleanPartyName(party)
            if (amount > 0 && p != null && !isFillerOrStopWord(item)) {
                return makeHinglishPurchase(context, item, p, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // P4. Item First, Party Second, Amount Third (I-P-A):
        // English: "5 bags cement from Suresh for 3000" / "5 bags cement bought from Suresh for 3000"
        val buyRegexIPAVEnglish = Regex("^(?:(\\d+(?:\\.\\d+)?)\\s+)?(?!(?:$buyVerbsAll)\\b)(.+?)\\s+(?:(?:$buyVerbsAll)\\s+)?from\\s+([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})\\s+(?:(?:$buyVerbsAll)\\s+)?(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?(?:\\s*(?:mein|me|mai|ke|ki|ka|par|pe))?(?:\\s+(?:$buyVerbsAll))?$", RegexOption.IGNORE_CASE)
        buyRegexIPAVEnglish.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].trim()
            val party = m.groupValues[3].trim()
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null && !isFillerOrStopWord(item)) {
                return makeHinglishPurchase(context, item, p, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // Hindi/Hinglish: "5 bori cement Suresh se 3000 me kharida" / "5 bori cement Suresh se kharida 3000 me"
        val buyRegexIPAVHindi = Regex("^(\\d+(?:\\.\\d+)?)\\s+(.+?)\\s+se\\s+(?:(?:$buyVerbsAll)\\s+)?(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?(?:\\s*(?:mein|me|mai|ke|ki|ka|par|pe))?(?:\\s+(?:$buyVerbsAll))?$", RegexOption.IGNORE_CASE)
        buyRegexIPAVHindi.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val middle = m.groupValues[2].trim()
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val hasBuyVerbInMiddle = Regex("\\b$buyVerbsAll\\b", RegexOption.IGNORE_CASE).containsMatchIn(middle)
            if (!hasBuyVerbInMiddle) {
                val (item, party) = splitItemAndParty(middle)
                val p = cleanPartyName(party)
                if (amount > 0 && p != null && !isFillerOrStopWord(item) && item.isNotBlank()) {
                    return makeHinglishPurchase(context, item, p, amount, qty, qOriginal, extractedDate, isPerUnit)
                }
            }
        }

        // P5. Party First, Amount Second, Item Third (P-A-I):
        val buyRegexPAIV = Regex("^(?:from\\s+([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})|([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})\\s+se)\\s+(?:(?:for\\s+|at\\s+)(\\d+(?:\\.\\d+)?)|(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$aMarker)\\s+(?:(?:$buyVerbsAll)\\s+)?(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)(?:\\s+(?:$buyVerbsAll))?$", RegexOption.IGNORE_CASE)
        buyRegexPAIV.find(q)?.let { m ->
            val party = m.groupValues[1].ifBlank { m.groupValues[2] }.trim()
            val amount = m.groupValues[3].ifBlank { m.groupValues[4] }.toDoubleOrNull() ?: 0.0
            val qty = m.groupValues[5].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[6].trim()
            val p = cleanPartyName(party)
            // isFillerOrStopWord treats "maal"/"mal" as noise so it doesn't get captured as a
            // party/connector by mistake — but here it IS the item slot ("5000 ka maal liya"),
            // so a bare generic-goods word should still be accepted.
            val isGenericGoodsWord = Regex("^(?:maal|mal|saman|saaman|samaan|stock|goods|items?)$", RegexOption.IGNORE_CASE).matches(item)
            if (amount > 0 && p != null && (!isFillerOrStopWord(item) || isGenericGoodsWord)) {
                return makeHinglishPurchase(context, item, p, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // P6. Party First, Item Second, Amount Third (P-I-A). The verb-before-comma allows a
        // speech pause ("stock liya, 15000 ka") without the comma getting swallowed into item.
        val buyRegexPIAV = Regex("^(?:(?:$buyVerbsAll)\\s+)?(?:from\\s+([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})|([a-z\u0900-\u097F]+(?:\\s+[a-z\u0900-\u097F]+){0,2})\\s+se)\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(?:(?:$buyVerbsAll)[,\\s]+)?(?:for\\s+|at\\s+)?(?:(?:rs|rupees|rupaye|rupya)\\s+)?(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?(?:\\s*(?:mein|me|mai|ke|ki|ka|par|pe))?(?:\\s+(?:$buyVerbsAll))?$", RegexOption.IGNORE_CASE)
        buyRegexPIAV.find(q)?.let { m ->
            val party = m.groupValues[1].ifBlank { m.groupValues[2] }.trim()
            val qty = m.groupValues[3].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[4].trim()
            val amount = m.groupValues[5].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            val hasUnitOrDigit = Regex("\\b($ALL_UNIT_REGEX_PATTERN)\\b|\\d").containsMatchIn(item) || m.groupValues[3].isNotBlank()
            // A bare generic-goods word ("stock", "maal") is itself sufficient evidence this slot
            // really is the item \u2014 it just has no unit/quantity attached to it.
            val isGenericGoodsWord = Regex("^(?:maal|mal|saman|saaman|samaan|stock|goods|items?)$", RegexOption.IGNORE_CASE).matches(item)
            if (amount > 0 && p != null && !isFillerOrStopWord(item) && (hasUnitOrDigit || isGenericGoodsWord)) {
                return makeHinglishPurchase(context, item, p, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // Verb-first English/Hinglish purchases: "bought 50 apples from Ram for 500" / "bought 10 kg rice for 5000 from Ramesh" / "purchased 50 apples for 1000"
        val buyRegexVerbFirstParty = Regex("^$buyVerbsPattern\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(?:from|se)\\s+([a-z\u0900-\u097F\\s]+?)\\s+(?:for\\s+|at\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        buyRegexVerbFirstParty.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2]
            val party = m.groupValues[3]
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        val buyRegexVerbFirstAmtParty = Regex("^$buyVerbsPattern\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(?:for\\s+|at\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s+(?:from|se)\\s+([a-z\u0900-\u097F\\s]+)$", RegexOption.IGNORE_CASE)
        buyRegexVerbFirstAmtParty.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2]
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[4]
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        val buyRegexVerbPartyFirst = Regex("^$buyVerbsPattern\\s+(?:from|se)\\s+([a-z\u0900-\u097F\\s]+?)\\s+(\\d+(?:\\.\\d+)?)\\s+(.+?)\\s+(?:for\\s+|at\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        buyRegexVerbPartyFirst.find(q)?.let { m ->
            val party = m.groupValues[1]
            val qty = m.groupValues[2].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[3]
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // 1. Purchase V P A: "Purchase from Gupta Traders 10000" / "Bought from Gupta Traders 10000" / "Kharida Gupta Traders se 10000"
        val buyVPA = Regex("^$generalBuyVerbs\\s+(?:(?:from|se)\\s+([a-z\u0900-\u097F\\s]+?)|([a-z\u0900-\u097F\\s]+?)\\s+se)\\s+(?:for\\s+|at\\s+|of\\s+)?(?:(?:rs|rupees|rupaye|rupya|cash)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        buyVPA.find(q)?.let { m ->
            val party = m.groupValues[1].ifBlank { m.groupValues[2] }
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return makeHinglishPurchase(context, "Goods", p, amount, 1.0, qOriginal, extractedDate, isPerUnit)
            }
        }

        // 2. Purchase V A P: "Purchase of 10000 from Gupta Traders" / "Bought 10000 from Gupta Traders" / "Kharida 10000 ka maal Gupta Traders se"
        val buyVAP = Regex("^$generalBuyVerbs\\s+(?:of\\s+|for\\s+)?(?:(?:rs|rupees|rupaye|rupya|cash)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*(?:ka\\s+maal\\s+)?(?:(?:from\\s+|se\\s+)([a-z\u0900-\u097F\\s]+)|([a-z\u0900-\u097F\\s]+?)\\s+se)$", RegexOption.IGNORE_CASE)
        buyVAP.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[2].ifBlank { m.groupValues[3] }
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return makeHinglishPurchase(context, "Goods", p, amount, 1.0, qOriginal, extractedDate, isPerUnit)
            }
        }

        // 3. Purchase A V P: "10000 purchase from Gupta Traders" / "10000 bought from Gupta Traders" / "10000 kharida Gupta Traders se" / "10000 ka purchase Gupta Traders se"
        val buyAVP = Regex("^(?:(?:rs|rupees|rupaye|rupya|cash)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*(?:ka\\s+purchase\\s+|ka\\s+|ki\\s+)?$generalBuyVerbs\\s+(?:(?:from\\s+|se\\s+)([a-z\u0900-\u097F\\s]+)|([a-z\u0900-\u097F\\s]+?)\\s+se)$", RegexOption.IGNORE_CASE)
        buyAVP.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[2].ifBlank { m.groupValues[3] }
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return makeHinglishPurchase(context, "Goods", p, amount, 1.0, qOriginal, extractedDate, isPerUnit)
            }
        }

        // 4. Purchase A P V: "10000 Gupta Traders se kharida" / "10000 ka maal Gupta Traders se kharida"
        val buyAPV = Regex("^(?:(?:rs|rupees|rupaye|rupya|cash)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*(?:ka\\s+maal\\s+|maal\\s+)?([a-z\u0900-\u097F\\s]+?)\\s+(?:se\\s+|from\\s+)$generalBuyVerbs$", RegexOption.IGNORE_CASE)
        buyAPV.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[2]
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return makeHinglishPurchase(context, "Goods", p, amount, 1.0, qOriginal, extractedDate, isPerUnit)
            }
        }

        // 5. Purchase P V A: "Gupta Traders se purchase 10000" / "Gupta Traders se kharida 10000" / "Gupta Traders purchase 10000"
        val buyPVA = Regex("^([a-z\u0900-\u097F\\s]+?)\\s+(?:(?:se|from)\\s+$generalBuyVerbs|(?:purchase|purchases|buy))\\s+(?:for\\s+|at\\s+|of\\s+)?(?:(?:rs|rupees|rupaye|rupya|cash)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        buyPVA.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return makeHinglishPurchase(context, "Goods", p, amount, 1.0, qOriginal, extractedDate, isPerUnit)
            }
        }

        // 6. Purchase P A V: "Gupta Traders se 10000 ka purchase" / "Gupta Traders se 10000 ka maal kharida" / "Gupta Traders 10000 purchase"
        val buyPAV = Regex("^([a-z\u0900-\u097F\\s]+?)\\s+(?:(?:se|from)\\s+(?:(?:rs|rupees|rupaye|rupya|cash)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*(?:ka\\s+maal\\s+|ka\\s+purchase\\s+|ka\\s+|ki\\s+)?$generalBuyVerbs|(?:(?:rs|rupees|rupaye|rupya|cash)\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s*(?:purchase|purchases|buy))$", RegexOption.IGNORE_CASE)
        buyPAV.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val p = cleanPartyName(party)
            if (amount > 0 && p != null) {
                return makeHinglishPurchase(context, "Goods", p, amount, 1.0, qOriginal, extractedDate, isPerUnit)
            }
        }

        val buyRegexVerbFirstCash = Regex("^$buyVerbsPattern\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(?:for\\s+|at\\s+)?(\\d+(?:\\.\\d+)?)\\s*$amtSuffix$", RegexOption.IGNORE_CASE)
        buyRegexVerbFirstCash.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].trim()
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(item) && !item.startsWith("from ") && !item.startsWith("se ") && !item.endsWith(" se")) {
                return makeHinglishPurchase(context, item, "Cash-in-hand", amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // "50 seb Ram se kharida 500" / "50 seb Arijit Singh se kharida 100000"
        val buyRegexQtyItemPartySeVerbAmt = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?([^0-9\u0966-\u096F].+?)\\s+se\\s+$buyVerbsPattern\\s+(\\d+(?:\\.\\d+)?)\\s*$amtSuffix", RegexOption.IGNORE_CASE)
        buyRegexQtyItemPartySeVerbAmt.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val middle = m.groupValues[2]
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val (item, party) = splitItemAndParty(middle)
            if (amount > 0 && item.isNotBlank() && party.isNotBlank() && !isFillerOrStopWord(party)) {
                return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        val buyRegexParty1 = Regex("([a-z\u0900-\u097F\\s]+?)\\s+se\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+$buyVerbsPattern\\s+(\\d+(?:\\.\\d+)?)\\s*$amtSuffix", RegexOption.IGNORE_CASE)
        buyRegexParty1.find(q)?.let { m ->
            val party = m.groupValues[1]
            val qty = m.groupValues[2].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[3]
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }
        
        val buyRegexParty2 = Regex("([a-z\u0900-\u097F\\s]+?)\\s+se\\s+(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-|\\bme\\b|\\bmein\\b|\\bmai\\b|\\bke\\b|\\bki\\b|\\bka\\b)\\s*(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+$buyVerbsPattern(?!\\s*\\d)", RegexOption.IGNORE_CASE)
        buyRegexParty2.find(q)?.let { m ->
            val party = m.groupValues[1]
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val qty = m.groupValues[3].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[4].trim()
            val hasTrailingAmt = Regex("\\d+\\s*(?:rs|rupees|rupaye|rupya|/-|me|mein|mai|ke|ki|ka)\\b", RegexOption.IGNORE_CASE).containsMatchIn(item)
            if (amount > 0 && !hasTrailingAmt && !isFillerOrStopWord(party)) {
                return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // [Party] se [Qty] [Item] [Amt] [me/mein/mai/ke/ki/ka] kharida
        // e.g. "Suresh se 10 bag cement 3500 me kharida" / "Verma Store se 20 liter oil 3000 me kharida"
        val buyRegexPartySeItemAmtVerb = Regex("([a-z\u0900-\u097F\\s]+?)\\s+se\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?\\s*(?:me|mein|mai|ke|ki|ka|par|pe)?\\s+$buyVerbsPattern$", RegexOption.IGNORE_CASE)
        buyRegexPartySeItemAmtVerb.find(q)?.let { m ->
            val party = m.groupValues[1]
            val qty = m.groupValues[2].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[3].trim()
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            val hasLeadingConnector = Regex("^(?:ke|ki|ka|me|mein|mai|se|ko|from|to|for)\\b", RegexOption.IGNORE_CASE).containsMatchIn(item)
            if (amount > 0 && !hasLeadingConnector && !isFillerOrStopWord(party)) {
                return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // [Party] se [BareAmt] [Item] kharida  (e.g. "amit se 5000 aloo kharida")
        // Reject unit-prefixed items ("amit se 5 kilo aloo kharida" has no amount) and extra digits.
        val buyRegexPartySeBareAmtItemVerb = Regex("^([a-z\u0900-\u097F\\s]+?)\\s+se\\s+(\\d+(?:\\.\\d+)?)\\s+(.+?)\\s+$buyVerbsPattern$", RegexOption.IGNORE_CASE)
        buyRegexPartySeBareAmtItemVerb.find(q)?.let { m ->
            val party = m.groupValues[1].trim()
            val amount = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val item = m.groupValues[3].trim()
            val hasLeadingConnector = Regex("^(?:ke|ki|ka|me|mein|mai|se|ko|from|to|for)\\b", RegexOption.IGNORE_CASE).containsMatchIn(item)
            val startsWithUnit = Regex("^($ALL_UNIT_REGEX_PATTERN)\\b", RegexOption.IGNORE_CASE).containsMatchIn(item)
            val hasDigit = Regex("\\d").containsMatchIn(item)
            val hasTrailingParty = item.contains(" se ") || item.contains(" from ")
            if (amount > 0 && !hasLeadingConnector && !startsWithUnit && !hasDigit && !hasTrailingParty && !isFillerOrStopWord(party) && !isFillerOrStopWord(item)) {
                return makeHinglishPurchase(context, item, party, amount, 1.0, qOriginal, extractedDate, isPerUnit)
            }
        }

        // [Qty] [Item] [Amt] [me/mein/mai/ke/ki/ka] kharida [Party] se
        // e.g. "10 bag cement 3500 me kharida Suresh se" / "20 liter oil 3000 me kharida Verma Store se"
        val buyRegexItemAmtVerbPartySe = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?\\s*(?:me|mein|mai|ke|ki|ka|par|pe)?\\s+$buyVerbsPattern\\s+([a-z\u0900-\u097F\\s]+?)\\s*se$", RegexOption.IGNORE_CASE)
        buyRegexItemAmtVerbPartySe.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].trim()
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[4].trim()
            val hasLeadingConnector = Regex("^(?:ke|ki|ka|me|mein|mai|se|ko|from|to|for)\\b", RegexOption.IGNORE_CASE).containsMatchIn(item)
            if (amount > 0 && !hasLeadingConnector && !isFillerOrStopWord(party)) {
                return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }
        
        // [Qty] [Item] [Amt] [me/mein/mai/ke/ki/ka] kharide
        // e.g. "5 killo chawal 250 me kharida" / "20 liter tel 2000 me kharida" / "१० लीटर दूध ५०० में खरीदा"
        val buyRegexItemAmtVerb = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?\\s*(?:me|mein|mai|ke|ki|ka|par|pe)?\\s+$buyVerbsPattern$", RegexOption.IGNORE_CASE)
        buyRegexItemAmtVerb.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].trim()
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val isGenericGoods = item.lowercase(Locale.ROOT) in setOf("maal", "mal", "goods", "item", "items", "saman", "saaman")
            val hasLeadingConnector = Regex("^(?:ke|ki|ka|me|mein|mai|se|ko|from|to|for)\\b", RegexOption.IGNORE_CASE).containsMatchIn(item)
            if (amount > 0 && !hasLeadingConnector && (!isFillerOrStopWord(item) || isGenericGoods) && !item.endsWith(" se") && !item.endsWith(" from") && !item.startsWith("from ") && !item.startsWith("se ") && !item.contains(" se ") && !item.contains(" from ")) {
                val resolvedItem = if (isGenericGoods) "Goods" else item
                return makeHinglishPurchase(context, resolvedItem, "Cash-in-hand", amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        // ── Order-independent purchase extractor (amount first OR last) ──
        val purchaseVerbs = setOf("kharide", "kharida", "kharidi", "khareed", "bought", "buy", "purchase", "purchased")
        val roleConnectors = setOf("ko", "se", "ne", "ka", "ke", "ki", "mein", "me", "par", "pe", "aur", "and", "or", "to", "from", "for", "of", "at")
        val roleAux = setOf("kiya", "karna", "karo", "kare", "hai", "hain", "tha", "thi", "hua", "huye", "hui", "in")
        val unitWords = setOf("kilo", "kila", "kg", "kgs", "gram", "grams", "gm", "gms", "litre", "liter", "litres", "liters", "ltr", "ltrs",
            "piece", "pieces", "pc", "pcs", "dozen", "doz", "bottle", "bottles", "btl", "btls", "packet", "packets", "pkt", "pkts", "meter", "meters", "mtr",
            "bag", "bags", "bori", "boriya", "darjan", "nag", "peti", "box")
        val qTokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        val verbIdx = qTokens.indexOfFirst { it.lowercase(Locale.ROOT) in purchaseVerbs }
        if (verbIdx > 0) {
            val amountM = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya)?\\s*(?:ke|ki|ka|me|mein|mai)\\b", RegexOption.IGNORE_CASE).find(q)
            val amount = amountM?.groupValues?.get(1)?.toDoubleOrNull()
            if (amount != null && amount > 0) {
                val beforeVerb = qTokens.subList(0, verbIdx)
                val qtyIdx = beforeVerb.indices.firstOrNull { idx ->
                    val wNum = beforeVerb[idx].toDoubleOrNull()
                    if (wNum == null || wNum == amount) false
                    else {
                        if (idx > 0 && beforeVerb[idx - 1].lowercase(Locale.ROOT) !in roleConnectors && beforeVerb[idx - 1].lowercase(Locale.ROOT) !in roleAux) {
                            false
                        } else {
                            true
                        }
                    }
                } ?: -1
                val qty = if (qtyIdx >= 0) beforeVerb[qtyIdx].toDoubleOrNull() ?: 1.0 else 1.0
                val candidateWords = beforeVerb.filterIndexed { idx, w ->
                    val wNum = w.toDoubleOrNull()
                    if (wNum == amount) return@filterIndexed false
                    if (idx == qtyIdx) return@filterIndexed false
                    w.lowercase(Locale.ROOT) !in roleConnectors && w.lowercase(Locale.ROOT) !in roleAux
                }
                val nonUnits = candidateWords.filter { it.lowercase(Locale.ROOT) !in unitWords }
                val itemWords = if (nonUnits.isNotEmpty()) nonUnits else candidateWords
                var partyWords = qTokens.subList(verbIdx + 1, qTokens.size)
                    .filter { w -> w.toDoubleOrNull() != amount && w.lowercase(Locale.ROOT) !in roleConnectors && w.lowercase(Locale.ROOT) !in roleAux }
                if (partyWords.isEmpty()) {
                    val seIdx = beforeVerb.indexOfFirst { it.lowercase(Locale.ROOT) == "se" }
                    if (seIdx > 0) {
                        partyWords = beforeVerb.subList(0, seIdx).filter { it.lowercase(Locale.ROOT) !in roleConnectors && it.lowercase(Locale.ROOT) !in roleAux }
                    }
                }
                if (itemWords.isNotEmpty()) {
                    val item = itemWords.joinToString(" ")
                    val party = partyWords.joinToString(" ").trim()
                    if (party.isBlank() || isFillerOrStopWord(party)) return makeHinglishPurchase(context, item, "Cash-in-hand", amount, qty, qOriginal, extractedDate, isPerUnit)
                    return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
                }
            }
        }

        // "qty item kharide <party> <amount>" with a BARE party (no "se" connector)
        val buyRegexBareParty = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?([^0-9\u0966-\u096F\\s]+(?:\\s+[^0-9\u0966-\u096F\\s]+)?)\\s+$buyVerbsPattern\\s+([a-z\u0900-\u097F\\s]+?)\\s+(\\d+(?:\\.\\d+)?)\\s*$amtSuffix", RegexOption.IGNORE_CASE)
        buyRegexBareParty.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2]
            val party = m.groupValues[3]
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        val buyRegexQtyItemAmtParty = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+$buyVerbsPattern\\s+(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya)?\\s*(?:me|mein|mai|ke|ki|ka)?\\s+([a-z\u0900-\u097F\\s]+?)\\s+se", RegexOption.IGNORE_CASE)
        buyRegexQtyItemAmtParty.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2]
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val party = m.groupValues[4]
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }


        // "10 bori cement kharida Raj se 3500 me" / "१० बोरी सीमेंट खरीदा राज से ३५०० में"
        val buyRegexQtyItemVerbPartySeAmt = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+$buyVerbsPattern\\s+([a-z\u0900-\u097F\\s]+?)\\s+(?:se|from)\\s+(\\d+(?:\\.\\d+)?)\\s*$amtSuffix", RegexOption.IGNORE_CASE)
        buyRegexQtyItemVerbPartySeAmt.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2]
            val party = m.groupValues[3]
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        val buyRegex1 = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+$buyVerbsPattern\\s+(\\d+(?:\\.\\d+)?)\\s*$amtSuffix", RegexOption.IGNORE_CASE)
        buyRegex1.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2].trim()
            val amount = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val isGenericGoods = item.lowercase() in setOf("maal", "mal", "goods", "item", "items", "saman", "saaman")
            if (amount > 0 && (!isFillerOrStopWord(item) || isGenericGoods) && !q.contains(" se ")) {
                val resolvedItem = if (isGenericGoods) "Goods" else item
                return makeHinglishPurchase(context, resolvedItem, "Cash-in-hand", amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }
        
        val buyRegex2 = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-|\\bme\\b|\\bmein\\b|\\bmai\\b|\\bke\\b|\\bki\\b|\\bka\\b)\\s*(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+$buyVerbsPattern(?!\\s*\\d)", RegexOption.IGNORE_CASE)
        buyRegex2.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val qty = m.groupValues[2].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[3].trim()
            val isGenericGoods = item.lowercase() in setOf("maal", "mal", "goods", "item", "items", "saman", "saaman")
            if (amount > 0 && (!isFillerOrStopWord(item) || isGenericGoods) && !q.contains(" se ")) {
                val resolvedItem = if (isGenericGoods) "Goods" else item
                return makeHinglishPurchase(context, resolvedItem, "Cash-in-hand", amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }
        
        val buyRegex3 = Regex("(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+$buyVerbsPattern\\s+([a-z\u0900-\u097F\\s]+?)\\s+se\\s+(\\d+(?:\\.\\d+)?)\\s*$amtSuffix", RegexOption.IGNORE_CASE)
        buyRegex3.find(q)?.let { m ->
            val qty = m.groupValues[1].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[2]
            val party = m.groupValues[3]
            val amount = m.groupValues[4].toDoubleOrNull() ?: 0.0
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }
        
        val buyRegex4 = Regex("(\\d+(?:\\.\\d+)?)\\s*$amtSuffix\\s+(?:(\\d+(?:\\.\\d+)?)\\s+)?(.+?)\\s+$buyVerbsPattern(?!\\s*\\d)\\s+([a-z\u0900-\u097F\\s]+?)\\s+se", RegexOption.IGNORE_CASE)
        buyRegex4.find(q)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val qty = m.groupValues[2].toDoubleOrNull() ?: 1.0
            val item = m.groupValues[3]
            val party = m.groupValues[4]
            if (amount > 0 && !isFillerOrStopWord(party)) {
                return makeHinglishPurchase(context, item, party, amount, qty, qOriginal, extractedDate, isPerUnit)
            }
        }

        return null
    }

    /**
     * Last-resort local fallback when every strict regex tier above fails to build a complete
     * voucher. Detects a bare sale/purchase verb and figures out which single piece is missing
     * (amount, item, or a party the user clearly referenced but whose name didn't resolve), so the
     * chat can ask one specific follow-up question instead of silently giving up or escalating to
     * the network-dependent cloud clarification loop.
     */
    internal fun tryExtractIncompleteTransaction(query: String): AiResponse.NeedsInfo? {
        var q = normalizeHindiNumbersCore(query)
        q = Regex("\\bli\\b", RegexOption.IGNORE_CASE).replace(q, "kharide")
        q = Regex("\\bkhar(?:i|ee?)d[a-z]*\\b", RegexOption.IGNORE_CASE).replace(q, "kharide")
        q = Regex("\\b(bought|purchased|buy)\\b", RegexOption.IGNORE_CASE).replace(q, "kharide")
        q = Regex("\\b(becha|bechi|bachi|bache|bech|becho|bika|biki|bikri|bichi)\\b", RegexOption.IGNORE_CASE).replace(q, "beche")
        q = Regex("\\b(sold|sell)\\b", RegexOption.IGNORE_CASE).replace(q, "beche")

        val hasSaleVerb = Regex("\\bbeche\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
        val hasBuyVerb = Regex("\\bkharide\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
        val extractedDate = parseDateFromQuery(query) ?: "today"

        // Payment/Receipt never have an item — the only thing worth asking for is a missing
        // amount. Only considered once sale/purchase verbs are ruled out, since "diya"/"liya" are
        // themselves ambiguous with sale/purchase phrasing elsewhere in this file.
        if (!hasSaleVerb && !hasBuyVerb) {
            val payVerb = Regex("\\b(?:diya|diye|de diya|paid|pay|gave|given|chukaya|udhar diya|udhaar diya|bhara|bhare|bhari)\\b", RegexOption.IGNORE_CASE)
            val recvVerb = Regex("\\b(?:liya|liye|lena|lene|mila|mile|aaya|aaye|received|receipt|udhaar liya|udhar liya|wapas diya|wapas diye|lautaya|lautaye)\\b", RegexOption.IGNORE_CASE)
            val isPayment = payVerb.containsMatchIn(q)
            val isReceipt = !isPayment && recvVerb.containsMatchIn(q)
            if (!isPayment && !isReceipt) return null
            val amt = Regex("\\d[\\d,]*(?:\\.\\d+)?").find(q)?.value?.replace(",", "")?.toDoubleOrNull()?.takeIf { it > 0 }
            if (amt != null) return null // amount present — some other reason it didn't resolve; don't guess further
            val party = extractParty(q)
            val txnType = if (isPayment) "Payment" else "Receipt"
            return AiResponse.NeedsInfo(
                txnType, null, party, null, null, extractedDate, "amount",
                "Got it${if (party != null) ", a $txnType ${if (isPayment) "to" else "from"} $party" else ""} — what is the amount (in ₹)?"
            )
        }

        val txnType = if (hasBuyVerb) "Purchase" else "Sales"
        val verbPattern = if (hasBuyVerb) "kharide" else "beche"

        // Amount only counts if it sits right next to the verb (at most a currency/connector word
        // between) — the same shape every strict buy/sell regex above requires. A bare number
        // elsewhere in the sentence is almost always a quantity, not a price.
        val amountNearVerb = Regex(
            "(\\d[\\d,]*(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?\\s*(?:mein|me|mai|ke|ki|ka|par|pe)?\\s+$verbPattern" +
                "|$verbPattern\\s+(\\d[\\d,]*(?:\\.\\d+)?)\\s*(?:rs|rupees|rupaye|rupya|/-)?",
            RegexOption.IGNORE_CASE
        ).find(q)
        val amount = amountNearVerb?.let { m -> m.groupValues[1].ifBlank { m.groupValues[2] }.replace(",", "").toDoubleOrNull() }
            ?.takeIf { it > 0 }

        val connectorPresent = Regex("\\b(?:se|ko|from|to)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
        val party = extractParty(q)

        var remainder = Regex(
            "\\b(?:beche|becha|bechi|bech|bikri|kharide|kharida|kharidi|khareed|bought|buy|purchase|purchased|sold|sell|se|ko|ne|ka|ki|ke|from|to|for|at|of|in|me|mein|mai|par|pe|rs|rupees|rupaye|rupya)\\b",
            RegexOption.IGNORE_CASE
        ).replace(q, " ")
        remainder = Regex("\\d[\\d,]*(?:\\.\\d+)?").replace(remainder, " ")
        party?.let { p -> remainder = remainder.replace(p, " ", ignoreCase = true) }
        val item = remainder.trim().split(Regex("\\s+")).filter { it.length > 1 }.joinToString(" ").takeIf { it.isNotBlank() }

        val partyLabel = if (txnType == "Sales") "customer's" else "supplier's"
        return when {
            amount == null -> AiResponse.NeedsInfo(
                txnType, item, party, null, null, extractedDate, "amount",
                "Got it${if (item != null) ", a $txnType of $item" else ""}${if (party != null) " ${if (txnType == "Sales") "to" else "from"} $party" else ""} — what is the amount (in ₹)?"
            )
            item == null -> AiResponse.NeedsInfo(
                txnType, null, party, amount, 1.0, extractedDate, "item",
                "Got the $txnType amount ₹${amount.toLong()} — what is the item?"
            )
            connectorPresent && party == null -> AiResponse.NeedsInfo(
                txnType, item, null, amount, 1.0, extractedDate, "party",
                "Got $item for ₹${amount.toLong()} — what is the $partyLabel name?"
            )
            else -> null
        }
    }

    suspend fun chatWithFinance(context: Context, query: String, financialContext: String, history: String): AiResult = withContext(Dispatchers.Default) {
        val reportResult = parseReportCommandLocal(query)
        if (reportResult != null) return@withContext AiResult(reportResult, Confidence.HIGH, "regex_report")

        // ── Knowledge/tax gate (defense-in-depth): a question must never reach the voucher regexes.
        //    Runs before the Hinglish/smart/voice parsers so "is paying cash above 10000 illegal?"
        //    answers from the law corpus or escalates to cloud instead of drafting a voucher. ──
        if (SmartFinancePrompts.isTaxLawQuery(query) || isGstQuery(query)) {
            val gstAnswer = GstKnowledgeBase.answer(context, query)
            if (gstAnswer != null) {
                return@withContext AiResult(AiResponse.Message(gstAnswer), Confidence.HIGH, "gst_knowledge")
            }
            // Not in the local GST corpus (e.g. an income-tax question) — no confidence, escalate.
            return@withContext AiResult(AiResponse.Message(""), Confidence.NONE, "knowledge")
        }

        val normalized = normalizeQuery(query); val qLow = normalized.lowercase(Locale.ROOT).trim()
        val greetings = setOf("hi", "hello", "hey", "namaste", "hola", "greetings", "helafin", "helper")
        val helpWords = setOf("help", "what can you do", "what is this", "how to use", "commands", "features", "capabilities", "what do you do", "help me")
        if (greetings.contains(qLow)) return@withContext AiResult(AiResponse.Message("Hi! I'm Helafin, your offline finance helper. How can I help you today? You can ask me to record transactions (e.g., 'Sold 5 bananas to Amit for 200' or 'Ramesh ko 5000 diye') or query reports/balances."), Confidence.HIGH, "regex_greeting")
        if (helpWords.any { qLow.contains(it) }) return@withContext AiResult(AiResponse.Message("I am Helafin, your offline financial assistant. Here is what I can do:\n\n1. **Record Transactions**:\n   • 'Sold 5 bananas to Amit for 200'\n   • 'Ramesh ko 5000 diye' (Payment)\n   • 'Gagan se 2000 received' (Receipt)\n\n2. **Check Balances**:\n   • 'What is Cash-in-hand balance?'\n   • 'Show Ramesh balance'\n\n3. **View Reports**:\n   • 'Show sales today'\n   • 'Show purchase this month'\n   • 'Show profit yesterday'"), Confidence.HIGH, "regex_help")
        val cachedJson = getCachedJson(context, normalized)
        if (!cachedJson.isNullOrBlank()) return@withContext AiResult(parseAiJson(cachedJson), Confidence.HIGH, "cache")
        val hinglishResult = tryParseHinglish(context, query)
        if (hinglishResult != null) return@withContext AiResult(hinglishResult, Confidence.HIGH, "regex_hinglish")
        val smartResult = smartRegexParser(context, query)
        if (smartResult != null) {
            // Lower confidence if amount is zero or party name is missing
            val conf = if (smartResult.amount == null || smartResult.amount == 0.0 || smartResult.partyName.isNullOrBlank())
                Confidence.MEDIUM else Confidence.HIGH
            return@withContext AiResult(smartResult, conf, "regex_smart")
        }
        val regexResponse = parseVoiceCommandLocal(query)
        if (regexResponse != null) {
            // Voice parser responses are less reliable — MEDIUM at best
            val conf = when (regexResponse) {
                is AiResponse.Message -> Confidence.MEDIUM
                is AiResponse.DraftVoucher -> if (regexResponse.amount == null || regexResponse.amount == 0.0)
                    Confidence.MEDIUM else Confidence.HIGH
                else -> Confidence.HIGH
            }
            return@withContext AiResult(regexResponse, conf, "regex_voice")
        }
        // Last resort before giving up / escalating to the cloud: a sale/purchase verb was there
        // but something required didn't resolve — ask for exactly that instead of staying silent.
        val partialTxn = tryExtractIncompleteTransaction(query)
        if (partialTxn != null) return@withContext AiResult(partialTxn, Confidence.HIGH, "regex_partial_txn")
        return@withContext AiResult(AiResponse.Message("Hi, I'm $ASSISTANT_NAME. I'm running fully offline. I couldn't understand that. Try saying: \"Sold item to John for 500\" or \"Ramesh ko 1000 rupaye diye\"."), Confidence.NONE, "regex_none")
    }

    private fun cleanAndExtractJson(input: String): String {
        var cleaned = input.trim()
        if (cleaned.startsWith("```")) { val firstNewline = cleaned.indexOf('\n'); cleaned = if (firstNewline != -1) cleaned.substring(firstNewline).trim() else cleaned.substring(3).trim(); if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length - 3).trim() }
        if (cleaned.startsWith("json", ignoreCase = true)) cleaned = cleaned.substring(4).trim()
        val firstBrace = cleaned.indexOf('{'); val firstBracket = cleaned.indexOf('['); val startIdx = if (firstBrace != -1 && firstBracket != -1) Math.min(firstBrace, firstBracket) else if (firstBrace != -1) firstBrace else firstBracket
        val lastBrace = cleaned.lastIndexOf('}'); val lastBracket = cleaned.lastIndexOf(']'); val endIdx = if (lastBrace != -1 && lastBracket != -1) Math.max(lastBrace, lastBracket) else if (lastBrace != -1) lastBrace else lastBracket
        if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) cleaned = cleaned.substring(startIdx, endIdx + 1)
        return cleaned
    }

    internal fun parseAiJson(json: String): AiResponse {
        try {
            val cleaned = cleanAndExtractJson(json); val obj = JSONObject(cleaned); val rawType = obj.optString("type", "").trim().uppercase(Locale.US)
            if (rawType == "MESSAGE") return AiResponse.Message(obj.optString("text", "I'm not sure what to say."))
            if (obj.has("type") && obj.has("amount")) {
                val type = when (rawType) { "PAYMENT" -> "Payment"; "RECEIPT" -> "Receipt"; "CONTRA" -> "Contra"; "JOURNAL" -> "Journal"; else -> rawType.replaceFirstChar { it.uppercase(Locale.US) } }
                val amount = obj.optDouble("amount", 0.0); val narration = obj.optString("narration").takeIf { it.isNotBlank() }
                val drLedger = obj.optString("dr_ledger").ifBlank { obj.optString("dr") }; val crLedger = obj.optString("cr_ledger").ifBlank { obj.optString("cr") }; val singleLedger = obj.optString("ledger")
                if (amount <= 0) return AiResponse.Message("I understood the voucher type, but I missed the amount.")

                // Safety net: this flat dr_ledger/cr_ledger schema has no concept of stock items,
                // so when the cloud model is given a sale/purchase of goods it doesn't recognise
                // (e.g. "seb" = Hindi for apple), it sometimes names the item itself as a ledger to
                // debit/credit instead of using the items-aware draft_voucher schema — producing a
                // nonsense "Dr <item>, Cr Cash" journal. Detect that case via the item dictionary and
                // rebuild it as a real cash Sales voucher with a proper inventory line instead.
                if (drLedger.isNotBlank() && crLedger.isNotBlank()) {
                    fun asItemName(raw: String): String? {
                        val resolved = LanguageTranslationUtils.translateItem(raw)
                        return resolved.takeIf { it.isNotBlank() && !it.equals(raw.trim(), ignoreCase = true) }
                    }
                    val drItem = asItemName(drLedger)
                    val crItem = asItemName(crLedger)
                    val (itemName, ledgerSide) = when {
                        drItem != null && crItem == null -> drItem to crLedger
                        crItem != null && drItem == null -> crItem to drLedger
                        else -> null to null
                    }
                    if (itemName != null && ledgerSide != null) {
                        return AiResponse.DraftVoucher(
                            "Sales", "today", null,
                            listOf(DraftEntry(ledgerSide, true, amount), DraftEntry("Sales Account", false, amount)),
                            listOf(DraftInventoryItem(itemName, 1.0, amount, amount)),
                            narration
                        )
                    }
                }

                val (dr, cr) = when { drLedger.isNotBlank() && crLedger.isNotBlank() -> drLedger to crLedger; rawType == "PAYMENT" -> (singleLedger.ifBlank { drLedger }.ifBlank { "Suspense" }) to (crLedger.ifBlank { "Cash-in-hand" }); rawType == "RECEIPT" -> (drLedger.ifBlank { "Cash-in-hand" }) to (singleLedger.ifBlank { crLedger }.ifBlank { "Suspense" }); else -> (drLedger.ifBlank { "Suspense" }) to (crLedger.ifBlank { "Suspense" }) }
                return AiResponse.DraftVoucher(type, "today", null, listOf(DraftEntry(dr, true, amount), DraftEntry(cr, false, amount)), emptyList(), narration)
            }
            if (obj.has("transactions")) {
                val txArray = obj.getJSONArray("transactions"); val transactionsList = mutableListOf<GeminiTransaction>()
                for (i in 0 until txArray.length()) {
                    val txObj = txArray.getJSONObject(i); val entitiesArr = txObj.optJSONArray("entities"); val entitiesList = mutableListOf<GeminiEntity>()
                    if (entitiesArr != null) { for (j in 0 until entitiesArr.length()) { val e = entitiesArr.getJSONObject(j); entitiesList.add(GeminiEntity(e.optString("role"), e.optString("raw_text_mention"), e.optString("suggested_ledger_name"), e.optDouble("amount", 0.0), e.optString("likely_side").takeIf { it.isNotEmpty() })) } }
                    val itemsArr = txObj.optJSONArray("items"); val itemsList = mutableListOf<DraftInventoryItem>()
                    if (itemsArr != null) { for (k in 0 until itemsArr.length()) { val iObj = itemsArr.getJSONObject(k); itemsList.add(DraftInventoryItem(iObj.optString("name"), iObj.optDouble("qty", 1.0), iObj.optDouble("rate", 0.0), iObj.optDouble("amount").takeIf { !it.isNaN() } ?: (iObj.optDouble("qty", 1.0) * iObj.optDouble("rate", 0.0)), iObj.optString("unit").takeIf { it != "null" })) } }
                    transactionsList.add(GeminiTransaction(txObj.optString("intent"), txObj.optString("suggested_voucher_type", "Journal"), txObj.optString("narrative_summary").takeIf { it != "null" }, entitiesList, itemsList))
                }
                return AiResponse.ParsedTransactions(GeminiTransactionsResponse(transactionsList))
            }
            if (obj.has("intent") && obj.getString("intent") == "CREATE_VOUCHER") return AiResponse.VoucherIntentResponse(VoucherIntent("CREATE_VOUCHER", obj.optString("referenceTag").takeIf { it.isNotEmpty() }, obj.optDouble("amount").takeIf { !it.isNaN() }, obj.optString("paymentMode").takeIf { it.isNotEmpty() }, obj.optString("narration").takeIf { it.isNotEmpty() }, obj.optBoolean("isInfoMissing"), obj.optString("clarificationQuestion").takeIf { it.isNotEmpty() }))
            val action = obj.optString("action")
            return when (action) {
                "draft_voucher" -> {
                    val amount = obj.optDouble("amount", 0.0); val partyName = obj.optString("party_name", "Unknown"); val type = obj.optString("voucher_type"); val isCash = obj.optBoolean("is_cash", false); val entries = mutableListOf<DraftEntry>()
                    when (type.lowercase()) { "sales", "sale" -> { entries.add(DraftEntry(if (isCash || partyName.isBlank()) "Cash-in-hand" else partyName, true, amount)); entries.add(DraftEntry("Sales Account", false, amount)) }; "purchase" -> { entries.add(DraftEntry("Purchase Account", true, amount)); entries.add(DraftEntry(if (isCash || partyName.isBlank()) "Cash-in-hand" else partyName, false, amount)) }; "payment" -> { entries.add(DraftEntry(partyName, true, amount)); entries.add(DraftEntry("Cash-in-hand", false, amount)) }; "receipt" -> { entries.add(DraftEntry("Cash-in-hand", true, amount)); entries.add(DraftEntry(partyName, false, amount)) }; else -> entries.add(DraftEntry(partyName, true, amount)) }
                    val invArr = obj.optJSONArray("items"); val inventory = mutableListOf<DraftInventoryItem>()
                    if (invArr != null) {
                        for (i in 0 until invArr.length()) {
                            val item = invArr.getJSONObject(i)
                            val rawName = item.optString("name")
                            val rawQty = item.optDouble("qty", 1.0)
                            val rawUnit = item.optString("unit").takeIf { it.isNotBlank() && it != "null" }
                            val (canonicalName, parsedQty, parsedUnit) = LanguageTranslationUtils.canonicalItemDetails(rawName, rawQty, rawUnit)
                            val finalRate = item.optDouble("rate", 0.0)
                            val finalAmt = item.optDouble("amount").takeIf { !it.isNaN() && it > 0 } ?: (parsedQty * finalRate)
                            inventory.add(DraftInventoryItem(canonicalName, parsedQty, finalRate, finalAmt, parsedUnit))
                        }
                    }
                    AiResponse.DraftVoucher(type, obj.optString("date"), null, entries, inventory, obj.optString("narration"))
                }
                "draft_ledger" -> AiResponse.DraftLedger(obj.optString("name"), obj.optString("group"), obj.optDouble("opening_balance", 0.0))
                "create_ledgers" -> {
                    val arr = obj.optJSONArray("ledgers")
                    val drafts = mutableListOf<AiResponse>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val l = arr.getJSONObject(i)
                            drafts.add(AiResponse.DraftLedger(l.optString("name"), l.optString("group", "Sundry Debtors"), l.optDouble("opening_balance", 0.0)))
                        }
                    }
                    // Fallback: a flat array of name strings under "names"
                    if (drafts.isEmpty()) {
                        val namesArr = obj.optJSONArray("names")
                        if (namesArr != null) {
                            for (i in 0 until namesArr.length()) {
                                val n = namesArr.optString(i).trim()
                                if (n.isNotEmpty()) drafts.add(AiResponse.DraftLedger(n, "Sundry Debtors", 0.0))
                            }
                        }
                    }
                    if (drafts.size == 1) drafts.first() else AiResponse.MultiAction(drafts)
                }
                "query_balance" -> AiResponse.QueryBalance(obj.optString("ledger"))
                "query_stock" -> AiResponse.QueryStock(obj.optString("item"))
                "query_sales" -> AiResponse.QuerySales(obj.optString("period"))
                "query_receivables" -> AiResponse.QueryCollectables
                "query_payables" -> AiResponse.QueryPayables
                "message" -> AiResponse.Message(obj.optString("text"))
                // ── DeepSeek/cloud AI chart and table generation ──
                "generate_chart" -> {
                    val chartType = obj.optString("chartType", "bar")
                    val title = obj.optString("title", "Chart")
                    val labelsArr = obj.optJSONArray("labels")
                    val labels = mutableListOf<String>()
                    if (labelsArr != null) {
                        for (i in 0 until labelsArr.length()) labels.add(labelsArr.getString(i))
                    }
                    val datasetsArr = obj.optJSONArray("datasets")
                    val datasets = mutableListOf<ChartDataset>()
                    if (datasetsArr != null) {
                        for (i in 0 until datasetsArr.length()) {
                            val ds = datasetsArr.getJSONObject(i)
                            val valsArr = ds.optJSONArray("values")
                            val values = mutableListOf<Double>()
                            if (valsArr != null) {
                                for (j in 0 until valsArr.length()) values.add(valsArr.getDouble(j))
                            }
                            datasets.add(ChartDataset(
                                label = ds.optString("label", ""),
                                values = values,
                                color = ds.optString("color", "").takeIf { it.isNotEmpty() }
                            ))
                        }
                    }
                    val rawCsv = obj.optString("rawCsv", buildCsvFromChartData(labels, datasets))
                    AiResponse.QueryChart(chartType, title, labels, datasets, rawCsv)
                }
                "generate_table" -> {
                    val title = obj.optString("title", "Data")
                    val columnsArr = obj.optJSONArray("columns")
                    val columns = mutableListOf<String>()
                    if (columnsArr != null) {
                        for (i in 0 until columnsArr.length()) columns.add(columnsArr.getString(i))
                    }
                    val rowsArr = obj.optJSONArray("rows")
                    val rows = mutableListOf<List<String>>()
                    if (rowsArr != null) {
                        for (i in 0 until rowsArr.length()) {
                            val rowArr = rowsArr.getJSONArray(i)
                            val row = mutableListOf<String>()
                            for (j in 0 until rowArr.length()) row.add(rowArr.optString(j, ""))
                            rows.add(row)
                        }
                    }
                    val rawCsv = obj.optString("rawCsv", buildCsvFromTable(columns, rows))
                    AiResponse.QueryDataTable(title, columns, rows, rawCsv)
                }
                else -> {
                    val fallbackText = obj.optString("text").ifBlank {
                        obj.optString("message").ifBlank {
                            obj.optString("answer").ifBlank {
                                obj.optString("response").ifBlank {
                                    obj.optString("reply")
                                }
                            }
                        }
                    }
                    if (fallbackText.isNotBlank()) {
                        AiResponse.Message(fallbackText)
                    } else {
                        AiResponse.Message(cleaned)
                    }
                }
            }
        } catch (e: Exception) {
            val fallback = cleanAndExtractJson(json)
            return AiResponse.Message(if (fallback.isNotBlank()) fallback else json.trim())
        }
    }

    // ── CSV builders for chart/table data ──
    private fun buildCsvFromChartData(labels: List<String>, datasets: List<ChartDataset>): String {
        val sb = StringBuilder()
        sb.append("Label")
        datasets.forEach { ds -> sb.append(",").append(escapeCsvField(ds.label)) }
        sb.append("\n")
        for (i in labels.indices) {
            sb.append(escapeCsvField(labels.getOrElse(i) { "" }))
            datasets.forEach { ds ->
                sb.append(",").append(if (i < ds.values.size) ds.values[i].toString() else "0")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun buildCsvFromTable(columns: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append(columns.joinToString(",") { escapeCsvField(it) }).append("\n")
        rows.forEach { row ->
            sb.append(row.joinToString(",") { escapeCsvField(it) }).append("\n")
        }
        return sb.toString()
    }

    private fun escapeCsvField(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n"))
            "\"${value.replace("\"", "\"\"")}\"" else value

    internal fun isGstQuery(query: String): Boolean {
        val q = query.lowercase(Locale.ROOT)
        val terms = listOf(
            "gst", "gstr", "input tax", "itc", "registration", "reverse charge", "composition",
            "e-way", "e-invoice", "hsn", "taxable", "exempt", "gstin", "advance ruling", "refund",
            "penalty", "tax invoice", "credit note", "debit note", "notification", "audit",
            "reconciliation", "tax rate", "gst act", "gst rule", "cgst", "sgst", "igst", "cess"
        )
        return terms.any { q.contains(it) }
    }
}
