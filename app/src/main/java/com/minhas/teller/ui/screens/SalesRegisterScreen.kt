package com.minhas.teller.ui.screens

import com.minhas.teller.ui.components.rememberInBackground
import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random
import androidx.compose.material.icons.automirrored.filled.ShowChart
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.TerminologyManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesRegisterScreen(navController: NavHostController) {
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

    val allVouchers by voucherViewModel.allVouchersWithAllEntries.collectAsState()
    val allLedgers by voucherViewModel.allLedgers.collectAsState()

    var fromDate by remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        mutableStateOf(cal.time)
    }
    var toDate by remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
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

    val fromDatePickerDialog = DatePickerDialog(
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
    val toDatePickerDialog = DatePickerDialog(
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

    val salesVouchers = remember(allVouchers) { allVouchers.filter { it.voucher.type == "Sales" } }

    val dailyCards = rememberInBackground(salesVouchers, allLedgers, initial = emptyList<DailySalesSummary>()) {
        // Cash ledgers looked up once instead of a linear search per voucher entry.
        val cashLedgerIds = allLedgers.filter { it.name.contains("Cash", true) }.map { it.id }.toSet()
        salesVouchers
            .groupBy { startOfDay(it.voucher.date) }
            .map { (date, dayVouchers) ->
                val total = dayVouchers.sumOf { v -> v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees() }
                val voucherCount = dayVouchers.size
                val avgTx = if (voucherCount > 0) total / voucherCount else 0.0
                val cashAmt = dayVouchers.sumOf { v ->
                    v.voucherEntries.filter { entry ->
                        entry.isDebit && entry.ledgerId in cashLedgerIds
                    }.sumOf { it.amount }.paiseToRupees()
                }
                val itemsQty = dayVouchers.sumOf { v -> v.inventoryEntries.sumOf { it.quantity } }
                DailySalesSummary(
                    date = date, total = total, voucherCount = voucherCount,
                    newCount = dayVouchers.size, 
                    avgTransaction = avgTx, itemsSoldQty = itemsQty,
                    cashAmount = cashAmt, creditAmount = total - cashAmt
                )
            }
            .sortedByDescending { it.date }
    }

    val totalRevenue = remember(salesVouchers) {
        salesVouchers.sumOf { v -> v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees() }
    }
    val periodDays = remember(fromDate, toDate) {
        val diff = toDate.time - fromDate.time
        ((diff / (1000 * 60 * 60 * 24)) + 1).toInt().coerceAtLeast(1)
    }
    
    val dateUiFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D1A))
    ) {
        WireMesh()
        StarFieldOverlay()
        
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, TerminologyManager.getTerm("Back"), tint = Color.White)
                        }
                        AutoShrinkText(
                            TerminologyManager.getTerm("SALES & REVENUE HUB"),
                            maxFontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            style = TextStyle(letterSpacing = 1.sp)
                        )
                        IconButton(onClick = { /* Menu */ }) { Icon(Icons.Default.Menu, null, tint = Color.White) }
                        IconButton(onClick = { /* Search */ }) { Icon(Icons.Default.Search, null, tint = Color.White) }
                    }
                    
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(25.dp))
                                .background(Color(0xFF1E1E3F))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(25.dp))
                                .clickable { fromDatePickerDialog.show() }
                                .padding(14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AutoShrinkText(dateUiFormatter.format(fromDate), color = Color.White, maxFontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null, tint = Color.White)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(25.dp))
                                .background(Color(0xFF1E1E3F))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(25.dp))
                                .clickable { toDatePickerDialog.show() }
                                .padding(14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AutoShrinkText(dateUiFormatter.format(toDate), color = Color.White, maxFontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null, tint = Color.White)
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navController.navigate(Screen.AddEditVoucher.route + "?voucherId=-1&type=Sales") },
                    shape = CircleShape,
                    containerColor = Color(0xFF4DB6AC),
                    modifier = Modifier.size(76.dp).shadow(12.dp, CircleShape)
                ) {
                    Icon(Icons.Default.Add, TerminologyManager.getTerm("New Sale"), tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), // Halved vertical spacing between cards
                contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
            ) {
                // 1. Total Revenue Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) { // Halved padding from 24dp
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                Text(TerminologyManager.getTerm("TOTAL REVENUE:"), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                Icon(Icons.Default.Timeline, null, tint = Color.White.copy(alpha = 0.4f))
                            }
                            Spacer(Modifier.height(6.dp)) // Halved from 12dp
                            AutoShrinkText(
                                text = "₹ ${String.format("%,.2f", totalRevenue)}",
                                color = Color(0xFF00E676),
                                maxFontSize = 40.sp, // Decreased to 40.sp (standard step down from 42.sp)
                                fontWeight = FontWeight.Black,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = Shadow(
                                        color = Color(0xFF00E676).copy(alpha = 0.5f),
                                        offset = Offset(0f, 0f),
                                        blurRadius = 20f
                                    )
                                )
                            )
                            Spacer(Modifier.height(6.dp)) // Halved from 12dp
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.ShowChart, null, tint = Color(0xFF00E676), modifier = Modifier.size(18.dp))
                                AutoShrinkText(TerminologyManager.getTerm("  +100.0% Growth vs previous month"), color = Color(0xFF00E676), maxFontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp)) // Halved from 8dp
                            AutoShrinkText("${TerminologyManager.getTerm("PERIOD")}: $periodDays ${TerminologyManager.getTerm("DAYS")}", color = Color.White.copy(alpha = 0.5f), maxFontSize = 12.sp, minFontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // 2. Daily Summary Tiles
                if (dailyCards.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                            Text(TerminologyManager.getTerm("No sales recorded in this period."), color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp)
                        }
                    }
                } else {
                    items(dailyCards) { day ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    val dayStartMillis = startOfDay(day.date).time
                                    val baseRoute = Screen.TodayInvoicesByDate.route.substringBefore("?")
                                    navController.navigate("$baseRoute?selectedDateMillis=$dayStartMillis")
                                },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x11FFFFFF))
                        ) {
                            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) { // Halved vertical padding
                                AutoShrinkText(dateUiFormatter.format(day.date), color = Color.White, maxFontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp)) // Halved from 4dp
                                AutoShrinkText(
                                    text = "₹ ${String.format("%,.2f", day.total)}",
                                    color = Color(0xFF00E676),
                                    maxFontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    style = androidx.compose.ui.text.TextStyle(
                                        shadow = Shadow(
                                            color = Color(0xFF00E676).copy(alpha = 0.5f),
                                            offset = Offset(0f, 0f),
                                            blurRadius = 12f
                                        )
                                    )
                                )
                                Spacer(Modifier.height(5.dp)) // Halved from 10dp
                                AutoShrinkText(
                                    text = "VOUCHERS: ${day.voucherCount} (${day.newCount} New) | AVG: ₹ ${String.format("%.0f", day.avgTransaction)}",
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxFontSize = 11.sp,
                                    minFontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(3.dp)) // Halved from 6dp
                                AutoShrinkText(
                                    text = "ITEMS: ${day.itemsSoldQty.toInt()} | CASH: ₹ ${String.format("%.0f", day.cashAmount)} | CREDIT: ₹ ${String.format("%.0f", day.creditAmount)}",
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxFontSize = 11.sp,
                                    minFontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(Modifier.height(6.dp)) // Halved from 12dp
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x11FFFFFF))
                                            .border(1.dp, Color(0x3300E676), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AutoShrinkText("[ 💵 Cash ₹ ${String.format("%.0f", day.cashAmount)} ]", color = Color.White.copy(alpha = 0.9f), maxFontSize = 10.sp, minFontSize = 7.sp, fontWeight = FontWeight.Black)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x11FFFFFF))
                                            .border(1.dp, Color(0x334DB6AC), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AutoShrinkText("[ 📄 Credit ₹ ${String.format("%.0f", day.creditAmount)} ]", color = Color.White.copy(alpha = 0.9f), maxFontSize = 10.sp, minFontSize = 7.sp, fontWeight = FontWeight.Black)
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

data class DailySalesSummary(
    val date: Date,
    val total: Double,
    val voucherCount: Int,
    val newCount: Int,
    val avgTransaction: Double,
    val itemsSoldQty: Double,
    val cashAmount: Double,
    val creditAmount: Double
)

@Composable
private fun StarFieldOverlay() {
    val stars = remember { List(80) { Offset(Random.nextFloat(), Random.nextFloat()) } }
    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEach { pos ->
            drawCircle(
                color = Color.White.copy(alpha = Random.nextFloat() * 0.3f),
                radius = Random.nextFloat() * 1.5.dp.toPx(),
                center = Offset(pos.x * size.width, pos.y * size.height)
            )
        }
    }
}

@Composable
private fun WireMesh() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val step = 45.dp.toPx()
        for (x in 0..size.width.toInt() step step.toInt()) {
            drawLine(Color.White.copy(alpha = 0.03f), Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), strokeWidth = 1f)
        }
        for (y in 0..size.height.toInt() step step.toInt()) {
            drawLine(Color.White.copy(alpha = 0.03f), Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 1f)
        }
    }
}

private fun startOfDay(date: Date): Date {
    val cal = Calendar.getInstance()
    cal.time = date
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.time
}
