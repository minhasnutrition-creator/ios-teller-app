package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Quotation entity - Stores customer quotations/proposals
 */
@Entity(tableName = "quotations")
data class Quotation(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val quotationNumber: String, // Auto-generated like QT-001
    val customerName: String,
    val customerPhone: String? = null,
    val customerAddress: String? = null,
    val quotationDate: Date = Date(),
    val expiryDate: Date? = null, // Valid until date
    val items: List<QuotationItem>, // Stored as JSON via TypeConverter
    val subTotal: Long, // paise
    val taxAmount: Long = 0L, // paise
    val discount: Long = 0L, // paise
    val grandTotal: Long, // paise
    val notes: String? = null,
    val terms: String? = null,
    val companyId: Int = 1,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

