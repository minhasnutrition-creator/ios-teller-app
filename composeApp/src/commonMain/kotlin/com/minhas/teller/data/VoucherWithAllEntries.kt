package com.minhas.teller.data.entities

import androidx.room.Embedded
import androidx.room.Relation

data class VoucherWithAllEntries(
    @Embedded val voucher: Voucher,
    @Relation(
        parentColumn = "id",
        entityColumn = "voucherId"
    )
    val voucherEntries: List<VoucherEntry>,
    @Relation(
        parentColumn = "id",
        entityColumn = "voucherId"
    )
    val inventoryEntries: List<InventoryEntry>
)