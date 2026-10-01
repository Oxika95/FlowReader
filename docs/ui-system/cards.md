# Cards

Every card is built on `FlowSurface` (page color, hairline border, `Shape.Card`, no elevation).
`FlowSurfaceStyle.Panel` adds the feather halo (drawn outside bounds, never in layout or touch
area); `Flat` is a hard edge for lists.

| Role | Component | File |
| --- | --- | --- |
| Fullscreen | `FlowFullscreenCard`, `FlowCardHeader`, `FlowActionRow`, `FlowTextAction`, `FlowConfirmCard` | `ui/design/card/FlowFullscreenCard.kt` |
| Floating | `FlowFloatingCard`, `FlowFloatingChip` | `ui/design/card/FlowFloatingCard.kt` |
| Display | `FlowDisplayCard`, `FlowDisplayList`, `FlowDisplayGrid`, `FlowEmptyState` | `ui/design/card/FlowDisplayCard.kt` |
| Media | `FlowMediaCard`, `MediaCardModel`, adapters, `MediaSegmentStrip` | `ui/design/card/media/` |

## Fullscreen card

```kotlin
FlowFullscreenCard(
    visible = open,
    onDismiss = { open = false },
    title = "Settings",
    height = FlowCardHeight.Fill,                 // Wrap (default) | Fill
    tabs = { FlowTabBar(tabs = flowTextTabs(Sections, tab) { tab = it }, inset = FlowTokens.Pad.CardBody) },
    footer = { FlowActionRow { FlowTextAction("Cancel", onCancel); FlowTextAction("Save", onSave) } },
) {
    // ColumnScope body; scrolls by default
}
```

| Param | Meaning |
| --- | --- |
| `variant` | `Standard` (header + body + footer), `Hero` (no header/body padding, darker scrim, feathered; media card), `Compact` (narrow; confirmations) |
| `height` | `Wrap` follows content up to the window; `Fill` always full height (lists, logs) |
| `dismissible` | `false` blocks Back, scrim tap and the close button (e.g. while signing in) |
| `onBack` | Shows a back arrow (multi-pane cards such as Download → Partial) |
| `showClose` | Close button top-right (default on except `Hero`) |
| `scrollable` | `false` when the body hosts a `LazyColumn` (give it `Modifier.weight(1f)`) |
| `bodyPadding` / `bodySpacing` | Defaults: `Pad.CardBody` sides, `Space.S` between children |
| `tabs` | Pinned under the header (does not scroll) |
| `footer` | Pinned under the body. Use `FlowActionRow(start = { destructive / secondary }) { Cancel; Confirm }` |

Slots top to bottom: header, tabs, body, footer. Max width `Comp.CardMaxWidth`
(`CompactCardMaxWidth` for `Compact`).

**Footer order:** start side = Delete / Test / extra; end side = Cancel, then the confirming
action rightmost. Destructive actions use `destructive = true`.

### Confirm card

```kotlin
FlowConfirmCard(
    visible = pending != null,
    title = "Uninstall ${p.name}?",
    message = "Your downloaded stories are kept.",
    confirmLabel = "Uninstall",
    destructive = true,
    onConfirm = { uninstall() },
    onDismiss = { pending = null },
    // dismissLabel = "" → info card with a single action
    // extraActions = { FlowTextAction("Overwrite", ...) }
)
```

## Floating card

Only inside a `FlowDock` item. Full dock width, Panel surface, `Pad.FloatingCard` padding.

```kotlin
FlowDock(DockEdge.Bottom, Modifier.align(Alignment.BottomCenter)) {
    Item(visible = chromeOpen) {
        FlowFloatingCard { MediaControls(...) }
    }
}
```

| Instance | Screen | Dock |
| --- | --- | --- |
| `TitleBannerCard` | Reader | Top |
| `MediaControlCard` | Reader | Bottom |
| `PlaybackPinCard` (accent border) | Reader | Top or bottom (edge nearest the off-screen sentence) |
| Jump-back chip (`FlowFloatingChip`) | Reader | Same edge rule |
| `ScrollLockUnlockButton` | Reader | Bottom, aligned with the media card's lock button |
| `NowPlayingCard` | Library | Bottom (while TTS plays) |

## Display card

One card for every list/grid item.

```kotlin
FlowDisplayList(contentPadding = flowDisplayListPadding(bottomExtra = docks.bottom)) {
    items(books, key = { it.bookId }) { b ->
        FlowDisplayCard(
            title = b.title,
            art = cover,
            layout = FlowDisplayLayout.Row,       // Row (list) | Tile (shelf grid)
            subtitle = "Read 2 days ago",
            badges = listOf("Linked"),
            stats = listOf(MediaStat("star", "4.6", "Rating")),
            progress = b.readingProgress,
            corner = FlowCornerBadge(Icons.Filled.Link, "Linked file"),
            onClick = { open(b) },
            onLongClick = { showMedia(b) },
        )
    }
}
```

Empty slots are hidden. `Row` is `Comp.ListRow` high (grows when badges/stats are present).
`Tile` is a 2:3 cover with a dark title band. `FlowDisplayGrid` uses adaptive `Comp.GridMinCell`
columns. Convention: **tap = primary (read/open), long-press = media card**.

## Media card

The file/media card: cover-led metadata for one item, with host-owned actions.
Feature code never lays out a media card; it builds a `MediaCardModel` (usually via an adapter)
and routes actions by id.

```kotlin
val model = FileMediaCardAdapter.model(FileMediaInfo(...), busy)
FlowMediaCard(
    visible = book != null,
    model = model,
    art = cover,
    onDismiss = onClose,
    onAction = { a ->
        when (a.id) {
            MediaActionIds.OPEN -> open()
            MediaActionIds.SHARE -> share()
            MediaActionIds.REMOVE -> remove()
        }
    },
)
```

### Layout (fixed)

```
┌──────────────────────────── cover (blurred underlay + gradient) ───┐
│                                                  (rail ○ ○ ○)      │
│ ▓▓▓▓ black band ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ Title (titleMaxLines)                                              │
│ Subtitle                                                           │
│ [badge] [badge]  ★ 4.6  👁 1.2M  📖 120                           │
│ tags · tags                                                        │
│ synopsis                                                           │
│ progress bar | segment strip                                       │
└────────────────────────────────────────────────────────────────────┘
  status line
  links
  [secondary] [secondary] [secondary]     (3 per row)
  [            primary              ]
  error
```

### `MediaCardModel`

| Field | Notes |
| --- | --- |
| `title`, `subtitle`, `titleMaxLines` | |
| `stats: List<MediaStat(icon, value, label)>` | Icon tokens from `FlowIcons` |
| `badges`, `tags`, `synopsis` | Hidden when empty |
| `progress: Float?` / `segments: MediaSegments?` | Reading progress bar or chapter cache strip |
| `status`, `error` | Host text under the band |
| `links: List<MediaLink>` | Opened with `ACTION_VIEW` unless `onLink` is passed |
| `rail: List<MediaAction>` | Circular buttons, top-end of the cover (`Toggle` / `Icon`) |
| `footer: List<MediaAction>` | `Secondary` / `Destructive` buttons, then exactly one `Primary` |

`MediaAction(id, label, kind, icon, on, enabled, owner)`. `owner = Plugin` actions are routed to
the plugin (`cardAction`); `Host` actions are handled by the caller. Reserved host ids:
`MediaActionIds.READ/OPEN/DOWNLOAD/REFRESH/DELETE/REMOVE/SHARE` and `list:{listId}`.

### Adapters (`MediaCardAdapters.kt`, pure Kotlin, unit-tested)

| Adapter | Source | Stats | Rail | Footer |
| --- | --- | --- | --- | --- |
| `FileMediaCardAdapter` | Files tab book | Read %, format, size | — | Share, Remove, Open |
| `PluginMediaCardAdapter` | Plugin story | v2 `card.stats` or v1 rating/views, then chapters | List toggles, Share, plugin rail actions (≤2) | Download, Refresh, Delete, plugin footer actions (≤2), Read |

New media source = new adapter + a caller that routes ids. Do not add parameters to
`FlowMediaCard` for one source.
