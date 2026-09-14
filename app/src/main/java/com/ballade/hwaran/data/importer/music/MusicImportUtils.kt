package com.ballade.hwaran.data.importer.music

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

object MusicImportUtils {

    private val VALID_AUDIO_EXTENSIONS = listOf("mp3", "flac", "wav", "ogg", "m4a", "opus", "aac")
    private val VALID_IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "gif")
    private val PREFERRED_COVER_NAMES = listOf("cover", "folder", "album")
    val VALID_LYRICS_EXTENSIONS = listOf("lrc", "txt", "ser", "usf")

    fun isAuxiliaryFolder(name: String): Boolean {
        if (com.ballade.hwaran.core.metadata.ZineMetadataExtractor.isInternalOrAuxiliary(name)) return true
        val lower = name.lowercase().trim()
        return lower in listOf("lyrics", "lyric", "lrc", "covers", "cover", "artwork", "art", "scans", "scan", "extras", "extra")
    }

    fun detectStructure(folder: DocumentFile): String {
        val files = folder.listFiles() ?: emptyArray()
        val nonAuxSubdirs = files.filter { file ->
            file.isDirectory &&
            !file.name.orEmpty().startsWith(".") &&
            !isAuxiliaryFolder(file.name.orEmpty())
        }

        val hasAlbumSubdirectories = nonAuxSubdirs.any { subDir ->
            findAudioFiles(subDir).isNotEmpty() ||
            (subDir.listFiles()?.any { it.isDirectory && !it.name.orEmpty().startsWith(".") } == true)
        }

        return if (hasAlbumSubdirectories) "MEGA" else "SINGLE"
    }

    fun detectStructure(folder: File): String {
        val files = folder.listFiles() ?: emptyArray()
        val nonAuxSubdirs = files.filter { file ->
            file.isDirectory &&
            !file.name.startsWith(".") &&
            !isAuxiliaryFolder(file.name)
        }

        val hasAlbumSubdirectories = nonAuxSubdirs.any { subDir ->
            findAudioFiles(subDir).isNotEmpty() ||
            (subDir.listFiles()?.any { it.isDirectory && !it.name.startsWith(".") } == true)
        }

        return if (hasAlbumSubdirectories) "MEGA" else "SINGLE"
    }

    fun isValidAudioFile(file: File): Boolean {
        if (file.isDirectory) return false
        val name = file.name
        if (name.startsWith(".")) return false
        val ext = file.extension.lowercase()
        return ext in VALID_AUDIO_EXTENSIONS
    }

    fun findAudioFiles(folder: File): List<File> {
        val files = folder.listFiles() ?: emptyArray()
        return files.filter { isValidAudioFile(it) }.sortedBy { it.name.lowercase() }
    }

    fun isValidAudioFile(file: DocumentFile): Boolean {
        if (file.isDirectory) return false
        val name = file.name ?: return false
        if (name.startsWith(".")) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VALID_AUDIO_EXTENSIONS
    }

    fun findAudioFiles(folder: DocumentFile): List<DocumentFile> {
        val files = folder.listFiles() ?: emptyArray()
        return files.filter { isValidAudioFile(it) }.sortedBy { it.name?.lowercase() ?: "" }
    }

    /**
     * Scans for lyrics files based on the 2 viable cases:
     * Case 1: Music and lyrics files are side by side in the same folder.
     * Case 2: A folder named "lyrics" sits alongside music, containing lyrics files for the tracks.
     * Supported extensions: .lrc, .txt, .ser, .usf
     */
    val LYRICS_FOLDER_NAMES = setOf("lyrics", "lyric", "lrc", "lrcs")

    fun findLyricsFiles(folder: DocumentFile): List<DocumentFile> {
        val files = folder.listFiles() ?: emptyArray()
        val result = mutableListOf<DocumentFile>()

        // Case 1: Side-by-side lyrics in the same folder
        for (file in files) {
            if (!file.isDirectory) {
                val ext = file.name?.substringAfterLast('.', "")?.lowercase().orEmpty()
                if (ext in VALID_LYRICS_EXTENSIONS) {
                    result.add(file)
                }
            }
        }

        // Case 2: In a folder we have music, then lyrics folder alongside music with lyrics inside
        val lyricsFolder = files.firstOrNull { it.isDirectory && it.name?.lowercase() in LYRICS_FOLDER_NAMES }
        if (lyricsFolder != null) {
            val subFiles = lyricsFolder.listFiles() ?: emptyArray()
            for (file in subFiles) {
                if (!file.isDirectory) {
                    val ext = file.name?.substringAfterLast('.', "")?.lowercase().orEmpty()
                    if (ext in VALID_LYRICS_EXTENSIONS) {
                        result.add(file)
                    }
                }
            }
        }

        return result
    }

    /**
     * Local java.io.File version of findLyricsFiles.
     */
    fun findLyricsFiles(folder: File): List<File> {
        val files = folder.listFiles() ?: emptyArray()
        val result = mutableListOf<File>()

        // Case 1: Side-by-side
        for (file in files) {
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in VALID_LYRICS_EXTENSIONS) {
                    result.add(file)
                }
            }
        }

        // Case 2: lyrics folder alongside
        val lyricsFolder = files.firstOrNull { it.isDirectory && it.name.lowercase() in LYRICS_FOLDER_NAMES }
        if (lyricsFolder != null) {
            val subFiles = lyricsFolder.listFiles() ?: emptyArray()
            for (file in subFiles) {
                if (file.isFile) {
                    val ext = file.extension.lowercase()
                    if (ext in VALID_LYRICS_EXTENSIONS) {
                        result.add(file)
                    }
                }
            }
        }

        return result
    }

    private fun cleanTitleForMatching(name: String): String {
        return name.trim()
            .replace(Regex("^[0-9]+[\\s._\\-]+"), "") // strip track prefixes like "01 - "
            .replace(Regex("[_\\-]+"), " ")
            .trim()
            .lowercase()
    }

    fun findMatchingLyricsDoc(
        audioFileName: String,
        metaTitle: String,
        lyricsFiles: List<DocumentFile>
    ): DocumentFile? {
        if (lyricsFiles.isEmpty()) return null
        val audioBase = audioFileName.substringBeforeLast('.')
        val cleanAudioBase = cleanTitleForMatching(audioBase)
        val cleanMeta = cleanTitleForMatching(metaTitle)

        val extPriority = mapOf("lrc" to 0, "txt" to 1, "ser" to 2, "usf" to 3)
        val sortedLyrics = lyricsFiles.sortedBy { extPriority[it.name?.substringAfterLast('.', "")?.lowercase()] ?: 99 }

        // 1. Exact base name match (case-insensitive)
        sortedLyrics.firstOrNull {
            val base = it.name?.substringBeforeLast('.').orEmpty()
            base.equals(audioBase, ignoreCase = true)
        }?.let { return it }

        // 2. Cleaned audio file base match
        if (cleanAudioBase.isNotBlank()) {
            sortedLyrics.firstOrNull {
                val base = it.name?.substringBeforeLast('.').orEmpty()
                cleanTitleForMatching(base) == cleanAudioBase
            }?.let { return it }
        }

        // 3. Metadata title match
        if (cleanMeta.isNotBlank()) {
            sortedLyrics.firstOrNull {
                val base = it.name?.substringBeforeLast('.').orEmpty()
                cleanTitleForMatching(base) == cleanMeta || base.equals(metaTitle, ignoreCase = true)
            }?.let { return it }
        }

        return null
    }

    fun findMatchingLyricsFile(
        audioFileName: String,
        metaTitle: String,
        lyricsFiles: List<File>
    ): File? {
        if (lyricsFiles.isEmpty()) return null
        val audioBase = audioFileName.substringBeforeLast('.')
        val cleanAudioBase = cleanTitleForMatching(audioBase)
        val cleanMeta = cleanTitleForMatching(metaTitle)

        val extPriority = mapOf("lrc" to 0, "txt" to 1, "ser" to 2, "usf" to 3)
        val sortedLyrics = lyricsFiles.sortedBy { extPriority[it.extension.lowercase()] ?: 99 }

        // 1. Exact base name match
        sortedLyrics.firstOrNull {
            it.nameWithoutExtension.equals(audioBase, ignoreCase = true)
        }?.let { return it }

        // 2. Cleaned audio file base match
        if (cleanAudioBase.isNotBlank()) {
            sortedLyrics.firstOrNull {
                cleanTitleForMatching(it.nameWithoutExtension) == cleanAudioBase
            }?.let { return it }
        }

        // 3. Metadata title match
        if (cleanMeta.isNotBlank()) {
            sortedLyrics.firstOrNull {
                cleanTitleForMatching(it.nameWithoutExtension) == cleanMeta || it.nameWithoutExtension.equals(metaTitle, ignoreCase = true)
            }?.let { return it }
        }

        return null
    }

    fun readLyrics(context: Context, lyricsDoc: DocumentFile): String? {
        return try {
            context.contentResolver.openInputStream(lyricsDoc.uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                if (bytes.isEmpty()) null
                else {
                    try {
                        String(bytes, java.nio.charset.StandardCharsets.UTF_8).removePrefix("\uFEFF")
                    } catch (e: Exception) {
                        String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun readLyrics(file: File): String? {
        return try {
            val bytes = file.readBytes()
            if (bytes.isEmpty()) null
            else {
                try {
                    String(bytes, java.nio.charset.StandardCharsets.UTF_8).removePrefix("\uFEFF")
                } catch (e: Exception) {
                    String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun extractEmbeddedCover(context: Context, audioUri: Uri, albumTitle: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            context.contentResolver.openFileDescriptor(audioUri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
            }
            val art = retriever.embeddedPicture
            if (art != null) {
                // Save to private cache
                val vaultBase = File(context.filesDir, "music_covers")
                if (!vaultBase.exists()) vaultBase.mkdirs()
                val thumbFile = File(vaultBase, "${albumTitle}_embedded_${System.currentTimeMillis()}.jpg")
                FileOutputStream(thumbFile).use { fos ->
                    fos.write(art)
                }
                thumbFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun findCoverImage(context: Context, folder: DocumentFile, audioFiles: List<DocumentFile>, albumTitle: String): String {
        // Priority 1: External image file (cover.jpg, folder.jpg, etc.)
        val files = folder.listFiles() ?: emptyArray()
        for (file in files) {
            val name = file.name?.lowercase() ?: continue
            val nameWithoutExt = name.substringBeforeLast('.')
            val ext = name.substringAfterLast('.')
            if (ext in VALID_IMAGE_EXTENSIONS && PREFERRED_COVER_NAMES.any { nameWithoutExt.contains(it) }) {
                val cached = com.ballade.hwaran.core.util.CoverCacheManager.cacheCoverFromUri(context, file.uri, "music", albumTitle)
                return cached ?: file.uri.toString()
            }
        }

        // Priority 2: Extract embedded cover from audio files
        for (audioDoc in audioFiles.take(5)) {
            val extracted = extractEmbeddedCover(context, audioDoc.uri, albumTitle)
            if (!extracted.isNullOrBlank()) {
                return extracted
            }
        }

        // Fallback: Default to empty if no image file is found
        return ""
    }

    data class TrackMetadata(
        val title: String, 
        val duration: Long, 
        val artist: String?, 
        val coverPath: String?,
        val lyrics: String? = null
    )

    fun extractTrackMetadata(context: Context, audioUri: Uri, fallbackName: String): TrackMetadata {
        val retriever = MediaMetadataRetriever()
        var title = fallbackName.substringBeforeLast('.')
        var duration = 0L
        var artist: String? = null
        var coverPath: String? = null
        var lyrics: String? = null
        try {
            context.contentResolver.openFileDescriptor(audioUri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
            }
            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!metaTitle.isNullOrBlank()) {
                title = metaTitle
            }
            val metaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!metaDuration.isNullOrBlank()) {
                duration = metaDuration.toLongOrNull() ?: 0L
            }
            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            if (!metaArtist.isNullOrBlank()) {
                artist = metaArtist
            }
            
            val art = retriever.embeddedPicture
            if (art != null) {
                val vaultBase = File(context.filesDir, "music_covers")
                if (!vaultBase.exists()) vaultBase.mkdirs()
                val safeTitle = fallbackName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                val thumbFile = File(vaultBase, "track_${System.currentTimeMillis()}_${safeTitle}.jpg")
                FileOutputStream(thumbFile).use { fos ->
                    fos.write(art)
                }
                coverPath = thumbFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        return TrackMetadata(title, duration, artist, coverPath, lyrics)
    }
}
