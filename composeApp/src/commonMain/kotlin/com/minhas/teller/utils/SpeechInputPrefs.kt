package com.minhas.teller.utils

import android.content.Context
import java.util.Locale

/**
 * Persists the language used for SPEECH INPUT (the mic / speech-to-text recognizer).
 *
 * This is deliberately decoupled from the TTS output voice in [TtsModelPrefs]: the voice
 * the assistant *speaks back with* and the language the *user speaks into the mic* are two
 * independent choices. Coupling them was the bug that made a Hindi/Hinglish speaker get
 * garbled transcriptions (the mic ran the generic English model and heard
 * "paanch kele beche amrit ko" as "panchkele DJ Amrit code").
 *
 * The finance assistant's parser works on Roman-script Hinglish ("beche", "kele", "ko"),
 * so the default is English (India) — an Indian-tuned recognizer that keeps Hinglish in
 * Roman script rather than transliterating it to Devanagari or mangling it as English.
 */
object SpeechInputPrefs {

    private const val PREFS_NAME = "speech_input_prefs"
    private const val LOCALE_TAG_KEY = "input_locale_tag"

    const val DEFAULT_LOCALE_TAG = "en-IN"

    fun getLocale(context: Context): Locale {
        val tag = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LOCALE_TAG_KEY, DEFAULT_LOCALE_TAG) ?: DEFAULT_LOCALE_TAG
        return Locale.forLanguageTag(tag)
    }

    fun setLocale(context: Context, locale: Locale) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(LOCALE_TAG_KEY, locale.toLanguageTag()).apply()
    }
}
