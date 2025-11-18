package com.ai.recorder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(e: SessionEntity): Long

    @Update
    suspend fun update(e: SessionEntity)

    @Query(
        "SELECT * FROM sessions " +
        "ORDER BY CASE WHEN time IS NULL THEN 1 ELSE 0 END, time DESC, createdAt DESC"
    )
    fun listAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE sessionId = :sid LIMIT 1")
    suspend fun bySessionId(sid: String): SessionEntity?

    @Query("UPDATE sessions SET transcript = :tr, transcribeSource = :src, transcribeError = NULL, audioState = :asv, updatedAt = :ts WHERE sessionId = :sid")
    suspend fun updateTranscript(sid: String, tr: String?, src: String?, asv: String, ts: String)

    @Query("UPDATE sessions SET transcribeError = :err, transcribeSource = :src, audioState = :asv, updatedAt = :ts WHERE sessionId = :sid")
    suspend fun updateTranscriptError(sid: String, err: String?, src: String?, asv: String, ts: String)

    @Query("UPDATE sessions SET summary = :sum, summaryState = :ssv, updatedAt = :ts WHERE sessionId = :sid")
    suspend fun updateSummary(sid: String, sum: String?, ssv: String, ts: String)

    @Query("UPDATE sessions SET title = CASE WHEN :title IS NULL THEN title ELSE :title END, summary = :sum, summaryState = :ssv, updatedAt = :ts WHERE sessionId = :sid")
    suspend fun updateSummaryWithTitle(sid: String, title: String?, sum: String?, ssv: String, ts: String)

    @Query("UPDATE sessions SET summaryState = :ssv, updatedAt = :ts WHERE sessionId = :sid")
    suspend fun updateSummaryState(sid: String, ssv: String, ts: String)

    @Query("UPDATE sessions SET summaryError = :err, summaryState = :ssv, updatedAt = :ts WHERE sessionId = :sid")
    suspend fun updateSummaryError(sid: String, err: String?, ssv: String, ts: String)

    @Query(
        "SELECT * FROM sessions WHERE " +
        "(note LIKE '%' || :q || '%' OR transcript LIKE '%' || :q || '%' OR summary LIKE '%' || :q || '%') " +
        "ORDER BY CASE WHEN time IS NULL THEN 1 ELSE 0 END, time DESC, createdAt DESC"
    )
    fun search(q: String): Flow<List<SessionEntity>>

    @Query(
        "SELECT * FROM sessions WHERE " +
        "(:q = '' OR note LIKE '%' || :q || '%' OR transcript LIKE '%' || :q || '%' OR summary LIKE '%' || :q || '%') AND " +
        "(:p = '' OR people LIKE '%' || :p || '%') AND " +
        "(:h = '' OR hashtags LIKE '%' || :h || '%') " +
        "ORDER BY CASE WHEN time IS NULL THEN 1 ELSE 0 END, time DESC, createdAt DESC"
    )
    fun searchAdvanced(q: String, p: String, h: String): Flow<List<SessionEntity>>

    @Query(
        "SELECT * FROM sessions WHERE " +
        "audioUri IS NOT NULL AND (transcript IS NULL OR transcript = '') AND (transcribeError IS NULL OR transcribeError = '') AND audioState != :done"
    )
    suspend fun listPendingForTranscribe(done: String = AudioState.done.name): List<SessionEntity>
}

@Dao
interface ImageDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(e: ImageEntity): Long

    @Query("SELECT * FROM images WHERE sessionId = :sid ORDER BY createdAt ASC")
    fun listBySession(sid: String): Flow<List<ImageEntity>>
}
