# Code graphs (Graphify)

[Graphify](https://github.com/Graphify-Labs/graphify) (Apache-2.0, runs locally, no account) builds a graph of each
repo from the syntax tree: files, classes, functions, and the calls, imports and type references between them.
The graphs are the first step of the lookup order in `../../AGENTS.md` (owner decision 2026-11-06). The registry
and `seams.md` cover what the graph can't see (see Blind spots), and `rg` confirms names and callers.

## Setup (once per machine)

```powershell
winget install astral-sh.uv
uv tool install --force 'graphifyy[mcp]==0.9.103'
uv tool update-shell                      # puts %USERPROFILE%\.local\bin on the user PATH
[Environment]::SetEnvironmentVariable('GRAPHIFY_BACKEND', 'none', 'User')
[Environment]::SetEnvironmentVariable('PYTHONUTF8', '1', 'User')
.\scripts\graph.ps1                       # first run: full code-only extract of both repos (about 10 s)
```

Restart Cursor afterwards so new shells see the PATH and variables.

- **Version pinned** at 0.9.103: Graphify releases every day or two and has changed its data format between releases.
  Re-check the behaviour on this page before moving the pin.
- **`GRAPHIFY_BACKEND=none`** keeps the AI passes off (community naming, doc extraction), so no code leaves the
  machine and nothing costs tokens. `scripts\graph.ps1` sets it for its own runs too. `PYTHONUTF8=1` avoids
  Windows console encoding crashes.
- **Cursor rule:** `graphify cursor install` (run once in this repo) wrote `.cursor/rules/graphify.mdc`, always on.
  Graphify owns that file and rewrites it on reinstall, so repo-specific wording goes in `AGENTS.md`, not there.
- **MCP server:** `%USERPROFILE%\.cursor\mcp.json` (per machine, not committed):

  ```json
  {
    "mcpServers": {
      "graphify": {
        "command": "C:\\Users\\<you>\\.local\\bin\\graphify-mcp.exe",
        "args": ["C:/Git/flow-reader/graphify-out/graph.json"],
        "env": { "GRAPHIFY_BACKEND": "none", "PYTHONUTF8": "1" }
      }
    }
  }
  ```

  Cursor lists it as `user-graphify`. It reloads `graph.json` when the file changes. Every tool takes
  `project_path`; pass `C:/Git/flow-reader-plugins` for the plugins graph.
- **Not used:** extraction without `--code-only`, `label` and other AI passes; `graphify install` and
  `graphify claude install` (Claude skill and hooks that Cursor would also load); `graphify hook install` (git hooks
  that clash with `core.hooksPath` = `scripts/git-hooks`; Windows bugs #4463, #4563); `watch`; work memory; the
  hosted app. MCP tools `list_prs`, `get_pr_impact`, `triage_prs` need GitHub access and are unused. Avoid
  `graph_stats` (reported to hang under MCP hosts, #4440).

## What the graphs hold

- **App:** every code file not ignored by `.gitignore` or `.graphifyignore` (`app/schemas/`, `docs/`, `brand/`,
  `scripts/*.json`): Kotlin, Gradle scripts, the agent scripts (.mjs and .ps1 files), CI YAML. 2026-11-06: 357 files,
  about 5,000 nodes and 15,000 edges.
- **Plugins:** every plugin `index.js` and `plugin.json`, `test/`, `scripts/`, CI YAML; `.graphifyignore` drops
  `index.json` and `package-lock.json`. 2026-11-06: 15 files, 276 nodes, 582 edges.
- **Output:** `<repo>/graphify-out/` (gitignored, not indexed): `graph.json`, `manifest.json` (file content hashes
  that `update` compares), `cache/`, and `GRAPH_REPORT.md` / `graph.html` after an update that rebuilds.
- **Node labels:** `.method()` for members, `name()` for top-level functions, the bare name for types and files. An
  ambiguous name exits 3 and lists file-qualified forms (`<path>::.removeQue()`) to retry with.

## Freshness

- `scripts\graph.ps1` runs `graphify update .` in each repo, or `graphify extract . --code-only` when `graph.json`
  is missing. `update` compares file contents with the manifest, so uncommitted and untracked edits count (checked
  2026-11-06). It stops a run after 120 s (Windows hang, #3646) and only ever warns: a stale graph is a lookup
  risk, not a verify failure.
- `/task` step 1 runs it first; `scripts\verify.ps1` runs it when the app or plugins repo changed. After editing
  code mid-task, run `graphify update .` in that repo before querying again.
- Measured 2026-11-06: full extract app 7 s, plugins 1 s; `update` 1 s with no change, about 5 s for a full rebuild.
- `Refusing to overwrite` (verify prints it as a warning): the rebuild lost nodes from files it did not re-extract,
  or a file failed to parse. Deleting a function in an edited file does not trigger it. Fix the parse error; if the
  loss is real, run `graphify update . --force` in that repo.

## Commands

Run from the app repo root. For the plugins graph add `--graph ../flow-reader-plugins/graphify-out/graph.json`
(CLI) or `project_path: "C:/Git/flow-reader-plugins"` (MCP).

| Need | CLI | MCP |
| --- | --- | --- |
| Neighbourhood of words or a symbol | `graphify query "<question>"`; `--context call` keeps call edges | `query_graph`; `context_filter: ["call"]` |
| One symbol's edges, in and out | `graphify explain "<Symbol>"` | `get_neighbors`, `get_node` |
| What reaches a symbol (reverse, depth 2) | `graphify affected "<Symbol>"` | none |
| How two symbols connect | `graphify path "<A>" "<B>"` | `shortest_path` |
| Most connected classes | none | `god_nodes` |

- Exit codes: 0 answer, 3 ambiguous name (candidates listed), 4 no match.
- Query output defaults to a 2,000-token budget and depth 2. When it says TRUNCATED, narrow the question (a
  symbol instead of a sentence, `--context call`, a file-qualified name) rather than raising the budget.
- Don't read `graph.json`, and read `GRAPH_REPORT.md` (about 7k tokens) only for a broad architecture review,
  never for a lookup. Skip `explain --all`.
- Edges marked INFERRED are name guesses with a confidence; `LibraryTopBar` "calls" `.add()` is one such guess.

## Blind spots

Measured 2026-11-06. Each row is a kind of link the graph has no edge for, with what covers it.

| Miss | Example | Use instead |
| --- | --- | --- |
| Kotlin member calls through an instance (a field, property or parameter). Graphify links member calls only on `this` and on a capitalized object or companion name (`PluginMembershipStore.remove`), plus top-level functions by name; it skips instance calls by design (#1698; bare names collide, #543) and no setting changes that | Graph found 1 of 3 callers of `PluginListWriter.dropUnlisted` (missed `TwoWaySync` and `PluginTabViewModel`, both via `lists.`). Every `flow.x.y()` call through `FlowApp` | `rg`, `seams.md` |
| Function references and property assignments | `pluginBooks.positionChanged = pluginSync::localPositionChanged` | `rg`, `seams.md` |
| Readers and writers of properties and flows | `tts.bookFinished`, `FlowApp.pendingPluginShare`, `savedStateHandle["queId"]` | `rg` |
| String ids and bindings | `__host_fetch`, plugin id `edgetts`, Room table `queue_items`, nav routes | `rg` |
| App to plugins: two graphs, no edge across QuickJS | `flow.fetch` → `PluginHttp.fetch` ↔ `test/host.mjs` | `cross-repo.md`, `contract-check.mjs` |
| Same-named plugin functions | Every plugin has `pathOf`, `search`, `loadChapter`; "who calls pathOf" answered with Patreon's | Name the plugin in the query, or use the file-qualified form |
| A class and a factory function of the same name | `AppearanceSettingsState` (data class and factory) is one node | `rg` |
| Files the Kotlin parser only partly reads | `PluginDb.kt` (from line 159), `FlowSettingsRows.kt` (from line 258), `PlaybackSettingsTab.kt` (2 symbols), `LibrarySortBar.kt` and `ReaderLayout.kt` (no symbols) | Read the file |
| Sentence questions seed on unrelated names | "where are bitmaps decoded" starts from `DecodedNext` (TTS audio) | Query a symbol |

What the graph does well: call chains inside one class; object and companion calls across files
(`PluginTabViewModel.loadStory` → `PluginMembershipStore.listsContaining`); top-level and Compose function calls,
including those inside trailing lambdas (`LibraryScreen` → `LibraryTopBar`); importers and construction sites of a
type (`explain AppearanceSettingsState`); calls inside a plugin's `index.js`; depth-2 reverse traversal (`affected`)
where edges exist. `god_nodes` gives the core classes (`TtsController`, `PluginTabViewModel`, `SettingsStore`,
`FlowApp`), not Compose plumbing, so `--exclude-hubs` isn't needed.

## Baseline (2026-11-06)

The 13 trial questions, asked again on both graphs (CLI and MCP) and checked against `rg` on the current code. A
question passes when every direct caller or reader appears. This is a record, not a gate.

| # | Question | Ground truth (`rg`) | Graph |
| --- | --- | --- | --- |
| 1 | Who writes reading positions? | `ProgressWriter.submit` from `FlowApp` and `ReaderViewModel` | Miss: `affected` finds only `submitAndDrain` in the same class |
| 2 | What runs after a plugin-book position write? | `PluginBookStore.scheduleMaintain` → `maintainChapterCache`; `TwoWaySync.localPositionChanged` → `flush` → Royal Road `syncProgress` | Miss: function reference and instance calls |
| 3 | Who holds `TwoWaySync.lock`? | `TwoWaySync.run`, `ingestCrawledLists`, `pullPositions`; `UpdateChecker.run`; `PluginDataEraser.erase` | Partial: the three `TwoWaySync` methods, `UpdateChecker.kt` as a file only, no `PluginDataEraser` |
| 4 | What deletes `queue_items` rows? | `BookCatalog.removeQue` (2 callers), `PluginCatalog.removeMembership` (3 callers) | Miss: `affected` finds no callers |
| 5 | Which plugins implement `syncProgress` and `readPositions`? | Royal Road only | Pass (plugins graph) |
| 6 | What implements `flow.fetch`? | `__host_fetch` in `JsPluginRuntime` → `PluginHttp.fetch`; mirrored in `test/host.mjs` | Miss: string binding |
| 7 | Who collects `TtsController.bookFinished`? | `FlowApp`, `QueuePlayback` | Miss: no node matches |
| 8 | Where are bitmaps decoded? | `data/ScaledBitmap.kt` | Miss: seeds `DecodedNext` |
| 9 | What depends on plugin id `edgetts`? | `TtsProviderRegistry`, `SettingsStore`, `TtsEngineIdsTest`, `EdgeTts/plugin.json` | Miss: string literal |
| 10 | Who reads the nav args `bookId`, `queId`, `chapter`? | `ReaderViewModel` (`savedStateHandle`) | Miss |
| 11 | What does `explain AppearanceSettingsState` return? | Two declarations: class and factory | Partial: one node; importers and both construction sites complete |
| 12 | Who calls `SystemTtsEngine.ensure` (successor of `ensureSystemForPackage`)? | `TtsController`, two calls | Miss: `systemEngine.` instance calls |
| 13 | What reads or writes `FlowApp.pendingPluginShare`? | `LibraryViewModel` writes; `PluginTab` reads and clears | Miss: property |

Score: 1 pass, 2 partial, 10 misses. Every miss is a row in Blind spots.

## History

- 2026-11 trial (context v2 plan): code-only graph of the app repo, 13 questions, 1/13 against a keep bar of
  10/13; the integration was removed.
- 2026-11-06: the owner reversed the removal and made Graphify the main context model: graph first, the registry
  and couplings list for what it can't see, `rg` to confirm. Its Cursor rule and MCP server are used because they
  are free and local.
