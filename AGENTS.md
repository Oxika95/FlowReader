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
.\scripts\graph.ps1                       # refresh the code graphs of both repos (verify runs it when they changed)
.\scripts\setup-emulator.ps1              # boot windowed AVD flowreader_api36 + push Gutenberg test books
.\scripts\setup-emulator.ps1 -Recreate -Install -Import   # fresh AVD, app installed, books in library
```

If the Kotlin compiler crashes with `NotImplementedError: Unknown file` after deleting files, remove
`app/build/kotlin` (stale incremental cache) and rebuild.

## Lookup order

Use the owner's words, not your own names. Start with the code graphs ([`docs/agent/graph.md`](docs/agent/graph.md));
the registry and couplings list cover what the graph can't see, and `rg` confirms names and callers.

1. Graph: `graphify query "<owner's words or a symbol>"`, `graphify explain "<Symbol>"` (one symbol's edges),
   `graphify affected "<Symbol>"` (what reaches it), `graphify path "<A>" "<B>"`. For plugin code add
   `--graph ../flow-reader-plugins/graphify-out/graph.json`. The `graphify` MCP server offers the same lookups
   (`query_graph`, `get_neighbors`, `shortest_path`; `project_path` for the plugins repo).
2. `rg -n -i "<owner's words>" docs/agent/registry.md`, then read the matching entries.
3. For each file in play: `rg -n "<FileName>" docs/agent/seams.md` (changes that must be matched elsewhere).
4. `rg` for the symbols, related literals and `FlowApp` properties in `app/src`, and in `../flow-reader-plugins/`
   when the change is plugin-visible. Callers always need this step: the graph has no edge for calls through an
   instance (every `flow.x.y()`), flows and properties, string ids, or the app-to-plugin boundary.

Every plan for a change, with or without `/task`, has these sections: **Existing code found** (graph queries,
entries, paths, symbols, and searches that found nothing), **Affected code** (`affected` results, seams lines,
callers confirmed with `rg`, covering tests), **Rules that apply** (from `.cursor/rules/*.mdc` whose globs match),
**Device checks**.

## Search rules

- Cursor's Grep tool can silently return nothing for large files (confirmed on the 109 KB `TtsController.kt`). For
  existence, caller and "is this used" checks, run `rg` through Shell.
- `rg` skips dot-folders (`.cursor`, `.github`) and gitignored paths unless you pass `--hidden`.
- Source files use CRLF; patterns anchored with `$` need `rg --crlf`.
- PowerShell 5.1 strips embedded double quotes from native arguments. Use single-quoted patterns, or `rg -f <file>`.
  Quote Gradle `-P` arguments: `"-Pkotlin.incremental=false"`.
- Don't create files with Shell redirects (`>`); use the file tools.
- Never read a large file whole. Find the place with `rg -n`, then read a line range (over-limit files: `scripts/size-baseline.json`).
- Graph queries: keep the default budget; when output says TRUNCATED, narrow (a symbol, `--context call`, the
  file-qualified name it lists) instead of raising it. Don't read `graphify-out/graph.json`; read `GRAPH_REPORT.md`
  only for a broad architecture review.
- Match repo roots with a trailing slash: `c:/git/flow-reader` is a prefix of `c:/git/flow-reader-plugins`.

## Definition of done

1. `scripts\verify.ps1` passed for the current files (`-Full` at handoff when app code changed).
2. Device checks ran, or are listed for the owner in the commit's `Device-check:` line.
3. Docs, `docs/agent/registry.md`, `docs/agent/seams.md` and rule files match the change.
4. The handoff quotes the "Rules changed" block. "Installed" alone is not done. Push only with the owner's go-ahead.

## Where knowledge lives

- Code graphs (setup, freshness, commands, blind spots): `docs/agent/graph.md`.
- Vocabulary, owner files, tests, pitfalls: `docs/agent/registry.md`. Couplings: `docs/agent/seams.md`.
- Feature rules by file glob: `.cursor/rules/*.mdc`; always-on rules: `working-agreement.mdc`, `graphify.mdc`
  (Graphify's own; rewritten on reinstall).
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
