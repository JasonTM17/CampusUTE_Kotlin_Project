package com.campusute.app.feature.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.campusute.app.core.network.ApiEnvelopeDto
import com.campusute.app.core.network.AiChatRequest
import com.campusute.app.core.network.AiChatResponse
import com.campusute.app.core.network.AssignmentDto
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.StubCampusApi
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
import java.io.IOException

/**
 * UI gate for the propose-only invariant (R-B): tapping "✨ AI tóm tắt" shows
 * the suggestion dialog and the editor content must NOT contain the AI text
 * until the user taps "Chèn vào ghi chú".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotesScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @org.junit.After fun tearDown() { Dispatchers.resetMain() }

    private class SummarizeApi : StubCampusApi() {
        val notes = mutableListOf(NoteDto("n1", "Ghi chú DBMS", "Học JOIN. Học INDEX. Làm lab 2.", "2026-09-19T00:00:00Z"))
        var writes = 0

        override suspend fun login(body: LoginRequestDto) = TODO()
        override suspend fun refresh(body: RefreshRequestDto) = TODO()
        override suspend fun logout(body: RefreshRequestDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
        override suspend fun me(): ApiEnvelopeDto<UserDto> = ApiEnvelopeDto()
        override suspend fun scheduleSessions(from: String, to: String): ApiEnvelopeDto<List<ScheduleSessionDto>> = ApiEnvelopeDto()
        override suspend fun taskChanges(since: String): ApiEnvelopeDto<TaskChangesDto> = ApiEnvelopeDto()
        override suspend fun taskSync(body: com.campusute.app.core.network.SyncRequestDto): ApiEnvelopeDto<com.campusute.app.core.network.SyncResponseDto> = ApiEnvelopeDto()
        override suspend fun aiChat(body: AiChatRequest): ApiEnvelopeDto<AiChatResponse> = ApiEnvelopeDto()
        override suspend fun assignmentsMe(): ApiEnvelopeDto<List<AssignmentDto>> = ApiEnvelopeDto(data = emptyList())
        override suspend fun submitAssignment(id: String, body: SubmitAssignmentDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = emptyMap())
        override suspend fun markNotificationRead(id: String): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = emptyMap())
        override suspend fun notifications(): ApiEnvelopeDto<InboxDto> = ApiEnvelopeDto(data = InboxDto())
        override suspend fun notes(): ApiEnvelopeDto<List<NoteDto>> = ApiEnvelopeDto(data = notes.toList())
        override suspend fun createNote(body: NoteRequestDto): ApiEnvelopeDto<NoteDto> { writes++; return ApiEnvelopeDto() }
        override suspend fun updateNote(id: String, body: NoteRequestDto): ApiEnvelopeDto<NoteDto> { writes++; return ApiEnvelopeDto() }
        override suspend fun deleteNote(id: String): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = emptyMap())
        override suspend fun aiSummarize(body: SummarizeRequestDto): ApiEnvelopeDto<SummarizeResponseDto> =
            ApiEnvelopeDto(data = SummarizeResponseDto("Học JOIN • Học INDEX • Làm lab 2.", true))
    }

    @Test
    fun `summarize shows dialog and content only changes after explicit confirm`() {
        val api = SummarizeApi()
        val vm = NotesViewModel(api)
        composeRule.setContent { MaterialTheme { NotesScreen(viewModel = vm) } }
        composeRule.waitForIdle()

        // Open the editor on the seeded note and ask the AI for a summary.
        composeRule.onNodeWithText("Sửa").performClick()
        composeRule.waitForIdle()
        val original = vm.draft.value!!.content

        composeRule.onNodeWithText("✨ AI tóm tắt").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("AI đề xuất tóm tắt").assertExists()
        composeRule.onNodeWithText("Học JOIN • Học INDEX • Làm lab 2.").assertExists()
        assertEquals(
            "dialog must not mutate the editor content",
            original,
            vm.draft.value!!.content,
        )
        assertEquals("propose-only: no write may happen from summarize", 0, api.writes)

        // Explicit confirm is the ONLY path that inserts the AI text.
        composeRule.onNodeWithText("Chèn vào ghi chú").performClick()
        composeRule.waitForIdle()
        assertTrue(vm.draft.value!!.content.contains("[Tóm tắt AI]"))
        assertTrue(vm.draft.value!!.content.contains("Học JOIN • Học INDEX • Làm lab 2."))
        assertEquals("confirm must not persist either; only explicit save writes", 0, api.writes)
    }

    @Test
    fun `dismiss keeps editor content untouched`() {
        val api = SummarizeApi()
        val vm = NotesViewModel(api)
        composeRule.setContent { MaterialTheme { NotesScreen(viewModel = vm) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Sửa").performClick()
        composeRule.waitForIdle()
        val original = vm.draft.value!!.content

        composeRule.onNodeWithText("✨ AI tóm tắt").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Bỏ qua").performClick()
        composeRule.waitForIdle()

        assertEquals(original, vm.draft.value!!.content)
    }
}
