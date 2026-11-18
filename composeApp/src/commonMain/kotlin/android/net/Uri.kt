package android.net

data class Uri(val uriString: String = "") {
    val path: String? get() = uriString
    val scheme: String? get() = uriString.substringBefore(":", "")
    val lastPathSegment: String? get() = uriString.substringAfterLast("/")

    companion object {
        fun parse(uriString: String?): Uri = Uri(uriString ?: "")
        fun fromFile(file: Any?): Uri = Uri(file.toString())
        val EMPTY = Uri("")

        fun encode(s: String?, allow: String? = null): String =
            if (s == null) "" else java.net.URLEncoder.encode(s)

        fun decode(s: String?): String {
            if (s == null) return ""
            return s.replace("+", " ")
                .replace("%20", " ")
                .replace("%0A", "\n")
                .replace("%26", "&")
                .replace("%3D", "=")
                .replace("%3F", "?")
                .replace("%23", "#")
                .replace("%2B", "+")
                .replace("%22", "\"")
        }
    }

    override fun toString(): String = uriString
}
