package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.ui.viewmodels.LedgerViewModel
import com.minhas.teller.ui.viewmodels.LedgerViewModelFactory
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.SessionManager
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.data.entities.Company

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSummaryScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val repository = LedgerRepository(database.ledgerDao())
    val viewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(repository))
    val allLedgers by viewModel.allLedgers.collectAsState()
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

    val groups = remember(allLedgers) {
        allLedgers.map { it.group }.distinct().sorted()
    }

    Scaffold(
        topBar = {
            TellerTopAppBar(
                title = "Group Summary",
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
                                val headers = listOf("Group Name", "Ledgers", "Total Balance")
                                val data = groups.map { groupName ->
                                    val groupLedgers = allLedgers.filter { it.group == groupName }
                                    val totalBal = groupLedgers.sumOf { it.openingBalance }.paiseToRupees()
                                    listOf(groupName, groupLedgers.size.toString(), "₹ %.2f".format(totalBal))
                                }
                                ExportUtils.exportReportToPdf(context, "GroupSummary.pdf", companyName, "Group Summary", headers, floatArrayOf(2f, 1f, 1.5f), data)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm("Export to Excel")) },
                            onClick = {
                                showExportMenu = false
                                val headers = listOf("Group Name", "Ledgers", "Total Balance")
                                val data = groups.map { groupName ->
                                    val groupLedgers = allLedgers.filter { it.group == groupName }
                                    val totalBal = groupLedgers.sumOf { it.openingBalance }.paiseToRupees()
                                    listOf(groupName, groupLedgers.size.toString(), "₹ %.2f".format(totalBal))
                                }
                                ExportUtils.exportReportToExcel(context, "GroupSummary.xlsx", "Group Summary", headers, data)
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp, vertical = 16.dp)
        ) {
            if (groups.isEmpty()) {
                Text(TerminologyManager.getTerm("No groups found."))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
                ) {
                    items(groups) { groupName ->
                        val groupLedgers = remember(allLedgers, groupName) {
                            allLedgers.filter { it.group == groupName }
                        }
                        val totalBalance = remember(groupLedgers) {
                            groupLedgers.sumOf { it.openingBalance }.paiseToRupees()
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    navController.navigate(Screen.GroupReport.route.replace("{groupName}", groupName))
                                }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                AutoShrinkText(text = groupName, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    AutoShrinkText(
                                        text = "${groupLedgers.size} ledger${if (groupLedgers.size != 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        minFontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    AutoShrinkText(
                                        text = "Total Bal: ₹ %.2f".format(totalBalance),
                                        style = MaterialTheme.typography.bodySmall,
                                        minFontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
