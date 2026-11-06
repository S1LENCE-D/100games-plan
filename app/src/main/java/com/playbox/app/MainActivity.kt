package com.playbox.app

import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.metrics.performance.JankStats
import com.playbox.app.diagnostics.AppLogger
import com.playbox.app.diagnostics.JankMonitor
import com.playbox.app.feedback.FeedbackController
import com.playbox.app.domain.model.AppSettings
import com.playbox.app.ui.MainViewModel
import com.playbox.app.ui.navigation.HundredGamesApp
import com.playbox.app.ui.theme.HundredGamesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    private var initialSettings: AppSettings = AppSettings()

    private val mainViewModel: MainViewModel by viewModels {
        val container = (application as HundredGamesApplication).container
        MainViewModel.Factory(
            settingsRepository = container.settingsRepository,
            initialSettings = initialSettings,
        )
    }

    private var jankStats: JankStats? = null
    private var predictiveBackBlocker: OnBackInvokedCallback? = null
    private var displayLogged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 在 Compose 首帧之前同步读取一次设置，避免先以默认配色绘制、随后再切换主题。
        // 系统启动画面/启动窗口会覆盖这段等待时间。
        initialSettings = loadInitialSettings()

        val contentStartedAt = SystemClock.elapsedRealtime()
        setContent {
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()
            LaunchedEffect(settings.soundEnabled, settings.hapticsEnabled) {
                FeedbackController.updateSettings(
                    sound = settings.soundEnabled,
                    haptics = settings.hapticsEnabled,
                )
            }
            LaunchedEffect(settings.predictiveBackEnabled) {
                updatePredictiveBack(settings.predictiveBackEnabled)
            }
            HundredGamesTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColorEnabled,
                colorPalette = settings.colorPalette,
            ) {
                HundredGamesApp(container = (application as HundredGamesApplication).container)
            }
        }

        window.decorView.post {
            AppLogger.info(
                "Startup",
                "首帧绘制完成 ${SystemClock.elapsedRealtime() - contentStartedAt}ms",
            )
        }

        jankStats = runCatching { JankStats.createAndTrack(window, JankMonitor) }
            .onFailure { throwable ->
                AppLogger.warn("Jank", "JankStats 初始化失败，本次运行将不记录掉帧", throwable)
            }
            .getOrNull()
    }

    override fun onResume() {
        super.onResume()
        jankStats?.isTrackingEnabled = true
        JankMonitor.reset()
        if (!displayLogged) {
            displayLogged = true
            logDisplayInfo()
        }
        AppLogger.info("Lifecycle", "Activity resumed")
    }

    override fun onPause() {
        jankStats?.isTrackingEnabled = false
        JankMonitor.flush()
        AppLogger.info("Lifecycle", "Activity paused")
        super.onPause()
    }

    override fun onDestroy() {
        unregisterPredictiveBackBlocker()
        jankStats?.isTrackingEnabled = false
        jankStats = null
        super.onDestroy()
    }

    private fun updatePredictiveBack(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (enabled) {
            unregisterPredictiveBackBlocker()
            return
        }
        if (predictiveBackBlocker != null) return
        val callback = OnBackInvokedCallback {
            onBackPressedDispatcher.onBackPressed()
        }
        predictiveBackBlocker = callback
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback,
        )
    }

    private fun unregisterPredictiveBackBlocker() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        predictiveBackBlocker?.let { callback ->
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        }
        predictiveBackBlocker = null
    }

    private fun loadInitialSettings(): AppSettings {
        val startedAt = SystemClock.elapsedRealtime()
        val container = (application as HundredGamesApplication).container
        val loaded = runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(1_500) {
                runCatching { container.settingsRepository.settings.first() }
                    .onFailure { throwable ->
                        AppLogger.warn("Startup", "预载设置失败，使用默认设置", throwable)
                    }
                    .getOrNull()
            }
        }
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        if (loaded == null) {
            AppLogger.warn("Startup", "预载设置超时/失败，耗时 ${elapsed}ms，使用默认设置")
        } else {
            AppLogger.info(
                "Startup",
                "预载设置 ${elapsed}ms · theme=${loaded.themeMode} · " +
                    "dynamic=${loaded.dynamicColorEnabled} · palette=${loaded.colorPalette}",
            )
        }
        return loaded ?: AppSettings()
    }

    private fun logDisplayInfo() {
        val configuration = resources.configuration
        val refreshRate = getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.refreshRate
        AppLogger.info(
            tag = "Display",
            message = "屏幕 ${configuration.screenWidthDp}×${configuration.screenHeightDp}dp · " +
                "刷新率 ${refreshRate?.let { rate -> "%.1f".format(rate) } ?: "未知"}Hz · " +
                "density ${resources.displayMetrics.density}",
        )
    }
}
