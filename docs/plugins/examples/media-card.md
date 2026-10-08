# Example: a media card

How a plugin fills the story media card, using Royal Road as the model
([`flow-reader-plugins/RoyalRoad`](https://github.com/Oxika95/flow-reader-plugins/tree/main/RoyalRoad)).

## 1. Manifest

```json
{
  "id": "royalroad",
  "version": "1.2.0",
  "apiVersion": 3,
  "lists": [
    { "id": "follow", "title": "Follow", "icon": "add" },
    { "id": "favorite", "title": "Favorite", "icon": "favorite" },
    { "id": "readlater", "title": "Read Later", "icon": "schedule" }
  ]
}
```

The three lists become the first three rail toggles (host-owned).

## 2. `loadWork` returns a card

```js
async loadWork(workId) {
  const res = await flow.fetch(`https://www.royalroad.com/fiction/${workId}`);
  const doc = flow.html.parse(res.text, res.url);
  // ... parse title, author, chapters, status, rating, views ...
  return {
    id: workId, title, url: res.url, author, cover, synopsis, tags, chapters,
    card: {
      stats: [
        { icon: 'star', value: ratingValue, label: 'Rating' },
        { icon: 'followers', value: '12k', label: 'Followers' },
        { icon: 'heart', value: '987', label: 'Favorites' },
        { icon: 'eye', value: '1.2M', label: 'Views' },
      ],
      badges: [status],                                    // "Ongoing"
      links: [{ label: author, url: authorProfileUrl }],
    },
  };
}
```

Result:

```
┌──────────── cover ─────────────────────── (+) (♥) (🕒) (share) ┐
│ Demo Fiction                                                    │
│ Author Name                                                     │
│ [Ongoing]  ★ 4.55  👥 12k  ♥ 987  👁 1.2M  📖 120               │
│ Fantasy · Adventure                                             │
│ A short synopsis…                                               │
│ ▮▮▮▯▯▯▯▯▯▯▯▯▯▯▯▯▯▯▯▯ (chapter cache strip)                      │
└─────────────────────────────────────────────────────────────────┘
  Cached 3 / 120 chapters · cache level 5
  Author Name ↗
  [Download] [Refresh] [Delete]
  [              Read              ]
```

`📖 120` is the host's chapter count, always appended after plugin stats.

## 3. Optional: custom actions

```js
card: {
  // ...
  actions: [
    { id: 'rate', label: 'Rate', icon: 'star', placement: 'rail', toggle: true, on: false },
    { id: 'comments', label: 'Comments', icon: 'comment', placement: 'footer' },
  ],
},

async cardAction(workId, actionId, on) {
  if (actionId === 'rate') {
    await flow.fetch(`https://example.com/rate/${workId}`, { method: 'POST', form: { on: String(on) } });
    return {
      card: { actions: [
        { id: 'rate', label: 'Rate', icon: 'star', placement: 'rail', toggle: true, on },
        { id: 'comments', label: 'Comments', icon: 'comment', placement: 'footer' },
      ] },
      toast: on ? 'Rated' : 'Rating removed',
    };
  }
  if (actionId === 'comments') return { reload: true };
  throw flow.error('UNSUPPORTED', 'Unknown action ' + actionId);
}
```

- A patch replaces whole slots: send the full `actions` array, not just the changed entry.
- `rate` appears on the rail after Share; `comments` sits between Delete and Read.
- Ids such as `read` or `list:follow` are reserved and silently dropped.

## 4. Search rows (optional)

```js
return { items: [{ id, title, author, cover, subtitle: latest, badges: ['Ongoing'],
                   stats: [{ icon: 'star', value: '4.5' }] }], hasMore };
```

These show on the search-result display card (`WorkCard`).
