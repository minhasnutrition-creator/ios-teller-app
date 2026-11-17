package com.minhas.teller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.theme.AccountsGradient
import com.minhas.teller.ui.theme.MainBackgroundGradient
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.utils.TerminologyManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.minhas.teller.ui.components.TellerTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsInfoScreen(navController: NavHostController, currentAppTheme: AppTheme) { // Added theme parameter
    val currentLanguage by TerminologyManager.currentLanguage.collectAsState()
    
    val items = listOf(
        ModernDashboardAction(TerminologyManager.getTerm("Ledgers"), TerminologyManager.getTerm("Manage Ledgers"), Icons.Default.AccountBalanceWallet, Screen.LedgerList.route, AccountsGradient),
        ModernDashboardAction(TerminologyManager.getTerm("Groups"), TerminologyManager.getTerm("Manage Account Groups"), Icons.Default.Group, Screen.GroupList.route, AccountsGradient)
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = TerminologyManager.getTerm("Accounts Info"),
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
