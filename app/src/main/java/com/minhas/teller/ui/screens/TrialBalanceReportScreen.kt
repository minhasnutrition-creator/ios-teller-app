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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.ui.viewmodels.LedgerViewModel
import com.minhas.teller.ui.viewmodels.LedgerViewModelFactory
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.SecurityUtils
import java.text.SimpleDateFormat
import java.util.*

// ── Premium glassmorphism palette ──
private val GradientColors = listOf(
    Color(0xFF3B1C78), // rich violet
    Color(0xFF2B1660),
    Color(0xFF1F1B5E),
    Color(0xFF131B4D), // indigo
    Color(0xFF0B1238)  // deep navy
)
private val GlassFill = Color.White.copy(alpha = 0.08f)
private val GlassFillDeep = Color.White.copy(alpha = 0.04f)
private val GlassStroke = Color.White.copy(alpha = 0.16f)
private val Cyan = Color(0xFF45E0E6) // rich cyan accent
// Two fixed number columns plus the icon used to leave the ledger name well under
// 50dp on a phone, so names ellipsised down to a letter ("Amrinder" -> "A…").
private val NumColWidth = 74.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrialBalanceReportScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)

    val ledgerRepository = LedgerRepository(database.ledgerDao())
    val ledgerViewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(ledgerRepository))
    val ledgers by ledgerViewModel.allLedgers.collectAsState()

    val voucherRepository = VoucherRepository(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.billAllocationDao(), database.auditLogDao(), database.ledgerDao(), db = database)
    val voucherViewModel: VoucherViewModel = viewModel(factory = VoucherViewModelFactory(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.ledgerDao(), database.billAllocationDao(), database.auditLogDao(), db = database))

    val companyRepository = CompanyRepository(database.companyDao())
    val companyViewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(companyRepository))
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val company by if (currentCompanyId != null) {
        companyViewModel.getCompany(currentCompanyId!!).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(null) }
    }

    val defaultCalendar = Calendar.getInstance()
    defaultCalendar.set(Calendar.DAY_OF_MONTH, 1)
    var fromDate by remember { mutableStateOf(defaultCalendar.time) }
    var toDate by remember { mutableStateOf(Date()) }

    val allVouchersWithAllEntries by voucherViewModel.allVouchersWithAllEntries.collectAsState()
    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    val fromDatePickerDialog = DatePickerDialog(context, { _, y, m, d ->
        val cal = Calendar.getInstance()
        cal.set(y, m, d)
        fromDate = cal.time
    }, Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH))

    val toDatePickerDialog = DatePickerDialog(context, { _, y, m, d ->
        val cal = Calendar.getInstance()
        cal.set(y, m, d)
        toDate = cal.time
    }, Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH))

    LaunchedEffect(toDate, currentCompanyId) {
        voucherViewModel.getAllVouchersWithAllEntriesBetweenDates(Date(0L), toDate)
    }

    // Off the UI thread: this walks every voucher since inception and used to rerun on each recomposition.
    val ledgerBalances = rememberInBackground(ledgers, allVouchersWithAllEntries, initial = emptyMap<Ledger, Double>()) {
        calculateLedgerBalances(ledgers, allVouchersWithAllEntries)
    }
    val trialBalanceData = ledgerBalances.map { (ledger, balance) ->
        val debitAmount = if (balance >= 0) balance else 0.0
        val creditAmount = if (balance < 0) Math.abs(balance) else 0.0
        Triple(ledger.name, debitAmount, creditAmount)
    }.sortedBy { it.first.lowercase() }

    val totalDebit = trialBalanceData.sumOf { it.second }
    val totalCredit = trialBalanceData.sumOf { it.third }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(GradientColors))
    ) {
        // ── Soft light effects (layered glows) ──
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (-140).dp, y = (-120).dp)
                .size(360.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF9C5CFF).copy(alpha = 0.50f), Color.Transparent)))
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 90.dp, y = (-60).dp)
                .size(380.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF5B8DEF).copy(alpha = 0.30f), Color.Transparent)))
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-90).dp, y = 120.dp)
                .size(340.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF26C6DA).copy(alpha = 0.22f), Color.Transparent)))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Persistent security warning (kept from the previous top bar)
            if (SecurityUtils.isCompromised) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFB71C1C).copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Security warning: This device may be compromised. Financial data is at risk.",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Top navigation bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(GlassFill)
                        .border(1.dp, GlassStroke, CircleShape)
                        .clickable { navController.popBackStack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                AutoShrinkText(
                    text = "Trial Balance",
                    color = Color.White,
                    maxFontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.weight(1f))
                GlassIconButton(Icons.Default.PictureAsPdf, "Export PDF") {
                    ExportUtils.exportTrialBalanceToPdf(context, trialBalanceData)
                }
                Spacer(Modifier.width(10.dp))
                GlassIconButton(Icons.Default.TableChart, "Export Report") {
                    ExportUtils.exportTrialBalanceToExcel(context, trialBalanceData)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Date filter pills ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DatePill(
                    label = "From",
                    date = dateFormatter.format(fromDate),
                    onClick = { fromDatePickerDialog.show() },
                    modifier = Modifier.weight(1f)
                )
                DatePill(
                    label = "To",
                    date = dateFormatter.format(toDate),
                    onClick = { toDatePickerDialog.show() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Main frosted-glass panel ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(GlassFill)
                    .border(1.dp, GlassStroke, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Column header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.size(32.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "LEDGER",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp
                            )
                        }
                        Text(
                            text = "DEBIT",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(NumColWidth)
                        )
                        Text(
                            text = "CREDIT",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(NumColWidth)
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    if (ledgers.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(TerminologyManager.getTerm("No records found."), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(trialBalanceData) { (name, debit, credit) ->
                                val ledgerId = ledgers.find { it.name == name }?.id
                                LedgerRowCard(name, debit, credit) {
                                    ledgerId?.let { id -> navController.navigate("ledger_view_report/$id") }
                                }
                            }
                        }
                    }

                    // ── TOTAL BALANCE footer bar ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Cyan.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TOTAL BALANCE",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Combined ledger position",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = "%.2f".format(totalDebit),
                                color = Cyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                modifier = Modifier.width(NumColWidth)
                            )
                            Text(
                                text = "%.2f".format(totalCredit),
                                color = Cyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                modifier = Modifier.width(NumColWidth)
                            )
                        }
                    }

                    if (Math.abs(totalDebit - totalCredit) > 0.01) {
                        Text(TerminologyManager.getTerm("Trial Balance Mismatch!"),
                            color = Color(0xFFFF8A80),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(GlassFill)
            .border(1.dp, GlassStroke, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DatePill(label: String, date: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(GlassFill)
            .border(1.dp, GlassStroke, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Cyan, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = label.uppercase(),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = date,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LedgerRowCard(name: String, debit: Double, credit: Double, onClick: () -> Unit) {
    val (icon, iconColor) = ledgerMeta(name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassFillDeep)
            .border(1.dp, GlassStroke.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (debit > 0) "%.2f".format(debit) else "",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(NumColWidth)
        )
        Text(
            text = if (credit > 0) "%.2f".format(credit) else "",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(NumColWidth)
        )
    }
}

private fun ledgerMeta(name: String): Pair<ImageVector, Color> {
    val n = name.lowercase()
    return when {
        n.contains("cash") -> Icons.Default.Paid to Color(0xFFFFD35C)            // coins — gold
        n.contains("purchase") -> Icons.Default.ShoppingCart to Color(0xFFFFA76B) // cart — orange
        n.contains("sale") -> Icons.Default.ShowChart to Color(0xFF59E0C8)        // trend chart — teal
        n.contains("gst") || n.contains("tax") || n.contains("duty") || n.contains("cess") ->
            Icons.Default.Shield to Color(0xFFC0A8FF)                            // tax shield — violet
        n.contains("bank") -> Icons.Default.AccountBalance to Color(0xFF80D8FF)
        else -> Icons.Default.AccountBalanceWallet to Color(0xFFA8B6CC)
    }
}

private fun calculateLedgerBalances(ledgers: List<Ledger>, vouchersWithAllEntries: List<VoucherWithAllEntries>): Map<Ledger, Double> {
    val balances = ledgers.associateWith { it.openingBalance }.toMutableMap()
    vouchersWithAllEntries.forEach { v ->
        if (v.voucher.type == "Quotation") return@forEach
        v.voucherEntries.forEach { entry ->
            val ledger = ledgers.find { it.id == entry.ledgerId }
            ledger?.let {
                if (entry.isDebit) balances[it] = (balances[it] ?: 0L) + entry.amount
                else balances[it] = (balances[it] ?: 0L) - entry.amount
            }
        }
    }
    return balances.mapValues { it.value.paiseToRupees() }
}
