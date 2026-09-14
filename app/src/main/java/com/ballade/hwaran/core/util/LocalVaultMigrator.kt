package com.ballade.hwaran.core.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URLDecoder

object LocalVaultMigrator {

    fun isItemInVault(manga: MangaEntity): Boolean {
        val path = manga.parentUri.trim()
        if (path.isEmpty()) return false
        if (path.startsWith("content://")) return false
        return path.contains("vault", ignoreCase = true) || File(path).exists()
    }

    suspend fun moveToVault(
        context: Context,
        database: AppDatabase,
        manga: MangaEntity,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        val contentType = manga.contentType
        val vaultDirName = when (contentType) {
            1 -> "book_vault"
            2 -> "video_vault"
            3 -> "music_vault"
            4 -> "novel_vault"
            else -> "manga_vault"
        }

        val vaultBase = File(context.filesDir, vaultDirName)
        if (!vaultBase.exists()) vaultBase.mkdirs()
        File(vaultBase, ".nomedia").createNewFile()

        val safeTitle = manga.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val targetFolder = File(vaultBase, "${safeTitle}_${manga.id}")
        if (!targetFolder.exists()) targetFolder.mkdirs()

        try {
            if (isItemInVault(manga)) {
                return@withContext Result.success("Already stored in Local Vault")
            }

            val contentResolver = context.contentResolver
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            if (manga.parentUri.startsWith("content://")) {
                try {
                    val uri = Uri.parse(manga.parentUri)
                    if (DocumentsContract.isTreeUri(uri)) {
                        val treeUri = DocumentsContract.buildTreeDocumentUri(uri.authority, DocumentsContract.getTreeDocumentId(uri))
                        contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                    } else {
                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                    }
                } catch (_: Exception) {}
            }

            val chapters = database.trackDao().getChaptersForMangaList(manga.id)

            when (contentType) {
                1 -> {
                    // PDF Book
                    val sourceUri = Uri.parse(manga.parentUri)
                    val destPdf = File(targetFolder, "${safeTitle}.pdf")
                    copyUriToFile(context, contentResolver, sourceUri, destPdf) { copied, total ->
                        if (total > 0) {
                            val pct = ((copied.toDouble() / total) * 100).toInt().coerceIn(0, 99)
                            onProgress(pct, "Migrating ${manga.title} ($pct%)")
                        }
                    }

                    tryDeleteSafUri(context, sourceUri)

                    val updatedManga = manga.copy(parentUri = destPdf.absolutePath)
                    database.libraryDao().insertManga(updatedManga)
                    chapters.forEach { ch ->
                        database.trackDao().insertChapter(ch.copy(folderUri = destPdf.absolutePath))
                    }
                }
                2, 3 -> {
                    // Video or Music
                    val ext = if (contentType == 2) "mp4" else "mp3"
                    val updatedChapters = mutableListOf<ChapterEntity>()
                    val total = chapters.size.coerceAtLeast(1)

                    chapters.forEachIndexed { idx, ch ->
                        val safeChTitle = ch.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val destFile = File(targetFolder, "$safeChTitle.$ext")

                        if (ch.folderUri.startsWith("content://")) {
                            val chUri = Uri.parse(ch.folderUri)
                            try {
                                contentResolver.takePersistableUriPermission(chUri, takeFlags)
                            } catch (_: Exception) {}
                            copyUriToFile(context, contentResolver, chUri, destFile) { copied, fileTotal ->
                                val base = (idx.toFloat() / total) * 100f
                                val fraction = if (fileTotal > 0) ((copied.toFloat() / fileTotal) / total) * 100f else 0f
                                val currentPct = (base + fraction).toInt().coerceIn(0, 99)
                                onProgress(currentPct, ch.title)
                            }
                            tryDeleteSafUri(context, chUri)
                            updatedChapters.add(ch.copy(folderUri = destFile.absolutePath))
                        } else {
                            val srcFile = File(ch.folderUri)
                            if (srcFile.exists() && srcFile.parentFile?.absolutePath != targetFolder.absolutePath) {
                                srcFile.copyTo(destFile, overwrite = true)
                                srcFile.delete()
                                updatedChapters.add(ch.copy(folderUri = destFile.absolutePath))
                            } else {
                                updatedChapters.add(ch)
                            }
                            onProgress((((idx + 1).toFloat() / total) * 100).toInt().coerceIn(0, 99), ch.title)
                        }
                    }

                    tryDeleteSafUri(context, Uri.parse(manga.parentUri))
                    database.libraryDao().insertManga(manga.copy(parentUri = targetFolder.absolutePath))
                    updatedChapters.forEach { database.trackDao().insertChapter(it) }
                }
                4 -> {
                    // Novel
                    val sourceDoc = try {
                        DocumentFile.fromTreeUri(context, Uri.parse(manga.parentUri))
                    } catch (_: Exception) { null } ?: try {
                        DocumentFile.fromSingleUri(context, Uri.parse(manga.parentUri))
                    } catch (_: Exception) { null }

                    if (sourceDoc != null && sourceDoc.isDirectory) {
                        sourceDoc.listFiles().forEach { child ->
                            val childName = child.name ?: "novel_chapter"
                            val dest = File(targetFolder, childName)
                            copyUriToFile(context, contentResolver, child.uri, dest)
                        }
                        sourceDoc.delete()
                    } else if (sourceDoc != null) {
                        val destFile = File(targetFolder, sourceDoc.name ?: "${safeTitle}.epub")
                        copyUriToFile(context, contentResolver, sourceDoc.uri, destFile)
                        sourceDoc.delete()
                    } else {
                        // Fallback physical novel resolution
                        val physicalDir = resolveSafUriToPhysicalFile(Uri.parse(manga.parentUri))
                        if (physicalDir != null && physicalDir.exists()) {
                            if (physicalDir.isDirectory) {
                                physicalDir.copyRecursively(targetFolder, overwrite = true)
                                physicalDir.deleteRecursively()
                            } else {
                                val destFile = File(targetFolder, physicalDir.name)
                                physicalDir.copyTo(destFile, overwrite = true)
                                physicalDir.delete()
                            }
                        } else {
                            throw FileNotFoundException("Original novel folder or file could not be found.")
                        }
                    }

                    database.libraryDao().insertManga(manga.copy(parentUri = targetFolder.absolutePath))
                    chapters.forEach { ch ->
                        val fileName = File(ch.folderUri).name.ifBlank { ch.title }
                        val localFile = File(targetFolder, fileName)
                        database.trackDao().insertChapter(
                            ch.copy(folderUri = if (localFile.exists()) localFile.absolutePath else targetFolder.absolutePath)
                        )
                    }
                }
                else -> {
                    // Manga / Manhua (contentType == 0)
                    val updatedChapters = mutableListOf<ChapterEntity>()
                    val total = chapters.size.coerceAtLeast(1)

                    chapters.forEachIndexed { idx, ch ->
                        onProgress(((idx.toFloat() / total) * 100).toInt(), ch.title)
                        val safeChName = ch.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val chFolder = File(targetFolder, safeChName)
                        if (!chFolder.exists()) chFolder.mkdirs()

                        if (ch.folderUri.startsWith("content://")) {
                            val chDoc = try {
                                DocumentFile.fromTreeUri(context, Uri.parse(ch.folderUri))
                            } catch (_: Exception) { null }

                            var copiedPages = false
                            if (chDoc != null && chDoc.isDirectory) {
                                val pages = chDoc.listFiles()
                                if (pages.isNotEmpty()) {
                                    pages.forEach { pageDoc ->
                                        val pageName = pageDoc.name ?: "page.jpg"
                                        val destPage = File(chFolder, pageName)
                                        copyUriToFile(context, contentResolver, pageDoc.uri, destPage)
                                    }
                                    chDoc.delete()
                                    copiedPages = true
                                }
                            }

                            if (!copiedPages) {
                                // Fallback physical directory copy
                                val physicalCh = resolveSafUriToPhysicalFile(Uri.parse(ch.folderUri))
                                if (physicalCh != null && physicalCh.exists() && physicalCh.isDirectory) {
                                    physicalCh.copyRecursively(chFolder, overwrite = true)
                                    physicalCh.deleteRecursively()
                                    copiedPages = true
                                }
                            }

                            if (!copiedPages) {
                                throw FileNotFoundException("Could not access chapters for \"${ch.title}\". Files may have been moved or deleted.")
                            }

                            updatedChapters.add(ch.copy(folderUri = chFolder.absolutePath))
                        } else {
                            val srcFolder = File(ch.folderUri)
                            if (srcFolder.exists() && srcFolder.absolutePath != chFolder.absolutePath) {
                                srcFolder.copyRecursively(chFolder, overwrite = true)
                                srcFolder.deleteRecursively()
                                updatedChapters.add(ch.copy(folderUri = chFolder.absolutePath))
                            } else {
                                updatedChapters.add(ch)
                            }
                        }
                    }

                    tryDeleteSafUri(context, Uri.parse(manga.parentUri))
                    database.libraryDao().insertManga(manga.copy(parentUri = targetFolder.absolutePath))
                    updatedChapters.forEach { database.trackDao().insertChapter(it) }
                }
            }

            // Copy cover image if external
            if (manga.coverPath.startsWith("content://")) {
                try {
                    val coverUri = Uri.parse(manga.coverPath)
                    val coverDest = File(targetFolder, "cover.jpg")
                    copyUriToFile(context, contentResolver, coverUri, coverDest)
                    database.libraryDao().insertManga(manga.copy(coverPath = coverDest.absolutePath, parentUri = targetFolder.absolutePath))
                } catch (_: Exception) {}
            }

            onProgress(100, "Done")
            HistoryTracker.logEvent("VAULT", manga.title, "Shifted to Local Vault")
            Result.success("Moved to Local Vault")
        } catch (e: Exception) {
            // Clean up partial folder on error so corrupted data doesn't remain in vault
            try {
                if (targetFolder.exists() && targetFolder.listFiles()?.isEmpty() == true) {
                    targetFolder.deleteRecursively()
                }
            } catch (_: Exception) {}

            e.printStackTrace()
            val friendlyError = when {
                e is FileNotFoundException -> e.message ?: "Original file not found"
                e is SecurityException -> "Storage permission revoked for original location. Please verify file exists."
                e.message?.contains("ExternalStorageProvider", ignoreCase = true) == true ->
                    "Cannot open original location. The folder may have been moved, renamed, or deleted from external storage."
                else -> e.localizedMessage ?: "Unknown migration error"
            }
            Result.failure(Exception(friendlyError, e))
        }
    }

    suspend fun deleteMedia(
        context: Context,
        database: AppDatabase,
        manga: MangaEntity
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Delete parent files from disk
            if (manga.parentUri.startsWith("content://")) {
                tryDeleteSafUri(context, Uri.parse(manga.parentUri))
            } else {
                val localFile = File(manga.parentUri)
                if (localFile.exists()) {
                    if (localFile.isDirectory) {
                        localFile.deleteRecursively()
                    } else {
                        localFile.delete()
                    }
                }
            }

            // 2. Also ensure each chapter's individual file/folder is deleted
            val chapters = database.trackDao().getChaptersForMangaList(manga.id)
            chapters.forEach { ch ->
                if (ch.folderUri.startsWith("content://")) {
                    tryDeleteSafUri(context, Uri.parse(ch.folderUri))
                } else {
                    val chFile = File(ch.folderUri)
                    if (chFile.exists()) {
                        if (chFile.isDirectory) chFile.deleteRecursively() else chFile.delete()
                    }
                }
            }

            // 3. Delete cached cover from internal app storage if present
            if (manga.coverPath.isNotBlank() && !manga.coverPath.startsWith("content://")) {
                try {
                    val cover = File(manga.coverPath)
                    if (cover.exists() && cover.isFile) {
                        cover.delete()
                    }
                } catch (_: Exception) {}
            }

            // 4. Delete from DB
            database.trackDao().deleteChaptersByMangaId(manga.id)
            database.libraryDao().deleteChildrenByParentId(manga.id)
            database.libraryDao().deleteManga(manga)

            // 5. Log event
            HistoryTracker.logEvent("DELETE", manga.title, "Media Item")
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun checkMediaExists(
        context: Context,
        database: AppDatabase,
        manga: MangaEntity
    ): Boolean = withContext(Dispatchers.IO) {
        // Virtual custom playlists always exist
        if (manga.parentUri.startsWith("custom_playlist_") || manga.title.equals("Favorites", ignoreCase = true)) {
            return@withContext true
        }

        // Vault items
        if (isItemInVault(manga)) {
            val vaultDir = File(manga.parentUri)
            if (vaultDir.exists()) return@withContext true
            val chapters = database.trackDao().getChaptersForMangaList(manga.id)
            return@withContext chapters.any { File(it.folderUri).exists() }
        }

        // SAF URIs
        if (manga.parentUri.startsWith("content://")) {
            val uri = Uri.parse(manga.parentUri)
            try {
                val doc = if (manga.parentUri.contains("/tree/")) {
                    DocumentFile.fromTreeUri(context, uri)
                } else {
                    DocumentFile.fromSingleUri(context, uri)
                }
                if (doc != null && doc.exists()) return@withContext true
            } catch (_: Exception) {}

            val physical = resolveSafUriToPhysicalFile(uri)
            if (physical != null && physical.exists()) return@withContext true

            val fileName = physical?.name
                ?: uri.lastPathSegment?.substringAfterLast("/")?.substringAfterLast("%2F")
            if (!fileName.isNullOrBlank()) {
                val decoded = try { URLDecoder.decode(fileName, "UTF-8") } catch (_: Exception) { fileName }
                val candidate = findFileInCommonDirectories(decoded)
                if (candidate != null && candidate.exists()) return@withContext true
            }

            // Check if any chapters still exist
            val chapters = database.trackDao().getChaptersForMangaList(manga.id)
            for (ch in chapters) {
                if (ch.folderUri.startsWith("content://")) {
                    val chUri = Uri.parse(ch.folderUri)
                    try {
                        val chDoc = DocumentFile.fromSingleUri(context, chUri)
                        if (chDoc != null && chDoc.exists()) return@withContext true
                    } catch (_: Exception) {}
                    val chPhys = resolveSafUriToPhysicalFile(chUri)
                    if (chPhys != null && chPhys.exists()) return@withContext true
                } else if (ch.folderUri.isNotBlank()) {
                    if (File(ch.folderUri).exists()) return@withContext true
                }
            }

            return@withContext false
        }

        // Direct paths
        if (manga.parentUri.startsWith("/") || manga.parentUri.startsWith("file://")) {
            val directPath = if (manga.parentUri.startsWith("file://")) Uri.parse(manga.parentUri).path ?: "" else manga.parentUri
            if (File(directPath).exists()) return@withContext true
            val chapters = database.trackDao().getChaptersForMangaList(manga.id)
            return@withContext chapters.any { File(it.folderUri).exists() }
        }

        false
    }

    suspend fun pruneMissingMedia(
        context: Context,
        database: AppDatabase
    ): Int = withContext(Dispatchers.IO) {
        val allManga = database.libraryDao().getAllMangaList()
        var pruned = 0
        for (manga in allManga) {
            if (manga.parentUri.startsWith("custom_playlist_") || manga.title.equals("Favorites", ignoreCase = true)) {
                continue
            }
            if (!checkMediaExists(context, database, manga)) {
                deleteMedia(context, database, manga)
                pruned++
            }
        }
        pruned
    }

    private fun copyUriToFile(
        context: Context,
        contentResolver: ContentResolver,
        sourceUri: Uri,
        destFile: File,
        onByteProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val streamData = openSourceInputStream(context, sourceUri)
            ?: throw FileNotFoundException(
                "Original file '${destFile.name}' could not be accessed. The file may have been moved, deleted, or storage permission was denied."
            )

        val buffer = ByteArray(128 * 1024) // 128KB buffer for high-throughput I/O
        var bytesCopied = 0L
        val totalBytes = streamData.second

        streamData.first.use { input ->
            destFile.outputStream().use { output ->
                var bytes = input.read(buffer)
                while (bytes >= 0) {
                    output.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    onByteProgress?.invoke(bytesCopied, totalBytes)
                    bytes = input.read(buffer)
                }
                output.flush()
            }
        }
    }

    private fun openSourceInputStream(context: Context, sourceUri: Uri): Pair<InputStream, Long>? {
        val contentResolver = context.contentResolver

        // Strategy 1: Standard ContentResolver stream
        try {
            val stream = contentResolver.openInputStream(sourceUri)
            if (stream != null) {
                val size = try {
                    contentResolver.openFileDescriptor(sourceUri, "r")?.use { it.statSize } ?: -1L
                } catch (_: Exception) { -1L }
                return Pair(stream, size)
            }
        } catch (_: SecurityException) {
            // Permission denial on SAF provider - fall through to physical resolution
        } catch (_: FileNotFoundException) {
            // Provider reported not found - fall through to physical resolution
        } catch (_: Exception) {}

        // Strategy 2: Physical file resolution from SAF Document ID
        val physicalFile = resolveSafUriToPhysicalFile(sourceUri)
        if (physicalFile != null && physicalFile.exists() && physicalFile.canRead()) {
            return Pair(physicalFile.inputStream(), physicalFile.length())
        }

        // Strategy 3: Direct file URI
        val uriStr = sourceUri.toString()
        if (uriStr.startsWith("file://") || uriStr.startsWith("/")) {
            val directPath = if (uriStr.startsWith("file://")) sourceUri.path ?: "" else uriStr
            val directFile = File(directPath)
            if (directFile.exists() && directFile.canRead()) {
                return Pair(directFile.inputStream(), directFile.length())
            }
        }

        // Strategy 4: Fallback search by filename across common storage roots
        val fileName = physicalFile?.name
            ?: sourceUri.lastPathSegment?.substringAfterLast("/")?.substringAfterLast("%2F")
        if (!fileName.isNullOrBlank()) {
            val decodedName = try { URLDecoder.decode(fileName, "UTF-8") } catch (_: Exception) { fileName }
            val candidate = findFileInCommonDirectories(decodedName)
            if (candidate != null && candidate.exists() && candidate.canRead()) {
                return Pair(candidate.inputStream(), candidate.length())
            }
        }

        return null
    }

    fun resolveSafUriToPhysicalFile(uri: Uri): File? {
        try {
            val uriStr = uri.toString()
            if (uriStr.startsWith("file://")) {
                return File(uri.path ?: "")
            }
            if (uriStr.startsWith("/")) {
                return File(uriStr)
            }

            val docId = if (uriStr.contains("/document/")) {
                try {
                    DocumentsContract.getDocumentId(uri)
                } catch (_: Exception) {
                    val raw = uriStr.substringAfter("/document/")
                    URLDecoder.decode(raw, "UTF-8")
                }
            } else if (uriStr.contains("/tree/")) {
                try {
                    DocumentsContract.getTreeDocumentId(uri)
                } catch (_: Exception) {
                    val raw = uriStr.substringAfter("/tree/").substringBefore("/document/")
                    URLDecoder.decode(raw, "UTF-8")
                }
            } else {
                null
            } ?: return null

            val decodedDocId = try { URLDecoder.decode(docId, "UTF-8") } catch (_: Exception) { docId }
            if (decodedDocId.startsWith("primary:", ignoreCase = true)) {
                val relPath = decodedDocId.substringAfter(":")
                return File(Environment.getExternalStorageDirectory(), relPath)
            } else if (decodedDocId.contains(":")) {
                val parts = decodedDocId.split(":", limit = 2)
                val volumeId = parts[0]
                val relPath = parts[1]
                val storageVolume = File("/storage/$volumeId")
                if (storageVolume.exists()) {
                    return File(storageVolume, relPath)
                }
                return File("/storage/emulated/0/$relPath")
            }
        } catch (_: Exception) {}
        return null
    }

    private fun findFileInCommonDirectories(fileName: String): File? {
        val roots = listOf(
            Environment.getExternalStorageDirectory(),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File(Environment.getExternalStorageDirectory(), "Book0"),
            File(Environment.getExternalStorageDirectory(), "Books"),
            File(Environment.getExternalStorageDirectory(), "Manhua"),
            File(Environment.getExternalStorageDirectory(), "Music"),
            File(Environment.getExternalStorageDirectory(), "Movies"),
            File(Environment.getExternalStorageDirectory(), "Telegram")
        )
        for (root in roots) {
            if (!root.exists()) continue
            val direct = File(root, fileName)
            if (direct.exists() && direct.canRead()) return direct
            // Search 1 level down
            val found = root.listFiles()?.firstOrNull { child ->
                if (child.isDirectory) {
                    val sub = File(child, fileName)
                    sub.exists() && sub.canRead()
                } else {
                    child.name.equals(fileName, ignoreCase = true)
                }
            }
            if (found != null) {
                return if (found.isDirectory) File(found, fileName) else found
            }
        }
        return null
    }

    private fun tryDeleteSafUri(context: Context, uri: Uri) {
        try {
            val singleDoc = DocumentFile.fromSingleUri(context, uri)
            if (singleDoc != null && singleDoc.exists()) {
                singleDoc.delete()
                return
            }
            val treeDoc = DocumentFile.fromTreeUri(context, uri)
            if (treeDoc != null && treeDoc.exists()) {
                treeDoc.delete()
                return
            }
        } catch (_: Exception) {}

        // Also try direct physical deletion if resolved
        try {
            val physical = resolveSafUriToPhysicalFile(uri)
            if (physical != null && physical.exists()) {
                if (physical.isDirectory) physical.deleteRecursively() else physical.delete()
            }
        } catch (_: Exception) {}
    }
}
