package com.playbox.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.playbox.app.AppContainer
import com.playbox.app.ui.screens.diagnostics.DiagnosticsRouteScreen
import com.playbox.app.ui.screens.donation.DonationRouteScreen
import com.playbox.app.ui.screens.gamedetail.GameDetailRouteScreen
import com.playbox.app.ui.screens.home.HomeRouteScreen
import com.playbox.app.ui.screens.nearby.NearbyRouteScreen
import com.playbox.app.ui.screens.settings.SettingsRouteScreen
import com.playbox.app.games.GameHostScreen

private const val SHARED_AXIS_DURATION_MILLIS = 220

@Composable
fun HundredGamesApp(container: AppContainer) {
    val navController = rememberNavController()
    HundredGamesNavHost(
        container = container,
        navController = navController,
    )
}

@Composable
private fun HundredGamesNavHost(
    container: AppContainer,
    navController: NavHostController,
) {
    val navigateBackOrHome: () -> Unit = {
        if (!navController.popBackStack()) {
            navController.navigate(HomeRoute) {
                launchSingleTop = true
            }
        }
    }

    val navigateToLobby: () -> Unit = {
        if (!navController.popBackStack(HomeRoute, inclusive = false)) {
            navController.navigate(HomeRoute) {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(
                    durationMillis = SHARED_AXIS_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
                initialOffsetX = { fullWidth -> fullWidth / 10 },
            ) + fadeIn(animationSpec = tween(
                    durationMillis = SHARED_AXIS_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ))
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = tween(
                    durationMillis = SHARED_AXIS_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
                targetOffsetX = { fullWidth -> -fullWidth / 14 },
            ) + fadeOut(animationSpec = tween(
                    durationMillis = SHARED_AXIS_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ))
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = tween(
                    durationMillis = SHARED_AXIS_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
                initialOffsetX = { fullWidth -> -fullWidth / 5 },
            ) + fadeIn(animationSpec = tween(
                    durationMillis = SHARED_AXIS_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(
                    durationMillis = SHARED_AXIS_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
                targetOffsetX = { fullWidth -> fullWidth / 8 },
            ) + fadeOut(animationSpec = tween(
                    durationMillis = SHARED_AXIS_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ))
        },
    ) {
        composable<HomeRoute> {
            HomeRouteScreen(
                container = container,
                onOpenSettings = {
                    navController.navigate(SettingsRoute) {
                        launchSingleTop = true
                    }
                },
                onOpenGame = { gameId ->
                    navController.navigate(GameDetailRoute(gameId = gameId.slug)) {
                        launchSingleTop = true
                    }
                },
                onOpenNearby = {
                    navController.navigate(NearbyRoute) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<NearbyRoute> {
            NearbyRouteScreen(
                container = container,
                onBack = navigateBackOrHome,
            )
        }

        composable<SettingsRoute> {
            SettingsRouteScreen(
                container = container,
                onBack = navigateBackOrHome,
                onOpenDiagnostics = {
                    navController.navigate(DiagnosticsRoute) {
                        launchSingleTop = true
                    }
                },
                onOpenDonation = {
                    navController.navigate(DonationRoute) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<DonationRoute> {
            DonationRouteScreen(onBack = navigateBackOrHome)
        }

        composable<DiagnosticsRoute> {
            DiagnosticsRouteScreen(onBack = navigateBackOrHome)
        }

        composable<GameDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<GameDetailRoute>()
            GameDetailRouteScreen(
                container = container,
                gameSlug = route.gameId,
                onBack = navigateBackOrHome,
                onStartGame = { gameId ->
                    navController.navigate(GameRoute(gameId = gameId.slug)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<GameRoute>(
            enterTransition = { fadeIn(animationSpec = tween(120)) },
            exitTransition = { fadeOut(animationSpec = tween(90)) },
            popEnterTransition = {
                fadeIn(animationSpec = tween(200)) +
                    scaleIn(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        initialScale = 0.96f,
                    )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(160)) +
                    scaleOut(
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        targetScale = 0.98f,
                    )
            },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<GameRoute>()
            GameHostScreen(
                container = container,
                gameSlug = route.gameId,
                onExit = navigateToLobby,
            )
        }
    }
}
