# Elements

Code: [`ui/design/tabs/`](../../app/src/main/java/com/personal/flowreader/ui/design/tabs/),
[`ui/design/controls/`](../../app/src/main/java/com/personal/flowreader/ui/design/controls/),
[`ui/design/FlowIcons.kt`](../../app/src/main/java/com/personal/flowreader/ui/design/FlowIcons.kt).

## Tabs

One bar, two levels. A tab is data (`FlowTab`); the bar owns metrics, indicator, scrolling.

```kotlin
FlowTabBar(
    tabs = listOf(
        FlowTab.text("Files", selected = tab == 0, onClick = { tab = 0 }),
        FlowTab.icon(Icons.Filled.Search, "Search", selected = tab == 1, onClick = { tab = 1 }),
        FlowTab.action(Icons.Filled.Add, "Add tab", open = addOpen, onClick = { addOpen = true }),
    ),
    level = FlowTabLevel.Primary,      // Primary | Secondary
    inset = FlowTokens.Pad.Screen,     // Pad.CardBody inside cards; Space.None when already padded
)

// Text-only shorthand:
FlowTabBar(flowTextTabs(listOf("About", "Audio"), selectedIndex) { idx -> tab = idx })

// Silhouette drawable shorthand (Settings):
FlowTabBar(flowImageTabs(listOf(R.drawable.ic_settings_about to "About"), selectedIndex) { idx -> tab = idx })
```

| Kind | Meaning |
| --- | --- |
| `FlowTab.text` | Destination with a label |
| `FlowTab.icon` | Destination shown as an icon (`contentDescription` required; long-press shows it as a tooltip) |
| `FlowTab.image` | Destination shown as a plugin's packaged `icon.png` silhouette, tinted with the tab colour like `FlowTab.icon`; same tooltip |
| `FlowTab.action` | Trailing button that opens something (Add tab, Account); `open` shows the selected state while its card is open |

`text` and `icon` take `badge: Int?`: above 0 draws an accent `FlowDot` at the label's or icon's
top-end (plugin Library tab and list sub-tabs with new chapters).

| Level | Use | Type | Indicator |
| --- | --- | --- | --- |
| `Primary` | Screen sections: library tabs, settings sections | `FlowType.tabPrimary` | `Comp.TabIndicatorPrimary` |
| `Secondary` | Sub-sections: plugin lists, Audio/Layout/Import sub-tabs | `FlowType.tabSecondary` | `Comp.TabIndicatorSecondary` |

Sizing (`FlowTabLayout`, unit-tested): each slot is at least its label width; leftover width is
shared (equal share first, then uncapped). If minimums don't fit, the bar scrolls and keeps the
selected tab in view.

## Buttons (`FlowButtons.kt`)

| Component | Where |
| --- | --- |
| `FlowPrimaryButton(label)` | One per card: the main action (full width, `Comp.ButtonPrimary`) |
| `RowScope.FlowSecondaryButton(label, destructive)` | Equal-width outlined buttons in a `Row` |
| `FlowTextAction(label, destructive)` | Card footers (`FlowActionRow`) |
| `FlowIconButton(icon, cd)` | Toolbar/header icons |
| `FlowCircleButton(icon, cd, selected, style)` | Rails: `OnCover` (media card) / `OnPage` (floating cards) |
| `FlowFab(icon, cd)` | `FlowScreen` FAB slot only |

## Settings rows (`FlowSettingsRows.kt`)

| Component | Notes |
| --- | --- |
| `FlowSection(title)` / `FlowLabel(text)` / `FlowHint(text, error)` | Headings and helper text |
| `FlowToggleRow(title, subtitle, checked, onCheckedChange)` | Whole row toggles; the switch is visual |
| `FlowSegmentedRow(title, subtitle, options, selected, optionLabel, onSelect)` | Compact multi-position pill toggle (chip height, `Space.S` segment padding) in the switch slot; 2–3 short options, else `FlowChoiceChips` |
| `FlowSliderRow(label, value, onValueChange, valueRange, steps, valueLabel, startCaption, endCaption)` | |
| `FlowTextField(value, onValueChange, label, placeholder, supportingText, isError, trailingIcon, …)` | `Shape.Field` |
| `FlowDropdownRow(selected, options, optionLabel, onSelect, label)` | |
| `FlowChipRow { FlowChip(...) }` / `FlowChoiceChips(options, selected, optionLabel, onSelect)` | Wrapping chip rows |
| `FlowCollapsible(title, subtitle, initiallyExpanded) { }` / `FlowCollapsibleBox(visible) { }` | Expandable sections |

## List rows (`FlowListRow.kt`)

| Component | Notes |
| --- | --- |
| `FlowListRow(title, meta, subtitle, subtitleError, info) { actions }` | Compact item: title with meta beside it, optional one-line subtitle, trailing `FlowIconButton`s. Long `info` text hides behind an info icon and expands inline |
| `FlowSectionRow(title, info) { actions }` | Section heading with trailing icon actions (e.g. `+`) on the same line |
| `FlowInfoButton(expanded, onClick, contentDescription)` | The info icon itself (outlined, filled while open) |

## Pull to refresh (`ui/design/layer/FlowPullRefresh.kt`)

`FlowPullRefresh(refreshing, onRefresh, modifier) { list }` wraps a list area (M3
`PullToRefreshBox`). The content must scroll vertically: `FlowDisplayList` / `FlowDisplayGrid`, or
`FlowEmptyState`, which scrolls so an empty list still pulls. Used by the plugin tab's lists.

## Meta (`FlowBadge.kt`)

`FlowBadge(label, onCover)`, `FlowStat(stat, color)`, `FlowMetaRow(badges, stats, onCover)`:
wrapping row of pills then icon+value stats. Used by display and media cards.

**New items:** `FlowCountBadge(count)` is a `colorScheme.primary` (accent) circle with a bold
"+N" in `onPrimary`; "+99" above 99, nothing at 0; size `Comp.CountBadge`. It marks new chapters
on plugin stories only (never Files): display rows (trailing edge, centred, half past the border),
tiles (cover top-right), and the media card title. `FlowDot()` (`Comp.TabDot`) is the number-less
version; tabs show it through `FlowTab(badge = n)` when `n > 0`.

## Icons (`FlowIcons`)

`FlowIcons.forToken(token)` maps string tokens (manifest `lists[].icon`, plugin card stats and
actions, adapters) to Material icons. Unknown → `bookmark`.

`add`, `favorite`/`heart`, `schedule`/`clock`, `bookmark`, `star`, `check`, `visibility`/`eye`,
`list`, `flag`, `download`, `pages`, `user`, `followers`, `comment`, `like`, `link`, `share`,
`notifications`/`bell`, `lock`, `edit`.

The token list is plugin API: add tokens freely, never rename or remove one.

## Covers (`ui/design/surface/FlowCover.kt`, `ui/common/BookCover.kt`)

`FlowCover(art, title, blur)` draws art (cropped) or a title placeholder. Load with `rememberBookCover(book, CoverEdge.X)`
(library) or `rememberRemoteCover(url, CoverEdge.X)` (plugin search; LRU-cached).
`FlowProgressBar(progress, track)` is the shared thin progress bar.
