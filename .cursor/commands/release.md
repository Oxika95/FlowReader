# Release

Pick the next version yourself (the next `0.YY` after the current `versionName`) and ask nothing else. Running
`/release` or saying "push" or "push to github" is the owner's go-ahead for the pushes below. App first; plugins
only after any app release they depend on is out. Don't work around a refused command; report it.
`holistic-review` was removed on 2026-10-06; don't recreate it.

## App (`flow-reader`, branch `master`)

1. Run `/review`. Stop on blockers and report them.
2. In `app/build.gradle.kts` bump `versionName` to `"<X.YY>-alpha"` and `versionCode` (0.85 -> 85, 0.99 -> 99,
   1.0 -> 100). The About tab reads `BuildConfig`.
3. Update `README.md` and `docs/` for user-visible changes. The root `README.md` is in the owner's words; don't
   rewrite it.
4. Run `powershell -NoProfile -File scripts\verify.ps1 -Full` (covers both repos and the smoke check), then
   `.\gradlew.bat :app:assembleDebug`.
5. Run `node scripts/agent/release-check.mjs`. It lists every `Device-check:` and `Verify-skipped:` trailer since the
   last tag that is still open. Show the list; the owner confirms each device check left for them before you go on.
6. Commit with commit-mine: `-m "Release <ver>: <summary>."`, with `--device` set from the release-check result.
7. Push `master`.
8. Copy `app/build/outputs/apk/debug/app-debug.apk` to `FlowReader-<ver>.apk`, as a separate command.
9. Run `gh release create v<ver> FlowReader-<ver>.apk -R Oxika95/FlowReader --target master --title "<X.YY> alpha" --notes-file <tmp>`.
   Don't pass `--prerelease`.
10. Delete the temp APK and the temp notes file.

### Release notes

- Short casual paragraphs about user-facing changes only, with migration or reinstall steps if any.
- End with `Debug APK attached.`
- Check each claim against the code before writing it, for example migrations and `PluginManager.installBundled`.

## Plugins (`flow-reader-plugins`, branch `main`)

Pushing `main` publishes to users immediately.

1. Bump each changed plugin's `version` per `../flow-reader-plugins/CONTEXT.md` `## Release rules`. The build fails
   if you forget.
2. Run `npm test`, `npm run build` and `npm run check`. Commit with commit-mine (include `index.json`) and push
   `main`.
3. Watch the Pages workflow, and confirm the live `index.json` file hash matches the local `index.json`.

## Record (`flow-reader-chats`, branch `main`)

- Append a row to `decisions/release.md` with the version, date and the confirmed device checks.
- Update the current version in `areas/release.md` (until area files are retired).
- Commit with commit-mine and push `main`. Commit only the release record, not other dirty files in that repo.
- Reply with the versions shipped, the release URL and the commit SHAs for all three repos.
