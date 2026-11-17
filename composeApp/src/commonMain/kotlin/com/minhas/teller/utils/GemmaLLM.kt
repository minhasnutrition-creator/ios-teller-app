package com.minhas.teller.utils

import android.content.Context

/**
 * On-device LLM helper.
 * On iOS, cloud AI models (DeepSeek & Gemini Flash) are used for intelligence & voucher drafts.
 */
object GemmaLLM {
    fun isLoaded(): Boolean = false
    fun getLoadError(): String? = null
    fun isModelValid(): Boolean = false
    fun resetModelState() {}
    fun loadModel(context: Context, modelPath: String) {}
    fun unload() {}

    suspend fun suggestVoucher(
        prompt: String,
        ledgers: List<String>,
        stockItems: List<String>
    ): String? = null

    suspend fun answerGstQuestion(question: String, context: String): String? = null
    suspend fun translateToHindi(text: String): String? = null
    suspend fun answerKnowledgeQuestion(question: String): String? = null
}
