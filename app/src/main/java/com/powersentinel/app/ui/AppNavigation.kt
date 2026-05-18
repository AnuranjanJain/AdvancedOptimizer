package com.powersentinel.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.powersentinel.app.ui.screens.DashboardScreen
import com.powersentinel.app.ui.screens.OnboardingScreen
import com.powersentinel.app.ui.screens.SplashScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") { SplashScreen(navController) }
        composable("onboarding") { OnboardingScreen(navController) }
        composable("dashboard") { DashboardScreen() }
    }
}
