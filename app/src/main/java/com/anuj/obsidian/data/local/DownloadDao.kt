package com.anuj.obsidian.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.anuj.obsidian.data.model.DownloadStatus
import com.anuj.obsidian.data.model.DownloadTask
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY startedAt DESC")
    fun getAllTasks(): Flow<List<DownloadTask>>

    @Query("SELECT * FROM download_tasks WHERE status NOT IN ('COMPLETED', 'COMPLETED_WITHOUT_TAGS', 'FAILED', 'CANCELLED') ORDER BY startedAt ASC")
    fun getActiveTasks(): Flow<List<DownloadTask>>

    @Query("SELECT * FROM download_tasks WHERE status NOT IN ('COMPLETED', 'COMPLETED_WITHOUT_TAGS', 'FAILED', 'CANCELLED') ORDER BY startedAt ASC")
    suspend fun getActiveTasksDirect(): List<DownloadTask>

    @Query("SELECT * FROM download_tasks WHERE taskId = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): DownloadTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTask): Long

    @Update
    suspend fun updateTask(task: DownloadTask)

    @Delete
    suspend fun deleteTask(task: DownloadTask)

    @Query("DELETE FROM download_tasks WHERE taskId = :taskId")
    suspend fun deleteTaskById(taskId: Long)

    @Query("DELETE FROM download_tasks WHERE status IN ('COMPLETED', 'COMPLETED_WITHOUT_TAGS')")
    suspend fun clearCompletedTasks()

    @Query("UPDATE download_tasks SET status = :status, progress = :progress WHERE taskId = :taskId")
    suspend fun updateProgress(taskId: Long, progress: Float, status: DownloadStatus)
}
