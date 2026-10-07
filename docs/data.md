# Data and storage

## Settings

DataStore name `flow_settings` — [`SettingsStore.kt`](../app/src/main/java/com/personal/flowreader/data/SettingsStore.kt).

Notable keys beyond Settings UI:

| Key | Purpose |
|-----|---------|
| `library_view` | List / Shelf |
| `library_tab` | Selected tab id |
| `enabled_plugins` | Comma-separated plugin ids |
| `custom_library_tabs` | JSON shelves |
| `notifications_asked` | Notification prompt flag |

Theme, TTS, filters JSON, share router/parse rules — see [settings.md](settings.md).

## Room (`flow.db`)

[`ProgressDb.kt`](../app/src/main/java/com/personal/flowreader/data/ProgressDb.kt)

| Table | Contents |
|-------|----------|
| `progress` | Locus (`chapterIndex`, `blockIndex`, `charOffset`) plus `chapterHref` and `anchorText` to re-find it, `locusVersion`, `readingProgress`, `inLibrary`, `libraryTabId`, `sourceKind` (`Imported` / `Linked` / plugin) |
| `book_filters` | Per-book Local filter JSON |
| `que_items` | Queue rows (`done`, `sortOrder`) |

### Reading position writes

All position writes go through `FlowApp.progress`
([`ProgressWriter.kt`](../app/src/main/java/com/personal/flowreader/data/ProgressWriter.kt)): debounced,
app-scoped (outlives the reader), and per book a write older than the last stored one is dropped.
Sources: reader jumps (`ReaderViewModel.persist`, only after load and only once the position moved),
and every spoken TTS sentence (`FlowApp.persistSpokenPosition`, also with the reader closed). The
reader registers a `ProgressLocator` per book that adds whole-book `readingProgress`, `chapterHref`
and a 64-character `anchorText` to each write.

Locus meaning:

- `chapterIndex` is the `ChapterSource` index: the EPUB spine entry (empty entries such as covers
  count), the TXT segment, or the plugin ToC index.
- `locusVersion` 0 rows come from the old whole-book parse, which counted only non-empty EPUB
  chapters and treated a TXT file as one chapter. They are mapped once on open
  (`ChapterSource.legacyLocus`) and rewritten as version 1.
- On open, `chapterHref` wins over `chapterIndex` when it resolves, then `anchorText` corrects the
  block and offset if the text moved (`LocusAnchor`).

### Book loading

Nothing is converted or cached per open. [`ChapterSource`](../app/src/main/java/com/personal/flowreader/data/ChapterSource.kt)
reads metadata only, then one chapter at a time:

- EPUB: container, OPF and nav/NCX; `load(i)` parses one zip entry.
- TXT: a byte scan cuts about 48 KB segments at blank lines; `load(i)` reads one byte range.
- Plugin stories: chapter cache, else fetch (`PluginBookStore.chapter`).

The reader and TTS share one app-scoped `ReadingSession` holding a contiguous window of prepared
chapters (filtered text and sentences). The window grows one adjacent chapter at a time as the
viewport or playhead nears an edge. Chapters more than one away from both are dropped. Sentence
indices are stable while the window moves (`SentenceTable`). Whole-book progress uses chapter byte
sizes (`BookMeter`), so no full parse is needed.

## Files

| Path | Contents |
|------|----------|
| `filesDir/books/{sha256}/book.epub` | Imported copies |
| Linked cache | Under cache when referencing in place |
| `filesDir/plugins/installed/{id}/` | Installed plugin `plugin.json` + `index.js` |
| `filesDir/plugins/data/{id}/` | Plugin story sessions, ToC, chapter cache, lists, settings, key-value store |
| `shared_prefs/plugin_secret_{id}.xml` | Encrypted plugin secrets and cookies (excluded from backup) |

Book ids are SHA-256 of file bytes ([`BookCatalog.kt`](../app/src/main/java/com/personal/flowreader/data/BookCatalog.kt)).

## Models

Shared types: [`Models.kt`](../app/src/main/java/com/personal/flowreader/data/Models.kt).

[Back to hub](README.md)
