package com.campusute.app.feature.appshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.core.designsystem.components.CampusCard

/** Profile tab (Phase 1) + offline-first study tasks (Phase 3 sync engine). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    tasksViewModel: TasksViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val tasksState by tasksViewModel.uiState.collectAsStateWithLifecycle()
    val tasks by tasksViewModel.tasks.collectAsStateWithLifecycle()

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        if (tasksState.offline) {
            Text(
                "Ngoại tuyến — thay đổi sẽ tự đồng bộ khi có mạng",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        CampusCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tasksState.newTitle,
                    onValueChange = tasksViewModel::onTitleChange,
                    label = { Text("Việc cần làm") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                TextButton(onClick = tasksViewModel::addTask) { Text("Thêm") }
            }
            if (tasks.isEmpty()) {
                Text(
                    "Chưa có việc nào — thêm khi offline để thử sync engine.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            tasks.forEach { task ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = task.done, onCheckedChange = { tasksViewModel.toggleDone(task) })
                    Text(
                        task.title,
                        modifier = Modifier.weight(1f),
                        style = if (task.done) MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough)
                        else MaterialTheme.typography.bodyMedium,
                    )
                    if (task.version == 0L) {
                        Text("chưa đồng bộ", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { tasksViewModel.delete(task) }) { Text("Xóa") }
                }
            }
            TextButton(onClick = { tasksViewModel.sync() }, enabled = !tasksState.syncing) {
                Text(if (tasksState.syncing) "Đang đồng bộ..." else "Đồng bộ ngay")
            }
        }
    }
}
