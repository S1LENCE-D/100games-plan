package com.playbox.app.games.reaction

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.playbox.app.domain.model.AppSettings
import com.playbox.app.domain.model.GameId
import com.playbox.app.domain.repository.GameStatsRepository
import com.playbox.app.domain.repository.SettingsRepository
import com.playbox.app.feedback.FeedbackController
import com.playbox.app.games.common.BaseGameViewModel
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReactionViewModel(
    statsRepository: GameStatsRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : BaseGameViewModel(
    gameId = GameId.REACTION,
    statsRepository = statsRepository,
    savedStateHandle = savedStateHandle,
) {
    private val random = Random.Default
    private var roundJob: Job? = null
    private var calibrationJob: Job? = null
    private var signalAt = 0L
    private var calibrationStartAt = 0L
    private var challengeStartedAt = 0L
    private var compensation = 0
    private var resultHandled = false
    private val calibrationSamples = mutableListOf<Long>()
    private val sampledAccentRounds = mutableSetOf<Int>()
    private var savedCompensation = 0
    private var savedCalibrationAvailable = false
    private var savedCalibrationLoaded = false
    private var startPending = false

    private val _uiState = MutableStateFlow(ReactionUiState())
    val uiState: StateFlow<ReactionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                applySavedCalibration(settings)
            }
        }
    }

    fun startGame() {
        if (startPending) return
        startPending = true
        viewModelScope.launch {
            if (!savedCalibrationLoaded) {
                runCatching { settingsRepository.settings.first() }
                    .onSuccess(::applySavedCalibration)
            }
            startPending = false
            startGameInternal()
        }
    }

    private fun applySavedCalibration(settings: AppSettings) {
        savedCompensation = settings.reactionLatencyOffsetMs.coerceIn(-150, 150)
        savedCalibrationAvailable = settings.reactionCalibrationSaved
        savedCalibrationLoaded = true
    }

    private fun startGameInternal() {
        roundJob?.cancel()
        calibrationJob?.cancel()
        resultHandled = false
        markGameOpenedOnce()
        if (savedCalibrationAvailable) {
            compensation = savedCompensation
            _uiState.value = ReactionUiState(
                phase = ReactionPhase.CALIBRATION,
                hasStarted = true,
                calibrationComplete = true,
                calibrationReused = true,
                calibrationSaved = true,
                compensationMs = savedCompensation,
                calibrationRound = CALIBRATION_ROUNDS,
            )
        } else {
            startCalibration()
        }
    }

    /**
     * 简易音游式延迟校准：跟随 800ms 节拍点击，
     * 记录点击相对最近节拍的平均偏移，作为系统+硬件输入延迟补偿。
     */
    fun calibrationTap() {
        val state = _uiState.value
        if (state.phase != ReactionPhase.CALIBRATION || state.calibrationComplete) return
        val now = SystemClock.elapsedRealtime()
        if (now < calibrationStartAt + 300L) return
        if (calibrationSamples.size >= CALIBRATION_ROUNDS) return
        val relative = now - calibrationStartAt
        // 每小节第一拍在 0、800、1600、2400ms；重音在最后一拍。
        val accentInterval = BEATS_PER_BAR * 800L
        val firstAccent = (BEATS_PER_BAR - 1) * 800L
        val accentRound = ((relative - firstAccent + accentInterval / 2) / accentInterval).toInt()
        if (accentRound !in 0 until CALIBRATION_ROUNDS) return
        if (accentRound in sampledAccentRounds) return
        val expected = firstAccent + accentRound * accentInterval
        val offset = relative - expected
        if (abs(offset) > 300L) return
        sampledAccentRounds += accentRound
        calibrationSamples += offset
        val average = calibrationSamples.average().toInt()
        _uiState.update {
            it.copy(
                calibrationSamples = calibrationSamples.size,
                calibrationRound = accentRound + 1,
                compensationMs = average,
            )
        }
    }

    fun beginChallenge() {
        val state = _uiState.value
        if (state.phase != ReactionPhase.CALIBRATION || !state.calibrationComplete) return
        challengeStartedAt = SystemClock.elapsedRealtime()
        _uiState.update {
            it.copy(
                phase = ReactionPhase.WAITING,
                round = 1,
                results = emptyList(),
                lastMillis = null,
                averageMillis = 0,
                bestMillis = 0,
                tooSoonCount = 0,
                score = 0,
            )
        }
        startRound()
    }

    fun skipCalibration() {
        calibrationJob?.cancel()
        calibrationSamples.clear()
        sampledAccentRounds.clear()
        compensation = if (savedCalibrationAvailable) savedCompensation else 0
        _uiState.update {
            it.copy(
                calibrationComplete = true,
                calibrationReused = savedCalibrationAvailable,
                calibrationSaved = savedCalibrationAvailable,
                calibrationSamples = 0,
                compensationMs = compensation,
                calibrationPulse = false,
                calibrationAccent = false,
            )
        }
        beginChallenge()
    }

    fun recalibrate() {
        roundJob?.cancel()
        startCalibration()
    }

    fun clearSavedCalibration() {
        calibrationJob?.cancel()
        roundJob?.cancel()
        savedCompensation = 0
        savedCalibrationAvailable = false
        compensation = 0
        viewModelScope.launch {
            settingsRepository.setReactionLatencyCalibration(0, false)
        }
        startCalibration()
    }

    fun tap() {
        val state = _uiState.value
        if (!state.hasStarted || state.phase == ReactionPhase.CALIBRATION || state.showResult) return
        // 开始挑战按钮的抬手可能落到全屏点击层，开局短暂忽略点击。
        if (SystemClock.elapsedRealtime() - challengeStartedAt < 500L) return
        when (state.phase) {
            ReactionPhase.WAITING -> {
                roundJob?.cancel()
                FeedbackController.error()
                _uiState.update {
                    it.copy(
                        phase = ReactionPhase.TOO_SOON,
                        tooSoonCount = it.tooSoonCount + 1,
                    )
                }
                roundJob = viewModelScope.launch {
                    delay(800L)
                    startRound()
                }
            }

            ReactionPhase.GO -> {
                val raw = (SystemClock.elapsedRealtime() - signalAt).coerceAtLeast(1L)
                val elapsed = (raw - compensation).coerceAtLeast(1L)
                FeedbackController.tap()
                val updatedResults = state.results + elapsed
                val nextRound = state.round + 1
                if (state.round >= state.totalRounds) {
                    finishGame(updatedResults)
                } else {
                    _uiState.update {
                        it.copy(
                            phase = ReactionPhase.ROUND_RESULT,
                            results = updatedResults,
                            lastMillis = elapsed,
                            round = nextRound,
                        )
                    }
                    roundJob = viewModelScope.launch {
                        delay(750L)
                        startRound()
                    }
                }
            }

            else -> Unit
        }
    }

    fun restart() {
        roundJob?.cancel()
        startGame()
    }

    private fun startCalibration() {
        calibrationJob?.cancel()
        roundJob?.cancel()
        calibrationSamples.clear()
        sampledAccentRounds.clear()
        compensation = 0
        calibrationStartAt = SystemClock.elapsedRealtime() + 900L
        _uiState.value = ReactionUiState(
            phase = ReactionPhase.CALIBRATION,
            hasStarted = true,
            calibrationComplete = false,
            calibrationReused = false,
            calibrationSaved = false,
            calibrationRound = 1,
        )
        calibrationJob = viewModelScope.launch {
            for (beat in 0 until CALIBRATION_ROUNDS * BEATS_PER_BAR) {
                val round = beat / BEATS_PER_BAR + 1
                val accent = beat % BEATS_PER_BAR == BEATS_PER_BAR - 1
                val wait = calibrationStartAt + beat * 800L - SystemClock.elapsedRealtime()
                if (wait > 0L) delay(wait)
                _uiState.update {
                    it.copy(
                        calibrationPulse = true,
                        calibrationAccent = accent,
                        calibrationRound = round,
                    )
                }
                if (accent) {
                    FeedbackController.calibrationAccent()
                } else {
                    FeedbackController.calibrationBeat()
                }
                delay(160L)
                _uiState.update {
                    it.copy(
                        calibrationPulse = false,
                        calibrationAccent = false,
                    )
                }
            }
            finishCalibration()
        }
    }

    private fun finishCalibration() {
        if (_uiState.value.calibrationComplete) return
        calibrationJob?.cancel()
        val hasEnoughSamples = calibrationSamples.size >= 3
        val average = if (hasEnoughSamples) {
            calibrationSamples.average().toInt()
        } else {
            0
        }
        compensation = if (hasEnoughSamples) {
            average.coerceIn(-150, 150)
        } else if (savedCalibrationAvailable) {
            savedCompensation
        } else {
            0
        }
        if (hasEnoughSamples) {
            savedCompensation = compensation
            savedCalibrationAvailable = true
            viewModelScope.launch {
                settingsRepository.setReactionLatencyCalibration(compensation, true)
            }
        }
        _uiState.update {
            it.copy(
                phase = ReactionPhase.CALIBRATION,
                calibrationComplete = true,
                calibrationReused = !hasEnoughSamples && savedCalibrationAvailable,
                calibrationSaved = hasEnoughSamples || savedCalibrationAvailable,
                calibrationPulse = false,
                calibrationSamples = calibrationSamples.size,
                compensationMs = compensation,
            )
        }
    }

    private fun startRound() {
        roundJob?.cancel()
        _uiState.update {
            it.copy(
                phase = ReactionPhase.WAITING,
                lastMillis = it.lastMillis,
            )
        }
        roundJob = viewModelScope.launch {
            delay(random.nextLong(1_200L, 3_000L))
            signalAt = SystemClock.elapsedRealtime()
            _uiState.update { it.copy(phase = ReactionPhase.GO) }
        }
    }

    private fun finishGame(results: List<Long>) {
        if (resultHandled) return
        resultHandled = true
        roundJob?.cancel()
        val average = results.average().toInt()
        val best = results.minOrNull()?.toInt() ?: 0
        _uiState.update {
            it.copy(
                phase = ReactionPhase.FINISHED,
                results = results,
                lastMillis = results.lastOrNull(),
                averageMillis = average,
                bestMillis = best,
                score = average,
                showResult = false,
            )
        }
        FeedbackController.win()
        viewModelScope.launch {
            val isNewRecord = persistFinishedGame(average, lowerIsBetter = true)
            _uiState.update { it.copy(isNewRecord = isNewRecord, showResult = true) }
        }
    }

    companion object {
        private const val CALIBRATION_ROUNDS = 4
        private const val BEATS_PER_BAR = 4

        fun factory(
            statsRepository: GameStatsRepository,
            settingsRepository: SettingsRepository,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ReactionViewModel(
                        statsRepository = statsRepository,
                        settingsRepository = settingsRepository,
                        savedStateHandle = createSavedStateHandle(),
                    )
                }
            }
    }
}
