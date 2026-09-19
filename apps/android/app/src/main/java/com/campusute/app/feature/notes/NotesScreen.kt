package com.campusute.app.feature.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.campusute.app.core.network.NoteDto

/**
 * Tab "Ghi chú": list + CRUD + AI tóm tắt propose-only (R-A/R-B). Đủ 5 trạng
 * thái UI: loading / empty / error(+retry) / content / busy (nút lưu và tóm tắt).
 */
@Composable
fun NotesScreen(viewModel: NotesViewModel = hiltViewModel()) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val proposal by viewModel.proposal.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val summarizing by viewModel.summarizing.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<NoteDto?>(null) }

    val current = draft
    if (current == null) {
        Column(Modifier.padding(16.dp)) {
            Button(onClick = { viewModel.openNew() }) { Text("Ghi chú mới") }
            when {
                loading -> Text("Đang tải...", style = MaterialTheme.typography.bodyMedium)
                error != null -> Column {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { viewModel.load() }) { Text("Thử lại") }
                }
                notes.isEmpty() -> Text("Chưa có ghi chú nào.", style = MaterialTheme.typography.bodyMedium)
                else -> LazyColumn(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        CampusCard {
                            Column(Modifier.padding(12.dp)) {
                                Text(note.title, style = MaterialTheme.typography.titleSmall)
                                if (note.content.isNotBlank()) {
                                    Text(
                                        note.content.take(120),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                    )
                                }
                                Text(
                                    note.updatedAt.substring(0, 16).replace('T', ' '),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Row {
                                    TextButton(onClick = { viewModel.openEdit(note) }) { Text("Sửa") }
                                    TextButton(onClick = { deleteTarget = note }) { Text("Xóa") }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = current.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Tiêu đề") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = current.content,
                onValueChange = viewModel::onContentChange,
                label = { Text("Nội dung") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            TextButton(
                onClick = { viewModel.summarize() },
                enabled = current.content.isNotBlank() && !summarizing,
            ) {
                Text(if (summarizing) "Đang tóm tắt..." else "✨ AI tóm tắt")
            }
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row {
                Button(onClick = { viewModel.save() }, enabled = !busy) {
                    Text(if (busy) "Đang lưu..." else "Lưu")
                }
                TextButton(onClick = { viewModel.closeEditor() }) { Text("Đóng") }
            }
        }
    }

    proposal?.let { p ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissProposal() },
            title = { Text("AI đề xuất tóm tắt") },
            text = { Text(p.summary.ifBlank { "(AI không tạo được tóm tắt từ nội dung này.)" }) },
            confirmButton = {
                TextButton(onClick = { viewModel.acceptProposal() }) { Text("Chèn vào ghi chú") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissProposal() }) { Text("Bỏ qua") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Xóa ghi chú?") },
            text = { Text(target.title) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(target); deleteTarget = null }) { Text("Xóa") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Giữ") } },
        )
    }
}
