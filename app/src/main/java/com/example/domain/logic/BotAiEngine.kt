package com.example.domain.logic

import com.example.domain.model.Pawn
import com.example.domain.model.Player
import com.example.domain.model.PlayerColor

/**
 * Heuristic Bot Decision Engine for Ludo Live.
 * Evaluates tactical candidate moves based on:
 * - Opponent piece capture (+120 pts)
 * - Safe zone entrance (+65 pts)
 * - Exact home arrival (+100 pts)
 * - Unlocking piece from yard (+50 pts)
 * - Escaping from an approaching opponent piece (+45 pts)
 * - Avoiding moving into vulnerable enemy attack ranges (-40 pts)
 * - Forward momentum towards home (+0.5 * step pts)
 */
object BotAiEngine {

    data class ScoredMove(
        val pawn: Pawn,
        val targetStep: Int,
        val score: Float,
        val reason: String
    )

    fun selectBestMove(
        activePlayer: Player,
        diceRoll: Int,
        allPlayers: List<Player>
    ): Pawn? {
        val movablePawns = activePlayer.pawns.filter { LudoBoardRules.canPawnMove(it, diceRoll) }
        if (movablePawns.isEmpty()) return null
        if (movablePawns.size == 1) return movablePawns.first()

        val scoredMoves = movablePawns.map { pawn ->
            evaluateMove(pawn, diceRoll, activePlayer, allPlayers)
        }

        return scoredMoves.maxByOrNull { it.score }?.pawn
    }

    private fun evaluateMove(
        pawn: Pawn,
        diceRoll: Int,
        activePlayer: Player,
        allPlayers: List<Player>
    ): ScoredMove {
        val targetStep = LudoBoardRules.computeNewStep(pawn, diceRoll)
        var score = 10f
        var reason = "Progress"

        // 1. Unlocking from yard
        if (pawn.isYard && diceRoll == 6) {
            val startGlobalIndex = activePlayer.color.trackStartIndex
            val hasOpponentOnStart = allPlayers
                .filter { it.color != activePlayer.color && (activePlayer.team == 0 || it.team != activePlayer.team) }
                .any { opp ->
                    opp.pawns.any { oppPawn ->
                        LudoBoardRules.getGlobalTrackIndex(opp.color, oppPawn.step) == startGlobalIndex
                    }
                }
            score += 55f
            reason = "Unlock from Yard"
            if (hasOpponentOnStart) {
                score += 70f
                reason = "Unlock & Capture on Start!"
            }
            return ScoredMove(pawn, targetStep, score, reason)
        }

        // 2. Exact Home Arrival
        if (targetStep == LudoBoardRules.MAX_STEPS) {
            score += 110f
            reason = "Score Point into Home"
            return ScoredMove(pawn, targetStep, score, reason)
        }

        // Target tile metrics
        val targetGlobalTrack = LudoBoardRules.getGlobalTrackIndex(activePlayer.color, targetStep)
        val isTargetSafe = targetGlobalTrack != -1 && LudoBoardRules.isTrackIndexSafe(targetGlobalTrack)
        val isTargetInHomeRun = targetStep > LudoBoardRules.STEPS_TO_HOME_ENTRANCE

        // 3. Capturing opponent pieces
        if (targetGlobalTrack != -1 && !isTargetSafe) {
            val opponentCaptured = allPlayers
                .filter { it.color != activePlayer.color && (activePlayer.team == 0 || it.team != activePlayer.team) }
                .any { opp ->
                    opp.pawns.any { oppPawn ->
                        LudoBoardRules.getGlobalTrackIndex(opp.color, oppPawn.step) == targetGlobalTrack
                    }
                }
            if (opponentCaptured) {
                score += 125f
                reason = "Capture Opponent!"
            }
        }

        // 4. Reaching safe zone / star
        if (isTargetSafe) {
            score += 65f
            reason = "Secure in Safe Star"
        } else if (isTargetInHomeRun) {
            score += 50f
            reason = "Enter Protected Home Stretch"
        }

        // 5. Escaping vulnerability: Is current pawn currently in danger?
        val currentGlobalTrack = LudoBoardRules.getGlobalTrackIndex(activePlayer.color, pawn.step)
        if (currentGlobalTrack != -1 && !LudoBoardRules.isTrackIndexSafe(currentGlobalTrack)) {
            val isThreatened = isTileUnderThreat(currentGlobalTrack, activePlayer, allPlayers)
            if (isThreatened) {
                score += 45f
                reason = "Escape Danger"
            }
        }

        // 6. Vulnerability Penalty: Will landing here put this pawn in danger from behind?
        if (targetGlobalTrack != -1 && !isTargetSafe) {
            val willBeThreatened = isTileUnderThreat(targetGlobalTrack, activePlayer, allPlayers)
            if (willBeThreatened) {
                score -= 35f
            }
        }

        // 7. Favor advancing tokens closest to home
        score += targetStep * 0.4f

        return ScoredMove(pawn, targetStep, score, reason)
    }

    /**
     * Checks if any opponent is within 1..6 tiles behind the specified global track index.
     */
    private fun isTileUnderThreat(
        globalTrackIndex: Int,
        activePlayer: Player,
        allPlayers: List<Player>
    ): Boolean {
        val opponents = allPlayers.filter {
            it.color != activePlayer.color && (activePlayer.team == 0 || it.team != activePlayer.team)
        }

        for (opp in opponents) {
            for (oppPawn in opp.pawns) {
                val oppTrack = LudoBoardRules.getGlobalTrackIndex(opp.color, oppPawn.step)
                if (oppTrack != -1) {
                    val distance = (globalTrackIndex - oppTrack + LudoBoardRules.TOTAL_TRACK_TILES) % LudoBoardRules.TOTAL_TRACK_TILES
                    if (distance in 1..6) {
                        return true
                    }
                }
            }
        }
        return false
    }
}
