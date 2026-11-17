// MediaPipe deprecated LlmInference in its own SDK and there's no drop-in replacement wired up
// yet, so suppress at file level — the deprecated import itself warns, which an annotation on the
// object below can't reach. Revisit when MediaPipe's GenAI API settles.
@file:Suppress("DEPRECATION")

package com.minhas.teller.utils

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object GemmaLLM {
    private const val TAG = "GemmaLLM"
    // Gemma 3 1B int4 is ~528 MB; reject anything below 300 MB as a corrupt/partial download.
    private const val MIN_MODEL_SIZE_BYTES = 300_000_000L

    @Volatile private var llmInference: LlmInference? = null
    @Volatile private var isModelLoaded = false
    @Volatile private var loadError: String? = null

    // ── Native engine lifecycle guard ──────────────────────────────────────────
    // MediaPipe's LlmInference must not be close()d while a generateResponseAsync call is
    // in-flight: tearing down the native engine under an active generation is a use-after-free
    // that Play Console reports as SIGSEGV/SIGABRT. Every generation path acquires a reference
    // here (bumping [activeGenerations]) and releases it when finished; unload()/resetModelState()
    // then defer the actual close() until the last in-flight generation completes.
    private var activeGenerations = 0   // guarded by synchronized(this)
    private var closeRequested = false  // guarded by synchronized(this)

    private val pendingCont = AtomicReference<kotlin.coroutines.Continuation<String?>?>(null)
    private val responseBuffer = StringBuilder()

    fun isLoaded(): Boolean = isModelLoaded
    fun getLoadError(): String? = loadError
    fun isModelValid(): Boolean = isModelLoaded && llmInference != null && loadError == null

    /**
     * Grabs the loaded engine for a single generation and registers it as in-flight. Returns null
     * when the model isn't ready, a close is pending, or another generation is already running
     * (the MediaPipe engine isn't reentrant, so generations are single-flight).
     */
    private fun acquireEngine(): LlmInference? = synchronized(this) {
        val engine = llmInference
        if (engine == null || !isModelLoaded || closeRequested || activeGenerations > 0) return null
        activeGenerations++
        engine
    }

    /** Marks a generation finished; runs a deferred close when this was the last in-flight one. */
    private fun releaseEngine() = synchronized(this) {
        if (activeGenerations > 0) activeGenerations--
        if (closeRequested && activeGenerations == 0) closeEngineLocked()
    }

    /** Closes the native engine and clears loaded state. Caller must hold `synchronized(this)`. */
    private fun closeEngineLocked() {
        val engine = llmInference
        llmInference = null
        isModelLoaded = false
        closeRequested = false
        if (engine == null) return
        try {
            engine.close()
            Log.d(TAG, "MediaPipe LLM unloaded successfully.")
        } catch (e: Error) {
            Log.e(TAG, "Native error closing LlmInference", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LlmInference", e)
        }
    }

    fun resetModelState() {
        synchronized(this) {
            loadError = "Model reset due to corruption"
            if (activeGenerations > 0) {
                // Defer the native close until in-flight generations finish.
                closeRequested = true
                isModelLoaded = false
            } else {
                closeEngineLocked()
            }
        }
    }

    fun loadModel(context: Context, modelPath: String) {
        if (isModelLoaded && llmInference != null) return
        synchronized(this) {
            if (isModelLoaded && llmInference != null) return
            // Don't (re)create the engine while a deferred close is pending for in-flight
            // generations — reloading here would orphan the old engine and race its close.
            if (closeRequested || activeGenerations > 0) return
            loadError = null
            try {
                val file = File(modelPath)
                when {
                    !file.exists() -> {
                        loadError = "Model file missing at $modelPath"
                        Log.w(TAG, loadError!!)
                    }
                    file.length() < MIN_MODEL_SIZE_BYTES -> {
                        loadError = "Model file too small: ${file.length() / 1024 / 1024}MB (Expected >400MB)"
                        Log.w(TAG, loadError!!)
                    }
                    modelPath.endsWith(".task") || modelPath.endsWith(".bin") -> {
                        Log.d(TAG, "Initializing MediaPipe LLM: $modelPath (${file.length() / 1024 / 1024}MB)")
                        val options = LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(modelPath)
                            .setMaxTokens(1024)         // 4B model handles 1024 tokens well; 256 was set for 1B OOM safety
                            .build()
                        llmInference = LlmInference.createFromOptions(context.applicationContext, options)
                            ?: throw Exception("LlmInference.createFromOptions returned null")
                        isModelLoaded = true
                        loadError = null
                        Log.d(TAG, "MediaPipe LLM loaded successfully!")
                    }
                    else -> {
                        loadError = "Model file must be a .task or .bin bundle (got: ${File(modelPath).name})"
                        Log.w(TAG, loadError!!)
                    }
                }
            } catch (e: Error) {
                loadError = "Native error during model load: ${e.message}"
                Log.e(TAG, "NATIVE ERROR during Gemma model load", e)
                isModelLoaded = false
                llmInference = null
            } catch (e: Exception) {
                val msg = e.message ?: ""
                // "Unable to open zip archive" means the .task file is corrupt/incomplete.
                // Delete it so the app falls back to NotDownloaded and re-triggers the download.
                val isCorruptArchive = msg.contains("zip archive", ignoreCase = true) ||
                        msg.contains("Unable to open", ignoreCase = true) ||
                        msg.contains("zip_utils", ignoreCase = true)
                if (isCorruptArchive) {
                    Log.w(TAG, "Corrupt model archive detected — deleting file: $modelPath")
                    try { File(modelPath).delete() } catch (_: Exception) {}
                    loadError = "MODEL_CORRUPT"   // sentinel: ChatViewModel maps this to NotDownloaded
                } else {
                    loadError = "Failed to load model: $msg"
                }
                Log.e(TAG, "CRITICAL: Failed to load MediaPipe model", e)
                isModelLoaded = false
                llmInference = null
            }
        }
    }

    fun unload() {
        synchronized(this) {
            loadError = null
            if (activeGenerations > 0) {
                // Defer the native close until the in-flight generation(s) finish — closing now
                // would be a use-after-free on the engine they're still using.
                closeRequested = true
                isModelLoaded = false
            } else {
                closeEngineLocked()
            }
        }
    }

    private suspend fun generateRawResponse(engine: LlmInference, prompt: String): String? {
        return try {
            engine.generateResponse(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "generateResponse failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun suggestVoucher(
        userInput: String,
        context: String? = null,
        sessionId: String? = null,
        replyInHindi: Boolean = false
    ): String? {
        val engine = acquireEngine() ?: return null
        val prompt = buildString {
            append("You are 'Helafin', a sentence translation and normalization assistant for the 'Teller' accounting app.\n")
            append("Your task is to rewrite the user's input (which can be in messy English, Hindi, or Hinglish, with words mixed up) into a standard, clean English accounting command.\n\n")
            append("Support only these standard output formats:\n")
            append("- 'Sold [Qty] [Item] to [Party] for [Amount]'\n")
            append("- 'Bought [Qty] [Item] from [Party] for [Amount]'\n")
            append("- 'Paid [Amount] to [Party]'\n")
            append("- 'Received [Amount] from [Party]'\n")
            append("- 'Show balance of [Party]'\n")
            append("- 'Show stock of [Item]'\n")
            append("- '[Bank/Financier] financed [Item] for [Customer] with loan amount [Amount] and down payment [Amount]'\n")
            append("- 'MESSAGE: [your response]' (Only for general greetings, help requests, or chat questions like 'hi', 'how to use', etc.)\n\n")
            if (replyInHindi) {
                append("IMPORTANT LANGUAGE RULE: For 'MESSAGE:' responses ONLY, write your reply in Hindi using Devanagari script. Keep party names, item names and amounts exactly as the user typed them.\n\n")
            }
            append("Rules:\n")
            append("1. Keep names and numbers exactly as provided. Convert Hindi number words to digits (e.g. 'hazaar' -> '1000', 'do' -> '2').\n")
            append("2. Output ONLY the rewritten standard sentence. Do not add explanations, quotes, markdown formatting, or backticks.\n\n")
            append("Examples:\n")
            append("Input: raman ko paanch hazaar diye cash\n")
            append("Output: Paid 5000 to Raman\n\n")
            append("Input: sahil se teen hazaar upi se mile\n")
            append("Output: Received 3000 from Sahil\n\n")
            append("Input: amit ne humse splendor kharidi 70000 ki\n")
            append("Output: Sold Splendor to Amit for 70000\n\n")
            append("Input: idfc bank financed splendor with loan amount 60000 and customer name amsndj\n")
            append("Output: IDFC Bank financed Splendor for Amsndj with loan amount 60000 and down payment 0\n\n")
            append("Input: hello how can you help me\n")
            if (replyInHindi) {
                append("Output: MESSAGE: नमस्ते! मैं हेलाफिन हूँ। मैं बिक्री, खरीद, भुगतान, प्राप्ति दर्ज करने और स्टॉक व बैलेंस देखने में आपकी मदद कर सकती हूँ।\n\n")
            } else {
                append("Output: MESSAGE: Hi! I am Helafin. I can help you record sales, purchases, payments, receipts, or check stock and balances.\n\n")
            }
            append("Input: $userInput\n")
            append("Output:")
        }

        return try {
            suspendCancellableCoroutine { cont ->
                val fullResponse = StringBuilder()
                var isFinished = false
                var future: com.google.common.util.concurrent.ListenableFuture<String>? = null

                val listener = com.google.mediapipe.tasks.genai.llminference.ProgressListener<String> { result, done ->
                    fullResponse.append(result)
                    val currentText = fullResponse.toString()
                    val hasNewline = currentText.contains("\n")

                    if (hasNewline || done) {
                        if (!isFinished) {
                            isFinished = true
                            if (hasNewline && !done) {
                                try {
                                    future?.cancel(true)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to cancel future: ${e.message}", e)
                                }
                            }
                            cont.resume(currentText)
                        }
                    }
                }

                try {
                    future = engine.generateResponseAsync(prompt, listener)
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "OOM during generateResponseAsync — unloading model")
                    unload()
                    System.gc()
                    if (!isFinished) {
                        isFinished = true
                        cont.resumeWithException(e)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "generateResponseAsync failed: ${e.message}", e)
                    if (!isFinished) {
                        isFinished = true
                        cont.resumeWithException(e)
                    }
                }

                cont.invokeOnCancellation {
                    if (!isFinished) {
                        isFinished = true
                        try {
                            future?.cancel(true)
                        } catch (e: Exception) {
                            Log.e(TAG, "Cancellation of future failed: ${e.message}", e)
                        }
                    }
                }
            }
        } finally {
            releaseEngine()
        }
    }

    /**
     * Answer a GST-law question on-device using the provided retrieved excerpts (RAG). Returns the
     * synthesized answer, or null if the model isn't loaded. Mirrors [suggestVoucher]'s async
     * generation but waits for the full response instead of stopping at the first newline.
     */
    suspend fun answerGstQuestion(question: String, context: String): String? {
        val engine = acquireEngine() ?: return null
        val prompt = buildString {
            append("You are 'Helafin', a GST (Goods & Services Tax) compliance assistant for the 'Teller' accounting app.\n")
            append("Answer the user's question using ONLY the GST law excerpts provided below. If the excerpts do not contain the answer, say you couldn't find it in the provided law. Do not invent law.\n")
            append("Keep the answer concise and mention the section number or source when you use it.\n\n")
            append("Relevant GST law excerpts:\n")
            append(context)
            append("\n\nQuestion: ")
            append(question)
            append("\n\nAnswer:")
        }

        return try {
            suspendCancellableCoroutine { cont ->
                val fullResponse = StringBuilder()
                var isFinished = false
                var future: com.google.common.util.concurrent.ListenableFuture<String>? = null

                val listener = com.google.mediapipe.tasks.genai.llminference.ProgressListener<String> { result, done ->
                    fullResponse.append(result)
                    if (done && !isFinished) {
                        isFinished = true
                        cont.resume(fullResponse.toString())
                    }
                }

                try {
                    future = engine.generateResponseAsync(prompt, listener)
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "OOM during GST answer — unloading model")
                    unload()
                    System.gc()
                    if (!isFinished) { isFinished = true; cont.resumeWithException(e) }
                } catch (e: Exception) {
                    Log.e(TAG, "generateResponseAsync failed: ${e.message}", e)
                    if (!isFinished) { isFinished = true; cont.resumeWithException(e) }
                }

                cont.invokeOnCancellation {
                    if (!isFinished) {
                        isFinished = true
                        try { future?.cancel(true) } catch (e: Exception) { Log.e(TAG, "Cancellation failed: ${e.message}", e) }
                    }
                }
            }
        } finally {
            releaseEngine()
        }
    }

    /**
     * Translates [text] to Hindi (Devanagari script) using the on-device model. Returns null if the
     * model isn't loaded or translation fails, so callers can gracefully fall back to English.
     */
    suspend fun translateToHindi(text: String): String? {
        val engine = acquireEngine() ?: return null
        val prompt = buildString {
            append("Translate the following English text into Hindi (Devanagari script). Keep numbers, currency symbols, party names and item names unchanged. Output only the Hindi translation, nothing else.\n\n")
            append("English: ").append(text).append("\n\n")
            append("Hindi:")
        }

        return try {
            suspendCancellableCoroutine { cont ->
                val fullResponse = StringBuilder()
                var isFinished = false
                var future: com.google.common.util.concurrent.ListenableFuture<String>? = null

                val listener = com.google.mediapipe.tasks.genai.llminference.ProgressListener<String> { result, done ->
                    fullResponse.append(result)
                    if (done && !isFinished) {
                        isFinished = true
                        cont.resume(fullResponse.toString().trim().takeIf { it.isNotBlank() })
                    }
                }

                try {
                    future = engine.generateResponseAsync(prompt, listener)
                } catch (e: OutOfMemoryError) {
                    unload()
                    System.gc()
                    if (!isFinished) { isFinished = true; cont.resumeWithException(e) }
                } catch (e: Exception) {
                    if (!isFinished) { isFinished = true; cont.resumeWithException(e) }
                }

                cont.invokeOnCancellation {
                    if (!isFinished) {
                        isFinished = true
                        try { future?.cancel(true) } catch (e: Exception) { Log.e(TAG, "Cancellation failed: ${e.message}", e) }
                    }
                }
            }
        } finally {
            releaseEngine()
        }
    }

    /**
     * Answers a knowledge question (GST law, income tax, app usage, accounting concepts)
     * using the Gemma 3 4B on-device model with a rich domain-specific system prompt.
     *
     * Uses [SmartFinancePrompts.buildKnowledgeSystemPrompt] as the system context, so the
     * 4B model has authoritative Indian tax law and Teller app guidance to draw from.
     *
     * Returns null if the model is not loaded — callers should fall back to [DeepSeekClient.smartChat].
     */
    suspend fun answerKnowledgeQuestion(question: String): String? {
        val engine = acquireEngine() ?: return null
        val systemPrompt = SmartFinancePrompts.buildKnowledgeSystemPrompt()
        val prompt = buildString {
            append("<start_of_turn>user\n")
            append(systemPrompt)
            append("\n\nUser question: ")
            append(question)
            append("\n<end_of_turn>\n<start_of_turn>model\n")
        }

        return try {
            suspendCancellableCoroutine { cont ->
                val fullResponse = StringBuilder()
                var isFinished = false
                var future: com.google.common.util.concurrent.ListenableFuture<String>? = null

                val listener = com.google.mediapipe.tasks.genai.llminference.ProgressListener<String> { result, done ->
                    fullResponse.append(result)
                    if (done && !isFinished) {
                        isFinished = true
                        val answer = fullResponse.toString().trim().takeIf { it.isNotBlank() }
                        cont.resume(answer)
                    }
                }

                try {
                    future = engine.generateResponseAsync(prompt, listener)
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "OOM during knowledge answer — unloading model")
                    unload()
                    System.gc()
                    if (!isFinished) { isFinished = true; cont.resumeWithException(e) }
                } catch (e: Exception) {
                    Log.e(TAG, "answerKnowledgeQuestion failed: ${e.message}", e)
                    if (!isFinished) { isFinished = true; cont.resumeWithException(e) }
                }

                cont.invokeOnCancellation {
                    if (!isFinished) {
                        isFinished = true
                        try { future?.cancel(true) } catch (e: Exception) { Log.e(TAG, "Cancellation failed: ${e.message}", e) }
                    }
                }
            }
        } finally {
            releaseEngine()
        }
    }
}
