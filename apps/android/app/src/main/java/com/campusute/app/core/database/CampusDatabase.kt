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

// ---- Phase 3: study tasks (sync-able) ----

@Entity(tableName = "study_tasks")
data class StudyTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val dueDate: String? = null,
    val done: Boolean = false,
    val deleted: Boolean = false,
    val version: Long = 0, // 0 = local-only (not yet acknowledged by server)
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface StudyTaskDao {
    @Query("SELECT * FROM study_tasks WHERE deleted = 0 ORDER BY dueDate, updatedAt")
    fun observeActive(): Flow<List<StudyTaskEntity>>

    @Query("SELECT * FROM study_tasks WHERE id = :id")
    suspend fun byId(id: String): StudyTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<StudyTaskEntity>)

    @Query("DELETE FROM study_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM study_tasks")
    suspend fun count(): Int
}

@Entity(tableName = "pending_ops")
data class PendingOpEntity(
    @PrimaryKey val clientOpId: String,
    val opType: String, // CREATE | UPDATE | DELETE
    val taskId: String?,
    val baseVersion: Long?,
    val title: String?,
    val dueDate: String?,
    val done: Boolean?,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface PendingOpDao {
    @Query("SELECT * FROM pending_ops ORDER BY createdAt LIMIT :limit")
    suspend fun peek(limit: Int): List<PendingOpEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(op: PendingOpEntity)

    @Query("DELETE FROM pending_ops WHERE clientOpId IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM pending_ops")
    suspend fun count(): Int
}

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface SyncStateDao {
    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: SyncStateEntity)
}

@Database(
    entities = [
        ScheduleSessionEntity::class,
        StudyTaskEntity::class,
        PendingOpEntity::class,
        SyncStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CampusDatabase : RoomDatabase() {
    abstract fun scheduleSessionDao(): ScheduleSessionDao
    abstract fun studyTaskDao(): StudyTaskDao
    abstract fun pendingOpDao(): PendingOpDao
    abstract fun syncStateDao(): SyncStateDao
}
