package com.campusute.app.feature.chat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.campusute.app.core.network.AiChatRequest
import com.campusute.app.core.network.AiChatResponse
import com.campusute.app.core.network.AiCitationDto
import com.campusute.app.core.network.ApiEnvelopeDto
import com.campusute.app.core.network.ApiErrorDto
import com.campusute.app.core.network.AssignmentDto
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.StubCampusApi
import com.campusute.app.core.network.InboxDto
import com.campusute.app.core.network.LoginRequestDto
import com.campusute.app.core.network.NetworkModule
import com.campusute.app.core.network.NoteDto
import com.campusute.app.core.network.NoteRequestDto
import com.campusute.app.core.network.RefreshRequestDto
import com.campusute.app.core.network.ScheduleSessionDto
import com.campusute.app.core.network.SubmitAssignmentDto
import com.campusute.app.core.network.SummarizeRequestDto
import com.campusute.app.core.network.SummarizeResponseDto
import com.campusute.app.core.network.SyncRequestDto
import com.campusute.app.core.network.SyncResponseDto
import com.campusute.app.core.network.TaskChangesDto
import com.campusute.app.core.network.TokenResponseDto
import com.campusute.app.core.network.UserDto
import com.campusute.app.core.security.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Gate for the chat defects this change set closes: the send path must still work
 * offline, a citation chip must actually reveal its excerpt (it used to be a no-op
 * lambda), every citation must render (it used to be capped at three), and the AI
 * call must not ride the shared 20s read timeout that made slow answers look offline.
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

    private open class StubApi : StubCampusApi() {
        override suspend fun login(body: LoginRequestDto) = throw IOException("offline")
        override suspend fun refresh(body: RefreshRequestDto) = TODO()
        override suspend fun logout(body: RefreshRequestDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto()
        override suspend fun me(): ApiEnvelopeDto<UserDto> = ApiEnvelopeDto()
        override suspend fun scheduleSessions(from: String, to: String): ApiEnvelopeDto<List<ScheduleSessionDto>> = ApiEnvelopeDto()
        override suspend fun taskChanges(since: String): ApiEnvelopeDto<TaskChangesDto> = ApiEnvelopeDto()
        override suspend fun taskSync(body: SyncRequestDto): ApiEnvelopeDto<SyncResponseDto> = ApiEnvelopeDto()
        override suspend fun aiChat(body: AiChatRequest): ApiEnvelopeDto<AiChatResponse> = throw IOException("airplane mode")
        override suspend fun assignmentsMe(): ApiEnvelopeDto<List<AssignmentDto>> = ApiEnvelopeDto(data = emptyList())
        override suspend fun submitAssignment(id: String, body: SubmitAssignmentDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = emptyMap())
        override suspend fun notes(): ApiEnvelopeDto<List<NoteDto>> = ApiEnvelopeDto(data = emptyList())
        override suspend fun createNote(body: NoteRequestDto): ApiEnvelopeDto<NoteDto> = ApiEnvelopeDto()
        override suspend fun updateNote(id: String, body: NoteRequestDto): ApiEnvelopeDto<NoteDto> = ApiEnvelopeDto()
        override suspend fun deleteNote(id: String): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = emptyMap())
        override suspend fun aiSummarize(body: SummarizeRequestDto): ApiEnvelopeDto<SummarizeResponseDto> =
            ApiEnvelopeDto(data = SummarizeResponseDto("", false))
        override suspend fun markNotificationRead(id: String): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = emptyMap())
        override suspend fun notifications(): ApiEnvelopeDto<InboxDto> = ApiEnvelopeDto(data = InboxDto())
    }

    private class OfflineApi : StubApi()

    private class CitedApi : StubApi() {
        override suspend fun aiChat(body: AiChatRequest): ApiEnvelopeDto<AiChatResponse> = ApiEnvelopeDto(
            data = AiChatResponse(
                answer = "[1] Quy chế 1: cần 36 tín chỉ.\n\n[2] Quy chế 2: điểm rèn luyện.\n\n[3] Quy chế 3: học phí.\n\n[4] Quy chế 4: thực tập.",
                tools = listOf("get_my_grades"),
                citations = (1..4).map {
                    AiCitationDto(
                        document = "Quy chế $it",
                        page = 10 + it,
                        excerpt = "Trích đoạn số $it nêu điều kiện tốt nghiệp.",
                        source = "ai-service",
                    )
                },
            ),
        )
    }

    /** A socket read timeout is a slow answer, not a dead network. */
    private class SlowSocketApi : StubApi() {
        override suspend fun aiChat(body: AiChatRequest): ApiEnvelopeDto<AiChatResponse> =
            throw SocketTimeoutException("read timed out")
    }

    /** An escaping cancellation must never leave the composer locked. */
    private class CancelledApi : StubApi() {
        override suspend fun aiChat(body: AiChatRequest): ApiEnvelopeDto<AiChatResponse> =
            throw CancellationException("scope cancelled")
    }

    /** The gateway's 429 carries its own diagnosis in the error body. */
    private class RateLimitedApi : StubApi() {
        override suspend fun aiChat(body: AiChatRequest): ApiEnvelopeDto<AiChatResponse> {
            val raw = Response.Builder()
                .request(Request.Builder().url("http://localhost/api/v1/ai/chat").build())
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .message("Too Many Requests")
                .build()
            throw HttpException(
                retrofit2.Response.error<AiChatResponse>(
                    "{\"data\":null,\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Quá nhiều lần thử.\"}}"
                        .toResponseBody("application/json".toMediaType()),
                    raw,
                ),
            )
        }
    }

    /** Each failure names its own prompt so a retry can be attributed to the right row. */
    private class PerPromptFailingApi : StubApi() {
        val seen = mutableListOf<String>()

        override suspend fun aiChat(body: AiChatRequest): ApiEnvelopeDto<AiChatResponse> {
            seen += body.message
            return ApiEnvelopeDto(
                error = ApiErrorDto(code = "AI_UNAVAILABLE", message = "loi cua ${body.message}"),
            )
        }
    }

    private class FailingApi : StubApi() {
        override suspend fun aiChat(body: AiChatRequest): ApiEnvelopeDto<AiChatResponse> =
            ApiEnvelopeDto(error = ApiErrorDto(code = "AI_UNAVAILABLE", message = "Trợ lý đang bảo trì."))
    }

    private fun sendPrompt(vm: ChatViewModel, prompt: String) {
        composeRule.onNodeWithText("Hỏi trợ lý AI...").performTextInput(prompt)
        composeRule.onNodeWithContentDescription("Gửi").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `typing and tapping send calls aiChat and shows both bubbles even offline`() {
        val vm = ChatViewModel(OfflineApi())
        composeRule.setContent { ChatScreen(viewModel = vm) }

        sendPrompt(vm, "Tuan nay hoc gi")

        val texts = vm.uiState.value.messages.map { it.text }
        assertTrue("user bubble must exist", texts.any { it == "Tuan nay hoc gi" })
        assertTrue("offline reply must exist", texts.any { it.contains("Không kết nối") })
        assertEquals("no fake greeting message any more", 2, vm.uiState.value.messages.size)
        assertTrue("offline banner must be raised", vm.uiState.value.offline)
    }

    @Test
    fun `send is inert while the input is blank`() {
        val vm = ChatViewModel(OfflineApi())
        composeRule.setContent { ChatScreen(viewModel = vm) }
        composeRule.onNodeWithContentDescription("Gửi").performClick()
        composeRule.waitForIdle()
        assertTrue("blank input must not send", vm.uiState.value.messages.none { it.role == ChatRole.USER })
    }

    @Test
    fun `first run shows starter prompts and no transcript`() {
        val vm = ChatViewModel(OfflineApi())
        composeRule.setContent { ChatScreen(viewModel = vm) }
        composeRule.onNodeWithText("Học kỳ này mình còn bao nhiêu tín chỉ?").assertExists()
        assertEquals(0, vm.uiState.value.messages.size)
    }

    @Test
    fun `tapping a citation chip reveals its excerpt`() {
        val vm = ChatViewModel(CitedApi())
        composeRule.setContent { ChatScreen(viewModel = vm) }

        sendPrompt(vm, "Minh con bao nhieu tin chi?")

        composeRule.onNodeWithText("[2] Quy chế 2 · tr.12").assertExists()
        composeRule.onNodeWithText("[2] Quy chế 2 · tr.12").performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("Trích đoạn số 2 nêu điều kiện tốt nghiệp.")
            .assertExists()
    }

    @Test
    fun `every citation renders numbered to match its in-body marker and the agent is attributed`() {
        val vm = ChatViewModel(CitedApi())
        composeRule.setContent { ChatScreen(viewModel = vm) }

        sendPrompt(vm, "Diem cua minh the nao?")

        (1..4).forEach {
            composeRule.onNodeWithText("[$it] Quy chế $it · tr.${10 + it}").assertExists()
        }
        // Chips and in-body markers must agree: a marker with no chip is untappable and a
        // chip with no marker cites nothing — exactly what per-sentence numbering broke.
        assertEquals(
            setOf("[1]", "[2]", "[3]", "[4]"),
            CITATION_MARKER.findAll(answerOf(vm)).map { it.value }.toSet(),
        )
        composeRule.onNodeWithText("Agent Agent Điểm").assertExists()
    }

    private fun answerOf(vm: ChatViewModel) = vm.uiState.value.messages
        .last { it.role == ChatRole.ASSISTANT && it.status == ChatStatus.OK }
        .text

    @Test
    fun `the sanitizer also drops directional marks and the word joiner`() {
        val spoofed = "a\u200Eb\u200Füc\u061Cd\u2060"
        assertEquals("ab\u00FCcd", sanitizeUntrusted(spoofed))
    }

    @Test
    fun `a backend error envelope renders an error row not an answer bubble`() {
        val vm = ChatViewModel(FailingApi())
        composeRule.setContent { ChatScreen(viewModel = vm) }

        sendPrompt(vm, "Sao khong tra loi?")

        val last = vm.uiState.value.messages.last()
        assertEquals(ChatStatus.ERROR, last.status)
        assertEquals("Trợ lý đang bảo trì.", last.text)
        assertEquals("the failed prompt must be retryable", "Sao khong tra loi?", last.retryPrompt)
        composeRule.onNodeWithText("Thử lại").assertExists()
    }

    @Test
    fun `retry resends the failed prompt without duplicating the user bubble`() {
        val vm = ChatViewModel(OfflineApi())
        composeRule.setContent { ChatScreen(viewModel = vm) }

        sendPrompt(vm, "Lich thi hom nay")
        assertEquals(2, vm.uiState.value.messages.size)

        composeRule.onNodeWithText("Thử lại").performClick()
        composeRule.waitForIdle()

        assertEquals(
            "retry must replace the error row, not append a second user bubble",
            listOf(ChatRole.USER, ChatRole.ASSISTANT),
            vm.uiState.value.messages.map { it.role },
        )
        assertEquals(1, vm.uiState.value.messages.count { it.role == ChatRole.USER })
    }

    @Test
    fun `ai client outlasts the gateway timeout without dropping the auth chain`() {
        // Regression for the false-offline defect: the gateway waits 60s for ai-service,
        // so an AI call riding the shared 20s client can never deliver a slow answer.
        // Built from a client that really carries an interceptor + authenticator, so a
        // switch to a fresh OkHttpClient() (which would 401 every AI call) fails here too.
        val auth = okhttp3.Interceptor { chain -> chain.proceed(chain.request()) }
        val base = OkHttpClient.Builder().addInterceptor(auth).authenticator { _, _ -> null }.build()
        val ai = NetworkModule.aiOkHttp(base)

        assertTrue("AI client must outlast the 60s gateway budget", ai.readTimeoutMillis >= 70_000)
        assertTrue("call budget must bound the whole exchange", ai.callTimeoutMillis >= 80_000)
        assertTrue("auth interceptor must survive the rebuilder", ai.interceptors.containsAll(base.interceptors))
        assertEquals("connection pool must stay shared", base.connectionPool, ai.connectionPool)
        assertEquals("dispatcher must stay shared", base.dispatcher, ai.dispatcher)
    }

    @Test
    fun `a socket read timeout is reported as a slow reply, not as lost network`() {
        // SocketTimeoutException extends IOException, so a ladder that tests IOException
        // first mislabelled every slow answer as "check your connection".
        val vm = ChatViewModel(SlowSocketApi())
        composeRule.setContent { ChatScreen(viewModel = vm) }

        sendPrompt(vm, "Trich doan van ban dai qua")

        val last = vm.uiState.value.messages.last()
        assertEquals(ChatStatus.ERROR, last.status)
        assertFalse("a slow answer must not raise the offline banner", vm.uiState.value.offline)
        assertTrue("must say too-slow, got: ${last.text}", last.text.contains("mất quá lâu"))
    }

    @Test
    fun `an escaping failure still unlocks the composer`() {
        // Regression for the timeout lock: the old ladder rethrew every CancellationException
        // — including the 70s timeout — so busy stayed true forever and HomeShell's hoisted VM
        // kept the input field disabled across tab switches.
        val vm = ChatViewModel(CancelledApi())
        composeRule.setContent { ChatScreen(viewModel = vm) }

        sendPrompt(vm, "Cau mot")

        assertFalse("busy must clear on every exit path", vm.uiState.value.busy)

        // A live composer must accept the next prompt; a locked one swallows it.
        composeRule.onNodeWithText("Hỏi trợ lý AI...").performTextInput("Cau hai")
        composeRule.onNodeWithContentDescription("Gửi").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf("Cau mot", "Cau hai"),
            vm.uiState.value.messages.filter { it.role == ChatRole.USER }.map { it.text },
        )
    }

    @Test
    fun `a rate limited reply shows the server diagnosis and offers no futile retry`() {
        val vm = ChatViewModel(RateLimitedApi())
        composeRule.setContent { ChatScreen(viewModel = vm) }

        sendPrompt(vm, "Hoi lien tiep ba cau")

        val last = vm.uiState.value.messages.last()
        assertEquals(ChatStatus.ERROR, last.status)
        assertEquals("Quá nhiều lần thử.", last.text)
        assertNull("resending at once only re-rolls the same window", last.retryPrompt)
        composeRule.onAllNodesWithText("Thử lại").assertCountEquals(0)
    }

    @Test
    fun `retrying an older failure resends that row and keeps the newer one`() {
        val api = PerPromptFailingApi()
        val vm = ChatViewModel(api)
        composeRule.setContent { ChatScreen(viewModel = vm) }

        sendPrompt(vm, "cau A")
        sendPrompt(vm, "cau B")
        assertEquals(listOf("cau A", "cau B"), api.seen)

        // The first "Thử lại" on screen belongs to the older row.
        composeRule.onAllNodesWithText("Thử lại")[0].performClick()
        composeRule.waitForIdle()

        assertEquals("older row must resend ITS prompt", listOf("cau A", "cau B", "cau A"), api.seen)
        assertTrue(
            "newer failure must survive an older row's retry",
            vm.uiState.value.messages.any { it.text == "loi cua cau B" },
        )
    }

    @Test
    fun `untrusted payloads cannot smuggle bidi or zero width characters`() {
        val spoofed = "Điều\u202Ekiểm\u200Btra\uFEFF"
        val cleaned = sanitizeUntrusted(spoofed)
        assertEquals("Điềukiểmtra", cleaned)
        assertTrue(cleaned.none { it == '\u202E' || it == '\u200B' || it == '\uFEFF' })
    }
}
