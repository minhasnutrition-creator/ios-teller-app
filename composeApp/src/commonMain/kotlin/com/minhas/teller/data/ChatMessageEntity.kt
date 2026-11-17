package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["companyId", "timestamp"]),
        Index(value = ["sessionId"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageType: String,
    val text: String? = null,
    val isUser: Boolean,
    val timestamp: Date,
    val shouldSpeak: Boolean = true,
    val draftData: String? = null,
    val draftType: String? = null,
    val companyId: Long? = null,
    val isEncrypted: Boolean = false,
    val sessionId: String,
    val isDeleted: Boolean = false,
    val createdAt: Date,
    val updatedAt: Date
)
