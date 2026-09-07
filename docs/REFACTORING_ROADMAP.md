# Architectural Refactoring Roadmap & Target Blueprint

**Document:** Target Architecture & Phased Refactoring Strategy  
**Project:** Hwaran Media Manager  
**Status:** Implementation Blueprint  

---

## 1. Target Folder & Package Architecture

The current codebase suffers from a chaotic mixture of flat technical layers and fractured ad-hoc media folders. The target architecture follows **Feature-Driven Clean Architecture** combined with Unidirectional Data Flow (UDF) and Dependency Injection (Hilt / Koin).

```
app/src/main/java/com/ballade/hwaran/
├── core/
│   ├── database/                   # Room Database instance, migrations, TypeConverters
│   │   ├── AppDatabase.kt
│   │   ├── dao/                    # Focused DAOs
│   │   │   ├── MediaDao.kt
│   │   │   ├── TrackDao.kt
│   │   │   ├── PlaylistDao.kt
│   │   │   └── HistoryDao.kt
│   │   └── entity/                 # Normalized entities
│   ├── datastore/                  # Domain-partitioned preferences
│   │   ├── UiPreferences.kt
│   │   ├── PlayerPreferences.kt
│   │   └── SecurityPreferences.kt
│   ├── di/                         # Hilt/Koin modules
│   │   ├── DatabaseModule.kt
│   │   ├── RepositoryModule.kt
│   │   └── MediaModule.kt
│   ├── model/                      # Immutable Domain Models (UI & Business)
│   │   ├── MediaItem.kt
│   │   ├── MediaTrack.kt
│   │   ├── Playlist.kt
│   │   └── ImportResult.kt
│   └── ui/                         # Design system tokens & base components
│       ├── theme/
│       ├── components/             # Reusable atomic UI (Pill, Slider, GlassPanel)
│       └── background/             # Dynamic background renderers
├── features/
│   ├── home/                       # Home & Library collection view
│   │   ├── HomeScreen.kt           # Slim screen coordinator (~150 lines)
│   │   ├── HomeViewModel.kt
│   │   ├── components/             # Decomposed cards, filters, mode pills
│   │   └── dialogs/                # Import dialogs, mode selectors
│   ├── description/                # Media details, chapters & child boxes
│   │   ├── DescriptionScreen.kt    # Slim screen coordinator (~200 lines)
│   │   ├── DescriptionViewModel.kt
│   │   └── components/             # ChapterList, BoxGrid, EditHeader
│   ├── music/                      # Music playback, playlists, now playing
│   │   ├── MusicScreen.kt
│   │   ├── NowPlayingScreen.kt
│   │   ├── MusicViewModel.kt
│   │   ├── service/                # MediaSessionService implementation
│   │   └── components/
│   ├── reader/                     # Webtoon & PDF reader
│   │   ├── WebtoonReaderScreen.kt
│   │   ├── PdfReaderScreen.kt
│   │   └── ReaderViewModel.kt
│   ├── video/                      # Video playback & series viewer
│   │   ├── VideoPlayerScreen.kt
│   │   └── VideoViewModel.kt
│   ├── history/                    # History analytics & cards
│   │   ├── HistoryScreen.kt
│   │   └── HistoryViewModel.kt
│   ├── settings/                   # Global user preferences
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   └── workspace/                  # Multi-workspace management
└── importer/                       # Consolidated media import engine
    ├── MediaImportManager.kt       # Single coordinator
    ├── strategies/                 # Strategy pattern per media format
    │   ├── BookImportStrategy.kt
    │   ├── ToonImportStrategy.kt
    │   ├── VideoImportStrategy.kt
    │   └── MusicImportStrategy.kt
    └── scanner/                    # High-speed SAF DocumentsContract scanner
```

---

## 2. Phase-by-Phase Remediation Roadmap

### Phase 1: Database Normalization & Indexing (Schema v16)
**Objective:** Restore relational integrity, prevent full-table scans, and eliminate data duplication.

1. **Add Foreign Keys & Cascading Deletions:**
   - Attach `@ForeignKey(entity = MangaEntity::class, parentColumns = ["id"], childColumns = ["mangaId"], onDelete = ForeignKey.CASCADE)` to `ChapterEntity`.
   - Attach `@ForeignKey(entity = MangaEntity::class, parentColumns = ["id"], childColumns = ["mangaId"], onDelete = ForeignKey.CASCADE)` to `PdfMarkerEntity`.
2. **Add Critical Indexes:**
   - Index `chapter(mangaId)`
   - Index `chapter(folderUri)`
   - Index `manga(parentUri)`
   - Composite Index `manga(contentType, workspace)`
3. **Normalize Playlists (Schema v16):**
   - Create `playlists` table (`id`, `title`, `coverUri`, `createdAt`).
   - Create `playlist_tracks` join table (`playlist_id`, `track_id`, `position`).
   - Deprecate cloning `ChapterEntity` rows into fake playlist parent IDs.
4. **Enable Schema Exporting:**
   - Set `exportSchema = true` in `AppDatabase` and configure KSP schema directory in `app/build.gradle.kts`.

---

### Phase 2: Consolidated Media Import Engine
**Objective:** Delete 26 redundant files (~3,000 duplicated lines) and unify import under a single strategy pattern.

1. **Strategy Pattern Implementation:**
   - Define `interface MediaImportStrategy`:
     ```kotlin
     interface MediaImportStrategy {
         val mediaType: MediaType
         val supportedExtensions: Set<String>
         suspend fun inspectFolder(folder: DocumentFile): ValidationResult
         suspend fun parseItem(context: Context, uri: Uri): MediaMetadata
     }
     ```
2. **Single `MediaImportManager`:**
   - Replace 7 duplicate mega-import loops with one generic loop handling validation, cancellation, progress calculation, and database batch insertions.
3. **High-Speed SAF Cursor Scanner:**
   - Replace recursive `DocumentFile.listFiles()` calls with single cursor queries on `DocumentsContract.buildChildDocumentsUriUsingTree`, speeding up directory scanning by 50x–100x.
4. **Delete Redundant Packages:**
   - Safely remove `data/book/`, `data/music/`, `data/toon/`, and `data/video/` import files once migrated.

---

### Phase 3: Decomposition of God Composables & ViewModels
**Objective:** Break 2,000+ line files into small, testable, single-responsibility components.

1. **`HomeScreen.kt` (2,112 lines -> ~150 lines):**
   - Extract `LibraryContent` (1,064 lines) into `features/home/components/LibraryGrid.kt`.
   - Extract `MangaCard` (85 lines) into `features/home/components/MangaCard.kt`.
   - Extract `ImportTypeDialog` (229 lines) and `MegaImportSummaryDialog` (144 lines) into `features/home/dialogs/`.
2. **`DescriptionScreen.kt` (1,956 lines -> ~200 lines):**
   - Extract chapter list rendering into `ChapterSection.kt`.
   - Extract child box grid rendering into `ChildBoxesSection.kt`.
   - Extract dialogs (Delete, Create Box, Initial Label) into separate dialog composables.
3. **`DescriptionViewModel.kt` (1,042 lines -> ~300 lines):**
   - Remove top-level `AppImportScope`. All operations must run under `viewModelScope` with proper cancellation.
   - Delegate file I/O and cover rendering to dedicated use cases/repositories.
4. **Purge Direct SQLite Access from UI:**
   - Remove all instances of `val database = remember { AppDatabase.getDatabase(context) }` from composables. Route all state through ViewModels and Repositories.

---

### Phase 4: Media Playback & Audio Engine Modernization
**Objective:** Eliminate memory leaks, thread violations, and main-thread ANR traps.

1. **Eliminate Static `HwaranPlayerHolder`:**
   - Bind `ExoPlayer` directly inside `MusicNotificationService`.
   - UI and ViewModels must interact with playback exclusively through `MediaBrowser` or `MediaController` bound to the `SessionToken`.
2. **Offload Artwork Processing from Main Thread:**
   - Remove synchronous `BitmapFactory.decodeStream` and PNG compression from `createMediaItems()` on `Dispatchers.Main`.
   - Cache artwork asynchronously in the background during import or stream directly via Coil.
3. **Event-Driven Playback Persistence:**
   - Delete the 500ms continuous DataStore write loop.
   - Persist playback progress only on track changes, pause events, and debounced at 15–30 second intervals.
4. **Video Playback Lifecycle Fix:**
   - Migrate video playback into a dedicated `VideoViewModel`.
   - Ensure `exoPlayer.pause()` is executed on `Lifecycle.Event.ON_STOP` to prevent background audio leakage.

---

### Phase 5: Dependency Injection & System Manifest Fixes
**Objective:** Modernize architectural plumbing and ensure Android platform compliance.

1. **Adopt Dependency Injection (Hilt):**
   - Annotate `HwaranApp` with `@HiltAndroidApp`.
   - Annotate `MainActivity` and `SplashActivity` with `@AndroidEntryPoint`.
   - Inject Database, DataStore, and Repositories directly into ViewModels (`@HiltViewModel`).
   - Eliminate parameter drilling across `NavGraph.kt`.
2. **Manifest Updates:**
   - Declare media permissions: `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`.
   - Set `android:launchMode="singleTop"` on `MainActivity` to prevent stacked instances on external intent launches.
   - Register or deprecate `PopUpPlayerActivity`.
   - Standardize target SDK to API 35 (Android 15) in `app/build.gradle.kts`.
