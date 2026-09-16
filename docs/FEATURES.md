# Features Specification & Capabilities

> **"What is Hwaran capable of?"**

Hwaran is an **all-in-one private offline media vault & experience engine** built to organize, manage, and consume local Manga/Manhua, Books/PDFs, Novels/E-Books, Video/Anime, and Music in a unified, fluid, and privacy-first interface.

---

## 1. The Five Specialized Media Engines

### 📖 1. Manga & Manhua Engine (`contentType = 0`)
- **Dual Reading Modes**:
  - **Continuous Webtoon Scrolling**: High-performance vertical strip reader optimized for long-strip manhwa and webcomics.
  - **Paged LTR / RTL Mode**: Traditional comic page-by-page reader supporting both Japanese Manga (Right-to-Left) and Western Comic (Left-to-Right) reading directions.
- **Smart Edge Navigation & White Margin Crop**:
  - Full-height tap zones for seamless one-handed navigation.
  - Automatic white border crop zoom to maximize content on mobile screens.
- **4,000+ Master Tag Catalog**:
  - Comprehensive tag taxonomy with cross-genre filtering, AND/OR multi-tag logic, and instant tag-based discovery.
- **Natural Chapter Sorter**:
  - Natural sorting that correctly sequences decimal chapters (e.g. `Chapter 10.5` directly after `Chapter 10`).

---

### 📚 2. Book & PDF Reader (`contentType = 1`)
- **Native Hardware PDF Rendering**:
  - Smooth multi-page document rendering backed by Android `PdfRenderer` and `PdfiumAndroid`.
- **Multi-Color Visual Highlighter & Marker**:
  - 6 aesthetic highlighter colors (Golden Sun, Emerald, Cyber Blue, Coral Rose, Violet, Charcoal).
  - Tap-to-highlight and tap-to-delete gesture workflow.
- **Eye-Care Tint Modes**:
  - Instant toggle between *Warm Sepia*, *Paper Green*, *OLED Night Mode*, and *Crisp Light*.
- **Interactive Link Extractor & Page Scrub Navigator**:
  - Detects embedded hyperlinks and internal document anchors with instant tap navigation.

---

### 📜 3. Novel & WebBook Reader (`contentType = 4`)
- **Universal Multi-Format E-Book Engine**:
  - Native parsing and rendering for `.epub`, `.html`, `.mobi`, `.prc`, `.fb2`, `.azw`, `.azw3`, `.txt`, `.md`, and `.rtf`.
- **Granular Typography Customization**:
  - Adjust font sizes, line heights, paragraph spacing, and text alignment (Justified / Left-aligned).
  - Asset fonts built-in (*Literata*, *Lora*, *Merriweather*, *Source Serif*, *Nunito*, *Mono*).
  - Custom font file loader: Import any `.ttf` or `.otf` font file directly from storage.
- **Paragraph-Accurate Checkpoints & Sticky Tabs**:
  - Floating checkpoint card to save exact chapter and paragraph positions (`+ Mark Here`).
  - Top-right persistent progress sticky tab showing real-time `$percentage%` and checkpoint indicators.
  - Quick-jump top bar capsule (`Last: $savedPct% (Ch. X)`) to instantly resume saved reading locations.
- **Auto-Scroll Position Resumption**:
  - Restores the exact scroll position upon opening books without overwriting progress.

---

### 🎬 4. Video & Anime Player (`contentType = 2`)
- **Multi-Format ExoPlayer Video Engine**:
  - Hardware-accelerated playback supporting MP4, MKV, WebM, AVI, TS, M4V, and 3GP.
- **Automated Series & Franchise Hierarchy**:
  - Recursively discovers multi-season anime series, movies, OVAs, ONAs, specials, and bonus tracks.
- **Intuitive Player Gestures**:
  - Horizontal scrub: Precise timeline scrubbing with floating timestamp preview.
  - Left-edge vertical drag: Screen brightness adjustment.
  - Right-edge vertical drag: Volume boost adjustment.
  - Double-tap seek: 10-second skip forward and backward.
- **Picture-in-Picture (PiP) & Background Playback**:
  - Continue watching while multitasking.

---

### 🎵 5. Music & Audio Engine (`contentType = 3`)
- **System-Integrated Media3 Foreground Service**:
  - Background audio playback using `MediaSessionService` with lockscreen controls, notification widget, and Bluetooth headset support.
- **7 Handcrafted Reactive Canvas Shaders**:
  1. *Liquid* — Organic blob metaball physics reacting to touch.
  2. *Celestial* — Constellation orbital particle physics.
  3. *Drunk Stars* — Floating, dreamy star clusters.
  4. *Jellyfish* — Ambient underwater organic tentacles.
  5. *Flower* — Blooming chromatic petal geometry.
  6. *Kaleidoscope* — Symmetric refractive kaleidoscope geometry.
  7. *Poker* — Casino-style floating suit physics.
- **Synchronized LRC Karaoke Lyrics**:
  - Real-time auto-scrolling lyrics synced to timestamps with manual `.lrc` file association and inline editing.
- **Smart / Advance / Normal Shuffle**:
  - Smart shuffle prioritizes artist/album variety.
  - Full playback history back-stack allows seamless Previous (`<`) navigation in shuffle mode.
- **Dynamic Color Palette Generation**:
  - Real-time dominant color extraction from album covers using AndroidX Palette.

---

## 2. Vault & System Capabilities

### 🗂️ Workspaces
- Organize libraries into distinct user-defined workspaces (e.g. *"Favorites"*, *"Current Season"*, *"Archive"*, *"Reading List"*).
- Move individual media entries or batch-move entire workspaces with tree propagation.

### 🔒 Vault Security & Biometrics
- Mark any media item or folder as locked (`isLocked = true`).
- Protected by biometric prompt (Fingerprint / Face Unlock) or application PIN.
- `.nomedia` protection isolates private files from system gallery and music players.

### ⚡ Centralized Import Pipeline
- **Single Import**: Import single files or folders directly.
- **Mega Import**: Recursively scan complex directory structures, auto-detecting manga chapters, season folders, and music albums.
- **Storage Modes**: Choose between *External SAF Linkage* (keep files on SD card/storage) or *Internal Vault Sandboxing* (copy into private app storage).

### 🔍 Search, Discovery & Continue Watching
- Cross-library search with scope targeting (Title, Tags, or All Fields).
- Multi-tag filter with AND/OR logic.
- Real-time **Continue Watching / Reading** dashboard showing exact progress percentages and resume targets.

---

## 3. Privacy & Offline Guarantee

- **100% Offline**: Zero external network requests, analytics, tracking, or telemetry.
- **Self-Contained**: No accounts, cloud dependencies, or subscriptions.
- **User File Safety**: Never alters or deletes original user media files unless explicitly initiated.
