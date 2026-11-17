package com.minhas.teller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhas.teller.utils.SecurityUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TellerTopAppBar(
    title: String,
    canNavigateBack: Boolean = false,
    navigateUp: () -> Unit = {},
    showHome: Boolean = false,
    navController: androidx.navigation.NavController? = null,
    titleColor: Color? = null,
    titleFontWeight: FontWeight? = null,
    iconColor: Color? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column {
        // Persistent security warning — visible on every screen until the user
        // addresses the issue (unlike a Toast that vanishes in 2 seconds).
        if (SecurityUtils.isCompromised) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFB71C1C),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Security warning: This device may be compromised. Financial data is at risk.",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        TopAppBar(
            // Titles come from TerminologyManager and can be long in other languages;
            // shrink to fit rather than wrapping and distorting the bar.
            title = { AutoShrinkText(title, fontWeight = titleFontWeight) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = titleColor ?: MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = iconColor ?: MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = iconColor ?: MaterialTheme.colorScheme.onSurface
            ),
            navigationIcon = {
                if (canNavigateBack) {
                    IconButton(onClick = navigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            },
            actions = {
                actions()
                if (showHome && navController != null && com.minhas.teller.SessionManager.currentCompanyId.value != null) {
                    IconButton(onClick = {
                        navController.navigate("dashboard") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home"
                        )
                    }
                }
            }
        )
    }
}