package com.minhas.teller.data.repositories

import com.minhas.teller.data.dao.GodownDao
import com.minhas.teller.data.entities.Godown

class GodownRepository(private val dao: GodownDao) {
    suspend fun getAllGodowns(companyId: Int): List<Godown> = dao.getAllGodowns(companyId)
    suspend fun getGodownById(id: Int): Godown? = dao.getGodownById(id)
    suspend fun insert(godown: Godown): Long = dao.insert(godown)
    suspend fun update(godown: Godown) = dao.update(godown)
    suspend fun delete(godown: Godown) = dao.delete(godown)
}
