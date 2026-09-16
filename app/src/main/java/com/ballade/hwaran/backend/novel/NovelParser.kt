package com.ballade.hwaran.backend.novel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.ballade.hwaran.core.database.entity.ChapterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.zip.ZipFile

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

    val NOVEL_TEXT_EXTENSIONS = listOf(
        ".txt",
        ".text",
        ".md",
        ".markdown"
    )

    val BOOK_EBOOK_EXTENSIONS = listOf(
        ".pdf",
        ".epub",
        ".kf8.images",
        ".kindle.images",
        ".kf8",
        ".kindle",
        ".mobi",
        ".azw",
        ".azw3",
        ".html",
        ".htm",
        ".xhtml",
        ".fb2"
    )

    val NOVEL_EXTENSIONS = NOVEL_TEXT_EXTENSIONS

    fun isNovelFile(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return NOVEL_TEXT_EXTENSIONS.any { lower.endsWith(it) }
    }

    fun isBookFile(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return BOOK_EBOOK_EXTENSIONS.any { lower.endsWith(it) }
    }

    fun isSupportedBookOrNovelFile(name: String?): Boolean = isBookFile(name) || isNovelFile(name)

    fun isKindleOrMobi(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return lower.endsWith(".kf8.images") ||
                lower.endsWith(".kindle.images") ||
                lower.endsWith(".kf8") ||
                lower.endsWith(".kindle") ||
                lower.endsWith(".mobi") ||
                lower.endsWith(".azw") ||
                lower.endsWith(".azw3") ||
                lower.endsWith(".prc")
    }

    fun isHtml(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".xhtml")
    }

    fun isFb2(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return name.lowercase().endsWith(".fb2")
    }

    suspend fun parseNovel(context: Context, uri: Uri, fileName: String? = null): NovelBook = withContext(Dispatchers.IO) {
        val resolvedName = fileName ?: (uri.lastPathSegment ?: "Novel")
        val lower = resolvedName.lowercase()

        // Check if SAF DocumentFile is a directory
        if (uri.toString().startsWith("content://")) {
            try {
                val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                if (doc != null && doc.isDirectory) {
                    return@withContext parseNovelFromDocumentDirectory(context, doc, resolvedName)
                }
            } catch (_: Exception) {}
        }

        when {
            lower.endsWith(".epub") || lower.endsWith(".epub3") -> parseEpub(context, uri, resolvedName)
            isKindleOrMobi(lower) -> parseKindleOrMobi(context, uri, resolvedName)
            isHtml(lower) -> parseHtml(context, uri, resolvedName)
            isFb2(lower) -> parseFb2(context, uri, resolvedName)
            lower.endsWith(".md") || lower.endsWith(".markdown") -> parseTextOrMarkdown(context, uri, resolvedName, isMarkdown = true)
            else -> parseTextOrMarkdown(context, uri, resolvedName, isMarkdown = false)
        }
    }

    suspend fun parseNovelFromFile(file: File): NovelBook = withContext(Dispatchers.IO) {
        if (file.isDirectory) {
            return@withContext parseNovelFromDirectory(file)
        }
        val lower = file.name.lowercase()
        when {
            lower.endsWith(".epub") || lower.endsWith(".epub3") -> parseEpubFromFile(file)
            isKindleOrMobi(lower) -> parseKindleOrMobiFromFile(file)
            isHtml(lower) -> parseHtmlFromFile(file)
            isFb2(lower) -> parseFb2FromFile(file)
            lower.endsWith(".md") || lower.endsWith(".markdown") -> parseTextOrMarkdownFromFile(file, isMarkdown = true)
            else -> parseTextOrMarkdownFromFile(file, isMarkdown = false)
        }
    }

    suspend fun parseNovelFromChapterEntities(
        context: Context,
        mangaTitle: String,
        dbChapters: List<ChapterEntity>,
        author: String? = null,
        description: String? = null,
        coverBitmap: Bitmap? = null
    ): NovelBook = withContext(Dispatchers.IO) {
        val sorted = dbChapters.sortedWith(compareBy { ch ->
            ch.title.replace(Regex("\\d+")) { it.value.padStart(10, '0') }
        })

        val parsedChapters = mutableListOf<NovelChapter>()
        sorted.forEachIndexed { index, chapterEntity ->
            val uriStr = chapterEntity.folderUri
            val lower = uriStr.lowercase()
            val text = when {
                isHtml(lower) -> {
                    val raw = readTextFromUri(context, uriStr)
                    cleanHtmlContent(raw)
                }
                else -> readTextFromUri(context, uriStr)
            }
            val cleanTitle = chapterEntity.title.substringBeforeLast(".")
                .replace("_", " ")
                .trim()
                .ifBlank { "Chapter ${index + 1}" }

            parsedChapters.add(
                NovelChapter(
                    id = chapterEntity.id,
                    index = index,
                    title = cleanTitle,
                    content = text.trim().removePrefix("\uFEFF"),
                    wordCount = countWords(text)
                )
            )
        }

        NovelBook(
            title = mangaTitle,
            author = author,
            description = description,
            coverBitmap = coverBitmap,
            chapters = parsedChapters,
            totalWordCount = parsedChapters.sumOf { it.wordCount }
        )
    }

    private fun parseNovelFromDirectory(dir: File): NovelBook {
        val allFiles = mutableListOf<File>()

        fun scanDir(current: File) {
            current.listFiles()?.forEach { f ->
                if (f.isDirectory) {
                    if (!f.name.equals(".zine", ignoreCase = true) && !f.name.startsWith(".")) {
                        scanDir(f)
                    }
                } else if (f.isFile) {
                    if (isSupportedBookOrNovelFile(f.name)) {
                        allFiles.add(f)
                    }
                }
            }
        }

        scanDir(dir)

        // Find cover: check root directory, then images/ / img/ / covers/ subfolder
        val coverFile = findCoverInDirectory(dir)
        val coverBitmap = coverFile?.let { BitmapFactory.decodeFile(it.absolutePath) }

        // Priority 1: Standalone EPUB inside directory
        val epubFile = allFiles.firstOrNull { it.name.lowercase().endsWith(".epub") || it.name.lowercase().endsWith(".epub3") }
        if (epubFile != null) {
            val book = parseEpubFromFile(epubFile)
            return if (book.coverBitmap == null && coverBitmap != null) book.copy(coverBitmap = coverBitmap) else book
        }

        // Priority 2: HTML Book inside directory (e.g., pg*-h/pg*-images.html or index.html)
        val htmlFile = allFiles.firstOrNull { isHtml(it.name) }
        if (htmlFile != null) {
            val book = parseHtmlFromFile(htmlFile)
            return if (book.coverBitmap == null && coverBitmap != null) book.copy(coverBitmap = coverBitmap) else book
        }

        // Priority 3: Kindle / MOBI file inside directory
        val mobiFile = allFiles.firstOrNull { isKindleOrMobi(it.name) }
        if (mobiFile != null) {
            val book = parseKindleOrMobiFromFile(mobiFile)
            return if (book.coverBitmap == null && coverBitmap != null) book.copy(coverBitmap = coverBitmap) else book
        }

        // Priority 4: FB2 inside directory
        val fb2File = allFiles.firstOrNull { isFb2(it.name) }
        if (fb2File != null) {
            val book = parseFb2FromFile(fb2File)
            return if (book.coverBitmap == null && coverBitmap != null) book.copy(coverBitmap = coverBitmap) else book
        }

        // Priority 5: Multiple text/markdown chapter files
        val textFiles = allFiles.filter { it.name.lowercase().let { n -> n.endsWith(".txt") || n.endsWith(".text") || n.endsWith(".md") || n.endsWith(".markdown") } }
        val sortedFiles = textFiles.sortedWith(compareBy { f ->
            f.name.replace(Regex("\\d+")) { it.value.padStart(10, '0') }
        })

        if (sortedFiles.size > 1) {
            val chapters = mutableListOf<NovelChapter>()
            sortedFiles.forEachIndexed { index, file ->
                val cleanTitle = file.nameWithoutExtension.replace("_", " ").trim().ifBlank { "Chapter ${index + 1}" }
                val rawText = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
                chapters.add(
                    NovelChapter(
                        index = index,
                        title = cleanTitle,
                        content = rawText.trim().removePrefix("\uFEFF"),
                        wordCount = countWords(rawText)
                    )
                )
            }

            return NovelBook(
                title = dir.name,
                coverBitmap = coverBitmap,
                chapters = chapters,
                totalWordCount = chapters.sumOf { it.wordCount }
            )
        } else if (sortedFiles.size == 1) {
            val book = parseTextOrMarkdownFromFile(sortedFiles.first(), isMarkdown = sortedFiles.first().name.lowercase().endsWith(".md"))
            return if (book.coverBitmap == null && coverBitmap != null) book.copy(coverBitmap = coverBitmap) else book
        }

        return NovelBook(
            title = dir.name,
            coverBitmap = coverBitmap,
            chapters = emptyList(),
            totalWordCount = 0
        )
    }

    fun findCoverInDirectory(dir: File): File? {
        val candidateNames = listOf("cover", "poster", "folder", "artwork", "thumb", "illus-fpc", "titlepage")
        // Check root dir
        dir.listFiles()?.firstOrNull { f ->
            f.isFile && candidateNames.any { prefix -> f.nameWithoutExtension.equals(prefix, ignoreCase = true) }
        }?.let { return it }

        // Check subdirectories (images/, img/, covers/)
        val subDirs = listOf("images", "img", "covers", "artwork")
        for (sub in subDirs) {
            val subDir = File(dir, sub)
            if (subDir.exists() && subDir.isDirectory) {
                subDir.listFiles()?.firstOrNull { f ->
                    f.isFile && candidateNames.any { prefix -> f.nameWithoutExtension.equals(prefix, ignoreCase = true) }
                }?.let { return it }
            }
        }
        return null
    }

    private fun parseNovelFromDocumentDirectory(
        context: Context,
        folderDoc: androidx.documentfile.provider.DocumentFile,
        fallbackTitle: String
    ): NovelBook {
        val allDocs = mutableListOf<androidx.documentfile.provider.DocumentFile>()

        fun scanDoc(doc: androidx.documentfile.provider.DocumentFile) {
            doc.listFiles().forEach { child ->
                val name = child.name ?: ""
                if (child.isDirectory) {
                    if (!name.equals(".zine", ignoreCase = true) && !name.startsWith(".")) {
                        scanDoc(child)
                    }
                } else {
                    if (isSupportedBookOrNovelFile(name)) {
                        allDocs.add(child)
                    }
                }
            }
        }

        scanDoc(folderDoc)

        // Priority 1: EPUB
        val epubDoc = allDocs.firstOrNull { it.name?.lowercase()?.let { n -> n.endsWith(".epub") || n.endsWith(".epub3") } == true }
        if (epubDoc != null) {
            return parseEpub(context, epubDoc.uri, epubDoc.name ?: fallbackTitle)
        }

        // Priority 2: HTML
        val htmlDoc = allDocs.firstOrNull { isHtml(it.name) }
        if (htmlDoc != null) {
            return parseHtml(context, htmlDoc.uri, htmlDoc.name ?: fallbackTitle)
        }

        // Priority 3: Kindle/MOBI
        val mobiDoc = allDocs.firstOrNull { isKindleOrMobi(it.name) }
        if (mobiDoc != null) {
            return parseKindleOrMobi(context, mobiDoc.uri, mobiDoc.name ?: fallbackTitle)
        }

        // Priority 4: FB2
        val fb2Doc = allDocs.firstOrNull { isFb2(it.name) }
        if (fb2Doc != null) {
            return parseFb2(context, fb2Doc.uri, fb2Doc.name ?: fallbackTitle)
        }

        // Priority 5: TXT / MD files
        val textDocs = allDocs.filter {
            val n = it.name?.lowercase() ?: ""
            n.endsWith(".txt") || n.endsWith(".text") || n.endsWith(".md") || n.endsWith(".markdown")
        }
        val sortedDocs = textDocs.sortedWith(compareBy { doc ->
            (doc.name ?: "").replace(Regex("\\d+")) { it.value.padStart(10, '0') }
        })

        if (sortedDocs.size > 1) {
            val chapters = mutableListOf<NovelChapter>()
            sortedDocs.forEachIndexed { index, doc ->
                val cleanTitle = (doc.name ?: "").substringBeforeLast(".").replace("_", " ").trim().ifBlank { "Chapter ${index + 1}" }
                val rawText = readTextFromUri(context, doc.uri.toString())
                chapters.add(
                    NovelChapter(
                        index = index,
                        title = cleanTitle,
                        content = rawText.trim().removePrefix("\uFEFF"),
                        wordCount = countWords(rawText)
                    )
                )
            }
            return NovelBook(
                title = folderDoc.name ?: fallbackTitle,
                chapters = chapters,
                totalWordCount = chapters.sumOf { it.wordCount }
            )
        } else if (sortedDocs.size == 1) {
            val doc = sortedDocs.first()
            return parseTextOrMarkdown(context, doc.uri, doc.name ?: fallbackTitle, isMarkdown = doc.name?.lowercase()?.endsWith(".md") == true)
        }

        return NovelBook(
            title = folderDoc.name ?: fallbackTitle,
            chapters = emptyList(),
            totalWordCount = 0
        )
    }

    fun readTextFromUri(context: Context, uriString: String): String {
        return try {
            if (uriString.startsWith("content://")) {
                val uri = Uri.parse(uriString)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                } ?: ""
            } else {
                val f = if (uriString.startsWith("file://")) File(Uri.parse(uriString).path ?: "") else File(uriString)
                if (f.exists() && f.isFile) {
                    f.readText(Charsets.UTF_8)
                } else ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // EPUB Parser (Zero third-party dependencies, standard Android Zip & XML Pull)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun parseEpub(context: Context, uri: Uri, fallbackTitle: String): NovelBook {
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

        try {
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
                        description = it.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
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

                // Fallback cover search in zip if OPF didn't specify it
                if (coverBitmap == null) {
                    val zipCoverEntry = zip.entries().asSequence().firstOrNull { entry ->
                        val n = entry.name.lowercase()
                        (n.contains("cover") || n.contains("folder") || n.contains("poster")) &&
                                (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))
                    }
                    if (zipCoverEntry != null) {
                        try {
                            zip.getInputStream(zipCoverEntry).use { stream ->
                                coverBitmap = BitmapFactory.decodeStream(stream)
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

                        var chapterTitle = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(rawHtml)?.groupValues?.get(1)?.trim()
                        if (chapterTitle.isNullOrBlank()) {
                            chapterTitle = Regex("""<h[1-3][^>]*>([^<]+)</h[1-3]>""", RegexOption.IGNORE_CASE).find(rawHtml)?.groupValues?.get(1)?.trim()
                        }
                        if (chapterTitle.isNullOrBlank()) {
                            chapterTitle = "Chapter ${chapterIndex + 1}"
                        }

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
        } catch (_: Exception) {}

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

    // ─────────────────────────────────────────────────────────────────────────────
    // HTML / Web-Book Parser (Supports standalone HTML and pg*-h web-book folders)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun parseHtml(context: Context, uri: Uri, fallbackTitle: String): NovelBook {
        val tempFile = File(context.cacheDir, "temp_novel_${System.currentTimeMillis()}.html")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return parseHtmlFromFile(tempFile, fallbackTitle.substringBeforeLast("."))
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun parseHtmlFromFile(file: File, fallbackTitle: String = file.nameWithoutExtension): NovelBook {
        val rawHtml = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
        val baseDir = file.parentFile
        return parseHtmlContent(rawHtml, fallbackTitle, baseDir)
    }

    fun parseHtmlContent(rawHtml: String, fallbackTitle: String, baseDir: File? = null): NovelBook {
        var title = fallbackTitle
        var author: String? = null
        var description: String? = null
        var coverBitmap: Bitmap? = null

        // 1. Extract title
        Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(rawHtml)?.let {
            val t = it.groupValues[1].trim()
            if (t.isNotBlank()) title = t
        }
        if (title == fallbackTitle) {
            Regex("""<h1[^>]*>([^<]+)</h1>""", RegexOption.IGNORE_CASE).find(rawHtml)?.let {
                val t = it.groupValues[1].trim()
                if (t.isNotBlank()) title = t
            }
        }

        // Clean common Project Gutenberg title prefixes/suffixes
        title = title.replace(Regex("""^The Project Gutenberg eBook of\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*,?\s*by\s+[^\r\n]+$""", RegexOption.IGNORE_CASE), "")
            .trim()

        // 2. Extract author
        Regex("""<meta\s+name=["']author["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(rawHtml)?.let {
            author = it.groupValues[1].trim()
        }
        if (author == null) {
            Regex("""(?i)(?:Author|By):\s*<[^>]+>([^<]+)<""", RegexOption.IGNORE_CASE).find(rawHtml)?.let {
                author = it.groupValues[1].trim()
            }
        }

        // 3. Extract cover
        if (baseDir != null) {
            val coverFile = findCoverInDirectory(baseDir)
            if (coverFile != null) {
                try {
                    coverBitmap = BitmapFactory.decodeFile(coverFile.absolutePath)
                } catch (_: Exception) {}
            }
        }

        if (coverBitmap == null && baseDir != null) {
            // Check images referenced in HTML (e.g., images/cover.jpg or images/illus-fpc.png)
            val imgMatches = Regex("""<img\s+[^>]*src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE).findAll(rawHtml)
            for (match in imgMatches) {
                val src = match.groupValues[1]
                val lowerSrc = src.lowercase()
                if (lowerSrc.contains("cover") || lowerSrc.contains("illus-fpc") || lowerSrc.contains("titlepage") || lowerSrc.contains("poster")) {
                    val resolvedImg = File(baseDir, src)
                    if (resolvedImg.exists() && resolvedImg.isFile) {
                        try {
                            coverBitmap = BitmapFactory.decodeFile(resolvedImg.absolutePath)
                            if (coverBitmap != null) break
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        // 4. Split chapters
        val chapters = mutableListOf<NovelChapter>()

        // Check for structured HTML chapter dividers (<div class="chapter">, <section>, <h2>/<h1>, <mbp:pagebreak>)
        val chapterSplits = mutableListOf<Pair<String, String>>() // (Title, HtmlContent)

        val chapterDivRegex = Regex("""(?i)(<div\s+[^>]*class=["'][^"']*\bchapter\b[^"']*["'][^>]*>|<section[^>]*>|<div\s+[^>]*id=["']chap[^"']*["'][^>]*>)""")
        val divMatches = chapterDivRegex.findAll(rawHtml).toList()

        if (divMatches.size >= 2) {
            for (i in divMatches.indices) {
                val start = divMatches[i].range.first
                val end = if (i < divMatches.size - 1) divMatches[i + 1].range.first else rawHtml.length
                val sectionHtml = rawHtml.substring(start, end)

                var chapTitle = Regex("""<h[1-4][^>]*>([^<]+)</h[1-4]>""", RegexOption.IGNORE_CASE).find(sectionHtml)?.groupValues?.get(1)?.trim()
                if (chapTitle.isNullOrBlank()) {
                    chapTitle = Regex("""title=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(divMatches[i].value)?.groupValues?.get(1)?.trim()
                }
                if (chapTitle.isNullOrBlank()) {
                    chapTitle = "Chapter ${i + 1}"
                }
                chapterSplits.add(chapTitle to sectionHtml)
            }
        } else {
            // Check for <h2> / <h3> tags as chapter separators
            val headingRegex = Regex("""(?i)(<h[1-3][^>]*>)(.*?)(</h[1-3]>)""")
            val headingMatches = headingRegex.findAll(rawHtml).toList()

            if (headingMatches.size >= 2) {
                for (i in headingMatches.indices) {
                    val chapTitle = headingMatches[i].groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                    val start = headingMatches[i].range.first
                    val end = if (i < headingMatches.size - 1) headingMatches[i + 1].range.first else rawHtml.length
                    val sectionHtml = rawHtml.substring(start, end)
                    chapterSplits.add((if (chapTitle.isNotBlank()) chapTitle else "Chapter ${i + 1}") to sectionHtml)
                }
            }
        }

        if (chapterSplits.isNotEmpty()) {
            chapterSplits.forEachIndexed { index, (chapTitle, sectionHtml) ->
                val cleaned = cleanHtmlContent(sectionHtml)
                if (cleaned.isNotBlank()) {
                    chapters.add(
                        NovelChapter(
                            index = index,
                            title = chapTitle,
                            content = cleaned,
                            wordCount = countWords(cleaned)
                        )
                    )
                }
            }
        } else {
            // Fallback to full HTML cleaning + smart segmentation
            val fullText = cleanHtmlContent(rawHtml)
            val parsedBook = parseRawText(fullText, title, isMarkdown = false)
            chapters.addAll(parsedBook.chapters)
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

    // ─────────────────────────────────────────────────────────────────────────────
    // FB2 (FictionBook) Parser
    // ─────────────────────────────────────────────────────────────────────────────

    fun parseFb2(context: Context, uri: Uri, fallbackTitle: String): NovelBook {
        val raw = readTextFromUri(context, uri.toString())
        return parseFb2Content(raw, fallbackTitle)
    }

    fun parseFb2FromFile(file: File, fallbackTitle: String = file.nameWithoutExtension): NovelBook {
        val raw = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
        return parseFb2Content(raw, fallbackTitle)
    }

    fun parseFb2Content(fb2: String, fallbackTitle: String): NovelBook {
        var title = Regex("""<book-title[^>]*>(.*?)</book-title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(fb2)?.groupValues?.get(1)?.let { cleanHtmlContent(it) }?.trim() ?: fallbackTitle
        if (title.isBlank()) title = fallbackTitle

        val firstName = Regex("""<first-name>(.*?)</first-name>""", RegexOption.IGNORE_CASE).find(fb2)?.groupValues?.get(1)?.trim() ?: ""
        val lastName = Regex("""<last-name>(.*?)</last-name>""", RegexOption.IGNORE_CASE).find(fb2)?.groupValues?.get(1)?.trim() ?: ""
        val author = "$firstName $lastName".trim().ifEmpty { null }

        val description = Regex("""<annotation[^>]*>(.*?)</annotation>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(fb2)?.groupValues?.get(1)?.let { cleanHtmlContent(it) }?.trim()

        val chapters = mutableListOf<NovelChapter>()
        var chapterIndex = 0

        val sectionRegex = Regex("""<section[^>]*>(.*?)</section>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val matches = sectionRegex.findAll(fb2).toList()

        if (matches.isNotEmpty()) {
            for (m in matches) {
                val secContent = m.groupValues[1]
                val secTitle = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                    .find(secContent)?.groupValues?.get(1)?.let { cleanHtmlContent(it) }?.trim() ?: "Chapter ${chapterIndex + 1}"

                val pTags = Regex("""<p[^>]*>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(secContent)
                val text = pTags.map { cleanHtmlContent(it.groupValues[1]).trim() }.filter { it.isNotBlank() }.joinToString("\n\n")
                if (text.isNotBlank()) {
                    chapters.add(
                        NovelChapter(
                            index = chapterIndex,
                            title = secTitle,
                            content = text,
                            wordCount = countWords(text)
                        )
                    )
                    chapterIndex++
                }
            }
        }

        if (chapters.isEmpty()) {
            val bodyMatch = Regex("""<body[^>]*>(.*?)</body>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(fb2)
            val body = bodyMatch?.groupValues?.get(1) ?: fb2
            val pTags = Regex("""<p[^>]*>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(body)
            val text = pTags.map { cleanHtmlContent(it.groupValues[1]).trim() }.filter { it.isNotBlank() }.joinToString("\n\n")
            if (text.isNotBlank()) {
                chapters.add(
                    NovelChapter(
                        index = 0,
                        title = title,
                        content = text,
                        wordCount = countWords(text)
                    )
                )
            }
        }

        return NovelBook(
            title = title,
            author = author,
            description = description,
            coverBitmap = null,
            chapters = chapters,
            totalWordCount = chapters.sumOf { it.wordCount }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // MOBI / Kindle / KF8 Parser (Pure Kotlin, zero external libraries)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun parseKindleOrMobi(context: Context, uri: Uri, fallbackTitle: String): NovelBook {
        val tempFile = File(context.cacheDir, "temp_mobi_${System.currentTimeMillis()}.mobi")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return parseKindleOrMobiFromFile(tempFile, fallbackTitle.substringBeforeLast("."))
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun parseKindleOrMobiFromFile(file: File, fallbackTitle: String = file.nameWithoutExtension): NovelBook {
        val bytes = try { file.readBytes() } catch (_: Exception) { ByteArray(0) }
        if (bytes.size < 78) {
            return NovelBook(title = fallbackTitle, chapters = emptyList())
        }

        // Check if file starts with ZIP header (PK\x03\x04) — many KF8 / Kindle containers are EPUB archives!
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
            return parseEpubFromFile(file, fallbackTitle)
        }

        try {
            // Palm Database Header
            val numRecords = readUInt16BE(bytes, 76)
            if (numRecords <= 0 || bytes.size < 78 + numRecords * 8) {
                return NovelBook(title = fallbackTitle, chapters = emptyList())
            }

            val recordOffsets = IntArray(numRecords)
            for (i in 0 until numRecords) {
                val off = readInt32BE(bytes, 78 + i * 8)
                recordOffsets[i] = if (off in 0..bytes.size) off else bytes.size
            }

            val rec0Offset = recordOffsets[0]
            val rec0End = if (numRecords > 1) recordOffsets[1] else bytes.size
            if (rec0Offset < 0 || rec0Offset >= bytes.size || rec0End <= rec0Offset || rec0End > bytes.size) {
                return NovelBook(title = fallbackTitle, chapters = emptyList())
            }

            val compression = readUInt16BE(bytes, rec0Offset)
            val textRecordCount = readUInt16BE(bytes, rec0Offset + 8)

            var title = fallbackTitle
            var author: String? = null
            var description: String? = null
            var coverBitmap: Bitmap? = null
            var firstImageIndex = -1
            var coverRecordOffset = -1

            // Check MOBI Header (Starts at offset 16 of record 0)
            var extraFlags = 0
            var kf8BoundaryOffset = -1

            if (rec0End - rec0Offset >= 24 + 16) {
                val mobiMagic = String(bytes, rec0Offset + 16, 4, Charsets.US_ASCII)
                if (mobiMagic == "MOBI") {
                    val fullNameOffset = readInt32BE(bytes, rec0Offset + 84)
                    val fullNameLength = readInt32BE(bytes, rec0Offset + 88)
                    if (fullNameOffset > 0 && fullNameLength > 0 && rec0Offset + fullNameOffset + fullNameLength <= bytes.size) {
                        try {
                            val mobiTitle = String(bytes, rec0Offset + fullNameOffset, fullNameLength, Charsets.UTF_8).trim()
                            if (mobiTitle.isNotBlank()) title = mobiTitle
                        } catch (_: Exception) {}
                    }

                    if (rec0End - rec0Offset >= 112) {
                        firstImageIndex = readInt32BE(bytes, rec0Offset + 108)
                    }

                    // Read extraFlags from MOBI header (offset 242 from rec0Offset)
                    if (rec0End - rec0Offset >= 244) {
                        extraFlags = readUInt16BE(bytes, rec0Offset + 242)
                    }

                    // Check for EXTH header inside record 0
                    val exthIndex = findBytes(bytes, "EXTH".toByteArray(Charsets.US_ASCII), rec0Offset, rec0End)
                    if (exthIndex != -1 && exthIndex + 8 <= rec0End) {
                        val recordCount = readInt32BE(bytes, exthIndex + 8)
                        var curr = exthIndex + 12
                        for (r in 0 until recordCount) {
                            if (curr + 8 > rec0End) break
                            val tagType = readInt32BE(bytes, curr)
                            val tagLength = readInt32BE(bytes, curr + 4)
                            val dataLength = tagLength - 8
                            if (dataLength > 0 && curr + 8 + dataLength <= rec0End) {
                                when (tagType) {
                                    100 -> author = String(bytes, curr + 8, dataLength, Charsets.UTF_8).trim()
                                    103 -> description = String(bytes, curr + 8, dataLength, Charsets.UTF_8).trim()
                                    121 -> { // KF8 Boundary offset
                                        kf8BoundaryOffset = if (dataLength == 4) readInt32BE(bytes, curr + 8) else -1
                                    }
                                    201 -> { // Cover offset
                                        coverRecordOffset = if (dataLength == 4) readInt32BE(bytes, curr + 8) else -1
                                    }
                                    503 -> { // Updated Title
                                        val exthTitle = String(bytes, curr + 8, dataLength, Charsets.UTF_8).trim()
                                        if (exthTitle.isNotBlank()) title = exthTitle
                                    }
                                }
                            }
                            curr += tagLength
                        }
                    }
                }
            }

            // Extract Cover Image
            val targetCoverIndex = if (coverRecordOffset >= 0 && firstImageIndex >= 0) {
                firstImageIndex + coverRecordOffset
            } else if (firstImageIndex in 0 until numRecords) {
                firstImageIndex
            } else -1

            if (targetCoverIndex in 0 until numRecords) {
                val start = recordOffsets[targetCoverIndex]
                val end = if (targetCoverIndex + 1 < numRecords) recordOffsets[targetCoverIndex + 1] else bytes.size
                if (start in 0 until bytes.size && end in (start + 1)..bytes.size) {
                    try {
                        coverBitmap = BitmapFactory.decodeByteArray(bytes, start, end - start)
                    } catch (_: Exception) {}
                }
            }

            // If still no cover, scan the first 3 image records
            if (coverBitmap == null && firstImageIndex in 0 until numRecords) {
                for (idx in firstImageIndex until minOf(firstImageIndex + 3, numRecords)) {
                    val start = recordOffsets[idx]
                    val end = if (idx + 1 < numRecords) recordOffsets[idx + 1] else bytes.size
                    if (start in 0 until bytes.size && end in (start + 1)..bytes.size) {
                        try {
                            val bmp = BitmapFactory.decodeByteArray(bytes, start, end - start)
                            if (bmp != null) {
                                coverBitmap = bmp
                                break
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            // Extract text from text records (1 .. textRecordCount)
            val decompressedStream = ByteArrayOutputStream()
            val maxTextRecords = minOf(textRecordCount, numRecords - 1)

            for (i in 1..maxTextRecords) {
                if (i >= numRecords) break
                val start = recordOffsets[i]
                val end = if (i + 1 < numRecords) recordOffsets[i + 1] else bytes.size
                if (start in 0 until bytes.size && end in (start + 1)..bytes.size) {
                    try {
                        val recordBytes = bytes.copyOfRange(start, end)
                        val trailingBytes = getTrailingBytesCount(recordBytes, extraFlags)
                        val cleanLength = (recordBytes.size - trailingBytes).coerceIn(0, recordBytes.size)
                        val cleanRecord = if (cleanLength < recordBytes.size) recordBytes.copyOfRange(0, cleanLength) else recordBytes

                        when (compression) {
                            2 -> decompressPalmDoc(cleanRecord, decompressedStream)
                            1 -> decompressedStream.write(cleanRecord)
                            else -> {} // Skip treating Huffman / proprietary compression binary streams as plain text
                        }
                    } catch (_: Throwable) {}
                }
            }

            val decompressedBytes = decompressedStream.toByteArray()
            val rawText = try {
                String(decompressedBytes, Charsets.UTF_8)
            } catch (_: Exception) {
                String(decompressedBytes, Charset.forName("ISO-8859-1"))
            }

            val isHtmlContent = rawText.contains("<html", ignoreCase = true) ||
                    rawText.contains("<body", ignoreCase = true) ||
                    rawText.contains("<div", ignoreCase = true) ||
                    rawText.contains("<p>", ignoreCase = true) ||
                    rawText.contains("<mbp:pagebreak", ignoreCase = true)

            if (isHtmlContent) {
                val parsedHtml = parseHtmlContent(rawText, title, file.parentFile)
                val resolvedTitle = if (title != fallbackTitle && title.isNotBlank() && !title.trim('\"', '\'').equals("Cover", ignoreCase = true)) {
                    title
                } else if (parsedHtml.title.isNotBlank() && parsedHtml.title != "Novel" && !parsedHtml.title.trim('\"', '\'').equals("Cover", ignoreCase = true)) {
                    parsedHtml.title
                } else {
                    title
                }
                return NovelBook(
                    title = resolvedTitle,
                    author = author ?: parsedHtml.author,
                    description = description ?: parsedHtml.description,
                    coverBitmap = coverBitmap ?: parsedHtml.coverBitmap,
                    chapters = parsedHtml.chapters,
                    totalWordCount = parsedHtml.totalWordCount
                )
            } else {
                val parsedText = parseRawText(rawText, title, isMarkdown = false)
                return NovelBook(
                    title = title,
                    author = author,
                    description = description,
                    coverBitmap = coverBitmap,
                    chapters = parsedText.chapters,
                    totalWordCount = parsedText.totalWordCount
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return NovelBook(title = fallbackTitle, chapters = emptyList())
        }
    }

    private fun getTrailingBytesCount(recordBytes: ByteArray, extraFlags: Int): Int {
        var totalTrailing = 0
        var flags = extraFlags ushr 1
        while (flags > 0) {
            if ((flags and 1) != 0) {
                var count = 0
                for (i in 0 until 4) {
                    val idx = recordBytes.size - totalTrailing - 1
                    if (idx < 0) break
                    val byteVal = recordBytes[idx].toInt() and 0xFF
                    totalTrailing++
                    count = (count shl 7) or (byteVal and 0x7F)
                    if ((byteVal and 0x80) != 0) break
                }
                totalTrailing += (count - 1).coerceAtLeast(0)
            }
            flags = flags ushr 1
        }
        if ((extraFlags and 1) != 0) {
            val idx = recordBytes.size - totalTrailing - 1
            if (idx >= 0) {
                val byteVal = recordBytes[idx].toInt() and 0xFF
                totalTrailing += (byteVal and 0x03) + 1
            }
        }
        return totalTrailing.coerceIn(0, recordBytes.size)
    }

    private fun decompressPalmDoc(bytes: ByteArray, out: ByteArrayOutputStream) {
        var pos = 0
        val len = bytes.size
        val recordOut = ByteArray(65536)
        var outPos = 0

        try {
            while (pos < len) {
                val b = bytes[pos++].toInt() and 0xFF
                when {
                    b == 0 -> {
                        if (outPos < recordOut.size) {
                            recordOut[outPos++] = 0
                        }
                    }
                    b in 1..8 -> {
                        val count = minOf(b, len - pos)
                        for (i in 0 until count) {
                            if (outPos < recordOut.size) {
                                recordOut[outPos++] = bytes[pos++]
                            } else {
                                pos++
                            }
                        }
                    }
                    b in 9..0x7F -> {
                        if (outPos < recordOut.size) {
                            recordOut[outPos++] = b.toByte()
                        }
                    }
                    b in 0x80..0xBF -> {
                        if (pos < len) {
                            val b2 = bytes[pos++].toInt() and 0xFF
                            val distance = ((b and 0x3F) shl 5) or (b2 ushr 3)
                            val length = (b2 and 0x07) + 3
                            if (distance > 0) {
                                for (k in 0 until length) {
                                    val srcPos = outPos - distance
                                    if (srcPos in 0 until outPos && outPos < recordOut.size) {
                                        recordOut[outPos] = recordOut[srcPos]
                                        outPos++
                                    }
                                }
                            }
                        }
                    }
                    b in 0xC0..0xFF -> {
                        if (outPos + 1 < recordOut.size) {
                            recordOut[outPos++] = 0x20.toByte() // Space
                            recordOut[outPos++] = (b xor 0x80).toByte()
                        }
                    }
                }
            }
            if (outPos > 0) {
                out.write(recordOut, 0, outPos)
            }
        } catch (_: Throwable) {
            if (outPos > 0) {
                try { out.write(recordOut, 0, outPos) } catch (_: Throwable) {}
            }
        }
    }

    private fun readUInt16BE(bytes: ByteArray, offset: Int): Int {
        if (offset + 1 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun readInt32BE(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun findBytes(source: ByteArray, target: ByteArray, start: Int, end: Int): Int {
        if (target.isEmpty()) return -1
        val max = minOf(end, source.size) - target.size
        for (i in start..max) {
            var match = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun cleanHtmlContent(html: String): String {
        return html
            // Strip <script>...</script> — (?s) makes . match newlines
            .replace(Regex("""(?si)<script\b[^>]*>.*?</script>"""), "")
            // Strip <style>...</style> — must handle multi-line CSS blocks
            .replace(Regex("""(?si)<style\b[^>]*>.*?</style>"""), "")
            // Strip <head>...</head> entirely (meta, title, stylesheets, etc.)
            .replace(Regex("""(?si)<head\b[^>]*>.*?</head>"""), "")
            // Strip raw CSS @rules (@page, @media, @font-face, etc.)
            .replace(Regex("""(?si)@(?:page|charset|import|media|font-face|keyframes|supports)[^{]*\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}"""), "")
            // Strip raw CSS rules / pseudo-selectors (e.g., a:hover { color: red })
            .replace(Regex("""(?si)(?:^|\n)\s*[a-zA-Z0-9_\-#.:*~>\[\]"=' ,]+\s*\{[^{}]*\}"""), "")
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""(?i)</p>"""), "\n\n")
            .replace(Regex("""(?i)</div>"""), "\n")
            .replace(Regex("""(?i)<mbp:pagebreak\s*/?>"""), "\n\n")
            .replace(Regex("""<[^>]*>"""), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
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
        val rawText = try { file.readText(Charset.forName("UTF-8")) } catch (_: Exception) { "" }
        return parseRawText(rawText, file.nameWithoutExtension, isMarkdown)
    }

    private fun parseRawText(rawText: String, defaultTitle: String, isMarkdown: Boolean): NovelBook {
        val title = defaultTitle
        val chapters = mutableListOf<NovelChapter>()

        val cleanedText = rawText
            .replace(Regex("""(?si)<style\b[^>]*>.*?</style>"""), "")
            .replace(Regex("""(?si)<script\b[^>]*>.*?</script>"""), "")
            .replace(Regex("""(?si)@(?:page|charset|import|media|font-face|keyframes|supports)[^{]*\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}"""), "")
            .replace(Regex("""(?si)(?:^|\n)\s*[a-zA-Z0-9_\-#.:*~>\[\]"=' ,]+\s*\{[^{}]*\}"""), "")

        // Chapter detection regex: matches "Chapter 1", "CHAPTER I", "Volume 1", "Prologue", "Epilogue", "# Title", "第1章", etc.
        val chapterRegex = Regex("""(?im)^(?:(?:\#{1,3}\s+.*)|(?:(?:Chapter|Volume|Book|Part|Act|Section|Prologue|Epilogue|第[0-9一二三四五六七八九十百千]+[章回节卷])\b[^\r\n]*))$""")

        val matches = chapterRegex.findAll(cleanedText).toList()

        if (matches.size >= 2) {
            // Split into detected chapters
            for (i in matches.indices) {
                val currentMatch = matches[i]
                val chapterTitle = currentMatch.value.trim().removePrefix("#").trim()
                val startIndex = currentMatch.range.first
                val endIndex = if (i < matches.size - 1) matches[i + 1].range.first else cleanedText.length

                val content = cleanedText.substring(startIndex, endIndex).trim()
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
        var inLatinWord = false

        for (i in text.indices) {
            val c = text[i]
            val block = Character.UnicodeBlock.of(c)
            val isCjk = block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                    block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                    block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                    block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                    block == Character.UnicodeBlock.HIRAGANA ||
                    block == Character.UnicodeBlock.KATAKANA ||
                    block == Character.UnicodeBlock.HANGUL_SYLLABLES

            if (isCjk) {
                inLatinWord = false
                count++
            } else if (Character.isLetterOrDigit(c)) {
                if (!inLatinWord) {
                    inLatinWord = true
                    count++
                }
            } else {
                inLatinWord = false
            }
        }
        return count
    }
}
