# Hwaran Metadata Specification

> **"How does Hwaran detect, parse, and structure portable metadata?"**

This document defines how Hwaran discovers, parses, and populates metadata for all supported media classifications:
**Song / Music, Video Series, Video Channel, Novel, Book (PDF), Manga, and Manhua / Webtoon**.

---

## 1. Core Metadata Architecture & Rules

### Discovery & Priority Hierarchy
When importing or opening any media item, Hwaran searches for metadata using the following strict priority:
1. **`Material/.zine/*.json`** *(Highest priority — hidden portable metadata folder)*
2. **`Material/*.json`** *(Root-level JSON metadata file)*
3. **Internal App Cache** *(`/data/data/com.ballade.hwaran/files/metadata/{mangaId}.json`)*
4. **Filesystem Structure** *(Directory hierarchies, e.g. Season folders, Album folders)*
5. **Filename Hints** *(Filename patterns, e.g. `S01E02`, Track numbers)*

### Naming Does NOT Matter
Hwaran detects metadata files strictly by the `.json` file extension, **not** by matching a specific filename. All of the following are valid and automatically parsed:
- `Material/.zine/metadata.json`
- `Material/.zine/info.json`
- `Material/.zine/data.json`
- `Material/.zine/custom-name.json`
- `Material/entry.json`
- `Material/series.json`

If multiple `.json` files exist within the same folder, Hwaran prioritizes standard conventions (`metadata.json`, `info.json`, `entry.json`, `data.json`, `series.json`, `album.json`, `book.json`) followed by alphabetical ordering.

### Universal Invariants
1. **Metadata Overrides Filesystem**: Any value defined in the JSON file takes precedence over folder or file naming hints.
2. **Internal Files Are Excluded**: `.zine/` directories, `.json` metadata files, and auxiliary files are strictly hidden and never imported as media items.
3. **Cover Artwork Isolation**: Images matching cover names (`cover.jpg`, `poster.png`, `folder.webp`, etc.) or explicitly referenced in JSON are treated solely as artwork. They are **never** treated as media items, pages, episodes, or tracks.
4. **No Random Fallback**: If no cover is present or matched, Hwaran falls back to default placeholder graphics instead of grabbing random images from inside folders.
5. **Hierarchy Preservation**: Structured media (Volumes, Seasons, Albums) is never flattened into an unstructured list.

---

## 2. Complete Field Reference & Supported JSON Aliases

Hwaran features a flexible JSON parser supporting snake_case, camelCase, aliases, string values, and arrays:

| Target Field | Supported JSON Keys (Any of these) | Value Type | Example / Description |
| :--- | :--- | :--- | :--- |
| **Title** | `title`, `name`, `series_title`, `seriesTitle`, `book_title`, `bookTitle`, `album_title`, `albumTitle`, `album` | `String` | `"Solo Leveling"`, `"Dune"` |
| **Alt Title** | `altTitle`, `alt_title`, `alternative_title`, `alternativeTitle`, `nativeTitle`, `native_title`, `original_title`, `originalTitle`, `japanese_title`, `korean_title`, `romaji_title`, `english_title` | `String` | Native title, Romaji, or international title |
| **Description / Synopsis** | `description`, `synopsis`, `summary`, `overview`, `intro`, `about` | `String` | Plot overview or media blurb |
| **Author / Creator** | `author`, `writer`, `creator`, `author_name`, `authorName`, `authors` | `String` or `Array<String>` | Primary writer, author, or director |
| **Artist / Studio** | `artist`, `illustrator`, `penciller`, `artists`, `studio`, `singer`, `band` | `String` or `Array<String>` | Illustrator, animator, performing artist, or studio |
| **Type / Classification** | `type`, `box_purpose`, `boxPurpose`, `media_type`, `mediaType`, `kind` | `String` | `"Song"`, `"Series"`, `"Channel"`, `"Novel"`, `"Book"`, `"Manga"`, `"Manhua"` |
| **Status** | `status`, `publication_status`, `publicationStatus`, `series_status`, `seriesStatus` | `String` | `"Ongoing"`, `"Completed"`, `"Hiatus"`, `"Cancelled"` |
| **Rating / Score** | `rating`, `score` | `String` | `"9.2 (280K)"`, `"8.8"` |
| **Genres & Tags** | `tags`, `genres`, `genre`, `categories`, `category`, `keywords` | `Array<String>` or comma-separated `String` | `["Action", "Fantasy"]` or `"Action, Fantasy, Sci-Fi"` |
| **Publisher / Label** | `publisher`, `studio`, `network`, `label`, `imprint` | `String` | Record label, publishing house, or TV network |
| **Serialization** | `serialization`, `magazine` | `String` | Magazine, platform, or serialized outlet |
| **Year / Release Date** | `year`, `release_year`, `releaseYear`, `release_date`, `releaseDate`, `date`, `published`, `release` | `String` | `"2024"`, `"1965-08-01"` |
| **Language** | `language`, `lang` | `String` | `"English"`, `"Japanese"`, `"Korean"`, etc. |
| **Pages / Duration** | `pages`, `pageCount`, `page_count` | `String` | Total pages (Books) or duration summary |
| **Total Units** | `totalChapters`, `total_chapters`, `totalEpisodes`, `total_episodes`, `episodes`, `chapters` | `Integer` | Total known chapters, tracks, or episodes |
| **Cover File Override** | `cover`, `cover_image`, `coverImage`, `cover_art`, `coverArt`, `poster`, `thumbnail`, `image` | `String` | Exact filename of cover artwork (e.g. `"poster.png"`) |

---

## 3. Concrete Specifications by Media Domain

### 1. Song / Music (Audio)
- **Invariant**: `Artist -> Album -> Track -> Lyrics`
- Audio extensions: `.mp3`, `.flac`, `.wav`, `.m4a`, `.ogg`, `.opus`, `.aac`
- Lyrics (`.lrc`) belong to their respective audio track and are never exposed as separate media items.
- Cover art (`cover.jpg`, `folder.png`, `album.jpg`) attaches to the Album.

```json
{
  "album_title": "The Dark Side of the Moon",
  "artist": "Pink Floyd",
  "publisher": "Harvest Records",
  "year": "1973",
  "type": "Song",
  "status": "Completed",
  "genres": ["Progressive Rock", "Classic Rock"],
  "description": "The Dark Side of the Moon is the eighth studio album by Pink Floyd.",
  "total_chapters": 10,
  "cover": "cover.jpg"
}
```

---

### 2. Series (Video / Anime)
- **Invariant**: `Series -> Season -> Episode -> Video`
- Video extensions: `.mp4`, `.mkv`, `.webm`, `.avi`, `.mov`, `.flv`, `.ts`, `.wmv`, `.m4v`, `.3gp`
- Automatically discovers 11 relationship classifications (Season, Sequel, Prequel, Movie, OVA, ONA, Special, Blu-ray, Spinoff, Recap, Alt Version).

```json
{
  "series_title": "Frieren: Beyond Journey's End",
  "author": "Kanehito Yamada",
  "studio": "Madhouse",
  "year": "2023",
  "type": "Series",
  "status": "Completed",
  "rating": "9.8 (450K)",
  "genres": ["Adventure", "Drama", "Fantasy"],
  "total_episodes": 28,
  "cover": "poster.jpg"
}
```

---

### 3. Channel (Video Collections)
- **Invariant**: `Channel -> Video`
- Represents a flat video collection without season grouping (creator content, lectures, documentaries).

```json
{
  "name": "Kurzgesagt – In a Nutshell",
  "creator": "Philipp Dettmer",
  "studio": "Kurzgesagt GmbH",
  "publisher": "YouTube",
  "year": "2013",
  "type": "Channel",
  "status": "Ongoing",
  "categories": ["Science", "Education", "Animation"],
  "cover": "avatar.png"
}
```

---

### 4. Novel (Light Novel / Web Novel / Literature)
- **Invariant**: `Novel -> Volume -> Chapter -> Text/EPUB/Markdown`
- Text extensions: `.txt`, `.md`, `.text`, `.markdown`
- E-Book extensions: `.epub`, `.html`, `.fb2`, `.mobi`, `.azw`, `.azw3`

```json
{
  "title": "The Beginning After the End",
  "alt_title": "TBATE",
  "author": "TurtleMe",
  "artist": "Fuyuki23",
  "publisher": "Tapas Media",
  "year": "2017",
  "type": "Novel",
  "status": "Ongoing",
  "tags": ["Reincarnation", "Isekai", "Magic", "Fantasy"],
  "synopsis": "King Grey has unrivaled strength, wealth, and prestige in a world governed by martial ability.",
  "cover": "cover.jpg"
}
```

---

### 5. Book (PDF / Technical Documents)
- **Invariant**: `Material -> File (.pdf)`

```json
{
  "book_title": "Atomic Habits",
  "author": "James Clear",
  "publisher": "Avery",
  "year": "2018",
  "pages": "320",
  "type": "Book",
  "status": "Completed",
  "genres": ["Self-Help", "Productivity"],
  "cover": "cover.jpg"
}
```

---

### 6. Manga (Japanese Comics)
- **Invariant**: `Material -> Volume (optional) -> Chapter -> Pages`

```json
{
  "title": "Sousou no Frieren",
  "alt_title": "Frieren: Beyond Journey's End",
  "native_title": "葬送のフリーレン",
  "author": "Kanehito Yamada",
  "artist": "Tsukasa Abe",
  "publisher": "Shogakukan",
  "year": "2020",
  "type": "Manga",
  "status": "Ongoing",
  "tags": ["Adventure", "Fantasy", "Shounen"],
  "total_chapters": 135,
  "cover": "cover.webp"
}
```

---

### 7. Manhua / Manhwa (Webtoons)
- **Invariant**: `Material -> Chapter -> Vertical Strips`

```json
{
  "title": "Omniscient Reader's Viewpoint",
  "native_title": "전지적 독자 시점",
  "author": "Sing Shong",
  "artist": "Sleepy-C",
  "publisher": "Naver Webtoon",
  "year": "2020",
  "type": "Manhua",
  "status": "Ongoing",
  "tags": ["Action", "Apocalypse", "Constellations"],
  "total_chapters": 220,
  "cover": "cover.jpg"
}
```

---

## 4. Cover Artwork Matching Rules

1. **Explicit Declaration**: If `"cover"`, `"cover_image"`, `"poster"`, or `"thumbnail"` is declared in JSON, Hwaran searches for that exact file first.
2. **Standard Filename Priority**:
   - `cover.jpg`, `cover.jpeg`, `cover.png`, `cover.webp`
   - `poster.jpg`, `poster.png`, `poster.webp`
   - `folder.jpg`, `folder.png`, `folder.webp`
   - `artwork.jpg`, `artwork.png`, `artwork.webp`
   - `album.jpg`, `album.png`, `album.webp`
   - `front.jpg`, `front.png`, `front.webp`
3. **Strict Isolation**: Cover art is never exposed as an internal page, episode, or track.
4. **No Arbitrary Picking**: If no cover image matches, a styled placeholder is used.
