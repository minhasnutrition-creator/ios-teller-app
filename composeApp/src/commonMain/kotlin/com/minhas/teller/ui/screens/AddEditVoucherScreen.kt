package com.minhas.teller.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.CameraAlt // Import Camera Icon
import androidx.compose.material.icons.filled.Mic // Import Mic Icon
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Add this import
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.utils.rupeesToPaise
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.utils.parseToPaiseOrZero
import com.minhas.teller.data.entities.BillAllocation
import com.minhas.teller.data.entities.HsnEntry
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.data.repositories.UnitRepository
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import com.minhas.teller.ui.viewmodels.LedgerViewModel
import com.minhas.teller.ui.components.CreateStockItemDialog
import com.minhas.teller.ui.components.StockItemSelectionDialog
import androidx.core.content.FileProvider
import java.io.File
import android.net.Uri
import com.minhas.teller.ui.viewmodels.LedgerViewModelFactory
import com.minhas.teller.ui.viewmodels.StockItemViewModel
import com.minhas.teller.ui.viewmodels.StockItemViewModelFactory
import com.minhas.teller.ui.viewmodels.UnitViewModel
import com.minhas.teller.ui.viewmodels.UnitViewModelFactory
import com.minhas.teller.ui.components.LedgerSelectionDialog
import com.minhas.teller.ui.components.StockItemSelectionDialog
import com.minhas.teller.ui.components.OutstandingBillsDialog
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.SessionManager
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.data.entities.Company
import com.minhas.teller.ui.components.ValidatedNumberTextField
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.painterResource
import com.minhas.teller.R
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.utils.LanguageType
import androidx.compose.material.icons.automirrored.filled.HelpOutline // Import Help Icon
import androidx.compose.ui.graphics.Brush // Added
import com.minhas.teller.ui.components.* // Added Modern Components
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.foundation.border // Added
import com.minhas.teller.utils.LicenseUtils
import com.minhas.teller.navigation.Screen
import com.minhas.teller.utils.TutorialManager
import com.minhas.teller.utils.TutorialStep
import com.minhas.teller.utils.GstStateCodes
import com.minhas.teller.utils.GstComplianceRules
import com.minhas.teller.data.gst.SupplyType
import com.minhas.teller.ui.components.InteractiveTutorialOverlay
import kotlinx.coroutines.delay

// Helper to get consistent text field colors for visibility on dark background
@Composable
private fun getTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    cursorColor = MaterialTheme.colorScheme.primary
)

data class HelpInfo(val title: String, val message: String)

@Composable
private fun HelpDialog(title: String, helpText: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(helpText) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(TerminologyManager.getTerm("Got it"))
            }
        }
    )
}


// 1. Defined Metallic Gradients for 3D Icon Simulation
private val GoldGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFFFF099), Color(0xFFD4AF37), Color(0xFF8A6600))
)
private val SilverGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFFFFFFF), Color(0xFFC0C0C0), Color(0xFF5A5A5A))
)
private val BronzeGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFFFC299), Color(0xFFCD7F32), Color(0xFF703800))
)
private val TitaniumGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFF0F8FF), Color(0xFFA2B5C6), Color(0xFF4A5D6E))
)

private data class PaymentItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val metallicBrush: Brush,
    val mode: String
)

@Composable
private fun Skeuomorphic3DButton(
    item: PaymentItem,
    isSelected: Boolean,
    themeColors: TopThemeColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val buttonShape = RoundedCornerShape(20.dp)
    
    // Dynamic offsets for interactive press/select feedback
    val topOffset = if (isSelected) 3.5.dp else 0.dp
    val edgeOffset = if (isSelected) 1.5.dp else 3.5.dp
    val shadowElevation = if (isSelected) 4.dp else 12.dp

    // Dynamic skeuomorphic colors from the active screen theme
    val themeColorTop = themeColors.dateTint
    val themeColorMid = themeColors.voucherTint
    val themeColorBottom = themeColors.voucherText

    val faceBrush = if (isSelected) {
        Brush.verticalGradient(
            colors = listOf(
                themeColorMid,
                themeColorBottom,
                themeColorBottom.copy(alpha = 0.8f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                themeColorTop,
                themeColorMid,
                themeColorBottom
            )
        )
    }

    // Top rim highlight line
    val highlightColor = themeColorTop.copy(alpha = 0.8f)
    val rimBrush = if (isSelected) {
        Brush.verticalGradient(
            colors = listOf(
                highlightColor.copy(alpha = 0.4f),
                themeColorBottom.copy(alpha = 0.1f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                highlightColor,
                themeColorBottom.copy(alpha = 0.2f)
            )
        )
    }

    Box(
        modifier = modifier
            .height(95.dp)
            .clickable { onClick() }
    ) {
        // 1. BOTTOM LAYER: The tight, physical 3D extruded rim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = edgeOffset)
                .shadow(
                    elevation = shadowElevation,
                    shape = buttonShape,
                    ambientColor = themeColorBottom.copy(alpha = 0.3f),
                    spotColor = themeColorBottom.copy(alpha = 0.7f)
                )
                .background(
                    color = themeColorBottom, // Match deep rich shadow color
                    shape = buttonShape
                )
        )

        // 2. TOP LAYER: The rich, glossy button face
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = topOffset)
                .background(
                    brush = faceBrush,
                    shape = buttonShape
                )
                // Thin, crisp reflection line on the top rim
                .border(
                    width = 1.5.dp,
                    brush = rimBrush,
                    shape = buttonShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp)
            ) {
                // 3. ICON CONTAINER: No shadow, enlarged size
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer { alpha = 0.99f }
                        .drawWithCache {
                            onDrawWithContent {
                                drawContent()
                                drawRect(
                                    brush = item.metallicBrush,
                                    blendMode = BlendMode.SrcAtop
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 4. BOLD TEXT LABELS
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                    maxLines = 2
                )
            }
        }
    }
}



/**
 * Rounded section panel used to visually separate the blocks of the simple-mode form
 * (items / payment type / party / totals), mirroring the grouping the Purchase screen
 * gets from its GlassCard.
 *
 * Unlike GlassCard this stays light: the body text in these sections is dark
 * (0xFF32194D deep purple and the crimson voucherText), so the panel is a near-white
 * surface with the screen's own accent used only for the tint and border. That keeps
 * the panel tied to the voucher's colour theme without dropping text contrast.
 */
@Composable
private fun VoucherSectionCard(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.72f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    accent.copy(alpha = 0.55f),
                    accent.copy(alpha = 0.22f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.05f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditVoucherScreen(navController: NavHostController, voucherId: Int?, initialType: String? = null, isDuplicate: Boolean = false) {
    val context = LocalContext.current
    val currentLanguage by TerminologyManager.currentLanguage.collectAsState()
    val isSimpleLanguage = currentLanguage == LanguageType.SIMPLE

    // --- TUTORIAL LOGIC ---
    val isTutorialActive = TutorialManager.isTutorialActive
    val currentStep = TutorialManager.currentStep
    
    // We need to observe all vouchers to find the one we just created for deletion
    // The ViewModel already exposes allVouchersWithAllEntries, we just need to collect it
    // But we need to make sure we have access to the viewModel first.
    // The viewModel is initialized below. So we should move this block DOWN or initialize it earlier.
    // Actually, `voucherViewModel` is initialized below. I should insert this AFTER viewModel init.


    // --- EDUCATIONAL MODE CHECK ---
    // Block NEW voucher creation in Educational Mode (editing existing is allowed for viewing)
    var appStatus by remember { mutableStateOf<LicenseUtils.AppStatus?>(null) }
    var showEducationalBlocker by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        val (status, _) = LicenseUtils.getAppStatus(context)
        appStatus = status
        // Educational mode blocking removed - using simplified approach
    }
    

    // Help Dialog State
    var showHelpDialogFor by remember { mutableStateOf<HelpInfo?>(null) }
    
    fun showHelpDialog(title: String, message: String) {
        showHelpDialogFor = HelpInfo(title, message)
    }

    if (showHelpDialogFor != null) {
        HelpDialog(
            title = showHelpDialogFor!!.title,
            helpText = showHelpDialogFor!!.message,
            onDismiss = { showHelpDialogFor = null }
        )
    }

    val database = AppDatabase.getDatabase(context)

    // Updated ViewModel Factory
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

    val unitRepository = remember { UnitRepository(database.unitDao()) }
    val unitViewModel: UnitViewModel = viewModel(factory = UnitViewModelFactory(unitRepository))
    val allUnits by unitViewModel.allUnits.collectAsState(initial = emptyList())

    val companyRepository = remember { CompanyRepository(database.companyDao()) }
    val companyViewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(companyRepository))
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val company by produceState<Company?>(initialValue = null, currentCompanyId) {
        currentCompanyId?.let { id ->
            companyViewModel.getCompany(id).collect { value = it }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    var type by remember { mutableStateOf(initialType ?: "Payment") }

    // New Ledger Creation State
    var showCreateLedgerDialog by remember { mutableStateOf(false) }
    var newLedgerName by remember { mutableStateOf("") }
    var newLedgerGroup by remember { mutableStateOf("Sundry Debtors") } // Default

    // New Stock Item Creation State (Simple Mode) — drives CreateStockItemDialog below, which
    // handles its own name/unit/HSN fields internally.
    var showCreateStockItemDialog by remember { mutableStateOf(false) }

    var date by remember { mutableStateOf(Date()) }
    var voucherNumber by remember { mutableStateOf("") }
    var narration by remember { mutableStateOf("") }
    var customField by remember { mutableStateOf("") } // Custom Field State
    var showCustomField by remember { mutableStateOf(false) } // Toggle for custom field

    // GST return fields (Sales/outward)
    var placeOfSupply by remember { mutableStateOf("") }
    var supplyType by remember { mutableStateOf("") }
    var reverseCharge by remember { mutableStateOf(false) }

    // Interest Calculation State
    var chargeInterest by remember { mutableStateOf(false) }
    var interestRate by remember { mutableStateOf("") }
    var interestDays by remember { mutableStateOf("") }

    val debitEntries = remember { mutableStateListOf<VoucherEntry>() }
    val creditEntries = remember { mutableStateListOf<VoucherEntry>() }
    val inventoryEntries = remember { mutableStateListOf<InventoryEntry>() }
    // For Journal/Adjustment barter entries: items GIVEN AWAY (stock out).
    // Kept separate from inventoryEntries (which doubles as "items RECEIVED" for Journal)
    // so both lists can show plain positive quantities in the UI; they're merged with a
    // sign flip right before saving.
    val stockOutEntries = remember { mutableStateListOf<InventoryEntry>() }

    val billAllocations = remember { mutableStateListOf<BillAllocation>() }

    // Simple Mode State
    val useSimpleUI = (type in listOf("Sales", "Purchase", "Receipt", "Payment"))
    var simplePaymentMode by remember { mutableStateOf("Pay Later") } // "Pay Later", "Cash", "Bank"
    var simplePartyLedger by remember { mutableStateOf<com.minhas.teller.data.entities.Ledger?>(null) }
    var ledgerFilter by remember { mutableStateOf<((com.minhas.teller.data.entities.Ledger) -> Boolean)?>(null) }

    // Unsaved-changes guard for Back navigation: a fresh voucher starts with placeholder
    // entries (ledgerId/stockItemId 0, amount 0), so "list is non-empty" alone can't signal
    // dirty state — only entries a user actually filled in count.
    var showDiscardChangesDialog by remember { mutableStateOf(false) }
    fun hasUnsavedVoucherChanges(): Boolean {
        return narration.isNotBlank() ||
            simplePartyLedger != null ||
            debitEntries.any { it.ledgerId != 0 || it.amount != 0L } ||
            creditEntries.any { it.ledgerId != 0 || it.amount != 0L } ||
            inventoryEntries.any { it.stockItemId != 0 || it.amount != 0L } ||
            stockOutEntries.any { it.stockItemId != 0 || it.amount != 0L }
    }
    fun requestNavigateBack() {
        if (hasUnsavedVoucherChanges()) showDiscardChangesDialog = true else navController.popBackStack()
    }
    BackHandler(enabled = true) { requestNavigateBack() }


    // Initialize Simple Mode Defaults
    LaunchedEffect(useSimpleUI, type) {
        if (useSimpleUI) {
            // Inventory Placeholder (Only for Sales/Purchase)
            if ((type == "Sales" || type == "Purchase") && inventoryEntries.isEmpty()) {
                inventoryEntries.add(InventoryEntry(voucherId = 0, stockItemId = 0, quantity = 1.0, rate = 0.0, amount = 0L))
            }
            // Ensure Debit/Credit entries exist for mapping
             if (debitEntries.isEmpty()) debitEntries.add(VoucherEntry(voucherId = 0, ledgerId = 0, amount = 0L, isDebit = true))
             if (creditEntries.isEmpty()) creditEntries.add(VoucherEntry(voucherId = 0, ledgerId = 0, amount = 0L, isDebit = false))
             
             // Pre-set Payment Mode if editing existing? (For now default Pay Later)
             // But if new, we can set intelligent defaults
             if (type == "Receipt") simplePaymentMode = "Cash" // Usually Cash
             if (type == "Payment") simplePaymentMode = "Cash" 
        }
    }

    // Gemini Analysis State
    var isAnalyzing by remember { mutableStateOf(false) }
    var showScanConfirmationDialog by remember { mutableStateOf<com.minhas.teller.utils.SmartReceiptData?>(null) }
    var isOcrSaving by remember { mutableStateOf(false) }
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Google Doc Scanner Launcher
    val scannerLauncher = rememberLauncherForActivityResult(StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pages = scanResult?.pages
            if (!pages.isNullOrEmpty()) {
                val imageUri = pages[0].imageUri
                isAnalyzing = true
                coroutineScope.launch {
                    val (receiptData, scanMethod) = com.minhas.teller.utils.OcrHelper.analyzeReceiptWithGemini(context, imageUri, voucherType = type)
                    isAnalyzing = false

                    if (receiptData != null) {
                        val taggedData = if (scanMethod == "gemini") {
                            receiptData.copy(narration = (receiptData.narration ?: "") + " [AI Scanned]")
                        } else receiptData
                        showScanConfirmationDialog = taggedData
                    } else {
                        Toast.makeText(context, TerminologyManager.getTerm("Could not extract receipt data."), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    if (showScanConfirmationDialog != null) {
        val data = showScanConfirmationDialog!!
        AlertDialog(
            onDismissRequest = { showScanConfirmationDialog = null },
            title = { Text(TerminologyManager.getTerm("Scanned Details")) },
            text = {
                Column {
                    Text("${TerminologyManager.getTerm("Date")}: ${data.date?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it) } ?: TerminologyManager.getTerm("Not detected")}")
                    Text("${TerminologyManager.getTerm("Vendor")}: ${data.vendor ?: TerminologyManager.getTerm("Not detected")}")
                    Text("${TerminologyManager.getTerm("Amount")}: ${data.amount ?: TerminologyManager.getTerm("Not detected")}")
                    Text("${TerminologyManager.getTerm("Voucher Type")}: ${TerminologyManager.getTerm(type)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(TerminologyManager.getTerm("Do you want to use these details?"))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Validate company before attempting save
                        val cid = currentCompanyId
                        if (cid == null || cid == 0) {
                            Toast.makeText(context, TerminologyManager.getTerm("No company selected. Please select a company first."), Toast.LENGTH_LONG).show()
                            return@TextButton
                        }
                        isOcrSaving = true
                        coroutineScope.launch {
                            try {
                                // 1. Apply scanned data to form fields
                                data.date?.let { date = it }
                                var description = "AI Scan: "
                                data.vendor?.let { description += "$it " }
                                data.narration?.let { description += "($it)" }
                                if (description != "AI Scan: ") narration = description

                                // 2. Determine appropriate ledger group based on SCREEN's voucher type
                                //    The voucher type is ALWAYS locked to the current screen, never derived from OCR.
                                val targetGroup = when (type) {
                                    "Sales" -> "Sundry Debtors"
                                    "Purchase" -> "Sundry Creditors"
                                    "Payment" -> "Cash-in-hand"
                                    "Receipt" -> "Cash-in-hand"
                                    else -> "Sundry Creditors"
                                }

                                // 3. Find or create the party/vendor ledger from scanned vendor name
                                var vendorLedger: com.minhas.teller.data.entities.Ledger? = null
                                if (!data.vendor.isNullOrBlank()) {
                                    vendorLedger = allLedgers.find {
                                        it.name.equals(data.vendor, ignoreCase = true) && it.group.equals(targetGroup, ignoreCase = true)
                                    }
                                    if (vendorLedger == null) {
                                        val newId = ledgerViewModel.insertLedger(
                                            com.minhas.teller.data.entities.Ledger(
                                                name = data.vendor.replaceFirstChar { it.uppercase() },
                                                group = targetGroup,
                                                openingBalance = 0L,
                                                companyId = cid ?: 0
                                            )
                                        )
                                        vendorLedger = com.minhas.teller.data.entities.Ledger(
                                            id = newId.toInt(),
                                            name = data.vendor.replaceFirstChar { it.uppercase() },
                                            group = targetGroup,
                                            openingBalance = 0L,
                                            companyId = cid ?: 0
                                        )
                                    }
                                }

                                // 4. Link vendor ledger to form state
                                if (vendorLedger != null) {
                                    if (useSimpleUI) simplePartyLedger = vendorLedger
                                    if (type == "Sales" && customField.isBlank()) {
                                        customField = vendorLedger.name
                                    }
                                }

                                // 5. Assign ledgers correctly for each voucher type (double-entry).
                                //    The voucher type is LOCKED to the screen — only ONE voucher is created.
                                when (type) {
                                    "Sales" -> {
                                        // Debit = customer (Sundry Debtors), Credit = Sales Account
                                        if (debitEntries.isNotEmpty() && vendorLedger != null)
                                            debitEntries[0] = debitEntries[0].copy(ledgerId = vendorLedger.id)
                                        if (creditEntries.isNotEmpty()) {
                                            val salesLedger = allLedgers.find {
                                                it.group.contains("Sales", ignoreCase = true) ||
                                                    it.name.contains("Sales", ignoreCase = true)
                                            } ?: allLedgers.find {
                                                it.group.contains("Direct Income", ignoreCase = true)
                                            }
                                            if (salesLedger != null)
                                                creditEntries[0] = creditEntries[0].copy(ledgerId = salesLedger.id)
                                        }
                                    }
                                    "Purchase" -> {
                                        // Debit = Purchase Account, Credit = supplier (Sundry Creditors)
                                        if (debitEntries.isNotEmpty()) {
                                            val purchaseLedger = allLedgers.find {
                                                it.group.contains("Purchase", ignoreCase = true) ||
                                                    it.name.contains("Purchase", ignoreCase = true)
                                            }
                                            if (purchaseLedger != null)
                                                debitEntries[0] = debitEntries[0].copy(ledgerId = purchaseLedger.id)
                                        }
                                        if (creditEntries.isNotEmpty() && vendorLedger != null)
                                            creditEntries[0] = creditEntries[0].copy(ledgerId = vendorLedger.id)
                                    }
                                    "Receipt" -> {
                                        // Debit = Cash/Bank (where money landed), Credit = payer
                                        if (debitEntries.isNotEmpty()) {
                                            val cashBank = allLedgers.find {
                                                it.group.contains("Cash", ignoreCase = true) ||
                                                    it.group.contains("Bank", ignoreCase = true)
                                            }
                                            if (cashBank != null)
                                                debitEntries[0] = debitEntries[0].copy(ledgerId = cashBank.id)
                                        }
                                        if (creditEntries.isNotEmpty() && vendorLedger != null)
                                            creditEntries[0] = creditEntries[0].copy(ledgerId = vendorLedger.id)
                                    }
                                    "Payment" -> {
                                        // Debit = payee, Credit = Cash/Bank (where money came from)
                                        if (debitEntries.isNotEmpty() && vendorLedger != null)
                                            debitEntries[0] = debitEntries[0].copy(ledgerId = vendorLedger.id)
                                        if (creditEntries.isNotEmpty()) {
                                            val cashBank = allLedgers.find {
                                                it.group.contains("Cash", ignoreCase = true) ||
                                                    it.group.contains("Bank", ignoreCase = true)
                                            }
                                            if (cashBank != null)
                                                creditEntries[0] = creditEntries[0].copy(ledgerId = cashBank.id)
                                        }
                                    }
                                }

                                // 6. Set amounts on both sides from scanned amount (ledgerIds already set in step 5)
                                data.amount?.let { amt ->
                                    if (debitEntries.isNotEmpty())
                                        debitEntries[0] = debitEntries[0].copy(amount = amt.rupeesToPaise())
                                    if (creditEntries.isNotEmpty())
                                        creditEntries[0] = creditEntries[0].copy(amount = amt.rupeesToPaise())
                                }

                                // 7. Create inventory entry for Sales/Purchase types
                                if ((type == "Sales" || type == "Purchase") && data.amount != null) {
                                    inventoryEntries.clear()
                                    inventoryEntries.add(
                                        InventoryEntry(
                                            id = 0, voucherId = 0, stockItemId = 0,
                                            quantity = 1.0, rate = data.amount, amount = data.amount.rupeesToPaise()
                                        )
                                    )
                                }

                                // 8. Balance totals so Dr == Cr
                                val totalDr = debitEntries.sumOf { it.amount }
                                val totalCr = creditEntries.sumOf { it.amount }
                                if (kotlin.math.abs(totalDr - totalCr) > 0L) {
                                    val balanced = (totalDr + totalCr) / 2
                                    if (debitEntries.isNotEmpty()) debitEntries[0] = debitEntries[0].copy(amount = balanced)
                                    if (creditEntries.isNotEmpty()) creditEntries[0] = creditEntries[0].copy(amount = balanced)
                                }

                                // 9. Clear bill allocations for non-payment/receipt vouchers
                                if (type != "Payment" && type != "Receipt") billAllocations.clear()

                                // 10. Build the Voucher object (mirrors the normal SAVE button exactly)
                                val refDisplay = billAllocations.firstOrNull { !it.refName.isNullOrBlank() }?.refName
                                val parsedInterestRate = interestRate.toDoubleOrNull() ?: 0.0
                                val parsedInterestDays = interestDays.toIntOrNull() ?: 0
                                val newVoucher = Voucher(
                                    id = if (voucherId == -1 || voucherId == null || isDuplicate) 0 else voucherId,
                                    type = type, date = date, voucherNumber = voucherNumber,
                                    narration = narration.ifBlank { null },
                                    customField = customField.ifBlank { null },
                                    chargeInterest = chargeInterest,
                                    interestRate = if (chargeInterest) parsedInterestRate else 0.0,
                                    interestDays = if (chargeInterest) parsedInterestDays else 0,
                                    refDisplayName = refDisplay,
                                    companyId = cid ?: 0,
                                    placeOfSupply = placeOfSupply.ifBlank { null },
                                    supplyType = supplyType.ifBlank { null },
                                    reverseCharge = reverseCharge
                                )
                                val isNewVoucher = voucherId == -1 || voucherId == null || isDuplicate
                                val finalInventoryEntries = inventoryEntries.toList()
                                val allEntries = debitEntries + creditEntries

                                // 11. Save — OCR ALWAYS creates a SINGLE voucher of the screen's type.
                                //     Never auto-create companion Receipt/Payment vouchers for OCR.
                                //     The user can handle payment settlement separately if needed.
                                if (isNewVoucher) {
                                    voucherViewModel.insertVoucherWithAllEntries(
                                        newVoucher, allEntries, finalInventoryEntries, billAllocations
                                    )
                                    Toast.makeText(context, TerminologyManager.getTerm("Saved"), Toast.LENGTH_SHORT).show()
                                } else {
                                    voucherViewModel.updateVoucherWithAllEntries(
                                        newVoucher, allEntries, finalInventoryEntries, billAllocations
                                    )
                                    Toast.makeText(context, TerminologyManager.getTerm("Updated"), Toast.LENGTH_SHORT).show()
                                }

                                // 12. Dismiss dialog and navigate back
                                showScanConfirmationDialog = null
                                isOcrSaving = false
                                navController.popBackStack()
                            } catch (e: Exception) {
                                isOcrSaving = false
                                Toast.makeText(context, "OCR save failed: ${e.message}", Toast.LENGTH_LONG).show()
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
                        Text(TerminologyManager.getTerm("Yes, Save"))
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { 
                        showScanConfirmationDialog = null
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
                        Text(TerminologyManager.getTerm("Scan Again"))
                    }
                    TextButton(onClick = { 
                         // Apply Data but let user edit
                        data.date?.let { date = it }
                        var description = "AI Scan: "
                        data.vendor?.let { description += "$it " }
                        data.narration?.let { description += "($it)" }
                        if (description != "AI Scan: ") narration = description
                        data.amount?.let { amt ->
                             if (debitEntries.isNotEmpty() && debitEntries[0].amount == 0L) {
                                 debitEntries[0] = debitEntries[0].copy(amount = amt.rupeesToPaise())
                             }
                        }
                        showScanConfirmationDialog = null
                    }) {
                        Text(TerminologyManager.getTerm("Edit Manually"))
                    }
                }
            }
        )
    }

    val voucherTypes = listOf("Payment", "Receipt", "Journal", "Sales", "Purchase", "Quotation")
    var expanded by remember { mutableStateOf(false) }

    var showLedgerSelectionDialog by remember { mutableStateOf(false) }
    var currentLedgerEntryIndex by remember { mutableStateOf(-1) }
    var isDebitEntrySelection by remember { mutableStateOf(false) }

    var showStockItemSelectionDialog by remember { mutableStateOf(false) }
    var currentInventoryEntryIndex by remember { mutableStateOf(-1) }
    // currentInventoryEntryIndex encoding: -1 = unset, 0.. = index into inventoryEntries
    // (also used as the "Received" list for Journal), <= -2 = index into stockOutEntries
    // (Journal "Given" list) via -(i + 2).
    fun updateInventoryEntryAt(index: Int, updater: (InventoryEntry) -> InventoryEntry) {
        if (index >= 0) {
            if (index < inventoryEntries.size) inventoryEntries[index] = updater(inventoryEntries[index])
        } else if (index <= -2) {
            val outIdx = -(index) - 2
            if (outIdx < stockOutEntries.size) stockOutEntries[outIdx] = updater(stockOutEntries[outIdx])
        }
    }

    // A scanned barcode fills the first still-empty row rather than always appending a new one, so
    // scanning into the default single blank row (the common case) doesn't leave a spare row behind.
    fun fillInventoryRowWithStockItem(item: com.minhas.teller.data.entities.StockItem) {
        val idx = inventoryEntries.indexOfFirst { it.stockItemId == 0 }.let { existing ->
            if (existing >= 0) existing else {
                inventoryEntries.add(InventoryEntry(voucherId = 0, stockItemId = 0, quantity = 1.0, rate = 0.0, amount = 0L))
                inventoryEntries.size - 1
            }
        }
        updateInventoryEntryAt(idx) { current ->
            val newRate = if (current.rate == 0.0) item.rate else current.rate
            val qty = if (current.quantity <= 0.0) 1.0 else current.quantity
            current.copy(
                stockItemId = item.id,
                quantity = qty,
                rate = newRate,
                amount = (qty * newRate).rupeesToPaise(),
                hsnCode = item.hsnCode,
                gstRate = item.gstRate,
                cgstRate = item.cgstRate,
                sgstRate = item.sgstRate,
                igstRate = item.igstRate
            )
        }
    }

    var showBarcodeNotLinkedDialog by remember { mutableStateOf(false) }
    var showBarcodeLinkPicker by remember { mutableStateOf(false) }
    var showCreateStockItemForBarcode by remember { mutableStateOf(false) }
    var pendingBarcode by remember { mutableStateOf<String?>(null) }

    val barcodeResultHandle = navController.currentBackStackEntry?.savedStateHandle
    val barcodeResult = barcodeResultHandle?.getStateFlow<String?>("barcode_result", null)?.collectAsState()
    LaunchedEffect(barcodeResult?.value) {
        val code = barcodeResult?.value ?: return@LaunchedEffect
        barcodeResultHandle.remove<String>("barcode_result")
        val companyId = currentCompanyId ?: 0
        val match = stockItemRepository.getByBarcode(companyId, code)
        if (match != null) {
            fillInventoryRowWithStockItem(match)
            android.widget.Toast.makeText(context, "${TerminologyManager.getTerm("Added")}: ${match.name}", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            pendingBarcode = code
            showBarcodeNotLinkedDialog = true
        }
    }

    var showOutstandingDialog by remember { mutableStateOf(false) }
    val outstandingVouchers by voucherViewModel.outstandingVouchers.collectAsState()

    var isVoucherNumberValid by remember { mutableStateOf(true) }
    var showSaveSuccessDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // HSN & Tax Automation State Variables
    val failedAutoAssignRows = remember { mutableStateMapOf<Int, Boolean>() }
    val selectedCategoryRows = remember { mutableStateMapOf<Int, String>() }
    val selectedSubCategoryRows = remember { mutableStateMapOf<Int, HsnEntry>() }
    val categories = remember { mutableStateOf<List<String>>(emptyList()) }
    val hsnEntriesMap = remember { mutableStateMapOf<String, List<HsnEntry>>() }

    val countryName = remember(context) { com.minhas.teller.utils.DashboardPrefs.getTaxCountry(context) }
    val isIndia = remember(countryName) { countryName.equals("India", true) }
    val taxConfig = remember(countryName) { com.minhas.teller.utils.TaxConfigManager.getConfigForCountry(countryName) }
    val taxLabel = remember(taxConfig) { taxConfig.taxName }
    val taxEnabled = remember { com.minhas.teller.utils.DashboardPrefs.isTaxSystemEnabled(context, currentCompanyId) }
    val businessStateCode = remember(company) { GstStateCodes.codeForStateName(company?.state) }

    LaunchedEffect(Unit) {
        categories.value = com.minhas.teller.utils.HSNDataManager.getInstance(context).getCategories()
    }

    fun onResolvedHsnAndTax(i: Int, name: String, hsn: String, rate: Double) {
        coroutineScope.launch {
            try {
                val existingItem = allStockItems.find { it.name.equals(name, true) }
                if (existingItem != null) {
                    if (existingItem.hsnCode != hsn || existingItem.gstRate != rate) {
                        val updatedItem = existingItem.copy(
                            hsnCode = hsn,
                            gstRate = rate,
                            cgstRate = rate / 2.0,
                            sgstRate = rate / 2.0,
                            igstRate = rate
                        )
                        stockItemRepository.update(updatedItem)
                    }
                    if (i >= 0 && i < inventoryEntries.size) {
                        inventoryEntries[i] = inventoryEntries[i].copy(
                            stockItemId = existingItem.id,
                            hsnCode = hsn,
                            gstRate = rate,
                            cgstRate = rate / 2.0,
                            sgstRate = rate / 2.0,
                            igstRate = rate
                        )
                    }
                    val suggestedUnitName = com.minhas.teller.utils.ProductCategory.resolveUnit(
                        com.minhas.teller.utils.ProductCategory.defaultUnitForItem(name)
                    )
                    val matchedUnit = allUnits.firstOrNull {
                        it.name.equals(suggestedUnitName, ignoreCase = true) ||
                        com.minhas.teller.utils.ProductCategory.resolveUnit(it.name).equals(suggestedUnitName, ignoreCase = true)
                    }
                    val defaultUnit = matchedUnit?.id ?: allUnits.firstOrNull()?.id ?: 1
                    val newStockItem = com.minhas.teller.data.entities.StockItem(
                        name = name,
                        unitId = defaultUnit,
                        companyId = currentCompanyId ?: 0,
                        hsnCode = hsn,
                        gstRate = rate,
                        cgstRate = rate / 2.0,
                        sgstRate = rate / 2.0,
                        igstRate = rate,
                        hsnAutoDetected = true
                    )
                    val newId = stockItemRepository.insert(newStockItem).toInt()
                    // Immediately add to allStockItems so concurrent/sequential calls for the
                    // same name find the just-created item instead of inserting a duplicate.
                    stockItemViewModel.addToLocalList(newStockItem.copy(id = newId))
                    if (i >= 0 && i < inventoryEntries.size) {
                        inventoryEntries[i] = inventoryEntries[i].copy(
                            stockItemId = newId,
                            hsnCode = hsn,
                            gstRate = rate,
                            cgstRate = rate / 2.0,
                            sgstRate = rate / 2.0,
                            igstRate = rate
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AddEditVoucher", "Failed to resolve HSN/tax for '$name'", e)
            }
        }
    }

    // Reactive Tax & Totals Recalculation Effect
    LaunchedEffect(
        inventoryEntries.toList().map { Triple(it.stockItemId, it.quantity, it.rate) },
        inventoryEntries.map { it.gstRate },
        allLedgers,
        type,
        isIndia,
        taxLabel,
        placeOfSupply,
        supplyType,
        businessStateCode,
        simplePartyLedger?.stateCode
    ) {
        if (useSimpleUI && (type == "Sales" || type == "Purchase")) {
            val subtotal = inventoryEntries.sumOf { it.quantity * it.rate }
            var taxTotal = 0.0

            // Inter-state vs intra-state (IGST vs CGST/SGST) for India — mirrors GstReturnBuilder
            // so the books and GSTR-1/GSTR-3B always agree. Intra-state => CGST+SGST; inter-state
            // (or B2CL/EXPORT) => IGST.
            val partyState = simplePartyLedger?.stateCode
            val pos = when {
                GstStateCodes.isValidCode(placeOfSupply) -> placeOfSupply
                GstStateCodes.isValidCode(partyState) -> partyState!!
                else -> businessStateCode ?: "00"
            }
            val interState = supplyType.equals(SupplyType.B2CL, true) ||
                supplyType.equals(SupplyType.EXPORT, true) ||
                (businessStateCode != null && pos != businessStateCode)

            if (taxEnabled) {
                // ── Tax ON: reverse-calculate taxable base from tax-inclusive amount ──
                for (idx in inventoryEntries.indices) {
                    val entry = inventoryEntries[idx]
                    val gst = entry.gstRate
                    val totAmt = GstComplianceRules.round2(entry.quantity * entry.rate)
                    val taxableAmt = if (gst > 0.0) GstComplianceRules.round2(totAmt / (1.0 + gst / 100.0)) else totAmt
                    val taxAmt = GstComplianceRules.round2(totAmt - taxableAmt)

                    val cgst = if (isIndia && !interState) gst / 2.0 else 0.0
                    val sgst = if (isIndia && !interState) gst / 2.0 else 0.0
                    val igst = if (!isIndia || interState) gst else 0.0

                    inventoryEntries[idx] = entry.copy(
                        taxableAmount = taxableAmt.rupeesToPaise(),
                        taxAmount = taxAmt.rupeesToPaise(),
                        totalAmount = totAmt.rupeesToPaise(),
                        cgstRate = cgst,
                        sgstRate = sgst,
                        igstRate = igst
                    )
                    taxTotal += taxAmt
                }
            } else {
                // ── Tax OFF: zero out all tax fields, full amount is taxable ──
                for (idx in inventoryEntries.indices) {
                    val entry = inventoryEntries[idx]
                    val totAmt = entry.quantity * entry.rate
                    inventoryEntries[idx] = entry.copy(
                        taxableAmount = totAmt.rupeesToPaise(),
                        taxAmount = 0L,
                        totalAmount = totAmt.rupeesToPaise(),
                        gstRate = 0.0,
                        cgstRate = 0.0,
                        sgstRate = 0.0,
                        igstRate = 0.0
                    )
                }
            }

            if (type == "Sales") {
                val taxableBase = subtotal - taxTotal
                if (debitEntries.isNotEmpty()) {
                    debitEntries[0] = debitEntries[0].copy(amount = subtotal.rupeesToPaise())
                }
                if (creditEntries.isNotEmpty()) {
                    creditEntries[0] = creditEntries[0].copy(amount = taxableBase.rupeesToPaise())
                }

                // Remove any existing tax-ledger entries (Duties & Taxes group) from credits
                val nonTaxCredits = creditEntries.filterIndexed { index, entry ->
                    index == 0 || allLedgers.find { it.id == entry.ledgerId }?.group?.equals("Duties & Taxes", true) != true
                }
                creditEntries.clear()
                creditEntries.addAll(nonTaxCredits)

                if (taxEnabled && taxTotal > 0.0) {
                    if (isIndia && interState) {
                        val igstLedger = allLedgers.find { it.name.equals("IGST", true) }
                        if (igstLedger != null) {
                            creditEntries.add(VoucherEntry(voucherId = 0, ledgerId = igstLedger.id, amount = taxTotal.rupeesToPaise(), isDebit = false))
                        }
                    } else if (isIndia) {
                        val cgstLedger = allLedgers.find { it.name.equals("CGST", true) }
                        val sgstLedger = allLedgers.find { it.name.equals("SGST", true) }
                        val (cgstAmt, sgstAmt) = GstComplianceRules.splitTax(taxTotal)
                        if (cgstLedger != null) {
                            creditEntries.add(VoucherEntry(voucherId = 0, ledgerId = cgstLedger.id, amount = cgstAmt.rupeesToPaise(), isDebit = false))
                        }
                        if (sgstLedger != null) {
                            creditEntries.add(VoucherEntry(voucherId = 0, ledgerId = sgstLedger.id, amount = sgstAmt.rupeesToPaise(), isDebit = false))
                        }
                    } else {
                        val taxLedger = allLedgers.find { it.name.equals(taxLabel, true) || it.group.equals("Duties & Taxes", true) }
                        if (taxLedger != null) {
                            creditEntries.add(VoucherEntry(voucherId = 0, ledgerId = taxLedger.id, amount = taxTotal.rupeesToPaise(), isDebit = false))
                        }
                    }
                }
            } else if (type == "Purchase") {
                val taxableBase = subtotal - taxTotal
                if (debitEntries.isNotEmpty()) {
                    debitEntries[0] = debitEntries[0].copy(amount = taxableBase.rupeesToPaise())
                }
                if (creditEntries.isNotEmpty()) {
                    creditEntries[0] = creditEntries[0].copy(amount = subtotal.rupeesToPaise())
                }

                // Remove any existing tax-ledger entries (Duties & Taxes group) from debits
                val nonTaxDebits = debitEntries.filterIndexed { index, entry ->
                    index == 0 || allLedgers.find { it.id == entry.ledgerId }?.group?.equals("Duties & Taxes", true) != true
                }
                debitEntries.clear()
                debitEntries.addAll(nonTaxDebits)

                if (taxEnabled && taxTotal > 0.0) {
                    if (isIndia && interState) {
                        val igstLedger = allLedgers.find { it.name.equals("IGST", true) }
                        if (igstLedger != null) {
                            debitEntries.add(VoucherEntry(voucherId = 0, ledgerId = igstLedger.id, amount = taxTotal.rupeesToPaise(), isDebit = true))
                        }
                    } else if (isIndia) {
                        val cgstLedger = allLedgers.find { it.name.equals("CGST", true) }
                        val sgstLedger = allLedgers.find { it.name.equals("SGST", true) }
                        val (cgstAmt, sgstAmt) = GstComplianceRules.splitTax(taxTotal)
                        if (cgstLedger != null) {
                            debitEntries.add(VoucherEntry(voucherId = 0, ledgerId = cgstLedger.id, amount = cgstAmt.rupeesToPaise(), isDebit = true))
                        }
                        if (sgstLedger != null) {
                            debitEntries.add(VoucherEntry(voucherId = 0, ledgerId = sgstLedger.id, amount = sgstAmt.rupeesToPaise(), isDebit = true))
                        }
                    } else {
                        val taxLedger = allLedgers.find { it.name.equals(taxLabel, true) || it.group.equals("Duties & Taxes", true) }
                        if (taxLedger != null) {
                            debitEntries.add(VoucherEntry(voucherId = 0, ledgerId = taxLedger.id, amount = taxTotal.rupeesToPaise(), isDebit = true))
                        }
                    }
                }
            }
        }
    }

    val currentVoucherWithAllEntries by produceState<com.minhas.teller.data.entities.VoucherWithAllEntries?>(initialValue = null, voucherId) {
        if (voucherId != null && voucherId != -1) {
            voucherViewModel.getVoucherWithAllEntriesById(voucherId).collect { value = it }
        }
    }


    val calendar = Calendar.getInstance()
    calendar.time = date
    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, day: Int ->
            val selectedCalendar = Calendar.getInstance()
            selectedCalendar.set(year, month, day)
            date = selectedCalendar.time
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    LaunchedEffect(currentVoucherWithAllEntries) {
        currentVoucherWithAllEntries?.let { vw ->
            type = vw.voucher.type
            date = vw.voucher.date
            voucherNumber = vw.voucher.voucherNumber
            narration = vw.voucher.narration ?: ""
            customField = vw.voucher.customField ?: ""
            showCustomField = vw.voucher.customField?.isNotBlank() == true
            placeOfSupply = vw.voucher.placeOfSupply ?: ""
            supplyType = vw.voucher.supplyType ?: ""
            reverseCharge = vw.voucher.reverseCharge
            chargeInterest = vw.voucher.chargeInterest
            interestRate = if (vw.voucher.interestRate == 0.0) "" else vw.voucher.interestRate.toString()
            interestDays = if (vw.voucher.interestDays == 0) "" else vw.voucher.interestDays.toString()

            debitEntries.clear()
            creditEntries.clear()
            vw.voucherEntries.forEach { entry ->
                if (entry.isDebit) debitEntries.add(entry) else creditEntries.add(entry)
            }
            inventoryEntries.clear()
            inventoryEntries.addAll(vw.inventoryEntries)
            stockOutEntries.clear()
            if (vw.voucher.type == "Journal") {
                val given = inventoryEntries.filter { it.quantity < 0 }
                inventoryEntries.removeAll(given)
                stockOutEntries.addAll(given.map { it.copy(quantity = -it.quantity, amount = -it.amount) })
            }
        }
    }

    LaunchedEffect(voucherId) {
        if (voucherId == null || voucherId == -1) {
            if (debitEntries.isEmpty()) debitEntries.add(VoucherEntry(voucherId = 0, ledgerId = 0, amount = 0L, isDebit = true))
            if (creditEntries.isEmpty()) creditEntries.add(VoucherEntry(voucherId = 0, ledgerId = 0, amount = 0L, isDebit = false))
        }
    }

    // --- TUTORIAL EFFECT ---
    val allVouchersForTutorial by voucherViewModel.allVouchersWithAllEntries.collectAsState()
    
    if (isTutorialActive && currentStep == TutorialStep.VOUCHER_CREATION_DEMO) {
        InteractiveTutorialOverlay(
            currentStep = TutorialStep.VOUCHER_CREATION_DEMO,
            onNext = {},
            onSkip = { TutorialManager.skipTutorial(context) }
        )
    }

    LaunchedEffect(currentStep) {
        if (isTutorialActive && currentStep == TutorialStep.VOUCHER_CREATION_DEMO) {
            delay(1500)
            
            // Step 1: Pre-fill
            type = "Payment"
            narration = "Tutorial Demo Transaction"
            voucherNumber = "TUT-DEMO"
            
            delay(1500)
            
            // Step 2: Fill Entries
             if (debitEntries.isNotEmpty()) {
                 val cashLedger = allLedgers.find { it.group.contains("Cash", true) } ?: allLedgers.firstOrNull()
                 if (cashLedger != null) {
                     debitEntries[0] = debitEntries[0].copy(ledgerId = cashLedger.id, amount = 100.0.rupeesToPaise())
                 }
             }
             if (creditEntries.isNotEmpty()) {
                  val otherLedger = allLedgers.firstOrNull { it.id != debitEntries.firstOrNull()?.ledgerId }
                  if (otherLedger != null) {
                       creditEntries[0] = creditEntries[0].copy(ledgerId = otherLedger.id, amount = 100.0.rupeesToPaise())
                  }
             }
             
             Toast.makeText(context, TerminologyManager.getTerm("Auto-filled test data..."), Toast.LENGTH_SHORT).show()
             delay(2000)
             
             // Step 3: Save
            val newVoucher = com.minhas.teller.data.entities.Voucher(
                id = 0,
                type = type,
                date = date,
                voucherNumber = "TUT-DEMO",
                narration = narration,
                customField = null,
                chargeInterest = false,
                interestRate = 0.0,
                interestDays = 0,
                companyId = currentCompanyId ?: 0
            )
            voucherViewModel.insertVoucherWithAllEntries(newVoucher, debitEntries, inventoryEntries, billAllocations)
            Toast.makeText(context, TerminologyManager.getTerm("Saved! Now cleaning up..."), Toast.LENGTH_SHORT).show()
            delay(2500)
            
            // Step 4: Delete
            val demoVoucher = voucherViewModel.allVouchersWithAllEntries.value.find { it.voucher.voucherNumber == "TUT-DEMO" }
            if (demoVoucher != null) {
                voucherViewModel.deleteVoucherWithAllEntries(demoVoucher.voucher)
                Toast.makeText(context, TerminologyManager.getTerm("Deleted (Clean up complete)"), Toast.LENGTH_SHORT).show()
            } else {
                 val fallback = voucherViewModel.allVouchersWithAllEntries.value.find { it.voucher.narration == "Tutorial Demo Transaction" }
                 if (fallback != null) {
                     voucherViewModel.deleteVoucherWithAllEntries(fallback.voucher)
                 }
            }
            
            delay(1000)
            TutorialManager.advanceStep() // Go to REPORTS highlight
            navController.popBackStack()
        }
    }

    // Per-voucher-type soft pastel gradient — unique per screen, light watercolor style
    val rainbowColors = when (type) {
        "Sales"       -> listOf(Color(0xFFFFD6D6), Color(0xFFFFEBB5), Color(0xFFD6F5D6), Color(0xFFB5D9FF), Color(0xFFFFD6F0)) // Soft red-yellow-green-blue-pink
        "Purchase"    -> listOf(Color(0xFFB5EEE5), Color(0xFFB5E8F5), Color(0xFFD6F0FF), Color(0xFFEBF8FF), Color(0xFFD6EEF5)) // Soft teal-cyan-sky
        "Receipt"     -> listOf(Color(0xFFE8D6FF), Color(0xFFD0E8FF), Color(0xFFB5F0E0), Color(0xFFFFEBB5), Color(0xFFFFD6D6)) // Soft purple-blue-mint
        "Payment"     -> listOf(Color(0xFFFFD6E0), Color(0xFFFFD6C0), Color(0xFFFFEBB5), Color(0xFFFFD6E8), Color(0xFFFFB5D0)) // Soft pink-orange-gold
        "Journal"     -> listOf(Color(0xFFFFEBCC), Color(0xFFFFD6D6), Color(0xFFFFD6F0), Color(0xFFE8D6FF), Color(0xFFD0E8FF)) // Soft gold-pink-purple-blue
        "Quotation"   -> listOf(Color(0xFFD6F5E0), Color(0xFFD6EED6), Color(0xFFEEF5D6), Color(0xFFD6F5E0), Color(0xFFB5EED6)) // Soft greens
        else          -> listOf(Color(0xFFFFEBCC), Color(0xFFFFD6D6), Color(0xFFFFD6F0), Color(0xFFE8D6FF), Color(0xFFD0E8FF))
    }
    val rainbowBrush = Brush.horizontalGradient(rainbowColors)

    // Money In / Money Out get the same light pastel-to-white look as every other voucher type now
    // (previously a dark navy + gold "premium" look — moved to a light background, see rainbowCardBrush below
    // for the mid-section rainbow-tint that replaces the old brown/gold wood-tone card).
    val isMoneyFlow = useSimpleUI && (type == "Receipt" || type == "Payment")

    // Whole screen fading background: starts with horizontal gradient at the top, fades vertically to white at the bottom
    val backgroundModifier = Modifier
        .background(Color.White)
        .background(rainbowBrush)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.2f),
                    Color.White.copy(alpha = 0.7f),
                    Color.White
                )
            )
        )

    // Rainbow multicolor tint used for the Money In / Money Out mid-section card
    // (replaces the old solid brown/gold "wood tone" card background).
    val rainbowCardBrush = Brush.horizontalGradient(rainbowColors.map { it.copy(alpha = 0.55f) })

    // Dark rainbow background for Money In / Money Out mid‑section
    val darkRainbowBrush = Brush.horizontalGradient(
        rainbowColors.map {
            Color(it.red * 0.3f, it.green * 0.3f, it.blue * 0.3f, it.alpha)
        }
    )

    val topThemeColors = when (type) {
        "Sales" -> TopThemeColors(
            inOutOutline = listOf(Color(0xFF913D3D), Color(0xFF9A4848)),
            dateBorder = Color(0xFF913D3D).copy(alpha = 0.55f),
            dateTint = Color(0xFF803333),
            dateText = Color(0xFF6B2626),
            voucherBorder = Color(0xFF9A4848).copy(alpha = 0.55f),
            voucherTint = Color(0xFF661E1E),
            voucherText = Color(0xFF401414)
        )
        "Purchase" -> TopThemeColors(
            inOutOutline = listOf(Color(0xFFF59E0B), Color(0xFFFCD34D)),
            dateBorder = Color(0xFF0EA5E9).copy(alpha = 0.55f),
            dateTint = Color(0xFF0284C7),
            dateText = Color(0xFF01579B),
            voucherBorder = Color(0xFFAB47BC).copy(alpha = 0.55f),
            voucherTint = Color(0xFF7B1FA2),
            voucherText = Color(0xFF4A148C)
        )
        "Receipt" -> TopThemeColors(
            inOutOutline = listOf(Color(0xFF10B981), Color(0xFF34D399)),
            dateBorder = Color(0xFF10B981).copy(alpha = 0.55f),
            dateTint = Color(0xFF059669),
            dateText = Color(0xFF047857),
            voucherBorder = Color(0xFF0D9488).copy(alpha = 0.55f),
            voucherTint = Color(0xFF0D9488),
            voucherText = Color(0xFF115E59)
        )
        "Payment" -> TopThemeColors(
            inOutOutline = listOf(Color(0xFFEF4444), Color(0xFFF87171)),
            dateBorder = Color(0xFFEF4444).copy(alpha = 0.55f),
            dateTint = Color(0xFFDC2626),
            dateText = Color(0xFFB91C1C),
            voucherBorder = Color(0xFFF59E0B).copy(alpha = 0.55f),
            voucherTint = Color(0xFFD97706),
            voucherText = Color(0xFFB45309)
        )
        "Journal" -> TopThemeColors(
            inOutOutline = listOf(Color(0xFF6366F1), Color(0xFF818CF8)),
            dateBorder = Color(0xFF6366F1).copy(alpha = 0.55f),
            dateTint = Color(0xFF4F46E5),
            dateText = Color(0xFF3730A3),
            voucherBorder = Color(0xFF7C3AED).copy(alpha = 0.55f),
            voucherTint = Color(0xFF7C3AED),
            voucherText = Color(0xFF5B21B6)
        )
        "Quotation" -> TopThemeColors(
            inOutOutline = listOf(Color(0xFF10B981), Color(0xFF6EE7B7)),
            dateBorder = Color(0xFF10B981).copy(alpha = 0.55f),
            dateTint = Color(0xFF059669),
            dateText = Color(0xFF047857),
            voucherBorder = Color(0xFF06B6D4).copy(alpha = 0.55f),
            voucherTint = Color(0xFF0891B2),
            voucherText = Color(0xFF0E7490)
        )
        else -> TopThemeColors(
            inOutOutline = listOf(Color(0xFF6366F1), Color(0xFF818CF8)),
            dateBorder = Color(0xFF6366F1).copy(alpha = 0.55f),
            dateTint = Color(0xFF4F46E5),
            dateText = Color(0xFF3730A3),
            voucherBorder = Color(0xFF7C3AED).copy(alpha = 0.55f),
            voucherTint = Color(0xFF7C3AED),
            voucherText = Color(0xFF5B21B6)
        )
    }

    Box(modifier = Modifier.fillMaxSize().imePadding().then(backgroundModifier)) { // Added imePadding for keyboard
        Scaffold(
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {                // ── Colourful top section: gradient covers header + summary cards ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column {
                        // Header row (on top of the gradient)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val headerIconTint = Color(0xFF32194D)
                            IconButton(
                                onClick = { requestNavigateBack() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = TerminologyManager.getTerm("Cancel"),
                                    modifier = Modifier.size(28.dp),
                                    tint = headerIconTint
                                )
                            }
                            val headerTitle = when {
                                useSimpleUI && type == "Sales" -> TerminologyManager.getTerm("NEW SALE")
                                useSimpleUI && type == "Purchase" -> TerminologyManager.getTerm("NEW PURCHASE")
                                useSimpleUI && type == "Receipt" -> TerminologyManager.getTerm("MONEY IN")
                                useSimpleUI && type == "Payment" -> TerminologyManager.getTerm("MONEY OUT")
                                type == "Journal" -> TerminologyManager.getTerm("ADJUSTMENTS")
                                else -> TerminologyManager.getTerm(type).uppercase()
                            }
                            var fontSize by remember { mutableStateOf(24.sp) }
                             LaunchedEffect(headerTitle) {
                                 fontSize = 24.sp
                             }
                             Text(
                                 text = headerTitle,
                                 style = MaterialTheme.typography.headlineSmall.copy(
                                     fontWeight = FontWeight.ExtraBold,
                                     fontSize = fontSize
                                 ),
                                 modifier = Modifier.weight(1f).padding(start = 4.dp),
                                 color = headerIconTint,
                                 maxLines = 1,
                                 overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                                 onTextLayout = { textLayoutResult ->
                                     if (textLayoutResult.hasVisualOverflow && fontSize.value > 10f) {
                                         fontSize = (fontSize.value * 0.9f).sp
                                     }
                                 }
                             )
                            // Mic icon on all screens
                            IconButton(onClick = { /* voice input future */ }) {
                                Icon(Icons.Filled.Mic, contentDescription = TerminologyManager.getTerm("Voice"), tint = headerIconTint)
                            }
                            // OCR Scan Button (Google Doc Scanner)
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
                                         .addOnFailureListener { e ->
                                             Toast.makeText(context, "${TerminologyManager.getTerm("Scanner Error")}: ${e.message}", Toast.LENGTH_SHORT).show()
                                         }
                                 } else {
                                     Toast.makeText(context, TerminologyManager.getTerm("Context is not an Activity"), Toast.LENGTH_SHORT).show()
                                 }
                            }) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = TerminologyManager.getTerm("Scan Bill"), tint = headerIconTint)
                            }

                             if (voucherId != -1 && voucherId != null && (type == "Sales" || type == "Purchase" || type == "Quotation") && currentVoucherWithAllEntries != null) {
                                var showExportMenu by remember { mutableStateOf(false) }
                                IconButton(onClick = { showExportMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = TerminologyManager.getTerm("Export"), tint = headerIconTint)
                                }
                                DropdownMenu(
                                    expanded = showExportMenu,
                                    onDismissRequest = { showExportMenu = false },
                                    modifier = Modifier.background(Color(0xFF2C2C2C))
                                ) {
                                    DropdownMenuItem(text = { Text(TerminologyManager.getTerm("Export to PDF"), color = Color.White) }, onClick = {
                                        showExportMenu = false
                                        currentVoucherWithAllEntries?.let { vw ->
                                            val customerLedger = if (type == "Sales") vw.voucherEntries.firstOrNull { !it.isDebit }?.let { entry -> allLedgers.find { it.id == entry.ledgerId } }
                                            else vw.voucherEntries.firstOrNull { it.isDebit }?.let { entry -> allLedgers.find { it.id == entry.ledgerId } }
                                            ExportUtils.exportInvoiceToPdf(context, vw.voucher, customerLedger, vw.inventoryEntries, allStockItems, allUnits, company)
                                        }
                                    })
                                    DropdownMenuItem(text = { Text(TerminologyManager.getTerm("Export to Excel"), color = Color.White) }, onClick = {
                                        showExportMenu = false
                                        currentVoucherWithAllEntries?.let { vw ->
                                            val customerLedger = if (type == "Sales") vw.voucherEntries.firstOrNull { !it.isDebit }?.let { entry -> allLedgers.find { it.id == entry.ledgerId } }
                                            else vw.voucherEntries.firstOrNull { it.isDebit }?.let { entry -> allLedgers.find { it.id == entry.ledgerId } }
                                            ExportUtils.exportInvoiceToExcel(context, vw.voucher, customerLedger, vw.inventoryEntries, allStockItems, allUnits, company)
                                        }
                                    })
                                    DropdownMenuItem(text = { Text(TerminologyManager.getTerm("Share on WhatsApp"), color = Color.White) }, onClick = {
                                        showExportMenu = false
                                        currentVoucherWithAllEntries?.let { vw ->
                                            val customerLedger = if (type == "Sales") vw.voucherEntries.firstOrNull { !it.isDebit }?.let { entry -> allLedgers.find { it.id == entry.ledgerId } }
                                            else vw.voucherEntries.firstOrNull { it.isDebit }?.let { entry -> allLedgers.find { it.id == entry.ledgerId } }
                                            ExportUtils.shareInvoiceOnWhatsApp(context, vw.voucher, customerLedger, vw.inventoryEntries, allStockItems, allUnits, company)
                                        }
                                    })
                                }
                            } else {
                                IconButton(onClick = { requestNavigateBack() }) {
                                     Icon(Icons.Filled.Home, contentDescription = TerminologyManager.getTerm("Home"), tint = headerIconTint)
                                }
                            }
                        } // end header Row

                        // TOTAL IN / TOTAL OUT cards sit inside the gradient box
                        val totalDr = debitEntries.sumOf { it.amount }
                        val totalCr = creditEntries.sumOf { it.amount }
                        val totalInSum = totalDr.paiseToRupees()
                        val totalOutSum = totalCr.paiseToRupees()

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .background(Color(0xFFE0E5EC), shape = RoundedCornerShape(32.dp))
                                .border(
                                    width = 2.dp,
                                    brush = Brush.horizontalGradient(colors = topThemeColors.inOutOutline),
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
                                        .neumorphicShadow(RoundedCornerShape(24.dp))
                                        .background(Color(0xFFE0E5EC), RoundedCornerShape(24.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Green Arrow Button
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .neumorphicShadow(CircleShape, darkShadowColor = Color(0xFF4CAF50).copy(alpha = 0.5f), blur = 4.dp, offset = 3.dp)
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
                                        .neumorphicShadow(RoundedCornerShape(24.dp))
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
                                                .neumorphicShadow(CircleShape, darkShadowColor = Color(0xFFF44336).copy(alpha = 0.5f), blur = 4.dp, offset = 3.dp)
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
                    } // end Column
                } // end gradient Box

                // Scrollable Form Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Take remaining space
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Compact Row 1: Date & Voucher No (Combined for ALL types now)
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
                                .floatingDropShadow(borderRadius = 25.dp)
                                .clip(RoundedCornerShape(25.dp))
                                .background(Color.White.copy(alpha = 0.90f))
                                .border(1.5.dp, topThemeColors.dateBorder, RoundedCornerShape(25.dp))
                                .clickable { datePickerDialog.show() }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = TerminologyManager.getTerm("Calendar"),
                                    tint = topThemeColors.dateTint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(TerminologyManager.getTerm("Date:"), fontSize = 12.sp, color = Color(0xFF607D8B), fontWeight = FontWeight.Medium, maxLines = 1)
                                    AutoShrinkText(
                                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date),
                                        color = topThemeColors.dateText,
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
                                .floatingDropShadow(borderRadius = 25.dp)
                                .clip(RoundedCornerShape(25.dp))
                                .background(Color.White.copy(alpha = 0.90f))
                                .border(1.5.dp, topThemeColors.voucherBorder, RoundedCornerShape(27.dp))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = TerminologyManager.getTerm("Doc Icon"),
                                    tint = topThemeColors.voucherTint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(TerminologyManager.getTerm("Voucher:"), fontSize = 12.sp, color = Color(0xFF607D8B), fontWeight = FontWeight.Medium, maxLines = 1)
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = voucherNumber,
                                        onValueChange = { voucherNumber = it; isVoucherNumberValid = true },
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = topThemeColors.voucherText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        singleLine = true,
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(topThemeColors.voucherTint),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (type != "Journal") {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.6f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color(0xFF32194D).copy(alpha = 0.15f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                        .clickable { chargeInterest = !chargeInterest }
                                ) {
                                    Text(
                                        TerminologyManager.getTerm("Charge interest?"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF32194D),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = chargeInterest,
                                        onCheckedChange = { chargeInterest = it },
                                        modifier = Modifier.scale(0.7f)
                                    )
                                }

                                if (chargeInterest) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ValidatedNumberTextField(
                                            value = interestRate,
                                            onValueChange = { interestRate = it },
                                            label = TerminologyManager.getTerm("Rate (% per annum)"),
                                            modifier = Modifier.weight(1f),
                                            isDecimal = true,
                                            isLightTheme = true
                                        )
                                        ValidatedNumberTextField(
                                            value = interestDays,
                                            onValueChange = { interestDays = it },
                                            label = TerminologyManager.getTerm("Interest-free days"),
                                            modifier = Modifier.weight(1f),
                                            isDecimal = false,
                                            isLightTheme = true
                                        )
                                    }
                                    val days = interestDays.toIntOrNull() ?: 0
                                    Text(
                                        if (days <= 0) TerminologyManager.getTerm("Interest starts from day 1.") else "${TerminologyManager.getTerm("Interest starts after")} $days ${TerminologyManager.getTerm("days.")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF32194D).copy(alpha = 0.7f),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (useSimpleUI) {
                        // Safety Check: Ensure entries are initialized before rendering
                        val isReady = debitEntries.isNotEmpty() && creditEntries.isNotEmpty() &&
                                      (!((type == "Sales" || type == "Purchase") && inventoryEntries.isEmpty()))
                                      
                        if (!isReady) {
                             Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                 CircularProgressIndicator()
                             }
                        } else {
                            // --- SIMPLE MODE UI ---
                            // --- SIMPLE MODE UI ---
                            // Same hue family as each screen's own top gradient (rainbowColors above),
                            // just deepened for white-text contrast — keeps the mid-section
                            // visually tied to the pastel header instead of clashing with it.
                            val sectionCardBg = when (type) {
                                "Sales" -> Color(0xFF9C1750)     // Deep Raspberry (matches red/pink gradient)
                                "Purchase" -> Color(0xFF0E5E60)  // Deep Teal (matches teal/cyan gradient)
                                "Receipt" -> Color(0xFF3D2472)   // Deep Purple-Indigo (matches purple/blue/mint gradient, Money In)
                                "Payment" -> Color(0xFF9E3D22)   // Deep Coral/Rust (matches pink/orange gradient, Money Out)
                                else -> Color(0xFF32194D).copy(alpha = 0.08f)
                            }
                            val sectionCardTextColor = if (type == "Sales" || type == "Purchase" || type == "Receipt" || type == "Payment") {
                                Color.White
                            } else {
                                Color(0xFF32194D)
                            }
                            val sectionCardSubtextColor = if (type == "Sales" || type == "Purchase" || type == "Receipt" || type == "Payment") {
                                Color.White.copy(alpha = 0.7f)
                            } else {
                                Color(0xFF32194D).copy(alpha = 0.7f)
                            }

                            when (type) {
                                "Sales" -> {
                                    val itemSectionTitle = if (isSimpleLanguage) TerminologyManager.getTerm("WHAT IS THE ITEM?") else TerminologyManager.getTerm("INVENTORY ITEMS")

                                    val selectedDebitLedger = allLedgers.find { it.id == debitEntries[0].ledgerId }
                                    val debitIsBillWise = selectedDebitLedger?.maintainBillWise == true
                                    val debitAllocations = billAllocations.filter { it.voucherEntryId == debitEntries[0].ledgerId }

                                    LaunchedEffect(debitAllocations, debitIsBillWise) {
                                        if (debitIsBillWise) {
                                            val total = debitAllocations.sumOf { it.amount }
                                            if (kotlin.math.abs(total - debitEntries[0].amount) > 0.001) {
                                                debitEntries[0] = debitEntries[0].copy(amount = total)
                                                if (creditEntries.size == 1) creditEntries[0] = creditEntries[0].copy(amount = total)
                                            }
                                            if (debitAllocations.isEmpty() && debitEntries[0].ledgerId != 0) {
                                                // Items are entered before payment type now, so the invoice total is
                                                // already known here - pre-fill the auto-created Advance/New Ref
                                                // allocation with it instead of leaving it at 0 for the user to retype.
                                                val currentInvoiceTotal = inventoryEntries.sumOf { it.quantity * it.rate }
                                                billAllocations.add(
                                                    BillAllocation(
                                                        voucherEntryId = debitEntries[0].ledgerId,
                                                        refType = "New Ref",
                                                        refName = customField.ifBlank { voucherNumber },
                                                        amount = currentInvoiceTotal.rupeesToPaise()
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    fun selectPaymentType(mode: String) {
                                         simplePaymentMode = mode
                                         showCustomField = mode == "Finance" || mode == "Cash" || mode == "Bank"
                                         if (mode == "Cash") {
                                             val cashLedger = allLedgers.find { it.group.contains("Cash", ignoreCase = true) }
                                             if (cashLedger != null) {
                                                 debitEntries[0] = debitEntries[0].copy(ledgerId = cashLedger.id)
                                                 return
                                             }
                                         }
                                         
                                         // Filter ledgers depending on clicked payment option
                                         ledgerFilter = when (mode) {
                                             "Bank" -> { ledger ->
                                                 ledger.group.contains("Bank Accounts", ignoreCase = true) ||
                                                 ledger.group.contains("Bank OD A/c", ignoreCase = true) ||
                                                 ledger.group.contains("Bank", ignoreCase = true)
                                             }
                                             "Finance" -> { ledger ->
                                                 ledger.group.contains("Finance", ignoreCase = true) ||
                                                 ledger.name.contains("Finance", ignoreCase = true)
                                             }
                                             "Credit" -> { ledger ->
                                                 val gp = ledger.group.lowercase()
                                                 (gp.contains("debtor") || gp.contains("creditor") || gp.contains("supplier") || gp.contains("customer")) &&
                                                 !gp.contains("gst") && !gp.contains("sale") && !gp.contains("tax")
                                             }
                                             else -> null
                                         }
                                         
                                         // Bank Account / On Credit / Finance Sale (and Cash with no existing ledger) - open the picker
                                         currentLedgerEntryIndex = 0
                                         isDebitEntrySelection = true
                                         showLedgerSelectionDialog = true
                                     }

                                    // 5. Item Section (now first, so the invoice total is known before
                                    // payment type is chosen - needed to auto-fill the Advance amount below).
                                    VoucherSectionCard(accent = topThemeColors.voucherTint) {
                                        ModernSectionLabel(
                                            text = itemSectionTitle,
                                            onHelpClick = {
                                                showHelpDialog(
                                                    title = TerminologyManager.getTerm("Item Selection"),
                                                    message = TerminologyManager.getTerm("Select the product or service you sold.\n\nYou can also enter the Quantity and Rate manually.")
                                                )
                                            },
                                            textColor = Color(0xFF32194D),
                                            helpIconTint = Color(0xFF32194D).copy(alpha = 0.5f),
                                            fontSize = 19.sp
                                        )
                                        inventoryEntries.forEachIndexed { i, entry ->
                                            val item = allStockItems.find { it.id == entry.stockItemId }
                                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                ModernInventoryRow(
                                                    itemName = item?.name ?: "",
                                                    onItemClick = { currentInventoryEntryIndex = i; showStockItemSelectionDialog = true },
                                                    qty = if (entry.quantity == 0.0) "" else formatQty(entry.quantity),
                                                    onQtyChange = { qtyStr ->
                                                        val qty = qtyStr.toDoubleOrNull() ?: 0.0
                                                        inventoryEntries[i] = entry.copy(quantity = qty, amount = (qty * entry.rate).rupeesToPaise())
                                                    },
                                                    rate = if (entry.rate == 0.0) "" else entry.rate.toString(),
                                                    onRateChange = { rateStr ->
                                                        val rate = rateStr.toDoubleOrNull() ?: 0.0
                                                        inventoryEntries[i] = entry.copy(rate = rate, amount = (entry.quantity * rate).rupeesToPaise())
                                                    },
                                                    amount = entry.quantity * entry.rate,
                                                    onDeleteClick = {
                                                        if (inventoryEntries.size > 1) {
                                                            inventoryEntries.removeAt(i)
                                                        }
                                                    },
                                                    showRate = false,
                                                    onAmountChange = { amtStr ->
                                                        val amt = amtStr.toDoubleOrNull() ?: 0.0
                                                        val q = if (entry.quantity <= 0.0) 1.0 else entry.quantity
                                                        inventoryEntries[i] = entry.copy(quantity = q, rate = amt / q, amount = amt.rupeesToPaise())
                                                    },
                                                    showDeleteIcon = inventoryEntries.size > 1
                                                )

                                                if (taxEnabled && entry.hsnCode != null) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    AutoShrinkText(
                                                        text = "${taxConfig.codeSystemName ?: "Code"}: ${entry.hsnCode}  |  ${taxLabel}: ${entry.gstRate}%  |  Tax: ₹${String.format("%.2f", entry.taxAmount.paiseToRupees())}",
                                                        color = Color(0xFF32194D).copy(alpha = 0.7f),
                                                        maxFontSize = 11.sp,
                                                        minFontSize = 8.sp,
                                                        modifier = Modifier.padding(start = 4.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Add Item Button
                                            TextButton(
                                                onClick = { inventoryEntries.add(InventoryEntry(voucherId = 0, stockItemId = 0, quantity = 1.0, rate = 0.0, amount = 0L)) },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Text(TerminologyManager.getTerm("+ ADD ANOTHER ITEM"), color = Color(0xFF32194D))
                                            }
                                            TextButton(
                                                onClick = { navController.navigate(com.minhas.teller.navigation.Screen.BarcodeScanner.route) },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF32194D), modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(TerminologyManager.getTerm("SCAN BARCODE"), color = Color(0xFF32194D))
                                            }
                                        }
                                    }

                                    VoucherSectionCard(accent = topThemeColors.voucherTint) {
                                        ModernSectionLabel(
                                            text = TerminologyManager.getTerm("SELECT THE PAYMENT TYPE"),
                                            onHelpClick = {
                                                showHelpDialog(
                                                    title = TerminologyManager.getTerm("About Payment"),
                                                    message = TerminologyManager.getTerm("Tap Cash, Bank Account, On Credit, or Finance Sale.\n\n'On Credit' means the customer will pay you later. 'Finance Sale' is for sales financed through a finance company.")
                                                )
                                            },
                                            textColor = Color(0xFF32194D),
                                            helpIconTint = Color(0xFF32194D).copy(alpha = 0.5f)
                                        )
                                        val paymentItems = remember {
                                            listOf(
                                                PaymentItem(TerminologyManager.getTerm("Cash"), Icons.Default.Payments, GoldGradient, "Cash"),
                                                PaymentItem(TerminologyManager.getTerm("Bank Account"), Icons.Default.AccountBalance, SilverGradient, "Bank"),
                                                PaymentItem(TerminologyManager.getTerm("On Credit"), Icons.Default.CreditCard, BronzeGradient, "Credit"),
                                                PaymentItem(TerminologyManager.getTerm("Finance Sale"), Icons.Default.Wallet, TitaniumGradient, "Finance")
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            paymentItems.forEach { item ->
                                                Skeuomorphic3DButton(
                                                    item = item,
                                                    isSelected = simplePaymentMode == item.mode,
                                                    themeColors = topThemeColors,
                                                    modifier = Modifier.weight(1f),
                                                    onClick = { selectPaymentType(item.mode) }
                                                )
                                            }
                                        }
                                    }

                                    val showCustomerNameField = simplePaymentMode == "Finance" || simplePaymentMode == "Cash" || simplePaymentMode == "Bank"
                                    if (selectedDebitLedger != null || showCustomerNameField || debitIsBillWise) {
                                        VoucherSectionCard(accent = topThemeColors.voucherTint) {
                                            if (selectedDebitLedger != null) {
                                                val ledgerIcon = when {
                                                    selectedDebitLedger!!.group.contains("Bank", ignoreCase = true) -> Icons.Default.AccountBalance
                                                    selectedDebitLedger!!.group.contains("Cash", ignoreCase = true) -> Icons.Default.Payments
                                                    selectedDebitLedger!!.group.contains("Finance", ignoreCase = true) -> Icons.Default.Wallet
                                                    else -> Icons.Default.Person
                                                }
                                                val ledgerLabel = when {
                                                    simplePaymentMode == "Finance" -> TerminologyManager.getTerm("Finance Company")
                                                    simplePaymentMode == "Credit" || simplePaymentMode == "Pay Later" -> TerminologyManager.getTerm("Customer")
                                                    simplePaymentMode == "Bank" -> TerminologyManager.getTerm("Bank Account")
                                                    simplePaymentMode == "Cash" -> TerminologyManager.getTerm("Cash Account")
                                                    else -> TerminologyManager.getTerm("Selected")
                                                }
                                                // Plain tinted row rather than a Card - it already sits inside the
                                                // section panel, and a nested card reads as a double border.
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 12.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(topThemeColors.voucherTint.copy(alpha = 0.10f))
                                                        .border(
                                                            1.dp,
                                                            topThemeColors.voucherTint.copy(alpha = 0.35f),
                                                            RoundedCornerShape(14.dp)
                                                        )
                                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(42.dp)
                                                            .clip(CircleShape)
                                                            .background(topThemeColors.voucherTint.copy(alpha = 0.18f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = ledgerIcon,
                                                            contentDescription = null,
                                                            tint = topThemeColors.voucherTint,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            ledgerLabel,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = topThemeColors.voucherTint.copy(alpha = 0.7f),
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Text(
                                                            selectedDebitLedger!!.name,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = topThemeColors.voucherText,
                                                            fontSize = 16.sp
                                                        )
                                                    }
                                                }
                                            }

                                            if (showCustomerNameField) {
                                                ModernTextField(
                                                    value = customField,
                                                    onValueChange = { customField = it },
                                                    placeholder = if (simplePaymentMode == "Finance") TerminologyManager.getTerm("Write Customer Name (Required)") else TerminologyManager.getTerm("Write Customer Name (Optional)"),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    isLightTheme = true
                                                )
                                            }

                                            if (type == "Sales") {
                                                var posExpanded by remember { mutableStateOf(false) }
                                                var stExpanded by remember { mutableStateOf(false) }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    ModernDropdownField(
                                                        text = GstStateCodes.nameForCode(placeOfSupply) ?: "",
                                                        onClick = { posExpanded = true },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        placeholder = "Place of supply (state)"
                                                    )
                                                    DropdownMenu(
                                                        expanded = posExpanded,
                                                        onDismissRequest = { posExpanded = false },
                                                        modifier = Modifier.fillMaxWidth(0.9f)
                                                    ) {
                                                        GstStateCodes.stateNameByCode.forEach { (code, name) ->
                                                            DropdownMenuItem(
                                                                text = { Text(name) },
                                                                onClick = { placeOfSupply = code; posExpanded = false }
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    ModernDropdownField(
                                                        text = if (supplyType.isBlank()) "Auto-detect supply type" else supplyType,
                                                        onClick = { stExpanded = true },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        placeholder = "Supply type (GST)"
                                                    )
                                                    DropdownMenu(
                                                        expanded = stExpanded,
                                                        onDismissRequest = { stExpanded = false },
                                                        modifier = Modifier.fillMaxWidth(0.9f)
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text(TerminologyManager.getTerm("Auto-detect")) },
                                                            onClick = { supplyType = ""; stExpanded = false }
                                                        )
                                                        listOf("B2B", "B2CL", "B2CS", "EXPORT", "NIL", "EXEMPT", "NONGST").forEach { st ->
                                                            DropdownMenuItem(
                                                                text = { Text(st) },
                                                                onClick = { supplyType = st; stExpanded = false }
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Bill Allocations (On Credit / Finance Sale against a bill-wise ledger).
                                            // The first allocation is auto-created as "Advance" with the invoice total
                                            // pre-filled (see the LaunchedEffect above) since items are already entered
                                            // by the time payment type is picked now.
                                            if (debitIsBillWise) {
                                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                                    debitAllocations.forEachIndexed { index, allocation ->
                                                        BillAllocationRow(
                                                            allocation = allocation,
                                                            onChange = { updated ->
                                                                val idxVal = billAllocations.indexOf(allocation)
                                                                if (idxVal != -1) billAllocations[idxVal] = updated
                                                            },
                                                            onDelete = { billAllocations.remove(allocation) },
                                                            onPickRef = {
                                                                ledgerFilter = null
                                                                currentLedgerEntryIndex = 0
                                                                isDebitEntrySelection = true
                                                                voucherViewModel.getOutstandingVouchers(debitEntries[0].ledgerId)
                                                                showOutstandingDialog = true
                                                            },
                                                            onAddClick = if (index == debitAllocations.lastIndex) {
                                                                { billAllocations.add(BillAllocation(voucherEntryId = debitEntries[0].ledgerId, refType = "New Ref", refName = "", amount = 0L)) }
                                                            } else null
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 4. TOTAL AMOUNT / TAX BREAKDOWN CARD (Moved Down)
                                    val subtotal = inventoryEntries.sumOf { it.quantity * it.rate }
                                    val taxTotal = inventoryEntries.sumOf { it.taxAmount }.paiseToRupees()
                                    val taxableBase = subtotal - taxTotal

                                    VoucherSectionCard(accent = topThemeColors.voucherTint) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                                            if (taxEnabled) {
                                                // Tax ON: show full breakdown
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(TerminologyManager.getTerm("Base Amount"), color = Color(0xFF32194D).copy(alpha = 0.6f), fontSize = 13.sp)
                                                    AutoShrinkText("₹${String.format("%.2f", taxableBase)}", color = Color(0xFF32194D), maxFontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }

                                                if (taxTotal > 0.0) {
                                                    if (isIndia) {
                                                        val (cgstAmt, sgstAmt) = GstComplianceRules.splitTax(taxTotal)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text(TerminologyManager.getTerm("CGST"), color = Color(0xFF32194D).copy(alpha = 0.6f), fontSize = 13.sp)
                                                            AutoShrinkText("₹${String.format("%.2f", cgstAmt)}", color = Color(0xFF32194D), maxFontSize = 13.sp)
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text(TerminologyManager.getTerm("SGST"), color = Color(0xFF32194D).copy(alpha = 0.6f), fontSize = 13.sp)
                                                            AutoShrinkText("₹${String.format("%.2f", sgstAmt)}", color = Color(0xFF32194D), maxFontSize = 13.sp)
                                                        }
                                                    } else {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text(taxLabel, color = Color(0xFF32194D).copy(alpha = 0.6f), fontSize = 13.sp)
                                                            AutoShrinkText("₹${String.format("%.2f", taxTotal)}", color = Color(0xFF32194D), maxFontSize = 13.sp)
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                                HorizontalDivider(color = Color(0xFF32194D).copy(alpha = 0.15f), thickness = 1.dp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                            } else {
                                                // Tax OFF: simple total only
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(TerminologyManager.getTerm("Subtotal"), color = Color(0xFF32194D).copy(alpha = 0.6f), fontSize = 13.sp)
                                                    AutoShrinkText("₹${String.format("%.2f", subtotal)}", color = Color(0xFF32194D), maxFontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                HorizontalDivider(color = Color(0xFF32194D).copy(alpha = 0.15f), thickness = 1.dp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(TerminologyManager.getTerm("GRAND TOTAL"), color = Color(0xFF32194D).copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                AutoShrinkText("₹${String.format("%.2f", subtotal)}", color = Color(0xFF32194D), maxFontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
                                    }
                                    
                                    // Initial Setup: Ensure at least one entry for each
                                    LaunchedEffect(allLedgers) {
                                        if (debitEntries.isEmpty()) debitEntries.add(VoucherEntry(voucherId = 0, ledgerId = 0, amount = 0L, isDebit = true))
                                        if (creditEntries.isEmpty()) creditEntries.add(VoucherEntry(voucherId = 0, ledgerId = 0, amount = 0L, isDebit = false))

                                        if (creditEntries.isNotEmpty() && creditEntries[0].ledgerId == 0) {
                                            val salesLedger = allLedgers.find { it.name.contains("Sales", ignoreCase = true) }
                                                ?: allLedgers.find { it.group.contains("Sales", ignoreCase = true) || it.group.contains("Income", ignoreCase = true) }
                                            salesLedger?.let { creditEntries[0] = creditEntries[0].copy(ledgerId = it.id) }
                                        }
                                    }
                                }

                                "Purchase" -> {
                                LaunchedEffect(Unit) {
                                    while (debitEntries.size > 1) debitEntries.removeAt(debitEntries.lastIndex)
                                    while (creditEntries.size > 1) creditEntries.removeAt(creditEntries.lastIndex)
                                    if (debitEntries.isEmpty()) debitEntries.add(VoucherEntry(voucherId = 0, ledgerId = 0, amount = 0L, isDebit = true))
                                    if (creditEntries.isEmpty()) creditEntries.add(VoucherEntry(voucherId = 0, ledgerId = 0, amount = 0L, isDebit = false))
                                }
                                // 1. WHAT DID YOU BUY? (Product)
                                val q1 = if (isSimpleLanguage) TerminologyManager.getTerm("1. WHAT DID YOU BUY?") else TerminologyManager.getTerm("1. PURCHASE ITEMS (DEBIT)")
                                val q2 = if (isSimpleLanguage) TerminologyManager.getTerm("2. WHO DID YOU BUY IT FROM?") else TerminologyManager.getTerm("2. SUPPLIER / PARTY (CREDIT)")
                                val productPlaceholder = if (isSimpleLanguage) TerminologyManager.getTerm("Select Product") else TerminologyManager.getTerm("Select Item")
                                val partyPlaceholder = if (isSimpleLanguage) TerminologyManager.getTerm("SELECT CONTACT") else TerminologyManager.getTerm("SELECT PARTY")
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = sectionCardBg)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(q1, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp), color = sectionCardTextColor)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        inventoryEntries.forEachIndexed { index, entry ->
                                             val currentItem = allStockItems.find { it.id == entry.stockItemId }
                                             
                                             Spacer(modifier = Modifier.height(16.dp))
                                             Box(modifier = Modifier.fillMaxWidth()) {
                                                ModernTextField(
                                                    value = currentItem?.name ?: "",
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    placeholder = productPlaceholder,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    isLightTheme = true,
                                                    trailingIcon = {
                                                        if (inventoryEntries.size > 1) {
                                                            IconButton(onClick = { 
                                                                inventoryEntries.removeAt(index)
                                                                val total = inventoryEntries.sumOf { it.amount }
                                                                if (debitEntries.isNotEmpty()) debitEntries[0] = debitEntries[0].copy(amount = total)
                                                                if (creditEntries.isNotEmpty()) creditEntries[0] = creditEntries[0].copy(amount = total)
                                                            }) { Icon(Icons.Default.Delete, TerminologyManager.getTerm("Remove"), tint = Color(0xFFFF5252)) }
                                                        }
                                                    }
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .padding(end = 48.dp)
                                                        .clickable {
                                                            currentInventoryEntryIndex = index
                                                            showStockItemSelectionDialog = true
                                                        }
                                                )
                                            }

                                            if (currentItem != null) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    // Quantity
                                                    ValidatedNumberTextField(
                                                        value = if (entry.quantity == 0.0) "" else formatQty(entry.quantity),
                                                        onValueChange = { qtyStr ->
                                                            val qty = qtyStr.toDoubleOrNull() ?: 0.0
                                                            val rate = entry.rate
                                                            val amount = qty * rate
                                                            inventoryEntries[index] = entry.copy(quantity = qty, amount = amount.rupeesToPaise())
                                                            
                                                            val total = inventoryEntries.sumOf { it.amount }
                                                            if (debitEntries.isNotEmpty()) debitEntries[0] = debitEntries[0].copy(amount = total)
                                                            if (creditEntries.isNotEmpty()) creditEntries[0] = creditEntries[0].copy(amount = total)
                                                        },
                                                        label = TerminologyManager.getTerm("QUANTITY"),
                                                        modifier = Modifier.weight(1f),
                                                        isDecimal = true,
                                                        isLightTheme = true
                                                    )

                                                    // Rate
                                                    ValidatedNumberTextField(
                                                        value = if (entry.rate == 0.0) "" else entry.rate.toString(),
                                                        onValueChange = { rateStr ->
                                                            val rate = rateStr.toDoubleOrNull() ?: 0.0
                                                            val qty = entry.quantity
                                                            val amount = qty * rate
                                                            inventoryEntries[index] = entry.copy(rate = rate, amount = amount.rupeesToPaise())

                                                            val total = inventoryEntries.sumOf { it.amount }
                                                            if (debitEntries.isNotEmpty()) debitEntries[0] = debitEntries[0].copy(amount = total)
                                                            if (creditEntries.isNotEmpty()) creditEntries[0] = creditEntries[0].copy(amount = total)
                                                        },
                                                        label = TerminologyManager.getTerm("RATE"),
                                                        modifier = Modifier.weight(1f),
                                                        isDecimal = true,
                                                        isLightTheme = true
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                AutoShrinkText("${TerminologyManager.getTerm("TOTAL:")} ₹${String.format("%.2f", entry.amount.paiseToRupees())}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = sectionCardTextColor)
                                            }
                                        }
                                        
                                        TextButton(
                                            onClick = { inventoryEntries.add(InventoryEntry(voucherId = 0, stockItemId = 0, quantity = 1.0, rate = 0.0, amount = 0L)) },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text(TerminologyManager.getTerm("+ Add Another Item"), color = sectionCardTextColor)
                                        }
                                    }
                                }

                                // 2. Party Selection
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = sectionCardBg)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(q2, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp), color = sectionCardTextColor)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        ModernDropdownField(
                                            text = simplePartyLedger?.name ?: "",
                                            onClick = {
                                                // Supplier here can legitimately be Cash/Bank (paid on the spot) per
                                                // the help text below, so only tax/stock control ledgers are excluded.
                                                ledgerFilter = { ledger ->
                                                    val gp = ledger.group.lowercase()
                                                    !gp.contains("duties") && !gp.contains("tax") &&
                                                    !gp.contains("stock-in-hand")
                                                }
                                                isDebitEntrySelection = false
                                                currentLedgerEntryIndex = 0
                                                showLedgerSelectionDialog = true
                                            },
                                            modifier = Modifier.fillMaxWidth().height(56.dp),
                                            placeholder = partyPlaceholder,
                                            isLightTheme = true
                                        )
                                    }
                                }

                                // Auto-set Purchase Ledger
                                LaunchedEffect(Unit) {
                                    if (debitEntries[0].ledgerId == 0) {
                                        val accountLedger = allLedgers.find { it.name.contains("Purchase", ignoreCase = true) }
                                        accountLedger?.let { debitEntries[0] = debitEntries[0].copy(ledgerId = it.id) }
                                    }
                                }

                                // Sync Party Ledger Display
                                val purchasePartyId = creditEntries[0].ledgerId
                                LaunchedEffect(purchasePartyId) {
                                    if (purchasePartyId != 0) {
                                        simplePartyLedger = allLedgers.find { it.id == purchasePartyId }
                                    } else {
                                        simplePartyLedger = null
                                    }
                                }
                            }
                            
                            "Receipt", "Payment" -> {
                                val isReceipt = type == "Receipt"
                                val q1 = if (isReceipt) {
                                    if (isSimpleLanguage) TerminologyManager.getTerm("WHO PAID YOU?") else TerminologyManager.getTerm("RECEIVED FROM (CREDIT)")
                                } else {
                                    if (isSimpleLanguage) TerminologyManager.getTerm("WHO DID YOU PAY?") else TerminologyManager.getTerm("PAID TO (DEBIT)")
                                }
                                val q2 = if (isSimpleLanguage) TerminologyManager.getTerm("HOW MUCH?") else TerminologyManager.getTerm("AMOUNT")
                                val q3 = if (isReceipt) {
                                    if (isSimpleLanguage) TerminologyManager.getTerm("RECEIVED IN (CASH/BANK)") else TerminologyManager.getTerm("DEPOSITED TO (DEBIT)")
                                } else {
                                    if (isSimpleLanguage) TerminologyManager.getTerm("PAID FROM (CASH/BANK)") else TerminologyManager.getTerm("PAID FROM (CREDIT)")
                                }
                                val partySelectPlaceholder = if (isSimpleLanguage) TerminologyManager.getTerm("SELECT PERSON/COMPANY") else TerminologyManager.getTerm("SELECT PARTY")

                                val partyLedgerId = if (isReceipt) creditEntries[0].ledgerId else debitEntries[0].ledgerId
                                val partyLedger = allLedgers.find { it.id == partyLedgerId }
                                val isBillWise = partyLedger?.maintainBillWise == true
                                val myAllocations = billAllocations.filter { it.voucherEntryId == partyLedgerId }

                                LaunchedEffect(isBillWise, partyLedgerId, myAllocations) {
                                    if (isBillWise && partyLedgerId != 0) {
                                        val total = myAllocations.sumOf { it.amount }
                                        if (kotlin.math.abs(total - debitEntries[0].amount) > 0.001) {
                                            debitEntries[0] = debitEntries[0].copy(amount = total)
                                            creditEntries[0] = creditEntries[0].copy(amount = total)
                                        }
                                        if (myAllocations.isEmpty()) {
                                            billAllocations.add(
                                                BillAllocation(
                                                    voucherEntryId = partyLedgerId,
                                                    refType = "Agst Ref",
                                                    refName = "",
                                                    amount = 0L
                                                )
                                            )
                                        }
                                    }
                                }

                                // Bank/Cash "PAID FROM" account picker helper
                                val accountLedgerId = if (isReceipt) debitEntries[0].ledgerId else creditEntries[0].ledgerId
                                val accountLedger = allLedgers.find { it.id == accountLedgerId }
                                val isCashAccountSelected = accountLedger?.group?.contains("Cash", ignoreCase = true) == true
                                val isBankAccountSelected = accountLedger != null && !isCashAccountSelected
                                fun selectAccountMode(mode: String) {
                                    if (mode == "Cash") {
                                        val cashLedgers = allLedgers.filter { it.group.contains("Cash", ignoreCase = true) }
                                        if (cashLedgers.size == 1) {
                                            val id = cashLedgers[0].id
                                            if (isReceipt) debitEntries[0] = debitEntries[0].copy(ledgerId = id)
                                            else creditEntries[0] = creditEntries[0].copy(ledgerId = id)
                                            return
                                        }
                                        ledgerFilter = { ledger -> ledger.group.contains("Cash", ignoreCase = true) }
                                    } else {
                                        ledgerFilter = { ledger ->
                                            ledger.group.contains("Bank Accounts", ignoreCase = true) ||
                                            ledger.group.contains("Bank OD A/c", ignoreCase = true) ||
                                            ledger.group.contains("Bank", ignoreCase = true)
                                        }
                                    }
                                    currentLedgerEntryIndex = 0
                                    isDebitEntrySelection = isReceipt
                                    showLedgerSelectionDialog = true
                                }

                                // Plain white card matching target screenshot exactly
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FA)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(16.dp)
                                    ) {
                                        // 1. Party Selection
                                        MoneyStepCard(stepNumber = 1, title = q1, icon = Icons.Filled.Person) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(52.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFFEEEEEE))
                                                    .clickable {
                                                        // The party here is who you paid/received from - the actual
                                                        // Cash/Bank account is picked separately in step 2 below, and
                                                        // tax/stock control ledgers are never a valid counterparty.
                                                        ledgerFilter = { ledger ->
                                                            val gp = ledger.group.lowercase()
                                                            !gp.contains("duties") && !gp.contains("tax") &&
                                                            !gp.contains("cash") && !gp.contains("bank") &&
                                                            !gp.contains("stock-in-hand")
                                                        }
                                                        isDebitEntrySelection = !isReceipt
                                                        currentLedgerEntryIndex = 0
                                                        showLedgerSelectionDialog = true
                                                    }
                                                    .padding(horizontal = 14.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                AutoShrinkText(
                                                    text = if (simplePartyLedger?.name?.isNotEmpty() == true) simplePartyLedger!!.name else partySelectPlaceholder,
                                                    color = if (simplePartyLedger?.name?.isNotEmpty() == true) Color(0xFF333333) else Color(0xFF999999),
                                                    maxFontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(end = 24.dp)
                                                )
                                                Icon(
                                                    imageVector = Icons.Filled.ArrowDropDown,
                                                    contentDescription = null,
                                                    tint = Color(0xFF999999),
                                                    modifier = Modifier.align(Alignment.CenterEnd)
                                                )
                                            }
                                        }

                                        if (isBillWise && partyLedgerId != 0) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                TerminologyManager.getTerm("WHAT IS THIS PAYMENT FOR?"),
                                                color = Color(0xFF32194D),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                TerminologyManager.getTerm("Is this an advance, or against a previous bill?"),
                                                color = Color(0xFF32194D).copy(alpha = 0.7f),
                                                fontSize = 11.sp
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))

                                            myAllocations.forEach { allocation ->
                                                BillAllocationRow(
                                                    allocation = allocation,
                                                    onChange = { updated ->
                                                        val index = billAllocations.indexOf(allocation)
                                                        if (index != -1) billAllocations[index] = updated
                                                     },
                                                    onDelete = { billAllocations.remove(allocation) },
                                                    onPickRef = {
                                                        ledgerFilter = null
                                                        currentLedgerEntryIndex = 0
                                                        isDebitEntrySelection = !isReceipt
                                                        voucherViewModel.getOutstandingVouchers(partyLedgerId)
                                                        showOutstandingDialog = true
                                                    }
                                                )
                                            }

                                            TextButton(
                                                onClick = {
                                                    billAllocations.add(
                                                        BillAllocation(
                                                            voucherEntryId = partyLedgerId,
                                                            refType = "Agst Ref",
                                                            refName = "",
                                                            amount = 0L
                                                        )
                                                     )
                                                 }
                                             ) { Text(TerminologyManager.getTerm("+ Add Another Entry"), color = Color(0xFF32194D)) }
                                        }

                                        // 2. Amount
                                        if (!isBillWise) {
                                            MoneyStepCard(stepNumber = 2, title = q2, icon = Icons.Filled.MonetizationOn) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(52.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFFEEEEEE))
                                                        .padding(horizontal = 14.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (debitEntries[0].amount == 0L) {
                                                        Text(TerminologyManager.getTerm("AMOUNT (₹)"), color = Color(0xFF999999), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                    }
                                                    androidx.compose.foundation.text.BasicTextField(
                                                        value = if (debitEntries[0].amount == 0L) "" else debitEntries[0].amount.paiseToRupees().toString(),
                                                        onValueChange = { amtStr ->
                                                            if (!isBillWise) {
                                                                val amt = amtStr.toDoubleOrNull() ?: 0.0
                                                                debitEntries[0] = debitEntries[0].copy(amount = amt.rupeesToPaise())
                                                                creditEntries[0] = creditEntries[0].copy(amount = amt.rupeesToPaise())
                                                            }
                                                        },
                                                        readOnly = isBillWise,
                                                        singleLine = true,
                                                        textStyle = androidx.compose.ui.text.TextStyle(
                                                            color = Color(0xFF333333),
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Medium
                                                        ),
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF333333)),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }

                                        // 3. Payment Mode (Cash / Bank Account) — two icon buttons
                                        MoneyStepCard(
                                            stepNumber = 3,
                                            title = q3,
                                            icon = Icons.Filled.Lock,
                                            subtitle = if (isBankAccountSelected) "${TerminologyManager.getTerm("Selected:")} ${accountLedger?.name}" else null
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                PaymentTypeCard(
                                                    label = TerminologyManager.getTerm("Cash"),
                                                    icon = Icons.Default.Payments,
                                                    isSelected = isCashAccountSelected,
                                                    onClick = { selectAccountMode("Cash") },
                                                    modifier = Modifier.weight(1f),
                                                    cardHeight = 90.dp,
                                                    iconColorOverride = Color(0xFFB8860B),
                                                    gradientTopSelected = Color(0xFF6B4A1E),
                                                    gradientBottomSelected = Color(0xFF3E2A1D),
                                                    gradientTopUnselected = Color(0xFF3E2A1D),
                                                    gradientBottomUnselected = Color(0xFF241812),
                                                    borderColorSelected = Color(0xFFE7C878),
                                                    borderColorUnselected = Color(0xFF6B4A1E)
                                                )
                                                PaymentTypeCard(
                                                    label = TerminologyManager.getTerm("Bank Account"),
                                                    icon = Icons.Default.AccountBalance,
                                                    isSelected = isBankAccountSelected,
                                                    onClick = { selectAccountMode("Bank") },
                                                    modifier = Modifier.weight(1f),
                                                    cardHeight = 90.dp,
                                                    iconColorOverride = Color(0xFF6E8A94),
                                                    gradientTopSelected = Color(0xFF6B4A1E),
                                                    gradientBottomSelected = Color(0xFF3E2A1D),
                                                    gradientTopUnselected = Color(0xFF3E2A1D),
                                                    gradientBottomUnselected = Color(0xFF241812),
                                                    borderColorSelected = Color(0xFFE7C878),
                                                    borderColorUnselected = Color(0xFF6B4A1E)
                                                )
                                            }
                                        }
                                    }
                                }

                                LaunchedEffect(partyLedgerId) {
                                    if (partyLedgerId != 0) {
                                         simplePartyLedger = allLedgers.find { it.id == partyLedgerId }
                                    }
                                }
                            }
                        }
                    } // End of Safe Block
                    } else {
                        // --- STANDARD ACCOUNTING UI ---
                        
                        // Compact Row 2: Narration Only (Voucher No moved to top)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ModernTextField(
                                value = narration,
                                onValueChange = { narration = it },
                                placeholder = TerminologyManager.getTerm("Narration"),
                                modifier = Modifier.weight(1f),
                                isLightTheme = true,
                                height = 40.dp,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        showHelpDialog(
                                            title = TerminologyManager.getTerm("Narration"),
                                            message = TerminologyManager.getTerm("Narration is a brief note or description for the transaction (e.g., 'Paid for office supplies', 'Goods sold to Mr. Sharma').")
                                        )
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = TerminologyManager.getTerm("Help with Narration"), tint = Color.LightGray)
                                    }
                                }
                            )
                        }

                        if (showCustomField) {
                            Spacer(modifier = Modifier.height(4.dp))
                            ModernTextField(
                                value = customField,
                                onValueChange = { customField = it },
                                placeholder = TerminologyManager.getTerm("Custom Field (e.g. IMEI, Vehicle No)"),
                                modifier = Modifier.fillMaxWidth(),
                                isLightTheme = true
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val debitTitle = when(type) {
                            "Sales" -> TerminologyManager.getTerm("PAYMENT MODE / RECEIVED IN")
                            "Purchase" -> TerminologyManager.getTerm("PURCHASE ACCOUNT / WHAT YOU BOUGHT")
                            "Receipt" -> TerminologyManager.getTerm("DEPOSITED TO / CASH")
                            "Payment" -> TerminologyManager.getTerm("PAID TO")
                            "Quotation" -> TerminologyManager.getTerm("PARTY A/C (CUSTOMER)")
                            else -> if (isSimpleLanguage) TerminologyManager.getTerm("MONEY ADDED TO / RECEIVED BY") else TerminologyManager.getTerm("DEBIT (DR)")
                        }

                        val creditTitle = when(type) {
                            "Sales" -> TerminologyManager.getTerm("Sale Type")
                            "Purchase" -> TerminologyManager.getTerm("Payment Mode / Paid From")
                            "Receipt" -> TerminologyManager.getTerm("Received From")
                            "Payment" -> TerminologyManager.getTerm("Paid From")
                            "Quotation" -> TerminologyManager.getTerm("SALES ACCOUNT")
                            else -> if (isSimpleLanguage) TerminologyManager.getTerm("MONEY TAKEN FROM / GIVEN BY") else TerminologyManager.getTerm("Credit (Cr)")
                        }

                        // Debit Section
                        SectionHeader(
                            title = debitTitle,
                            color = Color(0xFF32194D),
                            onHelpClick = {
                                val helpText = when(type) {
                                    "Sales" -> TerminologyManager.getTerm("Please add the Customer's name (the person buying from you). If it's a cash sale, select 'Cash-in-hand'.")
                                    "Purchase" -> TerminologyManager.getTerm("Please select your Purchase Account. This tracks what you are buying.")
                                    "Receipt" -> TerminologyManager.getTerm("Where did the money go? Select 'Cash-in-hand' if you received cash, or your 'Bank Account'.")
                                    "Payment" -> TerminologyManager.getTerm("Who are you paying? Select the person or expense account (like Rent, Salary).")
                                    "Quotation" -> TerminologyManager.getTerm("Select the Customer to whom you are giving this quotation.")
                                    else -> if (isSimpleLanguage) TerminologyManager.getTerm("Select the account that is receiving the money or value (e.g. your customer or your expense account).") else TerminologyManager.getTerm("DEBIT (Dr) is for the receiving aspect of a transaction. For example, the account that receives value.")
                                }
                                showHelpDialog(title = TerminologyManager.getTerm("Debit Section"), message = helpText)
                            },
                            onAdd = {
                                debitEntries.add(VoucherEntry(voucherId = 0, ledgerId = 0, amount = 0L, isDebit = true))
                            }
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            debitEntries.forEachIndexed { i, entry ->
                                VoucherEntryRow(
                                    entry = entry,
                                    allLedgers = allLedgers,
                                    billAllocations = billAllocations,
                                    voucherNumber = voucherNumber,
                                    onLedgerClick = {
                                        ledgerFilter = when (type) {
                                            "Quotation" -> { ledger ->  // Dr = Customer
                                                val gp = ledger.group.lowercase()
                                                (gp.contains("debtor") || gp.contains("creditor") || gp.contains("supplier") || gp.contains("customer")) &&
                                                !gp.contains("tax") && !gp.contains("duties") && !gp.contains("gst")
                                            }
                                            // Journal & others — show all except auto-managed groups
                                            else -> { ledger ->
                                                val gp = ledger.group.lowercase()
                                                !gp.contains("duties") && !gp.contains("tax") && !gp.contains("gst") &&
                                                !gp.contains("stock-in-hand")
                                            }
                                        }
                                        currentLedgerEntryIndex = i
                                        isDebitEntrySelection = true
                                        showLedgerSelectionDialog = true
                                    },
                                    onAmountChange = { newAmt -> debitEntries[i] = entry.copy(amount = newAmt.rupeesToPaise()) },
                                    onDeleteClick = { debitEntries.removeAt(i) },
                                    modifier = Modifier.fillMaxWidth(),
                                    onShowOutstanding = {
                                        currentLedgerEntryIndex = i
                                        isDebitEntrySelection = true
                                        voucherViewModel.getOutstandingVouchers(entry.ledgerId)
                                        showOutstandingDialog = true
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Credit Section
                        SectionHeader(
                            title = creditTitle,
                            color = Color(0xFFB71C3C),
                            onHelpClick = {
                                val helpText = when(type) {
                                    "Sales" -> TerminologyManager.getTerm("Please select your Sales Account. This tracks your total sales revenue.")
                                    "Purchase" -> TerminologyManager.getTerm("Please add the Supplier's name (who you bought from). If you paid cash immediately, select 'Cash-in-hand'.")
                                    "Receipt" -> TerminologyManager.getTerm("Who paid you? Select the customer or person who gave you the money.")
                                    "Payment" -> TerminologyManager.getTerm("How did you pay? Select 'Cash-in-hand' or your 'Bank Account'.")
                                    "Quotation" -> TerminologyManager.getTerm("Select your Sales Account (e.g., Local Sales, Services).")
                                    else -> if (isSimpleLanguage) TerminologyManager.getTerm("Select the account that is giving the money or value (e.g. your bank, cash, or a supplier).") else TerminologyManager.getTerm("CREDIT (Cr) is for the giving aspect of a transaction. For example, the account that gives value.")
                                }
                                showHelpDialog(title = TerminologyManager.getTerm("Credit Section"), message = helpText)
                            },
                            onAdd = {
                                creditEntries.add(VoucherEntry(voucherId = 0, ledgerId = 0, amount = 0L, isDebit = false))
                            }
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            creditEntries.forEachIndexed { i, entry ->
                                val emptyText = if (type == "Sales" || type == "Quotation") TerminologyManager.getTerm("Add or create any sale account") else TerminologyManager.getTerm("Select Ledger")
                                VoucherEntryRow(
                                    entry = entry,
                                    allLedgers = allLedgers,
                                    billAllocations = billAllocations,
                                    voucherNumber = voucherNumber,
                                    onLedgerClick = {
                                        ledgerFilter = when (type) {
                                            "Quotation" -> { ledger ->  // Cr = Sales Account
                                                val gp = ledger.group.lowercase()
                                                (gp.contains("sales") || gp.contains("income")) &&
                                                !gp.contains("tax") && !gp.contains("duties") && !gp.contains("gst")
                                            }
                                            // Journal & others — show all except auto-managed groups
                                            else -> { ledger ->
                                                val gp = ledger.group.lowercase()
                                                !gp.contains("duties") && !gp.contains("tax") && !gp.contains("gst") &&
                                                !gp.contains("stock-in-hand")
                                            }
                                        }
                                        currentLedgerEntryIndex = i
                                        isDebitEntrySelection = false
                                        showLedgerSelectionDialog = true
                                    },
                                    onAmountChange = { newAmt -> creditEntries[i] = entry.copy(amount = newAmt.rupeesToPaise()) },
                                    onDeleteClick = { creditEntries.removeAt(i) },
                                    modifier = Modifier.fillMaxWidth(),
                                    emptyText = emptyText,
                                    onShowOutstanding = {
                                        currentLedgerEntryIndex = i
                                        isDebitEntrySelection = false
                                        voucherViewModel.getOutstandingVouchers(entry.ledgerId)
                                        showOutstandingDialog = true
                                    }
                                )
                            }
                        }

                        if (type == "Sales" || type == "Purchase" || type == "Quotation") {
                            Spacer(modifier = Modifier.height(12.dp))
                            SectionHeader(
                                title = TerminologyManager.getTerm("Inventory"),
                                color = Color(0xFF7B1FA2),
                                                            onHelpClick = {
                                                                showHelpDialog(
                                                                    title = TerminologyManager.getTerm("Inventory Section"),
                                                                    message = TerminologyManager.getTerm("Use this section only if you are selling or buying physical products (stock).\n\nAdd the items, the quantity sold/purchased, and the rate per item. The total amount will be calculated automatically.")
                                                                )
                                                            },                                onAdd = {
                                    inventoryEntries.add(InventoryEntry(voucherId = 0, stockItemId = 0, quantity = 1.0, rate = 0.0, amount = 0L))
                                }
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                inventoryEntries.forEachIndexed { i, entry ->
                                    InventoryEntryRow(
                                        entry = entry,
                                        stockItemViewModel = stockItemViewModel,
                                        onItemClick = { currentInventoryEntryIndex = i; showStockItemSelectionDialog = true },
                                        onQtyChange = { qty -> inventoryEntries[i] = entry.copy(quantity = qty, amount = (qty * entry.rate).rupeesToPaise()) },
                                        onRateChange = { rate -> inventoryEntries[i] = entry.copy(rate = rate, amount = (entry.quantity * rate).rupeesToPaise()) },
                                        onDeleteClick = { inventoryEntries.removeAt(i) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        if (type == "Journal") {
                            Spacer(modifier = Modifier.height(12.dp))
                            SectionHeader(
                                title = TerminologyManager.getTerm("Items Exchanged (Optional)"),
                                color = Color(0xFF7B1FA2),
                                onHelpClick = {
                                    showHelpDialog(
                                        title = TerminologyManager.getTerm("Items Exchanged"),
                                        message = TerminologyManager.getTerm("Use this when the adjustment involves swapping goods instead of money — e.g. you took 20 iron rods from a supplier and gave 5 aluminium rods in exchange.\n\nAdd what you RECEIVED under 'Received' and what you GAVE under 'Given'. Stock levels for both items update automatically — you don't need to enter a separate ledger amount just for the item value.")
                                    )
                                },
                                onAdd = null
                            )

                            Text(
                                text = TerminologyManager.getTerm("RECEIVED (STOCK IN)"),
                                color = Color(0xFF2E7D32),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 6.dp, start = 4.dp, bottom = 2.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                inventoryEntries.forEachIndexed { i, entry ->
                                    InventoryEntryRow(
                                        entry = entry,
                                        stockItemViewModel = stockItemViewModel,
                                        onItemClick = { currentInventoryEntryIndex = i; showStockItemSelectionDialog = true },
                                        onQtyChange = { qty -> inventoryEntries[i] = entry.copy(quantity = qty, amount = (qty * entry.rate).rupeesToPaise()) },
                                        onRateChange = { rate -> inventoryEntries[i] = entry.copy(rate = rate, amount = (entry.quantity * rate).rupeesToPaise()) },
                                        onDeleteClick = { inventoryEntries.removeAt(i) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            TextButton(onClick = { inventoryEntries.add(InventoryEntry(voucherId = 0, stockItemId = 0, quantity = 1.0, rate = 0.0, amount = 0L)) }) {
                                Text(TerminologyManager.getTerm("+ Add item received"), color = Color(0xFF2E7D32))
                            }

                            Text(
                                text = TerminologyManager.getTerm("GIVEN (STOCK OUT)"),
                                color = Color(0xFFC62828),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 6.dp, start = 4.dp, bottom = 2.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                stockOutEntries.forEachIndexed { i, entry ->
                                    InventoryEntryRow(
                                        entry = entry,
                                        stockItemViewModel = stockItemViewModel,
                                        onItemClick = { currentInventoryEntryIndex = -(i + 2); showStockItemSelectionDialog = true },
                                        onQtyChange = { qty -> stockOutEntries[i] = entry.copy(quantity = qty, amount = (qty * entry.rate).rupeesToPaise()) },
                                        onRateChange = { rate -> stockOutEntries[i] = entry.copy(rate = rate, amount = (entry.quantity * rate).rupeesToPaise()) },
                                        onDeleteClick = { stockOutEntries.removeAt(i) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            TextButton(onClick = { stockOutEntries.add(InventoryEntry(voucherId = 0, stockItemId = 0, quantity = 1.0, rate = 0.0, amount = 0L)) }) {
                                Text(TerminologyManager.getTerm("+ Add item given"), color = Color(0xFFC62828))
                            }
                        }
                    } // End of Standard UI Else Block
                    
                    Spacer(modifier = Modifier.height(24.dp)) // Bottom padding
                }

                // Save Button at the Bottom - teal gradient, painted on a Box behind a
                // transparent Button so all the existing click/disabled/loading logic is untouched.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .height(if (isMoneyFlow) 56.dp else 50.dp)
                        .clip(if (isMoneyFlow) RoundedCornerShape(50) else RoundedCornerShape(8.dp))
                        .background(
                            if (isMoneyFlow) Brush.horizontalGradient(listOf(Color(0xFF1B5E3F), Color(0xFF2E8B57)))
                            else Brush.horizontalGradient(listOf(topThemeColors.dateTint, topThemeColors.voucherTint))
                        )
                ) {
                Button(
                    onClick = {
                        if (isSaving) return@Button

                        if (simplePaymentMode == "Finance" && customField.trim().isBlank()) {
                            Toast.makeText(context, TerminologyManager.getTerm("Customer Name is required for Finance Sale"), Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val totalDr = debitEntries.sumOf { it.amount }
                        val totalCr = creditEntries.sumOf { it.amount }
                        
                        if (kotlin.math.abs(totalDr - totalCr) > 0L) {
                            Toast.makeText(context, TerminologyManager.getTerm("Totals Mismatch!"), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (debitEntries.isEmpty() || creditEntries.isEmpty()) {
                            Toast.makeText(context, TerminologyManager.getTerm("Add Debit and Credit entries"), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        // Guard: don't save with unassigned ledgers (ledgerId=0 → "Unknown" in reports)
                        if (debitEntries.any { it.ledgerId == 0 } || creditEntries.any { it.ledgerId == 0 }) {
                            Toast.makeText(context, TerminologyManager.getTerm("Please select a ledger for every entry before saving."), Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        isSaving = true
                        coroutineScope.launch {
                            // CRITICAL FIX: Clear allocations for non-payment/receipt vouchers
                            // This ensures new Sales/Purchases are treated as "New Reference"
                            // and will appear in outstandings.
                            if (type != "Payment" && type != "Receipt") {
                                billAllocations.clear()
                            }

                            // Get first non-blank refName from bill allocations for display
                            val refDisplay = billAllocations.firstOrNull { !it.refName.isNullOrBlank() }?.refName

                            val parsedInterestRate = interestRate.toDoubleOrNull() ?: 0.0
                            val parsedInterestDays = interestDays.toIntOrNull() ?: 0
                            val isNewVoucher = voucherId == -1 || voucherId == null || isDuplicate
                            val existingVoucher = currentVoucherWithAllEntries?.voucher

                            // The edit path must start from the stored row and change only the
                            // fields this screen owns. Building a fresh Voucher() here resets
                            // every column the screen doesn't set — companyId would fall back to
                            // 0, and since voucherDao.update is a full-row write that strips the
                            // voucher out of every company-scoped query, making it look deleted.
                            if (!isNewVoucher && existingVoucher == null) {
                                // Load hasn't landed yet; saving now would write a header built
                                // from blank defaults over a real voucher.
                                Toast.makeText(
                                    context,
                                    TerminologyManager.getTerm("Still loading this voucher. Please try again in a moment."),
                                    Toast.LENGTH_LONG
                                ).show()
                                isSaving = false
                                return@launch
                            }

                            val newVoucher = if (!isNewVoucher && existingVoucher != null) {
                                existingVoucher.copy(
                                    type = type,
                                    date = date,
                                    voucherNumber = voucherNumber,
                                    narration = narration.ifBlank { null },
                                    customField = customField.ifBlank { null },
                                    chargeInterest = chargeInterest,
                                    interestRate = if (chargeInterest) parsedInterestRate else 0.0,
                                    interestDays = if (chargeInterest) parsedInterestDays else 0,
                                    refDisplayName = refDisplay,
                                    placeOfSupply = placeOfSupply.ifBlank { null },
                                    supplyType = supplyType.ifBlank { null },
                                    reverseCharge = reverseCharge
                                )
                            } else {
                                Voucher(
                                    id = 0,
                                    type = type,
                                    date = date,
                                    voucherNumber = voucherNumber,
                                    narration = narration.ifBlank { null },
                                    customField = customField.ifBlank { null },
                                    chargeInterest = chargeInterest,
                                    interestRate = if (chargeInterest) parsedInterestRate else 0.0,
                                    interestDays = if (chargeInterest) parsedInterestDays else 0,
                                    refDisplayName = refDisplay,
                                    companyId = currentCompanyId ?: 0,
                                    placeOfSupply = placeOfSupply.ifBlank { null },
                                    supplyType = supplyType.ifBlank { null },
                                    reverseCharge = reverseCharge
                                )
                            }
                            // Journal barter items: merge "given" items back in with a negated
                            // quantity/amount so stock-direction math (sign-based) sees them correctly.
                            val finalInventoryEntries = if (type == "Journal") {
                                inventoryEntries + stockOutEntries.map {
                                    it.copy(quantity = -it.quantity, amount = -(it.quantity * it.rate).rupeesToPaise())
                                }
                            } else inventoryEntries

                            if (isNewVoucher) {
                                voucherViewModel.insertVoucherWithAllEntries(newVoucher, debitEntries + creditEntries, finalInventoryEntries, billAllocations)
                                Toast.makeText(context, TerminologyManager.getTerm("Saved"), Toast.LENGTH_SHORT).show()
                            } else {
                                voucherViewModel.updateVoucherWithAllEntries(newVoucher, debitEntries + creditEntries, finalInventoryEntries, billAllocations)
                                Toast.makeText(context, TerminologyManager.getTerm("Updated"), Toast.LENGTH_SHORT).show()
                            }
                            
                            // Note: Purchase no longer asks "Was payment made?" here — new
                            // purchases are created via PurchaseVoucherCreationScreen, which has
                            // its own BANK / CASH / ON CREDIT selector built into the voucher
                            // screen itself and handles the settlement entry there. This screen
                            // only ever sees Purchase in EDIT mode, where re-asking and inserting
                            // another voucher would just duplicate the bill.
                            if (type == "Sales" || type == "Purchase" || type == "Quotation") {
                                showSaveSuccessDialog = true
                                isSaving = false
                            } else {
                                navController.popBackStack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    shape = if (isMoneyFlow) RoundedCornerShape(50) else RoundedCornerShape(8.dp),
                    enabled = !isSaving,
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                    contentPadding = if (isMoneyFlow) PaddingValues(horizontal = 6.dp) else ButtonDefaults.ContentPadding,
                    // Transparent - the gradient is painted on the Box behind this Button.
                    // (MaterialTheme.colorScheme.primary was pure white under the app's default
                    // "Crystal" theme, which made the old solid-color button disappear.)
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = if (isMoneyFlow) Color(0xFFE7C878) else Color.White,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = if (isMoneyFlow) Color(0xFFE7C878) else Color.White)
                    } else if (isMoneyFlow) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF17603F)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("₹", color = Color(0xFFE7C878), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Text(
                                text = TerminologyManager.getTerm("Save Voucher").uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFE7C878),
                                modifier = Modifier.padding(end = 12.dp).size(20.dp)
                            )
                        }
                    } else {
                        Text(TerminologyManager.getTerm("Save Voucher"), fontSize = 16.sp)
                    }
                }
                } // end gradient Box
            }
        }
    }

    if (showDiscardChangesDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardChangesDialog = false },
            title = { Text(TerminologyManager.getTerm("Discard changes?")) },
            text = { Text(TerminologyManager.getTerm("You have unsaved changes to this voucher. If you go back now, they will be lost.")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardChangesDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text(TerminologyManager.getTerm("Discard"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardChangesDialog = false }) {
                    Text(TerminologyManager.getTerm("Keep Editing"))
                }
            }
        )
    }

    if (showSaveSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSaveSuccessDialog = false 
                navController.popBackStack()
            },
            title = { Text(TerminologyManager.getTerm("Voucher Saved")) },
            text = { Text(TerminologyManager.getTerm("Do you want to export this invoice to PDF?")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val tempVoucher = Voucher(
                            id = voucherId ?: 0, // ID might be 0 if new, but for export we mainly need Number & Date
                            type = type,
                            date = date,
                            voucherNumber = voucherNumber,
                            narration = narration,
                            chargeInterest = chargeInterest,
                            interestRate = interestRate.toDoubleOrNull() ?: 0.0,
                            interestDays = interestDays.toIntOrNull() ?: 0
                        )
                        // Find Customer/Party Ledger
                        // For Sales/Quotation: Usually the Debit entry is the Party (Debtor).
                        // For Purchase: Usually the Credit entry is the Party (Creditor).
                        val partyEntry = if (type == "Sales" || type == "Quotation") {
                            debitEntries.firstOrNull() 
                        } else {
                            creditEntries.firstOrNull()
                        }
                        
                        val customerLedger = partyEntry?.let { entry -> allLedgers.find { it.id == entry.ledgerId } }
                        
                        ExportUtils.exportInvoiceToPdf(context, tempVoucher, customerLedger, inventoryEntries, allStockItems, allUnits, company)
                        
                        showSaveSuccessDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text(TerminologyManager.getTerm("Yes, Export PDF"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveSuccessDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text(TerminologyManager.getTerm("No, Just Save"))
                }
            }
        )
    }

    // Note: the old "Was payment made?" prompt + auto-duplicate Purchase-voucher insert that
    // used to live here has been removed. PurchaseVoucherCreationScreen now owns payment
    // selection (BANK / CASH / ON CREDIT) directly on the voucher screen, so this generic
    // edit screen no longer needs to ask again after save.

    // Pre-set the ledger group based on payment context when the create dialog opens
    LaunchedEffect(showCreateLedgerDialog) {
        if (showCreateLedgerDialog) {
            newLedgerGroup = when {
                simplePaymentMode == "Finance" -> "Finance Companies"
                simplePaymentMode == "Bank" -> "Bank Accounts"
                simplePaymentMode == "Credit" || simplePaymentMode == "Pay Later" -> "Sundry Debtors"
                simplePaymentMode == "Cash" -> "Cash-in-hand"
                type == "Purchase" && useSimpleUI -> "Sundry Creditors"
                (type == "Payment" || type == "Receipt") && useSimpleUI -> "Cash-in-hand"
                else -> newLedgerGroup // keep existing for non-simple UI
            }
        }
    }

    if (showCreateLedgerDialog) {
        val isGroupLocked = simplePaymentMode == "Finance" || simplePaymentMode == "Bank"
        AlertDialog(
            onDismissRequest = { showCreateLedgerDialog = false },
            title = { Text(TerminologyManager.getTerm("Create New Ledger")) },
            text = {
                Column {
                    // Step 1: Group Selection with instructions
                    Text(
                        if (isGroupLocked) TerminologyManager.getTerm("Creating ledger for:")
                        else TerminologyManager.getTerm("What type of ledger do you want to create?"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var expandedGroup by remember { mutableStateOf(false) }

                    // Simple group names with descriptions
                    data class GroupOption(val internalName: String, val displayName: String, val description: String)
                    val allGroupOptions = listOf(
                        GroupOption("Sundry Debtors", TerminologyManager.getTerm("Customer / Debtor"), TerminologyManager.getTerm("Person who buys from you or owes you money")),
                        GroupOption("Sundry Creditors", TerminologyManager.getTerm("Supplier / Creditor"), TerminologyManager.getTerm("Person you buy from or who you owe money")),
                        GroupOption("Sales Accounts", TerminologyManager.getTerm("Sales Account"), TerminologyManager.getTerm("Track different types of sales (e.g. Local Sales, Export)")),
                        GroupOption("Purchase Accounts", TerminologyManager.getTerm("Purchase Account"), TerminologyManager.getTerm("Track different types of purchases")),
                        GroupOption("Indirect Expenses", TerminologyManager.getTerm("Expense"), TerminologyManager.getTerm("Rent, Salary, Electricity, Office expenses")),
                        GroupOption("Indirect Incomes", TerminologyManager.getTerm("Other Income"), TerminologyManager.getTerm("Interest received, Commission earned")),
                        GroupOption("Cash-in-hand", TerminologyManager.getTerm("Cash Account"), TerminologyManager.getTerm("Physical cash you hold")),
                        GroupOption("Bank Accounts", TerminologyManager.getTerm("Bank Account"), TerminologyManager.getTerm("Your bank or UPI account")),
                        GroupOption("Finance Companies", TerminologyManager.getTerm("Finance Company"), TerminologyManager.getTerm("NBFC, Money lender, or finance provider"))
                    )
                    // Filter groups to only show options relevant to the current voucher context
                    val groupOptions = when {
                        // Bank/Finance modes — group is locked, include all for lookup completeness
                        simplePaymentMode == "Bank" || simplePaymentMode == "Finance" -> allGroupOptions
                        // "On Credit" or "Pay Later" sales — only party/customer types are relevant
                        type == "Sales" && (simplePaymentMode == "Credit" || simplePaymentMode == "Pay Later") ->
                            allGroupOptions.filter { it.internalName in listOf("Sundry Debtors", "Sundry Creditors") }
                        // "Cash" sales — cash, bank, and party types
                        type == "Sales" && simplePaymentMode == "Cash" ->
                            allGroupOptions.filter { it.internalName in listOf("Cash-in-hand", "Bank Accounts", "Sundry Debtors", "Sundry Creditors") }
                        // Purchase — supplier/creditor, cash, bank
                        type == "Purchase" && useSimpleUI ->
                            allGroupOptions.filter { it.internalName in listOf("Sundry Creditors", "Sundry Debtors", "Cash-in-hand", "Bank Accounts") }
                        // Payment/Receipt — cash, bank, and party types
                        (type == "Payment" || type == "Receipt") && useSimpleUI ->
                            allGroupOptions.filter { it.internalName in listOf("Cash-in-hand", "Bank Accounts", "Sundry Debtors", "Sundry Creditors") }
                        // Non-simple UI (Journal, Quotation) — show all
                        else -> allGroupOptions
                    }

                    val selectedGroupOption = groupOptions.find { it.internalName == newLedgerGroup }

                    if (isGroupLocked) {
                        // Fixed group — not selectable; shown as a styled info row
                        val lockedGroup = selectedGroupOption
                        androidx.compose.material3.Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    lockedGroup?.displayName ?: newLedgerGroup,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (lockedGroup != null) {
                                    Text(
                                        lockedGroup.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
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
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Step 2: Name input with context-aware hint
                    val nameHint = when (newLedgerGroup) {
                        "Sundry Debtors" -> TerminologyManager.getTerm("Enter customer/person name")
                        "Sundry Creditors" -> TerminologyManager.getTerm("Enter supplier/vendor name")
                        "Sales Accounts" -> TerminologyManager.getTerm("e.g. Local Sales, Export Sales")
                        "Purchase Accounts" -> TerminologyManager.getTerm("e.g. Raw Material Purchase")
                        "Indirect Expenses" -> TerminologyManager.getTerm("e.g. Rent, Salary, Electricity")
                        "Indirect Incomes" -> TerminologyManager.getTerm("e.g. Interest Income")
                        "Cash-in-hand" -> TerminologyManager.getTerm("e.g. Shop Cash, Petty Cash")
                        "Bank Accounts" -> TerminologyManager.getTerm("e.g. HDFC Savings, GPay")
                        "Finance Companies" -> TerminologyManager.getTerm("e.g. Bajaj Finance, HDFC Fin")
                        else -> TerminologyManager.getTerm("Enter name")
                    }
                    
                    Text(
                        TerminologyManager.getTerm("Enter the name"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        nameHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            val shouldMaintainBillWise = newLedgerGroup == "Sundry Debtors" || newLedgerGroup == "Sundry Creditors" || newLedgerGroup == "Supplier" || newLedgerGroup == "Finance Companies"
                            val newId = ledgerViewModel.insertLedger(com.minhas.teller.data.entities.Ledger(
                                name = newLedgerName, 
                                group = newLedgerGroup, 
                                openingBalance = 0L, 
                                maintainBillWise = shouldMaintainBillWise,
                                companyId = currentCompanyId ?: 0
                            ))
                            
                            // Auto-select the new ledger
                            if (currentLedgerEntryIndex != -1) {
                                if (isDebitEntrySelection) {
                                    debitEntries[currentLedgerEntryIndex] = debitEntries[currentLedgerEntryIndex].copy(ledgerId = newId.toInt())
                                } else {
                                    creditEntries[currentLedgerEntryIndex] = creditEntries[currentLedgerEntryIndex].copy(ledgerId = newId.toInt())
                                }
                            }
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

    // Note: this used to have a second, minimal "Add New Product" AlertDialog here (name + unit
    // only, no HSN lookup) that was gated on the same showCreateStockItemDialog flag as the full
    // CreateStockItemDialog below — both fired together. Removed it; CreateStockItemDialog (which
    // does look up HSN/GST via SmartTaxService, same as AddEditStockItemScreen) is the only one
    // that should run, so items created this way actually get an HSN code on record.

    if (showLedgerSelectionDialog) {
        LedgerSelectionDialog(
            onDismissRequest = { showLedgerSelectionDialog = false },
            onLedgerSelected = { selected ->
                if (currentLedgerEntryIndex != -1) {
                    if (isDebitEntrySelection) {
                        debitEntries[currentLedgerEntryIndex] = debitEntries[currentLedgerEntryIndex].copy(ledgerId = selected.id)
                    } else {
                        creditEntries[currentLedgerEntryIndex] = creditEntries[currentLedgerEntryIndex].copy(ledgerId = selected.id)
                    }
                }
                showLedgerSelectionDialog = false
            },
            onCreateNewClick = {
                showCreateLedgerDialog = true
            },
            filter = ledgerFilter
        )
    }

    if (showCreateStockItemDialog) {
        CreateStockItemDialog(
            onDismissRequest = { showCreateStockItemDialog = false },
            onStockItemCreated = { newItem ->
                updateInventoryEntryAt(currentInventoryEntryIndex) { current ->
                    val newRate = if (current.rate == 0.0) newItem.rate else current.rate
                    current.copy(
                        stockItemId = newItem.id,
                        rate = newRate,
                        amount = (current.quantity * newRate).rupeesToPaise(),
                        hsnCode = newItem.hsnCode,
                        gstRate = newItem.gstRate,
                        cgstRate = newItem.cgstRate,
                        sgstRate = newItem.sgstRate,
                        igstRate = newItem.igstRate
                    )
                }
                showCreateStockItemDialog = false
                showStockItemSelectionDialog = false
            }
        )
    }

    if (showStockItemSelectionDialog) {
        StockItemSelectionDialog(
            onDismissRequest = { showStockItemSelectionDialog = false },
            onStockItemSelected = { selected ->
                updateInventoryEntryAt(currentInventoryEntryIndex) { current ->
                    val newRate = if (current.rate == 0.0) selected.rate else current.rate
                    current.copy(
                        stockItemId = selected.id,
                        rate = newRate,
                        amount = (current.quantity * newRate).rupeesToPaise(),
                        hsnCode = selected.hsnCode,
                        gstRate = selected.gstRate,
                        cgstRate = selected.cgstRate,
                        sgstRate = selected.sgstRate,
                        igstRate = selected.igstRate
                    )
                }
                showStockItemSelectionDialog = false
            },
            onCreateNewClick = { showCreateStockItemDialog = true }
        )
    }

    if (showBarcodeNotLinkedDialog) {
        AlertDialog(
            onDismissRequest = { showBarcodeNotLinkedDialog = false; pendingBarcode = null },
            title = { Text(TerminologyManager.getTerm("Barcode not linked")) },
            text = { Text(TerminologyManager.getTerm("This barcode (${pendingBarcode}) isn't linked to any item yet. Create a new item for it, or link it to one you already have.")) },
            confirmButton = {
                TextButton(onClick = {
                    showBarcodeNotLinkedDialog = false
                    showCreateStockItemForBarcode = true
                }) { Text(TerminologyManager.getTerm("Create new item")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBarcodeNotLinkedDialog = false
                    showBarcodeLinkPicker = true
                }) { Text(TerminologyManager.getTerm("Link to existing item")) }
            }
        )
    }

    if (showCreateStockItemForBarcode) {
        CreateStockItemDialog(
            onDismissRequest = { showCreateStockItemForBarcode = false; pendingBarcode = null },
            onStockItemCreated = { newItem ->
                val withBarcode = newItem.copy(barcode = pendingBarcode)
                coroutineScope.launch { stockItemRepository.update(withBarcode) }
                fillInventoryRowWithStockItem(withBarcode)
                showCreateStockItemForBarcode = false
                pendingBarcode = null
            }
        )
    }

    if (showBarcodeLinkPicker) {
        StockItemSelectionDialog(
            onDismissRequest = { showBarcodeLinkPicker = false; pendingBarcode = null },
            onStockItemSelected = { selected ->
                val withBarcode = selected.copy(barcode = pendingBarcode)
                coroutineScope.launch { stockItemRepository.update(withBarcode) }
                fillInventoryRowWithStockItem(withBarcode)
                showBarcodeLinkPicker = false
                pendingBarcode = null
            },
            onCreateNewClick = {
                showBarcodeLinkPicker = false
                showCreateStockItemForBarcode = true
            }
        )
    }

    if (showOutstandingDialog) {
        OutstandingBillsDialog(
            outstandingVouchers = outstandingVouchers,
            onDismissRequest = { showOutstandingDialog = false },
            onBillSelected = { voucher, amount ->
                if (currentLedgerEntryIndex != -1) {
                    val currentEntry = if (isDebitEntrySelection) debitEntries[currentLedgerEntryIndex] else creditEntries[currentLedgerEntryIndex]
                    
                    var targetEntry = currentEntry
                    if (voucher.ledgerId != currentEntry.ledgerId && voucher.ledgerId != 0) {
                         targetEntry = currentEntry.copy(ledgerId = voucher.ledgerId)
                    }

                    billAllocations.add(
                        BillAllocation(
                            voucherEntryId = targetEntry.ledgerId,
                            refVoucherId = voucher.voucherId,
                            refName = voucher.voucherNumber,
                            refType = "Agst Ref",
                            amount = amount.rupeesToPaise()
                        )
                    )
                    val newTotal = targetEntry.amount + amount.rupeesToPaise()
                    
                    if (isDebitEntrySelection) {
                        debitEntries[currentLedgerEntryIndex] = targetEntry.copy(amount = newTotal)
                        if (useSimpleUI && debitEntries.size == 1 && creditEntries.size == 1) {
                             creditEntries[0] = creditEntries[0].copy(amount = newTotal)
                        }
                    } else {
                        creditEntries[currentLedgerEntryIndex] = targetEntry.copy(amount = newTotal)
                        if (useSimpleUI && debitEntries.size == 1 && creditEntries.size == 1) {
                             debitEntries[0] = debitEntries[0].copy(amount = newTotal)
                        }
                    }
                }
                showOutstandingDialog = false
            }
        )
    }
}

@Composable
fun SectionHeader(
    title: String, 
    color: androidx.compose.ui.graphics.Color, 
    onHelpClick: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
            color = color,
            modifier = Modifier.weight(1f)
        )

        if (onHelpClick != null) {
            IconButton(onClick = onHelpClick, modifier = Modifier.size(22.dp)) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "${TerminologyManager.getTerm("Help for")} $title", tint = color, modifier = Modifier.size(18.dp))
            }
        }

        if (onAdd != null) {
            Spacer(modifier = Modifier.width(6.dp))

            // Plain Box (not IconButton) so the button stays exactly this size instead of
            // being padded out to Material's ~48dp minimum touch target.
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = TerminologyManager.getTerm("Add"),
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
fun VoucherEntryRow(
    entry: VoucherEntry,
    allLedgers: List<com.minhas.teller.data.entities.Ledger>,
    billAllocations: MutableList<BillAllocation>,
    voucherNumber: String, // Added voucherNumber
    onLedgerClick: () -> Unit,
    onAmountChange: (Double) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = TerminologyManager.getTerm("Select Ledger"),
    onAddClick: (() -> Unit)? = null,
    onShowOutstanding: (() -> Unit)? = null
) {
    // Fast lookup from passed list instead of async fetch
    val ledger = allLedgers.find { it.id == entry.ledgerId }
    
    val isCashOrStock = ledger?.group?.contains("Cash", true) == true || ledger?.group?.contains("Stock", true) == true
    val showBillWise = ledger?.maintainBillWise == true && !isCashOrStock
    
    // Auto-calculate amount if bill-wise
    val myAllocations = billAllocations.filter { it.voucherEntryId == entry.ledgerId }
    
    LaunchedEffect(entry.ledgerId, showBillWise) {
        if (showBillWise && entry.ledgerId != 0) {
             // Auto-add default allocation if empty
             if (myAllocations.isEmpty()) {
                 billAllocations.add(BillAllocation(
                     voucherEntryId = entry.ledgerId, 
                     refType = "New Ref", 
                     refName = voucherNumber, // Default to Voucher No
                     amount = 0L
                 ))
             }
        }
    }
    
    LaunchedEffect(myAllocations, showBillWise) {
        if (showBillWise) {
            val total = myAllocations.sumOf { it.amount }
            if (total != entry.amount) {
                onAmountChange(total.paiseToRupees())
            }
        }
    }

    var amountText by remember(entry.id, entry.ledgerId, entry.amount) { 
        mutableStateOf(if (entry.amount == 0L) "" else entry.amount.paiseToRupees().toString()) 
    }

    LaunchedEffect(entry.amount) {
        val current = amountText.toDoubleOrNull() ?: 0.0
        if (kotlin.math.abs(current - entry.amount.paiseToRupees()) > 0.001) {
            amountText = if (entry.amount == 0L) "" else entry.amount.paiseToRupees().toString()
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ledger Selection Box - takes more space when bill-wise is active
            ModernDropdownField(
                text = ledger?.name ?: emptyText,
                onClick = onLedgerClick,
                modifier = Modifier.weight(if (showBillWise) 0.9f else 0.55f).height(56.dp),
                placeholder = emptyText,
                showDropdownIcon = false,
                isLightTheme = true
            )

            // Amount Box - HIDDEN when bill-wise is enabled (amount goes in rows below)
            if (!showBillWise) {
                Spacer(modifier = Modifier.width(4.dp))

                ValidatedNumberTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        onAmountChange(it.toDoubleOrNull() ?: 0.0)
                    },
                    label = "₹",
                    modifier = Modifier.weight(0.35f),
                    isDecimal = true,
                    isLightTheme = true
                )
            }

            // Plus Sign
            if (onAddClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                        .clickable(onClick = onAddClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, TerminologyManager.getTerm("Add"), tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }

            Spacer(modifier = Modifier.width(2.dp))
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Delete, TerminologyManager.getTerm("Delete"), tint = Color(0xFFE53935), modifier = Modifier.size(17.dp))
            }
        }
        
        // Show total amount as text when bill-wise is active
        if (showBillWise && entry.amount > 0) {
            AutoShrinkText(
                text = "${TerminologyManager.getTerm("Total:")} ₹${String.format("%.2f", entry.amount.paiseToRupees())}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF32194D),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }

        // Bill Allocations Sub-List
        if (showBillWise) {
            Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                myAllocations.forEachIndexed { index, allocation ->
                    BillAllocationRow(
                        allocation = allocation,
                        onChange = { updated ->
                            val idxVal = billAllocations.indexOf(allocation)
                            if (idxVal != -1) {
                                billAllocations[idxVal] = updated
                            }
                        },
                        onDelete = {
                             billAllocations.remove(allocation)
                        },
                        onPickRef = onShowOutstanding,
                        onAddClick = if (index == myAllocations.lastIndex) {
                            { billAllocations.add(BillAllocation(voucherEntryId = entry.ledgerId, refType = "New Ref", refName = "", amount = 0L)) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
fun BillAllocationTypeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) Color(0xFF32194D) else Color.White
    val contentColor = if (isSelected) Color.White else Color(0xFF32194D)
    val borderColor = if (isSelected) Color.Transparent else Color(0xFF32194D).copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                lineHeight = 11.sp
            )
        }
    }
}

@Composable
fun BillAllocationRow(
    allocation: BillAllocation,
    onChange: (BillAllocation) -> Unit,
    onDelete: () -> Unit,
    onPickRef: (() -> Unit)? = null,
    onAddClick: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.6f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF32194D).copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Row 1: Three Selectable Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val currentRefType = allocation.refType ?: "New Ref"
                    
                    BillAllocationTypeButton(
                        label = TerminologyManager.getTerm("Against"),
                        icon = Icons.Default.Description,
                        isSelected = currentRefType == "Agst Ref",
                        onClick = {
                            onChange(allocation.copy(refType = "Agst Ref"))
                            if (onPickRef != null) {
                                onPickRef()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    BillAllocationTypeButton(
                        label = TerminologyManager.getTerm("Advance"),
                        icon = Icons.Default.Payments,
                        isSelected = currentRefType == "New Ref",
                        onClick = {
                            onChange(allocation.copy(refType = "New Ref"))
                        },
                        modifier = Modifier.weight(1f)
                    )

                    BillAllocationTypeButton(
                        label = TerminologyManager.getTerm("General Payment"),
                        icon = Icons.Default.CreditCard,
                        isSelected = currentRefType == "On Account",
                        onClick = {
                            onChange(allocation.copy(refType = "On Account"))
                        },
                        modifier = Modifier.weight(1.1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Row 2: Note and Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModernTextField(
                        value = allocation.refName ?: "",
                        onValueChange = { onChange(allocation.copy(refName = it)) },
                        placeholder = TerminologyManager.getTerm("Note (e.g. Bill #123)"),
                        modifier = Modifier.weight(1f),
                        isLightTheme = true
                    )
                    
                    ValidatedNumberTextField(
                        value = if (allocation.amount == 0L) "" else allocation.amount.paiseToRupees().toString(),
                        onValueChange = { str ->
                            val amt = str.parseToPaiseOrZero()
                            onChange(allocation.copy(amount = amt))
                        },
                        label = TerminologyManager.getTerm("Amount (₹)"),
                        modifier = Modifier.weight(1f),
                        isDecimal = true,
                        isLightTheme = true
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Bottom Row: Trash and Add Another Entry
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = TerminologyManager.getTerm("Remove Reference"),
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(18.dp)
                )
            }
            
            if (onAddClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onAddClick,
                    modifier = Modifier.height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(TerminologyManager.getTerm("+ Add Another Entry"), fontSize = 13.sp, color = Color(0xFF32194D), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatQty(q: Double): String {
    return if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()
}

@Composable
fun InventoryEntryRow(
    entry: InventoryEntry,
    stockItemViewModel: StockItemViewModel,
    onItemClick: () -> Unit,
    onQtyChange: (Double) -> Unit,
    onRateChange: (Double) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRateReadOnly: Boolean = false
) {
    val stockItem by stockItemViewModel.allStockItems.collectAsState(initial = emptyList())
    val item = stockItem.find { it.id == entry.stockItemId }
    
    var qtyText by remember(entry.id, entry.stockItemId) {
        mutableStateOf(if (entry.quantity == 0.0) "" else formatQty(entry.quantity))
    }
    var rateText by remember(entry.id, entry.stockItemId, isRateReadOnly) {
        mutableStateOf(if (entry.rate == 0.0) "" else entry.rate.toString())
    }

    LaunchedEffect(entry.quantity) {
        val current = qtyText.toDoubleOrNull() ?: 0.0
        if (kotlin.math.abs(current - entry.quantity) > 0.001) {
            qtyText = if (entry.quantity == 0.0) "" else formatQty(entry.quantity)
        }
    }
    LaunchedEffect(entry.rate) {
        val current = rateText.toDoubleOrNull() ?: 0.0
        if (kotlin.math.abs(current - entry.rate) > 0.001) {
            rateText = if (entry.rate == 0.0) "" else String.format("%.2f", entry.rate)
        }
    }

    Row(
        modifier = modifier.padding(vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModernDropdownField(
            text = item?.name ?: "",
            onClick = onItemClick,
            modifier = Modifier.weight(1f).height(56.dp),
            placeholder = TerminologyManager.getTerm("ITEM"),
            showDropdownIcon = false,
            isLightTheme = true
        )

        ValidatedNumberTextField(
            value = qtyText,
            onValueChange = {
                qtyText = it
                onQtyChange(it.toDoubleOrNull() ?: 0.0)
            },
            label = TerminologyManager.getTerm("QTY"),
            modifier = Modifier.width(80.dp),
            isDecimal = true,
            isLightTheme = true
        )

        ValidatedNumberTextField(
            value = rateText,
            onValueChange = {
                if (!isRateReadOnly) {
                    rateText = it
                    onRateChange(it.toDoubleOrNull() ?: 0.0)
                }
            },
            label = if (isRateReadOnly) TerminologyManager.getTerm("AUTO RATE") else TerminologyManager.getTerm("RATE"),
            readOnly = isRateReadOnly,
            modifier = Modifier.width(90.dp),
            isDecimal = true,
            isLightTheme = true
        )

        IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, TerminologyManager.getTerm("Delete"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        }
    }
}

private fun Modifier.floatingDropShadow(borderRadius: androidx.compose.ui.unit.Dp = 23.dp): Modifier = this.drawBehind {
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

private fun Modifier.neumorphicShadow(
    shape: androidx.compose.ui.graphics.Shape,
    darkShadowColor: Color = Color(0xFFA3B1C6),
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

        val outline = shape.createOutline(size, layoutDirection, this)

        // Draw Dark Shadow (Bottom-Right offset, positive X and Y)
        canvas.save()
        canvas.translate(offsetPx, offsetPx)
        canvas.drawOutline(outline, paintDark)
        canvas.restore()
    }
}

data class TopThemeColors(
    val inOutOutline: List<Color>,
    val dateBorder: Color,
    val dateTint: Color,
    val dateText: Color,
    val voucherBorder: Color,
    val voucherTint: Color,
    val voucherText: Color
)


