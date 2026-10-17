package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM local_tasks ORDER BY isCompleted ASC, priority DESC, dueDateTime ASC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM local_tasks WHERE isCompleted = 0 ORDER BY priority DESC, dueDateTime ASC")
    fun getPendingTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM local_tasks WHERE isCompleted = 0 ORDER BY priority DESC, dueDateTime ASC")
    suspend fun getPendingTasks(): List<TaskEntity>

    @Query("SELECT * FROM local_tasks WHERE isCompleted = 0 AND dueDateTime IS NOT NULL AND dueDateTime < :currentTime")
    suspend fun getOverdueTasks(currentTime: Long): List<TaskEntity>

    @Query("SELECT * FROM local_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE local_tasks SET isCompleted = :completed, completedAt = :completedAt, updatedAtEpoch = :updatedAt WHERE id = :id")
    suspend fun setCompletion(id: String, completed: Boolean, completedAt: Long?, updatedAt: Long)

    @Query("DELETE FROM local_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM local_tasks")
    suspend fun clearAll()
}
