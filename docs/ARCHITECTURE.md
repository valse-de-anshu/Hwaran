# Architecture Blueprint

> **"How is Hwaran structured and built?"**

Hwaran follows a **Feature-based Clean Architecture** coupled with **Unidirectional Data Flow (UDF)**. Heavy I/O, database queries, file scanning, and media decoding are kept strictly off the main thread using Kotlin Coroutines and Dispatchers.

---

## 1. High-Level Package Structure

```text
com.ballade.hwaran/
│
├── audio/                               # AndroidX Media3 Audio Architecture
│   ├── HwaranPlayerHolder.kt            # Singleton ExoPlayer provider
│   └── MusicNotificationService.kt      # Foreground MediaSessionService with notification provider
│
├── backend/                             # Feature-isolated data & business operations
│   ├── book/                            # BookBackend: PDF & document processing
│   ├── history/                         # HistoryBackend: event logging & queries
│   ├── music/                           # MusicBackend: audio imports & metadata
│   ├── novel/                           # NovelBackend, NovelParser & Multi-format loaders
│   ├── toon/                            # ToonBackend: manga/manhua chapter ingestion
│   ├── video/                           # Video business logic
│   │   ├── channel/                     # VideoChannelBackend: channel & video clips
│   │   └── series/                      # VideoSeriesBackend: multi-season series & franchise links
│   └── workspace/                       # WorkspaceBackend: workspace creation & moves
│
├── core/                                # Shared cross-cutting infrastructure
│   ├── database/                        # Room database & persistence
│   │   ├── AppDatabase.kt               # Database singleton (version 15)
│   │   ├── dao/                         # 4 focused DAOs + composite shim
│   │   │   ├── AnnotationDao.kt         # pdf_marker operations
│   │   │   ├── HistoryDao.kt            # history_event operations
│   │   │   ├── LibraryDao.kt            # Composite interface for backward compatibility
│   │   │   ├── MediaDao.kt              # manga table operations
│   │   │   └── TrackDao.kt              # chapter table operations
│   │   └── entity/                      # Room entities (MangaEntity, ChapterEntity, etc.)
│   │       └── Entities.kt
│   ├── datastore/                       # App settings & preferences via DataStore
│   │   └── GlobalSettings.kt            # Reactive flows for UI, audio, and vault settings
│   └── util/                            # Utilities (HistoryTracker, CoverArtResolver, SAF utils)
│
├── data/                                # Data ingestion and repositories
│   ├── importer/                        # Centralized media import pipeline
│   │   ├── book/                        # PDF and document single/mega importers
│   │   ├── music/                       # Audio album/track single/mega importers
│   │   ├── toon/                        # Manga/manhua chapter single/mega importers
│   │   └── video/                       # Video and structured series importers
│   └── repository/                      # Domain repositories
│       └── LibraryRepository.kt         # SAF scanning, streaming, and metadata extraction
│
├── frontend/                            # Presentation screens organized by feature domain
│   ├── canvas/                          # CanvasScreen.kt: interactive sketchpad & scratchpad
│   ├── description/                     # Item details, chapters, franchise & video management
│   │   ├── DescriptionScreen.kt         # Content coordinator & media router
│   │   ├── book/BookDescriptionView.kt  # Dedicated Book/Novel/PDF description view
│   │   ├── music/PlaylistDetailScreen.kt# Music playlist / album description
│   │   ├── toon/ToonDescriptionView.kt  # Manga/Manhua description view & chapter tree
│   │   └── video/                       # SeriesDescriptionView.kt, ChannelDescriptionView.kt
│   ├── editor/                          # In-app metadata & lyrics editor
│   ├── history/                         # History & analytics timeline
│   ├── home/                            # Home screen, dashboard, search & mode switcher
│   ├── intro/IntroScreen.kt             # App intro / onboarding
│   ├── lock/LockSelectionScreen.kt      # App lock & PIN selection
│   ├── player/                          # Immersive media players
│   │   ├── book/                        # BookPlayerScreen.kt & WebBookViewer.kt (EPUB/HTML)
│   │   ├── music/MusicPlayerScreen.kt   # Audio playback player, lyrics & queue
│   │   ├── novel/                       # NovelPlayerScreen.kt & NovelReaderSettingsPill.kt
│   │   ├── toon/ToonPlayerScreen.kt     # Webtoon continuous scroll & paged comic reader
│   │   └── video/VideoPlayerScreen.kt   # ExoPlayer video player with gestures
│   ├── settings/SettingsScreen.kt       # Application settings & customization
│   └── workspace/                       # Workspace management UI & dialogs
│
└── ui/                                  # Shared presentation infrastructure
    ├── background/                      # 7 ambient Canvas shader backgrounds
    ├── components/                      # Atomic, reusable composable widgets
    ├── dialogs/                         # Modal sheets & pickers
    ├── navigation/                      # NavGraph & route coordinators
    ├── theme/                           # Design tokens, typography, and color schemes
    └── viewmodels/                      # Lifecycle-aware ViewModels holding UI state
```

---

## 2. Unidirectional Data Flow (UDF)

All screens in Hwaran adhere to the single-directional state model:

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
2. **State Flows Down**: ViewModels expose immutable `StateFlow<T>` models observed via Compose `collectAsState()` or `collectAsStateWithLifecycle()`.
3. **No Direct Main-Thread I/O**: Operations touching the disk, Room, SAF, or bitmaps must run within `withContext(Dispatchers.IO)`.

---

## 3. Storage & Vault Architecture

Hwaran supports two distinct storage paradigms:

### A. External Mode (Storage Access Framework / SAF)
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
- **`MusicViewModel`**: Observes player position, playback state, and current item, maintaining a playback history back-stack for shuffle navigation and exposing reactive flows to the UI.

---

## 5. Novel & WebBook Parsing Pipeline

```text
[File / Uri] ──► [NovelParser / WebBookViewer] ──► [Structured NovelBook / HTML DOM]
                                                              │
                    ┌─────────────────────────────────────────┴─────────────────────────────────────────┐
                    ▼                                                                                   ▼
    [Vertical LazyColumn Paragraphs]                                                    [Paginated Chapter Pager]
                    │                                                                                   │
    [Checkpoint Offset Tracking & DB]                                                   [Page State Persistence]
```

- **NovelParser**: Extracts chapter boundaries using regex heuristics across raw text and markdown files.
- **WebBookViewer**: Prepares and renders styled, sanitized HTML documents with dynamic eye-care CSS injection and scroll progress tracking.
- **Checkpoints**: Stored in `pdf_marker` table with page/chapter index and paragraph scroll offsets, enabling exact resumption.
