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
import com.ballade.hwaran.core.datastore.GlobalSettings
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
        val tempFile = File(context.cacheDir, "thumb_gen_$videoName.mp4")
        try {
            context.resources.openRawResource(resId).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            val finalBitmap = createVideoThumbnail(tempFile)
            if (finalBitmap != null) {
                val scaled = Bitmap.createScaledBitmap(finalBitmap, 120, 180, true)
                withContext(Dispatchers.Main) {
                    thumbnails[resId] = scaled.asImageBitmap()
                }
                if (finalBitmap != scaled) finalBitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Thumbnail failed for raw $videoName", e)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private suspend fun generateThumbnailFromPath(context: Context, fileName: String, hashCode: Int) {
        val file = File(context.filesDir, "custom_splash/$fileName")
        if (!file.exists()) return
        
        try {
            val finalBitmap = createVideoThumbnail(file)
            if (finalBitmap != null) {
                val scaled = Bitmap.createScaledBitmap(finalBitmap, 120, 180, true)
                withContext(Dispatchers.Main) {
                    thumbnails[hashCode] = scaled.asImageBitmap()
                }
                if (finalBitmap != scaled) finalBitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Thumbnail failed for path $fileName", e)
        }
    }

    private fun createVideoThumbnail(file: File): Bitmap? {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ThumbnailUtils.createVideoThumbnail(file, Size(300, 450), null)
            } catch (e: Exception) {
                null
            }
        } else {
            @Suppress("DEPRECATION")
            ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
        }

        return bitmap ?: run {
            val retriever = MediaMetadataRetriever()
            var fis: FileInputStream? = null
            try {
                fis = FileInputStream(file)
                retriever.setDataSource(fis.fd)
                retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(500000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0)
            } catch (e: Exception) {
                null
            } finally {
                try { fis?.close() } catch (e: Exception) {}
                try { retriever.release() } catch (e: Exception) {}
            }
        }
    }

    fun addCustomSplashVideo(context: Context, uri: android.net.Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val customDir = File(context.filesDir, "custom_splash")
                    if (!customDir.exists()) customDir.mkdirs()
                    
                    val fileName = "custom_${System.currentTimeMillis()}.mp4"
                    val destinationFile = File(customDir, fileName)
                    
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destinationFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    globalSettings.addCustomSplashVideo(fileName)
                    generateThumbnailFromPath(context, fileName, fileName.hashCode())
                } catch (e: Exception) {
                    Log.e("SettingsViewModel", "Failed to add custom splash", e)
                }
            }
        }
    }

    fun removeCustomSplashVideo(context: Context, fileName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(context.filesDir, "custom_splash/$fileName")
                    if (file.exists()) file.delete()
                    globalSettings.removeCustomSplashVideo(fileName)
                    withContext(Dispatchers.Main) {
                        thumbnails.remove(fileName.hashCode())
                    }
                } catch (e: Exception) {
                    Log.e("SettingsViewModel", "Failed to remove custom splash", e)
                }
            }
        }
    }

    val coverTransparency: StateFlow<Float> = globalSettings.coverTransparencyFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 1.0f)

    val animationSpeed: StateFlow<Float> = globalSettings.animationSpeedFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    val animationVisibility: StateFlow<Boolean> = globalSettings.animationVisibilityFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val animationType: StateFlow<Int> = globalSettings.animationTypeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    val sfwText: StateFlow<String> = globalSettings.sfwTextFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, "X")

    val nsfwText: StateFlow<String> = globalSettings.nsfwTextFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, "Y")

    val descriptionUiTransparency: StateFlow<Float> = globalSettings.descriptionUiTransparencyFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 1.0f)

    val homeUiTransparency: StateFlow<Float> = globalSettings.homeUiTransparencyFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 1.0f)

    val appTheme: StateFlow<Int> = globalSettings.appThemeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    val storageMode: StateFlow<Int> = globalSettings.storageModeFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val mediaMode: StateFlow<Int> = globalSettings.mediaModeFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
        
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

    fun addWorkspace(mediaMode: Int, workspace: String) {
        viewModelScope.launch {
            globalSettings.addWorkspace(mediaMode, workspace)
        }
    }

    fun removeWorkspace(mediaMode: Int, workspace: String) {
        viewModelScope.launch {
            globalSettings.removeWorkspace(mediaMode, workspace)
        }
    }

    fun setActiveScreen(mediaMode: Int, screenName: String?) {
        viewModelScope.launch {
            globalSettings.setActiveScreen(mediaMode, screenName)
        }
    }
    
    val hasAskedDefaultApp: StateFlow<Boolean> = globalSettings.hasAskedDefaultAppFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

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

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    init {
        viewModelScope.launch {
            // Read critical launch settings in one go (single DataStore read)
            try {
                val theme = globalSettings.appThemeFlow.first()
                val animType = globalSettings.animationTypeFlow.first()
                val animVis = globalSettings.animationVisibilityFlow.first()
                val introSeen = globalSettings.hasSeenIntroFlow.first()

                // Wait for the StateFlows to reflect the loaded values using combine —
                // this is a true suspension (no CPU spin) with a hard 300ms timeout.
                val settled = kotlinx.coroutines.withTimeoutOrNull(300) {
                    kotlinx.coroutines.flow.combine(
                        appTheme, animationType, animationVisibility, hasSeenIntro
                    ) { t, at, av, hi -> t == theme && at == animType && av == animVis && hi == introSeen }
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
            com.ballade.hwaran.core.util.HistoryTracker.logEvent("PASSWORD_SET", if (value.isEmpty()) "Password Cleared" else "Password Configured", "")
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
