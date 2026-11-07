# Agent guide: Flow Reader

Android eReader (Kotlin, Jetpack Compose Material 3, Room, DataStore, QuickJS plugins).
Single module: `app/`. Hub for code locations and reference docs: [`docs/agent/CONTEXT.md`](docs/agent/CONTEXT.md).

## Workspace

Open `flow-reader.code-workspace` (in the chats repo) for every task. It holds `flow-reader`,
`flow-reader-plugins` (public plugins) and `flow-reader-chats` (private backlog, saved plans, history). A repo's
rules, hooks and AGENTS.md load only when it is open. Use local desktop chats; cloud agents can't reach local
files, the emulator or adb. Start work with `/task <request>`.

## Build and test (Windows / PowerShell)

```powershell
.\gradlew.bat :app:compileDebugKotlin     # compile check
.\gradlew.bat :app:testDebugUnitTest      # JVM unit tests (app/src/test)
.\gradlew.bat :app:installDebug           # device; bundles ../flow-reader-plugins when present
.\scripts\verify.ps1 [-Full]              # build/tests if code changed, rules, duplicates, notes, couplings; -Full adds smoke check
.\scripts\setup-emulator.ps1              # boot windowed AVD flowreader_api36 + push Gutenberg test books
.\scripts\setup-emulator.ps1 -Recreate -Install -Import   # fresh AVD, app installed, books in library
```

If the Kotlin compiler crashes with `NotImplementedError: Unknown file` after deleting files, remove
`app/build/kotlin` (stale incremental cache) and rebuild.

## Lookup order

Use the owner's words, not your own names.

1. Registry: `rg -n -i "<owner's words>" docs/agent/registry.md`, then read the matching entries (feature,
   owner files, tests, pitfalls).
2. Couplings: for each file in play, `rg -n "<FileName>" docs/agent/seams.md` (places that must change together).
3. Code: `rg` for the symbols, related literals and `FlowApp` properties in `app/src`, and in
   `../flow-reader-plugins/` when the change is plugin-visible. Find callers with the steps below.

Every plan for a change, with or without `/task`, has these sections: **Existing code found** (registry entries,
paths, symbols, and searches that found nothing), **Affected code** (seams lines, callers found with the steps
below, covering tests), **Rules that apply** (from `.cursor/rules/*.mdc` whose globs match), **Device checks**.

## Finding callers

Most calls here go through an object kept somewhere else (`flow.pluginSync.run(...)`, `lists.dropUnlisted(...)`),
so search by the name first, then by the names that object goes by:

1. Count the definitions: `rg -n 'fun <name>\b' app/src/main`.
2. If there is one definition, `rg -n '\b<name>\(|::<name>\b' app/src` lists every call, including functions
   passed by name (`vm::closeRename`).
3. If several classes define the name (`get`, `run`, `submit`):
   - Find where the class's object is kept: `rg -n ':\s*<Class>\b|=\s*<Class>\(' app/src/main`. This gives
     `FlowApp` fields (`lateinit var pluginSync: TwoWaySync`), constructor parameters and other fields.
   - Find shortcuts to those fields: `rg -n 'get\(\) = .*\b<field>\b' app/src/main` (for example
     `private val sync get() = app.pluginSync`).
   - Search the calls through those names: `rg -n '\b(<field>|<shortcut>)\.<name>\(' app/src`. For a
     constructor parameter or field, only hits in the file that declares it count.
4. Properties and flows have no brackets: `rg -n '\.<prop>\b' app/src`.
5. Names plugins can see (host API, manifest fields, string ids): search both repos. In the plugins repo,
   search one plugin folder at a time, because every plugin defines `pathOf`, `search` and `loadChapter`.
6. Tests that cover it: `rg -n '<name>' app/src/test`.

## Search rules

- Cursor's Grep tool can silently return nothing for large files (confirmed on the 109 KB `TtsController.kt`). For
  existence, caller and "is this used" checks, run `rg` through Shell.
- `rg` skips dot-folders (`.cursor`, `.github`) and gitignored paths unless you pass `--hidden`.
- Source files use CRLF; patterns anchored with `$` need `rg --crlf`.
- PowerShell 5.1 strips embedded double quotes from native arguments. Use single-quoted patterns, or `rg -f <file>`.
  Quote Gradle `-P` arguments: `"-Pkotlin.incremental=false"`.
- Don't create files with Shell redirects (`>`); use the file tools.
- Never read a large file whole. Find the place with `rg -n`, then read a line range (over-limit files: `scripts/size-baseline.json`).
- Match repo roots with a trailing slash: `c:/git/flow-reader` is a prefix of `c:/git/flow-reader-plugins`.

## Definition of done

1. `scripts\verify.ps1` passed for the current files (`-Full` at handoff when app code changed).
2. Device checks ran, or are listed for the owner in the commit's `Device-check:` line.
3. Docs, `docs/agent/registry.md`, `docs/agent/seams.md` and rule files match the change.
4. The handoff quotes the "Rules changed" block. "Installed" alone is not done. Push only with the owner's go-ahead.

## Where knowledge lives

- Vocabulary, owner files, tests, pitfalls: `docs/agent/registry.md`. Couplings: `docs/agent/seams.md`.
- Feature rules by file glob: `.cursor/rules/*.mdc`; always-on rule: `working-agreement.mdc`.
- Runtime shape and plugin-visible changes: `docs/agent/architecture.md`, `docs/agent/cross-repo.md`.
- Behavior for people: `docs/`. Plugin contract: `docs/plugins/api.md`.
- Backlog, saved plans and handoffs, decisions as history: `../flow-reader-chats/` (`backlog.md`, `tasks/`, `decisions/`).

## Hooks and commits

Cursor hooks (source and details in `scripts/agent/README.md`) guard edits and Shell:

- **Edit lock.** The first edit in the app or plugins repo takes a per-chat lock. If another chat holds it, the edit
  is denied and names the holder. See it with `node $HOME/.cursor/hooks/flow-agent/lock.mjs status`; free yours with
  `lock.mjs release`. `/takeover` (owner only) takes over a lock.
- **Commits.** `git add`, `git commit` and other writing git commands are blocked. Use
  `node $HOME/.cursor/hooks/flow-agent/commit-mine.mjs -m "<area>: <msg>" --device "ran: ... | left for owner: ... | none: <reason>" [--also <path>...]`.
  It commits the files this chat edited plus `--also` paths (use it for files changed through Shell), and they must
  match the last passing verify stamp.
- **Verify reminder.** If a turn changes files that don't match the last passing verify, a `[verify-gate]`
  follow-up asks for `scripts\verify.ps1`.
- **Refusals.** If an edit or command is refused, don't work around it (no alternate path, Shell write, or edit of
  hook files). Tell the owner what was refused and why.

## Hard rules

- UI: use the design system ([`docs/ui-system/README.md`](docs/ui-system/README.md)); never `AlertDialog`,
  `Dialog`, `ModalBottomSheet`, M3 `Card`, `Scaffold`, or literal `dp`/alpha/`zIndex`.
- Plugins: contract in [`docs/plugins/api.md`](docs/plugins/api.md); plugin-visible changes follow
  [`docs/agent/cross-repo.md`](docs/agent/cross-repo.md). Alpha: only the current `apiVersion` is supported.
- Keep files under ~500 lines; split by responsibility within the package. Over-limit files: `scripts/size-baseline.json`.
- Pure logic (layouts, adapters, parsers) stays Android-free and gets JVM unit tests.
- Match surrounding code; comments only for constraints the code can't show.
- License AGPL-3.0; adapted code keeps its credit header.
