package com.ballade.hwaran.core.database.dao

import androidx.room.Dao

@Dao
interface LibraryDao : MediaDao, TrackDao, HistoryDao, AnnotationDao
