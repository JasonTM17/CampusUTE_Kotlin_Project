package com.campusute.app.feature.appshell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.core.designsystem.components.CampusCard
import com.campusute.app.feature.appshell.AssignmentsSection
import com.campusute.app.feature.appshell.NotificationList

/** Profile + Assignments + Notifications (Sprint S1/S4). */
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()

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
        Text(
            text = "Bài tập",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        if (assignments.isEmpty()) {
            Text("Chưa có bài tập nào.", style = MaterialTheme.typography.bodySmall)
        } else {
            assignments.forEach { a ->
                CampusCard {
                    Text("${a.courseCode} · ${a.title}", style = MaterialTheme.typography.titleSmall)
                    if (a.dueAt != null) {
                        Text("Hạn: ${a.dueAt.substring(0, 16).replace('T', ' ')}",
                             style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        if (a.submitted) "✓ Đã nộp" else "● Chưa nộp",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Text(
            text = "Thông báo (${inbox.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        if (inbox.isEmpty()) {
            Text("Chưa có thông báo.", style = MaterialTheme.typography.bodySmall)
        } else {
            inbox.forEach { n ->
                CampusCard {
                    Text((if (!n.read) "● " else "") + n.title, style = MaterialTheme.typography.titleSmall)
                    if (n.body.isNotBlank()) Text(n.body, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
