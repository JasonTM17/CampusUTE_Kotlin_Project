package com.campusute.app.feature.appshell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.R
import com.campusute.app.core.designsystem.components.CampusCard
import com.campusute.app.core.designsystem.components.CampusTopBar

/** Signed-in shell (Phase 1): profile + logout. Schedule feed mounts at P2. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CampusTopBar(
                title = stringResource(R.string.app_name),
                actions = {
                    TextButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Text("Đăng xuất")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            CampusCard {
                Text(
                    text = "Xin chào, ${user?.fullName ?: "sinh viên"}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = user?.let { "${it.studentCode ?: "-"} · ${it.department ?: "-"}" } ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            CampusCard {
                Text("Lịch học tuần này", style = MaterialTheme.typography.titleSmall)
                Text(
                    "PLANNED — trục lịch học offline-first sẽ lắp vào ở Phase 2 (vertical slice Student Schedule).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
