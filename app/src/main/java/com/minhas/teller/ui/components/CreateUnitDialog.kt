package com.minhas.teller.ui.components

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Unit as TellerUnit
import com.minhas.teller.data.repositories.UnitRepository
import com.minhas.teller.ui.viewmodels.UnitViewModel
import com.minhas.teller.ui.viewmodels.UnitViewModelFactory

@Composable
fun CreateUnitDialog(
    onDismissRequest: () -> Unit,
    onUnitCreated: (TellerUnit) -> Unit
) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val repository = UnitRepository(database.unitDao())
    val viewModel: UnitViewModel = viewModel(factory = UnitViewModelFactory(repository))

    var name by remember { mutableStateOf("") }
    var formalName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(TerminologyManager.getTerm("Create Unit"), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(TerminologyManager.getTerm("Symbol (e.g. Pcs)")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = formalName,
                    onValueChange = { formalName = it },
                    label = { Text(TerminologyManager.getTerm("Formal Name (e.g. Pieces)")) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismissRequest) { Text(TerminologyManager.getTerm("Cancel")) }
                    Button(onClick = {
                        if (name.isNotBlank()) {
                            val unit = TellerUnit(name = name, formalName = formalName)
                            viewModel.insert(unit) { id ->
                                onUnitCreated(unit.copy(id = id.toInt()))
                            }
                        }
                    }) { Text(TerminologyManager.getTerm("Create")) }
                }
            }
        }
    }
}
