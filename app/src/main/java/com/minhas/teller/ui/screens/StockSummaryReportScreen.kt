package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.ui.components.rememberInBackground
import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.data.repositories.UnitRepository
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.ui.viewmodels.StockItemViewModel
import com.minhas.teller.ui.viewmodels.StockItemViewModelFactory
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import com.minhas.teller.ui.viewmodels.UnitViewModel
import com.minhas.teller.ui.viewmodels.UnitViewModelFactory
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.ExportUtils
import java.text.SimpleDateFormat
import java.util.*
import com.minhas.teller.navigation.Screen

private val itemTintPalette = listOf(
    Color(0xFFFFF3CD), Color(0xFFFFD9D2), Color(0xFFD6E9FF), Color(0xFFD3F3DA),
    Color(0xFFEBD9FF), Color(0xFFFFE3C2), Color(0xFFCFF3EC), Color(0xFFFFD9EC)
)

private fun itemTint(name: String): Color =
    itemTintPalette[Math.abs(name.hashCode()) % itemTintPalette.size]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockSummaryReportScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)

    val stockItemRepository = StockItemRepository(database.stockItemDao())
    val stockItemViewModel: StockItemViewModel = viewModel(factory = StockItemViewModelFactory(stockItemRepository))
    val stockItems by stockItemViewModel.allStockItems.collectAsState()

    val voucherRepository = VoucherRepository(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.billAllocationDao(), database.auditLogDao(), database.ledgerDao(), db = database)
    val voucherViewModel: VoucherViewModel = viewModel(factory = VoucherViewModelFactory(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.ledgerDao(), database.billAllocationDao(), database.auditLogDao(), db = database))
    val allVouchersWithAllEntries by voucherViewModel.allVouchersWithAllEntries.collectAsState()

    val unitRepository = UnitRepository(database.unitDao())
    val unitViewModel: UnitViewModel = viewModel(factory = UnitViewModelFactory(unitRepository))
    val allUnits by unitViewModel.allUnits.collectAsState()

    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()

    val defaultCalendar = Calendar.getInstance()
    defaultCalendar.set(Calendar.DAY_OF_MONTH, 1)
    var fromDate by remember { mutableStateOf(defaultCalendar.time) }
    var toDate by remember { mutableStateOf(Date()) }

    val dateFormatter = SimpleDateFormat("dd MMM yy", Locale.getDefault())

    val fromDatePickerDialog = DatePickerDialog(context, { _, y, m, d ->
        val cal = Calendar.getInstance()
        cal.set(y, m, d, 0, 0, 0)
        fromDate = cal.time
    }, Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH))

    val toDatePickerDialog = DatePickerDialog(context, { _, y, m, d ->
        val cal = Calendar.getInstance()
        cal.set(y, m, d, 23, 59, 59)
        toDate = cal.time
    }, Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH))

    LaunchedEffect(toDate) {
        // Load all movements from the beginning up to `toDate` so "closing stock" is the true
        // cumulative balance (opening + all purchases − all sales), not just the selected month.
        voucherViewModel.getAllVouchersWithAllEntriesBetweenDates(Date(0L), toDate)
    }

    // Off the UI thread: replays every stock movement since inception.
    val stockSummary = rememberInBackground(stockItems, allVouchersWithAllEntries, allUnits, initial = emptyList<StockSummaryItem>()) {
        calculateStockSummary(stockItems, allVouchersWithAllEntries, allUnits)
    }
    val totalValue = stockSummary.sumOf { it.value }
    var searchQuery by remember { mutableStateOf("") }
    var showLowStockOnly by remember { mutableStateOf(false) }

    val filteredSummary = remember(stockSummary, searchQuery, showLowStockOnly) {
        stockSummary.filter { item ->
            item.stockItem.name.contains(searchQuery, ignoreCase = true) &&
                (!showLowStockOnly || item.quantity <= 3)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF060D40), Color(0xFF0D1240))))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    AutoShrinkText("Stock Summary", maxFontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))

                    IconButton(onClick = { navController.navigate(Screen.MonthlyInventory.route) }) {
                        Icon(Icons.Default.CalendarMonth, "Monthly Inventory", tint = Color.White)
                    }

                    IconButton(onClick = {
                        val exportData = filteredSummary.map {
                            listOf(it.stockItem.name, "%.2f %s".format(it.quantity, it.unit), "%.2f".format(it.value / if(it.quantity!=0.0) it.quantity else 1.0), "%.2f".format(it.value)) 
                        }.toMutableList().apply {
                            add(listOf("TOTAL", "", "", "%.2f".format(totalValue)))
                        }
                        ExportUtils.exportStockSummaryToPdf(context, exportData)
                    }) {
                        Icon(Icons.Default.PictureAsPdf, "Export PDF", tint = Color.White)
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 6.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // 1. Control Row
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Search Bar
                        Row(
                            modifier = Modifier.weight(1f).height(48.dp).background(Color(0xFF1A237E).copy(alpha = 0.5f), RoundedCornerShape(10.dp)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                                cursorBrush = SolidColor(Color.White),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) Text(TerminologyManager.getTerm("Search Items"), color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp)
                                    innerTextField()
                                }
                            )
                        }
                        
                        IconButton(onClick = { /* Filter */ }, modifier = Modifier.size(48.dp).background(Color(0xFF1A237E).copy(alpha = 0.5f), RoundedCornerShape(10.dp)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))) {
                            Icon(Icons.Default.FilterList, null, tint = Color.White)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                // 2. Date Pills + Low Stock toggle
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.9f)).clickable { fromDatePickerDialog.show() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            AutoShrinkText("From: ${dateFormatter.format(fromDate)}", color = Color.Black, maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.9f)).clickable { toDatePickerDialog.show() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            AutoShrinkText("To: ${dateFormatter.format(toDate)}", color = Color.Black, maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        val isLow = showLowStockOnly
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isLow) Color(0xFFFFD54F).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
                                .border(1.dp, if (isLow) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                .clickable { showLowStockOnly = !showLowStockOnly }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Low",
                                color = if (isLow) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }

                // 3. Hero Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Brush.horizontalGradient(listOf(Color(0xFF4CAF50).copy(alpha = 0.22f), Color(0xFF2196F3).copy(alpha = 0.22f))))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(TerminologyManager.getTerm("TOTAL INVENTORY VALUE"), color = Color(0xFF81C784), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.height(10.dp))
                            AutoShrinkText(
                                text = "₹ ${String.format("%,.2f", totalValue)}",
                                color = Color.White,
                                maxFontSize = 36.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF81C784).copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                    AutoShrinkText("${filteredSummary.size} Items", color = Color(0xFF81C784), maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF40C4FF).copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                    AutoShrinkText("${filteredSummary.count { it.quantity > 0 }} In Stock", color = Color(0xFF40C4FF), maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }

                // 4. Header Row
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(TerminologyManager.getTerm("Stock Item"), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.8f), fontSize = 13.sp)
                        Text(TerminologyManager.getTerm("Quantity"), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center, fontSize = 13.sp)
                        Text(TerminologyManager.getTerm("Value"), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), textAlign = TextAlign.End, fontSize = 13.sp)
                    }
                }

                // 5. Items List
                items(filteredSummary) { item ->
                    val stockStatusText = when {
                        item.quantity <= 0 -> "OUT OF STOCK"
                        item.quantity <= 3 -> "LOW STOCK"
                        else -> "IN STOCK"
                    }
                    val stockStatusColor = when {
                        item.quantity <= 0 -> Color(0xFFFF5252) // Red
                        item.quantity <= 3 -> Color(0xFFFFD54F) // Yellow
                        else -> Color(0xFF00E676) // Green
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(0.5.dp, Color(0x11FFFFFF), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = itemTint(item.stockItem.name).copy(alpha = 0.22f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    navController.navigate(Screen.AddEditStockItem.route.replace("{stockItemId}", item.stockItem.id.toString()))
                                }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.8f)) {
                                    AutoShrinkText(item.stockItem.name, color = Color.White, fontWeight = FontWeight.Bold, maxFontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    AutoShrinkText("Avg Rate: ₹ ${String.format("%.2f", item.value / if(item.quantity!=0.0) item.quantity else 1.0)}", color = Color.Gray, maxFontSize = 11.sp, minFontSize = 8.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = stockStatusColor.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, stockStatusColor.copy(alpha = 0.3f))
                                    ) {
                                        AutoShrinkText(
                                            text = stockStatusText,
                                            color = stockStatusColor.copy(alpha = 0.8f),
                                            maxFontSize = 7.5.sp,
                                            minFontSize = 5.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                                
                                Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x1AFFFFFF)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                        AutoShrinkText("${String.format("%.2f", item.quantity)} ${item.unit}", color = Color.White, maxFontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }

                                AutoShrinkText(
                                    text = "₹ ${String.format("%,.0f", item.value)}",
                                    color = Color(0xFF81C784),
                                    maxFontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.weight(1.5f),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // 6. Pinned Footer
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                color = Color(0xFF121212).copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(TerminologyManager.getTerm("TOTAL STOCK VALUE"), fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
                    AutoShrinkText("₹ ${String.format("%,.2f", totalValue)}", fontWeight = FontWeight.Black, color = Color(0xFF81C784), textAlign = TextAlign.End, maxFontSize = 20.sp)
                }
            }
        }
    }
}

data class StockSummaryItem(
    val stockItem: StockItem,
    val quantity: Double,
    val value: Double,
    val unit: String
)

private fun calculateStockSummary(
    stockItems: List<StockItem>,
    vouchersWithAllEntries: List<VoucherWithAllEntries>,
    units: List<com.minhas.teller.data.entities.Unit>
): List<StockSummaryItem> {
    val valuations = com.minhas.teller.data.accounting.AccountingEngine.calculateStockValuation(stockItems, vouchersWithAllEntries)
    val unitMap = units.associateBy { it.id }
    val itemMap = stockItems.associateBy { it.id }

    return valuations.map { v ->
        val stockItem = itemMap[v.stockItemId] ?: StockItem(id = v.stockItemId, name = v.name, unitId = 0)
        val unitName = unitMap[stockItem.unitId]?.name ?: ""
        StockSummaryItem(stockItem, v.closingQuantity, v.closingValue, unitName)
    }
}
