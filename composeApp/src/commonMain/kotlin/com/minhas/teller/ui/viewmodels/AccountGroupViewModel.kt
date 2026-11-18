package com.minhas.teller.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minhas.teller.SessionManager
import com.minhas.teller.data.entities.AccountGroup
import com.minhas.teller.data.repositories.AccountGroupRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AccountGroupViewModel(private val repository: AccountGroupRepository) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val allGroups: StateFlow<List<AccountGroup>> = SessionManager.currentCompanyId.flatMapLatest { companyId ->
        if (companyId != null) {
            repository.getAllGroups(companyId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun insert(name: String, parent: String?) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            repository.insert(AccountGroup(name = name, parentGroup = parent, companyId = companyId))
        }
    }

    fun initDefaultGroups(companyId: Int) {
        viewModelScope.launch {
            val existingNames = repository.getGroupNames(companyId).toSet()
            val missing = DEFAULT_GROUP_NAMES.filterNot { it in existingNames }
            if (missing.isNotEmpty()) {
                repository.insertAll(missing.map { AccountGroup(name = it, parentGroup = "Primary", companyId = companyId) })
            }
        }
    }

    companion object {
        val DEFAULT_GROUP_NAMES = listOf(
            "Sundry Debtors", "Sundry Creditors", "Cash-in-hand", "Bank Accounts",
            "Sales Accounts", "Purchase Accounts", "Direct Expenses", "Indirect Expenses",
            "Direct Incomes", "Indirect Incomes", "Fixed Assets", "Current Assets",
            "Loans (Liability)", "Capital Account", "Stock-in-hand", "Finance Companies"
        )

        fun defaultGroupsFor(companyId: Int): List<AccountGroup> =
            DEFAULT_GROUP_NAMES.map { AccountGroup(name = it, parentGroup = "Primary", companyId = companyId) }
    }
}

class AccountGroupViewModelFactory(private val repository: AccountGroupRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>): T {
        if (modelClass.isAssignableFrom(AccountGroupViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AccountGroupViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
