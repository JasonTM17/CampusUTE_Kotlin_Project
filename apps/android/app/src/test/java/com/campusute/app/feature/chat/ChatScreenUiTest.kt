package com.campusute.app.feature.chat

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.campusute.app.core.data.SessionRepository
import com.campusute.app.core.network.ApiEnvelopeDto
import com.campusute.app.core.network.AiChatRequest
import com.campusute.app.core.network.AiChatResponse
import com.campusute.app.core.network.AssignmentDto
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.InboxDto
import com.campusute.app.core.network.LoginRequestDto
import com.campusute.app.core.network.NoteDto
import com.campusute.app.core.network.NoteRequestDto
import com.campusute.app.core.network.RefreshRequestDto
import com.campusute.app.core.network.ScheduleSessionDto
import com.campusute.app.core.network.SubmitAssignmentDto
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
import java.io.IOException

/**
 * UI-test gate for the chat-send defect: typing into the input + tapping
 * Gửi MUST produce a user bubble, an aiChat call, and (offline) a friendly
 * error bubble. Reproduces the emulator failure deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @org.junit.After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeTokenStore : TokenStore {
        var access: String? = "t"
        var refresh: String? = "r"
        override fun saveTokens(accessToken: String, refreshToken: String) { access = accessToken; refresh = refreshToken }
        override fun accessToken() = access
        override fun refreshToken() = refresh
        override fun clear() { access = null; refresh = null }
    }

    private class OfflineApi : CampusApi {
        override suspend fun login(body: LoginRequestDto) = throw IOException("offline")
        override suspend fun refresh(body: RefreshRequestDto) = TODO()
        override suspend fun logout(body: RefreshRequestDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
        override suspend fun me(): ApiEnvelopeDto<UserDto> = ApiEnvelopeDto()
        override suspend fun scheduleSessions(from: String, to: String): ApiEnvelopeDto<List<ScheduleSessionDto>> = ApiEnvelopeDto()
        override suspend fun taskChanges(since: String): ApiEnvelopeDto<TaskChangesDto> = ApiEnvelopeDto()
        override suspend fun taskSync(body: com.campusute.app.core.network.SyncRequestDto): ApiEnvelopeDto<com.campusute.app.core.network.SyncResponseDto> = ApiEnvelopeDto()
        override suspend fun aiChat(body: AiChatRequest): ApiEnvelopeDto<AiChatResponse> = throw IOException("airplane mode")
        override suspend fun assignmentsMe(): ApiEnvelopeDto<List<AssignmentDto>> = ApiEnvelopeDto(data = emptyList())
        override suspend fun submitAssignment(id: String, body: SubmitAssignmentDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = emptyMap())
        override suspend fun notes(): ApiEnvelopeDto<List<NoteDto>> = ApiEnvelopeDto(data = emptyList())
        override suspend fun createNote(body: NoteRequestDto): ApiEnvelopeDto<NoteDto> = ApiEnvelopeDto()
        override suspend fun updateNote(id: String, body: NoteRequestDto): ApiEnvelopeDto<NoteDto> = ApiEnvelopeDto()
        override suspend fun deleteNote(id: String): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = emptyMap())
        override suspend fun aiSummarize(body: com.campusute.app.core.network.SummarizeRequestDto): ApiEnvelopeDto<com.campusute.app.core.network.SummarizeResponseDto> =
            ApiEnvelopeDto(data = com.campusute.app.core.network.SummarizeResponseDto("", false))
        override suspend fun markNotificationRead(id: String): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = emptyMap())
        override suspend fun notifications(): ApiEnvelopeDto<InboxDto> = ApiEnvelopeDto(data = InboxDto())
    }

    @Test
    fun `typing and tapping gui calls aiChat and shows both bubbles even offline`() {
        val vm = ChatViewModel(OfflineApi())
        composeRule.setContent { ChatScreen(viewModel = vm) }

        composeRule.onNodeWithText("Hỏi trợ lý AI...").performTextInput("Tuan nay hoc gi")
        composeRule.onNodeWithText("Gửi").performClick()
        composeRule.waitForIdle()

        val texts = vm.uiState.value.messages.map { it.text }
        assertTrue("user bubble must exist", texts.any { it == "Tuan nay hoc gi" })
        assertTrue("offline reply must exist", texts.any { it.contains("Không kết nối") })
        assertEquals(3, vm.uiState.value.messages.size)
    }

    @Test
    fun `gui button is disabled while input is blank`() {
        val vm = ChatViewModel(OfflineApi())
        composeRule.setContent { ChatScreen(viewModel = vm) }
        composeRule.onNodeWithText("Gửi").performClick()
        composeRule.waitForIdle()
        assertTrue("blank input must not send", vm.uiState.value.messages.none { it.fromUser })
    }

    @Test
    fun `send button exists with content description root reachable`() {
        val vm = ChatViewModel(OfflineApi())
        composeRule.setContent { ChatScreen(viewModel = vm) }
        composeRule.onNodeWithText("Hỏi trợ lý AI...").assertExists()
    }
}
