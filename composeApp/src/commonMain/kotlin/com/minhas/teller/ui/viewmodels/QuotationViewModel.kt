package com.minhas.teller.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minhas.teller.data.entities.Quotation
import com.minhas.teller.data.entities.QuotationItem
import com.minhas.teller.data.repositories.QuotationRepository
import com.minhas.teller.utils.rupeesToPaise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class QuotationViewModel(private val repository: QuotationRepository) : ViewModel() {
    
    private val _quotations = MutableStateFlow<List<Quotation>>(emptyList())
    val quotations: StateFlow<List<Quotation>> = _quotations.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _fromDate = MutableStateFlow(getFirstDayOfMonth())
    val fromDate: StateFlow<Date> = _fromDate.asStateFlow()
    
    private val _toDate = MutableStateFlow(Date())
    val toDate: StateFlow<Date> = _toDate.asStateFlow()
    
    fun loadQuotations(companyId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getQuotationsByDateRange(companyId, _fromDate.value, _toDate.value)
                .collect { list ->
                    _quotations.value = list
                    _isLoading.value = false
                }
        }
    }
    
    fun setDateRange(from: Date, to: Date, companyId: Int) {
        _fromDate.value = from
        _toDate.value = to
        loadQuotations(companyId)
    }
    
    suspend fun getQuotationById(id: Int): Quotation? {
        return repository.getQuotationById(id)
    }
    
    fun createQuotation(
        customerName: String,
        customerPhone: String?,
        customerAddress: String?,
        items: List<QuotationItem>,
        subTotal: Double,
        taxAmount: Double,
        discount: Double,
        grandTotal: Double,
        notes: String?,
        terms: String?,
        companyId: Int,
        expiryDate: java.util.Date? = null,
        onSuccess: (Quotation) -> Unit
    ) {
        viewModelScope.launch {
            val quotationNumber = repository.getNextQuotationNumber(companyId)
            val quotation = Quotation(
                quotationNumber = quotationNumber,
                customerName = customerName,
                customerPhone = customerPhone,
                customerAddress = customerAddress,
                expiryDate = expiryDate,
                items = items,
                subTotal = subTotal.rupeesToPaise(),
                taxAmount = taxAmount.rupeesToPaise(),
                discount = discount.rupeesToPaise(),
                grandTotal = grandTotal.rupeesToPaise(),
                notes = notes,
                terms = terms,
                companyId = companyId
            )
            
            val id = repository.insert(quotation)
            onSuccess(quotation.copy(id = id.toInt()))
        }
    }
    
    fun updateQuotation(quotation: Quotation, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.update(quotation)
            onSuccess()
        }
    }
    
    fun deleteQuotation(quotation: Quotation, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.delete(quotation)
            onSuccess()
        }
    }
    
    private fun getFirstDayOfMonth(): Date {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        return calendar.time
    }
}

class QuotationViewModelFactory(private val repository: QuotationRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuotationViewModel::class.java)) {
            return QuotationViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
