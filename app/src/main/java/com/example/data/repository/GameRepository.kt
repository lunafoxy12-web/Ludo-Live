package com.example.data.repository

import com.example.domain.model.OnlineFriend
import com.example.domain.model.TableStake
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UserWallet(
    val coins: Long = 25_000,
    val gems: Long = 150,
    val level: Int = 14,
    val winCount: Int = 42,
    val totalMatches: Int = 68,
    val username: String = "Alex Rivers",
    val isVip: Boolean = false
)

class GameRepository {

    private val _wallet = MutableStateFlow(UserWallet())
    val wallet: StateFlow<UserWallet> = _wallet.asStateFlow()

    private val _onlineFriends = MutableStateFlow(createInitialOnlineFriends())
    val onlineFriends: StateFlow<List<OnlineFriend>> = _onlineFriends.asStateFlow()

    val availableStakes: List<TableStake> = listOf(
        TableStake(
            id = "stake_casual",
            title = "Casual Arena",
            stakeCoins = 500,
            rewardPot = 1_800,
            minLevel = 1,
            badge = "Friendly"
        ),
        TableStake(
            id = "stake_club",
            title = "Club Champions",
            stakeCoins = 2_500,
            rewardPot = 9_200,
            minLevel = 5,
            badge = "Hot"
        ),
        TableStake(
            id = "stake_highroller",
            title = "High Roller Bay",
            stakeCoins = 10_000,
            rewardPot = 37_000,
            minLevel = 10,
            badge = "High Stake"
        ),
        TableStake(
            id = "stake_diamond",
            title = "Grand Master VIP",
            stakeCoins = 50_000,
            rewardPot = 190_000,
            minLevel = 20,
            badge = "Grand Pot"
        )
    )

    fun deductCoins(amount: Long): Boolean {
        if (_wallet.value.coins >= amount) {
            _wallet.update { it.copy(coins = it.coins - amount) }
            return true
        }
        return false
    }

    fun deductGems(amount: Long): Boolean {
        if (_wallet.value.gems >= amount) {
            _wallet.update { it.copy(gems = it.gems - amount) }
            return true
        }
        return false
    }

    fun creditBalance(coins: Long, gems: Long) {
        _wallet.update {
            it.copy(
                coins = it.coins + coins,
                gems = it.gems + gems
            )
        }
    }

    fun recordMatchWon(rewardCoins: Long) {
        _wallet.update {
            it.copy(
                coins = it.coins + rewardCoins,
                winCount = it.winCount + 1,
                totalMatches = it.totalMatches + 1,
                level = it.level + 1
            )
        }
    }

    fun recordMatchFinished() {
        _wallet.update { it.copy(totalMatches = it.totalMatches + 1) }
    }

    private fun createInitialOnlineFriends(): List<OnlineFriend> = listOf(
        OnlineFriend("u1", "Elena Rostova", 28, "68%", 125_000, "Online", "👩‍🦰"),
        OnlineFriend("u2", "Marcus Chen", 35, "74%", 420_000, "Live Video", "🧑‍💻"),
        OnlineFriend("u3", "Sarah Connor", 19, "59%", 85_000, "Online", "👱‍♀️"),
        OnlineFriend("u4", "Liam O'Connor", 42, "81%", 980_000, "In Match", "🧔‍♂️"),
        OnlineFriend("u5", "Priya Sharma", 24, "63%", 210_000, "Online", "🧕"),
        OnlineFriend("u6", "Tariq Mansoor", 15, "52%", 64_000, "Live Video", "👨‍🎨"),
        OnlineFriend("u7", "Zoe Vance", 31, "70%", 512_000, "Online", "👩‍🎤")
    )
}
