package com.campusute.backend.auth

import com.campusute.backend.common.ApiException
import com.campusute.backend.common.ErrorCode
import com.campusute.backend.config.AppProperties
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Refresh-token rotation with reuse detection (ADR: short-lived access token
 * + rotating refresh token). Presenting an already-rotated or revoked token
 * is treated as theft: every session of that user is revoked. The mass
 * revocation runs in its own transaction (REQUIRES_NEW) so it survives the
 * 401 that rotate() then throws — rollback must not resurrect stolen
 * sessions.
 */
@Service
class RefreshTokenService(
    private val lookup: RefreshTokenLookup,
    private val props: AppProperties,
    private val revoker: SessionRevoker,
) {
    private val random = SecureRandom()

    data class Rotation(val userId: UUID, val refreshToken: String)

    @Transactional
    fun issueFor(userId: UUID, userAgent: String?): String = newToken(userId, userAgent).first

    /**
     * Validates the presented token, rotates it and returns the owner id with
     * the new refresh token. Reuse (revoked/rotated token) revokes every
     * session of the owning user before failing.
     */
    @Transactional
    fun rotate(presentedToken: String, userAgent: String?): Rotation {
        val stored = lookup.find(sha256(presentedToken))
            ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Refresh token không hợp lệ.")

        if (!stored.isActive()) {
            revoker.revokeAllFor(stored.userId) // committed in its own tx
            throw ApiException(ErrorCode.AUTH_SESSION_REVOKED, "Phiên đã bị thu hồi, vui lòng đăng nhập lại.")
        }

        val (raw, _) = newToken(stored.userId, userAgent)
        stored.revokedAt = Instant.now()
        stored.replacedBy = sha256(raw)
        lookup.save(stored)
        return Rotation(stored.userId, raw)
    }

    @Transactional
    fun revoke(presentedToken: String) {
        val stored = lookup.find(sha256(presentedToken)) ?: return
        if (stored.revokedAt == null) {
            stored.revokedAt = Instant.now()
            lookup.save(stored)
        }
    }

    private fun newToken(userId: UUID, userAgent: String?): Pair<String, Instant> {
        val bytes = ByteArray(32).also(random::nextBytes)
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val expiresAt = Instant.now().plusSeconds(props.refreshTtlDays * 86_400)
        lookup.save(
            RefreshToken(
                userId = userId,
                tokenHash = sha256(raw),
                expiresAt = expiresAt,
                userAgent = userAgent?.take(255),
            ),
        )
        return raw to expiresAt
    }

    companion object {
        fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

@Component
class SessionRevoker(private val lookup: RefreshTokenLookup) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revokeAllFor(userId: UUID) {
        lookup.revokeAllFor(userId, Instant.now())
    }
}
