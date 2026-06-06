package com.example.game

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "game_save_state")
data class GameSaveState(
    @PrimaryKey val id: Int = 1,
    val shards: Int = 0,
    val playerLevel: Int = 1,
    val currXp: Int = 0,
    val maxHealthLevel: Int = 1,
    val speedLevel: Int = 1,
    val damageLevel: Int = 1,
    val fireRateLevel: Int = 1,
    val hasGreenKey: Boolean = false,
    val roomGridX: Int = 0, // Current map grid coordinates (0 to 2)
    val roomGridY: Int = 0, // Current map grid coordinates (0 to 2)
    val unlockedSectors: String = "0,0", // Comma-separated or similar list of discovered grid coordinate hashes (e.g. "0,0;1,0")
    val clearedSectors: String = "", // Sectors where all hostile waves are completed
    val highscore: Int = 0
)

@Dao
interface GameSaveDao {
    @Query("SELECT * FROM game_save_state WHERE id = 1 LIMIT 1")
    fun getSaveStateFlow(): Flow<GameSaveState?>

    @Query("SELECT * FROM game_save_state WHERE id = 1 LIMIT 1")
    suspend fun getSaveStateDirect(): GameSaveState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGame(saveState: GameSaveState)

    @Query("DELETE FROM game_save_state")
    suspend fun clearSave()
}
