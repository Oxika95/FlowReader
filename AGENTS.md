# Agent guide — Flow Reader

Android eReader (Kotlin, Jetpack Compose Material 3, Room, DataStore, QuickJS plugins).
Single module: `app/`. Feature docs: [`docs/README.md`](docs/README.md).

## Build and test (Windows / PowerShell)

```powershell
.\gradlew.bat :app:compileDebugKotlin      # compile check
.\gradlew.bat :app:testDebugUnitTest       # JVM unit tests (app/src/test)
.\gradlew.bat :app:installDebug            # device; bundles ../flow-reader-plugins when present
```

If the Kotlin compiler crashes with `NotImplementedError: Unknown file` after deleting files, remove
`app/build/kotlin` (stale incremental cache) and rebuild.

## UI: use the design system

Read [`docs/ui-system/README.md`](docs/ui-system/README.md) before touching UI. Summary:

| Need | Use | Never |
| --- | --- | --- |
| Modal content (settings, editor, list, sheet) | `FlowFullscreenCard` | `Dialog`, `ModalBottomSheet`, custom scaffolds |
| Confirmation / info | `FlowConfirmCard` | `AlertDialog` |
| Item metadata + actions (book, story) | `FlowMediaCard` + an adapter in `MediaCardAdapters.kt` | Per-feature splash layouts |
| List / grid item | `FlowDisplayCard` in `FlowDisplayList` / `FlowDisplayGrid` | M3 `Card`, ad-hoc rows |
| Floating bar/card over content | `FlowFloatingCard` inside a `FlowDock` item | Manual `Box` alignment + padding |
| Tabs / sub-tabs | `FlowTabBar(tabs = List<FlowTab>, level)` | New tab bar composables |
| Primary screen | `FlowScreen(kind, topDock, bottomDock, fab, snackbar)` | M3 `Scaffold` |
| Settings controls | `FlowToggleRow`, `FlowSliderRow`, `FlowTextField`, `FlowDropdownRow`, `FlowChoiceChips` | Raw M3 controls with custom padding |
| Sizes, alphas, z-order, motion | `FlowTokens`, `FlowType`, `FlowLayer`, `FlowMotion` | Literal `dp` / alpha / `zIndex` |

Behavior:

- Fullscreen cards register with the window overlay stack from wherever they're composed. Nested
  cards **stack**: keep the parent `visible` while the child is open. Back closes only the top card.
- Floating cards have no `visible` parameter; the dock item animates them.
- Content pads by the dock heights it receives (`FlowScreenPadding`).

Layout: design system `app/src/main/java/com/personal/flowreader/ui/design/`, tokens `ui/theme/`,
features `ui/library`, `ui/reader`, `ui/settings`, `ui/plugin`, share UI in `share/`.

## Plugins

- API contract: [`docs/plugins/api.md`](docs/plugins/api.md) (apiVersion 2; v1 still accepted).
- What the app renders: [`docs/plugins/ui-contract.md`](docs/plugins/ui-contract.md).
- Plugins never draw UI. They fill declarative slots (`WorkDetail.card`: stats, badges, links,
  ≤2 rail + ≤2 footer actions → `cardAction`). Host actions are fixed.
- Changing anything plugin-visible (models in `plugin/api/`, icon tokens in `FlowIcons`, media
  card slots, caps) = update `docs/plugins/*` and the schemas in `docs/plugins/schema/`, and keep
  older `apiVersion`s working. Icon tokens are append-only.
- Official plugins live in the sibling repo `../flow-reader-plugins` (`npm test`, `npm run build`).

## Conventions

- Match surrounding code; comments only for constraints the code can't show.
- Pure logic (layouts, adapters, parsers) stays Android-free and gets JVM unit tests.
- Update the relevant doc in `docs/` with behavior changes.
