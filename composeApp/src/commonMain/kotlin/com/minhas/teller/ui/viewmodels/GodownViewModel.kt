package com.minhas.teller.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minhas.teller.SessionManager
import com.minhas.teller.data.entities.Godown
import com.minhas.teller.data.repositories.GodownRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GodownViewModel(private val repository: GodownRepository) : ViewModel() {

    private val _allGodowns = MutableStateFlow<List<Godown>>(emptyList())
    val allGodowns: StateFlow<List<Godown>> = _allGodowns

    init {
        viewModelScope.launch {
            SessionManager.currentCompanyId.collect { companyId ->
                if (companyId != null) loadGodowns(companyId)
                else _allGodowns.value = emptyList()
            }
        }
    }

    fun loadGodowns(companyId: Int) {
        viewModelScope.launch {
            _allGodowns.value = repository.getAllGodowns(companyId)
        }
    }

    fun insert(godown: Godown) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            repository.insert(godown.copy(companyId = companyId))
            loadGodowns(companyId)
        }
    }

    fun update(godown: Godown) {
        viewModelScope.launch {
            repository.update(godown)
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            loadGodowns(companyId)
        }
    }

    fun delete(godown: Godown) {
        viewModelScope.launch {
            repository.delete(godown)
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            loadGodowns(companyId)
        }
    }
}

class GodownViewModelFactory(private val repository: GodownRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>): T {
        if (modelClass.isAssignableFrom(GodownViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GodownViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
