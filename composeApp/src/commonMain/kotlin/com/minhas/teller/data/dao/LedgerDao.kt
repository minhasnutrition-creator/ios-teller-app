package com.minhas.teller.data.dao

import androidx.room.*
import com.minhas.teller.data.entities.Ledger
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface LedgerDao {

    @Query("SELECT * FROM ledgers WHERE companyId = :companyId AND isDeleted = 0 ORDER BY name ASC")
    fun getAllLedgers(companyId: Int): Flow<List<Ledger>>

    @Query("SELECT * FROM ledgers WHERE companyId = :companyId AND isDeleted = 0")
    suspend fun getAllLedgersSync(companyId: Int): List<Ledger>

    @Query("SELECT * FROM ledgers WHERE companyId = :companyId")
    suspend fun getAllLedgersForSync(companyId: Int): List<Ledger>

    @Query("SELECT * FROM ledgers WHERE id = :id")
    fun getLedgerById(id: Int): Flow<Ledger>

    @Query("SELECT * FROM ledgers WHERE id = :id")
    suspend fun getLedgerByIdSync(id: Int): Ledger?

    @Query("SELECT * FROM ledgers WHERE uid = :uid LIMIT 1")
    suspend fun getLedgerByUid(uid: String): Ledger?

    @Query("SELECT * FROM ledgers WHERE companyId = :companyId AND isDeleted = 0 AND (`group` = 'Cash-in-hand' OR `group` = 'Bank Accounts')")
    fun getCashBankLedgers(companyId: Int): Flow<List<Ledger>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ledger: Ledger): Long

    @Update
    suspend fun update(ledger: Ledger)

    @Delete
    suspend fun delete(ledger: Ledger)

    @Query("SELECT l.openingBalance + ifnull(SUM(CASE WHEN ve.isDebit THEN ve.amount ELSE -ve.amount END), 0) FROM ledgers l LEFT JOIN voucher_entries ve ON l.id = ve.ledgerId INNER JOIN vouchers v ON v.id = ve.voucherId AND v.companyId = :companyId AND v.date <= :toDate AND v.type != 'Quotation' WHERE l.id = :ledgerId")
    fun getClosingBalance(ledgerId: Int, companyId: Int, toDate: Date): Flow<Long?>

    @Query("DELETE FROM ledgers WHERE companyId = :companyId AND (TRIM(name) = '' OR name IS NULL) AND id NOT IN (SELECT DISTINCT ledgerId FROM voucher_entries)")
    suspend fun deleteNamelessUnusedLedgers(companyId: Int): Int

    @Query("UPDATE ledgers SET name = :fallbackName WHERE companyId = :companyId AND (TRIM(name) = '' OR name IS NULL)")
    suspend fun renameNamelessUsedLedgers(companyId: Int, fallbackName: String = "General Account"): Int

    @Query("DELETE FROM ledgers WHERE companyId = :companyId AND openingBalance = 0 AND UPPER(TRIM(name)) IN (:dummyNamesUpper) AND id NOT IN (SELECT DISTINCT ledgerId FROM voucher_entries)")
    suspend fun deleteUnusedDummyLedgers(companyId: Int, dummyNamesUpper: List<String>): Int

    @Query("""
        DELETE FROM ledgers
        WHERE companyId = :companyId AND (
            name = 'VPS MOTORS' OR
            name = 'General Account' OR
            name = 'General Customer' OR
            name = 'General Supplier' OR
            name = 'Primary Bank' OR
            UPPER(TRIM(name)) IN (:dummyNamesUpper) OR
            name LIKE '%JetOLEDB%' OR
            name LIKE 'MSys%' OR
            name LIKE '%Standard Jet%' OR
            name LIKE '%Standard ACE%' OR
            name LIKE '%StoreData%' OR
            name LIKE '%ItemType%' OR
            TRIM(name) = '' OR
            name IS NULL
        ) AND id NOT IN (SELECT DISTINCT ledgerId FROM voucher_entries)
    """)
    suspend fun purgeBogusUnusedLedgers(companyId: Int, dummyNamesUpper: List<String>): Int

    @Query("DELETE FROM ledgers WHERE id IN (:ledgerIds)")
    suspend fun deleteLedgersByIds(ledgerIds: List<Int>): Int

    @Query("UPDATE ledgers SET name = 'Primary Bank' WHERE companyId = :companyId AND UPPER(TRIM(name)) = 'BANK ACCOUNTS' AND id IN (SELECT DISTINCT ledgerId FROM voucher_entries)")
    suspend fun renameUsedBankAccountsLedger(companyId: Int): Int

    @Query("UPDATE ledgers SET name = 'General Customer' WHERE companyId = :companyId AND UPPER(TRIM(name)) = 'SUNDRY DEBTORS' AND id IN (SELECT DISTINCT ledgerId FROM voucher_entries)")
    suspend fun renameUsedSundryDebtorsLedger(companyId: Int): Int

    @Query("UPDATE ledgers SET name = 'General Supplier' WHERE companyId = :companyId AND UPPER(TRIM(name)) = 'SUNDRY CREDITORS' AND id IN (SELECT DISTINCT ledgerId FROM voucher_entries)")
    suspend fun renameUsedSundryCreditorsLedger(companyId: Int): Int
}
