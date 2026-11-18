package com.minhas.teller.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class SmartReceiptData(
    val date: Date?,
    val vendor: String?,
    val amount: Double?,
    val categorySuggestion: String?,
    val narration: String?
)

data class ScannedInventoryItem(
    val name: String,
    val rate: Double?,
    val unitGuess: String?,   // e.g. "kg", "g", "ml", "L", "pcs"
    val barcode: String? = null,  // scanned product barcode (EAN/UPC/QR), if any
    val rawOcrText: String = ""   // full OCR text for manual inspection
)

object OcrHelper {
    private const val TAG = "OcrHelper"

    // ── Standalone (bundled) recognizers – fully offline, model lives in APK ──
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val devanagariRecognizer = TextRecognition.getClient(
        DevanagariTextRecognizerOptions.Builder().build()
    )

    // On-device, free, offline detectors
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }
    private val imageLabeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }
    private val entityExtractor by lazy {
        EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        )
    }

    // ── Tesseract (lazy, only if traineddata files are present) ──
    private var tessApi: Any? = null  // com.googlecode.tesseract.android.TessBaseAPI
    private var tessReady: Boolean = false
    // TessBaseAPI is a singleton native object and is not thread-safe; serialize init + OCR so two
    // concurrent scans can't race setImage/getUTF8Text on it (native SIGSEGV).
    private val tessLock = Any()

    /**
     * Attempt to initialise Tesseract for a set of Indian-language scripts.
     * If the traineddata files aren't available yet, Tess is silently skipped
     * and we rely on ML Kit alone.
     */
    private fun ensureTesseract(context: Context) {
        synchronized(tessLock) {
            if (tessReady) return
            try {
                // Try to load the Tesseract class – only if the JAR is on the classpath
                val clazz = Class.forName("com.googlecode.tesseract.android.TessBaseAPI")
                val api = clazz.getDeclaredConstructor().newInstance()
                val dataPath = File(context.filesDir, "tesseract").absolutePath
                val initMethod = clazz.getMethod("init", String::class.java, String::class.java)

                // Check if at least Hindi traineddata exists
                val hinFile = File(dataPath, "tessdata/hin.traineddata")
                if (!hinFile.exists()) {
                    // Try to copy from assets
                    copyTessDataFromAssets(context, dataPath)
                }

                if (hinFile.exists()) {
                    initMethod.invoke(api, dataPath, "hin+eng")  // Hindi + English
                    tessApi = api
                    tessReady = true
                    Log.i(TAG, "Tesseract initialised with hin+eng")
                } else {
                    Log.i(TAG, "Tesseract traineddata not found — using ML Kit only")
                }
            } catch (e: ClassNotFoundException) {
                Log.i(TAG, "Tesseract library not available — using ML Kit only")
            } catch (e: Exception) {
                Log.w(TAG, "Tesseract init failed — falling back to ML Kit", e)
            }
        }
    }

    private fun copyTessDataFromAssets(context: Context, dataPath: String) {
        try {
            val tessDir = File(dataPath, "tessdata")
            if (!tessDir.exists()) tessDir.mkdirs()
            val assets = context.assets
            val files = assets.list("tessdata") ?: return
            for (filename in files) {
                val outFile = File(tessDir, filename)
                if (!outFile.exists()) {
                    assets.open("tessdata/$filename").use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    Log.i(TAG, "Copied tessdata/$filename from assets")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy tessdata from assets", e)
        }
    }

    private fun runTesseract(bitmap: Bitmap): String {
        synchronized(tessLock) {
            if (!tessReady || tessApi == null) return ""
            return try {
                val clazz = tessApi!!::class.java
                clazz.getMethod("setImage", Bitmap::class.java).invoke(tessApi, bitmap)
                clazz.getMethod("getUTF8Text").invoke(tessApi) as? String ?: ""
            } catch (e: Exception) {
                Log.e(TAG, "Tesseract OCR failed", e)
                ""
            }
        }
    }

    // ── Image preprocessing ──────────────────────────────────────────────────
    /**
     * Load, resize, and enhance a camera photo so OCR works reliably.
     * - Resizes to max 1920 px on the longest edge (ML Kit sweet-spot)
     * - Reads EXIF rotation and corrects orientation
     * - Converts to grayscale and boosts contrast for sharper text
     */
    private fun preprocessImage(context: Context, uri: Uri): Pair<Bitmap, Int> {
        // 1. Decode bounds to find native dimensions
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        // 2. Calculate sample size to target ~1920 px on the longest edge
        val maxDim = 1920
        val sampleSize = maxOf(1, maxOf(options.outWidth, options.outHeight) / maxDim)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }

        var bitmap: Bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: throw IllegalStateException("Cannot decode image from URI")

        // 3. Read EXIF rotation
        val rotation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90  -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) { 0 }

        // 4. Convert to grayscale and boost contrast
        bitmap = enhanceForOcr(bitmap)

        return Pair(bitmap, rotation)
    }

    /**
     * Grayscale + contrast stretch using a ColorMatrix.
     * This sharpens text edges for both ML Kit and Tesseract.
     */
    private fun enhanceForOcr(source: Bitmap): Bitmap {
        val w = source.width; val h = source.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // Luminance-based grayscale (perceptual weights) + contrast boost
        val cm = ColorMatrix(floatArrayOf(
            //   R      G      B      A     offset
            0.33f, 0.59f, 0.11f, 0f, 0f,    // Red   → grey
            0.33f, 0.59f, 0.11f, 0f, 0f,    // Green → grey
            0.33f, 0.59f, 0.11f, 0f, 0f,    // Blue  → grey
            0f,    0f,    0f,    1f, 0f      // Alpha unchanged
        ))
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        source.recycle()
        return out
    }

    // ── Receipt / vendor scanning (unchanged public API) ─────────────────────

    /**
     * Smart on-device extraction of the largest money amount and a date from free text,
     * using ML Kit Entity Extraction. Best-effort: returns (null, null) if the small
     * language model isn't available yet (it downloads once, then works fully offline).
     */
    private suspend fun extractMoneyAndDate(text: String): Pair<Double?, Date?> = try {
        entityExtractor.downloadModelIfNeeded().await()
        val params = EntityExtractionParams.Builder(text).build()
        val annotations = entityExtractor.annotate(params).await()
        var maxMoney: Double? = null
        var date: Date? = null
        for (annotation in annotations) {
            for (entity in annotation.entities) {
                when (entity.type) {
                    Entity.TYPE_MONEY -> {
                        val m = entity.asMoneyEntity()
                        if (m != null) {
                            val value = m.integerPart + m.fractionalPart / 100.0
                            if (value > 0 && (maxMoney == null || value > maxMoney!!)) maxMoney = value
                        }
                    }
                    Entity.TYPE_DATE_TIME -> {
                        val d = entity.asDateTimeEntity()
                        if (d != null && date == null) date = Date(d.timestampMillis)
                    }
                }
            }
        }
        Pair(maxMoney, date)
    } catch (e: Exception) {
        Log.e(TAG, "Entity extraction unavailable (offline fallback to regex)", e)
        Pair(null, null)
    }

    suspend fun analyzeReceipt(context: Context, uri: Uri): SmartReceiptData? = withContext(Dispatchers.Default) {
        val rawText = try {
            val image = InputImage.fromFilePath(context, uri)
            val visionText = latinRecognizer.process(image).await()
            visionText.text
        } catch (e: Exception) {
            return@withContext SmartReceiptData(null, null, null, null, "Error: ${e.message}")
        }
        if (rawText.isBlank()) return@withContext SmartReceiptData(null, null, null, null, "No text found.")

        val local = parseReceiptLocally(rawText)
        val (smartAmount, smartDate) = extractMoneyAndDate(rawText)
        return@withContext local.copy(
            amount = local.amount ?: smartAmount,
            date = smartDate ?: local.date
        )
    }

    suspend fun analyzeReceiptWithGemini(
        context: Context,
        uri: Uri,
        voucherType: String? = null
    ): Pair<SmartReceiptData?, String> = withContext(Dispatchers.Default) {
        val geminiResult = GeminiReceiptScanner.scan(context, uri, voucherType)
        if (geminiResult != null && (geminiResult.vendor != null || geminiResult.amount != null)) {
            Log.i(TAG, "Receipt scanned with Gemini Flash (type=$voucherType)")
            val data = SmartReceiptData(
                date = geminiResult.date,
                vendor = geminiResult.vendor,
                amount = geminiResult.amount,
                categorySuggestion = null,
                narration = geminiResult.narration
                    ?: "Scanned via AI${geminiResult.vendor?.let { " from $it" } ?: ""}"
            )
            return@withContext Pair(data, "gemini")
        }

        Log.i(TAG, "Falling back to on-device OCR")
        val ocrResult = analyzeReceipt(context, uri)
        Pair(ocrResult, "ocr")
    }

    private fun parseReceiptLocally(text: String): SmartReceiptData {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var amount: Double? = null

        val explicitRegex = Pattern.compile("(?i)(grand total|total amount|net amount|total payable|total|amount due|net pay)[:\\s]*([₹rs\\s]*[\\d,]+(?:\\.\\d{1,2})?)")

        for (line in lines) {
            if (line.contains("subtotal", true) || line.contains("tax", true) || line.contains("cgst", true) || line.contains("sgst", true)) continue
            val matcher = explicitRegex.matcher(line)
            if (matcher.find()) {
                val value = matcher.group(2)?.replace(",", "")?.replace("₹", "")?.replace("rs", "", true)?.trim()?.toDoubleOrNull()
                if (value != null && value > 0) amount = value
            }
        }

        if (amount == null) {
            val currencyRegex = Pattern.compile("([\\d]{1,3}(,[\\d]{3})*(?:\\.\\d{1,2})?)")
            var maxVal = 0.0
            for (line in lines) {
                if (line.count { it.isDigit() } > 10) continue
                val matcher = currencyRegex.matcher(line)
                while (matcher.find()) {
                    val v = matcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                    if (v > maxVal && v < 1000000) maxVal = v
                }
            }
            if (maxVal > 0) amount = maxVal
        }

        var date: Date? = null
        val dateRegex = Pattern.compile("([\\d]{1,2}[/.-][\\d]{1,2}[/.-][\\d]{2,4}|[\\d]{4}[/.-][\\d]{1,2}[/.-][\\d]{1,2}|[\\d]{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{2,4})", Pattern.CASE_INSENSITIVE)

        for (line in lines) {
            val matcher = dateRegex.matcher(line)
            if (matcher.find()) {
                date = parseDate(matcher.group(1) ?: "")
                if (date != null) break
            }
        }

        val noiseWords = listOf("tax invoice", "invoice", "bill of supply", "cash memo", "receipt", "gstin", "date", "ph:", "mo:", "tel:", "gst", "tin")
        val vendorLine = lines.firstOrNull { line ->
            val l = line.lowercase()
            line.length > 3 && noiseWords.none { l.contains(it) } && !l.matches(Regex(".*\\d.*"))
        } ?: "Unknown Vendor"

        val vendor = normalizeVendor(vendorLine)
        val category = guessCategory(vendor, null)

        return SmartReceiptData(date ?: Date(), vendor, amount, category, "Scanned Receipt from $vendor")
    }

    private fun parseDate(dateStr: String): Date? {
        val formats = listOf(
            "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy", "yyyy-MM-dd",
            "dd MMM yyyy", "MMM dd, yyyy", "dd MMMM yyyy", "d MMM yyyy"
        )
        for (fmt in formats) {
            try { return SimpleDateFormat(fmt, Locale.getDefault()).parse(dateStr) } catch (e: Exception) {}
        }
        return null
    }

    private fun normalizeVendor(vendor: String?): String {
        val v = vendor?.trim()?.replace(Regex("[^a-zA-Z0-9\\s&]"), "") ?: ""
        if (v.equals("Cash", true) || v.equals("Cash-in-hand", true)) return "Cash"
        return v.ifBlank { "Unknown Vendor" }
    }

    // ── INVENTORY ITEM SCANNER (revised) ─────────────────────────────────────
    // Tiered pipeline:
    //   1. Preprocess image (resize, greyscale, contrast, EXIF rotate)
    //   2. ML Kit Latin OCR
    //   3. ML Kit Devanagari OCR (fallback)
    //   4. Tesseract OCR (fallback for Tamil, Telugu, Bengali, etc.)
    //   5. Barcode scan + image labelling
    //   6. Smart name / price / unit extraction with relaxed heuristics
    //
    // Everything is on-device and free. No internet, no API keys.

    /** Scan a QR/barcode from an image (used for P2P sync pairing). Returns the raw decoded string. */
    suspend fun scanQrFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.Default) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            barcodeScanner.process(image).await()
                .firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
        } catch (e: Exception) {
            Log.e(TAG, "QR scan failed", e)
            null
        }
    }

    suspend fun scanInventoryItem(context: Context, uri: Uri): ScannedInventoryItem =
        withContext(Dispatchers.Default) {
            // ── Preprocessing ────────────────────────────────────────────
            val (bitmap, rotationDegrees) = try {
                preprocessImage(context, uri)
            } catch (e: Exception) {
                Log.e(TAG, "Image preprocess failed", e)
                return@withContext ScannedInventoryItem("", null, null)
            }

            val image = InputImage.fromBitmap(bitmap, rotationDegrees)

            // ── OCR tier 1: Latin (English) ──────────────────────────────
            var rawText = try {
                latinRecognizer.process(image).await().text
            } catch (e: Exception) { Log.e(TAG, "Latin OCR failed", e); "" }

            // ── OCR tier 2: Devanagari (Hindi / Marathi) ─────────────────
            if (rawText.isBlank()) {
                rawText = try {
                    devanagariRecognizer.process(image).await().text
                } catch (e: Exception) { Log.e(TAG, "Devanagari OCR failed", e); "" }
            }

            // ── OCR tier 3: Tesseract (Tamil, Telugu, Bengali, etc.) ─────
            if (rawText.isBlank()) {
                ensureTesseract(context)
                rawText = runTesseract(bitmap)
                if (rawText.isNotBlank()) {
                    Log.i(TAG, "Tesseract produced ${rawText.length} chars")
                }
            }

            // ── Barcode + image label in parallel ─────────────────────────
            val barcode = try {
                barcodeScanner.process(image).await()
                    .firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
            } catch (e: Exception) { Log.e(TAG, "Barcode scan failed", e); null }

            val topLabel = try {
                imageLabeler.process(image).await()
                    .filter { it.confidence >= 0.6f }
                    .maxByOrNull { it.confidence }?.text
            } catch (e: Exception) { Log.e(TAG, "Image labeling failed", e); null }

            // `image` is an InputImage wrapping this bitmap, so it must outlive every ML Kit
            // process() call above. Recycling it before barcode/label reads freed native pixels
            // and crashed the ML Kit native pipeline (SIGSEGV) — recycle only now that it's done.
            if (!bitmap.isRecycled) bitmap.recycle()

            // ── Nothing readable at all → best-effort from barcode / label ─
            if (rawText.isBlank()) {
                return@withContext ScannedInventoryItem(
                    name = topLabel.orEmpty(),
                    rate = null,
                    unitGuess = null,
                    barcode = barcode,
                    rawOcrText = ""
                )
            }

            val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }

            // ── 1. Extract price (MRP / rate) ──────────────────────────
            val priceRegex = Pattern.compile(
                "(?i)(?:mrp|price|rate|cost|rs\\.?|₹|₹)[\\s:]*([\\d,]+(?:\\.\\d{1,2})?)"
            )
            var rate: Double? = null
            for (line in lines) {
                val m = priceRegex.matcher(line)
                if (m.find()) {
                    rate = m.group(1)?.replace(",", "")?.toDoubleOrNull()
                    if (rate != null && rate > 0) break
                }
            }
            if (rate == null) {
                val fallbackPrice = Pattern.compile("(?:₹|Rs\\.?)\\s*([\\d,]+(?:\\.\\d{1,2})?)")
                for (line in lines) {
                    val m = fallbackPrice.matcher(line)
                    if (m.find()) {
                        rate = m.group(1)?.replace(",", "")?.toDoubleOrNull()
                        if (rate != null && rate > 0) break
                    }
                }
            }

            // ── 2. Detect unit ─────────────────────────────────────────
            val unitRegex = Pattern.compile(
                "(?i)\\b(\\d+(?:\\.\\d+)?\\s*)(kg|g|gm|gram|grams|ml|millilit[er]+|l\\b|lit[er]+|litr[es]+|pcs|pieces?|nos?|units?|pack|box|dozen|dz)\\b"
            )
            var unitGuess: String? = null
            for (line in lines) {
                val m = unitRegex.matcher(line)
                if (m.find()) {
                    unitGuess = normaliseUnit(m.group(2) ?: "")
                    break
                }
            }

            // ── 3. Pick item name (improved heuristics) ─────────────────
            // Noise-word list — trimmed to avoid false-positive rejections.
            // Removed: "protein", "energy", "store", "carbo", "made in"
            // (those were matching legitimate product names via substring).
            val noiseWords = listOf(
                "mrp", "price", "rate", "cost", "mfd", "mfg", "exp", "best before",
                "best in", "batch", "manufactured", "marketed", "packed", "packing",
                "gstin", "fssai", "lic no", "lic.no", "tel", "phone", "mob", "contact",
                "customer care", "helpline", "www", "http", ".com", "net wt",
                "net weight", "net qty", "net quantity", "gross", "barcode",
                "ingredients", "ingredient", "nutrition",
                "keep", "use by", "use before", "for best", "maximum retail",
                "incl. of", "inclusive of", "all taxes", "email",
                "country of origin", "consume", "shake well", "serving"
            )

            /**
             * Score a line on how "product-name-like" it is.
             * Revised to be friendlier to real-world product labels:
             * - Softer index penalty (0.2 vs 0.6) — names often appear mid-label
             * - ALL-CAPS bonus retained (common for brand names)
             * - Digit penalty softened — "5 Star", "7Up" etc. are real names
             * - Max length raised to 60 chars (was 45)
             */
            fun nameScore(line: String, index: Int): Double {
                val letters = line.count { it.isLetter() }
                val digits = line.count { it.isDigit() }
                val alphaRatio = letters.toDouble() / line.length.coerceAtLeast(1)
                val words = line.trim().split(Regex("\\s+")).size
                var s = alphaRatio * 5.0                         // letter-rich lines score higher
                if (line == line.uppercase() && letters >= 3) s += 2.5   // ALL-CAPS brand (raised from 2.0)
                s += if (words in 1..5) 2.0 else -1.5            // names are short phrases
                s -= index * 0.2                                  // gentler position penalty (was 0.6)
                s -= digits * 0.25                                // softened digit penalty (was 0.4)
                if (line.length > 30) s -= (line.length - 30) * 0.08  // softer long-line penalty
                return s
            }

            // Collect every line that looks like part of a product name.
            val nameCandidates = lines.withIndex()
                .filter { (_, line) ->
                    val l = line.lowercase()
                    val letters = line.count { it.isLetter() }
                    // Wider length window: 2..60 (was 2..45)
                    line.length in 2..60 &&
                        letters >= 2 &&
                        // Instead of hard "letters >= digits" filter, just require
                        // the line isn't > 70 % digits — allows names like "5 Star"
                        (letters.toDouble() / line.length.coerceAtLeast(1)) >= 0.3 &&
                        noiseWords.none { l.contains(it) } &&
                        !l.matches(Regex(".*\\d{5,}.*"))           // skip barcode-heavy lines
                }
                .toList()

            // Labels usually split the name across several lines (brand, product, size).
            // Anchor on the strongest line, then extend to the contiguous name-like lines
            // around it so we capture the full name instead of just the first word
            // (e.g. "MuscleBlaze Whey Protein 5.5 lbs" instead of "MuscleBlaze").
            val anchor = nameCandidates.maxByOrNull { (idx, line) -> nameScore(line, idx) }

            val nameLine = if (anchor != null) {
                val candidateIndices = nameCandidates.map { it.index }.toSet()
                val chosen = mutableListOf<Int>()
                var i = anchor.index
                while (i >= 0 && candidateIndices.contains(i)) { chosen.add(i); i-- }
                i = anchor.index + 1
                while (i < lines.size && candidateIndices.contains(i)) { chosen.add(i); i++ }
                chosen.sorted().joinToString(" ") { lines[it].trim() }
            } else {
                lines.firstOrNull() ?: ""
            }

            // Clean up the name
            val cleanName = nameLine
                .replace(Regex("[^\\w\\s&()\\-/'.,]"), " ")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
                .take(80)

            val finalName = if (cleanName.length < 3 && !topLabel.isNullOrBlank()) {
                topLabel.replaceFirstChar { it.uppercase() }
            } else cleanName

            if (rate == null) {
                rate = extractMoneyAndDate(rawText).first
            }

            ScannedInventoryItem(
                name = finalName,
                rate = rate,
                unitGuess = unitGuess,
                barcode = barcode,
                rawOcrText = rawText
            )
        }

    private fun normaliseUnit(raw: String): String = when (raw.lowercase().trim()) {
        "kg", "kgs"                          -> "kg"
        "g", "gm", "gram", "grams"          -> "g"
        "l", "lit", "liter", "litre",
        "liters", "litres"                   -> "L"
        "ml", "milliliter", "millilitre",
        "milliliters", "millilitres"         -> "ml"
        "pcs", "piece", "pieces"             -> "pcs"
        "no", "nos", "unit", "units"         -> "pcs"
        "pack"                               -> "pack"
        "box"                                -> "box"
        "dozen", "dz"                        -> "dozen"
        else                                 -> raw.lowercase().trim()
    }

    private fun guessCategory(vendor: String?, fallback: String?): String? {
        val v = vendor?.lowercase(Locale.getDefault()).orEmpty()
        return when {
            v.contains("starbucks") || v.contains("mcdonald") || v.contains("domino") || v.contains("pizza") || v.contains("kfc") || v.contains("burger") || v.contains("cafe") || v.contains("restaurant") || v.contains("hotel") || v.contains("bar") || v.contains("bakery") || v.contains("swiggy") || v.contains("zomato") -> "Food/Pantry"
            v.contains("uber") || v.contains("ola") || v.contains("meru") || v.contains("rapido") || v.contains("taxi") || v.contains("cab") -> "Transport"
            v.contains("shell") || v.contains("petrol") || v.contains("fuel") || v.contains("pump") || v.contains("oil") || v.contains("hp ") || v.contains("indian oil") || v.contains("bharat pet") -> "Fuel/Vehicle"
            v.contains("amazon") || v.contains("flipkart") || v.contains("myntra") || v.contains("ajio") || v.contains("retail") -> "Supplies"
            v.contains("big bazaar") || v.contains("dmart") || v.contains("walmart") || v.contains("supermarket") || v.contains("store") || v.contains("grocer") -> "Groceries"
            v.contains("reliance") && v.contains("jio") -> "Internet"
            v.contains("airtel") || v.contains("vodafone") || v.contains("vi ") || v.contains("bsnl") || v.contains("act fiber") -> "Phone/Internet"
            v.contains("bescom") || v.contains("power") || v.contains("electric") || v.contains("water") || v.contains("gas") -> "Utilities"
            v.contains("pharmacy") || v.contains("medical") || v.contains("hospital") || v.contains("clinic") || v.contains("dr.") || v.contains("doctor") || v.contains("apollo") || v.contains("medplus") -> "Medical Expenses"
            v.contains("pvr") || v.contains("inox") || v.contains("cinema") || v.contains("netflix") || v.contains("prime") || v.contains("hotstar") || v.contains("spotify") -> "Entertainment"
            v.contains("repair") || v.contains("service") || v.contains("garage") || v.contains("mechanic") || v.contains("auto parts") -> "Repairs & Maintenance"
            else -> fallback
        }
    }
}
