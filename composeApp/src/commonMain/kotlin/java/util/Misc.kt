package java.util

open class Hashtable<K, V> : LinkedHashMap<K, V>()

class EnumMap<K : Enum<K>, V>(keyType: Any?) : LinkedHashMap<K, V>()
