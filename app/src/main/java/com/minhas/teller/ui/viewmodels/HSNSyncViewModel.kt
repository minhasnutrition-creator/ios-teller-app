package com.minhas.teller.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.repositories.HSNSyncRepository
import com.minhas.teller.utils.HSNDataManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for HSN data synchronization
 */
class HSNSyncViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HSNSyncRepository
    private val hsnManager: HSNDataManager

    init {
        val database = AppDatabase.getDatabase(application)
        val hsnDao = database.hsnDao()
        val stockItemDao = database.stockItemDao()
        repository = HSNSyncRepository(application, hsnDao, stockItemDao)
        hsnManager = HSNDataManager.getInstance(application)

        // Load initial stats
        viewModelScope.launch {
            loadStats()
        }
    }

    data class SyncUiState(
        val isLoading: Boolean = false,
        val totalHSNCount: Int = 0,
        val lastSyncDate: String = "Never",
        val isAutoUpdateEnabled: Boolean = true,
        val syncMessage: String? = null,
        val syncSuccess: Boolean = false,
        val addedCount: Int = 0,
        val updatedCount: Int = 0,
        val itemsRefreshed: Int = 0
    )

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    /**
     * Load current stats
     */
    private suspend fun loadStats() {
        val count = hsnManager.getHSNCount()
        val lastUpdate = getLastSyncDate()
        _uiState.value = _uiState.value.copy(
            totalHSNCount = count,
            lastSyncDate = lastUpdate,
            isAutoUpdateEnabled = repository.getStats().isAutoUpdateEnabled
        )
    }

    private fun getLastSyncDate(): String {
        val prefs = getApplication<Application>().getSharedPreferences("HSNDataManager", android.content.Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("last_hsn_update", 0)
        return if (timestamp > 0) {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
        } else "Never"
    }

    /**
     * Trigger manual sync - uses new multi-source HSNDataManager
     */
    fun syncHSNData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, syncMessage = "Connecting to HSN database...")

            val result = hsnManager.importFromMultipleSources()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                syncSuccess = result.success,
                syncMessage = if (result.success) {
                    "✓ ${result.message}\nSource: ${result.source}"
                } else {
                    "✗ ${result.message}"
                },
                addedCount = result.addedCount,
                updatedCount = result.updatedCount,
                lastSyncDate = getLastSyncDate()
            )

            // Refresh stats
            loadStats()
        }
    }
    
    /**
     * Toggle auto-update
     */
    fun toggleAutoUpdate(enabled: Boolean) {
        repository.setAutoUpdate(enabled)
        _uiState.value = _uiState.value.copy(isAutoUpdateEnabled = enabled)
    }
    
    /**
     * Check if sync is recommended (30+ days since last sync)
     */
    fun isSyncRecommended(): Boolean {
        return repository.isSyncNeeded()
    }
    
    /**
     * Refresh stats
     */
    fun refreshStats() {
        viewModelScope.launch {
            loadStats()
        }
    }
}
