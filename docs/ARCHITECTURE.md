# Architecture Blueprint

> **"How the hell is Hwaran built?"**

Hwaran follows a **feature-based clean architecture** with **Unidirectional Data Flow (UDF)**. Heavy I/O, database queries, and media decoding are kept strictly off the main thread.

---

## 1. High-Level Package Architecture

```text
com.ballade.hwaran/
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
└── ui/                                  # Presentation layer (Jetpack Compose)
    ├── theme/                           # Design tokens, typography, colors, theme
    ├── background/                      # 7 ambient Canvas shader backgrounds
    ├── components/                      # Pure, reusable atomic widgets
    ├── dialogs/                         # Modal sheets, onboarding, picker dialogs
    ├── navigation/                      # NavGraph & route coordinators
    ├── screens/                         # Feature screens grouped by media domain
    │   ├── home/                        # Library grid, workspace filters, mode switcher
    │   ├── description/                 # Item details, chapter list, child boxes
    │   ├── music/                       # Music player, playlist details, tag editors
    │   ├── reader/                      # Webtoon continuous reader & PDF reader
    │   ├── video/                       # Video player with gestures & preview
    │   ├── history/                     # History feed & domain-specific cards
    │   └── workspace/                   # Workspace management & move dialogs
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
