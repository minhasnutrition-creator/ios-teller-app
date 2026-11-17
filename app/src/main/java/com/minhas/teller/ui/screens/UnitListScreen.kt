package com.minhas.teller.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Unit as TellerUnit
import com.minhas.teller.data.repositories.UnitRepository
import com.minhas.teller.data.entities.Unit
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.viewmodels.UnitViewModel
import com.minhas.teller.ui.viewmodels.UnitViewModelFactory
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.SessionManager
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.data.entities.Company
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitListScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val repository = UnitRepository(database.unitDao())
    val viewModel: UnitViewModel = viewModel(factory = UnitViewModelFactory(repository))
    val units by viewModel.allUnits.collectAsState()
    var showExportMenu by remember { mutableStateOf(false) }

    val companyRepository = CompanyRepository(database.companyDao())
    val companyViewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(companyRepository))
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val currentCompany by produceState<Company?>(initialValue = null, currentCompanyId) {
        currentCompanyId?.let { id ->
            companyViewModel.getCompany(id).collect { value = it }
        }
    }
    val coroutineScope = rememberCoroutineScope()
    // Seed common measurement units if they don't exist for the current company
    LaunchedEffect(currentCompanyId) {
        currentCompanyId?.let { companyId ->
            coroutineScope.launch {
                val defaultUnitNames = listOf(
                    "Dozen", "Pack", "Case", "Liter", "Pound", "Ounce", "Meter",
                    "Square Meter", "Cubic Meter", "KG", "Gram", "Pcs"
                )
                defaultUnitNames.forEach { unitName ->
                    // Avoid duplicate insert
                    val existing = repository.getUnitByName(companyId, unitName)
                    if (existing == null) {
                        repository.insert(
                            Unit(
                                name = unitName,
                                companyId = companyId
                            )
                        )
                    }
                }
            }
        }
    }}

@Composable
fun UnitItem(unit: TellerUnit, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = unit.name, style = MaterialTheme.typography.headlineSmall)
            unit.formalName?.let {
                Text(text = "Formal Name: $it")
            }
        }
    }
}