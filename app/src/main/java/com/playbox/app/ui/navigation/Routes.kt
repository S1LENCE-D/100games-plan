package com.playbox.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object SettingsRoute

@Serializable
data class GameDetailRoute(
    val gameId: String,
)

@Serializable
data class GameRoute(
    val gameId: String,
)

@Serializable
data object DiagnosticsRoute

@Serializable
data object DonationRoute

@Serializable
data object NearbyRoute
