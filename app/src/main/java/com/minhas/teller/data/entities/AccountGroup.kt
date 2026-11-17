package com.minhas.teller.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "account_groups",
    indices = [Index(value = ["companyId"])]
)
data class AccountGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val parentGroup: String?, // For hierarchy, though Teller often has 'Primary'
    val companyId: Int
)
