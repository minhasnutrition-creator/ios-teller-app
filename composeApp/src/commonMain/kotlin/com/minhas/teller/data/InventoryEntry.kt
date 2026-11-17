package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_entries",
    indices = [
        Index(value = ["voucherId"]),
        Index(value = ["stockItemId"])
    ]
)
data class InventoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val voucherId: Int, // Foreign key to Voucher
    val stockItemId: Int, // Foreign key to StockItem
    val quantity: Double,
    val rate: Double,
    val amount: Long, // paise; quantity * rate
    // HSN & Tax fields - copied from StockItem at time of sale
    val hsnCode: String? = null,
    val gstRate: Double = 0.0,
    val cgstRate: Double = 0.0,
    val sgstRate: Double = 0.0,
    val igstRate: Double = 0.0,
    val taxableAmount: Long = 0L, // paise; amount before tax
    val taxAmount: Long = 0L, // paise; total tax
    val totalAmount: Long = 0L // paise; amount + tax
)
