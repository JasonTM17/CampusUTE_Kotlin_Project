package com.campusute.app.feature.schedule

import com.campusute.app.core.data.ScheduleRepository
import com.campusute.app.core.data.ScheduleSyncOutcome
import com.campusute.app.core.database.ScheduleSessionDao
import com.campusute.app.core.database.ScheduleSessionEntity
import com.campusute.app.core.network.ApiEnvelopeDto
import com.campusute.app.core.network.ApiErrorDto
import com.campusute.app.core.network.CampusApi
import com.campusute.app.core.network.LoginRequestDto
import com.campusute.app.core.network.RefreshRequestDto
import com.campusute.app.core.network.ScheduleSessionDto
import com.campusute.app.core.network.TokenResponseDto
import com.campusute.app.core.network.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

/** Repository contract: Room upsert on success; stale data kept when offline. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleRepositoryTest {

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

    private class FakeApi(var behaviour: Behaviour) : CampusApi {
        enum class Behaviour { OK_CONFLICT, OK_CLEAN, OFFLINE, ERROR }
        private val session = ScheduleSessionDto(
            id = "s1", courseCode = "SE104", courseName = "Software Engineering",
            lecturerName = "Trần Thị Bích", building = "A", room = "A2-05",
            date = "2026-09-15", startAt = "2026-09-15T08:00:00+07:00",
            endAt = "2026-09-15T09:30:00+07:00", conflict = true,
        )
        override suspend fun login(body: LoginRequestDto) = TODO()
        override suspend fun refresh(body: RefreshRequestDto) = TODO()
        override suspend fun logout(body: RefreshRequestDto): ApiEnvelopeDto<Map<String, String>> =
            ApiEnvelopeDto(data = emptyMap())
        override suspend fun me() = ApiEnvelopeDto(data = UserDto("1", "e", "n", null, null, listOf("STUDENT")))
        override suspend fun taskChanges(since: String): ApiEnvelopeDto<com.campusute.app.core.network.TaskChangesDto> =
            ApiEnvelopeDto(data = com.campusute.app.core.network.TaskChangesDto(emptyList(), "1970-01-01T00:00:00Z"))
        override suspend fun taskSync(body: com.campusute.app.core.network.SyncRequestDto): ApiEnvelopeDto<com.campusute.app.core.network.SyncResponseDto> =
            ApiEnvelopeDto(data = com.campusute.app.core.network.SyncResponseDto(emptyList(), "1970-01-01T00:00:00Z"))
        override suspend fun scheduleSessions(from: String, to: String): ApiEnvelopeDto<List<ScheduleSessionDto>> =
            when (behaviour) {
                Behaviour.OK_CONFLICT -> ApiEnvelopeDto(
                    data = listOf(session),
                    meta = mapOf("conflict" to kotlinx.serialization.json.JsonPrimitive(true)),
                )
                Behaviour.OK_CLEAN -> ApiEnvelopeDto(
                    data = listOf(session.copy(conflict = false)),
                )
                Behaviour.OFFLINE -> throw IOException("offline")
                Behaviour.ERROR -> ApiEnvelopeDto(error = ApiErrorDto("SYSTEM_INTERNAL", "boom"))
            }
    }

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `sync upserts sessions and propagates conflict flag`() = runTest {
        val dao = FakeDao()
        val repo = ScheduleRepository(FakeApi(FakeApi.Behaviour.OK_CONFLICT), dao)
        val outcome = repo.sync("2026-09-15", "2026-09-21")
        assertTrue(outcome is ScheduleSyncOutcome.Success && outcome.conflict)
        assertEquals(1, dao.count())
        assertEquals("SE104", dao.rows.value.first().courseCode)
    }

    @Test
    fun `offline sync keeps previously synced data`() = runTest {
        val dao = FakeDao()
        val repo = ScheduleRepository(FakeApi(FakeApi.Behaviour.OK_CONFLICT), dao)
        repo.sync("2026-09-15", "2026-09-21")
        repo.sync("2026-09-22", "2026-09-28") // fresh week range clears nothing outside
        val offlineRepo = ScheduleRepository(FakeApi(FakeApi.Behaviour.OFFLINE), dao)
        val outcome = offlineRepo.sync("2026-09-15", "2026-09-21")
        assertTrue("offline sync must report Offline", outcome is ScheduleSyncOutcome.Offline)
        assertEquals("stale data must survive offline sync", 1, dao.count())
    }

    @Test
    fun `server error maps to Failure outcome`() = runTest {
        val outcome = ScheduleRepository(FakeApi(FakeApi.Behaviour.ERROR), FakeDao())
            .sync("2026-09-15", "2026-09-21")
        assertTrue(outcome is ScheduleSyncOutcome.Failure)
    }
}
