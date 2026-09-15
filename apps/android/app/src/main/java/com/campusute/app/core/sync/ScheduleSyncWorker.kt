package com.campusute.app.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.campusute.app.core.data.ScheduleRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/** Background timetable sync (ADR-0002): network refreshes Room, never the UI. */
@HiltWorker
class ScheduleSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduleRepository: ScheduleRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        return when (scheduleRepository.sync(monday.toString(), monday.plusDays(6).toString())) {
            is com.campusute.app.core.data.ScheduleSyncOutcome.Success -> Result.success()
            com.campusute.app.core.data.ScheduleSyncOutcome.Offline -> Result.retry()
            is com.campusute.app.core.data.ScheduleSyncOutcome.Failure -> Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "campusute-schedule-sync"
    }
}
