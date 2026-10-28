package com.example.core.sound

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Low-latency audio and haptics manager for Ludo Live.
 * Generates tactile sound feedback for dice rolls, token steps, captures,
 * safe zone chimes, gifting swooshes, and victory celebrations.
 */
class SoundManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default)

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 85)
    } catch (_: Throwable) {
        null
    }

    private fun safeTone(tone: Int, durationMs: Int) {
        try {
            toneGenerator?.startTone(tone, durationMs)
        } catch (_: Throwable) {}
    }

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Throwable) {
        null
    }

    var isSoundEnabled: Boolean = true
    var isHapticsEnabled: Boolean = true

    fun playDiceRoll() {
        if (!isSoundEnabled && !isHapticsEnabled) return
        scope.launch {
            for (i in 0..4) {
                if (isSoundEnabled) {
                    safeTone(ToneGenerator.TONE_PROP_BEEP, 30)
                }
                vibrateLight()
                delay(60)
            }
        }
    }

    fun playTokenMove() {
        if (!isSoundEnabled && !isHapticsEnabled) return
        scope.launch {
            if (isSoundEnabled) {
                safeTone(ToneGenerator.TONE_DTMF_D, 40)
            }
            vibrateLight()
        }
    }

    fun playTokenCapture() {
        if (!isSoundEnabled && !isHapticsEnabled) return
        scope.launch {
            if (isSoundEnabled) {
                safeTone(ToneGenerator.TONE_CDMA_HIGH_L, 120)
                delay(70)
                safeTone(ToneGenerator.TONE_CDMA_LOW_L, 180)
            }
            vibrateHeavy()
        }
    }

    fun playSafeZoneChime() {
        if (!isSoundEnabled && !isHapticsEnabled) return
        scope.launch {
            if (isSoundEnabled) {
                safeTone(ToneGenerator.TONE_DTMF_A, 80)
                delay(90)
                safeTone(ToneGenerator.TONE_DTMF_B, 120)
            }
            vibrateLight()
        }
    }

    fun playHomeReached() {
        if (!isSoundEnabled && !isHapticsEnabled) return
        scope.launch {
            if (isSoundEnabled) {
                safeTone(ToneGenerator.TONE_PROP_PROMPT, 100)
                delay(120)
                safeTone(ToneGenerator.TONE_PROP_ACK, 200)
            }
            vibrateMedium()
        }
    }

    fun playGiftSent() {
        if (!isSoundEnabled && !isHapticsEnabled) return
        scope.launch {
            if (isSoundEnabled) {
                safeTone(ToneGenerator.TONE_DTMF_3, 70)
                delay(80)
                safeTone(ToneGenerator.TONE_DTMF_6, 70)
                delay(80)
                safeTone(ToneGenerator.TONE_DTMF_9, 120)
            }
            vibrateLight()
        }
    }

    fun playVictoryFanfare() {
        if (!isSoundEnabled && !isHapticsEnabled) return
        scope.launch {
            val tones = intArrayOf(
                ToneGenerator.TONE_DTMF_1,
                ToneGenerator.TONE_DTMF_3,
                ToneGenerator.TONE_DTMF_5,
                ToneGenerator.TONE_DTMF_8,
                ToneGenerator.TONE_PROP_ACK
            )
            for (t in tones) {
                if (isSoundEnabled) {
                    safeTone(t, 120)
                }
                vibrateMedium()
                delay(140)
            }
        }
    }

    private fun vibrateLight() {
        if (!isHapticsEnabled || vibrator?.hasVibrator() != true) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20)
            }
        } catch (_: Exception) {}
    }

    private fun vibrateMedium() {
        if (!isHapticsEnabled || vibrator?.hasVibrator() != true) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        } catch (_: Exception) {}
    }

    private fun vibrateHeavy() {
        if (!isHapticsEnabled || vibrator?.hasVibrator() != true) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 30, 80), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
    }
}
