package com.minhas.teller.data.accounting

import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.paiseToRupees
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class ParsedBankTransaction(
    val date: Date,
    val description: String,
    val amount: Double,
    val isDeposit: Boolean, // true = Credit/Deposit, false = Debit/Withdrawal
    val referenceNumber: String? = null
)

enum class ReconciliationMatchLevel {
    EXACT,          // Same date (+/- 2 days), exact amount, matching direction
    AMOUNT_PROBABLE,// Same amount (+/- 7 days buffer for cheque/NEFT clearance)
    DESCRIPTION_MATCH, // Name / Voucher # found in bank narration
    UNMATCHED
}

data class AutoReconResult(
    val bankTx: ParsedBankTransaction,
    val matchedVoucher: VoucherWithAllEntries?,
    val matchLevel: ReconciliationMatchLevel,
    val suggestedLedgerName: String? = null
)

data class FullReconciliationReport(
    val matched: List<AutoReconResult>,
    val missingInBooks: List<ParsedBankTransaction>,
    val unclearedInBank: List<VoucherWithAllEntries>,
    val bankStatementBalance: Double,
    val bookBalance: Double,
    val reconciledDifference: Double
)

object UniversalBankParser {

    private val supportedDateFormats = listOf(
        SimpleDateFormat("dd/MM/yyyy", Locale.US),
        SimpleDateFormat("dd-MM-yyyy", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("dd/MM/yy", Locale.US),
        SimpleDateFormat("dd-MM-yy", Locale.US),
        SimpleDateFormat("dd-MMM-yyyy", Locale.US),
        SimpleDateFormat("dd MMM yyyy", Locale.US),
        SimpleDateFormat("d/M/yyyy", Locale.US),
        SimpleDateFormat("d-M-yyyy", Locale.US),
        SimpleDateFormat("dd.MM.yyyy", Locale.US),
        SimpleDateFormat("MM/dd/yyyy", Locale.US),
        SimpleDateFormat("yyyy/MM/dd", Locale.US)
    )

    private val refPattern = Regex("""(?i)\b(?:UPI|NEFT|IMPS|RTGS|CHQ|REF|TXN)[/: -]*([A-Za-z0-9]{6,25})\b""")

    /**
     * Parses bank statements from diverse Indian & international banks
     * (HDFC, SBI, ICICI, Axis, PNB, Kotak, Bank of Baroda, etc.) from CSV/TSV/text input.
     */
    fun parseCsv(inputStream: InputStream): List<ParsedBankTransaction> {
        val reader = BufferedReader(InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))
        val lines = reader.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        // 1. Locate header row & detect delimiter
        var headerIndex = -1
        var headerTokens = emptyList<String>()
        var delimiter = ','

        for (i in 0 until lines.size.coerceAtMost(30)) {
            val line = lines[i]
            val detectedDelim = detectDelimiter(line)
            val tokens = splitCsvLine(line, detectedDelim).map { it.trim().lowercase() }
            val hasDate = tokens.any { it.contains("date") || it == "dt" || it == "txn date" || it == "trans date" }
            val hasDesc = tokens.any { it.contains("desc") || it.contains("narration") || it.contains("particular") || it.contains("remark") || it.contains("detail") }
            val hasAmount = tokens.any { it.contains("amount") || it.contains("debit") || it.contains("withdrawal") || it.contains("credit") || it.contains("deposit") || it == "dr" || it == "cr" }

            if (hasDate && (hasDesc || hasAmount)) {
                headerIndex = i
                headerTokens = tokens
                delimiter = detectedDelim
                break
            }
        }

        if (headerIndex == -1) return emptyList()

        val dateCol = headerTokens.indexOfFirst { it.contains("date") || it == "dt" }
        val descCol = headerTokens.indexOfFirst { it.contains("desc") || it.contains("narration") || it.contains("particular") || it.contains("remark") || it.contains("detail") }
        val refCol = headerTokens.indexOfFirst { it.contains("chq") || it.contains("cheque") || it.contains("ref") || it.contains("utr") || it.contains("trans id") || it.contains("txn id") }
        val debitCol = headerTokens.indexOfFirst { it.contains("debit") || it.contains("withdrawal") || it == "dr" || it.startsWith("dr ") || it.endsWith(" dr") || it.contains("dr.") || it.contains("paid out") }
        val creditCol = headerTokens.indexOfFirst { it.contains("credit") || it.contains("deposit") || it == "cr" || it.startsWith("cr ") || it.endsWith(" cr") || it.contains("cr.") || it.contains("received in") }
        val amountCol = headerTokens.indexOfFirst { it.contains("amount") || it.contains("txn amt") }

        val results = mutableListOf<ParsedBankTransaction>()

        for (i in (headerIndex + 1) until lines.size) {
            val line = lines[i]
            val tokens = splitCsvLine(line, delimiter)
            if (tokens.size <= dateCol.coerceAtLeast(0)) continue

            val rawDate = tokens.getOrNull(dateCol)?.trim() ?: continue
            val parsedDate = parseDate(rawDate) ?: continue
            val description = (if (descCol >= 0) tokens.getOrNull(descCol)?.trim() else "") ?: ""
            val rawRef = if (refCol >= 0) tokens.getOrNull(refCol)?.trim() else null
            val refNumber = rawRef?.takeIf { it.isNotBlank() } ?: extractReferenceFromDesc(description)

            var amount = 0.0
            var isDeposit = false

            if (debitCol >= 0 && creditCol >= 0) {
                val debitStr = cleanAmountStr(tokens.getOrNull(debitCol))
                val creditStr = cleanAmountStr(tokens.getOrNull(creditCol))
                val debitVal = debitStr.toDoubleOrNull() ?: 0.0
                val creditVal = creditStr.toDoubleOrNull() ?: 0.0

                if (creditVal > 0) {
                    amount = creditVal
                    isDeposit = true
                } else if (debitVal > 0) {
                    amount = debitVal
                    isDeposit = false
                }
            } else if (amountCol >= 0) {
                // If amount is the last declared column, unquoted numbers with Indian grouping commas (e.g., 1,50,000.00)
                // might have been split into multiple tokens; rejoin them safely.
                val rawAmountToken = if (amountCol == headerTokens.size - 1 && tokens.size > headerTokens.size) {
                    tokens.subList(amountCol, tokens.size).joinToString(",")
                } else {
                    tokens.getOrNull(amountCol) ?: ""
                }

                val isExplicitDr = rawAmountToken.contains("Dr", ignoreCase = true)
                val isExplicitCr = rawAmountToken.contains("Cr", ignoreCase = true)
                val isParenthesisNegative = rawAmountToken.trim().startsWith("(") && rawAmountToken.trim().endsWith(")")

                val rawAmtStr = cleanAmountStr(rawAmountToken)
                val parsedAmt = rawAmtStr.toDoubleOrNull() ?: 0.0

                amount = abs(parsedAmt)
                isDeposit = when {
                    isExplicitCr -> true
                    isExplicitDr -> false
                    isParenthesisNegative -> false
                    else -> parsedAmt >= 0
                }
            }

            if (amount > 0.0) {
                results.add(
                    ParsedBankTransaction(
                        date = parsedDate,
                        description = description,
                        amount = amount,
                        isDeposit = isDeposit,
                        referenceNumber = refNumber
                    )
                )
            }
        }

        return results
    }

    private fun detectDelimiter(line: String): Char {
        val commaCount = line.count { it == ',' }
        val tabCount = line.count { it == '\t' }
        val semiCount = line.count { it == ';' }
        return when {
            tabCount > commaCount && tabCount > semiCount -> '\t'
            semiCount > commaCount && semiCount > tabCount -> ';'
            else -> ','
        }
    }

    fun cleanAmountStr(raw: String?): String {
        if (raw == null) return ""
        var cleaned = raw.trim()
        val isNegative = (cleaned.startsWith("(") && cleaned.endsWith(")")) || cleaned.startsWith("-")
        if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            cleaned = cleaned.substring(1, cleaned.length - 1)
        }
        cleaned = cleaned.replace(",", "")
            .replace("\u20B9", "")
            .replace("₹", "")
            .replace("$", "")
            .replace("€", "")
            .replace("£", "")
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .replace("INR", "", ignoreCase = true)
            .replace("Cr", "", ignoreCase = true)
            .replace("Dr", "", ignoreCase = true)
            .trim()
        val digitsAndDot = cleaned.filter { it.isDigit() || it == '.' }
        return if (isNegative && digitsAndDot.isNotBlank()) "-$digitsAndDot" else digitsAndDot
    }

    fun parseDate(raw: String): Date? {
        val cleaned = raw.replace("\"", "").trim()
        for (format in supportedDateFormats) {
            try {
                return format.parse(cleaned)
            } catch (ignored: Exception) {}
        }
        return null
    }

    private fun extractReferenceFromDesc(desc: String): String? {
        val match = refPattern.find(desc)
        return match?.groupValues?.getOrNull(1)
    }

    fun splitCsvLine(line: String, delimiter: Char = ','): List<String> {
        val tokens = mutableListOf<String>()
        var sb = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when (ch) {
                '\"' -> inQuotes = !inQuotes
                delimiter -> {
                    if (inQuotes) sb.append(ch)
                    else {
                        tokens.add(sb.toString().trim())
                        sb = StringBuilder()
                    }
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }
}

object AutomatedReconciler {

    private const val THREE_DAYS_MILLIS = 3L * 24 * 60 * 60 * 1000
    private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
    private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000

    /**
     * Reconciles bank statement transactions against book vouchers for a bank ledger
     * using a high-precision 4-tier matching algorithm.
     */
    fun reconcile(
        bankTransactions: List<ParsedBankTransaction>,
        bookVouchers: List<VoucherWithAllEntries>,
        bankLedgerId: Int
    ): FullReconciliationReport {
        val matchedResults = mutableListOf<AutoReconResult>()
        val unmatchedBankTx = bankTransactions.toMutableList()
        val unmatchedBookVouchers = bookVouchers.toMutableList()

        // 1. Pass 1: EXACT MATCH (Same Amount within 0.05 + Date within +/- 3 days + matching debit/credit)
        val iter1 = unmatchedBankTx.iterator()
        while (iter1.hasNext()) {
            val bTx = iter1.next()
            val match = unmatchedBookVouchers.firstOrNull { vw ->
                val vDate = vw.voucher.date?.time ?: 0L
                val dateDiff = abs(bTx.date.time - vDate)
                val bankEntries = vw.voucherEntries.filter { it.ledgerId == bankLedgerId }
                if (bankEntries.isEmpty()) return@firstOrNull false

                // Net bank effect in voucher
                val isDebit = bankEntries.any { it.isDebit }
                val totalVoucherBankAmount = bankEntries.sumOf { it.amount }.paiseToRupees()

                // In double-entry: Bank deposit is DEBIT in books; withdrawal is CREDIT in books.
                val directionMatches = (bTx.isDeposit && isDebit) || (!bTx.isDeposit && !isDebit)
                val amountMatches = abs(totalVoucherBankAmount - bTx.amount) < 0.05

                dateDiff <= THREE_DAYS_MILLIS && directionMatches && amountMatches
            }

            if (match != null) {
                matchedResults.add(
                    AutoReconResult(
                        bankTx = bTx,
                        matchedVoucher = match,
                        matchLevel = ReconciliationMatchLevel.EXACT
                    )
                )
                iter1.remove()
                unmatchedBookVouchers.remove(match)
            }
        }

        // 2. Pass 2: REFERENCE / UTR / CHEQUE NUMBER MATCH (Within 30 days)
        val iter2 = unmatchedBankTx.iterator()
        while (iter2.hasNext()) {
            val bTx = iter2.next()
            val bRef = bTx.referenceNumber?.lowercase()
            val bDesc = bTx.description.lowercase()

            val match = unmatchedBookVouchers.firstOrNull { vw ->
                val vDate = vw.voucher.date?.time ?: 0L
                val dateDiff = abs(bTx.date.time - vDate)
                if (dateDiff > THIRTY_DAYS_MILLIS) return@firstOrNull false

                val bankEntries = vw.voucherEntries.filter { it.ledgerId == bankLedgerId }
                if (bankEntries.isEmpty()) return@firstOrNull false
                val isDebit = bankEntries.any { it.isDebit }
                val totalVoucherBankAmount = bankEntries.sumOf { it.amount }.paiseToRupees()
                val directionMatches = (bTx.isDeposit && isDebit) || (!bTx.isDeposit && !isDebit)
                val amountMatches = abs(totalVoucherBankAmount - bTx.amount) < 0.05
                if (!directionMatches || !amountMatches) return@firstOrNull false

                val vNum = vw.voucher.voucherNumber?.lowercase() ?: ""
                val vNarration = vw.voucher.narration?.lowercase() ?: ""

                val refMatches = (bRef != null && (vNum.contains(bRef) || vNarration.contains(bRef))) ||
                    (vNum.isNotBlank() && bDesc.contains(vNum))

                refMatches
            }

            if (match != null) {
                matchedResults.add(
                    AutoReconResult(
                        bankTx = bTx,
                        matchedVoucher = match,
                        matchLevel = ReconciliationMatchLevel.DESCRIPTION_MATCH
                    )
                )
                iter2.remove()
                unmatchedBookVouchers.remove(match)
            }
        }

        // 3. Pass 3: CLEARANCE WINDOW (Cheque / NEFT clearance within +/- 7 days)
        val iter3 = unmatchedBankTx.iterator()
        while (iter3.hasNext()) {
            val bTx = iter3.next()
            val match = unmatchedBookVouchers.firstOrNull { vw ->
                val vDate = vw.voucher.date?.time ?: 0L
                val dateDiff = abs(bTx.date.time - vDate)
                val bankEntries = vw.voucherEntries.filter { it.ledgerId == bankLedgerId }
                if (bankEntries.isEmpty()) return@firstOrNull false
                val isDebit = bankEntries.any { it.isDebit }
                val totalVoucherBankAmount = bankEntries.sumOf { it.amount }.paiseToRupees()
                val directionMatches = (bTx.isDeposit && isDebit) || (!bTx.isDeposit && !isDebit)
                val amountMatches = abs(totalVoucherBankAmount - bTx.amount) < 0.05

                dateDiff <= SEVEN_DAYS_MILLIS && directionMatches && amountMatches
            }

            if (match != null) {
                matchedResults.add(
                    AutoReconResult(
                        bankTx = bTx,
                        matchedVoucher = match,
                        matchLevel = ReconciliationMatchLevel.AMOUNT_PROBABLE
                    )
                )
                iter3.remove()
                unmatchedBookVouchers.remove(match)
            }
        }

        // 4. Pass 4: PROBABLE AMOUNT MATCH (Within 15 days window)
        val iter4 = unmatchedBankTx.iterator()
        while (iter4.hasNext()) {
            val bTx = iter4.next()
            val match = unmatchedBookVouchers.firstOrNull { vw ->
                val vDate = vw.voucher.date?.time ?: 0L
                val dateDiff = abs(bTx.date.time - vDate)
                val bankEntries = vw.voucherEntries.filter { it.ledgerId == bankLedgerId }
                if (bankEntries.isEmpty()) return@firstOrNull false
                val isDebit = bankEntries.any { it.isDebit }
                val totalVoucherBankAmount = bankEntries.sumOf { it.amount }.paiseToRupees()
                val directionMatches = (bTx.isDeposit && isDebit) || (!bTx.isDeposit && !isDebit)
                val amountMatches = abs(totalVoucherBankAmount - bTx.amount) < 0.05

                dateDiff <= 15L * 24 * 60 * 60 * 1000 && directionMatches && amountMatches
            }

            if (match != null) {
                matchedResults.add(
                    AutoReconResult(
                        bankTx = bTx,
                        matchedVoucher = match,
                        matchLevel = ReconciliationMatchLevel.AMOUNT_PROBABLE
                    )
                )
                iter4.remove()
                unmatchedBookVouchers.remove(match)
            }
        }

        // Calculate balances
        val totalBankDeposits = bankTransactions.filter { it.isDeposit }.sumOf { it.amount }
        val totalBankWithdrawals = bankTransactions.filter { !it.isDeposit }.sumOf { it.amount }
        val netBankBalance = totalBankDeposits - totalBankWithdrawals

        val bookDebits = bookVouchers.flatMap { it.voucherEntries }.filter { it.ledgerId == bankLedgerId && it.isDebit }.sumOf { it.amount }
        val bookCredits = bookVouchers.flatMap { it.voucherEntries }.filter { it.ledgerId == bankLedgerId && !it.isDebit }.sumOf { it.amount }
        val netBookBalance = bookDebits - bookCredits

        return FullReconciliationReport(
            matched = matchedResults,
            missingInBooks = unmatchedBankTx,
            unclearedInBank = unmatchedBookVouchers,
            bankStatementBalance = netBankBalance,
            bookBalance = netBookBalance.paiseToRupees(),
            reconciledDifference = abs(netBankBalance - netBookBalance.paiseToRupees())
        )
    }
}
