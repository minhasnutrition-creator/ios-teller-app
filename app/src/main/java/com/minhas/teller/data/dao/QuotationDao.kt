package com.minhas.teller.data.dao

import androidx.room.*
import com.minhas.teller.data.entities.Quotation
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface QuotationDao {
    
    @Query("SELECT * FROM quotations WHERE companyId = :companyId ORDER BY createdAt DESC")
    fun getAllByCompany(companyId: Int): Flow<List<Quotation>>
    
    @Query("SELECT * FROM quotations WHERE companyId = :companyId AND quotationDate BETWEEN :fromDate AND :toDate ORDER BY createdAt DESC")
    fun getByDateRange(companyId: Int, fromDate: Date, toDate: Date): Flow<List<Quotation>>
    
    @Query("SELECT * FROM quotations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Quotation?
    
    @Query("SELECT COUNT(*) FROM quotations WHERE companyId = :companyId")
    suspend fun getCount(companyId: Int): Int
    
    @Query("SELECT * FROM quotations WHERE companyId = :companyId AND customerName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    suspend fun searchByCustomer(query: String, companyId: Int): List<Quotation>
    
    @Insert
    suspend fun insert(quotation: Quotation): Long
    
    @Update
    suspend fun update(quotation: Quotation)
    
    @Delete
    suspend fun delete(quotation: Quotation)
    
    @Query("DELETE FROM quotations WHERE id = :id")
    suspend fun deleteById(id: Int)
    
    @Query("SELECT MAX(CAST(SUBSTR(quotationNumber, 4) AS INTEGER)) FROM quotations WHERE companyId = :companyId AND quotationNumber LIKE 'QT-%'")
    suspend fun getLastQuotationNumber(companyId: Int): Int?
}
