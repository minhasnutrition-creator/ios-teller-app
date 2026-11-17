package com.minhas.teller.data.dao

import androidx.room.*
import com.minhas.teller.data.entities.AccountGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountGroupDao {
    @Query("SELECT * FROM account_groups WHERE companyId = :companyId ORDER BY name ASC")
    fun getAllGroups(companyId: Int): Flow<List<AccountGroup>>

    @Query("SELECT * FROM account_groups WHERE id = :id")
    suspend fun getGroupById(id: Int): AccountGroup?

    @Query("SELECT COUNT(*) FROM account_groups WHERE companyId = :companyId")
    suspend fun getGroupCount(companyId: Int): Int

    @Query("SELECT name FROM account_groups WHERE companyId = :companyId")
    suspend fun getGroupNames(companyId: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: AccountGroup)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(groups: List<AccountGroup>)

    @Update
    suspend fun update(group: AccountGroup)

    @Delete
    suspend fun delete(group: AccountGroup)
}
