package com.ballade.hwaran.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.entity.ChapterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = LibraryRepository(application, database)

    private val _isNsfwFilter = MutableStateFlow(false)
    val isNsfwFilter: StateFlow<Boolean> = _isNsfwFilter

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    val libraryState: StateFlow<List<MangaEntity>> = database.libraryDao().getAllManga()
        .combine(_isNsfwFilter) { allManga, isNsfw ->
            _isLoading.value = false
            if (isNsfw) {
                allManga.filter { it.isNsfw }
            } else {
                allManga.filter { !it.isNsfw }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val favoritedUris: StateFlow<Set<String>> = database.libraryDao().getAllManga()
        .map { list -> list.find { it.title.equals("Favorites", ignoreCase = true) && it.contentType == 3 } }
        .flatMapLatest { favManga ->
            if (favManga == null) kotlinx.coroutines.flow.flowOf(emptySet())
            else database.trackDao().getChaptersForManga(favManga.id)
                .map { chapters -> 
                    chapters.flatMap { listOfNotNull(it.folderUri, it.title.takeIf { t -> t.isNotBlank() }) }.toSet() 
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val allMangaState: StateFlow<List<MangaEntity>> = database.libraryDao().getAllManga()
        .combine(MutableStateFlow(Unit)) { allManga, _ ->
            _isLoading.value = false
            allManga
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allMangaEverywhereState: StateFlow<List<MangaEntity>> = database.libraryDao().getAllMangaEverywhereFlow()
        .combine(MutableStateFlow(Unit)) { allManga, _ ->
            _isLoading.value = false
            allManga
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun getScreenNamesForMode(mediaMode: Int): kotlinx.coroutines.flow.Flow<List<String>> {
        return database.libraryDao().getDistinctWorkspacesForContentType(mediaMode)
    }

    init {
        // Self-heal covers in background on startup
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val mediaDao = database.libraryDao()
                val allItems = mediaDao.getAllMangaList()
                allItems.forEach { manga ->
                    var updated = false
                    var newCover = manga.coverPath

                    // 1. If coverPath is a content:// URI, attempt to cache it locally
                    if (newCover.startsWith("content://")) {
                        val cached = com.ballade.hwaran.core.util.CoverCacheManager.cacheCoverFromUri(
                            context = app,
                            sourceUri = Uri.parse(newCover),
                            prefix = if (manga.contentType == 3) "music" else "cover",
                            title = manga.title
                        )
                        if (cached != null) {
                            newCover = cached
                            updated = true
                        }
                    }

                    // 3. For toon / manga (contentType == 0) or video (contentType == 2):
                    // If coverPath is empty, check if there is a legitimate dedicated cover in parent directory.
                    if ((manga.contentType == 0 || manga.contentType == 2) && newCover.isEmpty()) {
                        val folderDoc = if (manga.parentUri.startsWith("content://")) {
                            try { DocumentFile.fromTreeUri(app, Uri.parse(manga.parentUri)) } catch (e: Exception) { null }
                        } else if (manga.parentUri.isNotEmpty()) {
                            val f = File(manga.parentUri)
                            if (f.exists()) DocumentFile.fromFile(f) else null
                        } else null

                        if (folderDoc != null && folderDoc.exists() && folderDoc.isDirectory) {
                            val childFiles = folderDoc.listFiles() ?: emptyArray()
                            val dedicatedDoc = if (manga.contentType == 0) {
                                com.ballade.hwaran.data.importer.toon.ToonImportUtils.findCoverInFiles(childFiles)
                            } else {
                                val imageFiles = childFiles.filter { file ->
                                    !file.isDirectory && com.ballade.hwaran.data.importer.video.VideoImportUtils.coverExtensions.any { ext ->
                                        file.name?.lowercase()?.endsWith(".$ext") == true
                                    }
                                }.toTypedArray()
                                com.ballade.hwaran.data.importer.video.VideoImportUtils.findCoverInFiles(imageFiles)
                            }

                            if (dedicatedDoc != null) {
                                val prefix = if (manga.contentType == 0) "toon" else "video"
                                val cached = com.ballade.hwaran.core.util.CoverCacheManager.cacheCoverFromUri(
                                    context = app,
                                    sourceUri = dedicatedDoc.uri,
                                    prefix = prefix,
                                    title = manga.title
                                )
                                if (cached != null && cached != newCover) {
                                    newCover = cached
                                    updated = true
                                }
                            }
                        }
                    }

                    // 4. If cached file path does not exist on disk, clear it
                    if (newCover.isNotEmpty() && newCover.startsWith("/data/") && !File(newCover).exists()) {
                        newCover = ""
                        updated = true
                    }

                    if (updated && newCover != manga.coverPath) {
                        mediaDao.insertManga(manga.copy(coverPath = newCover))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    private val _isMegaImporting = MutableStateFlow(false)
    val isMegaImporting: StateFlow<Boolean> = _isMegaImporting

    val isImportingGlobal: StateFlow<Boolean> = combine(_isImporting, _isMegaImporting) { a, b -> a || b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _importProgress = MutableStateFlow(0)
    val importProgress: StateFlow<Int> = _importProgress

    private val _megaImportProgress = MutableStateFlow(0f)
    val megaImportProgress: StateFlow<Float> = _megaImportProgress

    data class MegaImportSummary(
        val total: Int,
        val imported: Int,
        val skipped: Int,
        val skippedFolders: List<String>,
        val isCancelled: Boolean = false
    )

    private val _megaImportSummary = MutableStateFlow<MegaImportSummary?>(null)
    val megaImportSummary: StateFlow<MegaImportSummary?> = _megaImportSummary

    private val _isCancelRequested = MutableStateFlow(false)
    val isCancelRequested: StateFlow<Boolean> = _isCancelRequested

    private var importJob: kotlinx.coroutines.Job? = null

    fun toggleFilter(isNsfw: Boolean) {
        _isNsfwFilter.value = isNsfw
    }

    private val _isCancelArmed = MutableStateFlow(false)
    val isCancelArmed: StateFlow<Boolean> = _isCancelArmed

    fun armCancel() {
        _isCancelArmed.value = true
    }

    fun cancelImport() {
        _isCancelRequested.value = true
        _isCancelArmed.value = false
    }

    fun clearMegaImportSummary() {
        _megaImportSummary.value = null
    }

    fun megaImportFolder(
        parentUri: Uri,
        boxPurposeOverride: String? = null,
        workspace: String? = null,
        isNsfwOverride: Boolean? = null,
        storageModeOverride: Int? = null,
        mediaModeOverride: Int? = null
    ) {
        if (isImportingGlobal.value) return
        _isMegaImporting.value = true
        _isCancelRequested.value = false
        _isCancelArmed.value = false

        importJob?.cancel()
        importJob = viewModelScope.launch {
            _megaImportProgress.value = 0f
            _megaImportSummary.value = null

            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                getApplication<Application>().contentResolver.takePersistableUriPermission(parentUri, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val globalSettings = com.ballade.hwaran.core.datastore.GlobalSettings(getApplication())
            val mediaMode = mediaModeOverride ?: globalSettings.mediaModeFlow.first()
            val isLocalMode = (storageModeOverride ?: globalSettings.storageModeFlow.first()) == 0
            val videoLayoutMode = globalSettings.videoLayoutModeFlow.first()
            val boxPurpose = boxPurposeOverride ?: if (videoLayoutMode == 1) "channel" else "series"

            try {
                if (mediaMode == 3) {
                    importMusicFolder(parentUri, workspace)
                    return@launch
                } else if (mediaMode == 1) { // Book Mode
                    val parentDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), parentUri)
                    if (parentDoc != null) {
                        val mode = com.ballade.hwaran.data.importer.book.BookImportUtils.detectImportMode(parentDoc)
                        if (mode == "SINGLE") {
                            _isMegaImporting.value = false
                            importFolder(
                                uri = parentUri,
                                boxPurposeOverride = boxPurposeOverride,
                                workspace = workspace,
                                isNsfwOverride = isNsfwOverride,
                                storageModeOverride = storageModeOverride
                            )
                            return@launch
                        } else {
                            val bookRepository = com.ballade.hwaran.data.importer.book.BookImportRepository(database.libraryDao())
                            val bookSummary = if (isLocalMode) {
                                com.ballade.hwaran.data.importer.book.BookLocalMegaImport.execute(
                                    context = getApplication(),
                                    repository = bookRepository,
                                    parentUri = parentUri,
                                    workspace = workspace,
                                    isNsfw = isNsfwOverride ?: _isNsfwFilter.value,
                                    boxPurpose = boxPurpose,
                                    isCancelled = { _isCancelRequested.value },
                                    onProgress = { progress -> _megaImportProgress.value = progress }
                                )
                            } else {
                                com.ballade.hwaran.data.importer.book.BookExternalMegaImport.execute(
                                    context = getApplication(),
                                    repository = bookRepository,
                                    parentUri = parentUri,
                                    workspace = workspace,
                                    isNsfw = isNsfwOverride ?: _isNsfwFilter.value,
                                    boxPurpose = boxPurpose,
                                    isCancelled = { _isCancelRequested.value },
                                    onProgress = { progress -> _megaImportProgress.value = progress }
                                )
                            }
                            _megaImportSummary.value = MegaImportSummary(
                                total = bookSummary.total,
                                imported = bookSummary.imported,
                                skipped = bookSummary.skipped,
                                skippedFolders = bookSummary.skippedFolders,
                                isCancelled = bookSummary.isCancelled
                            )
                        }
                    }
                } else if (mediaMode == 0) {
                    val toonRepository = com.ballade.hwaran.data.importer.toon.ToonImportRepository(database.libraryDao())
                    val toonSummary = if (isLocalMode) {
                        com.ballade.hwaran.data.importer.toon.ToonLocalMegaImport.execute(
                            context = getApplication(),
                            repository = toonRepository,
                            parentUri = parentUri,
                            workspace = workspace,
                            isNsfw = isNsfwOverride ?: _isNsfwFilter.value,
                            boxPurpose = boxPurpose,
                            isCancelled = { _isCancelRequested.value },
                            onProgress = { progress -> _megaImportProgress.value = progress }
                        )
                    } else {
                        com.ballade.hwaran.data.importer.toon.ToonExternalMegaImport.execute(
                            context = getApplication(),
                            repository = toonRepository,
                            parentUri = parentUri,
                            workspace = workspace,
                            isNsfw = isNsfwOverride ?: _isNsfwFilter.value,
                            boxPurpose = boxPurpose,
                            isCancelled = { _isCancelRequested.value },
                            onProgress = { progress -> _megaImportProgress.value = progress }
                        )
                    }
                    _megaImportSummary.value = MegaImportSummary(
                        total = toonSummary.total,
                        imported = toonSummary.imported,
                        skipped = toonSummary.skipped,
                        skippedFolders = toonSummary.skippedFolders,
                        isCancelled = toonSummary.isCancelled
                    )
                } else if (mediaMode == 2) {
                    val parentDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), parentUri)
                    if (parentDoc != null) {
                        val mode = com.ballade.hwaran.data.importer.video.VideoImportUtils.detectImportMode(parentDoc)
                        if (mode == "SINGLE") {
                            _isMegaImporting.value = false
                            importFolder(
                                uri = parentUri,
                                boxPurposeOverride = boxPurposeOverride,
                                workspace = workspace,
                                isNsfwOverride = isNsfwOverride,
                                storageModeOverride = storageModeOverride
                            )
                            return@launch
                        }
                    }

                    // Video Mega Import — isolated pipeline
                    val videoRepository = com.ballade.hwaran.data.importer.video.VideoImportRepository(database.libraryDao())
                    val videoSummary = if (isLocalMode) {
                        com.ballade.hwaran.data.importer.video.VideoLocalMegaImport.execute(
                            context = getApplication(),
                            repository = videoRepository,
                            parentUri = parentUri,
                            workspace = workspace,
                            isNsfw = isNsfwOverride ?: _isNsfwFilter.value,
                            boxPurpose = boxPurpose,
                            isCancelled = { _isCancelRequested.value },
                            onProgress = { progress -> _megaImportProgress.value = progress }
                        )
                    } else {
                        com.ballade.hwaran.data.importer.video.VideoExternalMegaImport.execute(
                            context = getApplication(),
                            repository = videoRepository,
                            parentUri = parentUri,
                            workspace = workspace,
                            isNsfw = isNsfwOverride ?: _isNsfwFilter.value,
                            boxPurpose = boxPurpose,
                            isCancelled = { _isCancelRequested.value },
                            onProgress = { progress -> _megaImportProgress.value = progress }
                        )
                    }
                    _megaImportSummary.value = MegaImportSummary(
                        total = videoSummary.total,
                        imported = videoSummary.imported,
                        skipped = videoSummary.skipped,
                        skippedFolders = videoSummary.skippedFolders,
                        isCancelled = videoSummary.isCancelled
                    )
                } else {

                    // Legacy path for Book/Video
                    var totalCount = 0
                    var importedCount = 0
                    var skippedCount = 0
                    val skippedFolders = mutableListOf<String>()
                    var isCancelled = false

                    withContext(Dispatchers.IO) {
                        val parentDoc = DocumentFile.fromTreeUri(getApplication(), parentUri)
                        val allChildren = parentDoc?.listFiles() ?: emptyArray()
                        val candidates = allChildren.filter {
                            it.isDirectory && !(it.name?.startsWith(".") == true)
                        }.sortedBy { it.name?.lowercase() ?: "" }
                        totalCount = candidates.size

                        if (totalCount == 0) {
                            if (mediaMode == 4 || boxPurpose == "novel") {
                                val hasDirectNovels = allChildren.any { !it.isDirectory && com.ballade.hwaran.backend.novel.NovelParser.isNovelFile(it.name) }
                                if (hasDirectNovels) {
                                    _isMegaImporting.value = false
                                    importFolder(
                                        uri = parentUri,
                                        boxPurposeOverride = boxPurposeOverride,
                                        workspace = workspace,
                                        isNsfwOverride = isNsfwOverride,
                                        storageModeOverride = storageModeOverride,
                                        mediaModeOverride = mediaModeOverride
                                    )
                                    return@withContext
                                }
                            } else if (mediaMode == 1 || boxPurpose == "book") {
                                val hasDirectBooks = allChildren.any { !it.isDirectory && com.ballade.hwaran.backend.novel.NovelParser.isBookFile(it.name) }
                                if (hasDirectBooks) {
                                    _isMegaImporting.value = false
                                    importFolder(
                                        uri = parentUri,
                                        boxPurposeOverride = boxPurposeOverride,
                                        workspace = workspace,
                                        isNsfwOverride = isNsfwOverride,
                                        storageModeOverride = storageModeOverride,
                                        mediaModeOverride = mediaModeOverride
                                    )
                                    return@withContext
                                }
                            }
                            _megaImportSummary.value = MegaImportSummary(0, 0, 0, emptyList(), false)
                            return@withContext
                        }

                        for (index in candidates.indices) {
                            if (!isActive || _isCancelRequested.value) {
                                isCancelled = true
                                break
                            }

                            val child = candidates[index]
                            
                            val expectedPath = if (isLocalMode) {
                                val vaultBase = java.io.File(getApplication<Application>().filesDir, "manga_vault")
                                java.io.File(vaultBase, child.name ?: "").absolutePath
                            } else {
                                child.uri.toString()
                            }
                            
                            val existing = database.libraryDao().getRootMangaByUri(expectedPath)
                            if (existing != null) {
                                skippedCount++
                                skippedFolders.add("${child.name ?: "Unknown Folder"}: Already imported")
                                _megaImportProgress.value = (index + 1).toFloat() / totalCount
                                continue
                            }

                            val (isValid, reason) = if (boxPurpose == "novel" || mediaMode == 4) {
                                val childFiles = child.listFiles() ?: emptyArray()
                                val hasNovels = childFiles.any { !it.isDirectory && com.ballade.hwaran.backend.novel.NovelParser.isNovelFile(it.name) } ||
                                        childFiles.any { it.isDirectory && !com.ballade.hwaran.core.metadata.ZineMetadataExtractor.isInternalOrAuxiliary(it.name) }
                                if (hasNovels) true to null else false to "No novel or text files found"
                            } else if (boxPurpose == "book" || mediaMode == 1) {
                                val childFiles = child.listFiles() ?: emptyArray()
                                val hasBooks = childFiles.any { !it.isDirectory && com.ballade.hwaran.backend.novel.NovelParser.isBookFile(it.name) } ||
                                        childFiles.any { it.isDirectory && !com.ballade.hwaran.core.metadata.ZineMetadataExtractor.isInternalOrAuxiliary(it.name) }
                                if (hasBooks) true to null else false to "No book or PDF/EPUB/web-book files found"
                            } else {
                                com.ballade.hwaran.data.importer.toon.ToonImportUtils.isToonFolderValid(child)
                            }
                            if (isValid) {
                                try {
                                    val importedId = repository.scanImportedFolder(
                                        rootUri = child.uri,
                                        isLocalMode = isLocalMode,
                                        isFile = false,
                                        isNsfwInput = isNsfwOverride ?: _isNsfwFilter.value,
                                        boxPurposeInput = boxPurpose,
                                        workspace = workspace,
                                        documentFileOverride = child,
                                        isCancelled = { _isCancelRequested.value }
                                    )
                                    if (importedId != null) {
                                        importedCount++
                                    } else {
                                        skippedCount++
                                        skippedFolders.add("${child.name ?: "Unknown Folder"}: Scan failed")
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    skippedCount++
                                    skippedFolders.add("${child.name ?: "Unknown Folder"}: ${e.message ?: "Unknown error"}")
                                }
                            } else {
                                skippedCount++
                                skippedFolders.add("${child.name ?: "Unknown Folder"}: ${reason ?: "Invalid structure"}")
                            }

                            _megaImportProgress.value = (index + 1).toFloat() / totalCount
                        }
                    }
                    _megaImportSummary.value = MegaImportSummary(
                        total = totalCount,
                        imported = importedCount,
                        skipped = skippedCount,
                        skippedFolders = skippedFolders,
                        isCancelled = isCancelled || _isCancelRequested.value
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isMegaImporting.value = false
                _megaImportProgress.value = 0f
                _isCancelRequested.value = false
                _isCancelArmed.value = false
            }
        }
    }

    fun importFolder(
        uri: Uri, 
        isFile: Boolean = false, 
        boxPurposeOverride: String? = null, 
        workspace: String? = null, 
        isNsfwOverride: Boolean? = null, 
        storageModeOverride: Int? = null, 
        mediaModeOverride: Int? = null,
        onImported: (Long) -> Unit = {}
    ) {
        if (isImportingGlobal.value) return
        _isImporting.value = true
        _isCancelRequested.value = false
        _isCancelArmed.value = false

        importJob?.cancel()
        importJob = viewModelScope.launch {
            var importedId: Long? = null
            try {
                val globalSettings = com.ballade.hwaran.core.datastore.GlobalSettings(getApplication())
                val isLocalMode = (storageModeOverride ?: globalSettings.storageModeFlow.first()) == 0
                val mediaMode = mediaModeOverride ?: globalSettings.mediaModeFlow.first()
                val videoLayoutMode = globalSettings.videoLayoutModeFlow.first()
                val boxPurpose = boxPurposeOverride ?: if (videoLayoutMode == 1) "channel" else "series"

                val expectedPath = withContext(Dispatchers.IO) {
                    if (isLocalMode) {
                        val folderDoc = if (isFile) DocumentFile.fromSingleUri(getApplication(), uri) else DocumentFile.fromTreeUri(getApplication(), uri)
                        val folderName = folderDoc?.name ?: "Unknown"
                        val finalFolderName = if (isFile) folderName.substringBeforeLast(".") else folderName
                        val vaultBase = java.io.File(getApplication<Application>().filesDir, "manga_vault")
                        java.io.File(vaultBase, finalFolderName).absolutePath
                    } else {
                        uri.toString()
                    }
                }

                val existing = database.libraryDao().getRootMangaByUri(expectedPath)
                if (existing != null) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        "${existing.title} is already imported",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    getApplication<Application>().contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }

                if (mediaMode == 0 && !isFile) {
                    val toonRepository = com.ballade.hwaran.data.importer.toon.ToonImportRepository(database.libraryDao())
                    if (isLocalMode) {
                        importedId = com.ballade.hwaran.data.importer.toon.ToonLocalSingleImport.execute(
                            context = getApplication(),
                            repository = toonRepository,
                            uri = uri,
                            workspace = workspace,
                            isNsfw = isNsfwOverride ?: _isNsfwFilter.value,
                            boxPurpose = boxPurpose,
                            isCancelled = { _isCancelRequested.value },
                            onProgress = { progress -> _importProgress.value = progress }
                        )
                    } else {
                        importedId = com.ballade.hwaran.data.importer.toon.ToonExternalSingleImport.execute(
                            context = getApplication(),
                            repository = toonRepository,
                            uri = uri,
                            workspace = workspace,
                            isNsfw = isNsfwOverride ?: _isNsfwFilter.value,
                            boxPurpose = boxPurpose,
                            isCancelled = { _isCancelRequested.value },
                            onProgress = { progress -> _importProgress.value = progress }
                        )
                    }
                } else if (mediaMode == 1) {
                    val bookRepository = com.ballade.hwaran.data.importer.book.BookImportRepository(database.libraryDao())
                    if (isFile) {
                        // Single-file URI from OpenDocument picker — must use fromSingleUri,
                        // NOT fromTreeUri (which always returns null for file URIs).
                        val bookDoc = androidx.documentfile.provider.DocumentFile.fromSingleUri(getApplication(), uri)
                        if (bookDoc != null) {
                            if (isLocalMode) {
                                importedId = com.ballade.hwaran.data.importer.book.BookLocalSingleImport.executeSinglePdf(
                                    context = getApplication(),
                                    repository = bookRepository,
                                    pdfDoc = bookDoc,
                                    workspace = workspace,
                                    isNsfw = isNsfwOverride ?: _isNsfwFilter.value,
                                    boxPurpose = boxPurpose,
                                    importMode = "Single Import",
                                    isCancelled = { _isCancelRequested.value }
                                )
                            } else {
                                importedId = com.ballade.hwaran.data.importer.book.BookExternalSingleImport.executeSinglePdf(
                                    context = getApplication(),
                                    repository = bookRepository,
                                    pdfDoc = bookDoc,
                                    workspace = workspace,
                                    isNsfw = isNsfwOverride ?: _isNsfwFilter.value,
                                    boxPurpose = boxPurpose,
                                    importMode = "Single Import",
                                    isCancelled = { _isCancelRequested.value }
                                )
                            }
                        }
                    } else {
                        if (isLocalMode) {
                            importedId = com.ballade.hwaran.data.importer.book.BookLocalSingleImport.execute(
                                context = getApplication(),
                                repository = bookRepository,
                                uri = uri,
                                workspace = workspace,
                                isNsfw = isNsfwOverride ?: _isNsfwFilter.value,
                                boxPurpose = boxPurpose ?: "book",
                                isCancelled = { _isCancelRequested.value },
                                onProgress = { progress -> _importProgress.value = progress }
                            )
                        } else {
                            importedId = com.ballade.hwaran.data.importer.book.BookExternalSingleImport.execute(
                                context = getApplication(),
                                repository = bookRepository,
                                uri = uri,
                                workspace = workspace,
                                isNsfw = isNsfwOverride ?: _isNsfwFilter.value,
                                boxPurpose = boxPurpose ?: "book",
                                isCancelled = { _isCancelRequested.value },
                                onProgress = { progress -> _importProgress.value = progress }
                            )
                        }
                    }
                } else if (mediaMode == 2) {
                    val videoRepository = com.ballade.hwaran.data.importer.video.VideoImportRepository(database.libraryDao())
                    if (isLocalMode) {
                        importedId = com.ballade.hwaran.data.importer.video.VideoLocalSingleImport.execute(
                            context = getApplication(),
                            repository = videoRepository,
                            uri = uri,
                            workspace = workspace,
                            isNsfw = isNsfwOverride ?: _isNsfwFilter.value,
                            boxPurpose = boxPurpose,
                            isCancelled = { _isCancelRequested.value },
                            onProgress = { progress -> _importProgress.value = progress }
                        )
                    } else {
                        importedId = com.ballade.hwaran.data.importer.video.VideoExternalSingleImport.execute(
                            context = getApplication(),
                            repository = videoRepository,
                            uri = uri,
                            workspace = workspace,
                            isNsfw = isNsfwOverride ?: _isNsfwFilter.value,
                            boxPurpose = boxPurpose,
                            isCancelled = { _isCancelRequested.value },
                            onProgress = { progress -> _importProgress.value = progress }
                        )
                    }
                } else if (mediaMode == 3 && !isFile) {
                    val musicRepository = com.ballade.hwaran.data.importer.music.MusicImportRepository(database.libraryDao())
                    val folderDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), uri)
                    if (folderDoc != null) {
                        importedId = com.ballade.hwaran.data.importer.music.MusicExternalSingleImport.execute(
                            context = getApplication(),
                            repository = musicRepository,
                            folderDoc = folderDoc,
                            workspace = workspace,
                            isCancelled = { _isCancelRequested.value },
                            onProgress = { progress -> _importProgress.value = progress }
                        )
                    }
                } else {
                    importedId = repository.scanImportedFolder(
                        rootUri = uri,
                        isLocalMode = isLocalMode,
                        isFile = isFile,
                        isNsfwInput = isNsfwOverride ?: _isNsfwFilter.value,
                        boxPurposeInput = boxPurpose,
                        workspace = workspace,
                        isCancelled = { _isCancelRequested.value }
                    ) { progress ->
                        _importProgress.value = progress
                    }
                }
                
                importedId?.let { onImported(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isImporting.value = false
                _importProgress.value = 0
                _isCancelRequested.value = false
                _isCancelArmed.value = false
            }
        }
    }

    fun updateMangaLockState(manga: MangaEntity, isLocked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            database.libraryDao().insertManga(manga.copy(isLocked = isLocked))
            val children = database.mediaDao().getChildrenForMangaList(manga.id)
            for (child in children) {
                database.mediaDao().insertManga(child.copy(isLocked = isLocked))
            }
        }
    }

    fun updateMangaMetadata(mangaId: Long, title: String, artist: String, coverPath: String) {
        viewModelScope.launch {
            val manga = database.libraryDao().getMangaById(mangaId)
            if (manga != null) {
                // Fix: Persist URI permission for the new cover image if it's a content URI
                if (coverPath.startsWith("content://")) {
                    try {
                        val uri = Uri.parse(coverPath)
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        getApplication<Application>().contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                database.libraryDao().insertManga(manga.copy(
                    title = title,
                    description = artist,
                    coverPath = coverPath
                ))
            }
        }
    }

    fun updateMangaGenre(mangaId: Long, genre: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val manga = database.libraryDao().getMangaById(mangaId)
            if (manga != null) {
                database.libraryDao().insertManga(manga.copy(genre = genre))
            }
        }
    }

    fun updateChapterGenre(chapterId: Long, genre: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val chapter = database.trackDao().getChapterById(chapterId)
            if (chapter != null) {
                database.trackDao().insertChapter(chapter.copy(genre = genre))
            }
        }
    }

    fun updateTracker(mangaId: Long, title: String? = null, page: Int? = null) {
        viewModelScope.launch {
            val manga = database.libraryDao().getMangaById(mangaId)
            if (manga != null) {
                database.libraryDao().insertManga(manga.copy(
                    lastReadTitle = title ?: manga.lastReadTitle,
                    lastReadPage = page ?: manga.lastReadPage
                ))
            }
        }
    }

    fun deletePlaylist(mangaId: Long) {
        viewModelScope.launch {
            val manga = database.libraryDao().getMangaById(mangaId)
            if (manga != null) {
                com.ballade.hwaran.core.util.HistoryTracker.logEvent("DELETE", manga.title, "Playlist")
                // Delete associated chapters first
                database.trackDao().deleteChaptersByMangaId(mangaId)
                // Delete the playlist itself
                database.libraryDao().deleteManga(manga)
            }
        }
    }

    fun createPlaylist(title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newPlaylist = MangaEntity(
                title = title,
                description = "Custom Playlist",
                thoughts = "No thoughts added.",
                coverPath = "",
                isNsfw = false,
                parentUri = "custom_playlist_${System.currentTimeMillis()}",
                lastModified = System.currentTimeMillis(),
                contentType = 3
            )
            database.libraryDao().insertManga(newPlaylist)
        }
    }

    private fun ensurePermanentSong(song: ChapterEntity, targetMangaId: Long): ChapterEntity {
        var permanentUri = song.folderUri
        var permanentArtUri = song.thumbnailUri

        if (song.folderUri.startsWith("content://")) {
            val contentUri = Uri.parse(song.folderUri)
            var canPersist = false
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                getApplication<Application>().contentResolver.takePersistableUriPermission(contentUri, takeFlags)
                canPersist = true
            } catch (e: Exception) {
                canPersist = false
            }

            if (!canPersist) {
                try {
                    val targetDir = File(getApplication<Application>().filesDir, "media/saved_tracks").apply { mkdirs() }
                    val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40)
                    val targetFile = File(targetDir, "trk_${System.currentTimeMillis()}_${cleanTitle}.mp3")
                    getApplication<Application>().contentResolver.openInputStream(contentUri)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (targetFile.exists() && targetFile.length() > 0) {
                        permanentUri = Uri.fromFile(targetFile).toString()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LibraryViewModel", "Failed to copy external audio track", e)
                }
            }
        }

        if (song.thumbnailUri != null && song.thumbnailUri.startsWith("file://") && song.thumbnailUri.contains("/cache/")) {
            try {
                val artCacheFile = File(Uri.parse(song.thumbnailUri).path ?: "")
                if (artCacheFile.exists()) {
                    val artDir = File(getApplication<Application>().filesDir, "media/saved_art").apply { mkdirs() }
                    val targetArt = File(artDir, "art_${System.currentTimeMillis()}_${artCacheFile.name}")
                    artCacheFile.copyTo(targetArt, overwrite = true)
                    permanentArtUri = Uri.fromFile(targetArt).toString()
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Failed to persist art", e)
            }
        }

        return song.copy(
            id = 0,
            mangaId = targetMangaId,
            folderUri = permanentUri,
            thumbnailUri = permanentArtUri
        )
    }

    fun addSongToPlaylist(playlistId: Long, song: ChapterEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val existingChapters = database.trackDao().getChaptersForMangaList(playlistId)
            val duplicate = existingChapters.find { 
                it.folderUri == song.folderUri || 
                (it.title.isNotBlank() && it.title == song.title && it.artist == song.artist)
            }
            if (duplicate == null) {
                val newSong = ensurePermanentSong(song, playlistId)
                database.trackDao().insertChapter(newSong)
            }
        }
    }

    fun toggleFavorite(song: ChapterEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Find "Favorites" playlist
            var favPlaylist = database.libraryDao().getAllMangaList().find { 
                it.title.equals("Favorites", ignoreCase = true) && it.contentType == 3 
            }
            
            // Create it if it doesn't exist
            if (favPlaylist == null) {
                val newFav = MangaEntity(
                    title = "Favorites",
                    description = "Your favorite tracks",
                    thoughts = "No thoughts added.",
                    coverPath = "android.resource://com.ballade.hwaran/drawable/fav",
                    isNsfw = false,
                    parentUri = "favorites_${System.currentTimeMillis()}",
                    lastModified = System.currentTimeMillis(),
                    contentType = 3
                )
                val newId = database.libraryDao().insertManga(newFav)
                favPlaylist = database.libraryDao().getMangaById(newId)
            }
            
            // Toggle song in the favorites playlist
            if (favPlaylist != null) {
                val existingChapters = database.trackDao().getChaptersForMangaList(favPlaylist.id)
                val duplicate = existingChapters.find { 
                    it.folderUri == song.folderUri || 
                    (it.title.isNotBlank() && it.title == song.title && it.artist == song.artist)
                }
                if (duplicate != null) {
                    if (duplicate.folderUri.contains("media/saved_tracks/")) {
                        try {
                            val f = File(Uri.parse(duplicate.folderUri).path ?: "")
                            if (f.exists()) f.delete()
                        } catch (e: Exception) {}
                    }
                    if (duplicate.thumbnailUri?.contains("media/saved_art/") == true) {
                        try {
                            val f = File(Uri.parse(duplicate.thumbnailUri).path ?: "")
                            if (f.exists()) f.delete()
                        } catch (e: Exception) {}
                    }
                    database.trackDao().deleteChapter(duplicate)
                } else {
                    val permanentSong = ensurePermanentSong(song, favPlaylist.id)
                    database.trackDao().insertChapter(permanentSong)
                }
            }
        }
    }

    fun deleteChapterOnlyFromDb(chapterId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val chapter = database.trackDao().getChapterById(chapterId)
            if (chapter != null) {
                com.ballade.hwaran.core.util.HistoryTracker.logEvent("DELETE", chapter.title, "Chapter (DB only)")
                database.trackDao().deleteChapter(chapter)
            }
        }
    }

    fun deleteChaptersOnlyFromDb(chapterIds: List<Long>) {
        if (chapterIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            chapterIds.forEach { chapterId ->
                val chapter = database.trackDao().getChapterById(chapterId)
                if (chapter != null) {
                    com.ballade.hwaran.core.util.HistoryTracker.logEvent("DELETE", chapter.title, "Chapter (DB only)")
                    database.trackDao().deleteChapter(chapter)
                }
            }
        }
    }

    fun deleteSelectedChapters(chapterIds: List<Long>, deleteFromDisk: Boolean = false) {
        if (chapterIds.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chapterIds.forEach { id ->
                    val chapter = database.trackDao().getChapterById(id)
                    if (chapter != null) {
                        if (deleteFromDisk) {
                            com.ballade.hwaran.core.util.HistoryTracker.logEvent("DELETE", chapter.title, "Chapter (Physical)")
                            try {
                                if (chapter.folderUri.startsWith("content://")) {
                                    val uri = Uri.parse(chapter.folderUri)
                                    val deleted = DocumentFile.fromSingleUri(getApplication(), uri)?.delete()
                                        ?: DocumentFile.fromTreeUri(getApplication(), uri)?.delete()
                                    if (deleted != true) {
                                        try {
                                            getApplication<Application>().contentResolver.delete(uri, null, null)
                                        } catch (se: SecurityException) {
                                            se.printStackTrace()
                                        }
                                    }
                                } else {
                                    val path = chapter.folderUri
                                    val file = java.io.File(path)
                                    when {
                                        file.isDirectory -> file.deleteRecursively()
                                        file.isFile -> file.delete()
                                        else -> {
                                            val parent = file.parentFile
                                            if (parent != null && parent.exists()) parent.deleteRecursively()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            com.ballade.hwaran.core.util.HistoryTracker.logEvent("DELETE", chapter.title, "Chapter (DB only)")
                        }
                        database.trackDao().deleteChapter(chapter)
                    }
                }
            }
        }
    }

    fun deleteWorkspace(mediaMode: Int, workspaceName: String) {
        deleteWorkspace(mediaMode, -1, workspaceName)
    }

    fun deleteWorkspace(mediaMode: Int, videoLayoutMode: Int, workspaceName: String) {
        if (workspaceName == "I Love It") return
        viewModelScope.launch(Dispatchers.IO) {
            val globalSettings = com.ballade.hwaran.core.datastore.GlobalSettings(getApplication())
            globalSettings.removeWorkspace(mediaMode, workspaceName)

            val items = database.libraryDao().getMangaListForMove(mediaMode, videoLayoutMode, workspaceName)
            items.forEach { m ->
                com.ballade.hwaran.core.util.HistoryTracker.logEvent("IMPORT", "Merged to I Love It", m.title)
                database.libraryDao().updateWorkspaceForMangaTree(m.id, "I Love It")
            }
        }
    }
    fun importMusicFolder(uri: Uri, workspace: String?) {
        if (isImportingGlobal.value) return
        
        importJob?.cancel()
        importJob = viewModelScope.launch {
            _isCancelRequested.value = false
            _isCancelArmed.value = false
            _isImporting.value = true
            _importProgress.value = 0
            
            try {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    getApplication<Application>().contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }

                val parentDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), uri)
                if (parentDoc != null) {
                    val structure = com.ballade.hwaran.data.importer.music.MusicImportUtils.detectStructure(parentDoc)
                    val repository = com.ballade.hwaran.data.importer.music.MusicImportRepository(database.libraryDao())

                    if (structure == "SINGLE") {
                        val albumId = com.ballade.hwaran.data.importer.music.MusicExternalSingleImport.execute(
                            context = getApplication(),
                            repository = repository,
                            folderDoc = parentDoc,
                            workspace = workspace,
                            isCancelled = { _isCancelRequested.value },
                            onProgress = { progress -> _importProgress.value = progress }
                        )
                        if (albumId != null) {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(getApplication(), "Imported ${parentDoc.name ?: "1 album"}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(getApplication(), "Failed or skipped import", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        val summary = com.ballade.hwaran.data.importer.music.MusicExternalMegaImport.execute(
                            context = getApplication(),
                            repository = repository,
                            parentDoc = parentDoc,
                            workspace = workspace,
                            isCancelled = { _isCancelRequested.value },
                            onProgress = { progress -> _importProgress.value = (progress * 100).toInt() }
                        )
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                getApplication(),
                                "Imported ${summary.imported} • Skipped ${summary.skipped}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Import error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isImporting.value = false
                _importProgress.value = 0
                _isCancelRequested.value = false
                _isCancelArmed.value = false
            }
        }
    }

    fun getAllDistinctWorkspaces(): kotlinx.coroutines.flow.Flow<List<String>> {
        return database.libraryDao().getAllDistinctWorkspaces()
    }

    fun moveEntireWorkspace(mediaMode: Int, videoLayoutMode: Int, oldWorkspace: String, newWorkspace: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.libraryDao().updateEntireWorkspace(mediaMode, videoLayoutMode, oldWorkspace, newWorkspace)
            com.ballade.hwaran.core.util.HistoryTracker.logEvent("IMPORT", "Moved Workspace", "$oldWorkspace -> $newWorkspace")
            
            // Also need to rename the workspace in GlobalSettings so UI knows about it if it's the active one
            val globalSettings = com.ballade.hwaran.core.datastore.GlobalSettings(getApplication())
            globalSettings.renameWorkspaceIfActive(mediaMode, oldWorkspace, newWorkspace)
        }
    }

    fun moveItemsToDifferentWorkspace(rootIds: List<Long>, oldWorkspace: String, newWorkspace: String) {
        viewModelScope.launch(Dispatchers.IO) {
            for (rootId in rootIds) {
                // This updates the root manga and all its children/seasons/episodes
                database.libraryDao().updateWorkspaceForMangaTree(rootId, newWorkspace)
            }
            com.ballade.hwaran.core.util.HistoryTracker.logEvent("IMPORT", "Moved ${rootIds.size} items", "$oldWorkspace -> $newWorkspace")
        }
    }

    fun renameWorkspace(mediaMode: Int, videoLayoutMode: Int, oldWorkspace: String, newWorkspace: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanOld = oldWorkspace.trim()
            val cleanNew = newWorkspace.trim()
            if (cleanOld.isBlank() || cleanNew.isBlank() || cleanOld == "I Love It" || cleanNew == "I Love It") return@launch

            database.libraryDao().updateEntireWorkspace(mediaMode, videoLayoutMode, cleanOld, cleanNew)

            val globalSettings = com.ballade.hwaran.core.datastore.GlobalSettings(getApplication())
            globalSettings.addWorkspace(mediaMode, cleanNew)
            globalSettings.removeWorkspace(mediaMode, cleanOld)
            globalSettings.renameWorkspaceIfActive(mediaMode, cleanOld, cleanNew)
            
            com.ballade.hwaran.core.util.HistoryTracker.logEvent("IMPORT", "Renamed Workspace", "$cleanOld -> $cleanNew")
        }
    }
}
