package com.campusute.app.feature.appshell

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
import com.campusute.app.core.network.RefreshRequestDto
import com.campusute.app.core.network.SyncRequestDto
import com.campusute.app.core.network.SyncResponseDto
import com.campusute.app.core.network.TaskChangesDto
import com.campusute.app.core.network.TaskDto
import com.campusute.app.core.network.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Conflict gates (closeout v1.1 plan, ruling R-A'): a CONFLICT push must NOT
 * silently take the server version — the op is retained with the local payload
 * copied in, both resolutions are offered, and the retained op can never wedge
 * the push loop (the batch.size < BATCH exit bounds every round).
 */
class TasksConflictRepositoryTest {

    private class RecordingTaskDao : StudyTaskDao {
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
            ops.value = ops.value.filterNot { it.clientOpId == op.clientOpId } + op
        }
        override suspend fun deleteByIds(ids: List<String>) {
            ops.value = ops.value.filterNot { it.clientOpId in ids }
        }
        override suspend fun count() = ops.value.size
    }

    private class FakeSyncStateDao : SyncStateDao {
        private val map = mutableMapOf<String, String>()
        override suspend fun get(key: String) = map[key]
        override suspend fun put(state: SyncStateEntity) { map[state.key] = state.value }
    }

    /** taskSync always answers CONFLICT with a newer server row. */
    private class ConflictingApi : CampusApi {
        val received = mutableListOf<List<com.campusute.app.core.network.PushOperationDto>>()
        val serverTask = TaskDto(id = "srv-1", title = "Tiêu đề máy chủ", version = 7, updatedAt = "2026-09-22T00:00:00Z")

        override suspend fun login(body: LoginRequestDto) = TODO()
        override suspend fun refresh(body: RefreshRequestDto) = TODO()
        override suspend fun logout(body: RefreshRequestDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
        override suspend fun me(): ApiEnvelopeDto<UserDto> = ApiEnvelopeDto()
        override suspend fun scheduleSessions(from: String, to: String): ApiEnvelopeDto<List<com.campusute.app.core.network.ScheduleSessionDto>> = ApiEnvelopeDto()
        override suspend fun taskChanges(since: String): ApiEnvelopeDto<TaskChangesDto> =
            ApiEnvelopeDto(data = TaskChangesDto(emptyList(), "2026-09-22T00:00:01Z"))
        override suspend fun taskSync(body: SyncRequestDto): ApiEnvelopeDto<SyncResponseDto> {
            received.add(body.operations)
            return ApiEnvelopeDto(
                data = SyncResponseDto(
                    results = body.operations.map { OpResultDto(it.clientOpId, "CONFLICT", serverTask) },
                    serverTime = "2026-09-22T00:00:00Z",
                ),
            )
        }
        override suspend fun aiChat(body: com.campusute.app.core.network.AiChatRequest): ApiEnvelopeDto<com.campusute.app.core.network.AiChatResponse> = ApiEnvelopeDto()
        override suspend fun assignmentsMe(): ApiEnvelopeDto<List<com.campusute.app.core.network.AssignmentDto>> = ApiEnvelopeDto()
        override suspend fun submitAssignment(id: String, body: com.campusute.app.core.network.SubmitAssignmentDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
        override suspend fun notes(): ApiEnvelopeDto<List<com.campusute.app.core.network.NoteDto>> = ApiEnvelopeDto()
        override suspend fun createNote(body: com.campusute.app.core.network.NoteRequestDto): ApiEnvelopeDto<com.campusute.app.core.network.NoteDto> = ApiEnvelopeDto()
        override suspend fun updateNote(id: String, body: com.campusute.app.core.network.NoteRequestDto): ApiEnvelopeDto<com.campusute.app.core.network.NoteDto> = ApiEnvelopeDto()
        override suspend fun deleteNote(id: String): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
        override suspend fun aiSummarize(body: com.campusute.app.core.network.SummarizeRequestDto): ApiEnvelopeDto<com.campusute.app.core.network.SummarizeResponseDto> = ApiEnvelopeDto()
        override suspend fun markNotificationRead(id: String): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
        override suspend fun notifications(): ApiEnvelopeDto<com.campusute.app.core.network.InboxDto> = ApiEnvelopeDto()
        override suspend fun gradesMe(): ApiEnvelopeDto<List<com.campusute.app.core.network.CourseGradesDto>> = ApiEnvelopeDto()
        override suspend fun events(): ApiEnvelopeDto<List<com.campusute.app.core.network.CampusEventDto>> = ApiEnvelopeDto()
        override suspend fun registerEvent(id: String, idempotencyKey: String): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
    }

    private fun repo(api: CampusApi): TasksRepository =
        TasksRepository(api, RecordingTaskDao(), FakePendingDao(), FakeSyncStateDao())

    @Test
    fun `conflict retains op with local payload and surfaces both sides`() = runTest {
        val taskDao = RecordingTaskDao()
        val pending = FakePendingDao()
        val api = ConflictingApi()
        val repository = TasksRepository(api, taskDao, pending, FakeSyncStateDao())

        // An UPDATE op: enqueued with null title/dueDate (R-A' W2 caveat).
        val local = StudyTaskEntity("srv-1", "Tiêu đề của tôi", version = 3)
        taskDao.upsertAll(listOf(local))
        repository.setDone(local, done = true)

        val outcome = repository.sync()
        assertTrue("got $outcome", outcome is SyncRunOutcome.Success && outcome.conflicts == 1)

        val conflict = repository.conflicts.value.single()
        assertEquals("srv-1", conflict.op.taskId)
        assertEquals("W2 caveat: local payload must be copied into the retained op", true, conflict.op.done)
        assertEquals(7L, conflict.serverTask.version)
        // Server state applied to Room, but the op row SURVIVES for resolution.
        assertEquals("Tiêu đề máy chủ", taskDao.byId("srv-1")?.title)
        assertEquals(1, pending.count())
    }

    @Test
    fun `keep mine re-pushes with the server baseVersion and applies`() = runTest {
        val taskDao = RecordingTaskDao()
        val pending = FakePendingDao()
        val api = ConflictingApi()
        val repository = TasksRepository(api, taskDao, pending, FakeSyncStateDao())
        val local = StudyTaskEntity("srv-1", "Tiêu đề của tôi", version = 3)
        taskDao.upsertAll(listOf(local))
        repository.setDone(local, done = true)
        repository.sync()
        val conflict = repository.conflicts.value.single()

        repository.resolveConflict(conflict.op.clientOpId, keepMine = true)
        assertEquals("conflict must leave the flow", 0, repository.conflicts.value.size)
        // The next sync re-pushes the retained op with baseVersion = server
        // version (7) and the local done flag.
        repository.sync()
        val rePush = api.received.last().single { it.clientOpId == conflict.op.clientOpId }
        assertEquals(7L, rePush.baseVersion)
        assertEquals(true, rePush.done)
    }

    @Test
    fun `accept server drops the retained op`() = runTest {
        val pending = FakePendingDao()
        val repository = TasksRepository(ConflictingApi(), RecordingTaskDao(), pending, FakeSyncStateDao())
        val local = StudyTaskEntity("srv-1", "Của tôi", version = 3)
        repository.setDone(local, done = true)
        repository.sync()
        val conflict = repository.conflicts.value.single()

        repository.resolveConflict(conflict.op.clientOpId, keepMine = false)
        assertEquals(0, repository.conflicts.value.size)
        assertEquals("op must be deleted", 0, pending.count())
    }
}
