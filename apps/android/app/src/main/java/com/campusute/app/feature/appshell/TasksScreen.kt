package com.campusute.app.feature.appshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.campusute.app.core.data.TaskConflict
import com.campusute.app.core.database.StudyTaskEntity
import com.campusute.app.core.designsystem.components.CampusButton
import com.campusute.app.core.designsystem.components.CampusCard
import com.campusute.app.core.designsystem.components.CampusEmptyState
import com.campusute.app.core.designsystem.components.CampusOfflineBanner
import com.campusute.app.core.designsystem.components.CampusSectionHeader
import com.campusute.app.core.designsystem.components.CampusStatusBadge
import java.time.LocalDate

/**
 * Catalogue #2 (study-tasks): offline-first study task list. The conflict card
 * is the frame's centrepiece — a sync that lost a race must offer BOTH
 * resolutions, never silently pick the server.
 */
@Composable
fun TasksScreen(viewModel: TasksViewModel = hiltViewModel()) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (uiState.offline) {
            CampusOfflineBanner(message = "Ngoại tuyến — việc thêm/sửa sẽ đồng bộ khi có mạng")
        }

        conflicts.forEach { conflict ->
            CampusCard {
                Text("Xung đột đồng bộ", style = MaterialTheme.typography.titleSmall)
                Text(
                    conflict.op.title ?: conflict.serverTask.title,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Máy chủ có phiên bản mới hơn (v${conflict.serverTask.version}).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row {
                    TextButton(onClick = { viewModel.resolveConflict(conflict, keepMine = true) }) {
                        Text("Giữ phiên bản của tôi")
                    }
                    TextButton(onClick = { viewModel.resolveConflict(conflict, keepMine = false) }) {
                        Text("Nhận phiên bản máy chủ")
                    }
                }
            }
        }

        CampusSectionHeader(
            title = "Công việc học tập",
            actionLabel = if (uiState.syncing) null else "Đồng bộ",
            onAction = if (uiState.syncing) null else ({ viewModel.sync() }),
        )

        if (tasks.isEmpty() && conflicts.isEmpty()) {
            CampusEmptyState(title = "Chưa có việc nào", hint = "Thêm việc đầu tiên ở dưới.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        pending = uiState.pending > 0,
                        onToggle = { viewModel.toggleDone(task) },
                        onDelete = { viewModel.delete(task) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = uiState.newTitle,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Việc cần làm") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(onClick = { viewModel.addTask() }, enabled = uiState.newTitle.isNotBlank()) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
        CampusButton(text = "Thêm việc", onClick = { viewModel.addTask() })
    }
}

@Composable
private fun TaskRow(
    task: StudyTaskEntity,
    pending: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    CampusCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                )
                task.dueDate?.let { due ->
                    val today = LocalDate.now().toString()
                    val row: (@Composable () -> Unit) = {
                        when {
                            !task.done && due < today -> CampusStatusBadge("Quá hạn", com.campusute.app.core.designsystem.components.CampusTone.Danger)
                            !task.done && due == today -> CampusStatusBadge("Hôm nay", com.campusute.app.core.designsystem.components.CampusTone.Info)
                            else -> Text(due, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    row()
                }
            }
            if (pending) {
                CampusStatusBadge("Chưa đồng bộ", com.campusute.app.core.designsystem.components.CampusTone.Neutral)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null)
            }
        }
    }
}
