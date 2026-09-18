package com.campusute.backend.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * In-app notification center storage. Notifications are inserted SYNCHRONOUSLY
 * in the same transaction as the triggering action (Kongming counsel: small
 * volume, atomic with the source event, no worker infra).
 */
@Entity
@Table(name = "notifications")
class Notification(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Column(nullable = false) val type: String, // GRADE | ASSIGNMENT | SYSTEM
    @Column(nullable = false) var title: String,
    @Column(name = "body", nullable = false) var bodyText: String = "",
    @Column(nullable = false) var read: Boolean = false,
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    val createdAt: Instant = Instant.EPOCH,
)

interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<Notification>
    fun countByUserIdAndRead(userId: UUID, read: Boolean): Long
}

@Service
class NotificationService(private val repo: NotificationRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun notify(userId: UUID, type: String, title: String, bodyText: String = "") {
        repo.save(
            Notification(
                userId = userId,
                type = type,
                title = title.take(255),
                bodyText = bodyText.take(1000),
            ),
        )
    }

    fun list(userId: UUID) = repo.findByUserIdOrderByCreatedAtDesc(userId)
    fun unreadCount(userId: UUID) = repo.countByUserIdAndRead(userId, false)
    fun markRead(id: UUID, userId: UUID) {
        repo.findById(id).orElse(null)?.let { n ->
            if (n.userId == userId) {
                n.read = true
                repo.save(n)
            }
        }
    }
}

data class NotificationDto(val id: UUID, val type: String, val title: String, val body: String, val read: Boolean, val createdAt: String)
