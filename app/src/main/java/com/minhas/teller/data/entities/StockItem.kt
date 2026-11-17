package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_items",
    indices = [
        Index(value = ["companyId"]),
        Index(value = ["unitId"]),
        Index(value = ["companyId", "barcode"])
    ]
)
data class StockItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val alias: String? = null,
    val unitId: Int, // Foreign key to Unit
    val openingStock: Double = 0.0,
    val rate: Double = 0.0,
    val companyId: Int = 0,
    // Scanned from a product's barcode (EAN/UPC/Code128/...) to find this item instantly at billing
    // time. Not every item has one, and several items can't share the same code.
    val barcode: String? = null,
    // HSN & Tax fields - auto-populated by Smart Tax system
    val hsnCode: String? = null,
    val gstRate: Double = 0.0, // Total GST % (CGST + SGST)
    val cgstRate: Double = 0.0,
    val sgstRate: Double = 0.0,
    val igstRate: Double = 0.0,
    val hsnAutoDetected: Boolean = false, // Track if HSN was auto-detected
    val uid: String? = null,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)