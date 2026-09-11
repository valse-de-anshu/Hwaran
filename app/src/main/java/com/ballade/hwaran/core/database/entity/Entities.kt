package com.ballade.hwaran.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manga")
data class MangaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val thoughts: String,
    val coverPath: String,
    val isNsfw: Boolean,
    val parentUri: String,
    val lastModified: Long,
    val contentType: Int = 0,
    val parentMangaId: Long? = null,
    val boxLabel: String? = null,
    val boxPurpose: String? = null,
    val isLocked: Boolean = false,
    val position: Int = 0,
    val lastReadTitle: String? = null,
    val lastReadPage: Int? = null,
    val genre: String? = null,
    val workspace: String? = null,
    val openCount: Int = 0,
    val isFavorite: Boolean = false
)

@Entity(tableName = "chapter")
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaId: Long,
    val title: String,
    val folderUri: String,
    val thumbnailUri: String? = null,
    val position: Int = 0,
    val artist: String? = null,
    val duration: Long = 0L,
    val lyrics: String? = null,
    val genre: String? = null,
    val openCount: Int = 0
)

@Entity(tableName = "history_event")
data class HistoryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val eventType: String,
    val itemName: String,
    val details: String
)

@Entity(tableName = "pdf_marker")
data class PdfMarkerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaId: Long,
    val page: Int,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val color: Int,
    val createdAt: Long = System.currentTimeMillis()
)

