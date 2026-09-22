package com.ballade.hwaran.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import com.ballade.hwaran.frontend.zine.ZineScraperScreen
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
    object Description : Screen("description/{mangaId}?edit={edit}") {
        fun createRoute(mangaId: Long, edit: Boolean = false) = "description/$mangaId?edit=$edit"
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
    object ZineScraper : Screen("zine_scraper")
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

/**
 * Safe backstack pop extension that prevents rapid double-back pops
 * from popping the root destination and causing a black screen / app crash.
 */
fun NavHostController.popBackStackSafely(): Boolean {
    val currentEntry = currentBackStackEntry
    if (currentEntry != null && currentEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
        if (currentEntry.destination.route != Screen.Home.route) {
            return popBackStack()
        }
    }
    return false
}

fun NavHostController.popBackStackSafely(route: String, inclusive: Boolean = false): Boolean {
    val currentEntry = currentBackStackEntry
    if (currentEntry != null && currentEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
        val popped = popBackStack(route, inclusive)
        if (!popped && currentBackStackEntry?.destination?.route == null) {
            navigate(Screen.Home.route) {
                popUpTo(0) { this.inclusive = true }
                launchSingleTop = true
            }
        }
        return popped
    }
    return false
}

@Composable
fun BlockTouchesWhenExiting(content: @Composable () -> Unit) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val isResumed = lifecycleState == androidx.lifecycle.Lifecycle.State.RESUMED

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (!isResumed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
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
            slideInHorizontally(
                initialOffsetX = { (it * 0.25f).toInt() },
                animationSpec = tween(240, easing = EmphasizedDecelerate)
            ) + fadeIn(animationSpec = tween(200, easing = LinearOutSlowInEasing))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { (-it * 0.10f).toInt() },
                animationSpec = tween(200, easing = EmphasizedAccelerate)
            ) + fadeOut(animationSpec = tween(160))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { (-it * 0.10f).toInt() },
                animationSpec = tween(240, easing = EmphasizedDecelerate)
            ) + fadeIn(animationSpec = tween(200, easing = LinearOutSlowInEasing))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(220, easing = EmphasizedAccelerate)
            ) + fadeOut(animationSpec = tween(180))
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
                    onNavigateToPlaylistDetail = { mangaId -> navController.navigateSafely(Screen.PlaylistDetail.createRoute(mangaId)) },
                    onNavigateToEditDescription = { mangaId -> navController.navigateSafely(Screen.Description.createRoute(mangaId, edit = true)) },
                    onNavigateToMedia = { id, contentType ->
                        when (contentType) {
                            1 -> navController.navigateSafely(Screen.PdfReader.createRoute(id))
                            2 -> navController.navigateSafely(Screen.VideoPlayer.createRoute(id))
                            4 -> navController.navigateSafely(Screen.NovelReader.createRoute(id))
                            else -> navController.navigateSafely(Screen.Reader.createRoute(id))
                        }
                    },
                    onNavigateToZineScraper = { navController.navigateSafely(Screen.ZineScraper.route) }
                )
            }
        }
        composable(Screen.Settings.route) {
            BlockTouchesWhenExiting {
                SettingsScreen(
                    settingsViewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStackSafely() },
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
                    onNavigateBack = { navController.popBackStackSafely() }
                )
            }
        }
        composable(Screen.LockSelection.route) {
            BlockTouchesWhenExiting {
                val libraryViewModel: LibraryViewModel = viewModel()
                LockSelectionScreen(
                    libraryViewModel = libraryViewModel,
                    settingsViewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStackSafely() }
                )
            }
        }
        composable(Screen.ZineScraper.route) {
            BlockTouchesWhenExiting {
                val libraryViewModel: LibraryViewModel = viewModel()
                val coroutineScope = rememberCoroutineScope()
                ZineScraperScreen(
                    onNavigateBack = { navController.popBackStackSafely() },
                    onImportFolder = { folderPath, openWhenDone ->
                        val file = java.io.File(folderPath)
                        if (file.exists()) {
                            val uri = android.net.Uri.fromFile(file)
                            libraryViewModel.importFolder(
                                uri = uri,
                                isFile = file.isFile,
                                mediaModeOverride = 0,
                                storageModeOverride = 0,
                                onImported = { importedId ->
                                    if (openWhenDone) {
                                        coroutineScope.launch {
                                            val firstChapterId = libraryViewModel.getFirstChapterId(importedId)
                                            if (firstChapterId != null) {
                                                navController.navigateSafely(Screen.Reader.createRoute(firstChapterId))
                                            } else {
                                                navController.navigateSafely(Screen.Description.createRoute(importedId))
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                )
            }
        }
        composable(
            route = Screen.Description.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(260, easing = EmphasizedDecelerate)
                ) + fadeIn(animationSpec = tween(180))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { (-it * 0.10f).toInt() },
                    animationSpec = tween(200, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(160))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { (-it * 0.10f).toInt() },
                    animationSpec = tween(240, easing = EmphasizedDecelerate)
                ) + fadeIn(animationSpec = tween(180))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(220, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(180))
            }
        ) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getString("mangaId")?.toLongOrNull() ?: 0L
            val shouldEdit = backStackEntry.arguments?.getString("edit")?.toBooleanStrictOrNull() ?: false
            BlockTouchesWhenExiting {
                val descriptionViewModel: DescriptionViewModel = viewModel()
                val manga by descriptionViewModel.manga.collectAsState()
                
                LaunchedEffect(mangaId, shouldEdit) {
                    descriptionViewModel.loadManga(mangaId)
                    if (shouldEdit) {
                        descriptionViewModel.enterEditMode()
                    }
                }

                LaunchedEffect(manga?.contentType) {
                    if (manga?.contentType == 3) {
                        navController.navigate(Screen.PlaylistDetail.createRoute(mangaId)) {
                            popUpTo(Screen.Description.createRoute(mangaId, shouldEdit)) { inclusive = true }
                        }
                    }
                }

                if (manga?.contentType != 3) {
                    val mediaMode by settingsViewModel.mediaMode.collectAsState()
                    DescriptionScreen(
                        mangaId = mangaId,
                        descriptionViewModel = descriptionViewModel,
                        settingsViewModel = settingsViewModel,
                        onNavigateBack = { 
                            navController.popBackStackSafely(Screen.Home.route, inclusive = false)
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
            route = Screen.PlaylistDetail.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(260, easing = EmphasizedDecelerate)
                ) + fadeIn(animationSpec = tween(180))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { (-it * 0.10f).toInt() },
                    animationSpec = tween(200, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(160))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { (-it * 0.10f).toInt() },
                    animationSpec = tween(240, easing = EmphasizedDecelerate)
                ) + fadeIn(animationSpec = tween(180))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(220, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(180))
            }
        ) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getString("mangaId")?.toLongOrNull() ?: 0L
            BlockTouchesWhenExiting {
                val descriptionViewModel: DescriptionViewModel = viewModel()
                val libraryViewModel: LibraryViewModel = viewModel()
                PlaylistDetailScreen(
                    mangaId = mangaId,
                    descriptionViewModel = descriptionViewModel,
                    musicViewModel = musicViewModel,
                    settingsViewModel = settingsViewModel,
                    libraryViewModel = libraryViewModel,
                    onNavigateBack = { 
                        val popped = navController.popBackStack(Screen.Home.route, inclusive = false)
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
                    musicViewModel = musicViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChapter = { newChapterId ->
                        navController.navigateSafely(Screen.Reader.createRoute(newChapterId)) {
                            popUpTo(Screen.Reader.route) { inclusive = true }
                        }
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
                    musicViewModel = musicViewModel,
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
                    }
                )
            }
        }
        composable(
            route = Screen.NowPlaying.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(260, easing = EmphasizedDecelerate)
                ) + fadeIn(animationSpec = tween(180))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(220, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(180))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(180))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(220, easing = EmphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(180))
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
                    musicViewModel = musicViewModel,
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
                    musicViewModel = musicViewModel,
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
                    musicViewModel = musicViewModel,
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

