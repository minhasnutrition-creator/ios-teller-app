package com.minhas.teller.utils

import android.content.Context

/**
 * Persists the user's chosen TTS voice language so the choice survives app restarts.
 *
 * Speech uses the Android system TextToSpeech engine (SystemTtsEngine). The former
 * Piper/sherpa-onnx on-device pipeline was removed as dead code.
 */
object TtsModelPrefs {

    private const val PREFS_NAME = "tts_prefs"
    private const val VOICE_LANG_KEY = "voice_language"

    fun getVoiceLanguage(context: Context): SystemTtsEngine.Language {
        val code = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(VOICE_LANG_KEY, null)
        return SystemTtsEngine.Language.fromCode(code)
    }

    fun setVoiceLanguage(context: Context, lang: SystemTtsEngine.Language) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(VOICE_LANG_KEY, lang.code).apply()
    }
}
