package com.minhas.teller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.minhas.teller.navigation.Screen
import com.minhas.teller.utils.TerminologyManager

/**
 * Small two-way tab shown at the top of the Accounts Info lists:
 * "Customers/Companies" (ledgers) | "Groups". Switching replaces the current screen,
 * so Back still returns to wherever the user came from.
 */
@Composable
fun AccountsTabToggle(
    navController: NavHostController,
    ledgersSelected: Boolean,
    unselectedTextColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val selectedColor = Color(0xFF00897B)
    fun switchTo(target: String) {
        val current = navController.currentBackStackEntry?.destination?.route
        navController.navigate(target) {
            if (current != null) popUpTo(current) { inclusive = true }
            launchSingleTop = true
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            Triple(TerminologyManager.getTerm("Ledgers"), true, Screen.LedgerList.route),
            Triple(TerminologyManager.getTerm("Groups"), false, Screen.GroupList.route)
        ).forEach { (label, isLedgerTab, route) ->
            val selected = isLedgerTab == ledgersSelected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (selected) selectedColor else Color.Transparent)
                    .clickable(enabled = !selected) { switchTo(route) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) Color.White else unselectedTextColor,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
