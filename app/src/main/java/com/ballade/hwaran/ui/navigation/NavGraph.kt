package com.ballade.hwaran.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ballade.hwaran.frontend.description.DescriptionScreen
import com.ballade.hwaran.frontend.home.HomeScreen
import com.ballade.hwaran.frontend.player.toon.ToonPlayerScreen as ReaderScreen
import com.ballade.hwaran.frontend.player.book.BookPlayerScreen as PdfReaderScreen
import com.ballade.hwaran.frontend.player.video.VideoPlayerScreen
import com.ballade.hwaran.frontend.player.music.MusicPlayerScreen as NowPlayingScreen
import com.ballade.hwaran.frontend.editor.music.EditPlaylistScreen
import com.ballade.hwaran.frontend.editor.music.EditSongScreen
import com.ballade.hwaran.frontend.history.HistoryScreen as FrontendHistoryScreen
import com.ballade.hwaran.ui.screens.SettingsScreen
import com.ballade.hwaran.ui.screens.LockSelectionScreen
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ballade.hwaran.ui.viewmodels.LibraryViewModel
import androidx.compose.ui.input.pointer.pointerInput
import com.ballade.hwaran.frontend.description.music.PlaylistDetailScreen
import com.ballade.hwaran.ui.viewmodels.MusicViewModel
import com.ballade.hwaran.ui.viewmodels.DescriptionViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

sealed class Screen(val route: String) {
    object Intro : Screen("intro")
    object Home : Screen("home")
    object Settings : Screen("settings")
    object History : Screen("history")
    object LockSelection : Screen("lock_selection")
    object Description : Screen("description/{mangaId}") {
        fun createRoute(mangaId: Long) = "description/$mangaId"
    }
    object Reader : Screen("reader/{chapterId}") {
        fun createRoute(chapterId: Long) = "reader/$chapterId"
    }
    object PdfReader : Screen("pdf_reader/{mangaId}") {
        fun createRoute(mangaId: Long) = "pdf_reader/$mangaId"
    }
    object VideoPlayer : Screen("video_player/{chapterId}") {
        fun createRoute(chapterId: Long) = "video_player/$chapterId"
    }
    object PlaylistDetail : Screen("playlist_detail/{mangaId}") {
        fun createRoute(mangaId: Long) = "playlist_detail/$mangaId"
    }
    object NowPlaying : Screen("now_playing")
    object EditPlaylist : Screen("edit_playlist/{mangaId}") {
        fun createRoute(mangaId: Long) = "edit_playlist/$mangaId"
    }
    object EditSong : Screen("edit_song/{chapterId}") {
        fun createRoute(chapterId: Long) = "edit_song/$chapterId"
    }
    object ExternalPdf : Screen("external_pdf/{uri}") {
        fun createRoute(uri: String) = "external_pdf/${java.net.URLEncoder.encode(uri, "UTF-8")}"
    }
    object ExternalVideo : Screen("external_video/{uri}") {
        fun createRoute(uri: String) = "external_video/${java.net.URLEncoder.encode(uri, "UTF-8")}"
    }
    object ExternalImage : Screen("external_image/{uri}") {
        fun createRoute(uri: String) = "external_image/${java.net.URLEncoder.encode(uri, "UTF-8")}"
    }
}

@Composable
fun BlockTouchesWhenExiting(content: @Composable () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (state != Lifecycle.State.RESUMED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }
    }
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route,
    settingsViewModel: SettingsViewModel,
    musicViewModel: MusicViewModel
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(600))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(600))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(600))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(600))
        }
    ) {
        composable(
            route = Screen.Intro.route,
            exitTransition = {
                fadeOut(animationSpec = tween(600))
            }
        ) {
            BlockTouchesWhenExiting {
                com.ballade.hwaran.ui.screens.IntroScreen(
                    settingsViewModel = settingsViewModel,
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Intro.route) { inclusive = true }
                        }
                    }
                )
            }
        }
        composable(
            route = Screen.Home.route,
            enterTransition = {
                if (initialState.destination.route == Screen.Intro.route) {
                    fadeIn(animationSpec = tween(600))
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(500, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(500))
                }
            }
        ) {
            BlockTouchesWhenExiting {
                HomeScreen(
                    settingsViewModel = settingsViewModel,
                    musicViewModel = musicViewModel,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToDescription = { mangaId -> navController.navigate(Screen.Description.createRoute(mangaId)) }
                )
            }
        }
        composable(Screen.Settings.route) {
            BlockTouchesWhenExiting {
                SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLockSelection = { navController.navigate(Screen.LockSelection.route) },
                    onNavigateToHistory = { navController.navigate(Screen.History.route) }
                )
            }
        }
        composable(Screen.History.route) {
            BlockTouchesWhenExiting {
                FrontendHistoryScreen(
                    settingsViewModel = settingsViewModel,
                    musicViewModel = musicViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToReader = { chapterId -> navController.navigate(Screen.Reader.createRoute(chapterId)) },
                    onNavigateToPdfReader = { mangaId -> navController.navigate(Screen.PdfReader.createRoute(mangaId)) },
                    onNavigateToVideoPlayer = { chapterId -> navController.navigate(Screen.VideoPlayer.createRoute(chapterId)) },
                    onNavigateToPlaylist = { mangaId -> navController.navigate(Screen.PlaylistDetail.createRoute(mangaId)) },
                    onNavigateToNowPlaying = { navController.navigate(Screen.NowPlaying.route) }
                )
            }
        }
        composable(Screen.LockSelection.route) {
            BlockTouchesWhenExiting {
                val libraryViewModel: LibraryViewModel = viewModel()
                LockSelectionScreen(
                    libraryViewModel = libraryViewModel,
                    settingsViewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.Description.route) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getString("mangaId")?.toLongOrNull() ?: 0L
            BlockTouchesWhenExiting {
                val descriptionViewModel: DescriptionViewModel = viewModel()
                val manga by descriptionViewModel.manga.collectAsState()
                
                LaunchedEffect(mangaId) {
                    descriptionViewModel.loadManga(mangaId)
                }

                if (manga?.contentType == 3) {
                    val libraryViewModel: LibraryViewModel = viewModel()
                    PlaylistDetailScreen(
                        mangaId = mangaId,
                        descriptionViewModel = descriptionViewModel,
                        musicViewModel = musicViewModel,
                        settingsViewModel = settingsViewModel,
                        libraryViewModel = libraryViewModel,
                        onNavigateBack = { navController.popBackStack(Screen.Home.route, inclusive = false) },
                        onNavigateToNowPlaying = { navController.navigate(Screen.NowPlaying.route) },
                        onNavigateToEditPlaylist = { id -> navController.navigate(Screen.EditPlaylist.createRoute(id)) },
                        onNavigateToEditSong = { id -> navController.navigate(Screen.EditSong.createRoute(id)) }
                    )
                } else {
                    val mediaMode by settingsViewModel.mediaMode.collectAsState()
                    DescriptionScreen(
                        mangaId = mangaId,
                        descriptionViewModel = descriptionViewModel,
                        settingsViewModel = settingsViewModel,
                        onNavigateBack = { 
                            if (mediaMode == 2) {
                                navController.popBackStack(Screen.Home.route, inclusive = false)
                            } else {
                                navController.popBackStack()
                            }
                        },
                        onNavigateToMedia = { id, contentType -> 
                            when (contentType) {
                                1 -> navController.navigate(Screen.PdfReader.createRoute(id))
                                2 -> navController.navigate(Screen.VideoPlayer.createRoute(id))
                                else -> navController.navigate(Screen.Reader.createRoute(id))
                            }
                        },
                        onNavigateToDescription = { id -> 
                            if (mediaMode == 2) {
                                navController.navigate(Screen.Description.createRoute(id)) {
                                    popUpTo(Screen.Description.route) { inclusive = true }
                                }
                            } else {
                                navController.navigate(Screen.Description.createRoute(id))
                            }
                        }
                    )
                }
            }
        }
        composable(Screen.Reader.route) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId")?.toLongOrNull() ?: 0L
            BlockTouchesWhenExiting {
                ReaderScreen(
                    chapterId = chapterId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChapter = { newChapterId ->
                        navController.popBackStack()
                        navController.navigate(Screen.Reader.createRoute(newChapterId))
                    }
                )
            }
        }
        composable(Screen.PdfReader.route) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getString("mangaId")?.toLongOrNull() ?: 0L
            BlockTouchesWhenExiting {
                PdfReaderScreen(
                    mangaId = mangaId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.VideoPlayer.route) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId")?.toLongOrNull() ?: 0L
            BlockTouchesWhenExiting {
                VideoPlayerScreen(
                    chapterId = chapterId,
                    onNavigateBack = { 
                        navController.popBackStack()
                    },
                    onNavigateToChapter = { newChapterId ->
                        navController.popBackStack()
                        navController.navigate(Screen.VideoPlayer.createRoute(newChapterId))
                    }
                )
            }
        }
        composable(Screen.NowPlaying.route) {
            BlockTouchesWhenExiting {
                val libraryViewModel: LibraryViewModel = viewModel()
                NowPlayingScreen(
                    musicViewModel = musicViewModel,
                    settingsViewModel = settingsViewModel,
                    libraryViewModel = libraryViewModel,
                    onNavigateBack = { 
                        val currentChapter = musicViewModel.currentChapter.value
                        val playlistId = currentChapter?.mangaId
                        if (playlistId != null) {
                            navController.navigate(Screen.Description.createRoute(playlistId)) {
                                popUpTo(Screen.Home.route) { inclusive = false }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onNavigateToEditSong = { id -> navController.navigate(Screen.EditSong.createRoute(id)) }
                )
            }
        }
        composable(Screen.EditPlaylist.route) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getString("mangaId")?.toLongOrNull() ?: 0L
            BlockTouchesWhenExiting {
                EditPlaylistScreen(
                    mangaId = mangaId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.EditSong.route) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId")?.toLongOrNull() ?: 0L
            BlockTouchesWhenExiting {
                EditSongScreen(
                    chapterId = chapterId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.ExternalPdf.route) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            BlockTouchesWhenExiting {
                PdfReaderScreen(
                    mangaId = -1L,
                    externalUri = uri,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.ExternalVideo.route) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            BlockTouchesWhenExiting {
                VideoPlayerScreen(
                    chapterId = -1L,
                    externalUri = uri,
                    onNavigateBack = { 
                        navController.popBackStack()
                    }
                )
            }
        }
        composable(Screen.ExternalImage.route) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            BlockTouchesWhenExiting {
                ReaderScreen(
                    chapterId = -1L,
                    externalUri = uri,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChapter = {}
                )
            }
        }

    }
}

