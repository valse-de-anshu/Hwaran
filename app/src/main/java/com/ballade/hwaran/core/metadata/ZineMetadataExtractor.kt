package com.ballade.hwaran.core.metadata

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Parsed metadata representation from a .zine JSON or root JSON file.
 */
data class ParsedZineMetadata(
    val title: String? = null,
    val altTitle: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val type: String? = null,
    val status: String? = null,
    val rating: String? = null,
    val tags: List<String> = emptyList(),
    val publisher: String? = null,
    val serialization: String? = null,
    val year: String? = null,
    val language: String? = null,
    val pages: String? = null,
    val totalChapters: Int = 0,
    val coverFileName: String? = null,
    val rawJson: String? = null,
    val url: String? = null,
    val views: String? = null,
    val likes: String? = null,
    val comments: String? = null,
    val videoItems: List<VideoItemMetadata> = emptyList()
) {
    fun toEntryMetadata(existing: EntryMetadata? = null): EntryMetadata {
        return EntryMetadata(
            title = title?.takeIf { it.isNotBlank() } ?: existing?.title ?: "",
            altTitle = altTitle?.takeIf { it.isNotBlank() } ?: existing?.altTitle ?: "",
            author = author?.takeIf { it.isNotBlank() } ?: existing?.author ?: "",
            artist = artist?.takeIf { it.isNotBlank() } ?: existing?.artist ?: "",
            description = description?.takeIf { it.isNotBlank() } ?: existing?.description ?: "",
            type = type?.takeIf { it.isNotBlank() } ?: existing?.type ?: "",
            status = status?.takeIf { it.isNotBlank() } ?: existing?.status ?: "",
            rating = rating?.takeIf { it.isNotBlank() } ?: existing?.rating ?: "",
            tags = if (tags.isNotEmpty()) tags else existing?.tags ?: emptyList(),
            publisher = publisher?.takeIf { it.isNotBlank() } ?: existing?.publisher ?: "",
            serialization = serialization?.takeIf { it.isNotBlank() } ?: existing?.serialization ?: "",
            year = year?.takeIf { it.isNotBlank() } ?: existing?.year ?: "",
            language = language?.takeIf { it.isNotBlank() } ?: existing?.language ?: "",
            pages = pages?.takeIf { it.isNotBlank() } ?: existing?.pages ?: "",
            totalChapters = if (totalChapters > 0) totalChapters else existing?.totalChapters ?: 0,
            isFavorite = existing?.isFavorite ?: false,
            url = url?.takeIf { it.isNotBlank() } ?: existing?.url ?: "",
            views = views?.takeIf { it.isNotBlank() } ?: existing?.views ?: "",
            likes = likes?.takeIf { it.isNotBlank() } ?: existing?.likes ?: "",
            comments = comments?.takeIf { it.isNotBlank() } ?: existing?.comments ?: "",
            videoItems = videoItems.ifEmpty { existing?.videoItems ?: emptyList() }
        )
    }
}

/**
 * ZineMetadataExtractor
 *
 * Implements Hwaran Import & Metadata Rules:
 * Priority:
 * 1. Material/.zine/ (JSON)
 * 2. Material/ (root JSON)
 * 3. Filesystem structure
 * 4. Filename hints
 *
 * Filename does NOT matter — any .json file is accepted.
 * Rules:
 * - Covers are never media items (Rule 5).
 * - .zine and .json are internal only (Rule 7).
 * - Metadata overrides filesystem hints (Rule 4).
 */
object ZineMetadataExtractor {

    private val PREFERRED_JSON_NAMES = listOf(
        "metadata.json", "meta.json", "info.json", "entry.json", "data.json",
        "series.json", "album.json", "book.json", "artist.json"
    )

    private val IGNORED_JSON_NAMES = setOf(
        "history.json", "download_history.json", "batch_history.json", "url_history.json", "history.bak"
    )

    fun isIgnoredJson(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase().trim()
        if (lower in IGNORED_JSON_NAMES) return true
        if (lower.endsWith(".history.json") || lower.endsWith("_history.json") || lower == "history.json") return true
        return false
    }

    private val DEDICATED_COVER_NAMES = setOf(
        "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
        "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
        "poster.jpg", "poster.jpeg", "poster.png", "poster.webp",
        "thumb.jpg", "thumb.jpeg", "thumb.png", "thumb.webp"
    )

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    /**
     * Checks whether a file or directory is internal metadata (.zine, *.json, .nomedia, *.lrc, hidden)
     * and should NEVER be imported as media content.
     */
    fun isInternalOrAuxiliary(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase().trim()
        if (lower == ".zine" || lower.startsWith(".zine/") || lower.startsWith(".zine\\")) return true
        if (isIgnoredJson(lower)) return true
        if (lower.endsWith(".json")) return true
        if (lower == ".nomedia") return true
        if (lower.endsWith(".lrc")) return true
        if (lower.startsWith(".") && lower != "." && lower != "..") return true
        return false
    }

    /**
     * Checks if a filename is a dedicated cover image (Rule 5).
     */
    fun isDedicatedCoverName(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase().trim()
        if (lower in DEDICATED_COVER_NAMES) return true
        val baseName = lower.substringBeforeLast(".")
        val ext = lower.substringAfterLast(".", "")
        return ext in IMAGE_EXTENSIONS && (
            baseName == "cover" || baseName == "folder" ||
            baseName == "poster" || baseName == "thumb" ||
            baseName.startsWith("cover_") || baseName.startsWith("poster_")
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Local File Support (java.io.File)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Finds and parses metadata JSON from a local folder.
     * Checks Material/.zine/ first, then Material root JSON.
     */
    fun extractFromFolder(folder: File): ParsedZineMetadata? {
        if (!folder.exists() || !folder.isDirectory) return null

        // 1. Look in .zine/
        val zineDir = folder.listFiles()?.firstOrNull { it.isDirectory && it.name.equals(".zine", ignoreCase = true) }
        if (zineDir != null) {
            val jsonFile = findJsonFileInDir(zineDir)
            if (jsonFile != null) {
                try {
                    val content = jsonFile.readText()
                    val parsed = parseJson(content)
                    if (parsed != null) return parsed
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 2. Look in root folder
        var rootParsed: ParsedZineMetadata? = null
        val rootJsonFile = findJsonFileInDir(folder)
        if (rootJsonFile != null) {
            try {
                val content = rootJsonFile.readText()
                rootParsed = parseJson(content)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (rootParsed != null && rootParsed.videoItems.isNotEmpty()) {
            return rootParsed
        }

        // 3. Look in immediate child subfolders (e.g. video/, episodes/, etc.)
        val children = folder.listFiles() ?: emptyArray()
        for (child in children) {
            if (child.isDirectory && !isInternalOrAuxiliary(child.name)) {
                val subParsed = extractFromFolder(child)
                if (subParsed != null && subParsed.videoItems.isNotEmpty()) {
                    return if (rootParsed != null) {
                        rootParsed.copy(
                            videoItems = subParsed.videoItems,
                            views = if (rootParsed.views.isNullOrBlank()) subParsed.views else rootParsed.views,
                            likes = if (rootParsed.likes.isNullOrBlank()) subParsed.likes else rootParsed.likes,
                            comments = if (rootParsed.comments.isNullOrBlank()) subParsed.comments else rootParsed.comments
                        )
                    } else {
                        subParsed
                    }
                }
            }
        }

        return rootParsed
    }

    fun findJsonFileInDir(dir: File): File? {
        val jsonFiles = dir.listFiles()?.filter {
            it.isFile && it.name.lowercase().endsWith(".json") && !isIgnoredJson(it.name)
        } ?: return null
        if (jsonFiles.isEmpty()) return null

        // Sort: preferred names first, then alphabetical
        return jsonFiles.sortedWith(compareBy<File> { file ->
            val idx = PREFERRED_JSON_NAMES.indexOf(file.name.lowercase())
            if (idx >= 0) idx else 1000
        }.thenBy { it.name.lowercase() }).firstOrNull()
    }

    /**
     * Resolves the target metadata JSON file for a local media folder:
     * 1. If .zine/ exists:
     *    - If it contains any .json file, return that file.
     *    - If empty, target .zine/metadata.json.
     * 2. Else if root folder contains any .json file, return that file.
     * 3. Else fallback to folder/entry.json.
     */
    fun findMetadataFile(dir: File): File {
        val zineDir = dir.listFiles()?.firstOrNull { it.isDirectory && it.name.equals(".zine", ignoreCase = true) }
        if (zineDir != null) {
            val existingJson = findJsonFileInDir(zineDir)
            if (existingJson != null) return existingJson
            return File(zineDir, "metadata.json")
        }
        val rootJson = findJsonFileInDir(dir)
        if (rootJson != null) return rootJson

        return File(dir, "entry.json")
    }

    /**
     * Finds dedicated cover image in a local folder.
     * Priority:
     * 1. Check if metadata specified cover filename exists
     * 2. Check .zine/ for cover/poster/image
     * 3. Check root folder for cover.jpg, folder.png, poster.webp, thumb.jpg
     */
    fun findCoverInFolder(folder: File, specifiedCoverName: String? = null): File? {
        if (!folder.exists() || !folder.isDirectory) return null

        val zineDir = folder.listFiles()?.firstOrNull { it.isDirectory && it.name.equals(".zine", ignoreCase = true) }

        // 1. Specified cover name from metadata
        if (!specifiedCoverName.isNullOrBlank()) {
            val directFile = File(folder, specifiedCoverName)
            if (directFile.exists() && directFile.isFile) return directFile

            if (zineDir != null) {
                val zineCover = File(zineDir, specifiedCoverName)
                if (zineCover.exists() && zineCover.isFile) return zineCover
            }
        }

        // 2. Look inside .zine/ for cover images
        if (zineDir != null) {
            val zineImages = zineDir.listFiles()?.filter {
                it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS
            } ?: emptyList()

            // Look for dedicated name in .zine first
            val zineNamed = zineImages.firstOrNull { isDedicatedCoverName(it.name) }
            if (zineNamed != null) return zineNamed

            // Any image in .zine is a cover
            if (zineImages.isNotEmpty()) return zineImages.first()
        }

        // 3. Look in root folder for dedicated cover names strictly
        val rootFiles = folder.listFiles() ?: emptyArray()
        val dedicated = rootFiles.firstOrNull { it.isFile && isDedicatedCoverName(it.name) }
        if (dedicated != null) return dedicated

        return null
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SAF DocumentFile Support
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Finds and parses metadata JSON from a DocumentFile directory.
     * Checks Material/.zine/ first, then Material root JSON.
     */
    fun extractFromDocumentFolder(context: Context, folderDoc: DocumentFile): ParsedZineMetadata? {
        if (!folderDoc.isDirectory) return null
        val children = folderDoc.listFiles()

        // 1. Look in .zine/
        val zineDoc = children.firstOrNull { it.isDirectory && it.name.equals(".zine", ignoreCase = true) }
        if (zineDoc != null) {
            val jsonDoc = findJsonDocInDir(zineDoc)
            if (jsonDoc != null) {
                val content = readDocText(context, jsonDoc)
                if (!content.isNullOrBlank()) {
                    val parsed = parseJson(content)
                    if (parsed != null) return parsed
                }
            }
        }

        // 2. Look in root folder
        var rootParsed: ParsedZineMetadata? = null
        val rootJsonDoc = findJsonDocInDir(folderDoc)
        if (rootJsonDoc != null) {
            val content = readDocText(context, rootJsonDoc)
            if (!content.isNullOrBlank()) {
                rootParsed = parseJson(content)
            }
        }

        if (rootParsed != null && rootParsed.videoItems.isNotEmpty()) {
            return rootParsed
        }

        // 3. Look in immediate child subfolders (e.g. video/, episodes/, etc.)
        for (child in children) {
            if (child.isDirectory && !isInternalOrAuxiliary(child.name)) {
                val subParsed = extractFromDocumentFolder(context, child)
                if (subParsed != null && subParsed.videoItems.isNotEmpty()) {
                    return if (rootParsed != null) {
                        rootParsed.copy(
                            videoItems = subParsed.videoItems,
                            views = if (rootParsed.views.isNullOrBlank()) subParsed.views else rootParsed.views,
                            likes = if (rootParsed.likes.isNullOrBlank()) subParsed.likes else rootParsed.likes,
                            comments = if (rootParsed.comments.isNullOrBlank()) subParsed.comments else rootParsed.comments
                        )
                    } else {
                        subParsed
                    }
                }
            }
        }

        return rootParsed
    }

    fun findJsonDocInDir(dirDoc: DocumentFile): DocumentFile? {
        val files = dirDoc.listFiles().filter {
            !it.isDirectory && it.name?.lowercase()?.endsWith(".json") == true && !isIgnoredJson(it.name)
        }
        if (files.isEmpty()) return null

        return files.sortedWith(compareBy<DocumentFile> { doc ->
            val idx = PREFERRED_JSON_NAMES.indexOf(doc.name?.lowercase().orEmpty())
            if (idx >= 0) idx else 1000
        }.thenBy { doc -> doc.name?.lowercase().orEmpty() }).firstOrNull()
    }

    /**
     * Resolves or creates the target metadata JSON DocumentFile for a SAF directory:
     * 1. If .zine/ directory exists:
     *    - If it contains any .json file, return that DocumentFile.
     *    - If empty, create/return .zine/metadata.json.
     * 2. Else if root directory contains any .json file, return that DocumentFile.
     * 3. Else create/return root/entry.json.
     */
    fun findOrCreateMetadataDoc(dirDoc: DocumentFile): DocumentFile? {
        if (!dirDoc.isDirectory) return null
        val zineDoc = dirDoc.listFiles().firstOrNull { it.isDirectory && it.name.equals(".zine", ignoreCase = true) }
        if (zineDoc != null) {
            val existingJson = findJsonDocInDir(zineDoc)
            if (existingJson != null) return existingJson
            return zineDoc.findFile("metadata.json") ?: zineDoc.createFile("application/json", "metadata.json")
        }
        val rootJson = findJsonDocInDir(dirDoc)
        if (rootJson != null) return rootJson

        return dirDoc.findFile("entry.json") ?: dirDoc.createFile("application/json", "entry.json")
    }

    fun readDocText(context: Context, doc: DocumentFile): String? {
        return try {
            context.contentResolver.openInputStream(doc.uri)?.use { stream ->
                stream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Finds dedicated cover image in a DocumentFile directory.
     * Priority:
     * 1. Check if metadata specified cover filename exists
     * 2. Check .zine/ for cover/poster/image
     * 3. Check root folder for cover.jpg, folder.png, poster.webp, thumb.jpg
     */
    fun findCoverInDocumentFolder(folderDoc: DocumentFile, specifiedCoverName: String? = null): DocumentFile? {
        if (!folderDoc.isDirectory) return null
        val children = folderDoc.listFiles()
        val zineDoc = children.firstOrNull { it.isDirectory && it.name.equals(".zine", ignoreCase = true) }

        // 1. Specified cover name from metadata
        if (!specifiedCoverName.isNullOrBlank()) {
            val direct = children.firstOrNull { !it.isDirectory && it.name.equals(specifiedCoverName, ignoreCase = true) }
            if (direct != null) return direct

            if (zineDoc != null) {
                val zineDirect = zineDoc.listFiles().firstOrNull { !it.isDirectory && it.name.equals(specifiedCoverName, ignoreCase = true) }
                if (zineDirect != null) return zineDirect
            }
        }

        // 2. Look inside .zine/ for cover images
        if (zineDoc != null) {
            val zineFiles = zineDoc.listFiles()
            val zineImages = zineFiles.filter { doc ->
                !doc.isDirectory && IMAGE_EXTENSIONS.contains(doc.name?.substringAfterLast('.', "")?.lowercase())
            }
            val zineNamed = zineImages.firstOrNull { isDedicatedCoverName(it.name) }
            if (zineNamed != null) return zineNamed
            if (zineImages.isNotEmpty()) return zineImages.first()
        }

        // 3. Look in root folder for dedicated cover names strictly
        val dedicated = children.firstOrNull { doc ->
            !doc.isDirectory && isDedicatedCoverName(doc.name)
        }
        if (dedicated != null) return dedicated

        return null
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Flexible JSON Parser
    // ─────────────────────────────────────────────────────────────────────────────

    fun parseJson(jsonString: String): ParsedZineMetadata? {
        return try {
            val obj = JSONObject(jsonString.trim().removePrefix("\uFEFF"))

            // Title resolution (multiple field name candidates across books, manga, channels, video creators)
            val title = optFirstString(
                obj,
                "title", "name", "series_title", "seriesTitle",
                "book_title", "bookTitle", "album_title", "albumTitle", "album",
                "model_name", "modelName", "model", "channel_name", "channelName", "channel",
                "creator_name", "creatorName"
            )

            // Alt title resolution
            val altTitle = optFirstString(
                obj,
                "altTitle", "alt_title", "alternative_title", "alternativeTitle",
                "nativeTitle", "native_title", "original_title", "originalTitle",
                "japanese_title", "korean_title", "romaji_title", "english_title",
                "handle", "channel_handle", "username"
            )

            // Description resolution
            val description = optFirstString(
                obj,
                "description", "synopsis", "summary", "overview", "intro", "about", "bio"
            )

            // Author resolution
            val author = optFirstStringOrArray(
                obj,
                "author", "writer", "creator", "author_name", "authorName", "authors",
                "model_name", "modelName", "model", "channel_name", "channelName", "channel", "uploader"
            )

            // Artist resolution
            val artist = optFirstStringOrArray(
                obj,
                "artist", "illustrator", "penciller", "artists", "studio", "singer", "band"
            )

            // Type resolution
            val type = optFirstString(
                obj,
                "type", "box_purpose", "boxPurpose", "media_type", "mediaType", "kind"
            )

            // Status resolution
            val status = optFirstString(
                obj,
                "status", "publication_status", "publicationStatus", "series_status", "seriesStatus"
            )

            // Rating resolution
            val rating = optFirstString(obj, "rating", "score")

            // Tags & Genres resolution (collect and merge)
            val tags = extractTagsAndGenres(obj)

            // Publisher resolution
            val publisher = optFirstString(
                obj,
                "publisher", "studio", "network", "label", "imprint"
            )

            // Serialization / Platform / Source resolution
            val serialization = optFirstString(
                obj,
                "serialization", "magazine", "platform", "source", "site", "website_name"
            )

            // Year / Release date resolution
            val year = optFirstString(
                obj,
                "year", "release_year", "releaseYear", "release_date", "releaseDate",
                "date", "published", "release"
            )

            // Language resolution
            val language = optFirstString(obj, "language", "lang")

            // Pages resolution
            val pages = optFirstString(obj, "pages", "pageCount", "page_count")

            // Parse any video / chapter lists generically (e.g. most_viewed, top_rated, latest, longest, videos, items)
            val videoItems = extractVideoItems(obj)

            // Total chapters / episodes resolution
            val totalChaptersRaw = optFirstInt(
                obj,
                "totalChapters", "total_chapters", "totalEpisodes", "total_episodes",
                "total_videos", "totalVideos", "total_tracks", "totalTracks",
                "videos_count", "videoCount", "episodes", "chapters", "total_items"
            )
            val totalChapters = if (totalChaptersRaw > 0) totalChaptersRaw else videoItems.size

            // Cover file name specified in JSON
            val coverFileName = optFirstString(
                obj,
                "cover", "cover_image", "coverImage", "cover_art", "coverArt",
                "poster", "thumbnail", "image"
            )

            // URL / source link resolution (must look like a link or URL, not just a plain name)
            val url = optFirstUrl(
                obj,
                "url", "link", "source_url", "sourceUrl",
                "channelUrl", "channel_url", "seriesUrl", "series_url",
                "websiteUrl", "website_url", "website", "homepage", "webpage", "source"
            )

            // Online statistics (stored as strings to preserve human-readable formats like "1.2M")
            var views = optFirstStringOrNumber(obj, "views", "view_count", "viewCount", "watchCount", "watch_count", "total_views")
            if (views.isNullOrBlank() && videoItems.isNotEmpty()) {
                val totalV = videoItems.sumOf { it.viewCount }
                if (totalV > 0L) {
                    views = when {
                        totalV >= 1_000_000 -> String.format("%.1fM", totalV / 1_000_000.0)
                        totalV >= 1_000 -> String.format("%.1fK", totalV / 1_000.0)
                        else -> totalV.toString()
                    }
                }
            }

            var likes = optFirstStringOrNumber(obj, "likes", "like", "like_count", "likeCount", "rated", "favorites", "favourites", "total_likes")
            if (likes.isNullOrBlank() && videoItems.isNotEmpty()) {
                val totalL = videoItems.sumOf { it.likeCount }
                if (totalL > 0L) {
                    likes = when {
                        totalL >= 1_000_000 -> String.format("%.1fM", totalL / 1_000_000.0)
                        totalL >= 1_000 -> String.format("%.1fK", totalL / 1_000.0)
                        else -> totalL.toString()
                    }
                }
            }

            val comments = optFirstStringOrNumber(obj, "comments", "comment_count", "commentCount")

            ParsedZineMetadata(
                title = title,
                altTitle = altTitle,
                author = author,
                artist = artist,
                description = description,
                type = type,
                status = status,
                rating = rating,
                tags = tags,
                publisher = publisher,
                serialization = serialization,
                year = year,
                language = language,
                pages = pages,
                totalChapters = totalChapters,
                coverFileName = coverFileName,
                rawJson = jsonString,
                url = url,
                views = views,
                likes = likes,
                comments = comments,
                videoItems = videoItems
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractVideoItems(obj: JSONObject): List<VideoItemMetadata> {
        val result = linkedMapOf<String, VideoItemMetadata>()

        fun processArray(key: String, onIndex: (VideoItemMetadata, Int) -> VideoItemMetadata) {
            val arr = obj.optJSONArray(key) ?: return
            for (i in 0 until arr.length()) {
                val itemObj = arr.optJSONObject(i) ?: continue
                val rawTitle = optFirstString(itemObj, "title", "name", "video_title") ?: continue
                val normKey = rawTitle.trim().lowercase()
                val existing = result[normKey] ?: VideoItemMetadata(
                    id = optFirstString(itemObj, "id", "video_id", "videoId") ?: "",
                    title = rawTitle,
                    viewCount = optFirstLong(itemObj, "view_count", "viewCount", "views"),
                    likeCount = optFirstLong(itemObj, "like_count", "likeCount", "likes", "like", "rated"),
                    duration = optFirstLong(itemObj, "duration", "duration_seconds", "length"),
                    uploadDate = optFirstString(itemObj, "upload_date", "uploadDate", "date") ?: "",
                    url = optFirstUrl(itemObj, "url", "link") ?: ""
                )
                val updated = onIndex(
                    existing.copy(
                        viewCount = if (existing.viewCount == 0L) optFirstLong(itemObj, "view_count", "viewCount", "views") else existing.viewCount,
                        likeCount = if (existing.likeCount == 0L) optFirstLong(itemObj, "like_count", "likeCount", "likes") else existing.likeCount,
                        duration = if (existing.duration == 0L) optFirstLong(itemObj, "duration", "duration_seconds", "length") else existing.duration,
                        url = if (existing.url.isBlank()) (optFirstUrl(itemObj, "url", "link") ?: "") else existing.url,
                        uploadDate = if (existing.uploadDate.isBlank()) (optFirstString(itemObj, "upload_date", "uploadDate", "date") ?: "") else existing.uploadDate
                    ),
                    i
                )
                result[normKey] = updated
            }
        }

        // Process various list types if present
        processArray("most_viewed") { item, idx -> item.copy(mostViewedRank = idx) }
        processArray("top_rated") { item, idx -> item.copy(topRatedRank = idx) }
        processArray("latest") { item, idx -> item.copy(latestRank = idx) }
        processArray("longest") { item, _ -> item }
        processArray("videos") { item, _ -> item }
        processArray("chapters") { item, _ -> item }
        processArray("items") { item, _ -> item }
        processArray("episodes") { item, _ -> item }

        return result.values.toList()
    }

    private fun optFirstLong(obj: JSONObject, vararg keys: String): Long {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val v = obj.optDouble(k, -1.0)
                if (v >= 0.0) return v.toLong()
                val str = obj.optString(k, "").trim()
                val parsed = str.toDoubleOrNull()
                if (parsed != null && parsed >= 0.0) return parsed.toLong()
            }
        }
        return 0L
    }

    private fun optFirstUrl(obj: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val str = obj.optString(k, "").trim()
                if (str.startsWith("http://", ignoreCase = true) ||
                    str.startsWith("https://", ignoreCase = true) ||
                    str.startsWith("www.", ignoreCase = true)
                ) {
                    return str
                }
            }
        }
        return null

    }

    private fun optFirstString(obj: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val str = obj.optString(k, "").trim()
                if (str.isNotBlank()) return str
            }
        }
        return null
    }

    /** Reads a stat field that may be a number or a string (e.g. "1.2M" or 1200000). */
    private fun optFirstStringOrNumber(obj: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val raw = obj.opt(k)
                val str = raw?.toString()?.trim().orEmpty()
                if (str.isNotBlank() && str != "0") return str
            }
        }
        return null
    }

    private fun optFirstInt(obj: JSONObject, vararg keys: String): Int {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val v = obj.optInt(k, -1)
                if (v >= 0) return v
            }
        }
        return 0
    }

    private fun optFirstStringOrArray(obj: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val arr = obj.optJSONArray(k)
                if (arr != null) {
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val item = arr.optString(i, "").trim()
                        if (item.isNotBlank()) list.add(item)
                    }
                    if (list.isNotEmpty()) return list.joinToString(", ")
                } else {
                    val str = obj.optString(k, "").trim()
                    if (str.isNotBlank()) return str
                }
            }
        }
        return null
    }

    private fun extractTagsAndGenres(obj: JSONObject): List<String> {
        val result = mutableListOf<String>()
        val tagKeys = listOf("tags", "genres", "genre", "categories", "category", "keywords")
        for (k in tagKeys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val arr = obj.optJSONArray(k)
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.optString(i, "").trim()
                        if (item.isNotBlank() && !result.contains(item) && !item.equals("Favorite", ignoreCase = true)) {
                            result.add(item)
                        }
                    }
                } else {
                    val str = obj.optString(k, "").trim()
                    if (str.isNotBlank()) {
                        val split = str.split(",", ";").map { it.trim() }.filter {
                            it.isNotBlank() && !result.contains(it) && !it.equals("Favorite", ignoreCase = true)
                        }
                        result.addAll(split)
                    }
                }
            }
        }
        return result.distinct()
    }
}
