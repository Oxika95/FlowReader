# Hand off and close this chat

Be terse. No push unless the owner says so. Don't work around a refused command; report it.

1. **Verify and commit.** In each code repo this chat touched:
   - if app code changed, run `powershell -NoProfile -File scripts\verify.ps1 -Full` in `flow-reader` (it also
     checks the plugins repo when that changed, and runs the smoke check);
   - commit with
     `node $HOME/.cursor/hooks/flow-agent/commit-mine.mjs -m "<area>: <msg>" --device "ran: ... | left for owner: ... | none: <reason>" [--also <path>...]`,
     run from that repo's folder. Pass files changed through Shell with `--also`. If a file isn't covered by the
     verify stamp, rerun verify rather than skipping it; `--no-verify-reason` only with the owner's approval.
2. **Report:**
   - the commit SHAs and the verify stamp (mode, time, result);
   - the **Rules changed** block: every rule file (`.cursor/rules/`, `AGENTS.md`, `docs/agent/` entries) added,
     edited or deleted, with one line on what changed; write "none" if there were none;
   - the device checks done, and those left for the owner;
   - the registry, seams, rules and backlog updates made.
3. **Unfinished work.** Append a `## Handoff` section to this task's file in `../flow-reader-chats/tasks/` with what
   is done, what is next and open questions. If there is no task file, add a backlog entry to
   `../flow-reader-chats/backlog.md`. When the work is complete, mark the section `Status: done`.
4. **Sync transcripts.** In `../flow-reader-chats`, run `.\scripts\sync-transcripts.ps1` (it respects the exclude
   list). Then commit there with commit-mine, `--also raw/`, together with the task or backlog change.
5. **Release the lock:** `node $HOME/.cursor/hooks/flow-agent/lock.mjs release`.
