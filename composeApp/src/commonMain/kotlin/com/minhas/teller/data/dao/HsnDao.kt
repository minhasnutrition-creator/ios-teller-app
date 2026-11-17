package com.minhas.teller.data.dao

import androidx.room.*
import com.minhas.teller.data.entities.HsnEntry

@Dao
interface HsnDao {
    @Query("SELECT COUNT(*) FROM hsn_directory")
    suspend fun count(): Int

    @Query("SELECT * FROM hsn_directory ORDER BY hsnCode ASC")
    suspend fun getAll(): List<HsnEntry>

    @Query("SELECT * FROM hsn_directory WHERE hsnCode = :hsnCode LIMIT 1")
    suspend fun getByCode(hsnCode: String): HsnEntry?

    @Query("SELECT * FROM hsn_directory WHERE hsnCode LIKE :query OR description LIKE :query OR keywords LIKE :query ORDER BY hsnCode ASC")
    suspend fun search(query: String): List<HsnEntry>

    @Query("SELECT DISTINCT category FROM hsn_directory ORDER BY category ASC")
    suspend fun getCategories(): List<String>

    @Query("SELECT * FROM hsn_directory WHERE category = :category ORDER BY hsnCode ASC")
    suspend fun getEntriesByCategory(category: String): List<HsnEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HsnEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<HsnEntry>)

    @Delete
    suspend fun delete(entry: HsnEntry)

    @Query("DELETE FROM hsn_directory")
    suspend fun deleteAll()
}
