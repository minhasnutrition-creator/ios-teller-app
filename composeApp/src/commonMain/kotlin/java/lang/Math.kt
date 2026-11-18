package java.lang

import kotlin.math.pow

typealias StringBuilder = kotlin.text.StringBuilder

object Math {
    fun abs(a: Int): Int = kotlin.math.abs(a)
    fun abs(a: Long): Long = kotlin.math.abs(a)
    fun abs(a: Float): Float = kotlin.math.abs(a)
    fun abs(a: Double): Double = kotlin.math.abs(a)
    fun max(a: Int, b: Int): Int = kotlin.math.max(a, b)
    fun max(a: Long, b: Long): Long = kotlin.math.max(a, b)
    fun max(a: Float, b: Float): Float = kotlin.math.max(a, b)
    fun max(a: Double, b: Double): Double = kotlin.math.max(a, b)
    fun min(a: Int, b: Int): Int = kotlin.math.min(a, b)
    fun min(a: Long, b: Long): Long = kotlin.math.min(a, b)
    fun min(a: Float, b: Float): Float = kotlin.math.min(a, b)
    fun min(a: Double, b: Double): Double = kotlin.math.min(a, b)
    fun round(a: Float): Int = kotlin.math.round(a).toInt()
    fun round(a: Double): Long = kotlin.math.round(a).toLong()
    fun floor(a: Double): Double = kotlin.math.floor(a)
    fun ceil(a: Double): Double = kotlin.math.ceil(a)
    fun sqrt(a: Double): Double = kotlin.math.sqrt(a)
    fun pow(a: Double, b: Double): Double = a.pow(b)
    fun random(): Double = kotlin.random.Random.nextDouble()
    fun toRadians(angdeg: Double): Double = angdeg / 180.0 * kotlin.math.PI
    fun toDegrees(angrad: Double): Double = angrad * 180.0 / kotlin.math.PI
    const val PI: Double = kotlin.math.PI
    const val E: Double = kotlin.math.E
}

object Integer {
    fun bitCount(i: Int): Int = i.countOneBits()
    fun toBinaryString(i: Int): String = i.toString(2)
    fun toHexString(i: Int): String = i.toString(16)
    fun parseInt(s: String): Int = s.toInt()
    fun parseInt(s: String, radix: Int): Int = s.toInt(radix)
    const val MAX_VALUE: Int = Int.MAX_VALUE
    const val MIN_VALUE: Int = Int.MIN_VALUE
}
