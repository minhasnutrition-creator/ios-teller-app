package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["companyId"]),
        Index(value = ["entityType", "entityId"])
    ]
)
data class AuditLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Date = Date(),
    val action: String, // e.g., "INSERT", "UPDATE", "DELETE"
    val entityType: String, // e.g., "VOUCHER", "LEDGER"
    val entityId: String, // ID of the voucher or ledger
    val details: String, // JSON or summary of changes
    val companyId: Int = 0
)
