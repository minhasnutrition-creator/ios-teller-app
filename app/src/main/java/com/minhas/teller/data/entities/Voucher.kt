package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "vouchers",
    indices = [
        Index(value = ["companyId"]),
        Index(value = ["companyId", "date"]),
        Index(value = ["type"])
    ]
)
data class Voucher(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String, // e.g., "Payment", "Receipt", "Journal", "Sales", "Purchase"
    val date: Date,
    val voucherNumber: String,
    val narration: String? = null,
    val companyId: Int = 0,
    val customField: String? = null, // For extra fields like IMEI, Vehicle No, etc.
    val chargeInterest: Boolean = false,
    val interestRate: Double = 0.0,
    val interestDays: Int = 0,
    val refDisplayName: String? = null,
    val isFinanced: Boolean = false,
    val placeOfSupply: String? = null,
    val supplyType: String? = null,
    val reverseCharge: Boolean = false,
    val uid: String? = null,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
    // For a "Sales Return" / "Purchase Return": the id of the original bill being returned.
    // Lets the Returns screen stop the same items being returned twice. Null for every other voucher.
    val returnOfVoucherId: Int? = null
)