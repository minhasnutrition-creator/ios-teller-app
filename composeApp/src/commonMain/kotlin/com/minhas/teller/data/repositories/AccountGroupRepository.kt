package com.minhas.teller.data.repositories

import com.minhas.teller.data.dao.AccountGroupDao
import com.minhas.teller.data.entities.AccountGroup
import kotlinx.coroutines.flow.Flow

class AccountGroupRepository(private val dao: AccountGroupDao) {
    fun getAllGroups(companyId: Int): Flow<List<AccountGroup>> = dao.getAllGroups(companyId)

    suspend fun getGroupById(id: Int): AccountGroup? = dao.getGroupById(id)

    suspend fun getGroupCount(companyId: Int): Int = dao.getGroupCount(companyId)

    suspend fun getGroupNames(companyId: Int): List<String> = dao.getGroupNames(companyId)

    suspend fun insert(group: AccountGroup) = dao.insert(group)

    suspend fun insertAll(groups: List<AccountGroup>) = dao.insertAll(groups)

    suspend fun update(group: AccountGroup) = dao.update(group)

    suspend fun delete(group: AccountGroup) = dao.delete(group)
}
