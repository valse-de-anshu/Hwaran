package com.ballade.hwaran.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM manga WHERE parentMangaId IS NULL ORDER BY position ASC, title ASC")
    fun getAllManga(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE parentMangaId IS NULL")
    suspend fun getAllMangaList(): List<MangaEntity>

    @Query("UPDATE manga SET workspace = :newWorkspace WHERE contentType = :mediaMode AND (workspace IS NULL OR workspace = '')")
    suspend fun updateDefaultWorkspace(mediaMode: Int, newWorkspace: String)

    @Query("SELECT DISTINCT workspace FROM manga WHERE contentType = :contentType AND parentMangaId IS NULL AND workspace IS NOT NULL")
    fun getDistinctWorkspacesForContentType(contentType: Int): Flow<List<String>>

    @Query("SELECT * FROM manga WHERE parentMangaId = :parentId ORDER BY position ASC, id ASC")
    fun getChildrenForManga(parentId: Long): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE id = :id LIMIT 1")
    suspend fun getMangaById(id: Long): MangaEntity?

    @Query("SELECT * FROM manga WHERE parentUri = :uri AND parentMangaId IS NULL LIMIT 1")
    suspend fun getRootMangaByUri(uri: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE parentUri = :uri LIMIT 1")
    suspend fun getMangaByUri(uri: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE parentMangaId = :parentId")
    suspend fun getChildrenForMangaList(parentId: Long): List<MangaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManga(manga: MangaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Delete
    suspend fun deleteManga(manga: MangaEntity): Int

    @Query("DELETE FROM manga WHERE parentMangaId = :parentId")
    suspend fun deleteChildrenByParentId(parentId: Long)

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
    suspend fun insertHistoryEvent(event: HistoryEventEntity): Long

    @Query("SELECT * FROM history_event ORDER BY timestamp DESC")
    fun getAllHistoryEventsFlow(): Flow<List<HistoryEventEntity>>

    @Query("SELECT * FROM history_event ORDER BY timestamp DESC")
    suspend fun getAllHistoryEvents(): List<HistoryEventEntity>

    @Query("DELETE FROM history_event")
    suspend fun clearAllHistoryEvents()

    @Query("SELECT * FROM pdf_marker WHERE mangaId = :mangaId ORDER BY page ASC")
    fun getMarkersForPdf(mangaId: Long): Flow<List<PdfMarkerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarker(marker: PdfMarkerEntity): Long

    @Delete
    suspend fun deleteMarker(marker: PdfMarkerEntity): Int
}
