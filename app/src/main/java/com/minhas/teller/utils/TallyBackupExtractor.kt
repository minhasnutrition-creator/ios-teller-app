package com.minhas.teller.utils

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Direct extractor for Tally proprietary backup archives (.001, TBK900, TBK, .900).
 * Decodes the 16KB container blocks and extracts structured TLV (Tag-Length-Value)
 * master ledgers, GSTINs, addresses, account groups, and genuine double-entry transaction
 * records without requiring Tally software to be installed.
 */
object TallyBackupExtractor {

    // Magic header for Tally backup archives (TBK900 / .001)
    val TBK_MAGIC = byteArrayOf(0xF4.toByte(), 0xDD.toByte(), 0x56.toByte(), 0x36.toByte())

    val DUMMY_OR_TEMPLATE_ACCOUNTS = listOf(
        "AXIS BANK", "CENTRAL BANK OF INDIA", "CORPORATION BANK", "DENA BANK",
        "ICICI BANK", "IDBI BANK", "KOTAK MAHINDRA BANK", "ORIENTAL BANK OF COMMERCE",
        "PUNJAB NATIONAL BANK", "STATE BANK OF INDIA", "SBI BANK", "UNION BANK OF INDIA",
        "UNITED BANK OF INDIA", "UNITED BANK OF INDI", "BANK OF BARODA", "CANARA BANK",
        "BANK OF INDIA", "INDIAN BANK", "INDIAN OVERSEAS BANK", "UCO BANK", "PUNJAB & SIND BANK",
        "YES BANK", "INDUSIND BANK", "FEDERAL BANK", "SOUTH INDIAN BANK", "BANDHAN BANK",
        "HDFC BANK", "STANDARD CHARTERED BANK", "CITIBANK", "HSBC BANK", "RBL BANK",
        "DEFAULT", "VOUCHER CLASS", "PRIMARY COST CATEGORY", "MAIN LOCATION",
        "BANK ACCOUNTS", "SUNDRY DEBTORS", "SUNDRY CREDITORS", "CASH-IN-HAND",
        "SALES ACCOUNTS", "PURCHASE ACCOUNTS", "DUTIES & TAXES", "PROVISIONS",
        "RESERVES & SURPLUS", "SECURED LOANS", "UNSECURED LOANS", "CURRENT ASSETS",
        "CURRENT LIABILITIES", "DIRECT EXPENSES", "INDIRECT EXPENSES", "DIRECT INCOMES",
        "INDIRECT INCOMES", "FIXED ASSETS", "INVESTMENTS", "MISC. EXPENSES",
        "BRANCH / DIVISIONS", "SUSPENSE A/C", "GENERAL ACCOUNT", "GENERAL CUSTOMER",
        "GENERAL SUPPLIER", "PRIMARY BANK"
    )

    private val TALLY_SYSTEM_TOKENS = setOf(
        "ctra", "pymt", "rcpt", "jrnl", "c/note", "d/note", "sale", "purc",
        "dely note", "rcpt note", "rej out", "rej in", "phy stk", "stk jrnl",
        "memo", "rev jrnl", "sales ordr", "purc order", "mfg. plan", "sales plan",
        "quotation", "indent", "purc quot", "attd", "pyrl", "job wrk in ordr",
        "job wrk out ordr", "mat out", "mat in", "inr", "default", "primary cost category",
        "main location", "standard", "msys"
    )

    fun isTallyBackup(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        // If it starts with '<' or UTF-16 XML, it's XML, not TBK binary archive
        if (bytes[0] == '<'.code.toByte() || (bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() && bytes[2] == '<'.code.toByte())) {
            return false
        }
        if (bytes[0] == TBK_MAGIC[0] &&
            bytes[1] == TBK_MAGIC[1] &&
            bytes[2] == TBK_MAGIC[2] &&
            bytes[3] == TBK_MAGIC[3]) return true
        if (bytes.size >= 16) {
            val head = String(bytes.sliceArray(0 until minOf(bytes.size, 128)), Charsets.US_ASCII)
            if (head.contains("TBK900", true) || head.contains("TallyBackup", true) || head.contains("Tally.ERP", true)) {
                return true
            }
        }
        return false
    }

    /**
     * Extracts Tally company data directly from raw .001 / TBK / .900 bytes.
     */
    fun extract(inputStream: InputStream): UniversalParser.TallyImportResult {
        val bytes = inputStream.readBytes()
        return extract(bytes)
    }

    fun extract(bytes: ByteArray): UniversalParser.TallyImportResult {
        val ledgersMap = linkedMapOf<String, UniversalParser.ParsedLedger>()
        val stockItemsMap = linkedMapOf<String, UniversalParser.ParsedStockItem>()
        val vouchers = mutableListOf<UniversalParser.ParsedVoucher>()

        val len = bytes.size
        if (len < 16) {
            return UniversalParser.TallyImportResult(emptyList(), emptyList(), emptyList())
        }

        // 1. First Pass: Scan for Master Ledgers & Master Groups in Manager.900 (or before transactions)
        var currentLedgerName: String? = null
        var currentGstin: String? = null
        var currentState: String? = null
        var currentPincode: String? = null
        var currentAddress: String? = null
        var currentGroup: String? = null

        val cbPositions = mutableListOf<Pair<Int, String>>()
        var i = 0

        while (i <= len - 8) {
            if (bytes[i] == 0x02.toByte() && bytes[i + 1] == 0x10.toByte()) {
                val tag = (bytes[i + 2].toInt() and 0xFF) or ((bytes[i + 3].toInt() and 0xFF) shl 8)
                val type = (bytes[i + 4].toInt() and 0xFF) or ((bytes[i + 5].toInt() and 0xFF) shl 8)

                val isStringField = (type == 0x000F || type == 0x0F00)
                if (isStringField && i + 8 <= len) {
                    val strLen = (bytes[i + 6].toInt() and 0xFF) or ((bytes[i + 7].toInt() and 0xFF) shl 8)
                    val strStart = i + 8
                    val strEnd = strStart + strLen

                    if (strLen in 2..512 && strEnd <= len) {
                        try {
                            val rawStr = String(bytes, strStart, strLen, Charsets.UTF_16LE).trim().trim('\u0000').trim()
                            if (rawStr.isNotBlank()) {
                                when (tag) {
                                    0x01f7 -> { // Master Ledger Name
                                        if (isValidLedgerName(rawStr)) {
                                            flushPendingLedger(
                                                currentLedgerName, currentGroup, currentGstin,
                                                currentAddress, currentPincode, ledgersMap
                                            )
                                            currentLedgerName = rawStr
                                            currentGstin = null
                                            currentState = null
                                            currentPincode = null
                                            currentAddress = null
                                            currentGroup = null
                                        }
                                    }
                                    0x01fc -> { // GSTIN
                                        val g = rawStr.uppercase()
                                        if (g.matches(Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$"))) {
                                            currentGstin = g
                                        }
                                    }
                                    0x0002 -> { // Account Group
                                        if (rawStr.length in 3..50 && !isSystemToken(rawStr)) {
                                            currentGroup = rawStr
                                        }
                                    }
                                    0x05fa -> { // State
                                        if (rawStr.length in 2..40 && !isSystemToken(rawStr)) {
                                            currentState = rawStr
                                        }
                                    }
                                    0x05fb -> { // Pincode
                                        if (rawStr.matches(Regex("^[0-9]{6}$"))) {
                                            currentPincode = rawStr
                                        }
                                    }
                                    0x001d, 0x03ee -> { // Address
                                        if (rawStr.length in 4..120 && !isSystemToken(rawStr) && !rawStr.contains("0000aa")) {
                                            val cleanAddr = rawStr.replace(Regex("[^a-zA-Z0-9 ,.-]"), " ").trim()
                                            if (cleanAddr.length >= 4) {
                                                currentAddress = if (currentAddress != null) "$currentAddress, $cleanAddr" else cleanAddr
                                            }
                                        }
                                    }
                                    0x00cb -> { // Voucher Number / Transaction Record Marker
                                        if (rawStr.length in 1..40) {
                                            cbPositions.add(Pair(i, rawStr))
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                        i = strEnd - 1
                    }
                }
            }
            i++
        }

        // Flush last pending master ledger
        flushPendingLedger(
            currentLedgerName, currentGroup, currentGstin,
            currentAddress, currentPincode, ledgersMap
        )

        // Ensure base system accounts exist
        if (!ledgersMap.containsKey("Cash")) {
            ledgersMap["Cash"] = UniversalParser.ParsedLedger(name = "Cash", parentGroup = "Cash-in-hand")
        }

        // 2. Second Pass: Extract Real Transactions from TranMgr.900 records
        var vchCounter = 1
        var lastValidDateMs = 0L

        // Calculate max allowable Excel day (cannot be in the future beyond today)
        val todayEpoch = System.currentTimeMillis()
        val maxExcelDay = ((todayEpoch - (-2209075200000L)) / 86400000L).toInt()

        for (idx in cbPositions.indices) {
            val (pos, rawVNo) = cbPositions[idx]
            val nextPos = if (idx + 1 < cbPositions.size) cbPositions[idx + 1].first else minOf(len, pos + 2048)

            val recStart = maxOf(0, pos - 0x140)
            val recEnd = minOf(len, nextPos)

            // A) Decode Voucher Date (stored as Excel serial day in recStart..pos)
            var dateMs = 0L
            val dateScanStart = maxOf(0, pos - 0xC0)
            val dateScanEnd = minOf(len - 2, pos)
            val dateCounts = mutableMapOf<Int, Int>()

            var di = dateScanStart
            while (di <= dateScanEnd) {
                val u16 = readUInt16(bytes, di)
                if (u16 in 34700..maxExcelDay) {
                    dateCounts[u16] = (dateCounts[u16] ?: 0) + 1
                }
                di += 2
            }

            val bestCandidate = dateCounts.entries.maxByOrNull { it.value }
            if (bestCandidate != null && bestCandidate.value >= 2) {
                dateMs = excelDayToEpochMillis(bestCandidate.key)
                lastValidDateMs = dateMs
            } else if (lastValidDateMs != 0L) {
                dateMs = lastValidDateMs
            } else if (bestCandidate != null) {
                dateMs = excelDayToEpochMillis(bestCandidate.key)
                lastValidDateMs = dateMs
            } else {
                dateMs = if (lastValidDateMs != 0L) lastValidDateMs else System.currentTimeMillis()
            }

            // B) Decode Party Name, GSTIN, Address & Tokens in this record window
            var partyName: String? = null
            var partyGstin: String? = null
            var detectedVType: String? = null
            var targetBankName: String? = null
            val candidateStringsInWindow = mutableListOf<String>()

            var w = recStart
            while (w <= recEnd - 8) {
                if (bytes[w] == 0x02.toByte() && bytes[w + 1] == 0x10.toByte()) {
                    val wTag = (bytes[w + 2].toInt() and 0xFF) or ((bytes[w + 3].toInt() and 0xFF) shl 8)
                    val wType = (bytes[w + 4].toInt() and 0xFF) or ((bytes[w + 5].toInt() and 0xFF) shl 8)
                    if (wType == 0x000F || wType == 0x0F00) {
                        val slen = (bytes[w + 6].toInt() and 0xFF) or ((bytes[w + 7].toInt() and 0xFF) shl 8)
                        if (slen in 2..256 && w + 8 + slen <= recEnd) {
                            try {
                                val s = String(bytes, w + 8, slen, Charsets.UTF_16LE).trim().trim('\u0000').trim()
                                if (s.isNotBlank() && !isSystemToken(s) && !s.contains("0000aa")) {
                                    candidateStringsInWindow.add(s)
                                    if ((wTag == 0x00ce || wTag == 0x00d0) && partyName == null) {
                                        partyName = s
                                    } else if (wTag == 0x01fc && partyGstin == null) {
                                        partyGstin = s.uppercase()
                                    }

                                    // Check if this string explicitly names a voucher type
                                    val sLow = s.lowercase()
                                    if (sLow == "sales" || sLow == "purchase" || sLow == "payment" ||
                                        sLow == "receipt" || sLow == "contra" || sLow == "journal") {
                                        detectedVType = s.replaceFirstChar { it.uppercase() }
                                    }
                                    if (sLow.contains("bank") && targetBankName == null) {
                                        targetBankName = s
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
                w++
            }

            // If partyName wasn't explicitly tagged with 0x00ce/0x00d0, see if any string in window matches an extracted master ledger
            if (partyName == null) {
                partyName = candidateStringsInWindow.firstOrNull { cand ->
                    ledgersMap.containsKey(cand) &&
                    !cand.contains("bank", true) &&
                    !cand.contains("cash", true) &&
                    !cand.contains("sale", true) &&
                    !cand.contains("purchase", true) &&
                    !cand.contains("tax", true) &&
                    !isSystemToken(cand)
                }
            }

            // CRITICAL: If no genuine party name could be confirmed, SKIP THIS TRANSACTION!
            // Never invent a fallback debtor or hallucinate transactions.
            if (partyName == null || partyName.isBlank() || isSystemToken(partyName) ||
                partyName.uppercase() in DUMMY_OR_TEMPLATE_ACCOUNTS
            ) {
                continue
            }

            // C) Decode Double-Entry Amounts (Type 0x0009 64-bit int stored in 1/100,000 of a Rupee)
            val posAmounts = mutableListOf<Double>()
            val negAmounts = mutableListOf<Double>()
            var ai = recStart
            while (ai <= recEnd - 10) {
                if (bytes[ai] == 0x00.toByte() && bytes[ai + 1] == 0x09.toByte()) {
                    val rawVal = readInt64(bytes, ai + 2)
                    val amtRupees = rawVal / 100000.0
                    if (abs(amtRupees) in 1.0..100000000.0) {
                        if (amtRupees > 0) posAmounts.add(amtRupees) else negAmounts.add(abs(amtRupees))
                    }
                }
                ai++
            }

            // Find matching balanced pair
            var finalAmt = 0.0
            for (p in posAmounts) {
                for (n in negAmounts) {
                    if (abs(p - n) < 0.01) {
                        finalAmt = p
                        break
                    }
                }
                if (finalAmt > 0.0) break
            }
            if (finalAmt == 0.0) {
                finalAmt = posAmounts.firstOrNull() ?: negAmounts.firstOrNull() ?: 0.0
            }

            // Skip zero or dummy records
            if (finalAmt <= 0.0) continue

            // D) Assign Voucher Type & Balanced Double-Entry Ledgers
            val isBankOrFinance = rawVNo.contains("FINANCE", true) ||
                    rawVNo.contains("DEPOSIT", true) ||
                    rawVNo.contains("NEFT", true) ||
                    rawVNo.contains("BANK", true) ||
                    rawVNo.startsWith("REC", true)

            val vType: String
            val vNoFormatted: String
            val entries: List<UniversalParser.ParsedVoucherEntry>

            if (detectedVType == "Receipt" || isBankOrFinance) {
                vType = "Receipt"
                vNoFormatted = if (rawVNo.all { it.isDigit() }) rawVNo else "REC-${vchCounter++}"
                val chosenBank = targetBankName ?: ledgersMap.keys.firstOrNull { it.contains("bank", true) } ?: "Bank Account"

                // Debit Bank, Credit Party (Receipt of payment)
                entries = listOf(
                    UniversalParser.ParsedVoucherEntry(ledgerName = chosenBank, amount = finalAmt, isDebit = true),
                    UniversalParser.ParsedVoucherEntry(ledgerName = partyName, amount = finalAmt, isDebit = false)
                )

                ledgersMap.getOrPut(chosenBank) {
                    UniversalParser.ParsedLedger(name = chosenBank, parentGroup = "Bank Accounts")
                }
            } else if (detectedVType == "Payment" || rawVNo.startsWith("PAY", true)) {
                vType = "Payment"
                vNoFormatted = rawVNo
                val chosenBank = targetBankName ?: ledgersMap.keys.firstOrNull { it.contains("bank", true) } ?: "Bank Account"

                // Debit Party, Credit Bank (Payment)
                entries = listOf(
                    UniversalParser.ParsedVoucherEntry(ledgerName = partyName, amount = finalAmt, isDebit = true),
                    UniversalParser.ParsedVoucherEntry(ledgerName = chosenBank, amount = finalAmt, isDebit = false)
                )

                ledgersMap.getOrPut(chosenBank) {
                    UniversalParser.ParsedLedger(name = chosenBank, parentGroup = "Bank Accounts")
                }
            } else if (detectedVType == "Purchase" || rawVNo.startsWith("PUR", true)) {
                vType = "Purchase"
                vNoFormatted = rawVNo
                val purchaseLedger = "Purchase Accounts"

                // Debit Purchase, Credit Party
                entries = listOf(
                    UniversalParser.ParsedVoucherEntry(ledgerName = purchaseLedger, amount = finalAmt, isDebit = true),
                    UniversalParser.ParsedVoucherEntry(ledgerName = partyName, amount = finalAmt, isDebit = false)
                )

                ledgersMap.getOrPut(purchaseLedger) {
                    UniversalParser.ParsedLedger(name = purchaseLedger, parentGroup = "Purchase Accounts")
                }
            } else {
                vType = "Sales"
                vNoFormatted = rawVNo
                val salesLedger = "Sales Accounts"

                // Debit Party, Credit Sales (Sales Invoice)
                entries = listOf(
                    UniversalParser.ParsedVoucherEntry(ledgerName = partyName, amount = finalAmt, isDebit = true),
                    UniversalParser.ParsedVoucherEntry(ledgerName = salesLedger, amount = finalAmt, isDebit = false)
                )

                ledgersMap.getOrPut(salesLedger) {
                    UniversalParser.ParsedLedger(name = salesLedger, parentGroup = "Sales Accounts")
                }
            }

            // Ensure party is registered in ledgers
            ledgersMap.getOrPut(partyName) {
                UniversalParser.ParsedLedger(
                    name = partyName,
                    parentGroup = if (vType == "Purchase") "Sundry Creditors" else "Sundry Debtors",
                    gstin = partyGstin
                )
            }

            vouchers.add(
                UniversalParser.ParsedVoucher(
                    voucherNumber = vNoFormatted,
                    date = dateMs,
                    voucherType = vType,
                    partyName = partyName,
                    narration = if (isBankOrFinance) rawVNo else null,
                    entries = entries
                )
            )
        }

        // Collect all ledgers actually referenced in voucher entries
        val usedLedgerNames = mutableSetOf<String>()
        for (v in vouchers) {
            for (e in v.entries) {
                if (e.ledgerName.isNotBlank()) {
                    usedLedgerNames.add(e.ledgerName.trim().lowercase())
                }
            }
        }

        // Clean and filter extracted master ledgers:
        // 1. Strip blank or system tokens (e.g. "default", "voucher class", "inr")
        // 2. Eliminate unused template dummy banks (AXIS, SBI, etc. from Manager.900 dictionary)
        // 3. Keep real company accounts (referenced in vouchers, non-zero opening balance, or contact details)
        val cleanLedgers = ledgersMap.values.filter { pl ->
            val cleanName = pl.name.trim()
            if (cleanName.isBlank() || isSystemToken(cleanName)) return@filter false
            val upper = cleanName.uppercase()
            if (upper in DUMMY_OR_TEMPLATE_ACCOUNTS) {
                if (pl.openingBalance != 0.0) return@filter true
                return@filter usedLedgerNames.contains(cleanName.lowercase())
            }
            if (vouchers.isNotEmpty()) {
                if (pl.openingBalance != 0.0) return@filter true
                if (usedLedgerNames.contains(cleanName.lowercase())) return@filter true
                return@filter !pl.gstin.isNullOrBlank() || !pl.address.isNullOrBlank() || !pl.phoneNumber.isNullOrBlank()
            }
            if (pl.openingBalance != 0.0 || !pl.gstin.isNullOrBlank() || !pl.address.isNullOrBlank() || !pl.phoneNumber.isNullOrBlank()) {
                return@filter true
            }
            !isGenericDictionaryWord(cleanName)
        }

        return UniversalParser.TallyImportResult(
            ledgers = cleanLedgers,
            vouchers = vouchers,
            stockItems = stockItemsMap.values.toList()
        )
    }

    fun isGenericDictionaryWord(str: String): Boolean {
        val lower = str.lowercase().trim()
        if (lower.length <= 2) return true
        if (isSystemToken(lower)) return true
        val genericWords = setOf(
            "andhra pradesh", "arunachal pradesh", "assam", "bihar", "chhattisgarh", "goa", "gujarat",
            "haryana", "himachal pradesh", "jharkhand", "karnataka", "kerala", "madhya pradesh",
            "maharashtra", "manipur", "meghalaya", "mizoram", "nagaland", "odisha", "punjab",
            "rajasthan", "sikkim", "tamil nadu", "telangana", "tripura", "uttar pradesh",
            "uttarakhand", "west bengal", "delhi", "jammu & kashmir", "ladakh", "puducherry",
            "chandigarh", "primary", "secondary", "direct", "indirect", "expenses", "incomes",
            "assets", "liabilities", "duties", "taxes", "loans", "advances", "capital", "drawing",
            "branch", "divisions", "suspense", "profit & loss", "balance sheet", "trading"
        )
        return lower in genericWords
    }

    fun sanitizeName(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9 .,/&()-]"), "").replace(Regex("\\s+"), " ").trim()
    }

    private fun flushPendingLedger(
        name: String?,
        group: String?,
        gstin: String?,
        address: String?,
        pincode: String?,
        map: MutableMap<String, UniversalParser.ParsedLedger>
    ) {
        val cleanName = name?.let { sanitizeName(it) }
        if (cleanName != null && isValidLedgerName(cleanName) && !map.containsKey(cleanName)) {
            val resolvedGroup = resolveParentGroup(cleanName, group)
            map[cleanName] = UniversalParser.ParsedLedger(
                name = cleanName,
                parentGroup = resolvedGroup,
                gstin = gstin,
                address = address,
                pincode = pincode
            )
        }
    }

    fun isSystemToken(str: String): Boolean {
        val lower = str.lowercase().trim()
        return lower in TALLY_SYSTEM_TOKENS ||
                lower.startsWith("ja98b") ||
                lower.startsWith("6000") ||
                lower.startsWith("0000aa") ||
                lower.matches(Regex("^[0-9a-fA-F-]{32,}$")) ||
                lower.contains("default") ||
                lower.contains("voucher class") ||
                lower == "yes" || lower == "no"
    }

    private fun isValidLedgerName(name: String): Boolean {
        val trimmed = sanitizeName(name)
        if (trimmed.length < 2 || trimmed.length > 80) return false
        if (isSystemToken(trimmed)) return false
        // Filter out UUIDs
        if (trimmed.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))) return false
        if (trimmed.matches(Regex("^[0-9a-zA-Z]{16}$")) && !trimmed.contains(" ") && !trimmed.contains("Bank", true)) return false
        return true
    }

    private fun resolveParentGroup(ledgerName: String, declaredGroup: String?): String {
        val lowerName = ledgerName.lowercase()
        val lowerGroup = declaredGroup?.let { sanitizeName(it).lowercase() }

        if (lowerGroup != null &&
            !isSystemToken(lowerGroup) &&
            lowerGroup != "yes" && lowerGroup != "no" &&
            !lowerGroup.contains("-") &&
            lowerGroup.length in 3..50 &&
            !lowerGroup.equals(lowerName, ignoreCase = true)
        ) {
            return declaredGroup?.let { sanitizeName(it) } ?: "Sundry Debtors"
        }

        return when {
            lowerName.contains("bank") -> "Bank Accounts"
            lowerName.contains("cash") -> "Cash-in-hand"
            lowerName.contains("sale") -> "Sales Accounts"
            lowerName.contains("purchase") -> "Purchase Accounts"
            else -> "Sundry Debtors"
        }
    }

    private fun readInt64(bytes: ByteArray, offset: Int): Long {
        if (offset + 8 > bytes.size) return 0L
        var value = 0L
        for (i in 0 until 8) {
            value = value or ((bytes[offset + i].toLong() and 0xFFL) shl (i * 8))
        }
        return value
    }

    private fun readUInt16(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun excelDayToEpochMillis(excelDay: Int): Long {
        // Excel serial day 1 = 1900-01-01. Unix epoch = 1970-01-01 (day 25569).
        val daysSinceUnixEpoch = excelDay - 25569L
        return daysSinceUnixEpoch * 86400000L
    }
}
