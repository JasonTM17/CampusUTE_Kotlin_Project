package com.campusute.app.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.core.database.ScheduleSessionEntity
import com.campusute.app.core.designsystem.components.CampusCard
import com.campusute.app.core.designsystem.components.CampusEmptyState
import com.campusute.app.core.designsystem.components.CampusLoading
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayLabels = mapOf(
    DayOfWeek.MONDAY to "T2", DayOfWeek.TUESDAY to "T3", DayOfWeek.WEDNESDAY to "T4",
    DayOfWeek.THURSDAY to "T5", DayOfWeek.FRIDAY to "T6", DayOfWeek.SATURDAY to "T7",
    DayOfWeek.SUNDAY to "CN",
)

/** Week timetable with day selector, conflict badges and offline banner. */
@Composable
fun TimetableScreen(viewModel: ScheduleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        if (state.offline) {
            OfflineBanner(message = state.message ?: "Ngoại tuyến")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (0..6L).forEach { offset ->
                val date = state.weekStart.plusDays(offset)
                val selected = date == state.selectedDate
                AssistChip(
                    onClick = { viewModel.selectDay(date) },
                    label = {
                        Text(
                            "${dayLabels[date.dayOfWeek]} ${date.format(DateTimeFormatter.ofPattern("dd/MM"))}",
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    modifier = if (selected) Modifier.background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier,
                )
            }
        }
        TextButton(onClick = { viewModel.changeWeek(false) }) { Text("← Tuần trước") }
        when {
            state.loading -> CampusLoading()
            state.daySessions.isEmpty() -> CampusEmptyState(
                title = "Không có lịch học ngày này",
                hint = state.message ?: "Kéo xuống không cần thiết — bấm Tuần trước/sau để xem ngày khác.",
            )
            else -> SessionList(sessions = state.daySessions)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { viewModel.changeWeek(true) }) { Text("Tuần sau →") }
            TextButton(onClick = { viewModel.refresh() }, enabled = !state.syncing) {
                Text(if (state.syncing) "Đang đồng bộ..." else "Làm mới")
            }
        }
    }
}

@Composable
private fun SessionList(sessions: List<ScheduleSessionEntity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sessions, key = { it.id }) { session ->
            CampusCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${session.startAt.substring(11, 16)} – ${session.endAt.substring(11, 16)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (session.conflict) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) { Text("Trùng lịch") }
                    }
                }
                Text("${session.courseCode} · ${session.courseName}", style = MaterialTheme.typography.bodyLarge)
                Text(session.lecturerName, style = MaterialTheme.typography.bodySmall)
                Text("Phòng ${session.room} — tòa ${session.building}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OfflineBanner(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF59E0B))
            .padding(8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall)
    }
}
