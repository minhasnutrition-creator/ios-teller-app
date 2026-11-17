package com.minhas.teller.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.utils.TerminologyManager

// ─────────────────────────────────────────────
// Installation Guide — dead-simple, for everyone
// ─────────────────────────────────────────────

/**
 * A single step in the installation guide.
 * @param emoji Big emoji shown as the step icon
 * @param title What to do (1-4 words, Hinglish OK)
 * @param whatYouSee Description of what's on screen
 * @param whatYouDo Action instruction (very simple)
 * @param phoneContent Builder for the phone mockup visual
 */
data class InstallStep(
    val emoji: String,
    val title: String,
    val whatYouSee: String,
    val whatYouDo: String,
    val warning: String = "" // optional extra callout, e.g. Play Protect text
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallGuideScreen(navController: NavController) {
    val steps = remember {
        listOf(
            InstallStep(
                emoji = "📨",
                title = "You got a message",
                whatYouSee = "Your boss or manager sent you a message on WhatsApp (or ShareChat / Bluetooth). It has a file attached — that file is the app.",
                whatYouDo = "Open the chat. You will see a file with a little Android robot icon. That is the app.",
                warning = "The file name ends with .apk — that means it is an Android app."
            ),
            InstallStep(
                emoji = "👆",
                title = "Tap the file",
                whatYouSee = "Inside the chat, you will see the file with the name \"Teller Team\" or \"Teller Tasks\". Below it, it says \"APK\" or shows a download icon.",
                whatYouDo = "Just tap once on the file. Your phone will ask: \"Do you want to install this app?\"",
                warning = "If it says \"Download\" first, tap Download. After it finishes, tap the file again."
            ),
            InstallStep(
                emoji = "⚙️",
                title = "Allow Install",
                whatYouSee = "Your phone might say: \"For your security, your phone is not allowed to install unknown apps from this source.\"",
                whatYouDo = "Tap the \"Settings\" button that appears. Then turn ON the switch that says \"Allow from this source\". Then press the Back button to return to the install.",
                warning = "This is normal! Every phone does this. Only apps from Play Store can install without this step. Turning it ON is safe for our app."
            ),
            InstallStep(
                emoji = "🛡️",
                title = "If you see a warning",
                whatYouSee = "Google Play Protect might show a popup: \"Blocked by Play Protect — This app can collect data that could be used to track you.\"",
                whatYouDo = "DO NOT PANIC! Tap on \"More details\" at the bottom. Then tap \"Install anyway\" at the bottom right. That's it!",
                warning = "Why this happens: Because our app is not on Play Store, Google doesn't recognise it yet. It is 100% SAFE — it's made by your boss for your shop work."
            ),
            InstallStep(
                emoji = "📲",
                title = "Tap Install",
                whatYouSee = "Now you will see the real install screen. It shows the app name, and a big \"Install\" button at the bottom.",
                whatYouDo = "Tap the big \"Install\" button. Wait 10-20 seconds. When it says \"App installed\", tap \"Open\" or \"Done\".",
                warning = "If nothing happens after tapping, your phone storage might be full. Ask your boss for help."
            ),
            InstallStep(
                emoji = "🔢",
                title = "Enter the Code",
                whatYouSee = "When you open the app for the first time, it will ask for a \"10-character code\" or \"Employee code\".",
                whatYouDo = "Your boss will give you this code. Type the code. Tap \"Connect\" or \"Join\". Done! Now you can see your tasks and update stock.",
                warning = "If the code doesn't work, tell your boss. They can find the code in the main app under Daily Tasks or Live Stock."
            )
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(modifier = Modifier.statusBarsPadding().padding(top = 8.dp)) {
                TellerTopAppBar(
                    title = TerminologyManager.getTerm("📱 How to Install the App"),
                    canNavigateBack = true,
                    navigateUp = { navController.navigateUp() },
                    showHome = true,
                    navController = navController
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.3f)),
                border = BorderStroke(1.5.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("👋", fontSize = 32.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            TerminologyManager.getTerm("Even if you have never installed an app before — you CAN do this."),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            TerminologyManager.getTerm("Follow the 6 steps below. Each step has a picture to help you."),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Scrollable steps
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                steps.forEachIndexed { index, step ->
                    InstallStepCard(
                        stepNumber = index + 1,
                        step = step
                    )
                }

                // Final encouragement card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D47A1).copy(alpha = 0.25f)),
                    border = BorderStroke(1.5.dp, Color(0xFF42A5F5).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎉", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            TerminologyManager.getTerm("You are now connected!"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            TerminologyManager.getTerm("Your boss can now send you tasks, check stock, and track work — all from their phone to yours."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            TerminologyManager.getTerm("If anything goes wrong, just ask your boss. They can help you! 🤝"),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF90CAF9),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallStepCard(stepNumber: Int, step: InstallStep) {
    val stepColors = listOf(
        Color(0xFF4CAF50), // Step 1 - Green
        Color(0xFF2196F3), // Step 2 - Blue
        Color(0xFFFF9800), // Step 3 - Orange
        Color(0xFFF44336), // Step 4 - Red
        Color(0xFF9C27B0), // Step 5 - Purple
        Color(0xFF00897B)  // Step 6 - Teal
    )
    val accentColor = stepColors[(stepNumber - 1) % stepColors.size]

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Step Header: Number + Title ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Big number circle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$stepNumber",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${TerminologyManager.getTerm("STEP")} $stepNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = TerminologyManager.getTerm(step.title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(step.emoji, fontSize = 40.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Phone Mockup ──
            PhoneMockup(
                accentColor = accentColor,
                stepNumber = stepNumber,
                emoji = step.emoji,
                title = step.title
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── "What you see" box ──
            InfoRow(
                icon = Icons.Default.Visibility,
                iconTint = Color(0xFF90CAF9),
                label = TerminologyManager.getTerm("What you will see on screen:"),
                text = TerminologyManager.getTerm(step.whatYouSee)
            )
            Spacer(modifier = Modifier.height(10.dp))

            // ── "What you do" box ──
            InfoRow(
                icon = Icons.Default.TouchApp,
                iconTint = Color(0xFFA5D6A7),
                label = TerminologyManager.getTerm("What YOU have to do:"),
                text = TerminologyManager.getTerm(step.whatYouDo)
            )

            // ── Warning / Extra note ──
            if (step.warning.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (stepNumber == 4) Color(0xFFB71C1C).copy(alpha = 0.2f)
                                         else Color(0xFFFFD54F).copy(alpha = 0.1f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (stepNumber == 4) Color(0xFFEF5350).copy(alpha = 0.5f)
                        else Color(0xFFFFD54F).copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            if (stepNumber == 4) "⚠️" else "💡",
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = TerminologyManager.getTerm(step.warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = iconTint,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )
        }
    }
}

// ──────────────────────────────────────────────────────
// Phone Mockup — miniature Compose screens that look like
// the REAL thing the user will see on their phone.
// No more abstract blue boxes with lines.
// ──────────────────────────────────────────────────────

@Composable
private fun PhoneMockup(
    accentColor: Color,
    stepNumber: Int,
    emoji: String,
    title: String
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Outer phone body
        Surface(
            modifier = Modifier.width(200.dp).height(340.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF2C2C3A),
            border = BorderStroke(2.dp, Color(0xFF555566))
        ) {
            // Screen area with padding (bezels)
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFF5F5F5) // Light phone background
            ) {
                when (stepNumber) {
                    1 -> ChatMockup(accentColor)
                    2 -> TapFileMockup(accentColor)
                    3 -> SettingsToggleMockup(accentColor)
                    4 -> PlayProtectMockup(accentColor)
                    5 -> InstallMockup(accentColor)
                    6 -> CodeEntryMockup(accentColor)
                }
            }
        }
    }
}

// ── Step 1: WhatsApp chat with APK file ──

@Composable
private fun ChatMockup(accent: Color) {
    Column(modifier = Modifier.fillMaxSize()) {
        // WhatsApp green top bar
        Surface(color = Color(0xFF075E54)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(TerminologyManager.getTerm("Boss (Manager)"), fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }
        // Chat messages
        Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            // Sent message
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    color = Color(0xFFDCF8C6), shape = RoundedCornerShape(6.dp, 6.dp, 0.dp, 6.dp),
                    modifier = Modifier.widthIn(max = 140.dp)
                ) {
                    Text(TerminologyManager.getTerm("Here is the app for your phone 📱"), fontSize = 7.sp, modifier = Modifier.padding(6.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // APK file message bubble (THE IMPORTANT ONE)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Surface(
                    color = Color(0xFFFFFFFF), shape = RoundedCornerShape(6.dp, 6.dp, 6.dp, 0.dp),
                    border = BorderStroke(0.5.dp, Color(0xFFE0E0E0)),
                    modifier = Modifier.widthIn(max = 150.dp)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Android robot icon
                        Surface(
                            color = Color(0xFF4CAF50), shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🤖", fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(TerminologyManager.getTerm("Teller Team.apk"), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            Text(TerminologyManager.getTerm("APK · 8.2 MB"), fontSize = 7.sp, color = Color.Gray)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            // Yellow arrow pointing at the file bubble
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 0.dp)
            ) {
                Text("👆", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(2.dp))
                Text(TerminologyManager.getTerm("Tap this file!"), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF57C00))
            }
        }
    }
}

// ── Step 2: File download / tap to install ──

@Composable
private fun TapFileMockup(accent: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // File manager style header
        Text(TerminologyManager.getTerm("Downloads"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))
        // The APK file card — big and obvious
        Surface(
            color = Color.White, shape = RoundedCornerShape(12.dp),
            border = BorderStroke(2.dp, accent),
            modifier = Modifier.width(150.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Android icon
                Surface(
                    color = accent.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("📦", fontSize = 24.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(TerminologyManager.getTerm("Teller Team"), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(TerminologyManager.getTerm("APK file"), fontSize = 7.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(color = accent, shape = RoundedCornerShape(4.dp)) {
                    Text(TerminologyManager.getTerm("TAP TO INSTALL"), fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Instruction
        Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(6.dp)) {
            Text(TerminologyManager.getTerm("👆 Tap the file once"), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100), modifier = Modifier.padding(6.dp))
        }
    }
}

// ── Step 3: Settings — Allow from this source ──

@Composable
private fun SettingsToggleMockup(accent: Color) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Settings-style blue top bar
        Surface(color = Color(0xFF1565C0)) {
            Row(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(TerminologyManager.getTerm("Install unknown apps"), fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Warning card
            Surface(color = Color(0xFFFFF8E1), shape = RoundedCornerShape(6.dp)) {
                Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.Top) {
                    Text("⚠️", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(TerminologyManager.getTerm("Your phone is not allowed to install apps from this source."), fontSize = 7.sp, color = Color(0xFF795548))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // "Allow from this source" toggle row (KEY ELEMENT)
            Surface(color = Color.White, shape = RoundedCornerShape(8.dp), border = BorderStroke(2.dp, Color(0xFFFFC107))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(TerminologyManager.getTerm("Allow from this source"), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(TerminologyManager.getTerm("Turn this ON to install apps"), fontSize = 7.sp, color = Color.Gray)
                    }
                    // Toggle switch showing ON
                    Surface(color = Color(0xFF4CAF50), shape = RoundedCornerShape(10.dp), modifier = Modifier.width(32.dp).height(18.dp)) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                            Box(modifier = Modifier
                                .size(14.dp).offset(x = (-2).dp)
                                .clip(CircleShape).background(Color.White))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            // Arrow + instruction
            Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp)) {
                Text(TerminologyManager.getTerm("👇 Turn this switch ON"), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), modifier = Modifier.padding(6.dp))
            }
        }
    }
}

// ── Step 4: Play Protect warning ──

@Composable
private fun PlayProtectMockup(accent: Color) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Red danger top bar
        Surface(color = Color(0xFFC62828)) {
            Row(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = Color.White, modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(TerminologyManager.getTerm("Google Play Protect"), fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            // Big shield with exclamation
            Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(40.dp), modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("🛡️", fontSize = 28.sp) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(TerminologyManager.getTerm("Blocked by Play Protect"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
            Spacer(modifier = Modifier.height(4.dp))
            Text(TerminologyManager.getTerm("This app can collect data that could be used to track you."), fontSize = 7.sp, color = Color.DarkGray, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
            // "More details" link — THIS IS WHAT THEY TAP FIRST
            Surface(color = Color(0xFFE3F2FD), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.5.dp, Color(0xFF2196F3))) {
                Text(TerminologyManager.getTerm("▼  More details"), fontSize = 8.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Arrow pointing to "More details"
            Text(TerminologyManager.getTerm("👆 Tap here first"), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF6F00))
            Spacer(modifier = Modifier.height(12.dp))
            // "Install anyway" — THE FINAL STEP
            Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp), border = BorderStroke(2.dp, Color(0xFF4CAF50))) {
                Text(TerminologyManager.getTerm("INSTALL ANYWAY →"), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(TerminologyManager.getTerm("Then tap this"), fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
        }
    }
}

// ── Step 5: Install screen ──

@Composable
private fun InstallMockup(accent: Color) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Surface(color = Color(0xFF37474F)) {
            Row(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(TerminologyManager.getTerm("App installer"), fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            // App icon
            Surface(color = accent.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("📦", fontSize = 26.sp) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(TerminologyManager.getTerm("Teller Team"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(TerminologyManager.getTerm("Do you want to install this app?"), fontSize = 7.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(10.dp))
            // Permissions list
            repeat(3) {
                Surface(color = Color.White, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(8.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(listOf("Storage access", "Network access", "No special permissions")[it], fontSize = 7.sp, color = Color.DarkGray)
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            // BIG GREEN INSTALL BUTTON — the focal point
            Surface(color = Color(0xFF4CAF50), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(TerminologyManager.getTerm("INSTALL"), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Arrow pointing to button
            Text(TerminologyManager.getTerm("👆 Tap this big green button"), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE65100))
            Spacer(modifier = Modifier.height(4.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(6.dp)) {
                Text(TerminologyManager.getTerm("Cancel"), fontSize = 8.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
        }
    }
}

// ── Step 6: Enter the employee code ──

@Composable
private fun CodeEntryMockup(accent: Color) {
    Column(modifier = Modifier.fillMaxSize()) {
        // App header
        Surface(color = accent) {
            Row(modifier = Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(TerminologyManager.getTerm("Teller Team"), fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("🔢", fontSize = 36.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(TerminologyManager.getTerm("Enter Employee Code"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.height(2.dp))
            Text(TerminologyManager.getTerm("Ask your boss for this 10-character code"), fontSize = 7.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))
            // 6 digit boxes
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 1..6) {
                    val filled = i <= 4
                    Surface(
                        color = if (filled) accent.copy(alpha = 0.15f) else Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(if (filled) 1.5.dp else 1.dp, if (filled) accent else Color(0xFFBDBDBD)),
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (filled) {
                                Text("●", fontSize = 10.sp, color = accent)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            // Connect button — what they need to tap
            Surface(color = accent, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(32.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(TerminologyManager.getTerm("CONNECT"), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(TerminologyManager.getTerm("👆 Type the code, then tap CONNECT"), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE65100))
        }
    }
}
