package com.campusute.app.feature.appshell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusute.app.core.data.SessionRepository
import com.campusute.app.core.network.CampusApi
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

    init {
        viewModelScope.launch {
            // Soft-fail: shell renders generic greeting when /me is unreachable.
            runCatching { api.me() }.onSuccess { envelope ->
                _user.value = envelope.data
            }
        }
    }

    fun logout() = sessionRepository.logout()
}
