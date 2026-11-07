# Registry

One entry per feature: vocabulary, owner file, files that change together, tests, rules, pitfalls.
Every source file and plugin folder must be named by an `Owner:` or `Changes with:` line;
`scripts/agent/seams-check.mjs` fails otherwise, and `scripts/registry-allow.txt` lists any exceptions
(currently none). Couplings for each entry are in `docs/agent/seams.md`.

## Import

### Import manager (share ingress to the library)
- Words: import manager, share ingress, share target, Auto Play on Share, Manual Override, chooser, hand-off
- Not the same as: Settings "Import" tab, which is the rule editor (see Import tab entries); there is no separate import job system
- Owner: share/ShareIngressActivity.kt
- Changes with: share/ShareRouter.kt, share/ShareInbox.kt, share/ShareOverlay.kt, share/ShareChooserCard.kt, share/ShareModels.kt, plugin/PluginShareRequest.kt, ui/library/LibraryViewModel.kt, data/BookCatalog.kt, MainActivity.kt
- Tests: ShareRouterTest, ShareInboxTest, SharedTextTitleTest
- Rules: .cursor/rules/share.mdc
- Pitfalls:
  - Settings "Manual Override" is `ShareAskMode.Ask`; Auto mode never shows the chooser.
  - Import execution (`executeShare`, `routeImportText`, `openExternalUri`, `add`) lives in `LibraryViewModel`, not in `share/`.
  - `ShareRouter.decide` is pure; it gets rules and a payload and returns a `ShareAction` (ToFiles, ToQueue, Plugin, Parse).
  - Shares reach the Library through `FlowApp.shareInbox`, never through Activity intents, so Android intent replay does not repeat an import.
  - `ShareIngressActivity` is `singleInstance` and reads only `EXTRA_TEXT`. EPUB/TXT files arrive by VIEW or SEND at `MainActivity`.
  - `ShareDispatch` lives in `ShareOverlay.kt`. The overlay needs display-over-other-apps; without it an in-app card is used.
  - In-app routes (clipboard import, manual add) must force `askMode = Auto`; there is no overlay context.
  - A rule can point at an uninstalled plugin or a deleted shelf. The router does not validate; the consumer reports it.
  - The book id is the SHA-256 of the file bytes, so the same bytes dedupe to one book.
  - Three "queue" ids exist: `RouterLanding` `queue`, `LibraryTabId` `que`, `QueueBook.ID` `"queue"`.
  - Books routed to Queue still get a Files row.
- Rejected: Queue-only share target next to the chooser (duplicate entry points; areas/import.md); a library-wide import spinner or job list (an import manager already exists in Settings; areas/import.md); Files as the default for domain rules or auto-routed text (text and URLs go to Queue; areas/import.md)
- Device check: with display-over-other-apps granted, share a URL from a browser in Ask mode (chooser appears, pick Queue) and in Auto mode (lands without a chooser); share a plain-text snippet; open an EPUB from the Files app with Flow Reader as the target.
- Known issues: share-1, share-2, share-3, share-4, share-5, share-9, share-10

### Web page import and crawl (WebPageIngest, WebCrawl, EpubWriter)
- Words: web import, Parse, Default parser, Custom parser, Crawl, Next link, hidden text, Desktop site
- Not the same as: plugin chapter fetching (`PluginHttp`); only `HtmlParagraphs` is shared with plugin chapter text
- Owner: share/WebPageIngest.kt
- Changes with: share/WebCrawl.kt, share/EpubWriter.kt, share/HtmlParagraphs.kt, share/HiddenContent.kt, share/SelectorBuilder.kt, ui/library/WebCrawlCards.kt, ui/library/LibraryViewModel.kt
- Tests: WebPageIngestTest, WebCrawlTest, HiddenContentTest, SelectorBuilderTest
- Rules: .cursor/rules/share.mdc
- Pitfalls:
  - Default parsing produces a plain-text book; Custom parsing and every crawl produce an EPUB.
  - `EpubWriter` uses a fixed timestamp and a name-based UUID, so a repeated crawl of the same pages yields the same bytes and the same book id.
  - `HiddenContent.strip` must run before `<style>` elements are removed.
  - Previous, Next and cover links are read before the Remove selectors run.
  - The crawl prompt and progress cards render in the Library screen only.
  - `fetchHtml` has no size cap; cover download has an 8 MB cap and a type check.
  - Parser Test in Settings and the real import are parallel paths (`ImportParseRules.runTest` and `LibraryViewModel.startWebImport`) and must stay in step.
  - Redirects: relative links resolve against the requested URL.
- Rejected: vendoring WebToEpub site parsers (GPLv3 Chrome-extension JS; Custom mirrors WebToEpub's Default Parser fields instead); phrase matching of anti-scrape notices (too much code for a weak result); extra hiding heuristics such as `font-size:0` or off-screen (user kept the core set); Plugin as a Parser option (Plugin is a router destination only). All from areas/import.md.
- Device check: share a chapter URL with a Custom parser rule and a Next selector, accept the crawl prompt, and check the EPUB opens with all chapters; run Test on the parser rule and compare against the imported text.
- Known issues: share-6

### Import tab: Router
- Words: Router, router rule, landing, Manual Override, Auto Play on Share, Interrupt Playback, domain rule
- Not the same as: the UI says "Import", the code says Sharing, DomainRule and ImportRule (`SharingSettingsTab`, `DomainRuleEditorState`, `ImportRuleEditRequest`)
- Owner: ui/settings/ImportRouterRules.kt
- Changes with: ui/settings/SharingSettingsTab.kt, ui/settings/ImportRuleControls.kt, ui/settings/SettingsOverlay.kt, share/ShareDomainRules.kt, share/ShareModels.kt, share/RouterRuleEdit.kt, data/SettingsStore.kt
- Tests: ShareRouterTest, RouterRuleEditTest
- Rules: .cursor/rules/share.mdc, .cursor/rules/settings.mdc
- Pitfalls:
  - Rules are hand-rolled JSON (`ShareJson` in `ShareDomainRules.kt`). A new rule field needs four edits: the model in `ShareModels.kt`, encode, decode, and the editor. Read new fields with defaults.
  - The stored list is read through `shareRouterRulesOnce`; an empty list reads as `RouterRules.seed()`.
  - Plugin-owned rules come from `RouterRules.withPluginHosts` and sit before the catch-all `seed-url-any` rule. List order decides which rule wins.
  - `matchSubdomains` also turns on `*` wildcards in host and path; regex path rules ignore the host pattern.
  - Landing ids are `files`, `queue`, `plugin`, or a custom tab id. Removing a shelf has to rewrite the rules that point at it.
  - `share_router_rules` has more than one writer. Every write goes through `SettingsStore.editShareRouterRules(RouterRuleEdit)` (or `seedPluginShareRules`), which applies the change to the stored list inside one DataStore edit. Never write a list the UI read earlier. The tab shows `shareRouterRulesFlow`, so it follows seeding.
  - Two `tts_*` keys appear on this tab but travel in `TtsSettingsState`.
  - Never nest `verticalScroll` in the settings card; editors are stacked `FlowFullscreenCard`s hosted by `SettingsOverlay`, not dialogs.
- Rejected: a fixed "Defaults" section separate from plugin hand-offs (merged into the one rule list; areas/import.md)
- Device check: Settings > Import > Router: add a rule, edit it, drag to reorder, delete two with multi-select; share a matching URL and confirm the new rule routes it. Install or update a plugin from Settings > Plugins and check its share rule appears.
- Known issues: settings-2, settings-3

### Import tab: Parser
- Words: Parser, parse rule, Default parser, Test, Pick, Body, Next, Remove selectors, Desktop site, Crawl
- Not the same as: plugin parsing in the plugin's own JS
- Owner: ui/settings/ImportParseRules.kt
- Changes with: ui/settings/SharingSettingsTab.kt, ui/settings/ImportRuleControls.kt, ui/settings/PagePickerOverlay.kt, ui/settings/PickPanels.kt, ui/settings/PickerFields.kt, ui/settings/SettingsOverlay.kt, share/ShareDomainRules.kt, share/ParseRuleReorder.kt, share/WebPageIngest.kt, data/SettingsStore.kt
- Tests: PickerFieldsTest, WebPageIngestTest, SelectorBuilderTest, ShareRouterTest (`DefaultParseRuleTest`)
- Rules: .cursor/rules/share.mdc, .cursor/rules/settings.mdc
- Pitfalls:
  - The Default parse rule (`seed-parse-default`, `ParseRules.DEFAULT_ID`) is protected: it can't be deleted, and the store re-wraps every list with `ParseRules.withDefault` on read and on write.
  - `withDefault` can only keep a Default rule it is given; if it gets a list without one it inserts a factory Default and the user's edits are gone. `EditableRuleList` reports only the unlocked ids on reorder, so build the saved list with `ParseRules.reordered(rules, ids)` (share/ParseRuleReorder.kt), never from `ids` alone.
  - Custom with a blank Body is documented as "use Default heuristics"; check `WebPageIngest` before relying on that.
  - Older stored rules decode with Crawl off.
  - The on-page picker is the only user of the `FlowPicker` JS bridge and `PickField`; the page it loads can call the bridge.
  - Hand-rolled JSON: a new field needs the model, encode, decode and editor edits.
- Rejected: Plugin as a Parser option (tried and reverted; areas/import.md)
- Device check: Settings > Import > Parser: open a rule, Pick a Body on a real page, press Test, save; reopen and confirm the fields persisted; edit the Default rule and confirm it survives leaving Settings.
- Known issues: share-6, settings-8

### On-page picker (PagePickerOverlay)
- Words: picker, Pick, selector, inspector, page picker
- Owner: ui/settings/PagePickerOverlay.kt
- Changes with: ui/settings/PickerFields.kt, ui/settings/PickPanels.kt, share/SelectorBuilder.kt, app/src/main/assets/picker/picker.js
- Tests: PickerFieldsTest, SelectorBuilderTest
- Rules: .cursor/rules/settings.mdc
- Pitfalls:
  - The only scrolling surface inside the settings card that scrolls itself; every other tab must not nest a scroll.
  - The JS bridge is `FlowPicker`; Kotlin calls `__flowPicker` methods (setMode, clear, highlight, narrower, wider). The pick JSON shape `{path[{tag,id,classes,nth}],text}` is read in Kotlin and written in `picker.js`.
- Rejected: none recorded
- Device check: open the picker from a parse rule, tap an element, use narrower and wider, switch to navigate mode and back.
- Known issues: settings-8

### Editable rule list (EditableRuleList)
- Words: rule list, hold to edit, drag to reorder, multi-select delete, DragHandle
- Owner: ui/settings/EditableRuleList.kt
- Changes with: ui/settings/ImportRuleControls.kt, ui/settings/FilterRuleList.kt, ui/library/LibraryQueueTab.kt, ui/reader/QueueTocOverlay.kt
- Tests: none
- Rules: .cursor/rules/settings.mdc
- Pitfalls:
  - Used by five screens: Filters, Router, Parser, the Library Queue tab and the reader's Queue ToC. Check all five after any change.
  - `DragHandle` accumulates offset inside `pointerInput`; a composition parameter read there is stale.
  - Rows the caller marks `locked` stay in place and can't be selected for delete. `onReorder` gets only the unlocked ids, so a caller with locked rows must merge them back into the saved list (Parser: `ParseRules.reordered`).
- Rejected: custom tab rows with packed gaps (replaced by the design-system tab bar; areas/settings.md)
- Device check: in Settings > Filters and Settings > Import, hold a row to enter edit mode, drag, delete; do the same in the Queue tab. In Parser, customize Default first and confirm it is unchanged after a reorder.
- Known issues: none recorded

## Settings

### Settings overlay and tab placement
- Words: Settings, settings card, About, Audio, Filters, Import, Layout, Plugins, sub-tab, breadcrumb
- Not the same as: UI "Layout" is code `Appearance` and `Chrome`; UI "Import" is code Sharing (`SharingSettingsTab`)
- Owner: ui/settings/SettingsOverlay.kt
- Changes with: ui/settings/**, data/SettingsStore.kt, data/ChromePrefs.kt
- Tests: PickerFieldsTest
- Rules: .cursor/rules/settings.mdc, .cursor/rules/ui-system.mdc
- Pitfalls:
  - Primary tabs route by integer index and `else` means Layout; Layout's sub-tabs use `else` for Font. Inserting a tab shifts every index.
  - Wiring has two patterns: state and callbacks passed down from the Library and Reader screens (Audio, Filters, Layout), and panes that read `applicationContext as FlowApp` and load their own data (Import, About data and notifications, Plugins).
  - Writes run on different scopes: `app.appScope` outlives the pane; `rememberCoroutineScope` is cancelled when the pane leaves.
  - Most values are one-time `*Once()` reads at open; only `chrome`, `pluginManager.installed` and `pluginRepos.repos` are live collectors.
  - A new Audio setting touches about seven places: key and setter, `TtsController` setter, `SettingsState.kt` (the `TtsSettingsState(TtsUiState)` and `TtsSettingsCallbacks(TtsController)` builders are the only place the screens' state and callbacks are assembled), `SettingsOverlay.kt`, `AudioSettings.kt`, and the sub-tab file. The screens need no change.
  - Sub-tab bars must go through `SettingsSubTabBar`. Tab icons must be PNG silhouettes in `drawable-nodpi`.
  - The filter editor has three entry points: Settings from Library, Settings from Reader, and the reader's selection toolbar.
  - Plugin-related settings sit in three places: About > Notifications, About > Data and Plugins.
- Rejected: splitting Audio into two primary tabs (restored as sub-tabs); Font as its own primary tab (moved under Layout); a separate library settings card (areas/settings.md)
- Device check: open Settings from the Library and from the Reader, visit every primary tab and sub-tab, change one value in each, close and reopen to confirm it persisted.
- Known issues: settings-2, settings-3, settings-4, settings-5, settings-6, settings-7, settings-8

## Plugins

### Plugin two-way sync (TwoWaySync)
- Words: sync, Sync now, two-way sync, baseline, outbox, conflict card, Replace with site lists, membership, progress sync
- Not the same as: the background new-chapter check (`UpdateChecker`), which shares the same global lock but does not sync lists or positions
- Owner: plugin/sync/TwoWaySync.kt
- Changes with: plugin/sync/**, plugin/store/PluginMembershipStore.kt, plugin/store/PluginListWriter.kt, plugin/store/PluginDataEraser.kt, ui/plugin/PluginSyncController.kt, ui/plugin/SyncReportProblem.kt, ui/plugin/PositionConflictCard.kt, ui/plugin/SyncTargets.kt
- Tests: ListReconcileTest, ProgressReconcileTest, SyncPullKindTest, SyncReportProblemTest, SyncStateTest, SyncTargetsTest
- Rules: .cursor/rules/sync.mdc, .cursor/rules/plugin-api.mdc
- Pitfalls:
  - `TwoWaySync.lock` is one global, non-reentrant `Mutex`. Holders: `run`, `ingestCrawledLists`, `pullPositions`, `UpdateChecker.run` and `PluginDataEraser.erase`. A holder that calls another holder deadlocks.
  - `tocStored` takes no sync lock because `UpdateChecker` holds it and calls it. `ProgressWriter`'s `onWritten` hook must never call `drain`.
  - The baselines in `sync.json` (`lists`, `positions`) are what both sides last agreed on. What a baseline holds at the moment of the next sync decides whether a difference counts as a local change or a site change; the order of baseline writes matters.
  - Position sync uses loose URL matching (`ProgressReconcile`); the chapter cache and `TocRemap` use exact URLs.
  - The outbox is keyed `list:{listId}:{workId}` and `pos:{workId}`; a newer op for a key replaces the older one.
  - List sync order: lists are written, then flushed, then baselines are saved, then `dropUnlisted` for stories removed from a list. Local data (library row, downloads, Queue, rename) is cleared only when the story is on none of the plugin's story lists (Follow / Favorite / Read Later, etc.). Site reading progress is never cleared by the app.
  - Local unfollow (`PluginTabViewModel.toggleList` off) and a removal discovered by sync both call `PluginListWriter.dropUnlisted`. Still on another tracked list → keep local data. Remove from library (`deleteStory`) clears local data and never changes site lists: it drops the story from the `sync.json` list baselines and outbox (`SyncState.forgetStoryLists`) so the next sync does not read the missing local membership as a removal to push. The site still lists the story, so a later sync adds it back. No hidden flag.
  - `PluginCatalog.removeMembership` does not touch list membership; it removes the library row, Queue rows and book folder.
  - All hooks on `PluginBookStore` are nullable and run inside `runCatching`, so a failing hook is silent. A store built without them does no sync and no remap.
  - Reading a plugin story through the Queue writes only the Queue row and triggers no site sync.
  - Book ids are `{bookIdPrefix}:{workId}`; sync state is keyed by workId, database rows by bookId.
- Rejected: auto-resolving a position conflict by newest or furthest (user chose a manual prompt); syncing favorites and read-later before the endpoints were verified (lifted 2026-10-09). Both from areas/plugins.md.
- Device check: with a signed-in Royal Road account, Sync now; toggle a list on one story and confirm the site changes; move the chapter on the site and back in the app and confirm the conflict card and its two choices; turn the network off, toggle a list, turn it on, Sync now.
- Known issues: sync-1, sync-2, sync-3, sync-4, sync-5

## Startup

### FlowApp startup seeding of share router rules
- Words: plugin share rules, seeded rules, plugin hosts, startup seeding, installed collect
- Not the same as: the plugin install flow in `PluginInstaller`; this entry is only what happens to share rules when the installed list changes
- Owner: FlowApp.kt
- Changes with: share/ShareDomainRules.kt, data/SettingsStore.kt, plugin/PluginManager.kt, ui/settings/SettingsOverlay.kt, ui/library/LibraryViewModel.kt
- Tests: ShareRouterTest, RouterRuleEditTest
- Rules: .cursor/rules/share.mdc
- Pitfalls:
  - `FlowApp.onCreate` launches a collector on `pluginManager.installed` that calls `SettingsStore.seedPluginShareRules`: one DataStore edit that reads the stored rules and seeded ids, runs `RouterRules.withPluginHosts`, and writes both back when they changed.
  - Seeded plugin rule ids are remembered in `seeded_plugin_share_rules` so a rule the user deleted is not re-added.
  - The collector runs in `appScope` on every emission of the installed list, including the first one at startup, after plugins load.
  - `share_router_rules` is also changed by the Import tab and by shelf removal, both through `editShareRouterRules`; see `docs/agent/seams.md` for every reader and writer.
  - `FlowApp.kt` is the shared wiring file for the whole app; most changes there belong to other entries (hooks, migrator, workers).
- Rejected: none recorded
- Device check: install a plugin that declares share hosts (or reinstall one), then open Settings > Import > Router and confirm its rule appears once; delete that rule, restart the app, and confirm it does not come back.
- Known issues: settings-2

## Reader

### Reader screen (ReaderScreen)
- Words: reader, reader screen, follow, follow scroll, scroll lock, jump back, pin card, playback pin, selection toolbar, double-tap, body text
- Not the same as: the Library "Files" splash, which is a media card opened from the Library
- Owner: ui/reader/ReaderScreen.kt
- Changes with: ui/reader/ReaderBodyList.kt, ui/reader/ReaderBodyRow.kt, ui/reader/ReaderLayout.kt, ui/reader/ReaderTextLayout.kt, ui/reader/ReaderTextToolbar.kt, ui/reader/ReaderTouchGestures.kt, ui/reader/ReaderTouchPolicy.kt, ui/open/OpenBookViewModel.kt, ui/settings/SettingsState.kt, MainActivity.kt
- Tests: ReaderTouchPolicyTest, ReaderHomeTest, ReaderProgressPolicyTest
- Rules: .cursor/rules/reader.mdc, .cursor/rules/ui-system.mdc
- Pitfalls:
  - The body is a RecyclerView with DiffUtil (`ReaderBodyController`), not a lazy list. Each row hosts its own Compose view. The list adapter's committed items can differ from the `items` the composition holds, so an adapter call needs the adapter's own index.
  - Every explicit scroll goes through `scrollToHome` (restore, follow, jump back, scroll lock, home marker, ToC, title and body double-tap). A new reason for moving the list goes through the same function.
  - The list factory captures its scroll callbacks once; values they read live must come through `rememberUpdatedState`.
  - Touch routing is `resolveTouch`; the body double-tap is handled inside `ReaderBodyRow` and does not pass through it.
  - `Locus` is a typealias of `BookPlace`. Chapter-title rows carry `blockIndex` -1.
  - The TTS settings state and callbacks are assembled by hand in both `ReaderScreen` and `LibraryScreen`; a new TTS setting needs both.
  - Positions are written by `ReaderViewModel` (`persist`) and by TTS through `FlowApp`; see "Position writes".
- Rejected: moving text on a single tap, or any scroll that aligns items to the top; "Play from current viewport"; `graphicsLayer { clip = false }` under the list fade for highlight side padding (it wiped highlights); post-slip reanchor, `edgeLoadSkip` and `justLoadedBelow` (loading must not move the camera on its own). From areas/reader.md.
- Device check: open a book, scroll, double-tap a sentence to play, let TTS run across a chapter boundary, tap the jump-back chip, lock and unlock scroll, then leave and reopen the book and confirm it resumes at the same sentence.
- Known issues: none recorded

### Reader title banner and media card
- Words: title banner, title card, media controls, media card, book card from reader, cover blur, scroll lock controls
- Not the same as: the Queue book banner, which shows Queue text instead of a book (same composables, `QueueBook.ID`)
- Owner: ui/reader/ReaderChrome.kt
- Changes with: ui/reader/ReaderBookCard.kt, ui/library/FilesBookSplash.kt, ui/plugin/StoryMediaCard.kt, ui/design/card/media/MediaCardAdapters.kt, ui/common/BookCover.kt
- Tests: MediaCardAdaptersTest
- Rules: .cursor/rules/reader.mdc, .cursor/rules/ui-system.mdc
- Pitfalls:
  - Long-press on the title opens the same media card the Library uses: `FilesBookSplash` for a local book, `StoryMediaCard` (through `PluginStoryOverlays`) for a plugin story.
  - Actions the reader cannot honor are listed in `ReaderHiddenActions`. A new host action needs routing in `FilesBookSplash` and a decision there.
  - Floating chrome sits in the reader's two `FlowDock`s; the reader does not use `FlowScreen`.
  - Cover bitmaps come from `rememberBookCover` (sidecar file first for non-EPUB books).
- Rejected: a cover underlay that pushed the title or shrank the card; a filled circle behind the back button; cover blur of 8dp or 4dp (6dp was settled). From areas/reader.md.
- Device check: long-press the title on a local book and on a plugin story; check Share, Contents and Read, and that actions the reader hides are absent.
- Known issues: none recorded

### Reader rail (LocusRail)
- Words: rail, margin rail, current-sentence bar, cache dots, hold to regenerate, sentence bar, locus rail
- Owner: ui/reader/ReaderRail.kt
- Changes with: ui/reader/ReaderBodyRow.kt, ui/reader/ReaderHome.kt, tts/TtsController.kt
- Tests: ReaderHomeTest
- Rules: .cursor/rules/reader.mdc
- Pitfalls:
  - The rail reports the current sentence's span (`SentenceSpan`) back to the screen; home and pin-edge geometry use it.
  - Cache dots reflect the TTS clip cache; a 3 second hold on the bar forces that clip to regenerate.
  - The rail sits in a fixed gutter shared with highlight padding (`ReaderLayout.kt` constants); changing the width moves the text column.
  - Rail segments are equal-weight per sentence, not weighted by character count.
- Rejected: rail segments weighted by character count; semi-transparent bars and alpha feathering (stacked at overlaps; the feather blends into the opaque page background instead); red, purple or blue zone colors and hatch patterns for cache bars (accent plus dots); a pulse on the current bar. From areas/reader.md.
- Device check: play a book with Edge TTS and watch cache dots fill ahead; hold a bar for 3 seconds and confirm the clip regenerates.
- Known issues: none recorded

### Reader home line (home marker)
- Words: home line, home marker, home position, anchor line, where the current sentence sits, jump to home
- Owner: ui/reader/ReaderHome.kt
- Changes with: ui/reader/HomeMarker.kt, ui/reader/ReaderScreen.kt, ui/settings/AppearanceSettings.kt, data/SettingsStore.kt
- Tests: ReaderHomeTest
- Rules: .cursor/rules/reader.mdc, .cursor/rules/settings.mdc
- Pitfalls:
  - `ReaderHome` is pure math (home pixel, edge, index lookup) and is tested; keep new geometry there.
  - Settings keys are `home_position` (a fraction of the viewport height) and `show_home_marker`; the marker bar writes the position back through a callback.
  - Follow, restore, jump back and ToC all anchor a sentence at the home line, so a change to home math moves all of them.
- Rejected: absolute pin insets of 128/132dp and `Modifier.offset` stacking (left a hole; the media card jumped). From areas/reader.md.
- Device check: Settings > Layout > Reading: drag the home position, toggle the marker, then play and confirm the spoken sentence sits on the line.
- Known issues: none recorded

### Reader contents (ToC)
- Words: Contents, ToC, table of contents, chapter list, Queue Contents
- Owner: ui/reader/TocOverlay.kt
- Changes with: ui/reader/ReaderBook.kt, ui/reader/QueueTocOverlay.kt, ui/reader/ReaderViewModel.kt, ui/reader/ReaderScreen.kt
- Tests: QueueBookTest
- Rules: .cursor/rules/reader.mdc, .cursor/rules/ui-system.mdc
- Pitfalls:
  - `ReaderBook` hides whether the book is local or a plugin story; plugin ToC rows come from the stored ToC, not the site.
  - A ToC jump goes through `ReaderViewModel.jumpToChapter`, then a scroll to home.
  - The Queue has its own Contents card (`QueueTocOverlay`, built on `EditableRuleList`); it is live and editable.
- Rejected: none recorded
- Device check: open Contents in a local book, a plugin story and the Queue; jump to a chapter far from the current one and confirm the text lands there.
- Known issues: none recorded

### Reader loading, chapter window and resume
- Words: chapter window, resume, locus anchor, reading session, opening a book, prepare chapters
- Owner: ui/reader/ReaderViewModel.kt
- Changes with: ui/reader/ReaderSessions.kt, ui/reader/ReaderProgressPolicy.kt, ui/open/OpenBookViewModel.kt, data/ReadingSession.kt, data/ResumeLocus.kt, data/LocusAnchor.kt
- Tests: ReadingSessionTest, ResumeLocusTest, LocusAnchorTest, ReaderProgressPolicyTest
- Rules: .cursor/rules/reader.mdc, .cursor/rules/data.mdc
- Pitfalls:
  - `ReadingSession` is a window of loaded chapters shared by the reader and TTS; both move its focus and the window trims to one contiguous range around the foci.
  - Opening a book attaches TTS, which loads the chapters on either side by itself.
  - Navigation arguments: `bookId`, `queId`, and `chapter` (removed from the saved state once read; -1 means none). In Queue mode `bookId` is `"queue"` from Auto Play but a real book id from the Library.
  - `progress.drain()` runs before the saved row is read when opening; Queue mode does not drain.
  - Saved offsets index the text after visual filters; changing the extractor, splitter or filters moves saved positions.
  - `LocusAnchor` re-finds a position by text within the saved chapter only.
  - `FilterPreviewMode` lives in this file but is used by Settings and the Library.
- Rejected: none recorded
- Device check: open a book, read two chapters, force-stop the app, reopen and confirm the position; open an EPUB, a TXT and a plugin story.
- Known issues: none recorded

### Queue stream in the reader (QueueBook)
- Words: Queue, Queue book, Queue stream, Queue playback, que, queue item done, Queue Contents
- Not the same as: the Library Queue tab, which lists rows (see "Queue tab and chapters")
- Owner: ui/reader/QueueBook.kt
- Changes with: ui/reader/QueueStreams.kt, ui/reader/QueuePlayback.kt, ui/reader/ReaderQueueMode.kt, ui/reader/ReaderViewModel.kt, data/BookCatalog.kt, data/ProgressDb.kt
- Tests: QueueBookTest
- Rules: .cursor/rules/reader.mdc, .cursor/rules/data.mdc
- Pitfalls:
  - The Queue is one composite document for both the reader and TTS; block ids change when items are reordered.
  - `QueuePlayback` is app-scoped (created in `FlowApp`); it follows the `queue_items` table, appends in place or rebuilds, and marks an item done when playback moves past it.
  - Speech filters are set before `tts.attach`; `queue.attach` comes after it because it reads the TTS sentence.
  - `QueueStreams` and `ReaderViewModel.openBook` are two ways of opening a book; keep side effects in step.
  - `QueuePlayback.updates` is a flow consumed by `ReaderViewModel`; see seams.
- Rejected: "Queue from start" and Send to Queue from plugin UI (areas/plugins.md)
- Device check: add three items to the Queue, play across item boundaries, reorder while playing, delete the current item, and check Contents updates live.
- Known issues: none recorded

## TTS

### TTS controller and playback loop
- Words: TTS, playback, play, pause, attach, prefetch, clip, cache, spoken position, skip sentence, book finished
- Owner: tts/TtsController.kt
- Changes with: tts/TtsModels.kt, tts/SpeechText.kt, tts/SynthDebugLog.kt, ui/reader/ReaderViewModel.kt, ui/library/LibraryViewModel.kt, ui/library/NowPlayingCard.kt
- Tests: SpeechTextTest, FocusResumeTest, PendingRemainderMatchTest, TtsEngineIdsTest
- Rules: .cursor/rules/tts.mdc
- Pitfalls:
  - `TtsController` is one very large class. Find members with `rg -n` and read by offset.
  - Order for a book: set speech filters, then `attach`. The clip cache key is computed at attach time.
  - `attach` for a different book stops the old one. Queue mode attaches the Queue as one book.
  - `play` sets `playing` asynchronously but `jumpTo` updates state at once; the reader's one-shot follow flag depends on that order.
  - `spoken` is the position stream: only `FlowApp` collects it and writes positions. `bookFinished` has two collectors (`FlowApp` and `QueuePlayback`).
  - Clip files, their names and the sweep belong to the TTS clip cache entry below; the controller only decides when to sweep or clear and cancels the jobs and decoded audio that fed removed files. A new splitter setting must still clear the cache (`invalidateEdgeCache`).
  - A sentence with nothing speakable after TTS-only filters is skipped, not sent to a provider.
  - Playback errors are logged to the synth log and force debug on; they never show on the media card.
- Rejected: MediaPlayer per clip for the Edge path (hard gaps, no overlap); a fixed 1 second hedge delay or absolute stall threshold (first audio takes 1 to 2.7 seconds on a phone, so every clip hedged). From areas/tts.md.
- Device check: play with System and Edge engines, skip forward and back, change voice during playback, background the app, and let a book end.
- Known issues: none recorded

### TTS clip cache
- Words: clip cache, cache hit, cache sweep, sidecar, word-timing file, `.part`, clip file name, stale clip
- Owner: tts/TtsClipCache.kt
- Changes with: tts/ClipCacheNames.kt, tts/TtsController.kt
- Tests: TtsClipCacheTest, ClipCacheNamesTest
- Rules: .cursor/rules/tts.mdc
- Pitfalls:
  - `TtsClipCache` owns the `cacheDir/tts` folder: `hit` (engine extension, then `mp3`, then `wav`), `wordsFile`, `writeAtomically` (temp file then rename), `sweep` and `clear`. `ClipCacheNames` owns the pure parts: `bookKey`, `voiceKey`, `base`, `parse`, `isStale`. Nothing else builds or parses these names; `rules-check` rule `clip-cache` fails on `ClipCacheNames.parse` / `isStale` calls, or on the word-timing and temp-file name suffixes written as string literals, outside those two files.
  - A clip name is `b<bookKey>_s<index>_<voiceKey>_t<textKey>.<ext>`. `textKey` fingerprints the sentence text, so a clip is only reused for the text it was made from. Speech filters are part of `voiceKey`.
  - Word-timing sidecars share the clip's base name and are swept with it. In-flight `.part` files of the attached book are never swept.
  - `sweep` only deletes files. The caller passes `onRemove` to cancel the synthesis job and drop decoded audio for each file (`TtsController.deleteStaleCache`). `clear` is the controller's `clearEdgeCache`, which also resets job and UI state first.
  - The folder also holds `filter_preview.mp3` and the `sys-synth` subfolder; a sweep removes the preview, `clear` removes everything directly in it.
  - Readiness for the rail (`scanReadySentenceIndices`) and the prune window (`cacheWindow`) stay in the controller because they depend on prefetch settings and playhead.
- Rejected: none recorded
- Device check: play with an Edge voice until cache dots fill, change Clip size, replay, and confirm the sentence heard matches the highlight and no stale clips remain.
- Known issues: a clip is not swept when only the voice differs and the window has not moved (see backlog tts-1 notes).

### TTS engines and providers
- Words: engine, voice, System TTS, Edge, voice list, voice gender, provider, clip audio
- Owner: tts/provider/TtsProviderRegistry.kt
- Changes with: tts/provider/TtsProvider.kt, tts/provider/SystemTtsProvider.kt, tts/provider/SystemTtsEngine.kt, tts/provider/EngineSlot.kt, tts/provider/PluginTtsProvider.kt, tts/TtsVoicePick.kt, tts/VoiceGender.kt, plugin/PluginSource.kt
- Tests: TtsVoicePickTest, VoiceGenderTest, TtsEngineIdsTest, EngineSlotTest
- Rules: .cursor/rules/tts.mdc, .cursor/rules/plugin-api.mdc
- Pitfalls:
  - Engine ids are `system` and `tts:<pluginId>`; the legacy `edge` setting value maps to the Edge TTS plugin.
  - The registry is rebuilt on `refreshEngines`; a provider built earlier keeps its own state.
  - The Edge TTS plugin id is hard-coded in the app (settings default and registry). Never rename the plugin.
  - System and plugin providers both map the speed and pitch settings to engine-specific rates.
  - `SystemTtsEngine` owns the one Android `TextToSpeech`: bind, rebind, `stop`, `shutdown` and the single utterance bridge (`utterance`, used by `speakSystem` and `SystemTtsProvider.synthesize`). Everything that touches the engine runs inside `use`, under one lock (`EngineSlot`); `shutdown` and a rebind to another engine package wait for the running utterance. `stop` never blocks and is safe from any thread. An outside `stop` ends the running utterance as a cancellation (`onStop`). `TtsController` keeps no `TextToSpeech` field.
- Rejected: none recorded
- Device check: Settings > Audio > Voice: switch engines, pick a voice, play the sample, and confirm the voice persists after restart.
- Known issues: none recorded

### Edge TTS plugin and raw WebSocket
- Words: Edge TTS, edgetts, handshake, GEC, Readest, raw socket, WebSocket upgrade
- Owner: plugin/runtime/PluginWs.kt
- Changes with: plugin/runtime/EdgeAudioFrames.kt, ../flow-reader-plugins/EdgeTts, tts/provider/PluginTtsProvider.kt
- Tests: EdgeAudioFramesTest
- Rules: .cursor/rules/tts.mdc, ../flow-reader-plugins/.cursor/rules/edgetts.mdc
- Pitfalls:
  - Readest's `edgeTTS.ts` is the upstream for the handshake and headers; do not invent a parallel one.
  - The WebSocket must be a raw TLS socket speaking RFC 6455 (`PluginWs`); an OkHttp upgrade gets HTTP 200 back.
  - Frame parsing for audio and word boundaries is `EdgeAudioFrames`; the plugin returns clip audio and boundaries to `PluginTtsProvider`.
- Rejected: OkHttp `newWebSocket` for the Edge speech socket; keeping `speech.platform.bing.com` as a fallback host (it rejects every handshake). From areas/tts.md.
- Device check: with Edge selected, play a sentence on Wi-Fi; turn the network off mid-clip and confirm playback waits and retries.
- Known issues: none recorded

### Audio output and tonal underlay
- Words: audio engine, crossfade, sentence gap, tonal underlay, keep alive, Bluetooth underlay, Auto Off On, white noise
- Owner: tts/TtsAudioEngine.kt
- Changes with: tts/Crossfade.kt, tts/AudioKeepAlive.kt, tts/UnderlayBluetoothMonitor.kt, tts/UnderlayRoute.kt, data/TonalUnderlay.kt, ui/settings/AudioSettingsControls.kt
- Tests: CrossfadeTest, UnderlayRouteTest, TonalUnderlayTest
- Rules: .cursor/rules/tts.mdc
- Pitfalls:
  - Edge playback is one `TtsAudioEngine` (AudioTrack, decode-ahead). Sentence offset: negative is an equal-power crossfade, zero is gapless, positive is written silence.
  - The underlay is a separate quiet white-noise track (`AudioKeepAlive`), levels in dBFS; mode `Off`, `Device` (Auto) or `Always`.
  - Auto relies on routing events (play, track routing, audio device callback, Bluetooth broadcasts), not polling; `targetActive` carries the result.
  - Route listeners must be set before new tracks are created.
- Rejected: an 18 kHz sine underlay (inaudible and A2DP may filter it); a low-Hz tone underlay; a 2 second route poll for Auto; a positive dB or SPL underlay scale; M3 `SliderDefaults.CenteredTrack` for center-origin sliders (not in the BOM); switching the highlight at the crossfade midpoint. From areas/tts.md.
- Device check: Settings > Audio > Playback: set the underlay to Auto with a Bluetooth device, play to that device and to the speaker; change the sentence offset below, at and above zero.
- Known issues: none recorded

### Edge network lanes
- Words: network lane, race, hedge, mobile data fallback, offline wait, CellFirst
- Owner: tts/EdgeNetwork.kt
- Changes with: tts/NetworkMonitor.kt, tts/NetworkLanePolicy.kt, tts/RaceSchedule.kt, tts/TtsModels.kt
- Tests: NetworkLanePolicyTest, RaceScheduleTest
- Rules: .cursor/rules/tts.mdc
- Pitfalls:
  - `RaceSchedule` and `NetworkLanePolicy` are pure; keep timing choices there.
  - `NetworkMonitor.changes` is a counter the playhead waits on after a network failure.
  - The setting is `tts_mobile_data_fallback`; the Audio settings screen shows lane controls only when `showNetworkLanes` is set.
- Rejected: RSSI-only network switching (signal bars mispredict; outcomes lead, RSSI only arms overlap). From areas/tts.md.
- Device check: with Wi-Fi on and mobile data fallback on, play; switch Wi-Fi off and confirm playback continues or waits and resumes.
- Known issues: none recorded

### Media session and playback service
- Words: media notification, media session, foreground service, audio focus, headset unplug, notification permission
- Owner: tts/TtsPlaybackService.kt
- Changes with: tts/FocusResume.kt, tts/TtsController.kt, FlowApp.kt, MainActivity.kt
- Tests: FocusResumeTest
- Rules: .cursor/rules/tts.mdc
- Pitfalls:
  - Notification buttons are service intent actions (START, STOP, PLAY, PAUSE, NEXT, PREV); channel `flow_tts_playback`. See seams.
  - `FocusResume` is pure: a transient loss arms a resume that applies only if the gain comes within five minutes; a user pause, stop or attach disarms it.
  - Notification permission is asked on first play through the activity-registered asker on `FlowApp`.
- Rejected: none recorded
- Device check: play with the screen off, use the notification buttons, take a call or start a timer and confirm resume, unplug headphones.
- Known issues: none recorded

### Sentence splitting and word highlight
- Words: sentence, split, clip size, size leeway, Koehn, highlight, word highlight, sentence table
- Owner: data/SentenceSplitter.kt
- Changes with: data/KoehnSentenceBreak.kt, data/SentenceLengthNormalizer.kt, data/SentenceTable.kt, tts/WordHighlight.kt, ui/reader/ReaderTextLayout.kt
- Tests: SentenceSplitterTest, SentenceTableTest, WordHighlightTest
- Rules: .cursor/rules/tts.mdc, .cursor/rules/data.mdc
- Pitfalls:
  - Splitting then length normalizing (join short, split long toward the clip size setting); a changed setting re-splits and must clear the Edge cache.
  - Sentence indices are relative to the origin chapter of the window; a re-split renumbers all of them.
  - `Sentence.text` must equal the block text from `start` to `end`; word highlight and body rows rely on it.
  - Sentence highlight shows for every engine; word highlight only when boundaries exist.
- Rejected: the full upstream `en.txt` abbreviation list (blocked too many real breaks); dual highlights. From areas/tts.md.
- Device check: change Clip size and Size leeway, replay, and confirm sentence boundaries and highlight still line up; check a line with "Mr." or "e.g.".
- Known issues: none recorded

## Library

### Library screen and tabs
- Words: Library, Files, Queue tab, shelf, custom tab, Add a tab, plugin tab, tab icons, view mode, list or grid, now playing
- Owner: ui/library/LibraryScreen.kt
- Changes with: ui/library/LibraryViewModel.kt, ui/library/LibraryTopBar.kt, ui/library/LibraryTabIcons.kt, ui/library/PluginTabIcons.kt, ui/library/PluginNewChapters.kt, ui/library/NowPlayingCard.kt, ui/library/LibraryBookCards.kt, ui/library/LibraryRowText.kt, ui/library/FilesBookSplash.kt, library/plugin/LibraryPluginActions.kt, data/ChromePrefs.kt
- Tests: LibraryTabIdTest, LibraryRowTextTest
- Rules: .cursor/rules/library.mdc, .cursor/rules/ui-system.mdc
- Pitfalls:
  - Tab ids: `LibraryTabId` (`files`, `que`, a plugin id, a custom tab id). A shelf id is also a `RouterLanding` and `library_books.shelfId`; see seams.
  - Removing a shelf rewrites the router rules, the saved tab and the sort entry that point at it.
  - `LibraryViewModel` implements `LibraryPluginActions` for the plugin tab; plugin overlays are hoisted to the Library root.
  - Library settings are the shared Settings overlay; there is no separate library settings card.
  - Row subtitles ("Author · chapter x/y") are pure (`LibraryRowText`); data fills `author` and `chapterCount` at ingest or on start.
- Rejected: PDF support (reflow breaks lines and running heads; a page viewer would be a second reader); a "smart" clipboard title parser (the first 32 characters are used); a floating title card for the Library header; status-bar-sized side gutters. From areas/library.md.
- Device check: switch every tab, add a custom tab, remove it, toggle list and grid, tap a book card for its media card, double-tap to read, and start TTS to see the now-playing card.
- Known issues: none recorded

### Library sort
- Words: sort, sort circle, Sort card, Updates order, per-tab sort
- Owner: ui/library/LibrarySort.kt
- Changes with: ui/library/LibrarySortBar.kt, ui/library/LibraryScreen.kt, data/SettingsStore.kt
- Tests: LibrarySortTest
- Rules: .cursor/rules/library.mdc
- Pitfalls:
  - Sort is stored per tab in `library_sort` as `tabKey=Mode` pairs; a plugin's sort uses the plugin id as the tab key.
  - The sort button re-derives `FlowScreen`'s FAB padding; change both together.
- Rejected: none recorded
- Device check: change sort on Files, a shelf and a plugin list; restart and confirm each remembered its own.
- Known issues: none recorded

### Queue tab and chapters
- Words: Queue tab, que, reorder, hold to edit, expandable chapters, mark done
- Owner: ui/library/LibraryQueueTab.kt
- Changes with: ui/library/QueueChapters.kt, ui/settings/EditableRuleList.kt, data/BookCatalog.kt, data/ProgressDb.kt
- Tests: QueueChaptersTest
- Rules: .cursor/rules/library.mdc
- Pitfalls:
  - Rows are `queue_items`; a Queue position write also sets `queue_state`.
  - Opening a chapter navigates to `reader/{bookId}/que/{queId}?chapter=` (Int, default -1).
  - The tab is built on `EditableRuleList`; see its entry for the five callers.
  - Removing a plugin membership deletes that story's Queue rows.
- Rejected: none recorded
- Device check: add two books and a plugin story, expand chapters, open one at a chapter, reorder, multi-delete.
- Known issues: none recorded

### Book files, covers and ingest
- Words: add book, EPUB, TXT, cover, import file, linked file, chapter source, book bytes
- Owner: data/Ingest.kt
- Changes with: data/ChapterSource.kt, data/EpubChapterSource.kt, data/TxtChapterSource.kt, data/EpubCover.kt, data/ScaledBitmap.kt, data/BookCatalog.kt, ui/common/BookCover.kt, ui/open/OpenBookViewModel.kt
- Tests: IngestTest, ChapterSourceTest, BookBytesTest, SharedTextTitleTest, ScaledBitmapTest
- Rules: .cursor/rules/data.mdc
- Pitfalls:
  - Book id is the SHA-256 of the file bytes; never change the derivation (it keys progress, filters and the Queue).
  - TXT "chapters" are fixed-size byte segments; changing the segment size moves saved positions.
  - A Linked book keeps its first id after the file changes.
  - Cover lookup is split: `BookCover.kt` for the UI, `EpubCover` for extraction and the media notification.
  - `ScaledBitmap` owns every size-capped image decode (file or bytes, power-of-two subsampling). Covers (`BookCover`, `EpubCover`, remote covers), plugin tab icons and the chapter notification large icon all call it; don't add another `BitmapFactory` call with its own sample loop.
  - `chapterCount` 0 means metadata not filled yet; it is refilled at app start.
- Rejected: none recorded
- Device check: import an EPUB and a TXT from Files, reopen both, check the cover appears on the card and the banner.
- Known issues: none recorded

## Plugin UI

### Plugin tab (PluginTab)
- Words: plugin tab, Follow, Favorite, Read Later, list sub-tab, Search, Account, pull to refresh, new chapters dot
- Owner: ui/plugin/PluginTabViewModel.kt
- Changes with: ui/plugin/PluginTab.kt, ui/plugin/PluginSyncController.kt, ui/plugin/PositionConflictCard.kt, ui/plugin/SyncTargets.kt, ui/library/PluginNewChapters.kt, library/plugin/LibraryPluginActions.kt
- Tests: SyncTargetsTest, GroupedRowsTest, CrawlCountTest
- Rules: .cursor/rules/plugin-ui.mdc, .cursor/rules/plugin-api.mdc
- Pitfalls:
  - `PluginTabViewModel` is very large and keyed `plugin-tab:{id}:{version}`; up to three instances of it can exist per plugin (Library, reader card, Settings). They share only `PluginManager.sessionChanges` and `TwoWaySync.changes`.
  - Book ids are `{prefix}:{workId}`; build them through `PluginManager.bookIdFor`.
  - Position writes go through `ProgressWriter` with the catalog row existing first; never write a plugin position table directly.
  - Tap on a story opens its media card; double-tap opens the reader.
  - Modal overlays inside a tab sit below the tab bar; hoist them to the Library root.
- Rejected: an RR tab as a browse page with chips, or an on-tab search bar; equal-width tab slots with ellipsized labels (labels must not truncate). From areas/plugins.md.
- Device check: open a plugin tab, switch list sub-tabs, pull to refresh, toggle a list on a story, read a chapter, then return and check the "Chapter n/total" chip.
- Known issues: none recorded

### Plugin story cards (media card, manage, position, rename)
- Words: story card, Manage, Download, Download All, Partial Download, cache bar, position slider, rename, Remove from library
- Owner: ui/plugin/StoryMediaCard.kt
- Changes with: ui/plugin/StoryCacheCards.kt, ui/plugin/CacheLevelDraft.kt, ui/plugin/StoryRenameCard.kt, ui/plugin/WorkCard.kt, ui/design/card/media/MediaCardAdapters.kt, ui/plugin/PluginTabViewModel.kt
- Tests: MediaCardAdaptersTest, PluginCardTest, CacheLevelDraftTest
- Rules: .cursor/rules/plugin-ui.mdc, .cursor/rules/plugin-api.mdc
- Pitfalls:
  - The card content is a `MediaCardModel` from the pure `PluginMediaCardAdapter`; do not add `FlowMediaCard` parameters for a new source.
  - Footer is Download, Manage, plugin actions, Read. Download tap: keep-all downloads everything, otherwise partial from the saved position; a running download offers Cancel.
  - Hold on the cache bar opens the position slider card.
  - The Manage cache level field saves on Done, focus loss or closing the card (`CacheLevelDraft`), never per keystroke: saving "1" while typing "15" pruned chapters with cleanup on.
  - `PluginStoryOverlays` is shared with the reader; hide actions the reader cannot do through `ReaderHiddenActions`.
- Rejected: a Download menu sheet with a Partial split button and cog panel; a separate "Cache window" button or prefetch and keep-behind fields; a separate partial-download start field (start is always the saved position); double tap on the cache bar; a rail rename pencil (inline title edit instead); bulk-downloading chapter bodies on Follow. From areas/plugins.md.
- Device check: tap a story, Download, Manage (All and Partial), rename inline, hold the cache bar, remove from library.
- Known issues: none recorded

### Plugin account, sign-in and bot check
- Words: Account, sign in, web sign-in, cookies, bot check, Anubis, clearance, Open site, site browser
- Owner: ui/plugin/AccountSheets.kt
- Changes with: ui/plugin/WebLoginOverlay.kt, ui/plugin/SiteWebViewCard.kt, ui/plugin/ClearanceOverlay.kt, plugin/runtime/PluginClearanceGate.kt, plugin/runtime/AnubisCookies.kt, plugin/runtime/AnubisDetector.kt, plugin/runtime/ClearanceOnlyCookieJar.kt, plugin/runtime/WebLoginCookies.kt, plugin/runtime/PluginSessionSeed.kt, plugin/runtime/PluginSecrets.kt
- Tests: AnubisCookiesTest, AnubisDetectorTest, ClearanceOnlyCookieJarTest, WebLoginCookiesTest, PluginSessionSeedTest
- Rules: .cursor/rules/plugin-ui.mdc, .cursor/rules/plugin-api.mdc
- Pitfalls:
  - The plugin never sees a password: sign-in is the site's own page in a WebView, completed when the manifest's `doneCookie` appears.
  - Completion order: import cookies, stamp `cookiesImportedAt`, call `session()`, emit `sessionChanged`.
  - The Android `CookieManager` is process-wide: the sign-in view clears it before loading and seeds from the plugin's jar; the bot-check solver clears Anubis cookies when it starts.
  - `PluginClearanceGate` is a global handler set by `PluginClearanceHost`; a request without a host waits.
  - Plugin secrets are encrypted and are deleted and recreated if they fail to decrypt after a restore.
  - An auth error from any story action closes Account and opens the sign-in card.
- Rejected: none recorded
- Device check: sign in to a plugin, sign out, open Account > Open site, and trigger a bot check on a protected page.
- Known issues: none recorded

### Creator page, browse and search
- Words: creator page, Memberships, browse list, add by link, search, Add from source, sort chips
- Owner: ui/plugin/CreatorPage.kt
- Changes with: ui/plugin/PluginBrowse.kt, ui/plugin/AddFromSource.kt, ui/plugin/WorkCard.kt, ui/plugin/PluginListCrawlCard.kt, ui/common/BookCover.kt
- Tests: GroupedRowsTest, SiteOpenUrlTest
- Rules: .cursor/rules/plugin-ui.mdc
- Pitfalls:
  - File names do not match contents: the search pane is in `AddFromSource.kt`; `BrowseListPane` is in `CreatorPage.kt`.
  - `PluginBrowseController` drives creator-page tabs and paging through the plugin's `browse`.
  - Remote covers use `rememberRemoteCover`, a separate client from the plugin HTTP stack.
- Rejected: none recorded
- Device check: on Patreon open a creator from Memberships, switch tabs, load more, add a story by link, and search.
- Known issues: none recorded

### Plugin settings and data panes
- Words: plugin settings, per-plugin settings form, delete data, erase plugin data
- Owner: ui/plugin/PluginSettingsSheet.kt
- Changes with: ui/plugin/PluginDataSettings.kt, plugin/store/PluginDataEraser.kt, ui/settings/PluginsSettingsTab.kt
- Rules: .cursor/rules/plugin-ui.mdc, .cursor/rules/settings.mdc
- Tests: none
- Pitfalls:
  - The form is generated from the plugin manifest `settings`; saved values override the manifest default.
  - Erase goes through `PluginDataEraser` and takes the sync lock; see "Plugin two-way sync".
- Rejected: none recorded
- Device check: change a plugin setting, reopen Settings and confirm it saved; delete a plugin's data and confirm stories and sign-in are gone.
- Known issues: none recorded

## Plugin runtime and store

### Plugin host runtime (flow.* API)
- Words: plugin runtime, QuickJS, flow.fetch, flow.html, flow.storage, flow.secrets, host API, allowedHosts, request interval
- Not the same as: the plugin UI (cards, tabs), which the app draws from JSON the plugin returns
- Owner: plugin/runtime/JsPluginRuntime.kt
- Changes with: plugin/runtime/PluginHttp.kt, plugin/runtime/PluginKvStore.kt, plugin/runtime/HtmlHandles.kt, plugin/runtime/PluginResponseProbe.kt, plugin/api/PluginManifest.kt, plugin/api/PluginModels.kt, plugin/PluginSource.kt, ../flow-reader-plugins/test/host.mjs
- Tests: PluginApiVersionTest, PluginResponseProbeTest
- Rules: .cursor/rules/plugin-api.mdc
- Pitfalls:
  - One QuickJS context per plugin on its own thread; calls are serialized and cross the boundary as JSON strings. HTML handles are cleared after every call.
  - Any `flow.*` change starts in the app and is mirrored in the plugins repo's Node host (`test/host.mjs`); follow `docs/agent/cross-repo.md`.
  - Alpha: only the current `apiVersion` is supported; a bump means every plugin is republished.
  - Requests may only go to the manifest's `allowedHosts`, at no faster than `minRequestIntervalMs`.
  - `flow.storage` is one DataStore per plugin file; `PluginSecrets` is encrypted and excluded from backup.
  - Plugin ids and `bookIdPrefix` are never changed after publishing; they key books, data and sign-in.
- Rejected: Mihon/Tachiyomi extension APKs and downloaded dex bundles (install prompts per update, host-process privileges, a Gradle build per plugin); androidx.javascriptengine (no host callback bridge for fetch); compile-time Kotlin plugins with plugin-supplied Compose UI (replaced by JS plus app-rendered UI). From areas/plugins.md.
- Device check: install a plugin from a repo, open its tab, search, open a story and read a chapter; turn on the Plugins debug channel and confirm the response probes appear.
- Known issues: none recorded

### Plugin store, catalog and chapter cache
- Words: plugin catalog, plugin database, story row, chapter cache, cache level, keep all, ToC remap, plugin book store
- Owner: plugin/store/PluginCatalog.kt
- Changes with: plugin/store/PluginBookStore.kt, plugin/store/PluginDb.kt, plugin/store/PluginSessionStore.kt, plugin/store/PluginStorageMigrator.kt, plugin/store/TocRemap.kt, AppPositionStore.kt
- Tests: PluginSessionStoreTest, PluginMembershipStoreTest, PluginChapterBlocksTest, TocRemapTest
- Rules: .cursor/rules/plugin-api.mdc, .cursor/rules/data.mdc
- Pitfalls:
  - Each plugin has its own Room database (`plugin_<id>.db`); every catalog call waits for `markMigrated` so the 9-to-10 staging rows move first.
  - `PluginBookStore` hooks (`positionChanged`, `tocStored`, `tocRemapped`) are nullable and wrapped in `runCatching`; a failure is silent.
  - Cache level N means chapters ahead of the saved position only; cleanup (default off) removes chapters more than N behind; keep-all disables cleanup. Bodies are keyed by chapter URL.
  - Chapter fetches are serialized per book, so the reader, TTS and cache upkeep share one fetch per chapter.
  - `ProgressWriter.write` drops updates for books without a progress row; create the catalog row first.
  - Chapter text becomes blocks through `HtmlParagraphs`; each chapter opens with its title as a heading.
- Rejected: syncing favorites and read-later before the endpoints were verified (lifted 2026-10-09; areas/plugins.md)
- Device check: add a story, set cache level 3, read, and confirm chapters ahead are cached and, with cleanup on, older ones removed; delete a chapter on the site and refresh to see the remap.
- Known issues: none recorded

### Plugin repos and install
- Words: repository, repo index, install plugin, update plugin, sha256, third-party repo, official repo
- Owner: plugin/repo/RepoManager.kt
- Changes with: plugin/repo/RepoIndex.kt, plugin/repo/PluginInstaller.kt, plugin/repo/PluginVersionUpdates.kt, ui/settings/PluginsSettingsTab.kt, data/SettingsStore.kt
- Tests: RepoIndexTest, PluginVersionUpdatesTest
- Rules: .cursor/rules/plugin-api.mdc, .cursor/rules/settings.mdc
- Pitfalls:
  - An index entry missing an id, code URL or sha256 is skipped. The installer checks each file's hash; a CRLF copy of a plugin hashes differently from the published bytes.
  - The official repo is pre-added; other repos must be `https://` and need a trust prompt. `plugin_repos` is newline-joined.
  - Debug builds bundle the sibling plugins checkout and re-seed when files differ; release builds ship none.
- Rejected: none recorded
- Device check: Settings > Plugins: install, update and uninstall a plugin; add a third-party repo and confirm the trust prompt.
- Known issues: none recorded

### Plugin update checks and notifications
- Words: new chapters, chapter check, update interval, bell, notification, plugin updates, WorkManager
- Owner: plugin/updates/UpdateChecker.kt
- Changes with: plugin/updates/UpdateDiff.kt, plugin/updates/UpdateNotifier.kt, plugin/updates/UpdateScheduler.kt, plugin/updates/ChapterUpdateWorker.kt, plugin/updates/PluginVersionWorker.kt, plugin/updates/ActiveChapterNotifications.kt, ui/settings/NotificationSettings.kt
- Tests: UpdateDiffTest, PluginVersionUpdatesTest
- Rules: .cursor/rules/plugin-api.mdc, .cursor/rules/sync.mdc
- Pitfalls:
  - `UpdateChecker.run` holds the global `TwoWaySync.lock`; code it calls must not take that lock (`tocStored` does not).
  - Plugins with the `updates` capability are asked cheaply first and only changed stories are re-fetched; others get one `loadWork` per story. Only bell-on stories notify.
  - Work names, channels and intent actions are in seams. Chapter notifications open a story through `ACTION_OPEN_STORY` on `MainActivity`.
  - `UpdateScheduler` is applied at app start from the saved intervals; changing an interval in Settings must re-apply it.
  - Plugin version notices are remembered per `id@version` in `plugin_version_notified`.
- Rejected: none recorded
- Device check: turn on a story's bell, run the check from Settings > About > Notifications, and open the notification; set the plugin update interval and confirm the work is rescheduled.
- Known issues: none recorded

## Network

### HTTP client factory (HttpClients)
- Words: OkHttpClient, HTTP client, timeout, callTimeout, connect timeout, read timeout, shared pool
- Owner: net/HttpClients.kt
- Changes with: plugin/runtime/PluginHttp.kt, plugin/repo/RepoManager.kt, plugin/store/PluginBookStore.kt, share/WebPageIngest.kt, ui/common/BookCover.kt
- Tests: none
- Rules: none
- Pitfalls:
  - Plain HTTP clients come from `HttpClients.create(connectSeconds, readSeconds, callSeconds) { builder tweaks }`. All clients share one root, so they share a connection pool and dispatcher; defaults are 20 / 30 / 60 seconds. `callTimeout` bounds the whole call including a slow body, which connect and read timeouts do not.
  - `rules-check` rule `http-client` fails on `OkHttpClient.Builder(` or `OkHttpClient()` outside `net/HttpClients.kt`.
  - `PluginWs` (raw WebSocket) and the Edge race lanes bind to an Android `Network` and do not use OkHttp builders; leave them out of the factory.
  - `PluginHttp` adds its own cookie jar through `configure`; do not reuse a jar across plugins.
  - Plugin `flow.fetch` now fails a request that takes over 60 seconds in total (before: no total limit).
- Rejected: none recorded
- Device check: open the Plugins tab, refresh a plugin list and open a story; share a web page into the Library; confirm covers still load.
- Known issues: none recorded

## Plugins repo

### Royal Road plugin
- Words: Royal Road, RR, rr, royalroad, Follows, fiction, author notes
- Owner: ../flow-reader-plugins/RoyalRoad
- Changes with: ../flow-reader-plugins/test/royalroad.test.mjs, ../flow-reader-plugins/index.json
- Tests: royalroad.test.mjs (plugins repo)
- Rules: ../flow-reader-plugins/.cursor/rules/royalroad.mdc
- Pitfalls:
  - Book ids `rr:*`; plugin id `royalroad` and prefix `rr` never change.
  - Parses site HTML, so a markup change on the site breaks it silently; the plugin has probes (markup and login-wall checks) that feed the Plugins debug channel.
  - Chapter body is `div.chapter-inner` only; author notes are a setting, default off.
  - Follow, Favorite and Read Later are syncable lists; the app's two-way sync calls `setMembership`, `syncProgress` and `readPositions`.
  - Any release needs a `plugin.json` version bump and a rebuilt `index.json`; pushing `main` publishes to users at once.
- Rejected: none recorded
- Device check: sign in, Sync now, follow a story from the app and confirm it appears on the site, read a chapter with author notes on and off.
- Known issues: none recorded

### Patreon plugin
- Words: Patreon, pt, creator, collection, tag, Memberships, locked post, tier
- Owner: ../flow-reader-plugins/Patreon
- Changes with: ../flow-reader-plugins/test/patreon.test.mjs
- Tests: patreon.test.mjs (plugins repo)
- Rules: ../flow-reader-plugins/.cursor/rules/patreon.mdc
- Pitfalls:
  - Book ids `pt:*`; a work id is a campaign id, optionally with a collection (`.c`) or tag (`.t`) suffix.
  - Post metadata comes from the JSON:API and is indexed per creator in `flow.storage`, so posts read once stay in the ToC after access lapses.
  - Follow is not syncable; Memberships is a browse list (`kind: "browse"`) and is syncable.
  - Indexing has a time budget per call and resumes on the next call; interactive opens get a larger budget.
  - Sign-in cookie rules are seeded by the app (`PluginSessionSeed`), which names this plugin.
- Rejected: none recorded
- Device check: sign in, open a creator from Memberships, follow a collection, read a post, change the Chapter order setting.
- Known issues: none recorded

### Edge TTS plugin
- Words: Edge TTS plugin, EdgeTts, synthesize, voices, kind tts
- Owner: ../flow-reader-plugins/EdgeTts
- Changes with: ../flow-reader-plugins/test/edgetts.test.mjs
- Tests: edgetts.test.mjs (plugins repo)
- Rules: ../flow-reader-plugins/.cursor/rules/edgetts.mdc
- Pitfalls:
  - `kind: "tts"`: it returns clip audio and word boundaries; the app owns the socket (`PluginWs`) and all playback.
  - Readest is upstream; keep the credit header in `index.js`. See "Edge TTS plugin and raw WebSocket".
- Rejected: none recorded
- Device check: install the plugin, pick it in Settings > Audio > Voice, and play.
- Known issues: none recorded

## Data

### Position writes (ProgressWriter)
- Words: position, progress, resume point, saved place, session id, position log, domain
- Owner: data/ProgressWriter.kt
- Changes with: data/ReadingPosition.kt, AppPositionStore.kt, data/PositionLog.kt, ui/reader/ReaderViewModel.kt, FlowApp.kt, plugin/store/PluginCatalog.kt, scripts/agent/rules-check.mjs
- Tests: ProgressWriterTest, PositionLogFormatTest
- Rules: .cursor/rules/data.mdc
- Pitfalls:
  - One write path: build a seek with `ProgressUpdate.chapterSeek` (never a second `ProgressUpdate(` copy); save it with `progress.submitAndDrain`, or for a plugin story `PluginCatalog.seekChapter` (creates the catalog row first when missing). `rules-check` fails on a `.writePosition(` call outside `AppPositionStore` / `PluginCatalog` and a `ProgressUpdate(` constructor outside `ProgressWriter`, `ReaderSessions` and `QueueBook`. Kotlin has no package-private, so a visibility modifier cannot enforce this inside the one module.
  - `drain()` reports a store exception to the writer's `onFailed` (`FlowApp` logs `WRITE_FAILED` to `positions.log`) and drops that update; `appScope` has no exception handler, so nothing may escape the writer.
  - One position table per domain: Library, Queue, and each plugin's own database. `AppPositionStore` routes a write by `PositionDomain`; never share a position between domains.
  - Every write carries a `ReadingSessionId` and a row key and updates only that row's position columns.
  - The writer is debounced and serialized and keeps the latest update per row; a reader that needs the saved row calls `drain()` first.
  - The `onWritten` hook (set in `FlowApp`) schedules plugin chapter upkeep; it must not call `drain`.
  - A Queue position write also sets `queue_state`.
  - Every write and Queue ingest event is appended to `positions.log` (rotated).
- Rejected: none recorded
- Device check: read, close, reopen in each domain (Library book, Queue item, plugin story); check `positions.log` shows one line per write.
- Known issues: none recorded

### Room schema and migrations
- Words: Room, database, migration, schema, entity, DAO, flow.db
- Owner: data/ProgressDb.kt
- Changes with: data/LegacyMigration.kt, plugin/store/PluginDb.kt, plugin/store/PluginStorageMigrator.kt, app/schemas
- Tests: LegacyMigrationTest
- Rules: .cursor/rules/data.mdc
- Pitfalls:
  - A schema change is a version bump plus a `Migration`, with the exported schema JSON committed under `app/schemas`. `FlowApp` registers each migration; versions 1 to 8 fall back to a destructive rebuild.
  - The 9-to-10 migration stages plugin rows in a table that `PluginStorageMigrator` reads by column name; do not rename its columns.
  - Pure row mapping lives in a JVM-tested function (`LegacyMigration`).
- Rejected: none recorded
- Device check: install the previous release, add data, update to the new build, and confirm the Library, Queue and plugin stories survive.
- Known issues: none recorded

### Settings store and keys (SettingsStore)
- Words: setting, preference, DataStore, key, ReaderPrefs, TtsPrefs, chrome prefs, legacy key
- Owner: data/SettingsStore.kt
- Changes with: data/SettingsCodecs.kt, data/ChromePrefs.kt, ui/settings/SettingsState.kt, ui/open/OpenBookViewModel.kt, ui/settings/DataSettings.kt
- Tests: AccentLightnessTest, UiScaleTest
- Rules: .cursor/rules/data.mdc, .cursor/rules/settings.mdc
- Pitfalls:
  - Keys are listed in `docs/agent/seams.md`. Three keys (library view, library tab style, settings tab style) are defined in `ChromePrefs.kt`, not in the store.
  - Only `chrome` is observed; everything else is read once and copied into in-memory state (`OpenUi` for display settings, the TTS controller for audio). A new setting must be wired into one of those copies or nothing sees a change.
  - Several values are stored as encoded blobs (JSON lists, comma lists, `tabKey=Mode` pairs); see seams.
  - Legacy values are mapped on read: a stored `edge` engine becomes the Edge TTS plugin engine id (`migrateEngineKey`), and a missing underlay mode is derived from the stored level and Bluetooth address (`UnderlayMode.legacy`).
  - The file is near 700 lines and has a size baseline; new groups of settings go in a separate file (see `SettingsCodecs.kt`).
- Rejected: none recorded
- Device check: change one setting in every tab, force-stop, reopen, and confirm each persisted.
- Known issues: none recorded

### Shared models (Models.kt)
- Words: BookDoc, Block, BookPlace, Locus, Sentence, BookItem, TtsEngines, tab id
- Owner: data/Models.kt
- Changes with: data/ReadingPosition.kt, data/TonalUnderlay.kt
- Tests: LibraryTabIdTest, AccentLightnessTest, UiScaleTest
- Rules: .cursor/rules/data.mdc
- Pitfalls:
  - `Locus` is a typealias of `BookPlace`.
  - The file also holds bounds and defaults (UI scale, accent lightness scale, `LibraryTabId`); changing a default changes what existing users see before they save anything.
  - Do not change how a book id is derived; see "Book files, covers and ingest".
- Rejected: none recorded
- Device check: none beyond the entries that read these types.
- Known issues: none recorded

### Text filters (rules, editor, Filters tab)
- Words: filters, find and replace, Global, Groups, Local, TTS-only filter, filter sample
- Owner: data/TextFilters.kt
- Changes with: ui/settings/FiltersSettings.kt, ui/settings/FilterRuleList.kt, ui/settings/FilterEditorSession.kt, ui/settings/FilterEdits.kt, ui/library/LibraryViewModel.kt, ui/reader/ReaderViewModel.kt, ui/reader/ReaderTextToolbar.kt
- Tests: TextFiltersTest, FilterEditsTest
- Rules: .cursor/rules/settings.mdc, .cursor/rules/data.mdc
- Pitfalls:
  - Rules are hand-written JSON; the same format is stored in `global_filters`, `group_filters` and each row's `localFilters` column. Enqueueing a book copies its filters into the Queue row; later edits do not follow.
  - "Groups" is a second app-wide list, not per shelf or plugin.
  - Rule edits (add, update, enable, delete, reorder) and the editor preview live once: `FilterRuleEditing` (in `FilterEditorSession.kt`) holds the edit methods and `FilterEdits` the list logic and preview. `LibraryViewModel` and `ReaderViewModel` each implement only `editFilters` (store the list, then refresh). The Library ignores the Local scope.
  - The editor has three entry points: Settings from Library, Settings from Reader, and the reader selection toolbar.
  - Text filters change the visual text and therefore saved offsets; TTS-only rules go through `TtsController.setSpeechFilters`.
  - Compiled patterns are cached in a synchronized LRU.
- Rejected: duplicate editable sample and result boxes in the filter editor; a secondary scope header under the editor title. From areas/settings.md.
- Device check: add one Global and one Local rule, check the sample, play a sentence the rule touches, and check the reader text changed.
- Known issues: none recorded

## Design system

### Design system cards
- Words: fullscreen card, confirm card, floating card, display card, card variant Hero Standard Compact, footer, breadcrumb
- Owner: ui/design/card/FlowFullscreenCard.kt
- Changes with: ui/design/card/FlowFloatingCard.kt, ui/design/card/FlowDisplayCard.kt, ui/design/layer/FlowOverlayFrame.kt
- Tests: none
- Rules: .cursor/rules/ui-system.mdc
- Pitfalls:
  - A fullscreen card must be under a `FlowOverlayHost` or it throws. Keep the parent `visible` while its child is open; Back and scrim tap close only the top card.
  - Cards wrap their content and scroll only on overflow. A lazy-list body needs `scrollable = false` and `Modifier.weight(1f, fill = false)`.
  - Floating cards are only for `FlowDock` items. A display card's tap and double-tap map to the media card and the reader.
  - No `verticalScroll` inside a card body.
- Rejected: per-feature tab bars, modal shells and settings rows (the fragmentation the system replaced); a pinned card footer; hosting editors in a `Dialog` window (it misreported its height and cut off Cancel and Save; editors are overlays hoisted to the host). From areas/ui.md.
- Device check: open Settings, then a rule editor on top, then a confirm card; Back closes one at a time.
- Known issues: none recorded

### Design system media card
- Words: media card, hero card, MediaCardModel, MediaAction, adapter, splash, stats, links
- Owner: ui/design/card/media/FlowMediaCard.kt
- Changes with: ui/design/card/media/MediaCardModel.kt, ui/design/card/media/MediaCardAdapters.kt, ui/library/FilesBookSplash.kt, ui/plugin/StoryMediaCard.kt
- Tests: MediaCardAdaptersTest, PluginCardTest
- Rules: .cursor/rules/ui-system.mdc, .cursor/rules/plugin-api.mdc
- Pitfalls:
  - Content is plain data (`MediaCardModel`) built by a pure adapter with a unit test. A new source gets a new adapter, not new `FlowMediaCard` parameters.
  - Host action ids are reserved (`MediaActionIds`); plugin action ids are a separate namespace.
  - Reader and Library show the same card; per-context hiding uses `withoutHostActions`.
  - The plugin-facing slot contract is `docs/plugins/ui-contract.md`.
- Rejected: a dedicated plugin capability flag for card actions (the handler runs only on tap of a plugin-declared button). From areas/ui.md.
- Device check: open the media card from a Files book and a plugin story; compare slots with `docs/plugins/ui-contract.md`.
- Known issues: none recorded

### Design system layers, docks and screens
- Words: overlay host, overlay stack, scrim, dock, dock item, FlowScreen, FAB, pull to refresh, screen padding
- Owner: ui/design/layer/FlowOverlayHost.kt
- Changes with: ui/design/layer/FlowDock.kt, ui/design/layer/FlowScreen.kt, ui/design/layer/FlowPullRefresh.kt, ui/theme/FlowLayer.kt
- Tests: none
- Rules: .cursor/rules/ui-system.mdc
- Pitfalls:
  - One `FlowOverlayHost` at the window root (`MainActivity`) holds the overlay stack, the single scrim, Back handling and a `system` slot (the debug bubble).
  - Dock items are declared top to bottom; content pads by the dock height it reports (`FlowScreenPadding`).
  - `FlowScreen` is used by the Library; the reader builds its own docks.
  - The Library sort button re-derives `FlowScreen`'s FAB padding.
  - Layer order is `FlowLayer` constants; no literal z-index.
- Rejected: none recorded
- Device check: open a card over the Library and over the Reader; the dock and FAB clear the system bars.
- Known issues: none recorded

### Design system tabs
- Words: tab bar, tab, icon tab, image tab, text tab, action tab, Primary Secondary level, tab badge
- Owner: ui/design/tabs/FlowTabBar.kt
- Changes with: ui/design/tabs/FlowTabLayout.kt, ui/settings/SettingsChrome.kt, ui/library/LibraryTopBar.kt
- Tests: FlowTabLayoutTest
- Rules: .cursor/rules/ui-system.mdc
- Pitfalls:
  - A tab is data (`FlowTab.text`, `icon`, `image`, `action`), not a composable; a new tab is a new entry, never a new bar.
  - Icon tabs pass `contentDescription` (long-press tooltip). `flowImageTabs` is composable; duplicate labels need an explicit `key`.
  - Settings sub-tabs go through `SettingsSubTabBar` so the breadcrumb and text-tab preference work.
  - Tab labels never truncate.
- Rejected: custom tab rows with packed gaps; equal-width tab slots with ellipsized labels. From areas/settings.md and areas/plugins.md.
- Device check: Library and Settings with icon tabs, then switch Settings > Layout > UI to text tabs.
- Known issues: none recorded

### Design system controls, surfaces and icons
- Words: toggle row, slider row, dropdown row, text field, chips, list row, badge, dot, button, FAB, cover, surface, icon token
- Owner: ui/design/controls/FlowSettingsRows.kt
- Changes with: ui/design/controls/FlowButtons.kt, ui/design/controls/FlowListRow.kt, ui/design/controls/FlowBadge.kt, ui/design/surface/FlowCover.kt, ui/design/surface/FlowSurface.kt, ui/design/FlowIcons.kt
- Tests: none
- Rules: .cursor/rules/ui-system.mdc
- Pitfalls:
  - `FlowToggleRow` requires a subtitle. `FlowTextField` takes `colors` (default M3 outlined colours; extend it, do not copy the M3 field). `FlowCollapsible` owns its expanded state unless the caller passes `expanded` and `onExpandedChange`; use `FlowCollapsibleBox` for a region without a header.
  - Icon tokens in `FlowIcons` are plugin API: append only; never rename or remove.
  - `FlowSurface` is for design-system internals.
  - Do not borrow another component's size token for a new component; add one.
  - "Use this instead" list: `docs/ui-system/README.md`.
- Rejected: none recorded
- Device check: Settings > Layout and Audio exercise most rows; Library cards exercise badges and dots.
- Known issues: none recorded

### Theme and tokens
- Words: theme, accent, hue, UI scale, token, FlowTokens, FlowType, FlowMotion, layer
- Owner: ui/theme/FlowTokens.kt
- Changes with: ui/theme/Theme.kt, ui/theme/FlowType.kt, ui/theme/FlowMotion.kt, ui/theme/FlowLayer.kt
- Tests: UiScaleTest, AccentLightnessTest
- Rules: .cursor/rules/ui-system.mdc
- Pitfalls:
  - Feature code uses tokens, never literal `dp`, alpha or z-index.
  - `FlowTheme` multiplies density by the UI scale, so dp and sp both scale; the share window uses its own theme call.
  - A new token or component also updates `docs/ui-system/`.
- Rejected: token-only scaling without a density override (leaves M3 internals unscaled); keeping the share overlay as raw Android Views (replaced by a Compose card). From areas/ui.md.
- Device check: switch theme (Light, Dark, OLED), accent, and UI scale at 0.75 and 1.25.
- Known issues: none recorded

## Settings tabs

### Settings tabs map
- Words: Settings tab, sub-tab, About, Audio, Filters, Import, Layout, Plugins, Info, Notifications, Data, Debug, Voice, Playback, Theme, UI, Font, Reading
- Not the same as: `DebugRoute.SettingsTabs`, a list of names in the same order used by the smoke script
- Owner: ui/settings/SettingsChrome.kt
- Changes with: ui/settings/SettingsOverlay.kt, ui/settings/SettingsState.kt, ui/debug/DebugRoute.kt
- Tests: DebugRouteTest
- Rules: .cursor/rules/settings.mdc, .cursor/rules/ui-system.mdc
- Pitfalls:
  - Index to file: 0 About (`AboutSettingsTab`: Info, Notifications, Data, and Debug only while debug is on); 1 Audio (`AudioSettingsTab`: Voice, Playback); 2 Filters (`FiltersSettingsTab`: one sub-tab per scope); 3 Import (`SharingSettingsTab`: Router, Parser); 4 Layout (`LayoutSettingsTab`: Theme, UI, Font, Reading); 5 Plugins (`PluginsSettingsTab`: Installed, then one per plugin).
  - Inserting a tab shifts the index and the `when`, the section list and `DebugRoute.SettingsTabs` all follow it.
  - Sub-tab selection resets when the primary tab changes; Debug appears and disappears with the toggle.
- Rejected: Font as its own primary tab; splitting Audio into two primary tabs; a separate library settings card; a theme selector on the Library screen. From areas/settings.md.
- Device check: visit every tab and sub-tab from the Library and from the Reader; turn debug on and off with About open.
- Known issues: none recorded

### Audio settings tab
- Words: Audio, Voice, Playback, engine, speed, pitch, pre-cache, clip size, underlay, sentence offset, highlight sync
- Owner: ui/settings/AudioSettings.kt
- Changes with: ui/settings/VoiceSettingsTab.kt, ui/settings/PlaybackSettingsTab.kt, ui/settings/AudioSettingsControls.kt, ui/settings/SettingsState.kt
- Tests: none
- Rules: .cursor/rules/settings.mdc, .cursor/rules/tts.mdc
- Pitfalls:
  - State and callbacks come from the screen that hosts Settings (Library or Reader); see "Settings overlay and tab placement" for the nine edit points of a new audio setting.
  - `showClipSettings` and `showNetworkLanes` default to false, so a screen that forgets to set them still compiles.
  - Center-origin sliders use the custom `CenterOriginSlider`.
- Rejected: M3 `SliderDefaults.CenteredTrack` (not in the BOM)
- Device check: change engine, voice, speed, pre-cache, underlay mode and sentence offset; confirm each persists and takes effect on the next sentence.
- Known issues: none recorded

### Layout settings tab
- Words: Layout, Theme, accent, UI scale, Font, line spacing, justify, orientation, Reading, home position, chapter headings, keep screen awake
- Owner: ui/settings/AppearanceSettings.kt
- Changes with: ui/settings/ThemeSettingsPane.kt, ui/open/OpenBookViewModel.kt, ui/settings/SettingsState.kt, data/SettingsStore.kt
- Tests: AccentLightnessTest, UiScaleTest
- Rules: .cursor/rules/settings.mdc
- Pitfalls:
  - Display values live in `OpenUi` (`OpenBookViewModel`, an app-wide display view model despite the name) and feed `FlowTheme` in `MainActivity`.
  - Font scale and line spacing ranges are literals in the settings screen and the view model; the store does not clamp them.
  - Layout > UI also switches Library and Settings bars between icon and text tabs.
- Rejected: none recorded
- Device check: change theme, accent, UI scale, font, spacing and orientation, then open a book.
- Known issues: none recorded

### About settings tab (Info, Notifications, Data)
- Words: About, Info, Notifications, Data, plugin story downloads, check interval, debug mode, version
- Owner: ui/settings/AboutSettingsTab.kt
- Changes with: ui/settings/NotificationSettings.kt, ui/settings/DataSettings.kt, ui/open/OpenBookViewModel.kt
- Tests: none
- Rules: .cursor/rules/settings.mdc
- Pitfalls:
  - Notifications and Data panes read `FlowApp` directly and write on the pane's own scope, so a write started as the pane closes may be cancelled.
  - Turning on new-chapter alerts asks for notification permission through `FlowApp.requestNotificationPermission`.
  - Data holds the defaults for new plugin stories (`plugin_cache_level`, `plugin_cache_cleanup`).
- Rejected: none recorded
- Device check: toggle chapter alerts, change the interval, change story download defaults, turn debug on and open the bubble.
- Known issues: none recorded

### Plugins settings tab
- Words: Plugins, Installed, Repositories, Advanced, per-plugin tab, Update, Uninstall, plugin account row
- Owner: ui/settings/PluginsSettingsTab.kt
- Changes with: ui/plugin/AccountSheets.kt, ui/plugin/PluginSettingsSheet.kt, ui/plugin/PluginDataSettings.kt, plugin/repo/PluginInstaller.kt
- Tests: none
- Rules: .cursor/rules/settings.mdc, .cursor/rules/plugin-api.mdc
- Pitfalls:
  - Installed uses compact `FlowListRow`s; each installed plugin then gets a sub-tab (its `icon.png` when it ships one) with the account row and the manifest-driven settings form.
  - It collects `pluginManager.installed` and `pluginRepos.repos` live; see seams.
- Rejected: none recorded
- Device check: install, update, open a plugin's sub-tab, sign in, uninstall.
- Known issues: none recorded

## Debug

### Debug mode, synth log and smoke routes
- Words: debug mode, bug bubble, synth log, debug log, smoke route, flow_debug_route, Debug tab
- Owner: ui/debug/DebugRoute.kt
- Changes with: debug/DebugLog.kt, ui/debug/DebugSynthDumpFab.kt, tts/SynthDebugLog.kt, ui/library/LibraryScreen.kt, MainActivity.kt, scripts/smoke.ps1
- Tests: DebugLogTest, DebugRouteTest
- Rules: .cursor/rules/tts.mdc
- Pitfalls:
  - `DebugRoute` names are the only ones the smoke script can open (`library`, `reader/first`, `settings/<tab>`, `plugin/<id>`); a new route needs a parse case and a test.
  - Debug channels (`debug_tts_log`, `debug_reader_log`, `debug_plugins_log`) are set from About and flip `DebugLog` globals; the TTS controller can also turn debug on after an error.
  - The bubble is mounted in the overlay host's `system` slot and shows while debug is on in either place.
  - The synth log is an in-memory ring; Save writes a file under the app's external files folder.
- Rejected: none recorded
- Device check: turn on debug, play to force a log line, open the bubble, Save, then drag the bubble to the bottom target to turn debug off.
- Known issues: none recorded
