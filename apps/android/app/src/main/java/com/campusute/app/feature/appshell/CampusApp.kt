package com.campusute.app.feature.appshell

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.campusute.app.feature.auth.LoginScreen

/** Top-level navigation shell: login gate + signed-in home. */
@Composable
fun CampusApp(
    signedIn: Boolean,
    onSessionEnded: () -> Unit,
    onSessionStarted: () -> Unit,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (signedIn) "home" else "login",
    ) {
        composable("login") {
            LoginScreen(
                onSignedIn = {
                    onSessionStarted()
                    navController.navigate("home") { popUpTo("login") { inclusive = true } }
                },
            )
        }
        composable("home") {
            HomeScreen(
                onLogout = {
                    onSessionEnded()
                    navController.navigate("login") { popUpTo("home") { inclusive = true } }
                },
            )
        }
    }
}
