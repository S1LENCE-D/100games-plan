package com.playbox.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import com.playbox.app.diagnostics.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

// 沿用阶段 1 的 DataStore 文件名，避免覆盖升级时丢失收藏、最高分和设置。
internal const val PREFERENCES_FILE_NAME = "playbox_preferences"

/**
 * 全应用共享同一个 Preferences DataStore，游戏记录与设置使用独立 key 命名空间。
 * 遇到损坏文件时回退为空偏好，避免启动崩溃。
 */
internal val Context.appPreferences: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_FILE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

internal val DataStore<Preferences>.safeData: Flow<Preferences>
    get() = data.catch { throwable ->
        if (throwable is IOException) {
            AppLogger.warn("DataStore", "读取偏好失败，回退为空偏好", throwable)
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }
