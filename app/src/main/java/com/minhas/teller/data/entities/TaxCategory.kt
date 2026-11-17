package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tax_categories")
data class TaxCategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val taxRate: Double,
    val companyId: Int = 1
)
