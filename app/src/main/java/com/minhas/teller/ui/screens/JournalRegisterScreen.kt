package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.paiseToRupees
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import java.text.SimpleDateFormat
import java.util.Locale
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.utils.ExportUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import com.minhas.teller.SessionManager
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.data.entities.Company

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalRegisterScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val voucherViewModel: VoucherViewModel = viewModel(factory = VoucherViewModelFactory(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.ledgerDao(), database.billAllocationDao(), database.auditLogDao(), db = database))

    val companyRepository = CompanyRepository(database.companyDao())
    val companyViewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(companyRepository))

    val allVouchers by voucherViewModel.allVouchersWithAllEntries.collectAsState()
    val journalVouchers = remember(allVouchers) {
        allVouchers.filter { it.voucher.type == "Journal" }
    }
    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val currentCompany by produceState<Company?>(initialValue = null, currentCompanyId) {
        currentCompanyId?.let { id ->
            companyViewModel.getCompany(id).collect { value = it }
        }
    }
    val companyName = currentCompany?.name ?: "Teller App"

    var showExportMenu by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var selectedVoucher by remember { mutableStateOf<VoucherWithAllEntries?>(null) }

    if (showDialog && selectedVoucher != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(TerminologyManager.getTerm("Voucher Options")) },
            text = { Text(TerminologyManager.getTerm("What would you like to do with this voucher?")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        navController.navigate(Screen.AddEditVoucher.route + "?voucherId=${selectedVoucher!!.voucher.id}")
                    }
                ) {
                    Text(TerminologyManager.getTerm("Edit"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        navController.navigate(Screen.VoucherView.route.replace("{voucherId}", selectedVoucher!!.voucher.id.toString()))
                    }
                ) {
                    Text(TerminologyManager.getTerm("View"))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TellerTopAppBar(
                title = "Journal Register",
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
                .padding(horizontal = 8.dp, vertical = 16.dp)
        ) {
            // Export Actions moved from Top Bar
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { showExportMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Export")
                }
                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(TerminologyManager.getTerm("Export to PDF")) },
                        onClick = {
                            showExportMenu = false
                            val headers = listOf("Date", "Vch No", "Particulars", "Amount")
                            val data = journalVouchers.map { voucherWithAllEntries ->
                                val totalAmount = voucherWithAllEntries.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                                // Simplified Particulars for list view
                                val particulars = "Journal Entry" 
                                listOf(
                                    dateFormatter.format(voucherWithAllEntries.voucher.date),
                                    voucherWithAllEntries.voucher.voucherNumber,
                                    particulars,
                                    "%.2f".format(totalAmount)
                                )
                            }
                            ExportUtils.exportReportToPdf(context, "JournalRegister.pdf", companyName, "Journal Register", headers, floatArrayOf(1.5f, 1.5f, 3f, 2f), data)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(TerminologyManager.getTerm("Export to Excel")) },
                        onClick = {
                            showExportMenu = false
                            val headers = listOf("Date", "Vch No", "Particulars", "Amount")
                            val data = journalVouchers.map { voucherWithAllEntries ->
                                val totalAmount = voucherWithAllEntries.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                                val particulars = "Journal Entry"
                                listOf(
                                    dateFormatter.format(voucherWithAllEntries.voucher.date),
                                    voucherWithAllEntries.voucher.voucherNumber,
                                    particulars,
                                    "%.2f".format(totalAmount)
                                )
                            }
                            ExportUtils.exportReportToExcel(context, "JournalRegister.xlsx", "Journal Register", headers, data)
                        }
                    )
                }
            }

            if (journalVouchers.isEmpty()) {
                Text(TerminologyManager.getTerm("No Journal vouchers found."))
            } else {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(TerminologyManager.getTerm("Date"), modifier = Modifier.weight(1.5f).padding(8.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                    Text(TerminologyManager.getTerm("Vch No"), modifier = Modifier.weight(1.5f).padding(8.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                    Text(TerminologyManager.getTerm("Particulars"), modifier = Modifier.weight(3f).padding(8.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                    Text(TerminologyManager.getTerm("Amount"), modifier = Modifier.weight(2f).padding(8.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, fontSize = 12.sp)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
                ) {
                    items(journalVouchers) { voucherWithAllEntries ->
                        val totalAmount = voucherWithAllEntries.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                        val particulars = "Journal Entry" // Simplified, real app would list accounts

                        Column(modifier = Modifier.clickable {
                            selectedVoucher = voucherWithAllEntries
                            showDialog = true
                        }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateFormatter.format(voucherWithAllEntries.voucher.date),
                                    modifier = Modifier.weight(1.5f).padding(8.dp),
                                    fontSize = 12.sp
                                )
                                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                                Text(
                                    text = voucherWithAllEntries.voucher.voucherNumber,
                                    modifier = Modifier.weight(1.5f).padding(8.dp),
                                    fontSize = 12.sp
                                )
                                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                                Text(
                                    text = particulars,
                                    modifier = Modifier.weight(3f).padding(8.dp),
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                                Text(
                                    text = "%.2f".format(totalAmount),
                                    modifier = Modifier.weight(2f).padding(8.dp),
                                    textAlign = TextAlign.End,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}