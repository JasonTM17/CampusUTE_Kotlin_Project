package com.campusute.app.core.data

import com.campusute.app.core.database.PendingOpDao
import com.campusute.app.core.database.PendingOpEntity
import com.campusute.app.core.database.StudyTaskDao
import com.campusute.app.core.database.StudyTaskEntity
import com.campusute.app.core.database.SyncStateDao
import com.campusute.app.core.database.SyncStateEntity
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.PushOperationDto
import com.campusute.app.core.network.SyncRequestDto
import com.campusute.app.core.network.TaskDto
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncRunOutcome {
    data class Success(val pushed: Int, val conflicts: Int, val pulled: Int) : SyncRunOutcome
    data object Offline : SyncRunOutcome
    data class Failure(val message: String) : SyncRunOutcome
}

/**
 * Generic offline-first sync engine (ADR-0002):
 * 1. PUSH: pending ops FIFO, clientOpId makes create-replays idempotent;
 *    CONFLICT = server-win (server task overwrites Room, op dropped).
 * 2. PULL: delta `since` last serverTime; server rows upsert, tombstones delete.
 * Airplane-mode replay: ops recorded offline are pushed once when back online;
 * replaying a pushed batch is a server-side DUPLICATE no-op.
 */
@Singleton
class TasksRepository @Inject constructor(
    private val api: CampusApi,
    private val taskDao: StudyTaskDao,
    private val pendingDao: PendingOpDao,
    private val syncState: SyncStateDao,
) {
    fun observeActive(): Flow<List<StudyTaskEntity>> = taskDao.observeActive()

    suspend fun pendingCount(): Int = pendingDao.count()

    suspend fun createTask(title: String, dueDate: LocalDate?): String {
        val clientOpId = "c-" + UUID.randomUUID().toString()
        val localId = "local-$clientOpId"
        taskDao.upsertAll(
            listOf(
                StudyTaskEntity(
                    id = localId,
                    title = title.take(255),
                    dueDate = dueDate?.toString(),
                ),
            ),
        )
        pendingDao.enqueue(
            PendingOpEntity(
                clientOpId = clientOpId,
                opType = "CREATE",
                taskId = localId,
                baseVersion = null,
                title = title.take(255),
                dueDate = dueDate?.toString(),
                done = false,
            ),
        )
        return localId
    }

    suspend fun setDone(task: StudyTaskEntity, done: Boolean) {
        val clientOpId = "u-" + UUID.randomUUID().toString()
        taskDao.upsertAll(listOf(task.copy(done = done))) // optimistic local write
        pendingDao.enqueue(
            PendingOpEntity(
                clientOpId = clientOpId,
                opType = "UPDATE",
                taskId = task.id,
                baseVersion = task.version.takeIf { it > 0 },
                title = null,
                dueDate = null,
                done = done,
            ),
        )
    }

    suspend fun deleteTask(task: StudyTaskEntity) {
        val clientOpId = "d-" + UUID.randomUUID().toString()
        taskDao.deleteById(task.id) // optimistic
        pendingDao.enqueue(
            PendingOpEntity(
                clientOpId = clientOpId,
                opType = "DELETE",
                taskId = task.id,
                baseVersion = task.version.takeIf { it > 0 },
                title = null,
                dueDate = null,
                done = null,
            ),
        )
    }

    suspend fun sync(): SyncRunOutcome {
        val push = pushPending()
        if (push is SyncRunOutcome.Offline || push is SyncRunOutcome.Failure) return push

        return try {
            val since = syncState.get(KEY_LAST_SYNC) ?: "1970-01-01T00:00:00Z"
            val envelope = api.taskChanges(since)
            val changes = envelope.data
            if (envelope.error != null || changes == null) {
                SyncRunOutcome.Failure("Không lấy được thay đổi từ máy chủ.")
            } else {
                applyServerChanges(changes.changes)
                syncState.put(SyncStateEntity(KEY_LAST_SYNC, changes.serverTime))
                SyncRunOutcome.Success(
                    pushed = (push as SyncRunOutcome.Success).pushed,
                    conflicts = (push as SyncRunOutcome.Success).conflicts,
                    pulled = changes.changes.size,
                )
            }
        } catch (_: java.io.IOException) {
            SyncRunOutcome.Offline
        } catch (_: Exception) {
            SyncRunOutcome.Failure("Đồng bộ thất bại.")
        }
    }

    private suspend fun pushPending(): SyncRunOutcome = try {
        var pushed = 0
        var conflicts = 0
        while (true) {
            val batch = pendingDao.peek(BATCH)
            if (batch.isEmpty()) break
            val response = api.taskSync(
                SyncRequestDto(
                    batch.map {
                        PushOperationDto(
                            clientOpId = it.clientOpId,
                            opType = it.opType,
                            taskId = it.taskId?.takeUnless { id -> id.startsWith("local-") },
                            baseVersion = it.baseVersion,
                            title = it.title,
                            dueDate = it.dueDate,
                            done = it.done,
                        )
                    },
                ),
            )
            val results = response.data?.results ?: return SyncRunOutcome.Failure("Đồng bộ bị từ chối.")
            for ((op, result) in batch.zip(results)) {
                when (result.status) {
                    "APPLIED", "DUPLICATE", "CONFLICT" -> {
                        conflicts += if (result.status == "CONFLICT") 1 else 0
                        result.task?.let { applyServerState(it) }
                        pendingDao.deleteByIds(listOf(op.clientOpId))
                        // After CREATE, remap the local id to the server id.
                        if (result.status == "APPLIED" && op.opType == "CREATE" && result.task != null) {
                            taskDao.deleteById(op.taskId ?: "")
                        }
                    }
                    else -> pendingDao.deleteByIds(listOf(op.clientOpId)) // INVALID: drop poison op
                }
                pushed++
            }
            if (batch.size < BATCH) break
        }
        SyncRunOutcome.Success(pushed, conflicts, pulled = 0)
    } catch (_: java.io.IOException) {
        SyncRunOutcome.Offline
    } catch (_: Exception) {
        SyncRunOutcome.Failure("Đẩy dữ liệu thất bại.")
    }

    private suspend fun applyServerChanges(changes: List<TaskDto>) {
        changes.forEach { change ->
            if (change.deleted) taskDao.deleteById(change.id) else applyServerState(change)
        }
    }

    private suspend fun applyServerState(task: TaskDto) {
        taskDao.upsertAll(
            listOf(
                StudyTaskEntity(
                    id = task.id,
                    title = task.title,
                    dueDate = task.dueDate,
                    done = task.done,
                    deleted = task.deleted,
                    version = task.version,
                    updatedAt = System.currentTimeMillis(),
                ),
            ),
        )
    }

    private companion object {
        const val BATCH = 20
        const val KEY_LAST_SYNC = "task_last_sync"
    }
}
