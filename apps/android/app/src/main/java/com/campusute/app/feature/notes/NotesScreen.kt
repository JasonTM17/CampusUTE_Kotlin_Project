package com.campusute.app.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.campusute.app.core.designsystem.components.CampusButton
import com.campusute.app.core.designsystem.components.CampusCard
import com.campusute.app.core.designsystem.components.CampusEmptyState
import com.campusute.app.core.designsystem.components.CampusErrorState
import com.campusute.app.core.designsystem.components.CampusLeadLine
import com.campusute.app.core.designsystem.components.CampusSectionHeader
import com.campusute.app.core.designsystem.components.CampusSkeleton
import com.campusute.app.core.designsystem.components.CampusTextField
import com.campusute.app.core.network.NoteDto
import com.campusute.app.feature.appshell.formatTimestamp

/**
 * Tab "Ghi chú" (catalogue #7). AI tóm tắt là propose-only (Ruling R-B): kết quả chỉ vào được
 * draft khi người dùng bấm "Chèn vào ghi chú".
 *
 * The sparkle glyph is gone — §3 of the design contract bans emoji as iconography, and a summary
 * that failed used to report itself in the same slot as a list that failed to load.
 */
@Composable
fun NotesScreen(viewModel: NotesViewModel = hiltViewModel()) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val proposal by viewModel.proposal.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val summarizeError by viewModel.summarizeError.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val summarizing by viewModel.summarizing.collectAsStateWithLifecycle()
    val dirty by viewModel.dirty.collectAsStateWithLifecycle()
    var deleteTarget by rememberSaveable { mutableStateOf<NoteDto?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val current = draft
    if (current == null) {
        NoteList(
            notes = notes,
            loading = loading,
            error = error,
            onNew = viewModel::openNew,
            onRetry = viewModel::load,
            onEdit = viewModel::openEdit,
            onDelete = { deleteTarget = it },
        )
    } else {
        NoteEditor(
            draft = current,
            busy = busy,
            summarizing = summarizing,
            error = error,
            summarizeError = summarizeError,
            dirty = dirty,
            onTitleChange = viewModel::onTitleChange,
            onContentChange = viewModel::onContentChange,
            onSummarize = viewModel::summarize,
            onSave = viewModel::save,
            onClose = { if (dirty) confirmDiscard = true else viewModel.closeEditor() },
        )
    }

    proposal?.let { p ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissProposal() },
            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
            title = { Text("AI đề xuất tóm tắt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        // Server text stays plain: no styling here may imply the app vouched for it.
                        p.summary.ifBlank { "(AI không tạo được tóm tắt từ nội dung này.)" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Bản tóm tắt chỉ là đề xuất — nó chỉ vào ghi chú khi bạn bấm Chèn vào.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.acceptProposal() }) { Text("Chèn vào ghi chú") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissProposal() }) { Text("Bỏ qua") }
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard bản nháp?") },
            text = { Text("Nội dung chưa lưu sẽ mất.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; viewModel.closeEditor() }) { Text("Vứt bỏ") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Tiếp tục sửa") } },
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

@Composable
private fun NoteList(
    notes: List<NoteDto>,
    loading: Boolean,
    error: String?,
    onNew: () -> Unit,
    onRetry: () -> Unit,
    onEdit: (NoteDto) -> Unit,
    onDelete: (NoteDto) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CampusSectionHeader("Ghi chú của bạn", modifier = Modifier.weight(1f))
                TextButton(onClick = onNew) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Ghi chú mới", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        when {
            loading && notes.isEmpty() -> item { CampusSkeleton(rows = 3) }
            error != null -> item { CampusErrorState(error, onRetry = onRetry) }
            notes.isEmpty() -> item {
                CampusEmptyState(
                    title = "Chưa có ghi chú nào",
                    hint = "Ghi lại bài giảng rồi dùng AI tóm tắt khi cần ôn nhanh.",
                    icon = Icons.Filled.List,
                )
            }
            else -> items(notes, key = { it.id }) { note ->
                CampusCard(onClick = { onEdit(note) }) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CampusLeadLine(
                            primary = note.title,
                            secondary = formatTimestamp(note.updatedAt),
                            modifier = Modifier.weight(1f),
                        )
                        IconButtonSmall(onClick = { onDelete(note) })
                    }
                    if (note.content.isNotBlank()) {
                        Text(
                            note.content.take(160),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconButtonSmall(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Filled.Delete, contentDescription = "Xóa ghi chú", modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun NoteEditor(
    draft: NoteDraft,
    busy: Boolean,
    summarizing: Boolean,
    error: String?,
    summarizeError: String?,
    dirty: Boolean,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onSummarize: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CampusSectionHeader(if (draft.id == null) "Ghi chú mới" else "Sửa ghi chú")
        CampusTextField(
            value = draft.title,
            onValueChange = onTitleChange,
            label = "Tiêu đề",
            errorText = error?.takeIf { draft.title.isBlank() },
        )
        CampusTextField(
            value = draft.content,
            onValueChange = onContentChange,
            label = "Nội dung",
            singleLine = false,
            minLines = 6,
        )
        TextButton(
            onClick = onSummarize,
            enabled = draft.content.isNotBlank() && !summarizing,
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(if (summarizing) "Đang tóm tắt..." else "AI tóm tắt", modifier = Modifier.padding(start = 6.dp))
        }
        summarizeError?.let {
            CampusErrorState(it, onRetry = onSummarize, retryLabel = "Thử lại với AI")
        }
        if (error != null && draft.title.isNotBlank()) {
            CampusErrorState(error)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                Text(if (dirty) "Đóng (chưa lưu)" else "Đóng")
            }
            CampusButton(
                text = if (busy) "Đang lưu..." else "Lưu",
                onClick = onSave,
                enabled = !busy,
                modifier = Modifier.weight(2f),
            )
        }
    }
}
