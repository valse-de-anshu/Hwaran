package com.ballade.hwaran.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ballade.hwaran.core.database.entity.HistoryEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEvent(event: HistoryEventEntity): Long

    @Query("SELECT * FROM history_event ORDER BY timestamp DESC")
    fun getAllHistoryEventsFlow(): Flow<List<HistoryEventEntity>>

    @Query("SELECT * FROM history_event ORDER BY timestamp DESC")
    suspend fun getAllHistoryEvents(): List<HistoryEventEntity>

    @Query("DELETE FROM history_event")
    suspend fun clearAllHistoryEvents()
}
