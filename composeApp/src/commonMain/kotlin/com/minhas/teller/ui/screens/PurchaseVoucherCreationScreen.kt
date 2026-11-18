package com.minhas.teller.ui.screens

import androidx.compose.animation.core.*

import androidx.compose.ui.composed

import androidx.compose.ui.draw.drawWithContent

import android.app.DatePickerDialog

import android.content.Context

import android.content.Intent

import android.net.Uri

import android.os.Bundle

import android.speech.RecognitionListener

import android.speech.RecognizerIntent

import android.speech.SpeechRecognizer

import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.IntentSenderRequest

import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult

import androidx.compose.foundation.Canvas

import androidx.compose.foundation.Image

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.text.BasicTextField

import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.*

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.*

import androidx.compose.ui.focus.FocusRequester

import androidx.compose.ui.focus.focusRequester

import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.graphics.*

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.painterResource

import androidx.compose.ui.text.TextStyle

import androidx.compose.ui.text.font.FontFamily

import androidx.compose.ui.text.font.FontStyle

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import androidx.compose.ui.window.Dialog

import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.navigation.NavController

import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions

import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG

import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL

import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

import com.minhas.teller.R

import com.minhas.teller.SessionManager

import com.minhas.teller.data.AppDatabase

import com.minhas.teller.data.entities.*
import kotlin.Unit

import com.minhas.teller.data.repositories.CompanyRepository

import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.utils.rupeesToPaise
import com.minhas.teller.utils.paiseToRupees

import com.minhas.teller.data.repositories.StockItemRepository

import com.minhas.teller.data.repositories.UnitRepository

import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.components.LedgerSelectionDialog
import com.minhas.teller.ui.components.StockItemSelectionDialog
import com.minhas.teller.ui.components.ModernDropdownField
import com.minhas.teller.ui.components.ModernTextField
import com.minhas.teller.ui.components.ValidatedNumberTextField

import com.minhas.teller.ui.viewmodels.*

import com.minhas.teller.utils.FinanceHelper
import com.minhas.teller.utils.GstComplianceRules

import com.minhas.teller.utils.OcrHelper

import com.minhas.teller.utils.SpeechInputPrefs

import com.minhas.teller.utils.TerminologyManager

import com.minhas.teller.navigation.Screen

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.delay

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import java.text.SimpleDateFormat

import java.util.*
import androidx.compose.material3.HorizontalDivider

@Composable
private fun GlassCard(

    modifier: Modifier = Modifier,

    borderColor: Color = Color(0xFF0D9488).copy(alpha = 0.20f),

    content: @Composable ColumnScope.() -> Unit

) {

    Card(

        modifier = modifier

            .fillMaxWidth()

            .clip(ChamferedCornerShape(12.dp))

            .border(

                width = 1.5.dp,

                brush = Brush.linearGradient(

                    colors = listOf(

                        Color(0xFF0EA5E9).copy(alpha = 0.8f),

                        Color(0xFF0D9488).copy(alpha = 0.4f)

                    )

                ),

                shape = ChamferedCornerShape(12.dp)

            )

            .drawCircuitTraces()

            .shimmerHighlight(),

        shape = ChamferedCornerShape(12.dp),

        colors = CardDefaults.cardColors(

            containerColor = Color(0xFF111827).copy(alpha = 0.90f)

        ),

        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)

    ) {

        Column(

            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),

            content = content

        )

    }

}

@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun PurchaseVoucherCreationScreen(

    navController: NavController,

    voucherId: Int?,

    isDuplicate: Boolean = false

) {

    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    val database = AppDatabase.getDatabase(context)

    val voucherViewModel: VoucherViewModel = viewModel(factory = VoucherViewModelFactory(

        database.voucherDao(),

        database.voucherEntryDao(),

        database.inventoryEntryDao(),

        database.ledgerDao(),

        database.billAllocationDao(),

        database.auditLogDao(),

        db = database

    ))

    val ledgerRepository = remember { LedgerRepository(database.ledgerDao()) }

    val ledgerViewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(ledgerRepository))

    val allLedgers by ledgerViewModel.allLedgers.collectAsState(initial = emptyList())

    val stockItemRepository = remember { StockItemRepository(database.stockItemDao()) }

    val stockItemViewModel: StockItemViewModel = viewModel(factory = StockItemViewModelFactory(stockItemRepository))

    val allStockItems by stockItemViewModel.allStockItems.collectAsState(initial = emptyList())

    // Used to auto-assign HSN/GST to stock items created on the fly from voice input below —
    // without this, items created via voice never got an HSN code at all (unlike items created
    // through AddEditStockItemScreen or CreateStockItemDialog, both of which call this).
    val smartTaxService = remember { com.minhas.teller.utils.SmartTaxService.getInstance(context) }

    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val taxEnabled = remember { com.minhas.teller.utils.DashboardPrefs.isTaxSystemEnabled(context, currentCompanyId) }

    // --- FORM STATES ---

    var date by remember { mutableStateOf(Date()) }

    var voucherNumber by remember { mutableStateOf("") }

    var narration by remember { mutableStateOf("") }

    var customField by remember { mutableStateOf("") }

    var chargeInterest by remember { mutableStateOf(false) }

    var interestRate by remember { mutableStateOf("12") }

    var interestDays by remember { mutableStateOf("30") }

    val inventoryEntries = remember { mutableStateListOf<InventoryEntry>() }

    var selectedPartyLedger by remember { mutableStateOf<Ledger?>(null) }

    // Payment mode selection: "BANK", "CASH", "ON CREDIT"

    var paymentMode by remember { mutableStateOf("BANK") }

    var selectedBankLedger by remember { mutableStateOf<Ledger?>(null) }

    // Dialog state

    var showDatePicker by remember { mutableStateOf(false) }

    var showStockItemSelection by remember { mutableStateOf(false) }

    var showLedgerSelection by remember { mutableStateOf(false) }

    var showCreateStockItem by remember { mutableStateOf(false) }

    var showCreateLedgerDialog by remember { mutableStateOf(false) }

    var newLedgerName by remember { mutableStateOf("") }

    var newLedgerGroup by remember { mutableStateOf("Sundry Creditors") }

    // Bank account picker (separate from the supplier ledger create-dialog above, since a
    // bank ledger doesn't need the Debtor/Creditor type selector).
    var showCreateBankLedgerDialog by remember { mutableStateOf(false) }

    var newBankLedgerName by remember { mutableStateOf("") }

    // Item input flow states

    var showItemInputDialog by remember { mutableStateOf(false) }

    var itemInputStage by remember { mutableStateOf(1) } // 1: Quantity, 2: Total Amount

    var pendingStockItem by remember { mutableStateOf<StockItem?>(null) }

    var quantityInput by remember { mutableStateOf("") }

    var totalAmountInput by remember { mutableStateOf("") }

    var editingEntryIndex by remember { mutableStateOf<Int?>(null) }

    // Listening Voice Assistant state

    var isListeningVoice by remember { mutableStateOf(false) }

    var voiceTextFeedback by remember { mutableStateOf("") }

    // OCR Analysis state

    var isOcrAnalyzing by remember { mutableStateOf(false) }

    var scannedReceiptDetails by remember { mutableStateOf<com.minhas.teller.utils.SmartReceiptData?>(null) }
    var isOcrSaving by remember { mutableStateOf(false) }

    // Resolve cash / bank accounts

    val bankAccounts = remember(allLedgers) {

        allLedgers.filter {

            it.group.contains("Bank Accounts", ignoreCase = true) ||

            it.group.contains("Bank OD A/c", ignoreCase = true) ||

            it.name.contains("Bank", ignoreCase = true)

        }

    }

    val cashAccounts = remember(allLedgers) {

        allLedgers.filter {

            it.group.contains("Cash-in-hand", ignoreCase = true) ||

            it.name.contains("Cash", ignoreCase = true)

        }

    }

    LaunchedEffect(bankAccounts) {

        if (selectedBankLedger == null && bankAccounts.isNotEmpty()) {

            selectedBankLedger = bankAccounts.firstOrNull()

        }

    }

    // Auto-generate voucher number

    val allVouchers by voucherViewModel.allVouchersWithAllEntries.collectAsState()

    LaunchedEffect(allVouchers) {

        if (voucherNumber.isBlank()) {

            val count = allVouchers.filter { it.voucher.type == "Purchase" }.size

            voucherNumber = "INV-${count + 205}"

        }

    }

    // --- Dynamic IN / OUT stats calculations (matches other screens' current voucher total) ---
    val currentVoucherTotal = inventoryEntries.sumOf { it.amount }.paiseToRupees()
    val totalInSum = currentVoucherTotal
    val totalOutSum = currentVoucherTotal

    // Speech Recognition Launcher

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    val speechRecognizerIntent = remember {

        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

            putExtra(RecognizerIntent.EXTRA_LANGUAGE, SpeechInputPrefs.getLocale(context))

            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

        }

    }

    val speechListener = remember {

        object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {

                voiceTextFeedback = TerminologyManager.getTerm("Listening...")

            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {

                voiceTextFeedback = TerminologyManager.getTerm("Processing...")

            }

            override fun onError(error: Int) {

                voiceTextFeedback = TerminologyManager.getTerm("Voice recognition error. Try again.")

                coroutineScope.launch {

                    delay(2000)

                    isListeningVoice = false

                }

            }

            override fun onResults(results: Bundle?) {

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                // Rank the recogniser's alternatives and undo its known billing mistakes
                // (glued number words, "for" written as 4) — see [SpeechRepair].
                val spokenText = com.minhas.teller.utils.SpeechRepair.pickAndRepair(
                    matches,
                    results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                )

                voiceTextFeedback = "Heard: \"$spokenText\""

                coroutineScope.launch {

                    val parsedDraft = withContext(Dispatchers.Default) {

                        FinanceHelper.parseSpeechToDraft(context, spokenText)

                    }

                    if (parsedDraft != null) {

                        // Apply OCR/voice parsing details

                        if (!parsedDraft.partyName.isNullOrBlank()) {

                            val matchedParty = allLedgers.find { it.name.contains(parsedDraft.partyName, ignoreCase = true) }

                            if (matchedParty != null) {

                                selectedPartyLedger = matchedParty

                            } else {

                                // Create ledger

                                val newId = ledgerViewModel.insertLedger(

                                    Ledger(

                                        name = parsedDraft.partyName.replaceFirstChar { it.uppercase() },

                                        group = "Sundry Creditors",

                                        openingBalance = 0L,

                                        companyId = currentCompanyId ?: 0

                                    )

                                )

                                selectedPartyLedger = Ledger(

                                    id = newId.toInt(),

                                    name = parsedDraft.partyName.replaceFirstChar { it.uppercase() },

                                    group = "Sundry Creditors",

                                    openingBalance = 0L,

                                    companyId = currentCompanyId ?: 0

                                )

                            }

                        }

                        if (parsedDraft.inventoryItems.isNotEmpty()) {
                            val cid = currentCompanyId ?: 0
                            val unitDao = database.unitDao()
                            val allUnits = if (cid != 0) unitDao.getAllUnits(cid) else emptyList()

                            parsedDraft.inventoryItems.forEach { draftItem ->
                                val (canonicalName, qty, deducedUnit) = com.minhas.teller.utils.LanguageTranslationUtils.canonicalItemDetails(
                                    draftItem.name,
                                    draftItem.quantity,
                                    draftItem.unit
                                )
                                val qtyChanged = qty != draftItem.quantity
                                val effectiveRate = if (qtyChanged && qty > 0 && draftItem.amount > 0) draftItem.amount / qty else draftItem.rate

                                val key = com.minhas.teller.utils.LanguageTranslationUtils.itemKey(canonicalName)
                                var matchedItem = allStockItems.find {
                                    it.name.equals(canonicalName, ignoreCase = true) ||
                                    com.minhas.teller.utils.LanguageTranslationUtils.itemKey(it.name) == key
                                }

                                val unitName = com.minhas.teller.utils.ProductCategory.resolveUnit(
                                    deducedUnit
                                        ?: matchedItem?.unitId?.let { savedId -> allUnits.firstOrNull { it.id == savedId }?.name }
                                        ?: com.minhas.teller.utils.ProductCategory.defaultUnitForItem(canonicalName)
                                )
                                var unit = allUnits.firstOrNull {
                                    it.name.equals(unitName, ignoreCase = true) ||
                                    com.minhas.teller.utils.ProductCategory.resolveUnit(it.name).equals(unitName, ignoreCase = true)
                                }
                                if (unit == null && cid != 0) {
                                    val newUnit = com.minhas.teller.data.entities.Unit(name = unitName, companyId = cid)
                                    val newUnitId = unitDao.insert(newUnit)
                                    unit = newUnit.copy(id = newUnitId.toInt())
                                }

                                if (matchedItem != null) {
                                    val currentUnit = allUnits.firstOrNull { it.id == matchedItem.unitId }
                                    val isGeneric = currentUnit == null || currentUnit.name.lowercase() in listOf("units", "unit", "nos", "")
                                    val currentUnitName = currentUnit?.name
                                    val shouldUpdate = if (deducedUnit != null) {
                                        currentUnitName == null || !currentUnitName.equals(unitName, ignoreCase = true)
                                    } else {
                                        isGeneric && unitName !in listOf("Units", "Unit", "Nos", "Pcs")
                                    }
                                    if (shouldUpdate && unit != null) {
                                        val updated = matchedItem.copy(unitId = unit.id)
                                        stockItemRepository.update(updated)
                                        matchedItem = updated
                                    }

                                    inventoryEntries.add(
                                        InventoryEntry(
                                            voucherId = 0,
                                            stockItemId = matchedItem.id,
                                            quantity = qty,
                                            rate = effectiveRate,
                                            amount = draftItem.amount.rupeesToPaise(),
                                            hsnCode = if (taxEnabled) matchedItem.hsnCode else null,
                                            gstRate = if (taxEnabled) matchedItem.gstRate else 0.0,
                                            cgstRate = if (taxEnabled) matchedItem.cgstRate else 0.0,
                                            sgstRate = if (taxEnabled) matchedItem.sgstRate else 0.0,
                                            igstRate = if (taxEnabled) matchedItem.igstRate else 0.0,
                                            taxableAmount = (if (taxEnabled) GstComplianceRules.round2(draftItem.amount / (1.0 + matchedItem.gstRate / 100.0)) else draftItem.amount).rupeesToPaise(),
                                            taxAmount = (if (taxEnabled) GstComplianceRules.round2(draftItem.amount - (draftItem.amount / (1.0 + matchedItem.gstRate / 100.0))) else 0.0).rupeesToPaise(),
                                            totalAmount = draftItem.amount.rupeesToPaise()
                                        )
                                    )
                                } else {
                                    val itemName = canonicalName.replaceFirstChar { it.uppercase() }
                                    val taxInfo = if (taxEnabled) smartTaxService.getTaxInfoForItem(itemName) else null

                                    val newStockItemId = stockItemRepository.insert(
                                        StockItem(
                                            name = itemName,
                                            unitId = unit?.id ?: 1,
                                            rate = effectiveRate,
                                            companyId = cid,
                                            hsnCode = taxInfo?.hsnCode,
                                            gstRate = taxInfo?.gstRate ?: 0.0,
                                            cgstRate = taxInfo?.cgst ?: 0.0,
                                            sgstRate = taxInfo?.sgst ?: 0.0,
                                            igstRate = taxInfo?.igst ?: 0.0,
                                            hsnAutoDetected = taxInfo != null
                                        )
                                    ).toInt()

                                    inventoryEntries.add(
                                        InventoryEntry(
                                            voucherId = 0,
                                            stockItemId = newStockItemId,
                                            quantity = qty,
                                            rate = effectiveRate,
                                            amount = draftItem.amount.rupeesToPaise(),
                                            hsnCode = taxInfo?.hsnCode,
                                            gstRate = taxInfo?.gstRate ?: 0.0,
                                            cgstRate = taxInfo?.cgst ?: 0.0,
                                            sgstRate = taxInfo?.sgst ?: 0.0,
                                            igstRate = taxInfo?.igst ?: 0.0,
                                            taxableAmount = GstComplianceRules.round2(draftItem.amount / (1.0 + (taxInfo?.gstRate ?: 0.0) / 100.0)).rupeesToPaise(),
                                            taxAmount = GstComplianceRules.round2(draftItem.amount - (draftItem.amount / (1.0 + (taxInfo?.gstRate ?: 0.0) / 100.0))).rupeesToPaise(),
                                            totalAmount = draftItem.amount.rupeesToPaise()
                                        )
                                    )
                                }
                            }
                        }

                        if (!parsedDraft.narration.isNullOrBlank()) {

                            narration = parsedDraft.narration

                        }

                        Toast.makeText(context, TerminologyManager.getTerm("AI voice entry processed!"), Toast.LENGTH_SHORT).show()

                    } else {

                        Toast.makeText(context, TerminologyManager.getTerm("AI assistant couldn't extract details from that phrase."), Toast.LENGTH_LONG).show()

                    }

                    isListeningVoice = false

                }

            }

            override fun onPartialResults(partialResults: Bundle?) {

                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                matches?.firstOrNull()?.let {

                    voiceTextFeedback = it

                }

            }

            override fun onEvent(eventType: Int, params: Bundle?) {}

        }

    }

    DisposableEffect(Unit) {

        speechRecognizer.setRecognitionListener(speechListener)

        onDispose {

            speechRecognizer.destroy()

        }

    }

    // OCR Document Scanner Launcher

    val scannerLauncher = rememberLauncherForActivityResult(StartIntentSenderForResult()) { result ->

        if (result.resultCode == android.app.Activity.RESULT_OK) {

            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)

            val pages = scanResult?.pages

            if (!pages.isNullOrEmpty()) {

                val imageUri = pages[0].imageUri

                isOcrAnalyzing = true

                coroutineScope.launch {

                    val (receiptData, scanMethod) = OcrHelper.analyzeReceiptWithGemini(context, imageUri, voucherType = "Purchase")

                    isOcrAnalyzing = false

                    if (receiptData != null) {
                        val taggedData = if (scanMethod == "gemini") {
                            receiptData.copy(narration = (receiptData.narration ?: "") + " [AI Scanned]")
                        } else receiptData
                        scannedReceiptDetails = taggedData

                    } else {

                        Toast.makeText(context, TerminologyManager.getTerm("Could not extract receipt data."), Toast.LENGTH_SHORT).show()

                    }

                }

            }

        }

    }

    // Apply scanned details dialog

    if (scannedReceiptDetails != null) {

        val scan = scannedReceiptDetails!!

        AlertDialog(

            onDismissRequest = { scannedReceiptDetails = null },

            title = { Text(TerminologyManager.getTerm("Receipt Scanned Details")) },

            text = {

                Column {

                    Text("Date: ${scan.date?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it) } ?: "Not detected"}")

                    Text("Vendor: ${scan.vendor ?: "Not detected"}")

                    Text("Amount: ${scan.amount?.let { "₹$it" } ?: "Not detected"}")

                    Text(TerminologyManager.getTerm("Voucher Type: Purchase"))

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(TerminologyManager.getTerm("Do you want to apply these scanned details to your purchase?"), fontWeight = FontWeight.Bold)

                }

            },

            confirmButton = {

                TextButton(onClick = {
                    // Validate company before attempting save
                    val cid = currentCompanyId
                    if (cid == null || cid == 0) {
                        Toast.makeText(context, TerminologyManager.getTerm("No company selected. Please select a company first."), Toast.LENGTH_LONG).show()
                        return@TextButton
                    }
                    isOcrSaving = true
                    coroutineScope.launch {
                        try {
                            // 1. Apply scanned data to form state
                            scan.date?.let { date = it }
                            scan.narration?.let { narration = it }

                            // 2. Match or create Supplier ledger (awaited synchronously inside coroutine)
                            if (!scan.vendor.isNullOrBlank()) {
                                val matchedParty = allLedgers.find { it.name.contains(scan.vendor, ignoreCase = true) }
                                if (matchedParty != null) {
                                    selectedPartyLedger = matchedParty
                                } else {
                                    val newId = ledgerViewModel.insertLedger(
                                        Ledger(
                                            name = scan.vendor.replaceFirstChar { it.uppercase() },
                                            group = "Sundry Creditors",
                                            openingBalance = 0L,
                                            companyId = currentCompanyId ?: 0
                                        )
                                    )
                                    selectedPartyLedger = Ledger(
                                        id = newId.toInt(),
                                        name = scan.vendor.replaceFirstChar { it.uppercase() },
                                        group = "Sundry Creditors",
                                        openingBalance = 0L,
                                        companyId = currentCompanyId ?: 0
                                    )
                                }
                            }

                            // 3. Add scanned item to inventory
                            scan.amount?.let { amt ->
                                inventoryEntries.clear()
                                inventoryEntries.add(
                                    InventoryEntry(
                                        voucherId = 0,
                                        stockItemId = 0,
                                        quantity = 1.0,
                                        rate = amt,
                                        amount = amt.rupeesToPaise(),
                                        taxableAmount = amt.rupeesToPaise(),
                                        totalAmount = amt.rupeesToPaise()
                                    )
                                )
                            }

                            // 4. Validate
                            val purchaseSum = inventoryEntries.sumOf { it.amount }
                            if (purchaseSum <= 0L) {
                                Toast.makeText(context, TerminologyManager.getTerm("Add at least one item!"), Toast.LENGTH_SHORT).show()
                                isOcrSaving = false
                                return@launch
                            }
                            if (selectedPartyLedger == null) {
                                Toast.makeText(context, TerminologyManager.getTerm("Select supplier!"), Toast.LENGTH_SHORT).show()
                                isOcrSaving = false
                                return@launch
                            }
                            if (voucherNumber.isBlank()) {
                                voucherNumber = "INV-${(allVouchers.filter { it.voucher.type == "Purchase" }.size + 205)}"
                            }

                            // 5. Find or create Purchase ledger
                            val purchaseLedger = allLedgers.find {
                                it.group.equals("Purchase Accounts", ignoreCase = true) ||
                                    it.name.contains("Purchase", ignoreCase = true)
                            }
                            val pLedgerId = purchaseLedger?.id ?: ledgerViewModel.insertLedger(
                                Ledger(
                                    name = "Purchase Account",
                                    group = "Purchase Accounts",
                                    openingBalance = 0L,
                                    companyId = currentCompanyId ?: 0
                                )
                            ).toInt()
                            val supplierId = selectedPartyLedger!!.id

                            // 6. Build voucher and entries
                            val purchaseVoucher = Voucher(
                                id = 0,
                                type = "Purchase",
                                date = date,
                                voucherNumber = voucherNumber,
                                narration = narration.ifBlank { null },
                                customField = customField.ifBlank { null },
                                chargeInterest = chargeInterest,
                                interestRate = if (chargeInterest) (interestRate.toDoubleOrNull() ?: 0.0) else 0.0,
                                interestDays = if (chargeInterest) (interestDays.toIntOrNull() ?: 0) else 0,
                                companyId = currentCompanyId ?: 0
                            )
                            val purchaseEntries = listOf(
                                VoucherEntry(voucherId = 0, ledgerId = pLedgerId, amount = purchaseSum, isDebit = true),
                                VoucherEntry(voucherId = 0, ledgerId = supplierId, amount = purchaseSum, isDebit = false)
                            )

                            // 7. Save with appropriate payment handling
                            if (paymentMode == "ON CREDIT") {
                                voucherViewModel.insertVoucherWithAllEntries(purchaseVoucher, purchaseEntries, inventoryEntries)
                            } else {
                                val paidFromId = if (paymentMode == "BANK") {
                                    selectedBankLedger?.id ?: bankAccounts.firstOrNull()?.id ?: ledgerViewModel.insertLedger(
                                        Ledger(name = "Bank Account", group = "Bank Accounts", openingBalance = 0L, companyId = currentCompanyId ?: 0)
                                    ).toInt()
                                } else {
                                    cashAccounts.firstOrNull()?.id ?: ledgerViewModel.insertLedger(
                                        Ledger(name = "Cash-in-hand", group = "Cash-in-hand", openingBalance = 0L, companyId = currentCompanyId ?: 0)
                                    ).toInt()
                                }
                                voucherViewModel.insertPurchaseWithOptionalPayment(
                                    purchaseVoucher = purchaseVoucher,
                                    purchaseEntries = purchaseEntries,
                                    purchaseInventoryEntries = inventoryEntries,
                                    supplierLedgerId = supplierId,
                                    paidFromLedgerId = paidFromId
                                )
                            }

                            // 8. Success — dismiss dialog and navigate back
                            scannedReceiptDetails = null
                            isOcrSaving = false
                            Toast.makeText(context, TerminologyManager.getTerm("Purchase Voucher Saved!"), Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        } catch (e: Exception) {
                            isOcrSaving = false
                            Toast.makeText(context, TerminologyManager.getTerm("Error saving voucher: ${e.message}"), Toast.LENGTH_LONG).show()
                        }
                    }

                },
                    enabled = !isOcrSaving
                ) {
                    if (isOcrSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(TerminologyManager.getTerm("Saving..."))
                    } else {
                        Text(TerminologyManager.getTerm("Yes, Import"))
                    }
                }

            },

            dismissButton = {

                TextButton(onClick = { scannedReceiptDetails = null }) {

                    Text(TerminologyManager.getTerm("Cancel"))

                }

            }

        )

    }

    // Voice recognition sheet/dialog

    if (isListeningVoice) {

        Dialog(onDismissRequest = {

            speechRecognizer.stopListening()

            isListeningVoice = false

        }) {

            Card(

                shape = RoundedCornerShape(24.dp),

                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E36)),

                modifier = Modifier

                    .fillMaxWidth()

                    .border(1.dp, Color(0xFF1E3A8A), RoundedCornerShape(24.dp))

            ) {

                Column(

                    modifier = Modifier.padding(24.dp),

                    horizontalAlignment = Alignment.CenterHorizontally

                ) {

                    Text(

                        TerminologyManager.getTerm("Helafin AI Voice Input"),

                        fontWeight = FontWeight.Bold,

                        fontSize = 20.sp,

                        color = Color.White

                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(

                        TerminologyManager.getTerm("Describe what you bought, from whom, and price.\nExample: \"Bought a ProBook Laptop for 45000 from Apex Solutions\""),

                        fontSize = 12.sp,

                        color = Color.LightGray,

                        textAlign = TextAlign.Center

                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Pulse Listening Animation

                    Box(

                        modifier = Modifier

                            .size(72.dp)

                            .clip(RoundedCornerShape(36.dp))

                            .background(Color(0xFF3B82F6).copy(alpha = 0.2f)),

                        contentAlignment = Alignment.Center

                    ) {

                        Icon(

                            imageVector = Icons.Default.Mic,

                            contentDescription = TerminologyManager.getTerm("Listening Icon"),

                            tint = Color.Cyan,

                            modifier = Modifier.size(36.dp)

                        )

                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(

                        voiceTextFeedback,

                        color = Color.White,

                        fontWeight = FontWeight.Medium,

                        textAlign = TextAlign.Center

                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(

                        onClick = {

                            speechRecognizer.stopListening()

                            isListeningVoice = false

                        },

                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f))

                    ) {

                        Text(TerminologyManager.getTerm("Cancel"))

                    }

                }

            }

        }

    }

    // Two-step ItemInputDialog (Quantity -> Total Amount Paid)

    if (showItemInputDialog && pendingStockItem != null) {

        val stockItem = pendingStockItem!!

        val itemName = stockItem.name

        val focusRequester = remember { FocusRequester() }

        // Request focus when stage changes

        LaunchedEffect(itemInputStage) {

            delay(100) // slight delay to let composition settle

            try {

                focusRequester.requestFocus()

            } catch (e: Exception) {

                // Ignore focus request failure

            }

        }

        AlertDialog(

            onDismissRequest = { showItemInputDialog = false },

            title = {

                Column {

                    Text(

                        text = if (itemInputStage == 1) TerminologyManager.getTerm("Enter Quantity") else TerminologyManager.getTerm("Enter Total Amount"),

                        fontWeight = FontWeight.Bold,

                        style = MaterialTheme.typography.titleLarge,

                        color = Color(0xFF1E3A8A)

                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(

                        text = if (itemInputStage == 1) "Step 1: Quantity for $itemName" else "Step 2: Total paid for $quantityInput x $itemName",

                        style = MaterialTheme.typography.bodyMedium,

                        color = Color(0xFF64748B)

                    )

                }

            },

            text = {

                Column(

                    modifier = Modifier

                        .fillMaxWidth()

                        .padding(vertical = 8.dp)

                ) {

                    if (itemInputStage == 1) {

                        OutlinedTextField(

                            value = quantityInput,

                            onValueChange = { quantityInput = it },

                            label = { Text(TerminologyManager.getTerm("Quantity")) },

                            placeholder = { Text(TerminologyManager.getTerm("e.g. 100")) },

                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                            singleLine = true,

                            modifier = Modifier

                                .fillMaxWidth()

                                .focusRequester(focusRequester),

                            colors = OutlinedTextFieldDefaults.colors(

                                focusedBorderColor = Color(0xFF0D9488),

                                focusedLabelColor = Color(0xFF0D9488)

                            )

                        )

                    } else {

                        OutlinedTextField(

                            value = totalAmountInput,

                            onValueChange = { totalAmountInput = it },

                            label = { Text(TerminologyManager.getTerm("Total Amount Paid")) },

                            placeholder = { Text(TerminologyManager.getTerm("e.g. 500")) },

                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                            singleLine = true,

                            modifier = Modifier

                                .fillMaxWidth()

                                .focusRequester(focusRequester),

                            colors = OutlinedTextFieldDefaults.colors(

                                focusedBorderColor = Color(0xFF0D9488),

                                focusedLabelColor = Color(0xFF0D9488)

                            )

                        )

                    }

                }

            },

            confirmButton = {

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.End,

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    if (editingEntryIndex != null) {

                        TextButton(

                            onClick = {

                                inventoryEntries.removeAt(editingEntryIndex!!)

                                showItemInputDialog = false

                            }

                        ) {

                            Text(TerminologyManager.getTerm("Delete"), color = Color.Red)

                        }

                        Spacer(modifier = Modifier.width(16.dp))

                    }

                    if (itemInputStage == 2) {

                        TextButton(

                            onClick = { itemInputStage = 1 }

                        ) {

                            Text(TerminologyManager.getTerm("Back"), color = Color(0xFF64748B))

                        }

                        Spacer(modifier = Modifier.width(8.dp))

                    }

                    Button(

                        onClick = {

                            if (itemInputStage == 1) {

                                val qty = quantityInput.toDoubleOrNull()

                                if (qty == null || qty <= 0) {

                                    Toast.makeText(context, TerminologyManager.getTerm("Please enter a valid quantity!"), Toast.LENGTH_SHORT).show()

                                } else {

                                    itemInputStage = 2

                                }

                            } else {

                                val qty = quantityInput.toDoubleOrNull() ?: 1.0

                                val total = totalAmountInput.toDoubleOrNull()

                                if (total == null || total < 0) {

                                    Toast.makeText(context, TerminologyManager.getTerm("Please enter a valid total amount!"), Toast.LENGTH_SHORT).show()

                                } else {

                                    val calcRate = if (qty > 0) total / qty else 0.0

                                    val rateGst = stockItem.gstRate

                                    val entry = InventoryEntry(

                                        voucherId = 0,

                                        stockItemId = stockItem.id,

                                        quantity = qty,

                                        rate = calcRate,

                                        amount = total.rupeesToPaise(),

                                        hsnCode = if (taxEnabled) stockItem.hsnCode else null,

                                        gstRate = if (taxEnabled) stockItem.gstRate else 0.0,

                                        cgstRate = if (taxEnabled) stockItem.cgstRate else 0.0,

                                        sgstRate = if (taxEnabled) stockItem.sgstRate else 0.0,

                                        igstRate = if (taxEnabled) stockItem.igstRate else 0.0,

                                        taxableAmount = (if (taxEnabled) total / (1.0 + rateGst / 100.0) else total).rupeesToPaise(),

                                        taxAmount = (if (taxEnabled) total - (total / (1.0 + rateGst / 100.0)) else 0.0).rupeesToPaise(),

                                        totalAmount = total.rupeesToPaise()

                                    )

                                    if (editingEntryIndex != null) {

                                        inventoryEntries[editingEntryIndex!!] = entry

                                    } else {

                                        // Check if item is already added

                                        val existingIndex = inventoryEntries.indexOfFirst { it.stockItemId == stockItem.id }

                                        if (existingIndex != -1) {

                                            val existing = inventoryEntries[existingIndex]

                                            val newQty = existing.quantity + qty

                                            val totalPaise = total.rupeesToPaise()

                                            val newTotal = existing.amount + totalPaise

                                            val newRate = if (newQty > 0) newTotal.paiseToRupees() / newQty else 0.0

                                            inventoryEntries[existingIndex] = existing.copy(

                                                quantity = newQty,

                                                rate = newRate,

                                                amount = newTotal,

                                                taxableAmount = if (taxEnabled) (newTotal.paiseToRupees() / (1.0 + rateGst / 100.0)).rupeesToPaise() else newTotal,

                                                taxAmount = if (taxEnabled) (newTotal.paiseToRupees() - (newTotal.paiseToRupees() / (1.0 + rateGst / 100.0))).rupeesToPaise() else 0L,

                                                totalAmount = newTotal

                                            )

                                        } else {

                                            inventoryEntries.add(entry)

                                        }

                                    }

                                    showItemInputDialog = false

                                }

                            }

                        },

                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))

                    ) {

                        Text(if (itemInputStage == 1) TerminologyManager.getTerm("Next") else if (editingEntryIndex != null) TerminologyManager.getTerm("Update") else TerminologyManager.getTerm("Add Item"))

                    }

                }

            },

            dismissButton = {

                TextButton(

                    onClick = { showItemInputDialog = false }

                ) {

                    Text(TerminologyManager.getTerm("Cancel"), color = Color(0xFF64748B))

                }

            }

        )

    }

    // Purchase screen light gradient background — soft teal-cyan palette

    val purchaseBgModifier = Modifier

        .background(Color.White)

        .background(

            Brush.horizontalGradient(

                listOf(

                    Color(0xFFB5EEE5),

                    Color(0xFFB5E8F5),

                    Color(0xFFD6F0FF),

                    Color(0xFFEBF8FF),

                    Color(0xFFD6EEF5)

                )

            )

        )

        .background(

            Brush.verticalGradient(

                colors = listOf(

                    Color.Transparent,

                    Color.White.copy(alpha = 0.25f),

                    Color.White.copy(alpha = 0.75f),

                    Color.White

                )

            )

        )

    Box(modifier = Modifier.fillMaxSize().imePadding().navigationBarsPadding().then(purchaseBgModifier)) {

        Scaffold(

            containerColor = Color.Transparent,

            modifier = Modifier.fillMaxSize(),

            bottomBar = {
                // --- SAVE BUTTON AT THE VERY BOTTOM ---
                Box(
                     modifier = Modifier
                         .fillMaxWidth()
                         .padding(horizontal = 16.dp, vertical = 12.dp)
                         .height(54.dp)
                         .neumorphicShadowPvc(
                             shape = RoundedCornerShape(27.dp),
                             darkShadowColor = Color(0xFFA3B1C6).copy(alpha = 0.6f),
                             lightShadowColor = Color(0xFFFFFFFF),
                             blur = 8.dp,
                             offset = 4.dp
                         )
                         .clip(RoundedCornerShape(27.dp))
                         .background(
                             Brush.horizontalGradient(
                                 listOf(
                                     Color(0xFF0F1E3D),
                                     Color(0xFF1E3A8A)
                                 )
                             )
                         )
                         .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(27.dp))
                         .clickable {
                            val purchaseSum = inventoryEntries.sumOf { it.amount }
                            if (purchaseSum <= 0.0) {
                                Toast.makeText(context, TerminologyManager.getTerm("Add at least one item!"), Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            if (selectedPartyLedger == null) {
                                Toast.makeText(context, TerminologyManager.getTerm("Select supplier!"), Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            if (voucherNumber.isBlank()) {
                                Toast.makeText(context, TerminologyManager.getTerm("Voucher number is required!"), Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            coroutineScope.launch {
                                // Find/Insert purchase ledger
                                val purchaseLedger = allLedgers.find {
                                    it.group.equals(
                                        "Purchase Accounts",
                                        ignoreCase = true
                                    ) || it.name.contains("Purchase", ignoreCase = true)
                                }
                                val pLedgerId = purchaseLedger?.id ?: ledgerViewModel.insertLedger(
                                    Ledger(
                                        name = "Purchase Account",
                                        group = "Purchase Accounts",
                                        openingBalance = 0L,
                                        companyId = currentCompanyId ?: 0
                                    )
                                ).toInt()
                                val supplierId = selectedPartyLedger!!.id
                                val purchaseVoucher = Voucher(
                                    id = 0,
                                    type = "Purchase",
                                    date = date,
                                    voucherNumber = voucherNumber,
                                    narration = narration.ifBlank { null },
                                    customField = customField.ifBlank { null },
                                    chargeInterest = chargeInterest,
                                    interestRate = if (chargeInterest) (interestRate.toDoubleOrNull() ?: 0.0) else 0.0,
                                    interestDays = if (chargeInterest) (interestDays.toIntOrNull() ?: 0) else 0,
                                    companyId = currentCompanyId ?: 0
                                )
                                val purchaseEntries = listOf(
                                    VoucherEntry(
                                        voucherId = 0,
                                        ledgerId = pLedgerId,
                                        amount = purchaseSum,
                                        isDebit = true
                                    ),
                                    VoucherEntry(
                                        voucherId = 0,
                                        ledgerId = supplierId,
                                        amount = purchaseSum,
                                        isDebit = false
                                    )
                                )
                                if (paymentMode == "ON CREDIT") {
                                    voucherViewModel.insertVoucherWithAllEntries(
                                        purchaseVoucher,
                                        purchaseEntries,
                                        inventoryEntries
                                    )
                                } else {
                                    // Paid immediately via CASH or BANK
                                    val paidFromId = if (paymentMode == "BANK") {
                                        selectedBankLedger?.id ?: bankAccounts.firstOrNull()?.id ?: ledgerViewModel.insertLedger(
                                            Ledger(
                                                name = "Bank Account",
                                                group = "Bank Accounts",
                                                openingBalance = 0L,
                                                companyId = currentCompanyId ?: 0
                                            )
                                        ).toInt()
                                    } else {
                                        cashAccounts.firstOrNull()?.id ?: ledgerViewModel.insertLedger(
                                            Ledger(
                                                name = "Cash-in-hand",
                                                group = "Cash-in-hand",
                                                openingBalance = 0L,
                                                companyId = currentCompanyId ?: 0
                                            )
                                        ).toInt()
                                    }
                                    voucherViewModel.insertPurchaseWithOptionalPayment(
                                        purchaseVoucher = purchaseVoucher,
                                        purchaseEntries = purchaseEntries,
                                        purchaseInventoryEntries = inventoryEntries,
                                        supplierLedgerId = supplierId,
                                        paidFromLedgerId = paidFromId
                                    )
                                }
                                Toast.makeText(context, TerminologyManager.getTerm("Purchase Voucher Saved!"), Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = TerminologyManager.getTerm("SAVE VOUCHER"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = TerminologyManager.getTerm("Save Icon"),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

        ) { paddingValues ->

            Column(

                modifier = Modifier

                    .fillMaxSize()

                    .padding(paddingValues)

            ) {

                // --- CUSTOM HEADER BAR ---

                Row(

                    modifier = Modifier

                        .fillMaxWidth()

                        .padding(horizontal = 8.dp, vertical = 8.dp),

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    IconButton(onClick = { navController.popBackStack() }) {

                        Icon(

                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,

                            contentDescription = TerminologyManager.getTerm("Back"),

                            tint = Color(0xFF0D9488)

                        )

                    }

                    var fontSize by remember { mutableStateOf(18.sp) }
                     LaunchedEffect(TerminologyManager.getTerm("NEW PURCHASE")) {
                         fontSize = 18.sp
                     }
                     Text(
                          text = TerminologyManager.getTerm("NEW PURCHASE"),
                          style = MaterialTheme.typography.headlineSmall.copy(
                              fontWeight = FontWeight.ExtraBold,
                              fontSize = fontSize
                          ),
                          maxLines = 1,
                          overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                          modifier = Modifier.weight(1f).padding(start = 4.dp),
                          color = Color(0xFF0D9488),
                          onTextLayout = { textLayoutResult ->
                              if (textLayoutResult.hasVisualOverflow && fontSize.value > 10f) {
                                  fontSize = (fontSize.value * 0.9f).sp
                              }
                          }
                      )

                    // Header Actions

                    IconButton(onClick = {

                        isListeningVoice = true

                        speechRecognizer.startListening(speechRecognizerIntent)

                    }) {

                        Icon(

                            imageVector = Icons.Default.Mic,

                            contentDescription = TerminologyManager.getTerm("Voice input"),

                            tint = Color(0xFF0D9488),

                            modifier = Modifier.size(26.dp)

                        )

                    }

                    IconButton(onClick = {

                        val options = GmsDocumentScannerOptions.Builder()

                            .setGalleryImportAllowed(true)

                            .setResultFormats(RESULT_FORMAT_JPEG)

                            .setScannerMode(SCANNER_MODE_FULL)

                            .build()

                        val activity = context as? android.app.Activity

                        if (activity != null) {

                            GmsDocumentScanning.getClient(options)

                                .getStartScanIntent(activity)

                                .addOnSuccessListener { intentSender ->

                                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())

                                }

                        }

                    }) {

                        Icon(

                            imageVector = Icons.Default.PhotoCamera,

                            contentDescription = TerminologyManager.getTerm("Receipt scanner"),

                            tint = Color(0xFF0D9488),

                            modifier = Modifier.size(26.dp)

                        )

                    }

                    IconButton(onClick = { navController.navigate(Screen.Dashboard.route) }) {

                        Icon(

                            imageVector = Icons.Default.Home,

                            contentDescription = TerminologyManager.getTerm("Home"),

                            tint = Color(0xFF0D9488),

                            modifier = Modifier.size(26.dp)

                        )

                    }

                }

                if (isOcrAnalyzing) {

                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.Cyan)

                }

                // Scrollable main content

                Column(

                    modifier = Modifier

                        .weight(1f)

                        .verticalScroll(rememberScrollState())

                        .padding(horizontal = 16.dp)

                ) {

                    // --- 1. CASH IN/OUT SUMMARY CARD ---

                     // Neumorphic In/Out card with strict Soft UI design system
                     Box(
                         modifier = Modifier
                             .fillMaxWidth()
                             .padding(vertical = 4.dp)
                             .background(Color(0xFFE0E5EC), shape = RoundedCornerShape(32.dp))
                             .border(
                                 width = 2.dp,
                                 brush = Brush.horizontalGradient(
                                     colors = listOf(Color(0xFFF59E0B), Color(0xFFFCD34D)) // Yellow
                                 ),
                                 shape = RoundedCornerShape(32.dp)
                             )
                             .padding(6.dp)
                     ) {
                         Row(
                             modifier = Modifier.fillMaxWidth(),
                             horizontalArrangement = Arrangement.SpaceBetween,
                             verticalAlignment = Alignment.CenterVertically
                         ) {
                             // "In" Button Panel
                             Box(
                                 modifier = Modifier
                                     .weight(1f)
                                     .neumorphicShadowPvc(RoundedCornerShape(24.dp))
                                     .background(Color(0xFFE0E5EC), RoundedCornerShape(24.dp))
                                     .padding(horizontal = 12.dp, vertical = 8.dp)
                             ) {
                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                     // Green Arrow Button
                                     Box(
                                         modifier = Modifier
                                             .size(36.dp)
                                             .neumorphicShadowPvc(CircleShape, darkShadowColor = Color(0xFF4CAF50).copy(alpha = 0.5f), blur = 4.dp, offset = 3.dp)
                                             .background(Color(0xFFE0E5EC), CircleShape),
                                         contentAlignment = Alignment.Center
                                     ) {
                                         Icon(
                                             imageVector = Icons.Default.ArrowDownward,
                                             contentDescription = TerminologyManager.getTerm("Cash In"),
                                             tint = Color(0xFF2E7D32),
                                             modifier = Modifier.size(20.dp)
                                         )
                                     }

                                     Spacer(modifier = Modifier.width(12.dp))

                                     Column {
                                         Text(text = TerminologyManager.getTerm("In:"), color = Color(0xFF546E7A), fontSize = 12.sp)
                                         val inStr = String.format("%,.2f", totalInSum)
                                         val inFontSize = when {
                                             inStr.length > 12 -> 11.sp
                                             inStr.length > 9 -> 13.sp
                                             inStr.length > 6 -> 15.sp
                                             else -> 18.sp
                                         }
                                         Text(
                                             text = "₹$inStr",
                                             color = Color(0xFF263238),
                                             fontSize = inFontSize,
                                             fontWeight = FontWeight.Bold,
                                             maxLines = 1
                                          )
                                     }
                                 }
                             }

                             Spacer(modifier = Modifier.width(8.dp))

                             // "Out" Button Panel
                             Box(
                                 modifier = Modifier
                                     .weight(1f)
                                     .neumorphicShadowPvc(RoundedCornerShape(24.dp))
                                     .background(Color(0xFFE0E5EC), RoundedCornerShape(24.dp))
                                     .padding(horizontal = 12.dp, vertical = 8.dp)
                             ) {
                                 Row(
                                     verticalAlignment = Alignment.CenterVertically,
                                     horizontalArrangement = Arrangement.SpaceBetween,
                                     modifier = Modifier.fillMaxWidth()
                                 ) {
                                     Column(horizontalAlignment = Alignment.Start) {
                                         Text(text = TerminologyManager.getTerm("Out:"), color = Color(0xFF546E7A), fontSize = 12.sp)
                                         val outStr = String.format("%,.2f", totalOutSum)
                                         val outFontSize = when {
                                             outStr.length > 12 -> 11.sp
                                             outStr.length > 9 -> 13.sp
                                             outStr.length > 6 -> 15.sp
                                             else -> 18.sp
                                         }
                                         Text(
                                             text = "₹$outStr",
                                             color = Color(0xFF263238),
                                             fontSize = outFontSize,
                                             fontWeight = FontWeight.Bold,
                                             maxLines = 1
                                          )
                                     }

                                     Spacer(modifier = Modifier.width(12.dp))

                                     // Red Arrow Button
                                     Box(
                                         modifier = Modifier
                                             .size(36.dp)
                                             .neumorphicShadowPvc(CircleShape, darkShadowColor = Color(0xFFF44336).copy(alpha = 0.5f), blur = 4.dp, offset = 3.dp)
                                             .background(Color(0xFFE0E5EC), CircleShape),
                                         contentAlignment = Alignment.Center
                                     ) {
                                         Icon(
                                             imageVector = Icons.Default.ArrowUpward,
                                             contentDescription = TerminologyManager.getTerm("Cash Out"),
                                             tint = Color(0xFFC62828),
                                             modifier = Modifier.size(20.dp)
                                         )
                                     }
                                 }
                             }
                         }
                     }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // --- 2. DATE & VOUCHER NO (full width, no card background) ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Date pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .floatingDropShadowPvc(borderRadius = 25.dp)
                                .clip(RoundedCornerShape(25.dp))
                                .background(Color.White.copy(alpha = 0.90f))
                                .border(1.5.dp, Color(0xFF0EA5E9).copy(alpha = 0.55f), RoundedCornerShape(25.dp))
                                .clickable { showDatePicker = true }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = TerminologyManager.getTerm("Calendar"),
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(TerminologyManager.getTerm("Date:"), fontSize = 12.sp, color = Color(0xFF607D8B), fontWeight = FontWeight.Medium, maxLines = 1)
                                    AutoShrinkText(
                                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date),
                                        color = Color(0xFF01579B),
                                        maxFontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        // Voucher No pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .floatingDropShadowPvc(borderRadius = 25.dp)
                                .clip(RoundedCornerShape(25.dp))
                                .background(Color.White.copy(alpha = 0.90f))
                                .border(1.5.dp, Color(0xFFAB47BC).copy(alpha = 0.55f), RoundedCornerShape(27.dp))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = TerminologyManager.getTerm("Doc Icon"),
                                    tint = Color(0xFF7B1FA2),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(TerminologyManager.getTerm("Voucher:"), fontSize = 12.sp, color = Color(0xFF607D8B), fontWeight = FontWeight.Medium, maxLines = 1)
                                    BasicTextField(
                                        value = voucherNumber,
                                        onValueChange = { voucherNumber = it },
                                        textStyle = TextStyle(
                                            color = Color(0xFF4A148C),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        singleLine = true,
                                        cursorBrush = SolidColor(Color(0xFF7B1FA2))
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = Color(0xFF0D9488).copy(alpha = 0.15f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(6.dp))

                    // --- 3. ITEM SELECTION ("1. WHAT ITEM DID YOU BUY?") ---
                    GlassCard(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                TerminologyManager.getTerm("1. WHAT ITEM DID YOU BUY?"),
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = TerminologyManager.getTerm("Items"),
                                tint = Color(0xFF0D9488),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (inventoryEntries.isEmpty()) {

                            // Compact single-row CTA — no separate placeholder box + duplicate add/subtotal row

                            Row(

                                modifier = Modifier

                                    .fillMaxWidth()

                                    .clip(RoundedCornerShape(12.dp))

                                    .background(Color.White.copy(alpha = 0.55f))

                                    .border(1.dp, Color(0xFF334155).copy(alpha = 0.35f), RoundedCornerShape(12.dp))

                                    .clickable { showStockItemSelection = true }

                                    .padding(horizontal = 14.dp, vertical = 12.dp),

                                horizontalArrangement = Arrangement.SpaceBetween,

                                verticalAlignment = Alignment.CenterVertically

                            ) {

                                Text(

                                    TerminologyManager.getTerm("No items yet — tap to add from stock"),

                                    fontSize = 13.sp,

                                    color = Color(0xFF0F172A),

                                    fontWeight = FontWeight.Bold

                                )

                                Box(

                                    modifier = Modifier

                                        .size(28.dp)

                                        .clip(RoundedCornerShape(14.dp))

                                        .background(Color(0xFF0D9488).copy(alpha = 0.15f)),

                                    contentAlignment = Alignment.Center

                                ) {

                                    Icon(

                                        imageVector = Icons.Default.Add,

                                        contentDescription = TerminologyManager.getTerm("Add Item"),

                                        tint = Color(0xFF0D9488),

                                        modifier = Modifier.size(18.dp)

                                    )

                                }

                            }

                        } else {

                            // Box holding item list with gradient outline

                            Box(

                                modifier = Modifier

                                    .fillMaxWidth()

                                    .clip(RoundedCornerShape(12.dp))

                                    .background(Color.White.copy(alpha = 0.55f))

                                    .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(12.dp))

                                    .padding(12.dp)

                            ) {

                                Column {

                                    inventoryEntries.forEachIndexed { i, entry ->

                                        val stockItem = allStockItems.find { it.id == entry.stockItemId }

                                        val itemName = stockItem?.name ?: TerminologyManager.getTerm("Scanned Item")

                                        Row(

                                            modifier = Modifier

                                                .fillMaxWidth()

                                                .clickable {

                                                    val e = inventoryEntries[i]

                                                    val sItem = allStockItems.find { it.id == e.stockItemId }

                                                    pendingStockItem = sItem

                                                    quantityInput = if (e.quantity % 1.0 == 0.0) e.quantity.toInt().toString() else e.quantity.toString()

                                                    totalAmountInput = if (e.amount % 1.0 == 0.0) e.amount.toInt().toString() else e.amount.toString()

                                                    itemInputStage = 1

                                                    editingEntryIndex = i

                                                    showItemInputDialog = true

                                                }

                                                .padding(vertical = 8.dp),

                                            horizontalArrangement = Arrangement.SpaceBetween,

                                            verticalAlignment = Alignment.CenterVertically

                                        ) {

                                            Column(modifier = Modifier.weight(1f)) {

                                                val displayQty = if (entry.quantity % 1.0 == 0.0) entry.quantity.toInt().toString() else entry.quantity.toString()

                                                AutoShrinkText(

                                                    "$displayQty $itemName",

                                                    color = Color(0xFF0F172A),

                                                    fontWeight = FontWeight.Bold,

                                                    maxFontSize = 15.sp

                                                )

                                                if (entry.rate > 0.0) {

                                                    AutoShrinkText(

                                                        "Rate: ₹${String.format("%,.2f", entry.rate)} each",

                                                        color = Color(0xFF475569),

                                                        maxFontSize = 12.sp,

                                                        fontWeight = FontWeight.Bold

                                                    )

                                                }

                                            }

                                            AutoShrinkText(

                                                "₹${String.format("%,.2f", entry.amount.paiseToRupees())}",

                                                color = Color(0xFF0F766E),

                                                fontWeight = FontWeight.ExtraBold,

                                                maxFontSize = 15.sp

                                            )

                                        }

                                        if (i < inventoryEntries.size - 1) {

                                            HorizontalDivider(color = Color(0xFF0D9488).copy(alpha = 0.15f))

                                        }

                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Add button and subtotal row

                                    Row(

                                        modifier = Modifier.fillMaxWidth(),

                                        horizontalArrangement = Arrangement.SpaceBetween,

                                        verticalAlignment = Alignment.CenterVertically

                                    ) {

                                        Text(

                                            TerminologyManager.getTerm("+ Add Another Item"),

                                            color = Color(0xFF0EA5E9),

                                            fontWeight = FontWeight.ExtraBold,

                                            fontSize = 13.sp,

                                            modifier = Modifier.clickable { showStockItemSelection = true }

                                        )

                                        val subTotal = inventoryEntries.sumOf { it.amount }.paiseToRupees()

                                        AutoShrinkText(

                                            "Sub-Total: ₹${String.format("%,.2f", subTotal)}",

                                            color = Color(0xFF0F172A),

                                            fontWeight = FontWeight.Bold,

                                            maxFontSize = 13.sp

                                        )

                                    }

                                }

                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Large Total Amount section (only shown once at least one item is added —

                            // avoids showing "Sub-Total ₹0.00" and "TOTAL AMOUNT ₹0.00" redundantly)

                            val grandTotal = inventoryEntries.sumOf { it.amount }.paiseToRupees()

                            Row(

                                modifier = Modifier.fillMaxWidth(),

                                horizontalArrangement = Arrangement.SpaceBetween,

                                verticalAlignment = Alignment.CenterVertically

                            ) {

                                Column {

                                    Text(TerminologyManager.getTerm("TOTAL AMOUNT"), color = Color(0xFF334155), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                                    AutoShrinkText(

                                        "₹${String.format("%,.2f", grandTotal)}",

                                        style = TextStyle(

                                            fontWeight = FontWeight.Black

                                        ),

                                        maxFontSize = 28.sp,

                                        color = Color(0xFF0F766E)

                                    )

                                }

                                // Big circular plus button

                                Box(

                                    modifier = Modifier

                                        .size(44.dp)

                                        .clip(RoundedCornerShape(22.dp))

                                        .background(Color(0xFF0D9488).copy(alpha = 0.15f))

                                        .clickable { showStockItemSelection = true },

                                    contentAlignment = Alignment.Center

                                ) {

                                    Icon(

                                        imageVector = Icons.Default.Add,

                                        contentDescription = TerminologyManager.getTerm("Add Item"),

                                        tint = Color(0xFF0D9488),

                                        modifier = Modifier.size(24.dp)

                                    )

                                }

                            }

                        }

                    }
Spacer(modifier = Modifier.height(6.dp))
HorizontalDivider(color = Color(0xFF0D9488).copy(alpha = 0.15f), thickness = 1.dp)
Spacer(modifier = Modifier.height(6.dp))

                        // Charge interest toggle (small and compact)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clickable { chargeInterest = !chargeInterest },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Percent,
                                    contentDescription = TerminologyManager.getTerm("Percent"),
                                    tint = Color(0xFF0D9488),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    TerminologyManager.getTerm("Were we charged interest?"),
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Switch(
                                checked = chargeInterest,
                                onCheckedChange = { chargeInterest = it },
                                modifier = Modifier.scale(0.7f),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF0D9488),
                                    checkedTrackColor = Color(0xFF0D9488).copy(alpha = 0.4f)
                                )
                            )
                        }

                        if (chargeInterest) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ValidatedNumberTextField(
                                    value = interestRate,
                                    onValueChange = { interestRate = it },
                                    label = TerminologyManager.getTerm("Rate (% per year)"),
                                    modifier = Modifier.weight(1f),
                                    isDecimal = true,
                                    isLightTheme = true
                                )
                                ValidatedNumberTextField(
                                    value = interestDays,
                                    onValueChange = { interestDays = it },
                                    label = TerminologyManager.getTerm("Grace Period (Days)"),
                                    modifier = Modifier.weight(1f),
                                    isDecimal = false,
                                    isLightTheme = true
                                )
                            }
                        }

                    // --- 4. SUPPLIER SELECTION ("2. WHO DID YOU BUY IT FROM?") ---

                    GlassCard(modifier = Modifier.padding(vertical = 4.dp)) {

                        Row(

                            modifier = Modifier.fillMaxWidth(),

                            horizontalArrangement = Arrangement.SpaceBetween,

                            verticalAlignment = Alignment.CenterVertically

                        ) {

                            Text(

                                TerminologyManager.getTerm("2. WHO DID YOU BUY IT FROM?"),

                                color = Color(0xFF0F172A),

                                fontWeight = FontWeight.ExtraBold,

                                fontSize = 16.sp

                            )

                            Icon(

                                imageVector = Icons.Default.Person,

                                contentDescription = TerminologyManager.getTerm("Supplier"),

                                tint = Color(0xFF0D9488)

                            )

                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Supplier box dropdown trigger

                        Box(

                            modifier = Modifier

                                .fillMaxWidth()

                                .height(56.dp)

                                .clip(RoundedCornerShape(12.dp))

                                .background(Color.White.copy(alpha = 0.55f))

                                .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(12.dp))

                                .clickable { showLedgerSelection = true }

                                .padding(horizontal = 16.dp),

                            contentAlignment = Alignment.CenterStart

                        ) {

                            Row(

                                modifier = Modifier.fillMaxWidth(),

                                verticalAlignment = Alignment.CenterVertically,

                                horizontalArrangement = Arrangement.SpaceBetween

                            ) {

                                AutoShrinkText(

                                    text = selectedPartyLedger?.name ?: TerminologyManager.getTerm("Select Supplier / Vendor"),

                                    color = if (selectedPartyLedger == null) Color(0xFF475569) else Color(0xFF0F172A),

                                    maxFontSize = 15.sp,

                                    fontWeight = FontWeight.Bold

                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {

                                    Icon(

                                        imageVector = Icons.Default.Place,

                                        contentDescription = TerminologyManager.getTerm("Location"),

                                        tint = Color(0xFF0D9488),

                                        modifier = Modifier.size(20.dp)

                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Icon(

                                        imageVector = Icons.Default.LocationCity,

                                        contentDescription = TerminologyManager.getTerm("Building"),

                                        tint = Color(0xFF0EA5E9),

                                        modifier = Modifier.size(20.dp)

                                    )

                                }

                            }

                        }

                    }

                    // --- 5. HOW WAS PAYMENT MADE? ---

                    GlassCard(modifier = Modifier.padding(vertical = 4.dp)) {

                        Text(

                            TerminologyManager.getTerm("3. HOW WAS PAYMENT MADE?"),

                            color = Color(0xFF0F172A),

                            fontWeight = FontWeight.ExtraBold,

                            fontSize = 16.sp

                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(

                            TerminologyManager.getTerm("Select your payment method below"),

                            color = Color(0xFF475569),

                            fontSize = 12.sp,

                            fontWeight = FontWeight.Bold

                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(

                            modifier = Modifier.fillMaxWidth(),

                            horizontalArrangement = Arrangement.spacedBy(10.dp)

                        ) {

                            // BANK Button

                            Box(

                                modifier = Modifier

                                    .weight(1f)

                                    .height(90.dp)

                                    .border(

                                        width = if (paymentMode == "BANK") 2.dp else 1.dp,

                                        color = if (paymentMode == "BANK") Color(0xFF0D9488) else Color.White.copy(alpha = 0.5f),

                                        shape = RoundedCornerShape(16.dp)

                                    )

                                    .clip(RoundedCornerShape(16.dp))

                                    .background(if (paymentMode == "BANK") Color(0xFF0D9488).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.35f))

                                    .clickable { paymentMode = "BANK" }

                                    .padding(8.dp),

                                contentAlignment = Alignment.Center

                            ) {

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                                    Image(

                                        painter = painterResource(id = R.drawable.ic_rpt_safe_box),

                                        contentDescription = TerminologyManager.getTerm("Bank"),

                                        modifier = Modifier.size(36.dp)

                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(TerminologyManager.getTerm("BANK"), color = Color(0xFF0F172A), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)

                                }

                            }

                            // CASH Button

                            Box(

                                modifier = Modifier

                                    .weight(1f)

                                    .height(90.dp)

                                    .border(

                                        width = if (paymentMode == "CASH") 2.dp else 1.dp,

                                        color = if (paymentMode == "CASH") Color(0xFF0D9488) else Color.White.copy(alpha = 0.5f),

                                        shape = RoundedCornerShape(16.dp)

                                    )

                                    .clip(RoundedCornerShape(16.dp))

                                    .background(if (paymentMode == "CASH") Color(0xFF0D9488).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.35f))

                                    .clickable { paymentMode = "CASH" }

                                    .padding(8.dp),

                                contentAlignment = Alignment.Center

                            ) {

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                                    Image(

                                        painter = painterResource(id = R.drawable.ic_rpt_transaction),

                                        contentDescription = TerminologyManager.getTerm("Cash"),

                                        modifier = Modifier.size(36.dp)

                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(TerminologyManager.getTerm("CASH"), color = Color(0xFF0F172A), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)

                                }

                            }

                            // CREDIT Button

                            Box(

                                modifier = Modifier

                                    .weight(1f)

                                    .height(90.dp)

                                    .border(

                                        width = if (paymentMode == "ON CREDIT") 2.dp else 1.dp,

                                        color = if (paymentMode == "ON CREDIT") Color(0xFF0D9488) else Color.White.copy(alpha = 0.5f),

                                        shape = RoundedCornerShape(16.dp)

                                    )

                                    .clip(RoundedCornerShape(16.dp))

                                    .background(if (paymentMode == "ON CREDIT") Color(0xFF0D9488).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.35f))

                                    .clickable { paymentMode = "ON CREDIT" }

                                    .padding(8.dp),

                                contentAlignment = Alignment.Center

                            ) {

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                                    Image(

                                        painter = painterResource(id = R.drawable.ic_bill),

                                        contentDescription = TerminologyManager.getTerm("On Credit"),

                                        modifier = Modifier.size(36.dp)

                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(TerminologyManager.getTerm("ON CREDIT"), color = Color(0xFF0F172A), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)

                                }

                            }

                        }

                        // Bank Account picker. Previously this only rendered when 2+ bank
                        // ledgers existed, so with 0 or 1 bank ledgers tapping "BANK" showed no
                        // feedback at all — the payment mode was still being recorded (with 0
                        // accounts it silently auto-creates a generic "Bank Account" ledger on
                        // save, with 1 it auto-selects that one), but it looked broken since
                        // there was nothing to see or pick. Now it always shows, and lets you
                        // add a new bank account directly from here.

                        if (paymentMode == "BANK") {

                            Spacer(modifier = Modifier.height(12.dp))

                            var expandedBanks by remember { mutableStateOf(false) }

                            Box(

                                modifier = Modifier

                                    .fillMaxWidth()

                                    .height(44.dp)

                                    .clip(RoundedCornerShape(8.dp))

                                    .background(Color.White.copy(alpha = 0.55f))

                                    .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(8.dp))

                                    .clickable { expandedBanks = true }

                                    .padding(horizontal = 12.dp),

                                contentAlignment = Alignment.CenterStart

                            ) {

                                Row(

                                    modifier = Modifier.fillMaxWidth(),

                                    horizontalArrangement = Arrangement.SpaceBetween,

                                    verticalAlignment = Alignment.CenterVertically

                                ) {

                                    AutoShrinkText(

                                        text = selectedBankLedger?.name ?: TerminologyManager.getTerm("Select Bank Account"),

                                        color = Color(0xFF0F172A),

                                        maxFontSize = 13.sp,

                                        fontWeight = FontWeight.Bold

                                    )

                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = TerminologyManager.getTerm("Drop Down"), tint = Color(0xFF0D9488))

                                }

                                DropdownMenu(

                                    expanded = expandedBanks,

                                    onDismissRequest = { expandedBanks = false }

                                ) {

                                    bankAccounts.forEach { bank ->

                                        DropdownMenuItem(

                                            text = { Text(bank.name, fontWeight = FontWeight.Bold) },

                                            onClick = {

                                                selectedBankLedger = bank

                                                expandedBanks = false

                                            }

                                        )

                                    }

                                    DropdownMenuItem(

                                        text = { Text(TerminologyManager.getTerm("+ Add New Bank Account"), color = Color(0xFF0D9488), fontWeight = FontWeight.Bold) },

                                        onClick = {

                                            expandedBanks = false

                                            showCreateBankLedgerDialog = true

                                        }

                                    )

                                }

                            }

                        }

                    }

                    Spacer(modifier = Modifier.height(24.dp))

                }

            }

        }

        // --- Dialogs ---

        if (showDatePicker) {

            val cal = Calendar.getInstance().apply { time = date }

            DatePickerDialog(

                context,

                { _, year, month, dayOfMonth ->

                    cal.set(Calendar.YEAR, year)

                    cal.set(Calendar.MONTH, month)

                    cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                    date = cal.time

                    showDatePicker = false

                },

                cal.get(Calendar.YEAR),

                cal.get(Calendar.MONTH),

                cal.get(Calendar.DAY_OF_MONTH)

            ).apply {

                setOnDismissListener { showDatePicker = false }

                show()

            }

        }

        if (showStockItemSelection) {

            StockItemSelectionDialog(

                onDismissRequest = { showStockItemSelection = false },

                onStockItemSelected = { selected ->

                    pendingStockItem = selected

                    quantityInput = ""

                    totalAmountInput = ""

                    itemInputStage = 1

                    editingEntryIndex = null

                    showItemInputDialog = true

                    showStockItemSelection = false

                },

                onCreateNewClick = { showCreateStockItem = true }

            )

        }

        if (showCreateStockItem) {

            com.minhas.teller.ui.components.CreateStockItemDialog(

                onDismissRequest = { showCreateStockItem = false },

                onStockItemCreated = { newItem ->

                    pendingStockItem = newItem

                    quantityInput = ""

                    totalAmountInput = ""

                    itemInputStage = 1

                    editingEntryIndex = null

                    showItemInputDialog = true

                    showCreateStockItem = false

                }

            )

        }

        if (showLedgerSelection) {
            LedgerSelectionDialog(
                onDismissRequest = { showLedgerSelection = false },
                onLedgerSelected = { selected ->
                    selectedPartyLedger = selected
                    showLedgerSelection = false
                },
                onCreateNewClick = {
                    showCreateLedgerDialog = true
                },
                filter = { ledger ->
                    val gp = ledger.group.lowercase()
                    (gp.contains("creditor") || gp.contains("supplier") || gp.contains("debtor") || gp.contains("customer")) &&
                    !gp.contains("gst") && !gp.contains("sale") && !gp.contains("tax")
                }
            )
        }

        if (showCreateLedgerDialog) {
            AlertDialog(
                onDismissRequest = { showCreateLedgerDialog = false },
                title = { Text(TerminologyManager.getTerm("Create New Ledger")) },
                text = {
                    Column {
                        Text(
                            TerminologyManager.getTerm("What type of ledger do you want to create?"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        var expandedGroup by remember { mutableStateOf(false) }
                        
                        data class GroupOption(val internalName: String, val displayName: String, val description: String)
                        val groupOptions = listOf(
                            GroupOption("Sundry Creditors", TerminologyManager.getTerm("Supplier / Creditor"), TerminologyManager.getTerm("Person you buy from or who you owe money")),
                            GroupOption("Sundry Debtors", TerminologyManager.getTerm("Customer / Debtor"), TerminologyManager.getTerm("Person who buys from you or owes you money"))
                        )
                        
                        val selectedGroupOption = groupOptions.find { it.internalName == newLedgerGroup }
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            ModernDropdownField(
                                text = selectedGroupOption?.displayName ?: TerminologyManager.getTerm("Select Type"),
                                onClick = { expandedGroup = true },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                placeholder = TerminologyManager.getTerm("Select Type")
                            )
                            DropdownMenu(expanded = expandedGroup, onDismissRequest = { expandedGroup = false }) {
                                groupOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { 
                                            Column {
                                                Text(option.displayName, fontWeight = FontWeight.SemiBold)
                                                Text(option.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }, 
                                        onClick = { 
                                            newLedgerGroup = option.internalName
                                            expandedGroup = false 
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val nameHint = when (newLedgerGroup) {
                            "Sundry Debtors" -> TerminologyManager.getTerm("Enter customer/person name")
                            "Sundry Creditors" -> TerminologyManager.getTerm("Enter supplier/vendor name")
                            else -> TerminologyManager.getTerm("Enter name")
                        }
                        
                        Text(
                            TerminologyManager.getTerm("Enter the name"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ModernTextField(
                            value = newLedgerName,
                            onValueChange = { newLedgerName = it },
                            placeholder = nameHint,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newLedgerName.isNotBlank()) {
                            coroutineScope.launch {
                                val shouldMaintainBillWise = newLedgerGroup == "Sundry Debtors" || newLedgerGroup == "Sundry Creditors"
                                val newLedger = com.minhas.teller.data.entities.Ledger(
                                    name = newLedgerName, 
                                    group = newLedgerGroup, 
                                    openingBalance = 0L, 
                                    maintainBillWise = shouldMaintainBillWise,
                                    companyId = currentCompanyId ?: 0
                                )
                                val newId = ledgerViewModel.insertLedger(newLedger)
                                selectedPartyLedger = newLedger.copy(id = newId.toInt())
                                showCreateLedgerDialog = false
                                newLedgerName = "" // Reset
                            }
                        }
                    }) {
                        Text(TerminologyManager.getTerm("Create"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateLedgerDialog = false }) {
                        Text(TerminologyManager.getTerm("Cancel"))
                    }
                }
            )
        }

        if (showCreateBankLedgerDialog) {
            AlertDialog(
                onDismissRequest = { showCreateBankLedgerDialog = false },
                title = { Text(TerminologyManager.getTerm("Add Bank Account")) },
                text = {
                    Column {
                        Text(
                            TerminologyManager.getTerm("Enter the bank account name"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ModernTextField(
                            value = newBankLedgerName,
                            onValueChange = { newBankLedgerName = it },
                            placeholder = TerminologyManager.getTerm("e.g. HDFC Bank - 1234"),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newBankLedgerName.isNotBlank()) {
                            coroutineScope.launch {
                                val newLedger = com.minhas.teller.data.entities.Ledger(
                                    name = newBankLedgerName,
                                    group = "Bank Accounts",
                                    openingBalance = 0L,
                                    companyId = currentCompanyId ?: 0
                                )
                                val newId = ledgerViewModel.insertLedger(newLedger)
                                selectedBankLedger = newLedger.copy(id = newId.toInt())
                                showCreateBankLedgerDialog = false
                                newBankLedgerName = ""
                            }
                        }
                    }) {
                        Text(TerminologyManager.getTerm("Create"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateBankLedgerDialog = false }) {
                        Text(TerminologyManager.getTerm("Cancel"))
                    }
                }
            )
        }

    }

}

// Helper extension to draw a premium sweeping shimmer highlight effect

private fun Modifier.shimmerHighlight(durationMillis: Int = 3500): Modifier = composed {

    this.drawWithContent {

        val width = size.width

        val height = size.height

        // Draw static iridescent pearlescent gradient in the background (before content)

        val brush = Brush.linearGradient(

            colors = listOf(

                Color.Transparent,

                Color(0xFFFF3B30).copy(alpha = 0.08f), // Iridescent Red

                Color(0xFFFF9500).copy(alpha = 0.09f), // Iridescent Orange

                Color(0xFFFFCC00).copy(alpha = 0.12f), // Iridescent Yellow

                Color.White.copy(alpha = 0.20f),       // Soft center shine

                Color(0xFF4CD964).copy(alpha = 0.12f), // Iridescent Green

                Color(0xFF5AC8FA).copy(alpha = 0.09f), // Iridescent Blue

                Color(0xFF5856D6).copy(alpha = 0.08f), // Iridescent Purple

                Color.Transparent

            ),

            start = Offset(0f, 0f),

            end = Offset(width, height)

        )

        drawRect(brush = brush)

        // Draw the text, fields, and buttons on top

        drawContent()

    }

}

// Custom Shape for cyberpunk chamfered corners

class ChamferedCornerShape(private val cutSize: androidx.compose.ui.unit.Dp = 12.dp) : androidx.compose.ui.graphics.Shape {

    override fun createOutline(

        size: androidx.compose.ui.geometry.Size,

        layoutDirection: androidx.compose.ui.unit.LayoutDirection,

        density: androidx.compose.ui.unit.Density

    ): androidx.compose.ui.graphics.Outline {

        val cut = with(density) { cutSize.toPx() }

        val path = androidx.compose.ui.graphics.Path().apply {

            moveTo(cut, 0f)

            lineTo(size.width - cut, 0f)

            lineTo(size.width, cut)

            lineTo(size.width, size.height - cut)

            lineTo(size.width - cut, size.height)

            lineTo(cut, size.height)

            lineTo(0f, size.height - cut)

            lineTo(0f, cut)

            close()

        }

        return androidx.compose.ui.graphics.Outline.Generic(path)

    }

}

// Custom modifier to draw subtle glowing circuit board traces

private fun Modifier.drawCircuitTraces(): Modifier = this.drawWithContent {

    drawContent()

    val width = size.width

    val height = size.height

    val traceColor = Color(0xFF0EA5E9).copy(alpha = 0.09f) // soft glowing cyan

    // Top-Left to Center Trace

    drawLine(

        color = traceColor,

        start = Offset(width * 0.05f, height * 0.25f),

        end = Offset(width * 0.18f, height * 0.25f),

        strokeWidth = 1.2.dp.toPx()

    )

    drawLine(

        color = traceColor,

        start = Offset(width * 0.18f, height * 0.25f),

        end = Offset(width * 0.24f, height * 0.45f),

        strokeWidth = 1.2.dp.toPx()

    )

    drawCircle(

        color = traceColor,

        radius = 2.5.dp.toPx(),

        center = Offset(width * 0.24f, height * 0.45f)

    )

    // Bottom-Right to Center Trace

    drawLine(

        color = traceColor,

        start = Offset(width * 0.95f, height * 0.75f),

        end = Offset(width * 0.82f, height * 0.75f),

        strokeWidth = 1.2.dp.toPx()

    )

    drawLine(

        color = traceColor,

        start = Offset(width * 0.82f, height * 0.75f),

        end = Offset(width * 0.76f, height * 0.55f),

        strokeWidth = 1.2.dp.toPx()

    )

    drawCircle(

        color = traceColor,

        radius = 2.5.dp.toPx(),

        center = Offset(width * 0.76f, height * 0.55f)

    )

}

// Custom modifier to draw a soft feathered drop shadow to simulate a floating element

private fun Modifier.floatingDropShadowPvc(borderRadius: androidx.compose.ui.unit.Dp = 23.dp): Modifier = this.drawBehind {

    val shadowColor = Color.Black.copy(alpha = 0.14f)

    for (i in 1..6) {

        val offset = i.dp.toPx()

        val inflate = (i * 0.6f).dp.toPx()

        drawRoundRect(

            color = shadowColor,

            topLeft = Offset(-inflate, offset - inflate),

            size = androidx.compose.ui.geometry.Size(size.width + 2 * inflate, size.height + 2 * inflate),

            cornerRadius = androidx.compose.ui.geometry.CornerRadius(borderRadius.toPx())

        )

    }

}

// Custom modifier to simulate a sunken debossed pocket with inner shadows following rounded corners
private fun Modifier.debossedCapsule(
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 1.5.dp,
    shadowColor: Color = Color.Black.copy(alpha = 0.28f),
    highlightColor: Color = Color.White.copy(alpha = 0.75f)
): Modifier = this.drawBehind {
    val W = size.width
    val H = size.height
    val r = cornerRadius.toPx()
    val sw = strokeWidth.toPx()
    val inset = sw / 2f

    // Draw the sunken pocket background (slightly darker/cooler gray)
    drawRoundRect(
        color = Color(0xFFCBD5E1),
        cornerRadius = CornerRadius(r)
    )

    // 1. Top-Left Shadow Path
    val shadowPath = Path().apply {
        // Bottom-left corner (from 135 to 180 degrees)
        arcTo(
            rect = Rect(
                left = inset,
                top = H - inset - 2 * r,
                right = inset + 2 * r,
                bottom = H - inset
            ),
            startAngleDegrees = 135f,
            sweepAngleDegrees = 45f,
            forceMoveTo = true
        )
        // Top-left corner (from 180 to 270 degrees)
        arcTo(
            rect = Rect(
                left = inset,
                top = inset,
                right = inset + 2 * r,
                bottom = inset + 2 * r
            ),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        // Top-right corner (from 270 to 315 degrees)
        arcTo(
            rect = Rect(
                left = W - inset - 2 * r,
                top = inset,
                right = W - inset,
                bottom = inset + 2 * r
            ),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 45f,
            forceMoveTo = false
        )
    }

    // 2. Bottom-Right Highlight Path
    val highlightPath = Path().apply {
        // Top-right corner (from 315 to 360/0 degrees)
        arcTo(
            rect = Rect(
                left = W - inset - 2 * r,
                top = inset,
                right = W - inset,
                bottom = inset + 2 * r
            ),
            startAngleDegrees = 315f,
            sweepAngleDegrees = 45f,
            forceMoveTo = true
        )
        // Bottom-right corner (from 0 to 90 degrees)
        arcTo(
            rect = Rect(
                left = W - inset - 2 * r,
                top = H - inset - 2 * r,
                right = W - inset,
                bottom = H - inset
            ),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        // Bottom-left corner (from 90 to 135 degrees)
        arcTo(
            rect = Rect(
                left = inset,
                top = H - inset - 2 * r,
                right = inset + 2 * r,
                bottom = H - inset
            ),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 45f,
            forceMoveTo = false
        )
    }

    // Draw the paths
    drawPath(
        path = shadowPath,
        color = shadowColor,
        style = Stroke(width = sw, cap = StrokeCap.Round)
    )
    drawPath(
        path = highlightPath,
        color = highlightColor,
        style = Stroke(width = sw, cap = StrokeCap.Round)
    )
}

// Custom modifier to draw an inner shadow for neumorphic depth effect
private fun Modifier.innerShadow(
    shape: androidx.compose.ui.graphics.Shape,
    color: Color = Color.Black,
    blur: androidx.compose.ui.unit.Dp = 4.dp,
    offsetY: androidx.compose.ui.unit.Dp = 2.dp,
    offsetX: androidx.compose.ui.unit.Dp = 2.dp,
    spread: androidx.compose.ui.unit.Dp = 0.dp
) = this.drawWithContent {
    drawContent()
    drawIntoCanvas { canvas ->
        val shadowSize = size
        val shadowOutline = shape.createOutline(shadowSize, layoutDirection, this)
        val paint = Paint().apply {
            this.color = color
            this.isAntiAlias = true
            this.style = PaintingStyle.Stroke
            this.strokeWidth = spread.toPx() + blur.toPx()
        }
        val frameworkPaint = paint.asFrameworkPaint()
        if (blur.toPx() > 0) {
            frameworkPaint.maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(
                org.jetbrains.skia.FilterBlurMode.NORMAL,
                blur.toPx()
            )
        }
        canvas.save()
        canvas.translate(offsetX.toPx(), offsetY.toPx())
        canvas.drawOutline(shadowOutline, paint)
        canvas.restore()
    }
}

private fun Modifier.neumorphicShadowPvc(
    shape: androidx.compose.ui.graphics.Shape,
    darkShadowColor: Color = Color(0xFFA3B1C6),
    lightShadowColor: Color = Color(0xFFFFFFFF),
    blur: androidx.compose.ui.unit.Dp = 8.dp,
    offset: androidx.compose.ui.unit.Dp = 5.dp
) = this.drawBehind {
    val blurPx = blur.toPx()
    val offsetPx = offset.toPx()
    
    drawIntoCanvas { canvas ->
        val paintDark = Paint().apply {
            color = darkShadowColor
            isAntiAlias = true
        }
        val frameworkPaintDark = paintDark.asFrameworkPaint()
        if (blurPx > 0) {
            frameworkPaintDark.maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(
                org.jetbrains.skia.FilterBlurMode.NORMAL,
                blurPx
            )
        }
        
        val paintLight = Paint().apply {
            color = lightShadowColor
            isAntiAlias = true
        }
        val frameworkPaintLight = paintLight.asFrameworkPaint()
        if (blurPx > 0) {
            frameworkPaintLight.maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(
                org.jetbrains.skia.FilterBlurMode.NORMAL,
                blurPx
            )
        }
        
        val outline = shape.createOutline(size, layoutDirection, this)
        
        // Draw Light Shadow (Top-Left offset, negative X and Y)
        canvas.save()
        canvas.translate(-offsetPx, -offsetPx)
        canvas.drawOutline(outline, paintLight)
        canvas.restore()
        
        // Draw Dark Shadow (Bottom-Right offset, positive X and Y)
        canvas.save()
        canvas.translate(offsetPx, offsetPx)
        canvas.drawOutline(outline, paintDark)
        canvas.restore()
    }
}

