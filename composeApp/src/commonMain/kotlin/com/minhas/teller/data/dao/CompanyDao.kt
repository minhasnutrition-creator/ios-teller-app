package com.minhas.teller.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Transaction
import com.minhas.teller.data.entities.Company
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {
    @Query("SELECT * FROM companies WHERE isDeleted = 0")
    fun getAllCompanies(): Flow<List<Company>>

    @Query("SELECT * FROM companies WHERE id = :id")
    fun getCompanyById(id: Int): Flow<Company?>

    @Query("SELECT * FROM companies WHERE id = :id LIMIT 1")
    suspend fun getCompanyByIdSync(id: Int): Company?

    @Query("SELECT * FROM companies WHERE isDeleted = 0")
    suspend fun getAllCompaniesSync(): List<Company>

    @Query("SELECT * FROM companies")
    suspend fun getAllCompaniesForSync(): List<Company>

    @Query("SELECT * FROM companies WHERE uid = :uid LIMIT 1")
    suspend fun getCompanyByUid(uid: String): Company?

    @Insert
    suspend fun insert(company: Company): Long

    @Update
    suspend fun update(company: Company)

    @Delete
    suspend fun delete(company: Company)

    @Query("DELETE FROM bill_allocations WHERE refVoucherId IN (SELECT id FROM vouchers WHERE companyId = :companyId) OR voucherEntryId IN (SELECT id FROM voucher_entries WHERE voucherId IN (SELECT id FROM vouchers WHERE companyId = :companyId))")
    suspend fun deleteBillAllocationsForCompany(companyId: Int)

    @Query("DELETE FROM inventory_entries WHERE voucherId IN (SELECT id FROM vouchers WHERE companyId = :companyId)")
    suspend fun deleteInventoryEntriesForCompany(companyId: Int)

    @Query("DELETE FROM voucher_entries WHERE voucherId IN (SELECT id FROM vouchers WHERE companyId = :companyId)")
    suspend fun deleteVoucherEntriesForCompany(companyId: Int)

    @Query("DELETE FROM vouchers WHERE companyId = :companyId")
    suspend fun deleteVouchersForCompany(companyId: Int)

    @Query("DELETE FROM quotations WHERE companyId = :companyId")
    suspend fun deleteQuotationsForCompany(companyId: Int)

    @Query("DELETE FROM stock_items WHERE companyId = :companyId")
    suspend fun deleteStockItemsForCompany(companyId: Int)

    @Query("DELETE FROM units WHERE companyId = :companyId")
    suspend fun deleteUnitsForCompany(companyId: Int)

    @Query("DELETE FROM ledgers WHERE companyId = :companyId")
    suspend fun deleteLedgersForCompany(companyId: Int)

    @Query("DELETE FROM godowns WHERE companyId = :companyId")
    suspend fun deleteGodownsForCompany(companyId: Int)

    @Query("DELETE FROM tax_categories WHERE companyId = :companyId")
    suspend fun deleteTaxCategoriesForCompany(companyId: Int)

    @Query("DELETE FROM chat_messages WHERE companyId = :companyId")
    suspend fun deleteChatMessagesForCompany(companyId: Int)

    @Query("DELETE FROM account_groups WHERE companyId = :companyId")
    suspend fun deleteAccountGroupsForCompany(companyId: Int)

    @Query("DELETE FROM companies WHERE id = :companyId")
    suspend fun deleteCompanyRecord(companyId: Int)

    @Query("UPDATE companies SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :companyId")
    suspend fun softDeleteCompany(companyId: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE vouchers SET isDeleted = 1, updatedAt = :updatedAt WHERE companyId = :companyId")
    suspend fun softDeleteVouchersForCompany(companyId: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE ledgers SET isDeleted = 1, updatedAt = :updatedAt WHERE companyId = :companyId")
    suspend fun softDeleteLedgersForCompany(companyId: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE stock_items SET isDeleted = 1, updatedAt = :updatedAt WHERE companyId = :companyId")
    suspend fun softDeleteStockItemsForCompany(companyId: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE units SET isDeleted = 1, updatedAt = :updatedAt WHERE companyId = :companyId")
    suspend fun softDeleteUnitsForCompany(companyId: Int, updatedAt: Long = System.currentTimeMillis())

    @Transaction
    suspend fun softDeleteCompanyCascade(companyId: Int, updatedAt: Long = System.currentTimeMillis()) {
        softDeleteVouchersForCompany(companyId, updatedAt)
        softDeleteStockItemsForCompany(companyId, updatedAt)
        softDeleteUnitsForCompany(companyId, updatedAt)
        softDeleteLedgersForCompany(companyId, updatedAt)
        deleteBillAllocationsForCompany(companyId)
        deleteInventoryEntriesForCompany(companyId)
        deleteVoucherEntriesForCompany(companyId)
        deleteQuotationsForCompany(companyId)
        deleteGodownsForCompany(companyId)
        deleteTaxCategoriesForCompany(companyId)
        deleteChatMessagesForCompany(companyId)
        deleteAccountGroupsForCompany(companyId)
        softDeleteCompany(companyId, updatedAt)
    }

    @Transaction
    suspend fun deleteCompanyCascade(companyId: Int) {
        deleteBillAllocationsForCompany(companyId)
        deleteInventoryEntriesForCompany(companyId)
        deleteVoucherEntriesForCompany(companyId)
        deleteVouchersForCompany(companyId)
        deleteQuotationsForCompany(companyId)
        deleteStockItemsForCompany(companyId)
        deleteUnitsForCompany(companyId)
        deleteLedgersForCompany(companyId)
        deleteGodownsForCompany(companyId)
        deleteTaxCategoriesForCompany(companyId)
        deleteChatMessagesForCompany(companyId)
        deleteAccountGroupsForCompany(companyId)
        deleteCompanyRecord(companyId)
    }
}
