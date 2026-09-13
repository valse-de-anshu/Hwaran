package com.ballade.hwaran.ui.viewmodels

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.palette.graphics.Palette
import com.ballade.hwaran.audio.HwaranPlayerHolder
import com.ballade.hwaran.audio.MusicNotificationService
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.data.importer.music.MusicImportUtils
import java.io.File
import java.io.InputStream
import android.graphics.Color as AndroidColor

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "music_prefs")

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val context = application.applicationContext
    
    // Persistence Keys
    private val KEY_LAST_CHAPTER_ID = longPreferencesKey("last_chapter_id")
    private val KEY_LAST_MANGA_ID = longPreferencesKey("last_manga_id")
    private val KEY_LAST_POSITION = longPreferencesKey("last_position")

    // Shared ExoPlayer — obtained from the singleton holder so
    // MusicNotificationService can attach a MediaSession to the same instance.
    private var _cachedPlayer: ExoPlayer? = null
    private val exoPlayer: ExoPlayer 
        get() {
            val p = HwaranPlayerHolder.getOrCreate(getApplication())
            if (_cachedPlayer != p) {
                _cachedPlayer = p
                attachListeners(p)
            }
            return p
        }
    
    private fun attachListeners(player: ExoPlayer) {
        player.addListener(playerListener)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                try {
                    android.util.Log.d("MusicViewModel", "onIsPlayingChanged(true) -> Starting service")
                    val serviceIntent = Intent(context, MusicNotificationService::class.java)
                    context.startService(serviceIntent)
                } catch (e: Exception) {
                    android.util.Log.e("MusicViewModel", "Failed to start service from listener", e)
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val p = exoPlayer
            val index = p.currentMediaItemIndex
            val playlist = _currentPlaylist.value
            if (index >= 0 && index < playlist.size) {
                val chapter = playlist[index]
                _currentChapter.value = chapter
                _currentPosition.value = 0L
                _playbackProgress.value = 0f
                _totalDuration.value = 0L
                updateDominantColor(chapter.thumbnailUri ?: chapter.folderUri ?: _currentManga.value?.coverPath)
                // ADD OPEN COUNT INCREMENT HERE & RESOLVE MISSING LYRICS
                viewModelScope.launch(Dispatchers.IO) {
                    var dbChapter = database.trackDao().getChapterById(chapter.id)
                    if (dbChapter != null) {
                        if (dbChapter.lyrics.isNullOrBlank()) {
                            val resolvedLyrics = tryResolveLyrics(dbChapter)
                            if (!resolvedLyrics.isNullOrBlank()) {
                                dbChapter = dbChapter.copy(lyrics = resolvedLyrics)
                            }
                        }
                        database.trackDao().insertChapter(dbChapter.copy(openCount = dbChapter.openCount + 1))
                        withContext(Dispatchers.Main) {
                            if (_currentChapter.value?.id == dbChapter.id) {
                                _currentChapter.value = dbChapter
                                _currentPlaylist.value = _currentPlaylist.value.map {
                                    if (it.id == dbChapter.id) dbChapter else it
                                }
                            }
                        }
                        val dbManga = database.libraryDao().getMangaById(dbChapter.mangaId)
                        if (dbManga != null) {
                            database.libraryDao().insertManga(dbManga.copy(openCount = dbManga.openCount + 1))
                        }
                    }
                }
                
                com.ballade.hwaran.core.util.HistoryTracker.logEvent(
                    "LISTEN",
                    chapter.title,
                    "mangaId:${_currentManga.value?.id ?: -1L}|chapterId:${chapter.id}|fallback:Playlist: ${_currentManga.value?.title ?: "Unknown"}"
                )
            }
        }
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _totalDuration.value = exoPlayer.duration.coerceAtLeast(0)
            }
        }
    }
    
    private var mediaController: androidx.media3.session.MediaController? = null

    private val _currentManga = MutableStateFlow<MangaEntity?>(null)
    val currentManga: StateFlow<MangaEntity?> = _currentManga

    private val _currentPlaylist = MutableStateFlow<List<ChapterEntity>>(emptyList())
    val currentPlaylist: StateFlow<List<ChapterEntity>> = _currentPlaylist

    private val _currentChapter = MutableStateFlow<ChapterEntity?>(null)
    val currentChapter: StateFlow<ChapterEntity?> = _currentChapter

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration: StateFlow<Long> = _totalDuration

    enum class ShuffleMode {
        OFF,
        NORMAL,
        SMART,
        ADVANCE
    }

    private val _shuffleType = MutableStateFlow(ShuffleMode.OFF)
    val currentShuffleMode: StateFlow<ShuffleMode> = _shuffleType.asStateFlow()

    private val _shuffleMode = MutableStateFlow(false)
    val shuffleMode: StateFlow<Boolean> = _shuffleMode

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    data class ColorPalette(
        val dominant: Long = 0xFF000000,
        val vibrant: Long = 0xFF000000,
        val muted: Long = 0xFF000000
    )

    private val _colorPalette = MutableStateFlow(ColorPalette())
    val colorPalette: StateFlow<ColorPalette> = _colorPalette

    // Centralized Playlist Palettes for UI Stability - Cache by ID to Path mapping
    private val _playlistPalettes = MutableStateFlow<Map<Long, Pair<String, ColorPalette>>>(emptyMap())
    val playlistPalettes: StateFlow<Map<Long, Pair<String, ColorPalette>>> = _playlistPalettes

    fun extractPlaylistColor(playlist: MangaEntity) {
        val currentEntry = _playlistPalettes.value[playlist.id]
        if (playlist.coverPath.isEmpty() || (currentEntry != null && currentEntry.first == playlist.coverPath)) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = loadBitmap(playlist.coverPath)
                if (bitmap != null) {
                    val p = Palette.from(bitmap).generate()
                    
                    var dominantInt = p.getDominantColor(0)
                    var vibrantInt = p.getVibrantColor(0).let { if (it == 0) p.getLightVibrantColor(0) else it }
                    var mutedInt = p.getMutedColor(0)

                    // If we only have one color, generate a Hue-shifted vibrant counterpart
                    if (vibrantInt == 0 && dominantInt != 0) {
                        val hsv = FloatArray(3)
                        AndroidColor.colorToHSV(dominantInt, hsv)
                        hsv[0] = (hsv[0] + 30f) % 360f 
                        hsv[1] = (hsv[1] * 1.2f).coerceAtMost(1f) 
                        vibrantInt = AndroidColor.HSVToColor(hsv)
                    }
                    
                    if (mutedInt == 0 && dominantInt != 0) {
                        val hsv = FloatArray(3)
                        AndroidColor.colorToHSV(dominantInt, hsv)
                        hsv[2] = (hsv[2] * 0.5f) 
                        mutedInt = AndroidColor.HSVToColor(hsv)
                    }

                    if (dominantInt != 0 || vibrantInt != 0) {
                        _playlistPalettes.value += (playlist.id to (playlist.coverPath to ColorPalette(
                            dominant = dominantInt.toLong(),
                            vibrant = vibrantInt.toLong(),
                            muted = mutedInt.toLong()
                        )))
                    }
                }
            } catch (e: Exception) {}
        }
    }

    init {
        // Accessing exoPlayer here triggers initial listener attachment
        val initialPlayer = exoPlayer
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        initialPlayer.setAudioAttributes(audioAttributes, true)

        viewModelScope.launch {
            var lastSaveTime = 0L
            while (true) {
                if (exoPlayer.playbackState != Player.STATE_IDLE && exoPlayer.playbackState != Player.STATE_ENDED) {
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration
                    
                    if (dur > 0) {
                        _totalDuration.value = dur
                        _currentPosition.value = pos
                        _playbackProgress.value = pos.toFloat() / dur.toFloat()
                    } else if (dur == 0L || dur == C.TIME_UNSET) {
                        // Still trying to get duration, at least update position if possible
                        _currentPosition.value = pos
                    }
                }
                val isPlaying = exoPlayer.isPlaying
                val delayMs = if (isPlaying) 50L else 250L
                delay(delayMs)
                
                val now = System.currentTimeMillis()
                if (exoPlayer.playbackState != Player.STATE_IDLE && now - lastSaveTime > 5000L) {
                    lastSaveTime = now
                    savePlaybackState()
                }
            }
        }

        // Restore Playback Session
        viewModelScope.launch {
            restorePlaybackState()
        }
        
        // Connect MediaController to ensure the MediaSessionService is alive and showing notifications
        viewModelScope.launch {
            val sessionToken = androidx.media3.session.SessionToken(
                context, 
                android.content.ComponentName(context, MusicNotificationService::class.java)
            )
            val controllerFuture = androidx.media3.session.MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture.addListener({
                try {
                    mediaController = controllerFuture.get()
                    android.util.Log.d("MusicViewModel", "MediaController connected")
                } catch (e: Exception) {
                    android.util.Log.e("MusicViewModel", "Failed to connect MediaController", e)
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(context))
        }
    }

    private fun savePlaybackState() {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                _currentChapter.value?.let { prefs[KEY_LAST_CHAPTER_ID] = it.id }
                _currentManga.value?.let { prefs[KEY_LAST_MANGA_ID] = it.id }
                prefs[KEY_LAST_POSITION] = exoPlayer.currentPosition
            }
        }
    }

    private fun createMediaItems(chapters: List<ChapterEntity>, manga: MangaEntity): List<MediaItem> {
        return chapters.map { chapter ->
            val path = chapter.folderUri
            val uri = if (path.startsWith("content://") || path.startsWith("file://")) {
                Uri.parse(path)
            } else if (path.startsWith("/")) {
                val file = File(path)
                val targetFile = if (file.exists() && file.isDirectory) {
                    file.listFiles()?.firstOrNull { f ->
                        val name = f.name.lowercase()
                        name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".m4a") ||
                        name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".aac") ||
                        name.endsWith(".wma") || name.endsWith(".opus")
                    } ?: file
                } else {
                    file
                }
                Uri.fromFile(targetFile)
            } else {
                Uri.parse(path)
            }
            
            val artPath = chapter.thumbnailUri ?: manga.coverPath
            val artUri = if (artPath.isNotEmpty()) {
                getOrCreateSquareCover(context, artPath)
            } else null
            
            val metadata = MediaMetadata.Builder()
                .setTitle(chapter.title)
                .setArtist(manga.title)
                .setArtworkUri(artUri)
                .build()
                
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build()
        }
    }

    private fun preparePlayerFromState() {
        val player = exoPlayer
        val playlist = _currentPlaylist.value
        val manga = _currentManga.value
        val chapter = _currentChapter.value
        
        if (player.mediaItemCount == 0 && playlist.isNotEmpty() && manga != null) {
            val mediaItems = createMediaItems(playlist, manga)
            player.setMediaItems(mediaItems)
            
            val index = if (chapter != null) playlist.indexOfFirst { it.id == chapter.id } else 0
            if (index != -1) {
                player.seekTo(index, _currentPosition.value)
            }
            
            val playlistMetadata = MediaMetadata.Builder()
                .setTitle("Hwaran")
                .setArtist("Hwaran")
                .build()
            player.setPlaylistMetadata(playlistMetadata)
            player.prepare()
        }
    }

    private suspend fun restorePlaybackState() {
        try {
            val prefs = context.dataStore.data.first()
            val chapterId = prefs[KEY_LAST_CHAPTER_ID] ?: return
            val mangaId = prefs[KEY_LAST_MANGA_ID] ?: return
            val position = prefs[KEY_LAST_POSITION] ?: 0L

            withContext(Dispatchers.IO) {
                val manga = database.libraryDao().getMangaById(mangaId)
                val chapters = database.trackDao().getChaptersForMangaList(mangaId)
                val index = chapters.indexOfFirst { it.id == chapterId }

                if (manga != null && index != -1) {
                    withContext(Dispatchers.Main) {
                        _currentManga.value = manga
                        _currentPlaylist.value = chapters
                        _currentChapter.value = chapters[index]
                        _currentPosition.value = position

                        val mediaItems = createMediaItems(chapters, manga)
                        exoPlayer.setMediaItems(mediaItems)
                        
                        // Set playlist metadata to help identify the app/source in system widgets
                        val playlistMetadata = MediaMetadata.Builder()
                            .setTitle("Hwaran")
                            .setArtist("Hwaran")
                            .build()
                        exoPlayer.setPlaylistMetadata(playlistMetadata)

                        exoPlayer.seekTo(index, position)
                        exoPlayer.prepare()
                        updateDominantColor(chapters[index].thumbnailUri ?: chapters[index].folderUri ?: manga.coverPath)
                    }
                }
            }
        } catch (e: Exception) {
            // Restore failed
        }
    }

    fun playExternalAudio(uri: Uri, context: Context) {
        val player = exoPlayer
        player.stop()
        player.clearMediaItems()
        
        val metadata = MediaMetadata.Builder()
            .setTitle(uri.lastPathSegment ?: "External Audio")
            .setArtist("External")
            .build()
            
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
            
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        // Sync local state
        _currentChapter.value = ChapterEntity(id = -1L, mangaId = -1L, title = uri.lastPathSegment ?: "External", folderUri = uri.toString())
        _currentManga.value = null
        _currentPlaylist.value = emptyList()

        // Ensure service is started
        try {
            val serviceIntent = Intent(context, MusicNotificationService::class.java)
            context.startService(serviceIntent)
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Failed to start service for external audio", e)
        }
    }

    fun playPlaylist(manga: MangaEntity, chapters: List<ChapterEntity>, startIndex: Int = 0) {
        _currentManga.value = manga
        _currentPlaylist.value = chapters
        val startChapter = chapters.getOrNull(startIndex)
        _currentChapter.value = startChapter
        if (startChapter != null && startChapter.lyrics.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                val resolved = tryResolveLyrics(startChapter)
                if (!resolved.isNullOrBlank()) {
                    val updated = startChapter.copy(lyrics = resolved)
                    database.trackDao().insertChapter(updated)
                    withContext(Dispatchers.Main) {
                        if (_currentChapter.value?.id == updated.id) {
                            _currentChapter.value = updated
                            _currentPlaylist.value = _currentPlaylist.value.map {
                                if (it.id == updated.id) updated else it
                            }
                        }
                    }
                }
            }
        }

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        
        val mediaItems = createMediaItems(chapters, manga)
        exoPlayer.setMediaItems(mediaItems)
        exoPlayer.shuffleModeEnabled = _shuffleMode.value
        exoPlayer.repeatMode = _repeatMode.value
        
        // Set playlist metadata to help identify the app/source in system widgets
        val playlistMetadata = MediaMetadata.Builder()
            .setTitle("Hwaran")
            .setArtist("Hwaran")
            .build()
        exoPlayer.setPlaylistMetadata(playlistMetadata)

        exoPlayer.seekTo(startIndex, 0)
        exoPlayer.prepare()
        exoPlayer.play()
        
        // Ensure service is started for background playback and notification
        try {
            android.util.Log.d("MusicViewModel", "Starting MusicNotificationService...")
            val serviceIntent = Intent(context, MusicNotificationService::class.java)
            context.startService(serviceIntent)
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Failed to start service", e)
        }
        
        updateDominantColor(chapters.getOrNull(startIndex)?.thumbnailUri ?: chapters.getOrNull(startIndex)?.folderUri ?: manga.coverPath)
        savePlaybackState()
    }

    fun togglePlayPause() {
        val player = exoPlayer
        if (player.isPlaying) {
            player.pause()
        } else {
            preparePlayerFromState()
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
            
            // Explicitly ensure service is started when resuming playback from app UI
            try {
                val serviceIntent = Intent(context, MusicNotificationService::class.java)
                context.startService(serviceIntent)
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to start service on play", e)
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun setShuffleMode(mode: ShuffleMode) {
        _shuffleType.value = mode
        val isEnabled = mode != ShuffleMode.OFF
        _shuffleMode.value = isEnabled
        exoPlayer.shuffleModeEnabled = isEnabled

        if (isEnabled) {
            when (mode) {
                ShuffleMode.NORMAL -> applyNormalShuffleOrder()
                ShuffleMode.SMART -> applySmartShuffleOrder()
                ShuffleMode.ADVANCE -> applyAdvanceShuffleOrder()
                ShuffleMode.OFF -> {}
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun applyNormalShuffleOrder() {
        val count = exoPlayer.mediaItemCount
        if (count > 0) {
            exoPlayer.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(count))
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun applySmartShuffleOrder() {
        val playlist = _currentPlaylist.value
        val count = exoPlayer.mediaItemCount
        if (count <= 1 || playlist.size != count) {
            applyNormalShuffleOrder()
            return
        }
        val currentIndex = exoPlayer.currentMediaItemIndex.coerceIn(0, count - 1)
        val otherIndices = (0 until count).filter { it != currentIndex }.shuffled().toMutableList()
        val smartIndices = mutableListOf<Int>()
        smartIndices.add(currentIndex)
        while (otherIndices.isNotEmpty()) {
            val lastTrack = playlist[smartIndices.last()]
            val nextIdx = otherIndices.firstOrNull {
                playlist[it].mangaId != lastTrack.mangaId ||
                (playlist[it].genre != lastTrack.genre && !playlist[it].genre.isNullOrBlank())
            } ?: otherIndices.first()
            smartIndices.add(nextIdx)
            otherIndices.remove(nextIdx)
        }
        exoPlayer.setShuffleOrder(
            ShuffleOrder.DefaultShuffleOrder(
                smartIndices.toIntArray(),
                System.currentTimeMillis()
            )
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun applyAdvanceShuffleOrder() {
        val count = exoPlayer.mediaItemCount
        if (count <= 1) return
        val currentIndex = exoPlayer.currentMediaItemIndex.coerceIn(0, count - 1)
        val otherIndices = (0 until count).filter { it != currentIndex }.shuffled()
        val advanceIndices = mutableListOf<Int>()
        advanceIndices.add(currentIndex)
        advanceIndices.addAll(otherIndices)
        exoPlayer.setShuffleOrder(
            ShuffleOrder.DefaultShuffleOrder(
                advanceIndices.toIntArray(),
                System.currentTimeMillis()
            )
        )
    }

    fun toggleShuffle() {
        val next = when (_shuffleType.value) {
            ShuffleMode.OFF -> ShuffleMode.NORMAL
            ShuffleMode.NORMAL -> ShuffleMode.SMART
            ShuffleMode.SMART -> ShuffleMode.ADVANCE
            ShuffleMode.ADVANCE -> ShuffleMode.OFF
        }
        setShuffleMode(next)
    }

    fun cycleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        exoPlayer.repeatMode = nextMode
    }

    fun setRepeatMode(mode: Int) {
        _repeatMode.value = mode
        exoPlayer.repeatMode = mode
    }

    fun next() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNext()
        }
    }

    fun previous() {
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPrevious()
        }
    }

    fun seekTo(progress: Float) {
        val duration = exoPlayer.duration
        if (duration > 0) {
            val seekPos = (duration * progress).toLong()
            exoPlayer.seekTo(seekPos)
            _currentPosition.value = seekPos
            _playbackProgress.value = progress
        }
    }

    fun seekToPosition(positionMs: Long) {
        val duration = exoPlayer.duration
        val target = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        exoPlayer.seekTo(target)
        _currentPosition.value = target
        if (duration > 0) {
            _playbackProgress.value = target.toFloat() / duration.toFloat()
        }
    }

    fun updateCurrentChapterLyrics(newLyrics: String?) {
        val current = _currentChapter.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = current.copy(lyrics = newLyrics)
            database.trackDao().insertChapter(updated)
            withContext(Dispatchers.Main) {
                _currentChapter.value = updated
                _currentPlaylist.value = _currentPlaylist.value.map {
                    if (it.id == updated.id) updated else it
                }
            }
        }
    }

    fun updateCurrentChapterGenre(newGenre: String) {
        val current = _currentChapter.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = current.copy(genre = newGenre)
            database.trackDao().insertChapter(updated)
            withContext(Dispatchers.Main) {
                _currentChapter.value = updated
                _currentPlaylist.value = _currentPlaylist.value.map {
                    if (it.id == updated.id) updated else it
                }
            }
        }
    }

    private fun tryResolveLyrics(chapter: ChapterEntity): String? {
        try {
            // Case A: SAF Document Tree
            val manga = _currentManga.value
            if (manga != null && manga.parentUri.startsWith("content://")) {
                val folderDoc = try {
                    DocumentFile.fromTreeUri(context, Uri.parse(manga.parentUri))
                } catch (e: Exception) {
                    null
                }
                if (folderDoc != null) {
                    val lyricsDocs = MusicImportUtils.findLyricsFiles(folderDoc)
                    if (lyricsDocs.isNotEmpty()) {
                        val decoded = Uri.decode(chapter.folderUri)
                        val fileName = decoded.substringAfterLast('/').substringAfterLast(':')
                        val matched = MusicImportUtils.findMatchingLyricsDoc(
                            fileName,
                            chapter.title,
                            lyricsDocs
                        )
                        if (matched != null) {
                            val lyricsContent = MusicImportUtils.readLyrics(context, matched)
                            if (!lyricsContent.isNullOrBlank()) return lyricsContent
                        }
                    }
                }
            }

            // Case B: Local File
            val filePath = if (chapter.folderUri.startsWith("file://")) {
                chapter.folderUri.removePrefix("file://")
            } else if (chapter.folderUri.startsWith("/")) {
                chapter.folderUri
            } else null

            if (filePath != null) {
                val audioFile = File(filePath)
                val parentDir = audioFile.parentFile
                if (parentDir != null && parentDir.isDirectory) {
                    val lyricsFiles = MusicImportUtils.findLyricsFiles(parentDir)
                    if (lyricsFiles.isNotEmpty()) {
                        val matched = MusicImportUtils.findMatchingLyricsFile(
                            audioFile.name,
                            chapter.title,
                            lyricsFiles
                        )
                        if (matched != null) {
                            val lyricsContent = MusicImportUtils.readLyrics(matched)
                            if (!lyricsContent.isNullOrBlank()) return lyricsContent
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun refreshCurrentChapter() {
        val current = _currentChapter.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = database.trackDao().getChapterById(current.id)
            if (updated != null) {
                withContext(Dispatchers.Main) {
                    _currentChapter.value = updated
                }
            }
        }
    }

    fun stopIfPlaylistDeleted(playlistId: Long) {
        if (_currentManga.value?.id == playlistId) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            _currentManga.value = null
            _currentPlaylist.value = emptyList()
            _currentChapter.value = null
            _colorPalette.value = ColorPalette()
            savePlaybackState()
        }
    }

    private fun updateDominantColor(path: String?) {
        if (path == null || path.isEmpty()) {
            _colorPalette.value = ColorPalette()
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = loadBitmap(path)
                if (bitmap != null) {
                    val p = Palette.from(bitmap).generate()
                    val dominantFinal = p.getDominantColor(0xFF000000.toInt())
                    val vibrantFinal = p.getVibrantColor(0xFF000000.toInt())
                    val mutedFinal = p.getMutedColor(0xFF000000.toInt())
                    
                    _colorPalette.value = ColorPalette(
                        dominant = dominantFinal.toLong(),
                        vibrant = vibrantFinal.toLong(),
                        muted = mutedFinal.toLong()
                    )
                } else {
                    _colorPalette.value = ColorPalette()
                }
            } catch (e: Exception) {
                _colorPalette.value = ColorPalette()
            }
        }
    }

    private fun loadBitmap(path: String): Bitmap? {
        return try {
            if (path.startsWith("content://") || path.startsWith("android.resource://")) {
                val input: InputStream? = getApplication<Application>().contentResolver.openInputStream(Uri.parse(path))
                BitmapFactory.decodeStream(input)
            } else {
                BitmapFactory.decodeFile(path)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getMd5Hash(input: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray())
            bytes.joinToString("") { String.format("%02x", it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    private fun getOrCreateSquareCover(context: Context, path: String?): Uri? {
        if (path.isNullOrEmpty()) return null
        
        try {
            val getStream = {
                if (path.startsWith("content://") || path.startsWith("android.resource://")) {
                    context.contentResolver.openInputStream(Uri.parse(path))
                } else {
                    val file = if (path.startsWith("file://")) File(Uri.parse(path).path ?: "") else File(path)
                    if (file.exists() && file.isFile) java.io.FileInputStream(file) else null
                }
            }
            
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            getStream()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            
            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) {
                val file = if (path.startsWith("/")) File(path) else if (path.startsWith("file://")) File(Uri.parse(path).path ?: "") else null
                if (file != null && file.exists()) {
                    return Uri.fromFile(file)
                }
                return Uri.parse(path)
            }
            
            if (width == height) {
                val file = if (path.startsWith("/")) File(path) else if (path.startsWith("file://")) File(Uri.parse(path).path ?: "") else null
                if (file != null && file.exists()) {
                    return Uri.fromFile(file)
                }
                return Uri.parse(path)
            }
            
            val cacheDir = File(context.cacheDir, "square_covers")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val hash = getMd5Hash(path)
            val cacheFile = File(cacheDir, "cropped_$hash.png")
            if (cacheFile.exists() && cacheFile.length() > 0) {
                return Uri.fromFile(cacheFile)
            }
            
            val targetSize = 512
            var inSampleSize = 1
            val minDim = Math.min(width, height)
            while (minDim / inSampleSize >= targetSize) {
                inSampleSize *= 2
            }
            
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            
            val bitmap = getStream()?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null
            
            val size = Math.min(bitmap.width, bitmap.height)
            val x = (bitmap.width - size) / 2
            val y = (bitmap.height - size) / 2
            val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
            
            java.io.FileOutputStream(cacheFile).use { out ->
                cropped.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            
            if (cropped != bitmap) {
                cropped.recycle()
            }
            bitmap.recycle()
            
            return Uri.fromFile(cacheFile)
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Error creating square cover for $path", e)
            val file = if (path.startsWith("/")) File(path) else if (path.startsWith("file://")) File(Uri.parse(path).path ?: "") else null
            if (file != null && file.exists()) {
                return Uri.fromFile(file)
            }
            return Uri.parse(path)
        }
    }

    fun stopPlayback() {
        exoPlayer.stop()
    }

    companion object {
        var skipRestore: Boolean = false
        var isPopUpActive: Boolean = false
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT release the player here — MusicNotificationService still needs it.
        // The player is released in HwaranPlayerHolder when the service is destroyed.
    }
}
