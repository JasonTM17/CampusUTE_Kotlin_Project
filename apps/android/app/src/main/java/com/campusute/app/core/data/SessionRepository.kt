package com.campusute.app.core.data

import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.LoginRequestDto
import com.campusute.app.core.network.RefreshRequestDto
import com.campusute.app.core.network.UserDto
import com.campusute.app.core.network.envelopeError
import com.campusute.app.core.security.TokenStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Why a login failed. One String was not enough: a wrong password, a locked-out client and an
 * unreachable campus all rendered the same red sentence, and only one of them is fixed by typing
 * again. A real 429 used to fall into `catch (HttpException)` and claim "Email hoặc mật khẩu không
 * đúng" — the app told the student their password was wrong when it was their own rate limiter.
 */
enum class LoginFailure { InvalidCredentials, RateLimited, SessionExpired, Offline, ServerError, Unknown }

sealed interface SessionResult {
    data class Success(val user: UserDto) : SessionResult
    data class Failure(val kind: LoginFailure, val message: String) : SessionResult
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
        val error = envelope.error
        when {
            // A 200 carrying an error envelope is still a refusal, and its code is the taxonomy.
            error != null -> SessionResult.Failure(classify(error.code, null), error.message)
            tokens == null -> SessionResult.Failure(LoginFailure.Unknown, "Phản hồi không hợp lệ từ máy chủ.")
            else -> {
                tokenStore.saveTokens(tokens.accessToken, tokens.refreshToken)
                SessionResult.Success(tokens.user)
            }
        }
    } catch (_: IOException) {
        SessionResult.Failure(
            LoginFailure.Offline,
            "Không thể kết nối máy chủ. Kiểm tra mạng và thử lại.",
        )
    } catch (http: HttpException) {
        val refusal = http.envelopeError()
        SessionResult.Failure(
            classify(refusal?.code, http.code()),
            refusal?.message ?: fallbackMessage(http.code()),
        )
    } catch (_: Exception) {
        SessionResult.Failure(LoginFailure.Unknown, "Đã xảy ra lỗi. Vui lòng thử lại.")
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

    private fun classify(code: String?, status: Int?): LoginFailure = when {
        code == "RATE_LIMITED" || status == 429 -> LoginFailure.RateLimited
        code == "AUTH_INVALID_CREDENTIALS" -> LoginFailure.InvalidCredentials
        code == "AUTH_TOKEN_EXPIRED" || code == "AUTH_SESSION_REVOKED" || code == "AUTH_TOKEN_INVALID" ->
            LoginFailure.SessionExpired
        status == 401 || status == 403 -> LoginFailure.InvalidCredentials
        status != null && status >= 500 -> LoginFailure.ServerError
        else -> LoginFailure.Unknown
    }

    private fun fallbackMessage(status: Int?): String = when (status) {
        429 -> "Quá nhiều lần thử, vui lòng thử lại sau một phút."
        in 500..599 -> "Máy chủ đang gặp sự cố — thử lại sau ít phút."
        else -> "Email hoặc mật khẩu không đúng."
    }
}
