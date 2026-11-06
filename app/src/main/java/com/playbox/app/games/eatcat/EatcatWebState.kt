package com.playbox.app.games.eatcat

import androidx.compose.runtime.Immutable
import org.json.JSONObject

@Immutable
data class EatcatWebState(
    val score: Int = 0,
    val keyHits: Int = 0,
    val rowHits: Int = 0,
    val errors: Int = 0,
    val started: Boolean = false,
    val over: Boolean = false,
    val paused: Boolean = false,
    val keyCount: Int = 4,
    val pattern: String = "",
    val timeLimit: Int = 0,
    val remaining: Int = 0,
    val isPractice: Boolean = false,
    val elapsedMs: Int = 0,
    val deviationMs: Int = 0,
    val liveCps: Float = 0f,
    val averageCps: Float = 0f,
    val cpsText: String = "",
    val hitsText: String = "",
    val scoreLayerVisible: Boolean = false,
) {
    val displayCps: Float get() = if (over) averageCps else liveCps

    companion object {
        fun fromJson(raw: String): EatcatWebState {
            val json = JSONObject(raw)
            return EatcatWebState(
                score = json.optInt("score"),
                keyHits = json.optInt("keyHits"),
                rowHits = json.optInt("rowHits"),
                errors = json.optInt("errors"),
                started = json.optBoolean("started"),
                over = json.optBoolean("over"),
                paused = json.optBoolean("paused"),
                keyCount = json.optInt("keyCount", 4),
                pattern = json.optString("pattern"),
                timeLimit = json.optInt("timeLimit"),
                remaining = json.optInt("remaining"),
                isPractice = json.optBoolean("isPractice"),
                elapsedMs = json.optInt("elapsedMs"),
                deviationMs = json.optInt("deviationMs"),
                liveCps = json.optDouble("liveCps", 0.0).toFloat(),
                averageCps = json.optDouble("averageCps", 0.0).toFloat(),
                cpsText = json.optString("cpsText"),
                hitsText = json.optString("hitsText"),
                scoreLayerVisible = json.optBoolean("scoreLayerVisible"),
            )
        }
    }
}
