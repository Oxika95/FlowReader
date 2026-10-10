# Plugin API (apiVersion 4)

Flow Reader source plugins are sandboxed JavaScript, loaded from user-added repositories
(LNReader-style). A plugin only fetches and parses a site; the app owns every screen,
the chapter cache, the reader, and sharing. See [ui-contract.md](ui-contract.md) for what the
app renders, and [examples/media-card.md](examples/media-card.md) for a media card walkthrough.

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
| 4 | `auth.web` (sign in on the site in an in-app browser), `lists[].notifyDefault`, browse lists (`lists[].kind`, `browse` capability); ToC refresh keeps cached chapters and positions by URL | Current (`PLUGIN_HOST_API_VERSION = 4`) |

- While Flow Reader is in alpha the host accepts only the current contract:
  `PLUGIN_MIN_API_VERSION = PLUGIN_HOST_API_VERSION = 4`. Older plugins are refused ("uses plugin
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
