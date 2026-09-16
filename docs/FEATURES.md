# Features Specification & Capabilities

> **"What is Hwaran capable of?"**

Hwaran is an **all-in-one private offline media vault & experience engine** built to organize, manage, and consume local Manga/Manhua, Books/PDFs, Novels/E-Books, Video/Anime, and Music in a unified, fluid, and privacy-first interface.

---

## 1. The Five Specialized Media Engines

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                   HWARAN MEDIA SUITE                                   │
├────────────────────┬────────────────────┬────────────────────┬─────────────────────────┤
│ 📖 MANGA & TOONS   │ 📚 BOOKS & NOVELS  │ 🎬 ANIME & VIDEO   │ 🎵 MUSIC & SHADERS      │
├────────────────────┼────────────────────┼────────────────────┼─────────────────────────┤
│ • Continuous Strip │ • Hardware PDF     │ • ExoPlayer Engine │ • Media3 Service        │
│ • Paged LTR / RTL  │ • WebBook Engine   │ • 11 Relations     │ • 8 Canvas Shaders      │
│ • Margin Crop Zoom │ • Pastel Wash Pen  │ • Series & Channel │ • Synced LRC Lyrics     │
│ • 5,700+ Master DB │ • External Sticky  │ • Gesture Controls │ • Shuffle Back-Stack    │
│ • Chapter Sorter   │ • Checkpoints & %  │ • PIP / Background │ • PopUp Mini Player     │
└────────────────────┴────────────────────┴────────────────────┴─────────────────────────┘
```

---

### 📖 1. Manga & Manhua Reading Suite (`contentType = 0`)
- **Dual Reading Layout Engines**:
  - **Single Long Strip (Webtoon)**: High-performance continuous vertical scrolling (120Hz native) without gesture conflicts or stuttering.
  - **Page-by-Page (Paged Comic)**: Smooth horizontal pagination (`HorizontalPager`) supporting both **Japanese Manga (Right-to-Left)** and **Western Comics / Manhwa (Left-to-Right)** reading directions.
- **Side Margin Cropping & Horizontal Zoom**:
  - Eliminates unwanted white gutters/letterboxing in manhua by expanding width from 100% to 150% with clean edge clipping.
  - Preserves 100% native scrolling physics with zero resistance ("stuck in glue" bug eliminated).
  - Quick preset chips: `Fit (100%)`, `Crop 15%`, `Crop 30%`, and one-tap Reset.
- **Pristine Distraction-Free Canvas & Top-Right Trigger**:
  - Reader screen is 100% distraction-free by default with zero overlay clutter.
  - Generous top-right thumb tap trigger zone (160dp x 140dp) for toggling overlay controls.
- **Vertical Settings Pill (`ToonReaderSettingsPill`) with Live Transparent Popups**:
  - Floating pill dock with 4 dedicated categories: **Display Screen**, **Crop Size Margin & Zoom**, **Reading Layout**, and **Canvas Background**.
  - Popups spawn with **100% transparent backgrounds**, allowing readers to see real-time page changes as they tweak parameters.
- **5,700+ Master Tag Catalog (`tags/master_tags.json`)**:
  - Curated taxonomy across literature, manga, anime, and media tropes with instant search, auto-suggestions, and multi-tag filtering (AND / OR logic).

---

### 📚 2. PDF & Book Suite (`contentType = 1`)
- **Hardware-Accelerated Native PDF Renderer**:
  - Ultra-fast, lag-free PDF page rendering using Android's native `PdfRenderer`.
  - Lightweight 220ms Crossfade loading state with document metadata.
- **Soft Aesthetic Pastel Watercolor Highlighters**:
  - Translucent watercolor washes using `drawRoundRect`, `CornerRadius(6.dp)`, and `BlendMode.Multiply`.
  - 6 pastel tones: Pastel Lemon (`#FFF59D`), Pastel Mint (`#A7F3D0`), Pastel Rose (`#FBCFE8`), Pastel Sky (`#BAE6FD`), Pastel Lavender (`#DDD6FE`), Pastel Peach (`#FED7AA`).
- **External Text Notes & Sticky Ribbon Tabs**:
  - Attach contextual notes to specific pages stored persistently per book (`notes_${mangaId}.json`) without Room migration risks.
  - Minimalist golden bookmark ribbon resting in the margin without obscuring text.
- **Full Undo / Redo Engine (Ctrl+Z & Ctrl+Y)**:
  - Robust Undo/Redo engine tracking generated Room database IDs for all highlights and notes.
- **Accurate Viewport Page Tracking**:
  - Viewport intersection algorithm (`maxByOrNull { visiblePixels }`) accurately detects the page currently occupying the screen.
- **Revamped 7-Tool Bottom Dock Pill**:
  - 1. **Highlighter** (`Brush`): Clean toggle ON/OFF.
  - 2. **Color Palette** (`Palette`): Pastel color selector.
  - 3. **Delete** (`DeleteOutline`): Deletes latest highlight or note with undo support.
  - 4. **Notes** (`StickyNote2`): Anchored Page Notes dialog.
  - 5. **Eye Care** (`Visibility`): Warm Sepia, Soft Mint, Dark OLED, and Crisp Light.
  - 6. **Undo** (`Undo`): Reverts last action.
  - 7. **Redo** (`Redo`): Re-applies reverted action.

---

### 📜 3. Novel & WebBook Reading Suite (`contentType = 4`)
- **Dedicated Novel Engine (`NovelPlayerScreen.kt`, `NovelReaderSettingsPill.kt`)**:
  - Full-featured reader for `.txt`, `.md`, `.text`, and `.markdown` text files.
  - Multi-chapter regex parsing recognizing chapter headings (e.g. `Chapter X`, `第X章`, `Act X`).
  - Word count indexing and chapter navigation tree.
- **WebBook Engine (`WebBookViewer.kt`)**:
  - Renders `.epub`, `.html`, `.xhtml`, `.fb2`, `.mobi`, `.azw`, `.azw3`, `.kf8`.
  - Dynamic CSS injection, eye-care dark mode overrides, and font scaling.
  - Normalized `0..10000` scroll progress scaling.
- **Granular Typography Customization**:
  - Adjust font size, line height, letter spacing, font weight, and text alignment (Justified / Left).
  - Built-in font families plus **Custom Font File Loader** importing `.ttf` and `.otf` fonts directly from storage via SAF.
- **Reading Checkpoint System**:
  - Save exact chapter and paragraph scroll positions (`+ Mark Here`) persisted in `pdf_marker`.
  - Persistent top-right completion percentage badge (`$percentage%`) and checkpoint ribbon.
  - One-tap resumption with smooth auto-scroll to the saved paragraph.

---

### 🎬 4. Video & Anime Suite (`contentType = 2`)
- **ExoPlayer Video Engine**:
  - Hardware-accelerated playback supporting MP4, MKV, WebM, AVI, TS, M4V, FLV, WMV, and 3GP.
- **Dual Container Modes: Series vs Channels**:
  - **Series Mode**: Structured anime/show hierarchies across 11 relationship types:
    1. *Season*
    2. *Sequel*
    3. *Prequel*
    4. *Movie*
    5. *OVA*
    6. *ONA*
    7. *Special*
    8. *Blu-ray*
    9. *Spinoff*
    10. *Recap / Summary*
    11. *Alt Version*
  - **Channel Mode**: Flat video playlist layout for creator clips, lectures, and documentaries.
- **Intuitive Player Gestures**:
  - Horizontal scrub: Precise timeline scrubbing with floating timestamp preview.
  - Left-edge vertical drag: Screen brightness adjustment.
  - Right-edge vertical drag: Volume boost adjustment.
  - Double-tap seek: 10-second skip forward and backward.
- **Picture-in-Picture (PiP) & Background Playback**:
  - Seamless multitasking support with system PiP.

---

### 🎵 5. Music & Audio Engine (`contentType = 3`)
- **AndroidX Media3 Foreground Service (`MusicNotificationService`)**:
  - System notification media controls, lockscreen art, and Bluetooth headset button support.
- **Floating MiniPlayer & PopUp Player (`PopUpPlayerActivity`)**:
  - Persistent floating mini player positioned gracefully above the navigation dock.
  - Standalone floating picture-in-picture audio player.
- **Wavy Music Slider & Animated Equalizer**:
  - Tactile wavy progress bar reacting to playback state.
  - 4-bar dynamic audio equalizer animation.
- **Synchronized LRC Karaoke Lyrics**:
  - Auto-scrolling time-synced lyrics with manual `.lrc` file import and inline editor.
- **Shuffle Mode Playback History Back-Stack**:
  - Previous (`<`) button works seamlessly in shuffle mode, navigating backward through played songs without re-shuffling.
- **Dynamic Color Palette Extraction**:
  - Real-time dominant color extraction from album covers using AndroidX Palette.

---

## 2. Dynamic Canvas Shaders & Scratchpad

### 🎨 8 Ambient Reactive Canvas Shaders
1. **Liquid**: Organic blob metaball physics that reacts to touch gestures.
2. **Constellation**: Celestial orbital particle physics and connective constellation lines.
3. **Crystal Snow**: Ambient crystalline snowfall particle physics.
4. **Drunk Stars**: Floating, dreamy star clusters with drifting physics.
5. **Flower**: Blooming chromatic petal geometry with radial pulse.
6. **Jellyfish**: Ambient underwater organic tentacles and undulating bell physics.
7. **Kaleidoscope**: Symmetric refractive kaleidoscope geometry with rotation.
8. **Neon Ripple**: Radiant neon wave ripple dynamics.

### 🖌️ Canvas Scratchpad (`CanvasScreen.kt`)
- Interactive drawing canvas with brush stroke size controls, color picker, eraser, clear, and undo.
- Floating background audio player integration for sketching while listening.

### 🔘 Customizable FAB Floating Action Buttons
- **JellyBall**: Signature tactile jelly physics.
- **DevilJellyBall**: Animated devil-horned variant.
- **CustomJellyBall**: User-styled floating button.
- **WobblyRing**: Translucent pulsing liquid ring.

---

## 3. Vault Sandboxing & Workspaces

### 🗂️ Workspaces
- Organize libraries into distinct user-defined workspaces (e.g. *"Favorites"*, *"Current Season"*, *"Archive"*, *"Reading List"*).
- Move individual media entries or batch-move entire workspaces with tree propagation.

### 🔒 Vault Security & Privacy Masking
- Mark any media item or folder as locked (`isLocked = true`).
- Protected by biometric authentication (Fingerprint / Face Unlock) or application PIN.
- `.nomedia` protection isolates private files from system gallery and music players.
- **SFW / NSFW Disguise Tags**: Custom privacy masking text labels (e.g. disguise sensitive collections under innocuous tags).
- **`VaultMigrationService`**: Background foreground service for safe migration between external storage and sandboxed vault without file loss.

---

## 4. Discovery, Search & Metadata

- **Advanced Search Engine (`HomeSearchView.kt`)**: Filter by media formats (`All`, `Book`, `Manhua`, `Manga`, `Series`, `Channel`, `Favorite`), search scope (Title, Tags, All), multi-tag logic (AND/OR), and sorting (Newest, Title A-Z, Title Z-A, Recently Read).
- **Centralized Import Studio (`ImportStudioSheet.kt`)**: Single file import and recursive Mega Importers for all 5 media domains.
- **Portable JSON Metadata**: Fully compatible with `.zine/*.json` and root `*.json` metadata with automatic cover matching.
