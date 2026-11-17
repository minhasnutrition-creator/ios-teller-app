package com.minhas.teller.utils.export

import android.content.Context
import android.util.Log
import com.minhas.teller.utils.ExcelNavigator
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import java.io.File
import java.io.FileOutputStream

object ExcelExportEngine {
    private const val TAG = "ExcelExportEngine"

    /**
     * Safely exports tabular report data into an Excel spreadsheet (.xlsx)
     * using SXSSFWorkbook streaming mode to eliminate OutOfMemoryError on large datasets.
     */
    fun exportReportToExcel(
        context: Context,
        fileName: String,
        sheetName: String,
        headers: List<String>,
        data: List<List<String>>
    ) {
        val file = File(context.getExternalFilesDir(null), fileName)
        com.minhas.teller.TellerApp.configureGlobalProperties()
        var workbook: SXSSFWorkbook? = null
        try {
            workbook = SXSSFWorkbook(100) // Keep 100 rows in memory, stream remaining to temp storage
            val sheet = workbook.createSheet(sheetName)

            // Header Style
            val headerStyle = workbook.createCellStyle().apply {
                val headerFont = workbook.createFont().apply {
                    bold = true
                    fontHeightInPoints = 11
                }
                setFont(headerFont)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
            }

            // Write Headers
            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { colIndex, title ->
                val cell = headerRow.createCell(colIndex)
                cell.setCellValue(title)
                cell.cellStyle = headerStyle
            }

            // Data Style
            val dataStyle = workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.LEFT
                verticalAlignment = VerticalAlignment.CENTER
            }

            // Write Data Rows
            data.forEachIndexed { rowIndex, rowData ->
                val row = sheet.createRow(rowIndex + 1)
                rowData.forEachIndexed { colIndex, cellValue ->
                    val cell = row.createCell(colIndex)
                    cell.setCellValue(cellValue)
                    cell.cellStyle = dataStyle
                }
            }

            // Write file
            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            ExcelNavigator.open(file.absolutePath, sheetName)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to export Excel report: ${e.message}", e)
        } finally {
            try {
                workbook?.dispose() // Clean up temp files created by SXSSFWorkbook on disk
                workbook?.close()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to close/dispose SXSSFWorkbook", e)
            }
        }
    }
}
