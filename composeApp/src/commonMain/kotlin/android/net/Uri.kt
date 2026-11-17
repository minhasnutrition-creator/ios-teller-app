package android.net

data class Uri(val uriString: String = "") {
    companion object {
        fun parse(uriString: String?): Uri = Uri(uriString ?: "")
        val EMPTY = Uri("")
    }

    override fun toString(): String = uriString
}
