package com.ballade.hwaran.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ballade.hwaran.core.database.entity.MangaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
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

    @Query("SELECT * FROM manga WHERE contentType = :contentType AND workspace = :workspace AND parentMangaId IS NULL AND (:videoLayoutMode = -1 OR :videoLayoutMode = (CASE WHEN :contentType = 2 THEN (CASE WHEN boxPurpose = 'channel' THEN 1 ELSE 0 END) ELSE -1 END))")
    suspend fun getMangaListForMove(contentType: Int, videoLayoutMode: Int, workspace: String): List<MangaEntity>

    @Query("UPDATE manga SET workspace = :workspace WHERE id = :rootId OR parentMangaId = :rootId")
    suspend fun updateWorkspaceForMangaTree(rootId: Long, workspace: String)

    @Query("SELECT DISTINCT workspace FROM manga WHERE workspace IS NOT NULL")
    fun getAllDistinctWorkspaces(): Flow<List<String>>

    @Query("UPDATE manga SET workspace = :newWorkspace WHERE contentType = :contentType AND workspace = :oldWorkspace AND (:videoLayoutMode = -1 OR :videoLayoutMode = (CASE WHEN :contentType = 2 THEN (CASE WHEN boxPurpose = 'channel' THEN 1 ELSE 0 END) ELSE -1 END))")
    suspend fun updateEntireWorkspace(contentType: Int, videoLayoutMode: Int, oldWorkspace: String, newWorkspace: String)

    @Delete
    suspend fun deleteManga(manga: MangaEntity): Int

    @Query("DELETE FROM manga WHERE parentMangaId = :parentId")
    suspend fun deleteChildrenByParentId(parentId: Long)

    @Query("UPDATE manga SET lastReadTitle = NULL, lastReadPage = NULL, openCount = 0, position = 0")
    suspend fun clearAllMediaHistory()

    @Query("SELECT * FROM manga")
    suspend fun getAllMangaEverywhere(): List<MangaEntity>
}
