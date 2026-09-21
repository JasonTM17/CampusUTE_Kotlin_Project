package com.campusute.app.feature.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.core.designsystem.components.CampusCard
import com.campusute.app.core.designsystem.components.CampusEmptyState
import com.campusute.app.core.designsystem.components.CampusErrorState
import com.campusute.app.core.designsystem.components.CampusLeadLine
import com.campusute.app.core.designsystem.components.CampusSkeleton
import com.campusute.app.core.designsystem.components.CampusStatusBadge
import com.campusute.app.core.designsystem.components.CampusTone
import com.campusute.app.core.network.CampusEventDto
import com.campusute.app.feature.appshell.LoadPhase
import com.campusute.app.feature.appshell.formatTimestamp

/**
 * Danh sách sự kiện. `EventDto` carries no description, so a card offers exactly one action and
 * never a "Chi tiết" affordance with nothing behind it.
 */
@Composable
fun EventsScreen(viewModel: EventsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.outcome?.let { outcome ->
            if (outcome is RegisterOutcome.Rejected) {
                item {
                    CampusErrorState(
                        message = outcome.message,
                        onRetry = {
                            state.events.firstOrNull { it.id == outcome.eventId }?.let(viewModel::register)
                        },
                    )
                }
            }
        }
        when (state.phase) {
            LoadPhase.Loading -> item { CampusSkeleton(rows = 3) }
            LoadPhase.Failed -> item {
                CampusErrorState(
                    message = state.message ?: "Không tải được sự kiện.",
                    onRetry = viewModel::load,
                )
            }
            LoadPhase.Ready -> if (state.events.isEmpty()) {
                item {
                    CampusEmptyState(
                        title = "Chưa có sự kiện nào",
                        hint = "Khi phòng công tác sinh viên mở đăng ký, sự kiện sẽ hiện ở đây.",
                        icon = Icons.Filled.DateRange,
                    )
                }
            } else {
                items(state.events, key = { it.id }) { event ->
                    EventCard(
                        event = event,
                        busy = state.registeringId == event.id,
                        justRegistered = (state.outcome as? RegisterOutcome.Registered)?.eventId == event.id,
                        onRegister = { viewModel.register(event) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: CampusEventDto,
    busy: Boolean,
    justRegistered: Boolean,
    onRegister: () -> Unit,
) {
    val closed = isPast(event)
    CampusCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CampusLeadLine(
                primary = event.title,
                secondary = buildString {
                    append(event.code)
                    formatTimestamp(event.startsAt)?.let { append(" · $it") }
                    event.location?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                },
                modifier = Modifier.weight(1f),
            )
            when {
                event.registered -> CampusStatusBadge("Đã ghi danh", CampusTone.Success)
                closed -> CampusStatusBadge("Đã kết thúc", CampusTone.Neutral)
                event.seatsLeft <= 0L -> CampusStatusBadge("Hết chỗ", CampusTone.Danger)
                else -> CampusStatusBadge("${event.seatsLeft} chỗ", CampusTone.Info)
            }
        }
        if (!event.registered && !closed && event.seatsLeft > 0L) {
            TextButton(
                onClick = onRegister,
                enabled = !busy,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(if (busy) "Đang ghi danh..." else "Ghi danh")
            }
        }
        if (justRegistered) {
            Text(
                "Đã giữ chỗ cho bạn.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
