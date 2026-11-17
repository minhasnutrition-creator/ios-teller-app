package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.PrinterPrefs
import com.minhas.teller.utils.ThermalPrinterHelper
import com.minhas.teller.utils.WifiPrinterDiscovery
import com.minhas.teller.utils.DiscoveredWifiPrinter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Date

private val GradientColors = listOf(
    Color(0xFF3B1C78),
    Color(0xFF2B1660),
    Color(0xFF1F1B5E),
    Color(0xFF131B4D),
    Color(0xFF0B1238)
)
private val GlassFill = Color.White.copy(alpha = 0.08f)
private val GlassFillDeep = Color.White.copy(alpha = 0.04f)
private val GlassStroke = Color.White.copy(alpha = 0.16f)
private val Cyan = Color(0xFF45E0E6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalPrinterScreen(navController: NavHostController, voucherId: Int? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()

    var company by remember { mutableStateOf<Company?>(null) }
    var loadedVoucherWithEntries by remember { mutableStateOf<VoucherWithAllEntries?>(null) }
    var customerLedger by remember { mutableStateOf<Ledger?>(null) }
    var stockItems by remember { mutableStateOf<List<StockItem>>(emptyList()) }

    val initialMode = remember { PrinterPrefs.getDefaultMode(context) }
    var connectionType by remember {
        mutableStateOf(
            when (initialMode) {
                PrinterPrefs.MODE_A4_SYSTEM -> ThermalPrinterHelper.ConnectionType.SYSTEM_PRINT
                PrinterPrefs.MODE_THERMAL_USB -> ThermalPrinterHelper.ConnectionType.USB
                PrinterPrefs.MODE_THERMAL_WIFI -> ThermalPrinterHelper.ConnectionType.NETWORK
                else -> ThermalPrinterHelper.ConnectionType.BLUETOOTH
            }
        )
    }
    var pairedPrinters by remember { mutableStateOf<List<ThermalPrinterHelper.BluetoothPrinterDevice>>(emptyList()) }
    var usbPrinters by remember { mutableStateOf<List<ThermalPrinterHelper.UsbPrinterDevice>>(emptyList()) }
    var selectedDeviceAddress by remember { mutableStateOf(PrinterPrefs.getBluetoothDevice(context).first) }
    var selectedUsbDevice by remember { mutableStateOf<ThermalPrinterHelper.UsbPrinterDevice?>(null) }
    val savedWifi = remember { PrinterPrefs.getWifiPrinter(context) }
    var networkIp by remember { mutableStateOf(savedWifi.first) }
    var networkPort by remember { mutableStateOf(savedWifi.second.toString()) }
    var paperWidth by remember { mutableStateOf(PrinterPrefs.getPaperWidth(context)) }
    var isPrinting by remember { mutableStateOf(false) }

    var defaultSummary by remember { mutableStateOf(PrinterPrefs.getFriendlyPrinterSummary(context)) }
    var isOneClick by remember { mutableStateOf(PrinterPrefs.isOneClickEnabled(context)) }

    val wifiDiscovery = remember { WifiPrinterDiscovery(context) }
    val discoveredWifiPrinters by wifiDiscovery.printers.collectAsState()
    val isScanningWifi by wifiDiscovery.isScanning.collectAsState()

    // Paired printers + Bluetooth-LE-only printers switched on nearby (those never appear in Android's
    // pairing list). Needs the "Nearby devices" permission on Android 12+, asked for here.
    fun refreshPrinters() {
        scope.launch {
            val paired = ThermalPrinterHelper.getPairedPrinters(context)
            pairedPrinters = paired
            if (selectedDeviceAddress == null && paired.isNotEmpty()) selectedDeviceAddress = paired.first().address
            usbPrinters = ThermalPrinterHelper.getUsbPrinters(context)
            if (selectedUsbDevice == null && usbPrinters.isNotEmpty()) selectedUsbDevice = usbPrinters.first()
            val ble = ThermalPrinterHelper.scanBlePrinters(context)
            val known = paired.map { it.address }.toSet()
            val merged = paired + ble.filter { it.address !in known }
            pairedPrinters = merged
            if (selectedDeviceAddress == null && merged.isNotEmpty()) selectedDeviceAddress = merged.first().address
        }
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) refreshPrinters()
        else Toast.makeText(context, "Allow \"Nearby devices\" so Teller can find your Bluetooth printer", Toast.LENGTH_LONG).show()
    }
    // Coming back from Android's Bluetooth settings (after pairing) or plugging in a USB printer: refresh.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && ThermalPrinterHelper.hasBluetoothPermission(context)) refreshPrinters()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(connectionType) {
        if (connectionType == ThermalPrinterHelper.ConnectionType.NETWORK) {
            wifiDiscovery.startDiscovery()
        } else {
            wifiDiscovery.stopDiscovery()
        }
    }

    DisposableEffect(Unit) {
        onDispose { wifiDiscovery.stopDiscovery() }
    }

    LaunchedEffect(currentCompanyId, voucherId) {
        currentCompanyId?.let { id ->
            company = db.companyDao().getCompanyById(id).firstOrNull()
        }

        if (voucherId != null) {
            val vwe = db.voucherDao().getVoucherWithAllEntriesById(voucherId)
            loadedVoucherWithEntries = vwe
            if (vwe != null) {
                val isSales = vwe.voucher.type == "Sales"
                val partyEntry = vwe.voucherEntries.firstOrNull { if (isSales) it.isDebit else !it.isDebit }
                if (partyEntry != null) {
                    customerLedger = db.ledgerDao().getLedgerByIdSync(partyEntry.ledgerId)
                }
                currentCompanyId?.let { cid ->
                    stockItems = db.stockItemDao().getAllStockItems(cid)
                }
            }
        }

        if (ThermalPrinterHelper.hasBluetoothPermission(context)) {
            refreshPrinters()
        } else {
            usbPrinters = ThermalPrinterHelper.getUsbPrinters(context)
            if (usbPrinters.isNotEmpty()) selectedUsbDevice = usbPrinters.first()
            bluetoothPermissionLauncher.launch(ThermalPrinterHelper.bluetoothPermissions())
        }
    }

    // Fallback sample voucher if opened directly without a voucher
    val activeVoucher = loadedVoucherWithEntries ?: remember(company) {
        VoucherWithAllEntries(
            voucher = Voucher(
                id = 0,
                voucherNumber = "SAMPLE-001",
                date = Date(),
                type = "Sales",
                companyId = company?.id ?: 1
            ),
            voucherEntries = listOf(
                VoucherEntry(id = 1, voucherId = 0, ledgerId = 1, amount = 25000L, isDebit = true),
                VoucherEntry(id = 2, voucherId = 0, ledgerId = 2, amount = 25000L, isDebit = false)
            ),
            inventoryEntries = listOf(
                InventoryEntry(id = 1, voucherId = 0, stockItemId = 1, quantity = 2.0, rate = 100.0, amount = 20000L, taxAmount = 3600L, taxableAmount = 20000L, totalAmount = 23600L),
                InventoryEntry(id = 2, voucherId = 0, stockItemId = 2, quantity = 1.0, rate = 14.0, amount = 1400L, taxAmount = 0L, taxableAmount = 1400L, totalAmount = 1400L)
            )
        )
    }

    val activeStockItems = if (stockItems.isNotEmpty()) stockItems else remember {
        listOf(
            StockItem(id = 1, name = "Sample Item A", unitId = 1, openingStock = 10.0, rate = 100.0, companyId = 1),
            StockItem(id = 2, name = "Sample Item B", unitId = 1, openingStock = 20.0, rate = 14.0, companyId = 1)
        )
    }

    val customerName = customerLedger?.name ?: if (voucherId != null) "Counter Sale" else "Sample Customer"

    val receiptPreviewText = remember(company, activeVoucher, activeStockItems, customerName, paperWidth) {
        ThermalPrinterHelper.buildReceiptText(
            company = company,
            voucherWithAllEntries = activeVoucher,
            stockItems = activeStockItems,
            customerName = customerName,
            paperWidth = paperWidth
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(GradientColors))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassFill)
                        .border(1.dp, GlassStroke, CircleShape)
                        .clickable { navController.popBackStack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                AutoShrinkText(
                    text = if (voucherId != null) "Print Invoice #${activeVoucher.voucher.voucherNumber}" else "Universal Printer Hub",
                    color = Color.White,
                    maxFontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassFill)
                        .border(1.dp, GlassStroke, CircleShape)
                        .clickable {
                            pairedPrinters = ThermalPrinterHelper.getPairedPrinters(context)
                            usbPrinters = ThermalPrinterHelper.getUsbPrinters(context)
                            Toast.makeText(context, "Printer list refreshed", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Cyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 1-Click Default Printer Status Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, if (isOneClick) Cyan else GlassStroke, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isOneClick) "⚡ 1-Click Invoicing Active:" else "Default Printer Setting:",
                            color = if (isOneClick) Cyan else Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = defaultSummary,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    if (isOneClick) {
                        TextButton(
                            onClick = {
                                PrinterPrefs.resetDefaultPrinter(context)
                                defaultSummary = PrinterPrefs.getFriendlyPrinterSummary(context)
                                isOneClick = false
                                Toast.makeText(context, "1-Click default printer cleared", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(TerminologyManager.getTerm("Clear"), color = Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Connection Type Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassFill)
                    .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val tabs = listOf(
                    Triple(ThermalPrinterHelper.ConnectionType.BLUETOOTH, "Bluetooth", Icons.Default.Bluetooth),
                    Triple(ThermalPrinterHelper.ConnectionType.USB, "USB OTG", Icons.Default.Usb),
                    Triple(ThermalPrinterHelper.ConnectionType.NETWORK, "WiFi / IP", Icons.Default.Wifi),
                    Triple(ThermalPrinterHelper.ConnectionType.SYSTEM_PRINT, "System", Icons.Default.Print)
                )

                tabs.forEach { (type, title, icon) ->
                    val isSelected = connectionType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Cyan.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { connectionType = type }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                icon,
                                contentDescription = title,
                                tint = if (isSelected) Cyan else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = title,
                                color = if (isSelected) Cyan else Color.White.copy(alpha = 0.6f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Paper Width Selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassFill)
                    .border(1.dp, GlassStroke, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (paperWidth == ThermalPrinterHelper.PaperWidth.MM_58) Cyan.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable {
                                paperWidth = ThermalPrinterHelper.PaperWidth.MM_58
                                PrinterPrefs.setPaperWidth(context, paperWidth)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "2-Inch / 58mm (Receipt)",
                            color = if (paperWidth == ThermalPrinterHelper.PaperWidth.MM_58) Cyan else Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (paperWidth == ThermalPrinterHelper.PaperWidth.MM_80) Cyan.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable {
                                paperWidth = ThermalPrinterHelper.PaperWidth.MM_80
                                PrinterPrefs.setPaperWidth(context, paperWidth)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "3-Inch / 80mm (POS Roll)",
                            color = if (paperWidth == ThermalPrinterHelper.PaperWidth.MM_80) Cyan else Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Transport Configuration Box
            when (connectionType) {
                ThermalPrinterHelper.ConnectionType.BLUETOOTH -> {
                    if (pairedPrinters.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(85.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(GlassFillDeep)
                                .border(1.dp, GlassStroke.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when {
                                    !ThermalPrinterHelper.hasBluetoothPermission(context) -> "Tap here and allow \"Nearby devices\" so Teller can find your printer."
                                    !ThermalPrinterHelper.isBluetoothOn() -> "Bluetooth is off. Tap here, turn Bluetooth on and pair your printer."
                                    else -> "No printer found. Switch the printer ON, then tap here to pair it (PIN is usually 0000 or 1234). Bluetooth LE printers appear here by themselves."
                                },
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.clickable {
                                    if (!ThermalPrinterHelper.hasBluetoothPermission(context)) {
                                        bluetoothPermissionLauncher.launch(ThermalPrinterHelper.bluetoothPermissions())
                                    } else {
                                        ThermalPrinterHelper.openBluetoothSettings(context)
                                    }
                                }
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(pairedPrinters) { device ->
                                    val isSelected = selectedDeviceAddress == device.address
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) Cyan.copy(alpha = 0.15f) else GlassFillDeep)
                                            .border(1.dp, if (isSelected) Cyan else GlassStroke.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                            .clickable { selectedDeviceAddress = device.address }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                                                contentDescription = null,
                                                tint = if (isSelected) Cyan else Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = device.name,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = device.address,
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    fontSize = 10.sp
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = Cyan,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            val selected = pairedPrinters.find { it.address == selectedDeviceAddress }
                            if (selected != null) {
                                Button(
                                    onClick = {
                                        PrinterPrefs.setBluetoothDevice(context, selected.address, selected.name)
                                        PrinterPrefs.setDefaultMode(context, PrinterPrefs.MODE_THERMAL_BT)
                                        PrinterPrefs.setOneClickEnabled(context, true)
                                        defaultSummary = PrinterPrefs.getFriendlyPrinterSummary(context)
                                        isOneClick = true
                                        Toast.makeText(context, "Saved ${selected.name} as 1-Click Default Printer!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Cyan.copy(alpha = 0.25f)),
                                    border = BorderStroke(1.dp, Cyan),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Cyan, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Set ${selected.name} as 1-Click Default", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                ThermalPrinterHelper.ConnectionType.USB -> {
                    if (usbPrinters.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(85.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(GlassFillDeep)
                                .border(1.dp, GlassStroke.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No USB printer detected. Plug in your USB POS printer using an OTG adapter.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(usbPrinters) { device ->
                                    val isSelected = selectedUsbDevice?.device == device.device
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) Cyan.copy(alpha = 0.15f) else GlassFillDeep)
                                            .border(1.dp, if (isSelected) Cyan else GlassStroke.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                            .clickable { selectedUsbDevice = device }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Usb,
                                                contentDescription = null,
                                                tint = if (isSelected) Cyan else Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = device.name,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = "Vendor ID: ${device.device.vendorId} | Product ID: ${device.device.productId}",
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    fontSize = 10.sp
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = Cyan,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            selectedUsbDevice?.let { usb ->
                                Button(
                                    onClick = {
                                        PrinterPrefs.setUsbDeviceName(context, usb.name)
                                        PrinterPrefs.setDefaultMode(context, PrinterPrefs.MODE_THERMAL_USB)
                                        PrinterPrefs.setOneClickEnabled(context, true)
                                        defaultSummary = PrinterPrefs.getFriendlyPrinterSummary(context)
                                        isOneClick = true
                                        Toast.makeText(context, "Saved USB Printer as 1-Click Default!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Cyan.copy(alpha = 0.25f)),
                                    border = BorderStroke(1.dp, Cyan),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Cyan, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(TerminologyManager.getTerm("Set USB Printer as 1-Click Default"), color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                ThermalPrinterHelper.ConnectionType.NETWORK -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Wi-Fi Auto-Discovery List
                        if (discoveredWifiPrinters.isNotEmpty()) {
                            Text(TerminologyManager.getTerm("Wi-Fi Printers Found (mDNS Auto-Discovery):"), color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 110.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(discoveredWifiPrinters) { wp ->
                                    val isSelected = networkIp == wp.host
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) Cyan.copy(alpha = 0.18f) else GlassFillDeep)
                                            .border(1.dp, if (isSelected) Cyan else GlassStroke.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                            .clickable {
                                                networkIp = wp.host
                                                networkPort = wp.port.toString()
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(Icons.Default.Wifi, contentDescription = null, tint = Cyan, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(wp.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    Text("${wp.host}:${wp.port} • Direct Port 9100 RAW", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
                                                }
                                            }
                                            if (isSelected) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Cyan, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (isScanningWifi) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Cyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(TerminologyManager.getTerm("Auto-discovering Wi-Fi printers on local network..."), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                        }

                        // Manual IP/Port Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(GlassFillDeep)
                                .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = networkIp,
                                onValueChange = { networkIp = it },
                                label = { Text(TerminologyManager.getTerm("Printer IP Address"), fontSize = 10.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Cyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                    focusedLabelColor = Cyan,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.weight(2f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = networkPort,
                                onValueChange = { networkPort = it },
                                label = { Text(TerminologyManager.getTerm("Port"), fontSize = 10.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Cyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                    focusedLabelColor = Cyan,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Set as Default Button
                        Button(
                            onClick = {
                                val ip = networkIp.trim()
                                val port = networkPort.trim().toIntOrNull() ?: 9100
                                if (ip.isBlank()) {
                                    Toast.makeText(context, "Enter printer IP address", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val found = discoveredWifiPrinters.find { it.host == ip }
                                val label = found?.name ?: "Wi-Fi Printer ($ip)"
                                PrinterPrefs.setWifiPrinter(context, ip, port, label)
                                PrinterPrefs.setDefaultMode(context, PrinterPrefs.MODE_THERMAL_WIFI)
                                PrinterPrefs.setOneClickEnabled(context, true)
                                defaultSummary = PrinterPrefs.getFriendlyPrinterSummary(context)
                                isOneClick = true
                                Toast.makeText(context, "Saved $label as 1-Click Default Printer!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, Cyan),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Cyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(TerminologyManager.getTerm("Set Wi-Fi Printer as 1-Click Default"), color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                ThermalPrinterHelper.ConnectionType.SYSTEM_PRINT -> {
                    val isPrintServiceOn = ThermalPrinterHelper.isSystemPrintServiceEnabled(context)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassFillDeep)
                            .border(1.dp, if (!isPrintServiceOn) Color(0xFFFFB74D).copy(alpha = 0.4f) else GlassStroke, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Print, contentDescription = null, tint = Cyan, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(TerminologyManager.getTerm("Standard A4 Android Print Spooler"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(
                                        text = "Works with HP, Canon, Epson, Brother, or any Wi-Fi/Office printer.",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            if (!isPrintServiceOn) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFB74D).copy(alpha = 0.15f))
                                        .padding(8.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(TerminologyManager.getTerm("⚠️ Phone Print Service is OFF. Android will only download a PDF file."),
                                            color = Color(0xFFFFB74D),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Button(
                                            onClick = { ThermalPrinterHelper.openSystemPrintSettings(context) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(TerminologyManager.getTerm("⚙️ Enable Print Service in Settings (1-Tap)"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        }
                                    }
                                }
                            } else {
                                Text(TerminologyManager.getTerm("💡 In the print preview, tap 'Save as PDF ▾' at top to select your physical printer."),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 10.sp
                                )
                                Button(
                                    onClick = {
                                        PrinterPrefs.setDefaultMode(context, PrinterPrefs.MODE_A4_SYSTEM)
                                        PrinterPrefs.setOneClickEnabled(context, true)
                                        defaultSummary = PrinterPrefs.getFriendlyPrinterSummary(context)
                                        isOneClick = true
                                        Toast.makeText(context, "Saved Standard A4 as 1-Click Default!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Cyan.copy(alpha = 0.25f)),
                                    border = BorderStroke(1.dp, Cyan),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Cyan, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(TerminologyManager.getTerm("Set Standard A4 as 1-Click Default"), color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live Receipt Preview Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Cyan.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE RECEIPT PREVIEW",
                            color = Cyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${paperWidth.charsPerLine} Chars / Line",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = receiptPreviewText,
                            color = Color(0xFFE0E0E0),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Secondary Button: Share Receipt PDF
                OutlinedButton(
                    onClick = {
                        try {
                            val pdf = ThermalPrinterHelper.generateThermalReceiptPdf(
                                context = context,
                                company = company,
                                voucherWithAllEntries = activeVoucher,
                                stockItems = activeStockItems,
                                customerName = customerName,
                                paperWidth = paperWidth
                            )
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", pdf)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Thermal Receipt"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to share receipt: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassStroke)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Share Receipt", fontSize = 12.sp)
                }

                // If the direct connection fails, hand the same receipt to the phone's own print screen,
                // which works with any printer that has an Android print service (HP, Canon, Epson,
                // Brother, Mopria...). Nothing is lost: the user just picks the printer there.
                fun printWithPhoneFallback(reason: String) {
                    Toast.makeText(context, "$reason Opening the phone's print screen instead…", Toast.LENGTH_LONG).show()
                    try {
                        val pdf = ThermalPrinterHelper.generateThermalReceiptPdf(
                            context = context,
                            company = company,
                            voucherWithAllEntries = activeVoucher,
                            stockItems = activeStockItems,
                            customerName = customerName,
                            paperWidth = paperWidth
                        )
                        ThermalPrinterHelper.printDocumentWithSystem(context, pdf, "Receipt #${activeVoucher.voucher.voucherNumber}")
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open the print screen: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                // Primary Button: Print
                Button(
                    onClick = {
                        scope.launch {
                            isPrinting = true
                            try {
                                when (connectionType) {
                                    ThermalPrinterHelper.ConnectionType.BLUETOOTH -> {
                                        val targetAddress = selectedDeviceAddress
                                        if (targetAddress == null) {
                                            Toast.makeText(context, "Select a paired Bluetooth printer", Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        val bytes = ThermalPrinterHelper.buildReceiptBytes(
                                            company = company,
                                            voucherWithAllEntries = activeVoucher,
                                            stockItems = activeStockItems,
                                            customerName = customerName,
                                            paperWidth = paperWidth
                                        )
                                        if (!ThermalPrinterHelper.hasBluetoothPermission(context)) {
                                            bluetoothPermissionLauncher.launch(ThermalPrinterHelper.bluetoothPermissions())
                                            return@launch
                                        }
                                        val ok = ThermalPrinterHelper.printToBluetoothDevice(targetAddress, bytes, context)
                                        if (ok) Toast.makeText(context, "Printed via Bluetooth successfully!", Toast.LENGTH_SHORT).show()
                                        else printWithPhoneFallback("Could not reach the Bluetooth printer. Check it is ON and close by.")
                                    }

                                    ThermalPrinterHelper.ConnectionType.USB -> {
                                        val usb = selectedUsbDevice
                                        if (usb == null) {
                                            Toast.makeText(context, "No USB printer selected", Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        val bytes = ThermalPrinterHelper.buildReceiptBytes(
                                            company = company,
                                            voucherWithAllEntries = activeVoucher,
                                            stockItems = activeStockItems,
                                            customerName = customerName,
                                            paperWidth = paperWidth
                                        )
                                        val ok = ThermalPrinterHelper.printToUsbDevice(context, usb.device, bytes)
                                        if (ok) Toast.makeText(context, "Printed via USB successfully!", Toast.LENGTH_SHORT).show()
                                        else printWithPhoneFallback("USB printer did not respond (allow USB access when asked).")
                                    }

                                    ThermalPrinterHelper.ConnectionType.NETWORK -> {
                                        val ip = networkIp.trim()
                                        val port = networkPort.trim().toIntOrNull() ?: 9100
                                        if (ip.isBlank()) {
                                            Toast.makeText(context, "Enter printer IP address", Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        val bytes = ThermalPrinterHelper.buildReceiptBytes(
                                            company = company,
                                            voucherWithAllEntries = activeVoucher,
                                            stockItems = activeStockItems,
                                            customerName = customerName,
                                            paperWidth = paperWidth
                                        )
                                        val ok = ThermalPrinterHelper.printToNetworkPrinter(ip, port, bytes)
                                        if (ok) Toast.makeText(context, "Printed via Network successfully!", Toast.LENGTH_SHORT).show()
                                        else printWithPhoneFallback("Could not connect to the printer at $ip:$port.")
                                    }

                                    ThermalPrinterHelper.ConnectionType.SYSTEM_PRINT -> {
                                        val pdf = ThermalPrinterHelper.generateThermalReceiptPdf(
                                            context = context,
                                            company = company,
                                            voucherWithAllEntries = activeVoucher,
                                            stockItems = activeStockItems,
                                            customerName = customerName,
                                            paperWidth = paperWidth
                                        )
                                        ThermalPrinterHelper.printDocumentWithSystem(
                                            context = context,
                                            pdfFile = pdf,
                                            jobName = "Receipt #${activeVoucher.voucher.voucherNumber}"
                                        )
                                    }
                                }
                            } finally {
                                isPrinting = false
                            }
                        }
                    },
                    enabled = !isPrinting,
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPrinting) "Printing..." else "Print Now",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
