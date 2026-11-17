package com.minhas.teller.ui.components

import com.minhas.teller.utils.TerminologyManager
import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.utils.rupeesToPaise
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.utils.GstComplianceRules
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherEditDialog(
    voucherWithAllEntries: VoucherWithAllEntries,
    allLedgers: List<Ledger>,
    onDismissRequest: () -> Unit,
    onSave: (Voucher, List<VoucherEntry>, List<InventoryEntry>) -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val taxEnabled = remember { com.minhas.teller.utils.DashboardPrefs.isTaxSystemEnabled(context) }
    
    var allStockItems by remember { mutableStateOf<List<StockItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        allStockItems = database.stockItemDao().getAllStockItems(voucherWithAllEntries.voucher.companyId)
    }

    var voucherDate by remember { mutableStateOf(voucherWithAllEntries.voucher.date) }
    var voucherNumber by remember { mutableStateOf(voucherWithAllEntries.voucher.voucherNumber) }
    var narration by remember { mutableStateOf(voucherWithAllEntries.voucher.narration ?: "") }
    
    var entries by remember { mutableStateOf(voucherWithAllEntries.voucherEntries.map { it.copy() }) }
    var inventoryEntries by remember { mutableStateOf(voucherWithAllEntries.inventoryEntries.map { it.copy() }) }
    
    var selectedEntryIndexForLedgerChange by remember { mutableStateOf<Int?>(null) }
    
    val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
    val calendar = Calendar.getInstance().apply { time = voucherDate }
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            voucherDate = calendar.time
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // Calculate live totals
    val debitSum = entries.filter { it.isDebit }.sumOf { it.amount }
    val creditSum = entries.filter { !it.isDebit }.sumOf { it.amount }
    val diff = abs(debitSum - creditSum)
    val isBalanced = diff == 0L

    if (selectedEntryIndexForLedgerChange != null) {
        LedgerSelectionDialog(
            onDismissRequest = { selectedEntryIndexForLedgerChange = null },
            onLedgerSelected = { selectedLedger ->
                val index = selectedEntryIndexForLedgerChange!!
                entries = entries.mapIndexed { idx, entry ->
                    if (idx == index) entry.copy(ledgerId = selectedLedger.id) else entry
                }
                selectedEntryIndexForLedgerChange = null
            }
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
            color = Color(0xFF0F182E),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Quick Audit Edit",
                            color = Color(0xFFB8A0D0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        AutoShrinkText(
                            text = "${voucherWithAllEntries.voucher.type} Voucher",
                            color = Color.White,
                            maxFontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Form Scroll Area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Date input
                    OutlinedTextField(
                        value = sdf.format(voucherDate),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(TerminologyManager.getTerm("Date"), color = Color.White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFCE93D8),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        trailingIcon = {
                            IconButton(onClick = { datePickerDialog.show() }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select Date", tint = Color.White)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { datePickerDialog.show() }
                    )

                    // Voucher number input
                    OutlinedTextField(
                        value = voucherNumber,
                        onValueChange = { voucherNumber = it },
                        label = { Text(TerminologyManager.getTerm("Voucher Number"), color = Color.White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFCE93D8),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Narration input
                    OutlinedTextField(
                        value = narration,
                        onValueChange = { narration = it },
                        label = { Text(TerminologyManager.getTerm("Narration / Explanation"), color = Color.White.copy(alpha = 0.6f)) },
                        placeholder = { Text(TerminologyManager.getTerm("Enter audit explanation..."), color = Color.White.copy(alpha = 0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFCE93D8),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Ledger Entries section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ledger Accounting Entries",
                            color = Color(0xFFB8A0D0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                val defaultLedgerId = allLedgers.firstOrNull()?.id ?: 1
                                entries = entries + VoucherEntry(
                                    voucherId = voucherWithAllEntries.voucher.id,
                                    ledgerId = defaultLedgerId,
                                    amount = 0L,
                                    isDebit = true
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Entry", tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(TerminologyManager.getTerm("Add Line"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    entries.forEachIndexed { entryIndex, entry ->
                        val ledgerName = allLedgers.find { it.id == entry.ledgerId }?.name ?: "Select Ledger"
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Ledger selector Box
                                Box(
                                    modifier = Modifier
                                        .weight(1.8f)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .clickable { selectedEntryIndexForLedgerChange = entryIndex }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AutoShrinkText(
                                            text = ledgerName,
                                            color = Color.White,
                                            maxFontSize = 13.sp
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select", tint = Color.White.copy(alpha = 0.6f))
                                    }
                                }

                                // Amount Field
                                var amountText by remember(entry.amount) { mutableStateOf(if (entry.amount == 0L) "" else entry.amount.paiseToRupees().toString()) }
                                OutlinedTextField(
                                    value = amountText,
                                    onValueChange = { text ->
                                        amountText = text
                                        val amt = text.toDoubleOrNull() ?: 0.0
                                        entries = entries.mapIndexed { idx, e ->
                                            if (idx == entryIndex) e.copy(amount = amt.rupeesToPaise()) else e
                                        }
                                    },
                                    placeholder = { Text("0.00", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFFCE93D8),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                    ),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                )

                                // Dr / Cr Switch
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(if (entry.isDebit) Color(0xFF69F0AE).copy(alpha = 0.2f) else Color.Transparent)
                                            .clickable {
                                                entries = entries.mapIndexed { idx, e ->
                                                    if (idx == entryIndex) e.copy(isDebit = true) else e
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        AutoShrinkText(TerminologyManager.getTerm("Dr"), color = if (entry.isDebit) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.6f), maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(if (!entry.isDebit) Color(0xFFEF9A9A).copy(alpha = 0.2f) else Color.Transparent)
                                            .clickable {
                                                entries = entries.mapIndexed { idx, e ->
                                                    if (idx == entryIndex) e.copy(isDebit = false) else e
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        AutoShrinkText(TerminologyManager.getTerm("Cr"), color = if (!entry.isDebit) Color(0xFFEF9A9A) else Color.White.copy(alpha = 0.6f), maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Delete Button
                                IconButton(
                                    onClick = {
                                        entries = entries.filterIndexed { idx, _ -> idx != entryIndex }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Line", tint = Color(0xFFEF9A9A), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Inventory Items Section (if it has inventory entries)
                    if (inventoryEntries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Inventory Items Details",
                            color = Color(0xFFB8A0D0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        inventoryEntries.forEachIndexed { itemIndex, itemEntry ->
                            val itemName = allStockItems.find { it.id == itemEntry.stockItemId }?.name ?: "Stock Item"
                            
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AutoShrinkText(
                                        text = itemName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxFontSize = 13.sp
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Quantity
                                        var qtyText by remember(itemEntry.quantity) { mutableStateOf(itemEntry.quantity.toString()) }
                                        OutlinedTextField(
                                            value = qtyText,
                                            onValueChange = { text ->
                                                qtyText = text
                                                val q = text.toDoubleOrNull() ?: 0.0
                                                inventoryEntries = inventoryEntries.mapIndexed { idx, item ->
                                                    if (idx == itemIndex) {
                                                        val amt = GstComplianceRules.round2(q * item.rate)
                                                        val gst = if (taxEnabled) item.gstRate else 0.0
                                                        val taxableAmt = if (gst > 0.0) GstComplianceRules.round2(amt / (1.0 + gst / 100.0)) else amt
                                                        val tax = GstComplianceRules.round2(amt - taxableAmt)
                                                        item.copy(quantity = q, amount = amt.rupeesToPaise(), taxableAmount = taxableAmt.rupeesToPaise(), taxAmount = tax.rupeesToPaise(), totalAmount = amt.rupeesToPaise())
                                                    } else item
                                                }
                                            },
                                            label = { Text(TerminologyManager.getTerm("Qty"), fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFFCE93D8),
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                            ),
                                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                            modifier = Modifier.weight(1f)
                                        )

                                        // Rate
                                        var rateText by remember(itemEntry.rate) { mutableStateOf(itemEntry.rate.toString()) }
                                        OutlinedTextField(
                                            value = rateText,
                                            onValueChange = { text ->
                                                rateText = text
                                                val r = text.toDoubleOrNull() ?: 0.0
                                                inventoryEntries = inventoryEntries.mapIndexed { idx, item ->
                                                    if (idx == itemIndex) {
                                                        val amt = GstComplianceRules.round2(item.quantity * r)
                                                        val gst = if (taxEnabled) item.gstRate else 0.0
                                                        val taxableAmt = if (gst > 0.0) GstComplianceRules.round2(amt / (1.0 + gst / 100.0)) else amt
                                                        val tax = GstComplianceRules.round2(amt - taxableAmt)
                                                        item.copy(rate = r, amount = amt.rupeesToPaise(), taxableAmount = taxableAmt.rupeesToPaise(), taxAmount = tax.rupeesToPaise(), totalAmount = amt.rupeesToPaise())
                                                    } else item
                                                }
                                            },
                                            label = { Text(TerminologyManager.getTerm("Rate"), fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFFCE93D8),
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                            ),
                                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                            modifier = Modifier.weight(1.2f)
                                        )

                                        // GST % — only shown when tax is enabled
                                        if (taxEnabled) {
                                        var gstText by remember(itemEntry.gstRate) { mutableStateOf(itemEntry.gstRate.toString()) }
                                        OutlinedTextField(
                                            value = gstText,
                                            onValueChange = { text ->
                                                gstText = text
                                                val g = text.toDoubleOrNull() ?: 0.0
                                                inventoryEntries = inventoryEntries.mapIndexed { idx, item ->
                                                    if (idx == itemIndex) {
                                                        // Keep the entered (tax-inclusive) gross fixed and re-split it at the
                                                        // new rate, matching the qty/rate handlers above and the app's
                                                        // inclusive-price convention. (Previously this treated taxableAmount as
                                                        // the base, which changed the total and left `amount` stale.)
                                                        val gross = item.amount.paiseToRupees()
                                                        val taxableAmt = if (g > 0.0) GstComplianceRules.round2(gross / (1.0 + g / 100.0)) else gross
                                                        val tax = GstComplianceRules.round2(gross - taxableAmt)
                                                        item.copy(gstRate = g, taxableAmount = taxableAmt.rupeesToPaise(), taxAmount = tax.rupeesToPaise(), totalAmount = gross.rupeesToPaise())
                                                    } else item
                                                }
                                            },
                                            label = { Text(TerminologyManager.getTerm("GST %"), fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFFCE93D8),
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                            ),
                                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        }

                                        // HSN — only shown when tax is enabled
                                        if (taxEnabled) {
                                        var hsnText by remember(itemEntry.hsnCode) { mutableStateOf(itemEntry.hsnCode ?: "") }
                                        OutlinedTextField(
                                            value = hsnText,
                                            onValueChange = { text ->
                                                hsnText = text
                                                inventoryEntries = inventoryEntries.mapIndexed { idx, item ->
                                                    if (idx == itemIndex) item.copy(hsnCode = text) else item
                                                }
                                            },
                                            label = { Text(TerminologyManager.getTerm("HSN"), fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f)) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFFCE93D8),
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                            ),
                                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                            modifier = Modifier.weight(1.3f)
                                        )
                                        }
                                    }

                                    // Tax calculations display — only shown when tax is enabled
                                    if (taxEnabled) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AutoShrinkText(
                                            text = "Taxable: ₹${String.format("%.2f", itemEntry.taxableAmount.paiseToRupees())} | GST Tax: ₹${String.format("%.2f", itemEntry.taxAmount.paiseToRupees())}",
                                            color = Color.White.copy(alpha = 0.6f),
                                            maxFontSize = 11.sp,
                                            minFontSize = 8.sp
                                        )
                                        AutoShrinkText(
                                            text = "Total: ₹${String.format("%.2f", itemEntry.totalAmount.paiseToRupees())}",
                                            color = Color(0xFF69F0AE),
                                            fontWeight = FontWeight.Bold,
                                            maxFontSize = 11.sp,
                                            minFontSize = 8.sp
                                        )
                                    }
                                    }
                                }
                            }
                        }
                        
                        // Expected GST Helper — only shown when tax is enabled
                        if (taxEnabled) {
                        val totalExpectedGst = inventoryEntries.sumOf { it.taxAmount }
                        val gstLedgersActual = entries.filter { entry ->
                            val ledg = allLedgers.find { l -> l.id == entry.ledgerId }
                            val name = ledg?.name?.lowercase().orEmpty()
                            val grp = ledg?.group?.lowercase().orEmpty()
                            name.contains("gst") || name.contains("tax") || name.contains("duty") || grp.contains("duties") || grp.contains("taxes")
                        }.sumOf { it.amount }
                        val gstMismatchDiff = abs(totalExpectedGst - gstLedgersActual).paiseToRupees()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (gstMismatchDiff < 2.0) Color(0xFF69F0AE).copy(alpha = 0.05f) else Color(0xFFEF9A9A).copy(alpha = 0.05f))
                                .border(1.dp, if (gstMismatchDiff < 2.0) Color(0xFF69F0AE).copy(alpha = 0.1f) else Color(0xFFEF9A9A).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GST Validation Audit Info",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            AutoShrinkText(
                                text = "Expected GST: ₹${String.format("%.2f", totalExpectedGst.paiseToRupees())} | Actual Entry GST: ₹${String.format("%.2f", gstLedgersActual.paiseToRupees())}",
                                color = if (gstMismatchDiff < 2.0) Color(0xFF69F0AE) else Color(0xFFEF9A9A),
                                maxFontSize = 10.sp,
                                minFontSize = 7.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        }
                    }
                }

                // Balance and Save Bar
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Double Entry Balancing",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isBalanced) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isBalanced) Color(0xFF69F0AE) else Color(0xFFEF9A9A),
                            modifier = Modifier.size(16.dp)
                        )
                        AutoShrinkText(
                            text = if (isBalanced) {
                                "Balanced: ₹${String.format("%,.2f", debitSum.paiseToRupees())}"
                            } else {
                                "Mismatched! Diff: ₹${String.format("%,.2f", diff.paiseToRupees())}"
                            },
                            color = if (isBalanced) Color(0xFF69F0AE) else Color(0xFFEF9A9A),
                            fontWeight = FontWeight.Black,
                            maxFontSize = 13.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
                    ) {
                        Text(TerminologyManager.getTerm("Cancel"), fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Button(
                        onClick = {
                            if (isBalanced && entries.isNotEmpty()) {
                                val updatedVoucher = voucherWithAllEntries.voucher.copy(
                                    date = voucherDate,
                                    voucherNumber = voucherNumber,
                                    narration = narration
                                )
                                onSave(updatedVoucher, entries, inventoryEntries)
                                onDismissRequest()
                            }
                        },
                        enabled = isBalanced && entries.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF40C4FF),
                            disabledContainerColor = Color.White.copy(alpha = 0.08f)
                        )
                    ) {
                        Text(
                            text = "Save Audit Changes",
                            color = if (isBalanced && entries.isNotEmpty()) Color(0xFF0F182E) else Color.White.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
