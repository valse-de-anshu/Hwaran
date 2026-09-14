package com.ballade.hwaran.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import com.ballade.hwaran.frontend.settings.SettingsScreen
import com.ballade.hwaran.frontend.lock.LockSelectionScreen
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
import com.ballade.hwaran.frontend.player.novel.NovelPlayerScreen
import com.ballade.hwaran.frontend.canvas.CanvasScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

sealed class Screen(val route: String) {
    object Intro : Screen("intro")
    object Home : Screen("home")
    object Canvas : Screen("canvas")
    object Settings : Screen("settings")
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
    object NovelReader : Screen("novel_reader/{mangaId}") {
        fun createRoute(mangaId: Long) = "novel_reader/$mangaId"
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
    object ExternalNovel : Screen("external_novel/{uri}") {
        fun createRoute(uri: String) = "external_novel/${java.net.URLEncoder.encode(uri, "UTF-8")}"
    }
    object ExternalImage : Screen("external_image/{uri}") {
        fun createRoute(uri: String) = "external_image/${java.net.URLEncoder.encode(uri, "UTF-8")}"
    }
}

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * Safe navigation extension that prevents rapid multi-tap double-navigation
 * while preserving 100% natural, responsive touch input.
 */
fun NavHostController.navigateSafely(route: String, builder: androidx.navigation.NavOptionsBuilder.() -> Unit = {}) {
    val currentEntry = currentBackStackEntry
    if (currentEntry == null || currentEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
        navigate(route) {
            launchSingleTop = true
            builder()
        }
    }
}

@Composable
fun BlockTouchesWhenExiting(content: @Composable () -> Unit) {
    content()
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
            slideInHorizontally(
                initialOffsetX = { (it * 0.30f).toInt() },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(240, easing = LinearOutSlowInEasing))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { (-it * 0.10f).toInt() },
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(200))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { (-it * 0.10f).toInt() },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(240, easing = LinearOutSlowInEasing))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(280))
        }
    ) {
        composable(
            route = Screen.Intro.route,
            exitTransition = {
                fadeOut(animationSpec = tween(600))
            }
        ) {
            BlockTouchesWhenExiting {
                com.ballade.hwaran.frontend.intro.IntroScreen(
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
                    fadeIn(animationSpec = tween(300))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { (-it * 0.08f).toInt() },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(240, easing = LinearOutSlowInEasing))
                }
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { (-it * 0.10f).toInt() },
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { (-it * 0.08f).toInt() },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(240, easing = LinearOutSlowInEasing))
            }
        ) {
            BlockTouchesWhenExiting {
                HomeScreen(
                    settingsViewModel = settingsViewModel,
                    musicViewModel = musicViewModel,
                    onNavigateToSettings = { navController.navigateSafely(Screen.Settings.route) },
                    onNavigateToDescription = { mangaId -> navController.navigateSafely(Screen.Description.createRoute(mangaId)) },
                    onNavigateToMedia = { id, contentType ->
                        when (contentType) {
                            1 -> navController.navigateSafely(Screen.PdfReader.createRoute(id))
                            2 -> navController.navigateSafely(Screen.VideoPlayer.createRoute(id))
                            4 -> navController.navigateSafely(Screen.NovelReader.createRoute(id))
                            else -> navController.navigateSafely(Screen.Reader.createRoute(id))
                        }
                    }
                )
            }
        }
        composable(Screen.Settings.route) {
            BlockTouchesWhenExiting {
                SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLockSelection = { navController.navigate(Screen.LockSelection.route) },
                    onNavigateToCanvas = { navController.navigate(Screen.Canvas.route) }
                )
            }
        }
        composable(
            route = Screen.Canvas.route,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(400)) }
        ) {
            BlockTouchesWhenExiting {
                CanvasScreen(
                    settingsViewModel = settingsViewModel,
                    musicViewModel = musicViewModel,
                    onNavigateBack = { navController.popBackStack() }
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
        composable(
            route = Screen.Description.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(200))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { (-it * 0.10f).toInt() },
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { (-it * 0.10f).toInt() },
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(200))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(280))
            }
        ) { backStackEntry ->
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
                        onNavigateBack = { 
                            val popped = navController.popBackStack()
                            if (!popped) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onNavigateToNowPlaying = { navController.navigateSafely(Screen.NowPlaying.route) },
                        onNavigateToEditPlaylist = { id -> navController.navigateSafely(Screen.EditPlaylist.createRoute(id)) },
                        onNavigateToEditSong = { id -> navController.navigateSafely(Screen.EditSong.createRoute(id)) }
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
                                1 -> navController.navigateSafely(Screen.PdfReader.createRoute(id))
                                2 -> navController.navigateSafely(Screen.VideoPlayer.createRoute(id))
                                4 -> navController.navigateSafely(Screen.NovelReader.createRoute(id))
                                else -> navController.navigateSafely(Screen.Reader.createRoute(id))
                            }
                        },
                        onNavigateToDescription = { id -> 
                            if (mediaMode == 2) {
                                navController.navigateSafely(Screen.Description.createRoute(id)) {
                                    popUpTo(Screen.Description.route) { inclusive = true }
                                }
                            } else {
                                navController.navigateSafely(Screen.Description.createRoute(id))
                            }
                        }
                    )
                }
            }
        }
        composable(
            route = Screen.Reader.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(280, easing = EmphasizedDecelerate)
                ) + fadeIn(animationSpec = tween(240))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(240, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(240))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(240, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(200))
            }
        ) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId")?.toLongOrNull() ?: 0L
            BlockTouchesWhenExiting {
                ReaderScreen(
                    chapterId = chapterId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChapter = { newChapterId ->
                        navController.popBackStack()
                        navController.navigateSafely(Screen.Reader.createRoute(newChapterId))
                    }
                )
            }
        }
        composable(
            route = Screen.PdfReader.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(280, easing = EmphasizedDecelerate)
                ) + fadeIn(animationSpec = tween(240))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(240, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(240))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(240, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(200))
            }
        ) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getString("mangaId")?.toLongOrNull() ?: 0L
            BlockTouchesWhenExiting {
                PdfReaderScreen(
                    mangaId = mangaId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable(
            route = Screen.VideoPlayer.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(280, easing = EmphasizedDecelerate)
                ) + fadeIn(animationSpec = tween(240))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(240, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(240))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(240, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(200))
            }
        ) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getString("chapterId")?.toLongOrNull() ?: 0L
            BlockTouchesWhenExiting {
                VideoPlayerScreen(
                    chapterId = chapterId,
                    onNavigateBack = { 
                        navController.popBackStack()
                    },
                    onNavigateToChapter = { newChapterId ->
                        navController.popBackStack()
                        navController.navigateSafely(Screen.VideoPlayer.createRoute(newChapterId))
                    }
                )
            }
        }
        composable(
            route = Screen.NowPlaying.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(240))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(240))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(240))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(240))
            }
        ) {
            BlockTouchesWhenExiting {
                val libraryViewModel: LibraryViewModel = viewModel()
                NowPlayingScreen(
                    musicViewModel = musicViewModel,
                    settingsViewModel = settingsViewModel,
                    libraryViewModel = libraryViewModel,
                    onNavigateBack = { 
                        val popped = navController.popBackStack()
                        if (!popped) {
                            val currentChapter = musicViewModel.currentChapter.value
                            val playlistId = currentChapter?.mangaId
                            if (playlistId != null && playlistId > 0L) {
                                navController.navigateSafely(Screen.Description.createRoute(playlistId)) {
                                    popUpTo(Screen.Home.route) { inclusive = false }
                                }
                            } else {
                                settingsViewModel.setActiveTab(1)
                                navController.navigateSafely(Screen.Home.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                    },
                    onNavigateToEditSong = { id -> navController.navigateSafely(Screen.EditSong.createRoute(id)) }
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
        composable(
            route = Screen.ExternalPdf.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(280, easing = EmphasizedDecelerate)
                ) + fadeIn(animationSpec = tween(240))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(240, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(200))
            }
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            BlockTouchesWhenExiting {
                PdfReaderScreen(
                    mangaId = -1L,
                    externalUri = uri,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable(
            route = Screen.ExternalVideo.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(280, easing = EmphasizedDecelerate)
                ) + fadeIn(animationSpec = tween(240))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(240, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(200))
            }
        ) { backStackEntry ->
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
        composable(
            route = Screen.NovelReader.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(280, easing = EmphasizedDecelerate)
                ) + fadeIn(animationSpec = tween(240))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(240, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(240))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(240, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(200))
            }
        ) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getString("mangaId")?.toLongOrNull() ?: 0L
            BlockTouchesWhenExiting {
                NovelPlayerScreen(
                    mangaId = mangaId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable(
            route = Screen.ExternalNovel.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(280, easing = EmphasizedDecelerate)
                ) + fadeIn(animationSpec = tween(240))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(240, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(200))
            }
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            BlockTouchesWhenExiting {
                NovelPlayerScreen(
                    mangaId = -1L,
                    externalUriString = uri,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable(
            route = Screen.ExternalImage.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(280, easing = EmphasizedDecelerate)
                ) + fadeIn(animationSpec = tween(240))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { (it * 0.15f).toInt() },
                    animationSpec = tween(240, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(200))
            }
        ) { backStackEntry ->
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

