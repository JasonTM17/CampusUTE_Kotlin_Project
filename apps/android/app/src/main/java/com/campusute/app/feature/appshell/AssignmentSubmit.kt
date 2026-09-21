package com.campusute.app.feature.appshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.campusute.app.core.designsystem.components.CampusButton
import com.campusute.app.core.designsystem.components.CampusErrorState
import com.campusute.app.core.designsystem.components.CampusStatusBadge
import com.campusute.app.core.designsystem.components.CampusTone
import com.campusute.app.core.designsystem.components.CampusTextField
import com.campusute.app.core.network.AssignmentDto

/** The backend stores `note.take(5000)`; the counter has to agree with the server, not with taste. */
const val SUBMISSION_NOTE_LIMIT = 5000

/**
 * Submit sheet (catalogue #4). Submission is a text note — there is no upload endpoint — so the
 * sheet says so where a student would otherwise look for a paperclip, and it surfaces the server's
 * own refusal ("Đã quá hạn nộp bài", "Bạn không thuộc lớp học phần này") instead of a generic retry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentSubmitSheet(
    assignment: AssignmentDto,
    busy: Boolean,
    outcome: SubmitOutcome?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by rememberSaveable(assignment.id) { mutableStateOf("") }

    val rejection = (outcome as? SubmitOutcome.Rejected)
        ?.takeIf { it.assignmentId == assignment.id }
        ?.message
    val offlineFailure = (outcome as? SubmitOutcome.Offline)
        ?.takeIf { it.assignmentId == assignment.id }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Nộp bài: ${assignment.title}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                val badge = badgeFor(dueStatus(assignment.submitted, assignment.dueAt))
                CampusStatusBadge(badge.text, badge.tone)
            }
            Text(
                "${assignment.courseCode}" + (formatTimestamp(assignment.dueAt)?.let { " · Hạn nộp $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CampusTextField(
                value = note,
                onValueChange = { note = it.take(SUBMISSION_NOTE_LIMIT) },
                label = "Nội dung nộp bài",
                singleLine = false,
                minLines = 4,
                supportingText = "$note/$SUBMISSION_NOTE_LIMIT · chỉ nhận nội dung văn bản, không tải tệp lên",
            )
            rejection?.let {
                CampusErrorState(it)
            }
            offlineFailure?.let {
                CampusErrorState("Không có kết nối — bài chưa được nộp.")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Hủy") }
                CampusButton(
                    text = if (busy) "Đang nộp..." else "Nộp bài",
                    onClick = { onSubmit(note.trim()) },
                    // A blank note is not a submission the server would accept, so the affordance
                    // stays off rather than firing a request that can only be refused.
                    enabled = !busy && note.isNotBlank(),
                    modifier = Modifier.weight(2f),
                )
            }
        }
    }
}
