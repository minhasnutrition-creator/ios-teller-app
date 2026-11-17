package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Unit
import com.minhas.teller.data.repositories.UnitRepository
import com.minhas.teller.ui.viewmodels.UnitViewModel
import com.minhas.teller.ui.viewmodels.UnitViewModelFactory
import com.minhas.teller.SessionManager
import com.minhas.teller.ui.components.TellerTopAppBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditUnitScreen(navController: NavHostController, unitId: Int = -1) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)

    val unitRepository = remember { UnitRepository(database.unitDao()) }
    val unitViewModel: UnitViewModel = viewModel(factory = UnitViewModelFactory(unitRepository))
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var formalName by remember { mutableStateOf("") }

    var isNameError by remember { mutableStateOf(false) }

    // Load unit data if in edit mode
    LaunchedEffect(unitId) {
        if (unitId != -1) {
            unitRepository.getUnitById(unitId)?.let { unit ->
                name = unit.name
                formalName = unit.formalName ?: ""
            }
        }
    }

    Scaffold(
        topBar = {
            TellerTopAppBar(
                title = "Unit Details",
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    isNameError = false // Clear error on change
                },
                label = { Text(TerminologyManager.getTerm("Name")) },
                isError = isNameError,
                supportingText = { if (isNameError) Text(TerminologyManager.getTerm("Name cannot be empty")) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = formalName,
                onValueChange = { formalName = it },
                label = { Text(TerminologyManager.getTerm("Formal Name")) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isNameError = name.isBlank()

                    if (isNameError) {
                        Toast.makeText(context, "Please correct the errors.", Toast.LENGTH_SHORT).show()
                    } else {
                        coroutineScope.launch {
                            val companyId = SessionManager.currentCompanyId.value ?: return@launch
                            val unit = Unit(
                                id = if (unitId != -1) unitId else 0,
                                name = name,
                                formalName = formalName.ifBlank { null },
                                companyId = companyId
                            )
                            if (unitId == -1) {
                                unitViewModel.insert(unit)
                                Toast.makeText(context, "Unit added", Toast.LENGTH_SHORT).show()
                            } else {
                                unitViewModel.update(unit)
                                Toast.makeText(context, "Unit updated", Toast.LENGTH_SHORT).show()
                            }
                            navController.popBackStack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (unitId == -1) "Save Unit" else "Update Unit")
            }
        }
    }
}
