package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.data.repositories.UnitRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.ui.viewmodels.StockItemViewModel
import com.minhas.teller.ui.viewmodels.StockItemViewModelFactory
import com.minhas.teller.ui.viewmodels.UnitViewModel
import com.minhas.teller.ui.viewmodels.UnitViewModelFactory
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.ExportUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseRegisterScreen(navController: NavHostController) {
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
    val unitRepository = UnitRepository(database.unitDao())
    val unitViewModel: UnitViewModel = viewModel(factory = UnitViewModelFactory(unitRepository))

    val allVouchers by voucherViewModel.allVouchersWithAllEntries.collectAsState()
    val allLedgers by voucherViewModel.allLedgers.collectAsState()
    val allStockItems by stockItemViewModel.allStockItems.collectAsState()
    val allUnits by unitViewModel.allUnits.collectAsState()

    var fromDate by remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        mutableStateOf(cal.time)
    }
    var toDate by remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        mutableStateOf(cal.time)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                voucherViewModel.getAllVouchersWithAllEntriesBetweenDates(fromDate, toDate)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val fromDatePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, day ->
            val cal = Calendar.getInstance()
            cal.set(year, month, day, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
            fromDate = cal.time
            voucherViewModel.getAllVouchersWithAllEntriesBetweenDates(fromDate, toDate)
        },
        Calendar.getInstance().get(Calendar.YEAR),
        Calendar.getInstance().get(Calendar.MONTH),
        Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    )
    val toDatePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, day ->
            val cal = Calendar.getInstance()
            cal.set(year, month, day, 23, 59, 59); cal.set(Calendar.MILLISECOND, 999)
            toDate = cal.time
            voucherViewModel.getAllVouchersWithAllEntriesBetweenDates(fromDate, toDate)
        },
        Calendar.getInstance().get(Calendar.YEAR),
        Calendar.getInstance().get(Calendar.MONTH),
        Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    )

    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val currentCompany by produceState<Company?>(initialValue = null, currentCompanyId) {
        currentCompanyId?.let { id -> companyViewModel.getCompany(id).collect { value = it } }
    }
    val companyName = currentCompany?.name ?: "Teller App"

    val purchaseVouchers = remember(allVouchers) { allVouchers.filter { it.voucher.type == "Purchase" } }
    val stockItemMap = remember(allStockItems) { allStockItems.associateBy { it.id } }
    val unitMap = remember(allUnits) { allUnits.associateBy { it.id } }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }
    val fullDateFormatter = remember { SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()) }

    // Statistics Calculation
    val totalPurchaseAmount = remember(purchaseVouchers) {
        purchaseVouchers.sumOf { v -> v.voucherEntries.filter { !it.isDebit }.sumOf { it.amount } }.paiseToRupees()
    }
    val totalVouchers = purchaseVouchers.size
    val avgPerDay = remember(totalPurchaseAmount, fromDate, toDate) {
        val diff = toDate.time - fromDate.time
        val days = (diff / (1000 * 60 * 60 * 24)).coerceAtLeast(1)
        totalPurchaseAmount / days.toDouble()
    }

    var showExportMenu by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var selectedVoucher by remember { mutableStateOf<VoucherWithAllEntries?>(null) }

    if (showDialog && selectedVoucher != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(TerminologyManager.getTerm("Voucher Options")) },
            text = { Text(TerminologyManager.getTerm("What would you like to do with this voucher?")) },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    navController.navigate(Screen.AddEditVoucher.route + "?voucherId=${selectedVoucher!!.voucher.id}")
                }) { Text(TerminologyManager.getTerm("Edit")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    navController.navigate(Screen.VoucherView.route.replace("{voucherId}", selectedVoucher!!.voucher.id.toString()))
                }) { Text(TerminologyManager.getTerm("View")) }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A0F0F), // Very dark red
                        Color(0xFF2D0A0A)  // Dark red
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(modifier = Modifier.fillMaxWidth().background(Color(0x33000000)).statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        AutoShrinkText(
                            text = "PURCHASE REGISTER HUB",
                            maxFontSize = 17.sp,
                            minFontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            style = TextStyle(letterSpacing = 1.sp)
                        )
                        IconButton(onClick = { navController.navigate(Screen.Dashboard.route) }) {
                            Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White)
                        }
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Export", tint = Color.White)
                        }
                        DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(TerminologyManager.getTerm("Export to PDF (by Supplier)")) },
                                onClick = {
                                    showExportMenu = false
                                    val headers = listOf("Date", "Vch No", "Supplier", "Amount")
                                    val data = purchaseVouchers.map { v ->
                                        val amt = v.voucherEntries.filter { !it.isDebit }.sumOf { it.amount }.paiseToRupees()
                                        listOf(dateFormatter.format(v.voucher.date), v.voucher.voucherNumber, getPurchaseSupplierName(v, allLedgers), "%.2f".format(amt))
                                    }
                                    ExportUtils.exportReportToPdf(context, "PurchaseRegister.pdf", companyName, "Purchase Register", headers, floatArrayOf(1.5f, 1.5f, 3f, 2f), data)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(TerminologyManager.getTerm("Export to Excel (by Supplier)")) },
                                onClick = {
                                    showExportMenu = false
                                    val headers = listOf("Date", "Vch No", "Supplier", "Amount")
                                    val data = purchaseVouchers.map { v ->
                                        val amt = v.voucherEntries.filter { !it.isDebit }.sumOf { it.amount }.paiseToRupees()
                                        listOf(dateFormatter.format(v.voucher.date), v.voucher.voucherNumber, getPurchaseSupplierName(v, allLedgers), "%.2f".format(amt))
                                    }
                                    ExportUtils.exportReportToExcel(context, "PurchaseRegister.xlsx", "Purchase Register", headers, data)
                                }
                            )
                        }
                    }

                    // Date Dropdowns
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                AutoShrinkText(SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(fromDate), color = Color.White, maxFontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
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
                                AutoShrinkText(SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(toDate), color = Color.White, maxFontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
            ) {
                // Hero Total Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Brush.horizontalGradient(listOf(
                                    Color(0x3340C4FF),
                                    Color(0x3369F0AE),
                                    Color(0x33B388FF),
                                    Color(0x33FFB74D)
                                )))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(TerminologyManager.getTerm("TOTAL PURCHASE"), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.height(10.dp))
                            AutoShrinkText(
                                text = "₹ ${String.format("%,.2f", totalPurchaseAmount)}",
                                color = Color(0xFFFF8A8A),
                                maxFontSize = 38.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(Modifier.height(12.dp))
                            AutoShrinkText(
                                text = "VOUCHERS: $totalVouchers | AVG/DAY: ₹ ${String.format("%,.2f", avgPerDay)}",
                                color = Color.White.copy(alpha = 0.7f),
                                maxFontSize = 12.sp,
                                minFontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Table Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(TerminologyManager.getTerm("Date / Vch"), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                        Text(TerminologyManager.getTerm("Supplier"), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.6f))
                        Text(TerminologyManager.getTerm("Items"), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                        Text(TerminologyManager.getTerm("Amount"), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2.0f), textAlign = TextAlign.End)
                    }
                }

                if (purchaseVouchers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x11FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(TerminologyManager.getTerm("No purchases in this period"), fontSize = 14.sp, color = Color.White.copy(alpha = 0.4f))
                        }
                    }
                } else {
                    items(purchaseVouchers) { v ->
                        val totalAmount = v.voucherEntries.filter { !it.isDebit }.sumOf { it.amount }.paiseToRupees()
                        val supplierName = getPurchaseSupplierName(v, allLedgers)
                        val inventoryEntries = v.inventoryEntries

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedVoucher = v; showDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x33582A2A)), // Reddish frosted tint
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x22FFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .fillMaxHeight()
                                        .background(Color(0x2240C4FF))
                                        .padding(horizontal = 6.dp, vertical = 12.dp)
                                ) {
                                    AutoShrinkText(
                                        dateFormatter.format(v.voucher.date),
                                        color = Color.White,
                                        maxFontSize = 14.sp,
                                        minFontSize = 7.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    AutoShrinkText(
                                        v.voucher.voucherNumber,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxFontSize = 12.sp,
                                        minFontSize = 7.sp
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1.6f)
                                        .fillMaxHeight()
                                        .background(Color(0x2269F0AE))
                                        .padding(horizontal = 6.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = supplierName,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        softWrap = true,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Items Column
                                Column(
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .fillMaxHeight()
                                        .background(Color(0x22B388FF))
                                        .padding(horizontal = 6.dp, vertical = 12.dp)
                                ) {
                                    if (inventoryEntries.isEmpty()) {
                                        Text(TerminologyManager.getTerm("No Items"), color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp)
                                    } else {
                                        inventoryEntries.forEach { entry ->
                                            val stockItem = stockItemMap[entry.stockItemId]
                                            val name = stockItem?.name ?: "Item"
                                            val unitName = stockItem?.unitId?.let { unitMap[it]?.name } ?: ""
                                            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                                AutoShrinkText(
                                                    text = name,
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    maxFontSize = 13.sp,
                                                    minFontSize = 7.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                AutoShrinkText(
                                                    text = "x ${entry.quantity} $unitName",
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    maxFontSize = 11.sp,
                                                    minFontSize = 7.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                 Column(
                                     modifier = Modifier
                                         .weight(2.0f)
                                         .fillMaxHeight()
                                         .background(Color(0x22FFB74D))
                                         .padding(horizontal = 6.dp, vertical = 12.dp),
                                     horizontalAlignment = Alignment.End
                                 ) {
                                     AutoShrinkText(
                                         text = "₹ ${String.format("%,.2f", totalAmount)}",
                                         modifier = Modifier.fillMaxWidth(),
                                         maxFontSize = 16.sp,
                                         minFontSize = 6.sp,
                                         stepSp = 0.5f,
                                         fontWeight = FontWeight.Black,
                                         color = Color(0xFFFF8A8A),
                                         textAlign = TextAlign.End
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

private fun getPurchaseSupplierName(voucherWithAllEntries: VoucherWithAllEntries, allLedgers: List<Ledger>): String {
    // 1. Look for ledger in Sundry Creditors or similar groups
    for (entry in voucherWithAllEntries.voucherEntries) {
        val ledger = allLedgers.find { it.id == entry.ledgerId } ?: continue
        val group = ledger.group.lowercase(Locale.ROOT)
        if (group.contains("creditor") || group.contains("supplier") || group.contains("pay")) {
            return ledger.name
        }
    }
    
    // 2. Fallback: Find the credited entry that is NOT a purchase account
    val creditedEntry = voucherWithAllEntries.voucherEntries.find { !it.isDebit }
    if (creditedEntry != null) {
        val ledger = allLedgers.find { it.id == creditedEntry.ledgerId }
        if (ledger != null && !ledger.group.lowercase(Locale.ROOT).contains("purchase")) {
            return ledger.name
        }
    }
    
    // 3. Last fallback: Check all entries for a party name
    for (entry in voucherWithAllEntries.voucherEntries) {
        val ledger = allLedgers.find { it.id == entry.ledgerId } ?: continue
        if (!ledger.group.lowercase(Locale.ROOT).contains("purchase") && 
            !ledger.group.lowercase(Locale.ROOT).contains("tax") &&
            !ledger.group.lowercase(Locale.ROOT).contains("expense")) {
            return ledger.name
        }
    }
    
    return "N/A"
}
