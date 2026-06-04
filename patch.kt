    private fun extractChapterMetadata(context: android.content.Context, chapterUri: String, contentType: Int): Pair<Long, String?> {
        if (contentType != 2 && contentType != 3) return 0L to null
        
        var targetFileUri = chapterUri
        if (!chapterUri.startsWith("content://")) {
            val file = java.io.File(chapterUri)
            if (file.isDirectory) {
                val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".webm", ".m4v", ".3gp", ".mov", ".flv")
                val audioExtensions = listOf(".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a")
                val exts = if (contentType == 2) videoExtensions else audioExtensions
                val mediaFile = file.listFiles()?.find { f -> exts.any { f.name.lowercase().endsWith(it) } }
                if (mediaFile != null) {
                    targetFileUri = mediaFile.absolutePath
                } else {
                    return 0L to null
                }
            }
        } else {
            val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(chapterUri))
            if (doc != null && doc.isDirectory) {
                val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".webm", ".m4v", ".3gp", ".mov", ".flv")
                val audioExtensions = listOf(".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a")
                val exts = if (contentType == 2) videoExtensions else audioExtensions
                val mediaDoc = doc.listFiles().find { f -> f.name != null && exts.any { f.name!!.lowercase().endsWith(it) } }
                if (mediaDoc != null) {
                    targetFileUri = mediaDoc.uri.toString()
                } else {
                    return 0L to null
                }
            }
        }

        try {
            val retriever = android.media.MediaMetadataRetriever()
            if (targetFileUri.startsWith("content://")) {
                retriever.setDataSource(context, android.net.Uri.parse(targetFileUri))
            } else {
                retriever.setDataSource(targetFileUri)
            }
            
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            var thumbUri: String? = null
            
            if (contentType == 2) {
                val seekMs = if (duration > 3_600_000L) 5_000L else duration / 2L
                val seekUs = seekMs * 1_000L
                val bitmap = retriever.embeddedPicture?.let { 
                    android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) 
                } ?: retriever.getFrameAtTime(seekUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                
                if (bitmap != null) {
                    val thumbFile = java.io.File(context.filesDir, "thumb_${System.currentTimeMillis()}.jpg")
                    thumbFile.outputStream().use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    thumbUri = thumbFile.absolutePath
                }
            } else {
                val art = retriever.embeddedPicture
                if (art != null) {
                    val coverFile = java.io.File(context.filesDir, "audio_cover_${System.currentTimeMillis()}.jpg")
                    coverFile.outputStream().use { it.write(art) }
                    thumbUri = coverFile.absolutePath
                }
            }
            retriever.release()
            return duration to thumbUri
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0L to null
    }
