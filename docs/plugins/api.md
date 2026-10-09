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
| `apiVersion` | Must be `4` (see [Versions](#versions)). A missing value reads as `1` and is refused. |
| `bookIdPrefix` | Book ids are `{bookIdPrefix}:{workId}`. Defaults to `id`. |
| `allowedHosts` | `flow.fetch` rejects any other host. A listed host also allows its subdomains. |
| `minRequestIntervalMs` | The host spaces this plugin's requests at least this far apart (0 to 10000). |
| `capabilities` | Any of `search`, `lists`, `auth`, `membership`, `progressSync`, `resolveUrl`, `updates`, `browse`. |
| `lists[]` | `id`, `title`, `icon` ([icon tokens](ui-contract.md#icon-tokens)), `membershipToggle`, `syncable`, `notifyDefault` (new-chapter bell defaults on for stories on this list; syncable story lists always do), `kind` (`stories` default, or `browse`: see [Browse lists](#browse-lists)). |
| `auth.fields[]` | `key`, `label`, `secret`, `type` (`text`/`email`/`password`). |
| `auth.web` | `{ url, doneCookie }`: sign in on the site itself (see [Web sign-in](#web-sign-in)). Replaces `fields`. |
| `settings[]` | `key`, `type` (`toggle`/`int`/`choice`/`text`), `label`, `description`, `default`, `options[]` (`choice`), `min`/`max` (`int`). |
| `shareHosts` | Seeds Import > Router rules that send shared URLs on these hosts to this plugin. |

## Functions (`index.js`)

All functions are optional unless the matching capability is declared. All may be `async`.
Arguments and return values are plain JSON.

```js
module.exports = {
  async search(query, page) {},              // -> Page          (capability: search)
  async list(listId, page) {},               // -> Page          (capability: lists; syncable lists)
  async loadWork(workId) {},                 // -> WorkDetail    (required)
  async loadChapter(chapter, work) {},       // -> Chapter       (required)
  async login(fields) {},                    // -> Session       (capability: auth)
  async logout() {},                         //                  (capability: auth)
  async session() {},                        // -> Session       (capability: auth)
  async setMembership(workId, listId, on) {},// -> boolean       (capability: membership)
  async syncProgress(workId, chapter) {},    //                  (capability: progressSync)
  async resolveUrl(url) {},                  // -> workId | null (capability: resolveUrl)
  async cardAction(workId, actionId, on) {}, // -> CardActionResult (needed if card.actions is used)
  async checkUpdates(works) {},              // -> UpdateInfo[]  (capability: updates)
  async browse(id, page, sort, tab) {},      // -> BrowsePage    (capability: browse)
};
```

`page` starts at 1. `setMembership` returns `true` if the site was updated, `false` if only the
local list changed (for example signed out, or removal is not supported remotely).

### Types

```ts
type Work = {
  id: string; title: string; url?: string; author?: string; cover?: string; subtitle?: string;
  badges?: Badge[];                  // pills on the search-result display card (max 4)
  stats?: Stat[];                    // stats on the search-result display card (max 6)
  group?: string;                    // browse lists / items tabs: header where the group changes
};
type Badge = string | { label: string; tone?: 'positive' | 'negative' };  // green / red pill
type Page = { items: Work[]; hasMore?: boolean };
type ChapterRef = { title: string; url: string; id?: string; locked?: boolean };  // locked: no access (paywall)
type WorkDetail = {
  id: string; title: string; url?: string; author?: string; cover?: string;
  synopsis?: string; tags?: string[];
  chapters: ChapterRef[];            // full table of contents, reading order
  card?: Card;                       // media card slots
};
type Chapter = { title: string; html?: string; text?: string };   // html preferred
type Session = { loggedIn: boolean; account?: string };

type Stat = { icon: string; value: string; label?: string };       // icon: icon token
type Link = { label?: string; url: string };                       // http(s) only
type CardAction = {
  id: string;                        // [a-z0-9][a-z0-9_-]{0,31}; not reserved (below)
  label: string;                     // button text / accessibility label
  icon?: string;                     // icon token (rail actions show only the icon)
  placement?: 'rail' | 'footer';     // default 'rail'
  toggle?: boolean;                  // rail: filled accent while `on`
  on?: boolean;
  enabled?: boolean;                 // default true
};
type Card = { stats?: Stat[]; badges?: string[]; links?: Link[]; actions?: CardAction[] };
type CardPatch = Card;               // slots present replace; absent slots are kept
type CardActionResult = { card?: CardPatch; toast?: string; reload?: boolean };
type UpdateQuery = { id: string; url: string; chapters: number; lastChapterUrl: string };
type UpdateInfo = { id: string; chapters?: number; latestUrl?: string };
type Sort = { id: string; label?: string };
type BrowseTab = { id: string; label?: string };
type BrowsePage = {
  items: Work[]; hasMore?: boolean; sorts?: Sort[]; sort?: string;  // sort: id applied
  tabs?: BrowseTab[]; tab?: string;  // tab: id this page belongs to
  text?: string; cover?: string; stats?: Stat[]; badges?: Badge[]; links?: Link[];  // text tab
  sections?: BrowseSection[];        // text tab: blocks below `text`
  groups?: BrowseGroup[];            // items tab: groups always shown, in order
  storyId?: string;                  // story tab: a workId whose chapters the host lists
};
type BrowseGroup = { title: string; empty?: string };  // title matches Work.group
type BrowseSection = {
  heading?: string; collapsed?: boolean;  // collapsed: heading's group starts folded
  title?: string; text?: string; links?: Link[];  // links max 3
};
```

The `work` argument to `loadChapter` is `{ id, url }` of the owning work.

### Card limits (enforced by the host)

| Slot | Max | Extra rules |
| --- | --- | --- |
| `stats` | 6 | Empty `value` dropped |
| `badges` | 4 | Blank dropped |
| `links` | 3 | Non-http(s) URLs dropped |
| `actions` (`rail`) | 2 | After the host's list toggles and Share |
| `actions` (`footer`) | 2 | Between Delete and Read |

Reserved action ids (dropped if used): `read`, `download`, `refresh`, `delete`, `share`, `open`,
`remove`, `notify`, and anything starting with `list:`. Duplicate ids keep the first.

An empty card (all slots empty after filtering) counts as no card: the media card shows only the
host's chapter count stat.

### `cardAction(workId, actionId, on)`

Called when the user taps a plugin action on the media card.

- `on` is passed only for `toggle` actions: the requested **new** state (`!current`).
- Return `{ card }` to patch the card (e.g. flip `on`, update a stat); the host persists the
  patched card with the story. Return `{ toast }` for a short message, `{ reload: true }` to
  re-run `loadWork` and replace the card with fresh data.
- Throw `flow.error(...)` to fail; `AUTH_REQUIRED` opens sign-in. The host shows other errors on
  the card.

### `checkUpdates(works)`

Called by the background new-chapter check (WorkManager, every few hours) with every work the user
monitors (bell on the media card). `works[]` is what the host already stored: `chapters` is the
ToC length, `lastChapterUrl` the last ToC entry's URL.

- Return one `UpdateInfo` per work you could check **cheaply** (a list page covering many works, a
  feed). Give `chapters` (site chapter count) and/or `latestUrl` (newest chapter URL).
- The host treats a work as changed when `chapters` is larger or `latestUrl` differs from
  `lastChapterUrl`, then calls `loadWork` for that work only. Omitted works are skipped this run.
- Never prompt: do not throw `AUTH_REQUIRED`; skip what needs sign-in. Errors fail the whole run.
- Without the `updates` capability the host calls `loadWork` per monitored work instead.

### Browse lists

A list with `"kind": "browse"` holds entry points (for example the creators a user supports), not
stories:

- `list(listId, page)` returns its rows as `Work`s; the host stores only the rows (no library books,
  no catalog entries) and Overwrite never deletes stories. `membershipToggle` and `notifyDefault`
  are ignored; browse lists never notify.
- Signed in, the host re-imports a syncable browse list silently when its tab is shown (at most every
  15 minutes) and right after an entry page closes, so row `badges` / `stats` can carry live state
  such as "3 new".
- Tapping a row opens the entry page: `browse(row.id, page, sort, tab)`. The host passes `""` for
  the plugin's default `sort` / `tab` and the chosen ids afterwards.
- `tabs` (two or more) show as a tab bar; `tab` is the tab this result fills (unknown values fall
  back to the first tab). The host loads a tab only when it is first shown and keeps each loaded tab
  while the page is open, so cache slow data (profiles) in `flow.storage` rather than refetching.
- A tab is one of:
  - **Story tab** (`storyId` set): the host lists that work's chapters (fetching it with `loadWork`
    once if not stored) with lock icons (`ChapterRef.locked`) and downloaded icons, newest first or
    oldest first. Tapping a chapter opens that work's media card positioned at that chapter (Read,
    Download, and the position slider start there); downloads are started from the card.
  - **Text tab** (`text`, `cover`, `stats`, `badges`, `links`, `sections`; no `items`): a header
    card plus `text` paragraphs (split on blank lines) and link buttons, then `sections`. A section
    `heading` starts a divided group (for example "Your membership", "More tiers"); each section
    shows its `title`, `text` paragraphs, and `links`. Empty sections are dropped. `collapsed: true`
    on a heading section folds that group (the heading section and the heading-less ones after it)
    behind the tappable heading.
  - **Items tab**: `items` as `Work`s the user can open as stories, with `sorts` (chips below the
    tabs when there are two or more; `sort` is the applied one, unknown values fall back to the
    first) and paging via `hasMore`.
- Rows (list rows and items) may set `group`: when two or more groups are present the host adds a
  divider and the group title wherever the group changes, so return rows already ordered by group.
  An items tab may also declare `groups`: the host then shows those groups in that order, each with
  its header even when empty, plus the group's `empty` text (or "Nothing here yet.") once the last
  page is loaded. Items whose `group` is not declared follow under their own headers.
- Badges may carry a `tone` (`positive` green, `negative` red), for example membership status.
- Opening an item shows its media card; adding it to a story list is what brings it into the library.
- Download all skips `locked` chapters that are not already downloaded.

### Web sign-in

For sites whose login needs a captcha, device verification, or OAuth, declare
`"auth": { "web": { "url": "https://site/login", "doneCookie": "session_id" } }` instead of
`fields`. The host:

1. Clears its browser cookies for `allowedHosts` and opens `url` in an in-app WebView with the
   same user agent as `flow.fetch` (Cloudflare clearance cookies are tied to it). No JavaScript
   bridge; navigation to other sites (OAuth) is allowed.
2. Once `doneCookie` has a value on an allowed host, copies every cookie of the allowed hosts into
   the plugin's cookie jar (domain = the allowed host and its subdomains, lifetime one year) and
   closes the browser.
3. Calls `session()`; `loggedIn: false` reports "Sign in failed".

`login(fields)` is not called. `logout()` should still `flow.cookies.clear()`; the host also clears
the browser cookies. The plugin never sees a password.

### ToC refresh

Every `loadWork` result replaces the stored ToC. The host matches chapters by `url`: cached bodies,
Download all / partial pins, and the saved position move with their URL, so inserting chapters
before ones already read is safe. Keep each chapter's `url` stable; a removed URL deletes its body.

## Host API (`flow` global)

| Call | Result |
| --- | --- |
| `await flow.fetch(url, { method, headers, form, body, contentType })` | `{ status, url, headers, text }`. `url` is the final URL after redirects; header names are lower-case. Non-2xx statuses are returned, not thrown. Cookies persist per plugin. |
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
      "iconUrl": "https://www.royalroad.com/favicon.ico",
      "manifestUrl": "RoyalRoad/plugin.json",
      "pluginUrl": "RoyalRoad/index.js",
      "manifestSha256": "…",
      "sha256": "…"
    }
  ]
}
```

URLs may be relative to `index.json`. `sha256` is the hex SHA-256 of `index.js` and
`manifestSha256` of `plugin.json`; the installer refuses files that do not match. Each entry's
`apiVersion` decides compatibility; the top-level `apiVersion` is informational. The
[flow-reader-plugins](https://github.com/Oxika95/flow-reader-plugins) repo builds this file in CI.
