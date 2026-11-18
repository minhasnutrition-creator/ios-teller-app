package java.util

class Locale(val language: String, val country: String = "", val variant: String = "") {
    val displayName: String get() = "$language-$country"

    override fun toString(): String = if (country.isEmpty()) language else "${language}_$country"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Locale) return false
        return language == other.language && country == other.country
    }

    override fun hashCode(): Int = language.hashCode() * 31 + country.hashCode()

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
    }
}
