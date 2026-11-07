# Flow UI system

One component per role. Feature code supplies **content**; the design system owns **layout,
layer, motion and theme**. Code: [`ui/design/`](../../app/src/main/java/com/personal/flowreader/ui/design/)
and tokens in [`ui/theme/`](../../app/src/main/java/com/personal/flowreader/ui/theme/).

| Page | Covers |
| --- | --- |
| [tokens.md](tokens.md) | `FlowTokens`, `FlowType`, `FlowMotion`, `FlowLayer` |
| [layers.md](layers.md) | Draw order, overlay stack, Back, docks, `FlowScreen` |
| [cards.md](cards.md) | Fullscreen, floating, display and media cards |
| [elements.md](elements.md) | Tabs, buttons, settings rows, badges, icons |
| [recipes.md](recipes.md) | Step-by-step: add a tab, card, dock item, media source |

Plugin-facing contract: [plugins/ui-contract.md](../plugins/ui-contract.md).

## Map

### Screen types

| Screen | Shell | System bars | Top dock | Bottom dock | FAB |
| --- | --- | --- | --- | --- | --- |
| Library | `FlowScreen(Library)` | Visible; docks/actions clear them | — | `NowPlayingCard` while TTS plays | Per tab (add book / paste / plugin add) |
| Reader | Custom `Box` + two `FlowDock`s | Hidden (immersive) | `TitleBannerCard`, edge chip | Edge chip, `MediaControlCard`, `ScrollLockControls` | — |

Both screens sit inside one `FlowOverlayHost` (in `MainActivity`), so fullscreen cards stack
over either screen the same way.

### Card types

| Type | Component | Layer | Positioned by | Examples |
| --- | --- | --- | --- | --- |
| Fullscreen | `FlowFullscreenCard` (`Standard` / `Hero` / `Compact`) | `Fullscreen` (stacked) | Overlay host (centered, scrim) | Settings, ToC, filter/router/parse editors, add tab/book, plugin sheets, synth log, share chooser |
| Confirm | `FlowConfirmCard` (Compact fullscreen) | `Fullscreen` | Overlay host | Uninstall, third-party repo, sync choice, info |
| Media | `FlowMediaCard` (Hero fullscreen) + `MediaCardModel` | `Fullscreen` | Overlay host | Files book splash, plugin story card |
| Floating | `FlowFloatingCard` / `FlowFloatingChip` | `Dock` | `FlowDock` item | Title banner, media controls, now playing, playback pin, jump-back chip |
| Display | `FlowDisplayCard` (`Row` / `Tile`) | `Content` | `FlowDisplayList` / `FlowDisplayGrid` | Library books, queue rows, plugin search results |

### UI elements

| Element | Component | Notes |
| --- | --- | --- |
| Tab bar | `FlowTabBar(tabs, level)` | `Primary` = screen tabs (library, settings sections); `Secondary` = sub-tabs (plugin lists, settings sub-sections) |
| Tab | `FlowTab.text` / `.icon` / `.image` / `.action` | A tab is data, not a composable. Icon-first (rule 8) |
| Buttons | `FlowPrimaryButton`, `FlowSecondaryButton`, `FlowTextAction`, `FlowIconButton`, `FlowCircleButton`, `FlowFab` | |
| Settings rows | `FlowToggleRow`, `FlowSliderRow`, `FlowDropdownRow`, `FlowTextField`, `FlowChoiceChips`, `FlowCollapsible` | |
| Text | `FlowSection`, `FlowLabel`, `FlowHint` | |
| Meta | `FlowBadge`, `FlowStat`, `FlowMetaRow`, `FlowDot`, `newChaptersLabel` | "N new" chip / notification dot |
| Icons | `FlowIcons.forToken` | Shared with plugins |

### Layers (back to front)

```
Content (0)  →  ContentScrim (10)  →  Dock (20)  →  Action (30)  →  Fullscreen stack (40+)  →  System (90)
```

Only the top fullscreen card draws a scrim; parents shrink to `Scrim.ParentScale` beneath it.
Details: [layers.md](layers.md).

## Rules

1. **Never build a card from scratch.** Use the card for the role; pass content.
2. **Never position floating UI yourself.** Put it in a `FlowDock` item.
3. **Never call `AlertDialog`, `Dialog`, `ModalBottomSheet`, or M3 `Card`.** Use
   `FlowConfirmCard` / `FlowFullscreenCard` / `FlowSurface`.
4. **No raw `dp`, alpha or z-index literals in feature code.** Add a token if one is missing.
5. **Tabs are lists.** New tab = new `FlowTab` entry, never a new bar.
6. **Nested fullscreen cards stack.** Open the child while the parent stays `visible`; Back pops one.
7. **Content pads by dock height** (`FlowScreenPadding`), never by guessed constants.
8. **Icon-first tabs.** A fixed, recurring destination with an obvious icon is a `FlowTab.icon`
   (Files, Queue, plugin Search, plugin lists via manifest `lists[].icon`); a plugin tab whose
   package ships `icon.png` is a `FlowTab.image` (a silhouette, tinted like icon tabs). User-named things stay text (custom shelves, plugin names
   without an icon, plugin lists without `icon`). Settings sections and sub-tabs are image tabs from
   `res/drawable-nodpi/ic_settings_*.png` via `flowImageTabs`; Settings > Plugins reuses each plugin's
   `icon.png`; Filters scopes stay text. Settings › Layout › UI switches Library or Settings bars to
   text (`ChromePrefs`, `FlowApp.chrome`); action tabs stay icons. Every icon/image tab passes a
   `contentDescription`; long-press shows it as a tooltip.

## Use this instead

Raw Material or hand-built UI in feature code usually has a design-system replacement. Look here
before writing a row, button, list or loader.

| Instead of | Use |
| --- | --- |
| `AlertDialog`, `Dialog`, `ModalBottomSheet` | `FlowConfirmCard` (confirm, info) or `FlowFullscreenCard` (everything else) |
| M3 `Card`, a hand-built panel | `FlowFullscreenCard`, `FlowFloatingCard` in a `FlowDock`, `FlowDisplayCard` in a list, or `FlowSurface` inside the design system |
| `Scaffold`, hand-padded screen | `FlowScreen(kind, topDock, bottomDock, fab, snackbar)`; pad by `FlowScreenPadding` |
| `PrimaryTabRow`, `Tab`, a new bar | `FlowTabBar` with `FlowTab` entries; in Settings, `SettingsSubTabBar` |
| Material `Switch` in a row | `FlowToggleRow` (subtitle is required) |
| raw `Slider` with captions | `FlowSliderRow` |
| `OutlinedTextField` | `FlowTextField` (extend it if it lacks a parameter) |
| `ExposedDropdownMenuBox` | `FlowDropdownRow` |
| `FilterChip` rows | `FlowChoiceChips` |
| `TextButton`, `IconButton` | `FlowTextAction`, `FlowIconButton` |
| `Button`, `FilledIconButton`, a hand-sized floating button | `FlowPrimaryButton`, `FlowSecondaryButton`, `FlowFab`, `FlowCircleButton` |
| a clickable title with expand arrows, bare `AnimatedVisibility` | `FlowCollapsible`, or `FlowCollapsibleBox` when the caller owns the state |
| a row with title, meta and action buttons | `FlowListRow`, `FlowSectionRow` |
| a "?" button that opens help | `FlowInfoButton` |
| a drag-to-reorder or multi-select list | `EditableRuleList` (five callers; check all) |
| a badge, "N new" chip or dot | `FlowBadge`, `newChaptersLabel`, `FlowDot` |
| a progress bar | `FlowProgressBar` |
| pull-to-refresh | `FlowPullRefresh`; an empty list message that still scrolls is `FlowEmptyState` |
| item metadata card for a new source | `FlowMediaCard` with a new pure adapter, not new parameters |
| decoding a cover bitmap | `rememberBookCover`, `rememberRemoteCover`, `FlowCover` |
| `MaterialTheme.typography.*` | `FlowType` roles |
| literal `dp`, alpha, `zIndex` | `FlowTokens`, `FlowLayer`; add a token if one is missing |
| a Material icon import | `FlowIcons.forToken` (the same tokens plugins use) |
