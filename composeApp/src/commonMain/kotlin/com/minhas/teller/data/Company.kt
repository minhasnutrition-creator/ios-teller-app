package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "companies")
data class Company(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val mailingName: String?,
    val address: String?,
    val state: String?,
    val country: String?,
    val pincode: String?,
    val gstin: String?,
    val phoneNumber: String?,
    val email: String?,
    val backupEnabled: Boolean = false,
    val currencySymbol: String = "₹",
    val financialYearFrom: Long,
    val stampType: Int = 0,
    val stampColor: Int = 0,
    val logoUri: String? = null,
    val signatureUri: String? = null,
    val stampDesignName: String? = null,
    val upiId: String? = null,
    val uid: String? = null,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)

