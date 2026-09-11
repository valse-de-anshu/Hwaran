# Hwaran Metadata Specification: `entry.json`

## 1. Overview

Hwaran uses an open, human-readable, and portable JSON metadata file called `entry.json` (also compatibly falls back to `metadata.json`) to persist rich metadata for media entries.

When a media entry (Manga, Manhua, Book, Series, or Channel) is imported or viewed, Hwaran automatically discovers, parses, and populates this metadata. Edits made inside the app (via the description edit screen) are written back to `entry.json` as well as mirrored into Hwaran's internal database and cache.

---

## 2. File Location Conventions

The `entry.json` file resides in the root directory of the media item:

### A. Folders (Manga / Manhua / Multi-file Webtoons)
Place `entry.json` at the root of the series directory:
```text
Storage/Manga/Solo Leveling/
├── entry.json               <-- Series metadata
├── cover.jpg                <-- (Optional) Cover artwork
├── Chapter 01/
│   ├── 001.jpg
│   └── 002.jpg
└── Chapter 02/
    └── 001.jpg
```

### B. Single Files (Books / PDFs / EPUBs / Light Novels)
For single-file books, place `entry.json` in the directory containing the book or in a dedicated folder:
```text
Storage/Books/Dune/
├── Dune.pdf
├── entry.json               <-- Book metadata
└── cover.jpg                <-- Cover artwork
```

### C. Fallback Cache
If the storage provider or directory is read-only (e.g. certain restricted SAF scopes), Hwaran transparently stores the metadata in its internal app cache:
```text
/data/data/com.ballade.hwaran/files/metadata/{mangaId}.json
```

---

## 3. JSON Schema Specification

Below is the complete reference schema for `entry.json`:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "HwaranEntryMetadata",
  "type": "object",
  "properties": {
    "title": {
      "type": "string",
      "description": "Primary display title of the media"
    },
    "altTitle": {
      "type": "string",
      "description": "Alternative title, romaji, kanji, or original language title"
    },
    "author": {
      "type": "string",
      "description": "Author or writer"
    },
    "artist": {
      "type": "string",
      "description": "Illustrator or artist (used primarily for Manga/Manhua)"
    },
    "publisher": {
      "type": "string",
      "description": "Publishing house, studio, or imprint"
    },
    "serialization": {
      "type": "string",
      "description": "Magazine serialization or platform (e.g., Weekly Shonen Jump, KakaoPage)"
    },
    "year": {
      "type": "string",
      "description": "Year of publication or release (e.g., '2024' or '1965')"
    },
    "language": {
      "type": "string",
      "description": "Language of the text/audio (e.g., 'English', 'Japanese', 'Korean', 'Chinese')"
    },
    "pages": {
      "type": "string",
      "description": "Total page count for books/PDFs (e.g., '412')"
    },
    "status": {
      "type": "string",
      "enum": ["Ongoing", "Completed", "Hiatus", "Cancelled"],
      "description": "Publication or release status"
    },
    "type": {
      "type": "string",
      "enum": ["Book", "Manhua", "Manga", "Series", "Channel"],
      "description": "Material classification tag (Tag 1)"
    },
    "rating": {
      "type": "string",
      "description": "Rating score and review count (e.g., '8.7 (152K)')"
    },
    "description": {
      "type": "string",
      "description": "Synopsis, plot summary, or blurb"
    },
    "tags": {
      "type": "array",
      "items": { "type": "string" },
      "description": "List of genre, theme, and descriptive tags (Tag 2)"
    },
    "totalChapters": {
      "type": "integer",
      "description": "Expected or known total chapters/episodes"
    },
    "isFavorite": {
      "type": "boolean",
      "description": "True if marked as favorite/bookmarked"
    }
  }
}
```

---

## 4. Field Reference & Content Type Variations

Hwaran automatically adapts the displayed fields based on the media's `contentType`:

| Field | JSON Key | Manga / Manhua (`contentType = 0`) | Book / Novel / PDF (`contentType = 1`) | Video Series / Channel (`contentType = 2`) |
| :--- | :--- | :--- | :--- | :--- |
| **Title** | `title` | Series title | Book title | Series title |
| **Alt Title** | `altTitle` | Original native / Romaji title | Original title | Alternative title |
| **Author** | `author` | Story / Original creator | Author / Novelist | Director / Creator |
| **Artist** | `artist` | Illustrator / Comic artist | *(Hidden in Book Info)* | Studio / Production |
| **Publisher** | `publisher` | Label / Publisher | Publishing company | Distributor / Platform |
| **Serialization** | `serialization` | Magazine / Web platform | *(Hidden in Book Info)* | Network / Channel |
| **Published Year** | `year` | Release year | Release year | Release year |
| **Language** | `language` | *(Optional)* | Language of book | Audio / Subtitle language |
| **Pages** | `pages` | *(Chapter count displayed)* | Total page count | *(Duration displayed)* |
| **Status** | `status` | Ongoing / Completed | Status | Status |
| **Material Tag** | `type` | `"Manga"` or `"Manhua"` | `"Book"` | `"Series"` or `"Channel"` |
| **Genres / Tags** | `tags` | Master Tag vocabulary | Master Tag vocabulary | Master Tag vocabulary |
| **Favorite** | `tags` | Includes `"Favorite"` | Includes `"Favorite"` | Includes `"Favorite"` |

---

## 5. Concrete Examples

### A. Book / Novel / PDF Example (`entry.json`)
```json
{
  "title": "Dune",
  "altTitle": "Dune Chronicles #1",
  "author": "Frank Herbert",
  "publisher": "Chilton Books",
  "year": "1965",
  "language": "English",
  "pages": "412",
  "status": "Completed",
  "type": "Book",
  "rating": "9.2 (280K)",
  "tags": [
    "Favorite",
    "Science Fiction",
    "Space Opera",
    "Philosophy",
    "Classic Literature",
    "Politics"
  ],
  "description": "Set on the desert planet Arrakis, Dune is the story of the boy Paul Atreides, heir to a noble family tasked with ruling an inhospitable world where the only valueless thing is water and the only valuable thing is the spice melange."
}
```

### B. Manga / Manhua Example (`entry.json`)
```json
{
  "title": "Solo Leveling",
  "altTitle": "나 혼자만 레벨업",
  "author": "Chugong",
  "artist": "DUBU (REDICE Studio)",
  "publisher": "D&C Media",
  "serialization": "KakaoPage",
  "year": "2018",
  "status": "Completed",
  "type": "Manhua",
  "rating": "9.8 (520K)",
  "tags": [
    "Favorite",
    "Action",
    "Fantasy",
    "Dungeon",
    "Hunters",
    "Level System",
    "Necromancy",
    "Overpowered Protagonist"
  ],
  "description": "10 years ago, after 'the Gate' that connected the real world with the monster world opened, some of the ordinary, everyday people received the power to hunt monsters within the Gate. They are known as 'Hunters'."
}
```

---

## 6. How Hwaran Auto-Populates & Synchronizes Metadata

1. **Discovery on Scan / Opening**:
   - When a folder or book is opened in `DescriptionScreen`, `MediaMetadataManager.loadMetadata(...)` looks for `entry.json` (or `metadata.json`) in the folder via direct filesystem access or SAF `DocumentFile`.
   - If found, it merges existing database values with the JSON fields.
   - If not found, it checks `files/metadata/{mangaId}.json` in internal storage.
   - If neither exists, default values are inferred from the media title, folder name, and Room database record.

2. **In-App Editing**:
   - Tapping the **Edit** action in the 3-dot menu unlocks the description editing canvas.
   - You can modify titles, authors, publishers, status, years, language, pages, and interactive tag chips.
   - Tapping **Save** writes immediately to `entry.json` in the original directory (and internal cache) while keeping the Room database entities synchronized.

3. **Tags Integration**:
   - Tag 1 (Material Type) is restricted to the valid library categories: `Book`, `Manhua`, `Manga`, `Series`, `Channel`.
   - Tag 2 (Generic / Genre Tags) autocompletes against Hwaran's curated Master Tag database (over 5,700 deduplicated tags covering Manga, Manhua, Light Novels, Literature, Anime, Doujinshi, and BISAC categories).
   - Toggling the bookmark button automatically syncs the `"Favorite"` tag with both `entry.json` and the Library's `"Favorite"` filter tab.
