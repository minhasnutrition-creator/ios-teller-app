package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.core.content.FileProvider
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.viewmodels.VoucherViewModel
import com.minhas.teller.ui.viewmodels.VoucherViewModelFactory
import com.minhas.teller.utils.GstComplianceRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.firstOrNull

data class HsnGstSummary(
    val hsn: String,
    val description: String,
    val quantity: Double,
    val taxableValue: Double,
    val cgst: Double,
    val sgst: Double,
    val igst: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GstReportScreen(navController: NavHostController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)

    val voucherRepository = remember {
        VoucherRepository(db.voucherDao(), db.voucherEntryDao(), db.inventoryEntryDao(), db.billAllocationDao(), db.auditLogDao(), db.ledgerDao(), db = db)
    }

    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()

    var gstin by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf<List<HsnGstSummary>>(emptyList()) }
    var totalItc by remember { mutableStateOf(0.0) }

    LaunchedEffect(currentCompanyId) {
        currentCompanyId?.let { id ->
            db.companyDao().getCompanyById(id).firstOrNull()?.let { gstin = it.gstin ?: "" }
            val allVouchers = voucherRepository.getAllVouchersWithAllEntries(id)
            val stockNames = db.stockItemDao().getAllStockItems(id).associate { it.id to it.name }
            summary = computeHsnGstSummary(allVouchers, stockNames)
            totalItc = allVouchers
                .filter { it.voucher.type.equals("Purchase", true) }
                .sumOf { vwe -> vwe.inventoryEntries.sumOf { GstComplianceRules.lineTax(it) } }
        }
    }

    val totals = summary.fold(Triple(0.0, 0.0, 0.0)) { acc, s ->
        Triple(acc.first + s.taxableValue, acc.second + s.cgst + s.sgst, acc.third + s.igst)
    }
    val outputTax = totals.second + totals.third
    val netTax = outputTax - totalItc

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText("GST Summary", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        exportGstJson(context, gstin, summary, totals)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export JSON", tint = Color.White)
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
            // Totals card
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AutoShrinkText("GSTIN: ${gstin.ifBlank { "Not set" }}", color = Color.White.copy(alpha = 0.7f), maxFontSize = 12.sp)
                    AutoShrinkText("Taxable Value: ₹${"%,.2f".format(totals.first)}", color = Color.White, maxFontSize = 15.sp, fontWeight = FontWeight.Bold)
                    AutoShrinkText("Output tax (sales): ₹${"%,.2f".format(outputTax)}", color = Color(0xFF69F0AE), maxFontSize = 13.sp, fontWeight = FontWeight.Bold)
                    AutoShrinkText("Input tax credit (purchases): ₹${"%,.2f".format(totalItc)}", color = Color(0xFFFFB74D), maxFontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    AutoShrinkText("Net GST payable: ₹${"%,.2f".format(netTax)}", color = if (netTax > 0.0) Color(0xFFFF5252) else Color(0xFF00E676), maxFontSize = 15.sp, fontWeight = FontWeight.Bold)
                    AutoShrinkText(if (netTax > 0.0) "Amount to pay after credit" else "No payment — ITC exceeds output tax", color = Color.White.copy(alpha = 0.6f), maxFontSize = 11.sp, minFontSize = 8.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(TerminologyManager.getTerm("Tap the share icon (top) to export as JSON"), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }

            Button(
                onClick = {
                    val id = currentCompanyId ?: return@Button
                    val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                    val fp = String.format(Locale.US, "%02d%04d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
                    navController.navigate(Screen.GstFilingWizard.createRoute(id, fp))
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE))
            ) { Text(TerminologyManager.getTerm("Start guided filing"), color = Color(0xFF0F1A12), fontWeight = FontWeight.Bold) }

            // HSN-wise table
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AutoShrinkText(TerminologyManager.getTerm("HSN"), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        AutoShrinkText(TerminologyManager.getTerm("Taxable"), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                        AutoShrinkText(TerminologyManager.getTerm("Tax"), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                }
                items(summary, key = { it.hsn }) { s ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                AutoShrinkText(s.hsn, color = Color.White, fontWeight = FontWeight.Bold, maxFontSize = 13.sp)
                                Text(s.description, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1)
                            }
                            AutoShrinkText("₹${"%,.0f".format(s.taxableValue)}", color = Color.White, maxFontSize = 12.sp, modifier = Modifier.weight(1.2f))
                            AutoShrinkText("₹${"%,.0f".format(s.cgst + s.sgst + s.igst)}", color = Color(0xFF69F0AE), maxFontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private fun computeHsnGstSummary(
    vouchers: List<com.minhas.teller.data.entities.VoucherWithAllEntries>,
    stockNames: Map<Int, String>
): List<HsnGstSummary> {
    // Sales transactions add to the HSN summary.
    val map = LinkedHashMap<String, MutableList<com.minhas.teller.data.entities.InventoryEntry>>()
    vouchers.forEach { vwe ->
        if (!vwe.voucher.type.equals("Sales", true)) return@forEach
        vwe.inventoryEntries.forEach { entry ->
            val hsn = entry.hsnCode?.takeIf { it.isNotBlank() } ?: "9999"
            map.getOrPut(hsn) { mutableListOf() }.add(entry)
        }
    }
    return map.map { (hsn, entries) ->
        var taxable = 0.0
        var cgst = 0.0
        var sgst = 0.0
        var igst = 0.0
        var qty = 0.0
        entries.forEach { entry ->
            taxable += GstComplianceRules.taxableValue(entry)
            val (c, s, i) = GstComplianceRules.taxSplit(entry)
            cgst += c
            sgst += s
            igst += i
            qty += entry.quantity
        }
        val stockItemId = entries.firstOrNull()?.stockItemId
        HsnGstSummary(hsn, stockItemId?.let { stockNames[it] } ?: "", qty, taxable, cgst, sgst, igst)
    }.sortedBy { it.hsn }
}

private fun exportGstJson(context: android.content.Context, gstin: String, summary: List<HsnGstSummary>, totals: Triple<Double, Double, Double>) {
    try {
        val json = JSONObject()
        json.put("gstin", gstin)
        json.put("return_period", SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()))
        val summaryObj = JSONObject()
        summaryObj.put("taxable_value", totals.first)
        summaryObj.put("cgst", summary.sumOf { it.cgst })
        summaryObj.put("sgst", summary.sumOf { it.sgst })
        summaryObj.put("igst", totals.third)
        json.put("summary", summaryObj)

        val arr = JSONArray()
        summary.forEach { s ->
            val o = JSONObject()
            o.put("hsn", s.hsn)
            o.put("description", s.description)
            o.put("quantity", s.quantity)
            o.put("taxable_value", s.taxableValue)
            o.put("cgst", s.cgst)
            o.put("sgst", s.sgst)
            o.put("igst", s.igst)
            arr.put(o)
        }
        json.put("hsn_wise", arr)

        val file = File(context.getExternalFilesDir(null), "GST_Summary_${System.currentTimeMillis()}.json")
        file.writeText(json.toString(2))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export GST JSON"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}
