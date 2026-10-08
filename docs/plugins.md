# Plugins

Source plugins add a library tab for a web-fiction site (followed and saved serials, not a
browser). Plugins are sandboxed JavaScript installed from repositories; the app draws every
screen, owns the chapter cache and the reader, and routes shared links.

- [Plugin API](plugins/api.md): manifest, functions, `flow` host API, repository index
- [UI contract](plugins/ui-contract.md): the fixed cards, sheets, and rules plugins render into

## Install

**Settings → Import → Plugins** lists repositories, available plugins, and installed plugins (Install,
Update, Uninstall). The official repository
(`https://oxika95.github.io/flow-reader-plugins/index.json`) is pre-added; third-party
repositories must be `https://` and show a trust prompt. Uninstalling keeps story data and
sign-in, so a reinstall restores the library.

Then show the tab from Library → **+** → **Plugins**.

## Tab

Sub-tabs are the plugin's lists (for Royal Road: Follow, Favorite, Read Later), then Search and
Account (or plugin Settings) when supported. Tap a story to read; long-press opens the story media
card (cover, badges, stats, tags, synopsis, cache strip, list toggles, new-chapter bell, Share, links,
Download / Refresh / Delete, **Read**). Plugins add their own stats, badges, links
and up to two rail and two footer actions ([example](plugins/examples/media-card.md)).

- **Stream (default):** full ToC on add; chapter bodies on demand. Reading or listening downloads
  the current chapter and the **cache level** (N) chapters after it whenever the chapter changes,
  and syncs that chapter to the site as read (fetching or preloading alone doesn't sync)
- Each chapter opens with its title as a heading (like an EPUB chapter), shown and spoken; a body
  that already starts with the title isn't repeated
- **Saved position** (the progress row) is the one anchor: the cache bar's locus, the partial
  download start, and the stream window all use it. **Hold the cache bar** to open a chapter slider
  (with − / + for exact steps); **Save** asks before replacing an existing position, writes the
  start of that chapter, then downloads ahead from it
- **Download:** tap = "Download all N chapters?" confirm; while running the button reads **Cancel
  download**. **Hold Download** for Partial download: the saved position, the cache level field,
  **Clean up old chapters**, and **Download next N** (the saved chapter through N after it)
- **Cleanup** (per story, off by default): deletes chapters more than N behind the saved position.
  Download all pins the whole ToC, so those chapters are never cleaned up; a cancelled or failed
  Download all drops the pin again. With cleanup off, chapters stay until Delete
- New stories take cache level and cleanup from **Settings → Import → Plugins → New stories**

## New-chapter notifications

Sites can't push to the app, so a background job (WorkManager) checks for new chapters every
**Settings → Import → Plugins → New chapters** interval (Off / 3h / 6h / 12h / Daily, default 12h;
optional Wi-Fi only; **Check now** runs once).

- **Which stories:** the **bell** on the story media card. Unset, it is on while the story is on a
  syncable list (Royal Road: Follow); tapping it stores an explicit on/off (`notify=` in `meta.txt`).
- **How:** plugins with the `updates` capability (apiVersion 3) answer `checkUpdates` cheaply (Royal
  Road: the signed-in Follows page, else each story's public RSS feed); only stories that changed
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
(one folder per plugin, e.g. `RoyalRoad/`). CI tests them and publishes `index.json` to GitHub Pages.

Debug builds also bundle a local checkout of that repo (default `../flow-reader-plugins`, override
with `-PflowPluginsDir=...`) and reinstall it whenever the files change, so plugin edits reach the
device with `./gradlew :app:installDebug`. Release builds ship no plugins.

## App source

- [`plugin/`](../app/src/main/java/com/personal/flowreader/plugin/): manager, runtime, repositories, stores
- [`ui/plugin/`](../app/src/main/java/com/personal/flowreader/ui/plugin/): tab, cards, and sheets
- [`PluginsSettingsTab.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/PluginsSettingsTab.kt)

On-disk: `filesDir/plugins/installed/{id}/` (code), `filesDir/plugins/data/{id}/` (stories, lists, settings).

[Back to hub](README.md)
