package com.campusute.app.feature.appshell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.core.designsystem.components.CampusCard

@Composable
fun AssignmentsSection(viewModel: HomeViewModel = hiltViewModel()) {
    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val submitBusy by viewModel.submitBusy.collectAsStateWithLifecycle()

    Column(Modifier.padding(vertical = 4.dp)) {
        Text("Bài tập", style = MaterialTheme.typography.titleMedium,
             modifier = Modifier.padding(bottom = 8.dp))
        if (assignments.isEmpty()) {
            Text("Chưa có bài tập nào.", style = MaterialTheme.typography.bodySmall)
        } else {
            assignments.forEach { a ->
                var showSubmit by remember { mutableStateOf(false) }
                CampusCard {
                    Text("${a.courseCode} · ${a.title}", style = MaterialTheme.typography.titleSmall)
                    if (a.dueAt != null) Text("Hạn: ${a.dueAt.substring(0, 16).replace('T', ' ')}",
                                              style = MaterialTheme.typography.bodySmall)
                    Text(if (a.submitted) "✓ Đã nộp" else "● Chưa nộp",
                         style = MaterialTheme.typography.labelMedium)
                    if (!a.submitted) {
                        TextButton(onClick = {
                            if (!showSubmit) showSubmit = true
                            else { viewModel.submitAssignment(a.id) { showSubmit = false } }
                        }, enabled = !submitBusy) {
                            Text(if (showSubmit) "Xác nhận nộp?" else "Nộp bài")
                        }
                    }
                }
            }
        }
    }
}
