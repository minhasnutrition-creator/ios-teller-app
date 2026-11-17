package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ledgers",
    indices = [
        Index(value = ["companyId"]),
        Index(value = ["companyId", "group"])
    ]
)
data class Ledger(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val alias: String? = null,
    val group: String,
    val openingBalance: Long, // paise
    val maintainBillWise: Boolean = false,
    val phoneNumber: String? = null,
    val companyId: Int = 0,
    val gstin: String? = null,
    val stateCode: String? = null,
    val uid: String? = null,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)