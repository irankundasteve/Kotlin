package com.example.helloworld

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "settings_table")
data class AppSettings(
    @PrimaryKey val id: Int = 0,
    val themeMode: Int = 0, // 0: System, 1: Light, 2: Dark
    val accentColor: Int = -12450820 // Default #BB86FC as Int
)

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings_table WHERE id = 0")
    fun getSettings(): Flow<AppSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSettings(settings: AppSettings)
}
