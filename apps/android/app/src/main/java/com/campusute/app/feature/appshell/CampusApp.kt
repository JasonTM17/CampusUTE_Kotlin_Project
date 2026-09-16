package com.campusute.app.feature.appshell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.campusute.app.R
import com.campusute.app.core.data.SessionRepository
import com.campusute.app.core.designsystem.components.CampusTopBar
import com.campusute.app.feature.auth.LoginScreen
import com.campusute.app.feature.chat.ChatScreen
import com.campusute.app.feature.schedule.TimetableScreen

/** Signed-in shell: top bar (logout) + bottom tabs (home / timetable / AI chat). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusApp(
    signedIn: Boolean,
    onSessionEnded: () -> Unit,
    onSessionStarted: () -> Unit,
    sessionRepository: SessionRepository,
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
            HomeShell(
                onLogout = {
                    sessionRepository.logout()
                    onSessionEnded()
                    navController.navigate("login") { popUpTo("home") { inclusive = true } }
                },
                sessionRepository = sessionRepository,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShell(
    onLogout: () -> Unit,
    sessionRepository: SessionRepository,
) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        topBar = {
            CampusTopBar(
                title = stringResource(R.string.app_name),
                actions = {
                    TextButton(onClick = {
                        sessionRepository.logout()
                        onLogout()
                    }) { Text("Đăng xuất") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("🏠") },
                    label = { Text("Trang chủ") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("📅") },
                    label = { Text("Lịch học") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Text("🤖") },
                    label = { Text("Trợ lý AI") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> HomeScreen()
                1 -> TimetableScreen(hiltViewModel())
                else -> ChatScreen(hiltViewModel())
            }
        }
    }
}
