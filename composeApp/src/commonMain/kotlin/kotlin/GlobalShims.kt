package kotlin

import kotlin.reflect.KClass

// 1. String formatting
fun String.Companion.format(format: String, vararg args: Any?): String =
    com.minhas.teller.compat.simpleFormat(format, args)

fun String.Companion.format(locale: Any?, format: String, vararg args: Any?): String =
    com.minhas.teller.compat.simpleFormat(format, args)

fun String.format(vararg args: Any?): String =
    com.minhas.teller.compat.simpleFormat(this, args)

fun String.format(locale: Any?, vararg args: Any?): String =
    com.minhas.teller.compat.simpleFormat(this, args)

fun String.lowercase(locale: Any?): String = this.lowercase()
fun String.uppercase(locale: Any?): String = this.uppercase()

fun String.toByteArray(charset: Any? = null): ByteArray = this.encodeToByteArray()

// 2. Map getOrDefault
fun <K, V> Map<K, V>.getOrDefault(key: K, defaultValue: V): V =
    get(key) ?: defaultValue

// 3. Throwable / localizedMessage
val Throwable.localizedMessage: String?
    get() = message

// 4. Synchronized
inline fun <R> synchronized(lock: Any, block: () -> R): R = block()

// 5. Reflection & ViewModel Factory Class<T> support
typealias Class<T> = KClass<T>

val <T : Any> KClass<T>.java: KClass<T>
    get() = this

fun KClass<*>.isAssignableFrom(other: Any?): Boolean = true

val KClass<*>.name: String
    get() = qualifiedName ?: simpleName ?: ""

// 6. Volatile annotation
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Volatile
