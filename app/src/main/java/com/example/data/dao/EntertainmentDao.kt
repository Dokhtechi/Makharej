package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.EntertainmentPackMetaEntity
import com.example.data.model.EntertainmentPuzzleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntertainmentDao {

    @Query("SELECT * FROM entertainment_puzzles ORDER BY gameType, difficulty, levelNumber ASC")
    fun observeAllPuzzles(): Flow<List<EntertainmentPuzzleEntity>>

    @Query("SELECT * FROM entertainment_puzzles WHERE gameType = :gameType AND difficulty = :difficulty ORDER BY levelNumber ASC")
    fun observePuzzlesByGameAndDifficulty(
        gameType: String,
        difficulty: String
    ): Flow<List<EntertainmentPuzzleEntity>>

    @Query("SELECT * FROM entertainment_puzzles WHERE id = :id LIMIT 1")
    suspend fun getPuzzleById(id: String): EntertainmentPuzzleEntity?

    @Query("SELECT * FROM entertainment_puzzles")
    suspend fun getAllPuzzlesSnapshot(): List<EntertainmentPuzzleEntity>

    @Query("SELECT COUNT(*) FROM entertainment_puzzles")
    suspend fun getTotalPuzzleCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPuzzles(puzzles: List<EntertainmentPuzzleEntity>)

    @Query("DELETE FROM entertainment_puzzles WHERE gameType = :gameType")
    suspend fun deleteByGameType(gameType: String)

    @Query(
        "UPDATE entertainment_puzzles SET savedProgressJson = :progressJson, mistakesCount = :mistakesCount, updatedAtEpochMs = :updatedAt WHERE id = :id"
    )
    suspend fun saveProgress(
        id: String,
        progressJson: String,
        mistakesCount: Int,
        updatedAt: Long
    )

    @Query(
        "UPDATE entertainment_puzzles SET isCompleted = 1, savedProgressJson = :progressJson, bestTimeSeconds = CASE WHEN bestTimeSeconds = 0 OR :elapsedSeconds < bestTimeSeconds THEN :elapsedSeconds ELSE bestTimeSeconds END, mistakesCount = :mistakesCount, updatedAtEpochMs = :updatedAt WHERE id = :id"
    )
    suspend fun markCompleted(
        id: String,
        progressJson: String,
        elapsedSeconds: Int,
        mistakesCount: Int,
        updatedAt: Long
    )

    @Query("UPDATE entertainment_puzzles SET savedProgressJson = '', isCompleted = 0, mistakesCount = 0 WHERE id = :id")
    suspend fun resetPuzzleProgress(id: String)

    @Query("SELECT * FROM entertainment_pack_meta WHERE packId = 'main_offline_pack' LIMIT 1")
    fun observePackMeta(): Flow<EntertainmentPackMetaEntity?>

    @Query("SELECT * FROM entertainment_pack_meta WHERE packId = 'main_offline_pack' LIMIT 1")
    suspend fun getPackMetaOnce(): EntertainmentPackMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPackMeta(meta: EntertainmentPackMetaEntity)
}
