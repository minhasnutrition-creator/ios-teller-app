package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.accounting.ProfitCalculator
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.utils.LicenseUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class ItemProfitRow(
    val stockItemId: Int,
    val itemName: String,
    val qtySold: Double,
    val avgCost: Double,
    val avgSalePrice: Double,
    val marginPercent: Double,
    val totalProfit: Double,
    /** False when the item was never bought and has no opening rate, so its cost is unknown. */
    val costKnown: Boolean = true
)

enum class ProfitSortOption(val label: String) {
    PROFIT_HIGH("Profit ↓"),
    PROFIT_LOW("Loss ↓"),
    MARGIN_HIGH("Margin % ↓"),
    QTY_HIGH("Qty Sold ↓"),
    NAME_AZ("Name A-Z")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemProfitabilityScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val database = remember { AppDatabase.getDatabase(context) }

    val cal = Calendar.getInstance()
    val fyStartYear = if (cal.get(Calendar.MONTH) >= 3) cal.get(Calendar.YEAR) else cal.get(Calendar.YEAR) - 1
    val fyStart = Calendar.getInstance().apply { set(fyStartYear, 3, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }

    var fromDate by remember { mutableStateOf(fyStart.time) }
    var toDate by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.time)
    }
    val dateFormatterShort = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }

    val fromDatePicker = remember {
        DatePickerDialog(context, { _, y, m, d ->
            fromDate = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.time
        }, fyStart.get(Calendar.YEAR), fyStart.get(Calendar.MONTH), fyStart.get(Calendar.DAY_OF_MONTH))
    }
    val toDatePicker = remember {
        DatePickerDialog(context, { _, y, m, d ->
            toDate = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59); set(Calendar.MILLISECOND, 999) }.time
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
    }

    var allProfitRows by remember { mutableStateOf<List<ItemProfitRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedItemId by remember { mutableStateOf<Int?>(null) }
    var selectedSort by remember { mutableStateOf(ProfitSortOption.PROFIT_HIGH) }
    var filterExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var appStatus by remember { mutableStateOf<LicenseUtils.AppStatus?>(null) }

    LaunchedEffect(Unit) {
        val (status, _) = LicenseUtils.getAppStatus(context)
        appStatus = status
    }

    var anyGst by remember { mutableStateOf(false) }

    LaunchedEffect(fromDate, toDate, currentCompanyId) {
        val cid = currentCompanyId ?: return@LaunchedEffect
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                // Profit per item = sale value WITHOUT GST − what those goods cost WITHOUT GST, where cost
                // is the weighted-average purchase price of the stock on the day of each sale (opening
                // stock + purchases so far), net of sales returns. See ProfitCalculator.
                //
                // Before this, sale and purchase amounts (stored in paise) were mixed with the item's
                // rate in rupees, the cost was an average of ALL purchases (even ones made after the
                // sale), returns were ignored and GST was counted as profit.
                val allVouchers = database.voucherDao().getAllVouchersWithAllEntries(cid)
                // Include deleted items: their old sales still happened in this period.
                val stockItems = database.stockItemDao().getAllStockItemsForSync(cid)
                val costLedger = ProfitCalculator.buildCostLedger(stockItems, allVouchers)
                val items = ProfitCalculator.itemProfits(costLedger, ProfitCalculator.inRange(fromDate, toDate))

                val rows = items.map { p ->
                    ItemProfitRow(
                        stockItemId = p.itemKey,
                        itemName = p.name,
                        qtySold = p.quantity,
                        avgCost = if (p.costKnown) p.avgCostRate else 0.0,
                        avgSalePrice = p.avgSaleRate,
                        marginPercent = if (p.costKnown) p.marginPercent else 0.0,
                        totalProfit = if (p.costKnown) p.profitPaise / 100.0 else 0.0,
                        costKnown = p.costKnown
                    )
                }

                withContext(Dispatchers.Main) {
                    allProfitRows = rows
                    anyGst = items.any { it.hadTax }
                    isLoading = false
                }
            } catch (e: Exception) {
                android.util.Log.e("ItemProfitability", "Failed to calculate item profit", e)
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    val filteredRows = remember(allProfitRows, selectedItemId, selectedSort) {
        var list = if (selectedItemId != null) allProfitRows.filter { it.stockItemId == selectedItemId } else allProfitRows
        when (selectedSort) {
            ProfitSortOption.PROFIT_HIGH -> list.sortedByDescending { it.totalProfit }
            ProfitSortOption.PROFIT_LOW  -> list.sortedBy { it.totalProfit }
            ProfitSortOption.MARGIN_HIGH -> list.sortedByDescending { it.marginPercent }
            ProfitSortOption.QTY_HIGH    -> list.sortedByDescending { it.qtySold }
            ProfitSortOption.NAME_AZ     -> list.sortedBy { it.itemName }
        }
    }

    // Items with no purchase price can't have a profit worked out — they are left out of the totals.
    val totalProfit = filteredRows.filter { it.costKnown }.sumOf { it.totalProfit }
    val profitableCount = filteredRows.count { it.costKnown && it.totalProfit > 0 }
    val lossCount = filteredRows.count { it.costKnown && it.totalProfit < 0 }
    val noCostCount = filteredRows.count { !it.costKnown }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText("Item Profitability", maxFontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color(0xFF0D0D1A) // Match Sales Hub background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
            // ROW 1: DATES AND FILTER ITEM (COMBINED)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Reduced Width Dates
                Box(modifier = Modifier.weight(0.7f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1E1E3F)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp)).clickable { fromDatePicker.show() }.padding(10.dp), contentAlignment = Alignment.Center) {
                    Text(dateFormatterShort.format(fromDate), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text("→", color = Color.Gray, fontSize = 12.sp)
                Box(modifier = Modifier.weight(0.7f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1E1E3F)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp)).clickable { toDatePicker.show() }.padding(10.dp), contentAlignment = Alignment.Center) {
                    Text(dateFormatterShort.format(toDate), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }

                // Filter Item Dropdown (Integrated)
                Box(modifier = Modifier.weight(1.2f)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { filterExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E1E3F),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(TerminologyManager.getTerm("Filter Item"), fontSize = 8.sp, color = Color.Gray)
                                Text(if (selectedItemId == null) "All Items" else allProfitRows.find { it.stockItemId == selectedItemId }?.itemName ?: "All Items", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.ArrowDropDown, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                        DropdownMenuItem(text = { Text(TerminologyManager.getTerm("All Items")) }, onClick = { selectedItemId = null; filterExpanded = false })
                        allProfitRows.sortedBy { it.itemName }.forEach { row ->
                            DropdownMenuItem(text = { Text(row.itemName) }, onClick = { selectedItemId = row.stockItemId; filterExpanded = false })
                        }
                    }
                }
            }

            // ROW 2: SUMMARY AND SORT/EXPORT
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(TerminologyManager.getTerm("Total Profit"), color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("₹ ${String.format("%,.2f", totalProfit)}", color = if (totalProfit >= 0) Color(0xFF00E676) else Color(0xFFFF5252), fontSize = 30.sp, fontWeight = FontWeight.Black)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Profitable Count Box
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF00E676).copy(alpha = 0.15f)).border(1.dp, Color(0x3300E676), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, null, tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(profitableCount.toString(), color = Color(0xFF00E676), fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }
                    // Loss Count Box
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF5252).copy(alpha = 0.15f)).border(1.dp, Color(0x33FF5252), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(lossCount.toString(), color = Color(0xFFFF5252), fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }

                    // Sort Icon/Button
                    Box {
                        IconButton(onClick = { sortExpanded = true }, modifier = Modifier.size(40.dp).background(Color(0x1AFFFFFF), CircleShape)) {
                            Icon(Icons.AutoMirrored.Filled.Sort, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                            ProfitSortOption.entries.forEach { opt ->
                                DropdownMenuItem(text = { Text(opt.label) }, onClick = { selectedSort = opt; sortExpanded = false })
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // TABLE HEADER
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E1E3F),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            ) {
                Row(modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(TerminologyManager.getTerm("Item"), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.8f))
                    Text(TerminologyManager.getTerm("Sold"), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
                    Text(TerminologyManager.getTerm("Cost"), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
                    Text(TerminologyManager.getTerm("Sale"), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
                    Text(TerminologyManager.getTerm("Margin"), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f), textAlign = TextAlign.Center)
                    Text(TerminologyManager.getTerm("Profit"), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f), textAlign = TextAlign.End)
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredRows) { row ->
                        val isProfit = row.totalProfit >= 0
                        val rowColor = if (!row.costKnown) Color(0xFFFFC107) else if (isProfit) Color(0xFF00E676) else Color(0xFFFF5252)
                        
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(row.itemName, modifier = Modifier.weight(1.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (row.qtySold % 1.0 == 0.0) "%.0f".format(row.qtySold) else "%.2f".format(row.qtySold), modifier = Modifier.weight(0.7f), fontSize = 12.sp, textAlign = TextAlign.Center, color = Color.White)
                                Text(if (row.costKnown) "%.0f".format(row.avgCost) else "—", modifier = Modifier.weight(0.9f), fontSize = 12.sp, textAlign = TextAlign.Center, color = Color.Gray)
                                Text("%.0f".format(row.avgSalePrice), modifier = Modifier.weight(0.9f), fontSize = 12.sp, textAlign = TextAlign.Center, color = Color.White)
                                Text(if (row.costKnown) "%.1f%%".format(row.marginPercent) else TerminologyManager.getTerm("No cost"), modifier = Modifier.weight(1.1f), fontSize = 12.sp, textAlign = TextAlign.Center, color = rowColor, fontWeight = FontWeight.Medium)
                                Text(if (row.costKnown) "₹%.0f".format(row.totalProfit) else "—", modifier = Modifier.weight(1.4f), fontSize = 14.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, color = rowColor)
                            }
                            Spacer(Modifier.height(8.dp))
                            // Colored Indicator Bar
                            Box(modifier = Modifier.width(110.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(rowColor))
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    }
                }

                // TOTAL FOOTER BAR
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    color = Color(0xFF121212),
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("TOTAL (${filteredRows.size} items)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            Text("₹${String.format("%,.0f", totalProfit)}", color = if (totalProfit >= 0) Color(0xFF00E676) else Color(0xFFFF5252), fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                        }
                        if (anyGst) {
                            Text(TerminologyManager.getTerm("Cost and sale prices are without GST. GST is not your profit."), color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        if (noCostCount > 0) {
                            Text("⚠ $noCostCount " + TerminologyManager.getTerm("item(s) have no purchase price saved, so their profit can't be worked out. Add their purchase to see it."), color = Color(0xFFFFC107), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color, textAlign = TextAlign.Center)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BestWorstCard(modifier: Modifier, label: String, name: String, profit: Double, isPositive: Boolean) {
    val color = if (isPositive) Color(0xFF00C853) else MaterialTheme.colorScheme.error
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("₹%.2f".format(profit), fontSize = 12.sp, color = color)
        }
    }
}

@Composable
private fun ProfitDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
