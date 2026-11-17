package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.entities.LedgerWithBalance
import com.minhas.teller.ui.viewmodels.LedgerViewModel
import com.minhas.teller.ui.viewmodels.LedgerViewModelFactory
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.minhas.teller.navigation.Screen
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.utils.paiseToRupees
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashBankBooksScreen(navController: NavHostController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val repository = LedgerRepository(database.ledgerDao())
    val viewModel: LedgerViewModel = viewModel(factory = LedgerViewModelFactory(repository))
    val allLedgers by viewModel.allLedgers.collectAsState()

    val cashBankLedgers = remember(allLedgers) {
        allLedgers.filter { it.group == "Cash-in-hand" || it.group == "Bank Accounts" }
    }

    val ledgersWithBalances = cashBankLedgers.map { ledger ->
        val balance by viewModel.getClosingBalance(ledger.id, Date()).collectAsState(initial = ledger.openingBalance)
        LedgerWithBalance(ledger, balance ?: ledger.openingBalance)
    }

    Scaffold(
        topBar = {
            TellerTopAppBar(
                title = "Cash/Bank Books",
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp, vertical = 16.dp)
        ) {
            if (ledgersWithBalances.isEmpty()) {
                Text(TerminologyManager.getTerm("No Cash or Bank ledgers found."))
            } else {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(TerminologyManager.getTerm("Ledger Name"), modifier = Modifier.weight(3f).padding(8.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                    Text(TerminologyManager.getTerm("Group"), modifier = Modifier.weight(2f).padding(8.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 12.sp)
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                    Text(TerminologyManager.getTerm("Balance"), modifier = Modifier.weight(2f).padding(8.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.End, fontSize = 12.sp)
                }

                LazyColumn(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline)) {
                    items(ledgersWithBalances) { lwb ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.navigate(Screen.LedgerViewReport.route.replace("{ledgerId}", lwb.ledger.id.toString()))
                                    }
                                    .height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = lwb.ledger.name,
                                    modifier = Modifier.weight(3f).padding(8.dp),
                                    fontSize = 12.sp
                                )
                                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                                Text(
                                    text = lwb.ledger.group,
                                    modifier = Modifier.weight(2f).padding(8.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 11.sp
                                )
                                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                                val bal = lwb.balance.paiseToRupees()
                                val balanceText = if (bal >= 0) "%.2f Dr".format(bal) else "%.2f Cr".format(Math.abs(bal))
                                Text(
                                    text = balanceText,
                                    modifier = Modifier.weight(2f).padding(8.dp),
                                    textAlign = TextAlign.End,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (bal >= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}
