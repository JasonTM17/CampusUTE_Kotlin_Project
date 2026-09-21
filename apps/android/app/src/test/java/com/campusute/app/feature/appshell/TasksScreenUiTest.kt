package com.campusute.app.feature.appshell

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI gate for catalogue #2 (study-tasks): empty + add flow render, and the
 * sync-conflict card offers BOTH resolutions and resolves on tap — the frame's
 * state matrix, asserted semantically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TasksScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @org.junit.After fun tearDown() { Dispatchers.resetMain() }

    private class FakeTaskDao : StudyTaskDao {
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

    private class ConflictingApi : CampusApi {
        val serverTask = TaskDto(id = "srv-1", title = "Tiêu đề máy chủ", version = 7, updatedAt = "2026-09-22T00:00:00Z")
        override suspend fun login(body: LoginRequestDto) = TODO()
        override suspend fun refresh(body: RefreshRequestDto) = TODO()
        override suspend fun logout(body: RefreshRequestDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
        override suspend fun me(): ApiEnvelopeDto<UserDto> = ApiEnvelopeDto()
        override suspend fun scheduleSessions(from: String, to: String): ApiEnvelopeDto<List<com.campusute.app.core.network.ScheduleSessionDto>> = ApiEnvelopeDto()
        override suspend fun taskChanges(since: String): ApiEnvelopeDto<TaskChangesDto> =
            ApiEnvelopeDto(data = TaskChangesDto(emptyList(), "2026-09-22T00:00:01Z"))
        override suspend fun taskSync(body: SyncRequestDto): ApiEnvelopeDto<SyncResponseDto> =
            ApiEnvelopeDto(
                data = SyncResponseDto(
                    results = body.operations.map { OpResultDto(it.clientOpId, "CONFLICT", serverTask) },
                    serverTime = "2026-09-22T00:00:00Z",
                ),
            )
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

    private fun viewModel(taskDao: FakeTaskDao = FakeTaskDao(), pending: FakePendingDao = FakePendingDao()): TasksViewModel =
        TasksViewModel(TasksRepository(ConflictingApi(), taskDao, pending, FakeSyncStateDao()))

    @Test
    fun `empty state renders then add flow shows the task row`() {
        val vm = viewModel()
        composeRule.setContent { MaterialTheme { TasksScreen(viewModel = vm) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Chưa có việc nào").assertExists()

        composeRule.onNodeWithText("Việc cần làm").performTextInput("Ôn JOIN")
        composeRule.onNodeWithText("Thêm việc").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Ôn JOIN").assertExists()
        assertEquals(1, vm.tasks.value.size)
    }

    @Test
    fun `conflict card offers both resolutions and accept-server resolves it`() {
        val taskDao = FakeTaskDao()
        val pending = FakePendingDao()
        // Pre-seed the local row + a pending UPDATE op so init-sync hits CONFLICT.
        kotlinx.coroutines.runBlocking {
            taskDao.upsertAll(listOf(StudyTaskEntity("srv-1", "Tiêu đề của tôi", version = 3)))
            pending.enqueue(
                PendingOpEntity(
                    clientOpId = "u-seed", opType = "UPDATE", taskId = "srv-1",
                    baseVersion = 3L, title = null, dueDate = null, done = true,
                ),
            )
        }
        val vm = viewModel(taskDao, pending)
        composeRule.setContent { MaterialTheme { TasksScreen(viewModel = vm) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Xung đột đồng bộ").assertExists()
        composeRule.onNodeWithText("Giữ phiên bản của tôi").assertExists()
        composeRule.onNodeWithText("Nhận phiên bản máy chủ").assertExists()

        composeRule.onNodeWithText("Nhận phiên bản máy chủ").performClick()
        composeRule.waitForIdle()

        assertEquals("resolution must clear the card", 0, vm.conflicts.value.size)
        composeRule.onNodeWithText("Xung đột đồng bộ").assertDoesNotExist()
    }
}
