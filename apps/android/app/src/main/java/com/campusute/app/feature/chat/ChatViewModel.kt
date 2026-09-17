package com.campusute.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusute.app.core.network.CampusApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Citation(val document: String, val page: Int, val excerpt: String)

data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val citations: List<Citation> = emptyList(),
)

data class ChatUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage("Xin chào! Hỏi mình về lịch học, điểm, quy chế hoặc tài liệu của trường nhé.", fromUser = false),
    ),
    val input: String = "",
    val busy: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val api: CampusApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputChange(value: String) = _uiState.update { it.copy(input = value) }

    fun send() {
        val state = _uiState.value
        val message = state.input.trim()
        if (message.isEmpty() || state.busy) return
        _uiState.update {
            it.copy(
                input = "",
                busy = true,
                messages = it.messages + ChatMessage(message, fromUser = true),
            )
        }
        viewModelScope.launch {
            val reply = try {
                kotlinx.coroutines.withTimeout(65_000) {
                    val envelope = api.aiChat(com.campusute.app.core.network.AiChatRequest(message))
                    val data = envelope.data
                    if (data != null) {
                        ChatMessage(
                            text = data.answer,
                            fromUser = false,
                            citations = data.citations.map { Citation(it.document ?: "?", it.page ?: 1, it.excerpt ?: "") },
                        )
                    } else {
                        ChatMessage(envelope.error?.message ?: "AI không trả lời được lúc này.", fromUser = false)
                    }
                }
            } catch (_: java.io.IOException) {
                ChatMessage("Không kết nối được trợ lý AI (offline).", fromUser = false)
            } catch (_: Exception) {
                ChatMessage("Trợ lý AI tạm không khả dụng — các tính năng khác vẫn hoạt động.", fromUser = false)
            }
            _uiState.update { it.copy(busy = false, messages = it.messages + reply) }
        }
    }
}
