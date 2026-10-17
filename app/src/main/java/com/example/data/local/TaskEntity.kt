package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String = "",
    val category: String = "عام",
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val dueDateTime: Long? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val hasReminder: Boolean = false,
    val createdAtEpoch: Long = System.currentTimeMillis(),
    val updatedAtEpoch: Long = System.currentTimeMillis()
)
