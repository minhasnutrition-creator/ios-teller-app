package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Unit
import com.minhas.teller.data.entities.HsnEntry
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.data.repositories.UnitRepository
import com.minhas.teller.ui.viewmodels.StockItemViewModel
import com.minhas.teller.ui.viewmodels.StockItemViewModelFactory
import com.minhas.teller.ui.viewmodels.UnitViewModel
import com.minhas.teller.ui.viewmodels.UnitViewModelFactory
import com.minhas.teller.ui.components.ValidatedNumberTextField
import com.minhas.teller.SessionManager
import com.minhas.teller.ui.components.HsnDidYouMeanSection
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.utils.HsnFuzzyMatcher
import com.minhas.teller.utils.SmartTaxService
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditStockItemScreen(navController: NavHostController, stockItemId: Int = -1) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)

    val stockItemRepository = StockItemRepository(database.stockItemDao(), database.inventoryEntryDao())
    val stockItemViewModel: StockItemViewModel = viewModel(factory = StockItemViewModelFactory(stockItemRepository))

    val unitRepository = UnitRepository(database.unitDao())
    val unitViewModel: UnitViewModel = viewModel(factory = UnitViewModelFactory(unitRepository))
    val allUnits by unitViewModel.allUnits.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var selectedUnit: Unit? by remember { mutableStateOf(null) }
    var openingStock by remember { mutableStateOf("0.0") }
    var rate by remember { mutableStateOf("0.0") }
    
    // Smart Tax HSN fields
    var hsnCode by remember { mutableStateOf("") }
    var gstRate by remember { mutableStateOf(0.0) }
    var cgstRate by remember { mutableStateOf(0.0) }
    var sgstRate by remember { mutableStateOf(0.0) }
    var hsnAutoDetected by remember { mutableStateOf(false) }
    // Manual override: lets the user type the HSN code and tax rate directly instead of picking
    // from the bundled category/HSN list — the safety valve for when the app's auto-detected or
    // preset rate for an item is wrong and needs a quick correction.
    var manualHsnEntry by remember { mutableStateOf(false) }
    var gstRateInput by remember { mutableStateOf("0") }

    fun formatRate(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    fun applyManualGstRate(text: String) {
        gstRateInput = text
        val parsed = text.toDoubleOrNull() ?: return
        gstRate = parsed
        cgstRate = parsed / 2.0
        sgstRate = parsed / 2.0
        hsnAutoDetected = false
    }

    var showUnitSelectionDropdown by remember { mutableStateOf(false) }

    var isNameError by remember { mutableStateOf(false) }
    var isUnitError by remember { mutableStateOf(false) }

    // HSN/tax matches for the typed name. Filled only when the user asks for them — see
    // matchHsnFromName() below.
    var nameSuggestions by remember { mutableStateOf<List<SmartTaxService.TaxSuggestion>>(emptyList()) }
    var matchConfidence by remember { mutableStateOf(HsnFuzzyMatcher.MatchConfidence.NONE) }
    var showNameSuggestions by remember { mutableStateOf(false) }
    var isMatchingHsn by remember { mutableStateOf(false) }

    val smartTaxService = remember { SmartTaxService.getInstance(context) }
    val countryName = remember { com.minhas.teller.utils.DashboardPrefs.getTaxCountry(context) }
    val isIndia = remember { countryName.equals("India", true) }
    val taxConfig = remember { com.minhas.teller.utils.TaxConfigManager.getConfigForCountry(countryName) }
    val taxLabel = remember { taxConfig.taxName }
    val taxCodeLabel = remember { taxConfig.codeSystemName ?: "Tax Code" }
    val taxEnabled = remember { com.minhas.teller.utils.DashboardPrefs.isTaxSystemEnabled(context) }

    val categories = remember { mutableStateOf<List<String>>(emptyList()) }
    val hsnEntriesMap = remember { mutableStateMapOf<String, List<HsnEntry>>() }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedSubCategory by remember { mutableStateOf<HsnEntry?>(null) }

    LaunchedEffect(Unit) {
        categories.value = com.minhas.teller.utils.HSNDataManager.getInstance(context).getCategories()
    }

    LaunchedEffect(hsnCode) {
        if (hsnCode.isNotBlank()) {
            val dbEntry = smartTaxService.getHSNByCode(hsnCode)
            dbEntry?.let { entry ->
                selectedCategory = entry.category
                selectedSubCategory = entry
            }
        }
    }

    val barcodeResultHandle = navController.currentBackStackEntry?.savedStateHandle
    val barcodeResult = barcodeResultHandle?.getStateFlow<String?>("barcode_result", null)?.collectAsState()
    LaunchedEffect(barcodeResult?.value) {
        val code = barcodeResult?.value ?: return@LaunchedEffect
        barcodeResultHandle.remove<String>("barcode_result")
        barcode = code
    }

    var existingStockItem by remember { mutableStateOf<com.minhas.teller.data.entities.StockItem?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Load stock item data if in edit mode
    LaunchedEffect(stockItemId) {
        if (stockItemId != -1) {
            stockItemRepository.getStockItemById(stockItemId)?.let { stockItem ->
                existingStockItem = stockItem
                name = stockItem.name
                alias = stockItem.alias ?: ""
                barcode = stockItem.barcode ?: ""
                openingStock = stockItem.openingStock.toString()
                rate = stockItem.rate.toString()
                selectedUnit = unitRepository.getUnitById(stockItem.unitId)
                // Load HSN data
                hsnCode = stockItem.hsnCode ?: ""
                gstRate = stockItem.gstRate
                cgstRate = stockItem.cgstRate
                sgstRate = stockItem.sgstRate
                hsnAutoDetected = stockItem.hsnAutoDetected
                gstRateInput = formatRate(stockItem.gstRate)
            }
        }
    }
    
    fun applyTaxInfo(info: SmartTaxService.TaxInfo) {
        hsnCode = info.hsnCode
        gstRate = info.gstRate
        cgstRate = info.cgst
        sgstRate = info.sgst
        hsnAutoDetected = true
        gstRateInput = formatRate(info.gstRate)
    }

    /**
     * Looks the typed name up in the HSN database and fills in the code and tax rates.
     *
     * Deliberately manual. This used to run on every keystroke, which meant a name with spaces
     * was re-searched word by word and the suggestion list kept reopening over the field while
     * the user was still typing. Now nothing is searched until this is called: the closest match
     * is applied straight away, and the rest are offered in the list in case the top hit is wrong.
     */
    fun matchHsnFromName() {
        val query = name.trim()
        if (query.length < 2) {
            Toast.makeText(context, TerminologyManager.getTerm("Type the item name first"), Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            isMatchingHsn = true
            val result = try {
                smartTaxService.lookupHsnSuggestions(query, 5)
            } catch (e: Exception) {
                SmartTaxService.TaxLookupResult(HsnFuzzyMatcher.MatchConfidence.NONE, emptyList())
            }
            isMatchingHsn = false
            when (result.confidence) {
                HsnFuzzyMatcher.MatchConfidence.NONE -> {
                    nameSuggestions = emptyList()
                    matchConfidence = HsnFuzzyMatcher.MatchConfidence.NONE
                    showNameSuggestions = false
                    Toast.makeText(context, "No $taxCodeLabel match for \"$query\" — enter it manually", Toast.LENGTH_SHORT).show()
                }
                HsnFuzzyMatcher.MatchConfidence.EXACT_CONFIDENT -> {
                    val top = result.suggestions.first().taxInfo
                    applyTaxInfo(top)
                    nameSuggestions = emptyList()
                    matchConfidence = HsnFuzzyMatcher.MatchConfidence.NONE
                    showNameSuggestions = false
                    Toast.makeText(
                        context,
                        "$taxCodeLabel ${top.hsnCode} • $taxLabel ${formatRate(top.gstRate)}%",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                HsnFuzzyMatcher.MatchConfidence.DID_YOU_MEAN_ONE,
                HsnFuzzyMatcher.MatchConfidence.AMBIGUOUS_MULTIPLE -> {
                    matchConfidence = result.confidence
                    nameSuggestions = result.suggestions
                    showNameSuggestions = result.suggestions.isNotEmpty()
                }
            }
        }
    }

    if (showDeleteConfirmDialog && existingStockItem != null) {
        val item = existingStockItem!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    TerminologyManager.getTerm("Permanently Delete Item?"),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    TerminologyManager.getTerm("Are you sure you want to permanently delete '${item.name}'? This will remove it from active stock and future billing. This action cannot be undone.")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        stockItemViewModel.delete(item)
                        Toast.makeText(context, TerminologyManager.getTerm("Stock item permanently deleted"), Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(TerminologyManager.getTerm("Permanently Delete"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TellerTopAppBar(
                title = "Stock Item Details",
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController,
                actions = {
                    if (stockItemId != -1) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Item",
                                tint = Color(0xFFFF5252)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isNameError = false
                        // Typing never searches — clear stale match suggestions for the previous name.
                        showNameSuggestions = false
                        matchConfidence = HsnFuzzyMatcher.MatchConfidence.NONE
                        nameSuggestions = emptyList()
                    },
                    label = { Text(TerminologyManager.getTerm("Name")) },
                    isError = isNameError,
                    supportingText = { if (isNameError) Text(TerminologyManager.getTerm("Name cannot be empty")) },
                    trailingIcon = if (taxEnabled) {
                        {
                            if (isMatchingHsn) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = { matchHsnFromName() }) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "Find $taxCodeLabel and $taxLabel for this item"
                                    )
                                }
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

            }
            if (taxEnabled) {
                // The explicit trigger. Nothing is looked up until this (or the field's search
                // icon) is tapped, so a multi-word name can be typed in peace.
                TextButton(
                    onClick = { matchHsnFromName() },
                    enabled = !isMatchingHsn,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isMatchingHsn) TerminologyManager.getTerm("Matching…")
                        else "Find $taxCodeLabel & $taxLabel from name"
                    )
                }
                if (showNameSuggestions && nameSuggestions.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    HsnDidYouMeanSection(
                        confidence = matchConfidence,
                        suggestions = nameSuggestions,
                        taxCodeLabel = taxCodeLabel,
                        taxLabel = taxLabel,
                        onSelect = { picked ->
                            applyTaxInfo(picked.taxInfo)
                            showNameSuggestions = false
                            matchConfidence = HsnFuzzyMatcher.MatchConfidence.NONE
                            nameSuggestions = emptyList()
                            Toast.makeText(
                                context,
                                "$taxCodeLabel ${picked.taxInfo.hsnCode} • $taxLabel ${formatRate(picked.taxInfo.gstRate)}%",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text(TerminologyManager.getTerm("Alias")) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text(TerminologyManager.getTerm("Barcode (optional)")) },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { navController.navigate(com.minhas.teller.navigation.Screen.BarcodeScanner.route) }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = TerminologyManager.getTerm("Scan barcode"))
                }
            }

            // Unit Selection Dropdown
            ExposedDropdownMenuBox(
                expanded = showUnitSelectionDropdown,
                onExpandedChange = { showUnitSelectionDropdown = !showUnitSelectionDropdown },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedUnit?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(TerminologyManager.getTerm("Unit")) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showUnitSelectionDropdown) },
                    isError = isUnitError,
                    supportingText = { if (isUnitError) Text(TerminologyManager.getTerm("Unit cannot be empty")) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = showUnitSelectionDropdown,
                    onDismissRequest = { showUnitSelectionDropdown = false }
                ) {
                    allUnits.forEach { unit ->
                        DropdownMenuItem(
                            text = { Text(unit.name) },
                            onClick = {
                                selectedUnit = unit
                                showUnitSelectionDropdown = false
                                isUnitError = false
                            }
                        )
                    }
                }
            }

            ValidatedNumberTextField(
                value = openingStock,
                onValueChange = { openingStock = it },
                label = "Opening Stock",
                modifier = Modifier.fillMaxWidth(),
                isDecimal = true
            )

            ValidatedNumberTextField(
                value = rate,
                onValueChange = { rate = it },
                label = "Rate",
                modifier = Modifier.fillMaxWidth(),
                isDecimal = true
            )
            
            // HSN & Tax System Dropdown Selection
            // Only shown when the tax system is enabled in Settings.
            if (taxEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tax & HSN details",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Enter manually",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = manualHsnEntry,
                            onCheckedChange = { checked ->
                                manualHsnEntry = checked
                                if (checked) gstRateInput = formatRate(gstRate)
                            }
                        )
                    }
                }

                // Manual override: type the HSN/SAC code and tax rate directly. Use this if the
                // category picker below doesn't have the right item, or if an auto-populated /
                // preset rate turns out to be wrong and needs a quick fix.
                if (manualHsnEntry) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = hsnCode,
                        onValueChange = {
                            hsnCode = it
                            hsnAutoDetected = false
                        },
                        label = { Text("$taxCodeLabel (e.g. 6910)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ValidatedNumberTextField(
                        value = gstRateInput,
                        onValueChange = { applyManualGstRate(it) },
                        label = "$taxLabel Rate (%)",
                        modifier = Modifier.fillMaxWidth(),
                        isDecimal = true
                    )
                    if (isIndia && gstRate > 0.0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "CGST: ${formatRate(gstRate / 2.0)}% | SGST: ${formatRate(gstRate / 2.0)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Step 1: Category Selection Dropdown
                var categoryExpanded by remember { mutableStateOf(false) }
                val categoriesList = categories.value

                // Preset category/HSN picker — only shown outside manual-entry mode, since manual
                // mode already lets the user set hsnCode/gstRate directly above.
                if (!manualHsnEntry) {
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
                                            hsnAutoDetected = false
                                            gstRateInput = formatRate(singleEntry.gstRate)
                                        } else {
                                            hsnCode = ""
                                            gstRate = 0.0
                                            cgstRate = 0.0
                                            sgstRate = 0.0
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Step 2: Conditional Sub-Category Dropdown
                val subCatList = hsnEntriesMap[selectedCategory] ?: emptyList()
                
                LaunchedEffect(selectedCategory) {
                    if (selectedCategory.isNotEmpty() && !hsnEntriesMap.containsKey(selectedCategory)) {
                        hsnEntriesMap[selectedCategory] = com.minhas.teller.utils.HSNDataManager.getInstance(context).getEntriesByCategory(selectedCategory)
                    }
                }
                
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
                                        hsnAutoDetected = false
                                        gstRateInput = formatRate(entry.gstRate)
                                    }
                                )
                            }
                        }
                    }
                }
                }

                // Display selected HSN details
                if (hsnCode.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Selected HSN / Tax Details (${if (hsnAutoDetected) "auto-detected" else "manually set"}):",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF2E7D32)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$taxCodeLabel: $hsnCode", style = MaterialTheme.typography.bodyMedium)
                            Text("$taxLabel Rate: $gstRate%", style = MaterialTheme.typography.bodyMedium)
                            if (isIndia) {
                                Text("CGST: ${gstRate / 2.0}% | SGST: ${gstRate / 2.0}%", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            if (hsnAutoDetected) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(TerminologyManager.getTerm("If this looks wrong, flip \"Enter manually\" above to correct it."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val stock = openingStock.toDoubleOrNull() ?: 0.0
                    val itemRate = rate.toDoubleOrNull() ?: 0.0

                    isNameError = name.isBlank()
                    isUnitError = selectedUnit == null

                    if (isNameError || isUnitError) {
                        Toast.makeText(context, "Please correct the errors.", Toast.LENGTH_SHORT).show()
                    } else {
                        coroutineScope.launch {
                            val companyId = SessionManager.currentCompanyId.value ?: return@launch
                            val cgst = if (taxEnabled && isIndia) gstRate / 2.0 else 0.0
                            val sgst = if (taxEnabled && isIndia) gstRate / 2.0 else 0.0
                            val igst = if (taxEnabled && !isIndia) gstRate else 0.0

                            val stockItem = StockItem(
                                id = if (stockItemId != -1) stockItemId else 0,
                                name = name,
                                alias = alias.ifBlank { null },
                                barcode = barcode.trim().ifBlank { null },
                                unitId = selectedUnit!!.id,
                                openingStock = stock,
                                rate = itemRate,
                                companyId = companyId,
                                hsnCode = if (taxEnabled) hsnCode.ifBlank { null } else null,
                                gstRate = if (taxEnabled) gstRate else 0.0,
                                cgstRate = cgst,
                                sgstRate = sgst,
                                igstRate = igst,
                                hsnAutoDetected = taxEnabled && hsnAutoDetected
                            )
                            if (stockItemId == -1) {
                                stockItemViewModel.insert(stockItem)
                                Toast.makeText(context, "Stock Item added", Toast.LENGTH_SHORT).show()
                            } else {
                                stockItemViewModel.update(stockItem)
                                Toast.makeText(context, "Stock Item updated", Toast.LENGTH_SHORT).show()
                            }
                            navController.popBackStack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (stockItemId == -1) "Save Stock Item" else "Update Stock Item")
            }

            if (stockItemId != -1) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(TerminologyManager.getTerm("Delete Stock Item"), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
