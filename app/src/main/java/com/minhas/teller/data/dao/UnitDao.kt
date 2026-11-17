package com.minhas.teller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.minhas.teller.data.entities.Unit

@Dao
interface UnitDao {
    @Query("SELECT * FROM units WHERE companyId = :companyId AND isDeleted = 0 ORDER BY name ASC")
    suspend fun getAllUnits(companyId: Int): List<Unit>

    @Query("SELECT * FROM units WHERE companyId = :companyId")
    suspend fun getAllUnitsForSync(companyId: Int): List<Unit>

    @Query("SELECT * FROM units WHERE id = :unitId")
    suspend fun getUnitById(unitId: Int): Unit?

    @Query("SELECT * FROM units WHERE companyId = :companyId AND name = :name LIMIT 1")
    suspend fun getUnitByName(companyId: Int, name: String): Unit?

    @Query("SELECT * FROM units WHERE uid = :uid LIMIT 1")
    suspend fun getUnitByUid(uid: String): Unit?

    @Insert
    suspend fun insert(unit: Unit): Long

    @Update
    suspend fun update(unit: Unit)

    @Delete
    suspend fun delete(unit: Unit)
}
