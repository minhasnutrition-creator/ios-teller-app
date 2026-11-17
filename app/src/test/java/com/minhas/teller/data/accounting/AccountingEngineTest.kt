package com.minhas.teller.data.accounting

import com.minhas.teller.data.entities.AccountGroup
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.rupeesToPaise
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class AccountingEngineTest {

    @Test
    fun testDoubleEntryValidation_balancedVoucher_returnsTrue() {
        val entries = listOf(
            VoucherEntry(id = 1, voucherId = 1, ledgerId = 1, amount = 5000.0.rupeesToPaise(), isDebit = true),
            VoucherEntry(id = 2, voucherId = 1, ledgerId = 2, amount = 5000.0.rupeesToPaise(), isDebit = false)
        )
        val result = AccountingEngine.validateDoubleEntry(entries)
        assertTrue(result.isValid)
        assertEquals(5000.0, result.totalDebit, 0.001)
        assertEquals(5000.0, result.totalCredit, 0.001)
        assertEquals(0.0, result.difference, 0.001)
    }

    @Test
    fun testDoubleEntryValidation_unbalancedVoucher_returnsFalse() {
        val entries = listOf(
            VoucherEntry(id = 1, voucherId = 1, ledgerId = 1, amount = 5000.0.rupeesToPaise(), isDebit = true),
            VoucherEntry(id = 2, voucherId = 1, ledgerId = 2, amount = 4500.0.rupeesToPaise(), isDebit = false)
        )
        val result = AccountingEngine.validateDoubleEntry(entries)
        assertFalse(result.isValid)
        assertEquals(500.0, result.difference, 0.001)
    }

    @Test
    fun testAccountHierarchy_resolvesCustomHierarchicalGroups() {
        val userGroups = listOf(
            AccountGroup(id = 1, name = "Delivery Vans", parentGroup = "Vehicles", companyId = 1),
            AccountGroup(id = 2, name = "Vehicles", parentGroup = "Fixed Assets", companyId = 1),
            AccountGroup(id = 3, name = "HDFC Current A/c", parentGroup = "Bank Accounts", companyId = 1),
            AccountGroup(id = 4, name = "Staff Welfare", parentGroup = "Indirect Expenses", companyId = 1)
        )

        // Nested sub-group "Delivery Vans" -> "Vehicles" -> "Fixed Assets" -> ASSET
        val vanType = AccountHierarchy.classifyGroup("Delivery Vans", userGroups)
        assertEquals(PrimaryAccountType.ASSET, vanType)

        // Sub-group "HDFC Current A/c" -> "Bank Accounts" -> ASSET
        val bankType = AccountHierarchy.classifyGroup("HDFC Current A/c", userGroups)
        assertEquals(PrimaryAccountType.ASSET, bankType)

        // Sub-group "Staff Welfare" -> "Indirect Expenses" -> EXPENSE
        val expType = AccountHierarchy.classifyGroup("Staff Welfare", userGroups)
        assertEquals(PrimaryAccountType.EXPENSE, expType)
    }

    @Test
    fun testBalanceSheet_equationBalancesWithNetProfitAndStock() {
        // Initial setup:
        // Capital = 100,000 (Credit: -100,000)
        // Bank = 100,000 (Debit: +100,000)
        val capitalLedger = Ledger(id = 1, name = "Owner Capital", group = "Capital Account", openingBalance = (-100000.0).rupeesToPaise(), companyId = 1)
        val bankLedger = Ledger(id = 2, name = "Main Bank", group = "Bank Accounts", openingBalance = 100000.0.rupeesToPaise(), companyId = 1)
        val salesLedger = Ledger(id = 3, name = "Sales", group = "Sales Accounts", openingBalance = 0L, companyId = 1)
        val customerLedger = Ledger(id = 4, name = "Customer Rohit", group = "Sundry Debtors", openingBalance = 0L, companyId = 1)
        val purchaseLedger = Ledger(id = 5, name = "Purchases", group = "Purchase Accounts", openingBalance = 0L, companyId = 1)

        val stockItem = StockItem(id = 1, name = "Widget A", unitId = 1, openingStock = 0.0, rate = 0.0, companyId = 1)

        val ledgers = listOf(capitalLedger, bankLedger, salesLedger, customerLedger, purchaseLedger)
        val stockItems = listOf(stockItem)

        // Transaction 1: Buy 100 items at ₹200 each = ₹20,000 paid from Bank
        val purchaseVoucher = VoucherWithAllEntries(
            voucher = Voucher(id = 1, voucherNumber = "PUR-01", date = Date(1000L), type = "Purchase", companyId = 1),
            voucherEntries = listOf(
                VoucherEntry(id = 1, voucherId = 1, ledgerId = purchaseLedger.id, amount = 20000.0.rupeesToPaise(), isDebit = true),
                VoucherEntry(id = 2, voucherId = 1, ledgerId = bankLedger.id, amount = 20000.0.rupeesToPaise(), isDebit = false)
            ),
            inventoryEntries = listOf(
                InventoryEntry(id = 1, voucherId = 1, stockItemId = stockItem.id, quantity = 100.0, rate = 200.0, amount = 20000.0.rupeesToPaise(), taxableAmount = 20000.0.rupeesToPaise())
            )
        )

        // Transaction 2: Sell 40 items at ₹350 each = ₹14,000 to Customer Rohit (receivable)
        // Cost of Goods Sold = 40 * ₹200 = ₹8,000
        // Net Profit = ₹14,000 - ₹8,000 = ₹6,000
        // Remaining Stock = 60 items * ₹200 = ₹12,000
        val salesVoucher = VoucherWithAllEntries(
            voucher = Voucher(id = 2, voucherNumber = "SAL-01", date = Date(2000L), type = "Sales", companyId = 1),
            voucherEntries = listOf(
                VoucherEntry(id = 3, voucherId = 2, ledgerId = customerLedger.id, amount = 14000.0.rupeesToPaise(), isDebit = true),
                VoucherEntry(id = 4, voucherId = 2, ledgerId = salesLedger.id, amount = 14000.0.rupeesToPaise(), isDebit = false)
            ),
            inventoryEntries = listOf(
                InventoryEntry(id = 2, voucherId = 2, stockItemId = stockItem.id, quantity = 40.0, rate = 350.0, amount = 14000.0.rupeesToPaise(), taxableAmount = 14000.0.rupeesToPaise())
            )
        )

        val vouchers = listOf(purchaseVoucher, salesVoucher)

        // Calculate Balance Sheet
        val bs = AccountingEngine.calculateBalanceSheet(
            ledgers = ledgers,
            vouchersUpToDate = vouchers,
            stockItems = stockItems,
            asOfDate = Date(3000L)
        )

        // Expected Assets:
        // Bank: 100,000 - 20,000 = 80,000
        // Sundry Debtors: 14,000
        // Stock-in-hand: 12,000
        // Total Assets = 80,000 + 14,000 + 12,000 = 106,000
        assertEquals(80000.0, bs.assets["Bank Accounts"] ?: 0.0, 0.01)
        assertEquals(14000.0, bs.assets["Sundry Debtors"] ?: 0.0, 0.01)
        assertEquals(12000.0, bs.assets["Stock-in-hand"] ?: 0.0, 0.01)
        assertEquals(106000.0, bs.totalAssets, 0.01)

        // Expected Liabilities & Capital:
        // Capital: 100,000
        // Profit & Loss A/c (Net Profit): 6,000
        // Total Liabilities & Capital = 106,000
        assertEquals(100000.0, bs.capital["Capital Account"] ?: 0.0, 0.01)
        assertEquals(6000.0, bs.capital["Profit & Loss A/c (Net Profit)"] ?: 0.0, 0.01)
        assertEquals(106000.0, bs.totalLiabilitiesAndCapital, 0.01)

        // Verify the Balance Sheet equation is balanced!
        assertTrue("Balance sheet must balance: Assets (${bs.totalAssets}) == Liab+Cap (${bs.totalLiabilitiesAndCapital})", bs.isBalanced)
    }
}
