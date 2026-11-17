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

/**
 * A single step in the iOS installation guide.
 */
data class InstallStep(
    val emoji: String,
    val title: String,
    val whatYouSee: String,
    val whatYouDo: String,
    val warning: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallGuideScreen(navController: NavController) {
    val steps = remember {
        listOf(
            InstallStep(
                emoji = "📱",
                title = "Open App Store",
                whatYouSee = "On the employee's iPhone or iPad, find the blue App Store icon on the home screen.",
                whatYouDo = "Tap the App Store icon to open it. Then tap the Search tab at the bottom right corner.",
                warning = "Make sure the iPhone is connected to Wi-Fi or mobile data."
            ),
            InstallStep(
                emoji = "🔍",
                title = "Search App Name",
                whatYouSee = "A search bar appears at the top of the App Store.",
                whatYouDo = "Type 'Task and Stock for Teller' in the search bar and press Search on the keyboard.",
                warning = "The app is completely FREE to download. No payment or credit card is needed."
            ),
            InstallStep(
                emoji = "☁️",
                title = "Tap 'GET' to Install",
                whatYouSee = "You will see 'Task and Stock for Teller' with the blue Teller logo and a 'GET' button.",
                whatYouDo = "Tap the 'GET' button. Confirm with Face ID, Touch ID, or Apple ID password. The app will download in seconds.",
                warning = "Once installed, the button changes to 'OPEN' or appears on the home screen."
            ),
            InstallStep(
                emoji = "🔢",
                title = "Enter Employee Code",
                whatYouSee = "When 'Task and Stock for Teller' opens for the first time, it asks for your Employee Code.",
                whatYouDo = "Enter the 10-character code provided by your manager (from Daily Tasks or Live Stock) and tap 'Connect'.",
                warning = "Done! The employee's phone is now securely connected to your store's live tasks and stock."
            )
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(modifier = Modifier.statusBarsPadding().padding(top = 8.dp)) {
                TellerTopAppBar(
                    title = TerminologyManager.getTerm("📱 How to Install on iPhone"),
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.25f)),
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
                            "Install 'Task and Stock for Teller' on any employee iPhone.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "It is free on the Apple App Store. Follow the 4 simple steps below.",
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
                            "Employees are Instantly Connected!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "They can now view tasks, record stock updates, and submit daily work reports directly to your Teller app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallStepCard(
    stepNumber: Int,
    step: InstallStep
) {
    val accentColor = when (stepNumber) {
        1 -> Color(0xFF42A5F5) // Blue
        2 -> Color(0xFF66BB6A) // Green
        3 -> Color(0xFFFFA726) // Orange
        else -> Color(0xFFAB47BC) // Purple
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.85f)),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$stepNumber",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${step.emoji} ${step.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // What you see
            InfoRow(
                icon = Icons.Default.Visibility,
                iconTint = Color(0xFF90CAF9),
                label = "What you see on the iPhone:",
                text = step.whatYouSee
            )

            Spacer(modifier = Modifier.height(10.dp))

            // What you do
            InfoRow(
                icon = Icons.Default.TouchApp,
                iconTint = Color(0xFFA5D6A7),
                label = "What to do:",
                text = step.whatYouDo
            )

            // Warning / Note
            if (step.warning.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF00838F).copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, Color(0xFF4DD0E1).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("💡", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = step.warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
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
