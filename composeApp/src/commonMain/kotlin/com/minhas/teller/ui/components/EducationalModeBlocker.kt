package com.minhas.teller.ui.components

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@Composable
fun EducationalModeBlocker(
    featureName: String,
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TerminologyManager.getTerm("FEATURE LOCKED"), fontWeight = FontWeight.Bold) },
        text = { Text("\"$featureName\" is not available in Educational Mode.\n\nYour 30-day trial has ended. Upgrade to continue using all features.") },
        confirmButton = { Button(onClick = onUpgrade) { Text(TerminologyManager.getTerm("Upgrade")) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(TerminologyManager.getTerm("Dismiss")) } }
    )
}

