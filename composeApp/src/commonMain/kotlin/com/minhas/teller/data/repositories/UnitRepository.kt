package com.minhas.teller.data.repositories

import com.minhas.teller.data.dao.UnitDao
import com.minhas.teller.data.entities.Unit
import java.util.UUID

class UnitRepository(private val unitDao: UnitDao) {

    suspend fun getAllUnits(companyId: Int): List<Unit> {
        return unitDao.getAllUnits(companyId)
    }

    suspend fun getUnitByName(companyId: Int, name: String): Unit? {
        return unitDao.getUnitByName(companyId, name)
    }

    suspend fun getUnitById(id: Int): Unit? {
        return unitDao.getUnitById(id)
    }

    suspend fun insert(unit: Unit): Long {
        val id = unitDao.insert(
            unit.copy(uid = unit.uid ?: UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis())
        )
        com.minhas.teller.utils.CloudAutoBackup.trigger()
        return id
    }

    suspend fun update(unit: Unit) {
        unitDao.update(
            unit.copy(uid = unit.uid ?: UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis())
        )
        com.minhas.teller.utils.CloudAutoBackup.trigger()
    }

    suspend fun delete(unit: Unit) {
        unitDao.update(unit.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
        com.minhas.teller.utils.CloudAutoBackup.trigger()
    }
}
