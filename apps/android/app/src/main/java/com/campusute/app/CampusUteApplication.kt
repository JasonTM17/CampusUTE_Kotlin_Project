package com.campusute.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.campusute.app.core.sync.ScheduleSyncWorker
import dagger.hilt.android.HiltAndroidApp
import java.time.Duration
import javax.inject.Inject

@HiltAndroidApp
class CampusUteApplication : Application(), Configuration.Provider {

    // @HiltAndroidApp classes must use FIELD injection (constructor injection
    // crashes at onCreate — caught on the emulator in the hardening round).
    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        schedulePeriodicSync()
    }

    private fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(Duration.ofHours(6))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1))
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ScheduleSyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
