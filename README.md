# Flow Reader

Personal Android eReader. Kotlin + Compose. Vertical Flow scroll, TTS, pin-card on scroll-away.

Open an EPUB or TXT, read, listen.

## What works

- **Library** — Files tab (list or shelf). Import a copy or reference in place via the system file picker. No all-files access.
- **Queue** — share text/URLs to Flow Reader (routed to Queue), or paste from the clipboard. While listening, the reader marks the item done and moves to the next unfinished one on its own.
- **Reader** — reflow text, theme / accent / font / spacing, Edge or system TTS, text filters (Global / Groups / Local).

URLs and PDF are later. PDF especially needs a different page viewer, not this reflow path.

## Build

```bash
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Min SDK 26. Target / compile SDK 36.

## Docs

Feature and UI map: [docs/](docs/README.md)

## Notes

Readest source is kept as a protocol reference at `C:\Git\readest-personal-fork`. This app does not fork it.
