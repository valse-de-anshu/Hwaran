package com.ballade.hwaran.data.sync

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.backend.novel.NovelParser
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.metadata.ZineMetadataExtractor
import com.ballade.hwaran.core.util.HistoryTracker
import com.ballade.hwaran.core.util.StorageResolver
import com.ballade.hwaran.data.importer.book.BookExternalSingleImport
import com.ballade.hwaran.data.importer.book.BookImportRepository
import com.ballade.hwaran.data.importer.book.NovelExternalSingleImport
import com.ballade.hwaran.data.importer.music.MusicExternalSingleImport
import com.ballade.hwaran.data.importer.music.MusicImportRepository
import com.ballade.hwaran.data.importer.music.MusicImportUtils
import com.ballade.hwaran.data.importer.toon.ToonExternalSingleImport
import com.ballade.hwaran.data.importer.toon.ToonImportRepository
import com.ballade.hwaran.data.importer.toon.ToonImportUtils
import com.ballade.hwaran.data.importer.video.VideoExternalSingleImport
import com.ballade.hwaran.data.importer.video.VideoImportRepository
import com.ballade.hwaran.data.importer.video.VideoImportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class SyncSummary(
    val discoveredCount: Int,
    val updatedCount: Int
)

data class DiscoveredMediaUnit(
    val folder: File,
    val mode: Int, // 0: Manga, 1: Book, 2: Video, 3: Music, 4: Novel
    val purpose: String,
    val singleFile: File? = null
)

object LibrarySyncEngine {

    private const val TAG = "LibrarySyncEngine"
    private val syncMutex = Mutex()

    private val textExtensions = setOf("txt", "md", "markdown", "html", "htm", "xhtml", "epub")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
    private val bookExtensions = setOf("pdf", "epub", "mobi", "azw3")

    private val organizationalFolderNames = setOf(
        "download", "downloads", "zine", "zine scraper", "zine_scraper",
        "vacuum", "quick grab", "youtube", "music", "channel", "channels",
        "series", "book", "books", "manhua", "manga", "novel", "novels",
        "video", "videos", "document", "documents", "files", "media"
    )

    /**
     * Identifies whether a directory is an organizational grouping folder
     * (e.g. "novel", "music", "channel", "Zine", "Download", "test my app")
     * rather than an actual media leaf unit. Organizational folders must be
     * recursed into and must never be registered as single media items.
     */
    fun isOrganizationalFolder(dir: File): Boolean {
        val lower = dir.name.lowercase().trim()
        if (lower in organizationalFolderNames) return true
        if (lower.startsWith("test my app") || lower.startsWith("test_my_app") || lower.startsWith("new folder")) return true
        if (ZineMetadataExtractor.isRootStorageDir(dir)) return true

        val subdirs = dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: emptyArray()
        val hasCategorySubdirs = subdirs.count { sub ->
            sub.name.lowercase().trim() in organizationalFolderNames
        } >= 2
        return hasCategorySubdirs
    }

    /**
     * Resyncs a single media entity: checks for newly added chapters/videos/tracks/books,
     * updates metadata (like count, view count, description, tags), and refreshes lastModified.
     * Returns the count of newly discovered items.
     */
    suspend fun syncSingleMedia(
        context: Context,
        manga: MangaEntity,
        database: AppDatabase
    ): Int = withContext(Dispatchers.IO) {
        val parentUriStr = manga.parentUri ?: return@withContext 0
        val uri = try {
            Uri.parse(parentUriStr)
        } catch (_: Exception) {
            return@withContext 0
        }

        // Never resync broad root storage or organizational directories as a single media item
        val directFile = StorageResolver.resolveToFile(context, uri)
        if (directFile != null && (ZineMetadataExtractor.isRootStorageDir(directFile) || isOrganizationalFolder(directFile))) {
            return@withContext 0
        }

        val doc = StorageResolver.resolveDocument(context, uri) ?: return@withContext 0
        if (!doc.exists()) return@withContext 0

        val initialChapterCount = database.trackDao().getChaptersForMangaList(manga.id).size

        when (manga.contentType) {
            0 -> {
                val toonRepo = ToonImportRepository(database.libraryDao())
                ToonExternalSingleImport.execute(
                    context = context,
                    repository = toonRepo,
                    uri = uri,
                    workspace = manga.workspace,
                    isNsfw = manga.isNsfw,
                    boxPurpose = manga.boxPurpose,
                    importMode = "Auto-Sync",
                    isCancelled = { false },
                    onProgress = {}
                )
            }
            1 -> {
                val bookRepo = BookImportRepository(database.libraryDao())
                BookExternalSingleImport.execute(
                    context = context,
                    repository = bookRepo,
                    uri = uri,
                    workspace = manga.workspace,
                    isNsfw = manga.isNsfw,
                    boxPurpose = manga.boxPurpose ?: "book",
                    importMode = "Auto-Sync",
                    isCancelled = { false },
                    onProgress = {}
                )
            }
            2 -> {
                val videoRepo = VideoImportRepository(database.libraryDao())
                VideoExternalSingleImport.execute(
                    context = context,
                    repository = videoRepo,
                    uri = uri,
                    workspace = manga.workspace,
                    isNsfw = manga.isNsfw,
                    boxPurpose = manga.boxPurpose,
                    importMode = "Auto-Sync",
                    isCancelled = { false },
                    onProgress = {}
                )
            }
            3 -> {
                val musicRepo = MusicImportRepository(database.libraryDao())
                MusicExternalSingleImport.execute(
                    context = context,
                    repository = musicRepo,
                    folderDoc = doc,
                    workspace = manga.workspace,
                    isCancelled = { false },
                    onProgress = {}
                )
            }
            4 -> {
                val bookRepo = BookImportRepository(database.libraryDao())
                NovelExternalSingleImport.execute(
                    context = context,
                    repository = bookRepo,
                    uri = uri,
                    workspace = manga.workspace,
                    isNsfw = manga.isNsfw,
                    boxPurpose = "novel",
                    importMode = "Auto-Sync",
                    isCancelled = { false },
                    onProgress = {}
                )
            }
        }

        val finalChapterCount = database.trackDao().getChaptersForMangaList(manga.id).size
        val newChapters = (finalChapterCount - initialChapterCount).coerceAtLeast(0)
        return@withContext newChapters
    }

    /**
     * Full Library Sync & Deep Traversal:
     * 1. Discovers and cleans up erroneous organizational containers.
     * 2. Deeply traverses target directories (Download/Zine, Download/Zine Scraper, Zine, Download, Movies, Music, Documents)
     *    like roots in the earth, discovering nested media leaf units without strict folder hierarchy limits.
     * 3. Registers newly discovered media in-place (external mode) with proper classification and cover art.
     * 4. Updates existing media chapter counts and metadata.
     */
    suspend fun syncAll(
        context: Context,
        database: AppDatabase,
        onProgress: ((String) -> Unit)? = null
    ): SyncSummary = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            var updatedCount = 0
            var discoveredCount = 0

            Log.d(TAG, "Starting Library Deep Sync...")

            val existingMedia = database.libraryDao().getAllMangaList()
            val existingUris = HashSet<String>()

            // 1. Clean up erroneous container entries so child media items can be imported
            for (manga in existingMedia) {
                val directFile = manga.parentUri?.let { StorageResolver.resolveToFile(context, Uri.parse(it)) }
                val isContainer = directFile != null && isOrganizationalFolder(directFile)
                if (isContainer) {
                    try {
                        database.mediaDao().deleteManga(manga)
                        database.trackDao().deleteChaptersByMangaId(manga.id)
                        Log.d(TAG, "Removed invalid organizational container: ${manga.title} (${manga.parentUri})")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    continue
                }

                manga.parentUri?.let { existingUris.add(it) }
                directFile?.absolutePath?.let { existingUris.add(it) }
            }

            // 2. Discover new media units across candidate storage roots
            onProgress?.invoke("Scanning deep folders for new media…")
            val candidateRoots = mutableListOf<File>()
            val extStorage = Environment.getExternalStorageDirectory()

            listOf(
                File(extStorage, "Download/Zine"),
                File(extStorage, "Download/Zine Scraper"),
                File(extStorage, "Download/zine_scraper"),
                File(extStorage, "Zine"),
                File(extStorage, "Download"),
                File(extStorage, "Movies"),
                File(extStorage, "Music"),
                File(extStorage, "Documents")
            ).forEach { dir ->
                if (dir.exists() && dir.isDirectory && dir.canRead()) {
                    candidateRoots.add(dir)
                }
            }

            val discoveredItems = mutableListOf<DiscoveredMediaUnit>()
            val visitedDirs = HashSet<String>()

            for (root in candidateRoots) {
                discoveredItems.addAll(scanDeepMedia(root, visitedDirs, maxDepth = 15))
            }

            Log.d(TAG, "Discovered ${discoveredItems.size} candidate media leaf units")

            val toonRepo = ToonImportRepository(database.libraryDao())
            val bookRepo = BookImportRepository(database.libraryDao())
            val videoRepo = VideoImportRepository(database.libraryDao())
            val musicRepo = MusicImportRepository(database.libraryDao())

            // 3. Register newly discovered items
            for (item in discoveredItems) {
                val folderPath = item.folder.absolutePath
                val folderUriStr = Uri.fromFile(item.folder).toString()
                val targetUriStr = if (item.singleFile != null) Uri.fromFile(item.singleFile).toString() else folderUriStr
                val targetPath = item.singleFile?.absolutePath ?: folderPath

                if (targetPath in existingUris || targetUriStr in existingUris) {
                    continue
                }

                // Check if already in DB
                val inDb = database.libraryDao().getRootMangaByUri(targetUriStr) != null ||
                        database.libraryDao().getRootMangaByUri(targetPath) != null
                if (inDb) {
                    existingUris.add(targetPath)
                    existingUris.add(targetUriStr)
                    continue
                }

                val displayName = item.singleFile?.name?.substringBeforeLast(".") ?: item.folder.name
                onProgress?.invoke("Importing $displayName…")
                Log.d(TAG, "Importing: $displayName (mode=${item.mode}, purpose=${item.purpose})")

                try {
                    val importedId: Long? = when (item.mode) {
                        0 -> {
                            ToonExternalSingleImport.execute(
                                context = context,
                                repository = toonRepo,
                                uri = Uri.fromFile(item.folder),
                                workspace = null,
                                isNsfw = false,
                                boxPurpose = item.purpose,
                                importMode = "Auto-Discovered",
                                isCancelled = { false },
                                onProgress = {}
                            )
                        }
                        1 -> {
                            if (item.singleFile != null) {
                                val pdfDoc = DocumentFile.fromFile(item.singleFile)
                                BookExternalSingleImport.executeSinglePdf(
                                    context = context,
                                    repository = bookRepo,
                                    pdfDoc = pdfDoc,
                                    workspace = null,
                                    isNsfw = false,
                                    boxPurpose = item.purpose,
                                    importMode = "Auto-Discovered",
                                    isCancelled = { false }
                                )
                            } else {
                                BookExternalSingleImport.execute(
                                    context = context,
                                    repository = bookRepo,
                                    uri = Uri.fromFile(item.folder),
                                    workspace = null,
                                    isNsfw = false,
                                    boxPurpose = item.purpose,
                                    importMode = "Auto-Discovered",
                                    isCancelled = { false },
                                    onProgress = {}
                                )
                            }
                        }
                        2 -> {
                            VideoExternalSingleImport.execute(
                                context = context,
                                repository = videoRepo,
                                uri = Uri.fromFile(item.folder),
                                workspace = null,
                                isNsfw = false,
                                boxPurpose = item.purpose,
                                importMode = "Auto-Discovered",
                                isCancelled = { false },
                                onProgress = {}
                            )
                        }
                        3 -> {
                            val doc = DocumentFile.fromFile(item.folder)
                            MusicExternalSingleImport.execute(
                                context = context,
                                repository = musicRepo,
                                folderDoc = doc,
                                workspace = null,
                                isCancelled = { false },
                                onProgress = {}
                            )
                        }
                        4 -> {
                            NovelExternalSingleImport.execute(
                                context = context,
                                repository = bookRepo,
                                uri = Uri.fromFile(item.folder),
                                workspace = null,
                                isNsfw = false,
                                boxPurpose = "novel",
                                importMode = "Auto-Discovered",
                                isCancelled = { false },
                                onProgress = {}
                            )
                        }
                        else -> null
                    }

                    if (importedId != null) {
                        discoveredCount++
                        existingUris.add(targetPath)
                        existingUris.add(targetUriStr)
                        Log.d(TAG, "Successfully imported $displayName as id $importedId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to import $displayName", e)
                }
            }

            // 4. Update existing valid media items
            onProgress?.invoke("Updating existing library…")
            val refreshedExisting = database.libraryDao().getAllMangaList()
            for (manga in refreshedExisting) {
                try {
                    val newItems = syncSingleMedia(context, manga, database)
                    if (newItems > 0) {
                        updatedCount++
                        Log.d(TAG, "Updated ${manga.title} with $newItems new items")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            HistoryTracker.logEvent(
                "AUTO_SYNC",
                "Library Deep Sync",
                "Discovered: $discoveredCount new items, Updated: $updatedCount existing items"
            )

            Log.d(TAG, "Library Deep Sync finished. Discovered: $discoveredCount, Updated: $updatedCount")
            SyncSummary(discoveredCount, updatedCount)
        }
    }

    /**
     * Recursively traverses candidate directories like deep roots to identify media leaf units.
     * Skips hidden directories (starting with '.') and system folders.
     */
    private fun scanDeepMedia(
        root: File,
        visitedDirs: MutableSet<String>,
        maxDepth: Int = 15
    ): List<DiscoveredMediaUnit> {
        val result = mutableListOf<DiscoveredMediaUnit>()

        fun walk(dir: File, depth: Int) {
            if (depth > maxDepth || !dir.exists() || !dir.isDirectory) return
            val canonical = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
            if (canonical in visitedDirs) return
            visitedDirs.add(canonical)

            val name = dir.name
            if (name.startsWith(".") || name.equals("Android", ignoreCase = true) || name.equals("lost.dir", ignoreCase = true)) {
                return
            }

            val files = dir.listFiles() ?: return
            val subdirs = files.filter { it.isDirectory && !it.name.startsWith(".") }
            val fileChildren = files.filter { it.isFile && !it.name.startsWith(".") }

            val isOrganizational = isOrganizationalFolder(dir)

            if (!isOrganizational) {
                // Check metadata if present
                val parsedZine = ZineMetadataExtractor.extractFromFolder(dir)
                val zineType = (parsedZine?.type ?: "").lowercase().trim()

                // 1. NOVEL CHECK
                val hasNovelSubdir = subdirs.any { sub ->
                    val subLower = sub.name.lowercase()
                    (subLower == "novel chapter" || subLower == "novel_chapter" || subLower == "chapters" || subLower == "chapter") &&
                    (sub.listFiles() ?: emptyArray()).any { f ->
                        f.isFile && f.extension.lowercase() in textExtensions &&
                        !f.name.equals("entry.json", ignoreCase = true) &&
                        !f.name.equals("metadata.json", ignoreCase = true)
                    }
                }
                val hasNovelDirectChapters = fileChildren.any { f ->
                    val fn = f.name.lowercase()
                    f.extension.lowercase() in textExtensions &&
                    (fn.startsWith("chapter") || fn.startsWith("ch_") || fn.startsWith("ch."))
                }
                val isExplicitNovel = zineType in listOf("novel", "light novel")

                if (isExplicitNovel || hasNovelSubdir || hasNovelDirectChapters) {
                    result.add(DiscoveredMediaUnit(folder = dir, mode = 4, purpose = "novel"))
                    // Stop recursing into internal novel chapter folders
                    return
                }

                // 2. MANGA / WEBTOON CHECK
                val isExplicitManga = zineType in listOf("manga", "manhua", "manhwa", "comic", "webtoon")
                val hasMangaChapterSubdirs = subdirs.any { sub ->
                    val subFiles = sub.listFiles() ?: emptyArray()
                    subFiles.any { f ->
                        f.isFile && f.extension.lowercase() in imageExtensions
                    }
                }
                val hasDirectMangaPages = fileChildren.count { f ->
                    f.extension.lowercase() in imageExtensions && !ZineMetadataExtractor.isDedicatedCoverName(f.name)
                } >= 3 && fileChildren.none { f ->
                    VideoImportUtils.videoExtensions.contains(f.extension.lowercase()) ||
                    MusicImportUtils.isValidAudioFile(f)
                }

                if (isExplicitManga || hasMangaChapterSubdirs || hasDirectMangaPages) {
                    val purpose = when {
                        zineType in listOf("manhua", "manhwa") -> "manhua"
                        else -> "manga"
                    }
                    result.add(DiscoveredMediaUnit(folder = dir, mode = 0, purpose = purpose))
                    // Stop recursing into chapter subdirectories
                    return
                }

                // 3. VIDEO SERIES / CHANNEL CHECK
                val isExplicitVideo = zineType in listOf("channel", "series", "video", "anime", "movie")
                val hasDirectVideos = fileChildren.any { f ->
                    VideoImportUtils.videoExtensions.contains(f.extension.lowercase())
                }
                val hasVideoSubdir = subdirs.any { sub ->
                    val subLower = sub.name.lowercase()
                    (subLower == "video" || subLower == "videos") &&
                    (sub.listFiles() ?: emptyArray()).any { f ->
                        f.isFile && VideoImportUtils.videoExtensions.contains(f.extension.lowercase())
                    }
                }

                if (isExplicitVideo || hasDirectVideos || hasVideoSubdir) {
                    val purpose = when {
                        zineType == "channel" -> "channel"
                        else -> "series"
                    }
                    result.add(DiscoveredMediaUnit(folder = dir, mode = 2, purpose = purpose))
                    // Stop recursing deeper into this video's internal folders
                    return
                }

                // 4. MUSIC COLLECTION / ALBUM CHECK
                val isExplicitMusic = zineType in listOf("music", "song", "audio", "album", "artist")
                val hasDirectAudio = fileChildren.any { f ->
                    MusicImportUtils.isValidAudioFile(f)
                }

                if (isExplicitMusic || hasDirectAudio) {
                    result.add(DiscoveredMediaUnit(folder = dir, mode = 3, purpose = "music"))
                    // Stop recursing deeper into music internal folders (e.g. lyrics/)
                    return
                }

                // 5. STANDALONE BOOKS / PDFS CHECK
                val directBooks = fileChildren.filter { f ->
                    f.extension.lowercase() in bookExtensions
                }
                for (bookFile in directBooks) {
                    result.add(
                        DiscoveredMediaUnit(
                            folder = dir,
                            mode = 1,
                            purpose = "book",
                            singleFile = bookFile
                        )
                    )
                }
            } else {
                // If it's an organizational folder, it may still contain direct book PDFs (like test my app/book/*.pdf)
                val directBooks = fileChildren.filter { f ->
                    f.extension.lowercase() in bookExtensions
                }
                for (bookFile in directBooks) {
                    result.add(
                        DiscoveredMediaUnit(
                            folder = dir,
                            mode = 1,
                            purpose = "book",
                            singleFile = bookFile
                        )
                    )
                }
            }

            // 6. Recurse into subdirectories
            for (sub in subdirs) {
                walk(sub, depth + 1)
            }
        }

        walk(root, 1)
        return result
    }
}
