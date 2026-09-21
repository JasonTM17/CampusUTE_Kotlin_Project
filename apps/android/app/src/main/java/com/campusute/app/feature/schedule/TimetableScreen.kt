package com.campusute.app.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.core.database.ScheduleSessionEntity
import com.campusute.app.core.designsystem.components.CampusCard
import com.campusute.app.core.designsystem.components.CampusEmptyState
import com.campusute.app.core.designsystem.components.CampusErrorState
import com.campusute.app.core.designsystem.components.CampusLeadLine
import com.campusute.app.core.designsystem.components.CampusLoading
import com.campusute.app.core.designsystem.components.CampusOfflineBanner
import com.campusute.app.core.designsystem.components.CampusSectionHeader
import com.campusute.app.core.designsystem.components.CampusStatusBadge
import com.campusute.app.core.designsystem.components.CampusTone
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayLabels = mapOf(
    DayOfWeek.MONDAY to "T2", DayOfWeek.TUESDAY to "T3", DayOfWeek.WEDNESDAY to "T4",
    DayOfWeek.THURSDAY to "T5", DayOfWeek.FRIDAY to "T6", DayOfWeek.SATURDAY to "T7",
    DayOfWeek.SUNDAY to "CN",
)

private val shortDate = DateTimeFormatter.ofPattern("dd/MM")

/**
 * Tuần học (catalogue #6): the week first, the day second.
 *
 * The screen used to render one day at a time, so a clash between Tuesday and Thursday was only
 * visible if the student happened to tap both. The grid now shows all seven columns, and a
 * conflicting session is outlined in the error hue wherever it appears.
 */
@Composable
fun TimetableScreen(viewModel: ScheduleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val byDay = state.weekSessions.groupBy { runCatching { LocalDate.parse(it.date) }.getOrNull() }

    Column(Modifier.fillMaxSize()) {
        if (state.offline) {
            CampusOfflineBanner()
        }
        state.message?.let {
            CampusErrorState(it, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), onRetry = viewModel::refresh)
        }
        WeekStrip(
            weekStart = state.weekStart,
            today = state.today,
            selected = state.selectedDate,
            counts = byDay.mapValues { (_, sessions) -> sessions.size },
            conflicts = byDay.mapValues { (_, sessions) -> sessions.any { it.conflict } },
            onSelect = viewModel::selectDay,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { viewModel.changeWeek(false) }) { Text("Tuần trước") }
            Text(
                "${state.weekStart.format(shortDate)} - ${state.weekStart.plusDays(6).format(shortDate)}",
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(onClick = { viewModel.changeWeek(true) }) { Text("Tuần sau") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = viewModel::refresh, enabled = !state.syncing) {
                Text(if (state.syncing) "Đang đồng bộ..." else "Làm mới")
            }
        }

        when {
            state.weekIsEmpty -> CampusEmptyState(
                title = "Tuần này không có lịch học",
                hint = "Bấm Tuần trước/Tuần sau để xem ngày khác, hoặc Làm mới để tải lại từ cổng trường.",
                icon = Icons.Filled.DateRange,
            )
            state.loading -> CampusLoading()
            else -> DayDetail(
                date = state.selectedDate,
                sessions = byDay[state.selectedDate].orEmpty(),
            )
        }
    }
}

@Composable
private fun WeekStrip(
    weekStart: LocalDate,
    today: LocalDate,
    selected: LocalDate,
    counts: Map<LocalDate?, Int>,
    conflicts: Map<LocalDate?, Boolean>,
    onSelect: (LocalDate) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (0L..6L).forEach { offset ->
            val date = weekStart.plusDays(offset)
            val isSelected = date == selected
            val sessionCount = counts[date] ?: 0
            val box = RoundedCornerShape(12.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            date == today -> MaterialTheme.colorScheme.surfaceContainerHigh
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        box,
                    )
                    .border(
                        width = if (conflicts[date] == true) 2.dp else 0.dp,
                        color = if (conflicts[date] == true) MaterialTheme.colorScheme.error else Color.Transparent,
                        shape = box,
                    )
                    .clickable { onSelect(date) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    dayLabels[date.dayOfWeek] ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    date.format(DateTimeFormatter.ofPattern("dd")),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (sessionCount > 0) "$sessionCount lớp" else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayDetail(date: LocalDate, sessions: List<ScheduleSessionEntity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            CampusSectionHeader(
                title = "${dayLabels[date.dayOfWeek]} ${date.format(shortDate)}" +
                    if (date == LocalDate.now()) " · hôm nay" else "",
            )
        }
        if (sessions.isEmpty()) {
            item {
                CampusEmptyState(
                    title = "Không có lịch học ngày này",
                    hint = "Chọn một ngày khác trên dải lịch để xem tiết học.",
                    icon = Icons.Filled.DateRange,
                )
            }
        } else {
            items(sessions, key = { it.id }) { session -> SessionCard(session) }
        }
    }
}

@Composable
private fun SessionCard(session: ScheduleSessionEntity) {
    CampusCard(
        modifier = if (session.conflict) {
            Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
        } else {
            Modifier
        },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${hhmm(session.startAt)} – ${hhmm(session.endAt)}", style = MaterialTheme.typography.titleSmall)
            if (session.conflict) CampusStatusBadge("Trùng lịch", CampusTone.Danger)
        }
        CampusLeadLine(
            primary = "${session.courseCode} · ${session.courseName}",
            secondary = session.lecturerName,
        )
        Text(
            "Phòng ${session.room} — tòa ${session.building}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "2026-09-21T07:00:00Z" -> "07:00". A malformed clock must not take the timetable down. */
private fun hhmm(raw: String): String = if (raw.length >= 16) raw.substring(11, 16) else raw
