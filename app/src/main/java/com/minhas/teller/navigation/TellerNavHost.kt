package com.minhas.teller.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.minhas.teller.SessionManager
import com.minhas.teller.ui.components.GlassBottomNavBar
import com.minhas.teller.ui.screens.*
import com.minhas.teller.ui.screens.dashboard.DashboardBento
import com.minhas.teller.ui.screens.dashboard.DashboardCrystal
import com.minhas.teller.ui.screens.dashboard.DashboardCyberFinance
import com.minhas.teller.ui.theme.*
import com.minhas.teller.utils.*
import kotlinx.coroutines.launch

data class BottomNavItem(val label: String, val route: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    navController: NavHostController,
    onThemeChanged: (Int) -> Unit,
    currentAppTheme: AppTheme,
    backgroundBrush: Brush
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState(initial = null)
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Deep-link from the GST return reminder notification → open GST Returns.
    val activity = context as? android.app.Activity
    LaunchedEffect(Unit) {
        if (activity?.intent?.getBooleanExtra(com.minhas.teller.workers.GstReturnReminderWorker.EXTRA_OPEN_GST_RETURNS, false) == true) {
            activity.intent.removeExtra(com.minhas.teller.workers.GstReturnReminderWorker.EXTRA_OPEN_GST_RETURNS)
            kotlinx.coroutines.delay(900) // let Splash finish its own navigation first
            navController.navigate(Screen.GstReturns.route)
        }
    }
    val coroutineScope = rememberCoroutineScope()

    // Global Lockdown Observer: Re-verify trial and license on ON_RESUME event (e.g. app return from background)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    val (status, _) = LicenseUtils.getAppStatus(context)
                    if (status == LicenseUtils.AppStatus.TRIAL_EXPIRED || status == LicenseUtils.AppStatus.NOT_LICENSED) {
                        val activeRoute = navController.currentDestination?.route
                        if (activeRoute != null && !activeRoute.startsWith(Screen.License.route) && activeRoute != Screen.Splash.route) {
                            navController.navigate(Screen.License.route.replace("{reason}", "expired")) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        launch {
            PdfNavigator.events.collect { event ->
                val encodedPath = Uri.encode(event.filePath)
                val encodedTitle = Uri.encode(event.title)
                navController.navigate("pdf_viewer?filePath=$encodedPath&title=$encodedTitle&voucherId=${event.voucherId}")
            }
        }
        launch {
            ExcelNavigator.events.collect { event ->
                val encodedPath = Uri.encode(event.filePath)
                val encodedTitle = Uri.encode(event.title)
                navController.navigate("excel_viewer?filePath=$encodedPath&title=$encodedTitle")
            }
        }
    }

    val bottomBarRoutes = listOf(Screen.Dashboard.route, Screen.Daybook.route, Screen.Reports.route)
    val showBottomBar = currentRoute in bottomBarRoutes

    // Dynamic Background Logic
    val dynamicBackgroundBrush = if (currentAppTheme == AppTheme.DEFAULT) {
        when {
            // Reports (Yellow)
            currentRoute == Screen.Reports.route || 
            currentRoute == Screen.Daybook.route || 
            currentRoute == Screen.TrialBalanceReport.route || 
            currentRoute == Screen.StockSummaryReport.route || 
            currentRoute == Screen.ProfitAndLossReport.route || 
            currentRoute == Screen.BalanceSheetReport.route ||
            currentRoute == Screen.AccountBooks.route ||
            currentRoute == Screen.OutstandingPaymentsSummary.route ||
            currentRoute == Screen.CashBankBooks.route ||
            currentRoute == Screen.JournalRegister.route ||
            currentRoute == Screen.ReceiptsAndPayments.route ||
            currentRoute == Screen.LedgerViewReport.route ||
            currentRoute?.startsWith("ledger_view_report") == true ||
            currentRoute == Screen.GroupSummary.route ||
            currentRoute == Screen.StatementOfAccounts.route ||
            currentRoute == Screen.InventoryBooks.route ||
            currentRoute == Screen.PendingPaymentsMenu.route -> BackgroundYellowGradient

            // Vouchers (Purple)
            currentRoute == Screen.AddEditVoucher.route ||
            currentRoute?.startsWith("add_edit_voucher") == true ||
            currentRoute == Screen.VoucherList.route ||
            currentRoute?.startsWith("voucher_view") == true ||
            currentRoute == Screen.EntrySelection.route ||
            currentRoute == Screen.QuotationList.route -> BackgroundPurpleGradient

            // Accounts Info (Teal)
            currentRoute == Screen.AccountsInfo.route ||
            currentRoute == Screen.LedgerList.route ||
            currentRoute == Screen.GroupList.route ||
            currentRoute == Screen.AddEditLedger.route ||
            currentRoute?.startsWith("add_edit_ledger") == true ||
            currentRoute == Screen.AddEditGroup.route ||
            currentRoute == Screen.ListOfAccounts.route -> BackgroundTealGradient

            // Inventory Info (Dark Green/Teal)
            currentRoute == Screen.InventoryInfo.route ||
            currentRoute == Screen.StockItemList.route ||
            currentRoute == Screen.UnitList.route ||
            currentRoute == Screen.AddEditStockItem.route ||
            currentRoute?.startsWith("add_edit_stock_item") == true ||
            currentRoute == Screen.AddEditUnit.route ||
            currentRoute?.startsWith("add_edit_unit") == true -> BackgroundInventoryGradient

            else -> MainBackgroundGradient // Fallback to standard
        }
    } else {
        backgroundBrush
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentAppTheme == AppTheme.CRYSTAL) {
            CrystalBackground(modifier = Modifier.fillMaxSize())
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(dynamicBackgroundBrush)
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {} // Explicitly empty to prevent any background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Splash.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Screen.Splash.route) { SplashScreen(navController) }
                    composable(
                        route = Screen.OutstandingBillDetail.route,
                        arguments = listOf(
                            navArgument("voucherId") { type = NavType.IntType },
                            navArgument("ledgerId") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        OutstandingBillDetailScreen(
                            navController = navController,
                            voucherId = backStackEntry.arguments?.getInt("voucherId") ?: -1,
                            ledgerId = backStackEntry.arguments?.getInt("ledgerId") ?: -1
                        )
                    }
                    composable(Screen.CompanyList.route) { CompanyListScreen(navController) }
                    composable(Screen.CreateCompany.route) { CreateCompanyScreen(navController) }

                    composable(Screen.Dashboard.route) {
                        when (currentAppTheme) {
                            AppTheme.DEFAULT -> DashboardScreen(navController)
                            AppTheme.MODERN_LIGHT -> DashboardModernLight(navController)
                            AppTheme.CYBER_FINANCE -> DashboardCyberFinance(navController)
                            AppTheme.BENTO -> DashboardBento(navController)
                            AppTheme.CRYSTAL -> DashboardCrystal(navController)
                        }
                    }

                    composable(Screen.AccountsInfo.route) { LedgerListScreen(navController) }
                    composable(Screen.InventoryInfo.route) { InventoryInfoScreen(navController, currentAppTheme) }
                    composable(Screen.GroupList.route) { GroupListScreen(navController, currentAppTheme) }
                    composable(Screen.LedgerList.route) { LedgerListScreen(navController) }
                    composable(
                        route = Screen.AddEditLedger.route,
                        arguments = listOf(navArgument("ledgerId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        AddEditLedgerScreen(navController, backStackEntry.arguments?.getInt("ledgerId") ?: -1)
                    }
                    composable(Screen.StockItemList.route) { StockItemListScreen(navController) }
                    composable(Screen.UnitList.route) { UnitListScreen(navController) }
                    composable(
                        route = Screen.AddEditStockItem.route,
                        arguments = listOf(navArgument("stockItemId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        AddEditStockItemScreen(navController, backStackEntry.arguments?.getInt("stockItemId") ?: -1)
                    }
                    composable(
                        route = Screen.AddEditUnit.route + "/{unitId}",
                        arguments = listOf(navArgument("unitId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        AddEditUnitScreen(navController, backStackEntry.arguments?.getInt("unitId") ?: -1)
                    }
                    composable(Screen.AddEditUnit.route) { AddEditUnitScreen(navController, -1) }
                    composable(Screen.SalesRegister.route) { SalesRegisterScreen(navController) }
                    composable(Screen.PurchaseRegister.route) { PurchaseRegisterScreen(navController) }
                    composable(Screen.Daybook.route) { DaybookScreen(navController) }
                    composable(Screen.StockSummaryReport.route) { StockSummaryReportScreen(navController) }
                    composable(Screen.ProfitAndLossReport.route) { ProfitAndLossReportScreen(navController) }
                    composable(Screen.BalanceSheetReport.route) { BalanceSheetReportScreen(navController) }
                    composable(
                        route = Screen.AddEditVoucher.route + "?voucherId={voucherId}&type={type}&isDuplicate={isDuplicate}",
                        arguments = listOf(
                            navArgument("voucherId") { type = NavType.IntType; defaultValue = -1 },
                            navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("isDuplicate") { type = NavType.BoolType; defaultValue = false }
                        )
                    ) { backStackEntry ->
                        val vId = backStackEntry.arguments?.getInt("voucherId") ?: -1
                        val vType = backStackEntry.arguments?.getString("type")
                        val isDup = backStackEntry.arguments?.getBoolean("isDuplicate") ?: false
                        
                        if (vType == "Purchase" && (vId == -1 || vId == 0)) {
                            PurchaseVoucherCreationScreen(
                                navController = navController,
                                voucherId = vId,
                                isDuplicate = isDup
                            )
                        } else {
                            AddEditVoucherScreen(
                                navController = navController,
                                voucherId = vId,
                                initialType = vType,
                                isDuplicate = isDup
                            )
                        }
                    }
                    composable(Screen.QuotationList.route) { QuotationListScreen(navController) }
                    composable(Screen.CreateQuotation.route) { CreateQuotationScreen(navController) }
                    composable(
                        route = Screen.EditQuotation.route,
                        arguments = listOf(navArgument("quotationId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        // EditQuotationScreen(navController, backStackEntry.arguments?.getInt("quotationId") ?: -1)
                        // For now reuse CreateQuotation with edit mode
                    }
                    composable(Screen.Reports.route) { ReportsScreen(navController, currentAppTheme) }
                    composable(Screen.DisplayOptions.route) { DisplayOptionsScreen(navController, currentAppTheme) }
                    composable(Screen.TrialBalanceReport.route) { TrialBalanceReportScreen(navController) }
                    composable(Screen.PendingPaymentsMenu.route) { PendingPaymentsMenuScreen(navController, currentAppTheme) }
                    composable(
                        route = Screen.OutstandingPaymentsSummary.route,
                        arguments = listOf(navArgument("group") { type = NavType.StringType; nullable = true; defaultValue = null })
                    ) { backStackEntry ->
                        OutstandingPaymentsSummaryScreen(navController, backStackEntry.arguments?.getString("group"), currentAppTheme)
                    }
                    composable(Screen.AccountBooks.route) { AccountBooksScreen(navController, currentAppTheme) }
                    composable(
                        route = Screen.LedgerViewReport.route,
                        arguments = listOf(navArgument("ledgerId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        LedgerViewReportScreen(navController, backStackEntry.arguments?.getInt("ledgerId") ?: -1, currentAppTheme)
                    }
                    composable(Screen.ListOfAccounts.route) { ListOfAccountsScreen(navController) }
                    composable(Screen.GroupSummary.route) { GroupSummaryScreen(navController) }
                    composable(Screen.CashBankBooks.route) { CashBankBooksScreen(navController) }
                    composable(Screen.JournalRegister.route) { JournalRegisterScreen(navController) }
                    composable(Screen.StatementOfAccounts.route) { StatementOfAccountsScreen(navController, currentAppTheme) }
                    composable(Screen.InventoryBooks.route) { InventoryBooksScreen(navController, currentAppTheme) }
                    composable(Screen.ReceiptsAndPayments.route) { ReceiptsAndPaymentsReportScreen(navController) }
                    composable(
                        route = Screen.GroupReport.route,
                        arguments = listOf(navArgument("groupName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        GroupReportScreen(navController, backStackEntry.arguments?.getString("groupName") ?: "")
                    }
                    composable(
                        route = Screen.VoucherView.route,
                        arguments = listOf(navArgument("voucherId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        VoucherViewScreen(navController, backStackEntry.arguments?.getInt("voucherId") ?: -1)
                    }
                    composable(Screen.About.route) { AboutScreen(navController) }
                    composable(Screen.LiveStockManagement.route) { LiveStockManagementScreen(navController, currentAppTheme) }
                    composable(Screen.DailyTasks.route) { DailyTasksScreen(navController) }
                    composable(
                        route = Screen.DailyTaskEmployeeDetail.route,
                        arguments = listOf(navArgument("employeeCode") { type = NavType.StringType })
                    ) { backStackEntry ->
                        DailyTaskEmployeeDetailScreen(navController, backStackEntry.arguments?.getString("employeeCode") ?: "")
                    }
                    composable(
                        route = Screen.License.route,
                        arguments = listOf(navArgument("reason") { type = NavType.StringType; nullable = true; defaultValue = null })
                    ) { backStackEntry ->
                        LicenseScreen(navController, backStackEntry.arguments?.getString("reason"))
                    }
                    composable(Screen.Settings.route) { SettingsScreen(navController, onThemeChanged) }
                    composable(Screen.CloudSync.route) { CloudSyncScreen(navController) }
                    composable(
                        route = Screen.Chat.routeTemplate,
                        arguments = listOf(
                            navArgument("initialQuery") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("autoVoice") { type = NavType.BoolType; defaultValue = false }
                        )
                    ) { backStackEntry ->
                        val rawQuery = backStackEntry.arguments?.getString("initialQuery")
                        val initialQuery = if (rawQuery.isNullOrBlank() || rawQuery.startsWith("{")) null else rawQuery
                        val autoVoice = backStackEntry.arguments?.getBoolean("autoVoice") ?: false
                        ChatScreen(navController, initialQuery = initialQuery, autoStartVoice = autoVoice)
                    }
                    composable(Screen.Help.route) { HelpScreen(navController) }
                    composable(Screen.HelpDetail.route) { backStackEntry ->
                        HelpDetailScreen(navController, backStackEntry.arguments?.getString("topicId") ?: "")
                    }
                    composable(Screen.AuditLog.route) { AuditLogScreen(navController) }
                    composable(Screen.BankReconciliation.route) { BankReconciliationScreen(navController) }
                    composable(Screen.EntrySelection.route) { com.minhas.teller.ui.screens.NewEntrySelectionScreen(navController) }
                    composable(Screen.Returns.route) { com.minhas.teller.ui.screens.ReturnsScreen(navController) }
                    composable(Screen.AiStatus.route) { AiModelStatusScreen(navController) }
                    composable(Screen.ItemProfitability.route) { ItemProfitabilityScreen(navController) }
                    composable(
                        route = Screen.MonthlySalesReport.route,
                        arguments = listOf(navArgument("showroomCode") { type = NavType.StringType; nullable = true; defaultValue = null })
                    ) { backStackEntry ->
                        MonthlySalesReportScreen(navController, backStackEntry.arguments?.getString("showroomCode"), currentAppTheme)
                    }
                    composable(Screen.EditCompany.route) { EditCompanyScreen(navController) }
                    composable(Screen.WhyUpgrade.route) { WhyUpgradeScreen(navController) }
                    composable(Screen.TodayInvoices.route) { TodayInvoicesScreen(navController) }
                    composable(
                        route = Screen.TodayInvoicesByDate.route,
                        arguments = listOf(navArgument("selectedDateMillis") { type = NavType.LongType; defaultValue = -1L })
                    ) { backStackEntry ->
                        val millis = backStackEntry.arguments?.getLong("selectedDateMillis").takeIf { it != -1L }
                        TodayInvoicesScreen(navController, millis)
                    }
                    composable(Screen.InvoiceTemplates.route) { InvoiceTemplateSelectionScreen(navController) }
                    composable(
                        route = Screen.PdfViewer.route,
                        arguments = listOf(
                            navArgument("filePath") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("voucherId") { type = NavType.IntType; defaultValue = -1 }
                        )
                    ) { backStackEntry ->
                        PdfViewerScreen(
                            navController,
                            backStackEntry.arguments?.getString("filePath") ?: "",
                            backStackEntry.arguments?.getString("title") ?: "",
                            backStackEntry.arguments?.getInt("voucherId") ?: -1
                        )
                    }
                    composable(
                        route = Screen.ExcelViewer.route,
                        arguments = listOf(
                            navArgument("filePath") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null }
                        )
                    ) { backStackEntry ->
                        ExcelViewerScreen(navController, backStackEntry.arguments?.getString("filePath") ?: "", backStackEntry.arguments?.getString("title") ?: "")
                    }
                    composable(Screen.SignatureDrawing.route) { SignatureDrawingScreen(navController) }
                    composable(Screen.TellerScore.route) { TellerScoreScreen(navController) }
                    composable(Screen.AiAuditor.route) { AiAuditorScreen(navController) }
                    composable(Screen.ImportCleanup.route) { ImportCleanupScreen(navController) }
                    composable(Screen.CaScrutiny.route) { CaScrutinyScreen(navController) }
                    composable(Screen.InstallGuide.route) { InstallGuideScreen(navController) }
                    composable(Screen.WarningDetail.route) { backStackEntry ->
                        WarningDetailScreen(navController, backStackEntry.arguments?.getString("warningId") ?: "")
                    }
                    composable(Screen.GstReport.route) { GstReportScreen(navController) }
                    composable(Screen.GstReturns.route) { GstReturnsScreen(navController) }
                    composable(
                        route = Screen.GstFilingWizard.route,
                        arguments = listOf(
                            navArgument("companyId") { type = NavType.IntType },
                            navArgument("fp") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { backStackEntry ->
                        GstFilingWizardScreen(
                            navController = navController,
                            companyId = backStackEntry.arguments?.getInt("companyId") ?: -1,
                            fp = backStackEntry.arguments?.getString("fp") ?: ""
                        )
                    }
                    composable(
                        route = Screen.GstPortalWebView.route,
                        arguments = listOf(
                            navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null }
                        )
                    ) { backStackEntry ->
                        GstPortalWebViewScreen(
                            navController = navController,
                            url = backStackEntry.arguments?.getString("url") ?: "https://www.gst.gov.in",
                            title = backStackEntry.arguments?.getString("title") ?: "GST Portal"
                        )
                    }
                    composable(Screen.MonthlyInventory.route) { MonthlyInventoryScreen(navController) }
                    composable(Screen.DesktopCompanion.route) { DesktopCompanionScreen(navController) }
                    composable(
                        route = Screen.ThermalPrinter.route,
                        arguments = listOf(
                            navArgument("voucherId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val voucherId = backStackEntry.arguments?.getString("voucherId")?.toIntOrNull()
                        ThermalPrinterScreen(navController, voucherId)
                    }
                    composable(Screen.BarcodeGenerator.route) { BarcodeGeneratorScreen(navController) }
                    composable(Screen.Gstr2bReconciler.route) { Gstr2bReconcilerScreen(navController) }
                    composable(Screen.Utilities.route) { UtilitiesScreen(navController, currentAppTheme) }
                    composable(Screen.QrScanner.route) { QrScannerScreen(navController) }
                    composable(Screen.BarcodeScanner.route) {
                        QrScannerScreen(
                            navController,
                            resultKey = "barcode_result",
                            scanAnyFormat = true,
                            hint = "Point the camera at the item's barcode"
                        )
                    }
                    composable(Screen.PortalAutopilot.route) { PortalAutopilotScreen(navController) }
                    composable(
                        route = Screen.EInvoice.route,
                        arguments = listOf(navArgument("voucherId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        EInvoiceScreen(navController, backStackEntry.arguments?.getInt("voucherId") ?: -1)
                    }
                }

                if (showBottomBar) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                    ) {
                        GlassBottomNavBar(navController)
                    }
                }
            }
        }
    }
}

@Composable
private fun CrystalBackground(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val blurRadius = 140.dp
    val radiusPx = with(density) { 520.dp.toPx() }

    val blueCenter = with(density) { Offset(80.dp.toPx(), 140.dp.toPx()) }
    val purpleCenter = with(density) { Offset(320.dp.toPx(), 260.dp.toPx()) }
    val pinkCenter = with(density) { Offset(80.dp.toPx(), 720.dp.toPx()) }
    val tealCenter = with(density) { Offset(340.dp.toPx(), 920.dp.toPx()) }

    Box(modifier = modifier.background(MainBackgroundGradient)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3B82F6).copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        center = blueCenter,
                        radius = radiusPx
                    )
                )
                .blur(blurRadius)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6).copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = purpleCenter,
                        radius = radiusPx
                    )
                )
                .blur(blurRadius)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFEC4899).copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        center = pinkCenter,
                        radius = radiusPx
                    )
                )
                .blur(blurRadius)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF14B8A6).copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = tealCenter,
                        radius = radiusPx
                    )
                )
                .blur(blurRadius)
        )
    }
}

fun getScreenTitle(route: String?): String {
    return when (route) {
        Screen.Dashboard.route -> TerminologyManager.getTerm("Dashboard")
        Screen.CompanyList.route -> TerminologyManager.getTerm("Select Company")
        Screen.CreateCompany.route -> TerminologyManager.getTerm("Create Company")
        Screen.AccountsInfo.route -> TerminologyManager.getTerm("Accounts Info")
        Screen.InventoryInfo.route -> TerminologyManager.getTerm("Inventory Info")
        Screen.GroupList.route -> TerminologyManager.getTerm("Groups")
        Screen.LedgerList.route -> TerminologyManager.getTerm("Ledgers")
        Screen.StockItemList.route -> TerminologyManager.getTerm("Stock Items")
        Screen.UnitList.route -> TerminologyManager.getTerm("Units")
        Screen.SalesRegister.route -> TerminologyManager.getTerm("Sales Register")
        Screen.PurchaseRegister.route -> TerminologyManager.getTerm("Purchase Register")
        Screen.Daybook.route -> TerminologyManager.getTerm("Daybook")
        Screen.Reports.route -> TerminologyManager.getTerm("Reports")
        Screen.DisplayOptions.route -> TerminologyManager.getTerm("Display Options")
        Screen.TrialBalanceReport.route -> TerminologyManager.getTerm("Trial Balance")
        Screen.StockSummaryReport.route -> TerminologyManager.getTerm("Stock Summary")
        Screen.ProfitAndLossReport.route -> TerminologyManager.getTerm("Profit & Loss")
        Screen.BalanceSheetReport.route -> TerminologyManager.getTerm("Balance Sheet")
        Screen.PendingPaymentsMenu.route -> TerminologyManager.getTerm("Pending Payments")
        Screen.OutstandingPaymentsSummary.route -> TerminologyManager.getTerm("Outstanding Payments")
        Screen.AccountBooks.route -> TerminologyManager.getTerm("Account Books")
        Screen.ListOfAccounts.route -> TerminologyManager.getTerm("List of Accounts")
        Screen.GroupSummary.route -> TerminologyManager.getTerm("Group Summary")
        Screen.CashBankBooks.route -> TerminologyManager.getTerm("Cash/Bank Books")
        Screen.JournalRegister.route -> TerminologyManager.getTerm("Journal Register")
        Screen.StatementOfAccounts.route -> TerminologyManager.getTerm("Statement of Accounts")
        Screen.InventoryBooks.route -> TerminologyManager.getTerm("Inventory Books")
        Screen.ReceiptsAndPayments.route -> TerminologyManager.getTerm("Receipts & Payments")
        Screen.AddEditVoucher.route -> TerminologyManager.getTerm("Voucher Entry")
        Screen.AddEditGroup.route -> TerminologyManager.getTerm("Group Details")
        Screen.VoucherList.route -> TerminologyManager.getTerm("Vouchers")
        Screen.QuotationList.route -> TerminologyManager.getTerm("Quotations")
        Screen.LiveStockManagement.route -> TerminologyManager.getTerm("Live Stock")
        Screen.DailyTasks.route -> TerminologyManager.getTerm("Daily Tasks")
        Screen.About.route -> TerminologyManager.getTerm("About")
        Screen.Settings.route -> TerminologyManager.getTerm("Settings")
        Screen.Help.route -> TerminologyManager.getTerm("Help")
        Screen.InstallGuide.route -> TerminologyManager.getTerm("Install Guide")
        Screen.AuditLog.route -> TerminologyManager.getTerm("Audit Trail (Edit Log)")
        Screen.BankReconciliation.route -> TerminologyManager.getTerm("Bank Reconciliation")
        Screen.GstReport.route -> TerminologyManager.getTerm("GST Summary")
        Screen.GstReturns.route -> TerminologyManager.getTerm("GST Returns")
        else -> {
            if (route?.startsWith("add_edit_ledger") == true) TerminologyManager.getTerm("Ledger Details")
            else if (route?.startsWith("add_edit_stock_item") == true) TerminologyManager.getTerm("Stock Item Details")
            else if (route?.startsWith("add_edit_unit") == true) TerminologyManager.getTerm("Unit Details")
            else if (route?.startsWith("ledger_view_report") == true) TerminologyManager.getTerm("Ledger Report")
            else if (route?.startsWith("group_report") == true) TerminologyManager.getTerm("Group Report")
            else if (route?.startsWith("add_edit_voucher") == true) TerminologyManager.getTerm("Voucher Entry")
            else if (route?.startsWith("voucher_view") == true) TerminologyManager.getTerm("Voucher View")
            else if (route?.startsWith("help_detail") == true) TerminologyManager.getTerm("Help Detail")
            else if (route?.startsWith("gst_filing_wizard") == true) TerminologyManager.getTerm("File GST Returns")
            else if (route?.startsWith("gst_portal_webview") == true) TerminologyManager.getTerm("GST Portal")
            else if (route?.startsWith("monthly_inventory") == true) TerminologyManager.getTerm("Monthly Inventory")
            else if (route?.startsWith("desktop_companion") == true) "PC Desktop Companion"
            else if (route?.startsWith("cloud_sync") == true) "Cloud Sync"
            else if (route?.startsWith("thermal_printer") == true) TerminologyManager.getTerm("Thermal Printer")
            else if (route?.startsWith("barcode_generator") == true) TerminologyManager.getTerm("Barcode & Label Generator")
            else if (route?.startsWith("gstr2b_reconciler") == true) TerminologyManager.getTerm("GSTR-2B ITC Reconciliation")
            else if (route?.startsWith("utilities") == true) TerminologyManager.getTerm("Utilities")
            else if (route?.startsWith("qr_scanner") == true) TerminologyManager.getTerm("QR Scanner")
            else if (route?.startsWith("ai_auditor") == true) TerminologyManager.getTerm("AI Auditor")
            else TerminologyManager.getTerm("Teller")
        }
    }
}
