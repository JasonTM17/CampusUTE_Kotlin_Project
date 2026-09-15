package com.campusute.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusute.app.core.data.SessionRepository
import com.campusute.app.core.data.SessionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.length >= 6 && !loading
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun login() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = sessionRepository.login(state.email, state.password)) {
                is SessionResult.Success -> _uiState.update { it.copy(loading = false, success = true) }
                is SessionResult.Failure -> _uiState.update { it.copy(loading = false, error = result.friendlyMessage) }
            }
        }
    }
}
