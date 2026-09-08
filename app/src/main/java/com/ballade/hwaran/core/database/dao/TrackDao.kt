package com.ballade.hwaran.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ballade.hwaran.core.database.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM chapter WHERE folderUri = :uri LIMIT 1")
    suspend fun getChapterByUri(uri: String): ChapterEntity?

    @Query("SELECT * FROM chapter WHERE mangaId = :mangaId ORDER BY position ASC, title ASC")
    fun getChaptersForManga(mangaId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapter WHERE mangaId = :mangaId ORDER BY position ASC, title ASC")
    suspend fun getChaptersForMangaList(mangaId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapter WHERE id = :chapterId LIMIT 1")
    suspend fun getChapterById(chapterId: Long): ChapterEntity?

    @Delete
    suspend fun deleteChapter(chapter: ChapterEntity): Int

    @Query("DELETE FROM chapter WHERE mangaId = :mangaId")
    suspend fun deleteChaptersByMangaId(mangaId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)
}
