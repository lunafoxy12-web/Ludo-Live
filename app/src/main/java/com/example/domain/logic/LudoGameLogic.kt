package com.example.domain.logic

import com.example.data.model.FirestoreGame
import com.example.data.model.LastMoveRecord
import com.example.data.model.PawnData
import com.example.data.model.PawnOccupant
import com.example.domain.model.PlayerColor

/**
 * Result of validating a proposed pawn move.
 */
data class MoveValidationResult(
    val isValid: Boolean,
    val reason: String = "",
    val targetStep: Int = -1,
    val isUnlocking: Boolean = false,
    val willCapture: Boolean = false,
    val capturedOpponentColor: String? = null,
    val capturedPawnId: Int? = null,
    val willReachHome: Boolean = false
)

/**
 * Result of executing a dice roll.
 */
data class DiceRollOutcome(
    val diceValue: Int,
    val isForfeitedThreeSixes: Boolean = false,
    val movablePawnIds: List<Int> = emptyList(),
    val hasLegalMoves: Boolean = false,
    val updatedGame: FirestoreGame
)

/**
 * Result of executing a legal pawn move.
 */
data class MoveExecutionOutcome(
    val updatedGame: FirestoreGame,
    val wasCaptured: Boolean,
    val capturedOpponentColor: String?,
    val capturedPawnId: Int?,
    val reachedHome: Boolean,
    val earnedBonusTurn: Boolean,
    val isGameOver: Boolean,
    val winnerColor: String?,
    val winnerUserId: String?
)

/**
 * Production-ready Ludo Game Logic engine for real-time Firestore synchronization.
 * Handles:
 * - Move validation & exact home-stretch rules
 * - Dice roll outcomes & 3-consecutive-sixes turn penalty
 * - Capturing opponent pieces on unsafe perimeter tiles
 * - Safe zones (starts and star tiles)
 * - Complete board occupancy recalculation
 * - Turn rotation and bonus turns
 */
class LudoGameLogic {

    companion object {
        const val MAX_STEPS = 57 // 0 = Yard, 1 = Start, 1..51 = Track, 52..56 = Home-stretch, 57 = Home
        const val TOTAL_TRACK_TILES = 52
        const val STEPS_TO_HOME_ENTRANCE = 51

        // 8 Safe perimeter tiles where tokens are immune to capture
        val SAFE_TRACK_TILES: Set<Int> = setOf(
            0,   // Red starting track tile
            8,   // Safe star
            13,  // Green starting track tile
            21,  // Safe star
            26,  // Yellow starting track tile
            34,  // Safe star
            39,  // Blue starting track tile
            47   // Safe star
        )

        fun isSafeTrackTile(trackIndex: Int): Boolean {
            return SAFE_TRACK_TILES.contains(trackIndex)
        }

        /**
         * Converts color and step to global 0..51 perimeter track index.
         * Returns -1 if pawn is in Yard, in Home-stretch, or Finished.
         */
        fun getGlobalTrackIndex(color: PlayerColor, step: Int): Int {
            if (step !in 1..STEPS_TO_HOME_ENTRANCE) return -1
            return (color.trackStartIndex + (step - 1)) % TOTAL_TRACK_TILES
        }
    }

    /**
     * Creates an initialized FirestoreGame with default 4x4 pawns in Yard and 0 occupancy.
     */
    fun createInitialGame(
        gameId: String,
        hostUserId: String,
        gameMode: String = "QUICK_1V1",
        stakeCoins: Long = 50L,
        playerUids: List<String> = listOf(hostUserId)
    ): FirestoreGame {
        val initialPawns = mutableMapOf<String, List<PawnData>>()
        for (color in listOf("RED", "GREEN", "YELLOW", "BLUE")) {
            initialPawns[color] = (0..3).map { id ->
                PawnData(pawnId = id, color = color, step = 0, state = "YARD")
            }
        }

        return FirestoreGame(
            gameId = gameId,
            hostUserId = hostUserId,
            status = if (playerUids.size >= 2) "IN_PROGRESS" else "WAITING",
            gameMode = gameMode,
            stakeCoins = stakeCoins,
            potCoins = stakeCoins * (if (gameMode == "QUICK_1V1") 2 else 4),
            playerUids = playerUids,
            currentTurnUserId = hostUserId,
            currentTurnColor = "RED",
            turnTimeoutSeconds = 15,
            consecutiveSixes = 0,
            diceRoll = null,
            diceRolled = false,
            boardOccupancy = emptyMap(),
            homeOccupancy = mapOf("RED" to 0, "GREEN" to 0, "YELLOW" to 0, "BLUE" to 0),
            pawns = initialPawns,
            winnerUserId = null,
            winnerColor = null
        )
    }

    /**
     * Validates whether a move can legally be made.
     */
    fun validateMove(
        game: FirestoreGame,
        color: PlayerColor,
        pawnId: Int,
        diceValue: Int
    ): MoveValidationResult {
        if (game.status != "IN_PROGRESS") {
            return MoveValidationResult(false, "Game is not in progress")
        }
        if (game.currentTurnColor != color.name) {
            return MoveValidationResult(false, "Not ${color.displayName}'s turn")
        }
        if (!game.diceRolled || game.diceRoll != diceValue) {
            return MoveValidationResult(false, "Dice must be rolled before moving")
        }

        val playerPawns = game.pawns[color.name]
            ?: return MoveValidationResult(false, "Player color not found in game")
        val pawn = playerPawns.firstOrNull { it.pawnId == pawnId }
            ?: return MoveValidationResult(false, "Pawn $pawnId not found")

        // 1. Pawn already in Home
        if (pawn.state == "HOME" || pawn.step >= MAX_STEPS) {
            return MoveValidationResult(false, "Pawn has already reached home")
        }

        // 2. Pawn in Yard
        if (pawn.state == "YARD" || pawn.step == 0) {
            return if (diceValue == 6) {
                MoveValidationResult(
                    isValid = true,
                    reason = "Unlock from Yard",
                    targetStep = 1,
                    isUnlocking = true
                )
            } else {
                MoveValidationResult(false, "Must roll a 6 to unlock token from Yard")
            }
        }

        // 3. Home-stretch & Over-roll check
        val targetStep = pawn.step + diceValue
        if (targetStep > MAX_STEPS) {
            return MoveValidationResult(
                isValid = false,
                reason = "Exact roll required to reach home (rolled $diceValue, needed ${MAX_STEPS - pawn.step})"
            )
        }

        val willReachHome = targetStep == MAX_STEPS

        // 4. Capture evaluation on target tile
        var willCapture = false
        var capturedColor: String? = null
        var capturedPawnId: Int? = null

        val targetTrack = getGlobalTrackIndex(color, targetStep)
        if (targetTrack != -1 && !isSafeTrackTile(targetTrack)) {
            // Check all opponent pawns
            for ((oppColorStr, oppPawns) in game.pawns) {
                if (oppColorStr == color.name) continue
                val oppColor = PlayerColor.entries.firstOrNull { it.name == oppColorStr } ?: continue

                for (oppPawn in oppPawns) {
                    val oppTrack = getGlobalTrackIndex(oppColor, oppPawn.step)
                    if (oppTrack == targetTrack) {
                        willCapture = true
                        capturedColor = oppColorStr
                        capturedPawnId = oppPawn.pawnId
                        break
                    }
                }
                if (willCapture) break
            }
        }

        return MoveValidationResult(
            isValid = true,
            reason = "Valid Move",
            targetStep = targetStep,
            willCapture = willCapture,
            capturedOpponentColor = capturedColor,
            capturedPawnId = capturedPawnId,
            willReachHome = willReachHome
        )
    }

    /**
     * Processes a dice roll for the active player.
     * Enforces the 3 consecutive sixes penalty rule and calculates legal moves.
     */
    fun processDiceRoll(
        game: FirestoreGame,
        diceValue: Int
    ): DiceRollOutcome {
        require(diceValue in 1..6) { "Dice value must be between 1 and 6" }

        val activeColor = PlayerColor.entries.firstOrNull { it.name == game.currentTurnColor } ?: PlayerColor.RED
        val activePawns = game.pawns[activeColor.name] ?: emptyList()

        val newSixCount = if (diceValue == 6) game.consecutiveSixes + 1 else 0

        // 3 consecutive sixes penalty: turn forfeited!
        if (newSixCount >= 3) {
            val (nextUserId, nextColor) = getNextTurnPlayer(game)
            val updated = game.copy(
                consecutiveSixes = 0,
                diceRoll = diceValue,
                diceRolled = false,
                currentTurnUserId = nextUserId,
                currentTurnColor = nextColor.name
            )
            return DiceRollOutcome(
                diceValue = diceValue,
                isForfeitedThreeSixes = true,
                movablePawnIds = emptyList(),
                hasLegalMoves = false,
                updatedGame = updated
            )
        }

        // Evaluate which pawns can move
        val mockGameWithDice = game.copy(diceRoll = diceValue, diceRolled = true, consecutiveSixes = newSixCount)
        val movableIds = activePawns
            .filter { validateMove(mockGameWithDice, activeColor, it.pawnId, diceValue).isValid }
            .map { it.pawnId }

        val hasLegalMoves = movableIds.isNotEmpty()

        val updatedGame = if (!hasLegalMoves) {
            // No legal moves: pass turn to next player
            val (nextUserId, nextColor) = getNextTurnPlayer(game)
            mockGameWithDice.copy(
                diceRolled = false,
                currentTurnUserId = nextUserId,
                currentTurnColor = nextColor.name,
                consecutiveSixes = 0
            )
        } else {
            mockGameWithDice
        }

        return DiceRollOutcome(
            diceValue = diceValue,
            isForfeitedThreeSixes = false,
            movablePawnIds = movableIds,
            hasLegalMoves = hasLegalMoves,
            updatedGame = updatedGame
        )
    }

    /**
     * Executes a validated pawn move, captures opponents if applicable,
     * updates board occupancy, and determines bonus turn or next turn rotation.
     */
    fun executeMove(
        game: FirestoreGame,
        color: PlayerColor,
        pawnId: Int
    ): MoveExecutionOutcome {
        val diceRoll = game.diceRoll ?: throw IllegalStateException("Cannot execute move without rolled dice")
        val validation = validateMove(game, color, pawnId, diceRoll)
        require(validation.isValid) { "Illegal move: ${validation.reason}" }

        val targetStep = validation.targetStep
        val targetState = when {
            targetStep == 0 -> "YARD"
            targetStep in 1..STEPS_TO_HOME_ENTRANCE -> "TRACK"
            targetStep in 52..56 -> "HOME_STRETCH"
            else -> "HOME"
        }

        // 1. Update the moving pawn
        val updatedPawns = game.pawns.toMutableMap()
        val playerPawns = (updatedPawns[color.name] ?: emptyList()).map {
            if (it.pawnId == pawnId) {
                it.copy(step = targetStep, state = targetState)
            } else {
                it
            }
        }
        updatedPawns[color.name] = playerPawns

        // 2. Handle Opponent Capture: Send captured opponent pawn back to YARD
        var wasCaptured = false
        var capturedColorStr: String? = null
        var capturedPawnId: Int? = null

        if (validation.willCapture && validation.capturedOpponentColor != null && validation.capturedPawnId != null) {
            wasCaptured = true
            capturedColorStr = validation.capturedOpponentColor
            capturedPawnId = validation.capturedPawnId

            val opponentList = updatedPawns[capturedColorStr] ?: emptyList()
            updatedPawns[capturedColorStr] = opponentList.map { opp ->
                if (opp.pawnId == capturedPawnId) {
                    opp.copy(step = 0, state = "YARD")
                } else {
                    opp
                }
            }
        }

        // 3. Recalculate Board Occupancy and Home Occupancy
        val updatedBoardOccupancy = computeBoardOccupancy(updatedPawns, game.players)
        val updatedHomeOccupancy = computeHomeOccupancy(updatedPawns)

        // 4. Check Victory: All 4 pawns of active player reached HOME
        val finishedCount = playerPawns.count { it.step == MAX_STEPS }
        val isGameOver = finishedCount == 4
        val winnerColor = if (isGameOver) color.name else null
        val winnerUserId = if (isGameOver) game.currentTurnUserId else null

        // 5. Bonus Turn Logic:
        // Rolling a 6, capturing an opponent, or reaching home grants another roll!
        val earnedBonusTurn = !isGameOver && (diceRoll == 6 || wasCaptured || validation.willReachHome)

        val nextTurnUserId: String
        val nextTurnColor: PlayerColor

        if (earnedBonusTurn) {
            nextTurnUserId = game.currentTurnUserId
            nextTurnColor = color
        } else {
            val (nextUid, nextC) = getNextTurnPlayer(game)
            nextTurnUserId = nextUid
            nextTurnColor = nextC
        }

        val lastMoveRecord = LastMoveRecord(
            pawnId = pawnId,
            color = color.name,
            fromStep = (game.pawns[color.name]?.firstOrNull { it.pawnId == pawnId }?.step ?: 0),
            toStep = targetStep,
            capturedOpponentColor = capturedColorStr,
            capturedPawnId = capturedPawnId
        )

        val finalGame = game.copy(
            status = if (isGameOver) "COMPLETED" else "IN_PROGRESS",
            currentTurnUserId = nextTurnUserId,
            currentTurnColor = nextTurnColor.name,
            diceRoll = null,
            diceRolled = false,
            boardOccupancy = updatedBoardOccupancy,
            homeOccupancy = updatedHomeOccupancy,
            pawns = updatedPawns,
            lastMove = lastMoveRecord,
            winnerUserId = winnerUserId,
            winnerColor = winnerColor
        )

        return MoveExecutionOutcome(
            updatedGame = finalGame,
            wasCaptured = wasCaptured,
            capturedOpponentColor = capturedColorStr,
            capturedPawnId = capturedPawnId,
            reachedHome = validation.willReachHome,
            earnedBonusTurn = earnedBonusTurn,
            isGameOver = isGameOver,
            winnerColor = winnerColor,
            winnerUserId = winnerUserId
        )
    }

    /**
     * Computes the 0..51 perimeter track occupancy map.
     */
    fun computeBoardOccupancy(
        pawnsMap: Map<String, List<PawnData>>,
        playersMap: Map<String, com.example.data.model.PlayerMetadata>
    ): Map<String, List<PawnOccupant>> {
        val occupancy = mutableMapOf<String, MutableList<PawnOccupant>>()

        for ((colorStr, pawns) in pawnsMap) {
            val color = PlayerColor.entries.firstOrNull { it.name == colorStr } ?: continue
            val uid = playersMap.values.firstOrNull { it.color == colorStr }?.uid ?: ""

            for (pawn in pawns) {
                if (pawn.step in 1..STEPS_TO_HOME_ENTRANCE) {
                    val trackIndex = getGlobalTrackIndex(color, pawn.step)
                    val key = trackIndex.toString()
                    val list = occupancy.getOrPut(key) { mutableListOf() }
                    list.add(PawnOccupant(color = colorStr, pawnId = pawn.pawnId, uid = uid))
                }
            }
        }
        return occupancy
    }

    /**
     * Computes count of finished pawns in home (0..4) for each color.
     */
    fun computeHomeOccupancy(pawnsMap: Map<String, List<PawnData>>): Map<String, Int> {
        return pawnsMap.mapValues { (_, pawns) ->
            pawns.count { it.step == MAX_STEPS || it.state == "HOME" }
        }
    }

    /**
     * Rotates turn to the next player.
     */
    fun getNextTurnPlayer(game: FirestoreGame): Pair<String, PlayerColor> {
        val currentColor = PlayerColor.entries.firstOrNull { it.name == game.currentTurnColor } ?: PlayerColor.RED
        val activeColors = when (game.gameMode) {
            "QUICK_1V1" -> listOf(PlayerColor.RED, PlayerColor.YELLOW)
            else -> listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE)
        }

        val currentIndex = activeColors.indexOf(currentColor)
        val nextIndex = if (currentIndex != -1) (currentIndex + 1) % activeColors.size else 0
        val nextColor = activeColors[nextIndex]

        val nextUserId = game.players.values.firstOrNull { it.color == nextColor.name }?.uid
            ?: game.playerUids.getOrNull(nextIndex)
            ?: game.hostUserId

        return Pair(nextUserId, nextColor)
    }
}
