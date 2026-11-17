package com.minhas.teller.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PlayBillingManager(
    private val context: Context,
    private val scope: CoroutineScope
) : PurchasesUpdatedListener {

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails = _productDetails.asStateFlow()

    private val _purchaseStatus = MutableStateFlow<PurchaseStatus>(PurchaseStatus.Idle)
    val purchaseStatus = _purchaseStatus.asStateFlow()

    /** Exposed so the UI can show exactly what step the billing client is on. */
    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    // Region must match setGlobalOptions({ region }) in functions/index.js.
    private val functions = FirebaseFunctions.getInstance("asia-southeast1")

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        // enablePendingPurchases() with no args is deprecated as of Billing Library 7 — it's
        // functionally equivalent to enableOneTimeProducts() below, spelled out explicitly so this
        // doesn't silently break on a future library bump.
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    private var connectionRetryAttempt = 0
    private var connectionTimeoutJob: Job? = null
    private var queryTimeoutJob: Job? = null

    init {
        startConnection()
    }

    private fun startConnection() {
        _connectionState.value = ConnectionState.Connecting
        cancelConnectionTimeout()

        // If the Play Store / Play Services never calls back, the UI shows a spinner forever.
        // Surface a visible error after CONNECTION_TIMEOUT_MS so the user can retry.
        connectionTimeoutJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (_connectionState.value == ConnectionState.Connecting) {
                Log.e("Billing", "Connection timed out after ${CONNECTION_TIMEOUT_MS}ms — onBillingSetupFinished never called")
                _purchaseStatus.value = PurchaseStatus.Error(
                    "Google Play is not responding. Check that the Play Store app is installed and up to date, and that you are signed in with a Google account."
                )
                _connectionState.value = ConnectionState.Error
            }
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    cancelConnectionTimeout()
                    connectionRetryAttempt = 0
                    _connectionState.value = ConnectionState.Connected
                    queryProducts()
                } else {
                    cancelConnectionTimeout()
                    Log.e("Billing", "Setup failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                    _connectionState.value = ConnectionState.Error
                    // Surface the real reason instead of leaving the plan cards to time out into a
                    // generic "couldn't load plans". BILLING_UNAVAILABLE here usually means the Play
                    // Store app on the device is too old for this Billing Library version, or the
                    // signed-in Google account can't transact in its country.
                    _purchaseStatus.value = PurchaseStatus.Error(
                        "Google Play Billing setup failed: " +
                            "${billingResult.responseCode} ${billingResult.debugMessage}"
                    )
                    scheduleReconnect()
                }
            }

            override fun onBillingServiceDisconnected() {
                cancelConnectionTimeout()
                _connectionState.value = ConnectionState.Disconnected
                // The old comment here said "Retry logic should go here in a real app" — there was
                // none, so a dropped connection meant queryProducts() never ran again and every
                // plan card in LicenseScreen would show a spinner forever. Retry with capped
                // exponential backoff instead.
                scheduleReconnect()
            }
        })
    }

    private fun cancelConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null
    }

    private fun cancelQueryTimeout() {
        queryTimeoutJob?.cancel()
        queryTimeoutJob = null
    }

    private fun scheduleReconnect() {
        if (connectionRetryAttempt >= MAX_RECONNECT_ATTEMPTS) {
            _connectionState.value = ConnectionState.Error
            _purchaseStatus.value = PurchaseStatus.Error(
                "Could not connect to Google Play Billing after $MAX_RECONNECT_ATTEMPTS attempts. Check your connection and reopen this screen."
            )
            return
        }
        val delayMs = RECONNECT_BASE_DELAY_MS * (1L shl connectionRetryAttempt)
        connectionRetryAttempt++
        scope.launch {
            delay(delayMs)
            if (!billingClient.isReady) startConnection()
        }
    }

    fun queryProducts() {
        _connectionState.value = ConnectionState.QueryingProducts
        cancelQueryTimeout()

        val subsProductList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SUBS_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SUBS_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val inAppProductList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(INAPP_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("teller_lifetime")
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subsProductList)
            .build()

        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(inAppProductList)
            .build()

        val combinedMap = mutableMapOf<String, ProductDetails>()
        var queriesPending = 2

        queryTimeoutJob = scope.launch {
            delay(QUERY_TIMEOUT_MS)
            if (_connectionState.value == ConnectionState.QueryingProducts) {
                Log.e("Billing", "queryProductDetailsAsync timed out after ${QUERY_TIMEOUT_MS}ms")
                if (combinedMap.isNotEmpty()) {
                    _productDetails.value = combinedMap.toMap()
                    _connectionState.value = ConnectionState.Ready
                } else {
                    _purchaseStatus.value = PurchaseStatus.Error(
                        "Google Play did not respond to the product query."
                    )
                    _connectionState.value = ConnectionState.Error
                }
            }
        }

        fun checkQueryFinished() {
            queriesPending--
            if (queriesPending <= 0) {
                cancelQueryTimeout()
                _productDetails.value = combinedMap.toMap()
                if (combinedMap.isNotEmpty()) {
                    _connectionState.value = ConnectionState.Ready
                    Log.d("Billing", "Loaded ${combinedMap.size} products from Play Store: ${combinedMap.keys.joinToString()}")
                } else {
                    _connectionState.value = ConnectionState.Error
                    _purchaseStatus.value = PurchaseStatus.Error(
                        "Google Play returned 0 products. Verify product IDs (teller_monthly, teller_yearly, teller_lifetime_v2) exist, are Active, and have active Base Plans in Play Console."
                    )
                }
            }
        }

        billingClient.queryProductDetailsAsync(subsParams) { billingResult, result ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                result.productDetailsList.forEach { details ->
                    combinedMap[details.productId] = details
                }
            } else {
                Log.e("Billing", "SUBS query failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
            }
            checkQueryFinished()
        }

        billingClient.queryProductDetailsAsync(inAppParams) { billingResult, result ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                result.productDetailsList.forEach { details ->
                    combinedMap[details.productId] = details
                }
            } else {
                Log.e("Billing", "INAPP query failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
            }
            checkQueryFinished()
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        // Used to `?: return` here, so tapping a plan whose details never loaded did nothing at
        // all — no sheet, no error, no log.
        val details = _productDetails.value[productId]
        if (details == null) {
            _purchaseStatus.value = PurchaseStatus.Error(
                "Plan \"$productId\" isn't available from Google Play on this device/account yet."
            )
            return
        }

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)

        // For Subscriptions, we need to set the OfferToken
        if (details.productType == BillingClient.ProductType.SUBS) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken != null) {
                productDetailsParamsBuilder.setOfferToken(offerToken)
            }
        }

        val productDetailsParamsList = listOf(productDetailsParamsBuilder.build())

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val launchResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(
                "Billing",
                "launchBillingFlow failed: ${launchResult.responseCode} ${launchResult.debugMessage}"
            )
            _purchaseStatus.value = PurchaseStatus.Error(
                "Couldn't open Google Play checkout: " +
                    "${launchResult.responseCode} ${launchResult.debugMessage}"
            )
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            _purchaseStatus.value = PurchaseStatus.Idle
        } else {
            _purchaseStatus.value = PurchaseStatus.Error("Purchase failed: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        // GRANT LICENSE
                        scope.launch(Dispatchers.IO) {
                            processSuccessfulPurchase(purchase)
                        }
                    }
                }
            } else {
                 scope.launch(Dispatchers.IO) {
                    processSuccessfulPurchase(purchase)
                }
            }
        }
    }

    /**
     * Used to just write "type": "PREMIUM" straight into Realtime Database from here, trusting
     * that onPurchasesUpdated firing meant a real purchase happened. That's spoofable (and
     * database.rules.json now denies the client write access to license nodes entirely), so this
     * instead calls the verifyPurchase Cloud Function, which checks the purchase token against the
     * Play Developer API server-side and only then writes the license. See /functions/index.js.
     */
    private suspend fun processSuccessfulPurchase(purchase: Purchase) {
        _purchaseStatus.value = PurchaseStatus.Processing

        val productId = purchase.products.firstOrNull()
        if (productId == null) {
            _purchaseStatus.value = PurchaseStatus.Error("Purchase had no associated product.")
            return
        }

        try {
            val machineId = LicenseUtils.getMachineId(context)

            val data = hashMapOf(
                "purchaseToken" to purchase.purchaseToken,
                "productId" to productId,
                "machineId" to machineId
            )

            val result = functions.getHttpsCallable("verifyPurchase")
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val response = result.data as? Map<String, Any?>
            val licenseKey = response?.get("licenseKey") as? String

            if (licenseKey.isNullOrBlank()) {
                _purchaseStatus.value = PurchaseStatus.Error("Server did not return a license key.")
                return
            }

            LicenseUtils.activateLicenseLocally(context, licenseKey, "PREMIUM")
            _purchaseStatus.value = PurchaseStatus.Success(licenseKey)

        } catch (e: FirebaseFunctionsException) {
            Log.e("Billing", "verifyPurchase rejected", e)
            _purchaseStatus.value = PurchaseStatus.Error(
                "Purchase could not be verified: ${e.message ?: e.code.name}"
            )
        } catch (e: Exception) {
            Log.e("Billing", "Error processing purchase", e)
            _purchaseStatus.value = PurchaseStatus.Error("Failed to activate license: ${e.message}")
        }
    }

    /** What the billing client is currently doing, surfaced to the UI. */
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

    companion object {
        const val SUBS_MONTHLY = "teller_monthly"
        const val SUBS_YEARLY = "teller_yearly"
        const val INAPP_LIFETIME = "teller_lifetime_v2"
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_BASE_DELAY_MS = 1000L
        private const val CONNECTION_TIMEOUT_MS = 15_000L
        private const val QUERY_TIMEOUT_MS = 10_000L
    }
}
