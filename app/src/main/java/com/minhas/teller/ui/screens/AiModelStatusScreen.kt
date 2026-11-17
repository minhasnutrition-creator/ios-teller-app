package com.minhas.teller.ui.screens

import com.minhas.teller.utils.TerminologyManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.minhas.teller.utils.FinanceHelper
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.components.ModernTextField
import kotlinx.coroutines.launch
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelStatusScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // --- PERMISSION REQUEST FOR NOTIFICATIONS (Android 13+) ---
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = {}
        )
        LaunchedEffect(Unit) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    // Observe WorkManager for Download Status
    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkLiveData(FinanceHelper.OFFLINE_MLC_DOWNLOAD_WORK_NAME)
        .observeAsState()

    val downloadWorkInfo = workInfos?.firstOrNull()
    val isDownloading = downloadWorkInfo?.state == WorkInfo.State.RUNNING || 
                       downloadWorkInfo?.state == WorkInfo.State.ENQUEUED
    val isFailed = downloadWorkInfo?.state == WorkInfo.State.FAILED
    val isSucceeded = downloadWorkInfo?.state == WorkInfo.State.SUCCEEDED
    
    val progress = downloadWorkInfo?.progress?.getInt("progress", 0) ?: 0
    val stageText = downloadWorkInfo?.progress?.getString("stage")
    val errorText = downloadWorkInfo?.progress?.getString("error")
        ?: downloadWorkInfo?.outputData?.getString("error")
    val attempt = downloadWorkInfo?.runAttemptCount ?: 0
    val notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    
    // Check actual file status
    var isModelReady by remember { mutableStateOf(false) }
    var modelSize by remember { mutableStateOf("0 MB") }
    
    fun checkStatus() {
        val modelFile = com.minhas.teller.utils.AiModelPrefs.getModelFile(context)
        isModelReady = (modelFile.exists() && modelFile.length() >= com.minhas.teller.utils.AiModelPrefs.getMinSize(context)) || com.minhas.teller.utils.GemmaLLM.isLoaded()
        if (modelFile.exists()) {
            val sizeMb = modelFile.length() / (1024 * 1024)
            modelSize = "$sizeMb MB"
        } else {
            modelSize = "0 MB"
        }
    }

    LaunchedEffect(Unit, isSucceeded) {
        checkStatus()
    }

    // Test AI
    var testInput by remember { mutableStateOf("") }
    var testOutput by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText("AI Model Status") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(TerminologyManager.getTerm("Model Information"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(TerminologyManager.getTerm("Model: Gemma 3 1B"))
                    Text("Status: ${if (isModelReady) "Ready" else if (isDownloading) "Downloading..." else "Not Ready"}")
                    Text("Size on Disk: $modelSize")
                    Text("Worker State: ${downloadWorkInfo?.state ?: "None"} (Attempt $attempt)")
                    if (!stageText.isNullOrBlank()) {
                        Text("Stage: $stageText")
                    }
                    if (!notificationPermissionGranted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(TerminologyManager.getTerm("Notifications are OFF. Enable them to allow background download notification."), color = MaterialTheme.colorScheme.error)
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text(TerminologyManager.getTerm("Open Notification Settings"))
                        }
                    }
                    
                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Progress: $progress%", modifier = Modifier.align(Alignment.End))
                        if (progress == 0 && errorText.isNullOrBlank()) {
                            Text(TerminologyManager.getTerm("If this stays at 0%, check: Notifications ON, Background data ON, Data Saver OFF."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    if (isFailed) {
                        Text(TerminologyManager.getTerm("Last Download Failed. Please try again."), color = MaterialTheme.colorScheme.error)
                    }
                    if (!errorText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Error: $errorText", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { 
                        // Force Delete Existing Files for Redownload
                        val modelFile = com.minhas.teller.utils.AiModelPrefs.getModelFile(context)
                        try { if(modelFile.exists()) modelFile.delete() } catch (e: Exception) { e.printStackTrace() }
                        
                        FinanceHelper.enqueueOfflineModelDownloadForce(context)
                        checkStatus()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isDownloading && notificationPermissionGranted
                ) {
                    Icon(if (isModelReady) Icons.Default.Refresh else Icons.Default.CloudDownload, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isModelReady) "Redownload" else "Download Model")
                }
            }

            HorizontalDivider()

            // Testing Section
            Text(TerminologyManager.getTerm("Test Offline AI"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            ModernTextField(
                value = testInput,
                onValueChange = { testInput = it },
                placeholder = "Enter query (e.g. Sold 500 to Raj)",
                modifier = Modifier.fillMaxWidth()
            )
            
            Button(
                onClick = {
                    if (testInput.isNotBlank()) {
                        isTesting = true
                        scope.launch {
                            try {
                                val result = FinanceHelper.chatWithFinance(context, testInput, "", "")
                                testOutput = "[${result.confidence}] ${result.response}"
                            } catch (e: Exception) {
                                testOutput = "Error: ${e.message}"
                            } finally {
                                isTesting = false
                            }
                        }
                    }
                },
                enabled = !isTesting && isModelReady
            ) {
                if (isTesting) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text(TerminologyManager.getTerm("Run Test"))
            }
            
            if (testOutput.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = testOutput,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}
