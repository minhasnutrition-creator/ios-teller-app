package com.minhas.teller.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.repositories.AccountGroupRepository
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.ui.viewmodels.*
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.utils.TerminologyManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class OutstandingItem(
    val voucherId: Int,
    val voucherNumber: String,
    val date: Date,
    val ledgerName: String,
    val ledgerGroup: String,
    val originalAmount: Double,
    val pendingAmount: Double,
    val isDebit: Boolean,
    val voucherType: String,
    val ledgerId: Int,
    val paymentHistory: List<com.minhas.teller.data.repositories.PaymentHistoryItem> = emptyList(),
    val billRefName: String? = null,
    val refDisplayName: String? = null,
    val customerName: String? = null,
    val phoneNumber: String? = null
)

data class AgingBucket(
    val label: String,
    val total: Double,
    val count: Int,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutstandingPaymentsSummaryScreen(
    navController: NavHostController,
    initialGroup: String? = null,
    currentAppTheme: AppTheme = AppTheme.DEFAULT
) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)

    val ledgerRepository = remember { LedgerRepository(database.ledgerDao()) }
    val ledgerViewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(ledgerRepository))
    val allLedgers by ledgerViewModel.allLedgers.collectAsState()

    val voucherRepository = remember { VoucherRepository(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.billAllocationDao(), database.auditLogDao(), database.ledgerDao(), db = database) }
    val voucherViewModel: VoucherViewModel = viewModel(factory = VoucherViewModelFactory(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.ledgerDao(), database.billAllocationDao(), database.auditLogDao(), db = database))
    
    val reportOutstandingVouchers by voucherViewModel.reportOutstandingVouchers.collectAsState()

    val accountGroupRepository = remember { AccountGroupRepository(database.accountGroupDao()) }
    val accountGroupViewModel: AccountGroupViewModel = viewModel(factory = AccountGroupViewModelFactory(accountGroupRepository))
    val allGroups by accountGroupViewModel.allGroups.collectAsState()

    val companyRepository = remember { CompanyRepository(database.companyDao()) }
    val companyViewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(companyRepository))

    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val company by if (currentCompanyId != null) {
        companyViewModel.getCompany(currentCompanyId!!).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(null) }
    }

    var fromDate by remember { mutableStateOf<Date?>(null) }
    var toDate by remember { mutableStateOf<Date?>(null) }

    LaunchedEffect(currentCompanyId) {
        if (currentCompanyId != null) {
            voucherViewModel.loadReportOutstandingVouchers()
        }
    }

    LaunchedEffect(company) {
        if (fromDate == null) {
            val today = Date()
            val cal = Calendar.getInstance()
            
            if (company != null && company!!.financialYearFrom > 0L) {
                cal.time = Date(company!!.financialYearFrom)
            } else {
                val currentYear = cal.get(Calendar.YEAR)
                val currentMonth = cal.get(Calendar.MONTH)
                val year = if (currentMonth < Calendar.APRIL) currentYear - 1 else currentYear
                cal.set(year, Calendar.APRIL, 1)
            }
            // Reset time to start of day
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            
            fromDate = cal.time
            toDate = today
        }
    }

    // Default to non-null for UI usage
    val displayFromDate = fromDate ?: Date()
    val displayToDate = toDate ?: Date()

    // RESTORED VARIABLES
    var searchQuery by remember { mutableStateOf("") }

    // Sort order for the list by voucher date. Default: newest first.
    var sortDescending by remember { mutableStateOf(true) }
    
    // Filter State: "Both", "Receivable", "Payable"
    var filterType by remember {
        mutableStateOf(
            when (initialGroup) {
                "Sundry Debtors" -> "Receivable"
                "Sundry Creditors" -> "Payable"
                else -> "Both"
            }
        )
    }

    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val fullDateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    var showDateRangeDialog by remember { mutableStateOf(false) }

    fun showFromDatePicker() {
        val cal = Calendar.getInstance().apply { time = displayFromDate }
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
        val cal = Calendar.getInstance().apply { time = displayToDate }
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
                    text = TerminologyManager.getTerm("Period Filter"),
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
                        text = TerminologyManager.getTerm("Select the date range for outstanding balances:"),
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
                                    text = fullDateFormatter.format(displayFromDate),
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
                                    text = fullDateFormatter.format(displayToDate),
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

    // Group Filter (From Navigation Argument)
    val selectedGroup = initialGroup ?: "All"

    // O(1) ledger lookup: a linear find() per outstanding bill made this filter O(bills x ledgers)
    // and it reruns on every keystroke in the search box.
    val ledgerById = remember(allLedgers) { allLedgers.associateBy { it.id } }
    val filteredItems = remember(reportOutstandingVouchers, ledgerById, selectedGroup, displayFromDate, displayToDate, searchQuery, filterType) {
        reportOutstandingVouchers.mapNotNull { ov ->
            // FIX: Only filter by End Date ("As On" report).
            if (ov.date.after(displayToDate)) return@mapNotNull null

            val ledger = ledgerById[ov.ledgerId] ?: return@mapNotNull null
            
            // Filtering Logic based on Menu Selection
            if (selectedGroup != "All") {
                val isMatch = when (selectedGroup) {
                    "Finance Companies" -> ledger.group.contains("Loan", true) || ledger.group.contains("Finance", true)
                    "Sundry Debtors" -> true
                    "Sundry Creditors" -> true
                    else -> ledger.group.equals(selectedGroup, ignoreCase = true)
                }
                if (!isMatch) return@mapNotNull null
            }
            
            if (searchQuery.isNotEmpty() && !ledger.name.contains(searchQuery, ignoreCase = true)) return@mapNotNull null

            // Use isDebit directly from the repository calculation (based on Bill Balance)
            val isDebit = ov.isDebit
            
            // Filter by Type (Receivable/Payable/Both Switch)
            if (filterType == "Receivable" && !isDebit) return@mapNotNull null
            if (filterType == "Payable" && isDebit) return@mapNotNull null

            OutstandingItem(
                voucherId = ov.voucherId,
                voucherNumber = ov.voucherNumber,
                date = ov.date,
                ledgerName = ledger.name,
                ledgerGroup = ledger.group,
                originalAmount = ov.originalAmount,
                pendingAmount = ov.pendingAmount,
                isDebit = isDebit,
                voucherType = ov.type,
                ledgerId = ledger.id,
                paymentHistory = ov.paymentHistory,
                billRefName = ov.billRefName,
                refDisplayName = ov.refDisplayName,
                customerName = ov.customerName,
                phoneNumber = ledger.phoneNumber
            )
        }
    }

    // Sort by voucher date (newest or oldest first per the toggle).
    val sortedItems = remember(filteredItems, sortDescending) {
        if (sortDescending) filteredItems.sortedByDescending { it.date }
        else filteredItems.sortedBy { it.date }
    }

    val totalReceivable = filteredItems.filter { it.isDebit }.sumOf { it.pendingAmount }
    val totalPayable = filteredItems.filter { !it.isDebit }.sumOf { it.pendingAmount }

    // Aging buckets: how much is outstanding in each age band (0-30 / 31-60 / 61-90 / 90+ days)
    val agingBuckets = remember(filteredItems) {
        val dayMillis = 1000L * 60 * 60 * 24
        val totals = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        val counts = intArrayOf(0, 0, 0, 0)
        filteredItems.forEach { item ->
            val days = ((System.currentTimeMillis() - item.date.time) / dayMillis).coerceAtLeast(0L)
            val idx = when {
                days <= 30 -> 0
                days <= 60 -> 1
                days <= 90 -> 2
                else -> 3
            }
            totals[idx] += item.pendingAmount
            counts[idx]++
        }
        listOf(
            AgingBucket("0-30 days", totals[0], counts[0], Color(0xFF4CAF50)),
            AgingBucket("31-60 days", totals[1], counts[1], Color(0xFFFFC107)),
            AgingBucket("61-90 days", totals[2], counts[2], Color(0xFFFF9800)),
            AgingBucket("90+ days", totals[3], counts[3], Color(0xFFF44336))
        )
    }

    // 1. Fix Background: Dark Radial Gradient
    val radialBackground = Brush.radialGradient(
        colors = listOf(Color(0xFF121212), Color(0xFF080808)),
        center = androidx.compose.ui.geometry.Offset.Unspecified,
        radius = Float.POSITIVE_INFINITY
    )

    val backgroundModifier = Modifier.fillMaxSize().background(radialBackground)

    Box(
        modifier = backgroundModifier
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                com.minhas.teller.ui.components.AutoShrinkText(
                    TerminologyManager.getTerm("Outstanding Payments"),
                    color = Color.White
                )
            },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, TerminologyManager.getTerm("Back"), tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val headers = listOf("Date", "Ledger Name", "Vch No", "Group", "Pending", "Days")
                            val data = sortedItems.map { item ->
                                val diffInMillis = displayToDate.time - item.date.time
                                val pendingDays = (diffInMillis / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                                val shortGroup = when (item.ledgerGroup) {
                                    "Sundry Debtors" -> "Cust"
                                    "Sundry Creditors" -> "Supp"
                                    "Loans (Liability)" -> "Loan"
                                    else -> item.ledgerGroup.take(4)
                                }
                                listOf(dateFormatter.format(item.date), item.ledgerName, item.voucherNumber, shortGroup, if (item.isDebit) "%.2f Dr".format(item.pendingAmount) else "%.2f Cr".format(item.pendingAmount), pendingDays.toString())
                            }
                            ExportUtils.exportReportToPdf(context, "Outstandings_${System.currentTimeMillis()}.pdf", company?.name ?: "Teller", TerminologyManager.getTerm("Outstanding Payments (Receivables/Payables)"), headers, floatArrayOf(1.5f, 3f, 2f, 1f, 2f, 1f), data)
                        }) {
                            Icon(Icons.Default.PictureAsPdf, TerminologyManager.getTerm("Export PDF"), tint = Color.White)
                        }
                        IconButton(onClick = {
                            val headers = listOf("Date", "Party Name", "Vch No", "Type", "Pending", "Days")
                            val data = sortedItems.map { item ->
                                val diffInMillis = displayToDate.time - item.date.time
                                val pendingDays = (diffInMillis / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                                val shortGroup = when (item.ledgerGroup) {
                                    "Sundry Debtors" -> "Customer"
                                    "Sundry Creditors" -> "Supplier"
                                    "Loans (Liability)" -> "Loan"
                                    else -> item.ledgerGroup
                                }
                                listOf(dateFormatter.format(item.date), item.ledgerName, item.voucherNumber, shortGroup, if (item.isDebit) "%.2f Dr".format(item.pendingAmount) else "%.2f Cr".format(item.pendingAmount), pendingDays.toString())
                            }
                            ExportUtils.exportReportToExcel(context, "Outstandings_${System.currentTimeMillis()}.xlsx", "Outstandings", headers, data)
                        }) {
                            Icon(Icons.Default.TableChart, TerminologyManager.getTerm("Export Excel"), tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E).copy(alpha = 0.9f))
                )
            }
        ) { paddingValues ->
            // Single LazyColumn — entire screen scrolls as one unit (hero + search + list)
            val heroBgColor = when (currentAppTheme) {
                AppTheme.CRYSTAL -> Color.White.copy(alpha = 0.15f)
                AppTheme.CYBER_FINANCE -> Color(0xFF151515)
                AppTheme.BENTO -> Color.White
                else -> Color(0x802C2B4B)
            }
            val heroBorder = when (currentAppTheme) {
                AppTheme.CRYSTAL -> BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                AppTheme.CYBER_FINANCE -> BorderStroke(2.dp, Color(0xFF00E676))
                AppTheme.BENTO -> BorderStroke(1.dp, Color.LightGray)
                else -> BorderStroke(1.dp, Color(0x33FFFFFF))
            }
            val outlineColor = if (currentAppTheme == AppTheme.CRYSTAL) Color.White.copy(alpha = 0.4f) else Color(0xFF00E676)
            val buttonTextColor = if (currentAppTheme == AppTheme.BENTO) Color.Black else Color.White

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // Hero Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, top = 0.dp, bottom = 4.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = heroBgColor),
                        border = heroBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = if (currentAppTheme == AppTheme.CRYSTAL) 0.dp else 8.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = TerminologyManager.getTerm("TOTAL OUTSTANDING"),
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val netAmount = totalReceivable - totalPayable
                            val isNetReceivable = netAmount >= 0
                            val netColor = if (isNetReceivable) Color(0xFF00E676) else Color(0xFFFF5252)
                            AutoShrinkText(
                                text = "₹ ${"%.2f".format(kotlin.math.abs(netAmount))}",
                                color = netColor,
                                maxFontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            )
                            AutoShrinkText(
                                text = if (isNetReceivable) TerminologyManager.getTerm("Receivable") else TerminologyManager.getTerm("Payable"),
                                color = netColor.copy(alpha = 0.85f),
                                maxFontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                AutoShrinkText("₹ ${"%.2f".format(totalReceivable)} Rec", color = Color(0xFF00E676), maxFontSize = 13.sp, fontWeight = FontWeight.Bold)
                                AutoShrinkText("₹ ${"%.2f".format(totalPayable)} Pay", color = Color(0xFFFF5252), maxFontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                val options = listOf("Receivable", "Payable", "Both")
                                options.forEach { option ->
                                    val isSelected = filterType == option
                                    val color = when (option) {
                                        "Receivable" -> Color(0xFF00E676)
                                        "Payable" -> Color(0xFFFF5252)
                                        else -> Color.White
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) { if (option == "Both") Color.White else color.copy(alpha = 0.2f) } else Color.Transparent)
                                            .border(1.dp, if (isSelected) color else Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                            .clickable { filterType = option }
                                            .padding(horizontal = 12.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = TerminologyManager.getTerm(option),
                                            color = if (isSelected) { if (option == "Both") Color(0xFF1A1A2E) else color } else Color.Gray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                            // Aging buckets — inside the hero card, no label, minimal padding.
                            val bucketTextColor = if (currentAppTheme == AppTheme.BENTO) Color.Black else Color.White
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                agingBuckets.forEach { bucket ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(bucket.color.copy(alpha = 0.12f))
                                            .padding(vertical = 10.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(bucket.label, color = bucket.color, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text("₹${"%.0f".format(bucket.total)}", color = bucketTextColor, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                                        AutoShrinkText("${bucket.count} bill${if (bucket.count != 1) "s" else ""}", color = bucketTextColor.copy(alpha = 0.5f), maxFontSize = 9.sp, minFontSize = 7.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                // Search & Date Filter Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(TerminologyManager.getTerm("Search"), color = Color.Gray, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = if (currentAppTheme == AppTheme.BENTO) Color.LightGray.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = if (currentAppTheme == AppTheme.BENTO) Color.LightGray.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = if (currentAppTheme == AppTheme.BENTO) Color.Black else Color.White,
                                unfocusedTextColor = if (currentAppTheme == AppTheme.BENTO) Color.Black else Color.White
                            ),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Compact single date-range box (From – To), like the ledger view.
                        val compactDf = SimpleDateFormat("dd MMM yy", Locale.getDefault())
                        OutlinedButton(
                            onClick = { showDateRangeDialog = true },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, outlineColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonTextColor)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = TerminologyManager.getTerm("Date Range"), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${compactDf.format(displayFromDate)} - ${compactDf.format(displayToDate)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Sort-by-date toggle (newest / oldest first).
                        OutlinedButton(
                            onClick = { sortDescending = !sortDescending },
                            modifier = Modifier.height(48.dp).width(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, outlineColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonTextColor)
                        ) {
                            Icon(
                                imageVector = if (sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = if (sortDescending) TerminologyManager.getTerm("Newest first") else TerminologyManager.getTerm("Oldest first"),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Transaction Cards
                items(sortedItems) { item ->
                    OutstandingPaymentItem(item, context, navController, currentAppTheme)
                    }
                }
            }
        }
    }


@Composable
fun OutstandingPaymentItem(
    item: OutstandingItem,
    context: android.content.Context,
    navController: NavHostController,
    currentAppTheme: AppTheme = AppTheme.DEFAULT
) {
    var showUpiSetupDialog by remember { mutableStateOf(false) }
    var savedUpiId by remember {
        mutableStateOf(context.getSharedPreferences("payment_prefs", android.content.Context.MODE_PRIVATE).getString("upi_id", "") ?: "")
    }

    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val sendWhatsApp = { upi: String ->
        val companyName = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE).getString("company_name", "Teller App") ?: "Teller App"
        val formattedDate = dateFormatter.format(item.date)
        val formattedAmt = "%.2f".format(item.pendingAmount)
        val message = if (upi.isNotBlank()) {
            "Hello ${item.ledgerName}, a payment of ₹$formattedAmt is pending since $formattedDate. Please pay instantly using this link: upi://pay?pa=$upi&pn=${android.net.Uri.encode(companyName)}&am=$formattedAmt&cu=INR&tn=Bill-${item.voucherNumber}"
        } else {
            "Hello ${item.ledgerName}, a payment of ₹$formattedAmt is pending since $formattedDate."
        }
        val phone = item.phoneNumber?.filter { it.isDigit() }.orEmpty()
        val base = if (phone.isNotBlank()) "https://wa.me/$phone?text=" else "https://wa.me/?text="
        val uri = android.net.Uri.parse(base + android.net.Uri.encode(message))
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "WhatsApp not installed", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val diffInMillis = System.currentTimeMillis() - item.date.time
    val pendingDays = (diffInMillis / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
    
    // Color Coding
    val suffix = if (item.isDebit) "Receivable" else "Payable"
    val statusColor = if (item.isDebit) Color(0xFF00E676) else Color(0xFFFF5252)
    
    val typeBorderColor = when (item.voucherType) {
        "Sales"    -> Color(0xFF00E676)  // Green
        "Purchase" -> Color(0xFFFF5252)  // Red
        "Payment"  -> Color(0xFFFFD54F)  // Yellow
        "Receipt"  -> Color(0xFF2196F3)  // Blue
        else       -> statusColor
    }

    val cardBgColor = when (currentAppTheme) {
        AppTheme.CRYSTAL      -> typeBorderColor.copy(alpha = 0.07f)
        AppTheme.CYBER_FINANCE -> typeBorderColor.copy(alpha = 0.45f)
        AppTheme.BENTO        -> typeBorderColor.copy(alpha = 0.6f)
        else                  -> typeBorderColor.copy(alpha = 0.38f) // darker frosted glass
    }
    
    val cardBorder = when (currentAppTheme) {
        AppTheme.CRYSTAL -> BorderStroke(1.dp, typeBorderColor.copy(alpha = 0.3f))
        AppTheme.CYBER_FINANCE -> BorderStroke(1.dp, typeBorderColor.copy(alpha = 0.45f))
        AppTheme.BENTO -> BorderStroke(1.dp, typeBorderColor.copy(alpha = 0.4f))
        else -> BorderStroke(1.dp, typeBorderColor.copy(alpha = 0.35f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp, vertical = 3.dp) // Reduced vertical spacing between cards by 50%
            .clickable { 
                navController.navigate("outstanding_bill_detail/${item.voucherId}/${item.ledgerId}")
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (currentAppTheme == AppTheme.CRYSTAL) 0.dp else 4.dp)
    ) {
        // Reduced inner top/bottom padding further by 25% (from 8.dp to 6.dp)
        Box(modifier = Modifier.background(Color.White.copy(alpha = 0.06f)).padding(start = 11.dp, end = 11.dp, top = 4.dp, bottom = 4.dp)) {
            // Watermark text in background — colors match card type
            Text(
                text = item.voucherType.uppercase(),
                color = when (item.voucherType) {
                    "Sales"    -> Color(0xFF00E676).copy(alpha = 0.05f)  // Green
                    "Purchase" -> Color(0xFFFF5252).copy(alpha = 0.05f)  // Red
                    "Payment"  -> Color(0xFFFFD54F).copy(alpha = 0.05f)  // Yellow
                    "Receipt"  -> Color(0xFF2196F3).copy(alpha = 0.05f)  // Blue
                    else -> if (item.isDebit) Color(0xFF00E676).copy(alpha = 0.05f) else Color(0xFFFF5252).copy(alpha = 0.05f)
                },
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center)
            )

            // Determine display name and sub-label based on payment mode
            val isFinanceLedger = item.ledgerGroup.lowercase().let { g ->
                g.contains("finance") || g.contains("bank account") || g.contains("bank od") || g.contains("bank occ")
            }
            val isOnCreditLedger = item.ledgerGroup == "Sundry Debtors"
            // Main header: finance company name, "On Credit", or ledger name
            val mainHeaderName = when {
                isFinanceLedger -> item.ledgerName  // e.g. "ICICI Bank" or "Bajaj Finance"
                isOnCreditLedger -> TerminologyManager.getTerm("On Credit")
                else -> item.ledgerName
            }
            // Sub-label: customer name (from customField) for finance/on-credit
            val subCustomerName = when {
                isFinanceLedger && !item.customerName.isNullOrBlank() -> item.customerName
                isOnCreditLedger -> item.ledgerName  // ledgerName IS the customer for on-credit
                else -> null
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // ROW 1: Main header (Left) & Amount (Right)
                Row(
                     modifier = Modifier.fillMaxWidth(),
                     horizontalArrangement = Arrangement.SpaceBetween,
                     verticalAlignment = Alignment.Top
                ) {
                    AutoShrinkText(
                        text = mainHeaderName,
                        color = if (currentAppTheme == AppTheme.BENTO) Color.Black else Color.White,
                        maxFontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        AutoShrinkText(
                            text = "₹ ${"%.2f".format(item.pendingAmount)}",
                            color = statusColor,
                            maxFontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        AutoShrinkText(
                            text = suffix,
                            color = statusColor,
                            maxFontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                // ROW 1b: Customer name sub-label
                if (!subCustomerName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(1.dp))
                    AutoShrinkText(
                        text = subCustomerName!!,
                        color = typeBorderColor.copy(alpha = 0.9f),
                        maxFontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                // Bill ref note — suppress when it merely repeats the header or customer sub-label
                val displayNote = (item.refDisplayName?.takeIf { it.isNotBlank() }
                    ?: item.billRefName?.takeIf { it.isNotBlank() })
                    ?.takeIf { note ->
                        !note.equals(mainHeaderName, ignoreCase = true) &&
                        !note.equals(subCustomerName, ignoreCase = true)
                    }
                if (displayNote != null) {
                    Spacer(modifier = Modifier.height(1.dp))
                    AutoShrinkText(
                        text = displayNote,
                        color = Color(0xFF9E9E9E),
                        maxFontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // ROW 2: Date (Left) & Days Pending/Share (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AutoShrinkText(
                        text = dateFormatter.format(item.date),
                        color = if (currentAppTheme == AppTheme.BENTO) Color.DarkGray else Color(0xFFE0E0E0),
                        maxFontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )

                    AutoShrinkText(
                        text = "$pendingDays Days",
                        color = if (pendingDays > 30) Color(0xFFFF5252) else (if (currentAppTheme == AppTheme.BENTO) Color.Gray else Color(0xFFB0B0B0)),
                        maxFontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )




                    val actionColor = if (currentAppTheme == AppTheme.BENTO) Color.Gray else Color(0xFFB0B0B0)
                    Row(
                        modifier = Modifier
                            .clickable {
                                if (savedUpiId.isBlank()) {
                                    showUpiSetupDialog = true
                                } else {
                                    sendWhatsApp(savedUpiId)
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = TerminologyManager.getTerm("Send Reminder"),
                            color = actionColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = TerminologyManager.getTerm("Share"),
                            tint = actionColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // MSMED Act Section 16 Badge & 1-tap Legal Notice if > 45 days overdue
                val isMsmedOverdue = pendingDays > 45 && item.isDebit
                if (isMsmedOverdue) {
                    val overdueDays = (pendingDays - 45).coerceAtLeast(0)
                    val monthsOverdue = overdueDays / 30.0
                    val msmedInterest = item.pendingAmount * (Math.pow(1.0 + (0.195 / 12.0), monthsOverdue) - 1.0)
                    val totalWithInterest = item.pendingAmount + msmedInterest

                    val sendMsmeNotice = { upi: String ->
                        val companyName = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE).getString("company_name", "Teller App") ?: "Teller App"
                        val formattedDate = dateFormatter.format(item.date)
                        val formattedAmt = "%.2f".format(item.pendingAmount)
                        val formattedInterest = "%.2f".format(msmedInterest)
                        val formattedTotal = "%.2f".format(totalWithInterest)
                        val upiSnippet = if (upi.isNotBlank()) "\nPay Now: upi://pay?pa=$upi&pn=${android.net.Uri.encode(companyName)}&am=$formattedTotal&cu=INR&tn=MSME-${item.voucherNumber}" else ""
                        val msg = "⚠️ *STATUTORY NOTICE UNDER MSMED ACT, 2006 (SECTION 15 & 16)*\n\n" +
                                "To: *${item.ledgerName}*\n" +
                                "From: *$companyName*\n\n" +
                                "Your invoice *#${item.voucherNumber}* dated *$formattedDate* for *₹$formattedAmt* is outstanding beyond the mandatory 45-day credit period (Overdue by $overdueDays days).\n\n" +
                                "As per Section 16 of the MSMED Act, compound penal interest @ 3x RBI Bank Rate (19.5% p.a.) has accrued:\n" +
                                "• Principal: ₹$formattedAmt\n" +
                                "• Accrued MSME Interest: ₹$formattedInterest\n" +
                                "• *Total Payable: ₹$formattedTotal*\n" +
                                "$upiSnippet\n\n" +
                                "Please settle immediately to prevent escalation to the MSME Facilitation Council (MSEFC)."
                        val phone = item.phoneNumber?.filter { it.isDigit() }.orEmpty()
                        val base = if (phone.isNotBlank()) "https://wa.me/$phone?text=" else "https://wa.me/?text="
                        val uri = android.net.Uri.parse(base + android.net.Uri.encode(msg))
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "WhatsApp not installed", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFD32F2F).copy(alpha = 0.16f))
                            .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MSMED Sec 16 (>45d): +₹${"%.0f".format(msmedInterest)} statutory interest",
                                color = Color(0xFFFF8A80),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Total with 19.5% compound int: ₹${"%.2f".format(totalWithInterest)}",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF5252))
                                .clickable {
                                    if (savedUpiId.isBlank()) {
                                        showUpiSetupDialog = true
                                    } else {
                                        sendMsmeNotice(savedUpiId)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Send MSME Notice",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (showUpiSetupDialog) {
                    var upiInput by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showUpiSetupDialog = false },
                        title = { Text(TerminologyManager.getTerm("Setup Business UPI"), color = Color.White) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = TerminologyManager.getTerm("Enter your business UPI ID (e.g. sharma@okaxis) to attach instant payment links to your customer WhatsApp reminders."),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                                OutlinedTextField(
                                    value = upiInput,
                                    onValueChange = { upiInput = it },
                                    label = { Text(TerminologyManager.getTerm("UPI ID"), color = Color.White.copy(alpha = 0.6f)) },
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        cursorColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (upiInput.isNotBlank()) {
                                        context.getSharedPreferences("payment_prefs", android.content.Context.MODE_PRIVATE)
                                            .edit()
                                            .putString("upi_id", upiInput.trim())
                                            .apply()
                                        savedUpiId = upiInput.trim()
                                        showUpiSetupDialog = false
                                        sendWhatsApp(upiInput.trim())
                                    }
                                }
                            ) {
                                    Text(TerminologyManager.getTerm("Save & Send"), color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showUpiSetupDialog = false
                                    sendWhatsApp("")
                                }
                            ) {
                                Text(TerminologyManager.getTerm("Send without UPI"), color = Color.White.copy(alpha = 0.6f))
                            }
                        },
                        containerColor = Color(0xFF2A1B4E)
                    )
                }
                
                // HISTORY SECTION
                if (item.paymentHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    item.paymentHistory.forEach { history ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AutoShrinkText(
                                text = "Received on ${dateFormatter.format(history.date)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                maxFontSize = 11.sp
                            )
                            AutoShrinkText(
                                text = "₹ ${"%.2f".format(history.amount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF00E676).copy(alpha = 0.8f),
                                maxFontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
