# Start a task

Usage: `/start AREA TASK`. Area ids: `../flow-reader-chats/_config/areas.md`.

Open `../flow-reader-chats/stages/task/01-plan/CONTEXT.md` and run that stage with AREA and TASK,
loading only what its Inputs table lists. Continue through 02-implement and 03-verify after the
plan is approved. Keep the chat to this one task; when done or scope shifts, run `/handoff`.

If `../flow-reader-chats` is absent: use `docs/agent/CONTEXT.md`, plan first, then implement and
verify with the commands in `AGENTS.md`.
