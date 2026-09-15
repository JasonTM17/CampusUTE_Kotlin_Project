package com.campusute.app.core.data

import com.campusute.app.core.database.ScheduleSessionDao
import com.campusute.app.core.database.ScheduleSessionEntity
import com.campusute.app.core.network.CampusApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScheduleSyncOutcome {
    data class Success(val count: Int, val conflict: Boolean) : ScheduleSyncOutcome
    data object Offline : ScheduleSyncOutcome
    data class Failure(val message: String) : ScheduleSyncOutcome
}

/**
 * Room is the single source of truth: sync() only refreshes Room, and the UI
 * observes Flow queries. A failed sync leaves the last good data in place
 * (offline-first contract, ADR-0002).
 */
@Singleton
class ScheduleRepository @Inject constructor(
    private val api: CampusApi,
    private val dao: ScheduleSessionDao,
) {
    fun observeDay(date: String): Flow<List<ScheduleSessionEntity>> = dao.observeDay(date)

    suspend fun sync(from: String, to: String): ScheduleSyncOutcome = try {
        val envelope = api.scheduleSessions(from, to)
        val sessions = envelope.data
        when {
            envelope.error != null -> ScheduleSyncOutcome.Failure(envelope.error.message)
            sessions == null -> ScheduleSyncOutcome.Failure("Phản hồi lịch học không hợp lệ.")
            else -> {
                dao.clearRange(from, to)
                dao.upsertAll(
                    sessions.map {
                        ScheduleSessionEntity(
                            id = it.id,
                            courseCode = it.courseCode,
                            courseName = it.courseName,
                            lecturerName = it.lecturerName,
                            building = it.building,
                            room = it.room,
                            date = it.date,
                            startAt = it.startAt,
                            endAt = it.endAt,
                            conflict = it.conflict,
                        )
                    },
                )
                val conflict = envelope.meta["conflict"]?.toString()?.toBooleanStrictOrNull() ?: false
                ScheduleSyncOutcome.Success(sessions.size, conflict)
            }
        }
    } catch (_: java.io.IOException) {
        ScheduleSyncOutcome.Offline
    } catch (_: Exception) {
        ScheduleSyncOutcome.Failure("Không đồng bộ được lịch học.")
    }

    suspend fun count(): Int = dao.count()
}
