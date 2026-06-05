package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prompt_records")
data class PromptRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val response: String,
    val modelUsed: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isBookmarked: Boolean = false,
    val isError: Boolean = false,
    val category: String = "General"
)
