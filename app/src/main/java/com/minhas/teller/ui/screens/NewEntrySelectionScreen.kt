package com.minhas.teller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.minhas.teller.navigation.Screen
import com.minhas.teller.utils.TerminologyManager
import androidx.compose.material.icons.automirrored.filled.CompareArrows

data class EntryOption(
    val title: String,
    val description: String,
    val voucherType: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEntrySelectionScreen(navController: NavController) {
    val options = listOf(
        EntryOption(
            TerminologyManager.getTerm("Sale"),
            TerminologyManager.getTerm("Selling your products or services"),
            "Sales",
            Icons.AutoMirrored.Filled.TrendingUp,
            Color(0xFF4CAF50) // Green
        ),
        EntryOption(
            TerminologyManager.getTerm("Money In"),
            TerminologyManager.getTerm("Money you received from someone"),
            "Receipt",
            Icons.Default.ArrowDownward,
            Color(0xFF2196F3) // Blue
        ),
        EntryOption(
            TerminologyManager.getTerm("Money Out"),
            TerminologyManager.getTerm("Money you gave to someone"),
            "Payment",
            Icons.Default.ArrowUpward,
            Color(0xFFF44336) // Red
        ),
        EntryOption(
            TerminologyManager.getTerm("Purchase"),
            TerminologyManager.getTerm("Purchasing products for stock"),
            "Purchase",
            Icons.Default.ShoppingCart,
            Color(0xFFFF9800) // Orange
        ),
        EntryOption(
            TerminologyManager.getTerm("Adjustment"),
            TerminologyManager.getTerm("Exchange or non-cash adjustments"),
            "Journal",
            Icons.AutoMirrored.Filled.CompareArrows,
            Color(0xFF9C27B0) // Purple
        ),
        EntryOption(
            TerminologyManager.getTerm("Return"),
            TerminologyManager.getTerm("Customer gave items back, or you sent items back"),
            "Return",
            Icons.Default.Replay,
            Color(0xFF00897B) // Teal
        )
    )

    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.offset(x = (-12).dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = TerminologyManager.getTerm("Back"),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Text(
                text = TerminologyManager.getTerm("What kind of entry are we doing today?"),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    lineHeight = 36.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 24.dp, top = 4.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(options) { option ->
                    EntryOptionCard(option) {
                        // Normal push: back from the voucher returns to this selection screen,
                        // then back again to wherever the user came from.
                        if (option.voucherType == "Return") {
                            navController.navigate(Screen.Returns.route)
                        } else {
                            navController.navigate(Screen.AddEditVoucher.route + "?type=${option.voucherType}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EntryOptionCard(option: EntryOption, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val darkShadow = if (isDark) Color(0xFF000000).copy(alpha = 0.55f) else Color(0xFFA3B1C6).copy(alpha = 0.6f)
    val lightShadow = if (isDark) Color(0xFFFFFFFF).copy(alpha = 0.08f) else Color(0xFFFFFFFF)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .neumorphicShadow(
                shape = RoundedCornerShape(16.dp),
                darkShadowColor = darkShadow,
                lightShadowColor = lightShadow,
                blur = 8.dp,
                offset = 4.dp
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.4f else 0.8f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) Color(0xFFFFFFFF).copy(alpha = 0.08f) else Color(0xFFFFFFFF).copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(option.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    tint = option.color,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
