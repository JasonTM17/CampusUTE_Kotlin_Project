package com.campusute.app.feature.appshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.core.designsystem.components.CampusCard
import com.campusute.app.core.designsystem.components.CampusEmptyState
import com.campusute.app.core.designsystem.components.CampusErrorState
import com.campusute.app.core.designsystem.components.CampusFilterChips
import com.campusute.app.core.designsystem.components.CampusLeadLine
import com.campusute.app.core.designsystem.components.CampusOfflineBanner
import com.campusute.app.core.designsystem.components.CampusSectionHeader
import com.campusute.app.core.designsystem.components.CampusSkeleton
import com.campusute.app.core.designsystem.components.CampusStatusBadge
import com.campusute.app.core.designsystem.components.CampusTone
import com.campusute.app.core.network.NotificationItemDto

/**
 * The triage inbox (catalogue #5).
 *
 * `type` has always been delivered by `NotificationDto` and never rendered, which made a grade and
 * a system notice look identical. The chips are single-choice because "unread AND grade" is a
 * second axis the frame does not have; unread status is shown by the section split instead.
 *
 * Tap means *mark read* and nothing else — the DTO carries no source id, so there is no deep link
 * to design.
 */
private enum class InboxFilter(val label: String, val type: String? = null) {
    All("Tất cả"),
    Unread("Chưa đọc"),
    Grade("Điểm", "GRADE"),
    Assignment("Bài tập", "ASSIGNMENT"),
    System("Hệ thống", "SYSTEM"),
}

@Composable
fun NotificationList(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(InboxFilter.All) }

    Column(Modifier.fillMaxSize()) {
        if (state.offline) {
            CampusOfflineBanner("Không có kết nối — danh sách có thể chưa cập nhật")
        }
        CampusFilterChips(
            options = InboxFilter.entries,
            selected = filter,
            onSelect = { filter = it },
            labelFor = { it.label },
            modifier = Modifier.padding(vertical = 8.dp),
        )
        state.inboxActionError?.let { message ->
            CampusErrorState(
                message = message,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                onRetry = { viewModel.retry(HomeSection.Inbox) },
            )
        }

        when (state.inboxPhase) {
            LoadPhase.Loading -> CampusSkeleton(rows = 3)
            LoadPhase.Failed -> CampusErrorState(
                message = "Không tải được hộp thông báo.",
                onRetry = { viewModel.retry(HomeSection.Inbox) },
                modifier = Modifier.padding(12.dp),
            )
            LoadPhase.Ready -> {
                val visible = state.inbox.filter { matches(filter, it) }
                if (visible.isEmpty()) {
                    CampusEmptyState(
                        title = if (filter == InboxFilter.All) "Hộp thư trống" else "Không có mục nào theo bộ lọc này",
                        hint = if (filter == InboxFilter.All) {
                            "Điểm mới và nhắc nhở từ giảng viên sẽ xuất hiện ở đây."
                        } else {
                            "Thử chọn \"Tất cả\" để xem toàn bộ hộp thư."
                        },
                        icon = Icons.Filled.Notifications,
                    )
                } else {
                    val unreadRows = visible.filterNot { it.read }
                    val readRows = visible.filter { it.read }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (unreadRows.isNotEmpty()) {
                            item {
                                CampusSectionHeader(
                                    title = "Chưa đọc (${unreadRows.size})",
                                    actionLabel = "Đánh dấu tất cả",
                                    onAction = viewModel::markAllRead,
                                )
                            }
                            items(unreadRows, key = { it.id }) { row ->
                                NotificationRow(row) { viewModel.markRead(row) }
                            }
                        }
                        if (readRows.isNotEmpty()) {
                            item { CampusSectionHeader(title = "Đã đọc") }
                            items(readRows, key = { it.id }) { row ->
                                NotificationRow(row, onOpen = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun matches(filter: InboxFilter, row: NotificationItemDto): Boolean = when (filter) {
    InboxFilter.All -> true
    InboxFilter.Unread -> !row.read
    else -> row.type == filter.type
}

@Composable
private fun NotificationRow(
    notification: NotificationItemDto,
    onOpen: (() -> Unit)?,
) {
    CampusCard(modifier = Modifier, onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CampusLeadLine(
                primary = notification.title,
                secondary = formatTimestamp(notification.createdAt),
                modifier = Modifier.weight(1f),
            )
            CampusStatusBadge(typeLabel(notification.type), toneFor(notification.type))
        }
        if (notification.body.isNotBlank()) {
            Text(notification.body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The vocabulary is fixed by `Notification.type` on the backend: GRADE | ASSIGNMENT | SYSTEM. */
private fun typeLabel(type: String): String = when (type) {
    "GRADE" -> "Điểm"
    "ASSIGNMENT" -> "Bài tập"
    else -> "Hệ thống"
}

private fun toneFor(type: String): CampusTone = when (type) {
    "GRADE" -> CampusTone.Info
    "ASSIGNMENT" -> CampusTone.Warning
    else -> CampusTone.Neutral
}
