package com.minhas.teller.data.dao

import androidx.room.*
import com.minhas.teller.data.entities.HindiMapping
import kotlinx.coroutines.flow.Flow

@Dao
interface HindiMappingDao {
    
    @Query("SELECT COUNT(*) FROM hindi_mappings")
    suspend fun count(): Int
    
    @Query("SELECT * FROM hindi_mappings ORDER BY hindiName ASC")
    suspend fun getAll(): List<HindiMapping>
    
    @Query("SELECT * FROM hindi_mappings WHERE hinglishName = :name OR hindiName = :name LIMIT 1")
    suspend fun findByHindiName(name: String): HindiMapping?
    
    @Query("SELECT * FROM hindi_mappings WHERE englishName = :name LIMIT 1")
    suspend fun findByEnglishName(name: String): HindiMapping?
    
    @Query("SELECT * FROM hindi_mappings WHERE keywords LIKE '%' || :query || '%' LIMIT 5")
    suspend fun search(query: String): List<HindiMapping>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: HindiMapping): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<HindiMapping>)
    
    @Query("DELETE FROM hindi_mappings")
    suspend fun deleteAll()
}
