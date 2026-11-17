package android.util

import kotlin.io.encoding.Base64 as KBase64
import kotlin.io.encoding.ExperimentalEncodingApi

object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8

    @OptIn(ExperimentalEncodingApi::class)
    fun encodeToString(input: ByteArray, flags: Int): String {
        return KBase64.encode(input)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decode(str: String, flags: Int): ByteArray {
        return try {
            KBase64.decode(str.trim())
        } catch (_: Exception) {
            ByteArray(0)
        }
    }
}
