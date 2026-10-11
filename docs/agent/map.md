# Code map

Change X, edit Y. One section per area (ids match the area list in the private context repo).
Paths are relative to `app/src/main/java/com/personal/flowreader/`. Line numbers: [index.md](index.md).

## reader

| Change | Files | Also update |
| --- | --- | --- |
| Reader rendering, pin card | `ui/reader/ReaderScreen.kt` (large), `ReaderChrome.kt`, `ReaderHome.kt` | `docs/reader.md` |
| Margin rail (cache dots, hold-to-regenerate) | `ui/reader/ReaderRail.kt` | `docs/reader.md` |
| TTS highlight drawing, justify offsets, hit-test | `ui/reader/ReaderTextLayout.kt` | |
| Gestures / touch routing | `ui/reader/ReaderTouchPolicy.kt` (pure), `ReaderTouchGestures.kt` | `ReaderTouchPolicyTest` |
| Load, progress, filters, ToC | `ui/reader/ReaderViewModel.kt`, `ReaderBook.kt` (local/plugin book), `ReaderSessions.kt` (window build, locator), `TocOverlay.kt` | `docs/data.md` |
| Chapter loading window, stable sentence indices | `data/ChapterSource.kt`, `EpubChapterSource.kt`, `TxtChapterSource.kt`, `ReadingSession.kt`, `SentenceTable.kt`, `LocusAnchor.kt` | `ChapterSourceTest`, `ReadingSessionTest`, `SentenceTableTest` |
| Queue Contents (live, hold-to-edit) | `ui/reader/QueueTocOverlay.kt` (on `EditableRuleList` `below`/`deleteNote`), wired in `ReaderScreen` | `docs/reader.md` |

## tts-audio

| Change | Files | Also update |
| --- | --- | --- |
| Playback, queueing, prefetch, cache | `tts/TtsController.kt` (large) | `docs/tts.md` |
| PCM output, crossfade, gaps | `tts/TtsAudioEngine.kt`, `Crossfade.kt` | `CrossfadeTest` |
| Edge voices / synth | `tts/EdgeTtsClient.kt`, `EdgeHandshake.kt`; voice list `EdgeVoiceCatalog.kt` (pure), `EdgeVoiceStore.kt`, snapshot `resources/tts/edge_voices.json` | `EdgeTtsClientTest`, `EdgeVoiceCatalogTest` |
| Edge race stagger / network fallback (Wi-Fi and cellular lanes, offline wait) | `tts/RaceSchedule.kt`, `NetworkLanePolicy.kt` (pure), `NetworkMonitor.kt`, `EdgeNetwork.kt`, `TtsController.awaitNetworkRetry` | `RaceScheduleTest`, `NetworkLanePolicyTest`, `docs/tts.md` |
| Word highlight timing | `tts/WordHighlight.kt` | `WordHighlightTest` |
| Media session / background | `tts/TtsPlaybackService.kt`, `AudioKeepAlive.kt`, `UnderlayBluetoothMonitor.kt` | |
| Sentence splitting | `data/SentenceSplitter.kt`, `KoehnSentenceBreak.kt`, `SentenceLengthNormalizer.kt` | `SentenceSplitterTest` |
| Unspeakable / rejected sentences (skip, log) | `tts/SpeechText.kt` (pure), `TtsController.loop`, `EdgeContentException` in `EdgeTtsClient.kt` | `SpeechTextTest`, `docs/tts.md` |

## library-queue

| Change | Files | Also update |
| --- | --- | --- |
| EPUB/TXT ingest, covers | `data/Ingest.kt`, `EpubCover.kt`, `BookCatalog.kt` | `IngestTest` |
| Library tabs, shelves, view mode | `ui/library/LibraryViewModel.kt` (large), `LibraryScreen.kt`, `LibraryTopBar.kt` (icon tabs, Add a tab; plugin favicons `PluginTabIcons.kt`; new-chapter dots `PluginNewChapters.kt`), `data/Models.kt` (`LibraryTabId`) | `docs/library.md` |
| Library row subtitle ("Author · chapter x/y") | `ui/library/LibraryRowText.kt` (pure), Files `library_books.author` / `chapterCount` (filled in `BookCatalog.storeRowMeta`), `LibraryBookCards.kt`; plugin rows `ui/plugin/PluginTab.kt` | `LibraryRowTextTest`, `docs/library.md` |
| Share ingest de-dupe | `share/ShareInbox.kt` (pure) | `ShareInboxTest`, `docs/import-share.md` |
| Queue | `BookCatalog` (`listQue`, `reorderQue`, `markQueDone`), `ProgressDb` (`queue_items`, `queue_state`); list UI `ui/library/LibraryQueueTab.kt` (`QueTab` on `EditableRuleList`), expandable chapters `QueueChapters.kt` (opens `reader/{id}/que/{queId}?chapter=`, `ReaderQueueMode.startChapter`) | `QueueChaptersTest`, `docs/library.md` |
| Queue stream (one document in reader + TTS) | `ui/reader/QueueBook.kt` (pure composite book, locator, `QueueChange`, `remap`, `tocItems`), `QueueStreams.kt` (build/open, `QueueStreamRef`), `QueuePlayback.kt` (live owner: `queue_items` Flow, in-place append or rebuild, done marking), `ReaderQueueMode.kt` | `QueueBookTest`, `docs/tts.md` |
| Position writes (session id, per-domain row, log) | `data/ProgressWriter.kt`, `ReadingPosition.kt` (`ReadingSessionId`), `AppPositionStore.kt` (routes to Library / Queue / plugin tables), `PositionLog.kt` | `ProgressWriterTest`, `PositionLogFormatTest`, `docs/data.md` |

## plugins

| Change | Files | Also update |
| --- | --- | --- |
| Runtime / `flow.*` host API | `plugin/runtime/*` | [cross-repo.md](cross-repo.md) |
| Models, caps, versions | `plugin/api/PluginModels.kt`, `PluginManifest.kt` | [cross-repo.md](cross-repo.md) |
| Tab UI, story card, downloads, position slider, rename, sign-in | `ui/plugin/PluginTabViewModel.kt` (large), `PluginTab.kt`, `StoryMediaCard.kt`, `StoryCacheCards.kt` (`MovePositionCard`, `PositionSliderCard`), `StoryRenameCard.kt`, `AccountSheets.kt`, `WebLoginOverlay.kt` (`auth.web`; cookies `plugin/runtime/WebLoginCookies.kt`), `PluginBrowse.kt` (`PluginBrowseController`), `CreatorPage.kt` (`BrowseListPane`, `CreatorPageOverlay`) | `docs/plugins.md` |
| Chapter cache policy (cache ahead, cleanup, keep all, URL-keyed bodies) | `plugin/store/PluginSessionStore.kt` (`chapterKey`, `fetchIndices`, `pruneIndices`, `savedChapter`, pure), `TocRemap.kt` (removed-chapter fallback, `chapterMoves`, pure), `PluginBookStore.kt` (`maintainChapterCache`, `scheduleMaintain(bookId, href)`) | `PluginSessionStoreTest`, `TocRemapTest`, `docs/plugins.md` |
| Per-story user settings (rename, new-chapter count, bell, cache policy, keep all) | `plugin/store/PluginDb.kt` (`plugin_stories`, `PluginStoryDao`, migration 1 → 2), `PluginBookStore.read` (merges them into the session), `PluginCatalog.list` / `get` (rename over the plugin's title) | `docs/data.md`, `docs/plugins.md` |
| Two-way sync (lists, positions, outbox, conflicts) | `plugin/sync/` (`ListReconcile`, `ProgressReconcile` pure, `SyncState`, `TwoWaySync`); list writes `plugin/store/PluginListWriter.kt`; UI `ui/plugin/PluginSyncController.kt`, `PositionConflictCard.kt`, `AccountSheets.kt`; plugin side `readPositions`, `setMembership`, `syncProgress`, `WorkDetail.readChapterUrl` | `ListReconcileTest`, `ProgressReconcileTest`, `SyncStateTest`, `docs/plugins.md` |
| Delete plugin data | `plugin/store/PluginDataEraser.kt`, `PluginManager.eraseData`; UI `ui/plugin/PluginDataSettings.kt`, uninstall card in `ui/settings/PluginsSettingsTab.kt` | `docs/plugins.md` |
| New-chapter count + notifications | count `plugin_stories.newChapters` via `PluginBookStore.addNewChapters`/`clearNewChapters`, observed with `newChapterCounts`; `plugin/updates/` (`UpdateChecker`, `UpdateDiff` pure, `UpdateNotifier`, `UpdateScheduler`, `ChapterUpdateWorker`); bell in `MediaCardAdapters.kt` + `PluginTabViewModel.toggleNotify`; settings `ui/settings/NotificationSettings.kt`; plugin side `checkUpdates` (capability `updates`) | `UpdateDiffTest`, `docs/plugins.md` |
| Plugin version updates | `plugin/updates/PluginVersionWorker.kt`, `UpdateScheduler.applyVersionCheck`, `UpdateNotifier.postPluginUpdates`; pure `plugin/repo/PluginVersionUpdates.kt`; settings `PluginVersionCheckSection`, `SettingsStore.PluginVersionCheckPrefs` | `PluginVersionUpdatesTest`, `docs/plugins.md` |
| Repos / install, per-plugin settings | `plugin/repo/*`, `ui/settings/PluginsSettingsTab.kt` (`PluginAccountSettings`, `PluginSettingsForm`); sign-in sync `PluginManager.sessionChanges`; download defaults `ui/settings/DataSettings.kt` | `RepoIndexTest`, `docs/settings.md` |
| Media card content | `ui/design/card/media/MediaCardAdapters.kt` (pure); position slider `MediaSegmentSlider.kt` | `MediaCardAdaptersTest` |

## import-share

| Change | Files | Also update |
| --- | --- | --- |
| Share routing / parse rules | `share/ShareDomainRules.kt` (`RouterRules`, `ParseRules`), `ShareRouter.kt`; UI `ui/settings/SharingSettingsTab.kt`, `ImportRouterRules.kt`, `ImportParseRules.kt`, `ImportRuleControls.kt` | `ShareRouterTest`, `docs/import-share.md` |
| Web page parse, Next-link crawl to EPUB | `share/WebPageIngest.kt` (`ParseSelectors`, `CssList`), `HiddenContent.kt`, `WebCrawl.kt`, `EpubWriter.kt`, `HtmlParagraphs.kt` (also plugin chapters); crawl prompt `ui/library/WebCrawlCards.kt` + `LibraryViewModel.startWebImport` | `WebPageIngestTest`, `WebCrawlTest`, `HtmlParagraphsTest` |
| On-page picker | `ui/settings/PagePickerOverlay.kt` (WebView), `assets/picker/picker.js`, `share/SelectorBuilder.kt` (pure) | `SelectorBuilderTest`, `docs/import-share.md` |

## settings

| Change | Files | Also update |
| --- | --- | --- |
| A setting (new key) | `data/SettingsStore.kt` (prefs + setter), `ui/settings/SettingsState.kt`, the tab file | `docs/settings.md`, `docs/data.md` |
| Text filters (Global/Groups/Local) | `data/TextFilters.kt`; UI `ui/settings/FiltersSettings.kt`, `FilterRuleList.kt` on `EditableRuleList.kt` (shared with Router/Parser lists); TTS-only rules via `TtsController.setSpeechFilters` | `TextFiltersTest`, `docs/settings.md` |
| Layout theme (hue, saturation, lightness) | `ui/settings/ThemeSettingsPane.kt`; color math `ui/theme/Theme.kt`; `AccentLightness.scale` in `data/Models.kt` | `docs/settings.md`, `AccentLightnessTest` |
| Audio settings UI | `ui/settings/AudioSettings.kt` (tab shell), `VoiceSettingsTab.kt`, `PlaybackSettingsTab.kt`, `AudioSettingsControls.kt` | `docs/settings.md` |

## ui-system

| Change | Files | Also update |
| --- | --- | --- |
| Any UI component / token | `ui/design/**`, `ui/theme/**` | `docs/ui-system/` (rule `ui-system.mdc`) |

## brand

| Change | Files | Also update |
| --- | --- | --- |
| App icon, themed icon, splash, notification icon | Re-trace from `brand/concept/` (`brand/trace`: `npm run trace`, `npm run compare`) or edit `brand/flow-reader-mark.svg`, then `node brand/build.mjs` (writes `res/drawable/ic_launcher_*`, `ic_stat_flow`, `values/brand_colors.xml`); wiring `res/mipmap-anydpi/ic_launcher.xml`, `res/values-v31/themes.xml` | `docs/brand.md` |

## release

| Change | Files | Also update |
| --- | --- | --- |
| Version | `app/build.gradle.kts` (`versionCode`, `versionName`) | release stage in the context repo |

## shared

| Change | Files | Also update |
| --- | --- | --- |
| Room schema | `data/ProgressDb.kt` (bump version + `Migration`; schemas in `app/schemas/`), `LegacyMigration.kt` (9 to 10); plugin DBs `plugin/store/PluginDb.kt`, `PluginCatalog.kt`, `PluginStorageMigrator.kt` | `LegacyMigrationTest`, `docs/data.md` |
| App singletons, routes | [architecture.md](architecture.md) | `docs/navigation.md` |

## Large files

Read by offset from [index.md](index.md), never whole: `TtsController.kt` 2240,
`ReaderScreen.kt` 1160 (one composable), `PluginTabViewModel.kt` 1072, `LibraryViewModel.kt` 882,
`BookCatalog.kt` 517.
Single-class files can't be split by moving code; they need helper-class extraction (separate,
tested refactors).
