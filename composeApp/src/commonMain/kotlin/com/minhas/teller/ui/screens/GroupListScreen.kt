package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.minhas.teller.data.repositories.AccountGroupRepository
import com.minhas.teller.ui.viewmodels.AccountGroupViewModel
import com.minhas.teller.ui.viewmodels.AccountGroupViewModelFactory
import com.minhas.teller.ui.components.TellerTopAppBar // Not used, but keeping imports consistent
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.SessionManager
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.data.entities.Company
import com.minhas.teller.ui.theme.GlobalCharcoal
import com.minhas.teller.ui.theme.GlobalLavender
import com.minhas.teller.ui.theme.GlobalMintGreen
import com.minhas.teller.ui.theme.GlobalNeonCyan
import com.minhas.teller.ui.theme.GlobalPaleBlue
import com.minhas.teller.ui.theme.GlobalPaleYellow
import com.minhas.teller.ui.theme.GlobalPastelOrange
import com.minhas.teller.ui.theme.GlobalSalmonRed
import com.minhas.teller.data.entities.AccountGroup
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.ui.components.SearchAndFilterBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(navController: NavHostController, currentAppTheme: AppTheme) { // Added theme parameter
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val repository = AccountGroupRepository(database.accountGroupDao())
    val viewModel: AccountGroupViewModel = viewModel(factory = AccountGroupViewModelFactory(repository))
    val groups by viewModel.allGroups.collectAsState()
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

    // Fill in any missing default groups (handles both brand-new companies and
    // companies that were created before default-group seeding existed).
    LaunchedEffect(currentCompanyId) {
        currentCompanyId?.let { viewModel.initDefaultGroups(it) }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    
    // Search State
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredGroups = remember(groups, searchQuery) {
        if (searchQuery.isBlank()) groups
        else groups.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(TerminologyManager.getTerm("Add Group"), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text(TerminologyManager.getTerm("Group Name")) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newGroupName.isNotBlank()) {
                        viewModel.insert(newGroupName, "Primary")
                        showAddDialog = false
                        newGroupName = ""
                    }
                }) {
                    Text(TerminologyManager.getTerm("Add"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = "Groups",
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, "Add Group")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Compact tab: Customers/Companies | Groups
                com.minhas.teller.ui.components.AccountsTabToggle(
                    navController = navController,
                    ledgersSelected = false
                )

                // Export Button relocated from Top Bar to screen content
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Export", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                    ) {
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm("Export to PDF"), color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showExportMenu = false
                                val headers = listOf("Name", "Parent Group")
                                val data = groups.map { group ->
                                    listOf(
                                        group.name,
                                        group.parentGroup ?: ""
                                    )
                                }
                                ExportUtils.exportReportToPdf(context, "GroupList.pdf", companyName, "Group List", headers, floatArrayOf(2f, 2f), data)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm("Export to Excel"), color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                showExportMenu = false
                                val headers = listOf("Name", "Parent Group")
                                val data = groups.map { group ->
                                    listOf(
                                        group.name,
                                        group.parentGroup ?: ""
                                    )
                                }
                                ExportUtils.exportReportToExcel(context, "GroupList.xlsx", "Group List", headers, data)
                            }
                        )
                    }
                }
                
                // Search Bar
                SearchAndFilterBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    placeholder = "Search Groups..."
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredGroups) { group ->
                        ThemeAwareGroupItem(group, currentAppTheme)
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeAwareGroupItem(group: AccountGroup, currentAppTheme: AppTheme) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
    
    when (currentAppTheme) {
        AppTheme.CYBER_FINANCE -> {
            Card(
                modifier = cardModifier
                    .background(GlobalCharcoal, RoundedCornerShape(8.dp))
                    .border(2.dp, GlobalNeonCyan, RoundedCornerShape(8.dp)), // Example neon border
                colors = CardDefaults.cardColors(containerColor = Color.Transparent) // Use Transparent to let background/border be the main color
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = group.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontFamily = FontFamily.Monospace)
                    Text(text = "Parent: ${group.parentGroup}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
                }
            }
        }
        AppTheme.BENTO -> {
            // Pastel colors for Bento cards
            val cardColor = remember { listOf(GlobalSalmonRed, GlobalPaleBlue, GlobalPaleYellow, GlobalPastelOrange, GlobalMintGreen, GlobalLavender).random() }
            Card(
                modifier = cardModifier
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(cardColor),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = group.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "Parent: ${group.parentGroup}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
        AppTheme.CRYSTAL -> {
            Card(
                modifier = cardModifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f)) // White glass effect
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .drawBehind { // BackdropFilter simulation (visual only)
                        drawRect(Color.White.copy(alpha = 0.1f))
                    },
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = group.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(text = "Parent: ${group.parentGroup}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
        else -> { // DEFAULT Theme
            Card(
                modifier = cardModifier,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = group.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "Parent: ${group.parentGroup}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
