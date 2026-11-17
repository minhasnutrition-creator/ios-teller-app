package com.minhas.teller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.minhas.teller.data.entities.VoucherEntry

@Dao
interface VoucherEntryDao {
    @Query("SELECT * FROM voucher_entries WHERE voucherId = :voucherId ORDER BY id ASC")
    suspend fun getVoucherEntriesForVoucher(voucherId: Int): List<VoucherEntry>

    @Query("SELECT * FROM voucher_entries WHERE ledgerId = :ledgerId")
    suspend fun getVoucherEntriesForLedger(ledgerId: Int): List<VoucherEntry>

    @Insert
    suspend fun insert(voucherEntry: VoucherEntry): Long

    @Insert
    suspend fun insertAll(voucherEntries: List<VoucherEntry>): List<Long>

    @Update
    suspend fun update(voucherEntry: VoucherEntry)

    @Delete
    suspend fun delete(voucherEntry: VoucherEntry)

    @Query("DELETE FROM voucher_entries WHERE voucherId = :voucherId")
    suspend fun deleteVoucherEntriesForVoucher(voucherId: Int)

    @Query("DELETE FROM voucher_entries WHERE voucherId IN (:voucherIds)")
    suspend fun deleteVoucherEntriesForVouchers(voucherIds: List<Int>): Int

    @Query("SELECT id FROM voucher_entries WHERE voucherId IN (:voucherIds)")
    suspend fun getEntryIdsForVouchers(voucherIds: List<Int>): List<Int>
}
