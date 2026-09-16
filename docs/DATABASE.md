# Database Architecture & Schema

> **"How does data persist in Hwaran?"**

Hwaran uses **Room** (`hwaran_database`) backed by SQLite. As of the current architecture, the database is at **Version 16** with four focused DAOs, a composite shim interface, and four normalized entities.

---

## 1. Schema Overview

The database contains 4 primary entities:

```text
┌──────────────────────────────────────────────────────────────┐
│                         manga table                          │
├──────────────────────────────────────────────────────────────┤
│ id (PK)                                         INTEGER (AI) │
│ title, description, thoughts                    TEXT         │
│ coverPath, parentUri                            TEXT         │
│ isNsfw                                          INTEGER      │
│ lastModified                                    INTEGER      │
│ contentType (0..4)                              INTEGER      │
│ parentMangaId (self-reference)                  INTEGER?     │
│ boxLabel, boxPurpose                            TEXT?        │
│ isLocked                                        INTEGER      │
│ position                                        INTEGER      │
│ lastReadTitle                                   TEXT?        │
│ lastReadPage                                    INTEGER?     │
│ genre                                           TEXT?        │
│ workspace                                       TEXT?        │
│ openCount                                       INTEGER      │
│ isFavorite                                      INTEGER      │
└──────────────────────────────────────────────────────────────┘
           │ 1                                  │ 1
           │                                    │
           ▼ N                                  ▼ N
┌──────────────────────────────┐   ┌──────────────────────────────┐
│        chapter table         │   │       pdf_marker table       │
├──────────────────────────────┤   ├──────────────────────────────┤
│ id (PK)         INTEGER (AI) │   │ id (PK)         INTEGER (AI) │
│ mangaId         INTEGER      │   │ mangaId         INTEGER      │
│ title           TEXT         │   │ page            INTEGER      │
│ folderUri       TEXT         │   │ x1, y1, x2, y2  REAL         │
│ thumbnailUri    TEXT?        │   │ color           INTEGER      │
│ position        INTEGER      │   │ createdAt       INTEGER      │
│ artist          TEXT?        │   └──────────────────────────────┘
│ duration        INTEGER      │
│ lyrics          TEXT?        │
│ genre           TEXT?        │
│ openCount       INTEGER      │
└──────────────────────────────┘

┌──────────────────────────────┐
│     history_event table      │
├──────────────────────────────┤
│ id (PK)         INTEGER (AI) │
│ timestamp       INTEGER      │
│ eventType       TEXT         │
│ itemName        TEXT         │
│ details         TEXT         │
└──────────────────────────────┘
```

---

## 2. Table Specifications

### A. `manga` Table (`MangaEntity`)
The central entity for all media items, books, playlists, novel entries, and container series.

| Column | Type | Default | Description |
|---|---|---|---|
| `id` | `INTEGER` (PK) | Auto | Auto-increment unique item ID |
| `title` | `TEXT` | Required | Display title |
| `description` | `TEXT` | `""` | Item synopsis / notes |
| `thoughts` | `TEXT` | `""` | User-written personal thoughts & reviews |
| `coverPath` | `TEXT` | `""` | Absolute file path or content URI to cover art |
| `isNsfw` | `INTEGER` | `0` | 1 = Sensitive / NSFW, 0 = Standard |
| `parentUri` | `TEXT` | `""` | File path or SAF tree URI |
| `lastModified` | `INTEGER` | Required | Unix timestamp of last scan/modification |
| `contentType` | `INTEGER` | `0` | **`0`** = Toon / Manga / Manhua<br>**`1`** = Book / PDF / WebBook<br>**`2`** = Video / Anime / Channel<br>**`3`** = Music / Audio Playlists<br>**`4`** = Novel / Text / E-Books |
| `parentMangaId` | `INTEGER?` | `NULL` | Self-referencing parent ID for subfolders, seasons, or series containers |
| `boxLabel` | `TEXT?` | `NULL` | Custom UI tag or category label |
| `boxPurpose` | `TEXT?` | `NULL` | Container purpose (`series`, `channel`, `season`, `specials`, `book`, `novel`, `manga`, `manhua`, `song`) |
| `isLocked` | `INTEGER` | `0` | 1 = Requires PIN / biometric authentication |
| `position` | `INTEGER` | `0` | Manual order in library grid or paragraph scroll position |
| `lastReadTitle` | `TEXT?` | `NULL` | Title of last opened chapter, song, or video |
| `lastReadPage` | `INTEGER?` | `NULL` | Last read page (PDF/Comic), chapter index (Novel), or scaled scroll progress (WebBook `0..10000`) |
| `genre` | `TEXT?` | `NULL` | Primary genre tag |
| `workspace` | `TEXT?` | `NULL` | Name of assigned workspace (e.g. "Favorites", "Light Novels", "Backlog") |
| `openCount` | `INTEGER` | `0` | Frequency counter for access tracking |
| `isFavorite` | `INTEGER` | `0` | 1 = Pinned as favorite item |

---

### B. `chapter` Table (`ChapterEntity`)
Represents an individual readable, playable, or viewable unit inside a parent container.

| Column | Type | Default | Description |
|---|---|---|---|
| `id` | `INTEGER` (PK) | Auto | Auto-increment unique chapter ID |
| `mangaId` | `INTEGER` | Required | Foreign key reference to `manga.id` |
| `title` | `TEXT` | Required | Track / Episode / Chapter title |
| `folderUri` | `TEXT` | Required | URI or absolute path to chapter folder / media file |
| `thumbnailUri` | `TEXT?` | `NULL` | Optional extracted thumbnail URI |
| `position` | `INTEGER` | `0` | Track index, episode number, or chapter sorting order |
| `artist` | `TEXT?` | `NULL` | Audio artist name (Music mode) |
| `duration` | `INTEGER` | `0` | Playback duration in milliseconds |
| `lyrics` | `TEXT?` | `NULL` | Synchronized LRC lyrics or text content |
| `genre` | `TEXT?` | `NULL` | Track genre |
| `openCount` | `INTEGER` | `0` | Frequency counter for play / read count |

---

### C. `history_event` Table (`HistoryEventEntity`)
Audit log of playback, reading sessions, and user events.

| Column | Type | Default | Description |
|---|---|---|---|
| `id` | `INTEGER` (PK) | Auto | Auto-increment unique event ID |
| `timestamp` | `INTEGER` | Required | Unix timestamp of event occurrence |
| `eventType` | `TEXT` | Required | Event tag (`READ_TOON`, `READ_BOOK`, `READ_NOVEL`, `PLAY_VIDEO`, `LISTEN`, `IMPORT`, `PASSWORD_SET`, etc.) |
| `itemName` | `TEXT` | Required | Title of media item or chapter |
| `details` | `TEXT` | Required | Contextual metadata (e.g. `mangaId:12|pages:4|pos:23|totalPages:50`) |

---

### D. `pdf_marker` Table (`PdfMarkerEntity`)
Highlights, annotations, and reading checkpoints for PDF documents, Novels, and WebBooks.

| Column | Type | Default | Description |
|---|---|---|---|
| `id` | `INTEGER` (PK) | Auto | Auto-increment marker ID |
| `mangaId` | `INTEGER` | Required | Foreign key reference to `manga.id` |
| `page` | `INTEGER` | Required | 0-indexed PDF page number, Novel chapter index, or scaled WebBook scroll progress |
| `x1` | `REAL` | Required | Normalized top-left X (PDF) OR paragraph scroll offset integer stored as float (Novel checkpoint) |
| `y1` | `REAL` | Required | Normalized top-left Y (PDF) |
| `x2` | `REAL` | Required | Normalized bottom-right X (PDF) |
| `y2` | `REAL` | Required | Normalized bottom-right Y (PDF) |
| `color` | `INTEGER` | Required | ARGB color integer (e.g. pastel highlighter color or golden bookmark flag) |
| `createdAt` | `INTEGER` | CurrentTime | Unix timestamp of marker creation |

---

## 3. The 4 Focused DAOs & Composite LibraryDao

The database access layer is decomposed into 4 domain-focused DAOs in [`core/database/dao/`](file:///home/valse-de-anshu/Desktop/hwaran/app/src/main/java/com/ballade/hwaran/core/database/dao/):

1. **`MediaDao`**:
   - Manages CRUD operations on the `manga` table.
   - Filters by `contentType` (0 = Manga, 1 = Book, 2 = Video, 3 = Music, 4 = Novel).
   - Manages workspace queries, renaming, and mass movement across workspaces.
   - Handles parent-child queries (`parentMangaId`) for seasons and series relationships.
2. **`TrackDao`**:
   - Manages chapter, track, and episode queries on the `chapter` table.
   - Supports batch insertion, track reordering (`position`), lyrics updating, and cascading chapter deletion.
3. **`HistoryDao`**:
   - Manages insertion of audit events into `history_event`.
   - Supplies Flow-based history streams sorted by timestamp descending.
4. **`AnnotationDao`**:
   - Manages PDF highlights, text notes markers, and novel reading checkpoints in `pdf_marker`.
   - Supports undo/redo insertion and deletion by ID.
5. **`LibraryDao`**:
   - Composite interface extending `MediaDao`, `TrackDao`, `HistoryDao`, and `AnnotationDao` for backward compatibility across legacy repositories.

---

## 4. Complete Migration History

| Version Step | Migration Description |
|---|---|
| **4 → 5** | `ALTER TABLE chapter ADD COLUMN thumbnailUri TEXT` |
| **5 → 6** | `ALTER TABLE manga ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0` |
| **6 → 7** | `ALTER TABLE manga ADD COLUMN position INTEGER NOT NULL DEFAULT 0`<br>`ALTER TABLE chapter ADD COLUMN position INTEGER NOT NULL DEFAULT 0` |
| **7 → 8** | `ALTER TABLE chapter ADD COLUMN artist TEXT`<br>`ALTER TABLE chapter ADD COLUMN duration INTEGER NOT NULL DEFAULT 0` |
| **8 → 9** | `ALTER TABLE chapter ADD COLUMN lyrics TEXT` |
| **9 → 10** | `ALTER TABLE manga ADD COLUMN lastReadTitle TEXT`<br>`ALTER TABLE manga ADD COLUMN lastReadPage INTEGER` |
| **10 → 11** | `ALTER TABLE manga ADD COLUMN genre TEXT`<br>`ALTER TABLE chapter ADD COLUMN genre TEXT` |
| **11 → 12** | `ALTER TABLE manga ADD COLUMN workspace TEXT` |
| **12 → 13** | `CREATE TABLE IF NOT EXISTS history_event (...)` |
| **13 → 14** | `CREATE TABLE IF NOT EXISTS pdf_marker (...)` |
| **14 → 15** | `ALTER TABLE manga ADD COLUMN openCount INTEGER NOT NULL DEFAULT 0`<br>`ALTER TABLE chapter ADD COLUMN openCount INTEGER NOT NULL DEFAULT 0` |
| **15 → 16** | `ALTER TABLE manga ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0` |

---

## 5. Database Safety Guardrails

- **Zero Data Loss Principle**: Destructive migrations (`fallbackToDestructiveMigration`) are never relied upon for schema evolution.
- **Relational Integrity**: Deleting a parent `MangaEntity` automatically executes cascading cleanup of child chapters and markers to prevent orphaned records.
- **Off-Main-Thread Execution**: All database queries are executed strictly within coroutines on `Dispatchers.IO`.
- **Local Vault Privacy**: When an item is marked `isLocked = true`, its cover, parent URI, and metadata are protected behind authentication before display.
