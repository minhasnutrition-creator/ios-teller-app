package com.minhas.teller.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minhas.teller.SessionManager
import com.minhas.teller.data.entities.TaxCategory
import com.minhas.teller.data.repositories.TaxCategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TaxCategoryViewModel(private val repository: TaxCategoryRepository) : ViewModel() {

    private val _allCategories = MutableStateFlow<List<TaxCategory>>(emptyList())
    val allCategories: StateFlow<List<TaxCategory>> = _allCategories

    init {
        viewModelScope.launch {
            SessionManager.currentCompanyId.collect { companyId ->
                if (companyId != null) loadCategories(companyId)
                else _allCategories.value = emptyList()
            }
        }
    }

    fun loadCategories(companyId: Int) {
        viewModelScope.launch {
            _allCategories.value = repository.getAll(companyId)
        }
    }

    fun insert(category: TaxCategory) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            repository.insert(category.copy(companyId = companyId))
            loadCategories(companyId)
        }
    }

    fun insertAll(categories: List<TaxCategory>) {
        viewModelScope.launch {
            repository.insertAll(categories)
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            loadCategories(companyId)
        }
    }

    fun update(category: TaxCategory) {
        viewModelScope.launch {
            repository.update(category)
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            loadCategories(companyId)
        }
    }

    fun delete(category: TaxCategory) {
        viewModelScope.launch {
            repository.delete(category)
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            loadCategories(companyId)
        }
    }

    fun deleteAll(companyId: Int) {
        viewModelScope.launch {
            repository.deleteAll(companyId)
            loadCategories(companyId)
        }
    }
}

class TaxCategoryViewModelFactory(private val repository: TaxCategoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>): T {
        if (modelClass.isAssignableFrom(TaxCategoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaxCategoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
