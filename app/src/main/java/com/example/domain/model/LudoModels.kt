package com.example.domain.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Standard Ludo player colors mapped to board corners.
 */
enum class PlayerColor(
    val displayName: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val trackStartIndex: Int,
    val homeEntranceTrackIndex: Int
) {
    RED(
        displayName = "Red",
        primaryColor = Color(0xFFEF4444),
        secondaryColor = Color(0xFFB91C1C),
        trackStartIndex = 0,
        homeEntranceTrackIndex = 50
    ),
    GREEN(
        displayName = "Green",
        primaryColor = Color(0xFF10B981),
        secondaryColor = Color(0xFF047857),
        trackStartIndex = 13,
        homeEntranceTrackIndex = 11
    ),
    YELLOW(
        displayName = "Yellow",
        primaryColor = Color(0xFFF59E0B),
        secondaryColor = Color(0xFFD97706),
        trackStartIndex = 26,
        homeEntranceTrackIndex = 24
    ),
    BLUE(
        displayName = "Blue",
        primaryColor = Color(0xFF3B82F6),
        secondaryColor = Color(0xFF1D4ED8),
        trackStartIndex = 39,
        homeEntranceTrackIndex = 37
    );

    val next: PlayerColor
        get() = when (this) {
            RED -> GREEN
            GREEN -> YELLOW
            YELLOW -> BLUE
            BLUE -> RED
        }
}

/**
 * Location state of an individual pawn token.
 */
enum class PawnState {
    YARD,
    TRACK,
    HOME_RUN,
    FINISHED
}

data class Pawn(
    val id: Int,
    val color: PlayerColor,
    val step: Int = 0, // 0 = Yard, 1 = Start tile, 1..51 = track, 52..56 = home run, 57 = FINISHED
    val state: PawnState = PawnState.YARD
) {
    val isHome: Boolean get() = state == PawnState.FINISHED
    val isYard: Boolean get() = state == PawnState.YARD
    val isOnTrack: Boolean get() = state == PawnState.TRACK
    val isInHomeRun: Boolean get() = state == PawnState.HOME_RUN
}

data class Player(
    val id: String,
    val name: String,
    val color: PlayerColor,
    val isHuman: Boolean,
    val isBot: Boolean = !isHuman,
    val team: Int = 0, // 0 for FFA; 1 or 2 for 2v2 match
    val pawns: List<Pawn> = (0..3).map { Pawn(id = it, color = color) },
    val coins: Long = 10_000,
    val score: Int = 0,
    val isMuted: Boolean = false,
    val isCameraOn: Boolean = true,
    val audioLevel: Float = 0.5f,
    val sfuStatus: String = "Live SFU (HD)",
    val avatarUrl: String = ""
) {
    val finishedPawnCount: Int get() = pawns.count { it.isHome }
    val hasWon: Boolean get() = pawns.all { it.isHome }
}

enum class GameMode(val label: String, val description: String, val playerCount: Int) {
    CLASSIC_4P("Classic 4P", "Free-for-all 4 players battle to the center", 4),
    QUICK_1V1("Quick 1v1", "Fast 2-player head-to-head showdown", 2),
    TEAM_2V2("2v2 Team Match", "Allied pairs share paths & victory pool", 4),
    OFFLINE_AI("Solo vs AI", "Smart heuristic bots with instant turns", 4)
}

data class TableStake(
    val id: String,
    val title: String,
    val stakeCoins: Long,
    val rewardPot: Long,
    val minLevel: Int = 1,
    val badge: String? = null
)

enum class GiftType(val title: String, val iconText: String, val coinCost: Long, val gemCost: Long = 0) {
    CROWN("Royal Crown", "👑", 200, 2),
    DIAMOND("Flawless Gem", "💎", 500, 5),
    ROCKET("Hyper Rocket", "🚀", 1000, 10),
    LUCKY_DICE("Gold Dice", "🎲", 150, 1),
    FIRE("On Fire!", "🔥", 100, 0),
    HEARTS("Big Love", "💖", 50, 0),
    KISS("Sweet Kiss", "💋", 80, 0)
}

data class ActiveGiftFly(
    val id: String,
    val giftType: GiftType,
    val senderColor: PlayerColor,
    val receiverColor: PlayerColor,
    val startOffset: Offset,
    val endOffset: Offset,
    val progress: Float = 0f
)

data class OnlineFriend(
    val id: String,
    val name: String,
    val level: Int,
    val winRate: String,
    val coins: Long,
    val status: String, // "Online", "In Match", "Live Video"
    val avatarEmoji: String
)
