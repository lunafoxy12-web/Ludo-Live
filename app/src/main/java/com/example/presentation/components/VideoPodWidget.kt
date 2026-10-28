package com.example.presentation.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.domain.model.Player
import com.example.domain.model.PlayerColor

/**
 * Live Video & Social Pod widget representing an active player at one of the 4 corners of the board.
 * Supports:
 * - Real CameraX front camera preview for local player (Red)
 * - SFU WebRTC style live streaming video for peer players
 * - Animated audio frequency wavebars
 * - Active turn countdown progress ring & pulsing glow
 * - Direct tap to open in-game gifting sheet
 */
@Composable
fun VideoPodWidget(
    player: Player,
    isCurrentTurn: Boolean,
    timerSeconds: Int,
    isLocalPlayer: Boolean,
    onGiftClick: () -> Unit,
    isCameraOn: Boolean = player.isCameraOn,
    onToggleCamera: () -> Unit = {},
    onToggleMic: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) onToggleCamera()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pod_turn_glow")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val waveHeight by infiniteTransition.animateFloat(
        initialValue = 3f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_height"
    )

    val podShape = RoundedCornerShape(18.dp)
    val borderColor = if (isCurrentTurn) {
        player.color.primaryColor.copy(alpha = pulseGlow)
    } else {
        Color.White.copy(alpha = 0.15f)
    }

    val effectiveCameraActive = isLocalPlayer && isCameraOn && hasCameraPermission

    Box(
        modifier = modifier
            .width(108.dp)
            .height(118.dp)
            .shadow(if (isCurrentTurn) 14.dp else 4.dp, podShape)
            .clip(podShape)
            .background(Color(0xFF0F172A))
            .border(if (isCurrentTurn) 2.5.dp else 1.dp, borderColor, podShape)
            .then(
                if (!isLocalPlayer) Modifier.clickable { onGiftClick() } else Modifier
            )
            .testTag("video_pod_${player.color.name.lowercase()}")
    ) {
        // 1. Video Layer: CameraX preview if local and enabled, or animated SFU peer feed
        if (effectiveCameraActive) {
            CameraPreviewView(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(podShape)
            )
        } else {
            AnimatedPeerVideoView(
                player = player,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Turn Countdown Ring (Overlaid around video pod)
        if (isCurrentTurn) {
            val progress = (timerSeconds / 15f).coerceIn(0f, 1f)
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                color = player.color.primaryColor,
                trackColor = Color.White.copy(alpha = 0.12f),
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round
            )
        }

        // 3. Top Badges Row: SFU Indicator / Mic status / Gift button
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Player Color Tag
            Box(
                modifier = Modifier
                    .background(player.color.primaryColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = player.color.displayName.take(1),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Audio Spectrum Equalizer Wavebars
            if (!player.isMuted) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                ) {
                    Box(modifier = Modifier.width(2.dp).height(waveHeight.dp).background(Color(0xFF10B981), RoundedCornerShape(1.dp)))
                    Box(modifier = Modifier.width(2.dp).height((18f - waveHeight).dp).background(Color(0xFF34D399), RoundedCornerShape(1.dp)))
                    Box(modifier = Modifier.width(2.dp).height((waveHeight * 0.7f).dp).background(Color(0xFF10B981), RoundedCornerShape(1.dp)))
                }
            } else {
                Box(
                    modifier = Modifier
                        .background(Color.Red.copy(alpha = 0.8f), CircleShape)
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "Muted",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            // Gift Icon Button
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFB703))
                    .clickable { onGiftClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CardGiftcard,
                    contentDescription = "Send Gift",
                    tint = Color(0xFF1E1B4B),
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // 4. Local Controls Bar (If Local Player: Cam & Mic Toggles)
        if (isLocalPlayer) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (isCameraOn) {
                            onToggleCamera()
                        } else {
                            if (!hasCameraPermission) {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                onToggleCamera()
                            }
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("video_pod_camera_toggle")
                ) {
                    Icon(
                        imageVector = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = if (isCameraOn) "Turn Camera Off" else "Turn Camera On",
                        tint = if (isCameraOn) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onToggleMic,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("video_pod_mic_toggle")
                ) {
                    Icon(
                        imageVector = if (!player.isMuted) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (!player.isMuted) "Mute Microphone" else "Unmute Microphone",
                        tint = if (!player.isMuted) Color.White else Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 5. Bottom Overlay: Player Name & Score
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                        startY = 40f
                    )
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = player.name,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pts: ${player.score}",
                    color = Color(0xFFFFD166),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "🏠 ${player.finishedPawnCount}/4",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp
                )
            }
        }
    }
}

/**
 * CameraX Preview for the local player's front camera.
 */
@Composable
private fun CameraPreviewView(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        cameraProviderFuture.get().unbindAll()
                    } catch (_: Throwable) {}
                }, ContextCompat.getMainExecutor(context))
            } catch (_: Throwable) {}
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val cameraSelector = when {
                            cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                            cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                            else -> return@addListener
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                    } catch (_: Throwable) {}
                }, ContextCompat.getMainExecutor(ctx))
            } catch (_: Throwable) {}
            previewView
        },
        modifier = modifier
    )
}

/**
 * Simulated Live SFU Peer Video Stream avatar with realistic breathing animation
 * and dynamic background gradient.
 */
@Composable
private fun AnimatedPeerVideoView(player: Player, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "peer_stream")
    val avatarScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar_breath"
    )

    val avatarEmoji = when (player.color) {
        PlayerColor.RED -> "😎"
        PlayerColor.GREEN -> "👩‍🦰"
        PlayerColor.YELLOW -> "🧑‍💻"
        PlayerColor.BLUE -> "👩‍🎤"
    }

    Box(
        modifier = modifier
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        player.color.primaryColor.copy(alpha = 0.4f),
                        Color(0xFF0F172A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .scale(avatarScale)
                    .clip(CircleShape)
                    .background(player.color.primaryColor.copy(alpha = 0.25f))
                    .border(1.5.dp, player.color.primaryColor.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = avatarEmoji,
                    fontSize = 24.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = player.sfuStatus,
                color = Color(0xFF6EE7B7),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
