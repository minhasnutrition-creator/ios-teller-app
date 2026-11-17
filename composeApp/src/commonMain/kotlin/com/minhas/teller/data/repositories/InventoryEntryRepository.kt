package com.minhas.teller.data.repositories

import com.minhas.teller.data.dao.InventoryEntryDao
import com.minhas.teller.data.entities.InventoryEntry

class InventoryEntryRepository(private val inventoryEntryDao: InventoryEntryDao) {
    suspend fun getInventoryEntriesForVoucher(voucherId: Int): List<InventoryEntry> {
        return inventoryEntryDao.getInventoryEntriesForVoucher(voucherId)
    }

    suspend fun insert(inventoryEntry: InventoryEntry): Long {
        return inventoryEntryDao.insert(inventoryEntry)
    }

    suspend fun insertAll(inventoryEntries: List<InventoryEntry>): List<Long> {
        return inventoryEntryDao.insertAll(inventoryEntries)
    }

    suspend fun update(inventoryEntry: InventoryEntry) {
        inventoryEntryDao.update(inventoryEntry)
    }

    suspend fun delete(inventoryEntry: InventoryEntry) {
        inventoryEntryDao.delete(inventoryEntry)
    }

    suspend fun deleteInventoryEntriesForVoucher(voucherId: Int) {
        inventoryEntryDao.deleteInventoryEntriesForVoucher(voucherId)
    }
}