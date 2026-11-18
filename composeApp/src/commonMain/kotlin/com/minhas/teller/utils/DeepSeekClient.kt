package com.minhas.teller.utils

import android.util.Log
import com.minhas.teller.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * DeepSeek Cloud AI client for the Teller app.
 *
 * Follows the exact HTTP pattern from GeminiReceiptScanner.kt:
 * raw HttpURLConnection + org.json.JSONObject, no external libraries.
 *
 * DeepSeek uses Bearer header auth (unlike Gemini which uses query param).
 * DeepSeek API is OpenAI-compatible — same message/tool format.
 */
object DeepSeekClient {

    private const val TAG = "DeepSeekClient"
    private const val DEEPSEEK_URL = "https://api.deepseek.com/v1/chat/completions"
    // deepseek-chat was retired 2026-07-24 and now maps to deepseek-v4-flash (non-thinking mode).
    private const val MODEL = "deepseek-v4-flash"

    /**
     * Sends a chat completion request to DeepSeek.
     *
     * @param systemPrompt  The system prompt defining Helafin's role and rules
     * @param userQuery     The user's original query
     * @param financialContext  Current business context (ledger names, sales totals, etc.)
     * @param history       Recent conversation messages as role/content pairs
     * @return              The raw JSON response text, or null if the call failed
     */
    suspend fun chat(
        systemPrompt: String,
        userQuery: String,
        financialContext: String = "",
        history: List<Map<String, String>> = emptyList()
    ): String? = withContext(Dispatchers.Default) {

        val apiKey = BuildConfig.DEEPSEEK_API_KEY
        if (apiKey.isBlank()) {
            Log.d(TAG, "DeepSeek API key not configured, skipping cloud fallback")
            return@withContext null
        }

        try {
            // Build messages array: system + history + current query
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", buildSystemPrompt(systemPrompt, financialContext))
                })
                history.forEach { h ->
                    put(JSONObject().apply {
                        put("role", h["role"] ?: "user")
                        put("content", h["content"] ?: "")
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userQuery)
                })
            }

            val requestJson = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("temperature", 0.3)
                put("max_tokens", 2048)
                put("response_format", JSONObject().apply {
                    put("type", "json_object")
                })
            }

            val url = URL(DEEPSEEK_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")

            conn.outputStream.use { os ->
                os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "DeepSeek API error ${conn.responseCode}: ${errorBody.take(300)}")
                return@withContext null
            }

            val responseText = conn.inputStream.bufferedReader().readText()
            // Deliberately not logged: the response contains the user's financial data.

            // Extract the content text from the OpenAI-compatible response
            parseContent(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "DeepSeek API call failed: ${e.message}", e)
            null
        }
    }

    /**
     * Builds the full system prompt from the role definition + financial context.
     */
    private fun buildSystemPrompt(roleDefinition: String, financialContext: String): String {
        val sb = StringBuilder()
        sb.append(roleDefinition)
        if (financialContext.isNotBlank()) {
            sb.append("\n\n")
            sb.append("=== CURRENT BUSINESS CONTEXT ===\n")
            sb.append(financialContext)
            sb.append("\n=== END CONTEXT ===")
        }
        return sb.toString()
    }

    /**
     * Extracts the text content from an OpenAI-compatible chat completion response.
     */
    private fun parseContent(responseText: String): String? {
        return try {
            val json = JSONObject(responseText)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                Log.e(TAG, "No choices in DeepSeek response")
                return null
            }
            val message = choices.getJSONObject(0).optJSONObject("message")
            val content = message?.optString("content", "") ?: ""
            if (content.isBlank()) null else content.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse DeepSeek response: ${e.message}")
            null
        }
    }

    /**
     * Smart knowledge chat — answers GST law, income tax, app usage, and accounting questions.
     *
     * This is the cloud fallback used when [GemmaLLM.answerKnowledgeQuestion] returns null
     * (i.e. the 4B on-device model is not yet downloaded). Uses [SmartFinancePrompts] for the
     * system prompt so answers are consistent with on-device responses.
     *
     * Unlike [chat], this does NOT request JSON output — answers are free-form text.
     *
     * @param question  The user's knowledge question
     * @param history   Recent conversation for context (role/content pairs)
     * @return          The answer text, or null if the call failed / API key not set
     */
    suspend fun smartChat(
        question: String,
        history: List<Map<String, String>> = emptyList()
    ): String? = withContext(Dispatchers.Default) {

        val apiKey = BuildConfig.DEEPSEEK_API_KEY
        if (apiKey.isBlank()) {
            Log.d(TAG, "DeepSeek API key not configured, cannot answer knowledge question")
            return@withContext null
        }

        try {
            val systemPrompt = SmartFinancePrompts.buildKnowledgeSystemPrompt()
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                history.takeLast(6).forEach { h ->
                    put(JSONObject().apply {
                        put("role", h["role"] ?: "user")
                        put("content", h["content"] ?: "")
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", question)
                })
            }

            val requestJson = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("temperature", 0.4)
                put("max_tokens", 1024)
                // Free-form text response (no JSON schema constraint)
            }

            val url = java.net.URL(DEEPSEEK_URL)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 45_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")

            conn.outputStream.use { os ->
                os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "DeepSeek smartChat error ${conn.responseCode}: ${errorBody.take(300)}")
                return@withContext null
            }

            val responseText = conn.inputStream.bufferedReader().readText()
            parseContent(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "DeepSeek smartChat failed: ${e.message}", e)
            null
        }
    }

    /**
     * Second-opinion verifier for answers produced by the small on-device model.
     *
     * Sends the user's question along with the candidate answer and asks a stronger model to decide
     * whether it is correct and complete. Returns the corrected answer when it disagrees, or null
     * when the answer is accepted (the model replies exactly "OK"), the API is unavailable, or the
     * call fails — callers then keep the local answer.
     */
    suspend fun verifyAnswer(
        question: String,
        candidateAnswer: String
    ): String? = withContext(Dispatchers.Default) {

        val apiKey = BuildConfig.DEEPSEEK_API_KEY
        if (apiKey.isBlank()) {
            Log.d(TAG, "DeepSeek API key not configured, skipping answer verification")
            return@withContext null
        }

        try {
            val systemPrompt = buildString {
                append("You are a strict fact-checker for a finance app assistant.\n")
                append("A smaller on-device model produced the candidate answer below, and it may be wrong or incomplete.\n")
                append("Decide whether it correctly and completely answers the user's question.\n")
                append("If the candidate answer is correct and helpful, reply with exactly: OK\n")
                append("If it is wrong, incomplete, or off-topic, reply with a corrected, accurate, plain-language answer instead.")
            }

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "User question: $question\n\nCandidate answer:\n$candidateAnswer")
                })
            }

            val requestJson = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("temperature", 0.2)
                put("max_tokens", 1024)
                // Free-form text response (no JSON schema constraint)
            }

            val url = java.net.URL(DEEPSEEK_URL)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 45_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")

            conn.outputStream.use { os ->
                os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                Log.e(TAG, "DeepSeek verifyAnswer error ${conn.responseCode}: ${errorBody.take(300)}")
                return@withContext null
            }

            val responseText = conn.inputStream.bufferedReader().readText()
            val text = parseContent(responseText)?.trim()
            if (text == null || text.equals("OK", ignoreCase = true)) null else text
        } catch (e: Exception) {
            Log.e(TAG, "DeepSeek verifyAnswer failed: ${e.message}", e)
            null
        }
    }
}
