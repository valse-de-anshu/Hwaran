package com.ballade.hwaran.data.importer.toon

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.metadata.MediaMetadataManager
import com.ballade.hwaran.core.metadata.ZineMetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ToonExternalSingleImport {

    private data class DiscoveredExternalChapter(
        val title: String,
        val folderDoc: DocumentFile
    )

    suspend fun execute(
        context: Context,
        repository: ToonImportRepository,
        uri: Uri,
        workspace: String?,
        isNsfw: Boolean,
        boxPurpose: String?,
        importMode: String = "Single Import",
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit
    ): Long? = withContext(Dispatchers.IO) {

        val sourceDoc = if (uri.scheme == "file") {
            uri.path?.let { DocumentFile.fromFile(File(it)) }
        } else {
            DocumentFile.fromTreeUri(context, uri)
        } ?: return@withContext null
        val (isValid, _) = ToonImportUtils.isToonFolderValid(sourceDoc)
        if (!isValid) return@withContext null
        if (isCancelled()) return@withContext null

        val uriStr = uri.toString()
        val existingManga = repository.getRootMangaByUri(uriStr)

        val folderName = sourceDoc.name ?: "Unknown"

        // 1. Extract metadata from .zine/*.json or root *.json
        val parsedZine = ZineMetadataExtractor.extractFromDocumentFolder(context, sourceDoc)

        // Rule 4: Metadata overrides filesystem hints
        val finalTitle = parsedZine?.title?.takeIf { it.isNotBlank() } ?: existingManga?.title ?: folderName
        val finalDescription = parsedZine?.description?.takeIf { it.isNotBlank() } ?: existingManga?.description ?: "No description added yet."
        val finalTags = parsedZine?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: existingManga?.genre

        // 2. Dedicated Cover Detection (Rule 5)
        var coverPath = ""
        if (existingManga != null && existingManga.coverPath.isNotEmpty() && !existingManga.coverPath.startsWith("content://")) {
            val f = java.io.File(existingManga.coverPath)
            if (f.exists() && f.length() > 0) {
                coverPath = existingManga.coverPath
            }
        }

        if (coverPath.isEmpty()) {
            val coverDoc = ZineMetadataExtractor.findCoverInDocumentFolder(sourceDoc, parsedZine?.coverFileName)
            if (coverDoc != null) {
                coverPath = com.ballade.hwaran.core.util.CoverCacheManager.cacheCoverFromUri(context, coverDoc.uri, "toon", finalTitle)
                    ?: coverDoc.uri.toString()
            }
        }

        val mangaToInsert = MangaEntity(
            id = existingManga?.id ?: 0L,
            title = finalTitle,
            description = finalDescription,
            thoughts = existingManga?.thoughts ?: "No thoughts added.",
            coverPath = coverPath,
            isNsfw = existingManga?.isNsfw ?: isNsfw,
            parentUri = uriStr,
            lastModified = sourceDoc.lastModified(),
            contentType = 0, // 0 = Manga / Toon
            boxPurpose = existingManga?.boxPurpose ?: boxPurpose ?: parsedZine?.type,
            boxLabel = existingManga?.boxLabel,
            genre = finalTags,
            workspace = existingManga?.workspace ?: workspace
        )

        val mangaId = repository.insertManga(mangaToInsert)
        if (isCancelled()) return@withContext mangaId

        // 3. Media Hierarchy Discovery (Volume -> Chapter -> Page)
        val supportedExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
        val isSupportedImage: (DocumentFile) -> Boolean = { file ->
            val name = file.name ?: ""
            !name.startsWith(".") && supportedExtensions.contains(name.substringAfterLast(".", "").lowercase())
        }

        val rootFiles = sourceDoc.listFiles()
        val subDirs = rootFiles.filter { it.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(it.name) }
        val looseImages = rootFiles.filter {
            !it.isDirectory && isSupportedImage(it) && !ZineMetadataExtractor.isDedicatedCoverName(it.name)
        }

        val discoveredChapters = mutableListOf<DiscoveredExternalChapter>()

        for (subDir in subDirs) {
            val subChildren = subDir.listFiles()
            val childSubDirs = subChildren.filter { it.isDirectory && !ZineMetadataExtractor.isInternalOrAuxiliary(it.name) }
            val directImages = subChildren.filter { !it.isDirectory && isSupportedImage(it) && !ZineMetadataExtractor.isDedicatedCoverName(it.name) }

            if (childSubDirs.isNotEmpty()) {
                val volName = subDir.name ?: "Volume"
                for (chapDoc in childSubDirs) {
                    val chapImages = chapDoc.listFiles().filter { !it.isDirectory && isSupportedImage(it) }
                    if (chapImages.isNotEmpty()) {
                        val chapName = chapDoc.name ?: "Chapter"
                        val combinedTitle = if (chapName.contains(volName, ignoreCase = true)) chapName else "$volName - $chapName"
                        discoveredChapters.add(DiscoveredExternalChapter(combinedTitle, chapDoc))
                    }
                }
            } else if (directImages.isNotEmpty()) {
                val chapName = subDir.name ?: "Chapter"
                discoveredChapters.add(DiscoveredExternalChapter(chapName, subDir))
            }
        }

        if (discoveredChapters.isEmpty() && looseImages.isNotEmpty()) {
            discoveredChapters.add(DiscoveredExternalChapter(finalTitle, sourceDoc))
        }

        discoveredChapters.sortWith(compareBy { item ->
            item.title.replace(Regex("\\d+")) { match ->
                match.value.padStart(10, '0')
            }
        })

        // 4. Insert Chapter Entities
        val chapterEntities = mutableListOf<ChapterEntity>()
        discoveredChapters.forEachIndexed { index, chapterItem ->
            val chapterUriStr = chapterItem.folderDoc.uri.toString()
            val existingChapter = repository.getChapterByUri(chapterUriStr)
            if (existingChapter == null) {
                chapterEntities.add(
                    ChapterEntity(
                        mangaId = mangaId,
                        title = chapterItem.title,
                        folderUri = chapterUriStr,
                        position = index
                    )
                )
            } else {
                chapterEntities.add(
                    existingChapter.copy(
                        mangaId = mangaId,
                        title = chapterItem.title,
                        position = index
                    )
                )
            }
        }

        repository.insertChapters(chapterEntities)

        // 5. Cache Full Metadata
        if (parsedZine != null) {
            MediaMetadataManager.saveMetadata(
                context = context,
                mangaId = mangaId,
                parentUri = uriStr,
                metadata = parsedZine.toEntryMetadata(),
                forceWriteToFile = false
            )
        }

        com.ballade.hwaran.core.util.HistoryTracker.logEvent(
            "IMPORT",
            mangaToInsert.title,
            "Toon • $importMode | Source: $uriStr"
        )

        return@withContext mangaId
    }
}
