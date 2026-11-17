package com.minhas.teller.data.entities

/**
 * Individual item in a quotation
 */
data class QuotationItem(
    val itemName: String,
    val description: String? = null,
    val quantity: Double = 1.0,
    val unit: String = "Nos",
    val rate: Double,
    val amount: Long, // paise
    val hsnCode: String? = null,
    val gstRate: Double = 0.0
)
