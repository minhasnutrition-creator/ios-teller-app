package com.minhas.teller.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.theme.AccountsGradient
import com.minhas.teller.ui.theme.LiveStockGradient
import com.minhas.teller.ui.theme.ReportsGradient
import com.minhas.teller.ui.theme.InventoryGradient
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.utils.TerminologyManager
import androidx.compose.ui.graphics.Color
import com.minhas.teller.ui.components.TellerTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingPaymentsMenuScreen(navController: NavController, currentAppTheme: AppTheme) {
    val items = listOf(
        ModernDashboardAction(
            TerminologyManager.getTerm("All Pending"),
            TerminologyManager.getTerm("View all outstanding payments"),
            Icons.AutoMirrored.Filled.List,
            Screen.OutstandingPaymentsSummary.route.replace("?group={group}", ""),
            LiveStockGradient
        ),
        ModernDashboardAction(
            TerminologyManager.getTerm("Money to Collect"),
            TerminologyManager.getTerm("All Receivables"),
            Icons.Default.ArrowCircleDown,
            Screen.OutstandingPaymentsSummary.route.replace("{group}", "Sundry Debtors"),
            ReportsGradient
        ),
        ModernDashboardAction(
            TerminologyManager.getTerm("Finance Companies"),
            TerminologyManager.getTerm("Bank/Finance Dues"),
            Icons.Default.AccountBalance,
            Screen.OutstandingPaymentsSummary.route.replace("{group}", "Finance Companies"),
            AccountsGradient
        ),
        ModernDashboardAction(
            TerminologyManager.getTerm("Bills to Pay"),
            TerminologyManager.getTerm("All Payables"),
            Icons.Default.ArrowCircleUp,
            Screen.OutstandingPaymentsSummary.route.replace("{group}", "Sundry Creditors"),
            InventoryGradient
        )
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = TerminologyManager.getTerm("Pending Payments"),
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
                .padding(horizontal = 8.dp, vertical = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                ThemeAwareReportsActionCard(item, navController, currentAppTheme)
            }
        }
    }
}
