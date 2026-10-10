# Agent guide: Flow Reader

Android eReader (Kotlin, Jetpack Compose Material 3, Room, DataStore, QuickJS plugins).
Single module: `app/`.

## Where to go

- Task workflow (plan, implement, verify, handoff, release): `../flow-reader-chats/CONTEXT.md`
  (private sibling repo).
- Code locations and reference docs: [`docs/agent/CONTEXT.md`](docs/agent/CONTEXT.md). Use it
  directly when the chats repo is absent.

| Folder | Holds |
| --- | --- |
| `app/` | All app code and tests |
| `docs/` | Feature docs ([hub](docs/README.md)), `agent/` (map, index), `plugins/` (contract), `ui-system/` |
| `.cursor/rules/` | Glob-scoped rules, auto-loaded when matching files are touched |
| `.cursor/commands/` | `/start`, `/handoff`, `/review`, `/release`, `/status` (stage triggers) |
| `scripts/` | Emulator setup, agent index generator |
| `brand/` | Icon sources and build |

Sibling repos: `../flow-reader-plugins` (public plugins), `../flow-reader-chats` (private context).

## Build and test (Windows / PowerShell)

```powershell
.\gradlew.bat :app:compileDebugKotlin # compile check
.\gradlew.bat :app:testDebugUnitTest # JVM unit tests (app/src/test)
.\gradlew.bat :app:installDebug # device; bundles ../flow-reader-plugins when present
.\scripts\setup-emulator.ps1 # boot windowed AVD flowreader_api36 + push Gutenberg test books
.\scripts\setup-emulator.ps1 -Recreate -Install -Import # fresh AVD, app installed, books in library
.\scripts\gen-agent-index.ps1 # after adding, moving, or splitting files
```

If the Kotlin compiler crashes with `NotImplementedError: Unknown file` after deleting files, remove
`app/build/kotlin` (stale incremental cache) and rebuild.

## Hard rules

- UI: use the design system ([`docs/ui-system/README.md`](docs/ui-system/README.md)); never
 `AlertDialog`, `Dialog`, `ModalBottomSheet`, M3 `Card`, `Scaffold`, or literal `dp`/alpha/`zIndex`.
 Details load automatically from `.cursor/rules/ui-system.mdc` when editing UI files.
- Plugins: contract in [`docs/plugins/api.md`](docs/plugins/api.md); plugin-visible changes follow
 [`docs/agent/cross-repo.md`](docs/agent/cross-repo.md). Alpha: only the current `apiVersion` is
 supported.
- Match surrounding code; comments only for constraints the code can't show.
- Pure logic (layouts, adapters, parsers) stays Android-free and gets JVM unit tests.
- Keep files under ~500 lines; split by responsibility within the same package. Files already over
 the limit: `docs/agent/map.md` `## Large files`.
- Update the relevant doc in `docs/` with behavior changes; regenerate the index when files change.
