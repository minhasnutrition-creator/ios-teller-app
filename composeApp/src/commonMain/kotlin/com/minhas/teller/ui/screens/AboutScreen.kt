package com.minhas.teller.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.minhas.teller.BuildConfig
import com.minhas.teller.R
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.*
import com.minhas.teller.utils.LicenseUtils
import com.minhas.teller.utils.TerminologyManager
import kotlinx.coroutines.launch

// ── Deep-space cosmic palette: radial #13092A (top-center) → #0A0D18 (edges) ──
private val CosmicTop = Color(0xFF13092A)
private val CosmicEdge = Color(0xFF0A0D18)
private val AmbientPurple = Color(0xFF8A2BE2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val clipboardManager = LocalClipboardManager.current
    val machineId = remember { LicenseUtils.getMachineId(context) }
    val storedKey = remember { LicenseUtils.getStoredLicenseKey(context) }
    val storedType = remember { LicenseUtils.getStoredLicenseType(context) }
    val isDevBuild = BuildConfig.FORCE_FULL_LICENSE

    var showSurrenderDialog by remember { mutableStateOf(false) }

    var appStatus by remember { mutableStateOf<LicenseUtils.AppStatus?>(null) }
    var daysLeft by remember { mutableStateOf(0) }
    var isLoadingStatus by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val (status, days) = LicenseUtils.getAppStatus(context)
        appStatus = status
        daysLeft = days
        isLoadingStatus = false
    }

    if (showSurrenderDialog) {
        AlertDialog(
            onDismissRequest = { showSurrenderDialog = false },
            title = { Text(TerminologyManager.getTerm("Surrender License?")) },
            text = { Text(TerminologyManager.getTerm("Surrendering will deactivate the app on this device. You can then use the license key on another device. Are you sure?")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSurrenderDialog = false
                        scope.launch {
                            val result = LicenseUtils.surrenderLicense(context)
                            Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                            if (result.first) {
                                navController.navigate(Screen.License.route) {
                                    popUpTo(0)
                                }
                            }
                        }
                    }
                ) {
                    Text(TerminologyManager.getTerm("Yes, Surrender"), color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSurrenderDialog = false }) {
                    Text(TerminologyManager.getTerm("Cancel"))
                }
            }
        )
    }

    val statusLabel = when {
        isLoadingStatus -> "Verifying..."
        isDevBuild -> "Active (Developer License)"
        appStatus == LicenseUtils.AppStatus.LICENSED -> "Active & Verified"
        appStatus == LicenseUtils.AppStatus.TRIAL_ACTIVE -> "Free Trial ($daysLeft days left)"
        appStatus == LicenseUtils.AppStatus.TRIAL_EXPIRED -> "Trial Expired"
        appStatus == LicenseUtils.AppStatus.NOT_LICENSED -> "Unlicensed"
        else -> "Active"
    }

    val typeLabel = when {
        isDevBuild -> "Enterprise Pro (Full Access)"
        storedType != "Unknown" && storedType.isNotBlank() -> storedType
        appStatus == LicenseUtils.AppStatus.LICENSED -> "Pro License"
        appStatus == LicenseUtils.AppStatus.TRIAL_ACTIVE -> "45-Day Full Trial"
        else -> "Standard"
    }

    val resolvedKey = when {
        isDevBuild -> "DEV-FULL-LICENSE"
        !storedKey.isNullOrBlank() && storedKey != "Unknown" -> storedKey
        else -> null
    }

    val statusHealthy = isDevBuild ||
        appStatus == LicenseUtils.AppStatus.LICENSED ||
        appStatus == LicenseUtils.AppStatus.TRIAL_ACTIVE
    val statusColor = if (statusHealthy) CosmicCyan else Color(0xFFFF6B6B)

    // ── Cosmic background: deep radial gradient (top-center glow) + nebula ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(CosmicTop, CosmicEdge),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.maxDimension * 1.2f
                    )
                )
            }
    ) {
        NebulaGlow(
            color = CosmicCyan,
            glowSize = 280.dp,
            blurRadius = 90.dp,
            alignment = Alignment.TopEnd,
            alpha = 0.28f
        )
        NebulaGlow(
            color = AmbientPurple,
            glowSize = 240.dp,
            blurRadius = 90.dp,
            alignment = Alignment.BottomStart,
            alpha = 0.18f
        )
        NebulaGlow(
            color = CosmicCyan,
            glowSize = 300.dp,
            blurRadius = 100.dp,
            alignment = Alignment.Center,
            alpha = 0.12f
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TellerTopAppBar(
                    title = TerminologyManager.getTerm("About"),
                    canNavigateBack = true,
                    navigateUp = { navController.popBackStack() },
                    titleColor = CosmicGold,
                    titleFontWeight = FontWeight.Bold
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 0.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                HeaderSection()

                Spacer(modifier = Modifier.height(24.dp))

                LicenseInformationCard(
                    statusLabel = statusLabel,
                    statusColor = statusColor,
                    typeLabel = typeLabel,
                    machineId = machineId,
                    resolvedKey = resolvedKey,
                    isDevBuild = isDevBuild,
                    manageLicense = isDevBuild || appStatus == LicenseUtils.AppStatus.LICENSED,
                    showSurrender = !isDevBuild &&
                        !storedKey.isNullOrBlank() &&
                        storedKey != "Unknown" &&
                        appStatus == LicenseUtils.AppStatus.LICENSED,
                    onCopy = { text, toast ->
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
                    },
                    onUpgradeClick = { navController.navigate(Screen.License.route) },
                    onSurrenderClick = { showSurrenderDialog = true }
                )

                Spacer(modifier = Modifier.height(16.dp))

                DeveloperCard()

                Spacer(modifier = Modifier.height(16.dp))

                AboutTheAppCard()

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

/** Soft, blurred radial glow used to build the nebula/particle backdrop. */
@Composable
private fun NebulaGlow(
    color: Color,
    glowSize: androidx.compose.ui.unit.Dp,
    blurRadius: androidx.compose.ui.unit.Dp,
    alignment: Alignment,
    alpha: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .size(glowSize)
                .align(alignment)
                .blur(blurRadius)
                .background(
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), Color.Transparent)
                    )
                )
        )
    }
}

// ───────────────────────────── Header ─────────────────────────────

@Composable
private fun HeaderSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val logoPainter = painterResource(id = R.drawable.teller_3d_glowing)

        // 3D glowing logo — full width (max 340dp), black background already removed
        // (screen-blend equivalent applied to the asset) so the neon streams show cleanly.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(logoPainter.intrinsicSize.width / logoPainter.intrinsicSize.height)
        ) {
            Image(
                painter = logoPainter,
                contentDescription = TerminologyManager.getTerm("App Logo"),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )

            // Floating HUD labels — small, semi-transparent, monospaced.
            Text(
                text = "ADVANCED MANAGEMENT SYSTEM",
                color = CosmicCyan.copy(alpha = 0.45f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            )
            Text(
                text = "QUANTUM OPTIMIZATION",
                color = CosmicCyan.copy(alpha = 0.45f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
            Text(
                text = "TELLER PROTOCOLS",
                color = CosmicGold.copy(alpha = 0.55f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = TerminologyManager.getTerm("Teller"),
            style = TextStyle(
                brush = CyanLavenderGradient,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${TerminologyManager.getTerm("Version")} ${BuildConfig.VERSION_NAME}",
            color = CosmicSilverGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

// ────────────────────── License Information Card ──────────────────────

@Composable
private fun LicenseInformationCard(
    statusLabel: String,
    statusColor: Color,
    typeLabel: String,
    machineId: String,
    resolvedKey: String?,
    isDevBuild: Boolean,
    manageLicense: Boolean,
    showSurrender: Boolean,
    onCopy: (String, String) -> Unit,
    onUpgradeClick: () -> Unit,
    onSurrenderClick: () -> Unit
) {
    GlassCard {
        GlassSectionTitle("License Information")
        Spacer(modifier = Modifier.height(14.dp))

        // Status row — cyan pill with a soft glow for a healthy state.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Status:",
                color = CosmicSilver,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(statusColor.copy(alpha = 0.10f))
                    .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Type row
        KeyValueRow(label = "Type:", value = typeLabel)
        Spacer(modifier = Modifier.height(12.dp))

        // Device ID badge with copy icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Device ID:",
                color = CosmicSilver,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            CopyBadge(
                text = machineId.take(12),
                contentDescription = "Copy Device ID",
                onClick = { onCopy(machineId, "Device ID copied to clipboard") }
            )
        }

        // License key row (kept as an additional detail when present)
        if (resolvedKey != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Key:",
                    color = CosmicSilver,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                val masked = if (resolvedKey.length > 12 && !isDevBuild)
                    resolvedKey.take(4) + "..." + resolvedKey.takeLast(4)
                else resolvedKey
                CopyBadge(
                    text = masked,
                    contentDescription = "Copy License Key",
                    onClick = { onCopy(resolvedKey, "License key copied") }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // CTA — full-width copper/rose-gold pill with a top-edge gloss.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(30.dp),
                    spotColor = Color(0xFFE88F78).copy(alpha = 0.35f)
                )
                .clip(RoundedCornerShape(30.dp))
                .background(CopperRoseGoldGradient)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
                .clickable(onClick = onUpgradeClick)
                .padding(vertical = 14.dp)
        ) {
            Text(
                text = if (manageLicense) "Manage License / Subscription"
                else "View Plans / Upgrade License",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (showSurrender) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .border(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.5f), RoundedCornerShape(30.dp))
                    .clickable(onClick = onSurrenderClick)
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = "Surrender License",
                    color = Color(0xFFFF6B6B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** Light-silver label + white value row. */
@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = CosmicSilver,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}

/** Monospace value badge with an inline copy icon. */
@Composable
private fun CopyBadge(
    text: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, CosmicCardBorder.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = contentDescription,
            tint = CosmicCyan,
            modifier = Modifier.size(14.dp)
        )
    }
}

// ─────────────────────────── Developer Card ───────────────────────────

@Composable
private fun DeveloperCard() {
    GlassCard {
        GlassSectionTitle("Developer")
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.developer_avatar),
                contentDescription = "Arjit Minhas",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, CosmicCyan.copy(alpha = 0.55f), CircleShape)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Arjit Minhas",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ────────────────────────── About the App Card ──────────────────────────

@Composable
private fun AboutTheAppCard() {
    GlassCard {
        GlassSectionTitle("About Teller")
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Engineered for Independence. Built for Growth.",
            style = TextStyle(
                brush = CyanLavenderGradient,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Teller was built on a bold conviction: enterprise-grade financial power shouldn't be chained to a desk or locked behind legacy software. From the shop floor to the boardroom, Teller transforms your smartphone into an uncompromising financial command center.",
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        AboutFeaturePill(
            emoji = "\uD83D\uDEE1\uFE0F",
            title = "100% Offline-First & Private",
            description = "Your books belong strictly to you. Work anywhere—remote warehouses, basements, or flights—with zero internet needed."
        )
        Spacer(modifier = Modifier.height(12.dp))
        AboutFeaturePill(
            emoji = "\u26A1",
            title = "Tally-Grade Precision",
            description = "Full double-entry accounting, bill-wise tracking, multi-company architecture, and real-time financial reporting."
        )
        Spacer(modifier = Modifier.height(12.dp))
        AboutFeaturePill(
            emoji = "\uD83D\uDE80",
            title = "Intelligent & Connected",
            description = "Instant PC companion pairing, smart voice billing, receipt OCR, and seamless cloud synchronization."
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "✨ Your business in your pocket. Unstoppable anywhere.",
                style = TextStyle(
                    brush = CopperRoseGoldGradient,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.padding(end = 36.dp)
            )

            // Glowing 4-point star/sparkle corner accent (cyan + gold).
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = CosmicCyan,
                    modifier = Modifier.size(18.dp)
                )
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = CosmicGold,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutFeaturePill(
    emoji: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, CosmicCardBorder.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 16.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}
