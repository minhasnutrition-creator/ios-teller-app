package com.minhas.teller.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import com.minhas.teller.ui.components.StylishButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.utils.rupeesToPaise
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.ui.viewmodels.LedgerViewModel
import com.minhas.teller.ui.viewmodels.LedgerViewModelFactory
import com.minhas.teller.data.repositories.AccountGroupRepository
import com.minhas.teller.ui.viewmodels.AccountGroupViewModel
import com.minhas.teller.ui.viewmodels.AccountGroupViewModelFactory
import com.minhas.teller.ui.components.ValidatedNumberTextField
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.utils.GstStateCodes
import com.minhas.teller.SessionManager
import com.minhas.teller.ui.components.TellerTopAppBar
import kotlinx.coroutines.launch

import com.minhas.teller.ui.components.ModernTextField
import com.minhas.teller.ui.components.ModernDropdownField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLedgerScreen(navController: NavHostController, ledgerId: Int = -1) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val database = AppDatabase.getDatabase(context)
    
    val repository = remember { LedgerRepository(database.ledgerDao()) }
    val viewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(repository))
    
    val groupRepository = remember { AccountGroupRepository(database.accountGroupDao()) }
    val groupViewModel: AccountGroupViewModel = viewModel(factory = AccountGroupViewModelFactory(groupRepository))
    val groupsList by groupViewModel.allGroups.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var openingBalance by remember { mutableStateOf("0.0") }
    var openingIsCredit by remember { mutableStateOf(false) }
    var maintainBillWise by remember { mutableStateOf(false) }
    var phoneNumber by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    var stateCode by remember { mutableStateOf("") }

    var isNameError by remember { mutableStateOf(false) }
    var isGroupError by remember { mutableStateOf(false) }

    val selectedLedger by viewModel.selectedLedger.collectAsState()

    // Fill in any missing default groups (handles both brand-new companies and
    // companies that were created before default-group seeding existed).
    LaunchedEffect(Unit) {
        SessionManager.currentCompanyId.value?.let { groupViewModel.initDefaultGroups(it) }
    }

    // Load ledger data if in edit mode
    LaunchedEffect(ledgerId) {
        if (ledgerId != -1) {
            viewModel.loadLedger(ledgerId)
        }
    }

    LaunchedEffect(selectedLedger) {
        selectedLedger?.let { ledger ->
            name = ledger.name
            alias = ledger.alias ?: ""
            group = ledger.group
            openingBalance = kotlin.math.abs(ledger.openingBalance.paiseToRupees()).toString()
            openingIsCredit = ledger.openingBalance < 0
            maintainBillWise = ledger.maintainBillWise
            phoneNumber = ledger.phoneNumber ?: ""
            gstin = ledger.gstin ?: ""
            stateCode = ledger.stateCode ?: ""
        }
    }

    Scaffold(
        topBar = {
            TellerTopAppBar(
                title = "Ledger Details",
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModernTextField(
                value = name,
                onValueChange = {
                    name = it
                    isNameError = false
                },
                placeholder = "Name",
                modifier = Modifier.fillMaxWidth()
            )
            
            ModernTextField(
                value = alias,
                onValueChange = { alias = it },
                placeholder = "Alias",
                modifier = Modifier.fillMaxWidth()
            )

            ModernTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                placeholder = "Phone Number (for WhatsApp reminders)",
                modifier = Modifier.fillMaxWidth()
            )

            ModernTextField(
                value = gstin,
                onValueChange = { gstin = it.uppercase().take(15) },
                placeholder = "GSTIN (for GST returns)",
                modifier = Modifier.fillMaxWidth()
            )

            var isStateExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                ModernDropdownField(
                    text = GstStateCodes.nameForCode(stateCode) ?: "",
                    onClick = { focusManager.clearFocus(force = true); isStateExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "State (for GST returns)"
                )
                DropdownMenu(
                    expanded = isStateExpanded,
                    onDismissRequest = { isStateExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    GstStateCodes.stateNameByCode.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                stateCode = code
                                isStateExpanded = false
                            }
                        )
                    }
                }
            }

            var isExpanded by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxWidth()) {
                ModernDropdownField(
                    text = TerminologyManager.getTerm(group),
                    onClick = { focusManager.clearFocus(force = true); isExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Under Group"
                )
                DropdownMenu(
                    expanded = isExpanded,
                    onDismissRequest = { isExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    groupsList.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm(option.name)) },
                            onClick = {
                                group = option.name
                                isExpanded = false
                                isGroupError = false
                                
                                // Auto-enable bill-wise for specific groups
                                if (group == "Sundry Debtors" || group == "Sundry Creditors" || group == "Supplier" || group == "Finance Companies") {
                                    maintainBillWise = true
                                }
                            }
                        )
                    }
                }
            }

            ValidatedNumberTextField(
                value = openingBalance,
                onValueChange = { openingBalance = it },
                label = "Opening Balance",
                modifier = Modifier.fillMaxWidth(),
                isDecimal = true
            )
            
            // Opening balance side: Dr = asset / money owed to you; Cr = liability / money you owe.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(TerminologyManager.getTerm("Credit (Cr) opening balance (money you owe)"), modifier = Modifier.weight(1f))
                Switch(
                    checked = openingIsCredit,
                    onCheckedChange = { openingIsCredit = it }
                )
            }

            val isPartyGroup = remember(group) {
                val g = group.lowercase()
                g.contains("debtor") || g.contains("creditor") || g.contains("supplier") || 
                g.contains("customer") || g.contains("finance") || g.contains("loan") ||
                g.contains("payable") || g.contains("receivable")
            }

            if (isPartyGroup) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(TerminologyManager.getTerm("Maintain balances bill-by-bill"), modifier = Modifier.weight(1f))
                    Switch(
                        checked = maintainBillWise,
                        onCheckedChange = { maintainBillWise = it }
                    )
                }
            } else {
                // Reset to false for non-party groups
                SideEffect {
                    if (maintainBillWise) {
                        maintainBillWise = false
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            StylishButton(
                onClick = {
                    isNameError = name.isBlank()
                    isGroupError = group.isBlank()

                    if (isNameError || isGroupError) {
                        Toast.makeText(context, "Please correct the errors.", Toast.LENGTH_SHORT).show()
                    } else {
                        val balance = (openingBalance.toDoubleOrNull() ?: 0.0).let { if (openingIsCredit) -kotlin.math.abs(it) else kotlin.math.abs(it) }
                        coroutineScope.launch {
                            val companyId = SessionManager.currentCompanyId.value ?: return@launch
                            val ledger = Ledger(
                                id = if (ledgerId != -1) ledgerId else 0,
                                name = name,
                                alias = alias.ifBlank { null },
                                group = group,
                                openingBalance = balance.rupeesToPaise(),
                                maintainBillWise = maintainBillWise,
                                phoneNumber = phoneNumber.ifBlank { null },
                                companyId = companyId,
                                gstin = gstin.ifBlank { null },
                                stateCode = stateCode.ifBlank { null }
                            )
                            if (ledgerId == -1) {
                                viewModel.insert(ledger)
                                Toast.makeText(context, "Ledger added", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.update(ledger)
                                Toast.makeText(context, "Ledger updated", Toast.LENGTH_SHORT).show()
                            }
                            navController.popBackStack()
                        }
                    }
                },
                text = if (ledgerId == -1) "Save Ledger" else "Update Ledger",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
