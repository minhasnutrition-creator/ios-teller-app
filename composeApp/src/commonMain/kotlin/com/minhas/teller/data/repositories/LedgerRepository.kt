package com.minhas.teller.data.repositories

import com.minhas.teller.data.dao.LedgerDao
import com.minhas.teller.data.entities.Ledger
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID

class LedgerRepository(private val ledgerDao: LedgerDao) {

    fun getAllLedgers(companyId: Int): Flow<List<Ledger>> = ledgerDao.getAllLedgers(companyId)

    suspend fun getAllLedgersSync(companyId: Int): List<Ledger> = ledgerDao.getAllLedgersSync(companyId)

    fun getLedger(id: Int): Flow<Ledger> {
        return ledgerDao.getLedgerById(id)
    }

    suspend fun getLedgerById(id: Int): Ledger? {
        return ledgerDao.getLedgerByIdSync(id)
    }

    fun getCashBankLedgers(companyId: Int): Flow<List<Ledger>> {
        return ledgerDao.getCashBankLedgers(companyId)
    }

    suspend fun insert(ledger: Ledger): Long {
        val id = ledgerDao.insert(
            ledger.copy(
                uid = ledger.uid ?: UUID.randomUUID().toString(),
                updatedAt = System.currentTimeMillis()
            )
        )
        com.minhas.teller.utils.CloudAutoBackup.trigger()
        return id
    }

    suspend fun update(ledger: Ledger) {
        ledgerDao.update(
            ledger.copy(
                uid = ledger.uid ?: UUID.randomUUID().toString(),
                updatedAt = System.currentTimeMillis()
            )
        )
        com.minhas.teller.utils.CloudAutoBackup.trigger()
    }

    suspend fun delete(ledger: Ledger) {
        ledgerDao.update(ledger.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
        com.minhas.teller.utils.CloudAutoBackup.trigger()
    }

    fun getClosingBalance(ledgerId: Int, companyId: Int, toDate: Date): Flow<Long?> {
        return ledgerDao.getClosingBalance(ledgerId, companyId, toDate)
    }
}