package com.minhas.teller.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minhas.teller.SessionManager
import com.minhas.teller.data.entities.Voucher
import com.minhas.teller.data.entities.VoucherEntry
import com.minhas.teller.data.entities.InventoryEntry
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.entities.VoucherWithAllEntries
import com.minhas.teller.data.entities.BillAllocation
import com.minhas.teller.utils.rupeesToPaise
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.data.repositories.OutstandingVoucher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.minhas.teller.data.dao.VoucherDao
import com.minhas.teller.data.dao.VoucherEntryDao
import com.minhas.teller.data.dao.InventoryEntryDao
import com.minhas.teller.data.dao.LedgerDao
import com.minhas.teller.data.dao.BillAllocationDao
import com.minhas.teller.data.entities.Ledger
import com.minhas.teller.data.repositories.LedgerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class VoucherViewModel(
    private val repository: VoucherRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    private val _allVouchersWithAllEntries = MutableStateFlow<List<VoucherWithAllEntries>>(emptyList())
    val allVouchersWithAllEntries: StateFlow<List<VoucherWithAllEntries>> = _allVouchersWithAllEntries

    private val _vouchersForSelectedLedger = MutableStateFlow<List<VoucherWithAllEntries>>(emptyList())
    val vouchersForSelectedLedger: StateFlow<List<VoucherWithAllEntries>> = _vouchersForSelectedLedger
    
    private val _outstandingVouchers = MutableStateFlow<List<OutstandingVoucher>>(emptyList())
    val outstandingVouchers: StateFlow<List<OutstandingVoucher>> = _outstandingVouchers

    private val _reportOutstandingVouchers = MutableStateFlow<List<OutstandingVoucher>>(emptyList())
    val reportOutstandingVouchers: StateFlow<List<OutstandingVoucher>> = _reportOutstandingVouchers

    private val _allBillAllocations = MutableStateFlow<List<BillAllocation>>(emptyList())
    val allBillAllocations: StateFlow<List<BillAllocation>> = _allBillAllocations

    // Emits the Room-generated ID of the most recently inserted purchase voucher.
    // Consumed by AddEditVoucherScreen to create a linked payment without re-saving the purchase.
    private val _lastInsertedPurchaseId = MutableStateFlow(0)
    val lastInsertedPurchaseId: StateFlow<Int> = _lastInsertedPurchaseId

    val allLedgers: StateFlow<List<Ledger>> =
        SessionManager.currentCompanyId.flatMapLatest { companyId: Int? ->
            if (companyId != null) {
                ledgerRepository.getAllLedgers(companyId)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Observe company changes to reload data
        viewModelScope.launch {
            SessionManager.currentCompanyId.collect { companyId ->
                if (companyId != null) {
                    loadAllVouchersWithAllEntries(companyId)
                } else {
                    _allVouchersWithAllEntries.value = emptyList()
                }
            }
        }
    }

    private fun loadAllVouchersWithAllEntries(companyId: Int) {
        viewModelScope.launch {
            _allVouchersWithAllEntries.value = repository.getAllVouchersWithAllEntries(companyId)
            _allBillAllocations.value = repository.getAllBillAllocations(companyId)
        }
    }

    fun getAllVouchersWithAllEntriesBetweenDates(startDate: Date, endDate: Date) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            _allVouchersWithAllEntries.value = repository.getAllVouchersWithAllEntriesBetweenDates(companyId, startDate, endDate)
            _allBillAllocations.value = repository.getAllBillAllocations(companyId)
        }
    }

    fun getVouchersForLedger(ledgerId: Int) {
        viewModelScope.launch {
             val companyId = SessionManager.currentCompanyId.value ?: return@launch
            _vouchersForSelectedLedger.value = repository.getVouchersWithAllEntriesForLedger(companyId, ledgerId)
        }
    }

    fun getVouchersForLedgerBetweenDates(ledgerId: Int, startDate: Date, endDate: Date) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            _vouchersForSelectedLedger.value = repository.getVouchersWithAllEntriesForLedgerBetweenDates(companyId, ledgerId, startDate, endDate)
        }
    }

    fun insertVoucherWithAllEntries(
        voucher: Voucher, 
        voucherEntries: List<VoucherEntry>, 
        inventoryEntries: List<InventoryEntry>,
        billAllocations: List<BillAllocation> = emptyList()
    ) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            repository.insertVoucherWithAllEntries(voucher.copy(companyId = companyId), voucherEntries, inventoryEntries, billAllocations)
            loadAllVouchersWithAllEntries(companyId) // Refresh list after insertion
            loadReportOutstandingVouchers()
        }
    }

    fun insertPurchaseWithOptionalPayment(
        purchaseVoucher: Voucher,
        purchaseEntries: List<VoucherEntry>,
        purchaseInventoryEntries: List<InventoryEntry>,
        supplierLedgerId: Int,
        paidFromLedgerId: Int?
    ) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            val purchaseId = repository.insertVoucherWithAllEntries(
                purchaseVoucher.copy(companyId = companyId),
                purchaseEntries,
                purchaseInventoryEntries,
                emptyList()
            ).toInt()

            if (paidFromLedgerId != null && paidFromLedgerId != 0) {
                val amount = purchaseEntries.filter { it.isDebit }.sumOf { it.amount }
                val paymentVoucher = Voucher(
                    type = "Payment",
                    date = purchaseVoucher.date,
                    voucherNumber = "${purchaseVoucher.voucherNumber}-PAY",
                    narration = "Auto payment for ${purchaseVoucher.voucherNumber}",
                    customField = null
                )
                val paymentEntries = listOf(
                    VoucherEntry(voucherId = 0, ledgerId = supplierLedgerId, amount = amount, isDebit = true),
                    VoucherEntry(voucherId = 0, ledgerId = paidFromLedgerId, amount = amount, isDebit = false)
                )
                val allocations = listOf(
                    BillAllocation(voucherEntryId = supplierLedgerId, refVoucherId = purchaseId, amount = amount)
                )

                repository.insertVoucherWithAllEntries(
                    paymentVoucher.copy(companyId = companyId),
                    paymentEntries,
                    emptyList(),
                    allocations
                )
            }

            loadAllVouchersWithAllEntries(companyId)
            loadReportOutstandingVouchers()
        }
    }

    

    /**
     * Saves a purchase voucher and exposes its generated ID via [lastInsertedPurchaseId].
     * Use this when the caller needs the ID afterwards (e.g., to link a payment voucher).
     */
    fun insertPurchaseVoucherAndReturnId(
        voucher: Voucher,
        voucherEntries: List<VoucherEntry>,
        inventoryEntries: List<InventoryEntry>,
        billAllocations: List<BillAllocation> = emptyList()
    ) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            val newId = repository.insertVoucherWithAllEntries(
                voucher.copy(companyId = companyId),
                voucherEntries,
                inventoryEntries,
                billAllocations
            ).toInt()
            _lastInsertedPurchaseId.value = newId
            loadAllVouchersWithAllEntries(companyId)
            loadReportOutstandingVouchers()
        }
    }

    /**
     * Creates only a Payment voucher linked to an already-saved purchase voucher.
     * The purchase voucher is NOT re-saved here — it must have been saved before calling this.
     */
    fun insertPaymentForExistingPurchase(
        purchaseVoucherId: Int,
        purchaseVoucherNumber: String,
        purchaseDate: Date,
        supplierLedgerId: Int,
        paidFromLedgerId: Int,
        amount: Double
    ) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch

            val paymentVoucher = Voucher(
                type = "Payment",
                date = purchaseDate,
                voucherNumber = "${purchaseVoucherNumber}-PAY",
                narration = "Payment for purchase ${purchaseVoucherNumber}",
                customField = null
            )
            val paymentEntries = listOf(
                VoucherEntry(voucherId = 0, ledgerId = supplierLedgerId, amount = amount.rupeesToPaise(), isDebit = true),
                VoucherEntry(voucherId = 0, ledgerId = paidFromLedgerId, amount = amount.rupeesToPaise(), isDebit = false)
            )
            // voucherEntryId is used as a matching key during insert (matched against entry.ledgerId
            // in VoucherRepository.insertVoucherWithAllEntries), then replaced with the real entry ID.
            val allocations = listOf(
                BillAllocation(voucherEntryId = supplierLedgerId, refVoucherId = purchaseVoucherId, amount = amount.rupeesToPaise())
            )

            repository.insertVoucherWithAllEntries(
                paymentVoucher.copy(companyId = companyId),
                paymentEntries,
                emptyList(),
                allocations
            )

            loadAllVouchersWithAllEntries(companyId)
            loadReportOutstandingVouchers()
        }
    }

    fun updateVoucherWithAllEntries(
        voucher: Voucher, 
        voucherEntries: List<VoucherEntry>, 
        inventoryEntries: List<InventoryEntry>,
        billAllocations: List<BillAllocation> = emptyList()
    ) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            // A caller-built Voucher with an unset companyId (0) must never reach the
            // full-row @Update — it would hide the voucher from every company-scoped
            // query and look to the user like the voucher was deleted.
            val safeVoucher = if (voucher.companyId == 0) voucher.copy(companyId = companyId) else voucher
            repository.updateVoucherWithAllEntries(safeVoucher, voucherEntries, inventoryEntries, billAllocations)
            loadAllVouchersWithAllEntries(companyId) // Refresh list after update
            loadReportOutstandingVouchers()
        }
    }

    fun deleteVoucherWithAllEntries(voucher: Voucher) {
        viewModelScope.launch {
            repository.deleteVoucherWithAllEntries(voucher)
             val companyId = SessionManager.currentCompanyId.value ?: return@launch
            loadAllVouchersWithAllEntries(companyId) // Refresh list after deletion
            loadReportOutstandingVouchers()
        }
    }

    fun getVoucherWithAllEntriesById(voucherId: Int): StateFlow<VoucherWithAllEntries?> {
        val flow = MutableStateFlow<VoucherWithAllEntries?>(null)
        viewModelScope.launch {
            flow.value = repository.getVoucherWithAllEntriesById(voucherId)
        }
        return flow
    }
    
    fun getOutstandingVouchers(ledgerId: Int) {
        viewModelScope.launch {
            _outstandingVouchers.value = repository.getOutstandingVouchers(ledgerId)
        }
    }
    
    fun settleBill(
        outstanding: OutstandingVoucher,
        settleAmount: Double,
        accountId: Int,
        settleDate: Date
    ) {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            
            val type = if (outstanding.isDebit) "Receipt" else "Payment"
            val voucherNumber = "SET-${outstanding.voucherNumber}"
            
            val voucher = Voucher(
                type = type,
                date = settleDate,
                voucherNumber = voucherNumber,
                narration = "Settlement for bill ${outstanding.voucherNumber}",
                companyId = companyId
            )
            
            // For Receipt: Debit Cash/Bank (accountId), Credit Party (outstanding.ledgerId)
            // For Payment: Debit Party (outstanding.ledgerId), Credit Cash/Bank (accountId)
            val partyEntry = VoucherEntry(
                voucherId = 0,
                ledgerId = outstanding.ledgerId,
                amount = settleAmount.rupeesToPaise(),
                isDebit = !outstanding.isDebit // Flip the outstanding type
            )
            
            val accountEntry = VoucherEntry(
                voucherId = 0,
                ledgerId = accountId,
                amount = settleAmount.rupeesToPaise(),
                isDebit = outstanding.isDebit // Match the outstanding type
            )
            
            val allocation = BillAllocation(
                refVoucherId = outstanding.voucherId,
                amount = settleAmount.rupeesToPaise(),
                refType = "Agst Ref",
                voucherEntryId = outstanding.ledgerId // This is matched in repository by ledgerId during insert
            )
            
            repository.insertVoucherWithAllEntries(
                voucher,
                listOf(partyEntry, accountEntry),
                emptyList(),
                listOf(allocation)
            )
            
            loadAllVouchersWithAllEntries(companyId)
            loadReportOutstandingVouchers()
        }
    }

    fun loadReportOutstandingVouchers() {
        viewModelScope.launch {
            val companyId = SessionManager.currentCompanyId.value ?: return@launch
            _reportOutstandingVouchers.value = repository.getAllOutstandingVouchers(companyId)
        }
    }
    
    fun clearOutstandingVouchers() {
        _outstandingVouchers.value = emptyList()
    }
    
    fun getBillAllocationsForVoucher(voucherId: Int) = repository.getBillAllocationsForVoucher(voucherId)
}

class VoucherViewModelFactory(
    private val voucherDao: VoucherDao,
    private val voucherEntryDao: VoucherEntryDao,
    private val inventoryEntryDao: InventoryEntryDao,
    private val ledgerDao: LedgerDao,
    private val billAllocationDao: BillAllocationDao,
    private val auditLogDao: com.minhas.teller.data.dao.AuditLogDao,
    private val db: AppDatabase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>): T {
        if (modelClass.isAssignableFrom(VoucherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VoucherViewModel(
                VoucherRepository(voucherDao, voucherEntryDao, inventoryEntryDao, billAllocationDao, auditLogDao, ledgerDao, db = db),
                LedgerRepository(ledgerDao)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
