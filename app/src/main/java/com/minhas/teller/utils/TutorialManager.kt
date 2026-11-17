package com.minhas.teller.utils

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

enum class TutorialStep {
    NONE,
    WELCOME,                // 1. Welcome Dialog
    DASHBOARD_HIGHLIGHT_VOUCHER, // 2. Point to Vouchers
    VOUCHER_CREATION_DEMO,  // 3. Inside Voucher: Auto-fill, Save, Auto-delete
    DASHBOARD_HIGHLIGHT_REPORTS, // 4. Back in Dashboard: Point to Reports
    DASHBOARD_HIGHLIGHT_AI,      // 5. Point to AI
    AI_CHAT_INTRO,          // 6. Inside Chat: "Tap Mic"
    AI_VOICE_SIMULATION,    // 7. Wait for voice/Simulate
    AI_SHOW_DRAFT,          // 8. Show draft, then cancel
    COMPLETED
}

object TutorialManager {
    var currentStep by mutableStateOf(TutorialStep.NONE)
    var isTutorialActive by mutableStateOf(false)

    fun startTutorial(context: Context) {
        val prefs = context.getSharedPreferences("TellerPrefs", Context.MODE_PRIVATE)
        // Check if old tutorial was shown, if so, maybe we force new one? 
        // Or just use a new key "interactive_tutorial_v1"
        if (!prefs.getBoolean("interactive_tutorial_v1", false)) {
            currentStep = TutorialStep.WELCOME
            isTutorialActive = true
        }
    }

    fun advanceStep() {
        currentStep = when (currentStep) {
            TutorialStep.WELCOME -> TutorialStep.DASHBOARD_HIGHLIGHT_VOUCHER
            TutorialStep.DASHBOARD_HIGHLIGHT_VOUCHER -> TutorialStep.VOUCHER_CREATION_DEMO
            TutorialStep.VOUCHER_CREATION_DEMO -> TutorialStep.DASHBOARD_HIGHLIGHT_REPORTS
            TutorialStep.DASHBOARD_HIGHLIGHT_REPORTS -> TutorialStep.DASHBOARD_HIGHLIGHT_AI
            TutorialStep.DASHBOARD_HIGHLIGHT_AI -> TutorialStep.AI_CHAT_INTRO
            TutorialStep.AI_CHAT_INTRO -> TutorialStep.AI_VOICE_SIMULATION
            TutorialStep.AI_VOICE_SIMULATION -> TutorialStep.AI_SHOW_DRAFT
            TutorialStep.AI_SHOW_DRAFT -> TutorialStep.COMPLETED
            else -> TutorialStep.COMPLETED
        }
    }

    fun skipTutorial(context: Context) {
        currentStep = TutorialStep.COMPLETED
        isTutorialActive = false
        markCompleted(context)
    }

    fun markCompleted(context: Context) {
        val prefs = context.getSharedPreferences("TellerPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("interactive_tutorial_v1", true).apply()
        isTutorialActive = false
    }
}
