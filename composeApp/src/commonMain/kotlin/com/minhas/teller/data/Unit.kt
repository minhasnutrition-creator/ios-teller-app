package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "units")
data class Unit(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String, // e.g., "Pcs", "Kgs", "Mtrs"
    val formalName: String? = null,
    val companyId: Int = 1,
    val uid: String? = null,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)