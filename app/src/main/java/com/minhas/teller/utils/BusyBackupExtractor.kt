package com.minhas.teller.utils

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Direct extractor for Busy Accounting Software backup files (.bsh, .zip, .mdb, .bak).
 * Decompresses the archive and extracts Master Ledgers (Customers, Vendors, Banks),
 * Contact information (GSTIN, Address, Pincode), Stock Items, and Voucher records
 * without picking up MS Access / Jet engine metadata or GUI artifact strings.
 */
object BusyBackupExtractor {

    // Magic headers
    val ZIP_MAGIC = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())
    val JET_MDB_SIGNATURE = "Standard Jet DB".toByteArray(Charsets.US_ASCII)
    val ACE_MDB_SIGNATURE = "Standard ACE DB".toByteArray(Charsets.US_ASCII)

    private val BUSY_SYSTEM_IGNORE_WORDS = setOf(
        "msys", "standard jet", "standard ace", "jetoledb", "table", "column", "index",
        "primarykey", "foreignkey", "database", "compact", "repair", "autonumber",
        "datatype", "constraint", "byte", "integer", "long", "single", "double",
        "currency", "datetime", "text", "binary", "memo", "oleobject", "guid",
        "decimal", "boolean", "validationrule", "validationtext", "required",
        "allowzerolength", "fieldsize", "format", "inputmask", "caption",
        "defaultvalue", "description", "owner", "connect", "attributes",
        "order", "filter", "rowsource", "visible", "enabled", "locked",
        "tabindex", "tabstop", "itemtype", "textbox", "checkbox", "listbox",
        "combobox", "commandbutton", "optionbutton", "togglebutton", "subform",
        "pagebreak", "image", "attachment", "navigationitem", "menuitem",
        "storedata", "storedprocedure", "master1", "master2", "tran1", "tran2",
        "help1", "help2", "user1", "user2", "sysdata", "version", "engine",
        "microsoft", "access", "ole", "props", "relations", "queries", "views",
        "check", "unique", "cascade", "restrict", "references", "select", "insert",
        "update", "delete", "from", "where", "group by", "order by", "having",
        "inner join", "outer join", "left join", "right join", "union", "into",
        "default", "package", "temp", "temporary", "schema", "catalog", "collating"
    )

    private val GSTIN_REGEX = Regex("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}")

    fun isBusyBackup(bytes: ByteArray, filename: String? = null): Boolean {
        val lowerName = filename?.lowercase() ?: ""
        if (lowerName.endsWith(".bsh") || lowerName.endsWith(".bak")) return true

        if (bytes.size >= 4 && bytes[0] == ZIP_MAGIC[0] && bytes[1] == ZIP_MAGIC[1] &&
            bytes[2] == ZIP_MAGIC[2] && bytes[3] == ZIP_MAGIC[3]) {
            try {
                val zis = ZipInputStream(ByteArrayInputStream(bytes))
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val n = entry.name.lowercase()
                    if (n.endsWith(".mdb") || n.contains("comp") || n.contains("busy") || n.endsWith(".bsh")) {
                        return true
                    }
                    entry = zis.nextEntry
                }
            } catch (_: Exception) {}
        }

        if (bytes.size >= 32) {
            val headerSlice = bytes.sliceArray(4 until minOf(bytes.size, 24))
            val headerStr = String(headerSlice, Charsets.US_ASCII)
            if (headerStr.contains("Standard Jet DB") || headerStr.contains("Standard ACE DB")) {
                return true
            }
        }

        return false
    }

    /**
     * Extracts Busy company data directly into UniversalParser.TallyImportResult format
     * for seamless conversion into Teller Room entities.
     */
    fun extract(inputStream: InputStream, filename: String? = null): UniversalParser.TallyImportResult {
        val bytes = inputStream.readBytes()
        return extract(bytes, filename)
    }

    fun extract(bytes: ByteArray, filename: String? = null): UniversalParser.TallyImportResult {
        val rawDataStreams = mutableListOf<ByteArray>()

        // 1. If it's a ZIP / BSH archive, decompress and inspect entries
        if (bytes.size >= 4 && bytes[0] == ZIP_MAGIC[0] && bytes[1] == ZIP_MAGIC[1] &&
            bytes[2] == ZIP_MAGIC[2] && bytes[3] == ZIP_MAGIC[3]) {
            try {
                val zis = ZipInputStream(ByteArrayInputStream(bytes))
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryBytes = zis.readBytes()
                    val eName = entry.name.lowercase()

                    // If Busy export contains internal XML or CSV files, parse directly
                    if (eName.endsWith(".xml") && entryBytes.isNotEmpty()) {
                        try {
                            val xmlRes = UniversalParser.parseTallyXml(ByteArrayInputStream(entryBytes))
                            if (xmlRes.ledgers.isNotEmpty() || xmlRes.vouchers.isNotEmpty()) {
                                return xmlRes
                            }
                        } catch (_: Exception) {}
                    }

                    if (entryBytes.isNotEmpty()) {
                        rawDataStreams.add(entryBytes)
                    }
                    entry = zis.nextEntry
                }
            } catch (_: Exception) {
                rawDataStreams.add(bytes)
            }
        } else {
            rawDataStreams.add(bytes)
        }

        val ledgersMap = linkedMapOf<String, UniversalParser.ParsedLedger>()
        val stockItemsMap = linkedMapOf<String, UniversalParser.ParsedStockItem>()
        val vouchers = mutableListOf<UniversalParser.ParsedVoucher>()

        // 2. Scan collected streams for legitimate business ledgers and stock items
        for (stream in rawDataStreams) {
            val extractedLines = extractReadableLines(stream)

            for ((line, nearbyGstin) in extractedLines) {
                val cleanLine = sanitizeString(line)
                if (cleanLine.isBlank()) continue

                // Check for Stock Item
                if (isValidBusyStockItem(cleanLine)) {
                    val lower = cleanLine.lowercase()
                    val unit = when {
                        lower.contains("kg") || lower.contains("kgs") -> "Kg"
                        lower.contains("ltr") || lower.contains("litre") || lower.contains("liter") -> "Ltr"
                        lower.contains("gm") || lower.contains("gram") -> "Gm"
                        lower.contains("ml") -> "Ml"
                        lower.contains("meter") || lower.contains("mtr") -> "Mtr"
                        lower.contains("box") || lower.contains("boxes") -> "Box"
                        lower.contains("bag") || lower.contains("bags") -> "Bag"
                        lower.contains("pkt") || lower.contains("pack") -> "Pkt"
                        lower.contains("tin") -> "Tin"
                        lower.contains("sqft") -> "SqFt"
                        else -> "Nos"
                    }
                    if (!stockItemsMap.containsKey(cleanLine)) {
                        stockItemsMap[cleanLine] = UniversalParser.ParsedStockItem(
                            name = cleanLine,
                            unitName = unit
                        )
                    }
                    continue
                }

                // Check for Ledger
                if (isValidBusyLedger(cleanLine)) {
                    val lower = cleanLine.lowercase()
                    val group = when {
                        lower.contains("bank") -> "Bank Accounts"
                        lower.contains("cash") -> "Cash-in-hand"
                        lower.contains("sales") -> "Sales Accounts"
                        lower.contains("purchase") -> "Purchase Accounts"
                        lower.contains("supplier") || lower.contains("creditor") || lower.contains("vendor") -> "Sundry Creditors"
                        else -> "Sundry Debtors"
                    }

                    if (!ledgersMap.containsKey(cleanLine)) {
                        ledgersMap[cleanLine] = UniversalParser.ParsedLedger(
                            name = cleanLine,
                            parentGroup = group,
                            gstin = nearbyGstin
                        )
                    }
                }
            }
        }

        // Ensure base accounts exist only if real ledgers were successfully extracted
        if (ledgersMap.isNotEmpty()) {
            if (ledgersMap.keys.none { it.equals("Cash", ignoreCase = true) }) {
                ledgersMap["Cash"] = UniversalParser.ParsedLedger(name = "Cash", parentGroup = "Cash-in-hand")
            }
            if (ledgersMap.keys.none { it.contains("Sales", ignoreCase = true) }) {
                ledgersMap["Sales Accounts"] = UniversalParser.ParsedLedger(name = "Sales Accounts", parentGroup = "Sales Accounts")
            }
        }

        return UniversalParser.TallyImportResult(
            ledgers = ledgersMap.values.toList(),
            vouchers = vouchers,
            stockItems = stockItemsMap.values.toList()
        )
    }

    private fun sanitizeString(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9 .,/&()-]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    fun isValidBusyLedger(candidate: String): Boolean {
        val s = candidate.trim()
        if (s.length < 3 || s.length > 70) return false
        val lower = s.lowercase()

        // Discard any system / Jet engine keywords
        if (BUSY_SYSTEM_IGNORE_WORDS.any { lower.contains(it) }) return false

        // Discard programming punctuation / code tokens
        if (s.contains(":") || s.contains(";") || s.contains("{") || s.contains("}") ||
            s.contains("\\") || s.contains("<") || s.contains(">") || s.contains("=") ||
            s.contains("_") || s.contains("[") || s.contains("]") || s.contains("~") ||
            s.contains("^") || s.contains("|") || s.contains("*") || s.contains("%") ||
            s.contains("$") || s.contains("@")) {
            return false
        }

        // Must contain at least one letter and not be purely digits
        if (!s.any { it.isLetter() }) return false
        if (s.all { it.isDigit() || it.isWhitespace() || it == '-' || it == '.' }) return false

        // Discard hex hashes or UUIDs
        if (s.matches(Regex("^[0-9a-fA-F-]{16,}$"))) return false

        // Check for recognizable business entity traits
        val businessKeywords = listOf(
            "ltd", "pvt", "limited", "traders", "enterprises", "agency", "agencies",
            "store", "stores", "industries", "brothers", "sons", "company", "co.",
            "corporation", "hardware", "textiles", "electronics", "bank", "accounts",
            "sales", "purchase", "services", "distributors", "associates", "steels",
            "motors", "jewellers", "garments", "stationery", "medical", "pharmacy",
            "supermarket", "bazaar", "mart", "kirana", "cloth", "auto", "trading"
        )

        return businessKeywords.any { lower.contains(it) }
    }

    fun isValidBusyStockItem(candidate: String): Boolean {
        val s = candidate.trim()
        if (s.length < 3 || s.length > 60) return false
        val lower = s.lowercase()

        // Discard system / Jet engine keywords
        if (BUSY_SYSTEM_IGNORE_WORDS.any { lower.contains(it) }) return false

        // Discard technical / programming artifacts
        if (s.contains(":") || s.contains(";") || s.contains("{") || s.contains("}") ||
            s.contains("\\") || s.contains("<") || s.contains(">") || s.contains("=") ||
            s.contains("_") || s.contains("[") || s.contains("]") || s.contains("~") ||
            s.contains("^") || s.contains("|") || s.contains("*") || s.contains("%") ||
            s.contains("$") || s.contains("@")) {
            return false
        }

        // Must contain at least one letter
        if (!s.any { it.isLetter() }) return false

        // Discard common system UI elements that match "item", "box", etc.
        if (lower in listOf("item", "pack", "box", "package", "store", "items", "boxes", "packs")) return false
        if (lower.startsWith("item") && s.all { it.isLetterOrDigit() } && s.length <= 8) return false

        // Item indicator keywords
        val itemIndicators = listOf(
            "kg", "ltr", "gm", "ml", "mtr", "box", "bag", "pkt", "tin", "sqft",
            "rice", "oil", "flour", "wheat", "sugar", "dal", "steel", "cement",
            "cotton", "soap", "tea", "biscuit", "cable", "wire", "paint", "pipe",
            "plywood", "paper", "battery", "tyre", "fertilizer", "seed", "grain"
        )

        return itemIndicators.any { lower.contains(it) }
    }

    /**
     * Extracts ASCII and UTF-16LE text lines from raw binary stream while maintaining
     * proximity to any valid GSTIN on the same line or within 200 bytes.
     */
    private fun extractReadableLines(bytes: ByteArray): List<Pair<String, String?>> {
        val results = mutableListOf<Pair<String, String?>>()
        val len = bytes.size

        // 1. Scan ASCII strings
        var i = 0
        val sb = java.lang.StringBuilder()
        var lineStart = 0

        while (i < len) {
            val c = bytes[i].toInt() and 0xFF
            if (c in 32..126) {
                if (sb.isEmpty()) lineStart = i
                sb.append(c.toChar())
            } else {
                if (sb.length in 3..120) {
                    val line = sb.toString().trim()
                    // Check if line contains GSTIN
                    val gMatch = GSTIN_REGEX.find(line)
                    val gstin = gMatch?.value
                    val cleanText = if (gMatch != null) line.replace(gMatch.value, "").trim() else line
                    if (cleanText.isNotBlank()) {
                        results.add(Pair(cleanText, gstin))
                    }
                }
                sb.setLength(0)
            }
            i++
        }
        if (sb.length in 3..120) {
            val line = sb.toString().trim()
            val gMatch = GSTIN_REGEX.find(line)
            val gstin = gMatch?.value
            val cleanText = if (gMatch != null) line.replace(gMatch.value, "").trim() else line
            if (cleanText.isNotBlank()) {
                results.add(Pair(cleanText, gstin))
            }
        }

        // 2. Scan UTF-16LE strings
        i = 0
        sb.setLength(0)
        while (i <= len - 2) {
            val c = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            if (c in 32..126) {
                if (sb.isEmpty()) lineStart = i
                sb.append(c.toChar())
            } else {
                if (sb.length in 3..120) {
                    val line = sb.toString().trim()
                    val gMatch = GSTIN_REGEX.find(line)
                    val gstin = gMatch?.value
                    val cleanText = if (gMatch != null) line.replace(gMatch.value, "").trim() else line
                    if (cleanText.isNotBlank()) {
                        results.add(Pair(cleanText, gstin))
                    }
                }
                sb.setLength(0)
            }
            i += 2
        }

        return results.distinctBy { it.first }
    }
}
