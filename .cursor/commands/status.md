# Status

Read-only. Report:

1. **Locks.** `node $HOME/.cursor/hooks/flow-agent/lock.mjs status` (app and plugins repos: free, or holder, last
   active, expiry).
2. **Last verify stamp** per repo: `<repo>/.git/flow-agent/stamp-pass.json` (mode, time) and the sibling
   stamp.json (latest run, result). Say if a repo has changed since the last passing stamp (`git status --short`).
3. **Open tasks.** Files in `../flow-reader-chats/tasks/` (not `README.md`) that have no `## Handoff` section marked
   `Status: done`. One line each: file name, and its handoff status if any. Read only the headings and the handoff
   section, not whole files.
4. **Owner device checks.** If any `../flow-reader-chats/tasks/*-owner-device-checks.md` exists and is not marked
   fully done in its own handoff/status line, list the file name and how many unchecked sections remain (read
   headings only).
5. **Backlog.** The top items in `../flow-reader-chats/backlog.md`: the `###` ids and titles with severity.
6. **Unpushed commits** per repo: `git log --oneline origin/master..HEAD` (app, chats if it has a remote) and
   `origin/main..HEAD` (plugins).
