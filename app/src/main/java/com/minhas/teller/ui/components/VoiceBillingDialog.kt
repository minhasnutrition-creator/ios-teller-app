package com.minhas.teller.ui.components

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.minhas.teller.utils.AiResponse
import com.minhas.teller.utils.VoiceBillingManager
import com.minhas.teller.utils.VoiceBillingState
import kotlinx.coroutines.launch

@Composable
fun VoiceBillingDialog(
    onDismiss: () -> Unit,
    onNavigateToEdit: ((draft: AiResponse.DraftVoucher) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val manager = remember { VoiceBillingManager(context) }
    val state by manager.state.collectAsState()
    val partialText by manager.partialText.collectAsState()

    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        manager.startListening()
    }

    DisposableEffect(Unit) {
        onDispose {
            manager.stopListening()
        }
    }

    Dialog(
        onDismissRequest = {
            manager.stopListening()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable { onDismiss() }
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(enabled = false) {} // prevent dismissing on dialog click
                    .clip(RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141926)),
                border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFF2979FF)))),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Voice Billing",
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                AutoShrinkText(TerminologyManager.getTerm("AI Voice Billing"),
                                    color = Color.White,
                                    maxFontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                AutoShrinkText(TerminologyManager.getTerm("Speak in English, Hindi, or Hinglish"),
                                    color = Color.Gray,
                                    maxFontSize = 11.sp,
                                    minFontSize = 8.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                manager.stopListening()
                                onDismiss()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // Dynamic State Views
                    when (val currentState = state) {
                        is VoiceBillingState.Listening -> {
                            // Pulsing Mic Animation
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.35f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(900, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )
                            val ringAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.6f,
                                targetValue = 0.1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(900, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "alpha"
                            )

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(130.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676).copy(alpha = ringAlpha))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF00E676), Color(0xFF00B0FF))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "Listening",
                                        tint = Color.Black,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            AutoShrinkText(TerminologyManager.getTerm("Listening..."),
                                color = Color(0xFF00E676),
                                maxFontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (partialText.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "“$partialText”",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .padding(12.dp)
                                )
                            } else {
                                Spacer(Modifier.height(12.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .padding(12.dp)
                                ) {
                                    AutoShrinkText(TerminologyManager.getTerm("Try saying:"),
                                        color = Color.Gray,
                                        maxFontSize = 11.sp,
                                        minFontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(TerminologyManager.getTerm("• “Ramesh ko 5000 ka cement becha nagad”\n") +
                                        "• “Sold 10 steel pipes to Amit for 4500”\n" +
                                        "• “Paid 2000 to Electrician by Bank”",
                                        color = Color(0xFFB0BEC5),
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = {
                                    if (partialText.isNotBlank()) {
                                        manager.processSpokenText(partialText)
                                    } else {
                                        manager.stopListening()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Text(TerminologyManager.getTerm("Done Speaking"), color = Color.White, fontSize = 13.sp)
                            }
                        }

                        is VoiceBillingState.Processing -> {
                            CircularProgressIndicator(color = Color(0xFF00E676), modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(16.dp))
                            AutoShrinkText(TerminologyManager.getTerm("Analyzing transaction..."),
                                color = Color.White,
                                maxFontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (currentState.spokenText.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "“${currentState.spokenText}”",
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        is VoiceBillingState.Parsed -> {
                            val draft = currentState.draft
                            val vType = draft.type ?: "Sales"
                            val typeColor = when (vType.lowercase()) {
                                "sales" -> Color(0xFF00E676)
                                "purchase" -> Color(0xFFFF5252)
                                "payment" -> Color(0xFFFFB300)
                                "receipt" -> Color(0xFF29B6F6)
                                else -> Color(0xFFAB47BC)
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, typeColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(typeColor.copy(alpha = 0.2f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        AutoShrinkText(
                                            vType.uppercase(),
                                            color = typeColor,
                                            maxFontSize = 12.sp,
                                            minFontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    val totalAmt = draft.amount ?: draft.entries.firstOrNull()?.amount ?: 0.0
                                    AutoShrinkText(
                                        "₹ ${"%.2f".format(totalAmt)}",
                                        color = typeColor,
                                        maxFontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                if (!draft.partyName.isNullOrBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    AutoShrinkText(
                                        "Party: ${draft.partyName}",
                                        color = Color.White,
                                        maxFontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (draft.inventoryItems.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    AutoShrinkText(TerminologyManager.getTerm("Items:"), color = Color.Gray, maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    draft.inventoryItems.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            AutoShrinkText(
                                                "${item.name} (${item.quantity} ${item.unit ?: "Pcs"})",
                                                color = Color.LightGray,
                                                maxFontSize = 12.sp
                                            )
                                            AutoShrinkText(
                                                "₹ ${"%.2f".format(item.amount)}",
                                                color = Color.White,
                                                maxFontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                Spacer(Modifier.height(6.dp))

                                draft.entries.forEach { entry ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        AutoShrinkText(
                                            "${if (entry.isDebit) "Dr" else "Cr"}  ${entry.ledgerName}",
                                            color = if (entry.isDebit) Color(0xFF81C784) else Color(0xFFFF8A80),
                                            maxFontSize = 11.sp,
                                            minFontSize = 8.sp
                                        )
                                        AutoShrinkText(
                                            "₹ ${"%.2f".format(entry.amount)}",
                                            color = Color.White.copy(alpha = 0.8f),
                                            maxFontSize = 11.sp,
                                            minFontSize = 8.sp
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(18.dp))

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { manager.startListening() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                ) {
                                    Text(TerminologyManager.getTerm("Retry"), color = Color.White, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        if (!isSaving) {
                                            isSaving = true
                                            coroutineScope.launch {
                                                manager.saveParsedVoucher(draft)
                                                isSaving = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(2f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(
                                            color = Color.Black,
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(TerminologyManager.getTerm("1-Tap Post"),
                                            color = Color.Black,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        is VoiceBillingState.Success -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            AutoShrinkText(TerminologyManager.getTerm("Voucher Posted!"),
                                color = Color.White,
                                maxFontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                currentState.message,
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(18.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { manager.startListening() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E676))
                                ) {
                                    Text(TerminologyManager.getTerm("New Voice Entry"), color = Color(0xFF00E676), fontSize = 12.sp)
                                }
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f))
                                ) {
                                    Text(TerminologyManager.getTerm("Done"), color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }

                        is VoiceBillingState.Error -> {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                currentState.message,
                                color = Color(0xFFFF8A80),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { manager.startListening() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF))
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(TerminologyManager.getTerm("Try Again"))
                            }
                        }

                        is VoiceBillingState.Idle -> {
                            Button(
                                onClick = { manager.startListening() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Black)
                                Spacer(Modifier.width(6.dp))
                                Text(TerminologyManager.getTerm("Tap to Speak"), color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
