package com.example.privatecalendar.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE date = :date ORDER BY time ASC")
    fun getEventsForDate(date: LocalDate): Flow<List<Event>>

    @Query("SELECT DISTINCT date FROM events")
    fun getDatesWithEvents(): Flow<List<LocalDate>>

    @Query("SELECT * FROM events")
    fun getAllEvents(): Flow<List<Event>>

    @Query("SELECT * FROM events")
    suspend fun getAllEventsSync(): List<Event>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: Event): Long

    @Update
    suspend fun updateEvent(event: Event)

    @Delete
    suspend fun deleteEvent(event: Event)
}
