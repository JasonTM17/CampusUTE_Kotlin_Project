package com.campusute.backend.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Refresh token row. Only the SHA-256 hash of the presented token is stored,
 * so a database leak cannot mint sessions. Rotation sets revokedAt +
 * replacedBy; presenting a revoked token triggers revocation of the whole
 * user's sessions (reuse detection).
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "issued_at", nullable = false, insertable = false, updatable = false)
    val issuedAt: Instant = Instant.EPOCH,
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
    @Column(name = "replaced_by")
    var replacedBy: String? = null,
    @Column(name = "user_agent")
    val userAgent: String? = null,
) {
    fun isActive(now: Instant = Instant.now()): Boolean =
        revokedAt == null && expiresAt.isAfter(now)
}
