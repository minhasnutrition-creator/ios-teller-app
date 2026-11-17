package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.theme.AccountsGradient
import com.minhas.teller.ui.theme.ReportsOrangeGradient
import com.minhas.teller.ui.theme.GlassWhite
import com.minhas.teller.ui.theme.GlassBorder
import com.minhas.teller.ui.viewmodels.LedgerViewModel
import com.minhas.teller.ui.viewmodels.LedgerViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

// Money-in green and money-out orange — distinct from the blue "reports" identity but consistent
// with the app's colour-coded amount convention (green = incoming, orange/red = outgoing).
private val ReceiptsGreen = Color(0xFF2ECC71)
private val PaymentsOrange = Color(0xFFFF8A3D)

private enum class ReportPeriod(val label: String) {
    TODAY("Today"),
    WEEK("This Week"),
    MONTH("This Month"),
    YEAR("This Year"),
    ALL("All Time")
}

private data class CategoryAmount(val label: String, val amount: Double)

private data class ReceiptsPaymentsData(
    val totalReceipts: Double,
    val totalPayments: Double,
    val openingBalance: Double,
    val closingBalance: Double,
    val receipts: List<CategoryAmount>,
    val payments: List<CategoryAmount>
) {
    val netFlow: Double get() = totalReceipts - totalPayments
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsAndPaymentsReportScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)

    val ledgerRepository = remember { LedgerRepository(database.ledgerDao()) }
    val ledgerViewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(ledgerRepository))
    val allLedgers by ledgerViewModel.allLedgers.collectAsState(initial = emptyList())
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()

    var period by remember { mutableStateOf(ReportPeriod.MONTH) }
    var data by remember { mutableStateOf<ReceiptsPaymentsData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(period, currentCompanyId, allLedgers) {
        val companyId = currentCompanyId ?: return@LaunchedEffect
        isLoading = true
        data = withContext(Dispatchers.IO) {
            val (start, end) = periodRange(period)
            computeReceiptsPayments(database, allLedgers, companyId, start, end)
        }
        isLoading = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = "Receipts & Payments",
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Period selector ─────────────────────────────────────────────
            PeriodSelector(selected = period, onSelect = { period = it })

            if (isLoading || data == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                return@Column
            }

            val d = data!!

            // ── Net cash flow hero ──────────────────────────────────────────
            NetFlowHero(netFlow = d.netFlow)

            // ── Money in / money out summary ────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    title = "Money In",
                    amount = d.totalReceipts,
                    gradient = AccountsGradient,
                    accent = ReceiptsGreen,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "Money Out",
                    amount = d.totalPayments,
                    gradient = ReportsOrangeGradient,
                    accent = PaymentsOrange,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Opening / closing balances ──────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BalanceCard("Opening Balance", d.openingBalance, Modifier.weight(1f))
                BalanceCard("Closing Balance", d.closingBalance, Modifier.weight(1f))
            }

            // ── Breakdown: money in ─────────────────────────────────────────
            BreakdownCard(
                title = "Money In — by source",
                accent = ReceiptsGreen,
                items = d.receipts,
                total = d.totalReceipts,
                emptyText = "No receipts in this period."
            )

            // ── Breakdown: money out ────────────────────────────────────────
            BreakdownCard(
                title = "Money Out — by purpose",
                accent = PaymentsOrange,
                items = d.payments,
                total = d.totalPayments,
                emptyText = "No payments in this period."
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ── UI pieces ───────────────────────────────────────────────────────────────

@Composable
private fun PeriodSelector(selected: ReportPeriod, onSelect: (ReportPeriod) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReportPeriod.values().forEach { p ->
            val isSelected = p == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Brush.horizontalGradient(listOf(Color(0xFF2193B0), Color(0xFF6DD5ED))) else SolidColor(GlassWhite))
                    .border(1.dp, if (isSelected) Color.Transparent else GlassBorder, RoundedCornerShape(50))
                    .clickable { onSelect(p) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = p.label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun NetFlowHero(netFlow: Double) {
    val positive = netFlow >= 0
    val gradient = if (positive) {
        Brush.linearGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFEF473A), Color(0xFFCB2D3E)))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .padding(20.dp)
    ) {
        Column {
            Text(TerminologyManager.getTerm("Net Cash Flow"), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            AutoShrinkText(
                text = formatAmount(netFlow),
                color = Color.White,
                maxFontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (positive) "More money in than out this period" else "More money out than in this period",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    amount: Double,
    gradient: Brush,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .padding(16.dp)
    ) {
        Column {
            Text(title, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            AutoShrinkText(
                text = formatAmount(amount),
                color = Color.White,
                maxFontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BalanceCard(label: String, amount: Double, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlassWhite)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            AutoShrinkText(
                text = formatAmount(amount),
                color = Color.White,
                maxFontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BreakdownCard(
    title: String,
    accent: Color,
    items: List<CategoryAmount>,
    total: Double,
    emptyText: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GlassWhite)
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        if (items.isEmpty()) {
            Text(emptyText, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            return@Column
        }

        val maxAmount = items.maxOf { it.amount }.coerceAtLeast(0.01)
        items.forEachIndexed { index, cat ->
            CategoryRow(cat, maxAmount, accent)
            if (index != items.lastIndex) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(TerminologyManager.getTerm("Total"), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            AutoShrinkText(
                text = formatAmount(total),
                color = accent,
                maxFontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CategoryRow(cat: CategoryAmount, maxAmount: Double, accent: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutoShrinkText(
                text = cat.label,
                color = Color.White,
                maxFontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            AutoShrinkText(
                text = formatAmount(cat.amount),
                color = accent,
                maxFontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((cat.amount / maxAmount).toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent)
            )
        }
    }
}

// ── Computation helpers ─────────────────────────────────────────────────────

private fun periodRange(period: ReportPeriod): Pair<Date, Date> {
    val end = Date()
    val start = when (period) {
        ReportPeriod.TODAY -> Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        ReportPeriod.WEEK -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time
        ReportPeriod.MONTH -> Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        ReportPeriod.YEAR -> Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        ReportPeriod.ALL -> Calendar.getInstance().apply { set(1970, Calendar.JANUARY, 1) }.time
    }
    return start to end
}

private suspend fun computeReceiptsPayments(
    database: AppDatabase,
    allLedgers: List<Ledger>,
    companyId: Int,
    startDate: Date,
    endDate: Date
): ReceiptsPaymentsData {
    val cashBankLedgers = allLedgers.filter { it.group == "Cash-in-hand" || it.group == "Bank Accounts" }
    val cashBankIds = cashBankLedgers.map { it.id }.toSet()
    val ledgerNameById = allLedgers.associateBy { it.id }

    val vouchers = database.voucherDao().getAllVouchersWithAllEntriesBetweenDates(companyId, startDate, endDate)

    val receiptMap = mutableMapOf<String, Double>()
    val paymentMap = mutableMapOf<String, Double>()
    var totalReceipts = 0.0
    var totalPayments = 0.0

    for (vwe in vouchers) {
        val cashEntries = vwe.voucherEntries.filter { it.ledgerId in cashBankIds }
        if (cashEntries.isEmpty()) continue
        val counterparties = vwe.voucherEntries.filter { it.ledgerId !in cashBankIds }
        val counterpartyName = counterparties.firstOrNull()?.ledgerId?.let { ledgerNameById[it]?.name } ?: "Transfer"

        for (entry in cashEntries) {
            if (entry.isDebit) {
                totalReceipts += entry.amount
                receiptMap[counterpartyName] = (receiptMap[counterpartyName] ?: 0.0) + entry.amount
            } else {
                totalPayments += entry.amount
                paymentMap[counterpartyName] = (paymentMap[counterpartyName] ?: 0.0) + entry.amount
            }
        }
    }

    // Closing balance = current cash + bank balance; opening = closing − period net flow.
    var closingBalance = 0.0
    for (ledger in cashBankLedgers) {
        closingBalance += (database.ledgerDao().getClosingBalance(ledger.id, companyId, endDate).first() ?: 0L).paiseToRupees()
    }
    val openingBalance = closingBalance - (totalReceipts - totalPayments)

    return ReceiptsPaymentsData(
        totalReceipts = totalReceipts,
        totalPayments = totalPayments,
        openingBalance = openingBalance,
        closingBalance = closingBalance,
        receipts = receiptMap.entries.sortedByDescending { it.value }.map { CategoryAmount(it.key, it.value) },
        payments = paymentMap.entries.sortedByDescending { it.value }.map { CategoryAmount(it.key, it.value) }
    )
}

private fun formatAmount(amount: Double): String {
    val abs = Math.abs(amount)
    val formatted = String.format(java.util.Locale.getDefault(), "₹%,.2f", abs)
    return if (amount < 0) "-$formatted" else formatted
}
