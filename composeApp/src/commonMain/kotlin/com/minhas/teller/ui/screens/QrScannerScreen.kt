package com.minhas.teller.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live in-app barcode/QR scanner (CameraX preview + ML Kit barcode analysis).
 *
 * Auto-detects a code from the camera stream and returns its raw value to the previous
 * screen via the back-stack entry's saved state under [resultKey]. By default only QR
 * codes are accepted (the e-invoice use case this was built for); pass [scanAnyFormat] to
 * also pick up 1D product barcodes (EAN/UPC/Code128/...). Uses CameraX + bundled ML Kit
 * rather than the Google Code Scanner so there is no dependency on Play Services (keeps
 * full device coverage in the Play Console).
 */
@Composable
fun QrScannerScreen(
    navController: NavHostController,
    resultKey: String = "qr_result",
    scanAnyFormat: Boolean = false,
    hint: String = "Point the camera at the QR code on your PC"
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) navController.popBackStack()
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val previewView = remember { PreviewView(context) }
    val scanner = remember { BarcodeScanning.getClient() }
    val handled = remember { AtomicBoolean(false) }

    DisposableEffect(lifecycleOwner, hasCameraPermission) {
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)

        if (hasCameraPermission) {
            val listener = Runnable {
                val provider = try {
                    providerFuture.get()
                } catch (_: Exception) {
                    null
                }
                if (provider == null) return@Runnable

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && !handled.get()) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val qr = barcodes.firstOrNull {
                                    (scanAnyFormat || it.format == Barcode.FORMAT_QR_CODE) && !it.rawValue.isNullOrBlank()
                                }
                                if (qr != null && handled.compareAndSet(false, true)) {
                                    val raw = qr.rawValue
                                    ContextCompat.getMainExecutor(context).execute {
                                        navController.previousBackStackEntry
                                            ?.savedStateHandle
                                            ?.set(resultKey, raw)
                                        navController.popBackStack()
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (_: Exception) {
                    // No usable camera — leave the preview black; the user can cancel.
                }
            }
            providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        }

        onDispose {
            try { providerFuture.get().unbindAll() } catch (_: Exception) {}
            executor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
        }

        Text(
            text = hint,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        )
    }
}
