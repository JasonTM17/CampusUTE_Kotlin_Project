package com.campusute.app.feature.appshell

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.campusute.app.core.data.ScheduleRepository
import com.campusute.app.core.data.SessionRepository
import com.campusute.app.core.database.ScheduleSessionDao
import com.campusute.app.core.database.ScheduleSessionEntity
import com.campusute.app.core.network.ApiEnvelopeDto
import com.campusute.app.core.network.AiChatRequest
import com.campusute.app.core.network.AiChatResponse
import com.campusute.app.core.network.AssignmentDto
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.InboxDto
import com.campusute.app.core.network.LoginRequestDto
import com.campusute.app.core.network.NoteDto
import com.campusute.app.core.network.NoteRequestDto
import com.campusute.app.core.network.NotificationItemDto
import com.campusute.app.core.network.RefreshRequestDto
import com.campusute.app.core.network.ScheduleSessionDto
import com.campusute.app.core.network.StubCampusApi
import com.campusute.app.core.network.SubmitAssignmentDto
import com.campusute.app.core.network.SummarizeRequestDto
import com.campusute.app.core.network.SummarizeResponseDto
import com.campusute.app.core.network.TaskChangesDto
import com.campusute.app.core.network.TokenResponseDto
import com.campusute.app.core.network.UserDto
import com.campusute.app.core.security.TokenStore
import com.campusute.app.feature.chat.ChatViewModel
import com.campusute.app.feature.notes.NotesViewModel
import com.campusute.app.feature.schedule.ScheduleViewModel
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
 * Shell regression + bell gates (closeout plan phase 4, Kongming delta 5):
 * - the 5-tab shell renders and tabs 0..3 stay selectable after the hoist
 * - bell badge shows the unread count; tapping a notification decrements it
 * - bell tap navigates to the notification tab
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BellBadgeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @org.junit.After fun tearDown() { Dispatchers.resetMain() }

    private class FakeTokenStore : TokenStore {
        var access: String? = "t"
        var refresh: String? = "r"
        override fun saveTokens(accessToken: String, refreshToken: String) { access = accessToken; refresh = refreshToken }
        override fun accessToken() = access
        override fun refreshToken() = refresh
        override fun clear() { access = null; refresh = null }
    }

    private class FakeDao : ScheduleSessionDao {
        val rows = MutableStateFlow<List<ScheduleSessionEntity>>(emptyList())
        override fun observeDay(date: String): Flow<List<ScheduleSessionEntity>> =
            MutableStateFlow(rows.value.filter { it.date == date })
        override fun observeRange(from: String, to: String) = rows
        override suspend fun upsertAll(sessions: List<ScheduleSessionEntity>) {
            rows.value = (rows.value.associateBy { it.id } + sessions.associateBy { it.id }).values.toList()
        }
        override suspend fun clearRange(from: String, to: String) {
            rows.value = rows.value.filter { it.date !in from..to }
        }
        override suspend fun count() = rows.value.size
    }

    private class ShellApi : StubCampusApi() {
        val reads = mutableListOf<String>()
        private val user = UserDto("1", "student@demo.campusute.vn", "Nguyễn Văn Sơn", "21110101", "CNTT", listOf("STUDENT"))
        private val notifications = (1..3).map { i ->
            NotificationItemDto(
                id = "n$i",
                type = "GRADE",
                title = "Thông báo điểm số $i",
                body = "Bài n$i",
                read = false,
                createdAt = "2026-09-19T0$i:00:00Z",
            )
        }

        override suspend fun login(body: LoginRequestDto) = TODO()
        override suspend fun refresh(body: RefreshRequestDto) = TODO()
        override suspend fun logout(body: RefreshRequestDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
        override suspend fun me(): ApiEnvelopeDto<UserDto> = ApiEnvelopeDto(data = user)
        override suspend fun scheduleSessions(from: String, to: String): ApiEnvelopeDto<List<ScheduleSessionDto>> = ApiEnvelopeDto()
        override suspend fun taskChanges(since: String): ApiEnvelopeDto<TaskChangesDto> = ApiEnvelopeDto()
        override suspend fun taskSync(body: com.campusute.app.core.network.SyncRequestDto): ApiEnvelopeDto<com.campusute.app.core.network.SyncResponseDto> = ApiEnvelopeDto()
        override suspend fun aiChat(body: AiChatRequest): ApiEnvelopeDto<AiChatResponse> = ApiEnvelopeDto()
        override suspend fun assignmentsMe(): ApiEnvelopeDto<List<AssignmentDto>> = ApiEnvelopeDto(data = emptyList())
        override suspend fun submitAssignment(id: String, body: SubmitAssignmentDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = emptyMap())
        override suspend fun notes(): ApiEnvelopeDto<List<NoteDto>> = ApiEnvelopeDto(data = emptyList())
        override suspend fun createNote(body: NoteRequestDto): ApiEnvelopeDto<NoteDto> = ApiEnvelopeDto()
        override suspend fun updateNote(id: String, body: NoteRequestDto): ApiEnvelopeDto<NoteDto> = ApiEnvelopeDto()
        override suspend fun deleteNote(id: String): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = emptyMap())
        override suspend fun aiSummarize(body: SummarizeRequestDto): ApiEnvelopeDto<SummarizeResponseDto> = ApiEnvelopeDto(data = SummarizeResponseDto("", false))
        override suspend fun markNotificationRead(id: String): ApiEnvelopeDto<Map<String, String>> {
            reads += id
            return ApiEnvelopeDto(data = mapOf("status" to "OK"))
        }
        override suspend fun notifications(): ApiEnvelopeDto<InboxDto> =
            ApiEnvelopeDto(data = InboxDto(notifications = notifications, unread = 3))
    }

    @Test
    fun `five tabs render and bell badge decrements after mark read`() {
        val api = ShellApi()
        val sessionRepository = SessionRepository(api, FakeTokenStore())
        val homeVm = HomeViewModel(api, sessionRepository)
        val timetableVm = ScheduleViewModel(ScheduleRepository(api, FakeDao()))
        val notesVm = NotesViewModel(api)
        val chatVm = ChatViewModel(api)

        composeRule.setContent {
            MaterialTheme {
                HomeShell(
                    onLogout = {},
                    sessionRepository = sessionRepository,
                    homeViewModel = homeVm,
                    timetableViewModel = timetableVm,
                    notesViewModel = notesVm,
                    chatViewModel = chatVm,
                )
            }
        }
        composeRule.waitForIdle()

        // Shell regression: all 5 tabs render (Kongming delta 5).
        listOf("Trang chủ", "Lịch học", "Thông báo", "Trợ lý AI", "Ghi chú").forEach {
            composeRule.onNodeWithText(it).assertExists()
        }

        // Unread badge reflects the backend unread count.
        composeRule.onNodeWithText("3").assertExists()

        // Bell tap switches to the notification tab.
        composeRule.onNodeWithContentDescription("Chuông thông báo").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Thông báo điểm số 1", substring = true).assertExists()

        // Tap-to-read: badge decrements 3 -> 2 and the API was called once.
        composeRule.onNodeWithText("Thông báo điểm số 1", substring = true).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("n1"), api.reads)
        composeRule.onNodeWithText("2").assertExists()
        composeRule.onNodeWithText("3").assertDoesNotExist()

        // Tabs 0..3 remain selectable after the VM hoist.
        composeRule.onNodeWithText("Trang chủ").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Bài tập cần chú ý").assertExists()
        composeRule.onNodeWithText("Lịch học").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("← Tuần trước").assertExists()

        // The new notes tab renders its empty state.
        composeRule.onNodeWithText("Ghi chú").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ghi chú mới").assertExists()
    }
}
