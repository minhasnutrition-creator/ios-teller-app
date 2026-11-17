package com.minhas.teller.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.minhas.teller.data.entities.AuditLog

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(log: AuditLog)

    @Query("SELECT * FROM audit_logs WHERE companyId = :companyId ORDER BY timestamp DESC")
    suspend fun getLogsForCompany(companyId: Int): List<AuditLog>

    @Query("SELECT * FROM audit_logs WHERE entityType = :entityType AND entityId = :entityId ORDER BY timestamp DESC")
    suspend fun getLogsForEntity(entityType: String, entityId: String): List<AuditLog>
}
