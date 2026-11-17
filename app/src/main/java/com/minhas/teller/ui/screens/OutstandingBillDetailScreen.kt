package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.utils.paiseToRupees
import java.text.SimpleDateFormat
import java.util.*
import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.material.icons.filled.DateRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutstandingBillDetailScreen(navController: NavHostController, voucherId: Int, ledgerId: Int) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val voucherRepository = remember { VoucherRepository(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.billAllocationDao(), database.auditLogDao(), database.ledgerDao(), db = database) }
    val ledgerRepository = remember { LedgerRepository(database.ledgerDao()) }
    val stockItemRepository = remember { StockItemRepository(database.stockItemDao()) }
    val voucherViewModel: VoucherViewModel = viewModel(factory = VoucherViewModelFactory(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.ledgerDao(), database.billAllocationDao(), database.auditLogDao(), db = database))

    var voucherWithEntries by remember { mutableStateOf<com.minhas.teller.data.entities.VoucherWithAllEntries?>(null) }
    var ledger by remember { mutableStateOf<com.minhas.teller.data.entities.Ledger?>(null) }
    var stockItemsMap by remember { mutableStateOf<Map<Int, com.minhas.teller.data.entities.StockItem>>(emptyMap()) }
    var unitsMap by remember { mutableStateOf<Map<Int, com.minhas.teller.data.entities.Unit>>(emptyMap()) }
    var showSettleDialog by remember { mutableStateOf(false) }
    var companyName by remember { mutableStateOf("Teller") }
    var company by remember { mutableStateOf<com.minhas.teller.data.entities.Company?>(null) }

    LaunchedEffect(voucherId) {
        voucherWithEntries = voucherRepository.getVoucherWithAllEntriesById(voucherId)
        ledger = ledgerRepository.getLedgerById(ledgerId)
        
        val companyId = SessionManager.currentCompanyId.value ?: 1
        stockItemsMap = stockItemRepository.getAllStockItems(companyId).associateBy { it.id }
        database.unitDao().getAllUnits(companyId).let { units ->
            unitsMap = units.associateBy { it.id }
        }
        
        // Fetch company name dynamically
        val companyObj = database.companyDao().getCompanyByIdSync(companyId)
        company = companyObj
        if (companyObj != null) {
            companyName = companyObj.name
        }
        
        voucherViewModel.loadReportOutstandingVouchers()
    }

    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    
    val reportOutstandingVouchers by voucherViewModel.reportOutstandingVouchers.collectAsState()
    val currentOutstanding = reportOutstandingVouchers.find { it.voucherId == voucherId && it.ledgerId == ledgerId }

    val radialBackground = Brush.radialGradient(
        colors = listOf(Color(0xFF121212), Color(0xFF000000)),
        radius = 2000f
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText("Outstanding Payments", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val headers = listOf("Date", "Reference/Vch No", "Total Bill Amount", "Pending Amount", "Status")
                        val currentOutstandingVal = currentOutstanding
                        val data = listOf(
                            listOf(
                                currentOutstandingVal?.date?.let { dateFormatter.format(it) } ?: "",
                                voucherWithEntries?.voucher?.voucherNumber ?: "",
                                "₹ %.2f".format(currentOutstandingVal?.originalAmount ?: 0.0),
                                "₹ %.2f".format(currentOutstandingVal?.pendingAmount ?: 0.0),
                                if (currentOutstandingVal?.isDebit == true) "Receivable" else "Payable"
                            )
                        )
                        ExportUtils.exportReportToPdf(
                            context = context,
                            fileName = "OutstandingDetail_${System.currentTimeMillis()}.pdf",
                            subtitle = companyName,
                            title = "Outstanding Bill Detail - ${ledger?.name ?: "Party"}",
                            headers = headers,
                            widths = floatArrayOf(2f, 2.5f, 2.5f, 2.5f, 1.5f),
                            data = data
                        )
                    }) {
                        Icon(Icons.Default.PictureAsPdf, "Export PDF", tint = Color.White)
                    }
                    IconButton(onClick = {
                        val headers = listOf("Date", "Reference/Vch No", "Total Bill Amount", "Pending Amount", "Status")
                        val currentOutstandingVal = currentOutstanding
                        val data = listOf(
                            listOf(
                                currentOutstandingVal?.date?.let { dateFormatter.format(it) } ?: "",
                                voucherWithEntries?.voucher?.voucherNumber ?: "",
                                "%.2f".format(currentOutstandingVal?.originalAmount ?: 0.0),
                                "%.2f".format(currentOutstandingVal?.pendingAmount ?: 0.0),
                                if (currentOutstandingVal?.isDebit == true) "Receivable" else "Payable"
                            )
                        )
                        ExportUtils.exportReportToExcel(
                            context = context,
                            fileName = "OutstandingDetail_${System.currentTimeMillis()}.xlsx",
                            sheetName = "Bill Detail",
                            headers = headers,
                            data = data
                        )
                    }) {
                        Icon(Icons.Default.TableChart, "Export Excel", tint = Color.White)
                    }
                    IconButton(onClick = {
                        val v = voucherWithEntries?.voucher
                        if (v != null) {
                            ExportUtils.exportInvoiceToPdf(
                                context = context,
                                voucher = v,
                                customerLedger = ledger,
                                inventoryEntries = voucherWithEntries?.inventoryEntries ?: emptyList(),
                                stockItems = stockItemsMap.values.toList(),
                                units = unitsMap.values.toList(),
                                company = company,
                                share = true,
                                voucherEntries = voucherWithEntries?.voucherEntries ?: emptyList()
                            )
                        } else {
                            Toast.makeText(context, "Voucher data not loaded yet", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Receipt, "Print Invoice", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
        modifier = Modifier.background(radialBackground)
    ) { padding ->
        val isReceivable = currentOutstanding?.isDebit ?: true
        val statusColor = if (isReceivable) Color(0xFF00E676) else Color(0xFFFF5252)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B2A).copy(alpha = 0.9f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    AutoShrinkText(
                        text = ledger?.name ?: "Loading...",
                        color = Color.White,
                        maxFontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AutoShrinkText(
                        text = "Ref: ${voucherWithEntries?.voucher?.voucherNumber}  •  ${voucherWithEntries?.voucher?.date?.let { dateFormatter.format(it) }}",
                        color = Color(0xFFB0B0C0),
                        maxFontSize = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(TerminologyManager.getTerm("Total Bill Amount"), color = Color(0xFF808090), fontSize = 16.sp)
                        AutoShrinkText(
                            text = "₹ ${"%.2f".format(currentOutstanding?.originalAmount ?: 0.0)}",
                            color = Color(0xFFE0E0E0),
                            maxFontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(TerminologyManager.getTerm("Payment History"), color = Color(0xFF808090), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (currentOutstanding?.paymentHistory.isNullOrEmpty()) {
                        Text(
                            text = "No allocations recorded.",
                            color = Color(0xFF505060),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        currentOutstanding?.paymentHistory?.forEach { history ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AutoShrinkText(
                                    text = "Received on ${dateFormatter.format(history.date)}",
                                    color = Color(0xFFB0B0C0),
                                    maxFontSize = 14.sp
                                )
                                AutoShrinkText(
                                    text = "₹ ${"%.2f".format(history.amount)}",
                                    color = Color(0xFF00E676),
                                    maxFontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(TerminologyManager.getTerm("Total Remaining"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        AutoShrinkText(
                            text = "₹ ${"%.2f".format(currentOutstanding?.pendingAmount ?: 0.0)}",
                            color = statusColor,
                            maxFontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // --- ITEMS SECTION ---
                    val inventoryEntries = voucherWithEntries?.inventoryEntries ?: emptyList()
                    if (inventoryEntries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(TerminologyManager.getTerm("Items"), color = Color(0xFF808090), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        inventoryEntries.forEach { item ->
                            val stockItem = stockItemsMap[item.stockItemId]
                            val itemName = stockItem?.name ?: "Item"
                            val unitName = stockItem?.unitId?.let { unitsMap[it]?.name } ?: ""
                            
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AutoShrinkText(itemName, color = Color.White, maxFontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    AutoShrinkText("₹ ${"%.2f".format(item.amount.paiseToRupees())}", color = Color.White, maxFontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                AutoShrinkText(
                                    text = "${item.quantity} $unitName @ ₹ ${"%.2f".format(item.rate)}",
                                    color = Color(0xFF808090),
                                    maxFontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedButton(
                onClick = { 
                    navController.navigate("add_edit_voucher?voucherId=$voucherId")
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(TerminologyManager.getTerm("Edit Original Bill"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { showSettleDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = statusColor
                )
            ) {
                Text(
                    text = if (isReceivable) "Settle (Receive)" else "Settle (Pay)",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(120.dp))
        }
    }

    if (showSettleDialog && currentOutstanding != null) {
        SettlePaymentDialog(
            outstanding = currentOutstanding,
            ledgerName = ledger?.name ?: "",
            onDismiss = { showSettleDialog = false },
            onSettle = { amount, accountId, date ->
                val isFullSettlement = amount >= (currentOutstanding.pendingAmount - 0.01)
                voucherViewModel.settleBill(currentOutstanding, amount, accountId, date)
                showSettleDialog = false
                if (isFullSettlement) {
                    navController.navigateUp()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlePaymentDialog(
    outstanding: com.minhas.teller.data.repositories.OutstandingVoucher,
    ledgerName: String,
    onDismiss: () -> Unit,
    onSettle: (Double, Int, Date) -> Unit
) {
    var amountText by remember { mutableStateOf(outstanding.pendingAmount.toString()) }
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val ledgerRepository = remember { LedgerRepository(database.ledgerDao()) }
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    
    val cashBankLedgers by if (currentCompanyId != null) {
        ledgerRepository.getCashBankLedgers(currentCompanyId!!).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<com.minhas.teller.data.entities.Ledger>()) }
    }
    
    var selectedLedgerId by remember { mutableStateOf<Int?>(null) }
    var expanded by remember { mutableStateOf(false) }
    
    var selectedDate by remember { mutableStateOf(Date()) }
    val calendar = Calendar.getInstance()
    calendar.time = selectedDate
    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, day: Int ->
            val selectedCalendar = Calendar.getInstance()
            selectedCalendar.set(year, month, day)
            selectedDate = selectedCalendar.time
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    LaunchedEffect(cashBankLedgers) {
        if (selectedLedgerId == null && cashBankLedgers.isNotEmpty()) {
            selectedLedgerId = cashBankLedgers.first().id
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1C1B1E),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = if (outstanding.isDebit) "Did you receive the full amount?" else "Did you pay the full amount?",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                AutoShrinkText(
                    text = "Pending: ₹ ${"%.2f".format(outstanding.pendingAmount)}",
                    color = Color.LightGray,
                    maxFontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(if (outstanding.isDebit) "Amount received" else "Amount paid") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E676),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFF00E676),
                        unfocusedLabelColor = Color.Gray
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Account Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = cashBankLedgers.find { it.id == selectedLedgerId }?.name ?: "Select Account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (outstanding.isDebit) "Received In Account" else "Paid From Account") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF00E676),
                            unfocusedLabelColor = Color.Gray
                        )
                    )
                    // Overlay to capture clicks anywhere on the text field and trigger the dropdown
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { expanded = true }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        cashBankLedgers.forEach { ledgerItem ->
                            DropdownMenuItem(
                                text = { Text(ledgerItem.name) },
                                onClick = {
                                    selectedLedgerId = ledgerItem.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Date Selection Field
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(TerminologyManager.getTerm("Payment Date")) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { datePickerDialog.show() }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select Date", tint = Color.White)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF00E676),
                            unfocusedLabelColor = Color.Gray
                        )
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { datePickerDialog.show() }
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(TerminologyManager.getTerm("Discard"), color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(
                        onClick = { 
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            selectedLedgerId?.let { onSettle(amt, it, selectedDate) }
                        }
                    ) {
                        Text(TerminologyManager.getTerm("Settle"), color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
