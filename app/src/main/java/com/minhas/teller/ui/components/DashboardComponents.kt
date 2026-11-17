package com.minhas.teller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.theme.*
import com.minhas.teller.utils.DashboardPrefs
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.ui.screens.ModernDashboardAction
import com.minhas.teller.ui.viewmodels.AttentionItem
import com.minhas.teller.ui.viewmodels.AttentionSeverity
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

@Composable
fun CustomizeDashboardDialog(
    currentSelection: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    val options = DashboardPrefs.ALL_METRICS.toList()
    val selection = remember { mutableStateListOf(*currentSelection.toTypedArray()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TerminologyManager.getTerm("Customize Dashboard")) },
        text = {
            LazyColumn {
                items(options) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selection.contains(option)) selection.remove(option)
                                else selection.add(option)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selection.contains(option),
                            onCheckedChange = { 
                                if (selection.contains(option)) selection.remove(option)
                                else selection.add(option)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(TerminologyManager.getTerm(option))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selection.toSet()) }) {
                Text(TerminologyManager.getTerm("Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(TerminologyManager.getTerm("Cancel"))
            }
        }
    )
}

// ==========================================
// BESPOKE CANVAS ILLUSTRATIONS FOR MAIN CARDS
// ==========================================

@Composable
fun ReceiptGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.1f)
            val segments = 8
            val segmentW = (w * 0.7f) / segments
            for (i in 1..segments) {
                val x = w * 0.15f + i * segmentW
                val y = if (i % 2 == 0) h * 0.1f else h * 0.13f
                lineTo(x, y)
            }
            lineTo(w * 0.85f, h * 0.85f)
            for (i in segments downTo 0) {
                val x = w * 0.15f + i * segmentW
                val y = if (i % 2 == 0) h * 0.85f else h * 0.82f
                lineTo(x, y)
            }
            lineTo(w * 0.15f, h * 0.1f)
            close()
        }
        
        // Softened cyan-blue theme colors
        val fillColor = Color(0xFF00E5FF).copy(alpha = 0.05f)
        val outlineColor = Color(0xFFE0F7FA).copy(alpha = 0.5f)
        val lineCol = Color(0xFF80DEEA).copy(alpha = 0.4f)
        
        drawPath(path = path, color = fillColor)
        drawPath(path = path, color = outlineColor, style = Stroke(width = 2.2.dp.toPx()))
        
        val lineStart = w * 0.26f
        val lineEndMax = w * 0.74f
        val lineYStart = h * 0.25f
        val lineSpacing = h * 0.08f
        val lineCount = 5
        
        for (i in 0 until lineCount) {
            val y = lineYStart + i * lineSpacing
            val endX = if (i == 4) w * 0.52f else lineStart + (lineEndMax - lineStart) * (0.6f + (i % 3) * 0.15f)
            drawLine(
                color = lineCol,
                start = Offset(lineStart, y),
                end = Offset(endX, y),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        
        val badgeRadius = w * 0.16f
        val badgeCx = w * 0.78f
        val badgeCy = h * 0.76f
        
        drawCircle(
            color = Color.White.copy(alpha = 0.15f),
            radius = badgeRadius,
            center = Offset(badgeCx, badgeCy)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = badgeRadius,
            center = Offset(badgeCx, badgeCy),
            style = Stroke(width = 2.2.dp.toPx())
        )
        
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = badgeRadius * 1.1f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER
            alpha = (0.65f * 255).toInt()
        }
        
        drawContext.canvas.nativeCanvas.drawText(
            "₹",
            badgeCx,
            badgeCy - (paint.descent() + paint.ascent()) / 2,
            paint
        )
    }
}

@Composable
fun ClipboardGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        val boardLeft = w * 0.18f
        val boardTop = h * 0.08f
        val boardW = w * 0.65f
        val boardH = h * 0.78f
        
        val fillColor = Color(0xFFFF8A80).copy(alpha = 0.05f)
        val outlineColor = Color(0xFFFFEBEE).copy(alpha = 0.5f)
        val lineCol = Color(0xFFFFCDD2).copy(alpha = 0.4f)
        val clipCol = Color(0xFFFF8A80).copy(alpha = 0.4f)
        
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(boardLeft, boardTop),
            size = Size(boardW, boardH),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(boardLeft, boardTop),
            size = Size(boardW, boardH),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            style = Stroke(width = 2.2.dp.toPx())
        )
        
        val clipPath = Path().apply {
            moveTo(w * 0.42f, h * 0.08f)
            lineTo(w * 0.58f, h * 0.08f)
            lineTo(w * 0.55f, h * 0.14f)
            lineTo(w * 0.45f, h * 0.14f)
            close()
        }
        drawPath(path = clipPath, color = clipCol)
        drawPath(path = clipPath, color = outlineColor, style = Stroke(width = 1.5.dp.toPx()))
        
        val lineStart = w * 0.26f
        val lineEndMax = w * 0.7f
        val lineYStart = h * 0.22f
        val lineSpacing = h * 0.07f
        
        for (i in 0 until 5) {
            val y = lineYStart + i * lineSpacing
            val boxSize = w * 0.05f
            val boxX = w * 0.25f
            
            drawRoundRect(
                color = lineCol,
                topLeft = Offset(boxX, y - boxSize / 2),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                style = Stroke(width = 1.8.dp.toPx())
            )
            
            val endX = lineStart + (lineEndMax - lineStart) * (0.6f + (i % 2) * 0.2f)
            drawLine(
                color = lineCol,
                start = Offset(lineStart + boxSize + 6.dp.toPx(), y),
                end = Offset(endX, y),
                strokeWidth = 2.0.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        
        // Stack of overlapping banknotes at bottom right
        val noteW = w * 0.45f
        val noteH = h * 0.24f
        
        val noteColors = listOf(
            Color(0xFFFFD54F).copy(alpha = 0.08f),
            Color(0xFFFFD54F).copy(alpha = 0.15f),
            Color(0xFFFFD54F).copy(alpha = 0.25f)
        )
        val borderColors = listOf(
            Color(0xFFFFE082).copy(alpha = 0.35f),
            Color(0xFFFFD54F).copy(alpha = 0.5f),
            Color(0xFFFFC107).copy(alpha = 0.65f)
        )
        
        val cashOffsets = listOf(
            Offset(w * 0.35f, h * 0.62f) to -15f,
            Offset(w * 0.42f, h * 0.58f) to -5f,
            Offset(w * 0.46f, h * 0.65f) to 12f
        )
        
        cashOffsets.forEachIndexed { idx, (offset, angle) ->
            rotate(angle, pivot = Offset(offset.x + noteW / 2, offset.y + noteH / 2)) {
                drawRoundRect(
                    color = Color(0xFFA83833), // Seamless mask matching display entries gradient at bottom
                    topLeft = offset,
                    size = Size(noteW, noteH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = noteColors[idx],
                    topLeft = offset,
                    size = Size(noteW, noteH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = borderColors[idx],
                    topLeft = offset,
                    size = Size(noteW, noteH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = 1.8.dp.toPx())
                )
                
                drawCircle(
                    color = borderColors[idx],
                    radius = noteH * 0.25f,
                    center = Offset(offset.x + noteW / 2, offset.y + noteH / 2),
                    style = Stroke(width = 1.2.dp.toPx())
                )
                
                val midY = offset.y + noteH / 2
                drawLine(
                    color = borderColors[idx],
                    start = Offset(offset.x + 8.dp.toPx(), midY),
                    end = Offset(offset.x + noteW / 2 - noteH * 0.35f, midY),
                    strokeWidth = 1.2.dp.toPx()
                )
                drawLine(
                    color = borderColors[idx],
                    start = Offset(offset.x + noteW / 2 + noteH * 0.35f, midY),
                    end = Offset(offset.x + noteW - 8.dp.toPx(), midY),
                    strokeWidth = 1.2.dp.toPx()
                )
            }
        }
    }
}

@Composable
fun TempleGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        val fillCol = Color(0xFF00E676).copy(alpha = 0.05f)
        val outlineCol = Color(0xFFE8F5E9).copy(alpha = 0.5f)
        val pillarCol = Color(0xFF69F0AE).copy(alpha = 0.4f)
        
        val step1H = h * 0.08f
        val step1W = w * 0.75f
        val step1X = w * 0.125f
        val step1Y = h * 0.75f
        
        drawRoundRect(
            color = fillCol,
            topLeft = Offset(step1X, step1Y),
            size = Size(step1W, step1H),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        drawRoundRect(
            color = outlineCol,
            topLeft = Offset(step1X, step1Y),
            size = Size(step1W, step1H),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = 2.2.dp.toPx())
        )
        
        val step2H = h * 0.08f
        val step2W = w * 0.67f
        val step2X = w * 0.165f
        val step2Y = step1Y - step2H
        
        drawRoundRect(
            color = fillCol,
            topLeft = Offset(step2X, step2Y),
            size = Size(step2W, step2H),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        drawRoundRect(
            color = outlineCol,
            topLeft = Offset(step2X, step2Y),
            size = Size(step2W, step2H),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = 2.2.dp.toPx())
        )
        
        val pedimentPath = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.14f, h * 0.3f)
            lineTo(w * 0.86f, h * 0.3f)
            close()
        }
        
        drawPath(path = pedimentPath, color = fillCol)
        drawPath(path = pedimentPath, color = outlineCol, style = Stroke(width = 2.2.dp.toPx()))
        
        val archiH = h * 0.08f
        val archiW = w * 0.69f
        val archiX = w * 0.155f
        val archiY = h * 0.3f
        
        drawRoundRect(
            color = fillCol,
            topLeft = Offset(archiX, archiY),
            size = Size(archiW, archiH),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        drawRoundRect(
            color = outlineCol,
            topLeft = Offset(archiX, archiY),
            size = Size(archiW, archiH),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = 2.2.dp.toPx())
        )
        
        val pillarCount = 4
        val pillarW = w * 0.07f
        val pillarH = step2Y - (archiY + archiH)
        val gap = (archiW - (pillarCount * pillarW)) / (pillarCount + 1)
        
        for (i in 0 until pillarCount) {
            val px = archiX + gap + i * (pillarW + gap)
            val py = archiY + archiH
            
            drawRoundRect(
                color = fillCol,
                topLeft = Offset(px, py),
                size = Size(pillarW, pillarH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            drawRoundRect(
                color = pillarCol,
                topLeft = Offset(px, py),
                size = Size(pillarW, pillarH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(width = 2.0.dp.toPx())
            )
        }
    }
}

@Composable
fun WarehouseGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        val rackL = w * 0.15f
        val rackR = w * 0.85f
        val rackW = rackR - rackL
        val rackTop = h * 0.15f
        val rackBot = h * 0.85f
        
        val shelf1Y = h * 0.5f
        val shelf2Y = rackBot
        
        val frameCol = Color(0xFFFFD54F).copy(alpha = 0.55f)
        val shelfCol = Color(0xFFFFCA28).copy(alpha = 0.55f)
        val crossCol = Color(0xFFFFE082).copy(alpha = 0.2f)
        
        val boxOutlineCol = Color(0xFFFFE082).copy(alpha = 0.45f)
        val boxFillCol = Color(0xFFFFB74D).copy(alpha = 0.05f)
        val boxLineCol = Color(0xFFFFB74D).copy(alpha = 0.35f)
        
        drawLine(color = frameCol, start = Offset(rackL, rackTop), end = Offset(rackL, rackBot), strokeWidth = 2.5.dp.toPx())
        drawLine(color = frameCol, start = Offset(rackR, rackTop), end = Offset(rackR, rackBot), strokeWidth = 2.5.dp.toPx())
        
        drawLine(color = shelfCol, start = Offset(rackL - 4.dp.toPx(), rackTop), end = Offset(rackR + 4.dp.toPx(), rackTop), strokeWidth = 2.5.dp.toPx())
        drawLine(color = shelfCol, start = Offset(rackL, shelf1Y), end = Offset(rackR, shelf1Y), strokeWidth = 2.5.dp.toPx())
        drawLine(color = shelfCol, start = Offset(rackL, shelf2Y), end = Offset(rackR, shelf2Y), strokeWidth = 2.5.dp.toPx())
        
        val midX = rackL + rackW / 2
        drawLine(color = crossCol, start = Offset(rackL, shelf1Y), end = Offset(midX, shelf2Y), strokeWidth = 1.8.dp.toPx())
        drawLine(color = crossCol, start = Offset(midX, shelf1Y), end = Offset(rackL, shelf2Y), strokeWidth = 1.8.dp.toPx())
        
        drawLine(color = frameCol, start = Offset(midX, rackTop), end = Offset(midX, rackBot), strokeWidth = 2.0.dp.toPx())
        
        // Box 1: Top Left
        val box1W = w * 0.24f
        val box1H = h * 0.22f
        val box1X = rackL + (midX - rackL - box1W) / 2
        val box1Y = shelf1Y - box1H - 2.dp.toPx()
        
        drawRoundRect(color = boxFillCol, topLeft = Offset(box1X, box1Y), size = Size(box1W, box1H), cornerRadius = CornerRadius(2.dp.toPx()))
        drawRoundRect(color = boxOutlineCol, topLeft = Offset(box1X, box1Y), size = Size(box1W, box1H), cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(width = 2.0.dp.toPx()))
        drawLine(color = boxLineCol, start = Offset(box1X, box1Y + box1H * 0.35f), end = Offset(box1X + box1W, box1Y + box1H * 0.35f), strokeWidth = 1.5.dp.toPx())
        
        // Box 2: Top Right (Smaller)
        val box2W = w * 0.2f
        val box2H = h * 0.16f
        val box2X = midX + (rackR - midX - box2W) / 2
        val box2Y = shelf1Y - box2H - 2.dp.toPx()
        
        drawRoundRect(color = boxFillCol, topLeft = Offset(box2X, box2Y), size = Size(box2W, box2H), cornerRadius = CornerRadius(2.dp.toPx()))
        drawRoundRect(color = boxOutlineCol, topLeft = Offset(box2X, box2Y), size = Size(box2W, box2H), cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(width = 2.0.dp.toPx()))
        drawLine(color = boxLineCol, start = Offset(box2X, box2Y + box2H * 0.35f), end = Offset(box2X + box2W, box2Y + box2H * 0.35f), strokeWidth = 1.5.dp.toPx())
        
        // Box 3: Bottom Right (Large box with cross tape)
        val box3W = w * 0.28f
        val box3H = h * 0.26f
        val box3X = midX + (rackR - midX - box3W) / 2
        val box3Y = shelf2Y - box3H - 2.dp.toPx()
        
        drawRoundRect(color = boxFillCol, topLeft = Offset(box3X, box3Y), size = Size(box3W, box3H), cornerRadius = CornerRadius(2.dp.toPx()))
        drawRoundRect(color = frameCol, topLeft = Offset(box3X, box3Y), size = Size(box3W, box3H), cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(width = 2.0.dp.toPx()))
        
        drawLine(color = boxLineCol, start = Offset(box3X, box3Y), end = Offset(box3X + box3W, box3Y + box3H), strokeWidth = 1.5.dp.toPx())
        drawLine(color = boxLineCol, start = Offset(box3X + box3W, box3Y), end = Offset(box3X, box3Y + box3H), strokeWidth = 1.5.dp.toPx())
    }
}

@Composable
fun CarStockGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        val fillCol = Color(0xFFFF4081).copy(alpha = 0.05f)
        val carOutlineCol = Color(0xFFFCE4EC).copy(alpha = 0.5f)
        val wheelFillCol = Color(0xFFC2185B).copy(alpha = 0.4f)
        val wheelOutlineCol = Color(0xFFF8BBD0).copy(alpha = 0.5f)
        
        val carPath = Path().apply {
            moveTo(w * 0.1f, h * 0.72f)
            lineTo(w * 0.05f, h * 0.72f)
            lineTo(w * 0.05f, h * 0.65f)
            quadraticTo(w * 0.05f, h * 0.58f, w * 0.1f, h * 0.55f)
            lineTo(w * 0.2f, h * 0.55f)
            lineTo(w * 0.32f, h * 0.38f)
            lineTo(w * 0.55f, h * 0.38f)
            lineTo(w * 0.66f, h * 0.52f)
            lineTo(w * 0.85f, h * 0.52f)
            quadraticTo(w * 0.92f, h * 0.52f, w * 0.92f, h * 0.6f)
            lineTo(w * 0.92f, h * 0.72f)
            lineTo(w * 0.82f, h * 0.72f)
            
            val frontWheelR = w * 0.11f
            val frontWheelCx = w * 0.72f
            arcTo(
                rect = Rect(Offset(frontWheelCx - frontWheelR, h * 0.72f - frontWheelR), Size(frontWheelR * 2, frontWheelR * 2)),
                startAngleDegrees = 0f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            
            lineTo(w * 0.34f, h * 0.72f)
            
            val rearWheelR = w * 0.11f
            val rearWheelCx = w * 0.24f
            arcTo(
                rect = Rect(Offset(rearWheelCx - rearWheelR, h * 0.72f - rearWheelR), Size(rearWheelR * 2, rearWheelR * 2)),
                startAngleDegrees = 0f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            close()
        }
        
        drawPath(path = carPath, color = fillCol)
        drawPath(path = carPath, color = carOutlineCol, style = Stroke(width = 2.2.dp.toPx()))
        
        val wheelR = w * 0.08f
        val frontWheelCx = w * 0.72f
        val rearWheelCx = w * 0.24f
        val wheelCy = h * 0.72f
        
        drawCircle(color = wheelFillCol, radius = wheelR, center = Offset(frontWheelCx, wheelCy))
        drawCircle(color = wheelOutlineCol, radius = wheelR, center = Offset(frontWheelCx, wheelCy), style = Stroke(width = 2.0.dp.toPx()))
        drawCircle(color = wheelFillCol, radius = wheelR, center = Offset(rearWheelCx, wheelCy))
        drawCircle(color = wheelOutlineCol, radius = wheelR, center = Offset(rearWheelCx, wheelCy), style = Stroke(width = 2.0.dp.toPx()))
        
        val chartPath = Path().apply {
            moveTo(w * 0.08f, h * 0.45f)
            cubicTo(w * 0.25f, h * 0.35f, w * 0.45f, h * 0.48f, w * 0.65f, h * 0.22f)
            lineTo(w * 0.85f, h * 0.15f)
        }
        
        drawPath(path = chartPath, color = Color.White.copy(alpha = 0.7f), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        
        val dots = listOf(
            Offset(w * 0.08f, h * 0.45f),
            Offset(w * 0.35f, h * 0.39f),
            Offset(w * 0.65f, h * 0.22f),
            Offset(w * 0.85f, h * 0.15f)
        )
        
        dots.forEachIndexed { index, dot ->
            drawCircle(color = Color.White.copy(alpha = 0.8f), radius = 3.dp.toPx(), center = dot)
            drawCircle(color = Color(0xFFFF4081).copy(alpha = 0.35f), radius = 6.dp.toPx(), center = dot)
            
            if (index == dots.size - 1) {
                val arrowPath = Path().apply {
                    moveTo(dot.x, dot.y)
                    lineTo(dot.x - 9.dp.toPx(), dot.y + 1.dp.toPx())
                    lineTo(dot.x - 2.dp.toPx(), dot.y + 7.dp.toPx())
                    close()
                }
                drawPath(path = arrowPath, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun ScrollGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        val fillCol = Color(0xFFFFAB40).copy(alpha = 0.05f)
        val outlineCol = Color(0xFFFFF8E1).copy(alpha = 0.5f)
        val scribbleCol = Color(0xFFFFD54F).copy(alpha = 0.5f)
        val quillCol = Color(0xFF8D6E63).copy(alpha = 0.5f)
        val quillOutlineCol = Color(0xFFFFE082).copy(alpha = 0.45f)
        
        val scrollPath = Path().apply {
            moveTo(w * 0.2f, h * 0.25f)
            cubicTo(w * 0.2f, h * 0.15f, w * 0.4f, h * 0.15f, w * 0.4f, h * 0.25f)
            lineTo(w * 0.72f, h * 0.25f)
            cubicTo(w * 0.72f, h * 0.15f, w * 0.82f, h * 0.2f, w * 0.82f, h * 0.32f)
            lineTo(w * 0.82f, h * 0.75f)
            cubicTo(w * 0.82f, h * 0.85f, w * 0.62f, h * 0.85f, w * 0.62f, h * 0.75f)
            lineTo(w * 0.3f, h * 0.75f)
            cubicTo(w * 0.3f, h * 0.82f, w * 0.2f, h * 0.8f, w * 0.2f, h * 0.68f)
            close()
        }
        
        drawPath(path = scrollPath, color = fillCol)
        drawPath(path = scrollPath, color = outlineCol, style = Stroke(width = 2.2.dp.toPx()))
        
        val scribblePath = Path().apply {
            moveTo(w * 0.32f, h * 0.58f)
            quadraticTo(w * 0.38f, h * 0.54f, w * 0.44f, h * 0.58f)
            quadraticTo(w * 0.5f, h * 0.62f, w * 0.56f, h * 0.56f)
            lineTo(w * 0.64f, h * 0.56f)
        }
        
        drawPath(path = scribblePath, color = scribbleCol, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
        
        val quillPath = Path().apply {
            val tipX = w * 0.58f
            val tipY = h * 0.55f
            
            moveTo(tipX, tipY)
            lineTo(w * 0.8f, h * 0.22f)
            lineTo(w * 0.84f, h * 0.25f)
            lineTo(tipX + 8.dp.toPx(), tipY + 4.dp.toPx())
            close()
        }
        
        drawPath(path = quillPath, color = quillCol)
        drawPath(path = quillPath, color = quillOutlineCol, style = Stroke(width = 1.8.dp.toPx()))
        
        drawLine(
            color = scribbleCol,
            start = Offset(w * 0.58f, h * 0.55f),
            end = Offset(w * 0.68f, h * 0.42f),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

@Composable
fun AiAssistantGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val fillCol = Color(0xFFD500F9).copy(alpha = 0.05f)
        val outlineCol = Color(0xFFF3E5F5).copy(alpha = 0.5f)
        val lineCol = Color(0xFFE040FB).copy(alpha = 0.4f)

        // Draw a stylized holographic chat bubble
        val bubblePath = Path().apply {
            val left = w * 0.15f
            val top = h * 0.15f
            val width = w * 0.65f
            val height = h * 0.65f
            
            moveTo(left + 8.dp.toPx(), top)
            lineTo(left + width - 8.dp.toPx(), top)
            quadraticTo(left + width, top, left + width, top + 8.dp.toPx())
            lineTo(left + width, top + height - 16.dp.toPx())
            quadraticTo(left + width, top + height, left + width - 16.dp.toPx(), top + height)
            
            // Chat tail
            lineTo(left + width * 0.5f, top + height)
            lineTo(left + width * 0.35f, top + height + 8.dp.toPx())
            lineTo(left + width * 0.4f, top + height)
            
            lineTo(left + 8.dp.toPx(), top + height)
            quadraticTo(left, top + height, left, top + height - 8.dp.toPx())
            lineTo(left, top + 8.dp.toPx())
            quadraticTo(left, top, left + 8.dp.toPx(), top)
            close()
        }

        drawPath(path = bubblePath, color = fillCol)
        drawPath(path = bubblePath, color = outlineCol, style = Stroke(width = 2.2.dp.toPx()))

        // Draw horizontal voice waves or lines inside the chat bubble
        val lineStart = w * 0.25f
        val lineEndMax = w * 0.7f
        val lineYStart = h * 0.3f
        val lineSpacing = h * 0.12f
        val lineCount = 3

        for (i in 0 until lineCount) {
            val y = lineYStart + i * lineSpacing
            val endX = lineStart + (lineEndMax - lineStart) * (0.5f + (i % 3) * 0.2f)
            drawLine(
                color = lineCol,
                start = Offset(lineStart, y),
                end = Offset(endX, y),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Draw glowing sparkles / AI stars around the chat bubble
        drawSparkle(Offset(w * 0.78f, h * 0.22f), w * 0.1f, Color.White, Color(0xFFB388FF))
        drawSparkle(Offset(w * 0.84f, h * 0.48f), w * 0.07f, Color.White, Color(0xFFB388FF))
    }
}

fun DrawScope.drawSparkle(center: Offset, size: Float, mainColor: Color = Color.White, glowColor: Color = Color(0xFFE040FB)) {
    val starPath = Path().apply {
        moveTo(center.x, center.y - size)
        quadraticTo(center.x, center.y, center.x + size, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + size)
        quadraticTo(center.x, center.y, center.x - size, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - size)
        close()
    }
    drawPath(path = starPath, color = mainColor.copy(alpha = 0.55f))
    drawPath(path = starPath, color = glowColor.copy(alpha = 0.20f), style = Stroke(width = 1.8.dp.toPx()))
}

@Composable
fun SupportGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val cx = w * 0.5f
        val cy = h * 0.5f
        val outerRadius = w * 0.32f
        val innerRadius = w * 0.16f

        val fillCol = Color(0xFF00E5FF).copy(alpha = 0.05f)
        val outerOutlineCol = Color(0xFFE0F7FA).copy(alpha = 0.5f)
        val innerOutlineCol = Color(0xFF80DEEA).copy(alpha = 0.45f)
        val strapCol = Color(0xFF00E5FF).copy(alpha = 0.4f)
        val gridCol = Color(0xFF00E5FF).copy(alpha = 0.12f)

        // Draw lifebuoy outer circle
        drawCircle(
            color = fillCol,
            radius = outerRadius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = outerOutlineCol,
            radius = outerRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 2.2.dp.toPx())
        )

        // Draw lifebuoy inner circle
        drawCircle(
            color = innerOutlineCol,
            radius = innerRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 2.0.dp.toPx())
        )

        // Draw the 4 safety straps/bands around the buoy (top, bottom, left, right)
        val strapW = w * 0.08f
        
        // Vertical straps
        drawRect(
            color = strapCol,
            topLeft = Offset(cx - strapW / 2, cy - outerRadius - 1.dp.toPx()),
            size = Size(strapW, outerRadius - innerRadius + 2.dp.toPx())
        )
        drawRect(
            color = strapCol,
            topLeft = Offset(cx - strapW / 2, cy + innerRadius - 1.dp.toPx()),
            size = Size(strapW, outerRadius - innerRadius + 2.dp.toPx())
        )

        // Horizontal straps
        drawRect(
            color = strapCol,
            topLeft = Offset(cx - outerRadius - 1.dp.toPx(), cy - strapW / 2),
            size = Size(outerRadius - innerRadius + 2.dp.toPx(), strapW)
        )
        drawRect(
            color = strapCol,
            topLeft = Offset(cx + innerRadius - 1.dp.toPx(), cy - strapW / 2),
            size = Size(outerRadius - innerRadius + 2.dp.toPx(), strapW)
        )

        // Draw a decorative background grid behind it to match wireframe style
        drawLine(
            color = gridCol,
            start = Offset(w * 0.1f, cy),
            end = Offset(w * 0.9f, cy),
            strokeWidth = 1.2.dp.toPx()
        )
        drawLine(
            color = gridCol,
            start = Offset(cx, h * 0.1f),
            end = Offset(cx, h * 0.9f),
            strokeWidth = 1.2.dp.toPx()
        )
    }
}

@Composable
fun CardWireframeGraphic(title: String, modifier: Modifier = Modifier) {
    val cleanTitle = title.lowercase()
    val reducedOpacityModifier = modifier.alpha(0.25f)
    when {
        cleanTitle.contains("voucher") || cleanTitle.contains("add new entry") || cleanTitle.contains("entry") -> {
            ReceiptGraphic(modifier = reducedOpacityModifier)
        }
        cleanTitle.contains("display") || cleanTitle.contains("all entries") || cleanTitle.contains("entries") -> {
            ClipboardGraphic(modifier = reducedOpacityModifier)
        }
        cleanTitle.contains("accounts") || cleanTitle.contains("account info") || cleanTitle.contains("ledgers") -> {
            TempleGraphic(modifier = reducedOpacityModifier)
        }
        cleanTitle.contains("inventory") || cleanTitle.contains("stock items") || cleanTitle.contains("items") -> {
            WarehouseGraphic(modifier = reducedOpacityModifier)
        }
        cleanTitle.contains("live") || cleanTitle.contains("showroom") || cleanTitle.contains("live stock") -> {
            CarStockGraphic(modifier = reducedOpacityModifier)
        }
        cleanTitle.contains("estimates") || cleanTitle.contains("quotes") || cleanTitle.contains("estimate") -> {
            ScrollGraphic(modifier = reducedOpacityModifier)
        }
        cleanTitle.contains("assistant") || cleanTitle.contains("helper") || cleanTitle.contains("chat") || cleanTitle.contains("ai") -> {
            AiAssistantGraphic(modifier = reducedOpacityModifier)
        }
        cleanTitle.contains("help") || cleanTitle.contains("support") || cleanTitle.contains("guide") -> {
            SupportGraphic(modifier = reducedOpacityModifier)
        }
        else -> {
            Box(modifier = modifier)
        }
    }
}

@Composable
fun ModernActionCard(
    item: com.minhas.teller.ui.screens.ModernDashboardAction,
    navController: NavController,
    isLightMode: Boolean,
    modifier: Modifier = Modifier,
    downloadProgress: Int = 0,
    isDownloading: Boolean = false,
    isDownloadFailed: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(28.dp)
    val backgroundBrush = item.gradient
    val darkShadow = if (isLightMode) Color(0xFFA3B1C6).copy(alpha = 0.65f) else Color(0xFF000000).copy(alpha = 0.6f)
    val lightShadow = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFFFFFFFF).copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.99f)
            .neumorphicShadow(
                shape = shape,
                darkShadowColor = darkShadow,
                lightShadowColor = lightShadow,
                blur = 12.dp,
                offset = 6.dp
            )
            .clip(shape)
            .background(backgroundBrush)
            .border(
                width = 1.dp,
                color = if (isLightMode) Color(0xFFFFFFFF).copy(alpha = 0.5f) else Color(0xFFFFFFFF).copy(alpha = 0.15f),
                shape = shape
            )
            .clickable { 
                if (onClick != null) {
                    onClick()
                } else {
                    navController.navigate(item.route)
                }
            }
            .padding(18.dp)
    ) {
        // Dynamic Bespoke Graphic drawn on the left side
        CardWireframeGraphic(
            title = item.title,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight(0.60f)
                .fillMaxWidth(0.55f)
                .padding(start = 2.dp)
        )

        // Small outline icon at the top right
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier
                .size(26.dp)
                .align(Alignment.TopEnd)
        )

        // Text labels at bottom left
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            AutoShrinkText(
                text = item.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxFontSize = if (item.title.contains("Display", ignoreCase = true)) 15.sp else 17.sp,
                style = androidx.compose.ui.text.TextStyle(
                    lineHeight = if (item.title.contains("Display", ignoreCase = true)) 17.sp else 19.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            
            if (isDownloading) {
                AutoShrinkText(
                    text = "Downloading AI... $downloadProgress%",
                    color = Color.White.copy(alpha = 0.9f),
                    maxFontSize = 11.sp,
                    minFontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            } else if (isDownloadFailed) {
                AutoShrinkText(
                    text = "Download Failed (Tap to Retry)",
                    color = Color(0xFFFFCDD2),
                    maxFontSize = 11.sp,
                    minFontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                AutoShrinkText(
                    text = item.subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Normal,
                    maxFontSize = 10.5.sp,
                    minFontSize = 7.sp,
                    style = androidx.compose.ui.text.TextStyle(lineHeight = 12.sp)
                )
            }
        }
    }
}

private fun Modifier.neumorphicShadow(
    shape: androidx.compose.ui.graphics.Shape,
    darkShadowColor: Color = Color(0xFFA3B1C6),
    lightShadowColor: Color = Color(0xFFFFFFFF),
    blur: androidx.compose.ui.unit.Dp = 8.dp,
    offset: androidx.compose.ui.unit.Dp = 5.dp
) = this.drawBehind {
    val blurPx = blur.toPx()
    val offsetPx = offset.toPx()
    
    drawIntoCanvas { canvas ->
        val paintDark = Paint().apply {
            color = darkShadowColor
            isAntiAlias = true
        }
        val frameworkPaintDark = paintDark.asFrameworkPaint()
        if (blurPx > 0) {
            frameworkPaintDark.maskFilter = android.graphics.BlurMaskFilter(
                blurPx,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }
        
        val paintLight = Paint().apply {
            color = lightShadowColor
            isAntiAlias = true
        }
        val frameworkPaintLight = paintLight.asFrameworkPaint()
        if (blurPx > 0) {
            frameworkPaintLight.maskFilter = android.graphics.BlurMaskFilter(
                blurPx,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }
        
        val outline = shape.createOutline(size, layoutDirection, this)
        
        // Draw Light Shadow (Top-Left offset, negative X and Y)
        canvas.save()
        canvas.translate(-offsetPx, -offsetPx)
        canvas.drawOutline(outline, paintLight)
        canvas.restore()
        
        // Draw Dark Shadow (Bottom-Right offset, positive X and Y)
        canvas.save()
        canvas.translate(offsetPx, offsetPx)
        canvas.drawOutline(outline, paintDark)
        canvas.restore()
    }
}

// ==========================================
// NEEDS ATTENTION CARD (replaces Teller Score)
// ==========================================

@Composable
fun NeedsAttentionCard(
    items: List<AttentionItem>,
    totalIssues: Int,
    isLoading: Boolean,
    onItemClick: (AttentionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 16.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E).copy(alpha = 0.92f),
                        Color(0xFF16213E).copy(alpha = 0.95f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White.copy(alpha = 0.5f),
                    strokeWidth = 2.dp
                )
            }
        } else if (items.isEmpty()) {
            // All clear state
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    AutoShrinkText(
                        text = "ALL CLEAR",
                        maxFontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E676),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Your books look healthy. No issues need attention.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            // Issues list
            Column {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        AutoShrinkText(
                            text = "NEEDS ATTENTION",
                            maxFontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFB74D),
                            letterSpacing = 0.8.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF5252).copy(alpha = 0.2f),
                    ) {
                        AutoShrinkText(
                            text = "$totalIssues issue${if (totalIssues != 1) "s" else ""}",
                            maxFontSize = 11.sp,
                            minFontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(6.dp))

                // Items (show max 4, then a "+N more" summary)
                val displayItems = items.take(4)
                val remaining = items.size - 4

                displayItems.forEach { item ->
                    AttentionItemRow(item = item, onClick = { onItemClick(item) })
                    if (item != displayItems.last() || remaining > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }

                if (remaining > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "+$remaining more issue${if (remaining != 1) "s" else ""} — tap to view all",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttentionItemRow(item: AttentionItem, onClick: () -> Unit) {
    val severityColor = when (item.severity) {
        AttentionSeverity.HIGH -> Color(0xFFFF5252)
        AttentionSeverity.MEDIUM -> Color(0xFFFFB74D)
        AttentionSeverity.LOW -> Color(0xFFFFF59D)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(severityColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = severityColor,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            AutoShrinkText(
                text = item.title,
                maxFontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = androidx.compose.ui.text.TextStyle(lineHeight = 17.sp)
            )
            AutoShrinkText(
                text = item.description,
                maxFontSize = 11.sp,
                minFontSize = 8.sp,
                color = Color.White.copy(alpha = 0.5f),
                style = androidx.compose.ui.text.TextStyle(lineHeight = 14.sp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Count badge
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = severityColor.copy(alpha = 0.18f),
        ) {
            AutoShrinkText(
                text = "${item.count}",
                maxFontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = severityColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Chevron
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.size(18.dp)
        )
    }
}

