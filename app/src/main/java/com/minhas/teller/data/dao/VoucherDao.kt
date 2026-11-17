package com.minhas.teller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherWithAllEntries
import java.util.Date

@Dao
interface VoucherDao {
    @Transaction
    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND isDeleted = 0 ORDER BY date DESC")
    suspend fun getAllVouchersWithAllEntries(companyId: Int): List<VoucherWithAllEntries>

    @Transaction
    @Query("SELECT * FROM vouchers WHERE companyId = :companyId ORDER BY date DESC")
    suspend fun getAllVouchersWithAllEntriesForSync(companyId: Int): List<VoucherWithAllEntries>

    @Transaction
    @Query("SELECT * FROM vouchers WHERE companyId = :companyId AND isDeleted = 0 AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getAllVouchersWithAllEntriesBetweenDates(companyId: Int, startDate: Date, endDate: Date): List<VoucherWithAllEntries>

    @Transaction
    @Query("""
        SELECT DISTINCT v.* FROM vouchers v
        INNER JOIN voucher_entries ve ON ve.voucherId = v.id
        WHERE v.companyId = :companyId AND v.isDeleted = 0 AND ve.ledgerId = :ledgerId
        ORDER BY v.date DESC
    """)
    suspend fun getAllVouchersWithAllEntriesForLedger(companyId: Int, ledgerId: Int): List<VoucherWithAllEntries>

    @Transaction
    @Query("""
        SELECT DISTINCT v.* FROM vouchers v
        INNER JOIN voucher_entries ve ON ve.voucherId = v.id
        WHERE v.companyId = :companyId AND v.isDeleted = 0 AND ve.ledgerId = :ledgerId AND v.date BETWEEN :startDate AND :endDate
        ORDER BY v.date DESC
    """)
    suspend fun getAllVouchersWithAllEntriesForLedgerBetweenDates(companyId: Int, ledgerId: Int, startDate: Date, endDate: Date): List<VoucherWithAllEntries>

    @Transaction
    @Query("SELECT * FROM vouchers WHERE id = :voucherId")
    suspend fun getVoucherWithAllEntriesById(voucherId: Int): VoucherWithAllEntries?

    @Query("SELECT * FROM vouchers WHERE id = :voucherId")
    suspend fun getVoucherById(voucherId: Int): Voucher?

    @Insert
    suspend fun insert(voucher: Voucher): Long

    @Update
    suspend fun update(voucher: Voucher)

    @Delete
    suspend fun delete(voucher: Voucher)

    /** Sum debit entries for vouchers of a given type — replaces loading every voucher+entry
     *  just to sum one field. Used by getTotalSales / getTotalPurchase etc. */
    @Query("""
        SELECT COALESCE(SUM(ve.amount), 0)
        FROM voucher_entries ve
        INNER JOIN vouchers v ON ve.voucherId = v.id
        WHERE v.companyId = :companyId AND v.isDeleted = 0 AND v.type = :type AND ve.isDebit = 1
    """)
    suspend fun sumVoucherEntryAmountsByType(companyId: Int, type: String): Long

    @Query("SELECT * FROM vouchers WHERE voucherNumber = :voucherNumber AND companyId = :companyId AND isDeleted = 0 LIMIT 1")
    suspend fun getVoucherByNumber(voucherNumber: String, companyId: Int): Voucher?

    /** Every live Sales bill number of a company - used to hand out the next "INV-<n>" on the PC. */
    @Query("SELECT voucherNumber FROM vouchers WHERE companyId = :companyId AND type = 'Sales' AND isDeleted = 0")
    suspend fun getSalesVoucherNumbers(companyId: Int): List<String>

    @Query("SELECT * FROM vouchers WHERE uid = :uid LIMIT 1")
    suspend fun getVoucherByUid(uid: String): Voucher?

    @Query("""
        SELECT id FROM vouchers
        WHERE companyId = :companyId AND (
            refDisplayName = 'VPS MOTORS' OR
            refDisplayName = 'General Customer' OR
            refDisplayName = 'General Supplier' OR
            refDisplayName = 'General Account' OR
            refDisplayName = 'Primary Bank' OR
            (voucherNumber LIKE 'REC-%' AND (narration LIKE '%TallyBackup%' OR narration LIKE '%.001%' OR narration LIKE '%TBK%' OR narration LIKE '%0000%' OR narration IS NULL))
        )
    """)
    suspend fun getBogusVoucherIds(companyId: Int): List<Int>

    @Query("DELETE FROM vouchers WHERE id IN (:voucherIds)")
    suspend fun deleteVouchersByIds(voucherIds: List<Int>): Int

    @Query("DELETE FROM vouchers WHERE companyId = :companyId AND (refDisplayName = 'VPS MOTORS' OR refDisplayName = 'General Customer' OR refDisplayName = 'General Supplier')")
    suspend fun deleteBogusVouchers(companyId: Int): Int
}
