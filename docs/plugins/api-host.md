# Plugin API: host API, limits, repository index

Part of the [Plugin API](api.md) (apiVersion 6).

## Host API (`flow` global)

| Call | Result |
| --- | --- |
| `await flow.fetch(url, { method, headers, form, body, contentType, cookies, auth, saveAs })` | `{ status, url, headers, text }` or, with `saveAs`, `{ status, url, headers, path, bytes }` (bytes written under the plugin data dir; text is omitted). `url` is the final URL after redirects; header names are lower-case. Non-2xx statuses are returned, not thrown. Cookies persist per plugin. `cookies: false` omits session cookies but still sends and stores bot-clearance cookies (Anubis auth `techaro.lol-anubis` / `techaro.lol-anubis-*` excluding `cookie-verification` / `original-referer`; Cloudflare `cf_clearance` / `__cf_bm` / `__cfruid`). When the response is an Anubis challenge page and a UI host is present, the app runs the site's check in a WebView once (clears stale Anubis auth cookies first; finishes only after a new auth cookie and the challenge node are gone), imports cookies, and retries the fetch once. When the manifest names `auth.bearer`, fetches send `Authorization: Bearer` unless `auth: false` or the call already sets that header. Optional `serverSetting` adds that settings URL's host (exact match) to the allowlist; cleartext `http` is accepted only for that host. |
| `await flow.ws.collect({ url, headers, messages, endPath, timeoutMs, binaryFormat? })` | `{ texts, binariesBase64 }` or, with `binaryFormat: "edge-audio"`, `{ texts, audioPath }` (host-written MP3). See [api-tts.md](api-tts.md). |
| `flow.crypto.sha256(inputBase64)` | Base64 SHA-256 digest of the decoded bytes (TTS tokens, etc.). |
| `flow.crypto.sha256Hex(inputHex)` | Hex SHA-256 of even-length hex input bytes (Edge Sec-MS-GEC). |
| `flow.html.parse(html, baseUrl?)` | `Node` (see below). Handles are valid only during the current call. |
| `await flow.storage.get(key)` / `set(key, value)` / `remove(key)` | Per-plugin string key-value store. |
| `await flow.secrets.get(key)` / `set` / `remove` / `clear()` | Per-plugin encrypted store. Never store passwords. |
| `await flow.cookies.clear()` | Drop this plugin's cookie jar. |
| `flow.settings` | Current values of the manifest `settings` (typed: boolean / number / string). |
| `flow.error(code, message)` | Error to `throw`. Codes: `AUTH_REQUIRED`, `NETWORK`, `PARSE`, `UNSUPPORTED`. |
| `flow.url.resolve(base, href)` | Absolute URL. |
| `await flow.sleep(ms)` | Delay (max 10 s). |
| `flow.log(...args)` | Plugins debug channel (bug bubble / `FlowReader.Plugins`) and logcat `FlowPlugin`. |
| `flow.plugin` | `{ id, version }`. |
| flow.epub.spine | Spine items for a relative EPUB under the plugin data dir. |
| flow.epub.html | Raw HTML of one spine item (relative EPUB path, then zip href). |
| flow.epub.cfiWrite | epub.js-style CFI for an EpubIngest block. |
| flow.epub.cfiRead | spineIndex, blockIndex, charOffset, anchorText, fraction — or null. |

Throwing `AUTH_REQUIRED` makes the app open its sign-in sheet.

Optional `locusSync` capability: `syncProgress(workId, chapter, locus)` receives a third argument
`{ blockIndex, charOffset, anchorText, fraction, positionAt }`. `readPositions` may return those
fields plus `updatedAt`. `WorkDetail.file` is a relative EPUB path; the host reads chapters with
`EpubChapterSource` and skips the HTML chapter cache.

### `Node`

`select(css) -> Node[]`, `selectFirst(css) -> Node | null`, `text()`, `ownText()`, `html()`,
`outerHtml()`, `attr(name)` (`abs:href` resolves against the base URL), `hasClass(name)`,
`data()` (script/style contents), `children() -> Node[]`, `parent() -> Node | null`,
`contains(node) -> boolean`, `remove()`.

Selectors are Jsoup CSS.

## Limits

- Each call runs with a timeout (60 s; `loadChapter` and `list` included) and a 128 MB heap.
- No `setTimeout`, DOM, or Node.js APIs. Use `flow.*`.
- Text responses larger than 8 MB are refused. `saveAs` allows larger bodies (EPUB downloads).

## Repository index

A repository is a static `index.json` (GitHub Pages works):

```json
{
  "name": "Flow Reader Official",
  "apiVersion": 6,
  "plugins": [
    {
      "id": "royalroad",
      "name": "Royal Road",
      "description": "Your followed and saved serials",
      "version": "1.3.0",
      "apiVersion": 6,
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
