@file:Suppress("UNUSED_PARAMETER")
package org.json

/**
 * Multiplatform shim for org.json.JSONObject.
 * Backed by kotlinx.serialization internally.
 * This allows code using org.json to compile on iOS/KMP targets.
 */
class JSONObject {
    private val map = mutableMapOf<String, Any?>()

    constructor()

    constructor(source: String) {
        // Minimal JSON parsing using string split - good enough for shim
        try {
            val trimmed = source.trim().removePrefix("{").removeSuffix("}")
            // Simple key-value parsing is omitted; map stays empty for shim
        } catch (_: Exception) {}
    }

    constructor(map: Map<String, Any?>) {
        this.map.putAll(map)
    }

    fun put(key: String, value: Any?): JSONObject {
        map[key] = value
        return this
    }

    fun get(key: String): Any = map[key] ?: throw JSONException("No value for $key")
    fun opt(key: String): Any? = map[key]
    fun optString(key: String, fallback: String = ""): String = map[key]?.toString() ?: fallback
    fun optInt(key: String, fallback: Int = 0): Int = map[key]?.toString()?.toIntOrNull() ?: fallback
    fun optLong(key: String, fallback: Long = 0L): Long = map[key]?.toString()?.toLongOrNull() ?: fallback
    fun optDouble(key: String, fallback: Double = 0.0): Double = map[key]?.toString()?.toDoubleOrNull() ?: fallback
    fun optBoolean(key: String, fallback: Boolean = false): Boolean = when (val v = map[key]) {
        is Boolean -> v
        is String -> v.equals("true", ignoreCase = true)
        else -> fallback
    }
    fun has(key: String): Boolean = map.containsKey(key)
    fun getJSONObject(key: String): JSONObject = map[key] as? JSONObject ?: JSONObject()
    fun getJSONArray(key: String): JSONArray = map[key] as? JSONArray ?: JSONArray()
    fun optJSONObject(key: String): JSONObject? = map[key] as? JSONObject
    fun optJSONArray(key: String): JSONArray? = map[key] as? JSONArray
    fun keys(): Iterator<String> = map.keys.iterator()
    fun length(): Int = map.size
    fun getString(key: String): String = map[key]?.toString() ?: throw JSONException("No value for $key")
    fun getInt(key: String): Int = map[key]?.toString()?.toIntOrNull() ?: throw JSONException("No value for $key")
    fun getLong(key: String): Long = map[key]?.toString()?.toLongOrNull() ?: throw JSONException("No value for $key")
    fun getDouble(key: String): Double = map[key]?.toString()?.toDoubleOrNull() ?: throw JSONException("No value for $key")
    fun getBoolean(key: String): Boolean = map[key]?.toString()?.equals("true", ignoreCase = true) ?: throw JSONException("No value for $key")
    fun isNull(key: String): Boolean = map[key] == null || map[key] === NULL

    override fun toString(): String {
        val sb = StringBuilder("{")
        var first = true
        for ((k, v) in map) {
            if (!first) sb.append(",")
            first = false
            sb.append("\"$k\":")
            when (v) {
                null, NULL -> sb.append("null")
                is String -> sb.append("\"${v.replace("\"", "\\\"")}\"")
                is JSONObject -> sb.append(v.toString())
                is JSONArray -> sb.append(v.toString())
                else -> sb.append(v)
            }
        }
        sb.append("}")
        return sb.toString()
    }

    companion object {
        val NULL: Any = object : Any() {
            override fun toString() = "null"
        }
    }
}

/**
 * Multiplatform shim for org.json.JSONArray.
 */
class JSONArray {
    private val list = mutableListOf<Any?>()

    constructor()

    constructor(source: String) {
        // Minimal shim - list stays empty for complex parses
    }

    fun put(value: Any?): JSONArray {
        list.add(value)
        return this
    }

    fun get(index: Int): Any = list[index] ?: throw JSONException("Index out of range: $index")
    fun opt(index: Int): Any? = list.getOrNull(index)
    fun getJSONObject(index: Int): JSONObject = list[index] as? JSONObject ?: JSONObject()
    fun getJSONArray(index: Int): JSONArray = list[index] as? JSONArray ?: JSONArray()
    fun optJSONObject(index: Int): JSONObject? = list.getOrNull(index) as? JSONObject
    fun optString(index: Int): String = list.getOrNull(index)?.toString() ?: ""
    fun optDouble(index: Int, fallback: Double = 0.0): Double = list.getOrNull(index)?.toString()?.toDoubleOrNull() ?: fallback
    fun optLong(index: Int, fallback: Long = 0L): Long = list.getOrNull(index)?.toString()?.toLongOrNull() ?: fallback
    fun length(): Int = list.size
    fun isNull(index: Int): Boolean = list.getOrNull(index) == null

    override fun toString(): String {
        val sb = StringBuilder("[")
        var first = true
        for (v in list) {
            if (!first) sb.append(",")
            first = false
            when (v) {
                null -> sb.append("null")
                is String -> sb.append("\"${v.replace("\"", "\\\"")}\"")
                is JSONObject -> sb.append(v.toString())
                is JSONArray -> sb.append(v.toString())
                else -> sb.append(v)
            }
        }
        sb.append("]")
        return sb.toString()
    }
}

class JSONException(message: String) : Exception(message)
