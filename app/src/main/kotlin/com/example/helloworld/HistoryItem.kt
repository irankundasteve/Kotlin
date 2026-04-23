package com.example.helloworld

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_table")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val date: Long,
    val language: String,
    val rate: Float,
    val pitch: Float,
    val isFavorite: Boolean = false
)
