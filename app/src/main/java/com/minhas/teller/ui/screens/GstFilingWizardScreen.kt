package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.gst.GstReturn
import com.minhas.teller.data.gst.GstReturnBuilder
import com.minhas.teller.data.gst.Gstr1Json
import com.minhas.teller.data.gst.Gstr3bJson
import com.minhas.teller.data.gst.TaxRegion
import com.minhas.teller.data.gst.parseFp
import com.minhas.teller.data.gst.round2
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.utils.GstFilingPrefs
import com.minhas.teller.utils.GstReturnPrefs
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val GST_LOGIN_URL = "https://services.gst.gov.in/services/login"

private const val STEP_DOWNLOAD = "download"
private const val STEP_GSTR1 = "gstr1_portal"
private const val STEP_GSTR3B = "gstr3b_portal"
private const val STEP_SUBMIT = "submit"

private data class WizardStep(
    val id: String,
    val stepLabel: String,
    val title: String,
    val description: String,
    val actionLabel: String?,
    val accent: Color,
    val onAccent: Color,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GstFilingWizardScreen(
    navController: NavHostController,
    companyId: Int,
    fp: String
) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val voucherRepository = remember {
        VoucherRepository(db.voucherDao(), db.voucherEntryDao(), db.inventoryEntryDao(), db.billAllocationDao(), db.auditLogDao(), db.ledgerDao(), db = db)
    }

    var company by remember { mutableStateOf<Company?>(null) }
    var gstReturn by remember { mutableStateOf<GstReturn?>(null) }
    var gstr1Json by remember { mutableStateOf("") }
    var gstr3bJson by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var periodFp by remember(fp) { mutableStateOf(fp) }

    LaunchedEffect(companyId, periodFp) {
        loading = true
        val comp = db.companyDao().getCompanyById(companyId).firstOrNull()
        company = comp
        if (comp == null || !TaxRegion.isIndia(comp.country)) {
            gstReturn = null
            loading = false
            return@LaunchedEffect
        }
        val ledgers = db.ledgerDao().getAllLedgersSync(companyId)
        val (from, to) = monthRangeFromFp(periodFp) ?: run { loading = false; return@LaunchedEffect }
        val vouchers = voucherRepository.getAllVouchersWithAllEntriesBetweenDates(companyId, from, to)
        val r = GstReturnBuilder.buildGstReturn(vouchers, ledgers, comp, from, to)
        gstReturn = r
        gstr1Json = Gstr1Json.toJson(r).toString(2)
        gstr3bJson = Gstr3bJson.toJson(r).toString(2)
        loading = false
    }

    var completed by remember(companyId, periodFp) {
        mutableStateOf(GstFilingPrefs.completedSteps(context, companyId, periodFp))
    }

    fun toggle(stepId: String) {
        val nowDone = !completed.contains(stepId)
        if (nowDone) GstFilingPrefs.markStepDone(context, companyId, periodFp, stepId)
        else GstFilingPrefs.unmarkStepDone(context, companyId, periodFp, stepId)
        completed = GstFilingPrefs.completedSteps(context, companyId, periodFp)
        if (nowDone) {
            when (stepId) {
                STEP_GSTR1 -> GstReturnPrefs.setLastFiledGstr1(context, companyId, periodFp)
                STEP_GSTR3B -> GstReturnPrefs.setLastFiledGstr3b(context, companyId, periodFp)
            }
        }
    }

    fun openPortal(stepId: String) {
        val title = if (stepId == STEP_GSTR1) "GSTR-1 — GST Login" else "GSTR-3B — GST Login"
        navController.navigate(Screen.GstPortalWebView.createRoute(GST_LOGIN_URL, title))
    }

    fun downloadFiles() {
        val results = listOf("GSTR1_${periodFp}.json" to gstr1Json, "GSTR3B_${periodFp}.json" to gstr3bJson)
            .map { (name, json) -> name to saveJsonToDownloads(context, name, json) }
        val saved = results.count { it.second }
        val msg = if (saved == results.size) "Saved GSTR-1 & GSTR-3B to Downloads"
        else "Saved $saved of ${results.size} files to Downloads"
        scope.launch { snackbarHostState.showSnackbar(msg) }
        if (saved == results.size && !completed.contains(STEP_DOWNLOAD)) {
            toggle(STEP_DOWNLOAD)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText("File GST Returns", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F1A12))
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0F1A12), Color(0xFF060D40))))
                .padding(padding)
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF69F0AE))
                }
                !TaxRegion.isIndia(company?.country) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(TerminologyManager.getTerm("Guided filing is available for India"), color = Color.White.copy(alpha = 0.7f))
                }
                gstReturn == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(TerminologyManager.getTerm("Unable to prepare the return"), color = Color.White.copy(alpha = 0.7f))
                }
                else -> {
                    val r = gstReturn!!
                    val steps = remember(r) {
                        listOf(
                            WizardStep(STEP_DOWNLOAD, "STEP 1", "Download the return files", "We've prepared GSTR-1 and GSTR-3B from your books. Save both files to your phone's Downloads folder.", "Download files", Color(0xFF69F0AE), Color(0xFF0F1A12), Icons.Default.Download),
                            WizardStep(STEP_GSTR1, "STEP 2", "File GSTR-1", "Login, then: Services → Returns → Returns Dashboard. Pick this month, open GSTR-1, upload the file we made, and submit with OTP.", "Login to GST", Color(0xFF26A69A), Color.White, Icons.Default.Upload),
                            WizardStep(STEP_GSTR3B, "STEP 3", "File GSTR-3B", "Back on the portal: Services → Returns → Returns Dashboard → GSTR-3B. Upload the file, pay the challan if shown, then submit with OTP.", "Login to GST", Color(0xFF3F51B5), Color.White, Icons.Default.Upload),
                            WizardStep(STEP_SUBMIT, "STEP 4", "Submit with OTP / DSC", "Enter the OTP sent to your registered phone (or use your DSC). You're done when the portal shows 'Filed'.", null, Color(0xFFFFB74D), Color(0xFF0F1A12), Icons.Default.VerifiedUser)
                        )
                    }
                    val doneCount = steps.count { it.id in completed }

                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        item { monthSelector(periodFp, onPrev = { periodFp = shiftFp(periodFp, -1) }, onNext = { periodFp = shiftFp(periodFp, 1) }) }
                        item { summaryCard(r, periodFp) }
                        if (r.warnings.isNotEmpty()) {
                            item { warningsCard(r.warnings) }
                        }
                        item {
                            eInvoiceCard(onClick = {
                                navController.navigate(Screen.WarningDetail.route.replace("{warningId}", "einvoice_pending"))
                            })
                        }
                        item { progressHeader(doneCount, steps.size) }
                        items(steps) { step ->
                            stepCard(step, done = step.id in completed, onAction = {
                                when (step.id) {
                                    STEP_DOWNLOAD -> downloadFiles()
                                    STEP_GSTR1, STEP_GSTR3B -> openPortal(step.id)
                                }
                            }, onToggle = { toggle(step.id) })
                        }
                        item { portalGuideCard() }
                    }
                }
            }
        }
    }
}

@Composable
private fun monthSelector(fp: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = Color.White)
        }
        AutoShrinkText(fpLabel(fp), color = Color.White, fontWeight = FontWeight.Bold, maxFontSize = 15.sp)
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = Color.White)
        }
    }
}

private fun fpLabel(fp: String): String {
    val (m, y) = parseFp(fp) ?: return fp
    val cal = Calendar.getInstance().apply { clear(); set(y, m - 1, 1) }
    return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
}

private fun shiftFp(fp: String, delta: Int): String {
    val (m, y) = parseFp(fp) ?: return fp
    val cal = Calendar.getInstance().apply { clear(); set(y, m - 1, 1); add(Calendar.MONTH, delta) }
    return String.format(Locale.US, "%02d%04d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
}

/** Save a JSON file to the shared Downloads folder (MediaStore on 10+, legacy fallback below). */
private fun saveJsonToDownloads(context: android.content.Context, fileName: String, content: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } ?: return false
            true
        } else {
            val file = File(context.getExternalFilesDir(null), fileName)
            file.writeText(content)
            ExportUtils.saveFileToDownloads(context, file, fileName, "application/json")
        }
    } catch (e: Exception) {
        false
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun summaryCard(r: GstReturn, fp: String) {
    val outCgst = r.outward.sumOf { it.cgst }
    val outSgst = r.outward.sumOf { it.sgst }
    val outIgst = r.outward.sumOf { it.igst }
    val itc = r.inward.sumOf { it.cgst + it.sgst + it.igst }
    val taxCollected = outCgst + outSgst + outIgst
    val netLiability = (taxCollected - itc).round2()

    val (m, y) = parseFp(fp) ?: (0 to 0)
    val dueFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val gstr1Due = if (m in 1..12) dueFmt.format(Calendar.getInstance().apply { clear(); set(y, m, 11) }.time) else ""
    val gstr3bDue = if (m in 1..12) dueFmt.format(Calendar.getInstance().apply { clear(); set(y, m, 20) }.time) else ""

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF26A69A).copy(alpha = 0.22f), Color(0xFF3F51B5).copy(alpha = 0.22f))))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AutoShrinkText(TerminologyManager.getTerm("NET TAX PAYABLE"), color = Color(0xFF69F0AE), maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            AutoShrinkText(
                "₹${"%,.2f".format(netLiability)}",
                color = if (netLiability > 0.0) Color.White else Color(0xFF69F0AE),
                maxFontSize = 34.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                if (netLiability > 0.0) "Keep ₹${"%,.2f".format(netLiability)} ready in your bank account."
                else "No payment needed — your input tax credit covers the liability.",
                color = if (netLiability > 0.0) Color.White.copy(alpha = 0.85f) else Color(0xFF69F0AE),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            AutoShrinkText("Period: ${fpLabel(fp)}  •  GSTIN: ${r.gstin.ifBlank { "Not set" }}", color = Color.White.copy(alpha = 0.6f), maxFontSize = 12.sp)

            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chip("Tax collected ₹${"%,.2f".format(taxCollected)}", Color(0xFF69F0AE))
                chip("ITC ₹${"%,.2f".format(itc)}", Color(0xFFFFB74D))
                chip("GSTR-1 due $gstr1Due", Color(0xFF40C4FF))
                chip("GSTR-3B due $gstr3bDue", Color(0xFF8C9EFF))
            }
        }
    }
}

@Composable
private fun chip(label: String, accent: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        AutoShrinkText(label, color = accent, maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold)
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
private fun eInvoiceCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF00897B).copy(alpha = 0.16f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Send, null, tint = Color(0xFF00897B), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(TerminologyManager.getTerm("E-Invoice"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(
                TerminologyManager.getTerm("Sales bills that need to be sent to the government as an e-invoice, separate from your monthly GSTR filing."),
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
            ) { Text(TerminologyManager.getTerm("Send Bills to Government"), color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun progressHeader(doneCount: Int, total: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        AutoShrinkText("$doneCount of $total steps done", color = Color.White.copy(alpha = 0.7f), maxFontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else doneCount / total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF69F0AE),
            trackColor = Color.White.copy(alpha = 0.12f)
        )
    }
}

@Composable
private fun stepCard(step: WizardStep, done: Boolean, onAction: () -> Unit, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AutoShrinkText(step.stepLabel, color = step.accent, maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(step.accent.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(step.icon, null, tint = step.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                AutoShrinkText(step.title, color = Color.White, fontWeight = FontWeight.Bold, maxFontSize = 15.sp, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (done) Color(0xFF00E676) else Color.White.copy(alpha = 0.3f)
                )
            }

            Text(step.description, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step.actionLabel != null) {
                    Button(
                        onClick = onAction,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = step.accent)
                    ) { Text(step.actionLabel, color = step.onAccent, fontWeight = FontWeight.Bold) }
                }
                OutlinedButton(
                    onClick = onToggle,
                    modifier = if (step.actionLabel != null) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, if (done) Color(0xFF69F0AE) else step.accent.copy(alpha = 0.6f))
                ) { Text(if (done) "Done ✓" else "Mark done", color = if (done) Color(0xFF69F0AE) else Color.White) }
            }
        }
    }
}

@Composable
private fun portalGuideCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF40C4FF).copy(alpha = 0.08f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = Color(0xFF40C4FF), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(TerminologyManager.getTerm("How to upload on the GST portal"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(TerminologyManager.getTerm("After you login, follow these taps:"), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            guideStep("1", "Tap Services → Returns → Returns Dashboard")
            guideStep("2", "Pick the Financial Year and the month you are filing")
            guideStep("3", "GSTR-1: tap GSTR-1 → Prepare Online → Upload, and choose the file we saved to Downloads")
            guideStep("4", "GSTR-3B: tap GSTR-3B → Prepare Online, upload or enter, pay the challan if it shows")
            guideStep("5", "Tap File / Submit and enter the OTP from your phone")
        }
    }
}

@Composable
private fun guideStep(num: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(num, color = Color(0xFF40C4FF), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(18.dp))
        Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
    }
}

private fun monthRangeFromFp(fp: String): Pair<Date, Date>? {
    val (m, y) = parseFp(fp) ?: return null
    val cal = Calendar.getInstance().apply { clear(); set(y, m - 1, 1, 0, 0, 0) }
    val from = cal.time
    cal.add(Calendar.MONTH, 1)
    cal.add(Calendar.MILLISECOND, -1)
    return from to cal.time
}
