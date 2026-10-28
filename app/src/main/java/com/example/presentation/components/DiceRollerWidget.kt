package com.example.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.PlayerColor

/**
 * 3D-styled interactive Dice Roller with physics-feel rotation, pips rendering,
 * and responsive roll trigger button.
 */
@Composable
fun DiceRollerWidget(
    diceValue: Int?,
    isRolling: Boolean,
    canRoll: Boolean,
    turnColor: PlayerColor,
    onRollClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isRolling) {
        if (isRolling) {
            rotation.animateTo(
                targetValue = rotation.value + 720f,
                animationSpec = tween(550, easing = LinearEasing)
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "dice_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dice_scale"
    )

    Column(
        modifier = modifier.testTag("dice_roller_container"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 3D Rounded Dice Cube
        Box(
            modifier = Modifier
                .size(68.dp)
                .scale(if (canRoll && !isRolling) pulseScale else 1f)
                .rotate(rotation.value)
                .shadow(12.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9), Color(0xFFCBD5E1)),
                        radius = 120f
                    )
                )
                .border(2.5.dp, turnColor.primaryColor, RoundedCornerShape(16.dp))
                .clickable(enabled = canRoll && !isRolling) { onRollClick() }
                .testTag("dice_cube_button"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                drawDiceFace(
                    value = diceValue ?: 6,
                    pipColor = turnColor.primaryColor,
                    isRolling = isRolling
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Call to action button / Turn badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (canRoll) turnColor.primaryColor else Color(0xFF334155)
                )
                .clickable(enabled = canRoll && !isRolling) { onRollClick() }
                .padding(horizontal = 14.dp, vertical = 5.dp)
                .testTag("roll_cta_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    isRolling -> "Rolling..."
                    canRoll -> "TAP TO ROLL 🎲"
                    diceValue != null -> "Rolled: $diceValue"
                    else -> "${turnColor.displayName}'s Turn"
                },
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun DrawScope.drawDiceFace(value: Int, pipColor: Color, isRolling: Boolean) {
    val pipRadius = size.minDimension * 0.11f
    val center = Offset(size.width / 2f, size.height / 2f)
    val left = size.width * 0.25f
    val right = size.width * 0.75f
    val top = size.height * 0.25f
    val bottom = size.height * 0.75f

    fun drawPip(x: Float, y: Float) {
        // Pip drop shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.25f),
            radius = pipRadius,
            center = Offset(x + 1f, y + 1.5f)
        )
        // Pip inner fill
        drawCircle(
            color = if (isRolling) Color.Gray else pipColor,
            radius = pipRadius,
            center = Offset(x, y)
        )
        // Pip specular white glint
        drawCircle(
            color = Color.White.copy(alpha = 0.6f),
            radius = pipRadius * 0.35f,
            center = Offset(x - pipRadius * 0.25f, y - pipRadius * 0.25f)
        )
    }

    val displayValue = if (isRolling) (1..6).random() else value

    when (displayValue) {
        1 -> {
            drawPip(center.x, center.y)
        }
        2 -> {
            drawPip(left, top)
            drawPip(right, bottom)
        }
        3 -> {
            drawPip(left, top)
            drawPip(center.x, center.y)
            drawPip(right, bottom)
        }
        4 -> {
            drawPip(left, top)
            drawPip(right, top)
            drawPip(left, bottom)
            drawPip(right, bottom)
        }
        5 -> {
            drawPip(left, top)
            drawPip(right, top)
            drawPip(center.x, center.y)
            drawPip(left, bottom)
            drawPip(right, bottom)
        }
        6 -> {
            drawPip(left, top)
            drawPip(right, top)
            drawPip(left, center.y)
            drawPip(right, center.y)
            drawPip(left, bottom)
            drawPip(right, bottom)
        }
    }
}
