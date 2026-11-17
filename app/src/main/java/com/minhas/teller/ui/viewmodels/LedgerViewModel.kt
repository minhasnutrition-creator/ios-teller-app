package com.minhas.teller.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minhas.teller.SessionManager
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.repositories.LedgerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

class LedgerViewModel(private val repository: LedgerRepository) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val allLedgers: StateFlow<List<Ledger>> = SessionManager.currentCompanyId.flatMapLatest { companyId ->
        if (companyId != null) {
            repository.getAllLedgers(companyId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedLedger = MutableStateFlow<Ledger?>(null)
    val selectedLedger: StateFlow<Ledger?> = _selectedLedger

    fun loadLedger(id: Int) {
        viewModelScope.launch {
            _selectedLedger.value = repository.getLedger(id).firstOrNull()
        }
    }

    fun getLedger(id: Int): Flow<Ledger> {
        return repository.getLedger(id)
    }

    fun insert(ledger: Ledger) = viewModelScope.launch {
        val companyId = SessionManager.currentCompanyId.value ?: return@launch
        repository.insert(ledger.copy(companyId = companyId))
    }

    suspend fun insertLedger(ledger: Ledger): Long {
        val companyId = SessionManager.currentCompanyId.value ?: return -1
        return repository.insert(ledger.copy(companyId = companyId))
    }

    fun update(ledger: Ledger) = viewModelScope.launch {
        repository.update(ledger)
    }

    fun delete(ledger: Ledger) = viewModelScope.launch {
        repository.delete(ledger)
    }

    fun getClosingBalance(ledgerId: Int, toDate: Date): Flow<Long?> {
        val companyId = SessionManager.currentCompanyId.value ?: return flowOf(null)
        return repository.getClosingBalance(ledgerId, companyId, toDate)
    }
}

class LedgerViewModelFactory(private val repository: LedgerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LedgerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LedgerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}