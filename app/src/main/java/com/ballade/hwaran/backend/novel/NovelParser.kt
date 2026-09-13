package com.ballade.hwaran.backend.novel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.text.HtmlCompat
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class NovelBook(
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val coverBitmap: Bitmap? = null,
    val coverPath: String? = null,
    val chapters: List<NovelChapter>,
    val totalWordCount: Int = 0
)

data class NovelChapter(
    val id: Long = 0,
    val index: Int,
    val title: String,
    val content: String,
    val wordCount: Int = 0
)

object NovelParser {

    suspend fun parseNovel(context: Context, uri: Uri, fileName: String? = null): NovelBook = withContext(Dispatchers.IO) {
        val resolvedName = fileName ?: (uri.lastPathSegment ?: "Novel")
        val lower = resolvedName.lowercase()

        when {
            lower.endsWith(".epub") -> parseEpub(context, uri, resolvedName)
            lower.endsWith(".md") || lower.endsWith(".markdown") -> parseTextOrMarkdown(context, uri, resolvedName, isMarkdown = true)
            else -> parseTextOrMarkdown(context, uri, resolvedName, isMarkdown = false)
        }
    }

    suspend fun parseNovelFromFile(file: File): NovelBook = withContext(Dispatchers.IO) {
        val lower = file.name.lowercase()
        when {
            lower.endsWith(".epub") -> parseEpubFromFile(file)
            lower.endsWith(".md") || lower.endsWith(".markdown") -> parseTextOrMarkdownFromFile(file, isMarkdown = true)
            else -> parseTextOrMarkdownFromFile(file, isMarkdown = false)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // EPUB Parser (Zero third-party dependencies, standard Android Zip & XML Pull)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun parseEpub(context: Context, uri: Uri, fallbackTitle: String): NovelBook {
        // Copy stream to temp cache file for fast random-access zip reading
        val tempFile = File(context.cacheDir, "temp_novel_${System.currentTimeMillis()}.epub")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return parseEpubFromFile(tempFile, fallbackTitle.substringBeforeLast("."))
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun parseEpubFromFile(file: File, fallbackTitle: String = file.nameWithoutExtension): NovelBook {
        var title: String = fallbackTitle
        var author: String? = null
        var description: String? = null
        var coverBitmap: Bitmap? = null
        val chapters = mutableListOf<NovelChapter>()

        ZipFile(file).use { zip ->
            // 1. Locate container.xml to find OPF path
            val containerEntry = zip.getEntry("META-INF/container.xml") ?: zip.getEntry("meta-inf/container.xml")
            var opfPath = "OEBPS/content.opf"
            if (containerEntry != null) {
                val containerText = zip.getInputStream(containerEntry).bufferedReader().readText()
                val fullPathMatch = Regex("""full-path\s*=\s*["']([^"']+)["']""").find(containerText)
                if (fullPathMatch != null) {
                    opfPath = fullPathMatch.groupValues[1]
                }
            }

            val opfEntry = zip.getEntry(opfPath) ?: zip.entries().asSequence().find { it.name.lowercase().endsWith(".opf") }
            val opfDir = if (opfEntry != null && opfEntry.name.contains("/")) opfEntry.name.substringBeforeLast("/") + "/" else ""

            val manifestMap = mutableMapOf<String, String>() // id -> href
            val spineIds = mutableListOf<String>()
            var coverId: String? = null

            if (opfEntry != null) {
                val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()

                // Extract metadata
                Regex("""<dc:title[^>]*>([^<]+)</dc:title>""", RegexOption.IGNORE_CASE).find(opfContent)?.let {
                    title = it.groupValues[1].trim()
                }
                Regex("""<dc:creator[^>]*>([^<]+)</dc:creator>""", RegexOption.IGNORE_CASE).find(opfContent)?.let {
                    author = it.groupValues[1].trim()
                }
                Regex("""<dc:description[^>]*>([^<]+)</dc:description>""", RegexOption.IGNORE_CASE).find(opfContent)?.let {
                    description = HtmlCompat.fromHtml(it.groupValues[1], HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
                }

                // Check for cover in meta
                Regex("""<meta\s+name=["']cover["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(opfContent)?.let {
                    coverId = it.groupValues[1]
                }

                // Parse manifest items
                val itemRegex = Regex("""<item\s+[^>]*>""", RegexOption.IGNORE_CASE)
                itemRegex.findAll(opfContent).forEach { match ->
                    val tag = match.value
                    val id = Regex("""id=["']([^"']+)["']""").find(tag)?.groupValues?.get(1)
                    val href = Regex("""href=["']([^"']+)["']""").find(tag)?.groupValues?.get(1)
                    val properties = Regex("""properties=["']([^"']+)["']""").find(tag)?.groupValues?.get(1)

                    if (id != null && href != null) {
                        manifestMap[id] = href
                        if (properties?.contains("cover-image", ignoreCase = true) == true || id.equals("cover", ignoreCase = true) || id.contains("cover", ignoreCase = true)) {
                            if (coverId == null) coverId = id
                        }
                    }
                }

                // Parse spine itemrefs
                val itemrefRegex = Regex("""<itemref\s+[^>]*idref=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
                itemrefRegex.findAll(opfContent).forEach { match ->
                    val idref = match.groupValues[1]
                    spineIds.add(idref)
                }
            }

            // Extract cover image if found
            if (coverId != null && manifestMap.containsKey(coverId)) {
                val coverHref = manifestMap[coverId]!!
                val fullCoverPath = opfDir + coverHref
                val coverEntry = zip.getEntry(fullCoverPath) ?: zip.getEntry(coverHref)
                if (coverEntry != null) {
                    try {
                        zip.getInputStream(coverEntry).use { coverStream ->
                            coverBitmap = BitmapFactory.decodeStream(coverStream)
                        }
                    } catch (_: Exception) {}
                }
            }

            // Read chapters in spine order
            var chapterIndex = 0
            for (id in spineIds) {
                val href = manifestMap[id] ?: continue
                val fullChapterPath = opfDir + href
                val chapterEntry = zip.getEntry(fullChapterPath) ?: zip.getEntry(href) ?: continue

                try {
                    val rawHtml = zip.getInputStream(chapterEntry).bufferedReader(Charset.forName("UTF-8")).readText()
                    
                    // Extract chapter title from <title>, <h1>, <h2>, or fallback
                    var chapterTitle = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(rawHtml)?.groupValues?.get(1)?.trim()
                    if (chapterTitle.isNullOrBlank()) {
                        chapterTitle = Regex("""<h[1-3][^>]*>([^<]+)</h[1-3]>""", RegexOption.IGNORE_CASE).find(rawHtml)?.groupValues?.get(1)?.trim()
                    }
                    if (chapterTitle.isNullOrBlank()) {
                        chapterTitle = "Chapter ${chapterIndex + 1}"
                    }

                    // Convert HTML to clean readable text while preserving paragraph structure
                    val formattedText = cleanHtmlContent(rawHtml)
                    if (formattedText.isNotBlank()) {
                        val words = countWords(formattedText)
                        chapters.add(
                            NovelChapter(
                                index = chapterIndex,
                                title = chapterTitle,
                                content = formattedText,
                                wordCount = words
                            )
                        )
                        chapterIndex++
                    }
                } catch (_: Exception) {}
            }
        }

        val totalWords = chapters.sumOf { it.wordCount }
        return NovelBook(
            title = title,
            author = author,
            description = description,
            coverBitmap = coverBitmap,
            chapters = chapters,
            totalWordCount = totalWords
        )
    }

    private fun cleanHtmlContent(html: String): String {
        return try {
            // Replace <p>, <br>, <div> with explicit line breaks for clean paragraphing
            val preprocessed = html
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("""(?i)</p>"""), "\n\n")
                .replace(Regex("""(?i)</div>"""), "\n")
            
            val spanned = HtmlCompat.fromHtml(preprocessed, HtmlCompat.FROM_HTML_MODE_LEGACY)
            spanned.toString()
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trim()
        } catch (e: Exception) {
            html.replace(Regex("""<[^>]*>"""), "").trim()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TXT / Markdown Parser
    // ─────────────────────────────────────────────────────────────────────────────

    private fun parseTextOrMarkdown(context: Context, uri: Uri, fileName: String, isMarkdown: Boolean): NovelBook {
        val rawText = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charset.forName("UTF-8"))).readText()
        } ?: ""

        return parseRawText(rawText, fileName.substringBeforeLast("."), isMarkdown)
    }

    private fun parseTextOrMarkdownFromFile(file: File, isMarkdown: Boolean): NovelBook {
        val rawText = file.readText(Charset.forName("UTF-8"))
        return parseRawText(rawText, file.nameWithoutExtension, isMarkdown)
    }

    private fun parseRawText(rawText: String, defaultTitle: String, isMarkdown: Boolean): NovelBook {
        val title = defaultTitle
        val chapters = mutableListOf<NovelChapter>()

        // Chapter detection regex: matches "Chapter 1", "CHAPTER I", "Volume 1", "Prologue", "Epilogue", "# Title", "第1章", etc.
        val chapterRegex = Regex("""(?im)^(?:(?:\#{1,3}\s+.*)|(?:(?:Chapter|Volume|Book|Part|Act|Section|Prologue|Epilogue|第[0-9一二三四五六七八九十百千]+[章回节卷])\b[^\r\n]*))$""")

        val matches = chapterRegex.findAll(rawText).toList()

        if (matches.size >= 2) {
            // Split into detected chapters
            for (i in matches.indices) {
                val currentMatch = matches[i]
                val chapterTitle = currentMatch.value.trim().removePrefix("#").trim()
                val startIndex = currentMatch.range.first
                val endIndex = if (i < matches.size - 1) matches[i + 1].range.first else rawText.length

                val content = rawText.substring(startIndex, endIndex).trim()
                chapters.add(
                    NovelChapter(
                        index = i,
                        title = chapterTitle,
                        content = content,
                        wordCount = countWords(content)
                    )
                )
            }
        } else {
            // No distinct chapter headers found — segment into balanced reading segments (~3,000 words per chapter)
            val paragraphs = rawText.split(Regex("""\n\s*\n"""))
            val currentChunk = StringBuilder()
            var currentWordCount = 0
            var segmentIndex = 0

            for (p in paragraphs) {
                val words = countWords(p)
                if (currentWordCount + words > 3500 && currentChunk.isNotEmpty()) {
                    val content = currentChunk.toString().trim()
                    chapters.add(
                        NovelChapter(
                            index = segmentIndex,
                            title = "Chapter ${segmentIndex + 1}",
                            content = content,
                            wordCount = currentWordCount
                        )
                    )
                    currentChunk.clear()
                    currentWordCount = 0
                    segmentIndex++
                }
                currentChunk.append(p).append("\n\n")
                currentWordCount += words
            }

            if (currentChunk.isNotEmpty()) {
                val content = currentChunk.toString().trim()
                chapters.add(
                    NovelChapter(
                        index = segmentIndex,
                        title = if (segmentIndex == 0) "Complete Text" else "Chapter ${segmentIndex + 1}",
                        content = content,
                        wordCount = currentWordCount
                    )
                )
            }
        }

        val totalWords = chapters.sumOf { it.wordCount }
        return NovelBook(
            title = title,
            chapters = chapters,
            totalWordCount = totalWords
        )
    }

    fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        var count = 0
        var isWord = false
        val endOfLine = text.length - 1

        for (i in text.indices) {
            val c = text[i]
            // Support Latin words and CJK characters as individual words
            if (Character.isLetterOrDigit(c)) {
                if (!isWord) {
                    isWord = true
                    count++
                }
                // If it's a CJK ideograph, treat each character as a word
                val block = Character.UnicodeBlock.of(c)
                if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                    block == Character.UnicodeBlock.HIRAGANA ||
                    block == Character.UnicodeBlock.KATAKANA ||
                    block == Character.UnicodeBlock.HANGUL_SYLLABLES) {
                    count++
                }
            } else if (Character.isWhitespace(c) || c == '-' || c == '/' || c == '.') {
                isWord = false
            }
        }
        return count
    }
}
