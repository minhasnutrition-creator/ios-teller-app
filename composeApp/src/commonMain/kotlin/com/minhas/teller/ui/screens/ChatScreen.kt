package com.minhas.teller.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.minhas.teller.utils.ReplyLocalizer
import com.minhas.teller.utils.SpeechInputPrefs
import com.minhas.teller.utils.SpeechRepair
import com.minhas.teller.utils.SystemTtsEngine
import com.minhas.teller.utils.TtsModelPrefs
import android.app.DatePickerDialog
import android.widget.DatePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.minhas.teller.ui.components.AutoShrinkText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.ui.viewmodels.ChatMessage
import com.minhas.teller.ui.viewmodels.FormField
import com.minhas.teller.ui.viewmodels.ChatViewModel
import com.minhas.teller.ui.viewmodels.ChatViewModelFactory
import com.minhas.teller.ui.viewmodels.AiModelStatus
import com.minhas.teller.utils.AiResponse
import com.minhas.teller.utils.FinanceHelper
import com.minhas.teller.utils.LicenseUtils
import com.minhas.teller.utils.TerminologyManager
import com.minhas.teller.utils.TutorialManager
import com.minhas.teller.utils.TutorialStep
import com.minhas.teller.ui.components.AutoShrinkText
import com.minhas.teller.ui.components.InteractiveTutorialOverlay
import com.minhas.teller.ui.components.ChatChartCard
import com.minhas.teller.ui.components.ChatTableCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    initialQuery: String? = null,
    autoStartVoice: Boolean = false
) {
    val context = LocalContext.current

    // --- TUTORIAL LOGIC ---
    val isTutorialActive = TutorialManager.isTutorialActive
    val currentStep = TutorialManager.currentStep

    if (isTutorialActive) {
        if (currentStep == TutorialStep.AI_CHAT_INTRO) {
             InteractiveTutorialOverlay(
                currentStep = TutorialStep.AI_CHAT_INTRO,
                onNext = { /* User must tap mic */ },
                onSkip = { TutorialManager.skipTutorial(context) }
             )
        }
        if (currentStep == TutorialStep.AI_SHOW_DRAFT) {
             InteractiveTutorialOverlay(
                currentStep = TutorialStep.AI_SHOW_DRAFT,
                onNext = {},
                onSkip = {}
             )
        }
    }

    val db = AppDatabase.getDatabase(context)
    val voucherRepository = remember {
        VoucherRepository(db.voucherDao(), db.voucherEntryDao(), db.inventoryEntryDao(), db.billAllocationDao(), db.auditLogDao(), db.ledgerDao(), db = db)
    }
    val ledgerRepository = remember { LedgerRepository(db.ledgerDao()) }
    val stockItemDao = db.stockItemDao()
    val unitDao = db.unitDao()

    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(context.applicationContext, voucherRepository, ledgerRepository, stockItemDao, unitDao, db.chatMessageDao())
    )

    LaunchedEffect(currentStep, isTutorialActive) {
        if (isTutorialActive) {
            if (currentStep == TutorialStep.AI_CHAT_INTRO) {
                 delay(4000)
                 if (currentStep == TutorialStep.AI_CHAT_INTRO) {
                     TutorialManager.advanceStep()
                 }
            }
            if (currentStep == TutorialStep.AI_VOICE_SIMULATION) {
                delay(1000)
                viewModel.sendMessage("Given 500 to Gagan")
                TutorialManager.advanceStep()
            }
            if (currentStep == TutorialStep.AI_SHOW_DRAFT) {
                delay(4000)
                TutorialManager.markCompleted(context)
                TutorialManager.isTutorialActive = false
                navController.popBackStack()
            }
        }
    }

    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val aiModelStatus by viewModel.aiModelStatus.collectAsState()
    val history by viewModel.history.collectAsState()
    val autoListenTick by viewModel.autoListenRequest.collectAsState()

    var showHistory by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var isListening by remember { mutableStateOf(false) }
    var accumulatedText by remember { mutableStateOf("") }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val sendToGeminiRunnable = remember {
        Runnable {
            if (accumulatedText.isNotBlank()) {
                viewModel.sendMessage(accumulatedText)
                accumulatedText = ""
            }
        }
    }

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    
    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true; accumulatedText = "" }
            override fun onBeginningOfSpeech() { handler.removeCallbacks(sendToGeminiRunnable) }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                isListening = false
                handler.removeCallbacks(sendToGeminiRunnable)
                // The recogniser returns several transcripts of the same utterance and they differ
                // exactly where it was unsure — which is where the billing mistakes live ("4300"
                // for "for 300", "panchram" for "paanch rum"). Rank them instead of taking #1.
                val text = SpeechRepair.pickAndRepair(
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
                    results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                )
                val queryToSend = if (text.isNotBlank()) text else accumulatedText
                accumulatedText = ""
                if (queryToSend.isNotBlank()) {
                    viewModel.sendMessage(queryToSend)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (partial.isNotBlank()) {
                    accumulatedText = partial
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    DisposableEffect(Unit) {
        val recognizer = try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(recognitionListener)
                }
            } else null
        } catch (e: Exception) {
            Log.e("ChatScreen", "Failed to initialize SpeechRecognizer", e)
            null
        }
        speechRecognizer = recognizer

        onDispose {
            viewModel.saveCurrentSession()
            try {
                recognizer?.destroy()
            } catch (e: Exception) {
                Log.e("ChatScreen", "Failed to destroy SpeechRecognizer", e)
            }
            speechRecognizer = null
            handler.removeCallbacks(sendToGeminiRunnable)
        }
    }

    fun startVoiceInput() {
        try {
            // Stop any assistant speech immediately so the mic isn't listening over
            // the assistant's own voice (and the user's command isn't drowned out).
            SystemTtsEngine.stop()
            val recognizer = speechRecognizer
            if (recognizer == null || !SpeechRecognizer.isRecognitionAvailable(context)) {
                Toast.makeText(context, "Voice recognition not available on this device", Toast.LENGTH_SHORT).show()
                return
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, SpeechInputPrefs.getLocale(context))
                // Ask for the n-best list (SpeechRepair.pickAndRepair ranks it) and for partial
                // results, which onPartialResults keeps as a fallback transcript.
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e("ChatScreen", "startVoiceInput failed", e)
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> FinanceHelper.enqueueOfflineModelDownloadForce(context) }

    fun startDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            FinanceHelper.enqueueOfflineModelDownloadForce(context)
        }
    }

    var showMicDisclosureDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) startVoiceInput()
    }

    fun checkAndStartVoiceInput() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceInput()
        } else {
            showMicDisclosureDialog = true
        }
    }

    // Helafin just asked a follow-up question (missing amount/item/party/etc.) — open the mic so
    // the user can speak the answer straight away instead of tapping the mic button again.
    LaunchedEffect(autoListenTick) {
        if (autoListenTick > 0 && !isListening) {
            checkAndStartVoiceInput()
        }
    }

    var initialQueryTriggered by androidx.compose.runtime.saveable.rememberSaveable(initialQuery) { mutableStateOf(false) }
    LaunchedEffect(initialQuery) {
        val cleanQuery = initialQuery?.takeIf { it.isNotBlank() && !it.startsWith("{") }
        if (cleanQuery != null && !initialQueryTriggered) {
            initialQueryTriggered = true
            delay(200)
            viewModel.sendMessage(cleanQuery)
        }
    }

    var autoVoiceTriggered by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoStartVoice) {
        if (autoStartVoice && !autoVoiceTriggered) {
            autoVoiceTriggered = true
            delay(400)
            checkAndStartVoiceInput()
        }
    }

    // --- Natural system TTS (Google TextToSpeech) ---
    var selectedVoiceLang by remember { mutableStateOf(TtsModelPrefs.getVoiceLanguage(context)) }
    var selectedInputTag by remember { mutableStateOf(SpeechInputPrefs.getLocale(context).toLanguageTag()) }
    var showVoiceSettings by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        SystemTtsEngine.init(context)
        onDispose { SystemTtsEngine.shutdown() }
    }

    fun speak(text: String) {
        SystemTtsEngine.speak(text, selectedVoiceLang)
    }

    fun voiceSample(lang: SystemTtsEngine.Language): String = when (lang) {
        SystemTtsEngine.Language.ENGLISH -> "Hello, I am your finance assistant."
        SystemTtsEngine.Language.HINDI -> "नमस्ते, मैं आपका वित्त सहायक हूँ।"
        SystemTtsEngine.Language.PUNJABI -> "ਸਤ ਸ੍ਰੀ ਅਕਾਲ, ਮੈਂ ਤੁਹਾਡਾ ਵਿੱਤ ਸਹਾਇਕ ਹਾਂ।"
        SystemTtsEngine.Language.ITALIAN -> "Ciao, sono il tuo assistente finanziario."
        SystemTtsEngine.Language.SPANISH -> "Hola, soy tu asistente financiero."
        SystemTtsEngine.Language.FRENCH -> "Bonjour, je suis votre assistant financier."
        SystemTtsEngine.Language.RUSSIAN -> "Здравствуйте, я ваш финансовый помощник."
    }

    LaunchedEffect(messages) {
        messages.lastOrNull()?.let { lastMessage ->
            val rawTextToSpeak = when (lastMessage) {
                is ChatMessage.Text -> if (!lastMessage.isUser && lastMessage.shouldSpeak) lastMessage.text else null
                is ChatMessage.ReviewVoucher -> "I have drafted a voucher for your review."
                is ChatMessage.ReviewLedger -> "I've drafted a new ledger."
                is ChatMessage.Success -> lastMessage.text
                is ChatMessage.InteractiveForm -> "Please fill out the details in the form."
                is ChatMessage.ChartMessage -> "Here is the chart you requested."
                is ChatMessage.TableMessage -> "Here is the data table you requested."
            }
            if (rawTextToSpeak != null) {
                val localizedSpeech = ReplyLocalizer.localize(selectedVoiceLang, rawTextToSpeak)
                delay(150)
                speak(localizedSpeech)
            }
        }
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AutoShrinkText(TerminologyManager.getTerm("Finance Assistant")) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = TerminologyManager.getTerm("Back"))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadHistory(); showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "Chat History")
                    }
                    IconButton(onClick = { showVoiceSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = TerminologyManager.getTerm("Voice Settings"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
             AiModelStatusHeader(
                 status = aiModelStatus,
                 onDownload = { startDownload() },
                 onRetry = { viewModel.forceReloadModel() }
             )
             Spacer(modifier = Modifier.height(8.dp))
             LazyColumn(
                 state = listState,
                 modifier = Modifier.weight(1f).fillMaxWidth(),
                 verticalArrangement = Arrangement.spacedBy(8.dp)
             ) {
                 if (messages.isEmpty()) {
                     item {
                         Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                             Text(
                                 TerminologyManager.getTerm("I am Helafin, your AI Assistant!\n\nCreating vouchers is incredibly simple—just tell me what happened in English, Hindi, or Hinglish!\n\nExamples:\n\"Sold 5kg sugar to Amit for 500\"\n\"Ramesh ko 1000 rupaye diye\"\n\"Create ledger for Client A\"\n\nTap the MIC to speak!"),
                                 style = MaterialTheme.typography.bodyMedium,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                                 textAlign = TextAlign.Center
                             )
                         }
                     }
                 }
                items(messages) { msg ->
                    when (msg) {
                        is ChatMessage.Text -> ChatBubble(msg.text, msg.isUser)
                        is ChatMessage.ReviewVoucher -> ReviewVoucherCard(msg.draft, onConfirm = { finalDate -> viewModel.confirmVoucher(msg.draft.copy(date = finalDate)) })
                        is ChatMessage.ReviewLedger -> ReviewLedgerCard(msg.draft, onConfirm = { viewModel.confirmLedger(msg.draft) })
                        is ChatMessage.Success -> SuccessBubble(msg.text)
                        is ChatMessage.InteractiveForm -> InteractiveFormCard(msg, onSubmit = { values -> viewModel.submitForm(msg.formId, values) })
                        is ChatMessage.ChartMessage -> ChatChartCard(msg.chartData)
                        is ChatMessage.TableMessage -> ChatTableCard(msg.tableData)
                    }
                }
                if (isLoading) {
                    item {
                        ThinkingBubble()
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isListening) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Box(modifier = Modifier.weight(1f).height(56.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                         Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                             AudioVisualizer(modifier = Modifier.height(30.dp))
                             Spacer(modifier = Modifier.width(12.dp))
                             Text(
                                 if (accumulatedText.isNotBlank()) accumulatedText else TerminologyManager.getTerm("Helafin is listening..."),
                                 style = MaterialTheme.typography.labelLarge,
                                 color = MaterialTheme.colorScheme.onPrimaryContainer,
                                 maxLines = 1,
                                 overflow = TextOverflow.Ellipsis
                             )
                         }
                     }
                     Spacer(modifier = Modifier.width(8.dp))
                     IconButton(onClick = { speechRecognizer?.stopListening(); isListening = false }, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape)) {
                         Icon(Icons.Default.Close, TerminologyManager.getTerm("Stop"), tint = MaterialTheme.colorScheme.onErrorContainer)
                     }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { checkAndStartVoiceInput() }, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)) {
                        Icon(Icons.Default.Mic, contentDescription = TerminologyManager.getTerm("Voice Input"), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = inputText, onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f), placeholder = { Text(TerminologyManager.getTerm("Type or speak...")) },
                        maxLines = 3, shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotBlank()) { viewModel.sendMessage(inputText); inputText = "" }
                        })
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { if (inputText.isNotBlank()) { viewModel.sendMessage(inputText); inputText = "" } }, modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = TerminologyManager.getTerm("Send"), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        if (showVoiceSettings) {
            AlertDialog(
                onDismissRequest = { showVoiceSettings = false },
                title = { Text(TerminologyManager.getTerm("Voice Settings")) },
                text = {
                    Column {
                        Text(
                            TerminologyManager.getTerm("Assistant voice (speaks)"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf(
                            SystemTtsEngine.Language.ENGLISH to "English (Natural)",
                            SystemTtsEngine.Language.HINDI to "Hindi (Natural)",
                            SystemTtsEngine.Language.PUNJABI to "Punjabi (Natural)",
                            SystemTtsEngine.Language.ITALIAN to "Italian (Natural)",
                            SystemTtsEngine.Language.SPANISH to "Spanish (Natural)",
                            SystemTtsEngine.Language.FRENCH to "French (Natural)",
                            SystemTtsEngine.Language.RUSSIAN to "Russian (Natural)"
                        ).forEach { (lang, label) ->
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                selectedVoiceLang = lang
                                TtsModelPrefs.setVoiceLanguage(context, lang)
                                speak(voiceSample(lang))
                            }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = (lang == selectedVoiceLang), onClick = {
                                    selectedVoiceLang = lang
                                    TtsModelPrefs.setVoiceLanguage(context, lang)
                                    speak(voiceSample(lang))
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            TerminologyManager.getTerm("Your speech (input)"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf(
                            "en-IN" to "English (India)",
                            "hi-IN" to "Hindi",
                            "pa-IN" to "Punjabi",
                            "gu-IN" to "Gujarati",
                            "mr-IN" to "Marathi",
                            "bn-IN" to "Bengali",
                            "ta-IN" to "Tamil",
                            "te-IN" to "Telugu",
                            "kn-IN" to "Kannada",
                            "ml-IN" to "Malayalam",
                            "ur-IN" to "Urdu",
                            "or-IN" to "Odia",
                            "en-US" to "English (US)"
                        ).forEach { (tag, label) ->
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                selectedInputTag = tag
                                SpeechInputPrefs.setLocale(context, Locale.forLanguageTag(tag))
                            }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = (tag == selectedInputTag), onClick = {
                                    selectedInputTag = tag
                                    SpeechInputPrefs.setLocale(context, Locale.forLanguageTag(tag))
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showVoiceSettings = false }) { Text(TerminologyManager.getTerm("Done")) } }
            )
        }
        if (showHistory) {
            AlertDialog(
                onDismissRequest = { showHistory = false },
                title = { Text(TerminologyManager.getTerm("Chat History")) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        TextButton(onClick = { viewModel.startNewSession(); showHistory = false }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(TerminologyManager.getTerm("New Chat"))
                        }
                        if (history.isEmpty()) {
                            Text(TerminologyManager.getTerm("No past conversations yet. Start chatting and your conversations will be saved here."),
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        } else {
                            LazyColumn {
                                items(history, key = { it.sessionId }) { session ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.loadSession(session.sessionId)
                                                showHistory = false
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(session.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (session.preview != session.title) {
                                                Text(session.preview, fontSize = 12.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text(java.text.SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(session.lastTimestamp)), fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showHistory = false }) { Text(TerminologyManager.getTerm("Close")) } }
            )
        }
        if (showMicDisclosureDialog) {
            AlertDialog(
                onDismissRequest = { showMicDisclosureDialog = false },
                title = { Text(TerminologyManager.getTerm("Microphone Access Required")) },
                text = {
                    Text(TerminologyManager.getTerm("Teller requires microphone access to enable voice commands for the AI Assistant, Helafin. This allows you to speak transactions (e.g., 'Sold 5kg sugar to Amit for 500' or 'Ramesh ko 1000 rupaye diye') instead of typing them.\n\nYour audio is processed entirely on your device and is never sent to an external server."))
                },
                confirmButton = {
                    TextButton(onClick = {
                        showMicDisclosureDialog = false
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }) {
                        Text(TerminologyManager.getTerm("I Agree"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMicDisclosureDialog = false }) {
                        Text(TerminologyManager.getTerm("Cancel"))
                    }
                }
            )
        }
    }
}

@Composable
fun AiModelStatusHeader(
    status: AiModelStatus,
    onDownload: () -> Unit,
    onRetry: () -> Unit
) {
    val (color, text, showActions) = when (status) {
        is AiModelStatus.Ready -> Triple(Color(0xFF4CAF50), TerminologyManager.getTerm("Helafin Offline AI is Ready"), false)
        is AiModelStatus.Loading -> Triple(Color(0xFFFF9800), TerminologyManager.getTerm("Helafin is initializing..."), false)
        is AiModelStatus.Downloading -> {
            Triple(Color(0xFF2196F3), "${TerminologyManager.getTerm("Downloading Offline AI Model")} (${status.progress}%)", false)
        }
        is AiModelStatus.NotDownloaded -> Triple(Color(0xFF9E9E9E), TerminologyManager.getTerm("Offline AI Model not installed"), true)
        is AiModelStatus.Error -> Triple(Color(0xFFF44336), TerminologyManager.getTerm("Helafin Error"), true)
    }

    var showExplanation by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .then(
                if (status is AiModelStatus.Error) {
                    Modifier.clickable { showExplanation = true }
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "status_dot_pulse")
                val dotAlpha by if (status is AiModelStatus.Loading || status is AiModelStatus.Downloading) {
                    infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )
                } else {
                    remember { mutableStateOf(1f) }
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color.copy(alpha = dotAlpha), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (status is AiModelStatus.Downloading && status.stage.isNotBlank()) {
                        Text(
                            text = status.stage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (status is AiModelStatus.Error) {
                        Text(
                            text = TerminologyManager.getTerm("Tap to see details"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            if (showActions) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (status is AiModelStatus.NotDownloaded) {
                            onDownload()
                        } else {
                            onRetry()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (status is AiModelStatus.NotDownloaded) TerminologyManager.getTerm("Install") else TerminologyManager.getTerm("Retry"),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }

    if (showExplanation && status is AiModelStatus.Error) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text(TerminologyManager.getTerm("Model Loading Error")) },
            text = { Text(status.message) },
            confirmButton = {
                TextButton(onClick = { showExplanation = false }) { Text(TerminologyManager.getTerm("Dismiss")) }
            }
        )
    }
}

@Composable
fun ThinkingBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val dotCount = 3
    val dots = List(dotCount) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(offsetMillis = index * 200)
            ),
            label = "dot_$index"
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = TerminologyManager.getTerm("Helafin is processing"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    dots.forEach { dotAlpha ->
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = dotAlpha.value),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(text: String, isUser: Boolean) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer, shape = if (isUser) RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp), modifier = Modifier.widthIn(max = 280.dp)) {
            Text(text = text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SuccessBubble(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(color = Color(0xFFE8F5E9), contentColor = Color(0xFF2E7D32), shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun ReviewVoucherCard(draft: AiResponse.DraftVoucher, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    var isSubmitting by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val submitDateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }

    var selectedDate by remember(draft.date) {
        mutableStateOf(FinanceHelper.parseVoucherDate(draft.date))
    }

    val isToday = remember(selectedDate) {
        android.text.format.DateUtils.isToday(selectedDate.time)
    }
    val isYesterday = remember(selectedDate) {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val calSelected = Calendar.getInstance().apply { time = selectedDate }
        cal.get(Calendar.YEAR) == calSelected.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == calSelected.get(Calendar.DAY_OF_YEAR)
    }
    val dateLabelSuffix = when {
        isToday -> " (${TerminologyManager.getTerm("Today")})"
        isYesterday -> " (${TerminologyManager.getTerm("Yesterday")})"
        else -> ""
    }

    fun showDatePicker() {
        val cal = Calendar.getInstance().apply { time = selectedDate }
        DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                selectedDate = newCal.time
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Card(modifier = Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(TerminologyManager.getTerm("Review Voucher Draft"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${TerminologyManager.getTerm("Type")}: ${draft.type}")
                if (!draft.voucherNumber.isNullOrEmpty()) Text("${TerminologyManager.getTerm("Voucher #")}: ${draft.voucherNumber}", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Selectable Date Picker Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showDatePicker() }
            ) {
                OutlinedTextField(
                    value = "${dateFormatter.format(selectedDate)}$dateLabelSuffix",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(TerminologyManager.getTerm("Voucher Date")) },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker() }) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = TerminologyManager.getTerm("Select Date"),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showDatePicker() }
                )
            }

            // Quick date selection chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isToday,
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 12)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        selectedDate = cal.time
                    },
                    label = { AutoShrinkText(TerminologyManager.getTerm("Today"), maxFontSize = 14.sp, minFontSize = 10.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = isYesterday,
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, -1)
                            set(Calendar.HOUR_OF_DAY, 12)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        selectedDate = cal.time
                    },
                    label = { AutoShrinkText(TerminologyManager.getTerm("Yesterday"), maxFontSize = 14.sp, minFontSize = 10.sp) }
                )
                FilterChip(
                    selected = !isToday && !isYesterday,
                    onClick = { showDatePicker() },
                    label = { AutoShrinkText(TerminologyManager.getTerm("Pick Date"), maxFontSize = 14.sp, minFontSize = 10.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(TerminologyManager.getTerm("Ledgers:"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            draft.entries.forEach { entry ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "${if (entry.isDebit) TerminologyManager.getTerm("Dr") else TerminologyManager.getTerm("Cr")} ${entry.ledgerName}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${entry.amount}", fontWeight = FontWeight.Bold)
                }
            }
            if (draft.inventoryItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(TerminologyManager.getTerm("Items:"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                draft.inventoryItems.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${item.name} (${item.quantity} x ${item.rate})")
                        Text("${item.amount}", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!draft.narration.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("${TerminologyManager.getTerm("Narration")}: ${draft.narration}", style = MaterialTheme.typography.bodySmall, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    isSubmitting = true
                    onConfirm(submitDateFormatter.format(selectedDate))
                },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) else Text(TerminologyManager.getTerm("Confirm & Save"))
            }
        }
    }
}

@Composable
fun ReviewLedgerCard(draft: AiResponse.DraftLedger, onConfirm: () -> Unit) {
    var isSubmitting by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(TerminologyManager.getTerm("Review New Ledger"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("${TerminologyManager.getTerm("Name")}: ${draft.name}")
            Text("${TerminologyManager.getTerm("Group")}: ${draft.group}")
            if ((draft.openingBalance ?: 0.0) > 0) Text("${TerminologyManager.getTerm("Opening Balance")}: ${draft.openingBalance}")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { isSubmitting = true; onConfirm() }, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) else Text(TerminologyManager.getTerm("Create Ledger"))
            }
        }
    }
}

@Composable
fun InteractiveFormCard(msg: ChatMessage.InteractiveForm, onSubmit: (Map<String, String>) -> Unit) {
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val submitDateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }

    val formValues = remember(msg) {
        mutableStateMapOf<String, String>().apply {
            msg.fields.forEach { field ->
                val initialVal = msg.initialValues[field.key] ?: ""
                val resolvedVal = if (field.key.equals("date", ignoreCase = true) || field.key.contains("date", ignoreCase = true)) {
                    val parsedDate = FinanceHelper.parseVoucherDate(initialVal)
                    submitDateFormatter.format(parsedDate)
                } else {
                    initialVal
                }
                put(field.key, resolvedVal)
            }
        }
    }
    var isSubmitted by remember(msg) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(msg.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier = Modifier.height(12.dp))
            
            msg.fields.forEach { field ->
                val isDateField = field.key.equals("date", ignoreCase = true) || field.key.contains("date", ignoreCase = true)
                if (isDateField) {
                    val dateStr = formValues[field.key] ?: ""
                    val currentDate = remember(dateStr) {
                        FinanceHelper.parseVoucherDate(dateStr)
                    }
                    val isToday = remember(currentDate) {
                        android.text.format.DateUtils.isToday(currentDate.time)
                    }
                    val isYesterday = remember(currentDate) {
                        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                        val calCurrent = Calendar.getInstance().apply { time = currentDate }
                        cal.get(Calendar.YEAR) == calCurrent.get(Calendar.YEAR) &&
                        cal.get(Calendar.DAY_OF_YEAR) == calCurrent.get(Calendar.DAY_OF_YEAR)
                    }
                    val dateLabelSuffix = when {
                        isToday -> " (${TerminologyManager.getTerm("Today")})"
                        isYesterday -> " (${TerminologyManager.getTerm("Yesterday")})"
                        else -> ""
                    }

                    fun showFormDatePicker() {
                        val cal = Calendar.getInstance().apply { time = currentDate }
                        DatePickerDialog(
                            context,
                            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                                val newCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    set(Calendar.HOUR_OF_DAY, 12)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                formValues[field.key] = submitDateFormatter.format(newCal.time)
                                isSubmitted = false
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showFormDatePicker() }
                    ) {
                        OutlinedTextField(
                            value = "${dateFormatter.format(currentDate)}$dateLabelSuffix",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(field.label) },
                            trailingIcon = {
                                IconButton(onClick = { showFormDatePicker() }) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = TerminologyManager.getTerm("Select Date"),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showFormDatePicker() }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isToday,
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, 12)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                formValues[field.key] = submitDateFormatter.format(cal.time)
                                isSubmitted = false
                            },
                            label = { AutoShrinkText(TerminologyManager.getTerm("Today"), maxFontSize = 14.sp, minFontSize = 10.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        FilterChip(
                            selected = isYesterday,
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    add(Calendar.DAY_OF_YEAR, -1)
                                    set(Calendar.HOUR_OF_DAY, 12)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                formValues[field.key] = submitDateFormatter.format(cal.time)
                                isSubmitted = false
                            },
                            label = { AutoShrinkText(TerminologyManager.getTerm("Yesterday"), maxFontSize = 14.sp, minFontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = !isToday && !isYesterday,
                            onClick = { showFormDatePicker() },
                            label = { AutoShrinkText(TerminologyManager.getTerm("Pick Date"), maxFontSize = 14.sp, minFontSize = 10.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = formValues[field.key] ?: "",
                        onValueChange = {
                            formValues[field.key] = it
                            isSubmitted = false
                        },
                        label = { Text(field.label) },
                        placeholder = { Text(field.placeholder) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (field.isNumber) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    isSubmitted = true
                    onSubmit(formValues.toMap())
                },
                enabled = !isSubmitted,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isSubmitted) TerminologyManager.getTerm("Submitted") else TerminologyManager.getTerm("Submit"))
            }
        }
    }
}

@Composable
fun AudioVisualizer(modifier: Modifier = Modifier) {
    val barCount = 5
    val infiniteTransition = rememberInfiniteTransition(label = "audio_viz")
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(barCount) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 0.3f, targetValue = 1.0f,
                animationSpec = infiniteRepeatable(animation = tween(durationMillis = 300 + (index * 150), easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                label = "bar_$index"
            )
            Box(modifier = Modifier.width(4.dp).fillMaxHeight(height).background(MaterialTheme.colorScheme.onPrimaryContainer, CircleShape))
        }
    }
}
