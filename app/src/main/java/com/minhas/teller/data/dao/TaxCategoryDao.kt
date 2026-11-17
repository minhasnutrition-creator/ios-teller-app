package com.minhas.teller.data.dao

import androidx.room.*
import com.minhas.teller.data.entities.TaxCategory

@Dao
interface TaxCategoryDao {
    @Query("SELECT * FROM tax_categories WHERE companyId = :companyId ORDER BY name ASC")
    suspend fun getAll(companyId: Int): List<TaxCategory>

    @Query("SELECT * FROM tax_categories WHERE id = :id")
    suspend fun getById(id: Int): TaxCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: TaxCategory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<TaxCategory>)

    @Update
    suspend fun update(category: TaxCategory)

    @Delete
    suspend fun delete(category: TaxCategory)

    @Query("DELETE FROM tax_categories WHERE companyId = :companyId")
    suspend fun deleteAll(companyId: Int)
}
