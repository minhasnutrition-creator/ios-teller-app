package com.minhas.teller.utils

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Universal Master Importer that ingests ANY backup or export file from any accounting system:
 * - Tally XML Exports (<ENVELOPE>)
 * - Tally Binary Backup Archives (.001 / TBK900 / .900)
 * - Busy Accounting Software Backups (.bsh / .zip / .mdb)
 * - Excel Spreadsheets (.xlsx / .xls)
 * - CSV Tabular Data (.csv)
 */
object UniversalBackupImporter {

    enum class DetectedFormat(val displayName: String) {
        TALLY_XML("Tally XML Export"),
        TALLY_TBK("Tally Binary Backup (.001 / TBK)"),
        BUSY_BACKUP("Busy Accounting Backup (.bsh / .mdb)"),
        EXCEL_WORKBOOK("Excel Workbook (.xlsx / .xls)"),
        CSV_FILE("CSV Tabular File (.csv)"),
        UNKNOWN("Unknown Format")
    }

    data class BackupImportResult(
        val detectedFormat: DetectedFormat,
        val ledgersCount: Int,
        val vouchersCount: Int,
        val stockItemsCount: Int,
        val tallyResult: UniversalParser.TallyImportResult? = null,
        val excelResult: VoucherExcelImporter.ImportResult? = null,
        val errorMessage: String? = null
    )

    fun detectFormat(bytes: ByteArray, filename: String? = null): DetectedFormat {
        val lowerName = filename?.lowercase() ?: ""

        // 1. Tally XML (supports UTF-8, UTF-16LE, and BOM)
        val sampleLen = minOf(2000, bytes.size)
        val headStrUtf8 = bytes.decodeToString(0, sampleLen, throwOnInvalidSequence = false)
        val headStrUtf16 = runCatching { bytes.decodeToString(0, sampleLen, throwOnInvalidSequence = false) }.getOrDefault("")
        val isXml = headStrUtf8.contains("<ENVELOPE", ignoreCase = true) || headStrUtf16.contains("<ENVELOPE", ignoreCase = true) ||
            headStrUtf8.contains("<TALLYMESSAGE", ignoreCase = true) || headStrUtf16.contains("<TALLYMESSAGE", ignoreCase = true) ||
            headStrUtf8.contains("<LEDGER", ignoreCase = true) || headStrUtf16.contains("<LEDGER", ignoreCase = true) ||
            lowerName.endsWith(".xml")
        if (isXml) {
            return DetectedFormat.TALLY_XML
        }

        // 2. Tally Binary Backup (.001 / TBK900 / .900)
        if (TallyBackupExtractor.isTallyBackup(bytes) || lowerName.endsWith(".001") || lowerName.contains("tbk") || lowerName.endsWith(".900")) {
            return DetectedFormat.TALLY_TBK
        }

        // 3. Busy Backup (.bsh / .mdb / .bak)
        if (BusyBackupExtractor.isBusyBackup(bytes, filename)) {
            return DetectedFormat.BUSY_BACKUP
        }

        // 4. Excel Workbook (.xlsx or .xls)
        val isOleXls = bytes.size >= 8 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
        val isZipXlsx = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte() &&
            (lowerName.endsWith(".xlsx") || headStrUtf8.contains("[Content_Types].xml") || isZipContainingExcel(bytes))
        if (isOleXls || isZipXlsx || lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
            return DetectedFormat.EXCEL_WORKBOOK
        }

        // 5. ZIP Archive (inspect inner entries)
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
            val detectedFromZip = detectFromZipArchive(bytes)
            if (detectedFromZip != DetectedFormat.UNKNOWN) {
                return detectedFromZip
            }
        }

        // 6. CSV file
        if (lowerName.endsWith(".csv") || headStrUtf8.lines().take(5).any { it.contains(",") && it.split(",").size >= 2 }) {
            return DetectedFormat.CSV_FILE
        }

        return DetectedFormat.UNKNOWN
    }

    private fun isZipContainingExcel(bytes: ByteArray): Boolean {
        return try {
            val zis = java.util.zip.ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zis.nextEntry
            var found = false
            while (entry != null) {
                val n = entry.name.lowercase()
                if (n.contains("[content_types].xml") || n.contains("xl/workbook.xml")) {
                    found = true
                    break
                }
                entry = zis.nextEntry
            }
            found
        } catch (_: Exception) {
            false
        }
    }

    private fun detectFromZipArchive(bytes: ByteArray): DetectedFormat {
        return try {
            val zis = java.util.zip.ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zis.nextEntry
            var result = DetectedFormat.UNKNOWN
            while (entry != null) {
                val n = entry.name.lowercase()
                if (n.contains("[content_types].xml") || n.endsWith(".xlsx") || n.endsWith(".xls")) {
                    return DetectedFormat.EXCEL_WORKBOOK
                }
                if (n.endsWith(".xml")) {
                    return DetectedFormat.TALLY_XML
                }
                if (n.endsWith(".001") || n.contains("tbk") || n.endsWith(".900") || n.contains("company.900")) {
                    return DetectedFormat.TALLY_TBK
                }
                if (n.endsWith(".bsh") || n.endsWith(".mdb")) {
                    return DetectedFormat.BUSY_BACKUP
                }
                if (n.endsWith(".csv")) {
                    result = DetectedFormat.CSV_FILE
                }
                entry = zis.nextEntry
            }
            result
        } catch (_: Exception) {
            DetectedFormat.UNKNOWN
        }
    }

    private fun extractInnerZipFile(bytes: ByteArray, predicate: (String) -> Boolean): ByteArray? {
        return try {
            val zis = java.util.zip.ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zis.nextEntry
            var extracted: ByteArray? = null
            while (entry != null) {
                if (!entry.isDirectory && predicate(entry.name.lowercase())) {
                    val content = zis.readBytes()
                    if (content.isNotEmpty()) {
                        extracted = content
                        break
                    }
                }
                entry = zis.nextEntry
            }
            extracted
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Ingests ANY backup or export file from any application and returns normalized data ready for Teller.
     */
    fun parseAnyFile(
        inputStream: InputStream,
        filename: String? = null,
        context: Context? = null,
        companyId: Int = 1,
        existingItemsMap: Map<String, com.minhas.teller.data.entities.StockItem>? = null,
        onProgress: ((step: String, current: Int, total: Int, percent: Int) -> Unit)? = null
    ): BackupImportResult {
        val rawBytes = inputStream.readBytes()
        var effectiveBytes = rawBytes
        val format = detectFormat(rawBytes, filename)

        // If this is a generic ZIP container (not an XLSX workbook directly), extract inner payload if matching format
        if (rawBytes.size >= 4 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte()) {
            when (format) {
                DetectedFormat.TALLY_XML -> {
                    val innerXml = extractInnerZipFile(rawBytes) { it.endsWith(".xml") }
                    if (innerXml != null) effectiveBytes = innerXml
                }
                DetectedFormat.TALLY_TBK -> {
                    val innerTbk = extractInnerZipFile(rawBytes) { it.endsWith(".001") || it.contains("tbk") || it.endsWith(".900") }
                    if (innerTbk != null) effectiveBytes = innerTbk
                }
                DetectedFormat.CSV_FILE -> {
                    val innerCsv = extractInnerZipFile(rawBytes) { it.endsWith(".csv") }
                    if (innerCsv != null) effectiveBytes = innerCsv
                }
                DetectedFormat.EXCEL_WORKBOOK -> {
                    val innerXls = extractInnerZipFile(rawBytes) { it.endsWith(".xlsx") || it.endsWith(".xls") }
                    if (innerXls != null) effectiveBytes = innerXls
                }
                else -> {}
            }
        }

        return when (format) {
            DetectedFormat.TALLY_TBK -> {
                try {
                    val result = TallyBackupExtractor.extract(effectiveBytes)
                    BackupImportResult(
                        detectedFormat = format,
                        ledgersCount = result.ledgers.size,
                        vouchersCount = result.vouchers.size,
                        stockItemsCount = result.stockItems.size,
                        tallyResult = result
                    )
                } catch (e: Exception) {
                    BackupImportResult(format, 0, 0, 0, errorMessage = "Failed to extract Tally backup: ${e.message}")
                }
            }

            DetectedFormat.BUSY_BACKUP -> {
                try {
                    val result = BusyBackupExtractor.extract(effectiveBytes, filename)
                    BackupImportResult(
                        detectedFormat = format,
                        ledgersCount = result.ledgers.size,
                        vouchersCount = result.vouchers.size,
                        stockItemsCount = result.stockItems.size,
                        tallyResult = result
                    )
                } catch (e: Exception) {
                    BackupImportResult(format, 0, 0, 0, errorMessage = "Failed to extract Busy backup: ${e.message}")
                }
            }

            DetectedFormat.TALLY_XML -> {
                try {
                    val result = UniversalParser.parseTallyXml(ByteArrayInputStream(effectiveBytes))
                    BackupImportResult(
                        detectedFormat = format,
                        ledgersCount = result.ledgers.size,
                        vouchersCount = result.vouchers.size,
                        stockItemsCount = result.stockItems.size,
                        tallyResult = result
                    )
                } catch (e: Exception) {
                    BackupImportResult(format, 0, 0, 0, errorMessage = "Failed to parse Tally XML: ${e.message}")
                }
            }

            DetectedFormat.EXCEL_WORKBOOK, DetectedFormat.CSV_FILE -> {
                try {
                    val result = VoucherExcelImporter.parse(
                        inputStream = ByteArrayInputStream(effectiveBytes),
                        context = context,
                        companyId = companyId,
                        existingItemsMap = existingItemsMap,
                        onProgress = onProgress
                    )
                    val sCount = (result as? VoucherExcelImporter.ImportResult.SaleResult)?.vouchers?.size ?: 0
                    val pCount = (result as? VoucherExcelImporter.ImportResult.PurchaseResult)?.vouchers?.size ?: 0
                    val payCount = (result as? VoucherExcelImporter.ImportResult.PaymentResult)?.vouchers?.size ?: 0
                    val recCount = (result as? VoucherExcelImporter.ImportResult.ReceiptResult)?.vouchers?.size ?: 0
                    val totalVch = sCount + pCount + payCount + recCount
                    BackupImportResult(
                        detectedFormat = format,
                        ledgersCount = totalVch,
                        vouchersCount = totalVch,
                        stockItemsCount = 0,
                        excelResult = result
                    )
                } catch (e: Exception) {
                    BackupImportResult(format, 0, 0, 0, errorMessage = "Failed to parse spreadsheet: ${e.message}")
                }
            }

            DetectedFormat.UNKNOWN -> {
                // Comprehensive fallback cascade: try Excel, then XML, then Tally TBK, then Busy
                try {
                    val excelRes = VoucherExcelImporter.parse(
                        inputStream = ByteArrayInputStream(effectiveBytes),
                        context = context,
                        companyId = companyId,
                        existingItemsMap = existingItemsMap,
                        onProgress = onProgress
                    )
                    val vCount = ((excelRes as? VoucherExcelImporter.ImportResult.SaleResult)?.vouchers?.size ?: 0) +
                        ((excelRes as? VoucherExcelImporter.ImportResult.PurchaseResult)?.vouchers?.size ?: 0) +
                        ((excelRes as? VoucherExcelImporter.ImportResult.PaymentResult)?.vouchers?.size ?: 0) +
                        ((excelRes as? VoucherExcelImporter.ImportResult.ReceiptResult)?.vouchers?.size ?: 0)
                    if (vCount > 0) {
                        return BackupImportResult(DetectedFormat.EXCEL_WORKBOOK, vCount, vCount, 0, excelResult = excelRes)
                    }
                } catch (_: Exception) {}

                try {
                    val xmlRes = UniversalParser.parseTallyXml(ByteArrayInputStream(effectiveBytes))
                    if (xmlRes.ledgers.isNotEmpty() || xmlRes.vouchers.isNotEmpty()) {
                        return BackupImportResult(DetectedFormat.TALLY_XML, xmlRes.ledgers.size, xmlRes.vouchers.size, xmlRes.stockItems.size, tallyResult = xmlRes)
                    }
                } catch (_: Exception) {}

                try {
                    val tbkRes = TallyBackupExtractor.extract(effectiveBytes)
                    if (tbkRes.ledgers.isNotEmpty() || tbkRes.vouchers.isNotEmpty()) {
                        return BackupImportResult(DetectedFormat.TALLY_TBK, tbkRes.ledgers.size, tbkRes.vouchers.size, tbkRes.stockItems.size, tallyResult = tbkRes)
                    }
                } catch (_: Exception) {}

                try {
                    val busyRes = BusyBackupExtractor.extract(effectiveBytes, filename)
                    if (busyRes.ledgers.isNotEmpty() || busyRes.stockItems.isNotEmpty()) {
                        return BackupImportResult(DetectedFormat.BUSY_BACKUP, busyRes.ledgers.size, busyRes.vouchers.size, busyRes.stockItems.size, tallyResult = busyRes)
                    }
                } catch (_: Exception) {}

                BackupImportResult(
                    format, 0, 0, 0,
                    errorMessage = "Unrecognized backup file format. Supported: Tally Backup (.001 / TBK), Busy Backup (.bsh / .zip), Tally XML, Excel (.xlsx / .xls), and CSV."
                )
            }
        }
    }
}
