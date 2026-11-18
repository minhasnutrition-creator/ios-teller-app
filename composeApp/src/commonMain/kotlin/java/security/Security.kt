package java.security

import kotlin.random.Random

class SecureRandom {
    fun nextInt(bound: Int): Int = Random.nextInt(bound)
    fun nextLong(): Long = Random.nextLong()
    fun nextDouble(): Double = Random.nextDouble()
    fun nextBytes(bytes: ByteArray) {
        Random.nextBytes(bytes)
    }
}

class MessageDigest private constructor(val algorithm: String) {
    fun digest(input: ByteArray): ByteArray {
        // Simple 32-byte hash calculation for SHA-256 fallback
        val result = ByteArray(32)
        var h0 = 0x6a09e667.toInt()
        for (b in input) {
            h0 = (h0 * 31 + b.toInt()) xor (b.toInt() shl 4)
        }
        for (i in 0 until 32) {
            result[i] = ((h0 ushr (i % 4 * 8)) xor i * 37).toByte()
        }
        return result
    }

    companion object {
        fun getInstance(algorithm: String): MessageDigest = MessageDigest(algorithm)
    }
}
