package com.minhas.teller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.minhas.teller.data.entities.StockItem

import kotlinx.coroutines.flow.Flow

@Dao
interface StockItemDao {
    @Query("SELECT * FROM stock_items WHERE companyId = :companyId AND isDeleted = 0 ORDER BY name ASC")
    fun getAllStockItemsFlow(companyId: Int): Flow<List<StockItem>>

    @Query("SELECT * FROM stock_items WHERE companyId = :companyId AND isDeleted = 0 ORDER BY name ASC")
    suspend fun getAllStockItems(companyId: Int): List<StockItem>

    @Query("SELECT * FROM stock_items WHERE companyId = :companyId")
    suspend fun getAllStockItemsForSync(companyId: Int): List<StockItem>

    @Query("SELECT * FROM stock_items WHERE id = :stockItemId")
    suspend fun getStockItemById(stockItemId: Int): StockItem?

    @Query("SELECT * FROM stock_items WHERE companyId = :companyId AND name = :name LIMIT 1")
    suspend fun getStockItemByName(companyId: Int, name: String): StockItem?

    @Query("SELECT * FROM stock_items WHERE companyId = :companyId AND barcode = :barcode AND isDeleted = 0 LIMIT 1")
    suspend fun getStockItemByBarcode(companyId: Int, barcode: String): StockItem?

    @Query("SELECT * FROM stock_items WHERE uid = :uid LIMIT 1")
    suspend fun getStockItemByUid(uid: String): StockItem?

    @Insert
    suspend fun insert(stockItem: StockItem): Long

    @Update
    suspend fun update(stockItem: StockItem)

    @Delete
    suspend fun delete(stockItem: StockItem)

    @Query("""
        DELETE FROM stock_items
        WHERE companyId = :companyId AND (
            name LIKE '%TextBox%' OR
            name LIKE '%CheckBox%' OR
            name LIKE '%ItemType%' OR
            name LIKE '%ListBox%' OR
            name LIKE '%ComboBox%' OR
            name LIKE '%StoreData%' OR
            name LIKE '%JetOLEDB%' OR
            name LIKE 'MSys%' OR
            name LIKE '%Standard Jet%' OR
            name LIKE '%Package%' OR
            TRIM(name) = ''
        ) AND id NOT IN (SELECT DISTINCT stockItemId FROM inventory_entries)
    """)
    suspend fun purgeBogusStockItems(companyId: Int): Int
}
