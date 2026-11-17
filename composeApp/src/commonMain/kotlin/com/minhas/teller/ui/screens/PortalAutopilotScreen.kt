package com.minhas.teller.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.minhas.teller.PlatformUtils

/**
 * Government Portal Autopilot.
 * On iOS, directs the user to the portal via Safari.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalAutopilotScreen(navController: NavHostController) {
    val inbox = navController.previousBackStackEntry?.savedStateHandle
    val targetUrl = remember { inbox?.get<String>("portal_url") ?: "https://einvoice1.gst.gov.in/Account/Login" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Government Portal Autopilot") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "e-Invoice & GST Portal",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "On iOS, government bulk upload and filing is opened directly in Safari.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { PlatformUtils.openUrl(targetUrl) }
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Portal in Safari")
                }
            }
        }
    }
}
