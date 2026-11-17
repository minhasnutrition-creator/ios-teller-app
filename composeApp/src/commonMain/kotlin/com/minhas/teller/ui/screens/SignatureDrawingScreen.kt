package com.minhas.teller.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.CompanyRepository
import com.minhas.teller.ui.components.AutoShrinkText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private fun createBitmapFromPaths(paths: List<android.graphics.Path>): Bitmap {
    val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT)
    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    paths.forEach { canvas.drawPath(it, paint) }
    return bitmap
}

private suspend fun saveBitmapToFile(context: Context, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
    val file = File(context.filesDir, "signature_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    file
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureDrawingScreen(navController: NavController) {
    SignatureDrawingScreenV2(navController)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureDrawingScreenV2(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    val companyRepository = CompanyRepository(db.companyDao())
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()

    val paths = remember { mutableStateListOf<android.graphics.Path>() }
    val currentPath = remember { mutableStateOf<android.graphics.Path?>(null) }
    val drawPaint = remember {
        Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 12f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText("Draw Signature") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { paths.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                    }
                    IconButton(onClick = {
                        if (paths.isEmpty()) {
                            Toast.makeText(context, "Please draw a signature first", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        scope.launch {
                            try {
                                val bitmap = createBitmapFromPaths(paths.toList())
                                val savedFile = saveBitmapToFile(context, bitmap)
                                withContext(Dispatchers.IO) {
                                    val companyId = currentCompanyId ?: 1
                                    val company = companyRepository.getCompanySync(companyId)
                                    if (company != null) {
                                        companyRepository.update(company.copy(signatureUri = savedFile.absolutePath))
                                    }
                                }
                                Toast.makeText(context, "Signature saved successfully", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to save signature", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val p = android.graphics.Path().apply { moveTo(offset.x, offset.y) }
                                currentPath.value = p
                                paths.add(p)
                            },
                            onDrag = { change, _ ->
                                currentPath.value?.lineTo(change.position.x, change.position.y)
                            },
                            onDragEnd = { currentPath.value = null }
                        )
                    }
            ) {
                val canvas = drawContext.canvas.nativeCanvas
                paths.forEach { canvas.drawPath(it, drawPaint) }
                currentPath.value?.let { canvas.drawPath(it, drawPaint) }
            }
        }
    }
}
