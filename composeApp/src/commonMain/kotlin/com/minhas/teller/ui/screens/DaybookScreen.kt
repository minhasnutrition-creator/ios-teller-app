package com.minhas.teller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.utils.TerminologyManager
import androidx.compose.ui.text.style.TextOverflow
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.ui.viewmodels.StockItemViewModel
import com.minhas.teller.ui.viewmodels.StockItemViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaybookScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)

    val voucherViewModel: VoucherViewModel = viewModel(factory = VoucherViewModelFactory(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.ledgerDao(), database.billAllocationDao(), database.auditLogDao(), db = database))

    val companyRepository = CompanyRepository(database.companyDao())
    val companyViewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(companyRepository))
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val company by if (currentCompanyId != null) {
        companyViewModel.getCompany(currentCompanyId!!).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(null) }
    }
    val companyName = company?.name ?: TerminologyManager.getTerm("Teller App")

    val allVouchersWithAllEntries by voucherViewModel.allVouchersWithAllEntries.collectAsState()
    val allLedgers by voucherViewModel.allLedgers.collectAsState()
    val ledgerMap = remember(allLedgers) { allLedgers.associateBy { it.id } }

    val stockItemRepository = remember { StockItemRepository(database.stockItemDao()) }
    val stockItemViewModel: StockItemViewModel = viewModel(factory = StockItemViewModelFactory(stockItemRepository))
    val stockItems by stockItemViewModel.allStockItems.collectAsState()
    val stockItemMap = remember(stockItems) { stockItems.associateBy { it.id } }
    
    val unitRepository = com.minhas.teller.data.repositories.UnitRepository(database.unitDao())
    val unitViewModel: com.minhas.teller.ui.viewmodels.UnitViewModel = viewModel(factory = com.minhas.teller.ui.viewmodels.UnitViewModelFactory(unitRepository))
    val allUnits by unitViewModel.allUnits.collectAsState()
    val unitMap = remember(allUnits) { allUnits.associateBy { it.id } }

    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val shortDateFormatter = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }

    var fromDate by remember { 
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        mutableStateOf(cal.time) 
    }
    var toDate by remember { 
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        mutableStateOf(cal.time) 
    }

    LaunchedEffect(fromDate, toDate) {
        voucherViewModel.getAllVouchersWithAllEntriesBetweenDates(fromDate, toDate)
    }

    var showExportMenu by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedVoucher by remember { mutableStateOf<VoucherWithAllEntries?>(null) }

    if (showDialog && selectedVoucher != null) {
        AlertDialog(
            onDismissRequest = { 
                showDialog = false 
                selectedVoucher = null
            },
            title = { Text(TerminologyManager.getTerm("Voucher Options")) },
            text = { Text(TerminologyManager.getTerm("What would you like to do with this voucher?")) },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            showDialog = false
                            showDeleteConfirmDialog = true
                        }
                    ) {
                        Text(TerminologyManager.getTerm("Delete"), color = Color(0xFFFF5252))
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                showDialog = false
                                navController.navigate(Screen.VoucherView.route.replace("{voucherId}", selectedVoucher!!.voucher.id.toString()))
                                selectedVoucher = null
                            }
                        ) {
                            Text(TerminologyManager.getTerm("View"))
                        }
                        TextButton(
                            onClick = {
                                showDialog = false
                                navController.navigate(Screen.AddEditVoucher.route + "?voucherId=${selectedVoucher!!.voucher.id}")
                                selectedVoucher = null
                            }
                        ) {
                            Text(TerminologyManager.getTerm("Edit"))
                        }
                    }
                }
            },
            dismissButton = null
        )
    }

    if (showDeleteConfirmDialog && selectedVoucher != null) {
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
                    TerminologyManager.getTerm("Are you sure you want to permanently delete this transaction? All associated ledger balances and stock movements will be reversed. This action cannot be undone.")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        voucherViewModel.deleteVoucherWithAllEntries(selectedVoucher!!.voucher)
                        selectedVoucher = null
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


    // Full screen gradient background
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33000000))
                        .statusBarsPadding()
                ) {
                    // Custom top bar with own back button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = TerminologyManager.getTerm("Back"),
                                tint = Color.White
                            )
                        }
                        Text(
                            text = TerminologyManager.getTerm("Daybook").uppercase(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            letterSpacing = 1.sp
                        )
                        IconButton(onClick = { navController.navigate(Screen.Dashboard.route) }) {
                            Icon(Icons.Default.Home, contentDescription = TerminologyManager.getTerm("Home"), tint = Color.White)
                        }
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = TerminologyManager.getTerm("Export"), tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(TerminologyManager.getTerm("Export to PDF")) },
                                    onClick = {
                                        showExportMenu = false
                                        val filteredVouchers = allVouchersWithAllEntries.filter {
                                            !it.voucher.date.before(fromDate) && !it.voucher.date.after(toDate)
                                        }
                                        val headers = listOf("Date", "Vch No", "Type", "Party", "Particulars (Items & Narration)", "Amount")
                                        val data = filteredVouchers.map { vwae ->
                                            val totalAmt = vwae.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                                            val particulars = buildString {
                                                if (vwae.inventoryEntries.isNotEmpty()) {
                                                    vwae.inventoryEntries.forEach { e ->
                                                        val stockItem = stockItemMap[e.stockItemId]
                                                        val itemName = stockItem?.name ?: "Item"
                                                        val unitName = stockItem?.unitId?.let { unitMap[it]?.name } ?: ""
                                                        val qtyStr = if (e.quantity % 1 == 0.0) e.quantity.toInt().toString() else e.quantity.toString()
                                                        val rateStr = "%.2f".format(e.rate)
                                                        val amtStr = "%.2f".format(e.quantity * e.rate)
                                                        append("- $itemName: $qtyStr $unitName @ ₹$rateStr = ₹$amtStr\n")
                                                    }
                                                }
                                                if (!vwae.voucher.narration.isNullOrBlank()) {
                                                    append("Narration: ${vwae.voucher.narration}")
                                                }
                                            }.trim()
                                            
                                            listOf(
                                                dateFormatter.format(vwae.voucher.date),
                                                vwae.voucher.voucherNumber,
                                                vwae.voucher.type,
                                                resolvePartyName(vwae, ledgerMap),
                                                particulars.ifEmpty { "N/A" },
                                                "%.2f".format(totalAmt)
                                            )
                                        }
                                        ExportUtils.exportReportToPdf(
                                            context, "Daybook.pdf", companyName,
                                            "Daybook", headers, floatArrayOf(1.2f, 1.2f, 1.2f, 2.0f, 4.0f, 1.5f), data
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(TerminologyManager.getTerm("Export to Excel")) },
                                    onClick = {
                                        showExportMenu = false
                                        val filteredVouchers = allVouchersWithAllEntries.filter {
                                            !it.voucher.date.before(fromDate) && !it.voucher.date.after(toDate)
                                        }
                                        val headers = listOf("Date", "Vch No", "Type", "Party", "Particulars (Items & Narration)", "Amount")
                                        val data = filteredVouchers.map { vwae ->
                                            val totalAmt = vwae.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                                            val particulars = buildString {
                                                if (vwae.inventoryEntries.isNotEmpty()) {
                                                    vwae.inventoryEntries.forEach { e ->
                                                        val stockItem = stockItemMap[e.stockItemId]
                                                        val itemName = stockItem?.name ?: "Item"
                                                        val unitName = stockItem?.unitId?.let { unitMap[it]?.name } ?: ""
                                                        val qtyStr = if (e.quantity % 1 == 0.0) e.quantity.toInt().toString() else e.quantity.toString()
                                                        val rateStr = "%.2f".format(e.rate)
                                                        val amtStr = "%.2f".format(e.quantity * e.rate)
                                                        append("- $itemName: $qtyStr $unitName @ ₹$rateStr = ₹$amtStr\n")
                                                    }
                                                }
                                                if (!vwae.voucher.narration.isNullOrBlank()) {
                                                    append("Narration: ${vwae.voucher.narration}")
                                                }
                                            }.trim()
                                            
                                            listOf(
                                                dateFormatter.format(vwae.voucher.date),
                                                vwae.voucher.voucherNumber,
                                                vwae.voucher.type,
                                                resolvePartyName(vwae, ledgerMap),
                                                particulars.ifEmpty { "N/A" },
                                                "%.2f".format(totalAmt)
                                            )
                                        }
                                        ExportUtils.exportReportToExcel(
                                            context, "Daybook.xlsx", "Daybook", headers, data
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Date range pickers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // From Date
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable {
                                    val cal = Calendar.getInstance().apply { time = fromDate }
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            val resultCal = Calendar.getInstance()
                                            resultCal.set(year, month, day, 0, 0, 0)
                                            resultCal.set(Calendar.MILLISECOND, 0)
                                            fromDate = resultCal.time
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
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
                                        text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(fromDate),
                                        maxFontSize = 13.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        // To Date
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable {
                                    val cal = Calendar.getInstance().apply { time = toDate }
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            val resultCal = Calendar.getInstance()
                                            resultCal.set(year, month, day, 23, 59, 59)
                                            resultCal.set(Calendar.MILLISECOND, 999)
                                            toDate = resultCal.time
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
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
                                        text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(toDate),
                                        maxFontSize = 13.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Table header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF4D3069))
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = TerminologyManager.getTerm("Date"),
                            modifier = Modifier.weight(1.2f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = TerminologyManager.getTerm("Party Name"),
                            modifier = Modifier.weight(2f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = TerminologyManager.getTerm("Type"),
                            modifier = Modifier.weight(1.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = TerminologyManager.getTerm("Amount"),
                            modifier = Modifier.weight(1.3f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        ) { paddingValues ->
            val filteredVouchers = remember(allVouchersWithAllEntries, fromDate, toDate) {
                allVouchersWithAllEntries.filter {
                    !it.voucher.date.before(fromDate) && !it.voucher.date.after(toDate)
                }.sortedByDescending { it.voucher.date }
            }

            if (filteredVouchers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Assessment,
                            contentDescription = null,
                            tint = Color(0xFFB8A0D0),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = TerminologyManager.getTerm("No entries found"),
                            color = Color(0xFFB8A0D0),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = TerminologyManager.getTerm("Try adjusting the date range"),
                            color = Color(0x88B8A0D0),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 4.dp),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
                ) {
                    items(filteredVouchers, key = { it.voucher.id }) { voucherWithAllEntries ->
                        DaybookCardItem(
                            voucherWithAllEntries = voucherWithAllEntries,
                            shortDateFormatter = shortDateFormatter,
                            ledgerMap = ledgerMap,
                            stockItemMap = stockItemMap,
                            unitMap = unitMap,
                            onClick = {
                                navController.navigate(Screen.VoucherView.route.replace("{voucherId}", voucherWithAllEntries.voucher.id.toString()))
                            },
                            onLongClick = {
                                selectedVoucher = voucherWithAllEntries
                                showDialog = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DaybookCardItem(
    voucherWithAllEntries: VoucherWithAllEntries,
    shortDateFormatter: SimpleDateFormat,
    ledgerMap: Map<Int, Ledger>,
    stockItemMap: Map<Int, StockItem>,
    unitMap: Map<Int, com.minhas.teller.data.entities.Unit>,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val v = voucherWithAllEntries.voucher
    val entries = voucherWithAllEntries.voucherEntries
    val totalAmount = entries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()

    // Determine icon and color based on voucher type
    data class VoucherTypeStyle(val icon: androidx.compose.ui.graphics.vector.ImageVector, val iconTint: Color, val iconBg: Color, val amountColor: Color)
    val typeStyle = when (v.type) {
        "Sales"    -> VoucherTypeStyle(Icons.AutoMirrored.Filled.TrendingUp,      Color(0xFF00E676), Color(0xFF00E676).copy(alpha = 0.2f), Color(0xFF00E676))
        "Purchase" -> VoucherTypeStyle(Icons.Default.ShoppingCart,    Color(0xFFFF5252), Color(0xFFFF5252).copy(alpha = 0.2f), Color(0xFFFF5252))
        "Payment"  -> VoucherTypeStyle(Icons.Default.CreditCard,      Color(0xFF40C4FF), Color(0xFF40C4FF).copy(alpha = 0.2f), Color(0xFF40C4FF))
        "Receipt"  -> VoucherTypeStyle(Icons.Default.Description,     Color(0xFFFFAB40), Color(0xFFFFAB40).copy(alpha = 0.2f), Color(0xFFFFAB40))
        else       -> VoucherTypeStyle(Icons.AutoMirrored.Filled.Assignment,      Color(0xFFFBBF24), Color(0xFFFBBF24).copy(alpha = 0.2f), Color(0xFFFBBF24))
    }

    // Resolve actual party name from ledgers
    val partyName = resolvePartyName(voucherWithAllEntries, ledgerMap)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2D1B4E).copy(alpha = 0.8f))
            .border(1.dp, Color(0xFF4D3069), RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 6.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date column with clock icon
            Column(modifier = Modifier.weight(1.2f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AutoShrinkText(
                        text = shortDateFormatter.format(v.date), // dd/MM/yy
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = Color(0xFFB8A0D0),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Party Name with icon
            Row(
                modifier = Modifier.weight(2f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Party icon (person or custom)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF4D3069))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFFB8A0D0),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val ledgerName = resolveLedgerName(voucherWithAllEntries, ledgerMap)
                val customerName = v.refDisplayName
                // For financed sales, show the finance company as "Financed by"
                val financedByName = if (v.type == "Sales" && !v.customField.isNullOrBlank()) {
                    entries.firstOrNull { e -> ledgerMap[e.ledgerId]?.group?.let { g ->
                        g.lowercase().contains("finance") || g == "Bank Accounts" } == true
                    }?.let { ledgerMap[it.ledgerId]?.name }
                } else null
                Column {
                    AutoShrinkText(
                        text = ledgerName,
                        maxFontSize = 13.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    if (!financedByName.isNullOrBlank()) {
                        AutoShrinkText(
                            text = TerminologyManager.getTerm("Financed by") + ": $financedByName",
                            maxFontSize = 10.sp,
                            minFontSize = 7.sp,
                            color = Color(0xFF40C4FF),
                            fontWeight = FontWeight.Normal
                        )
                    } else if (!customerName.isNullOrBlank() && customerName != ledgerName) {
                        AutoShrinkText(
                            text = customerName,
                            maxFontSize = 11.sp,
                            minFontSize = 8.sp,
                            color = Color(0xFFB8A0D0),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            // Type with Material Icon
            Column(modifier = Modifier.weight(1.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Type icon in colored rounded box
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(typeStyle.iconBg)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = typeStyle.icon,
                            contentDescription = v.type,
                            tint = typeStyle.iconTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Column {
                        AutoShrinkText(
                            text = v.type,
                            maxFontSize = 10.sp,
                            minFontSize = 7.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Multi-line Items display
                        if (voucherWithAllEntries.inventoryEntries.isNotEmpty()) {
                            voucherWithAllEntries.inventoryEntries.take(2).forEach { e ->
                                val stockItem = stockItemMap[e.stockItemId]
                                val itemName = stockItem?.name ?: "Item"
                                val unitName = stockItem?.unitId?.let { unitMap[it]?.name } ?: ""
                                val qtyStr = if (e.quantity % 1 == 0.0) e.quantity.toInt().toString() else e.quantity.toString()
                                Column(modifier = Modifier.padding(bottom = 2.dp)) {
                                    AutoShrinkText(
                                        text = itemName,
                                        maxFontSize = 9.sp,
                                        minFontSize = 7.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.Medium,
                                        style = androidx.compose.ui.text.TextStyle(lineHeight = 11.sp)
                                    )
                                    AutoShrinkText(
                                        text = "x $qtyStr $unitName".trim(),
                                        maxFontSize = 9.sp,
                                        minFontSize = 7.sp,
                                        color = Color(0xFFB8A0D0),
                                        style = androidx.compose.ui.text.TextStyle(lineHeight = 11.sp)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = v.narration ?: v.voucherNumber,
                                fontSize = 9.sp,
                                color = Color(0xFFB8A0D0),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 10.sp
                            )
                        }
                    }
                }
            }

            // Amount
            Text(
                text = "₹${String.format("%,.2f", totalAmount)}",
                modifier = Modifier.weight(1.3f),
                fontSize = 12.sp,
                color = typeStyle.amountColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 2
            )
        }
    }
}

private fun resolvePartyName(voucherWithAllEntries: VoucherWithAllEntries, ledgerMap: Map<Int, Ledger>): String {
    val v = voucherWithAllEntries.voucher
    val refDisplayName = v.refDisplayName
    if (!refDisplayName.isNullOrBlank()) return refDisplayName

    val entries = voucherWithAllEntries.voucherEntries
    if (entries.isEmpty()) return v.narration?.take(20) ?: TerminologyManager.getTerm("Unknown")

    return when (v.type) {
        "Sales" -> {
            val debtorEntry = entries.firstOrNull { ledgerMap[it.ledgerId]?.group == "Sundry Debtors" }
            if (debtorEntry != null) {
                ledgerMap[debtorEntry.ledgerId]?.name ?: TerminologyManager.getTerm("Sundry Debtor")
            } else {
                val debitedEntry = entries.firstOrNull { it.isDebit }
                ledgerMap[debitedEntry?.ledgerId]?.name ?: TerminologyManager.getTerm("Walk-in Customer")
            }
        }
        "Purchase" -> {
            val creditorEntry = entries.firstOrNull { ledgerMap[it.ledgerId]?.group == "Sundry Creditors" }
            if (creditorEntry != null) {
                ledgerMap[creditorEntry.ledgerId]?.name ?: TerminologyManager.getTerm("Sundry Creditor")
            } else {
                val creditedEntry = entries.firstOrNull { !it.isDebit }
                ledgerMap[creditedEntry?.ledgerId]?.name ?: TerminologyManager.getTerm("Supplier")
            }
        }
        "Receipt" -> {
            val partyEntry = entries.firstOrNull { !it.isDebit && ledgerMap[it.ledgerId]?.group != "Cash-in-hand" && ledgerMap[it.ledgerId]?.group != "Bank Accounts" }
            if (partyEntry != null) {
                ledgerMap[partyEntry.ledgerId]?.name ?: TerminologyManager.getTerm("Party")
            } else {
                val creditedEntry = entries.firstOrNull { !it.isDebit }
                ledgerMap[creditedEntry?.ledgerId]?.name ?: TerminologyManager.getTerm("Received From")
            }
        }
        "Payment" -> {
            val partyEntry = entries.firstOrNull { it.isDebit && ledgerMap[it.ledgerId]?.group != "Cash-in-hand" && ledgerMap[it.ledgerId]?.group != "Bank Accounts" }
            if (partyEntry != null) {
                ledgerMap[partyEntry.ledgerId]?.name ?: TerminologyManager.getTerm("Party")
            } else {
                val debitedEntry = entries.firstOrNull { it.isDebit }
                ledgerMap[debitedEntry?.ledgerId]?.name ?: TerminologyManager.getTerm("Paid To")
            }
        }
        else -> {
            val partyEntry = entries.firstOrNull { ledgerMap[it.ledgerId]?.group != "Cash-in-hand" && ledgerMap[it.ledgerId]?.group != "Bank Accounts" }
            if (partyEntry != null) {
                ledgerMap[partyEntry.ledgerId]?.name ?: TerminologyManager.getTerm("Party")
            } else {
                ledgerMap[entries.firstOrNull()?.ledgerId]?.name ?: (v.narration?.take(20) ?: v.type)
            }
        }
    }
}

private fun resolveLedgerName(voucherWithAllEntries: VoucherWithAllEntries, ledgerMap: Map<Int, Ledger>): String {
    val v = voucherWithAllEntries.voucher
    val entries = voucherWithAllEntries.voucherEntries
    if (entries.isEmpty()) return v.narration?.take(20) ?: TerminologyManager.getTerm("Unknown")

    return when (v.type) {
        "Sales" -> {
            // For financed sales, show the end customer name (stored in customField)
            if (!v.customField.isNullOrBlank()) return v.customField!!
            val debtorEntry = entries.firstOrNull { ledgerMap[it.ledgerId]?.group == "Sundry Debtors" }
            if (debtorEntry != null) {
                ledgerMap[debtorEntry.ledgerId]?.name ?: TerminologyManager.getTerm("Sundry Debtor")
            } else {
                val debitedEntry = entries.firstOrNull { it.isDebit }
                ledgerMap[debitedEntry?.ledgerId]?.name ?: TerminologyManager.getTerm("Walk-in Customer")
            }
        }
        "Purchase" -> {
            val creditorEntry = entries.firstOrNull { ledgerMap[it.ledgerId]?.group == "Sundry Creditors" }
            if (creditorEntry != null) {
                ledgerMap[creditorEntry.ledgerId]?.name ?: TerminologyManager.getTerm("Sundry Creditor")
            } else {
                val creditedEntry = entries.firstOrNull { !it.isDebit }
                ledgerMap[creditedEntry?.ledgerId]?.name ?: TerminologyManager.getTerm("Supplier")
            }
        }
        "Receipt" -> {
            val partyEntry = entries.firstOrNull { !it.isDebit && ledgerMap[it.ledgerId]?.group != "Cash-in-hand" && ledgerMap[it.ledgerId]?.group != "Bank Accounts" }
            if (partyEntry != null) {
                ledgerMap[partyEntry.ledgerId]?.name ?: TerminologyManager.getTerm("Party")
            } else {
                val creditedEntry = entries.firstOrNull { !it.isDebit }
                ledgerMap[creditedEntry?.ledgerId]?.name ?: TerminologyManager.getTerm("Received From")
            }
        }
        "Payment" -> {
            val partyEntry = entries.firstOrNull { it.isDebit && ledgerMap[it.ledgerId]?.group != "Cash-in-hand" && ledgerMap[it.ledgerId]?.group != "Bank Accounts" }
            if (partyEntry != null) {
                ledgerMap[partyEntry.ledgerId]?.name ?: TerminologyManager.getTerm("Party")
            } else {
                val debitedEntry = entries.firstOrNull { it.isDebit }
                ledgerMap[debitedEntry?.ledgerId]?.name ?: TerminologyManager.getTerm("Paid To")
            }
        }
        else -> {
            val partyEntry = entries.firstOrNull { ledgerMap[it.ledgerId]?.group != "Cash-in-hand" && ledgerMap[it.ledgerId]?.group != "Bank Accounts" }
            if (partyEntry != null) {
                ledgerMap[partyEntry.ledgerId]?.name ?: TerminologyManager.getTerm("Party")
            } else {
                ledgerMap[entries.firstOrNull()?.ledgerId]?.name ?: (v.narration?.take(20) ?: v.type)
            }
        }
    }
}