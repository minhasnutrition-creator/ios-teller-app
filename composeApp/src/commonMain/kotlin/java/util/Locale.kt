package java.util

class Locale(val language: String, val country: String = "", val variant: String = "") {
    val displayName: String get() = if (country.isEmpty()) language else "$language-$country"
    val displayCountry: String get() = country.ifEmpty { displayName }

    fun toLanguageTag(): String = if (country.isEmpty()) language else "$language-$country"

    override fun toString(): String = if (country.isEmpty()) language else "${language}_$country"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Locale) return false
        return language == other.language && country == other.country
    }

    override fun hashCode(): Int = language.hashCode() * 31 + country.hashCode()

    class Builder {
        private var lang = "en"
        private var region = ""
        fun setLanguage(language: String): Builder { lang = language; return this }
        fun setRegion(region: String): Builder { this.region = region; return this }
        fun build(): Locale = Locale(lang, region)
    }

    companion object {
        val ROOT = Locale("", "")
        val US = Locale("en", "US")
        val ENGLISH = Locale("en", "")
        val UK = Locale("en", "GB")
        val GERMANY = Locale("de", "DE")
        val FRANCE = Locale("fr", "FR")
        val JAPAN = Locale("ja", "JP")
        val CHINA = Locale("zh", "CN")

        private var defaultLocale = US

        fun getDefault(): Locale = defaultLocale
        fun setDefault(locale: Locale) {
            defaultLocale = locale
        }

        fun forLanguageTag(tag: String): Locale {
            val parts = tag.split("-", "_")
            return Locale(parts.getOrElse(0) { "en" }, parts.getOrElse(1) { "" })
        }

        fun getISOCountries(): Array<String> = arrayOf(
            "US", "IN", "GB", "CA", "AU", "DE", "FR", "JP", "CN", "BR", "ZA", "AE", "SA", "SG"
        )
    }
}
