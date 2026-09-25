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

## Files

| Path | Contents |
|------|----------|
| `filesDir/books/{sha256}/book.epub` | Imported copies |
| Linked cache | Under cache when referencing in place |
| `filesDir/plugins/royalroad/` | RR session, ToC, chapters, covers |

Book ids are SHA-256 of file bytes ([`BookCatalog.kt`](../app/src/main/java/com/personal/flowreader/data/BookCatalog.kt)).

## Models

Shared types: [`Models.kt`](../app/src/main/java/com/personal/flowreader/data/Models.kt).

[Back to hub](README.md)
