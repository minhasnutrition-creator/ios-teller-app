package com.minhas.teller.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhas.teller.utils.DashboardPrefs
import com.minhas.teller.utils.TerminologyManager

// ─────────────────────────────────────────────────────────────────
// Data classes for overview card
// ─────────────────────────────────────────────────────────────────

data class OverviewCardData(
    val mode: DashboardPrefs.OverviewMode = DashboardPrefs.OverviewMode.SALES_VS_PURCHASE,
    // Current month
    val sales: Double = 0.0,
    val purchase: Double = 0.0,
    val receipt: Double = 0.0,
    val payment: Double = 0.0,
    val receivables: Double = 0.0,
    val payables: Double = 0.0,
    val cash: Double = 0.0,
    val bank: Double = 0.0,
    // Last month
    val lastMonthSales: Double = 0.0,
    val lastMonthPurchase: Double = 0.0,
    val lastMonthReceipt: Double = 0.0,
    val lastMonthPayment: Double = 0.0,
    // Last year same month
    val lastYearSales: Double = 0.0,
    val lastYearPurchase: Double = 0.0,
    // Actions
    val onPrintInvoicesClick: () -> Unit = {},
    val onPendingPaymentsClick: () -> Unit = {},
    val currencySymbol: String = "₹",
    // Last 6 months of sales (oldest → current), used by the sparkline
    val salesTrend: List<Float> = emptyList()
)

// ─────────────────────────────────────────────────────────────────
// Mode picker dialog
// ─────────────────────────────────────────────────────────────────

@Composable
fun OverviewModePickerDialog(
    currentMode: DashboardPrefs.OverviewMode,
    onModeSelected: (DashboardPrefs.OverviewMode) -> Unit,
    onDismiss: () -> Unit
) {
    val modes = listOf(
        ModeOption(DashboardPrefs.OverviewMode.SALES_VS_PURCHASE, Icons.Default.BarChart, "Sales & Purchases", "Current month revenue vs costs"),
        ModeOption(DashboardPrefs.OverviewMode.MONTH_OVER_MONTH, Icons.AutoMirrored.Filled.TrendingUp, "Month-over-Month", "Compare with previous month"),
        ModeOption(DashboardPrefs.OverviewMode.YEAR_OVER_YEAR, Icons.Default.CalendarToday, "Year-over-Year", "Vs same month last year"),
        ModeOption(DashboardPrefs.OverviewMode.CASH_IN_VS_OUT, Icons.Default.SwapHoriz, "Cash In vs Out", "Receipts against payments"),
        ModeOption(DashboardPrefs.OverviewMode.MONEY_FLOW, Icons.Default.AccountBalanceWallet, "Money Flow", "To collect vs to pay")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(TerminologyManager.getTerm("Dashboard View"), fontWeight = FontWeight.Black, color = Color.White, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { opt ->
                    val isSelected = currentMode == opt.mode
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onModeSelected(opt.mode) },
                        color = if (isSelected) Color(0xFF3F51B5).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF3F51B5).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = opt.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF8C9EFF) else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                AutoShrinkText(
                                    opt.title,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxFontSize = 14.sp
                                )
                                Text(
                                    opt.description,
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.sp
                                )
                            }
                            if (isSelected) {
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF8C9EFF), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(TerminologyManager.getTerm("Close"), color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(20.dp)
    )
}

private data class ModeOption(
    val mode: DashboardPrefs.OverviewMode,
    val icon: ImageVector,
    val title: String,
    val description: String
)

// ─────────────────────────────────────────────────────────────────
// Main Overview Card Content — renders based on selected mode
// ─────────────────────────────────────────────────────────────────

@Composable
fun OverviewCardContent(
    data: OverviewCardData,
    modifier: Modifier = Modifier
) {
    when (data.mode) {
        DashboardPrefs.OverviewMode.SALES_VS_PURCHASE -> SalesVsPurchaseView(data, modifier)
        DashboardPrefs.OverviewMode.MONTH_OVER_MONTH -> MonthOverMonthView(data, modifier)
        DashboardPrefs.OverviewMode.YEAR_OVER_YEAR -> YearOverYearView(data, modifier)
        DashboardPrefs.OverviewMode.CASH_IN_VS_OUT -> CashInVsOutView(data, modifier)
        DashboardPrefs.OverviewMode.MONEY_FLOW -> MoneyFlowView(data, modifier)
    }
}

// ─────────────────────────────────────────────────────────────────
// Helper: percentage change text with color
// ─────────────────────────────────────────────────────────────────

@Composable
private fun TrendIndicator(current: Double, previous: Double, fontSize: Int = 12) {
    if (previous <= 0.0) return
    val pct = ((current - previous) / previous * 100)
    val isUp = pct >= 0
    val color = if (isUp) Color(0xFF4CAF50) else Color(0xFFEF5350)
    val arrow = if (isUp) "↑" else "↓"
    AutoShrinkText(
        text = "$arrow ${"%.1f".format(kotlin.math.abs(pct))}%",
        maxFontSize = fontSize.sp,
        minFontSize = (fontSize * 0.7f).sp,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

// ─────────────────────────────────────────────────────────────────
// Helper: formatted currency
// ─────────────────────────────────────────────────────────────────

private fun fmt(amount: Double, symbol: String): String {
    return if (amount >= 1_00_00_000) "$symbol${"%.2f".format(amount / 1_00_00_000)} Cr"
    else if (amount >= 1_00_000) "$symbol${"%.2f".format(amount / 1_00_000)} L"
    else "$symbol${"%,.0f".format(amount)}"
}

@Composable
private fun StatIconBadge(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.4f), color.copy(alpha = 0.1f)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun DoughnutChart(
    firstValue: Double,
    secondValue: Double,
    firstColor: Color,
    secondColor: Color,
    modifier: Modifier = Modifier
) {
    val total = (firstValue + secondValue).toFloat().coerceAtLeast(1f)
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.24f
        val inset = stroke / 2f
        val arcTopLeft = Offset(inset, inset)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val firstSweep = ((firstValue.toFloat() / total) * 360f).coerceIn(0f, 360f)
        val depth = 2.5.dp.toPx()

        // Track
        drawArc(
            color = Color.White.copy(alpha = 0.08f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = arcTopLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Depth shadows (offset down for a 3D lift)
        drawArc(
            color = firstColor.copy(alpha = 0.35f),
            startAngle = -90f, sweepAngle = firstSweep, useCenter = false,
            topLeft = arcTopLeft + Offset(0f, depth), size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        drawArc(
            color = secondColor.copy(alpha = 0.35f),
            startAngle = -90f + firstSweep, sweepAngle = 360f - firstSweep, useCenter = false,
            topLeft = arcTopLeft + Offset(0f, depth), size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        // Main segments
        drawArc(
            color = firstColor,
            startAngle = -90f, sweepAngle = firstSweep, useCenter = false,
            topLeft = arcTopLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        drawArc(
            color = secondColor,
            startAngle = -90f + firstSweep, sweepAngle = 360f - firstSweep, useCenter = false,
            topLeft = arcTopLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
    }
}

@Composable
private fun Sparkline(points: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val max = points.maxOrNull()?.coerceAtLeast(0.001f) ?: 1f
        val min = points.minOrNull() ?: 0f
        val range = (max - min).coerceAtLeast(0.001f)
        val stepX = size.width / (points.size - 1)
        val padY = 3.dp.toPx()
        val pts = points.mapIndexed { i, v ->
            Offset(i * stepX, size.height - padY - ((v - min) / range) * (size.height - 2 * padY))
        }

        val fillPath = Path().apply {
            moveTo(pts.first().x, size.height)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height)
            close()
        }
        drawPath(fillPath, Brush.verticalGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent)))
        for (i in 0 until pts.size - 1) {
            drawLine(color, pts[i], pts[i + 1], strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        pts.forEach { drawCircle(color, radius = 3.5.dp.toPx(), center = it) }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f))))
        )
        Spacer(Modifier.width(9.dp))
        AutoShrinkText(label, color = Color.White.copy(alpha = 0.72f), maxFontSize = 12.sp, modifier = Modifier.weight(1f))
        AutoShrinkText(value, color = Color.White, fontWeight = FontWeight.Black, maxFontSize = 14.sp)
    }
}

private fun DrawScope.drawCylinder(base: Offset, height: Float, width: Float, topColor: Color, bottomColor: Color) {
    val topY = base.y - height
    val corner = width * 0.4f
    // base shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.25f),
        topLeft = Offset(base.x - 2.dp.toPx(), base.y - 3.dp.toPx()),
        size = Size(width + 4.dp.toPx(), 7.dp.toPx())
    )
    // body
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(topColor, bottomColor)),
        topLeft = Offset(base.x, topY),
        size = Size(width, height),
        cornerRadius = CornerRadius(corner, corner)
    )
    // top highlight
    drawOval(
        color = topColor.copy(alpha = 0.85f),
        topLeft = Offset(base.x, topY - 2.dp.toPx()),
        size = Size(width, 5.dp.toPx())
    )
}

// ─────────────────────────────────────────────────────────────────
// MODE 1: Sales vs Purchases (default, matches current behavior)
// ─────────────────────────────────────────────────────────────────

@Composable
private fun SalesVsPurchaseView(data: OverviewCardData, modifier: Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Left: 3D cylinder bar chart
        Box(modifier = Modifier.weight(0.42f).fillMaxHeight().padding(end = 6.dp), contentAlignment = Alignment.Center) {
            val maxVal = maxOf(data.sales, data.purchase, 1.0)
            Canvas(modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)) {
                val barW = size.width * 0.30f
                val gap = barW * 0.95f
                val h = size.height
                val salesH = (data.sales / maxVal * h * 0.84f).toFloat().coerceAtLeast(8f)
                val purchaseH = (data.purchase / maxVal * h * 0.84f).toFloat().coerceAtLeast(8f)
                drawCylinder(Offset(gap, h), purchaseH, barW, Color(0xFF40C4FF), Color(0xFF0D47A1))
                drawCylinder(Offset(0f, h), salesH, barW, Color(0xFF69F0AE), Color(0xFF1B5E20))
            }
        }
        // Right: stats
        Column(modifier = Modifier.weight(0.58f), verticalArrangement = Arrangement.SpaceEvenly) {
            MiniStatRow("Sales", fmt(data.sales, data.currencySymbol), Color(0xFF69F0AE), Icons.AutoMirrored.Filled.TrendingUp)
            MiniStatRow("Purchases", fmt(data.purchase, data.currencySymbol), Color(0xFF40C4FF), Icons.Default.ShoppingCart)
            MiniStatRow("Receipts", fmt(data.receipt, data.currencySymbol), Color(0xFFFFAB91), Icons.AutoMirrored.Filled.CallReceived)
            Spacer(Modifier.height(3.dp))
            HeroActionChipsRow(
                onPrintInvoicesClick = data.onPrintInvoicesClick,
                onPendingPaymentsClick = data.onPendingPaymentsClick
            )
        }
    }
}

@Composable
private fun MiniStatRow(label: String, value: String, color: Color, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        StatIconBadge(icon, color)
        Spacer(Modifier.width(10.dp))
        AutoShrinkText(label, color = Color.White.copy(alpha = 0.6f), maxFontSize = 12.sp, modifier = Modifier.weight(1f))
        AutoShrinkText(value, color = Color.White, fontWeight = FontWeight.Black, maxFontSize = 16.sp)
    }
}

@Composable
private fun HeroActionChipsRow(
    onPrintInvoicesClick: () -> Unit,
    onPendingPaymentsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Button 1: Invoices (Print)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(29.dp)
                .clip(RoundedCornerShape(14.5.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF40C4FF).copy(alpha = 0.22f),
                            Color(0xFF00B0FF).copy(alpha = 0.10f)
                        )
                    )
                )
                .border(
                    BorderStroke(0.7.dp, Color(0xFF40C4FF).copy(alpha = 0.35f)),
                    RoundedCornerShape(14.5.dp)
                )
                .clickable(onClick = onPrintInvoicesClick),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = "Print Invoices",
                    tint = Color(0xFF80D8FF),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                AutoShrinkText(
                    text = "Invoices",
                    color = Color.White.copy(alpha = 0.92f),
                    maxFontSize = 10.5.sp,
                    minFontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Button 2: Pending Payments
        Box(
            modifier = Modifier
                .weight(1f)
                .height(29.dp)
                .clip(RoundedCornerShape(14.5.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFFD54F).copy(alpha = 0.22f),
                            Color(0xFFFFB300).copy(alpha = 0.10f)
                        )
                    )
                )
                .border(
                    BorderStroke(0.7.dp, Color(0xFFFFD54F).copy(alpha = 0.35f)),
                    RoundedCornerShape(14.5.dp)
                )
                .clickable(onClick = onPendingPaymentsClick),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = "Pending Payments",
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                AutoShrinkText(
                    text = "Pending",
                    color = Color.White.copy(alpha = 0.92f),
                    maxFontSize = 10.5.sp,
                    minFontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// MODE 2: Month-over-Month
// ─────────────────────────────────────────────────────────────────

@Composable
private fun MonthOverMonthView(data: OverviewCardData, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        MoMComparisonRow("Sales", data.sales, data.lastMonthSales, data.currencySymbol, Color(0xFF69F0AE), Icons.AutoMirrored.Filled.TrendingUp)
        MoMComparisonRow("Purchases", data.purchase, data.lastMonthPurchase, data.currencySymbol, Color(0xFF40C4FF), Icons.Default.ShoppingCart)
        MoMComparisonRow("Receipts", data.receipt, data.lastMonthReceipt, data.currencySymbol, Color(0xFFFFAB91), Icons.AutoMirrored.Filled.CallReceived)
        MoMComparisonRow("Payments", data.payment, data.lastMonthPayment, data.currencySymbol, Color(0xFFCE93D8), Icons.Default.CreditCard)
    }
}

@Composable
private fun MoMComparisonRow(label: String, current: Double, previous: Double, symbol: String, color: Color, icon: ImageVector) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        StatIconBadge(icon, color)
        Spacer(Modifier.width(10.dp))
        AutoShrinkText(label, color = Color.White.copy(alpha = 0.6f), maxFontSize = 12.sp, modifier = Modifier.width(56.dp))
        AutoShrinkText(fmt(current, symbol), color = Color.White, fontWeight = FontWeight.Black, maxFontSize = 15.sp, modifier = Modifier.width(76.dp))
        TrendIndicator(current, previous, 11)
        Spacer(Modifier.weight(1f))
        // Mini progress bar
        val maxV = maxOf(current, previous, 1.0)
        val ratio = (current / maxV).toFloat().coerceIn(0f, 1f)
        Box(modifier = Modifier.width(48.dp).height(7.dp).clip(RoundedCornerShape(3.5.dp)).background(Color.White.copy(alpha = 0.1f))) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(ratio).clip(RoundedCornerShape(3.5.dp)).background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.45f)))))
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// MODE 3: Year-over-Year
// ─────────────────────────────────────────────────────────────────

@Composable
private fun YearOverYearView(data: OverviewCardData, modifier: Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Left: sales trend sparkline
        Column(modifier = Modifier.weight(0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
            AutoShrinkText(TerminologyManager.getTerm("SALES TREND"), maxFontSize = 9.sp, minFontSize = 6.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.5f), letterSpacing = 0.5.sp)
            Spacer(Modifier.height(4.dp))
            Sparkline(
                points = if (data.salesTrend.size >= 2) data.salesTrend else listOf(data.lastYearSales.toFloat(), data.lastMonthSales.toFloat(), data.sales.toFloat()),
                color = Color(0xFF69F0AE),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        // Right: rows
        Column(modifier = Modifier.weight(0.6f), verticalArrangement = Arrangement.SpaceEvenly) {
            YoYRow("Sales", data.sales, data.lastYearSales, data.currencySymbol, Color(0xFF69F0AE))
            Spacer(Modifier.height(4.dp))
            YoYRow("Purchase", data.purchase, data.lastYearPurchase, data.currencySymbol, Color(0xFF40C4FF))
            Spacer(Modifier.height(4.dp))
            YoYRow("Net", data.sales - data.purchase, data.lastYearSales - data.lastYearPurchase, data.currencySymbol, Color(0xFFFFD54F))
        }
    }
}

@Composable
private fun YoYRow(label: String, thisYear: Double, lastYear: Double, symbol: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AutoShrinkText(label, color = Color.White.copy(alpha = 0.6f), maxFontSize = 11.sp, minFontSize = 8.sp, modifier = Modifier.width(68.dp))
        AutoShrinkText(fmt(thisYear, symbol), color = Color.White, fontWeight = FontWeight.Black, maxFontSize = 16.sp, modifier = Modifier.width(78.dp))
        TrendIndicator(thisYear, lastYear, 11)
    }
}

// ─────────────────────────────────────────────────────────────────
// MODE 4: Cash In vs Out
// ─────────────────────────────────────────────────────────────────

@Composable
private fun CashInVsOutView(data: OverviewCardData, modifier: Modifier) {
    val netCash = data.receipt - data.payment
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                DoughnutChart(
                    firstValue = data.receipt, secondValue = data.payment,
                    firstColor = Color(0xFF4CAF50), secondColor = Color(0xFFEF5350),
                    modifier = Modifier.fillMaxSize()
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AutoShrinkText(if (netCash >= 0) "IN" else "OUT", color = Color.White.copy(alpha = 0.65f), maxFontSize = 9.sp, minFontSize = 6.sp, fontWeight = FontWeight.Black)
                    AutoShrinkText(fmt(netCash, data.currencySymbol), color = if (netCash >= 0) Color(0xFF4CAF50) else Color(0xFFEF5350), fontWeight = FontWeight.Black, maxFontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                LegendRow(Color(0xFF4CAF50), "Money In", fmt(data.receipt, data.currencySymbol))
                LegendRow(Color(0xFFEF5350), "Money Out", fmt(data.payment, data.currencySymbol))
                LegendRow(Color(0xFFFFD54F), "Net Flow", fmt(netCash, data.currencySymbol))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// MODE 5: Money Flow (Receivables vs Payables)
// ─────────────────────────────────────────────────────────────────

@Composable
private fun MoneyFlowView(data: OverviewCardData, modifier: Modifier) {
    val netPosition = data.receivables - data.payables
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                DoughnutChart(
                    firstValue = data.receivables, secondValue = data.payables,
                    firstColor = Color(0xFF4CAF50), secondColor = Color(0xFFEF5350),
                    modifier = Modifier.fillMaxSize()
                )
                AutoShrinkText(fmt(netPosition, data.currencySymbol), color = if (netPosition >= 0) Color(0xFF4CAF50) else Color(0xFFEF5350), fontWeight = FontWeight.Black, maxFontSize = 11.sp, minFontSize = 8.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                LegendRow(Color(0xFF4CAF50), "To Collect", fmt(data.receivables, data.currencySymbol))
                LegendRow(Color(0xFFEF5350), "To Pay", fmt(data.payables, data.currencySymbol))
                LegendRow(Color(0xFFFFD54F), "Net Position", fmt(netPosition, data.currencySymbol))
            }
        }
    }
}
