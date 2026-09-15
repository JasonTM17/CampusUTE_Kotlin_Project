package com.campusute.app.core.network

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorDto(
    val code: String,
    val message: String,
    val traceId: String? = null,
)

/**
 * Mirrors the frozen backend contract (packages/api-contracts/openapi.json):
 * every response is {data, meta, error}. meta values are heterogeneous
 * (booleans, numbers) so they ride as generic JSON elements.
 */
@Serializable
data class ApiEnvelopeDto<T>(
    val data: T? = null,
    val meta: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    val error: ApiErrorDto? = null,
)

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val fullName: String,
    val studentCode: String? = null,
    val department: String? = null,
    val roles: List<String> = emptyList(),
)

@Serializable
data class TokenResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresInMinutes: Long,
    val user: UserDto,
)

@Serializable
data class ScheduleSessionDto(
    val id: String,
    val courseCode: String,
    val courseName: String,
    val lecturerName: String,
    val building: String,
    val room: String,
    val date: String,
    val startAt: String,
    val endAt: String,
    val conflict: Boolean = false,
)
