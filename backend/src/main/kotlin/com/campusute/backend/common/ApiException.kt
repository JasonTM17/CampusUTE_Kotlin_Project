package com.campusute.backend.common

/**
 * Domain error taxonomy mapped to HTTP status by GlobalExceptionHandler.
 * Client-facing messages stay friendly; technical detail lives in logs.
 */
enum class ErrorCode(val status: Int) {
    VALIDATION_FAILED(400),
    AUTH_INVALID_CREDENTIALS(401),
    AUTH_TOKEN_EXPIRED(401),
    AUTH_TOKEN_INVALID(401),
    AUTH_SESSION_REVOKED(401),
    AUTH_FORBIDDEN(403),
    NOT_FOUND(404),
    RATE_LIMITED(429),
    AI_UNAVAILABLE(503),
    SYSTEM_INTERNAL(500),
}

class ApiException(
    val code: ErrorCode,
    override val message: String,
    val details: Map<String, Any?>? = null,
) : RuntimeException(message)
