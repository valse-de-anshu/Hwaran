package com.ballade.hwaran.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

import com.ballade.hwaran.core.metadata.EntryMetadata
import com.ballade.hwaran.core.metadata.MasterTagItem
import com.ballade.hwaran.core.metadata.MediaMetadataManager
import java.util.UUID

val AppImportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class DescriptionViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val libraryDao = database.libraryDao()
    private val trackDao = database.trackDao()

    private val _manga = MutableStateFlow<MangaEntity?>(null)
    val manga: StateFlow<MangaEntity?> = _manga

    private val _rootManga = MutableStateFlow<MangaEntity?>(null)
    val rootManga: StateFlow<MangaEntity?> = _rootManga

    private val _chapters = MutableStateFlow<List<ChapterEntity>>(emptyList())
    val chapters: StateFlow<List<ChapterEntity>> = _chapters

    private val _childBoxes = MutableStateFlow<List<MangaEntity>>(emptyList())
    val childBoxes: StateFlow<List<MangaEntity>> = _childBoxes

    // Edit Mode States
    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode

    val draftTitle = MutableStateFlow("")
    val draftDescription = MutableStateFlow("")
    val draftThoughts = MutableStateFlow("")
    val draftIsNsfw = MutableStateFlow(false)
    val draftCoverPath = MutableStateFlow("")
    val draftGenre = MutableStateFlow("")
    val draftContentType = MutableStateFlow(0)
    val draftBoxPurpose = MutableStateFlow<String?>(null)

    // Entry Metadata & Master Tags State
    private val _entryMetadata = MutableStateFlow(EntryMetadata())
    val entryMetadata: StateFlow<EntryMetadata> = _entryMetadata

    private val _assignedTags = MutableStateFlow<List<String>>(emptyList())
    val assignedTags: StateFlow<List<String>> = _assignedTags

    private val _tagQuery = MutableStateFlow("")
    val tagQuery: StateFlow<String> = _tagQuery

    private val _tagSuggestions = MutableStateFlow<List<MasterTagItem>>(emptyList())
    val tagSuggestions: StateFlow<List<MasterTagItem>> = _tagSuggestions

    private val _isTagSearchVisible = MutableStateFlow(false)
    val isTagSearchVisible: StateFlow<Boolean> = _isTagSearchVisible

    // Editable Metadata Fields
    val draftAltTitle = MutableStateFlow("")
    val draftAuthor = MutableStateFlow("")
    val draftArtist = MutableStateFlow("")
    val draftPublisher = MutableStateFlow("")
    val draftSerialization = MutableStateFlow("")
    val draftYear = MutableStateFlow("")
    val draftStatus = MutableStateFlow("Ongoing")
    val draftRating = MutableStateFlow("8.7 (152K)")

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    private val _importProgress = MutableStateFlow(0)
    val importProgress: StateFlow<Int> = _importProgress

    private var chaptersJob: Job? = null
    private var childBoxesJob: Job? = null
    private var currentMangaId: Long = -1L
    private var importJob: Job? = null

    fun loadManga(mangaId: Long) {
        currentMangaId = mangaId
        if (mangaId == -1L) {
            _isEditMode.value = true
            _manga.value = null
            _rootManga.value = null
            draftTitle.value = ""
            draftDescription.value = ""
            draftIsNsfw.value = false
            draftCoverPath.value = ""
            draftContentType.value = 0
            draftBoxPurpose.value = null
            _entryMetadata.value = EntryMetadata()
            _assignedTags.value = emptyList()
            draftAltTitle.value = ""
            draftAuthor.value = ""
            draftArtist.value = ""
            draftPublisher.value = ""
            draftSerialization.value = ""
            draftYear.value = ""
            draftStatus.value = "Ongoing"
            draftRating.value = "8.7 (152K)"
            _chapters.value = emptyList()
            _childBoxes.value = emptyList()
        } else {
            viewModelScope.launch {
                val m = withContext(Dispatchers.IO) { libraryDao.getMangaById(mangaId) }
                _manga.value = m
                if (m != null) {
                    draftTitle.value = m.title
                    draftDescription.value = if (m.description == "No description added yet.") "" else m.description
                    draftThoughts.value = if (m.thoughts == "No thoughts added.") "" else m.thoughts
                    draftIsNsfw.value = m.isNsfw
                    draftCoverPath.value = m.coverPath
                    draftGenre.value = m.genre ?: ""
                    draftContentType.value = m.contentType
                    draftBoxPurpose.value = m.boxPurpose

                    val context = getApplication<Application>().applicationContext
                    val meta = withContext(Dispatchers.IO) {
                        MediaMetadataManager.loadMetadata(context, mangaId, m.parentUri, m)
                    }
                    _entryMetadata.value = meta
                    _assignedTags.value = meta.tags
                    draftAltTitle.value = meta.altTitle
                    draftAuthor.value = meta.author
                    draftArtist.value = meta.artist
                    draftPublisher.value = meta.publisher
                    draftSerialization.value = meta.serialization
                    draftYear.value = meta.year
                    draftStatus.value = meta.status
                    draftRating.value = meta.rating
                    searchMasterTags("")
                    
                    val rootId = m.parentMangaId ?: m.id
                    val root = if (m.parentMangaId == null) m else withContext(Dispatchers.IO) { libraryDao.getMangaById(rootId) }
                    _rootManga.value = root
                }

                childBoxesJob?.cancel()
                childBoxesJob = launch {
                    val parentIdForChildren = m?.parentMangaId ?: mangaId
                    libraryDao.getChildrenForManga(parentIdForChildren).collect { list ->
                        _childBoxes.value = list
                    }
                }
            }
            
            chaptersJob?.cancel()
            chaptersJob = viewModelScope.launch {
                val contentType = withContext(Dispatchers.IO) {
                    libraryDao.getMangaById(mangaId)?.contentType ?: 0
                }
                trackDao.getChaptersForManga(mangaId).collect { list ->
                    val sortedList = list.sortedWith(compareBy<ChapterEntity> { 
                        Regex("(\\d+(\\.\\d+)?)").find(it.title)?.value?.toFloat() ?: Float.MAX_VALUE 
                    }.thenBy {
                        it.title.replace(Regex("\\d+")) { matchResult ->
                            matchResult.value.padStart(10, '0')
                        }
                    })
                    _chapters.value = sortedList
                    // Background scan for missing thumbnails/durations in video/music mode
                    val type = _manga.value?.contentType ?: 0
                    if (type == 1 || type == 2) {
                        scanForMissingMetadata(list, type)
                    }
                }
            }        }
    }

    // Track chapters currently being scanned to prevent O(N^2) explosion
    // when the DB updates and re-triggers the Flow emission.
    private val scanningChapters = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    private fun scanForMissingMetadata(chapters: List<ChapterEntity>, contentType: Int) {
        val missing = chapters.filter { 
            (it.duration == 0L || it.thumbnailUri == null) && !scanningChapters.contains(it.id) 
        }
        if (missing.isEmpty()) return

        // Mark as scanning immediately so subsequent Flow emissions ignore them
        missing.forEach { scanningChapters.add(it.id) }

        AppImportScope.launch {
            missing.forEach { chapter ->
                try {
                    var duration = chapter.duration
                    var thumbUri = chapter.thumbnailUri
                    var artist = chapter.artist

                    // Step 1: Always try the OS-level thumbnail first (API 29+).
                    // This is the EXACT same source the system file manager uses,
                    // so thumbnails will match what users see outside the app.
                    if (thumbUri == null && chapter.folderUri.startsWith("content://") && contentType == 2) {
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                val bitmap = getApplication<Application>().contentResolver.loadThumbnail(
                                    Uri.parse(chapter.folderUri),
                                    android.util.Size(640, 360),
                                    null
                                )
                                val tmpFile = File(getApplication<Application>().cacheDir, "thumb_${chapter.id}.tmp")
                                val thumbFile = File(getApplication<Application>().cacheDir, "thumb_${chapter.id}_v2.jpg")
                                FileOutputStream(tmpFile).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                                tmpFile.renameTo(thumbFile)
                                thumbUri = thumbFile.absolutePath
                            }
                        } catch (_: Exception) {}
                    }

                    // Step 2: MediaMetadataRetriever for duration + embedded art/frame
                    if (duration == 0L || thumbUri == null) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            if (chapter.folderUri.startsWith("content://")) {
                                val uri = Uri.parse(chapter.folderUri)
                                val fd = getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")
                                if (fd != null) {
                                    retriever.setDataSource(fd.fileDescriptor)
                                    fd.close() // Safe to close after setDataSource
                                } else {
                                    retriever.setDataSource(getApplication(), uri)
                                }
                            } else {
                                val file = File(chapter.folderUri)
                                val targetPath = if (file.exists() && file.isDirectory) {
                                    val childFiles = file.listFiles()
                                    val target = childFiles?.firstOrNull { f ->
                                        val name = f.name.lowercase()
                                        if (contentType == 2) {
                                            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".webm") || name.endsWith(".m4v") || name.endsWith(".3gp") || name.endsWith(".mov") || name.endsWith(".flv")
                                        } else {
                                            name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".m4a") || name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".aac")
                                        }
                                    }
                                    target?.absolutePath ?: chapter.folderUri
                                } else {
                                    chapter.folderUri
                                }
                                retriever.setDataSource(targetPath)
                            }

                            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            duration = durationStr?.toLongOrNull() ?: 0L

                            if (contentType == 1 && artist.isNullOrBlank()) {
                                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                            }

                            if (thumbUri == null) {
                                // Step 3: Embedded poster art (MKV/MP4/MP3 cover image)
                                val bitmap = retriever.embeddedPicture?.let {
                                    BitmapFactory.decodeByteArray(it, 0, it.size)
                                } ?: run {
                                    // Step 4: Mid-video frame as final fallback (for video only)
                                    // IMPORTANT: getFrameAtTime takes MICROSECONDS, but duration is in MILLISECONDS.
                                    // For very long videos (>1hr), seek to 5s mark to avoid OOM.
                                    if (contentType == 2 && duration > 0) {
                                        val seekMs = if (duration > 3_600_000L) 5_000L else duration / 2L
                                        val seekUs = seekMs * 1_000L
                                        retriever.getFrameAtTime(seekUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                            ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                    } else null
                                }

                                if (bitmap != null) {
                                    val tmpFile = File(getApplication<Application>().cacheDir, "thumb_${chapter.id}.tmp")
                                    val thumbFile = File(getApplication<Application>().cacheDir, "thumb_${chapter.id}_v2.jpg")
                                    FileOutputStream(tmpFile).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                    }
                                    tmpFile.renameTo(thumbFile)
                                    thumbUri = thumbFile.absolutePath
                                }
                            }
                        } finally {
                            retriever.release()
                        }
                    }

                    trackDao.insertChapter(chapter.copy(duration = duration, thumbnailUri = thumbUri, artist = artist))
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    scanningChapters.remove(chapter.id)
                }
            }
        }
    }
    fun createChildBox(label: String, purpose: String, onCreated: (Long) -> Unit) {
        val parent = _manga.value ?: return
        viewModelScope.launch {
            val rootParentId = parent.parentMangaId ?: parent.id
            val nextPosition = _childBoxes.value.size
            
            // Generate a safe unique subfolder name for this box in the vault
            val safeLabel = label.replace(Regex("[^a-zA-Z0-9]"), "_")
            val uniqueSubfolder = "${safeLabel}_${System.currentTimeMillis()}"
            val newParentUri = if (parent.parentUri.startsWith("content://")) {
                parent.parentUri // External mode might still share root URI, but scans subdirs
            } else {
                val rootDir = java.io.File(parent.parentUri).parentFile ?: java.io.File(parent.parentUri)
                java.io.File(rootDir, uniqueSubfolder).absolutePath
            }

            val entity = MangaEntity(
                title = label,
                description = parent.description,
                thoughts = parent.thoughts,
                coverPath = parent.coverPath,
                isNsfw = parent.isNsfw,
                parentUri = newParentUri,
                lastModified = System.currentTimeMillis(),
                contentType = parent.contentType,
                parentMangaId = rootParentId,
                boxLabel = label,
                boxPurpose = purpose,
                position = nextPosition,
                workspace = parent.workspace
            )
            val newId = withContext(Dispatchers.IO) { libraryDao.insertManga(entity) }
            onCreated(newId)
        }
    }

    fun updateChapterPositions(chapters: List<ChapterEntity>) {
        _chapters.value = chapters
        viewModelScope.launch(Dispatchers.IO) {
            chapters.forEachIndexed { index, chapter ->
                trackDao.insertChapter(chapter.copy(position = index))
            }
        }
    }

    fun updateAllBoxPositions(boxes: List<MangaEntity>) {
        val updatedBoxes = boxes.mapIndexed { index, box -> box.copy(position = index) }

        // Update childBoxes in-memory (excluding the root which has parentMangaId == null)
        _childBoxes.value = updatedBoxes.filter { it.parentMangaId != null }.toList()
        
        // Also update the root if it's in the list (though it's usually tracked separately)
        updatedBoxes.find { it.parentMangaId == null }?.let { root ->
            _rootManga.value = root
            if (_manga.value?.id == root.id) {
                _manga.value = root
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            updatedBoxes.forEach { box ->
                libraryDao.insertManga(box)
            }
        }
    }

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
    }

    fun saveManga() {
        viewModelScope.launch {
            val currentTags = _assignedTags.value
            val genreString = if (currentTags.isNotEmpty()) {
                currentTags.joinToString(", ")
            } else {
                draftGenre.value.ifBlank { null }
            }

            val entity = MangaEntity(
                id = if (currentMangaId == -1L) 0L else currentMangaId,
                title = draftTitle.value.ifBlank { "Untitled" },
                description = draftDescription.value.ifBlank { "No description added yet." },
                thoughts = draftThoughts.value.ifBlank { "No thoughts added." },
                coverPath = draftCoverPath.value,
                isNsfw = draftIsNsfw.value,
                parentUri = _manga.value?.parentUri ?: "",
                lastModified = System.currentTimeMillis(),
                contentType = draftContentType.value,
                parentMangaId = _manga.value?.parentMangaId,
                boxLabel = _manga.value?.boxLabel,
                boxPurpose = draftBoxPurpose.value,
                position = _manga.value?.position ?: 0,
                lastReadTitle = _manga.value?.lastReadTitle,
                lastReadPage = _manga.value?.lastReadPage,
                genre = genreString
            )
            val newId = withContext(Dispatchers.IO) { libraryDao.insertManga(entity) }
            if (currentMangaId == -1L) {
                currentMangaId = newId
            }

            // Save EntryMetadata to entry.json / metadata.json
            val context = getApplication<Application>().applicationContext
            val updatedMeta = EntryMetadata(
                title = entity.title,
                altTitle = draftAltTitle.value,
                author = draftAuthor.value,
                artist = draftArtist.value,
                description = entity.description,
                type = when (entity.contentType) {
                    0 -> if (entity.genre?.contains("manhua", ignoreCase = true) == true) "Manhua" 
                         else if (entity.genre?.contains("manhwa", ignoreCase = true) == true) "Manhwa" 
                         else "Manga"
                    1 -> "Book"
                    2 -> if (entity.boxPurpose == "series") "Series Video" else "Channel Video"
                    else -> "Media"
                },
                status = draftStatus.value,
                rating = draftRating.value,
                tags = currentTags,
                publisher = draftPublisher.value,
                serialization = draftSerialization.value,
                year = draftYear.value,
                totalChapters = _chapters.value.size
            )
            withContext(Dispatchers.IO) {
                MediaMetadataManager.saveMetadata(context, currentMangaId, entity.parentUri, updatedMeta)
            }
            _entryMetadata.value = updatedMeta

            _isEditMode.value = false
            loadManga(currentMangaId)
        }
    }

    fun addTag(tag: String) {
        val clean = tag.trim()
        if (clean.isNotBlank() && !_assignedTags.value.any { it.equals(clean, ignoreCase = true) }) {
            _assignedTags.value = _assignedTags.value + clean
            syncGenreWithTags()
        }
    }

    fun removeTag(tag: String) {
        _assignedTags.value = _assignedTags.value.filterNot { it.equals(tag, ignoreCase = true) }
        syncGenreWithTags()
    }

    private fun syncGenreWithTags() {
        draftGenre.value = _assignedTags.value.joinToString(", ")
    }

    fun setTagQuery(query: String) {
        _tagQuery.value = query
        searchMasterTags(query)
    }

    fun toggleTagSearchVisible() {
        _isTagSearchVisible.value = !_isTagSearchVisible.value
    }

    fun searchMasterTags(query: String) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val suggestions = withContext(Dispatchers.IO) {
                MediaMetadataManager.searchMasterTags(context, query)
            }
            _tagSuggestions.value = suggestions
        }
    }

    fun setMaterialTag(tag: String) {
        when (tag) {
            "Manga" -> {
                draftContentType.value = 0
                if (draftGenre.value.contains("manhua", ignoreCase = true) || draftGenre.value.contains("manhwa", ignoreCase = true) || draftGenre.value.contains("webtoon", ignoreCase = true)) {
                    draftGenre.value = "Manga"
                }
            }
            "Manhua" -> {
                draftContentType.value = 0
                draftGenre.value = "Manhua"
            }
            "Book" -> {
                draftContentType.value = 1
            }
            "Series Video" -> {
                draftContentType.value = 2
                draftBoxPurpose.value = "series"
            }
            "Channel Video" -> {
                draftContentType.value = 2
                draftBoxPurpose.value = "channel"
            }
        }
    }

    fun updateMaterialTagDirectly(tag: String) {
        val m = _manga.value ?: return
        setMaterialTag(tag)
        viewModelScope.launch {
            val updated = when (tag) {
                "Manga" -> m.copy(
                    contentType = 0,
                    genre = if (m.genre?.contains("manhua", ignoreCase = true) == true || m.genre?.contains("manhwa", ignoreCase = true) == true || m.genre?.contains("webtoon", ignoreCase = true) == true) "Manga" else m.genre
                )
                "Manhua" -> m.copy(
                    contentType = 0,
                    genre = "Manhua"
                )
                "Book" -> m.copy(
                    contentType = 1
                )
                "Series Video" -> m.copy(
                    contentType = 2,
                    boxPurpose = "series"
                )
                "Channel Video" -> m.copy(
                    contentType = 2,
                    boxPurpose = "channel"
                )
                else -> m
            }
            withContext(Dispatchers.IO) {
                libraryDao.insertManga(updated)
            }
            loadManga(m.id)
        }
    }

    fun getEffectiveMaterialTag(isEdit: Boolean): String {
        return if (isEdit) {
            when (draftContentType.value) {
                0 -> if (draftGenre.value.contains("manhua", ignoreCase = true) || draftGenre.value.contains("manhwa", ignoreCase = true) || draftGenre.value.contains("webtoon", ignoreCase = true)) "Manhua" else "Manga"
                1 -> "Book"
                2 -> if (draftBoxPurpose.value == "channel") "Channel Video" else "Series Video"
                3 -> "Music"
                else -> "Manga"
            }
        } else {
            val m = _manga.value
            when (m?.contentType) {
                0 -> if (m.genre?.contains("manhua", ignoreCase = true) == true || m.genre?.contains("manhwa", ignoreCase = true) == true || m.genre?.contains("webtoon", ignoreCase = true) == true) "Manhua" else "Manga"
                1 -> "Book"
                2 -> if (m.boxPurpose == "channel") "Channel Video" else "Series Video"
                3 -> "Music"
                else -> "Manga"
            }
        }
    }

    fun updateTracker(title: String? = null, page: Int? = null) {
        viewModelScope.launch {
            val m = libraryDao.getMangaById(currentMangaId)
            if (m != null) {
                withContext(Dispatchers.IO) {
                    libraryDao.insertManga(m.copy(
                        lastReadTitle = title ?: m.lastReadTitle,
                        lastReadPage = page ?: m.lastReadPage
                    ))
                }
                loadManga(currentMangaId)
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _isImporting.value = false
        _importProgress.value = 0
    }

    fun importChapters(uri: Uri, storageModeOverride: Int? = null) {
        if (currentMangaId == -1L) return // Must save first

        _isImporting.value = true
        _importProgress.value = 0
        
        importJob = AppImportScope.launch {
            val isLocalMode = (storageModeOverride ?: com.ballade.hwaran.core.datastore.GlobalSettings(getApplication()).storageModeFlow.first()) == 0

            try {
                val m = libraryDao.getMangaById(currentMangaId) ?: return@launch
                val sourceDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), uri) ?: return@launch
                val contentResolver = getApplication<Application>().contentResolver
                
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }

                fun copyDoc(doc: androidx.documentfile.provider.DocumentFile, currentDestDir: java.io.File) {
                    doc.listFiles().forEach { child ->
                        val name = child.name ?: return@forEach
                        val destFile = java.io.File(currentDestDir, name)

                        if (child.isDirectory) {
                            destFile.mkdirs()
                            copyDoc(child, destFile)
                            child.delete()
                        } else {
                            try {
                                contentResolver.openInputStream(child.uri)?.use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                child.delete()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

                // Try to use faster cursor for external mode if possible
                val subDirs = mutableListOf<androidx.documentfile.provider.DocumentFile>()
                val files = mutableListOf<androidx.documentfile.provider.DocumentFile>()
                val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".webm", ".m4v", ".3gp", ".mov", ".flv")
                val audioExtensions = listOf(".mp3", ".flac", ".m4a", ".wav", ".ogg", ".aac")
                val children = sourceDoc.listFiles()
                children.forEach { 
                    if (it.isDirectory) subDirs.add(it) 
                    else {
                        if (m.contentType == 2) {
                            if (it.name != null && videoExtensions.any { ext -> it.name!!.lowercase().endsWith(ext) }) {
                                files.add(it)
                            }
                        } else if (m.contentType == 3) {
                            if (it.name != null && audioExtensions.any { ext -> it.name!!.lowercase().endsWith(ext) }) {
                                files.add(it)
                            }
                        } else {
                            files.add(it)
                        }
                    }
                }

                if (!isLocalMode) {
                    // EXTERNAL MODE
                    if ((m.contentType == 2 || m.contentType == 3) && files.isNotEmpty()) {
                        val total = files.size
                        files.forEachIndexed { index, fileDoc ->
                            val rawName = fileDoc.name ?: "Unknown Chapter"
                            val title = if (rawName.contains(".")) rawName.substringBeforeLast(".") else rawName
                            trackDao.insertChapter(
                                ChapterEntity(
                                    mangaId = currentMangaId,
                                    title = title,
                                    folderUri = fileDoc.uri.toString(),
                                    position = index
                                )
                            )
                            _importProgress.value = (((index + 1).toFloat() / total) * 100).toInt()
                        }
                    } else if (subDirs.isEmpty()) {
                        trackDao.insertChapter(
                            ChapterEntity(
                                mangaId = currentMangaId,
                                title = sourceDoc.name ?: "Unknown Chapter",
                                folderUri = sourceDoc.uri.toString()
                            )
                        )
                    } else {
                        val totalChapters = subDirs.size
                        var processed = 0
                        subDirs.forEach { chapterDoc ->
                            val rawName = chapterDoc.name ?: "Unknown Chapter"
                            val title = if (m.contentType == 2 && rawName.contains(".")) rawName.substringBeforeLast(".") else rawName
                            
                            val mediaFileDoc = if (m.contentType == 2 || m.contentType == 3) {
                                val extensions = if (m.contentType == 2) videoExtensions else audioExtensions
                                chapterDoc.listFiles().find { f ->
                                    f.name?.let { name -> extensions.any { name.lowercase().endsWith(it) } } == true
                                }
                            } else null
                            val finalUriStr = mediaFileDoc?.uri?.toString() ?: chapterDoc.uri.toString()

                            trackDao.insertChapter(
                                ChapterEntity(
                                    mangaId = currentMangaId,
                                    title = title,
                                    folderUri = finalUriStr,
                                    position = processed
                                )
                            )
                            processed++
                            _importProgress.value = ((processed.toFloat() / totalChapters) * 100).toInt()
                        }
                    }
                    _importProgress.value = 100
                } else {
                    // LOCAL MODE
                    var vaultPath = m.parentUri
                    if (vaultPath.isEmpty() || vaultPath.startsWith("content://")) {
                        val vaultBaseRoot = java.io.File(getApplication<Application>().filesDir, "manga_vault")
                        if (!vaultBaseRoot.exists()) vaultBaseRoot.mkdirs()
                        val safeTitle = m.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                        val mangaVault = java.io.File(vaultBaseRoot, "${safeTitle}_${m.id}")
                        if (!mangaVault.exists()) mangaVault.mkdirs()
                        vaultPath = mangaVault.absolutePath
                        
                        // Update manga entity
                        libraryDao.insertManga(m.copy(parentUri = vaultPath))
                    }
                    val vaultBase = java.io.File(vaultPath)
                    if (!vaultBase.exists()) vaultBase.mkdirs()

                    if ((m.contentType == 2 || m.contentType == 3) && files.isNotEmpty()) {
                        val total = files.size
                        files.forEachIndexed { index, child ->
                            val rawName = child.name ?: "Unknown Chapter"
                            val chapterName = if (rawName.contains(".")) rawName.substringBeforeLast(".") else rawName
                            val chapterDest = java.io.File(vaultBase, chapterName)
                            chapterDest.mkdirs()
                            
                            val destFile = java.io.File(chapterDest, child.name ?: "file")
                            val totalSize = child.length()
                            val baseProgress = (index.toFloat() / total) * 100
                            val progressScale = 100f / total

                            try {
                                contentResolver.openInputStream(child.uri)?.use { input ->
                                    destFile.outputStream().use { output ->
                                        val buffer = ByteArray(8192)
                                        var bytesCopied = 0L
                                        var bytesRead = input.read(buffer)
                                        var lastReport = -1
                                        while (bytesRead >= 0) {
                                            output.write(buffer, 0, bytesRead)
                                            bytesCopied += bytesRead
                                            if (totalSize > 0) {
                                                val fileProgress = (bytesCopied.toDouble() / totalSize)
                                                val overallProgress = (baseProgress + fileProgress * progressScale).toInt()
                                                if (overallProgress != lastReport) {
                                                    lastReport = overallProgress
                                                    _importProgress.value = overallProgress.coerceIn(0, 100)
                                                }
                                            }
                                            bytesRead = input.read(buffer)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            
                            trackDao.insertChapter(
                                ChapterEntity(
                                    mangaId = currentMangaId,
                                    title = chapterName,
                                    folderUri = destFile.absolutePath,
                                    position = index
                                )
                            )
                        }
                        _importProgress.value = 100
                    } else if (subDirs.isEmpty()) {
                        val rawName = sourceDoc.name ?: "Unknown Chapter"
                        val chapterName = if (m.contentType == 2 && rawName.contains(".")) rawName.substringBeforeLast(".") else rawName
                        val chapterDest = java.io.File(vaultBase, chapterName)
                        chapterDest.mkdirs()
                        
                        val totalFiles = children.filter { !it.isDirectory }.size
                        var copiedFiles = 0
                        children.forEach { child ->
                            if (!child.isDirectory) {
                                val destFile = java.io.File(chapterDest, child.name ?: return@forEach)
                                try {
                                    contentResolver.openInputStream(child.uri)?.use { input ->
                                        destFile.outputStream().use { output -> input.copyTo(output) }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                copiedFiles++
                                _importProgress.value = if (totalFiles > 0) ((copiedFiles.toFloat() / totalFiles) * 100).toInt() else 100
                            }
                        }

                        val mediaFile = if (m.contentType == 2 || m.contentType == 3) {
                            val extensions = if (m.contentType == 2) videoExtensions else audioExtensions
                            chapterDest.listFiles()?.firstOrNull { f ->
                                val name = f.name.lowercase()
                                extensions.any { name.endsWith(it) }
                            }
                        } else null
                        val finalFolderUri = mediaFile?.absolutePath ?: chapterDest.absolutePath

                        trackDao.insertChapter(
                            ChapterEntity(
                                mangaId = currentMangaId,
                                title = chapterDest.name,
                                folderUri = finalFolderUri
                            )
                        )
                        _importProgress.value = 100
                    } else {
                        val totalChapters = subDirs.size
                        var copiedChapters = 0

                        subDirs.forEach { chapterDoc ->
                            val rawName = chapterDoc.name ?: return@forEach
                            val chapterName = if (m.contentType == 2 && rawName.contains(".")) rawName.substringBeforeLast(".") else rawName
                            val chapterDest = java.io.File(vaultBase, chapterName)
                            chapterDest.mkdirs()
                            
                            copyDoc(chapterDoc, chapterDest)
                            
                            val mediaFile = if (m.contentType == 2 || m.contentType == 3) {
                                val extensions = if (m.contentType == 2) videoExtensions else audioExtensions
                                chapterDest.listFiles()?.firstOrNull { f ->
                                    val name = f.name.lowercase()
                                    extensions.any { name.endsWith(it) }
                                }
                            } else null
                            val finalFolderUri = mediaFile?.absolutePath ?: chapterDest.absolutePath

                            trackDao.insertChapter(
                                ChapterEntity(
                                    mangaId = currentMangaId,
                                    title = chapterDest.name,
                                    folderUri = finalFolderUri,
                                    position = copiedChapters
                                )
                            )
                            
                            copiedChapters++
                            _importProgress.value = ((copiedChapters.toFloat() / totalChapters) * 100).toInt()
                        }
                    }
                    
                    // Cleanup external source folder after moving everything into the Vault
                    try {
                        sourceDoc.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Reload chapters in UI
                loadManga(currentMangaId)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isImporting.value = false
                _importProgress.value = 0
            }
        }
    }

    fun importMultipleVideos(uris: List<Uri>, storageModeOverride: Int? = null) {
        if (currentMangaId == -1L) return

        _isImporting.value = true
        _importProgress.value = 0

        importJob = AppImportScope.launch {
            val isLocalMode = (storageModeOverride ?: com.ballade.hwaran.core.datastore.GlobalSettings(getApplication()).storageModeFlow.first()) == 0

            try {
                val m = libraryDao.getMangaById(currentMangaId) ?: return@launch
                val contentResolver = getApplication<Application>().contentResolver

                val total = uris.size
                val currentChaptersCount = _chapters.value.size
                val newChapters = mutableListOf<ChapterEntity>()
                
                uris.forEachIndexed { index, uri ->
                    try {
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (e: SecurityException) {
                        e.printStackTrace()
                    }

                    var fileName = "Unknown Video"
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }

                    val chapterName = fileName.substringBeforeLast(".")
                    var finalUri = uri.toString()
                    
                    if (isLocalMode) {
                        var vaultPath = m.parentUri
                        if (vaultPath.isEmpty() || vaultPath.startsWith("content://")) {
                            val vaultBaseRoot = java.io.File(getApplication<Application>().filesDir, "manga_vault")
                            if (!vaultBaseRoot.exists()) vaultBaseRoot.mkdirs()
                            val safeTitle = m.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                            val mangaVault = java.io.File(vaultBaseRoot, "${safeTitle}_${m.id}")
                            if (!mangaVault.exists()) mangaVault.mkdirs()
                            vaultPath = mangaVault.absolutePath
                            
                            // Update manga entity
                            libraryDao.insertManga(m.copy(parentUri = vaultPath))
                        }
                        val vaultBase = java.io.File(vaultPath)
                        if (!vaultBase.exists()) vaultBase.mkdirs()
                        
                        val chapterDest = java.io.File(vaultBase, chapterName)
                        chapterDest.mkdirs()
                        
                        val destFile = java.io.File(chapterDest, fileName)
                        try {
                            contentResolver.openInputStream(uri)?.use { input ->
                                destFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            finalUri = destFile.absolutePath
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Extract Metadata
                    var duration = 0L
                    var thumbUri: String? = null
                    
                    if (m.contentType == 2) {
                        try {
                            val retriever = MediaMetadataRetriever()
                            if (finalUri.startsWith("content://")) {
                                retriever.setDataSource(getApplication(), Uri.parse(finalUri))
                            } else {
                                retriever.setDataSource(finalUri)
                            }
                            
                            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            duration = durationStr?.toLongOrNull() ?: 0L
                            
                            // IMPORTANT: getFrameAtTime takes MICROSECONDS, duration is MILLISECONDS.
                            // For long videos use 5s mark to avoid OOM on huge seeks.
                            val seekMs = if (duration > 3_600_000L) 5_000L else duration / 2L
                            val seekUs = seekMs * 1_000L
                            val bitmap = retriever.embeddedPicture?.let { 
                                BitmapFactory.decodeByteArray(it, 0, it.size) 
                            } ?: retriever.getFrameAtTime(seekUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            
                            if (bitmap != null) {
                                val thumbFile = File(getApplication<Application>().cacheDir, "thumb_${UUID.randomUUID()}.jpg")
                                FileOutputStream(thumbFile).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                                }
                                thumbUri = thumbFile.absolutePath
                            }
                            retriever.release()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    newChapters.add(
                        ChapterEntity(
                            mangaId = currentMangaId,
                            title = chapterName,
                            folderUri = finalUri,
                            position = currentChaptersCount + index,
                            duration = duration,
                            thumbnailUri = thumbUri
                        )
                    )

                    _importProgress.value = (((index + 1).toFloat() / total) * 100).toInt()
                }

                if (newChapters.isNotEmpty()) {
                    trackDao.insertChapters(newChapters)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isImporting.value = false
                _importProgress.value = 0
            }
        }
    }

    fun importMultipleMusic(uris: List<Uri>, storageModeOverride: Int? = null) {
        if (currentMangaId == -1L) return

        _isImporting.value = true
        _importProgress.value = 0

        AppImportScope.launch {
            val isLocalMode = (storageModeOverride ?: com.ballade.hwaran.core.datastore.GlobalSettings(getApplication()).storageModeFlow.first()) == 0

            try {
                val m = libraryDao.getMangaById(currentMangaId) ?: return@launch
                val contentResolver = getApplication<Application>().contentResolver

                val total = uris.size
                uris.forEachIndexed { index, uri ->
                    try {
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (e: SecurityException) {
                        e.printStackTrace()
                    }

                    var fileName = "Unknown Song"
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }

                    val chapterName = if (fileName.contains(".")) fileName.substringBeforeLast(".") else fileName

                    var duration = 0L
                    var thumbUri: String? = null
                    var artist: String? = null

                    if (!isLocalMode) {
                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(getApplication(), uri)
                            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            duration = durationStr?.toLongOrNull() ?: 0L
                            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                            val bitmap = retriever.embeddedPicture?.let { 
                                BitmapFactory.decodeByteArray(it, 0, it.size) 
                            }
                            if (bitmap != null) {
                                val thumbFile = File(getApplication<Application>().cacheDir, "thumb_${UUID.randomUUID()}.jpg")
                                FileOutputStream(thumbFile).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                                }
                                thumbUri = thumbFile.absolutePath
                            }
                            retriever.release()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        trackDao.insertChapter(
                            ChapterEntity(
                                mangaId = currentMangaId,
                                title = chapterName,
                                folderUri = uri.toString(),
                                position = index,
                                duration = duration,
                                thumbnailUri = thumbUri,
                                artist = artist
                            )
                        )
                    } else {
                        var vaultPath = m.parentUri
                        if (vaultPath.isEmpty() || vaultPath.startsWith("content://")) {
                            val vaultBaseRoot = java.io.File(getApplication<Application>().filesDir, "manga_vault")
                            if (!vaultBaseRoot.exists()) vaultBaseRoot.mkdirs()
                            val safeTitle = m.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                            val mangaVault = java.io.File(vaultBaseRoot, "${safeTitle}_${m.id}")
                            if (!mangaVault.exists()) mangaVault.mkdirs()
                            vaultPath = mangaVault.absolutePath
                            
                            // Update manga entity
                            libraryDao.insertManga(m.copy(parentUri = vaultPath))
                        }
                        val vaultBase = java.io.File(vaultPath)
                        if (!vaultBase.exists()) vaultBase.mkdirs()
                        
                        val chapterDest = java.io.File(vaultBase, chapterName)
                        chapterDest.mkdirs()
                        
                        val destFile = java.io.File(chapterDest, fileName)
                        try {
                            contentResolver.openInputStream(uri)?.use { input ->
                                destFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(destFile.absolutePath)
                            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            duration = durationStr?.toLongOrNull() ?: 0L
                            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                            val bitmap = retriever.embeddedPicture?.let { 
                                BitmapFactory.decodeByteArray(it, 0, it.size) 
                            }
                            if (bitmap != null) {
                                val thumbFile = File(getApplication<Application>().cacheDir, "thumb_${UUID.randomUUID()}.jpg")
                                FileOutputStream(thumbFile).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                                }
                                thumbUri = thumbFile.absolutePath
                            }
                            retriever.release()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        
                        trackDao.insertChapter(
                            ChapterEntity(
                                mangaId = currentMangaId,
                                title = chapterName,
                                folderUri = chapterDest.absolutePath,
                                position = index,
                                duration = duration,
                                thumbnailUri = thumbUri,
                                artist = artist
                            )
                        )
                    }

                    _importProgress.value = (((index + 1).toFloat() / total) * 100).toInt()
                }

                // Reload chapters in UI
                loadManga(currentMangaId)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isImporting.value = false
                _importProgress.value = 0
            }
        }
    }

    fun deleteManga(onDeleted: (Long?) -> Unit) {
        viewModelScope.launch {
            val m = _manga.value
            if (m != null) {
                com.ballade.hwaran.core.util.HistoryTracker.logEvent("DELETE", m.title, "Manga Folder")
                withContext(Dispatchers.IO) {
                    val isChildBox = m.parentMangaId != null
                    
                    if (isChildBox) {
                        // For child boxes, only delete chapters and their specific files
                        val chaptersToDelete = trackDao.getChaptersForMangaList(m.id)
                        chaptersToDelete.forEach { chapter ->
                            val folder = java.io.File(chapter.folderUri)
                            if (folder.exists()) {
                                folder.deleteRecursively()
                            }
                        }
                        trackDao.deleteChaptersByMangaId(m.id)
                    } else {
                        // For root manga, delete everything in the vault
                        val folder = java.io.File(m.parentUri)
                        if (folder.exists()) {
                            folder.deleteRecursively() // Physical true-delete from vault
                        }
                        
                        // Clean up all children and their chapters from DB
                        val children = libraryDao.getChildrenForMangaList(m.id)
                        children.forEach { child ->
                            trackDao.deleteChaptersByMangaId(child.id)
                        }
                        libraryDao.deleteChildrenByParentId(m.id)
                        trackDao.deleteChaptersByMangaId(m.id)
                    }
                    
                    database.libraryDao().deleteManga(m)
                }
                onDeleted(m.parentMangaId)
            }
        }
    }
    
    fun deleteSelectedChapters(chapterIds: List<Long>) {
        if (chapterIds.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chapterIds.forEach { id ->
                    val chapter = database.trackDao().getChapterById(id)
                    if (chapter != null) {
                        com.ballade.hwaran.core.util.HistoryTracker.logEvent("DELETE", chapter.title, "Chapter (Folder)")
                        val folder = java.io.File(chapter.folderUri)
                        if (folder.exists()) {
                            folder.deleteRecursively() // Physical true-delete
                        }
                        database.trackDao().deleteChapter(chapter)
                    }
                }
            }
        }
    }
    fun setNsfw(isNsfw: Boolean) {
        viewModelScope.launch {
            val m = _manga.value
            if (m != null) {
                val updatedManga = m.copy(isNsfw = isNsfw, lastModified = System.currentTimeMillis())
                withContext(Dispatchers.IO) {
                    libraryDao.insertManga(updatedManga)
                }
                _manga.value = updatedManga
                draftIsNsfw.value = updatedManga.isNsfw
            }
        }
    }

    fun updateBoxLabel(mangaId: Long, label: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val m = libraryDao.getMangaById(mangaId)
                if (m != null) {
                    libraryDao.insertManga(m.copy(boxLabel = label))
                }
            }
            if (_manga.value?.id == mangaId) {
                _manga.value = _manga.value?.copy(boxLabel = label)
            }
            if (_rootManga.value?.id == mangaId) {
                _rootManga.value = _rootManga.value?.copy(boxLabel = label)
            }
        }
    }

    fun updateChapterThumbnail(chapterId: Long, thumbnailUri: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val chapter = database.trackDao().getChapterById(chapterId)
                if (chapter != null) {
                    database.trackDao().insertChapter(chapter.copy(thumbnailUri = thumbnailUri))
                }
            }
            // Update in-memory chapters list
            _chapters.value = _chapters.value.map {
                if (it.id == chapterId) it.copy(thumbnailUri = thumbnailUri) else it
            }
        }
    }
}
