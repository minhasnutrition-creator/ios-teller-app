package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "godowns",
    indices = [Index(value = ["companyId"])]
)
data class Godown(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val companyId: Int = 0
)
