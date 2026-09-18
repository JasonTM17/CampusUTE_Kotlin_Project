package com.campusute.app.feature.auth

import com.campusute.app.core.data.SessionRepository
import com.campusute.app.core.network.ApiEnvelopeDto
import com.campusute.app.core.network.ApiErrorDto
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.LoginRequestDto
import com.campusute.app.core.network.RefreshRequestDto
import com.campusute.app.core.network.TokenResponseDto
import com.campusute.app.core.network.UserDto
import com.campusute.app.core.network.AssignmentDto
import com.campusute.app.core.network.NoteDto
import com.campusute.app.core.network.NoteRequestDto
import com.campusute.app.core.network.SubmitAssignmentDto
import com.campusute.app.core.network.InboxDto
import com.campusute.app.core.security.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeTokenStore : TokenStore {
        var access: String? = null
        var refresh: String? = null
        override fun saveTokens(accessToken: String, refreshToken: String) { access = accessToken; refresh = refreshToken }
        override fun accessToken() = access
        override fun refreshToken() = refresh
        override fun clear() { access = null; refresh = null }
    }

    private class FakeApi(var behaviour: Behaviour) : CampusApi {
        enum class Behaviour { OK, REJECTED, OFFLINE }
        override suspend fun login(body: LoginRequestDto) = when (behaviour) {
            Behaviour.OK -> ApiEnvelopeDto(data = TokenResponseDto("a", "r", 15, user))
            Behaviour.REJECTED -> ApiEnvelopeDto(error = ApiErrorDto("AUTH_INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng."))
            Behaviour.OFFLINE -> throw IOException("offline")
        }
        override suspend fun refresh(body: RefreshRequestDto) = TODO()
        override suspend fun logout(body: RefreshRequestDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = emptyMap())
        override suspend fun me(): ApiEnvelopeDto<UserDto> = ApiEnvelopeDto(data = user)
        override suspend fun aiChat(body: com.campusute.app.core.network.AiChatRequest): ApiEnvelopeDto<com.campusute.app.core.network.AiChatResponse> =
            ApiEnvelopeDto(data = com.campusute.app.core.network.AiChatResponse("ok"))
        override suspend fun scheduleSessions(
            from: String,
            to: String,
        ): ApiEnvelopeDto<List<com.campusute.app.core.network.ScheduleSessionDto>> = ApiEnvelopeDto(data = emptyList())
        override suspend fun taskChanges(since: String): ApiEnvelopeDto<com.campusute.app.core.network.TaskChangesDto> =
            ApiEnvelopeDto(data = com.campusute.app.core.network.TaskChangesDto(emptyList(), "1970-01-01T00:00:00Z"))
        override suspend fun taskSync(body: com.campusute.app.core.network.SyncRequestDto): ApiEnvelopeDto<com.campusute.app.core.network.SyncResponseDto> =
            ApiEnvelopeDto(data = com.campusute.app.core.network.SyncResponseDto(emptyList(), "1970-01-01T00:00:00Z"))
        override suspend fun assignmentsMe(): ApiEnvelopeDto<List<AssignmentDto>> = ApiEnvelopeDto(data = emptyList())
        override suspend fun submitAssignment(id: String, body: SubmitAssignmentDto): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = emptyMap())
        override suspend fun notes(): ApiEnvelopeDto<List<NoteDto>> = ApiEnvelopeDto(data = emptyList())
        override suspend fun createNote(body: NoteRequestDto): ApiEnvelopeDto<NoteDto> = ApiEnvelopeDto()
        override suspend fun markNotificationRead(id: String): ApiEnvelopeDto<Map<String, String>> = ApiEnvelopeDto(data = emptyMap())
        override suspend fun notifications(): ApiEnvelopeDto<InboxDto> = ApiEnvelopeDto(data = InboxDto())
    }
    private fun viewModel(api: CampusApi, store: TokenStore) =
        LoginViewModel(SessionRepository(api, store))

    @Test
    fun `successful login persists tokens and emits success`() = runTest(dispatcher) {
        val store = FakeTokenStore()
        val vm = viewModel(FakeApi(FakeApi.Behaviour.OK), store)
        vm.onEmailChange("student@demo.campusute.vn")
        vm.onPasswordChange("Demo#Student1")
        vm.login()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.success)
        assertEquals("a", store.access)
        assertEquals("r", store.refresh)
    }

    @Test
    fun `rejected login surfaces friendly error without tokens`() = runTest(dispatcher) {
        val store = FakeTokenStore()
        val vm = viewModel(FakeApi(FakeApi.Behaviour.REJECTED), store)
        vm.onEmailChange("student@demo.campusute.vn")
        vm.onPasswordChange("wrong-pass-1")
        vm.login()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.success)
        assertEquals("Email hoặc mật khẩu không đúng.", vm.uiState.value.error)
        assertEquals(null, store.access)
    }

    @Test
    fun `offline login maps to connection message`() = runTest(dispatcher) {
        val vm = viewModel(FakeApi(FakeApi.Behaviour.OFFLINE), FakeTokenStore())
        vm.onEmailChange("student@demo.campusute.vn")
        vm.onPasswordChange("Demo#Student1")
        vm.login()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.success)
        assertTrue(vm.uiState.value.error!!.contains("Không thể kết nối"))
    }

    @Test
    fun `submit disabled while inputs incomplete`() = runTest(dispatcher) {
        val vm = viewModel(FakeApi(FakeApi.Behaviour.OK), FakeTokenStore())
        vm.onEmailChange("a@b.vn")
        vm.onPasswordChange("123")
        assertFalse(vm.uiState.value.canSubmit)
    }

    companion object {
        val user = UserDto("1", "student@demo.campusute.vn", "Nguyễn Văn Sơn", "21110101", "CNTT", listOf("STUDENT"))
    }
}
