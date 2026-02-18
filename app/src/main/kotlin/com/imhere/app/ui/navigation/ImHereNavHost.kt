package com.imhere.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.imhere.app.ui.screens.HomeRoute
import com.imhere.app.ui.screens.SettingsRoute

object AppRoute {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

@Composable
fun ImHereNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = AppRoute.HOME) {
        composable(AppRoute.HOME) {
            HomeRoute(onOpenSettings = { navController.navigate(AppRoute.SETTINGS) })
        }
        composable(AppRoute.SETTINGS) {
            SettingsRoute(onBack = { navController.popBackStack() })
        }
    }
}
