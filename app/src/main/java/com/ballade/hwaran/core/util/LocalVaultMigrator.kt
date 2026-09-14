package com.ballade.hwaran.core.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
        try {
            if (isItemInVault(manga)) {
                return@withContext Result.success("Already stored in Local Vault")
            }

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

            val safeTitle = manga.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val targetFolder = File(vaultBase, "${safeTitle}_${manga.id}")
            if (!targetFolder.exists()) targetFolder.mkdirs()

            val contentResolver = context.contentResolver
            val chapters = database.trackDao().getChaptersForMangaList(manga.id)

            when (contentType) {
                1 -> {
                    // PDF Book: manga.parentUri is the PDF uri or folder
                    val sourceUri = Uri.parse(manga.parentUri)
                    val destPdf = File(targetFolder, "${safeTitle}.pdf")
                    copyUriToFile(contentResolver, sourceUri, destPdf)

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
                        onProgress(((idx.toFloat() / total) * 100).toInt(), ch.title)
                        val safeChTitle = ch.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val destFile = File(targetFolder, "$safeChTitle.$ext")

                        if (ch.folderUri.startsWith("content://")) {
                            val chUri = Uri.parse(ch.folderUri)
                            copyUriToFile(contentResolver, chUri, destFile)
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
                            copyUriToFile(contentResolver, child.uri, dest)
                        }
                        sourceDoc.delete()
                    } else if (sourceDoc != null) {
                        val destFile = File(targetFolder, sourceDoc.name ?: "${safeTitle}.epub")
                        copyUriToFile(contentResolver, sourceDoc.uri, destFile)
                        sourceDoc.delete()
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

                            if (chDoc != null && chDoc.isDirectory) {
                                chDoc.listFiles().forEach { pageDoc ->
                                    val pageName = pageDoc.name ?: "page.jpg"
                                    val destPage = File(chFolder, pageName)
                                    copyUriToFile(contentResolver, pageDoc.uri, destPage)
                                }
                                chDoc.delete()
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
                    copyUriToFile(contentResolver, coverUri, coverDest)
                    database.libraryDao().insertManga(manga.copy(coverPath = coverDest.absolutePath, parentUri = targetFolder.absolutePath))
                } catch (_: Exception) {}
            }

            HistoryTracker.logEvent("VAULT", manga.title, "Shifted to Local Vault")
            Result.success("Moved to Local Vault")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
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
            } else if (manga.parentUri.isNotBlank()) {
                val localFolder = File(manga.parentUri)
                if (localFolder.exists()) {
                    localFolder.deleteRecursively()
                }
            }

            // 2. Delete chapters' files
            val chapters = database.trackDao().getChaptersForMangaList(manga.id)
            chapters.forEach { ch ->
                if (ch.folderUri.startsWith("content://")) {
                    tryDeleteSafUri(context, Uri.parse(ch.folderUri))
                } else if (ch.folderUri.isNotBlank()) {
                    val localFile = File(ch.folderUri)
                    if (localFile.exists()) {
                        localFile.deleteRecursively()
                    }
                }
            }

            // 3. Delete from DB
            database.trackDao().deleteChaptersByMangaId(manga.id)
            database.libraryDao().deleteChildrenByParentId(manga.id)
            database.libraryDao().deleteManga(manga)

            // 4. Log event
            HistoryTracker.logEvent("DELETE", manga.title, "Media Item")
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun copyUriToFile(contentResolver: ContentResolver, sourceUri: Uri, destFile: File) {
        contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
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
            }
        } catch (_: Exception) {}
    }
}
