package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.utils.DashboardPrefs
import com.minhas.teller.utils.TaxConfigManager
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.ui.viewmodels.TellerScoreViewModel
import com.minhas.teller.ui.viewmodels.TellerScoreViewModelFactory
import com.minhas.teller.utils.ExportUtils
import java.util.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.HorizontalDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TellerScoreScreen(navController: NavHostController) {
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
    
    val factory = remember { TellerScoreViewModelFactory(voucherRepository, ledgerRepository) }
    val viewModel: TellerScoreViewModel = viewModel(factory = factory)
    
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()
    val scoreData by viewModel.scoreData.collectAsState()
    
    val countryName = remember(context) { DashboardPrefs.getTaxCountry(context) }
    val countryConfig = remember(countryName) { TaxConfigManager.getConfigForCountry(countryName) }
    val taxLabel = countryConfig.taxName
    val taxEntriesLabel = if (countryName.equals("India", true)) "CGST/SGST/IGST" else taxLabel

    val companyRepository = remember { CompanyRepository(database.companyDao()) }
    val company by produceState<Company?>(initialValue = null, currentCompanyId) {
        currentCompanyId?.let { id ->
            companyRepository.getCompany(id).collect { value = it }
        }
    }
    
    var showLoanDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentCompanyId) {
        currentCompanyId?.let { viewModel.loadScore(it) }
    }

    val animatedScore = remember { Animatable(0f) }
    LaunchedEffect(scoreData.isLoading) {
        if (!scoreData.isLoading) {
            animatedScore.animateTo(
                targetValue = scoreData.overallScore.toFloat(),
                animationSpec = tween(durationMillis = 1500)
            )
        }
    }

    val scoreColor = when {
        scoreData.overallScore >= 800 -> Color(0xFF00E676) // Green
        scoreData.overallScore >= 650 -> Color(0xFFFFB300) // Amber
        else -> Color(0xFFFF5252) // Red
    }

    val scoreGrade = when {
        scoreData.overallScore >= 800 -> "EXCELLENT"
        scoreData.overallScore >= 650 -> "GOOD"
        scoreData.overallScore >= 500 -> "AVERAGE"
        else -> "NEEDS ATTENTION"
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = "Teller Health Score",
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController
            )
        }
    ) { paddingValues ->
        if (scoreData.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Score Meter
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
                            text = "BUSINESS HEALTH IDENTITY",
                            maxFontSize = 11.sp,
                            minFontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB8A0D0),
                            letterSpacing = 1.sp
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Box(
                            modifier = Modifier.size(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Ambient backdrop glow matching score status
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(scoreColor.copy(alpha = 0.18f), Color.Transparent),
                                        center = center,
                                        radius = size.width / 2f
                                    ),
                                    radius = size.width / 2f
                                )
                                // Outer dotted ring for high-tech instrumentation look
                                val dashPathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 12f), 0f)
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.12f),
                                    radius = size.width / 2.1f,
                                    style = Stroke(width = 1.dp.toPx(), pathEffect = dashPathEffect)
                                )
                                // Background Arc
                                drawArc(
                                    color = Color.White.copy(alpha = 0.08f),
                                    startAngle = 135f,
                                    sweepAngle = 270f,
                                    useCenter = false,
                                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                                )
                                // Active Score Arc Glow shadow
                                drawArc(
                                    color = scoreColor.copy(alpha = 0.25f),
                                    startAngle = 135f,
                                    sweepAngle = (animatedScore.value / 1000f) * 270f,
                                    useCenter = false,
                                    style = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round)
                                )
                                // Active Score Arc
                                drawArc(
                                    color = scoreColor,
                                    startAngle = 135f,
                                    sweepAngle = (animatedScore.value / 1000f) * 270f,
                                    useCenter = false,
                                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AutoShrinkText(
                                    text = animatedScore.value.toInt().toString(),
                                    maxFontSize = 42.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = "/ 1000",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                AutoShrinkText(
                                    text = scoreGrade,
                                    maxFontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scoreColor
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "This score is computed in real-time entirely on your device using cash flows, margins, collections, and debt discipline.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }

                // Call to Action Buttons (Export/Share + Loan Apply)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showLoanDialog = true },
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(TerminologyManager.getTerm("Get Loan Capital"), fontWeight = FontWeight.Bold)
                        }
                        
                        OutlinedButton(
                            onClick = {
                                ExportUtils.exportFinancialHealthPassport(context, scoreData, company)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(TerminologyManager.getTerm("Export PDF"), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Score Breakdown Components
                item {
                    AutoShrinkText(
                        text = "DETAILED SCORE BREAKDOWN",
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB8A0D0),
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
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
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ScoreComponentRow(
                            name = "Cash Flow Health (20%)",
                            score = scoreData.cashFlowScore,
                            description = "Measures liquid cash availability relative to monthly operating expenses."
                        )
                        ScoreComponentRow(
                            name = "Collection Efficiency (20%)",
                            score = scoreData.collectionScore,
                            description = "Rate of customer dues collection (receipt entries vs total sales invoices)."
                        )
                        ScoreComponentRow(
                            name = "Profit Margin Stability (20%)",
                            score = scoreData.profitMarginScore,
                            description = "Gross profit margin percentage across all operations and sales."
                        )
                        ScoreComponentRow(
                            name = "Debt-to-Income Discipline (15%)",
                            score = scoreData.debtRatioScore,
                            description = "Outstanding supplier payables relative to current monthly sales capacity."
                        )
                        ScoreComponentRow(
                            name = "Payment Reliability (10%)",
                            score = scoreData.paymentDisciplineScore,
                            description = "History of clearing payables on-time. Fewer overdue supplier bills translates to a higher score."
                        )
                        ScoreComponentRow(
                            name = "Audit Compliance (15%)",
                            score = scoreData.auditScore,
                            description = "Pre-audit verification status of ledger bookkeeping, tax allocation, and compliance rules."
                        )
                    }
                }

                // Bank Underwriting Ratios
                item {
                    AutoShrinkText(
                        text = "BANK UNDERWRITING RATIOS",
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB8A0D0),
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BankRatioRow(
                            label = "Current Ratio (Liquidity)",
                            value = String.format(Locale.US, "%.2f", scoreData.currentRatio),
                            target = "> 1.33 : 1",
                            status = if (scoreData.currentRatio >= 1.33) "Compliant" else "Under Benchmark",
                            pass = scoreData.currentRatio >= 1.33
                        )
                        BankRatioRow(
                            label = "Quick Ratio (Acid Test)",
                            value = String.format(Locale.US, "%.2f", scoreData.quickRatio),
                            target = "> 1.00 : 1",
                            status = if (scoreData.quickRatio >= 1.0) "Compliant" else "Under Benchmark",
                            pass = scoreData.quickRatio >= 1.0
                        )
                        BankRatioRow(
                            label = "Debt Service Coverage (DSCR)",
                            value = String.format(Locale.US, "%.2f", scoreData.dscr),
                            target = "> 1.50 : 1",
                            status = if (scoreData.dscr >= 1.5) "Compliant" else "Low Coverage",
                            pass = scoreData.dscr >= 1.5
                        )
                        BankRatioRow(
                            label = "Debt-to-Revenue Ratio",
                            value = String.format(Locale.US, "%.2f", scoreData.debtToRevenueRatio),
                            target = "< 0.30 : 1",
                            status = if (scoreData.debtToRevenueRatio <= 0.3) "Compliant" else "Highly Leveraged",
                            pass = scoreData.debtToRevenueRatio <= 0.3
                        )
                        BankRatioRow(
                            label = "Collection Efficiency",
                            value = String.format(Locale.US, "%.1f%%", scoreData.collectionEfficiency),
                            target = "> 85.0%",
                            status = if (scoreData.collectionEfficiency >= 85.0) "Compliant" else "Needs Improvement",
                            pass = scoreData.collectionEfficiency >= 85.0
                        )
                    }
                }

                // AI CA Compliance Pre-Audit
                item {
                    AutoShrinkText(
                        text = "AI COMPLIANCE PRE-AUDIT STATUS",
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB8A0D0),
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
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
                        AuditCheckRow(
                            checkName = "Negative Cash Ledger Evaluation",
                            status = if (scoreData.negativeCashDeductions == 0) "PASSED" else "WARNING",
                            pass = scoreData.negativeCashDeductions == 0,
                            message = if (scoreData.negativeCashDeductions == 0) 
                                "All cash balances are positive. Payment sequences are correct." 
                                else "Negative cash balances detected. Unrecorded payments or errors present."
                        )
                        AuditCheckRow(
                            checkName = "ITA Section 40A(3) Cash Limits",
                            status = if (scoreData.cashLimitDisallowances == 0) "COMPLIANT" else "WARNING",
                            pass = scoreData.cashLimitDisallowances == 0,
                            message = if (scoreData.cashLimitDisallowances == 0) 
                                "No single-day cash payments exceed INR 10,000 limit." 
                                else "Found ${scoreData.cashLimitDisallowances} payment(s) exceeding INR 10,000 in cash."
                        )
                        AuditCheckRow(
                            checkName = "Sales $taxLabel Tax Mappings",
                            status = if (scoreData.missingTaxInvoicesCount == 0) "VERIFIED" else "WARNING",
                            pass = scoreData.missingTaxInvoicesCount == 0,
                            message = if (scoreData.missingTaxInvoicesCount == 0) 
                                "All sales vouchers have appropriate tax ledgers allocated." 
                                else "${scoreData.missingTaxInvoicesCount} sales voucher(s) missing $taxEntriesLabel entries."
                        )
                        AuditCheckRow(
                            checkName = "Double-Entry Balance Verification",
                            status = if (scoreData.unbalancedVouchersCount == 0) "BALANCED" else "WARNING",
                            pass = scoreData.unbalancedVouchersCount == 0,
                            message = if (scoreData.unbalancedVouchersCount == 0) 
                                "Debits equal credits across all vouchers." 
                                else "${scoreData.unbalancedVouchersCount} voucher(s) are unbalanced."
                        )
                    }
                }

                // Loan Eligibility Box
                item {
                    AutoShrinkText(
                        text = "BANK CAPABILITY RECOMMENDATION",
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB8A0D0),
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                item {
                    val capEligibilityVal = Math.min(5000000.0, Math.max(100000.0, scoreData.totalSalesAllTime * 0.20))
                    val capEligibilityStr = "₹ ${String.format(Locale.US, "%,.2f", capEligibilityVal)}"
                    val riskCat = when {
                        scoreData.overallScore >= 800 -> "CLASS-A (Low Risk)"
                        scoreData.overallScore >= 650 -> "CLASS-B (Moderate Risk)"
                        else -> "CLASS-C (Review Required)"
                    }
                    val detailNote = when {
                        scoreData.overallScore >= 800 -> "Excellent creditworthiness profile. Recommended for instant working capital overdraft limits with minimal collateral."
                        scoreData.overallScore >= 650 -> "Good credit standing. Eligible for standard term loans under MSME credit schemes."
                        else -> "High payables ratio or late collection alerts. Banker approval may require secondary trade guarantees or collateral backing."
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF311B92).copy(alpha = 0.6f), Color(0xFF1A237E).copy(alpha = 0.6f))
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        AutoShrinkText(TerminologyManager.getTerm("ESTIMATED WORKING CAPITAL LIMIT ELIGIBILITY"), color = Color.White.copy(alpha = 0.6f), maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold)
                        AutoShrinkText(capEligibilityStr, color = Color(0xFF00E676), maxFontSize = 22.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            AutoShrinkText(TerminologyManager.getTerm("Risk Category:"), color = Color.White.copy(alpha = 0.5f), maxFontSize = 12.sp)
                            AutoShrinkText(riskCat, color = scoreColor, fontWeight = FontWeight.Bold, maxFontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(detailNote, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }

                // Action Plan Checklist
                item {
                    AutoShrinkText(
                        text = "SCORE BOOSTING ACTIONS PLAN",
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB8A0D0),
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF2D1B4E).copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        var recommendationsCount = 0
                        
                        if (scoreData.negativeCashDeductions > 0) {
                            ActionPlanItem("Clear Negative Cash account by verifying opening balance or missing receipts.", "+45 pts")
                            recommendationsCount++
                        }
                        if (scoreData.cashLimitDisallowances > 0) {
                            ActionPlanItem("Modify payments exceeding ₹10,000 in cash to Bank/Online transfer.", "+30 pts")
                            recommendationsCount++
                        }
                        if (scoreData.totalPayables > scoreData.totalSalesAllTime * 0.30) {
                            ActionPlanItem("Pay off outstanding supplier dues to lower your Debt-to-Revenue ratio.", "+25 pts")
                            recommendationsCount++
                        }
                        if (scoreData.collectionEfficiency < 85.0) {
                            ActionPlanItem("Enable automatic WhatsApp payment reminders for Sundry Debtors.", "+35 pts")
                            recommendationsCount++
                        }
                        if (scoreData.missingTaxInvoicesCount > 0) {
                            ActionPlanItem("Add $taxEntriesLabel ledger accounts to missing Sales vouchers.", "+20 pts")
                            recommendationsCount++
                        }
                        if (scoreData.unbalancedVouchersCount > 0) {
                            ActionPlanItem("Verify and balance voucher entries that have debit-credit mismatches.", "+25 pts")
                            recommendationsCount++
                        }
                        if (scoreData.missingNarrationCount > 3) {
                            ActionPlanItem("Add narration explanations to all transaction vouchers for cleaner audit trail.", "+15 pts")
                            recommendationsCount++
                        }
                        
                        if (recommendationsCount == 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Color(0xFF00E676))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(TerminologyManager.getTerm("Your credit and audit hygiene are outstanding! Keep maintaining positive ledger balances."), color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Bank Verification Checklist (CFO Dashboard)
                item {
                    AutoShrinkText(
                        text = "BANK VERIFICATION SUMMARY",
                        maxFontSize = 11.sp,
                        minFontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB8A0D0),
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VerificationStatRow("All-Time Sales Revenue", "₹ ${String.format(Locale.US, "%,.2f", scoreData.totalSalesAllTime)}")
                        VerificationStatRow("All-Time Purchases", "₹ ${String.format(Locale.US, "%,.2f", scoreData.totalPurchaseAllTime)}")
                        VerificationStatRow("Net Liquid Assets", "₹ ${String.format(Locale.US, "%,.2f", scoreData.cashInHand + scoreData.bankBalance)}")
                        VerificationStatRow("Avg. Outstanding Collection Days", "${scoreData.avgCollectionDays} days")
                        VerificationStatRow("Sundry Debtors (Receivables)", "₹ ${String.format(Locale.US, "%,.2f", scoreData.totalReceivables)}")
                        VerificationStatRow("Sundry Creditors (Payables)", "₹ ${String.format(Locale.US, "%,.2f", scoreData.totalPayables)}")
                    }
                }
            }
        }
    }

    if (showLoanDialog) {
        AlertDialog(
            onDismissRequest = { showLoanDialog = false },
            title = { Text(TerminologyManager.getTerm("Instant Working Capital"), color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Get direct loan offers from top MSME lending partners based on your Teller Score.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "• No physical paperwork required\n• Processing within 4 hours\n• Special interest rate discounts for Teller Scores > 700",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLoanDialog = false
                        Toast.makeText(context, "Connecting to NBFC partner APIs...", Toast.LENGTH_LONG).show()
                    }
                ) {
                    Text(TerminologyManager.getTerm("Apply Now"), color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoanDialog = false }) {
                    Text(TerminologyManager.getTerm("Cancel"), color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF2A1B4E)
        )
    }
}

@Composable
fun ScoreComponentRow(name: String, score: Int, description: String) {
    val color = when {
        score >= 80 -> Color(0xFF00E676)
        score >= 60 -> Color(0xFFFFB300)
        else -> Color(0xFFFF5252)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutoShrinkText(name, color = Color.White, fontWeight = FontWeight.Bold, maxFontSize = 13.sp)
            AutoShrinkText("$score / 100", color = color, fontWeight = FontWeight.Bold, maxFontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(score / 100f)
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(description, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, lineHeight = 14.sp)
    }
}

@Composable
fun BankRatioRow(label: String, value: String, target: String, status: String, pass: Boolean) {
    val statusColor = if (pass) Color(0xFF00E676) else Color(0xFFFF5252)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutoShrinkText(label, color = Color.White, fontWeight = FontWeight.Bold, maxFontSize = 13.sp)
            AutoShrinkText(value, color = Color.White, fontWeight = FontWeight.Black, maxFontSize = 14.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutoShrinkText("Target benchmark: $target", color = Color.White.copy(alpha = 0.5f), maxFontSize = 11.sp, minFontSize = 8.sp)
            AutoShrinkText(status, color = statusColor, fontWeight = FontWeight.SemiBold, maxFontSize = 11.sp, minFontSize = 8.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
    }
}

@Composable
fun AuditCheckRow(checkName: String, status: String, pass: Boolean, message: String) {
    val color = if (pass) Color(0xFF00E676) else Color(0xFFFF5252)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutoShrinkText(checkName, color = Color.White, fontWeight = FontWeight.Bold, maxFontSize = 13.sp)
            AutoShrinkText(status, color = color, fontWeight = FontWeight.Black, maxFontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(message, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, lineHeight = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
    }
}

@Composable
fun ActionPlanItem(actionText: String, impact: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFFE040FB))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(actionText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            AutoShrinkText("Est. Score Impact: $impact", color = Color(0xFF00E676), maxFontSize = 10.sp, minFontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VerificationStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutoShrinkText(label, color = Color.White.copy(alpha = 0.7f), maxFontSize = 13.sp)
        AutoShrinkText(value, color = Color.White, fontWeight = FontWeight.SemiBold, maxFontSize = 13.sp)
    }
}

private fun getComponentRating(score: Int): String {
    return when {
        score >= 80 -> "Excellent"
        score >= 60 -> "Good"
        score >= 40 -> "Average"
        else -> "Critical"
    }
}


