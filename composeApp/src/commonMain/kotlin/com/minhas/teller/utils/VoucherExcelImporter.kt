package com.minhas.teller.utils

import android.content.Context
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * Parses Teller-format voucher Excel files and returns strongly-typed import rows.
 *
 * Supports four voucher types:
 *   - Sale     (two sheets: Sale_Vouchers + Sale_Items)
 *   - Purchase (two sheets: Purchase_Vouchers + Purchase_Items)
 *   - Payment  (one sheet: Payment_Vouchers)
 *   - Receipt  (one sheet: Receipt_Vouchers)
 *
 * Every field is validated; errors are collected per-row so the user gets a
 * complete list of problems before the import is aborted.
 */
object VoucherExcelImporter {

    // ──────────────────────────────────────────────────────────────────────────
    //  Public Result Types
    // ──────────────────────────────────────────────────────────────────────────

    enum class ImportType { SALE, PURCHASE, PAYMENT, RECEIPT, UNKNOWN }

    data class ImportError(
        val sheet: String,
        val row: Int,       // 1-based row number as the user sees it
        val column: String,
        val message: String
    ) {
        override fun toString() = "[$sheet] Row $row, '$column': $message"
    }

    data class ParsedSaleVoucher(
        val voucherNumber: String,
        val date: Date,
        val partyName: String,
        val placeOfSupply: String?,
        val isIGST: Boolean,
        val isFinanced: Boolean,
        val financedBy: String? = null,
        val narration: String?,
        val cashLedger: String?,
        val cashAmount: Double,
        val roundOff: Double,
        val items: List<ParsedSaleItem> = emptyList()
    )

    data class ParsedSaleItem(
        val voucherNumber: String,
        val itemName: String,
        val category: String? = null,
        val quantity: Double,
        val unit: String,
        val rate: Double,
        val hsnCode: String?,
        val gstRate: Double,
        val discount: Double
    )

    data class ParsedPurchaseVoucher(
        val voucherNumber: String,
        val date: Date,
        val partyName: String,
        val supplierInvNo: String?,
        val placeOfSupply: String?,
        val isIGST: Boolean,
        val reverseCharge: Boolean,
        val narration: String?,
        val cashLedger: String?,
        val cashAmount: Double,
        val items: List<ParsedPurchaseItem> = emptyList()
    )

    data class ParsedPurchaseItem(
        val voucherNumber: String,
        val itemName: String,
        val category: String? = null,
        val quantity: Double,
        val unit: String,
        val rate: Double,
        val hsnCode: String?,
        val gstRate: Double,
        val discount: Double
    )

    data class ParsedPaymentVoucher(
        val voucherNumber: String,
        val date: Date,
        val payFromLedger: String,
        val payToLedger: String,
        val amount: Double,
        val isFinanced: Boolean,
        val financedBy: String?,
        val narration: String?,
        val billReference: String?,
        val chequeNo: String?
    )

    data class ParsedReceiptVoucher(
        val voucherNumber: String,
        val date: Date,
        val receiveInLedger: String,
        val receivedFrom: String,
        val amount: Double,
        val isFinanced: Boolean,
        val financedBy: String?,
        val narration: String?,
        val billReference: String?,
        val chequeNo: String?
    )

    sealed class ImportResult {
        abstract val unresolvedItems: List<String>
        abstract val missingColumns: List<String>

        data class SaleResult(
            val vouchers: List<ParsedSaleVoucher>,
            val errors: List<ImportError>,
            override val unresolvedItems: List<String> = emptyList(),
            override val missingColumns: List<String> = emptyList()
        ) : ImportResult()

        data class PurchaseResult(
            val vouchers: List<ParsedPurchaseVoucher>,
            val errors: List<ImportError>,
            override val unresolvedItems: List<String> = emptyList(),
            override val missingColumns: List<String> = emptyList()
        ) : ImportResult()

        data class PaymentResult(
            val vouchers: List<ParsedPaymentVoucher>,
            val errors: List<ImportError>,
            override val unresolvedItems: List<String> = emptyList(),
            override val missingColumns: List<String> = emptyList()
        ) : ImportResult()

        data class ReceiptResult(
            val vouchers: List<ParsedReceiptVoucher>,
            val errors: List<ImportError>,
            override val unresolvedItems: List<String> = emptyList(),
            override val missingColumns: List<String> = emptyList()
        ) : ImportResult()

        data class UnknownFormat(
            val errors: List<ImportError>,
            override val unresolvedItems: List<String> = emptyList(),
            override val missingColumns: List<String> = emptyList()
        ) : ImportResult()

        val hasErrors: Boolean
            get() = when (this) {
                is SaleResult -> errors.isNotEmpty()
                is PurchaseResult -> errors.isNotEmpty()
                is PaymentResult -> errors.isNotEmpty()
                is ReceiptResult -> errors.isNotEmpty()
                is UnknownFormat -> true
            }

        val errorMessages: List<String>
            get() = when (this) {
                is SaleResult -> errors.map { it.toString() }
                is PurchaseResult -> errors.map { it.toString() }
                is PaymentResult -> errors.map { it.toString() }
                is ReceiptResult -> errors.map { it.toString() }
                is UnknownFormat -> errors.map { it.toString() }
            }

        val detectedType: ImportType
            get() = when (this) {
                is SaleResult -> ImportType.SALE
                is PurchaseResult -> ImportType.PURCHASE
                is PaymentResult -> ImportType.PAYMENT
                is ReceiptResult -> ImportType.RECEIPT
                is UnknownFormat -> ImportType.UNKNOWN
            }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Public Entry Point & Smart HSN/Tax Resolution
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Resolves the HSN code and GST rate for a line item.
     * Searches itemName, category, and "$category $itemName" in order.
     * Returns Pair(hsnCode, gstRate) or null if not understood and no explicit HSN/tax given.
     */
    fun resolveHsnAndTax(
        itemName: String,
        category: String?,
        explicitHsn: String?,
        explicitGst: Double?,
        context: Context? = null,
        companyId: Int? = null,
        existingItemsMap: Map<String, com.minhas.teller.data.entities.StockItem>? = null
    ): Pair<String?, Double>? {
        // 1. If explicit HSN and explicit GST are provided, accept them directly
        if (!explicitHsn.isNullOrBlank() && explicitGst != null && explicitGst >= 0.0) {
            return Pair(explicitHsn.trim(), explicitGst)
        }

        // 2. Check if item already exists in company database (via fast in-memory map)
        val cleanName = itemName.trim().lowercase()
        val existing = existingItemsMap?.get(cleanName)
        if (existing != null && !existing.hsnCode.isNullOrBlank()) {
            val hsn = explicitHsn?.takeIf { it.isNotBlank() } ?: existing.hsnCode
            val gst = if (explicitGst != null && explicitGst > 0.0) explicitGst else existing.gstRate
            return Pair(hsn, gst)
        }

        // 3. Match against cached offline HSN directory + brand/category aliases (HsnCommonItems)
        val entries: List<com.minhas.teller.data.entities.HsnEntry> = if (context != null) {
            try {
                com.minhas.teller.utils.HsnPresetData.getEntries(context)
            } catch (_: Throwable) {
                com.minhas.teller.utils.HsnPresetData.getFallbackEntries()
            }
        } else {
            com.minhas.teller.utils.HsnPresetData.getFallbackEntries()
        }

        val cleanCat = category?.trim()?.takeIf {
            it.isNotBlank() &&
            !it.equals("General Inventory", ignoreCase = true) &&
            !it.equals("General", ignoreCase = true) &&
            !it.equals("Inventory", ignoreCase = true)
        }
        val cleanCatLower = cleanCat?.lowercase()
        val candidates = mutableListOf<String>()
        val combined = if (cleanCat != null && itemName.isNotBlank()) "$cleanCat ${itemName.trim()}" else null
        if (combined != null && !combined.equals("Dairy Milk", ignoreCase = true)) {
            candidates.add(combined)
        }
        if (itemName.isNotBlank()) candidates.add(itemName.trim())
        if (combined != null && combined.equals("Dairy Milk", ignoreCase = true)) {
            candidates.add(combined)
        }
        if (cleanCat != null) candidates.add(cleanCat)

        var bestMatch: com.minhas.teller.data.entities.HsnEntry? = null
        var bestScore = 0

        val itemLower = itemName.lowercase().trim()

        for (candidate in candidates) {
            val cLower = candidate.lowercase()
            for (entry in entries) {
                var score = com.minhas.teller.data.repositories.HSNSyncRepository.scoreHsnMatch(cLower, entry)
                if (cleanCatLower != null) {
                    val entryDescLower = entry.description.lowercase()
                    val entryCatLower = entry.category.lowercase()
                    val catMatches = com.minhas.teller.data.repositories.HSNSyncRepository.wholeWordContains(entryDescLower, cleanCatLower) ||
                            (entryCatLower.isNotBlank() && (com.minhas.teller.data.repositories.HSNSyncRepository.wholeWordContains(entryCatLower, cleanCatLower) || com.minhas.teller.data.repositories.HSNSyncRepository.wholeWordContains(cleanCatLower, entryCatLower)))
                    if (catMatches) {
                        score += 20_000
                    }
                }
                if (itemLower.isNotBlank()) {
                    val entryKwLower = entry.keywords.lowercase()
                    val entryDescLower = entry.description.lowercase()
                    if (com.minhas.teller.data.repositories.HSNSyncRepository.wholeWordContains(entryKwLower, itemLower) ||
                        com.minhas.teller.data.repositories.HSNSyncRepository.wholeWordContains(entryDescLower, itemLower)) {
                        score += 30_000
                    }
                }
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = entry
                }
            }
            if (bestScore >= com.minhas.teller.data.repositories.HSNSyncRepository.CONFIDENT_MIN_SCORE) {
                break
            }
        }

        val itemWordsCount = itemLower.split(Regex("\\s+")).count { it.length >= 2 }
        val needsFuzzyCheck = bestMatch == null ||
            bestScore < com.minhas.teller.data.repositories.HSNSyncRepository.CONFIDENT_MIN_SCORE ||
            (itemWordsCount >= 2 && bestScore < if (cleanCatLower != null) 44_000 else 24_000)
        val fuzzyCandidate = if (needsFuzzyCheck) {
            com.minhas.teller.utils.HsnFuzzyMatcher.findConfidentAutoMatch(itemName, cleanCat, entries)
        } else null

        val resolvedEntry = when {
            bestMatch != null && bestScore >= 24_000 -> bestMatch
            fuzzyCandidate != null -> fuzzyCandidate
            bestMatch != null && bestScore >= com.minhas.teller.data.repositories.HSNSyncRepository.CONFIDENT_MIN_SCORE -> bestMatch
            else -> null
        }

        if (resolvedEntry != null) {
            val hsn = explicitHsn?.takeIf { it.isNotBlank() } ?: resolvedEntry.hsnCode
            val gst = if (explicitGst != null && explicitGst > 0.0) explicitGst else resolvedEntry.gstRate
            return Pair(hsn, gst)
        }

        // 4. If explicit HSN was supplied without GST
        if (!explicitHsn.isNullOrBlank()) {
            return Pair(explicitHsn.trim(), explicitGst ?: 0.0)
        }

        // Not understood
        return null
    }

    fun parse(
        inputStream: InputStream,
        context: Context? = null,
        companyId: Int? = null,
        existingItemsMap: Map<String, com.minhas.teller.data.entities.StockItem>? = null,
        forcedType: ImportType? = null,
        onProgress: ((step: String, current: Int, total: Int, percent: Int) -> Unit)? = null
    ): ImportResult {
        onProgress?.invoke("Reading file data into memory...", 0, 100, 15)
        val bytes = inputStream.readBytes()
        onProgress?.invoke("Opening Excel workbook structure...", 0, 100, 25)
        val wb: Workbook = try {
            WorkbookFactory.create(ByteArrayInputStream(bytes))
        } catch (_: Throwable) {
            try {
                onProgress?.invoke("Trying CSV parser...", 0, 100, 30)
                csvToWorkbook(bytes)
            } catch (e: Throwable) {
                return ImportResult.UnknownFormat(
                    listOf(ImportError("File", 0, "-", "Could not open Excel or CSV file: ${e.message}"))
                )
            }
        }

        if (forcedType != null) {
            return when (forcedType) {
                ImportType.SALE -> parseSale(wb, context, companyId, existingItemsMap, onProgress)
                ImportType.PURCHASE -> parsePurchase(wb, context, companyId, existingItemsMap, onProgress)
                ImportType.PAYMENT -> parsePayment(wb, onProgress)
                ImportType.RECEIPT -> parseReceipt(wb, onProgress)
                ImportType.UNKNOWN -> ImportResult.UnknownFormat(emptyList())
            }.also { wb.close() }
        }

        val sheetNames = (0 until wb.numberOfSheets).map { wb.getSheetName(it).lowercase() }
        onProgress?.invoke("Detected ${wb.numberOfSheets} sheet(s): ${sheetNames.joinToString(", ")}", 0, 100, 35)

        val isSale = sheetNames.any { it.contains("sale") || it.contains("invoice") || it.contains("tax invoice") }
        val isPurchase = sheetNames.any { it.contains("purchase") || it.contains("bill") || it.contains("pur_") }
        val isPayment = sheetNames.any { 
            it.contains("payment") || it.contains("money_out") || it.contains("paid") || 
            it.contains("debtor") || it.contains("pending") || it.contains("outstanding") || it.contains("dues")
        }
        val isReceipt = sheetNames.any { 
            it.contains("receipt") || it.contains("money_in") || it.contains("received") || it.contains("collection")
        }

        return when {
            isSale -> parseSale(wb, context, companyId, existingItemsMap, onProgress)
            isPurchase -> parsePurchase(wb, context, companyId, existingItemsMap, onProgress)
            isReceipt -> parseReceipt(wb, onProgress)
            isPayment -> parsePayment(wb, onProgress)
            else -> {
                // Inspect headers of the first sheet to auto-detect voucher type
                val firstSheet = wb.getSheetAt(0)
                val headers = getHeaders(firstSheet)

                val hasQty = findHeaderIndex(headers, "Quantity") != null
                val hasRate = findHeaderIndex(headers, "Rate") != null
                val hasExplicitItem = headers.keys.any {
                    val l = it.lowercase().replace(Regex("[^a-z0-9]"), "")
                    l == "itemname" || l == "item" || l == "productname" || l == "product" || l == "stockitem" || l == "service"
                }
                val hasInventory = hasQty || (hasExplicitItem && hasRate)

                val hasVendor = headers.keys.any { 
                    val l = it.lowercase()
                    l.contains("vendor") || l.contains("supplier") || l.contains("creditor") || l.contains("pay to")
                }
                val hasReceiptClue = headers.keys.any {
                    val l = it.lowercase()
                    l.contains("receipt") || l.contains("received") || l.contains("collection") || l.contains("deposit") || l.contains("money in")
                }
                val hasPaymentClue = headers.keys.any {
                    val l = it.lowercase()
                    l.contains("payment") || l.contains("paid") || l.contains("debtor") || l.contains("pending") || 
                    l.contains("outstanding") || l.contains("due") || l.contains("money out") || l.contains("pay")
                }

                when {
                    hasInventory -> {
                        if (hasVendor) {
                            parsePurchase(wb, context, companyId, existingItemsMap, onProgress)
                        } else {
                            parseSale(wb, context, companyId, existingItemsMap, onProgress)
                        }
                    }
                    hasReceiptClue && !hasPaymentClue -> parseReceipt(wb, onProgress)
                    hasPaymentClue -> parsePayment(wb, onProgress)
                    findHeaderIndex(headers, "PayToLedger") != null || findHeaderIndex(headers, "PayFromLedger") != null -> parsePayment(wb, onProgress)
                    findHeaderIndex(headers, "ReceivedFrom") != null || findHeaderIndex(headers, "ReceiveInLedger") != null -> parseReceipt(wb, onProgress)
                    else -> {
                        // Debtor, pending or financial ledger rows with no inventory items: default to Payment vouchers!
                        parsePayment(wb, onProgress)
                    }
                }
            }
        }.also { wb.close() }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Sale Import (Supports both 2-Sheet Template and 1-Sheet Flat Exports)
    // ──────────────────────────────────────────────────────────────────────────

    private fun parseSale(
        wb: Workbook,
        context: Context? = null,
        companyId: Int? = null,
        existingItemsMap: Map<String, com.minhas.teller.data.entities.StockItem>? = null,
        onProgress: ((step: String, current: Int, total: Int, percent: Int) -> Unit)? = null
    ): ImportResult.SaleResult {
        val errors = mutableListOf<ImportError>()
        val vouchers = mutableListOf<ParsedSaleVoucher>()

        val vSheet = findSheet(wb, listOf("Sale_Vouchers", "Sale", "Sales", "Invoices", "Invoice", "Tax Invoice", "Sale_Book", "Sheet1"))
            ?: wb.getSheetAt(0)
        val iSheet = findSheet(wb, listOf("Sale_Items", "SaleItems", "Items", "Item_Details"))

        val itemsByVoucher = mutableMapOf<String, MutableList<ParsedSaleItem>>()
        val missingCols = mutableListOf<String>()
        val unresItems = mutableListOf<String>()

        if (iSheet != null && iSheet != vSheet) {
            // Multi-sheet mode (Template format)
            onProgress?.invoke("Scanning item records in sheet '${iSheet.sheetName}'...", 0, iSheet.lastRowNum + 1, 40)
            val headers = getHeaders(iSheet)
            val dataStartRow = findDataStartRow(iSheet)
            val totalItemRows = (iSheet.lastRowNum - dataStartRow + 1).coerceAtLeast(1)
            for (r in dataStartRow..iSheet.lastRowNum) {
                val row = iSheet.getRow(r) ?: continue
                if (isRowEmpty(row, headers.size)) continue
                val rowErrs = mutableListOf<ImportError>()
                val vNo = reqStr(row, headers, "VoucherNumber", "Sale_Items", r + 1, rowErrs)
                val itemName = reqStr(row, headers, "ItemName", "Sale_Items", r + 1, rowErrs)
                val category = optStr(row, headers, "ItemCategory") ?: "General"
                val qty = optDouble(row, headers, "Quantity") ?: 1.0
                val rawRate = optDouble(row, headers, "Rate")
                val lineAmt = optDouble(row, headers, "Amount") ?: 0.0
                val rate = rawRate ?: if (qty > 0.0 && lineAmt > 0.0) lineAmt / qty else lineAmt
                val unit = optStr(row, headers, "Unit") ?: "Nos"
                val explicitHsn = optStr(row, headers, "HSNCode")
                val explicitGstRate = optDouble(row, headers, "GSTRate")
                val discount = optDouble(row, headers, "Discount") ?: 0.0

                var resolvedHsn = explicitHsn?.takeIf { it.isNotBlank() } ?: "0000"
                var resolvedGst = explicitGstRate ?: 0.0

                if (itemName != null) {
                    val taxRes = resolveHsnAndTax(itemName, category, explicitHsn, explicitGstRate, context, companyId, existingItemsMap)
                    if (taxRes != null) {
                        resolvedHsn = taxRes.first ?: resolvedHsn
                        resolvedGst = taxRes.second
                    } else {
                        unresItems.add(itemName)
                    }
                }

                errors += rowErrs
                if (rowErrs.isEmpty() && vNo != null && itemName != null) {
                    itemsByVoucher.getOrPut(vNo) { mutableListOf() }.add(
                        ParsedSaleItem(vNo, itemName, category, qty, unit, rate, resolvedHsn, resolvedGst, discount)
                    )
                }

                val rowIdx = r - dataStartRow + 1
                val pct = 40 + (rowIdx * 25 / totalItemRows)
                onProgress?.invoke("Validating item row ${r + 1} of ${iSheet.lastRowNum + 1} (${itemName ?: "item"})...", rowIdx, totalItemRows, pct)
            }

            onProgress?.invoke("Scanning vouchers in sheet '${vSheet.sheetName}'...", 0, vSheet.lastRowNum + 1, 65)
            val vHeaders = getHeaders(vSheet)
            val vDataStartRow = findDataStartRow(vSheet)
            val seenNumbers = mutableSetOf<String>()
            val totalVchRows = (vSheet.lastRowNum - vDataStartRow + 1).coerceAtLeast(1)
            for (r in vDataStartRow..vSheet.lastRowNum) {
                val row = vSheet.getRow(r) ?: continue
                if (isRowEmpty(row, vHeaders.size)) continue
                val rowErrs = mutableListOf<ImportError>()

                val vNo = reqStr(row, vHeaders, "VoucherNumber", "Sale_Vouchers", r + 1, rowErrs) ?: continue
                val date = reqDate(row, vHeaders, "Date", "Sale_Vouchers", r + 1, rowErrs) ?: Date()
                val party = reqStr(row, vHeaders, "PartyName", "Sale_Vouchers", r + 1, rowErrs)
                val pos = optStr(row, vHeaders, "PlaceOfSupply")
                val isIGST = optBool(row, vHeaders, "IsIGST")
                val financedBy = optStr(row, vHeaders, "FinancedBy")
                val isFinanced = optBool(row, vHeaders, "IsFinanced") || !financedBy.isNullOrBlank()
                val narr = optStr(row, vHeaders, "Narration")
                val cashL = optStr(row, vHeaders, "CashLedger")
                val cashAmt = optDouble(row, vHeaders, "CashAmount") ?: 0.0
                val roundOff = optDouble(row, vHeaders, "RoundOff") ?: 0.0

                if (vNo in seenNumbers) {
                    errors += ImportError("Sale_Vouchers", r + 1, "VoucherNumber", "Duplicate VoucherNumber '$vNo' – row skipped")
                    continue
                }
                seenNumbers += vNo
                errors += rowErrs

                if (rowErrs.isEmpty() && party != null) {
                    vouchers.add(
                        ParsedSaleVoucher(
                            voucherNumber = vNo, date = date, partyName = party,
                            placeOfSupply = pos, isIGST = isIGST, isFinanced = isFinanced,
                            financedBy = financedBy, narration = narr, cashLedger = cashL,
                            cashAmount = cashAmt, roundOff = roundOff, items = itemsByVoucher[vNo] ?: emptyList()
                        )
                    )
                }

                val rowIdx = r - vDataStartRow + 1
                val pct = 65 + (rowIdx * 30 / totalVchRows)
                onProgress?.invoke("Validating voucher row ${r + 1} of ${vSheet.lastRowNum + 1} ($vNo)...", rowIdx, totalVchRows, pct)
            }
        } else {
            // Single-sheet mode (Vyapar, Marg, Zoho Books, Busy, QuickBooks, MyBillBook, Khatabook)
            onProgress?.invoke("Scanning rows from sheet '${vSheet.sheetName}'...", 0, vSheet.lastRowNum + 1, 40)
            val headers = getHeaders(vSheet)
            val dataStartRow = findDataStartRow(vSheet)
            val vouchersMap = mutableMapOf<String, ParsedSaleVoucher>()
            var autoVchCounter = 1
            var lastVchNo = "INV-1"
            val totalRows = (vSheet.lastRowNum - dataStartRow + 1).coerceAtLeast(1)

            for (r in dataStartRow..vSheet.lastRowNum) {
                val row = vSheet.getRow(r) ?: continue
                if (isRowEmpty(row, headers.size)) continue
                val rowErrs = mutableListOf<ImportError>()

                var vNo = optStr(row, headers, "VoucherNumber")
                if (vNo.isNullOrBlank()) {
                    val pName = optStr(row, headers, "PartyName")
                    if (!pName.isNullOrBlank() || vouchersMap.isEmpty()) {
                        vNo = "INV-${autoVchCounter++}"
                        lastVchNo = vNo
                    } else {
                        vNo = lastVchNo
                    }
                } else {
                    lastVchNo = vNo
                }

                val date = reqDate(row, headers, "Date", vSheet.sheetName, r + 1, rowErrs) ?: Date()
                val party = optStr(row, headers, "PartyName") ?: "Cash Customer"
                val pos = optStr(row, headers, "PlaceOfSupply")
                val isIGST = optBool(row, headers, "IsIGST")
                val financedBy = optStr(row, headers, "FinancedBy")
                val isFinanced = optBool(row, headers, "IsFinanced") || !financedBy.isNullOrBlank()
                val narr = optStr(row, headers, "Narration")
                val cashL = optStr(row, headers, "CashLedger")
                val cashAmt = optDouble(row, headers, "CashAmount") ?: 0.0
                val roundOff = optDouble(row, headers, "RoundOff") ?: 0.0

                val rawItemName = optStr(row, headers, "ItemName")
                val qty = optDouble(row, headers, "Quantity") ?: 1.0
                val rawRate = optDouble(row, headers, "Rate")
                val lineAmt = optDouble(row, headers, "Amount") ?: 0.0
                val rate = rawRate ?: if (qty > 0.0 && lineAmt > 0.0) lineAmt / qty else lineAmt
                val unit = optStr(row, headers, "Unit") ?: "Nos"
                val explicitHsn = optStr(row, headers, "HSNCode")
                val explicitGstRate = optDouble(row, headers, "GSTRate")
                val discount = optDouble(row, headers, "Discount") ?: 0.0

                val itemName = rawItemName ?: if (rate > 0.0 || lineAmt > 0.0) "General Sales" else null

                if (!itemName.isNullOrBlank()) {
                    val category = optStr(row, headers, "ItemCategory") ?: "General"
                    var resolvedHsn = explicitHsn?.takeIf { it.isNotBlank() } ?: "0000"
                    var resolvedGst = explicitGstRate ?: 0.0

                    val taxRes = resolveHsnAndTax(itemName, category, explicitHsn, explicitGstRate, context, companyId, existingItemsMap)
                    if (taxRes != null) {
                        resolvedHsn = taxRes.first ?: resolvedHsn
                        resolvedGst = taxRes.second
                    }

                    val saleItem = ParsedSaleItem(
                        voucherNumber = vNo,
                        itemName = itemName,
                        category = category,
                        quantity = qty,
                        unit = unit,
                        rate = rate,
                        hsnCode = resolvedHsn,
                        gstRate = resolvedGst,
                        discount = discount
                    )
                    itemsByVoucher.getOrPut(vNo) { mutableListOf() }.add(saleItem)
                }

                if (!vouchersMap.containsKey(vNo)) {
                    vouchersMap[vNo] = ParsedSaleVoucher(
                        voucherNumber = vNo,
                        date = date,
                        partyName = party,
                        placeOfSupply = pos,
                        isIGST = isIGST,
                        isFinanced = isFinanced,
                        financedBy = financedBy,
                        narration = narr,
                        cashLedger = cashL,
                        cashAmount = cashAmt,
                        roundOff = roundOff,
                        items = emptyList()
                    )
                }

                val rowIdx = r - dataStartRow + 1
                val pct = 40 + (rowIdx * 55 / totalRows)
                onProgress?.invoke("Validating row ${r + 1} of ${vSheet.lastRowNum + 1} ($vNo - $party)...", rowIdx, totalRows, pct)
            }

            for ((vNo, vch) in vouchersMap) {
                vouchers.add(vch.copy(items = itemsByVoucher[vNo] ?: emptyList()))
            }
        }
        onProgress?.invoke("Validated ${vouchers.size} sale vouchers successfully", vouchers.size, vouchers.size, 98)
        return ImportResult.SaleResult(vouchers, errors, unresItems.distinct(), missingCols.distinct())
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Purchase Import (Supports both 2-Sheet Template and 1-Sheet Flat Exports)
    // ──────────────────────────────────────────────────────────────────────────

    private fun parsePurchase(
        wb: Workbook,
        context: Context? = null,
        companyId: Int? = null,
        existingItemsMap: Map<String, com.minhas.teller.data.entities.StockItem>? = null,
        onProgress: ((step: String, current: Int, total: Int, percent: Int) -> Unit)? = null
    ): ImportResult.PurchaseResult {
        val errors = mutableListOf<ImportError>()
        val vouchers = mutableListOf<ParsedPurchaseVoucher>()

        val vSheet = findSheet(wb, listOf("Purchase_Vouchers", "Purchase", "Purchases", "Bills", "Bill", "Pur_Book", "Sheet1"))
            ?: wb.getSheetAt(0)
        val iSheet = findSheet(wb, listOf("Purchase_Items", "PurchaseItems", "Items", "Item_Details"))

        val itemsByVoucher = mutableMapOf<String, MutableList<ParsedPurchaseItem>>()
        val missingCols = mutableListOf<String>()
        val unresItems = mutableListOf<String>()

        if (iSheet != null && iSheet != vSheet) {
            // Multi-sheet mode
            onProgress?.invoke("Scanning item records in sheet '${iSheet.sheetName}'...", 0, iSheet.lastRowNum + 1, 40)
            val headers = getHeaders(iSheet)
            val dataStartRow = findDataStartRow(iSheet)
            val totalItemRows = (iSheet.lastRowNum - dataStartRow + 1).coerceAtLeast(1)
            for (r in dataStartRow..iSheet.lastRowNum) {
                val row = iSheet.getRow(r) ?: continue
                if (isRowEmpty(row, headers.size)) continue
                val rowErrs = mutableListOf<ImportError>()
                val vNo      = reqStr(row, headers, "VoucherNumber", "Purchase_Items", r + 1, rowErrs)
                val itemName = reqStr(row, headers, "ItemName", "Purchase_Items", r + 1, rowErrs)
                val category = optStr(row, headers, "ItemCategory") ?: "General"
                val qty      = optDouble(row, headers, "Quantity") ?: 1.0
                val rawRate  = optDouble(row, headers, "Rate")
                val lineAmt  = optDouble(row, headers, "Amount") ?: 0.0
                val rate     = rawRate ?: if (qty > 0.0 && lineAmt > 0.0) lineAmt / qty else lineAmt
                val unit     = optStr(row, headers, "Unit") ?: "Nos"
                val explicitHsn = optStr(row, headers, "HSNCode")
                val explicitGstRate = optDouble(row, headers, "GSTRate")
                val discount = optDouble(row, headers, "Discount") ?: 0.0

                var resolvedHsn = explicitHsn?.takeIf { it.isNotBlank() } ?: "0000"
                var resolvedGst = explicitGstRate ?: 0.0

                if (itemName != null) {
                    val taxRes = resolveHsnAndTax(itemName, category, explicitHsn, explicitGstRate, context, companyId, existingItemsMap)
                    if (taxRes != null) {
                        resolvedHsn = taxRes.first ?: resolvedHsn
                        resolvedGst = taxRes.second
                    } else {
                        unresItems.add(itemName)
                    }
                }

                errors += rowErrs
                if (rowErrs.isEmpty() && vNo != null && itemName != null) {
                    itemsByVoucher.getOrPut(vNo) { mutableListOf() }.add(
                        ParsedPurchaseItem(vNo, itemName, category, qty, unit, rate, resolvedHsn, resolvedGst, discount)
                    )
                }

                val rowIdx = r - dataStartRow + 1
                val pct = 40 + (rowIdx * 25 / totalItemRows)
                onProgress?.invoke("Validating item row ${r + 1} of ${iSheet.lastRowNum + 1} (${itemName ?: "item"})...", rowIdx, totalItemRows, pct)
            }

            onProgress?.invoke("Scanning vouchers in sheet '${vSheet.sheetName}'...", 0, vSheet.lastRowNum + 1, 65)
            val vHeaders = getHeaders(vSheet)
            val vDataStartRow = findDataStartRow(vSheet)
            val seenNumbers = mutableSetOf<String>()
            val totalVchRows = (vSheet.lastRowNum - vDataStartRow + 1).coerceAtLeast(1)
            for (r in vDataStartRow..vSheet.lastRowNum) {
                val row = vSheet.getRow(r) ?: continue
                if (isRowEmpty(row, vHeaders.size)) continue
                val rowErrs = mutableListOf<ImportError>()

                val vNo      = reqStr(row, vHeaders, "VoucherNumber", "Purchase_Vouchers", r + 1, rowErrs) ?: continue
                val date     = reqDate(row, vHeaders, "Date", "Purchase_Vouchers", r + 1, rowErrs) ?: Date()
                val party    = reqStr(row, vHeaders, "PartyName", "Purchase_Vouchers", r + 1, rowErrs)
                val suppInv  = optStr(row, vHeaders, "SupplierInvNo")
                val pos      = optStr(row, vHeaders, "PlaceOfSupply")
                val isIGST   = optBool(row, vHeaders, "IsIGST")
                val revCharge = optBool(row, vHeaders, "ReverseCharge")
                val narr     = optStr(row, vHeaders, "Narration")
                val cashL    = optStr(row, vHeaders, "CashLedger")
                val cashAmt  = optDouble(row, vHeaders, "CashAmount") ?: 0.0

                if (vNo in seenNumbers) {
                    errors += ImportError("Purchase_Vouchers", r + 1, "VoucherNumber", "Duplicate VoucherNumber '$vNo' – row skipped")
                    continue
                }
                seenNumbers += vNo
                errors += rowErrs

                if (rowErrs.isEmpty() && party != null) {
                    vouchers.add(
                        ParsedPurchaseVoucher(
                            voucherNumber = vNo, date = date, partyName = party,
                            supplierInvNo = suppInv, placeOfSupply = pos,
                            isIGST = isIGST, reverseCharge = revCharge,
                            narration = narr, cashLedger = cashL, cashAmount = cashAmt,
                            items = itemsByVoucher[vNo] ?: emptyList()
                        )
                    )
                }

                val rowIdx = r - vDataStartRow + 1
                val pct = 65 + (rowIdx * 30 / totalVchRows)
                onProgress?.invoke("Validating purchase voucher row ${r + 1} of ${vSheet.lastRowNum + 1} ($vNo)...", rowIdx, totalVchRows, pct)
            }
        } else {
            // Single-sheet mode
            onProgress?.invoke("Scanning rows from sheet '${vSheet.sheetName}'...", 0, vSheet.lastRowNum + 1, 40)
            val headers = getHeaders(vSheet)
            val dataStartRow = findDataStartRow(vSheet)
            val vouchersMap = mutableMapOf<String, ParsedPurchaseVoucher>()
            var autoVchCounter = 1
            var lastVchNo = "PUR-1"
            val totalRows = (vSheet.lastRowNum - dataStartRow + 1).coerceAtLeast(1)

            for (r in dataStartRow..vSheet.lastRowNum) {
                val row = vSheet.getRow(r) ?: continue
                if (isRowEmpty(row, headers.size)) continue
                val rowErrs = mutableListOf<ImportError>()

                var vNo = optStr(row, headers, "VoucherNumber")
                if (vNo.isNullOrBlank()) {
                    val pName = optStr(row, headers, "PartyName")
                    if (!pName.isNullOrBlank() || vouchersMap.isEmpty()) {
                        vNo = "PUR-${autoVchCounter++}"
                        lastVchNo = vNo
                    } else {
                        vNo = lastVchNo
                    }
                } else {
                    lastVchNo = vNo
                }

                val date = reqDate(row, headers, "Date", vSheet.sheetName, r + 1, rowErrs) ?: Date()
                val party = optStr(row, headers, "PartyName") ?: "Supplier"
                val suppInv = optStr(row, headers, "SupplierInvNo")
                val pos = optStr(row, headers, "PlaceOfSupply")
                val isIGST = optBool(row, headers, "IsIGST")
                val revCharge = optBool(row, headers, "ReverseCharge")
                val narr = optStr(row, headers, "Narration")
                val cashL = optStr(row, headers, "CashLedger")
                val cashAmt = optDouble(row, headers, "CashAmount") ?: 0.0

                val rawItemName = optStr(row, headers, "ItemName")
                val qty = optDouble(row, headers, "Quantity") ?: 1.0
                val rawRate = optDouble(row, headers, "Rate")
                val lineAmt = optDouble(row, headers, "Amount") ?: 0.0
                val rate = rawRate ?: if (qty > 0.0 && lineAmt > 0.0) lineAmt / qty else lineAmt
                val unit = optStr(row, headers, "Unit") ?: "Nos"
                val explicitHsn = optStr(row, headers, "HSNCode")
                val explicitGstRate = optDouble(row, headers, "GSTRate")
                val discount = optDouble(row, headers, "Discount") ?: 0.0

                val itemName = rawItemName ?: if (rate > 0.0 || lineAmt > 0.0) "General Purchase" else null

                if (!itemName.isNullOrBlank()) {
                    val category = optStr(row, headers, "ItemCategory") ?: "General"
                    var resolvedHsn = explicitHsn?.takeIf { it.isNotBlank() } ?: "0000"
                    var resolvedGst = explicitGstRate ?: 0.0

                    val taxRes = resolveHsnAndTax(itemName, category, explicitHsn, explicitGstRate, context, companyId, existingItemsMap)
                    if (taxRes != null) {
                        resolvedHsn = taxRes.first ?: resolvedHsn
                        resolvedGst = taxRes.second
                    }

                    val purchaseItem = ParsedPurchaseItem(
                        voucherNumber = vNo,
                        itemName = itemName,
                        category = category,
                        quantity = qty,
                        unit = unit,
                        rate = rate,
                        hsnCode = resolvedHsn,
                        gstRate = resolvedGst,
                        discount = discount
                    )
                    itemsByVoucher.getOrPut(vNo) { mutableListOf() }.add(purchaseItem)
                }

                if (!vouchersMap.containsKey(vNo)) {
                    vouchersMap[vNo] = ParsedPurchaseVoucher(
                        voucherNumber = vNo,
                        date = date,
                        partyName = party,
                        supplierInvNo = suppInv,
                        placeOfSupply = pos,
                        isIGST = isIGST,
                        reverseCharge = revCharge,
                        narration = narr,
                        cashLedger = cashL,
                        cashAmount = cashAmt,
                        items = emptyList()
                    )
                }

                val rowIdx = r - dataStartRow + 1
                val pct = 40 + (rowIdx * 55 / totalRows)
                onProgress?.invoke("Validating purchase row ${r + 1} of ${vSheet.lastRowNum + 1} ($vNo - $party)...", rowIdx, totalRows, pct)
            }

            for ((vNo, vch) in vouchersMap) {
                vouchers.add(vch.copy(items = itemsByVoucher[vNo] ?: emptyList()))
            }
        }
        onProgress?.invoke("Validated ${vouchers.size} purchase vouchers successfully", vouchers.size, vouchers.size, 98)
        return ImportResult.PurchaseResult(vouchers, errors, unresItems.distinct(), missingCols.distinct())
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Payment Import
    // ──────────────────────────────────────────────────────────────────────────

    private fun parsePayment(
        wb: Workbook,
        onProgress: ((step: String, current: Int, total: Int, percent: Int) -> Unit)? = null
    ): ImportResult.PaymentResult {
        val errors = mutableListOf<ImportError>()
        val vouchers = mutableListOf<ParsedPaymentVoucher>()

        val sheet = findSheet(wb, listOf("Payment_Vouchers", "Payment", "Payments", "Payment Register", "Money Out", "Vendor Payment", "Sheet1"))
            ?: wb.getSheetAt(0)

        onProgress?.invoke("Scanning payments in sheet '${sheet.sheetName}'...", 0, sheet.lastRowNum + 1, 40)
        val headers = getHeaders(sheet)
        val dataStartRow = findDataStartRow(sheet)
        val seenNumbers = mutableSetOf<String>()
        var autoCounter = 1
        val totalRows = (sheet.lastRowNum - dataStartRow + 1).coerceAtLeast(1)

        for (r in dataStartRow..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            if (isRowEmpty(row, headers.size)) continue
            val rowErrs = mutableListOf<ImportError>()

            val vNo = optStr(row, headers, "VoucherNumber") ?: "PAY-${autoCounter++}"
            val date = reqDate(row, headers, "Date", sheet.sheetName, r + 1, rowErrs) ?: Date()
            val fromLed = optStr(row, headers, "PayFromLedger")?.takeIf { it.isNotBlank() } ?: "Primary Bank"
            val toLed = optStr(row, headers, "PayToLedger") ?: optStr(row, headers, "PartyName") ?: run {
                val c0 = row.getCell(0)?.toString()?.trim()
                if (!c0.isNullOrBlank() && c0.toDoubleOrNull() == null) c0 else "General Customer"
            }
            val amount = reqDouble(row, headers, "Amount", sheet.sheetName, r + 1, rowErrs)
            val isFinanced = optBool(row, headers, "IsFinanced")
            val financedBy = optStr(row, headers, "FinancedBy")
            val narr = optStr(row, headers, "Narration")
            val billRef = optStr(row, headers, "BillReference")
            val cheque = optStr(row, headers, "ChequeNo")

            if (vNo in seenNumbers) {
                errors += ImportError(sheet.sheetName, r + 1, "VoucherNumber", "Duplicate VoucherNumber '$vNo' – row skipped")
                continue
            }
            seenNumbers += vNo
            errors += rowErrs

            if (rowErrs.isEmpty() && amount != null) {
                if (amount <= 0) {
                    errors += ImportError(sheet.sheetName, r + 1, "Amount", "Amount must be a positive number, got: $amount")
                } else {
                    vouchers.add(
                        ParsedPaymentVoucher(
                            voucherNumber = vNo, date = date,
                            payFromLedger = fromLed, payToLedger = toLed,
                            amount = amount, isFinanced = isFinanced, financedBy = financedBy,
                            narration = narr, billReference = billRef, chequeNo = cheque
                        )
                    )
                }
            }

            val rowIdx = r - dataStartRow + 1
            val pct = 40 + (rowIdx * 55 / totalRows)
            onProgress?.invoke("Validating payment row ${r + 1} of ${sheet.lastRowNum + 1} ($vNo)...", rowIdx, totalRows, pct)
        }
        onProgress?.invoke("Validated ${vouchers.size} payment vouchers successfully", vouchers.size, vouchers.size, 98)
        return ImportResult.PaymentResult(vouchers, errors)
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Receipt Import
    // ──────────────────────────────────────────────────────────────────────────

    private fun parseReceipt(
        wb: Workbook,
        onProgress: ((step: String, current: Int, total: Int, percent: Int) -> Unit)? = null
    ): ImportResult.ReceiptResult {
        val errors = mutableListOf<ImportError>()
        val vouchers = mutableListOf<ParsedReceiptVoucher>()

        val sheet = findSheet(wb, listOf("Receipt_Vouchers", "Receipt", "Receipts", "Receipt Register", "Money In", "Customer Payment", "Sheet1"))
            ?: wb.getSheetAt(0)

        onProgress?.invoke("Scanning receipts in sheet '${sheet.sheetName}'...", 0, sheet.lastRowNum + 1, 40)
        val headers = getHeaders(sheet)
        val dataStartRow = findDataStartRow(sheet)
        val seenNumbers = mutableSetOf<String>()
        var autoCounter = 1
        val totalRows = (sheet.lastRowNum - dataStartRow + 1).coerceAtLeast(1)

        for (r in dataStartRow..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            if (isRowEmpty(row, headers.size)) continue
            val rowErrs = mutableListOf<ImportError>()

            val vNo = optStr(row, headers, "VoucherNumber") ?: "REC-${autoCounter++}"
            val date = reqDate(row, headers, "Date", sheet.sheetName, r + 1, rowErrs) ?: Date()
            val recvInLed = optStr(row, headers, "ReceiveInLedger")?.takeIf { it.isNotBlank() } ?: "Primary Bank"
            val recvFrom = optStr(row, headers, "ReceivedFrom") ?: optStr(row, headers, "PartyName") ?: run {
                val c0 = row.getCell(0)?.toString()?.trim()
                if (!c0.isNullOrBlank() && c0.toDoubleOrNull() == null) c0 else "General Customer"
            }
            val amount = reqDouble(row, headers, "Amount", sheet.sheetName, r + 1, rowErrs)
            val isFinanced = optBool(row, headers, "IsFinanced")
            val financedBy = optStr(row, headers, "FinancedBy")
            val narr = optStr(row, headers, "Narration")
            val billRef = optStr(row, headers, "BillReference")
            val cheque = optStr(row, headers, "ChequeNo")

            if (vNo in seenNumbers) {
                errors += ImportError(sheet.sheetName, r + 1, "VoucherNumber", "Duplicate VoucherNumber '$vNo' – row skipped")
                continue
            }
            seenNumbers += vNo
            errors += rowErrs

            if (rowErrs.isEmpty() && amount != null) {
                if (amount <= 0) {
                    errors += ImportError(sheet.sheetName, r + 1, "Amount", "Amount must be a positive number, got: $amount")
                } else {
                    vouchers.add(
                        ParsedReceiptVoucher(
                            voucherNumber = vNo, date = date,
                            receiveInLedger = recvInLed, receivedFrom = recvFrom,
                            amount = amount, isFinanced = isFinanced, financedBy = financedBy,
                            narration = narr, billReference = billRef, chequeNo = cheque
                        )
                    )
                }
            }

            val rowIdx = r - dataStartRow + 1
            val pct = 40 + (rowIdx * 55 / totalRows)
            onProgress?.invoke("Validating receipt row ${r + 1} of ${sheet.lastRowNum + 1} ($vNo)...", rowIdx, totalRows, pct)
        }
        onProgress?.invoke("Validated ${vouchers.size} receipt vouchers successfully", vouchers.size, vouchers.size, 98)
        return ImportResult.ReceiptResult(vouchers, errors)
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Parsing Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private val DATE_FORMATS = listOf(
        "dd-MM-yyyy", "dd/MM/yyyy", "yyyy-MM-dd", "MM/dd/yyyy", "dd-MMM-yyyy",
        "d-MMM-yyyy", "dd.MM.yyyy", "yyyy/MM/dd", "yyyyMMdd", "dd-MMM-yy",
        "d/M/yyyy", "d-M-yyyy", "yyyy.MM.dd"
    )

    private val HEADER_SYNONYMS = mapOf(
        "VoucherNumber" to listOf(
            "vouchernumber", "voucher no", "invoice no", "invoicenumber", "invoice number",
            "inv no", "bill no", "bill number", "vch no", "vchno", "number", "doc no",
            "ref no", "transaction id", "txn id", "bill #", "invoice #", "trans no", "voucher id",
            "receipt no", "receipt number", "rec no", "payment no", "payment number", "pay no", "receipt #"
        ),
        "PartyName" to listOf(
            "partyname", "party name", "party", "customer name", "customer", "client name",
            "client", "buyer name", "buyer", "vendor name", "vendor", "supplier name",
            "supplier", "account name", "particulars", "ledger name", "party/customer",
            "party / ledger", "account", "name", "debtor", "debtors", "sundry debtors",
            "debtor name", "creditor", "creditors", "sundry creditors", "creditor name",
            "party/debtor", "customer/debtor", "account title", "ledger"
        ),
        "Date" to listOf(
            "date", "invoice date", "bill date", "vch date", "voucher date", "txn date",
            "transaction date", "entry date", "inv date", "dated"
        ),
        "ItemName" to listOf(
            "itemname", "item name", "item", "product name", "product", "description",
            "item description", "particulars", "goods", "stock item", "product / service",
            "service", "product description", "details"
        ),
        "ItemCategory" to listOf(
            "itemcategory", "item category", "category", "product category", "group", "item group"
        ),
        "Quantity" to listOf(
            "quantity", "qty", "billed qty", "actual qty", "units", "count", "pieces", "nos",
            "pcs", "total qty", "item qty"
        ),
        "Rate" to listOf(
            "rate", "price", "unit price", "price/unit", "rate/unit", "unit rate", "sale price",
            "purchase price", "item rate", "mrp", "item price"
        ),
        "Amount" to listOf(
            "amount", "total", "net amount", "total amount", "grand total", "line total",
            "value", "taxable amount", "gross amount", "amt", "invoice amount", "subtotal", "net total",
            "debit", "credit", "dr", "cr", "balance", "paid", "received", "total amt",
            "pending", "pending amount", "pending amt", "due", "due amount", "due amt",
            "outstanding", "outstanding amount", "outstanding amt", "balance amount", "bal amt",
            "closing balance", "cl bal", "pending balance", "net balance", "total pending", "total dues"
        ),
        "HSNCode" to listOf(
            "hsncode", "hsn code", "hsn", "hsn/sac", "sac", "sac code", "hsn / sac", "hsn/sac code"
        ),
        "GSTRate" to listOf(
            "gstrate", "gst rate", "gst %", "gst", "tax %", "tax rate", "tax percentage",
            "rate (%)", "igst %", "tax", "gst(%)", "tax rate (%)"
        ),
        "Unit" to listOf(
            "unit", "units", "uom", "base unit", "measuring unit", "packing", "unit name"
        ),
        "Discount" to listOf(
            "discount", "disc", "discount %", "disc %", "discount amount", "disc amt", "discount(%)"
        ),
        "Narration" to listOf(
            "narration", "remarks", "notes", "description", "note", "comment"
        ),
        "ReceiveInLedger" to listOf(
            "receiveinledger", "receive in", "received in", "deposit to", "bank", "cash/bank", "payment mode",
            "mode", "account", "received into", "pay mode", "payment type", "to account", "destination account"
        ),
        "ReceivedFrom" to listOf(
            "receivedfrom", "received from", "from", "party", "customer", "customer name", "client", "paid by",
            "debtor", "debtors", "debtor name", "payer", "particulars", "ledger"
        ),
        "PayFromLedger" to listOf(
            "payfromledger", "pay from", "paid from", "bank", "cash/bank", "payment mode", "mode", "account",
            "from account", "paid through", "pay mode", "payment type", "source account"
        ),
        "PayToLedger" to listOf(
            "paytoledger", "pay to", "paid to", "beneficiary", "party", "party name", "vendor", "supplier", "payee",
            "customer", "customer name", "debtor", "debtors", "debtor name", "particulars", "account", "ledger"
        ),
        "BillReference" to listOf(
            "billreference", "bill reference", "ref", "ref no", "reference", "ref name", "bill no", "invoice no", "inv no", "bill #", "doc no"
        ),
        "ChequeNo" to listOf(
            "chequeno", "cheque no", "cheque", "chk no", "transaction id", "txn id", "utr", "utr no", "ref id"
        )
    )

    fun findHeaderIndex(headers: Map<String, Int>, col: String): Int? {
        val exact = headers[col]
        if (exact != null) return exact
        val synonyms = HEADER_SYNONYMS[col] ?: listOf(col.lowercase())
        val normSyns = synonyms.map { it.lowercase().replace(Regex("[^a-z0-9]"), "") }
        for ((k, idx) in headers) {
            val normK = k.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (normSyns.any { it == normK || normK.startsWith(it) || it.startsWith(normK) }) {
                return idx
            }
        }
        return null
    }

    private fun findSheet(wb: Workbook, preferredNames: List<String>): org.apache.poi.ss.usermodel.Sheet? {
        for (name in preferredNames) {
            for (i in 0 until wb.numberOfSheets) {
                val sName = wb.getSheetName(i).lowercase().trim()
                if (sName == name.lowercase() || sName.startsWith(name.lowercase()) || sName.contains(name.lowercase())) {
                    return wb.getSheetAt(i)
                }
            }
        }
        return null
    }

    private fun findSheet(wb: Workbook, name: String): org.apache.poi.ss.usermodel.Sheet? {
        return findSheet(wb, listOf(name))
    }

    /**
     * Converts a CSV byte stream into an in-memory XSSFWorkbook so CSV files from any app
     * can be imported directly through the same intelligent engine.
     */
    fun csvToWorkbook(bytes: ByteArray): XSSFWorkbook {
        val tabular = UniversalParser.parseCsv(ByteArrayInputStream(bytes))
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("ImportData")
        val headerRow = sheet.createRow(0)
        for ((c, h) in tabular.headers.withIndex()) {
            headerRow.createCell(c).setCellValue(h)
        }
        for (r in tabular.rows) {
            val row = sheet.createRow(r.index + 1)
            for ((c, cellVal) in r.cells.withIndex()) {
                row.createCell(c).setCellValue(cellVal)
            }
        }
        return wb
    }

    private fun findHeaderRowAndHeaders(sheet: org.apache.poi.ss.usermodel.Sheet): Pair<Int, Map<String, Int>> {
        var bestRowIdx = 0
        var bestHeaders = emptyMap<String, Int>()
        var bestScore = -1

        val maxRowToInspect = minOf(10, sheet.lastRowNum)
        for (r in 0..maxRowToInspect) {
            val row = sheet.getRow(r) ?: continue
            val headers = mutableMapOf<String, Int>()
            for (c in 0 until row.lastCellNum.toInt()) {
                val raw = row.getCell(c)?.toString()?.trim() ?: continue
                val clean = raw.replace(Regex("\\s*\\[.*?\\]\\s*$"), "").trim()
                if (clean.isNotEmpty()) headers[clean] = c
            }
            if (headers.size < 2) continue

            // Score this row by checking how many headers match accounting terms
            var score = 0
            for (h in headers.keys) {
                val lower = h.lowercase()
                if (findHeaderIndex(headers, "Date") == headers[h] ||
                    findHeaderIndex(headers, "VoucherNumber") == headers[h] ||
                    findHeaderIndex(headers, "PartyName") == headers[h] ||
                    findHeaderIndex(headers, "ItemName") == headers[h] ||
                    findHeaderIndex(headers, "Quantity") == headers[h] ||
                    findHeaderIndex(headers, "Rate") == headers[h] ||
                    findHeaderIndex(headers, "Amount") == headers[h] ||
                    findHeaderIndex(headers, "PayToLedger") == headers[h] ||
                    findHeaderIndex(headers, "PayFromLedger") == headers[h] ||
                    findHeaderIndex(headers, "ReceivedFrom") == headers[h] ||
                    findHeaderIndex(headers, "ReceiveInLedger") == headers[h]
                ) {
                    score += 100
                } else if (lower.contains("date") || lower.contains("inv") || lower.contains("bill") ||
                    lower.contains("party") || lower.contains("cust") || lower.contains("vendor") ||
                    lower.contains("item") || lower.contains("prod") || lower.contains("qty") ||
                    lower.contains("rate") || lower.contains("amt") || lower.contains("tax") ||
                    lower.contains("gst") || lower.contains("price") || lower.contains("disc")
                ) {
                    score += 50
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestRowIdx = r
                bestHeaders = headers
            }
        }

        if (bestHeaders.isEmpty()) {
            // Fallback: row 0 or 1
            for (r in listOf(0, 1)) {
                val row = sheet.getRow(r) ?: continue
                val headers = mutableMapOf<String, Int>()
                for (c in 0 until row.lastCellNum.toInt()) {
                    val raw = row.getCell(c)?.toString()?.trim() ?: continue
                    val clean = raw.replace(Regex("\\s*\\[.*?\\]\\s*$"), "").trim()
                    if (clean.isNotEmpty()) headers[clean] = c
                }
                if (headers.size >= 2) return Pair(r, headers)
            }
        }

        return Pair(bestRowIdx, bestHeaders)
    }

    private fun getHeaders(sheet: org.apache.poi.ss.usermodel.Sheet): Map<String, Int> {
        return findHeaderRowAndHeaders(sheet).second
    }

    private fun findHeaderRowIndex(sheet: org.apache.poi.ss.usermodel.Sheet): Int {
        return findHeaderRowAndHeaders(sheet).first
    }

    /**
     * Data rows start after the header + optional notes row.
     */
    private fun findDataStartRow(sheet: org.apache.poi.ss.usermodel.Sheet): Int {
        val headerRow = findHeaderRowIndex(sheet)
        val nextRow = sheet.getRow(headerRow + 1)
        val isNotesRow = nextRow?.getCell(0)?.toString()?.contains("Required", ignoreCase = true) == true
            || nextRow?.getCell(0)?.toString()?.contains("Optional", ignoreCase = true) == true
        return if (isNotesRow) headerRow + 2 else headerRow + 1
    }

    private fun isRowEmpty(row: org.apache.poi.ss.usermodel.Row, colCount: Int): Boolean {
        for (i in 0 until colCount) {
            if (row.getCell(i)?.toString()?.isNotBlank() == true) return false
        }
        return true
    }

    private fun readCellStr(cell: org.apache.poi.ss.usermodel.Cell?): String? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(cell.dateCellValue)
                } else {
                    val d = cell.numericCellValue
                    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    val d = cell.numericCellValue
                    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
                } catch (_: Exception) {
                    cell.toString().trim()
                }
            }
            else -> cell.toString().trim()
        }.takeIf { it.isNotEmpty() }
    }

    private fun cellStr(row: org.apache.poi.ss.usermodel.Row, headers: Map<String, Int>, col: String): String? {
        val exact = headers[col]
        if (exact != null) {
            val v = readCellStr(row.getCell(exact))
            if (v != null) return v
        }
        val synonyms = HEADER_SYNONYMS[col] ?: listOf(col.lowercase())
        val normSyns = synonyms.map { it.lowercase().replace(Regex("[^a-z0-9]"), "") }
        for ((k, idx) in headers) {
            val normK = k.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (normSyns.any { it == normK || normK.startsWith(it) || it.startsWith(normK) }) {
                val v = readCellStr(row.getCell(idx))
                if (v != null) return v
            }
        }
        return null
    }

    private fun optStr(row: org.apache.poi.ss.usermodel.Row, headers: Map<String, Int>, col: String): String? =
        cellStr(row, headers, col)

    private fun optDouble(row: org.apache.poi.ss.usermodel.Row, headers: Map<String, Int>, col: String): Double? {
        val s = cellStr(row, headers, col) ?: return null
        return s.replace(",", "").toDoubleOrNull()
    }

    private fun optBool(row: org.apache.poi.ss.usermodel.Row, headers: Map<String, Int>, col: String): Boolean {
        val s = cellStr(row, headers, col) ?: return false
        return s.uppercase() in listOf("YES", "Y", "TRUE", "1")
    }

    private fun reqStr(
        row: org.apache.poi.ss.usermodel.Row, headers: Map<String, Int>,
        col: String, sheet: String, rowNum: Int, errors: MutableList<ImportError>
    ): String? {
        val v = cellStr(row, headers, col)
        if (v == null) errors += ImportError(sheet, rowNum, col, "'$col' is required but is empty")
        return v
    }

    private fun reqDouble(
        row: org.apache.poi.ss.usermodel.Row, headers: Map<String, Int>,
        col: String, sheet: String, rowNum: Int, errors: MutableList<ImportError>
    ): Double? {
        val s = cellStr(row, headers, col)
        if (s == null) {
            errors += ImportError(sheet, rowNum, col, "'$col' is required but is empty")
            return null
        }
        val v = s.replace(",", "").toDoubleOrNull()
        if (v == null) errors += ImportError(sheet, rowNum, col, "'$col' must be a number, got: '$s'")
        return v
    }

    private fun reqDate(
        row: org.apache.poi.ss.usermodel.Row, headers: Map<String, Int>,
        col: String, sheet: String, rowNum: Int, errors: MutableList<ImportError>
    ): Date? {
        val idx = findHeaderIndex(headers, col)
        if (idx != null) {
            val cell = row.getCell(idx)
            if (cell != null && cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.dateCellValue
            }
        }
        val s = cellStr(row, headers, col)
        if (s == null) {
            return Date()
        }
        val numDate = s.toDoubleOrNull()
        if (numDate != null && numDate > 20000 && numDate < 80000) {
            try {
                return DateUtil.getJavaDate(numDate)
            } catch (_: Exception) {}
        }
        for (fmt in DATE_FORMATS) {
            try {
                return SimpleDateFormat(fmt, Locale.getDefault()).parse(s)
            } catch (_: Exception) {}
        }
        return Date()
    }
    // ──────────────────────────────────────────────────────────────────────────
    //  Web / Desktop API  (validate → stage → commit)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Result type returned by [validateBytes] for the web validate endpoint.
     * On success, [errors] is empty and [toJson] can be used to stage the data.
     */
    class WebImportResult(
        val voucherType: String,
        val summary: String,
        val errors: List<String>,
        internal val rawResult: ImportResult
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put("voucherType", voucherType)
            json.put("summary", summary)
            json.put("errors", JSONArray(errors))
            // Serialise the parsed vouchers as compact JSON for staging
            when (val r = rawResult) {
                is ImportResult.SaleResult -> {
                    val arr = JSONArray()
                    r.vouchers.forEach { v ->
                        val vo = JSONObject()
                        vo.put("t", "sale")
                        vo.put("vn", v.voucherNumber)
                        vo.put("dt", v.date.time)
                        vo.put("party", v.partyName)
                        vo.put("pos", v.placeOfSupply ?: "")
                        vo.put("igst", v.isIGST)
                        vo.put("fin", v.isFinanced)
                        vo.put("finBy", v.financedBy ?: "")
                        vo.put("narr", v.narration ?: "")
                        vo.put("cashLed", v.cashLedger ?: "")
                        vo.put("cashAmt", v.cashAmount)
                        vo.put("roff", v.roundOff)
                        val items = JSONArray()
                        v.items.forEach { i ->
                            val io = JSONObject()
                            io.put("nm", i.itemName)
                            io.put("cat", i.category ?: "")
                            io.put("qty", i.quantity)
                            io.put("unit", i.unit); io.put("rate", i.rate)
                            io.put("hsn", i.hsnCode ?: ""); io.put("gst", i.gstRate)
                            io.put("disc", i.discount)
                            items.put(io)
                        }
                        vo.put("items", items)
                        arr.put(vo)
                    }
                    json.put("data", arr)
                }
                is ImportResult.PurchaseResult -> {
                    val arr = JSONArray()
                    r.vouchers.forEach { v ->
                        val vo = JSONObject()
                        vo.put("t", "purchase")
                        vo.put("vn", v.voucherNumber); vo.put("dt", v.date.time)
                        vo.put("party", v.partyName); vo.put("sinv", v.supplierInvNo ?: "")
                        vo.put("pos", v.placeOfSupply ?: ""); vo.put("igst", v.isIGST)
                        vo.put("rc", v.reverseCharge); vo.put("narr", v.narration ?: "")
                        vo.put("cashLed", v.cashLedger ?: ""); vo.put("cashAmt", v.cashAmount)
                        val items = JSONArray()
                        v.items.forEach { i ->
                            val io = JSONObject()
                            io.put("nm", i.itemName)
                            io.put("cat", i.category ?: "")
                            io.put("qty", i.quantity)
                            io.put("unit", i.unit); io.put("rate", i.rate)
                            io.put("hsn", i.hsnCode ?: ""); io.put("gst", i.gstRate)
                            io.put("disc", i.discount)
                            items.put(io)
                        }
                        vo.put("items", items); arr.put(vo)
                    }
                    json.put("data", arr)
                }
                is ImportResult.PaymentResult -> {
                    val arr = JSONArray()
                    r.vouchers.forEach { v ->
                        val vo = JSONObject()
                        vo.put("t", "payment")
                        vo.put("vn", v.voucherNumber); vo.put("dt", v.date.time)
                        vo.put("from", v.payFromLedger); vo.put("to", v.payToLedger)
                        vo.put("amt", v.amount); vo.put("fin", v.isFinanced)
                        vo.put("finBy", v.financedBy ?: ""); vo.put("narr", v.narration ?: "")
                        vo.put("bill", v.billReference ?: ""); vo.put("chq", v.chequeNo ?: "")
                        arr.put(vo)
                    }
                    json.put("data", arr)
                }
                is ImportResult.ReceiptResult -> {
                    val arr = JSONArray()
                    r.vouchers.forEach { v ->
                        val vo = JSONObject()
                        vo.put("t", "receipt")
                        vo.put("vn", v.voucherNumber); vo.put("dt", v.date.time)
                        vo.put("into", v.receiveInLedger); vo.put("from", v.receivedFrom)
                        vo.put("amt", v.amount); vo.put("fin", v.isFinanced)
                        vo.put("finBy", v.financedBy ?: ""); vo.put("narr", v.narration ?: "")
                        vo.put("bill", v.billReference ?: ""); vo.put("chq", v.chequeNo ?: "")
                        arr.put(vo)
                    }
                    json.put("data", arr)
                }
                else -> {}
            }
            return json
        }
    }

    /** Parse raw .xlsx bytes, validate every row, return a [WebImportResult]. */
    fun validateBytes(context: Context, companyId: Int, bytes: ByteArray): WebImportResult {
        val result = parse(ByteArrayInputStream(bytes), context, companyId)
        return if (result.hasErrors) {
            WebImportResult(
                voucherType = result.detectedType.name,
                summary = "",
                errors = result.errorMessages,
                rawResult = result
            )
        } else {
            val count = when (result) {
                is ImportResult.SaleResult -> result.vouchers.size
                is ImportResult.PurchaseResult -> result.vouchers.size
                is ImportResult.PaymentResult -> result.vouchers.size
                is ImportResult.ReceiptResult -> result.vouchers.size
                else -> 0
            }
            WebImportResult(
                voucherType = result.detectedType.name.lowercase().replaceFirstChar { it.uppercase() },
                summary = "${result.detectedType.name.lowercase().replaceFirstChar { it.uppercase() }} · $count voucher(s) ready to post\nNo validation errors found.",
                errors = emptyList(),
                rawResult = result
            )
        }
    }

    /**
     * Deserialise the staged JSON (produced by [WebImportResult.toJson]) and
     * write all vouchers to the database. Returns the number of vouchers posted.
     */
    fun commitFromJson(context: Context, companyId: Int, json: JSONObject): Int {
        val db = com.minhas.teller.data.AppDatabase.getDatabase(context)
        val data = json.optJSONArray("data") ?: return 0
        var count = 0

        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Default) {
            val allLedgers = db.ledgerDao().getAllLedgersSync(companyId).toMutableList()

            suspend fun ensureLedger(name: String, group: String): com.minhas.teller.data.entities.Ledger {
                val trimmed = name.trim()
                val cleanName = when {
                    trimmed.isBlank() -> "General Account"
                    trimmed.equals("Sundry Debtors", ignoreCase = true) -> "General Customer"
                    trimmed.equals("Sundry Creditors", ignoreCase = true) -> "General Supplier"
                    trimmed.equals("Bank Accounts", ignoreCase = true) -> "Primary Bank"
                    trimmed.equals("Cash-in-hand", ignoreCase = true) -> "Cash"
                    TallyBackupExtractor.isSystemToken(trimmed) -> "General Account"
                    else -> trimmed
                }
                val existing = allLedgers.find { it.name.equals(cleanName, ignoreCase = true) }
                if (existing != null) return existing
                val newLedger = com.minhas.teller.data.entities.Ledger(
                    name = cleanName,
                    group = group,
                    openingBalance = 0L,
                    companyId = companyId
                )
                val newId = db.ledgerDao().insert(newLedger).toInt()
                val inserted = newLedger.copy(id = newId)
                allLedgers.add(inserted)
                return inserted
            }

            suspend fun ensureStockItem(name: String, unitName: String, hsn: String?, gstRate: Double): com.minhas.teller.data.entities.StockItem {
                val existing = db.stockItemDao().getStockItemByName(companyId, name)
                if (existing != null) return existing
                val unitEntity = db.unitDao().getUnitByName(companyId, unitName)
                    ?: run {
                        val uid = db.unitDao().insert(com.minhas.teller.data.entities.Unit(name = unitName, formalName = unitName, companyId = companyId)).toInt()
                        com.minhas.teller.data.entities.Unit(id = uid, name = unitName, formalName = unitName, companyId = companyId)
                    }
                val item = com.minhas.teller.data.entities.StockItem(
                    name = name, unitId = unitEntity.id, companyId = companyId,
                    hsnCode = hsn, gstRate = gstRate
                )
                val newId = db.stockItemDao().insert(item).toInt()
                return item.copy(id = newId)
            }

            for (i in 0 until data.length()) {
                val vo = data.getJSONObject(i)
                val type = vo.optString("t", "")
                val dateMs = vo.optLong("dt", System.currentTimeMillis())
                val date = java.util.Date(dateMs)
                val voucherNumber = vo.optString("vn", "").ifBlank { "V-${System.currentTimeMillis() % 1000000}" }
                val narration = vo.optString("narr", "").ifBlank { null }

                when (type) {
                    "sale" -> {
                        val partyName = vo.optString("party", "Unknown Party")
                        val financedBy = vo.optString("finBy", "").ifBlank { null }
                        val isFinanced = vo.optBoolean("fin", false) || financedBy != null

                        // Ensure customer account exists
                        val partyLedger = ensureLedger(partyName, "Sundry Debtors")
                        val salesLedger = ensureLedger("Sales Account", "Sales Accounts")

                        // If written finance company is not in the app, auto-create its account
                        if (financedBy != null) {
                            ensureLedger(financedBy, "Sundry Debtors")
                        }

                        val items = vo.optJSONArray("items")
                        var totalAmt = 0.0
                        val inventoryEntries = mutableListOf<com.minhas.teller.data.entities.InventoryEntry>()

                        if (items != null) {
                            for (j in 0 until items.length()) {
                                val it = items.getJSONObject(j)
                                val itemName = it.optString("nm", "Item")
                                val qty = it.optDouble("qty", 1.0)
                                val rate = it.optDouble("rate", 0.0)
                                val gstRate = it.optDouble("gst", 0.0)
                                val disc = it.optDouble("disc", 0.0)
                                val unitName = it.optString("unit", "Nos")
                                val hsnCode = it.optString("hsn", "").ifBlank { null }

                                val stockItem = ensureStockItem(itemName, unitName, hsnCode, gstRate)
                                val lineAmt = qty * rate * (1 - disc / 100)
                                totalAmt += lineAmt
                                val taxableAmt = if (gstRate > 0) lineAmt / (1 + gstRate / 100) else lineAmt
                                val taxAmt = lineAmt - taxableAmt

                                inventoryEntries.add(
                                    com.minhas.teller.data.entities.InventoryEntry(
                                        voucherId = 0,
                                        stockItemId = stockItem.id,
                                        quantity = qty,
                                        rate = rate,
                                        amount = lineAmt.rupeesToPaise(),
                                        hsnCode = hsnCode ?: stockItem.hsnCode,
                                        gstRate = gstRate,
                                        cgstRate = gstRate / 2,
                                        sgstRate = gstRate / 2,
                                        taxableAmount = taxableAmt.rupeesToPaise(),
                                        taxAmount = maxOf(0.0, taxAmt).rupeesToPaise(),
                                        totalAmount = lineAmt.rupeesToPaise()
                                    )
                                )
                            }
                        }

                        val narrationStr = buildString {
                            if (narration != null) append(narration)
                            if (financedBy != null) {
                                if (isNotEmpty()) append(" | ")
                                append("Financed by $financedBy")
                            }
                        }.ifBlank { null }

                        val voucherId = db.voucherDao().insert(
                            com.minhas.teller.data.entities.Voucher(
                                companyId = companyId,
                                date = date,
                                type = "Sales",
                                voucherNumber = voucherNumber,
                                narration = narrationStr,
                                refDisplayName = partyName, // Customer name added to sale voucher
                                isFinanced = isFinanced,
                                customField = financedBy ?: partyName
                            )
                        ).toInt()

                        db.voucherEntryDao().insert(
                            com.minhas.teller.data.entities.VoucherEntry(
                                voucherId = voucherId,
                                ledgerId = partyLedger.id,
                                amount = totalAmt.rupeesToPaise(),
                                isDebit = true
                            )
                        )
                        db.voucherEntryDao().insert(
                            com.minhas.teller.data.entities.VoucherEntry(
                                voucherId = voucherId,
                                ledgerId = salesLedger.id,
                                amount = totalAmt.rupeesToPaise(),
                                isDebit = false
                            )
                        )

                        for (inv in inventoryEntries) {
                            db.inventoryEntryDao().insert(inv.copy(voucherId = voucherId))
                        }
                        count++
                    }
                    "purchase" -> {
                        val partyName = vo.optString("party", "Unknown Party")
                        val partyLedger = ensureLedger(partyName, "Sundry Creditors")
                        val purchaseLedger = ensureLedger("Purchase Account", "Purchase Accounts")

                        val items = vo.optJSONArray("items")
                        var totalAmt = 0.0
                        val inventoryEntries = mutableListOf<com.minhas.teller.data.entities.InventoryEntry>()

                        if (items != null) {
                            for (j in 0 until items.length()) {
                                val it = items.getJSONObject(j)
                                val itemName = it.optString("nm", "Item")
                                val qty = it.optDouble("qty", 1.0)
                                val rate = it.optDouble("rate", 0.0)
                                val gstRate = it.optDouble("gst", 0.0)
                                val disc = it.optDouble("disc", 0.0)
                                val unitName = it.optString("unit", "Nos")
                                val hsnCode = it.optString("hsn", "").ifBlank { null }

                                val stockItem = ensureStockItem(itemName, unitName, hsnCode, gstRate)
                                val lineAmt = qty * rate * (1 - disc / 100)
                                totalAmt += lineAmt
                                val taxableAmt = if (gstRate > 0) lineAmt / (1 + gstRate / 100) else lineAmt
                                val taxAmt = lineAmt - taxableAmt

                                inventoryEntries.add(
                                    com.minhas.teller.data.entities.InventoryEntry(
                                        voucherId = 0,
                                        stockItemId = stockItem.id,
                                        quantity = qty,
                                        rate = rate,
                                        amount = lineAmt.rupeesToPaise(),
                                        hsnCode = hsnCode ?: stockItem.hsnCode,
                                        gstRate = gstRate,
                                        cgstRate = gstRate / 2,
                                        sgstRate = gstRate / 2,
                                        taxableAmount = taxableAmt.rupeesToPaise(),
                                        taxAmount = maxOf(0.0, taxAmt).rupeesToPaise(),
                                        totalAmount = lineAmt.rupeesToPaise()
                                    )
                                )
                            }
                        }

                        val voucherId = db.voucherDao().insert(
                            com.minhas.teller.data.entities.Voucher(
                                companyId = companyId,
                                date = date,
                                type = "Purchase",
                                voucherNumber = voucherNumber,
                                narration = narration,
                                refDisplayName = partyName
                            )
                        ).toInt()

                        db.voucherEntryDao().insert(
                            com.minhas.teller.data.entities.VoucherEntry(
                                voucherId = voucherId,
                                ledgerId = purchaseLedger.id,
                                amount = totalAmt.rupeesToPaise(),
                                isDebit = true
                            )
                        )
                        db.voucherEntryDao().insert(
                            com.minhas.teller.data.entities.VoucherEntry(
                                voucherId = voucherId,
                                ledgerId = partyLedger.id,
                                amount = totalAmt.rupeesToPaise(),
                                isDebit = false
                            )
                        )

                        for (inv in inventoryEntries) {
                            db.inventoryEntryDao().insert(inv.copy(voucherId = voucherId))
                        }
                        count++
                    }
                    "payment" -> {
                        val amt = vo.optDouble("amt", 0.0)
                        val fromLedgerName = vo.optString("from", "Cash").ifBlank { "Cash" }
                        val toLedgerName = vo.optString("to", "Unknown Party").ifBlank { "Unknown Party" }
                        val billRef = vo.optString("bill", "").ifBlank { null }

                        val fromLedger = ensureLedger(fromLedgerName, "Bank Accounts")
                        val toLedger = ensureLedger(toLedgerName, "Sundry Creditors")

                        val voucherId = db.voucherDao().insert(
                            com.minhas.teller.data.entities.Voucher(
                                companyId = companyId,
                                date = date,
                                type = "Payment",
                                voucherNumber = voucherNumber,
                                narration = narration,
                                refDisplayName = toLedgerName
                            )
                        ).toInt()

                        val entryId = db.voucherEntryDao().insert(
                            com.minhas.teller.data.entities.VoucherEntry(
                                voucherId = voucherId,
                                ledgerId = toLedger.id,
                                amount = amt.rupeesToPaise(),
                                isDebit = true
                            )
                        ).toInt()

                        db.voucherEntryDao().insert(
                            com.minhas.teller.data.entities.VoucherEntry(
                                voucherId = voucherId,
                                ledgerId = fromLedger.id,
                                amount = amt.rupeesToPaise(),
                                isDebit = false
                            )
                        )

                        if (!billRef.isNullOrBlank()) {
                            db.billAllocationDao().insert(
                                com.minhas.teller.data.entities.BillAllocation(
                                    voucherEntryId = entryId,
                                    refName = billRef,
                                    refType = "Against",
                                    amount = amt.rupeesToPaise()
                                )
                            )
                        }
                        count++
                    }
                    "receipt" -> {
                        val amt = vo.optDouble("amt", 0.0)
                        val fromLedgerName = vo.optString("from", "Unknown Party").ifBlank { "Unknown Party" }
                        val toLedgerName = vo.optString("into", "Cash").ifBlank { "Cash" }
                        val billRef = vo.optString("bill", "").ifBlank { null }

                        val toLedger = ensureLedger(toLedgerName, "Cash-in-hand")
                        val fromLedger = ensureLedger(fromLedgerName, "Sundry Debtors")

                        val voucherId = db.voucherDao().insert(
                            com.minhas.teller.data.entities.Voucher(
                                companyId = companyId,
                                date = date,
                                type = "Receipt",
                                voucherNumber = voucherNumber,
                                narration = narration,
                                refDisplayName = fromLedgerName
                            )
                        ).toInt()

                        db.voucherEntryDao().insert(
                            com.minhas.teller.data.entities.VoucherEntry(
                                voucherId = voucherId,
                                ledgerId = toLedger.id,
                                amount = amt.rupeesToPaise(),
                                isDebit = true
                            )
                        )

                        val entryId = db.voucherEntryDao().insert(
                            com.minhas.teller.data.entities.VoucherEntry(
                                voucherId = voucherId,
                                ledgerId = fromLedger.id,
                                amount = amt.rupeesToPaise(),
                                isDebit = false
                            )
                        ).toInt()

                        if (!billRef.isNullOrBlank()) {
                            db.billAllocationDao().insert(
                                com.minhas.teller.data.entities.BillAllocation(
                                    voucherEntryId = entryId,
                                    refName = billRef,
                                    refType = "Against",
                                    amount = amt.rupeesToPaise()
                                )
                            )
                        }
                        count++
                    }
                }
            }
        }
        return count
    }

    /**
     * Self-corrects an Excel workbook:
     * 1. Creates missing columns (e.g. ItemCategory, HSNCode, GSTRate).
     * 2. Sets the category for unresolved or unassigned items based on [categoryAssignments] or [defaultCategory].
     * 3. Auto-computes and fills in HSN code and GST rate for each item row.
     * Returns the modified workbook bytes ready for immediate re-parsing or downloading.
     */
    fun correctWorkbook(
        inputBytes: ByteArray,
        categoryAssignments: Map<String, String> = emptyMap(),
        defaultCategory: String = "",
        context: Context? = null,
        companyId: Int? = null
    ): ByteArray {
        val wb = XSSFWorkbook(ByteArrayInputStream(inputBytes))
        val itemSheets = listOfNotNull(findSheet(wb, "Sale_Items"), findSheet(wb, "Purchase_Items"))

        for (sheet in itemSheets) {
            val headers = getHeaders(sheet).toMutableMap()
            val headerRowIdx = findHeaderRowIndex(sheet)
            val headerRow = sheet.getRow(headerRowIdx) ?: continue

            // 1. Ensure ItemCategory column exists
            var catColIdx = headers["ItemCategory"] ?: headers["Category"]
            if (catColIdx == null) {
                catColIdx = headerRow.lastCellNum.toInt().coerceAtLeast(0)
                headerRow.createCell(catColIdx).setCellValue("ItemCategory [Text]")
                headers["ItemCategory"] = catColIdx
            }

            // 2. Ensure HSNCode column exists
            var hsnColIdx = headers["HSNCode"]
            if (hsnColIdx == null) {
                hsnColIdx = headerRow.lastCellNum.toInt().coerceAtLeast(0)
                headerRow.createCell(hsnColIdx).setCellValue("HSNCode [Text]")
                headers["HSNCode"] = hsnColIdx
            }

            // 3. Ensure GSTRate column exists
            var gstColIdx = headers["GSTRate"]
            if (gstColIdx == null) {
                gstColIdx = headerRow.lastCellNum.toInt().coerceAtLeast(0)
                headerRow.createCell(gstColIdx).setCellValue("GSTRate [Number]")
                headers["GSTRate"] = gstColIdx
            }

            // 4. Update data rows
            val dataStart = findDataStartRow(sheet)
            val itemNameIdx = headers["ItemName"] ?: continue

            for (r in dataStart..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                if (isRowEmpty(row, headers.size)) continue
                val itemName = row.getCell(itemNameIdx)?.toString()?.trim() ?: continue
                if (itemName.isEmpty()) continue

                // Check existing category
                var currentCat = cellStr(row, headers, "ItemCategory") ?: cellStr(row, headers, "Category")
                if (currentCat.isNullOrBlank()) {
                    val assigned = categoryAssignments[itemName]?.takeIf { it.isNotBlank() }
                        ?: defaultCategory.takeIf { it.isNotBlank() }
                    if (!assigned.isNullOrBlank()) {
                        val catCell = row.getCell(catColIdx) ?: row.createCell(catColIdx)
                        catCell.setCellValue(assigned)
                        currentCat = assigned
                    }
                }

                // Resolve HSN & Tax with the category
                val explicitHsn = cellStr(row, headers, "HSNCode")
                val explicitGst = optDouble(row, headers, "GSTRate")
                val taxRes = resolveHsnAndTax(itemName, currentCat, explicitHsn, explicitGst, context, companyId)
                if (taxRes != null) {
                    val hsnVal = taxRes.first
                    if (explicitHsn.isNullOrBlank() && !hsnVal.isNullOrBlank()) {
                        val hsnCell = row.getCell(hsnColIdx) ?: row.createCell(hsnColIdx)
                        hsnCell.setCellValue(hsnVal)
                    }
                    if (explicitGst == null || explicitGst == 0.0) {
                        val gstCell = row.getCell(gstColIdx) ?: row.createCell(gstColIdx)
                        gstCell.setCellValue(taxRes.second)
                    }
                }
            }
        }

        val out = java.io.ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        return out.toByteArray()
    }
}
