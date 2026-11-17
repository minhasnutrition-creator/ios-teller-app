package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hsn_directory")
data class HsnEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hsnCode: String,
    val description: String,
    val keywords: String,
    val gstRate: Double,
    val category: String
)
