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

// ---- task sync contract (Phase 3) ----

@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val dueDate: String? = null,
    val done: Boolean = false,
    val deleted: Boolean = false,
    val version: Long = 1,
    val updatedAt: String,
)

@Serializable
data class PushOperationDto(
    val clientOpId: String,
    val opType: String,
    val taskId: String? = null,
    val baseVersion: Long? = null,
    val title: String? = null,
    val dueDate: String? = null,
    val done: Boolean? = null,
)

@Serializable
data class SyncRequestDto(val operations: List<PushOperationDto>)

@Serializable
data class OpResultDto(
    val clientOpId: String,
    val status: String,
    val task: TaskDto? = null,
)

@Serializable
data class SyncResponseDto(val results: List<OpResultDto>, val serverTime: String)

@Serializable
data class TaskChangesDto(val changes: List<TaskDto>, val serverTime: String)
