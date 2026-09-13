package com.ballade.hwaran.frontend.history.neverwatched

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.ChapterEntity
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.frontend.history.models.SeriesNode
import com.ballade.hwaran.frontend.history.models.UnplayedPlaylist
import com.ballade.hwaran.frontend.history.models.UnwatchedItem
import com.ballade.hwaran.frontend.history.models.truncateMiddle
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToonNeverWatched(
    toonsByManga: List<Pair<MangaEntity, List<UnwatchedItem>>>,
    onNavigateToReader: (Long) -> Unit,
    onClose: () -> Unit
) {
    if (toonsByManga.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("All toons have been read!", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(toonsByManga, key = { it.first.id }) { (manga, items) ->
                var isExpanded by remember(manga.id) { mutableStateOf(false) }
                var visibleCount by remember(manga.id) { mutableIntStateOf(10) }
                val workspaceLabel = manga.workspace?.takeIf { it.isNotBlank() } ?: "I Love It"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isExpanded = !isExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(truncateMiddle(manga.title), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("workspace: $workspaceLabel", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                }
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val displayItems = items.take(visibleCount)
                            displayItems.forEachIndexed { idx, item ->
                                val isLast = idx == displayItems.size - 1 && visibleCount >= items.size
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onClose()
                                            item.chapterId?.let { onNavigateToReader(it) }
                                        }
                                        .padding(vertical = 6.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isLast) "└── " else "├── ",
                                        color = Color.White.copy(alpha = 0.15f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(truncateMiddle(item.title), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                                }
                            }
                            if (visibleCount < items.size) {
                                val remaining = items.size - visibleCount
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { visibleCount += 10 }.padding(vertical = 6.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "└── ",
                                        color = Color.White.copy(alpha = 0.15f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("⌄ $remaining", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelNeverWatched(
    unwatchedChannels: List<Pair<MangaEntity, List<UnwatchedItem>>>,
    onNavigateToVideoPlayer: (Long) -> Unit,
    onClose: () -> Unit
) {
    if (unwatchedChannels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("All channel videos have been watched!", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(unwatchedChannels, key = { it.first.id }) { (channel, items) ->
                var isExpanded by remember(channel.id) { mutableStateOf(false) }
                var visibleCount by remember(channel.id) { mutableIntStateOf(10) }
                val workspaceLabel = channel.workspace?.takeIf { it.isNotBlank() } ?: "I Love It"

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isExpanded = !isExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(truncateMiddle(channel.title), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("workspace: $workspaceLabel", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                }
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val displayItems = items.take(visibleCount)
                            displayItems.forEachIndexed { idx, item ->
                                val isLast = idx == displayItems.size - 1 && visibleCount >= items.size
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onClose()
                                            item.chapterId?.let { onNavigateToVideoPlayer(it) }
                                        }
                                        .padding(vertical = 6.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isLast) "└── " else "├── ",
                                        color = Color.White.copy(alpha = 0.15f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(truncateMiddle(item.title), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                                }
                            }
                            if (visibleCount < items.size) {
                                val remaining = items.size - visibleCount
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { visibleCount += 10 }.padding(vertical = 6.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "└── ",
                                        color = Color.White.copy(alpha = 0.15f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("⌄ $remaining", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesNeverWatched(
    unwatchedSeriesTree: List<SeriesNode>,
    onNavigateToVideoPlayer: (Long) -> Unit,
    onClose: () -> Unit
) {
    if (unwatchedSeriesTree.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("All series videos have been watched!", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(unwatchedSeriesTree) { node ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SeriesNeverWatchedNodeView(
                            node = node,
                            indentation = 0,
                            onVideoClick = { videoId ->
                                onClose()
                                onNavigateToVideoPlayer(videoId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesNeverWatchedNodeView(
    node: SeriesNode,
    indentation: Int = 0,
    onVideoClick: (Long) -> Unit
) {
    var isExpanded by remember(node.manga.id) { mutableStateOf(false) }
    var visibleCount by remember(node.manga.id) { mutableIntStateOf(10) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 6.dp, horizontal = (indentation * 12).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = truncateMiddle(node.manga.title),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }

        if (isExpanded) {
            node.children.forEach { childNode ->
                SeriesNeverWatchedNodeView(
                    node = childNode,
                    indentation = indentation + 1,
                    onVideoClick = onVideoClick
                )
            }

            val displayItems = node.unwatchedVideos.take(visibleCount)
            displayItems.forEachIndexed { idx, video ->
                val isLast = idx == displayItems.size - 1 && visibleCount >= node.unwatchedVideos.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { video.chapterId?.let { onVideoClick(it) } }
                        .padding(vertical = 4.dp, horizontal = ((indentation + 1) * 12 + 8).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isLast) "└── " else "├── ",
                        color = Color.White.copy(alpha = 0.15f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp).padding(end = 4.dp)
                    )
                    Text(
                        text = truncateMiddle(video.title),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (visibleCount < node.unwatchedVideos.size) {
                val remaining = node.unwatchedVideos.size - visibleCount
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { visibleCount += 10 }.padding(vertical = 4.dp, horizontal = ((indentation + 1) * 12 + 8).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "└── ",
                        color = Color.White.copy(alpha = 0.15f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("⌄ $remaining", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BookNeverWatched(
    unreadBooks: List<UnwatchedItem>,
    onNavigateToPdfReader: (Long) -> Unit,
    allMangaMap: Map<Long, MangaEntity>,
    onClose: () -> Unit
) {
    if (unreadBooks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("All books have been read!", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(unreadBooks) { item ->
                val manga = allMangaMap[item.mangaId]
                val workspaceLabel = manga?.workspace?.takeIf { it.isNotBlank() } ?: "I Love It"

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onClose()
                            onNavigateToPdfReader(item.mangaId)
                        },
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = truncateMiddle(item.title),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text("workspace: $workspaceLabel", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistNeverListened(
    neverListenedPlaylists: List<UnplayedPlaylist>,
    database: AppDatabase,
    musicViewModel: MusicViewModel,
    onNavigateToNowPlaying: () -> Unit,
    onClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    if (neverListenedPlaylists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("All playlist tracks have been played!", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    } else {
        var expandedPlaylistId by remember { mutableStateOf<Long?>(null) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(neverListenedPlaylists) { playlist ->
                val isExpanded = expandedPlaylistId == playlist.mangaId
                var visibleCount by remember(isExpanded) { mutableIntStateOf(10) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedPlaylistId = if (isExpanded) null else playlist.mangaId
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(truncateMiddle(playlist.title), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("${playlist.unplayedSongs.size} unplayed tracks", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val displayItems = playlist.unplayedSongs.take(visibleCount)
                            displayItems.forEachIndexed { idx, song ->
                                val isLast = idx == displayItems.size - 1 && visibleCount >= playlist.unplayedSongs.size
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onClose()
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val chs = database.trackDao().getChaptersForMangaList(playlist.mangaId)
                                                val playlistManga = database.libraryDao().getMangaById(playlist.mangaId)
                                                if (playlistManga != null) {
                                                    val clickIndex = chs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                                    withContext(Dispatchers.Main) {
                                                        musicViewModel.playPlaylist(playlistManga, chs, clickIndex)
                                                        onNavigateToNowPlaying()
                                                    }
                                                }
                                            }
                                        }
                                        .padding(vertical = 6.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isLast) "└── " else "├── ",
                                        color = Color.White.copy(alpha = 0.15f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text(truncateMiddle(song.title), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                            }
                            if (visibleCount < playlist.unplayedSongs.size) {
                                val remaining = playlist.unplayedSongs.size - visibleCount
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { visibleCount += 10 }.padding(vertical = 6.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "└── ",
                                        color = Color.White.copy(alpha = 0.15f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("⌄ $remaining", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
