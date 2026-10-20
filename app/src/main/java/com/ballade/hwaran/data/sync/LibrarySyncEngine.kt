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
        "download", "downloads", "zine", "zine scraper", "zine_scraper", "zine scrapper", "zine_scrapper", "zinescraper", "zinescrapper",
        "vacuum", "quick grab", "quick_grab", "youtube", "music", "channel", "channels",
        "series", "book", "books", "manhua", "manga", "novel", "novels",
        "video", "videos", "document", "documents", "files", "media"
    )

    fun isZineFolderName(name: String): Boolean {
        val lower = name.lowercase().trim()
        if (lower == "zine" || lower.startsWith("zine ") || lower.startsWith("zine_") || lower.startsWith("zine-")) return true
        if (lower == "zine scraper" || lower == "zine scrapper" ||
            lower == "zine_scraper" || lower == "zine_scrapper" ||
            lower == "zinescraper" || lower == "zinescrapper" ||
            lower == "zine-scraper" || lower == "zine-scrapper" ||
            lower.startsWith("zine scraper ") || lower.startsWith("zine scrapper ") ||
            lower.startsWith("zine_scraper_") || lower.startsWith("zine_scrapper_") ||
            lower.startsWith("zinescraper_") || lower.startsWith("zinescrapper_") ||
            lower.startsWith("zine-scraper-") || lower.startsWith("zine-scrapper-")) return true
        return false
    }

    /**
     * Determines whether a directory is specifically a Zine root folder.
     */
    fun isZineDirectory(dir: File): Boolean {
        return isZineFolderName(dir.name)
    }

    /**
     * Checks if a file or directory is inside a Zine root folder or hierarchy.
     * Traverses upward from the given file/dir to verify that at least one ancestor
     * (or the directory itself) is a Zine folder.
     */
    fun isInsideZineDirectory(file: File): Boolean {
        var curr: File? = file
        while (curr != null) {
            val name = curr.name.lowercase().trim()
            if (isZineFolderName(name)) return true
            val parent = curr.parentFile
            if (parent == null || curr.absolutePath == "/" || curr.absolutePath == "/storage/emulated/0" || curr.absolutePath == "/storage") {
                break
            }
            curr = parent
        }
        return false
    }

    fun isPathInsideZineDirectory(pathOrUri: String): Boolean {
        val clean = if (pathOrUri.startsWith("file://")) {
            Uri.parse(pathOrUri).path ?: Uri.decode(pathOrUri.removePrefix("file://"))
        } else {
            pathOrUri
        }
        return isInsideZineDirectory(File(clean))
    }

    /**
     * Discovers all candidate Zine roots across internal storage and Download folder.
     * Searches deeply for ANY folder named zine or zine scrapper across the phone,
     * even if nested in subfolders. Never includes generic root storage directories like
     * Download, Documents, Movies, or Music.
     */
    fun findZineRoots(extStorage: File): List<File> {
        val roots = LinkedHashMap<String, File>()

        fun addRoot(dir: File) {
            try {
                val canonical = dir.canonicalFile
                val key = canonical.canonicalPath.lowercase()
                if (!roots.containsKey(key)) {
                    roots[key] = canonical
                }
            } catch (_: Exception) {
                val key = dir.absolutePath.lowercase()
                if (!roots.containsKey(key)) {
                    roots[key] = dir
                }
            }
        }

        // 1. Explicit well-known locations
        val wellKnownPaths = listOf(
            "Zine",
            "Zine Scraper",
            "zine_scraper",
            "zine scrapper",
            "zine_scrapper",
            "Download/Zine",
            "Download/Zine Scraper",
            "Download/zine_scraper",
            "Download/zine scrapper",
            "Download/zine_scrapper"
        )
        for (rel in wellKnownPaths) {
            val dir = File(extStorage, rel)
            if (dir.exists() && dir.isDirectory && dir.canRead()) {
                addRoot(dir)
            }
        }

        val excludedDirNames = setOf(
            "android", "lost.dir", ".trash", "cache", ".thumbnails"
        )

        // 2. Search deeply across the entire storage for ANY folder named zine or zine scrapper (even in subfolders)
        fun searchDir(dir: File, depth: Int) {
            if (depth > 6 || !dir.exists() || !dir.isDirectory || !dir.canRead()) return

            val name = dir.name
            val nameLower = name.lowercase().trim()
            if (name.startsWith(".") || nameLower in excludedDirNames) {
                return
            }

            // If THIS folder is a Zine folder
            if (dir != extStorage && isZineDirectory(dir)) {
                addRoot(dir)
                // Once a Zine root is identified, scanDeepMedia will deeply inspect all its internal folders.
                return
            }

            // Recurse into subdirectories
            val subdirs = dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && f.name.lowercase().trim() !in excludedDirNames } ?: return
            for (sub in subdirs) {
                searchDir(sub, depth + 1)
            }
        }

        searchDir(extStorage, 0)

        return roots.values.toList()
    }

    /**
     * Identifies whether a directory is an organizational grouping folder
     * (e.g. "novel", "music", "channel", "Zine", "Download", "test my app")
     * rather than an actual media leaf unit. Organizational folders must be
     * recursed into and must never be registered as single media items.
     */
    fun isOrganizationalFolder(dir: File): Boolean {
        val lower = dir.name.lowercase().trim()
        if (lower in organizationalFolderNames) return true
        if (isZineFolderName(lower)) return true
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

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
                Log.w(TAG, "Cannot perform Library Deep Sync: MANAGE_EXTERNAL_STORAGE permission is not granted.")
                return@withContext SyncSummary(0, 0)
            }

            val existingMedia = database.libraryDao().getAllMangaList()
            val existingUris = HashSet<String>()
            val seenCanonicalPaths = HashSet<String>()

            // 1. Clean up erroneous container entries, non-Zine auto-discoveries, and duplicates
            for (manga in existingMedia) {
                val directFile = manga.parentUri?.let { StorageResolver.resolveToFile(context, Uri.parse(it)) }
                val isContainer = directFile != null && isOrganizationalFolder(directFile)
                val isFileAutoImport = manga.parentUri?.let { it.startsWith("file://") || it.startsWith("/") } == true
                val isNonZineAutoImport = isFileAutoImport && (
                    if (directFile != null) !isInsideZineDirectory(directFile)
                    else manga.parentUri?.let { !isPathInsideZineDirectory(it) } ?: false
                )

                val canonicalKey = try { directFile?.canonicalPath?.lowercase() } catch (_: Exception) { null }
                val isDuplicate = canonicalKey != null && canonicalKey in seenCanonicalPaths && manga.parentMangaId == null

                if (isContainer || isNonZineAutoImport || isDuplicate) {
                    try {
                        database.mediaDao().deleteManga(manga)
                        database.trackDao().deleteChaptersByMangaId(manga.id)
                        Log.d(TAG, "Removed invalid entry: ${manga.title} (${manga.parentUri}, container=$isContainer, nonZine=$isNonZineAutoImport, dup=$isDuplicate)")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    continue
                }

                if (canonicalKey != null) {
                    seenCanonicalPaths.add(canonicalKey)
                }
                manga.parentUri?.let { existingUris.add(it.lowercase()) }
                directFile?.canonicalPath?.lowercase()?.let { existingUris.add(it) }
                directFile?.absolutePath?.lowercase()?.let { existingUris.add(it) }
            }

            // 2. Discover new media units strictly within Zine storage roots
            onProgress?.invoke("Scanning Zine folders for new media…")
            val extStorage = Environment.getExternalStorageDirectory()
            val candidateRoots = findZineRoots(extStorage)
            Log.d(TAG, "Candidate Zine roots: ${candidateRoots.map { it.absolutePath }}")

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
                val canonicalFolder = try { item.folder.canonicalFile } catch (_: Exception) { item.folder }
                val canonicalTarget = try { (item.singleFile ?: item.folder).canonicalFile } catch (_: Exception) { item.singleFile ?: item.folder }
                val folderPath = canonicalFolder.absolutePath
                val folderUriStr = Uri.fromFile(canonicalFolder).toString()
                val targetUriStr = Uri.fromFile(canonicalTarget).toString()
                val targetPath = canonicalTarget.absolutePath

                val targetPathLower = targetPath.lowercase()
                val targetUriStrLower = targetUriStr.lowercase()

                if (targetPathLower in existingUris || targetUriStrLower in existingUris) {
                    continue
                }

                // Check if already in DB
                val inDb = database.libraryDao().getRootMangaByUri(targetUriStr) != null ||
                        database.libraryDao().getRootMangaByUri(targetPath) != null ||
                        database.libraryDao().getRootMangaByUri(targetUriStrLower) != null ||
                        database.libraryDao().getRootMangaByUri(targetPathLower) != null
                if (inDb) {
                    existingUris.add(targetPathLower)
                    existingUris.add(targetUriStrLower)
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
                        existingUris.add(targetPathLower)
                        existingUris.add(targetUriStrLower)
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
