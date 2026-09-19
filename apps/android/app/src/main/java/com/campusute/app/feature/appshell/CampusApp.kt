package com.campusute.app.feature.appshell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.campusute.app.R
import com.campusute.app.core.data.SessionRepository
import com.campusute.app.core.designsystem.components.CampusTopBar
import com.campusute.app.feature.auth.LoginScreen
import com.campusute.app.feature.chat.ChatScreen
import com.campusute.app.feature.notes.NotesScreen
import com.campusute.app.feature.notes.NotesViewModel
import com.campusute.app.feature.schedule.TimetableScreen

/** Signed-in shell: top bar (bell + logout) + bottom tabs (home / timetable / notifications / AI chat / notes). */
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
    homeViewModel: HomeViewModel = hiltViewModel(),
    timetableViewModel: com.campusute.app.feature.schedule.ScheduleViewModel = hiltViewModel(),
    notesViewModel: NotesViewModel = hiltViewModel(),
    chatViewModel: com.campusute.app.feature.chat.ChatViewModel = hiltViewModel(),
) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    val unread by homeViewModel.unread.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            CampusTopBar(
                title = stringResource(R.string.app_name),
                actions = {
                    // Bell + unread badge share the SAME HomeViewModel instance as
                    // the home tab and the notification list (one "home" entry).
                    IconButton(
                        onClick = { tab = 2 },
                        modifier = Modifier.semantics { contentDescription = "Chuông thông báo" },
                    ) {
                        BadgedBox(badge = {
                            if (unread > 0) { Badge { Text(unread.toString()) } }
                        }) {
                            // No contentDescription on the Icon itself: the test
                            // selector lives on this IconButton's semantics only.
                            Icon(Icons.Filled.Notifications, contentDescription = null)
                        }
                    }
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
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Trang chủ") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text("Lịch học") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    label = { Text("Thông báo") },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Send, contentDescription = null) },
                    label = { Text("Trợ lý AI") },
                )
                NavigationBarItem(
                    selected = tab == 4,
                    onClick = { tab = 4 },
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    label = { Text("Ghi chú") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> HomeScreen(homeViewModel)
                1 -> TimetableScreen(timetableViewModel)
                2 -> NotificationList(homeViewModel)
                3 -> ChatScreen(chatViewModel)
                else -> NotesScreen(notesViewModel)
            }
        }
    }
}
