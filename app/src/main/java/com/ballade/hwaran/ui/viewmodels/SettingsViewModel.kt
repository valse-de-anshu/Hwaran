package com.ballade.hwaran.ui.viewmodels

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ballade.hwaran.data.local.GlobalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val globalSettings = GlobalSettings(application)

    val thumbnails = mutableStateMapOf<Int, ImageBitmap?>()

    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab

    // Hoisted Panel states for coordinated onboarding tours
    private val _isWrapperExpanded = MutableStateFlow(false)
    val isWrapperExpanded: StateFlow<Boolean> = _isWrapperExpanded

    fun setWrapperExpanded(expanded: Boolean) {
        _isWrapperExpanded.value = expanded
    }

    private val _showGalleryNavigator = MutableStateFlow(false)
    val showGalleryNavigator: StateFlow<Boolean> = _showGalleryNavigator

    fun setShowGalleryNavigator(show: Boolean) {
        _showGalleryNavigator.value = show
    }

    private val _isMediaMenuExpanded = MutableStateFlow(false)
    val isMediaMenuExpanded: StateFlow<Boolean> = _isMediaMenuExpanded

    fun setMediaMenuExpanded(expanded: Boolean) {
        _isMediaMenuExpanded.value = expanded
    }

    private val _isCloverExpanded = MutableStateFlow(false)
    val isCloverExpanded: StateFlow<Boolean> = _isCloverExpanded

    fun setCloverExpanded(expanded: Boolean) {
        _isCloverExpanded.value = expanded
    }

    private val _showNowPlayingMenu = MutableStateFlow(false)
    val showNowPlayingMenu: StateFlow<Boolean> = _showNowPlayingMenu

    fun setShowNowPlayingMenu(show: Boolean) {
        _showNowPlayingMenu.value = show
    }

    // Onboarding Spotlight Targets (stored as Rect to be memory-safe and lightweight)
    val spotlightTargets = mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>()

    fun updateSpotlightTarget(key: String, rect: androidx.compose.ui.geometry.Rect?) {
        if (rect == null) {
            spotlightTargets.remove(key)
        } else {
            spotlightTargets[key] = rect
        }
    }

    private val _onboardingStep = MutableStateFlow<String?>(null)
    val onboardingStep: StateFlow<String?> = _onboardingStep

    val introSeen: StateFlow<Boolean> = globalSettings.introSeenTourFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val introAccepted: StateFlow<Boolean> = globalSettings.introAcceptedTourFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setIntroSeen(value: Boolean) {
        viewModelScope.launch { globalSettings.setIntroSeenTour(value) }
    }

    fun setIntroAccepted(value: Boolean) {
        viewModelScope.launch { globalSettings.setIntroAcceptedTour(value) }
    }

    fun startTour() {
        _onboardingStep.value = "step_welcome"
    }

    fun setOnboardingStep(step: String?) {
        _onboardingStep.value = step
    }

    fun endTour() {
        _onboardingStep.value = null
        spotlightTargets.clear()
    }

    fun loadSplashThumbnails(context: Context, rawVideos: List<Pair<String, Int>>, customVideos: Set<String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Load raw video thumbnails
                rawVideos.forEach { (videoName, resId) ->
                    if (!thumbnails.containsKey(resId)) {
                        generateThumbnailFromRaw(context, videoName, resId)
                    }
                }
                // Load custom video thumbnails
                customVideos.forEach { fileName ->
                    val hashCode = fileName.hashCode()
                    if (!thumbnails.containsKey(hashCode)) {
                        generateThumbnailFromPath(context, fileName, hashCode)
                    }
                }
            }
        }
    }

    private suspend fun generateThumbnailFromRaw(context: Context, videoName: String, resId: Int) {
        try {
            val retriever = MediaMetadataRetriever()
            val uri = android.net.Uri.parse("android.resource://${context.packageName}/$resId")
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            
            if (bitmap != null) {
                withContext(Dispatchers.Main) {
                    thumbnails[resId] = bitmap.asImageBitmap()
                }
            }
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Failed to generate thumbnail for $videoName", e)
        }
    }

    private suspend fun generateThumbnailFromPath(context: Context, fileName: String, hashCode: Int) {
        try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(file, Size(320, 240), null)
            } else {
                ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
            }
            
            if (bitmap != null) {
                withContext(Dispatchers.Main) {
                    thumbnails[hashCode] = bitmap.asImageBitmap()
                }
            }
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Failed to generate thumbnail for $fileName", e)
        }
    }

    val activeScreenToon: StateFlow<String?> = globalSettings.activeScreenToonFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val activeScreenBook: StateFlow<String?> = globalSettings.activeScreenBookFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val activeScreenVideo: StateFlow<String?> = globalSettings.activeScreenVideoFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val workspacesToon: StateFlow<List<String>> = globalSettings.workspacesToonFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val workspacesBook: StateFlow<List<String>> = globalSettings.workspacesBookFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val workspacesVideo: StateFlow<List<String>> = globalSettings.workspacesVideoFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setActiveScreen(mediaMode: Int, screenName: String?) {
        viewModelScope.launch { globalSettings.setActiveScreen(mediaMode, screenName) }
    }

    fun addWorkspace(mediaMode: Int, workspace: String) {
        viewModelScope.launch { globalSettings.addWorkspace(mediaMode, workspace) }
    }

    val appTheme: StateFlow<Int> = globalSettings.appThemeFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 1)

    val animationSpeed: StateFlow<Float> = globalSettings.animationSpeedFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 1.0f)

    val animationVisibility: StateFlow<Boolean> = globalSettings.animationVisibilityFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val animationType: StateFlow<Int> = globalSettings.animationTypeFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val sfwText: StateFlow<String> = globalSettings.sfwTextFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, "X")

    val nsfwText: StateFlow<String> = globalSettings.nsfwTextFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, "Y")

    val coverTransparency: StateFlow<Float> = globalSettings.coverTransparencyFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 1.0f)

    val descriptionUiTransparency: StateFlow<Float> = globalSettings.descriptionUiTransparencyFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 1.0f)

    val homeUiTransparency: StateFlow<Float> = globalSettings.homeUiTransparencyFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 1.0f)

    val storageMode: StateFlow<Int> = globalSettings.storageModeFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val mediaMode: StateFlow<Int> = globalSettings.mediaModeFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val hasAskedDefaultApp: StateFlow<Boolean> = globalSettings.hasAskedDefaultAppFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val videoLayoutMode: StateFlow<Int> = globalSettings.videoLayoutModeFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val isLibraryLocked: StateFlow<Boolean> = globalSettings.isLibraryLockedFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val libraryPassword: StateFlow<String> = globalSettings.libraryPasswordFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val splashVideoName: StateFlow<String> = globalSettings.splashVideoNameFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, "3d_intro.html")

    val splashRandomize: StateFlow<Boolean> = globalSettings.splashRandomizeFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val customSplashVideos: StateFlow<Set<String>> = globalSettings.customSplashVideosFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    val glowBrightness: StateFlow<Float> = globalSettings.glowBrightnessFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 1.0f)

    val glowRadius: StateFlow<Float> = globalSettings.glowRadiusFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 23f)

    val glowColor: StateFlow<Long> = globalSettings.glowColorFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 0xFF7A6284L)

    val fabStyle: StateFlow<Int> = globalSettings.fabStyleFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 1)

    val usePillAsHighlight: StateFlow<Boolean> = globalSettings.usePillAsHighlightFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val musicMode: StateFlow<Int> = globalSettings.musicModeFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val batterySavingMode: StateFlow<Boolean> = globalSettings.batterySavingModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val stopTracking: StateFlow<Boolean> = globalSettings.stopTrackingFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val hasSeenIntro: StateFlow<Boolean> = globalSettings.hasSeenIntroFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val descriptionLayoutMode: StateFlow<Int> = globalSettings.descriptionLayoutModeFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 1)

    fun setDescriptionLayoutMode(mode: Int) {
        viewModelScope.launch {
            globalSettings.setDescriptionLayoutMode(mode)
        }
    }

    fun seedDefaultWorkspace() {
        viewModelScope.launch { globalSettings.seedDefaultWorkspace() }
    }

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    init {
        viewModelScope.launch {
            // Seed "I Love It" as the permanent default workspace on every start
            try { globalSettings.seedDefaultWorkspace() } catch (e: Exception) { Log.e("SettingsViewModel", "Seed workspace failed", e) }

            // Read all three critical settings in one go (single DataStore read)
            try {
                val theme = globalSettings.appThemeFlow.first()
                val animType = globalSettings.animationTypeFlow.first()
                val animVis = globalSettings.animationVisibilityFlow.first()

                // Wait for the StateFlows to reflect the loaded values using combine —
                // this is a true suspension (no CPU spin) with a hard 300ms timeout.
                val settled = kotlinx.coroutines.withTimeoutOrNull(300) {
                    kotlinx.coroutines.flow.combine(
                        appTheme, animationType, animationVisibility
                    ) { t, at, av -> t == theme && at == animType && av == animVis }
                        .first { it }
                }
                // settled == null means timeout; we proceed anyway to avoid blocking forever
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Settings load failed", e)
            } finally {
                _isReady.value = true
            }
        }
    }

    private val _backgroundScrollOffset = MutableStateFlow(0f)
    val backgroundScrollOffset: StateFlow<Float> = _backgroundScrollOffset

    fun setBackgroundScrollOffset(offset: Float) {
        _backgroundScrollOffset.value = offset
    }

    fun setCoverTransparency(value: Float) {
        viewModelScope.launch { globalSettings.setCoverTransparency(value) }
    }

    fun setAnimationSpeed(value: Float) {
        viewModelScope.launch { globalSettings.setAnimationSpeed(value) }
    }

    fun setAnimationVisibility(value: Boolean) {
        viewModelScope.launch { globalSettings.setAnimationVisibility(value) }
    }

    fun setAnimationType(value: Int) {
        viewModelScope.launch { 
            globalSettings.setAnimationType(value)
            when (value) {
                0 -> setAnimationSpeed(0.1f) // Stars
                4 -> setAnimationSpeed(0.4f) // Kaleidoscope
                5 -> setAnimationSpeed(0.1f) // Flower
                else -> setAnimationSpeed(1.0f)
            }
        }
    }

    fun setSfwText(value: String) {
        viewModelScope.launch { globalSettings.setSfwText(value) }
    }

    fun setNsfwText(value: String) {
        viewModelScope.launch { globalSettings.setNsfwText(value) }
    }

    fun setDescriptionUiTransparency(value: Float) {
        viewModelScope.launch { globalSettings.setDescriptionUiTransparency(value) }
    }

    fun setHomeUiTransparency(value: Float) {
        viewModelScope.launch { globalSettings.setHomeUiTransparency(value) }
    }

    fun setAppTheme(value: Int) {
        viewModelScope.launch { 
            globalSettings.setAppTheme(value)
            // If theme is Pure Dark (1), default the pill/glow color to Preset 2 (Grape)
            if (value == 1) {
                globalSettings.setGlowColor(0xFF7A6284L)
            }
            val themeName = when (value) {
                0 -> "Orchid"
                1 -> "Pure Dark"
                2 -> "Poiesis"
                3 -> "Forest"
                4 -> "Amethyst"
                5 -> "Blueberry"
                6 -> "Snowfall"
                7 -> "Grape"
                else -> "Orchid"
            }
            // Theme logging removed per request
        }
    }

    fun setStorageMode(value: Int) {
        viewModelScope.launch { globalSettings.setStorageMode(value) }
    }

    fun setMediaMode(value: Int) {
        viewModelScope.launch { 
            globalSettings.setMediaMode(value)
            if (value == 1 || value == 2) {
                globalSettings.setStorageMode(1)
            }
        }
    }

    fun setHasAskedDefaultApp(value: Boolean) {
        viewModelScope.launch { globalSettings.setHasAskedDefaultApp(value) }
    }

    fun setVideoLayoutMode(value: Int) {
        viewModelScope.launch { globalSettings.setVideoLayoutMode(value) }
    }

    fun setIsLibraryLocked(value: Boolean) {
        viewModelScope.launch { globalSettings.setIsLibraryLocked(value) }
    }

    fun setLibraryPassword(value: String) {
        viewModelScope.launch { 
            globalSettings.setLibraryPassword(value)
            com.ballade.hwaran.data.local.HistoryTracker.logEvent("PASSWORD_SET", if (value.isEmpty()) "Password Cleared" else "Password Configured", "")
        }
    }

    fun setSplashVideoName(value: String) {
        viewModelScope.launch { globalSettings.setSplashVideoName(value) }
    }

    fun setSplashRandomize(value: Boolean) {
        viewModelScope.launch { globalSettings.setSplashRandomize(value) }
    }

    fun setGlowBrightness(value: Float) {
        viewModelScope.launch { globalSettings.setGlowBrightness(value) }
    }

    fun setGlowRadius(value: Float) {
        viewModelScope.launch { globalSettings.setGlowRadius(value) }
    }

    fun setGlowColor(value: Long) {
        viewModelScope.launch { globalSettings.setGlowColor(value) }
    }

    fun setFabStyle(value: Int) {
        viewModelScope.launch { globalSettings.setFabStyle(value) }
    }

    fun setUsePillAsHighlight(value: Boolean) {
        viewModelScope.launch { globalSettings.setUsePillAsHighlight(value) }
    }

    fun setMusicMode(value: Int) {
        viewModelScope.launch { globalSettings.setMusicMode(value) }
    }

    fun setBatterySavingMode(value: Boolean) {
        viewModelScope.launch { globalSettings.setBatterySavingMode(value) }
    }

    fun setStopTracking(value: Boolean) {
        viewModelScope.launch { globalSettings.setStopTracking(value) }
    }

    fun setHasSeenIntro(value: Boolean) {
        viewModelScope.launch { globalSettings.setHasSeenIntro(value) }
    }

    fun setActiveTab(value: Int) {
        _activeTab.value = value
    }
}
