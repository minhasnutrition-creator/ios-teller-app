package com.minhas.teller.utils

import com.minhas.teller.TellerApp
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class UniversalMultiAppImportTest {

    @Before
    fun setUp() {
        TellerApp.configureGlobalProperties()
    }

    private fun createWorkbookBytes(sheetName: String, headers: List<String>, rows: List<List<Any?>>): ByteArray {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet(sheetName)
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { idx, h ->
            headerRow.createCell(idx).setCellValue(h)
        }
        rows.forEachIndexed { rIdx, rowData ->
            val row = sheet.createRow(rIdx + 1)
            rowData.forEachIndexed { cIdx, value ->
                val cell = row.createCell(cIdx)
                when (value) {
                    is Number -> cell.setCellValue(value.toDouble())
                    is Boolean -> cell.setCellValue(value)
                    is Date -> {
                        cell.setCellValue(value)
                        val cs = wb.createCellStyle()
                        cs.dataFormat = wb.creationHelper.createDataFormat().getFormat("dd-MM-yyyy")
                        cell.cellStyle = cs
                    }
                    null -> cell.setCellValue("")
                    else -> cell.setCellValue(value.toString())
                }
            }
        }
        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        return out.toByteArray()
    }

    @Test
    fun testVyaparExport_flatSingleSheetSalesWithMultipleItems() {
        // Vyapar flat Excel export style: "Sale Invoice", single sheet, columns like "Invoice No", "Customer Name", "Price/Unit"
        val headers = listOf("Invoice No", "Date", "Customer Name", "Item Name", "Quantity", "Price/Unit", "Discount", "Tax %")
        val rows = listOf(
            listOf("INV-101", "2024-04-01", "Sharma Kirana Store", "Basmati Rice 5kg", 10.0, 450.0, 50.0, 5.0),
            listOf("INV-101", "2024-04-01", "Sharma Kirana Store", "Mustard Oil 1L", 20.0, 160.0, 0.0, 5.0),
            listOf("INV-102", "2024-04-02", "Gupta Provision", "Toor Dal 1kg", 15.0, 130.0, 0.0, 0.0)
        )
        val bytes = createWorkbookBytes("Sale Invoices", headers, rows)

        val result = VoucherExcelImporter.parse(ByteArrayInputStream(bytes))
        assertTrue("Result should be SaleResult, got: $result", result is VoucherExcelImporter.ImportResult.SaleResult)
        val saleResult = result as VoucherExcelImporter.ImportResult.SaleResult

        assertEquals("Should parse 2 distinct sale vouchers", 2, saleResult.vouchers.size)

        val vch101 = saleResult.vouchers.find { it.voucherNumber == "INV-101" }
        assertNotNull("INV-101 must exist", vch101)
        assertEquals("Sharma Kirana Store", vch101!!.partyName)
        assertEquals("INV-101 must have 2 aggregated line items", 2, vch101.items.size)
        assertEquals("Basmati Rice 5kg", vch101.items[0].itemName)
        assertEquals(10.0, vch101.items[0].quantity, 0.001)
        assertEquals(450.0, vch101.items[0].rate, 0.001)
        assertEquals(50.0, vch101.items[0].discount, 0.001)

        assertEquals("Mustard Oil 1L", vch101.items[1].itemName)
        assertEquals(20.0, vch101.items[1].quantity, 0.001)

        val vch102 = saleResult.vouchers.find { it.voucherNumber == "INV-102" }
        assertNotNull("INV-102 must exist", vch102)
        assertEquals("Gupta Provision", vch102!!.partyName)
        assertEquals(1, vch102.items.size)
    }

    @Test
    fun testBusyExport_singleSheetPurchases() {
        // Busy style: "Bill No", "Vendor Name", "Product Name", "Qty", "Rate"
        val headers = listOf("Bill No", "Date", "Vendor Name", "Product Name", "Qty", "Rate", "Tax Rate")
        val rows = listOf(
            listOf("PUR-8821", "15-05-2024", "National Distributors", "Refined Sunflower Oil 15L", 5.0, 1850.0, 5.0)
        )
        val bytes = createWorkbookBytes("Purchase_Book", headers, rows)

        val result = VoucherExcelImporter.parse(ByteArrayInputStream(bytes))
        assertTrue("Result should be PurchaseResult, got: $result", result is VoucherExcelImporter.ImportResult.PurchaseResult)
        val purchaseResult = result as VoucherExcelImporter.ImportResult.PurchaseResult

        assertEquals(1, purchaseResult.vouchers.size)
        val vch = purchaseResult.vouchers[0]
        assertEquals("PUR-8821", vch.voucherNumber)
        assertEquals("National Distributors", vch.partyName)
        assertEquals(1, vch.items.size)
        assertEquals("Refined Sunflower Oil 15L", vch.items[0].itemName)
        assertEquals(5.0, vch.items[0].quantity, 0.001)
        assertEquals(1850.0, vch.items[0].rate, 0.001)
    }

    @Test
    fun testMargAndCustomErp_csvSalesImport() {
        // Marg / Custom ERP CSV export
        val csvData = """
            Invoice Number,Date,Party,Item Name,Quantity,Rate,Tax Percentage
            CSV-001,10/06/2024,Verma Wholesale,Wheat Flour 10Kg,25,380,5
            CSV-001,10/06/2024,Verma Wholesale,Chana Dal 1Kg,50,95,0
            CSV-002,11/06/2024,Royal Sweets,Sugar M-30 50Kg,4,2100,5
        """.trimIndent()

        val result = VoucherExcelImporter.parse(ByteArrayInputStream(csvData.toByteArray(Charsets.UTF_8)))
        assertTrue("CSV data must parse into SaleResult", result is VoucherExcelImporter.ImportResult.SaleResult)
        val saleResult = result as VoucherExcelImporter.ImportResult.SaleResult

        assertEquals(2, saleResult.vouchers.size)
        val v1 = saleResult.vouchers.find { it.voucherNumber == "CSV-001" }
        assertNotNull(v1)
        assertEquals("Verma Wholesale", v1!!.partyName)
        assertEquals(2, v1.items.size)
    }

    @Test
    fun testZohoBooksAndQuickBooks_serialDatesAndSynonyms() {
        // Zoho Books / QuickBooks: "Invoice Number", "Invoice Date", "Customer", "Item", "Quantity", "Sales Price"
        val cal = Calendar.getInstance()
        cal.set(2024, Calendar.OCTOBER, 15, 0, 0, 0)
        val testDate = cal.time

        val headers = listOf("Invoice Number", "Invoice Date", "Customer", "Item", "Quantity", "Sales Price", "Tax Percentage")
        val rows = listOf(
            listOf("QB-9001", testDate, "Pooja Cosmetics", "Facewash Neem 100ml", 12.0, 110.0, 18.0)
        )
        val bytes = createWorkbookBytes("Invoices", headers, rows)

        val result = VoucherExcelImporter.parse(ByteArrayInputStream(bytes))
        assertTrue("QuickBooks format must parse as SaleResult", result is VoucherExcelImporter.ImportResult.SaleResult)
        val saleResult = result as VoucherExcelImporter.ImportResult.SaleResult

        assertEquals(1, saleResult.vouchers.size)
        val vch = saleResult.vouchers[0]
        assertEquals("QB-9001", vch.voucherNumber)
        assertEquals("Pooja Cosmetics", vch.partyName)
        assertEquals(1, vch.items.size)
        assertEquals("Facewash Neem 100ml", vch.items[0].itemName)

        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
        assertEquals(sdf.format(testDate), sdf.format(vch.date))
    }

    @Test
    fun testKhatabookAndMyBillBook_paymentsAndReceipts() {
        // Payment sheet: "Voucher No", "Date", "Paid To", "Paid From", "Amount"
        val payHeaders = listOf("Voucher No", "Date", "Paid To", "Paid From", "Amount", "Narration")
        val payRows = listOf(
            listOf("PAY-501", "2024-05-20", "Bajaj Auto Ltd", "HDFC Bank", 25000.0, "Monthly installment")
        )
        val payBytes = createWorkbookBytes("Payment_Book", payHeaders, payRows)

        val payResult = VoucherExcelImporter.parse(ByteArrayInputStream(payBytes))
        assertTrue("Must detect PaymentResult", payResult is VoucherExcelImporter.ImportResult.PaymentResult)
        val pRes = payResult as VoucherExcelImporter.ImportResult.PaymentResult
        assertEquals(1, pRes.vouchers.size)
        assertEquals("PAY-501", pRes.vouchers[0].voucherNumber)
        assertEquals("Bajaj Auto Ltd", pRes.vouchers[0].payToLedger)
        assertEquals("HDFC Bank", pRes.vouchers[0].payFromLedger)
        assertEquals(25000.0, pRes.vouchers[0].amount, 0.001)

        // Receipt sheet: "Receipt No", "Date", "Received From", "Receive In", "Amount"
        val recHeaders = listOf("Receipt No", "Date", "Received From", "Receive In", "Amount")
        val recRows = listOf(
            listOf("REC-301", "2024-05-21", "Singhania Corp", "ICICI Bank", 45000.0)
        )
        val recBytes = createWorkbookBytes("Receipt_Register", recHeaders, recRows)

        val recResult = VoucherExcelImporter.parse(ByteArrayInputStream(recBytes))
        assertTrue("Must detect ReceiptResult", recResult is VoucherExcelImporter.ImportResult.ReceiptResult)
        val rRes = recResult as VoucherExcelImporter.ImportResult.ReceiptResult
        assertEquals(1, rRes.vouchers.size)
        assertEquals("REC-301", rRes.vouchers[0].voucherNumber)
        assertEquals("Singhania Corp", rRes.vouchers[0].receivedFrom)
        assertEquals("ICICI Bank", rRes.vouchers[0].receiveInLedger)
        assertEquals(45000.0, rRes.vouchers[0].amount, 0.001)
    }

    @Test
    fun testMathematicalDoubleEntryIntegrity_onConversion() {
        // Generate Tally XML with 3 vouchers and verify conversion maintains Debits == Credits down to 0 paise
        val sampleXml = """
            <ENVELOPE>
                <BODY>
                    <IMPORTDATA>
                        <REQUESTDATA>
                            <TALLYMESSAGE>
                                <VOUCHER VCHTYPE="Sales" ACTION="Create">
                                    <DATE>20240401</DATE>
                                    <VOUCHERTYPENAME>Sales</VOUCHERTYPENAME>
                                    <VOUCHERNUMBER>VCH-BAL-01</VOUCHERNUMBER>
                                    <PARTYNAME>Kalyan Jewellers</PARTYNAME>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Kalyan Jewellers</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>Yes</ISDEEMEDPOSITIVE>
                                        <AMOUNT>-1180.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>Sales Account</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>1000.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>CGST Output</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>90.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                    <ALLLEDGERENTRIES.LIST>
                                        <LEDGERNAME>SGST Output</LEDGERNAME>
                                        <ISDEEMEDPOSITIVE>No</ISDEEMEDPOSITIVE>
                                        <AMOUNT>90.00</AMOUNT>
                                    </ALLLEDGERENTRIES.LIST>
                                </VOUCHER>
                            </TALLYMESSAGE>
                        </REQUESTDATA>
                    </IMPORTDATA>
                </BODY>
            </ENVELOPE>
        """.trimIndent()

        val parsedXml = UniversalParser.parseTallyXml(ByteArrayInputStream(sampleXml.toByteArray(Charsets.UTF_8)))
        val converted = TallyEntityConverter.convert(parsedXml, companyId = 1)

        assertEquals(1, converted.voucherBundles.size)
        val bundle = converted.voucherBundles[0]

        val totalDebits = bundle.entries.filter { it.isDebit }.sumOf { it.amount }
        val totalCredits = bundle.entries.filter { !it.isDebit }.sumOf { it.amount }

        assertEquals("Total debits must equal total credits in paise", totalDebits, totalCredits)
        assertEquals(118000L, totalDebits)
        assertEquals(118000L, totalCredits)
    }

    @Test
    fun testDebtorsPendingPaymentSheet_parsesAsPaymentVoucherWithoutGeneralSales() {
        // Excel sheet with debtors and pending payment amounts (no inventory columns)
        val headers = listOf("Debtor Name", "Date", "Pending Amount", "Bill Reference")
        val rows = listOf(
            listOf("Sharma Enterprises", "2024-04-10", 12500.0, "BILL-001"),
            listOf("Gupta Traders", "2024-04-12", 8400.0, "BILL-002"),
            listOf("Verma Stores", "2024-04-15", 3200.0, "BILL-003")
        )
        val bytes = createWorkbookBytes("Pending Debtors", headers, rows)

        // 1. Auto-detection should identify as PaymentResult (or ReceiptResult) - NEVER SaleResult!
        val autoResult = VoucherExcelImporter.parse(ByteArrayInputStream(bytes))
        assertFalse("Debtor pending payment sheet should NEVER be parsed as SaleResult", autoResult is VoucherExcelImporter.ImportResult.SaleResult)
        assertTrue("Auto-detection should route to PaymentResult, got: ${autoResult.javaClass.simpleName}", autoResult is VoucherExcelImporter.ImportResult.PaymentResult)

        val payResult = autoResult as VoucherExcelImporter.ImportResult.PaymentResult
        assertEquals("Should parse all 3 debtor records", 3, payResult.vouchers.size)
        assertEquals("Sharma Enterprises", payResult.vouchers[0].payToLedger)
        assertEquals(12500.0, payResult.vouchers[0].amount, 0.001)
        assertEquals("BILL-001", payResult.vouchers[0].billReference)

        assertEquals("Gupta Traders", payResult.vouchers[1].payToLedger)
        assertEquals(8400.0, payResult.vouchers[1].amount, 0.001)

        // 2. Explicit forced conversion to Receipt vouchers
        val forcedReceipt = VoucherExcelImporter.parse(
            inputStream = ByteArrayInputStream(bytes),
            forcedType = VoucherExcelImporter.ImportType.RECEIPT
        )
        assertTrue("Should allow switching to ReceiptResult", forcedReceipt is VoucherExcelImporter.ImportResult.ReceiptResult)
        val recResult = forcedReceipt as VoucherExcelImporter.ImportResult.ReceiptResult
        assertEquals(3, recResult.vouchers.size)
        assertEquals("Sharma Enterprises", recResult.vouchers[0].receivedFrom)
        assertEquals(12500.0, recResult.vouchers[0].amount, 0.001)
    }
}
