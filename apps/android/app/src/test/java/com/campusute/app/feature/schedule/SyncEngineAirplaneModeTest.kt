package com.campusute.app.feature.schedule

import com.campusute.app.core.data.SyncRunOutcome
import com.campusute.app.core.data.TasksRepository
import com.campusute.app.core.database.PendingOpDao
import com.campusute.app.core.database.PendingOpEntity
import com.campusute.app.core.database.StudyTaskDao
import com.campusute.app.core.database.StudyTaskEntity
import com.campusute.app.core.database.SyncStateDao
import com.campusute.app.core.database.SyncStateEntity
import com.campusute.app.core.network.ApiEnvelopeDto
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.LoginRequestDto
import com.campusute.app.core.network.OpResultDto
import com.campusute.app.core.network.PushOperationDto
import com.campusute.app.core.network.RefreshRequestDto
import com.campusute.app.core.network.ScheduleSessionDto
import com.campusute.app.core.network.SyncRequestDto
import com.campusute.app.core.network.SyncResponseDto
import com.campusute.app.core.network.TaskChangesDto
import com.campusute.app.core.network.TaskDto
import com.campusute.app.core.network.TokenResponseDto
import com.campusute.app.core.network.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Airplane-mode replay gates (Phase 3 exit criteria):
 * - ops recorded offline stay queued and replay exactly once when online
 * - the same clientOpId is never sent twice after ack
 * - CONFLICT resolves server-win: Room takes the server state, op dropped
 */
class SyncEngineAirplaneModeTest {

    private class RecordingDao : StudyTaskDao {
        val rows = MutableStateFlow<List<StudyTaskEntity>>(emptyList())
        override fun observeActive(): Flow<List<StudyTaskEntity>> = rows
        override suspend fun byId(id: String) = rows.value.firstOrNull { it.id == id }
        override suspend fun upsertAll(tasks: List<StudyTaskEntity>) {
            rows.value = (rows.value.associateBy { it.id } + tasks.associateBy { it.id }).values.toList()
        }
        override suspend fun deleteById(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
        }
        override suspend fun count() = rows.value.size
    }

    private class FakePendingDao : PendingOpDao {
        val ops = MutableStateFlow<List<PendingOpEntity>>(emptyList())
        override suspend fun peek(limit: Int) = ops.value.sortedBy { it.createdAt }.take(limit)
        override suspend fun enqueue(op: PendingOpEntity) {
            ops.value = ops.value + op
        }
        override suspend fun deleteByIds(ids: List<String>) {
            ops.value = ops.value.filterNot { it.clientOpId in ids }
        }
        override suspend fun count() = ops.value.size
    }

    private class FakeSyncStateDao : SyncStateDao {
        private val map = mutableMapOf<String, String>()
        override suspend fun get(key: String) = map[key]
        override suspend fun put(state: SyncStateEntity) {
            map[state.key] = state.value
        }
    }

    /** Api that is offline for the first [failFirst] sync attempts, then acks. */
    private class FlakyApi(private val failFirst: Int) : CampusApi {
        var attempts = 0
        val receivedBatches = mutableListOf<List<PushOperationDto>>()
        val serverTasks = mutableListOf<TaskDto>()
        var scripted: List<OpResultDto>? = null

        override suspend fun login(body: LoginRequestDto) = TODO()
        override suspend fun refresh(body: RefreshRequestDto) = TODO()
        override suspend fun logout(body: RefreshRequestDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
        override suspend fun me(): ApiEnvelopeDto<UserDto> = ApiEnvelopeDto()
        override suspend fun scheduleSessions(from: String, to: String): ApiEnvelopeDto<List<ScheduleSessionDto>> = ApiEnvelopeDto()

        override suspend fun taskSync(body: SyncRequestDto): ApiEnvelopeDto<SyncResponseDto> {
            attempts++
            if (attempts <= failFirst) throw IOException("airplane mode")
            receivedBatches.add(body.operations)
            val results = body.operations.map { op ->
                scripted?.firstOrNull() ?: run {
                    val serverTask = TaskDto(
                        id = "srv-" + op.clientOpId,
                        title = op.title ?: "(task)",
                        version = 1,
                        updatedAt = "2026-09-16T00:00:00Z",
                    )
                    serverTasks.add(serverTask)
                    OpResultDto(op.clientOpId, "APPLIED", serverTask)
                }
            }
            return ApiEnvelopeDto(data = SyncResponseDto(results, "2026-09-16T00:00:00Z"))
        }

        override suspend fun taskChanges(since: String): ApiEnvelopeDto<TaskChangesDto> =
            ApiEnvelopeDto(data = TaskChangesDto(serverTasks.toList(), "2026-09-16T00:00:01Z"))
    }

    @Test
    fun `airplane mode then replay pushes each op exactly once`() = runTest {
        val taskDao = RecordingDao()
        val pending = FakePendingDao()
        val api = FlakyApi(failFirst = 1)
        val repository = TasksRepository(api, taskDao, pending, FakeSyncStateDao())

        // Offline: create task — local write + queued op
        repository.createTask("Ôn Normalization", dueDate = java.time.LocalDate.of(2026, 10, 1))
        val offlineRun = repository.sync()
        assertTrue("first sync must report offline", offlineRun is SyncRunOutcome.Offline)
        assertEquals("op must stay queued while offline", 1, pending.count())
        assertEquals("local-first row must exist offline", 1, taskDao.count())

        // Back online: replay succeeds, op acked, local id remapped to server id
        val online = repository.sync()
        assertTrue("got $online", online is SyncRunOutcome.Success && online.pushed == 1)
        assertEquals("queue must drain after successful push", 0, pending.count())
        assertTrue("local id must be replaced", taskDao.rows.value.none { it.id.startsWith("local-") })

        // Idempotent replay: syncing again sends NO new operations
        repository.sync()
        assertEquals("no second batch without new local ops", 1, api.receivedBatches.size)
        val allIds = api.receivedBatches.flatten().map { it.clientOpId }
        assertEquals("clientOpIds must never repeat", allIds.size, allIds.distinct().size)
    }

    @Test
    fun `conflict resolves server-win and drops the local op`() = runTest {
        val taskDao = RecordingDao()
        val pending = FakePendingDao()
        val api = FlakyApi(failFirst = 0)
        val repository = TasksRepository(api, taskDao, pending, FakeSyncStateDao())

        // Server has v3 (someone else updated it meanwhile)
        val serverTask = TaskDto(
            id = "t-1", title = "Server title (mới hơn)", version = 3,
            updatedAt = "2026-09-16T00:00:00Z",
        )
        api.serverTasks.add(serverTask)

        // Client holds a stale v1 copy and tries to check it off
        taskDao.upsertAll(listOf(StudyTaskEntity("t-1", "Tiêu đề local cũ", version = 1)))
        repository.setDone(taskDao.byId("t-1")!!, done = true)
        api.scripted = listOf(OpResultDto("scripted", "CONFLICT", serverTask))

        val outcome = repository.sync()
        assertTrue("got $outcome", outcome is SyncRunOutcome.Success && outcome.conflicts == 1)
        assertEquals("conflicted op must be dropped", 0, pending.count())
        assertEquals("server state must win", "Server title (mới hơn)", taskDao.byId("t-1")?.title)
        assertEquals("server version must win", 3L, taskDao.byId("t-1")?.version)
    }
}
