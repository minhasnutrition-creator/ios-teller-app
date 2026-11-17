package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.components.TellerBackground
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.ui.viewmodels.StockItemViewModel
import com.minhas.teller.ui.viewmodels.StockItemViewModelFactory
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import com.minhas.teller.utils.ExportUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayInvoicesScreen(navController: NavHostController, selectedDateMillis: Long? = null) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val voucherViewModel: VoucherViewModel = viewModel(
        factory = VoucherViewModelFactory(
            database.voucherDao(), database.voucherEntryDao(),
            database.inventoryEntryDao(), database.ledgerDao(),
            database.billAllocationDao(), database.auditLogDao(),
            db = database
        )
    )
    val companyRepository = CompanyRepository(database.companyDao())
    val companyViewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(companyRepository))
    val stockItemRepository = StockItemRepository(database.stockItemDao())
    val stockItemViewModel: StockItemViewModel = viewModel(factory = StockItemViewModelFactory(stockItemRepository))

    val allVouchersWithAllEntries by voucherViewModel.allVouchersWithAllEntries.collectAsState()
    val ledgers by voucherViewModel.allLedgers.collectAsState()
    val stockItems by stockItemViewModel.allStockItems.collectAsState()

    val isSpecificDateView = selectedDateMillis != null

    var fromDate by remember {
        val date = if (selectedDateMillis != null) atStartOfDay(Date(selectedDateMillis))
        else atStartOfDay(Date())
        mutableStateOf(date)
    }
    var toDate by remember {
        val date = if (selectedDateMillis != null) atEndOfDay(Date(selectedDateMillis))
        else atEndOfDay(Date())
        mutableStateOf(date)
    }

    var dateRangeOption by remember { mutableStateOf("Today") }

    LaunchedEffect(dateRangeOption) {
        if (!isSpecificDateView) {
            val cal = Calendar.getInstance()
            val today = Date()
            toDate = atEndOfDay(today)
            when (dateRangeOption) {
                "Today" -> {
                    fromDate = atStartOfDay(today)
                }
                "Last 5 Days" -> {
                    cal.time = today
                    cal.add(Calendar.DAY_OF_YEAR, -4)
                    fromDate = atStartOfDay(cal.time)
                }
                "Last 10 Days" -> {
                    cal.time = today
                    cal.add(Calendar.DAY_OF_YEAR, -9)
                    fromDate = atStartOfDay(cal.time)
                }
                "Whole Month" -> {
                    cal.time = today
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    fromDate = atStartOfDay(cal.time)
                }
            }
        }
    }

    LaunchedEffect(fromDate, toDate) {
        voucherViewModel.getAllVouchersWithAllEntriesBetweenDates(fromDate, toDate)
    }

    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val currentCompany by produceState<Company?>(initialValue = null, currentCompanyId) {
        currentCompanyId?.let { id -> companyViewModel.getCompany(id).collect { value = it } }
    }
    val companyName = currentCompany?.name ?: "Teller App"

    var selectedFilter by remember { mutableStateOf("Sales") }
    var showExportMenu by remember { mutableStateOf(false) }
    var showVoucherDialog by remember { mutableStateOf(false) }
    var selectedVoucher by remember { mutableStateOf<VoucherWithAllEntries?>(null) }

    // ── Print from a card ────────────────────────────────────────────────────
    // The icon builds the real invoice PDF and opens it in the PDF viewer, which already offers
    // Download / Print / Share. Deliberately NOT the Printer Hub: that screen asks the user to
    // pick and pair a printer before they have seen anything, and the receipt preview it shows
    // isn't this invoice — so the printer stuff only starts if they tap Print on the actual bill.
    val scope = rememberCoroutineScope()
    var preparingVoucherId by remember { mutableStateOf<Int?>(null) }

    fun openInvoicePdf(v: VoucherWithAllEntries) {
        if (preparingVoucherId != null) return
        scope.launch {
            preparingVoucherId = v.voucher.id
            // Units and bill-wise allocations aren't loaded for the list, so fetch them for the
            // one voucher being printed — otherwise the PDF loses its UOM column and bill breakup.
            val units = runCatching { database.unitDao().getAllUnits(v.voucher.companyId) }.getOrDefault(emptyList())
            val allocations = runCatching {
                database.billAllocationDao().getAllocationsForVoucher(v.voucher.id).first()
            }.getOrDefault(emptyList())
            val isSales = v.voucher.type == "Sales"
            val partyEntry = v.voucherEntries.firstOrNull { if (isSales) it.isDebit else !it.isDebit }
            ExportUtils.exportInvoiceToPdf(
                context = context,
                voucher = v.voucher,
                customerLedger = ledgers.firstOrNull { it.id == partyEntry?.ledgerId },
                inventoryEntries = v.inventoryEntries,
                stockItems = stockItems,
                units = units,
                company = currentCompany,
                billAllocations = allocations,
                share = false,
                voucherEntries = v.voucherEntries
            )
            preparingVoucherId = null
        }
    }

    val ledgerMap = remember(ledgers) { ledgers.associateBy { it.id } }
    val stockItemMap = remember(stockItems) { stockItems.associateBy { it.id } }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val currencySymbol = "₹"

    val recentInvoices = remember(allVouchersWithAllEntries, selectedFilter, isSpecificDateView) {
        val base = if (isSpecificDateView)
            allVouchersWithAllEntries.filter { it.voucher.type == "Sales" }
        else if (selectedFilter == "All")
            allVouchersWithAllEntries
        else
            allVouchersWithAllEntries.filter { it.voucher.type == selectedFilter }
        base.sortedByDescending { it.voucher.date }
    }


    if (showVoucherDialog && selectedVoucher != null) {
        AlertDialog(
            onDismissRequest = { showVoucherDialog = false },
            title = { Text(TerminologyManager.getTerm("Voucher Options")) },
            text = { Text(TerminologyManager.getTerm("What would you like to do?")) },
            confirmButton = {
                TextButton(onClick = {
                    showVoucherDialog = false
                    navController.navigate(Screen.AddEditVoucher.route + "?voucherId=${selectedVoucher!!.voucher.id}")
                }) { Text(TerminologyManager.getTerm("Edit")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showVoucherDialog = false
                    navController.navigate(Screen.VoucherView.route.replace("{voucherId}", selectedVoucher!!.voucher.id.toString()))
                }) { Text(TerminologyManager.getTerm("View")) }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A1128), // Deep Royal Dark Blue
                        Color(0xFF050A14)  // Very dark blue/black
                    )
                )
            )
    ) {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 16.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "Daily Sales Summary",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dateLabel = dateFormatter.format(fromDate)
                Surface(
                    modifier = Modifier.clickable {
                        val cal = Calendar.getInstance().apply { time = fromDate }
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val pickedDate = Calendar.getInstance().apply { set(year, month, day) }.time
                                fromDate = atStartOfDay(pickedDate)
                                toDate = atEndOfDay(pickedDate)
                                dateRangeOption = "Custom"
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    color = Color(0xFF1E2D4A).copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        AutoShrinkText(
                            text = dateLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxFontSize = 15.sp
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Box {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { showExportMenu = true },
                        color = Color(0xFF1E2D4A).copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Export options",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm("Export to PDF")) },
                            onClick = {
                                showExportMenu = false
                                val headers = listOf("Customer", "Items", "Bill #", "Date", "Total")
                                val data = recentInvoices.map { v ->
                                    val total = v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                                    val itemsSummary = v.inventoryEntries.joinToString(", ") { e ->
                                        "${stockItemMap[e.stockItemId]?.name ?: "Item"} x ${e.quantity}"
                                    }
                                    listOf(
                                        resolveCustomerName(v, ledgerMap),
                                        itemsSummary,
                                        v.voucher.voucherNumber,
                                        dateFormatter.format(v.voucher.date),
                                        "$currencySymbol ${"%.2f".format(total)}"
                                    )
                                }
                                ExportUtils.exportReportToPdf(context, "Sales_Selected_Date.pdf", companyName, "Sales Report", headers, floatArrayOf(2.6f, 3.2f, 1.8f, 1.5f, 1.9f), data)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm("Export to Excel")) },
                            onClick = {
                                showExportMenu = false
                                val headers = listOf("Customer", "Items", "Bill #", "Date", "Total")
                                val data = recentInvoices.map { v ->
                                    val total = v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                                    val itemsSummary = v.inventoryEntries.joinToString(", ") { e ->
                                        "${stockItemMap[e.stockItemId]?.name ?: "Item"} x ${e.quantity}"
                                    }
                                    listOf(
                                        resolveCustomerName(v, ledgerMap),
                                        itemsSummary,
                                        v.voucher.voucherNumber,
                                        dateFormatter.format(v.voucher.date),
                                        "%.2f".format(total)
                                    )
                                }
                                ExportUtils.exportReportToExcel(context, "Sales_Selected_Date.xlsx", "Sales", headers, data)
                            }
                        )
                    }
                }
            }

            if (!isSpecificDateView) {
                val rangeOptions = listOf("Today", "Last 5 Days", "Last 10 Days", "Whole Month")
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(rangeOptions) { opt ->
                        FilterChip(
                            selected = dateRangeOption == opt,
                            onClick = { dateRangeOption = opt },
                            label = { Text(opt, fontSize = 11.sp, color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = 0.03f),
                                selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                labelColor = Color.White.copy(alpha = 0.6f),
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = dateRangeOption == opt,
                                borderColor = Color.White.copy(alpha = 0.08f),
                                selectedBorderColor = Color(0xFF00E5FF).copy(alpha = 0.4f),
                                borderWidth = 1.dp
                            )
                        )
                    }
                }
                val filterTypes = listOf("All", "Sales", "Purchase", "Receipt", "Payment", "Journal")
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filterTypes) { type ->
                        FilterChip(
                            selected = selectedFilter == type,
                            onClick = { selectedFilter = type },
                            label = { Text(type, fontSize = 11.sp, color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = 0.03f),
                                selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                labelColor = Color.White.copy(alpha = 0.6f),
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedFilter == type,
                                borderColor = Color.White.copy(alpha = 0.08f),
                                selectedBorderColor = Color(0xFF00E5FF).copy(alpha = 0.4f),
                                borderWidth = 1.dp
                            )
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            val totalSales = recentInvoices.sumOf { v ->
                v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
            }.paiseToRupees()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2D4A).copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Today's Total Sales:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AutoShrinkText(
                            text = "$currencySymbol ${"%,.2f".format(totalSales)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                    }

                    val sparklinePoints = remember(recentInvoices) {
                        if (recentInvoices.size >= 3) {
                            recentInvoices.reversed().map { v ->
                                v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.toFloat()
                            }
                        } else {
                            listOf(10f, 15f, 12f, 25f, 18f, 30f, 22f, 35f)
                        }
                    }
                    Sparkline(
                        data = sparklinePoints,
                        modifier = Modifier
                            .width(120.dp)
                            .height(45.dp),
                        color = Color(0xFFFFD54F)
                    )
                }
            }

            if (recentInvoices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(TerminologyManager.getTerm("No invoices found"), color = Color.White.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentInvoices) { v ->
                        val totalAmount = v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                        val customerName = resolveCustomerName(v, ledgerMap)
                        val itemsSummary = v.inventoryEntries.joinToString(", ") { e ->
                            "${stockItemMap[e.stockItemId]?.name ?: "Item"} x ${e.quantity}"
                        }
                        val isHighlighted = selectedVoucher?.voucher?.id == v.voucher.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .shadow(
                                    elevation = if (isHighlighted) 8.dp else 0.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    spotColor = Color(0xFF00E5FF),
                                    ambientColor = Color(0xFF00E5FF)
                                )
                                .clickable { selectedVoucher = v; showVoucherDialog = true },
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E2D4A).copy(alpha = if (isHighlighted) 0.5f else 0.25f)
                            ),
                            border = BorderStroke(
                                width = if (isHighlighted) 1.5.dp else 1.dp,
                                color = if (isHighlighted) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.08f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        AutoShrinkText(
                                            text = customerName,
                                            fontWeight = FontWeight.Bold,
                                            maxFontSize = 18.sp,
                                            color = Color.White
                                        )

                                        val ledgerName = resolveLedgerName(v, ledgerMap)
                                        if (ledgerName != customerName && ledgerName.isNotEmpty() && ledgerName != "Walk-in Customer") {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            AutoShrinkText(
                                                text = "($ledgerName)",
                                                maxFontSize = 11.sp,
                                                minFontSize = 8.sp,
                                                color = Color(0xFFB8A0D0),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    AutoShrinkText(
                                        text = "$currencySymbol ${"%,.2f".format(totalAmount)}",
                                        fontWeight = FontWeight.Bold,
                                        maxFontSize = 16.sp,
                                        color = Color(0xFF00E5FF),
                                        textAlign = TextAlign.End
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { openInvoicePdf(v) },
                                        enabled = preparingVoucherId == null,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        if (preparingVoucherId == v.voucher.id) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = Color(0xFF00E5FF)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Print,
                                                contentDescription = "Open invoice PDF for ${v.voucher.voucherNumber}",
                                                tint = Color(0xFF00E5FF),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    if (v.voucher.type == "Sales") {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                val customerLedger = v.voucherEntries.firstOrNull { entry ->
                                                    ledgerMap[entry.ledgerId]?.group == "Sundry Debtors" || ledgerMap[entry.ledgerId]?.group == "Finance Companies"
                                                }?.ledgerId?.let { ledgerMap[it] }
                                                ExportUtils.shareInvoiceOnWhatsApp(
                                                    context, v.voucher, customerLedger,
                                                    v.inventoryEntries, stockItems, emptyList(), currentCompany
                                                )
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share on WhatsApp",
                                                tint = Color(0xFF25D366),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                AutoShrinkText(
                                    text = itemsSummary,
                                    maxFontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )

                                val desc = v.voucher.customField ?: v.voucher.narration ?: ""
                                if (desc.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    AutoShrinkText(
                                        text = desc,
                                        maxFontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.5f)
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

private fun resolveCustomerName(voucherWithAllEntries: VoucherWithAllEntries, ledgerMap: Map<Int, Ledger>): String {
    val refDisplayName = voucherWithAllEntries.voucher.refDisplayName
    if (!refDisplayName.isNullOrBlank()) return refDisplayName
    val debtorEntry = voucherWithAllEntries.voucherEntries.firstOrNull { entry ->
        ledgerMap[entry.ledgerId]?.group == "Sundry Debtors"
    }
    return ledgerMap[debtorEntry?.ledgerId]?.name ?: "Walk-in Customer"
}

private fun resolveLedgerName(voucherWithAllEntries: VoucherWithAllEntries, ledgerMap: Map<Int, Ledger>): String {
    val entries = voucherWithAllEntries.voucherEntries
    if (entries.isEmpty()) return "Unknown"
    val debtorEntry = entries.firstOrNull { entry ->
        val grp = ledgerMap[entry.ledgerId]?.group
        grp == "Sundry Debtors" || grp == "Finance Companies"
    }
    if (debtorEntry != null) {
        return ledgerMap[debtorEntry.ledgerId]?.name ?: "Sundry Debtor"
    }
    val v = voucherWithAllEntries.voucher
    return if (v.type == "Receipt") {
        val party = entries.firstOrNull { !it.isDebit && ledgerMap[it.ledgerId]?.group != "Cash-in-hand" && ledgerMap[it.ledgerId]?.group != "Bank Accounts" }
        ledgerMap[party?.ledgerId]?.name ?: "Receipt"
    } else {
        val debited = entries.firstOrNull { it.isDebit }
        ledgerMap[debited?.ledgerId]?.name ?: "Walk-in Customer"
    }
}

private fun atStartOfDay(value: Date): Date {
    val cal = Calendar.getInstance()
    cal.time = value
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.time
}

private fun atEndOfDay(value: Date): Date {
    val cal = Calendar.getInstance()
    cal.time = value
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return cal.time
}

@Composable
fun Sparkline(
    data: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFFD54F),
    strokeWidth: Float = 3f
) {
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val width = size.width
        val height = size.height
        val maxVal = data.maxOrNull() ?: 1f
        val minVal = data.minOrNull() ?: 0f
        val delta = if (maxVal - minVal == 0f) 1f else maxVal - minVal

        val points = data.mapIndexed { index, valRaw ->
            val x = index * (width / (data.size - 1))
            val y = height - ((valRaw - minVal) / delta * (height - 10f) + 5f)
            Offset(x, y)
        }

        val strokePath = Path()
        val fillPath = Path()

        points.forEachIndexed { index, point ->
            if (index == 0) {
                strokePath.moveTo(point.x, point.y)
                fillPath.moveTo(point.x, height)
                fillPath.lineTo(point.x, point.y)
            } else {
                val prevPoint = points[index - 1]
                val ctrlPt1 = Offset(prevPoint.x + (point.x - prevPoint.x) * 0.5f, prevPoint.y)
                val ctrlPt2 = Offset(prevPoint.x + (point.x - prevPoint.x) * 0.5f, point.y)
                strokePath.cubicTo(ctrlPt1.x, ctrlPt1.y, ctrlPt2.x, ctrlPt2.y, point.x, point.y)
                fillPath.cubicTo(ctrlPt1.x, ctrlPt1.y, ctrlPt2.x, ctrlPt2.y, point.x, point.y)
            }
            if (index == points.size - 1) {
                fillPath.lineTo(point.x, height)
                fillPath.close()
            }
        }

        // Draw the filled gradient path
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.25f),
                    Color.Transparent
                )
            )
        )

        // Draw the stroke path
        drawPath(
            path = strokePath,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )
    }
}
