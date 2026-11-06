package com.playbox.app.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RawRes
import com.playbox.app.R

/**
 * 全局游戏反馈：短音效与触感都由这里统一调度。
 * 设置中的“音效/震动反馈”会在 MainActivity 收集设置后同步到这里。
 *
 * SoundPool 只加载小体积 WAV，不占用 MediaPlayer 解码线程；
 * 震动优先使用系统预定义效果，低版本 Android 回退到短 one-shot。
 */
object FeedbackController {

    enum class SoundCue(
        @RawRes val rawRes: Int,
        val volume: Float,
    ) {
        TAP(R.raw.feedback_tap, 0.42f),
        MOVE(R.raw.feedback_move, 0.55f),
        MERGE(R.raw.feedback_merge, 0.62f),
        ROTATE(R.raw.feedback_rotate, 0.55f),
        DROP(R.raw.feedback_drop, 0.62f),
        CLEAR(R.raw.feedback_clear, 0.70f),
        EAT(R.raw.feedback_eat, 0.58f),
        WIN(R.raw.feedback_win, 0.68f),
        GAME_OVER(R.raw.feedback_game_over, 0.60f),
        ERROR(R.raw.feedback_error, 0.42f),
        CALIBRATION(R.raw.feedback_calibration, 0.34f),
        CALIBRATION_ACCENT(R.raw.feedback_calibration_accent, 0.48f),
    }

    enum class HapticCue {
        TAP,
        MOVE,
        MERGE,
        ROTATE,
        DROP,
        CLEAR,
        EAT,
        WIN,
        GAME_OVER,
        ERROR,
    }

    @Volatile private var soundEnabled: Boolean = true
    @Volatile private var hapticsEnabled: Boolean = true
    @Volatile private var initialized: Boolean = false

    private var soundPool: SoundPool? = null
    private var soundIds: Map<SoundCue, Int> = emptyMap()
    private var vibrator: Vibrator? = null
    private val lastSoundAt = HashMap<SoundCue, Long>()

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext

        soundPool = runCatching {
            SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
        }.getOrNull()

        soundIds = soundPool?.let { pool ->
            SoundCue.entries.associateWith { cue ->
                runCatching { pool.load(appContext, cue.rawRes, 1) }.getOrDefault(0)
            }.filterValues { it != 0 }
        }.orEmpty()

        vibrator = runCatching { resolveVibrator(appContext) }.getOrNull()
    }

    fun updateSettings(sound: Boolean, haptics: Boolean) {
        soundEnabled = sound
        hapticsEnabled = haptics
    }

    fun tap() = emit(SoundCue.TAP, HapticCue.TAP)
    fun move() = emit(SoundCue.MOVE, HapticCue.MOVE)
    fun merge() = emit(SoundCue.MERGE, HapticCue.MERGE)
    fun rotate() = emit(SoundCue.ROTATE, HapticCue.ROTATE)
    fun drop() = emit(SoundCue.DROP, HapticCue.DROP)
    fun clear() = emit(SoundCue.CLEAR, HapticCue.CLEAR)
    fun eat() = emit(SoundCue.EAT, HapticCue.EAT)
    fun win() = emit(SoundCue.WIN, HapticCue.WIN)
    fun gameOver() = emit(SoundCue.GAME_OVER, HapticCue.GAME_OVER)
    fun error() = emit(SoundCue.ERROR, HapticCue.ERROR)
    fun calibrationBeat() = emit(SoundCue.CALIBRATION, HapticCue.TAP)
    fun calibrationAccent() = emit(SoundCue.CALIBRATION_ACCENT, HapticCue.MOVE)

    private fun emit(sound: SoundCue, haptic: HapticCue) {
        if (soundEnabled) playSound(sound)
        if (hapticsEnabled) vibrate(haptic)
    }

    private fun playSound(cue: SoundCue) {
        val pool = soundPool ?: return
        val id = soundIds[cue] ?: return
        val now = SystemClock.elapsedRealtime()
        val previous = lastSoundAt[cue] ?: 0L
        if (now - previous < 28L) return
        lastSoundAt[cue] = now
        pool.play(id, cue.volume, cue.volume, 1, 0, 1f)
    }

    private fun vibrate(cue: HapticCue) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return

        runCatching {
            when (cue) {
                HapticCue.TAP -> predefinedOrOneShot(device, VibrationEffect.EFFECT_CLICK, 18L)
                HapticCue.MOVE -> predefinedOrOneShot(device, VibrationEffect.EFFECT_TICK, 12L)
                HapticCue.MERGE -> predefinedOrOneShot(device, VibrationEffect.EFFECT_DOUBLE_CLICK, 28L)
                HapticCue.ROTATE -> predefinedOrOneShot(device, VibrationEffect.EFFECT_TICK, 14L)
                HapticCue.DROP -> predefinedOrOneShot(device, VibrationEffect.EFFECT_HEAVY_CLICK, 36L)
                HapticCue.CLEAR -> waveform(device, longArrayOf(0L, 14L, 28L, 18L))
                HapticCue.EAT -> predefinedOrOneShot(device, VibrationEffect.EFFECT_CLICK, 20L)
                HapticCue.WIN -> waveform(device, longArrayOf(0L, 30L, 40L, 36L, 50L, 54L))
                HapticCue.GAME_OVER -> waveform(device, longArrayOf(0L, 60L, 55L, 90L))
                HapticCue.ERROR -> predefinedOrOneShot(device, VibrationEffect.EFFECT_DOUBLE_CLICK, 38L)
            }
        }
    }

    private fun predefinedOrOneShot(device: Vibrator, effectId: Int, fallbackMillis: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            device.vibrate(VibrationEffect.createPredefined(effectId))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            device.vibrate(VibrationEffect.createOneShot(fallbackMillis, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            device.vibrate(fallbackMillis)
        }
    }

    private fun waveform(device: Vibrator, timings: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            device.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            device.vibrate(timings, -1)
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
