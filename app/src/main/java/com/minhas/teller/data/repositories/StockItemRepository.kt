package com.minhas.teller.data.repositories

import com.minhas.teller.data.dao.StockItemDao
import com.minhas.teller.data.dao.InventoryEntryDao
import com.minhas.teller.data.dao.StockMovement
import com.minhas.teller.data.entities.StockItem
import java.util.Date
import java.util.UUID

import kotlinx.coroutines.flow.Flow

class StockItemRepository(
    private val stockItemDao: StockItemDao,
    private val inventoryEntryDao: InventoryEntryDao? = null 
) {

    fun getAllStockItemsFlow(companyId: Int): Flow<List<StockItem>> {
        return stockItemDao.getAllStockItemsFlow(companyId)
    }

    suspend fun getAllStockItems(companyId: Int): List<StockItem> {
        return stockItemDao.getAllStockItems(companyId)
    }

    suspend fun getStockItemById(id: Int): StockItem? {
        return stockItemDao.getStockItemById(id)
    }

    suspend fun getByBarcode(companyId: Int, barcode: String): StockItem? {
        return stockItemDao.getStockItemByBarcode(companyId, barcode)
    }
    
    suspend fun getStockMovements(startDate: Date, endDate: Date, companyId: Int): List<StockMovement> {
        return inventoryEntryDao?.getStockMovements(startDate.time, endDate.time, companyId) ?: emptyList()
    }

    suspend fun insert(stockItem: StockItem): Long {
        val id = stockItemDao.insert(
            stockItem.copy(
                uid = stockItem.uid ?: UUID.randomUUID().toString(),
                updatedAt = System.currentTimeMillis()
            )
        )
        com.minhas.teller.utils.CloudAutoBackup.trigger()
        return id
    }

    suspend fun update(stockItem: StockItem) {
        stockItemDao.update(
            stockItem.copy(
                uid = stockItem.uid ?: UUID.randomUUID().toString(),
                updatedAt = System.currentTimeMillis()
            )
        )
        com.minhas.teller.utils.CloudAutoBackup.trigger()
    }

    /**
     * Update a stock item and back-fill its HSN code onto any existing inventory
     * entries that are still missing one. This is how a "quick fix" at the item
     * level actually clears the missing-HSN audit warning on already-saved vouchers.
     */
    suspend fun updateWithHsnBackfill(stockItem: StockItem) {
        val stamped = stockItem.copy(
            uid = stockItem.uid ?: UUID.randomUUID().toString(),
            updatedAt = System.currentTimeMillis()
        )
        stockItemDao.update(stamped)
        val hsn = stockItem.hsnCode
        if (!hsn.isNullOrBlank()) {
            inventoryEntryDao?.backfillHsnForStockItem(stockItem.id, hsn)
        }
        com.minhas.teller.utils.CloudAutoBackup.trigger()
    }

    suspend fun delete(stockItem: StockItem) {
        stockItemDao.update(stockItem.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
        com.minhas.teller.utils.CloudAutoBackup.trigger()
    }
}
