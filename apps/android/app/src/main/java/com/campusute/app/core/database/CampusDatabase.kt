package com.campusute.app.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first SSOT (ADR-0002): UI observes Room; the network only refreshes
 * it. Dates are ISO strings (yyyy-MM-dd) — simple, sortable, debuggable.
 */
@Entity(tableName = "schedule_sessions")
data class ScheduleSessionEntity(
    @PrimaryKey val id: String,
    val courseCode: String,
    val courseName: String,
    val lecturerName: String,
    val building: String,
    val room: String,
    val date: String,
    val startAt: String,
    val endAt: String,
    val conflict: Boolean,
)

@Dao
interface ScheduleSessionDao {
    @Query("SELECT * FROM schedule_sessions WHERE date = :date ORDER BY startAt")
    fun observeDay(date: String): Flow<List<ScheduleSessionEntity>>

    @Query("SELECT * FROM schedule_sessions WHERE date BETWEEN :from AND :to ORDER BY date, startAt")
    fun observeRange(from: String, to: String): Flow<List<ScheduleSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<ScheduleSessionEntity>)

    @Query("DELETE FROM schedule_sessions WHERE date BETWEEN :from AND :to")
    suspend fun clearRange(from: String, to: String)

    @Query("SELECT COUNT(*) FROM schedule_sessions")
    suspend fun count(): Int
}

@Database(entities = [ScheduleSessionEntity::class], version = 1, exportSchema = false)
abstract class CampusDatabase : RoomDatabase() {
    abstract fun scheduleSessionDao(): ScheduleSessionDao
}
