# Dependency licenses (app)

Checked 2026-11-06 against known SPDX ids for libraries in `app/build.gradle.kts`.
All of these combine with AGPL-3.0-or-later for an AGPL-licensed app.

| Library | Version | License |
| --- | --- | --- |
| AndroidX Compose BOM / UI / Material3 / icons | 2025.01.01 | Apache-2.0 |
| AndroidX Core / Activity / Lifecycle / Navigation | various | Apache-2.0 |
| AndroidX DataStore / Media / Work / Room / Security Crypto / RecyclerView | various | Apache-2.0 |
| Kotlin coroutines Android | 1.9.0 | Apache-2.0 |
| jsoup | 1.18.3 | MIT |
| OkHttp | 4.12.0 | Apache-2.0 |
| quickjs-kt | 1.0.0-alpha13 | Apache-2.0 |
| JUnit | 4.13.2 | EPL-1.0 |
| org.json (tests) | 20240303 | Public domain / JSON license |
| Conscrypt (tests) | 2.5.2 | Apache-2.0 |

Bundled third-party code with separate notices: see root `NOTICE.md` (sentence-splitter LGPL-3.0-or-later; Readest AGPL-3.0-or-later).

`EdgeAudioFrames.kt` implements the Edge binary-frame layout (2-byte header length + payload). It is
protocol behavior, not a translation of Readest source, so it has no Readest credit header.
`TtsController.kt` / `TtsAudioEngine.kt` "Readest-style" comments describe behavior only.
