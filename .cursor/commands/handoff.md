# Hand off and close this chat

Be terse. No push unless the owner says so. Don't work around a refused command; report it.

1. **Verify and commit.** In each code repo this chat touched:
   - if app code changed, run `powershell -NoProfile -File scripts\verify.ps1 -Full` in `flow-reader` (it also
     checks the plugins repo when that changed, and runs the smoke check);
   - commit with
     `node $HOME/.cursor/hooks/flow-agent/commit-mine.mjs -m "<area>: <msg>" --device "ran: ... | left for owner: ... | none: <reason>" [--also <path>...]`,
     run from that repo's folder. Pass files changed through Shell with `--also`. If a file isn't covered by the
     verify stamp, rerun verify rather than skipping it; `--no-verify-reason` only with the owner's approval.
2. **Device checks before close.**
   - Run every device check the plan listed that you can run (emulator/smoke, UI without signed-in site
     accounts). Say before using the emulator or a physical device.
   - Owner-only checks (signed-in site, personal account, hardware you were told not to touch): copy each
     into `../flow-reader-chats/tasks/<YYYY-MM-DD>-owner-device-checks.md` (create the day's file if
     missing; one checklist per day, append sections). Do not leave the task file's terminal state as
     "device checks pending".
   - The commit `Device-check:` trailer still names what ran and what is left for the owner (release gate).
3. **Report:**
   - the commit SHAs and the verify stamp (mode, time, result);
   - the **Rules changed** block: every rule file (`.cursor/rules/`, `AGENTS.md`, `docs/agent/` entries) added,
     edited or deleted, with one line on what changed; write "none" if there were none;
   - the device checks done, and those left for the owner (point at the owner-device-checks file);
   - the registry, seams, rules and backlog updates made.
4. **Unfinished work.** Append a `## Handoff` section to this task's file in `../flow-reader-chats/tasks/` with what
   is done, what is next and open questions. End with `Status: done` when this chat's work is closed
   (including after moving owner-only checks to the checklist). If there is no task file, add a backlog entry to
   `../flow-reader-chats/backlog.md`.
5. **Sync transcripts.** In `../flow-reader-chats`, run `.\scripts\sync-transcripts.ps1` (it respects the exclude
   list). Then commit there with commit-mine, `--also raw/`, together with the task or backlog change.
6. **Release the lock:** `node $HOME/.cursor/hooks/flow-agent/lock.mjs release`.
