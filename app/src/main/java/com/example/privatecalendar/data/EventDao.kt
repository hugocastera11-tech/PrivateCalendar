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
    fun getAllEventsSync(): List<Event>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventById(id: Int): Event?

    @Query("SELECT * FROM events WHERE externalId = :externalId LIMIT 1")
    suspend fun getEventByExternalId(externalId: String): Event?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEvent(event: Event): Long

    @Update
    fun updateEvent(event: Event)

    @Delete
    fun deleteEvent(event: Event)

    // Category DAOs
    @Query("SELECT * FROM event_categories")
    fun getAllCategories(): Flow<List<EventCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: EventCategory)

    @Update
    suspend fun updateCategory(category: EventCategory)

    @Delete
    suspend fun deleteCategory(category: EventCategory)
}
