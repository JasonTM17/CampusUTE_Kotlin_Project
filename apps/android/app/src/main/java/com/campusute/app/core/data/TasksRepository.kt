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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
 * A push the server refused because someone else moved the row first. [op] is
 * retained in pending_ops with the LOCAL payload written into it (UPDATE ops
 * enqueue with null fields, so the values are copied from the task row BEFORE
 * the server state overwrites it) — "keep mine" re-pushes the same clientOpId
 * with the server's new baseVersion, "accept server" just drops the op.
 */
data class TaskConflict(val op: PendingOpEntity, val serverTask: TaskDto)

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

    private val _conflicts = MutableStateFlow<List<TaskConflict>>(emptyList())
    val conflicts: StateFlow<List<TaskConflict>> = _conflicts.asStateFlow()

    /** Both resolutions for one conflict row, per the study-tasks frame. */
    suspend fun resolveConflict(clientOpId: String, keepMine: Boolean) {
        val conflict = _conflicts.value.firstOrNull { it.op.clientOpId == clientOpId } ?: return
        if (keepMine) {
            // The server already moved to serverTask.version; re-push the local
            // payload against that version so the next sync APPLIES cleanly.
            pendingDao.enqueue(conflict.op.copy(baseVersion = conflict.serverTask.version))
        } else {
            pendingDao.deleteByIds(listOf(clientOpId))
        }
        _conflicts.update { list -> list.filterNot { it.op.clientOpId == clientOpId } }
    }

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
                    "APPLIED", "DUPLICATE" -> {
                        result.task?.let { applyServerState(it) }
                        pendingDao.deleteByIds(listOf(op.clientOpId))
                        // After CREATE, remap the local id to the server id.
                        if (result.status == "APPLIED" && op.opType == "CREATE" && result.task != null) {
                            taskDao.deleteById(op.taskId ?: "")
                        }
                    }
                    "CONFLICT" -> {
                        val serverTask = result.task
                        if (serverTask == null) {
                            // Server owes us its state for a resolution card;
                            // without it there is nothing to show, drop the op.
                            pendingDao.deleteByIds(listOf(op.clientOpId))
                        } else {
                            // Copy the LOCAL payload into the retained op row
                            // BEFORE applyServerState overwrites the task row —
                            // UPDATE ops enqueue with null fields (W2 caveat).
                            val local = op.taskId?.let { taskDao.byId(it) }
                            val retained = op.copy(
                                title = local?.title ?: op.title,
                                dueDate = local?.dueDate ?: op.dueDate,
                                done = local?.done ?: op.done,
                            )
                            pendingDao.enqueue(retained)
                            applyServerState(serverTask)
                            _conflicts.update { list ->
                                list.filterNot { it.op.clientOpId == retained.clientOpId } +
                                    TaskConflict(retained, serverTask)
                            }
                        }
                        conflicts++
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
