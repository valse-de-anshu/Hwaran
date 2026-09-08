# Changelog

> **"What changed in Hwaran?"**

All notable changes, architectural milestones, and structural refactors to this project are documented here.

---

## [Unreleased / Current] - 2026-09-08

### 🏗️ Major Architecture & Package Restructure
- **Feature-Driven Architecture**: Transitioned the entire flat project structure into an organized, domain-isolated directory layout.
- **DAO Decomposition**:
  - Split the monolithic `LibraryDao` into 4 dedicated, focused DAOs:
    - `MediaDao`: Operations on the `manga` table.
    - `TrackDao`: Operations on the `chapter` table.
    - `HistoryDao`: Ingestion and reading feeds on the `history_event` table.
    - `AnnotationDao`: Marker and highlight operations on `pdf_marker`.
  - Added a composite `LibraryDao` interface combining all 4 for backward compatibility.
- **Import Pipeline Consolidation**:
  - Moved all 26 scattered import files from `data/book`, `data/music`, `data/toon`, `data/video` into `data/importer/{book, music, toon, video}/`.
- **UI Modularization**:
  - Extracted all 7 dynamic canvas shaders (`Celestial`, `DrunkStars`, `Flower`, `Jellyfish`, `Kaleidoscope`, `Liquid`, `Poker`) to `ui/background/`.
  - Extracted modal overlays and dialogs (`GenreSelectionDialog`, `PlaylistSelectionDialog`, `OnboardingOverlay`, `PremiumComponents`) to `ui/dialogs/`.
  - Reorganized screens into domain-specific packages:
    - `ui/screens/music/` (`MusicScreen`, `NowPlayingScreen`, `PlaylistDetailScreen`, `EditPlaylistScreen`, `EditSongScreen`)
    - `ui/screens/reader/` (`ReaderScreen`, `PdfReaderScreen`, `PdfLinkExtractor`)
    - `ui/screens/video/` (`VideoPlayerScreen`, `VideoPreview`)
    - `ui/screens/history/` (`HistoryScreen` + `cards/`, `trackers/`, `models/`)
- **Code Health & Bug Fixes**:
  - Fixed orphaned syntax block in `SettingsViewModel`.
  - Restored missing workspace manipulation methods in `LibraryViewModel` (`getAllDistinctWorkspaces`, `moveEntireWorkspace`, `moveItemsToDifferentWorkspace`, `renameWorkspace`).
  - Added `renameWorkspaceIfActive` method in `GlobalSettings`.
  - Made mode indicators in `MediaModeIndicator` public for workspace dialog access.
  - Added companion playback flags and `stopPlayback()` in `MusicViewModel` for `PopUpPlayerActivity`.
  - Cleaned up temp scratch files (`patch.kt`, `test_*.kt`, `.orig` files) and updated `.gitignore`.

---

## [Database Schema v15] - 2026-06-15
- Added `openCount` to `manga` and `chapter` entities for access frequency tracking.
- Maintained migration path from v4 through v15.

## [Database Schema v14] - 2026-06-14
- Added `pdf_marker` table for PDF reading annotations and highlights.

## [Database Schema v13] - 2026-06-14
- Added `history_event` table for unified cross-media playback and reading history logging.

## [Database Schema v12] - 2026-06-13
- Added `workspace` column to `manga` table to support user-customizable library organization.

## [Initial Architecture]
- Initial creation of Hwaran with unified Manga, Book, Video, and Music playback on Jetpack Compose and Room.
