package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.ui.components.rememberInBackground
import com.minhas.teller.ui.components.AutoShrinkText
import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.accounting.AccountingEngine
import com.minhas.teller.data.accounting.BalanceSheetResult
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.repositories.AccountGroupRepository
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.ui.viewmodels.AccountGroupViewModel
import com.minhas.teller.ui.viewmodels.AccountGroupViewModelFactory
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.ui.viewmodels.LedgerViewModel
import com.minhas.teller.ui.viewmodels.LedgerViewModelFactory
import com.minhas.teller.ui.viewmodels.StockItemViewModel
import com.minhas.teller.ui.viewmodels.StockItemViewModelFactory
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import com.minhas.teller.utils.ExportUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceSheetReportScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)

    val ledgerRepository = remember { LedgerRepository(database.ledgerDao()) }
    val ledgerViewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(ledgerRepository))
    val ledgers by ledgerViewModel.allLedgers.collectAsState()

    val voucherRepository = remember {
        VoucherRepository(
            database.voucherDao(),
            database.voucherEntryDao(),
            database.inventoryEntryDao(),
            database.billAllocationDao(),
            database.auditLogDao(),
            database.ledgerDao(),
            db = database
        )
    }
    val voucherViewModel: VoucherViewModel = viewModel(
        factory = VoucherViewModelFactory(
            database.voucherDao(),
            database.voucherEntryDao(),
            database.inventoryEntryDao(),
            database.ledgerDao(),
            database.billAllocationDao(),
            database.auditLogDao(),
            db = database
        )
    )
    val allVouchersWithAllEntries by voucherViewModel.allVouchersWithAllEntries.collectAsState()

    val companyRepository = remember { CompanyRepository(database.companyDao()) }
    val companyViewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(companyRepository))
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val company by if (currentCompanyId != null) {
        companyViewModel.getCompany(currentCompanyId!!).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(null) }
    }

    val stockItemRepository = remember { StockItemRepository(database.stockItemDao()) }
    val stockItemViewModel: StockItemViewModel = viewModel(factory = StockItemViewModelFactory(stockItemRepository))
    val stockItems by stockItemViewModel.allStockItems.collectAsState()

    val accountGroupRepository = remember { AccountGroupRepository(database.accountGroupDao()) }
    val accountGroupViewModel: AccountGroupViewModel = viewModel(factory = AccountGroupViewModelFactory(accountGroupRepository))
    val accountGroups by accountGroupViewModel.allGroups.collectAsState()

    var asOfDate by remember { mutableStateOf(Date()) }
    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    val datePickerDialog = DatePickerDialog(
        context,
        { _, y, m, d ->
            val cal = Calendar.getInstance()
            cal.set(y, m, d, 23, 59, 59)
            asOfDate = cal.time
        },
        Calendar.getInstance().get(Calendar.YEAR),
        Calendar.getInstance().get(Calendar.MONTH),
        Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    )

    // In double-entry accounting, a Balance Sheet is cumulative from inception up to asOfDate
    LaunchedEffect(asOfDate, currentCompanyId) {
        voucherViewModel.getAllVouchersWithAllEntriesBetweenDates(Date(0L), asOfDate)
    }

    // Off the UI thread: cumulative from inception, so this walks every voucher the company has.
    val emptyBs = remember { AccountingEngine.calculateBalanceSheet(ledgers = emptyList(), vouchersUpToDate = emptyList()) }
    val bsData: BalanceSheetResult = rememberInBackground(ledgers, allVouchersWithAllEntries, stockItems, accountGroups, asOfDate, initial = emptyBs) {
        AccountingEngine.calculateBalanceSheet(
            ledgers = ledgers,
            vouchersUpToDate = allVouchersWithAllEntries,
            stockItems = stockItems,
            allGroups = accountGroups,
            asOfDate = asOfDate
        )
    }

    // Full-screen gradient background, matching the app's other report screens (e.g. Daybook).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A0533), Color(0xFF2D1B4E), Color(0xFF1A0533))
                )
            )
    ) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = "Balance Sheet",
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController,
                titleColor = Color.White,
                iconColor = Color.White,
                actions = {
                    IconButton(onClick = {
                        val headers = listOf("Type", "Particulars", "Amount")
                        val data = mutableListOf<List<String>>()
                        bsData.capital.forEach { (group, amount) ->
                            data.add(listOf("Capital / Equity", group, "%.2f".format(amount)))
                        }
                        bsData.liabilities.forEach { (group, amount) ->
                            data.add(listOf("Liability", group, "%.2f".format(amount)))
                        }
                        bsData.assets.forEach { (group, amount) ->
                            data.add(listOf("Asset", group, "%.2f".format(amount)))
                        }
                        data.add(listOf("Total Liabilities & Capital", "", "%.2f".format(bsData.totalLiabilitiesAndCapital)))
                        data.add(listOf("Total Assets", "", "%.2f".format(bsData.totalAssets)))

                        ExportUtils.exportReportToPdf(
                            context,
                            "BalanceSheet_${System.currentTimeMillis()}.pdf",
                            company?.name ?: "Teller App",
                            "Balance Sheet (As on ${dateFormatter.format(asOfDate)})",
                            headers,
                            floatArrayOf(2.5f, 3f, 2f),
                            data
                        )
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(onClick = {
                        val headers = listOf("Type", "Particulars", "Amount")
                        val data = mutableListOf<List<String>>()
                        bsData.capital.forEach { (group, amount) ->
                            data.add(listOf("Capital / Equity", group, "%.2f".format(amount)))
                        }
                        bsData.liabilities.forEach { (group, amount) ->
                            data.add(listOf("Liability", group, "%.2f".format(amount)))
                        }
                        bsData.assets.forEach { (group, amount) ->
                            data.add(listOf("Asset", group, "%.2f".format(amount)))
                        }
                        data.add(listOf("Total Liabilities & Capital", "", "%.2f".format(bsData.totalLiabilitiesAndCapital)))
                        data.add(listOf("Total Assets", "", "%.2f".format(bsData.totalAssets)))

                        ExportUtils.exportReportToExcel(
                            context,
                            "BalanceSheet_${System.currentTimeMillis()}.xlsx",
                            "Balance Sheet",
                            headers,
                            data
                        )
                    }) {
                        Icon(Icons.Default.TableChart, contentDescription = "Export Excel", tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            // As-on date chip, glass style to match the gradient background.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x22FFFFFF))
                    .clickable { datePickerDialog.show() }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFB8A0D0)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AutoShrinkText(
                        text = "As on: ${dateFormatter.format(asOfDate)}",
                        maxFontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Balanced Indicator Badge
            if (bsData.isBalanced) {
                Surface(
                    color = Color(0xFF2E7D32).copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF69F0AE),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Balance Sheet is reconciled (Assets = Liabilities + Equity)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (kotlin.math.abs(bsData.differenceInOpeningBalances) > 0.01) {
                Surface(
                    color = Color(0xFFB71C1C).copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFFF8A80),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Opening balances difference: ₹%.2f (check opening trial balance)".format(bsData.differenceInOpeningBalances),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Two-column T-format Balance Sheet, glass cards matching the gradient background.
            Row(modifier = Modifier.fillMaxSize()) {
                // Liabilities & Capital Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x1AFFFFFF))
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF6A3093), Color(0xFF8E54E9))
                                )
                            )
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Liabilities & Equity",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        // Capital and Reserves
                        items(bsData.capital.toList()) { (group, amount) ->
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    AutoShrinkText(
                                        text = group,
                                        maxFontSize = 12.sp,
                                        minFontSize = 9.sp,
                                        modifier = Modifier.weight(1f),
                                        color = Color.White,
                                        fontWeight = if (group.contains("Profit & Loss")) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    AutoShrinkText(
                                        text = "%.2f".format(amount),
                                        maxFontSize = 12.sp,
                                        minFontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (amount < 0) Color(0xFFFF8A80) else Color.White
                                    )
                                }
                                HorizontalDivider(color = Color(0x1FFFFFFF), thickness = 0.5.dp)
                            }
                        }

                        // Liabilities
                        items(bsData.liabilities.toList()) { (group, amount) ->
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    AutoShrinkText(group, maxFontSize = 12.sp, minFontSize = 9.sp, modifier = Modifier.weight(1f), color = Color.White)
                                    AutoShrinkText("%.2f".format(amount), maxFontSize = 12.sp, minFontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                HorizontalDivider(color = Color(0x1FFFFFFF), thickness = 0.5.dp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x336A3093))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(TerminologyManager.getTerm("Total:"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        AutoShrinkText("%.2f".format(bsData.totalLiabilitiesAndCapital), fontWeight = FontWeight.Bold, maxFontSize = 13.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Assets Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x1AFFFFFF))
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF00897B), Color(0xFF26C6DA))
                                )
                            )
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Savings, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Assets",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(bsData.assets.toList()) { (group, amount) ->
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    AutoShrinkText(
                                        text = group,
                                        maxFontSize = 12.sp,
                                        minFontSize = 9.sp,
                                        modifier = Modifier.weight(1f),
                                        color = Color.White,
                                        fontWeight = if (group == "Stock-in-hand") FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    AutoShrinkText(
                                        text = "%.2f".format(amount),
                                        maxFontSize = 12.sp,
                                        minFontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (amount < 0) Color(0xFFFF8A80) else Color.White
                                    )
                                }
                                HorizontalDivider(color = Color(0x1FFFFFFF), thickness = 0.5.dp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x3300897B))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(TerminologyManager.getTerm("Total:"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        AutoShrinkText("%.2f".format(bsData.totalAssets), fontWeight = FontWeight.Bold, maxFontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }
    }
    }
}
