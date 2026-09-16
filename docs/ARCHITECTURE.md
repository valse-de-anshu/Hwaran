# Architecture Blueprint

> **"How is Hwaran structured and built?"**

Hwaran follows a **Feature-based Clean Architecture** coupled with **Unidirectional Data Flow (UDF)**. Heavy I/O, database queries, file scanning, metadata parsing, and media decoding are executed strictly off the main thread using Kotlin Coroutines and `Dispatchers.IO`.

---

## 1. High-Level Package Structure

```text
com.ballade.hwaran/
│
├── HwaranApp.kt                         # Application class (initializes HistoryTracker, etc.)
├── MainActivity.kt                      # Main single-activity host & external intent router
├── PopUpPlayerActivity.kt               # Floating picture-in-picture / pop-up mini audio player
├── SplashActivity.kt                    # Animated intro / 3D splash screen launcher
│
├── audio/                               # AndroidX Media3 Audio Architecture
│   ├── HwaranPlayerHolder.kt            # Singleton ExoPlayer provider
│   └── MusicNotificationService.kt      # Foreground MediaSessionService with notification provider
│
├── backend/                             # Feature-isolated data & business orchestration layer
│   ├── book/                            # BookBackend: PDF & document processing
│   ├── history/                         # HistoryBackend: event logging & queries
│   ├── music/                           # MusicBackend: audio imports & metadata
│   ├── novel/                           # NovelParser & multi-format loaders
│   ├── toon/                            # ToonBackend: manga/manhua chapter ingestion
│   ├── video/                           # Video business logic
│   │   ├── channel/                     # VideoChannelBackend: channel & video clips
│   │   └── series/                      # VideoSeriesBackend: multi-season series & franchise relations
│   └── workspace/                       # WorkspaceBackend: workspace creation, moves & renaming
│
├── core/                                # Shared cross-cutting infrastructure
│   ├── database/                        # Room database & persistence (Version 16)
│   │   ├── AppDatabase.kt               # Database singleton & migration registry
│   │   ├── dao/                         # 4 focused DAOs + composite shim
│   │   │   ├── AnnotationDao.kt         # pdf_marker operations
│   │   │   ├── HistoryDao.kt            # history_event operations
│   │   │   ├── LibraryDao.kt            # Composite interface for backward compatibility
│   │   │   ├── MediaDao.kt              # manga table operations
│   │   │   └── TrackDao.kt              # chapter table operations
│   │   └── entity/                      # Room entities (MangaEntity, ChapterEntity, etc.)
│   │       └── Entities.kt
│   ├── datastore/                       # App settings & preferences via DataStore
│   │   └── GlobalSettings.kt            # Reactive flows for UI, audio, themes, and vault settings
│   ├── metadata/                        # Portable metadata engines
│   │   ├── MediaMetadataManager.kt      # Metadata persistence & cache orchestrator
│   │   └── ZineMetadataExtractor.kt     # Multi-alias .zine JSON parser
│   ├── service/                         # Background services & managers
│   │   ├── VaultMigrationManager.kt     # Migration progress observable state flow
│   │   └── VaultMigrationService.kt     # Background foreground service for sandbox migration
│   └── util/                            # Utilities (HistoryTracker, CoverArtResolver, CoverCacheManager, LocalVaultMigrator)
│
├── data/                                # Data ingestion and repositories
│   ├── importer/                        # Centralized media import pipeline
│   │   ├── book/                        # PDF and document single/mega importers
│   │   ├── music/                       # Audio album/track single/mega importers
│   │   ├── toon/                        # Manga/manhua chapter single/mega importers
│   │   └── video/                       # Video, channel, and structured series importers (SeriesStructureImporter)
│   └── repository/                      # Domain repositories
│       └── LibraryRepository.kt         # SAF scanning, streaming, and metadata extraction
│
├── frontend/                            # Presentation screens organized by feature domain
│   ├── canvas/                          # CanvasScreen.kt: interactive sketchpad & scratchpad
│   ├── description/                     # Item details, chapters, franchise & video management
│   │   ├── DescriptionScreen.kt         # Content coordinator & media router
│   │   ├── book/BookDescriptionView.kt  # Dedicated Book/Novel/PDF description view
│   │   ├── music/PlaylistDetailScreen.kt# Music playlist / album description
│   │   ├── toon/                        # ToonDescriptionView.kt, ToonChaptersView.kt
│   │   └── video/                       # SeriesDescriptionView.kt, SeriesRelatedView.kt, ChannelDescriptionView.kt, ChannelVideosView.kt
│   ├── editor/                          # In-app metadata & lyrics editor (EditPlaylistScreen, EditSongScreen)
│   ├── home/                            # Home screen, dashboard, search & mode switcher
│   │   ├── HomeScreen.kt                # Top-level screen host
│   │   ├── HomeDashboard.kt             # Shortcuts, Continue Watching, Recently Added
│   │   ├── HomeNavDock.kt               # Bottom navigation dock
│   │   ├── HomeSearchView.kt            # Advanced multi-tag media search
│   │   ├── LibraryView.kt               # Media library grid & category filters
│   │   ├── MediaQuickActionsSheet.kt    # Bottom action sheet for library items
│   │   ├── importer/ImportStudioSheet.kt# Centralized import studio bottom sheet
│   │   └── music/MusicHomeScreen.kt     # Dedicated music dashboard window
│   ├── intro/IntroScreen.kt             # App intro / onboarding tour
│   ├── lock/LockSelectionScreen.kt      # App lock, vault selection & PIN management
│   ├── player/                          # Immersive media players
│   │   ├── book/                        # BookPlayerScreen.kt (PDF) & WebBookViewer.kt (EPUB/HTML)
│   │   ├── music/MusicPlayerScreen.kt   # Audio playback player, lyrics & queue
│   │   ├── novel/                       # NovelPlayerScreen.kt & NovelReaderSettingsPill.kt
│   │   ├── toon/                        # ToonPlayerScreen.kt & ToonReaderSettingsPill.kt
│   │   └── video/                       # VideoPlayerScreen.kt & VideoPreview.kt
│   ├── settings/SettingsScreen.kt       # Application settings & customization
│   └── workspace/                       # Workspace management UI, move sheets & dialogs
│
└── ui/                                  # Shared presentation infrastructure
    ├── background/                      # 8 ambient Canvas shader backgrounds
    │   ├── ConstellationBackground.kt
    │   ├── CrystalSnowBackground.kt
    │   ├── DrunkStarsBackground.kt
    │   ├── FlowerBackground.kt
    │   ├── JellyfishBackground.kt
    │   ├── KaleidoscopeBackground.kt
    │   ├── LiquidBackground.kt
    │   └── NeonRippleBackground.kt
    ├── components/                      # Atomic, reusable composable widgets (MiniPlayer, JellyBall, WavyMusicSlider, AnimatedEqualizer, etc.)
    ├── dialogs/                         # Modal sheets & pickers (GenreSelectionDialog, PlaylistSelectionDialog, ZineScraperDialog)
    ├── navigation/                      # NavGraph & route coordinators (AppNavGraph, Screen, safe navigation extensions)
    ├── theme/                           # Design tokens, typography, and color schemes
    └── viewmodels/                      # Lifecycle-aware ViewModels holding UI state (DescriptionViewModel, LibraryViewModel, MusicViewModel, PdfViewModel, ReaderViewModel, SettingsViewModel, VideoViewModel)
```

---

## 2. Unidirectional Data Flow (UDF)

All screens in Hwaran adhere strictly to the single-directional state model:

```text
┌────────────────────────────────────────────────────────┐
│                      Composable UI                     │
│         (Stateless Rendering + User Interaction)       │
└────────────▲───────────────────────────────┬───────────┘
             │                               │
       State │ (Immutable StateFlow)  Events │ (Callbacks / User Intent)
             │                               ▼
┌────────────┴───────────────────────────────────────────┐
│                       ViewModel                        │
│          (Exposes StateFlow, Handles UI Events)        │
└────────────▲───────────────────────────────┬───────────┘
             │                               │
       Flows │ (Room / DataStore)   Commands │ (withContext(Dispatchers.IO))
             │                               ▼
┌────────────┴───────────────────────────────────────────┐
│             Repository / Room / Storage Layer          │
│          (Database, SAF, Media Decoders, Importers)    │
└────────────────────────────────────────────────────────┘
```

1. **Events Flow Up**: UI emits user gestures as callbacks (`onClick`, `onValueChange`, `onNavigate`).
2. **State Flows Down**: ViewModels expose immutable `StateFlow<T>` models observed via Compose `collectAsState()`.
3. **No Direct Main-Thread I/O**: Operations touching the disk, Room, SAF, or bitmaps must run within `withContext(Dispatchers.IO)`.

---

## 3. Storage & Vault Architecture

Hwaran supports two distinct storage paradigms:

### A. External Mode (Storage Access Framework / SAF)
- User selects an external directory via `Intent.ACTION_OPEN_DOCUMENT_TREE`.
- Persistable permissions are acquired via `contentResolver.takePersistableUriPermission`.
- Files remain in their original external directory; Hwaran stores the document `treeUri` / file URI and indexes metadata into Room.

### B. Internal Vault Mode (`manga_vault/`)
- Content copied directly into the app's sandboxed `context.filesDir/manga_vault`.
- A `.nomedia` file is maintained at the root of `manga_vault` to prevent Android's MediaStore from indexing private files.
- `VaultMigrationService` runs in the foreground to safely migrate items between external and internal vault storage without risk of data loss.

---

## 4. Audio Playback Architecture

Audio playback uses **AndroidX Media3** with a decoupled service-player architecture:

- **`HwaranPlayerHolder`**: Supplies the singleton `ExoPlayer` instance configured with audio attributes (`USAGE_MEDIA`, `CONTENT_TYPE_MUSIC`) and `handleAudioBecomingNoisy = true`.
- **`MusicNotificationService`**: A `MediaSessionService` that manages the playback notification, media style controls, lockscreen controls, and system media buttons.
- **`MusicViewModel`**: Observes player position, playback state, and current track, maintaining a dedicated `playbackHistory` back-stack for shuffle navigation (allowing `<` Previous navigation without re-shuffling) and exposing reactive flows to the UI.
- **`PopUpPlayerActivity`**: Floating mini player activity enabling playback control outside the main app window.

---

## 5. Novel & WebBook Parsing Pipeline

```text
[File / Uri] ──► [NovelParser / WebBookViewer] ──► [Structured NovelBook / HTML DOM]
                                                              │
                    ┌─────────────────────────────────────────┴─────────────────────────────────────────┐
                    ▼                                                                                   ▼
    [Vertical LazyColumn Paragraphs]                                                    [Continuous Scaled WebView]
                    │                                                                                   │
    [Checkpoint Offset Tracking & DB]                                                   [0..10000 Progress Normalization]
```

- **NovelParser**: Extracts chapter boundaries using regex heuristics across raw text (`.txt`, `.md`) files, building indexed chapter hierarchies with word counts.
- **WebBookViewer**: Prepares and renders styled, sanitized HTML/EPUB documents with dynamic eye-care CSS injection and normalized `0..10000` scroll progress tracking.
- **Checkpoints**: Stored in the `pdf_marker` table with page/chapter index and paragraph scroll offsets (`x1`), enabling exact resumption and real-time completion percentage badges.

---

## 6. External Intent Routing Architecture

Hwaran acts as a default viewer for external media intents via `MainActivity.kt` and `NavGraph.kt`:

- `Screen.ExternalPdf`: Direct hardware-accelerated PDF viewing.
- `Screen.ExternalVideo`: Direct ExoPlayer playback with gestures.
- `Screen.ExternalNovel`: Direct text/novel reading with custom typography and checkpointing.
- `Screen.ExternalImage`: Direct image folder viewing.
