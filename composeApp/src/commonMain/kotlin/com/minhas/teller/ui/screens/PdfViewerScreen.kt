package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NavController
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.utils.EasyPrint
import com.minhas.teller.utils.ExportUtils
import com.minhas.teller.utils.ThermalPrinterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    navController: NavController,
    filePath: String,
    title: String,
    voucherId: Int = -1
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember { File(filePath) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun renderPdfPages(scale: Float): List<Bitmap> {
        val rendered = mutableListOf<Bitmap>()
        if (file.exists()) {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                rendered.add(bitmap)
            }
            renderer.close()
            pfd.close()
        }
        return rendered
    }

    LaunchedEffect(filePath) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                // Render at a moderate scale; fall back to lower resolution if the
                // higher one runs out of memory (a large PDF at 2x can OOM and crash).
                var rendered: List<Bitmap>
                try {
                    rendered = renderPdfPages(1.5f)
                } catch (e: OutOfMemoryError) {
                    rendered = renderPdfPages(1.0f)
                }
                withContext(Dispatchers.Main) {
                    pages = rendered
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    // ── Printing ─────────────────────────────────────────────────────────────
    // First print asks one plain question with real device names; every print after that is a
    // single tap, because the choice that worked is remembered. The Printer Hub (modes, IP
    // addresses, paper widths) stays available but is never in the way.
    var showPrinterSheet by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }
    var pendingBtTarget by remember { mutableStateOf<EasyPrint.Target?>(null) }
    val printerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun runPrint(target: EasyPrint.Target) {
        scope.launch {
            isPrinting = true
            val outcome = EasyPrint.print(context, target, file, title, voucherId)
            isPrinting = false
            when (outcome) {
                is EasyPrint.Outcome.Printed -> {
                    EasyPrint.remember(context, target)
                    Toast.makeText(context, "✓ Printed on ${target.label}", Toast.LENGTH_SHORT).show()
                }
                is EasyPrint.Outcome.Queued -> {
                    EasyPrint.remember(context, target)
                    Toast.makeText(context, "Sent to ${target.label}", Toast.LENGTH_SHORT).show()
                }
                is EasyPrint.Outcome.Dispatched -> EasyPrint.remember(context, target)
                is EasyPrint.Outcome.Failed ->
                    Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val target = pendingBtTarget
        pendingBtTarget = null
        when {
            target == null -> {}
            !granted.values.all { it } -> Toast.makeText(
                context,
                "Allow \"Nearby devices\" so Teller can reach your Bluetooth printer",
                Toast.LENGTH_LONG
            ).show()
            // Permission just arrived, so the paired list is readable now — if the user tapped the
            // generic "Bluetooth bill printer" row, show the real devices instead of guessing.
            target.address.isNullOrBlank() -> showPrinterSheet = true
            else -> runPrint(target)
        }
    }

    fun choose(target: EasyPrint.Target) {
        showPrinterSheet = false
        if (EasyPrint.needsBluetoothPermission(context, target)) {
            pendingBtTarget = target
            btPermissionLauncher.launch(ThermalPrinterHelper.bluetoothPermissions())
        } else {
            runPrint(target)
        }
    }

    fun onPrintClicked() {
        val saved = EasyPrint.savedTarget(context)
        if (saved != null) runPrint(saved) else showPrinterSheet = true
    }

    if (showPrinterSheet) {
        val targets = remember(showPrinterSheet) { EasyPrint.targets(context) }
        ModalBottomSheet(
            onDismissRequest = { showPrinterSheet = false },
            sheetState = printerSheetState
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                Text(
                    TerminologyManager.getTerm("Where should this print?"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    TerminologyManager.getTerm("Tap your printer once — Teller remembers it next time."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                targets.forEach { target ->
                    Card(
                        onClick = { choose(target) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (target.kind) {
                                    EasyPrint.Kind.BLUETOOTH -> Icons.Default.Bluetooth
                                    EasyPrint.Kind.WIFI -> Icons.Default.Wifi
                                    EasyPrint.Kind.USB -> Icons.Default.Usb
                                    EasyPrint.Kind.COUNTER_PC -> Icons.Default.Computer
                                    EasyPrint.Kind.SYSTEM -> Icons.Default.Print
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(target.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    target.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        showPrinterSheet = false
                        navController.navigate(
                            com.minhas.teller.navigation.Screen.ThermalPrinter.createRoute(
                                voucherId.takeIf { it > 0 }
                            )
                        )
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text(TerminologyManager.getTerm("My printer isn't listed — set one up")) }
            }
        }
    }

    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
    }

    val panModifier = if (scale > 1f) {
        Modifier.pointerInput(scale) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 4f)
                val newOffset = offset + pan
                val maxOffsetX = (size.width * (scale - 1f)) / 2f
                val maxOffsetY = (size.height * (scale - 1f)) / 2f
                offset = Offset(
                    newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                    newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                )
            }
        }
    } else {
        Modifier.transformable(state = transformableState)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText(title.ifBlank { "PDF Viewer" }, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        },
        bottomBar = {
            // Bottom action bar: Download / Print / Share with zoom and credential check.
            // navigationBarsPadding() keeps it clear of the gesture nav bar / 3-button nav,
            // which otherwise sits on top of this row and eats the taps.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF0D0D1A)))
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zoom Out
                    IconButton(
                        onClick = {
                            val newScale = (scale - 0.25f).coerceAtLeast(1f)
                            if (newScale == 1f) {
                                offset = Offset.Zero
                            } else {
                                offset = offset * (newScale / scale)
                            }
                            scale = newScale
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    // Zoom In
                    IconButton(
                        onClick = {
                            val newScale = (scale + 0.25f).coerceAtMost(4f)
                            scale = newScale
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    
                    Spacer(Modifier.width(8.dp))

                    // Download
                    Button(
                        onClick = {
                            val saved = ExportUtils.saveFileToDownloads(context, file, file.name, "application/pdf")
                            Toast.makeText(context, if (saved) "✓ Saved to Downloads" else "Could not save", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(TerminologyManager.getTerm("Download"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    // Print — see onPrintClicked(): straight to the known printer, or one
                    // plain question listing the printers this phone can actually reach.
                    Button(
                        onClick = { onPrintClicked() },
                        enabled = !isPrinting,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        if (isPrinting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            TerminologyManager.getTerm(if (isPrinting) "Printing…" else "Print"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    // Share
                    Button(
                        onClick = { ExportUtils.shareFile(context, file, "application/pdf", "Share $title") },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(TerminologyManager.getTerm("Share"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (pages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(TerminologyManager.getTerm("Could not render PDF"), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(TerminologyManager.getTerm("The file may be invalid or corrupted."), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFEEEEEE))
                    .then(panModifier),
                contentAlignment = Alignment.Center
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pages) { bitmap ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "PDF Page",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}
