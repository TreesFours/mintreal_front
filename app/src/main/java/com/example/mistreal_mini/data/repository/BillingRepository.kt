package com.example.mistreal_mini.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.android.billingclient.api.*
import com.example.mistreal_mini.util.DeviceIdProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.example.mistreal_mini.data.Resource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val infoRepository: InfoRepository,
    private val deviceIdProvider: DeviceIdProvider
) : PurchasesUpdatedListener {

    private val deviceId: String
        get() = deviceIdProvider.deviceId

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    // Carries which add-on was actually granted (from the backend's
    // resolved addonId), so the Subscription screen's checklist can update
    // the right row rather than just "something succeeded."
    private val _purchaseSuccess = MutableSharedFlow<String?>()
    val purchaseSuccess = _purchaseSuccess.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) { }
            override fun onBillingServiceDisconnected() { startConnection() }
        })
    }

    fun launchBillingFlow(activity: Activity, productId: String) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val offerToken = productDetails.subscriptionOfferDetails?.get(0)?.offerToken ?: ""
                
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .setOfferToken(offerToken)
                                .build()
                        )
                    )
                    .build()

                billingClient.launchBillingFlow(activity, billingFlowParams)
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    _errorEvent.emit("Product not found: $productId")
                }
            }
        }
    }

    /**
     * The non-Play rail — only ever called for installs where
     * BillingRail.isPlayStoreInstall(context) is false. Opens the backend's
     * Stripe Checkout URL in the system browser; there's no in-app purchase
     * callback like Play Billing's, so the Subscription screen just
     * re-polls /api/payment/my-addons when it's next resumed to pick up the
     * grant the Stripe webhook already wrote server-side.
     */
    fun launchStripeCheckout(activity: Activity, addonId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = infoRepository.createStripeCheckout(deviceId, addonId)
            if (result is Resource.Success) {
                val url = result.data
                if (url != null) {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } else {
                    _errorEvent.emit("Checkout failed to start")
                }
            } else {
                _errorEvent.emit(result.message ?: "Checkout failed to start")
            }
        }
    }

    /** Stripe equivalent of openCancelSubscription's Play Store deep link. */
    fun launchStripePortal(activity: Activity) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = infoRepository.createStripePortalSession(deviceId)
            val url = if (result is Resource.Success) result.data else null
            if (url != null) {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } else {
                _errorEvent.emit(result.message ?: "Couldn't open subscription management")
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    verifyPurchaseWithBackend(purchase)
                }
            }
        } else if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            CoroutineScope(Dispatchers.IO).launch {
                _errorEvent.emit("Billing error: ${billingResult.debugMessage}")
            }
        }
    }

    private fun verifyPurchaseWithBackend(purchase: Purchase) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = infoRepository.verifyPayment(deviceId, purchase.purchaseToken, purchase.products[0])
            if (result is Resource.Success) {
                _purchaseSuccess.emit(result.data?.addonId)
                acknowledgePurchase(purchase)
            } else {
                _errorEvent.emit("Verification failed: ${result.message}")
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { }
        }
    }
}
