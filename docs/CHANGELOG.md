# Changelog

> **"What changed in Hwaran?"**

All notable changes, architectural milestones, and structural refactors to this project are documented here.

---

## [Current] - 2026-09-12

### 📖 Premium Manga & Manhua Reading Suite
- **Lag-Free Reader Transition**:
  - Replaced the janky 1200ms crossfade and stuttering circular spinner with a silky-smooth, lightweight reader transition (220ms).
  - Elegant loading layout with glowing book icon, series title, chapter title, and thin indeterminate accent bar that never drops frames.
- **Dual Reading Layout Engines**:
  - **Single Long Strip (Webtoon)**: Continuous 120Hz native vertical scrolling without gesture interception or manual scroll delta conflicts.
  - **Page by Page (Single Page)**: Paged reading mode with smooth horizontal pagination (`HorizontalPager`).
  - **Reading Direction Toggle**: Full support for Left-to-Right (Western comics / webtoons) and Right-to-Left (Japanese Manga) page ordering.
  - **Edge-Tap Navigation**: Left 28% and Right 72% tap zones for instant page turning with subtle haptic feedback, while center tap toggles controls.
- **Margin Cropping & Zoom Architecture**:
  - **Crop Side Margins / Horizontal Scale Slider**: Eliminates annoying white gutters/letterboxing in manhua by expanding width from 100% to 150% with clean edge clipping.
  - Native 120fps scrolling physics are 100% preserved even when zoomed/cropped—completely fixing the "stuck in glue" resistance issue.
  - Quick preset chips: `Fit (100%)`, `Crop 15%`, `Crop 30%`, and one-tap Reset.
- **Pristine Distraction-Free Canvas & Top-Right Trigger**:
  - Reader screen is 100% clean by default with zero visible overlay buttons obstructing reading.
  - Tapping the dedicated top-right corner zone smoothly toggles the controls overlay on demand.
- **Revived Classic Bottom Navigation Pill**:
  - Restored the beloved classic bottom pill with `< Prev Chapter`, centered purple `Chapter Title` (tappable to open chapter picker), and `Next Chapter >`.
  - Restored the separate circular `^` Scroll to Top button with instant snap to Page 1.
- **Camera Cutout & Notch Safe Insets**:
  - Applied `displayCutoutPadding()` and extended `statusBarsPadding()` with safe vertical spacing, moving the top Back and Settings buttons completely out of the camera punch hole danger zone.
- **Rapid Scroll Stability Fix**:
  - Fixed rapid scrolling stutter/freeze by eliminating per-item crossfade animations, providing stable minimum item dimensions to prevent LazyColumn measurement jumps, and throttling preloads during scroll motion.
- **Sleek Reader Settings Sheet (`ToonReaderSettingsSheet`)**:
  - Glassmorphic modal sheet with reading layout toggle, reading direction selector, margin cropping slider, 4 canvas background tones (Theme, OLED Black, Deep Slate, Pure White), screen brightness override slider, and keep screen awake toggle.
  - Quick page scrubbing slider with live page preview.
  - All reader preferences persisted in `GlobalSettings` via DataStore.

### 🔍 Advanced Search Engine
- **Media Format Filtering**: Instant filter chips for all media classes (`All`, `Book`, `Manhua`, `Manga`, `Series`, `Channel`, `Favorite`) with thematic color accents and icons.
- **Search Scope Selector**: Switch query matching between `All Fields`, `Name / Title`, and `Tags / Genre`.
- **Dynamic Multi-Tag Filter**:
  - Automatically extracts all unique tags from library items.
  - Multi-selection chips with quick-removal (✕) badges and a live tag suggestion picker with search filtering.
  - Match mode toggle between `Match Any (OR)` and `Match All (AND)`.
- **Sorting Engine**: Sort live query results by `Newest Added`, `Title (A-Z)`, `Title (Z-A)`, and `Recently Read`.
- **Media Result Cards**: Redesigned cards with top-left format pills (`Book`, `Manhua`, `Manga`, `Series`, `Channel`), favorite status badges, reliable cover art loading via `CoverArtResolver`, and direct navigation to domain-specific descriptions.

### 🎨 Description Suite & Media Domain Isolation
- **Domain-Isolated Description Views**:
  - `frontend/description/book/BookDescriptionView.kt`: Dedicated view for Books, Novels, and PDFs with single-volume vs multi-chapter awareness, "Read / Resume (p. X)" action, and book-specific metadata (Author, Publisher, Published Year, Language, Status, Pages).
  - `frontend/description/toon/ToonDescriptionView.kt` & `ToonChaptersView.kt`: Dedicated view for Manga & Manhua stripped of book branching, focused on chapters, chapter deletion/import, and manga metadata (Author, Artist, Publisher, Serialization, Status, Rating).
  - `frontend/description/video/SeriesDescriptionView.kt`, `SeriesRelatedView.kt`, `SeriesRelationType.kt`: Dedicated Series view with anime/show franchise linking across 9 relationship types (Season, Movie, OVA, ONA, Special, Blu-ray, Prequel, Sequel, Spinoff / Alternate Version), tab filtering, and secondary video repository.
  - `frontend/description/video/ChannelDescriptionView.kt` & `ChannelVideosView.kt`: Dedicated Channel view with video playlist selector, custom thumbnails, file picker, and sorting controls.
  - `frontend/description/DescriptionScreen.kt`: Refactored into a lightweight coordinator/router that cleanly routes by `contentType` and `boxPurpose`.
- **Master Tag System**:
  - Integrated 4,000+ master tags dataset (`tags/master_tags.json`) with instant search, auto-suggestions, and one-tap ✕ chip removal.
  - Dynamic material format pills (`Book`, `Manhua`, `Manga`, `Series`, `Channel`) with seamless editing and persistence.

### 🏠 Home Screen & Library Polish
- **Theme-Agnostic Alpha Fading Mask**:
  - Removed hardcoded dark slate gradient box (`Color(0xFF0F0E17)`) from `LibraryView`.
  - Applied compositing alpha mask (`CompositingStrategy.Offscreen` + `BlendMode.DstIn`) directly to the grid, allowing media cards to dissolve smoothly under pills while preserving 100% of user color themes (Grape, Blueberry, Snowfall, PureDark) and dynamic animated canvas backgrounds (Celestial, Drunk Stars, Jellyfish, Liquid).
- **Auto-Scroll & Focus on Library Tag Pills**:
  - Attached `rememberLazyListState()` to `LazyRow` with `LaunchedEffect(selectedTag)`.
  - Home dashboard media shortcut pills (`Manhua`, `Manga`, `Series`, `Book`, `Channel`, `Favorite`) now directly open the Library and automatically scroll the pill row to center and focus the selected category.
  - Reset `libraryInitialTag = "All"` on back navigation from Library to ensure reliable re-triggering.
- **Home Dashboard Enhancements**:
  - Interactive headers: `Continue Watching >` opens History; `Recently Added >` opens full interactive `RecentlyAddedSheet` bottom sheet.
  - Real progress calculation: replaced hardcoded values with actual chapter count, duration, and read page tracking.
  - Removed redundant top search icon in favor of the bottom navigation dock search.

### 🧹 Codebase Cleanup
- Pruned empty ghost directories: `frontend/home/{book, toon, video}` and `frontend/editor/{book, toon, video}`.

---

## [2026-09-08] - Major Architecture & Package Restructure
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

### ⚡ Frontend / Backend Module Split & Screen Retirement
- **Backend Modules**: Established `backend/{toon, book, video/series, video/channel, music, history, workspace}` as the isolated business and data orchestration layer.
- **Frontend Architecture**: Established `frontend/{player, editor, home, description, history, workspace, intro, lock, settings}` as the presentation screen layer.
- **NavGraph Overhaul**: Rewired all navigation routes directly to canonical `frontend/` screens.
- **ui/screens/ Elimination**: Completely deleted obsolete, duplicate `ui/screens/` files and directories.

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
