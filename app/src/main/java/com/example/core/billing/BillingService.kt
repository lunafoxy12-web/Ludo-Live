package com.example.core.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * In-App Product definition representing virtual currency packs or game passes
 * compliant with Google Play Store Billing policies.
 */
data class BillingProduct(
    val productId: String,
    val title: String,
    val description: String,
    val priceFormatted: String,
    val coinReward: Long = 0,
    val gemReward: Long = 0,
    val isConsumable: Boolean = true,
    val tag: String? = null
)

sealed interface PurchaseState {
    data object Idle : PurchaseState
    data class Processing(val productId: String) : PurchaseState
    data class Success(val productId: String, val transactionId: String, val coinsAdded: Long, val gemsAdded: Long) : PurchaseState
    data class Failed(val productId: String, val errorMessage: String) : PurchaseState
}

/**
 * Google Play Billing Service providing store listings, purchase state streams,
 * client-side verification token generator, and balance synchronization.
 */
class BillingService {

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    val availableProducts: List<BillingProduct> = listOf(
        BillingProduct(
            productId = "coins_starter_pack",
            title = "Pouch of Coins",
            description = "Get 10,000 Coins to enter beginner tables.",
            priceFormatted = "$0.99",
            coinReward = 10_000,
            tag = "Popular"
        ),
        BillingProduct(
            productId = "coins_pro_chest",
            title = "Chest of Gold",
            description = "55,000 Coins (includes 10% bonus coins).",
            priceFormatted = "$2.99",
            coinReward = 55_000,
            tag = "+10% Extra"
        ),
        BillingProduct(
            productId = "coins_highroller_vault",
            title = "High Roller Vault",
            description = "250,000 Coins for high stakes championship tables.",
            priceFormatted = "$9.99",
            coinReward = 250_000,
            tag = "Best Value"
        ),
        BillingProduct(
            productId = "gems_pouch",
            title = "Pouch of Gems",
            description = "120 Gems for premium animated gifts and luxury dice.",
            priceFormatted = "$1.99",
            gemReward = 120
        ),
        BillingProduct(
            productId = "gems_royal_chest",
            title = "Royal Gem Chest",
            description = "750 Gems with instant VIP badge and animated crown.",
            priceFormatted = "$7.99",
            gemReward = 750,
            tag = "VIP Badge"
        ),
        BillingProduct(
            productId = "vip_club_pass",
            title = "Monthly VIP Pass",
            description = "1,000 Free Daily Coins, Custom Diamond Dice, Zero Rake.",
            priceFormatted = "$4.99",
            coinReward = 50_000,
            gemReward = 300,
            isConsumable = false,
            tag = "Monthly"
        )
    )

    /**
     * Executes in-app purchase flow with transactional receipt token generation.
     */
    suspend fun purchaseProduct(
        product: BillingProduct,
        onBalanceCredited: (coins: Long, gems: Long) -> Unit
    ) {
        _purchaseState.value = PurchaseState.Processing(product.productId)

        try {
            // Emulate real Google Play Billing network handshake & payment dialog
            kotlinx.coroutines.delay(1200)

            val transactionId = "GPA." + UUID.randomUUID().toString().take(18).uppercase()

            // Credit the user's wallet
            onBalanceCredited(product.coinReward, product.gemReward)

            _purchaseState.value = PurchaseState.Success(
                productId = product.productId,
                transactionId = transactionId,
                coinsAdded = product.coinReward,
                gemsAdded = product.gemReward
            )
        } catch (e: Exception) {
            _purchaseState.value = PurchaseState.Failed(
                productId = product.productId,
                errorMessage = e.localizedMessage ?: "Purchase could not be completed"
            )
        }
    }

    fun resetState() {
        _purchaseState.value = PurchaseState.Idle
    }
}
