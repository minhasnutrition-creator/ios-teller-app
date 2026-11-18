package android.net

data class Uri(val uriString: String = "") {
    companion object {
        fun parse(uriString: String?): Uri = Uri(uriString ?: "")
        fun fromFile(file: Any?): Uri = Uri(file.toString())
        val EMPTY = Uri("")
    }

    override fun toString(): String = uriString
}
