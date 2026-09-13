package com.ballade.hwaran.frontend.home

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.util.CoverArtResolver

enum class SearchScope {
    ALL,
    TITLE,
    CREATOR,
    CONTAINER,
    TAGS,
    SECONDARY;

    fun getLabel(selectedMedia: String): String = when (this) {
        ALL -> "All Fields"
        TITLE -> when (selectedMedia) {
            "Music" -> "Song Title"
            "Book" -> "Book Title"
            "Manga", "Manhua" -> "Series Title"
            "Series", "Channel" -> "Video Title"
            else -> "Title / Song"
        }
        CREATOR -> when (selectedMedia) {
            "Music" -> "Artist"
            "Book" -> "Author"
            "Series", "Channel" -> "Creator / Channel"
            else -> "Artist / Author"
        }
        CONTAINER -> when (selectedMedia) {
            "Music" -> "Album / Playlist"
            "Book" -> "Shelf / Folder"
            "Manga", "Manhua" -> "Workspace / Folder"
            "Series", "Channel" -> "Show / Folder"
            else -> "Album / Shelf"
        }
        TAGS -> when (selectedMedia) {
            "Music" -> "Music Genre"
            "Book" -> "Category"
            "Manga", "Manhua" -> "Tropes / Genre"
            else -> "Genre / Tags"
        }
        SECONDARY -> when (selectedMedia) {
            "Music" -> "Song Lyrics"
            "Book" -> "Synopsis / Notes"
            "Manga", "Manhua" -> "Synopsis"
            "Series", "Channel" -> "Episode Title"
            else -> "Lyrics / Notes"
        }
    }

    companion object {
        fun getAvailableScopes(selectedMedia: String): List<SearchScope> = listOf(ALL, TITLE, CREATOR, CONTAINER, TAGS, SECONDARY)
    }
}

enum class DurationFilter(val musicLabel: String, val videoLabel: String) {
    ALL("Any Length", "Any Length"),
    SHORT("< 3 mins", "< 10 mins"),
    MEDIUM("3 - 5 mins", "10 - 30 mins"),
    LONG("> 5 mins", "> 30 mins");

    fun getLabel(selectedMedia: String): String =
        if (selectedMedia == "Series" || selectedMedia == "Channel") videoLabel else musicLabel
}

enum class ProgressFilter(val label: String) {
    ALL("All Status"),
    UNREAD("Unread"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed")
}

enum class ChapterCountFilter(val label: String) {
    ALL("Any Chapters"),
    SHORT("< 10 chapters"),
    MEDIUM("10 - 50 chapters"),
    LONG("> 50 chapters")
}

enum class SearchSort(val label: String) {
    NEWEST("Newest Added"),
    TITLE_AZ("Title (A-Z)"),
    TITLE_ZA("Title (Z-A)"),
    ARTIST_AZ("Artist / Author (A-Z)"),
    DURATION("Duration / Length"),
    MOST_PLAYED("Most Played / Opened"),
    CHAPTER_COUNT("Chapter Count");

    companion object {
        fun getAvailableSorts(selectedMedia: String): List<SearchSort> = when (selectedMedia) {
            "Music" -> listOf(NEWEST, TITLE_AZ, TITLE_ZA, ARTIST_AZ, DURATION, MOST_PLAYED)
            "Book" -> listOf(NEWEST, TITLE_AZ, TITLE_ZA, MOST_PLAYED)
            "Manga", "Manhua" -> listOf(NEWEST, TITLE_AZ, TITLE_ZA, CHAPTER_COUNT, MOST_PLAYED)
            "Series", "Channel" -> listOf(NEWEST, TITLE_AZ, TITLE_ZA, DURATION, CHAPTER_COUNT, MOST_PLAYED)
            else -> listOf(NEWEST, TITLE_AZ, TITLE_ZA, MOST_PLAYED)
        }
    }
}

sealed interface SearchResultItem {
    val id: String
    val title: String

    data class Song(
        val chapter: ChapterEntity,
        val parentManga: MangaEntity?,
        val allChaptersInPlaylist: List<ChapterEntity>,
        val trackIndex: Int
    ) : SearchResultItem {
        override val id: String = "song_${chapter.id}"
        override val title: String = chapter.title
    }

    data class Media(
        val manga: MangaEntity,
        val trackCount: Int = 0
    ) : SearchResultItem {
        override val id: String = "media_${manga.id}"
        override val title: String = manga.title
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeSearchView(
    allManga: List<MangaEntity>,
    onNavigateToDescription: (Long) -> Unit,
    onPlaySong: ((MangaEntity, List<ChapterEntity>, Int) -> Unit)? = null,
    onBack: () -> Unit,
    glowColor: Color = Color(0xFF9C27B0),
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMedia by remember { mutableStateOf("All") } // "All", "Music", "Book", "Manhua", "Manga", "Series", "Channel", "Favorite"
    var selectedScope by remember { mutableStateOf(SearchScope.ALL) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedWorkspace by remember { mutableStateOf<String?>(null) }
    var selectedDuration by remember { mutableStateOf(DurationFilter.ALL) }
    var selectedProgress by remember { mutableStateOf(ProgressFilter.ALL) }
    var selectedChapterCount by remember { mutableStateOf(ChapterCountFilter.ALL) }
    var onlyFavorites by remember { mutableStateOf(false) }
    var selectedSort by remember { mutableStateOf(SearchSort.NEWEST) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var matchAllTags by remember { mutableStateOf(false) }
    var showAdvancedPanel by remember { mutableStateOf(false) }
    var tagFilterQuery by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(selectedMedia) {
        selectedWorkspace = null
        when (selectedMedia) {
            "Book" -> {
                selectedArtist = null
                selectedAlbum = null
                selectedDuration = DurationFilter.ALL
                selectedChapterCount = ChapterCountFilter.ALL
            }
            "Music" -> {
                selectedProgress = ProgressFilter.ALL
                selectedChapterCount = ChapterCountFilter.ALL
            }
            "Manga", "Manhua" -> {
                selectedArtist = null
                selectedAlbum = null
                selectedDuration = DurationFilter.ALL
            }
            "Series", "Channel" -> {
                selectedAlbum = null
                selectedChapterCount = ChapterCountFilter.ALL
            }
        }
    }

    val database = remember(context) { AppDatabase.getDatabase(context) }
    val allChapters by database.trackDao().getAllChaptersFlow().collectAsState(initial = emptyList())
    val chaptersByMangaId = remember(allChapters) { allChapters.groupBy { it.mangaId } }
    val mangaById = remember(allManga) { allManga.associateBy { it.id } }

    // Extract unique tags present in the user's library filtered by selected media genre
    val libraryTags = remember(allManga, allChapters, selectedMedia) {
        val filteredManga = when (selectedMedia) {
            "All" -> allManga
            "Favorite" -> allManga.filter { it.isFavorite }
            else -> allManga.filter { matchesMediaType(it, selectedMedia) }
        }
        val mangaTags = filteredManga.flatMap { manga ->
            manga.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && !it.equals("favorite", ignoreCase = true) } ?: emptyList()
        }
        val chapterTags = if (selectedMedia in listOf("All", "Music", "Favorite")) {
            allChapters.flatMap { chapter ->
                chapter.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && !it.equals("favorite", ignoreCase = true) } ?: emptyList()
            }
        } else emptyList()

        val extracted = (mangaTags + chapterTags).distinct().sorted()

        if (extracted.isNotEmpty()) extracted
        else when (selectedMedia) {
            "Music" -> listOf("Pop", "Rock", "Lo-Fi", "Classical", "Jazz", "Electronic", "Acoustic", "Hip Hop", "OST", "R&B")
            "Book" -> listOf("Fiction", "Non-Fiction", "Novel", "Science", "History", "Philosophy", "Biography", "Self-Help", "Tutorial", "Classic")
            "Manga", "Manhua" -> listOf("Action", "Romance", "Comedy", "Fantasy", "Sci-Fi", "Mystery", "Horror", "Drama", "Isekai", "Slice of Life")
            "Series", "Channel" -> listOf("Anime", "Documentary", "Educational", "Movie", "Live", "Animation", "Drama", "Tutorial")
            else -> listOf("Action", "Romance", "Comedy", "Fantasy", "Sci-Fi", "Mystery", "Lo-Fi", "Pop", "Documentary")
        }
    }

    // Extract all unique artists across the music library
    val libraryArtists = remember(allChapters) {
        allChapters.mapNotNull { it.artist?.trim() }
            .filter { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
            .distinct()
            .sorted()
    }

    // Extract all unique albums / playlists
    val libraryAlbums = remember(allManga) {
        allManga.filter { it.contentType == 3 || it.boxPurpose == "music" }
            .map { it.title.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    // Extract shelves / workspaces for the current media category
    val libraryWorkspaces = remember(allManga, selectedMedia) {
        val relevant = if (selectedMedia in listOf("All", "Favorite")) allManga
        else allManga.filter { matchesMediaType(it, selectedMedia) }

        relevant.mapNotNull { it.workspace?.trim() }
            .filter { it.isNotBlank() && !it.equals("default", ignoreCase = true) }
            .distinct()
            .sorted()
    }

    val mediaTypes = remember {
        listOf("All", "Music", "Book", "Manhua", "Manga", "Series", "Channel", "Favorite")
    }

    val activeFilterCount = remember(
        selectedMedia,
        selectedScope,
        selectedArtist,
        selectedAlbum,
        selectedWorkspace,
        selectedDuration,
        selectedProgress,
        selectedChapterCount,
        onlyFavorites,
        selectedSort,
        selectedTags
    ) {
        var count = 0
        if (selectedMedia != "All") count++
        if (selectedScope != SearchScope.ALL) count++
        if (selectedArtist != null) count++
        if (selectedAlbum != null) count++
        if (selectedWorkspace != null) count++
        if (selectedDuration != DurationFilter.ALL) count++
        if (selectedProgress != ProgressFilter.ALL) count++
        if (selectedChapterCount != ChapterCountFilter.ALL) count++
        if (onlyFavorites) count++
        if (selectedSort != SearchSort.NEWEST) count++
        if (selectedTags.isNotEmpty()) count += selectedTags.size
        count
    }

    // Filter and sort the collection
    val filteredResults: List<SearchResultItem> = remember(
        allManga,
        allChapters,
        chaptersByMangaId,
        mangaById,
        searchQuery,
        selectedMedia,
        selectedScope,
        selectedArtist,
        selectedAlbum,
        selectedWorkspace,
        selectedDuration,
        selectedProgress,
        selectedChapterCount,
        onlyFavorites,
        selectedSort,
        selectedTags,
        matchAllTags
    ) {
        val q = searchQuery.trim().lowercase()
        val artistFilter = selectedArtist
        val albumFilter = selectedAlbum
        val workspaceFilter = selectedWorkspace
        val durationFilter = selectedDuration
        val progressFilter = selectedProgress
        val chapterCountFilter = selectedChapterCount
        val nonNsfwManga = allManga.filter { !it.isNsfw }
        val musicMangaMap = nonNsfwManga.filter { it.contentType == 3 || it.boxPurpose == "music" }.associateBy { it.id }

        // Determine if we should evaluate songs
        val shouldEvaluateSongs = when (selectedMedia) {
            "Book", "Manhua", "Manga", "Series", "Channel" -> false
            else -> true // "All", "Music", "Favorite"
        }

        // 1. Evaluate Songs
        val matchingSongs = if (!shouldEvaluateSongs) {
            emptyList()
        } else {
            val evaluateAllSongs = searchQuery.isNotBlank() ||
                    selectedMedia == "Music" ||
                    artistFilter != null ||
                    albumFilter != null ||
                    workspaceFilter != null ||
                    durationFilter != DurationFilter.ALL ||
                    selectedScope in listOf(SearchScope.CREATOR, SearchScope.SECONDARY)

            if (!evaluateAllSongs) {
                emptyList()
            } else {
                allChapters.filter { chapter ->
                    val parentManga = musicMangaMap[chapter.mangaId] ?: mangaById[chapter.mangaId]
                    val isMusicTrack = (parentManga?.contentType == 3 || parentManga?.boxPurpose == "music") ||
                            chapter.duration > 0 ||
                            !chapter.artist.isNullOrBlank()

                    if (!isMusicTrack) return@filter false

                    // Favorites filter
                    if (onlyFavorites) {
                        val isFav = parentManga?.isFavorite == true || chapter.genre?.contains("favorite", ignoreCase = true) == true
                        if (!isFav) return@filter false
                    }

                    // Artist filter
                    if (artistFilter != null) {
                        if (!chapter.artist.equals(artistFilter, ignoreCase = true)) return@filter false
                    }

                    // Album filter
                    if (albumFilter != null) {
                        if (parentManga == null || !parentManga.title.equals(albumFilter, ignoreCase = true)) return@filter false
                    }

                    // Workspace / Folder filter
                    if (workspaceFilter != null) {
                        if (parentManga == null || !parentManga.workspace.equals(workspaceFilter, ignoreCase = true)) return@filter false
                    }

                    // Duration filter
                    when (durationFilter) {
                        DurationFilter.ALL -> {}
                        DurationFilter.SHORT -> if (chapter.duration !in 1..179999) return@filter false
                        DurationFilter.MEDIUM -> if (chapter.duration !in 180000..300000) return@filter false
                        DurationFilter.LONG -> if (chapter.duration <= 300000) return@filter false
                    }

                    // Tags filter
                    if (selectedTags.isNotEmpty()) {
                        val chTags = chapter.genre?.split(",")?.map { it.trim().lowercase() } ?: emptyList()
                        val pTags = parentManga?.genre?.split(",")?.map { it.trim().lowercase() } ?: emptyList()
                        val combined = (chTags + pTags).toSet()
                        val matchesTags = if (matchAllTags) {
                            selectedTags.all { t -> combined.any { it.contains(t.lowercase()) } }
                        } else {
                            selectedTags.any { t -> combined.any { it.contains(t.lowercase()) } }
                        }
                        if (!matchesTags) return@filter false
                    }

                    // Query Search & Scope
                    if (q.isNotBlank()) {
                        val matchesQuery = when (selectedScope) {
                            SearchScope.ALL -> {
                                chapter.title.lowercase().contains(q) ||
                                (chapter.artist?.lowercase()?.contains(q) == true) ||
                                (parentManga?.title?.lowercase()?.contains(q) == true) ||
                                (parentManga?.workspace?.lowercase()?.contains(q) == true) ||
                                (chapter.genre?.lowercase()?.contains(q) == true) ||
                                (chapter.lyrics?.lowercase()?.contains(q) == true)
                            }
                            SearchScope.TITLE -> chapter.title.lowercase().contains(q)
                            SearchScope.CREATOR -> chapter.artist?.lowercase()?.contains(q) == true
                            SearchScope.CONTAINER -> parentManga?.title?.lowercase()?.contains(q) == true || parentManga?.workspace?.lowercase()?.contains(q) == true
                            SearchScope.TAGS -> chapter.genre?.lowercase()?.contains(q) == true || parentManga?.genre?.lowercase()?.contains(q) == true
                            SearchScope.SECONDARY -> chapter.lyrics?.lowercase()?.contains(q) == true
                        }
                        if (!matchesQuery) return@filter false
                    }

                    true
                }.map { chapter ->
                    val parentManga = musicMangaMap[chapter.mangaId] ?: mangaById[chapter.mangaId]
                    val playlistChapters = chaptersByMangaId[chapter.mangaId] ?: listOf(chapter)
                    val idx = playlistChapters.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0)
                    SearchResultItem.Song(
                        chapter = chapter,
                        parentManga = parentManga,
                        allChaptersInPlaylist = playlistChapters,
                        trackIndex = idx
                    )
                }
            }
        }

        // 2. Evaluate Media Items (Books, Manga, Videos, Playlists)
        val matchingMedia = nonNsfwManga.filter { manga ->
            if (!matchesMediaType(manga, selectedMedia)) return@filter false

            if (onlyFavorites && !manga.isFavorite) return@filter false

            val mangaChapters = chaptersByMangaId[manga.id] ?: emptyList()

            if (artistFilter != null) {
                val hasArtist = mangaChapters.any { it.artist.equals(artistFilter, ignoreCase = true) } ||
                        manga.description.contains(artistFilter, ignoreCase = true)
                if (!hasArtist) return@filter false
            }

            if (albumFilter != null) {
                if (!manga.title.equals(albumFilter, ignoreCase = true)) return@filter false
            }

            if (workspaceFilter != null) {
                if (!manga.workspace.equals(workspaceFilter, ignoreCase = true)) return@filter false
            }

            if (durationFilter != DurationFilter.ALL) {
                val isVideo = manga.contentType == 2 || manga.boxPurpose == "series" || manga.boxPurpose == "channel"
                val hasMatchingDuration = mangaChapters.any { ch ->
                    if (isVideo) {
                        when (durationFilter) {
                            DurationFilter.SHORT -> ch.duration in 1..599999
                            DurationFilter.MEDIUM -> ch.duration in 600000..1800000
                            DurationFilter.LONG -> ch.duration > 1800000
                            else -> true
                        }
                    } else {
                        when (durationFilter) {
                            DurationFilter.SHORT -> ch.duration in 1..179999
                            DurationFilter.MEDIUM -> ch.duration in 180000..300000
                            DurationFilter.LONG -> ch.duration > 300000
                            else -> true
                        }
                    }
                }
                if (!hasMatchingDuration) return@filter false
            }

            // Reading / Watch Progress filter
            when (progressFilter) {
                ProgressFilter.ALL -> {}
                ProgressFilter.UNREAD -> {
                    if (manga.lastReadPage != null && manga.lastReadPage > 0) return@filter false
                }
                ProgressFilter.IN_PROGRESS -> {
                    if (manga.lastReadPage == null || manga.lastReadPage <= 0) return@filter false
                }
                ProgressFilter.COMPLETED -> {
                    if (manga.openCount <= 0) return@filter false
                }
            }

            // Chapter / Episode count filter
            when (chapterCountFilter) {
                ChapterCountFilter.ALL -> {}
                ChapterCountFilter.SHORT -> if (mangaChapters.isEmpty() || mangaChapters.size >= 10) return@filter false
                ChapterCountFilter.MEDIUM -> if (mangaChapters.size !in 10..50) return@filter false
                ChapterCountFilter.LONG -> if (mangaChapters.size <= 50) return@filter false
            }

            if (selectedTags.isNotEmpty()) {
                val mTags = manga.genre?.split(",")?.map { it.trim().lowercase() } ?: emptyList()
                val chTags = mangaChapters.flatMap { it.genre?.split(",")?.map { g -> g.trim().lowercase() } ?: emptyList() }
                val combined = (mTags + chTags).toSet()
                val matchesTags = if (matchAllTags) {
                    selectedTags.all { t -> combined.any { it.contains(t.lowercase()) } }
                } else {
                    selectedTags.any { t -> combined.any { it.contains(t.lowercase()) } }
                }
                if (!matchesTags) return@filter false
            }

            if (q.isNotBlank()) {
                val matchesQuery = when (selectedScope) {
                    SearchScope.ALL -> {
                        manga.title.lowercase().contains(q) ||
                        (manga.genre?.lowercase()?.contains(q) == true) ||
                        (manga.workspace?.lowercase()?.contains(q) == true) ||
                        manga.description.lowercase().contains(q) ||
                        manga.thoughts.lowercase().contains(q) ||
                        mangaChapters.any { it.title.lowercase().contains(q) || it.artist?.lowercase()?.contains(q) == true }
                    }
                    SearchScope.TITLE -> manga.title.lowercase().contains(q)
                    SearchScope.CREATOR -> {
                        mangaChapters.any { it.artist?.lowercase()?.contains(q) == true } ||
                        manga.description.lowercase().contains(q)
                    }
                    SearchScope.CONTAINER -> {
                        manga.workspace?.lowercase()?.contains(q) == true ||
                        (manga.boxLabel?.lowercase()?.contains(q) == true)
                    }
                    SearchScope.TAGS -> manga.genre?.lowercase()?.contains(q) == true
                    SearchScope.SECONDARY -> {
                        manga.description.lowercase().contains(q) ||
                        manga.thoughts.lowercase().contains(q) ||
                        mangaChapters.any { it.title.lowercase().contains(q) }
                    }
                }
                if (!matchesQuery) return@filter false
            }

            true
        }.map { manga ->
            SearchResultItem.Media(
                manga = manga,
                trackCount = (chaptersByMangaId[manga.id] ?: emptyList()).size
            )
        }

        // Combine: Matching Songs are placed first, followed by Albums / Series / Books
        val combined: List<SearchResultItem> = matchingSongs + matchingMedia

        when (selectedSort) {
            SearchSort.NEWEST -> combined.sortedByDescending {
                when (it) {
                    is SearchResultItem.Song -> it.chapter.id
                    is SearchResultItem.Media -> it.manga.id
                }
            }
            SearchSort.TITLE_AZ -> combined.sortedBy { it.title.lowercase() }
            SearchSort.TITLE_ZA -> combined.sortedByDescending { it.title.lowercase() }
            SearchSort.ARTIST_AZ -> combined.sortedBy {
                when (it) {
                    is SearchResultItem.Song -> it.chapter.artist?.lowercase() ?: ""
                    is SearchResultItem.Media -> it.manga.title.lowercase()
                }
            }
            SearchSort.DURATION -> combined.sortedByDescending {
                when (it) {
                    is SearchResultItem.Song -> it.chapter.duration
                    is SearchResultItem.Media -> (chaptersByMangaId[it.manga.id] ?: emptyList()).sumOf { c -> c.duration }
                }
            }
            SearchSort.MOST_PLAYED -> combined.sortedByDescending {
                when (it) {
                    is SearchResultItem.Song -> it.chapter.openCount
                    is SearchResultItem.Media -> it.manga.openCount
                }
            }
            SearchSort.CHAPTER_COUNT -> combined.sortedByDescending {
                when (it) {
                    is SearchResultItem.Song -> 1
                    is SearchResultItem.Media -> (chaptersByMangaId[it.manga.id] ?: emptyList()).size
                }
            }
        }
    }

    val (songCount, mediaCount) = remember(filteredResults) {
        val songs = filteredResults.count { it is SearchResultItem.Song }
        val media = filteredResults.count { it is SearchResultItem.Media }
        songs to media
    }

    val resultSummary = remember(
        filteredResults,
        selectedMedia,
        selectedArtist,
        selectedAlbum,
        selectedWorkspace,
        selectedDuration,
        selectedProgress,
        selectedChapterCount,
        onlyFavorites,
        selectedTags
    ) {
        buildString {
            if (songCount > 0 && mediaCount > 0) {
                append("$songCount song${if (songCount != 1) "s" else ""}, $mediaCount item${if (mediaCount != 1) "s" else ""}")
            } else if (songCount > 0) {
                append("$songCount song${if (songCount != 1) "s" else ""}")
            } else {
                append("$mediaCount result${if (mediaCount != 1) "s" else ""}")
            }
            if (selectedMedia != "All") append(" in $selectedMedia")
            if (selectedArtist != null) append(" • Artist: $selectedArtist")
            if (selectedAlbum != null) append(" • Album: $selectedAlbum")
            if (selectedWorkspace != null) append(" • Folder: $selectedWorkspace")
            if (selectedDuration != DurationFilter.ALL) append(" • ${selectedDuration.getLabel(selectedMedia)}")
            if (selectedProgress != ProgressFilter.ALL) append(" • ${selectedProgress.label}")
            if (selectedChapterCount != ChapterCountFilter.ALL) append(" • ${selectedChapterCount.label}")
            if (onlyFavorites) append(" • ⭐ Favorites")
            if (selectedTags.isNotEmpty()) append(" • ${selectedTags.size} tags")
        }
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gestureBottom = WindowInsets.systemGestures.asPaddingValues().calculateBottomPadding()
    val bottomClearance = maxOf(navBarBottom + 16.dp, maxOf(gestureBottom + 12.dp, 24.dp))

    val CardBg = MaterialTheme.colorScheme.surface
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val PrimaryPurple = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = bottomClearance)
    ) {
        // ── 1. Search Header Row (Back, Input, Tune/Filter Button) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Search Text Field with Scope Hint
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .focusRequester(focusRequester),
                shape = RoundedCornerShape(26.dp),
                placeholder = {
                    Text(
                        when (selectedScope) {
                            SearchScope.ALL -> when (selectedMedia) {
                                "Music" -> "Search all (song, artist, album, lyrics)..."
                                "Book" -> "Search books (title, author, synopsis, shelf)..."
                                "Manga", "Manhua" -> "Search manga (title, synopsis, genre)..."
                                "Series", "Channel" -> "Search videos (title, creator, episode)..."
                                else -> "Search vault (song, book, comic, video)..."
                            }
                            SearchScope.TITLE -> when (selectedMedia) {
                                "Music" -> "Search by song title..."
                                "Book" -> "Search by book title..."
                                "Manga", "Manhua" -> "Search by series title..."
                                "Series", "Channel" -> "Search by video title..."
                                else -> "Search by title..."
                            }
                            SearchScope.CREATOR -> when (selectedMedia) {
                                "Music" -> "Search by artist..."
                                "Book" -> "Search by author..."
                                "Series", "Channel" -> "Search by creator or channel..."
                                else -> "Search by artist or author..."
                            }
                            SearchScope.CONTAINER -> when (selectedMedia) {
                                "Music" -> "Search by album or playlist..."
                                "Book" -> "Search by shelf or folder..."
                                "Manga", "Manhua" -> "Search by workspace..."
                                "Series", "Channel" -> "Search by show or folder..."
                                else -> "Search by album or shelf..."
                            }
                            SearchScope.TAGS -> when (selectedMedia) {
                                "Music" -> "Search by music genre..."
                                "Book" -> "Search by category or genre..."
                                "Manga", "Manhua" -> "Search by tropes or genre..."
                                else -> "Search by tags..."
                            }
                            SearchScope.SECONDARY -> when (selectedMedia) {
                                "Music" -> "Search inside song lyrics..."
                                "Book" -> "Search inside synopsis & notes..."
                                "Manga", "Manhua" -> "Search inside synopsis..."
                                "Series", "Channel" -> "Search episode titles..."
                                else -> "Search lyrics or content..."
                            }
                        },
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = glowColor
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear",
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xEE1A1824),
                    unfocusedContainerColor = Color(0xEE15141E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = glowColor,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Advanced Filter Toggle Button with Badge
            Box(contentAlignment = Alignment.TopEnd) {
                IconButton(
                    onClick = { showAdvancedPanel = !showAdvancedPanel },
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (showAdvancedPanel || activeFilterCount > 0) glowColor.copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.08f),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            if (showAdvancedPanel || activeFilterCount > 0) glowColor.copy(alpha = 0.6f)
                            else Color.White.copy(alpha = 0.12f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Advanced Filters",
                        tint = if (showAdvancedPanel || activeFilterCount > 0) glowColor else Color.White
                    )
                }

                if (activeFilterCount > 0) {
                    Surface(
                        modifier = Modifier
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(18.dp),
                        shape = CircleShape,
                        color = glowColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$activeFilterCount",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ── 2. Quick Media Type Selector Pills ──
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(mediaTypes) { media ->
                val isSelected = selectedMedia == media
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) glowColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, if (isSelected) glowColor else Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { selectedMedia = media }
                ) {
                    Text(
                        text = media,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // ── 3. Expandable Advanced Search & Filter Panel ──
        AnimatedVisibility(
            visible = showAdvancedPanel,
            enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = glowColor.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(20.dp),
                color = CardBg,
                border = BorderStroke(1.dp, glowColor.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header: Advanced Filters Title + Reset Button
                    // Header: Dynamic Title + Reset Button
                    val panelTitle = when (selectedMedia) {
                        "Music" -> "Music & Audio Filters"
                        "Book" -> "Books & Documents Filters"
                        "Manga" -> "Manga & Comics Filters"
                        "Manhua" -> "Manhua & Webtoons Filters"
                        "Series" -> "Video & Series Filters"
                        "Channel" -> "Channels & Streams Filters"
                        "Favorite" -> "Favorites Vault Filters"
                        else -> "Vault Advanced Filters"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = panelTitle,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (activeFilterCount > 0) {
                            Text(
                                text = "Reset All",
                                color = Color(0xFFE57373),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedMedia = "All"
                                        selectedScope = SearchScope.ALL
                                        selectedArtist = null
                                        selectedAlbum = null
                                        selectedWorkspace = null
                                        selectedDuration = DurationFilter.ALL
                                        selectedProgress = ProgressFilter.ALL
                                        selectedChapterCount = ChapterCountFilter.ALL
                                        onlyFavorites = false
                                        selectedSort = SearchSort.NEWEST
                                        selectedTags = emptySet()
                                        tagFilterQuery = ""
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Search Scope Selection ──
                    Text(
                        text = "Search Target",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(SearchScope.getAvailableScopes(selectedMedia)) { scope ->
                            val isSelected = selectedScope == scope
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) glowColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, if (isSelected) glowColor else Color.White.copy(alpha = 0.1f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedScope = scope }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = scope.getLabel(selectedMedia),
                                        color = if (isSelected) Color.White else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // ── Reading / Watch Progress Section ──
                    if (selectedMedia != "Music") {
                        val statusLabel = when (selectedMedia) {
                            "Series", "Channel" -> "Watch Status"
                            "Book", "Manga", "Manhua" -> "Reading Status"
                            else -> "Reading / Watch Status"
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = statusLabel,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(ProgressFilter.values()) { prog ->
                                val isSelected = selectedProgress == prog
                                val progLabel = when (prog) {
                                    ProgressFilter.ALL -> "All Status"
                                    ProgressFilter.UNREAD -> if (selectedMedia in listOf("Series", "Channel")) "Unwatched" else "Unread"
                                    ProgressFilter.IN_PROGRESS -> if (selectedMedia in listOf("Series", "Channel")) "Watching" else "Reading"
                                    ProgressFilter.COMPLETED -> "Finished"
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) glowColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(1.dp, if (isSelected) glowColor else Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedProgress = prog }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = progLabel,
                                            color = if (isSelected) Color.White else TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Chapter / Episode Count Filter ──
                    if (selectedMedia in listOf("Manga", "Manhua", "Series")) {
                        val countTitle = if (selectedMedia == "Series") "Episode Count" else "Chapter Count"
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = countTitle,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(ChapterCountFilter.values()) { cFilter ->
                                val isSelected = selectedChapterCount == cFilter
                                val cLabel = when (cFilter) {
                                    ChapterCountFilter.ALL -> "Any"
                                    ChapterCountFilter.SHORT -> if (selectedMedia == "Series") "< 10 episodes" else "< 10 chapters"
                                    ChapterCountFilter.MEDIUM -> if (selectedMedia == "Series") "10 - 50 episodes" else "10 - 50 chapters"
                                    ChapterCountFilter.LONG -> if (selectedMedia == "Series") "> 50 episodes" else "> 50 chapters"
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) glowColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(1.dp, if (isSelected) glowColor else Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedChapterCount = cFilter }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cLabel,
                                            color = if (isSelected) Color.White else TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Filter by Shelf / Folder / Workspace Section ──
                    if (selectedMedia != "Music" && libraryWorkspaces.isNotEmpty()) {
                        val wsTitle = when (selectedMedia) {
                            "Book" -> "Filter by Shelf / Folder"
                            "Manga", "Manhua" -> "Filter by Workspace"
                            "Series", "Channel" -> "Filter by Channel / Folder"
                            else -> "Filter by Shelf / Folder"
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = wsTitle,
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedWorkspace != null) {
                                Text(
                                    text = "Clear",
                                    color = Color(0xFFE57373),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { selectedWorkspace = null }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(libraryWorkspaces) { ws ->
                                val isSelected = selectedWorkspace.equals(ws, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFF42A5F5).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF42A5F5) else Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedWorkspace = if (isSelected) null else ws
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFF42A5F5) else Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = ws,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Filter by Artist Section ──
                    if (selectedMedia in listOf("Music", "All", "Favorite") && libraryArtists.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filter by Artist",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedArtist != null) {
                                Text(
                                    text = "Clear",
                                    color = Color(0xFFE57373),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { selectedArtist = null }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(libraryArtists) { artist ->
                                val isSelected = selectedArtist.equals(artist, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFFEC407A).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFEC407A) else Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedArtist = if (isSelected) null else artist
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Rounded.Person, contentDescription = null, tint = if (isSelected) Color(0xFFEC407A) else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                                        Text(
                                            text = artist,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Filter by Album Section ──
                    if (selectedMedia == "Music" && libraryAlbums.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filter by Album / Playlist",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedAlbum != null) {
                                Text(
                                    text = "Clear",
                                    color = Color(0xFFE57373),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { selectedAlbum = null }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(libraryAlbums) { album ->
                                val isSelected = selectedAlbum.equals(album, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFFEC407A).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFEC407A) else Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedAlbum = if (isSelected) null else album
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Rounded.Album, contentDescription = null, tint = if (isSelected) Color(0xFFEC407A) else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                                        Text(
                                            text = album,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Duration Filter Section ──
                    if (selectedMedia in listOf("Music", "Series", "Channel", "All", "Favorite")) {
                        val durationTitle = when (selectedMedia) {
                            "Series", "Channel" -> "Video Length"
                            "Music" -> "Track Duration"
                            else -> "Length / Duration"
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = durationTitle,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(DurationFilter.values()) { dur ->
                                val isSelected = selectedDuration == dur
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) glowColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(1.dp, if (isSelected) glowColor else Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedDuration = dur }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dur.getLabel(selectedMedia),
                                            color = if (isSelected) Color.White else TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Favorites Only Filter ──
                    if (selectedMedia != "Favorite") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (onlyFavorites) Color(0xFFFFD54F).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, if (onlyFavorites) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onlyFavorites = !onlyFavorites }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (onlyFavorites) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                    contentDescription = null,
                                    tint = if (onlyFavorites) Color(0xFFFFD54F) else TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "⭐ Only Favorites",
                                    color = if (onlyFavorites) Color.White else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = if (onlyFavorites) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val tagsSectionTitle = when (selectedMedia) {
                        "Music" -> "Filter by Music Genre (${selectedTags.size})"
                        "Book" -> "Filter by Category (${selectedTags.size})"
                        "Manga", "Manhua" -> "Filter by Tropes & Genre (${selectedTags.size})"
                        "Series", "Channel" -> "Filter by Video Genre (${selectedTags.size})"
                        else -> "Filter by Tags (${selectedTags.size})"
                    }

                    // ── Tags Filter Section ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tagsSectionTitle,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (selectedTags.size > 1) {
                            Text(
                                text = if (matchAllTags) "Match: ALL (AND)" else "Match: ANY (OR)",
                                color = glowColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { matchAllTags = !matchAllTags }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Active Tag Chips
                    if (selectedTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            selectedTags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = glowColor.copy(alpha = 0.25f),
                                    border = BorderStroke(1.dp, glowColor.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = tag,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        IconButton(
                                            onClick = { selectedTags = selectedTags - tag },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Remove $tag",
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Tag Picker
                    val availableTagsToPick = remember(libraryTags, selectedTags, tagFilterQuery) {
                        libraryTags.filter { tag ->
                            !selectedTags.contains(tag) &&
                            (tagFilterQuery.isBlank() || tag.contains(tagFilterQuery, ignoreCase = true))
                        }
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(availableTagsToPick.take(25)) { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedTags = selectedTags + tag }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = glowColor, modifier = Modifier.size(12.dp))
                                    Text(
                                        text = tag,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── Sort Order Selection ──
                    Text(
                        text = "Sort Order",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(SearchSort.getAvailableSorts(selectedMedia)) { sort ->
                            val isSelected = selectedSort == sort
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) glowColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, if (isSelected) glowColor else Color.White.copy(alpha = 0.1f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedSort = sort }
                            ) {
                                Text(
                                    text = sort.label,
                                    color = if (isSelected) Color.White else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 4. Results Count & Active Filters Summary ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = resultSummary,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Text(
                text = selectedSort.label,
                color = glowColor.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // ── 5. Results 3-Column Grid ──
        if (filteredResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SearchOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (searchQuery.isBlank() && activeFilterCount == 0) "No items found in vault"
                               else "No results found matching your criteria",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    if (activeFilterCount > 0 || searchQuery.isNotBlank()) {
                        Button(
                            onClick = {
                                selectedMedia = "All"
                                selectedScope = SearchScope.ALL
                                selectedArtist = null
                                selectedAlbum = null
                                selectedWorkspace = null
                                selectedDuration = DurationFilter.ALL
                                selectedProgress = ProgressFilter.ALL
                                selectedChapterCount = ChapterCountFilter.ALL
                                onlyFavorites = false
                                selectedSort = SearchSort.NEWEST
                                selectedTags = emptySet()
                                tagFilterQuery = ""
                                searchQuery = ""
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = glowColor.copy(alpha = 0.2f),
                                contentColor = glowColor
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Reset Search & Filters", color = glowColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredResults, key = { it.id }) { item ->
                    when (item) {
                        is SearchResultItem.Song -> {
                            SearchSongItemCard(
                                song = item,
                                glowColor = glowColor,
                                context = context,
                                onPlay = {
                                    Toast.makeText(context, "Playing: ${item.chapter.title}", Toast.LENGTH_SHORT).show()
                                    item.parentManga?.let { parent ->
                                        onPlaySong?.invoke(parent, item.allChaptersInPlaylist, item.trackIndex)
                                    }
                                },
                                onClick = {
                                    item.parentManga?.let { parent ->
                                        onPlaySong?.invoke(parent, item.allChaptersInPlaylist, item.trackIndex)
                                        onNavigateToDescription(parent.id)
                                    } ?: run {
                                        Toast.makeText(context, "Playing: ${item.chapter.title}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        is SearchResultItem.Media -> {
                            SearchMediaItemCard(
                                manga = item.manga,
                                trackCount = item.trackCount,
                                context = context,
                                glowColor = glowColor,
                                onClick = { onNavigateToDescription(item.manga.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSongItemCard(
    song: SearchResultItem.Song,
    glowColor: Color,
    context: android.content.Context,
    onPlay: () -> Unit,
    onClick: () -> Unit
) {
    val chapter = song.chapter
    val parent = song.parentManga
    val coverModel = remember(chapter.thumbnailUri, parent?.coverPath) {
        chapter.thumbnailUri?.takeIf { it.isNotBlank() }
            ?: parent?.coverPath?.takeIf { it.isNotBlank() && it != "android.resource://android/drawable/ic_menu_gallery" }
    }

    val durationStr = remember(chapter.duration) { formatDuration(chapter.duration) }
    val artistName = chapter.artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
    val albumName = parent?.title?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        // Cover Art Box with Format Badge & Play Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF14131C),
            border = BorderStroke(1.dp, Color(0xFFEC407A).copy(alpha = 0.35f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (coverModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(coverModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = chapter.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF331327), Color(0xFF191122))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFFEC407A).copy(alpha = 0.7f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Dark gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f)
                                )
                            )
                        )
                )

                // Top-Left Format Pill Badge: "Song"
                Surface(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFEC407A).copy(alpha = 0.95f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "Song",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Top-Right Favorite Bookmark Icon
                if (parent?.isFavorite == true || chapter.genre?.contains("favorite", ignoreCase = true) == true) {
                    Surface(
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.TopEnd)
                            .size(20.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Bookmark,
                                contentDescription = "Favorite",
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                // Center Play Button Overlay
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(38.dp)
                        .clickable(onClick = onPlay),
                    shape = CircleShape,
                    color = Color(0xFFEC407A).copy(alpha = 0.88f),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.9f)),
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play ${chapter.title}",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Bottom-Right Duration Badge
                if (durationStr.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.BottomEnd),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = durationStr,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Song Title
        Text(
            text = chapter.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Subtitle: Artist • Album
        val subtitle = if (albumName != null) "$artistName • $albumName" else artistName
        Text(
            text = subtitle,
            color = Color(0xFFF48FB1),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchMediaItemCard(
    manga: MangaEntity,
    trackCount: Int = 0,
    context: android.content.Context,
    glowColor: Color,
    onClick: () -> Unit
) {
    val coverModel = remember(manga.coverPath, manga.parentUri, manga.contentType) {
        if (manga.contentType == 3) {
            if (manga.coverPath.isNotBlank() && manga.coverPath != "android.resource://android/drawable/ic_menu_gallery") {
                manga.coverPath
            } else {
                null
            }
        } else {
            CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, null, context)
        }
    }

    val mediaType = remember(manga) {
        when {
            manga.contentType == 3 || manga.boxPurpose == "music" -> "Album"
            manga.contentType == 1 || manga.boxPurpose == "book" -> "Book"
            (manga.contentType == 2 && manga.boxPurpose == "channel") -> "Channel"
            manga.contentType == 2 -> "Series"
            manga.contentType == 0 && (
                manga.boxPurpose == "manhua" ||
                manga.genre?.contains("manhua", ignoreCase = true) == true ||
                manga.genre?.contains("manhwa", ignoreCase = true) == true ||
                manga.genre?.contains("webtoon", ignoreCase = true) == true
            ) -> "Manhua"
            manga.contentType == 0 -> "Manga"
            else -> "Media"
        }
    }

    val badgeColor = when (mediaType) {
        "Book" -> Color(0xFF42A5F5)
        "Manhua" -> Color(0xFFFF9800)
        "Manga" -> Color(0xFFAB47BC)
        "Series" -> Color(0xFFEF5350)
        "Channel" -> Color(0xFF26A69A)
        "Album" -> Color(0xFFEC407A)
        else -> glowColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        // Cover Art with Format Badge
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF14131C),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (coverModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(coverModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = manga.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (mediaType == "Album") {
                                    Brush.linearGradient(
                                        listOf(Color(0xFF2E1C2B), Color(0xFF1B1425))
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.02f))
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (mediaType == "Album") Icons.Rounded.Album else Icons.Rounded.Image,
                            contentDescription = null,
                            tint = if (mediaType == "Album") Color(0xFFEC407A).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Top-Left Format Pill Badge
                Surface(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart),
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.90f)
                ) {
                    Text(
                        text = mediaType,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Favorite Bookmark Icon (if favorite)
                if (manga.isFavorite) {
                    Surface(
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.TopEnd)
                            .size(20.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Bookmark,
                                contentDescription = "Favorite",
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = manga.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val tagsPreview = remember(manga.genre) {
            manga.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && !it.equals("favorite", ignoreCase = true) }?.take(2)?.joinToString(" • ")
        }

        val subtitleText = when {
            mediaType == "Album" -> {
                if (trackCount > 0) "$trackCount tracks"
                else if (!tagsPreview.isNullOrBlank()) tagsPreview
                else manga.workspace ?: "Album"
            }
            !tagsPreview.isNullOrBlank() -> tagsPreview
            else -> manga.workspace ?: mediaType
        }

        Text(
            text = subtitleText,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%d:%02d", min, sec)
}

private fun matchesMediaType(manga: MangaEntity, selectedMedia: String): Boolean {
    return when (selectedMedia) {
        "All" -> true
        "Favorite" -> manga.isFavorite || manga.genre?.contains("favorite", ignoreCase = true) == true
        "Music" -> manga.contentType == 3 || manga.boxPurpose == "music"
        "Book" -> manga.contentType != 3 && (manga.contentType == 1 || manga.boxPurpose == "book")
        "Manhua" -> manga.contentType != 3 && (manga.contentType == 0 || manga.boxPurpose == "manhua") && (
            manga.boxPurpose == "manhua" ||
            manga.genre?.contains("manhua", ignoreCase = true) == true ||
            manga.genre?.contains("manhwa", ignoreCase = true) == true ||
            manga.genre?.contains("webtoon", ignoreCase = true) == true ||
            manga.title.contains("manhua", ignoreCase = true) ||
            manga.title.contains("manhwa", ignoreCase = true)
        )
        "Manga" -> manga.contentType == 0 && manga.boxPurpose != "manhua" && manga.boxPurpose != "book" && manga.boxPurpose != "music" && (
            manga.genre == null || (
                !manga.genre.contains("manhua", ignoreCase = true) &&
                !manga.genre.contains("manhwa", ignoreCase = true) &&
                !manga.genre.contains("webtoon", ignoreCase = true)
            )
        )
        "Series" -> manga.contentType != 3 && (manga.contentType == 2 || manga.boxPurpose == "series") && manga.boxPurpose != "channel"
        "Channel" -> manga.contentType != 3 && (manga.contentType == 2 || manga.boxPurpose == "channel") && manga.boxPurpose == "channel"
        else -> true
    }
}
