# Plugin API: functions and types

Part of the [Plugin API](api.md) (apiVersion 4).

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
  async readPositions(works) {},             // -> ReadPosition[] (capability: progressSync, optional)
  async resolveUrl(url) {},                  // -> workId | null (capability: resolveUrl)
  async cardAction(workId, actionId, on) {}, // -> CardActionResult (needed if card.actions is used)
  async checkUpdates(works) {},              // -> UpdateInfo[]  (capability: updates)
  async browse(id, page, sort, tab) {},      // -> BrowsePage    (capability: browse)
};
```

`page` starts at 1. `setMembership` returns `true` if the site was updated (added for `on: true`,
removed for `on: false`), `false` if only the local list changed (for example signed out, or the
site has no such list). Two-way sync calls it for both directions, so it must be idempotent.

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
// locked: the account can't read it (paywall). The host shows a lock icon, skips it in Download all,
// and opens it as its heading alone instead of calling loadChapter unless the body is already cached.
type ChapterRef = { title: string; url: string; id?: string; locked?: boolean };
type WorkDetail = {
  id: string; title: string; url?: string; author?: string; cover?: string;
  synopsis?: string; tags?: string[];
  chapters: ChapterRef[];            // full table of contents, reading order
  card?: Card;                       // media card slots
  readChapterUrl?: string;           // signed-in reading position on this page (progressSync)
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
`remove`, `notify`, `rename`, and anything starting with `list:`. Duplicate ids keep the first.

An empty card (all slots empty after filtering) counts as no card: the media card shows only the
host's chapter count stat.

