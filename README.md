# Hwaran (화란)

> **The Ultimate Private Offline Media Vault & Experience Engine.**  
> Read, watch, and listen to your local media in a fluid, handcrafted, privacy-first interface.

---

## What the Hell is Hwaran?

**Hwaran** is an Android media client built for power users who hoard local content. It is **not** an online streaming client, and it doesn't phone home. 

If you scrape, download, or collect:
- **Manga & Manhua** (image folders, archives, webtoon strips)
- **Books & Documents** (PDFs, multi-page epubs/manuals)
- **Videos & Anime** (structured season folders, episodes, loose clips)
- **Music & Soundtracks** (local audio files with dynamic reactive backgrounds)

Hwaran puts everything behind a single, unified, biometric/PIN-protected vault. It brings your raw folder dumps to life with silky 120Hz Jetpack Compose animations, ambient shader backgrounds, chapter navigation, progress tracking, and customizable workspaces.

---

## Universal Flow

Every piece of media in Hwaran follows a consistent 3-stage journey:

```text
┌─────────────────────────┐
│     1. Home Library     │  Workspaces, cover grids, mode toggles, instant search
└───────────┬─────────────┘
            │ Tap card
┌───────────▼─────────────┐
│  2. Description / Hub   │  Overview, seasons/volumes, chapter lists, metadata
└───────────┬─────────────┘
            │ Tap entry
┌───────────▼─────────────┐
│  3. Immersive Consumer  │  Webtoon vertical strip, PDF reader, Video/Music player
└─────────────────────────┘
```

---

## Core Pillars

1. **100% Offline & Private** — No tracking, no accounts, no analytics. Operates strictly on local files and Android Storage Access Framework (SAF).
2. **Four Media Engines in One App** — Seamless switching between Toon, Book, Video, and Music with media-tailored controls and UI behaviors.
3. **Fluid Handcrafted UI** — Material 3 + Custom Canvas shaders (Liquid, Celestial, Drunk Stars, Jellyfish, Kaleidoscope, Flower, Poker).
4. **Workspaces & Vault Organization** — Group content into custom workspaces (e.g. "Favorites", "Current", "Archive") without needing to duplicate files on disk.

---

## Tech Stack at a Glance

| Layer | Technologies |
|---|---|
| **Language** | Kotlin 2.0.21 |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Architecture** | Feature-based Clean Architecture + UDF |
| **Database** | Room 2.7.0 (KSP, 4 focused DAOs) |
| **Preferences** | Jetpack DataStore (Preferences) |
| **Media Playback** | AndroidX Media3 / ExoPlayer 1.5.0 + MediaSessionService |
| **Storage & I/O** | Android SAF (`DocumentFile`, `DocumentsContract`, Scoped Storage) |
| **Image Loading** | Coil 2.7.0 |

---

## Documentation Map

For detailed guides, refer to the documentation in [`docs/`](docs/):

- 🏛️ [**`docs/ARCHITECTURE.md`**](docs/ARCHITECTURE.md) — *How the hell is Hwaran built?* (Layering, packages, data flow, player lifecycle)
- 🗄️ [**`docs/DATABASE.md`**](docs/DATABASE.md) — *How does data persist?* (Room schema v15, tables, DAOs, migrations)
- 🎯 [**`docs/FEATURES.md`**](docs/FEATURES.md) — *What is Hwaran supposed to do?* (Feature matrix, reader modes, audio shaders)
- 🛠️ [**`docs/DEVELOPMENT.md`**](docs/DEVELOPMENT.md) — *How do I build, test, and work on Hwaran?* (Prerequisites, Gradle, Git strategy)
- 📜 [**`docs/CHANGELOG.md`**](docs/CHANGELOG.md) — *What changed?* (Version history and refactoring milestones)
- 📓 [**`docs/PROGRESS.md`**](docs/PROGRESS.md) — *What did we do today?* (Daily dev journal and active work log)
