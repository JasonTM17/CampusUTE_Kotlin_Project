package com.campusute.backend.common

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ResponseEntity<ApiEnvelope<Nothing>> =
        ResponseEntity.status(ex.code.status).body(ApiEnvelope.fail(toError(ex.code, ex.message, ex.details)))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiEnvelope<Nothing>> {
        val details = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        return ResponseEntity.badRequest()
            .body(ApiEnvelope.fail(toError(ErrorCode.VALIDATION_FAILED, "Dữ liệu không hợp lệ.", details)))
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ApiEnvelope<Nothing>> =
        ResponseEntity.status(403)
            .body(ApiEnvelope.fail(toError(ErrorCode.AUTH_FORBIDDEN, "Bạn không có quyền thực hiện thao tác này.")))

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuth(ex: AuthenticationException): ResponseEntity<ApiEnvelope<Nothing>> {
        val code = if (ex is BadCredentialsException) ErrorCode.AUTH_INVALID_CREDENTIALS else ErrorCode.AUTH_TOKEN_INVALID
        return ResponseEntity.status(401).body(ApiEnvelope.fail(toError(code, "Xác thực thất bại.")))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnknown(ex: Exception): ResponseEntity<ApiEnvelope<Nothing>> {
        // Full detail stays in server logs; clients never receive stack traces.
        // The exception class name rides along to speed up ops diagnosis
        // without leaking internals.
        log.error("unhandled_exception", ex)
        return ResponseEntity.status(500).body(
            ApiEnvelope.fail(
                toError(
                    ErrorCode.SYSTEM_INTERNAL,
                    "Lỗi hệ thống, vui lòng thử lại sau.",
                    details = mapOf("exception" to ex.javaClass.name),
                ),
            ),
        )
    }

    private fun toError(code: ErrorCode, message: String, details: Map<String, Any?>? = null) = ApiError(
        code = code.name,
        message = message,
        details = details,
        traceId = org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY),
    )
}
