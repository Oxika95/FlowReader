# Start a task

Arguments: `<area> <task description>`. Areas: tts-audio, reader, library-queue, plugins,
import-share, ui-system, settings, release.

1. Read `docs/agent/map.md`, then `../flow-reader-chats/areas/<area>.md` (if present). Read the most
   recent `../flow-reader-chats/handoffs/*-<area>-*.md` only if the task continues earlier work.
2. For `plugins` (or any change touching `plugin/api`, `plugin/runtime`, `FlowIcons`, media card
   slots): also read `../flow-reader-plugins/AGENTS.md` and follow the cross-repo checklist in `map.md`.
3. Locate code via `docs/agent/index.md` (line numbers) and Read by offset; search only for what the
   map and index don't answer. Delegate broad exploration to an explore subagent.
4. Check the area file's "Rejected" and "Open threads" before proposing a design.
5. For non-trivial work, present a short plan and wait for approval. Then implement, run
   `.\gradlew.bat :app:compileDebugKotlin` and `:app:testDebugUnitTest` (plus `npm test` in the plugins
   repo if touched), and update `docs/` for behavior changes.
6. Keep the chat to this one task. When it's done or scope shifts, run `/handoff`.
