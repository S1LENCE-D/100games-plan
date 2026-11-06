package com.playbox.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.playbox.app.data.local.appPreferences
import com.playbox.app.data.local.safeData
import com.playbox.app.domain.model.GameId
import com.playbox.app.domain.model.PlayerStats
import com.playbox.app.domain.repository.GameStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PreferencesGameStatsRepository(
    context: Context,
) : GameStatsRepository {

    private val dataStore = context.applicationContext.appPreferences

    override val stats: Flow<PlayerStats> = dataStore.safeData
        .map { preferences ->
            val playCounts = GameId.entries
                .mapNotNull { gameId ->
                    val count = preferences[playCountKey(gameId)] ?: 0
                    if (count > 0) gameId to count else null
                }
                .toMap()

            PlayerStats(
                highScores = GameId.entries.associateWith { gameId ->
                    preferences[highScoreKey(gameId)] ?: 0
                },
                playCounts = playCounts,
                favorites = preferences[FAVORITE_GAMES_KEY]
                    .orEmpty()
                    .mapNotNull { slug -> GameId.fromSlug(slug) }
                    .toSet(),
                recentGameIds = preferences[RECENT_GAMES_KEY]
                    .orEmpty()
                    .split(',')
                    .mapNotNull { slug -> GameId.fromSlug(slug.trim()) }
                    .distinct()
                    .take(MAX_RECENT_GAMES),
            )
        }
        .distinctUntilChanged()

    override suspend fun markGameOpened(gameId: GameId) {
        dataStore.edit { preferences ->
            val previous = preferences[RECENT_GAMES_KEY]
                .orEmpty()
                .split(',')
                .mapNotNull { slug -> GameId.fromSlug(slug.trim()) }

            preferences[RECENT_GAMES_KEY] = (listOf(gameId) + previous.filterNot { it == gameId })
                .take(MAX_RECENT_GAMES)
                .joinToString(separator = ",") { it.slug }
        }
    }

    override suspend fun recordGameFinished(
        gameId: GameId,
        score: Int,
        lowerIsBetter: Boolean,
    ) {
        dataStore.edit { preferences ->
            if (score > 0) {
                val scoreKey = highScoreKey(gameId)
                val existingBest = preferences[scoreKey]
                val updatedBest = when {
                    existingBest == null -> score
                    lowerIsBetter -> minOf(existingBest, score)
                    else -> maxOf(existingBest, score)
                }
                preferences[scoreKey] = updatedBest
            }
            val countKey = playCountKey(gameId)
            preferences[countKey] = (preferences[countKey] ?: 0) + 1
        }
    }

    override suspend fun setFavorite(gameId: GameId, favorite: Boolean) {
        dataStore.edit { preferences ->
            val updated = preferences[FAVORITE_GAMES_KEY].orEmpty().toMutableSet()
            if (favorite) {
                updated += gameId.slug
            } else {
                updated -= gameId.slug
            }
            preferences[FAVORITE_GAMES_KEY] = updated
        }
    }

    override suspend fun hasSeenTutorial(gameId: GameId): Boolean {
        return dataStore.safeData.first()[tutorialKey(gameId)] ?: false
    }

    override suspend fun markTutorialSeen(gameId: GameId) {
        dataStore.edit { preferences ->
            preferences[tutorialKey(gameId)] = true
        }
    }

    override suspend fun clearAllRecords() {
        dataStore.edit { preferences ->
            GameId.entries.forEach { gameId ->
                preferences.remove(highScoreKey(gameId))
                preferences.remove(playCountKey(gameId))
            }
            preferences.remove(FAVORITE_GAMES_KEY)
            preferences.remove(RECENT_GAMES_KEY)
        }
    }

    private fun highScoreKey(gameId: GameId) = intPreferencesKey("stats_high_score_${gameId.slug}")

    private fun playCountKey(gameId: GameId) = intPreferencesKey("stats_play_count_${gameId.slug}")

    private fun tutorialKey(gameId: GameId) = booleanPreferencesKey("tutorial_seen_${gameId.slug}")

    private companion object {
        const val MAX_RECENT_GAMES = 8
        val FAVORITE_GAMES_KEY = stringSetPreferencesKey("stats_favorite_games")
        val RECENT_GAMES_KEY = stringPreferencesKey("stats_recent_games")
    }
}
