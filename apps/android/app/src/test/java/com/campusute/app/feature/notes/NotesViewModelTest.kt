package com.campusute.app.feature.notes

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
import com.campusute.app.core.network.SummarizeRequestDto
import com.campusute.app.core.network.SummarizeResponseDto
import com.campusute.app.core.network.TaskChangesDto
import com.campusute.app.core.network.TokenResponseDto
import com.campusute.app.core.network.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Notes CRUD + PROPOSE-ONLY gate (closeout plan Ruling R-B):
 * - summarize NEVER mutates the draft and NEVER writes to the API
 * - only acceptProposal() inserts the AI text into the draft
 * - nothing is persisted until the user explicitly saves
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class RecordingApi : CampusApi {
        val writes = mutableListOf<String>()
        val notes = mutableListOf(NoteDto("n1", "Đã có", "nội dung cũ", "2026-09-19T00:00:00Z"))
        var summarizeResult = SummarizeResponseDto("Học JOIN • Học INDEX", true)
        var offline = false

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

        override suspend fun notes(): ApiEnvelopeDto<List<NoteDto>> {
            if (offline) throw IOException("offline")
            return ApiEnvelopeDto(data = notes.toList())
        }

        override suspend fun createNote(body: NoteRequestDto): ApiEnvelopeDto<NoteDto> {
            writes += "create"
            val saved = NoteDto("n${notes.size + 1}", body.title, body.content, "2026-09-19T01:00:00Z")
            notes.add(saved)
            return ApiEnvelopeDto(data = saved)
        }

        override suspend fun updateNote(id: String, body: NoteRequestDto): ApiEnvelopeDto<NoteDto> {
            writes += "update:$id"
            val saved = NoteDto(id, body.title, body.content, "2026-09-19T02:00:00Z")
            val idx = notes.indexOfFirst { it.id == id }
            if (idx >= 0) notes[idx] = saved
            return ApiEnvelopeDto(data = saved)
        }

        override suspend fun deleteNote(id: String): ApiEnvelopeDto<Map<String, String>> {
            writes += "delete:$id"
            notes.removeAll { it.id == id }
            return ApiEnvelopeDto(data = mapOf("status" to "DELETED"))
        }

        override suspend fun aiSummarize(body: SummarizeRequestDto): ApiEnvelopeDto<SummarizeResponseDto> {
            if (offline) throw IOException("offline")
            return ApiEnvelopeDto(data = summarizeResult)
        }
    }

    @Test
    fun `create round-trip adds note to list and closes editor`() = runTest(dispatcher) {
        val api = RecordingApi()
        val vm = NotesViewModel(api)
        dispatcher.scheduler.advanceUntilIdle()

        vm.openNew()
        vm.onTitleChange("Ôn JOIN")
        vm.onContentChange("INNER vs LEFT JOIN")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("create"), api.writes)
        assertEquals(2, vm.notes.value.size)
        assertTrue(vm.notes.value.any { it.title == "Ôn JOIN" })
        assertNull(vm.draft.value)
    }

    @Test
    fun `editing an existing note persists through updateNote`() = runTest(dispatcher) {
        val api = RecordingApi()
        val vm = NotesViewModel(api)
        dispatcher.scheduler.advanceUntilIdle()

        vm.openEdit(vm.notes.value.first())
        vm.onTitleChange("Tiêu đề sửa")
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("update:n1"), api.writes)
        assertEquals("Tiêu đề sửa", vm.notes.value.first().title)
    }

    @Test
    fun `summarize proposes without mutating draft or writing to api`() = runTest(dispatcher) {
        val api = RecordingApi()
        val vm = NotesViewModel(api)
        dispatcher.scheduler.advanceUntilIdle()

        vm.openEdit(vm.notes.value.first())
        vm.summarize()
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull("summary must surface as a proposal", vm.proposal.value)
        assertEquals("draft must be untouched by the AI result", "nội dung cũ", vm.draft.value!!.content)
        assertTrue("no API write may happen from summarize", api.writes.isEmpty())

        // Still nothing written after the user ACCEPTS — only the draft changes.
        vm.acceptProposal()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.draft.value!!.content.contains("[Tóm tắt AI]"))
        assertTrue(vm.draft.value!!.content.contains("Học JOIN • Học INDEX"))
        assertTrue("accept must not persist; only explicit save writes", api.writes.isEmpty())
    }

    @Test
    fun `dismiss proposal keeps draft untouched`() = runTest(dispatcher) {
        val api = RecordingApi()
        val vm = NotesViewModel(api)
        dispatcher.scheduler.advanceUntilIdle()

        vm.openEdit(vm.notes.value.first())
        vm.summarize()
        dispatcher.scheduler.advanceUntilIdle()
        vm.dismissProposal()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.proposal.value)
        assertEquals("nội dung cũ", vm.draft.value!!.content)
        assertTrue(api.writes.isEmpty())
    }

    @Test
    fun `offline load surfaces retryable error`() = runTest(dispatcher) {
        val api = RecordingApi().apply { offline = true }
        val vm = NotesViewModel(api)
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.error.value)
        assertTrue(vm.loading.value.not())
    }
}
