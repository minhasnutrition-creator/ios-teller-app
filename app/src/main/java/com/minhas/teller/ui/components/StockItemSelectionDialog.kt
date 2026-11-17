package com.minhas.teller.ui.components

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.ui.viewmodels.StockItemViewModel
import com.minhas.teller.ui.viewmodels.StockItemViewModelFactory
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockItemSelectionDialog(
    onDismissRequest: () -> Unit,
    onStockItemSelected: (StockItem) -> Unit,
    onCreateNewClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val repository = StockItemRepository(database.stockItemDao())
    val viewModel: StockItemViewModel = viewModel(factory = StockItemViewModelFactory(repository))
    val stockItems by viewModel.allStockItems.collectAsState()

    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Select Stock Item", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(modifier = Modifier.fillMaxHeight(0.7f)) {
                    if (onCreateNewClick != null) {
                        item {
                            Text(
                                text = "+ Add New Item",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onCreateNewClick()
                                        onDismissRequest()
                                    }
                                    .padding(vertical = 12.dp)
                            )
                            HorizontalDivider()
                        }
                    }

                    if (stockItems.isEmpty() && onCreateNewClick == null) {
                        item { Text(TerminologyManager.getTerm("No stock items found.")) }
                    } else {
                        items(stockItems) { stockItem ->
                            Text(
                                text = stockItem.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onStockItemSelected(stockItem)
                                        onDismissRequest()
                                    }
                                    .padding(vertical = 8.dp)
                            )
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        }
    }
}