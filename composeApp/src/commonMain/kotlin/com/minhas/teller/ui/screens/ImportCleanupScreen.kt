package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.ImportJob
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.theme.*
import com.minhas.teller.ui.viewmodels.ImportCleanupUiState
import com.minhas.teller.ui.viewmodels.ImportCleanupViewModel
import com.minhas.teller.ui.viewmodels.ImportCleanupViewModelFactory
import com.minhas.teller.utils.ExcelImportSampleGenerator
import com.minhas.teller.utils.VoucherExcelImporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCleanupScreen(navController: NavController) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val factory = remember { ImportCleanupViewModelFactory(db) }
    val viewModel: ImportCleanupViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    val companyId by SessionManager.currentCompanyId.collectAsState(initial = 1)
    val targetCompanyId = companyId ?: 1

    LaunchedEffect(targetCompanyId) {
        viewModel.loadJobs(targetCompanyId)
    }

    LaunchedEffect(uiState.isVoucherImportSuccess) {
        if (uiState.isVoucherImportSuccess) {
            Toast.makeText(context, "✓ Vouchers imported successfully!", Toast.LENGTH_LONG).show()
            viewModel.resetVoucherImportSuccess()
        }
    }

    var showPurgeConfirmDialog by remember { mutableStateOf(false) }

    // File pickers
    val universalPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.startUniversalImport(context, targetCompanyId, it)
        }
    }

    val xmlPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.startUniversalImport(context, targetCompanyId, it)
        }
    }

    val excelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.parseVoucherExcel(context, targetCompanyId, it)
        }
    }

    val csvPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.startImportJob(context, targetCompanyId, it, "csv")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText("Import & Cleanup Books", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F182E))
            )
        },
        containerColor = Color(0xFF0F182E)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F182E),
                            Color(0xFF1E2D4A)
                        )
                    )
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top-Level Error Alert Banner
                if (uiState.importError != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.2f)),
                            border = BorderStroke(1.5.dp, Color(0xFFEF5350)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFEF5350).copy(alpha = 0.2f), RoundedCornerShape(18.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = "Error",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    AutoShrinkText(TerminologyManager.getTerm("Import Issue Detected"),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxFontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        uiState.importError ?: "",
                                        color = Color(0xFFFFCDD2),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.dismissError() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Top-Level Success Banner
                if (uiState.importSuccessMessage != null && !uiState.isProcessing) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF00C853).copy(alpha = 0.2f)),
                            border = BorderStroke(1.5.dp, Color(0xFF00E676)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF00E676).copy(alpha = 0.2f), RoundedCornerShape(18.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    AutoShrinkText(TerminologyManager.getTerm("Import Successful"),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxFontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        uiState.importSuccessMessage ?: "",
                                        color = Color(0xFFB9F6CA),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.dismissSuccess() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Real-time Progress Card
                if (uiState.isProcessing) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF132238)),
                            border = BorderStroke(1.5.dp, Color(0xFF40C4FF).copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.5.dp,
                                            color = Color(0xFF40C4FF)
                                        )
                                        AutoShrinkText(
                                            text = uiState.progressStage.ifBlank { "Processing Data..." },
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxFontSize = 14.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF40C4FF).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        AutoShrinkText(
                                            text = "${uiState.progressPercent}%",
                                            color = Color(0xFF40C4FF),
                                            fontWeight = FontWeight.Bold,
                                            maxFontSize = 12.sp
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = uiState.progressFraction.coerceIn(0f, 1f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFF40C4FF),
                                    trackColor = Color.White.copy(alpha = 0.1f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AutoShrinkText(
                                        text = uiState.processingStatus.ifBlank { "Analyzing file structures..." },
                                        color = Color.White.copy(alpha = 0.8f),
                                        maxFontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (uiState.totalCount > 0) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        AutoShrinkText(
                                            text = "${uiState.processedCount} / ${uiState.totalCount}",
                                            color = Color(0xFF69F0AE),
                                            maxFontSize = 11.sp,
                                            minFontSize = 8.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 1: Ingestion Source Tiles
                item {
                    Text(
                        text = "Select Ingestion Source",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB8A0D0)
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { universalPicker.launch("*/*") },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2D4A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF00E676).copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CloudUpload,
                                    contentDescription = "Universal Backup",
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                AutoShrinkText(TerminologyManager.getTerm("Universal Backup Ingestion (Auto-Detect)"),
                                    maxFontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(TerminologyManager.getTerm("Upload ANY Tally (.001 / TBK / XML), Busy (.bsh / .zip), Excel, or CSV file"),
                                    fontSize = 12.sp,
                                    color = Color(0xFFB0BEC5)
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF80CBC4),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SourceTile(
                            title = "Tally XML / TBK",
                            subtitle = "Full Master & Voucher sync",
                            icon = Icons.Default.Code,
                            color = Color(0xFF69F0AE),
                            modifier = Modifier.weight(1f),
                            onClick = { xmlPicker.launch("*/*") }
                        )
                        SourceTile(
                            title = "Excel Sheets",
                            subtitle = "Sales, Purchase, Payments",
                            icon = Icons.Default.TableChart,
                            color = Color(0xFF40C4FF),
                            modifier = Modifier.weight(1f),
                            onClick = { excelPicker.launch("*/*") }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SourceTile(
                            title = "CSV Register",
                            subtitle = "Standard text table",
                            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                            color = Color(0xFFFFAB91),
                            modifier = Modifier.weight(1f),
                            onClick = { csvPicker.launch("*/*") }
                        )
                        SourceTile(
                            title = "WhatsApp Scan",
                            subtitle = "OCR receipt uploads",
                            icon = Icons.Default.PhotoCamera,
                            color = Color(0xFFCE93D8),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                Toast.makeText(context, "WhatsApp integration is automated via dynamic share. Use system Share sheet on PDF/images.", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }

                // Section: 100% Accurate Data Import Guidance Banner
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2D4A).copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF40C4FF).copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF40C4FF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(TerminologyManager.getTerm("For 100% Data Fidelity"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF40C4FF)
                                )
                            }
                            Text(TerminologyManager.getTerm("• From Tally: Press Alt+E → Format: XML (Data Interchange) → All Masters & Transactions. Teller parses all genuine vouchers, ledgers, and items without missing entries.\n• From Busy: Use Administration → Data Export → XML or Excel Register."),
                                fontSize = 12.sp,
                                color = Color(0xFFECEFF1).copy(alpha = 0.85f),
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                // Clean Books & Purge Corrupt Data Action Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2D4A)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF80CBC4).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF80CBC4).copy(alpha = 0.2f), RoundedCornerShape(22.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AutoFixHigh,
                                    contentDescription = "Clean Books",
                                    tint = Color(0xFF80CBC4),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                AutoShrinkText(TerminologyManager.getTerm("Clean Books & Purge Corrupt Data"),
                                    maxFontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(TerminologyManager.getTerm("Remove corrupt/synthetic vouchers, 0-balance template banks & import artifacts"),
                                    fontSize = 12.sp,
                                    color = Color(0xFFB0BEC5)
                                )
                            }
                            Button(
                                onClick = {
                                    showPurgeConfirmDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E676),
                                    contentColor = Color(0xFF003314)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(TerminologyManager.getTerm("Clean Now"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Section 3: Voucher Excel Import Card (6.7.25 feature)
                item {
                    VoucherExcelImportCard(
                        uiState = uiState,
                        onDownloadTemplate = { template ->
                            viewModel.downloadSampleTemplate(context, template)
                        },
                        onPickExcelFile = {
                            excelPicker.launch("*/*")
                        },
                        onDismissError = {
                            viewModel.dismissError()
                        },
                        onDownloadCorrected = {
                            viewModel.downloadCorrectedFile(context)
                        },
                        onConfirmImport = {
                            viewModel.commitVoucherImport(targetCompanyId)
                        },
                        onSwitchType = { newType ->
                            viewModel.switchVoucherType(context, targetCompanyId, newType)
                        }
                    )
                }

                // Section 4: Import Errors Card (when sheet has validation errors)
                if (uiState.importErrors.isNotEmpty()) {
                    item {
                        ImportErrorsCard(
                            errors = uiState.importErrors,
                            onDismiss = { viewModel.dismissError() }
                        )
                    }
                }

                // Section 5: Dry-Run Verification panel (Tally XML)
                if (uiState.isDryRunVerified && !uiState.isProcessing) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2A47)),
                            border = BorderStroke(1.5.dp, Color(0xFF69F0AE).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, "Verify", tint = Color(0xFF69F0AE), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(TerminologyManager.getTerm("Dry-Run Simulation Results"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                VerificationMetric("New Ledgers Found", uiState.dryRunLedgersCount.toString())
                                VerificationMetric("New Stock Items", uiState.dryRunStockCount.toString())
                                VerificationMetric("Total Vouchers parsed", uiState.dryRunVouchersCount.toString())
                                VerificationMetric("Estimated Sales", "₹${String.format("%.2f", uiState.dryRunSales)}")
                                VerificationMetric("Estimated Purchases", "₹${String.format("%.2f", uiState.dryRunPurchases)}")

                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.finalizeImport(targetCompanyId)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(TerminologyManager.getTerm("Finalize Ingest & Post Vouchers"), color = Color(0xFF0F182E), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Section 6: Spreadsheet Schema Column Mapper
                if (uiState.parsedHeaders.isNotEmpty() && !uiState.isDryRunVerified && !uiState.isProcessing) {
                    item {
                        Text(
                            text = "Map Columns Schema",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB8A0D0)
                        )
                    }

                    items(uiState.parsedHeaders) { header ->
                        val currentMapping = uiState.customMappings[header]
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    AutoShrinkText(header, color = Color.White, fontWeight = FontWeight.SemiBold, maxFontSize = 14.sp)
                                    AutoShrinkText("Detected: ${currentMapping?.first ?: "Unmapped"}.${currentMapping?.second ?: ""}", color = Color.White.copy(alpha = 0.6f), maxFontSize = 11.sp, minFontSize = 8.sp)
                                }

                                Button(
                                    onClick = {
                                        val nextField = when (currentMapping?.second) {
                                            "name" -> Pair("Ledger", "openingBalance")
                                            "openingBalance" -> Pair("Ledger", "gstin")
                                            "gstin" -> Pair("Voucher", "date")
                                            "date" -> Pair("VoucherEntry", "amount")
                                            else -> Pair("Ledger", "name")
                                        }
                                        viewModel.updateMapping(header, nextField.first, nextField.second)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(TerminologyManager.getTerm("Change target"), fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                viewModel.finalizeImport(targetCompanyId)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF40C4FF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(TerminologyManager.getTerm("Post Mapped Spreadsheets"), color = Color(0xFF0F182E), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Section 7: Import Jobs history Log
                item {
                    Text(
                        text = "Recent Import Activity",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB8A0D0)
                    )
                }

                if (uiState.activeJobs.isEmpty()) {
                    item {
                        Text(TerminologyManager.getTerm("No previous migration imports recorded for this company."),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    items(uiState.activeJobs) { job ->
                        JobActivityRow(job)
                    }
                }
            }
        }
    }

    // Clean Books & Purge Corrupt Data Confirmation Dialog
    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            title = {
                Text(TerminologyManager.getTerm("Clean Books & Purge Corrupt Data?"),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(TerminologyManager.getTerm("This will safely remove corrupt import data, template accounts, and database engine artifacts from this company:"),
                        color = Color(0xFFCFD8DC),
                        fontSize = 13.sp
                    )
                    Text(TerminologyManager.getTerm("• Corrupt or synthetic vouchers from failed imports\n") +
                        "• Statutory template bank accounts with 0 balance (SBI, Axis, PNB, ICICI, etc.)\n" +
                        "• Access & Jet engine metadata artifacts (TextBox, CheckBox, StoreData, ItemType)\n" +
                        "• Nameless ledgers and orphan voucher entries",
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Text(TerminologyManager.getTerm("Your real, genuine transactions and manually created accounts are protected and will NOT be touched."),
                        color = Color(0xFF69F0AE),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPurgeConfirmDialog = false
                        viewModel.cleanBooksAndPurgeBogusData(targetCompanyId) { res ->
                            Toast.makeText(
                                context,
                                "Cleaned ${res.deletedVouchers} corrupt vouchers, ${res.deletedLedgers} dummy accounts, and ${res.deletedStockItems} artifacts!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E676),
                        contentColor = Color(0xFF003314)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(TerminologyManager.getTerm("Clean Books Now"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text(TerminologyManager.getTerm("Cancel"), color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1E2D4A),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Smart Auto-Correction Dialog (6.7.25 feature)
    if (uiState.showCorrectionDialog) {
        MissingCategoryDialog(
            items = uiState.unrecognizedItems,
            missingColumns = uiState.missingColumns,
            onConfirm = { category ->
                viewModel.applySmartCorrection(context, targetCompanyId, category)
            },
            onDismiss = {
                viewModel.dismissCorrectionDialog()
            }
        )
    }
}

/**
 * Voucher Excel Import Card matching 6.7.25 layout & styling.
 */
@Composable
fun VoucherExcelImportCard(
    uiState: ImportCleanupUiState,
    onDownloadTemplate: (ExcelImportSampleGenerator.VoucherTemplate) -> Unit,
    onPickExcelFile: () -> Unit,
    onDismissError: () -> Unit,
    onDownloadCorrected: () -> Unit,
    onConfirmImport: () -> Unit,
    onSwitchType: (VoucherExcelImporter.ImportType) -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2A47)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF40C4FF).copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = null,
                        tint = Color(0xFF40C4FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Voucher Excel Import",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Sale · Purchase · Payment · Receipt",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // Step 1: Download Template
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Step 1 — Download a sample template",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFF69F0AE)
                )
                Text(
                    text = "Pick the voucher type you want to import. The sample Excel file shows you the exact column format required.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val templates = listOf(
                        Triple(ExcelImportSampleGenerator.VoucherTemplate.SALE, "Sale", Color(0xFF10B981)),
                        Triple(ExcelImportSampleGenerator.VoucherTemplate.PURCHASE, "Purchase", Color(0xFF6366F1)),
                        Triple(ExcelImportSampleGenerator.VoucherTemplate.PAYMENT, "Payment", Color(0xFFF59E0B)),
                        Triple(ExcelImportSampleGenerator.VoucherTemplate.RECEIPT, "Receipt", Color(0xFF06B6D4))
                    )
                    templates.forEach { (tmpl, label, clr) ->
                        OutlinedButton(
                            onClick = { onDownloadTemplate(tmpl) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, clr.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = clr.copy(alpha = 0.08f)
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = clr, modifier = Modifier.size(16.dp))
                                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Step 2: Fill & Import
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Step 2 — Fill & Import",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFF69F0AE)
                )
                Text(
                    text = "Fill in your data following the sample, then tap the button to select your completed Excel file for import.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onPickExcelFile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF40C4FF))
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, tint = Color(0xFF0F182E), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(TerminologyManager.getTerm("Select Excel File to Import"), color = Color(0xFF0F182E), fontWeight = FontWeight.Bold)
                }
            }

            // Single general error banner (if not a list)
            AnimatedVisibility(visible = uiState.importError != null && uiState.importErrors.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEF5350).copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, Color(0xFFEF5350)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.importError ?: "",
                            color = Color(0xFFFFCDD2),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissError) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Ready for Import Preview Card (6.7.25 wt5 card)
            AnimatedVisibility(visible = uiState.isVoucherImportReady) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFF69F0AE).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoShrinkText(
                            text = "Detected: ${uiState.detectedVoucherType?.name ?: "Vouchers"}",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxFontSize = 14.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (uiState.isSelfCorrected) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF40C4FF).copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            AutoShrinkText(
                                text = if (uiState.isSelfCorrected) "Self-corrected & ready" else "Validation passed",
                                color = if (uiState.isSelfCorrected) Color(0xFF69F0AE) else Color(0xFF40C4FF),
                                maxFontSize = 11.sp,
                                minFontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // 1-Click Voucher Type Switcher
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Import as Voucher Type:",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val types = listOf(
                                Pair(VoucherExcelImporter.ImportType.PAYMENT, "Payment"),
                                Pair(VoucherExcelImporter.ImportType.RECEIPT, "Receipt"),
                                Pair(VoucherExcelImporter.ImportType.SALE, "Sales"),
                                Pair(VoucherExcelImporter.ImportType.PURCHASE, "Purchase")
                            )
                            types.forEach { (vType, label) ->
                                val isSelected = uiState.detectedVoucherType == vType
                                val typeColor = when (vType) {
                                    VoucherExcelImporter.ImportType.PAYMENT -> Color(0xFFF59E0B)
                                    VoucherExcelImporter.ImportType.RECEIPT -> Color(0xFF06B6D4)
                                    VoucherExcelImporter.ImportType.SALE -> Color(0xFF10B981)
                                    VoucherExcelImporter.ImportType.PURCHASE -> Color(0xFF6366F1)
                                    else -> Color.White
                                }
                                OutlinedButton(
                                    onClick = { onSwitchType(vType) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) typeColor else Color.White.copy(alpha = 0.2f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSelected) typeColor.copy(alpha = 0.25f) else Color.Transparent
                                    )
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = uiState.voucherImportSummary ?: "",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    // Download corrected file button if self-corrected
                    if (uiState.correctedFileBytes != null) {
                        OutlinedButton(
                            onClick = onDownloadCorrected,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF69F0AE)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF69F0AE).copy(alpha = 0.08f))
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF69F0AE), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(TerminologyManager.getTerm("Download Corrected Excel File (.xlsx)"), color = Color(0xFF69F0AE), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = onConfirmImport,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF0F182E), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(TerminologyManager.getTerm("Confirm & Post Vouchers"), color = Color(0xFF0F182E), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Card detailing import validation errors found in user's sheet.
 */
@Composable
fun ImportErrorsCard(
    errors: List<String>,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C).copy(alpha = 0.2f)),
        border = BorderStroke(1.5.dp, Color(0xFFEF5350)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp))
                    AutoShrinkText(
                        text = "Import Aborted — ${errors.size} Error(s) Found",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFCDD2),
                        maxFontSize = 13.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White)
                }
            }
            Text(
                text = "Fix these issues in your sheet and try again:",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                errors.take(8).forEach { err ->
                    Text("• $err", color = Color(0xFFFFCDD2), fontSize = 11.sp, lineHeight = 14.sp)
                }
                if (errors.size > 8) {
                    AutoShrinkText("... and ${errors.size - 8} more errors", color = Color.Gray, maxFontSize = 10.sp, minFontSize = 7.sp)
                }
            }
        }
    }
}

/**
 * Dialog prompting user to assign category to unknown items so HSN & GST can be automatically resolved.
 */
@Composable
fun MissingCategoryDialog(
    items: List<String>,
    missingColumns: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var categoryText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Smart Auto-Correction",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Teller found items without a category to identify HSN & GST codes. What are these items?",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
                if (items.isNotEmpty()) {
                    Text(
                        text = "Items in your sheet:",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF69F0AE),
                        fontSize = 12.sp
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items.take(4).forEach { item ->
                            AutoShrinkText("• $item", color = Color.White.copy(alpha = 0.85f), maxFontSize = 12.sp)
                        }
                        if (items.size > 4) {
                            AutoShrinkText("... and ${items.size - 4} more", color = Color.Gray, maxFontSize = 11.sp, minFontSize = 8.sp)
                        }
                    }
                }
                Text(
                    text = "App will create the missing ItemCategory column, compute HSN codes & GST rates, and let you import & download the corrected sheet.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = categoryText,
                    onValueChange = { categoryText = it },
                    label = { Text(TerminologyManager.getTerm("Item Category (e.g. Services, Grocery, Auto)")) },
                    placeholder = { Text(TerminologyManager.getTerm("Enter Category Name")) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF69F0AE),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = Color(0xFF69F0AE),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (categoryText.isNotBlank()) {
                        onConfirm(categoryText.trim())
                    }
                },
                enabled = categoryText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69F0AE))
            ) {
                Text(TerminologyManager.getTerm("Apply & Auto-Correct"), color = Color(0xFF0F182E), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(TerminologyManager.getTerm("Cancel"), color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF1E2230),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun SourceTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            AutoShrinkText(title, color = Color.White, fontWeight = FontWeight.Bold, maxFontSize = 14.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
fun VerificationMetric(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AutoShrinkText(label, color = Color.White.copy(alpha = 0.7f), maxFontSize = 13.sp)
        AutoShrinkText(value, color = Color.White, fontWeight = FontWeight.Bold, maxFontSize = 13.sp)
    }
}

@Composable
fun JobActivityRow(job: ImportJob) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                AutoShrinkText(job.type.uppercase().replace("_", " "), color = Color.White, fontWeight = FontWeight.SemiBold, maxFontSize = 13.sp)
                Text(job.summaryJson ?: "No remarks", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                if (job.errorDetails != null) {
                    Text(job.errorDetails, color = Color(0xFFEF9A9A), fontSize = 10.sp)
                }
            }

            val statusColor = when (job.status) {
                "completed" -> Color(0xFF69F0AE)
                "failed" -> Color(0xFFEF9A9A)
                else -> Color(0xFFFFF59D)
            }
            AutoShrinkText(
                text = job.status.uppercase(),
                color = statusColor,
                fontWeight = FontWeight.Bold,
                maxFontSize = 11.sp,
                minFontSize = 8.sp
            )
        }
    }
}
