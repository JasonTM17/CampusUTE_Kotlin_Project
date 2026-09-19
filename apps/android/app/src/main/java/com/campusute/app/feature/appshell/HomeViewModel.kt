package com.campusute.app.feature.appshell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusute.app.core.data.SessionRepository
import com.campusute.app.core.network.AssignmentDto
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.NotificationItemDto
import com.campusute.app.core.network.UserDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: CampusApi,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user.asStateFlow()

    private val _assignments = MutableStateFlow<List<AssignmentDto>>(emptyList())
    val assignments: StateFlow<List<AssignmentDto>> = _assignments.asStateFlow()

    private val _inbox = MutableStateFlow<List<NotificationItemDto>>(emptyList())
    val inbox: StateFlow<List<NotificationItemDto>> = _inbox.asStateFlow()

    private val _unread = MutableStateFlow(0L)
    val unread: StateFlow<Long> = _unread.asStateFlow()

    private val _submitBusy = MutableStateFlow(false)
    val submitBusy: StateFlow<Boolean> = _submitBusy.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { api.me() }.onSuccess { envelope ->
                _user.value = envelope.data
            }
            refreshData()
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            runCatching { api.assignmentsMe() }.onSuccess { envelope ->
                _assignments.value = envelope.data ?: emptyList()
            }
            runCatching { api.notifications() }.onSuccess { envelope ->
                _inbox.value = envelope.data?.notifications ?: emptyList()
                _unread.value = envelope.data?.unread ?: 0
            }
        }
    }

    fun submitAssignment(assignmentId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                api.submitAssignment(assignmentId, com.campusute.app.core.network.SubmitAssignmentDto(note = "Nộp qua app"))
            }.isSuccess
            refreshData()
            onResult(ok)
        }
    }

    fun markRead(notification: NotificationItemDto) {
        viewModelScope.launch {
            runCatching { api.markNotificationRead(notification.id) }.onSuccess {
                // Badge must decrement immediately; inbox row flips to read in place.
                _unread.value = (_unread.value - 1).coerceAtLeast(0)
                _inbox.value = _inbox.value.map {
                    if (it.id == notification.id) it.copy(read = true) else it
                }
            }
        }
    }

    fun logout() = sessionRepository.logout()

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
