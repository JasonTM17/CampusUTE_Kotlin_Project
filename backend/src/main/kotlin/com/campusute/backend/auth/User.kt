package com.campusute.backend.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Column(nullable = false, unique = true)
    val email: String,
    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,
    @Column(name = "full_name", nullable = false)
    val fullName: String,
    @Column(name = "student_code")
    val studentCode: String? = null,
    @Column
    val department: String? = null,
    @Column(nullable = false)
    var enabled: Boolean = true,
    // Client-side UUID ids need an explicit isNew marker: EPOCH means "not
    // persisted yet"; the DB default fills the real timestamp on insert.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    val createdAt: Instant = Instant.EPOCH,
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")],
    )
    val roles: MutableSet<Role> = mutableSetOf(),
)
