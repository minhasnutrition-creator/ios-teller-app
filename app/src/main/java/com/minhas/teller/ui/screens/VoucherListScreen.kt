package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import java.text.SimpleDateFormat
import java.util.Locale
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.SessionManager
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.data.entities.Company

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherListScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val voucherViewModel: VoucherViewModel = viewModel(factory = VoucherViewModelFactory(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.ledgerDao(), database.billAllocationDao(), database.auditLogDao(), db = database))
    val vouchers by voucherViewModel.allVouchersWithAllEntries.collectAsState()
    var showExportMenu by remember { mutableStateOf(false) }
    val dateFormatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    val companyRepository = CompanyRepository(database.companyDao())
    val companyViewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(companyRepository))
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val currentCompany by produceState<Company?>(initialValue = null, currentCompanyId) {
        currentCompanyId?.let { id ->
            companyViewModel.getCompany(id).collect { value = it }
        }
    }
    val companyName = currentCompany?.name ?: "Teller"

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
                title = "Vouchers",
                canNavigateBack = true,
                navigateUp = { navController.popBackStack() },
                actions = {
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
                                val headers = listOf("Date", "Vch No", "Type", "Amount")
                                val data = vouchers.map { voucherWithEntries ->
                                    val v = voucherWithEntries.voucher
                                    val totalAmount = voucherWithEntries.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                                    listOf(
                                        dateFormatter.format(v.date),
                                        v.voucherNumber,
                                        v.type,
                                        "%.2f".format(totalAmount)
                                    )
                                }
                                ExportUtils.exportReportToPdf(context, "VoucherList.pdf", companyName, "Voucher List", headers, floatArrayOf(2f, 2f, 2f, 2f), data)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm("Export to Excel")) },
                            onClick = {
                                showExportMenu = false
                                val headers = listOf("Date", "Vch No", "Type", "Amount")
                                val data = vouchers.map { voucherWithEntries ->
                                    val v = voucherWithEntries.voucher
                                    val totalAmount = voucherWithEntries.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                                    listOf(
                                        dateFormatter.format(v.date),
                                        v.voucherNumber,
                                        v.type,
                                        "%.2f".format(totalAmount)
                                    )
                                }
                                ExportUtils.exportReportToExcel(context, "VoucherList.xlsx", "Voucher List", headers, data)
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.AddEditVoucher.route + "?voucherId=-1") }) {
                Icon(Icons.Filled.Add, "Add new voucher")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 8.dp, vertical = 16.dp)) {
            if (vouchers.isEmpty()) {
                Text(TerminologyManager.getTerm("No vouchers found. Click '+' to add one."))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
                ) {
                    items(vouchers) { voucher ->
                        VoucherItem(voucher = voucher) {
                            selectedVoucher = voucher
                            showDialog = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoucherItem(voucher: com.minhas.teller.data.entities.VoucherWithAllEntries, onClick: (VoucherWithAllEntries) -> Unit) {
    val dateFormatter = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    val totalAmount = voucher.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick(voucher) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "${voucher.voucher.type} - ${voucher.voucher.voucherNumber}", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Date: ${dateFormatter.format(voucher.voucher.date)}")
            Text(text = "Amount: $totalAmount")
            // Add more details if needed
        }
    }
}