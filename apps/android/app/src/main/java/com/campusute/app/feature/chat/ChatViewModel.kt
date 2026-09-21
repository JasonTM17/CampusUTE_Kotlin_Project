package com.campusute.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusute.app.core.network.AiChatRequest
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.AiNetwork
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

enum class ChatRole { USER, ASSISTANT }

enum class ChatStatus { OK, ERROR }

data class Citation(
    val document: String,
    val page: Int?,
    val excerpt: String,
    val source: String?,
)

data class ChatMessage(
    val id: Long,
    val text: String,
    val role: ChatRole,
    val status: ChatStatus = ChatStatus.OK,
    val agent: String? = null,
    val citations: List<Citation> = emptyList(),
    /** Prompt to resend when this row is an error; null otherwise. */
    val retryPrompt: String? = null,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val busy: Boolean = false,
    val offline: Boolean = false,
)

/**
 * Tool names the ai-service reports in `tools[]`, mapped to the Vietnamese agent label
 * shown on the answer. Unknown names fall through verbatim rather than being hidden.
 */
internal fun agentLabelFor(tools: List<String>): String? = when (tools.firstOrNull()) {
    "search_regulations" -> "Điều phối viên"
    "get_my_grades" -> "Agent Điểm"
    "get_my_schedule" -> "Agent Lịch học"
    null, "" -> null
    else -> tools.first()
}

/**
 * Answer text and citation excerpts originate from ingested documents, which are untrusted.
 * Strip control, bidi-override and zero-width characters so the payload cannot spoof
 * right-to-left or invisible runs in the rendered surface. Rendering stays plain `Text`.
 */
internal fun sanitizeUntrusted(raw: String): String = buildString(raw.length) {
    for (ch in raw) {
        when {
            ch == '\n' || ch == '\t' -> append(ch)
            ch.isISOControl() -> Unit
            ch in BIDI_AND_ZERO_WIDTH -> Unit
            else -> append(ch)
        }
    }
}

private val BIDI_AND_ZERO_WIDTH = setOf(
    '\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF',
    '\u200E', '\u200F', '\u061C',
    '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
    '\u2066', '\u2067', '\u2068', '\u2069',
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    @AiNetwork private val api: CampusApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private fun nextId() = ids.getAndIncrement()

    fun onInputChange(value: String) = _uiState.update { it.copy(input = value) }

    fun send() {
        val prompt = _uiState.value.input.trim()
        if (prompt.isEmpty() || _uiState.value.busy) return
        // Ids are drawn outside update{}: that lambda may run again on CAS contention, and an
        // impure body there would burn ids for a row that never exists.
        val bubble = ChatMessage(nextId(), prompt, ChatRole.USER)
        _uiState.update {
            it.copy(
                input = "",
                busy = true,
                offline = false,
                messages = it.messages + bubble,
            )
        }
        request(prompt)
    }

    /**
     * Resends the prompt behind ONE failed row, identified by id.
     * A shared "last failure" pointer made every on-screen retry button resend the newest
     * prompt and delete the newest row, so retrying an older error silently discarded a
     * different answer.
     */
    fun retry(messageId: Long) {
        val failed = _uiState.value.messages.firstOrNull { it.id == messageId } ?: return
        val prompt = failed.retryPrompt ?: return
        if (_uiState.value.busy) return
        _uiState.update {
            it.copy(
                busy = true,
                offline = false,
                messages = it.messages.filterNot { row -> row.id == messageId },
            )
        }
        request(prompt)
    }

    private fun request(prompt: String) {
        viewModelScope.launch {
            var offline = false
            val reply = try {
                // The AI client's read budget is 75s; staying inside it means a slow
                // answer surfaces as an answer, not as a spurious network failure.
                withTimeout(REPLY_BUDGET_MS) {
                    val envelope = api.aiChat(AiChatRequest(prompt))
                    val data = envelope.data
                    when {
                        data != null -> ChatMessage(
                            id = nextId(),
                            text = sanitizeUntrusted(data.answer),
                            role = ChatRole.ASSISTANT,
                            agent = agentLabelFor(data.tools),
                            citations = data.citations.map {
                                Citation(
                                    document = sanitizeUntrusted(it.document.orEmpty()).ifEmpty { "Tài liệu" },
                                    page = it.page,
                                    excerpt = sanitizeUntrusted(it.excerpt.orEmpty()),
                                    source = it.source?.let(::sanitizeUntrusted),
                                )
                            },
                        )
                        else -> errorRow(envelope.error?.message ?: "Trợ lý AI tạm không khả dụng.", prompt)
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                // TimeoutCancellationException IS a CancellationException, so without this
                // clause the rethrow below would eat the timeout and leave busy stuck true.
                errorRow(SLOW_REPLY, prompt)
            } catch (_: SocketTimeoutException) {
                errorRow(SLOW_REPLY, prompt)
            } catch (_: IOException) {
                offline = true
                errorRow("Không kết nối được trợ lý AI — kiểm tra mạng rồi thử lại.", prompt)
            } catch (http: HttpException) {
                // Non-2xx arrives as HttpException, so the server's own diagnosis (429
                // "asked too fast", 401, 400) has to be read out of the error body.
                val stated = http.response()?.errorBody()?.string()?.let(::serverMessage)
                if (http.code() == 429) {
                    errorRow(stated ?: "Bạn hỏi nhanh quá — chờ một phút rồi thử lại.", prompt, canRetry = false)
                } else {
                    errorRow(stated ?: "Trợ lý AI tạm không khả dụng — các tính năng khác vẫn hoạt động.", prompt)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                errorRow("Trợ lý AI tạm không khả dụng — các tính năng khác vẫn hoạt động.", prompt)
            }
            _uiState.update {
                it.copy(busy = false, offline = offline, messages = it.messages + reply)
            }
        }.invokeOnCompletion {
            // Belt for the composer lock: if an exception escapes the catch ladder above
            // (a re-wrapped cancellation, a bug in a future branch) busy would stay true
            // forever, and HomeShell hoists this one VM across tab switches.
            if (_uiState.value.busy) _uiState.update { it.copy(busy = false) }
        }
    }

    private fun serverMessage(body: String): String? = runCatching {
        Json.parseToJsonElement(body).jsonObject["error"]
            ?.jsonObject?.get("message")
            ?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun errorRow(
        message: String,
        prompt: String,
        canRetry: Boolean = true,
    ) = ChatMessage(
        id = nextId(),
        text = message,
        role = ChatRole.ASSISTANT,
        status = ChatStatus.ERROR,
        retryPrompt = prompt.takeIf { canRetry },
    )

    private companion object {
        const val REPLY_BUDGET_MS = 70_000L
        const val SLOW_REPLY = "Trợ lý mất quá lâu để trả lời. Hãy thử lại."
        val ids = AtomicLong(0)
    }
}
