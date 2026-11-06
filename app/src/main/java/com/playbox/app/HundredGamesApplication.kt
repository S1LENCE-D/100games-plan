package com.playbox.app

import android.app.Application
import android.os.Build
import android.os.Process
import com.playbox.app.diagnostics.AppLogger
import com.playbox.app.feedback.FeedbackController

class HundredGamesApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(this)
        FeedbackController.initialize(this)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                AppLogger.error(
                    tag = "Crash",
                    message = "未捕获异常 thread=${thread.name}",
                    throwable = throwable,
                )
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
            }
        }

        container = AppContainer(this)
        AppLogger.info(
            tag = "App",
            message = "启动 100Games Plan ${BuildConfig.VERSION_NAME} (${if (BuildConfig.DEBUG) "debug" else "release"}) · " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }
}
