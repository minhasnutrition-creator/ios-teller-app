package com.minhas.teller.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

/**
 * Generates ready-to-download sample Excel templates that show users the required column
 * format for each voucher type (Sale, Purchase, Payment, Receipt).
 *
 * Each template contains:
 *  - A styled header row with column names & data types
 *  - A "Notes" row explaining each column
 *  - 2 realistic sample data rows
 *  - Column widths auto-sized for readability
 */
object ExcelImportSampleGenerator {

    // ──────────────────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────────────────

    enum class VoucherTemplate(val displayName: String, val fileName: String) {
        SALE("Sale Voucher", "teller_sample_sale_import.xlsx"),
        PURCHASE("Purchase Voucher", "teller_sample_purchase_import.xlsx"),
        PAYMENT("Payment Voucher", "teller_sample_payment_import.xlsx"),
        RECEIPT("Receipt Voucher", "teller_sample_receipt_import.xlsx")
    }

    /**
     * Saves raw Excel bytes directly to the device's public Downloads directory.
     */
    fun saveBytesToDownloads(context: Context, fileName: String, bytes: ByteArray): File? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    }
                    return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                }
            }
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            return file
        } catch (t: Throwable) {
            android.util.Log.e("ExcelImportSampleGen", "Failed to save bytes to downloads: ${t.message}", t)
            throw java.io.IOException("Failed to save to downloads: ${t.localizedMessage ?: "Unknown error"}", t)
        }
    }

    /**
     * Saves the sample Excel file directly to the device's public Downloads directory.
     */
    fun saveToDownloads(context: Context, template: VoucherTemplate): File? {
        com.minhas.teller.TellerApp.configureGlobalProperties()
        val bytes = buildWorkbookBytes(template)
        return saveBytesToDownloads(context, template.fileName, bytes)
    }

    /**
     * Creates the sample Excel file for [template] in the app's cache directory,
     * then triggers a share-sheet so the user can save/send it.
     */
    fun downloadAndShare(context: Context, template: VoucherTemplate) {
        com.minhas.teller.TellerApp.configureGlobalProperties()
        val workbook = buildWorkbook(template)
        val cacheDir = File(context.cacheDir, "import_samples").apply { mkdirs() }
        val file = File(cacheDir, template.fileName)
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Teller Import Template – ${template.displayName}")
            putExtra(
                Intent.EXTRA_TEXT,
                "Fill in your data following the sample rows, then import this file into Teller."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Save/Share Sample Template"))
    }

    /**
     * Builds the sample workbook and returns its raw bytes.
     * Used by the web server to stream the file directly to the browser.
     */
    fun buildWorkbookBytes(template: VoucherTemplate): ByteArray {
        com.minhas.teller.TellerApp.configureGlobalProperties()
        val wb = buildWorkbook(template)
        val out = java.io.ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        return out.toByteArray()
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Template Definitions
    // ──────────────────────────────────────────────────────────────────────────

    private fun buildWorkbook(template: VoucherTemplate): XSSFWorkbook {
        return when (template) {
            VoucherTemplate.SALE -> buildSaleWorkbook()
            VoucherTemplate.PURCHASE -> buildPurchaseWorkbook()
            VoucherTemplate.PAYMENT -> buildPaymentWorkbook()
            VoucherTemplate.RECEIPT -> buildReceiptWorkbook()
        }
    }

    // ──────── SALE ────────────────────────────────────────────────────────────

    private fun buildSaleWorkbook(): XSSFWorkbook {
        val wb = XSSFWorkbook()

        // Sheet 1: Voucher header info
        val voucherSheet = wb.createSheet("Sale_Vouchers")
        val voucherCols = listOf(
            Col("VoucherNumber",   "Text",   "Required. Unique bill/invoice number. E.g. INV-001"),
            Col("Date",            "Date",   "Required. Format: DD-MM-YYYY. E.g. 15-01-2025"),
            Col("PartyName",       "Text",   "Required. Customer name (account auto-created if not in app)"),
            Col("PlaceOfSupply",   "Text",   "Optional. State name for GST. E.g. Maharashtra"),
            Col("IsIGST",          "Yes/No", "Optional. YES if inter-state (IGST), NO for intra-state (CGST+SGST). Default: NO"),
            Col("IsFinanced",      "Yes/No", "Optional. YES if payment is financed/loan. Default: NO"),
            Col("FinancedBy",      "Text",   "Optional. Finance company name (e.g. Bajaj Finance, HDFC). Auto-created if not in app"),
            Col("Narration",       "Text",   "Optional. Remarks / description for the voucher"),
            Col("CashLedger",      "Text",   "Optional. Cash/Bank ledger receiving down payment. E.g. Cash, HDFC Bank"),
            Col("CashAmount",      "Number", "Optional. Amount paid immediately via CashLedger"),
            Col("RoundOff",        "Number", "Optional. Round-off amount (+/-). E.g. 0.50 or -0.25")
        )
        val sampleVouchers = listOf(
            listOf("INV-001", "15-01-2025", "Rahul Verma", "Maharashtra", "NO", "YES", "Bajaj Finance", "Sale of motorcycle on finance", "Cash", "5000.00", "0.00"),
            listOf("INV-002", "16-01-2025", "Suresh Enterprises", "Gujarat", "YES", "NO", "", "Inter-state sale of equipment", "HDFC Bank", "11800.00", "0.50")
        )
        buildSheet(wb, voucherSheet, voucherCols, sampleVouchers, "SALE VOUCHER – Sheet 1: Voucher Header")

        // Sheet 2: Line items (inventory)
        val itemSheet = wb.createSheet("Sale_Items")
        val itemCols = listOf(
            Col("VoucherNumber",  "Text",   "Required. Must match VoucherNumber in Sale_Vouchers sheet"),
            Col("ItemName",       "Text",   "Required. Stock item name (e.g. Splendor +, iPhone 15, Cement)"),
            Col("ItemCategory",   "Text",   "Optional. Category to help app auto-detect HSN & Tax (e.g. Motorcycle, Mobile, Cement)"),
            Col("Quantity",       "Number", "Required. Quantity sold. E.g. 1"),
            Col("Unit",           "Text",   "Optional. Unit of measure. E.g. Nos, Kg, Ltr. Default: Nos"),
            Col("Rate",           "Number", "Required. Selling rate per unit (tax-inclusive). E.g. 75000.00"),
            Col("HSNCode",        "Text",   "Optional. HSN/SAC code. If blank, auto-detected from Name/Category"),
            Col("GSTRate",        "Number", "Optional. GST % on item. If blank, auto-detected from Name/Category"),
            Col("Discount",       "Number", "Optional. Discount amount (not %). E.g. 0.00")
        )
        val sampleItems = listOf(
            listOf("INV-001", "Splendor +",    "Motorcycle",   "1",  "Nos", "75000.00", "",     "",   "0.00"),
            listOf("INV-001", "Studds Helmet", "Helmet",       "1",  "Nos", "1500.00",  "6506", "18", "0.00"),
            listOf("INV-002", "Laptop",        "Electronics",  "10", "Nos", "50000.00", "8471", "18", "200.00")
        )
        buildSheet(wb, itemSheet, itemCols, sampleItems, "SALE VOUCHER – Sheet 2: Line Items (one row per item)")

        addInstructionsSheet(wb, "Sale")
        return wb
    }

    // ──────── PURCHASE ───────────────────────────────────────────────────────

    private fun buildPurchaseWorkbook(): XSSFWorkbook {
        val wb = XSSFWorkbook()

        val voucherSheet = wb.createSheet("Purchase_Vouchers")
        val voucherCols = listOf(
            Col("VoucherNumber",   "Text",   "Required. Unique purchase/bill number. E.g. PUR-001"),
            Col("Date",            "Date",   "Required. Format: DD-MM-YYYY. E.g. 10-01-2025"),
            Col("PartyName",       "Text",   "Required. Supplier/vendor ledger name"),
            Col("SupplierInvNo",   "Text",   "Optional. Original supplier invoice number"),
            Col("PlaceOfSupply",   "Text",   "Optional. State name for GST. E.g. Karnataka"),
            Col("IsIGST",          "Yes/No", "Optional. YES if inter-state purchase. Default: NO"),
            Col("ReverseCharge",   "Yes/No", "Optional. YES if Reverse Charge applicable. Default: NO"),
            Col("Narration",       "Text",   "Optional. Remarks / purpose of purchase"),
            Col("CashLedger",      "Text",   "Optional. Cash/Bank ledger used to pay. E.g. Cash, SBI Bank"),
            Col("CashAmount",      "Number", "Optional. Amount paid immediately")
        )
        val sampleVouchers = listOf(
            listOf("PUR-001", "10-01-2025", "ABC Suppliers", "SUP/2025/001", "Maharashtra", "NO",  "NO",  "Purchase of raw materials", "Cash", "11800.00"),
            listOf("PUR-002", "12-01-2025", "XYZ Wholesale",  "XYZ-100",      "Gujarat",     "YES", "NO",  "Purchase for stock",        "HDFC Bank", "23600.00")
        )
        buildSheet(wb, voucherSheet, voucherCols, sampleVouchers, "PURCHASE VOUCHER – Sheet 1: Voucher Header")

        val itemSheet = wb.createSheet("Purchase_Items")
        val itemCols = listOf(
            Col("VoucherNumber",  "Text",   "Required. Must match VoucherNumber in Purchase_Vouchers sheet"),
            Col("ItemName",       "Text",   "Required. Stock item name"),
            Col("ItemCategory",   "Text",   "Optional. Category to help app auto-detect HSN & Tax (e.g. Grocery, Steel)"),
            Col("Quantity",       "Number", "Required. Quantity purchased"),
            Col("Unit",           "Text",   "Optional. Unit of measure. E.g. Nos, Kg. Default: Nos"),
            Col("Rate",           "Number", "Required. Purchase rate per unit (tax-inclusive)"),
            Col("HSNCode",        "Text",   "Optional. HSN/SAC code. If blank, auto-detected"),
            Col("GSTRate",        "Number", "Optional. GST % on item. If blank, auto-detected"),
            Col("Discount",       "Number", "Optional. Discount amount. E.g. 0.00")
        )
        val sampleItems = listOf(
            listOf("PUR-001", "Rice Bags",   "Grocery", "100", "Kg",  "56.00",   "1006", "5",  "0.00"),
            listOf("PUR-001", "Sugar",       "Grocery", "50",  "Kg",  "47.20",   "1701", "5",  "0.00"),
            listOf("PUR-002", "Steel Rods",  "Steel",   "200", "Nos", "118.00",  "7214", "18", "500.00")
        )
        buildSheet(wb, itemSheet, itemCols, sampleItems, "PURCHASE VOUCHER – Sheet 2: Line Items")

        addInstructionsSheet(wb, "Purchase")
        return wb
    }

    // ──────── PAYMENT ───────────────────────────────────────────────────────

    private fun buildPaymentWorkbook(): XSSFWorkbook {
        val wb = XSSFWorkbook()

        val sheet = wb.createSheet("Payment_Vouchers")
        val cols = listOf(
            Col("VoucherNumber",  "Text",   "Required. Unique payment voucher number. E.g. PAY-001"),
            Col("Date",           "Date",   "Required. Format: DD-MM-YYYY. E.g. 20-01-2025"),
            Col("PayFromLedger",  "Text",   "Required. Bank/Cash ledger money is going OUT from. E.g. HDFC Bank, Cash"),
            Col("PayToLedger",    "Text",   "Required. Party/expense ledger being paid. E.g. Ramesh Traders, Rent A/c"),
            Col("Amount",         "Number", "Required. Payment amount. E.g. 50000.00"),
            Col("IsFinanced",     "Yes/No", "Optional. YES if this is a loan/EMI payment. Default: NO"),
            Col("FinancedBy",     "Text",   "Optional. Financier name if IsFinanced=YES. E.g. HDFC Finance"),
            Col("Narration",      "Text",   "Optional. Purpose of payment. E.g. Rent for January 2025"),
            Col("BillReference",  "Text",   "Optional. Reference bill/invoice number being settled"),
            Col("ChequeNo",       "Text",   "Optional. Cheque or transaction reference number")
        )
        val sampleRows = listOf(
            listOf("PAY-001", "20-01-2025", "HDFC Bank",  "Ramesh Traders",   "50000.00", "NO",  "",               "Payment for INV-001",    "INV-001", "CHQ123456"),
            listOf("PAY-002", "21-01-2025", "Cash",       "Rent A/c",          "15000.00", "NO",  "",               "Office rent Jan 2025",   "",        ""),
            listOf("PAY-003", "22-01-2025", "SBI Bank",   "HDFC Finance",      "12500.00", "YES", "HDFC Finance",   "EMI for vehicle loan",   "",        "NEFT-789")
        )
        buildSheet(wb, sheet, cols, sampleRows, "PAYMENT VOUCHER – One row per payment")

        addInstructionsSheet(wb, "Payment")
        return wb
    }

    // ──────── RECEIPT ──────────────────────────────────────────────────────

    private fun buildReceiptWorkbook(): XSSFWorkbook {
        val wb = XSSFWorkbook()

        val sheet = wb.createSheet("Receipt_Vouchers")
        val cols = listOf(
            Col("VoucherNumber",  "Text",   "Required. Unique receipt voucher number. E.g. RCT-001"),
            Col("Date",           "Date",   "Required. Format: DD-MM-YYYY. E.g. 25-01-2025"),
            Col("ReceiveInLedger","Text",   "Required. Bank/Cash ledger money is coming INTO. E.g. Cash, ICICI Bank"),
            Col("ReceivedFrom",   "Text",   "Required. Party/income ledger paying us. E.g. Suresh Enterprises, Interest Received"),
            Col("Amount",         "Number", "Required. Amount received. E.g. 25000.00"),
            Col("IsFinanced",     "Yes/No", "Optional. YES if received from a financing party. Default: NO"),
            Col("FinancedBy",     "Text",   "Optional. Financier name if IsFinanced=YES. E.g. ICICI Finance"),
            Col("Narration",      "Text",   "Optional. Reason for receipt. E.g. Payment against INV-002"),
            Col("BillReference",  "Text",   "Optional. Reference invoice/bill being settled"),
            Col("ChequeNo",       "Text",   "Optional. Cheque or transaction reference number")
        )
        val sampleRows = listOf(
            listOf("RCT-001", "25-01-2025", "Cash",       "Suresh Enterprises", "25000.00", "NO",  "",             "Payment for INV-002",    "INV-002", ""),
            listOf("RCT-002", "26-01-2025", "ICICI Bank", "Ramesh Traders",      "75000.00", "NO",  "",             "Full payment received",  "INV-001", "IMPS-567"),
            listOf("RCT-003", "27-01-2025", "HDFC Bank",  "ICICI Finance",       "50000.00", "YES", "ICICI Finance","Loan disbursement",      "",        "RTGS-999")
        )
        buildSheet(wb, sheet, cols, sampleRows, "RECEIPT VOUCHER – One row per receipt")

        addInstructionsSheet(wb, "Receipt")
        return wb
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Sheet Builder Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private data class Col(val header: String, val dataType: String, val notes: String)

    private fun buildSheet(
        wb: XSSFWorkbook,
        sheet: org.apache.poi.ss.usermodel.Sheet,
        cols: List<Col>,
        sampleData: List<List<String>>,
        title: String
    ) {
        val styles = createStyles(wb)

        // Row 0: Title (merged)
        val titleRow = sheet.createRow(0)
        titleRow.heightInPoints = 28f
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue(title)
        titleCell.cellStyle = styles.getValue("title")
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, cols.size - 1))

        // Row 1: Column headers
        val headerRow = sheet.createRow(1)
        headerRow.heightInPoints = 22f
        cols.forEachIndexed { i, col ->
            val cell = headerRow.createCell(i)
            cell.setCellValue("${col.header}\n[${col.dataType}]")
            cell.cellStyle = styles.getValue("header")
        }

        // Row 2: Notes / instructions
        val notesRow = sheet.createRow(2)
        notesRow.heightInPoints = 40f
        cols.forEachIndexed { i, col ->
            val cell = notesRow.createCell(i)
            cell.setCellValue(col.notes)
            cell.cellStyle = styles.getValue("notes")
        }

        // Rows 3+: Sample data
        sampleData.forEachIndexed { rowIdx, rowData ->
            val dataRow = sheet.createRow(3 + rowIdx)
            dataRow.heightInPoints = 20f
            rowData.forEachIndexed { colIdx, value ->
                val cell = dataRow.createCell(colIdx)
                cell.setCellValue(value)
                cell.cellStyle = if (rowIdx % 2 == 0) styles.getValue("data_even") else styles.getValue("data_odd")
            }
        }

        // Auto-size columns
        cols.indices.forEach { i ->
            sheet.setColumnWidth(i, 6500) // ~50 chars wide
        }
        sheet.createFreezePane(0, 3) // Freeze title + header + notes
    }

    private fun addInstructionsSheet(wb: XSSFWorkbook, voucherType: String) {
        val sheet = wb.createSheet("HOW_TO_IMPORT")
        val styles = createStyles(wb)

        val instructions = listOf(
            "HOW TO IMPORT $voucherType VOUCHERS INTO TELLER",
            "",
            "STEP 1: Fill in your data",
            "  • Do NOT change column headers or the sheet name",
            "  • Delete the sample rows (rows 4 & 5) before importing",
            "  • The Notes row (row 3) is just for guidance – it will be ignored",
            "  • Date must be in DD-MM-YYYY format (e.g. 15-01-2025)",
            "  • YES/NO fields: type exactly YES or NO (case-insensitive)",
            "",
            "STEP 2: Save the file",
            "  • Keep the file as .xlsx format",
            "  • Do NOT convert to .xls or .csv",
            "",
            "STEP 3: Import into Teller",
            "  • Open Teller → Import & Cleanup Books",
            "  • Select '$voucherType Voucher Excel' import type",
            "  • Tap 'Select File' and choose your filled Excel file",
            "  • Teller will validate every row and show any errors",
            "  • Fix errors if any, then re-import",
            "  • Tap 'Confirm Import' to post all vouchers",
            "",
            "IMPORTANT RULES:",
            "  • Party ledgers that do not exist will be AUTO-CREATED",
            "  • Stock items that do not exist will be AUTO-CREATED (Sale/Purchase only)",
            "  • Duplicate VoucherNumbers in the same import will be skipped",
            "  • Amount must be a positive number without currency symbols",
            "  • For Sale/Purchase: use two sheets – the first for voucher headers,",
            "    the second for line items. Link them via VoucherNumber.",
            "",
            "COLUMN REFERENCE – Required vs Optional:",
            "  [Required] = Import will FAIL if this column is empty",
            "  [Optional] = Safe to leave blank; a default value will be used"
        )

        instructions.forEachIndexed { i, line ->
            val row = sheet.createRow(i)
            val cell = row.createCell(0)
            cell.setCellValue(line)
            cell.cellStyle = when {
                i == 0 -> styles.getValue("title")
                line.startsWith("STEP") || line.startsWith("IMPORTANT") -> styles.getValue("header")
                else -> styles.getValue("notes_plain")
            }
        }
        sheet.setColumnWidth(0, 18000)
    }

    private fun createStyles(wb: XSSFWorkbook): Map<String, org.apache.poi.xssf.usermodel.XSSFCellStyle> {
        val titleFont = wb.createFont().apply {
            bold = true
            fontHeightInPoints = 13
            color = IndexedColors.WHITE.index
        }
        val headerFont = wb.createFont().apply {
            bold = true
            fontHeightInPoints = 10
            color = IndexedColors.WHITE.index
        }
        val notesFont = wb.createFont().apply {
            italic = true
            fontHeightInPoints = 9
            color = IndexedColors.GREY_50_PERCENT.index
        }
        val dataFont = wb.createFont().apply {
            fontHeightInPoints = 10
        }

        fun org.apache.poi.xssf.usermodel.XSSFCellStyle.center() = apply { alignment = HorizontalAlignment.CENTER }
        fun org.apache.poi.xssf.usermodel.XSSFCellStyle.wrap() = apply { wrapText = true }
        fun org.apache.poi.xssf.usermodel.XSSFCellStyle.vCenter() = apply { verticalAlignment = VerticalAlignment.CENTER }
        fun org.apache.poi.xssf.usermodel.XSSFCellStyle.fill(color: Short) = apply {
            fillForegroundColor = color
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        fun org.apache.poi.xssf.usermodel.XSSFCellStyle.border() = apply {
            setBorderBottom(BorderStyle.THIN)
            setBorderTop(BorderStyle.THIN)
            setBorderLeft(BorderStyle.THIN)
            setBorderRight(BorderStyle.THIN)
            bottomBorderColor = IndexedColors.GREY_25_PERCENT.index
            topBorderColor = IndexedColors.GREY_25_PERCENT.index
            leftBorderColor = IndexedColors.GREY_25_PERCENT.index
            rightBorderColor = IndexedColors.GREY_25_PERCENT.index
        }

        val titleStyle = wb.createCellStyle().apply {
            setFont(titleFont)
            fill(IndexedColors.DARK_BLUE.index)
            center(); vCenter(); wrap(); border()
        }
        val headerStyle = wb.createCellStyle().apply {
            setFont(headerFont)
            fill(IndexedColors.DARK_TEAL.index)
            center(); vCenter(); wrap(); border()
        }
        val notesStyle = wb.createCellStyle().apply {
            setFont(notesFont)
            fill(IndexedColors.LIGHT_YELLOW.index)
            vCenter(); wrap(); border()
        }
        val notesPlainStyle = wb.createCellStyle().apply {
            setFont(notesFont)
            vCenter(); wrap()
        }
        val dataEvenStyle = wb.createCellStyle().apply {
            setFont(dataFont)
            fill(IndexedColors.WHITE.index)
            vCenter(); border()
        }
        val dataOddStyle = wb.createCellStyle().apply {
            setFont(dataFont)
            fill(IndexedColors.LIGHT_TURQUOISE.index)
            vCenter(); border()
        }

        return mapOf(
            "title" to titleStyle,
            "header" to headerStyle,
            "notes" to notesStyle,
            "notes_plain" to notesPlainStyle,
            "data_even" to dataEvenStyle,
            "data_odd" to dataOddStyle
        )
    }
}
