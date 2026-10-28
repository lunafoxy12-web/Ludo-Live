package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.FirestoreChallenge
import com.example.data.model.FirestoreRoom
import com.example.data.model.FirestoreUserProfile
import com.example.domain.model.GameMode
import com.example.domain.model.PlayerColor
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreRepository(
    private val db: FirebaseFirestore
) {
    companion object {
        private const val TAG = "FirestoreRepo"

        fun create(context: Context): FirestoreRepository? {
            return try {
                if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                    com.google.firebase.FirebaseApp.initializeApp(context)
                }
                val dbId = try {
                    context.getString(R.string.firestore_database_id)
                } catch (_: Throwable) {
                    null
                }
                val firestore = if (!dbId.isNullOrBlank()) {
                    FirebaseFirestore.getInstance(dbId)
                } else {
                    FirebaseFirestore.getInstance()
                }
                FirestoreRepository(firestore)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize Firestore instance: ${t.message}", t)
                null
            }
        }
    }

    private val auth get() = try { Firebase.auth } catch (_: Throwable) { null }

    fun requireUserId(): String {
        return auth?.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google")
    }

    /**
     * Retrieves or creates a real user profile in Firestore.
     * New users are awarded 50 FREE COINS upon signup.
     */
    fun getOrCreateUserProfile(user: FirebaseUser): Flow<FirestoreUserProfile> = callbackFlow {
        val uid = user.uid
        val docRef = db.collection("users").document(uid)

        // Immediately emit an initial optimistic profile with 50 FREE COINS so UI displays instantly without lag or crash
        val initialProfile = FirestoreUserProfile(
            id = uid,
            displayName = user.displayName ?: "Player ${uid.take(4)}",
            email = user.email ?: "",
            photoUrl = user.photoUrl?.toString() ?: "",
            coins = 50L,
            gems = 10L,
            status = "online"
        )
        trySend(initialProfile)

        // Initial check and creation with 50 free coins
        try {
            docRef.get().addOnSuccessListener { snapshot ->
                try {
                    if (!snapshot.exists()) {
                        val newProfile = hashMapOf(
                            "id" to uid,
                            "displayName" to (user.displayName ?: "Player ${uid.take(4)}"),
                            "email" to (user.email ?: ""),
                            "photoUrl" to (user.photoUrl?.toString() ?: ""),
                            "coins" to 50L, // 50 FREE COINS ON SIGNUP
                            "gems" to 10L,
                            "winCount" to 0,
                            "totalMatches" to 0,
                            "status" to "online",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        docRef.set(newProfile).addOnFailureListener { e ->
                            Log.w(TAG, "Failed to create new user profile: ${e.message}")
                        }
                    } else {
                        // Update online status and timestamp
                        docRef.update(
                            mapOf(
                                "status" to "online",
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnFailureListener { e ->
                            Log.w(TAG, "Failed to update online status: ${e.message}")
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Error processing profile snapshot: ${t.message}")
                }
            }.addOnFailureListener { e ->
                Log.w(TAG, "Failed to fetch user profile: ${e.message}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "docRef.get exception: ${t.message}")
        }

        // Real-time listener for user profile and balance updates
        val listener = try {
            docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Snapshot error for user profile: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val profile = snapshot.toObject(FirestoreUserProfile::class.java)
                        if (profile != null) {
                            trySend(profile)
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Error converting user profile snapshot: ${t.message}")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "addSnapshotListener failed: ${t.message}")
            null
        }

        awaitClose {
            try {
                listener?.remove()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Listens in real-time to other active players who have signed in.
     */
    fun observeOnlineUsers(currentUserId: String): Flow<List<FirestoreUserProfile>> {
        return db.collection("users")
            .whereEqualTo("status", "online")
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.documents
                    .mapNotNull { it.toObject(FirestoreUserProfile::class.java) }
                    .filter { it.id != currentUserId }
            }
            .catch { e ->
                Log.e(TAG, "Error observing online users", e)
                emit(emptyList())
            }
    }

    /**
     * Creates a new real-time match room in Firestore.
     */
    suspend fun createRoom(
        gameMode: GameMode,
        stakeCoins: Long,
        userProfile: FirestoreUserProfile
    ): String {
        val uid = requireUserId()
        val roomId = UUID.randomUUID().toString().take(8)
        val docRef = db.collection("rooms").document(roomId)

        val initialPot = stakeCoins * gameMode.playerCount

        val roomData = hashMapOf(
            "id" to roomId,
            "hostUserId" to uid,
            "gameMode" to gameMode.name,
            "status" to "WAITING",
            "stakeCoins" to stakeCoins,
            "potCoins" to initialPot,
            "playerUids" to listOf(uid),
            "playerNames" to mapOf(uid to userProfile.displayName),
            "playerPhotos" to mapOf(uid to userProfile.photoUrl),
            "playerColors" to mapOf(uid to "RED"),
            "currentTurnUserId" to uid,
            "currentTurnColor" to "RED",
            "lastDiceValue" to 0,
            "lastMovePawnId" to -1,
            "lastMoveFromStep" to -1,
            "lastMoveToStep" to -1,
            "redPawns" to listOf(0L, 0L, 0L, 0L),
            "greenPawns" to listOf(0L, 0L, 0L, 0L),
            "yellowPawns" to listOf(0L, 0L, 0L, 0L),
            "bluePawns" to listOf(0L, 0L, 0L, 0L),
            "winnerUserId" to "",
            "lastNotice" to "Room created! Waiting for players to join...",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // Deduct table stake from host
        db.collection("users").document(uid).update("coins", FieldValue.increment(-stakeCoins)).await()
        docRef.set(roomData).await()
        return roomId
    }

    /**
     * Joins an existing open room.
     */
    suspend fun joinRoom(roomId: String, userProfile: FirestoreUserProfile): Boolean {
        val uid = requireUserId()
        val docRef = db.collection("rooms").document(roomId)

        return db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            if (!snapshot.exists()) return@runTransaction false

            val currentUids = snapshot.get("playerUids") as? List<*> ?: emptyList<Any>()
            if (uid in currentUids) return@runTransaction true // Already joined
            if (currentUids.size >= 4) return@runTransaction false // Full

            val mode = snapshot.getString("gameMode") ?: "CLASSIC_4P"
            val requiredCount = if (mode == "QUICK_1V1") 2 else 4
            val assignedColor = when (currentUids.size) {
                1 -> if (mode == "QUICK_1V1") "YELLOW" else "GREEN"
                2 -> "YELLOW"
                else -> "BLUE"
            }

            val updatedUids = currentUids + uid
            val newStatus = if (updatedUids.size >= requiredCount) "PLAYING" else "WAITING"
            val notice = if (newStatus == "PLAYING") {
                "All players joined! Match starting. Red rolls first."
            } else {
                "${userProfile.displayName} joined. Waiting for more..."
            }

            val stakeCoins = snapshot.getLong("stakeCoins") ?: 50L
            // Deduct stake
            val userDoc = db.collection("users").document(uid)
            transaction.update(userDoc, "coins", FieldValue.increment(-stakeCoins))

            transaction.update(
                docRef,
                mapOf(
                    "playerUids" to updatedUids,
                    "playerNames.$uid" to userProfile.displayName,
                    "playerPhotos.$uid" to userProfile.photoUrl,
                    "playerColors.$uid" to assignedColor,
                    "status" to newStatus,
                    "lastNotice" to notice,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            true
        }.await()
    }

    /**
     * Real-time subscription to game room state (turns, dice rolls, token positions).
     */
    fun observeRoom(roomId: String): Flow<FirestoreRoom?> {
        return db.collection("rooms").document(roomId)
            .snapshots()
            .map { it.toObject(FirestoreRoom::class.java) }
            .catch { e ->
                Log.e(TAG, "Error observing room $roomId", e)
                emit(null)
            }
    }

    /**
     * Broadcasts dice roll in real time to all opponents on the board.
     */
    suspend fun broadcastDiceRoll(
        roomId: String,
        diceValue: Int,
        notice: String
    ) {
        val docRef = db.collection("rooms").document(roomId)
        docRef.update(
            mapOf(
                "lastDiceValue" to diceValue,
                "lastNotice" to notice,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /**
     * Broadcasts token movement and turn transition in real time to all opponents.
     */
    suspend fun broadcastPawnMove(
        roomId: String,
        color: PlayerColor,
        pawnId: Int,
        fromStep: Int,
        toStep: Int,
        pawnsList: List<Long>,
        capturedColor: PlayerColor? = null,
        capturedPawnsList: List<Long>? = null,
        nextTurnUserId: String,
        nextTurnColor: PlayerColor,
        winnerUserId: String?,
        potReward: Long,
        notice: String
    ) {
        val docRef = db.collection("rooms").document(roomId)
        val pawnField = when (color) {
            PlayerColor.RED -> "redPawns"
            PlayerColor.GREEN -> "greenPawns"
            PlayerColor.YELLOW -> "yellowPawns"
            PlayerColor.BLUE -> "bluePawns"
        }

        val updates = mutableMapOf<String, Any>(
            pawnField to pawnsList,
            "lastDiceValue" to 0,
            "lastMovePawnId" to pawnId,
            "lastMoveFromStep" to fromStep,
            "lastMoveToStep" to toStep,
            "currentTurnUserId" to nextTurnUserId,
            "currentTurnColor" to nextTurnColor.name,
            "lastNotice" to notice,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (capturedColor != null && capturedPawnsList != null) {
            val capField = when (capturedColor) {
                PlayerColor.RED -> "redPawns"
                PlayerColor.GREEN -> "greenPawns"
                PlayerColor.YELLOW -> "yellowPawns"
                PlayerColor.BLUE -> "bluePawns"
            }
            updates[capField] = capturedPawnsList
        }

        if (!winnerUserId.isNullOrBlank()) {
            updates["winnerUserId"] = winnerUserId
            updates["status"] = "FINISHED"
            // Award pot in Firestore
            db.collection("users").document(winnerUserId).update(
                mapOf(
                    "coins" to FieldValue.increment(potReward),
                    "winCount" to FieldValue.increment(1),
                    "totalMatches" to FieldValue.increment(1)
                )
            ).await()
        }

        docRef.update(updates).await()
    }

    /**
     * Sends a real challenge to an online user.
     */
    suspend fun sendChallenge(
        fromUser: FirestoreUserProfile,
        toUser: FirestoreUserProfile,
        mode: GameMode,
        stakeCoins: Long
    ): String {
        val challengeId = UUID.randomUUID().toString().take(8)
        val docRef = db.collection("challenges").document(challengeId)

        val data = hashMapOf(
            "id" to challengeId,
            "fromUserId" to fromUser.id,
            "fromUserName" to fromUser.displayName,
            "toUserId" to toUser.id,
            "toUserName" to toUser.displayName,
            "gameMode" to mode.name,
            "stakeCoins" to stakeCoins,
            "status" to "PENDING",
            "roomId" to "",
            "createdAt" to FieldValue.serverTimestamp()
        )

        docRef.set(data).await()
        return challengeId
    }

    /**
     * Listens for real-time incoming challenges from other human players.
     */
    fun observeIncomingChallenges(userId: String): Flow<List<FirestoreChallenge>> {
        return db.collection("challenges")
            .whereEqualTo("toUserId", userId)
            .whereEqualTo("status", "PENDING")
            .snapshots()
            .map { it.toObjects(FirestoreChallenge::class.java) }
            .catch { e ->
                Log.e(TAG, "Error observing challenges", e)
                emit(emptyList())
            }
    }

    suspend fun acceptChallenge(challenge: FirestoreChallenge, userProfile: FirestoreUserProfile): String {
        val mode = GameMode.entries.firstOrNull { it.name == challenge.gameMode } ?: GameMode.QUICK_1V1
        val roomId = createRoom(mode, challenge.stakeCoins, userProfile)

        db.collection("challenges").document(challenge.id).update(
            mapOf(
                "status" to "ACCEPTED",
                "roomId" to roomId
            )
        ).await()

        return roomId
    }

    suspend fun declineChallenge(challengeId: String) {
        db.collection("challenges").document(challengeId).update("status", "DECLINED").await()
    }

    suspend fun addCoins(amount: Long) {
        val uid = requireUserId()
        db.collection("users").document(uid).update("coins", FieldValue.increment(amount)).await()
    }

    suspend fun deductCoins(amount: Long) {
        val uid = requireUserId()
        db.collection("users").document(uid).update("coins", FieldValue.increment(-amount)).await()
    }

    suspend fun addCoinsAndGems(coins: Long, gems: Long) {
        val uid = requireUserId()
        val updates = mutableMapOf<String, Any>()
        if (coins != 0L) updates["coins"] = FieldValue.increment(coins)
        if (gems != 0L) updates["gems"] = FieldValue.increment(gems)
        if (updates.isNotEmpty()) {
            db.collection("users").document(uid).update(updates).await()
        }
    }

    suspend fun deductCoinsAndGems(coins: Long, gems: Long) {
        val uid = requireUserId()
        val updates = mutableMapOf<String, Any>()
        if (coins > 0L) updates["coins"] = FieldValue.increment(-coins)
        if (gems > 0L) updates["gems"] = FieldValue.increment(-gems)
        if (updates.isNotEmpty()) {
            db.collection("users").document(uid).update(updates).await()
        }
    }

    suspend fun recordMatchResult(isWinner: Boolean, potReward: Long) {
        val uid = requireUserId()
        val updates = mutableMapOf<String, Any>(
            "totalMatches" to FieldValue.increment(1)
        )
        if (isWinner) {
            updates["winCount"] = FieldValue.increment(1)
            updates["coins"] = FieldValue.increment(potReward)
        }
        db.collection("users").document(uid).update(updates).await()
    }
}
