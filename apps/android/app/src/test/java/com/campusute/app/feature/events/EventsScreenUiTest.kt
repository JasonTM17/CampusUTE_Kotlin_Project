package com.campusute.app.feature.events

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.campusute.app.core.network.ApiEnvelopeDto
import com.campusute.app.core.network.ApiErrorDto
import com.campusute.app.core.network.CampusEventDto
import com.campusute.app.core.network.StubCampusApi
import com.campusute.app.core.network.httpRefusal
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
 * Gate for catalogue entry #9. The interesting property is not the list — it is that a double tap
 * cannot take two seats, which depends on the client reusing one Idempotency-Key per intent
 * because the backend rejects a blank key and deduplicates on a repeated one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventsScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }

    @After fun tearDown() { Dispatchers.resetMain() }

    private class EventsApi(
        var payload: ApiEnvelopeDto<List<CampusEventDto>> = ApiEnvelopeDto(data = emptyList()),
        var loadFailure: Throwable? = null,
        var registerFailure: Throwable? = null,
        var registerEnvelopeFailure: ApiErrorDto? = null,
    ) : StubCampusApi() {
        val keys = mutableListOf<String>()

        override suspend fun events(): ApiEnvelopeDto<List<CampusEventDto>> {
            loadFailure?.let { throw it }
            return payload
        }

        override suspend fun registerEvent(id: String, idempotencyKey: String): ApiEnvelopeDto<Map<String, String>> {
            keys += idempotencyKey
            registerFailure?.let { throw it }
            registerEnvelopeFailure?.let { return ApiEnvelopeDto(error = it) }
            return ApiEnvelopeDto(data = mapOf("status" to "REGISTERED"))
        }
    }

    private fun event(
        id: String = "e1",
        title: String = "Hội thảo nghề nghiệp",
        seats: Long = 12,
        registered: Boolean = false,
        startsAt: String = "2099-01-01T08:00:00Z",
    ) = CampusEventDto(
        id = id,
        code = "EV-$id",
        title = title,
        startsAt = startsAt,
        location = "Hội trường A",
        registered = registered,
        seatsLeft = seats,
    )

    private fun show(api: EventsApi) {
        composeRule.setContent { MaterialTheme { EventsScreen(EventsViewModel(api)) } }
        composeRule.waitForIdle()
    }

    @Test
    fun `an open event shows seats left and one action`() {
        show(EventsApi(payload = ApiEnvelopeDto(data = listOf(event()))))
        composeRule.onNodeWithText("Hội thảo nghề nghiệp").assertExists()
        composeRule.onNodeWithText("12 chỗ").assertExists()
        composeRule.onNodeWithText("Ghi danh").assertExists()
    }

    @Test
    fun `a registered event offers no second action`() {
        show(EventsApi(payload = ApiEnvelopeDto(data = listOf(event(registered = true)))))
        composeRule.onNodeWithText("Đã ghi danh").assertExists()
        composeRule.onNodeWithText("Ghi danh").assertDoesNotExist()
    }

    @Test
    fun `a full event says so and hides the action`() {
        show(EventsApi(payload = ApiEnvelopeDto(data = listOf(event(seats = 0)))))
        composeRule.onNodeWithText("Hết chỗ").assertExists()
        composeRule.onNodeWithText("Ghi danh").assertDoesNotExist()
    }

    @Test
    fun `a past event is closed rather than open`() {
        show(EventsApi(payload = ApiEnvelopeDto(data = listOf(event(startsAt = "2020-01-01T08:00:00Z")))))
        composeRule.onNodeWithText("Đã kết thúc").assertExists()
        composeRule.onNodeWithText("Ghi danh").assertDoesNotExist()
    }

    @Test
    fun `a repeated intent sends one request with one idempotency key`() {
        val api = EventsApi(payload = ApiEnvelopeDto(data = listOf(event())))
        show(api)
        val vm = EventsViewModel(api)
        vm.load()
        composeRule.waitForIdle()
        val row = vm.uiState.value.events.first()

        // Driven at the ViewModel because the UI removes its own button once the row flips — the
        // stale-argument path is exactly what must not be able to book a second seat.
        vm.register(row)
        vm.register(row)
        composeRule.waitForIdle()

        assertEquals("the second call must not mint a second key", 1, api.keys.size)
        assertEquals(1, api.keys.distinct().size)
        assertEquals(true, vm.uiState.value.events.first().registered)
        assertEquals(11L, vm.uiState.value.events.first().seatsLeft)
    }

    @Test
    fun `a server refusal is shown with the server's own reason`() {
        val api = EventsApi(
            payload = ApiEnvelopeDto(data = listOf(event())),
            registerFailure = httpRefusal(400, "Sự kiện đã đủ chỗ."),
        )
        show(api)
        composeRule.onNodeWithText("Ghi danh").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Sự kiện đã đủ chỗ.").assertExists()
        // The row must not flip to registered on a refusal.
        composeRule.onNodeWithText("Đã ghi danh").assertDoesNotExist()
        composeRule.onNodeWithText("12 chỗ").assertExists()
    }

    @Test
    fun `an envelope-level refusal is shown too`() {
        val api = EventsApi(payload = ApiEnvelopeDto(data = listOf(event())))
        api.registerEnvelopeFailure = ApiErrorDto("VALIDATION_FAILED", "Sự kiện đã đủ chỗ.")
        show(api)
        composeRule.onNodeWithText("Ghi danh").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Sự kiện đã đủ chỗ.").assertExists()
    }

    @Test
    fun `a refused list is not rendered as an empty calendar`() {
        show(EventsApi(loadFailure = httpRefusal(500, "Lỗi máy chủ")))
        composeRule.onNodeWithText("Lỗi máy chủ").assertExists()
        composeRule.onNodeWithText("Chưa có sự kiện nào").assertDoesNotExist()
    }

    @Test
    fun `a healthy empty list reads as an honest zero`() {
        show(EventsApi())
        composeRule.onNodeWithText("Chưa có sự kiện nào").assertExists()
    }
}
