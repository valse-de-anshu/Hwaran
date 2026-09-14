package com.ballade.hwaran

import com.ballade.hwaran.core.metadata.ZineMetadataExtractor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ZineMetadataImportRulesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testZineMetadataPriorityOverRoot() {
        val root = tempFolder.newFolder("SoloLeveling")

        // Root json
        File(root, "info.json").writeText("""
            {
                "title": "Root Solo Leveling",
                "description": "Root description"
            }
        """.trimIndent())

        // .zine json
        val zineDir = File(root, ".zine")
        zineDir.mkdir()
        File(zineDir, "anything.json").writeText("""
            {
                "title": "Zine Priority Title",
                "description": "Zine priority description",
                "author": "Chugong",
                "genres": ["Action", "Fantasy"]
            }
        """.trimIndent())

        val extracted = ZineMetadataExtractor.extractFromFolder(root)
        assertNotNull(extracted)
        assertEquals("Zine Priority Title", extracted?.title)
        assertEquals("Zine priority description", extracted?.description)
        assertEquals("Chugong", extracted?.author)
        assertTrue(extracted?.tags?.contains("Action") == true)
        assertTrue(extracted?.tags?.contains("Fantasy") == true)
    }

    @Test
    fun testArbitraryJsonFilenameInZine() {
        val root = tempFolder.newFolder("MangaA")
        val zineDir = File(root, ".zine")
        zineDir.mkdir()

        // Name does NOT matter: whatever-the-user-called-it.json
        File(zineDir, "whatever-the-user-called-it.json").writeText("""
            {
                "name": "Omniscient Reader's Viewpoint",
                "synopsis": "Only I know the end of this world.",
                "creator": "Sing Shong",
                "tags": "Action, Apocalypse, Fantasy"
            }
        """.trimIndent())

        val extracted = ZineMetadataExtractor.extractFromFolder(root)
        assertNotNull(extracted)
        assertEquals("Omniscient Reader's Viewpoint", extracted?.title)
        assertEquals("Only I know the end of this world.", extracted?.description)
        assertEquals("Sing Shong", extracted?.author)
        assertEquals(3, extracted?.tags?.size)
        assertTrue(extracted?.tags?.contains("Apocalypse") == true)
    }

    @Test
    fun testFallbackToRootJsonWhenNoZine() {
        val root = tempFolder.newFolder("BookB")
        File(root, "my_book_data.json").writeText("""
            {
                "book_title": "Atomic Habits",
                "author": "James Clear",
                "year": "2018",
                "publisher": "Avery"
            }
        """.trimIndent())

        val extracted = ZineMetadataExtractor.extractFromFolder(root)
        assertNotNull(extracted)
        assertEquals("Atomic Habits", extracted?.title)
        assertEquals("James Clear", extracted?.author)
        assertEquals("2018", extracted?.year)
        assertEquals("Avery", extracted?.publisher)
    }

    @Test
    fun testFlexibleMetadataFields() {
        val json = """
            {
                "series_title": "Frieren: Beyond Journey's End",
                "alt_title": "Sousou no Frieren",
                "writer": "Kanehito Yamada",
                "artist": "Tsukasa Abe",
                "overview": "The adventure is over but life goes on for an elf mage.",
                "type": "manga",
                "status": "Ongoing",
                "score": "9.1",
                "categories": ["Adventure", "Drama", "Fantasy"],
                "release_year": "2020",
                "lang": "Japanese",
                "cover_image": "frieren_poster.jpg"
            }
        """.trimIndent()

        val parsed = ZineMetadataExtractor.parseJson(json)
        assertNotNull(parsed)
        assertEquals("Frieren: Beyond Journey's End", parsed?.title)
        assertEquals("Sousou no Frieren", parsed?.altTitle)
        assertEquals("Kanehito Yamada", parsed?.author)
        assertEquals("Tsukasa Abe", parsed?.artist)
        assertEquals("The adventure is over but life goes on for an elf mage.", parsed?.description)
        assertEquals("manga", parsed?.type)
        assertEquals("Ongoing", parsed?.status)
        assertEquals("9.1", parsed?.rating)
        assertEquals(3, parsed?.tags?.size)
        assertEquals("2020", parsed?.year)
        assertEquals("Japanese", parsed?.language)
        assertEquals("frieren_poster.jpg", parsed?.coverFileName)
    }

    @Test
    fun testCoverDetectionRules() {
        val root = tempFolder.newFolder("MediaSeries")

        // Invariant Rule 5: Dedicated covers are strictly detected
        assertTrue(ZineMetadataExtractor.isDedicatedCoverName("cover.jpg"))
        assertTrue(ZineMetadataExtractor.isDedicatedCoverName("folder.png"))
        assertTrue(ZineMetadataExtractor.isDedicatedCoverName("poster.webp"))
        assertTrue(ZineMetadataExtractor.isDedicatedCoverName("thumb.jpg"))

        // Random internal pages are NEVER covers
        assertFalse(ZineMetadataExtractor.isDedicatedCoverName("page_001.jpg"))
        assertFalse(ZineMetadataExtractor.isDedicatedCoverName("002.png"))
        assertFalse(ZineMetadataExtractor.isDedicatedCoverName("ch1_raw.webp"))
        assertFalse(ZineMetadataExtractor.isDedicatedCoverName("episode01.mp4"))

        // Test finding cover in .zine
        val zineDir = File(root, ".zine")
        zineDir.mkdir()
        val zineCover = File(zineDir, "poster.jpg")
        zineCover.createNewFile()

        val foundCover = ZineMetadataExtractor.findCoverInFolder(root)
        assertNotNull(foundCover)
        assertEquals(zineCover.absolutePath, foundCover?.absolutePath)
    }

    @Test
    fun testInternalAndAuxiliaryExclusions() {
        // Invariant Rule 7: .zine and .json are internal and never media items
        assertTrue(ZineMetadataExtractor.isInternalOrAuxiliary(".zine"))
        assertTrue(ZineMetadataExtractor.isInternalOrAuxiliary(".zine/metadata.json"))
        assertTrue(ZineMetadataExtractor.isInternalOrAuxiliary("info.json"))
        assertTrue(ZineMetadataExtractor.isInternalOrAuxiliary("entry.json"))
        assertTrue(ZineMetadataExtractor.isInternalOrAuxiliary(".nomedia"))
        assertTrue(ZineMetadataExtractor.isInternalOrAuxiliary("song.lrc"))
        assertTrue(ZineMetadataExtractor.isInternalOrAuxiliary(".hidden_folder"))

        // Media files are not auxiliary
        assertFalse(ZineMetadataExtractor.isInternalOrAuxiliary("Chapter 01"))
        assertFalse(ZineMetadataExtractor.isInternalOrAuxiliary("page_01.jpg"))
        assertFalse(ZineMetadataExtractor.isInternalOrAuxiliary("episode01.mp4"))
        assertFalse(ZineMetadataExtractor.isInternalOrAuxiliary("track01.mp3"))
        assertFalse(ZineMetadataExtractor.isInternalOrAuxiliary("novel.epub"))
        assertFalse(ZineMetadataExtractor.isInternalOrAuxiliary("book.pdf"))
    }
}
