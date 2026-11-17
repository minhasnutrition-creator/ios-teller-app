package com.minhas.teller.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.screens.DashboardContent
import com.minhas.teller.ui.screens.ModernGatewayHeader
import com.minhas.teller.ui.screens.DesktopCompanionBannerCard
import com.minhas.teller.ui.screens.CloudSyncBannerCard
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.components.NeedsAttentionCard
import com.minhas.teller.ui.components.OverviewCardContent
import com.minhas.teller.ui.components.OverviewCardData
import com.minhas.teller.ui.components.OverviewModePickerDialog
import com.minhas.teller.ui.theme.*
import com.minhas.teller.utils.DashboardPrefs
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.utils.WarningsPrefs
import com.minhas.teller.SessionManager
import androidx.compose.material3.Icon
import com.minhas.teller.ui.viewmodels.DashboardAttentionViewModel
import com.minhas.teller.ui.viewmodels.DashboardAttentionViewModelFactory
import com.minhas.teller.ui.viewmodels.AttentionItem
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.ui.viewmodels.DashboardViewModel
import com.minhas.teller.ui.viewmodels.DashboardViewModelFactory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.automirrored.filled.HelpOutline

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardBento(navController: NavController) {
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

    val items = listOf(
        BentoAction(TerminologyManager.getTerm("Vouchers"), TerminologyManager.getTerm("Sales, Purchase"), Icons.Default.Receipt, Screen.EntrySelection.route, GlobalCoralPink),
        BentoAction(TerminologyManager.getTerm("Display All Entries"), TerminologyManager.getTerm("Daybook, Reports"), Icons.AutoMirrored.Filled.List, Screen.Reports.route, GlobalMintGreenB),
        BentoAction(TerminologyManager.getTerm("Masters"), TerminologyManager.getTerm("Ledgers, Groups"), Icons.Default.AccountBalance, Screen.AccountsInfo.route, GlobalPaleYellowB),
        BentoAction(TerminologyManager.getTerm("Inventory Info"), TerminologyManager.getTerm("Items, Units"), Icons.Default.Inventory, Screen.StockItemList.route, GlobalPastelOrangeB),
        BentoAction(TerminologyManager.getTerm("AI Assistant"), TerminologyManager.getTerm("Chat with Finance"), Icons.Default.Face, Screen.Chat.route, GlobalSkyBlue),
        BentoAction(TerminologyManager.getTerm("Live Stock"), TerminologyManager.getTerm("Showroom"), Icons.Default.DirectionsCar, Screen.LiveStockManagement.route, GlobalCoralPink),
        BentoAction(TerminologyManager.getTerm("Daily Tasks"), TerminologyManager.getTerm("Staff Checklists"), Icons.Default.Checklist, Screen.DailyTasks.route, GlobalMintGreenB),
        BentoAction(TerminologyManager.getTerm("Estimates"), TerminologyManager.getTerm("Quotations"), Icons.AutoMirrored.Filled.List, Screen.QuotationList.route, GlobalSkyBlue),
        BentoAction(TerminologyManager.getTerm("Utilities"), TerminologyManager.getTerm("Printer, Barcodes"), Icons.Default.Build, Screen.Utilities.route, GlobalPastelOrangeB),
        BentoAction(TerminologyManager.getTerm("Help"), TerminologyManager.getTerm("Guide & Manual"), Icons.AutoMirrored.Filled.HelpOutline, Screen.Help.route, GlobalCoralPink)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { paddingValues ->
        LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(paddingValues)
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(bottom = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF1E2D4A))
                            .clickable { showModePicker = true }
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                AutoShrinkText(TerminologyManager.getTerm("THIS MONTH'S OVERVIEW"), maxFontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f), letterSpacing = 0.5.sp)
                                AutoShrinkText(overviewMode.displayName, maxFontSize = 10.sp, minFontSize = 7.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                            Spacer(Modifier.height(8.dp))
                            OverviewCardContent(data = cardData)
                        }
                    }
                }
                items(items) { item ->
                    BentoCard(item, navController)
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

data class BentoAction(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String,
    val color: Color
)

@Composable
fun BentoCard(item: BentoAction, navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 168.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(item.color)
            .clickable { navController.navigate(item.route) }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            androidx.compose.material3.Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = Color.Black,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            AutoShrinkText(
                text = item.title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                maxFontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            AutoShrinkText(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxFontSize = 11.sp,
                minFontSize = 8.sp,
                color = Color.Black.copy(alpha = 0.7f)
            )
        }
    }
}
