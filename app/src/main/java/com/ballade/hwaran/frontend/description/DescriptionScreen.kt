package com.ballade.hwaran.frontend.description

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ballade.hwaran.frontend.description.book.BookDescriptionView
import com.ballade.hwaran.frontend.description.toon.ToonChaptersView
import com.ballade.hwaran.frontend.description.toon.ToonDescriptionView
import com.ballade.hwaran.frontend.description.video.ChannelDescriptionView
import com.ballade.hwaran.frontend.description.video.ChannelVideosView
import com.ballade.hwaran.frontend.description.video.SeriesDescriptionView
import com.ballade.hwaran.frontend.description.video.SeriesRelatedView
import com.ballade.hwaran.ui.viewmodels.DescriptionViewModel
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescriptionScreen(
    mangaId: Long,
    descriptionViewModel: DescriptionViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToMedia: (Long, Int) -> Unit,
    onNavigateToDescription: (Long) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(mangaId) {
        descriptionViewModel.loadManga(mangaId)
    }

    val CardBg = MaterialTheme.colorScheme.surface
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val PrimaryPurple = MaterialTheme.colorScheme.primary

    val manga by descriptionViewModel.manga.collectAsState()
    val chapters by descriptionViewModel.chapters.collectAsState()
    val childBoxes by descriptionViewModel.childBoxes.collectAsState()
    val availableMediaForLinking by descriptionViewModel.availableMediaForLinking.collectAsState()
    val isEditMode by descriptionViewModel.isEditMode.collectAsState()

    val draftDesc by descriptionViewModel.draftDescription.collectAsState()
    val draftCover by descriptionViewModel.draftCoverPath.collectAsState()
    val draftTitle by descriptionViewModel.draftTitle.collectAsState()

    val entryMetadata by descriptionViewModel.entryMetadata.collectAsState()
    val assignedTags by descriptionViewModel.assignedTags.collectAsState()
    val tagQuery by descriptionViewModel.tagQuery.collectAsState()
    val tagSuggestions by descriptionViewModel.tagSuggestions.collectAsState()
    val isTagSearchVisible by descriptionViewModel.isTagSearchVisible.collectAsState()
    val draftAltTitle by descriptionViewModel.draftAltTitle.collectAsState()
    val draftAuthor by descriptionViewModel.draftAuthor.collectAsState()
    val draftArtist by descriptionViewModel.draftArtist.collectAsState()
    val draftPublisher by descriptionViewModel.draftPublisher.collectAsState()
    val draftSerialization by descriptionViewModel.draftSerialization.collectAsState()
    val draftYear by descriptionViewModel.draftYear.collectAsState()
    val draftStatus by descriptionViewModel.draftStatus.collectAsState()
    val draftRating by descriptionViewModel.draftRating.collectAsState()
    val draftLanguage by descriptionViewModel.draftLanguage.collectAsState()
    val draftPages by descriptionViewModel.draftPages.collectAsState()
    val draftMaterialTag by descriptionViewModel.draftMaterialTag.collectAsState()
    val draftIsFavorite by descriptionViewModel.draftIsFavorite.collectAsState()

    val storageMode by settingsViewModel.storageMode.collectAsState()

    var showChaptersWindow by remember { mutableStateOf(false) }
    var showSeriesRelatedWindow by remember { mutableStateOf(false) }
    var seriesInitialTab by remember { mutableStateOf("Videos") }
    var showChannelVideosWindow by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Intercept Back Press gracefully
    BackHandler(enabled = showChaptersWindow) {
        showChaptersWindow = false
    }
    BackHandler(enabled = showSeriesRelatedWindow) {
        showSeriesRelatedWindow = false
    }
    BackHandler(enabled = showChannelVideosWindow) {
        showChannelVideosWindow = false
    }
    BackHandler(enabled = isEditMode && !showChaptersWindow && !showSeriesRelatedWindow && !showChannelVideosWindow) {
        descriptionViewModel.toggleEditMode()
    }

    // Launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            descriptionViewModel.draftCoverPath.value = it.toString()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { descriptionViewModel.importChapters(it, storageMode) }
    }

    val multipleVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            descriptionViewModel.importMultipleVideos(uris, storageMode)
        }
    }

    var chapterToUpdateThumbnail by remember { mutableStateOf<Long?>(null) }
    val chapterThumbnailLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            chapterToUpdateThumbnail?.let { chapterId ->
                descriptionViewModel.updateChapterThumbnail(chapterId, it.toString())
                chapterToUpdateThumbnail = null
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove \"${manga?.title}\"?", color = Color.White) },
            text = { Text("Are you sure you want to remove this entry and all associated data?", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    descriptionViewModel.deleteManga { parentId ->
                        if (parentId != null) {
                            onNavigateToDescription(parentId)
                        } else {
                            onNavigateBack()
                        }
                    }
                }) {
                    Text("Delete", color = Color(0xFFE57373))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CardBg,
            titleContentColor = Color.White,
            textContentColor = TextMuted
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (manga == null && !isEditMode) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryPurple)
                }
                return@Box
            }

            val scrollState = rememberLazyListState()

            when {
                // ── 1. Book / Novel (contentType == 1) ──
                manga != null && manga?.contentType == 1 -> {
                    if (showChaptersWindow) {
                        ToonChaptersView(
                            manga = manga!!,
                            chapters = chapters,
                            onNavigateBack = { showChaptersWindow = false },
                            onNavigateToChapter = { _ ->
                                onNavigateToMedia(manga!!.id, 1)
                            },
                            onPickChaptersFolder = { folderPickerLauncher.launch(null) },
                            onDeleteChapters = { chapterIds -> descriptionViewModel.deleteSelectedChapters(chapterIds) }
                        )
                    } else {
                        BookDescriptionView(
                            manga = manga!!,
                            chapters = chapters,
                            entryMetadata = entryMetadata,
                            assignedTags = assignedTags,
                            isEditMode = isEditMode,
                            tagQuery = tagQuery,
                            tagSuggestions = tagSuggestions,
                            isTagSearchVisible = isTagSearchVisible,
                            draftTitle = draftTitle,
                            draftAuthor = draftAuthor,
                            draftPublisher = draftPublisher,
                            draftYear = draftYear,
                            draftStatus = draftStatus,
                            draftLanguage = draftLanguage,
                            draftPages = draftPages,
                            draftMaterialTag = draftMaterialTag,
                            draftIsFavorite = draftIsFavorite,
                            draftDesc = draftDesc,
                            draftCover = draftCover,
                            scrollState = scrollState,
                            onToggleEditMode = { descriptionViewModel.toggleEditMode() },
                            onSaveManga = { descriptionViewModel.saveManga() },
                            onNavigateBack = onNavigateBack,
                            onNavigateToMedia = onNavigateToMedia,
                            onOpenChapters = { showChaptersWindow = true },
                            onToggleFavorite = { descriptionViewModel.toggleFavorite() },
                            onAddTag = { descriptionViewModel.addTag(it) },
                            onRemoveTag = { descriptionViewModel.removeTag(it) },
                            onSetTagQuery = { descriptionViewModel.setTagQuery(it) },
                            onToggleTagSearchVisible = { descriptionViewModel.toggleTagSearchVisible() },
                            onPickCover = { imagePickerLauncher.launch(arrayOf("image/*")) },
                            onSetMaterialTag = { descriptionViewModel.setMaterialTag(it) },
                            onUpdateDraftTitle = { descriptionViewModel.draftTitle.value = it },
                            onUpdateDraftAuthor = { descriptionViewModel.draftAuthor.value = it },
                            onUpdateDraftPublisher = { descriptionViewModel.draftPublisher.value = it },
                            onUpdateDraftYear = { descriptionViewModel.draftYear.value = it },
                            onUpdateDraftStatus = { descriptionViewModel.draftStatus.value = it },
                            onUpdateDraftLanguage = { descriptionViewModel.draftLanguage.value = it },
                            onUpdateDraftPages = { descriptionViewModel.draftPages.value = it },
                            onUpdateDraftDesc = { descriptionViewModel.draftDescription.value = it },
                            onDeleteManga = { showDeleteDialog = true }
                        )
                    }
                }

                // ── 2. Manga / Manhua (contentType == 0) ──
                manga != null && manga?.contentType == 0 -> {
                    if (showChaptersWindow) {
                        ToonChaptersView(
                            manga = manga!!,
                            chapters = chapters,
                            onNavigateBack = { showChaptersWindow = false },
                            onNavigateToChapter = { chapterId ->
                                onNavigateToMedia(chapterId, 0)
                            },
                            onPickChaptersFolder = { folderPickerLauncher.launch(null) },
                            onDeleteChapters = { chapterIds -> descriptionViewModel.deleteSelectedChapters(chapterIds) }
                        )
                    } else {
                        ToonDescriptionView(
                            manga = manga!!,
                            chapters = chapters,
                            entryMetadata = entryMetadata,
                            assignedTags = assignedTags,
                            isEditMode = isEditMode,
                            tagQuery = tagQuery,
                            tagSuggestions = tagSuggestions,
                            isTagSearchVisible = isTagSearchVisible,
                            draftTitle = draftTitle,
                            draftAltTitle = draftAltTitle,
                            draftAuthor = draftAuthor,
                            draftArtist = draftArtist,
                            draftPublisher = draftPublisher,
                            draftSerialization = draftSerialization,
                            draftYear = draftYear,
                            draftStatus = draftStatus,
                            draftRating = draftRating,
                            draftLanguage = draftLanguage,
                            draftPages = draftPages,
                            draftMaterialTag = draftMaterialTag,
                            draftIsFavorite = draftIsFavorite,
                            draftDesc = draftDesc,
                            draftCover = draftCover,
                            scrollState = scrollState,
                            onToggleEditMode = { descriptionViewModel.toggleEditMode() },
                            onSaveManga = { descriptionViewModel.saveManga() },
                            onNavigateBack = onNavigateBack,
                            onNavigateToMedia = onNavigateToMedia,
                            onOpenChapters = { showChaptersWindow = true },
                            onToggleFavorite = { descriptionViewModel.toggleFavorite() },
                            onAddTag = { descriptionViewModel.addTag(it) },
                            onRemoveTag = { descriptionViewModel.removeTag(it) },
                            onSetTagQuery = { descriptionViewModel.setTagQuery(it) },
                            onToggleTagSearchVisible = { descriptionViewModel.toggleTagSearchVisible() },
                            onPickCover = { imagePickerLauncher.launch(arrayOf("image/*")) },
                            onSetMaterialTag = { descriptionViewModel.setMaterialTag(it) },
                            onUpdateDraftTitle = { descriptionViewModel.draftTitle.value = it },
                            onUpdateDraftAltTitle = { descriptionViewModel.draftAltTitle.value = it },
                            onUpdateDraftAuthor = { descriptionViewModel.draftAuthor.value = it },
                            onUpdateDraftArtist = { descriptionViewModel.draftArtist.value = it },
                            onUpdateDraftPublisher = { descriptionViewModel.draftPublisher.value = it },
                            onUpdateDraftSerialization = { descriptionViewModel.draftSerialization.value = it },
                            onUpdateDraftYear = { descriptionViewModel.draftYear.value = it },
                            onUpdateDraftStatus = { descriptionViewModel.draftStatus.value = it },
                            onUpdateDraftRating = { descriptionViewModel.draftRating.value = it },
                            onUpdateDraftLanguage = { descriptionViewModel.draftLanguage.value = it },
                            onUpdateDraftPages = { descriptionViewModel.draftPages.value = it },
                            onUpdateDraftDesc = { descriptionViewModel.draftDescription.value = it },
                            onDeleteManga = { showDeleteDialog = true }
                        )
                    }
                }

                // ── 2. Channel (contentType == 2 && boxPurpose == "channel") ──
                manga != null && manga?.contentType == 2 && manga?.boxPurpose.equals("channel", ignoreCase = true) -> {
                    if (showChannelVideosWindow) {
                        ChannelVideosView(
                            channel = manga!!,
                            videos = chapters,
                            onNavigateBack = { showChannelVideosWindow = false },
                            onNavigateToVideo = { videoId -> onNavigateToMedia(videoId, 2) },
                            onPickVideos = { multipleVideoPickerLauncher.launch(arrayOf("video/*")) },
                            onPickVideosFolder = { folderPickerLauncher.launch(null) },
                            onDeleteVideos = { videoIds -> descriptionViewModel.deleteSelectedChapters(videoIds) },
                            onChangeVideoThumbnail = { videoId ->
                                chapterToUpdateThumbnail = videoId
                                chapterThumbnailLauncher.launch(arrayOf("image/*"))
                            }
                        )
                    } else {
                        ChannelDescriptionView(
                            manga = manga!!,
                            chapters = chapters,
                            entryMetadata = entryMetadata,
                            assignedTags = assignedTags,
                            isEditMode = isEditMode,
                            tagQuery = tagQuery,
                            tagSuggestions = tagSuggestions,
                            isTagSearchVisible = isTagSearchVisible,
                            draftTitle = draftTitle,
                            draftAltTitle = draftAltTitle,
                            draftAuthor = draftAuthor,
                            draftArtist = draftArtist,
                            draftPublisher = draftPublisher,
                            draftSerialization = draftSerialization,
                            draftYear = draftYear,
                            draftStatus = draftStatus,
                            draftRating = draftRating,
                            draftLanguage = draftLanguage,
                            draftMaterialTag = draftMaterialTag,
                            draftIsFavorite = draftIsFavorite,
                            draftDesc = draftDesc,
                            draftCover = draftCover,
                            scrollState = scrollState,
                            onToggleEditMode = { descriptionViewModel.toggleEditMode() },
                            onSaveManga = { descriptionViewModel.saveManga() },
                            onNavigateBack = onNavigateBack,
                            onNavigateToMedia = onNavigateToMedia,
                            onOpenVideos = { showChannelVideosWindow = true },
                            onToggleFavorite = { descriptionViewModel.toggleFavorite() },
                            onAddTag = { descriptionViewModel.addTag(it) },
                            onRemoveTag = { descriptionViewModel.removeTag(it) },
                            onSetTagQuery = { descriptionViewModel.setTagQuery(it) },
                            onToggleTagSearchVisible = { descriptionViewModel.toggleTagSearchVisible() },
                            onPickCover = { imagePickerLauncher.launch(arrayOf("image/*")) },
                            onPickVideos = { multipleVideoPickerLauncher.launch(arrayOf("video/*")) },
                            onSetMaterialTag = { descriptionViewModel.setMaterialTag(it) },
                            onUpdateDraftTitle = { descriptionViewModel.draftTitle.value = it },
                            onUpdateDraftAltTitle = { descriptionViewModel.draftAltTitle.value = it },
                            onUpdateDraftAuthor = { descriptionViewModel.draftAuthor.value = it },
                            onUpdateDraftArtist = { descriptionViewModel.draftArtist.value = it },
                            onUpdateDraftPublisher = { descriptionViewModel.draftPublisher.value = it },
                            onUpdateDraftSerialization = { descriptionViewModel.draftSerialization.value = it },
                            onUpdateDraftYear = { descriptionViewModel.draftYear.value = it },
                            onUpdateDraftStatus = { descriptionViewModel.draftStatus.value = it },
                            onUpdateDraftRating = { descriptionViewModel.draftRating.value = it },
                            onUpdateDraftLanguage = { descriptionViewModel.draftLanguage.value = it },
                            onUpdateDraftDesc = { descriptionViewModel.draftDescription.value = it },
                            onDeleteManga = { showDeleteDialog = true }
                        )
                    }
                }

                // ── 3. Series / Anime (contentType == 2 && boxPurpose != "channel") ──
                manga != null && manga?.contentType == 2 -> {
                    if (showSeriesRelatedWindow) {
                        SeriesRelatedView(
                            manga = manga!!,
                            videos = chapters,
                            childBoxes = childBoxes,
                            availableMediaForLinking = availableMediaForLinking,
                            initialTab = seriesInitialTab,
                            onNavigateBack = { showSeriesRelatedWindow = false },
                            onNavigateToVideo = { videoId -> onNavigateToMedia(videoId, 2) },
                            onPickVideos = { multipleVideoPickerLauncher.launch(arrayOf("video/*")) },
                            onPickVideosFolder = { folderPickerLauncher.launch(null) },
                            onDeleteVideos = { videoIds -> descriptionViewModel.deleteSelectedChapters(videoIds) },
                            onChangeVideoThumbnail = { videoId ->
                                chapterToUpdateThumbnail = videoId
                                chapterThumbnailLauncher.launch(arrayOf("image/*"))
                            },
                            onNavigateToRelated = { relatedId -> onNavigateToDescription(relatedId) },
                            onCreateRelatedBox = { label, purpose ->
                                descriptionViewModel.createChildBox(label, purpose) { newId ->
                                    onNavigateToDescription(newId)
                                }
                            },
                            onLinkExistingMedia = { targetId, purpose, label ->
                                descriptionViewModel.linkExistingMediaAsRelated(targetId, purpose, label)
                            },
                            onUnlinkRelated = { descriptionViewModel.unlinkRelatedMedia(it) },
                            onDeleteRelated = { descriptionViewModel.deleteRelatedMedia(it) },
                            onRefreshAvailableMedia = { descriptionViewModel.loadAvailableMediaForLinking() }
                        )
                    } else {
                        SeriesDescriptionView(
                            manga = manga!!,
                            chapters = chapters,
                            childBoxes = childBoxes,
                            entryMetadata = entryMetadata,
                            assignedTags = assignedTags,
                            isEditMode = isEditMode,
                            tagQuery = tagQuery,
                            tagSuggestions = tagSuggestions,
                            isTagSearchVisible = isTagSearchVisible,
                            draftTitle = draftTitle,
                            draftAltTitle = draftAltTitle,
                            draftAuthor = draftAuthor,
                            draftArtist = draftArtist,
                            draftPublisher = draftPublisher,
                            draftSerialization = draftSerialization,
                            draftYear = draftYear,
                            draftStatus = draftStatus,
                            draftRating = draftRating,
                            draftLanguage = draftLanguage,
                            draftMaterialTag = draftMaterialTag,
                            draftIsFavorite = draftIsFavorite,
                            draftDesc = draftDesc,
                            draftCover = draftCover,
                            scrollState = scrollState,
                            onToggleEditMode = { descriptionViewModel.toggleEditMode() },
                            onSaveManga = { descriptionViewModel.saveManga() },
                            onNavigateBack = onNavigateBack,
                            onNavigateToMedia = onNavigateToMedia,
                            onOpenRelated = { tab ->
                                seriesInitialTab = tab
                                descriptionViewModel.loadAvailableMediaForLinking()
                                showSeriesRelatedWindow = true
                            },
                            onToggleFavorite = { descriptionViewModel.toggleFavorite() },
                            onAddTag = { descriptionViewModel.addTag(it) },
                            onRemoveTag = { descriptionViewModel.removeTag(it) },
                            onSetTagQuery = { descriptionViewModel.setTagQuery(it) },
                            onToggleTagSearchVisible = { descriptionViewModel.toggleTagSearchVisible() },
                            onPickCover = { imagePickerLauncher.launch(arrayOf("image/*")) },
                            onPickEpisodes = { folderPickerLauncher.launch(null) },
                            onSetMaterialTag = { descriptionViewModel.setMaterialTag(it) },
                            onUpdateDraftTitle = { descriptionViewModel.draftTitle.value = it },
                            onUpdateDraftAltTitle = { descriptionViewModel.draftAltTitle.value = it },
                            onUpdateDraftAuthor = { descriptionViewModel.draftAuthor.value = it },
                            onUpdateDraftArtist = { descriptionViewModel.draftArtist.value = it },
                            onUpdateDraftPublisher = { descriptionViewModel.draftPublisher.value = it },
                            onUpdateDraftSerialization = { descriptionViewModel.draftSerialization.value = it },
                            onUpdateDraftYear = { descriptionViewModel.draftYear.value = it },
                            onUpdateDraftStatus = { descriptionViewModel.draftStatus.value = it },
                            onUpdateDraftRating = { descriptionViewModel.draftRating.value = it },
                            onUpdateDraftLanguage = { descriptionViewModel.draftLanguage.value = it },
                            onUpdateDraftDesc = { descriptionViewModel.draftDescription.value = it },
                            onDeleteManga = { showDeleteDialog = true }
                        )
                    }
                }
            }
        }
    }
}
