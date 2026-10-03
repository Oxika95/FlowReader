# Release

Ship app and/or plugin changes. The step-by-step procedure and history live in
`../flow-reader-chats/areas/release.md`; read it first.

1. Run `/review` on everything since the last release tag/commit in both code repos. Stop on blockers.
2. App (`flow-reader`): bump `versionCode` and `versionName` in `app/build.gradle.kts`, update
   `README.md` / `docs/` for user-visible changes, build, commit `Release <version>: <summary>.`,
   push, and publish the GitHub release with the APK per `areas/release.md`.
3. Plugins (`../flow-reader-plugins`), only after any required app release is out: bump each changed
   plugin's `version`, `npm test`, `npm run build`, `npm run check`, commit, push `main` (Pages deploys
   to users immediately).
4. Update `areas/release.md` (current version, anything that changed in the procedure) and append to
   `decisions.md`; commit and push `flow-reader-chats`.
5. Reply with versions shipped, release URL, and commit shas.
