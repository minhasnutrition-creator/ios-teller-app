package com.minhas.teller.utils

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-App Purchasing and Subscription Manager.
 * On iOS, StoreKit 2 is the underlying provider.
 */
class PlayBillingManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    enum class ConnectionState {
        Disconnected,
        Connecting,
        Connected,
        QueryingProducts,
        Ready,
        Error
    }

    sealed class PurchaseStatus {
        object Idle : PurchaseStatus()
        object Processing : PurchaseStatus()
        data class Success(val licenseKey: String) : PurchaseStatus()
        data class Error(val message: String) : PurchaseStatus()
    }

    private val _productDetails = MutableStateFlow<Map<String, Any>>(emptyMap())
    val productDetails = _productDetails.asStateFlow()

    private val _purchaseStatus = MutableStateFlow<PurchaseStatus>(PurchaseStatus.Idle)
    val purchaseStatus = _purchaseStatus.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.Ready)
    val connectionState = _connectionState.asStateFlow()

    fun queryProducts() {
        _connectionState.value = ConnectionState.Ready
    }

    fun launchPurchaseFlow(activity: Any?, productId: String) {
        _purchaseStatus.value = PurchaseStatus.Error("In-App Purchases are handled via Apple App Store.")
    }

    companion object {
        const val SUBS_MONTHLY = "teller_monthly"
        const val SUBS_YEARLY = "teller_yearly"
        const val INAPP_LIFETIME = "teller_lifetime_v2"
    }
}
