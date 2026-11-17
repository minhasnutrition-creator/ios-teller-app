package com.minhas.teller.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
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
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.navigation.Screen
import com.minhas.teller.utils.DashboardPrefs
import com.minhas.teller.utils.TaxConfigManager
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.ui.viewmodels.AiAuditorViewModel
import com.minhas.teller.ui.viewmodels.AiAuditorViewModelFactory
import com.minhas.teller.ui.viewmodels.Severity
import androidx.compose.material.icons.automirrored.filled.ArrowForward

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAuditorScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    
    val voucherRepository = remember {
        VoucherRepository(
            database.voucherDao(),
            database.voucherEntryDao(),
            database.inventoryEntryDao(),
            database.billAllocationDao(),
            database.auditLogDao(),
            database.ledgerDao(),
            db = database
        )
    }
    val ledgerRepository = remember { LedgerRepository(database.ledgerDao()) }
    
    val factory = remember { AiAuditorViewModelFactory(voucherRepository, ledgerRepository) }
    val viewModel: AiAuditorViewModel = viewModel(factory = factory)
    
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val auditState by viewModel.auditState.collectAsState()

    val currentCompany by produceState<Company?>(initialValue = null, currentCompanyId) {
        currentCompanyId?.let { id ->
            database.companyDao().getCompanyById(id).collect { value = it }
        }
    }
    val currencySymbol = currentCompany?.currencySymbol ?: "₹"
    val countryName = remember(context) { DashboardPrefs.getTaxCountry(context) }

    LaunchedEffect(currentCompanyId, countryName, currencySymbol) {
        currentCompanyId?.let { viewModel.runAudit(it, countryName, currencySymbol) }
    }

    // Animation for radar scanner
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val radarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarRotation"
    )
    val radarScale by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarScale"
    )
    val radarAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAlpha"
    )

    val scoreColor = when {
        auditState.auditScore >= 90 -> Color(0xFF00E676)
        auditState.auditScore >= 70 -> Color(0xFFFFB300)
        else -> Color(0xFFFF5252)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = TerminologyManager.getTerm("AI CA Audit Bot"),
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (auditState.isAuditing) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Core center glow
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFE040FB).copy(alpha = 0.22f), Color.Transparent),
                                    center = center,
                                    radius = size.width / 2f
                                )
                            )
                            // Outer dash ring
                            val dashPathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 20f), 0f)
                            drawCircle(
                                color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                radius = size.minDimension / 2.2f,
                                style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashPathEffect)
                            )
                            // Concentric solid rings
                            drawCircle(
                                color = Color(0xFFE040FB).copy(alpha = 0.12f),
                                radius = size.minDimension / 3.5f,
                                style = Stroke(width = 1.dp.toPx())
                            )
                            drawCircle(
                                color = Color(0xFF00E5FF).copy(alpha = 0.08f),
                                radius = size.minDimension / 1.8f,
                                style = Stroke(width = 1.dp.toPx())
                            )
                            // Expanding radar waves
                            drawCircle(
                                color = Color(0xFF00E5FF).copy(alpha = radarAlpha),
                                radius = size.minDimension / 2 * radarScale,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            // Sweeping scan arm
                            rotate(radarRotation) {
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            Color(0xFFE040FB).copy(alpha = 0.45f),
                                            Color(0xFFE040FB).copy(alpha = 0.1f),
                                            Color.Transparent
                                        )
                                    ),
                                    startAngle = 0f,
                                    sweepAngle = 45f,
                                    useCenter = true
                                )
                                drawLine(
                                    color = Color(0xFFE040FB).copy(alpha = 0.7f),
                                    start = center,
                                    end = Offset(center.x + size.width / 2f, center.y),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                        
                        // Scanner line rotation or center icon
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    val taxConfig = remember(countryName) { TaxConfigManager.getConfigForCountry(countryName) }
                    val taxLabel = taxConfig.taxName
                    AutoShrinkText(
                        text = TerminologyManager.getTerm("Analyzing all voucher ledgers..."),
                        maxFontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = TerminologyManager.getTerm("Verifying Section 40A(3), negative cash flow, missing narration and tax $taxLabel errors"),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Audit Score Hero Header
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF2A1B4E).copy(alpha = 0.8f),
                                            Color(0xFF150D2A).copy(alpha = 0.95f)
                                        )
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AutoShrinkText(
                                text = TerminologyManager.getTerm("LEDGER COMPLIANCE STATUS"),
                                maxFontSize = 11.sp,
                                minFontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB8A0D0),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    AutoShrinkText(
                                        text = "${auditState.auditScore}%",
                                        maxFontSize = 44.sp,
                                        fontWeight = FontWeight.Black,
                                        color = scoreColor
                                    )
                                    AutoShrinkText(
                                        text = TerminologyManager.getTerm("Compliance Score"),
                                        maxFontSize = 11.sp,
                                        minFontSize = 8.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(50.dp)
                                        .background(Color.White.copy(alpha = 0.1f))
                                )

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    AutoShrinkText(
                                        text = auditState.findings.size.toString(),
                                        maxFontSize = 44.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (auditState.findings.isEmpty()) Color(0xFF00E676) else Color(0xFFFF5252)
                                    )
                                    AutoShrinkText(
                                        text = TerminologyManager.getTerm("Issues Detected"),
                                        maxFontSize = 11.sp,
                                        minFontSize = 8.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = TerminologyManager.getTerm("Your AI Auditor scanned ${auditState.checkedCount} vouchers & ledgers. Clear issues to improve your compliance and secure easier bank financing."),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }

                    // 90-Day Predictive Cash Runway Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B233D)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF38BDF8))
                                        AutoShrinkText(TerminologyManager.getTerm("90-DAY CASH RUNWAY FORECAST"), color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, maxFontSize = 11.sp, minFontSize = 8.sp, letterSpacing = 1.sp)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (auditState.runway.runwayDays >= 60) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f)
                                    ) {
                                        AutoShrinkText(
                                            text = "${auditState.runway.runwayDays} Days Runway",
                                            color = if (auditState.runway.runwayDays >= 60) Color(0xFF10B981) else Color(0xFFEF4444),
                                            fontWeight = FontWeight.Bold,
                                            maxFontSize = 11.sp,
                                            minFontSize = 8.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AutoShrinkText(TerminologyManager.getTerm("+30 Days"), maxFontSize = 11.sp, minFontSize = 8.sp, color = Color.White.copy(alpha = 0.6f))
                                        AutoShrinkText("$currencySymbol${String.format(java.util.Locale.US, "%,.0f", auditState.runway.projectedCash30d)}", fontWeight = FontWeight.Bold, color = Color.White, maxFontSize = 14.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AutoShrinkText(TerminologyManager.getTerm("+60 Days"), maxFontSize = 11.sp, minFontSize = 8.sp, color = Color.White.copy(alpha = 0.6f))
                                        AutoShrinkText("$currencySymbol${String.format(java.util.Locale.US, "%,.0f", auditState.runway.projectedCash60d)}", fontWeight = FontWeight.Bold, color = Color.White, maxFontSize = 14.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AutoShrinkText(TerminologyManager.getTerm("+90 Days"), maxFontSize = 11.sp, minFontSize = 8.sp, color = Color.White.copy(alpha = 0.6f))
                                        AutoShrinkText("$currencySymbol${String.format(java.util.Locale.US, "%,.0f", auditState.runway.projectedCash90d)}", fontWeight = FontWeight.Bold, color = Color.White, maxFontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Checklist Status Section
                    item {
                        AutoShrinkText(
                            text = TerminologyManager.getTerm("COMPLIANCE CHECKLIST"),
                            maxFontSize = 11.sp,
                            minFontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB8A0D0),
                            letterSpacing = 0.8.sp
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF2D1B4E).copy(alpha = 0.5f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val taxConfig = remember(countryName) { TaxConfigManager.getConfigForCountry(countryName) }
                            val taxLabel = taxConfig.taxName
                            ChecklistItem(TerminologyManager.getTerm("Negative Cash Balances"), auditState.findings.none { it.type == com.minhas.teller.ui.viewmodels.FindingType.NEGATIVE_CASH })
                            ChecklistItem(TerminologyManager.getTerm("Daily Running Negative Cash Sentinel"), auditState.findings.none { it.type == com.minhas.teller.ui.viewmodels.FindingType.DAILY_NEGATIVE_CASH })
                            ChecklistItem(TerminologyManager.getTerm("Section 40A(3) Cash Limits"), auditState.findings.none { it.type == com.minhas.teller.ui.viewmodels.FindingType.CASH_LIMIT_EXCEEDED })
                            ChecklistItem(TerminologyManager.getTerm("Section 269ST (₹2L Cash Receipts)"), auditState.findings.none { it.type == com.minhas.teller.ui.viewmodels.FindingType.SECTION_269ST_VIOLATION })
                            ChecklistItem(TerminologyManager.getTerm("Duplicate Bill / Invoice Number"), auditState.findings.none { it.type == com.minhas.teller.ui.viewmodels.FindingType.DUPLICATE_INVOICE })
                            ChecklistItem(TerminologyManager.getTerm("Sales Voucher $taxLabel/Tax Allocation"), auditState.findings.none { it.type == com.minhas.teller.ui.viewmodels.FindingType.MISSING_TAX })
                            ChecklistItem(TerminologyManager.getTerm("Debit/Credit Accounting Balances"), auditState.findings.none { it.type == com.minhas.teller.ui.viewmodels.FindingType.UNBALANCED_ENTRY })
                            ChecklistItem(TerminologyManager.getTerm("Transaction Explanation (Narration)"), auditState.findings.none { it.type == com.minhas.teller.ui.viewmodels.FindingType.MISSING_NARRATION })
                        }
                    }

                    // Audit Findings list
                    if (auditState.findings.isNotEmpty()) {
                        item {
                            AutoShrinkText(
                                text = TerminologyManager.getTerm("DETAILED FINDINGS & CORRECTIONS"),
                                maxFontSize = 11.sp,
                                minFontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB8A0D0),
                                letterSpacing = 0.8.sp
                            )
                        }
                        
                        items(auditState.findings) { finding ->
                            FindingCard(
                                finding = finding,
                                onAction = {
                                    if (finding.voucherId != null) {
                                        navController.navigate(Screen.AddEditVoucher.route + "?voucherId=${finding.voucherId}")
                                    } else {
                                        navController.navigate(Screen.LedgerList.route)
                                    }
                                }
                            )
                        }
                    } else {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                AutoShrinkText(
                                    text = TerminologyManager.getTerm("All audits passed!"),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxFontSize = 16.sp
                                )
                                Text(
                                    text = TerminologyManager.getTerm("Your ledgers are fully compliant with standard CA guidelines."),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChecklistItem(label: String, passed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (passed) Color(0xFF00E676) else Color(0xFFFF5252),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        AutoShrinkText(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            maxFontSize = 13.sp
        )
    }
}

@Composable
fun FindingCard(
    finding: com.minhas.teller.ui.viewmodels.AuditFinding,
    onAction: () -> Unit
) {
    val severityColor = when (finding.severity) {
        Severity.HIGH -> Color(0xFFFF5252)
        Severity.MEDIUM -> Color(0xFFFFB300)
        Severity.LOW -> Color(0xFF40C4FF)
    }
    
    val severityLabel = when (finding.severity) {
        Severity.HIGH -> TerminologyManager.getTerm("CA Critical Warning")
        Severity.MEDIUM -> TerminologyManager.getTerm("IT Tax Compliance Warning")
        Severity.LOW -> TerminologyManager.getTerm("Audit Standard Insight")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B4E).copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutoShrinkText(
                    text = severityLabel.uppercase(),
                    color = severityColor,
                    maxFontSize = 9.sp,
                    minFontSize = 6.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                
                Icon(
                    imageVector = when (finding.severity) {
                        Severity.HIGH -> Icons.Default.Error
                        Severity.MEDIUM -> Icons.Default.Warning
                        Severity.LOW -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = severityColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            AutoShrinkText(finding.title, color = Color.White, fontWeight = FontWeight.Bold, maxFontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(finding.message, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp)
            
            if (finding.actionLabel != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onAction,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00E676))
                    ) {
                        Text(finding.actionLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}
