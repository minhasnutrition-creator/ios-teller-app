package com.minhas.teller.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minhas.teller.data.entities.BillAllocation
import kotlinx.coroutines.flow.Flow

import androidx.room.Embedded
import java.util.Date

data class BillAllocationDetail(
    @Embedded val allocation: BillAllocation,
    val isDebit: Boolean,
    val date: Date,
    val voucherId: Int,
    val voucherNumber: String,
    val type: String
)

@Dao
interface BillAllocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(billAllocation: BillAllocation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(billAllocations: List<BillAllocation>)

    @Query("SELECT * FROM bill_allocations WHERE refVoucherId = :refVoucherId")
    fun getAllocationsForRefVoucher(refVoucherId: Int): Flow<List<BillAllocation>>

    @Query("SELECT * FROM bill_allocations WHERE voucherEntryId = :voucherEntryId")
    fun getAllocationsByEntryId(voucherEntryId: Int): Flow<List<BillAllocation>>
    
    @Query("DELETE FROM bill_allocations WHERE voucherEntryId = :voucherEntryId")
    suspend fun deleteAllocationsByEntryId(voucherEntryId: Int)

    @Query("DELETE FROM bill_allocations WHERE voucherEntryId IN (:entryIds)")
    suspend fun deleteAllocationsByEntryIds(entryIds: List<Int>): Int

    @Query("SELECT ba.* FROM bill_allocations ba " +
           "INNER JOIN voucher_entries ve ON ba.voucherEntryId = ve.id " +
           "INNER JOIN vouchers v ON ve.voucherId = v.id " +
           "WHERE v.companyId = :companyId")
    suspend fun getAllAllocationsForCompany(companyId: Int): List<BillAllocation>

    // Helper to calculate total paid against a voucher
    @Query("SELECT SUM(amount) FROM bill_allocations WHERE refVoucherId = :refVoucherId")
    suspend fun getTotalAllocatedAmount(refVoucherId: Int): Long?

    @Query("SELECT b.*, v.isDebit, voucher.date, voucher.id as voucherId, voucher.voucherNumber, voucher.type FROM bill_allocations b " +
           "INNER JOIN voucher_entries v ON b.voucherEntryId = v.id " +
           "INNER JOIN vouchers voucher ON v.voucherId = voucher.id " +
           "WHERE v.ledgerId = :ledgerId")
    suspend fun getAllocationDetailsForLedger(ledgerId: Int): List<BillAllocationDetail>

    @Query("SELECT ba.* FROM bill_allocations ba " +
           "INNER JOIN voucher_entries ve ON ba.voucherEntryId = ve.id " +
           "WHERE ve.voucherId = :voucherId")
    fun getAllocationsForVoucher(voucherId: Int): Flow<List<BillAllocation>>
}
