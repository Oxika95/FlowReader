# Start a task

Type a dash to browse areas (start-plugins-royalroad), or send empty to pick from a list.

Arguments: `AREA[-SUB] TASK`. Parse them with `../flow-reader-chats/_config/areas.md`
`## Parsing /start`: aliases (capital letter in Reader Tts Library Plugins Import Ui Settings Brand
reLease Meta), unambiguous prefixes, a dash for the sub-area (`plugins-RoyalRoad`, `l-queue`). Ask
for every missing or ambiguous piece in one `AskQuestion` call, using the defaults that section
defines; never stop at a bare usage message. The generated `start-<area>[-<sub>].md` commands pass
AREA and SUB already filled in.

Then open `../flow-reader-chats/stages/task/01-plan/CONTEXT.md` and run that stage with AREA, SUB,
and TASK, loading only what its Inputs table lists. Continue through 02-implement and 03-verify
after the plan is approved. Keep the chat to this one task; when done or scope shifts, run
`/handoff`.

If `../flow-reader-chats` is absent: use `docs/agent/CONTEXT.md`, plan first, then implement and
verify with the commands in `AGENTS.md`.
