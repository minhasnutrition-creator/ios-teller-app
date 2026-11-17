package com.minhas.teller.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.ui.viewmodels.LedgerViewModel
import com.minhas.teller.ui.viewmodels.LedgerViewModelFactory
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.navigation.Screen
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.SessionManager
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.ui.viewmodels.StockItemViewModel
import com.minhas.teller.ui.viewmodels.StockItemViewModelFactory
import com.minhas.teller.data.entities.Company
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.components.TellerTopAppBar
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import com.minhas.teller.ui.theme.GlobalCharcoal
import com.minhas.teller.ui.theme.GlobalLavender
import com.minhas.teller.ui.theme.GlobalMintGreen
import com.minhas.teller.ui.theme.GlobalNeonCyan
import com.minhas.teller.ui.theme.GlobalNeonGreen
import com.minhas.teller.ui.theme.GlobalNeonMagenta
import com.minhas.teller.ui.theme.GlobalNeonPink
import com.minhas.teller.ui.theme.GlobalPaleBlue
import com.minhas.teller.ui.theme.GlobalPaleYellow
import com.minhas.teller.ui.theme.GlobalPastelOrange
import com.minhas.teller.ui.theme.GlobalSalmonRed
import androidx.compose.material.icons.automirrored.filled.ReceiptLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerViewReportScreen(navController: NavHostController, ledgerId: Int, currentAppTheme: AppTheme) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)

    val ledgerRepository = LedgerRepository(database.ledgerDao())
    val ledgerViewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(ledgerRepository))

    val voucherRepository = VoucherRepository(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.billAllocationDao(), database.auditLogDao(), database.ledgerDao(), db = database)
    val voucherViewModel: VoucherViewModel = viewModel(factory = VoucherViewModelFactory(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.ledgerDao(), database.billAllocationDao(), database.auditLogDao(), db = database))

    val stockItemRepository = StockItemRepository(database.stockItemDao())
    val stockItemViewModel: StockItemViewModel = viewModel(factory = StockItemViewModelFactory(stockItemRepository))
    val allStockItems by stockItemViewModel.allStockItems.collectAsState(initial = emptyList())

    val companyRepository = CompanyRepository(database.companyDao())
    val companyViewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(companyRepository))

    if (ledgerId == -1) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(TerminologyManager.getTerm("Ledger not found. Invalid ID."))
        }
        return
    }

    val ledger by ledgerViewModel.getLedger(ledgerId).collectAsState(initial = null)
    val allLedgers by ledgerViewModel.allLedgers.collectAsState()
    val vouchersForSelectedLedger by voucherViewModel.vouchersForSelectedLedger.collectAsState()

    val calendarCalc = Calendar.getInstance()
    val currentYear = calendarCalc.get(Calendar.YEAR)
    calendarCalc.set(currentYear, Calendar.JANUARY, 1, 0, 0, 0)
    val startOfThisYear = calendarCalc.time
    
    val totalSalesThisYear = remember(vouchersForSelectedLedger, ledgerId) {
        vouchersForSelectedLedger
            .filter { it.voucher.type == "Sales" && it.voucher.date.after(startOfThisYear) }
            .sumOf { v -> v.voucherEntries.find { entry -> entry.ledgerId == ledgerId }?.amount?.paiseToRupees() ?: 0.0 }
    }

    val lastPaymentDate = remember(vouchersForSelectedLedger) {
        vouchersForSelectedLedger
            .filter { it.voucher.type == "Receipt" }
            .maxByOrNull { it.voucher.date }?.voucher?.date
    }

    val last5Items = remember(vouchersForSelectedLedger, allStockItems) {
        vouchersForSelectedLedger
            .filter { it.voucher.type == "Sales" }
            .sortedByDescending { it.voucher.date }
            .flatMap { it.inventoryEntries }
            .take(5)
            .mapNotNull { entry -> allStockItems.find { it.id == entry.stockItemId }?.name }
            .distinct()
    }

    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val currentCompany = produceState<Company?>(initialValue = null, currentCompanyId) {
        currentCompanyId?.let { id ->
            companyViewModel.getCompany(id).collect { value = it }
        }
    }
    val companyName = currentCompany.value?.name ?: TerminologyManager.getTerm("Teller App")

    val defaultFromCal = Calendar.getInstance().apply {
        // Default to historical range (10 years ago e.g. 2016) so imported and past records are immediately visible
        add(Calendar.YEAR, -10)
        set(Calendar.MONTH, Calendar.APRIL)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    var fromDate by remember { mutableStateOf(defaultFromCal.time) }

    val defaultToCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
    var toDate by remember { mutableStateOf(defaultToCal.time) }

    var showDateRangeDialog by remember { mutableStateOf(false) }

    val closingBalance by remember(ledgerId, currentCompanyId, toDate) {
        ledgerViewModel.getClosingBalance(ledgerId, toDate)
    }.collectAsState(initial = 0L)

    var showPendingOnly by remember { mutableStateOf(false) }

    // Off by default: compact cards, no item list, no narration. On: cards show what was
    // traded plus the voucher's note/narration, and grow taller to fit it.
    var detailedView by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var showExportMenu by remember { mutableStateOf(false) }

    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val fullDateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val dateRangeFormatter = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }

    var showDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedVoucher by remember { mutableStateOf<VoucherWithAllEntries?>(null) }

    if (showDeleteConfirmDialog && selectedVoucher != null) {
        val voucher = selectedVoucher!!.voucher
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                selectedVoucher = null
            },
            title = {
                Text(
                    TerminologyManager.getTerm("Permanently Delete Transaction?"),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    TerminologyManager.getTerm("Are you sure you want to permanently delete ${voucher.type} Voucher #${voucher.voucherNumber}? All associated ledger balances and stock movements will be reversed. This action cannot be undone."),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = voucher
                        showDeleteConfirmDialog = false
                        selectedVoucher = null
                        voucherViewModel.deleteVoucherWithAllEntries(toDelete)
                        android.widget.Toast.makeText(context, TerminologyManager.getTerm("Transaction permanently deleted"), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(TerminologyManager.getTerm("Permanently Delete"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        selectedVoucher = null
                    }
                ) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        )
    }

    if (showDialog && selectedVoucher != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(TerminologyManager.getTerm("Voucher Options"), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(TerminologyManager.getTerm("What would you like to do with this voucher?"), color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showDialog = false
                            showDeleteConfirmDialog = true
                        }
                    ) {
                        Text(TerminologyManager.getTerm("Delete"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            showDialog = false
                            navController.navigate(Screen.AddEditVoucher.route + "?voucherId=${selectedVoucher!!.voucher.id}")
                        }
                    ) {
                        Text(TerminologyManager.getTerm("Edit"))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        navController.navigate(Screen.VoucherView.route.replace("{voucherId}", selectedVoucher!!.voucher.id.toString()))
                    }
                ) {
                    Text(TerminologyManager.getTerm("View"))
                }
            }
        )
    }

    fun showFromDatePicker() {
        val cal = Calendar.getInstance().apply { time = fromDate }
        val dialog = DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, day: Int ->
                val resultCal = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                fromDate = resultCal.time
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.setTitle(TerminologyManager.getTerm("Select Start Date (From)"))
        dialog.show()
    }

    fun showToDatePicker() {
        val cal = Calendar.getInstance().apply { time = toDate }
        val dialog = DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, day: Int ->
                val resultCal = Calendar.getInstance().apply {
                    set(year, month, day, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                toDate = resultCal.time
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.setTitle(TerminologyManager.getTerm("Select End Date (To)"))
        dialog.show()
    }

    if (showDateRangeDialog) {
        AlertDialog(
            onDismissRequest = { showDateRangeDialog = false },
            title = {
                Text(
                    text = TerminologyManager.getTerm("Statement Period"),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = TerminologyManager.getTerm("Select the date range for this account statement:"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // From Date Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFromDatePicker() },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = TerminologyManager.getTerm("From Date (Start):"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AutoShrinkText(
                                    text = fullDateFormatter.format(fromDate),
                                    maxFontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = TerminologyManager.getTerm("Change"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // To Date Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showToDatePicker() },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = TerminologyManager.getTerm("To Date (End):"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AutoShrinkText(
                                    text = fullDateFormatter.format(toDate),
                                    maxFontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = TerminologyManager.getTerm("Change"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = TerminologyManager.getTerm("Quick Presets:"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Presets Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                val c = Calendar.getInstance().apply {
                                    add(Calendar.YEAR, -10)
                                    set(Calendar.MONTH, Calendar.APRIL)
                                    set(Calendar.DAY_OF_MONTH, 1)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                fromDate = c.time
                                val toC = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, 23)
                                    set(Calendar.MINUTE, 59)
                                    set(Calendar.SECOND, 59)
                                    set(Calendar.MILLISECOND, 999)
                                }
                                toDate = toC.time
                            },
                            label = { Text(TerminologyManager.getTerm("All Time"), fontSize = 10.sp) }
                        )

                        AssistChip(
                            onClick = {
                                val c = Calendar.getInstance()
                                val curMonth = c.get(Calendar.MONTH)
                                val yr = if (curMonth < Calendar.APRIL) c.get(Calendar.YEAR) - 1 else c.get(Calendar.YEAR)
                                c.set(yr, Calendar.APRIL, 1, 0, 0, 0)
                                c.set(Calendar.MILLISECOND, 0)
                                fromDate = c.time
                                val toC = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, 23)
                                    set(Calendar.MINUTE, 59)
                                    set(Calendar.SECOND, 59)
                                    set(Calendar.MILLISECOND, 999)
                                }
                                toDate = toC.time
                            },
                            label = { Text(TerminologyManager.getTerm("This FY"), fontSize = 10.sp) }
                        )

                        AssistChip(
                            onClick = {
                                val c = Calendar.getInstance()
                                val curMonth = c.get(Calendar.MONTH)
                                val yr = if (curMonth < Calendar.APRIL) c.get(Calendar.YEAR) - 2 else c.get(Calendar.YEAR) - 1
                                c.set(yr, Calendar.APRIL, 1, 0, 0, 0)
                                c.set(Calendar.MILLISECOND, 0)
                                fromDate = c.time
                                val toC = Calendar.getInstance().apply {
                                    set(yr + 1, Calendar.MARCH, 31, 23, 59, 59)
                                    set(Calendar.MILLISECOND, 999)
                                }
                                toDate = toC.time
                            },
                            label = { Text(TerminologyManager.getTerm("Last FY"), fontSize = 10.sp) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDateRangeDialog = false }) {
                    Text(TerminologyManager.getTerm("Apply"))
                }
            }
        )
    }

    LaunchedEffect(ledgerId, currentCompanyId, fromDate, toDate) {
        voucherViewModel.getVouchersForLedgerBetweenDates(ledgerId, fromDate, toDate)
    }

    var entryTypeFilter by remember { mutableStateOf("All") } // "All", "Receivable", "Payable"

    val filteredVouchers = remember(vouchersForSelectedLedger, showPendingOnly, searchQuery, allLedgers, entryTypeFilter, ledgerId) {
        vouchersForSelectedLedger.filter { voucherWithEntries ->
            val v = voucherWithEntries.voucher
            val currentEntry = voucherWithEntries.voucherEntries.find { it.ledgerId == ledgerId }
            val isDebit = currentEntry?.isDebit ?: true

            val isTypeMatch = when (entryTypeFilter) {
                "Receivable" -> isDebit
                "Payable" -> !isDebit
                else -> true
            }

            val isPendingMatch = if (showPendingOnly) {
                v.type == "Sales" || v.type == "Purchase"
            } else true

            val isSearchMatch = if (searchQuery.isNotBlank()) {
                val query = searchQuery.trim()
                
                val isVoucherNoMatch = v.voucherNumber.contains(query, ignoreCase = true)
                val otherEntry = voucherWithEntries.voucherEntries.find { it.ledgerId != ledgerId }
                val otherLedger = allLedgers.find { it.id == otherEntry?.ledgerId }
                val isCustomerMatch = otherLedger?.name?.contains(query, ignoreCase = true) == true
                
                val isAmountMatch = currentEntry?.amount?.toString()?.contains(query) == true

                isVoucherNoMatch || isCustomerMatch || isAmountMatch
            } else true

            isTypeMatch && isPendingMatch && isSearchMatch
        }
    }

    // Gross totals for the two "pill" stats in the new header (not netted against
    // each other like closingBalance - these are the raw sums of every debit-side
    // and credit-side entry against this ledger within the filtered range).
    val totalReceivables = remember(vouchersForSelectedLedger, ledgerId) {
        vouchersForSelectedLedger.sumOf { voucherWithEntries ->
            voucherWithEntries.voucherEntries
                .filter { it.ledgerId == ledgerId && it.isDebit }
                .sumOf { it.amount }.paiseToRupees()
        }
    }
    val totalPayables = remember(vouchersForSelectedLedger, ledgerId) {
        vouchersForSelectedLedger.sumOf { voucherWithEntries ->
            voucherWithEntries.voucherEntries
                .filter { it.ledgerId == ledgerId && !it.isDebit }
                .sumOf { it.amount }.paiseToRupees()
        }
    }

    // Fixed light green -> pale yellow palette for this screen's new unified design.
    // Colors are hard-coded (not MaterialTheme.colorScheme.*) so text stays legible
    // against this specific background no matter which of the 5 app themes is active.
    val ledgerBgGradient = Brush.verticalGradient(listOf(Color(0xFFDCEDC8), Color(0xFFFFF9C4)))
    val ledgerTextDark = Color(0xFF1B2A1B)
    val ledgerTextMuted = Color(0xFF5C6B5C)
    val ledgerCardBg = Color(0xFFFAFAF2)
    val ledgerGreen = Color(0xFF2E7D32)
    val ledgerRed = Color(0xFFC62828)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ledgerBgGradient)
    ) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = "${(ledger?.name ?: TerminologyManager.getTerm("Ledger")).uppercase()} ${TerminologyManager.getTerm("STATEMENT")}",
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController,
                titleColor = ledgerTextDark,
                iconColor = ledgerTextDark
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                ledger?.let { targetLedger ->
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Top Summary: Total Receivables & Total Payables
                        item(key = "receivables_payables_header") {
                            LedgerReceivablesPayablesHeader(
                                totalReceivables = totalReceivables,
                                totalPayables = totalPayables,
                                selectedFilter = entryTypeFilter,
                                onFilterSelected = { entryTypeFilter = it },
                                cardBg = ledgerCardBg,
                                receivableColor = ledgerGreen,
                                payableColor = ledgerRed
                            )
                        }

                        // 2. Action Controls: Search Bar, Date Range, Pending Toggle, Export Menu
                        item(key = "search_date_row") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 1. Search Bar (compact, weight 1.4f)
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text(TerminologyManager.getTerm("Search..."), fontSize = 11.sp, color = ledgerTextMuted) },
                                    modifier = Modifier.weight(1.4f).height(48.dp).background(ledgerCardBg, RoundedCornerShape(10.dp)),
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = TerminologyManager.getTerm("Search"), modifier = Modifier.size(16.dp), tint = ledgerTextMuted) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(color = ledgerTextDark, fontSize = 12.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = ledgerTextDark,
                                        unfocusedTextColor = ledgerTextDark,
                                        focusedContainerColor = ledgerCardBg,
                                        unfocusedContainerColor = ledgerCardBg,
                                        focusedBorderColor = ledgerGreen,
                                        unfocusedBorderColor = ledgerTextMuted.copy(alpha = 0.4f),
                                        focusedLabelColor = ledgerGreen,
                                        unfocusedLabelColor = ledgerTextMuted
                                    )
                                )

                                // 2. Date Range selection button (weight 1.3f)
                                OutlinedButton(
                                    onClick = { showDateRangeDialog = true },
                                    modifier = Modifier.weight(1.3f).height(48.dp).background(ledgerCardBg, RoundedCornerShape(10.dp)),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ledgerTextDark),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ledgerTextMuted.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.DateRange, contentDescription = TerminologyManager.getTerm("Date Range"), modifier = Modifier.size(14.dp), tint = ledgerTextDark)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${dateRangeFormatter.format(fromDate)} - ${dateRangeFormatter.format(toDate)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            color = ledgerTextDark
                                        )
                                    }
                                }

                                // 3. Pending Payments Only toggle
                                OutlinedIconToggleButton(
                                    checked = showPendingOnly,
                                    onCheckedChange = { showPendingOnly = it },
                                    modifier = Modifier.size(48.dp).background(ledgerCardBg, RoundedCornerShape(10.dp)),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = IconButtonDefaults.outlinedIconToggleButtonColors(
                                        containerColor = if (showPendingOnly) ledgerGreen.copy(alpha = 0.18f) else Color.Transparent,
                                        contentColor = ledgerTextDark
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (showPendingOnly) Icons.Default.HourglassFull else Icons.Default.HourglassEmpty,
                                        contentDescription = TerminologyManager.getTerm("Pending Payments Only"),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 4. Export Menu
                                Box {
                                    IconButton(
                                        onClick = { showExportMenu = true },
                                        modifier = Modifier.size(48.dp).background(ledgerCardBg, RoundedCornerShape(10.dp))
                                    ) {
                                        Icon(Icons.Default.MoreVert, contentDescription = TerminologyManager.getTerm("Export"), tint = ledgerTextDark)
                                    }
                                    DropdownMenu(
                                        expanded = showExportMenu,
                                        onDismissRequest = { showExportMenu = false },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(TerminologyManager.getTerm("Export to PDF"), color = MaterialTheme.colorScheme.onSurface) },
                                            onClick = {
                                                showExportMenu = false
                                                ledger?.let { targetLedger ->
                                                    val headers = listOf("Date", "Particulars", "Vch Type", "Vch No", "Amount")
                                                    val data = filteredVouchers.map { voucherWithEntries ->
                                                        val v = voucherWithEntries.voucher
                                                        val otherEntry = voucherWithEntries.voucherEntries.find { it.ledgerId != targetLedger.id }
                                                        val otherLedger = allLedgers.find { it.id == otherEntry?.ledgerId }
                                                        val currentEntry = voucherWithEntries.voucherEntries.find { it.ledgerId == targetLedger.id }
                                                        val amountText = currentEntry?.let { entry ->
                                                            if (entry.isDebit) "%.2f Dr".format(entry.amount.paiseToRupees()) else "%.2f Cr".format(entry.amount.paiseToRupees())
                                                        } ?: ""

                                                        listOf(
                                                            dateFormatter.format(v.date),
                                                            otherLedger?.name ?: TerminologyManager.getTerm("Unknown"),
                                                            v.type,
                                                            v.voucherNumber,
                                                            amountText
                                                        )
                                                    }.toMutableList()

                                                    var totalDebit = 0.0
                                                    var totalCredit = 0.0
                                                    filteredVouchers.forEach { voucherWithEntries ->
                                                        val currentEntry = voucherWithEntries.voucherEntries.find { it.ledgerId == targetLedger.id }
                                                        currentEntry?.let {
                                                            if (it.isDebit) totalDebit += it.amount.paiseToRupees() else totalCredit += it.amount.paiseToRupees()
                                                        }
                                                    }
                                                    val netBalance = totalDebit - totalCredit
                                                    val balanceText = if (netBalance >= 0) "%.2f Dr".format(netBalance) else "%.2f Cr".format(kotlin.math.abs(netBalance))
                                                    data.add(listOf("", TerminologyManager.getTerm("Closing Balance"), "", "", balanceText))

                                                    ExportUtils.exportReportToPdf(context, "Ledger_${targetLedger.name}.pdf", companyName, "${TerminologyManager.getTerm("Ledger Report")}: ${targetLedger.name}", headers, floatArrayOf(1.5f, 3f, 1.5f, 1.5f, 2f), data)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(TerminologyManager.getTerm("Export to Excel"), color = MaterialTheme.colorScheme.onSurface) },
                                            onClick = {
                                                showExportMenu = false
                                                ledger?.let { targetLedger ->
                                                    val headers = listOf("Date", "Particulars", "Vch Type", "Vch No", "Amount")
                                                    val data = filteredVouchers.map { voucherWithEntries ->
                                                        val v = voucherWithEntries.voucher
                                                        val otherEntry = voucherWithEntries.voucherEntries.find { it.ledgerId != targetLedger.id }
                                                        val otherLedger = allLedgers.find { it.id == otherEntry?.ledgerId }
                                                        val currentEntry = voucherWithEntries.voucherEntries.find { it.ledgerId == targetLedger.id }
                                                        val amountText = currentEntry?.let { entry ->
                                                            if (entry.isDebit) "%.2f Dr".format(entry.amount.paiseToRupees()) else "%.2f Cr".format(entry.amount.paiseToRupees())
                                                        } ?: ""

                                                        listOf(
                                                            dateFormatter.format(v.date),
                                                            otherLedger?.name ?: TerminologyManager.getTerm("Unknown"),
                                                            v.type,
                                                            v.voucherNumber,
                                                            amountText
                                                        )
                                                    }.toMutableList()

                                                    var totalDebit = 0.0
                                                    var totalCredit = 0.0
                                                    filteredVouchers.forEach { voucherWithEntries ->
                                                        val currentEntry = voucherWithEntries.voucherEntries.find { it.ledgerId == targetLedger.id }
                                                        currentEntry?.let {
                                                            if (it.isDebit) totalDebit += it.amount else totalCredit += it.amount
                                                        }
                                                    }
                                                    val netBalance = totalDebit - totalCredit
                                                    val balanceText = if (netBalance >= 0) "%.2f Dr".format(netBalance) else "%.2f Cr".format(kotlin.math.abs(netBalance))
                                                    data.add(listOf("", TerminologyManager.getTerm("Closing Balance"), "", "", balanceText))

                                                    ExportUtils.exportReportToExcel(context, "Ledger_${targetLedger.name}.xlsx", TerminologyManager.getTerm("Ledger Report"), headers, data)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. View Switch: Detailed View vs Compact View
                        item(key = "detailed_view_switch") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ledgerCardBg, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    TerminologyManager.getTerm("Detailed View"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ledgerTextDark
                                )
                                Switch(
                                    checked = detailedView,
                                    onCheckedChange = { detailedView = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = ledgerGreen,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = ledgerTextMuted.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }

                        // 4. Column Headers
                        item(key = "list_header") {
                            LedgerStatementListHeader(ledgerTextMuted, detailedView)
                        }

                        // 5. Transaction Vouchers
                        if (filteredVouchers.isEmpty()) {
                            item(key = "empty_transactions") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = TerminologyManager.getTerm("No transactions found for selected period"),
                                        fontSize = 13.sp,
                                        color = ledgerTextMuted
                                    )
                                }
                            }
                        } else {
                            items(filteredVouchers, key = { it.voucher.id }) { voucherWithEntries ->
                                LedgerStatementEntryCard(
                                    voucherWithEntries = voucherWithEntries,
                                    targetLedger = targetLedger,
                                    allLedgers = allLedgers,
                                    allStockItems = allStockItems,
                                    dateFormatter = dateFormatter,
                                    cardBg = ledgerCardBg,
                                    textDark = ledgerTextDark,
                                    textMuted = ledgerTextMuted,
                                    receivableColor = ledgerGreen,
                                    payableColor = ledgerRed,
                                    detailedView = detailedView,
                                    onVoucherClick = { vwe ->
                                        navController.navigate(Screen.VoucherView.route.replace("{voucherId}", vwe.voucher.id.toString()))
                                    },
                                    onVoucherLongClick = { vwe ->
                                        selectedVoucher = vwe
                                        showDialog = true
                                    }
                                )
                            }
                        }
                    }

                    // Bottom Floating Summary Bar
                    val balance = (closingBalance ?: 0L).paiseToRupees()
                    val isReceivable = balance >= 0
                    val label = if (isReceivable) TerminologyManager.getTerm("YOU RECEIVE") else TerminologyManager.getTerm("YOU PAY")
                    val pillColor = if (isReceivable) ledgerGreen else ledgerRed

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, top = 4.dp),
                        shape = RoundedCornerShape(50),
                        colors = CardDefaults.cardColors(containerColor = ledgerCardBg)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isReceivable) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = pillColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            LedgerAutoSizeText(
                                text = "$label = ₹${String.format(Locale.getDefault(), "%,.2f", kotlin.math.abs(balance))}",
                                color = pillColor,
                                fontWeight = FontWeight.Bold,
                                maxFontSize = 18.sp,
                                minFontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(max = 260.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
fun LedgerStatementListHeader(labelColor: Color, detailedView: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading spacer matches the icon+date column width in the cards below
        // (65.dp icon column + 10.dp spacer) so labels line up with their content.
        Spacer(modifier = Modifier.width(75.dp))
        Text(
            TerminologyManager.getTerm("TRANSACTION"),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(1.1f)
        )
        if (detailedView) {
            Text(
                TerminologyManager.getTerm("ITEM"),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                letterSpacing = 0.5.sp,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            TerminologyManager.getTerm("AMOUNT"),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun LedgerReceivablesPayablesHeader(
    totalReceivables: Double,
    totalPayables: Double,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    cardBg: Color,
    receivableColor: Color,
    payableColor: Color
) {
    val isRecActive = selectedFilter == "Receivable"
    val isPayActive = selectedFilter == "Payable"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Receivables Card (Debits / Sales)
        Card(
            modifier = Modifier
                .weight(1f)
                .ledgerFloatingDropShadow(borderRadius = 34.dp)
                .clickable {
                    onFilterSelected(if (isRecActive) "All" else "Receivable")
                },
            shape = RoundedCornerShape(50),
            colors = CardDefaults.cardColors(
                containerColor = if (isRecActive) receivableColor.copy(alpha = 0.15f) else cardBg
            ),
            border = if (isRecActive) androidx.compose.foundation.BorderStroke(2.dp, receivableColor) else null,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isRecActive) {
                        Text("✓ ", color = receivableColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        TerminologyManager.getTerm("TOTAL RECEIVABLES"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = receivableColor,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                LedgerAutoSizeText(
                    text = "₹${String.format(Locale.getDefault(), "%,.2f", totalReceivables)}",
                    color = receivableColor,
                    fontWeight = FontWeight.Bold,
                    maxFontSize = 16.sp,
                    minFontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isRecActive) {
                    Text(
                        text = TerminologyManager.getTerm("Showing Debits (Tap to reset)"),
                        fontSize = 8.5.sp,
                        color = receivableColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Payables Card (Credits / Receipts / Purchases)
        Card(
            modifier = Modifier
                .weight(1f)
                .ledgerFloatingDropShadow(borderRadius = 34.dp)
                .clickable {
                    onFilterSelected(if (isPayActive) "All" else "Payable")
                },
            shape = RoundedCornerShape(50),
            colors = CardDefaults.cardColors(
                containerColor = if (isPayActive) payableColor.copy(alpha = 0.15f) else cardBg
            ),
            border = if (isPayActive) androidx.compose.foundation.BorderStroke(2.dp, payableColor) else null,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isPayActive) {
                        Text("✓ ", color = payableColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        TerminologyManager.getTerm("TOTAL PAYABLES"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = payableColor,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                LedgerAutoSizeText(
                    text = "₹${String.format(Locale.getDefault(), "%,.2f", totalPayables)}",
                    color = payableColor,
                    fontWeight = FontWeight.Bold,
                    maxFontSize = 16.sp,
                    minFontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isPayActive) {
                    Text(
                        text = TerminologyManager.getTerm("Showing Credits (Tap to reset)"),
                        fontSize = 8.5.sp,
                        color = payableColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// Same layered floating-shadow technique used for the Date/Voucher No pills on the
// voucher creation screen (see AddEditVoucherScreen.kt / PurchaseVoucherCreationScreen.kt),
// reproduced here since that helper is file-private there.
private fun Modifier.ledgerFloatingDropShadow(borderRadius: androidx.compose.ui.unit.Dp = 23.dp): Modifier = this.drawBehind {
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

// Shrinks fontSize a step at a time (measuring real layout overflow, not a length
// heuristic) until the text fits its box, down to minFontSize. Used everywhere on this
// screen where the box is fixed-size but the content (a name, item list, or amount) can
// be arbitrarily long/large - ledger names, item lists, voucher type, and every rupee
// amount all use this instead of relying on truncation.
@Composable
private fun LedgerAutoSizeText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    maxFontSize: TextUnit,
    minFontSize: TextUnit = 8.sp,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified
) {
    var fontSize by remember(text, maxFontSize) { mutableStateOf(maxFontSize) }

    Text(
        text = text,
        color = color,
        fontWeight = fontWeight,
        fontSize = fontSize,
        maxLines = maxLines,
        textAlign = textAlign,
        letterSpacing = letterSpacing,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize.value > minFontSize.value) {
                fontSize = TextUnit((fontSize.value - 1f).coerceAtLeast(minFontSize.value), TextUnitType.Sp)
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerStatementEntryCard(
    voucherWithEntries: VoucherWithAllEntries,
    targetLedger: Ledger,
    allLedgers: List<Ledger>,
    allStockItems: List<StockItem>,
    dateFormatter: SimpleDateFormat,
    cardBg: Color,
    textDark: Color,
    textMuted: Color,
    receivableColor: Color,
    payableColor: Color,
    detailedView: Boolean,
    onVoucherClick: (VoucherWithAllEntries) -> Unit,
    onVoucherLongClick: ((VoucherWithAllEntries) -> Unit)? = null
) {
    val v = voucherWithEntries.voucher
    val otherEntry = voucherWithEntries.voucherEntries.find { it.ledgerId != targetLedger.id }
    val otherLedger = allLedgers.find { it.id == otherEntry?.ledgerId }
    val currentEntry = voucherWithEntries.voucherEntries.find { it.ledgerId == targetLedger.id }
    val isDebit = currentEntry?.isDebit ?: true
    val accentColor = if (isDebit) receivableColor else payableColor

    // Items traded on this voucher (Sales/Purchase) - blank for pure money vouchers
    // (Receipt/Payment/Journal) which carry no inventory entries.
    val itemNames = remember(voucherWithEntries.inventoryEntries, allStockItems) {
        voucherWithEntries.inventoryEntries
            .mapNotNull { entry -> allStockItems.find { it.id == entry.stockItemId }?.name }
            .distinct()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onVoucherClick(voucherWithEntries) },
                onLongClick = onVoucherLongClick?.let { cb -> { cb(voucherWithEntries) } }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon badge + date
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(65.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Icon(
                        imageVector = if (isDebit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier
                            .size(13.dp)
                            .align(Alignment.TopEnd)
                            .background(cardBg, RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                AutoShrinkText(
                    text = dateFormatter.format(v.date),
                    maxFontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(if (detailedView) 1.1f else 2.1f)) {
                LedgerAutoSizeText(
                    text = v.type,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    maxFontSize = 13.sp,
                    minFontSize = 9.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                LedgerAutoSizeText(
                    text = otherLedger?.name ?: TerminologyManager.getTerm("Unknown"),
                    color = textDark,
                    fontWeight = FontWeight.Bold,
                    maxFontSize = 15.sp,
                    minFontSize = 10.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                AutoShrinkText(
                    text = v.voucherNumber,
                    maxFontSize = 10.sp,
                    minFontSize = 7.sp,
                    color = textMuted
                )
            }

            // Items traded (what was sold/purchased/traded on this voucher) - only in Detailed View
            if (detailedView) {
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (itemNames.isNotEmpty()) {
                        LedgerAutoSizeText(
                            text = itemNames.joinToString(", "),
                            color = textDark,
                            fontWeight = FontWeight.Medium,
                            maxFontSize = 12.sp,
                            minFontSize = 8.sp,
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "—",
                            fontSize = 12.sp,
                            color = textMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 95.dp)) {
                Icon(
                    imageVector = if (isDebit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                currentEntry?.let { entry ->
                    LedgerAutoSizeText(
                        text = "₹${String.format("%,.2f", entry.amount.paiseToRupees())}",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        maxFontSize = 14.sp,
                        minFontSize = 9.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = if (entry.isDebit) "Dr" else "Cr",
                        fontSize = 10.sp,
                        color = textMuted
                    )
                }
            }
        }

        // Note/narration - only in Detailed View, and only when the voucher actually has one.
        // This is what makes the card grow taller when the toggle is switched on.
        if (detailedView && !v.narration.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = v.narration,
                fontSize = 11.sp,
                color = textMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 75.dp)
            )
        }
        }
    }
}
