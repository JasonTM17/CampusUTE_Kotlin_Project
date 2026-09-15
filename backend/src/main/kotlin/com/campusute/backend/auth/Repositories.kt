package com.campusute.backend.auth

import jakarta.persistence.EntityNotFoundException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmailIgnoreCase(email: String): User?
}

fun UserRepository.getByEmail(email: String): User =
    findByEmailIgnoreCase(email) ?: throw EntityNotFoundException("user $email")

@Repository
interface RoleRepository : JpaRepository<Role, Long> {
    fun findByName(name: String): Role?
}

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshToken?

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.userId = :userId AND t.revokedAt IS NULL")
    fun revokeAllForUser(userId: UUID, now: Instant): Int
}

@Repository
interface RefreshTokenLookup {
    fun find(tokenHash: String): RefreshToken?
    fun revokeAllFor(userId: UUID, now: Instant): Int
    fun save(entity: RefreshToken): RefreshToken
}

@Repository
class DefaultRefreshTokenLookup(
    private val repo: RefreshTokenRepository,
) : RefreshTokenLookup {
    override fun find(tokenHash: String) = repo.findByTokenHash(tokenHash)
    override fun revokeAllFor(userId: UUID, now: Instant) = repo.revokeAllForUser(userId, now)
    override fun save(entity: RefreshToken) = repo.save(entity)
}
