package com.campusute.app.feature.auth

import com.campusute.app.core.data.LoginFailure
import com.campusute.app.core.data.SessionRepository
import com.campusute.app.core.network.ApiEnvelopeDto
import com.campusute.app.core.network.ApiErrorDto
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.LoginRequestDto
import com.campusute.app.core.network.RefreshRequestDto
import com.campusute.app.core.network.StubCampusApi
import com.campusute.app.core.network.TokenResponseDto
import com.campusute.app.core.network.UserDto
import com.campusute.app.core.network.httpRefusal
import com.campusute.app.core.security.TokenStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Gate for catalogue entry #8: each login failure must arrive as a *different* cause.
 *
 * `SessionRepository` used to funnel every `HttpException` into "Email hoặc mật khẩu không đúng.",
 * so a genuine 429 from the app's own rate limiter blamed the student's typing.
 */
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

    private class FakeApi(var behaviour: Behaviour) : StubCampusApi() {
        enum class Behaviour { OK, REJECTED, RATE_LIMITED, SERVER_ERROR, OFFLINE }

        override suspend fun login(body: LoginRequestDto) = when (behaviour) {
            Behaviour.OK -> ApiEnvelopeDto(data = TokenResponseDto("a", "r", 15, user))
            Behaviour.REJECTED -> ApiEnvelopeDto(
                error = ApiErrorDto("AUTH_INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng."),
            )
            Behaviour.RATE_LIMITED -> throw httpRefusal(429, "Quá nhiều lần thử, vui lòng thử lại sau một phút.")
            Behaviour.SERVER_ERROR -> throw httpRefusal(500, "Lỗi nội bộ.")
            Behaviour.OFFLINE -> throw IOException("offline")
        }

        override suspend fun refresh(body: RefreshRequestDto) = TODO()
    }

    private fun viewModel(api: CampusApi, store: TokenStore = FakeTokenStore()) =
        LoginViewModel(SessionRepository(api, store))

    private suspend fun attempt(behaviour: FakeApi.Behaviour, store: TokenStore = FakeTokenStore()) =
        viewModel(FakeApi(behaviour), store).also { vm ->
            vm.onEmailChange("student@demo.campusute.vn")
            vm.onPasswordChange("Demo#Student1")
            vm.login()
            // runCurrent, not advanceUntilIdle: a 429 starts a one-second ticker whose delays
            // advanceUntilIdle would fast-forward to zero, erasing the very state under test.
            dispatcher.scheduler.runCurrent()
        }

    @Test
    fun `successful login persists tokens and emits success`() = runTest(dispatcher) {
        val store = FakeTokenStore()
        val vm = attempt(FakeApi.Behaviour.OK, store)
        assertTrue(vm.uiState.value.success)
        assertEquals("a", store.access)
        assertEquals("r", store.refresh)
    }

    @Test
    fun `rejected login names the credential cause without storing tokens`() = runTest(dispatcher) {
        val store = FakeTokenStore()
        val vm = attempt(FakeApi.Behaviour.REJECTED, store)
        assertFalse(vm.uiState.value.success)
        assertEquals(LoginFailure.InvalidCredentials, vm.uiState.value.failure)
        assertEquals("Email hoặc mật khẩu không đúng.", vm.uiState.value.message)
        assertEquals(null, store.access)
        assertEquals("a wrong password must not lock the button", 0, vm.uiState.value.cooldownSeconds)
    }

    @Test
    fun `a rate limited login is not reported as a bad password`() = runTest(dispatcher) {
        val vm = attempt(FakeApi.Behaviour.RATE_LIMITED)
        // This is the defect: 429 used to land in the same catch as 401.
        assertEquals(LoginFailure.RateLimited, vm.uiState.value.failure)
        assertTrue(vm.uiState.value.message!!.contains("Quá nhiều lần thử"))
        assertEquals(60, vm.uiState.value.cooldownSeconds)
        assertFalse("the client must hold off while the window may still be full", vm.uiState.value.canSubmit)
    }

    @Test
    fun `a server fault is not reported as a bad password`() = runTest(dispatcher) {
        val vm = attempt(FakeApi.Behaviour.SERVER_ERROR)
        assertEquals(LoginFailure.ServerError, vm.uiState.value.failure)
        assertFalse(vm.uiState.value.message!!.contains("mật khẩu không đúng"))
    }

    @Test
    fun `offline login maps to the connection cause`() = runTest(dispatcher) {
        val vm = attempt(FakeApi.Behaviour.OFFLINE)
        assertEquals(LoginFailure.Offline, vm.uiState.value.failure)
        assertTrue(vm.uiState.value.message!!.contains("Không thể kết nối"))
    }

    @Test
    fun `editing the credentials clears the previous diagnosis`() = runTest(dispatcher) {
        val vm = attempt(FakeApi.Behaviour.REJECTED)
        vm.onPasswordChange("Demo#Student2")
        assertNull(vm.uiState.value.failure)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `submit disabled while inputs incomplete`() = runTest(dispatcher) {
        val vm = viewModel(FakeApi(FakeApi.Behaviour.OK))
        vm.onEmailChange("a@b.vn")
        vm.onPasswordChange("123")
        assertFalse(vm.uiState.value.canSubmit)
    }

    companion object {
        val user = UserDto("1", "student@demo.campusute.vn", "Nguyễn Văn Sơn", "21110101", "CNTT", listOf("STUDENT"))
    }
}
