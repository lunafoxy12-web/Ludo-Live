package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val coins: Long = 50L, // 50 FREE COINS on signup!
    val gems: Long = 10L,
    val winCount: Int = 0,
    val totalMatches: Int = 0,
    val status: String = "online",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreRoom(
    @DocumentId val id: String = "",
    val hostUserId: String = "",
    val gameMode: String = "QUICK_1V1",
    val status: String = "WAITING", // WAITING, PLAYING, FINISHED
    val stakeCoins: Long = 50L,
    val potCoins: Long = 100L,
    val playerUids: List<String> = emptyList(),
    val playerNames: Map<String, String> = emptyMap(),
    val playerPhotos: Map<String, String> = emptyMap(),
    val playerColors: Map<String, String> = emptyMap(),
    val currentTurnUserId: String = "",
    val currentTurnColor: String = "RED",
    val lastDiceValue: Int = 0,
    val lastMovePawnId: Int = -1,
    val lastMoveFromStep: Int = -1,
    val lastMoveToStep: Int = -1,
    val redPawns: List<Long> = listOf(0L, 0L, 0L, 0L),
    val greenPawns: List<Long> = listOf(0L, 0L, 0L, 0L),
    val yellowPawns: List<Long> = listOf(0L, 0L, 0L, 0L),
    val bluePawns: List<Long> = listOf(0L, 0L, 0L, 0L),
    val winnerUserId: String = "",
    val lastNotice: String = "Waiting for players to join...",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreChallenge(
    @DocumentId val id: String = "",
    val fromUserId: String = "",
    val fromUserName: String = "",
    val toUserId: String = "",
    val toUserName: String = "",
    val gameMode: String = "QUICK_1V1",
    val stakeCoins: Long = 50L,
    val status: String = "PENDING", // PENDING, ACCEPTED, DECLINED, CANCELLED
    val roomId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
