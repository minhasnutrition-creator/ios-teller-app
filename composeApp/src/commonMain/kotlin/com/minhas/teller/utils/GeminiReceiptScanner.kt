package com.minhas.teller.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.minhas.teller.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scans a receipt/invoice image using Gemini Flash API (cloud).
 * Falls back gracefully if the API key is missing or the network call fails.
 *
 * @param voucherType The type of voucher being created ("Sales", "Purchase", "Payment", "Receipt").
 *                    Used to give the AI context about what kind of document to expect.
 */
object GeminiReceiptScanner {
    private const val TAG = "GeminiScanner"
    private const val GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    private fun buildPrompt(voucherType: String?): String {
        val docTypeHint = when (voucherType) {
            "Sales" -> "This is a Sales invoice (a bill you issued to a customer). Look for the customer name, invoice date, and total amount."
            "Purchase" -> "This is a Purchase invoice (a bill from a supplier/vendor). Look for the supplier/shop name, invoice date, and total amount."
            "Payment" -> "This is a Payment receipt (money you paid out). Look for the payee name, date, and amount paid."
            "Receipt" -> "This is a Receipt (money you received). Look for the payer name, date, and amount received."
            else -> "This is an invoice or receipt. Extract whatever information is visible."
        }
        return """
You are a precise document data extractor. ${docTypeHint}
Be careful and accurate. If a field is not visible or unclear, use null.

Return ONLY valid JSON (no markdown, no extra text) in this exact format:
{
  "vendor": "Shop or company name on the document",
  "date": "Document date in yyyy-MM-dd format",
  "amount": total_amount_as_number_without_currency_symbol,
  "invoiceNumber": "Bill or invoice number if visible, else null",
  "narration": "Brief one-line description of the transaction"
}
""".trimIndent()
    }

    data class GeminiResult(
        val vendor: String?,
        val date: Date?,
        val amount: Double?,
        val invoiceNumber: String?,
        val narration: String?
    )

    suspend fun scan(context: Context, imageUri: Uri, voucherType: String? = null): GeminiResult? = withContext(Dispatchers.Default) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            Log.d(TAG, "Gemini API key not configured, skipping cloud scan")
            return@withContext null
        }

        try {
            // 1. Decode and resize image (keep under ~4MB to avoid API limits)
            val base64Image = uriToBase64(context, imageUri, maxWidth = 1024)
                ?: return@withContext null

            // 2. Build prompt with voucher type context
            val prompt = buildPrompt(voucherType)

            // 3. Build request body
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    })
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 1024)
                })
            }

            // 4. Call Gemini REST API
            val url = URL("$GEMINI_URL?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json")

            conn.outputStream.use { os ->
                os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "Gemini API error ${conn.responseCode}: $errorBody")
                return@withContext null
            }

            val responseText = conn.inputStream.bufferedReader().readText()
            // Deliberately not logged: the raw response contains the full extracted receipt.

            // 5. Parse response
            val responseJson = JSONObject(responseText)
            val candidates = responseJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                Log.e(TAG, "No candidates in Gemini response")
                return@withContext null
            }

            val text = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?.getJSONObject(0)
                ?.optString("text", "")
                ?: ""

            // Strip markdown code fences if present
            val jsonText = text.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            Log.d(TAG, "Extracted JSON: $jsonText")

            // 6. Parse the extracted JSON
            val data = JSONObject(jsonText)
            val vendor = data.optString("vendor", "").ifBlank { null }
            val date = parseDate(data.optString("date", "").ifBlank { null })
            val amount = data.optDouble("amount", Double.NaN).let { if (it.isNaN()) null else it }
            val invoiceNumber = data.optString("invoiceNumber", "").ifBlank { null }
            val narration = data.optString("narration", "").ifBlank { null }

            GeminiResult(
                vendor = if (vendor.isNullOrBlank() || vendor == "null") null else vendor,
                date = date,
                amount = amount,
                invoiceNumber = if (invoiceNumber.isNullOrBlank() || invoiceNumber == "null") null else invoiceNumber,
                narration = if (narration.isNullOrBlank() || narration == "null") null else narration
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gemini scan failed", e)
            null
        }
    }

    /**
     * Converts a content URI to a base64-encoded JPEG string, resizing to maxWidth pixels wide
     * to stay within API payload limits.
     */
    private fun uriToBase64(context: Context, uri: Uri, maxWidth: Int): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) return null

            // Resize if needed
            val resized = if (bitmap.width > maxWidth) {
                val ratio = maxWidth.toFloat() / bitmap.width
                val newHeight = (bitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val bytes = outputStream.toByteArray()
            outputStream.close()

            if (resized !== bitmap) resized.recycle()
            bitmap.recycle()

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode image", e)
            null
        }
    }

    private fun parseDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank() || dateStr == "null") return null
        val formats = listOf(
            "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy",
            "dd MMM yyyy", "MMM dd, yyyy", "dd MMMM yyyy"
        )
        for (fmt in formats) {
            try { return SimpleDateFormat(fmt, Locale.getDefault()).parse(dateStr.trim()) }
            catch (_: Exception) {}
        }
        return null
    }
}
