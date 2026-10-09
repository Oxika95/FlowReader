# Settings

Shared overlay from library or reader. Primary tabs (alphabetical): **About | Audio | Filters | Import | Layout | Plugins**.

![About](images/settings-about.png)

## About

Sub-tabs: **Info | Notifications | Data**.

### Info

| Control | Pref / notes |
|---------|----------------|
| Tagline | “Personal Android eReader with vertical flow reading and TTS.” |
| Version | Package `versionName` |
| Latest release | Link via GitHub API |
| GitHub | Oxika95/FlowReader |
| Debug mode | `debug_enabled` — synth dump FAB |

### Notifications

Background checks (WorkManager) that post notifications. Intervals are a slider in whole hours,
Off or 1–24 h (`CheckInterval`).

| Control | Key / notes |
|---------|-------------|
| New chapters › Check every | `plugin_update_interval`, default 12 h; stories with the bell on |
| New chapters › Wi-Fi only | `plugin_update_wifi_only`, default off |
| New chapters › Check now | One run when the network allows |
| Plugin updates › Check every | `plugin_version_interval`, default 24 h; refreshes repositories |
| Plugin updates › Notify about updates | `plugin_version_notify`, default on; once per version (`plugin_version_notified`) |

### Data

Storage and download defaults.

| Control | Key / notes |
|---------|-------------|
| Plugin story downloads › Cache level | `plugin_cache_level`, default 1; chapters downloaded ahead of the reading position for newly added plugin stories |
| Plugin story downloads › Clean up old chapters | `plugin_cache_cleanup`, default off; deletes chapters more than the cache level behind |

Each story can change its own values from the hold-Download card.

## Audio

Sub-tabs: **Voice | Playback**.

![Voice](images/settings-audio.png)

![Playback](images/settings-audio-playback.png)

### Voice

| Control | Key |
|---------|-----|
| TTS Engine | `tts_engine` (Edge TTS, System Default, installed engines) |
| Language (Edge only; filters the Voice list; starts on the current voice's language, English by default) | not stored |
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
| Use mobile data when Wi-Fi is weak | `tts_mobile_data_fallback` — default On; Edge overlap / cellular-first lanes ([tts.md](tts.md#edge-requests-and-networks)) |
| Tonal underlay | `tts_tonal_underlay` — float level (dB), snapped to `TtsPrefs.TONAL_UNDERLAY_STEPS` |
| Underlay Bluetooth device | `tts_underlay_bt_address` / `tts_underlay_bt_name` — standby until A2DP/Headset connected |
| Sentence offset (−500…+500 ms) | `tts_sentence_gap_ms` |
| Word highlight sync | `tts_highlight_sync_ms` |

## Filters

![Filters](images/settings-filters.png)

Library Settings: **Global | Groups**. Reader Settings also **Local** (per book).

Rules apply to **visible text and TTS**, not raw HTML. Editor fields: Title, Type (case / regex), Whole words, TTS only, Find, Replace, Preview, Cancel / Save.

- Rules run top to bottom (Global, then Groups, then Local). **Whole words** adds a word boundary
  only on pattern edges that are letters/digits, so `Mr.` or `* * *` still match.
- **TTS only** rules change spoken text, never the page, and apply per sentence (a pattern can't
  span two sentences). Changes from Library or Reader settings reach playback immediately; Edge
  clip names include a hash of the TTS-only rules, so audio made with older rules is never replayed.
- **Hold** a rule to enter edit mode: drag handles reorder (saved on drop), checkboxes select,
  **All/None** toggles the selection, **Delete** asks for confirmation, **Done** or Back exits.

Storage: `global_filters` / `group_filters` JSON; Local → the `localFilters` column of the book's own row (Library, Queue entry, or plugin DB).

## Import

Sub-tabs: **Router | Parser**.

![Router](images/settings-import.png)

![Parser](images/settings-import-parser.png)

### Router

| Control | Key / notes |
|---------|-------------|
| Manual Override | `share_ask_mode` — Ask vs Auto; Ask needs display-over permission |
| Auto Play on Share | `tts_auto_play_on_share` — default Off; a share saved to Library or Queue opens in the reader and starts TTS when nothing is playing ([tts.md](tts.md#playback-behavior)) |
| Interrupt Playback | `tts_share_interrupts` — default Off; shown only with Auto Play on Share; the share also replaces current playback |
| Rules list | Enable switch; hold for edit mode (drag reorder, multi-select, Delete) like Filters → `share_router_rules` |
| Rule editor | Content (Book files / Raw text / URL), URL match, Parse page, Destination (Files / Queue / shelves / Plugin) |

Default idea: EPUB → Files; raw text / most URLs → Queue; plugin domains → plugin.

### Parser

Parse rules (`share_parse_rules`): Test URL, URL match, Default/Custom parser, **Desktop site**
(desktop user agent for Test, Pick and imports), then Custom fields in order **Title, Cover image,
Body, Previous Button, Next Button, Remove** (comma-separated), Crawl limit (shown when Next is set,
default 100). Footer: **Test** (preview + per-field match summary) and **Pick**
(on-page picker for the Test URL, see [import-share.md](import-share.md#on-page-picker)).
While both are filled, the URL match outline is green when it covers the Test URL and red (with a
warning) when it doesn't (shares from that URL would use another rule).
URL match: `www.` is ignored; with **Allow Wildcards** on, `*` matches any characters in host or
path (`*royalroad.com*`, `example.com/fiction/*/chapter/*`; a trailing `/*` also matches the parent).
Hold a rule for edit mode (drag reorder, multi-select, Delete) like Filters. The **Default** rule
(any URL) is always last and protected: no delete, switch, or drag; tap edits its parser mode / CSS.

## Layout

Sub-tabs: **Theme | UI | Font | Reading**.

![Theme](images/settings-layout.png)

![UI](images/settings-layout-ui.png)

![Font](images/settings-layout-font.png)

| Area | Controls | Keys |
|------|----------|------|
| Theme | Light / Dark / OLED; Accent hue; Saturation (0–200% of the theme's accent saturation, default is the theme saturation); Lightness (black at the left, the theme's accent in the middle, white at the right) | `theme`, `accent_hue`, `accent_saturation`, `accent_lightness` |
| UI | UI scale; Orientation | `ui_scale`, `orientation` |
| Font | Sans/Serif/Mono; size; spacing; Justify | `font_family`, `font_scale`, `line_spacing`, `justify_text` |
| Reading | Chapter headings in body; Keep screen awake; Home position (15–85%, default 50%); Show home marker | `show_chapter_headings`, `keep_screen_awake`, `home_position`, `show_home_marker` |

## Plugins

Sub-tabs: **Installed**, then one per installed plugin (its name).

- **Installed:** one compact row per plugin (`FlowListRow`: name and version; "Update available"
  line when the repositories have a newer version; info icon for description and allowed hosts;
  icon actions Update, Settings (opens the plugin's sub-tab), Uninstall). The `+` beside the
  Installed heading opens **Add plugin** (plugins from repositories that aren't installed; Install).
  **Advanced** (collapsed) holds Repositories: Refresh beside the heading, the trust note behind its
  info icon, one row per repository (name, official / plugin count, URL or error; Remove), and the
  Repository URL field with an Add icon. Download defaults for new plugin stories are in About › Data.
- **Per plugin:** name and version (description and hosts behind the info icon); **Account** for plugins with `auth` (sign-in status,
  Sign in / Sign out, `PluginAccountSettings`; sign-in changes reach the library tab through
  `PluginManager.sessionChanges`); then the manifest's `settings[]` form (`PluginSettingsForm`;
  saved per plugin by `PluginManager.setSetting`); then **Data** (`PluginDataSettings`: Delete
  {plugin} data, confirmed). Uninstall's confirm card offers Uninstall (keeps data) or Delete data too.

See [plugins.md](plugins.md).

## Source

- [`SettingsOverlay.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/SettingsOverlay.kt)
- [`AboutSettingsTab.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/AboutSettingsTab.kt)
- [`AudioSettings.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/AudioSettings.kt)
- [`FiltersSettings.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/FiltersSettings.kt), [`FilterRuleList.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/FilterRuleList.kt) (edit mode)
- [`SharingSettingsTab.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/SharingSettingsTab.kt)
- [`AppearanceSettings.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/AppearanceSettings.kt)
- [`NotificationSettings.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/NotificationSettings.kt)
- [`DataSettings.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/DataSettings.kt)
- [`PluginsSettingsTab.kt`](../app/src/main/java/com/personal/flowreader/ui/settings/PluginsSettingsTab.kt)
- [`SettingsStore.kt`](../app/src/main/java/com/personal/flowreader/data/SettingsStore.kt)

[Back to hub](README.md)
