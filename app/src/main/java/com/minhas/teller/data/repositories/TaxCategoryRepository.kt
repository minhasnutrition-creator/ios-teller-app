package com.minhas.teller.data.repositories

import com.minhas.teller.data.dao.TaxCategoryDao
import com.minhas.teller.data.entities.TaxCategory

class TaxCategoryRepository(private val dao: TaxCategoryDao) {
    suspend fun getAll(companyId: Int): List<TaxCategory> = dao.getAll(companyId)
    suspend fun getById(id: Int): TaxCategory? = dao.getById(id)
    suspend fun insert(category: TaxCategory): Long = dao.insert(category)
    suspend fun insertAll(categories: List<TaxCategory>) = dao.insertAll(categories)
    suspend fun update(category: TaxCategory) = dao.update(category)
    suspend fun delete(category: TaxCategory) = dao.delete(category)
    suspend fun deleteAll(companyId: Int) = dao.deleteAll(companyId)
}
