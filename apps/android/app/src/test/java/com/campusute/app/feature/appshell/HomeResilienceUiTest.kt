package com.campusute.app.feature.appshell

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.campusute.app.core.data.SessionRepository
import com.campusute.app.core.network.ApiEnvelopeDto
import com.campusute.app.core.network.ApiErrorDto
import com.campusute.app.core.network.AssignmentDto
import com.campusute.app.core.network.InboxDto
import com.campusute.app.core.network.NotificationItemDto
import com.campusute.app.core.network.StubCampusApi
import com.campusute.app.core.network.UserDto
import com.campusute.app.core.network.httpRefusal
import com.campusute.app.core.security.StubTokenStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The exit criterion for catalogue entry #1 (`docs/ai/app-design.md` §5 step 1): prove that
 * "the API failed" and "there is nothing to show" reach the screen as different things.
 *
 * `HomeViewModel` used to wrap every call in `runCatching { }.onSuccess { }` with no else branch,
 * so a 500 left the list empty and the tab printed "Chưa có bài tập nào." — it rendered an outage
 * as a student with no homework. These tests fail on that shape.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeResilienceUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { Dispatchers.resetMain() }

    private class HomeApi : StubCampusApi() {
        var meFailure: Throwable? = null
        var assignmentFailure: Throwable? = null
        var inboxEnvelope: ApiEnvelopeDto<InboxDto> = ApiEnvelopeDto(data = InboxDto(emptyList(), 0))
        var assignments: List<AssignmentDto> = emptyList()
        var assignmentCalls = 0
        var inboxCalls = 0

        override suspend fun me(): ApiEnvelopeDto<UserDto> {
            meFailure?.let { throw it }
            return ApiEnvelopeDto(
                data = UserDto("u1", "student@demo.campusute.vn", "Nguyễn Văn A", "21110001", "CNTT", listOf("STUDENT")),
            )
        }

        override suspend fun assignmentsMe(): ApiEnvelopeDto<List<AssignmentDto>> {
            assignmentCalls++
            assignmentFailure?.let { throw it }
            return ApiEnvelopeDto(data = assignments)
        }

        override suspend fun notifications(): ApiEnvelopeDto<InboxDto> {
            inboxCalls++
            return inboxEnvelope
        }
    }

    private fun showHome(api: HomeApi) {
        val viewModel = HomeViewModel(api, SessionRepository(api, StubTokenStore()))
        composeRule.setContent {
            MaterialTheme { HomeScreen(viewModel) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a refused assignment list is not rendered as having no assignments`() {
        val api = HomeApi().apply { assignmentFailure = httpRefusal(500, "Lỗi máy chủ") }
        showHome(api)

        composeRule.onNodeWithText("Không tải được bài tập.").assertExists()
        composeRule.onNodeWithText("Thử lại").assertExists()
        // The sentence this whole screen exists to prevent.
        composeRule.onNodeWithText("Chưa có bài tập nào").assertDoesNotExist()
    }

    @Test
    fun `an envelope error on the inbox is a failure even though the call returned 200`() {
        val api = HomeApi().apply {
            inboxEnvelope = ApiEnvelopeDto(
                data = null,
                error = ApiErrorDto(code = "AUTH_FORBIDDEN", message = "Không truy cập được"),
            )
        }
        showHome(api)

        composeRule.onNodeWithText("Không tải được thông báo.").assertExists()
        composeRule.onNodeWithText("Hộp thư trống").assertDoesNotExist()
    }

    @Test
    fun `one failing section leaves the other sections readable`() {
        val api = HomeApi().apply {
            assignmentFailure = httpRefusal(500, "Lỗi máy chủ")
            inboxEnvelope = ApiEnvelopeDto(
                data = InboxDto(
                    notifications = listOf(
                        NotificationItemDto("n1", "GRADE", "Điểm học kỳ 1 đã công bố", "", false, "2026-09-19T08:00:00Z"),
                    ),
                    unread = 1,
                ),
            )
        }
        showHome(api)

        composeRule.onNodeWithText("Không tải được bài tập.").assertExists()
        composeRule.onNodeWithText("Điểm học kỳ 1 đã công bố").assertExists()
        composeRule.onNodeWithText("Xin chào, Nguyễn Văn A").assertExists()
    }

    @Test
    fun `retrying a section reloads only that section`() {
        val api = HomeApi().apply { assignmentFailure = httpRefusal(500, "Lỗi máy chủ") }
        showHome(api)
        val assignmentCallsBefore = api.assignmentCalls
        val inboxCallsBefore = api.inboxCalls

        api.assignmentFailure = null
        api.assignments = listOf(
            AssignmentDto(
                id = "a1",
                courseCode = "CST201",
                title = "Bài tập lớn Kotlin",
                dueAt = "2030-01-01T00:00:00Z",
            ),
        )
        composeRule.onNodeWithText("Thử lại").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Bài tập lớn Kotlin").assertExists()
        composeRule.onNodeWithText("Không tải được bài tập.").assertDoesNotExist()
        // Exactly one more assignment fetch, and the healthy sections stay untouched — a retry
        // that re-ran refreshData() would re-blank the inbox while it reloaded.
        assertEquals(assignmentCallsBefore + 1, api.assignmentCalls)
        assertEquals(inboxCallsBefore, api.inboxCalls)
    }

    @Test
    fun `an unreachable server is announced as offline instead of silently empty`() {
        val api = HomeApi().apply { assignmentFailure = IOException("no route") }
        showHome(api)

        composeRule.onNodeWithText("Không có kết nối — nội dung bên dưới có thể chưa cập nhật").assertExists()
        composeRule.onNodeWithText("Chưa có bài tập nào").assertDoesNotExist()
    }

    @Test
    fun `an empty list from a healthy server still reads as an honest zero`() {
        showHome(HomeApi())

        composeRule.onNodeWithText("Chưa có bài tập nào").assertExists()
        composeRule.onNodeWithText("Hộp thư trống").assertExists()
        composeRule.onNodeWithText("Không tải được bài tập.").assertDoesNotExist()
    }

    @Test
    fun `a failed profile does not greet the student with a blank card`() {
        val api = HomeApi().apply { meFailure = httpRefusal(500, "Lỗi máy chủ") }
        showHome(api)

        composeRule.onNodeWithText("Không tải được hồ sơ sinh viên.").assertExists()
        composeRule.onNodeWithText("Xin chào, sinh viên").assertDoesNotExist()
    }
}
