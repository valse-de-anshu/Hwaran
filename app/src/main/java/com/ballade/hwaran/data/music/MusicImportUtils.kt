package com.ballade.hwaran.data.music

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

    fun detectStructure(folder: DocumentFile): String {
        val files = folder.listFiles() ?: emptyArray()
        val hasDirectories = files.any { it.isDirectory && !it.name.orEmpty().startsWith(".") }
        return if (hasDirectories) "MEGA" else "SINGLE"
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
                return file.uri.toString()
            }
        }

        // Fallback: Default to music icon if no image file is found
        return ""
    }

    data class TrackMetadata(val title: String, val duration: Long, val artist: String?, val coverPath: String?)

    fun extractTrackMetadata(context: Context, audioUri: Uri, fallbackName: String): TrackMetadata {
        val retriever = MediaMetadataRetriever()
        var title = fallbackName.substringBeforeLast('.')
        var duration = 0L
        var artist: String? = null
        var coverPath: String? = null
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
        return TrackMetadata(title, duration, artist, coverPath)
    }
}
