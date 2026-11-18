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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.accounting.*
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.ui.viewmodels.LedgerViewModel
import com.minhas.teller.ui.viewmodels.LedgerViewModelFactory
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import com.minhas.teller.utils.TerminologyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

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

enum class ReconViewTab {
    ALL, MATCHED, NOT_IN_BOOKS, NOT_IN_BANK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankReconciliationScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }

    val ledgerRepository = remember { LedgerRepository(database.ledgerDao()) }
    val ledgerViewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(ledgerRepository))
    val allLedgers by ledgerViewModel.allLedgers.collectAsState()

    val voucherRepository = remember {
        VoucherRepository(
            database.voucherDao(),
            database.voucherEntryDao(),
            database.inventoryEntryDao(),
            database.billAllocationDao(),
            database.auditLogDao(),
            database.ledgerDao(),
            db = database
        )
    }
    val voucherViewModel: VoucherViewModel = viewModel(
        factory = VoucherViewModelFactory(
            database.voucherDao(),
            database.voucherEntryDao(),
            database.inventoryEntryDao(),
            database.ledgerDao(),
            database.billAllocationDao(),
            database.auditLogDao(),
            db = database
        )
    )

    var selectedLedgerId by remember { mutableStateOf<Int?>(null) }
    var reconReport by remember { mutableStateOf<FullReconciliationReport?>(null) }
    var processing by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(ReconViewTab.ALL) }

    val bankLedgers = remember(allLedgers) {
        allLedgers.filter { it.group.contains("Bank", ignoreCase = true) }
    }

    val bookEntries by voucherViewModel.vouchersForSelectedLedger.collectAsState()

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val lid = selectedLedgerId
            if (lid == null) {
                Toast.makeText(context, "Please select a bank account first", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            processing = true
            scope.launch {
                try {
                    val parsedTxns = withContext(Dispatchers.Default) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            UniversalBankParser.parseCsv(stream)
                        } ?: emptyList()
                    }

                    if (parsedTxns.isEmpty()) {
                        Toast.makeText(context, "No transactions found in file", Toast.LENGTH_LONG).show()
                    } else {
                        val currentBook = bookEntries
                        val res = AutomatedReconciler.reconcile(parsedTxns, currentBook, lid)
                        reconReport = res
                        Toast.makeText(context, "Parsed ${parsedTxns.size} bank transactions", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    processing = false
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
                    title = TerminologyManager.getTerm("Auto-Bank Reconciliation"),
                    canNavigateBack = true,
                    navigateUp = { navController.navigateUp() }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Setup & Account Picker Card
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
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Cyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = Cyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Select Bank Account",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Matches debit & credit entries automatically",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        var expanded by remember { mutableStateOf(false) }
                        val selectedLedgerName = bankLedgers.find { it.id == selectedLedgerId }?.name ?: "Tap to choose Bank Ledger"

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = selectedLedgerName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Cyan,
                                    unfocusedBorderColor = GlassStroke,
                                    focusedContainerColor = GlassFillDeep,
                                    unfocusedContainerColor = GlassFillDeep
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(Color(0xFF1E1744))
                            ) {
                                if (bankLedgers.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { AutoShrinkText(TerminologyManager.getTerm("No Bank Ledgers Found"), color = Color.White.copy(alpha = 0.6f)) },
                                        onClick = {}
                                    )
                                }
                                bankLedgers.forEach { ledger ->
                                    DropdownMenuItem(
                                        text = { AutoShrinkText(ledger.name, color = Color.White) },
                                        onClick = {
                                            selectedLedgerId = ledger.id
                                            expanded = false
                                            voucherViewModel.getVouchersForLedger(ledger.id)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (selectedLedgerId == null) {
                                        Toast.makeText(context, "Please select a Bank Ledger first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        csvLauncher.launch(arrayOf("text/*", "text/csv", "*/*"))
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = selectedLedgerId != null && !processing,
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color(0xFF0F1A12))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(TerminologyManager.getTerm("Upload Statement CSV"), color = Color(0xFF0F1A12), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (processing) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Cyan)
                        }
                    }
                }

                val report = reconReport
                if (report != null) {
                    // Stat Overview Cards
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Reconciliation Results",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ReconStatCard(
                                    title = "Fully Matched",
                                    count = report.matched.size,
                                    accentColor = Emerald,
                                    modifier = Modifier.weight(1f)
                                )
                                ReconStatCard(
                                    title = "Not in Books",
                                    count = report.missingInBooks.size,
                                    accentColor = Rose,
                                    modifier = Modifier.weight(1f)
                                )
                                ReconStatCard(
                                    title = "Not in Bank",
                                    count = report.unclearedInBank.size,
                                    accentColor = Amber,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Filter Chips Row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = currentTab == ReconViewTab.ALL,
                                onClick = { currentTab = ReconViewTab.ALL },
                                label = { AutoShrinkText(TerminologyManager.getTerm("All"), maxFontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = currentTab == ReconViewTab.MATCHED,
                                onClick = { currentTab = ReconViewTab.MATCHED },
                                label = { AutoShrinkText("Matched (${report.matched.size})", maxFontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = currentTab == ReconViewTab.NOT_IN_BOOKS,
                                onClick = { currentTab = ReconViewTab.NOT_IN_BOOKS },
                                label = { AutoShrinkText("Not in Books (${report.missingInBooks.size})", maxFontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = currentTab == ReconViewTab.NOT_IN_BANK,
                                onClick = { currentTab = ReconViewTab.NOT_IN_BANK },
                                label = { AutoShrinkText("Not in Bank (${report.unclearedInBank.size})", maxFontSize = 12.sp) }
                            )
                        }
                    }

                    // Matched Items
                    if (currentTab == ReconViewTab.ALL || currentTab == ReconViewTab.MATCHED) {
                        items(report.matched) { item ->
                            MatchedItemCard(item)
                        }
                    }

                    // Not in Books Items
                    if (currentTab == ReconViewTab.ALL || currentTab == ReconViewTab.NOT_IN_BOOKS) {
                        items(report.missingInBooks) { btx ->
                            MissingInBooksCard(btx)
                        }
                    }

                    // Not in Bank Items
                    if (currentTab == ReconViewTab.ALL || currentTab == ReconViewTab.NOT_IN_BANK) {
                        items(report.unclearedInBank) { vw ->
                            UnclearedInBankCard(vw, selectedLedgerId ?: 0)
                        }
                    }
                } else if (!processing) {
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
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = Cyan.copy(alpha = 0.7f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "How Auto-Bank Reconciliation Works",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "1. Select your Bank Account ledger\n2. Upload statement CSV downloaded from internet banking (HDFC, ICICI, SBI, Axis, Kotak, Chase, Standard)\n3. Teller automatically reconciles deposits & withdrawals against your journal and voucher entries.",
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
private fun ReconStatCard(
    title: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlassFill)
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AutoShrinkText(
            text = "$count",
            color = accentColor,
            fontWeight = FontWeight.Bold,
            maxFontSize = 20.sp
        )
        AutoShrinkText(
            text = title,
            color = Color.White.copy(alpha = 0.7f),
            maxFontSize = 11.sp,
            minFontSize = 8.sp
        )
    }
}

@Composable
private fun MatchedItemCard(item: AutoReconResult) {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val badgeLabel = if (item.matchLevel == ReconciliationMatchLevel.EXACT) "100% MATCHED" else "PROBABLE MATCH"
    val badgeColor = Emerald

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassFill)
            .border(1.dp, GlassStroke, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutoShrinkText(sdf.format(item.bankTx.date), color = Cyan, maxFontSize = 12.sp, style = TextStyle(fontFamily = FontFamily.Monospace))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                AutoShrinkText(badgeLabel, color = badgeColor, maxFontSize = 10.sp, minFontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.bankTx.description,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            AutoShrinkText(
                text = "₹${"%,.2f".format(item.bankTx.amount)}",
                color = if (item.bankTx.isDeposit) Emerald else Rose,
                fontWeight = FontWeight.Bold,
                maxFontSize = 15.sp
            )
        }

        if (item.matchedVoucher != null) {
            Spacer(modifier = Modifier.height(4.dp))
            AutoShrinkText(
                "Book Voucher: ${item.matchedVoucher.voucher.type} #${item.matchedVoucher.voucher.voucherNumber}",
                color = Color.White.copy(alpha = 0.6f),
                maxFontSize = 11.sp,
                minFontSize = 8.sp
            )
        }
    }
}

@Composable
private fun MissingInBooksCard(tx: ParsedBankTransaction) {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassFill)
            .border(1.dp, Rose.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutoShrinkText(sdf.format(tx.date), color = Cyan, maxFontSize = 12.sp, style = TextStyle(fontFamily = FontFamily.Monospace))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Rose.copy(alpha = 0.15f))
                    .border(1.dp, Rose.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                AutoShrinkText(TerminologyManager.getTerm("NOT IN BOOKS"), color = Rose, maxFontSize = 10.sp, minFontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tx.description,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            AutoShrinkText(
                text = "₹${"%,.2f".format(tx.amount)}",
                color = if (tx.isDeposit) Emerald else Rose,
                fontWeight = FontWeight.Bold,
                maxFontSize = 15.sp
            )
        }
    }
}

@Composable
private fun UnclearedInBankCard(vw: VoucherWithAllEntries, bankLedgerId: Int) {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val entry = vw.voucherEntries.firstOrNull { it.ledgerId == bankLedgerId }
    val amount = entry?.amount ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassFill)
            .border(1.dp, Amber.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutoShrinkText(sdf.format(vw.voucher.date), color = Cyan, maxFontSize = 12.sp, style = TextStyle(fontFamily = FontFamily.Monospace))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Amber.copy(alpha = 0.15f))
                    .border(1.dp, Amber.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                AutoShrinkText(TerminologyManager.getTerm("NOT IN BANK"), color = Amber, maxFontSize = 10.sp, minFontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${vw.voucher.type} #${vw.voucher.voucherNumber} ${vw.voucher.narration ?: ""}",
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            AutoShrinkText(
                text = "₹${"%,.2f".format(amount)}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxFontSize = 15.sp
            )
        }
    }
}
