# Plugin API (apiVersion 5)

Flow Reader plugins are sandboxed JavaScript, loaded from user-added repositories
(LNReader-style). **Source** plugins fetch and parse a site; **TTS** plugins synthesize
speech. The app owns every screen, caches, the reader, sharing, and audio playback.
See [ui-contract.md](ui-contract.md) for what the app renders, [api-tts.md](api-tts.md) for
TTS providers, and [examples/media-card.md](examples/media-card.md) for a media card walkthrough.

JSON Schemas: [`schema/plugin.schema.json`](schema/plugin.schema.json),
[`schema/work-detail.schema.json`](schema/work-detail.schema.json),
[`schema/card-action-result.schema.json`](schema/card-action-result.schema.json),
[`schema/browse-page.schema.json`](schema/browse-page.schema.json).

## Versions

| `apiVersion` | Adds | Host support |
| --- | --- | --- |
| 1 | Manifest, `search`/`list`/`loadWork`/`loadChapter`, auth, membership, progress sync, URL resolve | Refused |
| 2 | `WorkDetail.card` (stats, badges, links, actions), `Work.badges`/`Work.stats`, `cardAction()` | Refused |
| 3 | `updates` capability + `checkUpdates()` for the background new-chapter check; `notify` reserved | Refused |
| 4 | `auth.web`, `lists[].notifyDefault`, browse lists; ToC refresh by URL | Refused |
| 5 | `kind` (`source` \| `tts`); TTS plugins (`listVoices` / `synthesize`); `flow.ws.collect`; TTS capabilities | Current (`PLUGIN_HOST_API_VERSION = 5`) |

- While Flow Reader is in alpha the host accepts only the current contract:
  `PLUGIN_MIN_API_VERSION = PLUGIN_HOST_API_VERSION = 5`. Older plugins are refused ("uses plugin
  API N, which is no longer supported"), newer ones too ("needs a newer Flow Reader").
  Repositories list each plugin's `apiVersion`; incompatible entries are not offered.
- A contract change bumps both constants together; republish every plugin with the new
  `apiVersion` **and** a bumped `version`.


## Pages

| Page | Covers |
| --- | --- |
| [api-manifest.md](api-manifest.md) | Package files, `plugin.json` fields |
| [api-functions.md](api-functions.md) | `index.js` functions, types, card limits |
| [api-features.md](api-features.md) | `cardAction`, `checkUpdates`, two-way sync, browse lists, web sign-in, ToC refresh |
| [api-host.md](api-host.md) | `flow` host API, `Node`, limits, repository index |
| [api-tts.md](api-tts.md) | TTS provider plugins, `flow.ws`, capability gating |
| [source-kinds.md](source-kinds.md) | Design: `local` / `synced` / `sourced` categories, planned `category` key, Audiobookshelf |
