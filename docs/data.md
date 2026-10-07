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
| `progress` | Locus (`chapterIndex`, `blockIndex`, `charOffset`), `readingProgress`, `inLibrary`, `libraryTabId`, `sourceKind` (`Imported` / `Linked` / plugin) |
| `book_filters` | Per-book Local filter JSON |
| `que_items` | Queue rows (`done`, `sortOrder`) |

### Reading position writes

All position writes go through `FlowApp.progress`
([`ProgressWriter.kt`](../app/src/main/java/com/personal/flowreader/data/ProgressWriter.kt)): debounced,
app-scoped (outlives the reader), and per book a write older than the last stored one is dropped.
Sources: reader jumps (`ReaderViewModel.persist`, only after load and only once the position moved),
and every spoken TTS sentence (`FlowApp.persistSpokenPosition`, also with the reader closed).
Plugin books store the absolute ToC chapter.

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
