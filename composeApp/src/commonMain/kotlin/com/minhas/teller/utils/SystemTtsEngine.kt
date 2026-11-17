package com.minhas.teller.utils

import android.content.Context
import java.util.Locale

/**
 * Natural on-device text-to-speech engine.
 * Stubbed for iOS - native AVSpeechSynthesizer can be connected here.
 */
object SystemTtsEngine {

    enum class Language(val code: String, val displayName: String) {
        ENGLISH("en", "English (Natural)"),
        HINDI("hi", "Hindi (Natural)"),
        PUNJABI("pa", "Punjabi (Natural)"),
        ITALIAN("it", "Italian (Natural)"),
        SPANISH("es", "Spanish (Natural)"),
        FRENCH("fr", "French (Natural)"),
        RUSSIAN("ru", "Russian (Natural)");

        val isIndic: Boolean get() = this == HINDI || this == PUNJABI

        companion object {
            fun fromCode(code: String?): Language =
                values().firstOrNull { it.code == code } ?: ENGLISH
        }
    }

    fun init(context: Context) {}
    fun isReady(): Boolean = false
    fun speak(text: String, lang: Language) {}
    fun setLanguage(lang: Language) {}
    fun stop() {}
    fun shutdown() {}
}
