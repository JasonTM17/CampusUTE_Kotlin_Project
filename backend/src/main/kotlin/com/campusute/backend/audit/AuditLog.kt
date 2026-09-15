package com.campusute.backend.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "actor_id")
    val actorId: UUID? = null,
    @Column(nullable = false)
    val action: String,
    val resource: String? = null,
    @Column(nullable = false)
    val result: String,
    @Column(name = "trace_id")
    val traceId: String? = null,
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    val createdAt: Instant = Instant.EPOCH,
)
