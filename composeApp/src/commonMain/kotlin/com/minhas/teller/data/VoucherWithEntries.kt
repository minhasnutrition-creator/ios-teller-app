package com.minhas.teller.data.entities

import androidx.room.Embedded
import androidx.room.Relation

data class VoucherWithEntries(
    @Embedded val voucher: Voucher,
    @Relation(
        parentColumn = "id",
        entityColumn = "voucherId"
    )
    val entries: List<VoucherEntry>
)