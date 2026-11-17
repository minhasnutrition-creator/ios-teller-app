package com.minhas.teller.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.minhas.teller.SessionManager
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.*
import com.minhas.teller.data.entities.Unit
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.VoucherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed class VoiceBillingState {
    object Idle : VoiceBillingState()
    object Listening : VoiceBillingState()
    data class Processing(val spokenText: String) : VoiceBillingState()
    data class Parsed(val draft: AiResponse.DraftVoucher, val spokenText: String) : VoiceBillingState()
    data class Success(val voucherNumber: String, val message: String) : VoiceBillingState()
    data class Error(val message: String) : VoiceBillingState()
}

class VoiceBillingManager(private val context: Context) {
    private val tag = "VoiceBillingManager"
    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<VoiceBillingState>(VoiceBillingState.Idle)
    val state: StateFlow<VoiceBillingState> = _state.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val db = AppDatabase.getDatabase(context)
    private val ledgerRepo = LedgerRepository(db.ledgerDao())
    private val voucherRepo = VoucherRepository(
        db.voucherDao(),
        db.voucherEntryDao(),
        db.inventoryEntryDao(),
        db.billAllocationDao(),
        db.auditLogDao(),
        db.ledgerDao(),
        db
    )

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceBillingState.Error("Speech recognition not available on this device")
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _state.value = VoiceBillingState.Listening
                        _partialText.value = ""
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _state.value = VoiceBillingState.Processing(_partialText.value)
                    }

                    override fun onError(error: Int) {
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly."
                            SpeechRecognizer.ERROR_NETWORK -> "Network issue for speech recognition."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_CLIENT -> "Client error. Please try again."
                            else -> "Could not capture voice (Code $error)"
                        }
                        _state.value = VoiceBillingState.Error(errorMsg)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        // Rank the recogniser's alternatives and undo its known billing mistakes
                        // (glued number words, "for" written as 4) — see [SpeechRepair].
                        val text = SpeechRepair.pickAndRepair(
                            matches,
                            results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        ).trim()
                        if (text.isNotBlank()) {
                            _partialText.value = text
                            processSpokenText(text)
                        } else {
                            _state.value = VoiceBillingState.Error("No words detected. Try again.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let {
                            _partialText.value = it
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN,en-IN,en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error starting speech recognition: ${e.message}", e)
            _state.value = VoiceBillingState.Error("Failed to start voice listener: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    fun processSpokenText(text: String) {
        _state.value = VoiceBillingState.Processing(text)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val draft = FinanceHelper.parseSpeechToDraft(context, text)
                if (draft != null) {
                    _state.value = VoiceBillingState.Parsed(draft, text)
                } else {
                    _state.value = VoiceBillingState.Error("Could not detect voucher details. Example: 'Ramesh ko 5000 ka cement becha cash'")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error parsing speech to voucher: ${e.message}", e)
                _state.value = VoiceBillingState.Error("Parsing error: ${e.message}")
            }
        }
    }

    suspend fun saveParsedVoucher(draft: AiResponse.DraftVoucher): Boolean = withContext(Dispatchers.IO) {
        val companyId = SessionManager.currentCompanyId.value ?: return@withContext false
        try {
            val voucherDate = FinanceHelper.parseVoucherDate(draft.date)
            val vNum = if (!draft.voucherNumber.isNullOrEmpty()) draft.voucherNumber else "V-${System.currentTimeMillis() % 100000}"
            val voucherType = draft.type ?: "Sales"

            val voucher = Voucher(
                voucherNumber = vNum,
                date = voucherDate,
                type = voucherType,
                narration = draft.narration ?: "Voice-generated voucher",
                companyId = companyId,
                refDisplayName = draft.partyName,
                customField = draft.partyName
            )

            val existingLedgers = ledgerRepo.getAllLedgersSync(companyId).toMutableList()
            val voucherEntries = mutableListOf<VoucherEntry>()
            val inventoryEntries = mutableListOf<InventoryEntry>()
            val autoAllocations = mutableListOf<BillAllocation>()

            // 1. Process Ledger Entries
            draft.entries.forEach { entryDraft ->
                var ledger = existingLedgers.find { it.name.equals(entryDraft.ledgerName, ignoreCase = true) }
                if (ledger == null) {
                    val group = entryDraft.groupSuggestion ?: when {
                        entryDraft.ledgerName.contains("Cash", true) -> "Cash-in-hand"
                        entryDraft.ledgerName.contains("Bank", true) -> "Bank Accounts"
                        entryDraft.ledgerName.contains("Sales", true) -> "Sales Accounts"
                        entryDraft.ledgerName.contains("Purchase", true) -> "Purchase Accounts"
                        entryDraft.isDebit -> "Sundry Debtors"
                        else -> "Sundry Creditors"
                    }
                    val shouldMaintainBillWise = group.contains("Debtor", true) || group.contains("Creditor", true)
                    val newLedger = Ledger(
                        name = entryDraft.ledgerName,
                        group = group,
                        openingBalance = 0L,
                        maintainBillWise = shouldMaintainBillWise,
                        companyId = companyId
                    )
                    val newLedgerId = ledgerRepo.insert(newLedger)
                    ledger = newLedger.copy(id = newLedgerId.toInt())
                    existingLedgers.add(ledger)
                }

                voucherEntries.add(
                    VoucherEntry(
                        ledgerId = ledger.id,
                        isDebit = entryDraft.isDebit,
                        amount = entryDraft.amount.rupeesToPaise(),
                        voucherId = 0
                    )
                )

                if (ledger.maintainBillWise) {
                    autoAllocations.add(
                        BillAllocation(
                            voucherEntryId = ledger.id,
                            refType = "New Ref",
                            refName = vNum,
                            amount = entryDraft.amount.rupeesToPaise()
                        )
                    )
                }
            }

            // 2. Process Inventory Items
            val stockItemDao = db.stockItemDao()
            val unitDao = db.unitDao()

            val allUnits = unitDao.getAllUnits(companyId)
            draft.inventoryItems.forEach { itemDraft ->
                val (canonicalName, qty, deducedUnit) = LanguageTranslationUtils.canonicalItemDetails(
                    itemDraft.name,
                    itemDraft.quantity,
                    itemDraft.unit
                )
                val qtyChanged = qty != itemDraft.quantity
                val rate = if (qtyChanged && qty > 0 && itemDraft.amount > 0) itemDraft.amount / qty else itemDraft.rate

                var stockItem = stockItemDao.getStockItemByName(companyId, canonicalName)
                if (stockItem == null) {
                    val key = LanguageTranslationUtils.itemKey(canonicalName)
                    stockItem = stockItemDao.getAllStockItems(companyId)
                        .firstOrNull { LanguageTranslationUtils.itemKey(it.name) == key }
                }

                val unitName = ProductCategory.resolveUnit(
                    deducedUnit
                        ?: stockItem?.unitId?.let { savedId -> allUnits.firstOrNull { it.id == savedId }?.name }
                        ?: ProductCategory.defaultUnitForItem(canonicalName)
                )
                var unit = allUnits.firstOrNull {
                    it.name.equals(unitName, ignoreCase = true) ||
                    ProductCategory.resolveUnit(it.name).equals(unitName, ignoreCase = true)
                }
                if (unit == null) {
                    val newUnit = Unit(name = unitName, companyId = companyId)
                    val newUnitId = unitDao.insert(newUnit)
                    unit = newUnit.copy(id = newUnitId.toInt())
                }

                if (stockItem == null) {
                    val smartTax = SmartTaxService.getInstance(context)
                    val taxInfo = smartTax.getTaxInfoForItem(canonicalName)
                    val gstRate = itemDraft.gstRate ?: taxInfo?.gstRate ?: 0.0
                    val hsn = taxInfo?.hsnCode

                    val newItem = StockItem(
                        name = canonicalName,
                        unitId = unit.id,
                        openingStock = 0.0,
                        rate = rate,
                        companyId = companyId,
                        hsnCode = hsn,
                        gstRate = gstRate,
                        cgstRate = gstRate / 2,
                        sgstRate = gstRate / 2
                    )
                    val newId = stockItemDao.insert(newItem)
                    stockItem = newItem.copy(id = newId.toInt())
                } else {
                    val currentUnit = allUnits.firstOrNull { it.id == stockItem.unitId }
                    val isGeneric = currentUnit == null || currentUnit.name.lowercase() in listOf("units", "unit", "nos", "")
                    val currentUnitName = currentUnit?.name
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

                inventoryEntries.add(
                    InventoryEntry(
                        stockItemId = stockItem.id,
                        quantity = qty,
                        rate = rate,
                        amount = itemDraft.amount.rupeesToPaise(),
                        voucherId = 0
                    )
                )
            }

            if (voucherEntries.isEmpty()) {
                _state.value = VoiceBillingState.Error("No valid accounts detected for voucher")
                return@withContext false
            }

            val savedId = voucherRepo.insertVoucherWithAllEntries(voucher, voucherEntries, inventoryEntries)
            if (savedId > 0) {
                // Link bill allocations with created voucher entries
                if (autoAllocations.isNotEmpty()) {
                    val createdEntries = db.voucherEntryDao().getVoucherEntriesForVoucher(savedId.toInt())
                    autoAllocations.forEach { alloc ->
                        val matchingEntry = createdEntries.find { it.ledgerId == alloc.voucherEntryId }
                        if (matchingEntry != null) {
                            db.billAllocationDao().insert(alloc.copy(voucherEntryId = matchingEntry.id))
                        }
                    }
                }
                _state.value = VoiceBillingState.Success(vNum, "${voucher.type} Voucher #$vNum posted successfully!")
                true
            } else {
                _state.value = VoiceBillingState.Error("Failed to save voucher into database")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error saving parsed voucher: ${e.message}", e)
            _state.value = VoiceBillingState.Error("Save failed: ${e.message}")
            false
        }
    }

    fun reset() {
        stopListening()
        _state.value = VoiceBillingState.Idle
        _partialText.value = ""
    }
}
