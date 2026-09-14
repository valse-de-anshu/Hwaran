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
    val type: String = "Manga",
    val status: String = "Ongoing",
    val rating: String = "8.7 (152K)",
    val tags: List<String> = emptyList(),
    val publisher: String = "",
    val serialization: String = "",
    val year: String = "",
    val language: String = "English",
    val pages: String = "",
    val totalChapters: Int = 0,
    val isFavorite: Boolean = false
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
            type = manga?.boxPurpose ?: if (manga?.contentType == 1) "Book" else "Manga",
            status = "Ongoing",
            rating = "8.7 (152K)",
            tags = defaultTags,
            publisher = "",
            serialization = "",
            year = "",
            language = "English",
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

    /**
     * Saves EntryMetadata to:
     * 1. Internal app cache (files/metadata/{mangaId}.json)
     * 2. Local entry.json in parentUri directory (if writable)
     * 3. SAF entry.json (if content:// tree URI)
     */
    suspend fun saveMetadata(
        context: Context,
        mangaId: Long,
        parentUri: String?,
        metadata: EntryMetadata
    ): Boolean = withContext(Dispatchers.IO) {
        val obj = JSONObject().apply {
            put("title", metadata.title)
            put("altTitle", metadata.altTitle)
            put("author", metadata.author)
            put("artist", metadata.artist)
            put("description", metadata.description)
            put("type", metadata.type)
            put("status", metadata.status)
            put("rating", metadata.rating)
            put("tags", JSONArray(metadata.tags))
            put("publisher", metadata.publisher)
            put("serialization", metadata.serialization)
            put("year", metadata.year)
            put("language", metadata.language)
            put("pages", metadata.pages)
            put("totalChapters", metadata.totalChapters)
            put("isFavorite", metadata.isFavorite)
        }
        val content = obj.toString(2)

        // 1. Save to internal app storage cache
        try {
            val dir = File(context.filesDir, "metadata")
            if (!dir.exists()) dir.mkdirs()
            File(dir, "$mangaId.json").writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Try to save to local folder if parentUri is filesystem path
        if (!parentUri.isNullOrBlank() && parentUri.startsWith("/")) {
            try {
                val dir = File(parentUri)
                if (dir.exists() && dir.isDirectory && dir.canWrite()) {
                    File(dir, "entry.json").writeText(content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Try to save via SAF DocumentFile
        if (!parentUri.isNullOrBlank() && parentUri.startsWith("content://")) {
            try {
                val docDir = DocumentFile.fromTreeUri(context, Uri.parse(parentUri))
                if (docDir != null && docDir.canWrite()) {
                    val target = docDir.findFile("entry.json") ?: docDir.createFile("application/json", "entry.json")
                    if (target != null) {
                        context.contentResolver.openOutputStream(target.uri, "wt")?.use { stream ->
                            stream.write(content.toByteArray(Charsets.UTF_8))
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
