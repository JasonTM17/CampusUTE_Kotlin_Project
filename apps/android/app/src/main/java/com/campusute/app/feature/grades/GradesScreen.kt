package com.campusute.app.feature.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.campusute.app.core.designsystem.components.CampusSkeleton
import com.campusute.app.core.designsystem.components.CampusStatusBadge
import com.campusute.app.core.designsystem.components.CampusTone
import com.campusute.app.feature.appshell.LoadPhase

/**
 * Bảng điểm theo học phần. Per-course totals and letters only — see [GradesViewModel] for why the
 * cumulative GPA is not computable from `grades/me` and is therefore not shown.
 */
@Composable
fun GradesScreen(viewModel: GradesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state.phase) {
            LoadPhase.Loading -> item { CampusSkeleton(rows = 3) }
            LoadPhase.Failed -> item {
                CampusErrorState(
                    message = state.message ?: "Không tải được bảng điểm.",
                    onRetry = viewModel::load,
                )
            }
            LoadPhase.Ready -> if (state.courses.isEmpty()) {
                item {
                    CampusEmptyState(
                        title = "Chưa có điểm nào",
                        hint = "Điểm sẽ hiện ở đây sau khi giảng viên cập nhật thành phần đầu tiên.",
                        icon = Icons.Filled.List,
                    )
                }
            } else {
                items(state.courses, key = { it.courseCode }) { course ->
                    CourseGradeCard(course)
                }
                item {
                    Text(
                        "Điểm do giảng viên cập nhật. Bảng điểm chính thức lấy từ cổng đào tạo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseGradeCard(course: CourseGrade) {
    CampusCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(course.courseCode, style = MaterialTheme.typography.titleSmall)
                Text(
                    course.courseName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                course.total == null -> CampusStatusBadge("Chưa đủ trọng số", CampusTone.Neutral)
                course.isFailing -> CampusStatusBadge("${course.letter} · ${fmt(course.total)}", CampusTone.Danger)
                else -> CampusStatusBadge("${course.letter} · ${fmt(course.total)}", CampusTone.Info)
            }
        }
        course.components.forEach { component ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(component.label, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${fmt(component.score)} · ${component.weightPercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun fmt(value: Double): String = if (value == value.toLong().toDouble()) {
    value.toLong().toString()
} else {
    value.toString()
}
