# scripts/agent: checks and Cursor hooks

This folder holds the check scripts (`rules-check`, `dup-check`, `notes-check`, `seams-check`, `contract-check`,
`release-check`, `verify-state`) and the Cursor hooks for Flow Reader. This file covers the hooks.

## What the hooks do

Cursor runs them at user level, for all three repos (`flow-reader`, `flow-reader-plugins`, `flow-reader-chats`).
For paths outside those repos every hook answers with the neutral response and does nothing.

| Hook script | Event | Does |
| --- | --- | --- |
| `hook-preToolUse.mjs` | `preToolUse` | Edit lock for file tools and for `CallDynamicTool` calls that wrap Write, StrReplace, Delete or EditNotebook. Shell guard: git, hook CLIs, writes by non-holders, adb on a phone. Appends `--conversation <id>` to `commit-mine.mjs`, `lock.mjs` and `release-check.mjs`. |
| `hook-beforeShellExecution.mjs` | `beforeShellExecution` | Same Shell guard, without `--conversation` injection. Only for the fallback (see below). |
| `hook-beforeSubmitPrompt.mjs` | `beforeSubmitPrompt` | Always continues. Handles `/takeover`, records the turn-start content map, resets the verify-gate counter. |
| `hook-stop.mjs` | `stop` | Sends a `[verify-gate]` follow-up when files changed in the turn and don't match the last passing verify. |
| `hook-preCompact.mjs` | `preCompact` | Tells the user the chat is being compacted. |
| `hook-sessionEnd.mjs` | `sessionEnd` | Releases the locks on `user_close` and `window_close`. Deletes state older than 14 days. |
| `hook-subagentStop.mjs` | `subagentStop` | Releases the editing-subagent claim. |
| `hook-postToolUse-grep.mjs` | `postToolUse` (Grep) | If Grep came back empty, re-runs the search with `rg` and reports matches. Does nothing when the payload has no result. |
| `hook-capture.mjs` | any | Capture phase only (below). |

Supporting modules: `hook-lib.mjs` (stdin, the one JSON write, kill switch, config, repo detection; Node built-ins
only), `hook-state.mjs` (lock, edit log, subagent claim, turn-start maps, gate), `shell-parse.mjs` (command
segments, `cd` tracking, redirects), `guards.mjs` (git, CLI and adb rules), `shell-guard.mjs` (runs the rules over
a command), `hook-tools.mjs` and `hook-turn.mjs` (the handlers). The entry scripts load the handlers with a dynamic
import inside a try/catch, so a broken module gives the neutral response instead of a crash.

CLIs for agents and the owner: `commit-mine.mjs` and `lock.mjs`. State lives in `<repo>/.git/flow-agent/`.

## Install

From a terminal, in `C:\Git\flow-reader`:

```powershell
node scripts/agent/install-hooks.mjs --dry-run   # runs the selftest, prints the hooks.json it would write
node scripts/agent/install-hooks.mjs             # selftest, copy to ~/.cursor/hooks/flow-agent/, merge hooks.json
```

The installer runs `selftest.mjs` first and installs only if it passes. It then copies the scripts to
`%USERPROFILE%\.cursor\hooks\flow-agent\`, writes `config.json` there (repo roots, commit identity, lock timeout;
an existing `config.json` keeps its identity and timeouts) and merges its entries into
`%USERPROFILE%\.cursor\hooks.json`. Other tools' entries stay. The first run saves the original as
`hooks.json.flow-bak`. Running it again replaces only its own entries. Cursor reloads `hooks.json` when it is saved.

Options: `--shell-hook beforeShellExecution` (see "Verify before relying"), `--loop-limit N` (default 20).
`--uninstall` removes the entries and the installed folder. After any change in `scripts/agent/`, run the
installer again. Agents can't: the Shell guard denies `install-hooks.mjs`, and the edit guard denies writes under
`~/.cursor/hooks/` and to `hooks.json`.

Identity for `commit-mine`: `config.template.json` holds the commit name and email. Change them in the installed
`config.json` (or set `FLOW_AGENT_GIT_NAME` and `FLOW_AGENT_GIT_EMAIL`).

## Kill switch

Create `%USERPROFILE%\.cursor\hooks\flow-agent\OFF` and every hook answers with its neutral response straight away:

```powershell
New-Item "$env:USERPROFILE\.cursor\hooks\flow-agent\OFF"      # off
Remove-Item "$env:USERPROFILE\.cursor\hooks\flow-agent\OFF"   # on
```

If a hook blocks something it shouldn't, this is the first thing to try. Locks and edit logs stay as they were.

## Running the selftest

```powershell
node scripts/agent/selftest.mjs
powershell.exe -Command "node C:/Git/flow-reader/scripts/agent/selftest.mjs"            # as Cursor starts hooks; no -NoProfile
powershell.exe -NoProfile -Command "node C:/Git/flow-reader/scripts/agent/selftest.mjs" # for CI
```

Use the form without `-NoProfile` once in a while: a PowerShell profile that prints text would end up in hook
output, and only this form catches it. Both forms passed at the time of writing (235 checks, about 20 s). The
selftest builds three throwaway repos and a fake home folder under the temp directory and never touches the real
repos or `hooks.json`. Its payloads are written from the Cursor docs and past transcripts, not captured.

## Capture phase

The payload shapes are not confirmed until the owner records a real session.

```powershell
node scripts/agent/install-hooks.mjs --capture            # no selftest; the capture hook only prints the neutral response
# ... one chat that covers the list below ...
node scripts/agent/install-hooks.mjs --uninstall-capture  # keeps the recordings
```

Payloads go to `%USERPROFILE%\.cursor\hooks\flow-capture\<event>.jsonl`, one line per event with the raw input, any
`CURSOR_*` environment variables, and whether the text had a BOM or replacement characters. In that one chat:
edit, write and delete files directly and through `CallDynamicTool`; edit a notebook; run Shell commands (one
with `/takeover` typed as a prompt, and one with non-ASCII text); run a subagent that edits; run Grep with a
pattern that matches nothing; end turns; close the chat.

Capture and guards should not run at the same time, because both would fire on `preToolUse`. Uninstall the
capture entries before installing the hooks.

## Verify before relying

Nine details the Cursor docs leave open. "Assumed" means the code is written to that guess and nothing has
confirmed it. Fill in the last column after the capture.

| # | Question | Status | What the code assumes, and what happens if wrong | Answer from capture |
| --- | --- | --- | --- | --- |
| 1 | Tool names and path fields | Assumed | `tool_name` is one of Write, StrReplace, Delete, EditNotebook (also Edit, MultiEdit, ApplyPatch, NotebookEdit, SearchReplace). Path in `tool_input.path`, `file_path`, `target_file`, `target_notebook` or `notebook_path`, or patch headers. `CallDynamicTool`: `tool_input.toolName` and `tool_input.arguments.path` (object or JSON string). If the `matcher` in `hooks.json` doesn't match the real category names, the hook never runs for that tool: edits go unguarded. Adjust the matcher after the capture. | |
| 2 | Subagent identity | Assumed | A subagent's `transcript_path` ends in `agent-transcripts/<parent>/subagents/<id>.jsonl`; the lock identity is then `<parent>`. If a subagent instead sends its own `conversation_id` with an ordinary transcript path, it is treated as a separate chat and denied by the parent's lock, which shows up immediately. | |
| 3 | `stop.loop_count` scope | Assumed unknown | The hook counts reminders itself (max 2 per user message, one per state hash), so `loop_limit` is only a backstop: 20. If the count is per message, lower it to 2 with `--loop-limit 2`. | |
| 4 | When `sessionEnd` fires | Assumed | Locks are released only for `user_close` and `window_close` (`releaseOnReasons` in `config.json`). If it never fires on close, locks lapse after 30 minutes; if it fired per turn, nothing is released early. | |
| 5 | `updated_input` changes a Shell command | Assumed | The response `updated_input` replaces `tool_input`, so `--conversation <id>` reaches `commit-mine.mjs`. If it doesn't: `commit-mine` and `lock.mjs release` refuse ("missing --conversation") and agents can't commit; the owner commits by hand. The fix then is `--shell-hook beforeShellExecution` for the guards only; there is no way to pass the id in that mode. | |
| 6 | Grep result in `postToolUse` | Assumed | Result under `tool_output` (also tries `tool_result`, `result`, `output`, `tool_response`). If absent, the hook returns `{}`; remove it from `hooks.json` and rely on the AGENTS.md rule. | |
| 7 | Glob-scoped rules on read | Not used by the hooks | Recorded here for the instructions work; the hooks don't depend on it. | |
| 8 | Non-ASCII through the PowerShell pipe | Verified in simulation | The selftest pipes UTF-8 JSON containing `é` through `powershell.exe -Command "node ..."` and the path arrives intact. That is the documented launch path, not Cursor itself. Hook output is escaped to `\uXXXX` anyway. The capture records `replacement_chars` to confirm with real payloads. | |
| 9 | `/takeover` as typed | Assumed | `beforeSubmitPrompt` accepts a prompt that starts with `/takeover` or contains `[flow-takeover]`. If the command expands, put `[flow-takeover]` in the command text. | |

## Lock rules in short

- App and plugins repos are locked per chat. The chats repo has no lock; it keeps only the edit log.
- A hook event from the holder (edit, Shell, prompt, stop) refreshes `last_active_at`. Free or own lock: take or refresh.
- Expired (30 minutes idle): taken only when every path in the previous holder's edit log is clean in `git status`;
  otherwise the edit is denied, naming the files. `/takeover` takes it anyway and logs the previous holder's dirty files.
- Held by another chat: denied with the plan's wording. `lock.mjs release` frees it; `--force` is for the owner.
- One editing subagent per parent chat and repo; a claim lapses after 10 idle minutes or on `subagentStop`.
- Lock updates go through a `lock.mutex` created with `wx` (stale after 5 s) and a temp-file rename with retries.

## Git guard

For every agent: `status log diff show blame grep rev-parse rev-list ls-files ls-tree cat-file describe
merge-base shortlog fetch` plus the listing forms of `branch tag remote config worktree stash reflog` are allowed.
`push` is allowed for the lock holder (anyone in the chats repo); forced, deleting and mirror pushes are denied.
Everything else is denied for everyone, holder included, with a pointer to `commit-mine.mjs`. The parser splits on
`; && || |` and newlines, follows `cd`, `Set-Location`, `pushd` and `-C`, skips `-c k=v` and the other global
options, reads `powershell -Command`, `bash -c` and `iex` strings, and treats quoted text as text. It is a guard
rail, not a security boundary: a script that runs git itself isn't seen.

## Differences from the plan, and extras

- A Shell write command from a chat when the lock is **free** takes the lock, like an edit does. The plan denies
  non-holders; a chat that hasn't edited yet is a non-holder only because nobody holds the lock.
- The gate state (`gate/<conversation_id>.json`) is kept only in the app repo's state folder and covers both repos.
- Extra read-only git commands allowed: `show-ref for-each-ref name-rev diff-tree diff-index check-ignore ls-remote`.
  Any `--output` option is denied. Forced pushes are denied (not in the plan).
- Extra write commands recognised: `mkdir md touch tee tee-object`. Writes under `.git/flow-agent/` are denied.
- The grep hook has a 10 s timeout (the rg run is capped at 8 s); the plan's other timeouts are 5 s, `stop` 10 s.
- Edits reported by `subagentStop.modified_files` are added to the parent chat's edit log.
- Test seams exist only when `FLOW_AGENT_USERHOME` is set: `FLOW_AGENT_ADB_MOCK`, `FLOW_AGENT_TEST_CRASH`.
  `FLOW_AGENT_CONFIG` points at another config file and `FLOW_AGENT_RG` at another `rg`.

## commit-mine and lock

```powershell
node $HOME/.cursor/hooks/flow-agent/commit-mine.mjs -m "<area>: <msg>" --device "ran: ... | left for owner: ... | none: <reason>" [--also <path>...] [--no-verify-reason "<why>"]
node $HOME/.cursor/hooks/flow-agent/lock.mjs status
node $HOME/.cursor/hooks/flow-agent/lock.mjs release
```

The hook adds `--conversation <id>` (and replaces one the agent typed). Without it the scripts refuse. In the app
and plugins repos the chat must hold the lock and the committed files must match the last passing verify stamp
(`.git/flow-agent/stamp-pass.json`); `--no-verify-reason` skips that and adds a `Verify-skipped:` trailer. The commit
is `git -c user.name=... -c user.email=... commit --only` on this chat's edit-log paths that have changes, plus
`--also` paths, with a `Device-check:` trailer. It never amends or forces. Other changed files are listed as
"not committed: not edited by this chat".
