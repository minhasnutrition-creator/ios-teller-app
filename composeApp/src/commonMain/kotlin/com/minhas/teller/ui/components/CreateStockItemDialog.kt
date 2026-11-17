package com.minhas.teller.ui.components

import android.widget.Toast
import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.minhas.teller.utils.HsnFuzzyMatcher
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Unit as TellerUnit
import com.minhas.teller.data.entities.HsnEntry
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.data.repositories.UnitRepository
import com.minhas.teller.ui.viewmodels.StockItemViewModel
import com.minhas.teller.ui.viewmodels.StockItemViewModelFactory
import com.minhas.teller.ui.viewmodels.UnitViewModel
import com.minhas.teller.ui.viewmodels.UnitViewModelFactory
import com.minhas.teller.utils.SmartTaxService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStockItemDialog(
    onDismissRequest: () -> Unit,
    onStockItemCreated: (StockItem) -> Unit
) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val stockRepository = StockItemRepository(database.stockItemDao())
    val stockViewModel: StockItemViewModel = viewModel(factory = StockItemViewModelFactory(stockRepository))
    
    val unitRepository = UnitRepository(database.unitDao())
    val unitViewModel: UnitViewModel = viewModel(factory = UnitViewModelFactory(unitRepository))
    val units by unitViewModel.allUnits.collectAsState(initial = emptyList())

    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var selectedUnitId by remember { mutableStateOf<Int?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var showCreateUnitDialog by remember { mutableStateOf(false) }
    var isNameError by remember { mutableStateOf(false) }
    var isUnitError by remember { mutableStateOf(false) }

    // HSN and Tax fields
    var hsnCode by remember { mutableStateOf("") }
    var gstRate by remember { mutableStateOf(0.0) }
    var cgstRate by remember { mutableStateOf(0.0) }
    var sgstRate by remember { mutableStateOf(0.0) }
    var igstRate by remember { mutableStateOf(0.0) }

    // Error / dropdown states
    var failedAutoAssign by remember { mutableStateOf(false) }
    var showWarning by remember { mutableStateOf(false) }

    // Inline HSN suggestions ("Did you mean this?" / "Is it either one of these?") populated on button click.
    var nameSuggestions by remember { mutableStateOf<List<SmartTaxService.TaxSuggestion>>(emptyList()) }
    var lookupConfidence by remember { mutableStateOf(HsnFuzzyMatcher.MatchConfidence.NONE) }
    var showNameSuggestions by remember { mutableStateOf(false) }
    var isMatchingHsn by remember { mutableStateOf(false) }

    // Categories and cascading picker
    val categories = remember { mutableStateOf<List<String>>(emptyList()) }
    val hsnEntriesMap = remember { mutableStateMapOf<String, List<HsnEntry>>() }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedSubCategory by remember { mutableStateOf<HsnEntry?>(null) }

    val smartTaxService = remember { SmartTaxService.getInstance(context) }
    val countryName = remember { com.minhas.teller.utils.DashboardPrefs.getTaxCountry(context) }
    val isIndia = remember { countryName.equals("India", true) }
    val taxConfig = remember { com.minhas.teller.utils.TaxConfigManager.getConfigForCountry(countryName) }
    val taxLabel = remember { taxConfig.taxName }
    val taxCodeLabel = remember { taxConfig.codeSystemName ?: "Tax Code" }
    val taxEnabled = remember { com.minhas.teller.utils.DashboardPrefs.isTaxSystemEnabled(context) }

    LaunchedEffect(Unit) {
        categories.value = com.minhas.teller.utils.HSNDataManager.getInstance(context).getCategories()
    }

    fun applyPickedTaxInfo(info: SmartTaxService.TaxInfo) {
        hsnCode = info.hsnCode
        gstRate = info.gstRate
        cgstRate = info.cgst
        sgstRate = info.sgst
        igstRate = info.igst
        showWarning = false
        failedAutoAssign = false
        showNameSuggestions = false
        lookupConfidence = HsnFuzzyMatcher.MatchConfidence.NONE
        nameSuggestions = emptyList()
        coroutineScope.launch {
            val dbEntry = smartTaxService.getHSNByCode(info.hsnCode)
            dbEntry?.let {
                selectedCategory = it.category
                selectedSubCategory = it
            }
        }
    }

    if (showCreateUnitDialog) {
        CreateUnitDialog(
            onDismissRequest = { showCreateUnitDialog = false },
            onUnitCreated = { newUnit ->
                selectedUnitId = newUnit.id
                showCreateUnitDialog = false
            }
        )
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(TerminologyManager.getTerm("Create Stock Item"), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isNameError = false
                        showNameSuggestions = false
                        lookupConfidence = HsnFuzzyMatcher.MatchConfidence.NONE
                        nameSuggestions = emptyList()
                        showWarning = false
                    },
                    label = { Text(TerminologyManager.getTerm("Item Name")) },
                    isError = isNameError,
                    supportingText = { if (isNameError) Text(TerminologyManager.getTerm("Item name is required")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Populate HSN & Tax Button — only shown when tax is enabled
                if (taxEnabled) {
                Button(
                    onClick = {
                        val query = name.trim()
                        if (query.isNotBlank()) {
                            coroutineScope.launch {
                                isMatchingHsn = true
                                val result = try {
                                    smartTaxService.lookupHsnSuggestions(query, 5)
                                } catch (e: Exception) {
                                    SmartTaxService.TaxLookupResult(HsnFuzzyMatcher.MatchConfidence.NONE, emptyList())
                                }
                                isMatchingHsn = false
                                when (result.confidence) {
                                    HsnFuzzyMatcher.MatchConfidence.EXACT_CONFIDENT -> {
                                        applyPickedTaxInfo(result.suggestions.first().taxInfo)
                                    }
                                    HsnFuzzyMatcher.MatchConfidence.DID_YOU_MEAN_ONE,
                                    HsnFuzzyMatcher.MatchConfidence.AMBIGUOUS_MULTIPLE -> {
                                        lookupConfidence = result.confidence
                                        nameSuggestions = result.suggestions
                                        showNameSuggestions = result.suggestions.isNotEmpty()
                                        showWarning = false
                                        failedAutoAssign = false
                                    }
                                    HsnFuzzyMatcher.MatchConfidence.NONE -> {
                                        showNameSuggestions = false
                                        lookupConfidence = HsnFuzzyMatcher.MatchConfidence.NONE
                                        nameSuggestions = emptyList()
                                        showWarning = true
                                        failedAutoAssign = true
                                        selectedCategory = ""
                                        selectedSubCategory = null
                                        hsnCode = ""
                                        gstRate = 0.0
                                        cgstRate = 0.0
                                        sgstRate = 0.0
                                        igstRate = 0.0
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isMatchingHsn,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(
                        if (isMatchingHsn) TerminologyManager.getTerm("Finding $taxCodeLabel…")
                        else TerminologyManager.getTerm("Populate $taxCodeLabel & $taxLabel")
                    )
                }

                if (showNameSuggestions && nameSuggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HsnDidYouMeanSection(
                        confidence = lookupConfidence,
                        suggestions = nameSuggestions,
                        taxCodeLabel = taxCodeLabel,
                        taxLabel = taxLabel,
                        onSelect = { picked ->
                            applyPickedTaxInfo(picked.taxInfo)
                        }
                    )
                }

                // Warnings and dynamic cascading fallback wizard inside dialog
                if (showWarning) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚠️ Couldn't auto-assign HSN codes. Please select manually.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (failedAutoAssign) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Step 1: Category Selection Dropdown
                    var categoryExpanded by remember { mutableStateOf(false) }
                    val categoriesList = categories.value

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { categoryExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (selectedCategory.isNotEmpty()) "Category: $selectedCategory" else "Select HSN Category")
                        }
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            categoriesList.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        categoryExpanded = false
                                        selectedCategory = cat
                                        selectedSubCategory = null

                                        coroutineScope.launch {
                                            val entries = com.minhas.teller.utils.HSNDataManager.getInstance(context).getEntriesByCategory(cat)
                                            hsnEntriesMap[cat] = entries
                                            if (entries.size == 1) {
                                                val singleEntry = entries.first()
                                                selectedSubCategory = singleEntry
                                                hsnCode = singleEntry.hsnCode
                                                gstRate = singleEntry.gstRate
                                                cgstRate = singleEntry.gstRate / 2.0
                                                sgstRate = singleEntry.gstRate / 2.0
                                                igstRate = singleEntry.gstRate
                                                showWarning = false
                                            } else {
                                                hsnCode = ""
                                                gstRate = 0.0
                                                cgstRate = 0.0
                                                sgstRate = 0.0
                                                igstRate = 0.0
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Step 2: Conditional Sub-Category Dropdown
                    val subCatList = hsnEntriesMap[selectedCategory] ?: emptyList()

                    if (selectedCategory.isNotEmpty() && subCatList.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        var subCategoryExpanded by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { subCategoryExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (selectedSubCategory != null) "Item: ${selectedSubCategory!!.description}" else "Select HSN Item")
                            }
                            DropdownMenu(
                                expanded = subCategoryExpanded,
                                onDismissRequest = { subCategoryExpanded = false }
                            ) {
                                subCatList.forEach { entry ->
                                    DropdownMenuItem(
                                        text = { Text("${entry.description} ($taxCodeLabel: ${entry.hsnCode}, Rate: ${entry.gstRate}%)") },
                                        onClick = {
                                            subCategoryExpanded = false
                                            selectedSubCategory = entry
                                            hsnCode = entry.hsnCode
                                            gstRate = entry.gstRate
                                            cgstRate = entry.gstRate / 2.0
                                            sgstRate = entry.gstRate / 2.0
                                            igstRate = entry.gstRate
                                            showWarning = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Display populated HSN info
                if (hsnCode.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(TerminologyManager.getTerm("Populated HSN / Tax Details:"), style = MaterialTheme.typography.labelMedium, color = Color(0xFF2E7D32))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$taxCodeLabel: $hsnCode", style = MaterialTheme.typography.bodyMedium)
                            Text("$taxLabel Rate: $gstRate%", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                } // end if (taxEnabled)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Unit Dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selectedUnitName = units.find { it.id == selectedUnitId }?.name ?: "Select Unit"
                    OutlinedTextField(
                        value = selectedUnitName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(TerminologyManager.getTerm("Unit")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        isError = isUnitError,
                        supportingText = { if (isUnitError) Text(TerminologyManager.getTerm("Please select a unit")) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm("+ Create New Unit"), color = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                expanded = false
                                showCreateUnitDialog = true
                            }
                        )
                        units.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.name) },
                                onClick = {
                                    selectedUnitId = unit.id
                                    isUnitError = false
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismissRequest) { Text(TerminologyManager.getTerm("Cancel")) }
                    Button(onClick = {
                        isNameError = name.isBlank()
                        isUnitError = selectedUnitId == null
                        if (isNameError || isUnitError) {
                            Toast.makeText(context, "Please correct the errors.", Toast.LENGTH_SHORT).show()
                        } else {
                            val companyId = com.minhas.teller.SessionManager.currentCompanyId.value ?: 0
                            val item = StockItem(
                                name = name,
                                unitId = selectedUnitId!!,
                                companyId = companyId,
                                hsnCode = if (taxEnabled) hsnCode.ifBlank { null } else null,
                                gstRate = if (taxEnabled) gstRate else 0.0,
                                cgstRate = if (taxEnabled) cgstRate else 0.0,
                                sgstRate = if (taxEnabled) sgstRate else 0.0,
                                igstRate = if (taxEnabled) igstRate else 0.0,
                                hsnAutoDetected = taxEnabled && !failedAutoAssign && hsnCode.isNotEmpty()
                            )
                            stockViewModel.insert(item) { id ->
                                onStockItemCreated(item.copy(id = id.toInt()))
                            }
                        }
                    }) { Text(TerminologyManager.getTerm("Create")) }
                }
            }
        }
    }
}
