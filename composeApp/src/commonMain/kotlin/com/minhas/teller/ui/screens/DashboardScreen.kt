package com.minhas.teller.ui.screens

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.theme.*
import com.minhas.teller.ui.viewmodels.DashboardViewModel
import com.minhas.teller.ui.viewmodels.DashboardViewModelFactory
import com.minhas.teller.ui.viewmodels.HelafinNudge
import com.minhas.teller.ui.viewmodels.DashboardAttentionViewModel
import com.minhas.teller.ui.viewmodels.DashboardAttentionViewModelFactory
import com.minhas.teller.ui.viewmodels.AttentionItem
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.components.InteractiveTutorialOverlay
import com.minhas.teller.utils.TutorialManager
import com.minhas.teller.utils.TutorialStep
import com.minhas.teller.ui.components.TutorialTooltip
import androidx.compose.foundation.ExperimentalFoundationApi
import com.minhas.teller.utils.LicenseUtils
import com.minhas.teller.ui.components.EducationalModeBanner
import com.minhas.teller.utils.DashboardPrefs
import com.minhas.teller.ui.components.CustomizeDashboardDialog
import com.minhas.teller.ui.components.NeedsAttentionCard
import com.minhas.teller.ui.components.OverviewCardContent
import com.minhas.teller.ui.components.OverviewCardData
import com.minhas.teller.ui.components.OverviewModePickerDialog
import com.minhas.teller.ui.components.ModernActionCard
import com.minhas.teller.utils.FinanceHelper
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.minhas.teller.ui.screens.ModernDashboardAction
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.utils.WarningsPrefs
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.automirrored.filled.TrendingDown

@Composable
fun DashboardScreen(navController: NavController) {
    DashboardContent(navController, isLightMode = false)
}

@Composable
fun DashboardModernLight(navController: NavController) {
    DashboardContent(navController, isLightMode = true)
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DashboardContent(navController: NavController, isLightMode: Boolean) {
    val context = LocalContext.current
    
    // --- LICENSE CHECK ---
    var appStatus by remember { mutableStateOf<Pair<LicenseUtils.AppStatus, Int>?>(null) }
    LaunchedEffect(Unit) {
        val status = LicenseUtils.getAppStatus(context)
        appStatus = status
        // Educational Mode: Don't redirect, show banner instead
        
        // Start Tutorial Check
        TutorialManager.startTutorial(context)
    }
    
    // --- PERMISSION REQUEST FOR NOTIFICATIONS (Android 13+) ---
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                // Optionally handle denial, but for now just asking is enough
            }
        )
        LaunchedEffect(Unit) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    val db = AppDatabase.getDatabase(context)
    val repository = remember {
        VoucherRepository(db.voucherDao(), db.voucherEntryDao(), db.inventoryEntryDao(), db.billAllocationDao(), db.auditLogDao(), db.ledgerDao(), db = db)
    }
    val ledgerRepository = remember { LedgerRepository(db.ledgerDao()) }
    val stockItemDao = db.stockItemDao()
    val factory = remember { DashboardViewModelFactory(repository, ledgerRepository, stockItemDao) }
    val viewModel: DashboardViewModel = viewModel(factory = factory)

    val attentionFactory = remember { DashboardAttentionViewModelFactory(repository, ledgerRepository) }
    val attentionViewModel: DashboardAttentionViewModel = viewModel(factory = attentionFactory)
    val attentionState by attentionViewModel.state.collectAsState()

    val currentCompanyId by SessionManager.currentCompanyId.collectAsState(initial = null)
    val currentCompany by remember(currentCompanyId) {
        if (currentCompanyId != null) {
            db.companyDao().getCompanyById(currentCompanyId!!)
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }.collectAsState(initial = null)
    val currencySymbol = currentCompany?.currencySymbol ?: "₹"

    val isServerRunning by com.minhas.teller.web.WebCompanionManager.isServerRunning.collectAsState()
    val serverUrl by com.minhas.teller.web.WebCompanionManager.serverUrl.collectAsState()

    LaunchedEffect(currentCompanyId) {
        currentCompanyId?.let { id ->
            viewModel.loadDashboardData(id, context)
            attentionViewModel.loadAttentionItems(id, WarningsPrefs.getIgnored(context))
        }
    }

    val totalSales by viewModel.totalSales.collectAsState()
    val totalPurchase by viewModel.totalPurchase.collectAsState()
    val totalReceipt by viewModel.totalReceipt.collectAsState()
    val totalPayment by viewModel.totalPayment.collectAsState()
    val totalReceivables by viewModel.totalReceivables.collectAsState()
    val totalPayables by viewModel.totalPayables.collectAsState()
    val cashInHand by viewModel.cashInHand.collectAsState()
    val bankBalance by viewModel.bankBalance.collectAsState()
    val healthCardText by viewModel.healthCardText.collectAsState()
    val helafinNudges by viewModel.helafinNudges.collectAsState()
    val lastMonthSales by viewModel.lastMonthSales.collectAsState()
    val lastMonthPurchase by viewModel.lastMonthPurchase.collectAsState()
    val lastMonthReceipt by viewModel.lastMonthReceipt.collectAsState()
    val lastMonthPayment by viewModel.lastMonthPayment.collectAsState()
    val lastYearSales by viewModel.lastYearSales.collectAsState()
    val lastYearPurchase by viewModel.lastYearPurchase.collectAsState()
    val salesTrend by viewModel.salesTrend.collectAsState()

    // Overview card mode
    var overviewMode by remember { mutableStateOf(DashboardPrefs.getOverviewMode(context, currentCompanyId)) }
    var showModePicker by remember { mutableStateOf(false) }

    // Persistent Visibility State
    var visibleMetrics by remember { mutableStateOf(DashboardPrefs.getVisibleMetrics(context)) }
    var showCustomizeDialog by remember { mutableStateOf(false) }
    var showVoiceBillingDialog by remember { mutableStateOf(false) }

    // Tutorial State
    val isTutorialActive = TutorialManager.isTutorialActive
    val currentStep = TutorialManager.currentStep

    if (isTutorialActive && currentStep == TutorialStep.WELCOME) {
        InteractiveTutorialOverlay(
            currentStep = TutorialStep.WELCOME,
            onNext = { TutorialManager.advanceStep() },
            onSkip = { TutorialManager.skipTutorial(context) }
        )
    }

    if (showCustomizeDialog) {
        CustomizeDashboardDialog(
            currentSelection = visibleMetrics,
            onDismiss = { showCustomizeDialog = false },
            onSave = { newSelection ->
                visibleMetrics = newSelection
                DashboardPrefs.setVisibleMetrics(context, newSelection)
                showCustomizeDialog = false
            }
        )
    }

    if (showModePicker) {
        OverviewModePickerDialog(
            currentMode = overviewMode,
            onModeSelected = { mode ->
                overviewMode = mode
                DashboardPrefs.setOverviewMode(context, mode, currentCompanyId)
                showModePicker = false
            },
            onDismiss = { showModePicker = false }
        )
    }

    // --- OFFLINE AI DOWNLOAD STATUS ---
    // Removed MLC Download tracking
    val isDownloading = false
    val isDownloadFailed = false
    val downloadProgress = 0

    // --- FORCE RESTART DOWNLOAD IF STUCK ---
    // MediaPipe auto-download is handled in MainActivity on launch


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBackgroundBrush)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) {
                            com.minhas.teller.ui.components.VoiceTooltipManager.dismiss(context)
                        }
                    }
                }
            }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
        ) { paddingValues ->
            val items = listOf(
                ModernDashboardAction(
                    TerminologyManager.getTerm("Add New Entry"),
                    TerminologyManager.getTerm("Sales, Purchase, Payment"),
                    Icons.Outlined.Receipt,
                    Screen.EntrySelection.route,
                    DashboardVoucherGradient
                ),
                ModernDashboardAction(
                    TerminologyManager.getTerm("Display All Entries"),
                    TerminologyManager.getTerm("Daybook, Reports"),
                    Icons.AutoMirrored.Outlined.Assignment,
                    Screen.Reports.route,
                    DashboardDisplayEntriesGradient
                ),
                ModernDashboardAction(
                    TerminologyManager.getTerm("Accounts Info"),
                    TerminologyManager.getTerm("Groups, Ledgers & more"),
                    Icons.Outlined.AccountBalance,
                    Screen.AccountsInfo.route,
                    DashboardAccountsGradient
                ),
                ModernDashboardAction(
                    TerminologyManager.getTerm("Inventory"),
                    TerminologyManager.getTerm("Stock Items, Units"),
                    Icons.Outlined.Inventory2,
                    Screen.StockItemList.route,
                    DashboardInventoryGradient
                ),
                ModernDashboardAction(
                    TerminologyManager.getTerm("AI Assistant"),
                    TerminologyManager.getTerm("Chat with Finance"),
                    Icons.Outlined.AutoAwesome,
                    Screen.Chat.route,
                    DashboardAiHelperGradient
                ),
                ModernDashboardAction(
                    TerminologyManager.getTerm("Live Stock"),
                    TerminologyManager.getTerm("Live Updated Stock"),
                    Icons.Outlined.DirectionsCar,
                    Screen.LiveStockManagement.route,
                    DashboardLiveStockGradient
                ),
                ModernDashboardAction(
                    TerminologyManager.getTerm("Daily Tasks"),
                    TerminologyManager.getTerm("Staff Checklists & Reports"),
                    Icons.Outlined.Checklist,
                    Screen.DailyTasks.route,
                    DashboardDailyTasksGradient
                ),
                ModernDashboardAction(
                    TerminologyManager.getTerm("Quotes / Estimates"),
                    TerminologyManager.getTerm("Create Quotations"),
                    Icons.Outlined.EditNote,
                    Screen.QuotationList.route,
                    DashboardQuotesGradient
                ),

                ModernDashboardAction(
                    TerminologyManager.getTerm("Utilities"),
                    TerminologyManager.getTerm("Printer, Barcodes & Tools"),
                    Icons.Outlined.Build,
                    Screen.Utilities.route,
                    Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0091EA)))
                ),
                ModernDashboardAction(
                    TerminologyManager.getTerm("Help"),
                    TerminologyManager.getTerm("Guide & Manual"),
                    Icons.AutoMirrored.Outlined.HelpOutline,
                    Screen.Help.route,
                    DashboardSupportGradient
                )
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(paddingValues)
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(bottom = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header — full width
                item(span = { GridItemSpan(2) }) {
                    ModernGatewayHeader(navController, isLightMode, onVoiceBillingClick = { showVoiceBillingDialog = true })
                }

                if (appStatus?.first == LicenseUtils.AppStatus.TRIAL_EXPIRED) {
                    item(span = { GridItemSpan(2) }) {
                        EducationalModeBanner(
                            onUpgrade = { navController.navigate(Screen.License.route) }
                        )
                    }
                }

                // Trial banner — full width
                if (appStatus?.first == LicenseUtils.AppStatus.TRIAL_ACTIVE) {
                    item(span = { GridItemSpan(2) }) {
                        val days = appStatus?.second ?: 0
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate(Screen.License.route) }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timelapse, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Free Trial: $days days left. Tap to Upgrade Now.",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // HeroCard — interactive multi-view overview
                item(span = { GridItemSpan(2) }) {
                    val cardData = OverviewCardData(
                        mode = overviewMode,
                        sales = totalSales, purchase = totalPurchase,
                        receipt = totalReceipt, payment = totalPayment,
                        receivables = totalReceivables, payables = totalPayables,
                        cash = cashInHand, bank = bankBalance,
                        lastMonthSales = lastMonthSales, lastMonthPurchase = lastMonthPurchase,
                        lastMonthReceipt = lastMonthReceipt, lastMonthPayment = lastMonthPayment,
                        lastYearSales = lastYearSales, lastYearPurchase = lastYearPurchase,
                        currencySymbol = currencySymbol,
                        salesTrend = salesTrend,
                        onPrintInvoicesClick = { navController.navigate(Screen.TodayInvoices.route) },
                        onPendingPaymentsClick = { navController.navigate(Screen.OutstandingPaymentsSummary.route.replace("?group={group}", "")) }
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .pointerInput(Unit) { detectTapGestures(onLongPress = { showCustomizeDialog = true }) }
                            .clickable { showModePicker = true },
                        shape = RoundedCornerShape(28.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(Brush.linearGradient(listOf(Color(0xFF1E2D4A).copy(alpha = 0.85f), Color(0xFF0F182E).copy(alpha = 0.95f))))
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    AutoShrinkText(TerminologyManager.getTerm("THIS MONTH'S OVERVIEW"), color = Color.White.copy(alpha = 0.9f), maxFontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    AutoShrinkText(overviewMode.displayName, color = Color.White.copy(alpha = 0.4f), maxFontSize = 10.sp, minFontSize = 7.sp, fontWeight = FontWeight.Medium)
                                }
                                Spacer(Modifier.height(10.dp))
                                OverviewCardContent(data = cardData)
                            }
                        }
                    }
                }

                // Action grid items
                items(items) { item ->
                    val isAiItem = item.title == TerminologyManager.getTerm("AI Assistant")
                    val isVoucherItem = item.title == TerminologyManager.getTerm("Vouchers")
                    val isReportsItem = item.title == TerminologyManager.getTerm("Display All Entries")

                    val isHighlight = isTutorialActive && (
                        (currentStep == TutorialStep.DASHBOARD_HIGHLIGHT_VOUCHER && isVoucherItem) ||
                        (currentStep == TutorialStep.DASHBOARD_HIGHLIGHT_REPORTS && isReportsItem) ||
                        (currentStep == TutorialStep.DASHBOARD_HIGHLIGHT_AI && isAiItem)
                    )

                    Box {
                        ModernActionCard(
                            item = item,
                            navController = navController,
                            isLightMode = isLightMode,
                            isDownloading = isAiItem && isDownloading,
                            downloadProgress = if (isAiItem) downloadProgress else 0,
                            isDownloadFailed = isAiItem && isDownloadFailed,
                            onClick = when {
                                item.route == "action_voice_billing" -> {
                                    { showVoiceBillingDialog = true }
                                }
                                isHighlight -> {
                                    {
                                        TutorialManager.advanceStep()
                                        navController.navigate(item.route)
                                    }
                                }
                                else -> null
                            }
                        )

                        if (isHighlight) {
                            Box(modifier = Modifier
                                .matchParentSize()
                                .border(4.dp, Color(0xFFFFD700), RoundedCornerShape(24.dp))
                            )
                        }
                    }
                }

                if (helafinNudges.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            text = TerminologyManager.getTerm("Helafin Alerts & Insights"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isLightMode) Color.Black else Color.White,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
                        )
                    }
                    items(helafinNudges.size, span = { GridItemSpan(2) }) { index ->
                        val nudge = helafinNudges[index]
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when (nudge.type) {
                                    "NEGATIVE_MARGIN" -> Color(0xFFD32F2F).copy(alpha = 0.15f)
                                    "PENDING_RECEIVABLES" -> Color(0xFFF57C00).copy(alpha = 0.15f)
                                    else -> Color(0xFF1976D2).copy(alpha = 0.15f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    1.dp,
                                    when (nudge.type) {
                                        "NEGATIVE_MARGIN" -> Color(0xFFEF5350).copy(alpha = 0.3f)
                                        "PENDING_RECEIVABLES" -> Color(0xFFFFB74D).copy(alpha = 0.3f)
                                        else -> Color(0xFF64B5F6).copy(alpha = 0.3f)
                                    },
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    when (nudge.type) {
                                        "NEGATIVE_MARGIN" -> navController.navigate(Screen.ItemProfitability.route)
                                        "PENDING_RECEIVABLES" -> navController.navigate("outstanding_payments_summary?group=Sundry Debtors")
                                        "LOW_STOCK" -> navController.navigate(Screen.StockSummaryReport.route)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (nudge.type) {
                                        "NEGATIVE_MARGIN" -> Icons.AutoMirrored.Filled.TrendingDown
                                        "PENDING_RECEIVABLES" -> Icons.Default.Warning
                                        else -> Icons.Default.Inventory
                                    },
                                    contentDescription = null,
                                    tint = when (nudge.type) {
                                        "NEGATIVE_MARGIN" -> Color(0xFFEF5350)
                                        "PENDING_RECEIVABLES" -> Color(0xFFFFB74D)
                                        else -> Color(0xFF64B5F6)
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = nudge.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = if (isLightMode) Color.Black else Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = nudge.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = (if (isLightMode) Color.Black else Color.White).copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Needs Attention Card
                item(span = { GridItemSpan(2) }) {
                    NeedsAttentionCard(
                        items = attentionState.items,
                        totalIssues = attentionState.totalIssues,
                        isLoading = attentionState.isLoading,
                        onItemClick = { item ->
                            navController.navigate(Screen.WarningDetail.route.replace("{warningId}", item.id))
                        }
                    )
                }

                // Connect & Sync section
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = "Connect & Sync",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isLightMode) Color.Black else Color.White,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
                    )
                }
                item(span = { GridItemSpan(2) }) {
                    DesktopCompanionBannerCard(
                        isServerRunning = isServerRunning,
                        serverUrl = serverUrl,
                        onClick = { navController.navigate(Screen.DesktopCompanion.route) }
                    )
                }
                item(span = { GridItemSpan(2) }) {
                    CloudSyncBannerCard(
                        onClick = { navController.navigate(Screen.CloudSync.route) }
                    )
                }
            }
        }
        
        // Tutorial Tooltips
        if (isTutorialActive) {
            when (currentStep) {
                TutorialStep.DASHBOARD_HIGHLIGHT_VOUCHER -> {
                    TutorialTooltip(
                        text = TerminologyManager.getTerm("Step 1: Create Voucher\nTap 'Vouchers' to record your first transaction."),
                        onNext = {}, // Blocking
                        modifier = Modifier.padding(bottom = 0.dp)
                    )
                }
                TutorialStep.DASHBOARD_HIGHLIGHT_REPORTS -> {
                    TutorialTooltip(
                        text = TerminologyManager.getTerm("Step 2: Reports\nTap here to view your Daybook and financial reports."),
                        onNext = {},
                        modifier = Modifier.padding(bottom = 0.dp)
                    )
                }
                TutorialStep.DASHBOARD_HIGHLIGHT_AI -> {
                    TutorialTooltip(
                        text = TerminologyManager.getTerm("Step 3: AI Assistant\nTap here to use voice commands and automation."),
                        onNext = {},
                        modifier = Modifier.padding(bottom = 0.dp)
                    )
                }
                else -> {}
            }
        }

        if (showVoiceBillingDialog) {
            com.minhas.teller.ui.components.VoiceBillingDialog(
                onDismiss = { showVoiceBillingDialog = false }
            )
        }
    }
}

@Composable
fun ModernGatewayHeader(
    navController: NavController,
    isLightMode: Boolean,
    onVoiceBillingClick: () -> Unit = {}
) {
    val textColor = if (isLightMode) Color.Black else Color.White
    var showMenu by remember { mutableStateOf(false) }
    val isServerRunning by com.minhas.teller.web.WebCompanionManager.isServerRunning.collectAsState()
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                AutoShrinkText(
                    text = TerminologyManager.getTerm("Gateway of "),
                    color = textColor,
                    maxFontSize = 22.sp,
                    fontWeight = FontWeight.Normal
                )
                AutoShrinkText(
                    text = "Teller",
                    color = textColor,
                    maxFontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                imageVector = Icons.Default.Laptop,
                contentDescription = "PC Desktop Companion",
                tint = if (isServerRunning) Color(0xFF10B981) else textColor,
                modifier = Modifier.size(24.dp).clickable { navController.navigate(Screen.DesktopCompanion.route) }
            )
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = TerminologyManager.getTerm("Info"),
                tint = textColor,
                modifier = Modifier.size(24.dp).clickable { navController.navigate(Screen.About.route) }
            )
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = TerminologyManager.getTerm("Settings"),
                tint = textColor,
                modifier = Modifier.size(24.dp).clickable { navController.navigate(Screen.Settings.route) }
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = TerminologyManager.getTerm("Logout"),
                tint = textColor,
                modifier = Modifier.size(24.dp).clickable { 
                    SessionManager.clearSession()
                    navController.navigate(Screen.CompanyList.route) { popUpTo(0) }
                }
            )
            Box {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = TerminologyManager.getTerm("More"),
                    tint = textColor,
                    modifier = Modifier.size(24.dp).clickable { showMenu = true }
                )

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // Was previously only reachable via a banner that just showed for
                    // TRIAL_EXPIRED users — added here so it's always findable regardless of
                    // license status, same as the entries in About and Settings.
                    DropdownMenuItem(
                        text = { Text(TerminologyManager.getTerm("Cloud Sync")) },
                        leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFF2979FF)) },
                        onClick = {
                            showMenu = false
                            navController.navigate(Screen.CloudSync.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(TerminologyManager.getTerm("AI Voice Billing")) },
                        leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF00E676)) },
                        onClick = {
                            showMenu = false
                            onVoiceBillingClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(TerminologyManager.getTerm("Manage Subscription")) },
                        leadingIcon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            navController.navigate(Screen.License.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(TerminologyManager.getTerm("Add Digital Signature")) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            navController.navigate(Screen.SignatureDrawing.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(TerminologyManager.getTerm("Business Profile & Stamp")) },
                        leadingIcon = { Icon(Icons.Default.BusinessCenter, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            navController.navigate(Screen.EditCompany.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(TerminologyManager.getTerm("App Settings")) },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            navController.navigate(Screen.Settings.route)
                        }
                    )
                }
            }
        }
    }
}



// Legacy ActionCard for compatibility
@Composable
fun ActionCard(item: DashboardAction, navController: NavController) {
    val modernItem = ModernDashboardAction(
        title = item.title,
        subtitle = item.subtitle,
        icon = item.icon,
        route = item.route,
        gradient = androidx.compose.ui.graphics.SolidColor(item.color)
    )
    ModernActionCard(item = modernItem, navController = navController, isLightMode = false)
}

@Composable
fun DesktopCompanionBannerCard(
    isServerRunning: Boolean,
    serverUrl: String?,
    onClick: () -> Unit
) {
    val pendingPair by com.minhas.teller.web.WebCompanionManager.pendingPair.collectAsState()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isServerRunning) Color(0xFF0C2419) else Color(0xFF141E33)
        ),
        border = BorderStroke(
            1.5.dp,
            if (isServerRunning) Color(0xFF10B981).copy(alpha = 0.8f) else Color(0xFF38BDF8).copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isServerRunning) Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
                            else Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF2563EB)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Laptop,
                        contentDescription = "Desktop Pro",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AutoShrinkText(
                            text = if (isServerRunning) "PC Desktop Mode Live" else "Open on PC / Laptop",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            maxFontSize = 15.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isServerRunning) Color(0xFF10B981) else Color(0xFF38BDF8)
                        ) {
                            AutoShrinkText(
                                text = if (isServerRunning) "ACTIVE" else "PRO",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                maxFontSize = 9.sp,
                                minFontSize = 6.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    AutoShrinkText(
                        text = if (isServerRunning && pendingPair != null) "A computer is waiting - tap to allow"
                        else if (isServerRunning && serverUrl != null) serverUrl.substringBefore("?").removePrefix("http://")
                        else "Fast mechanical keyboard accounting in PC Chrome",
                        color = if (isServerRunning) Color(0xFF34D399) else Color.White.copy(alpha = 0.7f),
                        maxFontSize = 12.sp,
                        minFontSize = 9.sp,
                        fontWeight = if (isServerRunning) FontWeight.Bold else FontWeight.Normal,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = if (isServerRunning) androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.Default
                        )
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isServerRunning) Color(0xFF10B981) else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun CloudSyncBannerCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141E33)),
        border = BorderStroke(1.5.dp, Color(0xFF7C4DFF).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF2979FF), Color(0xFF7C4DFF)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Cloud Sync",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AutoShrinkText(
                            text = "Cloud Sync",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            maxFontSize = 15.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF7C4DFF)
                        ) {
                            AutoShrinkText(
                                text = "Backup",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                maxFontSize = 9.sp,
                                minFontSize = 6.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    AutoShrinkText(
                        text = "Sign in to back up & sync sales across phones",
                        color = Color.White.copy(alpha = 0.7f),
                        maxFontSize = 12.sp,
                        minFontSize = 9.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

