package com.campusute.app.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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
}
