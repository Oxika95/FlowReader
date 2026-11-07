# Graphify trial — DROPPED

**Score: 1/13. Decision: DROP.**

Pinned `graphifyy==0.9.103` via `uv`, code-only extract of `C:\Git\flow-reader` only
(4925 nodes / 14721 edges). Honest appendix-T questions (answers not seeded into the query
text); a question passes only when every listed direct caller/reader appears in result lines.

| # | Question | Result |
| --- | --- | --- |
| 1 | Who writes reading positions? | FAIL — `affected ProgressWriter.submit` empty; FlowApp property calls leave no call edges |
| 2 | After plugin-book position write | FAIL — incomplete chain (`syncProgress`, `maintainChapterCache` missing from honest query) |
| 3 | Who holds `TwoWaySync.lock`? | PASS — holders appear in TwoWaySync neighborhood (wrong `.lock()` seed is SyncState) |
| 4 | What deletes `queue_items`? | FAIL — `affected` on `removeQue` / `removeMembership` finds no callers |
| 5 | `syncProgress` / `readPositions` plugins | FAIL — Royal Road JS not in app graph |
| 6 | What implements `flow.fetch`? | FAIL — `__host_fetch` string binding not a node; `host.mjs` is plugins-repo only |
| 7 | Who collects `bookFinished`? | FAIL — property / SharedFlow not extracted |
| 8 | Where are bitmaps decoded? | FAIL — natural query seeded on unrelated `DecodedNext` |
| 9 | What depends on `edgetts`? | FAIL — string literal not a node; EdgeTts plugin outside extract root |
| 10 | Who reads nav `bookId`/`queId`/`chapter`? | FAIL — `queId` not surfaced; query distracted by `args`/`readStdin` |
| 11 | `explain AppearanceSettingsState` | FAIL — one node (class+factory collapsed), not two |
| 12 | Who calls ensureSystemForPackage (symbol since removed into SystemTtsEngine)? | FAIL — finds `setEngine` / `refreshCatalog` / `speakSystem`; misses registry lambda L145 and second `refreshCatalog` L1180 |
| 13 | `pendingPluginShare` readers/writers | FAIL — MutableStateFlow property not a node |

Keep threshold was ≥10/13 plus at least one dependency plain `rg` name search misses. Neither met.

**Interesting near-miss:** Q12 `affected` shows depth-2 callers (attach → refreshCatalog → the old ensureSystem helper) that a flat name search does not list — useful, but the question still fails completeness.

**Expected graph gaps (already covered in `docs/agent/seams.md`):** FlowApp property access (`progress`, `pendingPluginShare`, `tts.bookFinished`), companion `TwoWaySync.lock`, plugin-repo symbols.

Artifacts removed: graphify-out/, scripts/graph.ps1, `.graphifyignore`, `.graphifyrc`, and any graphify-out/ ignore entries. Install left on the machine (uv + `graphifyy==0.9.103`); unused.
