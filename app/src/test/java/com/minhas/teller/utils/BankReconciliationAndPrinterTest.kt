package com.minhas.teller.utils

import com.minhas.teller.data.accounting.*
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.rupeesToPaise
import com.minhas.teller.utils.paiseToRupees
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BankReconciliationAndPrinterTest {

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    // ────────────────────────────────────────────────────────────────────────
    // 1. THERMAL PRINTER UTILITY TESTS
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun testThermalPrinter_EscPosByteGeneration_58mm() {
        val company = Company(
            id = 1,
            name = "Ramesh Kirana Store",
            mailingName = "Ramesh Kirana Store",
            address = "Main Bazar, Jaipur",
            state = "Rajasthan",
            country = "India",
            pincode = "302001",
            phoneNumber = "9829012345",
            email = "ramesh@kirana.com",
            gstin = "08AABCR1234F1Z5",
            financialYearFrom = 1704067200000L,
            upiId = "rameshkirana@upi"
        )

        val stockItem1 = StockItem(id = 1, name = "Aashirvaad Atta 10kg", unitId = 1, openingStock = 50.0, rate = 420.0, companyId = 1)
        val stockItem2 = StockItem(id = 2, name = "Tata Salt 1kg", unitId = 1, openingStock = 100.0, rate = 28.0, companyId = 1)

        val voucher = VoucherWithAllEntries(
            voucher = Voucher(id = 101, voucherNumber = "BILL-1001", date = Date(), type = "Sales", companyId = 1),
            voucherEntries = listOf(
                VoucherEntry(id = 1, voucherId = 101, ledgerId = 1, amount = 448.0.rupeesToPaise(), isDebit = true),
                VoucherEntry(id = 2, voucherId = 101, ledgerId = 2, amount = 448.0.rupeesToPaise(), isDebit = false)
            ),
            inventoryEntries = listOf(
                InventoryEntry(id = 1, voucherId = 101, stockItemId = 1, quantity = 1.0, rate = 420.0, amount = 420.0.rupeesToPaise(), totalAmount = 420.0.rupeesToPaise()),
                InventoryEntry(id = 2, voucherId = 101, stockItemId = 2, quantity = 1.0, rate = 28.0, amount = 28.0.rupeesToPaise(), totalAmount = 28.0.rupeesToPaise())
            )
        )

        val bytes = ThermalPrinterHelper.buildReceiptBytes(
            company = company,
            voucherWithAllEntries = voucher,
            stockItems = listOf(stockItem1, stockItem2),
            customerName = "Sunil Kumar",
            paperWidth = ThermalPrinterHelper.PaperWidth.MM_58
        )

        assertNotNull(bytes)
        assertTrue("ESC/POS byte output must be non-empty", bytes.size > 50)

        val text = String(bytes)
        assertTrue(text.contains("Ramesh Kirana Store"))
        assertTrue(text.contains("BILL-1001"))
        assertTrue(text.contains("Aashirvaad Atta 10kg"))
        assertTrue(text.contains("Tata Salt 1kg"))
        assertTrue(text.contains("Sunil Kumar"))
        assertTrue(text.contains("Rs. 448.00")) // Clean rupee currency formatting without '?'
    }

    @Test
    fun testThermalPrinter_EscPosByteGeneration_80mm() {
        val company = Company(
            id = 2,
            name = "Apex Electronics World",
            mailingName = "Apex Electronics World",
            address = "MG Road, Bengaluru",
            state = "Karnataka",
            country = "India",
            pincode = "560001",
            phoneNumber = "9880011223",
            email = "contact@apex.com",
            gstin = "29AAAAA0000A1Z5",
            financialYearFrom = 1704067200000L
        )

        val voucher = VoucherWithAllEntries(
            voucher = Voucher(id = 202, voucherNumber = "POS-8899", date = Date(), type = "Sales", companyId = 2),
            voucherEntries = listOf(
                VoucherEntry(id = 1, voucherId = 202, ledgerId = 1, amount = 12500.0.rupeesToPaise(), isDebit = true),
                VoucherEntry(id = 2, voucherId = 202, ledgerId = 2, amount = 12500.0.rupeesToPaise(), isDebit = false)
            ),
            inventoryEntries = emptyList()
        )

        val bytes = ThermalPrinterHelper.buildReceiptBytes(
            company = company,
            voucherWithAllEntries = voucher,
            stockItems = emptyList(),
            customerName = "Retail Customer",
            paperWidth = ThermalPrinterHelper.PaperWidth.MM_80
        )

        val text = String(bytes)
        assertTrue(text.contains("Apex Electronics World"))
        assertTrue(text.contains("POS-8899"))
        assertTrue(text.contains("12500.00"))
    }

    @Test
    fun testThermalPrinter_CleanPosText_ReplacesCurrencyAndSpecialChars() {
        val input = "Total: ₹1,500.00 “Special Discount” – Paid"
        val cleaned = ThermalPrinterHelper.cleanPosText(input)

        assertFalse("Must not contain Unicode rupee symbol", cleaned.contains("₹"))
        assertTrue("Must replace rupee with Rs.", cleaned.contains("Rs. 1,500.00"))
        assertFalse("Must not contain curly quotes", cleaned.contains("“") || cleaned.contains("”"))
        assertTrue("Must use ASCII quotes", cleaned.contains("\"Special Discount\""))
        assertFalse("Must not contain en-dash", cleaned.contains("–"))
        assertTrue("Must use standard hyphen", cleaned.contains("-"))
    }

    @Test
    fun testThermalPrinter_TestReceiptGeneration() {
        val testBytes58 = ThermalPrinterHelper.buildTestReceiptBytes("POS-58 Printer", ThermalPrinterHelper.PaperWidth.MM_58)
        assertTrue(testBytes58.isNotEmpty())
        val text58 = String(testBytes58)
        assertTrue(text58.contains("TELLER POS TEST"))
        assertTrue(text58.contains("Hardware & Connection OK"))
        assertTrue(text58.contains("58mm"))

        val testBytes80 = ThermalPrinterHelper.buildTestReceiptBytes("POS-80 Printer", ThermalPrinterHelper.PaperWidth.MM_80)
        assertTrue(testBytes80.isNotEmpty())
        val text80 = String(testBytes80)
        assertTrue(text80.contains("80mm"))
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. BANK STATEMENT PARSING TESTS (INDIAN & INTERNATIONAL BANKS)
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun testUniversalBankParser_SbiNetbankingCsv() {
        val sbiCsv = """
            Txn Date,Value Date,Description,Ref No./Cheque No.,Branch Code,Debit,Credit,Balance
            01/10/2026,01/10/2026,BY TRANSFER UPI/427618291024/VIJAY TRADERS,UPI427618291024,999,,25000.00,125000.00
            03/10/2026,03/10/2026,TO TRANSFER NEFT/AXISN26090123/RENT,AXISN26090123,999,18000.00,,107000.00
            05/10/2026,05/10/2026,CHQ PAID-000124-ELECTRICITY,000124,999,3450.50,,103549.50
        """.trimIndent()

        val txns = UniversalBankParser.parseCsv(ByteArrayInputStream(sbiCsv.toByteArray()))
        assertEquals(3, txns.size)

        // 1. UPI Inflow
        assertEquals(25000.0, txns[0].amount, 0.001)
        assertTrue(txns[0].isDeposit)
        assertEquals("UPI427618291024", txns[0].referenceNumber)

        // 2. NEFT Outflow
        assertEquals(18000.0, txns[1].amount, 0.001)
        assertFalse(txns[1].isDeposit)
        assertEquals("AXISN26090123", txns[1].referenceNumber)

        // 3. Cheque
        assertEquals(3450.50, txns[2].amount, 0.001)
        assertFalse(txns[2].isDeposit)
        assertEquals("000124", txns[2].referenceNumber)
    }

    @Test
    fun testUniversalBankParser_HdfcBankCsv() {
        val hdfcCsv = """
            Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
            10/10/2026,IMPS-P2A-629102847291-KAPOOR TEXTILES,629102847291,10/10/2026,,45000.00,245000.00
            12/10/2026,POS-PURCHASE-HP FUEL,998822,12/10/2026,2000.00,,243000.00
        """.trimIndent()

        val txns = UniversalBankParser.parseCsv(ByteArrayInputStream(hdfcCsv.toByteArray()))
        assertEquals(2, txns.size)

        assertEquals(45000.0, txns[0].amount, 0.001)
        assertTrue(txns[0].isDeposit)
        assertEquals("629102847291", txns[0].referenceNumber)

        assertEquals(2000.0, txns[1].amount, 0.001)
        assertFalse(txns[1].isDeposit)
    }

    @Test
    fun testUniversalBankParser_IciciBankCsv() {
        val iciciCsv = """
            Transaction Date,Value Date,Cheque Number,Transaction Remarks,Withdrawal Amount (INR ),Deposit Amount (INR ),Balance (INR )
            14/10/2026,14/10/2026,000551,SALARY OCT 2026,50000.00,,150000.00
            15/10/2026,15/10/2026,,CASH DEPOSIT BRANCH,,100000.00,250000.00
        """.trimIndent()

        val txns = UniversalBankParser.parseCsv(ByteArrayInputStream(iciciCsv.toByteArray()))
        assertEquals(2, txns.size)

        assertEquals(50000.0, txns[0].amount, 0.001)
        assertFalse(txns[0].isDeposit)
        assertEquals("000551", txns[0].referenceNumber)

        assertEquals(100000.0, txns[1].amount, 0.001)
        assertTrue(txns[1].isDeposit)
    }

    @Test
    fun testUniversalBankParser_AxisBankCsv() {
        val axisCsv = """
            Tran Date,CHQNO,PARTICULARS,DR,CR,BAL
            11-10-2026,112233,GST PAYMENT GOVT,12450.00,,88000.00
            12-10-2026,,PAYMENT RECEIVED FROM ABC PVT LTD,,35000.00,123000.00
        """.trimIndent()

        val txns = UniversalBankParser.parseCsv(ByteArrayInputStream(axisCsv.toByteArray()))
        assertEquals(2, txns.size)

        assertEquals(12450.0, txns[0].amount, 0.001)
        assertFalse(txns[0].isDeposit)

        assertEquals(35000.0, txns[1].amount, 0.001)
        assertTrue(txns[1].isDeposit)
    }

    @Test
    fun testUniversalBankParser_TabSeparatedAndSemicolonDelimiters() {
        // Tab-separated (e.g. Netbanking copy-paste or TSV)
        val tsvData = "Date\tDescription\tWithdrawal\tDeposit\tBalance\n" +
            "01/10/2026\tClient Advance\t\t15000.00\t50000.00\n" +
            "02/10/2026\tOffice Supplies\t1200.00\t\t48800.00"

        val tsvTxns = UniversalBankParser.parseCsv(ByteArrayInputStream(tsvData.toByteArray()))
        assertEquals(2, tsvTxns.size)
        assertEquals(15000.0, tsvTxns[0].amount, 0.001)
        assertTrue(tsvTxns[0].isDeposit)
        assertEquals(1200.0, tsvTxns[1].amount, 0.001)
        assertFalse(tsvTxns[1].isDeposit)

        // Semicolon-delimited
        val semiData = "Date;Particulars;Debit;Credit;Balance\n" +
            "05/10/2026;Vendor Payment;5500.00;;20000.00"
        val semiTxns = UniversalBankParser.parseCsv(ByteArrayInputStream(semiData.toByteArray()))
        assertEquals(1, semiTxns.size)
        assertEquals(5500.0, semiTxns[0].amount, 0.001)
        assertFalse(semiTxns[0].isDeposit)
    }

    @Test
    fun testUniversalBankParser_IndianNumberCommasAndCurrencySymbols() {
        val rawCsv = """
            Date,Description,Amount
            01/10/2026,Sales Settlement,₹1,50,000.00 Cr
            02/10/2026,Loan EMI,₹25,000.50 Dr
            03/10/2026,Bank Charges,(500.00)
        """.trimIndent()

        val txns = UniversalBankParser.parseCsv(ByteArrayInputStream(rawCsv.toByteArray()))
        assertEquals(3, txns.size)

        assertEquals(150000.0, txns[0].amount, 0.01)
        assertTrue(txns[0].isDeposit)

        assertEquals(25000.50, txns[1].amount, 0.01)
        assertFalse(txns[1].isDeposit)

        assertEquals(500.0, txns[2].amount, 0.01)
        assertFalse(txns[2].isDeposit) // Parentheses indicate negative/withdrawal
    }

    // ────────────────────────────────────────────────────────────────────────
    // 3. 4-TIER AUTOMATED RECONCILIATION TESTS
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun testAutomatedReconciler_Tier1_ExactMatches() {
        val date1 = sdf.parse("05/10/2026")!!
        val date2 = sdf.parse("06/10/2026")!! // Within 1 day buffer

        val bankTx = listOf(
            ParsedBankTransaction(date = date2, description = "UPI From Customer A", amount = 15000.0, isDeposit = true),
            ParsedBankTransaction(date = date1, description = "NEFT To Supplier B", amount = 8500.0, isDeposit = false)
        )

        val v1 = VoucherWithAllEntries(
            voucher = Voucher(id = 1, voucherNumber = "REC-01", date = date1, type = "Receipt", companyId = 1),
            voucherEntries = listOf(
                VoucherEntry(id = 1, voucherId = 1, ledgerId = 10, amount = 15000.0.rupeesToPaise(), isDebit = true), // Bank debit (Deposit)
                VoucherEntry(id = 2, voucherId = 1, ledgerId = 20, amount = 15000.0.rupeesToPaise(), isDebit = false)
            ),
            inventoryEntries = emptyList()
        )

        val v2 = VoucherWithAllEntries(
            voucher = Voucher(id = 2, voucherNumber = "PAY-01", date = date1, type = "Payment", companyId = 1),
            voucherEntries = listOf(
                VoucherEntry(id = 3, voucherId = 2, ledgerId = 30, amount = 8500.0.rupeesToPaise(), isDebit = true),
                VoucherEntry(id = 4, voucherId = 2, ledgerId = 10, amount = 8500.0.rupeesToPaise(), isDebit = false) // Bank credit (Withdrawal)
            ),
            inventoryEntries = emptyList()
        )

        val report = AutomatedReconciler.reconcile(
            bankTransactions = bankTx,
            bookVouchers = listOf(v1, v2),
            bankLedgerId = 10
        )

        assertEquals("Both transactions should match exactly", 2, report.matched.size)
        assertTrue(report.matched.all { it.matchLevel == ReconciliationMatchLevel.EXACT })
        assertEquals(0, report.missingInBooks.size)
        assertEquals(0, report.unclearedInBank.size)
        assertEquals(0.0, report.reconciledDifference, 0.001)
    }

    @Test
    fun testAutomatedReconciler_Tier2_ChequeAndUtrReferenceMatch() {
        val issuedDate = sdf.parse("01/10/2026")!!
        val clearedDate = sdf.parse("22/10/2026")!! // 21 days later! Beyond 7-day window

        val bankTx = listOf(
            ParsedBankTransaction(
                date = clearedDate,
                description = "CHQ CLG 000456 CLEARING",
                amount = 75000.0,
                isDeposit = false,
                referenceNumber = "000456"
            )
        )

        val voucher = VoucherWithAllEntries(
            voucher = Voucher(
                id = 5,
                voucherNumber = "CHQ-000456",
                date = issuedDate,
                type = "Payment",
                companyId = 1,
                narration = "Issued cheque 000456 to Landlord"
            ),
            voucherEntries = listOf(
                VoucherEntry(id = 9, voucherId = 5, ledgerId = 99, amount = 75000.0.rupeesToPaise(), isDebit = true),
                VoucherEntry(id = 10, voucherId = 5, ledgerId = 10, amount = 75000.0.rupeesToPaise(), isDebit = false)
            ),
            inventoryEntries = emptyList()
        )

        val report = AutomatedReconciler.reconcile(
            bankTransactions = bankTx,
            bookVouchers = listOf(voucher),
            bankLedgerId = 10
        )

        assertEquals(1, report.matched.size)
        assertEquals("Should match by reference number across 21 days", ReconciliationMatchLevel.DESCRIPTION_MATCH, report.matched.first().matchLevel)
        assertEquals(0, report.missingInBooks.size)
        assertEquals(0, report.unclearedInBank.size)
    }

    @Test
    fun testAutomatedReconciler_Tier3_ChequeClearanceWindow() {
        val voucherDate = sdf.parse("10/10/2026")!!
        val bankClearDate = sdf.parse("15/10/2026")!! // 5 days clearance window

        val bankTx = listOf(
            ParsedBankTransaction(
                date = bankClearDate,
                description = "NEFT INWARD CLEARING",
                amount = 12000.0,
                isDeposit = true
            )
        )

        val voucher = VoucherWithAllEntries(
            voucher = Voucher(id = 6, voucherNumber = "REC-99", date = voucherDate, type = "Receipt", companyId = 1),
            voucherEntries = listOf(
                VoucherEntry(id = 11, voucherId = 6, ledgerId = 10, amount = 12000.0.rupeesToPaise(), isDebit = true),
                VoucherEntry(id = 12, voucherId = 6, ledgerId = 40, amount = 12000.0.rupeesToPaise(), isDebit = false)
            ),
            inventoryEntries = emptyList()
        )

        val report = AutomatedReconciler.reconcile(
            bankTransactions = bankTx,
            bookVouchers = listOf(voucher),
            bankLedgerId = 10
        )

        assertEquals(1, report.matched.size)
        assertEquals(ReconciliationMatchLevel.AMOUNT_PROBABLE, report.matched.first().matchLevel)
    }

    @Test
    fun testAutomatedReconciler_IdentifiesMissingInBooksAndUnclearedInBank() {
        val now = Date()

        // 1. Bank transaction that has NO corresponding voucher in the app (e.g. Bank Interest received)
        val unrecordedBankTx = ParsedBankTransaction(
            date = now,
            description = "CREDIT INTEREST QUARTERLY",
            amount = 1450.0,
            isDeposit = true
        )

        // 2. Voucher issued in app that hasn't cleared in the bank yet (Uncleared cheque)
        val unclearedCheque = VoucherWithAllEntries(
            voucher = Voucher(id = 7, voucherNumber = "PAY-CHQ-99", date = now, type = "Payment", companyId = 1),
            voucherEntries = listOf(
                VoucherEntry(id = 15, voucherId = 7, ledgerId = 50, amount = 30000.0.rupeesToPaise(), isDebit = true),
                VoucherEntry(id = 16, voucherId = 7, ledgerId = 10, amount = 30000.0.rupeesToPaise(), isDebit = false)
            ),
            inventoryEntries = emptyList()
        )

        val report = AutomatedReconciler.reconcile(
            bankTransactions = listOf(unrecordedBankTx),
            bookVouchers = listOf(unclearedCheque),
            bankLedgerId = 10
        )

        assertEquals(0, report.matched.size)
        assertEquals("Bank Interest should be flagged as missing in books", 1, report.missingInBooks.size)
        assertEquals(1450.0, report.missingInBooks.first().amount, 0.001)

        assertEquals("Unpresented cheque should be flagged as uncleared in bank", 1, report.unclearedInBank.size)
        assertEquals("PAY-CHQ-99", report.unclearedInBank.first().voucher.voucherNumber)
    }

    @Test
    fun testAutomatedReconciler_BalanceMathAndStressTest() {
        val date = Date()
        val bankTxList = mutableListOf<ParsedBankTransaction>()
        val bookVoucherList = mutableListOf<VoucherWithAllEntries>()

        // Generate 50 matching deposit/withdrawal pairs
        for (i in 1..50) {
            val amount = 100.0 * i
            val isDeposit = i % 2 == 0
            bankTxList.add(
                ParsedBankTransaction(
                    date = date,
                    description = "Txn #$i",
                    amount = amount,
                    isDeposit = isDeposit,
                    referenceNumber = "REF$i"
                )
            )

            bookVoucherList.add(
                VoucherWithAllEntries(
                    voucher = Voucher(id = i, voucherNumber = "V-$i", date = date, type = if (isDeposit) "Receipt" else "Payment", companyId = 1),
                    voucherEntries = listOf(
                        VoucherEntry(id = i * 2, voucherId = i, ledgerId = 10, amount = amount.rupeesToPaise(), isDebit = isDeposit),
                        VoucherEntry(id = i * 2 + 1, voucherId = i, ledgerId = 20, amount = amount.rupeesToPaise(), isDebit = !isDeposit)
                    ),
                    inventoryEntries = emptyList()
                )
            )
        }

        // Add 5 unrecorded bank deposits (Missing in books)
        for (j in 1..5) {
            bankTxList.add(
                ParsedBankTransaction(date = date, description = "Unrecorded $j", amount = 500.0 * j, isDeposit = true)
            )
        }

        // Add 3 unpresented book payments (Uncleared in bank)
        for (k in 1..3) {
            val vid = 100 + k
            bookVoucherList.add(
                VoucherWithAllEntries(
                    voucher = Voucher(id = vid, voucherNumber = "Uncleared-$k", date = date, type = "Payment", companyId = 1),
                    voucherEntries = listOf(
                        VoucherEntry(id = vid * 2, voucherId = vid, ledgerId = 30, amount = (2500.0 * k).rupeesToPaise(), isDebit = true),
                        VoucherEntry(id = vid * 2 + 1, voucherId = vid, ledgerId = 10, amount = (2500.0 * k).rupeesToPaise(), isDebit = false)
                    ),
                    inventoryEntries = emptyList()
                )
            )
        }

        val report = AutomatedReconciler.reconcile(
            bankTransactions = bankTxList,
            bookVouchers = bookVoucherList,
            bankLedgerId = 10
        )

        assertEquals("All 50 base pairs must be reconciled", 50, report.matched.size)
        assertEquals("5 unrecorded items must be missing in books", 5, report.missingInBooks.size)
        assertEquals("3 unpresented vouchers must be uncleared in bank", 3, report.unclearedInBank.size)

        // Mathematical verification
        val expectedMissingInBooksSum = (1..5).sumOf { 500.0 * it }
        val actualMissingInBooksSum = report.missingInBooks.sumOf { it.amount }
        assertEquals(expectedMissingInBooksSum, actualMissingInBooksSum, 0.001)

        val expectedUnclearedSum = (1..3).sumOf { 2500.0 * it }
        val actualUnclearedSum = report.unclearedInBank.flatMap { it.voucherEntries }.filter { it.ledgerId == 10 }.sumOf { it.amount.paiseToRupees() }
        assertEquals(expectedUnclearedSum, actualUnclearedSum, 0.001)
    }
}
