package com.minhas.teller.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.minhas.teller.ui.components.TellerTopAppBar
import com.minhas.teller.utils.OcrHelper
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.web.WebCompanionManager
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopCompanionScreen(navController: NavController) {
    val context = LocalContext.current
    val isRunning by WebCompanionManager.isServerRunning.collectAsState()
    val serverUrl by WebCompanionManager.serverUrl.collectAsState()
    val localIp by WebCompanionManager.localIp.collectAsState()
    val serverPort by WebCompanionManager.serverPort.collectAsState()
    val requestCount by WebCompanionManager.requestCount.collectAsState()
    val recentLogs by WebCompanionManager.recentLogs.collectAsState()

    // Pulsing animation for active live status
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val activeUrl = serverUrl ?: "http://${WebCompanionManager.getLocalIpAddress(context)}:$serverPort"
    val friendlyUrl = serverUrl?.replace(Regex("^http://[^:/]+"), "http://teller.local") ?: "http://teller.local:$serverPort"

    // Is this phone on a network a PC can reach (Wi-Fi, its own hotspot, USB tethering)? Mobile data
    // alone cannot work. Re-checked every few seconds so the address follows the phone if it changes
    // Wi-Fi or the hotspot is switched on; the server listens on every interface, so no restart is needed.
    var phoneIp by remember { mutableStateOf(WebCompanionManager.getLocalIpAddress(context)) }
    val hasNetwork = phoneIp != "127.0.0.1"

    // Short address the customer types on the PC. The secret pairing token is NOT part of it: the PC
    // asks for approval instead (Allow/Deny dialog below).
    val shortAddress = activeUrl.substringBefore("?").removePrefix("http://").trimEnd('/')

    // A computer opened the address and is waiting for this phone to approve it.
    val pendingPair by WebCompanionManager.pendingPair.collectAsState()
    LaunchedEffect(isRunning) {
        while (true) {
            val ip = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                WebCompanionManager.getLocalIpAddress(context)
            }
            phoneIp = ip
            if (isRunning) {
                WebCompanionManager.pruneExpiredPairs()
                if (ip != "127.0.0.1" && ip != localIp) {
                    WebCompanionManager.updateServerState(
                        true, ip, serverPort, com.minhas.teller.web.CompanionAuth.getOrCreateToken(context)
                    )
                }
            }
            kotlinx.coroutines.delay(4000)
        }
    }

    // ── One-tap pairing (phone scans the PC's QR from the hosted page) ──
    val scope = rememberCoroutineScope()
    var pairPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isPairing by remember { mutableStateOf(false) }

    fun createPairUri(): Uri {
        val file = File(context.cacheDir, "pair_qr_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    val pairCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) {
            Toast.makeText(context, "Camera didn't capture a photo. Please try again.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val uri = pairPhotoUri ?: return@rememberLauncherForActivityResult
        isPairing = true
        scope.launch {
            val sessionId = parseSessionId(OcrHelper.scanQrFromUri(context, uri))
            val ok = if (sessionId != null) {
                if (!isRunning) WebCompanionManager.startServer(context)
                WebCompanionManager.publishPairing(sessionId, context)
            } else false
            isPairing = false
            if (ok) {
                Toast.makeText(context, "Paired! Your PC should now open Teller.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Pairing failed. Make sure the PC shows a valid code and you're online.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Phone-as-trackpad / keyboard state
    var trackpadMode by remember { mutableStateOf(false) }
    var keyboardVisible by remember { mutableStateOf(false) }

    // The PC page polls fast only while this is on (and slowly otherwise), so tell the server.
    LaunchedEffect(trackpadMode, isRunning) {
        WebCompanionManager.setTrackpadActive(trackpadMode && isRunning)
    }
    DisposableEffect(Unit) {
        onDispose { WebCompanionManager.setTrackpadActive(false) }
    }

    LaunchedEffect(trackpadMode, isRunning) {
        while (trackpadMode && isRunning) {
            keyboardVisible = WebCompanionManager.isKeyboardRequested()
            kotlinx.coroutines.delay(300)
        }
        if (!trackpadMode || !isRunning) keyboardVisible = false
    }

    pendingPair?.let { req ->
        AlertDialog(
            onDismissRequest = { WebCompanionManager.denyPair(req.id) },
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            title = { Text(TerminologyManager.getTerm("Allow this computer?"), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "${req.device} (${req.ip}) wants to open your Teller books.",
                        textAlign = TextAlign.Center
                    )
                    Text(
                        req.code,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 40.sp,
                        letterSpacing = 6.sp,
                        color = Color(0xFF10B981)
                    )
                    Text(
                        "Tap Allow only if this number is the same as the one on your PC screen.",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { WebCompanionManager.approvePair(req.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) { Text(TerminologyManager.getTerm("Allow"), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { WebCompanionManager.denyPair(req.id) }) {
                    Text(TerminologyManager.getTerm("Deny"), color = Color(0xFFEF4444))
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TellerTopAppBar(
                title = "PC Desktop Companion",
                canNavigateBack = true,
                navigateUp = { navController.navigateUp() },
                showHome = true,
                navController = navController
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mobile data alone cannot work: the PC must reach the phone over Wi-Fi / hotspot.
            if (!hasNetwork) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2412)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color(0xFFF59E0B))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                TerminologyManager.getTerm("This phone is not on Wi-Fi"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Connect this phone to your shop Wi-Fi, or switch on the phone's Hotspot and connect your PC to it. Mobile data alone will not work.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Hero Status Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRunning) Color(0xFF0F291E) else Color(0xFF1E2433)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isRunning) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF374151)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .scale(if (isRunning) pulseScale else 1f)
                                    .clip(CircleShape)
                                    .background(if (isRunning) Color(0xFF10B981) else Color(0xFF6B7280))
                            )
                            Text(
                                text = if (isRunning) "SERVER LIVE & READY" else "SERVER OFFLINE",
                                color = if (isRunning) Color(0xFF10B981) else Color(0xFF9CA3AF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Switch(
                            checked = isRunning,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    WebCompanionManager.startServer(context)
                                } else {
                                    WebCompanionManager.stopServer(context)
                                }
                            },
                            modifier = Modifier.scale(0.8f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981)
                            )
                        )
                    }

                    if (isRunning) {
                        // Monospace URL Box (doubles as the "type this on your PC" label)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF064E3B).copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (hasNetwork) shortAddress else "No Wi-Fi",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF34D399)
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Teller Desktop URL", shortAddress))
                                        Toast.makeText(context, "Address copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Same Wi-Fi: ${friendlyUrl.substringBefore("?")}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8),
                            textAlign = TextAlign.Center
                        )

                        // Quick Share & Open Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Open Teller on your PC: http://$shortAddress")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Desktop URL"))
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(TerminologyManager.getTerm("Share URL"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(activeUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(TerminologyManager.getTerm("Open on Phone"), fontSize = 12.sp)
                            }
                        }

                        // Lost a laptop, or shared the link with someone who shouldn't have it?
                        // Rotating the token locks out every paired computer at once.
                        TextButton(
                            onClick = {
                                com.minhas.teller.web.WebCompanionManager.disconnectAllComputers(context)
                                Toast.makeText(context, TerminologyManager.getTerm("All computers disconnected. Open the new link on your PC to reconnect."), Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(TerminologyManager.getTerm("Disconnect all computers"), color = Color(0xFFFCA5A5), fontSize = 12.sp)
                        }
                    } else {
                        Text(
                            text = "Toggle the switch above to launch your local desktop web server. You can then access Teller from any PC on your local Wi-Fi!",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // One-tap pairing (no typing) — scan the PC's QR from the hosted page
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131A29)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2D3748))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🔗", fontSize = 22.sp)
                        Column(Modifier.weight(1f)) {
                            Text(TerminologyManager.getTerm("One-tap pairing"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            Text(TerminologyManager.getTerm("Scan a QR on your PC — no typing addresses"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                    Text(
                        text = "On your PC open:  tellerpc.web.app",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color(0xFF38BDF8)
                    )
                    Button(
                        onClick = {
                            if (!isRunning) WebCompanionManager.startServer(context)
                            val uri = createPairUri()
                            pairPhotoUri = uri
                            pairCameraLauncher.launch(uri)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !isPairing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isPairing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(TerminologyManager.getTerm("Pairing…"))
                        } else {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(TerminologyManager.getTerm("Scan PC QR"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Phone-as-trackpad / keyboard
            if (isRunning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A29)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2D3748))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(TerminologyManager.getTerm("Phone as Trackpad & Keyboard"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                Text(TerminologyManager.getTerm("Control the PC screen from your phone"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                            Switch(
                                checked = trackpadMode,
                                onCheckedChange = { trackpadMode = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF10B981))
                            )
                        }

                        if (trackpadMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0B1220))
                                    .border(1.dp, Color(0xFF2D3748), RoundedCornerShape(16.dp))
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown()
                                            fun nx(x: Float) = (x / size.width.toFloat()).coerceIn(0f, 1f)
                                            fun ny(y: Float) = (y / size.height.toFloat()).coerceIn(0f, 1f)
                                            WebCompanionManager.enqueuePointer("down", nx(down.position.x), ny(down.position.y))
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                if (!change.pressed) {
                                                    WebCompanionManager.enqueuePointer("up", nx(change.position.x), ny(change.position.y))
                                                    break
                                                } else {
                                                    WebCompanionManager.enqueuePointer("move", nx(change.position.x), ny(change.position.y))
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(TerminologyManager.getTerm("Trackpad — swipe to move, tap to click"), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                            }

                            if (keyboardVisible) {
                                Text(TerminologyManager.getTerm("PC needs text input — type below:"), fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                VirtualKeyboard(onKey = { WebCompanionManager.enqueueKey(it) })
                            }
                        }
                    }
                }
            }

            // Step-by-Step Instructions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161F30)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF283548))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "How to Use Desktop Pro Mode",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )

                    InstructionRow(
                        num = "1",
                        title = "Connect to Same Wi-Fi",
                        desc = "Ensure your PC / Mac and this phone are connected to the same office Wi-Fi network (or turn on Phone Hotspot and connect PC to it)."
                    )

                    InstructionRow(
                        num = "2",
                        title = "Open Chrome or Edge on PC",
                        desc = "Type the address above into your PC browser. The PC shows a 4-digit code: tap Allow on this phone when it matches (or just scan the QR code with your laptop camera)."
                    )

                    InstructionRow(
                        num = "3",
                        title = "Full Keyboard Speed Accounting",
                        desc = "Use F4 (Contra), F5 (Payment), F6 (Receipt), F8 (Sales), F9 (Purchase), Enter to jump fields, and Ctrl+S to save directly into phone DB!"
                    )
                }
            }

            // Live Activity Log
            if (isRunning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live PC Requests",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Badge(containerColor = Color(0xFF10B981)) {
                                Text("$requestCount calls", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (recentLogs.isEmpty()) {
                            Text(
                                text = "Waiting for requests from PC...",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        } else {
                            recentLogs.takeLast(6).forEach { log ->
                                Text(
                                    text = "→ $log",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualKeyboard(onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Z", "X", "C", "V", "B", "N", "M")
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    KeyboardKey(key, Modifier.weight(1f)) { onKey(key) }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyboardKey("Space", Modifier.weight(3f)) { onKey("SPACE") }
            KeyboardKey("⌫", Modifier.weight(1f)) { onKey("BACKSPACE") }
            KeyboardKey("⏎", Modifier.weight(1f)) { onKey("ENTER") }
        }
    }
}

@Composable
private fun KeyboardKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun InstructionRow(num: String, title: String, desc: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFF3B82F6)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = num, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            Text(text = desc, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/** Extract a pairing session id from a scanned PC QR. Accepts "tellpair:XXXX" or a bare code. */
private fun parseSessionId(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim()
    return when {
        s.startsWith("tellpair:", ignoreCase = true) -> s.substringAfter(':').takeIf { it.isNotBlank() }
        s.matches(Regex("[A-Za-z0-9]{4,12}")) -> s
        else -> null
    }
}
