package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.EasyPrint
import com.minhas.teller.utils.PrinterPrefs
import com.minhas.teller.utils.ThermalPrinterHelper
import com.minhas.teller.utils.WifiPrinterDiscovery
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
private val GlassStroke = Color.White.copy(alpha = 0.16f)
private val Cyan = Color(0xFF45E0E6)
private val Dim = Color.White.copy(alpha = 0.6f)

/**
 * Printer setup, rebuilt around one question at a time.
 *
 * The old screen opened on connection-type tabs (Bluetooth / USB / Wi-Fi / Counter PC), an IP
 * address field, a paper-width switch and a monospace receipt preview — asking a shopkeeper to
 * understand all of it before a single page came out, and showing a 58mm till-roll mock-up even
 * when they were trying to print an A4 invoice.
 *
 * This version is a three-step path: **find → test → confirm it printed**. Everything else is
 * folded into "Advanced settings" at the bottom, where it is still reachable but never in the way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalPrinterScreen(navController: NavHostController, voucherId: Int? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()

    var company by remember { mutableStateOf<Company?>(null) }
    var savedTarget by remember { mutableStateOf(EasyPrint.savedTarget(context)) }
    var found by remember { mutableStateOf<List<EasyPrint.Target>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf<EasyPrint.Target?>(null) }
    var awaitingAnswer by remember { mutableStateOf<EasyPrint.Target?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    var paperWidth by remember { mutableStateOf(PrinterPrefs.getPaperWidth(context)) }

    val wifiDiscovery = remember { WifiPrinterDiscovery(context) }
    val discoveredWifi by wifiDiscovery.printers.collectAsState()
    DisposableEffect(Unit) {
        wifiDiscovery.startDiscovery()
        onDispose { wifiDiscovery.stopDiscovery() }
    }

    fun search() {
        scope.launch {
            isSearching = true
            lastError = null
            found = EasyPrint.discover(context, discoveredWifi)
            isSearching = false
            hasSearched = true
        }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) search()
        else Toast.makeText(
            context,
            "Allow \"Nearby devices\" so Teller can find your Bluetooth printer",
            Toast.LENGTH_LONG
        ).show()
    }

    fun startSearch() {
        if (ThermalPrinterHelper.hasBluetoothPermission(context)) search()
        else btPermissionLauncher.launch(ThermalPrinterHelper.bluetoothPermissions())
    }

    fun test(target: EasyPrint.Target) {
        // An A4 printer has no test slip of ours to print — Android picks the printer at print
        // time — so save it and say so rather than pretending to test it.
        if (target.kind == EasyPrint.Kind.SYSTEM) {
            EasyPrint.remember(context, target)
            savedTarget = EasyPrint.savedTarget(context)
            Toast.makeText(context, "Saved. Android will ask which printer when you press Print.", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            testing = target
            lastError = null
            val outcome = EasyPrint.printTest(context, target)
            testing = null
            when (outcome) {
                is EasyPrint.Outcome.Failed -> lastError = outcome.message
                else -> awaitingAnswer = target
            }
        }
    }

    LaunchedEffect(currentCompanyId) {
        currentCompanyId?.let { company = db.companyDao().getCompanyById(it).firstOrNull() }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── Title ────────────────────────────────────────────────────────
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                AutoShrinkText(
                    text = "Printer setup",
                    color = Color.White,
                    maxFontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Step 1: where we stand ───────────────────────────────────────
            val saved = savedTarget
            PrinterCard {
                if (saved != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Cyan, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Ready to print", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(saved.label, color = Cyan, fontSize = 14.sp)
                            Text(saved.detail, color = Dim, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Bills print here with one tap. Nothing else to set up.",
                        color = Dim,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { test(saved) },
                            enabled = testing == null,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black)
                        ) {
                            if (testing != null) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                            } else {
                                Text("Print test page", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                EasyPrint.forget(context)
                                savedTarget = null
                                startSearch()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) { Text("Change printer") }
                    }
                } else {
                    Text("No printer yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Switch your printer on, then tap the button. Teller will look for it and " +
                            "print one test page so you can be sure before you use it on a real bill.",
                        color = Dim,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { startSearch() },
                        enabled = !isSearching,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black)
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                            Spacer(Modifier.width(10.dp))
                            Text("Looking for printers…", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Find my printer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // ── Step 3 (shown first when pending): did it actually print? ────
            val answering = awaitingAnswer
            if (answering != null) {
                Spacer(Modifier.height(12.dp))
                PrinterCard(stroke = Cyan) {
                    Text("Did a page come out of ${answering.label}?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "If yes, Teller will use this printer from now on.",
                        color = Dim,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                EasyPrint.remember(context, answering)
                                savedTarget = EasyPrint.savedTarget(context)
                                awaitingAnswer = null
                                found = emptyList()
                                hasSearched = false
                                Toast.makeText(context, "✓ ${answering.label} is now your printer", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black)
                        ) { Text("Yes, use this", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = {
                                awaitingAnswer = null
                                lastError = "Nothing printed on ${answering.label}. Try another printer below."
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) { Text("No") }
                    }
                }
            }

            lastError?.let { message ->
                Spacer(Modifier.height(12.dp))
                PrinterCard(stroke = Color(0xFFFF8A80)) {
                    Text(message, color = Color(0xFFFFCDD2), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text("Three things to check:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    listOf(
                        "Is the printer switched on, with the light steady?",
                        "Is there paper in it, and the lid clicked shut?",
                        "For Bluetooth: is it paired in Android Settings → Bluetooth?"
                    ).forEach {
                        Text("•  $it", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) { Text("Open Bluetooth settings") }
                }
            }

            // ── Step 2: pick one ─────────────────────────────────────────────
            if (found.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "Tap your printer",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    "We'll print one test page on it.",
                    color = Dim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                found.forEach { target ->
                    PrinterRow(
                        target = target,
                        busy = testing == target,
                        enabled = testing == null,
                        onClick = { test(target) }
                    )
                }
            } else if (hasSearched && !isSearching) {
                Spacer(Modifier.height(12.dp))
                PrinterCard {
                    Text("No printer found", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "Switch the printer on and wait for its light.",
                        "Open Android Settings → Bluetooth and pair it once (the PIN is usually 0000 or 1234).",
                        "Come back here and tap Find my printer again."
                    ).forEachIndexed { i, step ->
                        Text("${i + 1}.  $step", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black)
                        ) { Text("Open Bluetooth settings") }
                        OutlinedButton(
                            onClick = { startSearch() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) { Text("Search again") }
                    }
                }
            }

            // ── Advanced, folded away ────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Advanced settings", color = Dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(
                    if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Dim
                )
            }
            AnimatedVisibility(visible = showAdvanced) {
                Column {
                    PrinterCard {
                        Text("Paper roll width", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Only used by till-roll (thermal) printers. 2-inch is the common small roll.",
                            color = Dim, fontSize = 12.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(
                                ThermalPrinterHelper.PaperWidth.MM_58 to "58 mm (2 inch)",
                                ThermalPrinterHelper.PaperWidth.MM_80 to "80 mm (3 inch)"
                            ).forEach { (width, label) ->
                                FilterChip(
                                    selected = paperWidth == width,
                                    onClick = {
                                        paperWidth = width
                                        PrinterPrefs.setPaperWidth(context, width)
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = GlassFill,
                                        selectedContainerColor = Cyan.copy(alpha = 0.25f),
                                        labelColor = Dim,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    WifiManualCard(
                        onSaved = { ip, port, name ->
                            PrinterPrefs.setWifiPrinter(context, ip, port, name)
                            test(EasyPrint.Target(EasyPrint.Kind.WIFI, name, "Wi-Fi · $ip", ip, port))
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    SampleBillCard(context, company, paperWidth)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun PrinterCard(stroke: Color = GlassStroke, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassFill)
            .border(1.dp, stroke, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun PrinterRow(
    target: EasyPrint.Target,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(GlassFill)
            .border(1.dp, GlassStroke, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (target.kind) {
                EasyPrint.Kind.BLUETOOTH -> Icons.Default.Bluetooth
                EasyPrint.Kind.WIFI -> Icons.Default.Wifi
                EasyPrint.Kind.USB -> Icons.Default.Usb
                EasyPrint.Kind.COUNTER_PC -> Icons.Default.Computer
                EasyPrint.Kind.SYSTEM -> Icons.Default.Print
            },
            contentDescription = null,
            tint = Cyan,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(target.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(target.detail, color = Dim, fontSize = 12.sp)
        }
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Cyan)
    }
}

/** Manual Wi-Fi entry, for the one shop in a hundred whose printer never shows up in discovery. */
@Composable
private fun WifiManualCard(onSaved: (String, Int, String) -> Unit) {
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("9100") }
    PrinterCard {
        Text("Wi-Fi printer by address", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            "Only if your printer didn't show up above. The address is printed on the printer's own " +
                "self-test page, e.g. 192.168.1.100.",
            color = Dim, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP address", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(2f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = GlassStroke,
                    focusedLabelColor = Cyan,
                    unfocusedLabelColor = Dim
                )
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("Port", fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = GlassStroke,
                    focusedLabelColor = Cyan,
                    unfocusedLabelColor = Dim
                )
            )
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { if (ip.isNotBlank()) onSaved(ip.trim(), port.toIntOrNull() ?: 9100, "Network printer") },
            enabled = ip.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color.Black)
        ) { Text("Test this address") }
    }
}

/**
 * The till-roll preview, kept but demoted and finally labelled for what it is. It used to sit in
 * the middle of the screen with no explanation, so someone printing an A4 invoice saw a narrow
 * receipt and assumed that was their bill.
 */
@Composable
private fun SampleBillCard(
    context: android.content.Context,
    company: Company?,
    paperWidth: ThermalPrinterHelper.PaperWidth
) {
    var expanded by remember { mutableStateOf(false) }
    val sample = remember(company, paperWidth) {
        ThermalPrinterHelper.buildReceiptText(
            company = company,
            voucherWithAllEntries = VoucherWithAllEntries(
                voucher = Voucher(id = 0, voucherNumber = "SAMPLE-001", date = Date(), type = "Sales", companyId = company?.id ?: 1),
                voucherEntries = listOf(
                    VoucherEntry(id = 1, voucherId = 0, ledgerId = 1, amount = 25000L, isDebit = true),
                    VoucherEntry(id = 2, voucherId = 0, ledgerId = 2, amount = 25000L, isDebit = false)
                ),
                inventoryEntries = listOf(
                    InventoryEntry(id = 1, voucherId = 0, stockItemId = 1, quantity = 2.0, rate = 100.0, amount = 20000L, taxAmount = 3600L, taxableAmount = 20000L, totalAmount = 23600L)
                )
            ),
            stockItems = listOf(StockItem(id = 1, name = "Sample Item", unitId = 1, openingStock = 10.0, rate = 100.0, companyId = 1)),
            customerName = "Sample Customer",
            paperWidth = paperWidth
        )
    }
    PrinterCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Sample till-roll slip", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "What a bill looks like on a ${if (paperWidth == ThermalPrinterHelper.PaperWidth.MM_80) "80" else "58"}mm " +
                        "roll. Your A4 invoice is a different, full-page design.",
                    color = Dim, fontSize = 12.sp
                )
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Dim)
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = sample,
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(10.dp)
            )
        }
    }
}
