package com.example.presentation.viewmodel

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.billing.BillingProduct
import com.example.core.billing.BillingService
import com.example.core.sound.SoundManager
import com.example.data.model.FirestoreChallenge
import com.example.data.model.FirestoreRoom
import com.example.data.model.FirestoreUserProfile
import com.example.data.repository.FirestoreRepository
import com.example.domain.logic.BotAiEngine
import com.example.domain.logic.LudoBoardRules
import com.example.domain.model.ActiveGiftFly
import com.example.domain.model.GameMode
import com.example.domain.model.GiftType
import com.example.domain.model.Pawn
import com.example.domain.model.PawnState
import com.example.domain.model.Player
import com.example.domain.model.PlayerColor
import com.example.domain.model.TableStake
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class GameUiState(
    val gameMode: GameMode = GameMode.QUICK_1V1,
    val stake: TableStake = TableStake("stake_beginner", "50 Coins Table", 50L, 100L, 1, "Starter"),
    val players: List<Player> = emptyList(),
    val currentTurnColor: PlayerColor = PlayerColor.RED,
    val currentTurnUserId: String = "",
    val diceValue: Int? = null,
    val isDiceRolling: Boolean = false,
    val canRollDice: Boolean = true,
    val movablePawnIds: Set<Int> = emptySet(),
    val timerSeconds: Int = 15,
    val winner: Player? = null,
    val bonusTurnReason: String? = null,
    val activeGifts: List<ActiveGiftFly> = emptyList(),
    val isMatchmaking: Boolean = false,
    val isOnlineDrawerOpen: Boolean = false,
    val isStoreDialogOpen: Boolean = false,
    val giftPickerTarget: PlayerColor? = null,
    val localCameraEnabled: Boolean = false,
    val localMicMuted: Boolean = false,
    val activeRoomId: String? = null,
    val gameNotice: String = "Welcome to Ludo Live! Real Human Multiplayer.",
    val isMultiplayerSynced: Boolean = false
)

class LudoGameViewModel(
    val firestoreRepository: FirestoreRepository? = null,
    val soundManager: SoundManager,
    val billingService: BillingService
) : ViewModel() {

    private val _currentUserProfile = MutableStateFlow<FirestoreUserProfile?>(null)
    val currentUserProfile: StateFlow<FirestoreUserProfile?> = _currentUserProfile.asStateFlow()

    private val _realOnlineUsers = MutableStateFlow<List<FirestoreUserProfile>>(emptyList())
    val realOnlineUsers: StateFlow<List<FirestoreUserProfile>> = _realOnlineUsers.asStateFlow()

    private val _incomingChallenges = MutableStateFlow<List<FirestoreChallenge>>(emptyList())
    val incomingChallenges: StateFlow<List<FirestoreChallenge>> = _incomingChallenges.asStateFlow()

    val availableStakes = listOf(
        TableStake("stake_starter", "50 Coins Table", 50L, 100L, 1, "Free Play"),
        TableStake("stake_club", "250 Coins Table", 250L, 500L, 2, "Club"),
        TableStake("stake_pro", "1,000 Coins Table", 1000L, 2000L, 5, "Pro Arena"),
        TableStake("stake_highroller", "5,000 Coins Table", 5000L, 10000L, 10, "VIP High Roller")
    )

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var roomSyncJob: Job? = null
    private var botTurnJob: Job? = null
    private var autoMoveJob: Job? = null
    private var consecutiveSixes = 0

    init {
        // Prepare local default players so the UI is immediately ready
        setupOfflineGame(GameMode.QUICK_1V1)
    }

    /**
     * Activates Offline/Practice Mode with full stake & credit validation and intelligent bots.
     */
    fun playOffline(mode: GameMode = GameMode.QUICK_1V1, stake: TableStake = availableStakes.first()) {
        val current = _currentUserProfile.value
        val profile = if (current == null) {
            val guest = FirestoreUserProfile(
                id = "local_player",
                displayName = "You (Offline)",
                email = "",
                photoUrl = "",
                coins = 50L,
                gems = 10L,
                status = "online"
            )
            _currentUserProfile.value = guest
            guest
        } else {
            current
        }

        if (profile.coins < stake.stakeCoins) {
            _uiState.update {
                it.copy(
                    isStoreDialogOpen = true,
                    gameNotice = "Not enough coins for 🪙 ${stake.stakeCoins} table! Please refill or select a lower table."
                )
            }
            return
        }

        // Deduct table stake from user profile
        _currentUserProfile.update {
            it?.copy(coins = (it.coins - stake.stakeCoins).coerceAtLeast(0L))
        }

        setupOfflineGame(mode, stake)
    }

    /**
     * Activates Instant Guest Player mode with 50 free coins.
     */
    fun playAsGuest(displayName: String = "Player 1", mode: GameMode = GameMode.QUICK_1V1) {
        val guest = FirestoreUserProfile(
            id = "guest_${UUID.randomUUID().toString().take(6)}",
            displayName = displayName.ifBlank { "Player 1" },
            email = "",
            photoUrl = "",
            coins = 50L,
            gems = 10L,
            status = "online"
        )
        _currentUserProfile.value = guest
        val stake = availableStakes.first()
        _currentUserProfile.update {
            it?.copy(coins = (it.coins - stake.stakeCoins).coerceAtLeast(0L))
        }
        setupOfflineGame(mode, stake)
    }

    private fun setupOfflineGame(mode: GameMode, stake: TableStake = availableStakes.first()) {
        botTurnJob?.cancel()
        timerJob?.cancel()
        roomSyncJob?.cancel()
        autoMoveJob?.cancel()

        val redPlayer = Player(
            id = "local_player",
            name = "You",
            color = PlayerColor.RED,
            isHuman = true,
            isBot = false,
            coins = _currentUserProfile.value?.coins ?: 50L,
            pawns = (0..3).map { Pawn(it, PlayerColor.RED) },
            isCameraOn = false
        )

        val playersList = when (mode) {
            GameMode.QUICK_1V1 -> {
                val yellowBot = Player(
                    id = "bot_yellow",
                    name = "Alex 🤖",
                    color = PlayerColor.YELLOW,
                    isHuman = false,
                    isBot = true,
                    coins = stake.stakeCoins,
                    pawns = (0..3).map { Pawn(it, PlayerColor.YELLOW) }
                )
                listOf(redPlayer, yellowBot)
            }
            GameMode.CLASSIC_4P, GameMode.TEAM_2V2, GameMode.OFFLINE_AI -> {
                val greenBot = Player(
                    id = "bot_green",
                    name = "Mia 🤖",
                    color = PlayerColor.GREEN,
                    isHuman = false,
                    isBot = true,
                    coins = stake.stakeCoins,
                    team = if (mode == GameMode.TEAM_2V2) 1 else 0,
                    pawns = (0..3).map { Pawn(it, PlayerColor.GREEN) }
                )
                val yellowBot = Player(
                    id = "bot_yellow",
                    name = "Alex 🤖",
                    color = PlayerColor.YELLOW,
                    isHuman = false,
                    isBot = true,
                    coins = stake.stakeCoins,
                    team = if (mode == GameMode.TEAM_2V2) 2 else 0,
                    pawns = (0..3).map { Pawn(it, PlayerColor.YELLOW) }
                )
                val blueBot = Player(
                    id = "bot_blue",
                    name = "Leo 🤖",
                    color = PlayerColor.BLUE,
                    isHuman = false,
                    isBot = true,
                    coins = stake.stakeCoins,
                    team = if (mode == GameMode.TEAM_2V2) 1 else 0,
                    pawns = (0..3).map { Pawn(it, PlayerColor.BLUE) }
                )
                listOf(redPlayer, greenBot, yellowBot, blueBot)
            }
        }

        _uiState.update {
            it.copy(
                gameMode = mode,
                stake = stake,
                players = playersList,
                currentTurnColor = PlayerColor.RED,
                currentTurnUserId = "local_player",
                diceValue = null,
                isDiceRolling = false,
                canRollDice = true,
                movablePawnIds = emptySet(),
                winner = null,
                activeRoomId = null,
                isMultiplayerSynced = false,
                gameNotice = "Table stake: 🪙 ${stake.stakeCoins} | Win Pot: 🪙 ${stake.rewardPot}! Your turn 🎲"
            )
        }
    }

    fun onUserAuthenticated(user: FirebaseUser) {
        val repo = firestoreRepository

        // Immediately populate profile state with 50 bonus coins so the app is instantly responsive
        _currentUserProfile.value = FirestoreUserProfile(
            id = user.uid,
            displayName = user.displayName ?: "Player ${user.uid.take(4)}",
            email = user.email ?: "",
            photoUrl = user.photoUrl?.toString() ?: "",
            coins = 50L,
            gems = 10L,
            status = "online"
        )

        try {
            setupLocalPlayerMatch(user)
        } catch (_: Throwable) {}

        if (repo != null) {
            viewModelScope.launch {
                try {
                    repo.getOrCreateUserProfile(user)
                        .catch { e ->
                            // Never crash on network or Firestore errors
                        }
                        .collect { profile ->
                            _currentUserProfile.value = profile
                        }
                } catch (_: Throwable) {}
            }

            viewModelScope.launch {
                try {
                    repo.observeOnlineUsers(user.uid)
                        .catch { e ->
                            emit(emptyList())
                        }
                        .collect { users ->
                            _realOnlineUsers.value = users
                        }
                } catch (_: Throwable) {}
            }

            viewModelScope.launch {
                try {
                    repo.observeIncomingChallenges(user.uid)
                        .catch { e ->
                            emit(emptyList())
                        }
                        .collect { challenges ->
                            _incomingChallenges.value = challenges
                        }
                } catch (_: Throwable) {}
            }
        }
    }

    private fun setupLocalPlayerMatch(user: FirebaseUser) {
        val redPlayer = Player(
            id = user.uid,
            name = user.displayName ?: "You",
            color = PlayerColor.RED,
            isHuman = true,
            isBot = false,
            coins = _currentUserProfile.value?.coins ?: 50L,
            pawns = (0..3).map { Pawn(it, PlayerColor.RED) }
        )

        val yellowPlayer = Player(
            id = "waiting_opponent",
            name = "Waiting for Opponent...",
            color = PlayerColor.YELLOW,
            isHuman = false,
            isBot = false,
            coins = 50L,
            pawns = (0..3).map { Pawn(it, PlayerColor.YELLOW) }
        )

        _uiState.update {
            it.copy(
                players = listOf(redPlayer, yellowPlayer),
                currentTurnColor = PlayerColor.RED,
                currentTurnUserId = user.uid,
                gameNotice = "Signed in! Tap 'Social & Online' below to challenge real players, or roll to practice."
            )
        }
    }

    fun createMultiplayerRoom(mode: GameMode, stake: TableStake) {
        val user = _currentUserProfile.value
        if (user == null) {
            playOffline(mode, stake)
            return
        }

        val repo = firestoreRepository
        if (repo == null) {
            // Offline fallback
            setupOfflineGame(mode, stake)
            return
        }

        if (user.coins < stake.stakeCoins) {
            _uiState.update {
                it.copy(
                    isStoreDialogOpen = true,
                    gameNotice = "Not enough coins for 🪙 ${stake.stakeCoins} table! Please refill."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isMatchmaking = true, stake = stake, gameMode = mode) }
            try {
                val roomId = repo.createRoom(mode, stake.stakeCoins, user)
                _currentUserProfile.update { current ->
                    current?.copy(coins = (current.coins - stake.stakeCoins).coerceAtLeast(0L))
                }
                _uiState.update { it.copy(isMatchmaking = false, activeRoomId = roomId, isMultiplayerSynced = true) }
                subscribeToRoom(roomId, user.id)
            } catch (_: Exception) {
                _uiState.update { it.copy(isMatchmaking = false) }
                setupOfflineGame(mode, stake)
            }
        }
    }

    fun challengeUser(targetUser: FirestoreUserProfile, mode: GameMode, stake: TableStake) {
        val currentUser = _currentUserProfile.value ?: return
        val repo = firestoreRepository ?: return
        if (currentUser.coins < stake.stakeCoins) {
            _uiState.update {
                it.copy(
                    isStoreDialogOpen = true,
                    gameNotice = "Not enough coins for 🪙 ${stake.stakeCoins} table! Please refill."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(gameNotice = "Sent challenge to ${targetUser.displayName} for 🪙 ${stake.stakeCoins}...") }
            repo.sendChallenge(currentUser, targetUser, mode, stake.stakeCoins)
        }
    }

    fun acceptIncomingChallenge(challenge: FirestoreChallenge) {
        val user = _currentUserProfile.value ?: return
        val repo = firestoreRepository ?: return
        if (user.coins < challenge.stakeCoins) {
            _uiState.update {
                it.copy(
                    isStoreDialogOpen = true,
                    gameNotice = "Not enough coins to accept 🪙 ${challenge.stakeCoins} challenge! Refill coins."
                )
            }
            return
        }

        _currentUserProfile.update { current ->
            current?.copy(coins = (current.coins - challenge.stakeCoins).coerceAtLeast(0L))
        }

        viewModelScope.launch {
            val roomId = repo.acceptChallenge(challenge, user)
            _uiState.update {
                it.copy(
                    activeRoomId = roomId,
                    isMultiplayerSynced = true,
                    gameNotice = "Challenge accepted! Joined room with ${challenge.fromUserName}."
                )
            }
            subscribeToRoom(roomId, user.id)
        }
    }

    fun joinRoom(roomId: String) {
        val user = _currentUserProfile.value ?: return
        val repo = firestoreRepository ?: return
        viewModelScope.launch {
            try {
                val success = repo.joinRoom(roomId, user)
                if (success) {
                    _uiState.update {
                        it.copy(
                            activeRoomId = roomId,
                            isMultiplayerSynced = true,
                            gameNotice = "Joined multiplayer room! Roll to start."
                        )
                    }
                    subscribeToRoom(roomId, user.id)
                }
            } catch (e: Exception) {
                Log.w("ViewModel", "joinRoom error: ${e.message}")
            }
        }
    }

    fun declineIncomingChallenge(challenge: FirestoreChallenge) {
        val repo = firestoreRepository ?: return
        viewModelScope.launch {
            repo.declineChallenge(challenge.id)
        }
    }

    private fun subscribeToRoom(roomId: String, currentUserId: String) {
        val repo = firestoreRepository ?: return
        roomSyncJob?.cancel()
        roomSyncJob = viewModelScope.launch {
            repo.observeRoom(roomId).collect { room ->
                if (room == null) return@collect
                syncStateFromFirestore(room, currentUserId)
            }
        }
    }

    private fun syncStateFromFirestore(room: FirestoreRoom, currentUserId: String) {
        val mode = GameMode.entries.firstOrNull { it.name == room.gameMode } ?: GameMode.QUICK_1V1
        val updatedPlayers = mutableListOf<Player>()

        for (uid in room.playerUids) {
            val name = room.playerNames[uid] ?: "Player"
            val colorStr = room.playerColors[uid] ?: "RED"
            val color = PlayerColor.entries.firstOrNull { it.name == colorStr } ?: PlayerColor.RED
            val isLocal = uid == currentUserId

            val rawPawnSteps = when (color) {
                PlayerColor.RED -> room.redPawns
                PlayerColor.GREEN -> room.greenPawns
                PlayerColor.YELLOW -> room.yellowPawns
                PlayerColor.BLUE -> room.bluePawns
            }

            val pawns = rawPawnSteps.mapIndexed { idx, stepLong ->
                val step = stepLong.toInt()
                val state = when {
                    step == 0 -> PawnState.YARD
                    step in 1..51 -> PawnState.TRACK
                    step in 52..56 -> PawnState.HOME_RUN
                    else -> PawnState.FINISHED
                }
                Pawn(id = idx, color = color, step = step, state = state)
            }

            updatedPlayers.add(
                Player(
                    id = uid,
                    name = if (isLocal) "$name (You)" else name,
                    color = color,
                    isHuman = true,
                    isBot = false,
                    pawns = pawns,
                    avatarUrl = room.playerPhotos[uid] ?: ""
                )
            )
        }

        if (room.status == "WAITING") {
            val needed = mode.playerCount - updatedPlayers.size
            if (needed > 0 && updatedPlayers.none { it.color == PlayerColor.YELLOW }) {
                updatedPlayers.add(
                    Player(
                        id = "waiting",
                        name = "Waiting for Opponent...",
                        color = PlayerColor.YELLOW,
                        isHuman = false,
                        isBot = false,
                        pawns = (0..3).map { Pawn(it, PlayerColor.YELLOW) }
                    )
                )
            }
        }

        val turnColor = PlayerColor.entries.firstOrNull { it.name == room.currentTurnColor } ?: PlayerColor.RED
        val isMyTurn = room.currentTurnUserId == currentUserId
        val diceVal = if (room.lastDiceValue > 0) room.lastDiceValue else null

        val myPlayer = updatedPlayers.firstOrNull { it.id == currentUserId }
        val movableIds = if (isMyTurn && diceVal != null) {
            myPlayer?.pawns?.filter { LudoBoardRules.canPawnMove(it, diceVal) }?.map { it.id }?.toSet() ?: emptySet()
        } else {
            emptySet()
        }

        val winnerPlayer = updatedPlayers.firstOrNull { it.id == room.winnerUserId }

        _uiState.update {
            it.copy(
                players = updatedPlayers,
                currentTurnColor = turnColor,
                currentTurnUserId = room.currentTurnUserId,
                diceValue = diceVal,
                canRollDice = isMyTurn && (diceVal == null),
                movablePawnIds = movableIds,
                gameNotice = room.lastNotice,
                winner = winnerPlayer,
                stake = TableStake("room_stake", "${room.stakeCoins} Coins Table", room.stakeCoins, room.potCoins)
            )
        }

        if (room.lastDiceValue > 0 && !isMyTurn) {
            soundManager.playDiceRoll()
        }
        if (room.lastMovePawnId != -1 && !isMyTurn) {
            soundManager.playTokenMove()
        }
        if (winnerPlayer != null) {
            soundManager.playVictoryFanfare()
        }
    }

    fun rollDice() {
        val state = _uiState.value
        if (!state.canRollDice || state.isDiceRolling || state.winner != null) return

        timerJob?.cancel()
        soundManager.playDiceRoll()

        viewModelScope.launch {
            _uiState.update { it.copy(isDiceRolling = true, canRollDice = false) }
            delay(500)

            val rolled = (1..6).random()
            val roomId = state.activeRoomId

            if (roomId != null && firestoreRepository != null) {
                val notice = "${state.currentTurnColor.displayName} rolled $rolled 🎲"
                firestoreRepository.broadcastDiceRoll(roomId, rolled, notice)
            } else {
                // Local or practice mode
                val activePlayer = state.players.firstOrNull { it.color == state.currentTurnColor }
                val movable = activePlayer?.pawns?.filter { LudoBoardRules.canPawnMove(it, rolled) }?.map { it.id }?.toSet() ?: emptySet()

                _uiState.update {
                    it.copy(
                        diceValue = rolled,
                        isDiceRolling = false,
                        movablePawnIds = movable,
                        gameNotice = if (movable.isEmpty()) {
                            "Rolled $rolled. No legal moves."
                        } else if (movable.size == 1) {
                            "Rolled $rolled! Tap glowing piece or yard to move."
                        } else {
                            "Rolled $rolled! Tap glowing piece to move."
                        }
                    )
                }

                if (movable.isEmpty()) {
                    delay(1100)
                    passTurnLocal()
                } else if (activePlayer?.isBot == true) {
                    // Bot turn: auto select and move best pawn
                    delay(700)
                    val bestPawn = BotAiEngine.selectBestMove(activePlayer, rolled, state.players)
                    if (bestPawn != null) {
                        movePawn(bestPawn)
                    } else {
                        passTurnLocal()
                    }
                } else if (movable.size == 1) {
                    // Automatically move the only legal move after a brief delay if user has not tapped
                    autoMoveJob?.cancel()
                    autoMoveJob = viewModelScope.launch {
                        delay(1200)
                        val curr = _uiState.value
                        if (curr.diceValue == rolled && curr.movablePawnIds.size == 1) {
                            val singlePawn = activePlayer?.pawns?.firstOrNull { it.id == movable.first() }
                            if (singlePawn != null) {
                                movePawn(singlePawn)
                            }
                        }
                    }
                }
            }
        }
    }

    fun onPawnClicked(pawn: Pawn) {
        autoMoveJob?.cancel()
        autoMoveJob = null
        val state = _uiState.value
        if (state.winner != null) return
        if (pawn.color != state.currentTurnColor) return
        if (pawn.id !in state.movablePawnIds) return

        movePawn(pawn)
    }

    private fun movePawn(pawn: Pawn) {
        val state = _uiState.value
        val diceRoll = state.diceValue ?: return
        val activeColor = state.currentTurnColor
        val targetStep = LudoBoardRules.computeNewStep(pawn, diceRoll)
        if (targetStep == pawn.step && pawn.step != 0) return

        soundManager.playTokenMove()
        val targetGlobalTrack = LudoBoardRules.getGlobalTrackIndex(activeColor, targetStep)
        val isTargetSafe = targetGlobalTrack != -1 && LudoBoardRules.isTrackIndexSafe(targetGlobalTrack)

        // Check for opponent capture on track
        var capturedOpponent = false
        var capturedColor: PlayerColor? = null
        var capturedPawnsList: List<Long>? = null

        val updatedPlayers = state.players.map { player ->
            if (player.color == activeColor) {
                // Update active player's pawns
                val newPawns = player.pawns.map { p ->
                    if (p.id == pawn.id) {
                        val newState = when {
                            targetStep == 0 -> PawnState.YARD
                            targetStep in 1..51 -> PawnState.TRACK
                            targetStep in 52..56 -> PawnState.HOME_RUN
                            else -> PawnState.FINISHED
                        }
                        p.copy(step = targetStep, state = newState)
                    } else p
                }
                player.copy(pawns = newPawns)
            } else if (targetGlobalTrack != -1 && !isTargetSafe) {
                // Check if any opponent pawn is on the same track index
                var capturedThis = false
                val newPawns = player.pawns.map { oppPawn ->
                    val oppTrack = LudoBoardRules.getGlobalTrackIndex(player.color, oppPawn.step)
                    if (oppTrack == targetGlobalTrack && oppPawn.state == PawnState.TRACK && !capturedThis) {
                        capturedThis = true
                        capturedOpponent = true
                        capturedColor = player.color
                        oppPawn.copy(step = 0, state = PawnState.YARD)
                    } else oppPawn
                }
                if (capturedThis) {
                    capturedPawnsList = newPawns.map { it.step.toLong() }
                }
                player.copy(pawns = newPawns)
            } else {
                player
            }
        }

        if (capturedOpponent) {
            soundManager.playTokenCapture()
        }

        val myPlayer = updatedPlayers.first { it.color == activeColor }
        val hasWon = myPlayer.pawns.all { it.state == PawnState.FINISHED }
        val earnsBonus = diceRoll == 6 || targetStep == LudoBoardRules.MAX_STEPS || capturedOpponent

        val nextPlayer = if (earnsBonus && !hasWon) {
            myPlayer
        } else {
            val currentIdx = updatedPlayers.indexOfFirst { it.color == activeColor }
            val nextIdx = (currentIdx + 1) % updatedPlayers.size
            updatedPlayers[nextIdx]
        }

        val notice = if (hasWon) {
            "🏆 ${myPlayer.name} has WON the match!"
        } else if (earnsBonus) {
            if (capturedOpponent) "Piece Captured! Bonus roll!" else if (diceRoll == 6) "Rolled a 6! Bonus roll!" else "Pawn reached Home! Bonus roll!"
        } else {
            "${nextPlayer.name}'s turn 🎲"
        }

        // IMMEDIATE OPTIMISTIC STATE UPDATE FOR CRISP REAL-TIME UI
        _uiState.update {
            it.copy(
                players = updatedPlayers,
                currentTurnColor = nextPlayer.color,
                currentTurnUserId = nextPlayer.id,
                winner = if (hasWon) myPlayer else null,
                diceValue = null,
                isDiceRolling = false,
                canRollDice = !hasWon && nextPlayer.isHuman,
                movablePawnIds = emptySet(),
                gameNotice = notice
            )
        }

        if (hasWon) {
            soundManager.playVictoryFanfare()
            val potReward = state.stake.rewardPot
            if (myPlayer.isHuman) {
                _currentUserProfile.update { current ->
                    current?.copy(
                        coins = current.coins + potReward,
                        winCount = current.winCount + 1,
                        totalMatches = current.totalMatches + 1
                    )
                }
                firestoreRepository?.let { repo ->
                    viewModelScope.launch {
                        try { repo.recordMatchResult(isWinner = true, potReward = potReward) } catch (_: Throwable) {}
                    }
                }
            } else {
                _currentUserProfile.update { current ->
                    current?.copy(totalMatches = current.totalMatches + 1)
                }
                firestoreRepository?.let { repo ->
                    viewModelScope.launch {
                        try { repo.recordMatchResult(isWinner = false, potReward = 0) } catch (_: Throwable) {}
                    }
                }
            }
        }

        // Real-time synchronization to opponents in Firestore
        val roomId = state.activeRoomId
        val repo = firestoreRepository
        if (roomId != null && repo != null) {
            val myPawnsList = myPlayer.pawns.map { it.step.toLong() }
            viewModelScope.launch {
                try {
                    repo.broadcastPawnMove(
                        roomId = roomId,
                        color = activeColor,
                        pawnId = pawn.id,
                        fromStep = pawn.step,
                        toStep = targetStep,
                        pawnsList = myPawnsList,
                        capturedColor = capturedColor,
                        capturedPawnsList = capturedPawnsList,
                        nextTurnUserId = nextPlayer.id,
                        nextTurnColor = nextPlayer.color,
                        winnerUserId = if (hasWon) myPlayer.id else null,
                        potReward = state.stake.rewardPot,
                        notice = notice
                    )
                } catch (e: Exception) {
                    Log.w("ViewModel", "broadcastPawnMove network error: ${e.message}")
                }
            }
        } else {
            // Local bot progression
            if (!hasWon && nextPlayer.isBot) {
                triggerBotTurn(nextPlayer)
            }
        }
    }

    private fun passTurnLocal() {
        val players = _uiState.value.players
        if (players.isEmpty()) return
        val currentIdx = players.indexOfFirst { it.color == _uiState.value.currentTurnColor }
        val nextIdx = (currentIdx + 1) % players.size
        val nextPlayer = players[nextIdx]

        _uiState.update {
            it.copy(
                currentTurnColor = nextPlayer.color,
                currentTurnUserId = nextPlayer.id,
                diceValue = null,
                isDiceRolling = false,
                canRollDice = nextPlayer.isHuman,
                movablePawnIds = emptySet(),
                gameNotice = "${nextPlayer.name}'s turn."
            )
        }

        if (nextPlayer.isBot) {
            triggerBotTurn(nextPlayer)
        }
    }

    private fun triggerBotTurn(botPlayer: Player) {
        botTurnJob?.cancel()
        botTurnJob = viewModelScope.launch {
            delay(750)
            if (_uiState.value.winner != null) return@launch

            soundManager.playDiceRoll()
            _uiState.update { it.copy(isDiceRolling = true, canRollDice = false) }
            delay(500)

            val rolled = (1..6).random()
            val state = _uiState.value
            val currentBot = state.players.firstOrNull { it.color == botPlayer.color } ?: botPlayer
            val bestPawn = BotAiEngine.selectBestMove(currentBot, rolled, state.players)

            _uiState.update {
                it.copy(
                    diceValue = rolled,
                    isDiceRolling = false,
                    gameNotice = "${botPlayer.name} rolled $rolled 🎲"
                )
            }

            delay(650)
            if (bestPawn != null) {
                movePawn(bestPawn)
            } else {
                _uiState.update { it.copy(gameNotice = "${botPlayer.name} has no moves with $rolled.") }
                delay(900)
                passTurnLocal()
            }
        }
    }

    fun openGiftPicker(targetColor: PlayerColor) {
        _uiState.update { it.copy(giftPickerTarget = targetColor) }
    }

    fun closeGiftPicker() {
        _uiState.update { it.copy(giftPickerTarget = null) }
    }

    fun sendGift(giftType: GiftType, receiverColor: PlayerColor) {
        val currentProfile = _currentUserProfile.value
        val userCoins = currentProfile?.coins ?: 0L
        val userGems = currentProfile?.gems ?: 0L

        if (userCoins < giftType.coinCost || userGems < giftType.gemCost) {
            _uiState.update {
                it.copy(
                    giftPickerTarget = null,
                    isStoreDialogOpen = true,
                    gameNotice = "Not enough coins for ${giftType.title}! Visit the Store to get coins."
                )
            }
            return
        }

        // Deduct cost of gift
        _currentUserProfile.update {
            it?.copy(
                coins = (it.coins - giftType.coinCost).coerceAtLeast(0L),
                gems = (it.gems - giftType.gemCost).coerceAtLeast(0L)
            )
        }

        firestoreRepository?.let { repo ->
            viewModelScope.launch {
                try {
                    repo.deductCoinsAndGems(giftType.coinCost, giftType.gemCost)
                } catch (e: Exception) {
                    Log.w("ViewModel", "Firestore deduct error: ${e.message}")
                }
            }
        }

        soundManager.playGiftSent()

        val giftId = UUID.randomUUID().toString()
        val gift = ActiveGiftFly(
            id = giftId,
            giftType = giftType,
            senderColor = PlayerColor.RED,
            receiverColor = receiverColor,
            startOffset = Offset(0.2f, 0.8f),
            endOffset = when (receiverColor) {
                PlayerColor.GREEN -> Offset(0.2f, 0.2f)
                PlayerColor.YELLOW -> Offset(0.8f, 0.2f)
                PlayerColor.BLUE -> Offset(0.8f, 0.8f)
                PlayerColor.RED -> Offset(0.2f, 0.8f)
            }
        )

        _uiState.update {
            it.copy(
                activeGifts = it.activeGifts + gift,
                giftPickerTarget = null,
                gameNotice = "Sent ${giftType.iconText} ${giftType.title} to ${receiverColor.displayName} (-🪙${giftType.coinCost})!"
            )
        }

        viewModelScope.launch {
            delay(2400)
            _uiState.update { state ->
                state.copy(activeGifts = state.activeGifts.filterNot { it.id == giftId })
            }
        }
    }

    fun toggleCamera() {
        _uiState.update { state ->
            val newCameraState = !state.localCameraEnabled
            val updatedPlayers = state.players.map { p ->
                if (p.color == PlayerColor.RED) {
                    p.copy(isCameraOn = newCameraState)
                } else p
            }
            state.copy(
                localCameraEnabled = newCameraState,
                players = updatedPlayers,
                gameNotice = if (newCameraState) "Camera turned ON 🎥" else "Camera turned OFF 🚫"
            )
        }
    }

    fun toggleMicrophone() {
        _uiState.update { state ->
            val newMuteState = !state.localMicMuted
            val updatedPlayers = state.players.map { p ->
                if (p.color == PlayerColor.RED) {
                    p.copy(isMuted = newMuteState)
                } else p
            }
            state.copy(
                localMicMuted = newMuteState,
                players = updatedPlayers,
                gameNotice = if (newMuteState) "Microphone muted 🔇" else "Microphone unmuted 🎙️"
            )
        }
    }

    fun setOnlineDrawerOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isOnlineDrawerOpen = isOpen) }
    }

    fun setStoreDialogOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isStoreDialogOpen = isOpen) }
    }

    fun purchaseProduct(product: BillingProduct) {
        viewModelScope.launch {
            billingService.purchaseProduct(product) { coins, gems ->
                viewModelScope.launch {
                    val repo = firestoreRepository
                    if (repo != null) {
                        try {
                            repo.addCoinsAndGems(coins, gems)
                        } catch (_: Exception) {}
                    }
                    _currentUserProfile.update { current ->
                        current?.copy(
                            coins = current.coins + coins,
                            gems = current.gems + gems
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isStoreDialogOpen = false,
                            gameNotice = "Purchased ${product.title}! +$coins Coins added to balance."
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        roomSyncJob?.cancel()
        botTurnJob?.cancel()
        soundManager.release()
    }
}
