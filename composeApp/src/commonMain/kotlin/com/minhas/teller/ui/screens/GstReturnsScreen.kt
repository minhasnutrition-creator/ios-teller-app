package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.core.content.FileProvider
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.gst.GstReturn
import com.minhas.teller.data.gst.GstReturnBuilder
import com.minhas.teller.data.gst.Gstr1Json
import com.minhas.teller.data.gst.Gstr3bJson
import com.minhas.teller.data.gst.TaxRegion
import com.minhas.teller.navigation.Screen
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.GstReturnPrefs
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GstReturnsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)

    val voucherRepository = remember {
        VoucherRepository(db.voucherDao(), db.voucherEntryDao(), db.inventoryEntryDao(), db.billAllocationDao(), db.auditLogDao(), db.ledgerDao(), db = db)
    }

    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()

    var company by remember { mutableStateOf<Company?>(null) }
    var gstReturn by remember { mutableStateOf<GstReturn?>(null) }
    var gstr1Json by remember { mutableStateOf("") }
    var gstr3bJson by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(0) }
    var month by remember { mutableStateOf(0) } // 0-based
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        year = cal.get(Calendar.YEAR)
        month = cal.get(Calendar.MONTH)
    }

    LaunchedEffect(currentCompanyId, year, month) {
        val id = currentCompanyId ?: return@LaunchedEffect
        if (year == 0) return@LaunchedEffect
        loading = true
        val comp = db.companyDao().getCompanyById(id).firstOrNull()
        company = comp
        if (!TaxRegion.isIndia(comp?.country)) {
            gstReturn = null
            loading = false
            return@LaunchedEffect
        }
        val ledgers = db.ledgerDao().getAllLedgersSync(id)
        val (from, to) = monthRange(year, month)
        val vouchers = voucherRepository.getAllVouchersWithAllEntriesBetweenDates(id, from, to)
        val r = GstReturnBuilder.buildGstReturn(vouchers, ledgers, comp, from, to)
        gstReturn = r
        gstr1Json = Gstr1Json.toJson(r).toString(2)
        gstr3bJson = Gstr3bJson.toJson(r).toString(2)
        loading = false
    }

    val monthLabel = remember(year, month) {
        if (year == 0) "" else {
            val cal = Calendar.getInstance().apply { set(year, month, 1) }
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText("GST Returns", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F1A12))
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0F1A12), Color(0xFF060D40))))
                .padding(padding)
        ) {
            // Month selector
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { month -= 1 }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = Color.White)
                }
                AutoShrinkText(monthLabel, color = Color.White, fontWeight = FontWeight.Bold, maxFontSize = 16.sp)
                IconButton(onClick = { month += 1 }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = Color.White)
                }
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF69F0AE))
                }
                !TaxRegion.isIndia(company?.country) -> notIndiaCard()
                gstReturn == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(TerminologyManager.getTerm("Select a company to prepare returns"), color = Color.White.copy(alpha = 0.7f))
                }
                else -> {
                    val r = gstReturn!!
                    val outTaxable = r.outward.sumOf { it.taxableValue }
                    val outCgst = r.outward.sumOf { it.cgst }
                    val outSgst = r.outward.sumOf { it.sgst }
                    val outIgst = r.outward.sumOf { it.igst }
                    val itc = r.inward.sumOf { it.cgst + it.sgst + it.igst }

                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        item { totalsCard(r, outTaxable, outCgst, outSgst, outIgst, itc) }
                        if (r.warnings.isNotEmpty()) {
                            item { warningsCard(r.warnings) }
                        }
                        item { actionsCard(navController, context, currentCompanyId, r, gstr1Json, gstr3bJson) }
                    }
                }
            }
        }
    }
}

@Composable
private fun notIndiaCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(TerminologyManager.getTerm("GST filing is available for India"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(TerminologyManager.getTerm("This company is not set up for India GST. Tax return filing for other countries is added separately."),
                color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun totalsCard(r: GstReturn, outTaxable: Double, cgst: Double, sgst: Double, igst: Double, itc: Double) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AutoShrinkText("GSTIN: ${r.gstin.ifBlank { "Not set" }}", color = Color.White.copy(alpha = 0.7f), maxFontSize = 12.sp)
            AutoShrinkText("Period: ${r.fp}", color = Color.White.copy(alpha = 0.7f), maxFontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            AutoShrinkText("Outward taxable: ₹${"%,.2f".format(outTaxable)}", color = Color.White, maxFontSize = 15.sp, fontWeight = FontWeight.Bold)
            AutoShrinkText("CGST+SGST: ₹${"%,.2f".format(cgst + sgst)}", color = Color(0xFF69F0AE), maxFontSize = 13.sp, fontWeight = FontWeight.Bold)
            AutoShrinkText("IGST: ₹${"%,.2f".format(igst)}", color = Color(0xFF40C4FF), maxFontSize = 13.sp, fontWeight = FontWeight.Bold)
            AutoShrinkText("Input tax credit (ITC): ₹${"%,.2f".format(itc)}", color = Color(0xFFFFB74D), maxFontSize = 13.sp, fontWeight = FontWeight.Bold)
            AutoShrinkText("Net liability: ₹${"%,.2f".format(cgst + sgst + igst - itc)}", color = Color.White, maxFontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun warningsCard(warnings: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x33FFD54F))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(TerminologyManager.getTerm("Before you file"), color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            warnings.forEach { w ->
                Text("• $w", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun actionsCard(navController: NavHostController, context: android.content.Context, companyId: Int?, r: GstReturn, gstr1: String, gstr3b: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = {
                val id = companyId ?: return@Button
                navController.navigate(Screen.GstFilingWizard.createRoute(id, r.fp))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE))
        ) { Text(TerminologyManager.getTerm("Start guided filing"), color = Color(0xFF0F1A12), fontWeight = FontWeight.Bold) }

        Button(
            onClick = {
                navController.navigate(Screen.Gstr2bReconciler.route)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF45E0E6))
        ) {
            Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF0F1A12))
            Spacer(Modifier.width(8.dp))
            Text(TerminologyManager.getTerm("Reconcile GSTR-2B (ITC Matcher)"), color = Color(0xFF0F1A12), fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = {
                navController.navigate(Screen.WarningDetail.route.replace("{warningId}", "einvoice_pending"))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
        ) {
            Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(TerminologyManager.getTerm("Send Bills to Government (e-Invoice)"), color = Color.White, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = { exportJson(context, "GSTR1_${r.fp}.json", gstr1) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26A69A))
        ) { Text(TerminologyManager.getTerm("Export GSTR-1 JSON"), color = Color.White) }

        Button(
            onClick = { exportJson(context, "GSTR3B_${r.fp}.json", gstr3b) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5))
        ) { Text(TerminologyManager.getTerm("Export GSTR-3B JSON"), color = Color.White) }

        Button(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gst.gov.in")))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE))
        ) { Text(TerminologyManager.getTerm("File on GST Portal"), color = Color(0xFF0F1A12), fontWeight = FontWeight.Bold) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    val id = companyId ?: return@OutlinedButton
                    GstReturnPrefs.setLastFiledGstr1(context, id, r.fp)
                    android.widget.Toast.makeText(context, "Marked GSTR-1 ${r.fp} as filed", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) { Text(TerminologyManager.getTerm("Mark GSTR-1 filed"), color = Color.White) }

            OutlinedButton(
                onClick = {
                    val id = companyId ?: return@OutlinedButton
                    GstReturnPrefs.setLastFiledGstr3b(context, id, r.fp)
                    android.widget.Toast.makeText(context, "Marked GSTR-3B ${r.fp} as filed", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) { Text(TerminologyManager.getTerm("Mark GSTR-3B filed"), color = Color.White) }
        }

        Text(TerminologyManager.getTerm("Export the JSON, then upload it via the GST portal's Offline Utility. Final OTP + DSC/EVC submission happens on the portal."),
            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
        )
    }
}

private fun exportJson(context: android.content.Context, fileName: String, json: String) {
    try {
        val file = File(context.getExternalFilesDir(null), fileName)
        file.writeText(json)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export $fileName"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun monthRange(year: Int, month0Based: Int): Pair<Date, Date> {
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(year, month0Based, 1, 0, 0, 0)
    val from = cal.time
    cal.add(Calendar.MONTH, 1)
    cal.add(Calendar.MILLISECOND, -1)
    return from to cal.time
}
