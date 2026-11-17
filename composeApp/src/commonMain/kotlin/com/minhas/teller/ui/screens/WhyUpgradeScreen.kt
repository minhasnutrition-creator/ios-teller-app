package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.AutoShrinkText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhyUpgradeScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText("Why Upgrade?") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FeatureCard(
                    title = "Unlimited Companies",
                    iconColor = Color(0xFF2196F3),
                    icon = Icons.Default.Business,
                    items = listOf("Create unlimited companies", "Switch between companies easily", "Separate financial data for each business")
                )
            }
            item {
                FeatureCard(
                    title = "Advanced Reports",
                    iconColor = Color(0xFF4CAF50),
                    icon = Icons.Default.Assessment,
                    items = listOf("Detailed financial statements", "Custom report generation", "Export to PDF and Excel", "Trend analysis and insights")
                )
            }
            item {
                FeatureCard(
                    title = "AI-Powered Insights",
                    iconColor = Color(0xFF9C27B0),
                    icon = Icons.Default.Psychology,
                    items = listOf("Smart tax estimation", "AI-powered financial advice", "Automated categorization", "Predictive analytics")
                )
            }
            item {
                FeatureCard(
                    title = "Priority Support",
                    iconColor = Color(0xFFFF9800),
                    icon = Icons.Default.Support,
                    items = listOf("24/7 customer support", "Faster response times", "Dedicated account manager", "Priority feature requests")
                )
            }
            item {
                FeatureCard(
                    title = "Cloud Backup",
                    iconColor = Color(0xFF00BCD4),
                    icon = Icons.Default.Cloud,
                    items = listOf("Automatic daily backups", "Google Drive integration", "Easy data restoration", "Cross-device sync")
                )
            }
            item {
                Button(
                    onClick = {
                        navController.navigate(Screen.License.route.replace("{reason}", "why_upgrade"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(TerminologyManager.getTerm("Get Premium Now"))
                }
            }
        }
    }
}

@Composable
fun FeatureCard(
    title: String,
    iconColor: Color,
    icon: ImageVector,
    items: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(item, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
