package com.example.domain.logic

import androidx.compose.ui.geometry.Offset
import com.example.domain.model.Pawn
import com.example.domain.model.PawnState
import com.example.domain.model.PlayerColor

/**
 * Coordinate and tile representation on the 15x15 standard Ludo grid.
 * (col: 0..14, row: 0..14)
 */
data class GridCoord(val col: Int, val row: Int)

object LudoBoardRules {

    const val TOTAL_TRACK_TILES = 52
    const val STEPS_TO_HOME_ENTRANCE = 51
    const val TOTAL_HOME_RUN_TILES = 5
    const val MAX_STEPS = 57 // 0 = Yard, 1 = Start, 57 = Home center

    // Global track coordinates (0..51) mapped on the 15x15 Ludo grid
    val TRACK_COORDINATES: List<GridCoord> = listOf(
        // Segment 1 (Left arm top, moving right towards center): 0..4
        GridCoord(1, 6), GridCoord(2, 6), GridCoord(3, 6), GridCoord(4, 6), GridCoord(5, 6),
        // Segment 2 (Top arm left, moving up): 5..10
        GridCoord(6, 5), GridCoord(6, 4), GridCoord(6, 3), GridCoord(6, 2), GridCoord(6, 1), GridCoord(6, 0),
        // Segment 3 (Top arm crossing): 11..12
        GridCoord(7, 0), GridCoord(8, 0),
        // Segment 4 (Top arm right, moving down): 13..17
        GridCoord(8, 1), GridCoord(8, 2), GridCoord(8, 3), GridCoord(8, 4), GridCoord(8, 5),
        // Segment 5 (Right arm top, moving right): 18..23
        GridCoord(9, 6), GridCoord(10, 6), GridCoord(11, 6), GridCoord(12, 6), GridCoord(13, 6), GridCoord(14, 6),
        // Segment 6 (Right arm crossing): 24..25
        GridCoord(14, 7), GridCoord(14, 8),
        // Segment 7 (Right arm bottom, moving left): 26..30
        GridCoord(13, 8), GridCoord(12, 8), GridCoord(11, 8), GridCoord(10, 8), GridCoord(9, 8),
        // Segment 8 (Bottom arm right, moving down): 31..36
        GridCoord(8, 9), GridCoord(8, 10), GridCoord(8, 11), GridCoord(8, 12), GridCoord(8, 13), GridCoord(8, 14),
        // Segment 9 (Bottom arm crossing): 37..38
        GridCoord(7, 14), GridCoord(6, 14),
        // Segment 10 (Bottom arm left, moving up): 39..43
        GridCoord(6, 13), GridCoord(6, 12), GridCoord(6, 11), GridCoord(6, 10), GridCoord(6, 9),
        // Segment 11 (Left arm bottom, moving left): 44..49
        GridCoord(5, 8), GridCoord(4, 8), GridCoord(3, 8), GridCoord(2, 8), GridCoord(1, 8), GridCoord(0, 8),
        // Segment 12 (Left arm crossing): 50..51
        GridCoord(0, 7), GridCoord(0, 6)
    )

    // The 8 safe tiles on the global 52-tile track
    val SAFE_TRACK_INDICES: Set<Int> = setOf(
        0,   // Red Start
        8,   // Star safe tile
        13,  // Green Start
        21,  // Star safe tile
        26,  // Yellow Start
        34,  // Star safe tile
        39,  // Blue Start
        47   // Star safe tile
    )

    // Home run paths (5 tiles leading into the center) for each color
    val HOME_RUN_COORDINATES: Map<PlayerColor, List<GridCoord>> = mapOf(
        PlayerColor.RED to listOf(
            GridCoord(1, 7), GridCoord(2, 7), GridCoord(3, 7), GridCoord(4, 7), GridCoord(5, 7)
        ),
        PlayerColor.GREEN to listOf(
            GridCoord(7, 1), GridCoord(7, 2), GridCoord(7, 3), GridCoord(7, 4), GridCoord(7, 5)
        ),
        PlayerColor.YELLOW to listOf(
            GridCoord(13, 7), GridCoord(12, 7), GridCoord(11, 7), GridCoord(10, 7), GridCoord(9, 7)
        ),
        PlayerColor.BLUE to listOf(
            GridCoord(7, 13), GridCoord(7, 12), GridCoord(7, 11), GridCoord(7, 10), GridCoord(9, 7) // (7,9)
        )
    )

    // Yard spawn coordinates for 4 pawns per color
    val YARD_COORDINATES: Map<PlayerColor, List<GridCoord>> = mapOf(
        PlayerColor.RED to listOf(GridCoord(1, 10), GridCoord(3, 10), GridCoord(1, 12), GridCoord(3, 12)),
        PlayerColor.GREEN to listOf(GridCoord(1, 1), GridCoord(3, 1), GridCoord(1, 3), GridCoord(3, 3)),
        PlayerColor.YELLOW to listOf(GridCoord(10, 1), GridCoord(12, 1), GridCoord(10, 3), GridCoord(12, 3)),
        PlayerColor.BLUE to listOf(GridCoord(10, 10), GridCoord(12, 10), GridCoord(10, 12), GridCoord(12, 12))
    )

    // Center Home Coordinates (Col 7, Row 7 center)
    val HOME_CENTER_COORDINATES: Map<PlayerColor, GridCoord> = mapOf(
        PlayerColor.RED to GridCoord(6, 7),
        PlayerColor.GREEN to GridCoord(7, 6),
        PlayerColor.YELLOW to GridCoord(8, 7),
        PlayerColor.BLUE to GridCoord(7, 8)
    )

    fun isTrackIndexSafe(trackIndex: Int): Boolean {
        return SAFE_TRACK_INDICES.contains(trackIndex)
    }

    /**
     * Converts a player's pawn step (0..57) to its global board grid coordinate.
     */
    fun getPawnGridCoord(pawn: Pawn): GridCoord {
        return when {
            pawn.step == 0 -> {
                val list = YARD_COORDINATES[pawn.color] ?: emptyList()
                list.getOrElse(pawn.id % list.size) { GridCoord(2, 2) }
            }
            pawn.step in 1..STEPS_TO_HOME_ENTRANCE -> {
                val globalIndex = (pawn.color.trackStartIndex + (pawn.step - 1)) % TOTAL_TRACK_TILES
                TRACK_COORDINATES[globalIndex]
            }
            pawn.step in 52..56 -> {
                val homeRun = HOME_RUN_COORDINATES[pawn.color] ?: emptyList()
                val index = pawn.step - 52
                homeRun.getOrElse(index) { GridCoord(7, 7) }
            }
            else -> {
                // Step 57: Finished in center
                HOME_CENTER_COORDINATES[pawn.color] ?: GridCoord(7, 7)
            }
        }
    }

    /**
     * Determines the global track index (0..51) for a pawn if it is on the main perimeter.
     * Returns -1 if the pawn is in Yard, in Home Run, or Finished.
     */
    fun getGlobalTrackIndex(color: PlayerColor, step: Int): Int {
        if (step !in 1..STEPS_TO_HOME_ENTRANCE) return -1
        return (color.trackStartIndex + (step - 1)) % TOTAL_TRACK_TILES
    }

    /**
     * Checks if a pawn can legally move with the given dice roll.
     */
    fun canPawnMove(pawn: Pawn, diceRoll: Int): Boolean {
        if (pawn.isHome) return false
        if (pawn.isYard) {
            return diceRoll == 6
        }
        return pawn.step + diceRoll <= MAX_STEPS
    }

    /**
     * Returns the simulated new step after moving.
     */
    fun computeNewStep(pawn: Pawn, diceRoll: Int): Int {
        if (pawn.isYard) {
            return if (diceRoll == 6) 1 else 0
        }
        val target = pawn.step + diceRoll
        return if (target <= MAX_STEPS) target else pawn.step
    }

    /**
     * Calculates the normalized (0.0 .. 1.0) pixel center coordinate for a 15x15 cell
     */
    fun gridToOffsetFraction(col: Int, row: Int): Offset {
        val cellSize = 1f / 15f
        val x = (col + 0.5f) * cellSize
        val y = (row + 0.5f) * cellSize
        return Offset(x, y)
    }
}
