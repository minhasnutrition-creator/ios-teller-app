package com.minhas.teller.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class VoiceBillingState {
    object Idle : VoiceBillingState()
    object Listening : VoiceBillingState()
    data class Processing(val spokenText: String) : VoiceBillingState()
    data class Parsed(val draft: AiResponse.DraftVoucher, val spokenText: String) : VoiceBillingState()
    data class Success(val voucherNumber: String, val message: String) : VoiceBillingState()
    data class Error(val message: String) : VoiceBillingState()
}

/**
 * Manages voice-driven billing and transaction recording.
 * Stubbed for iOS - native SFSpeechRecognizer can be plugged in here.
 */
class VoiceBillingManager(private val context: Context) {

    private val _state = MutableStateFlow<VoiceBillingState>(VoiceBillingState.Idle)
    val state: StateFlow<VoiceBillingState> = _state.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    fun startListening() {
        // TODO: Connect iOS SFSpeechRecognizer
        _state.value = VoiceBillingState.Error("Voice billing not yet supported on iOS")
    }

    fun stopListening() {
        _state.value = VoiceBillingState.Idle
    }

    fun processSpokenText(text: String) {}

    suspend fun saveParsedVoucher(draft: AiResponse.DraftVoucher): Boolean = false

    fun reset() {
        _state.value = VoiceBillingState.Idle
        _partialText.value = ""
    }
}
