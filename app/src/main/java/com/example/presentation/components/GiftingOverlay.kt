package com.example.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.ActiveGiftFly
import com.example.domain.model.GiftType
import com.example.domain.model.PlayerColor
import kotlin.math.sin

/**
 * Animated floating gift layer that renders micro-interactions flying smoothly
 * across the screen from sender pod to receiver pod.
 */
@Composable
fun GiftingOverlay(
    activeGifts: List<ActiveGiftFly>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        for (gift in activeGifts) {
            val progressAnim = remember(gift.id) { Animatable(0f) }
            val scaleAnim = remember(gift.id) { Animatable(0.5f) }

            LaunchedEffect(gift.id) {
                scaleAnim.animateTo(1.3f, tween(300, easing = FastOutSlowInEasing))
                progressAnim.animateTo(1.0f, tween(1600, easing = LinearEasing))
                scaleAnim.animateTo(1.8f, tween(300, easing = FastOutSlowInEasing))
            }

            val t = progressAnim.value
            // Parabolic arc interpolation
            val currentX = (1 - t) * (gift.startOffset.x * widthPx) + t * (gift.endOffset.x * widthPx)
            val currentY = (1 - t) * (gift.startOffset.y * heightPx) + t * (gift.endOffset.y * heightPx) - (sin(t * Math.PI) * 120f).toFloat()

            Box(
                modifier = Modifier
                    .offset { IntOffset(currentX.toInt(), currentY.toInt()) }
                    .scale(scaleAnim.value)
                    .size(54.dp)
                    .shadow(12.dp, CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFFD166), Color(0xFFEF4444)),
                            radius = 60f
                        ),
                        CircleShape
                    )
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = gift.giftType.iconText,
                    fontSize = 28.sp
                )
            }
        }
    }
}

/**
 * Social Gifting Selection Modal.
 */
@Composable
fun GiftPickerDialog(
    targetColor: PlayerColor,
    userCoins: Long,
    userGems: Long,
    onSendGift: (GiftType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("gift_picker_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Send Gift to ${targetColor.displayName}",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Balance: 🪙 $userCoins | 💎 $userGems",
                            color = Color(0xFFFFD166),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(GiftType.entries.toTypedArray()) { gift ->
                        val canAfford = if (gift.gemCost > 0) userGems >= gift.gemCost else userCoins >= gift.coinCost

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF2E1065))
                                .border(
                                    1.dp,
                                    if (canAfford) Color(0xFFFFB703) else Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable(enabled = canAfford) {
                                    onSendGift(gift)
                                }
                                .padding(vertical = 12.dp, horizontal = 6.dp)
                                .testTag("gift_item_${gift.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = gift.iconText, fontSize = 28.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = gift.title,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (gift.gemCost > 0) "💎 ${gift.gemCost}" else "🪙 ${gift.coinCost}",
                                    color = if (canAfford) Color(0xFFFFD166) else Color.Red.copy(alpha = 0.8f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
