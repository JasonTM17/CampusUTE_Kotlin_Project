package com.campusute.app.feature.appshell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.campusute.app.feature.events.EventsScreen
import com.campusute.app.feature.grades.GradesScreen
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
    bouncedReason: String? = null,
) {
    val navController = rememberNavController()
    // startDestination is read once, so a session that dies mid-week needs an explicit hop back
    // to the form — otherwise the shell stays mounted over a token store that just emptied.
    var wasSignedIn by rememberSaveable { mutableStateOf(signedIn) }
    LaunchedEffect(signedIn) {
        if (wasSignedIn && !signedIn) {
            navController.navigate("login") { popUpTo("home") { inclusive = true } }
        }
        wasSignedIn = signedIn
    }
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
                bouncedReason = bouncedReason,
            )
        }
        composable("home") {
            HomeShell(
                onLogout = {
                    onSessionEnded()
                    navController.navigate("login") { popUpTo("home") { inclusive = true } }
                },
                sessionRepository = sessionRepository,
                onOpenGrades = { navController.navigate("grades") },
                onOpenEvents = { navController.navigate("events") },
                onOpenTasks = { navController.navigate("tasks") },
            )
        }
        composable("grades") {
            SubScreen(title = "Điểm & học phần", onBack = { navController.popBackStack() }) {
                GradesScreen()
            }
        }
        composable("events") {
            SubScreen(title = "Sự kiện", onBack = { navController.popBackStack() }) {
                EventsScreen()
            }
        }
        composable("tasks") {
            SubScreen(title = "Công việc học tập", onBack = { navController.popBackStack() }) {
                TasksScreen()
            }
        }
    }
}

/**
 * A destination reached from a tab rather than from the bar. The shell's five tabs cannot host
 * bảng điểm and sự kiện without a sixth icon, so these ride the same NavHost with their own title
 * and a real up affordance — the back stack the design contract says a multi-screen flow needs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            CampusTopBar(
                title = title,
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Quay lại" },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShell(
    onLogout: () -> Unit,
    sessionRepository: SessionRepository,
    onOpenGrades: () -> Unit = {},
    onOpenEvents: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    homeViewModel: HomeViewModel = hiltViewModel(),
    timetableViewModel: com.campusute.app.feature.schedule.ScheduleViewModel = hiltViewModel(),
    notesViewModel: NotesViewModel = hiltViewModel(),
    chatViewModel: com.campusute.app.feature.chat.ChatViewModel = hiltViewModel(),
) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val unread = homeState.unread
    Scaffold(
        topBar = {
            CampusTopBar(
                // The assistant tab owns a richer identity than the app name; reusing the
                // shared bar keeps one header on screen instead of stacking a second one.
                title = if (tab == 3) "Trợ lý Học đường" else stringResource(R.string.app_name),
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
                0 -> HomeScreen(homeViewModel, onOpenInbox = { tab = 2 }, onOpenGrades = onOpenGrades, onOpenEvents = onOpenEvents, onOpenTasks = onOpenTasks)
                1 -> TimetableScreen(timetableViewModel)
                2 -> NotificationList(homeViewModel)
                3 -> ChatScreen(chatViewModel)
                else -> NotesScreen(notesViewModel)
            }
        }
    }
}
