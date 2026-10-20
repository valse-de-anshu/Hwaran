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
        val dir5 = File("/storage/emulated/0/Download/test my app")

        assertTrue(LibrarySyncEngine.isZineDirectory(dir1))
        assertTrue(LibrarySyncEngine.isZineDirectory(dir2))
        assertTrue(LibrarySyncEngine.isZineDirectory(dir3))
        assertTrue(LibrarySyncEngine.isZineDirectory(dir4))
        assertTrue(LibrarySyncEngine.isZineDirectory(dir5))

        val nonZine1 = File("/storage/emulated/0/Download")
        val nonZine2 = File("/storage/emulated/0/Download/NCERT")
        val nonZine3 = File("/storage/emulated/0/Documents")
        val nonZine4 = File("/storage/emulated/0/Music")
        val nonZine5 = File("/storage/emulated/0/Movies")
        val nonZine6 = File("/storage/emulated/0/Download/MEGA Downloads")

        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine1))
        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine2))
        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine3))
        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine4))
        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine5))
        assertFalse(LibrarySyncEngine.isZineDirectory(nonZine6))
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
    }
}
