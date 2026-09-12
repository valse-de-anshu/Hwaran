package com.ballade.hwaran.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ballade.hwaran.core.database.entity.PdfMarkerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM pdf_marker WHERE mangaId = :mangaId ORDER BY page ASC")
    fun getMarkersForPdf(mangaId: Long): Flow<List<PdfMarkerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarker(marker: PdfMarkerEntity): Long

    @Delete
    suspend fun deleteMarker(marker: PdfMarkerEntity): Int

    @Query("DELETE FROM pdf_marker WHERE id = :id")
    suspend fun deleteMarkerById(id: Long): Int

    @Query("DELETE FROM pdf_marker WHERE mangaId = :mangaId AND page = :page")
    suspend fun deleteMarkersForPage(mangaId: Long, page: Int): Int
}
