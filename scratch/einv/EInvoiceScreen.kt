package com.minhas.teller.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.entities.Unit as TellerUnit
import com.minhas.teller.utils.EInvoiceStore
import com.minhas.teller.utils.GstEwayBillExporter
import com.minhas.teller.utils.SystemTtsEngine
import com.minhas.teller.utils.TtsModelPrefs
import kotlinx.coroutines.launch

/**
 * One screen, one obvious button at a time - built so someone with zero GST knowledge (the brief
 * this was built against: "a 10-year-old filing for their dad") can send a bill to the government
 * without anyone standing over their shoulder.
 *
 * Replaces the old cramped e-Invoice AlertDialog in VoucherViewScreen. The full manual path (share
 * the JSON yourself, paste a reply, pick a reply file) still exists - it's just folded away behind
 * "Do it the old way" instead of competing for attention with the one button that matters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EInvoiceScreen(navController: NavHostController, voucherId: Int) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    var vwe by remember { mutableStateOf<VoucherWithAllEntries?>(null) }
    var company by remember { mutableStateOf<Company?>(null) }
    var allLedgers by remember { mutableStateOf<List<Ledger>>(emptyList()) }
    var stockMap by remember { mutableStateOf<Map<Int, StockItem>>(emptyMap()) }
    var unitsMap by remember { mutableStateOf<Map<Int, TellerUnit>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(voucherId, tick) {
        loading = true
        val v = db.voucherDao().getVoucherWithAllEntriesById(voucherId)
        vwe = v
        if (v != null) {
            val companyId = v.voucher.companyId
            company = db.companyDao().getCompanyByIdSync(companyId)
            allLedgers = db.ledgerDao().getAllLedgersSync(companyId)
            stockMap = db.stockItemDao().getAllStockItems(companyId).associateBy { it.id }
            unitsMap = db.unitDao().getAllUnits(companyId).associateBy { it.id }
        }
        loading = false
    }

    val customerLedger = remember(vwe, allLedgers) {
        vwe?.let { EInvoiceStore.buyerLedger(it, allLedgers) }
    }
    val record = remember(vwe, tick) { vwe?.let { EInvoiceStore.get(it.voucher.companyId, it.voucher.id, context) } }
    val needs = remember(vwe, allLedgers) { vwe?.let { EInvoiceStore.needsEInvoice(it, allLedgers) } ?: false }
    val missingGstin = remember(vwe, allLedgers) { vwe?.let { EInvoiceStore.isB2bMissingGstin(it, allLedgers) } ?: false }
    val savedParty = remember(customerLedger, tick) { customerLedger?.let { EInvoiceStore.partyDetails(it.id, context) } }

    var address by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }
    var editingAddress by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    var eInvoicePaste by remember { mutableStateOf("") }

    LaunchedEffect(savedParty) {
        if (savedParty != null) {
            address = savedParty.address; place = savedParty.place; pincode = savedParty.pincode
        }
    }

    fun speakGuidance() {
        val text = when {
            vwe == null -> "This bill could not be found."
            record != null -> "This bill has already been sent to the government. There is nothing more to do here."
            !needs && missingGstin -> "This bill is marked B2B but the customer has no GST number saved. Add it in the customer's account first."
            !needs -> "This bill does not need to be sent to the government."
            customerLedger == null -> "Add a customer to this bill first, then come back here."
            savedParty == null || editingAddress -> "Type the customer's address and six digit PIN code, then tap Save."
            else -> "Tap the big green button. It will open the government website and send the bill for you. If it asks for a code sent to your phone, type that code there."
        }
        SystemTtsEngine.init(context)
        SystemTtsEngine.speak(text, TtsModelPrefs.getVoiceLanguage(context))
    }

    val qrHandle = navController.currentBackStackEntry?.savedStateHandle
    val qrResult = qrHandle?.getStateFlow<String?>("qr_result", null)?.collectAsState()
    LaunchedEffect(qrResult?.value) {
        val raw = qrResult?.value ?: return@LaunchedEffect
        qrHandle.remove<String>("qr_result")
        vwe?.let { v ->
            val rec = EInvoiceStore.parseReply(raw, v.voucher.voucherNumber)
            errorMsg = if (rec != null) {
                EInvoiceStore.save(v.voucher.companyId, v.voucher.id, rec, context)
                EInvoiceStore.setEnabled(v.voucher.companyId, true, context)
                tick++
                null
            } else "Could not find a government receipt number in that QR code."
        }
    }
    val portalDone = navController.currentBackStackEntry?.savedStateHandle?.getStateFlow("portal_capture_done", false)?.collectAsState()
    LaunchedEffect(portalDone?.value) {
        if (portalDone?.value == true) {
            navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("portal_capture_done")
            tick++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Bill to Government", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { speakGuidance() }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Read this out loud")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                vwe == null -> Text("This bill could not be found.", fontSize = 16.sp)

                record != null -> {
                    BigStatusCard(
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF2E7D32),
                        title = "Already sent",
                        body = "This bill was sent to the government. Nothing more to do."
                    )
                    OutlinedButton(onClick = {
                        vwe?.let { EInvoiceStore.clear(it.voucher.companyId, it.voucher.id, context) }
                        tick++
                    }) { Text("Undo (mark as not sent)") }
                }

                !needs && missingGstin -> BigStatusCard(
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFFEF6C00),
                    title = "Customer's GST number missing",
                    body = "This bill is marked B2B, but the customer's account has no GST number (GSTIN) saved. Add it in the customer's account, then come back here."
                )

                !needs -> BigStatusCard(
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF1565C0),
                    title = "Nothing to do",
                    body = "This bill does not need to be sent to the government. Only bills to customers who have a GST number (B2B) and export bills need an e-invoice."
                )

                customerLedger == null -> BigStatusCard(
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFFEF6C00),
                    title = "Add a customer first",
                    body = "This bill has no customer on it yet. Go back and add one, then come back here."
                )

                savedParty == null || editingAddress -> {
                    Text("Step 1: Where does this customer live?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("The government needs this before it can accept the bill.", fontSize = 14.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = address, onValueChange = { address = it }, label = { Text("Address") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2
                    )
                    OutlinedTextField(
                        value = place, onValueChange = { place = it }, label = { Text("City") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = pincode, onValueChange = { pincode = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("PIN code (6 digits)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Button(
                        onClick = {
                            if (address.isNotBlank() && pincode.trim().length == 6) {
                                EInvoiceStore.savePartyDetails(customerLedger.id, address, place, pincode, context)
                                editingAddress = false
                                tick++
                            } else {
                                errorMsg = "Enter the address and a 6-digit PIN code."
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Save", fontSize = 18.sp) }
                }

                else -> {
                    Text("Step 2: Send it", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "This will open the government website inside the app and fill in the bill for you. If it asks for a code sent to your phone, type the code you receive.",
                        fontSize = 14.sp, color = Color.Gray
                    )
                    Button(
                        onClick = {
                            val comp = company ?: return@Button
                            val v = vwe ?: return@Button
                            val problems = GstEwayBillExporter.validateForGovernmentPortal(v, customerLedger, stockMap, address, pincode, forEInvoice = true)
                            if (problems.isNotEmpty()) {
                                errorMsg = problems.joinToString("\n")
                                return@Button
                            }
                            val json = GstEwayBillExporter.generateEInvoiceJson(
                                comp, v, customerLedger, stockMap,
                                partyAddress = address, partyPlace = place, partyPincode = pincode, unitsMap = unitsMap
                            )
                            val fileName = "eInvoice_" + v.voucher.voucherNumber.replace("/", "_") + ".json"
                            val file = java.io.File(context.cacheDir, fileName)
                            java.io.FileWriter(file).use { it.write(json.toString(2)) }
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            navController.currentBackStackEntry?.savedStateHandle?.apply {
                                set("portal_url", "https://einvoice1.gst.gov.in/")
                                set("attach_uri", uri.toString())
                                set("attach_file_name", fileName)
                                set("wanted_doc_no", v.voucher.voucherNumber)
                                set("portal_company_id", v.voucher.companyId)
                                set("portal_voucher_id", v.voucher.id)
                            }
                            navController.navigate(com.minhas.teller.navigation.Screen.PortalAutopilot.route)
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Send to Government", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(onClick = { editingAddress = true }) { Text("Edit customer's address") }
                    TextButton(onClick = { showAdvanced = !showAdvanced }) {
                        Text(if (showAdvanced) "Hide the old way" else "Having trouble? Do it the old way")
                    }

                    if (showAdvanced) {
                        HorizontalDivider()
                        Text("Old way: share the file yourself, upload it on the government website, then bring the reply back here.", fontSize = 12.sp, color = Color.Gray)
                        OutlinedButton(
                            onClick = {
                                val comp = company ?: return@OutlinedButton
                                val v = vwe ?: return@OutlinedButton
                                val json = GstEwayBillExporter.generateEInvoiceJson(
                                    comp, v, customerLedger, stockMap,
                                    partyAddress = address, partyPlace = place, partyPincode = pincode, unitsMap = unitsMap
                                )
                                GstEwayBillExporter.shareJsonFile(context, json, "eInvoice_" + v.voucher.voucherNumber + ".json")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Share the file") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { navController.navigate(com.minhas.teller.navigation.Screen.QrScanner.route) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Scan QR")
                            }
                        }
                        OutlinedTextField(
                            value = eInvoicePaste,
                            onValueChange = { eInvoicePaste = it.take(200_000) },
                            label = { Text("Or paste the reply here") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4
                        )
                        Button(
                            enabled = eInvoicePaste.isNotBlank(),
                            onClick = {
                                val v = vwe ?: return@Button
                                val rec = EInvoiceStore.parseReply(eInvoicePaste, v.voucher.voucherNumber)
                                if (rec == null) {
                                    val portalError = EInvoiceStore.findError(eInvoicePaste, v.voucher.voucherNumber)
                                    errorMsg = portalError?.let { "The government website rejected this: $it" }
                                        ?: "Could not find a receipt number for this bill in that reply."
                                } else {
                                    EInvoiceStore.save(v.voucher.companyId, v.voucher.id, rec, context)
                                    EInvoiceStore.setEnabled(v.voucher.companyId, true, context)
                                    eInvoicePaste = ""
                                    errorMsg = null
                                    tick++
                                }
                            }
                        ) { Text("Save") }
                    }
                }
            }

            errorMsg?.let { msg ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C).copy(alpha = 0.1f))) {
                    Text(msg, modifier = Modifier.padding(14.dp), color = Color(0xFFC62828), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun BigStatusCard(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Text(body, fontSize = 14.sp)
        }
    }
}
