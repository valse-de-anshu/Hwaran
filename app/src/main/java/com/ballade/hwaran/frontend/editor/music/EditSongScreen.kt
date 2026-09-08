package com.ballade.hwaran.frontend.editor.music

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ballade.hwaran.core.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSongScreen(
    chapterId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var thumbnailUri by remember { mutableStateOf("") }
    var folderUri by remember { mutableStateOf("") }
    var mangaId by remember { mutableStateOf(0L) }
    var position by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0L) }
    var lyrics by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }

    LaunchedEffect(chapterId) {
        withContext(Dispatchers.IO) {
            val chapter = database.trackDao().getChapterById(chapterId)
            chapter?.let {
                title = it.title
                artist = it.artist ?: ""
                thumbnailUri = it.thumbnailUri ?: ""
                folderUri = it.folderUri
                mangaId = it.mangaId
                position = it.position
                duration = it.duration
                lyrics = it.lyrics ?: ""
                val parentManga = database.libraryDao().getMangaById(it.mangaId)
                genre = it.genre ?: parentManga?.genre ?: ""
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            thumbnailUri = it.toString()
        }
    }

    Scaffold(
        containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Repositioned Top Bar for Camera Safety
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 42.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = "Refine Piece",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                )
                IconButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val updatedChapter = com.ballade.hwaran.core.database.entity.ChapterEntity(
                            id = chapterId,
                            mangaId = mangaId,
                            title = title,
                            folderUri = folderUri,
                            thumbnailUri = thumbnailUri.ifBlank { null },
                            position = position,
                            artist = artist.ifBlank { null },
                            duration = duration,
                            lyrics = lyrics.ifBlank { null },
                            genre = genre.ifBlank { null }
                        )
                        database.trackDao().insertChapter(updatedChapter)
                        withContext(Dispatchers.Main) {
                            onNavigateBack()
                        }
                    }
                }) {
                    Icon(Icons.Rounded.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Premium Cover Picker
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val displayUri = if (thumbnailUri.isNotEmpty()) thumbnailUri else folderUri
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(displayUri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                                )
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            modifier = Modifier.padding(bottom = 16.dp).size(48.dp),
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Redesigned Input Fields
                RefineInputField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Title",
                    placeholder = "Enter piece title..."
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                RefineInputField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = "Artist / Collection",
                    placeholder = "Enter artist name..."
                )

                Spacer(modifier = Modifier.height(24.dp))

                GenreDropdown(
                    selectedGenre = genre,
                    onGenreSelected = { genre = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                RefineInputField(
                    value = lyrics,
                    onValueChange = { lyrics = it },
                    label = "Lyrics",
                    placeholder = "Enter lyrics...",
                    isLarge = true
                )
                
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun GenreDropdown(
    selectedGenre: String,
    onGenreSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val genres = com.ballade.hwaran.ui.dialogs.musicGenres
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Genre / Animation Style",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .clickable { expanded = true }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedGenre.ifEmpty { "Select Genre..." },
                    color = if (selectedGenre.isEmpty()) Color.White.copy(alpha = 0.2f) else Color.White
                )
                Icon(
                    if (expanded) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.4f else 0.85f)
                    .heightIn(max = if (isLandscape) 200.dp else 400.dp)
                    .background(Color(0xFF1A1A1A))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            ) {
                genres.forEach { genre ->
                    DropdownMenuItem(
                        text = { Text(genre, color = Color.White) },
                        onClick = {
                            onGenreSelected(genre)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RefineInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isLarge: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp)
        )
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.2f)) },
            singleLine = !isLarge,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = Color.White.copy(alpha = 0.02f),
                unfocusedContainerColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().then(if (isLarge) Modifier.heightIn(min = 160.dp) else Modifier)
        )
    }
}
