package com.campusute.app.feature.appshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.core.designsystem.components.CampusCard
import com.campusute.app.core.designsystem.components.CampusEmptyState
import com.campusute.app.core.designsystem.components.CampusErrorState
import com.campusute.app.core.designsystem.components.CampusHeroCard
import com.campusute.app.core.designsystem.components.CampusIconTile
import com.campusute.app.core.designsystem.components.CampusLeadLine
import com.campusute.app.core.designsystem.components.CampusOfflineBanner
import com.campusute.app.core.designsystem.components.CampusSectionHeader
import com.campusute.app.core.designsystem.components.CampusSkeleton
import com.campusute.app.core.designsystem.components.CampusStatChip
import com.campusute.app.core.designsystem.components.CampusStatusBadge
import com.campusute.app.core.designsystem.components.CampusTone
import com.campusute.app.core.network.AssignmentDto
import com.campusute.app.core.network.NotificationItemDto

/**
 * Trang chủ (catalogue #1): "what needs my attention today".
 *
 * Each of the three sections carries its own phase, so a failed one renders as a failure with a
 * retry and the other two stay readable. Before this, one bad response made the whole tab look
 * like a student with nothing to do.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onOpenInbox: (() -> Unit)? = null,
    onOpenGrades: (() -> Unit)? = null,
    onOpenEvents: (() -> Unit)? = null,
    onOpenTasks: (() -> Unit)? = null,
    onOpenProfile: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var submitTargetId by rememberSaveable { mutableStateOf<String?>(null) }

    val target = submitTargetId?.let { id -> state.assignments.firstOrNull { it.id == id } }
    LaunchedEffect(state.submission) {
        // The sheet owns its own rejection copy, so it only closes on an accepted submit.
        if (state.submission is SubmitOutcome.Accepted) {
            submitTargetId = null
            viewModel.clearSubmission()
        }
    }
    // A row that has since been submitted (or vanished) must not keep an orphan sheet open.
    LaunchedEffect(target) { if (submitTargetId != null && target == null) submitTargetId = null }

    // A Column, not a LazyColumn: the home digest is a short, bounded list (previews capped at
    // three), and eager composition keeps every section present for semantics tests regardless
    // of the viewport height.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.offline) {
            CampusOfflineBanner("Không có kết nối — nội dung bên dưới có thể chưa cập nhật")
        }
        ProfileSection(state, onOpenProfile) { viewModel.retry(HomeSection.Profile) }

        // v1.2 quick stats (Stitch frame): at-a-glance counters; the two that map to a
        // destination are shortcuts, the assignment count is answered by the section below.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampusStatChip(
                label = "Bài tập",
                value = state.assignments.size.toString(),
                icon = Icons.Filled.Edit,
                modifier = Modifier.weight(1f),
            )
            CampusStatChip(
                label = "Chưa đọc",
                value = "${state.unread} mới",
                icon = Icons.Filled.Notifications,
                modifier = Modifier.weight(1f),
                onClick = onOpenInbox,
            )
            CampusStatChip(
                label = "Điểm",
                value = "Xem",
                icon = Icons.Filled.Star,
                modifier = Modifier.weight(1f),
                onClick = onOpenGrades,
            )
        }

        CampusSectionHeader("Bài tập cần chú ý")
        when (state.assignmentPhase) {
            LoadPhase.Loading -> CampusSkeleton(rows = 2)
            LoadPhase.Failed -> CampusErrorState("Không tải được bài tập.", onRetry = { viewModel.retry(HomeSection.Assignments) })
            LoadPhase.Ready -> if (state.assignments.isEmpty()) {
                CampusEmptyState(
                    title = "Chưa có bài tập nào",
                    hint = "Khi giảng viên giao bài, hạn nộp sẽ hiện ở đây.",
                    icon = Icons.Filled.Notifications,
                )
            } else {
                state.assignments.forEach { assignment ->
                    AssignmentRow(assignment) { submitTargetId = assignment.id }
                }
            }
        }

        CampusSectionHeader(
            title = if (state.unread > 0) "Thông báo (${state.unread} chưa đọc)" else "Thông báo",
            actionLabel = if (onOpenInbox != null && state.inbox.isNotEmpty()) "Xem tất cả" else null,
            onAction = onOpenInbox,
        )
        when (state.inboxPhase) {
            LoadPhase.Loading -> CampusSkeleton(rows = 2)
            LoadPhase.Failed -> CampusErrorState("Không tải được thông báo.", onRetry = { viewModel.retry(HomeSection.Inbox) })
            LoadPhase.Ready -> if (state.inbox.isEmpty()) {
                CampusEmptyState(
                    title = "Hộp thư trống",
                    hint = "Điểm mới và nhắc nhở từ giảng viên sẽ xuất hiện ở đây.",
                    icon = Icons.Filled.Notifications,
                )
            } else {
                // A preview, not the inbox: the tab owns the full list and the filter chips.
                state.inbox.take(3).forEach { notification ->
                    NotificationPreviewRow(notification)
                }
            }
        }

        // Neither destination is a tab and the shell has five, so the sub-screens are entered from
        // home. They sit last: this screen's job is "what needs my attention today", and a static
        // link must not outrank live homework. It also keeps the first screenful free of chrome.
        if (onOpenGrades != null || onOpenEvents != null) {
            CampusSectionHeader("Học vụ")
            onOpenGrades?.let { open ->
                HomeLinkRow(Icons.Filled.Star, "Điểm & học phần", "Thành phần, điểm tổng kết và chữ cái từng môn", open)
            }
            onOpenEvents?.let { open ->
                HomeLinkRow(Icons.Filled.DateRange, "Sự kiện", "Sự kiện đang mở đăng ký", open)
            }
            onOpenTasks?.let { open ->
                HomeLinkRow(Icons.Filled.Done, "Công việc học tập", "Việc cần làm, đồng bộ offline-first", open)
            }
        }
    }

    val openId = submitTargetId
    if (target != null && openId != null) {
        AssignmentSubmitSheet(
            assignment = target,
            busy = state.submitBusy,
            outcome = state.submission?.takeIf { it.assignmentId == openId },
            onSubmit = { note -> viewModel.submitAssignment(openId, note) },
            onDismiss = { submitTargetId = null; viewModel.clearSubmission() },
        )
    }
}

@Composable
private fun ProfileSection(state: HomeUiState, onOpenProfile: (() -> Unit)?, onRetry: () -> Unit) {
    when (state.profilePhase) {
        LoadPhase.Loading -> CampusSkeleton(rows = 1)
        LoadPhase.Failed -> CampusErrorState("Không tải được hồ sơ sinh viên.", onRetry = onRetry)
        LoadPhase.Ready -> {
            val user = state.user
            // The hero doubles as the entry to Hồ sơ & Cài đặt (v1.3).
            CampusHeroCard(
                fullName = user?.fullName ?: "sinh viên",
                profileLine = user?.let { "${it.studentCode ?: "-"} · ${it.department ?: "-"}" } ?: "",
                onClick = onOpenProfile,
            )
        }
    }
}

/** Học vụ entry (v1.2 Stitch frame): tonal icon tile + two-line text + chevron. */
@Composable
private fun HomeLinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    CampusCard(onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CampusIconTile(icon)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssignmentRow(assignment: AssignmentDto, onSubmit: () -> Unit) {
    val badge = badgeFor(dueStatus(assignment.submitted, assignment.dueAt))
    CampusCard(onClick = if (assignment.submitted) null else onSubmit) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CampusLeadLine(
                primary = assignment.title,
                secondary = buildString {
                    append(assignment.courseCode)
                    formatTimestamp(assignment.dueAt)?.let { append(" · Hạn nộp $it") }
                },
                modifier = Modifier.weight(1f),
            )
            CampusStatusBadge(badge.text, badge.tone)
        }
        if (assignment.description.isNotBlank()) {
            Text(
                assignment.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun NotificationPreviewRow(notification: NotificationItemDto) {
    CampusCard(tonal = true) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CampusIconTile(Icons.Filled.Notifications)
            CampusLeadLine(
                primary = notification.title,
                secondary = formatTimestamp(notification.createdAt),
                modifier = Modifier.weight(1f),
            )
            if (!notification.read) CampusStatusBadge("Mới", CampusTone.Info)
        }
    }
}
