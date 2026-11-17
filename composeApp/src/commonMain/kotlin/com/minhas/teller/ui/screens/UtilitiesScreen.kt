package com.minhas.teller.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.utils.TerminologyManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilitiesScreen(navController: NavHostController, currentAppTheme: AppTheme) {
    val currentLanguage by TerminologyManager.currentLanguage.collectAsState()

    val items = listOf(
        ModernDashboardAction(
            title = TerminologyManager.getTerm("Thermal POS"),
            subtitle = TerminologyManager.getTerm("Bluetooth Receipts"),
            icon = Icons.Default.Print,
            route = Screen.ThermalPrinter.route,
            gradient = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0091EA)))
        ),
        ModernDashboardAction(
            title = TerminologyManager.getTerm("Barcode Labels"),
            subtitle = TerminologyManager.getTerm("24-Up Sticker Sheets"),
            icon = Icons.Default.QrCodeScanner,
            route = Screen.BarcodeGenerator.route,
            gradient = Brush.linearGradient(listOf(Color(0xFFFF9100), Color(0xFFFF3D00)))
        ),
        ModernDashboardAction(
            title = TerminologyManager.getTerm("QR Scanner"),
            subtitle = TerminologyManager.getTerm("Scan Barcodes & Codes"),
            icon = Icons.Default.CameraAlt,
            route = Screen.QrScanner.route,
            gradient = Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFF00B0FF)))
        ),
        ModernDashboardAction(
            title = TerminologyManager.getTerm("Import & Backup"),
            subtitle = TerminologyManager.getTerm("Excel, XML, CSV import"),
            icon = Icons.Default.CloudUpload,
            route = Screen.ImportCleanup.route,
            gradient = Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF2979FF)))
        ),
        ModernDashboardAction(
            title = TerminologyManager.getTerm("Auto-Bank Recon"),
            subtitle = TerminologyManager.getTerm("Statement Match"),
            icon = Icons.Default.AccountBalance,
            route = Screen.BankReconciliation.route,
            gradient = Brush.linearGradient(listOf(Color(0xFF1DE9B6), Color(0xFF00B0FF)))
        ),
        ModernDashboardAction(
            title = TerminologyManager.getTerm("PC Companion"),
            subtitle = TerminologyManager.getTerm("Connect PC Browser"),
            icon = Icons.Default.Laptop,
            route = Screen.DesktopCompanion.route,
            gradient = Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
        )
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = TerminologyManager.getTerm("Utilities"),
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                ThemeAwareReportsActionCard(item, navController, currentAppTheme)
            }
        }
    }
}
