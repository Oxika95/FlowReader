# Plugin API: package and manifest

Part of the [Plugin API](api.md) (apiVersion 4).

## Package

A plugin is two files, installed to `filesDir/plugins/installed/{id}/`:

| File | Purpose |
| --- | --- |
| `plugin.json` | Manifest: identity, capabilities, lists, auth fields, settings, hosts |
| `index.js` | Code. CommonJS: assign your functions to `module.exports` |

### `plugin.json`

```json
{
  "id": "royalroad",
  "name": "Royal Road",
  "description": "Your followed and saved serials",
  "version": "1.3.0",
  "apiVersion": 4,
  "site": "https://www.royalroad.com",
  "iconUrl": "https://www.royalroad.com/favicon.ico",
  "lang": "en",
  "bookIdPrefix": "rr",
  "allowedHosts": ["royalroad.com", "royalroadl.com"],
  "minRequestIntervalMs": 700,
  "capabilities": ["search", "lists", "auth", "membership", "progressSync", "resolveUrl", "updates"],
  "lists": [
    { "id": "follow", "title": "Follow", "icon": "add", "membershipToggle": true, "syncable": true }
  ],
  "auth": {
    "fields": [
      { "key": "email", "label": "Email", "type": "email" },
      { "key": "password", "label": "Password", "secret": true }
    ],
    "note": "Password is not stored; only session cookies."
  },
  "settings": [
    { "key": "authorNotes", "type": "toggle", "label": "Include author notes", "default": true }
  ],
  "shareHosts": ["royalroad.com", "royalroadl.com"]
}
```

| Field | Rules |
| --- | --- |
| `id` | `[a-z0-9][a-z0-9_-]{1,39}`. Never change it after release: it keys book ids, data, and secrets. |
| `version` | Dotted numbers. The installer offers an update when the repo version is higher. |
| `apiVersion` | Must be `4` (see [Versions](api.md#versions)). A missing value reads as `1` and is refused. |
| `bookIdPrefix` | Book ids are `{bookIdPrefix}:{workId}`. Defaults to `id`. |
| `allowedHosts` | `flow.fetch` rejects any other host. A listed host also allows its subdomains. |
| `minRequestIntervalMs` | The host spaces this plugin's requests at least this far apart (0 to 10000). |
| `capabilities` | Any of `search`, `lists`, `auth`, `membership`, `progressSync`, `resolveUrl`, `updates`, `browse`. |
| `lists[]` | `id`, `title`, `icon` ([icon tokens](ui-contract.md#icon-tokens)), `membershipToggle`, `syncable`, `notifyDefault` (new-chapter bell defaults on for stories on this list; syncable story lists always do), `kind` (`stories` default, or `browse`: see [Browse lists](api-features.md#browse-lists)). |
| `auth.fields[]` | `key`, `label`, `secret`, `type` (`text`/`email`/`password`). |
| `auth.web` | `{ url, doneCookie }`: sign in on the site itself (see [Web sign-in](api-features.md#web-sign-in)). Replaces `fields`. |
| `settings[]` | `key`, `type` (`toggle`/`int`/`choice`/`text`), `label`, `description`, `default`, `options[]` (`choice`), `min`/`max` (`int`). |
| `shareHosts` | Seeds Import > Router rules that send shared URLs on these hosts to this plugin. |

