package kotlin

import kotlin.reflect.KClass

// 1. String formatting
fun String.Companion.format(format: String, vararg args: Any?): String =
    com.minhas.teller.compat.simpleFormat(format, args)

fun String.Companion.format(locale: java.util.Locale, format: String, vararg args: Any?): String =
    com.minhas.teller.compat.simpleFormat(format, args)

fun String.format(vararg args: Any?): String =
    if (args.isNotEmpty() && args[0] is java.util.Locale) {
        val rest = args.drop(1).toTypedArray()
        com.minhas.teller.compat.simpleFormat(this, rest)
    } else {
        com.minhas.teller.compat.simpleFormat(this, args)
    }

fun String.format(locale: java.util.Locale, vararg args: Any?): String =
    com.minhas.teller.compat.simpleFormat(this, args)

fun String.lowercase(locale: Any?): String = this.lowercase()
fun String.uppercase(locale: Any?): String = this.uppercase()

fun Char.titlecase(locale: Any?): String = this.titlecase()
fun Char.uppercase(locale: Any?): String = this.uppercase()
fun Char.lowercase(locale: Any?): String = this.lowercase()

fun String(bytes: ByteArray, charset: Any? = null): String = bytes.decodeToString()
fun String(bytes: ByteArray, offset: Int, length: Int, charset: Any? = null): String =
    bytes.decodeToString(startIndex = offset.coerceIn(0, bytes.size), endIndex = (offset + length).coerceIn(0, bytes.size))

fun String.toByteArray(charset: Any? = null): ByteArray = this.encodeToByteArray()

// 2. Map getOrDefault
fun <K, V> Map<K, V>.getOrDefault(key: K, defaultValue: V): V =
    get(key) ?: defaultValue

// 3. Throwable / localizedMessage
val Throwable.localizedMessage: String?
    get() = message

// 4. Synchronized
inline fun <R> synchronized(lock: Any, block: () -> R): R = block()

// 5. Runnable
fun interface Runnable {
    fun run()
}

// 6. Reflection & ViewModel Factory Class<T> support
typealias Class<T> = Any

val <T : Any> KClass<T>.java: KClass<T>
    get() = this

val <T : Any> T.javaClass: KClass<out T>
    get() = this::class

fun KClass<*>.isAssignableFrom(other: Any?): Boolean = true

val KClass<*>.name: String
    get() = qualifiedName ?: simpleName ?: ""

// 7. Charsets & StringBuffer
val Charsets = java.nio.charset.StandardCharsets
typealias StringBuffer = StringBuilder

// 8. Integer compatibility object
object Integer {
    fun bitCount(i: Int): Int = i.countOneBits()
    fun parseInt(s: String): Int = s.toInt()
    fun parseInt(s: String, radix: Int): Int = s.toInt(radix)
    fun toString(i: Int): String = i.toString()
    fun toString(i: Int, radix: Int): String = i.toString(radix)
    const val MAX_VALUE = Int.MAX_VALUE
    const val MIN_VALUE = Int.MIN_VALUE
}

// 7. Compose & Graphics extensions
fun androidx.compose.ui.graphics.NativeCanvas.drawText(text: String, x: Float, y: Float, paint: Any? = null) {}

fun android.graphics.Bitmap.asImageBitmap(): androidx.compose.ui.graphics.ImageBitmap =
    androidx.compose.ui.graphics.ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))

// 8. JVM Annotations made available in root package
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Volatile

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class JvmField

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.SOURCE)
annotation class JvmStatic

@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class JvmOverloads
