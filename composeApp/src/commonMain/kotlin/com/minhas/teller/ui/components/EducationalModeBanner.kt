package com.minhas.teller.ui.components

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EducationalModeBanner(
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
    showCompact: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (showCompact) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Educational Mode",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(onClick = onUpgrade) { Text(TerminologyManager.getTerm("Upgrade")) }
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Upgrade,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "EDUCATIONAL MODE - Tap to Upgrade",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(TerminologyManager.getTerm("EDUCATIONAL MODE"), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(TerminologyManager.getTerm("Your 30-day trial has ended. Upgrade to continue using all features."))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(TerminologyManager.getTerm("Restrictions:"), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    RestrictionItem("❌ Cannot create new voucher entries", isAllowed = false)
                    RestrictionItem("❌ Cannot export reports to Excel/PDF", isAllowed = false)
                    RestrictionItem("❌ Cannot backup your data", isAllowed = false)
                    RestrictionItem("❌ AI Assistant disabled", isAllowed = false)
                    Spacer(modifier = Modifier.height(8.dp))
                    RestrictionItem("✅ View existing entries (allowed)", isAllowed = true)
                    RestrictionItem("✅ View reports (allowed)", isAllowed = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        onUpgrade()
                    }
                ) {
                    Text(TerminologyManager.getTerm("Upgrade"))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDialog = false }) { Text(TerminologyManager.getTerm("Dismiss")) }
            }
        )
    }
}

@Composable
private fun RestrictionItem(text: String, isAllowed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isAllowed) Icons.Default.CheckCircle else Icons.Default.Block,
            contentDescription = null,
            tint = if (isAllowed) Color(0xFF2E7D32) else Color(0xFFC62828),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text)
    }
}

