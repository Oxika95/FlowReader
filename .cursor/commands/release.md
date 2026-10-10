# Release

Run the release pipeline in `../flow-reader-chats/stages/release/`: 01-review (stop on blockers),
then 02-ship (app first, plugins after), then 03-record. Open each stage's `CONTEXT.md` in turn and
load only what its Inputs table lists. Running `/release` is the user's go-ahead to push.
