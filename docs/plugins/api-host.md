# Plugin API: host API, limits, repository index

Part of the [Plugin API](api.md) (apiVersion 4).

## Host API (`flow` global)

| Call | Result |
| --- | --- |
| `await flow.fetch(url, { method, headers, form, body, contentType, cookies })` | `{ status, url, headers, text }`. `url` is the final URL after redirects; header names are lower-case. Non-2xx statuses are returned, not thrown. Cookies persist per plugin; `cookies: false` sends and stores none for this request. |
| `flow.html.parse(html, baseUrl?)` | `Node` (see below). Handles are valid only during the current call. |
| `await flow.storage.get(key)` / `set(key, value)` / `remove(key)` | Per-plugin string key-value store. |
| `await flow.secrets.get(key)` / `set` / `remove` / `clear()` | Per-plugin encrypted store. Never store passwords. |
| `await flow.cookies.clear()` | Drop this plugin's cookie jar. |
| `flow.settings` | Current values of the manifest `settings` (typed: boolean / number / string). |
| `flow.error(code, message)` | Error to `throw`. Codes: `AUTH_REQUIRED`, `NETWORK`, `PARSE`, `UNSUPPORTED`. |
| `flow.url.resolve(base, href)` | Absolute URL. |
| `await flow.sleep(ms)` | Delay (max 10 s). |
| `flow.log(...args)` | Logcat under `FlowPlugin`. |
| `flow.plugin` | `{ id, version }`. |

Throwing `AUTH_REQUIRED` makes the app open its sign-in sheet.

### `Node`

`select(css) -> Node[]`, `selectFirst(css) -> Node | null`, `text()`, `ownText()`, `html()`,
`outerHtml()`, `attr(name)` (`abs:href` resolves against the base URL), `hasClass(name)`,
`data()` (script/style contents), `children() -> Node[]`, `parent() -> Node | null`,
`contains(node) -> boolean`, `remove()`.

Selectors are Jsoup CSS.

## Limits

- Each call runs with a timeout (60 s; `loadChapter` and `list` included) and a 64 MB heap.
- No `setTimeout`, DOM, or Node.js APIs. Use `flow.*`.
- Responses larger than 8 MB are refused.

## Repository index

A repository is a static `index.json` (GitHub Pages works):

```json
{
  "name": "Flow Reader Official",
  "apiVersion": 4,
  "plugins": [
    {
      "id": "royalroad",
      "name": "Royal Road",
      "description": "Your followed and saved serials",
      "version": "1.3.0",
      "apiVersion": 4,
      "lang": "en",
      "manifestUrl": "RoyalRoad/plugin.json",
      "pluginUrl": "RoyalRoad/index.js",
      "manifestSha256": "…",
      "sha256": "…",
      "iconUrl": "RoyalRoad/icon.png",
      "iconSha256": "…"
    }
  ]
}
```

URLs may be relative to `index.json`. `sha256` is the hex SHA-256 of `index.js` and
`manifestSha256` of `plugin.json`; the installer refuses files that do not match. The optional
`iconUrl` (the package's `icon.png`) is downloaded once at install, only with its `iconSha256`. Each entry's
`apiVersion` decides compatibility; the top-level `apiVersion` is informational. The
[flow-reader-plugins](https://github.com/Oxika95/flow-reader-plugins) repo builds this file in CI.
