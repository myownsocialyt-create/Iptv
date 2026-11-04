package com.example.premium

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import androidx.compose.runtime.mutableStateMapOf
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class BillingHelper(private val context: Context) : PurchasesUpdatedListener {

    private val TAG = "BillingHelper"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appContext: Context = context.applicationContext

    private var billingClient: BillingClient? = null

    @Volatile
    private var isConnecting: Boolean = false
    private val connectLock = Any()
    private val pendingConnectCallbacks = mutableListOf<() -> Unit>()

    @Volatile
    private var activeSubProductId: String? = null
    @Volatile
    private var activeSubPurchaseToken: String? = null
    @Volatile
    private var hasCheckedActiveSubscription: Boolean = false

    val priceDisplayMap = sharedPriceDisplayMap

    private var onPurchaseCompleteCallback: ((Boolean, String?) -> Unit)? = null

    companion object {
        private val sharedProductDetailsMap = ConcurrentHashMap<String, ProductDetails>()

        val sharedPriceDisplayMap = mutableStateMapOf<String, String>(
            PremiumManager.PLAN_MONTHLY to "₹99.00/Month",
            PremiumManager.PLAN_YEARLY to "₹599.00/Year",
            PremiumManager.PLAN_LIFETIME to "₹1,499.00"
        )
    }

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        val pendingParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(pendingParams)
            .build()

        connectToPlayBilling()
    }

    fun connectToPlayBilling(onConnected: (() -> Unit)? = null) {
        val client = billingClient ?: run {
            if (onConnected != null) {
                mainHandler.post { onConnected.invoke() }
            }
            return
        }

        if (client.isReady) {
            if (onConnected != null) {
                mainHandler.post { onConnected.invoke() }
            }
            return
        }

        synchronized(connectLock) {
            if (onConnected != null) {
                pendingConnectCallbacks.add(onConnected)
            }
            if (isConnecting) {
                return
            }
            isConnecting = true
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val callbacksToRun: List<() -> Unit>
                synchronized(connectLock) {
                    isConnecting = false
                    callbacksToRun = pendingConnectCallbacks.toList()
                    pendingConnectCallbacks.clear()
                }

                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client successfully connected")
                    queryAvailableProducts()
                    // Only auto-run queryActivePurchases if no explicit caller callback is queued,
                    // avoiding duplicate queryActivePurchases calls on the same connection.
                    if (callbacksToRun.isEmpty()) {
                        queryActivePurchases()
                    } else {
                        mainHandler.post {
                            for (cb in callbacksToRun) {
                                cb.invoke()
                            }
                        }
                    }
                } else {
                    Log.w(
                        TAG,
                        "Billing setup finished with code: ${billingResult.responseCode} (${billingResult.debugMessage})"
                    )
                    if (callbacksToRun.isNotEmpty()) {
                        mainHandler.post {
                            for (cb in callbacksToRun) {
                                cb.invoke()
                            }
                        }
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                synchronized(connectLock) {
                    isConnecting = false
                }
                hasCheckedActiveSubscription = false
                Log.w(TAG, "Billing service disconnected; will reconnect lazily on next request")
            }
        })
    }

    fun queryAvailableProducts(onFinished: (() -> Unit)? = null) {
        val client = billingClient ?: run {
            if (onFinished != null) {
                mainHandler.post { onFinished.invoke() }
            }
            return
        }
        if (!client.isReady) {
            if (onFinished != null) {
                mainHandler.post { onFinished.invoke() }
            }
            return
        }

        val remainingQueries = AtomicInteger(2)
        val checkDone = {
            if (remainingQueries.decrementAndGet() == 0) {
                if (onFinished != null) {
                    mainHandler.post {
                        onFinished.invoke()
                    }
                }
            }
        }

        // 1. Query SUBS products (monthly_premium, yearly_premium)
        try {
            val subsIds = listOf(
                PremiumManager.PLAN_MONTHLY,
                PremiumManager.PLAN_YEARLY
            )
            val subsProducts = subsIds.map { id ->
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

        // 2. Query INAPP product (lifetime_premium)
        try {
            val inAppProducts = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PremiumManager.PLAN_LIFETIME)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
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

    private fun pickOffer(details: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
        val offers = details.subscriptionOfferDetails ?: return null
        return offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull()
    }

    private fun updateCachedProductDetails(details: ProductDetails) {
        val id = details.productId
        sharedProductDetailsMap[id] = details

        if (details.productType == BillingClient.ProductType.SUBS) {
            val selectedOffer = pickOffer(details)
            val phases = selectedOffer?.pricingPhases?.pricingPhaseList
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
                mainHandler.post {
                    sharedPriceDisplayMap[id] = formattedPrice
                }
            }
        }
    }

    fun queryActivePurchases(onFinished: ((Boolean) -> Unit)? = null) {
        val client = billingClient ?: run {
            if (onFinished != null) {
                mainHandler.post { onFinished.invoke(false) }
            }
            return
        }
        if (!client.isReady) {
            connectToPlayBilling {
                if (billingClient?.isReady == true) {
                    queryActivePurchases(onFinished)
                } else {
                    if (onFinished != null) {
                        mainHandler.post { onFinished.invoke(false) }
                    }
                }
            }
            return
        }

        val subParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        client.queryPurchasesAsync(subParams) { subResult, subPurchases ->
            val subOk = subResult.responseCode == BillingClient.BillingResponseCode.OK
            var foundPurchasedSub: Purchase? = null

            if (subOk) {
                for (purchase in subPurchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        if (foundPurchasedSub == null) {
                            foundPurchasedSub = purchase
                        }
                        handlePurchase(purchase)
                    }
                }
                if (foundPurchasedSub != null) {
                    activeSubProductId = foundPurchasedSub.products.firstOrNull()
                    activeSubPurchaseToken = foundPurchasedSub.purchaseToken
                } else {
                    activeSubProductId = null
                    activeSubPurchaseToken = null
                }
                hasCheckedActiveSubscription = true
            }

            val inAppParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            client.queryPurchasesAsync(inAppParams) { inAppResult, inAppPurchases ->
                val inAppOk = inAppResult.responseCode == BillingClient.BillingResponseCode.OK
                var foundPurchasedInApp: Purchase? = null

                if (inAppOk) {
                    for (purchase in inAppPurchases) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            if (foundPurchasedInApp == null) {
                                foundPurchasedInApp = purchase
                            }
                            handlePurchase(purchase)
                        }
                    }
                }

                val isAnyActive = (foundPurchasedSub != null) || (foundPurchasedInApp != null)

                mainHandler.post {
                    if (subOk && inAppOk && !isAnyActive) {
                        PremiumManager.setPremium(appContext, false, "")
                    }
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
                    mainHandler.post {
                        callback(false, "Unable to connect to Google Play Billing.")
                    }
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
        val proceedWithDetails = { details: ProductDetails ->
            if (!hasCheckedActiveSubscription) {
                queryActivePurchases {
                    startBillingFlowForDetails(activity, client, details, callback)
                }
            } else {
                startBillingFlowForDetails(activity, client, details, callback)
            }
        }

        val cachedDetails = sharedProductDetailsMap[productId]
        if (cachedDetails == null) {
            queryAvailableProducts {
                val refreshedDetails = sharedProductDetailsMap[productId]
                if (refreshedDetails != null) {
                    proceedWithDetails(refreshedDetails)
                } else {
                    mainHandler.post {
                        callback(false, "Product ($productId) not found on Google Play yet. Please check Play Console status.")
                    }
                }
            }
            return
        }

        proceedWithDetails(cachedDetails)
    }

    private fun startBillingFlowForDetails(
        activity: Activity,
        client: BillingClient,
        details: ProductDetails,
        callback: (Boolean, String?) -> Unit
    ) {
        try {
            val targetProductId = details.productId
            val isCurrentlyPremium = PremiumManager.isPremium(appContext)
            val currentActivePlan = PremiumManager.getActivePlan(appContext)

            // Guard 1: User already has Lifetime Premium
            if (isCurrentlyPremium && currentActivePlan == PremiumManager.PLAN_LIFETIME) {
                val msg = if (targetProductId == PremiumManager.PLAN_LIFETIME) {
                    "You already have this plan active."
                } else {
                    "You already have Lifetime Premium."
                }
                mainHandler.post {
                    callback(false, msg)
                }
                return
            }

            // Guard 2: User already has the exact selected plan active
            if ((isCurrentlyPremium && currentActivePlan == targetProductId) ||
                (!activeSubProductId.isNullOrEmpty() && activeSubProductId == targetProductId)
            ) {
                mainHandler.post {
                    callback(false, "You already have this plan active.")
                }
                return
            }

            val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)

            if (details.productType == BillingClient.ProductType.SUBS) {
                val offerToken = pickOffer(details)?.offerToken
                if (offerToken.isNullOrEmpty()) {
                    mainHandler.post {
                        callback(false, "Subscription offer not available for $targetProductId.")
                    }
                    return
                }
                productDetailsParamsBuilder.setOfferToken(offerToken)
            } else if (details.productType == BillingClient.ProductType.INAPP) {
                val offerToken = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
                if (!offerToken.isNullOrEmpty()) {
                    productDetailsParamsBuilder.setOfferToken(offerToken)
                }
            }

            val flowParamsBuilder = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))

            // If purchasing a SUBS product while a DIFFERENT SUBS product is active, attach SubscriptionUpdateParams
            if (details.productType == BillingClient.ProductType.SUBS) {
                val oldSubId = activeSubProductId
                val oldToken = activeSubPurchaseToken
                if (!oldSubId.isNullOrEmpty() && oldSubId != targetProductId && !oldToken.isNullOrEmpty()) {
                    val updateParams = BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                        .setOldPurchaseToken(oldToken)
                        .setSubscriptionReplacementMode(
                            BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
                        )
                        .build()
                    flowParamsBuilder.setSubscriptionUpdateParams(updateParams)
                }
            }

            val result = client.launchBillingFlow(activity, flowParamsBuilder.build())
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mainHandler.post {
                    callback(false, result.debugMessage.ifBlank { "Unable to launch purchase flow." })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching billing flow", e)
            mainHandler.post {
                callback(false, e.localizedMessage ?: "Error launching billing flow.")
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                var anyPurchased = false
                var anyPending = false

                if (purchases != null) {
                    for (purchase in purchases) {
                        when (purchase.purchaseState) {
                            Purchase.PurchaseState.PURCHASED -> {
                                anyPurchased = true
                                handlePurchase(purchase)
                            }
                            Purchase.PurchaseState.PENDING -> {
                                anyPending = true
                            }
                        }
                    }
                }

                mainHandler.post {
                    when {
                        anyPurchased -> {
                            onPurchaseCompleteCallback?.invoke(true, null)
                        }
                        anyPending -> {
                            onPurchaseCompleteCallback?.invoke(
                                false,
                                "Payment pending. Premium will activate once the payment completes."
                            )
                        }
                        else -> {
                            onPurchaseCompleteCallback?.invoke(
                                false,
                                "Purchase was not completed."
                            )
                        }
                    }
                }
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryActivePurchases { active ->
                    mainHandler.post {
                        onPurchaseCompleteCallback?.invoke(
                            active,
                            if (active) null else "Item already owned but could not be verified. Try Restore Purchases."
                        )
                    }
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                mainHandler.post {
                    onPurchaseCompleteCallback?.invoke(false, "User canceled")
                }
            }

            else -> {
                mainHandler.post {
                    onPurchaseCompleteCallback?.invoke(
                        false,
                        billingResult.debugMessage.ifBlank { "Purchase failed (code ${billingResult.responseCode})." }
                    )
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val plan = purchase.products.firstOrNull() ?: PremiumManager.PLAN_LIFETIME
            if (plan == PremiumManager.PLAN_MONTHLY || plan == PremiumManager.PLAN_YEARLY) {
                activeSubProductId = plan
                activeSubPurchaseToken = purchase.purchaseToken
                hasCheckedActiveSubscription = true
            }

            mainHandler.post {
                // If Lifetime is already active, do not overwrite activePlan with a lower tier subscription
                val currentPlan = PremiumManager.getActivePlan(appContext)
                val effectivePlan = if (
                    PremiumManager.isPremium(appContext) &&
                    currentPlan == PremiumManager.PLAN_LIFETIME &&
                    plan != PremiumManager.PLAN_LIFETIME
                ) {
                    PremiumManager.PLAN_LIFETIME
                } else {
                    plan
                }
                PremiumManager.setPremium(appContext, true, effectivePlan)
            }

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
            mainHandler.post {
                if (foundActive) {
                    onComplete(true, "Purchases restored successfully!")
                } else {
                    onComplete(false, "No active subscription found on this Google account.")
                }
            }
        }
    }

    fun destroy() {
        synchronized(connectLock) {
            isConnecting = false
            pendingConnectCallbacks.clear()
        }
        billingClient?.endConnection()
        billingClient = null
    }
}
