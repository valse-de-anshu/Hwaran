package com.ballade.hwaran.core.metadata

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ballade.hwaran.core.database.entity.MangaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class EntryMetadata(
    val title: String = "",
    val altTitle: String = "",
    val author: String = "",
    val artist: String = "",
    val description: String = "",
    val type: String = "",
    val status: String = "",
    val rating: String = "",
    val tags: List<String> = emptyList(),
    val publisher: String = "",
    val serialization: String = "",
    val year: String = "",
    val language: String = "",
    val pages: String = "",
    val totalChapters: Int = 0,
    val isFavorite: Boolean = false,
    // Online / source URL
    val url: String = "",
    // Online statistics (empty string = not available)
    val views: String = "",
    val likes: String = "",
    val comments: String = "",
    // Individual video/chapter statistics and rankings from JSON (e.g. most_viewed, top_rated)
    val videoItems: List<VideoItemMetadata> = emptyList()
)

data class VideoItemMetadata(
    val id: String = "",
    val title: String = "",
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val duration: Long = 0L,
    val uploadDate: String = "",
    val url: String = "",
    val topRatedRank: Int = -1,
    val mostViewedRank: Int = -1,
    val latestRank: Int = -1
)

data class MasterTagItem(
    val id: String,
    val tag: String,
    val section: String,
    val category: String,
    val aliases: List<String> = emptyList()
)

object MediaMetadataManager {

    private var cachedMasterTags: List<MasterTagItem>? = null

    /**
     * Loads the master tags dataset from assets.
     */
    suspend fun getMasterTags(context: Context): List<MasterTagItem> = withContext(Dispatchers.IO) {
        cachedMasterTags?.let { return@withContext it }
        val list = mutableListOf<MasterTagItem>()
        try {
            context.assets.open("tags/master_tags.json").use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", "")
                    val tag = obj.optString("tag", "")
                    val section = obj.optString("section", "SFW")
                    val category = obj.optString("category", "Theme")
                    val aliasesArr = obj.optJSONArray("aliases")
                    val aliases = mutableListOf<String>()
                    if (aliasesArr != null) {
                        for (j in 0 until aliasesArr.length()) {
                            aliases.add(aliasesArr.getString(j))
                        }
                    }
                    if (tag.isNotBlank()) {
                        list.add(MasterTagItem(id, tag, section, category, aliases))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cachedMasterTags = list
        list
    }

    /**
     * Filters master tags by query string matching name or aliases.
     */
    suspend fun searchTags(context: Context, query: String, limit: Int = 20): List<MasterTagItem> {
        val all = getMasterTags(context)
        if (query.isBlank()) {
            val popularNames = listOf(
                "Action", "Adventure", "Fantasy", "Light Novel", "Web Novel",
                "Fiction", "Cultivation", "LitRPG", "Comedy", "Drama",
                "Mystery", "Romance", "Sci-Fi", "Slice of Life", "Supernatural",
                "Psychological", "Martial Arts", "Horror", "Isekai", "Historical",
                "Non-Fiction", "Classic Literature", "Dystopian", "Philosophy"
            )
            val popularItems = popularNames.mapNotNull { name ->
                all.firstOrNull { it.tag.equals(name, ignoreCase = true) }
                    ?: MasterTagItem(name.lowercase(), name, "SFW", "Genre")
            }
            return popularItems.take(limit)
        }
        val q = query.trim().lowercase()
        return withContext(Dispatchers.Default) {
            all.filter { item ->
                item.tag.lowercase().contains(q) || item.aliases.any { it.lowercase().contains(q) }
            }.take(limit)
        }
    }

    suspend fun searchMasterTags(context: Context, query: String, limit: Int = 20): List<MasterTagItem> = searchTags(context, query, limit)


    /**
     * Reads metadata for a media entry according to Hwaran Import & Metadata Rules:
     * 1. .zine/ JSON in parent folder
     * 2. Root .json in parent folder
     * 3. Internal app cache (files/metadata/{mangaId}.json)
     * 4. Fallbacks to MangaEntity values
     */
    suspend fun loadMetadata(
        context: Context,
        mangaId: Long,
        parentUri: String?,
        manga: MangaEntity?
    ): EntryMetadata = withContext(Dispatchers.IO) {
        var parsedZine: ParsedZineMetadata? = null

        // 1. Try local filesystem folder (.zine/*.json or *.json)
        if (!parentUri.isNullOrBlank() && parentUri.startsWith("/")) {
            val dir = File(parentUri)
            if (dir.exists() && dir.isDirectory) {
                parsedZine = ZineMetadataExtractor.extractFromFolder(dir)
            }
        }

        // 2. Try SAF DocumentFile (.zine/*.json or *.json)
        if (parsedZine == null && !parentUri.isNullOrBlank() && parentUri.startsWith("content://")) {
            try {
                val docDir = DocumentFile.fromTreeUri(context, Uri.parse(parentUri))
                if (docDir != null && docDir.isDirectory) {
                    parsedZine = ZineMetadataExtractor.extractFromDocumentFolder(context, docDir)
                }
            } catch (_: Exception) {}
        }

        // 3. Try internal app storage cache
        if (parsedZine == null && mangaId > 0) {
            val cacheFile = File(context.filesDir, "metadata/$mangaId.json")
            if (cacheFile.exists()) {
                try {
                    val cachedContent = cacheFile.readText()
                    parsedZine = ZineMetadataExtractor.parseJson(cachedContent)
                } catch (_: Exception) {}
            }
        }

        val defaultTags = manga?.genre?.split(",")?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.equals("Favorite", ignoreCase = true) } ?: emptyList()

        val baseFallback = EntryMetadata(
            title = manga?.title ?: "",
            altTitle = "",
            author = "",
            artist = "",
            description = manga?.description?.takeIf { it != "No description added yet." } ?: "",
            type = manga?.boxPurpose ?: if (manga?.contentType == 1) "Book" else "",
            status = "",
            rating = "",
            tags = defaultTags,
            publisher = "",
            serialization = "",
            year = "",
            language = "",
            pages = "",
            totalChapters = 0,
            isFavorite = manga?.isFavorite == true
        )

        if (parsedZine != null) {
            val entryMeta = parsedZine.toEntryMetadata(baseFallback)
            val mergedTags = (entryMeta.tags + defaultTags).distinct()
            return@withContext entryMeta.copy(tags = mergedTags)
        }

        baseFallback
    }

    private fun mergeMetadataJson(existingContent: String?, metadata: EntryMetadata): String {
        val trimmed = existingContent?.trim().orEmpty()
        val obj = if (trimmed.startsWith("{")) {
            try {
                JSONObject(trimmed)
            } catch (_: Exception) {
                JSONObject()
            }
        } else {
            JSONObject()
        }

        // Merge or update properties while preserving existing untouched fields
        obj.put("title", metadata.title)
        if (metadata.altTitle.isNotBlank() || obj.has("altTitle")) obj.put("altTitle", metadata.altTitle)
        obj.put("author", metadata.author)
        if (metadata.artist.isNotBlank() || obj.has("artist")) obj.put("artist", metadata.artist)
        obj.put("description", metadata.description)
        obj.put("type", metadata.type)
        obj.put("status", metadata.status)
        obj.put("rating", metadata.rating)
        obj.put("tags", JSONArray(metadata.tags))
        if (metadata.publisher.isNotBlank() || obj.has("publisher")) obj.put("publisher", metadata.publisher)
        if (metadata.serialization.isNotBlank() || obj.has("serialization")) obj.put("serialization", metadata.serialization)
        if (metadata.year.isNotBlank() || obj.has("year")) obj.put("year", metadata.year)
        if (metadata.language.isNotBlank() || obj.has("language")) obj.put("language", metadata.language)
        if (metadata.pages.isNotBlank() || obj.has("pages")) obj.put("pages", metadata.pages)
        if (metadata.totalChapters > 0 || obj.has("totalChapters")) obj.put("totalChapters", metadata.totalChapters)
        obj.put("isFavorite", metadata.isFavorite)
        if (metadata.url.isNotBlank() || obj.has("url")) obj.put("url", metadata.url)
        if (metadata.views.isNotBlank() || obj.has("views")) obj.put("views", metadata.views)
        if (metadata.likes.isNotBlank() || obj.has("likes")) obj.put("likes", metadata.likes)
        if (metadata.comments.isNotBlank() || obj.has("comments")) obj.put("comments", metadata.comments)

        return obj.toString(2)
    }

    /**
     * Saves EntryMetadata according to Hwaran metadata rules:
     * 1. Always caches to internal app storage (files/metadata/{mangaId}.json).
     * 2. Finds existing .json in .zine/ or root folder.
     *    - If existing JSON is found, uses/updates it (never creates an extraneous entry.json).
     *    - If an existing JSON or .zine folder is empty, fills it with metadata.
     *    - Only creates entry.json at root when NO .json exists anywhere in .zine or root.
     * 3. [forceWriteToFile] determines if the file on disk should be written if it already exists with content.
     *    - Set true when user explicitly edits metadata in description.
     *    - Set false during media importing (so existing non-empty files are preserved untouched).
     */
    suspend fun saveMetadata(
        context: Context,
        mangaId: Long,
        parentUri: String?,
        metadata: EntryMetadata,
        forceWriteToFile: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        val fullContent = mergeMetadataJson(null, metadata)

        // 1. Save to internal app storage cache
        try {
            val dir = File(context.filesDir, "metadata")
            if (!dir.exists()) dir.mkdirs()
            File(dir, "$mangaId.json").writeText(fullContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Try to save to local folder if parentUri is filesystem path
        if (!parentUri.isNullOrBlank() && parentUri.startsWith("/")) {
            try {
                val f = File(parentUri)
                val dir = if (f.isFile) f.parentFile else f
                if (dir != null && dir.exists() && dir.isDirectory && dir.canWrite()) {
                    val targetFile = ZineMetadataExtractor.findMetadataFile(dir)
                    val exists = targetFile.exists()
                    val isEmpty = !exists || targetFile.length() == 0L

                    // Only write if forceWriteToFile == true (e.g. user edit) OR if the target file is empty
                    val shouldWrite = forceWriteToFile || isEmpty
                    if (shouldWrite) {
                        val existingContent = if (exists && targetFile.length() > 0L) {
                            try { targetFile.readText() } catch (_: Exception) { null }
                        } else null

                        val merged = mergeMetadataJson(existingContent, metadata)
                        targetFile.parentFile?.mkdirs()
                        targetFile.writeText(merged)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Try to save via SAF DocumentFile
        if (!parentUri.isNullOrBlank() && parentUri.startsWith("content://")) {
            try {
                val docDir = DocumentFile.fromTreeUri(context, Uri.parse(parentUri))
                if (docDir != null && docDir.isDirectory && docDir.canWrite()) {
                    val target = ZineMetadataExtractor.findOrCreateMetadataDoc(docDir)
                    if (target != null) {
                        val isEmpty = target.length() == 0L
                        val shouldWrite = forceWriteToFile || isEmpty
                        if (shouldWrite) {
                            val existingContent = if (!isEmpty) {
                                ZineMetadataExtractor.readDocText(context, target)
                            } else null

                            val merged = mergeMetadataJson(existingContent, metadata)
                            context.contentResolver.openOutputStream(target.uri, "wt")?.use { stream ->
                                stream.write(merged.toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        true
    }
}
