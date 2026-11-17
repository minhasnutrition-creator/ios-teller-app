package com.minhas.teller.data.converters

import androidx.room.TypeConverter
import com.minhas.teller.data.entities.QuotationItem
import com.minhas.teller.utils.rupeesToPaise
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.Date

private val json = Json { ignoreUnknownKeys = true }

class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromQuotationItemList(items: List<QuotationItem>?): String? {
        if (items == null) return null
        val array = buildJsonArray {
            for (item in items) {
                add(buildJsonObject {
                    put("itemName", item.itemName)
                    put("description", item.description ?: "")
                    put("quantity", item.quantity)
                    put("unit", item.unit)
                    put("rate", item.rate)
                    put("amountPaise", item.amount)
                    put("hsnCode", item.hsnCode ?: "")
                    put("gstRate", item.gstRate)
                })
            }
        }
        return json.encodeToString(array)
    }

    @TypeConverter
    fun toQuotationItemList(jsonStr: String?): List<QuotationItem>? {
        if (jsonStr == null) return null
        return try {
            val array = json.parseToJsonElement(jsonStr) as JsonArray
            array.map { element ->
                val obj = element.jsonObject
                QuotationItem(
                    itemName = obj["itemName"]?.jsonPrimitive?.content ?: "",
                    description = obj["description"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
                    quantity = obj["quantity"]?.jsonPrimitive?.double ?: 1.0,
                    unit = obj["unit"]?.jsonPrimitive?.content ?: "Nos",
                    rate = obj["rate"]?.jsonPrimitive?.double ?: 0.0,
                    amount = if (obj.containsKey("amountPaise"))
                        obj["amountPaise"]?.jsonPrimitive?.long ?: 0L
                    else
                        (obj["amount"]?.jsonPrimitive?.double ?: 0.0).rupeesToPaise(),
                    hsnCode = obj["hsnCode"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
                    gstRate = obj["gstRate"]?.jsonPrimitive?.double ?: 0.0
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
