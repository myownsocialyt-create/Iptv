package com.example.premium

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateMapOf
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BillingHelper(private val context: Context) : PurchasesUpdatedListener {

    private val TAG = "BillingHelper"

    private var billingClient: BillingClient? = null
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    val priceDisplayMap = mutableStateMapOf<String, String>(
        PremiumManager.PLAN_MONTHLY to "₹690.00/Month",
        PremiumManager.PLAN_YEARLY to "₹2,400.00/Year",
        PremiumManager.PLAN_LIFETIME to "₹2,600.00"
    )

    private var onPurchaseCompleteCallback: ((Boolean, String?) -> Unit)? = null

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        val pendingParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(pendingParams)
            .build()

        connectToPlayBilling()
    }

    fun connectToPlayBilling(onConnected: (() -> Unit)? = null) {
        val client = billingClient ?: return
        if (client.isReady) {
            onConnected?.invoke()
            return
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client successfully connected")
                    queryAvailableProducts()
                    queryActivePurchases()
                    onConnected?.invoke()
                } else {
                    Log.w(TAG, "Billing setup finished with code: ${billingResult.responseCode}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected, will retry later")
            }
        })
    }

    private fun queryAvailableProducts() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PremiumManager.PLAN_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PremiumManager.PLAN_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PremiumManager.PLAN_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        client.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && queryProductDetailsResult != null) {
                val list = queryProductDetailsResult.productDetailsList
                for (details in list) {
                    val id = details.productId
                    productDetailsMap[id] = details

                    if (details.productType == BillingClient.ProductType.SUBS) {
                        val pricingPhase = details.subscriptionOfferDetails?.firstOrNull()
                            ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                        val formattedPrice = pricingPhase?.formattedPrice
                        if (formattedPrice != null) {
                            val suffix = if (id == PremiumManager.PLAN_MONTHLY) "/Month" else "/Year"
                            priceDisplayMap[id] = "$formattedPrice$suffix"
                        }
                    } else if (details.productType == BillingClient.ProductType.INAPP) {
                        val formattedPrice = details.oneTimePurchaseOfferDetails?.formattedPrice
                        if (formattedPrice != null) {
                            priceDisplayMap[id] = formattedPrice
                        }
                    }
                }
            }
        }
    }

    fun queryActivePurchases(onFinished: ((Boolean) -> Unit)? = null) {
        val client = billingClient ?: run {
            onFinished?.invoke(false)
            return
        }
        if (!client.isReady) {
            connectToPlayBilling {
                queryActivePurchases(onFinished)
            }
            return
        }

        var isAnyActive = false

        // Check Subscriptions
        val subParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        client.queryPurchasesAsync(subParams) { subResult, subPurchases ->
            if (subResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in subPurchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        isAnyActive = true
                        handlePurchase(purchase)
                    }
                }
            }

            // Check In-App (Lifetime)
            val inAppParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            client.queryPurchasesAsync(inAppParams) { inAppResult, inAppPurchases ->
                if (inAppResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (purchase in inAppPurchases) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            isAnyActive = true
                            handlePurchase(purchase)
                        }
                    }
                }

                if (!isAnyActive && PremiumManager.isPremium(context)) {
                    // Retain existing local premium if set manually or already active
                }
                onFinished?.invoke(isAnyActive)
            }
        }
    }

    fun launchPurchaseFlow(
        activity: Activity,
        productId: String,
        callback: (Boolean, String?) -> Unit
    ) {
        this.onPurchaseCompleteCallback = callback
        val client = billingClient

        if (client == null || !client.isReady) {
            connectToPlayBilling {
                launchPurchaseFlow(activity, productId, callback)
            }
            return
        }

        val details = productDetailsMap[productId]
        if (details == null) {
            // If details not loaded yet (e.g. offline or pending Play Console publishing),
            // inform user gracefully
            Toast.makeText(activity, "Connecting to Google Play... please retry in a moment", Toast.LENGTH_SHORT).show()
            queryAvailableProducts()
            callback(false, "Product details not loaded yet. Check Play Store connection.")
            return
        }

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)

        if (details.productType == BillingClient.ProductType.SUBS) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken != null) {
                productDetailsParamsBuilder.setOfferToken(offerToken)
            }
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            callback(false, result.debugMessage)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
            onPurchaseCompleteCallback?.invoke(true, null)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            onPurchaseCompleteCallback?.invoke(false, "User canceled")
        } else {
            onPurchaseCompleteCallback?.invoke(false, billingResult.debugMessage)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val plan = purchase.products.firstOrNull() ?: PremiumManager.PLAN_LIFETIME
            PremiumManager.setPremium(context, true, plan)

            if (!purchase.isAcknowledged) {
                val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient?.acknowledgePurchase(acknowledgeParams) { result ->
                    Log.d(TAG, "Acknowledge purchase response: ${result.responseCode}")
                }
            }
        }
    }

    fun restorePurchases(activity: Activity, onComplete: (Boolean, String) -> Unit) {
        queryActivePurchases { foundActive ->
            if (foundActive || PremiumManager.isPremium(context)) {
                onComplete(true, "Purchases restored successfully!")
            } else {
                onComplete(false, "No active subscription found on this Google account.")
            }
        }
    }

    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
    }
}
