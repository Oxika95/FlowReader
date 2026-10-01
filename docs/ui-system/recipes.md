# Recipes

## Add a tab

Append a `FlowTab` to the list that feeds the existing bar. Never create a new bar.

```kotlin
val tabs = buildList {
    add(FlowTab.text("Files", selected = sel == Files, onClick = { sel = Files }))
    add(FlowTab.text("Archive", selected = sel == Archive, onClick = { sel = Archive }))   // new
}
FlowTabBar(tabs, level = FlowTabLevel.Primary)
```

Settings sections and sub-tabs use `flowTextTabs(labels, idx, onSelect)`; add the label.

## Add a fullscreen card (editor, sheet, list)

```kotlin
@Composable
fun ArchiveEditorCard(visible: Boolean, draft: Draft, onDismiss: () -> Unit, onSave: (Draft) -> Unit) {
    var d by remember(draft) { mutableStateOf(draft) }
    FlowFullscreenCard(
        visible = visible,
        onDismiss = onDismiss,
        title = "Edit archive",
        footer = {
            FlowActionRow {
                FlowTextAction("Cancel", onDismiss)
                FlowTextAction("Save", { onSave(d) }, enabled = d.name.isNotBlank())
            }
        },
    ) {
        FlowTextField(d.name, { d = d.copy(name = it) }, label = "Name")
        FlowToggleRow("Auto-archive", "Archive finished books", checked = d.auto, onCheckedChange = { d = d.copy(auto = it) })
    }
}
```

- Compose it next to its trigger; it renders in the window overlay automatically.
- Opened from another card? Keep the parent `visible`; the child stacks on top.
- Long list body? `scrollable = false`, `height = FlowCardHeight.Fill`, `LazyColumn(Modifier.weight(1f))`.
- Confirmation? `FlowConfirmCard`, not `AlertDialog`.

## Add a settings section or row

1. Add the section label to `SettingsSections` (`ui/settings/SettingsOverlay.kt`) and a branch in the
   `when (tab)` body.
2. Build the body from `FlowSection` / `FlowToggleRow` / `FlowSliderRow` / `FlowDropdownRow` /
   `FlowChoiceChips`. No `verticalScroll` (the card scrolls).

## Add a floating (dock) card

```kotlin
FlowDock(DockEdge.Bottom, Modifier.align(Alignment.BottomCenter)) {
    Item(visible = sleepTimerActive) {
        FlowFloatingCard { SleepTimerRow(...) }
    }
    Item(visible = chromeOpen) { MediaControlCard(...) }
}
```

Library: pass items through `FlowScreen(bottomDock = { ... })` and pad content by
`docks.bottom`. Do not add `visible` parameters or enter/exit animations to the card itself.

## Add a display list

```kotlin
FlowDisplayList(contentPadding = flowDisplayListPadding(bottomExtra = docks.bottom)) {
    items(items, key = { it.id }) { FlowDisplayCard(title = it.title, art = null, onClick = { open(it) }) }
}
if (items.isEmpty()) FlowEmptyState("Nothing here yet.")
```

## Add a media card source

1. Define a pure-Kotlin info class and `XMediaCardAdapter.model(info, busy): MediaCardModel` in
   `ui/design/card/media/MediaCardAdapters.kt`. Exactly one `Primary` footer action, last.
2. Unit-test the adapter (`MediaCardAdaptersTest`).
3. Call `FlowMediaCard(visible, model, art, onDismiss, onAction)` and route ids with
   `MediaActionIds` (and `MediaActionIds.listIdOf` for `list:` toggles).

## Add a token

1. Add it to the right group in `FlowTokens` (or `FlowType` / `FlowMotion` / `FlowLayer`).
2. Document it in [tokens.md](tokens.md).
3. Replace literals that should use it.

## Checklist before finishing UI work

- [ ] No `AlertDialog` / `Dialog` / `ModalBottomSheet` / M3 `Card` / `Scaffold` in feature code.
- [ ] No raw `dp`/alpha/z literals outside `ui/theme` and `ui/design`.
- [ ] New tab = list entry; new card = existing card role.
- [ ] Floating UI is in a dock item; content pads by `FlowScreenPadding`.
- [ ] Nested cards stack (parent stays visible); Back pops one level.
- [ ] Docs updated (`docs/ui-system/`, and `docs/plugins/` if plugin-visible).
