package com.minhas.teller.utils

import android.util.Xml
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

object UniversalParser {

    data class ParsedLedger(
        val name: String,
        val parentGroup: String? = null,
        val openingBalance: Double = 0.0,
        val address: String? = null,
        val pincode: String? = null,
        val gstin: String? = null,
        val phoneNumber: String? = null,
        val email: String? = null
    )

    data class ParsedVoucherEntry(
        val ledgerName: String,
        val amount: Double,
        val isDebit: Boolean
    )

    data class ParsedInventoryEntry(
        val stockItemName: String,
        val quantity: Double,
        val rate: Double,
        val totalAmount: Double,
        val hsnCode: String? = null,
        val gstRate: Double = 0.0
    )

    data class ParsedVoucher(
        val voucherNumber: String,
        val date: Long, // timestamp
        val voucherType: String, // Sales, Purchase, Payment, Receipt, Journal
        val partyName: String?,
        val narration: String? = null,
        val entries: List<ParsedVoucherEntry> = emptyList(),
        val inventoryEntries: List<ParsedInventoryEntry> = emptyList()
    )

    data class ParsedStockItem(
        val name: String,
        val unitName: String = "Nos",
        val openingQty: Double = 0.0,
        val openingVal: Double = 0.0,
        val hsnCode: String? = null,
        val gstRate: Double = 0.0
    )

    data class TallyImportResult(
        val ledgers: List<ParsedLedger>,
        val vouchers: List<ParsedVoucher>,
        val stockItems: List<ParsedStockItem>
    )

    data class TabularRow(
        val index: Int,
        val cells: List<String>
    )

    data class TabularImportResult(
        val headers: List<String>,
        val rows: List<TabularRow>
    )

    fun sanitizeXml(bytes: ByteArray): ByteArray {
        val rawText: String = if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            bytes.decodeToString(2, bytes.size, throwOnInvalidSequence = false)
        } else if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            bytes.decodeToString(2, bytes.size, throwOnInvalidSequence = false)
        } else if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes.decodeToString(3, bytes.size, throwOnInvalidSequence = false)
        } else {
            bytes.decodeToString(throwOnInvalidSequence = false)
        }

        // Replace bare & with &amp;
        val ampCleaned = rawText.replace(Regex("&(?!(amp|lt|gt|quot|apos|#\\d+|#x[0-9a-fA-F]+);)"), "&amp;")
        // Remove invalid XML control characters
        val sanitized = ampCleaned.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
        return sanitized.encodeToByteArray()
    }

    /**
     * Parses standard Tally XML files containing Masters (Ledgers, StockItems) and Vouchers.
     */
    fun parseTallyXml(inputStream: InputStream): TallyImportResult {
        val bytes = inputStream.readBytes()
        if (bytes.size >= 4 && bytes[0] == 0xF4.toByte() && bytes[1] == 0xDD.toByte() && bytes[2] == 0x56.toByte() && bytes[3] == 0x36.toByte()) {
            // Directly extract from Tally Binary Backup (.001 / TBK)
            return TallyBackupExtractor.extract(bytes)
        }

        val ledgers = mutableListOf<ParsedLedger>()
        val vouchers = mutableListOf<ParsedVoucher>()
        val stockItems = mutableListOf<ParsedStockItem>()

        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val builder = factory.newDocumentBuilder()
        val cleanBytes = sanitizeXml(bytes)
        val doc = builder.parse(ByteArrayInputStream(cleanBytes))

        val dateFormats = listOf(
            SimpleDateFormat("yyyyMMdd", Locale.ENGLISH),
            SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("d-MMM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH),
            SimpleDateFormat("d-MMM-yy", Locale.ENGLISH),
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("d-M-yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
            SimpleDateFormat("d/M/yyyy", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
            SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
        )
        var lastParsedDateMs = 0L
        fun parseDate(str: String?): Long {
            if (!str.isNullOrBlank()) {
                val trimmed = str.trim()
                for (df in dateFormats) {
                    try {
                        val d = df.parse(trimmed)
                        if (d != null) {
                            lastParsedDateMs = d.time
                            return d.time
                        }
                    } catch (_: Exception) {}
                }
            }
            return if (lastParsedDateMs != 0L) lastParsedDateMs else System.currentTimeMillis()
        }

        fun org.w3c.dom.Element.getFirstChildText(tagName: String): String? {
            val list = this.getElementsByTagName(tagName)
            for (i in 0 until list.length) {
                val node = list.item(i)
                if (node.parentNode == this) {
                    return node.textContent?.trim()
                }
            }
            return if (list.length > 0) list.item(0).textContent?.trim() else null
        }

        // 1. LEDGERs
        val ledgerNodes = doc.getElementsByTagName("LEDGER")
        for (i in 0 until ledgerNodes.length) {
            val el = ledgerNodes.item(i) as? org.w3c.dom.Element ?: continue
            val rawName = el.getAttribute("NAME").ifEmpty { el.getFirstChildText("NAME") ?: "" }
            val nameAttr = TallyBackupExtractor.sanitizeName(rawName)
            if (nameAttr.isBlank() || TallyBackupExtractor.isSystemToken(nameAttr)) continue
            val rawParent = el.getFirstChildText("PARENT")?.let { TallyBackupExtractor.sanitizeName(it) }
            val parentGroup = if (!rawParent.isNullOrBlank() && !TallyBackupExtractor.isSystemToken(rawParent)) rawParent else "Sundry Debtors"
            val openingBalance = el.getFirstChildText("OPENINGBALANCE")?.toDoubleOrNull()?.let { Math.abs(it) } ?: 0.0

            val addressNodes = el.getElementsByTagName("ADDRESS")
            val address = (0 until addressNodes.length).mapNotNull { addressNodes.item(it).textContent?.trim() }.filter { it.isNotEmpty() }.joinToString("\n").ifEmpty { null }
            val pincode = el.getFirstChildText("PINCODE")
            val gstin = el.getFirstChildText("PARTYGSTIN")
            val phone = el.getFirstChildText("LEDGERPHONE")
            val email = el.getFirstChildText("EMAIL")

            ledgers.add(
                ParsedLedger(
                    name = nameAttr,
                    parentGroup = parentGroup,
                    openingBalance = openingBalance,
                    address = address,
                    pincode = pincode,
                    gstin = gstin,
                    phoneNumber = phone,
                    email = email
                )
            )
        }

        // 2. STOCKITEMs
        val stockNodes = doc.getElementsByTagName("STOCKITEM")
        for (i in 0 until stockNodes.length) {
            val el = stockNodes.item(i) as? org.w3c.dom.Element ?: continue
            val itemName = el.getAttribute("NAME").ifEmpty { el.getFirstChildText("NAME") ?: "" }
            if (itemName.isEmpty()) continue
            val unitName = el.getFirstChildText("BASEUNITS") ?: "Nos"
            val openingQty = el.getFirstChildText("OPENINGBALANCE")?.split(" ")?.get(0)?.trim()?.toDoubleOrNull()?.let { Math.abs(it) } ?: 0.0
            val openingVal = el.getFirstChildText("OPENINGVALUE")?.toDoubleOrNull()?.let { Math.abs(it) } ?: 0.0
            val hsnCode = el.getFirstChildText("HSNCODE")
            val gstRate = el.getFirstChildText("GSTRATE")?.toDoubleOrNull() ?: 0.0

            stockItems.add(
                ParsedStockItem(
                    name = itemName,
                    unitName = unitName,
                    openingQty = openingQty,
                    openingVal = openingVal,
                    hsnCode = hsnCode,
                    gstRate = gstRate
                )
            )
        }

        // 3. VOUCHERs
        val voucherNodes = doc.getElementsByTagName("VOUCHER")
        for (i in 0 until voucherNodes.length) {
            val el = voucherNodes.item(i) as? org.w3c.dom.Element ?: continue
            val rawVchType = el.getAttribute("VCHTYPE").ifEmpty { el.getFirstChildText("VOUCHERTYPENAME") ?: "Journal" }
            val voucherType = when (rawVchType.trim().lowercase()) {
                "sales", "sale" -> "Sales"
                "purchase", "purchases" -> "Purchase"
                "payment", "payments" -> "Payment"
                "receipt", "receipts" -> "Receipt"
                "contra" -> "Contra"
                "journal" -> "Journal"
                "credit note", "creditnote", "credit_note" -> "Credit Note"
                "debit note", "debitnote", "debit_note" -> "Debit Note"
                else -> rawVchType.trim()
            }
            var voucherNumber = el.getFirstChildText("VOUCHERNUMBER") ?: ""
            if (voucherNumber.isEmpty()) {
                voucherNumber = "VCH-${vouchers.size + 1}"
            }
            val dateVal = parseDate(el.getFirstChildText("DATE"))
            val rawParty = el.getFirstChildText("PARTYNAME")
                ?: el.getFirstChildText("PARTYLEDGERNAME")
                ?: el.getFirstChildText("BASICBUYERNAME")
                ?: el.getFirstChildText("PARTYMAILINGNAME")
            val narration = el.getFirstChildText("NARRATION")

            val entries = mutableListOf<ParsedVoucherEntry>()
            val ledgerEntryNodes = el.getElementsByTagName("ALLLEDGERENTRIES.LIST")
            val legacyLedgerEntryNodes = el.getElementsByTagName("LEDGERENTRIES.LIST")
            val allLedgerLists = (0 until ledgerEntryNodes.length).map { ledgerEntryNodes.item(it) } +
                                 (0 until legacyLedgerEntryNodes.length).map { legacyLedgerEntryNodes.item(it) }
            for (node in allLedgerLists) {
                val entryEl = node as? org.w3c.dom.Element ?: continue
                val rawLedgerName = entryEl.getFirstChildText("LEDGERNAME") ?: ""
                val ledgerName = TallyBackupExtractor.sanitizeName(rawLedgerName)
                val amt = entryEl.getFirstChildText("AMOUNT")?.toDoubleOrNull() ?: 0.0
                val isDeemedPos = entryEl.getFirstChildText("ISDEEMEDPOSITIVE")
                if (ledgerName.isNotBlank() && !TallyBackupExtractor.isSystemToken(ledgerName)) {
                    val isDebit = if (isDeemedPos?.equals("Yes", ignoreCase = true) == true) true
                                  else if (isDeemedPos?.equals("No", ignoreCase = true) == true) false
                                  else amt < 0
                    entries.add(
                        ParsedVoucherEntry(
                            ledgerName = ledgerName,
                            amount = Math.abs(amt),
                            isDebit = isDebit
                        )
                    )
                }
            }

            val inventory = mutableListOf<ParsedInventoryEntry>()
            val invNodes = el.getElementsByTagName("ALLINVENTORYENTRIES.LIST")
            val legacyInvNodes = el.getElementsByTagName("INVENTORYENTRIES.LIST")
            val allInvLists = (0 until invNodes.length).map { invNodes.item(it) } +
                              (0 until legacyInvNodes.length).map { legacyInvNodes.item(it) }
            for (node in allInvLists) {
                val invEl = node as? org.w3c.dom.Element ?: continue
                val itemName = invEl.getFirstChildText("STOCKITEMNAME") ?: ""
                val rawQty = invEl.getFirstChildText("BILLEDQTY") ?: invEl.getFirstChildText("ACTUALQTY") ?: invEl.getFirstChildText("QTY")
                val cleanQty = rawQty?.trim()?.split(Regex("\\s+"))?.getOrNull(0)
                val qty = cleanQty?.toDoubleOrNull() ?: 0.0
                val rate = invEl.getFirstChildText("RATE")?.split("/")?.getOrNull(0)?.trim()?.toDoubleOrNull() ?: 0.0
                val amt = invEl.getFirstChildText("AMOUNT")?.toDoubleOrNull() ?: 0.0
                if (itemName.isNotEmpty()) {
                    inventory.add(
                        ParsedInventoryEntry(
                            stockItemName = itemName,
                            quantity = qty,
                            rate = rate,
                            totalAmount = Math.abs(amt)
                        )
                    )
                }
            }

            val resolvedParty = rawParty?.takeIf { it.isNotBlank() } ?: when (voucherType.lowercase()) {
                "sales" -> entries.firstOrNull { it.isDebit && !it.ledgerName.contains("tax", ignoreCase = true) && !it.ledgerName.contains("gst", ignoreCase = true) }?.ledgerName
                "purchase" -> entries.firstOrNull { !it.isDebit && !it.ledgerName.contains("tax", ignoreCase = true) && !it.ledgerName.contains("gst", ignoreCase = true) }?.ledgerName
                "receipt" -> entries.firstOrNull { !it.isDebit && !it.ledgerName.contains("cash", ignoreCase = true) && !it.ledgerName.contains("bank", ignoreCase = true) }?.ledgerName
                "payment" -> entries.firstOrNull { it.isDebit && !it.ledgerName.contains("cash", ignoreCase = true) && !it.ledgerName.contains("bank", ignoreCase = true) }?.ledgerName
                "credit note" -> entries.firstOrNull { !it.isDebit && !it.ledgerName.contains("tax", ignoreCase = true) && !it.ledgerName.contains("gst", ignoreCase = true) }?.ledgerName
                "debit note" -> entries.firstOrNull { it.isDebit && !it.ledgerName.contains("tax", ignoreCase = true) && !it.ledgerName.contains("gst", ignoreCase = true) }?.ledgerName
                else -> null
            } ?: entries.firstOrNull()?.ledgerName

            vouchers.add(
                ParsedVoucher(
                    voucherNumber = voucherNumber,
                    date = dateVal,
                    voucherType = voucherType,
                    partyName = resolvedParty,
                    narration = narration,
                    entries = entries,
                    inventoryEntries = inventory
                )
            )
        }

        return TallyImportResult(ledgers, vouchers, stockItems)
    }

    /**
     * Parses standard CSV files into a tabular data grid.
     */
    fun parseCsv(inputStream: InputStream): TabularImportResult {
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line = reader.readLine()
        val headers = mutableListOf<String>()
        val rows = mutableListOf<TabularRow>()

        if (line != null) {
            headers.addAll(parseCsvLine(line))
        }

        var index = 0
        line = reader.readLine()
        while (line != null) {
            val cells = parseCsvLine(line)
            if (cells.isNotEmpty() && cells.any { it.isNotBlank() }) {
                rows.add(TabularRow(index++, cells))
            }
            line = reader.readLine()
        }
        return TabularImportResult(headers, rows)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var curVal = StringBuilder()
        var inQuotes = false
        for (ch in line.toCharArray()) {
            if (inQuotes) {
                if (ch == '\"') {
                    inQuotes = false
                } else {
                    curVal.append(ch)
                }
            } else {
                if (ch == '\"') {
                    inQuotes = true
                } else if (ch == ',') {
                    result.add(curVal.toString().trim())
                    curVal = StringBuilder()
                } else {
                    curVal.append(ch)
                }
            }
        }
        result.add(curVal.toString().trim())
        return result
    }

    /**
     * Parses Excel (.xlsx) files using Apache POI.
     */
    fun parseExcel(inputStream: InputStream): TabularImportResult {
        val workbook = XSSFWorkbook(inputStream)
        val sheet = workbook.getSheetAt(0) ?: throw IllegalArgumentException("No sheets found in Excel file")
        val headers = mutableListOf<String>()
        val rows = mutableListOf<TabularRow>()

        val firstRow = sheet.getRow(0) ?: throw IllegalArgumentException("Excel sheet has no rows")
        val numCols = firstRow.lastCellNum.toInt()

        for (c in 0 until numCols) {
            val cell = firstRow.getCell(c)
            headers.add(cell?.toString()?.trim() ?: "Col$c")
        }

        var index = 0
        for (r in 1..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            val cells = mutableListOf<String>()
            var rowHasValue = false
            for (c in 0 until numCols) {
                val cell = row.getCell(c)
                val valStr = cell?.toString()?.trim() ?: ""
                if (valStr.isNotEmpty()) rowHasValue = true
                cells.add(valStr)
            }
            if (rowHasValue) {
                rows.add(TabularRow(index++, cells))
            }
        }
        workbook.close()
        return TabularImportResult(headers, rows)
    }

    /**
     * Uses heuristics (Regex patterns) to suggest mappings from input headers to database target entities.
     */
    fun detectColumns(headers: List<String>): Map<String, Pair<String, String>> {
        val mapping = mutableMapOf<String, Pair<String, String>>() // Header -> Pair(Entity, Field)

        val nameRegex = Regex("(?i).*(name|particulars|ledger|party|customer|vendor).*")
        val balanceRegex = Regex("(?i).*(bal|opening|closing|op.*bal|cl.*bal).*")
        val phoneRegex = Regex("(?i).*(phone|mobile|tel|contact).*")
        val emailRegex = Regex("(?i).*(email|mail).*")
        val addressRegex = Regex("(?i).*(address|add|loc).*")
        val gstinRegex = Regex("(?i).*(gst|gstin|tax.*no).*")
        val amountRegex = Regex("(?i).*(amt|amount|total|val|value|debit|credit|dr|cr).*")
        val dateRegex = Regex("(?i).*(date|dt|voucher.*date).*")
        val vchNoRegex = Regex("(?i).*(vch|voucher.*no|inv|invoice.*no|number).*")
        val hsnRegex = Regex("(?i).*(hsn|hsn.*code|sac|sac.*code).*")

        for (header in headers) {
            val clean = header.trim()
            when {
                gstinRegex.matches(clean) -> mapping[clean] = Pair("Ledger", "gstin")
                phoneRegex.matches(clean) -> mapping[clean] = Pair("Ledger", "phoneNumber")
                emailRegex.matches(clean) -> mapping[clean] = Pair("Ledger", "email")
                addressRegex.matches(clean) -> mapping[clean] = Pair("Ledger", "address")
                balanceRegex.matches(clean) -> mapping[clean] = Pair("Ledger", "openingBalance")
                nameRegex.matches(clean) -> {
                    if (headers.any { hsnRegex.matches(it) }) {
                        mapping[clean] = Pair("StockItem", "name")
                    } else {
                        mapping[clean] = Pair("Ledger", "name")
                    }
                }
                amountRegex.matches(clean) -> mapping[clean] = Pair("VoucherEntry", "amount")
                dateRegex.matches(clean) -> mapping[clean] = Pair("Voucher", "date")
                vchNoRegex.matches(clean) -> mapping[clean] = Pair("Voucher", "voucherNumber")
                hsnRegex.matches(clean) -> mapping[clean] = Pair("StockItem", "hsnCode")
            }
        }
        return mapping
    }
}
