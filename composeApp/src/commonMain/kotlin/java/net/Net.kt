package java.net

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class URL(val spec: String) {
    fun openConnection(): HttpURLConnection = HttpURLConnection(this)
    override fun toString(): String = spec
}

open class HttpURLConnection(val url: URL) {
    var requestMethod: String = "GET"
    var connectTimeout: Int = 10000
    var readTimeout: Int = 10000
    var doOutput: Boolean = false
    var doInput: Boolean = true
    var useCaches: Boolean = false
    var instanceFollowRedirects: Boolean = true

    private val headers = mutableMapOf<String, String>()
    private val outStream = ByteArrayOutputStream()

    fun setRequestProperty(key: String, value: String) {
        headers[key] = value
    }

    fun addRequestProperty(key: String, value: String) {
        headers[key] = value
    }

    val outputStream: OutputStream get() = outStream
    val inputStream: InputStream get() = ByteArrayInputStream(ByteArray(0))
    val errorStream: InputStream? get() = null
    val responseCode: Int get() = 200
    val responseMessage: String get() = "OK"

    fun connect() {}
    fun disconnect() {}
}

object URLEncoder {
    fun encode(s: String, enc: String = "UTF-8"): String {
        val allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~"
        val sb = StringBuilder()
        for (ch in s) {
            if (ch in allowed) {
                sb.append(ch)
            } else if (ch == ' ') {
                sb.append('+')
            } else {
                val bytes = ch.toString().encodeToByteArray()
                for (b in bytes) {
                    val hex = (b.toInt() and 0xFF).toString(16).uppercase()
                    sb.append('%').append(if (hex.length == 1) "0$hex" else hex)
                }
            }
        }
        return sb.toString()
    }
}
