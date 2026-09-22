package com.campusute.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusute.app.core.data.SettingsRepository
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.UserDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: UserDto? = null,
    val loadFailed: Boolean = false,
)

/**
 * Hồ sơ & Cài đặt (v1.3): account facts from /me, local display preferences and
 * the logout action. Theme/notification toggles persist via [SettingsRepository].
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: CampusApi,
    private val sessionRepository: com.campusute.app.core.data.SessionRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    val darkMode: StateFlow<Boolean> = settings.darkMode
    val notifications: StateFlow<Boolean> = settings.notifications

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            runCatching { api.me() }
                .onSuccess { envelope ->
                    val user = envelope.data
                    if (user != null) {
                        _uiState.update { it.copy(user = user, loadFailed = false) }
                    } else {
                        _uiState.update { it.copy(loadFailed = true) }
                    }
                }
                .onFailure { _uiState.update { it.copy(loadFailed = true) } }
        }
    }

    fun setDarkMode(enabled: Boolean) = settings.setDarkMode(enabled)

    fun setNotifications(enabled: Boolean) = settings.setNotifications(enabled)

    fun logout(onDone: () -> Unit) {
        sessionRepository.logout()
        onDone()
    }
}
