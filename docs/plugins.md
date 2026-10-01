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
card (cover, badges, stats, tags, synopsis, cache strip, list toggles, Share, links,
Download / Refresh / Delete, **Read**). apiVersion 2 plugins add their own stats, badges, links
and up to two rail and two footer actions ([example](plugins/examples/media-card.md)).

- **Stream (default):** full ToC on add; chapter bodies on demand; small cache around the current position
- **Download:** All or Partial (start chapter, cache level)

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
