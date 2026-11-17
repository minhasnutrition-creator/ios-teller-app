package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Hindi-English item name mapping entity
 * Maps Hindi/Hinglish names to English for Smart Tax detection
 */
@Entity(
    tableName = "hindi_mappings",
    indices = [
        Index(value = ["hinglishName"]),
        Index(value = ["englishName"])
    ]
)
data class HindiMapping(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val hindiName: String, // e.g., "केला"
    val hinglishName: String, // e.g., "kela", "kelaa"
    val englishName: String, // e.g., "banana"
    val category: String, // e.g., "fruit", "grocery"
    val hsnCode: String? = null,
    val keywords: String // Comma-separated for fuzzy matching
)
