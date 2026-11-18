package com.minhas.teller.ui.viewmodels

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.BillAllocation
import com.minhas.teller.data.entities.ImportJob
import com.minhas.teller.data.entities.ImportMapping
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.utils.rupeesToPaise
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Unit
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.utils.ExcelImportSampleGenerator
import com.minhas.teller.utils.TallyBackupExtractor
import com.minhas.teller.utils.TallyEntityConverter
import com.minhas.teller.utils.UniversalBackupImporter
import com.minhas.teller.utils.UniversalParser
import com.minhas.teller.utils.VoucherExcelImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.room.withTransaction
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImportCleanupUiState(
    val activeJobs: List<ImportJob> = emptyList(),
    val parsedHeaders: List<String> = emptyList(),
    val parsedRows: List<UniversalParser.TabularRow> = emptyList(),
    val detectedMappings: Map<String, Pair<String, String>> = emptyMap(), // Header -> (Entity, Field)
    val customMappings: Map<String, Pair<String, String>> = emptyMap(),
    val dryRunSales: Double = 0.0,
    val dryRunPurchases: Double = 0.0,
    val dryRunLedgersCount: Int = 0,
    val dryRunVouchersCount: Int = 0,
    val dryRunStockCount: Int = 0,
    val dryRunDuplicatesSkipped: Int = 0,
    val isDryRunVerified: Boolean = false,
    val isProcessing: Boolean = false,
    val processingStatus: String = "",
    val progressFraction: Float = 0f,
    val progressPercent: Int = 0,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val progressStage: String = "",
    val importSuccessMessage: String? = null,
    val importErrors: List<String> = emptyList(),
    val importError: String? = null,
    val detectedVoucherType: VoucherExcelImporter.ImportType? = null,
    val voucherImportSummary: String? = null,
    val isVoucherImportReady: Boolean = false,
    val isVoucherImportSuccess: Boolean = false,
    val pendingSaleVouchers: List<VoucherExcelImporter.ParsedSaleVoucher> = emptyList(),
    val pendingPurchaseVouchers: List<VoucherExcelImporter.ParsedPurchaseVoucher> = emptyList(),
    val pendingPaymentVouchers: List<VoucherExcelImporter.ParsedPaymentVoucher> = emptyList(),
    val pendingReceiptVouchers: List<VoucherExcelImporter.ParsedReceiptVoucher> = emptyList(),
    val showCorrectionDialog: Boolean = false,
    val unrecognizedItems: List<String> = emptyList(),
    val missingColumns: List<String> = emptyList(),
    val isSelfCorrected: Boolean = false,
    val correctedFileBytes: ByteArray? = null
)

class ImportCleanupViewModel(
    private val db: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportCleanupUiState())
    val uiState: StateFlow<ImportCleanupUiState> = _uiState

    private var activeJobId: Int = 0
    private var parsedTallyResult: UniversalParser.TallyImportResult? = null
    private var cachedFileBytes: ByteArray? = null
    private var allParsedRows: List<UniversalParser.TabularRow> = emptyList()

    private fun getRealFileName(context: Context, uri: Uri): String {
        val path = uri.path ?: ""
        val cut = path.lastIndexOf('/')
        val name = if (cut != -1) path.substring(cut + 1) else path
        return name.ifBlank { "selected_file" }
    }

    fun loadJobs(companyId: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val jobs = db.importJobDao().getJobsForCompany(companyId)
            _uiState.value = _uiState.value.copy(activeJobs = jobs)
        }
    }

    /**
     * Ingests ANY backup file (Tally .001/TBK/XML, Busy .bsh/.zip, Excel, CSV) automatically.
     */
    fun startUniversalImport(context: Context, companyId: Int, fileUri: Uri) {
        val filename = getRealFileName(context, fileUri)
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            processingStatus = "Opening $filename...",
            progressFraction = 0.05f,
            progressPercent = 5,
            processedCount = 0,
            totalCount = 0,
            progressStage = "Opening File",
            importError = null,
            importErrors = emptyList(),
            importSuccessMessage = null,
            isDryRunVerified = false,
            isVoucherImportReady = false,
            isVoucherImportSuccess = false
        )

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val inputStream = context.contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        progressFraction = 0f,
                        progressPercent = 0,
                        importError = "Could not open '$filename'. Please ensure Teller has permission to read the file."
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    processingStatus = "Reading $filename into memory...",
                    progressFraction = 0.10f,
                    progressPercent = 10,
                    progressStage = "Reading File Bytes"
                )
                val bytes = inputStream.readBytes()
                inputStream.close()
                cachedFileBytes = bytes

                _uiState.value = _uiState.value.copy(
                    processingStatus = "Pre-loading inventory and items from company...",
                    progressFraction = 0.18f,
                    progressPercent = 18,
                    progressStage = "Pre-loading Inventory"
                )

                val existingItemsMap = try {
                    db.stockItemDao().getAllStockItems(companyId).associateBy { it.name.trim().lowercase() }
                } catch (_: Throwable) {
                    emptyMap()
                }

                _uiState.value = _uiState.value.copy(
                    processingStatus = "Detecting format and extracting records from $filename...",
                    progressFraction = 0.25f,
                    progressPercent = 25,
                    progressStage = "Analyzing Structure"
                )

                val result = UniversalBackupImporter.parseAnyFile(
                    inputStream = ByteArrayInputStream(bytes),
                    filename = filename,
                    context = context,
                    companyId = companyId,
                    existingItemsMap = existingItemsMap,
                    onProgress = { step, current, total, percent ->
                        _uiState.value = _uiState.value.copy(
                            processingStatus = step,
                            progressFraction = percent / 100f,
                            progressPercent = percent,
                            processedCount = current,
                            totalCount = total,
                            progressStage = "Processing Records"
                        )
                    }
                )

                if (result.errorMessage != null) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        progressFraction = 0f,
                        progressPercent = 0,
                        importError = result.errorMessage
                    )
                    return@launch
                }

                if (result.tallyResult != null) {
                    parsedTallyResult = result.tallyResult
                    val tRes = result.tallyResult
                    val totalRecords = tRes.ledgers.size + tRes.vouchers.size + tRes.stockItems.size
                    _uiState.value = _uiState.value.copy(
                        dryRunLedgersCount = tRes.ledgers.size,
                        dryRunVouchersCount = tRes.vouchers.size,
                        dryRunStockCount = tRes.stockItems.size,
                        dryRunSales = tRes.vouchers.filter { it.voucherType.equals("Sales", true) }
                            .flatMap { it.entries }
                            .filter { it.isDebit }
                            .sumOf { it.amount },
                        dryRunPurchases = tRes.vouchers.filter { it.voucherType.equals("Purchase", true) }
                            .flatMap { it.entries }
                            .filter { it.isDebit }
                            .sumOf { it.amount },
                        isDryRunVerified = true,
                        isProcessing = false,
                        progressFraction = 1f,
                        progressPercent = 100,
                        processedCount = totalRecords,
                        totalCount = totalRecords,
                        processingStatus = "Verified $totalRecords records from $filename",
                        importSuccessMessage = "Extracted ${tRes.ledgers.size} ledgers, ${tRes.stockItems.size} stock items, and ${tRes.vouchers.size} vouchers from $filename."
                    )
                } else if (result.excelResult != null) {
                    _uiState.value = _uiState.value.copy(
                        progressFraction = 1f,
                        progressPercent = 100
                    )
                    populateReadyVouchers(result.excelResult)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        progressFraction = 0f,
                        progressPercent = 0,
                        importError = "No usable accounting data could be parsed from '$filename'. Check that the file contains Tally, Busy, Excel, or CSV records."
                    )
                }
            } catch (t: Throwable) {
                Log.e("TellerImport", "Failed to process backup file '$filename'", t)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    progressFraction = 0f,
                    progressPercent = 0,
                    importError = "Failed while processing '$filename' at stage '${_uiState.value.progressStage}':\n${t.message ?: t.javaClass.simpleName}\n\nDetails: ${t.localizedMessage ?: "Unknown failure"}"
                )
            }
        }
    }

    /**
     * Start parsing a Tally XML or CSV file.
     */
    fun startImportJob(context: Context, companyId: Int, fileUri: Uri, type: String) {
        val filename = getRealFileName(context, fileUri)
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            processingStatus = "Reading $filename...",
            progressFraction = 0.15f,
            progressPercent = 15,
            progressStage = "Reading File",
            importError = null,
            importErrors = emptyList(),
            importSuccessMessage = null,
            isDryRunVerified = false,
            isVoucherImportReady = false,
            isVoucherImportSuccess = false
        )

        if (type == "excel" || type == "excel_voucher") {
            parseVoucherExcel(context, companyId, fileUri)
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                // 1. Create a staging Job record
                val job = ImportJob(
                    companyId = companyId,
                    type = type,
                    status = "parsing"
                )
                val jobId = db.importJobDao().insert(job).toInt()
                activeJobId = jobId
                loadJobs(companyId)

                val inputStream: InputStream? = context.contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    updateJobStatus(jobId, "failed", error = "Could not open file input stream")
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        progressFraction = 0f,
                        progressPercent = 0,
                        importError = "Could not open '$filename'"
                    )
                    return@launch
                }

                if (type == "tally_xml") {
                    _uiState.value = _uiState.value.copy(
                        processingStatus = "Parsing Tally XML structures...",
                        progressFraction = 0.5f,
                        progressPercent = 50,
                        progressStage = "Parsing XML"
                    )
                    val result = UniversalParser.parseTallyXml(inputStream)
                    inputStream.close()
                    parsedTallyResult = result

                    val totalRecords = result.ledgers.size + result.vouchers.size + result.stockItems.size

                    _uiState.value = _uiState.value.copy(
                        dryRunLedgersCount = result.ledgers.size,
                        dryRunVouchersCount = result.vouchers.size,
                        dryRunStockCount = result.stockItems.size,
                        dryRunSales = result.vouchers.filter { it.voucherType.equals("Sales", true) }.sumOf { v ->
                            v.entries.filter { it.isDebit }.sumOf { it.amount }
                        },
                        dryRunPurchases = result.vouchers.filter { it.voucherType.equals("Purchase", true) }.sumOf { v ->
                            v.entries.filter { it.isDebit }.sumOf { it.amount }
                        },
                        isDryRunVerified = true,
                        isProcessing = false,
                        progressFraction = 1f,
                        progressPercent = 100,
                        processedCount = totalRecords,
                        totalCount = totalRecords,
                        processingStatus = "Verified $totalRecords records from $filename",
                        importSuccessMessage = "Extracted ${result.ledgers.size} ledgers and ${result.vouchers.size} vouchers from '$filename'."
                    )
                    updateJobStatus(jobId, "ready_to_post", rowCount = result.ledgers.size + result.vouchers.size, summary = "Found ${result.ledgers.size} ledgers, ${result.vouchers.size} vouchers")
                } else {
                    // CSV
                    _uiState.value = _uiState.value.copy(
                        processingStatus = "Parsing CSV table rows...",
                        progressFraction = 0.5f,
                        progressPercent = 50,
                        progressStage = "Parsing CSV"
                    )
                    val tabularResult = UniversalParser.parseCsv(inputStream)
                    inputStream.close()
                    allParsedRows = tabularResult.rows
                    val autoMappings = UniversalParser.detectColumns(tabularResult.headers)

                    _uiState.value = _uiState.value.copy(
                        parsedHeaders = tabularResult.headers,
                        parsedRows = tabularResult.rows.take(20),
                        detectedMappings = autoMappings,
                        customMappings = autoMappings,
                        isProcessing = false,
                        progressFraction = 1f,
                        progressPercent = 100,
                        processedCount = tabularResult.rows.size,
                        totalCount = tabularResult.rows.size,
                        processingStatus = "Loaded ${tabularResult.rows.size} rows",
                        importSuccessMessage = "Loaded ${tabularResult.rows.size} rows from '$filename'. Review column schema below and tap 'Post Mapped Spreadsheets'."
                    )
                    updateJobStatus(jobId, "needs_mapping", rowCount = tabularResult.rows.size, summary = "Tabular headers parsed")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    progressFraction = 0f,
                    progressPercent = 0,
                    importError = "Failed to parse $filename: ${e.localizedMessage ?: "Unknown error"}"
                )
                if (activeJobId > 0) {
                    updateJobStatus(activeJobId, "failed", error = e.localizedMessage)
                }
            }
        }
    }

    /**
     * Parses an Excel voucher file using VoucherExcelImporter.
     */
    fun parseVoucherExcel(context: Context, companyId: Int, fileUri: Uri) {
        val filename = getRealFileName(context, fileUri)
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            processingStatus = "Opening $filename...",
            progressFraction = 0.05f,
            progressPercent = 5,
            processedCount = 0,
            totalCount = 0,
            progressStage = "Opening File",
            importError = null,
            importErrors = emptyList(),
            importSuccessMessage = null,
            isVoucherImportReady = false,
            isVoucherImportSuccess = false
        )
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        progressFraction = 0f,
                        progressPercent = 0,
                        importError = "Could not open '$filename'. Please ensure file read permissions are granted."
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    processingStatus = "Reading $filename into memory...",
                    progressFraction = 0.15f,
                    progressPercent = 15,
                    progressStage = "Reading File Bytes"
                )
                val bytes = inputStream.readBytes()
                inputStream.close()
                cachedFileBytes = bytes

                _uiState.value = _uiState.value.copy(
                    processingStatus = "Pre-loading inventory and items from company...",
                    progressFraction = 0.25f,
                    progressPercent = 25,
                    progressStage = "Caching Company Items"
                )

                val existingItemsMap = try {
                    db.stockItemDao().getAllStockItems(companyId).associateBy { it.name.trim().lowercase() }
                } catch (_: Throwable) {
                    emptyMap()
                }

                _uiState.value = _uiState.value.copy(
                    processingStatus = "Analyzing workbook sheets and structure...",
                    progressFraction = 0.35f,
                    progressPercent = 35,
                    progressStage = "Parsing Sheet Structure"
                )

                val result = VoucherExcelImporter.parse(
                    inputStream = ByteArrayInputStream(bytes),
                    context = context,
                    companyId = companyId,
                    existingItemsMap = existingItemsMap,
                    onProgress = { step, current, total, percent ->
                        _uiState.value = _uiState.value.copy(
                            processingStatus = step,
                            progressFraction = percent / 100f,
                            progressPercent = percent,
                            processedCount = current,
                            totalCount = total,
                            progressStage = "Validating Records"
                        )
                    }
                )

                when (result) {
                    is VoucherExcelImporter.ImportResult.UnknownFormat -> {
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            progressFraction = 0f,
                            progressPercent = 0,
                            importError = "Could not detect voucher type in '$filename'. Ensure sheet contains Sales, Purchase, Payment, or Receipt columns.",
                            importErrors = result.errorMessages
                        )
                    }
                    else -> {
                        if (result.unresolvedItems.isNotEmpty() || result.missingColumns.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(
                                isProcessing = false,
                                progressFraction = 1f,
                                progressPercent = 100,
                                showCorrectionDialog = true,
                                unrecognizedItems = result.unresolvedItems,
                                missingColumns = result.missingColumns,
                                detectedVoucherType = result.detectedType
                            )
                        } else if (result.hasErrors) {
                            _uiState.value = _uiState.value.copy(
                                isProcessing = false,
                                progressFraction = 0f,
                                progressPercent = 0,
                                importError = "Import found ${result.errorMessages.size} issue(s) in '$filename'. Fix these or download the template:",
                                importErrors = result.errorMessages,
                                detectedVoucherType = result.detectedType
                            )
                        } else {
                            // Ready to import!
                            _uiState.value = _uiState.value.copy(
                                progressFraction = 1f,
                                progressPercent = 100,
                                importSuccessMessage = "Validated $filename successfully. Review summary below and tap 'Confirm & Post Vouchers'."
                            )
                            populateReadyVouchers(result)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e("TellerImport", "Failed to parse '$filename'", t)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    progressFraction = 0f,
                    progressPercent = 0,
                    importError = "Failed while parsing '$filename' at stage '${_uiState.value.progressStage}':\n${t.message ?: t.javaClass.simpleName}\n\nTechnical details: ${t.localizedMessage ?: "Unknown failure"}"
                )
            }
        }
    }

    /**
     * Applies smart auto-correction to the uploaded Excel file.
     */
    fun applySmartCorrection(context: Context, companyId: Int, defaultCategory: String) {
        val bytes = cachedFileBytes ?: return
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            processingStatus = "Auto-correcting sheet with HSN & GST codes...",
            showCorrectionDialog = false
        )
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val correctedBytes = VoucherExcelImporter.correctWorkbook(
                    inputBytes = bytes,
                    categoryAssignments = emptyMap(),
                    defaultCategory = defaultCategory,
                    context = context,
                    companyId = companyId
                )
                cachedFileBytes = correctedBytes

                val existingItemsMap = try {
                    db.stockItemDao().getAllStockItems(companyId).associateBy { it.name.trim().lowercase() }
                } catch (_: Throwable) {
                    emptyMap()
                }

                val result = VoucherExcelImporter.parse(
                    inputStream = ByteArrayInputStream(correctedBytes),
                    context = context,
                    companyId = companyId,
                    existingItemsMap = existingItemsMap,
                    onProgress = { step, current, total, percent ->
                        _uiState.value = _uiState.value.copy(
                            processingStatus = step,
                            progressFraction = percent / 100f,
                            progressPercent = percent,
                            processedCount = current,
                            totalCount = total,
                            progressStage = "Validating Corrected Sheet"
                        )
                    }
                )

                if (result.hasErrors) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        importError = "Corrected sheet still has ${result.errorMessages.size} error(s).",
                        importErrors = result.errorMessages,
                        showCorrectionDialog = false
                    )
                } else {
                    populateReadyVouchers(result, isSelfCorrected = true, correctedBytes = correctedBytes)
                }
            } catch (t: Throwable) {
                Log.e("TellerImport", "Auto-correction failed", t)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    importError = "Auto-correction failed: ${t.localizedMessage ?: t.message}",
                    showCorrectionDialog = false
                )
            }
        }
    }

    private fun populateReadyVouchers(
        result: VoucherExcelImporter.ImportResult,
        isSelfCorrected: Boolean = false,
        correctedBytes: ByteArray? = null
    ) {
        val saleList = (result as? VoucherExcelImporter.ImportResult.SaleResult)?.vouchers ?: emptyList()
        val purchaseList = (result as? VoucherExcelImporter.ImportResult.PurchaseResult)?.vouchers ?: emptyList()
        val paymentList = (result as? VoucherExcelImporter.ImportResult.PaymentResult)?.vouchers ?: emptyList()
        val receiptList = (result as? VoucherExcelImporter.ImportResult.ReceiptResult)?.vouchers ?: emptyList()

        val summary = when (result.detectedType) {
            VoucherExcelImporter.ImportType.SALE ->
                "Found ${saleList.size} sale voucher(s) with ${saleList.sumOf { it.items.size }} line item(s)."
            VoucherExcelImporter.ImportType.PURCHASE ->
                "Found ${purchaseList.size} purchase voucher(s) with ${purchaseList.sumOf { it.items.size }} line item(s)."
            VoucherExcelImporter.ImportType.PAYMENT ->
                "Found ${paymentList.size} payment voucher(s)."
            VoucherExcelImporter.ImportType.RECEIPT ->
                "Found ${receiptList.size} receipt voucher(s)."
            else -> null
        }

        _uiState.value = _uiState.value.copy(
            isProcessing = false,
            detectedVoucherType = result.detectedType,
            pendingSaleVouchers = saleList,
            pendingPurchaseVouchers = purchaseList,
            pendingPaymentVouchers = paymentList,
            pendingReceiptVouchers = receiptList,
            isVoucherImportReady = true,
            voucherImportSummary = summary,
            isSelfCorrected = isSelfCorrected,
            correctedFileBytes = correctedBytes ?: _uiState.value.correctedFileBytes,
            importError = null,
            importErrors = emptyList()
        )
    }

    fun dismissCorrectionDialog() {
        _uiState.value = _uiState.value.copy(showCorrectionDialog = false)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(importError = null, importErrors = emptyList())
    }

    fun dismissSuccess() {
        _uiState.value = _uiState.value.copy(importSuccessMessage = null)
    }

    fun resetVoucherImportSuccess() {
        _uiState.value = _uiState.value.copy(isVoucherImportSuccess = false)
    }

    /**
     * Re-parses the uploaded spreadsheet forced as a specific voucher type (Payment, Receipt, Sales, Purchase).
     */
    fun switchVoucherType(context: Context, companyId: Int, newType: VoucherExcelImporter.ImportType) {
        val bytes = cachedFileBytes ?: return
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            processingStatus = "Re-parsing sheet as ${newType.name.lowercase().replaceFirstChar { it.uppercase() }} vouchers...",
            progressFraction = 0.3f,
            progressPercent = 30,
            progressStage = "Switching Voucher Type",
            importError = null,
            importErrors = emptyList()
        )
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val existingItemsMap = try {
                    db.stockItemDao().getAllStockItems(companyId).associateBy { it.name.trim().lowercase() }
                } catch (_: Throwable) {
                    emptyMap()
                }

                val result = VoucherExcelImporter.parse(
                    inputStream = ByteArrayInputStream(bytes),
                    context = context,
                    companyId = companyId,
                    existingItemsMap = existingItemsMap,
                    forcedType = newType,
                    onProgress = { step, current, total, percent ->
                        _uiState.value = _uiState.value.copy(
                            processingStatus = step,
                            progressFraction = percent / 100f,
                            progressPercent = percent,
                            processedCount = current,
                            totalCount = total,
                            progressStage = "Validating Records"
                        )
                    }
                )

                _uiState.value = _uiState.value.copy(
                    progressFraction = 1f,
                    progressPercent = 100
                )
                populateReadyVouchers(result)
            } catch (t: Throwable) {
                Log.e("TellerImport", "Failed to switch voucher type to $newType", t)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    progressFraction = 0f,
                    progressPercent = 0,
                    importError = "Failed while converting to ${newType.name}: ${t.localizedMessage ?: t.message}"
                )
            }
        }
    }

    /**
     * Commits pending parsed vouchers into the database.
     */
    fun commitVoucherImport(companyId: Int) {
        val state = _uiState.value
        val type = state.detectedVoucherType ?: return
        val total = when (type) {
            VoucherExcelImporter.ImportType.SALE -> state.pendingSaleVouchers.size
            VoucherExcelImporter.ImportType.PURCHASE -> state.pendingPurchaseVouchers.size
            VoucherExcelImporter.ImportType.PAYMENT -> state.pendingPaymentVouchers.size
            VoucherExcelImporter.ImportType.RECEIPT -> state.pendingReceiptVouchers.size
            else -> 0
        }
        _uiState.value = state.copy(
            isProcessing = true,
            processingStatus = "Posting 0 of $total vouchers...",
            progressFraction = 0.05f,
            progressPercent = 5,
            processedCount = 0,
            totalCount = total,
            progressStage = "Posting to Database",
            importError = null,
            importSuccessMessage = null
        )

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val allLedgers = db.ledgerDao().getAllLedgersSync(companyId).toMutableList()

                suspend fun ensureLedger(name: String, defaultGroup: String): Ledger {
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
                    val newLedger = Ledger(
                        name = cleanName,
                        group = defaultGroup,
                        openingBalance = 0L,
                        companyId = companyId
                    )
                    val newId = db.ledgerDao().insert(newLedger).toInt()
                    val inserted = newLedger.copy(id = newId)
                    allLedgers.add(inserted)
                    return inserted
                }

                suspend fun ensureStockItem(name: String, unitName: String, hsn: String?, gstRate: Double): StockItem {
                    val existing = db.stockItemDao().getStockItemByName(companyId, name)
                    if (existing != null) return existing
                    val unitEntity = db.unitDao().getUnitByName(companyId, unitName) ?: run {
                        val uid = db.unitDao().insert(Unit(name = unitName, formalName = unitName, companyId = companyId)).toInt()
                        Unit(id = uid, name = unitName, formalName = unitName, companyId = companyId)
                    }
                    val item = StockItem(
                        name = name,
                        unitId = unitEntity.id,
                        companyId = companyId,
                        hsnCode = hsn,
                        gstRate = gstRate
                    )
                    val newId = db.stockItemDao().insert(item).toInt()
                    return item.copy(id = newId)
                }

                var count = 0

                db.withTransaction {
                    when (type) {
                        VoucherExcelImporter.ImportType.SALE -> {
                            val salesLedger = ensureLedger("Sales Account", "Sales Accounts")
                            for (pv in state.pendingSaleVouchers) {
                                val partyLedger = ensureLedger(pv.partyName, "Sundry Debtors")
                                if (pv.isFinanced && !pv.financedBy.isNullOrBlank()) {
                                    ensureLedger(pv.financedBy, "Sundry Debtors")
                                }

                                var totalAmt = 0.0
                                val invEntries = mutableListOf<InventoryEntry>()
                                for (it in pv.items) {
                                    val stockItem = ensureStockItem(it.itemName, it.unit, it.hsnCode, it.gstRate)
                                    val lineAmt = it.quantity * it.rate * (1 - it.discount / 100.0)
                                    totalAmt += lineAmt
                                    val taxableAmt = if (it.gstRate > 0) lineAmt / (1 + it.gstRate / 100.0) else lineAmt
                                    val taxAmt = lineAmt - taxableAmt
                                    val cgst = if (!pv.isIGST) it.gstRate / 2.0 else 0.0
                                    val sgst = if (!pv.isIGST) it.gstRate / 2.0 else 0.0
                                    val igst = if (pv.isIGST) it.gstRate else 0.0

                                    invEntries.add(
                                        InventoryEntry(
                                            voucherId = 0,
                                            stockItemId = stockItem.id,
                                            quantity = it.quantity,
                                            rate = it.rate,
                                            amount = lineAmt.rupeesToPaise(),
                                            hsnCode = it.hsnCode ?: stockItem.hsnCode,
                                            gstRate = it.gstRate,
                                            cgstRate = cgst,
                                            sgstRate = sgst,
                                            igstRate = igst,
                                            taxableAmount = taxableAmt.rupeesToPaise(),
                                            taxAmount = maxOf(0.0, taxAmt).rupeesToPaise(),
                                            totalAmount = lineAmt.rupeesToPaise()
                                        )
                                    )
                                }

                                val finalVoucherAmount = totalAmt + pv.roundOff
                                val voucherId = db.voucherDao().insert(
                                    Voucher(
                                        companyId = companyId,
                                        date = pv.date,
                                        type = "Sales",
                                        voucherNumber = pv.voucherNumber,
                                        narration = pv.narration,
                                        refDisplayName = pv.partyName,
                                        isFinanced = pv.isFinanced,
                                        customField = pv.financedBy ?: pv.partyName,
                                        placeOfSupply = pv.placeOfSupply
                                    )
                                ).toInt()

                                val partyDebitPaise = finalVoucherAmount.rupeesToPaise()
                                val salesCreditPaise = totalAmt.rupeesToPaise()
                                val roundOffPaise = partyDebitPaise - salesCreditPaise

                                db.voucherEntryDao().insert(
                                    VoucherEntry(
                                        voucherId = voucherId,
                                        ledgerId = partyLedger.id,
                                        amount = partyDebitPaise,
                                        isDebit = true
                                    )
                                )
                                db.voucherEntryDao().insert(
                                    VoucherEntry(
                                        voucherId = voucherId,
                                        ledgerId = salesLedger.id,
                                        amount = salesCreditPaise,
                                        isDebit = false
                                    )
                                )

                                if (roundOffPaise != 0L) {
                                    val roundOffLedger = ensureLedger("Round Off", "Indirect Expenses")
                                    if (roundOffPaise < 0L) {
                                        db.voucherEntryDao().insert(
                                            VoucherEntry(
                                                voucherId = voucherId,
                                                ledgerId = roundOffLedger.id,
                                                amount = -roundOffPaise,
                                                isDebit = true
                                            )
                                        )
                                    } else {
                                        db.voucherEntryDao().insert(
                                            VoucherEntry(
                                                voucherId = voucherId,
                                                ledgerId = roundOffLedger.id,
                                                amount = roundOffPaise,
                                                isDebit = false
                                            )
                                        )
                                    }
                                }

                                for (inv in invEntries) {
                                    db.inventoryEntryDao().insert(inv.copy(voucherId = voucherId))
                                }
                                count++
                                val prog = count.toFloat() / maxOf(1, total)
                                _uiState.value = _uiState.value.copy(
                                    progressFraction = prog,
                                    progressPercent = (prog * 100).toInt(),
                                    processedCount = count,
                                    totalCount = total,
                                    processingStatus = "Posting voucher $count of $total..."
                                )
                            }
                        }

                        VoucherExcelImporter.ImportType.PURCHASE -> {
                            val purchaseLedger = ensureLedger("Purchase Account", "Purchase Accounts")
                            for (pv in state.pendingPurchaseVouchers) {
                                val partyLedger = ensureLedger(pv.partyName, "Sundry Creditors")
                                var totalAmt = 0.0
                                val invEntries = mutableListOf<InventoryEntry>()

                                for (it in pv.items) {
                                    val stockItem = ensureStockItem(it.itemName, it.unit, it.hsnCode, it.gstRate)
                                    val lineAmt = it.quantity * it.rate * (1 - it.discount / 100.0)
                                    totalAmt += lineAmt
                                    val taxableAmt = if (it.gstRate > 0) lineAmt / (1 + it.gstRate / 100.0) else lineAmt
                                    val taxAmt = lineAmt - taxableAmt
                                    val cgst = if (!pv.isIGST) it.gstRate / 2.0 else 0.0
                                    val sgst = if (!pv.isIGST) it.gstRate / 2.0 else 0.0
                                    val igst = if (pv.isIGST) it.gstRate else 0.0

                                    invEntries.add(
                                        InventoryEntry(
                                            voucherId = 0,
                                            stockItemId = stockItem.id,
                                            quantity = it.quantity,
                                            rate = it.rate,
                                            amount = lineAmt.rupeesToPaise(),
                                            hsnCode = it.hsnCode ?: stockItem.hsnCode,
                                            gstRate = it.gstRate,
                                            cgstRate = cgst,
                                            sgstRate = sgst,
                                            igstRate = igst,
                                            taxableAmount = taxableAmt.rupeesToPaise(),
                                            taxAmount = maxOf(0.0, taxAmt).rupeesToPaise(),
                                            totalAmount = lineAmt.rupeesToPaise()
                                        )
                                    )
                                }

                                val voucherId = db.voucherDao().insert(
                                    Voucher(
                                        companyId = companyId,
                                        date = pv.date,
                                        type = "Purchase",
                                        voucherNumber = pv.voucherNumber,
                                        narration = pv.narration,
                                        refDisplayName = pv.partyName,
                                        placeOfSupply = pv.placeOfSupply,
                                        reverseCharge = pv.reverseCharge
                                    )
                                ).toInt()

                                db.voucherEntryDao().insert(
                                    VoucherEntry(
                                        voucherId = voucherId,
                                        ledgerId = purchaseLedger.id,
                                        amount = totalAmt.rupeesToPaise(),
                                        isDebit = true
                                    )
                                )
                                db.voucherEntryDao().insert(
                                    VoucherEntry(
                                        voucherId = voucherId,
                                        ledgerId = partyLedger.id,
                                        amount = totalAmt.rupeesToPaise(),
                                        isDebit = false
                                    )
                                )

                                for (inv in invEntries) {
                                    db.inventoryEntryDao().insert(inv.copy(voucherId = voucherId))
                                }
                                count++
                                val prog = count.toFloat() / maxOf(1, total)
                                _uiState.value = _uiState.value.copy(
                                    progressFraction = prog,
                                    progressPercent = (prog * 100).toInt(),
                                    processedCount = count,
                                    totalCount = total,
                                    processingStatus = "Posting voucher $count of $total..."
                                )
                            }
                        }

                        VoucherExcelImporter.ImportType.PAYMENT -> {
                            for (pv in state.pendingPaymentVouchers) {
                                val fromLedger = ensureLedger(pv.payFromLedger, "Bank Accounts")
                                val isDebtor = pv.payToLedger.contains("debtor", true) ||
                                    (pv.narration?.contains("debtor", true) == true) ||
                                    (pv.narration?.contains("sale", true) == true) ||
                                    (state.voucherImportSummary?.contains("debtor", true) == true)
                                val toLedgerGroup = if (isDebtor) "Sundry Debtors" else "Sundry Creditors"
                                val toLedger = ensureLedger(pv.payToLedger, toLedgerGroup)

                                val voucherId = db.voucherDao().insert(
                                    Voucher(
                                        companyId = companyId,
                                        date = pv.date,
                                        type = "Payment",
                                        voucherNumber = pv.voucherNumber,
                                        narration = pv.narration,
                                        refDisplayName = pv.payToLedger,
                                        isFinanced = pv.isFinanced,
                                        customField = pv.chequeNo ?: pv.financedBy
                                    )
                                ).toInt()

                                val debitEntryId = db.voucherEntryDao().insert(
                                    VoucherEntry(
                                        voucherId = voucherId,
                                        ledgerId = toLedger.id,
                                        amount = pv.amount.rupeesToPaise(),
                                        isDebit = true
                                    )
                                ).toInt()

                                db.voucherEntryDao().insert(
                                    VoucherEntry(
                                        voucherId = voucherId,
                                        ledgerId = fromLedger.id,
                                        amount = pv.amount.rupeesToPaise(),
                                        isDebit = false
                                    )
                                )

                                if (!pv.billReference.isNullOrBlank()) {
                                    db.billAllocationDao().insert(
                                        BillAllocation(
                                            voucherEntryId = debitEntryId,
                                            refName = pv.billReference,
                                            amount = pv.amount.rupeesToPaise()
                                        )
                                    )
                                }
                                count++
                                val prog = count.toFloat() / maxOf(1, total)
                                _uiState.value = _uiState.value.copy(
                                    progressFraction = prog,
                                    progressPercent = (prog * 100).toInt(),
                                    processedCount = count,
                                    totalCount = total,
                                    processingStatus = "Posting voucher $count of $total..."
                                )
                            }
                        }

                        VoucherExcelImporter.ImportType.RECEIPT -> {
                            for (pv in state.pendingReceiptVouchers) {
                                val inLedger = ensureLedger(pv.receiveInLedger, "Bank Accounts")
                                val isCreditor = pv.receivedFrom.contains("creditor", true) ||
                                    (pv.narration?.contains("creditor", true) == true) ||
                                    (pv.narration?.contains("purchase", true) == true)
                                val fromLedgerGroup = if (isCreditor) "Sundry Creditors" else "Sundry Debtors"
                                val fromLedger = ensureLedger(pv.receivedFrom, fromLedgerGroup)

                                val voucherId = db.voucherDao().insert(
                                    Voucher(
                                        companyId = companyId,
                                        date = pv.date,
                                        type = "Receipt",
                                        voucherNumber = pv.voucherNumber,
                                        narration = pv.narration,
                                        refDisplayName = pv.receivedFrom,
                                        isFinanced = pv.isFinanced,
                                        customField = pv.chequeNo ?: pv.financedBy
                                    )
                                ).toInt()

                                val debitEntryId = db.voucherEntryDao().insert(
                                    VoucherEntry(
                                        voucherId = voucherId,
                                        ledgerId = inLedger.id,
                                        amount = pv.amount.rupeesToPaise(),
                                        isDebit = true
                                    )
                                ).toInt()

                                db.voucherEntryDao().insert(
                                    VoucherEntry(
                                        voucherId = voucherId,
                                        ledgerId = fromLedger.id,
                                        amount = pv.amount.rupeesToPaise(),
                                        isDebit = false
                                    )
                                )

                                if (!pv.billReference.isNullOrBlank()) {
                                    db.billAllocationDao().insert(
                                        BillAllocation(
                                            voucherEntryId = debitEntryId,
                                            refName = pv.billReference,
                                            amount = pv.amount.rupeesToPaise()
                                        )
                                    )
                                }
                                count++
                                val prog = count.toFloat() / maxOf(1, total)
                                _uiState.value = _uiState.value.copy(
                                    progressFraction = prog,
                                    progressPercent = (prog * 100).toInt(),
                                    processedCount = count,
                                    totalCount = total,
                                    processingStatus = "Posting voucher $count of $total..."
                                )
                            }
                        }

                        else -> {}
                    }

                    // If imported vouchers are older than company's current financialYearFrom,
                    // automatically adjust the company financial year to cover the imported records.
                    val minVoucherDate = when (type) {
                        VoucherExcelImporter.ImportType.SALE -> state.pendingSaleVouchers.minOfOrNull { it.date.time }
                        VoucherExcelImporter.ImportType.PURCHASE -> state.pendingPurchaseVouchers.minOfOrNull { it.date.time }
                        VoucherExcelImporter.ImportType.PAYMENT -> state.pendingPaymentVouchers.minOfOrNull { it.date.time }
                        VoucherExcelImporter.ImportType.RECEIPT -> state.pendingReceiptVouchers.minOfOrNull { it.date.time }
                        else -> null
                    }
                    if (minVoucherDate != null && minVoucherDate > 0L) {
                        val currentComp = db.companyDao().getCompanyByIdSync(companyId)
                        if (currentComp != null && (currentComp.financialYearFrom > minVoucherDate || currentComp.financialYearFrom == 0L)) {
                            val cal = java.util.Calendar.getInstance()
                            cal.timeInMillis = minVoucherDate
                            val year = cal.get(java.util.Calendar.YEAR)
                            val month = cal.get(java.util.Calendar.MONTH)
                            val fyStartYear = if (month >= java.util.Calendar.APRIL) year else year - 1
                            cal.set(fyStartYear, java.util.Calendar.APRIL, 1, 0, 0, 0)
                            cal.set(java.util.Calendar.MILLISECOND, 0)
                            db.companyDao().update(currentComp.copy(financialYearFrom = cal.timeInMillis))
                        }
                    }
                }

                // Add record to importJobDao
                val job = ImportJob(
                    companyId = companyId,
                    type = "${type.name.lowercase()}_excel",
                    status = "completed",
                    rowCount = count,
                    summaryJson = "Successfully imported $count ${type.name.lowercase()} voucher(s)",
                    updatedAt = System.currentTimeMillis()
                )
                db.importJobDao().insert(job)
                loadJobs(companyId)

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isVoucherImportReady = false,
                    isVoucherImportSuccess = true,
                    progressFraction = 1f,
                    progressPercent = 100,
                    processedCount = count,
                    totalCount = total,
                    processingStatus = "Completed! Posted $count vouchers.",
                    importSuccessMessage = "Successfully imported $count ${type.name.lowercase()} vouchers into your company books!",
                    pendingSaleVouchers = emptyList(),
                    pendingPurchaseVouchers = emptyList(),
                    pendingPaymentVouchers = emptyList(),
                    pendingReceiptVouchers = emptyList()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    progressFraction = 0f,
                    progressPercent = 0,
                    importError = "Failed to post vouchers: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    /**
     * Downloads the self-corrected Excel file to the user's Downloads folder.
     */
    fun downloadCorrectedFile(context: Context) {
        val bytes = _uiState.value.correctedFileBytes ?: return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                ExcelImportSampleGenerator.saveBytesToDownloads(
                    context,
                    "teller_corrected_voucher_import.xlsx",
                    bytes
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Downloaded: teller_corrected_voucher_import.xlsx", Toast.LENGTH_LONG).show()
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to download file: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Downloads a sample Excel template for the chosen voucher type.
     */
    fun downloadSampleTemplate(context: Context, template: ExcelImportSampleGenerator.VoucherTemplate) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                ExcelImportSampleGenerator.saveToDownloads(context, template)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Downloaded ${template.displayName} template to Downloads", Toast.LENGTH_LONG).show()
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save template: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateMapping(header: String, entity: String, field: String) {
        val current = _uiState.value.customMappings.toMutableMap()
        current[header] = Pair(entity, field)
        _uiState.value = _uiState.value.copy(customMappings = current)
    }

    private fun parseCsvDate(str: String?): Date? {
        if (str.isNullOrBlank()) return null
        val fmts = listOf(
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
            SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("yyyyMMdd", Locale.ENGLISH)
        )
        for (fmt in fmts) {
            try {
                return fmt.parse(str.trim())
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Executes the actual import posting for parsed Tally XML data or mapped CSV tabular data.
     */
    fun finalizeImport(companyId: Int) {
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            processingStatus = "Starting database transaction...",
            progressFraction = 0.05f,
            progressPercent = 5,
            progressStage = "Posting to Database",
            importError = null,
            importSuccessMessage = null
        )
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val allLedgers = db.ledgerDao().getAllLedgersSync(companyId).toMutableList()

                suspend fun ensureLedger(name: String, defaultGroup: String): Ledger {
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
                    val newLedger = Ledger(
                        name = cleanName,
                        group = defaultGroup,
                        openingBalance = 0L,
                        companyId = companyId
                    )
                    val newId = db.ledgerDao().insert(newLedger).toInt()
                    val inserted = newLedger.copy(id = newId)
                    allLedgers.add(inserted)
                    return inserted
                }

                var postedTotal = 0

                db.withTransaction {
                    val tRes = parsedTallyResult
                    if (tRes != null) {
                        val totalRecords = tRes.ledgers.size + tRes.stockItems.size + tRes.vouchers.size
                        var count = 0

                        val uniqueUnits = tRes.stockItems.map { it.unitName }.distinct()
                        uniqueUnits.forEach { uName ->
                            val existingUnit = db.unitDao().getUnitByName(companyId, uName)
                            if (existingUnit == null) {
                                db.unitDao().insert(Unit(name = uName, formalName = uName, companyId = companyId))
                            }
                        }

                        tRes.ledgers.forEach { pl ->
                            val cleanName = pl.name.trim()
                            if (cleanName.isBlank() || TallyBackupExtractor.isSystemToken(cleanName)) return@forEach
                            val existing = allLedgers.find { it.name.equals(cleanName, ignoreCase = true) }
                            if (existing == null) {
                                val group = pl.parentGroup?.trim()?.takeIf { it.isNotBlank() && !TallyBackupExtractor.isSystemToken(it) } ?: "Sundry Debtors"
                                val newLedger = Ledger(
                                    name = cleanName,
                                    group = group,
                                    openingBalance = pl.openingBalance.rupeesToPaise(),
                                    phoneNumber = pl.phoneNumber,
                                    gstin = pl.gstin,
                                    companyId = companyId
                                )
                                val newId = db.ledgerDao().insert(newLedger).toInt()
                                allLedgers.add(newLedger.copy(id = newId))
                            }
                            count++
                            if (count % 10 == 0 || count == totalRecords) {
                                val prog = count.toFloat() / maxOf(1, totalRecords)
                                _uiState.value = _uiState.value.copy(
                                    progressFraction = prog,
                                    progressPercent = (prog * 100).toInt(),
                                    processedCount = count,
                                    totalCount = totalRecords,
                                    processingStatus = "Posting ledgers: $count of $totalRecords..."
                                )
                            }
                        }

                        tRes.stockItems.forEach { psi ->
                            val existing = db.stockItemDao().getStockItemByName(companyId, psi.name)
                            if (existing == null) {
                                val unit = db.unitDao().getUnitByName(companyId, psi.unitName)
                                val unitId = unit?.id
                                    ?: db.unitDao().insert(Unit(name = psi.unitName, formalName = psi.unitName, companyId = companyId)).toInt()
                                val newStockItem = StockItem(
                                    name = psi.name,
                                    unitId = unitId,
                                    openingStock = psi.openingQty,
                                    rate = if (psi.openingQty != 0.0) psi.openingVal / psi.openingQty else 0.0,
                                    companyId = companyId,
                                    hsnCode = psi.hsnCode,
                                    gstRate = psi.gstRate,
                                    cgstRate = if (psi.gstRate > 0) psi.gstRate / 2.0 else 0.0,
                                    sgstRate = if (psi.gstRate > 0) psi.gstRate / 2.0 else 0.0
                                )
                                db.stockItemDao().insert(newStockItem)
                            }
                            count++
                            if (count % 10 == 0 || count == totalRecords) {
                                val prog = count.toFloat() / maxOf(1, totalRecords)
                                _uiState.value = _uiState.value.copy(
                                    progressFraction = prog,
                                    progressPercent = (prog * 100).toInt(),
                                    processedCount = count,
                                    totalCount = totalRecords,
                                    processingStatus = "Posting stock items: $count of $totalRecords..."
                                )
                            }
                        }

                        tRes.vouchers.forEach { pv ->
                            val voucherId = db.voucherDao().insert(
                                Voucher(
                                    voucherNumber = pv.voucherNumber,
                                    date = java.util.Date(pv.date),
                                    type = pv.voucherType,
                                    refDisplayName = pv.partyName,
                                    narration = pv.narration,
                                    companyId = companyId
                                )
                            ).toInt()

                            pv.entries.forEach { entry ->
                                val rawName = entry.ledgerName.trim()
                                val cleanEntryName = when {
                                    rawName.isBlank() -> "General Account"
                                    rawName.equals("Sundry Debtors", ignoreCase = true) -> "General Customer"
                                    rawName.equals("Sundry Creditors", ignoreCase = true) -> "General Supplier"
                                    rawName.equals("Bank Accounts", ignoreCase = true) -> "Primary Bank"
                                    rawName.equals("Cash-in-hand", ignoreCase = true) -> "Cash"
                                    TallyBackupExtractor.isSystemToken(rawName) -> "General Account"
                                    else -> rawName
                                }
                                val ledger = allLedgers.find { it.name.equals(cleanEntryName, ignoreCase = true) }
                                    ?: run {
                                        val group = TallyEntityConverter.inferLedgerGroup(cleanEntryName, entry.isDebit)
                                        val newLedger = Ledger(
                                            name = cleanEntryName,
                                            group = group,
                                            openingBalance = 0L,
                                            companyId = companyId
                                        )
                                        val newId = db.ledgerDao().insert(newLedger).toInt()
                                        val created = newLedger.copy(id = newId)
                                        allLedgers.add(created)
                                        created
                                    }

                                db.voucherEntryDao().insert(
                                    VoucherEntry(
                                        voucherId = voucherId,
                                        ledgerId = ledger.id,
                                        amount = entry.amount.rupeesToPaise(),
                                        isDebit = entry.isDebit
                                    )
                                )
                            }

                            val debitsPaise = pv.entries.filter { it.isDebit }.sumOf { it.amount.rupeesToPaise() }
                            val creditsPaise = pv.entries.filter { !it.isDebit }.sumOf { it.amount.rupeesToPaise() }
                            val diffPaise = debitsPaise - creditsPaise
                            if (diffPaise != 0L && Math.abs(diffPaise) <= 100L) {
                                val roundOffLedger = ensureLedger("Round Off", "Indirect Expenses")
                                if (diffPaise > 0L) {
                                    db.voucherEntryDao().insert(
                                        VoucherEntry(
                                            voucherId = voucherId,
                                            ledgerId = roundOffLedger.id,
                                            amount = diffPaise,
                                            isDebit = false
                                        )
                                    )
                                } else {
                                    db.voucherEntryDao().insert(
                                        VoucherEntry(
                                            voucherId = voucherId,
                                            ledgerId = roundOffLedger.id,
                                            amount = -diffPaise,
                                            isDebit = true
                                        )
                                    )
                                }
                            }

                            pv.inventoryEntries.forEach { ie ->
                                val stockItem = db.stockItemDao().getStockItemByName(companyId, ie.stockItemName)
                                    ?: run {
                                        val defaultUnit = db.unitDao().getUnitByName(companyId, "Nos")
                                            ?: run {
                                                val uId = db.unitDao().insert(Unit(name = "Nos", formalName = "Numbers", companyId = companyId)).toInt()
                                                Unit(id = uId, name = "Nos", formalName = "Numbers", companyId = companyId)
                                            }
                                        val newItem = StockItem(
                                            name = ie.stockItemName,
                                            unitId = defaultUnit.id,
                                            openingStock = 0.0,
                                            rate = ie.rate,
                                            companyId = companyId,
                                            hsnCode = ie.hsnCode,
                                            gstRate = ie.gstRate
                                        )
                                        val newId = db.stockItemDao().insert(newItem).toInt()
                                        newItem.copy(id = newId)
                                    }

                                db.inventoryEntryDao().insert(
                                    InventoryEntry(
                                        voucherId = voucherId,
                                        stockItemId = stockItem.id,
                                        quantity = ie.quantity,
                                        rate = ie.rate,
                                        amount = ie.totalAmount.rupeesToPaise(),
                                        hsnCode = ie.hsnCode ?: stockItem.hsnCode,
                                        gstRate = ie.gstRate,
                                        cgstRate = ie.gstRate / 2.0,
                                        sgstRate = ie.gstRate / 2.0,
                                        taxableAmount = ie.totalAmount.rupeesToPaise(),
                                        totalAmount = ie.totalAmount.rupeesToPaise()
                                    )
                                )
                            }

                            count++
                            if (count % 10 == 0 || count == totalRecords) {
                                val prog = count.toFloat() / maxOf(1, totalRecords)
                                _uiState.value = _uiState.value.copy(
                                    progressFraction = prog,
                                    progressPercent = (prog * 100).toInt(),
                                    processedCount = count,
                                    totalCount = totalRecords,
                                    processingStatus = "Posting vouchers: $count of $totalRecords..."
                                )
                            }
                        }
                        postedTotal = totalRecords

                        // If imported vouchers are older than company's current financialYearFrom,
                        // automatically adjust the company financial year to cover the imported records.
                        val minVoucherDate = tRes.vouchers.minOfOrNull { it.date }
                        if (minVoucherDate != null && minVoucherDate > 0L) {
                            val currentComp = db.companyDao().getCompanyByIdSync(companyId)
                            if (currentComp != null && (currentComp.financialYearFrom > minVoucherDate || currentComp.financialYearFrom == 0L)) {
                                val cal = java.util.Calendar.getInstance()
                                cal.timeInMillis = minVoucherDate
                                val year = cal.get(java.util.Calendar.YEAR)
                                val month = cal.get(java.util.Calendar.MONTH) // 0-based, April is 3
                                val fyStartYear = if (month >= java.util.Calendar.APRIL) year else year - 1
                                cal.set(fyStartYear, java.util.Calendar.APRIL, 1, 0, 0, 0)
                                cal.set(java.util.Calendar.MILLISECOND, 0)
                                db.companyDao().update(currentComp.copy(financialYearFrom = cal.timeInMillis))
                            }
                        }
                    } else {
                        // Handle mapped CSV rows
                        val rowsToPost = if (allParsedRows.isNotEmpty()) allParsedRows else _uiState.value.parsedRows
                        if (rowsToPost.isNotEmpty()) {
                            val headers = _uiState.value.parsedHeaders
                            val mappings = _uiState.value.customMappings
                            val colIndexByMapping = mutableMapOf<Pair<String, String>, Int>()
                            for ((h, mapPair) in mappings) {
                                val idx = headers.indexOf(h)
                                if (idx >= 0) {
                                    colIndexByMapping[mapPair] = idx
                                }
                            }

                            val total = rowsToPost.size
                            var count = 0
                            val salesLedger = ensureLedger("Sales Account", "Sales Accounts")

                            for (row in rowsToPost) {
                                // Ledger mapping
                                val ledgerNameIdx = colIndexByMapping[Pair("Ledger", "name")]
                                if (ledgerNameIdx != null) {
                                    val lName = row.cells.getOrNull(ledgerNameIdx)?.trim()
                                    if (!lName.isNullOrBlank()) {
                                        val balIdx = colIndexByMapping[Pair("Ledger", "openingBalance")]
                                        val bal = balIdx?.let { row.cells.getOrNull(it)?.replace(Regex("[^0-9.-]"), "")?.toDoubleOrNull() ?: 0.0 } ?: 0.0
                                        val gstin = colIndexByMapping[Pair("Ledger", "gstin")]?.let { row.cells.getOrNull(it)?.trim() }
                                        val phone = colIndexByMapping[Pair("Ledger", "phoneNumber")]?.let { row.cells.getOrNull(it)?.trim() }

                                        val existing = allLedgers.find { it.name.equals(lName, ignoreCase = true) }
                                        if (existing == null) {
                                            val newL = Ledger(
                                                name = lName,
                                                group = "Sundry Debtors",
                                                openingBalance = bal.rupeesToPaise(),
                                                gstin = gstin,
                                                phoneNumber = phone,
                                                companyId = companyId
                                            )
                                            val nid = db.ledgerDao().insert(newL).toInt()
                                            allLedgers.add(newL.copy(id = nid))
                                        }
                                    }
                                }

                                // StockItem mapping
                                val stockNameIdx = colIndexByMapping[Pair("StockItem", "name")]
                                if (stockNameIdx != null) {
                                    val sName = row.cells.getOrNull(stockNameIdx)?.trim()
                                    if (!sName.isNullOrBlank()) {
                                        val hsn = colIndexByMapping[Pair("StockItem", "hsnCode")]?.let { row.cells.getOrNull(it)?.trim() }
                                        val exItem = db.stockItemDao().getStockItemByName(companyId, sName)
                                        if (exItem == null) {
                                            val defUnit = db.unitDao().getUnitByName(companyId, "Nos")
                                                ?: run {
                                                    val uid = db.unitDao().insert(Unit(name = "Nos", formalName = "Numbers", companyId = companyId)).toInt()
                                                    Unit(id = uid, name = "Nos", formalName = "Numbers", companyId = companyId)
                                                }
                                            db.stockItemDao().insert(
                                                StockItem(
                                                    name = sName,
                                                    unitId = defUnit.id,
                                                    companyId = companyId,
                                                    hsnCode = hsn ?: "0000",
                                                    gstRate = 0.0
                                                )
                                            )
                                        }
                                    }
                                }

                                // Voucher mapping
                                val vAmtIdx = colIndexByMapping[Pair("VoucherEntry", "amount")]
                                if (vAmtIdx != null) {
                                    val rawAmt = row.cells.getOrNull(vAmtIdx)?.replace(Regex("[^0-9.-]"), "")?.toDoubleOrNull() ?: 0.0
                                    if (rawAmt > 0) {
                                        val vNoIdx = colIndexByMapping[Pair("Voucher", "voucherNumber")]
                                        val vNo = vNoIdx?.let { row.cells.getOrNull(it)?.trim() }?.takeIf { it.isNotBlank() } ?: "CSV-${row.index + 1}"
                                        val dateIdx = colIndexByMapping[Pair("Voucher", "date")]
                                        val vDate = parseCsvDate(dateIdx?.let { row.cells.getOrNull(it)?.trim() }) ?: Date()
                                        val pName = ledgerNameIdx?.let { row.cells.getOrNull(it)?.trim() }?.takeIf { it.isNotBlank() } ?: "Sundry Debtors"
                                        val partyLedger = ensureLedger(pName, "Sundry Debtors")

                                        val vId = db.voucherDao().insert(
                                            Voucher(
                                                companyId = companyId,
                                                date = vDate,
                                                type = "Sales",
                                                voucherNumber = vNo,
                                                refDisplayName = pName
                                            )
                                        ).toInt()

                                        db.voucherEntryDao().insert(
                                            VoucherEntry(
                                                voucherId = vId,
                                                ledgerId = partyLedger.id,
                                                amount = rawAmt.rupeesToPaise(),
                                                isDebit = true
                                            )
                                        )
                                        db.voucherEntryDao().insert(
                                            VoucherEntry(
                                                voucherId = vId,
                                                ledgerId = salesLedger.id,
                                                amount = rawAmt.rupeesToPaise(),
                                                isDebit = false
                                            )
                                        )
                                    }
                                }

                                count++
                                if (count % 10 == 0 || count == total) {
                                    val prog = count.toFloat() / maxOf(1, total)
                                    _uiState.value = _uiState.value.copy(
                                        progressFraction = prog,
                                        progressPercent = (prog * 100).toInt(),
                                        processedCount = count,
                                        totalCount = total,
                                        processingStatus = "Posting mapped row $count of $total..."
                                    )
                                }
                            }
                            postedTotal = total
                        }
                    }

                    // Automatically purge unused template dummy banks and nameless ledgers
                    db.ledgerDao().deleteNamelessUnusedLedgers(companyId)
                    db.ledgerDao().renameNamelessUsedLedgers(companyId, "General Account")
                    db.ledgerDao().deleteUnusedDummyLedgers(companyId, TallyBackupExtractor.DUMMY_OR_TEMPLATE_ACCOUNTS)
                    db.ledgerDao().renameUsedBankAccountsLedger(companyId)
                    db.ledgerDao().renameUsedSundryDebtorsLedger(companyId)
                    db.ledgerDao().renameUsedSundryCreditorsLedger(companyId)
                }

                updateJobStatus(activeJobId, "completed", rowCount = postedTotal)
                loadJobs(companyId)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isDryRunVerified = false,
                    progressFraction = 1f,
                    progressPercent = 100,
                    processedCount = postedTotal,
                    totalCount = postedTotal,
                    processingStatus = "Completed! Successfully posted $postedTotal records.",
                    importSuccessMessage = "Successfully posted $postedTotal records into your company books!",
                    parsedHeaders = emptyList(),
                    parsedRows = emptyList(),
                    detectedMappings = emptyMap(),
                    customMappings = emptyMap()
                )
                parsedTallyResult = null
                allParsedRows = emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    progressFraction = 0f,
                    progressPercent = 0,
                    importError = "Failed to post data: ${e.localizedMessage ?: "Unknown error"}"
                )
                updateJobStatus(activeJobId, "failed", error = e.localizedMessage)
            }
        }
    }

    data class PurgeResult(
        val deletedVouchers: Int = 0,
        val deletedLedgers: Int = 0,
        val deletedStockItems: Int = 0
    )

    /**
     * Unified one-tap cleanup that purges corrupt/synthetic import vouchers, 0-balance template
     * accounts, database engine artifacts, and sanitizes company books.
     */
    fun cleanBooksAndPurgeBogusData(companyId: Int, onComplete: ((PurgeResult) -> kotlin.Unit)? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                var vCount = 0
                var lCount = 0
                var sCount = 0

                db.withTransaction {
                    // 1. Delete corrupt / synthetic vouchers from failed imports and their entries
                    val bogusVoucherIds = db.voucherDao().getBogusVoucherIds(companyId)
                    if (bogusVoucherIds.isNotEmpty()) {
                        val entryIds = db.voucherEntryDao().getEntryIdsForVouchers(bogusVoucherIds)
                        if (entryIds.isNotEmpty()) {
                            db.billAllocationDao().deleteAllocationsByEntryIds(entryIds)
                        }
                        db.voucherEntryDao().deleteVoucherEntriesForVouchers(bogusVoucherIds)
                        db.inventoryEntryDao().deleteInventoryEntriesForVouchers(bogusVoucherIds)
                        vCount = db.voucherDao().deleteVouchersByIds(bogusVoucherIds)
                    }

                    // 2. Delete nameless and unused template dummy ledgers
                    lCount += db.ledgerDao().deleteNamelessUnusedLedgers(companyId)
                    lCount += db.ledgerDao().deleteUnusedDummyLedgers(companyId, TallyBackupExtractor.DUMMY_OR_TEMPLATE_ACCOUNTS)
                    lCount += db.ledgerDao().purgeBogusUnusedLedgers(companyId, TallyBackupExtractor.DUMMY_OR_TEMPLATE_ACCOUNTS)

                    // 3. Delete corrupt stock items from failed imports
                    sCount += db.stockItemDao().purgeBogusStockItems(companyId)

                    // 4. Sanitize used group names if any
                    db.ledgerDao().renameUsedBankAccountsLedger(companyId)
                    db.ledgerDao().renameUsedSundryDebtorsLedger(companyId)
                    db.ledgerDao().renameUsedSundryCreditorsLedger(companyId)
                }

                val result = PurgeResult(vCount, lCount, sCount)
                withContext(Dispatchers.Main) {
                    val msg = buildString {
                        append("Cleaned books: Removed ")
                        if (vCount > 0) append("$vCount corrupt vouchers, ")
                        append("$lCount unused dummy ledgers, and $sCount corrupted items.")
                    }
                    _uiState.value = _uiState.value.copy(
                        importSuccessMessage = msg
                    )
                    onComplete?.invoke(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        importError = "Failed to clean books: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    // Alias for backwards compatibility
    fun cleanupCompanyLedgers(companyId: Int, onComplete: ((deletedCount: Int, renamedCount: Int) -> kotlin.Unit)? = null) {
        cleanBooksAndPurgeBogusData(companyId) { res ->
            onComplete?.invoke(res.deletedLedgers, 0)
        }
    }

    private suspend fun updateJobStatus(jobId: Int, status: String, rowCount: Int? = null, summary: String? = null, error: String? = null) {
        val job = db.importJobDao().getJobById(jobId)
        if (job != null) {
            val updated = job.copy(
                status = status,
                rowCount = rowCount ?: job.rowCount,
                errorDetails = error ?: job.errorDetails,
                summaryJson = summary ?: job.summaryJson,
                updatedAt = System.currentTimeMillis()
            )
            db.importJobDao().update(updated)
        }
    }
}

class ImportCleanupViewModelFactory(private val db: AppDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ImportCleanupViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ImportCleanupViewModel(db) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
