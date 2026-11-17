package com.minhas.teller.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.theme.*
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.ui.viewmodels.CaScrutinyViewModel
import com.minhas.teller.ui.viewmodels.CaScrutinyViewModelFactory
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaScrutinyScreen(navController: NavController) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val voucherRepository = remember {
        VoucherRepository(db.voucherDao(), db.voucherEntryDao(), db.inventoryEntryDao(), db.billAllocationDao(), db.auditLogDao(), db.ledgerDao(), db = db)
    }
    val ledgerRepository = remember { LedgerRepository(db.ledgerDao()) }
    val factory = remember { CaScrutinyViewModelFactory(db, voucherRepository, ledgerRepository) }
    val viewModel: CaScrutinyViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    var expandedChecks by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editingVoucher by remember { mutableStateOf<com.minhas.teller.data.entities.VoucherWithAllEntries?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val companyId by SessionManager.currentCompanyId.collectAsState(initial = 1)
    val targetCompanyId = companyId ?: 1

    // Initial Date Range: This Financial Year
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.MONTH, Calendar.APRIL)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    val fromDate = calendar.timeInMillis
    val toDate = System.currentTimeMillis()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, targetCompanyId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadComplianceData(targetCompanyId, fromDate, toDate)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun shareZipFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, TerminologyManager.getTerm("Share CA Audit Pack")))
        } catch (e: Exception) {
            Toast.makeText(context, "${TerminologyManager.getTerm("Sharing failed")}: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText(TerminologyManager.getTerm("CA & Scrutiny Center"), color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = TerminologyManager.getTerm("Back"), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F182E))
            )
        },
        containerColor = Color(0xFF0F182E)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F182E),
                            Color(0xFF1E2D4A)
                        )
                    )
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Pre-Audit Score Header
                item {
                    val scoreColor = when {
                        uiState.auditScore >= 800 -> Color(0xFF69F0AE)
                        uiState.auditScore >= 650 -> Color(0xFFFFF59D)
                        else -> Color(0xFFEF9A9A)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(TerminologyManager.getTerm("AUDIT READINESS RATING"), color = Color(0xFFB8A0D0), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = uiState.auditScore.toString(),
                                    color = scoreColor,
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = TerminologyManager.getTerm(" / 1000"),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val statusText = when {
                                uiState.auditScore >= 800 -> TerminologyManager.getTerm("CA-READY (No blocking compliance issues)")
                                uiState.auditScore >= 650 -> TerminologyManager.getTerm("GOOD (Minor audit issues warning)")
                                else -> TerminologyManager.getTerm("REVIEW REQUIRED (Critical legal / cash breaches)")
                            }
                            Text(statusText, color = scoreColor, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                }

                // Section 2: On-Device pre-check items
                item {
                    Text(
                        text = TerminologyManager.getTerm("Compliance Audit Watchdog"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB8A0D0)
                    )
                }

                items(uiState.preChecks) { check ->
                    val isExpanded = expandedChecks.contains(check.title)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (check.isPass) Color(0xFF1B2A3E).copy(alpha = 0.4f) else Color(0xFF2C1B2A).copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, if (check.isPass) Color(0xFF69F0AE).copy(alpha = 0.2f) else Color(0xFFEF9A9A).copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !check.isPass) {
                                expandedChecks = if (isExpanded) {
                                    expandedChecks - check.title
                                } else {
                                    expandedChecks + check.title
                                }
                            }
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(check.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(check.statusText, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, lineHeight = 14.sp)
                                    if (!check.isPass && !isExpanded) {
                                        Text(TerminologyManager.getTerm("Tap to review details & correct"), color = Color(0xFFCE93D8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (check.isPass) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (check.isPass) Color(0xFF69F0AE) else Color(0xFFEF9A9A),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    if (!check.isPass) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(20.dp).padding(start = 4.dp)
                                        )
                                    }
                                }
                            }
                            
                            if (isExpanded && check.failedVouchers.isNotEmpty()) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 14.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    check.failedVouchers.forEach { failedVoucher ->
                                        val vId = failedVoucher.voucherId
                                        val displayText = failedVoucher.displayText
                                        val reason = failedVoucher.reason
                                        val suggestion = failedVoucher.suggestion
                                        val stockItemId = failedVoucher.stockItemId

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White.copy(alpha = 0.03f))
                                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                                .clickable {
                                                    if (stockItemId != null && stockItemId > 0) {
                                                        // Missing HSN/tax code — open the item in Inventory to set it there
                                                        navController.navigate(Screen.AddEditStockItem.route.replace("{stockItemId}", stockItemId.toString()))
                                                    } else {
                                                        coroutineScope.launch {
                                                            val v = voucherRepository.getVoucherWithAllEntriesById(vId)
                                                            if (v != null) {
                                                                editingVoucher = v
                                                            }
                                                        }
                                                    }
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = displayText,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(TerminologyManager.getTerm("QUICK FIX"), color = Color(0xFF40C4FF), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = TerminologyManager.getTerm("Quick Edit"),
                                                        tint = Color(0xFF40C4FF),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                            if (reason.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "${TerminologyManager.getTerm("Issue")}: $reason",
                                                    color = Color(0xFFEF9A9A),
                                                    fontSize = 11.sp,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                            if (suggestion.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${TerminologyManager.getTerm("Suggestion")}: $suggestion",
                                                    color = Color(0xFF81C784),
                                                    fontSize = 10.sp,
                                                    lineHeight = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }

                // Section 3: Package Export Actions
                item {
                    Text(
                        text = TerminologyManager.getTerm("Generate Shared Compliance Packages"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB8A0D0)
                    )
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(TerminologyManager.getTerm("1. One-Tap CA Export Pack"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(TerminologyManager.getTerm("Packages Trial Balance, P&L, Balance Sheet, and key ledgers in a single structured Excel + PDF format ready for your CA's ingestion tool."), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)

                            Button(
                                onClick = {
                                    viewModel.generateCaPack(context) { file ->
                                        shareZipFile(file)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(TerminologyManager.getTerm("Generate CA Zip Pack"), color = Color(0xFF0F182E), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(TerminologyManager.getTerm("2. Prepare Scrutiny Pack (Evidence Loop)"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(TerminologyManager.getTerm("Creates a secure archive linking all vouchers, AuditLog logs, and attached scan receipts/invoices. Used to resolve income tax notices without search delays."), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(TerminologyManager.getTerm("Linked Invoices Scans:"), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                Text("${uiState.attachmentsCount} ${TerminologyManager.getTerm("files found")}", color = Color(0xFFCE93D8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    viewModel.generateScrutinyPack(context) { file ->
                                        shareZipFile(file)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF40C4FF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(TerminologyManager.getTerm("Prepare Scrutiny Evidence Pack"), color = Color(0xFF0F182E), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (uiState.isGenerating) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFFCE93D8))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(TerminologyManager.getTerm("Packaging sheets and verifying hashes..."), color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (editingVoucher != null) {
        com.minhas.teller.ui.components.VoucherEditDialog(
            voucherWithAllEntries = editingVoucher!!,
            allLedgers = uiState.allLedgers,
            onDismissRequest = { editingVoucher = null },
            onSave = { voucher, entries, inventoryEntries ->
                viewModel.updateVoucher(voucher, entries, inventoryEntries)
            }
        )
    }
}
