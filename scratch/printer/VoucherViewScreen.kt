package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.utils.rupeesToPaise
import com.minhas.teller.data.repositories.StockItemRepository
import com.minhas.teller.data.repositories.UnitRepository
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.ui.viewmodels.*
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.utils.PrinterPrefs
import com.minhas.teller.utils.ThermalPrinterHelper
import com.minhas.teller.utils.WifiPrinterDiscovery
import com.minhas.teller.utils.DiscoveredWifiPrinter
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherViewScreen(navController: NavHostController, voucherId: Int) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val taxEnabled = remember { com.minhas.teller.utils.DashboardPrefs.isTaxSystemEnabled(context) }

    val voucherViewModel: VoucherViewModel = viewModel(factory = VoucherViewModelFactory(database.voucherDao(), database.voucherEntryDao(), database.inventoryEntryDao(), database.ledgerDao(), database.billAllocationDao(), database.auditLogDao(), db = database))
    val voucherWithEntries by voucherViewModel.getVoucherWithAllEntriesById(voucherId).collectAsState(initial = null)
    val allLedgers by voucherViewModel.allLedgers.collectAsState()
    val billAllocations by voucherViewModel.getBillAllocationsForVoucher(voucherId).collectAsState(initial = emptyList())

    val companyRepository = CompanyRepository(database.companyDao())
    val companyViewModel: CompanyViewModel = viewModel(factory = CompanyViewModelFactory(companyRepository))
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val currentCompany by produceState<Company?>(initialValue = null, currentCompanyId) {
        currentCompanyId?.let { id ->
            companyViewModel.getCompany(id).collect { value = it }
        }
    }
    val companyName = currentCompany?.name ?: "Teller App"

    val stockItemRepository = StockItemRepository(database.stockItemDao())
    val stockItemViewModel: StockItemViewModel = viewModel(factory = StockItemViewModelFactory(stockItemRepository))
    val allStockItems by stockItemViewModel.allStockItems.collectAsState()

    val unitRepository = UnitRepository(database.unitDao())
    val unitViewModel: UnitViewModel = viewModel(factory = UnitViewModelFactory(unitRepository))
    val allUnits by unitViewModel.allUnits.collectAsState()

    val scope = rememberCoroutineScope()
    var showExportMenu by remember { mutableStateOf(false) }
    var showPrintDialog by remember { mutableStateOf(false) }
    var showSmartInterceptDialog by remember { mutableStateOf(false) }
    var showEwayBillDialog by remember { mutableStateOf(false) }
    var ewayPartyAddress by remember { mutableStateOf("") }
    var ewayPartyPlace by remember { mutableStateOf("") }
    var ewayPartyPincode by remember { mutableStateOf("") }
    var ewayPartyDistance by remember { mutableStateOf("") }
    var ewayMsg by remember { mutableStateOf("") }
    var rememberChoice by remember { mutableStateOf(PrinterPrefs.isOneClickEnabled(context)) }
    var isPrintingDirectly by remember { mutableStateOf(false) }

    val wifiDiscovery = remember { WifiPrinterDiscovery(context) }
    val discoveredWifiPrinters by wifiDiscovery.printers.collectAsState()
    val isScanningWifi by wifiDiscovery.isScanning.collectAsState()
    val isPrintStationActive by com.minhas.teller.web.WebCompanionManager.isPrintStationActive.collectAsState()
    val companionIp = remember { com.minhas.teller.web.WebCompanionManager.getLocalIpAddress(context) }

    val pairedPrinters = remember { ThermalPrinterHelper.getPairedPrinters(context) }

    LaunchedEffect(showPrintDialog) {
        if (showPrintDialog) {
            wifiDiscovery.startDiscovery()
            if (!com.minhas.teller.web.WebCompanionManager.isServerRunning.value) {
                com.minhas.teller.web.WebCompanionManager.startServer(context)
            }
        } else {
            wifiDiscovery.stopDiscovery()
        }
    }
    DisposableEffect(Unit) {
        onDispose { wifiDiscovery.stopDiscovery() }
    }

    fun executeCounterPcPrint(vwe: com.minhas.teller.data.entities.VoucherWithAllEntries) {
        showPrintDialog = false
        if (rememberChoice) {
            PrinterPrefs.setDefaultMode(context, PrinterPrefs.MODE_COUNTER_PC)
            PrinterPrefs.setOneClickEnabled(context, true)
        }
        com.minhas.teller.web.WebCompanionManager.queuePrintJob(vwe.voucher.id)
        android.widget.Toast.makeText(context, "Invoice sent to Counter PC! Printing on Windows printer...", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun executeA4Print(vwe: com.minhas.teller.data.entities.VoucherWithAllEntries, bypassIntercept: Boolean = false) {
        if (!bypassIntercept && !ThermalPrinterHelper.isSystemPrintServiceEnabled(context)) {
            showPrintDialog = false
            showSmartInterceptDialog = true
            return
        }
        showPrintDialog = false
        if (rememberChoice) {
            PrinterPrefs.setDefaultMode(context, PrinterPrefs.MODE_A4_SYSTEM)
            PrinterPrefs.setOneClickEnabled(context, true)
        }
        val isSales = vwe.voucher.type == "Sales"
        val partyEntry = vwe.voucherEntries.firstOrNull { if (isSales) it.isDebit else !it.isDebit }
        val partyLedger = allLedgers.find { it.id == partyEntry?.ledgerId }
        ExportUtils.exportInvoiceToPdf(
            context = context,
            voucher = vwe.voucher,
            customerLedger = partyLedger,
            inventoryEntries = vwe.inventoryEntries,
            stockItems = allStockItems,
            units = allUnits,
            company = currentCompany,
            billAllocations = billAllocations,
            share = false,
            onGenerated = { file ->
                ThermalPrinterHelper.printDocumentWithSystem(context, file, "${vwe.voucher.type} #${vwe.voucher.voucherNumber}")
            }
        )
    }

    // Android 12+ needs the "Nearby devices" (Bluetooth) permission at runtime before printing.
    var pendingBtPrint by remember { mutableStateOf<Triple<com.minhas.teller.data.entities.VoucherWithAllEntries, String, String>?>(null) }
    var btPermissionResult by remember { mutableStateOf<Boolean?>(null) }
    val btPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> btPermissionResult = granted.values.all { it } }

    fun executeBluetoothThermalPrint(vwe: com.minhas.teller.data.entities.VoucherWithAllEntries, address: String, name: String) {
        showPrintDialog = false
        if (!ThermalPrinterHelper.hasBluetoothPermission(context)) {
            pendingBtPrint = Triple(vwe, address, name)
            btPermissionLauncher.launch(ThermalPrinterHelper.bluetoothPermissions())
            return
        }
        if (rememberChoice) {
            PrinterPrefs.setBluetoothDevice(context, address, name)
            PrinterPrefs.setDefaultMode(context, PrinterPrefs.MODE_THERMAL_BT)
            PrinterPrefs.setOneClickEnabled(context, true)
        }
        scope.launch {
            isPrintingDirectly = true
            android.widget.Toast.makeText(context, "Printing to $name...", android.widget.Toast.LENGTH_SHORT).show()
            val isSales = vwe.voucher.type == "Sales"
            val partyEntry = vwe.voucherEntries.firstOrNull { if (isSales) it.isDebit else !it.isDebit }
            val customerName = allLedgers.find { it.id == partyEntry?.ledgerId }?.name ?: "Counter Sale"
            val bytes = ThermalPrinterHelper.buildReceiptBytes(
                company = currentCompany,
                voucherWithAllEntries = vwe,
                stockItems = allStockItems,
                customerName = customerName,
                paperWidth = PrinterPrefs.getPaperWidth(context)
            )
            val ok = ThermalPrinterHelper.printToBluetoothDevice(address, bytes, context)
            if (ok) {
                android.widget.Toast.makeText(context, "Printed successfully via Bluetooth!", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Bluetooth print failed. Opening Printer Hub...", android.widget.Toast.LENGTH_SHORT).show()
                navController.navigate(com.minhas.teller.navigation.Screen.ThermalPrinter.createRoute(vwe.voucher.id))
            }
            isPrintingDirectly = false
        }
    }

    LaunchedEffect(btPermissionResult) {
        val granted = btPermissionResult ?: return@LaunchedEffect
        btPermissionResult = null
        val pending = pendingBtPrint
        pendingBtPrint = null
        if (pending != null) {
            if (granted) executeBluetoothThermalPrint(pending.first, pending.second, pending.third)
            else android.widget.Toast.makeText(context, "Allow \"Nearby devices\" so Teller can print to your Bluetooth printer", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun executeWifiThermalPrint(vwe: com.minhas.teller.data.entities.VoucherWithAllEntries, ip: String, port: Int, name: String) {
        showPrintDialog = false
        if (rememberChoice) {
            PrinterPrefs.setWifiPrinter(context, ip, port, name)
            PrinterPrefs.setDefaultMode(context, PrinterPrefs.MODE_THERMAL_WIFI)
            PrinterPrefs.setOneClickEnabled(context, true)
        }
        scope.launch {
            isPrintingDirectly = true
            android.widget.Toast.makeText(context, "Connecting to $name ($ip)...", android.widget.Toast.LENGTH_SHORT).show()
            val isSales = vwe.voucher.type == "Sales"
            val partyEntry = vwe.voucherEntries.firstOrNull { if (isSales) it.isDebit else !it.isDebit }
            val customerName = allLedgers.find { it.id == partyEntry?.ledgerId }?.name ?: "Counter Sale"
            val bytes = ThermalPrinterHelper.buildReceiptBytes(
                company = currentCompany,
                voucherWithAllEntries = vwe,
                stockItems = allStockItems,
                customerName = customerName,
                paperWidth = PrinterPrefs.getPaperWidth(context)
            )
            val ok = ThermalPrinterHelper.printToNetworkPrinter(ip, port, bytes)
            if (ok) {
                android.widget.Toast.makeText(context, "Printed successfully to $name!", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Network print failed on $ip:$port", android.widget.Toast.LENGTH_LONG).show()
            }
            isPrintingDirectly = false
        }
    }

    fun triggerPrint() {
        val vwe = voucherWithEntries ?: return
        if (PrinterPrefs.isOneClickEnabled(context)) {
            when (PrinterPrefs.getDefaultMode(context)) {
                PrinterPrefs.MODE_A4_SYSTEM -> {
                    executeA4Print(vwe)
                    return
                }
                PrinterPrefs.MODE_THERMAL_BT -> {
                    val (addr, name) = PrinterPrefs.getBluetoothDevice(context)
                    if (!addr.isNullOrBlank()) {
                        executeBluetoothThermalPrint(vwe, addr, name ?: "Bluetooth Printer")
                        return
                    }
                }
                PrinterPrefs.MODE_THERMAL_WIFI -> {
                    val (ip, port, name) = PrinterPrefs.getWifiPrinter(context)
                    if (ip.isNotBlank()) {
                        executeWifiThermalPrint(vwe, ip, port, name)
                        return
                    }
                }
                PrinterPrefs.MODE_COUNTER_PC -> {
                    executeCounterPcPrint(vwe)
                    return
                }
            }
        }
        showPrintDialog = true
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = "Voucher View",
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = false,
                navController = navController,
                actions = {
                    IconButton(onClick = {
                        navController.navigate("dashboard") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { triggerPrint() }) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print Options",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Export",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm("Print Receipt / Invoice")) },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF00E5FF)) },
                            onClick = {
                                showExportMenu = false
                                triggerPrint()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm("Edit Entry")) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showExportMenu = false
                                voucherWithEntries?.let { vwe ->
                                    navController.navigate(com.minhas.teller.navigation.Screen.AddEditVoucher.route + "?voucherId=${vwe.voucher.id}")
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm("Duplicate Entry")) },
                            onClick = {
                                showExportMenu = false
                                voucherWithEntries?.let { vwe ->
                                    navController.navigate(com.minhas.teller.navigation.Screen.AddEditVoucher.route + "?voucherId=${vwe.voucher.id}&type=${vwe.voucher.type}&isDuplicate=true")
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm("Export to PDF")) },
                            onClick = {
                                showExportMenu = false
                                voucherWithEntries?.let { vwe ->
                                    if (vwe.voucher.type == "Sales") {
                                        val customerEntry = vwe.voucherEntries.firstOrNull { it.isDebit }
                                        val customerLedger = allLedgers.find { it.id == customerEntry?.ledgerId }
                                        ExportUtils.exportInvoiceToPdf(context, vwe.voucher, customerLedger, vwe.inventoryEntries, allStockItems, allUnits, currentCompany, billAllocations)
                                    } else if (vwe.voucher.type == "Purchase") {
                                        val supplierEntry = vwe.voucherEntries.firstOrNull { !it.isDebit }
                                        val supplierLedger = allLedgers.find { it.id == supplierEntry?.ledgerId }
                                        ExportUtils.exportInvoiceToPdf(context, vwe.voucher, supplierLedger, vwe.inventoryEntries, allStockItems, allUnits, currentCompany, billAllocations)
                                    } else {
                                        val headers = listOf("Particulars", "Debit", "Credit")
                                        val data = vwe.voucherEntries.map { entry ->
                                            val ledger = allLedgers.find { it.id == entry.ledgerId }
                                            listOf(
                                                ledger?.name ?: "Unknown",
                                                if (entry.isDebit) "%.2f".format(entry.amount.paiseToRupees()) else "",
                                                if (!entry.isDebit) "%.2f".format(entry.amount.paiseToRupees()) else ""
                                            )
                                        }
                                        ExportUtils.exportReportToPdf(context, "Voucher_${vwe.voucher.voucherNumber.replace("/", "_")}.pdf", companyName, "Voucher: ${vwe.voucher.voucherNumber}", headers, floatArrayOf(3f, 1.5f, 1.5f), data)
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(TerminologyManager.getTerm("Export to Excel")) },
                            onClick = {
                                showExportMenu = false
                                voucherWithEntries?.let { vwe ->
                                    if (vwe.voucher.type == "Sales") {
                                        val customerEntry = vwe.voucherEntries.firstOrNull { it.isDebit }
                                        val customerLedger = allLedgers.find { it.id == customerEntry?.ledgerId }
                                        ExportUtils.exportInvoiceToExcel(context, vwe.voucher, customerLedger, vwe.inventoryEntries, allStockItems, allUnits, currentCompany)
                                    } else if (vwe.voucher.type == "Purchase") {
                                        val supplierEntry = vwe.voucherEntries.firstOrNull { !it.isDebit }
                                        val supplierLedger = allLedgers.find { it.id == supplierEntry?.ledgerId }
                                        ExportUtils.exportInvoiceToExcel(context, vwe.voucher, supplierLedger, vwe.inventoryEntries, allStockItems, allUnits, currentCompany)
                                    } else {
                                        val headers = listOf("Particulars", "Debit", "Credit")
                                        val data = vwe.voucherEntries.map { entry ->
                                            val ledger = allLedgers.find { it.id == entry.ledgerId }
                                            listOf(
                                                ledger?.name ?: "Unknown",
                                                if (entry.isDebit) "%.2f".format(entry.amount.paiseToRupees()) else "",
                                                if (!entry.isDebit) "%.2f".format(entry.amount.paiseToRupees()) else ""
                                            )
                                        }
                                        ExportUtils.exportReportToExcel(context, "Voucher_${vwe.voucher.voucherNumber.replace("/", "_")}.xlsx", "Voucher", headers, data)
                                    }
                                }
                            }
                        )

                        if (voucherWithEntries?.voucher?.type == "Sales") {
                            DropdownMenuItem(
                                text = { Text(TerminologyManager.getTerm("Export e-Way Bill JSON")) },
                                leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                                onClick = {
                                    showExportMenu = false
                                    val customerEntry = voucherWithEntries?.voucherEntries?.firstOrNull { it.isDebit }
                                    if (allLedgers.find { it.id == customerEntry?.ledgerId } == null) {
                                        android.widget.Toast.makeText(context, "Customer ledger required", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        ewayMsg = ""
                                        showEwayBillDialog = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Send Bill to Government (e-Invoice)") },
                                leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                                onClick = {
                                    showExportMenu = false
                                    voucherWithEntries?.let { vw ->
                                        navController.navigate(com.minhas.teller.navigation.Screen.EInvoice.createRoute(vw.voucher.id))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(TerminologyManager.getTerm("WhatsApp with UPI Pay Link")) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showExportMenu = false
                                    voucherWithEntries?.let { vw ->
                                        currentCompany?.let { comp ->
                                            val customerEntry = vw.voucherEntries.firstOrNull { it.isDebit }
                                            val customerLedger = allLedgers.find { it.id == customerEntry?.ledgerId }
                                            if (customerLedger != null) {
                                                val total = vw.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                                                val upiId = comp.upiId ?: ""
                                                val payLink = if (upiId.isNotBlank()) {
                                                    "upi://pay?pa=$upiId&pn=${android.net.Uri.encode(comp.name)}&am=${String.format(Locale.US, "%.2f", total)}&tn=Inv-${android.net.Uri.encode(vw.voucher.voucherNumber)}"
                                                } else ""
                                                val msg = "Dear ${customerLedger.name},\nHere is your invoice #${vw.voucher.voucherNumber} from ${comp.name} for ₹${String.format(Locale.US, "%,.2f", total)}.\n" +
                                                        (if (payLink.isNotBlank()) "\nPay instantly via UPI: $payLink\n" else "") +
                                                        "\nThank you for your business!"
                                                try {
                                                    val waIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                        data = android.net.Uri.parse("https://api.whatsapp.com/send?text=${android.net.Uri.encode(msg)}" + (if (!customerLedger.phoneNumber.isNullOrBlank()) "&phone=${customerLedger.phoneNumber}" else ""))
                                                    }
                                                    context.startActivity(waIntent)
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, "WhatsApp not installed", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (showEwayBillDialog) {
            voucherWithEntries?.let { vwe ->
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFF00E5FF), unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                )
                LaunchedEffect(vwe.voucher.id) {
                    if (ewayPartyAddress.isBlank()) {
                        val customerEntry = vwe.voucherEntries.firstOrNull { it.isDebit }
                        allLedgers.find { it.id == customerEntry?.ledgerId }?.let { ledger ->
                            com.minhas.teller.utils.EInvoiceStore.partyDetails(ledger.id, context)?.let { saved ->
                                ewayPartyAddress = saved.address
                                ewayPartyPlace = saved.place
                                ewayPartyPincode = saved.pincode
                            }
                        }
                    }
                }
                AlertDialog(
                    onDismissRequest = { showEwayBillDialog = false },
                    containerColor = Color(0xFF1B2042),
                    title = { Text("e-Way Bill JSON", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("The e-Way Bill portal needs the delivery address and distance to generate this.", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                            OutlinedTextField(
                                value = ewayPartyAddress, onValueChange = { ewayPartyAddress = it; ewayMsg = "" },
                                label = { Text("Delivery address (required)") }, modifier = Modifier.fillMaxWidth(), maxLines = 2, colors = fieldColors
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = ewayPartyPlace, onValueChange = { ewayPartyPlace = it; ewayMsg = "" },
                                    label = { Text("City") }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors
                                )
                                OutlinedTextField(
                                    value = ewayPartyPincode,
                                    onValueChange = { ewayPartyPincode = it.filter { c -> c.isDigit() }.take(6); ewayMsg = "" },
                                    label = { Text("PIN code") }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors
                                )
                            }
                            OutlinedTextField(
                                value = ewayPartyDistance,
                                onValueChange = { ewayPartyDistance = it.filter { c -> c.isDigit() }.take(4) },
                                label = { Text("Transport distance in km") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors
                            )
                            if (ewayMsg.isNotBlank()) Text(ewayMsg, color = Color(0xFFFF8A80), fontSize = 12.sp)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            currentCompany?.let { comp ->
                                val customerEntry = vwe.voucherEntries.firstOrNull { it.isDebit }
                                val customerLedger = allLedgers.find { it.id == customerEntry?.ledgerId }
                                if (customerLedger == null) {
                                    ewayMsg = "Customer ledger required"
                                } else {
                                    val stockMap = allStockItems.associateBy { it.id }
                                    val problems = com.minhas.teller.utils.GstEwayBillExporter.validateForGovernmentPortal(
                                        vwe, customerLedger, stockMap, ewayPartyAddress, ewayPartyPincode, forEInvoice = false
                                    )
                                    if (problems.isNotEmpty()) {
                                        ewayMsg = problems.joinToString("\n")
                                    } else {
                                        val unitsMap = allUnits.associateBy { it.id }
                                        val json = com.minhas.teller.utils.GstEwayBillExporter.generateEwayBillJson(
                                            comp, vwe, customerLedger, stockMap,
                                            partyAddress = ewayPartyAddress, partyPlace = ewayPartyPlace, partyPincode = ewayPartyPincode,
                                            transportDistanceKm = ewayPartyDistance.toIntOrNull(), unitsMap = unitsMap
                                        )
                                        com.minhas.teller.utils.EInvoiceStore.savePartyDetails(
                                            customerLedger.id, ewayPartyAddress, ewayPartyPlace, ewayPartyPincode, context
                                        )
                                        com.minhas.teller.utils.GstEwayBillExporter.shareJsonFile(context, json, "eWayBill_" + vwe.voucher.voucherNumber + ".json")
                                        showEwayBillDialog = false
                                    }
                                }
                            }
                        }) { Text("Send JSON", color = Color(0xFF00E5FF)) }
                    },
                    dismissButton = { TextButton(onClick = { showEwayBillDialog = false }) { Text("Cancel", color = Color.White) } }
                )
            }
        }
        if (showPrintDialog) {
            voucherWithEntries?.let { vwe ->
                AlertDialog(
                    onDismissRequest = { showPrintDialog = false },
                    containerColor = Color(0xFF1B2042),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(TerminologyManager.getTerm("Print Options"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 480.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Current Default Printer Banner (if active)
                            if (PrinterPrefs.isOneClickEnabled(context)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF00E5FF).copy(alpha = 0.12f))
                                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(TerminologyManager.getTerm("⚡ 1-Click Default Printer Active:"), color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(PrinterPrefs.getFriendlyPrinterSummary(context), color = Color.White, fontSize = 11.sp)
                                        }
                                        TextButton(
                                            onClick = {
                                                PrinterPrefs.resetDefaultPrinter(context)
                                                rememberChoice = false
                                                android.widget.Toast.makeText(context, "1-Click default printer cleared", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(TerminologyManager.getTerm("Reset"), color = Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // ── Wi-Fi Auto-Discovery Section (Zero System Settings) ──
                            if (discoveredWifiPrinters.isNotEmpty()) {
                                Text(TerminologyManager.getTerm("Wi-Fi Printers Found (1-Click Direct):"), color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                discoveredWifiPrinters.forEach { wp ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                                            .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(wp.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text("Direct IP: ${wp.host}:${wp.port} • No setup needed", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                                }
                                            }
                                            Button(
                                                onClick = { executeWifiThermalPrint(vwe, wp.host, wp.port, wp.name) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(TerminologyManager.getTerm("Print"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            } else if (isScanningWifi) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color(0xFF00E5FF))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(TerminologyManager.getTerm("Searching for Wi-Fi & network printers..."), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                                }
                            }

                            // ── Counter PC Print Station (Windows USB Printer Bridge) ──
                            if (isPrintStationActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF00E676).copy(alpha = 0.15f))
                                        .border(1.5.dp, Color(0xFF00E676), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(TerminologyManager.getTerm("Counter PC (USB Printer)"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(TerminologyManager.getTerm("🟢 Online & Ready • Prints via Windows Spooler"), color = Color(0xFF00E676), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        Button(
                                            onClick = { executeCounterPcPrint(vwe) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(TerminologyManager.getTerm("Print to PC"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            // ── Option 1 (PRIMARY): Standard A4 Tax Invoice (HP, Epson, Canon, Brother) ──
                            val isPrintServiceOn = ThermalPrinterHelper.isSystemPrintServiceEnabled(context)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.08f))
                                    .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(TerminologyManager.getTerm("Standard A4 Tax Invoice"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(TerminologyManager.getTerm("HP, Epson EcoTank, Canon, Brother (Wi-Fi / USB)"), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(TerminologyManager.getTerm("A4 Full Page"), color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (!isPrintServiceOn) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFFFB74D).copy(alpha = 0.15f))
                                                .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                .padding(8.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(TerminologyManager.getTerm("⚠️ Phone Print Discovery is OFF: Android will only save a PDF unless enabled."),
                                                    color = Color(0xFFFFB74D),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Button(
                                                        onClick = { ThermalPrinterHelper.openSystemPrintSettings(context) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(TerminologyManager.getTerm("Turn ON Print Service (1-Tap)"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    }
                                                    TextButton(
                                                        onClick = { executeA4Print(vwe, bypassIntercept = true) },
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(TerminologyManager.getTerm("Print Anyway"), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .padding(6.dp)
                                        ) {
                                            Text(TerminologyManager.getTerm("💡 How to Print: On the next screen, tap 'Save as PDF ▾' at top to pick your printer. Android will remember it forever!"),
                                                color = Color(0xFF80D8FF),
                                                fontSize = 10.sp
                                            )
                                        }
                                        Button(
                                            onClick = { executeA4Print(vwe) },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Print, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(TerminologyManager.getTerm("Print A4 Tax Invoice Now"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            // ── Option 2: WhatsApp Share (Invoice PDF + UPI Pay Link) ──
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF25D366).copy(alpha = 0.08f))
                                    .border(1.dp, Color(0xFF25D366).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                    .clickable {
                                        showPrintDialog = false
                                        val isSales = vwe.voucher.type == "Sales"
                                        val partyEntry = vwe.voucherEntries.firstOrNull { if (isSales) it.isDebit else !it.isDebit }
                                        val partyLedger = allLedgers.find { it.id == partyEntry?.ledgerId }
                                        ExportUtils.exportInvoiceToPdf(
                                            context = context,
                                            voucher = vwe.voucher,
                                            customerLedger = partyLedger,
                                            inventoryEntries = vwe.inventoryEntries,
                                            stockItems = allStockItems,
                                            units = allUnits,
                                            company = currentCompany,
                                            billAllocations = billAllocations,
                                            share = true
                                        )
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(TerminologyManager.getTerm("Send on WhatsApp / Share PDF"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(TerminologyManager.getTerm("Share A4 PDF directly with customer or shop PC"), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(20.dp))
                                }
                            }

                            // ── Option 3: Thermal POS Receipt Printer (58mm / 80mm Roll) ──
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Receipt, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(TerminologyManager.getTerm("Thermal POS Receipt Printer"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(TerminologyManager.getTerm("2\" (58mm) or 3\" (80mm) roll via Bluetooth or USB OTG"), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                                        }
                                    }

                                    if (pairedPrinters.isNotEmpty()) {
                                        val first = pairedPrinters.first()
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Paired: ${first.name}", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Button(
                                                    onClick = { executeBluetoothThermalPrint(vwe, first.address, first.name) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(TerminologyManager.getTerm("⚡ Print Roll"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        showPrintDialog = false
                                                        navController.navigate(com.minhas.teller.navigation.Screen.ThermalPrinter.createRoute(vwe.voucher.id))
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                                ) {
                                                    Text(TerminologyManager.getTerm("Setup"), color = Color.White, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                showPrintDialog = false
                                                navController.navigate(com.minhas.teller.navigation.Screen.ThermalPrinter.createRoute(vwe.voucher.id))
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(TerminologyManager.getTerm("Setup Bluetooth / USB Receipt Roll"), color = Color.White, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            // ── Non-WiFi USB Printer Help Guide ──
                            if (!isPrintStationActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color(0xFF81D4FA), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(TerminologyManager.getTerm("Using a USB Printer on your Shop PC?"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                        Text(
                                            "Open http://${companionIp}:8080/print-station on your PC once to auto-print any invoice here with 1 tap!",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }

                            // ── Remember Preference Toggle ──
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { rememberChoice = !rememberChoice }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = rememberChoice,
                                    onCheckedChange = { rememberChoice = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF00E5FF),
                                        checkmarkColor = Color.Black
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Remember my choice (1-Click Print next time)",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showPrintDialog = false }) {
                            Text(TerminologyManager.getTerm("Cancel"), color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                )
            }
        }

        // ── Smart Intercept Dialog (Catches Missing Print Service Before "Save as PDF" Trap) ──
        if (showSmartInterceptDialog) {
            voucherWithEntries?.let { vwe ->
                AlertDialog(
                    onDismissRequest = { showSmartInterceptDialog = false },
                    containerColor = Color(0xFF1E1538),
                    icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(36.dp)) },
                    title = {
                        Text(TerminologyManager.getTerm("Phone Print Service is OFF"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(TerminologyManager.getTerm("Your Android system print service is currently turned OFF."),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(TerminologyManager.getTerm("If you proceed right now, Android will only download a PDF file to your phone's storage instead of sending it to a printer."),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                            Text(TerminologyManager.getTerm("Tap below to turn ON Print Service in 1 tap, or print using a Bluetooth receipt printer instead."),
                                color = Color(0xFF00E5FF),
                                fontSize = 12.sp
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSmartInterceptDialog = false
                                ThermalPrinterHelper.openSystemPrintSettings(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(TerminologyManager.getTerm("Turn ON Print Service (1 Tap)"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    showSmartInterceptDialog = false
                                    navController.navigate(com.minhas.teller.navigation.Screen.ThermalPrinter.createRoute(vwe.voucher.id))
                                }
                            ) {
                                Text(TerminologyManager.getTerm("Use Bluetooth POS"), color = Color(0xFF80D8FF), fontSize = 11.sp)
                            }
                            TextButton(
                                onClick = {
                                    showSmartInterceptDialog = false
                                    executeA4Print(vwe, bypassIntercept = true)
                                }
                            ) {
                                Text(TerminologyManager.getTerm("Save PDF Anyway"), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            }
                        }
                    }
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            voucherWithEntries?.let { vwe ->
                val labelColor = Color.White.copy(alpha = 0.6f)
                val type = vwe.voucher.type
                val dateStr = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault()).format(vwe.voucher.date)

                // ── Derive context-aware labels for this voucher type ──
                // Labels are derived from the actual ledger GROUP, not just voucher type,
                // because you might sell to a supplier or buy from a customer.
                data class EntryRole(val label: String, val ledgerName: String, val amount: Long, val icon: androidx.compose.ui.graphics.vector.ImageVector, val isTaxEntry: Boolean)

                // Helper: map a ledger group to a plain-language party label
                fun partyLabel(gp: String): String = when {
                    gp.contains("creditor") || gp.contains("supplier") -> "Supplier"
                    gp.contains("debtor") || gp.contains("customer") -> "Customer"
                    gp.contains("finance") -> "Finance Co."
                    gp.contains("cash") -> "Cash"
                    gp.contains("bank") -> "Bank"
                    else -> "Party"
                }

                val roleEntries = remember(vwe, allLedgers) {
                    buildList {
                        vwe.voucherEntries.forEach { entry ->
                            val ledger = allLedgers.find { it.id == entry.ledgerId }
                            val name = ledger?.name ?: "Unknown"
                            val gp = ledger?.group?.lowercase() ?: ""
                            val isTax = gp.contains("duties") || gp.contains("tax") || gp.contains("gst")
                            val isCashBank = gp.contains("cash") || gp.contains("bank")
                            if (entry.isDebit) {
                                when (type) {
                                    "Sales" -> {
                                        val label = if (isTax) "Tax Collected" else partyLabel(gp)
                                        add(EntryRole(label, name, entry.amount, if (isTax) Icons.Default.AccountBalance else Icons.Default.Person, isTax))
                                    }
                                    "Purchase" -> {
                                        if (isTax) add(EntryRole("Tax Paid", name, entry.amount, Icons.Default.AccountBalance, true))
                                        else add(EntryRole("Purchase Cost", name, entry.amount, Icons.Default.ShoppingCart, false))
                                    }
                                    "Receipt" -> add(EntryRole("Deposited To", name, entry.amount, Icons.Default.AccountBalance, isTax))
                                    "Payment" -> {
                                        val label = if (isTax) "Tax" else partyLabel(gp).let { if (it in listOf("Cash", "Bank")) "Paid To" else it }
                                        add(EntryRole(label, name, entry.amount, Icons.Default.Person, isTax))
                                    }
                                    "Quotation" -> add(EntryRole(partyLabel(gp), name, entry.amount, Icons.Default.Person, isTax))
                                    else -> add(EntryRole(if (isTax) "Tax" else if (isCashBank) "Received In" else partyLabel(gp), name, entry.amount, Icons.Default.AccountBalance, isTax))
                                }
                            } else {
                                when (type) {
                                    "Sales" -> {
                                        if (isTax) add(EntryRole("Tax Collected", name, entry.amount, Icons.Default.AccountBalance, true))
                                        else add(EntryRole("Sales Revenue", name, entry.amount, Icons.AutoMirrored.Filled.TrendingUp, false))
                                    }
                                    "Purchase" -> {
                                        val label = if (isTax) "Tax" else partyLabel(gp)
                                        add(EntryRole(label, name, entry.amount, Icons.Default.Person, isTax))
                                    }
                                    "Receipt" -> {
                                        val label = if (isTax) "Tax" else partyLabel(gp).let { if (it in listOf("Cash", "Bank")) "Paid From" else it }
                                        add(EntryRole(label, name, entry.amount, Icons.Default.Person, isTax))
                                    }
                                    "Payment" -> add(EntryRole("Paid From", name, entry.amount, Icons.Default.AccountBalance, isTax))
                                    "Quotation" -> add(EntryRole("Sales Account", name, entry.amount, Icons.AutoMirrored.Filled.TrendingUp, isTax))
                                    else -> add(EntryRole(if (isTax) "Tax" else if (isCashBank) "Given By" else partyLabel(gp), name, entry.amount, Icons.Default.AccountBalance, isTax))
                                }
                            }
                        }
                    }
                }

                // ── Derive screen title & icon ──
                val screenTitle = when (type) {
                    "Sales" -> "Sale Invoice"
                    "Purchase" -> "Purchase Invoice"
                    "Receipt" -> "Money Received"
                    "Payment" -> "Money Paid"
                    "Quotation" -> "Quotation"
                    "Journal" -> "Journal Entry"
                    else -> type
                }
                val screenIcon = when (type) {
                    "Sales" -> Icons.AutoMirrored.Filled.TrendingUp
                    "Purchase" -> Icons.Default.ShoppingCart
                    "Receipt" -> Icons.Default.ArrowDownward
                    "Payment" -> Icons.Default.ArrowUpward
                    "Quotation" -> Icons.Default.Description
                    else -> Icons.Default.Receipt
                }

                // ── Resolve party/counterparty ledger for the header ──
                // Party = any non-tax, non-cash/bank person/company involved in the transaction
                val partyRoleEntry = roleEntries.firstOrNull {
                    !it.isTaxEntry && it.label !in listOf("Sales Revenue", "Purchase Cost", "Deposited To", "Paid From",
                        "Sales Account", "Cash", "Bank", "Received In", "Given By")
                }
                val accountRoleEntry = roleEntries.firstOrNull {
                    !it.isTaxEntry && it.label in listOf("Sales Revenue", "Purchase Cost", "Deposited To", "Paid From")
                }

                // Grand total from amounts
                val grandTotal = vwe.inventoryEntries.let { inv ->
                    val t = inv.sumOf { it.totalAmount }
                    if (t > 0L) t else inv.sumOf { (it.quantity * it.rate).rupeesToPaise() }
                }.let { if (it > 0L) it else roleEntries.filter { !it.isTaxEntry }.maxOfOrNull { it.amount } ?: 0L }
                val taxTotal = vwe.inventoryEntries.sumOf { it.taxAmount }

                // ── 1. HEADER CARD: What transaction is this? ──
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(screenIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(screenTitle, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text("$dateStr  •  ${vwe.voucher.voucherNumber}", color = labelColor, fontSize = 13.sp)
                        }
                    }

                    if (partyRoleEntry != null) {
                        Spacer(modifier = Modifier.height(18.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(partyRoleEntry.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(partyRoleEntry.label.uppercase(), color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(partyRoleEntry.ledgerName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (accountRoleEntry != null && accountRoleEntry.ledgerName != partyRoleEntry?.ledgerName) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(accountRoleEntry.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(accountRoleEntry.label.uppercase(), color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(accountRoleEntry.ledgerName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (!vwe.voucher.narration.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = labelColor, modifier = Modifier.size(16.dp).offset(y = 2.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(vwe.voucher.narration, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }

                // ── 2. ITEMS CARD (for Sales, Purchase, Quotation) ──
                if (vwe.inventoryEntries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val itemsLabel = when (type) {
                        "Sales", "Quotation" -> "Items Sold"
                        "Purchase" -> "Items Purchased"
                        else -> "Items"
                    }
                    GlassCard {
                        Text(itemsLabel, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(14.dp))

                        vwe.inventoryEntries.forEachIndexed { index, entry ->
                            val stockItem = allStockItems.find { it.id == entry.stockItemId }
                            val unit = allUnits.find { it.id == stockItem?.unitId }
                            val unitSymbol = unit?.name ?: ""
                            val qtyStr = if (entry.quantity % 1 == 0.0) entry.quantity.toInt().toString() else entry.quantity.toString()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stockItem?.name ?: "Item",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "$qtyStr $unitSymbol  ×  ₹${formatAmount(entry.rate)}",
                                        color = labelColor,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    "₹${formatAmount(entry.amount)}",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (index < vwe.inventoryEntries.size - 1) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        // Totals.
                        // Amounts are stored TAX-INCLUSIVE: quantity * rate is the gross
                        // figure and taxableAmount is the pre-tax base back-computed from
                        // it. Showing the gross as "Subtotal" made the card read
                        // Subtotal 50,000 / Tax 2,381 / TOTAL 50,000 — lines that don't add up.
                        val invGross = vwe.inventoryEntries.sumOf { (it.quantity * it.rate).rupeesToPaise() }
                        val invTaxTotal = vwe.inventoryEntries.sumOf { it.taxAmount }
                        val invTaxableTotal = vwe.inventoryEntries.sumOf { it.taxableAmount }
                        // Fall back for rows saved before taxableAmount was populated.
                        val subtotal = if (invTaxableTotal > 0L) invTaxableTotal else invGross - invTaxTotal
                        val invGrandTotal = vwe.inventoryEntries.sumOf { it.totalAmount }.let { if (it > 0L) it else invGross }
                        val hasTax = invTaxTotal > 0L
                        val hasCgst = vwe.inventoryEntries.any { it.cgstRate > 0.0 }
                        val hasIgst = vwe.inventoryEntries.any { it.igstRate > 0.0 && it.cgstRate == 0.0 }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(TerminologyManager.getTerm("Subtotal"), color = labelColor, fontSize = 14.sp)
                            Text("₹${formatAmount(subtotal)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (taxEnabled && hasTax) {
                            Spacer(modifier = Modifier.height(6.dp))
                            if (hasCgst) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(TerminologyManager.getTerm("CGST + SGST"), color = labelColor, fontSize = 13.sp)
                                    Text("₹${formatAmount(invTaxTotal)}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                                }
                            } else if (hasIgst) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(TerminologyManager.getTerm("IGST"), color = labelColor, fontSize = 13.sp)
                                    Text("₹${formatAmount(invTaxTotal)}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.35f), thickness = 1.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(TerminologyManager.getTerm("TOTAL"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Text("₹${formatAmount(invGrandTotal)}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                // ── 3. TRANSACTION BREAKDOWN CARD — plain language, no Debit/Credit jargon ──
                Spacer(modifier = Modifier.height(16.dp))
                GlassCard {
                    Text(TerminologyManager.getTerm("Transaction Details"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Show main (non-tax) entries first, then tax entries at the bottom
                    val mainEntries = roleEntries.filter { !it.isTaxEntry }
                    val taxEntries = roleEntries.filter { it.isTaxEntry }

                    mainEntries.forEachIndexed { index, role ->
                        val isIncoming = role.label in listOf("Sales Revenue", "Received From", "Deposited To")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (isIncoming) Color(0xFF2E7D32).copy(alpha = 0.25f) else Color(0xFFC62828).copy(alpha = 0.25f),
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    role.icon,
                                    contentDescription = null,
                                    tint = if (isIncoming) Color(0xFF66BB6A) else Color(0xFFEF5350),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(role.ledgerName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(role.label, color = labelColor, fontSize = 12.sp)
                            }
                            Text(
                                "₹${formatAmount(role.amount)}",
                                color = if (isIncoming) Color(0xFF66BB6A) else Color(0xFFEF5350),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Tax entries (if any) — shown in a subtler style
                    if (taxEntries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(TerminologyManager.getTerm("Tax Components"), color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        taxEntries.forEach { role ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(modifier = Modifier.width(52.dp)) // Align with main entries
                                Text(role.ledgerName, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(
                                    "₹${formatAmount(role.amount)}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Grand Total highlight at the bottom of transaction details
                    if (!vwe.inventoryEntries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.35f), thickness = 1.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(TerminologyManager.getTerm("TOTAL"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Text("₹${formatAmount(grandTotal)}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            } ?: Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E2D4A).copy(alpha = 0.4f),
                            Color(0xFF0F182E).copy(alpha = 0.5f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 1000f)
                    )
                )
                .padding(20.dp),
            content = content
        )
    }
}

private fun formatAmount(amount: Double): String {
    val symbols = DecimalFormatSymbols(Locale.US)
    val formatter = DecimalFormat("#,##0.00", symbols)
    return formatter.format(amount)
}

private fun formatAmount(amount: Long): String = formatAmount(amount.paiseToRupees())
