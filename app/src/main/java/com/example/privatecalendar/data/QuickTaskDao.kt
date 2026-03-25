package com.example.privatecalendar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface QuickTaskDao {
    @Query("SELECT * FROM quick_tasks WHERE completedAt IS NULL ORDER BY createdAt DESC")
    fun observePendingTasks(): Flow<List<QuickTask>>

    @Query("SELECT * FROM quick_tasks WHERE completedAt IS NOT NULL AND completedAt >= :cutoff ORDER BY completedAt DESC")
    fun observeRecentCompletedTasks(cutoff: LocalDateTime): Flow<List<QuickTask>>

    @Insert
    suspend fun insertTask(task: QuickTask)

    @Query("UPDATE quick_tasks SET completedAt = :completedAt WHERE id = :id")
    suspend fun markTaskCompleted(id: Int, completedAt: LocalDateTime)

    @Query("DELETE FROM quick_tasks WHERE completedAt IS NOT NULL")
    suspend fun clearTrash()

    @Query("DELETE FROM quick_tasks WHERE completedAt IS NOT NULL AND completedAt < :cutoff")
    suspend fun deleteCompletedBefore(cutoff: LocalDateTime)
}
