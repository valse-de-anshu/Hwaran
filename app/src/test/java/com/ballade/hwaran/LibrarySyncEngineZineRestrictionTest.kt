package com.ballade.hwaran

import com.ballade.hwaran.data.sync.LibrarySyncEngine
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibrarySyncEngineZineRestrictionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFindZineRootsExcludesGenericSystemDirectories() {
        val extStorage = tempFolder.newFolder("storage_emulated_0")

        // Generic directories that must NEVER be treated as roots
        val downloadDir = File(extStorage, "Download").apply { mkdir() }
        File(extStorage, "Documents").mkdir()
        File(extStorage, "Movies").mkdir()
        File(extStorage, "Music").mkdir()

        // Unrelated subfolders inside Download
        File(downloadDir, "NCERT").mkdir()
        File(downloadDir, "MEGA Downloads").mkdir()
        File(downloadDir, "Ashes").mkdir()

        // Valid Zine roots
        val downloadZine = File(downloadDir, "Zine").apply { mkdir() }
        val rootZine = File(extStorage, "Zine").apply { mkdir() }
        val zineScraper = File(downloadDir, "zine_scraper").apply { mkdir() }

        val roots = LibrarySyncEngine.findZineRoots(extStorage)
        val canonicalRoots = roots.map { it.canonicalPath }

        // Assert Zine folders are recognized
        assertTrue("Download/Zine must be in candidate roots", canonicalRoots.contains(downloadZine.canonicalPath))
        assertTrue("Storage root Zine must be in candidate roots", canonicalRoots.contains(rootZine.canonicalPath))
        assertTrue("Download/zine_scraper must be in candidate roots", canonicalRoots.contains(zineScraper.canonicalPath))

        // Assert generic system folders are strictly EXCLUDED
        assertFalse("Download root must never be a candidate root", canonicalRoots.contains(downloadDir.canonicalPath))
        assertFalse("Documents must never be a candidate root", canonicalRoots.contains(File(extStorage, "Documents").canonicalPath))
        assertFalse("Movies must never be a candidate root", canonicalRoots.contains(File(extStorage, "Movies").canonicalPath))
        assertFalse("Music must never be a candidate root", canonicalRoots.contains(File(extStorage, "Music").canonicalPath))
        assertFalse("NCERT folder must never be a candidate root", canonicalRoots.contains(File(downloadDir, "NCERT").canonicalPath))
    }

    @Test
    fun testIsZineDirectory() {
        val dir1 = File("/storage/emulated/0/Download/Zine")
        val dir2 = File("/storage/emulated/0/Zine")
        val dir3 = File("/storage/emulated/0/Download/zine_scraper")
        val dir4 = File("/storage/emulated/0/Download/Zine Scraper")
        val dir5 = File("/storage/emulated/0/Download/zine scrapper")
        val dir6 = File("/storage/emulated/0/Download/zine_scrapper")
        val dir7 = File("/storage/emulated/0/Download/ZineScraper")
        val dir8 = File("/storage/emulated/0/Download/ZineScrapper")

        assertTrue(LibrarySyncEngine.isZineDirectory(dir1))
        assertTrue(LibrarySyncEngine.isZineDirectory(dir2))
        assertTrue(LibrarySyncEngine.isZineDirectory(dir3))
        assertTrue(LibrarySyncEngine.isZineDirectory(dir4))
        assertTrue(LibrarySyncEngine.isZineDirectory(dir5))
        assertTrue(LibrarySyncEngine.isZineDirectory(dir6))
        assertTrue(LibrarySyncEngine.isZineDirectory(dir7))
        assertTrue(LibrarySyncEngine.isZineDirectory(dir8))

        val nonZine1 = File("/storage/emulated/0/Download")
        val nonZine2 = File("/storage/emulated/0/Download/NCERT")
        val nonZine3 = File("/storage/emulated/0/Documents")
        val nonZine4 = File("/storage/emulated/0/Music")
        val nonZine5 = File("/storage/emulated/0/Movies")
        val nonZine6 = File("/storage/emulated/0/Download/MEGA Downloads")
        val nonZine7 = File("/storage/emulated/0/Download/test my app")
        val nonZine8 = File("/storage/emulated/0/Download/Videos")
        val nonZine9 = File("/storage/emulated/0/Download/Season 2")
        val nonZine10 = File("/storage/emulated/0/magazine")

        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine1))
        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine2))
        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine3))
        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine4))
        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine5))
        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine6))
        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine7))
        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine8))
        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine9))
        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine10))
    }

    @Test
    fun testIsInsideZineDirectoryDeepHierarchy() {
        val extStorage = tempFolder.newFolder("emulated_0")
        val zineFolder = File(extStorage, "Download/Zine").apply { mkdirs() }
        val nestedManga = File(zineFolder, "manga/solo_leveling/ch_01").apply { mkdirs() }
        val mangaImage = File(nestedManga, "001.jpg").apply { createNewFile() }

        // Must be recognized as inside Zine directory
        assertTrue(LibrarySyncEngine.isInsideZineDirectory(nestedManga))
        assertTrue(LibrarySyncEngine.isInsideZineDirectory(mangaImage))

        // Stray chemistry textbook in generic Download folder
        val ncertDir = File(extStorage, "Download/NCERT").apply { mkdirs() }
        val chemistryPdf = File(ncertDir, "Chemistry_Class_11.pdf").apply { createNewFile() }

        // Loose MP3 in Download
        val looseSong = File(extStorage, "Download/Song.mp3").apply { createNewFile() }

        // Loose document in Documents
        val documentsDir = File(extStorage, "Documents").apply { mkdirs() }
        val docFile = File(documentsDir, "homework.pdf").apply { createNewFile() }

        assertFalse("NCERT folder must not be inside Zine directory", LibrarySyncEngine.isInsideZineDirectory(ncertDir))
        assertFalse("Chemistry PDF must not be inside Zine directory", LibrarySyncEngine.isInsideZineDirectory(chemistryPdf))
        assertFalse("Loose song in Download must not be inside Zine directory", LibrarySyncEngine.isInsideZineDirectory(looseSong))
        assertFalse("Documents must not be inside Zine directory", LibrarySyncEngine.isInsideZineDirectory(docFile))

        // Files in Download/Videos, Download/Season 2, Download/test my app
        val videosDir = File(extStorage, "Download/Videos").apply { mkdirs() }
        val season2Dir = File(extStorage, "Download/Season 2").apply { mkdirs() }
        val testAppDir = File(extStorage, "Download/test my app").apply { mkdirs() }
        val videoFile = File(videosDir, "clip.mp4").apply { createNewFile() }
        val season2File = File(season2Dir, "ep1.mkv").apply { createNewFile() }
        val testAppFile = File(testAppDir, "song.mp3").apply { createNewFile() }

        assertFalse("Videos must not be inside Zine directory", LibrarySyncEngine.isInsideZineDirectory(videoFile))
        assertFalse("Season 2 must not be inside Zine directory", LibrarySyncEngine.isInsideZineDirectory(season2File))
        assertFalse("test my app must not be inside Zine directory", LibrarySyncEngine.isInsideZineDirectory(testAppFile))
    }

    @Test
    fun testDeepNestedZineScrapperDiscovery() {
        val extStorage = tempFolder.newFolder("deep_storage")

        // Deeply nested zine scrapper folder (depth 4)
        val deepZineScrapper = File(extStorage, "FolderA/FolderB/FolderC/zine scrapper").apply { mkdirs() }
        val deepMedia = File(deepZineScrapper, "manga/Omniscient").apply { mkdirs() }

        // Deeply nested Zine Scraper folder
        val nestedZineScraper = File(extStorage, "Download/CustomDownloads/Zine Scraper").apply { mkdirs() }

        val roots = LibrarySyncEngine.findZineRoots(extStorage)
        val canonicalRoots = roots.map { it.canonicalPath }

        assertTrue("Deep nested zine scrapper must be discovered", canonicalRoots.contains(deepZineScrapper.canonicalPath))
        assertTrue("Nested Zine Scraper must be discovered", canonicalRoots.contains(nestedZineScraper.canonicalPath))
        assertTrue("Media inside deep zine scrapper is recognized", LibrarySyncEngine.isInsideZineDirectory(deepMedia))
    }
}
