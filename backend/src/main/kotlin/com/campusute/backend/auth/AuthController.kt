package com.campusute.backend.auth

import com.campusute.backend.config.FixedWindowRateLimiter
import com.campusute.backend.common.ApiEnvelope
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "auth")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val auth: AuthService,
    private val rateLimiter: FixedWindowRateLimiter,
) {
    data class LoginRequest(
        @field:NotBlank @field:Email
        val email: String,
        @field:NotBlank
        val password: String,
    )

    data class TokenResponse(
        val accessToken: String,
        val refreshToken: String,
        val expiresInMinutes: Long,
        val user: UserDto,
    )

    data class RefreshRequest(@field:NotBlank val refreshToken: String)

    @Operation(summary = "Login with demo credentials (dev) or university SSO adapter")
    @PostMapping("/login")
    fun login(@Valid @RequestBody body: LoginRequest, request: HttpServletRequest): ApiEnvelope<TokenResponse> {
        rateLimiter.checkLogin(body.email.lowercase())
        val result = auth.login(body.email, body.password, request.getHeader("User-Agent"))
        return ApiEnvelope.ok(result.toResponse())
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody body: RefreshRequest, request: HttpServletRequest): ApiEnvelope<TokenResponse> {
        val result = auth.refresh(body.refreshToken, request.getHeader("User-Agent"))
        return ApiEnvelope.ok(result.toResponse())
    }

    @PostMapping("/logout")
    fun logout(@RequestBody body: RefreshRequest): ApiEnvelope<Map<String, Nothing>> {
        auth.logout(body.refreshToken)
        return ApiEnvelope.ok(emptyMap())
    }

    private fun AuthService.LoginResult.toResponse() = TokenResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresInMinutes = expiresInMinutes,
        user = user,
    )
}

@Tag(name = "me")
@RestController
@RequestMapping("/api/v1")
class MeController(private val auth: AuthService) {

    @GetMapping("/me")
    fun me(): ApiEnvelope<UserDto> {
        val id = currentUserUuid() ?: throw IllegalStateException("unauthenticated")
        return ApiEnvelope.ok(auth.currentUser(id))
    }
}
