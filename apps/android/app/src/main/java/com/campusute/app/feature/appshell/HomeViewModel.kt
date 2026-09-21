package com.campusute.app.feature.appshell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusute.app.core.data.SessionRepository
import com.campusute.app.core.network.AssignmentDto
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.InboxDto
import com.campusute.app.core.network.NotificationItemDto
import com.campusute.app.core.network.UserDto
import com.campusute.app.core.network.envelopeMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

/**
 * What one home section currently knows. [Failed] is a distinct phase on purpose: the previous
 * shape of this ViewModel wrapped every call in `runCatching { }.onSuccess { }`, so a 500 left the
 * list empty and the screen printed "Chưa có…" — a server outage was rendered as a quiet student.
 */
enum class LoadPhase { Loading, Ready, Failed }

data class HomeUiState(
    val user: UserDto? = null,
    val profilePhase: LoadPhase = LoadPhase.Loading,
    val assignments: List<AssignmentDto> = emptyList(),
    val assignmentPhase: LoadPhase = LoadPhase.Loading,
    val inbox: List<NotificationItemDto> = emptyList(),
    val inboxPhase: LoadPhase = LoadPhase.Loading,
    val unread: Long = 0,
    val submitBusy: Boolean = false,
    val submission: SubmitOutcome? = null,
    /** A mark-read refusal. Kept separate from [inboxPhase] — the list loaded, the action failed. */
    val inboxActionError: String? = null,
    /** True when the last attempt could not reach the server at all, for the shared banner. */
    val offline: Boolean = false,
)

/** Result of one submit attempt, keyed by assignment so a stale sheet cannot show another row's verdict. */
sealed interface SubmitOutcome {
    val assignmentId: String

    data class Accepted(override val assignmentId: String) : SubmitOutcome
    data class Rejected(override val assignmentId: String, val message: String) : SubmitOutcome
    data class Offline(override val assignmentId: String) : SubmitOutcome
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: CampusApi,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    profilePhase = LoadPhase.Loading,
                    assignmentPhase = LoadPhase.Loading,
                    inboxPhase = LoadPhase.Loading,
                )
            }
            val profile = runLoad { api.me() }
            val assignments = runLoad { api.assignmentsMe() }
            val inbox = runLoad { api.notifications() }
            val inboxData = inbox.ok()
            _uiState.update { state ->
                state.copy(
                    user = profile.ok() ?: state.user,
                    profilePhase = profile.phase(),
                    assignments = assignments.ok() ?: state.assignments,
                    assignmentPhase = assignments.phase(),
                    inbox = inboxData?.notifications ?: state.inbox,
                    inboxPhase = inbox.phase(),
                    unread = inboxData?.unread ?: state.unread,
                    offline = listOf(profile, assignments, inbox).any { it is LoadOutcome.Offline },
                )
            }
        }
    }

    /** Retry one section without disturbing the two that loaded. */
    fun retry(section: HomeSection) {
        viewModelScope.launch {
            // A retry re-probes the network, so the stale-data claim from the last attempt is
            // dropped up front rather than OR-ed forward forever.
            _uiState.update { it.copy(offline = false) }
            when (section) {
                HomeSection.Profile -> {
                    _uiState.update { it.copy(profilePhase = LoadPhase.Loading) }
                    val outcome = runLoad { api.me() }
                    _uiState.update {
                        it.copy(
                            user = outcome.ok() ?: it.user,
                            profilePhase = outcome.phase(),
                            offline = outcome is LoadOutcome.Offline,
                        )
                    }
                }
                HomeSection.Assignments -> {
                    _uiState.update { it.copy(assignmentPhase = LoadPhase.Loading) }
                    val outcome = runLoad { api.assignmentsMe() }
                    _uiState.update {
                        it.copy(
                            assignments = outcome.ok() ?: it.assignments,
                            assignmentPhase = outcome.phase(),
                            offline = outcome is LoadOutcome.Offline,
                        )
                    }
                }
                HomeSection.Inbox -> {
                    _uiState.update { it.copy(inboxPhase = LoadPhase.Loading, inboxActionError = null) }
                    val outcome = runLoad { api.notifications() }
                    val data = outcome.ok()
                    _uiState.update {
                        it.copy(
                            inbox = data?.notifications ?: it.inbox,
                            unread = data?.unread ?: it.unread,
                            inboxPhase = outcome.phase(),
                            offline = outcome is LoadOutcome.Offline,
                        )
                    }
                }
            }
        }
    }

    /**
     * Submit is text-only (the backend stores `note`), and every refusal is a distinct reason:
     * past the deadline, not enrolled, no such assignment. The old path returned a bare boolean,
     * so the sheet could only ever say "thử lại" against a rule the student has to read to fix.
     */
    fun submitAssignment(assignmentId: String, note: String) {
        _uiState.update { it.copy(submitBusy = true, submission = null) }
        viewModelScope.launch {
            val outcome = try {
                val envelope = api.submitAssignment(
                    assignmentId,
                    com.campusute.app.core.network.SubmitAssignmentDto(note = note.trim()),
                )
                val message = envelope.error?.message
                if (message != null) {
                    SubmitOutcome.Rejected(assignmentId, message)
                } else {
                    SubmitOutcome.Accepted(assignmentId)
                }
            } catch (_: IOException) {
                SubmitOutcome.Offline(assignmentId)
            } catch (http: HttpException) {
                SubmitOutcome.Rejected(assignmentId, http.envelopeMessage() ?: "Máy chủ từ chối yêu cầu.")
            } catch (_: Exception) {
                SubmitOutcome.Rejected(assignmentId, "Không nộp được bài — thử lại.")
            }
            _uiState.update { it.copy(submitBusy = false, submission = outcome) }
            if (outcome is SubmitOutcome.Accepted) refreshDataQuietly()
        }
    }

    fun clearSubmission() = _uiState.update { it.copy(submission = null) }

    /** The row already flips from the outcome; a full refresh would re-trigger the loading phase. */
    private fun refreshDataQuietly() {
        viewModelScope.launch {
            val assignments = runLoad { api.assignmentsMe() }
            _uiState.update {
                it.copy(
                    assignments = assignments.ok() ?: it.assignments,
                    assignmentPhase = assignments.phase(),
                )
            }
        }
    }

    fun markRead(notification: NotificationItemDto) {
        if (notification.read) return // already-read row: badge must not undercount
        viewModelScope.launch {
            runCatching { api.markNotificationRead(notification.id) }
                .onSuccess {
                    // Badge must decrement immediately; inbox row flips to read in place.
                    _uiState.update { state ->
                        state.copy(
                            inboxActionError = null,
                            unread = (state.unread - 1).coerceAtLeast(0),
                            inbox = state.inbox.map { if (it.id == notification.id) it.copy(read = true) else it },
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(inboxActionError = "Không đánh dấu đã đọc được — thử lại.") }
                }
        }
    }

    /**
     * There is no bulk endpoint, so this walks the unread rows one by one and stops at the first
     * refusal. Rows that already succeeded stay flipped — pretending the whole sweep failed would
     * contradict the server, and pretending it succeeded would be a lie.
     */
    fun markAllRead() {
        val pending = _uiState.value.inbox.filterNot { it.read }
        if (pending.isEmpty()) return
        viewModelScope.launch {
            var marked = 0
            var failed = false
            pending.forEach { row ->
                if (runCatching { api.markNotificationRead(row.id) }.isSuccess) {
                    marked++
                    _uiState.update { state ->
                        state.copy(
                            unread = (state.unread - 1).coerceAtLeast(0),
                            inbox = state.inbox.map { if (it.id == row.id) it.copy(read = true) else it },
                        )
                    }
                } else {
                    failed = true
                    return@forEach
                }
            }
            if (failed) {
                _uiState.update {
                    it.copy(inboxActionError = if (marked == 0) "Không đánh dấu đã đọc được — thử lại." else "Chỉ đánh dấu được $marked mục — thử lại phần còn lại.")
                }
            } else {
                _uiState.update { it.copy(inboxActionError = null) }
            }
        }
    }

    fun clearInboxActionError() = _uiState.update { it.copy(inboxActionError = null) }

    fun logout() = sessionRepository.logout()

    /**
     * One load, three honest outcomes. An envelope that carries `error` is a failure even on a 200,
     * because the payload is absent — treating it as an empty list is how the old code laundered
     * a refusal into "nothing to show".
     */
    private suspend fun <T> runLoad(block: suspend () -> com.campusute.app.core.network.ApiEnvelopeDto<T>): LoadOutcome<T> =
        try {
            val envelope = block()
            when {
                envelope.error != null -> LoadOutcome.Rejected(envelope.error.message)
                envelope.data == null -> LoadOutcome.Rejected("Phản hồi không có dữ liệu.")
                else -> LoadOutcome.Ok(envelope.data)
            }
        } catch (_: IOException) {
            LoadOutcome.Offline
        } catch (_: HttpException) {
            LoadOutcome.Rejected("Máy chủ từ chối yêu cầu.")
        } catch (_: Exception) {
            LoadOutcome.Rejected("Không đọc được phản hồi.")
        }

    private fun <T> LoadOutcome<T>.phase(): LoadPhase = when (this) {
        is LoadOutcome.Ok -> LoadPhase.Ready
        LoadOutcome.Offline -> LoadPhase.Failed
        is LoadOutcome.Rejected -> LoadPhase.Failed
    }
}

enum class HomeSection { Profile, Assignments, Inbox }

private sealed interface LoadOutcome<out T> {
    data class Ok<T>(val value: T) : LoadOutcome<T>
    data object Offline : LoadOutcome<Nothing>
    data class Rejected(val message: String) : LoadOutcome<Nothing>
}

private fun <T> LoadOutcome<T>.ok(): T? = (this as? LoadOutcome.Ok)?.value
