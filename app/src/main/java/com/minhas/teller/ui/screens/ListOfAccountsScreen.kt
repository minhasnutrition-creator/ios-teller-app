package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.ui.viewmodels.LedgerViewModel
import com.minhas.teller.ui.viewmodels.LedgerViewModelFactory

import androidx.compose.foundation.clickable
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.TellerTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListOfAccountsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val repository = LedgerRepository(database.ledgerDao())
    val viewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(repository))
    val allLedgers by viewModel.allLedgers.collectAsState()

    Scaffold(
        topBar = {
            TellerTopAppBar(
                title = "List of Accounts",
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
            if (allLedgers.isEmpty()) {
                Text(TerminologyManager.getTerm("No ledgers found."))
            } else {
                LazyColumn {
                    items(allLedgers) { ledger ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    navController.navigate(Screen.LedgerViewReport.route.replace("{ledgerId}", ledger.id.toString()))
                                }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = ledger.name, style = MaterialTheme.typography.titleMedium)
                                Text(text = "Group: ${ledger.group}", style = MaterialTheme.typography.bodySmall)
                                Text(text = "Balance: ${ledger.openingBalance}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
