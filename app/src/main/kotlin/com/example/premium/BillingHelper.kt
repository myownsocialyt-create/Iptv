package com.example.premium

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class BillingHelper(private val context: Context) : PurchasesUpdatedListener {

    private val TAG = "BillingHelper"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var billingClient: BillingClient? = null

    val priceDisplayMap = sharedPriceDisplayMap

    private var onPurchaseCompleteCallback: ((Boolean, String?) -> Unit)? = null

    companion object {
        private val sharedProductDetailsMap = ConcurrentHashMap<String, ProductDetails>()

        val sharedPriceDisplayMap = mutableStateMapOf<String, String>(
            PremiumManager.PLAN_MONTHLY to "₹690.00/Month",
            PremiumManager.PLAN_YEARLY to "₹2,400.00/Year",
            PremiumManager.PLAN_LIFETIME to "₹2,600.00"
        )
    }

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        val pendingParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(pendingParams)
            .build()

        connectToPlayBilling()
    }

    fun connectToPlayBilling(onConnected: (() -> Unit)? = null) {
        val client = billingClient ?: return
        if (client.isReady) {
            queryAvailableProducts()
            onConnected?.invoke()
            return
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client successfully connected")
                    queryAvailableProducts {
                        mainHandler.post {
                            onConnected?.invoke()
                        }
                    }
                    queryActivePurchases()
                } else {
                    Log.w(TAG, "Billing setup finished with code: ${billingResult.responseCode} (${billingResult.debugMessage})")
                    mainHandler.post {
                        onConnected?.invoke()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected, will retry later")
            }
        })
    }

    fun queryAvailableProducts(onFinished: (() -> Unit)? = null) {
        val client = billingClient ?: run {
            onFinished?.invoke()
            return
        }
        if (!client.isReady) {
            onFinished?.invoke()
            return
        }

        val allIds = listOf(
            PremiumManager.PLAN_MONTHLY,
            PremiumManager.PLAN_YEARLY,
            PremiumManager.PLAN_LIFETIME
        )

        val remainingQueries = AtomicInteger(2)
        val checkDone = {
            if (remainingQueries.decrementAndGet() == 0) {
                mainHandler.post {
                    onFinished?.invoke()
                }
            }
        }

        // 1. Query SUBS separately (BillingClient 8.0 requires all products in a single query to have the same ProductType)
        try {
            val subsProducts = allIds.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
            val subsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(subsProducts)
                .build()

            client.queryProductDetailsAsync(subsParams) { billingResult, queryResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && queryResult != null) {
                    val list = queryResult.productDetailsList
                    Log.d(TAG, "Fetched ${list.size} SUBS products from Play Console")
                    for (details in list) {
                        updateCachedProductDetails(details)
                    }
                } else {
                    Log.w(TAG, "SUBS query response: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                }
                checkDone()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying SUBS products", e)
            checkDone()
        }

        // 2. Query INAPP separately (for lifetime_premium or any plan configured as one-time product)
        try {
            val inAppProducts = allIds.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }
            val inAppParams = QueryProductDetailsParams.newBuilder()
                .setProductList(inAppProducts)
                .build()

            client.queryProductDetailsAsync(inAppParams) { billingResult, queryResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && queryResult != null) {
                    val list = queryResult.productDetailsList
                    Log.d(TAG, "Fetched ${list.size} INAPP products from Play Console")
                    for (details in list) {
                        updateCachedProductDetails(details)
                    }
                } else {
                    Log.w(TAG, "INAPP query response: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                }
                checkDone()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying INAPP products", e)
            checkDone()
        }
    }

    private fun updateCachedProductDetails(details: ProductDetails) {
        val id = details.productId
        sharedProductDetailsMap[id] = details

        if (details.productType == BillingClient.ProductType.SUBS) {
            val phases = details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
            // Prefer the recurring paid phase (priceAmountMicros > 0) so trial/intro offers don't hide the plan rate
            val paidPhase = phases?.lastOrNull { it.priceAmountMicros > 0L }
                ?: phases?.lastOrNull()
                ?: phases?.firstOrNull()
            val formattedPrice = paidPhase?.formattedPrice
            if (!formattedPrice.isNullOrBlank()) {
                val suffix = when (id) {
                    PremiumManager.PLAN_MONTHLY -> "/Month"
                    PremiumManager.PLAN_YEARLY -> "/Year"
                    else -> ""
                }
                mainHandler.post {
                    sharedPriceDisplayMap[id] = "$formattedPrice$suffix"
                }
            }
        } else if (details.productType == BillingClient.ProductType.INAPP) {
            val formattedPrice = details.oneTimePurchaseOfferDetails?.formattedPrice
                ?: details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
            if (!formattedPrice.isNullOrBlank()) {
                val suffix = when (id) {
                    PremiumManager.PLAN_MONTHLY -> "/Month"
                    PremiumManager.PLAN_YEARLY -> "/Year"
                    else -> ""
                }
                mainHandler.post {
                    sharedPriceDisplayMap[id] = "$formattedPrice$suffix"
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

                mainHandler.post {
                    onFinished?.invoke(isAnyActive)
                }
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
                val readyClient = billingClient
                if (readyClient != null && readyClient.isReady) {
                    launchWithReadyClient(activity, readyClient, productId, callback)
                } else {
                    callback(false, "Unable to connect to Google Play Billing.")
                }
            }
            return
        }

        launchWithReadyClient(activity, client, productId, callback)
    }

    private fun launchWithReadyClient(
        activity: Activity,
        client: BillingClient,
        productId: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val cachedDetails = sharedProductDetailsMap[productId]
        if (cachedDetails == null) {
            queryAvailableProducts {
                val refreshedDetails = sharedProductDetailsMap[productId]
                if (refreshedDetails != null) {
                    startBillingFlowForDetails(activity, client, refreshedDetails, callback)
                } else {
                    Toast.makeText(
                        activity,
                        "Product ($productId) not found on Google Play yet. Please check Play Console status.",
                        Toast.LENGTH_LONG
                    ).show()
                    callback(false, "Product details not found for $productId")
                }
            }
            return
        }

        startBillingFlowForDetails(activity, client, cachedDetails, callback)
    }

    private fun startBillingFlowForDetails(
        activity: Activity,
        client: BillingClient,
        details: ProductDetails,
        callback: (Boolean, String?) -> Unit
    ) {
        try {
            val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)

            if (details.productType == BillingClient.ProductType.SUBS) {
                val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
                if (!offerToken.isNullOrEmpty()) {
                    productDetailsParamsBuilder.setOfferToken(offerToken)
                }
            } else if (details.productType == BillingClient.ProductType.INAPP) {
                val offerToken = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
                if (!offerToken.isNullOrEmpty()) {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error launching billing flow", e)
            callback(false, e.localizedMessage)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
            mainHandler.post {
                onPurchaseCompleteCallback?.invoke(true, null)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            mainHandler.post {
                onPurchaseCompleteCallback?.invoke(false, "User canceled")
            }
        } else {
            mainHandler.post {
                onPurchaseCompleteCallback?.invoke(false, billingResult.debugMessage)
            }
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
