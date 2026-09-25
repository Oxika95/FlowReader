# Settings

Shared overlay from library or reader. Primary tabs (alphabetical): **About | Audio | Filters | Import | Layout**.

![About](images/settings-about.png)

## About

| Control | Pref / notes |
|---------|----------------|
| Tagline | “Personal Android eReader with vertical flow reading and TTS.” |
| Version | Package `versionName` |
| Latest release | Link via GitHub API |
| GitHub | Oxika95/FlowReader |
| Debug mode | `debug_enabled` — synth dump FAB |

## Audio

Sub-tabs: **Voice | Playback**.

![Voice](images/settings-audio.png)

![Playback](images/settings-audio-playback.png)

### Voice

| Control | Key |
|---------|-----|
| TTS Engine | `tts_engine` (Edge TTS, System Default, installed engines) |
| Voice | `tts_voice` (default `en-US-AndrewNeural`) |
| Speed 0.5–2.5× | `tts_speed` |
| Pitch 0.5–2 | `tts_pitch` |
| Advanced ▾ Pre-cache clips 1–10 | `tts_prefetch` |
| Clip size (chars) | `tts_clip_target_chars` |
| Size leeway ± | `tts_clip_flex_chars` |

### Playback

| Control | Key |
|---------|-----|
| Auto-scroll with playback | `tts_auto_scroll` |
| Double-tap starts playback | `tts_double_tap_play` |
| Tonal underlay | `tts_tonal_underlay` |
| Sentence offset (−500…+500 ms) | `tts_sentence_gap_ms` |
| Word highlight sync | `tts_highlight_sync_ms` |

## Filters

![Filters](images/settings-filters.png)

Library Settings: **Global | Groups**. Reader Settings also **Local** (per book).

Rules apply to **visible text and TTS**, not raw HTML. Editor fields: Title, Type (case / regex), Whole words, TTS only, Find, Replace, Preview, Delete / Save.

Storage: `global_filters` / `group_filters` JSON; Local → Room `book_filters`.

## Import

Sub-tabs: **Router | Parser**.

![Router](images/settings-import.png)

![Parser](images/settings-import-parser.png)

### Router

| Control | Key / notes |
|---------|-------------|
| Manual Override | `share_ask_mode` — Ask vs Auto; Ask needs display-over permission |
| Rules list | Enable, delete, long-press Copy/Move, drag reorder → `share_router_rules` |
| Rule editor | Content (Book files / Raw text / URL), URL match, Parse page, Destination (Files / Queue / shelves / Plugin) |

Default idea: EPUB → Files; raw text / most URLs → Queue; plugin domains → plugin.

### Parser

Parse rules (`share_parse_rules`): URL match, Default/Custom parser, Content/Title/Remove CSS, Test URL.

## Layout

Sub-tabs: **Theme | UI | Font**.

![Theme](images/settings-layout.png)

![UI](images/settings-layout-ui.png)

![Font](images/settings-layout-font.png)

| Area | Controls | Keys |
|------|----------|------|
| Theme | Light / Dark / OLED; Accent hue | `theme`, `accent_hue` |
| UI | UI scale; Orientation; Chapter headings; Keep screen awake | `ui_scale`, `orientation`, `show_chapter_headings`, `keep_screen_awake` |
| Font | Sans/Serif/Mono; size; spacing; Justify | `font_family`, `font_scale`, `line_spacing`, `justify_text` |

## Source

- [`SettingsOverlay.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/SettingsOverlay.kt)
- [`AboutSettingsTab.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/AboutSettingsTab.kt)
- [`AudioSettings.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/AudioSettings.kt)
- [`FiltersSettings.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/FiltersSettings.kt)
- [`SharingSettingsTab.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/SharingSettingsTab.kt)
- [`AppearanceSettings.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/AppearanceSettings.kt)
- [`SettingsStore.kt`](../app/src/main/java/com/personal/flowreader/data/SettingsStore.kt)

[Back to hub](README.md)
