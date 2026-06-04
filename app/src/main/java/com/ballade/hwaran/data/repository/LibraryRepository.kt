package com.ballade.hwaran.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.ballade.hwaran.data.local.AppDatabase
import com.ballade.hwaran.data.local.ChapterEntity
import com.ballade.hwaran.data.local.MangaEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import java.io.File

class ImportResult(
    val destinationFolder: File,
    val copiedChapters: List<File>,
    val copiedLooseFiles: List<File>
)

class LibraryRepository(private val context: Context, private val database: AppDatabase) {

    suspend fun getRootMangaByUri(uri: String): com.ballade.hwaran.data.local.MangaEntity? {
        return withContext(Dispatchers.IO) {
            database.libraryDao().getRootMangaByUri(uri)
        }
    }

    private suspend fun importMangaToVaultSaf(sourceDoc: DocumentFile, context: Context, isCancelled: () -> Boolean, onProgress: (Int) -> Unit): ImportResult? = withContext(Dispatchers.IO) {
        val vaultBase = File(context.filesDir, "manga_vault")
        if (!vaultBase.exists()) vaultBase.mkdirs()
        File(vaultBase, ".nomedia").createNewFile()

        val mangaName = sourceDoc.name ?: "Unknown"
        val destination = File(vaultBase, mangaName)
        if (!destination.exists()) destination.mkdirs()

        val supportedExtensions = setOf(
            "jpg", "jpeg", "png", "webp", "bmp", "gif",
            "mp4", "mkv", "avi", "webm", "m4v", "3gp", "mov", "flv",
            "mp3", "wav", "flac", "aac", "ogg", "m4a",
            "pdf"
        )
        val isSupportedFile: (DocumentFile) -> Boolean = { file ->
            val name = file.name ?: ""
            !name.startsWith(".") && supportedExtensions.contains(name.substringAfterLast(".", "").lowercase())
        }

        val rootFiles = sourceDoc.listFiles() ?: emptyArray()
        val chapterDocs = rootFiles.filter { it.isDirectory && !(it.name?.startsWith(".") == true) }
        val looseFiles = rootFiles.filter { !it.isDirectory && isSupportedFile(it) }

        // Cache all chapter subdirectory contents to avoid duplicate SAF queries
        val chapterFilesMap = mutableMapOf<DocumentFile, List<DocumentFile>>()
        var totalFilesCount = looseFiles.size
        
        chapterDocs.forEach { doc ->
            val filesInChapter = doc.listFiles()?.filter { !it.isDirectory && isSupportedFile(it) } ?: emptyList()
            chapterFilesMap[doc] = filesInChapter
            totalFilesCount += filesInChapter.size
        }

        var copiedFilesCount = 0
        var lastReportedProgress = -1
        
        val reportProgress: () -> Unit = {
            synchronized(this@LibraryRepository) {
                copiedFilesCount++
                if (totalFilesCount > 0) {
                    val currentProgress = ((copiedFilesCount.toDouble() / totalFilesCount) * 100).toInt()
                    if (currentProgress != lastReportedProgress) {
                        lastReportedProgress = currentProgress
                        onProgress(currentProgress.coerceIn(0, 100))
                    }
                }
            }
        }

        val semaphore = kotlinx.coroutines.sync.Semaphore(4)

        coroutineScope {
            looseFiles.map { child ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        if (isCancelled()) return@withPermit
                        ensureActive()
                        val name = child.name ?: return@withPermit
                        val destFile = File(destination, name)
                        try {
                            context.contentResolver.openInputStream(child.uri)?.use { input ->
                                destFile.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    var bytesRead = input.read(buffer)
                                    while (bytesRead >= 0) {
                                        if (isCancelled()) {
                                            break
                                        }
                                        output.write(buffer, 0, bytesRead)
                                        bytesRead = input.read(buffer)
                                    }
                                }
                            }
                            if (isCancelled()) {
                                destFile.delete()
                            } else {
                                reportProgress()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }.awaitAll()
        }

        coroutineScope {
            chapterDocs.map { chapterDoc ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        if (isCancelled()) return@withPermit
                        val name = chapterDoc.name ?: return@withPermit
                        val chapterDest = File(destination, name)
                        chapterDest.mkdirs()
                        
                        val children = chapterFilesMap[chapterDoc] ?: emptyList()
                        children.forEach { child ->
                            if (isCancelled()) return@forEach
                            ensureActive()
                            val childName = child.name ?: return@forEach
                            val destFile = File(chapterDest, childName)
                            try {
                                context.contentResolver.openInputStream(child.uri)?.use { input ->
                                    destFile.outputStream().use { output ->
                                        val buffer = ByteArray(8192)
                                        var bytesRead = input.read(buffer)
                                        while (bytesRead >= 0) {
                                            if (isCancelled()) {
                                                break
                                            }
                                            output.write(buffer, 0, bytesRead)
                                            bytesRead = input.read(buffer)
                                        }
                                    }
                                }
                                if (isCancelled()) {
                                    destFile.delete()
                                } else {
                                    reportProgress()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        if (isCancelled() || copiedFilesCount < totalFilesCount) {
            destination.deleteRecursively()
            return@withContext null
        }
        val copiedLoose = looseFiles.map { File(destination, it.name ?: "") }
        val copiedChaps = chapterDocs.map { File(destination, it.name ?: "") }
        sourceDoc.delete()
        onProgress(100)
        return@withContext ImportResult(destination, copiedChaps, copiedLoose)
    }

    private suspend fun importFileToVaultSaf(sourceDoc: DocumentFile, context: Context, isCancelled: () -> Boolean, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        if (isCancelled()) return@withContext null
        val vaultBase = File(context.filesDir, "manga_vault")
        if (!vaultBase.exists()) vaultBase.mkdirs()
        File(vaultBase, ".nomedia").createNewFile()

        val fileName = sourceDoc.name ?: "Unknown"
        val folderName = fileName.substringBeforeLast(".")
        val destination = File(vaultBase, folderName)
        if (!destination.exists()) destination.mkdirs()

        val destFile = File(destination, fileName)
        val totalBytes = sourceDoc.length()
        
        var copySuccess = false
        try {
            context.contentResolver.openInputStream(sourceDoc.uri)?.use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesCopied = 0L
                    var bytesRead = input.read(buffer)
                    var lastReportedProgress = -1
                    
                    while (bytesRead >= 0) {
                        if (isCancelled()) {
                            break
                        }
                        output.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead
                        if (totalBytes > 0) {
                            val currentProgress = ((bytesCopied.toDouble() / totalBytes) * 100).toInt()
                            if (currentProgress != lastReportedProgress) {
                                lastReportedProgress = currentProgress
                                onProgress(currentProgress.coerceIn(0, 100))
                            }
                        }
                        bytesRead = input.read(buffer)
                    }
                }
            }
            copySuccess = !isCancelled()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (isCancelled() || !copySuccess) {
            destFile.delete()
            destination.deleteRecursively()
            return@withContext null
        }

        onProgress(100)
        return@withContext destination
    }

    private fun extractPdfCover(context: Context, pdfUri: Uri, destinationFile: File): String? {
        try {
            val pfd = if (pdfUri.scheme == "content") {
                context.contentResolver.openFileDescriptor(pdfUri, "r")
            } else {
                ParcelFileDescriptor.open(File(pdfUri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
            }
            if (pfd != null) {
                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val width = page.width * 2
                    val height = page.height * 2
                    val maxDim = 800
                    val scale = if (width > maxDim || height > maxDim) maxDim.toFloat() / maxOf(width, height) else 1f
                    val w = (width * scale).toInt()
                    val h = (height * scale).toInt()
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    
                    destinationFile.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    
                    renderer.close()
                    pfd.close()
                    return destinationFile.absolutePath
                }
                renderer.close()
                pfd.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun extractVideoTitle(context: Context, videoUri: Uri, fallbackName: String): String {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!title.isNullOrBlank()) title else fallbackName
        } catch (e: Exception) {
            fallbackName
        } finally {
            try { retriever.release() } catch(e: Exception) {}
        }
    }

    private fun extractAudioCover(context: Context, audioUri: Uri): String? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, audioUri)
            val art = retriever.embeddedPicture
            if (art != null) {
                val coverFile = File(context.filesDir, "audio_cover_${System.currentTimeMillis()}.jpg")
                coverFile.outputStream().use { it.write(art) }
                coverFile.absolutePath
            } else null
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch(e: Exception) {}
        }
    }

    private fun extractAudioMetadata(context: Context, audioUri: Uri): Pair<String?, Long> {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, audioUri)
            val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            artist to duration
        } catch (e: Exception) {
            null to 0L
        } finally {
            try { retriever.release() } catch(e: Exception) {}
        }
    }

    suspend fun scanImportedFolder(
        rootUri: Uri,
        isLocalMode: Boolean = true,
        isFile: Boolean = false,
        isNsfwInput: Boolean = false,
        boxPurposeInput: String? = null,
        workspace: String? = null,
        documentFileOverride: DocumentFile? = null,
        importMode: String = "Single Import",
        isCancelled: () -> Boolean = { false },
        onProgress: (Int) -> Unit = {}
    ): Long? = withContext(Dispatchers.IO) {
        val rootDoc = documentFileOverride ?: if (isFile) DocumentFile.fromSingleUri(context, rootUri) ?: return@withContext null else DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext null
        
        var cachedRootDocFiles: Array<DocumentFile>? = null
        fun getRootDocFiles(): Array<DocumentFile> {
            ensureActive()
            if (cachedRootDocFiles == null) {
                cachedRootDocFiles = rootDoc.listFiles() ?: emptyArray()
            }
            return cachedRootDocFiles!!
        }

        val importResult = if (isLocalMode) {
            if (isFile) {
                val destFile = importFileToVaultSaf(rootDoc, context, isCancelled, onProgress) ?: return@withContext null
                ImportResult(destFile.parentFile!!, emptyList(), listOf(destFile))
            } else {
                importMangaToVaultSaf(rootDoc, context, isCancelled, onProgress) ?: return@withContext null
            }
        } else null
        
        if (isCancelled()) return@withContext null

        val libraryDao = database.libraryDao()

        val lastMod = if (isLocalMode) importResult!!.destinationFolder.lastModified() else rootDoc.lastModified()
        
        // CRITICAL: ONLY use getRootMangaByUri to avoid picking up child boxes (seasons) as the main entry.
        // Child boxes share the same parentUri but have a non-null parentMangaId.
        val existingManga = if (isLocalMode) {
            libraryDao.getRootMangaByUri(importResult!!.destinationFolder.absolutePath)
        } else {
            libraryDao.getRootMangaByUri(rootUri.toString())
        }

        val title = existingManga?.title ?: (if (isLocalMode) importResult!!.destinationFolder.name else rootDoc.name?.substringBeforeLast(".")) ?: "Unknown"
        val description = existingManga?.description ?: "No description added yet."
        val thoughts = existingManga?.thoughts ?: "No thoughts added."
        val isNsfw = existingManga?.isNsfw ?: isNsfwInput
        var coverPath = existingManga?.coverPath ?: ""

        val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".webm", ".m4v", ".3gp", ".mov", ".flv")
        val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif")
        val audioExtensions = listOf(".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a")

        var computedContentType = 0
        if (isFile) {
            val name = rootDoc.name?.lowercase() ?: ""
            if (name.endsWith(".pdf")) computedContentType = 1
            else if (videoExtensions.any { name.endsWith(it) }) computedContentType = 2
            else if (audioExtensions.any { name.endsWith(it) }) computedContentType = 3
        } else {
            val hasPdf = if (isLocalMode) {
                importResult!!.copiedLooseFiles.any { it.name.lowercase().endsWith(".pdf") }
            } else {
                getRootDocFiles().any { item ->
                    val itemName = item.name
                    itemName?.lowercase()?.endsWith(".pdf") == true
                }
            }
            val hasVideo = if (isLocalMode) {
                importResult!!.copiedLooseFiles.any { videoExtensions.any { ext -> it.name.lowercase().endsWith(ext) } }
            } else {
                getRootDocFiles().any { item ->
                    val itemName = item.name
                    itemName != null && videoExtensions.any { itemName.lowercase().endsWith(it) }
                }
            }
            val hasAudio = if (isLocalMode) {
                importResult!!.copiedLooseFiles.any { audioExtensions.any { ext -> it.name.lowercase().endsWith(ext) } }
            } else {
                getRootDocFiles().any { item ->
                    val itemName = item.name
                    itemName != null && audioExtensions.any { itemName.lowercase().endsWith(it) }
                }
            }
            val hasImages = if (isLocalMode) {
                importResult!!.copiedLooseFiles.any { imageExtensions.any { ext -> it.name.lowercase().endsWith(ext) } }
            } else {
                getRootDocFiles().any { item ->
                    val itemName = item.name
                    itemName != null && imageExtensions.any { itemName.lowercase().endsWith(it) }
                }
            }

            if (hasAudio) computedContentType = 3
            else if (hasVideo) computedContentType = 2
            else if (hasPdf) computedContentType = 1
            else if (hasImages) computedContentType = 0
            else computedContentType = 0 // Default to manga/subfolder mode
            
            // Try to find a cover image for music/video/manga folders
            if (coverPath.isEmpty()) {
                val potentialCover = if (isLocalMode) {
                    importResult!!.copiedLooseFiles.find { item ->
                        val itemName = item.name.lowercase()
                        itemName == "cover.jpg" || itemName == "cover.png" || itemName == "cover.gif" || itemName == "folder.jpg" || itemName == "folder.png" || itemName == "folder.gif" || itemName == "poster.jpg" || itemName == "poster.gif"
                    }
                } else {
                    getRootDocFiles().find { item ->
                        val itemName = item.name?.lowercase() ?: ""
                        itemName == "cover.jpg" || itemName == "cover.png" || itemName == "cover.gif" || itemName == "folder.jpg" || itemName == "folder.png" || itemName == "folder.gif" || itemName == "poster.jpg" || itemName == "poster.gif"
                    }
                }
                if (potentialCover != null) {
                    coverPath = if (isLocalMode) (potentialCover as java.io.File).absolutePath else (potentialCover as DocumentFile).uri.toString()
                }
            }
        }

        if (coverPath.isEmpty() || existingManga == null) {
            if (computedContentType == 1) {
                val pdfUriToExtract = if (isLocalMode) {
                    val pdfF = importResult!!.copiedLooseFiles.find { it.name.lowercase().endsWith(".pdf") }
                    if (pdfF != null && pdfF.isFile) Uri.fromFile(pdfF) else null
                } else {
                    if (isFile) {
                        rootUri
                    } else {
                        getRootDocFiles().find { it.name?.lowercase()?.endsWith(".pdf") == true }?.uri
                    }
                }
                
                if (pdfUriToExtract != null) {
                    val coverDest = File(context.filesDir, "pdf_cover_${System.currentTimeMillis()}.jpg")
                    val generatedCover = extractPdfCover(context, pdfUriToExtract, coverDest)
                    if (generatedCover != null) {
                        coverPath = generatedCover
                    }
                }
            }

            if (coverPath.isEmpty()) {
                if (isLocalMode) {
                    if (!isFile) {
                        val coverFile = importResult!!.copiedLooseFiles.find { 
                            it.name.lowercase().contains("cover") || 
                            imageExtensions.any { ext -> it.name.lowercase().endsWith(ext) }
                        }
                        if (coverFile != null) coverPath = coverFile.absolutePath
                    }
                } else {
                    if (!isFile) {
                        val coverFile = getRootDocFiles().find { !it.isDirectory && 
                            (it.name?.lowercase()?.contains("cover") == true || 
                             imageExtensions.any { ext -> it.name?.lowercase()?.endsWith(ext) == true }) 
                        }
                        if (coverFile != null) coverPath = coverFile.uri.toString()
                    }
                }
            }
        }

        // Keep coverPath empty instead of setting it to ic_menu_gallery resource.
        // This lets the UI handle placeholders dynamically and cleanly.

        if (isCancelled()) return@withContext null

        val mangaToInsert = MangaEntity(
            id = existingManga?.id ?: 0L,
            title = title,
            description = description,
            thoughts = thoughts,
            coverPath = coverPath,
            isNsfw = isNsfw,
            parentUri = if (isLocalMode) importResult!!.destinationFolder.absolutePath else rootUri.toString(),
            lastModified = lastMod,
            contentType = if (existingManga == null || existingManga.contentType == 0) computedContentType else existingManga.contentType,
            boxPurpose = existingManga?.boxPurpose ?: boxPurposeInput,
            boxLabel = existingManga?.boxLabel,
            workspace = workspace ?: existingManga?.workspace
        )
        
        val mangaId = libraryDao.insertManga(mangaToInsert)

        if (isLocalMode) {
            if (isFile && computedContentType == 1) {
                // No chapters for standalone PDF
            } else {
                val chaptersToInsert = if (computedContentType == 2) {
                    if (importResult!!.copiedLooseFiles.any { videoExtensions.any { ext -> it.name.lowercase().endsWith(ext) } }) {
                        importResult.copiedLooseFiles.filter { videoExtensions.any { ext -> it.name.lowercase().endsWith(ext) } }
                    } else importResult.copiedChapters
                } else if (computedContentType == 3) {
                    if (importResult!!.copiedLooseFiles.any { audioExtensions.any { ext -> it.name.lowercase().endsWith(ext) } }) {
                        importResult.copiedLooseFiles.filter { audioExtensions.any { ext -> it.name.lowercase().endsWith(ext) } }
                    } else importResult.copiedChapters
                } else {
                    if (importResult!!.copiedChapters.isNotEmpty()) {
                        importResult.copiedChapters
                    } else if (importResult.copiedLooseFiles.any { imageExtensions.any { ext -> it.name.lowercase().endsWith(ext) } }) {
                        listOf(importResult.destinationFolder)
                    } else {
                        importResult.copiedLooseFiles.filter { videoExtensions.any { ext -> it.name.lowercase().endsWith(ext) } }
                    }
                }.sortedWith(compareBy { item ->
                    val name = item.name
                    name.replace(Regex("\\d+")) { match ->
                        match.value.padStart(10, '0')
                    }
                })

                val semaphore = kotlinx.coroutines.sync.Semaphore(8)
                coroutineScope {
                    chaptersToInsert.mapIndexed { index, chapterItem ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                if (isCancelled()) return@withPermit
                                val existingChapter = libraryDao.getChapterByUri(chapterItem.absolutePath)
                                if (existingChapter == null) {
                                    val (artist, duration) = if (computedContentType == 3 && !chapterItem.isDirectory) {
                                        extractAudioMetadata(context, Uri.fromFile(chapterItem))
                                    } else null to 0L

                                    val thumb = if (computedContentType == 3 && !chapterItem.isDirectory) {
                                        extractAudioCover(context, Uri.fromFile(chapterItem))
                                    } else null

                                    val mediaFile = if ((computedContentType == 2 || computedContentType == 3) && chapterItem.isDirectory) {
                                        chapterItem.listFiles()?.firstOrNull { f ->
                                            val name = f.name.lowercase()
                                            if (computedContentType == 2) {
                                                videoExtensions.any { name.endsWith(it) }
                                            } else {
                                                audioExtensions.any { name.endsWith(it) }
                                            }
                                        }
                                    } else null
                                    val finalPath = mediaFile?.absolutePath ?: chapterItem.absolutePath

                                    libraryDao.insertChapter(
                                        ChapterEntity(
                                            mangaId = mangaId,
                                            title = if ((computedContentType == 2 || computedContentType == 3) && !chapterItem.isDirectory) extractVideoTitle(context, Uri.fromFile(chapterItem), chapterItem.name.substringBeforeLast(".")) else chapterItem.name,
                                            folderUri = finalPath,
                                            thumbnailUri = thumb,
                                            position = index,
                                            artist = artist,
                                            duration = duration
                                        )
                                    )
                                } else if (existingChapter.mangaId != mangaId) {
                                    libraryDao.insertChapter(existingChapter.copy(mangaId = mangaId, position = index))
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
        } else {
            // EXTERNAL MODE IMPROVEMENTS
            if (isFile && computedContentType == 1) {
                // No chapters
            } else if (isFile && (computedContentType == 2 || computedContentType == 3)) {
                val uriStr = rootDoc.uri.toString()
                val existingChapter = libraryDao.getChapterByUri(uriStr)
                if (existingChapter == null) {
                    val (artist, duration) = if (computedContentType == 3) {
                        extractAudioMetadata(context, rootDoc.uri)
                    } else null to 0L

                    val thumb = if (computedContentType == 3) {
                        extractAudioCover(context, rootDoc.uri)
                    } else null

                    libraryDao.insertChapter(
                        ChapterEntity(
                            mangaId = mangaId,
                            title = extractVideoTitle(context, rootDoc.uri, rootDoc.name?.substringBeforeLast(".") ?: "Unknown"),
                            folderUri = uriStr,
                            thumbnailUri = thumb,
                            artist = artist,
                            duration = duration
                        )
                    )
                }
                onProgress(100)
            } else {
                val itemsInRoot = getRootDocFiles()
                val subDirs = itemsInRoot.filter { it.isDirectory }
                val videoFiles = itemsInRoot.filter { file -> !file.isDirectory && videoExtensions.any { file.name?.lowercase()?.endsWith(it) == true } }
                val audioFiles = itemsInRoot.filter { file -> !file.isDirectory && audioExtensions.any { file.name?.lowercase()?.endsWith(it) == true } }
                val imageFiles = itemsInRoot.filter { file -> !file.isDirectory && imageExtensions.any { file.name?.lowercase()?.endsWith(it) == true } }

                val chaptersToInsert = if (computedContentType == 2) {
                    if (videoFiles.isNotEmpty()) videoFiles else subDirs
                } else if (computedContentType == 3) {
                    if (audioFiles.isNotEmpty()) audioFiles else subDirs
                } else {
                    if (subDirs.isNotEmpty()) subDirs else if (imageFiles.isNotEmpty()) listOf(rootDoc) else emptyList()
                }.sortedWith(compareBy { item ->
                    val name = item.name ?: ""
                    name.replace(Regex("\\d+")) { match ->
                        match.value.padStart(10, '0')
                    }
                })

                var indexed = 0
                val totalItems = chaptersToInsert.size
                val semaphore = kotlinx.coroutines.sync.Semaphore(8)
                coroutineScope {
                    chaptersToInsert.mapIndexed { index, chapterItem ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                if (isCancelled()) return@withPermit
                                val uriStr = chapterItem.uri.toString()
                                val existingChapter = libraryDao.getChapterByUri(uriStr)
                                if (existingChapter == null) {
                                    val (artist, duration) = if (computedContentType == 3 && !chapterItem.isDirectory) {
                                        extractAudioMetadata(context, chapterItem.uri)
                                    } else null to 0L

                                    val thumb = if (computedContentType == 3 && !chapterItem.isDirectory) {
                                        extractAudioCover(context, chapterItem.uri)
                                    } else null

                                    val mediaFileDoc = if ((computedContentType == 2 || computedContentType == 3) && chapterItem.isDirectory) {
                                        val extensions = if (computedContentType == 2) videoExtensions else audioExtensions
                                        chapterItem.listFiles().find { f ->
                                            f.name?.let { name -> extensions.any { name.lowercase().endsWith(it) } } == true
                                        }
                                    } else null
                                    val finalUriStr = mediaFileDoc?.uri?.toString() ?: uriStr

                                    libraryDao.insertChapter(
                                        ChapterEntity(
                                            mangaId = mangaId,
                                            title = if ((computedContentType == 2 || computedContentType == 3) && !chapterItem.isDirectory) extractVideoTitle(context, chapterItem.uri, chapterItem.name?.substringBeforeLast(".") ?: "Unknown") else chapterItem.name ?: "Unknown",
                                            folderUri = finalUriStr,
                                            thumbnailUri = thumb,
                                            position = index,
                                            artist = artist,
                                            duration = duration
                                        )
                                    )
                                } else if (existingChapter.mangaId != mangaId) {
                                    libraryDao.insertChapter(existingChapter.copy(mangaId = mangaId, position = index))
                                }
                                synchronized(this@LibraryRepository) {
                                    indexed++
                                    onProgress(if (totalItems > 0) ((indexed.toFloat() / totalItems) * 100).toInt() else 100)
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
        }
        if (isCancelled()) return@withContext null
        val sourceLoc = if (isLocalMode) {
            importResult?.destinationFolder?.absolutePath ?: "Private Vault"
        } else {
            rootUri.toString()
        }
        val typeStr = when (computedContentType) {
            1 -> "Book"
            2 -> "Video"
            3 -> "Music"
            else -> "Toon"
        }
        com.ballade.hwaran.data.local.HistoryTracker.logEvent("IMPORT", title, "$typeStr • $importMode | Source: $sourceLoc")
        return@withContext mangaId
    }

    suspend fun getChapterImages(chapterPath: String): List<String> = withContext(Dispatchers.IO) {
        if (chapterPath.startsWith("content://")) {
            val result = mutableListOf<String>()
            try {
                val uri = Uri.parse(chapterPath)
                val docId = android.provider.DocumentsContract.getDocumentId(uri)
                val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)
                
                val projection = arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                    
                    while (cursor.moveToNext()) {
                        val name = if (nameIndex != -1) cursor.getString(nameIndex) else null
                        val mime = if (mimeIndex != -1) cursor.getString(mimeIndex) else null
                        val childDocId = if (idIndex != -1) cursor.getString(idIndex) else null
                        
                        if (childDocId != null && mime != android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (name?.endsWith(".jpg", true) == true || name?.endsWith(".png", true) == true || name?.endsWith(".webp", true) == true) {
                                val childUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, childDocId)
                                result.add(childUri.toString())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext result.sortedBy { it.substringAfterLast("%2F").substringBeforeLast(".") } // Sort by numeric part if possible, otherwise string sort
        } else {
            val chapterDir = File(chapterPath)
            if (!chapterDir.exists() || !chapterDir.isDirectory) return@withContext emptyList()
            chapterDir.listFiles()
                ?.filter { !it.isDirectory && (it.name.endsWith(".jpg", true) || it.name.endsWith(".png", true) || it.name.endsWith(".webp", true)) }
                ?.sortedBy { it.name }
                ?.map { it.absolutePath }
                ?: emptyList()
        }
    }
}
