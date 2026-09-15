package com.campusute.backend.sync

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "study_tasks")
class StudyTask(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Column(nullable = false) var title: String,
    @Column(name = "due_date") var dueDate: java.time.LocalDate? = null,
    var done: Boolean = false,
    var deleted: Boolean = false,
    var version: Long = 1,
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    var updatedAt: Instant = Instant.EPOCH,
)

interface StudyTaskRepository : JpaRepository<StudyTask, UUID> {
    fun findByUserIdAndUpdatedAtAfterOrderByUpdatedAt(userId: UUID, after: Instant): List<StudyTask>
    fun findByIdAndUserId(id: UUID, userId: UUID): StudyTask?
}

@Entity
@Table(name = "sync_operations")
class SyncOperation(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Column(name = "client_op_id", nullable = false) val clientOpId: String,
    @Column(name = "op_type", nullable = false) val opType: String,
)

interface SyncOperationRepository : JpaRepository<SyncOperation, UUID> {
    fun existsByUserIdAndClientOpId(userId: UUID, clientOpId: String): Boolean
}

/**
 * Applies client operations with server-win conflict semantics:
 * - APPLIED: version matched (or create) — version bumped
 * - DUPLICATE: clientOpId already applied (offline replay) — no-op
 * - CONFLICT: baseVersion != serverVersion — server state returned, audit row
 * Reapplication is safe: replaying a batch yields DUPLICATEs, never duplicates.
 */
@Service
class TaskSyncService(
    private val tasks: StudyTaskRepository,
    private val ops: SyncOperationRepository,
    private val audit: com.campusute.backend.audit.AuditService,
) {
    data class PushOperation(
        val clientOpId: String,
        val opType: String, // CREATE | UPDATE | DELETE
        val taskId: UUID? = null,
        val baseVersion: Long? = null,
        val title: String? = null,
        val dueDate: java.time.LocalDate? = null,
        val done: Boolean? = null,
    )

    enum class OpStatus { APPLIED, DUPLICATE, CONFLICT, INVALID }

    data class OpResult(val clientOpId: String, val status: OpStatus, val task: StudyTask?)

    fun changes(userId: UUID, since: Instant): Pair<List<StudyTask>, Instant> =
        tasks.findByUserIdAndUpdatedAtAfterOrderByUpdatedAt(userId, since).let { it to Instant.now() }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun apply(userId: UUID, op: PushOperation): OpResult {
        if (op.clientOpId.isBlank() || op.clientOpId.length > 64) {
            return OpResult(op.clientOpId, OpStatus.INVALID, null)
        }
        if (ops.existsByUserIdAndClientOpId(userId, op.clientOpId)) {
            val existing = op.taskId?.let { tasks.findByIdAndUserId(it, userId) }
            return OpResult(op.clientOpId, OpStatus.DUPLICATE, existing)
        }

        val result: OpResult = when (op.opType.uppercase()) {
            "CREATE" -> {
                val task = StudyTask(userId = userId, title = op.title?.take(255) ?: "(không tiêu đề)")
                op.dueDate?.let { task.dueDate = it }
                op.done?.let { task.done = it }
                tasks.save(task)
                OpResult(op.clientOpId, OpStatus.APPLIED, task)
            }
            "UPDATE", "DELETE" -> {
                val task = op.taskId?.let { tasks.findByIdAndUserId(it, userId) }
                if (task == null) {
                    OpResult(op.clientOpId, OpStatus.INVALID, null)
                } else if (op.baseVersion == null || task.version != op.baseVersion) {
                    audit.record(userId, "SYNC_CONFLICT", task.id.toString(), "CONFLICT")
                    OpResult(op.clientOpId, OpStatus.CONFLICT, task) // server-win payload
                } else if (op.opType.uppercase() == "DELETE") {
                    task.deleted = true
                    task.version += 1
                    tasks.save(task)
                    OpResult(op.clientOpId, OpStatus.APPLIED, task)
                } else {
                    op.title?.let { task.title = it.take(255) }
                    op.dueDate?.let { task.dueDate = it }
                    op.done?.let { task.done = it }
                    task.version += 1
                    tasks.save(task)
                    OpResult(op.clientOpId, OpStatus.APPLIED, task)
                }
            }
            else -> OpResult(op.clientOpId, OpStatus.INVALID, null)
        }

        if (result.status == OpStatus.APPLIED) {
            ops.save(SyncOperation(userId = userId, clientOpId = op.clientOpId, opType = op.opType))
        }
        return result
    }
}
