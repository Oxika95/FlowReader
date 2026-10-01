# Plugin UI contract

Plugins never draw UI. The app renders its standard components ([UI system](../ui-system/README.md))
from the manifest and from data the plugin returns. Plugin UI code:
`app/src/main/java/com/personal/flowreader/ui/plugin/`.

## Plugin tab

Each enabled plugin adds one library tab (next to Files and Queue).

- **Sub-tabs**: a `Secondary` `FlowTabBar`. Order:
  1. One text tab per manifest `lists[]` entry, in declared order (local stories on that list).
  2. **Search** icon tab, if `search` is declared.
  3. Trailing action: **Account** (if `auth`), else **Plugin settings** (if `settings`).
- **Body**: local stories use `LibraryBooksPane` (the Files display cards, following the library
  view mode). Search results use `WorkCard` (a `FlowDisplayCard` row).
- **FAB** (`PluginTabFab`): `+` in the library FAB slot, shown when `resolveUrl` or `search` is
  declared. Opens **Add from source**.

## Components

| Component | Built on | When | Content | Actions |
| --- | --- | --- | --- | --- |
| `WorkCard` | `FlowDisplayCard` (Row) | Search results, add sheet | Cover, title, `author · subtitle`, v2 `badges`/`stats` | Tap opens the media card |
| Library card | `FlowDisplayCard` | Local stories on a list | Same card as Files | Tap reads at saved progress; long-press opens the media card |
| `StoryMediaCard` | `FlowMediaCard` + `PluginMediaCardAdapter` | Opening any story | See [Media card](#media-card) | See [Media card](#media-card) |
| `DownloadSheet` | `FlowFullscreenCard` | Download on the media card | Download all; partial download with cache settings | Download all, Begin partial download, Download range |
| `LoginSheet` | `FlowFullscreenCard` | Account while signed out, or `AUTH_REQUIRED` | One field per `auth.fields[]` (secret fields masked), `auth.note` | Sign in, Cancel |
| `AccountSheet` | `FlowFullscreenCard` | Account while signed in | Account name | Sync lists (if any list is `syncable`), Settings, Sign out |
| `SyncChoiceSheet` | `FlowConfirmCard` | After sign-in / Sync | Merge vs Overwrite | Merge, Overwrite, Cancel |
| `PluginSettingsSheet` | `FlowFullscreenCard` | Settings > Import > Plugins > plugin, or the tab's settings action | One row per manifest `settings[]` entry | Edits persist immediately |
| `AddFromSourceSheet` | `FlowFullscreenCard` | Tab FAB | Paste URL (`resolveUrl`); search box (`search`) | Go, Search, result tap |

All sheets stack over the media card (Back closes the top one).

## Media card

The story media card is the shared `FlowMediaCard`. The host fills every slot; a v2 plugin
supplies some slots through `WorkDetail.card`.

```
cover band ─ title ............................. host (WorkDetail.title)
             subtitle .......................... host (author)
             badges ............................ v2 card.badges   | v1 status
             stats ............................. v2 card.stats    | v1 rating (star), views (eye)
                                                 + host chapter count (pages), always last
             tags, synopsis .................... host (WorkDetail.tags / synopsis)
             chapter cache strip ............... host
rail ─────── list toggles (membershipToggle) ... host, manifest order
             Share ............................. host
             plugin rail actions (≤2) .......... v2 card.actions placement "rail"
body ─────── status line ....................... host ("Cached 12 / 40 chapters · cache level 5")
             links (≤3) ........................ v2 card.links
footer ───── Download, Refresh, Delete ......... host
             plugin footer actions (≤2) ........ v2 card.actions placement "footer"
             Read (primary) .................... host
             error ............................. host
```

## Rules

1. There are no plugin-defined card types, layouts, colors or fonts. Plugins fill slots.
2. Host actions (list toggles, Share, Download, Refresh, Delete, Read) are always present, in a
   fixed order. A plugin cannot remove, reorder or shadow them (reserved ids are dropped).
3. Plugin actions are capped (2 rail, 2 footer) and routed to `cardAction`. Rail actions render
   as circular icon buttons (`toggle` → filled accent while `on`); footer actions as outlined
   secondary buttons.
4. Rail list toggles appear only for lists with `membershipToggle: true`, in manifest order, and
   are filled while the story is on that list.
5. Missing optional fields hide their UI (no badges row, no tags, no synopsis, no links).
6. Busy state and errors go through the library host (`LibraryPluginActions`); `AUTH_REQUIRED`
   opens the `LoginSheet`. `cardAction` toasts appear in the library snackbar.
7. Cache strip colors: gray = cached behind the locus, desaturated accent = cached ahead,
   saturated accent = the locus (reading position or download start).
8. Links open in the system browser.

## Icon tokens

Used by `lists[].icon`, `card.stats[].icon`, `card.actions[].icon`, `Work.stats[].icon`.
Unknown tokens fall back to `bookmark`.

| Token | Aliases | Typical use |
| --- | --- | --- |
| `add` | | Follow list |
| `favorite` | `heart` | Favorites |
| `schedule` | `clock` | Read later |
| `bookmark` | | Default |
| `star` | | Rating |
| `check` | | Completed / read |
| `visibility` | `eye` | Views |
| `list` | | Generic list |
| `flag` | | Report / flag |
| `download` | | Downloads |
| `pages` | | Pages / chapters |
| `user` | | Author / account |
| `followers` | | Followers |
| `comment` | | Comments |
| `like` | | Likes |
| `link` | | Links |
| `share` | | Share |

Source of truth: `FlowIcons.tokens` in `ui/design/FlowIcons.kt`. Tokens are only ever added.
