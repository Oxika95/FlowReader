# Review changes

Review from git, never by reading other chats.

1. Scope: unpushed commits plus working tree in `flow-reader` and `../flow-reader-plugins`
   (`git log origin/master..HEAD --oneline` / `origin/main..HEAD`, `git status`, `git diff`), or the
   range the user names.
2. Check against: `AGENTS.md` hard rules, `.cursor/rules/*.mdc` for touched paths, the cross-repo
   checklist in `docs/agent/map.md` (plugin-visible changes), and docs updated for behavior changes.
3. Look for real bugs first (state, lifecycle, coroutine cancellation, migrations, API compatibility),
   then rule violations; skip style nits the code already tolerates.
4. Run `.\gradlew.bat :app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `npm test` /
   `npm run check` in the plugins repo if it changed.
5. Report findings by severity with file:line. Fix only what the user approves. Record durable
   lessons as Gotchas in `../flow-reader-chats/areas/<area>.md`.
