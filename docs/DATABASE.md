# Database Architecture & Schema

> **"How does data persist in Hwaran?"**

Hwaran uses **Room** (`hwaran_database`) backed by SQLite. As of the latest architecture refactor, the database is at **Version 15** with four focused DAOs and a composite shim interface.

---

## 1. Schema Overview

The database contains 4 primary entities:

```text
┌──────────────────────────────┐         ┌──────────────────────────────┐
│         manga table          │ 1     N │        chapter table         │
│──────────────────────────────│─────────│──────────────────────────────│
│ id (PK)                      │         │ id (PK)                      │
│ title, description, thoughts │         │ mangaId                      │
│ parentUri, coverPath         │         │ title, folderUri             │
│ contentType (0..4)           │         │ thumbnailUri                 │
│ boxPurpose, boxLabel         │         │ artist, duration             │
│ parentMangaId (self)         │         │ lyrics                       │
│ workspace, position          │         │ position, openCount, genre   │
│ isLocked, isNsfw             │         └──────────────────────────────┘
│ lastReadTitle, lastReadPage  │
│ openCount, lastModified      │         ┌──────────────────────────────┐
└──────────────────────────────┘         │       pdf_marker table       │
           │ 1                           │──────────────────────────────│
           │                             │ id (PK)                      │
           └───────────────────────────N │ mangaId                      │
                                         │ page, x1, y1, x2, y2         │
                                         │ color, createdAt             │
┌──────────────────────────────┐         └──────────────────────────────┘
│     history_event table      │
│──────────────────────────────│
│ id (PK)                      │
│ timestamp, eventType         │
│ itemName, details            │
└──────────────────────────────┘
```

---

## 2. Table Specifications

### A. `manga` Table (`MangaEntity`)
The central entity for all media items, books, playlists, and container series.

| Column | Type | Description |
|---|---|---|
| `id` | `INTEGER` (PK) | Auto-increment unique item ID |
| `title` | `TEXT` | Display title |
| `description` | `TEXT` | Item synopsis / notes |
| `thoughts` | `TEXT` | User-written personal thoughts & reviews |
| `coverPath` | `TEXT` | Absolute file path or content URI to cover art |
| `isNsfw` | `INTEGER` | 1 = Sensitive / NSFW, 0 = Standard |
| `parentUri` | `TEXT` | File path or SAF tree URI |
| `lastModified` | `INTEGER` | Unix timestamp of last scan/modification |
| `contentType` | `INTEGER` | **`0`** = Toon / Manga<br>**`1`** = Book / PDF<br>**`2`** = Video / Anime<br>**`3`** = Music / Audio<br>**`4`** = Novel / Text |
| `boxPurpose` | `TEXT?` | Container purpose (`series`, `channel`, `season`, `specials`) |
| `boxLabel` | `TEXT?` | Custom UI tag or category label |
| `parentMangaId` | `INTEGER?` | Self-referencing parent ID for subfolders / seasons |
| `isLocked` | `INTEGER` | 1 = Requires PIN / biometric authentication |
| `position` | `INTEGER` | Manual order in library grid or paragraph scroll position |
| `lastReadTitle` | `TEXT?` | Title of last opened chapter, song, or video |
| `lastReadPage` | `INTEGER?` | Last read page (PDF/Comic), chapter index (Novel), or scaled scroll progress (WebBook `0..10000`) |
| `genre` | `TEXT?` | Primary genre tag |
| `workspace` | `TEXT?` | Name of assigned workspace (e.g. "Favorites", "Backlog") |
| `openCount` | `INTEGER` | Frequency counter for sorting by popularity |
| `isFavorite` | `INTEGER` | 1 = Pinned as favorite |

---

### B. `chapter` Table (`ChapterEntity`)
Represents an individual readable, playable, or viewable unit inside a parent container.

| Column | Type | Description |
|---|---|---|
| `id` | `INTEGER` (PK) | Auto-increment unique chapter ID |
| `mangaId` | `INTEGER` | References `manga.id` |
| `title` | `TEXT` | Track / Episode / Chapter title |
| `folderUri` | `TEXT` | URI or absolute path to chapter folder / media file |
| `thumbnailUri` | `TEXT?` | Optional extracted thumbnail URI |
| `position` | `INTEGER` | Track index, episode number, or chapter order |
| `artist` | `TEXT?` | Audio artist name (Music mode) |
| `duration` | `INTEGER` | Playback duration in milliseconds |
| `lyrics` | `TEXT?` | Embedded or synchronized LRC lyrics |
| `genre` | `TEXT?` | Track genre |
| `openCount` | `INTEGER` | Play / Read count |

---

### C. `history_event` Table (`HistoryEventEntity`)
Audit log of playback, reading sessions, and user events.

| Column | Type | Description |
|---|---|---|
| `id` | `INTEGER` (PK) | Auto-increment unique event ID |
| `timestamp` | `INTEGER` | Unix timestamp of event occurrence |
| `eventType` | `TEXT` | Event tag (`READ_TOON`, `READ_BOOK`, `READ_NOVEL`, `PLAY_VIDEO`, `LISTEN`, `IMPORT`, `DELETE`) |
| `itemName` | `TEXT` | Title of media item or chapter |
| `details` | `TEXT` | Contextual metadata (e.g. `mangaId:12|pages:4|pos:23|totalPages:50`) |

---

### D. `pdf_marker` Table (`PdfMarkerEntity`)
Highlights and visual markers stored for PDF documents, WebBooks, and Novels.

| Column | Type | Description |
|---|---|---|
| `id` | `INTEGER` (PK) | Auto-increment marker ID |
| `mangaId` | `INTEGER` | Target document / book ID (`manga.id`) |
| `page` | `INTEGER` | 0-indexed PDF page, Novel chapter index, or WebBook percentage |
| `x1`, `y1`, `x2`, `y2` | `REAL` | Normalized bounding box coordinates (PDF) or paragraph scroll offset (`x1`) |
| `color` | `INTEGER` | ARGB color integer |
| `createdAt` | `INTEGER` | Unix timestamp of creation |

---

## 3. The 4 Focused DAOs

DAOs are located in [`core/database/dao/`](../app/src/main/java/com/ballade/hwaran/core/database/dao/):

1. **`MediaDao`**: Handles container operations on the `manga` table (filtering by `contentType`, workspace renames, tree deletes, parent-child queries).
2. **`TrackDao`**: Handles chapter, track, and episode queries and deletions on the `chapter` table.
3. **`HistoryDao`**: Manages event ingestion and chronological feeds for the History screen.
4. **`AnnotationDao`**: Manages PDF markers, highlights, and novel checkpoints.
5. **`LibraryDao`**: A composite interface extending all 4 DAOs to preserve backward compatibility for legacy repositories.

---

## 4. Migration History

| Version Step | Changes Applied |
|---|---|
| **4 → 5** | Added `chapter.thumbnailUri` |
| **5 → 6** | Added `manga.isLocked` |
| **6 → 7** | Added `manga.position` and `chapter.position` |
| **7 → 8** | Added `chapter.artist` and `chapter.duration` |
| **8 → 9** | Added `chapter.lyrics` |
| **9 → 10** | Added `manga.lastReadTitle` and `manga.lastReadPage` |
| **10 → 11** | Added `manga.genre` and `chapter.genre` |
| **11 → 12** | Added `manga.workspace` |
| **12 → 13** | Created `history_event` table |
| **13 → 14** | Created `pdf_marker` table |
| **14 → 15** | Added `manga.openCount` and `chapter.openCount` |

---

## 5. Database Safety Guardrails

- **No Destructive Migrations**: Room database migrations must never drop or recreate tables with user data.
- **Relational Integrity**: Deleting a parent `MangaEntity` must cascade delete child chapters and markers to prevent orphaned records.
- **Thread Safety**: All database queries must be called within coroutines on `Dispatchers.IO`.
