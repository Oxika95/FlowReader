# Plugin API: optional features

Part of the [Plugin API](api.md) (apiVersion 4).

## `cardAction(workId, actionId, on)`

Called when the user taps a plugin action on the media card.

- `on` is passed only for `toggle` actions: the requested **new** state (`!current`).
- Return `{ card }` to patch the card (e.g. flip `on`, update a stat); the host persists the
  patched card with the story. Return `{ toast }` for a short message, `{ reload: true }` to
  re-run `loadWork` and replace the card with fresh data.
- Throw `flow.error(...)` to fail; `AUTH_REQUIRED` opens sign-in. The host shows other errors on
  the card.

## `checkUpdates(works)`

Called by the background new-chapter check (WorkManager, every few hours) with every work the user
monitors (bell on the media card). `works[]` is what the host already stored: `chapters` is the
ToC length, `lastChapterUrl` the last ToC entry's URL.

- Return one `UpdateInfo` per work you could check **cheaply** (a list page covering many works, a
  feed). Give `chapters` (site chapter count) and/or `latestUrl` (newest chapter URL).
- The host treats a work as changed when `chapters` is larger or `latestUrl` differs from
  `lastChapterUrl`, then calls `loadWork` for that work only. Omitted works are skipped this run.
- Never prompt: do not throw `AUTH_REQUIRED`; skip what needs sign-in. Errors fail the whole run.
- Without the `updates` capability the host calls `loadWork` per monitored work instead.

## Two-way sync: `readPositions(works)` and `syncProgress`

While signed in, the host syncs `syncable` lists and reading positions in both directions on the
background check interval, when the plugin tab opens (at most every 10 minutes), after sign-in, and
on **Sync now**:

- Lists: `list()` pulls each syncable list; local additions and removals are pushed with
  `setMembership(workId, listId, on)`. The host keeps the last synced state, so a story missing on
  the site is removed locally only if it was there at the last sync.
- `syncProgress(workId, chapter)` pushes the reading position when it moves to another chapter.
  Pushes that fail are queued and retried; it may throw on network errors.
- `readPositions(works)` (optional) returns `[{ id, chapterUrl, chapterTitle? }]`: the site's
  last-read chapter for each work a list page covers (no per-work requests). `works[]` has the same
  shape as `checkUpdates` (`chapters: 0`, `lastChapterUrl: ""` for works never loaded). Omit unknown
  works; never throw `AUTH_REQUIRED` for single works.
- `WorkDetail.readChapterUrl` (optional): the site's reading position read from the same work page
  `loadWork` fetched. The host merges it whenever it stores the ToC (story opened, refreshed,
  new-chapter check), so works no list page covers still sync without extra requests.
- A work whose saved ToC is missing or lacks the returned chapter is not loaded for it: the host
  keeps the position (and `chapterTitle`, shown as "Last read") and applies it when the ToC is next
  stored.
- When only one side moved since the last sync it wins; when both moved the user picks. Overwrite
  (**Replace with site lists**) applies every site position as is.
- Fetch chapter bodies with `flow.fetch(url, { cookies: false })` when the site records reads on
  page views, so downloads ahead of the reader do not move the site's position.

## Browse lists

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

## Web sign-in

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

## ToC refresh

Every `loadWork` result replaces the stored ToC. The host identifies chapters by `url`: cached
bodies are stored per URL and the saved position records its chapter's URL, so inserting chapters
before ones already read is safe. Keep each chapter's `url` stable; a removed URL deletes its body,
and a saved position on it moves to the next surviving chapter. Removed chapters never count as new.

