package com.ballade.hwaran.frontend.home

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
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.util.CoverArtResolver

enum class SearchScope(val label: String) {
    ALL("All Fields"),
    NAME("Name / Title"),
    TAGS("Tags / Genre")
}

enum class SearchSort(val label: String) {
    NEWEST("Newest"),
    TITLE_AZ("Title (A-Z)"),
    TITLE_ZA("Title (Z-A)"),
    RECENTLY_READ("Recently Read")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeSearchView(
    allManga: List<MangaEntity>,
    onNavigateToDescription: (Long) -> Unit,
    onBack: () -> Unit,
    glowColor: Color = Color(0xFF9C27B0),
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMedia by remember { mutableStateOf("All") } // "All", "Book", "Manhua", "Manga", "Series", "Channel", "Favorite"
    var selectedScope by remember { mutableStateOf(SearchScope.ALL) }
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

    // Extract all unique tags present in the user's library
    val libraryTags = remember(allManga) {
        val extracted = allManga.flatMap { manga ->
            manga.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        }.distinct().sorted()

        if (extracted.isNotEmpty()) extracted
        else listOf("Action", "Romance", "Comedy", "Fantasy", "Sci-Fi", "Mystery", "Horror", "Drama", "Isekai", "Thriller", "Supernatural", "Slice of Life")
    }

    val mediaTypes = remember {
        listOf("All", "Book", "Manhua", "Manga", "Series", "Channel", "Favorite")
    }

    val activeFilterCount = remember(selectedMedia, selectedScope, selectedSort, selectedTags) {
        var count = 0
        if (selectedMedia != "All") count++
        if (selectedScope != SearchScope.ALL) count++
        if (selectedSort != SearchSort.NEWEST) count++
        if (selectedTags.isNotEmpty()) count += selectedTags.size
        count
    }

    // Filter and sort the collection
    val filteredResults = remember(
        allManga,
        searchQuery,
        selectedMedia,
        selectedScope,
        selectedSort,
        selectedTags,
        matchAllTags
    ) {
        val nonNsfw = allManga.filter { !it.isNsfw }

        // 1. Media Type Filter
        val mediaFiltered = nonNsfw.filter { manga ->
            matchesMediaType(manga, selectedMedia)
        }

        // 2. Query Search (with Scope)
        val queryFiltered = if (searchQuery.isBlank()) {
            mediaFiltered
        } else {
            val q = searchQuery.trim().lowercase()
            mediaFiltered.filter { manga ->
                when (selectedScope) {
                    SearchScope.NAME -> {
                        manga.title.lowercase().contains(q)
                    }
                    SearchScope.TAGS -> {
                        manga.genre?.lowercase()?.contains(q) == true
                    }
                    SearchScope.ALL -> {
                        manga.title.lowercase().contains(q) ||
                        (manga.genre?.lowercase()?.contains(q) == true) ||
                        (manga.workspace?.lowercase()?.contains(q) == true) ||
                        manga.description.lowercase().contains(q)
                    }
                }
            }
        }

        // 3. Multi-Tag Filter
        val tagFiltered = if (selectedTags.isEmpty()) {
            queryFiltered
        } else {
            queryFiltered.filter { manga ->
                val itemTags = manga.genre?.split(",")?.map { it.trim().lowercase() } ?: emptyList()
                if (matchAllTags) {
                    selectedTags.all { tag -> itemTags.any { it.contains(tag.lowercase()) } }
                } else {
                    selectedTags.any { tag -> itemTags.any { it.contains(tag.lowercase()) } }
                }
            }
        }

        // 4. Sorting
        when (selectedSort) {
            SearchSort.NEWEST -> tagFiltered.sortedByDescending { it.id }
            SearchSort.TITLE_AZ -> tagFiltered.sortedBy { it.title.lowercase() }
            SearchSort.TITLE_ZA -> tagFiltered.sortedByDescending { it.title.lowercase() }
            SearchSort.RECENTLY_READ -> tagFiltered.sortedWith(
                compareByDescending<MangaEntity> { it.lastReadPage != null || it.lastReadTitle != null }
                    .thenByDescending { it.openCount }
            )
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
                            SearchScope.ALL -> "Search vault (name, tags, description)..."
                            SearchScope.NAME -> "Search by title / name..."
                            SearchScope.TAGS -> "Search by tags / genres..."
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
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header: Advanced Filters Title + Reset Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Advanced Search & Filters",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchScope.values().forEach { scope ->
                            val isSelected = selectedScope == scope
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) glowColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, if (isSelected) glowColor else Color.White.copy(alpha = 0.1f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedScope = scope }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = scope.label,
                                        color = if (isSelected) Color.White else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── Tags Filter Section ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filter by Tags (${selectedTags.size})",
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

                    // Quick Tag Picker (Horizontally scrollable list of available library tags)
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
                        items(SearchSort.values()) { sort ->
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
                text = buildString {
                    append("${filteredResults.size} result")
                    if (filteredResults.size != 1) append("s")
                    if (selectedMedia != "All") append(" in $selectedMedia")
                    if (selectedTags.isNotEmpty()) append(" • ${selectedTags.size} tags")
                },
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
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
                        TextButton(
                            onClick = {
                                searchQuery = ""
                                selectedMedia = "All"
                                selectedScope = SearchScope.ALL
                                selectedSort = SearchSort.NEWEST
                                selectedTags = emptySet()
                            }
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
                items(filteredResults, key = { it.id }) { manga ->
                    SearchMediaItemCard(
                        manga = manga,
                        context = context,
                        glowColor = glowColor,
                        onClick = { onNavigateToDescription(manga.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchMediaItemCard(
    manga: MangaEntity,
    context: android.content.Context,
    glowColor: Color,
    onClick: () -> Unit
) {
    val coverModel = remember(manga.coverPath, manga.parentUri) {
        CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, null, context)
    }

    val mediaType = remember(manga) {
        when {
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
            manga.contentType == 3 -> "Music"
            else -> "Media"
        }
    }

    val badgeColor = when (mediaType) {
        "Book" -> Color(0xFF42A5F5)
        "Manhua" -> Color(0xFFFF9800)
        "Manga" -> Color(0xFFAB47BC)
        "Series" -> Color(0xFFEF5350)
        "Channel" -> Color(0xFF26A69A)
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
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(32.dp)
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

        // Subtitle / Tags preview
        val tagsPreview = remember(manga.genre) {
            manga.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && !it.equals("favorite", ignoreCase = true) }?.take(2)?.joinToString(" • ")
        }

        Text(
            text = if (!tagsPreview.isNullOrBlank()) tagsPreview else manga.workspace ?: mediaType,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun matchesMediaType(manga: MangaEntity, selectedMedia: String): Boolean {
    return when (selectedMedia) {
        "All" -> true
        "Favorite" -> manga.isFavorite || manga.genre?.contains("favorite", ignoreCase = true) == true
        "Book" -> manga.contentType == 1 || manga.boxPurpose == "book"
        "Manhua" -> (manga.contentType == 0 || manga.boxPurpose == "manhua") && (
            manga.boxPurpose == "manhua" ||
            manga.genre?.contains("manhua", ignoreCase = true) == true ||
            manga.genre?.contains("manhwa", ignoreCase = true) == true ||
            manga.genre?.contains("webtoon", ignoreCase = true) == true ||
            manga.title.contains("manhua", ignoreCase = true) ||
            manga.title.contains("manhwa", ignoreCase = true)
        )
        "Manga" -> manga.contentType == 0 && manga.boxPurpose != "manhua" && manga.boxPurpose != "book" && (
            manga.genre == null || (
                !manga.genre.contains("manhua", ignoreCase = true) &&
                !manga.genre.contains("manhwa", ignoreCase = true) &&
                !manga.genre.contains("webtoon", ignoreCase = true)
            )
        )
        "Series" -> (manga.contentType == 2 || manga.boxPurpose == "series") && manga.boxPurpose != "channel"
        "Channel" -> (manga.contentType == 2 || manga.boxPurpose == "channel") && manga.boxPurpose == "channel"
        else -> true
    }
}
