package com.campusute.app.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import retrofit2.HttpException

/**
 * The backend answers a refused call with `{data: null, error: {code, message}}` on a 4xx/5xx, so
 * the HTTP status alone is not the diagnosis — "Đã quá hạn nộp bài" and "Bạn không thuộc lớp học
 * phần này" are both 4xx and need different copy on screen.
 *
 * Reading the body is one-shot: [ResponseBody.string] consumes the stream, so every caller goes
 * through here rather than parsing ad hoc (chat had its own private copy of this).
 */
fun envelopeMessage(body: ResponseBody?): String? = runCatching {
    body?.string()?.let(::envelopeMessage)
}.getOrNull()

fun envelopeMessage(raw: String): String? = runCatching {
    Json.parseToJsonElement(raw).jsonObject["error"]
        ?.jsonObject?.get("message")
        ?.jsonPrimitive?.contentOrNull
}.getOrNull()

fun envelopeCode(raw: String): String? = runCatching {
    Json.parseToJsonElement(raw).jsonObject["error"]
        ?.jsonObject?.get("code")
        ?.jsonPrimitive?.contentOrNull
}.getOrNull()

fun HttpException.envelopeMessage(): String? = envelopeMessage(response()?.errorBody())
