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
| Reader | Custom `Box` + two `FlowDock`s | Hidden (immersive) | `TitleBannerCard`, edge chip | Edge chip, `MediaControlCard`, scroll-lock unlock | — |

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
| Tab | `FlowTab.text` / `.icon` / `.action` | A tab is data, not a composable |
| Buttons | `FlowPrimaryButton`, `FlowSecondaryButton`, `FlowTextAction`, `FlowIconButton`, `FlowCircleButton`, `FlowFab` | |
| Settings rows | `FlowToggleRow`, `FlowSliderRow`, `FlowDropdownRow`, `FlowTextField`, `FlowChoiceChips`, `FlowCollapsible` | |
| Text | `FlowSection`, `FlowLabel`, `FlowHint` | |
| Meta | `FlowBadge`, `FlowStat`, `FlowMetaRow` | |
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
