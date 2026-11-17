package com.minhas.teller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.minhas.teller.data.entities.InventoryEntry

import java.util.Date

data class StockMovement(
    val itemId: Int,
    val inwardQty: Double,
    val outwardQty: Double
)

@Dao
interface InventoryEntryDao {
    @Query("SELECT * FROM inventory_entries WHERE voucherId = :voucherId ORDER BY id ASC")
    suspend fun getInventoryEntriesForVoucher(voucherId: Int): List<InventoryEntry>

    @Query("""
        SELECT
            ie.stockItemId as itemId,
            SUM(CASE
                WHEN v.type = 'Purchase' OR v.type = 'Sales Return' OR v.type = 'Credit Note' THEN ie.quantity
                WHEN v.type = 'Journal' AND ie.quantity > 0 THEN ie.quantity
                ELSE 0 END) as inwardQty,
            SUM(CASE
                WHEN v.type = 'Sales' OR v.type = 'Purchase Return' OR v.type = 'Debit Note' THEN ie.quantity
                WHEN v.type = 'Journal' AND ie.quantity < 0 THEN -ie.quantity
                ELSE 0 END) as outwardQty
        FROM inventory_entries ie
        INNER JOIN vouchers v ON ie.voucherId = v.id
        WHERE v.date BETWEEN :startDate AND :endDate
          AND v.companyId = :companyId
        GROUP BY ie.stockItemId
    """)
    suspend fun getStockMovements(startDate: Long, endDate: Long, companyId: Int): List<StockMovement>

    @Insert
    suspend fun insert(inventoryEntry: InventoryEntry): Long

    @Insert
    suspend fun insertAll(inventoryEntries: List<InventoryEntry>): List<Long>

    @Update
    suspend fun update(inventoryEntry: InventoryEntry)

    @Delete
    suspend fun delete(inventoryEntry: InventoryEntry)

    @Query("DELETE FROM inventory_entries WHERE voucherId = :voucherId")
    suspend fun deleteInventoryEntriesForVoucher(voucherId: Int)

    @Query("DELETE FROM inventory_entries WHERE voucherId IN (:voucherIds)")
    suspend fun deleteInventoryEntriesForVouchers(voucherIds: List<Int>): Int

    @Query("UPDATE inventory_entries SET hsnCode = :hsnCode WHERE stockItemId = :stockItemId AND (hsnCode IS NULL OR hsnCode = '')")
    suspend fun backfillHsnForStockItem(stockItemId: Int, hsnCode: String): Int
}
