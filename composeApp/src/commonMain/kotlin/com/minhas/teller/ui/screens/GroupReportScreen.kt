package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.ui.viewmodels.LedgerViewModel
import com.minhas.teller.ui.viewmodels.LedgerViewModelFactory
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.SessionManager
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.data.entities.Company

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupReportScreen(navController: NavHostController, groupName: String) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val repository = LedgerRepository(database.ledgerDao())
    val viewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(repository))
    val ledgers by viewModel.allLedgers.collectAsState()
    var showExportMenu by remember { mutableStateOf(false) }

    val companyRepository = CompanyRepository(database.companyDao())
    val companyViewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(companyRepository))
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val currentCompany by produceState<Company?>(initialValue = null, currentCompanyId) {
        currentCompanyId?.let { id ->
            companyViewModel.getCompany(id).collect { value = it }
        }
    }
    val companyName = currentCompany?.name ?: "Teller App"


    val ledgersInGroup = ledgers.filter { it.group == groupName }

    Scaffold(
        topBar = {
            TellerTopAppBar(
                title = "Group Report: $groupName",
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
        ) {
            // Export Actions moved from Top Bar
            androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
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
                            val headers = listOf("Ledger Name", "Group")
                            val data = ledgersInGroup.map { ledger ->
                                listOf(
                                    ledger.name,
                                    ledger.group
                                )
                            }
                            ExportUtils.exportReportToPdf(context, "GroupReport_${groupName}.pdf", companyName, "Group Report: $groupName", headers, floatArrayOf(3f, 2f), data)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(TerminologyManager.getTerm("Export to Excel")) },
                        onClick = {
                            showExportMenu = false
                            val headers = listOf("Ledger Name", "Group")
                            val data = ledgersInGroup.map { ledger ->
                                listOf(
                                    ledger.name,
                                    ledger.group
                                )
                            }
                            ExportUtils.exportReportToExcel(context, "GroupReport_${groupName}.xlsx", "Group Report", headers, data)
                        }
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
            ) {
                items(ledgersInGroup) { ledger ->
                    LedgerItem(
                        ledger = ledger,
                        viewModel = viewModel,
                        onClick = {
                            // Navigate to individual ledger report
                            navController.navigate("ledger_view_report/${ledger.id}")
                        },
                        onLongClick = {
                            // Can add alter/delete from here too if needed
                        }
                    )
                }
            }
        }
    }
}
