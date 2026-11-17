package com.minhas.teller.data.converters

import androidx.room.TypeConverter
import com.minhas.teller.data.entities.QuotationItem
import com.minhas.teller.utils.rupeesToPaise
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

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
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("itemName", item.itemName)
            obj.put("description", item.description ?: JSONObject.NULL)
            obj.put("quantity", item.quantity)
            obj.put("unit", item.unit)
            obj.put("rate", item.rate)
            obj.put("amountPaise", item.amount)
            obj.put("hsnCode", item.hsnCode ?: JSONObject.NULL)
            obj.put("gstRate", item.gstRate)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toQuotationItemList(json: String?): List<QuotationItem>? {
        if (json == null) return null
        return try {
            val array = JSONArray(json)
            val items = mutableListOf<QuotationItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                items.add(QuotationItem(
                    itemName = obj.optString("itemName", ""),
                    description = obj.optString("description").takeIf { it.isNotEmpty() },
                    quantity = obj.optDouble("quantity", 1.0),
                    unit = obj.optString("unit", "Nos"),
                    rate = obj.optDouble("rate", 0.0),
                    amount = if (obj.has("amountPaise")) obj.optLong("amountPaise", 0L)
                             else obj.optDouble("amount", 0.0).rupeesToPaise(),
                    hsnCode = obj.optString("hsnCode").takeIf { it.isNotEmpty() },
                    gstRate = obj.optDouble("gstRate", 0.0)
                ))
            }
            items
        } catch (e: Exception) {
            emptyList()
        }
    }
}