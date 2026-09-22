package com.campusute.app.feature.profile

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.campusute.app.core.data.SessionRepository
import com.campusute.app.core.data.SettingsRepository
import com.campusute.app.core.network.ApiEnvelopeDto
import com.campusute.app.core.network.AiChatRequest
import com.campusute.app.core.network.AiChatResponse
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.CourseGradesDto
import com.campusute.app.core.network.CampusEventDto
import com.campusute.app.core.network.InboxDto
import com.campusute.app.core.network.LoginRequestDto
import com.campusute.app.core.network.NoteDto
import com.campusute.app.core.network.NoteRequestDto
import com.campusute.app.core.network.RefreshRequestDto
import com.campusute.app.core.network.ScheduleSessionDto
import com.campusute.app.core.network.SubmitAssignmentDto
import com.campusute.app.core.network.SummarizeRequestDto
import com.campusute.app.core.network.SummarizeResponseDto
import com.campusute.app.core.network.TaskChangesDto
import com.campusute.app.core.network.TokenResponseDto
import com.campusute.app.core.network.UserDto
import com.campusute.app.core.security.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.3 settings gates: account facts render, the dark-mode toggle flips and
 * persists via SettingsRepository, and logout is confirm-guarded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @org.junit.After fun tearDown() { Dispatchers.resetMain() }

    private class FakeTokenStore : TokenStore {
        var access: String? = "t"
        var refresh: String? = "r"
        var cleared = false
        override fun saveTokens(accessToken: String, refreshToken: String) { access = accessToken; refresh = refreshToken }
        override fun accessToken() = access
        override fun refreshToken() = refresh
        override fun clear() { cleared = true; access = null; refresh = null }
    }

    private class FakeApi : CampusApi {
        val meCalls get() = meCount
        private var meCount = 0
        private val user = UserDto(
            id = "1", email = "student@demo.campusute.vn", fullName = "Nguyễn Văn Sơn",
            studentCode = "21110101", department = "CNTT", roles = listOf("STUDENT"),
        )
        override suspend fun login(body: LoginRequestDto) = TODO()
        override suspend fun refresh(body: RefreshRequestDto) = TODO()
        override suspend fun logout(body: RefreshRequestDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
        override suspend fun me(): ApiEnvelopeDto<UserDto> { meCount++; return ApiEnvelopeDto(data = user) }
        override suspend fun scheduleSessions(from: String, to: String): ApiEnvelopeDto<List<ScheduleSessionDto>> = ApiEnvelopeDto()
        override suspend fun taskChanges(since: String): ApiEnvelopeDto<TaskChangesDto> = ApiEnvelopeDto()
        override suspend fun taskSync(body: com.campusute.app.core.network.SyncRequestDto): ApiEnvelopeDto<com.campusute.app.core.network.SyncResponseDto> = ApiEnvelopeDto()
        override suspend fun aiChat(body: AiChatRequest): ApiEnvelopeDto<AiChatResponse> = ApiEnvelopeDto()
        override suspend fun assignmentsMe(): ApiEnvelopeDto<List<com.campusute.app.core.network.AssignmentDto>> = ApiEnvelopeDto()
        override suspend fun submitAssignment(id: String, body: SubmitAssignmentDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
        override suspend fun notes(): ApiEnvelopeDto<List<NoteDto>> = ApiEnvelopeDto()
        override suspend fun createNote(body: NoteRequestDto): ApiEnvelopeDto<NoteDto> = ApiEnvelopeDto()
        override suspend fun updateNote(id: String, body: NoteRequestDto): ApiEnvelopeDto<NoteDto> = ApiEnvelopeDto()
        override suspend fun deleteNote(id: String): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
        override suspend fun aiSummarize(body: SummarizeRequestDto): ApiEnvelopeDto<SummarizeResponseDto> = ApiEnvelopeDto()
        override suspend fun markNotificationRead(id: String): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
        override suspend fun notifications(): ApiEnvelopeDto<InboxDto> = ApiEnvelopeDto()
        override suspend fun gradesMe(): ApiEnvelopeDto<List<CourseGradesDto>> = ApiEnvelopeDto()
        override suspend fun events(): ApiEnvelopeDto<List<CampusEventDto>> = ApiEnvelopeDto()
        override suspend fun registerEvent(id: String, idempotencyKey: String): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** One shared instance: the VM mutates the same flow the assertions read. */
    private fun settings() = SettingsRepository(context)

    private fun viewModel(
        sessionRepository: SessionRepository,
        settings: SettingsRepository,
    ): ProfileViewModel = ProfileViewModel(FakeApi(), sessionRepository, settings)

    @Test
    fun `profile renders account facts and toggles flip settings`() {
        val settings = settings()
        val sessionRepository = SessionRepository(FakeApi(), FakeTokenStore())
        val vm = viewModel(sessionRepository, settings)

        composeRule.setContent { MaterialTheme { ProfileScreen(onLogout = {}, viewModel = vm) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Xin chào, Nguyễn Văn Sơn").assertExists()
        composeRule.onNodeWithText("Email").assertExists()
        composeRule.onNodeWithText("student@demo.campusute.vn").assertExists()
        composeRule.onNodeWithText("Chế độ tối").assertExists()
        composeRule.onNodeWithText("Nhận thông báo").assertExists()
        composeRule.onNodeWithText("Đăng xuất").assertExists()
        assertTrue(!settings.darkMode.value)
        composeRule
            .onNode(androidx.compose.ui.test.hasScrollAction())
            .performScrollToNode(androidx.compose.ui.test.hasText("Chế độ tối"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Chế độ tối").performTouchInput { click() }
        composeRule.waitForIdle()
        assertTrue(
            "row tap vm=${vm.darkMode.value} settings=${settings.darkMode.value}",
            settings.darkMode.value,
        )

        composeRule.onNodeWithContentDescription("toggle-dark", useUnmergedTree = true)
            .performTouchInput { click() }
        composeRule.waitForIdle()
        assertTrue(
            "switch tap vm=${vm.darkMode.value} settings=${settings.darkMode.value}",
            !settings.darkMode.value,
        )
    }

    @Test
    fun `logout is confirm-guarded and clears the token store`() {
        val store = FakeTokenStore()
        val settings = settings()
        val sessionRepository = SessionRepository(FakeApi(), store)
        val vm = viewModel(sessionRepository, settings)
        composeRule.setContent { MaterialTheme { ProfileScreen(onLogout = {}, viewModel = vm) } }
        composeRule.waitForIdle()

        // The button sits below the fold inside the scrollable digest — scroll to it first.
        composeRule
            .onNode(androidx.compose.ui.test.hasScrollAction())
            .performScrollToNode(androidx.compose.ui.test.hasText("Đăng xuất"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Đăng xuất").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Đăng xuất?").assertExists()
        composeRule.onNodeWithText("Giữ").performClick()
        composeRule.waitForIdle()
        assertEquals("Giữ must not clear the session", false, store.cleared)

        composeRule.onNodeWithText("Đăng xuất").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Xác nhận đăng xuất", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals("confirm must clear the token store", true, store.cleared)
    }
}
