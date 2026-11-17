package com.minhas.teller.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.minhas.teller.navigation.Screen
import kotlinx.coroutines.delay

object VoiceTooltipManager {
    private const val PREFS_NAME = "TellerPrefs"
    private const val KEY_HAS_SEEN = "has_seen_voice_entry_tooltip"

    var isVisible by mutableStateOf(false)

    fun checkAndShowFirstTime(context: android.content.Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val hasSeen = prefs.getBoolean(KEY_HAS_SEEN, false)
        if (!hasSeen) {
            isVisible = true
        }
    }

    fun dismiss(context: android.content.Context? = null) {
        isVisible = false
        context?.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY_HAS_SEEN, true)?.apply()
    }
}

@Composable
fun GlassBottomNavBar(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isDashboard = currentRoute == Screen.Dashboard.route || currentRoute == null
    val showVoiceTooltip = isDashboard && VoiceTooltipManager.isVisible

    // Show only once for first-time user, auto-dismiss after 6 seconds
    LaunchedEffect(isDashboard) {
        if (isDashboard) {
            VoiceTooltipManager.checkAndShowFirstTime(context)
            if (VoiceTooltipManager.isVisible) {
                delay(6000)
                VoiceTooltipManager.dismiss(context)
            }
        } else {
            VoiceTooltipManager.dismiss(context)
        }
    }

    // Pulsing animation for the Voice Entry center button
    val infiniteTransition = rememberInfiniteTransition(label = "VoicePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp)
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── FLOATING VOICE COACHMARK POPUP ON DASHBOARD (First-time user only) ──
        AnimatedVisibility(
            visible = showVoiceTooltip,
            enter = fadeIn() + slideInVertically(initialOffsetY = { 20 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { 20 })
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        VoiceTooltipManager.dismiss(context)
                        navController.navigate("chat?autoVoice=true")
                    }
            ) {
                // Popup speech bubble
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1E3A8A).copy(alpha = 0.95f),
                                    Color(0xFF0F172A).copy(alpha = 0.98f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF38BDF8).copy(alpha = glowAlpha),
                                    Color(0xFF818CF8).copy(alpha = glowAlpha)
                                )
                            ),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "✨",
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Try creating vouchers or making entries using your voice!",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { VoiceTooltipManager.dismiss() }
                        )
                    }
                }

                // Downward pointing arrow triangle
                Box(
                    modifier = Modifier
                        .offset(y = (-1).dp)
                        .size(width = 12.dp, height = 6.dp)
                        .clip(GenericShape { size, _ ->
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        })
                        .background(Color(0xFF0F172A).copy(alpha = 0.98f))
                )
            }
        }

        // ── GLASS BOTTOM NAVIGATION BAR ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF0F1422).copy(alpha = 0.88f))
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color(0xFF38BDF8).copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.10f)
                        )
                    ),
                    RoundedCornerShape(32.dp)
                )
                .height(68.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. HOME TAB
                val isHomeSelected = currentRoute == Screen.Dashboard.route || currentRoute == null
                val homeColor = if (isHomeSelected) Color.White else Color.White.copy(alpha = 0.45f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(vertical = 8.dp, horizontal = 18.dp)
                ) {
                    Icon(
                        imageVector = if (isHomeSelected) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = "HOME",
                        tint = homeColor,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "HOME",
                        color = homeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // 2. CENTER VOICE ENTRY ACTION BUTTON (Replaces old RECORDS)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .clickable {
                            VoiceTooltipManager.dismiss(context)
                            navController.navigate("chat?autoVoice=true")
                        }
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .scale(pulseScale)
                            .size(42.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF0284C7),
                                        Color(0xFF6366F1),
                                        Color(0xFF9333EA)
                                    )
                                ),
                                CircleShape
                            )
                            .border(
                                1.5.dp,
                                Color(0xFF38BDF8).copy(alpha = glowAlpha),
                                CircleShape
                            )
                            .shadow(8.dp, CircleShape, spotColor = Color(0xFF38BDF8))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Voice Entry",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "VOICE ENTRY",
                        color = Color(0xFF38BDF8),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                // 3. REPORTS TAB
                val isReportsSelected = currentRoute == Screen.Reports.route
                val reportsColor = if (isReportsSelected) Color.White else Color.White.copy(alpha = 0.45f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            navController.navigate(Screen.Reports.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(vertical = 8.dp, horizontal = 18.dp)
                ) {
                    Icon(
                        imageVector = if (isReportsSelected) Icons.Filled.PieChart else Icons.Outlined.PieChart,
                        contentDescription = "REPORTS",
                        tint = reportsColor,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "REPORTS",
                        color = reportsColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

