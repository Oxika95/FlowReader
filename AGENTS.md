# Agent guide — Flow Reader

Android eReader (Kotlin, Jetpack Compose Material 3, Room, DataStore, QuickJS plugins).
Single module: `app/`.

## Start here

1. [`docs/agent/map.md`](docs/agent/map.md): areas, "change X → edit Y", cross-repo checklist.
2. [`docs/agent/index.md`](docs/agent/index.md): generated file/declaration/line index. Read large
   files by offset, never whole.
3. Area history (decisions, rejected ideas, open threads): `../flow-reader-chats/areas/<area>.md`
   (private sibling repo; skip if absent). Workflow: `../flow-reader-chats/workflow.md`.
4. Feature docs: [`docs/README.md`](docs/README.md).

Sibling repos: `../flow-reader-plugins` (public plugins), `../flow-reader-chats` (private context).

## Build and test (Windows / PowerShell)

```powershell
.\gradlew.bat :app:compileDebugKotlin      # compile check
.\gradlew.bat :app:testDebugUnitTest       # JVM unit tests (app/src/test)
.\gradlew.bat :app:installDebug            # device; bundles ../flow-reader-plugins when present
.\scripts\gen-agent-index.ps1              # after adding, moving, or splitting files
```

If the Kotlin compiler crashes with `NotImplementedError: Unknown file` after deleting files, remove
`app/build/kotlin` (stale incremental cache) and rebuild.

## Hard rules

- UI: use the design system ([`docs/ui-system/README.md`](docs/ui-system/README.md)); never
  `AlertDialog`, `Dialog`, `ModalBottomSheet`, M3 `Card`, `Scaffold`, or literal `dp`/alpha/`zIndex`.
  Details load automatically from `.cursor/rules/ui-system.mdc` when editing UI files.
- Plugins: contract in [`docs/plugins/api.md`](docs/plugins/api.md); plugin-visible changes follow the
  cross-repo checklist in `map.md` and keep older `apiVersion`s working (`plugin-api.mdc`).
- Match surrounding code; comments only for constraints the code can't show.
- Pure logic (layouts, adapters, parsers) stays Android-free and gets JVM unit tests.
- Keep files under ~500 lines; split by responsibility within the same package.
- Update the relevant doc in `docs/` with behavior changes; regenerate `index.md` when files change.
