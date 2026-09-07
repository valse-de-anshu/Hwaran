# Comprehensive Architecture & Code Audit Report: Hwaran Android Project

**Audit Date:** September 8, 2026  
**Target Codebase:** Hwaran Android Application  
**Package:** `com.ballade.hwaran`  
**Scope:** System Architecture, Data Layer (Room & Repositories), UI & State Management (Compose & ViewModels), Media Playback (Media3/ExoPlayer), Concurrency & Threading, Android System Integration, Build System & Git Hygiene.

---

## Executive Summary & Architecture Health Score

| Dimension | Rating (A-F) | Severity | Key Symptoms |
| :--- | :---: | :---: | :--- |
| **System Architecture & Design** | **F** | Critical | Total absence of Clean Architecture; zero Dependency Injection (Hilt/Koin); God-classes; static singletons holding mutable application state; deep parameter drilling. |
| **Data Layer & Persistence** | **D-** | Critical | Extreme entity overloading (`MangaEntity` and `ChapterEntity` representing 8 distinct media domains); zero indexes; missing foreign keys; desynchronized playlist duplication; 732-line monolithic repository; DataStore settings explosion. |
| **UI Layer & State Management** | **F** | Critical | Giant "God" Composables (`HistoryScreen.kt` at 2,676 lines; `HomeScreen.kt` at 2,112 lines; `DescriptionScreen.kt` at 1,956 lines); direct SQLite access inside Composable functions; 1,040-line `DescriptionViewModel`; state explosion. |
| **Media Playback & Services** | **D** | High | Leaked `ExoPlayer` instance via unreleased `HwaranPlayerHolder` singleton; ViewModel listener leaks; main-thread blocking bitmap operations inside playback setup; 500ms continuous atomic DataStore write loop. |
| **I/O & Concurrency** | **D+** | High | Heavy SAF tree traversal via `DocumentFile.listFiles()` triggering cascading IPC calls; `Dispatchers.Main` blocking; unmanaged global coroutine scopes (`AppImportScope`). |
| **Code Duplication & Hygiene** | **F** | Critical | ~70%+ duplicated import pipeline across Book, Toon, Video, and Music (26 redundant files); 385 lines of dead code (`SeriesStructureImporter.kt`); scratch files and merge leftovers committed to Git. |
| **Build & Platform Integration** | **C-** | Moderate | Deprecated AGP flags (`enableJetifier`); preview target SDK (`compileSdk = 37`); unregistered Activity in `AndroidManifest.xml` (`PopUpPlayerActivity`). |

---

## 1. Directory Structure & Codebase Layout Pathology

### 1.1 Current Directory Layout
```
app/src/main/java/com/ballade/hwaran/
├── audio/                      # Media3 audio player holder and service
├── data/
│   ├── book/                   # 7 import files for Books (PDFs)
│   ├── local/                  # Room Database, DAOs, Entities, DataStore
│   ├── music/                  # 5 import files for Music
│   ├── repository/             # Single 732-line LibraryRepository
│   ├── toon/                   # 7 import files for Manga/Toon
│   └── video/                  # 8 import files for Video (including dead code)
├── ui/
│   ├── components/             # Reusable UI components mixed with full screen dialogs
│   ├── navigation/             # NavGraph (parameter-drilling anti-pattern)
│   ├── screens/                # Monolithic screen composables (up to 2,676 lines)
│   │   ├── history/            # History sub-trackers & cards
│   │   ├── neverwatched/       # Never-watched composables
│   │   └── workspace/          # Workspace dialogs and managers
│   ├── theme/                  # Colors, Typography, Material 3 Theme
│   └── viewmodels/             # 7 monolithic ViewModels
├── HwaranApp.kt                # Application class
├── MainActivity.kt             # Monolithic entry point
├── PopUpPlayerActivity.kt      # Unregistered floating player activity
└── SplashActivity.kt           # 9-second splash screen with unclosed WebView
```

### 1.2 Layout Anti-Patterns Identified
1. **Arbitrary Categorization vs. Feature-Driven Architecture:**  
   Code is split between flat technical layers (`data/`, `ui/`, `audio/`) and fractured domain modules (`data/book/`, `data/music/`, `data/toon/`, `data/video/`), but this domain split is completely absent in `ui/screens/` and `ui/viewmodels/`.
2. **Component & Screen Confusion:**  
   The `ui/components/` directory contains full-screen dialogs (`GenreSelectionDialog.kt`, `PlaylistSelectionDialog.kt`), complex business widgets (`MediaModeIndicator.kt` - 417 lines), and dynamic background engines (`JellyBall.kt` - 487 lines), while `ui/screens/` contains background animation renderers (`CelestialBackground.kt`, `DrunkStarsBackground.kt`, `FlowerBackground.kt`, `JellyfishBackground.kt`, `LiquidBackground.kt`, `PokerBackground.kt`).
3. **Massive Code Bloat:**  
   Over 33,000 lines of Kotlin are concentrated in just a handful of bloated files. 5 files account for over 9,000 lines of UI code.

---

## 2. Data Layer Pathology: Room, Repositories & DataStore

### 2.1 Entity Overloading & Domain Collapse (`Entities.kt:6-42`)
The persistence model forces all application media into two primary Room entities:
- **`MangaEntity` (table: `manga`):**  
  Overloaded to represent 8 distinct domains:
  1. Webtoons / Manga series (`contentType = 0`)
  2. Books / Standalone PDF files (`contentType = 1`)
  3. Standalone Video files (`contentType = 2`)
  4. Video Series root containers (`contentType = 2, boxPurpose = "series"`)
  5. Video Seasons / Child containers (`contentType = 2, parentMangaId != null`)
  6. Music Albums / Artists (`contentType = 3`)
  7. Custom User Playlists (`contentType = 3, parentUri = "custom_playlist_..."`)
  8. Favorites Playlist (`contentType = 3, parentUri = "favorites_..."`)
- **`ChapterEntity` (table: `chapter`):**  
  Overloaded to represent Manga chapters, standalone PDF files, Video episodes, and Music audio tracks (with fields like `artist`, `duration`, `lyrics` that are null for all non-audio media).

#### Critical Flaws:
- **Denormalization (Violation of 1NF/2NF/3NF):** Migration 7→8 and 8→9 patched audio metadata directly into `chapter`. Migration 9→10 patched reader state (`lastReadTitle`, `lastReadPage`) into `manga`.
- **Inverted Playlist Model:** In `LibraryViewModel.kt:604-650`, adding a song to a playlist or "Favorites" clones the entire `ChapterEntity` row (`id = 0, mangaId = playlistId`). A song in 3 playlists exists 4 times in SQLite. Updating song metadata or deleting files causes database desynchronization.

### 2.2 Missing Relational Integrity & Missing Indexes
- **Zero Foreign Keys:**  
  `ChapterEntity` has no `@ForeignKey` to `MangaEntity(id)`. Deleting a `MangaEntity` leaves child records orphaned in SQLite unless manually cleaned up across multiple DAO calls.
- **Zero Indexes:**  
  Neither `MangaEntity`, `ChapterEntity`, nor `PdfMarkerEntity` defines an `@Index`.
  - Queries filtering by `WHERE mangaId = :mangaId` (`LibraryDao.kt:58`) force a **full-table scan** of the entire `chapter` table.
  - Verification queries like `WHERE folderUri = :uri` and `WHERE parentUri = :uri` force **full-table scans** during every file import.

### 2.3 Monolithic Repository Anti-Pattern (`LibraryRepository.kt:1-732`)
`LibraryRepository` is a 732-line God-class that violates the Single Responsibility Principle:
1. Low-level SAF tree streaming and file copying (`importMangaToVaultSaf`, lines 30–224).
2. Destructive file operations: line 166 directly executes `sourceDoc.delete()` after copying to vault, with no confirmation safeguard.
3. PDF rendering: uses `PdfRenderer` to draw bitmaps and compress to JPEG (lines 226–250).
4. Audio/Video metadata extraction: instantiates `MediaMetadataRetriever` twice per audio track (lines 252–308).
5. 374-line folder scanner (`scanImportedFolder`, lines 310–683).
6. Direct `DocumentsContract` cursor queries (lines 685–730).
7. **Direct DAO Bypassing:** ViewModels bypass `LibraryRepository` and make over 40 direct calls to `database.libraryDao()`.

### 2.4 DataStore Settings Explosion & Security Vulnerabilities (`GlobalSettings.kt:1-369`)
1. **33 Preferences in One Flat File:** Visual themes, media modes, security, tour states, tracking, and workspaces are dumped into a single unencrypted file.
2. **Plaintext Password Storage:** `LIBRARY_PASSWORD` (lines 29, 204, 296) stores the lock password in raw, unhashed plaintext.
3. **Delimiter Serialization:** Workspaces are stored as pipe-delimited strings (`"work1|||work2"`), vulnerable to delimiter injection.
4. **Recomposition Storms:** 25+ exposed `Flow` properties lack `.distinctUntilChanged()`. Any setting mutation emits to all collectors across the entire app.

---

## 3. The MegaImport Duplication Disaster

The codebase contains 4 parallel media import packages (`data/book/`, `data/music/`, `data/toon/`, `data/video/`) totaling 27 files:

| Duplication Category | Evidence |
| :--- | :--- |
| **100% Identical Summary Models** | `BookMegaImportSummary.kt`, `ToonMegaImportSummary.kt`, `VideoMegaImportSummary.kt` are byte-for-byte clones. |
| **Redundant Import Repositories** | `BookImportRepository`, `MusicImportRepository`, `ToonImportRepository`, `VideoImportRepository` are 25–40 line redundant wrappers over `LibraryDao`. |
| **7 Cloned Mega-Import Loops** | Identical tree iteration, validation, progress calculation, cancellation, and error collection across all 4 types. |
| **8th Inlined Import Loop** | `LibraryViewModel.kt:264-353` implements an 8th copy of the mega-import loop directly inside the ViewModel. |
| **Dead Code** | `SeriesStructureImporter.kt` (385 lines) is never invoked anywhere in the application. |
| **Critical Vault Bug** | `LibraryViewModel.kt:387` hardcodes `"manga_vault"` for duplicate checks, allowing local Books (`book_vault`) and Videos (`video_vault`) to be imported multiple times without duplicate warnings. |

---

## 4. UI Layer & State Management Pathology

### 4.1 The Giant "God" Composables
Compose UI is severely compromised by massive, monolithic Composable functions:

```
HistoryScreen.kt          -> 2,676 lines (single composable is ~900 lines)
HomeScreen.kt             -> 2,112 lines (LibraryContent alone is 1,064 lines)
DescriptionScreen.kt      -> 1,956 lines (DescriptionScreen composable is 1,805 lines!)
SettingsScreen.kt         -> 1,882 lines
MusicScreen.kt            -> 1,208 lines
PdfReaderScreen.kt        -> 1,187 lines
NowPlayingScreen.kt       -> 1,133 lines
```

### 4.2 Direct SQLite Access Inside UI Composables
Composable functions bypass ViewModels and Repositories, directly querying `AppDatabase.getDatabase(context)`:
- `EditSongScreen.kt:44`: `val database = remember { AppDatabase.getDatabase(context) }`
- `HistoryScreen.kt:549`: `val database = remember { AppDatabase.getDatabase(context) }`
- `HomeScreen.kt:1360`: `val db = com.ballade.hwaran.data.local.AppDatabase.getDatabase(context)`
- `PdfReaderScreen.kt:159`: `val database = remember { AppDatabase.getDatabase(context) }`
- `SettingsScreen.kt:800`: `val database = remember { AppDatabase.getDatabase(context) }`
- `VideoPlayerScreen.kt:92`: `val database = remember { AppDatabase.getDatabase(context) }`

### 4.3 Monolithic ViewModels & State Explosions
- **`DescriptionViewModel.kt` (1,042 lines):**  
  Manages folder parsing, image picking, database mutations, chapter deletions, cover rendering, and maintains a top-level unmanaged `AppImportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
- **`LibraryViewModel.kt` (795 lines):**  
  Exposes 20+ separate `StateFlow` primitives instead of a consolidated sealed UI State (`UiState<T>`).
- **NavGraph Parameter Drilling (`NavGraph.kt:101-365`):**  
  `NavGraph` manually instantiates multiple ViewModels and passes 10–17 parameters and callbacks down each screen branch (e.g. `LibraryContent` with 17 arguments).

---

## 5. Media Playback, Services & Concurrency Audit

### 5.1 ExoPlayer Lifecycle Leaks & Singletons (`HwaranPlayerHolder.kt`)
- `HwaranPlayerHolder` is a static singleton holding `@Volatile private var player: ExoPlayer? = null`.
- `HwaranPlayerHolder.release()` is **never called anywhere in the entire codebase**. The player instance, its decoders (`MediaCodec`), audio tracks, and memory remain allocated for the process lifetime.
- In `MusicViewModel.kt:54-66`, player listeners are attached every time `exoPlayer` is accessed, but `onCleared()` never detaches them. This permanently leaks `MusicViewModel` via the static player's listener list.

### 5.2 Main-Thread ANR in Artwork Decoding (`MusicViewModel.kt:268-306`)
- `createMediaItems()` is invoked on `Dispatchers.Main` during `preparePlayerFromState()` and `restorePlaybackState()`.
- Inside `createMediaItems()`, `getOrCreateSquareCover()` performs blocking image I/O, `BitmapFactory.decodeStream`, cropping, and PNG compression synchronously on the main thread for every song in the playlist.

### 5.3 500ms Continuous DataStore Disk Flooding (`MusicViewModel.kt:213-233`)
```kotlin
viewModelScope.launch {
    while (true) {
        delay(500)
        if (exoPlayer.playbackState != Player.STATE_IDLE) {
            savePlaybackState() // Calls context.dataStore.edit { ... }
        }
    }
}
```
Every 500ms, an atomic disk transaction is executed against flash storage, causing disk thrashing, memory churn, and battery drain.

### 5.4 Video Player Background Audio Leak (`VideoPlayerScreen.kt:232-304`)
- `ExoPlayer` is created inside `@Composable` via `remember(videoUri)`.
- On `Lifecycle.Event.ON_PAUSE`, it persists timestamp but **never pauses the player**. Video audio continues playing in the background without a foreground notification.
- No `AudioAttributes` or audio focus handling is configured.

### 5.5 Manifest & System Integration Deficiencies
1. **Unregistered Activity:** `PopUpPlayerActivity` is not declared in `AndroidManifest.xml`. Launching it crashes immediately with `ActivityNotFoundException`.
2. **Missing Media Permissions:** No `READ_MEDIA_AUDIO` or `READ_MEDIA_VIDEO` permissions are declared. Direct `File()` operations fail on Android 10+ scoped storage.
3. **Intent Stack Duplication:** Neither `MainActivity` nor `SplashActivity` specifies `launchMode="singleTop"`. External file opens and notification clicks create duplicate stacked activity instances.
4. **9-Second Forced Splash Delay:** `SplashActivity.kt` forces a hardcoded 9-second delay and creates an unclosed `WebView`.

---

## 6. Git Hygiene, Build System & Code Quality

### 6.1 Git Status & Commit History
- **Branch Strategy:** Work has been committed directly to `master` without feature branches or PR reviews.
- **Commit Messages:** Vague commits (e.g. `checkpoint : 2,we are broke but we need to restore the app`).
- **Committed Scratch Files:** Scratch files (`patch.kt`, `test_media3.kt`, `test_pdf.kt`, `test_permissions.kt`, `update_shadows.py`) and merge conflict artifacts (`DescriptionViewModel.kt.orig`) were directly tracked in the Git repository.
- **Payment Gateway Files:** Experimental backup files (`backup/payment_gateway/`) are preserved as requested for future integration.

### 6.2 Build System & Versions (`build.gradle.kts`, `libs.versions.toml`)
1. **Target SDK 37:** Android SDK 37 is a preview version. The current stable production target is API 35 (Android 15).
2. **Deprecated Gradle Flags in `gradle.properties`:**
   - `android.enableJetifier=true` (deprecated, scheduled for removal in AGP 10).
   - `android.disallowKotlinSourceSets=false` (experimental).
3. **Version Catalog Typo:** `libs.versions.toml:48` contains `aandroidx-media3-session` (accidental double 'a').

---

## 7. Priority Matrix & Architectural Recommendations

```
+-------------------------------------------------------------------------+
|                          SEVERITY & IMPACT MATRIX                       |
+-------------------------------------------------------------------------+
| CRITICAL (Fix First)                                                    |
| 1. Fix Room integrity: Schema v16 (indexes, foreign keys, cascade).     |
| 2. Unify 26 import files into 1 generic MediaImportManager.             |
| 3. Eliminate direct SQLite access from Composable UI functions.         |
| 4. Fix ExoPlayer memory leak & main-thread artwork ANR in MusicViewModel|
| 5. Declare or remove PopUpPlayerActivity in AndroidManifest.xml.        |
+-------------------------------------------------------------------------+
| HIGH (Phase 2)                                                          |
| 1. Introduce Dependency Injection (Hilt or Koin).                       |
| 2. Break down monolithic Composables (HomeScreen, DescriptionScreen).   |
| 3. Replace 500ms DataStore polling with debounced event persistence.    |
| 4. Hash/encrypt LIBRARY_PASSWORD in DataStore.                          |
| 5. Replace DocumentFile tree crawling with fast DocumentsContract cursor.|
+-------------------------------------------------------------------------+
| MEDIUM (Phase 3)                                                        |
| 1. Decompose GlobalSettings into modular preference stores.             |
| 2. Adopt strict feature-driven package structure.                       |
| 3. Add .distinctUntilChanged() to all preference flows.                 |
| 4. Normalize workspaces into Room database.                             |
| 5. Standardize compileSdk/targetSdk to API 35.                          |
+-------------------------------------------------------------------------+
```
