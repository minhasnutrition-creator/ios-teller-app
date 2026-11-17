package com.minhas.teller.data.repositories

import com.minhas.teller.data.dao.CompanyDao
import com.minhas.teller.data.entities.Company
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CompanyRepository(private val companyDao: CompanyDao) {
    val allCompanies: Flow<List<Company>> = companyDao.getAllCompanies()

    fun getCompany(id: Int): Flow<Company?> = companyDao.getCompanyById(id)

    suspend fun getCompanySync(id: Int): Company? = companyDao.getCompanyByIdSync(id)

    suspend fun insert(company: Company): Long {
        val id = companyDao.insert(
            company.copy(uid = company.uid ?: UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis())
        )
        com.minhas.teller.utils.CloudAutoBackup.trigger()
        return id
    }

    suspend fun update(company: Company) {
        companyDao.update(
            company.copy(uid = company.uid ?: UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis())
        )
        com.minhas.teller.utils.CloudAutoBackup.trigger()
    }

    suspend fun delete(company: Company) {
        // Soft delete the company and its child records so tombstones propagate to cloud sync
        companyDao.softDeleteCompanyCascade(company.id)
        com.minhas.teller.utils.CloudAutoBackup.trigger()
    }
}
