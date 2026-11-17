package com.minhas.teller.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.minhas.teller.data.entities.ImportJob
import com.minhas.teller.data.entities.ImportMapping
import com.minhas.teller.data.entities.EvidenceAttachment

@Dao
interface ImportJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: ImportJob): Long

    @Update
    suspend fun update(job: ImportJob)

    @Query("SELECT * FROM import_jobs WHERE id = :id")
    suspend fun getJobById(id: Int): ImportJob?

    @Query("SELECT * FROM import_jobs WHERE companyId = :companyId ORDER BY createdAt DESC")
    suspend fun getJobsForCompany(companyId: Int): List<ImportJob>

    @Delete
    suspend fun delete(job: ImportJob)
}

@Dao
interface ImportMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: ImportMapping): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<ImportMapping>)

    @Query("SELECT * FROM import_mappings WHERE importJobId = :jobId")
    suspend fun getMappingsForJob(jobId: Int): List<ImportMapping>

    @Query("DELETE FROM import_mappings WHERE importJobId = :jobId")
    suspend fun deleteMappingsForJob(jobId: Int)
}

@Dao
interface EvidenceAttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: EvidenceAttachment): Long

    @Query("SELECT * FROM evidence_attachments WHERE voucherId = :voucherId")
    suspend fun getAttachmentsForVoucher(voucherId: Int): List<EvidenceAttachment>

    @Query("SELECT * FROM evidence_attachments WHERE companyId = :companyId ORDER BY dateAdded DESC")
    suspend fun getAttachmentsForCompany(companyId: Int): List<EvidenceAttachment>

    @Query("DELETE FROM evidence_attachments WHERE id = :id")
    suspend fun deleteAttachmentById(id: Int)
}
