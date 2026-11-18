package java.util

open class Hashtable<K, V>(private val map: MutableMap<K, V> = mutableMapOf()) : MutableMap<K, V> by map

class EnumMap<K : Enum<K>, V>(keyType: Any? = null, private val map: MutableMap<K, V> = mutableMapOf()) : MutableMap<K, V> by map
