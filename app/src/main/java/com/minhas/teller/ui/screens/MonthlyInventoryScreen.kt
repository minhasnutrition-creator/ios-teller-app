package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.ui.components.AutoShrinkText
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Month grid of stock movements: one row per item, one column per day, showing how much came IN
 * (green) and went OUT (red) on each date — same idea as the Live Stock "Monthly Sales" grid, but
 * built from the local books (purchases/sales) instead of Firebase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyInventoryScreen(navController: NavHostController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val voucherRepository = remember {
        VoucherRepository(db.voucherDao(), db.voucherEntryDao(), db.inventoryEntryDao(), db.billAllocationDao(), db.auditLogDao(), db.ledgerDao(), db = db)
    }
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()

    var stockItems by remember { mutableStateOf<List<StockItem>>(emptyList()) }
    var vouchers by remember { mutableStateOf<List<VoucherWithAllEntries>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(Calendar.getInstance()) }

    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dayFmt = remember { SimpleDateFormat("EEE", Locale.getDefault()) }

    LaunchedEffect(currentCompanyId, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH)) {
        val cid = currentCompanyId ?: return@LaunchedEffect
        loading = true
        stockItems = db.stockItemDao().getAllStockItems(cid)
        val from = Calendar.getInstance().apply { clear(); set(selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), 1, 0, 0, 0) }.time
        val to = Calendar.getInstance().apply { clear(); set(selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), 1, 0, 0, 0); add(Calendar.MONTH, 1); add(Calendar.MILLISECOND, -1) }.time
        vouchers = voucherRepository.getAllVouchersWithAllEntriesBetweenDates(cid, from, to)
        loading = false
    }

    val daysInMonth = selected.getActualMaximum(Calendar.DAY_OF_MONTH)
    val hScroll = rememberScrollState()

    // itemId -> (in[day], out[day])
    val dailyData = remember(vouchers, daysInMonth, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH)) {
        val map = mutableMapOf<Int, Pair<IntArray, IntArray>>()
        vouchers.forEach { vwe ->
            val v = vwe.voucher
            val dayCal = Calendar.getInstance().apply { time = v.date }
            val day = dayCal.get(Calendar.DAY_OF_MONTH) - 1
            if (day !in 0 until daysInMonth) return@forEach
            vwe.inventoryEntries.forEach { entry ->
                val arr = map.getOrPut(entry.stockItemId) { Pair(IntArray(daysInMonth), IntArray(daysInMonth)) }
                when {
                    v.type.equals("Purchase", true) || v.type.equals("Sales Return", true) || v.type.equals("Credit Note", true) -> arr.first[day] += entry.quantity.toInt()
                    v.type.equals("Sales", true) || v.type.equals("Purchase Return", true) || v.type.equals("Debit Note", true) -> arr.second[day] += entry.quantity.toInt()
                    v.type.equals("Journal", true) && entry.quantity > 0 -> arr.first[day] += entry.quantity.toInt()
                    v.type.equals("Journal", true) && entry.quantity < 0 -> arr.second[day] += (-entry.quantity).toInt()
                }
            }
        }
        map
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { AutoShrinkText("Monthly Inventory", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F1A12))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0F1A12), Color(0xFF060D40))))
                .padding(padding)
        ) {
            // Month selector
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { selected = (selected.clone() as Calendar).apply { add(Calendar.MONTH, -1) } }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = Color.White)
                }
                Text(monthFmt.format(selected.time), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                IconButton(onClick = { selected = (selected.clone() as Calendar).apply { add(Calendar.MONTH, 1) } }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = Color.White)
                }
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Color(0xFF4CAF50), RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(4.dp))
                    Text(TerminologyManager.getTerm("In"), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Color(0xFFF44336), RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(4.dp))
                    Text(TerminologyManager.getTerm("Out"), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF69F0AE)) }
            } else {
                // Header row: item label + day numbers
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.width(110.dp).padding(start = 16.dp, bottom = 8.dp), contentAlignment = Alignment.BottomStart) {
                        Text(TerminologyManager.getTerm("Item"), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.horizontalScroll(hScroll).weight(1f)) {
                        for (day in 1..daysInMonth) {
                            val cal = Calendar.getInstance().apply { time = selected.time; set(Calendar.DAY_OF_MONTH, day) }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(40.dp).padding(bottom = 8.dp)
                            ) {
                                Text(dayFmt.format(cal.time), color = Color.Gray, fontSize = 9.sp)
                                Text("$day", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(stockItems, key = { it.id }) { item ->
                        val arr = dailyData[item.id] ?: Pair(IntArray(daysInMonth), IntArray(daysInMonth))
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Box(
                                modifier = Modifier.width(110.dp).height(44.dp).padding(start = 16.dp, end = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                            Row(modifier = Modifier.horizontalScroll(hScroll).weight(1f)) {
                                for (day in 0 until daysInMonth) {
                                    val inQ = arr.first[day]
                                    val outQ = arr.second[day]
                                    Box(
                                        modifier = Modifier
                                            .width(40.dp).height(44.dp)
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .border(0.2.dp, Color.White.copy(alpha = 0.08f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when {
                                            inQ > 0 && outQ > 0 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("+$inQ", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Text("-$outQ", color = Color(0xFFF44336), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            inQ > 0 -> Text("+$inQ", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            outQ > 0 -> Text("-$outQ", color = Color(0xFFF44336), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            else -> Text("·", color = Color.Gray.copy(alpha = 0.4f), fontSize = 12.sp)
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
