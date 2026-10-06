# Hand off and close this chat

Persist what the next chat needs, commit, and stop. Be terse.

1. If files were added, moved, or split: `.\scripts\gen-agent-index.ps1`. If areas or entry points
   changed, update `docs/agent/map.md`.
2. In `../flow-reader-chats`:
   - Update `areas/<area>.md` (Current state, Decisions, Rejected, Open threads, Gotchas, User
     preferences). Keep it under ~120 lines: replace stale bullets, don't append history.
   - Append decisions to `decisions.md`: `| YYYY-MM-DD | <area> | <decision> | <why> | <chat id> |`.
   - Write `handoffs/YYYY-MM-DD-<area>-<topic>.md` using the template in `handoffs/README.md`, only if
     work is unfinished or a follow-up is planned.
   - Run `.\scripts\sync-transcripts.ps1`. Add this chat's title/areas to `raw/catalog.json` if it is
     "untitled", then re-run so `raw/index.md` picks it up.
3. Run `git status` in all three repos (`flow-reader`, `../flow-reader-plugins`,
   `../flow-reader-chats`). Commit each repo that changed with an area-prefixed message
   (`tts: ...`, `plugins: ...`, `chats: ...`). Do not push any repo unless the user says so; app and
   plugin pushes go through `/release`.
4. Reply with: commits made (repo + short sha), open threads, and the suggested `/start` line for
   the next chat.
