package com.minhas.teller.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minhas.teller.utils.TeamCodes
import com.minhas.teller.utils.TerminologyManager

/** Shown once after legacy 6-digit Team codes were upgraded, so the manager can send the new ones. */
@Composable
fun CodesUpgradedDialog(moved: List<TeamCodes.MovedCode>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Shield, contentDescription = null) },
        title = { Text(TerminologyManager.getTerm("Team codes upgraded"), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(TerminologyManager.getTerm("For security, your team's codes were upgraded. Send each person their code — they enter it in the 'Task and Stock for Teller' app (free on App Store)."))
                moved.forEach { m ->
                    val pretty = TeamCodes.format(m.newCode)
                    val shareText = "Your connection code for 'Task and Stock for Teller' is $pretty. Install the free app from the App Store and enter this code to connect."
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(m.name.ifBlank { m.oldCode }, fontWeight = FontWeight.SemiBold)
                            Text(pretty, fontFamily = FontFamily.Monospace)
                        }
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = TerminologyManager.getTerm("Share code") + " " + m.name)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(TerminologyManager.getTerm("Done")) } }
    )
}
