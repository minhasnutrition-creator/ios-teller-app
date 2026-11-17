package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "voucher_entries",
    indices = [
        Index(value = ["voucherId"]),
        Index(value = ["ledgerId"])
    ]
)
data class VoucherEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val voucherId: Int, // Foreign key to Voucher
    val ledgerId: Int, // Foreign key to Ledger
    val amount: Long, // paise
    val isDebit: Boolean // true for debit, false for credit
)