# Architecture Blueprint

> **"How the hell is Hwaran built?"**

Hwaran follows a **feature-based clean architecture** with **Unidirectional Data Flow (UDF)**. Heavy I/O, database queries, and media decoding are kept strictly off the main thread.

---

## 1. High-Level Package Architecture

```text
com.ballade.hwaran/
│
├── backend/                             # Feature-isolated business & data operations
│   ├── toon/                            # ToonBackend: manga/manhua import & data orchestration
│   ├── book/                            # BookBackend: PDF import, file operations & data
│   ├── video/                           # Video business logic
│   │   ├── series/                      # VideoSeriesBackend: series & mega-import logic
│   │   └── channel/                     # VideoChannelBackend: channel & video management
│   ├── music/                           # MusicBackend: audio imports & playlist management
│   ├── history/                         # HistoryBackend: event logging & queries
│   └── workspace/                       # WorkspaceBackend: workspace creation, moves & renames
│
├── frontend/                            # Presentation screens organized by feature domain
│   ├── home/                            # Home screen & domain home tabs
│   │   ├── HomeScreen.kt                # Coordinator hosting bottom nav & mode switcher
│   │   └── music/MusicHomeScreen.kt     # Dedicated music home screen
│   ├── description/                     # Item details, chapters, franchise & video management
│   │   ├── DescriptionScreen.kt         # Content coordinator & media router
│   │   ├── book/BookDescriptionView.kt  # Dedicated Book/Novel/PDF description view
│   │   ├── toon/                        # ToonDescriptionView.kt, ToonChaptersView.kt (Manga & Manhua)
│   │   ├── video/                       # SeriesDescriptionView.kt, SeriesRelatedView.kt, ChannelDescriptionView.kt, ChannelVideosView.kt
│   │   └── music/PlaylistDetailScreen.kt# Music playlist / album description
│   ├── player/                          # Media players
│   │   ├── toon/ToonPlayerScreen.kt     # Webtoon continuous scroll & zoom reader
│   │   ├── book/BookPlayerScreen.kt     # PDF multi-page reader & link extractor
│   │   ├── video/VideoPlayerScreen.kt   # ExoPlayer video player with gestures & preview
│   │   └── music/MusicPlayerScreen.kt   # Audio playback player & queue
│   ├── editor/                          # Metadata & content editing
│   │   └── music/                       # EditSongScreen.kt, EditPlaylistScreen.kt
│   ├── history/                         # History & analytics
│   │   ├── HistoryScreen.kt             # Main history screen
│   │   ├── cards/                       # Domain-specific timeline cards
│   │   ├── trackers/                    # Reading/listening progress trackers
│   │   ├── models/                      # Timeline models & metadata parsing
│   │   └── neverwatched/                # Unplayed/unwatched backlog views
│   ├── workspace/                       # Workspace management UI
│   │   ├── dialogs/                     # Create, delete, rename dialogs
│   │   └── move/                        # Move entire workspace & items dialogs
│   ├── intro/IntroScreen.kt             # App intro / onboarding
│   ├── lock/LockSelectionScreen.kt      # App lock & PIN selection
│   └── settings/SettingsScreen.kt       # Application settings
│
├── core/                                # Shared cross-cutting infrastructure
│   ├── database/                        # Room database & data persistence
│   │   ├── AppDatabase.kt               # Database singleton (version 15)
│   │   ├── dao/                         # 4 focused DAOs + composite shim
│   │   │   ├── MediaDao.kt              # manga table operations
│   │   │   ├── TrackDao.kt              # chapter table operations
│   │   │   ├── HistoryDao.kt            # history_event operations
│   │   │   ├── AnnotationDao.kt         # pdf_marker operations
│   │   │   └── LibraryDao.kt            # Composite interface for backward compatibility
│   │   └── entity/                      # Room entities (MangaEntity, ChapterEntity, etc.)
│   │       └── Entities.kt
│   │
│   ├── datastore/                       # App settings & preferences via DataStore
│   │   └── GlobalSettings.kt            # Reactive flows for UI, audio, and vault settings
│   │
│   └── util/                            # Cross-cutting utilities
│       └── HistoryTracker.kt            # Lightweight asynchronous history logger
│
├── data/                                # Data ingestion and repositories
│   ├── repository/                      # Domain repositories
│   │   └── LibraryRepository.kt         # SAF scanning, streaming, and metadata extraction
│   │
│   └── importer/                        # Centralized media import pipeline
│       ├── book/                        # PDF and document single/mega importers
│       ├── music/                       # Audio album/track single/mega importers
│       ├── toon/                        # Manga/manhua chapter single/mega importers
│       └── video/                       # Video and structured series importers
│
├── audio/                               # Media3 / ExoPlayer audio engine
│   ├── HwaranPlayerHolder.kt            # Singleton player provider
│   └── MusicNotificationService.kt      # Foreground MediaSessionService
│
└── ui/                                  # Shared presentation infrastructure
    ├── theme/                           # Design tokens, typography, colors, theme
    ├── background/                      # 7 ambient Canvas shader backgrounds
    ├── components/                      # Pure, reusable atomic widgets
    ├── dialogs/                         # Modal sheets, onboarding, picker dialogs
    ├── navigation/                      # NavGraph & route coordinators
    └── viewmodels/                      # Lifecycle-aware ViewModels holding UI state
```

---

## 2. Unidirectional Data Flow (UDF)

All screens in Hwaran adhere to the single-directional state model:

```text
┌────────────────────────────────────────────────────────┐
│                      Composable UI                     │
└────────────▲───────────────────────────────┬───────────┘
             │                               │
       State │ (StateFlow)            Events │ (User Actions)
             │                               ▼
┌────────────┴───────────────────────────────────────────┐
│                       ViewModel                        │
└────────────▲───────────────────────────────┬───────────┘
             │                               │
       Flows │ (Room / DataStore)   Commands │ (Suspend / I/O)
             │                               ▼
┌────────────┴───────────────────────────────────────────┐
│             Repository / Room / Storage Layer          │
└────────────────────────────────────────────────────────┘
```

1. **Events Flow Up**: UI emits user gestures as callbacks (`onClick`, `onValueChange`).
2. **State Flows Down**: ViewModels expose immutable `StateFlow<T>` models observed via Compose `collectAsStateWithLifecycle()` or `collectAsState()`.
3. **No Direct Main-Thread I/O**: Operations touching the disk, Room, SAF, or bitmaps must run within `withContext(Dispatchers.IO)`.

---

## 3. Storage & Vault Architecture

Hwaran supports two distinct storage paradigms:

### A. External Mode (SAF / Scoped Storage)
- User selects an external directory via `Intent.ACTION_OPEN_DOCUMENT_TREE`.
- Persistable permissions are acquired via `contentResolver.takePersistableUriPermission`.
- Files remain in their original external directory; Hwaran only stores the document `treeUri` / file URI and indexes metadata into Room.

### B. Internal Vault Mode (`manga_vault/`)
- Content copied directly into the app's sandboxed `context.filesDir/manga_vault`.
- A `.nomedia` file is maintained at the root of `manga_vault` to prevent Android's MediaStore from indexing private files.
- Ideal for sensitive content and fast local read access without SAF IPC latency.

---

## 4. Audio Playback Architecture

Audio playback uses **AndroidX Media3** with a decoupled service-player architecture:

- **`HwaranPlayerHolder`**: Supplies the singleton `ExoPlayer` instance configured with audio attributes (`USAGE_MEDIA`, `CONTENT_TYPE_MUSIC`) and `handleAudioBecomingNoisy = true`.
- **`MusicNotificationService`**: A `MediaSessionService` that manages the playback notification, media style controls, lockscreen controls, and system media buttons.
- **`MusicViewModel`**: Observes player position, playback state, and current item, exposing reactive flows to `MusicScreen` and `NowPlayingScreen`.

---

## 5. Concurrency Rules

- **UI Thread**: Exclusively for layout, drawing, and UI event dispatching.
- **`Dispatchers.IO`**: Mandatory for:
  - All Room database inserts, updates, deletes, and list queries.
  - SAF tree traversals (`DocumentFile.listFiles()`).
  - Bitmaps decoding (`BitmapFactory.decodeStream`), cropping, and compression.
  - Audio and video metadata extraction (`MediaMetadataRetriever`).
