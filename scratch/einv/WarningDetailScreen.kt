package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.viewmodels.DashboardAttentionViewModel
import com.minhas.teller.ui.viewmodels.DashboardAttentionViewModelFactory
import com.minhas.teller.ui.viewmodels.WarningVoucher
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.WarningsPrefs
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** Open WhatsApp with a payment reminder (with UPI link if a business UPI is set). */
private fun sendReminder(context: android.content.Context, v: WarningVoucher) {
    try {
        val upi = context.getSharedPreferences("payment_prefs", android.content.Context.MODE_PRIVATE).getString("upi_id", "") ?: ""
        val companyName = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE).getString("company_name", "Teller App") ?: "Teller App"
        val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val amt = "%.2f".format(v.pendingAmount)
        val name = v.customerName ?: "customer"
        val message = if (upi.isNotBlank()) {
            "Hello $name, a payment of ₹$amt is pending since ${dateFmt.format(v.date)}. Please pay using this link: upi://pay?pa=$upi&pn=${android.net.Uri.encode(companyName)}&am=$amt&cu=INR&tn=Bill-${v.voucherNumber}"
        } else {
            "Hello $name, a payment of ₹$amt is pending for invoice ${v.voucherNumber}."
        }
        val phone = v.phoneNumber?.filter { it.isDigit() }.orEmpty()
        val base = if (phone.isNotBlank()) "https://wa.me/$phone?text=" else "https://wa.me/?text="
        val uri = android.net.Uri.parse(base + android.net.Uri.encode(message))
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "WhatsApp not installed", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarningDetailScreen(navController: NavHostController, warningId: String) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)

    val voucherRepository = remember {
        VoucherRepository(db.voucherDao(), db.voucherEntryDao(), db.inventoryEntryDao(), db.billAllocationDao(), db.auditLogDao(), db.ledgerDao(), db = db)
    }
    val ledgerRepository = remember { LedgerRepository(db.ledgerDao()) }
    val attentionFactory = remember { DashboardAttentionViewModelFactory(voucherRepository, ledgerRepository) }
    val attentionViewModel: DashboardAttentionViewModel = viewModel(factory = attentionFactory)
    val attentionState by attentionViewModel.state.collectAsState()

    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()

    LaunchedEffect(currentCompanyId) {
        currentCompanyId?.let { attentionViewModel.loadAttentionItems(it) }
    }

    val item = attentionState.items.firstOrNull { it.id == warningId }
    val fallbackTitle = if (warningId == "einvoice_pending") "E-Invoices" else null
    val fallbackDescription = if (warningId == "einvoice_pending")
        "Sales bills that still need to be sent to the government as an e-invoice." else null

    var vouchers by remember { mutableStateOf<List<WarningVoucher>>(emptyList()) }
    LaunchedEffect(currentCompanyId, warningId) {
        currentCompanyId?.let { attentionViewModel.getWarningVouchers(it, warningId) { vouchers = it } }
    }

    val scope = rememberCoroutineScope()
    var bulkSending by remember { mutableStateOf(false) }
    var bulkResultMsg by remember { mutableStateOf<String?>(null) }

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText(item?.title ?: fallbackTitle ?: "Warning", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F1A12))
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0F1A12), Color(0xFF060D40))))
                .padding(padding)
                .padding(16.dp)
        ) {
            // What's wrong
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item?.description ?: fallbackDescription ?: "", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("${item?.count ?: vouchers.size} item(s) affected", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // How to fix
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF69F0AE).copy(alpha = 0.08f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF69F0AE), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(TerminologyManager.getTerm("How to fix"), color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text(
                        item?.fixHint ?: fallbackDescription.takeIf { warningId == "einvoice_pending" }?.let {
                            "Open each bill below and tap Send, or use \"Send all now\" to send every pending bill in one go."
                        } ?: "",
                        color = Color.White, fontSize = 14.sp
                    )
                }
            }

            if (warningId == "einvoice_pending") {
                // Explains, in plain words, whether e-invoicing applies and why each bill is or isn't listed.
                var eStatus by remember { mutableStateOf<com.minhas.teller.utils.EInvoiceStore.Status?>(null) }
                var eBreakdown by remember { mutableStateOf<com.minhas.teller.utils.EInvoiceStore.Breakdown?>(null) }
                var eRefresh by remember { mutableStateOf(0) }
                LaunchedEffect(currentCompanyId, eRefresh) {
                    val cid = currentCompanyId ?: return@LaunchedEffect
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val all = db.voucherDao().getAllVouchersWithAllEntries(cid)
                        val ledgers = ledgerRepository.getAllLedgersSync(cid)
                        val st = com.minhas.teller.utils.EInvoiceStore.status(cid, all, ledgers, context)
                        val bd = st.fromMillis?.let { com.minhas.teller.utils.EInvoiceStore.breakdown(cid, all, ledgers, it, context) }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { eStatus = st; eBreakdown = bd }
                    }
                }
                eStatus?.let { st ->
                    val rupees: (Double) -> String = { "₹ " + String.format(Locale.US, "%,.0f", it) }
                    Spacer(Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val from = st.fromMillis
                            if (from == null) {
                                val msg = if (st.reason == com.minhas.teller.utils.EInvoiceStore.Reason.CROSSED_THIS_YEAR) {
                                    "Your sales this year (FY ${st.biggestFy}) are ${rupees(st.biggestTurnover)} — above ₹ 5 crore. " +
                                        "E-invoices become compulsory from ${dateFmt.format(java.util.Date(st.compulsoryFromMillis ?: System.currentTimeMillis()))}. You can start now."
                                } else {
                                    "Your highest yearly sales are ${rupees(st.biggestTurnover)}" +
                                        (if (st.biggestFy.isNotBlank()) " (FY ${st.biggestFy})" else "") +
                                        ", below ₹ 5 crore, so e-invoices are not compulsory for you. Start only if your CA or a customer asks for them."
                                }
                                Text(msg, color = Color.White, fontSize = 14.sp)
                                Button(
                                    onClick = {
                                        val cid = currentCompanyId ?: return@Button
                                        com.minhas.teller.utils.EInvoiceStore.startNow(cid, context)
                                        eRefresh++
                                        attentionViewModel.getWarningVouchers(cid, warningId) { vouchers = it }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE))
                                ) {
                                    Text(
                                        TerminologyManager.getTerm(if (st.reason == com.minhas.teller.utils.EInvoiceStore.Reason.CROSSED_THIS_YEAR) "Start now" else "Start anyway"),
                                        color = Color(0xFF0F1A12), fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text("E-invoicing is on for bills from ${dateFmt.format(java.util.Date(from))}.", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                eBreakdown?.let { b ->
                                    Text("• ${b.pending} bill(s) to send", color = Color.White, fontSize = 13.sp)
                                    if (b.alreadySent > 0) Text("• ${b.alreadySent} already sent", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                    if (b.b2c > 0) Text("• ${b.b2c} bill(s) are to customers without a GSTIN (cash, finance or walk-in). These don't need an e-invoice.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                    if (b.b2bMissingGstin > 0) Text("• ${b.b2bMissingGstin} bill(s) are marked B2B but the customer has no GSTIN saved. Add the GSTIN in that customer's account and they will appear here.", color = Color(0xFFFFC107), fontSize = 13.sp)
                                }
                            }
                            Text(
                                TerminologyManager.getTerm("A bill counts as B2B (needs an e-invoice) when the customer's account on it has a GSTIN, or when it is an export."),
                                color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            if (warningId == "einvoice_pending" && vouchers.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val companyId = currentCompanyId ?: return@Button
                        bulkSending = true
                        bulkResultMsg = null
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val company = db.companyDao().getCompanyByIdSync(companyId)
                            val allLedgers = ledgerRepository.getAllLedgersSync(companyId)
                            val stockMap = db.stockItemDao().getAllStockItems(companyId).associateBy { it.id }
                            val unitsMap = db.unitDao().getAllUnits(companyId).associateBy { it.id }
                            val items = mutableListOf<com.minhas.teller.utils.GstEwayBillExporter.BulkEInvoiceItem>()
                            val noAddress = mutableListOf<String>()
                            vouchers.forEach { wv ->
                                val vwe = db.voucherDao().getVoucherWithAllEntriesById(wv.voucherId)
                                val ledger = vwe?.let { com.minhas.teller.utils.EInvoiceStore.buyerLedger(it, allLedgers) }
                                val saved = ledger?.let { com.minhas.teller.utils.EInvoiceStore.partyDetails(it.id, context) }
                                if (vwe == null || ledger == null || saved == null) {
                                    noAddress += wv.voucherNumber
                                } else {
                                    items += com.minhas.teller.utils.GstEwayBillExporter.BulkEInvoiceItem(vwe, ledger, saved.address, saved.place, saved.pincode)
                                }
                            }
                            val result = company?.let {
                                com.minhas.teller.utils.GstEwayBillExporter.generateBulkEInvoiceFiles(it, items, stockMap, unitsMap)
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                bulkSending = false
                                val readyFiles = result?.readyFiles.orEmpty()
                                if (readyFiles.isNotEmpty()) {
                                    com.minhas.teller.utils.GstEwayBillExporter.shareJsonFiles(context, readyFiles)
                                }
                                val skippedLines = (result?.skipped.orEmpty().map { "${it.first}: ${it.second.joinToString(", ")}" }) +
                                    noAddress.map { "$it: no saved customer address yet — open it once and tap Send JSON to add one" }
                                bulkResultMsg = "Sent ${readyFiles.size} of ${vouchers.size}." +
                                    (if (skippedLines.isNotEmpty()) "\n\nSkipped:\n" + skippedLines.joinToString("\n") else "")
                            }
                        }
                    },
                    enabled = !bulkSending,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE))
                ) {
                    if (bulkSending) {
                        CircularProgressIndicator(color = Color(0xFF0F1A12), modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF0F1A12))
                        Spacer(Modifier.width(8.dp))
                        Text("${TerminologyManager.getTerm("Send all")} ${vouchers.size} ${TerminologyManager.getTerm("now")}", color = Color(0xFF0F1A12), fontWeight = FontWeight.Bold)
                    }
                }
                bulkResultMsg?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(msg, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Specific entries
            Text(TerminologyManager.getTerm("Affected entries"), color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            if (vouchers.isEmpty()) {
                Text(TerminologyManager.getTerm("No individual entries to list here. Follow the fix above, or open the related screen from the dashboard."),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(vouchers, key = { "${it.voucherNumber}-${it.date.time}" }) { v ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (warningId == "einvoice_pending") {
                                        navController.navigate(Screen.EInvoice.createRoute(v.voucherId))
                                    } else {
                                        navController.navigate(Screen.VoucherView.route.replace("{voucherId}", v.voucherId.toString()))
                                    }
                                }
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = v.customerName?.takeIf { it.isNotBlank() } ?: v.voucherNumber,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1
                                        )
                                        val detail = listOf(v.voucherNumber, v.type, dateFmt.format(v.date))
                                            .filter { it.isNotBlank() }
                                            .joinToString(" · ")
                                        Text(detail, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1)
                                    }
                                    Text(
                                        text = "₹ ${"%,.2f".format(v.pendingAmount)}",
                                        color = Color(0xFFFF5252),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                if (warningId == "overdue") {
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                navController.navigate(Screen.AddEditVoucher.route + "?voucherId=-1&type=Receipt")
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE))
                                        ) {
                                            Text(TerminologyManager.getTerm("Record payment"), color = Color(0xFF0F1A12), fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { sendReminder(context, v) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(TerminologyManager.getTerm("Remind"), color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Ignore button
            Button(
                onClick = {
                    WarningsPrefs.ignore(context, warningId)
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(TerminologyManager.getTerm("Ignore this warning"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
