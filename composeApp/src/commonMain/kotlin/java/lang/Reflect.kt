package java.lang

open class Class<T> {
    fun isAssignableFrom(other: Any?): Boolean = true
    val name: String get() = ""
    val simpleName: String get() = ""
}

val <T : Any> kotlin.reflect.KClass<T>.java: Class<T>
    get() = Class()

fun <T : Any> kotlin.reflect.KClass<T>.isAssignableFrom(other: Any?): Boolean = true

val Throwable.localizedMessage: String?
    get() = message
