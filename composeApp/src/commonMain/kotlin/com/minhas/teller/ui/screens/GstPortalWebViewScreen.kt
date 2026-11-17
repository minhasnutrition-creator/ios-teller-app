package com.minhas.teller.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.minhas.teller.PlatformUtils

/**
 * In-app screen for GST portal access.
 * On iOS, portals open in Safari for secure authentication and file handling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GstPortalWebViewScreen(
    navController: NavHostController,
    url: String,
    title: String
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                    text = "GST Portal: $title",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "For security and proper certificate handling on iOS, please open the portal in Safari.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { PlatformUtils.openUrl(url) }
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open in Safari")
                }
            }
        }
    }
}
