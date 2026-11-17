package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.QuotationItem
import com.minhas.teller.utils.rupeesToPaise
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.repositories.QuotationRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.viewmodels.QuotationViewModel
import com.minhas.teller.ui.viewmodels.QuotationViewModelFactory
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.utils.DashboardPrefs
import com.minhas.teller.utils.TaxConfigManager
import com.minhas.teller.utils.taxIdName
import kotlinx.coroutines.launch

// Input data class for quotation items
data class QuotationItemInput(
    val name: String = "",
    val quantity: String = "1",
    val rate: String = "",
    val unit: String = "Pcs",
    val gstRate: Double = 0.0,
    val hsnCode: String = "",
    val description: String = ""
) {
    val qtyDouble get() = quantity.toDoubleOrNull() ?: 0.0
    val rateDouble get() = rate.toDoubleOrNull() ?: 0.0
    val amount get() = qtyDouble * rateDouble
    val taxAmount get() = if (gstRate > 0.0) amount - (amount / (1.0 + gstRate / 100.0)) else 0.0
}

private val fieldColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color(0xFF4CAF50),
        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = Color(0xFF4CAF50),
        focusedLabelColor = Color(0xFF81C784),
        unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
        focusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f)
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateQuotationScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = AppDatabase.getDatabase(context)
    val repository = QuotationRepository(database.quotationDao())
    val viewModel: QuotationViewModel = viewModel(factory = QuotationViewModelFactory(repository))
    val companyId = SessionManager.currentCompanyId.collectAsState().value ?: 1

    val currentCompany by produceState<Company?>(initialValue = null, companyId) {
        database.companyDao().getCompanyById(companyId).collect { value = it }
    }
    val currencySymbol = currentCompany?.currencySymbol ?: "₹"

    val countryName = remember { DashboardPrefs.getTaxCountry(context) }
    val isIndia = countryName.equals("India", true)
    val countryConfig = TaxConfigManager.getConfigForCountry(countryName)
    val taxLabel = countryConfig.taxName
    val taxIdLabel = countryConfig.taxIdName
    val taxCodeLabel = countryConfig.codeSystemName ?: "Tax Code"
    val taxEnabled = remember { com.minhas.teller.utils.DashboardPrefs.isTaxSystemEnabled(context) }

    // Customer / header fields
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var customerGstin by remember { mutableStateOf("") }
    var validityDays by remember { mutableStateOf("30") }
    var notes by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf("1. Prices are subject to change without notice.\n2. Delivery within 7 working days.\n3. Payment due within 15 days of invoice.") }
    var discount by remember { mutableStateOf("") }

    // Bank Details
    var showBankDetailsDialog by remember { mutableStateOf(false) }
    var bankName by remember { mutableStateOf("") }
    var bankAccountNo by remember { mutableStateOf("") }
    var bankIfsc by remember { mutableStateOf("") }
    var bankBranch by remember { mutableStateOf("") }

    // Items
    var items by remember { mutableStateOf(listOf(QuotationItemInput())) }

    // Stock items for search
    var allStockItems by remember { mutableStateOf(listOf<StockItem>()) }
    var isCreating by remember { mutableStateOf(false) }
    var showItemPickerIndex by remember { mutableStateOf(-1) }

    // Load stock items once
    LaunchedEffect(companyId) {
        allStockItems = database.stockItemDao().getAllStockItems(companyId)
    }

    // Totals
    val subTotal = items.sumOf { it.amount }
    val totalTax = items.sumOf { it.taxAmount }
    val discountAmt = discount.toDoubleOrNull() ?: 0.0
    val grandTotal = subTotal - discountAmt

    if (showBankDetailsDialog) {
        BankDetailsDialog(
            initialBankName = bankName,
            initialAccountNo = bankAccountNo,
            initialIfsc = bankIfsc,
            initialBranch = bankBranch,
            onDismiss = { showBankDetailsDialog = false },
            onConfirm = { name, acc, ifsc, branch ->
                bankName = name
                bankAccountNo = acc
                bankIfsc = ifsc
                bankBranch = branch
                showBankDetailsDialog = false
            }
        )
    }

    // Item picker dialog
    if (showItemPickerIndex >= 0) {
        ItemPickerDialog(
            stockItems = allStockItems,
            onDismiss = { showItemPickerIndex = -1 },
            onItemSelected = { stock ->
                val idx = showItemPickerIndex
                if (idx >= 0 && idx < items.size) {
                    val updated = items.toMutableList()
                    updated[idx] = updated[idx].copy(
                        name = stock.name,
                        rate = stock.rate.toString(),
                        hsnCode = stock.hsnCode ?: "",
                        gstRate = stock.gstRate
                    )
                    items = updated
                }
                showItemPickerIndex = -1
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF1A1A4A), Color(0xFF0F0F2D)))
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                var fontSize by remember { mutableStateOf(22.sp) }
                LaunchedEffect("New Quotation") {
                    fontSize = 22.sp
                }
                Text(
                    text = "New Quotation",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                    onTextLayout = { textLayoutResult ->
                        if (textLayoutResult.hasVisualOverflow && fontSize.value > 10f) {
                            fontSize = (fontSize.value * 0.9f).sp
                        }
                    }
                )
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm("Add Bank Details")) },
                            onClick = {
                                showMenu = false
                                showBankDetailsDialog = true
                            }
                        )
                    }
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ── BILL TO ──────────────────────────────────────────────────
                SectionCard(title = "Bill To") {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(TerminologyManager.getTerm("Customer / Party Name *")) },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = Color.White.copy(alpha = 0.6f)) },
                        colors = fieldColors,
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customerPhone,
                            onValueChange = { customerPhone = it },
                            modifier = Modifier.weight(1f),
                            label = { Text(TerminologyManager.getTerm("Phone")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            leadingIcon = { Icon(Icons.Default.Phone, null, tint = Color.White.copy(alpha = 0.6f)) },
                            colors = fieldColors,
                            singleLine = true
                        )
                        if (taxEnabled) {
                        OutlinedTextField(
                            value = customerGstin,
                            onValueChange = { customerGstin = it.uppercase() },
                            modifier = Modifier.weight(1f),
                            label = { Text(taxIdLabel) },
                            colors = fieldColors,
                            singleLine = true
                        )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customerAddress,
                        onValueChange = { customerAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(TerminologyManager.getTerm("Address")) },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = Color.White.copy(alpha = 0.6f)) },
                        colors = fieldColors,
                        maxLines = 3,
                        minLines = 2
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = validityDays,
                        onValueChange = { validityDays = it },
                        modifier = Modifier.fillMaxWidth(0.4f),
                        label = { Text(TerminologyManager.getTerm("Valid for (days)")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors,
                        singleLine = true
                    )
                }

                // ── ITEMS ─────────────────────────────────────────────────────
                SectionCard(title = "Items  (${items.size})") {
                    items.forEachIndexed { index, item ->
                        ItemRow(
                            index = index,
                            item = item,
                            onPickItem = { showItemPickerIndex = index },
                            onUpdate = { updated ->
                                val m = items.toMutableList()
                                m[index] = updated
                                items = m
                            },
                            onRemove = if (items.size > 1) ({
                                val m = items.toMutableList()
                                m.removeAt(index)
                                items = m
                            }) else null,
                            taxLabel = taxLabel,
                            taxCodeLabel = taxCodeLabel,
                            currencySymbol = currencySymbol,
                            taxEnabled = taxEnabled
                        )
                        if (index < items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = Color.White.copy(alpha = 0.1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { items = items + QuotationItemInput() },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50))
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color(0xFF4CAF50))
                        Spacer(Modifier.width(6.dp))
                        Text(TerminologyManager.getTerm("Add Item"), color = Color(0xFF81C784))
                    }
                }

                // ── TOTALS ────────────────────────────────────────────────────
                SectionCard(title = "Summary") {
                    TotalRow("Sub Total", subTotal)
                    if (totalTax > 0) TotalRow("$taxLabel Tax", totalTax)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = discount,
                        onValueChange = { discount = it },
                        modifier = Modifier.fillMaxWidth(0.5f),
                        label = { Text("Discount ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = fieldColors,
                        singleLine = true
                    )
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = Color(0xFF4CAF50).copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(TerminologyManager.getTerm("GRAND TOTAL"), color = Color(0xFF81C784), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        AutoShrinkText("₹ %.2f".format(grandTotal), color = Color(0xFF81C784), maxFontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // ── NOTES & TERMS ─────────────────────────────────────────────
                SectionCard(title = "Notes & Terms") {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(TerminologyManager.getTerm("Notes (Optional)")) },
                        colors = fieldColors,
                        minLines = 2
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = terms,
                        onValueChange = { terms = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(TerminologyManager.getTerm("Terms & Conditions")) },
                        colors = fieldColors,
                        minLines = 3
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showBankDetailsDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF81C784))
                    ) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (bankName.isBlank() && bankAccountNo.isBlank()) "Add Bank Details" else "Edit Bank Details")
                    }
                }

                if (bankName.isNotBlank() || bankAccountNo.isNotBlank()) {
                    SectionCard(title = "Bank Details") {
                        if (bankName.isNotBlank()) Text("Bank: $bankName", color = Color.White, fontSize = 14.sp)
                        if (bankAccountNo.isNotBlank()) Text("Account No: $bankAccountNo", color = Color.White, fontSize = 14.sp)
                        if (bankIfsc.isNotBlank()) Text("IFSC: $bankIfsc", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        if (bankBranch.isNotBlank()) Text("Branch: $bankBranch", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }

                // ── CREATE BUTTON ─────────────────────────────────────────────
                Button(
                    onClick = {
                        if (customerName.isBlank()) return@Button
                        isCreating = true
                        val quotationItems = items.filter { it.name.isNotBlank() }.map {
                            QuotationItem(
                                itemName = it.name,
                                description = it.description.ifBlank { null },
                                quantity = it.qtyDouble,
                                unit = it.unit,
                                rate = it.rateDouble,
                                amount = it.amount.rupeesToPaise(),
                                hsnCode = it.hsnCode.ifBlank { null },
                                gstRate = it.gstRate
                            )
                        }
                        val formattedNotes = buildString {
                            if (notes.isNotBlank()) append(notes)
                            if (bankName.isNotBlank() || bankAccountNo.isNotBlank()) {
                                if (isNotEmpty()) append("\n\n")
                                append("Bank Details:\n")
                                if (bankName.isNotBlank()) append("Bank: $bankName\n")
                                if (bankAccountNo.isNotBlank()) append("A/C No: $bankAccountNo\n")
                                if (bankIfsc.isNotBlank()) append("IFSC: $bankIfsc\n")
                                if (bankBranch.isNotBlank()) append("Branch: $bankBranch\n")
                            }
                        }.trim().ifBlank { null }

                        viewModel.createQuotation(
                            customerName = customerName,
                            customerPhone = customerPhone.ifBlank { null },
                            customerAddress = buildString {
                                if (customerAddress.isNotBlank()) append(customerAddress)
                                if (customerGstin.isNotBlank()) {
                                    if (isNotEmpty()) append("\n")
                                    append("$taxIdLabel: $customerGstin")
                                }
                            }.ifBlank { null },
                            items = quotationItems,
                            subTotal = subTotal,
                            taxAmount = totalTax,
                            discount = discountAmt,
                            grandTotal = grandTotal,
                            notes = formattedNotes,
                            terms = terms.ifBlank { null },
                            companyId = companyId,
                            expiryDate = validityDays.toIntOrNull()?.let { days ->
                                java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, days) }.time
                            }
                        ) { quotation ->
                            scope.launch {
                                val company = database.companyDao().getCompanyByIdSync(companyId)
                                ExportUtils.generateQuotationPdf(context, quotation, company, share = false)
                            }
                            isCreating = false
                            navController.popBackStack()
                        }
                    },
                    enabled = customerName.isNotBlank() && items.any { it.name.isNotBlank() } && !isCreating,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.PictureAsPdf, null)
                        Spacer(Modifier.width(8.dp))
                        Text(TerminologyManager.getTerm("Save & Generate PDF"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color(0xFF81C784), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun TotalRow(label: String, amount: Double, currencySymbol: String = "₹") {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        AutoShrinkText("$currencySymbol %.2f".format(amount), color = Color.White, maxFontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemRow(
    index: Int,
    item: QuotationItemInput,
    onPickItem: () -> Unit,
    onUpdate: (QuotationItemInput) -> Unit,
    onRemove: (() -> Unit)?,
    taxLabel: String,
    taxCodeLabel: String,
    currencySymbol: String,
    taxEnabled: Boolean = true
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}.",
                color = Color(0xFF81C784),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.width(24.dp)
            )
            OutlinedTextField(
                value = item.name,
                onValueChange = { onUpdate(item.copy(name = it)) },
                modifier = Modifier.weight(1f),
                label = { Text(TerminologyManager.getTerm("Item Name")) },
                placeholder = { Text(TerminologyManager.getTerm("Type or pick from stock")) },
                trailingIcon = {
                    IconButton(onClick = onPickItem) {
                        Icon(Icons.Default.Search, "Pick item", tint = Color(0xFF4CAF50))
                    }
                },
                colors = fieldColors,
                singleLine = true
            )
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFFF5252).copy(alpha = 0.8f))
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = item.quantity,
                onValueChange = { onUpdate(item.copy(quantity = it)) },
                modifier = Modifier.weight(1f),
                label = { Text(TerminologyManager.getTerm("Qty")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = fieldColors,
                singleLine = true
            )
            OutlinedTextField(
                value = item.unit,
                onValueChange = { onUpdate(item.copy(unit = it)) },
                modifier = Modifier.weight(1f),
                label = { Text(TerminologyManager.getTerm("Unit")) },
                colors = fieldColors,
                singleLine = true
            )
            OutlinedTextField(
                value = item.rate,
                onValueChange = { onUpdate(item.copy(rate = it)) },
                modifier = Modifier.weight(1.5f),
                label = { Text("Rate ($currencySymbol)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = fieldColors,
                singleLine = true
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (taxEnabled) {
            OutlinedTextField(
                value = item.hsnCode,
                onValueChange = { onUpdate(item.copy(hsnCode = it)) },
                modifier = Modifier.weight(1f),
                label = { Text(taxCodeLabel) },
                colors = fieldColors,
                singleLine = true
            )
            OutlinedTextField(
                value = if (item.gstRate == 0.0) "" else item.gstRate.toString(),
                onValueChange = { onUpdate(item.copy(gstRate = it.toDoubleOrNull() ?: 0.0)) },
                modifier = Modifier.weight(1f),
                label = { Text("$taxLabel %") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = fieldColors,
                singleLine = true
            )
            }
            Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.End) {
                Text(TerminologyManager.getTerm("Amount"), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                AutoShrinkText(
                    "$currencySymbol %.2f".format(item.amount),
                    color = Color.White,
                    maxFontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (taxEnabled && item.taxAmount > 0) {
                    AutoShrinkText(
                        "+$currencySymbol %.2f $taxLabel".format(item.taxAmount),
                        color = Color(0xFF81C784),
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemPickerDialog(
    stockItems: List<StockItem>,
    onDismiss: () -> Unit,
    onItemSelected: (StockItem) -> Unit
) {
    val context = LocalContext.current
    val countryName = remember { DashboardPrefs.getTaxCountry(context) }
    val countryConfig = TaxConfigManager.getConfigForCountry(countryName)
    val taxLabel = countryConfig.taxName
    val taxCodeLabel = countryConfig.codeSystemName ?: "Tax Code"

    var query by remember { mutableStateOf("") }
    val filtered = remember(query, stockItems) {
        if (query.isBlank()) stockItems.take(30)
        else stockItems.filter { it.name.contains(query, ignoreCase = true) }.take(30)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF1E2D4A).copy(alpha = 0.9f), Color(0xFF0F182E).copy(alpha = 0.95f))
                        )
                    )
                    .padding(24.dp)
            ) {
                Text(TerminologyManager.getTerm("Select Item from Stock"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(TerminologyManager.getTerm("Search items...")) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.6f)) },
                    colors = fieldColors,
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text(TerminologyManager.getTerm("No items found"), color = Color.White.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(filtered) { _, stock ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { onItemSelected(stock) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    AutoShrinkText(stock.name, color = Color.White, maxFontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    if (!stock.hsnCode.isNullOrBlank()) {
                                        AutoShrinkText("$taxCodeLabel: ${stock.hsnCode}  •  $taxLabel: ${stock.gstRate}%", color = Color.White.copy(alpha = 0.5f), maxFontSize = 11.sp, minFontSize = 8.sp)
                                    }
                                }
                                AutoShrinkText("₹ %.2f".format(stock.rate), color = Color(0xFF81C784), maxFontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(TerminologyManager.getTerm("Cancel"), color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankDetailsDialog(
    initialBankName: String,
    initialAccountNo: String,
    initialIfsc: String,
    initialBranch: String,
    onDismiss: () -> Unit,
    onConfirm: (bankName: String, accountNo: String, ifsc: String, branch: String) -> Unit
) {
    var bankName by remember { mutableStateOf(initialBankName) }
    var accountNo by remember { mutableStateOf(initialAccountNo) }
    var ifsc by remember { mutableStateOf(initialIfsc) }
    var branch by remember { mutableStateOf(initialBranch) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF1E2D4A).copy(alpha = 0.95f), Color(0xFF0F182E).copy(alpha = 0.98f))
                        )
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(TerminologyManager.getTerm("Add / Edit Bank Details"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text(TerminologyManager.getTerm("Bank Name")) },
                    colors = fieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accountNo,
                    onValueChange = { accountNo = it },
                    label = { Text(TerminologyManager.getTerm("Account Number")) },
                    colors = fieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ifsc,
                    onValueChange = { ifsc = it.uppercase() },
                    label = { Text(TerminologyManager.getTerm("IFSC Code")) },
                    colors = fieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text(TerminologyManager.getTerm("Branch Name")) },
                    colors = fieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(TerminologyManager.getTerm("Cancel"), color = Color.White.copy(alpha = 0.6f))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(bankName, accountNo, ifsc, branch) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text(TerminologyManager.getTerm("Save Details"))
                    }
                }
            }
        }
    }
}
