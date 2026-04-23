package com.example.helloworld

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_table ORDER BY date DESC")
    fun getAllHistory(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItem)

    @Delete
    suspend fun delete(item: HistoryItem): Int

    @Query("DELETE FROM history_table WHERE isFavorite = 0")
    suspend fun clearHistory(): Int

    @Update
    suspend fun update(item: HistoryItem): Int
}
