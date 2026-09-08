# Features Specification

> **"What is Hwaran supposed to do?"**

Hwaran is an **all-in-one private offline media vault** designed to organize and consume local Manga/Manhua, Books/PDFs, Video/Anime, and Music in a unified, fluid experience.

---

## 1. The Four Media Engines

### 📖 1. Manga & Manhua Engine (`contentType = 0`)
- **Continuous Webtoon Scrolling**: Seamless vertical strip reading optimized for long-strip manhwa and multi-image manga chapters.
- **Natural Chapter Progression**: Auto-advance to the next chapter upon reaching the end of the current strip.
- **Dynamic Image Cache**: High-efficiency Coil memory cache tuned for vertical image sequences.
- **Smart Sorter**: Natural chapter sorting respecting decimal chapters (e.g. `Chapter 10.5` after `Chapter 10`).

### 📚 2. Book & PDF Reader (`contentType = 1`)
- **Native PDF Rendering**: Hardware-backed high-resolution rendering using Android `PdfRenderer`.
- **Visual Markers & Annotations**: Place bookmarks and color-coded highlight boxes directly onto PDF pages (`pdf_marker`).
- **Interactive Links**: Extraction of embedded hyperlinks inside PDF documents.
- **Reading Progress**: Automatic resumption to the exact last-read page.

### 🎬 3. Video & Anime Player (`contentType = 2`)
- **ExoPlayer Video Engine**: Hardware-accelerated decoding supporting MP4, MKV, WebM, AVI, and TS.
- **Structured Series Support**: Automatic detection of season folders, specials, and episodes via `SeriesStructureImporter`.
- **Intuitive Player Gestures**:
  - Horizontal drag: fast scrub with timestamp preview.
  - Vertical drag (left): brightness adjustments.
  - Vertical drag (right): volume adjustments.
- **Compact & Large Layouts**: Switchable layout modes in description hub for standard movies vs multi-season anime series.

### 🎵 4. Music & Audio Engine (`contentType = 3`)
- **System-Integrated Media3 Playback**: Continues playback in background with system notification controls and lock screen art.
- **Dynamic Reactive Visual Shaders**: 7 handcrafted ambient backgrounds driven by Canvas animations:
  1. *Liquid* (Blob metaball physics tracking touches)
  2. *Celestial* (Constellation orbit particles)
  3. *Drunk Stars* (Floating dreamy star cluster)
  4. *Jellyfish* (Underwater organic tentacles)
  5. *Flower* (Blooming organic petals)
  6. *Kaleidoscope* (Symmetric refractive geometry)
  7. *Poker* (Casino-style floating card suits)
- **Tag & Cover Editing**: In-app metadata and cover art modification saved directly to track entities.
- **Waveform Slider**: Elastic, interactive `WavyMusicSlider` with haptic feedback.

---

## 2. Vault & Organization Features

### 🗂️ Workspaces
- Users can segment their library into distinct workspaces (e.g. *"I Love It"*, *"Backlog"*, *"Read Again"*, *"Current Season"*).
- Items can be moved individually or as an entire workspace with tree propagation (updates parent container and all its chapters).
- Workspaces persist in DataStore and sync reactively with Room.

### 🔒 Vault Security & Locked Content
- Mark any media item as locked (`isLocked = 1`).
- Locked items are hidden from the primary grid until authenticated via the user's configured PIN or biometric prompt.
- `.nomedia` protection prevents other gallery and music apps on the device from discovering private media files.

### ⚡ Centralized Import Pipeline
- **Single Import**: Imports a single file or a single container directly.
- **Mega Import**: Recursively scans complex folder trees, auto-detecting album vs series structures without user friction.
- Supports both **Local Copy** (internal vault sandbox) and **External Link** (SAF tree URI permissions).

---

## 3. What Hwaran Explicitly Does NOT Do

- **No Online Streaming or Web Scraping Inside the App**: Hwaran consumes files that already exist on your device.
- **No Analytics or Telemetry**: Zero external network calls.
- **No Cloud Accounts or Subscriptions**: Purely offline and self-contained.
