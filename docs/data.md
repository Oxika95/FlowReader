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
| `plugin_cache_level`, `plugin_cache_cleanup` | Cache level and cleanup new plugin stories start with |
| `plugin_update_interval`, `plugin_update_wifi_only` | Background new-chapter check: hours between runs (0 = off, default 12), unmetered network only |

Theme, TTS, filters JSON, share router/parse rules — see [settings.md](settings.md).

## Room (`flow.db`)

[`ProgressDb.kt`](../app/src/main/java/com/personal/flowreader/data/ProgressDb.kt), schema version 10,
schemas exported to `app/schemas/`. Schema changes ship a `Migration` (data is kept); versions 1–8
and downgrades are destructive. Each domain owns its rows and positions; nothing is shared.

| Table | Contents |
|-------|----------|
| `library_books` | Files / shelf books: `bookId` (SHA-256), `title`, `storedPath`, `sourceUri`, `sourceKind` (`Imported` / `Linked`), `shelfId`, `addedAt`, `localFilters`, position |
| `queue_items` | One row per Queue entry, keyed by `queId`: its own copy of the file reference (`bookId`, `storedPath`, `sourceUri`, `sourceKind`), `sourceUrl` (web imports), `origin` (`Text` / `Web` / `File` / `Library` / `Plugin`), `sortOrder`, `done`, `doneAt`, `localFilters`, position |
| `queue_state` | Single row: `currentQueId` (last entry the Queue stream wrote a position for) |

Plugin stories live in one Room database per plugin, `plugin_<id>.db`
([`PluginDb.kt`](../app/src/main/java/com/personal/flowreader/plugin/store/PluginDb.kt), table
`plugin_books`: `bookId`, `workId`, `title`, `storedPath`, `workUrl`, `addedAt`, `localFilters`,
position). Columns are app-defined. Erasing a plugin's data deletes its database.

Every table embeds the same position columns
([`ReadingPosition.kt`](../app/src/main/java/com/personal/flowreader/data/ReadingPosition.kt)):
`chapterIndex`, `chapterHref`, `blockIndex`, `charOffset`, `anchorText`, `fraction`, plus provenance:
`positionAt` (write time), `positionSessionAt` (reading session start), `positionSource`
(`Reader` / `Tts` / `PluginSeek` / `Sync` / `Migration`).

Migration 9 → 10 ([`LegacyMigration.kt`](../app/src/main/java/com/personal/flowreader/data/LegacyMigration.kt),
pure mapper JVM-tested): library rows move to `library_books`; each old Queue row gets its own
`queue_items` row with a copy of the book's position; plugin rows go to a staging table that
`PluginStorageMigrator` drains into each installed plugin's database at startup (plugin reads wait
for it).

### Reading position writes

All position writes go through `FlowApp.progress`
([`ProgressWriter.kt`](../app/src/main/java/com/personal/flowreader/data/ProgressWriter.kt)): one
app-scoped, debounced, serialized writer. Each `ProgressUpdate` names its `ReadingSessionId`
(domain, row key, opened-at) and a row key; `AppPositionStore` writes the position columns of
exactly that row in that domain's table and nothing else. A write for a missing row is dropped.

Each reader launch opens a `ReadingSession` with its own `ReadingSessionId` and `ProgressLocator`,
so a locus is always mapped by the session it came from:

- Library / plugin books: row = `bookId`.
- Queue stream: the session's key is blank; the locator maps each locus through the stream's own
  segments to the `queId` under it. Queue writes never touch `library_books` or plugin rows.

Sources: reader jumps (`ReaderViewModel.persist`, only after load and once moved), every spoken
TTS sentence (`TtsController.spoken` carries the session that spoke it; `FlowApp.persistSpokenPosition`
writes through that session, also with the reader closed), the plugin card's position slider
(`PluginSeek`), and two-way sync (`Sync`). Plugin-domain writes call
`PluginBookStore.scheduleMaintain`, which on a chapter change syncs the site and downloads /
cleans up in its own job. A plugin story read through the Queue writes its Queue row only, so it
does not trigger downloads ahead.

### Position log

[`PositionLog.kt`](../app/src/main/java/com/personal/flowreader/data/PositionLog.kt):
`filesDir/logs/positions.log` (rotated to `.1` past 512 KB), appended to the Synth log dump. Lines
are tab-separated: every write (`POS`, source, session, `domain:row`, locus, fraction, href,
`written` / `noRow`) and events: `QUEUE_ADD` (with caller frames), `QUEUE_REMOVE`, `QUEUE_CHANGE`,
`INTENT` (share intents: action, kind, activity recreated, launched from history, extras hash),
`MIGRATE`, `MIGRATE_FAILED`.

Locus meaning:

- `chapterIndex` is the `ChapterSource` index: the EPUB spine entry (empty entries such as covers
  count), the TXT segment, or the plugin ToC index.
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
| `…/data/{id}/{work}/meta.txt` | Story session: `bookId`, `pluginId`, `workId`, `cacheLevel` (chapters ahead), `cleanup`, `pinnedRanges` (Download all only), `notify`. A file without `bookId` / `pluginId` is ignored |
| `…/data/{id}/{work}/c/{i}.txt` | Cached chapter body (title, blank line, text) |
| `shared_prefs/plugin_secret_{id}.xml` | Encrypted plugin secrets and cookies (excluded from backup) |

Book ids are SHA-256 of file bytes ([`BookCatalog.kt`](../app/src/main/java/com/personal/flowreader/data/BookCatalog.kt)).

## Models

Shared types: [`Models.kt`](../app/src/main/java/com/personal/flowreader/data/Models.kt).

[Back to hub](README.md)
