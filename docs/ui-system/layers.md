# Layers, overlays and docks

Code: [`ui/design/layer/`](../../app/src/main/java/com/personal/flowreader/ui/design/layer/).

## Window structure

```
MainActivity
└─ FlowOverlayHost                        provides LocalFlowOverlays
   ├─ NavHost (Library | Reader)          FlowLayer.Content … Action
   ├─ overlay layer                       FlowLayer.Fullscreen + depth
   │   ├─ scrim (under the top card only)
   │   └─ entries in open order (parents scaled to Scrim.ParentScale)
   └─ system slot                         FlowLayer.System (debug bubble)
```

`ShareIngressActivity` and the share overlay window each host their own `FlowOverlayHost`.

Within content, `FlowLayer.ContentLifted` raises a list row being dragged over its siblings.

## Overlay stack (`FlowOverlayHost`, `rememberFlowOverlay`)

- A fullscreen card registers itself while `visible = true`, wherever it is composed (a screen,
  a tab, inside another card). The host draws it; the caller's composition locals are captured,
  so theme and view models work.
- Order = open time. The newest visible entry is the **top**.
- Only the top entry gets the scrim, Back, and scrim-tap dismiss (`FlowOverlaySpec.dismissible`).
- Parents stay composed and visible, scaled to `Scrim.ParentScale`.
- Exit animations keep the last content frozen, so callers may null their state immediately.
- `FlowOverlayState.isEmpty` hides the FAB (`FlowScreen`).

### Back

| State | Back does |
| --- | --- |
| One or more fullscreen cards | Calls top card's `onDismiss` (unless `dismissible = false`) |
| Card with `onBack` (multi-pane) | Header arrow calls `onBack`; system Back still dismisses |
| No cards, Reader | Navigates to Library |
| No cards, Library | Exits (system) |

### Stacking example

Settings → Filters → edit filter: Settings stays `visible` while the filter editor opens. The
editor is the top card; Back closes it and returns to Settings.

```kotlin
SettingsOverlay(visible = overlay == ReaderOverlay.Settings, ...)   // do not hide while a child is open
FilterEditorCard(visible = filterEditor != null, ...)
```

## Docks (`FlowDock`)

```kotlin
FlowDock(DockEdge.Top, Modifier.align(Alignment.TopCenter)) {
    Item(visible = chromeOpen) { TitleBannerCard(...) }
    Item(visible = edgeChipTop) { FlowFloatingChip("Jump back to saved position", onJump) }
}
```

- Declare items in visual order, top to bottom, in both docks.
- Each item carries `Dock.Gap` on its screen-edge side (top dock: above it; bottom dock: below
  it), so the outermost item sits one gap from the edge and hidden items leave no hole.
- Items are inset horizontally by `Pad.Screen` and fill the dock width.
- Items animate with `FlowMotion.dockEnter/Exit`.
- `onOccupiedHeight` reports the dock height; `FlowScreen` passes it to content.

## `FlowScreen`

```kotlin
FlowScreen(
    kind = FlowScreenKind.Library,
    bottomDock = { Item(visible = tts.playing) { NowPlayingCard(...) } },
    fab = { FlowFab(Icons.Filled.Add, "Add book", onAdd) },
    snackbar = { SnackbarHost(snackbarHost) },
) { docks ->
    LibraryBooksPane(..., bottomInset = docks.bottom)
}
```

- `Library`: content starts below the status bar; docks and actions clear status/navigation bars.
  `Reader`: raw edges (immersive).
- Draws content, docks, FAB (bottom-end) and snackbar (above the FAB) at their layers.
- FAB hides while any fullscreen card is open.

The reader uses two `FlowDock`s directly inside its own `Box` because its content (reading
column, rails, tap bands) needs custom gesture layering.
