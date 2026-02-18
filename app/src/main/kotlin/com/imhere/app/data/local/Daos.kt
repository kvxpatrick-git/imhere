package com.imhere.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.imhere.core.model.StopReason
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordDao {
    @Query("SELECT * FROM keyword_entries ORDER BY id ASC")
    fun observeAll(): Flow<List<KeywordEntity>>

    @Query("SELECT * FROM keyword_entries ORDER BY id ASC")
    suspend fun listAll(): List<KeywordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<KeywordEntity>)

    @Query("DELETE FROM keyword_entries")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<KeywordEntity>) {
        clear()
        insertAll(items)
    }
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Query("UPDATE find_sessions SET stageReached = :stage WHERE id = :sessionId")
    suspend fun updateStage(sessionId: String, stage: Int)

    @Query(
        """
        UPDATE find_sessions
        SET endedAtEpochMs = :endedAt,
            stopReason = :reason,
            stopLatencyMs = :stopLatencyMs
        WHERE id = :sessionId
        """
    )
    suspend fun close(sessionId: String, endedAt: Long, reason: StopReason, stopLatencyMs: Long?)

    @Query("SELECT * FROM find_sessions ORDER BY startedAtEpochMs DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<SessionEntity>

    @Query("DELETE FROM find_sessions WHERE startedAtEpochMs < :expireBefore")
    suspend fun purgeExpired(expireBefore: Long)
}
