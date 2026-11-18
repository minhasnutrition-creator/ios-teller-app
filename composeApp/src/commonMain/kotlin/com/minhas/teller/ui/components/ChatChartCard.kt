package com.minhas.teller.ui.components

import com.minhas.teller.utils.TerminologyManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.minhas.teller.utils.AiResponse
import com.minhas.teller.utils.ChartDataset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

// ── Chart color palette (matches app theme) ──
private val chartColors = listOf(
    0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFFFF9800.toInt(),
    0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF00BCD4.toInt(),
    0xFFFF5722.toInt(), 0xFF795548.toInt(), 0xFF607D8B.toInt(),
    0xFF8BC34A.toInt()
)

/**
 * Chat bubble card that renders an AI-generated chart with export buttons.
 * Reuses Canvas drawing patterns from DashboardComponents.kt.
 */
@Composable
fun ChatChartCard(chartData: AiResponse.QueryChart) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp),
        modifier = Modifier
            .widthIn(max = 380.dp)
            .padding(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Chart title
            Text(
                text = chartData.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Render chart based on type
            when (chartData.chartType.lowercase()) {
                "pie" -> PieChartCanvas(chartData.labels, chartData.datasets)
                "line" -> LineChartCanvas(chartData.labels, chartData.datasets)
                else -> BarChartCanvas(chartData.labels, chartData.datasets)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Export button row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // PNG export
                TextButton(
                    onClick = {
                        scope.launch {
                            exportChartAsPng(context, chartData)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Save as PNG", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(TerminologyManager.getTerm("PNG"), fontSize = 11.sp)
                }

                // CSV export
                TextButton(
                    onClick = {
                        scope.launch {
                            exportRawCsv(context, chartData.rawCsv, chartData.title)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = "Save as CSV", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(TerminologyManager.getTerm("CSV"), fontSize = 11.sp)
                }

                // Share
                TextButton(
                    onClick = {
                        scope.launch {
                            shareChartAsPng(context, chartData)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(TerminologyManager.getTerm("Share"), fontSize = 11.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// BAR CHART — Compose Canvas
// ═══════════════════════════════════════════════════════════════
@Composable
fun BarChartCanvas(labels: List<String>, datasets: List<ChartDataset>) {
    if (datasets.isEmpty() || datasets[0].values.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val values = datasets[0].values
        val maxVal = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val chartLeft = 40f
        val chartRight = size.width - 16f
        val chartTop = 16f
        val chartBottom = size.height - 28f
        val totalBars = values.size.coerceAtLeast(1)

        val barAreaWidth = chartRight - chartLeft
        val barWidth = (barAreaWidth / totalBars) * 0.65f
        val gap = (barAreaWidth / totalBars) * 0.35f

        // Y-axis line
        drawLine(Color.Gray.copy(alpha = 0.4f), Offset(chartLeft, chartTop), Offset(chartLeft, chartBottom), strokeWidth = 1.5f)
        // X-axis line
        drawLine(Color.Gray.copy(alpha = 0.4f), Offset(chartLeft, chartBottom), Offset(chartRight, chartBottom), strokeWidth = 1.5f)

        values.forEachIndexed { i, value ->
            val barHeight = ((value / maxVal) * (chartBottom - chartTop - 10f)).toFloat()
            val x = chartLeft + (i * (barWidth + gap)) + gap / 2f
            val color = Color(chartColors[i % chartColors.size])

            // Bar with slight rounded top
            drawRect(
                color = color,
                topLeft = Offset(x, chartBottom - barHeight),
                size = Size(barWidth, barHeight)
            )

            // Value label on top of bar
            drawContext.canvas.nativeCanvas.drawText(
                formatCompact(value),
                x + barWidth / 2f,
                chartBottom - barHeight - 4f,
                Paint().apply {
                    textAlign = Paint.Align.CENTER
                    textSize = 20f
                    this.color = 0xFF666666.toInt()
                    isAntiAlias = true
                }
            )

            // X-axis label
            val fullLabel = labels.getOrElse(i) { "" }
            drawContext.canvas.nativeCanvas.drawText(
                if (fullLabel.length > 8) fullLabel.take(7) + ".." else fullLabel,
                x + barWidth / 2f,
                chartBottom + 16f,
                Paint().apply {
                    textAlign = Paint.Align.CENTER
                    textSize = 18f
                    this.color = 0xFF888888.toInt()
                    isAntiAlias = true
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// PIE CHART — Compose Canvas
// ═══════════════════════════════════════════════════════════════
@Composable
fun PieChartCanvas(labels: List<String>, datasets: List<ChartDataset>) {
    if (datasets.isEmpty() || datasets[0].values.isEmpty()) return

    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val values = datasets[0].values
        val total = values.sum().coerceAtLeast(1.0)
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = minOf(centerX, centerY) - 20f
        var startAngle = -90f

        values.forEachIndexed { i, value ->
            val sweep = ((value / total) * 360).toFloat().coerceAtLeast(0.5f)
            val color = Color(chartColors[i % chartColors.size])

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2)
            )

            // Percentage label on slice
            val midAngle = startAngle + sweep / 2f
            val labelRadius = radius * 0.65f
            val labelX = centerX + (labelRadius * kotlin.math.cos(Math.toRadians(midAngle.toDouble()))).toFloat()
            val labelY = centerY + (labelRadius * kotlin.math.sin(Math.toRadians(midAngle.toDouble()))).toFloat()
            val pct = ((value / total) * 100).toInt()

            if (sweep > 15f) { // Only label slices with enough room
                drawContext.canvas.nativeCanvas.drawText(
                    "$pct%",
                    labelX, labelY + 6f,
                    Paint().apply {
                        textAlign = Paint.Align.CENTER
                        textSize = 20f
                        this.color = 0xFFFFFFFF.toInt()
                        isFakeBoldText = true
                        isAntiAlias = true
                    }
                )
            }

            startAngle += sweep
        }

        // Inner circle for donut effect
        drawCircle(Color.White.copy(alpha = 0.7f), radius * 0.4f, center = Offset(centerX, centerY))
    }

    // Legend below pie
    val values = datasets[0].values
    val total = values.sum().coerceAtLeast(1.0)
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        labels.forEachIndexed { i, label ->
            if (i < values.size) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 1.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(chartColors[i % chartColors.size]), RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${label.take(18)}: ${((values[i] / total) * 100).toInt()}%",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// LINE CHART — Compose Canvas
// ═══════════════════════════════════════════════════════════════
@Composable
fun LineChartCanvas(labels: List<String>, datasets: List<ChartDataset>) {
    if (datasets.isEmpty()) return

    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val chartLeft = 40f
        val chartRight = size.width - 16f
        val chartTop = 20f
        val chartBottom = size.height - 28f

        // Find global max across all datasets
        val allValues = datasets.flatMap { it.values }
        val maxVal = allValues.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

        // Axes
        drawLine(Color.Gray.copy(alpha = 0.4f), Offset(chartLeft, chartTop), Offset(chartLeft, chartBottom), strokeWidth = 1.5f)
        drawLine(Color.Gray.copy(alpha = 0.4f), Offset(chartLeft, chartBottom), Offset(chartRight, chartBottom), strokeWidth = 1.5f)

        datasets.forEachIndexed { si, dataset ->
            val values = dataset.values
            if (values.size < 2) return@forEachIndexed
            val color = Color(chartColors[si % chartColors.size])

            val path = Path()
            values.forEachIndexed { i, value ->
                val x = chartLeft + (i.toFloat() / (values.size - 1).coerceAtLeast(1)) * (chartRight - chartLeft)
                val y = chartBottom - ((value / maxVal) * (chartBottom - chartTop - 10f)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = color, style = Stroke(width = 3f, cap = StrokeCap.Round))

            // Data point dots
            values.forEachIndexed { i, value ->
                val x = chartLeft + (i.toFloat() / (values.size - 1).coerceAtLeast(1)) * (chartRight - chartLeft)
                val y = chartBottom - ((value / maxVal) * (chartBottom - chartTop - 10f)).toFloat()
                drawCircle(color, 4f, Offset(x, y))
                drawCircle(Color.White, 2f, Offset(x, y))
            }
        }

        // X-axis labels
        labels.forEachIndexed { i, label ->
            val x = chartLeft + (i.toFloat() / (labels.size - 1).coerceAtLeast(1)) * (chartRight - chartLeft)
            val shortLabel = if (label.length > 6) label.take(5) + "." else label
            drawContext.canvas.nativeCanvas.drawText(
                shortLabel, x, chartBottom + 16f,
                Paint().apply {
                    textAlign = Paint.Align.CENTER
                    textSize = 18f
                    color = 0xFF888888.toInt()
                    isAntiAlias = true
                }
            )
        }
    }

    // Dataset legend
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        datasets.forEachIndexed { i, ds ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(chartColors[i % chartColors.size]), RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(text = ds.label.take(14), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// DATA TABLE CARD
// ═══════════════════════════════════════════════════════════════
@Composable
fun ChatTableCard(tableData: AiResponse.QueryDataTable) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp),
        modifier = Modifier
            .widthIn(max = 380.dp)
            .padding(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = tableData.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Scrollable table
            val scrollState = rememberScrollState()
            Row(modifier = Modifier.horizontalScroll(scrollState)) {
                Column {
                    // Header row
                    Row(modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))) {
                        tableData.columns.forEach { col ->
                            Box(modifier = Modifier.width(100.dp).padding(6.dp)) {
                                Text(
                                    text = col.take(12),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    // Data rows
                    tableData.rows.forEachIndexed { rowIdx, row ->
                        Row(
                            modifier = Modifier.background(
                                if (rowIdx % 2 == 0) Color.Transparent
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                            )
                        ) {
                            tableData.columns.indices.forEach { colIdx ->
                                Box(modifier = Modifier.width(100.dp).padding(6.dp)) {
                                    Text(
                                        text = row.getOrElse(colIdx) { "" }.take(15),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Export buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = {
                        scope.launch {
                            exportRawCsv(context, tableData.rawCsv, tableData.title)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = "CSV", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(TerminologyManager.getTerm("CSV"), fontSize = 11.sp)
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            shareRawCsv(context, tableData.rawCsv, tableData.title)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(TerminologyManager.getTerm("Share"), fontSize = 11.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// EXPORT HELPERS
// ═══════════════════════════════════════════════════════════════

private fun formatCompact(value: Double): String = when {
    value >= 10000000 -> String.format("%.1fCr", value / 10000000)
    value >= 100000 -> String.format("%.1fL", value / 100000)
    value >= 1000 -> String.format("%.1fK", value / 1000)
    else -> String.format("%.0f", value)
}

private suspend fun exportChartAsPng(context: Context, chartData: AiResponse.QueryChart) {
    withContext(Dispatchers.Default) {
        try {
            val width = 800
            val height = 500
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)

            val titlePaint = Paint().apply {
                textSize = 36f
                typeface = Typeface.DEFAULT_BOLD
                color = 0xFF333333.toInt()
                isAntiAlias = true
            }
            canvas.drawText(chartData.title, 40f, 50f, titlePaint)

            // Draw a note that this is a simplified bitmap export
            val notePaint = Paint().apply {
                textSize = 22f
                color = 0xFF999999.toInt()
                isAntiAlias = true
            }
            canvas.drawText("Chart: ${chartData.chartType} • ${chartData.labels.size} data points", 40f, 90f, notePaint)

            // Draw a basic representation of the data
            val dataPaint = Paint().apply {
                textSize = 24f
                color = 0xFF555555.toInt()
                isAntiAlias = true
            }
            val datasets = chartData.datasets
            if (datasets.isNotEmpty()) {
                val values = datasets[0].values
                val maxVal = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                val barAreaLeft = 100f
                val barAreaRight = width - 40f
                val barAreaTop = 130f
                val barAreaBottom = height - 70f
                val barCount = values.size

                // Draw axes
                val axisPaint = Paint().apply {
                    color = 0xFFCCCCCC.toInt()
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                }
                canvas.drawLine(barAreaLeft, barAreaTop, barAreaLeft, barAreaBottom, axisPaint)
                canvas.drawLine(barAreaLeft, barAreaBottom, barAreaRight, barAreaBottom, axisPaint)

                // Draw bars
                val totalBarWidth = (barAreaRight - barAreaLeft) / barCount
                val barWidth = totalBarWidth * 0.6f
                val barPaint = Paint().apply { isAntiAlias = true }

                values.forEachIndexed { i, value ->
                    val barHeight = ((value / maxVal) * (barAreaBottom - barAreaTop - 10f)).toFloat()
                    val x = barAreaLeft + (i * totalBarWidth) + (totalBarWidth - barWidth) / 2f
                    barPaint.color = chartColors[i % chartColors.size]
                    canvas.drawRoundRect(RectF(x, barAreaBottom - barHeight, x + barWidth, barAreaBottom), 8f, 8f, barPaint)

                    // Value label
                    canvas.drawText(formatCompact(value), x + barWidth / 2f, barAreaBottom - barHeight - 8f,
                        Paint().apply { textAlign = Paint.Align.CENTER; textSize = 22f; color = 0xFF666666.toInt(); isAntiAlias = true }
                    )

                    // X label
                    val label = chartData.labels.getOrElse(i) { "" }
                    canvas.drawText(if (label.length > 10) label.take(9) + "." else label, x + barWidth / 2f, barAreaBottom + 28f,
                        Paint().apply { textAlign = Paint.Align.CENTER; textSize = 20f; color = 0xFF999999.toInt(); isAntiAlias = true }
                    )
                }
            }

            // Save to cache dir and share
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(context.cacheDir, "chart_${dateStr}.png")
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            bitmap.recycle()

            shareFile(context, file, "image/png", "Chart: ${chartData.title}")
        } catch (e: Exception) {
            Log.e("ChatChart", "PNG export failed", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private suspend fun shareChartAsPng(context: Context, chartData: AiResponse.QueryChart) {
    // Same as export but uses share intent directly
    exportChartAsPng(context, chartData)
}

private suspend fun exportRawCsv(context: Context, rawCsv: String, title: String) {
    withContext(Dispatchers.Default) {
        try {
            val safeName = title.replace(Regex("[^a-zA-Z0-9 ]"), "").take(30).trim().replace(" ", "_")
            val file = File(context.cacheDir, "${safeName}_${System.currentTimeMillis()}.csv")
            FileWriter(file).use { it.write(rawCsv) }
            shareFile(context, file, "text/csv", "Data: $title")
        } catch (e: Exception) {
            Log.e("ChatChart", "CSV export failed", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "CSV export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private suspend fun shareRawCsv(context: Context, rawCsv: String, title: String) {
    exportRawCsv(context, rawCsv, title)
}

private fun shareFile(context: Context, file: File, mimeType: String, title: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share $title"))
    } catch (e: Exception) {
        Log.e("ChatChart", "Share failed", e)
    }
}
