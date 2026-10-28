package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreUser(
    @DocumentId val id: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val coins: Long = 50L, // 50-coin sign-up bonus!
    val gems: Long = 10L,
    val winCount: Int = 0,
    val totalMatches: Int = 0,
    val status: String = "online", // online, in_game, offline
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PawnData(
    val pawnId: Int = 0,
    val color: String = "RED",
    val step: Int = 0, // 0 = Yard, 1 = Start, 1..51 = Track, 52..56 = Home-stretch, 57 = Home
    val state: String = "YARD" // YARD, TRACK, HOME_STRETCH, HOME
)

@IgnoreExtraProperties
data class PawnOccupant(
    val color: String = "",
    val pawnId: Int = 0,
    val uid: String = ""
)

@IgnoreExtraProperties
data class LastMoveRecord(
    val pawnId: Int = -1,
    val color: String = "",
    val fromStep: Int = -1,
    val toStep: Int = -1,
    val capturedOpponentColor: String? = null,
    val capturedPawnId: Int? = null,
    val timestamp: Timestamp? = null
)

@IgnoreExtraProperties
data class PlayerMetadata(
    val uid: String = "",
    val displayName: String = "",
    val color: String = "RED",
    val photoUrl: String = "",
    val isReady: Boolean = true
)

@IgnoreExtraProperties
data class FirestoreGame(
    @DocumentId val gameId: String = "",
    val hostUserId: String = "",
    val status: String = "WAITING", // WAITING, IN_PROGRESS, COMPLETED, ABANDONED
    val gameMode: String = "QUICK_1V1", // QUICK_1V1, CLASSIC_4P, TEAM_2V2
    val stakeCoins: Long = 50L,
    val potCoins: Long = 100L,
    val playerUids: List<String> = emptyList(),
    val players: Map<String, PlayerMetadata> = emptyMap(),
    val currentTurnUserId: String = "",
    val currentTurnColor: String = "RED",
    @ServerTimestamp val turnStartTime: Timestamp? = null,
    val turnTimeoutSeconds: Int = 15,
    val consecutiveSixes: Int = 0,
    val diceRoll: Int? = null,
    val diceRolled: Boolean = false,
    val boardOccupancy: Map<String, List<PawnOccupant>> = emptyMap(), // Track index "0".."51" -> occupants
    val homeOccupancy: Map<String, Int> = mapOf("RED" to 0, "GREEN" to 0, "YELLOW" to 0, "BLUE" to 0),
    val pawns: Map<String, List<PawnData>> = emptyMap(),
    val lastMove: LastMoveRecord? = null,
    val winnerUserId: String? = null,
    val winnerColor: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
