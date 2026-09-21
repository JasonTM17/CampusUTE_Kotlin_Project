package com.campusute.app.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException

/**
 * Base test double: every endpoint answers with an empty envelope, so a feature fake overrides
 * only the calls it actually exercises.
 *
 * Before this, each of the seven fakes in `src/test` re-declared all of `CampusApi` by hand —
 * which meant adding one endpoint was a seven-file edit. Fakes that want a tripwire on an
 * unexpected call should keep overriding it with `TODO()`; the permissive default here is for
 * endpoints a screen genuinely never touches.
 */
open class StubCampusApi : CampusApi {

    override suspend fun login(body: LoginRequestDto): ApiEnvelopeDto<TokenResponseDto> = ApiEnvelopeDto()

    override suspend fun refresh(body: RefreshRequestDto): ApiEnvelopeDto<TokenResponseDto> = ApiEnvelopeDto()

    override suspend fun logout(body: RefreshRequestDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()

    override suspend fun me(): ApiEnvelopeDto<UserDto> = ApiEnvelopeDto()

    override suspend fun scheduleSessions(
        from: String,
        to: String,
    ): ApiEnvelopeDto<List<ScheduleSessionDto>> = ApiEnvelopeDto(data = emptyList())

    override suspend fun taskChanges(since: String): ApiEnvelopeDto<TaskChangesDto> =
        ApiEnvelopeDto(data = TaskChangesDto(emptyList(), "2026-01-01T00:00:00Z"))

    override suspend fun taskSync(body: SyncRequestDto): ApiEnvelopeDto<SyncResponseDto> =
        ApiEnvelopeDto(data = SyncResponseDto(emptyList(), "2026-01-01T00:00:00Z"))

    override suspend fun aiChat(body: AiChatRequest): ApiEnvelopeDto<AiChatResponse> =
        ApiEnvelopeDto(data = AiChatResponse(answer = ""))

    override suspend fun assignmentsMe(): ApiEnvelopeDto<List<AssignmentDto>> = ApiEnvelopeDto(data = emptyList())

    override suspend fun submitAssignment(
        id: String,
        body: SubmitAssignmentDto,
    ): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = mapOf("status" to "SUBMITTED"))

    override suspend fun notes(): ApiEnvelopeDto<List<NoteDto>> = ApiEnvelopeDto(data = emptyList())

    override suspend fun createNote(body: NoteRequestDto): ApiEnvelopeDto<NoteDto> = ApiEnvelopeDto()

    override suspend fun updateNote(id: String, body: NoteRequestDto): ApiEnvelopeDto<NoteDto> = ApiEnvelopeDto()

    override suspend fun deleteNote(id: String): ApiEnvelopeDto<Map<String, String>> =
        ApiEnvelopeDto(data = mapOf("status" to "DELETED"))

    override suspend fun aiSummarize(body: SummarizeRequestDto): ApiEnvelopeDto<SummarizeResponseDto> =
        ApiEnvelopeDto(data = SummarizeResponseDto(summary = "", proposed = true))

    override suspend fun markNotificationRead(id: String): ApiEnvelopeDto<Map<String, String>> =
        ApiEnvelopeDto(data = mapOf("status" to "OK"))

    override suspend fun notifications(): ApiEnvelopeDto<InboxDto> =
        ApiEnvelopeDto(data = InboxDto(notifications = emptyList(), unread = 0))

    override suspend fun gradesMe(): ApiEnvelopeDto<List<CourseGradesDto>> = ApiEnvelopeDto(data = emptyList())

    override suspend fun events(): ApiEnvelopeDto<List<CampusEventDto>> = ApiEnvelopeDto(data = emptyList())

    override suspend fun registerEvent(
        id: String,
        idempotencyKey: String,
    ): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = mapOf("status" to "REGISTERED"))
}

/**
 * A refused call that still carries the backend's diagnosis, so a test can assert the *server's*
 * words reach the screen rather than a generic "thử lại".
 *
 * `retrofit2.Response.error` takes the body twice on purpose — once as the parsed error payload,
 * once inside the raw okhttp response — and there is no single-argument overload.
 */
fun httpRefusal(code: Int, message: String): HttpException {
    val body = """{"data":null,"error":{"code":"SERVER","message":"$message"},"meta":{}}"""
        .toResponseBody("application/json".toMediaType())
    val raw = okhttp3.Response.Builder()
        .code(code)
        .message("Server Error")
        .protocol(Protocol.HTTP_1_1)
        .request(Request.Builder().url("https://test.local/").build())
        .body(body)
        .build()
    return HttpException(retrofit2.Response.error<Any>(body, raw))
}
