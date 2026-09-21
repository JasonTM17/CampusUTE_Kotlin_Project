package com.campusute.app.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionEndedReason(val message: String) {
    /** The refresh token was rejected, so every session for this account is gone server-side. */
    Expired("Phiên đăng nhập đã hết hạn — vui lòng đăng nhập lại."),

    /** There was no refresh token to try, so the local session had already been dropped. */
    Missing("Không còn phiên hợp lệ — vui lòng đăng nhập lại."),
}

/**
 * One-way notice from the network layer to the UI: the session died underneath a screen that
 * assumed it was alive.
 *
 * The token authenticator clears storage when a refresh fails, but it sits three hops away from
 * any composable and must not depend on one. This is the seam between them — it holds no
 * reference back, so the OkHttp graph stays acyclic.
 */
@Singleton
class SessionEvents @Inject constructor() {
    private val _ended = MutableStateFlow<SessionEndedReason?>(null)
    val ended: StateFlow<SessionEndedReason?> = _ended.asStateFlow()

    fun report(reason: SessionEndedReason) {
        _ended.value = reason
    }

    fun consume() {
        _ended.value = null
    }
}
