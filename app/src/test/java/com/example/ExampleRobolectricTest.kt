package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.logic.BotAiEngine
import com.example.domain.logic.LudoBoardRules
import com.example.domain.model.Pawn
import com.example.domain.model.PawnState
import com.example.domain.model.Player
import com.example.domain.model.PlayerColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Ludo Live", appName)
    val webClientId = context.getString(R.string.default_web_client_id)
    assertTrue(webClientId.isNotEmpty())
  }

  @Test
  fun `ludo rules unlock from yard only on six`() {
    val yardPawn = Pawn(id = 0, color = PlayerColor.RED, step = 0, state = PawnState.YARD)
    assertFalse(LudoBoardRules.canPawnMove(yardPawn, 4))
    assertTrue(LudoBoardRules.canPawnMove(yardPawn, 6))
    assertEquals(1, LudoBoardRules.computeNewStep(yardPawn, 6))
  }

  @Test
  fun `safe star tiles are recognized`() {
    assertTrue(LudoBoardRules.isTrackIndexSafe(0))   // Red start
    assertTrue(LudoBoardRules.isTrackIndexSafe(8))   // Star safe
    assertTrue(LudoBoardRules.isTrackIndexSafe(13))  // Green start
    assertTrue(LudoBoardRules.isTrackIndexSafe(21))  // Star safe
    assertTrue(LudoBoardRules.isTrackIndexSafe(26))  // Yellow start
    assertTrue(LudoBoardRules.isTrackIndexSafe(34))  // Star safe
    assertTrue(LudoBoardRules.isTrackIndexSafe(39))  // Blue start
    assertTrue(LudoBoardRules.isTrackIndexSafe(47))  // Star safe

    assertFalse(LudoBoardRules.isTrackIndexSafe(1))
    assertFalse(LudoBoardRules.isTrackIndexSafe(7))
  }

  @Test
  fun `bot ai prioritizes capturing opponent piece`() {
    // Red active bot with two pawns:
    // pawn 0 is at step 1 (track index 0). Roll 2 puts it at step 3 (track index 2).
    // An opponent piece (Green) is located at global track index 2.
    // pawn 1 is at step 10. Roll 2 puts it at step 12 (no opponent).
    val redPlayer = Player(
      id = "bot_red",
      name = "Bot Red",
      color = PlayerColor.RED,
      isHuman = false,
      pawns = listOf(
        Pawn(id = 0, color = PlayerColor.RED, step = 1, state = PawnState.TRACK),
        Pawn(id = 1, color = PlayerColor.RED, step = 10, state = PawnState.TRACK)
      )
    )

    // Green starts at 13. To be at global track index 2, step would be (2 - 13 + 52) % 52 + 1 = 42
    val greenPlayer = Player(
      id = "opp_green",
      name = "Opp Green",
      color = PlayerColor.GREEN,
      isHuman = true,
      pawns = listOf(
        Pawn(id = 0, color = PlayerColor.GREEN, step = 42, state = PawnState.TRACK)
      )
    )

    val bestMove = BotAiEngine.selectBestMove(
      activePlayer = redPlayer,
      diceRoll = 2,
      allPlayers = listOf(redPlayer, greenPlayer)
    )

    assertNotNull(bestMove)
    assertEquals(0, bestMove?.id) // Pawn 0 should be selected for the capture!
  }

  @Test
  fun `new user profile initializes with 50 free coins`() {
    val profile = com.example.data.model.FirestoreUserProfile(
      id = "user_123",
      displayName = "Alex",
      email = "alex@example.com"
    )
    assertEquals(50L, profile.coins)
  }

  @Test
  fun `ludo game logic unlocks pawn on six and calculates board occupancy`() {
    val logic = com.example.domain.logic.LudoGameLogic()
    val initialGame = logic.createInitialGame("game_1", "user_host", "QUICK_1V1", 50L, listOf("user_host", "user_opp"))

    // Initially pawn is in yard, roll 4 is invalid
    val invalidRoll = logic.validateMove(initialGame.copy(diceRoll = 4, diceRolled = true), PlayerColor.RED, 0, 4)
    assertFalse(invalidRoll.isValid)

    // Roll 6 allows unlocking
    val validRoll = logic.validateMove(initialGame.copy(diceRoll = 6, diceRolled = true), PlayerColor.RED, 0, 6)
    assertTrue(validRoll.isValid)
    assertEquals(1, validRoll.targetStep)
    assertTrue(validRoll.isUnlocking)

    // Execute move
    val outcome = logic.executeMove(initialGame.copy(diceRoll = 6, diceRolled = true), PlayerColor.RED, 0)
    assertTrue(outcome.earnedBonusTurn) // Rolling 6 grants bonus turn
    val redPawn0 = outcome.updatedGame.pawns["RED"]?.first { it.pawnId == 0 }
    assertEquals(1, redPawn0?.step)
    assertEquals("TRACK", redPawn0?.state)

    // Check board occupancy: track index 0 should contain Red pawn 0
    val occupants = outcome.updatedGame.boardOccupancy["0"]
    assertNotNull(occupants)
    assertEquals(1, occupants?.size)
    assertEquals("RED", occupants?.first()?.color)
    assertEquals(0, occupants?.first()?.pawnId)
  }

  @Test
  fun `ludo game logic captures opponent on unsafe tile and resets to yard`() {
    val logic = com.example.domain.logic.LudoGameLogic()
    val game = logic.createInitialGame("game_2", "user_host", "QUICK_1V1", 50L, listOf("user_host", "user_opp"))

    // Setup: Red pawn 0 at step 3 (global track index 2).
    // Opponent (Yellow) pawn 0 at step 29 (global track index 2, since Yellow starts at 26).
    // Red rolls 2: Red pawn 0 moves to step 5 (track index 4).
    // Opponent Yellow pawn 0 is at global track index 4 (Yellow step 31, since (26 + 30) % 52 = 4).
    // Tile 4 is NOT a safe star tile!
    val customPawns = game.pawns.toMutableMap()
    customPawns["RED"] = listOf(
      com.example.data.model.PawnData(pawnId = 0, color = "RED", step = 3, state = "TRACK"),
      com.example.data.model.PawnData(pawnId = 1, color = "RED", step = 0, state = "YARD"),
      com.example.data.model.PawnData(pawnId = 2, color = "RED", step = 0, state = "YARD"),
      com.example.data.model.PawnData(pawnId = 3, color = "RED", step = 0, state = "YARD")
    )
    customPawns["YELLOW"] = listOf(
      com.example.data.model.PawnData(pawnId = 0, color = "YELLOW", step = 31, state = "TRACK"), // On track tile 4!
      com.example.data.model.PawnData(pawnId = 1, color = "YELLOW", step = 0, state = "YARD"),
      com.example.data.model.PawnData(pawnId = 2, color = "YELLOW", step = 0, state = "YARD"),
      com.example.data.model.PawnData(pawnId = 3, color = "YELLOW", step = 0, state = "YARD")
    )

    val gameWithPawns = game.copy(
      pawns = customPawns,
      currentTurnColor = "RED",
      diceRoll = 2,
      diceRolled = true
    )

    val validation = logic.validateMove(gameWithPawns, PlayerColor.RED, 0, 2)
    assertTrue(validation.isValid)
    assertTrue(validation.willCapture)
    assertEquals("YELLOW", validation.capturedOpponentColor)
    assertEquals(0, validation.capturedPawnId)

    // Execute move and verify Yellow pawn was sent back to YARD and Red got bonus turn
    val outcome = logic.executeMove(gameWithPawns, PlayerColor.RED, 0)
    assertTrue(outcome.wasCaptured)
    assertTrue(outcome.earnedBonusTurn)

    val yellowPawn0 = outcome.updatedGame.pawns["YELLOW"]?.first { it.pawnId == 0 }
    assertEquals(0, yellowPawn0?.step)
    assertEquals("YARD", yellowPawn0?.state)
  }

  @Test
  fun `ludo game logic enforces exact home stretch roll and detects victory`() {
    val logic = com.example.domain.logic.LudoGameLogic()
    val game = logic.createInitialGame("game_3", "user_host", "QUICK_1V1", 50L, listOf("user_host", "user_opp"))

    // Red pawn 0 is at step 55 (in home stretch, needs exactly 2 to reach 57)
    val customPawns = game.pawns.toMutableMap()
    customPawns["RED"] = listOf(
      com.example.data.model.PawnData(pawnId = 0, color = "RED", step = 55, state = "HOME_STRETCH"),
      com.example.data.model.PawnData(pawnId = 1, color = "RED", step = 57, state = "HOME"),
      com.example.data.model.PawnData(pawnId = 2, color = "RED", step = 57, state = "HOME"),
      com.example.data.model.PawnData(pawnId = 3, color = "RED", step = 57, state = "HOME")
    )

    // Roll 3 overshoots home (55 + 3 = 58 > 57): Invalid!
    val overshootValidation = logic.validateMove(game.copy(pawns = customPawns, diceRoll = 3, diceRolled = true), PlayerColor.RED, 0, 3)
    assertFalse(overshootValidation.isValid)

    // Roll 2 is exact: Valid!
    val exactValidation = logic.validateMove(game.copy(pawns = customPawns, diceRoll = 2, diceRolled = true), PlayerColor.RED, 0, 2)
    assertTrue(exactValidation.isValid)
    assertTrue(exactValidation.willReachHome)

    // Executing move should complete the match because all 4 pawns reach HOME!
    val outcome = logic.executeMove(game.copy(pawns = customPawns, diceRoll = 2, diceRolled = true), PlayerColor.RED, 0)
    assertTrue(outcome.reachedHome)
    assertTrue(outcome.isGameOver)
    assertEquals("RED", outcome.winnerColor)
    assertEquals(4, outcome.updatedGame.homeOccupancy["RED"])
    assertEquals("COMPLETED", outcome.updatedGame.status)
  }

  @Test
  fun `gifting costs and balance deductions are accurate`() {
    val initialCoins = 500L
    val initialGems = 10L
    val gift = com.example.domain.model.GiftType.CROWN // 200 coins, 2 gems

    val remainingCoins = (initialCoins - gift.coinCost).coerceAtLeast(0L)
    val remainingGems = (initialGems - gift.gemCost).coerceAtLeast(0L)

    assertEquals(300L, remainingCoins)
    assertEquals(8L, remainingGems)
  }

  @Test
  fun `table stake deduction and win pot calculation are balanced`() {
    val stake = com.example.domain.model.TableStake("starter", "50 Coins Table", 50L, 100L)
    val userBalance = 50L
    assertTrue(userBalance >= stake.stakeCoins)

    // Deduct stake on match start
    val balanceAfterBet = userBalance - stake.stakeCoins
    assertEquals(0L, balanceAfterBet)

    // Award full pot on victory
    val balanceAfterWin = balanceAfterBet + stake.rewardPot
    assertEquals(100L, balanceAfterWin)
  }

  @Test
  fun `pawn advance on track updates state accurately`() {
    val pawn = Pawn(id = 0, color = PlayerColor.RED, step = 1, state = PawnState.TRACK)
    val targetStep = LudoBoardRules.computeNewStep(pawn, 4)
    assertEquals(5, targetStep)
    val targetGlobalTrack = LudoBoardRules.getGlobalTrackIndex(PlayerColor.RED, targetStep)
    assertEquals(4, targetGlobalTrack)
    assertFalse(LudoBoardRules.isTrackIndexSafe(targetGlobalTrack))
  }
}

