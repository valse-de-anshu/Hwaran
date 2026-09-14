package com.ballade.hwaran.core.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object CoverCacheManager {

    fun getCoversDir(context: Context): File {
        return File(context.filesDir, "covers").apply {
            if (!exists()) mkdirs()
        }
    }

    fun sanitize(text: String): String {
        return text.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40)
    }

    /**
     * Copies an image from a content/file Uri into the app's internal covers directory.
     * Returns the absolute path of the cached file, or null on failure.
     */
    fun cacheCoverFromUri(context: Context, sourceUri: Uri, prefix: String, title: String): String? {
        return try {
            val cleanTitle = sanitize(title)
            val coversDir = getCoversDir(context)
            val targetFile = File(coversDir, "${prefix}_${System.currentTimeMillis()}_${cleanTitle}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0) {
                targetFile.absolutePath
            } else {
                targetFile.delete()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Extracts embedded album art from an audio Uri (MP3/FLAC/M4A/etc.) and saves it
     * into the app's internal covers directory.
     */
    fun extractAudioCover(context: Context, audioUri: Uri, title: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            context.contentResolver.openFileDescriptor(audioUri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
            }
            val art = retriever.embeddedPicture
            if (art != null && art.isNotEmpty()) {
                val cleanTitle = sanitize(title)
                val coversDir = getCoversDir(context)
                val targetFile = File(coversDir, "music_${System.currentTimeMillis()}_${cleanTitle}.jpg")
                FileOutputStream(targetFile).use { fos ->
                    fos.write(art)
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    targetFile.absolutePath
                } else null
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Extracts a video frame thumbnail at 1s and saves it into internal covers.
     */
    fun extractVideoCover(context: Context, videoUri: Uri, title: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            context.contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
            }
            val frame = retriever.getFrameAtTime(1000000) ?: retriever.frameAtTime
            if (frame != null) {
                val cleanTitle = sanitize(title)
                val coversDir = getCoversDir(context)
                val targetFile = File(coversDir, "vid_${System.currentTimeMillis()}_${cleanTitle}.jpg")
                FileOutputStream(targetFile).use { fos ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    targetFile.absolutePath
                } else null
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Saves a bitmap directly to internal covers.
     */
    fun cacheBitmap(context: Context, bitmap: Bitmap, prefix: String, title: String): String? {
        return try {
            val cleanTitle = sanitize(title)
            val coversDir = getCoversDir(context)
            val targetFile = File(coversDir, "${prefix}_${System.currentTimeMillis()}_${cleanTitle}.jpg")
            FileOutputStream(targetFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            if (targetFile.exists() && targetFile.length() > 0) {
                targetFile.absolutePath
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
