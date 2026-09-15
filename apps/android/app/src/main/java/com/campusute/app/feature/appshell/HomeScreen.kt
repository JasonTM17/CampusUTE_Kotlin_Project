package com.campusute.app.feature.appshell

import androidx.compose.foundation.layout.Column
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

/** Profile tab (Phase 1). Schedule lives in its own tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsStateWithLifecycle()

    Column(Modifier.padding(16.dp)) {
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
            Text("Học kỳ hiện tại", style = MaterialTheme.typography.titleSmall)
            Text("HK1 2026–2027 · tuần đang diễn ra", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
