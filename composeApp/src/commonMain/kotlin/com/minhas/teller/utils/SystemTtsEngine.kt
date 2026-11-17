package com.minhas.teller.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Natural on-device text-to-speech backed by Android's system engine (Google TTS on most devices).
 *
 * Supports English, Hindi, Punjabi, Italian, Spanish, and French with robust multi-locale fallbacks
 * and voice selection matching.
 */
object SystemTtsEngine {

    enum class Language(val code: String, val locale: Locale, val displayName: String) {
        ENGLISH("en", Locale.ENGLISH, "English (Natural)"),
        HINDI("hi", Locale.forLanguageTag("hi-IN"), "Hindi (Natural)"),
        PUNJABI("pa", Locale.forLanguageTag("pa-IN"), "Punjabi (Natural)"),
        ITALIAN("it", Locale.ITALIAN, "Italian (Natural)"),
        SPANISH("es", Locale.forLanguageTag("es-ES"), "Spanish (Natural)"),
        FRENCH("fr", Locale.FRENCH, "French (Natural)"),
        RUSSIAN("ru", Locale.forLanguageTag("ru-RU"), "Russian (Natural)");

        val isIndic: Boolean get() = this == HINDI || this == PUNJABI

        companion object {
            fun fromCode(code: String?): Language =
                values().firstOrNull { it.code == code } ?: ENGLISH
        }
    }

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var pendingUtterance: Pair<String, Language>? = null

    fun init(context: Context) {
        if (tts != null) return
        val engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                val pending = pendingUtterance
                if (pending != null) {
                    pendingUtterance = null
                    speak(pending.first, pending.second)
                }
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
        tts = engine
    }

    fun isReady(): Boolean = ready && tts != null

    /**
     * Speaks [text] in [lang] using the system TTS engine with locale & voice fallback.
     */
    fun speak(text: String, lang: Language) {
        val cleaned = cleanForSpeech(text)
        if (cleaned.isBlank()) return

        val engine = tts
        if (engine == null || !ready) {
            pendingUtterance = cleaned to lang
            return
        }

        try {
            applyLanguage(engine, lang)
            engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "helafin_tts")
        } catch (_: Throwable) {
            // A TTS failure must never crash the app.
        }
    }

    fun setLanguage(lang: Language) {
        val engine = tts ?: return
        if (ready) {
            try {
                applyLanguage(engine, lang)
            } catch (_: Throwable) {}
        }
    }

    private fun applyLanguage(engine: TextToSpeech, lang: Language) {
        val candidates: List<Locale> = when (lang) {
            Language.ENGLISH -> listOf(Locale.ENGLISH, Locale.US, Locale.UK, Locale.forLanguageTag("en-IN"))
            Language.HINDI -> listOf(Locale.forLanguageTag("hi-IN"), Locale.forLanguageTag("hi"))
            Language.PUNJABI -> listOf(
                Locale.forLanguageTag("pa-IN"),
                Locale.forLanguageTag("pa"),
                Locale.forLanguageTag("pa-Guru-IN"),
                Locale.forLanguageTag("pa-Guru")
            )
            Language.ITALIAN -> listOf(Locale.ITALIAN, Locale.ITALY, Locale.forLanguageTag("it-IT"), Locale.forLanguageTag("it"))
            Language.SPANISH -> listOf(Locale.forLanguageTag("es-ES"), Locale.forLanguageTag("es"), Locale.forLanguageTag("es-US"))
            Language.FRENCH -> listOf(Locale.FRENCH, Locale.FRANCE, Locale.forLanguageTag("fr-FR"), Locale.forLanguageTag("fr"))
            Language.RUSSIAN -> listOf(Locale.forLanguageTag("ru-RU"), Locale.forLanguageTag("ru"))
        }

        var matched = false
        for (candidate in candidates) {
            val res = engine.setLanguage(candidate)
            if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                matched = true
                break
            }
        }

        // Direct voice inspection fallback (for voices registered under non-standard locale tags)
        if (!matched) {
            try {
                val matchingVoice = engine.voices?.firstOrNull { voice ->
                    val vLang = voice.locale?.language?.lowercase() ?: ""
                    val vTag = voice.locale?.toLanguageTag()?.lowercase() ?: ""
                    val vName = voice.name.lowercase()
                    when (lang) {
                        Language.PUNJABI -> vLang == "pa" || vLang == "pan" || vTag.startsWith("pa") || vName.contains("punjabi") || vName.contains("guru")
                        Language.HINDI -> vLang == "hi" || vLang == "hin" || vTag.startsWith("hi") || vName.contains("hindi") || vName.contains("deva")
                        Language.ITALIAN -> vLang == "it" || vLang == "ita" || vTag.startsWith("it") || vName.contains("italian")
                        Language.SPANISH -> vLang == "es" || vLang == "spa" || vTag.startsWith("es") || vName.contains("spanish")
                        Language.FRENCH -> vLang == "fr" || vLang == "fra" || vLang == "fre" || vTag.startsWith("fr") || vName.contains("french")
                        Language.RUSSIAN -> vLang == "ru" || vLang == "rus" || vTag.startsWith("ru") || vName.contains("russian")
                        Language.ENGLISH -> vLang == "en" || vLang == "eng" || vTag.startsWith("en")
                    }
                }
                if (matchingVoice != null) {
                    engine.voice = matchingVoice
                }
            } catch (_: Throwable) {}
        }
    }

    fun stop() {
        pendingUtterance = null
        try { tts?.stop() } catch (_: Throwable) {}
    }

    fun shutdown() {
        try { tts?.shutdown() } catch (_: Throwable) {}
        tts = null
        ready = false
        pendingUtterance = null
    }

    private fun cleanForSpeech(text: String): String {
        var clean = text
        clean = clean.replace(Regex("[*_~`#]"), "")
        clean = clean.replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
        clean = clean.replace(Regex("(?m)^[\\s*+-]+\\s*"), "")
        clean = clean.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "")
        clean = clean.replace(Regex("\\s+"), " ").trim()
        return clean
    }
}
