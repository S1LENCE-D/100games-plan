package com.playbox.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import com.playbox.app.data.local.appPreferences
import com.playbox.app.domain.model.GameId
import com.playbox.app.domain.repository.GameSaveRepository

class PreferencesGameSaveRepository(context: Context) : GameSaveRepository {
    private val dataStore = context.applicationContext.appPreferences

    override suspend fun save(gameId: GameId, payload: String) {
        dataStore.edit { preferences ->
            preferences[key(gameId)] = payload
        }
    }

    override suspend fun load(gameId: GameId): String? =
        dataStore.data.first()[key(gameId)]

    override suspend fun clear(gameId: GameId) {
        dataStore.edit { preferences ->
            preferences.remove(key(gameId))
        }
    }

    private fun key(gameId: GameId) = stringPreferencesKey("saved_game_${gameId.slug}")
}
