package com.minhas.teller.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.minhas.teller.R
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.theme.*
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.utils.TerminologyManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.SessionManager
import java.util.Calendar
import java.util.Locale

private data class ReportItemDef(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val iconColor: Color,
    val badge: String? = null,
    @param:DrawableRes val drawableRes: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(navController: NavController, currentAppTheme: AppTheme) {
    val currentLanguage by TerminologyManager.currentLanguage.collectAsState()
    val lang = currentLanguage.toString()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = TerminologyManager.getTerm("Reports"),
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentAppTheme) {
                AppTheme.CYBER_FINANCE -> ReportsScreenCyber(navController, lang)
                AppTheme.BENTO         -> ReportsScreenBento(navController, lang)
                AppTheme.CRYSTAL       -> ReportsScreenCrystal(navController, lang)
                AppTheme.MODERN_LIGHT  -> ReportsScreenDefault(navController, lang, isLight = true)
                else                   -> ReportsScreenDefault(navController, lang)
            }
        }
    }
}

// ── Shared item builder ────────────────────────────────────────────────────────
private fun buildReportItems(lang: String): List<ReportItemDef> {
    val guidedFilingRoute = run {
        val id = SessionManager.currentCompanyId.value
        if (id != null) {
            val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
            val fp = String.format(Locale.US, "%02d%04d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
            Screen.GstFilingWizard.createRoute(id, fp)
        } else {
            Screen.GstReturns.route
        }
    }
    return listOf(
    ReportItemDef(TerminologyManager.getTerm("Daybook"),                       Icons.Default.DateRange,              Screen.Daybook.route,             Color(0xFF64B5F6), drawableRes = R.drawable.ic_accounting_book),
    ReportItemDef(TerminologyManager.getTerm("Pending Payments"),              Icons.Default.CreditCard,             Screen.PendingPaymentsMenu.route, Color(0xFFFFD54F), drawableRes = R.drawable.ic_bill),
    ReportItemDef(TerminologyManager.getTerm("Purchase Report"),               Icons.Default.ShoppingCart,           Screen.PurchaseRegister.route,    Color(0xFFB0BEC5), drawableRes = R.drawable.ic_accounting),
    ReportItemDef(TerminologyManager.getTerm("Sales Report"),                  Icons.Default.Sell,                   Screen.SalesRegister.route,       Color(0xFFFFD54F), drawableRes = R.drawable.ic_accounting_software),
    ReportItemDef(TerminologyManager.getTerm("Profit & Loss A/c"), Icons.AutoMirrored.Filled.TrendingUp, Screen.ProfitAndLossReport.route, Color(0xFF4DB6AC), drawableRes = R.drawable.ic_bar_chart),
    ReportItemDef(TerminologyManager.getTerm("Item Profitability"),            Icons.Default.BarChart,               Screen.ItemProfitability.route,   Color(0xFFFFB74D), drawableRes = R.drawable.ic_bar_chart),
    ReportItemDef(TerminologyManager.getTerm("Trial Balance"), Icons.Default.AccountBalance, Screen.TrialBalanceReport.route, Color(0xFFB0BEC5), drawableRes = R.drawable.ic_calculator),
    ReportItemDef(TerminologyManager.getTerm("GST Summary"), Icons.Default.Percent, Screen.GstReport.route, Color(0xFF26A69A), drawableRes = R.drawable.ic_calculator),
    ReportItemDef(TerminologyManager.getTerm("GST Returns"), Icons.Default.AssignmentTurnedIn, guidedFilingRoute, Color(0xFF69F0AE), badge = "EASY"),
    ReportItemDef(TerminologyManager.getTerm("Show Accounts"),                 Icons.Default.ManageAccounts,         Screen.LedgerList.route,          Color(0xFFFFD54F), drawableRes = R.drawable.ic_accounting_app),
    ReportItemDef(TerminologyManager.getTerm("Balance Sheet"), Icons.Default.AccountBalanceWallet, Screen.BalanceSheetReport.route, Color(0xFF90CAF9), drawableRes = R.drawable.ic_bank_building),
    ReportItemDef(TerminologyManager.getTerm("Bank Reconciliation"), Icons.Default.AccountBalance, Screen.BankReconciliation.route, Color(0xFF00B0FF), badge = "AUTO", drawableRes = R.drawable.ic_bank_building),
    ReportItemDef(TerminologyManager.getTerm("Stock Summary"), Icons.Default.Inventory,  Screen.StockSummaryReport.route,  Color(0xFF81C784), drawableRes = R.drawable.ic_abacus),
    ReportItemDef(TerminologyManager.getTerm("Import & Cleanup"),              Icons.Default.CloudUpload,            Screen.ImportCleanup.route,       Color(0xFF69F0AE), badge = "NEW"),
    ReportItemDef(TerminologyManager.getTerm("CA & Scrutiny"),                 Icons.Default.Gavel,                  Screen.CaScrutiny.route,          Color(0xFF40C4FF), badge = "CA"),
    ReportItemDef(TerminologyManager.getTerm("Teller Score"),                  Icons.Default.Star,                   Screen.TellerScore.route,         Color(0xFFFFD700), badge = "HEALTH")
    )
}

// ── DEFAULT / MODERN_LIGHT theme ──────────────────────────────────────────────
@Composable
private fun ReportsScreenDefault(navController: NavController, lang: String, isLight: Boolean = false) {
    val items = buildReportItems(lang)

    Box(modifier = Modifier.fillMaxSize().background(if (isLight) Color(0xFFF2F5FA) else Color(0xFF060D40))) {
        // Ambient amber glow behind the center (dark mode only)
        if (!isLight) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFB8860B).copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.44f),
                        radius = size.width * 0.72f
                    )
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.route }) { item ->
                DefaultReportCard(item = item, isLight = isLight, onClick = { navController.navigate(item.route) })
            }
        }
    }
}

@Composable
private fun DefaultReportCard(item: ReportItemDef, isLight: Boolean = false, onClick: () -> Unit) {
    val cardGradient = if (isLight) listOf(Color.White, Color(0xFFE6EDF7)) else listOf(Color(0xFF0E1C60), Color(0xFF08124A))
    val titleColor = if (isLight) Color(0xFF0A1440) else Color.White
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color(0xFF2040A0).copy(alpha = if (isLight) 0.2f else 0.6f),
                spotColor   = Color(0xFF2040A0).copy(alpha = if (isLight) 0.2f else 0.6f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(cardGradient)
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    listOf(Color(0xFF8090D0).copy(alpha = if (isLight) 0.5f else 0.7f), Color(0xFF2A3880).copy(alpha = 0.4f), Color(0xFF8090D0).copy(alpha = if (isLight) 0.5f else 0.7f))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            // Large custom icon with optional badge
            Box(contentAlignment = Alignment.TopEnd) {
                if (item.drawableRes != null) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Transparent)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(item.drawableRes),
                            contentDescription = item.title,
                            modifier = Modifier.size(68.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = item.iconColor,
                        modifier = Modifier.size(72.dp)
                    )
                }
                if (item.badge != null) {
                    Box(
                        modifier = Modifier
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.badge,
                            color = Color(0xFF0A1440),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = item.title,
                color = titleColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                maxLines = 2
            )
        }
    }
}

// ── CYBER FINANCE theme ───────────────────────────────────────────────────────
@Composable
private fun ReportsScreenCyber(navController: NavController, lang: String) {
    val items = buildReportItems(lang)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.route }) { item ->
            val neonColor = when (item.route) {
                Screen.Daybook.route              -> GlobalNeonCyan
                Screen.PendingPaymentsMenu.route  -> GlobalNeonYellow
                Screen.PurchaseRegister.route     -> GlobalNeonBlue
                Screen.SalesRegister.route        -> GlobalNeonGreen
                Screen.ProfitAndLossReport.route  -> GlobalNeonPink
                Screen.ItemProfitability.route    -> GlobalNeonOrange
                Screen.TrialBalanceReport.route   -> GlobalNeonCyan
                Screen.LedgerList.route           -> GlobalNeonMagenta
                Screen.BalanceSheetReport.route   -> GlobalNeonBlue
                Screen.StockSummaryReport.route   -> GlobalNeonGreen
                Screen.TellerScore.route          -> GlobalNeonYellow
                else                              -> GlobalNeonMagenta
            }
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlobalCharcoal)
                    .border(2.dp, neonColor, RoundedCornerShape(12.dp))
                    .clickable { navController.navigate(item.route) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (item.drawableRes != null) {
                        Image(
                            painter = painterResource(item.drawableRes),
                            contentDescription = item.title,
                            modifier = Modifier.size(56.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(item.icon, item.title, tint = neonColor, modifier = Modifier.size(44.dp))
                    }
                    var titleFontSize by remember(item.title) { mutableStateOf(14.sp) }
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = titleFontSize,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        onTextLayout = { textLayoutResult ->
                            if (textLayoutResult.didOverflowWidth && titleFontSize > 8.sp) {
                                titleFontSize = (titleFontSize.value - 0.5f).sp
                            }
                        }
                    )
                }
            }
        }
    }
}

// ── BENTO theme ───────────────────────────────────────────────────────────────
@Composable
private fun ReportsScreenBento(navController: NavController, lang: String) {
    val items = buildReportItems(lang)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.route }) { item ->
            val cardColor = when (item.route) {
                Screen.Daybook.route             -> GlobalPaleBlue
                Screen.PendingPaymentsMenu.route -> GlobalPaleYellow
                Screen.PurchaseRegister.route    -> GlobalMintGreen
                Screen.SalesRegister.route       -> GlobalPastelOrange
                Screen.ProfitAndLossReport.route -> GlobalSalmonRed
                Screen.ItemProfitability.route   -> GlobalPaleYellow
                Screen.TrialBalanceReport.route  -> GlobalPaleBlue
                Screen.LedgerList.route          -> GlobalLavender
                Screen.TellerScore.route         -> GlobalPaleYellowB
                else                             -> GlobalLavender
            }
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardColor)
                    .clickable { navController.navigate(item.route) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (item.drawableRes != null) {
                        Image(
                            painter = painterResource(item.drawableRes),
                            contentDescription = item.title,
                            modifier = Modifier.size(56.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(item.icon, item.title, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(44.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(item.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ── CRYSTAL theme ─────────────────────────────────────────────────────────────
@Composable
private fun ReportsScreenCrystal(navController: NavController, lang: String) {
    val items = buildReportItems(lang)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.route }) { item ->
            val tintColor = when (item.route) {
                Screen.Daybook.route -> Color(0xFF2196F3) // Blue
                Screen.PendingPaymentsMenu.route -> Color(0xFFFFEB3B) // Yellow
                Screen.PurchaseRegister.route -> Color(0xFFF44336) // Red
                Screen.SalesRegister.route -> Color(0xFF4CAF50) // Green
                Screen.ProfitAndLossReport.route -> Color(0xFF00BCD4) // Cyan
                Screen.ItemProfitability.route -> Color(0xFFFF9800) // Orange
                Screen.TrialBalanceReport.route -> Color(0xFF9C27B0) // Purple
                Screen.LedgerList.route -> Color(0xFFE91E63) // Pink
                Screen.BalanceSheetReport.route -> Color(0xFFE040FB) // Magenta
                Screen.StockSummaryReport.route -> Color(0xFF009688) // Teal
                Screen.TellerScore.route -> Color(0xFFFFD700) // Gold
                else -> Color.White
            }

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tintColor.copy(alpha = 0.15f))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.4f),
                                tintColor.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { navController.navigate(item.route) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (item.drawableRes != null) {
                        Image(
                            painter = painterResource(item.drawableRes),
                            contentDescription = item.title,
                            modifier = Modifier.size(56.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(item.icon, item.title, tint = tintColor, modifier = Modifier.size(44.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ── Legacy compat (used by DashboardScreen tile cards) ────────────────────────
@Composable
fun ThemeAwareReportsActionCard(item: ModernDashboardAction, navController: NavController, currentAppTheme: AppTheme, onClick: () -> Unit = {}) {
    val tintColor = remember(item.route) {
        when {
            item.route.contains("Debtors", ignoreCase = true) -> Color(0xFF2196F3) // Blue
            item.route.contains("Creditors", ignoreCase = true) -> Color(0xFFE91E63) // Pink/Red
            item.route.contains("Finance", ignoreCase = true) -> Color(0xFFFFD54F) // Yellow
            else -> Color(0xFF00E5FF) // Cyan
        }
    }

    when (currentAppTheme) {
        AppTheme.CYBER_FINANCE -> {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlobalCharcoal)
                    .border(2.dp, tintColor, RoundedCornerShape(12.dp))
                    .clickable { navController.navigate(item.route) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(item.icon, item.title, tint = tintColor, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        AppTheme.BENTO -> {
            val cardColor = when {
                item.route.contains("Debtors", ignoreCase = true) -> GlobalPaleBlue
                item.route.contains("Creditors", ignoreCase = true) -> GlobalSalmonRed
                item.route.contains("Finance", ignoreCase = true) -> GlobalPaleYellow
                else -> GlobalMintGreen
            }
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardColor)
                    .clickable { navController.navigate(item.route) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(item.icon, item.title, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(item.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }
        AppTheme.CRYSTAL -> {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tintColor.copy(alpha = 0.15f))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.4f),
                                tintColor.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { navController.navigate(item.route) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(item.icon, item.title, tint = tintColor, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }
        else -> {
            val itemDef = ReportItemDef(item.title, item.icon, item.route, tintColor)
            DefaultReportCard(itemDef, onClick = { navController.navigate(item.route) })
        }
    }
}

@Composable
fun ModernActionCard(item: ModernDashboardAction, navController: NavController, onClick: () -> Unit = {}) {
    val itemDef = ReportItemDef(item.title, item.icon, item.route, Color(0xFF64B5F6))
    DefaultReportCard(itemDef, onClick = { navController.navigate(item.route) })
}
