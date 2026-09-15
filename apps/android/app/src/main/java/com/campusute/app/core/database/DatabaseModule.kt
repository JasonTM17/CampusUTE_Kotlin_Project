package com.campusute.app.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CampusDatabase =
        Room.databaseBuilder(context, CampusDatabase::class.java, "campusute.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun scheduleSessionDao(db: CampusDatabase): ScheduleSessionDao = db.scheduleSessionDao()

    @Provides
    fun studyTaskDao(db: CampusDatabase): StudyTaskDao = db.studyTaskDao()

    @Provides
    fun pendingOpDao(db: CampusDatabase): PendingOpDao = db.pendingOpDao()

    @Provides
    fun syncStateDao(db: CampusDatabase): SyncStateDao = db.syncStateDao()
}
