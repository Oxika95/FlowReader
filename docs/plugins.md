# Plugins

Source plugins add a library tab for a web-fiction site (followed and saved serials, not a
browser). Plugins are sandboxed JavaScript installed from repositories; the app draws every
screen, owns the chapter cache and the reader, and routes shared links.

- [Plugin API](plugins/api.md): manifest, functions, `flow` host API, repository index
- [UI contract](plugins/ui-contract.md): the fixed cards, sheets, and rules plugins render into
- [Source kinds](plugins/source-kinds.md) (design): synced plugins (Royal Road: the site owns
  position) vs sourced plugins (Patreon: content from the web, position on this device)

## Install

**Settings → Plugins → Installed** lists installed plugins (Update, Settings, Uninstall), New
stories defaults, available plugins (Install), and repositories. Each installed plugin then has its
own sub-tab with its manifest settings. The official repository
(`https://oxika95.github.io/flow-reader-plugins/index.json`) is pre-added; third-party
repositories must be `https://` and show a trust prompt. **Uninstall** keeps story data and
sign-in, so a reinstall restores the library; **Delete data too** on the same card also erases it.
The plugin's sub-tab has **Delete {plugin} data** (`PluginDataEraser`): library entries, covers,
downloaded chapters, lists, reading positions, sync state, `flow.storage`, settings, and sign-in
(encrypted cookies and WebView cookies). Nothing on the site changes. Sign out alone keeps data.

**Plugin updates** (Settings → About → Notifications): a background job (`PluginVersionWorker`,
WorkManager, any network; every 0–24 h, default 24 h) refreshes the repositories and, with
**Notify about updates** on, posts one "Plugin updates"
notification per new version of an installed, compatible plugin. Updates are never installed
automatically; tap Update in Settings → Plugins → Installed. Opening the page also refreshes repositories when none are
loaded yet.

Then show the tab from Library → **+** → **Plugins**.

## Tab

Sub-tabs are the plugin's lists as icons (manifest `lists[].icon`; for Royal Road: Follow,
Favorite, Read Later), then Search and Account (or plugin Settings) when supported. Rows read
"{Author} · chapter {last read}/{total}", or "{Author} · N chapters" before any reading. Tap a
story to read; long-press opens the story media card (cover, badges, stats, tags, synopsis,
position slider, list toggles, new-chapter bell, Share, Rename, links, Download / Refresh / Delete,
**Read**). Plugins add their own stats, badges, links and up to two rail and two footer actions
([example](plugins/examples/media-card.md)).

**New chapters** found by the update check (every story on a list, plus bell-on stories) add to
the story's `newChapters` count (`plugin_stories` in the plugin's database). The count shows as an accent "+N" badge on the
library row (grid: cover corner) and beside the media card title, and as a dot on the list sub-tab
and the plugin's Library tab. Opening the story in the reader clears it. Only bell-on stories also
post a system notification.

**Rename** sets a local title (`titleOverride` in `plugin_stories`) shown in the library, reader,
and queue; refresh and sync never touch it; **Reset** goes back to the plugin's title. Renames,
counts, the bell, and cache settings are backed up with the plugin's database.

**Locked chapters** (`ChapterRef.locked`, Patreon posts above your tier, shown by default) carry a
lock icon in the reader's Contents and the creator page. One that was never downloaded opens as its
heading alone (nothing is fetched, and TTS reads only the title).

Patreon's **All posts** story (what Follow adds for a creator) is named after the creator's
largest collection, else their most used tag, else the creator.

**Browse lists** (`kind: "browse"`, Patreon: Memberships) list entry points instead of stories.
Tapping a creator opens a creator page with tabs; each tab loads only when first shown and stays
loaded while the page is open:

- **About** (default): profile, patron count, membership status, and description. The plugin
  caches the profile for 6 hours, so this tab usually opens without network.
- **Posts**: every post of the All posts story, newest or oldest first, with a lock icon on posts
  the account cannot read and a download icon on posts stored locally. Tapping a post opens the All
  posts media card positioned at that post (Read, Download, and the position slider start there);
  downloads run from the card (locked posts are skipped).
- **Collections**: Patreon collections and tags as cards grouped under divided headers (both
  headers always show; an empty one says the creator has no collections or tags),
  with sort chips below the tabs (Latest post / Name / Most posts) and an "In Follow" badge on
  stories already added. Open one for its media card; its Download covers only that collection.
- **Membership**: "Your membership" first (your tier, matched by pledge amount, with its benefits and
  a Manage on Patreon link), then "More tiers" (collapsed until tapped), each with a Join link that
  opens Patreon.

The Memberships list is split into **Paid** (green badge, active patron) and **Free** (red badge,
free or former member) groups separated by a divider.

Adding a story to Follow brings it into the reader.
Signed in, the list re-imports itself when shown (at most every 15 minutes) and after a creator page
closes; Patreon rows carry an "N new" badge (posts in the home feed since the creator page was last
opened) and the newest post date. Sync never adds or deletes stories for a browse list.

- **Stream (default):** full ToC on add; chapter bodies on demand. Reading or listening downloads
  the current chapter and the **cache level** (N) chapters after it whenever the chapter changes,
  and syncs that chapter to the site as read (fetching or preloading alone doesn't sync)
- Each chapter opens with its title as a heading (like an EPUB chapter), shown and spoken; a body
  that already starts with the title isn't repeated
- **Saved position** (the story's row in the plugin's own database, `plugin_<id>.db`; see
  [data.md](data.md#room-flowdb)) is the one anchor: the position slider's mark, the partial
  download start, and the stream window all use it. **Drag the position slider** on the card (its
  track is the cache strip) and release on a chapter: "Move reading position?" offers **Move**
  (writes the start of that chapter, then downloads ahead from it) or **Fine-tune** (a card with
  − / + for exact steps)
- **Download:** tap = "Download all N chapters?" confirm; while running the button reads **Cancel
  download**. **Hold Download** for Partial download: the saved position, the cache level field,
  **Clean up old chapters**, and **Download next N** (the saved chapter through N after it)
- **Cleanup** (per story, off by default): deletes chapters more than N behind the saved position.
  Download all turns on **keep all** for the story: every chapter, including ones added later, is
  downloaded and never cleaned up. A cancelled or failed Download all turns it back off. With
  cleanup off, chapters stay until Delete
- New stories take cache level and cleanup from **Settings → About → Data → Plugin story downloads**
- **ToC refresh** (Refresh, update check, re-add): chapter bodies are stored by chapter URL
  (`c/{sha1(url)}.txt`), so a refresh moves nothing on disk and chapters inserted earlier in the
  ToC (Patreon posts unlocked by a tier change) shift nothing. Bodies whose URL left the ToC are
  deleted by the next cache upkeep. Positions are identified by chapter URL too; when the author
  removes the saved chapter ("stubbing" on Royal Road), the position moves to the next chapter that
  survived (log `TOC_REMAP`). An open reader or TTS session keeps the ToC it opened with and still
  reads the right text; new chapters show the next time the story opens

## Sign-in

Plugins sign in either with a form (`auth.fields`; the password goes only to the site) or, with
`auth.web`, on the site's own login page in an in-app browser (Royal Road, Patreon). The browser uses the plugin's user agent; once the manifest's `doneCookie` appears, the
cookies of the plugin's `allowedHosts` move into the plugin's encrypted cookie jar and the browser
closes. Sign out also clears those browser cookies. Google / Apple sign-in may refuse embedded
browsers; use the site's email sign-in. Sign in / Sign out live in the plugin tab's Account sheet
and in **Settings → Plugins → (plugin) → Account**; either one refreshes the other.

Signed in, the Account sheet offers **Sync** (also prompted right after sign-in). With several
`syncable` lists it is one **Sync lists** action covering all of them (Royal Road: Follow, Favorite,
Read Later). **Merge** keeps local additions; **Overwrite** makes each list match the site and
deletes a story's local data only when it is on none of the synced lists afterwards.

### Two-way sync

Plugins with `setMembership` and `readPositions` (Royal Road) sync lists and reading positions both
ways: on tab open (at most every 10 min), **Sync now** in the Account sheet, after sign-in, and
before each new-chapter check. Code: [`plugin/sync/`](../app/src/main/java/com/personal/flowreader/plugin/sync/)
(`ListReconcile`, `ProgressReconcile` (pure), `SyncState`, `TwoWaySync`).

- **Three-way merge:** each run compares local, site, and the last synced state (`sync.json` in the
  plugin data dir). A change on one side is copied to the other; additions and removals propagate
  both ways. A removed story keeps its downloads until it is on no list (Delete removes it from the
  site lists too).
- **First sync:** lists are unioned (nothing deleted); positions take the further chapter. Stories
  never opened, or whose saved ToC lacks the site's chapter, are not loaded: the card shows
  "Last read: {chapter}" and the position applies when the ToC is next stored (opening the story,
  new-chapter check). Royal Road reads every Follow's position from the Follows pages; stories only
  on Favorite / Read Later (those pages show no position) get theirs from "Continue Reading" on the
  story page whenever that page is loaded for its ToC. Sync never requests a page per story.
- **Guard:** site removals are skipped when the site list is empty or under half of a last-synced
  list of 4 or more (login-gated or truncated page); the Account sheet says so.
  **Replace with site lists** (Overwrite) accepts the site as is and resets the baseline: lists and
  every reading position the site reports (no conflict prompts; queued position pushes dropped).
  Merge there runs the normal position merge.
- **Positions:** a chapter change in the reader pushes to the site (not when it equals the synced
  chapter, or opening an unread story at chapter 1). When both sides moved since the last sync, the
  story gets a **Position conflict** badge and a card: Use {site} / Keep mine / Later.
- **Offline:** list toggles and position pushes queue in `sync.json` and retry on the next run;
  network and sign-in errors don't count against the 5-attempt limit.
- **Royal Road:** chapter downloads are anonymous (`cookies: false`) so preloading never moves the
  site's "Continue"; pushes view the chapter signed in and submit RR's "Set Progress" form when
  moving backwards (a signed-in view only moves progress forward).

## New-chapter notifications

Sites can't push to the app, so a background job (WorkManager) checks for new chapters every
**Settings → About → Notifications → New chapters** interval (slider, Off or 1–24 h, default
12 h; optional Wi-Fi only; **Check now** runs once).

- **Which stories:** the **bell** on the story media card. Unset, it is on while the story is on a
  `notifyDefault` list (Royal Road and Patreon: Follow) and off once it leaves; tapping it stores
  an explicit on/off (`notify` in `plugin_stories`) that wins over list membership.
- **How:** plugins with the `updates` capability answer `checkUpdates` cheaply (Royal Road: the
  signed-in Follows page, else each story's public RSS feed; Patreon: the signed-in home feed, else
  each creator's newest posts); only stories that changed
  get a `loadWork`. Other plugins get one `loadWork` per monitored story (max 100 per run). Followed
  stories never opened are fetched once (max 10 per run) as a baseline, without notifying.
- **Then:** the new ToC is stored; chapters new by URL (not re-titled or reordered ones) produce one
  notification per story, grouped, counting up until opened. Stories with a saved position
  download the cache level ahead of it. Tapping a notification opens the story's media card.
- Code: [`plugin/updates/`](../app/src/main/java/com/personal/flowreader/plugin/updates/)
  (`UpdateChecker`, `UpdateDiff` (pure), `UpdateNotifier`, `UpdateScheduler`, `ChapterUpdateWorker`).

## Import routing

Each installed plugin's `shareHosts` seed router rules that send those links to **Plugin · {name}**
(see [import-share.md](import-share.md)).

## Plugin source

Plugins live in the [flow-reader-plugins](https://github.com/Oxika95/flow-reader-plugins) repo
(one folder per plugin: `RoyalRoad/`, `Patreon/`). CI tests them and publishes `index.json` to GitHub Pages.

Debug builds also bundle a local checkout of that repo (default `../flow-reader-plugins`, override
with `-PflowPluginsDir=...`) and reinstall it whenever the files change, so plugin edits reach the
device with `./gradlew :app:installDebug`. Release builds ship no plugins.

## App source

- [`plugin/`](../app/src/main/java/com/personal/flowreader/plugin/): manager, runtime, repositories, stores
- [`ui/plugin/`](../app/src/main/java/com/personal/flowreader/ui/plugin/): tab, cards, and sheets
- [`PluginsSettingsTab.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/PluginsSettingsTab.kt)

On-disk: `filesDir/plugins/installed/{id}/` (code), `filesDir/plugins/data/{id}/` (stories, lists, settings).

[Back to hub](README.md)
