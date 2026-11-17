package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "import_jobs",
    indices = [Index(value = ["companyId"])]
)
data class ImportJob(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val companyId: Int,
    val type: String, // tally_xml, excel_tb, excel_sales, pdf_ledger, pdf_invoice, bank_statement
    val status: String, // pending, parsing, needs_mapping, ready_to_post, completed, failed
    val rowCount: Int = 0,
    val errorDetails: String? = null,
    val summaryJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "import_mappings",
    indices = [Index(value = ["importJobId"])]
)
data class ImportMapping(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val importJobId: Int,
    val sourceField: String,
    val targetEntity: String, // Ledger, StockItem, Voucher, VoucherEntry, InventoryEntry
    val targetField: String
)

@Entity(
    tableName = "evidence_attachments",
    indices = [
        Index(value = ["voucherId"]),
        Index(value = ["companyId"])
    ]
)
data class EvidenceAttachment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val voucherId: Int,
    val companyId: Int,
    val fileUri: String,
    val ocrText: String? = null,
    val dateAdded: Long = System.currentTimeMillis()
)
