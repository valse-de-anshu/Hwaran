# Hwaran Project Overview

## Description
**Hwaran** is a multi-functional Android application designed for organizing and viewing media collections, specifically tailored for "Manga" and similar content. It provides a comprehensive suite of tools for reading, watching, and listening to media, with a focus on privacy and organization.

## Key Features
- **Manga/Book Organization:** Manage collections with metadata like titles, descriptions, and custom thoughts.
- **Media Playback:** 
    - Integrated **Video Player** (using Media3/ExoPlayer).
    - **Music/Audio Player** with "Now Playing" functionality.
- **Reading Capabilities:**
    - **PDF Reader** for document viewing.
    - Generic **Reader** for images/folders (often used for manga chapters).
- **Privacy & Security:**
    - **Locking System:** Ability to lock specific entries or "boxes".
- **Customization:**
    - **Dynamic Backgrounds:** Includes specialized background animations like "Drunk Stars" and "Jellyfish".
    - **Theming:** Jetpack Compose based Material 3 theming.
- **Data Management:**
    - **Room Database:** Persistent storage for library metadata and chapters.
    - **DataStore:** Preference-based settings management.

## Tech Stack
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Asynchronous Programming:** Kotlin Coroutines & Flow
- **Dependency Injection/Management:** KSP (Kotlin Symbol Processing)
- **Database:** Room Persistence Library
- **Image Loading:** Coil (with Video support)
- **Navigation:** Jetpack Compose Navigation
- **Media Playback:** Media3 (ExoPlayer & UI)
- **Storage:** Android DataStore Preferences, DocumentFile API

## Project Structure
- `app/src/main/java/com/ballade/hwaran/`
    - `data/`: Data layer containing local database (`Room`) and repositories.
    - `ui/`: UI layer following a modern Compose architecture.
        - `components/`: Reusable Compose UI elements.
        - `navigation/`: Navigation graphs and routes.
        - `screens/`: Individual screen implementations (Home, Player, Reader, etc.).
        - `theme/`: Material 3 theme definitions.
        - `viewmodels/`: ViewModel implementations for state management.
    - `MainActivity.kt`: Main entry point with navigation hosting.
    - `SplashActivity.kt`: Entry splash screen.

## Deep Technical Architecture & App Flow

### 1. Architecture Pattern (MVVM)
The application strictly follows the Model-View-ViewModel (MVVM) architecture combined with Unidirectional Data Flow (UDF) tailored for Jetpack Compose.
- **Model (Data Layer)**: Comprises Room Database (`AppDatabase`), `LibraryRepository`, and Android `DataStore` (`GlobalSettings`). This layer acts as the single source of truth.
- **ViewModel**: State holders like `LibraryViewModel`, `MusicViewModel`, `SettingsViewModel`, `ReaderViewModel`, and `DescriptionViewModel`. They expose UI state via `StateFlow` and handle business logic via Kotlin Coroutines.
- **View (UI Layer)**: Entirely built with Jetpack Compose. Observes `StateFlow` from ViewModels and emits UI events back.

### 2. Data Layer Breakdown
#### Room Database (`AppDatabase.kt` & `Entities.kt`)
The app uses Room with two primary entities that are highly versatile:
- **`MangaEntity` (Table: `manga`)**: Acts as a container/folder. Despite the name, it's used for Manga, Books, Audio Playlists, and general media collections. Key fields: `title`, `coverPath` (URI string), `contentType` (determines if it's a book, playlist, etc.), `isNsfw`, `isLocked`, and `lastReadTitle`/`lastReadPage` for state persistence.
- **`ChapterEntity` (Table: `chapter`)**: Represents individual playable or readable content inside a `MangaEntity`. Used for Manga chapters, individual songs, or video files. Key fields: `mangaId` (Foreign Key equivalent), `folderUri` (path to content), `artist`, `duration`, and `lyrics` (for audio).
*Note*: The database uses `fallbackToDestructiveMigration(true)` but also contains explicit migrations (`MIGRATION_4_5` to `MIGRATION_9_10`) indicating schema evolution for features like locking, position tracking, and audio metadata.

#### Repository (`LibraryRepository.kt`)
Centralizes data access. It mediates between the Room DAOs and the ViewModels. It's likely responsible for parsing `DocumentFile` URIs, extracting metadata, and managing the local cache.

#### Preferences (`GlobalSettings.kt`)
Uses `androidx.datastore.preferences` to store lightweight, globally required states such as theme preferences, background animation toggles, scroll offsets, and active tabs.

### 3. UI Layer & State Management
#### Main Entry Point (`MainActivity.kt`)
- **Edge-to-Edge & Immersive UI**: Hides system bars using `WindowInsetsControllerCompat` to achieve a premium, immersive look.
- **Global Background Layer**: Employs a complex background system. It uses `AnimatedContent` for smooth theme gradient transitions.
- **Dynamic Background Animations**: Features highly interactive, hardware-accelerated Compose animations:
    - `DrunkStars`, `Jellyfish`, `Celestial`, `Poker`, `Kaleidoscope`, `Flower`, and `Liquid`.
    - The `LiquidBackground` specifically tracks global touch events via a custom `pointerInput` on the root layout. It creates a `LiquidPointerState` mimicking a mouse, registering 'Hover' (sliding) and 'Click' (explosion) behaviors globally.
- **MiniPlayer**: A persistent UI component overlaid on top of the navigation layer. Its visibility is state-driven (hidden on certain screens like `NowPlaying`, `Settings`, or `EditSong`).
- **Intent Handling**: Resolves external `ACTION_VIEW` intents, directly routing to `ExternalVideo`, `ExternalPdf`, or `ExternalImage` screens based on the MIME type.

#### ViewModels
- **`SettingsViewModel`**: Manages global UI states, active tabs, theme toggles, and animation parameters (`animationType`, `animationSpeed`, `animationVisibility`). Emits `isReady` state to hold the splash screen until data is loaded.
- **`MusicViewModel`**: Handles the Media3/ExoPlayer instance for audio playback. Manages current playing `ChapterEntity`, playlist queues, play/pause states, and updates the `MiniPlayer` and `NowPlayingScreen`.
- **`LibraryViewModel`**: Manages the main collections logic, querying `LibraryRepository`, handling NSFW filters, locks, and sorting.
- **`ReaderViewModel` & `DescriptionViewModel`**: Handle specific screen states like parsing PDF files or loading chapter lists for a specific Manga/Playlist.

### 4. Navigation Flow (`AppNavGraph.kt`)
Uses Jetpack Compose Navigation. Routes are defined in a sealed class/object structure (e.g., `Screen.Home.route`). It supports complex deep links and argument passing (like passing `mangaId` to the `DescriptionScreen`).

### 5. Media & Playback Implementation
- **Audio**: Powered by Media3. The app implements background audio capabilities. `ChapterEntity` serves as the audio track model. The `MusicScreen` and `NowPlayingScreen` react to the player's state.
- **Video**: `VideoPlayerScreen` directly integrates an ExoPlayer instance with custom Compose overlays for playback controls.
- **Images/PDF**: Uses Coil for image loading with video frame support. PDF rendering likely relies on a custom Compose wrapper or native PDF renderer.

### 6. Security & Privacy
- **NSFW Tagging**: Managed per `MangaEntity`.
- **Locking System**: Entries can have `isLocked = true`. The UI requires a passkey or biometric prompt (via `LockSelectionScreen`) to reveal locked content.

## Build Information
- **Build System:** Gradle (Kotlin DSL)
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 37 (Android 15+)
- **Gradle Version:** 9.2.1 (AGP) / 9.4.1 (Wrapper)
- **Java Version:** 11
- q
## For Gradle Sync (Verification):
- 1 export JAVA_HOME=/opt/android-studio/jbr && ./gradlew help
## For Building the App:
- 1 export JAVA_HOME=/opt/android-studio/jbr && ./gradlew assembleDebug 
## For Running Unit Tests (Optional Verification):
- 1 export JAVA_HOME=/opt/android-studio/jbr && ./gradlew
testDebugUnitTest
