package com.example.privatecalendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "quick_tasks")
data class QuickTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: EncryptedString,
    val createdAt: LocalDateTime,
    val completedAt: LocalDateTime? = null
)
