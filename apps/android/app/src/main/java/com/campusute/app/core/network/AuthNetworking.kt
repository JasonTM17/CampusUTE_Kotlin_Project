package com.campusute.app.core.network

import com.campusute.app.core.data.SessionEndedReason
import com.campusute.app.core.data.SessionEvents
import com.campusute.app.core.security.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/** Attaches the current access token to every request. */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.accessToken() ?: return chain.proceed(chain.request())
        return chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build(),
        )
    }
}

/**
 * Transparent refresh on 401: rotate once, save the new pair, retry. If the
 * refresh itself fails the session is cleared (the server revokes all
 * sessions on refresh-token reuse, so a dead refresh means re-login).
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val baseUrl: String,
    private val lazyClient: dagger.Lazy<OkHttpClient>,
    private val sessionEvents: SessionEvents,
) : Authenticator {

    // Concurrent 401s must not fire parallel refreshes with the same refresh
    // token — the server treats that as reuse and revokes every session.
    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        synchronized(refreshLock) {
            val currentRefresh = tokenStore.refreshToken() ?: run {
                sessionEvents.report(SessionEndedReason.Missing)
                return null
            }

            val body = """{"refreshToken":"${currentRefresh.replace("\"", "\\\"")}"}"""
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(baseUrl + "auth/refresh")
                .post(body)
                .build()

            val refreshed = runCatching { lazyClient.get().newCall(request).execute() }
                .getOrNull() ?: return null

            refreshed.use { resp ->
                val tokens = if (resp.isSuccessful) parseRefresh(resp.body?.string()) else null
                if (tokens == null) {
                    tokenStore.clear()
                    // Clearing storage is not enough: MainActivity read the token once at launch,
                    // so without this the shell stays on screen issuing requests it cannot author.
                    sessionEvents.report(SessionEndedReason.Expired)
                    return null
                }
                tokenStore.saveTokens(tokens.first, tokens.second)
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${tokens.first}")
                    .build()
            }
        }
    }

    private fun parseRefresh(body: String?): Pair<String, String>? = runCatching {
        val data = org.json.JSONObject(body ?: return null).getJSONObject("data")
        data.getString("accessToken") to data.getString("refreshToken")
    }.getOrNull()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
