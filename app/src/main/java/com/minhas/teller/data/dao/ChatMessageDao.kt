package com.minhas.teller.data.dao

import androidx.room.*
import com.minhas.teller.data.entities.ChatMessageEntity

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE companyId = :companyId AND isDeleted = 0 ORDER BY timestamp ASC")
    suspend fun getMessagesForCompany(companyId: Long): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND isDeleted = 0 ORDER BY timestamp ASC")
    suspend fun getMessagesBySession(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Update
    suspend fun update(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Query("UPDATE chat_messages SET isDeleted = 1 WHERE sessionId = :sessionId")
    suspend fun softDeleteSession(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE companyId = :companyId")
    suspend fun deleteAllForCompany(companyId: Long)
}
