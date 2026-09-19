package com.campusute.app.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface CampusApi {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): ApiEnvelopeDto<TokenResponseDto>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): ApiEnvelopeDto<TokenResponseDto>

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequestDto): ApiEnvelopeDto<Map<String, String>>

    @GET("me")
    suspend fun me(): ApiEnvelopeDto<UserDto>

    @GET("schedule/sessions")
    suspend fun scheduleSessions(
        @retrofit2.http.Query("from") from: String,
        @retrofit2.http.Query("to") to: String,
    ): ApiEnvelopeDto<List<ScheduleSessionDto>>

    @GET("tasks/changes")
    suspend fun taskChanges(
        @retrofit2.http.Query("since") since: String,
    ): ApiEnvelopeDto<TaskChangesDto>

    @POST("tasks/sync")
    suspend fun taskSync(@Body body: SyncRequestDto): ApiEnvelopeDto<SyncResponseDto>

    @POST("ai/chat")
    suspend fun aiChat(@Body body: AiChatRequest): ApiEnvelopeDto<AiChatResponse>

    @GET("assignments/me")
    suspend fun assignmentsMe(): ApiEnvelopeDto<List<AssignmentDto>>

    @POST("assignments/{id}/submit")
    suspend fun submitAssignment(
        @retrofit2.http.Path("id") id: String,
        @Body body: SubmitAssignmentDto,
    ): ApiEnvelopeDto<Map<String, String>>

    @GET("notes")
    suspend fun notes(): ApiEnvelopeDto<List<NoteDto>>

    @POST("notes")
    suspend fun createNote(@Body body: NoteRequestDto): ApiEnvelopeDto<NoteDto>

    @PUT("notes/{id}")
    suspend fun updateNote(
        @retrofit2.http.Path("id") id: String,
        @Body body: NoteRequestDto,
    ): ApiEnvelopeDto<NoteDto>

    @DELETE("notes/{id}")
    suspend fun deleteNote(@retrofit2.http.Path("id") id: String): ApiEnvelopeDto<Map<String, String>>

    @POST("ai/summarize")
    suspend fun aiSummarize(@Body body: SummarizeRequestDto): ApiEnvelopeDto<SummarizeResponseDto>

    @POST("notifications/{id}/read")
    suspend fun markNotificationRead(@retrofit2.http.Path("id") id: String): ApiEnvelopeDto<Map<String, String>>

    @GET("notifications")
    suspend fun notifications(): ApiEnvelopeDto<InboxDto>
}
