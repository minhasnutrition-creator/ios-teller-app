package com.minhas.teller.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minhas.teller.SessionManager
import com.minhas.teller.data.entities.StockItem
import com.minhas.teller.data.repositories.StockItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StockItemViewModel(private val repository: StockItemRepository) : ViewModel() {

    private val _allStockItems = MutableStateFlow<List<StockItem>>(emptyList())
    val allStockItems: StateFlow<List<StockItem>> = _allStockItems

    private val _selectedStockItem = MutableStateFlow<StockItem?>(null)
    val selectedStockItem: StateFlow<StockItem?> = _selectedStockItem

    init {
        viewModelScope.launch {
            SessionManager.currentCompanyId.collect { companyId ->
                if (companyId != null) {
                    repository.getAllStockItemsFlow(companyId).collect { items ->
                        _allStockItems.value = items
                    }
                } else {
                    _allStockItems.value = emptyList()
                }
            }
        }
    }

    fun getStockItemById(id: Int) {
        viewModelScope.launch {
            _selectedStockItem.value = repository.getStockItemById(id)
        }
    }

    fun addToLocalList(item: StockItem) {
        _allStockItems.value = _allStockItems.value + item
    }

    fun insert(stockItem: StockItem, onResult: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            val id = repository.insert(stockItem.copy(companyId = companyId))
            onResult?.invoke(id)
        }
    }

    fun update(stockItem: StockItem) {
        viewModelScope.launch {
            repository.updateWithHsnBackfill(stockItem)
            // No manual reload needed
        }
    }

    fun delete(stockItem: StockItem) {
        viewModelScope.launch {
            repository.delete(stockItem)
            // No manual reload needed
        }
    }
}

class StockItemViewModelFactory(private val repository: StockItemRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>): T {
        if (modelClass.isAssignableFrom(StockItemViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StockItemViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
