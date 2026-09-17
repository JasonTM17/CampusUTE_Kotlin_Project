package com.campusute.app.core.data

import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.LoginRequestDto
import com.campusute.app.core.network.RefreshRequestDto
import com.campusute.app.core.network.UserDto
import com.campusute.app.core.security.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionResult {
    data class Success(val user: UserDto) : SessionResult
    data class Failure(val friendlyMessage: String) : SessionResult
}

/**
 * Single entry point for session lifecycle. UI never touches the network or
 * token storage directly (Clean Architecture layering).
 */
@Singleton
class SessionRepository @Inject constructor(
    private val api: CampusApi,
    private val tokenStore: TokenStore,
) {
    suspend fun login(email: String, password: String): SessionResult = try {
        val envelope = api.login(LoginRequestDto(email.trim(), password))
        val tokens = envelope.data
        when {
            envelope.error != null -> SessionResult.Failure(envelope.error.message)
            tokens == null -> SessionResult.Failure("Phản hồi không hợp lệ từ máy chủ.")
            else -> {
                tokenStore.saveTokens(tokens.accessToken, tokens.refreshToken)
                SessionResult.Success(tokens.user)
            }
        }
    } catch (_: java.io.IOException) {
        SessionResult.Failure("Không thể kết nối máy chủ. Kiểm tra mạng và thử lại.")
    } catch (_: retrofit2.HttpException) {
        SessionResult.Failure("Email hoặc mật khẩu không đúng.")
    } catch (_: Exception) {
        SessionResult.Failure("Đã xảy ra lỗi. Vui lòng thử lại.")
    }

    fun logout() {
        val refresh = tokenStore.refreshToken()
        if (refresh != null) {
            // Best-effort revoke; the local session is cleared regardless.
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { api.logout(RefreshRequestDto(refresh)) }
            }
        }
        tokenStore.clear()
    }
}
