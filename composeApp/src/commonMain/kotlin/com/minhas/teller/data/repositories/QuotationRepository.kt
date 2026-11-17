package com.minhas.teller.data.repositories

import com.minhas.teller.data.dao.QuotationDao
import com.minhas.teller.data.entities.Quotation
import kotlinx.coroutines.flow.Flow
import java.util.Date

class QuotationRepository(private val quotationDao: QuotationDao) {
    
    fun getAllQuotations(companyId: Int): Flow<List<Quotation>> {
        return quotationDao.getAllByCompany(companyId)
    }
    
    fun getQuotationsByDateRange(companyId: Int, fromDate: Date, toDate: Date): Flow<List<Quotation>> {
        return quotationDao.getByDateRange(companyId, fromDate, toDate)
    }
    
    suspend fun getQuotationById(id: Int): Quotation? {
        return quotationDao.getById(id)
    }
    
    suspend fun insert(quotation: Quotation): Long {
        return quotationDao.insert(quotation)
    }
    
    suspend fun update(quotation: Quotation) {
        quotationDao.update(quotation.copy(updatedAt = Date()))
    }
    
    suspend fun delete(quotation: Quotation) {
        quotationDao.delete(quotation)
    }
    
    suspend fun deleteById(id: Int) {
        quotationDao.deleteById(id)
    }
    
    suspend fun getNextQuotationNumber(companyId: Int): String {
        val lastNumber = quotationDao.getLastQuotationNumber(companyId) ?: 0
        val nextNumber = lastNumber + 1
        return "QT-${String.format("%03d", nextNumber)}"
    }

    suspend fun searchByCustomer(query: String, companyId: Int): List<Quotation> {
        return quotationDao.searchByCustomer(query, companyId)
    }
    
    suspend fun getCount(companyId: Int): Int {
        return quotationDao.getCount(companyId)
    }
}
