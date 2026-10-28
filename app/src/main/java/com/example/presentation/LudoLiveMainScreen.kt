package com.example.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.GameMode
import com.example.domain.model.PlayerColor
import com.example.domain.model.TableStake
import com.example.presentation.components.AuthScreen
import com.example.presentation.components.DiceRollerWidget
import com.example.presentation.components.GiftPickerDialog
import com.example.presentation.components.GiftingOverlay
import com.example.presentation.components.LudoBoardView
import com.example.presentation.components.MatchmakingScreen
import com.example.presentation.components.OnlineChallengeBottomSheet
import com.example.presentation.components.OnlineUsersBottomBar
import com.example.presentation.components.StoreDialog
import com.example.presentation.components.VictoryDialog
import com.example.presentation.components.VideoPodWidget
import com.example.presentation.viewmodel.LudoGameViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun LudoLiveMainScreen(
    viewModel: LudoGameViewModel,
    modifier: Modifier = Modifier
) {
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val realOnlineUsers by viewModel.realOnlineUsers.collectAsState()
    val incomingChallenges by viewModel.incomingChallenges.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var isModePickerOpen by remember { mutableStateOf(false) }

    val isUserSignedIn = try {
        Firebase.auth.currentUser != null
    } catch (_: Throwable) {
        false
    }

    // If user is not signed in, gate behind Google Sign-In with 50 Free Coins Offer, or allow Offline Play
    if (currentUserProfile == null && !isUserSignedIn) {
        AuthScreen(
            onAuthSuccess = { user ->
                viewModel.onUserAuthenticated(user)
            },
            onPlayGuest = { name ->
                viewModel.playAsGuest(name)
            },
            onPlayOffline = {
                viewModel.playOffline()
            }
        )
        return
    }

    val userCoins = currentUserProfile?.coins ?: 50L
    val userGems = currentUserProfile?.gems ?: 10L

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0B091A),
        bottomBar = {
            OnlineUsersBottomBar(
                realOnlineUsers = realOnlineUsers,
                incomingChallenges = incomingChallenges,
                onOpenDrawer = { viewModel.setOnlineDrawerOpen(true) },
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F0C29), Color(0xFF1B143F), Color(0xFF0D0B21))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Top Bar: Real Coins (50 start), Gems, Store, Audio Toggle & Mode
                TopHeaderBar(
                    coins = userCoins,
                    gems = userGems,
                    isSoundOn = viewModel.soundManager.isSoundEnabled,
                    isCameraOn = uiState.localCameraEnabled,
                    gameMode = uiState.gameMode,
                    stake = uiState.stake,
                    onOpenStore = { viewModel.setStoreDialogOpen(true) },
                    onToggleSound = {
                        viewModel.soundManager.isSoundEnabled = !viewModel.soundManager.isSoundEnabled
                        viewModel.soundManager.isHapticsEnabled = viewModel.soundManager.isSoundEnabled
                    },
                    onToggleCamera = { viewModel.toggleCamera() },
                    onOpenModePicker = { isModePickerOpen = true }
                )

                // 2. Active Game Notice Banner (Live synchronizing indicator)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1B4B).copy(alpha = 0.85f))
                        .border(1.dp, Color(0xFFFFB703).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (uiState.isMultiplayerSynced) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = uiState.gameNotice,
                            color = Color(0xFFFFD166),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Central Stage: 4 Video Pods positioned around the Ludo Board
                val players = uiState.players
                val greenPlayer = players.firstOrNull { it.color == PlayerColor.GREEN }
                val yellowPlayer = players.firstOrNull { it.color == PlayerColor.YELLOW }
                val redPlayer = players.firstOrNull { it.color == PlayerColor.RED }
                val bluePlayer = players.firstOrNull { it.color == PlayerColor.BLUE }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Video Pods Row (Green & Yellow)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        greenPlayer?.let { player ->
                            VideoPodWidget(
                                player = player,
                                isCurrentTurn = uiState.currentTurnColor == player.color,
                                timerSeconds = uiState.timerSeconds,
                                isLocalPlayer = false,
                                onGiftClick = { viewModel.openGiftPicker(player.color) }
                            )
                        } ?: Spacer(modifier = Modifier.width(108.dp))

                        // Center Top Quick Status Pill
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2E1065), RoundedCornerShape(14.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Pot: 🪙 ${uiState.stake.rewardPot}",
                                color = Color(0xFFFFD166),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        yellowPlayer?.let { player ->
                            VideoPodWidget(
                                player = player,
                                isCurrentTurn = uiState.currentTurnColor == player.color,
                                timerSeconds = uiState.timerSeconds,
                                isLocalPlayer = false,
                                onGiftClick = { viewModel.openGiftPicker(player.color) }
                            )
                        } ?: Spacer(modifier = Modifier.width(108.dp))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Center Ludo Board Canvas
                    LudoBoardView(
                        players = uiState.players,
                        currentTurnColor = uiState.currentTurnColor,
                        movablePawnIds = uiState.movablePawnIds,
                        onPawnClick = { viewModel.onPawnClicked(it) },
                        modifier = Modifier
                            .fillMaxWidth(0.96f)
                            .padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bottom Video Pods Row (Red Local & Blue) + Dice Roller in Center
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        redPlayer?.let { player ->
                            VideoPodWidget(
                                player = player,
                                isCurrentTurn = uiState.currentTurnColor == player.color,
                                timerSeconds = uiState.timerSeconds,
                                isLocalPlayer = true,
                                onGiftClick = { viewModel.openGiftPicker(player.color) },
                                isCameraOn = uiState.localCameraEnabled,
                                onToggleCamera = { viewModel.toggleCamera() },
                                onToggleMic = { viewModel.toggleMicrophone() }
                            )
                        } ?: Spacer(modifier = Modifier.width(108.dp))

                        // Dynamic Dice Roller Center Controller
                        DiceRollerWidget(
                            diceValue = uiState.diceValue,
                            isRolling = uiState.isDiceRolling,
                            canRoll = uiState.canRollDice,
                            turnColor = uiState.currentTurnColor,
                            onRollClick = { viewModel.rollDice() },
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        bluePlayer?.let { player ->
                            VideoPodWidget(
                                player = player,
                                isCurrentTurn = uiState.currentTurnColor == player.color,
                                timerSeconds = uiState.timerSeconds,
                                isLocalPlayer = false,
                                onGiftClick = { viewModel.openGiftPicker(player.color) }
                            )
                        } ?: Spacer(modifier = Modifier.width(108.dp))
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            // 4. Parabolic Animated Gifting Layer
            GiftingOverlay(activeGifts = uiState.activeGifts)

            // 5. Matchmaking Radar Screen
            if (uiState.isMatchmaking) {
                MatchmakingScreen(
                    gameMode = uiState.gameMode,
                    stake = uiState.stake
                )
            }

            // 6. Victory Celebration Dialog
            uiState.winner?.let { winner ->
                VictoryDialog(
                    winner = winner,
                    stake = uiState.stake,
                    onPlayAgain = {
                        if (uiState.activeRoomId != null) {
                            viewModel.createMultiplayerRoom(
                                mode = uiState.gameMode,
                                stake = uiState.stake
                            )
                        } else {
                            viewModel.playOffline(uiState.gameMode, uiState.stake)
                        }
                    }
                )
            }

            // 7. Social Gifting Picker Sheet
            uiState.giftPickerTarget?.let { target ->
                GiftPickerDialog(
                    targetColor = target,
                    userCoins = userCoins,
                    userGems = userGems,
                    onSendGift = { gift -> viewModel.sendGift(gift, target) },
                    onDismiss = { viewModel.closeGiftPicker() }
                )
            }

            // 8. In-App Google Play Billing Store Dialog
            if (uiState.isStoreDialogOpen) {
                StoreDialog(
                    billingService = viewModel.billingService,
                    onPurchaseProduct = { product -> viewModel.purchaseProduct(product) },
                    onDismiss = { viewModel.setStoreDialogOpen(false) }
                )
            }

            // 9. Real Online Players Bottom Sheet
            if (uiState.isOnlineDrawerOpen) {
                OnlineChallengeBottomSheet(
                    realOnlineUsers = realOnlineUsers,
                    incomingChallenges = incomingChallenges,
                    availableStakes = viewModel.availableStakes,
                    onAcceptChallenge = { challenge ->
                        viewModel.setOnlineDrawerOpen(false)
                        viewModel.acceptIncomingChallenge(challenge)
                    },
                    onDeclineChallenge = { challenge ->
                        viewModel.declineIncomingChallenge(challenge)
                    },
                    onSendChallenge = { targetUser, mode, stake ->
                        viewModel.challengeUser(targetUser, mode, stake)
                    },
                    onDismiss = { viewModel.setOnlineDrawerOpen(false) }
                )
            }

            // 10. Mode & Table Stake Picker Dialog
            if (isModePickerOpen) {
                ModeStakeSelectorDialog(
                    availableStakes = viewModel.availableStakes,
                    onDismiss = { isModePickerOpen = false },
                    onSelect = { mode, stake ->
                        isModePickerOpen = false
                        if (uiState.activeRoomId == null && (currentUserProfile == null || currentUserProfile?.id == "local_player")) {
                            viewModel.playOffline(mode, stake)
                        } else {
                            viewModel.createMultiplayerRoom(mode, stake)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TopHeaderBar(
    coins: Long,
    gems: Long,
    isSoundOn: Boolean,
    isCameraOn: Boolean,
    gameMode: GameMode,
    stake: TableStake,
    onOpenStore: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleCamera: () -> Unit,
    onOpenModePicker: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Balance Pills
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Coins Pill (Real 50 free coins for new accounts!)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E1B4B))
                    .border(1.dp, Color(0xFFFFB703), RoundedCornerShape(20.dp))
                    .clickable { onOpenStore() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🪙", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$coins",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color(0xFF10B981), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Buy Coins",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            // Gems Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E1B4B))
                    .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(20.dp))
                    .clickable { onOpenStore() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "💎", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$gems",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Action Icons: Mode Selector, Camera Toggle, Sound Toggle, Store
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Mode Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2E1065))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .clickable { onOpenModePicker() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${gameMode.label} ▾",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(
                onClick = onToggleCamera,
                modifier = Modifier.size(32.dp).testTag("top_bar_camera_button")
            ) {
                Icon(
                    imageVector = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    contentDescription = if (isCameraOn) "Turn Camera Off" else "Turn Camera On",
                    tint = if (isCameraOn) Color(0xFF10B981) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onToggleSound,
                modifier = Modifier.size(32.dp).testTag("top_bar_sound_button")
            ) {
                Icon(
                    imageVector = if (isSoundOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                    contentDescription = "Toggle Audio",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onOpenStore,
                modifier = Modifier.size(32.dp).testTag("top_bar_store_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = "Store",
                    tint = Color(0xFFFFD166),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeStakeSelectorDialog(
    availableStakes: List<TableStake>,
    onDismiss: () -> Unit,
    onSelect: (GameMode, TableStake) -> Unit
) {
    var selectedMode by remember { mutableStateOf(GameMode.QUICK_1V1) }
    var selectedStake by remember { mutableStateOf(availableStakes.first()) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Card(
            shape = RoundedCornerShape(24.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(16.dp),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Multiplayer Room",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Game Modes
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(GameMode.QUICK_1V1, GameMode.CLASSIC_4P).forEach { mode ->
                        val isSel = selectedMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) Color(0xFF3B82F6) else Color(0xFF2E1065))
                                .border(1.dp, if (isSel) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { selectedMode = mode }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = mode.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(text = mode.description, color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }
                            if (isSel) Text(text = "✓", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stakes
                Text(
                    text = "Table Stakes (Real Coins)",
                    color = Color(0xFFFFD166),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableStakes.take(2).forEach { stake ->
                        val isSel = selectedStake == stake
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) Color(0xFF10B981) else Color(0xFF2E1065))
                                .clickable { selectedStake = stake }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = stake.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(text = "🪙 ${stake.stakeCoins}", color = Color(0xFFFFD166), fontSize = 10.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                androidx.compose.material3.Button(
                    onClick = { onSelect(selectedMode, selectedStake) },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        text = "Create Real-Time Match 🎲",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
