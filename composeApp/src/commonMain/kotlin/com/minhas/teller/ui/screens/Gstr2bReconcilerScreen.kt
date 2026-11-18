package com.minhas.teller.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.gst.*
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.utils.TerminologyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GradientColors = listOf(
    Color(0xFF3B1C78),
    Color(0xFF2B1660),
    Color(0xFF1F1B5E),
    Color(0xFF131B4D),
    Color(0xFF0B1238)
)
private val GlassFill = Color.White.copy(alpha = 0.08f)
private val GlassFillDeep = Color.White.copy(alpha = 0.04f)
private val GlassStroke = Color.White.copy(alpha = 0.16f)
private val Cyan = Color(0xFF45E0E6)
private val Emerald = Color(0xFF10B981)
private val Amber = Color(0xFFFFB74D)
private val Rose = Color(0xFFFF5252)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Gstr2bReconcilerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val voucherRepo = remember {
        VoucherRepository(db.voucherDao(), db.voucherEntryDao(), db.inventoryEntryDao(), db.billAllocationDao(), db.auditLogDao(), db.ledgerDao(), db = db)
    }
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()

    var company by remember { mutableStateOf<Company?>(null) }
    var ledgers by remember { mutableStateOf<List<Ledger>>(emptyList()) }
    var purchaseVouchers by remember { mutableStateOf<List<VoucherWithAllEntries>>(emptyList()) }
    var summary by remember { mutableStateOf<Gstr2bReconciliationSummary?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf<Gstr2bMatchStatus?>(null) } // null = All

    LaunchedEffect(currentCompanyId) {
        val cid = currentCompanyId ?: return@LaunchedEffect
        company = db.companyDao().getCompanyById(cid).firstOrNull()
        ledgers = db.ledgerDao().getAllLedgersSync(cid)
        purchaseVouchers = voucherRepo.getAllVouchersWithAllEntriesBetweenDates(cid, Date(0L), Date())
            .filter { it.voucher.type == "Purchase" }
    }

    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                try {
                    val jsonContent = withContext(Dispatchers.Default) {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream)).readText()
                        } ?: ""
                    }

                    if (jsonContent.isNotBlank()) {
                        val parsed = Gstr2bReconciler.parseGstr2bJson(jsonContent)
                        if (parsed.isEmpty()) {
                            Toast.makeText(context, "No valid GSTR-2B invoices found in file", Toast.LENGTH_LONG).show()
                        } else {
                            val res = Gstr2bReconciler.reconcile(parsed, purchaseVouchers, ledgers)
                            summary = res
                            Toast.makeText(context, "Processed ${parsed.size} GSTR-2B invoices", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to parse: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(GradientColors))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TellerTopAppBar(
                    title = TerminologyManager.getTerm("GSTR-2B ITC Reconciliation"),
                    canNavigateBack = true,
                    navigateUp = { navController.navigateUp() }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Header & Upload Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(GlassFill)
                            .border(1.dp, GlassStroke, RoundedCornerShape(20.dp))
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Cyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = Cyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Auto-Reconcile GSTR-2B",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Match supplier filings against purchase vouchers",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { jsonPickerLauncher.launch(arrayOf("application/json", "*/*")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color(0xFF0F1A12))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(TerminologyManager.getTerm("Import 2B JSON"), color = Color(0xFF0F1A12), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    // Generate demo 2B data based on existing purchase vouchers
                                    scope.launch {
                                        val sample2b = purchaseVouchers.mapIndexed { idx, pv ->
                                            val total = pv.inventoryEntries.sumOf { if (it.totalAmount > 0) it.totalAmount else it.amount }
                                            Gstr2bInvoice(
                                                gstin = "27AABCU9603R1ZM",
                                                tradeName = "DEMO SUPPLIER CORP",
                                                invoiceNumber = pv.voucher.voucherNumber.ifBlank { "INV-$idx" },
                                                invoiceDate = pv.voucher.date,
                                                taxableValue = total * 0.82,
                                                igst = 0.0,
                                                cgst = total * 0.09,
                                                sgst = total * 0.09
                                            )
                                        }
                                        val res = Gstr2bReconciler.reconcile(sample2b, purchaseVouchers, ledgers)
                                        summary = res
                                        Toast.makeText(context, "Loaded demo comparison dataset", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GlassStroke)
                            ) {
                                Text(TerminologyManager.getTerm("Load Demo Data"), color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (isProcessing) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Cyan)
                        }
                    }
                }

                // Summary Dashboard Cards
                val s = summary
                if (s != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "ITC Reconciliation Overview",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricCard(
                                    title = "Matched ITC",
                                    amount = s.matchedItc,
                                    count = s.matchedInvoices,
                                    accentColor = Emerald,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "Missing in Books",
                                    amount = s.missingInBooksItc,
                                    count = s.missingInBooksInvoices,
                                    accentColor = Cyan,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricCard(
                                    title = "At Risk ITC",
                                    amount = s.atRiskItc,
                                    count = s.atRiskInvoices,
                                    accentColor = Rose,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    title = "Total 2B ITC",
                                    amount = s.total2bItc,
                                    count = s.total2bInvoices,
                                    accentColor = Amber,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Filter Chips Row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedFilter == null,
                                onClick = { selectedFilter = null },
                                label = { AutoShrinkText("All (${s.results.size})", maxFontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = selectedFilter == Gstr2bMatchStatus.EXACT_MATCH,
                                onClick = { selectedFilter = Gstr2bMatchStatus.EXACT_MATCH },
                                label = { AutoShrinkText(TerminologyManager.getTerm("Matched"), maxFontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = selectedFilter == Gstr2bMatchStatus.MISSING_IN_BOOKS,
                                onClick = { selectedFilter = Gstr2bMatchStatus.MISSING_IN_BOOKS },
                                label = { AutoShrinkText(TerminologyManager.getTerm("In Portal Only"), maxFontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = selectedFilter == Gstr2bMatchStatus.MISSING_IN_2B,
                                onClick = { selectedFilter = Gstr2bMatchStatus.MISSING_IN_2B },
                                label = { AutoShrinkText(TerminologyManager.getTerm("At Risk"), maxFontSize = 12.sp) }
                            )
                        }
                    }

                    // Filtered List
                    val filteredResults = s.results.filter {
                        selectedFilter == null || it.status == selectedFilter
                    }

                    if (filteredResults.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(TerminologyManager.getTerm("No records under selected filter"), color = Color.White.copy(alpha = 0.5f))
                            }
                        }
                    } else {
                        items(filteredResults) { item ->
                            ReconciliationItemCard(item)
                        }
                    }
                } else if (!isProcessing) {
                    // Empty state guide
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(GlassFillDeep)
                                .border(1.dp, GlassStroke.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Cyan.copy(alpha = 0.7f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "How to Reconcile GSTR-2B",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "1. Download your GSTR-2B JSON return from gst.gov.in\n2. Tap 'Import 2B JSON' above\n3. Teller automatically cross-references every supplier invoice with your purchase register to safeguard your Input Tax Credit.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    amount: Double,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlassFill)
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        AutoShrinkText(title, color = Color.White.copy(alpha = 0.7f), maxFontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        AutoShrinkText(
            "₹${"%,.2f".format(amount)}",
            color = accentColor,
            fontWeight = FontWeight.Bold,
            maxFontSize = 16.sp
        )
        AutoShrinkText("$count invoices", color = Color.White.copy(alpha = 0.5f), maxFontSize = 11.sp, minFontSize = 8.sp)
    }
}

@Composable
private fun ReconciliationItemCard(result: Gstr2bMatchResult) {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val badgeColor = when (result.status) {
        Gstr2bMatchStatus.EXACT_MATCH -> Emerald
        Gstr2bMatchStatus.VALUE_MISMATCH -> Amber
        Gstr2bMatchStatus.MISSING_IN_BOOKS -> Cyan
        Gstr2bMatchStatus.MISSING_IN_2B -> Rose
    }
    val badgeLabel = when (result.status) {
        Gstr2bMatchStatus.EXACT_MATCH -> "100% MATCHED"
        Gstr2bMatchStatus.VALUE_MISMATCH -> "VALUE MISMATCH"
        Gstr2bMatchStatus.MISSING_IN_BOOKS -> "MISSING IN BOOKS"
        Gstr2bMatchStatus.MISSING_IN_2B -> "AT RISK (NOT IN 2B)"
    }

    val invNo = result.twoBInvoice?.invoiceNumber ?: result.purchaseVoucher?.voucher?.voucherNumber ?: "N/A"
    val gstin = result.twoBInvoice?.gstin ?: "Recorded in App"
    val tradeName = result.twoBInvoice?.tradeName ?: "Purchase Voucher #${result.purchaseVoucher?.voucher?.voucherNumber}"
    val taxAmount = result.twoBInvoice?.totalTax ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassFill)
            .border(1.dp, GlassStroke, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AutoShrinkText(tradeName, color = Color.White, fontWeight = FontWeight.Bold, maxFontSize = 14.sp)
                AutoShrinkText("GSTIN: $gstin", color = Color.White.copy(alpha = 0.6f), maxFontSize = 12.sp, style = TextStyle(fontFamily = FontFamily.Monospace))
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                AutoShrinkText(badgeLabel, color = badgeColor, maxFontSize = 10.sp, minFontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AutoShrinkText("Inv: $invNo", color = Cyan, maxFontSize = 12.sp, style = TextStyle(fontFamily = FontFamily.Monospace))
            if (taxAmount > 0) {
                AutoShrinkText("Tax/ITC: ₹${"%,.2f".format(taxAmount)}", color = Color.White, maxFontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (result.notes != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(result.notes, color = Amber, fontSize = 11.sp)
        }
    }
}
