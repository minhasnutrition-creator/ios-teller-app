package com.minhas.teller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.theme.InventoryGradient
import com.minhas.teller.ui.theme.MainBackgroundGradient
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.utils.TerminologyManager
import androidx.compose.ui.graphics.Color
import com.minhas.teller.ui.components.TellerTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryBooksScreen(navController: NavHostController, currentAppTheme: AppTheme) { // Added theme parameter
    val items = listOf(
        ModernDashboardAction(TerminologyManager.getTerm("Stock Summary"), TerminologyManager.getTerm("View Stock Summary"), Icons.Default.Inventory, Screen.StockSummaryReport.route, InventoryGradient),
        ModernDashboardAction(TerminologyManager.getTerm("Stock Items"), TerminologyManager.getTerm("View Stock Items"), Icons.Default.Category, Screen.StockItemList.route, InventoryGradient)
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = TerminologyManager.getTerm("Inventory Books"),
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
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                ThemeAwareReportsActionCard(item, navController, currentAppTheme)
            }
        }
    }
}
