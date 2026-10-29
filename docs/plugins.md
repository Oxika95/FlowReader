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
Favorite, Read Later), then Account (or plugin Settings) when supported. Search is the FAB.
Library rows: title, author, **Chapter n/total** chip (primary / secondary / muted) and **"N new"**
when chapters remain after the saved position. Sort circle above the FAB opens a compact **Sort**
card (default **Updates**: unread-ahead, then unstarted, then complete; else Title / Author / Date
added). Tap = media card; double-tap = reader. Plugin stats/badges/links/actions:
[media-card example](plugins/examples/media-card.md). A **plain accent dot** tracks an active shade
notification. **Rename** (inline on the media-card title) / **Reset** use `titleOverride`. **Locked** chapters are Contents-only.
**Pull to refresh** re-imports or syncs, then checks for new chapters. Patreon **All posts** names
from largest collection, else top tag, else creator.

**Browse lists** (`kind: "browse"`, Patreon: Memberships) list entry points instead of stories.
Tapping a creator opens a creator page with tabs; each tab loads only when first shown and stays
loaded while the page is open:

- **About** (default): profile, patron count, membership status, and description. The plugin
  caches the profile for 6 hours, so this tab usually opens without network.
- **Posts**: every post of the All posts story, newest or oldest first, with a lock icon on posts
  the account cannot read and a download icon on posts stored locally. Tapping a post opens the All
  posts media card positioned at that post (Read, Download, and the progress bar start there);
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
Adding a story to Follow brings it into the reader. Signed in, the list re-imports itself when shown (at most every 15 minutes) and after a creator page
closes; Patreon rows carry an "N new" badge (posts in the home feed since the creator page was last
opened) and the newest post date. Sync never adds or deletes stories for a browse list.

- **Stream (default):** full ToC on add; chapter bodies on demand. Reading or listening downloads
  the current chapter and the **cache level** (N) chapters after it whenever the chapter changes,
  and syncs that chapter to the site as read (fetching or preloading alone doesn't sync)
- Each chapter opens with its title as a heading (like an EPUB chapter), shown and spoken; a body
  that already starts with the title isn't repeated
- **Saved position** (the story's row in the plugin's own database, `plugin_<id>.db`; see
  [data.md](data.md#room-flowdb)) is the one anchor: the progress bar, the partial download start,
  and the stream window all use it. **Tap the progress bar** on the card to open the reading-
  position card (− / +); Save writes the start of that chapter, then downloads ahead from it
- **Download:** tap runs Manage settings — keep-all → every chapter; otherwise the saved position
  plus cache level ahead. While running the button reads **Cancel download**
- **Manage** (footer): cache level, clean up old chapters, keep all chapters, clear local
  downloads, remove from library. Keep-all downloads every chapter (including ones added later)
  and skips cleanup. With cleanup off (and keep-all off), chapters stay until cleared or removed
- **Contents** (rail ToC): chapter list; tap opens Read at that chapter. Rename is an inline edit
  icon on the title (not a rail bubble)
- New stories take cache level and cleanup from **Settings → About → Data → Plugin story downloads**
- **ToC refresh** (open card background for sync/`updates` plugins, pull down on the media card,
  update check, re-add): chapter bodies are stored by chapter URL
  (`c/{sha1(url)}.txt`), so a refresh moves nothing on disk and chapters inserted earlier in the
  ToC (Patreon posts unlocked by a tier change) shift nothing. Bodies whose URL left the ToC are
  deleted by the next cache upkeep. Positions are identified by chapter URL too; when the author
  removes the saved chapter ("stubbing" on Royal Road), the position moves to the next chapter that
  survived (log `TOC_REMAP`). An open reader or TTS session keeps the ToC it opened with and still
  reads the right text; new chapters show the next time the story opens
- **Caught up:** TTS reaching the end of the last chapter saves `chapterFinished`; once an update
  adds a chapter, the story opens and downloads ahead from it (site position moves only on open)

## Local-first library

Plugin tabs paint from on-device data first (`_lists/*.txt`, `plugin_*.db`, splash/ToC/bodies).
Opening a tab must not wait on the network: membership and catalog rows show immediately; cover
meta and browse post panes fill in a second pass. Cloud work (sync, Memberships reimport, chapter
bodies) runs afterward and never clears the list while refreshing. Opening a story media card
likewise shows the cached splash/ToC first; for plugins with syncable lists or `updates`, a
background ToC refresh runs with a non-blocking pull indicator on the card (pull down still
forces a refresh). Source-kind rules (`synced` vs `sourced`) are designed in
[plugins/source-kinds.md](plugins/source-kinds.md); Royal Road syncs lists and positions, Patreon
keeps positions on-device only.

## Sign-in

Plugins sign in either with a form (`auth.fields`; the password goes only to the site) or, with
`auth.web`, on the site's own login page in an in-app browser (Royal Road, Patreon). The browser uses the plugin's user agent; once the manifest's `doneCookie` appears, the
cookies of the plugin's `allowedHosts` move into the plugin's encrypted cookie jar and the browser
closes. Sign out also clears those browser cookies. Google / Apple sign-in may refuse embedded
browsers; use the site's email sign-in. Sign in / Sign out live in the plugin tab's Account sheet
and in **Settings → Plugins → (plugin) → Account**; either one refreshes the other.

Account UI seeds from encrypted secrets (`account`, Royal Road `loggedIn`) before the network
`session()` round-trip, so "Signed in as …" appears immediately when cookies were imported earlier.
Opening Account makes **no** network calls; returning to the tab re-checks `session()`. Cookie jar
lifetime is synthetic (1 year); a soft nudge after ~30 days (Royal Road) or ~14 days (Patreon)
suggests signing in again. Hard expiry still comes from HTTP / `session()` (`AUTH_REQUIRED`), which
also broadcasts `sessionChanges` so Settings and the tab stay aligned. There is no silent cookie
refresh.

### Account hub

The plugin tab's Account card is the day-to-day hub (Settings → Plugins stays install/admin), with
secondary tabs:

- **Account:** signed-in status; Sign in / Sign out / Sign in again.
- **URLs:** `sitePages` (Sign in first). **Open** = in-app browser; **Done** imports cookies and
  runs `session()` only (no list pull).
- **Manage:** manifest Settings when present; **Data** with Sync / Delete (two-way) or Import /
  Replace + Delete (sourced). Delete clears this device only; Sync after Delete is a fresh pull.

Sync / list errors point at **Open site** or **Sign in**, not “try Sync again”. With Debug mode on,
Settings → About → Debug → **Plugins** logs Sync steps, each `flow.fetch` (URL, status, HTML
fingerprint: title / logged-in / bot-check / fiction-list markers), and plugin `flow.log` lines
(e.g. “Requesting Oxika95's Follows…”, parse counts) on the bug bubble / `FlowReader.Plugins`.

Signed in after web/form login, two-way plugins run **Sync**; sourced plugins offer the Merge /
Overwrite import choice on Library.

### Two-way sync

Plugins with `setMembership` and `readPositions` (Royal Road today) sync lists and positions both
ways via **Sync** (Account; opening Account does not sync), after sign-in, pull-to-refresh, and
before each new-chapter check. Quiet OkHttp `list()` pulls. Code:
[`plugin/sync/`](../app/src/main/java/com/personal/flowreader/plugin/sync/).

- **Initial pull:** empty local baselines → site overwrites memberships + forces positions.
- **Progressive:** three-way merge local × site × `sync.json`; adds/removes both ways.
- **Success gating:** `lastSyncedAt` only when every syncable list fetch succeeds.
- **Pending positions:** "Last read: {chapter}" until ToC is stored; RR Follow positions from
  Follows pages (cached); no per-story page during sync.
- **Guard / Replace:** skip site removals when the list looks truncated; Overwrite resets baseline.
- **Positions / Offline / RR:** conflict card when both moved; outbox in `sync.json`; chapter
  downloads use `cookies: false`; backwards push via rewind form; `minRequestIntervalMs: 700`.

Patreon post indexes resume across calls; partial ToC is served when some posts are already indexed.

## New-chapter notifications

WorkManager job (**Settings → About → Notifications → New chapters**, Off or 1–24 h, default 12 h;
Wi-Fi only optional; **Check now**). Bell on the media card (`notifyDefault` lists, else explicit
`notify`). Plugins with `updates` use cheap `checkUpdates` (else `loadWork`, max 100); new-by-URL
chapters notify under a group summary. Code:
[`plugin/updates/`](../app/src/main/java/com/personal/flowreader/plugin/updates/).

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
