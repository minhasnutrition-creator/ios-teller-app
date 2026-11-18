package com.minhas.teller.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minhas.teller.SessionManager
import com.minhas.teller.data.entities.Unit as TellerUnit
import com.minhas.teller.data.repositories.UnitRepository
import com.minhas.teller.utils.ProductCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UnitViewModel(private val repository: UnitRepository) : ViewModel() {

    private val _allUnits = MutableStateFlow<List<TellerUnit>>(emptyList())
    val allUnits: StateFlow<List<TellerUnit>> = _allUnits

    private val _selectedUnit = MutableStateFlow<TellerUnit?>(null)
    val selectedUnit: StateFlow<TellerUnit?> = _selectedUnit

    init {
        viewModelScope.launch {
            SessionManager.currentCompanyId.collect { companyId ->
                if (companyId != null) {
                    loadAllUnits(companyId)
                } else {
                    _allUnits.value = emptyList()
                }
            }
        }
    }

    private fun loadAllUnits(companyId: Int) {
        viewModelScope.launch {
            val existing = repository.getAllUnits(companyId).map { it.name }.toSet()
            val missing = ProductCategory.ALL_UNITS.filter { it.symbol !in existing }
            if (missing.isNotEmpty()) {
                missing.forEach { predefined ->
                    repository.insert(TellerUnit(name = predefined.symbol, companyId = companyId))
                }
            }
            _allUnits.value = repository.getAllUnits(companyId)
        }
    }

    fun getUnitById(id: Int) {
        viewModelScope.launch {
            _selectedUnit.value = repository.getUnitById(id)
        }
    }

    fun insert(unit: TellerUnit, onResult: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            val id = repository.insert(unit.copy(companyId = companyId))
            loadAllUnits(companyId)
            onResult?.invoke(id)
        }
    }

    fun update(unit: TellerUnit) {
        viewModelScope.launch {
            repository.update(unit)
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            loadAllUnits(companyId)
        }
    }

    fun delete(unit: TellerUnit) {
        viewModelScope.launch {
            repository.delete(unit)
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            loadAllUnits(companyId)
        }
    }
}

class UnitViewModelFactory(private val repository: UnitRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>): T {
        if (modelClass.isAssignableFrom(UnitViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UnitViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
