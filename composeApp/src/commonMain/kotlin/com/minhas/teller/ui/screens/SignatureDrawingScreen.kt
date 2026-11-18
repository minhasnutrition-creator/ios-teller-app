package com.minhas.teller.ui.screens

import android.content.Context
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.minhas.teller.PlatformUtils
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.CompanyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureDrawingScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    val companyRepository = remember { CompanyRepository(db.companyDao()) }
    val currentCompanyId by SessionManager.currentCompanyId.collectAsState()

    val paths = remember { mutableStateListOf<Path>() }
    val currentPath = remember { mutableStateOf<Path?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Draw Signature") },
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
                                withContext(Dispatchers.Default) {
                                    val dir = File(PlatformUtils.getAppDataDirectory(), "signatures")
                                    if (!dir.exists()) dir.mkdirs()
                                    val sigFile = File(dir, "sig_${currentCompanyId ?: 1}.png")
                                    sigFile.writeBytes(ByteArray(0))

                                    val companyId = currentCompanyId ?: 1
                                    val company = companyRepository.getCompanySync(companyId)
                                    if (company != null) {
                                        companyRepository.update(company.copy(signatureUri = sigFile.absolutePath))
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
                                val p = Path().apply { moveTo(offset.x, offset.y) }
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
                val stroke = Stroke(width = 4.dp.toPx())
                paths.forEach { drawPath(it, color = Color.Black, style = stroke) }
                currentPath.value?.let { drawPath(it, color = Color.Black, style = stroke) }
            }
        }
    }
}
