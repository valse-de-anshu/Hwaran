# Hwaran Metadata Specification

This document defines how Hwaran discovers, parses, and populates metadata for all supported media types:
**Song / Music, Video Series, Video Channel, Novel, Book (PDF), Manga, and Manhua / Manhwa**.

---

## 1. Core Metadata Architecture & Rules

### Discovery & Priority Hierarchy
When importing or opening any media item, Hwaran searches for metadata using the following strict priority:
1. **`Material/.zine/*.json`** *(Highest priority — hidden portable metadata folder)*
2. **`Material/*.json`** *(Root-level JSON metadata file)*
3. **Filesystem Structure** *(Directory hierarchies, e.g. Season folders, Album folders)*
4. **Filename Hints** *(Filename patterns, e.g. `S01E02`, Track numbers)*

### Naming Does NOT Matter
Hwaran detects metadata files strictly by the `.json` file extension, **not** by matching a specific filename. All of the following are valid and automatically parsed:
- `Material/.zine/metadata.json`
- `Material/.zine/info.json`
- `Material/.zine/data.json`
- `Material/.zine/whatever-the-user-called-it.json`
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

## 3. Specifications & Concrete Examples by Media Type

### 1. Song / Music (Audio)

#### Structure Invariant: `Artist -> Album -> Track -> Lyrics`
- Audio extensions: `.mp3`, `.flac`, `.wav`, `.m4a`, `.ogg`, `.opus`, `.aac`
- Lyrics (`.lrc`) belong to their respective audio track and are never exposed as independent media items.
- Cover art (`cover.jpg`, `folder.png`) attaches to the Album.

#### Recommended Folder Structure:
```text
Music/
└── Pink Floyd/
    └── The Dark Side of the Moon/
        ├── .zine/
        │   └── album.json
        ├── cover.jpg
        ├── 01 - Speak to Me.mp3
        ├── 01 - Speak to Me.lrc
        ├── 02 - Breathe (In the Air).mp3
        ├── 02 - Breathe (In the Air).lrc
        └── 03 - On the Run.mp3
```

#### JSON Example (`album.json` or `.zine/metadata.json`):
```json
{
  "album_title": "The Dark Side of the Moon",
  "alt_title": "Dark Side of the Moon",
  "artist": "Pink Floyd",
  "creator": "Roger Waters, David Gilmour, Richard Wright, Nick Mason",
  "publisher": "Harvest Records",
  "year": "1973",
  "type": "Song",
  "status": "Completed",
  "rating": "9.9 (1.2M)",
  "genres": ["Progressive Rock", "Psychedelic Rock", "Classic Rock"],
  "description": "The Dark Side of the Moon is the eighth studio album by the English rock band Pink Floyd, released on 1 March 1973 by Harvest Records.",
  "total_chapters": 10,
  "cover": "cover.jpg"
}
```

---

### 2. Series (Video)

#### Structure Invariant: `Series -> Season -> Episode -> Video`
- Video extensions: `.mp4`, `.mkv`, `.webm`, `.avi`, `.mov`, `.flv`, `.ts`, `.wmv`, `.m4v`, `.3gp`
- If a series folder directly contains episode video files without season directories, Hwaran automatically infers `Season 1`.

#### Recommended Folder Structure:
```text
Series/
└── Breaking Bad/
    ├── .zine/
    │   └── series.json
    ├── poster.jpg
    ├── Season 01/
    │   ├── S01E01 - Pilot.mp4
    │   └── S01E02 - Cat's in the Bag....mp4
    └── Season 02/
        ├── S02E01 - Seven Thirty-Seven.mp4
        └── S02E02 - Grilled.mp4
```

#### JSON Example (`series.json` or `.zine/metadata.json`):
```json
{
  "series_title": "Breaking Bad",
  "author": "Vince Gilligan",
  "studio": "Sony Pictures Television",
  "publisher": "AMC",
  "year": "2008",
  "type": "Series",
  "status": "Completed",
  "rating": "9.5 (2.1M)",
  "genres": ["Crime", "Drama", "Thriller"],
  "description": "A chemistry teacher diagnosed with inoperable lung cancer turns to manufacturing and selling methamphetamine with a former student in order to secure his family's financial future.",
  "total_episodes": 62,
  "cover": "poster.jpg"
}
```

---

### 3. Channel (Video)

#### Structure Invariant: `Channel -> Video`
- Represents a flat, continuous video collection (e.g. creator content, lectures, documentaries, video podcasts).
- No season grouping is enforced or required.

#### Recommended Folder Structure:
```text
Channels/
└── Kurzgesagt - In a Nutshell/
    ├── .zine/
    │   └── info.json
    ├── avatar.png
    ├── What If We Detonated All Nuclear Bombs at Once.mp4
    ├── The Egg - A Short Story.mp4
    └── Why Beautiful Things Make us Happy.mp4
```

#### JSON Example (`info.json` or `.zine/metadata.json`):
```json
{
  "name": "Kurzgesagt – In a Nutshell",
  "creator": "Philipp Dettmer",
  "studio": "Kurzgesagt GmbH",
  "publisher": "YouTube",
  "year": "2013",
  "type": "Channel",
  "status": "Ongoing",
  "rating": "9.8 (850K)",
  "categories": ["Science", "Education", "Animation", "Philosophy", "Space"],
  "description": "Videos explaining things with optimistic nihilism. We are a small team who want to make science look beautiful. Because it is beautiful.",
  "cover": "avatar.png"
}
```

---

### 4. Novel (Light Novel / Web Novel / Literature)

#### Structure Invariant: `Novel -> Volume -> Chapter -> Text/EPUB/PDF`
- Used for text-heavy prose, light novels, web novels, and serialized fiction.
- Captures writer, character designer/illustrator, and web novel serialization platform.

#### Recommended Folder Structure:
```text
Novels/
└── The Beginning After the End/
    ├── .zine/
    │   └── novel.json
    ├── cover.jpg
    ├── Volume 01 - Early Years.epub
    ├── Volume 02 - New Heights.epub
    └── Volume 03 - Beckoning Fates.epub
```

#### JSON Example (`novel.json` or `.zine/metadata.json`):
```json
{
  "title": "The Beginning After the End",
  "alt_title": "TBATE",
  "author": "TurtleMe",
  "artist": "Fuyuki23",
  "publisher": "Tapas Media",
  "serialization": "Tapas",
  "year": "2017",
  "language": "English",
  "type": "Book",
  "status": "Ongoing",
  "rating": "9.4 (180K)",
  "tags": [
    "Reincarnation",
    "Isekai",
    "Magic",
    "Fantasy",
    "Progression",
    "Action"
  ],
  "synopsis": "King Grey has unrivaled strength, wealth, and prestige in a world governed by martial ability. However, solitude lingers closely behind those with great power. Beneath the glamorous exterior of a powerful king lurks the shell of man, devoid of purpose and will.",
  "cover": "cover.jpg"
}
```

---

### 5. Book (PDF / Non-Fiction / Textbooks)

#### Structure Invariant: `Material -> File (.pdf)`
- Standard document and book representation.
- Folder-level `.zine/` or root `.json` metadata attaches directly to the document.

#### Recommended Folder Structure:
```text
Books/
└── Atomic Habits/
    ├── .zine/
    │   └── book.json
    ├── cover.jpg
    └── Atomic Habits - James Clear.pdf
```

#### JSON Example (`book.json` or `.zine/metadata.json`):
```json
{
  "book_title": "Atomic Habits: An Easy & Proven Way to Build Good Habits & Break Bad Ones",
  "author": "James Clear",
  "publisher": "Avery / Penguin Random House",
  "year": "2018",
  "language": "English",
  "pages": "320",
  "type": "Book",
  "status": "Completed",
  "rating": "9.5 (450K)",
  "genres": ["Self-Help", "Productivity", "Psychology", "Personal Development", "Habits"],
  "summary": "No matter your goals, Atomic Habits offers a proven framework for improving—every day. James Clear, one of the world's leading experts on habit formation, reveals practical strategies that will teach you exactly how to form good habits, break bad ones, and master the tiny behaviors that lead to remarkable results.",
  "cover": "cover.jpg"
}
```

---

### 6. Manga (Japanese Comics)

#### Structure Invariant: `Material -> Volume (optional) -> Chapter -> Pages`
- Structured reading with Japanese alternative names, mangaka/illustrators, and magazine serialization.
- Chapters can reside inside volume folders or directly under the series root.

#### Recommended Folder Structure:
```text
Manga/
└── Frieren - Beyond Journey's End/
    ├── .zine/
    │   └── manga.json
    ├── cover.webp
    ├── Volume 01/
    │   ├── Chapter 001/
    │   │   ├── 001.jpg
    │   │   └── 002.jpg
    │   └── Chapter 002/
    │       └── 001.jpg
    └── Volume 02/
        └── Chapter 003/
            └── 001.jpg
```

#### JSON Example (`manga.json` or `.zine/metadata.json`):
```json
{
  "title": "Frieren: Beyond Journey's End",
  "alt_title": "Sousou no Frieren",
  "native_title": "葬送のフリーレン",
  "author": "Kanehito Yamada",
  "artist": "Tsukasa Abe",
  "publisher": "Shogakukan",
  "serialization": "Weekly Shonen Sunday",
  "year": "2020",
  "language": "Japanese",
  "type": "Manga",
  "status": "Ongoing",
  "rating": "9.6 (340K)",
  "tags": [
    "Adventure",
    "Drama",
    "Fantasy",
    "Elves",
    "Magic",
    "Melancholy",
    "Shounen"
  ],
  "synopsis": "The adventure is over but life goes on for an elf mage just beginning to learn what living is all about. Elf mage Frieren and her courageous fellow adventurers have defeated the Demon King and brought peace to the land.",
  "total_chapters": 135,
  "cover": "cover.webp"
}
```

---

### 7. Manhua / Manhwa (Webtoons)

#### Structure Invariant: `Material -> Volume (optional) -> Chapter -> Vertical Strips`
- Long-strip vertical scrolling comics (Korean Manhwa / Chinese Manhua).
- Full support for Korean native titles, colorist studios, and webtoon platforms.

#### Recommended Folder Structure:
```text
Manhua/
└── Omniscient Reader's Viewpoint/
    ├── .zine/
    │   └── metadata.json
    ├── cover.jpg
    ├── Chapter 001/
    │   ├── 001.jpg
    │   └── 002.jpg
    └── Chapter 002/
        └── 001.jpg
```

#### JSON Example (`metadata.json` or `.zine/metadata.json`):
```json
{
  "title": "Omniscient Reader's Viewpoint",
  "alt_title": "Jeonjijeog Dogja Sijeom",
  "native_title": "전지적 독자 시점",
  "author": "Sing Shong",
  "artist": "Sleepy-C (REDICE STUDIO)",
  "publisher": "Naver Webtoon",
  "serialization": "Naver Series",
  "year": "2020",
  "language": "Korean",
  "type": "Manhua",
  "status": "Ongoing",
  "rating": "9.9 (610K)",
  "tags": [
    "Action",
    "Apocalypse",
    "Fantasy",
    "System",
    "Survival",
    "Psychological",
    "Constellations"
  ],
  "synopsis": "Dokja was an average office worker whose sole interest was reading his favorite web novel 'Three Ways to Survive the Apocalypse'. But when the novel suddenly becomes reality, he is the only person who knows how the world will end.",
  "total_chapters": 220,
  "cover": "cover.jpg"
}
```

---

## 4. Cover Artwork Matching Rules

When associating cover art with an item, Hwaran operates under the following strict rules:

1. **Explicit Metadata Overrides All**: If `"cover"` (or `"cover_image"`, `"poster"`, `"thumbnail"`) is declared in the JSON, Hwaran searches for that exact file first.
2. **Standard Name Hierarchy**: If not specified in JSON, Hwaran checks for image files matching these standard names:
   - `cover.jpg`, `cover.jpeg`, `cover.png`, `cover.webp`
   - `poster.jpg`, `poster.png`, `poster.webp`
   - `folder.jpg`, `folder.png`, `folder.webp`
   - `artwork.jpg`, `artwork.png`, `artwork.webp`
   - `album.jpg`, `album.png`, `album.webp` *(Music)*
   - `front.jpg`, `front.png`, `front.webp`
3. **Never Media Items**: Cover images are isolated exclusively for UI display and card presentation. They are filtered out of chapter page lists, episode lists, and music tracks.
4. **No Arbitrary Guessing**: If no cover image matches the above rules, Hwaran displays a styled category placeholder rather than picking an arbitrary inner image file.

---

## 5. In-App Synchronization & Cache Architecture

- **Automatic Read & Fallback**:
  - When opening an item's Description screen, Hwaran reads from `Material/.zine/*.json` first, then `Material/*.json`.
  - If neither exists, Hwaran checks its internal read-write cache at `/data/data/com.ballade.hwaran/files/metadata/{mangaId}.json`.
  - If no metadata file is found anywhere, default values are inferred from database records and folder names.
- **In-App Editing**:
  - Editing metadata within the app saves changes back to the original `.zine/metadata.json` or root `.json` file when storage write permissions allow.
  - Changes are always cached locally and immediately synchronized into Room database entities.
- **Tag Integration**:
  - Material Type (Tag 1) is mapped directly to `"Song"`, `"Series"`, `"Channel"`, `"Novel"`, `"Book"`, `"Manga"`, or `"Manhua"`.
  - Descriptive Tags (Tag 2) integrate with Hwaran's Master Tag vocabulary of over 5,700 curated tags across literature, manga, anime, and media taxonomies.
  - Toggling favorite status seamlessly synchronizes the `"Favorite"` tag with the JSON file and the app's Favorites collection.
