package com.campusute.backend.common

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Canonical API envelope (contract-frozen, see packages/api-contracts):
 * every success is {data, meta, error:null}; every failure is
 * {data:null, meta:{}, error:{code,message,details,traceId}}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ApiEnvelope<T>(
    val data: T?,
    val meta: Map<String, Any?> = emptyMap(),
    val error: ApiError? = null,
) {
    companion object {
        fun <T> ok(data: T, meta: Map<String, Any?> = emptyMap()): ApiEnvelope<T> =
            ApiEnvelope(data = data, meta = meta, error = null)

        fun fail(error: ApiError): ApiEnvelope<Nothing> =
            ApiEnvelope(data = null, meta = emptyMap(), error = error)
    }
}

data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, Any?>? = null,
    val traceId: String? = null,
)
