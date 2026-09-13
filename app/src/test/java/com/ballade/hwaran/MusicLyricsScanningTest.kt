package com.ballade.hwaran

import com.ballade.hwaran.data.importer.music.MusicImportUtils
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MusicLyricsScanningTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCase1_sideBySideLyrics() {
        val root = tempFolder.newFolder("Album1")
        File(root, "01 - Moonlight.mp3").createNewFile()
        File(root, "01 - Moonlight.lrc").writeText("[00:10.00] Moonlight lyrics")
        File(root, "02 - Starlight.flac").createNewFile()
        File(root, "02 - Starlight.txt").writeText("Starlight lyrics text")
        File(root, "03 - Sunshine.mp3").createNewFile()
        File(root, "03 - Sunshine.ser").writeText("Sunshine ser lyrics")
        File(root, "04 - Rain.ogg").createNewFile()
        File(root, "04 - Rain.usf").writeText("Rain usf lyrics")
        File(root, "ignore.pdf").createNewFile()

        val lyricsFiles = MusicImportUtils.findLyricsFiles(root)
        assertEquals(4, lyricsFiles.size)
        assertTrue(lyricsFiles.any { it.name == "01 - Moonlight.lrc" })
        assertTrue(lyricsFiles.any { it.name == "02 - Starlight.txt" })
        assertTrue(lyricsFiles.any { it.name == "03 - Sunshine.ser" })
        assertTrue(lyricsFiles.any { it.name == "04 - Rain.usf" })

        val match = MusicImportUtils.findMatchingLyricsFile("01 - Moonlight.mp3", "Moonlight", lyricsFiles)
        assertNotNull(match)
        assertEquals("01 - Moonlight.lrc", match?.name)
        val content = MusicImportUtils.readLyrics(match!!)
        assertEquals("[00:10.00] Moonlight lyrics", content)
    }

    @Test
    fun testCase2_lyricsSubfolder() {
        val root = tempFolder.newFolder("Album2")
        File(root, "Track01.mp3").createNewFile()
        File(root, "Track02.wav").createNewFile()

        val lyricsFolder = File(root, "lyrics")
        lyricsFolder.mkdir()
        File(lyricsFolder, "Track01.lrc").writeText("Lyrics for Track 01")
        File(lyricsFolder, "Track02.txt").writeText("Lyrics for Track 02")

        val lyricsFiles = MusicImportUtils.findLyricsFiles(root)
        assertEquals(2, lyricsFiles.size)

        val match1 = MusicImportUtils.findMatchingLyricsFile("Track01.mp3", "Track 01", lyricsFiles)
        assertNotNull(match1)
        assertEquals("Track01.lrc", match1?.name)
        assertEquals("Lyrics for Track 01", MusicImportUtils.readLyrics(match1!!))

        val match2 = MusicImportUtils.findMatchingLyricsFile("Track02.wav", "Track 02", lyricsFiles)
        assertNotNull(match2)
        assertEquals("Track02.txt", match2?.name)
        assertEquals("Lyrics for Track 02", MusicImportUtils.readLyrics(match2!!))
    }

    @Test
    fun testExtensionPriority_lrcPreferredOverTxt() {
        val root = tempFolder.newFolder("Album3")
        File(root, "Song.mp3").createNewFile()
        File(root, "Song.txt").writeText("TXT lyrics")
        File(root, "Song.lrc").writeText("LRC lyrics")

        val lyricsFiles = MusicImportUtils.findLyricsFiles(root)
        assertEquals(2, lyricsFiles.size)

        val match = MusicImportUtils.findMatchingLyricsFile("Song.mp3", "Song", lyricsFiles)
        assertNotNull(match)
        assertEquals("Song.lrc", match?.name)
        assertEquals("LRC lyrics", MusicImportUtils.readLyrics(match!!))
    }

    @Test
    fun testLyricsParser_standardLrc() {
        val lrc = """
            [ti:Moonlight]
            [ar:Artist]
            [00:04.50]First line of song
            [00:10.200]Second line of song
            [01:05.10]Third line after one minute
        """.trimIndent()

        val parsed = com.ballade.hwaran.frontend.player.music.LyricsParser.parse(lrc)
        assertEquals(3, parsed.size)
        assertEquals(4500L, parsed[0].timestampMs)
        assertEquals("First line of song", parsed[0].text)
        assertEquals(10200L, parsed[1].timestampMs)
        assertEquals("Second line of song", parsed[1].text)
        assertEquals(65100L, parsed[2].timestampMs)
        assertEquals("Third line after one minute", parsed[2].text)
    }

    @Test
    fun testLyricsParser_emptyOrPlainLyrics() {
        val emptyParsed = com.ballade.hwaran.frontend.player.music.LyricsParser.parse("")
        assertTrue(emptyParsed.isEmpty())

        val plainParsed = com.ballade.hwaran.frontend.player.music.LyricsParser.parse("Just plain text without timestamps")
        assertTrue(plainParsed.isEmpty())
    }

    @Test
    fun testLyricsParser_offsetAndBom() {
        val lrcWithOffset = """
            \uFEFF[offset: 500]
            [00:01.00]Line 1
            [00:02.00]Line 2
        """.trimIndent().replace("\\uFEFF", "\uFEFF")

        val parsed = com.ballade.hwaran.frontend.player.music.LyricsParser.parse(lrcWithOffset)
        assertEquals(2, parsed.size)
        assertEquals(1500L, parsed[0].timestampMs)
        assertEquals("Line 1", parsed[0].text)
        assertEquals(2500L, parsed[1].timestampMs)
        assertEquals("Line 2", parsed[1].text)
    }

    @Test
    fun testLyricsParser_multipleTimestampsOnSameLine() {
        val lrc = "[00:01.00][00:05.00] Repeated chorus line"
        val parsed = com.ballade.hwaran.frontend.player.music.LyricsParser.parse(lrc)
        assertEquals(2, parsed.size)
        assertEquals(1000L, parsed[0].timestampMs)
        assertEquals("Repeated chorus line", parsed[0].text)
        assertEquals(5000L, parsed[1].timestampMs)
        assertEquals("Repeated chorus line", parsed[1].text)
    }

    @Test
    fun testLyricsParser_hoursFormat() {
        val lrc = "[01:02:03.45] Long track line"
        val parsed = com.ballade.hwaran.frontend.player.music.LyricsParser.parse(lrc)
        assertEquals(1, parsed.size)
        // 1h = 3600000ms, 2m = 120000ms, 3s = 3000ms, 450ms = 3723450ms
        assertEquals(3723450L, parsed[0].timestampMs)
        assertEquals("Long track line", parsed[0].text)
    }

    @Test
    fun testLyricsParser_activeIndexTracking() {
        val lines = listOf(
            com.ballade.hwaran.frontend.player.music.LyricLine(5000L, "Line 1"),
            com.ballade.hwaran.frontend.player.music.LyricLine(10000L, "Line 2"),
            com.ballade.hwaran.frontend.player.music.LyricLine(15000L, "Line 3")
        )

        // Before first line (intro): index should be -1
        val beforeIntro = lines.indexOfLast { it.timestampMs <= 2000L }
        assertEquals(-1, beforeIntro)

        // At Line 1: index should be 0
        val atLine1 = lines.indexOfLast { it.timestampMs <= 5000L }
        assertEquals(0, atLine1)

        val duringLine1 = lines.indexOfLast { it.timestampMs <= 8000L }
        assertEquals(0, duringLine1)

        // At Line 2: index should be 1
        val atLine2 = lines.indexOfLast { it.timestampMs <= 10000L }
        assertEquals(1, atLine2)

        // Past Line 3: index should be 2
        val pastLine3 = lines.indexOfLast { it.timestampMs <= 20000L }
        assertEquals(2, pastLine3)
    }

    @Test
    fun testDetectStructure_albumWithLyricsFolder_isSingle() {
        // Exactly matches user folder structure:
        // ├── Born To Die.mp3
        // ├── Diet Mountain Dew.mp3
        // └── lyrics
        //     ├── Born To Die.lrc
        //     └── Diet Mountain Dew.lrc
        val root = tempFolder.newFolder("LanaDelRey")
        File(root, "Born To Die.mp3").createNewFile()
        File(root, "Diet Mountain Dew.mp3").createNewFile()
        val lyricsFolder = File(root, "lyrics")
        lyricsFolder.mkdir()
        File(lyricsFolder, "Born To Die.lrc").writeText("[00:01.00] Born to die")
        File(lyricsFolder, "Diet Mountain Dew.lrc").writeText("[00:01.00] Diet mountain dew")

        val structure = MusicImportUtils.detectStructure(root)
        assertEquals("SINGLE", structure)

        val lyricsFiles = MusicImportUtils.findLyricsFiles(root)
        assertEquals(2, lyricsFiles.size)

        val match1 = MusicImportUtils.findMatchingLyricsFile("Born To Die.mp3", "Born To Die", lyricsFiles)
        assertNotNull(match1)
        assertEquals("Born To Die.lrc", match1?.name)

        val match2 = MusicImportUtils.findMatchingLyricsFile("Diet Mountain Dew.mp3", "Diet Mountain Dew", lyricsFiles)
        assertNotNull(match2)
        assertEquals("Diet Mountain Dew.lrc", match2?.name)
    }

    @Test
    fun testDetectStructure_megaFolderWithSubAlbums_isMega() {
        val root = tempFolder.newFolder("MyMusicLibrary")
        val album1 = File(root, "Album1")
        album1.mkdir()
        File(album1, "Song1.mp3").createNewFile()

        val album2 = File(root, "Album2")
        album2.mkdir()
        File(album2, "Song2.mp3").createNewFile()

        val structure = MusicImportUtils.detectStructure(root)
        assertEquals("MEGA", structure)
    }
}
