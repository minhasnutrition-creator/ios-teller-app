package com.minhas.teller.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.*
import com.minhas.teller.ui.screens.ModernGatewayHeader
import com.minhas.teller.ui.screens.ModernDashboardAction
import com.minhas.teller.ui.screens.DesktopCompanionBannerCard
import com.minhas.teller.ui.screens.CloudSyncBannerCard
import com.minhas.teller.ui.theme.*
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.utils.WarningsPrefs
import com.minhas.teller.ui.viewmodels.DashboardViewModel
import com.minhas.teller.ui.viewmodels.DashboardViewModelFactory
import com.minhas.teller.SessionManager
import com.minhas.teller.ui.viewmodels.DashboardAttentionViewModel
import com.minhas.teller.ui.viewmodels.DashboardAttentionViewModelFactory
import com.minhas.teller.ui.viewmodels.AttentionItem
import com.minhas.teller.ui.components.OverviewCardContent
import com.minhas.teller.ui.components.OverviewCardData
import com.minhas.teller.ui.components.OverviewModePickerDialog
import com.minhas.teller.utils.DashboardPrefs
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.minhas.teller.ui.components.CustomizeDashboardDialog

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.HelpOutline

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardCrystal(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val voucherRepository = remember {
        VoucherRepository(db.voucherDao(), db.voucherEntryDao(), db.inventoryEntryDao(), db.billAllocationDao(), db.auditLogDao(), db.ledgerDao(), db = db)
    }
    val ledgerRepository = remember { LedgerRepository(db.ledgerDao()) }
    val stockItemDao = db.stockItemDao()
    val factory = remember { DashboardViewModelFactory(voucherRepository, ledgerRepository, stockItemDao) }
    val viewModel: DashboardViewModel = viewModel(factory = factory)

    val attentionFactory = remember { DashboardAttentionViewModelFactory(voucherRepository, ledgerRepository) }
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

    LaunchedEffect(currentCompanyId) {
        currentCompanyId?.let { id ->
            viewModel.loadDashboardData(id, context)
            attentionViewModel.loadAttentionItems(id, WarningsPrefs.getIgnored(context))
        }
    }

    val totalSales by viewModel.totalSales.collectAsState(initial = 0.0)
    val totalPurchase by viewModel.totalPurchase.collectAsState(initial = 0.0)
    val totalReceipt by viewModel.totalReceipt.collectAsState(initial = 0.0)
    val totalPayment by viewModel.totalPayment.collectAsState(initial = 0.0)
    val totalReceivables by viewModel.totalReceivables.collectAsState(initial = 0.0)
    val totalPayables by viewModel.totalPayables.collectAsState(initial = 0.0)
    val cashInHand by viewModel.cashInHand.collectAsState(initial = 0.0)
    val bankBalance by viewModel.bankBalance.collectAsState(initial = 0.0)
    val lastMonthSales by viewModel.lastMonthSales.collectAsState(initial = 0.0)
    val lastMonthPurchase by viewModel.lastMonthPurchase.collectAsState(initial = 0.0)
    val lastMonthReceipt by viewModel.lastMonthReceipt.collectAsState(initial = 0.0)
    val lastMonthPayment by viewModel.lastMonthPayment.collectAsState(initial = 0.0)
    val lastYearSales by viewModel.lastYearSales.collectAsState(initial = 0.0)
    val lastYearPurchase by viewModel.lastYearPurchase.collectAsState(initial = 0.0)
    val salesTrend by viewModel.salesTrend.collectAsState()

    val isServerRunning by com.minhas.teller.web.WebCompanionManager.isServerRunning.collectAsState()
    val serverUrl by com.minhas.teller.web.WebCompanionManager.serverUrl.collectAsState()

    var overviewMode by remember { mutableStateOf(DashboardPrefs.getOverviewMode(context, currentCompanyId)) }
    var showModePicker by remember { mutableStateOf(false) }
    val currencySymbol = currentCompany?.currencySymbol ?: "₹"

    // Persistent Visibility State
    var visibleMetrics by remember { mutableStateOf(DashboardPrefs.getVisibleMetrics(context)) }
    var showCustomizeDialog by remember { mutableStateOf(false) }

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

    val items = listOf(
        ModernDashboardAction(
            TerminologyManager.getTerm("Vouchers"),
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
            TerminologyManager.getTerm("Inventory Info"),
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
            TerminologyManager.getTerm("Showroom", "Live Updated Stock"),
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
            TerminologyManager.getTerm("Estimates"),
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

    val crystalDrawables = listOf(
        com.minhas.teller.R.drawable.gem_amethyst,
        com.minhas.teller.R.drawable.gem_carnelian,
        com.minhas.teller.R.drawable.gem_malachite,
        com.minhas.teller.R.drawable.gem_lapis,
        com.minhas.teller.R.drawable.gem_jasper,
        com.minhas.teller.R.drawable.gem_tiger_eye,
        com.minhas.teller.R.drawable.gem_rose_quartz,
        com.minhas.teller.R.drawable.gem_jade
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val bottomContentPadding = padding.calculateBottomPadding()
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = bottomContentPadding + 100.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                ModernGatewayHeader(navController, isLightMode = false)
            }
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
                val cardShape = RoundedCornerShape(22.dp)
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { showModePicker = true }, onLongClick = { showCustomizeDialog = true })
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(220.dp).offset(y = (-4).dp).clip(cardShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF1E2D4A).copy(alpha = 0.85f), Color(0xFF0F182E).copy(alpha = 0.95f))))
                            .border(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.45f))), cardShape)
                            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                AutoShrinkText(TerminologyManager.getTerm("THIS MONTH'S OVERVIEW"), color = Color.White.copy(alpha = 0.9f), maxFontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                AutoShrinkText(overviewMode.displayName, color = Color(0xFFB8A0D0), maxFontSize = 10.sp, minFontSize = 7.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            OverviewCardContent(data = cardData)
                        }
                    }
                }
            }
            itemsIndexed(items) { index, item ->
                val drawableResId = crystalDrawables.getOrElse(index) { com.minhas.teller.R.drawable.gem_amethyst }
                CrystalActionCard(
                    item = item,
                    navController = navController,
                    drawableResId = drawableResId,
                    isLightMode = false
                )
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
                    color = Color.White,
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
}

// OctagonShape represents the emerald-cut gemstone shape
class OctagonShape(private val cornerCut: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val w = size.width
            val h = size.height
            val c = cornerCut
            moveTo(c, 0f)
            lineTo(w - c, 0f)
            lineTo(w, c)
            lineTo(w, h - c)
            lineTo(w - c, h)
            lineTo(c, h)
            lineTo(0f, h - c)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun CrystalActionCard(
    item: ModernDashboardAction,
    navController: NavController,
    drawableResId: Int,
    isLightMode: Boolean,
    modifier: Modifier = Modifier,
    downloadProgress: Int = 0,
    isDownloading: Boolean = false,
    isDownloadFailed: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.05f) // Matches premium square grid aspect ratio
    ) {
        // 1. Shadow Layer (Offset down to simulate light from above)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = 10.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = shape,
                    clip = false,
                    spotColor = Color.Black.copy(alpha = 0.80f),
                    ambientColor = Color.White.copy(alpha = 0.15f)
                )
        )

        // 2. Main Card Layer (Offset up to lift it visually)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (-4).dp)
                .clip(shape)
                .background(item.gradient)
                .clickable {
                    if (onClick != null) {
                        onClick()
                    } else {
                        navController.navigate(item.route)
                    }
                }
        ) {
            // Volumetric glass border outline Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw crisp volumetric gradient outline (specular edge reflection)
                val strokeBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.20f),
                        Color.Black.copy(alpha = 0.45f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
                val strokeWidth = 1.5.dp.toPx()
                val cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx())

                drawRoundRect(
                    brush = strokeBrush,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(w - strokeWidth, h - strokeWidth),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = strokeWidth)
                )
            }

            // 3. Dynamic Bespoke Graphic drawn on the left side
            CardWireframeGraphic(
                title = item.title,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxHeight(0.48f)
                    .fillMaxWidth(0.55f)
                    .padding(start = 14.dp, top = 14.dp)
            )

            // 4. Icon Top Right
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier
                    .padding(top = 18.dp, end = 18.dp)
                    .size(26.dp)
                    .align(Alignment.TopEnd)
            )

            // 5. Text Bottom Left
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 18.dp, end = 18.dp)
            ) {
                AutoShrinkText(
                    text = item.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxFontSize = if (item.title.contains("Display", ignoreCase = true)) 15.sp else 17.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        lineHeight = if (item.title.contains("Display", ignoreCase = true)) 17.sp else 19.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                
                if (isDownloading) {
                    AutoShrinkText(
                        text = "Downloading AI... $downloadProgress%",
                        color = Color.White.copy(alpha = 0.9f),
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                } else if (isDownloadFailed) {
                    AutoShrinkText(
                        text = TerminologyManager.getTerm("Download Failed (Tap to Retry)"),
                        color = Color(0xFFFFCDD2),
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    AutoShrinkText(
                        text = item.subtitle,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Normal,
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp,
                        style = androidx.compose.ui.text.TextStyle(lineHeight = 13.sp)
                    )
                }
            }
        }
    }
}
