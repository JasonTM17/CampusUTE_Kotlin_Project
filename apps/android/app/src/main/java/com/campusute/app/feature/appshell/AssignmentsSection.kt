package com.campusute.app.feature.appshell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.core.designsystem.components.CampusCard
import com.campusute.app.core.designsystem.components.CampusEmptyState

/** Assignments section (student view): list per enrolled section with submitted state. */
@Composable
fun AssignmentsSection(viewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val assignments by viewModel.assignments.collectAsStateWithLifecycle()

    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            "Bài tập",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (assignments.isEmpty()) {
            CampusEmptyState("Chưa có bài tập nào", "Bài tập do giảng viên đăng cho các lớp bạn tham gia.")
        } else {
            assignments.forEach { assignment ->
                CampusCard {
                    Text(
                        "${assignment.courseCode} · ${assignment.title}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (assignment.dueAt != null) {
                        Text(
                            "Hạn: ${assignment.dueAt.substring(0, 16).replace('T', ' ')}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        if (assignment.submitted) "✓ Đã nộp" else "● Chưa nộp",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
