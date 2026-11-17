package com.minhas.teller.data.dao

import androidx.room.*
import com.minhas.teller.data.entities.Godown

@Dao
interface GodownDao {
    @Query("SELECT * FROM godowns WHERE companyId = :companyId ORDER BY name ASC")
    suspend fun getAllGodowns(companyId: Int): List<Godown>

    @Query("SELECT * FROM godowns WHERE id = :id")
    suspend fun getGodownById(id: Int): Godown?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(godown: Godown): Long

    @Update
    suspend fun update(godown: Godown)

    @Delete
    suspend fun delete(godown: Godown)
}
