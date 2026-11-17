package com.minhas.teller.ui.components

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.ui.viewmodels.LedgerViewModel
import com.minhas.teller.ui.viewmodels.LedgerViewModelFactory
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerSelectionDialog(
    onDismissRequest: () -> Unit,
    onLedgerSelected: (Ledger) -> Unit,
    onCreateNewClick: (() -> Unit)? = null,
    filter: ((Ledger) -> Boolean)? = null,
    title: String? = null
) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val repository = LedgerRepository(database.ledgerDao())
    val viewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(repository))
    val ledgers by viewModel.allLedgers.collectAsState()
    
    val filteredLedgers = remember(ledgers, filter) {
        if (filter != null) ledgers.filter(filter) else ledgers
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = title ?: "Select Ledger", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(modifier = Modifier.fillMaxHeight(0.7f)) {
                    if (onCreateNewClick != null) {
                        item {
                            Text(
                                text = "+ Create New Ledger",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onCreateNewClick()
                                        onDismissRequest()
                                    }
                                    .padding(vertical = 12.dp)
                                    .padding(horizontal = 4.dp)
                            )
                            HorizontalDivider()
                        }
                    }

                    items(filteredLedgers) { ledger ->
                        Text(
                            text = ledger.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLedgerSelected(ledger)
                                    onDismissRequest()
                                }
                                .padding(vertical = 12.dp)
                                .padding(horizontal = 4.dp)
                        )
                        HorizontalDivider()
                    }
                }
                
                if (filteredLedgers.isEmpty() && onCreateNewClick == null) {
                    Text(TerminologyManager.getTerm("No ledgers found."), modifier = Modifier.padding(vertical = 16.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        }
    }
}