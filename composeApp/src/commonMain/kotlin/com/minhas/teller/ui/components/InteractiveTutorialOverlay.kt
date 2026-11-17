package com.minhas.teller.ui.components

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.minhas.teller.utils.TutorialManager
import com.minhas.teller.utils.TutorialStep

@Composable
fun InteractiveTutorialOverlay(
    currentStep: TutorialStep,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    // This overlay handles the full-screen dimming and text
    // The specific UI highlighting logic might be handled by the parent screen passing in specific coordinates
    // OR we just use a generic centered dialog for some steps.

    if (currentStep == TutorialStep.WELCOME) {
        Dialog(onDismissRequest = { /* Prevent dismiss */ }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(TerminologyManager.getTerm("Welcome to Teller!"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(TerminologyManager.getTerm("Let's take a quick interactive tour to show you how to:\n\n1. Create & Manage Vouchers\n2. Track Borrowed Money\n3. View Reports\n4. Use the AI Finance Assistant"),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                        Text(TerminologyManager.getTerm("Start Tour"))
                    }
                    TextButton(onClick = onSkip) {
                        Text(TerminologyManager.getTerm("Skip"))
                    }
                }
            }
        }
    } else if (currentStep == TutorialStep.VOUCHER_CREATION_DEMO) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.3f)).clickable(enabled=false){},
            contentAlignment = Alignment.Center
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(TerminologyManager.getTerm("Interactive Demo Running..."), fontWeight = FontWeight.Bold)
                    Text(TerminologyManager.getTerm("Creating and deleting a test voucher."))
                }
            }
        }
    } else if (currentStep == TutorialStep.AI_CHAT_INTRO) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.7f)).clickable(enabled=false){},
            contentAlignment = Alignment.BottomCenter
        ) {
             Card(modifier = Modifier.padding(32.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(TerminologyManager.getTerm("AI Assistant"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(TerminologyManager.getTerm("Tap the Microphone icon or wait for the simulation to start."))
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.End)) {
                        Text(TerminologyManager.getTerm("Skip"))
                    }
                }
            }
        }
    } else if (currentStep == TutorialStep.AI_SHOW_DRAFT) {
         Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.7f)).clickable(enabled=false){},
            contentAlignment = Alignment.BottomCenter
        ) {
             Card(modifier = Modifier.padding(32.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(TerminologyManager.getTerm("Review Draft"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(TerminologyManager.getTerm("The AI created this draft. In a real scenario, you would tap 'Confirm'. For this demo, we will finish here."))
                }
            }
        }
    }
    
    // For other steps, we might render a Box over the screen
    // logic depends on where this component is placed.
    // If placed at the root of a screen, it can overlay everything.
}

@Composable
fun TutorialTooltip(
    text: String,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    alignBottom: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = false) {} // Absorb clicks
    ) {
        Card(
            modifier = Modifier
                .align(if (alignBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(32.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onNext, modifier = Modifier.align(Alignment.End)) {
                    Text(TerminologyManager.getTerm("Next / Got it"))
                }
            }
        }
    }
}
