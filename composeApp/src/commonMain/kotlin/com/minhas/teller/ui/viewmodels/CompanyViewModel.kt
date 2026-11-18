package com.minhas.teller.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.minhas.teller.data.entities.Company
import com.minhas.teller.data.repositories.CompanyRepository
import kotlinx.coroutines.flow.Flow
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.repositories.LedgerRepository
import com.minhas.teller.data.repositories.AccountGroupRepository
import com.minhas.teller.utils.TaxConfigManager

class CompanyViewModel(
    private val repository: CompanyRepository,
    private val ledgerRepository: LedgerRepository? = null,
    private val accountGroupRepository: AccountGroupRepository? = null
) : ViewModel() {
    val allCompanies: Flow<List<Company>> = repository.allCompanies

    fun getCompany(id: Int): Flow<Company?> = repository.getCompany(id)

    fun insert(company: Company, context: android.content.Context? = null, businessType: String = "General", onResult: (Long) -> Unit) = viewModelScope.launch {
        val id = repository.insert(company)
        val companyId = id.toInt()

        accountGroupRepository?.insertAll(AccountGroupViewModel.defaultGroupsFor(companyId))

        // Create Default Ledgers
        ledgerRepository?.let { repo ->
            repo.insert(Ledger(name = "Cash", group = "Cash-in-hand", openingBalance = 0L, companyId = companyId))
            repo.insert(Ledger(name = "Sales", group = "Sales Accounts", openingBalance = 0L, companyId = companyId))
            repo.insert(Ledger(name = "Purchase", group = "Purchase Accounts", openingBalance = 0L, companyId = companyId))
            
            val countryName = company.country ?: "India"
            val isIndia = countryName.equals("India", true)
            if (isIndia) {
                repo.insert(Ledger(name = "CGST", group = "Duties & Taxes", openingBalance = 0L, companyId = companyId))
                repo.insert(Ledger(name = "SGST", group = "Duties & Taxes", openingBalance = 0L, companyId = companyId))
                repo.insert(Ledger(name = "IGST", group = "Duties & Taxes", openingBalance = 0L, companyId = companyId))
            } else {
                val taxConfig = TaxConfigManager.getConfigForCountry(countryName)
                repo.insert(Ledger(name = taxConfig.taxName, group = "Duties & Taxes", openingBalance = 0L, companyId = companyId))
            }
        }

        // Preload Units and Stock Items based on businessType
        if (context != null) {
            val db = com.minhas.teller.data.AppDatabase.getDatabase(context)
            val unitDao = db.unitDao()
            val stockItemDao = db.stockItemDao()

            when (businessType) {
                "Kirana" -> {
                    val bagsId = unitDao.insert(com.minhas.teller.data.entities.Unit(name = "Bags", formalName = "bags", companyId = companyId)).toInt()
                    val kgsId = unitDao.insert(com.minhas.teller.data.entities.Unit(name = "Kgs", formalName = "kgs", companyId = companyId)).toInt()
                    val packetsId = unitDao.insert(com.minhas.teller.data.entities.Unit(name = "Packets", formalName = "packets", companyId = companyId)).toInt()
                    val litresId = unitDao.insert(com.minhas.teller.data.entities.Unit(name = "Litres", formalName = "litres", companyId = companyId)).toInt()

                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Rice", unitId = bagsId, rate = 2400.0, companyId = companyId, gstRate = 0.0, hsnCode = "1006"))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Wheat Flour", unitId = bagsId, rate = 1200.0, companyId = companyId, gstRate = 0.0, hsnCode = "1101"))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Sugar", unitId = kgsId, rate = 45.0, companyId = companyId, gstRate = 5.0, hsnCode = "1701", cgstRate = 2.5, sgstRate = 2.5))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Mustard Oil", unitId = litresId, rate = 175.0, companyId = companyId, gstRate = 5.0, hsnCode = "1514", cgstRate = 2.5, sgstRate = 2.5))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Soap", unitId = packetsId, rate = 30.0, companyId = companyId, gstRate = 18.0, hsnCode = "3401", cgstRate = 9.0, sgstRate = 9.0))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Toothpaste", unitId = packetsId, rate = 95.0, companyId = companyId, gstRate = 18.0, hsnCode = "3306", cgstRate = 9.0, sgstRate = 9.0))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Soft Drink", unitId = packetsId, rate = 40.0, companyId = companyId, gstRate = 40.0, hsnCode = "2202", cgstRate = 20.0, sgstRate = 20.0))
                }
                "Electronics" -> {
                    val nosId = unitDao.insert(com.minhas.teller.data.entities.Unit(name = "Nos", formalName = "nos", companyId = companyId)).toInt()
                    val setsId = unitDao.insert(com.minhas.teller.data.entities.Unit(name = "Sets", formalName = "sets", companyId = companyId)).toInt()
                    val boxesId = unitDao.insert(com.minhas.teller.data.entities.Unit(name = "Boxes", formalName = "boxes", companyId = companyId)).toInt()

                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Mobile Phone", unitId = nosId, rate = 15000.0, companyId = companyId, gstRate = 18.0, hsnCode = "8517", cgstRate = 9.0, sgstRate = 9.0))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Laptop", unitId = nosId, rate = 45000.0, companyId = companyId, gstRate = 18.0, hsnCode = "8471", cgstRate = 9.0, sgstRate = 9.0))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Television", unitId = setsId, rate = 25000.0, companyId = companyId, gstRate = 18.0, hsnCode = "8528", cgstRate = 9.0, sgstRate = 9.0))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Air Conditioner", unitId = setsId, rate = 35000.0, companyId = companyId, gstRate = 18.0, hsnCode = "8415", cgstRate = 9.0, sgstRate = 9.0))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Washing Machine", unitId = setsId, rate = 18000.0, companyId = companyId, gstRate = 18.0, hsnCode = "8450", cgstRate = 9.0, sgstRate = 9.0))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "LED Bulb", unitId = boxesId, rate = 100.0, companyId = companyId, gstRate = 12.0, hsnCode = "8539", cgstRate = 6.0, sgstRate = 6.0))
                }
                "Vehicle Dealer" -> {
                    val nosId = unitDao.insert(com.minhas.teller.data.entities.Unit(name = "Nos", formalName = "nos", companyId = companyId)).toInt()
                    val unitsId = unitDao.insert(com.minhas.teller.data.entities.Unit(name = "Units", formalName = "units", companyId = companyId)).toInt()

                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Two Wheeler / Bike", unitId = unitsId, rate = 85000.0, companyId = companyId, gstRate = 18.0, hsnCode = "8711", cgstRate = 9.0, sgstRate = 9.0))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Four Wheeler / Car", unitId = unitsId, rate = 650000.0, companyId = companyId, gstRate = 18.0, hsnCode = "8703", cgstRate = 9.0, sgstRate = 9.0))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Engine Oil", unitId = nosId, rate = 350.0, companyId = companyId, gstRate = 18.0, hsnCode = "2710", cgstRate = 9.0, sgstRate = 9.0))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Spare Parts", unitId = nosId, rate = 1200.0, companyId = companyId, gstRate = 18.0, hsnCode = "8708", cgstRate = 9.0, sgstRate = 9.0))
                    stockItemDao.insert(com.minhas.teller.data.entities.StockItem(name = "Battery", unitId = nosId, rate = 4500.0, companyId = companyId, gstRate = 18.0, hsnCode = "8507", cgstRate = 9.0, sgstRate = 9.0))
                }
            }
        }
        
        onResult(id)
    }

    fun update(company: Company) = viewModelScope.launch {
        repository.update(company)
    }

    fun delete(company: Company) = viewModelScope.launch {
        repository.delete(company)
    }
}

class CompanyViewModelFactory(
    private val repository: CompanyRepository,
    private val ledgerRepository: LedgerRepository? = null,
    private val accountGroupRepository: AccountGroupRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
        if (modelClass.isAssignableFrom(CompanyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CompanyViewModel(repository, ledgerRepository, accountGroupRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
