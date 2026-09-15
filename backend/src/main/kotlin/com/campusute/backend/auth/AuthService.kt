package com.campusute.backend.auth

import com.campusute.backend.audit.AuditService
import com.campusute.backend.common.ApiException
import com.campusute.backend.common.ErrorCode
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuthService(
    private val users: UserRepository,
    private val jwt: JwtService,
    private val refreshTokens: RefreshTokenService,
    private val passwordEncoder: PasswordEncoder,
    private val audit: AuditService,
) {
    data class LoginResult(
        val accessToken: String,
        val refreshToken: String,
        val expiresInMinutes: Long,
        val user: UserDto,
    )

    fun login(email: String, password: String, userAgent: String?): LoginResult {
        val user = users.findByEmailIgnoreCase(email)
        // Same failure for unknown email and wrong password: no user enumeration.
        if (user == null || !passwordEncoder.matches(password, user.passwordHash) || !user.enabled) {
            audit.record(null, "LOGIN", email, "FAILURE")
            throw ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Email hoặc mật khẩu không đúng.")
        }
        val access = jwt.issue(user, user.roles.map(Role::name))
        val refresh = refreshTokens.issueFor(requireNotNull(user.id), userAgent)
        audit.record(user.id, "LOGIN", email)
        return LoginResult(access, refresh, ACCESS_TTL_MINUTES, user.toDto())
    }

    fun refresh(presented: String, userAgent: String?): LoginResult {
        val rotation = refreshTokens.rotate(presented, userAgent)
        val user = users.findById(rotation.userId).orElseThrow {
            ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Refresh token không hợp lệ.")
        }
        audit.record(user.id, "REFRESH")
        return LoginResult(
            accessToken = jwt.issue(user, user.roles.map(Role::name)),
            refreshToken = rotation.refreshToken,
            expiresInMinutes = ACCESS_TTL_MINUTES,
            user = user.toDto(),
        )
    }

    fun logout(presented: String) = refreshTokens.revoke(presented)

    fun currentUser(userId: UUID): UserDto =
        users.findById(userId).orElseThrow {
            ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng.")
        }.toDto()

    companion object {
        const val ACCESS_TTL_MINUTES = 15L
    }
}

fun User.toDto() = UserDto(
    id = requireNotNull(id),
    email = email,
    fullName = fullName,
    studentCode = studentCode,
    department = department,
    roles = roles.map(Role::name),
)

data class UserDto(
    val id: UUID,
    val email: String,
    val fullName: String,
    val studentCode: String?,
    val department: String?,
    val roles: List<String>,
)
