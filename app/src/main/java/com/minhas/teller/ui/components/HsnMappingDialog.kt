package com.minhas.teller.ui.components

import com.minhas.teller.utils.TerminologyManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.minhas.teller.data.entities.HsnEntry
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.utils.DashboardPrefs
import com.minhas.teller.utils.HSNDataManager
import com.minhas.teller.utils.TaxConfigManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HsnMappingDialog(
    stockItem: StockItem,
    onDismissRequest: () -> Unit,
    onMapped: (String, Double) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val countryName = remember { DashboardPrefs.getTaxCountry(context) }
    val isIndia = remember { countryName.equals("India", true) }
    val config = remember { TaxConfigManager.getConfigForCountry(countryName) }
    
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    
    var hsnEntries by remember { mutableStateOf<List<HsnEntry>>(emptyList()) }
    var selectedEntry by remember { mutableStateOf<HsnEntry?>(null) }
    var entryExpanded by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(isIndia) }
    
    // Core mapped outputs
    var finalHsnCode by remember { mutableStateOf(stockItem.hsnCode ?: "") }
    var finalGstRate by remember { mutableStateOf(stockItem.gstRate) }
    var isSelectionValid by remember { mutableStateOf(false) }
    
    // Non-India properties
    var customRateText by remember { mutableStateOf(stockItem.gstRate.toString()) }
    var rateExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isIndia) {
            coroutineScope.launch {
                val manager = HSNDataManager.getInstance(context)
                var cats = manager.getCategories()
                if (cats.isEmpty()) {
                    manager.importFromMultipleSources()
                    cats = manager.getCategories()
                }
                categories = cats
                isLoading = false
            }
        } else {
            isSelectionValid = true
        }
    }

    LaunchedEffect(selectedCategory) {
        if (isIndia) {
            if (selectedCategory.isNotBlank()) {
                val manager = HSNDataManager.getInstance(context)
                hsnEntries = manager.getEntriesByCategory(selectedCategory)
                selectedEntry = hsnEntries.firstOrNull()
            } else {
                hsnEntries = emptyList()
                selectedEntry = null
            }
        }
    }
    
    LaunchedEffect(selectedEntry) {
        if (isIndia && selectedEntry != null) {
            finalHsnCode = selectedEntry!!.hsnCode
            finalGstRate = selectedEntry!!.gstRate
            isSelectionValid = true
        } else if (isIndia) {
            isSelectionValid = false
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val titleText = if (isIndia) "Map HSN Code" else "Map ${config.taxName} Details"
                val bodyText = if (isIndia) {
                    "We couldn't automatically identify the HSN category for \"${stockItem.name}\". Please manually map it below."
                } else {
                    "We couldn't automatically identify the ${config.taxName} rate for \"${stockItem.name}\". Please select/input it below."
                }

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    if (isIndia) {
                        // Original India Layout
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "HSN Category",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = selectedCategory,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(TerminologyManager.getTerm("Select HSN Category")) },
                                    trailingIcon = {
                                        IconButton(onClick = { categoryExpanded = true }) {
                                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Dropdown")
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                                
                                DropdownMenu(
                                    expanded = categoryExpanded,
                                    onDismissRequest = { categoryExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 300.dp)
                                ) {
                                    categories.forEach { category ->
                                        DropdownMenuItem(
                                            text = { Text(category, fontSize = 14.sp) },
                                            onClick = {
                                                selectedCategory = category
                                                categoryExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (selectedCategory.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Select HSN Code / Description",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    val selectedLabel = if (selectedEntry != null) {
                                        "${selectedEntry!!.hsnCode} - ${selectedEntry!!.description} (${selectedEntry!!.gstRate}%)"
                                    } else {
                                        ""
                                    }
                                    
                                    OutlinedTextField(
                                        value = selectedLabel,
                                        onValueChange = {},
                                        readOnly = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text(TerminologyManager.getTerm("Select HSN Code")) },
                                        trailingIcon = {
                                            IconButton(onClick = { entryExpanded = true }) {
                                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Dropdown")
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                        )
                                    )

                                    DropdownMenu(
                                        expanded = entryExpanded,
                                        onDismissRequest = { entryExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 300.dp)
                                    ) {
                                        hsnEntries.forEach { entry ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                        Text(
                                                            text = "${entry.hsnCode} (${entry.gstRate}%)",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                        Text(
                                                            text = entry.description,
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    selectedEntry = entry
                                                    entryExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Non-India Country Layout
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Tax Rate input
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${config.taxName} Rate (%)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = customRateText,
                                        onValueChange = {
                                            customRateText = it
                                            finalGstRate = it.toDoubleOrNull() ?: 0.0
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text(TerminologyManager.getTerm("Enter rate (e.g. 15)")) },
                                        trailingIcon = {
                                            if (config.standardRates.isNotEmpty()) {
                                                IconButton(onClick = { rateExpanded = true }) {
                                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Standard Rates")
                                                }
                                            }
                                        }
                                    )
                                    
                                    if (config.standardRates.isNotEmpty()) {
                                        DropdownMenu(
                                            expanded = rateExpanded,
                                            onDismissRequest = { rateExpanded = false }
                                        ) {
                                            config.standardRates.forEach { rate ->
                                                DropdownMenuItem(
                                                    text = { Text("$rate%") },
                                                    onClick = {
                                                        customRateText = rate.toString()
                                                        finalGstRate = rate
                                                        rateExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Tax Code input (if applicable)
                            if (config.codeSystemName != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "${config.codeSystemName}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    OutlinedTextField(
                                        value = finalHsnCode,
                                        onValueChange = { finalHsnCode = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text(TerminologyManager.getTerm("Enter code (optional)")) }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(TerminologyManager.getTerm("Cancel"))
                    }

                    Button(
                        onClick = {
                            onMapped(finalHsnCode, finalGstRate)
                        },
                        enabled = isSelectionValid && (isIndia || customRateText.toDoubleOrNull() != null),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(TerminologyManager.getTerm("Apply & Save"))
                    }
                }
            }
        }
    }
}
