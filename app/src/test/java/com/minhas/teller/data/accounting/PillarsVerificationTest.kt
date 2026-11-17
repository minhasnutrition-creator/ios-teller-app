package com.minhas.teller.data.accounting

import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.gst.Gstr2bInvoice
import com.minhas.teller.data.gst.Gstr2bMatchStatus
import com.minhas.teller.data.gst.Gstr2bReconciler
import com.minhas.teller.utils.AutonomousBookkeeper
import com.minhas.teller.utils.ThermalPrinterHelper
import com.minhas.teller.utils.rupeesToPaise
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Date

class PillarsVerificationTest {

    @Test
    fun testUniversalBankParser_parsesCsvCorrectly() {
        val sampleCsv = """
            Date,Description,Withdrawal,Deposit,Balance
            15/10/2026,UPI-987654-ROHIT,,5000.00,105000.00
            16/10/2026,NEFT-OFFICE-RENT,15000.00,,90000.00
        """.trimIndent()

        val parsed = UniversalBankParser.parseCsv(ByteArrayInputStream(sampleCsv.toByteArray()))
        assertEquals(2, parsed.size)

        // Txn 1: Deposit of ₹5000
        assertEquals(5000.0, parsed[0].amount, 0.001)
        assertTrue(parsed[0].isDeposit)
        assertTrue(parsed[0].description.contains("ROHIT"))

        // Txn 2: Withdrawal of ₹15000
        assertEquals(15000.0, parsed[1].amount, 0.001)
        assertFalse(parsed[1].isDeposit)
        assertTrue(parsed[1].description.contains("RENT"))
    }

    @Test
    fun testAutomatedReconciler_exactMatch() {
        val now = Date()
        val bankTx = listOf(
            ParsedBankTransaction(date = now, description = "UPI Inflow", amount = 2500.0, isDeposit = true)
        )

        val voucher = VoucherWithAllEntries(
            voucher = Voucher(id = 1, voucherNumber = "REC-01", date = now, type = "Receipt", companyId = 1),
            voucherEntries = listOf(
                VoucherEntry(id = 1, voucherId = 1, ledgerId = 10, amount = 2500.0.rupeesToPaise(), isDebit = true), // Bank debit
                VoucherEntry(id = 2, voucherId = 1, ledgerId = 20, amount = 2500.0.rupeesToPaise(), isDebit = false)
            ),
            inventoryEntries = emptyList()
        )

        val report = AutomatedReconciler.reconcile(
            bankTransactions = bankTx,
            bookVouchers = listOf(voucher),
            bankLedgerId = 10
        )

        assertEquals(1, report.matched.size)
        assertEquals(ReconciliationMatchLevel.EXACT, report.matched.first().matchLevel)
        assertEquals(0, report.missingInBooks.size)
        assertEquals(0, report.unclearedInBank.size)
    }

    @Test
    fun testGstr2bReconciliation_flagsMissingInBooksAndAtRiskItc() {
        val twoBInvoices = listOf(
            Gstr2bInvoice(
                gstin = "07AAAAA0000A1Z5",
                tradeName = "Acme Supplies",
                invoiceNumber = "INV-101",
                invoiceDate = Date(),
                taxableValue = 10000.0,
                cgst = 900.0,
                sgst = 900.0
            )
        )

        // User books have a purchase from another vendor, but NOT INV-101
        val bookPurchase = VoucherWithAllEntries(
            voucher = Voucher(id = 1, voucherNumber = "LOCAL-999", date = Date(), type = "Purchase", companyId = 1),
            voucherEntries = listOf(
                VoucherEntry(id = 1, voucherId = 1, ledgerId = 1, amount = 5900.0.rupeesToPaise(), isDebit = true),
                VoucherEntry(id = 2, voucherId = 1, ledgerId = 2, amount = 5900.0.rupeesToPaise(), isDebit = false)
            ),
            inventoryEntries = listOf(
                InventoryEntry(id = 1, voucherId = 1, stockItemId = 1, quantity = 5.0, rate = 1000.0, amount = 5000.0.rupeesToPaise(), taxAmount = 900.0.rupeesToPaise())
            )
        )

        val summary = Gstr2bReconciler.reconcile(
            twoBInvoices = twoBInvoices,
            purchaseVouchers = listOf(bookPurchase),
            supplierLedgers = emptyList()
        )

        // INV-101 should be MISSING_IN_BOOKS (₹1,800 unclaimed ITC available!)
        assertTrue(summary.results.any { it.status == Gstr2bMatchStatus.MISSING_IN_BOOKS })
        assertEquals(1800.0, summary.missingInBooksItc, 0.01)

        // LOCAL-999 should be MISSING_IN_2B (supplier hasn't filed GSTR-1, ITC at risk!)
        assertTrue(summary.results.any { it.status == Gstr2bMatchStatus.MISSING_IN_2B })
        assertEquals(900.0, summary.atRiskItc, 0.01)
    }

    @Test
    fun testThermalPrinterHelper_buildsEscPosReceiptBytes() {
        val company = Company(
            id = 1,
            name = "SuperMart Retail",
            mailingName = "SuperMart Retail",
            address = "Connaught Place, New Delhi",
            state = "Delhi",
            country = "India",
            pincode = "110001",
            phoneNumber = "9876543210",
            email = "contact@supermart.com",
            gstin = "07AAAAA0000A1Z5",
            financialYearFrom = 1704067200000L,
            upiId = "supermart@okicici"
        )

        val stockItem = StockItem(id = 1, name = "Basmati Rice 5kg", unitId = 1, openingStock = 10.0, rate = 450.0, companyId = 1)

        val voucher = VoucherWithAllEntries(
            voucher = Voucher(id = 1, voucherNumber = "BILL-501", date = Date(), type = "Sales", companyId = 1),
            voucherEntries = listOf(
                VoucherEntry(id = 1, voucherId = 1, ledgerId = 1, amount = 450.0.rupeesToPaise(), isDebit = true),
                VoucherEntry(id = 2, voucherId = 1, ledgerId = 2, amount = 450.0.rupeesToPaise(), isDebit = false)
            ),
            inventoryEntries = listOf(
                InventoryEntry(id = 1, voucherId = 1, stockItemId = stockItem.id, quantity = 1.0, rate = 450.0, amount = 450.0.rupeesToPaise(), totalAmount = 450.0.rupeesToPaise())
            )
        )

        val bytes = ThermalPrinterHelper.buildReceiptBytes(
            company = company,
            voucherWithAllEntries = voucher,
            stockItems = listOf(stockItem),
            customerName = "Walk-in Customer",
            paperWidth = ThermalPrinterHelper.PaperWidth.MM_58
        )

        assertTrue("Generated byte array should not be empty", bytes.isNotEmpty())
        val asString = String(bytes)
        assertTrue(asString.contains("SuperMart Retail"))
        assertTrue(asString.contains("Basmati Rice"))
        assertTrue(asString.contains("450.00"))
    }

    @Test
    fun testAutonomousBookkeeper_detectsNegativeStockAndHealth() {
        val stockItem = StockItem(id = 1, name = "Copper Pipe", unitId = 1, openingStock = 2.0, rate = 100.0, companyId = 1)

        // Sale of 5 units when only 2 were in opening stock and 0 purchased -> negative stock!
        val overSale = VoucherWithAllEntries(
            voucher = Voucher(id = 1, voucherNumber = "SAL-01", date = Date(), type = "Sales", companyId = 1),
            voucherEntries = listOf(
                VoucherEntry(id = 1, voucherId = 1, ledgerId = 1, amount = 500.0.rupeesToPaise(), isDebit = true),
                VoucherEntry(id = 2, voucherId = 1, ledgerId = 2, amount = 500.0.rupeesToPaise(), isDebit = false)
            ),
            inventoryEntries = listOf(
                InventoryEntry(id = 1, voucherId = 1, stockItemId = stockItem.id, quantity = 5.0, rate = 100.0, amount = 500.0.rupeesToPaise())
            )
        )

        val anomalies = AutonomousBookkeeper.scanFinancialHealth(
            vouchers = listOf(overSale),
            ledgers = emptyList(),
            stockItems = listOf(stockItem)
        )

        assertTrue(anomalies.any { it.title.contains("Negative Stock") })
    }
}
