package com.example.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.logic.GridCoord
import com.example.domain.logic.LudoBoardRules
import com.example.domain.model.Pawn
import com.example.domain.model.Player
import com.example.domain.model.PlayerColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-fidelity vector Ludo Board rendered on a precise 15x15 grid.
 * Features rounded track cells, safe stars, 4 colored yards with white inner bases,
 * 4 triangular home paths leading to a center golden crown, and animated tokens.
 */
@Composable
fun LudoBoardView(
    players: List<Player>,
    currentTurnColor: PlayerColor,
    movablePawnIds: Set<Int>,
    onPawnClick: (Pawn) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pawn_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pawn_scale"
    )

    val activePlayer = players.firstOrNull { it.color == currentTurnColor }
    val movablePawns = activePlayer?.pawns?.filter { it.id in movablePawnIds } ?: emptyList()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.0f)
            .shadow(16.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .testTag("ludo_board_canvas")
    ) {
        val boardSizeDp = maxWidth
        val cellSizeDp = boardSizeDp / 15f

        // Collect all pawns from all players
        val allPawns = remember(players) {
            players.flatMap { it.pawns }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(players, currentTurnColor, movablePawnIds) {
                    detectTapGestures { tapOffset ->
                        if (movablePawns.isNotEmpty()) {
                            val boardPx = size.width.toFloat()
                            val cellSize = boardPx / 15f
                            val tapCol = (tapOffset.x / cellSize).toInt().coerceIn(0, 14)
                            val tapRow = (tapOffset.y / cellSize).toInt().coerceIn(0, 14)

                            if (movablePawns.size == 1) {
                                onPawnClick(movablePawns.first())
                            } else {
                                // Check if tapped inside active player's Yard while yard pawns are movable
                                val isTappedInYard = when (currentTurnColor) {
                                    PlayerColor.RED -> tapCol in 0..5 && tapRow in 9..14
                                    PlayerColor.GREEN -> tapCol in 0..5 && tapRow in 0..5
                                    PlayerColor.YELLOW -> tapCol in 9..14 && tapRow in 0..5
                                    PlayerColor.BLUE -> tapCol in 9..14 && tapRow in 9..14
                                }
                                val yardPawns = movablePawns.filter { it.step == 0 }
                                if (isTappedInYard && yardPawns.isNotEmpty()) {
                                    onPawnClick(yardPawns.first())
                                } else {
                                    // Find closest movable pawn to tapOffset
                                    val closest = movablePawns.minByOrNull { pawn ->
                                        val coord = LudoBoardRules.getPawnGridCoord(pawn)
                                        val cx = (coord.col + 0.5f) * cellSize
                                        val cy = (coord.row + 0.5f) * cellSize
                                        val dx = tapOffset.x - cx
                                        val dy = tapOffset.y - cy
                                        dx * dx + dy * dy
                                    }
                                    if (closest != null) {
                                        val coord = LudoBoardRules.getPawnGridCoord(closest)
                                        val cx = (coord.col + 0.5f) * cellSize
                                        val cy = (coord.row + 0.5f) * cellSize
                                        val dist = kotlin.math.hypot(tapOffset.x - cx, tapOffset.y - cy)
                                        if (dist <= cellSize * 2.2f) {
                                            onPawnClick(closest)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            val boardSize = size.minDimension
            val cellSize = boardSize / 15f

            // 1. Draw outer base board background
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                ),
                size = size
            )

            // 2. Draw 4 Corner Yards
            drawLudoYards(cellSize)

            // 3. Draw Track Grid and Home Stretch Columns
            drawLudoTrack(cellSize)

            // 4. Draw Center Home Triangles
            drawCenterHomeTriangles(cellSize)

            // 5. Draw Safe Stars (8 safe spots)
            drawSafeStars(cellSize)

            // 6. Draw Pawns on Board
            drawAllPawns(
                pawns = allPawns,
                cellSize = cellSize,
                currentTurnColor = currentTurnColor,
                movablePawnIds = movablePawnIds,
                pulseScale = pulseScale
            )
        }

        // Accessible touch overlay targets centered on movable pawns
        if (activePlayer != null && movablePawnIds.isNotEmpty()) {
            for (pawn in activePlayer.pawns) {
                if (pawn.id in movablePawnIds) {
                    val coord = LudoBoardRules.getPawnGridCoord(pawn)
                    val targetSizeDp = maxOf(48.dp, cellSizeDp * 1.6f)
                    val centerXDp = cellSizeDp * (coord.col + 0.5f)
                    val centerYDp = cellSizeDp * (coord.row + 0.5f)
                    val leftDp = centerXDp - (targetSizeDp / 2)
                    val topDp = centerYDp - (targetSizeDp / 2)

                    Box(
                        modifier = Modifier
                            .size(targetSizeDp)
                            .offset(x = leftDp, y = topDp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onPawnClick(pawn)
                            }
                            .testTag("pawn_${pawn.color.name}_${pawn.id}")
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawLudoYards(cellSize: Float) {
    val yardSize = cellSize * 6f

    // Green Yard (Top-Left)
    drawYardBox(
        origin = Offset(0f, 0f),
        yardSize = yardSize,
        cellSize = cellSize,
        baseColor = PlayerColor.GREEN.primaryColor,
        darkColor = PlayerColor.GREEN.secondaryColor
    )

    // Yellow Yard (Top-Right)
    drawYardBox(
        origin = Offset(cellSize * 9f, 0f),
        yardSize = yardSize,
        cellSize = cellSize,
        baseColor = PlayerColor.YELLOW.primaryColor,
        darkColor = PlayerColor.YELLOW.secondaryColor
    )

    // Red Yard (Bottom-Left)
    drawYardBox(
        origin = Offset(0f, cellSize * 9f),
        yardSize = yardSize,
        cellSize = cellSize,
        baseColor = PlayerColor.RED.primaryColor,
        darkColor = PlayerColor.RED.secondaryColor
    )

    // Blue Yard (Bottom-Right)
    drawYardBox(
        origin = Offset(cellSize * 9f, cellSize * 9f),
        yardSize = yardSize,
        cellSize = cellSize,
        baseColor = PlayerColor.BLUE.primaryColor,
        darkColor = PlayerColor.BLUE.secondaryColor
    )
}

private fun DrawScope.drawYardBox(
    origin: Offset,
    yardSize: Float,
    cellSize: Float,
    baseColor: Color,
    darkColor: Color
) {
    // Outer colored zone
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(baseColor, darkColor),
            start = origin,
            end = Offset(origin.x + yardSize, origin.y + yardSize)
        ),
        topLeft = origin,
        size = Size(yardSize, yardSize)
    )

    // Inner rounded white pedestal base
    val innerMargin = cellSize * 0.9f
    val innerOrigin = Offset(origin.x + innerMargin, origin.y + innerMargin)
    val innerSize = yardSize - (innerMargin * 2f)

    drawRoundRect(
        color = Color(0xFFF8FAFC),
        topLeft = innerOrigin,
        size = Size(innerSize, innerSize),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellSize * 0.6f)
    )

    // 4 circular pawn holder slots
    val slotRadius = cellSize * 0.65f
    val centers = listOf(
        Offset(innerOrigin.x + innerSize * 0.28f, innerOrigin.y + innerSize * 0.28f),
        Offset(innerOrigin.x + innerSize * 0.72f, innerOrigin.y + innerSize * 0.28f),
        Offset(innerOrigin.x + innerSize * 0.28f, innerOrigin.y + innerSize * 0.72f),
        Offset(innerOrigin.x + innerSize * 0.72f, innerOrigin.y + innerSize * 0.72f)
    )

    for (c in centers) {
        drawCircle(
            color = baseColor.copy(alpha = 0.22f),
            radius = slotRadius,
            center = c
        )
        drawCircle(
            color = baseColor,
            radius = slotRadius * 0.75f,
            center = c,
            style = Stroke(width = 2.5f)
        )
    }
}

private fun DrawScope.drawLudoTrack(cellSize: Float) {
    val trackBorderColor = Color(0xFFE2E8F0)
    val trackBgColor = Color(0xFFF1F5F9)

    // Helper to paint individual cell
    fun paintCell(c: Int, r: Int, fillColor: Color = trackBgColor) {
        val cellOffset = Offset(c * cellSize, r * cellSize)
        drawRect(
            color = fillColor,
            topLeft = cellOffset,
            size = Size(cellSize, cellSize)
        )
        drawRect(
            color = trackBorderColor,
            topLeft = cellOffset,
            size = Size(cellSize, cellSize),
            style = Stroke(width = 1.2f)
        )
    }

    // 1. Paint general track cells
    for (coord in LudoBoardRules.TRACK_COORDINATES) {
        paintCell(coord.col, coord.row)
    }

    // 2. Start Tiles Colored
    paintCell(1, 6, PlayerColor.RED.primaryColor.copy(alpha = 0.85f))
    paintCell(8, 1, PlayerColor.GREEN.primaryColor.copy(alpha = 0.85f))
    paintCell(13, 8, PlayerColor.YELLOW.primaryColor.copy(alpha = 0.85f))
    paintCell(6, 13, PlayerColor.BLUE.primaryColor.copy(alpha = 0.85f))

    // 3. Home Stretch Columns (5 colored tiles leading to center for each color)
    // Red Home Stretch: Row 7, Cols 1..5
    for (col in 1..5) {
        paintCell(col, 7, PlayerColor.RED.primaryColor.copy(alpha = 0.9f))
    }
    // Green Home Stretch: Col 7, Rows 1..5
    for (row in 1..5) {
        paintCell(7, row, PlayerColor.GREEN.primaryColor.copy(alpha = 0.9f))
    }
    // Yellow Home Stretch: Row 7, Cols 9..13
    for (col in 9..13) {
        paintCell(col, 7, PlayerColor.YELLOW.primaryColor.copy(alpha = 0.9f))
    }
    // Blue Home Stretch: Col 7, Rows 9..13
    for (row in 9..13) {
        paintCell(7, row, PlayerColor.BLUE.primaryColor.copy(alpha = 0.9f))
    }
}

private fun DrawScope.drawCenterHomeTriangles(cellSize: Float) {
    val centerOrigin = Offset(cellSize * 6f, cellSize * 6f)
    val centerWidth = cellSize * 3f
    val centerPt = Offset(cellSize * 7.5f, cellSize * 7.5f)

    // Red Triangle (Left)
    val redPath = Path().apply {
        moveTo(centerOrigin.x, centerOrigin.y)
        lineTo(centerPt.x, centerPt.y)
        lineTo(centerOrigin.x, centerOrigin.y + centerWidth)
        close()
    }
    drawPath(redPath, Brush.linearGradient(listOf(PlayerColor.RED.primaryColor, PlayerColor.RED.secondaryColor)))

    // Green Triangle (Top)
    val greenPath = Path().apply {
        moveTo(centerOrigin.x, centerOrigin.y)
        lineTo(centerOrigin.x + centerWidth, centerOrigin.y)
        lineTo(centerPt.x, centerPt.y)
        close()
    }
    drawPath(greenPath, Brush.linearGradient(listOf(PlayerColor.GREEN.primaryColor, PlayerColor.GREEN.secondaryColor)))

    // Yellow Triangle (Right)
    val yellowPath = Path().apply {
        moveTo(centerOrigin.x + centerWidth, centerOrigin.y)
        lineTo(centerOrigin.x + centerWidth, centerOrigin.y + centerWidth)
        lineTo(centerPt.x, centerPt.y)
        close()
    }
    drawPath(yellowPath, Brush.linearGradient(listOf(PlayerColor.YELLOW.primaryColor, PlayerColor.YELLOW.secondaryColor)))

    // Blue Triangle (Bottom)
    val bluePath = Path().apply {
        moveTo(centerOrigin.x, centerOrigin.y + centerWidth)
        lineTo(centerPt.x, centerPt.y)
        lineTo(centerOrigin.x + centerWidth, centerOrigin.y + centerWidth)
        close()
    }
    drawPath(bluePath, Brush.linearGradient(listOf(PlayerColor.BLUE.primaryColor, PlayerColor.BLUE.secondaryColor)))

    // Center Gold Crown Emblem / Medallion
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFDF00), Color(0xFFD97706)),
            center = centerPt,
            radius = cellSize * 0.9f
        ),
        radius = cellSize * 0.85f,
        center = centerPt
    )
    drawCircle(
        color = Color(0xFFFFFFFF),
        radius = cellSize * 0.85f,
        center = centerPt,
        style = Stroke(width = 2.5f)
    )

    // Draw 4-point star in center medallion
    drawStarShape(center = centerPt, radius = cellSize * 0.45f, points = 4, color = Color.White)
}

private fun DrawScope.drawSafeStars(cellSize: Float) {
    val starIndices = listOf(0, 8, 13, 21, 26, 34, 39, 47)
    for (idx in starIndices) {
        val coord = LudoBoardRules.TRACK_COORDINATES[idx]
        val center = Offset((coord.col + 0.5f) * cellSize, (coord.row + 0.5f) * cellSize)
        drawStarShape(center = center, radius = cellSize * 0.35f, points = 5, color = Color(0xFFF59E0B))
    }
}

private fun DrawScope.drawStarShape(center: Offset, radius: Float, points: Int, color: Color) {
    val path = Path()
    val innerRadius = radius * 0.42f
    val stepAngle = PI / points

    for (i in 0 until (points * 2)) {
        val r = if (i % 2 == 0) radius else innerRadius
        val angle = i * stepAngle - (PI / 2.0)
        val x = (center.x + r * cos(angle)).toFloat()
        val y = (center.y + r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color, style = Fill)
}

private fun DrawScope.drawAllPawns(
    pawns: List<Pawn>,
    cellSize: Float,
    currentTurnColor: PlayerColor,
    movablePawnIds: Set<Int>,
    pulseScale: Float
) {
    // Group pawns by their current grid coordinate to handle overlapping pawns smoothly
    val pawnsByCoord = pawns.groupBy { LudoBoardRules.getPawnGridCoord(it) }

    pawnsByCoord.forEach { (coord, pawnList) ->
        val baseCenter = Offset((coord.col + 0.5f) * cellSize, (coord.row + 0.5f) * cellSize)

        pawnList.forEachIndexed { idx, pawn ->
            // If multiple pawns share tile, offset them slightly
            val offset = if (pawnList.size > 1) {
                val spread = cellSize * 0.2f
                when (idx % 4) {
                    0 -> Offset(-spread, -spread)
                    1 -> Offset(spread, -spread)
                    2 -> Offset(-spread, spread)
                    else -> Offset(spread, spread)
                }
            } else {
                Offset.Zero
            }

            val pawnCenter = baseCenter + offset
            val isMovable = pawn.color == currentTurnColor && pawn.id in movablePawnIds
            val radius = (cellSize * 0.38f) * (if (isMovable) pulseScale else 1.0f)

            // Pulsing glow ring if movable
            if (isMovable) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = radius * 1.55f,
                    center = pawnCenter
                )
                drawCircle(
                    color = Color(0xFFFFD700),
                    radius = radius * 1.35f,
                    center = pawnCenter,
                    style = Stroke(width = 3.5f)
                )
            }

            // Drop shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = radius * 0.95f,
                center = pawnCenter + Offset(2f, 3f)
            )

            // Token outer ring
            drawCircle(
                color = Color.White,
                radius = radius,
                center = pawnCenter
            )

            // Token body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(pawn.color.primaryColor, pawn.color.secondaryColor),
                    center = pawnCenter - Offset(radius * 0.2f, radius * 0.2f),
                    radius = radius * 0.9f
                ),
                radius = radius * 0.85f,
                center = pawnCenter
            )

            // Specular shiny highlight on pawn top
            drawCircle(
                color = Color.White.copy(alpha = 0.65f),
                radius = radius * 0.32f,
                center = pawnCenter - Offset(radius * 0.25f, radius * 0.25f)
            )
        }
    }
}
