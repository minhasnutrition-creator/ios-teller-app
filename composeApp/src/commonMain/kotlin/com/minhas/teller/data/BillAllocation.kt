package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bill_allocations",
    indices = [
        Index(value = ["voucherEntryId"]),
        Index(value = ["refVoucherId"])
    ]
)
data class BillAllocation(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val voucherEntryId: Int, // The entry that is MAKING the payment (e.g. in Receipt voucher)
    val refVoucherId: Int? = null,   // The voucher being PAID (Optional if refName is used)
    val refName: String? = null,     // Bill Number / Name
    val refType: String? = null,     // New, Against, Advance, OnAccount
    val dueDate: Long? = null,       // Due Date
    val amount: Long       // paise; the amount adjusted
)