package com.minhas.teller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.theme.AccountsGradient
import com.minhas.teller.ui.theme.MainBackgroundGradient
import com.minhas.teller.ui.theme.ReportsGradient
import com.minhas.teller.ui.theme.VoucherGradient
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.utils.TerminologyManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.minhas.teller.ui.components.TellerTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountBooksScreen(navController: NavController, currentAppTheme: AppTheme) { // Added theme parameter
    val currentLanguage by TerminologyManager.currentLanguage.collectAsState()

    val items = listOf(
        ModernDashboardAction(TerminologyManager.getTerm("Sales Register"), TerminologyManager.getTerm("Monthly Sales"), Icons.Default.ShoppingCart, Screen.SalesRegister.route, VoucherGradient),
        ModernDashboardAction(TerminologyManager.getTerm("Purchase Register"), TerminologyManager.getTerm("Monthly Purchase"), Icons.Default.ShoppingBag, Screen.PurchaseRegister.route, VoucherGradient),
        ModernDashboardAction(TerminologyManager.getTerm("Journal Register"), TerminologyManager.getTerm("Journal Vouchers"), Icons.Default.Book, Screen.JournalRegister.route, VoucherGradient),
        ModernDashboardAction(TerminologyManager.getTerm("Ledger Accounts"), TerminologyManager.getTerm("List of Accounts"), Icons.Default.AccountBalanceWallet, Screen.ListOfAccounts.route, AccountsGradient),
        ModernDashboardAction(TerminologyManager.getTerm("Group Summary"), TerminologyManager.getTerm("Group wise report"), Icons.Default.Group, Screen.GroupSummary.route, AccountsGradient),
        ModernDashboardAction(TerminologyManager.getTerm("Cash/Bank Books"), TerminologyManager.getTerm("Cash/Bank Summary"), Icons.Default.AttachMoney, Screen.CashBankBooks.route, ReportsGradient)
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = TerminologyManager.getTerm("Account Books"),
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