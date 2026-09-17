package com.campusute.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.core.designsystem.components.CampusCard
import com.campusute.app.core.designsystem.components.CampusLoading

/** AI campus assistant chat: answers + mandatory citation chips (tap = excerpt). */
@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.messages, key = { index, _ -> index }) { _, message ->
                CampusCard {
                    Text(message.text, style = MaterialTheme.typography.bodyMedium)
                    if (message.citations.isNotEmpty()) {
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            message.citations.take(3).forEach { citation ->
                                AssistChip(
                                    onClick = { },
                                    label = { Text("📄 ${citation.document} tr.${citation.page}", style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                }
            }
            if (state.busy) {
                item { CampusLoading("Trợ lý đang suy nghĩ...") }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Hỏi trợ lý AI...") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = { viewModel.send() },
                ),
            )
            TextButton(onClick = viewModel::send, enabled = !state.busy && state.input.isNotBlank()) {
                Text("Gửi")
            }
        }
    }
}
