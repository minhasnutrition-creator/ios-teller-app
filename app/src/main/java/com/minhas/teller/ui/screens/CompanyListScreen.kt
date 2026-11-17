package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.utils.LicenseUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CompanyListScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val repository = CompanyRepository(database.companyDao())
    val viewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(repository))
    val companies by viewModel.allCompanies.collectAsState(initial = emptyList())

    var showOptionsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedCompany by remember { mutableStateOf<Company?>(null) }

    // Check License Status on Entry
    LaunchedEffect(Unit) {
        val (status, _) = LicenseUtils.getAppStatus(context)
        if (status == LicenseUtils.AppStatus.TRIAL_EXPIRED) {
            navController.navigate("license?reason=expired") {
                popUpTo(0)
            }
        }
    }

    // 1. Long-press Action Popup: Edit or Remove
    if (showOptionsDialog && selectedCompany != null) {
        val company = selectedCompany!!
        AlertDialog(
            onDismissRequest = {
                showOptionsDialog = false
                selectedCompany = null
            },
            title = {
                Text(company.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Text(
                    TerminologyManager.getTerm("What would you like to do with this company?"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showOptionsDialog = false
                            showDeleteConfirmDialog = true
                        }
                    ) {
                        Text(
                            TerminologyManager.getTerm("Remove"),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(
                        onClick = {
                            showOptionsDialog = false
                            selectedCompany = null
                            SessionManager.setCompanyId(company.id)
                            navController.navigate(Screen.EditCompany.route)
                        }
                    ) {
                        Text(TerminologyManager.getTerm("Edit"), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOptionsDialog = false
                        selectedCompany = null
                    }
                ) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        )
    }

    // 2. Removal Confirmation Dialog
    if (showDeleteConfirmDialog && selectedCompany != null) {
        val company = selectedCompany!!
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false 
                selectedCompany = null
            },
            title = {
                Text(
                    TerminologyManager.getTerm("Remove Company?"),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    TerminologyManager.getTerm("Are you sure you want to remove whole company data for '${company.name}'? All accounting vouchers, inventory data, and transactions will be permanently lost. This action cannot be undone.")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.delete(company)
                        if (SessionManager.currentCompanyId.value == company.id) {
                            SessionManager.clearSession()
                        }
                        selectedCompany = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(TerminologyManager.getTerm("Remove Permanently"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeleteConfirmDialog = false 
                        selectedCompany = null
                    }
                ) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = "Select Company",
                canNavigateBack = false,
                showHome = false
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Screen.CreateCompany.route) },
                icon = { Icon(Icons.Default.Add, contentDescription = "Create Company") },
                text = { Text(TerminologyManager.getTerm("Create Account")) }
            )
        }
    ) { paddingValues ->
        if (companies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text(TerminologyManager.getTerm("No companies found. Create one to start."))
            }
        } else {
            LazyColumn(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                items(companies) { company ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .combinedClickable(
                                onClick = {
                                    SessionManager.setCompanyId(company.id)
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.CompanyList.route) { inclusive = true }
                                    }
                                },
                                onLongClick = {
                                    selectedCompany = company
                                    showOptionsDialog = true
                                }
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = company.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Fin Year From: ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(company.financialYearFrom))}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
