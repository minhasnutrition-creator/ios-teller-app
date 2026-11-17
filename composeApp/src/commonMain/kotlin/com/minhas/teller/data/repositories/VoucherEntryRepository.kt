package com.minhas.teller.data.repositories

import com.minhas.teller.data.dao.VoucherEntryDao
import com.minhas.teller.data.entities.VoucherEntry

class VoucherEntryRepository(private val voucherEntryDao: VoucherEntryDao) {
    suspend fun getVoucherEntriesForVoucher(voucherId: Int): List<VoucherEntry> {
        return voucherEntryDao.getVoucherEntriesForVoucher(voucherId)
    }

    suspend fun insert(voucherEntry: VoucherEntry): Long {
        return voucherEntryDao.insert(voucherEntry)
    }

    suspend fun insertAll(voucherEntries: List<VoucherEntry>): List<Long> {
        return voucherEntryDao.insertAll(voucherEntries)
    }

    suspend fun update(voucherEntry: VoucherEntry) {
        voucherEntryDao.update(voucherEntry)
    }

    suspend fun delete(voucherEntry: VoucherEntry) {
        voucherEntryDao.delete(voucherEntry)
    }

    suspend fun deleteVoucherEntriesForVoucher(voucherId: Int) {
        voucherEntryDao.deleteVoucherEntriesForVoucher(voucherId)
    }
}