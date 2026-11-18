package com.minhas.teller.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.dao.StockItemDao
import com.minhas.teller.data.dao.UnitDao
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.utils.paiseToRupees
import com.minhas.teller.utils.rupeesToPaise
import com.minhas.teller.data.entities.Unit
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.utils.AiResponse
import com.minhas.teller.utils.Confidence
import com.minhas.teller.utils.FinanceHelper
import com.minhas.teller.utils.GemmaLLM
import com.minhas.teller.utils.GstComplianceRules
import com.minhas.teller.utils.GstKnowledgeBase
import com.minhas.teller.utils.AiModelPrefs
import com.minhas.teller.utils.AnswerValidator
import com.minhas.teller.utils.DeepSeekClient
import com.minhas.teller.utils.IntentClassifier
import com.minhas.teller.utils.SmartFinancePrompts
import com.minhas.teller.utils.ReplyLocalizer
import com.minhas.teller.utils.SystemTtsEngine
import com.minhas.teller.utils.TtsModelPrefs
import com.minhas.teller.utils.ChartDataset
import com.minhas.teller.SessionManager
import com.minhas.teller.data.dao.ChatMessageDao
import com.minhas.teller.data.entities.ChatMessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class FormField(
    val key: String,
    val label: String,
    val placeholder: String = "",
    val isNumber: Boolean = false
)

sealed class ChatMessage {
    abstract val isUser: Boolean
    abstract val timestamp: Long
    
    data class Text(
        val text: String,
        override val isUser: Boolean,
        override val timestamp: Long = System.currentTimeMillis(),
        val shouldSpeak: Boolean = true
    ) : ChatMessage()
    
    data class ReviewVoucher(
        val draft: AiResponse.DraftVoucher,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage() { override val isUser = false }

    data class ReviewLedger(
        val draft: AiResponse.DraftLedger,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage() { override val isUser = false }

    data class Success(
        val text: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage() { override val isUser = false }

    data class InteractiveForm(
        val formId: String,
        val title: String,
        val fields: List<FormField>,
        val initialValues: Map<String, String> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage() { override val isUser = false }

    // ── New: AI-generated chart and table messages ──
    data class ChartMessage(
        val chartData: com.minhas.teller.utils.AiResponse.QueryChart,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage() { override val isUser = false }

    data class TableMessage(
        val tableData: com.minhas.teller.utils.AiResponse.QueryDataTable,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage() { override val isUser = false }
}

// Multi-turn conversation state — persists across messages
sealed class PendingConversation {
    // "gave money to Amit" — waiting for amount
    data class PaymentNeedsAmount(val party: String, val mode: String = "Cash-in-hand", val date: String? = null) : PendingConversation()
    // "received from Amit" — waiting for amount  
    data class ReceiptNeedsAmount(val party: String, val mode: String = "Cash-in-hand", val date: String? = null) : PendingConversation()
    // Finance sale — collects: financier, item, customer, loanAmt, downPayment step by step
    data class FinanceSale(
        val financier: String? = null,
        val item: String? = null,
        val customer: String? = null,
        val loanAmount: Double? = null,
        val downPayment: Double? = null,
        val date: String? = null,
        val awaitingField: String = "financier" // financier|item|customer|loanAmount|downPayment
    ) : PendingConversation()
    // Simple sale — waiting for amount
    data class SaleNeedsAmount(val item: String, val party: String, val date: String? = null) : PendingConversation()
    // Generic sale/purchase missing amount, item and/or party — collects whichever is missing,
    // in that order, then builds the voucher from whatever was already known plus the replies.
    data class TransactionNeedsInfo(
        val txnType: String,       // "Sales" or "Purchase"
        val item: String? = null,
        val party: String? = null,
        val amount: Double? = null,
        val qty: Double? = null,
        val date: String? = null,
        val awaitingField: String  // "amount" | "item" | "party"
    ) : PendingConversation()
}

sealed class AiModelStatus {
    object NotDownloaded : AiModelStatus()
    data class Downloading(val progress: Int, val stage: String) : AiModelStatus()
    object Loading : AiModelStatus()
    object Ready : AiModelStatus()
    data class Error(val message: String) : AiModelStatus()
}

data class ChatSession(
    val sessionId: String,
    val title: String,
    val preview: String,
    val lastTimestamp: Long
)

class ChatViewModel(
    private val appContext: Context,
    private val voucherRepository: VoucherRepository,
    private val ledgerRepository: LedgerRepository,
    private val stockItemDao: StockItemDao,
    // NOTE: stockItemDao is listed before unitDao in constructor
    private val unitDao: UnitDao,
    private val chatMessageDao: ChatMessageDao
) : ViewModel() {

    // Prevents concurrent sendMessage calls from racing on _isLoading
    private val sendMutex = Mutex()

    // Active multi-turn conversation state
    private var pendingConversation: PendingConversation? = null

    // Tracks when DeepSeek asked a clarifying question — user's reply must
    // skip regex and go straight back to DeepSeek with full conversation context
    private var clarificationContext: String? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _history = MutableStateFlow<List<ChatSession>>(emptyList())
    val history: StateFlow<List<ChatSession>> = _history.asStateFlow()

    // Unique id for the current conversation; a new one is created each time the screen opens.
    private var sessionId: String = System.currentTimeMillis().toString()

    // Independent scope so a final history save can finish even after the ViewModel is cleared
    // (triggered from DisposableEffect when the user leaves the chat screen).
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // One-shot signal telling the screen to auto-open the mic: fired every time Helafin asks a
    // follow-up question (missing amount/item/party etc.) so the user can just speak the answer
    // instead of having to tap the mic button again. A timestamp (not a Boolean) so each firing is
    // a distinct value and always retriggers the collector, even for back-to-back questions.
    private val _autoListenRequest = MutableStateFlow(0L)
    val autoListenRequest: StateFlow<Long> = _autoListenRequest.asStateFlow()
    private fun requestAutoListen() { _autoListenRequest.value = System.currentTimeMillis() }

    private val _aiModelStatus = MutableStateFlow<AiModelStatus>(AiModelStatus.NotDownloaded)
    val aiModelStatus: StateFlow<AiModelStatus> = _aiModelStatus.asStateFlow()

    /** Reactive currency symbol — follows the active company, defaults to ₹ */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currencySymbol: StateFlow<String> = SessionManager.currentCompanyId
        .flatMapLatest { id ->
            if (id == null) flowOf("₹")
            else AppDatabase.getDatabase(appContext).companyDao().getCompanyById(id)
                .map { it?.currencySymbol ?: "₹" }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "₹")

    init {
        _messages.value = listOf(greetingMessage())
        monitorModelStatus()
        ensureOfflineModelDownloading()
    }

    private fun greetingMessage(): ChatMessage.Text = ChatMessage.Text(
        "Hi, I'm Helafin — your smart finance assistant.\n\n" +
        "🎙️ **Voice entry**: Tap the mic and say \"Sold 10 chairs to Ramu for 5000\"\n" +
        "📊 **Reports**: \"Show my sales this month\" or \"Who owes me money?\"\n" +
        "📜 **GST & Tax**: Ask me about GST rates, ITC, TDS, e-invoicing, ITR\n" +
        "📱 **App help**: \"How do I add a ledger?\" or \"How to create a purchase entry?\"\n\n" +
        "What can I help you with?",
        false,
        shouldSpeak = false
    )

    /** Persist the current conversation so it appears in the history list. */
    fun saveCurrentSession() {
        val companyId = SessionManager.currentCompanyId.value ?: return
        val textMessages = _messages.value.filterIsInstance<ChatMessage.Text>()
        if (textMessages.none { it.isUser }) return
        val session = sessionId
        persistScope.launch {
            try {
                chatMessageDao.softDeleteSession(session)
                val entities = textMessages.map { msg ->
                    ChatMessageEntity(
                        messageType = "text",
                        text = msg.text,
                        isUser = msg.isUser,
                        timestamp = Date(msg.timestamp),
                        shouldSpeak = msg.shouldSpeak,
                        companyId = companyId.toLong(),
                        sessionId = session,
                        createdAt = Date(msg.timestamp),
                        updatedAt = Date(msg.timestamp)
                    )
                }
                chatMessageDao.insertAll(entities)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to save chat history", e)
            }
        }
    }

    /** Refresh the history list with the 10 most recent conversations. */
    fun loadHistory() {
        val companyId = SessionManager.currentCompanyId.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val all = chatMessageDao.getMessagesForCompany(companyId.toLong())
                val sessions = all
                    .filter { !it.isDeleted }
                    .groupBy { it.sessionId }
                    .mapNotNull { (sid, msgs) ->
                        val sorted = msgs.sortedBy { it.timestamp }
                        val title = sorted.firstOrNull { it.isUser && !it.text.isNullOrBlank() }?.text
                            ?: sorted.firstOrNull { !it.text.isNullOrBlank() }?.text
                            ?: return@mapNotNull null
                        val preview = sorted.lastOrNull { !it.text.isNullOrBlank() }?.text ?: title
                        ChatSession(sid, title, preview, sorted.last().timestamp.time)
                    }
                    .sortedByDescending { it.lastTimestamp }
                    .take(10)
                _history.value = sessions
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to load chat history", e)
            }
        }
    }

    /** Reopen a past conversation. */
    fun loadSession(id: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val restored = chatMessageDao.getMessagesBySession(id)
                    .filter { !it.isDeleted && it.text != null }
                    .sortedBy { it.timestamp }
                    .map { ChatMessage.Text(it.text!!, it.isUser, it.timestamp.time, it.shouldSpeak) }
                sessionId = id
                pendingConversation = null
                clarificationContext = null
                _messages.value = if (restored.isEmpty()) listOf(greetingMessage()) else restored
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to load session $id", e)
            }
        }
    }

    /** Start a brand-new conversation. */
    fun startNewSession() {
        sessionId = System.currentTimeMillis().toString()
        pendingConversation = null
        clarificationContext = null
        _messages.value = listOf(greetingMessage())
    }

    private fun monitorModelStatus() {
        viewModelScope.launch {
            val workManager = androidx.work.WorkManager.getInstance(appContext)
            workManager.getWorkInfosForUniqueWorkFlow(FinanceHelper.OFFLINE_MLC_DOWNLOAD_WORK_NAME)
                .collect { workInfos ->
                    val modelFile = AiModelPrefs.getModelFile(appContext)
                    val fileExists = modelFile.exists() && modelFile.length() >= AiModelPrefs.DEFAULT_MIN_SIZE
                    
                    val info = workInfos.firstOrNull()
                    if (info != null) {
                        when (info.state) {
                            androidx.work.WorkInfo.State.RUNNING -> {
                                val progress = info.progress.getInt("progress", 0)
                                val stage = info.progress.getString("stage") ?: "Downloading..."
                                _aiModelStatus.value = AiModelStatus.Downloading(progress, stage)
                            }
                            androidx.work.WorkInfo.State.ENQUEUED -> {
                                _aiModelStatus.value = AiModelStatus.Downloading(0, "Queued...")
                            }
                            androidx.work.WorkInfo.State.FAILED -> {
                                if (fileExists) {
                                    checkAndLoadModel()
                                } else {
                                    val error = info.outputData.getString("error") ?: "Download failed"
                                    _aiModelStatus.value = AiModelStatus.Error(error)
                                }
                            }
                            else -> {
                                if (fileExists) {
                                    checkAndLoadModel()
                                } else {
                                    _aiModelStatus.value = AiModelStatus.NotDownloaded
                                }
                            }
                        }
                    } else {
                        if (fileExists) {
                            checkAndLoadModel()
                        } else {
                            _aiModelStatus.value = AiModelStatus.NotDownloaded
                        }
                    }
                }
        }
    }

    fun checkAndLoadModel() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val modelFile = AiModelPrefs.getModelFile(appContext)
            if (!modelFile.exists() || modelFile.length() < AiModelPrefs.DEFAULT_MIN_SIZE) {
                _aiModelStatus.value = AiModelStatus.NotDownloaded
                return@launch
            }

            if (GemmaLLM.isLoaded()) {
                _aiModelStatus.value = AiModelStatus.Ready
                return@launch
            }

            _aiModelStatus.value = AiModelStatus.Loading
            try {
                android.util.Log.d("ChatViewModel", "Loading Gemma model from ${modelFile.absolutePath} (${modelFile.length() / 1024 / 1024} MB)")
                GemmaLLM.loadModel(appContext, modelFile.absolutePath)
                if (GemmaLLM.isLoaded()) {
                    // ── Self-test: run a quick inference to verify the model actually works ──
                    _aiModelStatus.value = AiModelStatus.Loading  // stay in loading during test
                    try {
                        val testOutput = GemmaLLM.suggestVoucher("hello")
                        if (!testOutput.isNullOrBlank()) {
                            android.util.Log.d("ChatViewModel", "Gemma self-test passed: '$testOutput'")
                            _aiModelStatus.value = AiModelStatus.Ready
                        } else {
                            android.util.Log.w("ChatViewModel", "Gemma self-test returned empty — model loaded but produces no output")
                            _aiModelStatus.value = AiModelStatus.Ready  // still mark ready, may work for other inputs
                        }
                    } catch (testEx: Throwable) {
                        android.util.Log.e("ChatViewModel", "Gemma self-test FAILED: ${testEx.message}", testEx)
                        // Model loaded but inference crashed — mark as error
                        GemmaLLM.unload()
                        _aiModelStatus.value = AiModelStatus.Error("Model loaded but inference failed: ${testEx.message}")
                        return@launch
                    }
                } else {
                    val err = GemmaLLM.getLoadError() ?: "Failed to initialize Gemma model."
                    android.util.Log.e("ChatViewModel", "Gemma load failed: $err")
                    // MODEL_CORRUPT sentinel means the file was corrupt/incomplete and has been
                    // deleted by GemmaLLM — show NotDownloaded so the user can re-download cleanly.
                    if (err == "MODEL_CORRUPT") {
                        android.util.Log.w("ChatViewModel", "Corrupt model file deleted — resetting to NotDownloaded")
                        _aiModelStatus.value = AiModelStatus.NotDownloaded
                    } else {
                        _aiModelStatus.value = AiModelStatus.Error(err)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Gemma load exception: ${e.message}", e)
                _aiModelStatus.value = AiModelStatus.Error(e.message ?: "Unknown loading error")
            }
        }
    }

    fun forceReloadModel() {
        _aiModelStatus.value = AiModelStatus.Loading
        checkAndLoadModel()
    }

    /**
     * Auto-start the offline AI model download the first time the finance helper opens, so the
     * assistant works on-device without the user having to tap "Install". No-ops when the model is
     * already installed (or a download is already queued/running, since the work policy is KEEP).
     */
    fun ensureOfflineModelDownloading() {
        val modelFile = AiModelPrefs.getModelFile(appContext)
        val alreadyInstalled = modelFile.exists() && modelFile.length() >= AiModelPrefs.DEFAULT_MIN_SIZE
        if (!alreadyInstalled) {
            FinanceHelper.enqueueOfflineModelDownload(appContext)
        }
    }

    fun submitForm(formId: String, values: Map<String, String>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (formId) {
                    "FORM_PAYMENT" -> {
                        val party = values["party"]?.trim() ?: ""
                        val amountStr = values["amount"]?.trim() ?: "0"
                        val amount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
                        val mode = values["mode"]?.trim() ?: "Cash-in-hand"
                        val date = values["date"]?.trim() ?: "today"
                        val narration = values["narration"]?.trim() ?: ""

                        if (party.isEmpty() || amount <= 0) {
                            _messages.value = _messages.value + ChatMessage.Text("Invalid payment details. Party and amount are required.", false)
                            _isLoading.value = false
                            return@launch
                        }

                        val draft = com.minhas.teller.utils.AiResponse.DraftVoucher(
                            type = "Payment",
                            date = date,
                            voucherNumber = null,
                            entries = listOf(
                                com.minhas.teller.utils.DraftEntry(party, true, amount),
                                com.minhas.teller.utils.DraftEntry(mode, false, amount)
                            ),
                            inventoryItems = emptyList(),
                            narration = if (narration.isNotEmpty()) narration else "Payment to $party ₹$amount"
                        )
                        _messages.value = _messages.value + ChatMessage.ReviewVoucher(draft)
                    }
                    "FORM_RECEIPT" -> {
                        val party = values["party"]?.trim() ?: ""
                        val amountStr = values["amount"]?.trim() ?: "0"
                        val amount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
                        val mode = values["mode"]?.trim() ?: "Cash-in-hand"
                        val date = values["date"]?.trim() ?: "today"
                        val narration = values["narration"]?.trim() ?: ""

                        if (party.isEmpty() || amount <= 0) {
                            _messages.value = _messages.value + ChatMessage.Text("Invalid receipt details. Party and amount are required.", false)
                            _isLoading.value = false
                            return@launch
                        }

                        val draft = com.minhas.teller.utils.AiResponse.DraftVoucher(
                            type = "Receipt",
                            date = date,
                            voucherNumber = null,
                            entries = listOf(
                                com.minhas.teller.utils.DraftEntry(mode, true, amount),
                                com.minhas.teller.utils.DraftEntry(party, false, amount)
                            ),
                            inventoryItems = emptyList(),
                            narration = if (narration.isNotEmpty()) narration else "Receipt from $party ₹$amount"
                        )
                        _messages.value = _messages.value + ChatMessage.ReviewVoucher(draft)
                    }
                    "FORM_FINANCE_SALE" -> {
                        val financier = values["financier"]?.trim() ?: ""
                        val item = values["item"]?.trim() ?: ""
                        val customer = values["customer"]?.trim() ?: ""
                        val loanAmtStr = values["loanAmount"]?.trim() ?: "0"
                        val loanAmount = loanAmtStr.replace(",", "").toDoubleOrNull() ?: 0.0
                        val downPayStr = values["downPayment"]?.trim() ?: "0"
                        val downPayment = downPayStr.replace(",", "").toDoubleOrNull() ?: 0.0
                        val date = values["date"]?.trim() ?: "today"

                        if (financier.isEmpty() || item.isEmpty() || customer.isEmpty() || (loanAmount <= 0 && downPayment <= 0)) {
                            _messages.value = _messages.value + ChatMessage.Text("Invalid finance sale details. Financier, Item, Customer and positive amount are required.", false)
                            _isLoading.value = false
                            return@launch
                        }

                        val fs = PendingConversation.FinanceSale(
                            financier = financier,
                            item = item,
                            customer = customer,
                            loanAmount = loanAmount,
                            downPayment = downPayment,
                            date = date,
                            awaitingField = "done"
                        )
                        val draft = buildFinanceVoucher(fs)
                        _messages.value = _messages.value + ChatMessage.ReviewVoucher(draft)
                    }
                }
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage.Text("Error processing form: ${e.message}", false)
            }
            _isLoading.value = false
        }
    }

    fun sendMessage(query: String) {
        if (query.isBlank()) return
        val userMsg = ChatMessage.Text(query, true)
        val currentHistory = _messages.value
        _messages.value = currentHistory + userMsg
        _isLoading.value = true

        // Check for confirmation keywords if trailing messages are review cards
        val lastMessage = currentHistory.lastOrNull()
        val confirmationKeywords = setOf("confirm", "save", "yes", "ok", "yup", "theek hai", "sahi hai", "kar do", "create", "go ahead")
        if (confirmationKeywords.any { query.lowercase(Locale.ROOT).contains(it) }) {
            val trailingReviews = currentHistory.takeLastWhile { it is ChatMessage.ReviewVoucher || it is ChatMessage.ReviewLedger }
            if (trailingReviews.isNotEmpty()) {
                confirmReviews(trailingReviews)
                return
            }
        }

        // ── Multi-turn conversation: intercept reply if we're mid-workflow ──
        val pending = pendingConversation
        if (pending != null) {
            // A follow-up question only ever expects one short answer (an amount, a name, an
            // item) — if the user instead typed/spoke a whole new sentence with its own
            // transaction verb (sale/purchase/payment/receipt), that's a fresh, unrelated command,
            // not an answer to the dangling question. Without this check, e.g. a clean "Ravi ko
            // 3000 diye" sent right after some earlier, unrelated clarification got stuck pending
            // would be swallowed whole as if it were the answer to that stale question instead of
            // being parsed as its own Payment.
            val looksLikeFreshTransaction = query.trim().split(Regex("\\s+")).size > 4 &&
                Regex("\\b(?:diya|diye|de diya|paid|pay|gave|given|liya|liye|received|mila|mile|bech[a-z]*|kharid[a-z]*|khareed[a-z]*|sold|bought|purchase[a-z]*)\\b", RegexOption.IGNORE_CASE).containsMatchIn(query)
            if (looksLikeFreshTransaction) {
                pendingConversation = null
                // fall through — process below exactly as if nothing were pending
            } else {
                viewModelScope.launch {
                    sendMutex.withLock {
                        _isLoading.value = true
                        handlePendingReply(query, pending)
                        _isLoading.value = false
                    }
                }
                return
            }
        }

        // ── Clarification context: if DeepSeek asked a question and the user
        // gave a short reply (≤3 words), skip regex and go straight back to
        // DeepSeek with full conversation context. Longer replies are treated
        // as new standalone commands that Tier 1 regex can handle. ──
        val activeClarification = clarificationContext
        if (activeClarification != null && query.split(" ").size <= 3) {
            clarificationContext = null // consume it
            viewModelScope.launch {
                sendMutex.withLock {
                    _isLoading.value = true
                    val companyId = SessionManager.currentCompanyId.value
                    if (companyId != null) {
                        // Build a contextual query that combines the user's reply
                        // with the previous topic so DeepSeek stays on track
                        val contextualQuery = "Previous topic: $activeClarification\nUser's reply: $query"
                        handleDeepSeekResponse(companyId, contextualQuery)
                    }
                    _isLoading.value = false
                }
            }
            return
        }
        if (activeClarification != null && query.split(" ").size > 3) {
            // User typed a full new command — clear the clarification and let
            // regex handle it normally
            clarificationContext = null
        }

        viewModelScope.launch {
            sendMutex.withLock {
            _isLoading.value = true
            val companyId = SessionManager.currentCompanyId.value
            if (companyId == null) {
                _messages.value = _messages.value + ChatMessage.Text(localize("You haven't selected a company yet. Please go back to the Company List and select one to start using Helafin."), false)
                _isLoading.value = false
                return@launch
            }

            // ── Intent gate: knowledge/legal/help questions are answered directly (with grounding
            //    and escalation) and must NEVER reach the transaction regex parsers below. This is
            //    the fix for "asked a GST question → it drafted a voucher". ──
            if (IntentClassifier.classify(query) == IntentClassifier.Intent.QUESTION) {
                answerQuestion(query)
                _isLoading.value = false
                return@launch
            }

            val contextData = getFinancialContext()
            val history = formatHistory(currentHistory)
            android.util.Log.w("FinanceHelper", "Q => \"$query\"")

            // ── Upfront multi-turn detection on ORIGINAL query (before FinanceHelper) ──
            val qLowOriginal = query.lowercase(Locale.ROOT)
            val hasAmountOriginal = Regex("\\d+").containsMatchIn(query)
            val upfrontHandledOriginal = when {
                // "gave money to X" / "paid X" / "diya X" with no amount
                !hasAmountOriginal && (qLowOriginal.contains("gave") || qLowOriginal.contains("give") || qLowOriginal.contains("paid") || qLowOriginal.contains("diya") || qLowOriginal.contains("de diya")) -> {
                    val party = extractSimpleParty(qLowOriginal) ?: ""
                    val knownDate = com.minhas.teller.utils.FinanceHelper.parseDateFromQuery(query) ?: "today"
                    val fields = listOf(
                        FormField("party", "Paid To (Party Name)", placeholder = "Enter party name", isNumber = false),
                        FormField("amount", "Amount (${currencySymbol.value})", placeholder = "Enter amount", isNumber = true),
                        FormField("mode", "Payment Mode (Cash/Bank)", placeholder = "Cash-in-hand or Bank name", isNumber = false),
                        FormField("date", "Voucher Date", placeholder = "e.g. today, yesterday, dd/MM/yyyy", isNumber = false),
                        FormField("narration", "Narration", placeholder = "Payment details (optional)", isNumber = false)
                    )
                    val initialValues = mapOf(
                        "party" to party,
                        "mode" to "Cash-in-hand",
                        "date" to knownDate,
                        "narration" to (if (party.isNotEmpty()) "Payment to $party" else "")
                    )
                    _messages.value = _messages.value + ChatMessage.InteractiveForm(
                        formId = "FORM_PAYMENT",
                        title = "Payment Details Form",
                        fields = fields,
                        initialValues = initialValues
                    )
                    true
                }
                // "received from X" / "mila X" with no amount
                !hasAmountOriginal && (qLowOriginal.contains("received") || qLowOriginal.contains("mila") || qLowOriginal.contains("liya")) -> {
                    val party = extractSimpleParty(qLowOriginal) ?: ""
                    val knownDate = com.minhas.teller.utils.FinanceHelper.parseDateFromQuery(query) ?: "today"
                    val fields = listOf(
                        FormField("party", "Received From (Party Name)", placeholder = "Enter party name", isNumber = false),
                        FormField("amount", "Amount (${currencySymbol.value})", placeholder = "Enter amount", isNumber = true),
                        FormField("mode", "Receipt Mode (Cash/Bank)", placeholder = "Cash-in-hand or Bank name", isNumber = false),
                        FormField("date", "Voucher Date", placeholder = "e.g. today, yesterday, dd/MM/yyyy", isNumber = false),
                        FormField("narration", "Narration", placeholder = "Receipt details (optional)", isNumber = false)
                    )
                    val initialValues = mapOf(
                        "party" to party,
                        "mode" to "Cash-in-hand",
                        "date" to knownDate,
                        "narration" to (if (party.isNotEmpty()) "Receipt from $party" else "")
                    )
                    _messages.value = _messages.value + ChatMessage.InteractiveForm(
                        formId = "FORM_RECEIPT",
                        title = "Receipt Details Form",
                        fields = fields,
                        initialValues = initialValues
                    )
                    true
                }
                // "IDFC financed" / "financed a vehicle" / "X finance" - always show form with extracted info
                (qLowOriginal.contains("finance") || qLowOriginal.contains("financed") || qLowOriginal.contains("loan")) &&
                !qLowOriginal.contains("balance") && !qLowOriginal.contains("account") -> {
                    val knownFinancier = detectFinancierName(qLowOriginal) ?: ""
                    val knownItem = detectVehicleName(qLowOriginal) ?: ""
                    val knownCustomer = detectCustomerName(qLowOriginal) ?: ""
                    val knownDate = com.minhas.teller.utils.FinanceHelper.parseDateFromQuery(query) ?: "today"
                    
                    // Extract amounts from the text
                    val amounts = Regex("\\d[\\d,]*(?:\\.\\d+)?").findAll(query)
                        .mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
                        .toList()
                    
                    val knownLoanAmount = if (amounts.isNotEmpty()) formatAmount(amounts[0]) else ""
                    val knownDownPayment = if (amounts.size > 1) formatAmount(amounts[1]) else ""
                    android.util.Log.w("FinanceHelper", "FINANCE_FORM financier=\"$knownFinancier\" item=\"$knownItem\" customer=\"$knownCustomer\" loan=\"$knownLoanAmount\" down=\"$knownDownPayment\"")
                    
                    val fields = listOf(
                        FormField("financier", "Financier / Bank Name", placeholder = "e.g. IDFC Bank, Bajaj Finance", isNumber = false),
                        FormField("item", "Vehicle Model / Item Name", placeholder = "e.g. Splendor, Activa", isNumber = false),
                        FormField("customer", "Customer Name", placeholder = "Enter customer name", isNumber = false),
                        FormField("loanAmount", "Loan Amount (${currencySymbol.value})", placeholder = "Enter loan amount", isNumber = true),
                        FormField("downPayment", "Down Payment (${currencySymbol.value})", placeholder = "Enter down payment", isNumber = true),
                        FormField("date", "Voucher Date", placeholder = "e.g. today, yesterday, dd/MM/yyyy", isNumber = false)
                    )
                    val initialValues = mapOf(
                        "financier" to knownFinancier,
                        "item" to knownItem,
                        "customer" to knownCustomer,
                        "loanAmount" to knownLoanAmount,
                        "downPayment" to knownDownPayment,
                        "date" to knownDate
                    )
                    _messages.value = _messages.value + ChatMessage.InteractiveForm(
                        formId = "FORM_FINANCE_SALE",
                        title = "Finance Sale Information Form",
                        fields = fields,
                        initialValues = initialValues
                    )
                    true
                }
                else -> false
            }
            if (upfrontHandledOriginal) {
                _isLoading.value = false
                return@launch
            }

            // Chart/graph requests render a real visual chart. Try locally first (free, on-device,
            // works without DeepSeek); fall back to DeepSeek only if the intent is unclear.
            val qChartCheck = query.lowercase(Locale.ROOT)
            if (qChartCheck.contains("chart") || qChartCheck.contains("graph")) {
                val localChart = tryGenerateLocalChart(companyId, query)
                if (localChart != null) {
                    _messages.value = _messages.value + ChatMessage.ChartMessage(localChart)
                } else {
                    handleDeepSeekResponse(companyId, query)
                }
                _isLoading.value = false
                return@launch
            }

            // ═══════════════════════════════════════════════════════════
            // ═══════════════════════════════════════════════════════════
            // TIER 1: Local fast pattern matcher (<2ms)
            // ═══════════════════════════════════════════════════════════
            val aiResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                FinanceHelper.chatWithFinance(appContext, query, contextData, history)
            }
            android.util.Log.w("FinanceHelper", "PARSE source=${aiResult.source} conf=${aiResult.confidence} resp=${aiResult.response}")

            // HIGH confidence — instant local match (zero latency)
            if (aiResult.confidence == Confidence.HIGH) {
                android.util.Log.i("ChatViewModel", "AI query handled locally (source=${aiResult.source}): \"$query\"")
                dispatchResponse(aiResult.response, companyId, query)
                _isLoading.value = false
                return@launch
            }

            // ═══════════════════════════════════════════════════════════
            // TIER 2: DeepSeek Cloud AI (Handles reasoning, advice, knowledge, and complex vouchers)
            // ═══════════════════════════════════════════════════════════
            android.util.Log.i("ChatViewModel", "Routing query directly to DeepSeek Cloud AI: \"$query\"")
            handleDeepSeekResponse(companyId, query)

            _isLoading.value = false
            } // end sendMutex.withLock
        }
    }

    /**
     * Synthesizes a GST-law answer on-device from pre-retrieved [context] excerpts via Gemma.
     * Returns null if the model isn't available, so callers fall back to cloud or raw cited text.
     */
    private suspend fun synthesizeGstAnswer(query: String, context: String): String? {
        return try {
            val modelFile = AiModelPrefs.getModelFile(appContext)
            if (!GemmaLLM.isLoaded() && modelFile.exists() && modelFile.length() >= AiModelPrefs.DEFAULT_MIN_SIZE) {
                kotlinx.coroutines.withContext(Dispatchers.Default) { GemmaLLM.loadModel(appContext, modelFile.absolutePath) }
            }
            if (!GemmaLLM.isLoaded()) return null
            GemmaLLM.answerGstQuestion(query, context)
        } catch (t: Throwable) {
            null
        }
    }

    // ── Grounded question-answering (knowledge/legal/help) ─────────────────

    /**
     * Answers a knowledge/legal/help question without ever drafting a voucher.
     * Tax/law questions are grounded in the bundled GST corpus (RAG); general questions go through
     * the on-device model with a DeepSeek second opinion. Escalates to cloud / "ask your CA" whenever
     * the local answer is low-confidence.
     */
    private suspend fun answerQuestion(query: String) {
        if (SmartFinancePrompts.isTaxLawQuery(query)) {
            answerTaxQuestion(query)
        } else {
            answerGeneralQuestion(query)
        }
    }

    private suspend fun answerTaxQuestion(query: String) {
        val chunks = GstKnowledgeBase.retrieve(appContext, query, 3)
        if (chunks.isEmpty()) {
            escalateTaxToCloud(query, null)
            return
        }
        val context = chunks.joinToString("\n\n") { c -> "[${c.source}, p.${c.page}] ${c.text}" }
        val synthesized = synthesizeGstAnswer(query, context)
        if (synthesized != null && AnswerValidator.isGrounded(synthesized, chunks.map { it.text })) {
            _messages.value = _messages.value + ChatMessage.Text(synthesized, false)
            return
        }
        escalateTaxToCloud(query, chunks)
    }

    private suspend fun escalateTaxToCloud(query: String, fallbackChunks: List<GstKnowledgeBase.Chunk>?) {
        val cloud = DeepSeekClient.smartChat(query)
        if (cloud != null) {
            _messages.value = _messages.value + ChatMessage.Text(cloud, false)
            return
        }
        if (!fallbackChunks.isNullOrEmpty()) {
            val raw = fallbackChunks.joinToString("\n\n") { c ->
                "— ${c.text.trim()}\n   (${c.source}, p.${c.page})"
            }
            _messages.value = _messages.value + ChatMessage.Text(
                "I couldn't verify this against the latest law, but here are the relevant sections:\n\n$raw\n\nPlease confirm with your CA.",
                false
            )
            return
        }
        _messages.value = _messages.value + ChatMessage.Text(
            localize("I'm not confident about this. Please rephrase, or confirm with your CA."),
            false
        )
    }

    private suspend fun answerGeneralQuestion(query: String) {
        val local = GemmaLLM.answerKnowledgeQuestion(query)

        if (local == null || AnswerValidator.isLowConfidence(local)) {
            val cloud = DeepSeekClient.smartChat(query)
            if (cloud != null) {
                _messages.value = _messages.value + ChatMessage.Text(cloud, false)
            } else {
                _messages.value = _messages.value + ChatMessage.Text(
                    localize("I'm not confident I can answer this correctly. Please rephrase, or confirm with your CA."),
                    false
                )
            }
            return
        }

        // Second opinion: let DeepSeek verify/correct the on-device answer. If DeepSeek is
        // unavailable (offline), show the local answer as-is.
        val verified = DeepSeekClient.verifyAnswer(query, local)
        _messages.value = _messages.value + ChatMessage.Text(verified ?: local, false)
    }

    /**
     * Generates a chart on-device (no cloud) for common chart requests.
     * Returns null if the intent can't be determined, so callers can fall back to DeepSeek.
     */
    private suspend fun tryGenerateLocalChart(companyId: Int, query: String): AiResponse.QueryChart? {
        val q = query.lowercase(Locale.ROOT)
        val isPie = q.contains("pie")
        val isLine = q.contains("line")

        val metrics = mutableListOf<Pair<String, String>>()
        if (q.contains("sale") || q.contains("revenue") || q.contains("income")) metrics.add("Sales" to "Sales")
        if (q.contains("purchase") || q.contains("buy") || q.contains("bought") || q.contains("cost")) metrics.add("Purchase" to "Purchase")
        if (q.contains("expense") || q.contains("spend") || q.contains("payment") || q.contains("money out")) metrics.add("Expenses" to "Payment")
        if (q.contains("receipt") || q.contains("money in") || q.contains("received")) metrics.add("Receipts" to "Receipt")
        if (metrics.isEmpty()) return null

        val months = when {
            q.contains("year") || q.contains("annual") -> 12
            q.contains("3 month") || q.contains("three month") -> 3
            else -> 6
        }

        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val vouchers = voucherRepository.getAllVouchersWithAllEntries(companyId)

        if (isPie) {
            val totals = metrics.map { (_, vtype) ->
                vouchers.filter { it.voucher.type == vtype }
                    .sumOf { v -> v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees() }
            }
            val labels = metrics.map { it.first }
            val csv = buildString {
                append("Label,Amount\n")
                metrics.forEachIndexed { i, (label, _) -> append(label).append(",").append(totals[i]).append("\n") }
            }
            return AiResponse.QueryChart("pie", "Breakdown", labels, listOf(ChartDataset("Amount", totals)), csv)
        }

        // Bar / line — monthly aggregates (oldest → newest)
        val buckets = mutableListOf<Pair<Int, Int>>()
        for (i in months - 1 downTo 0) {
            val c = Calendar.getInstance(); c.add(Calendar.MONTH, -i)
            buckets.add(c.get(Calendar.YEAR) to c.get(Calendar.MONTH))
        }
        val labels = buckets.map { monthNames[it.second] }
        val totals = Array(metrics.size) { DoubleArray(buckets.size) }
        vouchers.forEach { vwe ->
            val vc = Calendar.getInstance().apply { time = vwe.voucher.date }
            val idx = buckets.indexOf(vc.get(Calendar.YEAR) to vc.get(Calendar.MONTH))
            if (idx != -1) {
                metrics.forEachIndexed { mi, (_, vtype) ->
                    if (vwe.voucher.type == vtype) {
                        totals[mi][idx] += vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
                    }
                }
            }
        }
        val datasets = metrics.mapIndexed { i, (label, _) -> ChartDataset(label, totals[i].toList()) }
        val title = if (metrics.size == 1) "${metrics[0].first} — Last $months months" else "Comparison — Last $months months"
        val csv = buildString {
            append("Month")
            metrics.forEach { append(",").append(it.first) }
            append("\n")
            for (i in buckets.indices) {
                append(labels[i])
                metrics.forEachIndexed { mi, _ -> append(",").append(totals[mi][i]) }
                append("\n")
            }
        }
        return AiResponse.QueryChart(if (isLine) "line" else "bar", title, labels, datasets, csv)
    }

    /**
     * Central dispatcher for all AiResponse subtypes.
     * Extracted from sendMessage() so both Tier 1 and Tier 3 can use it.
     */
    private suspend fun dispatchResponse(response: AiResponse, companyId: Int, targetQuery: String) {
        when (response) {
            is AiResponse.QueryBalance -> handleQueryBalance(companyId, response.ledgerName)
            is AiResponse.QueryStock -> handleQueryStock(companyId, response.itemName)
            is AiResponse.QuerySales -> handleQuerySales(companyId, response.period)
            is AiResponse.QueryMetric -> handleQueryMetric(companyId, response.metric, response.period)
            is AiResponse.QueryItemSales -> handleQueryItemSales(companyId, response.period, response.groupBy)
            is AiResponse.QueryOutstanding -> handleQueryOutstanding(companyId, response.ledgerName, response.period)
            is AiResponse.QueryTopParties -> handleQueryTopParties(companyId, response.kind, response.period, response.limit)
            is AiResponse.QueryRecentVouchers -> handleQueryRecentVouchers(companyId, response.voucherType, response.period, response.limit)
            is AiResponse.QuerySummary -> handleQuerySummary(companyId)
            is AiResponse.QueryCollectables -> handleQueryCollectables(companyId)
            is AiResponse.QueryCollectablesList -> handleQueryCollectablesList(companyId)
            is AiResponse.QueryPayables -> handleQueryPayables(companyId)
            is AiResponse.QueryPayablesList -> handleQueryPayablesList(companyId)
            is AiResponse.MultiAction -> handleMultiAction(response)
            is AiResponse.VoucherIntentResponse -> processVoucherIntent(response.intent)
            is AiResponse.ParsedTransactions -> handleParsedTransactions(companyId, response.response)
            // ── New: AI-generated chart and table responses ──
            is AiResponse.QueryChart -> _messages.value = _messages.value + ChatMessage.ChartMessage(response)
            is AiResponse.QueryDataTable -> _messages.value = _messages.value + ChatMessage.TableMessage(response)
            is AiResponse.Message -> {
                val msgText = response.text
                val qLow2 = targetQuery.lowercase(Locale.ROOT)
                when {
                    msgText.contains("loan amount", true) || msgText.contains("how much", true) -> {
                        val knownFinancier = detectFinancierName(qLow2)
                        val knownItem = detectVehicleName(qLow2)
                        val knownCustomer = detectCustomerName(qLow2)
                        val knownDate = com.minhas.teller.utils.FinanceHelper.parseDateFromQuery(targetQuery)
                        pendingConversation = PendingConversation.FinanceSale(
                            financier = knownFinancier, item = knownItem, customer = knownCustomer,
                            date = knownDate,
                            awaitingField = when {
                                knownFinancier == null -> "financier"
                                knownItem == null -> "item"
                                knownCustomer == null -> "customer"
                                else -> "loanAmount"
                            }
                        )
                        requestAutoListen()
                    }
                    msgText.contains("how much", true) && (qLow2.contains("paid") || qLow2.contains("gave")) -> {
                        val knownDate = com.minhas.teller.utils.FinanceHelper.parseDateFromQuery(targetQuery)
                        pendingConversation = PendingConversation.PaymentNeedsAmount(detectSimplePartyFromMsg(qLow2) ?: "Party", date = knownDate)
                        requestAutoListen()
                    }
                    msgText.contains("how much", true) && (qLow2.contains("received") || qLow2.contains("mila")) -> {
                        val knownDate = com.minhas.teller.utils.FinanceHelper.parseDateFromQuery(targetQuery)
                        pendingConversation = PendingConversation.ReceiptNeedsAmount(detectSimplePartyFromMsg(qLow2) ?: "Party", date = knownDate)
                        requestAutoListen()
                    }
                }
                _messages.value = _messages.value + ChatMessage.Text(msgText, false)
            }
            is AiResponse.NeedsInfo -> {
                pendingConversation = PendingConversation.TransactionNeedsInfo(
                    txnType = response.txnType, item = response.item, party = response.party,
                    amount = response.amount, qty = response.qty, date = response.date,
                    awaitingField = response.missingField
                )
                _messages.value = _messages.value + ChatMessage.Text(response.question, false)
                requestAutoListen()
            }
            is AiResponse.DraftVoucher -> _messages.value = _messages.value + ChatMessage.ReviewVoucher(response)
            is AiResponse.DraftLedger -> _messages.value = _messages.value + ChatMessage.ReviewLedger(response)
        }
    }

    /**
     * Builds a rich financial context string for the DeepSeek system prompt.
     * Includes ledger names, sales/purchase totals, stock summary, and balances.
     */
    private suspend fun buildFinancialContext(): String {
        val companyId = SessionManager.currentCompanyId.value ?: return ""
        val sb = StringBuilder()
        try {
            val ledgers = ledgerRepository.getAllLedgersSync(companyId)
            val salesTotal = voucherRepository.getTotalSales(companyId)
            val purchaseTotal = voucherRepository.getTotalPurchase(companyId)
            val items = stockItemDao.getAllStockItems(companyId)
            val vouchers = voucherRepository.getAllVouchersWithAllEntries(companyId)

            // Ledger summary
            sb.append("Ledgers (${ledgers.size}): ")
            sb.append(ledgers.take(30).joinToString(", ") { "${it.name} (${it.group})" })
            if (ledgers.size > 30) sb.append(", ...")

            // Financial totals
            sb.append("\nTotal Sales (all time): ₹${String.format("%.2f", salesTotal.paiseToRupees())}")
            sb.append("\nTotal Purchase (all time): ₹${String.format("%.2f", purchaseTotal.paiseToRupees())}")

            // Top stock items
            if (items.isNotEmpty()) {
                sb.append("\nStock items (${items.size}): ")
                sb.append(items.take(15).joinToString(", ") { it.name })
                if (items.size > 15) sb.append(", ...")
            }

            // Recent voucher counts by type
            val typeCounts = vouchers.groupBy { it.voucher.type }.mapValues { it.value.size }
            sb.append("\nRecent voucher counts: ")
            sb.append(typeCounts.entries.joinToString(", ") { "${it.key}: ${it.value}" })

            // Cash and bank balances
            val cashLedgers = ledgers.filter { it.group.equals("Cash-in-hand", true) }
            val bankLedgers = ledgers.filter { it.group.equals("Bank Accounts", true) || it.group.equals("Bank OD A/c", true) }
            if (cashLedgers.isNotEmpty()) sb.append("\nCash accounts: ${cashLedgers.joinToString(", ") { it.name }}")
            if (bankLedgers.isNotEmpty()) sb.append("\nBank accounts: ${bankLedgers.joinToString(", ") { it.name }}")

        } catch (e: Exception) {
            sb.append("\n(Financial context unavailable: ${e.message})")
        }
        return sb.toString()
    }

    /** DeepSeek system prompt — defines Helafin's behavior for cloud AI. */
    private val DEEPSEEK_SYSTEM_PROMPT = """
You are Helafin, an exceptionally intelligent, friendly, and expert financial & accounting AI assistant embedded in the "Teller" Android app.
Your users are Indian small business owners, accountants, and traders. They may communicate in English, Hindi, or Hinglish.

=== YOUR COMPREHENSIVE CAPABILITIES ===
1. Answer ANY question asked by the user: general knowledge, GST rules & tax slabs, income tax, business advice, profit calculations, accounting guidance, app navigation, or conversational queries.
2. Business analytics: check balances, sales, purchases, profit, stock levels, receivables, payables, and top parties based on provided financial context.
3. Voucher drafting: draft Sales, Purchase, Payment, and Receipt vouchers with inventory items.
4. Visualizations: create charts (bar, pie, line) and structured data tables.

=== TRANSACTION RULE (CLASSIFY FIRST, DRAFT ONLY WHEN CERTAIN) ===
Before choosing an action, decide whether the user is ASKING A QUESTION or RECORDING A TRANSACTION.
- If the user is asking a question (why, how, is it, can I, should I, what is, rules, limits, "is it against the law", etc.), ALWAYS respond with {"action":"message","text":"..."} and answer it directly. NEVER draft a voucher for a question.
- Draft a voucher ({"action":"draft_voucher", ...}) ONLY when the user is clearly stating a concrete transaction they want to record (e.g. "sold 50 items to arjit for 5rs", "paid 500 to rahul", "bought 10kg sugar").
- If you are unsure whether it is a question or a transaction, treat it as a question and answer it.
- If a transaction is missing required details (party, amount, etc.), ask for the missing information rather than guessing.

=== JSON RESPONSE FORMAT ===
You MUST respond with valid JSON using one of these actions:

For ANY general question, advice, tax query, knowledge, explanation, or greeting:
{"action":"message","text":"Your direct and helpful answer here"}

For business data queries:
{"action":"query_balance","ledger":"LedgerName"}
{"action":"query_stock","item":"ItemName"}
{"action":"query_sales","period":"today|yesterday|month|total"}
{"action":"query_profit","period":"today|yesterday|month|total"}
{"action":"query_cash_balance"}
{"action":"query_bank_balance"}
{"action":"query_outstanding","ledger":"PartyName","period":"total"}
{"action":"query_top_customers","period":"month","limit":5}
{"action":"query_top_suppliers","period":"month","limit":5}
{"action":"query_top_items","period":"month","limit":5}
{"action":"query_summary"}
{"action":"query_collectables"}
{"action":"query_payables"}

For voucher drafts (USE THIS FOR ALL TRANSACTIONS):
{"action":"draft_voucher","voucher_type":"Sales|Purchase|Payment|Receipt","party_name":"Name","amount":500,"is_cash":true,"date":"today","narration":"Narration text","items":[{"name":"Item","qty":1,"unit":"Kg|Liter|Pcs|Gram|Bag|Box|Meter|Dozen","rate":500}]}
IMPORTANT for "items": ALWAYS specify the correct "unit" (e.g. 'Kg' for kilo/killo/kg, 'Liter' for liter/litre, 'Gram', 'Bag', 'Box', 'Meter', 'Dozen', 'Pcs'). Keep each item name SINGULAR and never put the quantity or unit inside the name — use {"name":"Milk","qty":20,"unit":"Liter","rate":50}, NEVER {"name":"20 milk"} or {"name":"20 liter milk"}. Never merge several distinct items into one name; emit one object per item. If a spoken item already exists in the provided stock list, reuse its exact name.
{"action":"draft_ledger","name":"LedgerName","group":"Sundry Debtors","opening_balance":0}
{"action":"create_ledgers","ledgers":[{"name":"Amit","group":"Sundry Debtors","opening_balance":0},{"name":"Ramesh","group":"Sundry Debtors"}]}

When the user asks to create MULTIPLE parties/ledgers/accounts at once, you MUST return the "create_ledgers" action with a "ledgers" array (one object per party, each with "name" and optional "group"). NEVER reply with a plain "message" saying you created them, and NEVER ask for confirmation for a create request — just return the JSON so the app shows a review card.

For charts:
{"action":"generate_chart","chartType":"bar|pie|line","title":"Chart Title","labels":["A","B","C"],"datasets":[{"label":"Series1","values":[10,20,30]}],"rawCsv":"Label,Series1\nA,10\nB,20\nC,30"}

=== HINGLISH & HINDI ACCOUNTING VOCABULARY ===
You have native mastery over Hinglish, Hindi (Devanagari), and Indian business terminology:
- Transactions: "becha" / "bikri" / "sale kiya" = Sales; "kharida" / "khareeda" / "purchase kiya" = Purchase; "diya" / "diye" / "payment kiya" = Payment; "bheja" / "bheji" / "bhej diya" = sent/gave (item → Sale, money → Payment); "mila" / "mile" / "receive kiya" = Receipt.
- Accounts & Dues: "udhari" / "baaki" / "bakaya" / "lena hai" = Receivables/Dues; "dena hai" = Payables; "hisab" / "khata" / "balance" = Ledger Balance; "rokar" / "nagad" = Cash.
- Units & Quantities: "kilo" / "kg", "litre" / "ltr", "bori" / "bag", "peti" / "box", "darjan" / "dozen", "nag" / "pcs".
- Amounts (CRITICAL): A space-separated number like "83 500" or "1 500" is INDIAN NUMBERING — one amount ₹83,500 / ₹1,500 (space = thousands separator). Only treat two numbers as quantity × rate when an item word sits BETWEEN them (e.g. "5 chappal 500" = 5 × ₹500). So "83 500 ki Splendor" = ONE Splendor priced ₹83,500 — NEVER 83 items at ₹500.
- Items: "kela" = Banana, "aalu" = Potato, "pyaaz" = Onion, "tamatar" = Tomato, "chawal" = Rice, "aata" = Flour, "cheeni" = Sugar, "doodh" = Milk, "tel" = Oil, "dal" = Pulses, "dawai" = Medicine, "kapda" = Cloth, etc.
- If the user speaks in Hinglish or Hindi, you can answer in clean, easy-to-understand Hinglish or Hindi.

=== GOLDEN RULES ===
1. ALWAYS be helpful, accurate, and knowledgeable. Answer questions directly using {"action":"message","text":"..."}.
2. Extract party names, amounts, dates, and items accurately from messy, phonetic, or casual Hinglish input.
3. If the user wants to record a transaction with missing details, ask for the missing information clearly.
4. Default to "today" for dates if not specified.
5. Default to "Cash-in-hand" for cash transactions.
6. Only state tax rates, thresholds, or legal figures you are confident are correct. If the question is outside what you are sure of, say so and recommend the user confirm with a CA — do not invent numbers.
""".trimIndent()

    /** The voice/answer language the user selected (now tied to the natural system voice). */
    private fun spokenLanguage(): SystemTtsEngine.Language =
        TtsModelPrefs.getVoiceLanguage(appContext)

    private fun replyInHindi(): Boolean = spokenLanguage() == SystemTtsEngine.Language.HINDI
    private fun replyInPunjabi(): Boolean = spokenLanguage() == SystemTtsEngine.Language.PUNJABI
    private fun replyInItalian(): Boolean = spokenLanguage() == SystemTtsEngine.Language.ITALIAN
    private fun replyInSpanish(): Boolean = spokenLanguage() == SystemTtsEngine.Language.SPANISH
    private fun replyInFrench(): Boolean = spokenLanguage() == SystemTtsEngine.Language.FRENCH
    private fun replyInRussian(): Boolean = spokenLanguage() == SystemTtsEngine.Language.RUSSIAN

    /** Localizes a factual reply to selected language via the deterministic template localizer. */
    private fun localize(text: String): String =
        ReplyLocalizer.localize(spokenLanguage(), text)

    /**
     * Handles query escalation to DeepSeek cloud AI.
     * Builds financial context, calls the API, parses the JSON response,
     * and dispatches it through the same handler as local responses.
     */
    private suspend fun handleDeepSeekResponse(companyId: Int, query: String) {
        try {
            val financialContext = buildFinancialContext()
            val history = buildDeepSeekHistory()

            val systemPrompt = when (spokenLanguage()) {
                SystemTtsEngine.Language.HINDI -> DEEPSEEK_SYSTEM_PROMPT + "\n\n=== LANGUAGE ===\nAlways write your 'message' action 'text' ENTIRELY in Hindi using the Devanagari script ONLY. NEVER use romanized / Latin-script Hinglish or transliterated Hindi. Keep party names, item names, amounts and voucher numbers exactly as provided."
                SystemTtsEngine.Language.PUNJABI -> DEEPSEEK_SYSTEM_PROMPT + "\n\n=== LANGUAGE ===\nAlways write your 'message' action 'text' ENTIRELY in Punjabi using the Gurmukhi script ONLY. NEVER use romanized / Latin-script Punjabi or Hinglish. Keep party names, item names and amounts exactly as provided."
                SystemTtsEngine.Language.ITALIAN -> DEEPSEEK_SYSTEM_PROMPT + "\n\n=== LANGUAGE ===\nAlways write your 'message' action 'text' ENTIRELY in Italian (Italiano). Keep party names, item names, amounts and voucher numbers exactly as provided."
                SystemTtsEngine.Language.SPANISH -> DEEPSEEK_SYSTEM_PROMPT + "\n\n=== LANGUAGE ===\nAlways write your 'message' action 'text' ENTIRELY in Spanish (Español). Keep party names, item names, amounts and voucher numbers exactly as provided."
                SystemTtsEngine.Language.FRENCH -> DEEPSEEK_SYSTEM_PROMPT + "\n\n=== LANGUAGE ===\nAlways write your 'message' action 'text' ENTIRELY in French (Français). Keep party names, item names, amounts and voucher numbers exactly as provided."
                SystemTtsEngine.Language.RUSSIAN -> DEEPSEEK_SYSTEM_PROMPT + "\n\n=== LANGUAGE ===\nAlways write your 'message' action 'text' ENTIRELY in Russian (Русский). Keep party names, item names, amounts and voucher numbers exactly as provided."
                SystemTtsEngine.Language.ENGLISH -> DEEPSEEK_SYSTEM_PROMPT
            }
            val responseJson = DeepSeekClient.chat(
                systemPrompt = systemPrompt,
                userQuery = query,
                financialContext = financialContext,
                history = history
            )

            if (responseJson != null) {
                val aiResponse = FinanceHelper.parseAiJson(responseJson)
                // If DeepSeek returned a message (clarification, follow-up, etc.),
                // remember the topic so the user's next reply skips regex and goes
                // straight back to DeepSeek with full context.
                if (aiResponse is AiResponse.Message) {
                    clarificationContext = query
                }
                dispatchResponse(aiResponse, companyId, query)
            } else {
                // DeepSeek unavailable (no key, no network, or API error)
                // Fall back to the original "offline mode" message
                _messages.value = _messages.value + ChatMessage.Text(
                    "I'm running in offline mode right now. Here's what I can help with:\n\n" +
                    "📝 Record: 'Sold item to Party for ₹500' or 'Paid ₹1000 to Supplier'\n" +
                    "🔍 Check: 'Show sales today' or 'What's the balance of Cash?'\n" +
                    "📊 Reports: 'Show profit this month' or 'Top customers'\n\n" +
                    "Try rephrasing, or tap the cloud icon to check AI availability.",
                    false
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "DeepSeek escalation failed", e)
            _messages.value = _messages.value + ChatMessage.Text(
                "I hit a snag reaching the cloud AI. Let me help you offline instead — try saying something like \"Sold item to Party for amount\" or \"Show sales today\".",
                false
            )
        }
    }

    /** Builds recent conversation history for DeepSeek context. */
    private fun buildDeepSeekHistory(): List<Map<String, String>> {
        return _messages.value.takeLast(8).mapNotNull { msg ->
            when (msg) {
                is ChatMessage.Text -> mapOf(
                    "role" to if (msg.isUser) "user" else "assistant",
                    "content" to msg.text
                )
                is ChatMessage.ReviewVoucher -> mapOf(
                    "role" to "assistant",
                    "content" to "[Draft ${msg.draft.type} voucher for ${msg.draft.partyName ?: "party"} — ₹${msg.draft.amount ?: 0}]"
                )
                else -> null
            }
        }
    }

    // ── Multi-turn reply handler ────────────────────────────────────────────
    private suspend fun handlePendingReply(reply: String, pending: PendingConversation) {
        val q = reply.lowercase(Locale.ROOT).trim()
        val amount = Regex("\\d[\\d,]*(?:\\.\\d+)?").findAll(reply)
            .mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
            .maxOrNull() ?: 0.0

        val cancelWords = setOf("cancel", "stop", "nevermind", "never mind", "band karo", "chodo", "quit")
        if (cancelWords.any { q.contains(it) }) {
            pendingConversation = null
            _messages.value = _messages.value + ChatMessage.Text(localize("OK, cancelled. What else can I help with?"), false)
            return
        }

        when (pending) {
            is PendingConversation.PaymentNeedsAmount -> {
                if (amount <= 0) {
                    _messages.value = _messages.value + ChatMessage.Text(localize("I didn't catch the amount. How much was paid to ${pending.party}?"), false)
                    requestAutoListen()
                    return
                }
                pendingConversation = null
                val draft = com.minhas.teller.utils.AiResponse.DraftVoucher(
                    type = "Payment", date = pending.date ?: "today", voucherNumber = null,
                    entries = listOf(
                        com.minhas.teller.utils.DraftEntry(pending.party, true, amount),
                        com.minhas.teller.utils.DraftEntry(pending.mode, false, amount)
                    ),
                    inventoryItems = emptyList(),
                    narration = "Payment to ${pending.party} ₹$amount"
                )
                _messages.value = _messages.value + ChatMessage.ReviewVoucher(draft)
            }
            is PendingConversation.ReceiptNeedsAmount -> {
                if (amount <= 0) {
                    _messages.value = _messages.value + ChatMessage.Text(localize("I didn't catch the amount. How much did you receive from ${pending.party}?"), false)
                    requestAutoListen()
                    return
                }
                pendingConversation = null
                val draft = com.minhas.teller.utils.AiResponse.DraftVoucher(
                    type = "Receipt", date = pending.date ?: "today", voucherNumber = null,
                    entries = listOf(
                        com.minhas.teller.utils.DraftEntry(pending.mode, true, amount),
                        com.minhas.teller.utils.DraftEntry(pending.party, false, amount)
                    ),
                    inventoryItems = emptyList(),
                    narration = "Receipt from ${pending.party} ₹$amount"
                )
                _messages.value = _messages.value + ChatMessage.ReviewVoucher(draft)
            }
            is PendingConversation.SaleNeedsAmount -> {
                if (amount <= 0) {
                    _messages.value = _messages.value + ChatMessage.Text(localize("I didn't catch the amount. How much was the sale of ${pending.item}?"), false)
                    requestAutoListen()
                    return
                }
                pendingConversation = null
                val draft = com.minhas.teller.utils.AiResponse.DraftVoucher(
                    type = "Sales", date = pending.date ?: "today", voucherNumber = null,
                    entries = listOf(
                        com.minhas.teller.utils.DraftEntry(pending.party, true, amount),
                        com.minhas.teller.utils.DraftEntry("Sales Account", false, amount)
                    ),
                    inventoryItems = listOf(com.minhas.teller.utils.DraftInventoryItem(pending.item, 1.0, amount, amount)),
                    narration = "Sale of ${pending.item} to ${pending.party}"
                )
                _messages.value = _messages.value + ChatMessage.ReviewVoucher(draft)
            }
            is PendingConversation.FinanceSale -> {
                val updated = when (pending.awaitingField) {
                    "financier" -> {
                        val financier = detectFinancierName(q) ?: reply.trim().replaceFirstChar { it.uppercase() }
                        pending.copy(financier = financier, awaitingField = if (pending.item == null) "item" else if (pending.customer == null) "customer" else "loanAmount")
                    }
                    "item" -> {
                        val item = detectVehicleName(q) ?: reply.trim().replaceFirstChar { it.uppercase() }
                        pending.copy(item = item, awaitingField = if (pending.customer == null) "customer" else "loanAmount")
                    }
                    "customer" -> {
                        val customer = detectCustomerName(q) ?: reply.trim().replaceFirstChar { it.uppercase() }
                        pending.copy(customer = customer, awaitingField = "loanAmount")
                    }
                    "loanAmount" -> {
                        if (amount <= 0) {
                            _messages.value = _messages.value + ChatMessage.Text(localize("What is the loan amount (in ₹)?"), false)
                            requestAutoListen()
                            return
                        }
                        pending.copy(loanAmount = amount, awaitingField = "downPayment")
                    }
                    "downPayment" -> {
                        val dp = when {
                            q.contains("no") || q.contains("zero") || q.contains("kuch nahi") || q.contains("nothing") -> 0.0
                            amount > 0 -> amount
                            else -> {
                                _messages.value = _messages.value + ChatMessage.Text(localize("What is the down payment? Say 'no' if there isn't any."), false)
                                requestAutoListen()
                                return
                            }
                        }
                        pending.copy(downPayment = dp, awaitingField = "done")
                    }
                    else -> pending
                }

                if (updated.awaitingField == "done" || (updated.financier != null && updated.item != null && updated.customer != null && updated.loanAmount != null && updated.downPayment != null)) {
                    pendingConversation = null
                    val draft = buildFinanceVoucher(updated)
                    _messages.value = _messages.value + ChatMessage.ReviewVoucher(draft)
                } else {
                    pendingConversation = updated
                    _messages.value = _messages.value + ChatMessage.Text(financeNextQuestion(updated), false)
                    requestAutoListen()
                }
            }
            is PendingConversation.TransactionNeedsInfo -> handleTransactionNeedsInfoReply(reply, q, amount, pending)
        }
    }

    /** Merges one reply into a [PendingConversation.TransactionNeedsInfo], asking for the next
     *  missing field (amount → item → party) or building the voucher once nothing is left. */
    private fun handleTransactionNeedsInfoReply(reply: String, q: String, amount: Double, pending: PendingConversation.TransactionNeedsInfo) {
        val needsItem = pending.txnType == "Sales" || pending.txnType == "Purchase"
        val updated = when (pending.awaitingField) {
            "amount" -> {
                if (amount <= 0) {
                    _messages.value = _messages.value + ChatMessage.Text(localize("I didn't catch the amount. What is the amount (in ₹)?"), false)
                    requestAutoListen()
                    return
                }
                pending.copy(amount = amount, awaitingField = if (needsItem && pending.item == null) "item" else "done")
            }
            "item" -> {
                val item = reply.trim().replaceFirstChar { it.uppercase() }
                if (item.isBlank()) {
                    _messages.value = _messages.value + ChatMessage.Text(localize("What is the item?"), false)
                    requestAutoListen()
                    return
                }
                pending.copy(item = item, awaitingField = "done")
            }
            "party" -> {
                val party = if (q.contains("cash") || q.contains("no") || q.contains("nahi") || q.contains("none") || q.contains("nothing")) {
                    "Cash-in-hand"
                } else {
                    reply.trim().split(Regex("\\s+")).joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
                }
                if (party.isBlank()) {
                    val label = if (pending.txnType == "Sales") "customer's" else "supplier's"
                    _messages.value = _messages.value + ChatMessage.Text(localize("What is the $label name?"), false)
                    requestAutoListen()
                    return
                }
                pending.copy(party = party, awaitingField = "done")
            }
            else -> pending
        }

        if (updated.awaitingField == "done") {
            pendingConversation = null
            val amt = updated.amount ?: 0.0
            val itemName = updated.item ?: "Goods"
            val partyName = updated.party ?: "Cash-in-hand"
            val qty = updated.qty ?: 1.0
            val draft = when (updated.txnType) {
                "Purchase" -> com.minhas.teller.utils.AiResponse.DraftVoucher(
                    type = "Purchase", date = updated.date ?: "today", voucherNumber = null,
                    entries = listOf(
                        com.minhas.teller.utils.DraftEntry("Purchase Account", true, amt),
                        com.minhas.teller.utils.DraftEntry(partyName, false, amt)
                    ),
                    inventoryItems = listOf(com.minhas.teller.utils.DraftInventoryItem(itemName, qty, amt, amt)),
                    narration = "Purchase of $itemName from $partyName",
                    partyName = partyName, amount = amt
                )
                "Sales" -> com.minhas.teller.utils.AiResponse.DraftVoucher(
                    type = "Sales", date = updated.date ?: "today", voucherNumber = null,
                    entries = listOf(
                        com.minhas.teller.utils.DraftEntry(partyName, true, amt),
                        com.minhas.teller.utils.DraftEntry("Sales Account", false, amt)
                    ),
                    inventoryItems = listOf(com.minhas.teller.utils.DraftInventoryItem(itemName, qty, amt, amt)),
                    narration = "Sale of $itemName to $partyName",
                    partyName = partyName, amount = amt
                )
                "Payment" -> com.minhas.teller.utils.AiResponse.DraftVoucher(
                    type = "Payment", date = updated.date ?: "today", voucherNumber = null,
                    entries = listOf(
                        com.minhas.teller.utils.DraftEntry(partyName, true, amt),
                        com.minhas.teller.utils.DraftEntry("Cash-in-hand", false, amt)
                    ),
                    inventoryItems = emptyList(),
                    narration = "Payment to $partyName ₹$amt",
                    partyName = partyName, amount = amt
                )
                else -> com.minhas.teller.utils.AiResponse.DraftVoucher(
                    type = "Receipt", date = updated.date ?: "today", voucherNumber = null,
                    entries = listOf(
                        com.minhas.teller.utils.DraftEntry("Cash-in-hand", true, amt),
                        com.minhas.teller.utils.DraftEntry(partyName, false, amt)
                    ),
                    inventoryItems = emptyList(),
                    narration = "Receipt from $partyName ₹$amt",
                    partyName = partyName, amount = amt
                )
            }
            _messages.value = _messages.value + ChatMessage.ReviewVoucher(draft)
        } else {
            pendingConversation = updated
            val question = when (updated.awaitingField) {
                "item" -> "What is the item?"
                "party" -> "What is the ${if (updated.txnType == "Sales") "customer's" else "supplier's"} name?"
                else -> "What is the amount (in ₹)?"
            }
            _messages.value = _messages.value + ChatMessage.Text(localize(question), false)
            requestAutoListen()
        }
    }

    private fun financeNextQuestion(fs: PendingConversation.FinanceSale): String = when (fs.awaitingField) {
        "financier" -> "Which finance company or bank is providing the loan?"
        "item" -> "What is the vehicle or item being sold? (e.g. Splendor, Activa, Car)"
        "customer" -> "What is the customer's name?"
        "loanAmount" -> "What is the loan amount from ${fs.financier}? (in ₹)"
        "downPayment" -> "What is the down payment amount? Say 'no' if there isn't any."
        else -> "Got all the info! Let me create the voucher."
    }

    private suspend fun buildFinanceVoucher(fs: PendingConversation.FinanceSale): com.minhas.teller.utils.AiResponse.DraftVoucher {
        val loan = fs.loanAmount ?: 0.0
        val dp = fs.downPayment ?: 0.0
        val total = loan + dp
        val entries = mutableListOf<com.minhas.teller.utils.DraftEntry>()
        // Use existing Sales Account ledger if one exists, else fall back to "Local Sales"
        val companyId = SessionManager.currentCompanyId.value
        val salesAccountName = if (companyId != null) {
            ledgerRepository.getAllLedgersSync(companyId)
                .firstOrNull { it.group.equals("Sales Accounts", ignoreCase = true) }
                ?.name ?: "Local Sales"
        } else "Local Sales"
        entries.add(com.minhas.teller.utils.DraftEntry(salesAccountName, false, total))
        if (loan > 0) entries.add(com.minhas.teller.utils.DraftEntry(fs.financier ?: "Finance Company", true, loan, groupSuggestion = "Finance Companies"))
        if (dp > 0) entries.add(com.minhas.teller.utils.DraftEntry("Cash-in-hand", true, dp))
        val itemName = fs.item ?: "Vehicle"
        val items = listOf(com.minhas.teller.utils.DraftInventoryItem(itemName, 1.0, total, total))
        return com.minhas.teller.utils.AiResponse.DraftVoucher(
            type = "Sales", date = fs.date ?: "today", voucherNumber = null,
            entries = entries, inventoryItems = items,
            narration = "${fs.financier} Loan: ₹$loan, Down Payment: ₹$dp",
            partyName = fs.customer, amount = total
        )
    }

    private fun extractSimpleParty(q: String): String? {
        val patterns = listOf(
            Regex("(?:to|from|ko|se)\\s+([a-z]{2,20})"),
            Regex("(?:paid|gave|give|received)\\s+([a-z]{2,20})")
        )
        val stopWords = setOf("cash", "bank", "money", "amount", "rs", "rupees", "the", "a", "an", "is", "was")
        for (p in patterns) {
            val m = p.find(q) ?: continue
            val name = m.groupValues[1].trim()
            if (name !in stopWords && name.length > 1) return name.replaceFirstChar { it.uppercase() }
        }
        return null
    }

    private fun detectSimplePartyFromMsg(q: String) = extractSimpleParty(q)

    private fun detectFinancierName(q: String): String? = when {
        q.contains("idfc") -> "IDFC Bank"
        q.contains("bajaj") -> "Bajaj Finance"
        q.contains("hdfc") -> "HDFC Bank"
        q.contains("sbi") -> "SBI Bank"
        q.contains("icici") -> "ICICI Bank"
        q.contains("axis") -> "Axis Bank"
        else -> {
            val m = Regex("([a-z]+(?:\\s+[a-z]+)?)\\s+(?:bank|finance|financed)", RegexOption.IGNORE_CASE).find(q)
            m?.groupValues?.get(1)?.trim()?.replaceFirstChar { it.uppercase() }
        }
    }

    private fun formatAmount(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    private fun detectVehicleName(q: String): String? {
        // Only return actual model names, not generic words like "vehicle", "bike", "car"
        val genericWords = setOf("vehicle", "bike", "car", "scooter", "motorcycle", "two wheeler", "four wheeler", "gaadi")
        val m = Regex("(?:splendor|activa|pulsar|shine|apache|access|jupiter|classic|bullet|discover|xtreme|glamour|passion|pleasure|maestro|dio|unicorn|cbr|r15|fz|gixxer|dominar|himalayan|xpulse|hero|honda|yamaha|suzuki|tvs|royal\\s+enfield)(?:\\s+[a-z0-9]+)*", RegexOption.IGNORE_CASE).find(q) ?: return null
        
        val matchedWords = m.value.trim().split(Regex("\\s+"))
        if (matchedWords.isEmpty()) return null
        
        val stopWords = setOf(
            "with", "loan", "amount", "and", "customer", "name", "for", "to", 
            "by", "on", "from", "of", "financier", "bank", "finance", "down", 
            "payment", "at", "in", "is", "a", "an", "the", "value"
        )
        
        val finalWords = mutableListOf<String>()
        for (word in matchedWords) {
            val lowerWord = word.lowercase()
            if (lowerWord in stopWords) break
            val num = lowerWord.toDoubleOrNull()
            if (num != null && num > 1000) break
            finalWords.add(word)
        }
        
        if (finalWords.isEmpty()) return null
        val result = finalWords.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return if (result.lowercase() in genericWords) null else result
    }

    private fun detectCustomerName(q: String): String? {
        // Multiple patterns to extract customer names
        val patterns = listOf(
            // "for Rajesh", "to Rajesh", "on the name of Rajesh"
            Regex("(?:for|to|on\\s+the\\s+name\\s+of|on\\s+name\\s+of|customer|customer\\s+name)\\s+(?:is|:|hai|tha|ko)?\\s*([a-z]{2,25})", RegexOption.IGNORE_CASE),
            // "financed a vehicle for Rajesh", "loan to Rajesh"
            Regex("(?:financed|loan|finance)\\s+(?:a\\s+)?(?:vehicle|bike|car|scooter)\\s+(?:for|to)\\s+([a-z]{2,25})", RegexOption.IGNORE_CASE),
            // "Rajesh ka loan", "Rajesh ke liye"
            Regex("([a-z]{2,25})\\s+(?:ka|ke|ki)\\s+(?:loan|finance|vehicle)", RegexOption.IGNORE_CASE),
            // Simple name extraction after prepositions
            Regex("(?:for|to)\\s+([a-z]{2,25})\\s+(?:with|and|at)", RegexOption.IGNORE_CASE)
        )
        
        val stopWords = setOf("cash", "bank", "finance", "idfc", "bajaj", "hdfc", "sbi", "icici", "axis", "the", "and", "for", "is", "loan", "amount", "down", "payment", "vehicle", "bike", "car", "scooter")
        
        for (pattern in patterns) {
            val m = pattern.find(q) ?: continue
            val name = m.groupValues[1].trim()
            if (name.length > 1 && name.lowercase() !in stopWords) {
                return name.replaceFirstChar { it.uppercase() }
            }
        }
        return null
    }
    // ── End multi-turn helpers ──────────────────────────────────────────────

    private suspend fun handleParsedTransactions(companyId: Int, data: com.minhas.teller.utils.GeminiTransactionsResponse) {
        val ledgers = ledgerRepository.getAllLedgersSync(companyId)
        val newMessages = mutableListOf<ChatMessage>()

        data.transactions.forEach { tx ->
            val entries = mutableListOf<com.minhas.teller.utils.DraftEntry>()
            
            tx.entities.forEach { entity ->
                // Fuzzy Match
                val match = ledgers.find { it.name.equals(entity.raw_text_mention, ignoreCase = true) } 
                            ?: ledgers.find { it.name.contains(entity.raw_text_mention, ignoreCase = true) }
                            ?: ledgers.find { it.name.equals(entity.suggested_ledger_name, ignoreCase = true) }

                val ledgerName = match?.name ?: entity.suggested_ledger_name
                val isDebit = entity.likely_side.equals("Debit", ignoreCase = true)
                
                // Group Suggestion Logic
                val groupSuggestion = if (match == null) {
                    when (entity.role) {
                        "source_fund", "financing_source" -> if (ledgerName.contains("Bank", true)) "Bank Accounts" else "Cash-in-hand"
                        "customer" -> "Sundry Debtors"
                        "vendor", "supplier" -> "Sundry Creditors"
                        "acquired_asset" -> "Fixed Assets"
                        "expense_type" -> "Indirect Expenses"
                        else -> {
                            if (ledgerName.contains("Sale", true)) "Sales Accounts"
                            else if (ledgerName.contains("Purchase", true)) "Purchase Accounts"
                            else "Sundry Debtors"
                        }
                    }
                } else null

                entries.add(com.minhas.teller.utils.DraftEntry(
                    ledgerName = ledgerName,
                    isDebit = isDebit,
                    amount = entity.amount,
                    groupSuggestion = groupSuggestion
                ))
            }

            val draft = com.minhas.teller.utils.AiResponse.DraftVoucher(
                type = tx.suggested_voucher_type,
                date = "today",
                voucherNumber = null,
                entries = entries,
                inventoryItems = tx.items, // Pass parsed inventory items
                narration = tx.narrative_summary ?: "Auto-generated transaction"
            )
            newMessages.add(ChatMessage.ReviewVoucher(draft))
        }

        _messages.value = _messages.value + newMessages
    }



    private fun processVoucherIntent(intent: com.minhas.teller.utils.VoucherIntent) {
        // 1. Check if we need more info (specifically the Name/Bill No)
        if (intent.isInfoMissing || intent.referenceTag.isNullOrEmpty()) {
            // Display the question to the user in the Chat UI
            _messages.value = _messages.value + ChatMessage.Text(intent.clarificationQuestion ?: "Please enter the Bill No or Name.", false)
            return // Stop here, do not create voucher yet
        }

        // 2. Map the extracted Name/Bill No to the "Vch #" field (NOT Ledger)
        // We create a DraftVoucher from the intent to pass to the UI/Review Card
        
        // Determine Ledger based on Payment Mode
        val paymentMode = intent.paymentMode ?: "Cash"
        val ledgerName = if (paymentMode.contains("Bank", true)) "Bank Account" else "Cash-in-hand"
        val amount = intent.amount ?: 0.0

        // Simple heuristic to detect direction from narration (populated by AI)
        val isReceipt = intent.narration?.contains("Received", true) == true || 
                        intent.narration?.contains("Receipt", true) == true ||
                        intent.narration?.contains("Income", true) == true

        val entries = if (isReceipt) {
             listOf(
                com.minhas.teller.utils.DraftEntry(ledgerName, true, amount), // Debit Cash
                com.minhas.teller.utils.DraftEntry("Suspense Account", false, amount) // Credit Suspense
            )
        } else {
             listOf(
                com.minhas.teller.utils.DraftEntry(ledgerName, false, amount), // Credit Cash
                com.minhas.teller.utils.DraftEntry("Suspense Account", true, amount) // Debit Suspense
            )
        }

        val draft = com.minhas.teller.utils.AiResponse.DraftVoucher(
            type = if (isReceipt) "Receipt" else "Payment", // Infer type too!
            date = "today", 
            voucherNumber = intent.referenceTag, // CRITICAL: Map Name/Bill to Vch #
            entries = entries, 
            inventoryItems = emptyList(), 
            narration = intent.narration
        )

        _messages.value = _messages.value + ChatMessage.ReviewVoucher(draft)
    }

    private suspend fun handleQueryBalance(companyId: Int, name: String?) {
        val ledgers = ledgerRepository.getAllLedgersSync(companyId)
        if (name == null) {
            val totalCash = ledgers.find { it.name.contains("Cash", true) }?.let {
                (ledgerRepository.getClosingBalance(it.id, companyId, Date()).first() ?: 0L).paiseToRupees()
            } ?: 0.0
            _messages.value = _messages.value + ChatMessage.Text(localize("Your current Cash-in-hand balance is: $$totalCash."), false)
            return
        }

        val match = ledgers.find { it.name.contains(name, true) }
        if (match != null) {
            val bal = (ledgerRepository.getClosingBalance(match.id, companyId, Date()).first() ?: 0L).paiseToRupees()
            val type = if (bal >= 0) "Debit" else "Credit"
            _messages.value = _messages.value + ChatMessage.Text(localize("Current Balance for '${match.name}' is: $${Math.abs(bal)} ($type)."), false)
        } else {
            _messages.value = _messages.value + ChatMessage.Text(localize("I couldn't find a ledger named '$name'."), false)
        }
    }

    private suspend fun handleQueryStock(companyId: Int, itemName: String?) {
        val items = stockItemDao.getAllStockItems(companyId)
        if (itemName == null) {
            _messages.value = _messages.value + ChatMessage.Text(localize("You have ${items.size} unique items in stock."), false)
            return
        }

        val match = items.find { it.name.contains(itemName, true) }
        if (match != null) {
            val vouchers = voucherRepository.getAllVouchersWithAllEntries(companyId)
            var totalQty = match.openingStock
            vouchers.forEach { v ->
                v.inventoryEntries.filter { it.stockItemId == match.id }.forEach { entry ->
                    if (v.voucher.type == "Sales") totalQty -= entry.quantity
                    else if (v.voucher.type == "Purchase") totalQty += entry.quantity
                }
            }
            _messages.value = _messages.value + ChatMessage.Text(localize("Current stock for '${match.name}' is: $totalQty units."), false)
        } else {
            _messages.value = _messages.value + ChatMessage.Text(localize("I couldn't find an item named '$itemName'."), false)
        }
    }

    private suspend fun handleQuerySales(companyId: Int, period: String?) {
        val vouchers = voucherRepository.getAllVouchersWithAllEntries(companyId)
        val today = Date()
        val filtered = when(period) {
            "today" -> vouchers.filter { isSameDay(it.voucher.date, today) }
            "yesterday" -> vouchers.filter { isSameDay(it.voucher.date, yesterday()) }
            "month" -> vouchers.filter { isSameMonth(it.voucher.date, today) }
            else -> vouchers
        }
        val sales = filtered.filter { it.voucher.type == "Sales" }
            .sumOf { v -> v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees() }
        _messages.value = _messages.value + ChatMessage.Text(localize("Total sales ($period): $$sales"), false)
    }

    private suspend fun handleQueryMetric(companyId: Int, metric: String, period: String?) {
        val (start, end) = computeDateRange(period)
        val vouchers = if (start != null && end != null) {
            voucherRepository.getAllVouchersWithAllEntriesBetweenDates(companyId, start, end)
        } else {
            voucherRepository.getAllVouchersWithAllEntries(companyId)
        }

        fun sumByType(type: String): Double =
            vouchers.filter { it.voucher.type.equals(type, ignoreCase = true) }
                .sumOf { v -> v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees() }

        val sales = sumByType("Sales")
        val purchase = sumByType("Purchase")
        val receipt = sumByType("Receipt")
        val payment = sumByType("Payment")

        val text = when (metric.lowercase()) {
            "sales" -> "Total sales (${period ?: "total"}): $${formatMoney(sales)}"
            "purchase" -> "Total purchase (${period ?: "total"}): $${formatMoney(purchase)}"
            "receipt" -> "Total receipts (${period ?: "total"}): $${formatMoney(receipt)}"
            "payment" -> "Total payments (${period ?: "total"}): $${formatMoney(payment)}"
            "vouchers_count" -> "Total vouchers (${period ?: "total"}): ${vouchers.size}"
            "vouchers_without_tax" -> {
                val noTax = vouchers.filter { it.voucher.type.equals("Sales", true) || it.voucher.type.equals("Purchase", true) }
                    .count { v -> v.inventoryEntries.isEmpty() || v.inventoryEntries.all { it.taxAmount <= 0.01 } }
                "Vouchers without tax (${period ?: "total"}): $noTax"
            }
            "profit" -> {
                val profit = sales - purchase
                "Estimated profit (${period ?: "total"}) = Sales - Purchase: $${formatMoney(profit)} (Sales $${formatMoney(sales)} - Purchase $${formatMoney(purchase)})"
            }
            "cash" -> {
                val balances = computeLedgerBalances(companyId)
                val cashBal = balances.cash
                "Cash-in-hand balance: $${formatMoney(cashBal)}"
            }
            "bank" -> {
                val balances = computeLedgerBalances(companyId)
                val bankBal = balances.bank
                "Total bank balance: $${formatMoney(bankBal)}"
            }
            "gst_payable", "gst" -> {
                val outputTax = GstComplianceRules.outputTax(vouchers)
                val inputTax = GstComplianceRules.inputTax(vouchers)
                val net = outputTax - inputTax
                "Net GST payable (${period ?: "total"}): $${formatMoney(net)} (Output tax $${formatMoney(outputTax)} − Input tax credit $${formatMoney(inputTax)})"
            }
            else -> "I can track sales, purchase, receipt, payment, profit, and GST payable. Try: \"profit yesterday\" or \"payable GST\"."
        }

        _messages.value = _messages.value + ChatMessage.Text(localize("Helafin: $text"), false)
    }

    private suspend fun handleQueryItemSales(companyId: Int, period: String?, groupBy: String?) {
        val (start, end) = computeDateRange(period)
        val vouchers = if (start != null && end != null) {
            voucherRepository.getAllVouchersWithAllEntriesBetweenDates(companyId, start, end)
        } else {
            voucherRepository.getAllVouchersWithAllEntries(companyId)
        }

        val soldByItemId = mutableMapOf<Int, Double>()
        vouchers.filter { it.voucher.type == "Sales" }.forEach { v ->
            v.inventoryEntries.forEach { inv ->
                soldByItemId[inv.stockItemId] = (soldByItemId[inv.stockItemId] ?: 0.0) + inv.quantity
            }
        }

        if (soldByItemId.isEmpty()) {
            _messages.value = _messages.value + ChatMessage.Text(localize("Helafin: I couldn't find any item sales for ${period ?: "this period"}."), false)
            return
        }

        val stockItems = stockItemDao.getAllStockItems(companyId).associateBy { it.id }
        val sorted = soldByItemId.entries.sortedByDescending { it.value }

        val lines = sorted.take(15).joinToString("\n") { (itemId, qty) ->
            val name = stockItems[itemId]?.name ?: "Item #$itemId"
            "- $name: ${formatQty(qty)}"
        }
        val totalQty = soldByItemId.values.sum()
        val title = when (groupBy?.lowercase()) {
            "model" -> "Vehicles sold (${period ?: "total"}) by model"
            else -> "Items sold (${period ?: "total"})"
        }

        val topItemName = sorted.firstOrNull()?.let { stockItems[it.key]?.name }
        val intro = if (topItemName != null) "The top-selling product is **$topItemName**.\n\n" else ""

        _messages.value = _messages.value + ChatMessage.Text(localize("Helafin: ${intro}$title\nTotal: ${formatQty(totalQty)}\n$lines"), false)
    }

    private fun computeDateRange(period: String?): Pair<Date?, Date?> {
        val cal = Calendar.getInstance()
        val end = Date()

        return when (period) {
            "today" -> {
                val start = startOfDay(Date())
                Pair(start, endOfDay(Date()))
            }
            "yesterday" -> {
                val y = yesterday()
                Pair(startOfDay(y), endOfDay(y))
            }
            "month" -> {
                cal.time = Date()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                Pair(startOfDay(cal.time), endOfDay(Date()))
            }
            "last_month" -> {
                cal.time = Date()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, -1)
                val start = startOfDay(cal.time)
                cal.add(Calendar.MONTH, 1)
                cal.add(Calendar.DAY_OF_MONTH, -1)
                val endLast = endOfDay(cal.time)
                Pair(start, endLast)
            }
            "total", null -> Pair(null, null)
            else -> Pair(null, null)
        }
    }

    private fun yesterday(): Date {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return cal.time
    }

    private fun startOfDay(d: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = d
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun endOfDay(d: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = d
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.time
    }

    private fun formatMoney(v: Double): String = String.format(Locale.US, "%.2f", v)
    private fun formatQty(v: Double): String = if (kotlin.math.abs(v - v.toInt()) < 0.00001) v.toInt().toString() else String.format(Locale.US, "%.2f", v)

    private suspend fun handleQuerySummary(companyId: Int) {
        val sales = voucherRepository.getTotalSales(companyId)
        val purchase = voucherRepository.getTotalPurchase(companyId)
        val receipts = voucherRepository.getTotalReceipt(companyId)
        val payments = voucherRepository.getTotalPayment(companyId)
        val profit = sales - purchase
        val msg = "Summary Report:\nTotal Sales: $$sales\nTotal Purchase: $$purchase\nEstimated Margin: $$profit\nCash Flow (In-Out): $${receipts - payments}"
        _messages.value = _messages.value + ChatMessage.Text(localize(msg), false)
    }

    private suspend fun handleQueryCollectables(companyId: Int) {
        val ledgers = ledgerRepository.getAllLedgersSync(companyId)
        var total = 0.0
        ledgers.filter { it.group.contains("Debtors", true) }.forEach {
            total += (ledgerRepository.getClosingBalance(it.id, companyId, Date()).first() ?: 0L).paiseToRupees()
        }
        _messages.value = _messages.value + ChatMessage.Text(localize("Total money to collect from customers: $$total"), false)
    }

    private suspend fun handleQueryPayables(companyId: Int) {
        val ledgers = ledgerRepository.getAllLedgersSync(companyId)
        var total = 0.0
        ledgers.filter { it.group.contains("Creditors", true) }.forEach {
            total += (ledgerRepository.getClosingBalance(it.id, companyId, Date()).first() ?: 0L).paiseToRupees()
        }
        _messages.value = _messages.value + ChatMessage.Text(localize("Total money payable to suppliers: $${Math.abs(total)}"), false)
    }

    private suspend fun handleQueryCollectablesList(companyId: Int) {
        val balances = computeLedgerBalances(companyId)
        val lines = balances.receivablesByLedger.entries
            .sortedByDescending { it.value }
            .take(15)
            .joinToString("\n") { (name, value) -> "- $name: $${formatMoney(value)}" }
        val msg = if (lines.isBlank()) "Helafin: No receivables found." else "Helafin: Top receivables\n$lines"
        _messages.value = _messages.value + ChatMessage.Text(localize(msg), false)
    }

    private suspend fun handleQueryPayablesList(companyId: Int) {
        val balances = computeLedgerBalances(companyId)
        val lines = balances.payablesByLedger.entries
            .sortedByDescending { it.value }
            .take(15)
            .joinToString("\n") { (name, value) -> "- $name: $${formatMoney(value)}" }
        val msg = if (lines.isBlank()) "Helafin: No payables found." else "Helafin: Top payables\n$lines"
        _messages.value = _messages.value + ChatMessage.Text(localize(msg), false)
    }

    private suspend fun handleQueryOutstanding(companyId: Int, ledgerName: String?, period: String?) {
        val ledgers = ledgerRepository.getAllLedgersSync(companyId)
        val target = ledgerName?.let { name -> ledgers.find { it.name.contains(name, true) } }
        if (target == null) {
            _messages.value = _messages.value + ChatMessage.Text(localize("Helafin: Which party/ledger should I check outstanding for?"), false)
            return
        }

        val outstanding = voucherRepository.getOutstandingVouchers(target.id)
        val (start, end) = computeDateRange(period)
        val filtered = if (start != null && end != null) {
            outstanding.filter { it.date.after(start) && it.date.before(end) || isSameDay(it.date, start) || isSameDay(it.date, end) }
        } else outstanding

        if (filtered.isEmpty()) {
            _messages.value = _messages.value + ChatMessage.Text(localize("Helafin: No outstanding bills found for ${target.name}."), false)
            return
        }

        val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
        val lines = filtered.sortedBy { it.date }.takeLast(20).joinToString("\n") {
            "- ${it.type} #${it.voucherNumber} (${sdf.format(it.date)}): pending $${formatMoney(it.pendingAmount)}"
        }
        val totalPending = filtered.sumOf { it.pendingAmount }
        _messages.value = _messages.value + ChatMessage.Text(localize("Helafin: Outstanding for ${target.name} (Total $${formatMoney(totalPending)})\n$lines"), false)
    }

    private suspend fun handleQueryTopParties(companyId: Int, kind: String, period: String?, limit: Int) {
        val (start, end) = computeDateRange(period)
        val vouchers = if (start != null && end != null) {
            voucherRepository.getAllVouchersWithAllEntriesBetweenDates(companyId, start, end)
        } else {
            voucherRepository.getAllVouchersWithAllEntries(companyId)
        }

        val ledgers = ledgerRepository.getAllLedgersSync(companyId).associateBy { it.id }
        val totals = mutableMapOf<Int, Double>()

        fun isSalesAccount(name: String) = name.contains("sales", true)
        fun isPurchaseAccount(name: String) = name.contains("purchase", true)
        fun isCash(name: String) = name.contains("cash", true)

        if (kind.lowercase() == "customers") {
            vouchers.filter { it.voucher.type == "Sales" }.forEach { v ->
                val debitCandidates = v.voucherEntries.filter { it.isDebit }
                    .mapNotNull { e -> ledgers[e.ledgerId]?.let { it.id to e.amount } }
                    .filter { (id, _) ->
                        val n = ledgers[id]?.name ?: ""
                        !isSalesAccount(n)
                    }
                val partyId = debitCandidates.maxByOrNull { it.second }?.first
                if (partyId != null) {
                    val amount = v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
                    totals[partyId] = (totals[partyId] ?: 0.0) + amount
                }
            }
            val lines = totals.entries.sortedByDescending { it.value }.take(limit).joinToString("\n") { (id, amt) ->
                "- ${ledgers[id]?.name ?: "Ledger #$id"}: $${formatMoney(amt)}"
            }
            _messages.value = _messages.value + ChatMessage.Text(localize("Helafin: Top $limit customers by sales (${period ?: "total"})\n$lines"), false)
            return
        }

        if (kind.lowercase() == "suppliers") {
            vouchers.filter { it.voucher.type == "Purchase" }.forEach { v ->
                val creditCandidates = v.voucherEntries.filter { !it.isDebit }
                    .mapNotNull { e -> ledgers[e.ledgerId]?.let { it.id to e.amount } }
                    .filter { (id, _) ->
                        val n = ledgers[id]?.name ?: ""
                        !isPurchaseAccount(n) && !isCash(n)
                    }
                val partyId = creditCandidates.maxByOrNull { it.second }?.first
                if (partyId != null) {
                    val amount = v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
                    totals[partyId] = (totals[partyId] ?: 0.0) + amount
                }
            }
            val lines = totals.entries.sortedByDescending { it.value }.take(limit).joinToString("\n") { (id, amt) ->
                "- ${ledgers[id]?.name ?: "Ledger #$id"}: $${formatMoney(amt)}"
            }
            _messages.value = _messages.value + ChatMessage.Text(localize("Helafin: Top $limit suppliers by purchase (${period ?: "total"})\n$lines"), false)
            return
        }

        _messages.value = _messages.value + ChatMessage.Text(localize("Helafin: Try \"top 5 customers by sales\" or \"top 5 suppliers by purchase\"."), false)
    }

    private suspend fun handleQueryRecentVouchers(companyId: Int, voucherType: String?, period: String?, limit: Int) {
        val (start, end) = computeDateRange(period)
        val vouchers = if (start != null && end != null) {
            voucherRepository.getAllVouchersWithAllEntriesBetweenDates(companyId, start, end)
        } else {
            voucherRepository.getAllVouchersWithAllEntries(companyId)
        }

        val filtered = if (voucherType != null) vouchers.filter { it.voucher.type.equals(voucherType, true) } else vouchers
        val sorted = filtered.sortedByDescending { it.voucher.date }.take(limit)
        if (sorted.isEmpty()) {
            _messages.value = _messages.value + ChatMessage.Text(localize("Helafin: No vouchers found."), false)
            return
        }

        val sdf = SimpleDateFormat("dd-MMM", Locale.US)
        val lines = sorted.joinToString("\n") { v ->
            val amt = v.voucherEntries.filter { it.isDebit }.sumOf { it.amount }.paiseToRupees()
            "- ${v.voucher.type} #${v.voucher.voucherNumber} (${sdf.format(v.voucher.date)}): $${formatMoney(amt)}"
        }
        _messages.value = _messages.value + ChatMessage.Text(localize("Helafin: Last $limit ${voucherType ?: ""} vouchers (${period ?: "total"})\n$lines"), false)
    }

    private data class LedgerBalancesResult(
        val cash: Double,
        val bank: Double,
        val receivablesByLedger: Map<String, Double>,
        val payablesByLedger: Map<String, Double>
    )

    private suspend fun computeLedgerBalances(companyId: Int): LedgerBalancesResult {
        val vouchers = voucherRepository.getAllVouchersWithAllEntries(companyId)
        val ledgers = ledgerRepository.getAllLedgersSync(companyId)
        val ledgerBalances = mutableMapOf<Int, Long>()
        ledgers.forEach { ledgerBalances[it.id] = it.openingBalance }
        vouchers.forEach { v ->
            v.voucherEntries.forEach { e ->
                val current = ledgerBalances.getOrDefault(e.ledgerId, 0L)
                ledgerBalances[e.ledgerId] = if (e.isDebit) current + e.amount else current - e.amount
            }
        }

        val debtorGroups = listOf("Sundry Debtors", "Finance Companies", "Money to Collect")
        val creditorGroups = listOf("Sundry Creditors", "Money to Pay")
        val cashGroups = listOf("Cash-in-hand")
        val bankGroups = listOf("Bank Accounts", "Bank OD A/c")

        var cash = 0.0
        var bank = 0.0
        val recv = mutableMapOf<String, Double>()
        val pay = mutableMapOf<String, Double>()

        ledgers.forEach { l ->
            val bal = ledgerBalances.getOrDefault(l.id, 0L).paiseToRupees()
            when {
                cashGroups.any { it.equals(l.group, true) } -> cash += bal
                bankGroups.any { it.equals(l.group, true) } -> bank += bal
                debtorGroups.any { it.equals(l.group, true) } -> if (bal > 0.01) recv[l.name] = bal
                creditorGroups.any { it.equals(l.group, true) } -> if (bal < -0.01) pay[l.name] = kotlin.math.abs(bal)
            }
        }

        return LedgerBalancesResult(cash = cash, bank = bank, receivablesByLedger = recv, payablesByLedger = pay)
    }

    private fun isSameDay(d1: Date, d2: Date): Boolean = SimpleDateFormat("yyyyMMdd", Locale.US).format(d1) == SimpleDateFormat("yyyyMMdd", Locale.US).format(d2)
    private fun isSameMonth(d1: Date, d2: Date): Boolean = SimpleDateFormat("yyyyMM", Locale.US).format(d1) == SimpleDateFormat("yyyyMM", Locale.US).format(d2)

    private fun handleMultiAction(response: AiResponse.MultiAction) {
        val newMessages = mutableListOf<ChatMessage>()
        response.actions.forEach { action ->
            when (action) {
                is AiResponse.Message -> newMessages.add(ChatMessage.Text(action.text, false))
                is AiResponse.DraftVoucher -> newMessages.add(ChatMessage.ReviewVoucher(action))
                is AiResponse.DraftLedger -> newMessages.add(ChatMessage.ReviewLedger(action))
                else -> {}
            }
        }
        _messages.value = _messages.value + newMessages
    }

    private fun formatHistory(history: List<ChatMessage>): String {
        return history.takeLast(10).joinToString("\n") { msg ->
            val prefix = if (msg.isUser) "User" else "AI"
            when (msg) {
                is ChatMessage.Text -> "$prefix: ${msg.text}"
                is ChatMessage.ReviewVoucher -> "$prefix: [Draft]"
                else -> ""
            }
        }
    }

    fun confirmVoucher(draft: AiResponse.DraftVoucher) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch

            // Run the whole ledger/stock/HSN resolution + DB insert off the main thread.
            // This is the step that made voucher confirmation feel slow — it does many
            // sequential DB round-trips and HSN/GST scoring, none of which needs Main.
            val createdItemCount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                buildAndInsertVoucher(companyId, draft)
            }

            if (createdItemCount != null) {
                _messages.value = _messages.value + ChatMessage.Success("Voucher Created Successfully with $createdItemCount items!")
            }
        }
    }

    /**
     * Resolves ledgers, units, stock items and HSN/GST, then inserts the voucher.
     * Returns the number of inventory items attached, or null if the voucher could
     * not be built (no ledger entries). Runs on [Dispatchers.Default].
     */
    private suspend fun buildAndInsertVoucher(companyId: Int, draft: AiResponse.DraftVoucher): Int? {
        val voucherDate = com.minhas.teller.utils.FinanceHelper.parseVoucherDate(draft.date)
        val vNum = if (!draft.voucherNumber.isNullOrEmpty()) draft.voucherNumber else "Auto-${System.currentTimeMillis() % 1000}"

        val voucher = Voucher(
            voucherNumber = vNum,
            date = voucherDate,
            type = draft.type ?: "Journal",
            narration = draft.narration ?: "",
            companyId = companyId,
            refDisplayName = draft.partyName,
            customField = draft.partyName
        )
        val existingLedgers = ledgerRepository.getAllLedgersSync(companyId).toMutableList()
        val voucherEntries = mutableListOf<VoucherEntry>()
        val inventoryEntries = mutableListOf<InventoryEntry>()
        val autoAllocations = mutableListOf<com.minhas.teller.data.entities.BillAllocation>()

        // 1. Process Ledger Entries
        draft.entries.forEach { entryDraft ->
            var ledger = existingLedgers.find { it.name.equals(entryDraft.ledgerName, ignoreCase = true) }
            if (ledger == null) {
                val group = entryDraft.groupSuggestion ?: when {
                    entryDraft.ledgerName.contains("Cash", true) -> "Cash-in-hand"
                    entryDraft.ledgerName.contains("Bank", true) -> "Bank Accounts"
                    entryDraft.ledgerName.contains("Sales", true) -> "Sales Accounts"
                    entryDraft.ledgerName.contains("Purchase", true) -> "Purchase Accounts"
                    entryDraft.ledgerName.contains("Expense", true) -> "Direct Expenses"
                    entryDraft.isDebit -> "Sundry Debtors"
                    else -> "Sundry Creditors"
                }
                val shouldMaintainBillWise = group.contains("Debtor", true) || group.contains("Creditor", true) || group.equals("Finance Companies", ignoreCase = true)

                val newLedger = Ledger(
                    name = entryDraft.ledgerName,
                    group = group,
                    openingBalance = 0L,
                    maintainBillWise = shouldMaintainBillWise,
                    companyId = companyId
                )
                val newLedgerId = ledgerRepository.insert(newLedger)
                ledger = newLedger.copy(id = newLedgerId.toInt())
                existingLedgers.add(ledger)
            }

            if (ledger != null) {
                // If this is a Finance Companies ledger that was created before maintainBillWise fix, update it now
                if (ledger.group.equals("Finance Companies", ignoreCase = true) && !ledger.maintainBillWise) {
                    ledgerRepository.update(ledger.copy(maintainBillWise = true))
                    ledger = ledger.copy(maintainBillWise = true)
                }
                voucherEntries.add(VoucherEntry(ledgerId = ledger.id, isDebit = entryDraft.isDebit, amount = entryDraft.amount.rupeesToPaise(), voucherId = 0))

                // Auto-generate Bill Allocation if Bill-Wise is enabled
                if (ledger.maintainBillWise) {
                    autoAllocations.add(com.minhas.teller.data.entities.BillAllocation(
                        voucherEntryId = ledger.id, // Temp mapping via ledgerId
                        refType = "New Ref",
                        refName = vNum,
                        amount = entryDraft.amount.rupeesToPaise()
                    ))
                }
            }
        }

        // 2. Process Inventory Items
        draft.inventoryItems.forEach { itemDraft ->
            // Canonicalise the spoken item name: singularise/translate ("mangoes" → "Mango",
            // "wash baisen" → "Wash Basin") and strip a stray leading quantity/unit that ASR or the
            // cloud LLM merged into the name ("20 liter milk" → name "Milk", qty 20, unit "Liter").
            val (canonicalName, qty, deducedUnit) = com.minhas.teller.utils.LanguageTranslationUtils.canonicalItemDetails(
                itemDraft.name,
                itemDraft.quantity,
                itemDraft.unit
            )
            val qtyChanged = qty != itemDraft.quantity
            val rate = if (qtyChanged && qty > 0 && itemDraft.amount > 0) itemDraft.amount / qty else itemDraft.rate

            // Ensure Stock Item exists — exact name first, then a singular/case-insensitive
            // match so "apples"/"mangoes" reuse the existing "Apple"/"Mango" instead of duplicating.
            val allUnits = unitDao.getAllUnits(companyId)
            var stockItem = stockItemDao.getStockItemByName(companyId, canonicalName)
            if (stockItem == null) {
                val key = com.minhas.teller.utils.LanguageTranslationUtils.itemKey(canonicalName)
                val existing = stockItemDao.getAllStockItems(companyId)
                stockItem = existing
                    .firstOrNull { com.minhas.teller.utils.LanguageTranslationUtils.itemKey(it.name) == key }
                if (stockItem == null &&
                    !com.minhas.teller.utils.LanguageTranslationUtils.isKnownWord(canonicalName)
                ) {
                    // The name is one the item dictionary has never heard of, which is what a
                    // misheard item sounds like ("paanch rum" → "panchram" → "Ram"). Reuse the
                    // stock item it most likely refers to instead of creating a one-off duplicate.
                    // Known words are left alone, so a genuinely new "Pen" can't become "Pan".
                    val match = com.minhas.teller.utils.LanguageTranslationUtils.closestKnownItem(
                        canonicalName,
                        existing.map { it.name }
                    )
                    if (match != null) stockItem = existing.firstOrNull { it.name == match }
                }
            }

            // Resolve the unit: an explicitly-spoken unit wins; otherwise reuse the item's saved
            // unit; otherwise fall back to the category default for brand-new items.
            val unitName = com.minhas.teller.utils.ProductCategory.resolveUnit(
                deducedUnit
                    ?: stockItem?.unitId?.let { savedId -> allUnits.firstOrNull { it.id == savedId }?.name }
                    ?: com.minhas.teller.utils.ProductCategory.defaultUnitForItem(canonicalName)
            )
            var unit = allUnits.firstOrNull {
                it.name.equals(unitName, ignoreCase = true) ||
                com.minhas.teller.utils.ProductCategory.resolveUnit(it.name).equals(unitName, ignoreCase = true)
            }
            if (unit == null) {
                val newUnit = Unit(name = unitName, companyId = companyId)
                val newUnitId = unitDao.insert(newUnit)
                unit = newUnit.copy(id = newUnitId.toInt())
            }
            if (stockItem == null) {
                val countryName = com.minhas.teller.utils.DashboardPrefs.getTaxCountry(appContext)
                val isIndia = countryName.equals("India", true)

                var detectedHsnCode: String? = itemDraft.hsnCode?.takeIf { it.isNotBlank() }
                var detectedGstRate = itemDraft.gstRate ?: 0.0

                // The app's own HSN directory is checked first. A code guessed by the cloud
                // assistant (e.g. for a Hindi word it did not understand) is only a fallback.
                val smartTaxService = com.minhas.teller.utils.SmartTaxService.getInstance(appContext)
                val taxInfo = smartTaxService.getTaxInfoForItem(canonicalName)
                if (taxInfo != null) {
                    detectedHsnCode = taxInfo.hsnCode
                    detectedGstRate = taxInfo.gstRate
                }

                val cgst = if (isIndia) detectedGstRate / 2 else 0.0
                val sgst = if (isIndia) detectedGstRate / 2 else 0.0
                val igst = if (isIndia) 0.0 else detectedGstRate

                val newStockItem = StockItem(
                    name = canonicalName,
                    unitId = unit.id,
                    openingStock = 0.0,
                    rate = rate,
                    companyId = companyId,
                    hsnCode = detectedHsnCode,
                    gstRate = detectedGstRate,
                    cgstRate = cgst,
                    sgstRate = sgst,
                    igstRate = igst,
                    hsnAutoDetected = true
                )
                val newStockId = stockItemDao.insert(newStockItem)
                stockItem = newStockItem.copy(id = newStockId.toInt())
            } else {
                val currentUnit = allUnits.firstOrNull { it.id == stockItem.unitId }
                val isGeneric = currentUnit == null || currentUnit.name.lowercase() in listOf("units", "unit", "nos", "")
                val currentUnitName = currentUnit?.name
                // Persist an explicitly-chosen unit as the item's new default (so the next sale
                // defaults to it), and still upgrade a previously-generic fallback unit to a concrete one.
                val shouldUpdate = if (deducedUnit != null) {
                    currentUnitName == null || !currentUnitName.equals(unitName, ignoreCase = true)
                } else {
                    isGeneric && unitName !in listOf("Units", "Unit", "Nos", "Pcs")
                }
                if (shouldUpdate) {
                    val updated = stockItem.copy(unitId = unit.id)
                    stockItemDao.update(updated)
                    stockItem = updated
                }
            }

            if (stockItem != null) {
                // Back-fill HSN/tax on the item master if it's still blank, so the
                // auto-assigned code shows in Stock Item Details (not just on the line).
                if (stockItem.hsnCode.isNullOrBlank()) {
                    val countryName = com.minhas.teller.utils.DashboardPrefs.getTaxCountry(appContext)
                    val isIndia = countryName.equals("India", true)
                    var detectedHsnCode: String? = itemDraft.hsnCode?.takeIf { it.isNotBlank() }
                    var detectedGstRate = itemDraft.gstRate ?: stockItem.gstRate
                    run {
                        val taxInfo = com.minhas.teller.utils.SmartTaxService.getInstance(appContext).getTaxInfoForItem(canonicalName)
                        if (taxInfo != null) {
                            detectedHsnCode = taxInfo.hsnCode
                            detectedGstRate = taxInfo.gstRate
                        }
                    }
                    if (detectedHsnCode != null || detectedGstRate > 0.0) {
                        val cgst = if (isIndia) detectedGstRate / 2 else 0.0
                        val sgst = if (isIndia) detectedGstRate / 2 else 0.0
                        val igst = if (isIndia) 0.0 else detectedGstRate
                        val updated = stockItem.copy(
                            hsnCode = detectedHsnCode,
                            gstRate = detectedGstRate,
                            cgstRate = cgst,
                            sgstRate = sgst,
                            igstRate = igst,
                            hsnAutoDetected = true
                        )
                        stockItemDao.update(updated)
                        stockItem = updated
                    }
                }

                val itemGstRate = stockItem.gstRate
                val itemCgstRate = stockItem.cgstRate
                val itemSgstRate = stockItem.sgstRate
                val itemIgstRate = stockItem.igstRate

                // Amounts spoken by the user ("sold 500 apples for 50000") are
                // TAX-INCLUSIVE, matching every other entry path in the app
                // (AddEditVoucherScreen, VoucherEditDialog, PurchaseVoucherCreationScreen):
                // the entered figure is the total, and the taxable base is
                // back-computed from it. Adding tax on top here made the voucher
                // view show 52,500 for a 50,000 sale while the ledger entries,
                // outstanding and pending-payment screens all still said 50,000.
                val inclusiveAmount = GstComplianceRules.round2(qty * rate)
                val taxableAmt = if (itemGstRate > 0.0) {
                    GstComplianceRules.round2(inclusiveAmount / (1.0 + itemGstRate / 100.0))
                } else inclusiveAmount
                val taxAmt = GstComplianceRules.round2(inclusiveAmount - taxableAmt)

                inventoryEntries.add(InventoryEntry(
                    voucherId = 0,
                    stockItemId = stockItem.id,
                    quantity = qty,
                    rate = rate,
                    amount = inclusiveAmount.rupeesToPaise(),
                    hsnCode = stockItem.hsnCode,
                    gstRate = itemGstRate,
                    cgstRate = itemCgstRate,
                    sgstRate = itemSgstRate,
                    igstRate = itemIgstRate,
                    taxableAmount = taxableAmt.rupeesToPaise(),
                    taxAmount = taxAmt.rupeesToPaise(),
                    totalAmount = inclusiveAmount.rupeesToPaise()
                ))
            }
        }

        // 3. Split GST on Sales: post net revenue + separate CGST/SGST credits (matching the
        //    manual voucher screen), so the Sales ledger carries revenue only, not the tax.
        //    The customer debit entry stays at the gross (tax-inclusive) total.
        if (draft.type.equals("Sales", true)) {
            val totalTax = inventoryEntries.sumOf { it.taxAmount }
            val totalTaxable = inventoryEntries.sumOf { it.taxableAmount }
            val cgstLedger = existingLedgers.find { it.name.equals("CGST", true) }
            val sgstLedger = existingLedgers.find { it.name.equals("SGST", true) }
            if (totalTax > 0.0 && totalTaxable > 0.0 && (cgstLedger != null || sgstLedger != null)) {
                val salesCreditIndex = voucherEntries.indexOfFirst { !it.isDebit }
                if (salesCreditIndex >= 0) {
                    voucherEntries[salesCreditIndex] = voucherEntries[salesCreditIndex].copy(amount = totalTaxable)
                    val (cgstAmt, sgstAmt) = GstComplianceRules.splitTax(totalTax.paiseToRupees())
                    if (cgstLedger != null) voucherEntries.add(VoucherEntry(voucherId = 0, ledgerId = cgstLedger.id, amount = cgstAmt.rupeesToPaise(), isDebit = false))
                    if (sgstLedger != null) voucherEntries.add(VoucherEntry(voucherId = 0, ledgerId = sgstLedger.id, amount = sgstAmt.rupeesToPaise(), isDebit = false))
                }
            }
        }

        if (voucherEntries.isEmpty()) return null
        voucherRepository.insertVoucherWithAllEntries(voucher, voucherEntries, inventoryEntries, autoAllocations)
        return inventoryEntries.size
    }

    fun confirmLedger(draft: AiResponse.DraftLedger) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            val err = insertLedgerDraft(companyId, draft)
            if (err == null) {
                _messages.value = _messages.value + ChatMessage.Success("Ledger '${draft.name}' Created!")
            } else {
                _messages.value = _messages.value + ChatMessage.Text("Error: $err", false)
            }
        }
    }

    /**
     * Confirms one or more pending review cards. Supports the single-card fast path as
     * well as batch confirmation when the user replies "yes"/"confirm" to a group of
     * drafts (e.g. multiple parties created at once).
     */
    private fun confirmReviews(reviews: List<ChatMessage>) {
        val vouchers = reviews.filterIsInstance<ChatMessage.ReviewVoucher>().map { it.draft }
        val ledgers = reviews.filterIsInstance<ChatMessage.ReviewLedger>().map { it.draft }
        when {
            vouchers.size == 1 && ledgers.isEmpty() -> confirmVoucher(vouchers.first())
            ledgers.size == 1 && vouchers.isEmpty() -> confirmLedger(ledgers.first())
            else -> confirmBatch(vouchers, ledgers)
        }
    }

    /** Confirms multiple ledger/voucher drafts at once and reports a single summary. */
    private fun confirmBatch(vouchers: List<AiResponse.DraftVoucher>, ledgers: List<AiResponse.DraftLedger>) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value
            if (companyId == null) {
                _messages.value = _messages.value + ChatMessage.Text("Select a company first.", false)
                return@launch
            }
            var created = 0
            val errors = mutableListOf<String>()
            ledgers.forEach { d ->
                insertLedgerDraft(companyId, d)?.let { errors.add("${d.name}: $it") } ?: run { created++ }
            }
            vouchers.forEach { d ->
                val items = buildAndInsertVoucher(companyId, d)
                if (items == null) errors.add("${d.type ?: "Voucher"} could not be built") else created++
            }
            if (errors.isEmpty()) {
                _messages.value = _messages.value + ChatMessage.Success("Created $created item(s) successfully!")
            } else {
                _messages.value = _messages.value + ChatMessage.Text("Created $created, but ${errors.size} failed: ${errors.joinToString("; ")}", false)
            }
        }
    }

    /**
     * Inserts a single ledger from a draft. Returns an error message on failure, or null on success.
     * Auto-enables Bill Wise for Debtors/Creditors/Payables/Receivables/Finance Companies.
     */
    private suspend fun insertLedgerDraft(companyId: Int, draft: AiResponse.DraftLedger): String? {
        val technicalGroup = com.minhas.teller.utils.TerminologyManager.getTechnicalTerm(draft.group)
        val shouldMaintainBillWise = technicalGroup.contains("Debtors", true) ||
            technicalGroup.contains("Creditors", true) ||
            technicalGroup.contains("Payable", true) ||
            technicalGroup.contains("Receivable", true) ||
            technicalGroup.equals("Finance Companies", ignoreCase = true)
        return try {
            ledgerRepository.insert(Ledger(
                name = draft.name,
                group = technicalGroup,
                openingBalance = (draft.openingBalance ?: 0.0).rupeesToPaise(),
                maintainBillWise = shouldMaintainBillWise,
                companyId = companyId
            ))
            null
        } catch (e: Exception) {
            e.message ?: "Unknown error"
        }
    }

    private suspend fun getFinancialContext(): String {
        val companyId = SessionManager.currentCompanyId.value ?: return ""
        return "Sales: ${voucherRepository.getTotalSales(companyId)}"
    }
}

class ChatViewModelFactory(
    private val appContext: Context,
    private val voucherRepository: VoucherRepository,
    private val ledgerRepository: LedgerRepository,
    private val stockItemDao: StockItemDao,
    private val unitDao: UnitDao,
    private val chatMessageDao: ChatMessageDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(appContext, voucherRepository, ledgerRepository, stockItemDao, unitDao, chatMessageDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
