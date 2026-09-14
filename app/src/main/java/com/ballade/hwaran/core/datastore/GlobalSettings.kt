package com.ballade.hwaran.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class GlobalSettings(private val context: Context) {

    companion object {
        val SFW_TEXT = stringPreferencesKey("sfw_text")
        val NSFW_TEXT = stringPreferencesKey("nsfw_text")
        val COVER_TRANSPARENCY = floatPreferencesKey("cover_transparency")
        val ANIMATION_SPEED = floatPreferencesKey("animation_speed")
        val ANIMATION_VISIBILITY = booleanPreferencesKey("animation_visibility")
        val ANIMATION_TYPE = intPreferencesKey("animation_type")
        val DESCRIPTION_UI_TRANSPARENCY = floatPreferencesKey("description_ui_transparency")
        val HOME_UI_TRANSPARENCY = floatPreferencesKey("home_ui_transparency")
        val APP_THEME = intPreferencesKey("app_theme")
        val STORAGE_MODE = intPreferencesKey("storage_mode")
        val MEDIA_MODE = intPreferencesKey("media_mode")
        val HAS_ASKED_DEFAULT_APP = booleanPreferencesKey("has_asked_default_app")
        val VIDEO_LAYOUT_MODE = intPreferencesKey("video_layout_mode")
        val IS_LIBRARY_LOCKED = booleanPreferencesKey("is_library_locked")
        val LIBRARY_PASSWORD = stringPreferencesKey("library_password")
        val SPLASH_VIDEO_NAME = stringPreferencesKey("splash_video_name")
        val SPLASH_RANDOMIZE = booleanPreferencesKey("splash_randomize")
        val CUSTOM_SPLASH_VIDEOS = stringSetPreferencesKey("custom_splash_videos")
        val GLOW_BRIGHTNESS = floatPreferencesKey("glow_brightness")
        val GLOW_RADIUS = floatPreferencesKey("glow_radius")
        val GLOW_COLOR = longPreferencesKey("glow_color")
        val FAB_STYLE = intPreferencesKey("fab_style")
        val MUSIC_MODE = intPreferencesKey("music_mode")
        val HAS_SEEN_INTRO = booleanPreferencesKey("has_seen_intro")
        val DESCRIPTION_LAYOUT_MODE = intPreferencesKey("description_layout_mode")
        val INTRO_SEEN_TOUR = booleanPreferencesKey("intro_seen_tour")
        val INTRO_ACCEPTED_TOUR = booleanPreferencesKey("intro_accepted_tour")
        val ACTIVE_SCREEN_TOON = stringPreferencesKey("active_screen_toon")
        val ACTIVE_SCREEN_BOOK = stringPreferencesKey("active_screen_book")
        val ACTIVE_SCREEN_VIDEO = stringPreferencesKey("active_screen_video")
        val WORKSPACES_TOON = stringPreferencesKey("workspaces_toon_v2")
        val WORKSPACES_BOOK = stringPreferencesKey("workspaces_book_v2")
        val WORKSPACES_VIDEO = stringPreferencesKey("workspaces_video_v2")
        val BATTERY_SAVING_MODE = booleanPreferencesKey("battery_saving_mode")
        val STOP_TRACKING = booleanPreferencesKey("stop_tracking")
        val USE_PILL_AS_HIGHLIGHT = booleanPreferencesKey("use_pill_as_highlight")
        val READER_MODE = intPreferencesKey("reader_mode")
        val READER_CROP_ZOOM = floatPreferencesKey("reader_crop_zoom")
        val READER_DIRECTION = intPreferencesKey("reader_direction")
        val READER_BG_COLOR = intPreferencesKey("reader_bg_color")
        val READER_KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
    }

    val stopTrackingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[STOP_TRACKING] ?: false
    }

    suspend fun setStopTracking(value: Boolean) {
        context.dataStore.edit { it[STOP_TRACKING] = value }
    }

    val workspacesToonFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[WORKSPACES_TOON]?.split("|||")?.filter { it.isNotBlank() } ?: emptyList()
    }
    val workspacesBookFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[WORKSPACES_BOOK]?.split("|||")?.filter { it.isNotBlank() } ?: emptyList()
    }
    val workspacesVideoFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[WORKSPACES_VIDEO]?.split("|||")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun addWorkspace(mediaMode: Int, workspace: String) {
        val cleanWorkspace = workspace.trim()
        if (cleanWorkspace.isBlank()) return
        context.dataStore.edit { preferences ->
            val key = when (mediaMode) {
                0 -> WORKSPACES_TOON
                1 -> WORKSPACES_BOOK
                2 -> WORKSPACES_VIDEO
                else -> return@edit
            }
            val currentStr = preferences[key] ?: ""
            val currentList = currentStr.split("|||").filter { it.isNotBlank() }.toMutableList()
            if (!currentList.contains(cleanWorkspace)) {
                currentList.add(cleanWorkspace)
                preferences[key] = currentList.joinToString("|||")
            }
        }
    }

    suspend fun removeWorkspace(mediaMode: Int, workspace: String) {
        val cleanWorkspace = workspace.trim()
        if (cleanWorkspace.isBlank()) return
        context.dataStore.edit { preferences ->
            val key = when (mediaMode) {
                0 -> WORKSPACES_TOON
                1 -> WORKSPACES_BOOK
                2 -> WORKSPACES_VIDEO
                else -> return@edit
            }
            val currentStr = preferences[key] ?: ""
            val currentList = currentStr.split("|||").filter { it.isNotBlank() }.toMutableList()
            if (currentList.contains(cleanWorkspace)) {
                currentList.remove(cleanWorkspace)
                preferences[key] = currentList.joinToString("|||")
            }
        }
    }

    val activeScreenToonFlow: Flow<String?> = context.dataStore.data.map { it[ACTIVE_SCREEN_TOON] }
    val activeScreenBookFlow: Flow<String?> = context.dataStore.data.map { it[ACTIVE_SCREEN_BOOK] }
    val activeScreenVideoFlow: Flow<String?> = context.dataStore.data.map { it[ACTIVE_SCREEN_VIDEO] }

    suspend fun setActiveScreen(mediaMode: Int, screenName: String?) {
        context.dataStore.edit { preferences ->
            val key = when (mediaMode) {
                0 -> ACTIVE_SCREEN_TOON
                1 -> ACTIVE_SCREEN_BOOK
                2 -> ACTIVE_SCREEN_VIDEO
                else -> return@edit
            }
            if (screenName == null) {
                preferences.remove(key)
            } else {
                preferences[key] = screenName
            }
        }
    }

    suspend fun renameWorkspaceIfActive(mediaMode: Int, oldWorkspace: String, newWorkspace: String) {
        context.dataStore.edit { preferences ->
            val key = when (mediaMode) {
                0 -> ACTIVE_SCREEN_TOON
                1 -> ACTIVE_SCREEN_BOOK
                2 -> ACTIVE_SCREEN_VIDEO
                else -> return@edit
            }
            if (preferences[key] == oldWorkspace) {
                preferences[key] = newWorkspace
            }
        }
    }

    val descriptionLayoutModeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DESCRIPTION_LAYOUT_MODE] ?: 1 // Default to Large (1)
    }

    suspend fun setDescriptionLayoutMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[DESCRIPTION_LAYOUT_MODE] = mode
        }
    }

    val musicModeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MUSIC_MODE] ?: 0 // Default to Album Art Mode (0)
    }

    val hasSeenIntroFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_SEEN_INTRO] ?: false
    }

    val coverTransparencyFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[COVER_TRANSPARENCY] ?: 1.0f
    }

    val animationSpeedFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        val type = preferences[ANIMATION_TYPE] ?: 0 // Match Stars (0) default
        preferences[ANIMATION_SPEED] ?: if (type == 0) 0.1f else 1.0f
    }

    val animationVisibilityFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ANIMATION_VISIBILITY] ?: true
    }

    val animationTypeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[ANIMATION_TYPE] ?: 0 // Default to Stars (0)
    }

    val sfwTextFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SFW_TEXT] ?: "X"
    }

    val nsfwTextFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[NSFW_TEXT] ?: "Y"
    }

    val descriptionUiTransparencyFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[DESCRIPTION_UI_TRANSPARENCY] ?: 1.0f
    }

    val homeUiTransparencyFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[HOME_UI_TRANSPARENCY] ?: 1.0f
    }

    val appThemeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[APP_THEME] ?: 1 // Default to Dark Theme (1)
    }

    val storageModeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[STORAGE_MODE] ?: 1 // Default to External In-Place (1)
    }

    val mediaModeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MEDIA_MODE] ?: 0
    }
    
    val hasAskedDefaultAppFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_ASKED_DEFAULT_APP] ?: false
    }

    val videoLayoutModeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[VIDEO_LAYOUT_MODE] ?: 0
    }

    val isLibraryLockedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_LIBRARY_LOCKED] ?: false
    }

    val libraryPasswordFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LIBRARY_PASSWORD] ?: ""
    }

    val splashVideoNameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SPLASH_VIDEO_NAME] ?: "3d_intro.html" // Default to Kinetic 3D
    }

    val splashRandomizeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SPLASH_RANDOMIZE] ?: false
    }

    val customSplashVideosFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_SPLASH_VIDEOS] ?: emptySet()
    }

    val glowBrightnessFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[GLOW_BRIGHTNESS] ?: 1.0f
    }

    val glowRadiusFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[GLOW_RADIUS] ?: 23f // Default 23px
    }

    val glowColorFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[GLOW_COLOR] ?: 0xFFE2E8F0L // Default Titanium Platinum
    }

    val fabStyleFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[FAB_STYLE] ?: 1 // Default to JellyBall
    }

    val usePillAsHighlightFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_PILL_AS_HIGHLIGHT] ?: false // Default OFF
    }

    suspend fun setCoverTransparency(value: Float) {
        context.dataStore.edit { it[COVER_TRANSPARENCY] = value }
    }

    suspend fun setAnimationSpeed(value: Float) {
        context.dataStore.edit { it[ANIMATION_SPEED] = value }
    }

    suspend fun setAnimationVisibility(value: Boolean) {
        context.dataStore.edit { it[ANIMATION_VISIBILITY] = value }
    }

    suspend fun setAnimationType(value: Int) {
        context.dataStore.edit { it[ANIMATION_TYPE] = value }
    }

    suspend fun setSfwText(value: String) {
        context.dataStore.edit { it[SFW_TEXT] = value }
    }

    suspend fun setNsfwText(value: String) {
        context.dataStore.edit { it[NSFW_TEXT] = value }
    }

    suspend fun setDescriptionUiTransparency(value: Float) {
        context.dataStore.edit { it[DESCRIPTION_UI_TRANSPARENCY] = value }
    }

    suspend fun setHomeUiTransparency(value: Float) {
        context.dataStore.edit { it[HOME_UI_TRANSPARENCY] = value }
    }

    suspend fun setAppTheme(value: Int) {
        context.dataStore.edit { it[APP_THEME] = value }
    }

    suspend fun setStorageMode(value: Int) {
        context.dataStore.edit { it[STORAGE_MODE] = value }
    }

    suspend fun setMediaMode(value: Int) {
        context.dataStore.edit { it[MEDIA_MODE] = value }
    }

    suspend fun setHasAskedDefaultApp(value: Boolean) {
        context.dataStore.edit { it[HAS_ASKED_DEFAULT_APP] = value }
    }

    suspend fun setVideoLayoutMode(value: Int) {
        context.dataStore.edit { it[VIDEO_LAYOUT_MODE] = value }
    }

    suspend fun setIsLibraryLocked(value: Boolean) {
        context.dataStore.edit { it[IS_LIBRARY_LOCKED] = value }
    }

    suspend fun setLibraryPassword(value: String) {
        context.dataStore.edit { it[LIBRARY_PASSWORD] = value }
    }

    suspend fun setSplashVideoName(value: String) {
        context.dataStore.edit { it[SPLASH_VIDEO_NAME] = value }
    }

    suspend fun setSplashRandomize(value: Boolean) {
        context.dataStore.edit { it[SPLASH_RANDOMIZE] = value }
    }

    suspend fun setGlowBrightness(value: Float) {
        context.dataStore.edit { it[GLOW_BRIGHTNESS] = value }
    }

    suspend fun setGlowRadius(value: Float) {
        context.dataStore.edit { it[GLOW_RADIUS] = value }
    }

    suspend fun setGlowColor(value: Long) {
        context.dataStore.edit { it[GLOW_COLOR] = value }
    }

    suspend fun setFabStyle(value: Int) {
        context.dataStore.edit { it[FAB_STYLE] = value }
    }

    suspend fun setUsePillAsHighlight(value: Boolean) {
        context.dataStore.edit { it[USE_PILL_AS_HIGHLIGHT] = value }
    }

    suspend fun setMusicMode(value: Int) {
        context.dataStore.edit { it[MUSIC_MODE] = value }
    }

    suspend fun addCustomSplashVideo(fileName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[CUSTOM_SPLASH_VIDEOS] ?: emptySet()
            preferences[CUSTOM_SPLASH_VIDEOS] = current + fileName
        }
    }

    suspend fun removeCustomSplashVideo(fileName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[CUSTOM_SPLASH_VIDEOS] ?: emptySet()
            preferences[CUSTOM_SPLASH_VIDEOS] = current - fileName
        }
    }

    suspend fun setHasSeenIntro(value: Boolean) {
        context.dataStore.edit { it[HAS_SEEN_INTRO] = value }
    }

    val introSeenTourFlow: Flow<Boolean> = context.dataStore.data.map { it[INTRO_SEEN_TOUR] ?: false }
    val introAcceptedTourFlow: Flow<Boolean> = context.dataStore.data.map { it[INTRO_ACCEPTED_TOUR] ?: false }

    suspend fun setIntroSeenTour(value: Boolean) {
        context.dataStore.edit { it[INTRO_SEEN_TOUR] = value }
    }

    suspend fun setIntroAcceptedTour(value: Boolean) {
        context.dataStore.edit { it[INTRO_ACCEPTED_TOUR] = value }
    }

    val batterySavingModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BATTERY_SAVING_MODE] ?: false // Default OFF
    }

    suspend fun setBatterySavingMode(value: Boolean) {
        context.dataStore.edit { it[BATTERY_SAVING_MODE] = value }
    }

    val readerModeFlow: Flow<Int> = context.dataStore.data.map { it[READER_MODE] ?: 0 }
    suspend fun setReaderMode(value: Int) {
        context.dataStore.edit { it[READER_MODE] = value }
    }

    val readerCropZoomFlow: Flow<Float> = context.dataStore.data.map { it[READER_CROP_ZOOM] ?: 1.0f }
    suspend fun setReaderCropZoom(value: Float) {
        context.dataStore.edit { it[READER_CROP_ZOOM] = value }
    }

    val readerDirectionFlow: Flow<Int> = context.dataStore.data.map { it[READER_DIRECTION] ?: 0 }
    suspend fun setReaderDirection(value: Int) {
        context.dataStore.edit { it[READER_DIRECTION] = value }
    }

    val readerBgColorFlow: Flow<Int> = context.dataStore.data.map { it[READER_BG_COLOR] ?: 0 }
    suspend fun setReaderBgColor(value: Int) {
        context.dataStore.edit { it[READER_BG_COLOR] = value }
    }

    val readerKeepScreenOnFlow: Flow<Boolean> = context.dataStore.data.map { it[READER_KEEP_SCREEN_ON] ?: true }
    suspend fun setReaderKeepScreenOn(value: Boolean) {
        context.dataStore.edit { it[READER_KEEP_SCREEN_ON] = value }
    }
}
