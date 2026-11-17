package com.minhas.teller.ui.screens

import com.minhas.teller.ui.components.rememberInBackground
import com.minhas.teller.ui.components.AutoShrinkText
import android.app.DatePickerDialog
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.accounting.ProfitCalculator
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.ui.viewmodels.LedgerViewModel
import com.minhas.teller.ui.viewmodels.LedgerViewModelFactory
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.utils.GstComplianceRules
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.ui.components.TellerTopAppBar
import java.text.SimpleDateFormat
import java.util.*
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Unit as AppUnit
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.ui.viewmodels.StockItemViewModel
import com.minhas.teller.ui.viewmodels.StockItemViewModelFactory
import com.minhas.teller.data.repositories.UnitRepository
import com.minhas.teller.ui.viewmodels.UnitViewModel
import com.minhas.teller.ui.viewmodels.UnitViewModelFactory
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import com.minhas.teller.navigation.Screen
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.HorizontalDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfitAndLossReportScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)

    val ledgerRepository = LedgerRepository(database.ledgerDao())
    val ledgerViewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(ledgerRepository))
    val ledgers by ledgerViewModel.allLedgers.collectAsState()

    val voucherRepository = VoucherRepository(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.billAllocationDao(), database.auditLogDao(), database.ledgerDao(), db = database)
    // NOTE: the period's vouchers are filtered from allCompanyVouchers below. They used to come from
    // VoucherViewModel.allVouchersWithAllEntries, but that flow is ALSO filled with every voucher of
    // the company when the view model starts — whichever load finished last won, so the report could
    // silently show all-time figures for "This Month".

    val companyRepository = CompanyRepository(database.companyDao())
    val companyViewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(companyRepository))
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val company by if (currentCompanyId != null) {
        companyViewModel.getCompany(currentCompanyId!!).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(null) }
    }

    val stockItemRepository = StockItemRepository(database.stockItemDao())
    val stockItemViewModel: StockItemViewModel = viewModel(factory = StockItemViewModelFactory(stockItemRepository))
    val allStockItems by stockItemViewModel.allStockItems.collectAsState(initial = emptyList())

    val unitRepository = UnitRepository(database.unitDao())
    val unitViewModel: UnitViewModel = viewModel(factory = UnitViewModelFactory(unitRepository))
    val allUnits by unitViewModel.allUnits.collectAsState(initial = emptyList())

    var allCompanyVouchers by remember { mutableStateOf<List<VoucherWithAllEntries>>(emptyList()) }
    // Every stock item incl. deleted ones: old sales of a deleted item still need their cost.
    var costingStockItems by remember { mutableStateOf<List<StockItem>>(emptyList()) }
    LaunchedEffect(currentCompanyId) {
        val cid = currentCompanyId ?: return@LaunchedEffect
        allCompanyVouchers = voucherRepository.getAllVouchersWithAllEntries(cid)
        costingStockItems = database.stockItemDao().getAllStockItemsForSync(cid)
    }
    val accountGroups by remember(currentCompanyId) {
        currentCompanyId?.let { database.accountGroupDao().getAllGroups(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList<com.minhas.teller.data.entities.AccountGroup>())
    }.collectAsState(initial = emptyList())

    // Which ledgers are sales / purchases / income / expense — same rules as the Balance Sheet.
    val ledgerRoles = remember(ledgers, accountGroups) { ProfitCalculator.classifyLedgers(ledgers, accountGroups) }
    // Cost of every item ever sold, valued at the weighted-average purchase cost on the day of sale.
    val costLedger = rememberInBackground(costingStockItems, allCompanyVouchers, ledgerRoles, initial = ProfitCalculator.CostLedger.EMPTY) {
        ProfitCalculator.buildCostLedger(costingStockItems, allCompanyVouchers, ledgerRoles.purchaseLedgerIds)
    }
    // Don't show figures until costs are worked out (otherwise cost shows 0 for a moment = fake 100% margin).
    val costReady = costLedger !== ProfitCalculator.CostLedger.EMPTY || allCompanyVouchers.isEmpty()

    var showSalesProfitabilityDialog by remember { mutableStateOf(false) }
    var showPurchaseDetailDialog by remember { mutableStateOf(false) }
    var showLedgerTransactionsDialog by remember { mutableStateOf(false) }
    var selectedLedgerForTransactions by remember { mutableStateOf<Ledger?>(null) }
    val expandedIncomeGroups = remember { mutableStateListOf<String>() }
    val expandedExpenseGroups = remember { mutableStateListOf<String>() }

    // Default: from the 1st of this month 00:00 to the end of today. (The time of day used to be kept,
    // so opening the report at 5 PM silently dropped sales made on the 1st before 5 PM.)
    val defaultCalendar = Calendar.getInstance()
    defaultCalendar.set(Calendar.DAY_OF_MONTH, 1)
    defaultCalendar.set(Calendar.HOUR_OF_DAY, 0); defaultCalendar.set(Calendar.MINUTE, 0)
    defaultCalendar.set(Calendar.SECOND, 0); defaultCalendar.set(Calendar.MILLISECOND, 0)
    val todayEnd = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }
    var fromDate by remember { mutableStateOf(defaultCalendar.time) }
    var toDate by remember { mutableStateOf(todayEnd.time) }

    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    val fromDatePickerDialog = DatePickerDialog(context, { _, y, m, d ->
        val cal = Calendar.getInstance()
        cal.set(y, m, d, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        fromDate = cal.time
    }, Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH))

    val toDatePickerDialog = DatePickerDialog(context, { _, y, m, d ->
        val cal = Calendar.getInstance()
        cal.set(y, m, d, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 999)
        toDate = cal.time
    }, Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH))

    // Vouchers of the selected period (company-scoped, deleted ones already excluded by the DAO).
    val allVouchersWithAllEntries = remember(allCompanyVouchers, fromDate, toDate) {
        allCompanyVouchers.filter { !it.voucher.date.before(fromDate) && !it.voucher.date.after(toDate) }
    }

    // Aggregations run off the UI thread (they used to run on every recomposition, twice).
    val emptyPnl = remember { calculateProfitAndLoss(emptyList(), emptyList()) }
    val pnlData = rememberInBackground(ledgers, allVouchersWithAllEntries, ledgerRoles, costLedger, costReady, initial = emptyPnl) {
        if (!costReady) emptyPnl
        else calculateProfitAndLoss(ledgers, allVouchersWithAllEntries, ledgerRoles, costLedger)
    }

    // Compare against the immediately preceding window of the same length. Uses the same full-history
    // costs — it used to cost the previous period only from purchases made inside it (cost ≈ 0).
    val periodLengthMillis = toDate.time - fromDate.time
    val prevToMillis = fromDate.time - 1L
    val prevFromMillis = prevToMillis - periodLengthMillis
    val prevPnl = rememberInBackground(ledgers, allCompanyVouchers, ledgerRoles, costLedger, costReady, prevFromMillis, prevToMillis, initial = emptyPnl) {
        if (!costReady) emptyPnl
        else calculateProfitAndLoss(
            ledgers,
            allCompanyVouchers.filter { it.voucher.date.time in prevFromMillis..prevToMillis },
            ledgerRoles,
            costLedger
        )
    }
    val profitDelta = pnlData.netProfitLoss - prevPnl.netProfitLoss

    // Monthly profit trend (last 6 calendar months, company-wide)
    val companyVouchers = allCompanyVouchers
    val monthlyTrend = rememberInBackground(ledgers, companyVouchers, ledgerRoles, costLedger, costReady, initial = emptyList<Pair<String, Double>>()) {
        if (!costReady) emptyList()
        else calculateMonthlyProfit(ledgers, companyVouchers, ledgerRoles, costLedger, 6)
    }

    // Expense breakdown by category (ledger group) — excludes purchases/COGS,
    // which are shown separately in the P&L statement as Cost of Goods Sold.
    val expenseBreakdown = remember(pnlData) {
        pnlData.expenses.entries
            .filter { it.value > 0.0 }
            .filter { entry -> entry.key != ProfitCalculator.COGS_LABEL }
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    }

    // Groups holding the sales ledgers — tapping one opens the item-wise profit breakdown.
    val salesGroupNames = remember(ledgers, ledgerRoles) {
        ledgers.filter { it.id in ledgerRoles.salesLedgerIds }.map { it.group }.toSet()
    }

    var showIncomeDetails by remember { mutableStateOf(false) }
    var showExpenseDetails by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = TerminologyManager.getTerm("Profit & Loss"),
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header actions row (Export buttons)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = {
                        val headers = listOf("Type", "Particulars", "Amount")
                        val data = mutableListOf<List<String>>()
                        pnlData.expenses.forEach { (group, amount) -> data.add(listOf("Expense", group, "%.2f".format(amount))) }
                        pnlData.incomes.forEach { (group, amount) -> data.add(listOf("Income", group, "%.2f".format(amount))) }
                        data.add(listOf("Total Expenses", "", "%.2f".format(pnlData.totalExpenses)))
                        data.add(listOf("Total Incomes", "", "%.2f".format(pnlData.totalIncomes)))
                        data.add(listOf("Net Profit/Loss", "", "%.2f".format(pnlData.netProfitLoss)))
                        
                        ExportUtils.exportReportToPdf(context, "ProfitAndLoss_${System.currentTimeMillis()}.pdf", company?.name ?: "Teller App", "Profit & Loss Account", headers, floatArrayOf(1.5f, 3f, 2f), data)
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = TerminologyManager.getTerm("Export PDF"), tint = Color.White)
                    }
                    IconButton(onClick = {
                        val headers = listOf("Type", "Particulars", "Amount")
                        val data = mutableListOf<List<String>>()
                        pnlData.expenses.forEach { (group, amount) -> data.add(listOf("Expense", group, "%.2f".format(amount))) }
                        pnlData.incomes.forEach { (group, amount) -> data.add(listOf("Income", group, "%.2f".format(amount))) }
                        data.add(listOf("Total Expenses", "", "%.2f".format(pnlData.totalExpenses)))
                        data.add(listOf("Total Incomes", "", "%.2f".format(pnlData.totalIncomes)))
                        data.add(listOf("Net Profit/Loss", "", "%.2f".format(pnlData.netProfitLoss)))

                        ExportUtils.exportReportToExcel(context, "ProfitAndLoss_${System.currentTimeMillis()}.xlsx", "Profit & Loss", headers, data)
                    }) {
                        Icon(Icons.Default.TableChart, contentDescription = TerminologyManager.getTerm("Export Excel"), tint = Color.White)
                    }
                }
            }

            // Date Range Selection Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22FFFFFF))
                            .clickable { fromDatePickerDialog.show() }
                            .padding(12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFFB8A0D0),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = TerminologyManager.getTerm("From:"),
                                    fontSize = 10.sp,
                                    color = Color(0xFFB8A0D0)
                                )
                                AutoShrinkText(
                                    text = dateFormatter.format(fromDate),
                                    maxFontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22FFFFFF))
                            .clickable { toDatePickerDialog.show() }
                            .padding(12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFFB8A0D0),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = TerminologyManager.getTerm("To:"),
                                    fontSize = 10.sp,
                                    color = Color(0xFFB8A0D0)
                                )
                                AutoShrinkText(
                                    text = dateFormatter.format(toDate),
                                    maxFontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Quick date-range presets
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = TerminologyManager.getTerm("Quick Range"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB8A0D0),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(datePresets()) { preset ->
                            val isActive = fromDate.time == preset.from.time
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isActive) Color(0xFF6A5ACD) else Color(0x22FFFFFF))
                                    .clickable { fromDate = preset.from; toDate = preset.to }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = preset.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Key Profit / Loss Badge
            item {
                val isProfit = pnlData.netProfitLoss >= 0
                val deltaText = when {
                    profitDelta > 0.01 -> "▲ ₹${String.format(Locale.US, "%,.0f", profitDelta)} vs previous period"
                    profitDelta < -0.01 -> "▼ ₹${String.format(Locale.US, "%,.0f", kotlin.math.abs(profitDelta))} vs previous period"
                    else -> TerminologyManager.getTerm("No change vs previous period")
                }
                val marginText = (if (isProfit) "Margin: %.1f%%".format(pnlData.netProfitMargin) else TerminologyManager.getTerm("Loss")) + "   •   " + deltaText
                MetricCard(
                    title = if (isProfit) TerminologyManager.getTerm("Net Profit (Earnings)") else TerminologyManager.getTerm("Net Loss (Deficit)"),
                    value = Math.abs(pnlData.netProfitLoss),
                    subtitle = marginText,
                    backgroundColor = if (isProfit) Color(0xFF1B5E20).copy(alpha = 0.8f) else Color(0xFFB71C1C).copy(alpha = 0.8f),
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Proper hierarchical P&L statement
            item {
                PnlStatementCard(pnlData)
            }

            // Monthly profit trend
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B4E).copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            TerminologyManager.getTerm("MONTHLY PROFIT TREND"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB8A0D0),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            TerminologyManager.getTerm("Net profit / loss, last 6 months"),
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        MonthlyProfitTrendChart(monthlyTrend)
                    }
                }
            }

            // Expense breakdown by category
            item {
                ExpenseBreakdownCard(expenseBreakdown)
            }

            // Allocation Bar
            item {
                ProfitMarginBar(
                    totalRevenue = pnlData.totalIncomes,
                    netProfit = pnlData.netProfitLoss,
                    totalExpenses = pnlData.totalExpenses
                )
            }

            // Top Expense Breakdown (Leaks)
            if (pnlData.topExpenseLedgers.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF2D1B4E).copy(alpha = 0.6f))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "BIGGEST EXPENSES (WHERE YOUR MONEY GOES)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB8A0D0),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        pnlData.topExpenseLedgers.take(3).forEach { (name, amount) ->
                            val pct = if (pnlData.totalExpenses > 0) (amount / pnlData.totalExpenses).toFloat() else 0f
                            Column(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    ledgers.find { it.name == name }?.let {
                                        selectedLedgerForTransactions = it
                                        showLedgerTransactionsDialog = true
                                    }
                                }.padding(vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    AutoShrinkText(name, maxFontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                    AutoShrinkText("₹ ${String.format(Locale.US, "%,.2f", amount)}", maxFontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x11FFFFFF))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(pct)
                                            .background(Color(0xFFFF9800))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Detailed Toggles
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Incomes Detailed Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B4E).copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showIncomeDetails = !showIncomeDetails },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(TerminologyManager.getTerm("Income Details"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AutoShrinkText("₹ ${String.format(Locale.US, "%,.2f", pnlData.totalIncomes)}", maxFontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = if (showIncomeDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (showIncomeDetails) {
                                Spacer(modifier = Modifier.height(12.dp))
                                pnlData.incomes.forEach { (group, amount) ->
                                    val isGroupExpanded = expandedIncomeGroups.contains(group)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF37245C))
                                                .clickable {
                                                    if (group in salesGroupNames) {
                                                        showSalesProfitabilityDialog = true
                                                    } else {
                                                        if (isGroupExpanded) expandedIncomeGroups.remove(group)
                                                        else expandedIncomeGroups.add(group)
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (group in salesGroupNames) Icons.Default.ChevronRight else (if (isGroupExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown),
                                                    contentDescription = null,
                                                    tint = Color(0xFFB8A0D0),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                AutoShrinkText(group, fontWeight = FontWeight.SemiBold, color = Color.White, maxFontSize = 12.sp, minFontSize = 9.sp)
                                            }
                                            AutoShrinkText("₹ ${String.format(Locale.US, "%,.2f", amount)}", fontWeight = FontWeight.Bold, color = Color.White, maxFontSize = 12.sp, minFontSize = 9.sp)
                                        }

                                        if (isGroupExpanded && group !in salesGroupNames) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0x11FFFFFF))
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val groupLedgers = ledgers.filter { it.group == group }
                                                val ledgerBalances = groupLedgers.mapNotNull { ledger ->
                                                    val bal = pnlData.ledgerBalances[ledger.id] ?: 0.0
                                                    val displayBal = -bal
                                                    if (displayBal != 0.0) ledger to displayBal else null
                                                }
                                                
                                                if (ledgerBalances.isEmpty()) {
                                                    Text(TerminologyManager.getTerm("No ledger balances in this period"), color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                                                } else {
                                                    ledgerBalances.forEach { (ledger, bal) ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .clickable {
                                                                    selectedLedgerForTransactions = ledger
                                                                    showLedgerTransactionsDialog = true
                                                                }
                                                                .padding(vertical = 6.dp, horizontal = 8.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            AutoShrinkText(
                                                                text = ledger.name,
                                                                maxFontSize = 11.sp,
                                                                minFontSize = 8.sp,
                                                                color = Color(0xFFDCD6F7),
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            AutoShrinkText(
                                                                text = "₹ ${String.format(Locale.US, "%,.2f", bal)}",
                                                                maxFontSize = 11.sp,
                                                                minFontSize = 8.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = Color.White
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Expenses Detailed Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B4E).copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showExpenseDetails = !showExpenseDetails },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(TerminologyManager.getTerm("Expense Details"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AutoShrinkText("₹ ${String.format(Locale.US, "%,.2f", pnlData.totalExpenses)}", maxFontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = if (showExpenseDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (showExpenseDetails) {
                                Spacer(modifier = Modifier.height(12.dp))
                                pnlData.expenses.forEach { (group, amount) ->
                                    val isGroupExpanded = expandedExpenseGroups.contains(group)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF37245C))
                                                .clickable {
                                                    if (group == ProfitCalculator.COGS_LABEL) {
                                                        showPurchaseDetailDialog = true
                                                    } else {
                                                        if (isGroupExpanded) expandedExpenseGroups.remove(group)
                                                        else expandedExpenseGroups.add(group)
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (group == ProfitCalculator.COGS_LABEL) Icons.Default.ChevronRight else (if (isGroupExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown),
                                                    contentDescription = null,
                                                    tint = Color(0xFFB8A0D0),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                AutoShrinkText(group, fontWeight = FontWeight.SemiBold, color = Color.White, maxFontSize = 12.sp, minFontSize = 9.sp)
                                            }
                                            AutoShrinkText("₹ ${String.format(Locale.US, "%,.2f", amount)}", fontWeight = FontWeight.Bold, color = Color.White, maxFontSize = 12.sp, minFontSize = 9.sp)
                                        }

                                        if (isGroupExpanded && group != ProfitCalculator.COGS_LABEL) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0x11FFFFFF))
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val groupLedgers = ledgers.filter { it.group == group }
                                                val ledgerBalances = groupLedgers.mapNotNull { ledger ->
                                                    val bal = pnlData.ledgerBalances[ledger.id] ?: 0.0
                                                    if (bal != 0.0) ledger to bal else null
                                                }
                                                
                                                if (ledgerBalances.isEmpty()) {
                                                    Text(TerminologyManager.getTerm("No ledger balances in this period"), color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                                                } else {
                                                    ledgerBalances.forEach { (ledger, bal) ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .clickable {
                                                                    selectedLedgerForTransactions = ledger
                                                                    showLedgerTransactionsDialog = true
                                                                }
                                                                .padding(vertical = 6.dp, horizontal = 8.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            AutoShrinkText(
                                                                text = ledger.name,
                                                                maxFontSize = 11.sp,
                                                                minFontSize = 8.sp,
                                                                color = Color(0xFFDCD6F7),
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            AutoShrinkText(
                                                                text = "₹ ${String.format(Locale.US, "%,.2f", bal)}",
                                                                maxFontSize = 11.sp,
                                                                minFontSize = 8.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = Color.White
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSalesProfitabilityDialog) {
        SalesProfitabilityDialog(
            stockItems = costingStockItems,
            units = allUnits,
            // Whole period (sales AND sales returns), costed from the full history.
            periodVouchers = allVouchersWithAllEntries,
            costLedger = costLedger,
            companyName = company?.name ?: "Teller",
            onDismissRequest = { showSalesProfitabilityDialog = false }
        )
    }

    if (showPurchaseDetailDialog) {
        PurchaseDetailDialog(
            stockItems = allStockItems,
            units = allUnits,
            purchaseVouchers = allVouchersWithAllEntries.filter { it.voucher.type == "Purchase" },
            companyName = company?.name ?: "Teller",
            onDismissRequest = { showPurchaseDetailDialog = false }
        )
    }

    if (showLedgerTransactionsDialog && selectedLedgerForTransactions != null) {
        LedgerTransactionsDialog(
            ledger = selectedLedgerForTransactions!!,
            vouchers = allVouchersWithAllEntries,
            navController = navController,
            onDismissRequest = {
                showLedgerTransactionsDialog = false
                selectedLedgerForTransactions = null
            }
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: Double,
    subtitle: String? = null,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(vertical = 4.dp)
            .border(1.dp, contentColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor.copy(alpha = 0.8f),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            AutoShrinkText(
                text = "₹ ${String.format(Locale.US, "%,.2f", value)}",
                maxFontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = contentColor
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun ProfitMarginBar(totalRevenue: Double, netProfit: Double, totalExpenses: Double) {
    val profitPercentage = if (totalRevenue > 0 && netProfit > 0) (netProfit / totalRevenue).toFloat() else 0f
    val expensePercentage = if (totalRevenue > 0) (totalExpenses / totalRevenue).toFloat().coerceAtMost(1f) else 1f
    
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = TerminologyManager.getTerm("REVENUE ALLOCATION"),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFB8A0D0),
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x22FFFFFF))
        ) {
            if (netProfit > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(profitPercentage.coerceAtLeast(0.01f))
                        .background(Color(0xFF00E676))
                )
            }
            if (totalExpenses > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(expensePercentage.coerceAtLeast(0.01f))
                        .background(Color(0xFFFF5252))
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (netProfit > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF00E676), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    AutoShrinkText("Profit: ${(profitPercentage * 100).toInt()}%", maxFontSize = 10.sp, minFontSize = 7.sp, color = Color.White)
                }
            }
            if (totalExpenses > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF5252), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    AutoShrinkText("Expenses: ${(expensePercentage * 100).toInt()}%", maxFontSize = 10.sp, minFontSize = 7.sp, color = Color.White)
                }
            }
        }
    }
}

private data class DatePreset(val label: String, val from: Date, val to: Date)

private fun datePresets(): List<DatePreset> {
    fun startOfDay(c: Calendar): Date {
        val s = c.clone() as Calendar
        s.set(Calendar.HOUR_OF_DAY, 0); s.set(Calendar.MINUTE, 0); s.set(Calendar.SECOND, 0); s.set(Calendar.MILLISECOND, 0)
        return s.time
    }
    fun endOfDay(c: Calendar): Date {
        val e = c.clone() as Calendar
        e.set(Calendar.HOUR_OF_DAY, 23); e.set(Calendar.MINUTE, 59); e.set(Calendar.SECOND, 59); e.set(Calendar.MILLISECOND, 999)
        return e.time
    }

    val now = Calendar.getInstance()

    val thisMonth = now.clone() as Calendar
    thisMonth.set(Calendar.DAY_OF_MONTH, 1)

    val lastMonth = now.clone() as Calendar
    lastMonth.add(Calendar.MONTH, -1)
    lastMonth.set(Calendar.DAY_OF_MONTH, 1)
    val lastMonthEnd = lastMonth.clone() as Calendar
    lastMonthEnd.set(Calendar.DAY_OF_MONTH, lastMonthEnd.getActualMaximum(Calendar.DAY_OF_MONTH))

    val quarter = now.clone() as Calendar
    quarter.set(Calendar.MONTH, (now.get(Calendar.MONTH) / 3) * 3)
    quarter.set(Calendar.DAY_OF_MONTH, 1)

    val year = now.clone() as Calendar
    year.set(now.get(Calendar.YEAR), Calendar.JANUARY, 1)

    val fy = now.clone() as Calendar
    val fyYear = if (now.get(Calendar.MONTH) >= Calendar.APRIL) now.get(Calendar.YEAR) else now.get(Calendar.YEAR) - 1
    fy.set(fyYear, Calendar.APRIL, 1)

    return listOf(
        DatePreset("This Month", startOfDay(thisMonth), endOfDay(now)),
        DatePreset("Last Month", startOfDay(lastMonth), endOfDay(lastMonthEnd)),
        DatePreset("This Quarter", startOfDay(quarter), endOfDay(now)),
        DatePreset("This Year", startOfDay(year), endOfDay(now)),
        DatePreset("This FY", startOfDay(fy), endOfDay(now))
    )
}

@Composable
fun PnlStatementCard(pnlData: PnLData) {
    // Laid out so each line follows from the one above:
    //   Sales − Cost of goods sold = Gross profit;  Gross profit + Other income − Expenses = Net profit
    val otherIncome = pnlData.totalIncomes - pnlData.totalSales
    val operatingExpenses = pnlData.totalExpenses - pnlData.totalPurchases
    val isProfit = pnlData.netProfitLoss >= 0
    val isGrossProfit = pnlData.grossProfit >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B4E).copy(alpha = 0.55f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    TerminologyManager.getTerm("PROFIT & LOSS STATEMENT"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB8A0D0),
                    letterSpacing = 0.5.sp
                )
                Icon(Icons.Default.TableChart, null, tint = Color(0xFFB8A0D0), modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))

            StatementRow(TerminologyManager.getTerm("Revenue (Sales)"), pnlData.totalSales, Color.White, FontWeight.Medium)
            StatementRow(TerminologyManager.getTerm("Less: Cost of Goods Sold"), pnlData.totalPurchases, Color(0xFFFF8A80), FontWeight.Normal)
            StatementRow(
                TerminologyManager.getTerm(if (isGrossProfit) "Gross Profit" else "Gross Loss"),
                kotlin.math.abs(pnlData.grossProfit),
                if (isGrossProfit) Color(0xFF00E676) else Color(0xFFFF5252),
                FontWeight.Bold,
                subtitle = "%.1f%%".format(pnlData.grossProfitMargin)
            )
            if (pnlData.itemsWithoutCost > 0) {
                Text(
                    "⚠ ${pnlData.itemsWithoutCost} " + TerminologyManager.getTerm("item(s) sold have no purchase price saved, so profit may look higher than it really is. Add their purchase to fix it.") +
                        " (₹ ${String.format(Locale.US, "%,.2f", pnlData.salesWithoutCost)})",
                    fontSize = 10.sp,
                    color = Color(0xFFFFC107),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            HorizontalDivider(color = Color(0x22FFFFFF), modifier = Modifier.padding(vertical = 6.dp))

            if (kotlin.math.abs(otherIncome) >= 0.005) StatementRow(TerminologyManager.getTerm("Add: Other Income"), otherIncome, Color(0xFFB8A0D0), FontWeight.Normal)
            StatementRow(TerminologyManager.getTerm("Less: Operating Expenses"), operatingExpenses, Color(0xFFFF8A80), FontWeight.Normal)
            StatementRow(
                if (isProfit) "Net Profit" else "Net Loss",
                kotlin.math.abs(pnlData.netProfitLoss),
                if (isProfit) Color(0xFF00E676) else Color(0xFFFF5252),
                FontWeight.ExtraBold,
                subtitle = "%.1f%%".format(pnlData.netProfitMargin),
                emphasized = true
            )
        }
    }
}

@Composable
fun StatementRow(
    label: String,
    amount: Double,
    color: Color,
    weight: FontWeight,
    subtitle: String? = null,
    emphasized: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (emphasized) 4.dp else 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (emphasized) Color(0x1A00E676) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = if (emphasized) 8.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutoShrinkText(label, maxFontSize = if (emphasized) 13.sp else 12.sp, minFontSize = 9.sp, fontWeight = weight, color = Color.White.copy(alpha = 0.9f))
            AutoShrinkText("₹ ${String.format(Locale.US, "%,.2f", amount)}", maxFontSize = if (emphasized) 15.sp else 12.sp, minFontSize = 9.sp, fontWeight = weight, color = color)
        }
        if (subtitle != null) {
            AutoShrinkText(subtitle, maxFontSize = 10.sp, minFontSize = 7.sp, color = color.copy(alpha = 0.85f), modifier = Modifier.align(Alignment.End))
        }
    }
}

private fun calculateMonthlyProfit(
    ledgers: List<Ledger>,
    vouchers: List<VoucherWithAllEntries>,
    roles: ProfitCalculator.LedgerRoles,
    costLedger: ProfitCalculator.CostLedger,
    months: Int = 6
): List<Pair<String, Double>> {
    val monthFormatter = SimpleDateFormat("MMM", Locale.getDefault())
    val result = mutableListOf<Pair<String, Double>>()

    val base = Calendar.getInstance()
    base.set(Calendar.DAY_OF_MONTH, 1)
    base.set(Calendar.HOUR_OF_DAY, 0); base.set(Calendar.MINUTE, 0); base.set(Calendar.SECOND, 0); base.set(Calendar.MILLISECOND, 0)

    for (i in months - 1 downTo 0) {
        val startCal = base.clone() as Calendar
        startCal.add(Calendar.MONTH, -i)
        val endCal = startCal.clone() as Calendar
        endCal.add(Calendar.MONTH, 1)
        endCal.add(Calendar.MILLISECOND, -1)

        val monthVouchers = vouchers.filter {
            it.voucher.date.time in startCal.time.time..endCal.time.time
        }
        val pnl = calculateProfitAndLoss(ledgers, monthVouchers, roles, costLedger)
        result.add(monthFormatter.format(startCal.time) to pnl.netProfitLoss)
    }
    return result
}

private fun formatCompactAmount(value: Double): String {
    val abs = kotlin.math.abs(value)
    return when {
        abs >= 10000000 -> String.format("₹%.1fCr", value / 10000000)
        abs >= 100000 -> String.format("₹%.1fL", value / 100000)
        abs >= 1000 -> String.format("₹%.0fK", value / 1000)
        else -> String.format("₹%.0f", value)
    }
}

@Composable
fun MonthlyProfitTrendChart(monthly: List<Pair<String, Double>>) {
    if (monthly.isEmpty()) {
        Text(TerminologyManager.getTerm("No data for this period"), color = Color.Gray, fontSize = 12.sp)
        return
    }
    val green = Color(0xFF00E676)
    val red = Color(0xFFFF5252)
    val maxAbs = monthly.maxOf { kotlin.math.abs(it.second) }.coerceAtLeast(1.0)

    Canvas(modifier = Modifier.fillMaxWidth().height(170.dp)) {
        val chartLeft = 6.dp.toPx()
        val chartRight = size.width - 6.dp.toPx()
        val chartTop = 16.dp.toPx()
        val chartBottom = size.height - 26.dp.toPx()
        val midY = chartTop + (chartBottom - chartTop) / 2f

        // zero baseline
        drawLine(
            Color.White.copy(alpha = 0.25f),
            Offset(chartLeft, midY),
            Offset(chartRight, midY),
            strokeWidth = 2f
        )

        val n = monthly.size.coerceAtLeast(1)
        val slot = (chartRight - chartLeft) / n
        val barWidth = slot * 0.55f

        val valuePaint = Paint().apply {
            textAlign = Paint.Align.CENTER
            textSize = 10.dp.toPx()
            color = 0xFFFFFFFF.toInt()
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            textAlign = Paint.Align.CENTER
            textSize = 11.dp.toPx()
            color = 0xFFB8A0D0.toInt()
            isAntiAlias = true
        }

        monthly.forEachIndexed { i, (label, value) ->
            val cx = chartLeft + slot * i + slot / 2f
            val x = chartLeft + slot * i + (slot - barWidth) / 2f
            val rawBarH = ((kotlin.math.abs(value) / maxAbs).toFloat()) * ((chartBottom - chartTop) / 2f)
            val barH = if (value != 0.0) rawBarH.coerceAtLeast(2f) else 0f
            val color = if (value >= 0) green else red

            if (barH > 0f) {
                val top = if (value >= 0) midY - barH else midY
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, top),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                val valueY = if (value >= 0) top - 6.dp.toPx() else top + barH + 16.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(formatCompactAmount(value), cx, valueY, valuePaint)
            }

            drawContext.canvas.nativeCanvas.drawText(label, cx, chartBottom + 18.dp.toPx(), labelPaint)
        }
    }
}

@Composable
fun ExpenseBreakdownCard(expenses: List<Pair<String, Double>>) {
    if (expenses.isEmpty()) return
    val total = expenses.sumOf { it.second }
    if (total <= 0.0) return

    val colors = listOf(
        Color(0xFF00BCD4), Color(0xFFE91E63), Color(0xFFFFB300),
        Color(0xFF9C27B0), Color(0xFF42A5F5), Color(0xFF66BB6A)
    )

    val top = expenses.take(6)
    val otherSum = expenses.drop(6).sumOf { it.second }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B4E).copy(alpha = 0.55f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                TerminologyManager.getTerm("EXPENSE BREAKDOWN"),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB8A0D0),
                letterSpacing = 0.5.sp
            )
            Text(
                TerminologyManager.getTerm("Where your money goes (by category)"),
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            top.forEachIndexed { i, (name, amount) ->
                val pct = (amount / total).toFloat().coerceIn(0f, 1f)
                ExpenseBar(name, amount, pct, colors[i % colors.size])
            }

            if (otherSum > 0.0) {
                val pct = (otherSum / total).toFloat().coerceIn(0f, 1f)
                ExpenseBar(TerminologyManager.getTerm("Other"), otherSum, pct, Color(0xFF607D8B))
            }
        }
    }
}

@Composable
private fun ExpenseBar(name: String, amount: Double, pct: Float, color: Color) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                AutoShrinkText(
                    name,
                    maxFontSize = 12.sp,
                    minFontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                AutoShrinkText("₹ ${String.format(Locale.US, "%,.2f", amount)}", maxFontSize = 12.sp, minFontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                AutoShrinkText("${(pct * 100).toInt()}%", maxFontSize = 10.sp, minFontSize = 7.sp, color = color.copy(alpha = 0.9f))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x11FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

data class PnLData(
    val incomes: Map<String, Double>,
    val expenses: Map<String, Double>,
    val totalIncomes: Double,
    val totalExpenses: Double,
    val netProfitLoss: Double,
    val totalSales: Double,
    val totalPurchases: Double,
    val topExpenseLedgers: List<Pair<String, Double>>,
    val grossProfit: Double,
    val grossProfitMargin: Double,
    val netProfitMargin: Double,
    val ledgerBalances: Map<Int, Double>,
    /** Sale value (without GST) of items that have no purchase price saved. */
    val salesWithoutCost: Double = 0.0,
    /** How many such items were sold in the period. */
    val itemsWithoutCost: Int = 0
)

data class SalesItemProfitability(
    val stockItem: StockItem,
    val quantitySold: Double,
    val unitName: String,
    val avgSellingRate: Double,
    val totalSalesAmount: Double,
    val avgPurchaseRate: Double,
    val totalPurchaseCost: Double,
    val profit: Double,
    val marginPercentage: Double,
    /** False when the item was never bought and has no opening rate, so its cost is unknown. */
    val costKnown: Boolean = true
)

data class PurchaseItemDetail(
    val stockItem: StockItem,
    val quantityPurchased: Double,
    val unitName: String,
    val avgPurchaseRate: Double,
    val totalPurchaseAmount: Double
)

/**
 * Profit & Loss for the given period's vouchers. All sums are done in exact paise by
 * [ProfitCalculator] and converted to rupees only here, for display.
 *
 * (Before: ledger amounts — stored in paise — were summed as if they were rupees while the cost of
 * goods was in rupees, so sales looked 100× bigger than their cost and every report showed a ~99%
 * gross margin, even for goods sold at a loss. Cost also used the LAST purchase price, ignored
 * sales returns, and the previous-period / monthly figures had no purchase history at all.)
 */
private fun calculateProfitAndLoss(
    ledgers: List<Ledger>,
    vouchersWithAllEntries: List<VoucherWithAllEntries>,
    roles: ProfitCalculator.LedgerRoles = ProfitCalculator.LedgerRoles.EMPTY,
    costLedger: ProfitCalculator.CostLedger = ProfitCalculator.CostLedger.EMPTY
): PnLData {
    val pnl = ProfitCalculator.profitAndLoss(ledgers, vouchersWithAllEntries, roles, costLedger)

    val totalSales = pnl.salesPaise.paiseToRupees()
    val totalIncomes = pnl.totalIncomePaise.paiseToRupees()
    val grossProfit = pnl.grossProfitPaise.paiseToRupees()
    val netProfitLoss = pnl.netProfitPaise.paiseToRupees()

    val grossProfitMargin = if (pnl.salesPaise > 0L) grossProfit / totalSales * 100.0 else 0.0
    // Net margin is measured on sales (the usual definition); falls back to total income when
    // there were no trading sales in the period.
    val netProfitMargin = when {
        pnl.salesPaise > 0L -> netProfitLoss / totalSales * 100.0
        pnl.totalIncomePaise > 0L -> netProfitLoss / totalIncomes * 100.0
        else -> 0.0
    }

    return PnLData(
        incomes = pnl.incomesByGroup.mapValues { it.value.paiseToRupees() },
        expenses = pnl.expensesByGroup.mapValues { it.value.paiseToRupees() },
        totalIncomes = totalIncomes,
        totalExpenses = pnl.totalExpensePaise.paiseToRupees(),
        netProfitLoss = netProfitLoss,
        totalSales = totalSales,
        // Cost of goods sold (purchases of goods still in stock are NOT a cost yet).
        totalPurchases = pnl.cogs.cogsPaise.paiseToRupees(),
        topExpenseLedgers = pnl.expenseLedgers
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
            .map { it.first.name to it.second.paiseToRupees() },
        grossProfit = grossProfit,
        grossProfitMargin = grossProfitMargin,
        netProfitMargin = netProfitMargin,
        ledgerBalances = pnl.ledgerMovement.mapValues { it.value.paiseToRupees() },
        salesWithoutCost = pnl.cogs.salesWithoutCostPaise.paiseToRupees(),
        itemsWithoutCost = pnl.cogs.itemsWithoutCost
    )
}

/**
 * Item-wise profit for the period: sale value without GST minus what those goods cost (weighted-
 * average purchase cost on the day of sale, without GST), net of sales returns.
 */
private fun calculateSalesProfitability(
    stockItems: List<StockItem>,
    units: List<com.minhas.teller.data.entities.Unit>,
    periodVouchers: List<VoucherWithAllEntries>,
    costLedger: ProfitCalculator.CostLedger
): List<SalesItemProfitability> {
    val itemById = stockItems.associateBy { it.id }
    val unitById = units.associateBy { it.id }
    val periodIds = periodVouchers.mapTo(HashSet()) { it.voucher.id }

    return ProfitCalculator.itemProfits(costLedger, ProfitCalculator.inVouchers(periodIds)).map { p ->
        val stockItem = itemById[p.itemKey] ?: StockItem(id = p.itemKey, name = p.name, unitId = 0)
        val revenue = p.revenuePaise.paiseToRupees()
        val cost = if (p.costKnown) p.costPaise.paiseToRupees() else 0.0
        SalesItemProfitability(
            stockItem = stockItem,
            quantitySold = p.quantity,
            unitName = unitById[stockItem.unitId]?.name ?: "",
            avgSellingRate = p.avgSaleRate,
            totalSalesAmount = revenue,
            avgPurchaseRate = if (p.costKnown) p.avgCostRate else 0.0,
            totalPurchaseCost = cost,
            profit = revenue - cost,
            marginPercentage = if (p.costKnown) p.marginPercent else 0.0,
            costKnown = p.costKnown
        )
    }.sortedByDescending { it.totalSalesAmount }
}

private fun calculatePurchaseDetails(
    stockItems: List<StockItem>,
    units: List<com.minhas.teller.data.entities.Unit>,
    purchaseVouchers: List<VoucherWithAllEntries>
): List<PurchaseItemDetail> {
    val purchaseEntries = purchaseVouchers.flatMap { it.inventoryEntries }
    val groupedPurchases = purchaseEntries.groupBy { it.stockItemId }
    
    return groupedPurchases.mapNotNull { (stockItemId, entries) ->
        val stockItem = stockItems.find { it.id == stockItemId } ?: return@mapNotNull null
        val unitName = units.find { it.id == stockItem.unitId }?.name ?: ""
        
        val totalQty = entries.sumOf { it.quantity }
        val totalAmt = entries.sumOf { it.amount }.paiseToRupees()
        val avgRate = if (totalQty > 0.0) totalAmt / totalQty else 0.0
        
        PurchaseItemDetail(
            stockItem = stockItem,
            quantityPurchased = totalQty,
            unitName = unitName,
            avgPurchaseRate = avgRate,
            totalPurchaseAmount = totalAmt
        )
    }.sortedByDescending { it.totalPurchaseAmount }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesProfitabilityDialog(
    stockItems: List<StockItem>,
    units: List<com.minhas.teller.data.entities.Unit>,
    periodVouchers: List<VoucherWithAllEntries>,
    costLedger: ProfitCalculator.CostLedger,
    companyName: String,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    val profitabilityItems = rememberInBackground(stockItems, units, periodVouchers, costLedger, initial = emptyList<SalesItemProfitability>()) {
        calculateSalesProfitability(stockItems, units, periodVouchers, costLedger)
    }
    
    val filteredItems = remember(profitabilityItems, searchQuery) {
        profitabilityItems.filter { it.stockItem.name.contains(searchQuery, ignoreCase = true) }
    }
    
    val totalRevenue = filteredItems.sumOf { it.totalSalesAmount }
    val totalCost = filteredItems.sumOf { it.totalPurchaseCost }
    val totalProfit = totalRevenue - totalCost
    val avgMargin = if (totalRevenue > 0.0) (totalProfit / totalRevenue) * 100.0 else 0.0
    val itemsWithoutCost = filteredItems.count { !it.costKnown }
    
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            color = Color(0xFF0F0B25).copy(alpha = 0.98f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = TerminologyManager.getTerm("Sales Profitability Report"),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = TerminologyManager.getTerm("Product-wise gross margins & performance"),
                            fontSize = 11.sp,
                            color = Color(0xFFB8A0D0)
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val headers = listOf("Stock Item", "Qty Sold", "Avg Sale Rate", "Avg Purchase Rate", "Total Sales", "Total Purchase Cost", "Gross Profit", "Margin %")
                            val exportData = filteredItems.map { item ->
                                listOf(
                                    item.stockItem.name,
                                    "%.2f %s".format(item.quantitySold, item.unitName),
                                    "%.2f".format(item.avgSellingRate),
                                    if (item.costKnown) "%.2f".format(item.avgPurchaseRate) else "-",
                                    "%.2f".format(item.totalSalesAmount),
                                    if (item.costKnown) "%.2f".format(item.totalPurchaseCost) else "No purchase price",
                                    if (item.costKnown) "%.2f".format(item.profit) else "-",
                                    if (item.costKnown) "%.1f%%".format(item.marginPercentage) else "-"
                                )
                            }
                            ExportUtils.exportReportToPdf(
                                context,
                                "Sales_Profitability_${System.currentTimeMillis()}.pdf",
                                companyName,
                                "Sales Profitability Analysis",
                                headers,
                                floatArrayOf(2.2f, 1.2f, 1.4f, 1.4f, 1.4f, 1.4f, 1.4f, 1.2f),
                                exportData
                            )
                        }) {
                            Icon(Icons.Default.PictureAsPdf, TerminologyManager.getTerm("PDF"), tint = Color.White)
                        }

                        IconButton(onClick = {
                            val headers = listOf("Stock Item", "Qty Sold", "Avg Sale Rate", "Avg Purchase Rate", "Total Sales", "Total Purchase Cost", "Gross Profit", "Margin %")
                            val exportData = filteredItems.map { item ->
                                listOf(
                                    item.stockItem.name,
                                    "%.2f %s".format(item.quantitySold, item.unitName),
                                    "%.2f".format(item.avgSellingRate),
                                    if (item.costKnown) "%.2f".format(item.avgPurchaseRate) else "-",
                                    "%.2f".format(item.totalSalesAmount),
                                    if (item.costKnown) "%.2f".format(item.totalPurchaseCost) else "No purchase price",
                                    if (item.costKnown) "%.2f".format(item.profit) else "-",
                                    if (item.costKnown) "%.1f%%".format(item.marginPercentage) else "-"
                                )
                            }
                            ExportUtils.exportReportToExcel(
                                context,
                                "Sales_Profitability_${System.currentTimeMillis()}.xlsx",
                                "Sales Profitability",
                                headers,
                                exportData
                            )
                        }) {
                            Icon(Icons.Default.TableChart, TerminologyManager.getTerm("Excel"), tint = Color.White)
                        }

                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, TerminologyManager.getTerm("Close"), tint = Color.White)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Search Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2D1B4E).copy(alpha = 0.6f))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = Color(0xFFB8A0D0), modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(TerminologyManager.getTerm("Search sold products..."), color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Hero Metrics Summary Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCardMini(
                        title = TerminologyManager.getTerm("Revenue"),
                        value = totalRevenue,
                        color = Color(0xFF00E676),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCardMini(
                        title = TerminologyManager.getTerm("Purchase COGS"),
                        value = totalCost,
                        color = Color(0xFFFF9800),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCardMini(
                        title = TerminologyManager.getTerm("Gross Profit"),
                        value = totalProfit,
                        subtitle = "Margin: %.1f%%".format(avgMargin),
                        color = if (totalProfit >= 0) Color(0xFF00E676) else Color(0xFFFF5252),
                        modifier = Modifier.weight(1.2f)
                    )
                }

                Text(
                    text = TerminologyManager.getTerm("All amounts are without GST. GST is collected for the government, it is not your profit."),
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (itemsWithoutCost > 0) {
                    Text(
                        text = "⚠ $itemsWithoutCost " + TerminologyManager.getTerm("item(s) sold have no purchase price saved, so profit may look higher than it really is. Add their purchase to fix it."),
                        fontSize = 10.sp,
                        color = Color(0xFFFFC107),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Products List
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(TerminologyManager.getTerm("No sales matching filter found."), color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(filteredItems) { item ->
                            SalesProfitabilityItemCard(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCardMini(
    title: String,
    value: Double,
    subtitle: String? = null,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(0.5.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B4E).copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(title.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8A0D0))
            Spacer(modifier = Modifier.height(2.dp))
            AutoShrinkText("₹ ${String.format(Locale.US, "%,.2f", value)}", maxFontSize = 13.sp, fontWeight = FontWeight.Black, color = color)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(1.dp))
                AutoShrinkText(subtitle, maxFontSize = 9.sp, minFontSize = 7.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun SalesProfitabilityItemCard(item: SalesItemProfitability) {
    val isProfit = item.profit >= 0
    // No purchase price saved for this item: show that plainly instead of a fake 100% margin.
    val noCostColor = Color(0xFFFFC107)
    val badgeBg = if (!item.costKnown) noCostColor.copy(alpha = 0.2f) else if (isProfit) Color(0x3300E676) else Color(0x33FF5252)
    val badgeBorder = if (!item.costKnown) noCostColor else if (isProfit) Color(0xFF00E676) else Color(0xFFFF5252)
    val badgeTextColor = if (!item.costKnown) noCostColor else if (isProfit) Color(0xFF00E676) else Color(0xFFFF5252)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B4E).copy(alpha = 0.3f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: Title & Margin Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutoShrinkText(
                    text = item.stockItem.name,
                    maxFontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .border(0.5.dp, badgeBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    AutoShrinkText(
                        text = if (!item.costKnown) TerminologyManager.getTerm("No purchase price")
                               else "${if (isProfit) "+" else ""}${String.format("%.1f", item.marginPercentage)}% Margin",
                        maxFontSize = 10.sp,
                        minFontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Row 2: Quantities
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = TerminologyManager.getTerm("Quantity Sold: "),
                    fontSize = 11.sp,
                    color = Color(0xFFB8A0D0)
                )
                AutoShrinkText(
                    text = "${String.format("%.2f", item.quantitySold)} ${item.unitName}",
                    maxFontSize = 11.sp,
                    minFontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            HorizontalDivider(color = Color(0x11FFFFFF), thickness = 0.5.dp)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Row 3: Calculations Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(TerminologyManager.getTerm("SALE VALUE"), fontSize = 8.sp, color = Color(0xFFB8A0D0))
                    AutoShrinkText("₹ ${String.format(Locale.US, "%,.2f", item.totalSalesAmount)}", maxFontSize = 12.sp, minFontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    AutoShrinkText("Avg Rate: ₹ ${String.format("%.2f", item.avgSellingRate)}", maxFontSize = 9.sp, minFontSize = 7.sp, color = Color.LightGray)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(TerminologyManager.getTerm("PURCHASE COST"), fontSize = 8.sp, color = Color(0xFFB8A0D0))
                    AutoShrinkText(if (item.costKnown) "₹ ${String.format(Locale.US, "%,.2f", item.totalPurchaseCost)}" else "—", maxFontSize = 12.sp, minFontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    AutoShrinkText(if (item.costKnown) "Avg Cost: ₹ ${String.format("%.2f", item.avgPurchaseRate)}" else TerminologyManager.getTerm("Not bought yet"), maxFontSize = 9.sp, minFontSize = 7.sp, color = Color.LightGray)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(TerminologyManager.getTerm("GROSS PROFIT"), fontSize = 8.sp, color = Color(0xFFB8A0D0))
                    AutoShrinkText(
                        text = if (item.costKnown) "₹ ${String.format(Locale.US, "%,.2f", item.profit)}" else "—",
                        maxFontSize = 12.sp,
                        minFontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!item.costKnown) noCostColor else if (isProfit) Color(0xFF00E676) else Color(0xFFFF5252)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseDetailDialog(
    stockItems: List<StockItem>,
    units: List<com.minhas.teller.data.entities.Unit>,
    purchaseVouchers: List<VoucherWithAllEntries>,
    companyName: String,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    val purchaseItems = rememberInBackground(stockItems, units, purchaseVouchers, initial = emptyList<PurchaseItemDetail>()) {
        calculatePurchaseDetails(stockItems, units, purchaseVouchers)
    }
    
    val filteredItems = remember(purchaseItems, searchQuery) {
        purchaseItems.filter { it.stockItem.name.contains(searchQuery, ignoreCase = true) }
    }
    
    val totalPurchasesVal = filteredItems.sumOf { it.totalPurchaseAmount }
    
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            color = Color(0xFF0F0B25).copy(alpha = 0.98f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = TerminologyManager.getTerm("Purchase Analysis Report"),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = TerminologyManager.getTerm("Product-wise purchase volumes & costs"),
                            fontSize = 11.sp,
                            color = Color(0xFFB8A0D0)
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val headers = listOf("Stock Item", "Qty Purchased", "Avg Purchase Rate", "Total Purchase Value")
                            val exportData = filteredItems.map { item ->
                                listOf(
                                    item.stockItem.name,
                                    "%.2f %s".format(item.quantityPurchased, item.unitName),
                                    "%.2f".format(item.avgPurchaseRate),
                                    "%.2f".format(item.totalPurchaseAmount)
                                )
                            }
                            ExportUtils.exportReportToPdf(
                                context,
                                "Purchase_Details_${System.currentTimeMillis()}.pdf",
                                companyName,
                                "Purchase Volume Details",
                                headers,
                                floatArrayOf(3f, 2f, 2f, 2.5f),
                                exportData
                            )
                        }) {
                            Icon(Icons.Default.PictureAsPdf, TerminologyManager.getTerm("PDF"), tint = Color.White)
                        }

                        IconButton(onClick = {
                            val headers = listOf("Stock Item", "Qty Purchased", "Avg Purchase Rate", "Total Purchase Value")
                            val exportData = filteredItems.map { item ->
                                listOf(
                                    item.stockItem.name,
                                    "%.2f %s".format(item.quantityPurchased, item.unitName),
                                    "%.2f".format(item.avgPurchaseRate),
                                    "%.2f".format(item.totalPurchaseAmount)
                                )
                            }
                            ExportUtils.exportReportToExcel(
                                context,
                                "Purchase_Details_${System.currentTimeMillis()}.xlsx",
                                "Purchase Details",
                                headers,
                                exportData
                            )
                        }) {
                            Icon(Icons.Default.TableChart, TerminologyManager.getTerm("Excel"), tint = Color.White)
                        }

                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, TerminologyManager.getTerm("Close"), tint = Color.White)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Search Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2D1B4E).copy(alpha = 0.6f))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = Color(0xFFB8A0D0), modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(TerminologyManager.getTerm("Search purchased products..."), color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Hero metrics
                Card(
                    modifier = Modifier.fillMaxWidth().border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B4E).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(TerminologyManager.getTerm("TOTAL PURCHASE OUTFLOW"), fontSize = 10.sp, color = Color(0xFFB8A0D0), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        AutoShrinkText(
                            text = "₹ ${String.format(Locale.US, "%,.2f", totalPurchasesVal)}",
                            maxFontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF5252)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AutoShrinkText("Items cataloged: ${filteredItems.size}", maxFontSize = 11.sp, minFontSize = 8.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Products List
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(TerminologyManager.getTerm("No purchases matching filter found."), color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(filteredItems) { item ->
                            PurchaseItemCard(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PurchaseItemCard(item: PurchaseItemDetail) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B4E).copy(alpha = 0.3f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1.5f)) {
                AutoShrinkText(
                    text = item.stockItem.name,
                    maxFontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(TerminologyManager.getTerm("Quantity: "), fontSize = 11.sp, color = Color(0xFFB8A0D0))
                    AutoShrinkText("${String.format("%.2f", item.quantityPurchased)} ${item.unitName}", maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(2.dp))
                AutoShrinkText("Avg cost rate: ₹ ${String.format("%.2f", item.avgPurchaseRate)}", maxFontSize = 10.sp, minFontSize = 7.sp, color = Color.LightGray)
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(TerminologyManager.getTerm("PURCHASE VALUE"), fontSize = 8.sp, color = Color(0xFFB8A0D0))
                Spacer(modifier = Modifier.height(2.dp))
                AutoShrinkText(
                    text = "₹ ${String.format(Locale.US, "%,.2f", item.totalPurchaseAmount)}",
                    maxFontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFF5252)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerTransactionsDialog(
    ledger: Ledger,
    vouchers: List<VoucherWithAllEntries>,
    navController: NavHostController,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    
    val ledgerTransactions = remember(ledger, vouchers) {
        vouchers.flatMap { vw ->
            vw.voucherEntries.filter { it.ledgerId == ledger.id }.map { entry ->
                vw.voucher to entry
            }
        }.sortedByDescending { it.first.date }
    }
    
    val totalDebit = ledgerTransactions.filter { it.second.isDebit }.sumOf { it.second.amount }
    val totalCredit = ledgerTransactions.filter { !it.second.isDebit }.sumOf { it.second.amount }
    val netBalance = totalDebit - totalCredit
    
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            color = Color(0xFF0F0B25).copy(alpha = 0.98f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AutoShrinkText(
                            text = ledger.name,
                            maxFontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        AutoShrinkText(
                            text = "Group: ${ledger.group}",
                            maxFontSize = 11.sp,
                            minFontSize = 8.sp,
                            color = Color(0xFFB8A0D0)
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val headers = listOf("Date", "Vch Type", "Vch No", "Particulars", "Debit (Dr)", "Credit (Cr)")
                            val exportData = ledgerTransactions.map { (vch, entry) ->
                                listOf(
                                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(vch.date),
                                    vch.type,
                                    vch.voucherNumber,
                                    vch.narration ?: "",
                                    if (entry.isDebit) "%.2f".format(entry.amount.paiseToRupees()) else "",
                                    if (!entry.isDebit) "%.2f".format(entry.amount.paiseToRupees()) else ""
                                )
                            }
                            ExportUtils.exportReportToPdf(
                                context,
                                "Ledger_${ledger.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf",
                                "",
                                "Ledger Statement: ${ledger.name}",
                                headers,
                                floatArrayOf(1.8f, 1.5f, 1.5f, 3.2f, 1.8f, 1.8f),
                                exportData
                            )
                        }) {
                            Icon(Icons.Default.PictureAsPdf, TerminologyManager.getTerm("PDF"), tint = Color.White)
                        }

                        IconButton(onClick = {
                            val headers = listOf("Date", "Vch Type", "Vch No", "Particulars", "Debit (Dr)", "Credit (Cr)")
                            val exportData = ledgerTransactions.map { (vch, entry) ->
                                listOf(
                                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(vch.date),
                                    vch.type,
                                    vch.voucherNumber,
                                    vch.narration ?: "",
                                    if (entry.isDebit) "%.2f".format(entry.amount.paiseToRupees()) else "",
                                    if (!entry.isDebit) "%.2f".format(entry.amount.paiseToRupees()) else ""
                                )
                            }
                            ExportUtils.exportReportToExcel(
                                context,
                                "Ledger_${ledger.name.replace(" ", "_")}_${System.currentTimeMillis()}.xlsx",
                                "Ledger Transactions",
                                headers,
                                exportData
                            )
                        }) {
                            Icon(Icons.Default.TableChart, TerminologyManager.getTerm("Excel"), tint = Color.White)
                        }

                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, TerminologyManager.getTerm("Close"), tint = Color.White)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Balance Card
                Card(
                    modifier = Modifier.fillMaxWidth().border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B4E).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(TerminologyManager.getTerm("STATEMENT SUMMARY"), fontSize = 10.sp, color = Color(0xFFB8A0D0), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(TerminologyManager.getTerm("Total Debit (Dr)"), fontSize = 11.sp, color = Color.LightGray)
                                AutoShrinkText("₹ ${String.format(Locale.US, "%,.2f", totalDebit)}", maxFontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                            }
                            Column {
                                Text(TerminologyManager.getTerm("Total Credit (Cr)"), fontSize = 11.sp, color = Color.LightGray)
                                AutoShrinkText("₹ ${String.format(Locale.US, "%,.2f", totalCredit)}", maxFontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(TerminologyManager.getTerm("Net Balance"), fontSize = 11.sp, color = Color.LightGray)
                                val signStr = if (netBalance >= 0) "Dr" else "Cr"
                                AutoShrinkText(
                                    text = "₹ ${String.format(Locale.US, "%,.2f", Math.abs(netBalance))} $signStr",
                                    maxFontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = TerminologyManager.getTerm("TRANSACTION ENTRIES"),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB8A0D0),
                    letterSpacing = 0.5.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Transactions List
                if (ledgerTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(TerminologyManager.getTerm("No transaction history in this period."), color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(ledgerTransactions) { (vch, entry) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2D1B4E).copy(alpha = 0.3f))
                                    .border(0.5.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                                    .clickable {
                                        onDismissRequest()
                                        navController.navigate(Screen.VoucherView.route.replace("{voucherId}", vch.id.toString()))
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AutoShrinkText(
                                            text = vch.type,
                                            maxFontSize = 11.sp,
                                            minFontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB8A0D0)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        AutoShrinkText(
                                            text = "#${vch.voucherNumber}",
                                            maxFontSize = 11.sp,
                                            minFontSize = 8.sp,
                                            color = Color.LightGray
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AutoShrinkText(
                                        text = vch.narration ?: TerminologyManager.getTerm("No Narration"),
                                        maxFontSize = 11.sp,
                                        minFontSize = 8.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    AutoShrinkText(
                                        text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(vch.date),
                                        maxFontSize = 9.sp,
                                        minFontSize = 7.sp,
                                        color = Color.Gray
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1.0f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    val sign = if (entry.isDebit) "Dr" else "Cr"
                                    val color = if (entry.isDebit) Color(0xFF00E676) else Color(0xFFFF5252)
                                    AutoShrinkText(
                                        text = "₹ ${String.format(Locale.US, "%,.2f", entry.amount.paiseToRupees())}",
                                        maxFontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    AutoShrinkText(
                                        text = sign,
                                        maxFontSize = 10.sp,
                                        minFontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = color
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
