package com.minhas.teller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.minhas.teller.navigation.Screen
import com.minhas.teller.utils.AppTheme
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.ui.components.TellerTopAppBar

// Card background: very dark navy, matching screenshot
private val CardBg       = Color(0xFF0D1240)
private val CardBorder   = Color(0xFF2A3580)
// Icon circle gradients — each card gets its own icon color (blue/teal/gold tones)
private val IconBlue     = Brush.radialGradient(listOf(Color(0xFF4FC3F7), Color(0xFF0277BD)))
private val IconGold     = Brush.radialGradient(listOf(Color(0xFFFFD54F), Color(0xFFF57F17)))
private val IconTeal     = Brush.radialGradient(listOf(Color(0xFF4DB6AC), Color(0xFF00695C)))
private val IconGreen    = Brush.radialGradient(listOf(Color(0xFF81C784), Color(0xFF2E7D32)))
private val IconPurple   = Brush.radialGradient(listOf(Color(0xFFCE93D8), Color(0xFF6A1B9A)))
private val IconOrange   = Brush.radialGradient(listOf(Color(0xFFFFAB40), Color(0xFFE65100)))
private val IconRed      = Brush.radialGradient(listOf(Color(0xFFEF9A9A), Color(0xFFC62828)))
private val IconCyan     = Brush.radialGradient(listOf(Color(0xFF80DEEA), Color(0xFF00838F)))

data class DisplayOptionItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val iconGradient: Brush,
    val iconTint: Color = Color.White
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayOptionsScreen(navController: NavController, currentAppTheme: AppTheme) {
    val currentLanguage by TerminologyManager.currentLanguage.collectAsState()

    val items = listOf(
        DisplayOptionItem(TerminologyManager.getTerm("Daybook"),           Icons.Default.DateRange,                     Screen.Daybook.route,             IconBlue),
        DisplayOptionItem(TerminologyManager.getTerm("Account Books"),     Icons.AutoMirrored.Filled.MenuBook,          Screen.AccountBooks.route,        IconGold),
        DisplayOptionItem(TerminologyManager.getTerm("Statement of A/c"),  Icons.Default.Description,                   Screen.StatementOfAccounts.route, IconTeal),
        DisplayOptionItem(TerminologyManager.getTerm("Inventory Books"),   Icons.Default.Inventory,                     Screen.InventoryBooks.route,      IconGreen),
        DisplayOptionItem(TerminologyManager.getTerm("Item Profitability"),Icons.AutoMirrored.Filled.TrendingUp,        Screen.ItemProfitability.route,   IconOrange),
        DisplayOptionItem(TerminologyManager.getTerm("Receipts/Payments"), Icons.Default.SwapHoriz,                     Screen.ReceiptsAndPayments.route, IconPurple),
        DisplayOptionItem(TerminologyManager.getTerm("Audit Trail"),       Icons.Default.History,                       Screen.AuditLog.route,            IconRed),
        DisplayOptionItem(TerminologyManager.getTerm("Auto-Bank Recon"),   Icons.Default.AccountBalance,                Screen.BankReconciliation.route,  IconCyan)
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = TerminologyManager.getTerm("Display Options"),
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF060B2C), Color(0xFF0A1045), Color(0xFF060B2C))
                    )
                )
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(items) { item ->
                    DisplayOptionCard(item = item, onClick = { navController.navigate(item.route) })
                }
            }
        }
    }
}

@Composable
private fun DisplayOptionCard(item: DisplayOptionItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp), ambientColor = Color(0xFF3A4DB0), spotColor = Color(0xFF3A4DB0))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF121A54), Color(0xFF0A1040))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color(0xFF4A5BBE), Color(0xFF1E2760), Color(0xFF4A5BBE))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing circular icon container
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        ambientColor = Color(0xFF5C6BC0),
                        spotColor = Color(0xFF5C6BC0)
                    )
                    .clip(CircleShape)
                    .background(item.iconGradient)
                    .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )
        }
    }
}
