package com.minhas.teller.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.viewmodels.LedgerViewModel
import com.minhas.teller.ui.viewmodels.LedgerViewModelFactory
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.SessionManager
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.ui.viewmodels.CompanyViewModel
import com.minhas.teller.ui.viewmodels.CompanyViewModelFactory
import com.minhas.teller.data.repositories.AccountGroupRepository
import com.minhas.teller.ui.viewmodels.AccountGroupViewModel
import com.minhas.teller.ui.viewmodels.AccountGroupViewModelFactory
import com.minhas.teller.data.entities.Company
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.utils.LanguageType
import com.minhas.teller.utils.paiseToRupees

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerListScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val repository = LedgerRepository(database.ledgerDao())
    val viewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(repository))
    val ledgers by viewModel.allLedgers.collectAsState()
    
    val groupRepository = AccountGroupRepository(database.accountGroupDao())
    val groupViewModel: AccountGroupViewModel = viewModel(factory = AccountGroupViewModelFactory(groupRepository))
    val allGroups by groupViewModel.allGroups.collectAsState()
    
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

    var showDialog by remember { mutableStateOf(false) }
    var selectedLedger by remember { mutableStateOf<Ledger?>(null) }
    
    // Search & Filter State
    var searchQuery by remember { mutableStateOf("") }
    var showFilterMenu by remember { mutableStateOf(false) }
    var selectedGroupFilter by remember { mutableStateOf("All Groups") }

    val filteredLedgers = remember(ledgers, searchQuery, selectedGroupFilter) {
        ledgers.filter { ledger ->
            val matchesSearch = ledger.name.contains(searchQuery, ignoreCase = true)
            val matchesGroup = if (selectedGroupFilter == "All Groups") true else ledger.group == selectedGroupFilter
            matchesSearch && matchesGroup
        }
    }

    if (showDialog && selectedLedger != null) {
        LedgerActionsDialog(
            onDismissRequest = { showDialog = false },
            onAlter = {
                showDialog = false
                navController.navigate(Screen.AddEditLedger.route.replace("{ledgerId}", selectedLedger!!.id.toString()))
            },
            onDelete = {
                showDialog = false
                viewModel.delete(selectedLedger!!)
            }
        )
    }

    // Gradient colors for glassmorphism background
    val gradientColors = listOf(
        Color(0xFF1A0A2E), // Deep purple
        Color(0xFF16213E), // Navy blue
        Color(0xFF0F3460), // Teal blue
        Color(0xFF1A0A2E)  // Back to deep purple
    )
    
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddEditLedger.route.replace("{ledgerId}", "-1")) },
                containerColor = Color(0xFF7C4DFF),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, "Add new ledger")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = gradientColors,
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 5.dp, end = 5.dp, top = 16.dp, bottom = 16.dp)
            ) {
                // Top Bar with Back, Title, Home, Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    
                    Text(
                        text = TerminologyManager.getTerm("Ledgers"),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Row {
                        IconButton(onClick = { navController.navigate(Screen.Dashboard.route) }) {
                            Icon(
                                imageVector = Icons.Filled.Home,
                                contentDescription = "Home",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = Color.White
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                // Compact tab: Customers/Companies | Groups
                com.minhas.teller.ui.components.AccountsTabToggle(
                    navController = navController,
                    ledgersSelected = true,
                    unselectedTextColor = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(10.dp))
                
                // Search Bar with Filter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search ${TerminologyManager.getTerm("Ledgers")}...", color = Color.White.copy(0.6f)) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color.White.copy(0.7f)) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color.White.copy(0.1f),
                            focusedContainerColor = Color.White.copy(0.15f),
                            unfocusedBorderColor = Color.White.copy(0.2f),
                            focusedBorderColor = Color(0xFF7C4DFF),
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    IconButton(
                        onClick = { showFilterMenu = true },
                        modifier = Modifier
                            .background(
                                color = if (selectedGroupFilter != "All Groups") Color(0xFF7C4DFF) else Color.White.copy(0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = "Filter",
                            tint = Color.White
                        )
                    }
                }
                
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(TerminologyManager.getTerm("All Groups")) },
                        onClick = { selectedGroupFilter = "All Groups"; showFilterMenu = false }
                    )
                    allGroups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm(group.name)) },
                            onClick = { selectedGroupFilter = group.name; showFilterMenu = false }
                        )
                    }
                }
                
                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(TerminologyManager.getTerm("Export to PDF")) },
                        onClick = {
                            showExportMenu = false
                            val headers = listOf("Name", "Group", "Opening Balance")
                            val data = filteredLedgers.map { ledger ->
                                listOf(
                                    ledger.name,
                                    TerminologyManager.getTerm(ledger.group),
                                    ledger.openingBalance.toString()
                                )
                            }
                            ExportUtils.exportReportToPdf(context, "LedgerList.pdf", companyName, "Ledger List", headers, floatArrayOf(3f, 2f, 2f), data)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(TerminologyManager.getTerm("Export to Excel")) },
                        onClick = {
                            showExportMenu = false
                            val headers = listOf("Name", "Group", "Opening Balance")
                            val data = filteredLedgers.map { ledger ->
                                listOf(
                                    ledger.name,
                                    TerminologyManager.getTerm(ledger.group),
                                    ledger.openingBalance.toString()
                                )
                            }
                            ExportUtils.exportReportToExcel(context, "LedgerList.xlsx", "Ledger List", headers, data)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                if (filteredLedgers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (ledgers.isEmpty()) "No ledgers found.\nClick '+' to add one." else "No matches found.",
                            color = Color.White.copy(0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
                    ) {
                        items(filteredLedgers) { ledger ->
                            LedgerItem(
                                ledger = ledger,
                                viewModel = viewModel,
                                onClick = {
                                    navController.navigate(Screen.LedgerViewReport.route.replace("{ledgerId}", ledger.id.toString()))
                                },
                                onLongClick = {
                                    selectedLedger = ledger
                                    showDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerItem(
    ledger: Ledger, 
    viewModel: LedgerViewModel,
    onClick: () -> Unit, 
    onLongClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    
    // Calculate running closing balance
    val balanceState by viewModel.getClosingBalance(ledger.id, java.util.Date()).collectAsState(initial = ledger.openingBalance)
    val balance = balanceState ?: ledger.openingBalance
    
    // Determine icon and color based on ledger group/name
    val (icon, accentColor) = when {
        ledger.group.contains("Cash", ignoreCase = true) || 
        ledger.name.contains("Cash", ignoreCase = true) -> 
            Pair(Icons.Filled.AttachMoney, Color(0xFFFFA726)) // Orange for Cash
        ledger.group.contains("Purchase", ignoreCase = true) || 
        ledger.name.contains("Purchase", ignoreCase = true) -> 
            Pair(Icons.Filled.ShoppingCart, Color(0xFF42A5F5)) // Blue for Purchase
        ledger.group.contains("Sales", ignoreCase = true) || 
        ledger.name.contains("Sales", ignoreCase = true) -> 
            Pair(Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF66BB6A)) // Green for Sales
        ledger.group.contains("Expense", ignoreCase = true) || 
        ledger.name.contains("Expense", ignoreCase = true) -> 
            Pair(Icons.AutoMirrored.Filled.TrendingDown, Color(0xFFEF5350)) // Red for Expense
        else -> Pair(Icons.AutoMirrored.Filled.MenuBook, Color(0xFF7C4DFF)) // Purple default
    }
    
    val cardBackground = Color.White.copy(alpha = 0.08f)

    // Respect the app-wide language mode: SIMPLE = plain language, ACCOUNTING = Dr/Cr
    val currentLanguage by TerminologyManager.currentLanguage.collectAsState()
    val isSimple = currentLanguage != LanguageType.ACCOUNTING

    // "Owes you / You owe" only makes sense for customer & supplier accounts.
    // Cash, bank, tax and other accounts keep a neutral balance label.
    val isPartyLedger = ledger.group.equals("Sundry Debtors", ignoreCase = true) ||
        ledger.group.equals("Sundry Creditors", ignoreCase = true)

    val balanceRupees = balance.paiseToRupees()
    val amountAbs = "₹%,.0f".format(Math.abs(balanceRupees))

    // Format the balance text, status label and color.
    val balanceText: String
    val statusLabel: String
    val balanceColor: Color
    if (isSimple) {
        when {
            isPartyLedger && balanceRupees > 0 -> {
                balanceText = amountAbs; statusLabel = "Owes you"; balanceColor = Color(0xFF4CAF50)
            }
            isPartyLedger && balanceRupees < 0 -> {
                balanceText = amountAbs; statusLabel = "You owe"; balanceColor = Color(0xFFF44336)
            }
            isPartyLedger -> {
                balanceText = "₹0"; statusLabel = "All settled"; balanceColor = Color.White.copy(alpha = 0.7f)
            }
            balance != 0L -> {
                balanceText = amountAbs; statusLabel = "Balance"; balanceColor = Color.White.copy(alpha = 0.9f)
            }
            else -> {
                balanceText = "₹0"; statusLabel = ""; balanceColor = Color.White.copy(alpha = 0.7f)
            }
        }
    } else {
        when {
            balanceRupees > 0 -> { balanceText = "%.2f Dr".format(balanceRupees); statusLabel = ""; balanceColor = Color(0xFF4CAF50) }
            balanceRupees < 0 -> { balanceText = "%.2f Cr".format(Math.abs(balanceRupees)); statusLabel = ""; balanceColor = Color(0xFFF44336) }
            else -> { balanceText = "0.00"; statusLabel = ""; balanceColor = Color.White.copy(alpha = 0.8f) }
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left colored accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(60.dp)
                    .background(accentColor, shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Icon and Ledger Info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Icon in circular background
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = accentColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Ledger Name and Group
                    Column {
                        AutoShrinkText(
                            text = ledger.name,
                            color = Color.White,
                            maxFontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        AutoShrinkText(
                            text = "Group: ${TerminologyManager.getTerm(ledger.group)}",
                            color = Color.White.copy(alpha = 0.6f),
                            maxFontSize = 11.sp,
                            minFontSize = 8.sp
                        )
                    }
                }

                // Right side: Balance (amount + plain-language status in Simple mode)
                Column(horizontalAlignment = Alignment.End) {
                    AutoShrinkText(
                        text = balanceText,
                        color = balanceColor,
                        maxFontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (statusLabel.isNotEmpty()) {
                        AutoShrinkText(
                            text = statusLabel,
                            color = balanceColor.copy(alpha = 0.85f),
                            maxFontSize = 10.sp,
                            minFontSize = 7.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerActionsDialog(
    onDismissRequest: () -> Unit,
    onAlter: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(TerminologyManager.getTerm("Actions")) },
        text = { Text(TerminologyManager.getTerm("What would you like to do with this ledger?")) },
        confirmButton = {
            TextButton(onClick = onAlter) {
                Text(TerminologyManager.getTerm("Alter"))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(TerminologyManager.getTerm("Delete"), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismissRequest) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        }
    )
}