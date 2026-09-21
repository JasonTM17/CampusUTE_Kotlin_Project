package com.campusute.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusute.app.core.data.LoginFailure
import com.campusute.app.core.data.SessionRepository
import com.campusute.app.core.data.SessionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    val failure: LoginFailure? = null,
    val message: String? = null,
    val success: Boolean = false,
    /** Seconds this client is holding off after a 429. See [LoginViewModel] for what it is not. */
    val cooldownSeconds: Int = 0,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.length >= 6 && !loading && cooldownSeconds == 0
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, failure = null, message = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, failure = null, message = null) }

    fun login() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(loading = true, failure = null, message = null) }
        viewModelScope.launch {
            when (val result = sessionRepository.login(state.email, state.password)) {
                is SessionResult.Success -> _uiState.update { it.copy(loading = false, success = true) }
                is SessionResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            failure = result.kind,
                            message = result.message,
                            cooldownSeconds = if (result.kind == LoginFailure.RateLimited) RATE_LIMIT_COOLDOWN_SECONDS else 0,
                        )
                    }
                    if (result.kind == LoginFailure.RateLimited) startCooldown()
                }
            }
        }
    }

    /**
     * The backend rate-limits on a fixed one-minute window and sends no Retry-After, so the reset
     * instant is genuinely unknown. This wait is therefore a *client* hold-off — the copy says
     * "chờ", not "cổng sẽ mở sau". Retrying earlier is allowed by the UI once it elapses and may
     * still be refused, which the next response will say for itself.
     */
    private fun startCooldown() {
        viewModelScope.launch {
            while (_uiState.value.cooldownSeconds > 0) {
                delay(1_000)
                _uiState.update { it.copy(cooldownSeconds = (it.cooldownSeconds - 1).coerceAtLeast(0)) }
            }
        }
    }

    companion object {
        const val RATE_LIMIT_COOLDOWN_SECONDS = 60
    }
}
