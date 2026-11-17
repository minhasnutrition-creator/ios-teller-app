package com.minhas.teller.utils

import android.content.Context

object AiModelPrefs {
    // ── Gemma 3 1B — Android-compatible .task zip bundle hosted on Hugging Face ──
    const val DEFAULT_MODEL_URL = "https://huggingface.co/Minhasnutrition/tellerai/resolve/main/gemma3-1b-it-int4.task"
    const val DEFAULT_FILENAME = "gemma3-1b-it-int4.task"
    const val DEFAULT_MIN_SIZE = 300000000L          // 300 MB minimum for .task files
    const val DEFAULT_EXPECTED_SIZE = 554661243L    // 554 MB expected for gemma3-1b-it-int4.task
    const val SMART_MODEL_URL = DEFAULT_MODEL_URL
    const val SMART_FILENAME = DEFAULT_FILENAME
    const val SMART_MIN_SIZE = DEFAULT_MIN_SIZE
    const val SMART_EXPECTED_SIZE = DEFAULT_EXPECTED_SIZE

    // ── SHA-256: set this to verify downloaded model integrity ──
    // If null, skips verification. Set to the actual hash of a known-good model file.
    // You can compute it from a verified download via:
    //   certutil -hashfile gemma3-1b-it-int4.task SHA256
    // Or keep null until you have a confirmed good download.
    private val DEFAULT_SHA256: String? = null

    private const val PREFS_NAME = "ai_model_prefs"
    private const val KEY_MODEL_URL = "model_url"
    private const val KEY_MODEL_FILENAME = "model_filename"
    private const val KEY_MIN_SIZE = "min_size"
    private const val KEY_IS_SMART_MODEL = "is_smart_model"
    private const val KEY_EXPECTED_SHA256 = "expected_sha256"

    fun setModelSelection(context: Context, isSmart: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
        prefs.putBoolean(KEY_IS_SMART_MODEL, isSmart)
        prefs.putString(KEY_MODEL_URL, DEFAULT_MODEL_URL)
        prefs.putString(KEY_MODEL_FILENAME, DEFAULT_FILENAME)
        prefs.putLong(KEY_MIN_SIZE, DEFAULT_MIN_SIZE)
        prefs.apply()
    }

    fun isSmartModelSelected(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, 0).getBoolean(KEY_IS_SMART_MODEL, false)

    fun getModelUrl(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, 0)
            .getString(KEY_MODEL_URL, DEFAULT_MODEL_URL) ?: DEFAULT_MODEL_URL

    fun getModelFilename(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, 0)
            .getString(KEY_MODEL_FILENAME, DEFAULT_FILENAME) ?: DEFAULT_FILENAME

    fun getTmpFilename(context: Context): String = getModelFilename(context) + ".tmp"

    fun getModelFile(context: Context): java.io.File {
        val modelDir = context.getExternalFilesDir(null) ?: context.filesDir
        return java.io.File(modelDir, getModelFilename(context))
    }

    fun getMinSize(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, 0).getLong(KEY_MIN_SIZE, DEFAULT_MIN_SIZE)

    fun getExpectedSize(context: Context): Long = DEFAULT_EXPECTED_SIZE

    /**
     * Returns the expected SHA-256 hex string for the model file, or null if not set.
     * When non-null, the download worker will verify the file and re-download on mismatch.
     */
    fun getExpectedSha256(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, 0)
            .getString(KEY_EXPECTED_SHA256, DEFAULT_SHA256) ?: DEFAULT_SHA256
    }

    /**
     * Set the known-good SHA-256 hash after manually verifying a download.
     */
    fun setExpectedSha256(context: Context, sha256: String) {
        context.getSharedPreferences(PREFS_NAME, 0).edit()
            .putString(KEY_EXPECTED_SHA256, sha256)
            .apply()
    }
}
