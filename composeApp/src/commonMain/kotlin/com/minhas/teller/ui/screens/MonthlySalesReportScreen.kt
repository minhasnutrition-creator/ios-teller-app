package com.minhas.teller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.TerminologyManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Helper for deterministic colors
private fun getColorForItem(name: String): Color {
    val colors = listOf(Color(0xFF9C27B0), Color(0xFFFFEB3B), Color(0xFFE91E63), Color(0xFF2196F3), Color(0xFF4CAF50))
    return colors[Math.abs(name.hashCode()) % colors.size]
}

private fun hexToColor(hex: String?, fallback: Color): Color {
    return try {
        if (hex.isNullOrEmpty()) fallback
        else Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlySalesReportScreen(
    navController: NavController,
    showroomCode: String?,
    appTheme: AppTheme = AppTheme.DEFAULT
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var showExportMenu by remember { mutableStateOf(false) }
    var transactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var stockItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var stockColors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val monthFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dayFormatter = remember { SimpleDateFormat("EEE", Locale.getDefault()) }

    fun safeQty(map: Map<String, Any>): Int {
        val q = map["quantity"] ?: map["qty"] ?: 0
        return when (q) {
            is Long -> q.toInt()
            is Int -> q
            else -> 0
        }
    }

    LaunchedEffect(showroomCode, selectedCalendar.timeInMillis) {
        if (showroomCode.isNullOrBlank()) return@LaunchedEffect
        isLoading = true
        val db = FirebaseDatabase.getInstance()
        val txRef = db.getReference("transactions").child(showroomCode)
        val stockRef = db.getReference("stock").child(showroomCode)

        txRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Map<String, Any>>()
                val items = mutableSetOf<String>()
                snapshot.children.forEach {
                    @Suppress("UNCHECKED_CAST")
                    val map = it.value as? Map<String, Any> ?: return@forEach
                    list.add(map)
                    val item = (map["vehicle"] as? String ?: map["item"] as? String)?.trim()
                    if (!item.isNullOrBlank()) {
                        items.add(item)
                    }
                }

                // Also fetch stock keys (items added in Live Stock) so they appear in the grid even if no transactions
                stockRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(stockSnap: DataSnapshot) {
                        val colorsMap = mutableMapOf<String, String>()
                        stockSnap.children.forEach { child ->
                            // prefer stored metadata name if present
                            val name = child.child("name").getValue(String::class.java) ?: child.key?.replace("_", " ")
                            val color = child.child("color").getValue(String::class.java)
                            if (!name.isNullOrBlank()) {
                                val trimmed = name.trim()
                                items.add(trimmed)
                                if (!color.isNullOrBlank()) {
                                    colorsMap[trimmed] = color
                                }
                            }
                        }
                        transactions = list
                        stockItems = items.filter { it.isNotBlank() }.sorted()
                        stockColors = colorsMap
                        isLoading = false
                    }
                    override fun onCancelled(e: DatabaseError) {
                        // still proceed with transaction-derived items
                        transactions = list
                        stockItems = items.filter { it.isNotBlank() }.sorted()
                        isLoading = false
                    }
                })
            }
            override fun onCancelled(e: DatabaseError) { error = e.message; isLoading = false }
        })
    }

    val daysInMonth = selectedCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val hScroll = rememberScrollState()
    val totalOutQty = remember(transactions, selectedCalendar) {
        val selYear = selectedCalendar.get(Calendar.YEAR)
        val selMonth = selectedCalendar.get(Calendar.MONTH)
        transactions.filter { tx ->
            val ts = (tx["timestamp"] as? Long) ?: 0L
            val txCal = Calendar.getInstance().apply { timeInMillis = ts }
            tx["type"] == "OUT" && txCal.get(Calendar.YEAR) == selYear && txCal.get(Calendar.MONTH) == selMonth
        }.sumOf { safeQty(it).toLong() }.toInt()
    }
    
    // Grid Data
    val dailyData = remember(transactions, stockItems, daysInMonth, selectedCalendar) {
        val map = mutableMapOf<String, Pair<IntArray, IntArray>>()
        stockItems.forEach { map[it] = Pair(IntArray(daysInMonth), IntArray(daysInMonth)) }
        
        val selYear = selectedCalendar.get(Calendar.YEAR)
        val selMonth = selectedCalendar.get(Calendar.MONTH)
        
        transactions.forEach { tx ->
            val rawItem = (tx["vehicle"] as? String ?: tx["item"] as? String)?.trim() ?: return@forEach
            val ts = (tx["timestamp"] as? Long) ?: return@forEach
            val txCal = Calendar.getInstance().apply { timeInMillis = ts }
            
            if (txCal.get(Calendar.YEAR) == selYear && txCal.get(Calendar.MONTH) == selMonth) {
                val day = txCal.get(Calendar.DAY_OF_MONTH) - 1
                if (day in 0 until daysInMonth) {
                    // Normalize lookup: find the best match in stockItems
                    val matchedItem = stockItems.find { 
                        it.equals(rawItem, ignoreCase = true) || 
                        it.replace(" ", "_").equals(rawItem.replace(" ", "_"), ignoreCase = true) 
                    } ?: rawItem
                    
                    val arrays = map.getOrPut(matchedItem) { Pair(IntArray(daysInMonth), IntArray(daysInMonth)) }
                    val type = tx["type"] as? String ?: "OUT"
                    if (type == "IN") {
                        arrays.first[day] += safeQty(tx)
                    } else {
                        arrays.second[day] += safeQty(tx)
                    }
                }
            }
        }
        map
    }

    val topSellingItem = remember(dailyData) {
        var bestItem = "N/A"
        var maxQty = 0
        dailyData.forEach { (item, arrays) ->
            val totalSold = arrays.second.sum()
            if (totalSold > maxQty) {
                maxQty = totalSold
                bestItem = item
            }
        }
        Pair(bestItem, maxQty)
    }

    val bestDay = remember(dailyData, daysInMonth) {
        var bestDayNum = 0
        var maxQty = 0
        for (dayIdx in 0 until daysInMonth) {
            var daySold = 0
            dailyData.values.forEach { arrays ->
                daySold += arrays.second[dayIdx]
            }
            if (daySold > maxQty) {
                maxQty = daySold
                bestDayNum = dayIdx + 1
            }
        }
        Pair(bestDayNum, maxQty)
    }

    val momGrowth = remember(transactions, selectedCalendar, totalOutQty) {
        val prevCal = (selectedCalendar.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        val prevYear = prevCal.get(Calendar.YEAR)
        val prevMonth = prevCal.get(Calendar.MONTH)
        val prevTotal = transactions.filter { tx ->
            val ts = (tx["timestamp"] as? Long) ?: 0L
            val txCal = Calendar.getInstance().apply { timeInMillis = ts }
            tx["type"] == "OUT" && txCal.get(Calendar.YEAR) == prevYear && txCal.get(Calendar.MONTH) == prevMonth
        }.sumOf { safeQty(it).toLong() }.toInt()
        
        if (prevTotal == 0) {
            if (totalOutQty > 0) "+100%" else "0%"
        } else {
            val percent = ((totalOutQty - prevTotal).toDouble() / prevTotal * 100).toInt()
            val sign = if (percent >= 0) "+" else ""
            "$sign$percent%"
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A1A3A), Color(0xFF2D1B4E), Color(0xFF121926))))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, TerminologyManager.getTerm("Back"), tint = Color.White) }
                        Column(modifier = Modifier.weight(1f)) {
                            AutoShrinkText(
                                text = TerminologyManager.getTerm("Monthly Sales Report"),
                                maxFontSize = 18.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = TerminologyManager.getTerm("Product Sales Overview"),
                                fontSize = 11.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                            IconButton(onClick = { selectedCalendar = (selectedCalendar.clone() as Calendar).apply { add(Calendar.MONTH, -1) } }, modifier = Modifier.size(20.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                            Text(monthFormatter.format(selectedCalendar.time), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
                            Icon(Icons.Default.CalendarToday, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            IconButton(onClick = { selectedCalendar = (selectedCalendar.clone() as Calendar).apply { add(Calendar.MONTH, 1) } }, modifier = Modifier.size(20.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = TerminologyManager.getTerm("Export Menu"), tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(TerminologyManager.getTerm("Export to PDF")) },
                                    onClick = {
                                        showExportMenu = false
                                        val selYear = selectedCalendar.get(Calendar.YEAR)
                                        val selMonth = selectedCalendar.get(Calendar.MONTH)
                                        val monthName = monthFormatter.format(selectedCalendar.time)
                                        
                                        val filtered = transactions.filter { tx ->
                                            val ts = (tx["timestamp"] as? Long) ?: 0L
                                            val txCal = Calendar.getInstance().apply { timeInMillis = ts }
                                            txCal.get(Calendar.YEAR) == selYear && txCal.get(Calendar.MONTH) == selMonth
                                        }.sortedBy { (it["timestamp"] as? Long) ?: 0L }
                                        
                                        val headers = listOf("Date", "Product / Vehicle", "Type", "Quantity")
                                        val exportData = filtered.map { tx ->
                                            val ts = (tx["timestamp"] as? Long) ?: 0L
                                            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ts))
                                            val item = (tx["vehicle"] as? String ?: tx["item"] as? String)?.trim() ?: "Unknown"
                                            val type = tx["type"] as? String ?: "OUT"
                                            val qty = safeQty(tx).toString()
                                            listOf(dateStr, item, type, qty)
                                        }
                                        
                                        com.minhas.teller.utils.ExportUtils.exportReportToPdf(
                                            context,
                                            "Monthly_Sales_Report_${monthName.replace(" ", "_")}.pdf",
                                            TerminologyManager.getTerm("Monthly Sales Overview"),
                                            "${TerminologyManager.getTerm("Monthly Sales Report")} - $monthName",
                                            headers,
                                            floatArrayOf(2f, 3f, 1.5f, 1.5f),
                                            exportData
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(TerminologyManager.getTerm("Export to Excel")) },
                                    onClick = {
                                        showExportMenu = false
                                        val selYear = selectedCalendar.get(Calendar.YEAR)
                                        val selMonth = selectedCalendar.get(Calendar.MONTH)
                                        val monthName = monthFormatter.format(selectedCalendar.time)
                                        
                                        val filtered = transactions.filter { tx ->
                                            val ts = (tx["timestamp"] as? Long) ?: 0L
                                            val txCal = Calendar.getInstance().apply { timeInMillis = ts }
                                            txCal.get(Calendar.YEAR) == selYear && txCal.get(Calendar.MONTH) == selMonth
                                        }.sortedBy { (it["timestamp"] as? Long) ?: 0L }
                                        
                                        val headers = listOf("Date", "Product / Vehicle", "Type", "Quantity")
                                        val exportData = filtered.map { tx ->
                                            val ts = (tx["timestamp"] as? Long) ?: 0L
                                            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ts))
                                            val item = (tx["vehicle"] as? String ?: tx["item"] as? String)?.trim() ?: "Unknown"
                                            val type = tx["type"] as? String ?: "OUT"
                                            val qty = safeQty(tx).toString()
                                            listOf(dateStr, item, type, qty)
                                        }
                                        
                                        com.minhas.teller.utils.ExportUtils.exportReportToExcel(
                                            context,
                                            "Monthly_Sales_Report_${monthName.replace(" ", "_")}.xlsx",
                                            TerminologyManager.getTerm("Sales Report"),
                                            headers,
                                            exportData
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Spacer(modifier = Modifier.height(24.dp))
                if (isLoading) { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) } }
                else {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Left Sticky Header cell (left)
                            Box(modifier = Modifier.width(48.dp).padding(bottom = 12.dp), contentAlignment = Alignment.BottomCenter) {
                                Text(TerminologyManager.getTerm("Stock"), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            // Right scrollable days header
                            Row(modifier = Modifier.horizontalScroll(hScroll).weight(1f)) {
                                for (day in 1..daysInMonth) {
                                    val cal = Calendar.getInstance().apply { timeInMillis = selectedCalendar.timeInMillis; set(Calendar.DAY_OF_MONTH, day) }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp).padding(bottom = 12.dp)) {
                                        Text(dayFormatter.format(cal.time), color = Color.Gray, fontSize = 11.sp)
                                        Text("$day", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(stockItems) { item ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    val itemColor = remember(item, stockColors) {
                                        val hex = stockColors[item] ?: stockColors[item.replace(" ", "_")] ?: stockColors[item.replace("_", " ")]
                                        hexToColor(hex, getColorForItem(item))
                                    }
                                    val textColor = if (itemColor == Color(0xFFFFEB3B)) Color.Black else Color.White
                                    
                                    // Left Sticky Item Name (1/3 of the real name cell -> 48.dp)
                                    Box(
                                        modifier = Modifier
                                            .width(48.dp)
                                            .height(48.dp)
                                            .background(itemColor)
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = item,
                                            color = textColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            lineHeight = 10.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    
                                    // Right Scrollable daily data cells
                                    Row(modifier = Modifier.horizontalScroll(hScroll).weight(1f)) {
                                        val rowData = dailyData[item] ?: Pair(IntArray(daysInMonth), IntArray(daysInMonth))
                                        for (dayIdx in 0 until daysInMonth) {
                                            val inQ = rowData.first[dayIdx]; val outQ = rowData.second[dayIdx]
                                            Box(modifier = Modifier.width(48.dp).height(48.dp).background(Color.White.copy(alpha = 0.08f)).border(0.1.dp, Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    if (inQ > 0) Text("$inQ", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    if (outQ > 0) Text("$outQ", color = Color(0xFFF44336), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    if (inQ == 0 && outQ == 0) Text("-", color = Color.Gray.copy(alpha = 0.5f), fontSize = 14.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportStatCard(Modifier.weight(1f), TerminologyManager.getTerm("Sales"), TerminologyManager.getTerm("Month"), "$totalOutQty", Color(0xFFEF5350))
                        ReportStatCard(Modifier.weight(1f), TerminologyManager.getTerm("Top"), "${topSellingItem.second} ${TerminologyManager.getTerm("sold")}", topSellingItem.first, Color(0xFF42A5F5))
                        ReportStatCard(Modifier.weight(1f), TerminologyManager.getTerm("Best"), "Day ${bestDay.first}", if (bestDay.second > 0) "${bestDay.second} ${TerminologyManager.getTerm("sold")}" else "N/A", Color(0xFF66BB6A))
                        ReportStatCard(Modifier.weight(1f), TerminologyManager.getTerm("Growth"), TerminologyManager.getTerm("MoM"), momGrowth, Color(0xFFAB47BC))
                    }
                }
            }
        }
    }
}

@Composable
fun ReportStatCard(modifier: Modifier, title: String, subtitle: String, value: String, containerColor: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = containerColor), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
            }
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
