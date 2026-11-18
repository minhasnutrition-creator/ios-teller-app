package java.nio.charset

class Charset(val name: String) {
    companion object {
        fun forName(name: String): Charset = Charset(name)
        val defaultCharset: Charset = Charset("UTF-8")
    }
    override fun toString(): String = name
}

object StandardCharsets {
    val UTF_8 = Charset("UTF-8")
    val UTF_16 = Charset("UTF-16")
    val UTF_16LE = Charset("UTF-16LE")
    val UTF_16BE = Charset("UTF-16BE")
    val US_ASCII = Charset("US-ASCII")
    val ISO_8859_1 = Charset("ISO-8859-1")
}

object Charsets {
    val UTF_8 = kotlin.text.Charsets.UTF_8
    val UTF_16 = kotlin.text.Charsets.UTF_16
    val UTF_16LE = kotlin.text.Charsets.UTF_16LE
    val UTF_16BE = kotlin.text.Charsets.UTF_16BE
    val US_ASCII = kotlin.text.Charsets.US_ASCII
    val ISO_8859_1 = kotlin.text.Charsets.ISO_8859_1
}
