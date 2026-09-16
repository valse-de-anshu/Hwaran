package com.ballade.hwaran

import com.ballade.hwaran.backend.novel.NovelParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NovelMediaParsingTest {

    @Test
    fun testNovelExtensionRecognition() {
        // Plain text and Markdown belong to Novel
        assertTrue(NovelParser.isNovelFile("79569.txt"))
        assertTrue(NovelParser.isNovelFile("story.text"))
        assertTrue(NovelParser.isNovelFile("story.md"))
        assertTrue(NovelParser.isNovelFile("story.markdown"))

        // Rich ebooks and web-books do NOT belong to Novel
        assertFalse(NovelParser.isNovelFile("79569.epub"))
        assertFalse(NovelParser.isNovelFile("79569.kf8.images"))
        assertFalse(NovelParser.isNovelFile("79569.kindle.images"))
        assertFalse(NovelParser.isNovelFile("pg79569-images.html"))
        assertFalse(NovelParser.isNovelFile("book.mobi"))
        assertFalse(NovelParser.isNovelFile("book.azw3"))
        assertFalse(NovelParser.isNovelFile("book.fb2"))
        assertFalse(NovelParser.isNovelFile("document.pdf"))
        assertFalse(NovelParser.isNovelFile("video.mp4"))
        assertFalse(NovelParser.isNovelFile("song.mp3"))
        assertFalse(NovelParser.isNovelFile("cover.jpg"))
    }

    @Test
    fun testBookExtensionRecognition() {
        // Rich ebooks, web-books, and PDFs belong to Book
        assertTrue(NovelParser.isBookFile("79569.epub"))
        assertTrue(NovelParser.isBookFile("79569.kf8.images"))
        assertTrue(NovelParser.isBookFile("79569.kindle.images"))
        assertTrue(NovelParser.isBookFile("79569-no-images.epub"))
        assertTrue(NovelParser.isBookFile("pg79569-images.html"))
        assertTrue(NovelParser.isBookFile("chapter1.htm"))
        assertTrue(NovelParser.isBookFile("index.xhtml"))
        assertTrue(NovelParser.isBookFile("book.mobi"))
        assertTrue(NovelParser.isBookFile("book.azw3"))
        assertTrue(NovelParser.isBookFile("book.fb2"))
        assertTrue(NovelParser.isBookFile("manual.pdf"))

        // Plain text files do NOT belong to Book (they are Novel)
        assertFalse(NovelParser.isBookFile("79569.txt"))
        assertFalse(NovelParser.isBookFile("story.md"))
        assertFalse(NovelParser.isBookFile("video.mp4"))
        assertFalse(NovelParser.isBookFile("song.mp3"))
        assertFalse(NovelParser.isBookFile("cover.jpg"))
    }

    @Test
    fun testWordCountWithCJKAndLatin() {
        val latinText = "The quick brown fox jumps over the lazy dog."
        assertEquals(9, NovelParser.countWords(latinText))

        val cjkText = "你好世界"
        assertEquals(4, NovelParser.countWords(cjkText))
    }

    @Test
    fun testHtmlBookParsing() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>The Adventures of Sherlock Holmes</title>
                <meta name="author" content="Arthur Conan Doyle">
            </head>
            <body>
                <div class="chapter" title="A Scandal in Bohemia">
                    <h1>A Scandal in Bohemia</h1>
                    <p>To Sherlock Holmes she is always THE woman.</p>
                </div>
                <div class="chapter" title="The Red-Headed League">
                    <h1>The Red-Headed League</h1>
                    <p>I had called upon my friend, Mr. Sherlock Holmes, one day in autumn.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val parsed = NovelParser.parseHtmlContent(sampleHtml, "Fallback Title")
        assertEquals("The Adventures of Sherlock Holmes", parsed.title)
        assertEquals("Arthur Conan Doyle", parsed.author)
        assertEquals(2, parsed.chapters.size)
        assertEquals("A Scandal in Bohemia", parsed.chapters[0].title)
        assertTrue(parsed.chapters[0].content.contains("To Sherlock Holmes she is always THE woman."))
        assertEquals("The Red-Headed League", parsed.chapters[1].title)
    }

    @Test
    fun testGutenbergDirectoryStructure() {
        val filesInDirectory = listOf(
            "79569.epub",
            "79569.kf8.images",
            "79569.kindle.images",
            "79569-no-images.epub",
            "79569.txt",
            "cover.jpg",
            "pg79569-images.html"
        )

        val bookFiles = filesInDirectory.filter { NovelParser.isBookFile(it) }
        assertEquals(5, bookFiles.size)
        assertTrue(bookFiles.contains("79569.epub"))
        assertTrue(bookFiles.contains("79569.kf8.images"))
        assertTrue(bookFiles.contains("79569.kindle.images"))
        assertTrue(bookFiles.contains("79569-no-images.epub"))
        assertTrue(bookFiles.contains("pg79569-images.html"))

        val novelFiles = filesInDirectory.filter { NovelParser.isNovelFile(it) }
        assertEquals(1, novelFiles.size)
        assertEquals("79569.txt", novelFiles[0])
    }

    @Test
    fun testRealGutenbergEpubAndKindleParsing() = kotlinx.coroutines.runBlocking {
        val baseDir = File("/home/valse-de-anshu/Downloads/Zine/Vacuum/gutenberg/Ashes")
        if (baseDir.exists()) {
            val epubFile = File(baseDir, "79569.epub")
            if (epubFile.exists()) {
                val epubBook = NovelParser.parseNovelFromFile(epubFile)
                assertEquals("Ashes", epubBook.title)
                assertEquals("Theodore Thomas Flynn", epubBook.author)
                assertTrue(epubBook.chapters.isNotEmpty())
            }

            val kindleFile = File(baseDir, "79569.kindle.images")
            if (kindleFile.exists()) {
                val kindleBook = NovelParser.parseNovelFromFile(kindleFile)
                assertEquals("Ashes", kindleBook.title)
                assertEquals("Theodore Thomas Flynn", kindleBook.author)
                assertTrue(kindleBook.chapters.isNotEmpty())
            }

            val noImgEpub = File(baseDir, "79569-no-images.epub")
            if (noImgEpub.exists()) {
                val noImgBook = NovelParser.parseNovelFromFile(noImgEpub)
                assertEquals("Ashes", noImgBook.title)
                assertEquals("Theodore Thomas Flynn", noImgBook.author)
                assertTrue(noImgBook.chapters.isNotEmpty())
            }

            val txtFile = File(baseDir, "79569.txt")
            if (txtFile.exists()) {
                val txtBook = NovelParser.parseNovelFromFile(txtFile)
                assertEquals("79569", txtBook.title)
                assertTrue(txtBook.chapters.isNotEmpty())
            }

            // Test unzipping and assembling EPUB directly
            val targetDir = File("/tmp/test_epub_${System.currentTimeMillis()}")
            targetDir.mkdirs()
            try {
                val zipFile = File(baseDir, "79569-no-images.epub")
                if (zipFile.exists()) {
                    java.util.zip.ZipFile(zipFile).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val dest = File(targetDir, entry.name)
                            if (entry.isDirectory) dest.mkdirs()
                            else {
                                dest.parentFile?.mkdirs()
                                zip.getInputStream(entry).use { inStream ->
                                    dest.outputStream().use { outStream -> inStream.copyTo(outStream) }
                                }
                            }
                        }
                    }
                    val container = File(targetDir, "META-INF/container.xml")
                    assertTrue(container.exists())
                    val opfFile = File(targetDir, "OEBPS/content.opf")
                    assertTrue(opfFile.exists())
                }
            } finally {
                targetDir.deleteRecursively()
            }
        }
    }
}


