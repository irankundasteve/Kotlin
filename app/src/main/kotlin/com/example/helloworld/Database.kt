package com.example.helloworld

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_table ORDER BY date DESC")
    fun getAllHistory(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItem)

    @Delete
    suspend fun delete(item: HistoryItem)

    @Query("DELETE FROM history_table WHERE isFavorite = 0")
    suspend fun clearHistory()

    @Update
    suspend fun update(item: HistoryItem)
}

@Database(entities = [HistoryItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "history_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
